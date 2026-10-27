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

/**
 * TGx101 (user 2026-10-27 «уведомления в приложении — настроить грамотно», as the official app's «In-app»): while the
 * app is open — how a new message shows (a system notification / our silent capsule / nothing), the app's sound and
 * vibration for it, and the sounds inside an open chat
 */
public class Tgx101ForegroundNotifController extends RecyclerViewController<Void> implements View.OnClickListener {
  public Tgx101ForegroundNotifController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101ForegroundNotif;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101ForegroundNotif);
  }

  private static final int[] SHOW_IDS = {R.id.btn_tgx101FgSystem, R.id.btn_tgx101FgCapsule, R.id.btn_tgx101FgNone};
  private static final int[] SHOW_NAMES = {R.string.Tgx101FgSystem, R.string.Tgx101FgCapsule, R.string.Tgx101FgNone};

  /** what MagiX shows next to «When the app is open» */
  public static String value () {
    int show = Settings.instance().tgx101FgShow();
    return Lang.getString(SHOW_NAMES[Math.max(0, Math.min(SHOW_NAMES.length - 1, show))]);
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        Settings s = Settings.instance();
        if (id == R.id.btn_tgx101FgSound) {
          view.getToggler().setRadioEnabled(s.tgx101FgSound(), isUpdate);
        } else if (id == R.id.btn_tgx101FgVibrate) {
          view.getToggler().setRadioEnabled(s.tgx101FgVibrate(), isUpdate);
        } else if (id == R.id.btn_inApp_chatSounds) {
          view.getToggler().setRadioEnabled(tdlib.notifications().areInAppChatSoundsEnabled(), isUpdate);
        }
      }
    };
    int show = Settings.instance().tgx101FgShow();
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101FgShowHeader));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < SHOW_IDS.length; i++) {
      if (i > 0) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, SHOW_IDS[i], 0, SHOW_NAMES[i], R.id.btn_tgx101FgShowGroup, show == i));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101FgShowHint));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101FgSound, 0, R.string.Tgx101FgSound));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101FgVibrate, 0, R.string.Tgx101FgVibrate));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_inApp_chatSounds, 0, R.string.InChatSound));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101FgSoundHint));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    for (int i = 0; i < SHOW_IDS.length; i++) {
      if (SHOW_IDS[i] == id) {
        adapter.processToggle(v);
        Settings.instance().setTgx101FgShow(i);
        org.thunderdog.challegram.Tgx101Diag.mark("settings: app open → show " + i);
        return;
      }
    }
    if (id == R.id.btn_tgx101FgSound) {
      Settings.instance().setTgx101FgSound(adapter.toggleView(v));
    } else if (id == R.id.btn_tgx101FgVibrate) {
      Settings.instance().setTgx101FgVibrate(adapter.toggleView(v));
    } else if (id == R.id.btn_inApp_chatSounds) {
      tdlib.notifications().toggleInAppChatSoundsEnabled();
      adapter.toggleView(v);
    }
  }
}
