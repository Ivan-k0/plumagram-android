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
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * TGx101 «Газета» settings (user 2026-10-27): how photos show (as is / a thumbnail), whether long posts fold, the
 * archived channels, and every subscribed channel with a tick — «Select all» / «Deselect all» on top of the list
 */
public class Tgx101GazetaSettingsController extends RecyclerViewController<Void> implements View.OnClickListener {
  public Tgx101GazetaSettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101GazetaSettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101GazetaSettings);
  }

  private SettingsAdapter adapter;
  private long[] channels = new long[0];

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        if (id == R.id.btn_tgx101GazetaFold) {
          view.getToggler().setRadioEnabled(Tgx101Gazeta.foldLong(), isUpdate);
        } else if (id == R.id.btn_tgx101GazetaArchive) {
          view.getToggler().setRadioEnabled(Tgx101Gazeta.withArchive(tdlib), isUpdate);
        }
      }
    };
    recyclerView.setAdapter(adapter);
    build();
  }

  private void build () {
    Tgx101Gazeta.allChannels(tdlib, Tgx101Gazeta.withArchive(tdlib), all -> {
      if (isDestroyed()) return;
      channels = all;
      Set<Long> off = Tgx101Gazeta.excluded(tdlib);
      int photos = Tgx101Gazeta.photos();
      List<ListItem> items = new ArrayList<>();
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101GazetaPhotos));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101GazetaPhotosAsIs, 0, R.string.Tgx101GazetaPhotosAsIs, R.id.btn_tgx101GazetaPhotos, photos == Tgx101Gazeta.PHOTOS_AS_IS));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101GazetaPhotosThumb, 0, R.string.Tgx101GazetaPhotosThumb, R.id.btn_tgx101GazetaPhotos, photos == Tgx101Gazeta.PHOTOS_THUMB));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101GazetaFold, 0, R.string.Tgx101GazetaFold));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101GazetaArchive, 0, R.string.Tgx101GazetaArchive));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, Lang.getString(R.string.Tgx101GazetaChannelsN, all.length)));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101GazetaSelectAll, R.drawable.baseline_done_all_24, R.string.Tgx101GazetaSelectAll));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101GazetaSelectNone, R.drawable.baseline_remove_circle_24, R.string.Tgx101GazetaSelectNone));
      for (long chatId : all) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR, R.id.btn_tgx101GazetaChannel, 0, tdlib.chatTitle(chatId), !off.contains(chatId)).setLongId(chatId).setLongValue(chatId));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      adapter.setItems(items, false);
    });
  }

  private void setAll (boolean on) {
    Set<Long> off = Tgx101Gazeta.excluded(tdlib);
    for (long id : channels) {
      if (on) off.remove(id);
      else off.add(id);
    }
    Tgx101Gazeta.setExcluded(tdlib, off);
    for (ListItem item : adapter.getItems()) {
      if (item.getId() == R.id.btn_tgx101GazetaChannel) item.setSelected(on);
    }
    adapter.notifyDataSetChanged();
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_tgx101GazetaPhotosAsIs || id == R.id.btn_tgx101GazetaPhotosThumb) {
      adapter.processToggle(v);
      Tgx101Gazeta.setPhotos(id == R.id.btn_tgx101GazetaPhotosThumb ? Tgx101Gazeta.PHOTOS_THUMB : Tgx101Gazeta.PHOTOS_AS_IS);
    } else if (id == R.id.btn_tgx101GazetaFold) {
      Tgx101Gazeta.setFoldLong(adapter.toggleView(v));
    } else if (id == R.id.btn_tgx101GazetaArchive) {
      Tgx101Gazeta.setWithArchive(tdlib, adapter.toggleView(v));
      build();
    } else if (id == R.id.btn_tgx101GazetaSelectAll) {
      setAll(true);
    } else if (id == R.id.btn_tgx101GazetaSelectNone) {
      setAll(false);
    } else if (id == R.id.btn_tgx101GazetaChannel) {
      ListItem item = (ListItem) v.getTag();
      boolean on = adapter.toggleView(v);
      Set<Long> off = Tgx101Gazeta.excluded(tdlib);
      if (on) off.remove(item.getLongId());
      else off.add(item.getLongId());
      Tgx101Gazeta.setExcluded(tdlib, off);
    }
  }
}
