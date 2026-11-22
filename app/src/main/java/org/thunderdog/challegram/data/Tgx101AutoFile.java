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
package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.Tgx101SavedKit;
import org.thunderdog.challegram.ui.Tgx101StorageTools;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * (user 2026-11-14, the mockup «ИИ: Хранилище и музыка», Storage variant Б «само») A new record saved from this phone that no one put in
 * a folder goes to one by itself: first by the phone's own rules, then — when the AI is on — by the AI, into the folders that exist
 * (it never makes new ones by itself). Each move is kept in a list, «Что разложено само», with «Вернуть». Off by default.
 */
public final class Tgx101AutoFile {
  private Tgx101AutoFile () { }

  private static final long WAIT_MS = 15000; // the sheet of «Saved» may still be choosing a folder
  private static final int BATCH = 10, LOG_MAX = 40, LINE_MAX = 110;
  private static final List<TdApi.Message> queue = new ArrayList<>();
  private static Tdlib queueTdlib;
  private static final Runnable flush = Tgx101AutoFile::flush;

  public static void queue (Tdlib tdlib, TdApi.Message message) {
    if (!Settings.instance().tgx101StorageAutoFolders() || !Tgx101SavedStore.get(tdlib).storageOn) return;
    UI.post(() -> {
      if (queueTdlib != null && queueTdlib != tdlib) queue.clear();
      queueTdlib = tdlib;
      queue.add(message);
      UI.cancel(flush);
      UI.post(flush, WAIT_MS);
    });
  }

  private static void flush () {
    if (queue.isEmpty() || queueTdlib == null) return;
    final Tdlib tdlib = queueTdlib;
    final Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    final List<TdApi.Message> batch = new ArrayList<>();
    for (TdApi.Message m : new ArrayList<>(queue)) {
      queue.remove(m);
      if (m.content != null && !store.isFiled(m.id) && !Tgx101SavedCloud.isCloudFile(m)) batch.add(m);
      if (batch.size() >= BATCH) break;
    }
    if (!queue.isEmpty()) UI.post(flush, WAIT_MS);
    if (batch.isEmpty() || store.folders().isEmpty()) return;
    Tgx101StorageTools.propose(tdlib, batch, proposal -> {
      List<String[]> moved = new ArrayList<>();
      boolean[] placed = new boolean[batch.size()];
      for (Map.Entry<String, List<Integer>> e : proposal.entrySet()) {
        for (int index : e.getValue()) {
          placed[index] = true;
          put(tdlib, store, batch.get(index), e.getKey(), false, moved);
        }
      }
      List<TdApi.Message> left = new ArrayList<>();
      for (int i = 0; i < batch.size(); i++) if (!placed[i]) left.add(batch.get(i));
      if (left.isEmpty() || !org.thunderdog.challegram.ui.Tgx101StorageTools.AI || !Settings.instance().tgx101Ai()) {
        announce(moved);
        return;
      }
      ask(tdlib, store, left, moved);
    });
  }

  private static String clean (String s) {
    s = s.replace('\n', ' ').replace('[', '(').replace(']', ')').replace('*', ' ').replace('_', ' ').replace('`', '\'').replaceAll("\\s+", " ").trim();
    return s.length() > LINE_MAX ? s.substring(0, LINE_MAX) + "…" : s;
  }

  private static final Pattern NUMBER = Pattern.compile("(?<![\\d\\p{L}])(\\d{1,3})\\s*[:.)]\\s*");

  private static void ask (Tdlib tdlib, Tgx101SavedStore store, List<TdApi.Message> left, List<String[]> moved) {
    StringBuilder text = new StringBuilder("Папки: ");
    List<String> names = new ArrayList<>();
    for (Tgx101SavedStore.Folder f : store.folders()) names.add(clean(f.name));
    text.append(android.text.TextUtils.join(", ", names)).append("\n\nЗаписи:\n");
    for (int i = 0; i < left.size(); i++) {
      TdApi.Message m = left.get(i);
      TdApi.FormattedText t = Tgx101SavedKit.text(m);
      String body = t != null && t.text != null && !t.text.trim().isEmpty() ? t.text : Tgx101SavedKit.title(m);
      text.append(i + 1).append(") ").append(Tgx101SavedKit.kindLabel(Tgx101SavedKit.kindOf(m))).append(". ").append(clean(body));
      String source = Tgx101SavedKit.source(tdlib, m);
      if (m.forwardInfo != null && !source.isEmpty()) text.append(". Из: ").append(clean(source));
      text.append('\n');
    }
    String prompt = "Положи каждую запись в одну из моих папок — только из списка, новых не придумывай. Если ни одна не подходит явно — «номер: -». " +
      "Ответь только строками «номер: папка».";
    org.thunderdog.challegram.Tgx101Diag.mark("auto folders: asking the AI of " + left.size() + " records");
    TdApi.InputRichMessage message = new TdApi.InputRichMessage(new TdApi.RichMessageSourceMarkdown(text.toString(), new TdApi.InputRichMessageMedia[0]), false, false);
    Tgx101OwnAi.send(tdlib, new TdApi.ComposeRichMessageWithAi(message, "", "", prompt, false), result -> UI.post(() -> {
      String answer = null;
      if (result.getConstructor() == TdApi.RichMessage.CONSTRUCTOR) {
        TdApi.FormattedText f = Tgx101RichMessage.toText((TdApi.RichMessage) result);
        answer = f != null ? f.text : null;
      } else if (result.getConstructor() == TdApi.FormattedText.CONSTRUCTOR) {
        answer = ((TdApi.FormattedText) result).text;
      }
      if (answer == null) {
        org.thunderdog.challegram.Tgx101Diag.mark("auto folders: the AI failed — " + (result instanceof TdApi.Error ? ((TdApi.Error) result).message : "?"));
        announce(moved);
        return;
      }
      Map<String, String> existing = new LinkedHashMap<>();
      for (Tgx101SavedStore.Folder f : store.folders()) existing.put(f.name.trim().toLowerCase(Locale.ROOT), f.name);
      Matcher matcher = NUMBER.matcher(answer);
      List<int[]> starts = new ArrayList<>();
      while (matcher.find()) starts.add(new int[] {matcher.start(), matcher.end(), Integer.parseInt(matcher.group(1))});
      for (int i = 0; i < starts.size(); i++) {
        int end = i + 1 < starts.size() ? starts.get(i + 1)[0] : answer.length();
        String name = answer.substring(starts.get(i)[1], end);
        int newline = name.indexOf('\n');
        if (newline >= 0) name = name.substring(0, newline);
        name = name.replaceAll("[«»\"'*_`.;,]", "").trim();
        String known = existing.get(name.toLowerCase(Locale.ROOT));
        int n = starts.get(i)[2] - 1;
        if (known == null || n < 0 || n >= left.size()) continue;
        put(tdlib, store, left.get(n), known, true, moved);
      }
      announce(moved);
    }));
  }

  private static void put (Tdlib tdlib, Tgx101SavedStore store, TdApi.Message m, String folderName, boolean byAi, List<String[]> moved) {
    if (store.isFiled(m.id)) return; // put somewhere by hand meanwhile
    for (Tgx101SavedStore.Folder f : store.folders()) {
      if (f.name.equals(folderName)) {
        store.putInFolder(f.id, new long[] {m.id});
        String title = Tgx101SavedKit.title(m);
        moved.add(new String[] {String.valueOf(m.id), String.valueOf(f.id), title, f.name});
        log(tdlib, m.id, f.id, title, byAi);
        return;
      }
    }
  }

  private static void announce (List<String[]> moved) {
    org.thunderdog.challegram.Tgx101Diag.mark("auto folders: " + moved.size() + " records put");
    if (moved.isEmpty()) return;
    if (moved.size() == 1) {
      String title = moved.get(0)[2];
      if (title.length() > 30) title = title.substring(0, 29).trim() + "…";
      UI.showToast(Lang.getString(R.string.Tgx101AutoFileOne, title, moved.get(0)[3]), android.widget.Toast.LENGTH_SHORT);
    } else {
      UI.showToast(Lang.getString(R.string.Tgx101AutoFileMany, moved.size()), android.widget.Toast.LENGTH_SHORT);
    }
  }

  // The list of what moved by itself: «id \t folder \t time \t by AI \t title» lines, the latest first

  public static final class Entry {
    public final long messageId, folderId, time;
    public final boolean byAi;
    public final String title;

    Entry (long messageId, long folderId, long time, boolean byAi, String title) {
      this.messageId = messageId;
      this.folderId = folderId;
      this.time = time;
      this.byAi = byAi;
      this.title = title;
    }
  }

  private static synchronized void log (Tdlib tdlib, long messageId, long folderId, String title, boolean byAi) {
    List<String> lines = new ArrayList<>();
    lines.add(messageId + "\t" + folderId + "\t" + System.currentTimeMillis() + "\t" + (byAi ? 1 : 0) + "\t" + title.replace('\t', ' ').replace('\n', ' '));
    String old = Settings.instance().tgx101StorageAutoLog(tdlib.id());
    if (!old.isEmpty()) java.util.Collections.addAll(lines, old.split("\n"));
    if (lines.size() > LOG_MAX) lines = lines.subList(0, LOG_MAX);
    Settings.instance().setTgx101StorageAutoLog(tdlib.id(), android.text.TextUtils.join("\n", lines));
  }

  public static synchronized List<Entry> entries (Tdlib tdlib) {
    List<Entry> list = new ArrayList<>();
    String old = Settings.instance().tgx101StorageAutoLog(tdlib.id());
    if (old.isEmpty()) return list;
    for (String line : old.split("\n")) {
      String[] p = line.split("\t", 5);
      if (p.length < 5) continue;
      try {
        list.add(new Entry(Long.parseLong(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2]), "1".equals(p[3]), p[4]));
      } catch (NumberFormatException ignored) { }
    }
    return list;
  }

  /** «Вернуть»: out of the folder, and out of the list */
  public static synchronized void undo (Tdlib tdlib, Entry entry) {
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    Tgx101SavedStore.Folder folder = store.folderOf(entry.messageId);
    if (folder != null && folder.id == entry.folderId) store.removeFromFolders(new long[] {entry.messageId});
    List<String> lines = new ArrayList<>();
    for (String line : Settings.instance().tgx101StorageAutoLog(tdlib.id()).split("\n")) {
      if (!line.startsWith(entry.messageId + "\t")) lines.add(line);
    }
    Settings.instance().setTgx101StorageAutoLog(tdlib.id(), android.text.TextUtils.join("\n", lines));
    org.thunderdog.challegram.Tgx101Diag.mark("auto folders: one put back");
  }
}
