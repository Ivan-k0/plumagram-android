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
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.PopupLayout;

/**
 * TGx101 (user 2026-10-15 «свайпы сворачивания всплывающих окон — рабочие и плавные»): a bottom sheet that follows the
 * finger down and closes past a third of its height or on a quick flick (springs back otherwise); a tap outside closes
 * it; it rises above the keyboard. The content is any view; a scrolling list inside keeps its own scroll until it is
 * at the top.
 */
public final class Tgx101Sheet {
  private Tgx101Sheet () { }

  public static final class Handle {
    public final PopupLayout popup;
    public final LinearLayout body;

    Handle (PopupLayout popup, LinearLayout body) {
      this.popup = popup;
      this.body = body;
    }

    public void dismiss () {
      if (!popup.isWindowHidden()) popup.hideWindow(true);
    }
  }

  /** A sheet with a drag handle; {@code fill} adds content to its column before it is shown */
  public static Handle show (ViewController<?> c, @Nullable Runnable onDismiss, me.vkryl.core.lambda.RunnableData<LinearLayout> fill) {
    Context context = c.context();
    final PopupLayout popup = new PopupLayout(context);
    popup.init(true);
    popup.setNeedRootInsets();

    final DragSheet sheet = new DragSheet(context);
    sheet.setOrientation(LinearLayout.VERTICAL);
    sheet.setPadding(0, Screen.dp(10f), 0, Screen.dp(14f));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Theme.fillingColor());
    bg.setCornerRadius(Screen.dp(22f));
    sheet.setBackground(bg);
    sheet.setClickable(true); // taps on the sheet stay in it

    View grip = new View(context);
    GradientDrawable gripBg = new GradientDrawable();
    gripBg.setColor(Theme.separatorColor());
    gripBg.setCornerRadius(Screen.dp(2f));
    grip.setBackground(gripBg);
    LinearLayout.LayoutParams gripParams = new LinearLayout.LayoutParams(Screen.dp(36f), Screen.dp(4f));
    gripParams.gravity = Gravity.CENTER_HORIZONTAL;
    gripParams.bottomMargin = Screen.dp(8f);
    sheet.addView(grip, gripParams);

    // the content scrolls when it is taller than ~¾ of the screen (many folders / tags)
    final int maxBody = Math.round(Screen.currentHeight() * .72f);
    android.widget.ScrollView scroll = new android.widget.ScrollView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxBody, MeasureSpec.AT_MOST));
      }
    };
    scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
    scroll.setVerticalScrollBarEnabled(false);
    LinearLayout body = new LinearLayout(context);
    body.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(body, new android.widget.ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    fill.runWithData(body);

    final FrameLayout wrap = new FrameLayout(context);
    FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    sheetParams.leftMargin = sheetParams.rightMargin = sheetParams.bottomMargin = Screen.dp(8f);
    wrap.addView(sheet, sheetParams);
    wrap.setClickable(true);
    wrap.setOnClickListener(v -> popup.hideWindow(true)); // a tap outside only closes the sheet
    sheet.popup = popup;

    // above the keyboard (the sheet sits in the activity's own window)
    final android.graphics.Rect frame = new android.graphics.Rect();
    final int[] location = new int[2];
    // (test 2026-10-15: the visible-frame guess lifted a sheet with no field ~250 px off the bottom) — Android 11+ asks
    // the system for the keyboard itself; older ones only lift a sheet that has a focused text field
    final int[] imeTarget = {-1};
    final android.animation.ValueAnimator[] imeAnimator = new android.animation.ValueAnimator[1];
    // a sheet with a text field: the screen under it doesn't pan up for the keyboard (that and the sheet's own rise made
    // a double jump); the sheet rises by itself. Put back when it closes, and its keyboard goes with it
    final boolean hasInput = findInput(body) != null;
    final android.view.Window activityWindow = c.context().getWindow();
    final int previousSoftInput = activityWindow != null ? activityWindow.getAttributes().softInputMode : 0;
    if (hasInput && activityWindow != null) {
      activityWindow.setSoftInputMode((previousSoftInput & ~android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST) | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
    }
    final android.view.ViewTreeObserver.OnGlobalLayoutListener ime = () -> {
      if (wrap.getHeight() == 0) return;
      int bottom = 0;
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        android.view.WindowInsets insets = wrap.getRootWindowInsets();
        if (insets != null && insets.isVisible(android.view.WindowInsets.Type.ime())) {
          wrap.getLocationOnScreen(location);
          int imeTop = wrap.getRootView().getHeight() - insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
          bottom = Math.max(0, location[1] + wrap.getHeight() - imeTop);
        }
      } else if (wrap.findFocus() instanceof android.widget.EditText) {
        wrap.getWindowVisibleDisplayFrame(frame);
        wrap.getLocationOnScreen(location);
        bottom = Math.max(0, location[1] + wrap.getHeight() - frame.bottom);
      }
      // (user video 2026-10-16 09:01: the sheet jumped up and slid back while the keyboard came) — no jump: it rides up
      if (wrap.getPaddingBottom() != bottom && (imeTarget[0] != bottom)) {
        imeTarget[0] = bottom;
        if (imeAnimator[0] != null) imeAnimator[0].cancel();
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofInt(wrap.getPaddingBottom(), bottom);
        a.setDuration(200);
        a.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        a.addUpdateListener(v -> wrap.setPadding(0, 0, 0, (int) v.getAnimatedValue()));
        imeAnimator[0] = a;
        a.start();
      }
    };
    wrap.getViewTreeObserver().addOnGlobalLayoutListener(ime);
    wrap.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow (View v) { }

      @Override
      public void onViewDetachedFromWindow (View v) {
        v.getViewTreeObserver().removeGlobalOnLayoutListener(ime);
        v.removeOnAttachStateChangeListener(this);
      }
    });

    popup.setDismissListener(p -> {
      if (hasInput) {
        View field = findInput(body);
        if (field != null) org.thunderdog.challegram.tool.Keyboard.hide(field);
        // (user 2026-10-22 12:41 «закрываю действие — клавиатура остаётся»): swiped away, the field is already off its
        // window and has no token — hidden by the activity's own window then
        if (c.context().getWindow() != null) org.thunderdog.challegram.tool.Keyboard.hide(c.context().getWindow().getDecorView());
        if (activityWindow != null) activityWindow.setSoftInputMode(previousSoftInput);
        // (video 09:07) the focus went on to the screen's own field and its keyboard stayed — the screen keeps none
        View root = c.getValue();
        View focused = root != null ? root.findFocus() : null;
        if (focused instanceof android.widget.EditText) {
          focused.clearFocus();
          org.thunderdog.challegram.tool.Keyboard.hide(focused);
        }
      }
      if (onDismiss != null) onDismiss.run();
    });
    sheet.measure(View.MeasureSpec.makeMeasureSpec(Screen.currentWidth() - Screen.dp(16f), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Screen.currentHeight(), View.MeasureSpec.AT_MOST));
    popup.showSimplePopupView(wrap, sheet.getMeasuredHeight() + Screen.dp(8f) + Screen.getNavigationBarHeight());
    return new Handle(popup, body);
  }

  private static @Nullable android.widget.EditText findInput (View v) {
    if (v instanceof android.widget.EditText) return (android.widget.EditText) v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        android.widget.EditText e = findInput(g.getChildAt(i));
        if (e != null) return e;
      }
    }
    return null;
  }

  /** The column that follows the finger */
  static final class DragSheet extends LinearLayout {
    PopupLayout popup;
    private final int touchSlop;
    private float downX, downY;
    private boolean dragging;
    private VelocityTracker velocity;

    DragSheet (Context context) {
      super(context);
      touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private boolean childScrolledDown (View v, float x, float y) {
      if (!(v instanceof ViewGroup)) return false;
      ViewGroup g = (ViewGroup) v;
      for (int i = g.getChildCount() - 1; i >= 0; i--) {
        View child = g.getChildAt(i);
        if (x >= child.getLeft() && x < child.getRight() && y >= child.getTop() && y < child.getBottom()) {
          if (child.canScrollVertically(-1)) return true;
          return childScrolledDown(child, x - child.getLeft(), y - child.getTop());
        }
      }
      return false;
    }

    @Override
    public boolean onInterceptTouchEvent (MotionEvent e) {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downX = e.getRawX();
          downY = e.getRawY();
          dragging = false;
          animate().cancel();
          break;
        case MotionEvent.ACTION_MOVE: {
          float dy = e.getRawY() - downY, dx = e.getRawX() - downX;
          if (dy > touchSlop && dy > Math.abs(dx) && !childScrolledDown(this, e.getX(), e.getY())) {
            dragging = true;
            downY = e.getRawY() - getTranslationY();
            return true;
          }
          break;
        }
      }
      return false;
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (velocity == null) velocity = VelocityTracker.obtain();
      velocity.addMovement(e);
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downY = e.getRawY() - getTranslationY();
          return true;
        case MotionEvent.ACTION_MOVE: {
          float dy = e.getRawY() - downY;
          setTranslationY(dy > 0 ? dy : dy * .15f); // upward only a little give
          return true;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          velocity.computeCurrentVelocity(1000);
          float vy = velocity.getYVelocity();
          velocity.recycle();
          velocity = null;
          dragging = false;
          if (getTranslationY() > getHeight() / 3f || vy > Screen.dp(900f)) {
            if (popup != null) popup.hideWindow(true);
          } else {
            animate().translationY(0f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
          }
          return true;
        }
      }
      return super.onTouchEvent(e);
    }
  }

  // Small builders for sheet rows

  public static android.widget.TextView title (Context context, CharSequence text) {
    android.widget.TextView v = new android.widget.TextView(context);
    v.setText(text);
    v.setTextColor(Theme.textAccentColor());
    v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 17f);
    v.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoMedium());
    v.setPadding(Screen.dp(20f), Screen.dp(4f), Screen.dp(20f), Screen.dp(8f));
    return v;
  }

  /** A full-width row: optional colour square or icon, text, optional value on the right */
  public static View row (Context context, int iconRes, int swatch, CharSequence text, @Nullable CharSequence value, boolean selected, View.OnClickListener click) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(Screen.dp(20f), 0, Screen.dp(20f), 0);
    row.setMinimumHeight(Screen.dp(52f));
    org.thunderdog.challegram.tool.Views.setClickable(row);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(row);
    row.setOnClickListener(click);
    if (swatch != 0) {
      View sq = new View(context);
      GradientDrawable d = new GradientDrawable();
      d.setColor(swatch);
      d.setCornerRadius(Screen.dp(9f));
      sq.setBackground(d);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Screen.dp(32f), Screen.dp(32f));
      p.rightMargin = Screen.dp(14f);
      row.addView(sq, p);
    } else if (iconRes != 0) {
      android.widget.ImageView icon = new android.widget.ImageView(context);
      icon.setImageResource(iconRes);
      icon.setColorFilter(Theme.getColor(org.thunderdog.challegram.theme.ColorId.icon));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f));
      p.rightMargin = Screen.dp(18f);
      row.addView(icon, p);
    }
    android.widget.TextView label = new org.thunderdog.challegram.widget.EmojiTextView(context); // reactions in the app's emoji
    label.setText(text);
    label.setTextColor(Theme.textAccentColor());
    label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16f);
    label.setSingleLine(true);
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    if (value != null) {
      android.widget.TextView v = new android.widget.TextView(context);
      v.setText(value);
      v.setTextColor(Theme.textDecentColor());
      v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14f);
      row.addView(v);
    }
    if (selected) {
      android.widget.ImageView check = new android.widget.ImageView(context);
      check.setImageResource(org.thunderdog.challegram.R.drawable.baseline_check_24);
      check.setColorFilter(Theme.getColor(org.thunderdog.challegram.theme.ColorId.textLink));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Screen.dp(22f), Screen.dp(22f));
      p.leftMargin = Screen.dp(10f);
      row.addView(check, p);
    }
    return row;
  }
}
