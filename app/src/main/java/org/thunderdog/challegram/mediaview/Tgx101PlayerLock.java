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
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

/**
 * TGx101: the video player's child lock — a layer over the viewer that takes every touch. Holding the lock button
 * for two seconds (a ring fills up) takes the lock off; any other touch only shows the hint for a moment.
 */
final class Tgx101PlayerLock extends View {
  private static final long HOLD_MS = 2000;
  private static final long HINT_MS = 2500;

  private final Runnable onUnlock;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF ring = new RectF();
  private final Drawable icon;
  private long holdStart, hintUntil;
  private boolean holding;

  private Tgx101PlayerLock (Context context, Runnable onUnlock) {
    super(context);
    this.onUnlock = onUnlock;
    icon = context.getResources().getDrawable(R.drawable.baseline_lock_24, null).mutate();
    icon.setTint(0xffffffff);
    textPaint.setColor(0xe6ffffff);
    textPaint.setTextSize(Screen.dp(13f));
    hintUntil = SystemClock.uptimeMillis() + HINT_MS;
  }

  static View create (Context context, Runnable onUnlock) {
    Tgx101PlayerLock view = new Tgx101PlayerLock(context, onUnlock);
    view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return view;
  }

  private float cx () {
    return Screen.dp(52f);
  }

  private float cy () {
    return getMeasuredHeight() / 2f;
  }

  private boolean onButton (float x, float y) {
    return Math.hypot(x - cx(), y - cy()) < Screen.dp(44f);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        hintUntil = SystemClock.uptimeMillis() + HINT_MS;
        if (onButton(e.getX(), e.getY())) {
          holding = true;
          holdStart = SystemClock.uptimeMillis();
        }
        invalidate();
        break;
      case MotionEvent.ACTION_MOVE:
        if (holding && !onButton(e.getX(), e.getY())) {
          holding = false;
          invalidate();
        }
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        holding = false;
        invalidate();
        break;
    }
    return true; // nothing below gets a touch while locked
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    long now = SystemClock.uptimeMillis();
    boolean showHint = now < hintUntil || holding;
    if (!showHint) {
      postInvalidateDelayed(HINT_MS);
      return;
    }
    float cx = cx(), cy = cy(), r = Screen.dp(28f);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(0x4dffffff);
    c.drawCircle(cx, cy, r, paint);
    int s = Screen.dp(24f);
    icon.setBounds((int) cx - s / 2, (int) cy - s / 2, (int) cx + s / 2, (int) cy + s / 2);
    icon.draw(c);
    if (holding) {
      float progress = Math.min(1f, (now - holdStart) / (float) HOLD_MS);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(Screen.dp(3f));
      paint.setColor(0xff5b87b0);
      ring.set(cx - r - Screen.dp(4f), cy - r - Screen.dp(4f), cx + r + Screen.dp(4f), cy + r + Screen.dp(4f));
      c.drawArc(ring, -90, 360 * progress, false, paint);
      if (progress >= 1f) {
        holding = false;
        post(onUnlock);
        return;
      }
    }
    String hint = Lang.getString(R.string.Tgx101PlayerLockedHint);
    float y = cy + r + Screen.dp(22f);
    for (String line : hint.split("\n")) {
      c.drawText(line, Math.max(Screen.dp(8f), cx - textPaint.measureText(line) / 2f), y, textPaint);
      y += Screen.dp(17f);
    }
    postInvalidateOnAnimation();
  }
}
