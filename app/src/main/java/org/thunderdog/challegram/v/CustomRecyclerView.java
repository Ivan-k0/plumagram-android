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
 * File created on 24/08/2015 at 00:13
 */
package org.thunderdog.challegram.v;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.vkryl.android.animator.Animated;

public class CustomRecyclerView extends RecyclerView implements Animated {
  public CustomRecyclerView (Context context) {
    super(context);
    tgx101TrackEdges();
  }

  public CustomRecyclerView (Context context, AttributeSet attrs) {
    super(context, attrs);
    tgx101TrackEdges();
  }

  public CustomRecyclerView (Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    tgx101TrackEdges();
  }

  public void requestLayoutAt (int adapterIndex) {
    LayoutManager manager = getLayoutManager();
    int savedScrollPosition, savedScrollOffset;
    if (manager instanceof LinearLayoutManager) {
      savedScrollPosition = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
      View view = savedScrollPosition == -1 ? null : manager.findViewByPosition(savedScrollPosition);
      savedScrollOffset = view != null ? view.getTop() : 0;
    } else {
      savedScrollPosition = -1;
      savedScrollOffset = 0;
    }
    View view = manager.findViewByPosition(adapterIndex);
    if (view != null) {
      view.requestLayout();
      if (savedScrollOffset != -1 && manager instanceof LinearLayoutManager) {
        ((LinearLayoutManager) manager).scrollToPositionWithOffset(savedScrollPosition, savedScrollOffset);
      }
    } else {
      getAdapter().notifyItemChanged(adapterIndex);
    }
  }

  public void invalidateViewAt (int adapterIndex) {
    View view = getLayoutManager().findViewByPosition(adapterIndex);
    if (view != null) {
      view.invalidate();
    } else {
      getAdapter().notifyItemChanged(adapterIndex);
    }
  }

  public void invalidateAll () {
    final LayoutManager manager = getLayoutManager();
    if (manager != null && manager instanceof LinearLayoutManager) {
      int first = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
      int last = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
      for (int i = first; i <= last; i++) {
        View v = manager.findViewByPosition(i);
        if (v != null) {
          v.invalidate();
        }
      }
    }
    for (int i = 0; i < getChildCount(); i++) {
      View v = getChildAt(i);
      if (v != null) {
        v.invalidate();
      }
    }
  }

  public interface TouchInterceptor {
    boolean canInterceptAt (CustomRecyclerView v, float x, float y);
  }

  private @Nullable TouchInterceptor touchInterceptor;
  private boolean ignoreTouch;

  public void setTouchInterceptor (@Nullable TouchInterceptor touchInterceptor) {
    this.touchInterceptor = touchInterceptor;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    if (scrollDisabled) {
      return interceptEvents;
    }
    if (touchDisabled) {
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          return true;
        }
        case MotionEvent.ACTION_UP: {
          touchDisabled = false;
          return true;
        }
      }
      return false;
    }

    if (touchInterceptor != null && e.getAction() == MotionEvent.ACTION_DOWN && !touchInterceptor.canInterceptAt(this, e.getX(), e.getY())) {
      ignoreTouch = true;
      return true;
    }

    if (tgx101IsVertical()) {
      // TGx101 (user 2026-10-27 «все наши свайпы назад — как в списке чатов и в чате»): the same rules as the chat and
      // the chat list for every vertical list — a finger landing on a moving or stretched list only stops it and is
      // not held from the back swipe, and a move more to the right than up or down never starts the list's scroll
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
        tgx101DownX = e.getX();
        tgx101DownY = e.getY();
        // (user video 2026-10-29 12:17, «Новый опрос»: the question field took no tap): a list left stretched at its edge
        // swallowed every touch to «stop» — a short screen that never settles took no taps at all. A stretch is let go
        // without taking the touch; only a list really moving keeps the finger for itself
        tgx101ReleaseStretch();
        if (getScrollState() == SCROLL_STATE_SETTLING) {
          stopScroll();
          super.onInterceptTouchEvent(e);
          if (getScrollState() == SCROLL_STATE_DRAGGING) {
            stopScroll();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
          }
          return true;
        }
      } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE && getScrollState() == SCROLL_STATE_IDLE) {
        float dx = e.getX() - tgx101DownX, dy = e.getY() - tgx101DownY;
        if (dx > 0 && dx > Math.abs(dy) * 1.3f && !org.thunderdog.challegram.core.Lang.rtl()) {
          return false;
        }
      }
    }

    return super.onInterceptTouchEvent(e);
  }

  private float tgx101DownX, tgx101DownY;

  private boolean tgx101IsVertical () {
    LayoutManager manager = getLayoutManager();
    return manager != null && manager.canScrollVertically() && !manager.canScrollHorizontally();
  }

  /** a list still stretched at its edge (Android 12+) lets it go, as a fling would be stopped */
  // a fling into the top or bottom leaves the list stretched (Android 12+) for a moment; RecyclerView «catches» that
  // stretch on touch just like a fling — the touch lets it go first
  private android.widget.EdgeEffect tgx101TopEdge, tgx101BottomEdge;

  private void tgx101TrackEdges () {
    setEdgeEffectFactory(new EdgeEffectFactory() {
      @androidx.annotation.NonNull
      @Override
      protected android.widget.EdgeEffect createEdgeEffect (@androidx.annotation.NonNull RecyclerView view, int direction) {
        android.widget.EdgeEffect edge = super.createEdgeEffect(view, direction);
        if (direction == DIRECTION_TOP) tgx101TopEdge = edge;
        else if (direction == DIRECTION_BOTTOM) tgx101BottomEdge = edge;
        return edge;
      }
    });
  }

  private boolean tgx101ReleaseStretch () {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return false;
    boolean released = false;
    for (android.widget.EdgeEffect edge : new android.widget.EdgeEffect[] {tgx101TopEdge, tgx101BottomEdge}) {
      if (edge != null && edge.getDistance() != 0f) {
        edge.finish();
        released = true;
      }
    }
    if (released) invalidate();
    return released;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (scrollDisabled) {
      return false;
    }
    if (touchDisabled) {
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          touchDisabled = false;
          break;
        }
        case MotionEvent.ACTION_UP: {
          touchDisabled = false;
          return true;
        }
        default: {
          return false;
        }
      }
    }

    if (ignoreTouch) {
      ignoreTouch = false;
      return false;
    }

    return super.onTouchEvent(e);
  }

  private boolean touchDisabled;

  public void cancelTouchEvents () {
    touchDisabled = true;
  }

  private boolean scrollDisabled, interceptEvents;
  public void setScrollDisabled (boolean isDisabled) {
    this.scrollDisabled = isDisabled;
    this.interceptEvents = true;
  }

  public boolean isScrollDisabled () {
    return scrollDisabled;
  }

  public void setScrollDisabled (boolean isDisabled, boolean intercept) {
    this.scrollDisabled = isDisabled;
    this.interceptEvents = intercept;
  }

  public interface MeasureListener {
    void onMeasure (CustomRecyclerView v, int oldWidth, int oldHeight, int newWidth, int newHeight);
  }
  private @Nullable MeasureListener measureListener;
  public void setMeasureListener (@Nullable MeasureListener listener) {
    this.measureListener = listener;
  }

  private int oldWidth, oldHeight;

  @Override
  protected void onMeasure (int widthSpec, int heightSpec) {
    super.onMeasure(widthSpec, heightSpec);
    int newWidth = getMeasuredWidth();
    int newHeight = getMeasuredHeight();
    if (oldWidth != newWidth || oldHeight != newHeight) {
      int prevWidth = this.oldWidth;
      int prevHeight = this.oldHeight;
      oldWidth = newWidth;
      oldHeight = newHeight;
      if (measureListener != null) {
        measureListener.onMeasure(this, prevWidth, prevHeight, newWidth, newHeight);
      }
    }
  }

  private Runnable pendingAction;

  @Override
  public void runOnceViewBecomesReady (View view, Runnable action) {
    this.pendingAction = action;
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (pendingAction != null) {
      pendingAction.run();
      pendingAction = null;
    }
  }

  private int savedScrollPosition = RecyclerView.NO_POSITION;
  private int savedScrollTop;

  public void saveScrollPosition () {
    LayoutManager manager = getLayoutManager();
    if (!(manager instanceof LinearLayoutManager)) {
      savedScrollPosition = RecyclerView.NO_POSITION;
      return;
    }
    savedScrollPosition = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
    View view = manager.findViewByPosition(savedScrollPosition);
    savedScrollTop = view != null ? manager.getDecoratedTop(view) : 0;
  }

  public void restoreScrollPosition () {
    if (savedScrollPosition != RecyclerView.NO_POSITION) {
      LayoutManager manager = getLayoutManager();
      if (manager instanceof LinearLayoutManager) {
        ((LinearLayoutManager) manager).scrollToPositionWithOffset(savedScrollPosition, savedScrollTop);
      }
      savedScrollPosition = RecyclerView.NO_POSITION;
      savedScrollTop = 0;
    }
  }
}
