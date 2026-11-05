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

import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.Tgx101MusicTaste;

import java.util.List;

/** (user 2026-11-05) Held 👎 of the music player: the choices that go further than one track, and what is hidden so far */
final class Tgx101DislikeMenu extends Tgx101HoldMenu {
  private final TdApi.Message message;

  Tgx101DislikeMenu (ViewController<?> c, View anchor, @Nullable View.OnTouchListener anchorTouch, TdApi.Message message) {
    super(c, anchor, true, anchorTouch, true);
    this.message = message;
    open();
  }

  private Tgx101MusicTaste taste () {
    return Tgx101MusicTaste.get(c.tdlib());
  }

  @Override
  protected void mainPage () {
    row(R.drawable.tgx101_thumb_down_outline_24, 0, Lang.getString(R.string.Tgx101DislikeTrack), false, () -> {
      dismiss();
      taste().setDisliked(message, true);
    }, null);
    final String artist = Tgx101MusicTaste.artistOf(message);
    if (artist != null) {
      final boolean hidden = taste().isArtistHidden(artist);
      row(R.drawable.baseline_block_24, 0, Lang.getString(hidden ? R.string.Tgx101DislikeArtistBack : R.string.Tgx101DislikeArtist, artist), false, () -> {
        dismiss();
        taste().setArtistHidden(artist, !hidden);
      }, null);
    }
    separator();
    row(R.drawable.baseline_visibility_24, 0, Lang.getString(R.string.Tgx101DislikeHidden), true, this::hiddenPage, this::hiddenPage);
  }

  private void hiddenPage () {
    page(() -> {
      back(R.string.Tgx101DislikeHidden);
      final List<String> artists = taste().hiddenArtists();
      final List<String[]> tracks = taste().hiddenTracks();
      if (artists.isEmpty() && tracks.isEmpty()) {
        row(0, 0, Lang.getString(R.string.Tgx101DislikeHiddenNone), false, this::dismiss, null);
        return;
      }
      for (String artist : artists) {
        row(R.drawable.baseline_block_24, 0, artist, false, () -> {
          taste().setArtistHidden(artist, false);
          hiddenPage();
        }, null);
      }
      for (int i = tracks.size() - 1; i >= 0 && i >= tracks.size() - 12; i--) {
        final String[] track = tracks.get(i);
        row(R.drawable.tgx101_thumb_down_outline_24, 0, track[1], false, () -> {
          taste().restoreTrack(track[0]);
          hiddenPage();
        }, null);
      }
    });
  }
}
