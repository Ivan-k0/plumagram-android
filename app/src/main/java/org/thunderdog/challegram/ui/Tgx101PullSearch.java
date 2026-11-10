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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Tgx101Haptics;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 (user 2026-11-10 «поиск в настройках по-прежнему без предупреждения, без страховки, как с архивом или поиском в
 * списке чатов»): a pull down from the very top of a list opens its search the way the chat list's does — a circle with a
 * magnifier slides down, its ring fills; past the line the finger has to stay 0.15 s (the ring closes, the circle turns
 * blue, one tick), and the search opens only when the finger is let go there. Pulling back or letting go earlier cancels.
 */
public final class Tgx101PullSearch extends RecyclerView.ItemDecoration implements RecyclerView.OnItemTouchListener {
  /** the line to pull past — the chat list's too (ChatsRecyclerView.PULL_THRESHOLD_DP) */
  public static final float THRESHOLD_DP = 84f;
  private static final float SLOP_DP = 10f;
  private static final long HOLD_MS = 150;

  private final RecyclerView list;
  private final Runnable open;
  private boolean tracking, active, over, ready;
  private float startX, startY, distance;
  private long overAt;
  private @Nullable android.animation.ValueAnimator back;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private @Nullable Drawable icon;

  private final Runnable arm = new Runnable() {
    @Override
    public void run () {
      if (active && over) {
        ready = true;
        Tgx101Haptics.play(Tgx101Haptics.CLICK);
        list.invalidate();
      }
    }
  };

  private Tgx101PullSearch (RecyclerView list, Runnable open) {
    this.list = list;
    this.open = open;
  }

  public static void attach (RecyclerView list, Runnable open) {
    Tgx101PullSearch pull = new Tgx101PullSearch(list, open);
    list.addOnItemTouchListener(pull);
    list.addItemDecoration(pull);
  }

  @Override
  public boolean onInterceptTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        tracking = rv.getScrollState() == RecyclerView.SCROLL_STATE_IDLE && !rv.canScrollVertically(-1);
        active = over = ready = false;
        startX = e.getX();
        startY = e.getY();
        return false;
      case MotionEvent.ACTION_MOVE: {
        if (!tracking) return false;
        if (e.getPointerCount() > 1 || e.getEventTime() - e.getDownTime() > ViewConfiguration.getLongPressTimeout()) {
          tracking = false;
          return false;
        }
        float dx = Math.abs(e.getX() - startX), dy = e.getY() - startY;
        if (dy < -Screen.dp(8f) || dx > Screen.dp(24f)) {
          tracking = false; // scrolling up or sideways: not a pull
          return false;
        }
        if (dy >= Screen.dp(SLOP_DP) && dy > dx * 2f && !rv.canScrollVertically(-1)) {
          // the pull starts: the list doesn't get this gesture any more (no tap, no scroll)
          tracking = false;
          active = true;
          if (back != null) back.cancel();
          setDistance(dy);
          return true;
        }
        return false;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        tracking = false;
        return false;
    }
    return false;
  }

  @Override
  public void onTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) {
    if (!active) return;
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_MOVE:
        setDistance(Math.max(0f, e.getY() - startY));
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        boolean go = e.getActionMasked() == MotionEvent.ACTION_UP && ready;
        active = false;
        rv.removeCallbacks(arm);
        over = ready = false;
        animateBack();
        if (go) {
          org.thunderdog.challegram.Tgx101Diag.mark("settings: pulled down and let go past the line, search opens");
          open.run();
        }
        break;
      }
    }
  }

  @Override
  public void onRequestDisallowInterceptTouchEvent (boolean disallowIntercept) { }

  private void setDistance (float d) {
    distance = d;
    boolean nowOver = d >= Screen.dp(THRESHOLD_DP);
    if (nowOver != over) {
      over = nowOver;
      overAt = android.os.SystemClock.uptimeMillis();
      list.removeCallbacks(arm);
      if (nowOver) {
        list.postDelayed(arm, HOLD_MS);
      } else {
        ready = false;
      }
    }
    list.invalidate();
  }

  private void animateBack () {
    if (back != null) back.cancel();
    if (distance <= 0f) return;
    back = android.animation.ValueAnimator.ofFloat(distance, 0f);
    back.setDuration(200);
    back.setInterpolator(new android.view.animation.DecelerateInterpolator());
    back.addUpdateListener(a -> {
      distance = (float) a.getAnimatedValue();
      list.invalidate();
    });
    back.start();
  }

  @Override
  public void onDrawOver (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    if (distance <= 0f) return;
    float threshold = Screen.dp(THRESHOLD_DP);
    float progress = Math.min(.85f, distance / threshold);
    if (over && !ready) {
      progress = .85f + .15f * Math.min(1f, (android.os.SystemClock.uptimeMillis() - overAt) / (float) HOLD_MS);
      parent.postInvalidateOnAnimation();
    }
    float radius = Screen.dp(20f);
    float cx = parent.getWidth() / 2f;
    float cy = parent.getPaddingTop() - radius + Math.min(distance, threshold * 1.15f) * .62f;
    float alpha = Math.min(1f, distance / Screen.dp(24f));
    boolean isReady = ready && active;
    int accent = Theme.getColor(ColorId.fillingPositive);
    int filling = Theme.getColor(ColorId.filling);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(ColorUtils.alphaColor(.18f * alpha, 0xff000000));
    c.drawCircle(cx, cy + Screen.dp(1.5f), radius + Screen.dp(1f), paint);
    paint.setColor(ColorUtils.alphaColor(alpha, isReady ? accent : filling));
    c.drawCircle(cx, cy, radius, paint);
    if (!isReady) {
      float ring = radius - Screen.dp(4f);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(Screen.dp(2.5f));
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setColor(ColorUtils.alphaColor(alpha * .25f, accent));
      c.drawCircle(cx, cy, ring, paint);
      paint.setColor(ColorUtils.alphaColor(alpha, accent));
      c.drawArc(new RectF(cx - ring, cy - ring, cx + ring, cy + ring), -90f, 360f * progress, false, paint); // API 16 overload
    }
    if (icon == null) {
      icon = Drawables.get(parent.getResources(), R.drawable.baseline_search_24);
      if (icon != null) icon = icon.mutate();
    }
    if (icon != null) {
      int size = Screen.dp(isReady ? 22f : 18f);
      icon.setColorFilter(Paints.getColorFilter(isReady ? 0xffffffff : accent));
      icon.setAlpha((int) (255 * alpha));
      icon.setBounds((int) (cx - size / 2f), (int) (cy - size / 2f), (int) (cx + size / 2f), (int) (cy + size / 2f));
      icon.draw(c);
    }
  }
}
