/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.GroupCallListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.voip.Tgx101GroupCall;

/**
 * TGx101 (user 2026-10-13 «пили потихоньку групповые звонки», step 1): a bar over the chat while a voice chat is going on
 * in it, as in the official app — «Voice chat · N participants» and «Join» (or «Return» when already in it). Before, a
 * voice chat could only be found in the ⋮ menu.
 */
public class Tgx101VoiceChatBar extends View implements GroupCallListener {
  public static final float HEIGHT = 48f;

  public interface Callback {
    void onVoiceChatBarClick (long chatId, int groupCallId);
    void onVoiceChatBarVisibilityChanged (boolean visible);
  }

  private final Tdlib tdlib;
  private final Callback callback;
  private final TextPaint titlePaint = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  private final TextPaint subtitlePaint = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  private final TextPaint buttonPaint = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  private final RectF buttonRect = new RectF();
  private final Drawable icon;

  private long chatId;
  private int groupCallId;
  private @Nullable TdApi.GroupCall groupCall;
  private boolean subscribed, visible;

  public Tgx101VoiceChatBar (Context context, Tdlib tdlib, Callback callback) {
    super(context);
    this.tdlib = tdlib;
    this.callback = callback;
    titlePaint.setTypeface(Fonts.getRobotoMedium());
    titlePaint.setTextSize(Screen.dp(15f));
    subtitlePaint.setTypeface(Fonts.getRobotoRegular());
    subtitlePaint.setTextSize(Screen.dp(13f));
    buttonPaint.setTypeface(Fonts.getRobotoMedium());
    buttonPaint.setTextSize(Screen.dp(14f));
    icon = Drawables.get(getResources(), R.drawable.baseline_mic_24);
  }

  /** The chat's voice chat changed (or the chat was opened): shows, updates or hides the bar */
  public void setVideoChat (long chatId, @Nullable TdApi.VideoChat videoChat) {
    int newGroupCallId = videoChat != null ? videoChat.groupCallId : 0;
    boolean joinedHere = isJoinedHere(chatId, newGroupCallId);
    boolean show = newGroupCallId != 0 && (videoChat.hasParticipants || joinedHere);
    if (this.chatId != chatId || this.groupCallId != newGroupCallId) {
      this.chatId = chatId;
      this.groupCallId = newGroupCallId;
      this.groupCall = null;
    }
    setSubscribed(show);
    if (show) {
      requestGroupCall();
    }
    setBarVisible(show);
  }

  private static boolean isJoinedHere (long chatId, int groupCallId) {
    Tgx101GroupCall call = Tgx101GroupCall.current();
    return call != null && groupCallId != 0 && call.chatId == chatId && call.groupCallId == groupCallId && call.getState() != Tgx101GroupCall.STATE_ENDED;
  }

  private void requestGroupCall () {
    final int requestedId = groupCallId;
    tdlib.send(new TdApi.GetGroupCall(requestedId), (result, error) -> UI.post(() -> {
      if (result != null && requestedId == groupCallId) {
        groupCall = result;
        invalidate();
      }
    }));
  }

  private void setSubscribed (boolean subscribed) {
    if (this.subscribed != subscribed) {
      this.subscribed = subscribed;
      if (subscribed) {
        tdlib.listeners().subscribeForGlobalUpdates(this);
      } else {
        tdlib.listeners().unsubscribeFromGlobalUpdates(this);
      }
    }
  }

  private void setBarVisible (boolean visible) {
    this.visible = visible;
    callback.onVoiceChatBarVisibilityChanged(visible); // always: the chat hides all its bars when it's reopened
    invalidate();
  }

  public boolean isBarVisible () {
    return visible;
  }

  public void performDestroy () {
    setSubscribed(false);
  }

  @Override
  public void onGroupCallUpdated (TdApi.GroupCall groupCall) {
    UI.post(() -> {
      if (groupCall.id != groupCallId) return;
      this.groupCall = groupCall;
      if (!groupCall.isActive) {
        setSubscribed(false);
        setBarVisible(false);
      } else {
        invalidate();
      }
    });
  }

  // Drawing

  private String subtitle () {
    if (groupCall == null) {
      return Lang.getString(R.string.Tgx101VoiceChatBarLoading);
    }
    if (groupCall.participantCount <= 0) {
      return Lang.getString(R.string.Tgx101VoiceChatBarNobody);
    }
    return Lang.plural(R.string.xMembers, groupCall.participantCount);
  }

  private String title () {
    if (groupCall != null && !TextUtils.isEmpty(groupCall.title)) {
      return groupCall.title;
    }
    return Lang.getString(R.string.Tgx101VoiceChat);
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    int width = getMeasuredWidth(), height = Screen.dp(HEIGHT);
    c.drawRect(0, 0, width, height, Paints.fillingPaint(Theme.fillingColor()));
    int accent = Theme.getColor(ColorId.textNeutral);

    // the microphone in an accent circle
    float cx = Screen.dp(30f), cy = height / 2f;
    c.drawCircle(cx, cy, Screen.dp(17f), Paints.fillingPaint(me.vkryl.core.ColorUtils.alphaColor(.15f, accent)));
    if (icon != null) {
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(accent));
    }

    // the button on the right
    boolean joined = isJoinedHere(chatId, groupCallId);
    String button = Lang.getString(joined ? R.string.Tgx101VoiceChatBarReturn : R.string.Tgx101VoiceChatBarJoin);
    float buttonWidth = buttonPaint.measureText(button) + Screen.dp(28f);
    float buttonHeight = Screen.dp(30f);
    buttonRect.set(width - Screen.dp(12f) - buttonWidth, cy - buttonHeight / 2f, width - Screen.dp(12f), cy + buttonHeight / 2f);
    c.drawRoundRect(buttonRect, buttonHeight / 2f, buttonHeight / 2f, Paints.fillingPaint(accent));
    buttonPaint.setColor(0xffffffff);
    c.drawText(button, buttonRect.centerX() - (buttonWidth - Screen.dp(28f)) / 2f, cy + Screen.dp(5f), buttonPaint);

    // title and participants
    float textLeft = Screen.dp(58f);
    float textWidth = buttonRect.left - Screen.dp(10f) - textLeft;
    titlePaint.setColor(Theme.textAccentColor());
    subtitlePaint.setColor(Theme.textDecentColor());
    CharSequence t = TextUtils.ellipsize(title(), titlePaint, Math.max(0, textWidth), TextUtils.TruncateAt.END);
    CharSequence s = TextUtils.ellipsize(subtitle(), subtitlePaint, Math.max(0, textWidth), TextUtils.TruncateAt.END);
    c.drawText(t, 0, t.length(), textLeft, cy - Screen.dp(3f), titlePaint);
    c.drawText(s, 0, s.length(), textLeft, cy + Screen.dp(15f), subtitlePaint);

    // the line under the bar
    c.drawRect(0, height - Math.max(1, Screen.dp(.5f)), width, height, Paints.fillingPaint(Theme.separatorColor()));
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (!visible) return false;
    if (e.getAction() == MotionEvent.ACTION_UP) {
      callback.onVoiceChatBarClick(chatId, groupCallId);
      invalidate();
    }
    return true;
  }
}
