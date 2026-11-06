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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import tgx.td.Td;

/**
 * (user 2026-11-05 «мы можем плейлисты делать из наших папок или меток») Telegram has no playlists of its own; here they are the Storage's
 * folders and tags that hold music, and the music of the profile. A tap plays one as the queue.
 */
final class Tgx101Playlists {
  private static final int CHUNK = 100, MAX_TAGS = 20, SEARCH_LIMIT = 100;

  private final ViewController<?> c;
  private final Tdlib tdlib;
  private final Tgx101Sheet.Handle handle;
  private final Map<Long, TdApi.Message> music = new HashMap<>();
  private final Map<String, List<TdApi.Message>> tagged = new HashMap<>();
  private List<TdApi.Message> profile = new ArrayList<>();
  private int waiting;

  static void show (ViewController<?> c) {
    new Tgx101Playlists(c);
  }

  private Tgx101Playlists (ViewController<?> c) {
    this.c = c;
    this.tdlib = c.tdlib();
    this.handle = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(c.context(), Lang.getString(R.string.Tgx101Playlists)));
      body.addView(Tgx101Sheet.row(c.context(), 0, 0, Lang.getString(R.string.Tgx101PlaylistsLoading), null, false, null));
    });
    waiting = 1; // the player's own playlists, read from the cloud first
    org.thunderdog.challegram.data.Tgx101PlaylistStore.get(tdlib).sync(this::done);
    load();
  }

  // Loading

  private void load () {
    final Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    final LinkedHashSet<Long> ids = new LinkedHashSet<>();
    for (Tgx101SavedStore.Folder folder : store.folders()) ids.addAll(folder.messageIds);
    final List<String> tags = store.knownTags();
    for (int i = 0; i < tags.size() && i < MAX_TAGS; i++) ids.addAll(store.localTagged(tags.get(i)));
    final List<Long> all = new ArrayList<>(ids);
    final long self = tdlib.selfChatId();
    waiting++; // the profile
    for (int from = 0; from < all.size(); from += CHUNK) {
      long[] chunk = new long[Math.min(CHUNK, all.size() - from)];
      for (int i = 0; i < chunk.length; i++) chunk[i] = all.get(from + i);
      waiting++;
      tdlib.send(new TdApi.GetMessages(self, chunk), (result, error) -> UI.post(() -> {
        if (result != null) {
          for (TdApi.Message message : result.messages) {
            if (message != null && Td.isAudio(message.content)) music.put(message.id, message);
          }
        }
        done();
      }));
    }
    // Tags written as #hashtags in one's own messages
    for (int i = 0; i < tags.size() && i < MAX_TAGS; i++) {
      final String tag = tags.get(i);
      waiting++;
      tdlib.send(new TdApi.SearchChatMessages(self, null, "#" + tag, null, 0, 0, SEARCH_LIMIT, new TdApi.SearchMessagesFilterAudio()), (result, error) -> UI.post(() -> {
        if (result != null) {
          List<TdApi.Message> list = new ArrayList<>();
          for (TdApi.Message message : result.messages) {
            if (Td.isAudio(message.content)) list.add(message);
          }
          tagged.put(tag, list);
        }
        done();
      }));
    }
    tdlib.send(new TdApi.GetUserProfileAudios(tdlib.myUserId(), 0, SEARCH_LIMIT), (result, error) -> UI.post(() -> {
      if (result != null) profile = asMessages(result.audios);
      done();
    }));
  }

  /** The profile's music isn't messages: the player gets messages of no chat around them, as a file of the phone */
  private static List<TdApi.Message> asMessages (TdApi.Audio[] audios) {
    List<TdApi.Message> list = new ArrayList<>(audios.length);
    for (int i = 0; i < audios.length; i++) {
      TdApi.Message message = new TdApi.Message();
      message.id = -(i + 1);
      message.chatId = 0;
      message.content = new TdApi.MessageAudio(audios[i], new TdApi.FormattedText("", new TdApi.TextEntity[0]));
      list.add(message);
    }
    return list;
  }

  private void done () {
    if (--waiting == 0 && !c.isDestroyed()) fill();
  }

  // The list

  private void fill () {
    final LinearLayout body = handle.body;
    body.removeAllViews();
    body.addView(Tgx101Sheet.title(c.context(), Lang.getString(R.string.Tgx101Playlists)));
    boolean any = false;
    final org.thunderdog.challegram.data.Tgx101PlaylistStore own = org.thunderdog.challegram.data.Tgx101PlaylistStore.get(tdlib);
    for (org.thunderdog.challegram.data.Tgx101PlaylistStore.Playlist playlist : own.list()) {
      any = true;
      android.view.View row = Tgx101Sheet.row(c.context(), R.drawable.baseline_queue_music_24, 0, playlist.name, String.valueOf(playlist.tracks.size()), false, v -> {
        handle.dismiss();
        own.resolve(playlist, tracks -> {
          if (tracks.isEmpty()) {
            UI.showToast(R.string.Tgx101PlaylistGone, android.widget.Toast.LENGTH_SHORT);
          } else {
            play(tracks);
          }
        });
      });
      row.setOnLongClickListener(v -> {
        handle.dismiss();
        UI.post(() -> manage(own, playlist), 200);
        return true;
      });
      body.addView(row);
    }
    if (!profile.isEmpty()) {
      any = true;
      body.addView(row(R.drawable.baseline_account_circle_24, 0, Lang.getString(R.string.Tgx101PlaylistsProfile), profile));
    }
    final Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    for (Tgx101SavedStore.Folder folder : store.folders()) {
      List<TdApi.Message> list = new ArrayList<>();
      for (Long id : folder.messageIds) {
        TdApi.Message message = music.get(id);
        if (message != null) list.add(message);
      }
      if (list.isEmpty()) continue;
      any = true;
      body.addView(row(0, folder.color(), folder.name, list));
    }
    final List<String> tags = store.knownTags();
    for (int i = 0; i < tags.size() && i < MAX_TAGS; i++) {
      final String tag = tags.get(i);
      LinkedHashSet<Long> seen = new LinkedHashSet<>();
      List<TdApi.Message> list = new ArrayList<>();
      for (Long id : store.localTagged(tag)) {
        TdApi.Message message = music.get(id);
        if (message != null && seen.add(message.id)) list.add(message);
      }
      List<TdApi.Message> found = tagged.get(tag);
      if (found != null) {
        for (TdApi.Message message : found) {
          if (seen.add(message.id)) list.add(message);
        }
      }
      if (list.isEmpty()) continue;
      any = true;
      body.addView(row(R.drawable.baseline_label_24, 0, "#" + tag, list));
    }
    if (!any) {
      body.addView(Tgx101Sheet.row(c.context(), 0, 0, Lang.getString(R.string.Tgx101PlaylistsNone), null, false, null));
    }
  }

  /** Held: rename, remove */
  private void manage (org.thunderdog.challegram.data.Tgx101PlaylistStore own, org.thunderdog.challegram.data.Tgx101PlaylistStore.Playlist playlist) {
    final Tgx101Sheet.Handle[] sheet = new Tgx101Sheet.Handle[1];
    sheet[0] = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(c.context(), playlist.name));
      body.addView(Tgx101Sheet.row(c.context(), R.drawable.baseline_edit_24, 0, Lang.getString(R.string.Tgx101PlaylistRename), null, false, v -> {
        sheet[0].dismiss();
        UI.post(() -> Tgx101SavedActions.askName(c, Lang.getString(R.string.Tgx101PlaylistRename), playlist.name, name -> own.rename(playlist.id, name)), 200);
      }));
      body.addView(Tgx101Sheet.row(c.context(), R.drawable.baseline_delete_24, 0, Lang.getString(R.string.Tgx101PlaylistDelete), null, false, v -> {
        sheet[0].dismiss();
        own.remove(playlist.id);
        UI.showToast(Lang.getString(R.string.Tgx101PlaylistDeleted, playlist.name), android.widget.Toast.LENGTH_SHORT);
      }));
    });
  }

  private android.view.View row (int icon, int swatch, String name, List<TdApi.Message> tracks) {
    return Tgx101Sheet.row(c.context(), icon, swatch, name, String.valueOf(tracks.size()), false, v -> {
      handle.dismiss();
      play(tracks);
    });
  }

  private void play (List<TdApi.Message> tracks) {
    final List<TdApi.Message> queue = new ArrayList<>(tracks);
    tdlib.context().player().playPauseMessage(tdlib, queue.get(0), new TGPlayerController.PlayListBuilder() {
      @Override
      public TGPlayerController.PlayList buildPlayList (TdApi.Message fromMessage) {
        return new TGPlayerController.PlayList(queue, 0).setReachedEnds(true, true); // the whole of it, nothing more to load
      }

      @Override
      public boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId) {
        return false;
      }
    });
  }
}
