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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

/**
 * TGx101 (approved mockup «Группы с темами», Б — user 2026-10-22): «Topics | Feed» under the header of a group with topics.
 * The same control on the topics list and on the group's one feed; a tap on the other half (or a sideways swipe over it)
 * slides the thumb there and calls the listener, which swaps the screens.
 */
public class Tgx101TopicsSwitch extends View implements FactorAnimator.Target {
  public interface Listener {
    void onTopicsSwitch (boolean toFeed);
  }

  public static final float HEIGHT = 44f;

  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final FactorAnimator thumb;
  private float factor; // 0 — topics, 1 — feed
  private boolean feed;
  private Listener listener;
  private final String topicsText, feedText;

  public Tgx101TopicsSwitch (Context context, boolean feed) {
    super(context);
    this.feed = feed;
    this.factor = feed ? 1f : 0f;
    this.thumb = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200L, factor);
    this.topicsText = Lang.getString(R.string.Tgx101TopicsTabTopics);
    this.feedText = Lang.getString(R.string.Tgx101TopicsTabFeed);
    textPaint.setTypeface(Fonts.getRobotoMedium());
    textPaint.setTextSize(Screen.dp(14f));
  }

  public void setListener (Listener listener) {
    this.listener = listener;
  }

  /** Where the thumb stands, without telling the listener (a screen shown again) */
  public void setFeed (boolean feed, boolean animated) {
    this.feed = feed;
    if (animated) {
      thumb.animateTo(feed ? 1f : 0f);
    } else {
      thumb.forceFactor(feed ? 1f : 0f);
      factor = feed ? 1f : 0f;
      invalidate();
    }
  }

  private void select (boolean toFeed) {
    if (this.feed == toFeed) return;
    this.feed = toFeed;
    thumb.animateTo(toFeed ? 1f : 0f);
    if (listener != null) {
      // the thumb gets there first, then the screen changes
      postDelayed(() -> {
        if (listener != null && this.feed == toFeed) listener.onTopicsSwitch(toFeed);
      }, 140);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Screen.dp(HEIGHT), MeasureSpec.EXACTLY));
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    int side = Screen.dp(8f), vertical = Screen.dp(4f);
    rect.set(side, vertical, getMeasuredWidth() - side, getMeasuredHeight() - vertical);
    float radius = rect.height() / 2f;
    // the track: the field's colour with a hairline, like the floating capsules in the chat
    c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(Theme.fillingColor()));
    float half = Math.max(1, Screen.dp(.5f)) / 2f;
    rect.inset(half, half);
    c.drawRoundRect(rect, radius, radius, Paints.strokeSeparatorPaint(Theme.separatorColor()));
    rect.inset(-half, -half);
    // the thumb
    float pad = Screen.dp(3f);
    float segment = (rect.width() - pad * 2) / 2f;
    float left = rect.left + pad + segment * factor;
    RectF t = Paints.getRectF();
    t.set(left, rect.top + pad, left + segment, rect.bottom - pad);
    float tr = t.height() / 2f;
    c.drawRoundRect(t, tr, tr, Paints.fillingPaint(ColorUtils.alphaColor(.14f, Theme.getColor(ColorId.textNeutral))));
    // the texts
    float cy = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
    int active = Theme.getColor(ColorId.textNeutral), idle = Theme.textDecentColor();
    textPaint.setColor(ColorUtils.fromToArgb(active, idle, factor));
    c.drawText(topicsText, rect.left + pad + segment / 2f - textPaint.measureText(topicsText) / 2f, cy, textPaint);
    textPaint.setColor(ColorUtils.fromToArgb(idle, active, factor));
    c.drawText(feedText, rect.left + pad + segment * 1.5f - textPaint.measureText(feedText) / 2f, cy, textPaint);
  }

  private float downX, downY;
  private boolean tracking;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downX = e.getX();
        downY = e.getY();
        tracking = true;
        return true;
      case MotionEvent.ACTION_MOVE:
        if (tracking && Math.abs(e.getX() - downX) > Screen.dp(24f) && Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY)) {
          tracking = false;
          select(e.getX() > downX); // swipe towards a half selects it
        }
        return true;
      case MotionEvent.ACTION_UP:
        if (tracking) {
          tracking = false;
          select(e.getX() > getMeasuredWidth() / 2f);
        }
        return true;
      case MotionEvent.ACTION_CANCEL:
        tracking = false;
        return true;
    }
    return super.onTouchEvent(e);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    this.factor = factor;
    invalidate();
  }
}
