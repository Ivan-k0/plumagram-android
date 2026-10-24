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
package org.thunderdog.challegram.component.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.ui.MessagesController;

/**
 * TGx101 (user 2026-10-24 «сделай как в официальном»): a far jump in a chat — the arrow down, a send while scrolled
 * up, a quote far above — no longer stands the list on a message 1.5 screens before the target (that one frame read as
 * a jump). As the official app does it: what's on screen now is photographed, the list is put on the target at once, and
 * in one move the old picture leaves while the real list comes in from the side the target lies on. Without memory for the two pictures, or if a picture fails, the old way is used.
 */
public final class Tgx101ListSwap extends View {
  private static final long DURATION = 300;

  private @Nullable Bitmap from;
  private final Rect area = new Rect();
  private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private boolean down;
  private float progress;
  private @Nullable ValueAnimator animator;
  private @Nullable View list;

  public Tgx101ListSwap (Context context) {
    super(context);
    setWillNotDraw(false);
  }

  private boolean pending; // put on the target, the second picture not taken yet

  public boolean isRunning () {
    return from != null || pending;
  }

  /**
   * @param list the chat list (its own position on screen and size are taken)
   * @param jump puts the list on the target, synchronously
   * @param down the target is newer (below): the new picture comes in from below
   * @return false — nothing done, the caller jumps its own way
   */
  public boolean swap (View list, Runnable jump, boolean down) {
    if (isRunning()) finish();
    int w = list.getWidth(), h = list.getHeight();
    if (w <= 0 || h <= 0 || getWidth() == 0) return false;
    long need = (long) w * h * 4;
    Runtime rt = Runtime.getRuntime();
    if (rt.maxMemory() - (rt.totalMemory() - rt.freeMemory()) < need * 3) {
      Tgx101Diag.mark("list swap: not enough memory, old way");
      return false;
    }
    Bitmap before = shot(list);
    if (before == null) return false;
    this.list = list;
    this.down = down;
    pending = true;
    jump.run();
    list.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
      @Override
      public boolean onPreDraw () {
        list.getViewTreeObserver().removeOnPreDrawListener(this);
        pending = false;
        start(before);
        return true;
      }
    });
    return true;
  }

  private @Nullable Bitmap shot (View list) {
    Bitmap b = null;
    try {
      b = Bitmap.createBitmap(list.getWidth(), list.getHeight(), Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(b);
      list.draw(c);
      return b;
    } catch (Throwable t) {
      if (b != null) b.recycle();
      Tgx101Diag.mark("list swap: picture failed " + t.getClass().getSimpleName() + ", old way");
      return null;
    }
  }

  // (user 2026-10-24 «при переходе по стрелке подложка медиа мигает»): the target used to be a picture too — taken right
  // after the jump, before its photos had loaded, so grey placeholders came in and the real ones popped at the end. Now
  // only what leaves is a picture; the real list comes in (moved by its translation), its media load as usual
  private float baseTranslation;

  private void start (Bitmap before) {
    View list = this.list;
    if (list == null) {
      before.recycle();
      return;
    }
    int[] a = new int[2], b = new int[2];
    list.getLocationInWindow(a);
    getLocationInWindow(b);
    baseTranslation = list.getTranslationY();
    int left = a[0] - b[0] - Math.round(list.getTranslationX()), top = a[1] - b[1] - Math.round(baseTranslation);
    area.set(left, top, left + list.getWidth(), top + list.getHeight());
    from = before;
    progress = 0f;
    list.setTranslationY(baseTranslation + (down ? area.height() : -area.height()));
    setVisibility(VISIBLE);
    animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(DURATION);
    animator.setInterpolator(MessagesController.tgx101RevealCurve());
    animator.addUpdateListener(v -> {
      progress = (float) v.getAnimatedValue();
      View l = this.list;
      if (l != null) l.setTranslationY(baseTranslation + (down ? area.height() : -area.height()) * (1f - progress));
      invalidate();
    });
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        finish();
      }
    });
    animator.start();
    Tgx101Diag.mark("list swap: " + (down ? "down" : "up") + ", " + area.width() + "x" + area.height());
  }

  public void finish () {
    pending = false;
    if (animator != null) {
      ValueAnimator a = animator;
      animator = null;
      a.removeAllListeners();
      a.cancel();
    }
    if (list != null) {
      if (from != null) list.setTranslationY(baseTranslation);
      list = null;
    }
    if (from != null) {
      from.recycle();
      from = null;
    }
    setVisibility(INVISIBLE);
  }

  @Override
  protected void onDraw (Canvas c) {
    if (from == null) return;
    int save = c.save();
    c.clipRect(area);
    float d = area.height();
    c.drawBitmap(from, area.left, area.top + (down ? -d : d) * progress, paint);
    c.restoreToCount(save);
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    finish();
  }
}
