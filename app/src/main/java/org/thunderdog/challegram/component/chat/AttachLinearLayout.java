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
 * File created on 14/03/2016 at 00:29
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.thunderdog.challegram.tool.Views;

public class AttachLinearLayout extends LinearLayout {
  public AttachLinearLayout (Context context) {
    super(context);
  }

  /*private int calculateWidth () {
    int width = 0;
    for (int i = 0; i < getChildCount(); i++) {
      View v = getChildAt(i);
      if (v.getVisibility() == View.VISIBLE) {
        width += v.getLayoutParams().width;
      }
    }
    return width;
  }*/

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return (ev.getAction() == MotionEvent.ACTION_DOWN && !Views.isValid(this)) || super.onInterceptTouchEvent(ev);
  }

  // TGx101 (user 2026-10-17 «вёрстка поплыла», «/» over «Сообщение»): an INVISIBLE button (silent, scheduled…) kept its
  // place, so the ones left of it stood a button further left than the field's text knew (it counts visible ones only).
  // The visible buttons are packed to the right instead — no holes, whichever are hidden
  private void packVisible () {
    int shift = 0;
    for (int i = getChildCount() - 1; i >= 0; i--) {
      View v = getChildAt(i);
      if (v.getVisibility() == View.GONE) continue;
      if (v.getVisibility() == View.VISIBLE) {
        if (v.getTranslationX() != shift) v.setTranslationX(shift);
      } else {
        shift += v.getLayoutParams().width;
      }
    }
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    packVisible();
  }

  private Runnable onButtonsChanged;

  /** Called whenever the set of shown buttons may have changed — the field's placeholder re-fits its width */
  public void setOnButtonsChanged (Runnable onButtonsChanged) {
    this.onButtonsChanged = onButtonsChanged;
  }

  public void updatePivot () {
    packVisible();
    if (onButtonsChanged != null) onButtonsChanged.run();
    int totalWidth = 0;
    int width = 0;

    //int itemWidth = 0;
    int itemHeight = 0;

    for (int i = 0; i < getChildCount(); i++) {
      View v = getChildAt(i);
      ViewGroup.LayoutParams params = v.getLayoutParams();
      int w = params.width;
      if (itemHeight == 0) {
        //itemWidth = params.width;
        itemHeight = params.height;
      }
      totalWidth += w;
      if (v.getVisibility() == View.VISIBLE) {
        width += w;
      }
    }

    setPivotX(totalWidth - (int) ((float) width * .5f));
    setPivotY((int) ((float) itemHeight * .5f));
  }

  public int getVisibleChildrenWidth () {
    int totalWidth = 0;
    final int count = getChildCount();
    for (int i = 0; i < count; i++) {
      View v = getChildAt(i);
      if (v.getVisibility() == View.VISIBLE) {
        int width = v.getLayoutParams().width;
        totalWidth += width;
      }
    }
    return totalWidth;
  }
}
