/*
 * This file is a part of PlumaGram (a Telegram X fork)
 */
package org.thunderdog.challegram.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Screen;

/**
 * TGx101 (user 2026-11-28, variant 4 of five: «иконка меняется плавно на одну стрелочку, а вторая стрелка превращается в
 * цифру 1 — то будет играть один трек»): the shuffle sign morphing into «one track». At 0 two crossing arrows; at 1 the
 * rising arrow lies straight along the top, the lower arrow's head folds into a «1» and its tail goes. Drawn on a
 * 24-unit grid, stroked; tinted by the ImageView's colour filter as the round_shuffle icon was
 */
public class Tgx101ShuffleDrawable extends Drawable {
  private static final int N = 24;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float[] aFrom = sample(rising(), N), aTo = sample(line(3, 7, 21, 7), N);
  private final float[] tailB = sample(lowerTail(), N), stubB = sample(upperStub(), N);
  private final float[] headA = {18, 4, 21, 7, 18, 10};
  private final float[] headB = {18, 14, 21, 17, 18, 20}, one = {10.5f, 14.5f, 12.5f, 13f, 12.5f, 20f};
  private final Path path = new Path();
  private float factor;
  private final int size = Screen.dp(24f);

  public Tgx101ShuffleDrawable () {
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    paint.setColor(0xffffffff);
  }

  /** 0 shuffle … 1 one track */
  public void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      invalidateSelf();
    }
  }

  public float getFactor () {
    return factor;
  }

  private static Path rising () {
    Path p = new Path();
    p.moveTo(3, 17);
    p.lineTo(6.5f, 17);
    p.cubicTo(8.5f, 17, 9.7f, 16, 10.8f, 14.4f);
    p.lineTo(13.2f, 9.6f);
    p.cubicTo(14.3f, 8, 15.5f, 7, 17.5f, 7);
    p.lineTo(21, 7);
    return p;
  }

  private static Path upperStub () {
    Path p = new Path();
    p.moveTo(3, 7);
    p.lineTo(6.5f, 7);
    p.cubicTo(7.9f, 7, 8.9f, 7.5f, 9.7f, 8.4f);
    return p;
  }

  private static Path lowerTail () {
    Path p = new Path();
    p.moveTo(14.3f, 15.6f);
    p.cubicTo(15.1f, 16.5f, 16.1f, 17, 17.5f, 17);
    p.lineTo(21, 17);
    return p;
  }

  private static Path line (float x1, float y1, float x2, float y2) {
    Path p = new Path();
    p.moveTo(x1, y1);
    p.lineTo(x2, y2);
    return p;
  }

  private static float[] sample (Path p, int n) {
    PathMeasure m = new PathMeasure(p, false);
    float len = m.getLength();
    float[] out = new float[n * 2], pos = new float[2];
    for (int i = 0; i < n; i++) {
      m.getPosTan(len * i / (n - 1), pos, null);
      out[i * 2] = pos[0];
      out[i * 2 + 1] = pos[1];
    }
    return out;
  }

  private void polyline (float[] from, @Nullable float[] to, float f, float scale, float cx, float cy, int count) {
    path.reset();
    for (int i = 0; i < count; i++) {
      float x = to != null ? from[i * 2] + (to[i * 2] - from[i * 2]) * f : from[i * 2];
      float y = to != null ? from[i * 2 + 1] + (to[i * 2 + 1] - from[i * 2 + 1]) * f : from[i * 2 + 1];
      x = cx + (x - 12f) * scale;
      y = cy + (y - 12f) * scale;
      if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
    }
  }

  @Override
  public void draw (@NonNull Canvas c) {
    Rect b = getBounds();
    float scale = Math.min(b.width(), b.height()) / 24f;
    float cx = b.exactCenterX(), cy = b.exactCenterY();
    float f = factor;
    int alpha = paint.getAlpha();
    paint.setStrokeWidth(1.9f * scale);
    // the rising arrow lies straight along the top; its head stays
    polyline(aFrom, aTo, f, scale, cx, cy, N);
    c.drawPath(path, paint);
    polyline(headA, null, 0, scale, cx, cy, 3);
    c.drawPath(path, paint);
    // the lower arrow: its head folds into «1», its tail and the upper stub go
    polyline(headB, one, f, scale, cx, cy, 3);
    c.drawPath(path, paint);
    if (f < 1f) {
      float gone = Math.max(0f, 1f - f * 1.6f);
      paint.setAlpha((int) (alpha * gone));
      int keep = Math.max(2, Math.round(N * gone));
      polyline(tailB, null, 0, scale, cx, cy, keep);
      c.drawPath(path, paint);
      polyline(stubB, null, 0, scale, cx, cy, keep);
      c.drawPath(path, paint);
      paint.setAlpha(alpha);
    }
  }

  @Override
  public int getIntrinsicWidth () {
    return size;
  }

  @Override
  public int getIntrinsicHeight () {
    return size;
  }

  @Override
  public void setAlpha (int alpha) {
    paint.setAlpha(alpha);
    invalidateSelf();
  }

  @Override
  public void setColorFilter (@Nullable ColorFilter colorFilter) {
    paint.setColorFilter(colorFilter);
    invalidateSelf();
  }

  @Override
  @SuppressWarnings("deprecation")
  public int getOpacity () {
    return PixelFormat.TRANSLUCENT;
  }
}
