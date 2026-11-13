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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 «Подтверждение» (user 2026-11-13, mockup «Окно подтверждения» approved): a compact card that rises at the input
 * field before a voice, a sticker, photos / videos or a GIF go — what is going on the left, where to on the right,
 * «Cancel» quiet, «Send» in the accent colour, «Don't ask again for …» switching just this kind off. A tap outside or a
 * swipe down cancels.
 */
public final class Tgx101SendConfirmSheet {
  private Tgx101SendConfirmSheet () { }

  private static final int[] TITLES = {R.string.Tgx101ConfirmVoiceTitle, R.string.Tgx101ConfirmStickerTitle, R.string.Tgx101ConfirmMediaTitle, R.string.Tgx101ConfirmGifTitle};
  private static final int[] NEVER = {R.string.Tgx101ConfirmNeverVoice, R.string.Tgx101ConfirmNeverSticker, R.string.Tgx101ConfirmNeverMedia, R.string.Tgx101ConfirmNeverGif};
  private static final int[] ICONS = {R.drawable.baseline_mic_24, R.drawable.deproko_baseline_stickers_24, R.drawable.baseline_image_24, R.drawable.deproko_baseline_gif_24};

  /** true — the sheet is up and decides; false — this kind is sent without asking */
  public static boolean ask (ViewController<?> c, int kind, long chatId, @Nullable CharSequence title, @Nullable CharSequence preview, @Nullable CharSequence detail, Runnable onSend, @Nullable Runnable onCancel) {
    if (!Settings.instance().tgx101Confirm(kind)) return false;
    show(c, kind, chatId, title, preview, detail, onSend, onCancel);
    return true;
  }

  private static void show (ViewController<?> c, int kind, long chatId, @Nullable CharSequence titleText, @Nullable CharSequence preview, @Nullable CharSequence detail, Runnable onSend, @Nullable Runnable onCancel) {
    android.content.Context context = c.context();
    final String what = kind == Settings.CONFIRM_VOICE ? "voice" : kind == Settings.CONFIRM_STICKER ? "sticker" : kind == Settings.CONFIRM_MEDIA ? "media" : "gif";
    Tgx101Diag.mark("confirm: asked before sending " + what);
    final PopupLayout popup = new PopupLayout(context);
    popup.init(true);
    popup.setNeedRootInsets();

    final int accent = Theme.getColor(ColorId.textNeutral);
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(Screen.dp(14f), Screen.dp(14f), Screen.dp(14f), Screen.dp(12f));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Theme.fillingColor());
    bg.setCornerRadius(Screen.dp(22f));
    card.setBackground(bg);
    if (android.os.Build.VERSION.SDK_INT >= 21) card.setElevation(Screen.dp(10f));

    // the title, and where it goes
    LinearLayout head = new LinearLayout(context);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(Gravity.CENTER_VERTICAL);
    TextView title = new TextView(context);
    title.setText(titleText != null ? titleText : Lang.getString(TITLES[kind]));
    title.setTextColor(Theme.textAccentColor());
    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    title.setTypeface(Fonts.getRobotoMedium());
    title.setSingleLine(true);
    title.setEllipsize(TextUtils.TruncateAt.END);
    head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    TextView where = new TextView(context);
    where.setText(c.tdlib().chatTitle(chatId));
    where.setTextColor(Theme.textDecentColor());
    where.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    where.setSingleLine(true);
    where.setEllipsize(TextUtils.TruncateAt.END);
    where.setMaxWidth(Screen.dp(140f));
    LinearLayout.LayoutParams whereParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    whereParams.leftMargin = Screen.dp(10f);
    head.addView(where, whereParams);
    card.addView(head);

    // what goes: a plate with its icon (or the sticker's emoji, big) and a word on it
    LinearLayout plate = new LinearLayout(context);
    plate.setOrientation(LinearLayout.HORIZONTAL);
    plate.setGravity(Gravity.CENTER_VERTICAL);
    plate.setPadding(Screen.dp(10f), Screen.dp(8f), Screen.dp(12f), Screen.dp(8f));
    GradientDrawable plateBg = new GradientDrawable();
    plateBg.setColor(ColorUtils.alphaColor(.1f, accent));
    plateBg.setCornerRadius(Screen.dp(14f));
    plate.setBackground(plateBg);
    if (!TextUtils.isEmpty(preview)) {
      TextView big = new TextView(context);
      big.setText(preview);
      big.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 40f);
      big.setIncludeFontPadding(false);
      plate.addView(big, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    } else {
      ImageView icon = new ImageView(context);
      icon.setImageResource(ICONS[kind]);
      icon.setColorFilter(accent);
      plate.addView(icon, new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f)));
    }
    TextView detailView = new TextView(context);
    detailView.setText(detail);
    detailView.setTextColor(Theme.textAccentColor());
    detailView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    detailView.setMaxLines(2);
    detailView.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    detailParams.leftMargin = Screen.dp(12f);
    if (!TextUtils.isEmpty(detail)) plate.addView(detailView, detailParams);
    LinearLayout.LayoutParams plateParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    plateParams.topMargin = Screen.dp(12f);
    card.addView(plate, plateParams);

    // don't ask again for this kind
    final boolean[] never = {false};
    LinearLayout neverRow = new LinearLayout(context);
    neverRow.setOrientation(LinearLayout.HORIZONTAL);
    neverRow.setGravity(Gravity.CENTER_VERTICAL);
    neverRow.setMinimumHeight(Screen.dp(40f));
    final ImageView box = new ImageView(context);
    box.setImageResource(R.drawable.baseline_check_box_outline_blank_24);
    box.setColorFilter(Theme.textDecentColor());
    neverRow.addView(box, new LinearLayout.LayoutParams(Screen.dp(22f), Screen.dp(22f)));
    TextView neverText = new TextView(context);
    neverText.setText(Lang.getString(NEVER[kind]));
    neverText.setTextColor(Theme.textDecentColor());
    neverText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    LinearLayout.LayoutParams neverParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    neverParams.leftMargin = Screen.dp(10f);
    neverRow.addView(neverText, neverParams);
    neverRow.setOnClickListener(v -> {
      never[0] = !never[0];
      box.setImageResource(never[0] ? R.drawable.tgx101_check_box_24 : R.drawable.baseline_check_box_outline_blank_24);
      box.setColorFilter(never[0] ? accent : Theme.textDecentColor());
    });
    LinearLayout.LayoutParams neverRowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    neverRowParams.topMargin = Screen.dp(6f);
    card.addView(neverRow, neverRowParams);

    // Cancel · Send
    final boolean[] answered = {false};
    LinearLayout buttons = new LinearLayout(context);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    TextView cancel = button(context, Lang.getString(R.string.Cancel), ColorUtils.alphaColor(.08f, Theme.textAccentColor()), Theme.textAccentColor());
    cancel.setOnClickListener(v -> popup.hideWindow(true));
    LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, Screen.dp(46f), 1f);
    buttons.addView(cancel, cancelParams);
    TextView send = button(context, Lang.getString(R.string.Send), accent, Theme.fillingColor());
    send.setOnClickListener(v -> {
      if (answered[0]) return;
      answered[0] = true;
      if (never[0]) Settings.instance().setTgx101Confirm(kind, false);
      Tgx101Diag.mark("confirm: sent " + what + (never[0] ? ", not asked again" : ""));
      popup.hideWindow(true);
      onSend.run();
    });
    LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(0, Screen.dp(46f), 1.4f);
    sendParams.leftMargin = Screen.dp(8f);
    buttons.addView(send, sendParams);
    LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    buttonsParams.topMargin = Screen.dp(6f);
    card.addView(buttons, buttonsParams);

    // a swipe down cancels
    final float[] downY = {0f};
    card.setOnTouchListener((v, e) -> {
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
            popup.hideWindow(true);
          } else {
            v.animate().translationY(0f).setDuration(150).start();
          }
          return true;
      }
      return false;
    });

    FrameLayout wrap = new FrameLayout(context);
    FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    cardParams.leftMargin = cardParams.rightMargin = cardParams.bottomMargin = Screen.dp(8f);
    cardParams.bottomMargin += Tgx101Sheet.tgx101NavigationInset(c);
    wrap.addView(card, cardParams);
    wrap.setClickable(true);
    wrap.setOnClickListener(v -> popup.hideWindow(true)); // a tap outside only closes the card
    popup.setDismissListener(p -> {
      if (!answered[0]) {
        Tgx101Diag.mark("confirm: cancelled " + what);
        if (onCancel != null) onCancel.run();
      }
    });
    org.thunderdog.challegram.tool.Tgx101Haptics.play(org.thunderdog.challegram.tool.Tgx101Haptics.CLICK);
    int height = Screen.dp(8f + 14f + 22f + 12f + 60f + 6f + 40f + 6f + 46f + 12f);
    popup.showSimplePopupView(wrap, height);
  }

  private static TextView button (android.content.Context context, String text, int fill, int textColor) {
    TextView b = new TextView(context);
    b.setText(text);
    b.setGravity(Gravity.CENTER);
    b.setTextColor(textColor);
    b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    b.setTypeface(Fonts.getRobotoMedium());
    GradientDrawable d = new GradientDrawable();
    d.setColor(fill);
    d.setCornerRadius(Screen.dp(14f));
    b.setBackground(d);
    return b;
  }
}
