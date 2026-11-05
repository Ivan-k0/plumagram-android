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
package org.thunderdog.challegram.player;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.vkryl.core.lambda.RunnableData;

/**
 * (user 2026-11-05) What the account likes in music, on this phone only: ♥ with its copy in the Storage, what is hidden from the wave,
 * how the artists fare. A dislike is careful: it hides one track from the wave and touches nothing else; an artist is hidden only by
 * a choice of its own; plays and skips only order what is offered.
 */
public final class Tgx101MusicTaste {
  private static final int MAX_ENTRIES = 2000, ARTIST_LIMIT = 30;
  private static final Map<Integer, Tgx101MusicTaste> INSTANCES = new HashMap<>();
  private static final List<Runnable> LISTENERS = new ArrayList<>();

  public static synchronized Tgx101MusicTaste get (Tdlib tdlib) {
    Tgx101MusicTaste taste = INSTANCES.get(tdlib.id());
    if (taste == null) {
      taste = new Tgx101MusicTaste(tdlib);
      INSTANCES.put(tdlib.id(), taste);
    }
    return taste;
  }

  /** On the UI thread, when something changed for a track */
  public static void addListener (Runnable listener) {
    LISTENERS.add(listener);
  }

  public static void removeListener (Runnable listener) {
    LISTENERS.remove(listener);
  }

  private static void notifyChanged () {
    for (Runnable listener : new ArrayList<>(LISTENERS)) listener.run();
  }

  private final Tdlib tdlib;
  private final Map<String, long[]> liked = new LinkedHashMap<>();
  private final Map<String, String> disliked = new LinkedHashMap<>(); // the track → how to name it in the list of hidden
  private final Map<String, String> hiddenArtists = new LinkedHashMap<>(); // lower case → as it's written
  private final Map<String, Integer> artists = new LinkedHashMap<>();
  private final Map<String, Long> played = new LinkedHashMap<>(); // the track → when

  private Tgx101MusicTaste (Tdlib tdlib) {
    this.tdlib = tdlib;
    load();
  }

  // Keys

  public static @Nullable String keyOf (@Nullable TdApi.Message message) {
    if (message == null || !(message.content instanceof TdApi.MessageAudio)) return null;
    TdApi.File file = ((TdApi.MessageAudio) message.content).audio.audio;
    if (file.remote != null && file.remote.uniqueId != null && !file.remote.uniqueId.isEmpty()) return file.remote.uniqueId;
    return "m" + message.chatId + "_" + message.id;
  }

  public static @Nullable String artistOf (@Nullable TdApi.Message message) {
    if (message == null || !(message.content instanceof TdApi.MessageAudio)) return null;
    String performer = ((TdApi.MessageAudio) message.content).audio.performer;
    return performer == null || performer.trim().isEmpty() ? null : performer.trim();
  }

  private static String artistKey (String artist) {
    return artist.toLowerCase(java.util.Locale.ROOT);
  }

  private static String nameOf (TdApi.Message message) {
    TdApi.Audio audio = ((TdApi.MessageAudio) message.content).audio;
    String title = TD.getTitle(audio), artist = TD.getSubtitle(audio);
    return artist == null || artist.isEmpty() ? String.valueOf(title) : artist + " — " + title;
  }

  // ♥

  public synchronized boolean isLiked (@Nullable TdApi.Message message) {
    String key = keyOf(message);
    return key != null && liked.containsKey(key);
  }

  /** ♥ pressed: into its folder of the Storage, or out of there */
  public void toggleLike (TdApi.Message message) {
    final String key = keyOf(message);
    if (key == null || message.chatId == 0) return;
    long[] copies;
    synchronized (this) {
      copies = liked.get(key);
    }
    if (copies != null) {
      if (copies.length == 0) return; // still being sent
      synchronized (this) {
        liked.remove(key);
      }
      addToArtist(message, -3);
      save();
      tdlib.send(new TdApi.DeleteMessages(tdlib.selfChatId(), copies, true), tdlib.typedOkHandler());
      UI.showToast(R.string.Tgx101PlayerUnsaved, android.widget.Toast.LENGTH_SHORT);
      notifyChanged();
      return;
    }
    saveCopy(message, null, saved -> {
      Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
      Tgx101SavedStore.Folder folder = store.playerFolder(Lang.getString(R.string.Tgx101PlayerMusicFolder));
      store.putInFolderKeepingLast(folder.id, idsOf(saved));
      UI.showToast(Lang.getString(R.string.Tgx101SavedPutDone, folder.name), android.widget.Toast.LENGTH_SHORT);
    });
  }

  /** The folder chosen in the menu of held ♥: there now, and the next ♥ too */
  public void saveToFolder (TdApi.Message message, long folderId) {
    final Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    store.setPlayerFolder(folderId);
    withCopy(message, saved -> {
      Tgx101SavedStore.Folder folder = store.folder(folderId);
      if (folder == null) return;
      store.putInFolderKeepingLast(folderId, idsOf(saved));
      UI.showToast(Lang.getString(R.string.Tgx101SavedPutDone, folder.name), android.widget.Toast.LENGTH_SHORT);
    });
  }

  /** Runs on the track's copy in the Storage, made when there's none yet */
  public void withCopy (TdApi.Message message, RunnableData<List<TdApi.Message>> action) {
    final String key = keyOf(message);
    if (key == null || message.chatId == 0) return;
    long[] copies;
    synchronized (this) {
      copies = liked.get(key);
    }
    if (copies == null) {
      saveCopy(message, null, action);
    } else if (copies.length != 0) {
      tdlib.send(new TdApi.GetMessages(tdlib.selfChatId(), copies), (result, error) -> UI.post(() -> {
        if (result == null) return;
        List<TdApi.Message> found = new ArrayList<>();
        for (TdApi.Message copy : result.messages) {
          if (copy != null) found.add(copy);
        }
        if (found.isEmpty()) {
          // deleted in the Storage meanwhile: a new one
          synchronized (this) {
            liked.remove(key);
          }
          saveCopy(message, null, action);
        } else {
          action.runWithData(found);
        }
      }));
    }
  }

  private static long[] idsOf (List<TdApi.Message> messages) {
    long[] ids = new long[messages.size()];
    for (int i = 0; i < ids.length; i++) ids[i] = messages.get(i).id;
    return ids;
  }

  /** The copies get their real ids when they are sent; an empty entry until then keeps ♥ lit and unpressable */
  private void saveCopy (TdApi.Message message, @Nullable Runnable unused, RunnableData<List<TdApi.Message>> after) {
    final String key = keyOf(message);
    synchronized (this) {
      liked.put(key, new long[0]);
    }
    notifyChanged();
    tdlib.send(new TdApi.ForwardMessages(tdlib.selfChatId(), null, message.chatId, new long[] {message.id}, null, false, false), (result, error) -> {
      if (error != null) {
        UI.post(() -> onCopyFailed(key, error));
        return;
      }
      // Listening here, on the client's thread: the answer about the sending can't come before it
      // (crash 2026-11-05 16:35, a heart on a track): a message that can't be forwarded (a protected chat) comes back as null
      final List<TdApi.Message> copies = new ArrayList<>();
      final List<Long> waiting = new ArrayList<>();
      for (TdApi.Message copy : result.messages) {
        if (copy == null) continue;
        copies.add(copy);
        if (copy.sendingState != null) waiting.add(copy.id);
      }
      if (copies.isEmpty()) {
        UI.post(() -> onCopyFailed(key, new TdApi.Error(400, "MESSAGE_CANT_BE_FORWARDED")));
        return;
      }
      if (waiting.isEmpty()) {
        UI.post(() -> onCopySaved(message, key, copies, after));
        return;
      }
      final long selfChatId = tdlib.selfChatId();
      final org.thunderdog.challegram.telegram.MessageListener[] listener = new org.thunderdog.challegram.telegram.MessageListener[1];
      listener[0] = new org.thunderdog.challegram.telegram.MessageListener() {
        @Override
        public void onMessageSendSucceeded (TdApi.Message sent, long oldMessageId) {
          UI.post(() -> {
            if (!waiting.remove((Long) oldMessageId)) return;
            for (int i = 0; i < copies.size(); i++) {
              if (copies.get(i).id == oldMessageId) copies.set(i, sent);
            }
            if (waiting.isEmpty()) {
              tdlib.listeners().unsubscribeFromMessageUpdates(selfChatId, listener[0]);
              onCopySaved(message, key, copies, after);
            }
          });
        }

        @Override
        public void onMessageSendFailed (TdApi.Message failed, long oldMessageId, TdApi.Error sendError) {
          UI.post(() -> {
            if (!waiting.remove((Long) oldMessageId)) return;
            tdlib.listeners().unsubscribeFromMessageUpdates(selfChatId, listener[0]);
            onCopyFailed(key, sendError);
          });
        }
      };
      tdlib.listeners().subscribeToMessageUpdates(selfChatId, listener[0]);
    });
  }

  private void onCopySaved (TdApi.Message message, String key, List<TdApi.Message> copies, RunnableData<List<TdApi.Message>> after) {
    synchronized (this) {
      liked.put(key, idsOf(copies));
      trim(liked);
    }
    addToArtist(message, 3);
    save();
    notifyChanged();
    after.runWithData(copies);
  }

  private void onCopyFailed (String key, TdApi.Error error) {
    synchronized (this) {
      liked.remove(key);
    }
    notifyChanged();
    UI.showError(error);
  }

  // 👎

  public synchronized boolean isDisliked (@Nullable TdApi.Message message) {
    String key = keyOf(message);
    return key != null && disliked.containsKey(key);
  }

  public void setDisliked (TdApi.Message message, boolean isDisliked) {
    String key = keyOf(message);
    if (key == null) return;
    synchronized (this) {
      if (isDisliked) {
        disliked.put(key, nameOf(message));
        trim(disliked);
      } else {
        disliked.remove(key);
      }
    }
    save();
    notifyChanged();
  }

  public synchronized boolean isArtistHidden (@Nullable String artist) {
    return artist != null && hiddenArtists.containsKey(artistKey(artist));
  }

  public void setArtistHidden (String artist, boolean isHidden) {
    synchronized (this) {
      if (isHidden) {
        hiddenArtists.put(artistKey(artist), artist);
      } else {
        hiddenArtists.remove(artistKey(artist));
      }
    }
    save();
    notifyChanged();
  }

  /** The wave doesn't offer it */
  public synchronized boolean isHidden (TdApi.Message message) {
    String key = keyOf(message), artist = artistOf(message);
    return (key != null && disliked.containsKey(key)) || (artist != null && hiddenArtists.containsKey(artistKey(artist)));
  }

  /** [key, name] */
  public synchronized List<String[]> hiddenTracks () {
    List<String[]> list = new ArrayList<>();
    for (Map.Entry<String, String> entry : disliked.entrySet()) list.add(new String[] {entry.getKey(), entry.getValue()});
    return list;
  }

  public synchronized List<String> hiddenArtists () {
    return new ArrayList<>(hiddenArtists.values());
  }

  public void restoreTrack (String key) {
    synchronized (this) {
      disliked.remove(key);
    }
    save();
    notifyChanged();
  }

  // Listening

  private static final long SKIP_BEFORE_MS = 20000L;

  /** A track ended or was left for another */
  public void noteListen (TdApi.Message message, long positionMs, long durationMs, boolean leftForAnother) {
    String key = keyOf(message);
    if (key == null || positionMs < 0) return;
    if (positionMs >= 1000L) {
      synchronized (this) {
        played.remove(key);
        played.put(key, System.currentTimeMillis());
        trim(played);
      }
    }
    if (durationMs > 0 && positionMs >= durationMs * 8 / 10) {
      addToArtist(message, 1);
      save();
    } else if (leftForAnother && positionMs < SKIP_BEFORE_MS) {
      addToArtist(message, -1);
      save();
    } else if (positionMs >= 1000L) {
      save();
    }
  }

  /** Played within this time: not offered again */
  public synchronized boolean wasPlayedWithin (TdApi.Message message, long windowMs) {
    Long at = played.get(keyOf(message));
    return at != null && System.currentTimeMillis() - at < windowMs;
  }

  private void addToArtist (TdApi.Message message, int change) {
    String artist = artistOf(message);
    if (artist == null) return;
    String key = artistKey(artist);
    synchronized (this) {
      Integer score = artists.remove(key);
      artists.put(key, Math.max(-ARTIST_LIMIT, Math.min(ARTIST_LIMIT, (score != null ? score : 0) + change)));
      trim(artists);
    }
  }

  public synchronized int artistScore (@Nullable String artist) {
    if (artist == null) return 0;
    Integer score = artists.get(artistKey(artist));
    return score != null ? score : 0;
  }

  /** The best first, up to this many of them with a good score */
  public synchronized List<String> favoriteArtists (int limit) {
    List<Map.Entry<String, Integer>> entries = new ArrayList<>(artists.entrySet());
    java.util.Collections.sort(entries, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
    List<String> result = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : entries) {
      if (entry.getValue() <= 0 || result.size() >= limit) break;
      if (!hiddenArtists.containsKey(entry.getKey())) result.add(entry.getKey());
    }
    return result;
  }

  // Storage

  private static <V> void trim (Map<String, V> map) {
    Iterator<String> it = map.keySet().iterator();
    while (map.size() > MAX_ENTRIES && it.hasNext()) {
      it.next();
      it.remove();
    }
  }

  private synchronized void load () {
    String encoded = Settings.instance().tgx101MusicTaste(tdlib.id());
    if (encoded == null) return;
    try {
      JSONObject o = new JSONObject(encoded);
      JSONObject likedJson = o.optJSONObject("liked");
      if (likedJson != null) {
        for (Iterator<String> it = likedJson.keys(); it.hasNext(); ) {
          String key = it.next();
          JSONArray ids = likedJson.getJSONArray(key);
          long[] copies = new long[ids.length()];
          for (int i = 0; i < copies.length; i++) copies[i] = ids.getLong(i);
          if (copies.length != 0) liked.put(key, copies);
        }
      }
      readStrings(o.optJSONObject("disliked"), disliked);
      readStrings(o.optJSONObject("hidden"), hiddenArtists);
      JSONObject artistsJson = o.optJSONObject("artists");
      if (artistsJson != null) {
        for (Iterator<String> it = artistsJson.keys(); it.hasNext(); ) {
          String key = it.next();
          artists.put(key, artistsJson.getInt(key));
        }
      }
      JSONObject playedJson = o.optJSONObject("played");
      if (playedJson != null) {
        for (Iterator<String> it = playedJson.keys(); it.hasNext(); ) {
          String key = it.next();
          played.put(key, playedJson.getLong(key));
        }
      }
    } catch (Throwable ignored) { }
  }

  private static void readStrings (@Nullable JSONObject from, Map<String, String> to) throws org.json.JSONException {
    if (from == null) return;
    for (Iterator<String> it = from.keys(); it.hasNext(); ) {
      String key = it.next();
      to.put(key, from.getString(key));
    }
  }

  private void save () {
    final String encoded;
    synchronized (this) {
      try {
        JSONObject o = new JSONObject();
        JSONObject likedJson = new JSONObject();
        for (Map.Entry<String, long[]> entry : liked.entrySet()) {
          if (entry.getValue().length == 0) continue;
          JSONArray ids = new JSONArray();
          for (long id : entry.getValue()) ids.put(id);
          likedJson.put(entry.getKey(), ids);
        }
        o.put("liked", likedJson);
        o.put("disliked", new JSONObject(disliked));
        o.put("hidden", new JSONObject(hiddenArtists));
        o.put("artists", new JSONObject(artists));
        o.put("played", new JSONObject(played));
        encoded = o.toString();
      } catch (Throwable t) {
        return;
      }
    }
    Settings.instance().setTgx101MusicTaste(tdlib.id(), encoded);
  }
}
