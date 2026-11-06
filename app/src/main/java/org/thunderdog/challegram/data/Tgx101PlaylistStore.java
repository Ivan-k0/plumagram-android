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
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * (user 2026-11-06 «сохранять поток как плейлист так, чтобы он не пропадал при смене устройства — отдельно») The player's own
 * playlists: lists of messages with music, no copies of them. Kept on the phone and in one service file of Saved Messages,
 * «PlumaGram-Плейлисты.json», replaced in its own message; another device of the account reads it and merges: a playlist
 * changed later wins, a removed one stays removed.
 */
public final class Tgx101PlaylistStore {
  public static final String FILE_NAME = "PlumaGram-Плейлисты.json";
  private static final String CAPTION = "Служебный файл PlumaGram: плейлисты плеера. Не удаляйте — плейлисты пропадут на всех устройствах.";
  private static final long MAX_BYTES = 512 * 1024;
  private static final int MAX_TRACKS = 1000;

  public static final class Playlist {
    public final long id;
    public String name;
    public long edited;
    public final List<long[]> tracks = new ArrayList<>(); // [chatId, messageId]

    Playlist (long id, String name, long edited) {
      this.id = id;
      this.name = name;
      this.edited = edited;
    }
  }

  private static final Map<Integer, Tgx101PlaylistStore> INSTANCES = new HashMap<>();

  public static synchronized Tgx101PlaylistStore get (Tdlib tdlib) {
    Tgx101PlaylistStore store = INSTANCES.get(tdlib.id());
    if (store == null) {
      store = new Tgx101PlaylistStore(tdlib);
      INSTANCES.put(tdlib.id(), store);
    }
    return store;
  }

  /** This account's own file: hidden from the Storage's lists like the folders' one */
  public static boolean isCloudFile (@Nullable TdApi.Message m) {
    return m != null && m.content != null && m.content.getConstructor() == TdApi.MessageDocument.CONSTRUCTOR
      && m.forwardInfo == null && m.isOutgoing && FILE_NAME.equals(((TdApi.MessageDocument) m.content).document.fileName);
  }

  private final Tdlib tdlib;
  private final LinkedHashMap<Long, Playlist> playlists = new LinkedHashMap<>();
  private final Map<Long, Long> removed = new HashMap<>(); // id → when
  private boolean syncing, syncAgain;

  private Tgx101PlaylistStore (Tdlib tdlib) {
    this.tdlib = tdlib;
    try {
      String saved = Settings.instance().tgx101Playlists(tdlib.id());
      if (saved != null) read(new JSONObject(saved), playlists, removed);
    } catch (Throwable ignored) { }
  }

  // Use

  public synchronized List<Playlist> list () {
    List<Playlist> list = new ArrayList<>(playlists.values());
    java.util.Collections.sort(list, (a, b) -> Long.compare(b.edited, a.edited));
    return list;
  }

  public Playlist add (String name, List<TdApi.Message> tracks) {
    Playlist playlist;
    synchronized (this) {
      long id = System.currentTimeMillis();
      while (playlists.containsKey(id) || removed.containsKey(id)) id++;
      playlist = new Playlist(id, name, System.currentTimeMillis());
      for (TdApi.Message track : tracks) {
        if (track.chatId != 0 && playlist.tracks.size() < MAX_TRACKS) playlist.tracks.add(new long[] {track.chatId, track.id});
      }
      playlists.put(id, playlist);
    }
    changed();
    return playlist;
  }

  public void rename (long id, String name) {
    synchronized (this) {
      Playlist playlist = playlists.get(id);
      if (playlist == null) return;
      playlist.name = name;
      playlist.edited = System.currentTimeMillis();
    }
    changed();
  }

  public void remove (long id) {
    synchronized (this) {
      if (playlists.remove(id) == null) return;
      removed.put(id, System.currentTimeMillis());
    }
    changed();
  }

  /** Its messages, in its order; those gone are left out */
  public void resolve (Playlist playlist, me.vkryl.core.lambda.RunnableData<List<TdApi.Message>> callback) {
    final LinkedHashMap<Long, List<Long>> byChat = new LinkedHashMap<>();
    for (long[] track : playlist.tracks) {
      List<Long> ids = byChat.get(track[0]);
      if (ids == null) byChat.put(track[0], ids = new ArrayList<>());
      ids.add(track[1]);
    }
    final Map<String, TdApi.Message> found = new HashMap<>();
    final int[] left = {byChat.size()};
    if (left[0] == 0) {
      callback.runWithData(new ArrayList<>());
      return;
    }
    for (Map.Entry<Long, List<Long>> entry : byChat.entrySet()) {
      long[] ids = new long[entry.getValue().size()];
      for (int i = 0; i < ids.length; i++) ids[i] = entry.getValue().get(i);
      tdlib.send(new TdApi.GetMessages(entry.getKey(), ids), (result, error) -> UI.post(() -> {
        if (result != null) {
          for (TdApi.Message message : result.messages) {
            if (message != null && tgx.td.Td.isAudio(message.content)) found.put(message.chatId + "_" + message.id, message);
          }
        }
        if (--left[0] == 0) {
          List<TdApi.Message> list = new ArrayList<>();
          for (long[] track : playlist.tracks) {
            TdApi.Message message = found.get(track[0] + "_" + track[1]);
            if (message != null) list.add(message);
          }
          callback.runWithData(list);
        }
      }));
    }
  }

  // Storing

  private void changed () {
    saveLocal();
    sync(null);
  }

  private synchronized void saveLocal () {
    try {
      Settings.instance().setTgx101Playlists(tdlib.id(), write().toString());
    } catch (Throwable ignored) { }
  }

  private synchronized JSONObject write () throws Exception {
    JSONObject o = new JSONObject();
    o.put("app", "PlumaGram");
    o.put("kind", "playlists");
    o.put("v", 1);
    JSONArray items = new JSONArray();
    for (Playlist playlist : playlists.values()) {
      JSONObject p = new JSONObject();
      p.put("id", playlist.id);
      p.put("name", playlist.name);
      p.put("edited", playlist.edited);
      JSONArray tracks = new JSONArray();
      for (long[] track : playlist.tracks) {
        JSONArray t = new JSONArray();
        t.put(track[0]);
        t.put(track[1]);
        tracks.put(t);
      }
      p.put("tracks", tracks);
      items.put(p);
    }
    o.put("playlists", items);
    JSONObject gone = new JSONObject();
    for (Map.Entry<Long, Long> entry : removed.entrySet()) gone.put(String.valueOf(entry.getKey()), entry.getValue());
    o.put("removed", gone);
    return o;
  }

  private static void read (JSONObject o, Map<Long, Playlist> into, Map<Long, Long> removedInto) throws Exception {
    JSONArray items = o.optJSONArray("playlists");
    if (items != null) {
      for (int i = 0; i < items.length(); i++) {
        JSONObject p = items.getJSONObject(i);
        Playlist playlist = new Playlist(p.getLong("id"), p.optString("name"), p.optLong("edited"));
        JSONArray tracks = p.optJSONArray("tracks");
        if (tracks != null) {
          for (int j = 0; j < tracks.length() && j < MAX_TRACKS; j++) {
            JSONArray t = tracks.getJSONArray(j);
            playlist.tracks.add(new long[] {t.getLong(0), t.getLong(1)});
          }
        }
        into.put(playlist.id, playlist);
      }
    }
    JSONObject gone = o.optJSONObject("removed");
    if (gone != null) {
      for (java.util.Iterator<String> it = gone.keys(); it.hasNext(); ) {
        String key = it.next();
        removedInto.put(Long.parseLong(key), gone.getLong(key));
      }
    }
  }

  /** Reads the file of Saved Messages, merges it with this phone's, writes the result back where it changed */
  public void sync (@Nullable Runnable after) {
    synchronized (this) {
      if (syncing) {
        syncAgain = true;
        if (after != null) UI.post(after);
        return;
      }
      syncing = true;
    }
    final long self = tdlib.selfChatId();
    tdlib.send(new TdApi.SearchChatMessages(self, null, "PlumaGram", null, 0, 0, 50, new TdApi.SearchMessagesFilterDocument()), (result, error) -> {
      TdApi.Message file = null;
      if (result != null) {
        for (TdApi.Message m : result.messages) {
          if (isCloudFile(m) && !(m.sendingState instanceof TdApi.MessageSendingStateFailed) && (file == null || m.id > file.id)) file = m;
        }
      }
      final TdApi.Message message = file;
      if (message == null) {
        merged(null, null, after);
        return;
      }
      TdApi.File document = ((TdApi.MessageDocument) message.content).document.document;
      if (Math.max(document.size, document.expectedSize) > MAX_BYTES) {
        merged(message, null, after);
        return;
      }
      tdlib.send(new TdApi.DownloadFile(document.id, 16, 0, 0, true), (downloaded, downloadError) -> {
        JSONObject cloud = null;
        if (downloaded != null && downloaded.local != null && downloaded.local.isDownloadingCompleted) {
          try (FileInputStream in = new FileInputStream(downloaded.local.path)) {
            byte[] bytes = new byte[(int) Math.min(MAX_BYTES, new File(downloaded.local.path).length())];
            int read = 0;
            while (read < bytes.length) {
              int count = in.read(bytes, read, bytes.length - read);
              if (count < 0) break;
              read += count;
            }
            cloud = new JSONObject(new String(bytes, 0, read, "UTF-8"));
            if (!"playlists".equals(cloud.optString("kind"))) cloud = null;
          } catch (Throwable t) {
            Tgx101Diag.mark("playlists cloud: unreadable file " + t.getClass().getSimpleName());
          }
        }
        merged(message, cloud, after);
      });
    });
  }

  private void merged (@Nullable TdApi.Message message, @Nullable JSONObject cloud, @Nullable Runnable after) {
    boolean upload;
    String before = null;
    try {
      synchronized (this) {
        if (cloud != null) {
          Map<Long, Playlist> theirs = new LinkedHashMap<>();
          Map<Long, Long> theirRemoved = new HashMap<>();
          read(cloud, theirs, theirRemoved);
          for (Map.Entry<Long, Long> entry : theirRemoved.entrySet()) {
            Long mine = removed.get(entry.getKey());
            if (mine == null || mine < entry.getValue()) removed.put(entry.getKey(), entry.getValue());
          }
          for (Playlist playlist : theirs.values()) {
            Playlist mine = playlists.get(playlist.id);
            if (mine == null || mine.edited < playlist.edited) playlists.put(playlist.id, playlist);
          }
          for (Long id : new ArrayList<>(removed.keySet())) playlists.remove(id);
          before = canon(cloud);
        }
        upload = !write().toString().equals(before) && (!playlists.isEmpty() || !removed.isEmpty());
      }
      saveLocal();
      if (upload) upload(message);
    } catch (Throwable t) {
      Tgx101Diag.mark("playlists cloud: merge failed " + t.getClass().getSimpleName());
    }
    boolean again;
    synchronized (this) {
      syncing = false;
      again = syncAgain;
      syncAgain = false;
    }
    if (after != null) UI.post(after);
    if (again) sync(null);
  }

  private static String canon (JSONObject o) throws Exception {
    // compared as this side writes it, so an unchanged file isn't sent again
    Map<Long, Playlist> items = new LinkedHashMap<>();
    Map<Long, Long> gone = new HashMap<>();
    read(o, items, gone);
    JSONObject out = new JSONObject();
    out.put("app", "PlumaGram");
    out.put("kind", "playlists");
    out.put("v", 1);
    JSONArray list = new JSONArray();
    for (Playlist playlist : items.values()) {
      JSONObject p = new JSONObject();
      p.put("id", playlist.id);
      p.put("name", playlist.name);
      p.put("edited", playlist.edited);
      JSONArray tracks = new JSONArray();
      for (long[] track : playlist.tracks) {
        JSONArray t = new JSONArray();
        t.put(track[0]);
        t.put(track[1]);
        tracks.put(t);
      }
      p.put("tracks", tracks);
      list.put(p);
    }
    out.put("playlists", list);
    JSONObject removedJson = new JSONObject();
    for (Map.Entry<Long, Long> entry : gone.entrySet()) removedJson.put(String.valueOf(entry.getKey()), entry.getValue());
    out.put("removed", removedJson);
    return out.toString();
  }

  private void upload (@Nullable TdApi.Message message) throws Exception {
    File root = new File(AppContext.get().getCacheDir(), "tgx101_playlists_cloud");
    File dir = new File(root, tdlib.id() + "_" + System.currentTimeMillis()); // its own folder: the file keeps its name
    if (!dir.mkdirs()) throw new IllegalStateException("can't create " + dir);
    File file = new File(dir, FILE_NAME);
    String text;
    synchronized (this) {
      text = write().toString(1);
    }
    try (OutputStream os = new FileOutputStream(file)) {
      os.write(text.getBytes("UTF-8"));
    }
    TdApi.InputMessageContent content = new TdApi.InputMessageDocument(
      new TdApi.InputDocument(TD.createInputFile(file.getPath()), null, true),
      new TdApi.FormattedText(CAPTION, new TdApi.TextEntity[0]));
    if (message != null) {
      tdlib.send(new TdApi.EditMessageMedia(message.chatId, message.id, null, content), (result, error) ->
        Tgx101Diag.mark(error != null ? "playlists cloud: replacing failed — " + error.message : "playlists cloud: file replaced in " + message.id));
    } else {
      TdApi.MessageSendOptions options = new TdApi.MessageSendOptions();
      options.disableNotification = true;
      Tgx101SavedStore.suppressBanner(10_000);
      tdlib.send(new TdApi.SendMessage(tdlib.selfChatId(), null, null, options, null, content), (result, error) ->
        Tgx101Diag.mark(error != null ? "playlists cloud: sending failed — " + error.message : "playlists cloud: file sent"));
    }
  }
}
