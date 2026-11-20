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
import android.os.SystemClock;
import android.view.View;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 (user 2026-11-20, mockups «Анимация записи голосового», variant «Частицы»): while a voice note is recorded,
 * glowing dots fly out of the round button — few and slow in silence, a stream when one speaks loudly. Sliding left to
 * cancel turns them red and pulls them into the timer's red dot (which turns into a bin); sliding up carries them to the
 * lock, and once locked they circle round the button. A cancelled recording ends in a red puff.
 *
 * Cost (user «нагрузки на процессор больше не станет?»): a fixed pool of {@link #MAX} dots — no objects made while
 * drawing, plain circles only (no paths, shadows or blur), 30 frames a second like the button itself, and nothing at all
 * once the recording and its last dots are gone.
 */
public class Tgx101RecordParticles extends View {
  private static final int MAX = 120;
  private static final long FRAME_MS = 33;
  private static final int RED = 0xffff3b36; // the timer's dot

  private final float[] x = new float[MAX], y = new float[MAX], vx = new float[MAX], vy = new float[MAX];
  private final float[] life = new float[MAX], maxLife = new float[MAX], size = new float[MAX];
  private final boolean[] light = new boolean[MAX], burst = new boolean[MAX];
  private int count;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final java.util.Random random = new java.util.Random();

  private boolean active;
  private float cx, cy, radius, expand;
  private float cancel, lock, locked;
  private float targetX, targetY;
  private float level, smooth;
  private long lastFrame;

  public Tgx101RecordParticles (Context context) {
    super(context);
    paint.setStyle(Paint.Style.FILL);
  }

  /** false — a round video is recorded: no dots */
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

  public void setExpand (float expand) {
    if (this.expand != expand) {
      this.expand = expand;
      wake();
    }
  }

  /** @param cancel 0..1 slid to the left; @param lock 0..1 slid up; @param locked 0..1 hands-free */
  public void setGesture (float cancel, float lock, float locked) {
    this.cancel = cancel;
    this.lock = lock;
    this.locked = locked;
  }

  public void setTarget (float x, float y) {
    this.targetX = x;
    this.targetY = y;
  }

  /** the microphone's level, 0..1, from the recorder's thread or the main one */
  public void setLevel (float level) {
    this.level = Math.max(0f, Math.min(1f, level));
    wake();
  }

  public void reset () {
    count = 0;
    level = smooth = 0f;
    lastFrame = 0;
    invalidate();
  }

  /** the recording was cancelled by sliding to the end: a red puff where the button was */
  public void puff () {
    if (!active) return;
    for (int i = 0; i < 40 && count < MAX; i++) {
      double a = random.nextDouble() * Math.PI * 2;
      float v = Screen.dp(60f) + random.nextFloat() * Screen.dp(150f);
      add(cx, cy, (float) Math.cos(a) * v, (float) Math.sin(a) * v, .45f + random.nextFloat() * .35f, true);
    }
    wake();
  }

  private void add (float px, float py, float pvx, float pvy, float plife, boolean red) {
    int i = count++;
    x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy;
    life[i] = 0f; maxLife[i] = plife;
    size[i] = Screen.dp(1.1f) + random.nextFloat() * Screen.dp(1.9f);
    light[i] = random.nextFloat() < .35f;
    burst[i] = red;
  }

  private void wake () {
    if (active && SystemClock.uptimeMillis() - lastFrame > FRAME_MS * 3) {
      postInvalidateOnAnimation();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (!active || (count == 0 && expand <= 0f)) {
      lastFrame = 0;
      return;
    }
    long now = SystemClock.uptimeMillis();
    float dt = lastFrame == 0 ? FRAME_MS / 1000f : Math.min(.064f, (now - lastFrame) / 1000f);
    lastFrame = now;

    // the voice: quick up, slower down, as the recorder's level comes in steps
    float target = level * expand;
    smooth += (target - smooth) * (1f - (float) Math.exp(-dt / (target > smooth ? .035f : .12f)));

    // new dots from the button's edge
    float r = radius * expand;
    if (expand > .01f && r > 0) {
      float n = (6f + 170f * smooth) * dt * expand * (1f - Math.min(1f, cancel * 1.5f) * .6f);
      int m = (int) n + (random.nextFloat() < n - (int) n ? 1 : 0);
      while (m-- > 0 && count < MAX) {
        double a = random.nextDouble() * Math.PI * 2;
        float v = Screen.dp(18f) + Screen.dp(110f) * smooth * (.4f + random.nextFloat() * .6f);
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        add(cx + ca * r, cy + sa * r, ca * v, sa * v, .6f + random.nextFloat() * .6f, false);
      }
    }

    int accent = Theme.getColor(ColorId.circleButtonRegular);
    int lightAccent = ColorUtils.fromToArgb(accent, 0xffffffff, .45f);
    float pull = cancel * 9f * dt, up = lock * (1f - locked) * Screen.dp(260f) * dt, spin = 2.2f * locked * dt, inward = .9f * locked * dt;
    float drag = (float) Math.pow(.985f, dt * 60f);
    int w = 0;
    for (int i = 0; i < count; i++) {
      float l = life[i] + dt;
      if (l > maxLife[i]) continue;
      float px = x[i], py = y[i], pvx = vx[i], pvy = vy[i];
      if (!burst[i]) {
        if (pull > 0f) { pvx += (targetX - px) * pull; pvy += (targetY - py) * pull; }
        if (up > 0f) pvy -= up;
        if (spin > 0f) {
          float dx = px - cx, dy = py - cy;
          pvx += -dy * spin - dx * inward;
          pvy += dx * spin - dy * inward;
        }
      }
      pvx *= drag;
      pvy *= drag;
      px += pvx * dt;
      py += pvy * dt;
      // keep it packed: the live dots move to the front of the pool
      x[w] = px; y[w] = py; vx[w] = pvx; vy[w] = pvy; life[w] = l; maxLife[w] = maxLife[i];
      size[w] = size[i]; light[w] = light[i]; burst[w] = burst[i];
      w++;

      float alpha = 1f - l / maxLife[i];
      int color = burst[i] ? RED : ColorUtils.fromToArgb(light[i] ? lightAccent : accent, RED, Math.min(1f, cancel * 1.4f));
      paint.setColor(ColorUtils.alphaColor(alpha * .9f, color));
      c.drawCircle(px, py, size[i] * (.6f + .4f * alpha), paint);
    }
    count = w;

    if (count > 0 || expand > 0f) {
      postInvalidateDelayed(FRAME_MS);
    } else {
      lastFrame = 0;
    }
  }
}
