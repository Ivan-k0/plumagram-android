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
  // (user photo 2026-10-20 15:58 «стрелку и плавающую нашу кнопку — один отступ с края, лучше как стрелка»): 16 dp, as the «down» arrow
  private final int size = Screen.dp(52f), margin = Screen.dp(16f);

  public Tgx101StorageSwitch (Context context, ViewController<?> controller, int where, Host host) {
    super(context);
    this.controller = controller;
    this.tdlib = controller.tdlib();
    this.where = where;
    this.host = host;
    this.chatIcon = where == IN_STORAGE ? ContextCompat.getDrawable(context, R.drawable.baseline_forum_24) : null;
    setContentDescription(Lang.getString(where == IN_CHAT ? R.string.Tgx101SavedSwitchToStorage : R.string.Tgx101SavedSwitchToChat));
    if (android.os.Build.VERSION.SDK_INT >= 21) {
      // (user 2026-10-17 «внутри с какой-то фигурой, должно быть просто прозрачно»): no elevation — its shadow showed
      // through the see-through circle as a dark shape; the hairline is enough
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
    // (user 2026-10-18 «кнопка всё равно уезжает после открытия клавиатуры»): the screen changed its height a moment before
    // the keyboard flag did — placed for the short screen, it stayed there. The keyboard on screen is asked directly
    if (!isDragging() && !isKeyboardShown() && !imeOnScreen() && (r - l != or - ol || b - t != ob - ot || getTranslationY() == 0f)) place(false);
  };

  private Tgx101SavedStore store () {
    return Tgx101SavedStore.get(tdlib);
  }

  private boolean keyboardShown, shown;

  /** (user 2026-10-16 «когда открыта клавиатура — плавающая кнопка не нужна») */
  public void setKeyboardShown (boolean visible) {
    // (user 2026-10-16 again «перестала появляться»: hidden for a keyboard that never really came — a false «shown» at
    // the start, and the «hidden» never followed) Android 11+ tells for sure whether the keyboard is on screen
    if (visible && !imeReallyVisible()) visible = false;
    if (keyboardShown != visible) {
      keyboardShown = visible;
      org.thunderdog.challegram.Tgx101Diag.mark("storage switch: keyboard " + (visible ? "up" : "down"));
      removeCallbacks(afterKeyboard);
      if (visible) {
        updateVisibility();
      } else {
        // (test 2026-10-18 in the Saved chat: after the keyboard the button stood a third of the screen higher) the field
        // rides down with the keyboard for ~300 ms — placed while it was still up, the button took that for the bottom.
        // It comes back where it belongs once the field is down
        postDelayed(afterKeyboard, 360);
      }
    }
  }

  private final Runnable afterKeyboard = () -> {
    if (keyboardShown || isDragging()) return;
    if (shown && getVisibility() == VISIBLE && getAlpha() >= 1f) place(false);
    updateVisibility();
  };

  private boolean imeOnScreen () {
    if (android.os.Build.VERSION.SDK_INT < 30) return keyboardShown;
    android.view.WindowInsets insets = getRootWindowInsets();
    return insets != null && insets.isVisible(android.view.WindowInsets.Type.ime());
  }

  private boolean imeReallyVisible () {
    if (android.os.Build.VERSION.SDK_INT < 30) return true; // older ones: trust the screen's own keyboard tracking
    android.view.WindowInsets insets = getRootWindowInsets();
    return insets == null || insets.isVisible(android.view.WindowInsets.Type.ime());
  }

  /** shown while the storage and the button are on and the keyboard is down; comes and goes smoothly, as in the storage */
  public void updateVisibility () {
    if (keyboardShown && !imeReallyVisible()) keyboardShown = false; // no keyboard on screen after all
    Tgx101SavedStore s = store();
    boolean show = s.storageOn && s.switchButton && !keyboardShown;
    if (show == shown) return;
    org.thunderdog.challegram.Tgx101Diag.mark("storage switch: " + (show ? "shown" : "hidden") + " (" + (where == IN_CHAT ? "chat" : "storage") + ", storage " + s.storageOn + ", button " + s.switchButton + ", keyboard " + keyboardShown + ")");
    shown = show;
    if (show) {
      if (getVisibility() != VISIBLE || getAlpha() < 1f) {
        setVisibility(VISIBLE);
        place(false); // first in its place (also when it was still fading out), then it fades in there
        setAlpha(0f);
        setScaleX(.7f);
        setScaleY(.7f);
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
    // (user 2026-10-17 «кнопка не возвращается на своё место»): down to its usual place at least — the limit stood above it
    return p == null ? 0 : Math.max(minY(), Math.max(p.getHeight() - host.bottomReserved() - size - margin, usualY()));
  }

  // the usual place: the same height on both screens — 76 dp above the field (the chat's «down» arrow's place); the
  // storage's 12 dp put it lower there and the button jumped between the screens
  private static final float USUAL_GAP_DP = 76f;

  private int usualY () {
    View p = (View) getParent();
    return p == null ? 0 : Math.max(minY(), p.getHeight() - host.bottomReserved() - size - Screen.dp(USUAL_GAP_DP));
  }

  /** a moved place: so many dp from the screen's bottom, the same on both screens (kept inside this one's room) */
  private float movedY (float bottomDp) {
    View p = (View) getParent();
    if (p == null) return minY();
    return Math.max(minY(), Math.min(maxY(), p.getHeight() - Screen.dp(bottomDp) - size));
  }

  // (user 2026-10-17 «кнопки должны перемещаться на обоих экранах одинаково»): one place for the chat and the storage
  private static final int SPOT = IN_CHAT;

  private float sideX (boolean left) {
    View p = (View) getParent();
    return left ? margin : (p == null ? 0 : p.getWidth() - size - margin);
  }

  /** to its saved place (or the usual one: right, just above the input) */
  public void place (boolean animated) {
    View p = (View) getParent();
    if (p == null || p.getWidth() == 0) return;
    Tgx101SavedStore s = store();
    float y = s.switchBottomDp < 0f ? usualY() : movedY(s.switchBottomDp);
    float x = sideX(s.switchLeft[SPOT]);
    if (animated) {
      animate().translationX(x).translationY(y).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    } else {
      // (2026-10-16: the button stayed invisible) cancelling stopped its fade-in at alpha 0 as well — what it was
      // fading to is set at once
      animate().cancel();
      setTranslationX(x);
      setTranslationY(y);
      if (shown) {
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
      } else if (getVisibility() == VISIBLE) {
        setVisibility(GONE);
      }
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
          boolean left = getTranslationX() + size / 2f < p.getWidth() / 2f;
          int min = minY(), max = maxY();
          s.switchLeft[SPOT] = left;
          if (!left && Math.abs(getTranslationY() - usualY()) < Screen.dp(40f)) {
            s.switchBottomDp = -1f; // let go near its usual place: back there exactly
          } else {
            s.switchBottomDp = Screen.px(p.getHeight() - getTranslationY() - size);
          }
          s.switchLeft[1 - SPOT] = s.switchLeft[SPOT];
          s.save();
          org.thunderdog.challegram.Tgx101Diag.mark("storage switch: moved to " + (left ? "left" : "right") + " " + (s.switchBottomDp < 0f ? "usual place" : Math.round(s.switchBottomDp) + " dp from the bottom"));
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
    float body = r - (android.os.Build.VERSION.SDK_INT < 21 ? Screen.dp(1f) : 0);
    c.drawCircle(r, r, body, paint);
    // a hairline round it, as the field: it doesn't melt into a dark list
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(Math.max(1, Screen.dp(.5f)));
    paint.setColor(org.thunderdog.challegram.theme.Theme.separatorColor());
    c.drawCircle(r, r, body - paint.getStrokeWidth() / 2f, paint);
    paint.setStyle(Paint.Style.FILL);
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
