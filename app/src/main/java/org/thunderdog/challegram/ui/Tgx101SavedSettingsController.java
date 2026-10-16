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
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/** TGx101 (approved mockup 10): Saved settings — the «Saved» banner, what opens, home screen tiles, tags */
public class Tgx101SavedSettingsController extends RecyclerViewController<Void> implements View.OnClickListener {
  public Tgx101SavedSettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101SavedSettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101SavedSettings);
  }

  private SettingsAdapter adapter;

  private Tgx101SavedStore store () {
    return Tgx101SavedStore.get(tdlib);
  }

  private static int sheetName (int mode) {
    switch (mode) {
      case Tgx101SavedStore.SHEET_EXTERNAL_ONLY: return R.string.Tgx101SavedBannerExternal;
      case Tgx101SavedStore.SHEET_NEVER: return R.string.Tgx101SavedBannerNever;
    }
    return R.string.Tgx101SavedBannerAlways;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Tgx101SavedStore s = store();
        int id = item.getId();
        if (id == R.id.btn_tgx101SavedBanner) {
          view.setData(Lang.getString(sheetName(s.sheetMode)));
        } else if (id == R.id.btn_tgx101SavedOpen) {
          view.setData(Lang.getString(s.openMode == Tgx101SavedStore.OPEN_FEED ? R.string.Tgx101SavedOpenFeed : R.string.Tgx101SavedOpenHome));
        } else if (id == R.id.btn_tgx101SavedStorageOn) {
          view.getToggler().setRadioEnabled(s.storageOn, isUpdate);
        } else if (id == R.id.btn_tgx101SavedSwitch) {
          view.getToggler().setRadioEnabled(s.switchButton, isUpdate);
        } else if (id == R.id.btn_tgx101SavedInboxTile) {
          view.getToggler().setRadioEnabled(s.inboxTile, isUpdate);
        } else if (id == R.id.btn_tgx101SavedShowPinned) {
          view.getToggler().setRadioEnabled(s.showPinned, isUpdate);
        } else if (id == R.id.btn_tgx101SavedHashtag) {
          view.getToggler().setRadioEnabled(s.hashtagOwn, isUpdate);
        } else if (id == R.id.btn_tgx101SavedPanelLabels) {
          view.getToggler().setRadioEnabled(s.panelLabels, isUpdate);
        }
      }
    };
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    // (approved mockup 8 Г, user 2026-10-16)
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SavedStorageSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SavedStorageOn, 0, R.string.Tgx101SavedStorageOn));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101SavedOpen, 0, R.string.Tgx101SavedOpenSetting));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SavedSwitch, 0, R.string.Tgx101SavedSwitchSetting));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101SavedSwitchReset, 0, R.string.Tgx101SavedSwitchReset));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101SavedStorageDesc));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SavedBannerSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101SavedBanner, 0, R.string.Tgx101SavedBannerSetting));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101SavedBannerDesc));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SavedHomeSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SavedInboxTile, 0, R.string.Tgx101SavedInboxTileSetting));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SavedShowPinned, 0, R.string.Tgx101SavedShowPinnedSetting));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101SavedHomeDesc));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SavedTagsSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SavedHashtag, 0, R.string.Tgx101SavedHashtagSetting));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101SavedHashtagDesc));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SavedSelectSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SavedPanelLabels, 0, R.string.Tgx101SavedPanelLabelsSetting));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    Tgx101SavedStore s = store();
    if (id == R.id.btn_tgx101SavedBanner) {
      int[] modes = {Tgx101SavedStore.SHEET_ALWAYS, Tgx101SavedStore.SHEET_EXTERNAL_ONLY, Tgx101SavedStore.SHEET_NEVER};
      showOptions(Lang.getString(R.string.Tgx101SavedBannerSetting), new int[] {R.id.btn_done, R.id.btn_edit, R.id.btn_close},
        new String[] {Lang.getString(sheetName(modes[0])), Lang.getString(sheetName(modes[1])), Lang.getString(sheetName(modes[2]))}, null, null, (itemView, optionId) -> {
          s.sheetMode = optionId == R.id.btn_edit ? modes[1] : optionId == R.id.btn_close ? modes[2] : modes[0];
          s.save();
          adapter.updateValuedSettingById(R.id.btn_tgx101SavedBanner);
          return true;
        });
    } else if (id == R.id.btn_tgx101SavedOpen) {
      showOptions(Lang.getString(R.string.Tgx101SavedOpenSetting), new int[] {R.id.btn_done, R.id.btn_edit},
        new String[] {Lang.getString(R.string.Tgx101SavedOpenHome), Lang.getString(R.string.Tgx101SavedOpenFeed)}, null, null, (itemView, optionId) -> {
          s.openMode = optionId == R.id.btn_edit ? Tgx101SavedStore.OPEN_FEED : Tgx101SavedStore.OPEN_HOME;
          s.save();
          adapter.updateValuedSettingById(R.id.btn_tgx101SavedOpen);
          return true;
        });
    } else if (id == R.id.btn_tgx101SavedStorageOn) {
      s.storageOn = adapter.toggleView(v);
      s.save();
    } else if (id == R.id.btn_tgx101SavedSwitch) {
      s.switchButton = adapter.toggleView(v);
      s.save();
    } else if (id == R.id.btn_tgx101SavedSwitchReset) {
      s.switchButton = true;
      s.switchLeft[0] = s.switchLeft[1] = false;
      s.switchY[0] = s.switchY[1] = -1f;
      s.save();
      adapter.updateValuedSettingById(R.id.btn_tgx101SavedSwitch);
      UI.showToast(R.string.Done, android.widget.Toast.LENGTH_SHORT);
    } else if (id == R.id.btn_tgx101SavedInboxTile) {
      s.inboxTile = adapter.toggleView(v);
      s.save();
    } else if (id == R.id.btn_tgx101SavedShowPinned) {
      s.showPinned = adapter.toggleView(v);
      s.save();
    } else if (id == R.id.btn_tgx101SavedHashtag) {
      s.hashtagOwn = adapter.toggleView(v);
      s.save();
    } else if (id == R.id.btn_tgx101SavedPanelLabels) {
      s.panelLabels = adapter.toggleView(v);
      s.save();
    }
  }
}
