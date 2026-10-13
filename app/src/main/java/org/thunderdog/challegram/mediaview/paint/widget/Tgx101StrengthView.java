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
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

/** TGx101: «Strength» of the pixel blur — five steps from small squares to big ones; tap or slide over it */
public class Tgx101StrengthView extends View {
  public interface Listener {
    void onStrengthChanged (int strength);
  }

  private final String label;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private int strength = 3;
  private Listener listener;

  public Tgx101StrengthView (Context context, String label) {
    super(context);
    this.label = label;
    text.setTypeface(Fonts.getRobotoMedium());
    text.setTextSize(Screen.dp(12f));
    text.setColor(0xffffffff);
  }

  public void setListener (Listener listener) {
    this.listener = listener;
  }

  public void setStrength (int strength) {
    this.strength = strength;
    invalidate();
  }

  public int getStrength () {
    return strength;
  }

  private float barLeft () {
    return Screen.dp(16f) + text.measureText(label) + Screen.dp(14f);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    int action = e.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
      float left = barLeft(), right = getWidth() - Screen.dp(16f);
      int s = 1 + Math.round(Math.max(0f, Math.min(1f, (e.getX() - left) / Math.max(1f, right - left))) * 4f);
      if (s != strength) {
        strength = s;
        invalidate();
        if (listener != null) listener.onStrengthChanged(s);
      }
      if (action == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
      return true;
    }
    return super.onTouchEvent(e);
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    float cy = getHeight() / 2f;
    rect.set(Screen.dp(6f), Screen.dp(2f), getWidth() - Screen.dp(6f), getHeight() - Screen.dp(2f));
    paint.setColor(0x99101418);
    c.drawRoundRect(rect, Screen.dp(14f), Screen.dp(14f), paint);
    c.drawText(label, Screen.dp(16f), cy + Screen.dp(4.5f), text);
    float left = barLeft(), right = getWidth() - Screen.dp(16f);
    for (int i = 1; i <= 5; i++) {
      float x = left + (right - left) * (i - 1) / 4f;
      float q = Screen.dp(3f + i * 1.6f); // the squares grow with the strength
      paint.setColor(i <= strength ? 0xff3d8fd8 : 0x55ffffff);
      c.drawRect(x - q / 2f, cy - q / 2f, x + q / 2f, cy + q / 2f, paint);
    }
  }
}
