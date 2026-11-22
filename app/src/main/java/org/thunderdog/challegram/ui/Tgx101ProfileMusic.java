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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.HashSet;
import java.util.Set;

/**
 * TGx101 (user 2026-11-19, mockup «Музыка профиля»): «Add to profile» / «Remove from profile» for a song — held in a
 * chat or from the player's ⋮. The songs already on one's profile are known by their files, asked once and kept up
 * to date by what is added and taken off here.
 */
public final class Tgx101ProfileMusic {
  private Tgx101ProfileMusic () { }

  private static final Set<Integer> mine = new HashSet<>();
  /** the same songs by Telegram's id of the file: a song held in a chat is another local file of the same song */
  private static final Set<String> mineUnique = new HashSet<>();
  private static final java.util.List<Runnable> waiting = new java.util.ArrayList<>();
  private static long loadedFor;
  private static boolean loading;

  /** The song of a message, or null */
  public static @Nullable TdApi.Audio audioOf (@Nullable TdApi.Message message) {
    return message != null && message.content instanceof TdApi.MessageAudio ? ((TdApi.MessageAudio) message.content).audio : null;
  }

  /** Asks for one's own songs once per account, so the menus know which ones are on the profile */
  public static void prepare (Tdlib tdlib) {
    prepare(tdlib, null);
  }

  public static boolean isLoaded (Tdlib tdlib) {
    return loadedFor != 0 && loadedFor == tdlib.myUserId();
  }

  /** (user 2026-11-22: the player's ⋮ offered «Add to profile» for a song already there — the list wasn't asked yet)
   *  {@code after} runs on the UI thread once the list is known, at once when it already is */
  public static void prepare (Tdlib tdlib, @Nullable Runnable after) {
    long me = tdlib.myUserId();
    if (me != 0 && loadedFor == me) {
      if (after != null) after.run();
      return;
    }
    if (after != null) waiting.add(after);
    if (me == 0 || loading) return;
    loading = true;
    tdlib.send(new TdApi.GetUserProfileAudios(me, 0, 100), (result, error) -> UI.post(() -> {
      loading = false;
      if (result != null) {
        synchronized (mine) {
          mine.clear();
          mineUnique.clear();
          for (TdApi.Audio a : result.audios) add(a);
        }
        loadedFor = me;
      }
      java.util.List<Runnable> run = new java.util.ArrayList<>(waiting);
      waiting.clear();
      for (Runnable r : run) r.run();
    }));
  }

  private static void add (TdApi.Audio a) {
    mine.add(a.audio.id);
    if (a.audio.remote != null && a.audio.remote.uniqueId != null && !a.audio.remote.uniqueId.isEmpty()) mineUnique.add(a.audio.remote.uniqueId);
  }

  public static boolean isMine (Tdlib tdlib, @Nullable TdApi.Audio audio) {
    prepare(tdlib);
    if (audio == null) return false;
    synchronized (mine) {
      String unique = audio.audio.remote != null ? audio.audio.remote.uniqueId : null;
      return mine.contains(audio.audio.id) || (unique != null && !unique.isEmpty() && mineUnique.contains(unique));
    }
  }

  static void forget (int fileId) {
    synchronized (mine) {
      mine.remove(fileId);
    }
    loadedFor = 0; // its other copies (by Telegram's file id) aren't known here: asked anew next time
  }

  /** Adds the song to the top of one's profile, or takes it off when it is there already */
  public static void toggle (Tdlib tdlib, TdApi.Audio audio) {
    final int fileId = audio.audio.id;
    if (isMine(tdlib, audio)) {
      org.thunderdog.challegram.Tgx101Diag.mark("profile music: file " + fileId + " taken off from a menu");
      tdlib.send(new TdApi.RemoveProfileAudio(fileId), (ok, error) -> UI.post(() -> {
        if (error != null) {
          UI.showError(error);
        } else {
          forget(fileId);
          synchronized (mine) {
            if (audio.audio.remote != null && audio.audio.remote.uniqueId != null) mineUnique.remove(audio.audio.remote.uniqueId);
          }
          UI.showToast(R.string.Tgx101ProfileMusicRemoved, android.widget.Toast.LENGTH_SHORT);
        }
      }));
      return;
    }
    TdApi.InputFile file = audio.audio.remote != null && audio.audio.remote.id != null && !audio.audio.remote.id.isEmpty()
      ? new TdApi.InputFileRemote(audio.audio.remote.id)
      : new TdApi.InputFileId(fileId);
    TdApi.InputAudio input = new TdApi.InputAudio(file, null, audio.duration, audio.title, audio.performer);
    org.thunderdog.challegram.Tgx101Diag.mark("profile music: file " + fileId + " added from a menu");
    tdlib.send(new TdApi.AddProfileAudio(input), (ok, error) -> UI.post(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        synchronized (mine) {
          add(audio);
        }
        UI.showToast(R.string.Tgx101ProfileMusicAdded, android.widget.Toast.LENGTH_SHORT);
      }
    }));
  }
}
