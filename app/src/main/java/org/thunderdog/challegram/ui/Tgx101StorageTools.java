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
package org.thunderdog.challegram.ui;

import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.vkryl.core.lambda.RunnableData;

/**
 * (user 2026-11-14, the mockup «ИИ: Хранилище и музыка», Storage variant А) What the Storage can do on the phone alone: the same file
 * or link saved twice, the week in a few numbers, and the sorting by the phone's own rules for when there's no AI. Nothing is deleted
 * without asking; the rules only propose, the same screen of ticks as the AI's.
 */
public final class Tgx101StorageTools {
  private Tgx101StorageTools () { }

  private static final int SCAN = 3000, SAMPLES = 30, GROUPS_SHOWN = 30;
  private static final long WEEK = 7L * 24 * 60 * 60, YEAR = 365L * 24 * 60 * 60;

  private static TextView hint (ViewController<?> c, CharSequence text) {
    TextView v = Tgx101SavedHomeController.text(c.context(), 13.5f, Theme.textDecentColor(), false);
    v.setText(text);
    v.setPadding(Screen.dp(20f), Screen.dp(2f), Screen.dp(20f), Screen.dp(8f));
    return v;
  }

  private static void loading (ViewController<?> c, LinearLayout body, int title) {
    body.removeAllViews();
    body.addView(Tgx101Sheet.title(c.context(), Lang.getString(title)));
    body.addView(Tgx101Sheet.row(c.context(), 0, 0, Lang.getString(R.string.Tgx101StorageScanning), null, false, null));
  }

  // The same thing twice

  /** The same file (by Telegram's id of it) or the same link: one key, null — nothing to compare by */
  static @Nullable String sameKey (TdApi.Message m) {
    TdApi.File file = Tgx101SavedKit.mainFile(m);
    if (file != null && file.remote != null && file.remote.uniqueId != null && !file.remote.uniqueId.isEmpty()) {
      return "f" + file.remote.uniqueId;
    }
    if (m.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR) {
      String url = Tgx101SavedKit.url(m);
      TdApi.FormattedText text = Tgx101SavedKit.text(m);
      // a link alone, not a note that has one in it
      if (url != null && text != null && text.text.trim().length() <= url.length() + 2) {
        return "u" + url.trim().replaceAll("/+$", "").replaceFirst("^https?://(www\\.)?", "").toLowerCase(Locale.ROOT);
      }
    }
    return null;
  }

  public static void showDuplicates (ViewController<?> c) {
    final Tgx101Sheet.Handle[] handle = new Tgx101Sheet.Handle[1];
    handle[0] = Tgx101Sheet.show(c, null, body -> loading(c, body, R.string.Tgx101StorageDuplicates));
    Tgx101SavedKit.history(c.tdlib(), SCAN, messages -> {
      if (c.isDestroyed()) return;
      LinkedHashMap<String, List<TdApi.Message>> groups = new LinkedHashMap<>();
      for (TdApi.Message m : messages) {
        if (m.content == null) continue;
        String key = sameKey(m);
        if (key == null) continue;
        List<TdApi.Message> list = groups.get(key);
        if (list == null) groups.put(key, list = new ArrayList<>());
        list.add(m);
      }
      List<List<TdApi.Message>> twice = new ArrayList<>();
      for (List<TdApi.Message> list : groups.values()) if (list.size() > 1) twice.add(list);
      org.thunderdog.challegram.Tgx101Diag.mark("storage: duplicates, " + twice.size() + " groups of " + messages.size() + " records");
      fillDuplicates(c, handle[0], twice, messages.size());
    });
  }

  /** Of each group the one that stays: in a folder if one is, else the first saved */
  private static TdApi.Message keeper (Tgx101SavedStore store, List<TdApi.Message> group) {
    TdApi.Message keep = group.get(group.size() - 1); // the history goes from the latest: the last one is the first saved
    for (TdApi.Message m : group) {
      if (store.isFiled(m.id)) return m;
    }
    return keep;
  }

  private static void fillDuplicates (ViewController<?> c, Tgx101Sheet.Handle handle, List<List<TdApi.Message>> groups, int scanned) {
    LinearLayout body = handle.body;
    body.removeAllViews();
    body.addView(Tgx101Sheet.title(c.context(), Lang.getString(R.string.Tgx101StorageDuplicates)));
    if (groups.isEmpty()) {
      body.addView(hint(c, Lang.getString(R.string.Tgx101StorageDuplicatesNone, scanned)));
      return;
    }
    final Tgx101SavedStore store = Tgx101SavedStore.get(c.tdlib());
    final List<Long> extra = new ArrayList<>();
    long bytes = 0;
    for (List<TdApi.Message> group : groups) {
      TdApi.Message keep = keeper(store, group);
      for (TdApi.Message m : group) {
        if (m == keep) continue;
        extra.add(m.id);
        TdApi.File f = Tgx101SavedKit.mainFile(m);
        if (f != null) bytes += Math.max(f.size, f.expectedSize);
      }
    }
    body.addView(hint(c, bytes > 0 ? Lang.getString(R.string.Tgx101StorageDuplicatesInfo, extra.size(), Strings.buildSize(bytes)) : Lang.getString(R.string.Tgx101StorageDuplicatesInfoNoSize, extra.size())));
    for (int i = 0; i < groups.size() && i < GROUPS_SHOWN; i++) {
      final List<TdApi.Message> group = groups.get(i);
      final TdApi.Message keep = keeper(store, group);
      String title = Tgx101SavedKit.title(keep);
      body.addView(Tgx101Sheet.row(c.context(), 0, 0, title, "×" + group.size(), false, v -> {
        handle.dismiss();
        Tgx101SavedKit.openInFeed(c, keep);
      }));
    }
    if (groups.size() > GROUPS_SHOWN) body.addView(hint(c, Lang.getString(R.string.Tgx101StorageMore, groups.size() - GROUPS_SHOWN)));
    body.addView(Tgx101SavedActions.primaryButton(c.context(), Lang.getString(R.string.Tgx101StorageDuplicatesKeepOne), v -> {
      handle.dismiss();
      UI.post(() -> c.showConfirm(Lang.getString(R.string.Tgx101StorageDuplicatesConfirm, extra.size()), Lang.getString(R.string.Tgx101StorageDuplicatesDelete),
        R.drawable.baseline_delete_24, ViewController.OptionColor.RED, () -> {
          long[] ids = new long[extra.size()];
          for (int k = 0; k < ids.length; k++) ids[k] = extra.get(k);
          org.thunderdog.challegram.Tgx101Diag.mark("storage: duplicates, " + ids.length + " deleted");
          c.tdlib().send(new TdApi.DeleteMessages(c.tdlib().selfChatId(), ids, true), c.tdlib().typedOkHandler());
          UI.showToast(Lang.getString(R.string.Tgx101StorageDuplicatesDone, ids.length), android.widget.Toast.LENGTH_SHORT);
        }), 200);
    }));
  }

  // The week

  public static void showWeek (ViewController<?> c) {
    final Tgx101Sheet.Handle[] handle = new Tgx101Sheet.Handle[1];
    handle[0] = Tgx101Sheet.show(c, null, body -> loading(c, body, R.string.Tgx101StorageWeek));
    Tgx101SavedKit.history(c.tdlib(), SCAN, messages -> {
      if (!c.isDestroyed()) fillWeek(c, handle[0], messages, null);
    });
  }

  private static void fillWeek (ViewController<?> c, Tgx101Sheet.Handle handle, List<TdApi.Message> messages, @Nullable String phrase) {
    final Tgx101SavedStore store = Tgx101SavedStore.get(c.tdlib());
    final long now = System.currentTimeMillis() / 1000L;
    int week = 0, unfiled = 0, voices = 0, oldLinks = 0;
    int[] kinds = new int[Tgx101SavedKit.KIND_OTHER + 1];
    Map<String, Integer> folders = new HashMap<>();
    final List<String> weekTitles = new ArrayList<>();
    for (TdApi.Message m : messages) {
      if (m.content == null) continue;
      int kind = Tgx101SavedKit.kindOf(m);
      Tgx101SavedStore.Folder folder = store.folderOf(m.id);
      if (folder == null) unfiled++;
      if (now - m.date < WEEK) {
        week++;
        kinds[kind]++;
        if (folder != null) folders.put(folder.name, (folders.containsKey(folder.name) ? folders.get(folder.name) : 0) + 1);
        if (weekTitles.size() < 30) weekTitles.add(Tgx101SavedKit.kindLabel(kind) + ". " + Tgx101SavedKit.title(m));
      }
      if (kind == Tgx101SavedKit.KIND_VOICE) {
        TdApi.SpeechRecognitionResult r = ((TdApi.MessageVoiceNote) m.content).voiceNote.speechRecognitionResult;
        if (!(r instanceof TdApi.SpeechRecognitionResultText)) voices++;
      }
      if (kind == Tgx101SavedKit.KIND_LINK && now - m.date > YEAR) oldLinks++;
    }
    LinearLayout body = handle.body;
    body.removeAllViews();
    body.addView(Tgx101Sheet.title(c.context(), Lang.getString(R.string.Tgx101StorageWeek)));
    StringBuilder b = new StringBuilder(Lang.getString(R.string.Tgx101StorageWeekSaved, week));
    List<String> parts = new ArrayList<>();
    for (int k = 0; k < kinds.length; k++) {
      if (kinds[k] > 0) parts.add(Tgx101SavedKit.kindLabel(k).toLowerCase() + " " + kinds[k]);
    }
    if (!parts.isEmpty()) b.append(": ").append(android.text.TextUtils.join(", ", parts));
    String top = null;
    int topCount = 0;
    for (Map.Entry<String, Integer> e : folders.entrySet()) {
      if (e.getValue() > topCount) {
        top = e.getKey();
        topCount = e.getValue();
      }
    }
    if (top != null) b.append(". ").append(Lang.getString(R.string.Tgx101StorageWeekTop, top, topCount));
    body.addView(hint(c, b.append('.').toString()));
    if (phrase != null) {
      TextView v = Tgx101SavedHomeController.text(c.context(), 15f, Theme.textAccentColor(), false);
      v.setText(phrase);
      v.setPadding(Screen.dp(20f), Screen.dp(4f), Screen.dp(20f), Screen.dp(8f));
      body.addView(v);
    } else if (week > 0 && org.thunderdog.challegram.unsorted.Settings.instance().tgx101Ai()) {
      final String facts = b.toString();
      body.addView(Tgx101Sheet.row(c.context(), R.drawable.baseline_stars_24, 0, Lang.getString(R.string.Tgx101StorageWeekAi), null, false, v -> {
        v.setEnabled(false);
        askWeek(c.tdlib(), facts, weekTitles, answer -> {
          if (c.isDestroyed()) return;
          if (answer == null) UI.showToast(R.string.Tgx101TasteFailed, android.widget.Toast.LENGTH_SHORT);
          fillWeek(c, handle, messages, answer);
        });
      }));
    }
    if (unfiled > 0) {
      final boolean ai = org.thunderdog.challegram.unsorted.Settings.instance().tgx101AiSort() && org.thunderdog.challegram.unsorted.Settings.instance().tgx101Ai();
      body.addView(Tgx101Sheet.row(c.context(), R.drawable.baseline_folder_24, 0, Lang.getString(R.string.Tgx101StorageWeekUnfiled), String.valueOf(unfiled), false, v -> {
        handle.dismiss();
        c.navigateTo(new Tgx101SavedAiSortController(c.context(), c.tdlib()).tgx101LocalOnly(!ai));
      }));
    }
    if (voices > 0) body.addView(Tgx101Sheet.row(c.context(), R.drawable.baseline_mic_24, 0, Lang.getString(R.string.Tgx101StorageWeekVoices), String.valueOf(voices), false, null));
    if (oldLinks > 0) body.addView(Tgx101Sheet.row(c.context(), R.drawable.baseline_link_24, 0, Lang.getString(R.string.Tgx101StorageWeekOldLinks), String.valueOf(oldLinks), false, null));
  }

  private static void askWeek (Tdlib tdlib, String facts, List<String> titles, RunnableData<String> after) {
    StringBuilder text = new StringBuilder(facts).append("\n\nСохранено за неделю:\n");
    for (String t : titles) text.append("- ").append(t.replace('\n', ' ').replace('[', '(').replace(']', ')').replaceAll("[*_`]", " ")).append('\n');
    String prompt = "Опиши мою неделю в Хранилище одной-двумя короткими фразами: о чём больше всего сохранял, что стоит разобрать. Обращайся на «вы», без вступления.";
    TdApi.InputRichMessage message = new TdApi.InputRichMessage(new TdApi.RichMessageSourceMarkdown(text.toString(), new TdApi.InputRichMessageMedia[0]), false, false);
    org.thunderdog.challegram.Tgx101Diag.mark("storage: the week asked, " + text.length() + " chars");
    org.thunderdog.challegram.data.Tgx101OwnAi.send(tdlib, new TdApi.ComposeRichMessageWithAi(message, "", "", prompt, false), result -> UI.post(() -> {
      String answer = null;
      if (result.getConstructor() == TdApi.RichMessage.CONSTRUCTOR) {
        TdApi.FormattedText f = org.thunderdog.challegram.data.Tgx101RichMessage.toText((TdApi.RichMessage) result);
        answer = f != null ? f.text : null;
      } else if (result.getConstructor() == TdApi.FormattedText.CONSTRUCTOR) {
        answer = ((TdApi.FormattedText) result).text;
      }
      after.runWithData(answer != null && !answer.trim().isEmpty() ? answer.trim() : null);
    }));
  }

  // The phone's own rules

  /** Who a record came from: a chat or a user, 0 — written here */
  static long sourceOf (TdApi.Message m) {
    if (m.forwardInfo == null || m.forwardInfo.origin == null) return 0;
    TdApi.MessageOrigin o = m.forwardInfo.origin;
    switch (o.getConstructor()) {
      case TdApi.MessageOriginUser.CONSTRUCTOR: return ((TdApi.MessageOriginUser) o).senderUserId;
      case TdApi.MessageOriginChat.CONSTRUCTOR: return ((TdApi.MessageOriginChat) o).senderChatId;
      case TdApi.MessageOriginChannel.CONSTRUCTOR: return ((TdApi.MessageOriginChannel) o).chatId;
    }
    return 0;
  }

  /** A word of 4+ letters, its first 5 for a rough stem */
  private static List<String> stems (String s) {
    List<String> out = new ArrayList<>();
    for (String w : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
      if (w.length() >= 4) out.add(w.length() > 5 ? w.substring(0, 5) : w);
    }
    return out;
  }

  /**
   * Folder name → the indexes of the batch, by what is already in the folders: the folder remembered for the chat, the folder most
   * records from the same chat or site are in, a word of the folder's name in the record. What none of these places stays out
   */
  public static void propose (Tdlib tdlib, List<TdApi.Message> batch, RunnableData<LinkedHashMap<String, List<Integer>>> after) {
    final Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    List<Long> ids = new ArrayList<>();
    for (Tgx101SavedStore.Folder f : store.folders()) {
      List<Long> all = new ArrayList<>(f.messageIds);
      for (int i = Math.max(0, all.size() - SAMPLES); i < all.size(); i++) ids.add(all.get(i));
    }
    Tgx101SavedKit.byIds(tdlib, ids, samples -> {
      // what each source and each site lean to: folder id → count
      Map<Long, Map<Long, Integer>> bySource = new HashMap<>();
      Map<String, Map<Long, Integer>> bySite = new HashMap<>();
      for (TdApi.Message m : samples) {
        Tgx101SavedStore.Folder f = store.folderOf(m.id);
        if (f == null) continue;
        long source = sourceOf(m);
        if (source != 0) add(bySource, source, f.id);
        if (Tgx101SavedKit.kindOf(m) == Tgx101SavedKit.KIND_LINK) {
          String site = Tgx101SavedKit.site(m);
          if (!site.isEmpty()) add(bySite, site.toLowerCase(Locale.ROOT), f.id);
        }
      }
      LinkedHashMap<String, List<Integer>> proposal = new LinkedHashMap<>();
      int placed = 0;
      for (int i = 0; i < batch.size(); i++) {
        TdApi.Message m = batch.get(i);
        long folderId = 0;
        long source = sourceOf(m);
        if (source != 0) {
          Tgx101SavedStore.ChatRule rule = store.chatRule(source);
          if (rule != null && rule.folderId != 0 && store.folder(rule.folderId) != null) folderId = rule.folderId;
          if (folderId == 0) folderId = best(bySource.get(source));
        }
        if (folderId == 0 && Tgx101SavedKit.kindOf(m) == Tgx101SavedKit.KIND_LINK) {
          String site = Tgx101SavedKit.site(m);
          if (!site.isEmpty()) folderId = best(bySite.get(site.toLowerCase(Locale.ROOT)));
        }
        if (folderId == 0) {
          TdApi.FormattedText t = Tgx101SavedKit.text(m);
          List<String> words = stems(Tgx101SavedKit.title(m) + " " + (t != null ? t.text : ""));
          for (Tgx101SavedStore.Folder f : store.folders()) {
            for (String stem : stems(f.name)) {
              if (words.contains(stem)) {
                folderId = f.id;
                break;
              }
            }
            if (folderId != 0) break;
          }
        }
        Tgx101SavedStore.Folder folder = folderId != 0 ? store.folder(folderId) : null;
        if (folder == null) continue;
        List<Integer> list = proposal.get(folder.name);
        if (list == null) proposal.put(folder.name, list = new ArrayList<>());
        list.add(i);
        placed++;
      }
      org.thunderdog.challegram.Tgx101Diag.mark("storage: rules placed " + placed + " of " + batch.size() + " by " + samples.size() + " samples");
      after.runWithData(proposal);
    });
  }

  private static <K> void add (Map<K, Map<Long, Integer>> map, K key, long folderId) {
    Map<Long, Integer> counts = map.get(key);
    if (counts == null) map.put(key, counts = new HashMap<>());
    counts.put(folderId, (counts.containsKey(folderId) ? counts.get(folderId) : 0) + 1);
  }

  private static long best (@Nullable Map<Long, Integer> counts) {
    if (counts == null) return 0;
    long best = 0;
    int max = 0, total = 0;
    for (Map.Entry<Long, Integer> e : counts.entrySet()) {
      total += e.getValue();
      if (e.getValue() > max) {
        max = e.getValue();
        best = e.getKey();
      }
    }
    return max * 2 > total ? best : 0; // most of them, not one of many
  }
}
