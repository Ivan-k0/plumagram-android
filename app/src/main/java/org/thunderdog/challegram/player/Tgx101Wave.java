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
  private static final int PAGE = 30, REFILL_BELOW = 12, MIN_TO_START = 2, ARTISTS = 6;
  private static final long REPEAT_WINDOW_MS = 2 * 60 * 60 * 1000L;

  private static @Nullable Tgx101Wave session;

  /** @param seedArtist the wave starts around this artist, null: around what the account likes */
  public static void start (Tdlib tdlib, @Nullable String seedArtist, RunnableBool after) {
    if (session != null) session.stop();
    new Tgx101Wave(tdlib, seedArtist).begin(after);
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
    if (seedArtist != null) queries.add(seedArtist);
    for (String artist : taste.favoriteArtists(ARTISTS)) {
      if (!artist.equalsIgnoreCase(seedArtist)) queries.add(artist);
    }
    queries.add(""); // music of all the chats: what is new to the account
    load(queries.size(), found -> {
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

  private void stop () {
    isStopped = true;
    if (session == this) session = null;
    TdlibManager.instance().player().removeTrackChangeListener(this);
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
      stop(); // closed
      return;
    }
    if (newTrack == lastTrack) return;
    lastTrack = newTrack;
    if (this.tdlib != tdlib || !isOurs(newTrack)) {
      stop();
      return;
    }
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
    });
  }
}
