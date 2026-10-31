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

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * TGx101 (approved mockup «Фильтры и живые примеры» Е/Ж/З, user 2026-10-31): a small live example over a MagiX section
 * whose switches change what the screen looks like — drawn from the current settings, so a switch changes it at once,
 * before going to a chat. Channels: the mute bar or the bell, «Discuss», «Join»; message field: its style and buttons;
 * menus: the bottom menu and the number in the menu.
 */
public class Tgx101MagixPreview extends View {
  public static final int MODE_CHANNEL = 0, MODE_INPUT = 1, MODE_NAV = 2;

  private int mode;
  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();

  public Tgx101MagixPreview (Context context) {
    super(context);
  }

  public void setMode (int mode) {
    if (this.mode != mode) {
      this.mode = mode;
      invalidate();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Screen.dp(196f));
  }

  private void round (Canvas c, float l, float t, float r, float b, float radius, int color) {
    fill.setColor(color);
    rect.set(l, t, r, b);
    c.drawRoundRect(rect, Screen.dp(radius), Screen.dp(radius), fill);
  }

  private void circle (Canvas c, float cx, float cy, float radius, int color) {
    fill.setColor(color);
    c.drawCircle(cx, cy, radius, fill);
  }

  private static String fit (String text, TextPaint paint, float width) {
    return TextUtils.ellipsize(text, paint, Math.max(0, width), TextUtils.TruncateAt.END).toString();
  }

  @Override
  protected void onDraw (Canvas c) {
    int w = getWidth();
    float pad = Screen.dp(16f);
    // the phone's screen: a card with the chat's own colours
    float left = pad, top = Screen.dp(8f), right = w - pad, bottom = getHeight() - Screen.dp(30f);
    c.save();
    round(c, left, top, right, bottom, 16f, Theme.getColor(ColorId.chatBackground));
    TextPaint hint = Paints.getRegularTextPaint(12f, Theme.textDecentColor());
    String caption = Lang.getString(R.string.Tgx101PreviewHint);
    c.drawText(fit(caption, hint, w - pad * 2), (w - Math.min(hint.measureText(caption), w - pad * 2)) / 2f, getHeight() - Screen.dp(10f), hint);
    switch (mode) {
      case MODE_CHANNEL: drawChannel(c, left, top, right, bottom); break;
      case MODE_INPUT: drawInput(c, left, top, right, bottom); break;
      case MODE_NAV: drawNav(c, left, top, right, bottom); break;
    }
    c.restore();
  }

  private float header (Canvas c, float left, float top, float right, String title, String subtitle, int avatarColor, String letters) {
    float h = Screen.dp(46f);
    fill.setColor(Theme.getColor(ColorId.headerBackground));
    rect.set(left, top, right, top + h);
    c.save();
    c.clipRect(rect);
    round(c, left, top, right, top + h + Screen.dp(16f), 16f, Theme.getColor(ColorId.headerBackground));
    c.restore();
    float cx = left + Screen.dp(26f), cy = top + h / 2f;
    circle(c, cx, cy, Screen.dp(14f), avatarColor);
    TextPaint av = Paints.getMediumTextPaint(11f, 0xffffffff, false);
    c.drawText(letters, cx - av.measureText(letters) / 2f, cy + Screen.dp(4f), av);
    TextPaint name = Paints.getMediumTextPaint(14f, Theme.getColor(ColorId.headerText), false);
    TextPaint sub = Paints.getRegularTextPaint(11f, Theme.getColor(ColorId.headerText));
    sub.setAlpha(150);
    float x = cx + Screen.dp(22f);
    c.drawText(title, x, cy - Screen.dp(1f), name);
    if (subtitle != null) c.drawText(subtitle, x, cy + Screen.dp(13f), sub);
    return top + h;
  }

  private float bubble (Canvas c, float left, float y, float maxRight, String text, boolean out) {
    TextPaint p = Paints.getRegularTextPaint(13f, Theme.getColor(out ? ColorId.bubbleOut_text : ColorId.bubbleIn_text));
    float textW = Math.min(p.measureText(text), maxRight - left - Screen.dp(24f));
    float bw = textW + Screen.dp(24f), bh = Screen.dp(34f);
    float l = out ? maxRight - bw : left;
    round(c, l, y, l + bw, y + bh, 14f, Theme.getColor(out ? ColorId.bubbleOut_background : ColorId.bubbleIn_background));
    c.drawText(fit(text, p, textW), l + Screen.dp(12f), y + Screen.dp(22f), p);
    return y + bh;
  }

  private void drawChannel (Canvas c, float left, float top, float right, float bottom) {
    Settings s = Settings.instance();
    float y = header(c, left, top, right, Lang.getString(R.string.Tgx101FilterPreviewChannel), Lang.getString(R.string.Tgx101PreviewSubscribers), 0xff3d8bd1, "КН");
    if (!s.hideChannelSubscribeLink()) {
      TextPaint join = Paints.getMediumTextPaint(12f, Theme.getColor(ColorId.headerText), false);
      String j = Lang.getString(R.string.Tgx101PreviewJoin);
      c.drawText(j, right - Screen.dp(12f) - join.measureText(j), top + Screen.dp(28f), join);
    }
    boolean bar = s.showChannelMuteButton() && !s.tgx101ChannelMuteBell();
    boolean bell = s.showChannelMuteButton() && s.tgx101ChannelMuteBell();
    float barH = bar ? Screen.dp(36f) : 0;
    float postY = y + Screen.dp(10f);
    float postBottom = bubble(c, left + Screen.dp(10f), postY, right - Screen.dp(60f), Lang.getString(R.string.Tgx101FilterPreviewPost), false);
    if (s.showDiscussButton() || s.showCommentsButton()) {
      TextPaint d = Paints.getMediumTextPaint(12f, Theme.getColor(ColorId.textLink), false);
      c.drawText("💬 " + Lang.getString(R.string.Tgx101PreviewDiscuss), left + Screen.dp(22f), postBottom + Screen.dp(17f), d);
    }
    // the round buttons over the bottom: «down», and the bell next to it when the mute button folds into a bell
    float cy = bottom - barH - Screen.dp(22f);
    float cx = right - Screen.dp(24f);
    circle(c, cx, cy, Screen.dp(15f), Theme.getColor(ColorId.circleButtonChat));
    TextPaint icon = Paints.getMediumTextPaint(14f, Theme.getColor(ColorId.circleButtonChatIcon), false);
    c.drawText("↓", cx - icon.measureText("↓") / 2f, cy + Screen.dp(5f), icon);
    if (bell) {
      float bx = cx - Screen.dp(38f);
      circle(c, bx, cy, Screen.dp(15f), Theme.getColor(ColorId.circleButtonChat));
      TextPaint e = Paints.getRegularTextPaint(13f, 0xffffffff);
      c.drawText("🔔", bx - e.measureText("🔔") / 2f, cy + Screen.dp(5f), e);
    }
    if (bar) {
      c.save();
      rect.set(left, bottom - barH, right, bottom);
      c.clipRect(rect);
      round(c, left, bottom - barH - Screen.dp(16f), right, bottom, 16f, Theme.fillingColor());
      c.restore();
      TextPaint t = Paints.getMediumTextPaint(12f, Theme.getColor(ColorId.textNeutral), false);
      String m = Lang.getString(R.string.Tgx101PreviewMute);
      c.drawText(m, (left + right - t.measureText(m)) / 2f, bottom - barH / 2f + Screen.dp(4f), t);
    }
  }

  private void drawInput (Canvas c, float left, float top, float right, float bottom) {
    Settings s = Settings.instance();
    float y = header(c, left, top, right, Lang.getString(R.string.Tgx101PreviewMom), Lang.getString(R.string.Tgx101PreviewOnline), 0xffcc5049, "М");
    // one row: hers on the left, mine on the right — the field needs the room below
    float mid = (left + right) / 2f;
    bubble(c, left + Screen.dp(10f), y + Screen.dp(10f), mid - Screen.dp(6f), Lang.getString(R.string.Tgx101PreviewMomText), false);
    bubble(c, mid + Screen.dp(6f), y + Screen.dp(10f), right - Screen.dp(10f), Lang.getString(R.string.Tgx101PreviewMyText), true);
    int style = s.tgx101InputStyle(); // 0 floating, 1 flat, 2 well
    float fh = Screen.dp(38f);
    float fl, fr, ft, fb;
    if (style == 0) {
      fl = left + Screen.dp(8f); fr = right - Screen.dp(8f); fb = bottom - Screen.dp(8f); ft = fb - fh;
      round(c, fl, ft, fr, fb, 19f, Theme.fillingColor());
    } else {
      fl = left; fr = right; fb = bottom; ft = fb - fh - Screen.dp(4f);
      c.save();
      rect.set(fl, ft, fr, fb);
      c.clipRect(rect);
      round(c, fl, ft - Screen.dp(16f), fr, fb, 16f, Theme.fillingColor());
      c.restore();
      if (style == 2) {
        round(c, fl + Screen.dp(44f), ft + Screen.dp(6f), fr - Screen.dp(80f), fb - Screen.dp(6f), 14f, Theme.getColor(ColorId.background));
      }
    }
    float cy = (ft + fb) / 2f + Screen.dp(5f);
    TextPaint emoji = Paints.getRegularTextPaint(15f, Theme.getColor(ColorId.icon));
    TextPaint ph = Paints.getRegularTextPaint(14f, Theme.textDecentColor());
    float x = fl + Screen.dp(12f);
    if (!s.tgx101HideInputEmoji()) {
      c.drawText("😊", x, cy, emoji);
      x += Screen.dp(28f);
    }
    c.drawText(Lang.getString(R.string.Message), x, cy, ph);
    float rx = fr - Screen.dp(12f);
    String[] icons = s.tgx101HideInputCamera() ? new String[] {"🎤", "📎"} : new String[] {"🎤", "📷", "📎"};
    for (String i : icons) {
      rx -= emoji.measureText(i);
      c.drawText(i, rx, cy, emoji);
      rx -= Screen.dp(10f);
    }
    if (!s.tgx101HideInputCommands()) {
      TextPaint slash = Paints.getMediumTextPaint(15f, Theme.getColor(ColorId.icon), false);
      rx -= slash.measureText("/");
      c.drawText("/", rx, cy, slash);
    }
  }

  private void drawNav (Canvas c, float left, float top, float right, float bottom) {
    Settings s = Settings.instance();
    // the side menu's top on the left: the name and the number (or not)
    float menuR = left + (right - left) * .42f;
    c.save();
    rect.set(left, top, menuR, bottom);
    c.clipRect(rect);
    round(c, left, top, menuR + Screen.dp(16f), bottom, 16f, Theme.getColor(ColorId.headerBackground));
    c.restore();
    circle(c, left + Screen.dp(30f), top + Screen.dp(34f), Screen.dp(18f), 0xff40a920);
    TextPaint av = Paints.getMediumTextPaint(12f, 0xffffffff, false);
    c.drawText("Я", left + Screen.dp(30f) - av.measureText("Я") / 2f, top + Screen.dp(39f), av);
    TextPaint name = Paints.getMediumTextPaint(14f, Theme.getColor(ColorId.headerText), false);
    TextPaint phone = Paints.getRegularTextPaint(12f, Theme.getColor(ColorId.headerText));
    phone.setAlpha(170);
    c.drawText(fit(Lang.getString(R.string.Tgx101PreviewMe), name, menuR - left - Screen.dp(24f)), left + Screen.dp(12f), top + Screen.dp(76f), name);
    if (!s.needHidePhoneNumber()) {
      c.drawText(fit("+380 •• ••• •• 17", phone, menuR - left - Screen.dp(24f)), left + Screen.dp(12f), top + Screen.dp(94f), phone);
    }
    // a chat row on the right
    float x = menuR + Screen.dp(12f);
    float rowY = top + Screen.dp(30f);
    circle(c, x + Screen.dp(16f), rowY, Screen.dp(16f), 0xffcc5049);
    TextPaint av2 = Paints.getMediumTextPaint(11f, 0xffffffff, false);
    c.drawText("М", x + Screen.dp(16f) - av2.measureText("М") / 2f, rowY + Screen.dp(4f), av2);
    TextPaint t = Paints.getMediumTextPaint(14f, Theme.textAccentColor(), false);
    TextPaint m = Paints.getRegularTextPaint(13f, Theme.textDecentColor());
    float tx = x + Screen.dp(40f), tw = right - tx - Screen.dp(10f);
    c.drawText(fit(Lang.getString(R.string.Tgx101PreviewMom), t, tw), tx, rowY - Screen.dp(3f), t);
    c.drawText(fit(Lang.getString(R.string.Tgx101PreviewMomText), m, tw), tx, rowY + Screen.dp(14f), m);
    // the bottom menu as a capsule
    if (s.tgx101NavCapsule()) {
      float ch = Screen.dp(34f), cb = bottom - Screen.dp(10f);
      float cl = menuR + Screen.dp(10f), cr = right - Screen.dp(10f);
      round(c, cl, cb - ch, cr, cb, 17f, Theme.fillingColor());
      String[] tabs = {"💬", "📞", "👤", "⚙"};
      TextPaint e = Paints.getRegularTextPaint(13f, Theme.getColor(ColorId.icon));
      float step = (cr - cl) / tabs.length;
      for (int i = 0; i < tabs.length; i++) {
        float cx = cl + step * (i + .5f);
        c.drawText(tabs[i], cx - e.measureText(tabs[i]) / 2f, cb - ch / 2f + Screen.dp(5f), e);
      }
    }
  }
}
