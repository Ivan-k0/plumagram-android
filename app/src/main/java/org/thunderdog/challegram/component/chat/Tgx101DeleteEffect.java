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
  private static final long MAX_SNAPSHOT = 1_500_000L;
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
    float shift; // how far the chat has scrolled since — the dust is tied to the messages, not to the screen
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
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q; // drawVertices is hardware accelerated from Android 10
  }

  /**
   * Takes a snapshot of {@code source} (a message view about to be removed) and turns it to dust where it stands.
   * {@code outline} (the bubble, in the view's coordinates) limits the dust to the message itself.
   */
  public void explode (View source, @Nullable Path outline) {
    org.thunderdog.challegram.tool.Tgx101Haptics.play(org.thunderdog.challegram.tool.Tgx101Haptics.CLICK); // TGx101: deleted
    if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0 || !source.isShown() || getWidth() == 0) {
      Tgx101Diag.mark("delete effect: no snapshot (view " + (source == null ? "null" : source.getWidth() + "x" + source.getHeight() + (source.isShown() ? "" : " hidden")) + ", layer " + getWidth() + ")");
      return;
    }
    long t0 = SystemClock.uptimeMillis();
    RectF area = new RectF(0, 0, source.getWidth(), source.getHeight());
    if (outline != null) {
      RectF r = new RectF();
      outline.computeBounds(r, true);
      if (r.width() > 0 && r.height() > 0 && r.intersect(area)) area.set(r);
      else outline = null;
    }
    int w = Math.max(1, Math.round(area.width())), h = Math.max(1, Math.round(area.height()));
    float scale = (float) Math.min(1.0, Math.sqrt(MAX_SNAPSHOT / ((double) w * h)));
    int bw = Math.max(1, Math.round(w * scale)), bh = Math.max(1, Math.round(h * scale));
    Bitmap snapshot;
    try {
      snapshot = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
    } catch (Throwable t) {
      return;
    }
    Canvas sc = new Canvas(snapshot);
    sc.scale(bw / (float) w, bh / (float) h);
    sc.translate(-area.left, -area.top);
    if (outline != null) sc.clipPath(outline);
    source.draw(sc);

    Burst b = new Burst();
    b.snapshot = snapshot;
    source.getLocationInWindow(srcLoc);
    getLocationInWindow(loc);
    float bl = srcLoc[0] - loc[0] + area.left, bt = srcLoc[1] - loc[1] + area.top;
    b.bounds.set(bl, bt, bl + w, bt + h);

    int maxParticles = fastPath() ? MAX_PARTICLES_FAST : MAX_PARTICLES_SLOW;
    b.size = Math.max(Screen.dp(PIXEL_DP), (float) Math.sqrt((double) w * h / maxParticles));
    b.cols = Math.max(1, (int) Math.ceil(w / b.size));
    b.rows = Math.max(1, (int) Math.ceil(h / b.size));
    int n = b.cols * b.rows;
    int[] pixels = new int[n];
    Bitmap small = Bitmap.createScaledBitmap(snapshot, b.cols, b.rows, true); // each pixel — the colour of a dust grain
    small.getPixels(pixels, 0, b.cols, 0, 0, b.cols, b.rows);
    if (small != snapshot) small.recycle();

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
    b.start = SystemClock.uptimeMillis();
    bursts.add(b);
    setVisibility(VISIBLE);
    postInvalidateOnAnimation();
    Tgx101Diag.mark("delete effect: " + count + " pixels of " + Math.round(b.size) + " px, view " + w + "x" + h + ", snapshot " + bw + "x" + bh + ", " + (b.start - t0) + " ms" + (fastPath() ? "" : " (rects)"));
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
    long now = SystemClock.uptimeMillis();
    for (int k = bursts.size() - 1; k >= 0; k--) {
      Burst b = bursts.get(k);
      float t = (now - b.start) / 1000f;
      if (t >= END) {
        b.snapshot.recycle();
        bursts.remove(k);
        continue;
      }
      int save = c.save();
      c.translate(0, b.shift);
      drawBurst(c, b, t);
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
    if (front < b.bounds.right) {
      int save = c.save();
      c.clipRect(front, b.bounds.top, b.bounds.right, b.bounds.bottom);
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
        alpha = 255; // the front passed, the grain hasn't left yet — stands in for the message
        rgb = b.color[i];
      } else {
        float a = 1f - u / b.life[i];
        if (a <= 0f) continue;
        alpha = (int) (255 * a * (float) Math.sqrt(a));
        x += b.vx[i] * u + (float) Math.sin(u * 9f + b.phase[i]) * wobble;
        y += b.vy[i] * u - rise * u * u + (float) Math.cos(u * 6f + b.phase[i] * 1.7f) * wobble * .5f;
        rgb = u < FLASH ? mix(b.bright[i], b.color[i], u / FLASH) : b.color[i];
        gs = s * (.55f + .45f * a); // grains get smaller as they melt
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
