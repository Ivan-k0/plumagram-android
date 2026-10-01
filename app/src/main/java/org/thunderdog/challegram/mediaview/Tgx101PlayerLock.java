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
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.tool.Screen;

/**
 * TGx101: the video player's child lock — a transparent layer over the whole viewer that takes every touch: no
 * swipes (close, next / previous, gestures), no buttons. A tap only shows the controls (they hide by themselves after
 * 3 s); in them only the lock reacts, and only to a 2-second hold (a ring fills up around it).
 */
final class Tgx101PlayerLock extends View {
  interface Delegate {
    /** The lock button's centre on the screen, or false when the controls are hidden */
    boolean getLockCenter (int[] outRawXY);
    boolean getPauseCenter (int[] outRawXY);
    void onUnlock ();
    void onTap ();
    void onPlayPause ();
  }

  private static final long HOLD_MS = 2000;

  private final Delegate delegate;
  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF ring = new RectF();
  private final int[] center = new int[2], location = new int[2];
  private float downX, downY;
  private long lastTapAt;
  private float lastTapX;
  private final Runnable pendingTap = this::tapNow;

  private void tapNow () {
    delegate.onTap();
  }
  private boolean moved, holding;
  private long holdStart;

  private Tgx101PlayerLock (Context context, Delegate delegate) {
    super(context);
    this.delegate = delegate;
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setStrokeWidth(Screen.dp(3f));
    ringPaint.setStrokeCap(Paint.Cap.ROUND);
    ringPaint.setColor(0xff5b87b0);
  }

  static View create (Context context, Delegate delegate) {
    Tgx101PlayerLock view = new Tgx101PlayerLock(context, delegate);
    view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    // above everything in the viewer: the video and the bars are raised 1–3 dp, and touches go to the highest view
    androidx.core.view.ViewCompat.setTranslationZ(view, Screen.dp(24f));
    return view;
  }

  private boolean onLock (float rawX, float rawY) {
    return delegate.getLockCenter(center) && Math.hypot(rawX - center[0], rawY - center[1]) < Screen.dp(30f);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downX = e.getRawX();
        downY = e.getRawY();
        moved = false;
        holding = onLock(downX, downY);
        if (holding) {
          holdStart = SystemClock.uptimeMillis();
          Tgx101Diag.mark("player: lock held");
          invalidate();
        }
        break;
      case MotionEvent.ACTION_MOVE:
        if (Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > Screen.getTouchSlop()) {
          moved = true;
          if (holding && !onLock(e.getRawX(), e.getRawY())) {
            holding = false;
            invalidate();
          }
        }
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        boolean wasHolding = holding;
        holding = false;
        invalidate();
        if (wasHolding) {
          Tgx101Diag.mark("player: lock released after " + (SystemClock.uptimeMillis() - holdStart) + " ms — still locked");
        } else if (moved) {
          Tgx101Diag.mark("player: swipe blocked by the lock");
        } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
          onTapUp(e.getRawX(), e.getRawY());
        }
        break;
      }
    }
    return true;
  }

  /** Under the lock: the pause button and a double tap in the middle still play / pause; any other tap shows the controls */
  private void onTapUp (float rawX, float rawY) {
    int[] pause = new int[2];
    if (delegate.getPauseCenter(pause) && Math.hypot(rawX - pause[0], rawY - pause[1]) < Screen.dp(28f)) {
      Tgx101Diag.mark("player: pause button under the lock");
      delegate.onPlayPause();
      delegate.onTap();
      return;
    }
    long now = SystemClock.uptimeMillis();
    float width = getMeasuredWidth();
    getLocationOnScreen(location);
    float x = rawX - location[0];
    boolean middle = x > width / 3f && x < width * 2f / 3f;
    if (middle && now - lastTapAt < 300 && Math.abs(rawX - lastTapX) < Screen.dp(80f)) {
      removeCallbacks(pendingTap);
      lastTapAt = 0;
      Tgx101Diag.mark("player: double tap centre under the lock");
      delegate.onPlayPause();
      return;
    }
    lastTapAt = now;
    lastTapX = rawX;
    removeCallbacks(pendingTap);
    if (middle) {
      postDelayed(pendingTap, 300); // may become a double tap
    } else {
      delegate.onTap();
    }
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    if (!holding || !delegate.getLockCenter(center)) return;
    float progress = Math.min(1f, (SystemClock.uptimeMillis() - holdStart) / (float) HOLD_MS);
    getLocationOnScreen(location);
    float cx = center[0] - location[0], cy = center[1] - location[1], r = Screen.dp(20f);
    ring.set(cx - r, cy - r, cx + r, cy + r);
    c.drawArc(ring, -90, 360 * progress, false, ringPaint);
    if (progress >= 1f) {
      holding = false;
      post(delegate::onUnlock);
      return;
    }
    postInvalidateOnAnimation();
  }
}
