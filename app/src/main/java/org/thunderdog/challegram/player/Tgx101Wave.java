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
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.vkryl.core.lambda.RunnableBool;
import tgx.td.Td;

/**
 * (user 2026-11-05) The wave: an endless queue of music from the account's own chats and channels. Every third track is by an artist
 * it likes, the others are new to it; what was hidden isn't offered, and nothing played within two hours comes back. The queue is
 * topped up while it runs out, and the session ends as soon as something else starts to play.
 */
public final class Tgx101Wave implements TGPlayerController.TrackChangeListener {
  private static final int PAGE = 30, REFILL_BELOW = 12, MIN_TO_START = 2, ARTISTS = 6, FIRST_SEARCHES = 16;
  private static final long REPEAT_WINDOW_MS = 2 * 60 * 60 * 1000L;

  private static @Nullable Tgx101Wave session;
  // (user 2026-11-05 «включил поток, потом переключил на плейлист — поток собьётся? — так нужно, чтобы не сбивался»): the flow left for
  // something else keeps its queue and its place, and goes on from there when asked
  private static @Nullable Tgx101Wave suspended;

  /** @param seedArtist the wave starts around this artist, null: around what the account likes */
  public static void start (Tdlib tdlib, @Nullable String seedArtist, RunnableBool after) {
    if (session != null) session.stop(false);
    suspended = null;
    new Tgx101Wave(tdlib, seedArtist).begin(after);
  }

  /** The track the flow left at, when it can go on */
  public static @Nullable TdApi.Message resumable (Tdlib tdlib) {
    Tgx101Wave wave = suspended;
    return wave != null && wave.tdlib == tdlib && wave.queue != null && wave.lastOurs != null ? wave.lastOurs : null;
  }

  public static boolean resume (Tdlib tdlib) {
    Tgx101Wave wave = suspended;
    if (wave == null || resumable(tdlib) == null) return false;
    suspended = null;
    if (session != null) session.stop(false);
    wave.goOn();
    return true;
  }

  private @Nullable List<TdApi.Message> queue;
  private @Nullable TdApi.Message lastOurs;
  private long leftAt;

  private void goOn () {
    final List<TdApi.Message> list = new ArrayList<>(queue);
    int at = 0;
    for (int i = 0; i < list.size(); i++) {
      if (TGPlayerController.compareTracks(list.get(i), lastOurs)) at = i;
    }
    final int origin = at;
    isStopped = false;
    lastTrack = null;
    session = this;
    TdlibManager.instance().player().addTrackChangeListener(this);
    TdlibManager.instance().player().tgx101PlayFrom(tdlib, list.get(origin), new TGPlayerController.PlayListBuilder() {
      @Override
      public TGPlayerController.PlayList buildPlayList (TdApi.Message fromMessage) {
        return new TGPlayerController.PlayList(list, origin).setReachedEnds(true, true);
      }

      @Override
      public boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId) {
        return false;
      }
    }, leftAt);
  }

  private void rememberQueue () {
    List<TdApi.Message> list = TdlibManager.instance().player().getTrackList();
    if (list != null) queue = new ArrayList<>(list);
  }

  public static boolean isActive () {
    return session != null;
  }

  private final Tdlib tdlib;
  private final Tgx101MusicTaste taste;
  private final @Nullable String seedArtist;
  private final List<String> queries = new ArrayList<>();
  private final Map<String, String> offsets = new HashMap<>();
  private final Set<String> offered = new HashSet<>();
  private int nextQuery;
  private boolean isLoading, isStopped;
  private TdApi.Message lastTrack;

  private Tgx101Wave (Tdlib tdlib, @Nullable String seedArtist) {
    this.tdlib = tdlib;
    this.taste = Tgx101MusicTaste.get(tdlib);
    this.seedArtist = seedArtist;
  }

  private void begin (RunnableBool after) {
    if (!org.thunderdog.challegram.unsorted.Settings.instance().tgx101WaveHints()) {
      beginWith(new ArrayList<>(), after);
      return;
    }
    // Switched on with a warning. Whatever goes wrong there, the wave goes on from the chats alone
    String artist = seedArtist;
    if (artist == null) {
      List<String> favorites = taste.favoriteArtists(1);
      if (!favorites.isEmpty()) artist = favorites.get(0);
    }
    TdApi.Message current = TdlibManager.instance().player().getCurrentTrack();
    String title = current != null && current.content instanceof TdApi.MessageAudio && artist != null && artist.equalsIgnoreCase(Tgx101MusicTaste.artistOf(current)) ? ((TdApi.MessageAudio) current.content).audio.title : null;
    Tgx101WaveHints.find(artist, title, hints -> UI.post(() -> beginWith(hints, after)));
  }

  private void beginWith (List<String> hints, RunnableBool after) {
    if (seedArtist != null) queries.add(seedArtist);
    for (String artist : taste.favoriteArtists(ARTISTS)) {
      if (!artist.equalsIgnoreCase(seedArtist)) queries.add(artist);
    }
    queries.add(""); // music of all the chats: what is new to the account
    Collections.shuffle(hints);
    for (String hint : hints) {
      if (!taste.isArtistHidden(hint) && !queries.contains(hint)) queries.add(hint);
    }
    load(Math.min(queries.size(), FIRST_SEARCHES), found -> {
      if (found.size() < MIN_TO_START) {
        after.runWithBool(false);
        return;
      }
      session = this;
      List<TdApi.Message> mixed = mix(found);
      TdlibManager.instance().player().addTrackChangeListener(this);
      TdlibManager.instance().player().playPauseMessage(tdlib, mixed.get(0), new TGPlayerController.PlayListBuilder() {
        @Override
        public TGPlayerController.PlayList buildPlayList (TdApi.Message fromMessage) {
          return new TGPlayerController.PlayList(mixed, 0).setReachedEnds(true, true);
        }

        @Override
        public boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId) {
          return false;
        }
      });
      after.runWithBool(true);
    });
  }

  private void stop (boolean keep) {
    isStopped = true;
    if (session == this) session = null;
    TdlibManager.instance().player().removeTrackChangeListener(this);
    if (keep && queue != null && lastOurs != null) {
      leftAt = TdlibManager.instance().player().tgx101PositionWhenLeft(lastOurs);
      suspended = this;
    }
  }

  /** Two new for one known: the known ones are those of the artists it likes */
  private List<TdApi.Message> mix (List<TdApi.Message> found) {
    List<TdApi.Message> known = new ArrayList<>(), fresh = new ArrayList<>();
    for (TdApi.Message track : found) {
      (taste.artistScore(Tgx101MusicTaste.artistOf(track)) > 0 ? known : fresh).add(track);
    }
    Collections.shuffle(known);
    Collections.shuffle(fresh);
    List<TdApi.Message> mixed = new ArrayList<>(found.size());
    // Around the artist asked for: that one goes first
    if (seedArtist != null) {
      for (int i = 0; i < fresh.size(); i++) {
        if (seedArtist.equalsIgnoreCase(Tgx101MusicTaste.artistOf(fresh.get(i)))) {
          mixed.add(fresh.remove(i));
          break;
        }
      }
      if (mixed.isEmpty()) {
        for (int i = 0; i < known.size(); i++) {
          if (seedArtist.equalsIgnoreCase(Tgx101MusicTaste.artistOf(known.get(i)))) {
            mixed.add(known.remove(i));
            break;
          }
        }
      }
    }
    int at = mixed.size();
    while (!known.isEmpty() || !fresh.isEmpty()) {
      boolean takeKnown = !known.isEmpty() && (fresh.isEmpty() || at % 3 == 2);
      mixed.add((takeKnown ? known : fresh).remove(0));
      at++;
    }
    return mixed;
  }

  private interface Found {
    void onFound (List<TdApi.Message> tracks);
  }

  /** The next {@code count} of the searches, in turn */
  private void load (int count, Found callback) {
    isLoading = true;
    final List<TdApi.Message> found = new ArrayList<>();
    final int[] left = {count};
    final Runnable oneDone = () -> {
      if (--left[0] == 0) {
        isLoading = false;
        if (!isStopped) callback.onFound(found);
      }
    };
    for (int i = 0; i < count; i++) {
      final String query = queries.get(nextQuery++ % queries.size());
      String offset = offsets.get(query);
      if (offset != null && offset.isEmpty()) {
        // this search has given all it has
        UI.post(oneDone);
        continue;
      }
      tdlib.send(new TdApi.SearchMessages(null, query, offset != null ? offset : "", PAGE, new TdApi.SearchMessagesFilterAudio(), null, 0, 0), (result, error) -> UI.post(() -> {
        if (result != null) {
          offsets.put(query, result.nextOffset != null ? result.nextOffset : "");
          for (TdApi.Message message : result.messages) {
            if (accepts(message)) found.add(message);
          }
        } else {
          offsets.put(query, "");
        }
        oneDone.run();
      }));
    }
  }

  private boolean accepts (TdApi.Message message) {
    if (!Td.isAudio(message.content) || message.chatId == 0 || taste.isHidden(message) || taste.wasPlayedWithin(message, REPEAT_WINDOW_MS)) {
      return false;
    }
    return offered.add(Tgx101MusicTaste.keyOf(message)); // the same song is in many chats
  }

  /** Something else was started by the user: the wave is over */
  private boolean isOurs (TdApi.Message track) {
    return offered.contains(Tgx101MusicTaste.keyOf(track));
  }

  @Override
  public void onTrackChanged (Tdlib tdlib, @Nullable TdApi.Message newTrack, int fileId, int state, float progress, boolean byUser) {
    if (isStopped) return;
    if (newTrack == null || state == TGPlayerController.STATE_NONE) {
      stop(true); // closed: it may go on later
      return;
    }
    if (newTrack == lastTrack) return;
    lastTrack = newTrack;
    if (this.tdlib != tdlib || !isOurs(newTrack)) {
      stop(true); // something else plays now
      return;
    }
    lastOurs = newTrack;
    rememberQueue();
    refillIfNeeded();
  }

  private void refillIfNeeded () {
    if (isLoading) return;
    TGPlayerController player = TdlibManager.instance().player();
    List<TdApi.Message> list = player.getTrackList();
    TdApi.Message current = player.getCurrentTrack();
    if (list == null || current == null) return;
    int at = -1;
    for (int i = 0; i < list.size(); i++) {
      if (TGPlayerController.compareTracks(list.get(i), current)) at = i;
    }
    if (at == -1 || list.size() - at - 1 >= REFILL_BELOW) return;
    load(Math.min(3, queries.size()), found -> {
      TGPlayerController p = TdlibManager.instance().player();
      for (TdApi.Message track : mix(found)) p.addToPlayList(track);
      rememberQueue();
    });
  }
}
