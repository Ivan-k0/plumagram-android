/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/plumagram-android)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;

/**
 * TGx101 (user 2026-10-12, «как пыль в оффе, но пиксельная»): a deleted message turns to dust — a front sweeps over it
 * from left to right, and behind it the message comes apart into small square pixels of its own colours that drift
 * up and to the right, wobble a little and melt away. Drawn on a transparent layer over the chat from a snapshot taken
 * right before the message leaves the list; the list closes the gap underneath as usual.
 *
 * Speed: all pixels go out in one drawVertices call per 8k of them (Android 10+; older systems draw rects with fewer,
 * larger pixels), the snapshot of a big message is taken downscaled, and the pixel count is capped — a full-screen
 * photo gets bigger pixels instead of tens of thousands of them.
 */
public final class Tgx101DeleteEffect extends View {
  private static final float SWEEP = .45f, JITTER = .12f, LIFE_MIN = .55f, LIFE_RANGE = .4f, FLASH = .16f;
  private static final float END = SWEEP + JITTER + LIFE_MIN + LIFE_RANGE;
  private static final float PIXEL_DP = 2f; // TGx101 (user 2026-10-12 14:00 «блоки чуть крупнее»): was 1.25
  private static final int MAX_PARTICLES_FAST = 12000, MAX_PARTICLES_SLOW = 2000;
  private static final long MAX_SNAPSHOT = 3_000_000L; // the picture itself is seen now (it flattens) — sharper than the dust needed
  private static final int BATCH = 8000; // 4 vertices each — stays under the 16-bit index limit

  private static final class Burst {
    Bitmap snapshot;
    final RectF bounds = new RectF();
    int cols, rows, count;
    float size;
    float[] x0, y0, vx, vy, release, life, phase;
    int[] color, bright;
    float[] verts;
    int[] colors;
    long start;
    boolean fade; // an album that stays, smaller: its old look dissolves
    float shift; // how far the chat has scrolled since — the dust is tied to the messages, not to the screen
    @Nullable RectF group; // (no longer used: «one television» for a batch gave way to the wave, 2026-11-18)
    boolean right; // the bubble stands at the right: it shrinks towards its right edge
    int tone; // the picture's average colour, lit up — the glowing line and dot of the «television»
    @Nullable Bitmap soft; // a tiny copy drawn large — the blur it melts into (cheap, every Android)
    boolean sink; // tall (a photo, a video, a long post): it sinks away into the screen instead of the «television»
  }

  private final ArrayList<Burst> bursts = new ArrayList<>();
  private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final Paint dustPaint = new Paint();
  private final RectF dst = new RectF();
  private final int[] loc = new int[2], srcLoc = new int[2];
  private static short[] indices;

  public Tgx101DeleteEffect (Context context) {
    super(context);
    setWillNotDraw(false);
  }

  private static boolean fastPath () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !fastBroken; // drawVertices is hardware accelerated from Android 10
  }

  /**
   * Takes a snapshot of {@code source} (a message view about to be removed) and turns it to dust where it stands.
   * {@code outline} (the bubble, in the view's coordinates) limits the dust to the message itself.
   */
  private static long lastHapticAt;

  // TGx101 (4PDA 2026-10-18 #262, #272: «вылетает при анимации рассыпания… пока все сообщения не удалятся», «при удалении
  // чата»): clearing a chat removes every visible message at once — a snapshot and thousands of grains for each ran a
  // weak phone out of memory, and a snapshot of a message with pictures in video memory can't be drawn on a software
  // canvas at all. (2026-10-24) a batch shares one particle and snapshot budget (see explode(List)); whatever goes wrong
  // here only means no dust, never a crash
  private static boolean fastBroken;

  private static Boolean capable;

  /**
   * (user 2026-10-19 «у офф телеграм такой же принцип?») as Telegram's own dust, only on phones strong enough for it:
   * a phone the system calls low on memory, or one giving the app under 256 MB, just lets the message go as before
   */
  private static boolean capable (Context context) {
    if (capable == null) {
      boolean ok = true;
      try {
        android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        // isLowRamDevice is Android 4.4+: on 4.3 it threw, the catch left «ok» and the dust ran on the weakest phones
        if (am != null) ok = (Build.VERSION.SDK_INT < 19 || !am.isLowRamDevice()) && am.getMemoryClass() >= 256;
      } catch (Throwable ignored) { }
      capable = ok;
      Tgx101Diag.mark("delete effect: " + (ok ? "on" : "off — a weak phone"));
    }
    return capable;
  }

  // TGx101 (user 2026-10-24 «сделай по уму»): messages deleted together turn to dust as one — like Telegram's own
  // effect, which takes the whole group in one pass. Each message is photographed while it's still whole (take(), before
  // the list destroys it), then launch() gives the batch one budget: the grains are the same size on all of its messages
  // and together stay under the particle cap; the photos share a memory cap — past it they're taken coarser. Twenty
  // messages cost what one big photo does, no «at most 6» any more.
  private final ArrayList<Burst> pending = new ArrayList<>();
  private long pendingPixels;
  private int pendingAsked;
  private static final long BATCH_SNAPSHOT = 6_000_000L;
  private static final float COARSE = .35f;

  /** Photographs a message view about to leave the list ({@code null}: not on screen); {@link #launch()} lets the batch go */
  public void take (@Nullable View source, @Nullable Path outline) {
    take(source, outline, false);
  }

  public void take (@Nullable View source, @Nullable Path outline, boolean right) {
    pendingAsked++;
    if (!capable(getContext()) || source == null || source.getWidth() <= 0 || source.getHeight() <= 0 || !source.isShown()) return;
    try {
      RectF area = new RectF(0, 0, source.getWidth(), source.getHeight());
      if (outline != null) {
        RectF r = new RectF();
        outline.computeBounds(r, true);
        if (r.width() > 0 && r.height() > 0 && r.intersect(area)) area.set(r);
        else outline = null;
      }
      double pixels = Math.max(1.0, (double) area.width() * area.height());
      long left = BATCH_SNAPSHOT - pendingPixels;
      float scale = (float) Math.min(1.0, Math.sqrt(Math.min(MAX_SNAPSHOT, Math.max(0, left)) / pixels));
      scale = Math.max(scale, COARSE);
      Burst b = snapshot(source, area, outline, scale);
      if (b != null) {
        b.right = right;
        pending.add(b);
        pendingPixels += (long) (b.snapshot.getWidth()) * b.snapshot.getHeight();
      }
    } catch (Throwable t) {
      Tgx101Diag.mark("delete effect: snapshot failed " + t.getClass().getSimpleName() + " " + t.getMessage());
    }
  }

  /** Photographs an album about to be laid out smaller — {@link #launch()} lets it dissolve over the new layout */
  public void takeFade (@Nullable View source) {
    if (!capable(getContext()) || source == null || source.getWidth() <= 0 || source.getHeight() <= 0 || !source.isShown()) return;
    RectF area = new RectF(0, 0, source.getWidth(), source.getHeight());
    double pixels = Math.max(1.0, (double) area.width() * area.height());
    float scale = (float) Math.min(1.0, Math.sqrt(MAX_SNAPSHOT / pixels));
    Burst b = snapshot(source, area, null, Math.max(scale, COARSE));
    if (b != null) {
      b.fade = true;
      pending.add(b);
      pendingAsked++;
    }
  }

  /** «Television» (user 2026-11-18) instead of «shrink with blur» (А+В, kept) */
  static final boolean TV = true;
  static final long WAVE_STEP = 45;
  static final int WAVE_STEPS = 8;
  private long lastWave;

  static final float TGX101_SINK_FROM_DP = 140f;
  private boolean lastSink;

  /** When the chat may start closing in after the last batch set off: at once for what sinks, the «television» half run in */
  public long tgx101GapDelay () {
    return lastSink ? 30 : 140;
  }

  /** The last batch sank (tall) — its gap closes over a longer, softer glide */
  public boolean tgx101LastSank () {
    return lastSink;
  }

  /** How long the wave of the last batch runs before its top message starts going (0 for one message) */
  public long lastWave () {
    return lastWave;
  }

  public void launch () {
    lastWave = 0;
    int asked = pendingAsked;
    pendingAsked = 0;
    pendingPixels = 0;
    if (asked == 0) return;
    // one tap for the whole batch (user 2026-10-13 «при удалении нескольких сообщений виброотклик должен быть одинарный»)
    long now = SystemClock.uptimeMillis();
    if (now - lastHapticAt > 300) {
      org.thunderdog.challegram.tool.Tgx101Haptics.play(org.thunderdog.challegram.tool.Tgx101Haptics.CLICK);
    }
    lastHapticAt = now;
    if (pending.isEmpty()) {
      if (capable(getContext())) Tgx101Diag.mark("delete effect: no snapshot (" + asked + " not on screen)");
      return;
    }
    ArrayList<Burst> batch = new ArrayList<>(pending);
    pending.clear();
    try {
      long t0 = SystemClock.uptimeMillis();
      // what's still flying from an earlier batch keeps its share; a new batch always gets at least a quarter
      int maxParticles = fastPath() ? MAX_PARTICLES_FAST : MAX_PARTICLES_SLOW;
      int flying = 0;
      for (Burst f : bursts) flying += f.count;
      int budget = Math.max(maxParticles / 4, maxParticles - flying);
      double total = 0;
      for (Burst b : batch) total += (double) b.bounds.width() * b.bounds.height();
      float size = Math.max(Screen.dp(PIXEL_DP), (float) Math.sqrt(total / budget));
      int particles = 0;
      // (user 2026-10-30, mockup «Удаление без пыли», variant 3 «Схлопывание» with a wave): no dust — each message
      // flattens into a line where it stood, the line shrinks to the middle and goes out; several go top to bottom,
      // each 30 ms after the one above, the whole wave at most 150 ms. One picture per message instead of thousands of
      // grains — the GPU barely notices
      // (user 2026-10-30 18:45 «пусть сжимаются в один телевизор»): several messages go as one — no wave, the batch
      // flattens into one line around its common centre and that line goes out
      // (user 2026-11-18, mockup «Удаление сообщения: варианты», А+В «Сжатие с размытием» chosen): each message shrinks
      // a little towards its own edge, melts into a blur and goes out in 200 ms; several go as a wave from the bottom up,
      // each 45 ms after the one below, only the first 8 wait their turn (the wave stays short however many there are)
      ArrayList<Burst> leaving = new ArrayList<>();
      for (Burst b : batch) {
        b.count = 0;
        b.start = now;
        if (!b.fade) {
          leaving.add(b);
          try {
            int sw = Math.max(1, b.snapshot.getWidth() / 10), sh = Math.max(1, b.snapshot.getHeight() / 10);
            b.soft = Bitmap.createScaledBitmap(b.snapshot, sw, sh, true);
          } catch (Throwable ignored) {
            b.soft = null;
          }
        }
        bursts.add(b);
      }
      if (TV) {
        // (user 2026-11-18 «верни анимацию удаления — телевизор, но лучше»; earlier 2026-10-30 «пусть сжимаются в один
        // телевизор»): several go as one picture around their common centre, together, no wave
        RectF group = null;
        for (Burst b : leaving) {
          if (group == null) group = new RectF(b.bounds);
          else group.union(b.bounds);
        }
        if (leaving.size() > 1) for (Burst b : leaving) b.group = group;
        for (Burst b : leaving) b.tone = averageTone(b);
        // (user 2026-11-21 «удаление медиа топорно — подумай как»: a photo flattened to a line left a tall empty hole,
        // then the chat fell into it): what is tall sinks away — smaller towards its middle and fading, while the chat
        // closes in at once
        boolean tall = group != null && group.height() > Screen.dp(TGX101_SINK_FROM_DP);
        for (Burst b : leaving) b.sink = tall;
        lastSink = tall;
        lastWave = 0;
      } else {
        java.util.Collections.sort(leaving, (x, y) -> Float.compare(y.bounds.bottom, x.bounds.bottom));
        for (int i = 0; i < leaving.size(); i++) leaving.get(i).start = now + Math.min(i, WAVE_STEPS - 1) * WAVE_STEP;
        lastWave = (long) Math.min(leaving.size() > 0 ? leaving.size() - 1 : 0, WAVE_STEPS - 1) * WAVE_STEP;
      }
      int made = batch.size();
      batch.clear();
      setVisibility(VISIBLE);
      postInvalidateOnAnimation();
      Tgx101Diag.mark("delete effect: " + made + " message(s) of " + asked + ", " + particles + " pixels of " + Math.round(size) + " px, " + (SystemClock.uptimeMillis() - t0) + " ms" + (fastPath() ? "" : " (rects)"));
    } catch (Throwable t) {
      for (Burst b : batch) if (b.snapshot != null && !b.snapshot.isRecycled() && !bursts.contains(b)) b.snapshot.recycle();
      Tgx101Diag.mark("delete effect: failed " + t.getClass().getSimpleName() + " " + t.getMessage());
    }
  }

  private @Nullable Burst snapshot (View source, RectF area, @Nullable Path outline, float scale) {
    int w = Math.max(1, Math.round(area.width())), h = Math.max(1, Math.round(area.height()));
    int bw = Math.max(1, Math.round(w * scale)), bh = Math.max(1, Math.round(h * scale));
    Bitmap snapshot;
    try {
      snapshot = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
    } catch (Throwable t) {
      return null;
    }
    Canvas sc = new Canvas(snapshot);
    sc.scale(bw / (float) w, bh / (float) h);
    sc.translate(-area.left, -area.top);
    if (outline != null) sc.clipPath(outline);
    try {
      source.draw(sc);
    } catch (Throwable t) {
      snapshot.recycle(); // e.g. «Software rendering doesn't support hardware bitmaps»
      Tgx101Diag.mark("delete effect: snapshot failed " + t.getClass().getSimpleName());
      return null;
    }
    Burst b = new Burst();
    b.snapshot = snapshot;
    source.getLocationInWindow(srcLoc);
    getLocationInWindow(loc);
    float bl = srcLoc[0] - loc[0] + area.left, bt = srcLoc[1] - loc[1] + area.top;
    b.bounds.set(bl, bt, bl + w, bt + h);
    return b;
  }

  private int grains (Burst b, float size, long start) {
    float w = b.bounds.width(), h = b.bounds.height();
    b.size = size;
    b.cols = Math.max(1, (int) Math.ceil(w / b.size));
    b.rows = Math.max(1, (int) Math.ceil(h / b.size));
    int n = b.cols * b.rows;
    int[] pixels = new int[n];
    Bitmap small = Bitmap.createScaledBitmap(b.snapshot, b.cols, b.rows, true); // each pixel — the colour of a dust grain
    small.getPixels(pixels, 0, b.cols, 0, 0, b.cols, b.rows);
    if (small != b.snapshot) small.recycle();

    int count = 0;
    for (int p : pixels) if ((p >>> 24) >= 60) count++;
    b.count = count;
    b.x0 = new float[count]; b.y0 = new float[count]; b.vx = new float[count]; b.vy = new float[count];
    b.release = new float[count]; b.life = new float[count]; b.phase = new float[count]; b.color = new int[count]; b.bright = new int[count];
    float dp = Screen.density();
    int k = 0;
    for (int i = 0; i < n; i++) {
      int p = pixels[i];
      if ((p >>> 24) < 60) continue;
      int col = i % b.cols, row = i / b.cols;
      float r1 = rnd(i * 3 + 1), r2 = rnd(i * 7 + 5), r3 = rnd(i * 11 + 9), r4 = rnd(i * 13 + 2);
      b.x0[k] = b.bounds.left + col * b.size;
      b.y0[k] = b.bounds.top + row * b.size;
      b.vx[k] = (25f + r1 * 110f) * dp;
      b.vy[k] = -(35f + r2 * 140f) * dp;
      b.release[k] = SWEEP * col / (float) b.cols + JITTER * r3;
      b.life[k] = LIFE_MIN + LIFE_RANGE * r4;
      b.phase[k] = r1 * 6.283f;
      b.color[k] = p | 0xff000000;
      b.bright[k] = lighten(b.color[k], r2 < .14f ? .75f : .4f); // a flash as it breaks off; every 7th grain — a sparkle
      k++;
    }
    if (fastPath()) {
      b.verts = new float[Math.min(count, BATCH) * 8];
      b.colors = new int[Math.min(count, BATCH) * 4];
      ensureIndices();
    }
    b.start = start;
    return count;
  }

  private static void ensureIndices () {
    if (indices != null) return;
    indices = new short[BATCH * 6];
    for (int q = 0; q < BATCH; q++) {
      int v = q * 4, i = q * 6;
      indices[i] = (short) v; indices[i + 1] = (short) (v + 1); indices[i + 2] = (short) (v + 2);
      indices[i + 3] = (short) v; indices[i + 4] = (short) (v + 2); indices[i + 5] = (short) (v + 3);
    }
  }

  private static int lighten (int c, float f) {
    int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, bl = c & 0xff;
    r += (int) ((255 - r) * f); g += (int) ((255 - g) * f); bl += (int) ((255 - bl) * f);
    return 0xff000000 | (r << 16) | (g << 8) | bl;
  }

  private static int mix (int a, int b, float f) {
    int ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
    int br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
    return 0xff000000 | ((int) (ar + (br - ar) * f) << 16) | ((int) (ag + (bg - ag) * f) << 8) | (int) (ab + (bb - ab) * f);
  }

  /** The chat list scrolled by {@code dy} (content moved up by it) — the dust goes with it */
  public void onListScrolled (int dy) {
    if (bursts.isEmpty() || dy == 0) return;
    for (Burst b : bursts) b.shift -= dy;
    invalidate();
  }

  private static float rnd (int i) {
    double x = Math.sin(i * 127.1 + 311.7) * 43758.5453;
    return (float) (x - Math.floor(x));
  }

  @Override
  protected void onDraw (Canvas c) {
    if (bursts.isEmpty()) return;
    try {
      drawAll(c);
    } catch (Throwable t) {
      // no dust rather than a crash: drop what's flying
      Tgx101Diag.mark("delete effect: draw failed " + t.getClass().getSimpleName() + " " + t.getMessage());
      fastBroken = true;
      for (Burst b : bursts) {
        if (b.snapshot != null && !b.snapshot.isRecycled()) b.snapshot.recycle();
        if (b.soft != null && !b.soft.isRecycled()) b.soft.recycle();
      }
      bursts.clear();
      setVisibility(INVISIBLE);
    }
  }

  private static final float FADE_END = .18f;
  private static final float SQUASH_FLAT = .14f, SQUASH_OUT = .11f, SQUASH_END = SQUASH_FLAT + SQUASH_OUT;
  private static final android.view.animation.Interpolator SQUASH_CURVE = new android.view.animation.DecelerateInterpolator(1.6f);

  // TGx101 «television» (user 2026-11-18 «схлопывание должно быть до конца — полоска уменьшается, пока не исчезнет; в
  // самом конце чуть размытия, как у настоящего телевизора»): the picture flattens into a thin line lighting up as it
  // goes, the line runs in to the middle all the way, and a small soft glowing dot is left a moment and fades
  // (phone check 2026-11-18, light theme: the line all but invisible, it stood full width and vanished in 2–3 frames, no
  // dot to be seen, the row coming down crossed what was left): a darker line on a light theme, a lighter one on a dark,
  // an even run in to the middle, a short small dot; the chat closes in as the line is nearly gone
  // (second check: the row below came down while the line was still full width — the line and the dot were drawn over its
  // text): quicker, and the chat closes in only once the line is a dot (TGX101_GAP_CLOSE_DELAY = TV_FLAT + TV_IN)
  // (2026-11-21 «телевизор доработай, чтобы выглядело естественней»): a little quicker, the chat closes in from halfway
  private static final float TV_FLAT = .09f, TV_IN = .12f, TV_DOT = .05f, TV_END = TV_FLAT + TV_IN + TV_DOT;
  private static final android.view.animation.Interpolator TV_FLAT_CURVE = androidx.core.view.animation.PathInterpolatorCompat.create(.5f, 0f, .2f, 1f);
  private static final android.view.animation.Interpolator TV_IN_CURVE = androidx.core.view.animation.PathInterpolatorCompat.create(.4f, 0f, .6f, 1f);
  private final Paint tvPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private static int averageTone (Burst b) {
    int c;
    try {
      Bitmap one = Bitmap.createScaledBitmap(b.soft != null ? b.soft : b.snapshot, 1, 1, true);
      c = one.getPixel(0, 0);
      if (one != b.soft && one != b.snapshot) one.recycle();
    } catch (Throwable t) {
      c = 0xff808080;
    }
    c |= 0xff000000;
    // (user 2026-11-18 «в тёмной теме телевизор сильно светлый — под стать теме, чтобы не мелькало, но было заметно»):
    // a dark theme gets a line only a little lighter than the bubble (it was near white)
    return org.thunderdog.challegram.theme.Theme.isDark() ? lighten(c, .22f) : mix(c, 0xff000000, .38f);
  }

  private void drawTv (Canvas c, Burst b, float t) {
    if (t < 0f) t = 0f;
    RectF r = b.group != null ? b.group : b.bounds;
    float cx = r.centerX(), cy = r.centerY();
    float thin = Math.max(Screen.dp(2f), 2f);
    if (t < TV_FLAT) {
      float f = TV_FLAT_CURVE.getInterpolation(t / TV_FLAT);
      float sy = 1f - (1f - thin / Math.max(thin, r.height())) * f;
      dst.set(b.bounds.left, cy + (b.bounds.top - cy) * sy, b.bounds.right, cy + (b.bounds.bottom - cy) * sy);
      bitmapPaint.setAlpha(255);
      c.drawBitmap(b.snapshot, null, dst, bitmapPaint);
      // it lights up as it flattens
      tvPaint.setShader(null);
      tvPaint.setColor(me.vkryl.core.ColorUtils.alphaColor((org.thunderdog.challegram.theme.Theme.isDark() ? .55f : .85f) * f, b.tone));
      c.drawRect(dst, tvPaint);
      return;
    }
    float h = thin;
    if (t < TV_FLAT + TV_IN) {
      float f = TV_IN_CURVE.getInterpolation((t - TV_FLAT) / TV_IN);
      // the line runs in to the middle all the way (no cut-off at a fifth)
      float half = (b.group != null ? r.width() : b.bounds.width()) / 2f * (1f - f);
      float left = Math.max(b.bounds.left, cx - half), right = Math.min(b.bounds.right, cx + half);
      if (right > left) {
        tvPaint.setShader(null);
        tvPaint.setColor(b.tone);
        dst.set(left, cy - h / 2f, right, cy + h / 2f);
        c.drawRoundRect(dst, h / 2f, h / 2f, tvPaint);
      }
      if (f > .6f) drawTvDot(c, cx, cy, h, (f - .6f) / .4f, b.tone, 1f);
      return;
    }
    // the last moment: a soft dot of light, a little blurred, fading
    float f = Math.min(1f, (t - TV_FLAT - TV_IN) / TV_DOT);
    drawTvDot(c, cx, cy, h, 1f, b.tone, 1f - f);
  }

  private void drawTvDot (Canvas c, float cx, float cy, float h, float grow, int tone, float alpha) {
    if (alpha <= 0f) return;
    float radius = h * (1.2f + 1.6f * grow);
    int core = org.thunderdog.challegram.theme.Theme.isDark() ? lighten(tone, .25f) : tone; // no white flash on a dark theme
    tvPaint.setShader(new android.graphics.RadialGradient(cx, cy, radius,
      new int[] {me.vkryl.core.ColorUtils.alphaColor(alpha, core), me.vkryl.core.ColorUtils.alphaColor(alpha * .8f, tone), me.vkryl.core.ColorUtils.alphaColor(0f, tone)},
      new float[] {0f, .35f, 1f}, android.graphics.Shader.TileMode.CLAMP));
    c.drawCircle(cx, cy, radius, tvPaint);
    tvPaint.setShader(null);
  }

  private static final float SINK_END = .30f;
  private static final android.view.animation.Interpolator SINK_CURVE = androidx.core.view.animation.PathInterpolatorCompat.create(.4f, 0f, .2f, 1f);

  /** smaller towards the middle (to 60 %), softly blurring and fading out */
  private void drawSink (Canvas c, Burst b, float t) {
    if (t < 0f) t = 0f;
    float f = SINK_CURVE.getInterpolation(Math.min(1f, t / SINK_END));
    RectF r = b.group != null ? b.group : b.bounds;
    float cx = r.centerX(), cy = r.centerY(), scale = 1f - .4f * f;
    dst.set(cx + (b.bounds.left - cx) * scale, cy + (b.bounds.top - cy) * scale, cx + (b.bounds.right - cx) * scale, cy + (b.bounds.bottom - cy) * scale);
    float alpha = (float) Math.pow(1f - f, 1.4);
    bitmapPaint.setAlpha((int) (255 * alpha * (1f - .5f * f)));
    c.drawBitmap(b.snapshot, null, dst, bitmapPaint);
    if (b.soft != null) { // it goes out of focus as it leaves
      bitmapPaint.setAlpha((int) (255 * alpha * .5f * f));
      c.drawBitmap(b.soft, null, dst, bitmapPaint);
    }
    bitmapPaint.setAlpha(255);
  }

  private static final float SHRINK_END = .2f;
  private static final android.view.animation.Interpolator SHRINK_CURVE = androidx.core.view.animation.PathInterpolatorCompat.create(.3f, 0f, .8f, .15f);

  /** А+В: shrinks to .86 towards its own edge, the sharp picture giving way to its blur, and goes out */
  private void drawShrink (Canvas c, Burst b, float t) {
    float f = Math.max(0f, Math.min(1f, t / SHRINK_END));
    float e = SHRINK_CURVE.getInterpolation(f);
    // (phone check 2026-11-18: the neighbour coming down showed through the half-faded picture — «Test oOk»): it is gone
    // by 55 % of the time, before the chat closing in reaches it; the shrink goes on to the end unseen
    float scale = 1f - .14f * e, alpha = 1f - Math.min(1f, f / .55f);
    if (alpha <= 0f) return;
    float px = b.right ? b.bounds.right : b.bounds.left, py = b.bounds.centerY();
    dst.set(px + (b.bounds.left - px) * scale, py + (b.bounds.top - py) * scale, px + (b.bounds.right - px) * scale, py + (b.bounds.bottom - py) * scale);
    float melt = b.soft != null ? Math.min(1f, f * 2.2f) : 0f; // how far into the blur
    if (melt < 1f) {
      bitmapPaint.setAlpha((int) (255 * alpha * (1f - melt)));
      c.drawBitmap(b.snapshot, null, dst, bitmapPaint);
    }
    if (melt > 0f) {
      bitmapPaint.setAlpha((int) (255 * alpha * melt));
      c.drawBitmap(b.soft, null, dst, bitmapPaint);
    }
    bitmapPaint.setAlpha(255);
  }

  private void drawSquash (Canvas c, Burst b, float t) {
    float sy, sx = 1f, alpha = 1f;
    if (t <= 0f) {
      sy = 1f; // its turn in the wave hasn't come yet: the message stands as it was
    } else if (t < SQUASH_FLAT) {
      sy = 1f - .96f * SQUASH_CURVE.getInterpolation(t / SQUASH_FLAT);
    } else {
      float f = Math.min(1f, (t - SQUASH_FLAT) / SQUASH_OUT);
      sy = .04f;
      sx = 1f - .8f * SQUASH_CURVE.getInterpolation(f);
      alpha = 1f - f;
    }
    if (alpha <= 0f) return;
    if (b.group != null) {
      float gx = b.group.centerX(), gy = b.group.centerY();
      dst.set(gx + (b.bounds.left - gx) * sx, gy + (b.bounds.top - gy) * sy, gx + (b.bounds.right - gx) * sx, gy + (b.bounds.bottom - gy) * sy);
    } else {
      float cx = b.bounds.centerX(), cy = b.bounds.centerY();
      dst.set(cx - b.bounds.width() / 2f * sx, cy - b.bounds.height() / 2f * sy, cx + b.bounds.width() / 2f * sx, cy + b.bounds.height() / 2f * sy);
    }
    bitmapPaint.setAlpha((int) (255 * alpha));
    c.drawBitmap(b.snapshot, null, dst, bitmapPaint);
    bitmapPaint.setAlpha(255);
  }

  private void drawAll (Canvas c) {
    long now = SystemClock.uptimeMillis();
    for (int k = bursts.size() - 1; k >= 0; k--) {
      Burst b = bursts.get(k);
      float t = (now - b.start) / 1000f;
      if (t >= (b.fade ? FADE_END : b.sink ? SINK_END : TV ? TV_END : SHRINK_END)) {
        b.snapshot.recycle();
        if (b.soft != null && b.soft != b.snapshot) b.soft.recycle();
        bursts.remove(k);
        continue;
      }
      int save = c.save();
      c.translate(0, b.shift);
      if (b.fade) {
        bitmapPaint.setAlpha((int) (255 * Math.max(0f, 1f - t / FADE_END)));
        c.drawBitmap(b.snapshot, null, b.bounds, bitmapPaint);
        bitmapPaint.setAlpha(255);
      } else if (b.sink) {
        drawSink(c, b, t);
      } else if (TV) {
        drawTv(c, b, t);
      } else {
        drawShrink(c, b, t);
      }
      c.restoreToCount(save);
    }
    if (!bursts.isEmpty()) {
      postInvalidateOnAnimation();
    } else {
      setVisibility(INVISIBLE);
    }
  }

  private void drawBurst (Canvas c, Burst b, float t) {
    // the part the front hasn't reached yet — the message itself
    float front = b.bounds.left + b.bounds.width() * Math.min(1f, t / SWEEP);
    // (user 2026-10-30 «можно сделать не так грубо? особенно видно когда удаляешь медиа»): behind the front the message
    // stood as solid 20 px blocks until each grain flew off — a band of mosaic over a photo. The picture now stays sharp
    // until its grains have all left (the front lags by the grains' delay), and a grain shows only once it flies
    float released = b.bounds.left + b.bounds.width() * Math.max(0f, Math.min(1f, (t - JITTER) / SWEEP));
    if (released < b.bounds.right) {
      int save = c.save();
      c.clipRect(released, b.bounds.top, b.bounds.right, b.bounds.bottom);
      c.drawBitmap(b.snapshot, null, b.bounds, bitmapPaint);
      c.restoreToCount(save);
    }
    float s = b.size, dp = Screen.density();
    float wobble = 4f * dp, rise = 80f * dp;
    boolean fast = b.verts != null;
    int q = 0;
    for (int i = 0; i < b.count; i++) {
      float x = b.x0[i], y = b.y0[i];
      if (x >= front) continue; // still under the snapshot
      float u = t - b.release[i];
      int alpha, rgb;
      float gs = s;
      if (u <= 0f) {
        continue; // hasn't left yet: the picture under it still shows
      } else {
        float a = 1f - u / b.life[i];
        if (a <= 0f) continue;
        alpha = (int) (255 * a * (float) Math.sqrt(a));
        x += b.vx[i] * u + (float) Math.sin(u * 9f + b.phase[i]) * wobble;
        y += b.vy[i] * u - rise * u * u + (float) Math.cos(u * 6f + b.phase[i] * 1.7f) * wobble * .5f;
        rgb = u < FLASH ? mix(b.bright[i], b.color[i], u / FLASH) : b.color[i];
        gs = s * (.45f + .35f * a); // grains get smaller as they melt; a bit finer from the start
      }
      int color = (rgb & 0x00ffffff) | (alpha << 24);
      if (fast) {
        int v = q * 8, ci = q * 4;
        b.verts[v] = x; b.verts[v + 1] = y;
        b.verts[v + 2] = x + gs; b.verts[v + 3] = y;
        b.verts[v + 4] = x + gs; b.verts[v + 5] = y + gs;
        b.verts[v + 6] = x; b.verts[v + 7] = y + gs;
        b.colors[ci] = b.colors[ci + 1] = b.colors[ci + 2] = b.colors[ci + 3] = color;
        if (++q == BATCH) {
          flush(c, b, q);
          q = 0;
        }
      } else {
        dustPaint.setColor(color);
        c.drawRect(x, y, x + gs, y + gs, dustPaint);
      }
    }
    if (fast && q > 0) flush(c, b, q);
  }

  private void flush (Canvas c, Burst b, int quads) {
    c.drawVertices(Canvas.VertexMode.TRIANGLES, quads * 8, b.verts, 0, null, 0, b.colors, 0, indices, 0, quads * 6, dustPaint);
  }
}
