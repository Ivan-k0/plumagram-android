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
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 (user 2026-11-22, mockup «Музыка профиля»: «карточками»): the profile's music and the personal channel are
 * rounded cards, not plain rows — on the left a round accent ▶ (or the channel's avatar), the name in bold with the
 * grey line under it, on the right «ещё N ›» in the accent colour.
 */
public class Tgx101ProfileCard extends FrameLayout {
  /** the row's height: the card and the screen's filling around it */
  public static int rowHeight () {
    return Screen.dp(76f);
  }

  private final LinearLayout card;
  private final FrameLayout lead;
  private final ImageView playIcon;
  private final AvatarView avatar;
  private final TextView titleView, subtitleView, moreView;

  public Tgx101ProfileCard (Context context) {
    super(context);
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight()));

    card = new LinearLayout(context);
    card.setOrientation(LinearLayout.HORIZONTAL);
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setPadding(Screen.dp(10f), 0, Screen.dp(12f), 0);
    FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    cardParams.leftMargin = cardParams.rightMargin = Screen.dp(12f);
    cardParams.topMargin = cardParams.bottomMargin = Screen.dp(8f);
    addView(card, cardParams);

    lead = new FrameLayout(context);
    int leadSize = Screen.dp(40f);
    card.addView(lead, new LinearLayout.LayoutParams(leadSize, leadSize));

    playIcon = new ImageView(context);
    playIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    playIcon.setImageResource(R.drawable.baseline_play_arrow_36_white);
    int pad = Screen.dp(8f);
    playIcon.setPadding(pad, pad, pad, pad);
    lead.addView(playIcon, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    avatar = new AvatarView(context);
    avatar.setVisibility(View.GONE);
    lead.addView(avatar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout texts = new LinearLayout(context);
    texts.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    textsParams.leftMargin = Screen.dp(12f);
    textsParams.rightMargin = Screen.dp(8f);
    card.addView(texts, textsParams);

    titleView = new TextView(context);
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    titleView.setTypeface(Fonts.getRobotoMedium());
    titleView.setSingleLine(true);
    titleView.setEllipsize(TextUtils.TruncateAt.END);
    titleView.setGravity(Lang.gravity());
    texts.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    subtitleView = new TextView(context);
    subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    subtitleView.setTypeface(Fonts.getRobotoRegular());
    subtitleView.setSingleLine(true);
    subtitleView.setEllipsize(TextUtils.TruncateAt.END);
    subtitleView.setGravity(Lang.gravity());
    LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    subParams.topMargin = Screen.dp(1f);
    texts.addView(subtitleView, subParams);

    moreView = new TextView(context);
    moreView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    moreView.setTypeface(Fonts.getRobotoMedium());
    moreView.setSingleLine(true);
    card.addView(moreView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  /** The card's click goes to the row's own listener (by the row's id), so the profile handles it as before */
  @Override
  public void setOnClickListener (@Nullable OnClickListener l) {
    super.setOnClickListener(l);
    card.setOnClickListener(l == null ? null : v -> l.onClick(this));
  }

  @Override
  public void setOnLongClickListener (@Nullable OnLongClickListener l) {
    super.setOnLongClickListener(l);
    card.setOnLongClickListener(l == null ? null : v -> l.onLongClick(this));
  }

  /** A song card: ▶ on the left; {@code more} is «ещё N ›», or nothing */
  public void bindMusic (CharSequence title, CharSequence subtitle, @Nullable CharSequence more) {
    avatar.setVisibility(View.GONE);
    playIcon.setVisibility(View.VISIBLE);
    playIcon.setImageResource(R.drawable.baseline_play_arrow_36_white);
    bindTexts(title, subtitle, more);
  }

  /** The channel's card: its avatar on the left (a bullhorn on the accent circle when there is no channel yet) */
  public void bindChannel (Tdlib tdlib, @Nullable TdApi.Chat chat, CharSequence title, CharSequence subtitle, @Nullable CharSequence more) {
    if (chat != null) {
      playIcon.setVisibility(View.GONE);
      avatar.setVisibility(View.VISIBLE);
      avatar.setChat(tdlib, chat);
    } else {
      avatar.setVisibility(View.GONE);
      avatar.setChat(tdlib, null);
      playIcon.setVisibility(View.VISIBLE);
      playIcon.setImageResource(R.drawable.baseline_bullhorn_24);
    }
    bindTexts(title, subtitle, more);
  }

  private void bindTexts (CharSequence title, CharSequence subtitle, @Nullable CharSequence more) {
    titleView.setText(title);
    subtitleView.setText(subtitle);
    subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
    moreView.setText(more);
    moreView.setVisibility(TextUtils.isEmpty(more) ? View.GONE : View.VISIBLE);
    applyColors();
  }

  /** Colours are read on every bind, so a theme change re-binds them with the list */
  private void applyColors () {
    setBackgroundColor(Theme.fillingColor());
    int accent = Theme.getColor(ColorId.textNeutral);
    int fill = ColorUtils.compositeColor(Theme.fillingColor(), ColorUtils.alphaColor(Theme.isDark() ? .16f : .10f, accent));
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(fill);
    shape.setCornerRadius(Screen.dp(14f));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      card.setBackground(new RippleDrawable(ColorStateList.valueOf(ColorUtils.alphaColor(.18f, accent)), shape, null));
    } else {
      card.setBackgroundDrawable(shape);
    }
    GradientDrawable circle = new GradientDrawable();
    circle.setShape(GradientDrawable.OVAL);
    circle.setColor(accent);
    playIcon.setBackgroundDrawable(circle);
    playIcon.setColorFilter(0xffffffff);
    titleView.setTextColor(Theme.getColor(ColorId.text));
    subtitleView.setTextColor(Theme.getColor(ColorId.textLight));
    moreView.setTextColor(accent);
  }
}
