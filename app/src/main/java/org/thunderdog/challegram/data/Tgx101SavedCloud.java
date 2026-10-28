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
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * TGx101 (user 2026-10-29: the folders of the storage are one for the app and the web version): the data part of
 * {@link Tgx101SavedStore} lives in the cloud too — one service file in Saved Messages, «PlumaGram-Хранилище.json»,
 * that the web version (plumagram-web, storageCloud.ts) reads and writes the same way:
 *   {app: "PlumaGram", kind: "storage", v: 1, updated: <ms>, by: "android" | "web", next,
 *    folders: [{id, name, color, home, size?, ids, share?}], tags: {tag: [ids]}, homeTags: [tags]}
 * Message ids are TDLib's. The file is replaced in its own message (never a second one), silently; fields this side
 * doesn't know are kept. Merging is three-way, against what was last in sync: what either side added stays, what
 * either side took out goes; a changed name / colour comes from the side that changed it.
 */
public final class Tgx101SavedCloud {
  public static final String FILE_NAME = "PlumaGram-Хранилище.json";
  private static final String CAPTION = "Служебный файл PlumaGram: папки Хранилища для приложения и веб-версии. Не удаляйте.";
  /** the folder fields merged one by one (the rest of a folder is taken whole from the cloud) */
  private static final String[] FOLDER_FIELDS = {"name", "color", "home", "size", "share"};

  /** the largest file taken (the web version's limit too) */
  private static final long MAX_BYTES = 512 * 1024;
  private static final int MAX_FOLDERS = 500, MAX_IDS = 10_000, MAX_TAGS = 2_000, MAX_NAME = 256;

  /** (user 2026-10-29 «защита») only this account's own file counts: one forwarded into Saved Messages (sent by someone
   *  else) is an ordinary file — never read, never hidden */
  public static boolean isCloudFile (@Nullable TdApi.Message m) {
    return m != null && m.content != null && m.content.getConstructor() == TdApi.MessageDocument.CONSTRUCTOR
      && m.forwardInfo == null && m.isOutgoing && m.importInfo == null
      && FILE_NAME.equals(((TdApi.MessageDocument) m.content).document.fileName);
  }

  private static final Map<Integer, Tgx101SavedCloud> instances = new HashMap<>();

  private static synchronized Tgx101SavedCloud get (Tgx101SavedStore store) {
    Tgx101SavedCloud c = instances.get(store.tdlib.id());
    if (c == null) {
      c = new Tgx101SavedCloud(store);
      instances.put(store.tdlib.id(), c);
    }
    return c;
  }

  /** The storage was opened: what the cloud has now, at most every 20 s; {@code onChanged} when the folders here changed */
  public static void pull (Tdlib tdlib, @Nullable Runnable onChanged) {
    Tgx101SavedCloud c = get(Tgx101SavedStore.get(tdlib));
    synchronized (c) {
      if (onChanged != null) c.onChanged = onChanged;
      long now = android.os.SystemClock.uptimeMillis();
      if (c.pulledAt != 0 && now - c.pulledAt < 20_000) return;
      c.pulledAt = now;
    }
    c.sync();
  }

  /** Something was saved here: the data part changed — written to the cloud a moment later (several changes, one file) */
  static void changed (Tgx101SavedStore store) {
    if (store.tdlib.selfChatId() == 0) return;
    Tgx101SavedCloud c = get(store);
    String canon;
    try {
      canon = canon(store.dataJson());
    } catch (Throwable t) {
      return;
    }
    synchronized (c) {
      if (canon.equals(c.pmc().getString(c.key("local"), null))) return; // only the look or «last used» changed
      c.pmc().putString(c.key("local"), canon);
      c.pmc().putLong(c.key("localAt"), System.currentTimeMillis());
    }
    UI.removePendingRunnable(c.delayed);
    UI.post(c.delayed, 2500);
  }

  private final Tgx101SavedStore store;
  private final Tdlib tdlib;
  private final Runnable delayed = this::sync;
  private @Nullable Runnable onChanged;
  private long pulledAt, sentAt;
  private boolean running, again;

  private Tgx101SavedCloud (Tgx101SavedStore store) {
    this.store = store;
    this.tdlib = store.tdlib;
  }

  private me.vkryl.leveldb.LevelDB pmc () {
    return Settings.instance().pmc();
  }

  private String key (String what) {
    return "tgx101_storage_cloud_" + what + "_" + tdlib.id();
  }

  // Sync: find the file → download → merge → apply here → write back what the cloud lacks

  private void sync () {
    synchronized (this) {
      if (running) {
        again = true;
        return;
      }
      running = true;
      again = false;
    }
    if (tdlib.selfChatId() == 0) {
      done();
      return;
    }
    find(message -> {
      boolean sending = message != null ? message.sendingState instanceof TdApi.MessageSendingStatePending : android.os.SystemClock.uptimeMillis() - sentAt < 120_000;
      if (sending) { // the file sent a moment ago is still on its way: no second one
        synchronized (this) { again = true; }
        UI.post(this::done, 5000);
        return;
      }
      if (message == null) {
        merge(null, null);
        return;
      }
      TdApi.File file = ((TdApi.MessageDocument) message.content).document.document;
      if (Math.max(file.size, file.expectedSize) > MAX_BYTES) { // not ours to read: this phone's copy takes its place
        Tgx101Diag.mark("storage cloud: file too big (" + Math.max(file.size, file.expectedSize) + " bytes), rejected");
        merge(message, null);
        return;
      }
      tdlib.send(new TdApi.DownloadFile(file.id, 16, 0, 0, true), (downloaded, error) -> {
        JSONObject cloud = null;
        if (downloaded != null && downloaded.local != null && downloaded.local.isDownloadingCompleted) {
          try {
            File f = new File(downloaded.local.path);
            if (f.length() > MAX_BYTES) throw new IllegalStateException("too big");
            cloud = new JSONObject(read(f));
            String bad = invalid(cloud);
            if (bad != null) {
              Tgx101Diag.mark("storage cloud: file rejected — " + bad);
              cloud = null;
            }
          } catch (Throwable t) {
            Tgx101Diag.mark("storage cloud: unreadable file " + t.getClass().getSimpleName());
            cloud = null;
          }
        } else {
          Tgx101Diag.mark("storage cloud: download failed " + (error != null ? error.message : "?"));
          done(); // nothing written over a file that couldn't be read
          return;
        }
        merge(message, cloud);
      });
    });
  }

  private void done () {
    boolean more;
    synchronized (this) {
      running = false;
      more = again;
    }
    if (more) UI.post(this::sync);
  }

  /** The service file's message: the one known, else among the latest Saved messages, else by a search */
  private void find (me.vkryl.core.lambda.RunnableData<TdApi.Message> after) {
    long known = pmc().getLong(key("msg"), 0);
    if (known != 0) {
      tdlib.send(new TdApi.GetMessage(tdlib.selfChatId(), known), (m, error) -> {
        if (isCloudFile(m) && !(m.sendingState instanceof TdApi.MessageSendingStateFailed)) {
          after.runWithData(m);
        } else {
          pmc().remove(key("msg"));
          findAnew(after);
        }
      });
    } else {
      findAnew(after);
    }
  }

  private void findAnew (me.vkryl.core.lambda.RunnableData<TdApi.Message> after) {
    final long chatId = tdlib.selfChatId();
    tdlib.send(new TdApi.GetChatHistory(chatId, 0, 0, 100, false), (history, error) -> {
      TdApi.Message found = newest(history != null ? history.messages : null);
      if (found != null) {
        remember(found);
        after.runWithData(found);
        return;
      }
      // the search sees a file sent a moment ago only some time later — hence the history first
      tdlib.send(new TdApi.SearchChatMessages(chatId, null, "PlumaGram", null, 0, 0, 50, new TdApi.SearchMessagesFilterDocument()), (result, error2) -> {
        if (result == null) { // the search failed (offline): not «the file is gone» — nothing restored over it
          Tgx101Diag.mark("storage cloud: search failed " + (error2 != null ? error2.message : "?"));
          done();
          return;
        }
        TdApi.Message m = newest(result != null ? result.messages : null);
        if (m != null) remember(m);
        after.runWithData(m);
      });
    });
  }

  private static @Nullable TdApi.Message newest (@Nullable TdApi.Message[] messages) {
    TdApi.Message best = null;
    if (messages != null) {
      for (TdApi.Message m : messages) {
        if (isCloudFile(m) && !(m.sendingState instanceof TdApi.MessageSendingStateFailed) && (best == null || m.id > best.id)) best = m;
      }
    }
    return best;
  }

  private void remember (TdApi.Message m) {
    if (m.sendingState == null) pmc().putLong(key("msg"), m.id);
  }

  private void merge (@Nullable TdApi.Message message, @Nullable JSONObject cloud) {
    try {
      String baseRaw = pmc().getString(key("base"), null);
      JSONObject base = baseRaw != null ? new JSONObject(baseRaw) : null;
      long baseMsg = pmc().getLong(key("baseMsg"), 0);
      if (base != null && message != null && baseMsg != 0 && baseMsg != message.id) {
        // another file than the one last in sync (made anew after it was deleted): nothing in it counts as «taken out»
        Tgx101Diag.mark("storage cloud: a new file " + message.id + " (was " + baseMsg + "): merged as a union");
        base = null;
      }
      JSONObject local = store.dataJson();
      long localAt = pmc().getLong(key("localAt"), 0);
      boolean cloudNewer = cloud != null && cloud.optLong("updated") > localAt;
      JSONObject merged = cloud != null ? merge3(base, local, cloud, cloudNewer) : local;
      checkShares(merged, local, () -> finish(message, cloud, local, merged));
    } catch (Throwable t) {
      Tgx101Diag.mark("storage cloud: merge failed " + t);
      done();
    }
  }

  /**
   * (user 2026-10-29 «защита») «Share folder» from the cloud file is taken only for a chat this account made: a file
   * changed by someone else would otherwise send the folder's records to their channel. Not made here: this phone's
   * own share stays (or none).
   */
  private void checkShares (JSONObject merged, JSONObject local, Runnable after) throws Exception {
    LinkedHashMap<Long, JSONObject> localFolders = foldersById(local);
    JSONArray fs = merged.optJSONArray("folders");
    List<JSONObject> toCheck = new ArrayList<>();
    if (fs != null) {
      for (int i = 0; i < fs.length(); i++) {
        JSONObject f = fs.getJSONObject(i);
        JSONObject lf = localFolders.get(f.optLong("id"));
        Object share = f.opt("share"), had = lf != null ? lf.opt("share") : null;
        if (share != null && !same(share, had)) toCheck.add(f);
      }
    }
    if (toCheck.isEmpty()) {
      after.run();
      return;
    }
    final int[] left = {toCheck.size()};
    for (JSONObject f : toCheck) {
      JSONObject lf = localFolders.get(f.optLong("id"));
      long chatId = f.optJSONObject("share") != null ? f.optJSONObject("share").optLong("chat") : 0;
      Runnable one = () -> {
        boolean last;
        synchronized (left) { last = --left[0] == 0; }
        if (last) after.run();
      };
      if (chatId == 0) {
        keepLocalShare(f, lf);
        one.run();
        continue;
      }
      tdlib.send(new TdApi.GetChatMember(chatId, new TdApi.MessageSenderUser(tdlib.myUserId())), (member, error) -> {
        boolean creator = member != null && member.status != null && member.status.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR;
        if (!creator) {
          Tgx101Diag.mark("storage cloud: share of folder " + f.optLong("id") + " not taken — chat " + chatId + " isn't this account's");
          synchronized (left) { keepLocalShare(f, lf); }
        }
        one.run();
      });
    }
  }

  private static void keepLocalShare (JSONObject f, @Nullable JSONObject lf) {
    try {
      Object had = lf != null ? lf.opt("share") : null;
      if (had != null) f.put("share", had); else f.remove("share");
    } catch (Throwable ignored) { }
  }

  private void finish (@Nullable TdApi.Message message, @Nullable JSONObject cloud, JSONObject local, JSONObject merged) {
    try {
      String localCanon = canon(local);
      String mergedCanon = canon(merged);

      if (!mergedCanon.equals(localCanon)) {
        synchronized (store) {
          if (!canon(store.dataJson()).equals(localCanon)) { // changed here meanwhile: once more, from the start
            synchronized (this) { again = true; }
            done();
            return;
          }
          store.applyData(merged);
        }
        pmc().putString(key("local"), mergedCanon);
        Tgx101Diag.mark("storage cloud: merged from the cloud (" + merged.optJSONArray("folders").length() + " folders)");
        Runnable r = onChanged;
        if (r != null) UI.post(r);
      }

      JSONArray fs = merged.optJSONArray("folders");
      JSONObject tags = merged.optJSONObject("tags");
      if (cloud == null && (fs == null || fs.length() == 0) && (tags == null || tags.length() == 0)) {
        done(); // nothing to keep yet: no file in Saved Messages for it
        return;
      }
      if (cloud != null) {
        pmc().putBoolean(key("had"), true);
        if (message != null) pmc().putLong(key("baseMsg"), message.id);
      }
      if (cloud != null && mergedCanon.equals(canon(cloud))) {
        pmc().putString(key("base"), cloud.toString());
        done();
        return;
      }
      // (user 2026-10-29 «восстановление») the file was there and is gone: sent anew from this phone's copy
      boolean restoring = message == null && pmc().getBoolean(key("had"), false);
      write(message, cloud, merged, restoring);
    } catch (Throwable t) {
      Tgx101Diag.mark("storage cloud: merge failed " + t);
      done();
    }
  }

  /** The file replaced in its message, or sent the first time */
  private void write (@Nullable TdApi.Message message, @Nullable JSONObject cloud, JSONObject merged, boolean restoring) throws Exception {
    JSONObject out = cloud != null ? new JSONObject(cloud.toString()) : new JSONObject();
    Iterator<String> keys = merged.keys();
    while (keys.hasNext()) {
      String k = keys.next();
      out.put(k, merged.get(k));
    }
    out.put("app", "PlumaGram");
    out.put("kind", "storage");
    out.put("v", 1);
    out.put("updated", System.currentTimeMillis());
    out.put("by", "android");

    File root = new File(org.thunderdog.challegram.unsorted.AppContext.get().getFilesDir(), "tgx101_storage_cloud");
    File[] old = root.listFiles();
    if (old != null) for (File d : old) if (System.currentTimeMillis() - d.lastModified() > 10 * 60_000L) deleteDir(d);
    // its own folder each time: the file keeps its name, and TDLib never takes it for the one sent before
    File dir = new File(root, tdlib.id() + "_" + System.currentTimeMillis());
    if (!dir.mkdirs()) throw new IllegalStateException("can't create " + dir);
    File file = new File(dir, FILE_NAME);
    try (OutputStream os = new FileOutputStream(file)) {
      os.write(out.toString(1).getBytes("UTF-8"));
    }
    TdApi.InputMessageContent content = new TdApi.InputMessageDocument(
      new TdApi.InputDocument(TD.createInputFile(file.getPath()), null, true),
      new TdApi.FormattedText(CAPTION, new TdApi.TextEntity[0]));
    final String written = out.toString();
    if (message != null) {
      tdlib.send(new TdApi.EditMessageMedia(message.chatId, message.id, null, content), (result, error) -> {
        if (error != null) {
          Tgx101Diag.mark("storage cloud: replacing the file failed — " + error.message);
          if (error.message != null && error.message.contains("MESSAGE_ID_INVALID")) pmc().remove(key("msg"));
        } else {
          pmc().putString(key("base"), written);
          pmc().putLong(key("baseMsg"), message.id);
          pmc().putBoolean(key("had"), true);
          Tgx101Diag.mark("storage cloud: file replaced in " + message.id);
        }
        done();
      });
    } else {
      TdApi.MessageSendOptions options = new TdApi.MessageSendOptions();
      options.disableNotification = true;
      Tgx101SavedStore.suppressBanner(10_000);
      sentAt = android.os.SystemClock.uptimeMillis();
      tdlib.send(new TdApi.SendMessage(tdlib.selfChatId(), null, null, options, null, content), (result, error) -> {
        if (error != null) {
          Tgx101Diag.mark("storage cloud: sending the file failed — " + error.message);
        } else {
          // the id is the sending one for now: the next sync finds the message again
          pmc().putString(key("base"), written);
          pmc().putLong(key("baseMsg"), 0); // its real id comes later: the next file found is this one
          pmc().putBoolean(key("had"), true);
          Tgx101Diag.mark("storage cloud: file sent" + (restoring ? " (restored)" : ""));
          if (restoring) UI.showToast(org.thunderdog.challegram.R.string.Tgx101StorageRestored, android.widget.Toast.LENGTH_LONG);
        }
        done();
      });
    }
  }

  // Checking what came from the cloud

  /** Why the file can't be taken (null: it can) — the wrong types, too much of something */
  static @Nullable String invalid (JSONObject o) {
    Object fsRaw = o.opt("folders");
    if (fsRaw != null && !(fsRaw instanceof JSONArray)) return "folders not a list";
    JSONArray fs = (JSONArray) fsRaw;
    if (fs != null) {
      if (fs.length() > MAX_FOLDERS) return fs.length() + " folders";
      for (int i = 0; i < fs.length(); i++) {
        Object fr = fs.opt(i);
        if (!(fr instanceof JSONObject)) return "folder " + i + " not an object";
        JSONObject f = (JSONObject) fr;
        if (!isWhole(f.opt("id")) || ((Number) f.opt("id")).longValue() <= 0) return "folder " + i + ": id";
        Object name = f.opt("name");
        if (!(name instanceof String) || ((String) name).length() > MAX_NAME) return "folder " + i + ": name";
        if (f.has("color") && !isWhole(f.opt("color"))) return "folder " + i + ": color";
        if (f.has("size") && !isWhole(f.opt("size"))) return "folder " + i + ": size";
        if (f.has("home") && !(f.opt("home") instanceof Boolean)) return "folder " + i + ": home";
        String ids = idList(f.opt("ids"), true);
        if (ids != null) return "folder " + i + ": ids " + ids;
        Object share = f.opt("share");
        if (share != null && share != JSONObject.NULL) {
          if (!(share instanceof JSONObject)) return "folder " + i + ": share";
          JSONObject sh = (JSONObject) share;
          if (!isWhole(sh.opt("chat"))) return "folder " + i + ": share chat";
          if (sh.has("link") && !(sh.opt("link") instanceof String)) return "folder " + i + ": share link";
          String done = idList(sh.opt("done"), false);
          if (done != null) return "folder " + i + ": share done " + done;
        }
      }
    }
    Object tagsRaw = o.opt("tags");
    if (tagsRaw != null) {
      if (!(tagsRaw instanceof JSONObject)) return "tags not an object";
      JSONObject tags = (JSONObject) tagsRaw;
      if (tags.length() > MAX_TAGS) return tags.length() + " tags";
      Iterator<String> keys = tags.keys();
      while (keys.hasNext()) {
        String k = keys.next();
        if (k.length() > MAX_NAME) return "tag name";
        String ids = idList(tags.opt(k), true);
        if (ids != null) return "tag " + k + ": " + ids;
      }
    }
    Object home = o.opt("homeTags");
    if (home != null) {
      if (!(home instanceof JSONArray) || ((JSONArray) home).length() > MAX_TAGS) return "homeTags";
      for (int i = 0; i < ((JSONArray) home).length(); i++) if (!(((JSONArray) home).opt(i) instanceof String)) return "homeTags " + i;
    }
    if (o.has("next") && !isWhole(o.opt("next"))) return "next";
    if (o.has("updated") && !(o.opt("updated") instanceof Number)) return "updated";
    return null;
  }

  private static @Nullable String idList (@Nullable Object v, boolean required) {
    if (v == null) return required ? "missing" : null;
    if (!(v instanceof JSONArray)) return "not a list";
    JSONArray a = (JSONArray) v;
    if (a.length() > MAX_IDS) return a.length() + " ids";
    for (int i = 0; i < a.length(); i++) if (!isWhole(a.opt(i))) return "not a number at " + i;
    return null;
  }

  private static boolean isWhole (@Nullable Object v) {
    if (v instanceof Integer || v instanceof Long) return true;
    if (v instanceof Number) {
      double d = ((Number) v).doubleValue();
      return d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 9e15;
    }
    return false;
  }

  // Merging

  /** base: what was last in sync (null: never, so everything on both sides stays) */
  static JSONObject merge3 (@Nullable JSONObject base, JSONObject local, JSONObject cloud, boolean cloudNewer) throws Exception {
    JSONObject out = new JSONObject();
    LinkedHashMap<Long, JSONObject> b = foldersById(base), l = foldersById(local), c = foldersById(cloud);

    // the same number given to two different folders, one here, one in the cloud (made apart): the one here gets a new one
    long next = Math.max(Math.max(local.optLong("next", 1), cloud.optLong("next", 1)), 1);
    for (long id : maxIds(l, c)) next = Math.max(next, id + 1);
    LinkedHashMap<Long, JSONObject> lFixed = new LinkedHashMap<>();
    for (Map.Entry<Long, JSONObject> e : l.entrySet()) {
      JSONObject lf = e.getValue(), cf = c.get(e.getKey());
      if (cf != null && !b.containsKey(e.getKey()) && !lf.optString("name").trim().equalsIgnoreCase(cf.optString("name").trim())) {
        long id = next++;
        lf = new JSONObject(lf.toString());
        lf.put("id", id);
        lFixed.put(id, lf);
      } else {
        lFixed.put(e.getKey(), lf);
      }
    }
    l = lFixed;

    List<Long> order = new ArrayList<>();
    LinkedHashMap<Long, JSONObject> first = cloudNewer ? c : l, second = cloudNewer ? l : c;
    order.addAll(first.keySet());
    for (long id : second.keySet()) if (!order.contains(id)) order.add(id);

    JSONArray folders = new JSONArray();
    for (long id : order) {
      JSONObject bf = b.get(id), lf = l.get(id), cf = c.get(id);
      if (lf == null || cf == null) {
        if (bf != null) continue; // was there before and one side took it away
        folders.put(lf != null ? lf : cf);
        continue;
      }
      JSONObject f = new JSONObject(cf.toString()); // fields only the cloud knows stay
      for (String k : FOLDER_FIELDS) {
        boolean localChanged = bf != null ? !same(bf.opt(k), lf.opt(k)) : !cloudNewer;
        boolean cloudChanged = bf != null && !same(bf.opt(k), cf.opt(k));
        Object v = localChanged && !(cloudChanged && cloudNewer) ? lf.opt(k) : cf.opt(k);
        if (v == null) f.remove(k); else f.put(k, v);
      }
      f.put("ids", mergeList(bf != null ? bf.optJSONArray("ids") : null, lf.optJSONArray("ids"), cf.optJSONArray("ids")));
      folders.put(f);
    }
    out.put("next", next);
    out.put("folders", folders);

    JSONObject bt = base != null ? base.optJSONObject("tags") : null, lt = local.optJSONObject("tags"), ct = cloud.optJSONObject("tags");
    JSONObject tags = new JSONObject();
    LinkedHashSet<String> tagNames = new LinkedHashSet<>();
    addKeys(tagNames, lt);
    addKeys(tagNames, ct);
    for (String t : tagNames) {
      boolean inL = lt != null && lt.has(t), inC = ct != null && ct.has(t), inB = bt != null && bt.has(t);
      if (!(inL && inC) && inB) continue;
      tags.put(t, mergeList(inB ? bt.optJSONArray(t) : null, inL ? lt.optJSONArray(t) : null, inC ? ct.optJSONArray(t) : null));
    }
    out.put("tags", tags);
    out.put("homeTags", mergeList(base != null ? base.optJSONArray("homeTags") : null, local.optJSONArray("homeTags"), cloud.optJSONArray("homeTags")));
    return out;
  }

  private static List<Long> maxIds (Map<Long, JSONObject> a, Map<Long, JSONObject> b) {
    List<Long> out = new ArrayList<>(a.keySet());
    out.addAll(b.keySet());
    return out;
  }

  private static LinkedHashMap<Long, JSONObject> foldersById (@Nullable JSONObject o) {
    LinkedHashMap<Long, JSONObject> out = new LinkedHashMap<>();
    JSONArray fs = o != null ? o.optJSONArray("folders") : null;
    if (fs != null) {
      for (int i = 0; i < fs.length(); i++) {
        JSONObject f = fs.optJSONObject(i);
        if (f != null && f.has("id")) out.put(f.optLong("id"), f);
      }
    }
    return out;
  }

  private static void addKeys (LinkedHashSet<String> out, @Nullable JSONObject o) {
    if (o == null) return;
    Iterator<String> keys = o.keys();
    while (keys.hasNext()) out.add(keys.next());
  }

  /** what both have, plus what either added since the base; what either took out of the base goes */
  private static JSONArray mergeList (@Nullable JSONArray base, @Nullable JSONArray local, @Nullable JSONArray cloud) {
    LinkedHashSet<Object> b = items(base), l = items(local), c = items(cloud);
    JSONArray out = new JSONArray();
    LinkedHashSet<Object> all = new LinkedHashSet<>(l);
    all.addAll(c);
    for (Object v : all) {
      boolean inL = l.contains(v), inC = c.contains(v);
      if ((inL && inC) || !b.contains(v)) out.put(v);
    }
    return out;
  }

  /** numbers as longs (the web writes 1, the phone 1L — the same id) */
  private static LinkedHashSet<Object> items (@Nullable JSONArray a) {
    LinkedHashSet<Object> out = new LinkedHashSet<>();
    if (a != null) {
      for (int i = 0; i < a.length(); i++) {
        Object v = a.opt(i);
        out.add(v instanceof Number ? (Object) ((Number) v).longValue() : v);
      }
    }
    return out;
  }

  private static boolean same (@Nullable Object a, @Nullable Object b) {
    return String.valueOf(norm(a)).equals(String.valueOf(norm(b)));
  }

  private static Object norm (@Nullable Object v) {
    if (v == null || v == JSONObject.NULL) return null;
    if (v instanceof Number) {
      double d = ((Number) v).doubleValue();
      return d == Math.rint(d) ? (Object) (long) d : (Object) d;
    }
    if (v instanceof JSONObject) {
      JSONObject o = (JSONObject) v;
      List<String> keys = new ArrayList<>();
      addAll(keys, o.keys());
      Collections.sort(keys);
      StringBuilder sb = new StringBuilder("{");
      for (String k : keys) sb.append(k).append('=').append(norm(o.opt(k))).append(';');
      return sb.append('}').toString();
    }
    if (v instanceof JSONArray) {
      JSONArray a = (JSONArray) v;
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < a.length(); i++) sb.append(norm(a.opt(i))).append(',');
      return sb.append(']').toString();
    }
    return v;
  }

  private static void addAll (List<String> out, Iterator<String> it) {
    while (it.hasNext()) out.add(it.next());
  }

  /** The data part, written the same way whatever the order and number types: to tell whether two are the same */
  static String canon (JSONObject o) throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("next=").append(o.optLong("next", 1)).append('\n');
    JSONArray fs = o.optJSONArray("folders");
    if (fs != null) {
      for (int i = 0; i < fs.length(); i++) {
        JSONObject f = fs.getJSONObject(i);
        sb.append("folder ").append(f.optLong("id")).append(' ').append(f.optString("name"))
          .append(" c").append(f.optInt("color")).append(" h").append(f.optBoolean("home", true)).append(" s").append(f.optInt("size", 0))
          .append(" share=").append(norm(f.opt("share")));
        List<Long> ids = new ArrayList<>();
        JSONArray a = f.optJSONArray("ids");
        if (a != null) for (int k = 0; k < a.length(); k++) ids.add(a.optLong(k));
        Collections.sort(ids);
        sb.append(" ids=").append(ids).append('\n');
      }
    }
    JSONObject tags = o.optJSONObject("tags");
    if (tags != null) {
      List<String> names = new ArrayList<>();
      addAll(names, tags.keys());
      Collections.sort(names);
      for (String t : names) {
        List<Long> ids = new ArrayList<>();
        JSONArray a = tags.optJSONArray(t);
        if (a != null) for (int k = 0; k < a.length(); k++) ids.add(a.optLong(k));
        Collections.sort(ids);
        sb.append("tag ").append(t).append(' ').append(ids).append('\n');
      }
    }
    sb.append("home=").append(norm(o.optJSONArray("homeTags")));
    return sb.toString();
  }

  // Files

  private static String read (File file) throws Exception {
    try (InputStream in = new FileInputStream(file)) {
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) > 0) bytes.write(buf, 0, n);
      return bytes.toString("UTF-8");
    }
  }

  private static void deleteDir (File d) {
    File[] in = d.listFiles();
    if (in != null) for (File f : in) f.delete();
    d.delete();
  }
}
