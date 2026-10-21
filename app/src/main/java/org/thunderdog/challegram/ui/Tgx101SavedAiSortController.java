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
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TGx101 (user 2026-10-21 «делай 3 вариант с ИИ»): the storage sorted by Telegram's AI. The latest records that are in
 * no folder go to the AI with the folders' names; it answers «number: folder» (an existing folder, or a short new one).
 * Shown here by folder, every record ticked — untick what shouldn't go, ✓ puts the rest in.
 */
public class Tgx101SavedAiSortController extends RecyclerViewController<Void> implements View.OnClickListener, Menu {
  private static final int BATCH = 30, SCAN = 400, LINE_MAX = 110;
  private static final int ITEM_ID_BASE = 3100000;

  private SettingsAdapter adapter;
  private final List<TdApi.Message> batch = new ArrayList<>();
  // folder name (as it will be) → indexes into batch
  private final LinkedHashMap<String, List<Integer>> proposal = new LinkedHashMap<>();
  private boolean[] checked = new boolean[0];
  private boolean ready;

  public Tgx101SavedAiSortController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101SavedAiSort;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101SavedAiSort);
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_done;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_done) header.addDoneButton(menu, this);
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_done) apply();
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getData() instanceof String) view.setData((String) item.getData());
      }
    };
    showStatus(Lang.getString(R.string.Tgx101SavedAiWorking));
    recyclerView.setAdapter(adapter);
    load();
  }

  private void showStatus (CharSequence text) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, text, false));
    adapter.setItems(items, false);
  }

  // asking

  private void load () {
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    Tgx101SavedKit.history(tdlib, SCAN, messages -> {
      if (isDestroyed()) return;
      for (TdApi.Message m : messages) {
        if (batch.size() >= BATCH) break;
        if (m.content == null || store.isFiled(m.id)) continue;
        if (!hasSense(m)) continue; // a photo with no caption, a voice not transcribed: nothing to sort by (test 2026-10-21)
        batch.add(m);
      }
      if (batch.isEmpty()) {
        showStatus(Lang.getString(R.string.Tgx101SavedAiNothing));
        return;
      }
      ask(store);
    });
  }

  private static @Nullable String voiceText (TdApi.Message m) {
    if (m.content.getConstructor() != TdApi.MessageVoiceNote.CONSTRUCTOR) return null;
    TdApi.SpeechRecognitionResult r = ((TdApi.MessageVoiceNote) m.content).voiceNote.speechRecognitionResult;
    return r instanceof TdApi.SpeechRecognitionResultText ? ((TdApi.SpeechRecognitionResultText) r).text : null;
  }

  /** something the AI can tell the topic by: text, a caption, a file or link title, a transcript, where it came from */
  private boolean hasSense (TdApi.Message m) {
    TdApi.FormattedText text = Tgx101SavedKit.text(m);
    if (text != null && text.text != null && !text.text.trim().isEmpty()) return true;
    String v = voiceText(m);
    if (v != null && !v.trim().isEmpty()) return true;
    String title = Tgx101SavedKit.title(m);
    return !title.equals(Tgx101SavedKit.kindLabel(Tgx101SavedKit.kindOf(m))) && m.content.getConstructor() != TdApi.MessageVoiceNote.CONSTRUCTOR;
  }

  private static String clean (String s) {
    s = s.replace('\n', ' ').replace('[', '(').replace(']', ')').replace('*', ' ').replace('_', ' ').replace('`', '\'').replaceAll("\\s+", " ").trim();
    return s.length() > LINE_MAX ? s.substring(0, LINE_MAX) + "…" : s;
  }

  private String describe (TdApi.Message m) {
    StringBuilder b = new StringBuilder();
    int kind = Tgx101SavedKit.kindOf(m);
    b.append(Tgx101SavedKit.kindLabel(kind)).append(". ");
    b.append(clean(Tgx101SavedKit.title(m)));
    TdApi.FormattedText text = Tgx101SavedKit.text(m);
    if (text != null && text.text != null) {
      String title = Tgx101SavedKit.title(m).trim(), rest = text.text.trim();
      if (rest.startsWith(title)) rest = rest.substring(title.length());
      rest = clean(rest);
      if (!rest.isEmpty()) b.append(" — ").append(rest);
    }
    String voice = voiceText(m);
    if (voice != null && !voice.trim().isEmpty()) b.append(" — ").append(clean(voice));
    if (kind == Tgx101SavedKit.KIND_LINK) b.append(" (").append(clean(Tgx101SavedKit.site(m))).append(')');
    String source = Tgx101SavedKit.source(tdlib, m);
    if (m.forwardInfo != null && !source.isEmpty()) b.append(". Из: ").append(clean(source));
    return b.toString();
  }

  private void ask (Tgx101SavedStore store) {
    StringBuilder text = new StringBuilder();
    List<Tgx101SavedStore.Folder> folders = store.folders();
    text.append("Папки: ");
    if (folders.isEmpty()) {
      text.append("(пока нет)");
    } else {
      for (int i = 0; i < folders.size(); i++) {
        if (i > 0) text.append("; ");
        text.append(clean(folders.get(i).name));
      }
    }
    text.append("\n\nЗаписи:\n");
    for (int i = 0; i < batch.size(); i++) {
      text.append(i + 1).append(") ").append(describe(batch.get(i))).append('\n');
    }
    String prompt = "Разложи записи по папкам. Для каждой записи выбери подходящую папку из списка или придумай новую короткую (1–2 слова, с большой буквы). " +
      "Группируй по теме, а не по типу: папки «Фото», «Видео», «Файлы», «Голосовые», «Заметки» не создавай. Если тема неясна — «номер: -». " +
      "Ответь только строками вида «номер: папка», без пояснений.";
    org.thunderdog.challegram.Tgx101Diag.mark("ai sort: " + batch.size() + " records, " + folders.size() + " folders, " + text.length() + " chars");
    TdApi.InputRichMessage message = new TdApi.InputRichMessage(new TdApi.RichMessageSourceMarkdown(text.toString(), new TdApi.InputRichMessageMedia[0]), false, false);
    tdlib.client().send(new TdApi.ComposeRichMessageWithAi(message, "", "", prompt, false), object -> runOnUiThreadOptional(() -> {
      switch (object.getConstructor()) {
        case TdApi.RichMessage.CONSTRUCTOR: {
          TdApi.FormattedText answer = org.thunderdog.challegram.data.Tgx101RichMessage.toText((TdApi.RichMessage) object);
          onAnswer(store, answer != null ? answer.text : "");
          break;
        }
        case TdApi.FormattedText.CONSTRUCTOR:
          onAnswer(store, ((TdApi.FormattedText) object).text);
          break;
        case TdApi.Error.CONSTRUCTOR: {
          String error = ((TdApi.Error) object).message;
          org.thunderdog.challegram.Tgx101Diag.mark("ai sort: failed — " + error);
          showStatus(error != null && error.contains("PREMIUM") ? Lang.getString(R.string.Tgx101AiPremium) : Lang.getString(R.string.Tgx101AiFailed, TD.toErrorString(object)));
          break;
        }
      }
    }));
  }

  // «number: folder» anywhere — one per line, or all in one line as the AI sometimes answers (test 2026-10-21:
  // «4: Багфикс 5: Багфикс 6: …» came as a single paragraph)
  private static final Pattern NUMBER = Pattern.compile("(?<![\\d\\p{L}])(\\d{1,3})\\s*[:.)]\\s*");

  private static List<String[]> parse (String answer) {
    List<String[]> out = new ArrayList<>();
    Matcher m = NUMBER.matcher(answer);
    int lastStart = -1;
    String lastNumber = null;
    while (m.find()) {
      if (lastNumber != null) out.add(new String[] {lastNumber, answer.substring(lastStart, m.start())});
      lastNumber = m.group(1);
      lastStart = m.end();
    }
    if (lastNumber != null) out.add(new String[] {lastNumber, answer.substring(lastStart)});
    for (String[] pair : out) {
      String name = pair[1];
      int cut = name.length();
      for (char c : new char[] {'\n', ';', '|', ','}) {
        int at = name.indexOf(c);
        if (at >= 0 && at < cut) cut = at;
      }
      pair[1] = name.substring(0, cut);
    }
    return out;
  }

  private void onAnswer (Tgx101SavedStore store, String answer) {
    org.thunderdog.challegram.Tgx101Diag.mark("ai sort: answer " + answer.length() + " chars: " + answer.replace('\n', '|').substring(0, Math.min(400, answer.length())));
    Map<String, String> existing = new LinkedHashMap<>();
    for (Tgx101SavedStore.Folder f : store.folders()) existing.put(f.name.trim().toLowerCase(Locale.ROOT), f.name);
    boolean[] taken = new boolean[batch.size()];
    for (String[] pair : parse(answer)) {
      int index;
      try {
        index = Integer.parseInt(pair[0]) - 1;
      } catch (NumberFormatException e) {
        continue;
      }
      if (index < 0 || index >= batch.size() || taken[index]) continue;
      String name = pair[1].replaceAll("[«»\"'*_`.]", "").trim();
      if (name.isEmpty() || name.equals("-") || name.equals("—")) continue;
      if (name.length() > 24) name = name.substring(0, 24).trim();
      String known = existing.get(name.toLowerCase(Locale.ROOT));
      if (known != null) name = known;
      else name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
      List<Integer> list = proposal.get(name);
      if (list == null) proposal.put(name, list = new ArrayList<>());
      list.add(index);
      taken[index] = true;
    }
    if (proposal.isEmpty()) {
      showStatus(Lang.getString(R.string.Tgx101SavedAiNoAnswer));
      return;
    }
    checked = new boolean[batch.size()];
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.Tgx101SavedAiHint), false));
    for (Map.Entry<String, List<Integer>> e : proposal.entrySet()) {
      boolean isNew = !existing.containsKey(e.getKey().toLowerCase(Locale.ROOT));
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, e.getKey() + (isNew ? " · " + Lang.getString(R.string.Tgx101SavedAiNewFolder) : ""), false));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (int index : e.getValue()) {
        if (!first) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        first = false;
        checked[index] = true;
        TdApi.Message m = batch.get(index);
        String title = Tgx101SavedKit.title(m);
        if (title.length() > 28) title = title.substring(0, 27).trim() + "…"; // one line, clear of the tick
        ListItem item = new ListItem(ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE, ITEM_ID_BASE + index, 0, title, true);
        item.setData(Tgx101SavedKit.meta(tdlib, m));
        items.add(item);
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    adapter.setItems(items, false);
    ready = true;
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id >= ITEM_ID_BASE && id < ITEM_ID_BASE + checked.length) {
      int index = id - ITEM_ID_BASE;
      checked[index] = !checked[index];
      // the item keeps it too — scrolled away and back, the row was bound ticked again (user 2026-10-21 «снимаю галочки, он ставит опять»)
      if (v.getTag() instanceof ListItem) ((ListItem) v.getTag()).setSelected(checked[index]);
      adapter.processToggle(v);
    }
  }

  // Undo (user 2026-10-21 «можно ли будет возвращать действие? если я сделал, но потом передумал»): the last sort of
  // each account, until the app is closed — the records leave the folders, the folders the AI made go if left empty
  public static final class Undo {
    final long[] ids;
    final List<Long> createdFolders;
    public final int count;
    boolean announce = true;

    Undo (long[] ids, List<Long> createdFolders) {
      this.ids = ids;
      this.createdFolders = createdFolders;
      this.count = ids.length;
    }
  }

  private static final Map<Integer, Undo> lastSort = new java.util.HashMap<>();

  public static synchronized @androidx.annotation.Nullable Undo pending (Tdlib tdlib) {
    return lastSort.get(tdlib.id());
  }

  /** the storage shows «Put in folders: N · Undo» once, right after */
  public static synchronized @androidx.annotation.Nullable Undo takeAnnouncement (Tdlib tdlib) {
    Undo u = lastSort.get(tdlib.id());
    if (u == null || !u.announce) return null;
    u.announce = false;
    return u;
  }

  public static synchronized int undo (Tdlib tdlib) {
    Undo u = lastSort.remove(tdlib.id());
    if (u == null) return 0;
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    store.removeFromFolders(u.ids);
    for (long folderId : u.createdFolders) {
      Tgx101SavedStore.Folder f = store.folder(folderId);
      if (f != null && f.messageIds.isEmpty()) store.deleteFolder(folderId);
    }
    org.thunderdog.challegram.Tgx101Diag.mark("ai sort: undone, " + u.count + " records out, " + u.createdFolders.size() + " folders checked");
    return u.count;
  }

  private void apply () {
    if (!ready) {
      navigateBack();
      return;
    }
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    int total = 0, created = 0;
    List<Long> movedIds = new ArrayList<>(), createdIds = new ArrayList<>();
    for (Map.Entry<String, List<Integer>> e : proposal.entrySet()) {
      List<Long> ids = new ArrayList<>();
      for (int index : e.getValue()) if (checked[index]) ids.add(batch.get(index).id);
      if (ids.isEmpty()) continue;
      Tgx101SavedStore.Folder folder = null;
      for (Tgx101SavedStore.Folder f : store.folders()) {
        if (f.name.trim().equalsIgnoreCase(e.getKey())) { folder = f; break; }
      }
      if (folder == null) {
        folder = store.createFolder(e.getKey());
        createdIds.add(folder.id);
        created++;
      }
      long[] array = new long[ids.size()];
      for (int i = 0; i < array.length; i++) array[i] = ids.get(i);
      store.putInFolder(folder.id, array);
      movedIds.addAll(ids);
      total += array.length;
    }
    org.thunderdog.challegram.Tgx101Diag.mark("ai sort: put " + total + " records, " + created + " new folders");
    if (total > 0) {
      long[] moved = new long[movedIds.size()];
      for (int i = 0; i < moved.length; i++) moved[i] = movedIds.get(i);
      synchronized (Tgx101SavedAiSortController.class) {
        lastSort.put(tdlib.id(), new Undo(moved, createdIds));
      }
    }
    navigateBack();
  }
}
