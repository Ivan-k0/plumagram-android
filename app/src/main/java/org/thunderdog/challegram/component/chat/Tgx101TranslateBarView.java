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

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import me.vkryl.android.widget.FrameLayoutFix;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;

/**
 * TGx101 (user 2026-10-11, approved mockup «Перевод-чата»): the bar under the chat header while the whole chat is
 * translated — «Show original» — and after that «Translate to …», with ⋮ for another language or hiding it, like the
 * official app. It never shows up on its own: the chat is translated from ⋮ → «Translate chat».
 */
public class Tgx101TranslateBarView extends FrameLayoutFix {
  private final TextView textView;
  private final ImageView moreButton;

  public Tgx101TranslateBarView (Context context, ViewController<?> themeProvider) {
    super(context);
    ViewSupport.setThemedBackground(this, ColorId.filling, themeProvider);

    textView = new TextView(context);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    textView.setTypeface(Fonts.getRobotoMedium());
    textView.setTextColor(Theme.getColor(ColorId.textNeutral));
    textView.setGravity(Gravity.CENTER);
    textView.setSingleLine(true);
    textView.setCompoundDrawablePadding(Screen.dp(8f));
    textView.setPadding(Screen.dp(48f), 0, Screen.dp(48f), 0);
    textView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    RippleSupport.setTransparentSelector(textView);
    Views.setClickable(textView);
    addView(textView);
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(textView, ColorId.textNeutral);
    }

    moreButton = new ImageView(context);
    moreButton.setScaleType(ImageView.ScaleType.CENTER);
    moreButton.setImageResource(R.drawable.baseline_more_vert_24);
    moreButton.setColorFilter(Theme.iconColor());
    moreButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(48f), ViewGroup.LayoutParams.MATCH_PARENT, (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP));
    moreButton.setBackgroundResource(R.drawable.bg_btn_header);
    Views.setClickable(moreButton);
    addView(moreButton);
    if (themeProvider != null) {
      themeProvider.addThemeFilterListener(moreButton, ColorId.icon);
      themeProvider.addThemeInvalidateListener(this);
    }
  }

  public void setText (CharSequence text) {
    textView.setText(text);
    android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(getContext(), R.drawable.baseline_translate_24);
    if (icon != null) {
      icon = icon.mutate();
      int size = Screen.dp(18f);
      icon.setBounds(0, 0, size, size);
      icon.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(ColorId.textNeutral), android.graphics.PorterDuff.Mode.SRC_IN));
      textView.setCompoundDrawables(icon, null, null, null);
    }
  }

  public void setOnBarClickListener (View.OnClickListener listener) {
    textView.setOnClickListener(listener);
  }

  public void setOnMoreClickListener (View.OnClickListener listener) {
    moreButton.setOnClickListener(listener);
  }
}
