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
 * File created on 10/06/2017
 */
package org.thunderdog.challegram.voip.gui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.Tgx101Sheet;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.NoScrollTextView;

/**
 * The rating after a call, as the app's own sheet (user 2026-11-04 13:18, a screenshot of the system dialog:
 * «передалай на наш лад»): rises from the bottom, follows the finger down, a tap outside closes it.
 */
public class VoIPFeedbackActivity extends Activity {
  private View scrim;
  private Tgx101Sheet.DragSheet sheet;
  private MaterialEditTextGroup commentBox;
  private boolean closing;

  @Override
  protected void onCreate (Bundle savedInstanceState) {
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    super.onCreate(savedInstanceState);

    overridePendingTransition(0, 0);

    int accountId = getIntent().getIntExtra("account_id", TdlibAccount.NO_ID);
    int callId = getIntent().getIntExtra("call_id", 0);
    Tdlib tdlib = TdlibManager.getTdlib(accountId); // FIXME: acquire reference?

    FrameLayout root = new FrameLayout(this);
    scrim = new View(this);
    scrim.setBackgroundColor(Color.argb(102, 0, 0, 0));
    scrim.setAlpha(0f);
    scrim.setOnClickListener(v -> close());
    root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    sheet = new Tgx101Sheet.DragSheet(this);
    sheet.swipedAway = this::close;
    sheet.setOrientation(LinearLayout.VERTICAL);
    int pad = Screen.dp(20f);
    sheet.setPadding(pad, Screen.dp(20f), pad, Screen.dp(8f));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Theme.fillingColor());
    bg.setCornerRadius(Screen.dp(22f));
    sheet.setBackground(bg);
    sheet.setClickable(true);

    TextView title = new NoScrollTextView(this);
    title.setText(Lang.getString(R.string.Tgx101CallRateTitle));
    title.setTextColor(Theme.textAccentColor());
    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19f);
    title.setTypeface(Fonts.getRobotoMedium());
    title.setGravity(Gravity.CENTER);
    sheet.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    final BetterRatingView bar = new BetterRatingView(this);
    LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    barParams.gravity = Gravity.CENTER_HORIZONTAL;
    barParams.topMargin = Screen.dp(18f);
    sheet.addView(bar, barParams);

    commentBox = new MaterialEditTextGroup(this, tdlib);
    commentBox.setHint(R.string.VoipFeedbackCommentHint);
    commentBox.setVisibility(View.GONE);
    commentBox.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    boxParams.topMargin = Screen.dp(12f);
    sheet.addView(commentBox, boxParams);

    TextView send = new TextView(this);
    send.setText(Lang.getString(R.string.Tgx101CallRateSend));
    send.setTextColor(Theme.getColor(ColorId.fillingPositiveContent));
    send.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    send.setTypeface(Fonts.getRobotoMedium());
    send.setGravity(Gravity.CENTER);
    GradientDrawable sendBg = new GradientDrawable();
    sendBg.setColor(Theme.getColor(ColorId.fillingPositive));
    sendBg.setCornerRadius(Screen.dp(14f));
    send.setBackground(sendBg);
    Views.setClickable(send);
    send.setEnabled(false);
    send.setAlpha(.4f);
    send.setOnClickListener(v -> {
      int rating = bar.getRating();
      String comment = rating < 5 ? commentBox.getText().toString() : "";
      Log.i(Log.TAG_VOIP, "Submitting call feedback, call_id: %d, rating: %d, comment: %s", callId, rating, comment);
      tdlib.send(new TdApi.SendCallRating(new TdApi.InputCallDiscarded(callId), rating, comment, null), tdlib.typedOkHandler());
      close();
    });
    LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(50f));
    sendParams.topMargin = Screen.dp(20f);
    sheet.addView(send, sendParams);

    TextView later = new TextView(this);
    later.setText(Lang.getString(R.string.Tgx101CallRateLater));
    later.setTextColor(Theme.textDecentColor());
    later.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    later.setGravity(Gravity.CENTER);
    Views.setClickable(later);
    RippleSupport.setTransparentSelector(later);
    later.setOnClickListener(v -> {
      Log.i(Log.TAG_VOIP, "User denied to give feedback");
      close();
    });
    LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(46f));
    laterParams.topMargin = Screen.dp(4f);
    sheet.addView(later, laterParams);

    FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    sheetParams.leftMargin = sheetParams.rightMargin = sheetParams.bottomMargin = Screen.dp(8f);
    root.addView(sheet, sheetParams);
    setContentView(root);

    bar.setOnRatingChangeListener(rating -> {
      send.setEnabled(rating > 0);
      send.setAlpha(rating > 0 ? 1f : .4f);
      commentBox.setVisibility(rating < 5 && rating > 0 ? View.VISIBLE : View.GONE);
      if (commentBox.getVisibility() == View.GONE) hideKeyboard();
    });

    // over the navigation bar and, riding up with it, the keyboard
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      final ValueAnimator[] lift = new ValueAnimator[1];
      root.setOnApplyWindowInsetsListener((v, insets) -> {
        int bottom = insets.getSystemWindowInsetBottom();
        if (v.getPaddingBottom() != bottom) {
          if (lift[0] != null) lift[0].cancel();
          if (sheet.getHeight() == 0) {
            v.setPadding(0, 0, 0, bottom);
          } else {
            ValueAnimator a = ValueAnimator.ofInt(v.getPaddingBottom(), bottom);
            a.setDuration(200);
            a.setInterpolator(new DecelerateInterpolator(1.6f));
            a.addUpdateListener(u -> v.setPadding(0, 0, 0, (int) u.getAnimatedValue()));
            lift[0] = a;
            a.start();
          }
        }
        return insets;
      });
    }

    sheet.setVisibility(View.INVISIBLE);
    sheet.post(() -> {
      sheet.setTranslationY(sheet.getHeight() + Screen.dp(8f));
      sheet.setVisibility(View.VISIBLE);
      sheet.animate().translationY(0f).setDuration(220).setInterpolator(new DecelerateInterpolator(1.6f)).start();
      scrim.animate().alpha(1f).setDuration(220).start();
    });
  }

  private void hideKeyboard () {
    InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (manager != null) manager.hideSoftInputFromWindow(sheet.getWindowToken(), 0);
  }

  private void close () {
    if (closing) return;
    closing = true;
    hideKeyboard();
    scrim.animate().alpha(0f).setDuration(180).start();
    sheet.animate().translationY(sheet.getHeight() + Screen.dp(8f)).setDuration(180).setInterpolator(new DecelerateInterpolator(1.6f))
      .withEndAction(this::finish).start();
  }

  @Override
  public void onBackPressed () {
    close();
  }

  @Override
  public void finish() {
    super.finish();
    overridePendingTransition(0, 0);
  }
}
