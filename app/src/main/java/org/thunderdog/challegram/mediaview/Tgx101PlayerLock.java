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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.tool.Screen;

/**
 * TGx101: the video player's child lock — a transparent layer over the whole viewer that takes every touch: no
 * swipes (close, next / previous, gestures), no buttons. A tap only shows / hides the controls; in them only the lock
 * reacts, a tap on it takes the lock off.
 */
final class Tgx101PlayerLock extends View {
  interface Delegate {
    boolean isOnLock (float rawX, float rawY);
    void onUnlock ();
    void onTap ();
  }

  private final Delegate delegate;
  private float downX, downY;
  private boolean moved;

  private Tgx101PlayerLock (Context context, Delegate delegate) {
    super(context);
    this.delegate = delegate;
  }

  static View create (Context context, Delegate delegate) {
    Tgx101PlayerLock view = new Tgx101PlayerLock(context, delegate);
    view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return view;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downX = e.getRawX();
        downY = e.getRawY();
        moved = false;
        break;
      case MotionEvent.ACTION_MOVE:
        if (Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > Screen.getTouchSlop()) moved = true;
        break;
      case MotionEvent.ACTION_UP:
        if (!moved) {
          if (delegate.isOnLock(e.getRawX(), e.getRawY())) {
            delegate.onUnlock();
          } else {
            delegate.onTap();
          }
        }
        break;
    }
    return true;
  }
}
