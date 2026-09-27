/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/tgx101-android)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;


/**
 * TGx101: call screen background. A dark glow in the app icon colours, centred on the photo,
 * a faint tiled pattern of small paper planes and a thin ring around the photo.
 * Everything is drawn from a gradient and one small tile, nothing animates.
 */
public class Tgx101CallBackground extends View {
  private static final int COLOR_CENTER = 0xff26384b;
  private static final int COLOR_MIDDLE = 0xff151f2a;
  private static final int COLOR_EDGE = 0xff0b1118;
  private static final int PATTERN_COLOR = 0x1affffff; // ~10 %
  private static final int RING_COLOR = 0x10ffffff;

  private static Bitmap patternTile;

  private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint patternPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private @Nullable View photoView;
  private float glowCenterX = -1, glowCenterY = -1;
  private int glowWidth, glowHeight;

  public Tgx101CallBackground (Context context) {
    super(context);
    if (Settings.instance().getCallPattern() == Settings.CALL_PATTERN_PAPER_PLANES) {
      patternPaint.setColor(PATTERN_COLOR);
      patternPaint.setShader(new BitmapShader(getPatternTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setStrokeWidth(Screen.dp(4f));
    ringPaint.setColor(RING_COLOR);
  }

  public void setPhotoView (@Nullable View photoView) {
    this.photoView = photoView;
    invalidate();
  }

  @Override
  protected void onDraw (Canvas c) {
    final int width = getWidth(), height = getHeight();
    float cx = width / 2f, cy = height * .35f, radius = 0;
    if (photoView != null && photoView.getWidth() > 0) {
      radius = Math.min(photoView.getWidth(), photoView.getHeight()) / 2f;
      cx = photoView.getLeft() + photoView.getWidth() / 2f;
      cy = photoView.getTop() + photoView.getHeight() / 2f;
    }
    if (cx != glowCenterX || cy != glowCenterY || width != glowWidth || height != glowHeight) {
      glowCenterX = cx;
      glowCenterY = cy;
      glowWidth = width;
      glowHeight = height;
      float glowRadius = Math.max(Math.max(width, height) * .75f, 1f);
      glowPaint.setShader(new RadialGradient(cx, cy, glowRadius, new int[] {COLOR_CENTER, COLOR_MIDDLE, COLOR_EDGE}, new float[] {0f, .45f, 1f}, Shader.TileMode.CLAMP));
    }
    c.drawRect(0, 0, width, height, glowPaint);
    if (patternPaint.getShader() != null) {
      c.drawRect(0, 0, width, height, patternPaint);
    }
    if (radius > 0 && photoView.getAlpha() > 0f) {
      ringPaint.setAlpha((int) (Color.alpha(RING_COLOR) * photoView.getAlpha()));
      c.drawCircle(cx, cy, radius + ringPaint.getStrokeWidth() / 2f, ringPaint);
    }
  }

  // Pattern: ten kinds of paper planes with trails of different kinds and lengths.
  private static final int TRAIL_NONE = 0, TRAIL_DOTS = 1, TRAIL_DASHES = 2, TRAIL_WAVE = 3, TRAIL_LOOP = 4;

  // Generated from the pattern preview: {closed, x0, y0, x1, y1, ...}, nose points to +x, length about 1
  private static final float[][][] PLANES = {
    {{1f, 0.5f, 0f, -0.5f, -0.3f, -0.35f, 0f, -0.5f, 0.3f}, {0f, 0.5f, 0f, -0.35f, 0f}},
    {{1f, -0.5f, 0f, 0.5f, -0.42f, 0.28f, 0.42f, 0.05f, 0.16f}, {0f, 0.05f, 0.16f, 0.5f, -0.42f}, {0f, 0.05f, 0.16f, 0.02f, 0.4f}},
    {{1f, 0.45f, 0f, -0.15f, -0.48f, -0.45f, -0.45f, -0.3f, 0f, -0.45f, 0.45f, -0.15f, 0.48f}, {0f, 0.45f, 0f, -0.3f, 0f}, {0f, 0.45f, 0f, -0.38f, -0.22f}, {0f, 0.45f, 0f, -0.38f, 0.22f}},
    {{1f, 0.5f, 0f, -0.5f, -0.2f, -0.5f, 0.04f}, {1f, 0.25f, 0.01f, -0.42f, 0.26f, -0.5f, 0.04f}},
    {{1f, 0.5f, -0.08f, -0.42f, -0.46f, -0.2f, -0.04f}, {1f, 0.5f, -0.08f, -0.2f, -0.04f, -0.36f, 0.34f}},
    {{1f, 0.55f, 0f, -0.45f, -0.15f, -0.3f, 0f, -0.45f, 0.15f}, {0f, 0.55f, 0f, -0.3f, 0f}},
    {{1f, 0.5f, 0f, -0.45f, -0.32f, -0.32f, 0f, -0.45f, 0.32f}, {0f, 0.5f, 0f, -0.32f, 0f}, {0f, -0.45f, -0.32f, -0.56f, -0.44f}, {0f, -0.45f, 0.32f, -0.56f, 0.44f}},
    {{1f, 0.38f, -0.08f, -0.5f, -0.36f, -0.34f, 0f, -0.5f, 0.36f, 0.38f, 0.08f}, {0f, 0.38f, 0f, -0.34f, 0f}},
    {{1f, 0.42f, 0f, -0.36f, -0.5f, -0.24f, 0f, -0.36f, 0.5f}, {0f, 0.42f, 0f, -0.3f, -0.25f}, {0f, 0.42f, 0f, -0.3f, 0.25f}},
    {{1f, -0.5f, 0.02f, 0.5f, -0.3f, 0.3f, 0.34f, 0.08f, 0.14f}, {0f, 0.08f, 0.14f, 0.5f, -0.3f}, {0f, 0.08f, 0.14f, -0.18f, 0.12f}, {0f, -0.5f, 0.02f, -0.56f, -0.1f}},
  };
  // {x, y, plane, size, angle (radians), trail kind, trail length, trail bend}; x, y, size are fractions of the tile
  private static final float[][] ITEMS = {
    {0.124f, 0.087f, 5f, 0.069f, -0.006f, 2f, 1.925f, -0.143f},
    {0.082f, 0.278f, 5f, 0.089f, -1.073f, 0f, 2.052f, -0.153f},
    {0.087f, 0.386f, 5f, 0.06f, 0.224f, 0f, 1.201f, -0.039f},
    {0.064f, 0.544f, 9f, 0.077f, -0.003f, 0f, 1.174f, 0.191f},
    {0.056f, 0.708f, 6f, 0.05f, -0.365f, 1f, 1.808f, 0.018f},
    {0.07f, 0.903f, 4f, 0.073f, -0.924f, 1f, 1.832f, -0.233f},
    {0.276f, 0.074f, 5f, 0.086f, -0.176f, 0f, 0.9f, -0.167f},
    {0.231f, 0.287f, 8f, 0.077f, -1.189f, 4f, 1.238f, -0.208f},
    {0.253f, 0.437f, 7f, 0.084f, -0.986f, 0f, 1.436f, 0.103f},
    {0.246f, 0.597f, 2f, 0.087f, -0.838f, 4f, 1.103f, 0.216f},
    {0.289f, 0.714f, 2f, 0.085f, -0.592f, 2f, 1.204f, -0.216f},
    {0.3f, 0.913f, 3f, 0.061f, 0.284f, 2f, 1.391f, -0.205f},
    {0.387f, 0.045f, 5f, 0.06f, -0.695f, 0f, 0.85f, 0.239f},
    {0.43f, 0.286f, 7f, 0.076f, -1.12f, 0f, 0.422f, -0.082f},
    {0.433f, 0.422f, 8f, 0.063f, -1.167f, 4f, 0.937f, -0.183f},
    {0.456f, 0.573f, 0f, 0.08f, -0.271f, 1f, 1.622f, -0.142f},
    {0.401f, 0.737f, 7f, 0.084f, -0.836f, 3f, 0.617f, 0.086f},
    {0.433f, 0.922f, 2f, 0.068f, -0.199f, 4f, 0.982f, -0.1f},
    {0.557f, 0.077f, 7f, 0.089f, 0.152f, 1f, 1.723f, -0.127f},
    {0.607f, 0.244f, 0f, 0.061f, 0.079f, 2f, 2.187f, 0.225f},
    {0.631f, 0.429f, 0f, 0.051f, 0.469f, 4f, 1.25f, 0.084f},
    {0.548f, 0.608f, 0f, 0.051f, 0.366f, 4f, 1.108f, 0.052f},
    {0.631f, 0.78f, 5f, 0.071f, -1.069f, 0f, 1.644f, -0.015f},
    {0.534f, 0.902f, 8f, 0.054f, -0.782f, 1f, 0.596f, -0.154f},
    {0.792f, 0.112f, 3f, 0.068f, -1.132f, 0f, 0.71f, -0.155f},
    {0.779f, 0.268f, 3f, 0.054f, -0.25f, 2f, 2.095f, -0.244f},
    {0.758f, 0.415f, 2f, 0.054f, 0.579f, 2f, 2.027f, -0.026f},
    {0.799f, 0.594f, 4f, 0.066f, -0.959f, 2f, 1.683f, -0.22f},
    {0.706f, 0.71f, 4f, 0.059f, 0.102f, 1f, 1.632f, -0.056f},
    {0.78f, 0.954f, 5f, 0.078f, -0.768f, 2f, 1.082f, -0.055f},
    {0.887f, 0.048f, 2f, 0.07f, -0.324f, 0f, 1.245f, -0.153f},
    {0.942f, 0.219f, 0f, 0.059f, -0.254f, 1f, 1.764f, -0.233f},
    {0.926f, 0.433f, 9f, 0.065f, 0.226f, 1f, 0.631f, 0.219f},
    {0.893f, 0.631f, 7f, 0.077f, -1.035f, 2f, 2.081f, 0.081f},
    {0.964f, 0.785f, 6f, 0.052f, -1.098f, 1f, 1.268f, -0.249f},
    {0.906f, 0.877f, 2f, 0.067f, -0.01f, 0f, 1.28f, 0.123f},
  };

  /** A seamless tile of paper planes. Opaque strokes on an alpha-only bitmap, tinted by the paint colour. */
  private static Bitmap getPatternTile () {
    if (patternTile != null) {
      return patternTile;
    }
    final int tileSize = Screen.dp(240f);
    Bitmap bitmap = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ALPHA_8);
    Canvas c = new Canvas(bitmap);
    Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeJoin(Paint.Join.ROUND);
    stroke.setStrokeCap(Paint.Cap.ROUND);
    stroke.setStrokeWidth(Screen.dp(1.3f));
    stroke.setColor(0xffffffff);
    Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    fill.setColor(0xffffffff);
    Path path = new Path();
    for (float[] item : ITEMS) {
      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          c.save();
          c.translate((item[0] + dx) * tileSize, (item[1] + dy) * tileSize);
          c.rotate((float) Math.toDegrees(item[4]));
          drawPlane(c, path, stroke, fill, item, item[3] * tileSize);
          c.restore();
        }
      }
    }
    patternTile = bitmap;
    return bitmap;
  }

  private static void drawPlane (Canvas c, Path path, Paint stroke, Paint fill, float[] item, float size) {
    path.rewind();
    for (float[] line : PLANES[(int) item[2]]) {
      path.moveTo(line[1] * size, line[2] * size);
      for (int i = 3; i < line.length; i += 2) {
        path.lineTo(line[i] * size, line[i + 1] * size);
      }
      if (line[0] == 1f) {
        path.close();
      }
    }
    c.drawPath(path, stroke);

    final int kind = (int) item[5];
    if (kind == TRAIL_NONE) {
      return;
    }
    final float length = item[6], bend = item[7];
    final int steps = 60;
    final float dotStep = .14f * size, dashLength = .16f * size, gapLength = .11f * size;
    float prevX = 0, prevY = 0, travelled = 0;
    boolean dashOn = true;
    path.rewind();
    for (int i = 0; i <= steps; i++) {
      float t = i / (float) steps;
      float x = -.6f - t * length;
      float y = bend * t * t * length;
      if (kind == TRAIL_WAVE) {
        y += .07f * (float) Math.sin(t * length * 10);
      } else if (kind == TRAIL_LOOP) {
        x += .22f * (float) Math.sin(2 * Math.PI * t);
        y += .22f * (1f - (float) Math.cos(2 * Math.PI * t));
      }
      x *= size;
      y *= size;
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        travelled += (float) Math.hypot(x - prevX, y - prevY);
        if (kind == TRAIL_DOTS || kind == TRAIL_LOOP) {
          if (travelled >= dotStep) {
            travelled = 0;
            c.drawCircle(x, y, .035f * size, fill);
          }
        } else {
          if (dashOn) {
            path.lineTo(x, y);
          }
          if (travelled >= (dashOn ? dashLength : gapLength)) {
            dashOn = !dashOn;
            travelled = 0;
            path.moveTo(x, y);
          }
        }
      }
      prevX = x;
      prevY = y;
    }
    if (kind == TRAIL_DASHES || kind == TRAIL_WAVE) {
      c.drawPath(path, stroke);
    }
  }
}
