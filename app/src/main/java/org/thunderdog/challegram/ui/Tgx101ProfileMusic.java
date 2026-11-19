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
  private static long loadedFor;
  private static boolean loading;

  /** The song of a message, or null */
  public static @Nullable TdApi.Audio audioOf (@Nullable TdApi.Message message) {
    return message != null && message.content instanceof TdApi.MessageAudio ? ((TdApi.MessageAudio) message.content).audio : null;
  }

  /** Asks for one's own songs once per account, so the menus know which ones are on the profile */
  public static void prepare (Tdlib tdlib) {
    long me = tdlib.myUserId();
    if (me == 0 || loading || loadedFor == me) return;
    loading = true;
    tdlib.send(new TdApi.GetUserProfileAudios(me, 0, 100), (result, error) -> UI.post(() -> {
      loading = false;
      if (result == null) return;
      synchronized (mine) {
        mine.clear();
        for (TdApi.Audio a : result.audios) mine.add(a.audio.id);
      }
      loadedFor = me;
    }));
  }

  public static boolean isMine (Tdlib tdlib, @Nullable TdApi.Audio audio) {
    prepare(tdlib);
    if (audio == null) return false;
    synchronized (mine) {
      return mine.contains(audio.audio.id);
    }
  }

  static void forget (int fileId) {
    synchronized (mine) {
      mine.remove(fileId);
    }
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
          mine.add(fileId);
        }
        UI.showToast(R.string.Tgx101ProfileMusicAdded, android.widget.Toast.LENGTH_SHORT);
      }
    }));
  }
}
