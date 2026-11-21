/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 (user 2026-11-21, mockups «Анимация записи голосового» → «Волна и стая»: «каждые 13 секунд вылетает по одной
 * птичке, и они кружатся рядом (выше или ниже по мере громкости). После отправки — улетают вверх. Если отмена, то вниз
 * (краснеют)»). Every 13 seconds of a voice note one small bird flies out of the round button and circles beside it,
 * up and to the left — the flock rises when the voice is loud and sinks towards the field when it is quiet. Sliding left
 * to cancel turns them red. Sent, they gather into a small wedge and fly off to the right together (user 2026-11-21:
 * «не отправлять их через весь экран», idea 2); cancelled, they redden, sink 40–50 dp and fade.
 *
 * At most {@link #MAX} birds, two strokes each, one reused path; 60 frames a second while any bird is on screen
 * (30 looked jerky — user 2026-11-21 «очень дёрганно»).
 */
public class Tgx101RecordBirds extends View {
  private static final int MAX = 14;
  private static final long EVERY_MS = 13_000L;
  private static final long FRAME_MS = 16;
  private static final float FALL_S = .55f;
  private static final int RED = 0xffff3b36;
  private static final int CIRCLE = 0, UP = 1, DOWN = 2;

  private final float[] x = new float[MAX], y = new float[MAX], vx = new float[MAX], vy = new float[MAX];
  private final float[] angle = new float[MAX], speed = new float[MAX], rx = new float[MAX], ry = new float[MAX];
  private final float[] lift = new float[MAX], wing = new float[MAX], size = new float[MAX], age = new float[MAX];
  private final float[] slotX = new float[MAX], slotY = new float[MAX], gone = new float[MAX];
  private final int[] state = new int[MAX];
  // the wedge flying away: where its leader is, how long it has flown; heading right and 15° up
  private float flockX, flockY, flockAge;
  private static final float HEAD_X = .966f, HEAD_Y = -.259f;
  private int count, released;
  private boolean catchUp; // after a pause: the birds of the time already recorded don't all come back at once

  private final Path path = new Path();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final java.util.Random random = new java.util.Random();

  private boolean active;
  private float cx, cy, radius;
  private float level, slow, cancel;
  private long lastFrame;

  public Tgx101RecordBirds (Context context) {
    super(context);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
  }

  /** false — a round video is recorded: no birds */
  public void setActive (boolean active) {
    if (this.active != active) {
      this.active = active;
      if (!active) reset();
    }
  }

  public void setCircle (float cx, float cy, float radius) {
    this.cx = cx;
    this.cy = cy;
    this.radius = radius;
  }

  public void setLevel (float level) {
    this.level = Math.max(0f, Math.min(1f, level));
  }

  public void setCancel (float cancel) {
    this.cancel = cancel;
  }

  /** the recording's length so far: one more bird for every 13 seconds of it */
  public void setElapsed (long ms) {
    if (!active) return;
    int want = (int) (ms / EVERY_MS);
    if (catchUp) {
      catchUp = false;
      released = want;
      return;
    }
    while (released < want) {
      released++;
      if (count < MAX) add();
    }
  }

  /** the recording ended: sent (or kept for a look) — a wedge flying off to the right; cancelled — down a little, red, fading */
  public void release (boolean cancelled) {
    int n = 0;
    float sx = 0f, sy = 0f;
    for (int i = 0; i < count; i++) {
      if (state[i] == CIRCLE) { sx += x[i]; sy += y[i]; n++; }
    }
    if (n > 0 && !cancelled) {
      // the leader at the front, the others two by two behind it, on both sides
      flockX = sx / n + HEAD_X * Screen.dp(20f);
      flockY = sy / n + HEAD_Y * Screen.dp(20f);
      flockAge = 0f;
    }
    int slot = 0;
    for (int i = 0; i < count; i++) {
      if (state[i] != CIRCLE) continue;
      gone[i] = 0f;
      if (cancelled) {
        state[i] = DOWN;
        vx[i] = (random.nextFloat() - .5f) * Screen.dp(30f);
        vy[i] = -Screen.dp(10f);
      } else {
        state[i] = UP;
        int row = (slot + 1) / 2;
        float side = slot == 0 ? 0f : (slot % 2 == 1 ? 1f : -1f);
        float back = -row * Screen.dp(16f), across = side * row * Screen.dp(11f);
        slotX[i] = HEAD_X * back - HEAD_Y * across;
        slotY[i] = HEAD_Y * back + HEAD_X * across;
        slot++;
      }
    }
    catchUp = true;
    level = 0f;
    if (count > 0) wake();
  }

  /** a new recording: no birds left over */
  public void reset () {
    count = 0;
    released = 0;
    catchUp = false;
    level = slow = cancel = 0f;
    lastFrame = 0;
    invalidate();
  }

  private void add () {
    int i = count++;
    double a = -Math.PI / 2 + (random.nextFloat() - .5f) * 1.2f;
    x[i] = cx + (float) Math.cos(a) * radius;
    y[i] = cy + (float) Math.sin(a) * radius;
    vx[i] = vy[i] = 0f;
    angle[i] = random.nextFloat() * (float) (Math.PI * 2);
    speed[i] = (.9f + random.nextFloat() * .7f) * (random.nextBoolean() ? 1f : -1f);
    rx[i] = Screen.dp(24f) + random.nextFloat() * Screen.dp(30f);
    ry[i] = Screen.dp(10f) + random.nextFloat() * Screen.dp(10f);
    lift[i] = random.nextFloat() * Screen.dp(16f);
    wing[i] = random.nextFloat() * (float) (Math.PI * 2);
    size[i] = Screen.dp(7f) + random.nextFloat() * Screen.dp(3f);
    age[i] = 0f;
    state[i] = CIRCLE;
    wake();
  }

  private void wake () {
    if (SystemClock.uptimeMillis() - lastFrame > FRAME_MS * 3) {
      postInvalidateOnAnimation();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (count == 0) {
      lastFrame = 0;
      return;
    }
    long now = SystemClock.uptimeMillis();
    float dt = lastFrame == 0 ? FRAME_MS / 1000f : Math.max(.001f, Math.min(.064f, (now - lastFrame) / 1000f));
    lastFrame = now;
    slow += (level - slow) * (1f - (float) Math.exp(-dt / .26f));

    int base = ColorUtils.fromToArgb(Theme.getColor(ColorId.circleButtonRegular), Theme.textAccentColor(), .12f);
    float redness = Math.min(1f, cancel * 1.25f);
    float ease = 1f - (float) Math.exp(-dt / .22f), easeFirst = 1f - (float) Math.exp(-dt / .35f);
    float centerX = cx - Screen.dp(56f);
    float right = getWidth() + Screen.dp(40f), top = -Screen.dp(40f);
    // the wedge: a moment to gather, then it speeds away
    flockAge += dt;
    float flockSpeed = Screen.dp(30f) + Screen.dp(900f) * Math.max(0f, flockAge - .25f);
    flockX += HEAD_X * flockSpeed * dt;
    flockY += HEAD_Y * flockSpeed * dt;
    float follow = 1f - (float) Math.exp(-dt / (flockAge < .3f ? .12f : .05f));
    int w = 0;
    for (int i = 0; i < count; i++) {
      float px = x[i], py = y[i], pvx = vx[i], pvy = vy[i];
      float a = angle[i], l = age[i] + dt, f = wing[i];
      int st = state[i];
      if (st == CIRCLE) {
        a += speed[i] * dt;
        float tx = centerX + (float) Math.cos(a) * rx[i];
        float ty = cy - radius - Screen.dp(14f) - Screen.dp(80f) * slow - lift[i] + cancel * Screen.dp(40f);
        ty += (float) Math.sin(a) * ry[i];
        float e = l < 1f ? easeFirst : ease;
        float nx = px + (tx - px) * e, ny = py + (ty - py) * e;
        pvx = (nx - px) / dt;
        pvy = (ny - py) / dt;
        px = nx;
        py = ny;
        f += 11f * dt;
      } else if (st == UP) {
        float tx = flockX + slotX[i], ty = flockY + slotY[i];
        float nx = px + (tx - px) * follow, ny = py + (ty - py) * follow;
        pvx = (nx - px) / dt;
        pvy = (ny - py) / dt;
        px = nx;
        py = ny;
        f += 15f * dt;
        if (px > right || py < top) continue;
      } else {
        pvy += Screen.dp(300f) * dt;
        px += pvx * dt;
        py += pvy * dt;
        f += 4f * dt;
        if (gone[i] + dt > FALL_S) continue;
      }
      float g = st == CIRCLE ? 0f : gone[i] + dt;
      x[w] = px; y[w] = py; vx[w] = pvx; vy[w] = pvy; angle[w] = a; speed[w] = speed[i];
      rx[w] = rx[i]; ry[w] = ry[i]; lift[w] = lift[i]; wing[w] = f; size[w] = size[i]; age[w] = l; state[w] = st;
      slotX[w] = slotX[i]; slotY[w] = slotY[i]; gone[w] = g;
      w++;

      int color = st == DOWN ? RED : ColorUtils.fromToArgb(base, RED, redness);
      float alpha = Math.min(1f, l / .15f) * (st == DOWN ? 1f - g / FALL_S : 1f);
      float s = size[i];
      float flap = (float) Math.sin(f) * (st == DOWN ? .3f : 1f);
      float tilt = st == UP ? .35f : Math.max(-.5f, Math.min(.5f, pvx / Screen.dp(500f)));
      paint.setColor(ColorUtils.alphaColor(alpha, color));
      paint.setStrokeWidth(Math.max(Screen.dp(1.2f), s * .28f));
      c.save();
      c.translate(px, py);
      c.rotate((float) Math.toDegrees(tilt));
      path.rewind();
      path.moveTo(-s, -s * .55f * flap);
      path.quadTo(-s * .45f, -s * .6f * flap - s * .25f, 0, 0);
      path.quadTo(s * .45f, -s * .6f * flap - s * .25f, s, -s * .55f * flap);
      c.drawPath(path, paint);
      paint.setStyle(Paint.Style.FILL);
      c.drawCircle(0, s * .05f, Math.max(Screen.dp(1f), s * .17f), paint);
      paint.setStyle(Paint.Style.STROKE);
      c.restore();
    }
    count = w;
    if (count > 0) {
      postInvalidateOnAnimation();
    } else {
      lastFrame = 0;
    }
  }
}
