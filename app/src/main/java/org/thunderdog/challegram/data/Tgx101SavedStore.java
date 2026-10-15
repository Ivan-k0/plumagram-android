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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.telegram.GlobalMessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TGx101 (user 2026-10-15, approved mockups «Избранное как хранилище»): what Telegram itself can't keep for Saved
 * Messages lives here, per account, on this phone — folders (name, colour, which messages), tags given by name to
 * messages that can't carry a #hashtag (forwarded ones), which folders / tags are tiles on the home screen, and the
 * Saved settings. Message ids follow a message after it is sent (temporary → real id) and are dropped when deleted.
 */
public final class Tgx101SavedStore implements GlobalMessageListener {

  // Settings

  public static final int SHEET_ALWAYS = 0, SHEET_EXTERNAL_ONLY = 1, SHEET_NEVER = 2;
  public static final int OPEN_HOME = 0, OPEN_FEED = 1;

  public static final int[] FOLDER_COLORS = {0xff3f7cad, 0xffc0662a, 0xff2e8a4a, 0xff7c4fb0, 0xffb53c57, 0xff2a8c8c, 0xff8a7a2a};

  public static final class Folder {
    public final long id;
    public String name;
    public int colorIndex;
    public final LinkedHashSet<Long> messageIds = new LinkedHashSet<>();
    public boolean onHome = true;

    Folder (long id, String name, int colorIndex) {
      this.id = id;
      this.name = name;
      this.colorIndex = colorIndex;
    }

    public int color () {
      return FOLDER_COLORS[Math.abs(colorIndex) % FOLDER_COLORS.length];
    }
  }

  private static final Map<Integer, Tgx101SavedStore> instances = new java.util.HashMap<>();
  private static boolean listenerAdded;

  public static synchronized Tgx101SavedStore get (Tdlib tdlib) {
    Tgx101SavedStore store = instances.get(tdlib.id());
    if (store == null) {
      store = new Tgx101SavedStore(tdlib.id());
      instances.put(tdlib.id(), store);
    }
    ensureListener();
    return store;
  }

  /** From app start: the «Saved» banner has to see what is saved before Saved is first opened */
  public static synchronized void ensureListener () {
    if (!listenerAdded) {
      listenerAdded = true;
      TdlibManager.instance().global().addMessageListener(new GlobalMessageListener() {
        @Override
        public void onMessageSendSucceeded (Tdlib tdlib, TdApi.Message message, long oldMessageId) {
          if (message.chatId == tdlib.selfChatId()) get(tdlib).remap(oldMessageId, message.id);
        }

        @Override
        public void onMessagesDeleted (Tdlib tdlib, long chatId, long[] messageIds) {
          if (chatId == tdlib.selfChatId()) get(tdlib).forget(messageIds);
        }

        @Override
        public void onNewMessage (Tdlib tdlib, TdApi.Message message) {
          onSavedHere(tdlib, message);
        }

        @Override
        public void onNewMessages (Tdlib tdlib, TdApi.Message[] messages) {
          for (TdApi.Message m : messages) onSavedHere(tdlib, m);
        }
      });
    }
  }

  // «Saved» banner (mockups 7 and 9): something this phone just sent to Saved Messages

  private static long externalShareAt;
  private static long suppressUntil;

  /** The share sheet was opened from another app: what is saved in the next minutes counts as «from another app» */
  public static void markExternalShare () {
    externalShareAt = android.os.SystemClock.uptimeMillis();
  }

  /** No banner for a moment (a note typed on the Saved home screen, a reminder copy) */
  public static void suppressBanner (long ms) {
    suppressUntil = android.os.SystemClock.uptimeMillis() + ms;
  }

  private static final List<Long> pendingIds = new ArrayList<>();
  private static Runnable pendingShow;

  private static void onSavedHere (Tdlib tdlib, TdApi.Message m) {
    if (m == null || m.chatId != tdlib.selfChatId() || m.schedulingState != null) return;
    if (!(m.sendingState instanceof TdApi.MessageSendingStatePending)) return; // sent from another device / history
    long now = android.os.SystemClock.uptimeMillis();
    if (now < suppressUntil) return;
    final boolean external = now - externalShareAt < 120_000L;
    org.thunderdog.challegram.tool.UI.post(() -> {
      // typing in the Saved feed itself: every message is «saved», no banner for each
      org.thunderdog.challegram.BaseActivity a = org.thunderdog.challegram.tool.UI.getUiContext();
      org.thunderdog.challegram.navigation.ViewController<?> top = a != null ? a.navigation().getCurrentStackItem() : null;
      if (!external && top instanceof org.thunderdog.challegram.ui.MessagesController && ((org.thunderdog.challegram.ui.MessagesController) top).getChatId() == tdlib.selfChatId()) return;
      synchronized (pendingIds) {
        if (!pendingIds.contains(m.id)) pendingIds.add(m.id); // one message may be announced twice (single and batch)
        if (pendingShow != null) org.thunderdog.challegram.tool.UI.removePendingRunnable(pendingShow);
        pendingShow = () -> {
          long[] ids;
          synchronized (pendingIds) {
            ids = new long[pendingIds.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = pendingIds.get(i);
            pendingIds.clear();
            pendingShow = null;
          }
          if (external) externalShareAt = 0;
          org.thunderdog.challegram.ui.Tgx101SavedActions.showSavedBanner(tdlib, ids, external);
        };
        // an album or several forwarded messages arrive together: one banner for all of them
        org.thunderdog.challegram.tool.UI.post(pendingShow, 600);
      }
    });
  }

  private final int accountId;
  private final List<Folder> folders = new ArrayList<>();
  /** tag name (without #, lower case) → message ids tagged by name on this phone */
  private final Map<String, LinkedHashSet<Long>> localTags = new LinkedHashMap<>();
  /** tags shown as tiles on the home screen */
  private final LinkedHashSet<String> homeTags = new LinkedHashSet<>();
  private long nextFolderId = 1;

  public int sheetMode = SHEET_ALWAYS;
  public int openMode = OPEN_HOME;
  public boolean inboxTile = true;
  public boolean showPinned = true;
  public boolean hashtagOwn = true;

  private Tgx101SavedStore (int accountId) {
    this.accountId = accountId;
    load();
  }

  private String key () {
    return "tgx101_saved_store_" + accountId;
  }

  // Folders

  public synchronized List<Folder> folders () {
    return new ArrayList<>(folders);
  }

  public synchronized @Nullable Folder folder (long id) {
    for (Folder f : folders) if (f.id == id) return f;
    return null;
  }

  public synchronized Folder createFolder (String name) {
    Folder f = new Folder(nextFolderId++, name.trim(), folders.size());
    folders.add(f);
    save();
    return f;
  }

  public synchronized void renameFolder (long id, String name) {
    Folder f = folder(id);
    if (f != null) {
      f.name = name.trim();
      save();
    }
  }

  public synchronized void deleteFolder (long id) {
    Folder f = folder(id);
    if (f != null) {
      folders.remove(f);
      save();
    }
  }

  /** A message lives in one folder at most (moving it takes it out of the old one) */
  public synchronized void putInFolder (long folderId, long[] messageIds) {
    Folder target = folder(folderId);
    if (target == null) return;
    for (long id : messageIds) {
      for (Folder f : folders) f.messageIds.remove(id);
      target.messageIds.add(id);
    }
    save();
  }

  public synchronized void removeFromFolders (long[] messageIds) {
    for (long id : messageIds) for (Folder f : folders) f.messageIds.remove(id);
    save();
  }

  public synchronized @Nullable Folder folderOf (long messageId) {
    for (Folder f : folders) if (f.messageIds.contains(messageId)) return f;
    return null;
  }

  public synchronized boolean isFiled (long messageId) {
    return folderOf(messageId) != null;
  }

  public synchronized void setFolderOnHome (long id, boolean onHome) {
    Folder f = folder(id);
    if (f != null) {
      f.onHome = onHome;
      save();
    }
  }

  // Tags

  public static String normalizeTag (String tag) {
    String t = tag.trim();
    while (t.startsWith("#")) t = t.substring(1);
    return t.replaceAll("\\s+", "_").toLowerCase(java.util.Locale.ROOT);
  }

  public synchronized void addLocalTag (String tag, long[] messageIds) {
    String t = normalizeTag(tag);
    if (t.isEmpty()) return;
    LinkedHashSet<Long> set = localTags.get(t);
    if (set == null) {
      set = new LinkedHashSet<>();
      localTags.put(t, set);
    }
    for (long id : messageIds) set.add(id);
    save();
  }

  public synchronized List<Long> localTagged (String tag) {
    LinkedHashSet<Long> set = localTags.get(normalizeTag(tag));
    return set != null ? new ArrayList<>(set) : new ArrayList<>();
  }

  /** Every tag known here: given by name on this phone or shown on the home screen */
  public synchronized List<String> knownTags () {
    LinkedHashSet<String> all = new LinkedHashSet<>(homeTags);
    all.addAll(localTags.keySet());
    return new ArrayList<>(all);
  }

  public synchronized void rememberTag (String tag) {
    String t = normalizeTag(tag);
    if (!t.isEmpty() && !localTags.containsKey(t)) {
      localTags.put(t, new LinkedHashSet<>());
      save();
    }
  }

  public synchronized boolean isTagOnHome (String tag) {
    return homeTags.contains(normalizeTag(tag));
  }

  public synchronized void setTagOnHome (String tag, boolean onHome) {
    String t = normalizeTag(tag);
    if (t.isEmpty()) return;
    if (onHome) homeTags.add(t); else homeTags.remove(t);
    save();
  }

  public synchronized List<String> homeTags () {
    return new ArrayList<>(homeTags);
  }

  public synchronized List<String> tagsOf (long messageId) {
    List<String> out = new ArrayList<>();
    for (Map.Entry<String, LinkedHashSet<Long>> e : localTags.entrySet()) {
      if (e.getValue().contains(messageId)) out.add(e.getKey());
    }
    return out;
  }

  // Message id upkeep

  synchronized void remap (long oldId, long newId) {
    boolean changed = false;
    for (Folder f : folders) {
      if (f.messageIds.remove(oldId)) {
        f.messageIds.add(newId);
        changed = true;
      }
    }
    for (LinkedHashSet<Long> set : localTags.values()) {
      if (set.remove(oldId)) {
        set.add(newId);
        changed = true;
      }
    }
    if (changed) save();
  }

  synchronized void forget (long[] messageIds) {
    boolean changed = false;
    for (long id : messageIds) {
      for (Folder f : folders) changed |= f.messageIds.remove(id);
      for (LinkedHashSet<Long> set : localTags.values()) changed |= set.remove(id);
    }
    if (changed) save();
  }

  // Persistence

  public synchronized void save () {
    try {
      JSONObject o = new JSONObject();
      o.put("v", 1);
      o.put("next", nextFolderId);
      JSONArray fs = new JSONArray();
      for (Folder f : folders) {
        JSONObject j = new JSONObject();
        j.put("id", f.id);
        j.put("name", f.name);
        j.put("color", f.colorIndex);
        j.put("home", f.onHome);
        JSONArray ids = new JSONArray();
        for (long id : f.messageIds) ids.put(id);
        j.put("ids", ids);
        fs.put(j);
      }
      o.put("folders", fs);
      JSONObject tags = new JSONObject();
      for (Map.Entry<String, LinkedHashSet<Long>> e : localTags.entrySet()) {
        JSONArray ids = new JSONArray();
        for (long id : e.getValue()) ids.put(id);
        tags.put(e.getKey(), ids);
      }
      o.put("tags", tags);
      JSONArray home = new JSONArray();
      for (String t : homeTags) home.put(t);
      o.put("homeTags", home);
      o.put("sheet", sheetMode);
      o.put("open", openMode);
      o.put("inbox", inboxTile);
      o.put("pinned", showPinned);
      o.put("hashtag", hashtagOwn);
      Settings.instance().pmc().putString(key(), o.toString());
    } catch (Throwable t) {
      org.thunderdog.challegram.Tgx101Diag.mark("saved store: save failed " + t.getClass().getSimpleName());
    }
  }

  private void load () {
    String raw = Settings.instance().pmc().getString(key(), null);
    if (raw == null || raw.isEmpty()) return;
    try {
      JSONObject o = new JSONObject(raw);
      nextFolderId = o.optLong("next", 1);
      JSONArray fs = o.optJSONArray("folders");
      if (fs != null) {
        for (int i = 0; i < fs.length(); i++) {
          JSONObject j = fs.getJSONObject(i);
          Folder f = new Folder(j.getLong("id"), j.optString("name"), j.optInt("color"));
          f.onHome = j.optBoolean("home", true);
          JSONArray ids = j.optJSONArray("ids");
          if (ids != null) for (int k = 0; k < ids.length(); k++) f.messageIds.add(ids.getLong(k));
          folders.add(f);
          nextFolderId = Math.max(nextFolderId, f.id + 1);
        }
      }
      JSONObject tags = o.optJSONObject("tags");
      if (tags != null) {
        java.util.Iterator<String> keys = tags.keys();
        while (keys.hasNext()) {
          String k = keys.next();
          JSONArray ids = tags.getJSONArray(k);
          LinkedHashSet<Long> set = new LinkedHashSet<>();
          for (int i = 0; i < ids.length(); i++) set.add(ids.getLong(i));
          localTags.put(k, set);
        }
      }
      JSONArray home = o.optJSONArray("homeTags");
      if (home != null) for (int i = 0; i < home.length(); i++) homeTags.add(home.getString(i));
      sheetMode = o.optInt("sheet", SHEET_ALWAYS);
      openMode = o.optInt("open", OPEN_HOME);
      inboxTile = o.optBoolean("inbox", true);
      showPinned = o.optBoolean("pinned", true);
      hashtagOwn = o.optBoolean("hashtag", true);
    } catch (Throwable t) {
      org.thunderdog.challegram.Tgx101Diag.mark("saved store: load failed " + t.getClass().getSimpleName());
    }
  }

  /** All message ids filed in a folder or tagged by name here (for «Inbox» = saved but not filed) */
  public synchronized Set<Long> filedIds () {
    Set<Long> out = new java.util.HashSet<>();
    for (Folder f : folders) out.addAll(f.messageIds);
    return out;
  }
}
