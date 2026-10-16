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
    // TGx101 (approved mockup «Поделиться папкой»): the shared copy — a private channel / group, its invite link, what is
    // already copied there (to go on after a break and to add only what's new)
    public long shareChatId;
    public String shareLink;
    public boolean shareChannel = true, shareAuto = true, shareCopies = true, shareApproval;
    public final LinkedHashSet<Long> sharedIds = new LinkedHashSet<>();

    public boolean isShared () {
      return shareChatId != 0;
    }

    /** what is in the folder and not yet in its shared copy, oldest first */
    public List<Long> unshared () {
      List<Long> out = new ArrayList<>();
      for (long id : messageIds) if (!sharedIds.contains(id)) out.add(id);
      java.util.Collections.sort(out);
      return out;
    }

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
  @SuppressWarnings("FieldCanBeLocal")
  private static GlobalMessageListener listener;

  public static synchronized Tgx101SavedStore get (Tdlib tdlib) {
    Tgx101SavedStore store = instances.get(tdlib.id());
    if (store == null) {
      store = new Tgx101SavedStore(tdlib);
      instances.put(tdlib.id(), store);
    }
    ensureListener();
    return store;
  }

  /** From app start: the «Saved» banner has to see what is saved before Saved is first opened */
  public static synchronized void ensureListener () {
    if (!listenerAdded) {
      listenerAdded = true;
      org.thunderdog.challegram.Tgx101Diag.mark("saved store: listening");
      // the global list keeps listeners by weak reference: without this field the listener was collected at once
      // (test 2026-10-15: no banner, no id upkeep)
      TdlibManager.instance().global().addMessageListener(listener = new GlobalMessageListener() {
        @Override
        public void onMessageSendSucceeded (Tdlib tdlib, TdApi.Message message, long oldMessageId) {
          org.thunderdog.challegram.Tgx101Diag.mark("saved store: sent " + message.chatId + "/" + message.id + " (self " + tdlib.selfChatId() + ")");
          if (message.chatId == tdlib.selfChatId()) {
            get(tdlib).remap(oldMessageId, message.id);
            onSavedHere(tdlib, message); // only what this phone sent arrives here — already with its real id
          }
        }

        @Override
        public void onMessagesDeleted (Tdlib tdlib, long chatId, long[] messageIds) {
          if (chatId == tdlib.selfChatId()) get(tdlib).forget(messageIds);
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

  /**
   * TGx101 (test 2026-10-15: a message forwarded to Saved arrives ready, never «sent», so the update listeners can't
   * tell it was saved here): the places that save — share / forward sheet, swipe «Save», the menu — pass what Telegram
   * answered here. Several answers in a row (an album, several chats) make one banner.
   */
  public static void onSavedResult (Tdlib tdlib, TdApi.Object result) {
    if (result == null) return;
    if (result.getConstructor() == TdApi.Message.CONSTRUCTOR) {
      onSavedHere(tdlib, (TdApi.Message) result);
    } else if (result.getConstructor() == TdApi.Messages.CONSTRUCTOR) {
      for (TdApi.Message m : ((TdApi.Messages) result).messages) onSavedHere(tdlib, m);
    }
  }

  /** The chat a send / forward request goes to (0 for anything else) */
  public static long targetChatId (TdApi.Function<?> f) {
    switch (f.getConstructor()) {
      case TdApi.SendMessage.CONSTRUCTOR: return ((TdApi.SendMessage) f).chatId;
      case TdApi.SendMessageAlbum.CONSTRUCTOR: return ((TdApi.SendMessageAlbum) f).chatId;
      case TdApi.ForwardMessages.CONSTRUCTOR: return ((TdApi.ForwardMessages) f).chatId;
    }
    return 0;
  }

  /** {@code base} for any request; one to Saved Messages also reports what was saved (the banner) */
  public static org.drinkless.tdlib.Client.ResultHandler savedAware (Tdlib tdlib, TdApi.Function<?> f, org.drinkless.tdlib.Client.ResultHandler base) {
    if (targetChatId(f) != tdlib.selfChatId() || tdlib.selfChatId() == 0) return base;
    return object -> {
      base.onResult(object);
      onSavedResult(tdlib, object);
    };
  }

  private static void onSavedHere (Tdlib tdlib, TdApi.Message m) {
    org.thunderdog.challegram.Tgx101Diag.mark("saved store: saved here " + (m != null ? m.id : 0) + (m != null && m.schedulingState != null ? " scheduled" : ""));
    if (m == null || m.chatId != tdlib.selfChatId() || m.schedulingState != null) return;
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
  /** set by the folder sharing: a shared folder got something new */
  public static me.vkryl.core.lambda.RunnableData<long[]> onShareChanged;

  public synchronized void markShared (long folderId, long[] ids) {
    Folder f = folder(folderId);
    if (f == null) return;
    for (long id : ids) f.sharedIds.add(id);
    save();
  }

  public synchronized void setShare (long folderId, long chatId, @Nullable String link) {
    Folder f = folder(folderId);
    if (f == null) return;
    f.shareChatId = chatId;
    f.shareLink = link;
    if (chatId == 0) f.sharedIds.clear();
    save();
  }
  private final List<Folder> folders = new ArrayList<>();
  /** tag name (without #, lower case) → message ids tagged by name on this phone */
  private final Map<String, LinkedHashSet<Long>> localTags = new LinkedHashMap<>();
  /** tags shown as tiles on the home screen */
  private final LinkedHashSet<String> homeTags = new LinkedHashSet<>();
  private long nextFolderId = 1;
  /** reaction (emoji) → message ids marked with it on this phone (Telegram's own reaction tags need Premium) */
  private final Map<String, LinkedHashSet<Long>> localReactions = new LinkedHashMap<>();
  /** where the swipe buttons on «Recent» put a message with one tap */
  public long lastFolderId;
  public String lastTag;

  public int sheetMode = SHEET_ALWAYS;
  public int openMode = OPEN_HOME;
  public boolean inboxTile = true;
  public boolean showPinned = true;
  public boolean hashtagOwn = true;
  /** words on the selection panel's buttons (off: signs only) */
  public boolean panelLabels = true;
  /** (user 2026-10-16) off: Saved Messages is the usual chat — no storage screen, button or pull to it; the folders stay */
  public boolean storageOn = true;
  /** the round «chat ↔ storage» button (approved mockup 8 А) */
  public boolean switchButton = true;
  /** where the button was put, apart for the chat [0] and the storage [1]: the side and the height (0…1 of the free room, −1: the usual place) */
  public final boolean[] switchLeft = new boolean[2];
  public final float[] switchY = {-1f, -1f};

  /** no «Saved» banner after saving: turned off, or the storage itself is */
  public boolean bannerOff () {
    return !storageOn || sheetMode == SHEET_NEVER;
  }

  /** Saved Messages opens as the storage */
  public boolean opensStorage () {
    return storageOn && openMode == OPEN_HOME;
  }

  /** Kept here: the listener lists hold their listeners weakly */
  private org.thunderdog.challegram.telegram.MessageListener chatListener;

  private Tgx101SavedStore (Tdlib tdlib) {
    this.accountId = tdlib.id();
    load();
    // (test 2026-10-15: the app-wide message listener never got the «sent» update) — the Saved chat's own updates,
    // the path the chat screens use
    final long selfChatId = tdlib.selfChatId();
    if (selfChatId != 0) {
      chatListener = new org.thunderdog.challegram.telegram.MessageListener() {
        @Override
        public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
          org.thunderdog.challegram.Tgx101Diag.mark("saved store: sent " + message.id);
          remap(oldMessageId, message.id);
          onSavedHere(tdlib, message);
        }

        @Override
        public void onMessagesDeleted (long chatId, long[] messageIds) {
          forget(messageIds);
        }
      };
      tdlib.listeners().subscribeToMessageUpdates(selfChatId, chatListener);
      org.thunderdog.challegram.Tgx101Diag.mark("saved store: listening to " + selfChatId);
    }
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
    lastFolderId = folderId;
    save();
    if (target.isShared() && target.shareAuto && onShareChanged != null) onShareChanged.runWithData(new long[] {accountId, folderId}); // «add new from the folder»
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
    lastTag = t;
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
    if (!t.isEmpty() && !t.equals(lastTag)) {
      lastTag = t;
      save();
    }
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

  // Reactions kept on this phone

  /** Marks (or, if all of them have it, unmarks) the messages; true when it was added */
  public synchronized boolean toggleLocalReaction (String emoji, long[] messageIds) {
    LinkedHashSet<Long> set = localReactions.get(emoji);
    if (set == null) {
      set = new LinkedHashSet<>();
      localReactions.put(emoji, set);
    }
    boolean all = true;
    for (long id : messageIds) if (!set.contains(id)) all = false;
    for (long id : messageIds) {
      if (all) set.remove(id);
      else set.add(id);
    }
    if (set.isEmpty()) localReactions.remove(emoji);
    save();
    return !all;
  }

  public synchronized List<Long> localReacted (String emoji) {
    LinkedHashSet<Long> set = localReactions.get(emoji);
    return set != null ? new ArrayList<>(set) : new ArrayList<>();
  }

  /** emoji → how many messages carry it here */
  public synchronized LinkedHashMap<String, Integer> localReactionCounts () {
    LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
    for (Map.Entry<String, LinkedHashSet<Long>> e : localReactions.entrySet()) out.put(e.getKey(), e.getValue().size());
    return out;
  }

  public synchronized List<String> reactionsOf (long messageId) {
    List<String> out = new ArrayList<>();
    for (Map.Entry<String, LinkedHashSet<Long>> e : localReactions.entrySet()) if (e.getValue().contains(messageId)) out.add(e.getKey());
    return out;
  }

  public synchronized List<String> tagsOf (long messageId) {
    List<String> out = new ArrayList<>();
    for (Map.Entry<String, LinkedHashSet<Long>> e : localTags.entrySet()) {
      if (e.getValue().contains(messageId)) out.add(e.getKey());
    }
    return out;
  }

  // Message id upkeep

  /** old (sending) id → real id, for what a banner announced before Telegram gave the message its real id */
  private final LinkedHashMap<Long, Long> recentRemaps = new LinkedHashMap<Long, Long>() {
    @Override
    protected boolean removeEldestEntry (Map.Entry<Long, Long> eldest) {
      return size() > 200;
    }
  };

  /** The real ids of these (a banner may hold the ids a message had while it was being sent), without repeats */
  public synchronized long[] resolve (long[] ids) {
    LinkedHashSet<Long> out = new LinkedHashSet<>();
    for (long id : ids) {
      Long real = recentRemaps.get(id);
      out.add(real != null ? real : id);
    }
    long[] arr = new long[out.size()];
    int i = 0;
    for (long id : out) arr[i++] = id;
    return arr;
  }

  synchronized void remap (long oldId, long newId) {
    if (oldId != 0 && oldId != newId) recentRemaps.put(oldId, newId);
    boolean changed = false;
    for (Folder f : folders) {
      if (f.messageIds.remove(oldId)) {
        f.messageIds.add(newId);
        changed = true;
      }
      if (f.sharedIds.remove(oldId)) f.sharedIds.add(newId);
    }
    for (LinkedHashSet<Long> set : localTags.values()) {
      if (set.remove(oldId)) {
        set.add(newId);
        changed = true;
      }
    }
    for (LinkedHashSet<Long> set : localReactions.values()) {
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
      for (LinkedHashSet<Long> set : localReactions.values()) changed |= set.remove(id);
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
        if (f.shareChatId != 0) {
          JSONObject sh = new JSONObject();
          sh.put("chat", f.shareChatId);
          if (f.shareLink != null) sh.put("link", f.shareLink);
          sh.put("channel", f.shareChannel);
          sh.put("auto", f.shareAuto);
          sh.put("copies", f.shareCopies);
          sh.put("approval", f.shareApproval);
          JSONArray done = new JSONArray();
          for (long id : f.sharedIds) done.put(id);
          sh.put("done", done);
          j.put("share", sh);
        }
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
      JSONObject reactions = new JSONObject();
      for (Map.Entry<String, LinkedHashSet<Long>> e : localReactions.entrySet()) {
        JSONArray ids = new JSONArray();
        for (long id : e.getValue()) ids.put(id);
        reactions.put(e.getKey(), ids);
      }
      o.put("reactions", reactions);
      o.put("lastFolder", lastFolderId);
      if (lastTag != null) o.put("lastTag", lastTag);
      JSONArray home = new JSONArray();
      for (String t : homeTags) home.put(t);
      o.put("homeTags", home);
      o.put("sheet", sheetMode);
      o.put("open", openMode);
      o.put("inbox", inboxTile);
      o.put("pinned", showPinned);
      o.put("hashtag", hashtagOwn);
      o.put("panelLabels", panelLabels);
      o.put("storageOn", storageOn);
      o.put("switchButton", switchButton);
      for (int i = 0; i < 2; i++) {
        o.put("switchLeft" + i, switchLeft[i]);
        o.put("switchY" + i, (double) switchY[i]);
      }
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
          JSONObject sh = j.optJSONObject("share");
          if (sh != null) {
            f.shareChatId = sh.optLong("chat");
            f.shareLink = sh.optString("link", null);
            f.shareChannel = sh.optBoolean("channel", true);
            f.shareAuto = sh.optBoolean("auto", true);
            f.shareCopies = sh.optBoolean("copies", true);
            f.shareApproval = sh.optBoolean("approval", false);
            JSONArray done = sh.optJSONArray("done");
            if (done != null) for (int k = 0; k < done.length(); k++) f.sharedIds.add(done.getLong(k));
          }
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
      JSONObject reactions = o.optJSONObject("reactions");
      if (reactions != null) {
        java.util.Iterator<String> keys = reactions.keys();
        while (keys.hasNext()) {
          String k = keys.next();
          JSONArray ids = reactions.getJSONArray(k);
          // «❤️» kept by the first test build is Telegram's «❤»
          String key = k.replace("\uFE0F", "");
          LinkedHashSet<Long> set = localReactions.get(key);
          if (set == null) {
            set = new LinkedHashSet<>();
            localReactions.put(key, set);
          }
          for (int i = 0; i < ids.length(); i++) set.add(ids.getLong(i));
        }
      }
      lastFolderId = o.optLong("lastFolder", 0);
      lastTag = o.optString("lastTag", null);
      JSONArray home = o.optJSONArray("homeTags");
      if (home != null) for (int i = 0; i < home.length(); i++) homeTags.add(home.getString(i));
      sheetMode = o.optInt("sheet", SHEET_ALWAYS);
      openMode = o.optInt("open", OPEN_HOME);
      inboxTile = o.optBoolean("inbox", true);
      showPinned = o.optBoolean("pinned", true);
      hashtagOwn = o.optBoolean("hashtag", true);
      panelLabels = o.optBoolean("panelLabels", true);
      storageOn = o.optBoolean("storageOn", true);
      switchButton = o.optBoolean("switchButton", true);
      for (int i = 0; i < 2; i++) {
        switchLeft[i] = o.optBoolean("switchLeft" + i, false);
        switchY[i] = (float) o.optDouble("switchY" + i, -1d);
      }
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
