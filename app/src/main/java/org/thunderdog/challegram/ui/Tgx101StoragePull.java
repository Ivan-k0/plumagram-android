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
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Tgx101Haptics;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * TGx101 (user 2026-10-15/16): on the first chat folder the finger pulled to the right — where there are no folders — opens
 * Saved as a storage, the way pulling down opens the search: a circle with a bookmark slides in from the left edge at the
 * finger, its ring fills, one short vibration at the threshold, and it opens when released past it. Off in MagiX → Gestures.
 */
public final class Tgx101StoragePull extends View {
  public interface Host {
    boolean canPull (); // on the first folder, nothing else going on
    void open ();
  }

  private static final float THRESHOLD_DP = 96f;

  private final Host host;
  private final int slop;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF arc = new RectF();
  private Drawable icon;
  private boolean tracking, active, ready;
  private float startX, startY, fingerY, distance;
  private android.animation.ValueAnimator back;

  public Tgx101StoragePull (Context context, Host host) {
    super(context);
    this.host = host;
    this.slop = ViewConfiguration.get(context).getScaledTouchSlop();
    setWillNotDraw(false);
  }

  /** from the pager's dispatch: true — the pager doesn't get this gesture any more */
  public boolean onPagerTouch (View pager, MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        tracking = Settings.instance().tgx101SwipeToStorage() && host.canPull();
        active = ready = false;
        startX = e.getX();
        startY = e.getY();
        return false;
      case MotionEvent.ACTION_MOVE: {
        float dx = e.getX() - startX, dy = e.getY() - startY;
        if (active) {
          fingerY = e.getRawY();
          setDistance(Math.max(0f, dx));
          return true;
        }
        if (!tracking) return false;
        if (dx < -slop || Math.abs(dy) > Math.max(slop, dx)) {
          tracking = false; // left goes through the folders, vertical scrolls the chats
          return false;
        }
        if (dx > slop * 1.5f && dx > Math.abs(dy) * 2f) {
          tracking = false;
          active = true;
          if (back != null) back.cancel();
          fingerY = e.getRawY();
          MotionEvent cancel = MotionEvent.obtain(e);
          cancel.setAction(MotionEvent.ACTION_CANCEL);
          pager.onTouchEvent(cancel); // the list under the finger lets go (no tap, no long press)
          for (int i = 0; i < ((android.view.ViewGroup) pager).getChildCount(); i++) ((android.view.ViewGroup) pager).getChildAt(i).dispatchTouchEvent(cancel);
          cancel.recycle();
          setDistance(dx);
          return true;
        }
        return false;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        tracking = false;
        if (!active) return false;
        active = false;
        boolean open = e.getActionMasked() == MotionEvent.ACTION_UP && ready;
        animateBack();
        if (open) host.open();
        return true;
      }
    }
    return false;
  }

  private void setDistance (float d) {
    distance = d;
    boolean r = d >= Screen.dp(THRESHOLD_DP);
    if (r != ready) {
      ready = r;
      if (r) Tgx101Haptics.play(Tgx101Haptics.CLICK);
    }
    invalidate();
  }

  private void animateBack () {
    if (back != null) back.cancel();
    if (distance <= 0f) return;
    back = android.animation.ValueAnimator.ofFloat(distance, 0f);
    back.setDuration(200);
    back.setInterpolator(new android.view.animation.DecelerateInterpolator());
    back.addUpdateListener(a -> {
      distance = (float) a.getAnimatedValue();
      invalidate();
    });
    back.start();
  }

  @Override
  protected void onDraw (Canvas c) {
    if (distance <= 0f) return;
    float threshold = Screen.dp(THRESHOLD_DP);
    float progress = Math.min(1f, distance / threshold);
    float radius = Screen.dp(20f);
    int[] at = new int[2];
    getLocationOnScreen(at);
    float cy = Math.max(radius * 2, Math.min(getHeight() - radius * 2, fingerY - at[1]));
    float cx = -radius + Math.min(distance, threshold * 1.15f) * .62f; // slides in from the left edge, slower than the finger
    float alpha = Math.min(1f, distance / Screen.dp(24f));
    boolean full = ready || distance >= threshold;
    int accent = Theme.getColor(ColorId.fillingPositive);
    int filling = Theme.getColor(ColorId.filling);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(me.vkryl.core.ColorUtils.alphaColor(.18f * alpha, 0xff000000));
    c.drawCircle(cx + Screen.dp(1f), cy + Screen.dp(1.5f), radius + Screen.dp(1f), paint);
    paint.setColor(me.vkryl.core.ColorUtils.alphaColor(alpha, full ? accent : filling));
    c.drawCircle(cx, cy, radius, paint);
    if (!full) {
      float ring = radius - Screen.dp(4f);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(Screen.dp(2.5f));
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setColor(me.vkryl.core.ColorUtils.alphaColor(alpha * .25f, accent));
      c.drawCircle(cx, cy, ring, paint);
      paint.setColor(me.vkryl.core.ColorUtils.alphaColor(alpha, accent));
      arc.set(cx - ring, cy - ring, cx + ring, cy + ring);
      c.drawArc(arc, -90f, 360f * progress, false, paint);
    }
    if (icon == null) {
      icon = Drawables.get(getResources(), R.drawable.baseline_bookmark_24);
      if (icon != null) icon = icon.mutate();
    }
    if (icon != null) {
      int size = Screen.dp(full ? 22f : 18f);
      icon.setColorFilter(Paints.getColorFilter(full ? 0xffffffff : accent));
      icon.setAlpha((int) (255 * alpha));
      icon.setBounds((int) (cx - size / 2f), (int) (cy - size / 2f), (int) (cx + size / 2f), (int) (cy + size / 2f));
      icon.draw(c);
    }
  }
}
