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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 (approved mockup «Окна Material 3» В, user 2026-11-01): a setting with two or three choices shows them right in
 * its row as joined pills — a tap picks one, no window opens. The picked one is filled and carries a ✓.
 */
public class Tgx101Pills extends LinearLayout {
  public interface OnPick {
    void onPick (int index);
  }

  private final TextView titleView;
  private final LinearLayout pills;
  private @androidx.annotation.Nullable View.OnLongClickListener hold;

  /** TGx101 (user 2026-11-08 «пояснения — по долгому удержанию, как во всей системе»): holding the title or a pill explains */
  public void setHold (@androidx.annotation.Nullable View.OnLongClickListener hold) {
    this.hold = hold;
    titleView.setOnLongClickListener(hold);
    for (int i = 0; i < pills.getChildCount(); i++) pills.getChildAt(i).setOnLongClickListener(hold);
  }

  public Tgx101Pills (Context context) {
    super(context);
    setOrientation(VERTICAL);
    setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), Screen.dp(14f));
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    titleView = new TextView(context);
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    titleView.setTypeface(Fonts.getRobotoRegular());
    titleView.setGravity(Lang.gravity());
    titleView.setSingleLine(true);
    titleView.setEllipsize(TextUtils.TruncateAt.END);
    addView(titleView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    pills = new LinearLayout(context);
    pills.setOrientation(HORIZONTAL);
    LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(40f));
    params.topMargin = Screen.dp(10f);
    addView(pills, params);
  }

  public void bind (CharSequence title, String[] choices, int selected, OnPick onPick) {
    setBackgroundColor(Theme.fillingColor());
    titleView.setText(title);
    titleView.setTextColor(Theme.getColor(ColorId.text));
    int accent = Theme.getColor(ColorId.textNeutral);
    int text = Theme.getColor(ColorId.text);
    int outline = ColorUtils.alphaColor(.45f, Theme.getColor(ColorId.iconLight));
    int fill = ColorUtils.alphaColor(.18f, accent);
    float r = Screen.dp(20f);
    pills.removeAllViews();
    for (int i = 0; i < choices.length; i++) {
      final int index = i;
      boolean on = i == selected;
      TextView pill = new TextView(getContext());
      pill.setText(on ? "✓  " + choices[i] : choices[i]);
      pill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
      pill.setTypeface(Fonts.getRobotoMedium());
      pill.setTextColor(on ? accent : text);
      pill.setGravity(Gravity.CENTER);
      pill.setSingleLine(true);
      pill.setEllipsize(TextUtils.TruncateAt.END);
      pill.setPadding(Screen.dp(8f), 0, Screen.dp(8f), 0);
      GradientDrawable background = new GradientDrawable();
      background.setColor(on ? fill : 0);
      background.setStroke(Math.max(1, Screen.dp(1f)), outline);
      boolean first = i == 0, last = i == choices.length - 1;
      boolean startRound = Lang.rtl() ? last : first, endRound = Lang.rtl() ? first : last;
      background.setCornerRadii(new float[] {
        startRound ? r : 0, startRound ? r : 0,
        endRound ? r : 0, endRound ? r : 0,
        endRound ? r : 0, endRound ? r : 0,
        startRound ? r : 0, startRound ? r : 0
      });
      pill.setBackground(background);
      pill.setOnClickListener(v -> {
        if (index != selected) onPick.onPick(index);
      });
      if (hold != null) pill.setOnLongClickListener(hold);
      LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
      if (i > 0) params.leftMargin = -Math.max(1, Screen.dp(1f)); // the joined pills share one border line
      pills.addView(pill, params);
    }
  }
}
