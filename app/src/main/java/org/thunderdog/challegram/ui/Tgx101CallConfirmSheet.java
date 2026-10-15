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

import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.PopupLayout;

/**
 * TGx101 (user 2026-10-15, variant 1 approved): a call started from the phone's contact card asks in a compact sheet at
 * the bottom — the tap was in another app, so the sheet sits where the thumb is: one big «Call» / «Video call» button,
 * cancelled by a swipe down or a tap outside.
 */
public final class Tgx101CallConfirmSheet {
  private Tgx101CallConfirmSheet () { }

  private static final int CALL_GREEN = 0xff3fa34d;

  public static void show (ViewController<?> c, long userId, boolean video, Runnable onCall) {
    Tdlib tdlib = c.tdlib();
    android.content.Context context = c.context();
    final PopupLayout popup = new PopupLayout(context);
    popup.init(true);
    popup.setNeedRootInsets();

    LinearLayout sheet = new LinearLayout(context);
    sheet.setOrientation(LinearLayout.VERTICAL);
    sheet.setGravity(Gravity.CENTER_HORIZONTAL);
    sheet.setPadding(Screen.dp(14f), Screen.dp(10f), Screen.dp(14f), Screen.dp(14f));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Theme.fillingColor());
    bg.setCornerRadius(Screen.dp(22f));
    sheet.setBackground(bg);

    View handle = new View(context);
    GradientDrawable handleBg = new GradientDrawable();
    handleBg.setColor(Theme.separatorColor());
    handleBg.setCornerRadius(Screen.dp(2f));
    handle.setBackground(handleBg);
    LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(Screen.dp(36f), Screen.dp(4f));
    handleParams.bottomMargin = Screen.dp(12f);
    sheet.addView(handle, handleParams);

    AvatarView avatar = new AvatarView(context);
    avatar.setUser(tdlib, userId, false);
    LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(Screen.dp(52f), Screen.dp(52f));
    avatarParams.bottomMargin = Screen.dp(6f);
    sheet.addView(avatar, avatarParams);

    TextView name = new TextView(context);
    name.setText(tdlib.cache().userName(userId));
    name.setTextColor(Theme.textAccentColor());
    name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    name.setTypeface(Fonts.getRobotoMedium());
    name.setSingleLine(true);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    name.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    nameParams.bottomMargin = Screen.dp(14f);
    sheet.addView(name, nameParams);

    LinearLayout button = new LinearLayout(context);
    button.setOrientation(LinearLayout.HORIZONTAL);
    button.setGravity(Gravity.CENTER);
    GradientDrawable buttonBg = new GradientDrawable();
    buttonBg.setColor(CALL_GREEN);
    buttonBg.setCornerRadius(Screen.dp(26f));
    button.setBackground(buttonBg);
    ImageView icon = new ImageView(context);
    icon.setImageResource(video ? R.drawable.baseline_videocam_24 : R.drawable.baseline_call_24);
    icon.setColorFilter(0xffffffff);
    button.addView(icon, new LinearLayout.LayoutParams(Screen.dp(22f), Screen.dp(22f)));
    TextView label = new TextView(context);
    label.setText(Lang.getString(video ? R.string.Tgx101VideoCall : R.string.Call));
    label.setTextColor(0xffffffff);
    label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    label.setTypeface(Fonts.getRobotoMedium());
    LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    labelParams.leftMargin = Screen.dp(10f);
    button.addView(label, labelParams);
    final boolean[] answered = {false};
    button.setOnClickListener(v -> {
      if (answered[0]) return;
      answered[0] = true;
      Tgx101Diag.mark("contact call: confirmed in the sheet");
      popup.hideWindow(true);
      onCall.run();
    });
    sheet.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(52f)));

    TextView hint = new TextView(context);
    hint.setText(Lang.getString(R.string.Tgx101CallConfirmHint));
    hint.setTextColor(Theme.textDecentColor());
    hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
    hint.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    hintParams.topMargin = Screen.dp(10f);
    sheet.addView(hint, hintParams);

    // a swipe down cancels (the hint says so)
    final float[] downY = {0f};
    sheet.setOnTouchListener((v, e) -> {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downY[0] = e.getRawY();
          return true;
        case MotionEvent.ACTION_MOVE:
          v.setTranslationY(Math.max(0f, e.getRawY() - downY[0]));
          return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          if (v.getTranslationY() > Screen.dp(56f)) {
            Tgx101Diag.mark("contact call: cancelled by a swipe");
            popup.hideWindow(true);
          } else {
            v.animate().translationY(0f).setDuration(150).start();
          }
          return true;
      }
      return false;
    });

    android.widget.FrameLayout wrap = new android.widget.FrameLayout(context);
    android.widget.FrameLayout.LayoutParams sheetParams = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    sheetParams.leftMargin = sheetParams.rightMargin = sheetParams.bottomMargin = Screen.dp(8f);
    wrap.addView(sheet, sheetParams);
    avatar.attach();
    popup.setDismissListener(p -> {
      avatar.performDestroy();
      if (!answered[0]) Tgx101Diag.mark("contact call: cancelled");
    });
    int height = Screen.dp(8f + 10f + 4f + 12f + 52f + 6f + 22f + 14f + 52f + 10f + 18f + 14f);
    popup.showSimplePopupView(wrap, height);
  }
}
