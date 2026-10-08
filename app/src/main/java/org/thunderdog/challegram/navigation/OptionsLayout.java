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
 * File created on 26/03/2016 at 23:51
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.EmojiTextView;
import org.thunderdog.challegram.widget.RootFrameLayout;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.Animated;
import me.vkryl.core.StringUtils;

public class OptionsLayout extends LinearLayout implements Animated, RootFrameLayout.MarginModifier {
  private final CustomTextView textView;
  private final CustomTextView headerView;
  private final ViewController<?> parent;
  private final @Nullable ThemeDelegate forcedTheme;

  public OptionsLayout (Context context, ViewController<?> parent, @Nullable ThemeDelegate forcedTheme) {
    super(context);
    this.parent = parent;
    this.forcedTheme = forcedTheme;

    setOrientation(LinearLayout.VERTICAL);

    headerView = new CustomTextView(context, parent.tdlib());
    headerView.setTextSize(18f);
    headerView.setPadding(Screen.dp(16f), Screen.dp(18f), Screen.dp(16f), Screen.dp(6f));
    headerView.setTextColorId(ColorId.text);
    headerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    headerView.setMaxLineCount(1);
    headerView.setVisibility(View.GONE);
    addView(headerView);

    textView = new CustomTextView(context, parent.tdlib());
    textView.setPadding(Screen.dp(16f), Screen.dp(14f), Screen.dp(16f), Screen.dp(6f));
    textView.setTextColorId(ColorId.textLight);
    textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    addView(textView);

    ViewUtils.setBackground(this, new Drawable() {
      private final android.graphics.RectF rect = new android.graphics.RectF();
      @Override
      public void draw (@NonNull Canvas c) {
        View view = getChildAt(0);
        int height = view != null ? view.getMeasuredHeight() : 0;
        int color = forcedTheme != null ? forcedTheme.getColor(ColorId.filling) : Theme.getColor(ColorId.filling);
        if (TGX101_CARD) {
          // TGx101 (user 2026-10-08, mockup «Окна» variant А): a floating card like the message field — side gaps, round corners
          rect.set(0, height, getMeasuredWidth(), getMeasuredHeight() - getPaddingBottom());
          float r = Screen.dp(TGX101_CARD_RADIUS);
          c.drawRoundRect(rect, r, r, Paints.fillingPaint(color));
          float half = Math.max(1, Screen.dp(.5f)) / 2f;
          rect.inset(half, half);
          c.drawRoundRect(rect, r, r, Paints.strokeSmallPaint(me.vkryl.core.ColorUtils.alphaColor(.08f, 0xff000000)));
          return;
        }
        if (height > 0)
          c.drawRect(0, height, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(color));
      }

      @Override
      public void setAlpha (int alpha) { }

      @Override
      public void setColorFilter (@Nullable ColorFilter colorFilter) { }

      @Override
      @SuppressWarnings("deprecation")
      public int getOpacity () {
        return PixelFormat.UNKNOWN;
      }
    });

    if (forcedTheme != null) {
      textView.setForcedTheme(forcedTheme);
      headerView.setForcedTheme(forcedTheme);
    } else {
      // parent.addThemeTextColorListener(textView, ColorId.textLight);
      parent.addThemeInvalidateListener(textView);
      parent.addThemeInvalidateListener(headerView);
      parent.addThemeInvalidateListener(this);
    }
  }

  /** TGx101: sheets are floating cards (variant А of the «Окна» mockup) */
  public static final boolean TGX101_CARD = true;
  public static final float TGX101_CARD_RADIUS = 22f, TGX101_CARD_GAP = 8f;

  @Override
  public void onApplyMarginInsets (View child, FrameLayout.LayoutParams params, Rect legacyInsets, Rect insets, Rect insetsWithoutIme) {
    int gap = TGX101_CARD ? Screen.dp(TGX101_CARD_GAP) : 0;
    Views.setMargins(params, insets.left + gap, 0, insets.right + gap, 0);
    Views.setPaddingBottom(this, insetsWithoutIme.bottom + gap);
  }

  @Override
  public void onViewAdded (View child) {
    super.onViewAdded(child);
    // the top shadow of the old full-width sheet: a floating card has its own edge
    if (TGX101_CARD && child instanceof org.thunderdog.challegram.widget.ShadowView && indexOfChild(child) == 0) child.setAlpha(0f);
  }

  private final android.view.ViewOutlineProvider tgx101Outline = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP ? new android.view.ViewOutlineProvider() {
    @Override
    public void getOutline (View view, android.graphics.Outline outline) {
      View first = getChildAt(0);
      int top = first != null ? first.getMeasuredHeight() : 0;
      outline.setRoundRect(0, top, view.getWidth(), view.getHeight() - view.getPaddingBottom(), Screen.dp(TGX101_CARD_RADIUS));
    }
  } : null;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    int currentHeight = getMeasuredHeight();
    int actualHeight = 0;
    for (int i = 0; i < getChildCount(); i++) {
      View view = getChildAt(i);
      if (view != null) {
        actualHeight += view.getMeasuredHeight();
      }
    }
    int diff = Math.min(0, currentHeight - actualHeight);
    for (int i = 0; i < getChildCount(); i++) {
      View view = getChildAt(i);
      if (view != null) {
        view.setTranslationY(diff);
      }
    }
  }

  public static @ColorId int getOptionColorId (@ViewController.OptionColor int color) {
    switch (color) {
      case ViewController.OptionColor.NORMAL:
        return ColorId.text;
      case ViewController.OptionColor.LIGHT:
        return ColorId.textLight;
      case ViewController.OptionColor.INACTIVE:
        return ColorId.controlInactive;
      case ViewController.OptionColor.RED:
        return ColorId.textNegative;
      case ViewController.OptionColor.BLUE:
        return ColorId.textNeutral;
      case ViewController.OptionColor.GREEN:
        return ColorId.iconPositive;
    }
    throw new IllegalArgumentException("color == " + color);
  }

  public static TextView genOptionView (Context context, int id, CharSequence string, @ViewController.OptionColor int textColor, int icon, @ViewController.OptionColor int iconColor, OnClickListener onClickListener, @Nullable ThemeListenerList themeProvider, @Nullable ThemeDelegate forcedTheme) {
    EmojiTextView text = new EmojiTextView(context);
    text.setScrollDisabled(true);

    text.setId(id);
    text.setTypeface(Fonts.getRobotoRegular());
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    final int textColorId = getOptionColorId(textColor);
    if (forcedTheme != null) {
      text.setTextColor(forcedTheme.getColor(textColorId));
    } else {
      text.setTextColor(Theme.getColor(textColorId));
      if (themeProvider != null)
        themeProvider.addThemeColorListener(text, textColorId);
    }
    text.setOnClickListener(onClickListener);
    text.setSingleLine(true);
    text.setEllipsize(TextUtils.TruncateAt.END);
    org.thunderdog.challegram.unsorted.Settings.tgx101StyleRow(text, 16f);
    text.setGravity(Lang.rtl() ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
    text.setPadding(Screen.dp(17f), Screen.dp(1f), Screen.dp(17f), 0);
    text.setCompoundDrawablePadding(Screen.dp(18f));
    if (icon != 0) {
      Drawable drawable = Drawables.get(context.getResources(), icon);
      if (drawable != null) {
        final int drawableColorId;
        if (iconColor == ViewController.OptionColor.NORMAL) {
          drawableColorId = ColorId.icon;
        } else if (iconColor == ViewController.OptionColor.INACTIVE) {
          drawableColorId = ColorId.controlInactive;
        } else {
          drawableColorId = getOptionColorId(iconColor);
        }
        drawable.setColorFilter(Paints.getColorFilter(forcedTheme != null ? forcedTheme.getColor(drawableColorId) : Theme.getColor(drawableColorId)));
        if (themeProvider != null) {
          themeProvider.addThemeFilterListener(drawable, drawableColorId);
        }
        if (Drawables.needMirror(icon)) {
          // TODO
        }
        if (Lang.rtl()) {
          text.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
        } else {
          text.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        }
      }
    }
    Views.setClickable(text);
    if (!StringUtils.isEmpty(string)) {
      text.setText(string);
    }

    return text;
  }

  public void setHeader (CharSequence text) {
    if (!StringUtils.isEmpty(text)) {
      headerView.setBoldText(text, null, false);
      headerView.setVisibility(View.VISIBLE);
    } else {
      headerView.setVisibility(View.GONE);
    }
  }

  public static void updateSubtitle (EmojiTextView text, CharSequence name, @DrawableRes int icon, int color, @Nullable ThemeDelegate forcedTheme, @Nullable ViewController<?> themeProvider) {
    updateSubtitle(text, name, icon, color, color, forcedTheme, themeProvider);
  }

  public static void updateSubtitle (EmojiTextView text, CharSequence name, @DrawableRes int icon, int textColor, int iconColor, @Nullable ThemeDelegate forcedTheme, @Nullable ViewController<?> themeProvider) {
    final int textColorId = getOptionColorId(textColor);
    if (forcedTheme != null) {
      text.setTextColor(forcedTheme.getColor(textColorId));
    } else {
      text.setTextColor(Theme.getColor(textColorId));
      if (themeProvider != null) {
        themeProvider.addThemeTextColorListener(text, textColorId);
      }
    }
    final int iconColorId = getOptionColorId(iconColor);
    if (icon != 0) {
      Drawable drawable = Drawables.get(text.getContext().getResources(), icon);
      if (drawable != null) {
        final int drawableColorId = iconColor == ViewController.OptionColor.NORMAL ? ColorId.icon : iconColorId;
        drawable.setColorFilter(Paints.getColorFilter(forcedTheme != null ? forcedTheme.getColor(drawableColorId) : Theme.getColor(drawableColorId)));
        if (themeProvider != null) {
          themeProvider.addThemeFilterListener(drawable, drawableColorId);
        }
        if (Drawables.needMirror(icon)) {
          // TODO
        }
        if (Lang.rtl()) {
          text.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
        } else {
          text.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        }
      }
    }
    if (!StringUtils.isEmpty(name)) {
      text.setText(name);
    }
  }

  public static EmojiTextView genSubtitle (Context context) {
    EmojiTextView text = new EmojiTextView(context);
    text.setScrollDisabled(true);
    text.setMinHeight(Screen.dp(40f));
    text.setTypeface(Fonts.getRobotoRegular());
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    text.setGravity(Lang.rtl() ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
    text.setPadding(Screen.dp(17f), Screen.dp(6f), Screen.dp(17f), 0);
    text.setCompoundDrawablePadding(Screen.dp(8f));
    text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return text;
  }

  public void setSubtitle (ViewController.OptionItem item) {
    setSubtitle(item.name, item.icon, item.textColor, item.iconColor);
  }

  public void setSubtitle (CharSequence name, @DrawableRes int icon, int textColor, int iconColor) {
    EmojiTextView text = genSubtitle(getContext());
    updateSubtitle(text, name, icon, textColor, iconColor, forcedTheme, parent);
    addView(text, 1);
  }

  public void setInfo (ViewController<?> context, Tdlib tdlib, CharSequence info, boolean isTitle, int maxLineCount) {
    if (!StringUtils.isEmpty(info)) {
      String str = info.toString();
      TextEntity[] parsed = TD.collectAllEntities(context, tdlib, info, false, null);
      setInfo(str, parsed, isTitle, maxLineCount);
    } else {
      textView.setVisibility(View.GONE);
    }
  }

  public void setInfo (String text, TextEntity[] entities, boolean isTitle, int maxLineCount) {
    if (!StringUtils.isEmpty(text)) {
      textView.setVisibility(View.VISIBLE);
      textView.setMaxLineCount(maxLineCount);
      if (isTitle) {
        textView.setBoldText(text, entities, false);
        textView.setTextSize(19f);
        textView.setTextColorId(ColorId.text);
      } else {
        textView.setText(text, entities, false);
        textView.setTextSize(15f);
        textView.setTextColorId(ColorId.textLight);
      }
    } else {
      textView.setVisibility(View.GONE);
    }
  }

  public int getTextHeight () {
    if (textView.getVisibility() == View.VISIBLE) {
      return textView.getCurrentHeight(UI.getContext(getContext()).getControllerWidth(textView));
    } else {
      return 0;
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    super.onTouchEvent(event);
    return true;
  }

  private Runnable pendingAction;

  @Override
  public void runOnceViewBecomesReady (View view, Runnable action) {
    pendingAction = action;
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (TGX101_CARD && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      if (getOutlineProvider() != tgx101Outline) {
        setOutlineProvider(tgx101Outline);
        setClipToOutline(true);
      } else {
        invalidateOutline();
      }
    }
    if (pendingAction != null) {
      pendingAction.run();
      pendingAction = null;
    }
  }
}
