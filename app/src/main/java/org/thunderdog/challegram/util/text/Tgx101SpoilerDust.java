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
package org.thunderdog.challegram.util.text;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Screen;

import java.util.Random;

/**
 * TGx101 (user 2026-10-28 «как выглядит скрытый текст — посмотри на этот ужас»): hidden text was a solid block per piece
 * of a line (Telegram X left «TODO proper particles» there). Now, as in the official app, fine dust of the text's colour
 * drifting over the words: one tile of random dots drawn twice, the two layers sliding different ways — a shimmer for
 * the price of two rectangles, the same on every Android.
 */
public final class Tgx101SpoilerDust {
  private Tgx101SpoilerDust () { }

  private static @Nullable Bitmap tile;
  private static Paint near, far;
  private static final Matrix matrix = new Matrix();
  private static int lastColor;

  private static void ensure () {
    if (tile != null) return;
    int size = Math.max(32, Screen.dp(56f));
    Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    Random random = new Random(101);
    float step = Screen.dp(4.2f); // sparse: the words' shape must not show through
    int count = Math.round(size * size / (step * step));
    for (int i = 0; i < count; i++) {
      float x = random.nextFloat() * size, y = random.nextFloat() * size;
      float r = Screen.dp(.35f + random.nextFloat() * .4f);
      dot.setColor(0xffffffff);
      dot.setAlpha(70 + random.nextInt(186));
      // drawn three times near the edges so the tile repeats without seams
      for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
        c.drawCircle(x + dx * size, y + dy * size, r, dot);
      }
    }
    tile = b;
    near = new Paint(Paint.FILTER_BITMAP_FLAG);
    near.setShader(new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    far = new Paint(Paint.FILTER_BITMAP_FLAG);
    far.setShader(new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
  }

  /** Draws the dust inside {@code path} (bounds {@code bounds}); {@code alpha} 0…1. Returns true while it moves */
  public static boolean draw (Canvas c, Path path, RectF bounds, int color, float alpha) {
    // clipping to a path is drawn by the GPU only from Android 4.3 — older ones keep the plain blocks
    if (alpha <= 0f || bounds.isEmpty() || android.os.Build.VERSION.SDK_INT < 18) return false;
    try {
      ensure();
    } catch (Throwable t) {
      return false;
    }
    if (lastColor != color) {
      lastColor = color;
      PorterDuffColorFilter filter = new PorterDuffColorFilter(color | 0xff000000, PorterDuff.Mode.SRC_IN);
      near.setColorFilter(filter);
      far.setColorFilter(filter);
    }
    float t = (SystemClock.uptimeMillis() % 600000L) / 1000f;
    float dp = Screen.density();
    int a = Math.round(alpha * (color >>> 24) * .8f);
    int save = c.save();
    c.clipPath(path);
    matrix.setTranslate(t * 9f * dp, -t * 6f * dp);
    near.getShader().setLocalMatrix(matrix);
    near.setAlpha(a);
    c.drawRect(bounds, near);
    matrix.setTranslate(-t * 7f * dp + Screen.dp(17f), t * 8f * dp + Screen.dp(23f));
    far.getShader().setLocalMatrix(matrix);
    far.setAlpha(Math.round(a * .7f));
    c.drawRect(bounds, far);
    c.restoreToCount(save);
    return true;
  }
}
