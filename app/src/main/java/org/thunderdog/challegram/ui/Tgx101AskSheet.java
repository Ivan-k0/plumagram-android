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
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

/**
 * TGx101 (user 2026-10-31 «окно синхронизации контактов — дизайн не наш, а с андроида 4.1»): a question in the app's
 * own bottom sheet instead of the system dialog — an icon, a title, the text, one big button and a quiet one under it.
 * It swipes down like every other sheet. It is a {@link DialogInterface}, so code written for the system dialog keeps
 * its listeners.
 */
public final class Tgx101AskSheet implements DialogInterface {
  private Tgx101Sheet.Handle handle;
  private boolean showing = true;

  private Tgx101AskSheet () { }

  public boolean isShowing () {
    return showing;
  }

  @Override
  public void cancel () {
    dismiss();
  }

  @Override
  public void dismiss () {
    if (handle != null) handle.dismiss();
  }

  /** {@code onDismiss} runs however the sheet closes: a button, a swipe or a tap outside */
  public static Tgx101AskSheet show (ViewController<?> c, @DrawableRes int icon, CharSequence title, CharSequence message, CharSequence positive, @Nullable CharSequence negative, OnClickListener onPositive, @Nullable OnClickListener onNegative, @Nullable OnDismissListener onDismiss) {
    final Tgx101AskSheet ask = new Tgx101AskSheet();
    ask.handle = Tgx101Sheet.show(c, () -> {
      ask.showing = false;
      if (onDismiss != null) onDismiss.onDismiss(ask);
    }, body -> {
      Context context = c.context();
      body.setPadding(Screen.dp(20f), Screen.dp(6f), Screen.dp(20f), 0);

      ImageView iconView = new ImageView(context);
      iconView.setImageResource(icon);
      iconView.setColorFilter(Theme.getColor(ColorId.fillingPositiveContent));
      iconView.setScaleType(ImageView.ScaleType.CENTER);
      GradientDrawable iconBg = new GradientDrawable();
      iconBg.setShape(GradientDrawable.OVAL);
      iconBg.setColor(Theme.getColor(ColorId.fillingPositive));
      iconView.setBackground(iconBg);
      LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(Screen.dp(56f), Screen.dp(56f));
      iconParams.gravity = Gravity.CENTER_HORIZONTAL;
      iconParams.bottomMargin = Screen.dp(14f);
      body.addView(iconView, iconParams);

      TextView titleView = new TextView(context);
      titleView.setText(title);
      titleView.setTextColor(Theme.textAccentColor());
      titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19f);
      titleView.setTypeface(Fonts.getRobotoMedium());
      titleView.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      titleParams.bottomMargin = Screen.dp(8f);
      body.addView(titleView, titleParams);

      TextView text = new TextView(context);
      text.setText(message);
      text.setTextColor(Theme.textDecentColor());
      text.setLinkTextColor(Theme.textLinkColor());
      text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      text.setLineSpacing(Screen.dp(3f), 1f);
      text.setGravity(Gravity.CENTER);
      text.setMovementMethod(LinkMovementMethod.getInstance());
      LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      textParams.bottomMargin = Screen.dp(20f);
      body.addView(text, textParams);

      TextView button = new TextView(context);
      button.setText(positive);
      button.setTextColor(Theme.getColor(ColorId.fillingPositiveContent));
      button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      button.setTypeface(Fonts.getRobotoMedium());
      button.setGravity(Gravity.CENTER);
      GradientDrawable buttonBg = new GradientDrawable();
      buttonBg.setColor(Theme.getColor(ColorId.fillingPositive));
      buttonBg.setCornerRadius(Screen.dp(14f));
      button.setBackground(buttonBg);
      Views.setClickable(button);
      button.setOnClickListener(v -> onPositive.onClick(ask, BUTTON_POSITIVE));
      body.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(50f)));

      if (negative != null) {
        TextView quiet = new TextView(context);
        quiet.setText(negative);
        quiet.setTextColor(Theme.textDecentColor());
        quiet.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        quiet.setGravity(Gravity.CENTER);
        Views.setClickable(quiet);
        RippleSupport.setTransparentSelector(quiet);
        quiet.setOnClickListener(v -> {
          if (onNegative != null) onNegative.onClick(ask, BUTTON_NEGATIVE);
          else ask.dismiss();
        });
        LinearLayout.LayoutParams quietParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(46f));
        quietParams.topMargin = Screen.dp(4f);
        body.addView(quiet, quietParams);
      }
    });
    return ask;
  }
}
