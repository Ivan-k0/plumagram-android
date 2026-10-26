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
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/** TGx101 (user 2026-10-26 «настройки птички и всё, что с ней связано, вынеси в меню Фишки»): the bird's own screen */
public class Tgx101BirdSettingsController extends RecyclerViewController<Void> implements View.OnClickListener {
  public Tgx101BirdSettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101BirdSettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101BirdSection);
  }

  private SettingsAdapter adapter;

  public static String doubleTapValue () {
    int mode = Settings.instance().tgx101BirdDoubleTap();
    if (mode < 0 || mode >= Tgx101Bird.DOUBLE_TAP_TITLES.length) mode = 0;
    return Lang.getString(Tgx101Bird.DOUBLE_TAP_TITLES[mode]);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        boolean on = Settings.instance().tgx101Bird();
        if (id == R.id.btn_tgx101Bird) {
          view.getToggler().setRadioEnabled(on, isUpdate);
        } else if (id == R.id.btn_tgx101BirdInChats) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101BirdInChats(), isUpdate);
          view.setEnabledAnimated(on, isUpdate);
        } else if (id == R.id.btn_tgx101BirdRing) {
          view.setData(Tgx101GesturesController.birdRingValue());
          view.setEnabledAnimated(on, isUpdate);
        } else if (id == R.id.btn_tgx101BirdDoubleTap) {
          view.setData(doubleTapValue());
          view.setEnabledAnimated(on, isUpdate);
        }
      }
    };
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101Bird, 0, R.string.Tgx101Bird));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101BirdInChats, 0, R.string.Tgx101BirdInChats));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101BirdHint));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101BirdRing, 0, R.string.Tgx101BirdRing));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101BirdDoubleTap, 0, R.string.Tgx101BirdDoubleTap));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101BirdDoubleTapHint));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  private void updateAll () {
    adapter.updateValuedSettingById(R.id.btn_tgx101BirdInChats);
    adapter.updateValuedSettingById(R.id.btn_tgx101BirdRing);
    adapter.updateValuedSettingById(R.id.btn_tgx101BirdDoubleTap);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_tgx101Bird) {
      Settings.instance().setTgx101Bird(adapter.toggleView(v));
      updateAll();
    } else if (!Settings.instance().tgx101Bird()) {
      return;
    } else if (id == R.id.btn_tgx101BirdInChats) {
      Settings.instance().setTgx101BirdInChats(adapter.toggleView(v)); // a chat opened after this has it
    } else if (id == R.id.btn_tgx101BirdRing) {
      Tgx101GesturesController.chooseBirdRing(this, () -> adapter.updateValuedSettingById(R.id.btn_tgx101BirdRing));
    } else if (id == R.id.btn_tgx101BirdDoubleTap) {
      int n = Tgx101Bird.DOUBLE_TAP_TITLES.length;
      int[] ids = new int[n];
      String[] names = new String[n];
      for (int i = 0; i < n; i++) {
        ids[i] = 2200000 + i;
        names[i] = Lang.getString(Tgx101Bird.DOUBLE_TAP_TITLES[i]);
      }
      showOptions(Lang.getString(R.string.Tgx101BirdDoubleTap), ids, names, null, null, (itemView, optionId) -> {
        Settings.instance().setTgx101BirdDoubleTap(optionId - 2200000);
        adapter.updateValuedSettingById(R.id.btn_tgx101BirdDoubleTap);
        return true;
      });
    }
  }
}
