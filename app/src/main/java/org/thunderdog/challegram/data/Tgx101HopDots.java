/*
 * This file is a part of PlumaGram
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
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.os.SystemClock;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * TGx101 (user 2026-11-10, recorded off another app on the Mac: «3 точки перепрыгивают через друг дружку»): the dots
 * standing for a transcription on its way. Three dots in a row; the middle one hops in an arc over the right one while
 * that one slides into its place (0.2 s), then the left one hops over the middle one the same way (0.2 s), the middle one
 * gives a little springy dip (0.12 s), and they rest (0.53 s) — about once a second. A hop is about a dot's height.
 */
public final class Tgx101HopDots {
  private Tgx101HopDots () { }

  private static final long HOP_MS = 200, DIP_MS = 120, CYCLE_MS = 1050;

  /** the room the three dots take */
  public static int width () {
    return Screen.dp(5f) * 2 + Screen.dp(4f);
  }

  /** draws the dots centred on (cx, cy); the caller redraws while they are shown */
  public static void draw (Canvas c, float cx, float cy, int color) {
    float r = Screen.dp(2f), step = Screen.dp(5f), hop = r * 2.2f;
    float x0 = cx - step, x1 = cx, x2 = cx + step;
    float[] xs = {x0, x1, x2}, ys = {cy, cy, cy};
    long t = SystemClock.uptimeMillis() % CYCLE_MS;
    if (t < HOP_MS) {
      float p = ease(t / (float) HOP_MS);
      xs[1] = x1 + (x2 - x1) * p; // hops over the right one
      ys[1] = cy - hop * (float) Math.sin(Math.PI * p);
      xs[2] = x2 + (x1 - x2) * p; // which slides into its place
    } else if (t < HOP_MS * 2) {
      float p = ease((t - HOP_MS) / (float) HOP_MS);
      xs[0] = x0 + (x1 - x0) * p; // the left one hops over the middle one
      ys[0] = cy - hop * (float) Math.sin(Math.PI * p);
      xs[1] = x1 + (x0 - x1) * p;
    } else if (t < HOP_MS * 2 + DIP_MS) {
      float q = (t - HOP_MS * 2) / (float) DIP_MS;
      ys[1] = cy + r * .6f * (float) Math.sin(Math.PI * q); // a springy dip as it lands
    }
    android.graphics.Paint paint = Paints.fillingPaint(color);
    for (int i = 0; i < 3; i++) {
      c.drawCircle(xs[i], ys[i], r, paint);
    }
  }

  private static float ease (float p) {
    return (float) (1 - Math.cos(Math.PI * Math.max(0f, Math.min(1f, p)))) / 2f;
  }
}
