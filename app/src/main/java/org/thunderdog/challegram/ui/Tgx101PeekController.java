/*
 * This file is a part of TGx101, a modification of Telegram X
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
import android.widget.Toast;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101Peek;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.SliderWrapView;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101 «Не подглядывай!» (user 2026-10-22): MagiX → Features. The switch, where it works (every chat or the chosen ones,
 * picked as in a folder) and how strongly avatars, names and the last message are blurred in the chat list.
 */
public class Tgx101PeekController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;

  public Tgx101PeekController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101Peek;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101Peek);
  }

  /** For the row in «Features» */
  public static String value () {
    if (!Tgx101Peek.isOn()) return Lang.getString(R.string.Tgx101PeekOff);
    if (Tgx101Peek.isAll()) return Lang.getString(R.string.Tgx101PeekAllChats);
    return Lang.getString(R.string.Tgx101PeekChatsCount, Tgx101Peek.chats().size());
  }

  /** The quick buttons (bird, «Settings» hold, side menu): switch and say what happened. Returns the new state */
  public static boolean toggleWithToast () {
    boolean on = Tgx101Peek.toggle();
    UI.showToast(on ? R.string.Tgx101PeekToastOn : R.string.Tgx101PeekToastOff, Toast.LENGTH_SHORT);
    BaseActivity activity = UI.getUiContext();
    if (activity != null && activity.getDrawer() != null) activity.getDrawer().tgx101UpdatePeekItem(on);
    return on;
  }

  private static String[] sliderValues () {
    String[] values = new String[Tgx101Peek.STEPS.length];
    for (int i = 0; i < values.length; i++) {
      values[i] = Tgx101Peek.STEPS[i] == 0 ? Lang.getString(R.string.Tgx101PeekNone) : Tgx101Peek.STEPS[i] + "%";
    }
    return values;
  }

  private static int partOf (int id) {
    if (id == R.id.btn_tgx101PeekAvatar) return Tgx101Peek.PART_AVATAR;
    if (id == R.id.btn_tgx101PeekTitle) return Tgx101Peek.PART_TITLE;
    return Tgx101Peek.PART_TEXT;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        if (id == R.id.btn_tgx101PeekOn) {
          view.getToggler().setRadioEnabled(Tgx101Peek.isOn(), isUpdate);
        } else if (id == R.id.btn_tgx101PeekChats) {
          int count = Tgx101Peek.chats().size();
          view.setData(count == 0 ? Lang.getString(R.string.Tgx101PeekNoChats) : Lang.getString(R.string.Tgx101PeekChatsCount, count));
        }
      }

      @Override
      protected void onSliderValueChanged (ListItem item, SliderWrapView view, int value, int oldValue) {
        Tgx101Peek.setStrength(partOf(item.getId()), Tgx101Peek.STEPS[value]);
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (adapter != null) adapter.updateValuedSettingById(R.id.btn_tgx101PeekChats); // back from picking the chats
  }

  private ListItem slider (int id, int titleRes, int part) {
    return new ListItem(ListItem.TYPE_SLIDER, id, 0, titleRes).setSliderInfo(sliderValues(), Tgx101Peek.stepIndex(Tgx101Peek.strength(part)));
  }

  private List<ListItem> buildItems () {
    boolean all = Tgx101Peek.isAll();
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101PeekOn, R.drawable.baseline_eye_off_24, R.string.Tgx101PeekEnabled));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101PeekHint));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101PeekWhere));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101PeekAll, 0, R.string.Tgx101PeekAllChats, R.id.btn_tgx101PeekAll, all));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101PeekSelected, 0, R.string.Tgx101PeekSelectedChats, R.id.btn_tgx101PeekAll, !all));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101PeekChats, 0, R.string.Tgx101PeekChoose));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101PeekStrength));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(slider(R.id.btn_tgx101PeekAvatar, R.string.Tgx101PeekAvatars, Tgx101Peek.PART_AVATAR));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(slider(R.id.btn_tgx101PeekTitle, R.string.Tgx101PeekTitles, Tgx101Peek.PART_TITLE));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(slider(R.id.btn_tgx101PeekText, R.string.Tgx101PeekTexts, Tgx101Peek.PART_TEXT));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101PeekQuickHint));
    return items;
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_tgx101PeekOn) {
      boolean on = adapter.toggleView(v);
      Tgx101Peek.setOn(on);
      if (context().getDrawer() != null) context().getDrawer().tgx101UpdatePeekItem(on);
    } else if (id == R.id.btn_tgx101PeekAll || id == R.id.btn_tgx101PeekSelected) {
      adapter.processToggle(v);
      Tgx101Peek.setAll(id == R.id.btn_tgx101PeekAll);
      if (id == R.id.btn_tgx101PeekSelected && Tgx101Peek.chats().isEmpty()) pickChats();
    } else if (id == R.id.btn_tgx101PeekChats) {
      pickChats();
    }
  }

  private void pickChats () {
    SelectChatsController c = new SelectChatsController(context, tdlib);
    c.setArguments(SelectChatsController.Arguments.tgx101SelectChats((mode, chatIds, chatTypes) -> {
      Tgx101Peek.setChats(chatIds);
      if (!chatIds.isEmpty() && Tgx101Peek.isAll()) {
        Tgx101Peek.setAll(false); // chose chats — means «only in them»
        if (adapter != null) {
          adapter.setItems(buildItems(), false);
        }
      }
      if (adapter != null) adapter.updateValuedSettingById(R.id.btn_tgx101PeekChats);
    }, Tgx101Peek.chats()));
    navigateTo(c);
  }
}
