/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.thunderdog.challegram.mediaview.paint.widget;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.mediaview.paint.PaintMode;
import org.thunderdog.challegram.mediaview.paint.SimpleDrawing;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

/**
 * TGx101 (user 2026-10-13, photo editor mockup variant Б): the row of tools over the colour strip — five brushes, a shape
 * (hold to pick which), an eraser. Each cell draws a small sample of what it does.
 */
public class Tgx101PaintToolsView extends View {
  public static final int TOOL_PEN = 0, TOOL_MARKER = 1, TOOL_PENCIL = 2, TOOL_NEON = 3, TOOL_FREE_ARROW = 4, TOOL_SHAPE = 5, TOOL_MOSAIC = 6, TOOL_ERASER = 7;
  // user 2026-10-14 09:16 «убрать карандаш» (and room for the rest): the row shows these, in this order
  private static final int[] SHOWN = {TOOL_PEN, TOOL_MARKER, TOOL_NEON, TOOL_FREE_ARROW, TOOL_SHAPE, TOOL_MOSAIC, TOOL_ERASER};
  private static final int COUNT = SHOWN.length;

  public interface Listener {
    void onToolSelected (int tool, int paintMode, int brushStyle);
    void onShapePickerRequested (View anchor);
  }

  private final String[] labels;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final Path path = new Path();
  private final GestureDetector detector;
  private Listener listener;
  private int selected = TOOL_PEN;
  private int shapeMode = PaintMode.RECTANGLE;
  private int color = 0xffffffff;

  public Tgx101PaintToolsView (Context context, String[] labels) {
    super(context);
    this.labels = labels;
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    text.setTypeface(Fonts.getRobotoRegular());
    text.setTextSize(Screen.dp(9f));
    text.setTextAlign(Paint.Align.CENTER);
    detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
      @Override
      public boolean onDown (@NonNull MotionEvent e) {
        return true;
      }

      @Override
      public boolean onSingleTapUp (@NonNull MotionEvent e) {
        select(indexAt(e.getX()), true);
        return true;
      }

      @Override
      public void onLongPress (@NonNull MotionEvent e) {
        int i = indexAt(e.getX());
        if (i == TOOL_SHAPE && listener != null) {
          performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
          if (selected != TOOL_SHAPE) select(TOOL_SHAPE, true); // already the shape: the shape just drawn stays picked (for «Fill»)
          listener.onShapePickerRequested(Tgx101PaintToolsView.this);
        }
      }
    });
  }

  public void setListener (Listener listener) {
    this.listener = listener;
  }

  public void setBrushColor (int color) {
    if (this.color != color) {
      this.color = color;
      invalidate();
    }
  }

  public void setShapeMode (int mode) {
    this.shapeMode = mode;
    select(TOOL_SHAPE, true);
  }

  public int getSelected () {
    return selected;
  }

  private int indexAt (float x) {
    float cell = getWidth() / (float) COUNT;
    return SHOWN[Math.max(0, Math.min(COUNT - 1, (int) (x / cell)))];
  }

  public void select (int tool, boolean notify) {
    selected = tool;
    invalidate();
    if (notify && listener != null) {
      listener.onToolSelected(tool, modeOf(tool), styleOf(tool));
    }
  }

  public int modeOf (int tool) {
    return tool == TOOL_SHAPE ? shapeMode : PaintMode.PATH;
  }

  public static int styleOf (int tool) {
    switch (tool) {
      case TOOL_MARKER: return SimpleDrawing.STYLE_MARKER;
      case TOOL_PENCIL: return SimpleDrawing.STYLE_PENCIL;
      case TOOL_NEON: return SimpleDrawing.STYLE_NEON;
      case TOOL_FREE_ARROW: return SimpleDrawing.STYLE_FREE_ARROW;
      case TOOL_MOSAIC: return SimpleDrawing.STYLE_MOSAIC;
      case TOOL_ERASER: return SimpleDrawing.STYLE_ERASER;
    }
    return SimpleDrawing.STYLE_PEN;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return detector.onTouchEvent(e) || super.onTouchEvent(e);
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    float cell = getWidth() / (float) COUNT;
    float h = getHeight();
    for (int k = 0; k < COUNT; k++) {
      int i = SHOWN[k];
      float left = k * cell;
      rect.set(left + Screen.dp(3f), Screen.dp(2f), left + cell - Screen.dp(3f), h - Screen.dp(2f));
      fill.setColor(i == selected ? 0xff3d8fd8 : 0x33ffffff);
      c.drawRoundRect(rect, Screen.dp(12f), Screen.dp(12f), fill);
      float cx = rect.centerX(), cy = rect.top + (rect.height() - Screen.dp(12f)) / 2f;
      drawSample(c, i, cx, cy);
      text.setColor(i == selected ? 0xffffffff : 0xb3ffffff);
      c.drawText(labels[i], cx, rect.bottom - Screen.dp(5f), text);
    }
  }

  private void drawSample (Canvas c, int tool, float cx, float cy) {
    float w = Screen.dp(11f), s = Screen.dp(5f);
    int sample = tool == selected && tool != TOOL_ERASER && tool != TOOL_MOSAIC ? color : 0xffffffff;
    paint.setXfermode(null);
    paint.setMaskFilter(null);
    paint.setPathEffect(null);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setColor(sample);
    path.reset();
    path.moveTo(cx - w, cy + s);
    path.cubicTo(cx - w / 2f, cy - s * 1.6f, cx + w / 3f, cy + s * 1.6f, cx + w, cy - s);
    switch (tool) {
      case TOOL_PEN:
        paint.setStrokeWidth(Screen.dp(2.4f));
        c.drawPath(path, paint);
        break;
      case TOOL_MARKER:
        paint.setStrokeWidth(Screen.dp(6f));
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setColor(me.vkryl.core.ColorUtils.alphaColor(.5f, sample));
        c.drawPath(path, paint);
        break;
      case TOOL_PENCIL:
        paint.setStrokeWidth(Screen.dp(1.6f));
        paint.setPathEffect(new DashPathEffect(new float[] {Screen.dp(1.6f), Screen.dp(1f)}, 0));
        c.drawPath(path, paint);
        break;
      case TOOL_NEON:
        paint.setStrokeWidth(Screen.dp(4f));
        paint.setMaskFilter(new BlurMaskFilter(Screen.dp(3f), BlurMaskFilter.Blur.NORMAL));
        paint.setColor(tool == selected ? sample : 0xffff2d95);
        c.drawPath(path, paint);
        paint.setMaskFilter(null);
        paint.setStrokeWidth(Screen.dp(1.5f));
        paint.setColor(0xffffffff);
        c.drawPath(path, paint);
        break;
      case TOOL_FREE_ARROW:
        paint.setStrokeWidth(Screen.dp(2.2f));
        c.drawPath(path, paint);
        c.drawLine(cx + w, cy - s, cx + w - Screen.dp(6f), cy - s - Screen.dp(1f), paint);
        c.drawLine(cx + w, cy - s, cx + w - Screen.dp(2f), cy - s + Screen.dp(5.5f), paint);
        break;
      case TOOL_SHAPE: {
        paint.setStrokeWidth(Screen.dp(2f));
        float r = Screen.dp(8f);
        switch (shapeMode) {
          case PaintMode.ELLIPSE:
            c.drawOval(new android.graphics.RectF(cx - r * 1.2f, cy - r * .8f, cx + r * 1.2f, cy + r * .8f), paint);
            break;
          case PaintMode.LINE:
            c.drawLine(cx - r * 1.2f, cy + r * .7f, cx + r * 1.2f, cy - r * .7f, paint);
            break;
          case PaintMode.ARROW:
            c.drawLine(cx - r * 1.2f, cy + r * .7f, cx + r * 1.2f, cy - r * .7f, paint);
            c.drawLine(cx + r * 1.2f, cy - r * .7f, cx + r * .2f, cy - r * .9f, paint);
            c.drawLine(cx + r * 1.2f, cy - r * .7f, cx + r * .7f, cy + r * .2f, paint);
            break;
          default:
            c.drawRoundRect(new android.graphics.RectF(cx - r * 1.2f, cy - r * .8f, cx + r * 1.2f, cy + r * .8f), Screen.dp(2f), Screen.dp(2f), paint);
            break;
        }
        // the small corner mark: hold for more shapes
        fill.setColor(0x99ffffff);
        c.drawCircle(cx + r * 1.9f, cy + r * 1.1f, Screen.dp(1.6f), fill);
        break;
      }
      case TOOL_MOSAIC: {
        // a small grid of squares in shades
        float q = Screen.dp(4.5f);
        int[] shades = {0xffffffff, 0x99ffffff, 0xccffffff, 0x66ffffff, 0xddffffff, 0x88ffffff, 0xbbffffff, 0x55ffffff, 0xeeffffff};
        for (int yy = 0; yy < 3; yy++) {
          for (int xx = 0; xx < 3; xx++) {
            fill.setColor(shades[yy * 3 + xx]);
            c.drawRect(cx - q * 1.5f + xx * q, cy - q * 1.5f + yy * q, cx - q * 1.5f + (xx + 1) * q - 1, cy - q * 1.5f + (yy + 1) * q - 1, fill);
          }
        }
        break;
      }
      case TOOL_ERASER: {
        paint.setStrokeWidth(Screen.dp(1.8f));
        paint.setColor(0xffffffff);
        c.save();
        c.rotate(-35f, cx, cy);
        c.drawRoundRect(new android.graphics.RectF(cx - Screen.dp(10f), cy - Screen.dp(5f), cx + Screen.dp(10f), cy + Screen.dp(5f)), Screen.dp(2.5f), Screen.dp(2.5f), paint);
        c.drawLine(cx - Screen.dp(2f), cy - Screen.dp(5f), cx - Screen.dp(2f), cy + Screen.dp(5f), paint);
        c.restore();
        break;
      }
    }
  }
}
