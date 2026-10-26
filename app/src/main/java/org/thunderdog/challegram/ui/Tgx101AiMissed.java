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

import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tgx.td.MessageId;

/**
 * TGx101 (user 2026-10-21 «1 2 и 5 делай», mockup «ИИ/Кратко-и-Пропущенное» 2): «✨ What I missed» — with many unread
 * messages in a chat, the AI tells them in 5–7 points; each point leads to its message, «Read» marks them all read.
 */
public final class Tgx101AiMissed {
  private Tgx101AiMissed () { }

  public static final int MIN_UNREAD = 15;
  private static final int MAX_MESSAGES = 80, LINE_MAX = 160, TEXT_MAX = 7000;

  public static boolean canShow (Tdlib tdlib, TdApi.Chat chat, int unreadCount) {
    return Settings.instance().tgx101AiMissed() && chat != null && unreadCount >= MIN_UNREAD
      && chat.type.getConstructor() != TdApi.ChatTypeSecret.CONSTRUCTOR && !tdlib.isSelfChat(chat.id);
  }

  private static String clean (String s) {
    s = s.replace('\n', ' ').replace('[', '(').replace(']', ')').replace('*', ' ').replace('_', ' ').replace('`', '\'').replaceAll("\\s+", " ").trim();
    return s.length() > LINE_MAX ? s.substring(0, LINE_MAX) + "…" : s;
  }

  private static String time (int date) {
    return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(date * 1000L));
  }

  public static void open (MessagesController c, TdApi.Chat chat) {
    Tdlib tdlib = c.tdlib();
    final long lastRead = chat.lastReadInboxMessageId;
    final String title = chat.title;
    final LinearLayout[] holder = new LinearLayout[1];
    final Tgx101Sheet.Handle[] sheet = new Tgx101Sheet.Handle[1];
    sheet[0] = Tgx101Sheet.show(c, null, body -> {
      holder[0] = body;
      body.addView(Tgx101Sheet.title(c.context(), "✨ " + Lang.getString(R.string.Tgx101AiMissed)));
      body.addView(note(c, Lang.getString(R.string.Tgx101AiMissedWorking)));
    });
    org.thunderdog.challegram.Tgx101Diag.mark("ai missed: chat " + chat.id + ", " + chat.unreadCount + " unread");
    final int want = Math.min(MAX_MESSAGES, Math.max(MIN_UNREAD, chat.unreadCount));
    // (test 2026-10-21: 1 of 878 found) the first page is what is cached — pages are asked until the last read one
    loadUnread(tdlib, chat.id, 0, lastRead, want, new ArrayList<>(), 0, unread -> {
      Collections.sort(unread, (a, b) -> Long.compare(a.id, b.id));
      if (unread.isEmpty()) {
        UI.post(() -> fill(c, holder[0], sheet[0], chat, null, unread, Lang.getString(R.string.Tgx101AiMissedNothing)));
        return;
      }
      StringBuilder text = new StringBuilder();
      text.append("Чат: ").append(clean(title)).append("\n\n");
      for (int i = 0; i < unread.size() && text.length() < TEXT_MAX; i++) {
        TdApi.Message m = unread.get(i);
        text.append(i + 1).append(") ").append(time(m.date)).append(' ');
        if (!m.isChannelPost) text.append(clean(tdlib.senderName(m, false, true))).append(": ");
        String t = Tgx101SavedKit.title(m);
        TdApi.FormattedText ft = Tgx101SavedKit.text(m);
        String body = ft != null && ft.text != null && !ft.text.trim().isEmpty() ? ft.text : t;
        text.append(clean(body)).append('\n');
      }
      String prompt = "Это сообщения, которые я пропустил. Перескажи главное 5–7 пунктами по-русски, коротко, без вступления. " +
        "Каждый пункт — одна строка вида «номер: суть», где номер — сообщение, к которому пункт относится. Важное и то, что просят меня, — первым.";
      TdApi.InputRichMessage message = new TdApi.InputRichMessage(new TdApi.RichMessageSourceMarkdown(text.toString(), new TdApi.InputRichMessageMedia[0]), false, false);
      tdlib.client().send(new TdApi.ComposeRichMessageWithAi(message, "", "", prompt, false), result -> UI.post(() -> {
        String answer = null, failure = null;
        switch (result.getConstructor()) {
          case TdApi.RichMessage.CONSTRUCTOR: {
            TdApi.FormattedText f = org.thunderdog.challegram.data.Tgx101RichMessage.toText((TdApi.RichMessage) result);
            answer = f != null ? f.text : "";
            break;
          }
          case TdApi.FormattedText.CONSTRUCTOR:
            answer = ((TdApi.FormattedText) result).text;
            break;
          case TdApi.Error.CONSTRUCTOR: {
            String e = ((TdApi.Error) result).message;
            // (4PDA / group 2026-10-26, Xiaomi on Android 16: «#400: RICH_MESSAGE_UNSUPPORTED» shown raw) — Telegram doesn't
            // offer this AI to that account yet: say so plainly
            failure = e != null && e.contains("PREMIUM") ? Lang.getString(R.string.Tgx101AiPremium)
              : e != null && e.contains("RICH_MESSAGE_UNSUPPORTED") ? Lang.getString(R.string.Tgx101AiUnavailable)
              : Lang.getString(R.string.Tgx101AiFailed, TD.toErrorString(result));
            break;
          }
        }
        org.thunderdog.challegram.Tgx101Diag.mark("ai missed: " + (answer != null ? answer.length() + " chars" : "failed " + failure));
        fill(c, holder[0], sheet[0], chat, answer, unread, failure);
      }));
    });
  }

  private static void loadUnread (Tdlib tdlib, long chatId, long from, long lastRead, int want, List<TdApi.Message> out, int emptyTries, me.vkryl.core.lambda.RunnableData<List<TdApi.Message>> after) {
    tdlib.send(new TdApi.GetChatHistory(chatId, from, 0, 100, false), (history, error) -> {
      if (history == null || history.messages.length == 0) {
        if (history != null && emptyTries < 2) {
          loadUnread(tdlib, chatId, from, lastRead, want, out, emptyTries + 1, after); // the server is still loading it
        } else {
          after.runWithData(out);
        }
        return;
      }
      long last = from;
      boolean reachedRead = false;
      for (TdApi.Message m : history.messages) {
        if (m == null || m.id == from) continue;
        last = m.id;
        if (m.id <= lastRead) { reachedRead = true; break; }
        if (!m.isOutgoing) out.add(m);
      }
      if (reachedRead || out.size() >= want || last == from) {
        after.runWithData(out.size() > want ? new ArrayList<>(out.subList(0, want)) : out);
      } else {
        loadUnread(tdlib, chatId, last, lastRead, want, out, 0, after);
      }
    });
  }

  private static final Pattern NUMBER = Pattern.compile("(?<![\\d\\p{L}])(\\d{1,3})\\s*[:.)]\\s*");

  private static void fill (MessagesController c, LinearLayout body, Tgx101Sheet.Handle sheet, TdApi.Chat chat, String answer, List<TdApi.Message> unread, String failure) {
    if (body == null || c.isDestroyed()) return;
    while (body.getChildCount() > 1) body.removeViewAt(1);
    if (failure != null || answer == null) {
      body.addView(note(c, failure != null ? failure : Lang.getString(R.string.Tgx101AiMissedNothing)));
      return;
    }
    TdApi.Message first = unread.get(0), last = unread.get(unread.size() - 1);
    body.addView(note(c, Lang.getString(R.string.Tgx101AiMissedCount, unread.size()) + " · " + time(first.date) + " – " + time(last.date)));
    // the points: «number: point» — one per line or all in one paragraph
    Matcher m = NUMBER.matcher(answer);
    List<int[]> starts = new ArrayList<>();
    while (m.find()) starts.add(new int[] {m.start(), m.end(), Integer.parseInt(m.group(1))});
    int shown = 0;
    for (int i = 0; i < starts.size() && shown < 8; i++) {
      int end = i + 1 < starts.size() ? starts.get(i + 1)[0] : answer.length();
      String point = answer.substring(starts.get(i)[1], end).replaceAll("\\s+", " ").trim();
      if (point.isEmpty()) continue;
      int n = starts.get(i)[2] - 1;
      TdApi.Message target = n >= 0 && n < unread.size() ? unread.get(n) : null;
      body.addView(point(c, ++shown, point, target, sheet, chat.id));
    }
    if (shown == 0) body.addView(note(c, answer.trim()));
    // buttons
    LinearLayout buttons = new LinearLayout(c.context());
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    buttons.setPadding(Screen.dp(16f), Screen.dp(10f), Screen.dp(16f), Screen.dp(4f));
    buttons.addView(button(c, Lang.getString(R.string.Tgx101AiClose), false, v -> sheet.dismiss()), new LinearLayout.LayoutParams(0, Screen.dp(44f), 1f));
    View gap = new View(c.context());
    buttons.addView(gap, new LinearLayout.LayoutParams(Screen.dp(10f), 1));
    buttons.addView(button(c, Lang.getString(R.string.Tgx101AiMissedRead), true, v -> {
      sheet.dismiss();
      c.tdlib().client().send(new TdApi.ViewMessages(chat.id, new long[] {last.id}, new TdApi.MessageSourceChatHistory(), true), r -> { });
      c.highlightMessage(new MessageId(chat.id, last.id));
    }), new LinearLayout.LayoutParams(0, Screen.dp(44f), 1f));
    body.addView(buttons);
  }

  private static TextView note (MessagesController c, CharSequence text) {
    TextView v = new TextView(c.context());
    v.setText(text);
    v.setTextColor(Theme.textDecentColor());
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    v.setPadding(Screen.dp(20f), 0, Screen.dp(20f), Screen.dp(10f));
    return v;
  }

  private static View point (MessagesController c, int n, String text, TdApi.Message target, Tgx101Sheet.Handle sheet, long chatId) {
    LinearLayout row = new LinearLayout(c.context());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(Screen.dp(20f), Screen.dp(9f), Screen.dp(20f), Screen.dp(9f));
    Views.setClickable(row);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(row);
    TextView num = new TextView(c.context());
    num.setText(String.valueOf(n));
    num.setTextColor(Theme.getColor(org.thunderdog.challegram.theme.ColorId.textLink));
    num.setTypeface(Fonts.getRobotoMedium());
    num.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    row.addView(num, new LinearLayout.LayoutParams(Screen.dp(22f), ViewGroup.LayoutParams.WRAP_CONTENT));
    TextView label = new org.thunderdog.challegram.widget.EmojiTextView(c.context());
    label.setText(text);
    label.setTextColor(Theme.textAccentColor());
    label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    if (target != null) {
      TextView when = new TextView(c.context());
      when.setText(time(target.date) + " ›");
      when.setTextColor(Theme.textDecentColor());
      when.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
      when.setPadding(Screen.dp(8f), Screen.dp(2f), 0, 0);
      row.addView(when);
      row.setOnClickListener(v -> {
        sheet.dismiss();
        c.highlightMessage(new MessageId(chatId, target.id));
      });
    }
    return row;
  }

  private static TextView button (MessagesController c, String text, boolean primary, View.OnClickListener click) {
    TextView b = new TextView(c.context());
    b.setText(text);
    b.setGravity(Gravity.CENTER);
    b.setTypeface(Fonts.getRobotoMedium());
    b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    int accent = Theme.getColor(org.thunderdog.challegram.theme.ColorId.textLink);
    b.setTextColor(primary ? 0xffffffff : accent);
    GradientDrawable d = new GradientDrawable();
    d.setCornerRadius(Screen.dp(12f));
    d.setColor(primary ? accent : me.vkryl.core.ColorUtils.alphaColor(.12f, accent));
    b.setBackground(d);
    Views.setClickable(b);
    b.setOnClickListener(click);
    return b;
  }
}
