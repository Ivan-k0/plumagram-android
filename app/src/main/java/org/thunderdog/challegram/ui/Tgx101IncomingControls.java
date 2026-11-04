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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
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
import me.vkryl.core.ColorUtils;
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
  private final ImageView messageButton;
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

    // Decline · Message, closer to the centre (TGx101: the ringing is muted with the volume or power key, no «Без звука» button)
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    LayoutParams rowParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    rowParams.leftMargin = rowParams.rightMargin = Screen.dp(56f);
    rowParams.bottomMargin = Screen.dp(44f + 72f + 36f);
    addView(row, rowParams);
    ImageView decline = addButton(row, R.drawable.baseline_call_end_24, R.string.Tgx101CallDecline, v -> {
      if (!answered) callback.onDecline();
    });
    decline.setColorFilter(0xffff6b6b);
    messageButton = addButton(row, R.drawable.baseline_chat_bubble_24, R.string.Tgx101CallMessage, v -> toggleReplies());

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
    private final LinearLayout content;
    private float downX, startTranslation;
    private boolean dragging;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF fillRect = new RectF();
    // (user 2026-11-04, variant 4) waiting the knob breathes; pulled, the track fills with green; past the point where
    // letting go answers, a tick and the knob turns green. The same control on voice, video and group invites
    private static final int FILL_FROM = 0x8c11161c, FILL_TO = 0xff2fa855, KNOB_WHITE = 0xffffffff, KNOB_GREEN = 0xff2fa855;
    private static final float ANSWER_AT = .75f;
    private final GradientDrawable circle = new GradientDrawable();
    private ValueAnimator breath, settle, popAnimator, greenAnimator;
    private float breathAmount = 1f, breathWave, pop, green;
    private boolean past;

    AnswerSlider (Context context) {
      super(context);
      GradientDrawable pill = new GradientDrawable();
      pill.setCornerRadius(Screen.dp(36f));
      pill.setColor(0x1fffffff);
      setBackground(pill);

      TextView label = new TextView(context);

      label.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoRegular()); // TGx101: Manrope

      label.setText(Lang.getString(R.string.Tgx101CallAnswer));
      label.setTextColor(0xffffffff);
      label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
      label.setGravity(Gravity.CENTER_VERTICAL);

      // «Ответить ›››»: the arrows run as a wave toward the answer end; the whole label hides under the dark fill behind the knob
      content = new LinearLayout(context);
      content.setOrientation(LinearLayout.HORIZONTAL);
      content.setGravity(Gravity.CENTER);
      content.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
      LinearLayout.LayoutParams arrowsParams = new LinearLayout.LayoutParams(Screen.dp(40f), ViewGroup.LayoutParams.MATCH_PARENT);
      arrowsParams.leftMargin = Screen.dp(10f);
      content.addView(new ArrowsView(context), arrowsParams);
      LayoutParams contentParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      contentParams.leftMargin = Screen.dp(66f);
      addView(content, contentParams);

      fillPaint.setColor(FILL_FROM);
      setWillNotDraw(false);

      knob = new ImageView(context);
      knob.setImageResource(R.drawable.baseline_phone_24);
      knob.setColorFilter(KNOB_GREEN);
      knob.setScaleType(ImageView.ScaleType.CENTER);
      circle.setShape(GradientDrawable.OVAL);
      circle.setColor(0xffffffff);
      knob.setBackground(circle);
      knob.setContentDescription(Lang.getString(R.string.Tgx101CallAnswer));
      LayoutParams knobParams = new LayoutParams(Screen.dp(60f), Screen.dp(60f), Gravity.LEFT | Gravity.CENTER_VERTICAL);
      knobParams.leftMargin = Screen.dp(6f);
      addView(knob, knobParams);
    }

    private float fillRight () {
      return knob.getLeft() + knob.getTranslationX() + knob.getWidth() + Screen.dp(6f);
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      if (knob.getTranslationX() > 0f) {
        float inset = Screen.dp(2f);
        fillRect.set(inset, inset, fillRight(), getHeight() - inset);
        float r = fillRect.height() / 2f;
        c.drawRoundRect(fillRect, r, r, fillPaint);
      }
      super.dispatchDraw(c);
    }

    @Override
    protected boolean drawChild (Canvas c, View child, long drawingTime) {
      if (child == content && knob.getTranslationX() > 0f) {
        int save = c.save();
        c.clipRect(fillRight(), 0, getWidth(), getHeight());
        boolean result = super.drawChild(c, child, drawingTime);
        c.restoreToCount(save);
        return result;
      }
      return super.drawChild(c, child, drawingTime);
    }

    private void setKnobTranslation (float t) {
      knob.setTranslationX(t);
      float max = maxTranslation();
      float progress = max > 0 ? Math.min(1f, t / max) : 0f;
      fillPaint.setColor(ColorUtils.fromToArgb(FILL_FROM, FILL_TO, progress));
      content.setAlpha(Math.max(0f, 1f - progress * 1.3f));
      boolean nowPast = max > 0 && t >= max * ANSWER_AT;
      if (nowPast != past) {
        past = nowPast;
        animateGreen(nowPast);
        if (nowPast) {
          knob.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
          animatePop();
        }
      }
      invalidate();
    }

    private void animateGreen (boolean toGreen) {
      if (greenAnimator != null) greenAnimator.cancel();
      greenAnimator = ValueAnimator.ofFloat(green, toGreen ? 1f : 0f);
      greenAnimator.setDuration(140);
      greenAnimator.addUpdateListener(a -> {
        green = (float) a.getAnimatedValue();
        circle.setColor(ColorUtils.fromToArgb(KNOB_WHITE, KNOB_GREEN, green));
        knob.setColorFilter(ColorUtils.fromToArgb(KNOB_GREEN, KNOB_WHITE, green));
      });
      greenAnimator.start();
    }

    private void animatePop () {
      if (popAnimator != null) popAnimator.cancel();
      popAnimator = ValueAnimator.ofFloat(1f, 0f);
      popAnimator.setDuration(260);
      popAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
      popAnimator.addUpdateListener(a -> {
        pop = (float) a.getAnimatedValue();
        applyKnobScale();
      });
      popAnimator.start();
    }

    private void applyKnobScale () {
      float scale = (1f + .045f * breathAmount * breathWave) * (1f + .13f * pop);
      knob.setScaleX(scale);
      knob.setScaleY(scale);
    }

    private void updateBreath () {
      boolean run = !answered && getWindowVisibility() == View.VISIBLE && androidx.core.view.ViewCompat.isAttachedToWindow(this);
      if (run == (breath != null)) return;
      if (run) {
        breath = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        breath.setDuration(3900);
        breath.setRepeatCount(ValueAnimator.INFINITE);
        breath.setInterpolator(new android.view.animation.LinearInterpolator());
        breath.addUpdateListener(a -> {
          if (answered) {
            updateBreath();
            return;
          }
          // eases away under the finger instead of stopping at once
          float target = !dragging && knob.getTranslationX() == 0f ? 1f : 0f;
          breathAmount += (target - breathAmount) * .12f;
          breathWave = (float) Math.sin((float) a.getAnimatedValue());
          applyKnobScale();
        });
        breath.start();
      } else {
        breath.cancel();
        breath = null;
        breathWave = 0f;
        applyKnobScale();
      }
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      updateBreath();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      if (breath != null) {
        breath.cancel();
        breath = null;
      }
    }

    @Override
    protected void onWindowVisibilityChanged (int visibility) {
      super.onWindowVisibilityChanged(visibility);
      updateBreath();
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
          if (dragging) {
            getParent().requestDisallowInterceptTouchEvent(true);
            if (settle != null) settle.cancel(); // caught on its way back: it stays under the finger
            startTranslation = knob.getTranslationX();
          }
          return dragging;
        }
        case MotionEvent.ACTION_MOVE: {
          if (!dragging) return false;
          float t = Math.max(0f, Math.min(maxTranslation(), startTranslation + e.getX() - downX));
          setKnobTranslation(t);
          return true;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          if (!dragging) return false;
          dragging = false;
          float max = maxTranslation();
          if (e.getAction() == MotionEvent.ACTION_UP && max > 0 && knob.getTranslationX() >= max * ANSWER_AT) {
            answered = true;
            ValueAnimator end = ValueAnimator.ofFloat(knob.getTranslationX(), max);
            end.setDuration(120);
            end.addUpdateListener(a -> setKnobTranslation((float) a.getAnimatedValue()));
            end.start();
            callback.onAnswer();
          } else {
            settle = ValueAnimator.ofFloat(knob.getTranslationX(), 0f);
            settle.setDuration(240);
            settle.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
            settle.addUpdateListener(a -> setKnobTranslation((float) a.getAnimatedValue()));
            settle.start();
          }
          return true;
        }
      }
      return false;
    }
  }

  /** Three «›» lighting up one after another (a slow wave, ~1.4 s), only while the incoming screen is shown. */
  private class ArrowsView extends View {
    private static final long PERIOD = 1400;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    ArrowsView (Context context) {
      super(context);
      paint.setColor(0xffffffff);
      paint.setTextSize(Screen.dp(22f));
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoMedium());
    }

    @Override
    protected void onDraw (Canvas c) {
      float phase = (SystemClock.uptimeMillis() % PERIOD) / (float) PERIOD;
      float step = getWidth() / 3f;
      float baseline = getHeight() / 2f - (paint.descent() + paint.ascent()) / 2f;
      for (int i = 0; i < 3; i++) {
        // distance of this arrow from the wave crest, wrapping around
        float d = Math.abs(phase * 3f - i);
        d = Math.min(d, 3f - d);
        float glow = Math.max(0f, 1f - d);
        paint.setAlpha((int) (255 * (.3f + .7f * glow)));
        c.drawText("›", step * i + step / 2f + Screen.dp(2f) * glow, baseline, paint);
      }
      if (!answered && androidx.core.view.ViewCompat.isAttachedToWindow(this) && getWindowVisibility() == View.VISIBLE) {
        postInvalidateOnAnimation();
      }
    }

    @Override
    protected void onWindowVisibilityChanged (int visibility) {
      super.onWindowVisibilityChanged(visibility);
      if (visibility == View.VISIBLE) invalidate();
    }
  }
}
