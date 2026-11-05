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
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.navigation.ViewController;

import java.util.List;

import me.vkryl.core.lambda.RunnableData;

/**
 * (user 2026-11-05, video of 0.2.727 «зажатие сердца открывает меню папок не так как обычно, и вверху, а не под пальцем»): the music
 * player's held ♥ is the same menu as held «Send» in the Storage. What is picked goes to the track's copy in the Storage, made
 * when there's none yet.
 */
final class Tgx101HeartMenu extends Tgx101HoldMenu {
  interface Host {
    /** Runs on the track's copy in the Storage */
    void withSavedCopy (RunnableData<List<TdApi.Message>> action);
    /** Saves there; 0: the folder the ♥ saves to, made when missing. It's where the next tap saves */
    void saveToFolder (long folderId);
  }

  private final Host host;

  Tgx101HeartMenu (ViewController<?> c, View anchor, @Nullable View.OnTouchListener anchorTouch, Host host) {
    super(c, anchor, true, anchorTouch);
    this.host = host;
    open();
  }

  private void pick (Runnable action) {
    dismiss();
    action.run();
  }

  @Override
  protected void mainPage () {
    Tgx101SavedStore store = Tgx101SavedStore.get(c.tdlib());
    reactions(e -> pick(() -> host.withSavedCopy(saved -> Tgx101SavedKit.toggleReaction(c.tdlib(), saved, e, null))));
    String defaultName = Lang.getString(R.string.Tgx101PlayerMusicFolder);
    Tgx101SavedStore.Folder folder = store.playerFolderIfAny(defaultName);
    row(folder != null ? 0 : R.drawable.baseline_folder_24, folder != null ? folder.color() : 0, Lang.getString(R.string.Tgx101SendToFolder, folder != null ? folder.name : defaultName), false, () -> pick(() -> host.saveToFolder(0)), null);
    row(R.drawable.baseline_folder_24, 0, Lang.getString(R.string.Tgx101SendToFolderMore), true, this::foldersPage, this::foldersPage);
    row(R.drawable.baseline_label_24, 0, Lang.getString(R.string.Tgx101SendWithTagMore), true, this::tagsPage, this::tagsPage);
  }

  private void foldersPage () {
    page(() -> {
      back(R.string.Tgx101SavedToFolder);
      for (Tgx101SavedStore.Folder f : Tgx101SavedStore.get(c.tdlib()).folders()) {
        row(0, f.color(), f.name, false, () -> pick(() -> host.saveToFolder(f.id)), null);
      }
      separator();
      row(R.drawable.baseline_create_new_folder_24, 0, Lang.getString(R.string.Tgx101SavedNewFolder), false, () -> dismissThen(() ->
        Tgx101SavedActions.askName(c, Lang.getString(R.string.Tgx101SavedNewFolder), null, name -> host.saveToFolder(Tgx101SavedStore.get(c.tdlib()).createFolder(name).id))
      ), null);
    });
  }

  private void tagsPage () {
    page(() -> {
      back(R.string.Tgx101SavedTag);
      for (String t : Tgx101SavedStore.get(c.tdlib()).knownTags()) {
        row(0, Tgx101SavedListController.TAG_COLOR, "#" + t, false, () -> pick(() -> host.withSavedCopy(saved -> Tgx101SavedActions.applyTag(c, saved, t, false))), null);
      }
      separator();
      row(R.drawable.baseline_add_24, 0, Lang.getString(R.string.Tgx101SendNewTag), false, () -> dismissThen(() -> host.withSavedCopy(saved -> Tgx101SavedActions.tag(c, saved, null))), null);
    });
  }
}
