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
package org.thunderdog.challegram.ui;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * TGx101: incoming call controls of the new call screen (TruePhone-like). Three tap buttons —
 * «Отклонить», «Сообщение» (quick replies), «Без звука» — and a wide «Ответить» slider: answering
 * is the only swipe, so the phone can't pick up by accident in a pocket. The quick reply card
 * lies over the photo without resizing anything.
 */
public class Tgx101IncomingControls extends FrameLayout {
  public interface Callback {
    void onAnswer ();
    void onDecline ();
    void onSilence ();
    void onQuickReply (String text);
  }

  private static final int COLOR_BUTTON = 0x1affffff, COLOR_ACTIVE = 0x52ffffff;

  private final Callback callback;
  private final LinearLayout replies;
  private final ImageView messageButton, silenceButton;
  private boolean answered;

  public Tgx101IncomingControls (Context context, Callback callback) {
    super(context);
    this.callback = callback;

    // Quick replies: a card above the buttons, over the photo
    replies = new LinearLayout(context);
    replies.setOrientation(LinearLayout.VERTICAL);
    replies.setPadding(0, Screen.dp(6f), 0, Screen.dp(6f));
    GradientDrawable card = new GradientDrawable();
    card.setCornerRadius(Screen.dp(18f));
    card.setColor(0xff243342);
    replies.setBackground(card);
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      replies.setElevation(Screen.dp(8f));
    }
    replies.setVisibility(View.GONE);
    LayoutParams repliesParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    repliesParams.leftMargin = repliesParams.rightMargin = Screen.dp(28f);
    repliesParams.bottomMargin = Screen.dp(44f + 72f + 36f + 100f + 14f);
    addView(replies, repliesParams);

    // Decline · Message · Silence
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    LayoutParams rowParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    rowParams.leftMargin = rowParams.rightMargin = Screen.dp(20f);
    rowParams.bottomMargin = Screen.dp(44f + 72f + 36f);
    addView(row, rowParams);
    ImageView decline = addButton(row, R.drawable.baseline_call_end_24, R.string.Tgx101CallDecline, v -> {
      if (!answered) callback.onDecline();
    });
    decline.setColorFilter(0xffff6b6b);
    messageButton = addButton(row, R.drawable.baseline_chat_bubble_24, R.string.Tgx101CallMessage, v -> toggleReplies());
    silenceButton = addButton(row, R.drawable.baseline_volume_off_24, R.string.Tgx101CallSilence, v -> {
      callback.onSilence();
      setActive((ImageView) v, true);
    });

    // «Ответить» slider
    AnswerSlider slider = new AnswerSlider(context);
    LayoutParams sliderParams = new LayoutParams(Screen.dp(310f), Screen.dp(72f), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    sliderParams.bottomMargin = Screen.dp(44f);
    addView(slider, sliderParams);
  }

  private ImageView addButton (LinearLayout row, int icon, int label, View.OnClickListener onClick) {
    LinearLayout cell = new LinearLayout(getContext());
    cell.setOrientation(LinearLayout.VERTICAL);
    cell.setGravity(Gravity.CENTER_HORIZONTAL);
    ImageView button = new ImageView(getContext());
    button.setImageResource(icon);
    button.setColorFilter(0xffffffff);
    button.setScaleType(ImageView.ScaleType.CENTER);
    GradientDrawable circle = new GradientDrawable();
    circle.setShape(GradientDrawable.OVAL);
    circle.setColor(COLOR_BUTTON);
    button.setBackground(circle);
    button.setOnClickListener(onClick);
    button.setContentDescription(Lang.getString(label));
    cell.addView(button, new LinearLayout.LayoutParams(Screen.dp(64f), Screen.dp(64f)));
    TextView text = new TextView(getContext());
    text.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoRegular()); // TGx101: Manrope
    text.setText(Lang.getString(label));
    text.setTextColor(0xffffffff);
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
    text.setGravity(Gravity.CENTER_HORIZONTAL);
    text.setMaxLines(2);
    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    textParams.topMargin = Screen.dp(10f);
    cell.addView(text, textParams);
    row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    return button;
  }

  private static void setActive (ImageView button, boolean active) {
    ((GradientDrawable) button.getBackground()).setColor(active ? COLOR_ACTIVE : COLOR_BUTTON);
  }

  private void toggleReplies () {
    boolean show = replies.getVisibility() != View.VISIBLE;
    if (show) {
      replies.removeAllViews();
      for (int i = 0; i < Settings.QUICK_REPLY_SHOWN; i++) {
        final String reply = Settings.instance().getQuickReply(i);
        TextView item = new TextView(getContext());
        item.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoRegular()); // TGx101: Manrope
        item.setText(reply);
        item.setTextColor(0xffffffff);
        item.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        item.setPadding(Screen.dp(20f), Screen.dp(15f), Screen.dp(20f), Screen.dp(15f));
        item.setMaxLines(2);
        item.setOnClickListener(v -> {
          if (answered) return;
          answered = true;
          replies.setVisibility(View.GONE);
          callback.onQuickReply(reply);
        });
        if (i > 0) {
          View divider = new View(getContext());
          divider.setBackgroundColor(0x12ffffff);
          replies.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Screen.dp(.5f))));
        }
        replies.addView(item, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      }
    }
    replies.setVisibility(show ? View.VISIBLE : View.GONE);
    setActive(messageButton, show);
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent event) {
    // A tap outside the card closes it; otherwise the screen behind stays untouched
    if (event.getAction() == MotionEvent.ACTION_DOWN && replies.getVisibility() == View.VISIBLE) {
      toggleReplies();
      return true;
    }
    return false;
  }

  /** Wide pill with a white knob: drag it to the right end to answer; released earlier it slides back. */
  private class AnswerSlider extends FrameLayout {
    private final ImageView knob;
    private float downX, startTranslation;
    private boolean dragging;

    AnswerSlider (Context context) {
      super(context);
      GradientDrawable pill = new GradientDrawable();
      pill.setCornerRadius(Screen.dp(36f));
      pill.setColor(0x1fffffff);
      setBackground(pill);

      TextView label = new TextView(context);

      label.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoRegular()); // TGx101: Manrope

      label.setText(Lang.getString(R.string.Tgx101CallAnswer) + "  ›››");
      label.setTextColor(0xffffffff);
      label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
      label.setGravity(Gravity.CENTER);
      LayoutParams labelParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      labelParams.leftMargin = Screen.dp(66f);
      addView(label, labelParams);

      knob = new ImageView(context);
      knob.setImageResource(R.drawable.baseline_phone_24);
      knob.setColorFilter(0xff2fa855);
      knob.setScaleType(ImageView.ScaleType.CENTER);
      GradientDrawable circle = new GradientDrawable();
      circle.setShape(GradientDrawable.OVAL);
      circle.setColor(0xffffffff);
      knob.setBackground(circle);
      knob.setContentDescription(Lang.getString(R.string.Tgx101CallAnswer));
      LayoutParams knobParams = new LayoutParams(Screen.dp(60f), Screen.dp(60f), Gravity.LEFT | Gravity.CENTER_VERTICAL);
      knobParams.leftMargin = Screen.dp(6f);
      addView(knob, knobParams);
    }

    private float maxTranslation () {
      return getMeasuredWidth() - Screen.dp(6f) * 2 - knob.getMeasuredWidth();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (answered) return true;
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          float x = e.getX();
          dragging = x <= knob.getLeft() + knob.getTranslationX() + knob.getWidth() + Screen.dp(16f);
          downX = x;
          startTranslation = knob.getTranslationX();
          if (dragging) getParent().requestDisallowInterceptTouchEvent(true);
          return dragging;
        }
        case MotionEvent.ACTION_MOVE: {
          if (!dragging) return false;
          float t = Math.max(0f, Math.min(maxTranslation(), startTranslation + e.getX() - downX));
          knob.setTranslationX(t);
          return true;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          if (!dragging) return false;
          dragging = false;
          float max = maxTranslation();
          if (e.getAction() == MotionEvent.ACTION_UP && max > 0 && knob.getTranslationX() >= max * .75f) {
            answered = true;
            knob.animate().translationX(max).setDuration(120).start();
            callback.onAnswer();
          } else {
            ValueAnimator back = ValueAnimator.ofFloat(knob.getTranslationX(), 0f);
            back.setDuration(200);
            back.addUpdateListener(a -> knob.setTranslationX((float) a.getAnimatedValue()));
            back.start();
          }
          return true;
        }
      }
      return false;
    }
  }
}
