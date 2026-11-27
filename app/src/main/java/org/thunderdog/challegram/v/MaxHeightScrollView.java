package org.thunderdog.challegram.v;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.ScrollView;

import org.thunderdog.challegram.tool.Views;

public class MaxHeightScrollView extends ScrollView {
  public MaxHeightScrollView (Context context) {
    super(context);
  }

  private int maxHeight;

  public void setMaxHeight (int maxHeight) {
    if (this.maxHeight != maxHeight) {
      this.maxHeight = maxHeight;
      requestLayout();
    }
  }

  private boolean isScrollable;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (maxHeight != 0 && getMeasuredHeight() > maxHeight) {
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY));
      isScrollable = true;
    } else {
      isScrollable = false;
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent ev) {
    boolean handled;
    try {
      handled = super.onTouchEvent(ev);
    } catch (IllegalArgumentException e) {
      // TGx101 (crash from people 2026-11-27, «invalid pointerIndex -1» on POINTER_UP): ScrollView looks up a finger it
      // has lost track of (after a CANCEL, or a pointer it never saw) — the event is dropped instead of the app
      handled = false;
    }
    boolean res = handled && (ev.getAction() != MotionEvent.ACTION_DOWN || (isScrollable && Views.isValid(this)));
    switch (ev.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        if (isScrollable && Views.isValid(this)) {
          ViewParent parent = getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
          }
        }
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        ViewParent parent = getParent();
        if (parent != null) {
          parent.requestDisallowInterceptTouchEvent(false);
        }
        break;
    }
    return res;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return (super.onInterceptTouchEvent(ev) && (ev.getAction() != MotionEvent.ACTION_DOWN || Views.isValid(this))) || !Views.isValid(this);
  }
}
