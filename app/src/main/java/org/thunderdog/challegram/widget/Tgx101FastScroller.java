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
package org.thunderdog.challegram.widget;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Tgx101Haptics;

/**
 * TGx101 (user's video 2026-10-12 19:53 «нужна календарная плашка справа для быстрого перехода по дате»): while a long
 * grid scrolls, a handle shows on the right with the month of what's on screen; dragging it jumps through the whole list.
 * Drawn over the list itself (an item decoration), so it always matches the list wherever the sheet moves it.
 */
public final class Tgx101FastScroller extends RecyclerView.ItemDecoration implements RecyclerView.OnItemTouchListener {
  public interface LabelProvider {
    /** The label for the item at this adapter position (e.g. «Октябрь 2026»), null — none */
    @Nullable String labelAt (int position);
  }

  private final RecyclerView list;
  private final LabelProvider labels;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private float alpha;
  private boolean dragging;
  private float dragFraction = -1f;
  private @Nullable ValueAnimator fade;
  private @Nullable String label;
  private final Runnable hideLater = () -> fadeTo(0f);

  private Tgx101FastScroller (RecyclerView list, LabelProvider labels) {
    this.list = list;
    this.labels = labels;
    text.setTypeface(Fonts.getRobotoMedium());
    text.setTextSize(Screen.dp(13f));
  }

  public static Tgx101FastScroller attach (RecyclerView list, LabelProvider labels) {
    Tgx101FastScroller s = new Tgx101FastScroller(list, labels);
    list.addItemDecoration(s);
    list.addOnItemTouchListener(s);
    list.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (dy != 0 && s.isLongEnough()) s.show();
      }

      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE && !s.dragging) s.hideSoon();
      }
    });
    return s;
  }

  private boolean isLongEnough () {
    int range = list.computeVerticalScrollRange(), extent = list.computeVerticalScrollExtent();
    return extent > 0 && range > extent * 3;
  }

  private void show () {
    list.removeCallbacks(hideLater);
    if (alpha < 1f && (fade == null || !fade.isRunning() || (float) fade.getAnimatedValue() > alpha)) fadeTo(1f);
    hideSoon();
  }

  private void hideSoon () {
    list.removeCallbacks(hideLater);
    list.postDelayed(hideLater, 1500);
  }

  private void fadeTo (float to) {
    if (fade != null) fade.cancel();
    fade = ValueAnimator.ofFloat(alpha, to);
    fade.setDuration(to > alpha ? 150 : 250);
    fade.addUpdateListener(a -> { alpha = (float) a.getAnimatedValue(); list.invalidate(); });
    fade.start();
  }

  private float top () { return list.getPaddingTop() + Screen.dp(8f); }
  private float bottom () { return list.getHeight() - list.getPaddingBottom() - Screen.dp(8f); }
  private float thumbHeight () { return Screen.dp(44f); }

  private float fraction () {
    if (dragging && dragFraction >= 0f) return dragFraction;
    int range = list.computeVerticalScrollRange() - list.computeVerticalScrollExtent();
    return range > 0 ? Math.max(0f, Math.min(1f, list.computeVerticalScrollOffset() / (float) range)) : 0f;
  }

  private float thumbY () {
    return top() + (bottom() - top() - thumbHeight()) * fraction();
  }

  @Override
  public void onDrawOver (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    if (alpha <= 0f) return;
    float w = parent.getWidth();
    float y = thumbY(), h = thumbHeight();
    // the handle
    int accent = Theme.getColor(ColorId.fillingPositive);
    paint.setColor(withAlpha(accent, alpha));
    rect.set(w - Screen.dp(dragging ? 8f : 6f), y, w - Screen.dp(2f), y + h);
    c.drawRoundRect(rect, Screen.dp(3f), Screen.dp(3f), paint);
    // the month of what's on screen, next to the handle
    String l = currentLabel();
    if (l != null) {
      float tw = text.measureText(l), padX = Screen.dp(12f), ph = Screen.dp(30f);
      float right = w - Screen.dp(16f), cy = y + h / 2f;
      rect.set(right - tw - padX * 2, cy - ph / 2f, right, cy + ph / 2f);
      paint.setColor(withAlpha(0xe6151b22, alpha));
      paint.setShadowLayer(Screen.dp(4f), 0, Screen.dp(1f), withAlpha(0x40000000, alpha));
      c.drawRoundRect(rect, ph / 2f, ph / 2f, paint);
      paint.clearShadowLayer();
      text.setColor(withAlpha(0xffffffff, alpha));
      Paint.FontMetrics fm = text.getFontMetrics();
      c.drawText(l, rect.left + padX, cy - (fm.ascent + fm.descent) / 2f, text);
    }
  }

  private @Nullable String currentLabel () {
    RecyclerView.LayoutManager lm = list.getLayoutManager();
    int position = RecyclerView.NO_POSITION;
    if (lm instanceof LinearLayoutManager) position = ((LinearLayoutManager) lm).findFirstVisibleItemPosition();
    if (position == RecyclerView.NO_POSITION) return label;
    String l = labels.labelAt(position);
    if (l == null) l = labels.labelAt(position + 1);
    if (l != null && !l.equals(label)) {
      if (dragging && label != null) Tgx101Haptics.play(Tgx101Haptics.TICK); // a new month under the finger
      label = l;
    }
    return label;
  }

  private static int withAlpha (int color, float a) {
    return (Math.round((color >>> 24) * a) << 24) | (color & 0x00ffffff);
  }

  // dragging the handle

  private boolean hitsThumb (MotionEvent e) {
    if (alpha < .5f) return false;
    float y = thumbY();
    return e.getX() >= list.getWidth() - Screen.dp(36f) && e.getY() >= y - Screen.dp(16f) && e.getY() <= y + thumbHeight() + Screen.dp(16f);
  }

  @Override
  public boolean onInterceptTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN && hitsThumb(e)) {
      dragging = true;
      list.removeCallbacks(hideLater);
      if (list.getParent() != null) list.getParent().requestDisallowInterceptTouchEvent(true);
      Tgx101Haptics.play(Tgx101Haptics.TICK);
      list.stopScroll();
      return true;
    }
    return dragging;
  }

  @Override
  public void onTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_MOVE: {
        float f = (e.getY() - top() - thumbHeight() / 2f) / Math.max(1f, bottom() - top() - thumbHeight());
        dragFraction = Math.max(0f, Math.min(1f, f));
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        RecyclerView.LayoutManager lm = list.getLayoutManager();
        if (adapter != null && lm instanceof LinearLayoutManager && adapter.getItemCount() > 0) {
          int position = Math.min(adapter.getItemCount() - 1, Math.round(dragFraction * (adapter.getItemCount() - 1)));
          ((LinearLayoutManager) lm).scrollToPositionWithOffset(position, 0);
        }
        list.invalidate();
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        dragging = false;
        dragFraction = -1f;
        hideSoon();
        list.invalidate();
        break;
      }
    }
  }

  @Override
  public void onRequestDisallowInterceptTouchEvent (boolean disallowIntercept) { }
}
