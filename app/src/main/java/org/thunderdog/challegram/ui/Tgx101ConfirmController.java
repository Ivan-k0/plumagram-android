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
 * TGx101 MagiX → «Подтверждение» (user 2026-11-13, mockup approved): what to ask about before it goes — voices and rounds,
 * stickers, photos and videos, GIFs — and calls (Telegram X's own «ask before calling»).
 */
public class Tgx101ConfirmController extends RecyclerViewController<Void> implements View.OnClickListener {
  public Tgx101ConfirmController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101Confirm;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101Confirm);
  }

  private static final int[] IDS = {R.id.btn_tgx101ConfirmVoice, R.id.btn_tgx101ConfirmSticker, R.id.btn_tgx101ConfirmMedia, R.id.btn_tgx101ConfirmGif};
  private static final int[] NAMES = {R.string.Tgx101ConfirmVoice, R.string.Tgx101ConfirmSticker, R.string.Tgx101ConfirmMedia, R.string.Tgx101ConfirmGif};
  private static final int[] ICONS = {R.drawable.baseline_mic_24, R.drawable.deproko_baseline_stickers_24, R.drawable.baseline_image_24, R.drawable.deproko_baseline_gif_24};

  private SettingsAdapter adapter;

  /** what MagiX shows under «Подтверждение» */
  public static String value () {
    int on = 0;
    for (int k = 0; k < IDS.length; k++) if (Settings.instance().tgx101Confirm(k)) on++;
    if (Settings.instance().needOutboundCallsPrompt()) on++;
    return on == 0 ? Lang.getString(R.string.Tgx101ConfirmNone) : Lang.getString(R.string.Tgx101ConfirmCount, on);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        for (int k = 0; k < IDS.length; k++) {
          if (id == IDS[k]) {
            view.getToggler().setRadioEnabled(Settings.instance().tgx101Confirm(k), isUpdate);
            return;
          }
        }
        if (id == R.id.btn_tgx101ConfirmCalls) {
          view.getToggler().setRadioEnabled(Settings.instance().needOutboundCallsPrompt(), isUpdate);
        }
      }
    };
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101ConfirmBeforeSending));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int k = 0; k < IDS.length; k++) {
      if (k > 0) items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, IDS[k], ICONS[k], NAMES[k]));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101ConfirmOther));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101ConfirmCalls, R.drawable.baseline_call_24, R.string.Tgx101ConfirmCalls));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101ConfirmHint));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    for (int k = 0; k < IDS.length; k++) {
      if (id == IDS[k]) {
        boolean on = adapter.toggleView(v);
        Settings.instance().setTgx101Confirm(k, on);
        org.thunderdog.challegram.Tgx101Diag.mark("confirm settings: kind " + k + " " + on);
        return;
      }
    }
    if (id == R.id.btn_tgx101ConfirmCalls) {
      Settings.instance().setNeedOutboundCallsPrompt(adapter.toggleView(v));
    }
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (adapter != null) {
      // «Больше не спрашивать» in the card switches a kind off: the switches follow
      for (int i : IDS) adapter.updateValuedSettingById(i);
    }
  }
}
