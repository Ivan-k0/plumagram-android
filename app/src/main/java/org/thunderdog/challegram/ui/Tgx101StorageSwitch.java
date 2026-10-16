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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

/**
 * TGx101 (approved mockup 8 А, user 2026-10-16): the round button that takes Saved Messages from the chat to the storage
 * and back. A finger drags it anywhere; let go, it sticks to the nearer side and stays there (apart for the chat and the
 * storage). Held still — «Hide the button»; it comes back in the storage's settings.
 */
public final class Tgx101StorageSwitch extends View {
  public static final int IN_CHAT = 0, IN_STORAGE = 1;

  public interface Host {
    /** the room kept free at the bottom (the input) */
    int bottomReserved ();
    /** how far above the input the button sits in its usual place */
    int usualGap ();
    /** the list under the button: a swipe that starts on the button goes on there */
    @Nullable View passTarget ();
  }

  private final ViewController<?> controller;
  private final Tdlib tdlib;
  private final int where;
  private final Host host;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final @Nullable Drawable chatIcon;
  private final int size = Screen.dp(52f), margin = Screen.dp(12f);

  public Tgx101StorageSwitch (Context context, ViewController<?> controller, int where, Host host) {
    super(context);
    this.controller = controller;
    this.tdlib = controller.tdlib();
    this.where = where;
    this.host = host;
    this.chatIcon = where == IN_STORAGE ? ContextCompat.getDrawable(context, R.drawable.baseline_forum_24) : null;
    setContentDescription(Lang.getString(where == IN_CHAT ? R.string.Tgx101SavedSwitchToStorage : R.string.Tgx101SavedSwitchToChat));
    if (android.os.Build.VERSION.SDK_INT >= 21) {
      setElevation(Screen.dp(2f)); // a light shadow, as the arrow
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setOval(0, 0, size, size);
        }
      });
    }
    addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow (View v) {
        ViewParent p = getParent();
        if (p instanceof View) ((View) p).addOnLayoutChangeListener(parentLayout);
      }

      @Override
      public void onViewDetachedFromWindow (View v) {
        ViewParent p = getParent();
        if (p instanceof View) ((View) p).removeOnLayoutChangeListener(parentLayout);
      }
    });
  }

  /** for the parent: top-left, moved by its translation */
  public static ViewGroup.LayoutParams params (ViewGroup parent) {
    int s = Screen.dp(52f);
    if (parent instanceof android.widget.RelativeLayout) {
      android.widget.RelativeLayout.LayoutParams p = new android.widget.RelativeLayout.LayoutParams(s, s);
      p.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
      p.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
      return p;
    }
    return new android.widget.FrameLayout.LayoutParams(s, s, android.view.Gravity.TOP | android.view.Gravity.LEFT);
  }

  private final OnLayoutChangeListener parentLayout = (v, l, t, r, b, ol, ot, or, ob) -> {
    // (user's video 2026-10-16 16:26) while the keyboard is up the screen is shorter: the button only fades, it isn't
    // placed for that height (it slid down on the way out) — it comes back where it was once the keyboard goes
    if (!isDragging() && !isKeyboardShown() && (r - l != or - ol || b - t != ob - ot || getTranslationY() == 0f)) place(false);
  };

  private Tgx101SavedStore store () {
    return Tgx101SavedStore.get(tdlib);
  }

  private boolean keyboardShown, shown;

  /** (user 2026-10-16 «когда открыта клавиатура — плавающая кнопка не нужна») */
  public void setKeyboardShown (boolean visible) {
    if (keyboardShown != visible) {
      keyboardShown = visible;
      updateVisibility();
    }
  }

  /** shown while the storage and the button are on and the keyboard is down; comes and goes smoothly, as in the storage */
  public void updateVisibility () {
    Tgx101SavedStore s = store();
    boolean show = s.storageOn && s.switchButton && !keyboardShown;
    if (show == shown) return;
    shown = show;
    if (show) {
      if (getVisibility() != VISIBLE) {
        setVisibility(VISIBLE);
        setAlpha(0f);
        setScaleX(.7f);
        setScaleY(.7f);
        place(false);
      }
      animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).withEndAction(null).start();
    } else {
      animate().alpha(0f).scaleX(.7f).scaleY(.7f).setDuration(160).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(() -> {
        if (!shown) setVisibility(GONE);
      }).start();
    }
  }

  // the room the button moves in: [top … bottom] for its upper edge
  private int minY () {
    return margin;
  }

  private int maxY () {
    View p = (View) getParent();
    return p == null ? 0 : Math.max(minY(), p.getHeight() - host.bottomReserved() - size - margin);
  }

  private float sideX (boolean left) {
    View p = (View) getParent();
    return left ? margin : (p == null ? 0 : p.getWidth() - size - margin);
  }

  /** to its saved place (or the usual one: right, just above the input) */
  public void place (boolean animated) {
    View p = (View) getParent();
    if (p == null || p.getWidth() == 0) return;
    Tgx101SavedStore s = store();
    float f = s.switchY[where];
    int min = minY(), max = maxY();
    float y = f < 0f ? Math.max(min, p.getHeight() - host.bottomReserved() - size - host.usualGap()) : min + (max - min) * f;
    float x = sideX(s.switchLeft[where]);
    if (animated) {
      animate().translationX(x).translationY(y).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    } else {
      animate().cancel();
      setTranslationX(x);
      setTranslationY(y);
    }
  }

  // touch (user 2026-10-16 «перемещаться только после удержания … свайп должен продолжаться»): a tap switches; a finger
  // that moves at once is a swipe — it goes on in the list under the button; held still, the button lifts and follows
  // the finger, and let go where it was held it offers «Hide the button»

  private float downX, downY, startX, startY;
  private boolean dragging, held, pressed, passing;
  private @Nullable MotionEvent downEvent;
  private final Runnable hold = this::onHold;

  private void onHold () {
    held = true;
    UI.forceVibrate(this, true);
    // lifted: a bit bigger, ready to be carried
    animate().scaleX(1.12f).scaleY(1.12f).setDuration(140).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
  }

  private void showHideMenu () {
    controller.showOptions(null, new int[] {R.id.btn_tgx101SavedSwitch}, new String[] {Lang.getString(R.string.Tgx101SavedSwitchHide)}, (itemView, id) -> {
      Tgx101SavedStore s = store();
      s.switchButton = false;
      s.save();
      org.thunderdog.challegram.Tgx101Diag.mark("storage switch: hidden");
      shown = false;
      animate().alpha(0f).scaleX(.6f).scaleY(.6f).setDuration(160).withEndAction(() -> {
        setVisibility(GONE);
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
      }).start();
      UI.showToast(R.string.Tgx101SavedSwitchHidden, android.widget.Toast.LENGTH_SHORT);
      return true;
    });
  }

  private boolean isKeyboardShown () {
    return keyboardShown;
  }

  private boolean isDragging () {
    return dragging || held;
  }

  private void setPressedState (boolean on) {
    if (pressed == on) return;
    pressed = on;
    animate().scaleX(on ? .92f : 1f).scaleY(on ? .92f : 1f).setDuration(120).start();
  }

  private void dropDownEvent () {
    if (downEvent != null) {
      downEvent.recycle();
      downEvent = null;
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (passing) {
      View target = host.passTarget();
      if (target != null) {
        // the down was handed over where the button then stood: the rest goes from the same place
        MotionEvent copy = MotionEvent.obtain(e);
        copy.offsetLocation(passDx, passDy);
        target.dispatchTouchEvent(copy);
        copy.recycle();
      }
      if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) passing = false;
      return true;
    }
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        downX = e.getRawX();
        downY = e.getRawY();
        startX = getTranslationX();
        startY = getTranslationY();
        dragging = held = passing = false;
        dropDownEvent();
        downEvent = MotionEvent.obtain(e);
        setPressedState(true);
        postDelayed(hold, ViewConfiguration.getLongPressTimeout());
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
        boolean moved = Math.hypot(dx, dy) > ViewConfiguration.get(getContext()).getScaledTouchSlop();
        if (!held) {
          if (moved) {
            // a swipe that started on the button: the list under it takes it
            removeCallbacks(hold);
            setPressedState(false);
            View target = host.passTarget();
            if (target != null && downEvent != null) {
              // the list may sit deeper than a sibling (the chat's pager): by window places
              getLocationInWindow(locSelf);
              target.getLocationInWindow(locTarget);
              passDx = locSelf[0] - locTarget[0];
              passDy = locSelf[1] - locTarget[1];
              passing = true;
              MotionEvent down = MotionEvent.obtain(downEvent);
              down.offsetLocation(passDx, passDy);
              target.dispatchTouchEvent(down);
              down.recycle();
              MotionEvent move = MotionEvent.obtain(e);
              move.offsetLocation(passDx, passDy);
              target.dispatchTouchEvent(move);
              move.recycle();
            }
            dropDownEvent();
          }
          return true;
        }
        if (!dragging && moved) {
          dragging = true;
          getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (dragging) {
          View p = (View) getParent();
          float x = Math.max(0, Math.min(p.getWidth() - size, startX + dx));
          float y = Math.max(minY(), Math.min(maxY(), startY + dy));
          setTranslationX(x);
          setTranslationY(y);
        }
        return true;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        removeCallbacks(hold);
        dropDownEvent();
        boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
        pressed = false;
        animate().scaleX(1f).scaleY(1f).setDuration(160).start();
        if (dragging) {
          View p = (View) getParent();
          Tgx101SavedStore s = store();
          s.switchLeft[where] = getTranslationX() + size / 2f < p.getWidth() / 2f;
          int min = minY(), max = maxY();
          s.switchY[where] = max > min ? (getTranslationY() - min) / (max - min) : 0f;
          s.save();
          org.thunderdog.challegram.Tgx101Diag.mark("storage switch: moved to " + (s.switchLeft[where] ? "left" : "right") + " " + Math.round(s.switchY[where] * 100) + "%");
          place(true);
        } else if (up && held) {
          showHideMenu();
        } else if (up) {
          UI.forceVibrate(this, false);
          go();
        }
        dragging = held = false;
        return true;
      }
    }
    return true;
  }

  private float passDx, passDy;
  private final int[] locSelf = new int[2], locTarget = new int[2];

  /** to the other side: back if it lies right under this screen, else opened on top */
  private void go () {
    org.thunderdog.challegram.Tgx101Diag.mark("storage switch: " + (where == IN_CHAT ? "chat → storage" : "storage → chat"));
    ViewController<?> prev = controller.context().navigation().getPreviousStackItem();
    if (where == IN_CHAT) {
      if (prev instanceof Tgx101SavedHomeController && prev.tdlib() == tdlib) controller.navigateBack();
      else Tgx101SavedHomeController.open(controller.context(), tdlib);
    } else {
      if (prev instanceof MessagesController && prev.tdlib() == tdlib && ((MessagesController) prev).getChatId() == tdlib.selfChatId()) controller.navigateBack();
      else Tgx101SavedKit.openInFeed(controller, null);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    float r = size / 2f;
    if (android.os.Build.VERSION.SDK_INT < 21) { // no elevation: a soft shadow under it
      paint.setColor(0x33000000);
      c.drawCircle(r, r + Screen.dp(1.5f), r - Screen.dp(.5f), paint);
    }
    // (user 2026-10-16 «полупрозрачными, как поле ввода или стрелка»): the chats' «down» arrow — a see-through light circle
    paint.setColor(me.vkryl.core.ColorUtils.alphaColor(.9f, org.thunderdog.challegram.theme.Theme.fillingColor()));
    c.drawCircle(r, r, r - (android.os.Build.VERSION.SDK_INT < 21 ? Screen.dp(1f) : 0), paint);
    final int iconColor = Tgx101SavedHomeController.accent();
    if (where == IN_CHAT) {
      // the storage: four tiles
      paint.setColor(iconColor);
      float cell = Screen.dp(8f), gap = Screen.dp(3f), corner = Screen.dp(2f);
      float start = r - cell - gap / 2f;
      for (int i = 0; i < 4; i++) {
        float x = start + (i % 2) * (cell + gap), y = start + (i / 2) * (cell + gap);
        rect.set(x, y, x + cell, y + cell);
        c.drawRoundRect(rect, corner, corner, paint);
      }
    } else if (chatIcon != null) {
      int s = Screen.dp(24f);
      int left = Math.round(r - s / 2f);
      chatIcon.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
      chatIcon.setBounds(left, left, left + s, left + s);
      chatIcon.draw(c);
    }
  }
}
