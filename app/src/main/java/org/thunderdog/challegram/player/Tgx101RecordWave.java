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
 * TGx101 (user 2026-11-21, mockups «Анимация записи голосового» → «Волна и стая», Н3): while a voice note is recorded,
 * the recording bar fills with «water» from below — two translucent waves running towards each other. The louder the
 * voice, the higher the water and the more it sways; in silence it settles low. Sliding left to cancel tips it to the
 * left, turns it red and drains it.
 *
 * Drawn behind the bar's own text, 30 frames a second while it shows; two reused paths, nothing made while drawing.
 */
final class Tgx101RecordWave {
  private static final long FRAME_MS = 33;
  private static final int RED = 0xffff3b36;

  private final Path path = new Path();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float level, slow, near;
  private float heard, slowSpeed; // the loudness as heard (attack/release), the water's own speed (a soft spring)
  private float visible, cancel;
  private long lastFrame;

  Tgx101RecordWave () {
    paint.setStyle(Paint.Style.FILL);
  }

  void setLevel (float level) {
    this.level = Math.max(0f, Math.min(1f, level));
  }

  /** @param visible 0..1 the bar shows a voice recording; @param cancel 0..1 slid to the left */
  void setState (View host, float visible, float cancel) {
    boolean wake = this.visible <= 0f && visible > 0f;
    this.visible = visible;
    this.cancel = cancel;
    if (wake && host != null) host.invalidate();
  }

  void reset () {
    level = slow = near = heard = slowSpeed = 0f;
    lastFrame = 0;
  }

  void draw (Canvas c, View host, int width, int height) {
    if (visible <= 0f || width <= 0 || height <= 0) {
      lastFrame = 0;
      slow = near = heard = slowSpeed = 0f;
      return;
    }
    long now = SystemClock.uptimeMillis();
    float dt = lastFrame == 0 ? FRAME_MS : Math.min(64f, now - lastFrame);
    lastFrame = now;
    float target = level * visible;
    // TGx101 (user 2026-11-23 «волна сильно резко поднимается и сильно резко опускается — плавнее, органичнее»): the
    // loudness is heard with an attack and a slower release (a word lifts it in ~0.15 s, silence lets it go over ~0.5 s);
    // the water level follows it on a soft spring (≈1.1 Hz, damped 0.8) — it swells and settles with a little give,
    // instead of chasing every syllable; the sway grows and calms over ~0.25 / 0.6 s
    heard += (target - heard) * (1f - (float) Math.exp(-dt / (target > heard ? 150f : 480f)));
    float sec = dt / 1000f, omega = 2f * (float) Math.PI * 1.1f, zeta = .8f;
    slowSpeed += (omega * omega * (heard - slow) - 2f * zeta * omega * slowSpeed) * sec;
    slow = Math.max(0f, Math.min(1.1f, slow + slowSpeed * sec));
    near += (heard - near) * (1f - (float) Math.exp(-dt / (heard > near ? 250f : 600f)));

    float lvl = height * (.14f + .62f * slow) * visible * (1f - cancel * .85f);
    float surface = height - lvl;
    float amp = Screen.dp(1.5f) + Screen.dp(5f) * near;
    float fade = 1f - cancel * .9f;
    float redness = smooth(.05f, .8f, cancel);
    int color = ColorUtils.fromToArgb(Theme.getColor(ColorId.circleButtonRegular), RED, redness);
    float step = Screen.dp(3f), mid = width / 2f, t = now % 1_000_000L, density = Screen.density();

    for (int layer = 0; layer < 2; layer++) {
      float fq = (layer == 0 ? .021f : .0305f) / density; // radians per dp, as in the mockup
      float speed = layer == 0 ? 1f : -1.3f;
      float phase = (layer == 0 ? .18f : .25f) * 10f;
      float alpha = (layer == 0 ? .22f : .34f) * visible * fade;
      path.rewind();
      path.moveTo(0, height);
      for (float x = 0; x <= width + step; x += step) {
        float xx = Math.min(x, width);
        float y = surface + layer * Screen.dp(2f)
          + (float) Math.sin(xx * fq + t * .004f * speed + phase) * amp
          + (float) Math.sin(xx * .0417f / density - t * .003f * speed) * amp * .4f
          + (xx - mid) * cancel * .22f;
        path.lineTo(xx, y);
      }
      path.lineTo(width, height);
      path.close();
      paint.setColor(ColorUtils.alphaColor(alpha, color));
      c.drawPath(path, paint);
    }
    host.postInvalidateDelayed(16); // 60 frames a second (user 2026-11-21 «очень дёрганно»: 30 were not enough)
  }

  private static float smooth (float a, float b, float t) {
    if (t <= a) return 0f;
    if (t >= b) return 1f;
    float x = (t - a) / (b - a);
    return x * x * (3f - 2f * x);
  }
}
