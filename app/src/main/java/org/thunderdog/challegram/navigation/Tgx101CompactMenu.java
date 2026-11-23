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
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

/**
 * TGx101 (user 2026-11-20, two screenshots «сделать короче, как наше всплывающее меню в чатах, и снизу под пальцем»):
 * the options windows opened by a press (a hold on a profile row, a tap on a settings row…) are a small card at the finger,
 * as wide as its items, under the finger (above it when there is no room), growing out of the touch point.
 * <p>
 * Left as they were: questions with «Cancel» and choices with «✓» (the Material 3 windows, {@link Tgx101M3Sheets}),
 * windows with a long explanation, a coloured subtitle row, and windows opened with no finger on the screen.
 */
public final class Tgx101CompactMenu {
  public static final boolean ENABLED = true;

  /** a longer text above the items is an explanation: it keeps the wide card from the bottom */
  private static final int MAX_INFO_LENGTH = 140;
  /** the menu opens at the finger only if the finger touched the screen this recently */
  private static final long TOUCH_FRESH_MS = 900;

  private Tgx101CompactMenu () { }

  // The last touch of any window of the app, see BaseActivity.tgx101TrackFinger

  private static float downRawX = -1, downRawY = -1;
  private static long downAt, upAt;
  private static boolean fingerDown;

  public static void onTouch (android.view.MotionEvent e) {
    switch (e.getActionMasked()) {
      case android.view.MotionEvent.ACTION_DOWN:
        downRawX = e.getRawX();
        downRawY = e.getRawY();
        downAt = SystemClock.uptimeMillis();
        fingerDown = true;
        break;
      case android.view.MotionEvent.ACTION_UP:
      case android.view.MotionEvent.ACTION_CANCEL:
        fingerDown = false;
        upAt = SystemClock.uptimeMillis();
        break;
    }
  }

  private static boolean touchFresh () {
    if (downRawX < 0 || downAt == 0) return false;
    long now = SystemClock.uptimeMillis();
    return fingerDown ? now - downAt < 5000 : now - upAt < TOUCH_FRESH_MS;
  }

  /** The compact card for these options, or null when they keep the wide card */
  public static @Nullable PopupLayout show (ViewController<?> controller, ViewController.Options options, @Nullable OptionDelegate delegate, @Nullable ThemeDelegate forcedTheme) {
    if (!ENABLED || options.tgx101Wide || options.subtitle != null || options.items == null || options.items.length == 0) return null;
    if (options.info != null && options.info.length() > MAX_INFO_LENGTH) return null;
    int realItems = 0;
    for (ViewController.OptionItem item : options.items) {
      if (item == null) return null;
      if (item != ViewController.OptionItem.SEPARATOR) {
        if (item.name == null) return null;
        realItems++;
      }
    }
    if (realItems == 0) return null;
    if (!touchFresh()) {
      Tgx101Diag.mark("compact menu: no fresh touch — the wide card (" + realItems + " items)");
      return null;
    }
    return build(controller, options, delegate, forcedTheme, downRawX, downRawY);
  }

  private static int color (@Nullable ThemeDelegate forcedTheme, int colorId) {
    return forcedTheme != null ? forcedTheme.getColor(colorId) : Theme.getColor(colorId);
  }

  private static boolean isDark (@Nullable ThemeDelegate forcedTheme) {
    return forcedTheme != null ? forcedTheme.isDark() : Theme.isDark();
  }

  private static PopupLayout build (ViewController<?> controller, ViewController.Options options, @Nullable OptionDelegate delegate, @Nullable ThemeDelegate forcedTheme, float touchRawX, float touchRawY) {
    Context context = controller.context();
    final PopupLayout popup = new PopupLayout(context);
    popup.setTag(controller);
    popup.init(true);
    popup.setDismissOtherPopUps(!options.ignoreOtherPopUps);
    popup.setNeedFullScreen(true);
    popup.tgx101AllowWhileBusy = true; // as the wide card: it may open while the screen is still settling
    if (delegate != null) popup.setDisableCancelOnTouchDown(delegate.disableCancelOnTouchdown());

    View.OnClickListener onClick = v -> {
      if (delegate != null) {
        if (delegate.onOptionItemPressed(v, v.getId())) popup.hideWindow(true);
      } else {
        ViewController<?> c = controller.context().navigation().getCurrentStackItem();
        if (c instanceof OptionDelegate && ((OptionDelegate) c).onOptionItemPressed(v, v.getId())) popup.hideWindow(true);
      }
    };

    // the card: a tone lighter than the screen on a dark theme, round corners, a thin edge (Vivo draws shadows square)
    final LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setClickable(true);
    card.setPadding(0, Screen.dp(6f), 0, Screen.dp(6f));
    int filling = color(forcedTheme, ColorId.filling);
    GradientDrawable background = new GradientDrawable();
    background.setColor(isDark(forcedTheme) ? ColorUtils.fromToArgb(filling, 0xffffffff, .08f) : filling);
    float radius = Screen.dp(16f);
    background.setCornerRadius(radius);
    background.setStroke(Math.max(1, Screen.dp(.5f)), color(forcedTheme, ColorId.separator));
    card.setBackground(background);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      card.setElevation(Screen.dp(6f));
      card.setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        public void getOutline (View v, android.graphics.Outline outline) {
          outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
        }
      });
      card.setClipToOutline(true);
    }
    card.setMinimumWidth(Screen.dp(184f));

    // a header, small and grey: the window's title, or a short text that went above the items
    CharSequence headerText = !TextUtils.isEmpty(options.title) ? options.title : !TextUtils.isEmpty(options.info) ? options.info : null;
    if (headerText != null) {
      TextView header = new TextView(context);
      header.setText(headerText);
      header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
      header.setTextColor(color(forcedTheme, ColorId.textLight));
      header.setMaxLines(3);
      header.setEllipsize(TextUtils.TruncateAt.END);
      header.setGravity(Lang.gravity());
      header.setPadding(Screen.dp(16f), Screen.dp(6f), Screen.dp(16f), Screen.dp(6f));
      header.setMaxWidth(Screen.dp(260f));
      card.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      card.addView(divider(context, forcedTheme));
    }

    LinearLayout list = new LinearLayout(context);
    list.setOrientation(LinearLayout.VERTICAL);
    int index = 0;
    for (ViewController.OptionItem item : options.items) {
      if (item == ViewController.OptionItem.SEPARATOR) {
        list.addView(divider(context, forcedTheme));
        index++;
        continue;
      }
      TextView row = row(context, item, forcedTheme);
      if (delegate != null) row.setTag(delegate.getTagForItem(index));
      row.setOnClickListener(onClick);
      if (delegate != null) row.setOnLongClickListener(lv -> {
        if (!delegate.onOptionItemLongPressed(lv, lv.getId())) return false;
        popup.hideWindow(true);
        return true;
      });
      list.addView(row);
      index++;
    }
    ScrollView scroll = new ScrollView(context);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
    scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    final int edge = Screen.dp(12f), gap = Screen.dp(8f);
    final FrameLayoutFix root = new FrameLayoutFix(context) {
      @Override
      protected void onLayout (boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        place(this, card, touchRawX, touchRawY, edge, gap);
      }
    };
    root.setOnClickListener(v -> popup.hideWindow(true)); // a tap beside the card
    root.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    FrameLayoutFix.LayoutParams cardParams = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
    android.graphics.Rect insets = controller.context().getRootView().getSystemInsetsWithoutIme();
    cardParams.topMargin = Math.max(Screen.getStatusBarHeight(), insets != null ? insets.top : 0) + edge;
    cardParams.bottomMargin = (insets != null ? insets.bottom : 0) + edge;
    cardParams.leftMargin = (insets != null ? insets.left : 0) + edge;
    cardParams.rightMargin = (insets != null ? insets.right : 0) + edge;
    root.addView(card, cardParams);

    popup.showAnimatedPopupView(root, new PopupLayout.AnimatedPopupProvider() {
      private android.animation.ValueAnimator animator;
      private float factor;

      private void apply (float f) {
        factor = f;
        card.setAlpha(Math.min(1f, f * 1.8f));
        float scale = .6f + .4f * f;
        card.setScaleX(scale);
        card.setScaleY(scale);
        popup.setRevealFactor(f);
      }

      private void run (float to, long duration, android.animation.TimeInterpolator interpolator, Runnable end) {
        if (animator != null) animator.cancel();
        animator = android.animation.ValueAnimator.ofFloat(factor, to);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(a -> apply((float) a.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd (android.animation.Animator animation) {
            end.run();
          }
        });
        animator.start();
      }

      @Override
      public void prepareShowAnimation () {
        apply(0f);
      }

      @Override
      public void launchShowAnimation (PopupLayout p) {
        run(1f, 240, org.thunderdog.challegram.widget.PopupLayout.TGX101_IN_CURVE, p::onCustomShowComplete);
      }

      @Override
      public boolean launchHideAnimation (PopupLayout p, me.vkryl.android.animator.FactorAnimator originalAnimator) {
        run(0f, 200, org.thunderdog.challegram.widget.PopupLayout.TGX101_OUT_CURVE, p::onCustomHideAnimationComplete);
        return true;
      }
    });
    Tgx101Diag.mark("compact menu: " + options.items.length + " items at " + (int) touchRawX + "," + (int) touchRawY + (headerText != null ? " with a header" : ""));
    return popup;
  }

  /** Under the finger (above it when there is no room); on the finger's side of the screen; growing out of the touch point */
  private static void place (ViewGroup root, View card, float touchRawX, float touchRawY, int edge, int gap) {
    int w = card.getWidth(), h = card.getHeight();
    if (w == 0 || h == 0) return;
    FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) card.getLayoutParams();
    int[] loc = new int[2];
    root.getLocationOnScreen(loc);
    float x = touchRawX - loc[0], y = touchRawY - loc[1];
    int minLeft = params.leftMargin, maxLeft = root.getWidth() - params.rightMargin - w;
    int minTop = params.topMargin, maxTop = root.getHeight() - params.bottomMargin - h;
    // the card's near edge a little beside the finger: from the left half it grows right, from the right half left
    boolean leftHalf = x < root.getWidth() / 2f;
    int left = leftHalf ? (int) x - Screen.dp(24f) : (int) x + Screen.dp(24f) - w;
    left = Math.max(minLeft, Math.min(Math.max(minLeft, maxLeft), left));
    int top;
    boolean below = y + gap + h <= root.getHeight() - params.bottomMargin;
    if (below) {
      top = (int) y + gap;
    } else if (y - gap - h >= minTop) {
      top = (int) y - gap - h;
    } else {
      top = Math.max(minTop, Math.min(Math.max(minTop, maxTop), (int) y - h / 2));
    }
    card.layout(left, top, left + w, top + h);
    card.setPivotX(Math.max(0, Math.min(w, x - left)));
    card.setPivotY(Math.max(0, Math.min(h, y - top)));
  }

  private static View divider (Context context, @Nullable ThemeDelegate forcedTheme) {
    View divider = new View(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        // takes the card's width, never sets it (a plain view would stretch the card to the whole screen)
        setMeasuredDimension(MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthMeasureSpec) : 0, MeasureSpec.getSize(heightMeasureSpec));
      }
    };
    divider.setBackgroundColor(color(forcedTheme, ColorId.separator));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Screen.dp(.5f)));
    params.topMargin = params.bottomMargin = Screen.dp(4f);
    divider.setLayoutParams(params);
    return divider;
  }

  private static TextView row (Context context, ViewController.OptionItem item, @Nullable ThemeDelegate forcedTheme) {
    TextView row = new TextView(context);
    row.setId(item.id);
    row.setText(item.name);
    row.setTypeface(Fonts.getRobotoRegular());
    row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    row.setSingleLine(true);
    row.setEllipsize(TextUtils.TruncateAt.END);
    row.setMaxWidth(Screen.currentWidth() - Screen.dp(48f));
    row.setGravity(Gravity.CENTER_VERTICAL | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    row.setPadding(Screen.dp(16f), 0, Screen.dp(24f), 0);
    row.setCompoundDrawablePadding(Screen.dp(16f));
    int textColorId = OptionsLayout.getOptionColorId(item.textColor);
    row.setTextColor(color(forcedTheme, textColorId));
    Settings.tgx101StyleRow(row, 16f);
    if (item.icon != 0) {
      Drawable icon = Drawables.get(context.getResources(), org.thunderdog.challegram.ui.Tgx101MessageMenu.outlined(item.icon));
      if (icon != null) {
        icon = icon.mutate();
        int iconColorId;
        if (item.iconColor == ViewController.OptionColor.NORMAL) {
          iconColorId = item.textColor == ViewController.OptionColor.NORMAL ? ColorId.icon : textColorId; // a red item has a red icon
        } else if (item.iconColor == ViewController.OptionColor.INACTIVE) {
          iconColorId = ColorId.controlInactive;
        } else {
          iconColorId = OptionsLayout.getOptionColorId(item.iconColor);
        }
        icon.setColorFilter(Paints.getColorFilter(color(forcedTheme, iconColorId)));
        int size = Screen.dp(22f);
        icon.setBounds(0, 0, size, size);
        if (Lang.rtl()) {
          row.setCompoundDrawables(null, null, icon, null);
        } else {
          row.setCompoundDrawables(icon, null, null, null);
        }
      }
    }
    row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f)));
    Views.setClickable(row);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(row);
    return row;
  }
}
