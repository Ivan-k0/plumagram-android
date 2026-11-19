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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.MediaRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101 (user 2026-11-19 «добавим функцию добавления музыки в профиль… как в эталоне», mockup «Музыка профиля»: the
 * tab, the order changed by dragging after a hold): the songs a person added to the profile. A tap plays them as one
 * queue; on one's own profile a hold drags a song to another place, a swipe to the left takes it off the profile.
 */
public class Tgx101ProfileMusicController extends SharedCommonController {
  public Tgx101ProfileMusicController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    setFilter(new TdApi.SearchMessagesFilterAudio());
  }

  private static final int LIMIT = 100;

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101ProfileMusicTab);
  }

  @Override
  public int getIcon () {
    return R.drawable.baseline_music_note_24;
  }

  @Override
  protected String getExplainedTitle () {
    return Lang.getString(R.string.Tgx101ProfileMusic); // (crash 2026-11-19 22:32 «Stub!»: the base has none)
  }

  private long userId () {
    return tdlib.chatUserId(getChatId());
  }

  private boolean own () {
    return tdlib.isSelfUserId(userId());
  }

  @Override
  protected TdApi.Function<?> buildRequest (long chatId, TdApi.MessageTopic topicId, String query, long offset, String secretOffset, int limit) {
    return new TdApi.GetUserProfileAudios(tdlib.chatUserId(chatId), 0, LIMIT);
  }

  @Override
  protected boolean supportsLoadingMore (boolean isMore) {
    return false;
  }

  @Override
  protected boolean canSearch () {
    return false;
  }

  @Override
  protected boolean needDateSectionSplitting () {
    return false;
  }

  @Override
  protected CharSequence buildTotalCount (ArrayList<InlineResult<?>> data) {
    return Lang.pluralBold(R.string.xAudios, data.size());
  }

  /** The profile's songs aren't messages: the player and the list get messages of no chat around them */
  public static List<TdApi.Message> asMessages (TdApi.Audio[] audios) {
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

  private static int fileIdOf (InlineResult<?> result) {
    TdApi.Message m = result != null ? result.getMessage() : null;
    return m != null && m.content instanceof TdApi.MessageAudio ? ((TdApi.MessageAudio) m.content).audio.audio.id : 0;
  }

  // Own profile: drag after a hold to reorder, swipe left to take off

  private ItemTouchHelper touchHelper;
  private int movedFileId;

  @Override
  protected void onCreateView (Context context, MediaRecyclerView recyclerView, SettingsAdapter adapter) {
    super.onCreateView(context, recyclerView, adapter);
    if (!own()) return;
    touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
      private boolean isSong (RecyclerView.ViewHolder h) {
        int p = h.getBindingAdapterPosition();
        return p >= 0 && p < adapter.getItems().size() && adapter.getItems().get(p).getViewType() == ListItem.TYPE_CUSTOM_INLINE;
      }

      @Override
      public int getMovementFlags (@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder h) {
        return isSong(h) ? makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) : 0;
      }

      @Override
      public boolean isLongPressDragEnabled () {
        return true;
      }

      @Override
      public boolean canDropOver (@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder current, @NonNull RecyclerView.ViewHolder target) {
        return isSong(target);
      }

      @Override
      public boolean onMove (@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
        int a = from.getBindingAdapterPosition(), b = to.getBindingAdapterPosition();
        if (a < 0 || b < 0 || a == b) return false;
        List<ListItem> items = adapter.getItems();
        // songs lie with separators between them: two songs change places, the separators stay
        ListItem moved = items.get(a);
        movedFileId = fileIdOf((InlineResult<?>) moved.getData());
        items.set(a, items.get(b));
        items.set(b, moved);
        if (Math.abs(a - b) == 1) {
          adapter.notifyItemMoved(a, b);
        } else if (b > a) {
          adapter.notifyItemMoved(a, b);
          adapter.notifyItemMoved(b - 1, a);
        } else {
          adapter.notifyItemMoved(a, b);
          adapter.notifyItemMoved(b + 1, a);
        }
        return true;
      }

      @Override
      public void clearView (@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder h) {
        super.clearView(rv, h);
        if (movedFileId == 0) return;
        int fileId = movedFileId;
        movedFileId = 0;
        // the list's order as it stands now: the data follows it, the server is told where the moved song went
        ArrayList<InlineResult<?>> order = new ArrayList<>();
        for (ListItem item : adapter.getItems()) {
          if (item.getViewType() == ListItem.TYPE_CUSTOM_INLINE && item.getData() instanceof InlineResult) order.add((InlineResult<?>) item.getData());
        }
        int after = 0;
        for (InlineResult<?> r : order) {
          int id = fileIdOf(r);
          if (id == fileId) break;
          after = id;
        }
        if (data != null) {
          data.clear();
          data.addAll(order);
        }
        org.thunderdog.challegram.Tgx101Diag.mark("profile music: file " + fileId + " moved after " + after);
        tdlib.send(new TdApi.SetProfileAudioPosition(fileId, after), (ok, error) -> {
          if (error != null) UI.post(() -> {
            UI.showError(error);
            if (!isDestroyed()) reloadSongs();
          });
        });
      }

      @Override
      public void onSwiped (@NonNull RecyclerView.ViewHolder h, int direction) {
        int p = h.getBindingAdapterPosition();
        if (p < 0 || p >= adapter.getItems().size()) return;
        InlineResult<?> result = (InlineResult<?>) adapter.getItems().get(p).getData();
        int fileId = fileIdOf(result);
        if (data != null) data.remove(result);
        buildCells();
        if (fileId == 0) return;
        org.thunderdog.challegram.Tgx101Diag.mark("profile music: file " + fileId + " taken off");
        tdlib.send(new TdApi.RemoveProfileAudio(fileId), (ok, error) -> UI.post(() -> {
          if (error != null) {
            UI.showError(error);
            if (!isDestroyed()) reloadSongs();
          } else {
            UI.showToast(R.string.Tgx101ProfileMusicRemoved, android.widget.Toast.LENGTH_SHORT);
            Tgx101ProfileMusic.forget(fileId);
          }
        }));
      }
    });
    touchHelper.attachToRecyclerView(recyclerView);
  }

  /** a song added or taken off elsewhere (the profile's details changed) */
  public void tgx101Refresh () {
    if (!isDestroyed() && data != null) reloadSongs();
  }

  private void reloadSongs () {
    tdlib.send(new TdApi.GetUserProfileAudios(userId(), 0, LIMIT), (result, error) -> UI.post(() -> {
      if (isDestroyed() || result == null) return;
      ArrayList<InlineResult<?>> list = new ArrayList<>();
      for (TdApi.Message m : asMessages(result.audios)) {
        InlineResult<?> r = parseObject(m);
        if (r != null) list.add(r);
      }
      data = list;
      buildCells();
    }));
  }

  @Override
  protected boolean needsCustomLongClickListener () {
    return own() || super.needsCustomLongClickListener(); // a hold drags, it doesn't select
  }

  @Override
  protected boolean onLongClick (android.view.View v, ListItem item) {
    if (own()) return false; // the drag helper takes the hold
    return super.onLongClick(v, item);
  }
}
