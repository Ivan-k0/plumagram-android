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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;

/**
 * TGx101 (approved mockups Е, Е2, user 2026-10-16): holding «Send» under the note field opens a menu over the keyboard,
 * at the finger, like the message menu in the chats — the keyboard stays where it is, blurred. The same finger slides to
 * an item and lifts to pick it; resting on «To folder…» / «With a tag…» / «Remind…» turns the menu into that list in the
 * same place.
 */
public final class Tgx101SendMenu extends Tgx101HoldMenu {
  private final TdApi.FormattedText note;
  private final Runnable picked, sentHere;

  public static Tgx101SendMenu show (ViewController<?> c, View anchor, TdApi.FormattedText note, Runnable picked, Runnable sentHere) {
    return new Tgx101SendMenu(c, anchor, note, picked, sentHere);
  }

  private Tgx101SendMenu (ViewController<?> c, View anchor, TdApi.FormattedText note, Runnable picked, Runnable sentHere) {
    super(c, anchor, false, null);
    this.note = note;
    this.picked = picked;
    this.sentHere = sentHere;
    open();
  }

  // Pages

  @Override
  protected void mainPage () {
    Tgx101SavedStore store = Tgx101SavedStore.get(c.tdlib());
    reactions(e -> pick(() -> Tgx101SavedActions.sendNote(c, note, null, saved -> Tgx101SavedKit.toggleReaction(c.tdlib(), saved, e, added -> sentHere.run()))));

    Tgx101SavedStore.Folder last = store.folder(store.lastFolderId);
    if (last != null) {
      row(0, last.color(), Lang.getString(R.string.Tgx101SendToFolder, last.name), false, () -> pick(() -> toFolder(last)), null);
    }
    row(R.drawable.baseline_folder_24, 0, Lang.getString(R.string.Tgx101SendToFolderMore), true, this::foldersPage, this::foldersPage);
    if (store.lastTag != null && !store.lastTag.isEmpty()) {
      final String tag = store.lastTag;
      row(R.drawable.baseline_label_24, 0, Lang.getString(R.string.Tgx101SendWithTag, tag), false, () -> pick(() -> withTag(tag)), null);
    }
    row(R.drawable.baseline_label_24, 0, Lang.getString(R.string.Tgx101SendWithTagMore), true, this::tagsPage, this::tagsPage);
    separator();
    row(R.drawable.baseline_schedule_24, 0, Lang.getString(R.string.Tgx101SavedNoteRemind), true, this::remindPage, this::remindPage);
  }

  private void foldersPage () {
    page(() -> {
      back(R.string.Tgx101SavedToFolder);
      for (Tgx101SavedStore.Folder f : Tgx101SavedStore.get(c.tdlib()).folders()) {
        row(0, f.color(), f.name, false, () -> pick(() -> toFolder(f)), null);
      }
      separator();
      row(R.drawable.baseline_create_new_folder_24, 0, Lang.getString(R.string.Tgx101SavedNewFolder), false, () -> dismissThen(() ->
        Tgx101SavedActions.askName(c, Lang.getString(R.string.Tgx101SavedNewFolder), null, name -> {
          picked.run();
          toFolder(Tgx101SavedStore.get(c.tdlib()).createFolder(name));
        })
      ), null);
    });
  }

  private void tagsPage () {
    page(() -> {
      back(R.string.Tgx101SavedTag);
      for (String t : Tgx101SavedStore.get(c.tdlib()).knownTags()) {
        row(0, Tgx101SavedListController.TAG_COLOR, "#" + t, false, () -> pick(() -> withTag(t)), null);
      }
      separator();
      row(R.drawable.baseline_add_24, 0, Lang.getString(R.string.Tgx101SendNewTag), false, () -> pick(() -> Tgx101SavedActions.sendNote(c, note, null, saved -> {
        sentHere.run();
        Tgx101SavedActions.tag(c, saved, sentHere);
      })), null);
    });
  }

  private void remindPage () {
    page(() -> {
      back(R.string.Tgx101SavedRemind);
      long[] at = Tgx101SavedActions.remindTimes();
      int[] names = Tgx101SavedActions.remindNames(at.length);
      for (int i = 0; i < at.length; i++) {
        final long when = at[i];
        row(R.drawable.baseline_schedule_24, 0, Lang.getString(names[i]) + " · " + Lang.getRelativeTimestamp(when, java.util.concurrent.TimeUnit.SECONDS), false, () -> pick(() -> {
          TdApi.MessageSendOptions options = new TdApi.MessageSendOptions();
          options.schedulingState = new TdApi.MessageSchedulingStateSendAtDate((int) when, 0);
          Tgx101SavedActions.sendNote(c, note, options, saved -> UI.showToast(Lang.getString(R.string.Tgx101SavedRemindDone, Lang.getRelativeTimestamp(when, java.util.concurrent.TimeUnit.SECONDS)), android.widget.Toast.LENGTH_SHORT));
        }), null);
      }
    });
  }

  // Picks

  private void toFolder (Tgx101SavedStore.Folder f) {
    Tgx101SavedActions.sendNote(c, note, null, saved -> {
      Tgx101SavedStore.get(c.tdlib()).putInFolder(f.id, Tgx101SavedKit.idsOf(saved));
      UI.showToast(Lang.getString(R.string.Tgx101SavedPutDone, f.name), android.widget.Toast.LENGTH_SHORT);
      sentHere.run();
    });
  }

  private void withTag (String tag) {
    Tgx101SavedActions.sendNote(c, note, null, saved -> {
      Tgx101SavedActions.applyTag(c, saved, tag, false);
      sentHere.run();
    });
  }

  /** the field is emptied only when the note goes somewhere */
  private void pick (Runnable action) {
    dismiss();
    picked.run();
    action.run();
  }
}
