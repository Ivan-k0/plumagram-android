/*
 * This file is a part of PlumaGram
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
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

/**
 * TGx101 (user 2026-10-15 «как в современных галереях»): a thumb on the right of a long list. It shows while the list
 * moves; dragging it scrolls the whole list and a bubble beside the finger shows the date under it, live.
 */
public final class Tgx101FastScroller extends View {
  public interface Labels {
    @Nullable String labelAt (int position);
  }

  private final RecyclerView list;
  private final Labels labels;
  private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final int thumbHeight = Screen.dp(52f);
  private float alpha;
  private boolean dragging;
  private float thumbY;
  private String label;
  private long lastTick;

  public Tgx101FastScroller (Context context, RecyclerView list, Labels labels) {
    super(context);
    this.list = list;
    this.labels = labels;
    textPaint.setTextSize(Screen.dp(15f));
    textPaint.setTypeface(Fonts.getRobotoMedium());
    list.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (dy != 0 && longEnough()) show();
        if (!dragging) invalidate();
      }
    });
  }

  private boolean longEnough () {
    return list.computeVerticalScrollRange() > list.computeVerticalScrollExtent() * 3;
  }

  private final Runnable hide = () -> fade(0f);

  private void show () {
    removeCallbacks(hide);
    if (alpha < 1f) fade(1f);
    if (!dragging) postDelayed(hide, 1400);
  }

  private android.animation.ValueAnimator fader;

  private float fadeTarget;

  private void fade (float to) {
    // (test 2026-10-16: restarted on every scrolled frame, the fade-in never got past its first frame)
    if (fader != null && fader.isRunning() && fadeTarget == to) return;
    fadeTarget = to;
    if (fader != null) fader.cancel();
    fader = android.animation.ValueAnimator.ofFloat(alpha, to);
    fader.setDuration(to > alpha ? 120 : 260);
    fader.addUpdateListener(a -> {
      alpha = (float) a.getAnimatedValue();
      invalidate();
    });
    fader.start();
  }

  private float scrollFraction () {
    int range = list.computeVerticalScrollRange() - list.computeVerticalScrollExtent();
    return range <= 0 ? 0f : Math.max(0f, Math.min(1f, list.computeVerticalScrollOffset() / (float) range));
  }

  private float track () {
    return getHeight() - list.getPaddingTop() - thumbHeight - Screen.dp(8f);
  }

  private final java.util.List<android.graphics.Rect> exclusion = new java.util.ArrayList<>();
  private int excludedTop = Integer.MIN_VALUE;
  private float drawnTop;

  /**
   * The system «back» edge doesn't take the thumb while it is shown (Android 10+; the system allows ~200dp per edge,
   * so only around the thumb)
   */
  private void exclude (int thumbTop) {
    if (android.os.Build.VERSION.SDK_INT < 29 || thumbTop == excludedTop) return;
    excludedTop = thumbTop;
    exclusion.clear();
    if (thumbTop != Integer.MIN_VALUE) {
      exclusion.add(new android.graphics.Rect(getWidth() - Screen.dp(40f), thumbTop - Screen.dp(48f), getWidth(), thumbTop + thumbHeight + Screen.dp(48f)));
    }
    setSystemGestureExclusionRects(exclusion);
  }

  @Override
  protected void onDraw (Canvas c) {
    if (alpha <= 0f) {
      exclude(Integer.MIN_VALUE);
      return;
    }
    float top = list.getPaddingTop() + (dragging ? thumbY : scrollFraction() * track());
    drawnTop = top;
    exclude(Math.round(top) / Screen.dp(8f) * Screen.dp(8f)); // in steps: not a new request every frame
    int accent = Tgx101SavedHomeController.accent();
    float w = dragging ? Screen.dp(8f) : Screen.dp(5f);
    float right = getWidth() - Screen.dp(4f);
    thumbPaint.setColor(dragging ? accent : Theme.textDecentColor());
    thumbPaint.setAlpha(Math.round(alpha * (dragging ? 255 : 170)));
    rect.set(right - w, top, right, top + thumbHeight);
    c.drawRoundRect(rect, w / 2f, w / 2f, thumbPaint);
    if (dragging && label != null) {
      float tw = textPaint.measureText(label);
      float bh = Screen.dp(40f), pad = Screen.dp(14f);
      float bRight = right - Screen.dp(20f), bTop = Math.max(Screen.dp(4f), top + thumbHeight / 2f - bh / 2f);
      rect.set(bRight - tw - pad * 2, bTop, bRight, bTop + bh);
      bubblePaint.setColor(accent);
      bubblePaint.setAlpha(Math.round(alpha * 255));
      c.drawRoundRect(rect, bh / 2f, bh / 2f, bubblePaint);
      textPaint.setColor(0xffffffff);
      textPaint.setAlpha(Math.round(alpha * 255));
      c.drawText(label, rect.left + pad, rect.centerY() + textPaint.getTextSize() * .35f, textPaint);
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        // only on the shown thumb (a tap at a row's right edge stays the row's)
        if (alpha <= 0f || e.getX() < getWidth() - Screen.dp(40f) || e.getY() < drawnTop - Screen.dp(32f) || e.getY() > drawnTop + thumbHeight + Screen.dp(32f)) return false;
        dragging = true;
        removeCallbacks(hide);
        getParent().requestDisallowInterceptTouchEvent(true);
        Tgx101SwipeRow.closeOpen();
        drag(e.getY());
        return true;
      }
      case MotionEvent.ACTION_MOVE:
        if (dragging) drag(e.getY());
        return dragging;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (dragging) {
          dragging = false;
          label = null;
          invalidate();
          postDelayed(hide, 1400);
        }
        return true;
    }
    return false;
  }

  private void drag (float y) {
    float t = track();
    thumbY = Math.max(0f, Math.min(t, y - list.getPaddingTop() - thumbHeight / 2f));
    RecyclerView.Adapter<?> adapter = list.getAdapter();
    if (adapter == null || adapter.getItemCount() == 0) return;
    int position = Math.round((t <= 0 ? 0 : thumbY / t) * (adapter.getItemCount() - 1));
    if (list.getLayoutManager() instanceof LinearLayoutManager) {
      ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(position, 0);
    } else {
      list.scrollToPosition(position);
    }
    String next = labels.labelAt(position);
    long now = android.os.SystemClock.uptimeMillis();
    if (next != null && label != null && !next.equals(label) && now - lastTick > 90) { // a light tick on a new date
      lastTick = now;
      UI.forceVibrate(this, false);
    }
    label = next;
    invalidate();
  }
}
