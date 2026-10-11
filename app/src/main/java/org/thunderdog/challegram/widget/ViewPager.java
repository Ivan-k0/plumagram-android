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
 *
 * File created on 27/02/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.CheckResult;

import org.thunderdog.challegram.Log;

import me.vkryl.core.MathUtils;

public class ViewPager extends androidx.viewpager.widget.ViewPager {
  public ViewPager (Context context) {
    super(context);
    tgx101FastSettle(context);
  }

  public ViewPager (Context context, AttributeSet attrs) {
    super(context, attrs);
    tgx101FastSettle(context);
  }

  // TGx101 (user 2026-10-08 22:38 «в exteraGram свайп между папками плавнее и быстрее»): frame-by-frame the release took
  // ~550 ms here and ~370 ms there — androidx ViewPager settles slowly (up to 600 ms). The settle now takes 120–260 ms by
  // the distance left, with the same quintic ease-out
  private static final android.view.animation.Interpolator TGX101_SETTLE = t -> {
    t -= 1.0f;
    return t * t * t * t * t + 1.0f;
  };

  private void tgx101FastSettle (Context context) {
    try {
      java.lang.reflect.Field field = androidx.viewpager.widget.ViewPager.class.getDeclaredField("mScroller");
      field.setAccessible(true);
      field.set(this, new android.widget.Scroller(context, TGX101_SETTLE) {
        @Override
        public void startScroll (int startX, int startY, int dx, int dy, int duration) {
          int width = Math.max(1, getWidth());
          int fast = Math.round(120 + 140 * Math.min(1f, Math.abs(dx) / (float) width));
          super.startScroll(startX, startY, dx, dy, Math.min(duration, fast));
        }
      });
    } catch (Throwable t) {
      Log.w(t);
    }
  }

  private boolean oneShot;
  private java.lang.reflect.Field mFirstLayoutField;

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if (oneShot) {
      try {
        if (mFirstLayoutField == null) {
          mFirstLayoutField = androidx.viewpager.widget.ViewPager.class.getDeclaredField("mFirstLayout");
          mFirstLayoutField.setAccessible(true);
        }
        mFirstLayoutField.set(this, false);
      } catch (Throwable t) {
        Log.w(t);
      }
    } else {
      oneShot = true;
    }
  }

  // TGx101 (user 2026-10-11): no page flips from a pinch — once a second finger is down the gesture never pages (an
  // already started drag snaps back). «Strict» pagers (the profile's Media / Files / … tabs, where flips by mistake were
  // frequent) also need a clearly sideways move: two touch slops and twice the vertical distance; a gesture that went
  // vertical first stays a scroll.
  private boolean tgx101MultiTouch, tgx101Vertical, tgx101Strict;
  private float tgx101DownX, tgx101DownY;

  public void setStrictPaging (boolean strict) {
    this.tgx101Strict = strict;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    switch (ev.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        tgx101MultiTouch = tgx101Vertical = false;
        tgx101DownX = ev.getX();
        tgx101DownY = ev.getY();
        break;
      case MotionEvent.ACTION_POINTER_DOWN:
        tgx101MultiTouch = true;
        break;
      case MotionEvent.ACTION_MOVE:
        if (tgx101Strict && !tgx101MultiTouch) {
          float dx = Math.abs(ev.getX() - tgx101DownX), dy = Math.abs(ev.getY() - tgx101DownY);
          int slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
          if (tgx101Vertical) {
            return false;
          }
          if (dy > slop && dy * 2f > dx) {
            tgx101Vertical = true;
            return false;
          }
          if (dx < slop * 2 || dx < dy * 2f) {
            return false;
          }
        }
        break;
    }
    if (tgx101MultiTouch) {
      return false;
    }
    try {
      return pagingEnabled && super.onInterceptTouchEvent(ev);
    } catch (Throwable ignored) {
      return false;
    }
  }

  private boolean pagingEnabled = true;

  public void setPagingEnabled (boolean isEnabled) {
    this.pagingEnabled = isEnabled;
  }

  public boolean isPagingEnabled () {
    return pagingEnabled;
  }

  @Override
  public boolean onTouchEvent (MotionEvent ev) {
    int action = ev.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      tgx101MultiTouch = false;
    } else if (action == MotionEvent.ACTION_POINTER_DOWN && !tgx101MultiTouch) {
      tgx101MultiTouch = true;
      // a second finger during a page drag: the page goes back where it was
      MotionEvent cancel = MotionEvent.obtain(ev);
      cancel.setAction(MotionEvent.ACTION_CANCEL);
      try {
        super.onTouchEvent(cancel);
      } catch (Throwable ignored) { }
      cancel.recycle();
      return true;
    }
    if (tgx101MultiTouch) {
      return true; // the rest of this gesture is swallowed
    }
    return pagingEnabled && super.onTouchEvent(ev);
  }

  @CheckResult
  public static float clampPositionOffset (float positionOffset) {
    if (Float.isNaN(positionOffset)) return 0f;
    return MathUtils.clamp(positionOffset);
  }
}
