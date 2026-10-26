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

/** TGx101 (user 2026-10-26 «функции ИИ отдельным подменю, не растягивай простыню»): all the AI switches on one screen */
public class Tgx101AiSettingsController extends RecyclerViewController<Void> implements View.OnClickListener {
  public Tgx101AiSettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101AiSettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101AiSection);
  }

  private SettingsAdapter adapter;

  /** what MagiX shows next to «AI functions» */
  public static String value () {
    Settings s = Settings.instance();
    if (!s.tgx101Ai()) return Lang.getString(R.string.Tgx101AiOff);
    int on = (s.tgx101AiCompose() ? 1 : 0) + (s.tgx101AiSummary() ? 1 : 0) + (s.tgx101AiMissed() ? 1 : 0) + (s.tgx101AiSort() ? 1 : 0);
    return Lang.getString(R.string.Tgx101AiOnCount, on + " / 4");
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Settings s = Settings.instance();
        int id = item.getId();
        if (id == R.id.btn_tgx101AiAll) {
          view.getToggler().setRadioEnabled(s.tgx101Ai(), isUpdate);
          return;
        }
        if (id == R.id.btn_tgx101AiCompose) {
          view.getToggler().setRadioEnabled(s.tgx101AiCompose(), isUpdate);
        } else if (id == R.id.btn_tgx101AiSummary) {
          view.getToggler().setRadioEnabled(s.tgx101AiSummary(), isUpdate);
        } else if (id == R.id.btn_tgx101AiMissed) {
          view.getToggler().setRadioEnabled(s.tgx101AiMissed(), isUpdate);
        } else if (id == R.id.btn_tgx101AiSort) {
          view.getToggler().setRadioEnabled(s.tgx101AiSort(), isUpdate);
        }
        view.setEnabledAnimated(s.tgx101Ai(), isUpdate);
      }
    };
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101AiAll, 0, R.string.Tgx101AiAll));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101AiInfo));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101AiCompose, 0, R.string.Tgx101AiComposeSetting));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101AiSummary, 0, R.string.Tgx101AiSummarySetting));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101AiMissed, 0, R.string.Tgx101AiMissedSetting));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101AiSort, 0, R.string.Tgx101AiSortSetting));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    Settings s = Settings.instance();
    if (id == R.id.btn_tgx101AiAll) {
      s.setTgx101Ai(adapter.toggleView(v)); // user 2026-10-21: all AI things off / on at once
      adapter.updateValuedSettingById(R.id.btn_tgx101AiCompose);
      adapter.updateValuedSettingById(R.id.btn_tgx101AiSummary);
      adapter.updateValuedSettingById(R.id.btn_tgx101AiMissed);
      adapter.updateValuedSettingById(R.id.btn_tgx101AiSort);
    } else if (!s.tgx101Ai()) {
      return;
    } else if (id == R.id.btn_tgx101AiCompose) {
      s.setTgx101AiCompose(adapter.toggleView(v));
    } else if (id == R.id.btn_tgx101AiSummary) {
      s.setTgx101AiSummary(adapter.toggleView(v));
    } else if (id == R.id.btn_tgx101AiMissed) {
      s.setTgx101AiMissed(adapter.toggleView(v));
    } else if (id == R.id.btn_tgx101AiSort) {
      s.setTgx101AiSort(adapter.toggleView(v));
    }
  }
}
