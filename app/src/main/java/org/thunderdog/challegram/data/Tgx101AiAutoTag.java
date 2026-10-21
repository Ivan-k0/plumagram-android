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
package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.Tgx101SavedKit;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TGx101 (user 2026-10-21, AI in the storage, 5): a new record saved from this phone gets a tag from the AI — one of the
 * user's tags, or a new short one. Off by default (MagiX → Saved): every batch is a request to Telegram's AI. Records
 * saved within a few seconds go in one request.
 */
public final class Tgx101AiAutoTag {
  private Tgx101AiAutoTag () { }

  private static final long WAIT_MS = 5000;
  private static final List<TdApi.Message> queue = new ArrayList<>();
  private static Tdlib queueTdlib;

  private static final Runnable flush = Tgx101AiAutoTag::flush;

  public static void queue (Tdlib tdlib, TdApi.Message message) {
    if (!Settings.instance().tgx101AiAutoTags() || !Tgx101SavedStore.get(tdlib).storageOn) return;
    UI.post(() -> {
      if (queueTdlib != null && queueTdlib != tdlib) queue.clear();
      queueTdlib = tdlib;
      queue.add(message);
      UI.cancel(flush);
      UI.post(flush, WAIT_MS);
    });
  }

  private static String clean (String s) {
    s = s.replace('\n', ' ').replace('[', '(').replace(']', ')').replace('*', ' ').replace('_', ' ').replace('`', '\'').replaceAll("\\s+", " ").trim();
    return s.length() > 160 ? s.substring(0, 160) + "…" : s;
  }

  private static final Pattern NUMBER = Pattern.compile("(?<![\\d\\p{L}])(\\d{1,3})\\s*[:.)]\\s*");

  private static void flush () {
    if (queue.isEmpty() || queueTdlib == null) return;
    Tdlib tdlib = queueTdlib;
    List<TdApi.Message> batch = new ArrayList<>(queue.subList(0, Math.min(10, queue.size())));
    queue.removeAll(batch);
    if (!queue.isEmpty()) UI.post(flush, WAIT_MS);
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    StringBuilder text = new StringBuilder("Мои метки: ");
    List<String> tags = store.knownTags();
    text.append(tags.isEmpty() ? "(пока нет)" : android.text.TextUtils.join(", ", tags)).append("\n\nЗаписи:\n");
    for (int i = 0; i < batch.size(); i++) {
      TdApi.Message m = batch.get(i);
      TdApi.FormattedText t = Tgx101SavedKit.text(m);
      String body = t != null && t.text != null && !t.text.trim().isEmpty() ? t.text : Tgx101SavedKit.title(m);
      text.append(i + 1).append(") ").append(Tgx101SavedKit.kindLabel(Tgx101SavedKit.kindOf(m))).append(". ").append(clean(body)).append('\n');
    }
    String prompt = "Дай каждой записи одну метку: из моих, если подходит, иначе новую — одно короткое слово с маленькой буквы, без #. " +
      "Если тема неясна — «номер: -». Ответь только строками «номер: метка».";
    TdApi.InputRichMessage message = new TdApi.InputRichMessage(new TdApi.RichMessageSourceMarkdown(text.toString(), new TdApi.InputRichMessageMedia[0]), false, false);
    tdlib.client().send(new TdApi.ComposeRichMessageWithAi(message, "", "", prompt, false), result -> UI.post(() -> {
      String answer = null;
      if (result.getConstructor() == TdApi.RichMessage.CONSTRUCTOR) {
        TdApi.FormattedText f = Tgx101RichMessage.toText((TdApi.RichMessage) result);
        answer = f != null ? f.text : null;
      } else if (result.getConstructor() == TdApi.FormattedText.CONSTRUCTOR) {
        answer = ((TdApi.FormattedText) result).text;
      }
      if (answer == null) {
        org.thunderdog.challegram.Tgx101Diag.mark("ai tags: failed " + result);
        return;
      }
      Matcher matcher = NUMBER.matcher(answer);
      List<int[]> starts = new ArrayList<>();
      while (matcher.find()) starts.add(new int[] {matcher.start(), matcher.end(), Integer.parseInt(matcher.group(1))});
      int tagged = 0;
      for (int i = 0; i < starts.size(); i++) {
        int end = i + 1 < starts.size() ? starts.get(i + 1)[0] : answer.length();
        String tag = answer.substring(starts.get(i)[1], end).trim();
        int cut = tag.length();
        for (char c : new char[] {'\n', ',', ';', ' ', '.'}) { int at = tag.indexOf(c); if (at >= 0 && at < cut) cut = at; }
        tag = tag.substring(0, cut).replace("#", "").replaceAll("[«»\"'*_`]", "").trim();
        int n = starts.get(i)[2] - 1;
        if (tag.isEmpty() || tag.equals("-") || tag.equals("—") || n < 0 || n >= batch.size() || tag.length() > 24) continue;
        store.addLocalTag(tag.toLowerCase(), new long[] {batch.get(n).id});
        tagged++;
      }
      org.thunderdog.challegram.Tgx101Diag.mark("ai tags: " + tagged + " of " + batch.size() + " records tagged");
    }));
  }
}
