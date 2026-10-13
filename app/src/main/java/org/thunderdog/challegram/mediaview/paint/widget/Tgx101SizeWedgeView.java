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
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.MathUtils;

/**
 * TGx101 (user 2026-10-13, photo editor mockup variant Б): the brush thickness — a wedge on the left edge, as in the
 * official app. Wide at the top (thick), narrow at the bottom (thin). While it is dragged, a circle of the real brush
 * size shows next to the finger.
 */
public class Tgx101SizeWedgeView extends View {
  public interface Listener {
    void onSizeChanged (float factor);
  }

  private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path track = new Path();
  private Listener listener;
  private float factor = .25f;
  private boolean dragging;
  private float previewRadius;
  private int previewColor = 0xffffffff;

  public Tgx101SizeWedgeView (Context context) {
    super(context);
    knobPaint.setColor(0xffffffff);
    knobPaint.setShadowLayer(Screen.dp(2f), 0, Screen.dp(1f), 0x66000000);
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setStrokeWidth(Screen.dp(3f));
    ringPaint.setColor(0x993d8fd8);
    previewPaint.setShadowLayer(Screen.dp(3f), 0, 0, 0x88000000);
    setLayerType(LAYER_TYPE_SOFTWARE, null); // the shadows
  }

  public void setListener (Listener listener) {
    this.listener = listener;
  }

  public void setFactor (float factor) {
    factor = MathUtils.clamp(factor);
    if (this.factor != factor) {
      this.factor = factor;
      invalidate();
    }
  }

  /** The real brush radius (on screen) and colour, for the circle shown while dragging */
  public void setPreview (float radius, int color) {
    this.previewRadius = radius;
    this.previewColor = color;
    if (dragging) invalidate();
  }

  private float trackTop () {
    return getPaddingTop() + Screen.dp(12f);
  }

  private float trackBottom () {
    return getHeight() - getPaddingBottom() - Screen.dp(12f);
  }

  private float trackX () {
    return Screen.dp(20f);
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    float x = trackX(), top = trackTop(), bottom = trackBottom();
    float wide = Screen.dp(5f), narrow = Screen.dp(1.2f);
    track.reset();
    track.moveTo(x - wide, top);
    track.lineTo(x + wide, top);
    track.lineTo(x + narrow, bottom);
    track.lineTo(x - narrow, bottom);
    track.close();
    trackPaint.setShader(new LinearGradient(0, top, 0, bottom, 0xc0ffffff, 0x40ffffff, Shader.TileMode.CLAMP));
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    c.drawPath(track, trackPaint);
    float x = trackX();
    float y = trackBottom() - (trackBottom() - trackTop()) * factor;
    float r = Screen.dp(dragging ? 12f : 10f);
    c.drawCircle(x, y, r, knobPaint);
    c.drawCircle(x, y, r + Screen.dp(1.5f), ringPaint);
    if (dragging && previewRadius > 0) {
      previewPaint.setColor(previewColor);
      float px = x + Screen.dp(40f) + previewRadius;
      c.drawCircle(px, y, previewRadius, previewPaint);
    }
  }

  private boolean isActive () {
    if (getVisibility() != VISIBLE) return false;
    for (View v = this; v != null; ) {
      if (v.getAlpha() < 1f) return false;
      ViewParent p = v.getParent();
      v = p instanceof View ? (View) p : null;
    }
    return true;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        if (!isActive() || e.getX() > Screen.dp(44f)) return false; // the rest of the strip belongs to the photo
        dragging = true;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        apply(e.getY());
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (dragging) apply(e.getY());
        return dragging;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (dragging) {
          dragging = false;
          invalidate();
          return true;
        }
        return false;
      }
    }
    return dragging;
  }

  private void apply (float y) {
    float f = MathUtils.clamp((trackBottom() - y) / (trackBottom() - trackTop()));
    factor = f;
    invalidate();
    if (listener != null) listener.onSizeChanged(f);
  }
}
