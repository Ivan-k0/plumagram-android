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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * TGx101 (4PDA 2026-10-31 #358, mockup «Следующий канал»): the card of the next unread channel rising from the bottom
 * while the end of a channel is pulled up — its photo with a ring that fills as the finger goes, its name and unread
 * count; a full ring turns it green, «release to open». One card, no blur: next to nothing for the GPU.
 */
public class Tgx101NextChannelCard extends View {
  private final AvatarReceiver avatar;
  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private @Nullable TdApi.Chat chat;
  private float progress;
  private boolean ready;

  public Tgx101NextChannelCard (Context context) {
    super(context);
    avatar = new AvatarReceiver(this);
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setStrokeCap(Paint.Cap.ROUND);
    ringPaint.setStrokeWidth(Screen.dp(2.5f));
  }

  public static int cardWidth () { return Screen.dp(252f); }
  public static int cardHeight () { return Screen.dp(68f); }

  public void setChat (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    if (this.chat != chat) {
      this.chat = chat;
      if (chat != null) avatar.requestChat(tdlib, chat.id, AvatarReceiver.Options.NONE);
      else avatar.clear();
      invalidate();
    }
  }

  public void setProgress (float progress, boolean ready) {
    if (this.progress != progress || this.ready != ready) {
      this.progress = progress;
      this.ready = ready;
      invalidate();
    }
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    avatar.attach();
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    avatar.detach();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(cardWidth(), cardHeight());
  }

  @Override
  protected void onDraw (Canvas c) {
    int w = getWidth(), h = getHeight();
    rect.set(0, 0, w, h);
    fillPaint.setColor(ready && chat != null ? 0xff1d3a2c : 0xf01f2b38);
    c.drawRoundRect(rect, Screen.dp(18f), Screen.dp(18f), fillPaint);

    float pad = Screen.dp(12f);
    float ringR = Screen.dp(21f);
    float cx = pad + ringR, cy = h / 2f;
    // the photo grows from 80 % to its full size as the ring fills
    float photoR = Screen.dp(17f) * (.8f + .2f * Math.min(1f, progress));
    avatar.setBounds((int) (cx - photoR), (int) (cy - photoR), (int) (cx + photoR), (int) (cy + photoR));
    if (chat != null) {
      avatar.draw(c);
    } else {
      fillPaint.setColor(0xff2a3644);
      c.drawCircle(cx, cy, photoR, fillPaint);
    }
    rect.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
    ringPaint.setColor(0xff2a3644);
    c.drawArc(rect, 0, 360, false, ringPaint);
    if (chat != null && progress > 0f) {
      ringPaint.setColor(ready ? 0xff5ccb8a : Theme.getColor(ColorId.checkActive));
      c.drawArc(rect, -90, 360f * Math.min(1f, progress), false, ringPaint);
    }

    float textX = cx + ringR + Screen.dp(12f), maxW = w - textX - pad;
    TextPaint title = Paints.getMediumTextPaint(14f, 0xffffffff, false);
    TextPaint sub = Paints.getRegularTextPaint(12f, ready && chat != null ? 0xff5ccb8a : 0xff8d9aab);
    String name = chat != null ? chat.title : Lang.getString(R.string.Tgx101NextChannelNone);
    String line = chat == null ? "" : ready ? Lang.getString(R.string.Tgx101NextChannelReleaseShort) : Lang.plural(R.string.xNewMessages, chat.unreadCount);
    if (line.isEmpty()) {
      c.drawText(TextUtils.ellipsize(name, title, maxW, TextUtils.TruncateAt.END).toString(), textX, cy + Screen.dp(5f), title);
    } else {
      c.drawText(TextUtils.ellipsize(name, title, maxW, TextUtils.TruncateAt.END).toString(), textX, cy - Screen.dp(3f), title);
      c.drawText(TextUtils.ellipsize(line, sub, maxW, TextUtils.TruncateAt.END).toString(), textX, cy + Screen.dp(14f), sub);
    }
  }
}
