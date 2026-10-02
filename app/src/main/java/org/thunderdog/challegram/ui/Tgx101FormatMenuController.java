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

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/** TGx101: order and visibility of the formatting items in the text selection menu of the message input. */
public class Tgx101FormatMenuController extends RecyclerViewController<Void> implements View.OnClickListener {
  /** In the official order */
  public static final int[] IDS = {
    R.id.btn_bold, R.id.btn_italic, R.id.btn_monospace, R.id.btn_strikethrough, R.id.btn_underline,
    R.id.btn_link, R.id.btn_quote, R.id.btn_spoiler, R.id.btn_plain
  };
  private static final int[] NAMES = {
    R.string.TextFormatBold, R.string.TextFormatItalic, R.string.TextFormatMonospace, R.string.TextFormatStrikethrough, R.string.TextFormatUnderline,
    R.string.TextFormatLink, R.string.TextFormatQuote, R.string.TextFormatSpoiler, R.string.TextFormatClear
  };
  private static final int[] ICONS = {
    R.drawable.baseline_format_bold_24, R.drawable.baseline_format_italic_24, R.drawable.baseline_code_24, R.drawable.baseline_strikethrough_s_24, R.drawable.baseline_format_underlined_24,
    R.drawable.baseline_link_24, R.drawable.baseline_format_quote_close_24, R.drawable.baseline_eye_off_24, R.drawable.baseline_format_clear_24
  };

  public static int iconOf (int id) {
    int i = indexOf(IDS, id);
    return i >= 0 ? ICONS[i] : 0;
  }

  public static int nameOf (int id) {
    int i = indexOf(IDS, id);
    return i >= 0 ? NAMES[i] : 0;
  }

  /** Ids in the user's order; hidden ones are negative */
  public static int[] getOrder () {
    int[] saved = Settings.instance().getTgx101FormatMenu();
    ArrayList<Integer> result = new ArrayList<>();
    if (saved != null) {
      for (int entry : saved) {
        int id = Math.abs(entry);
        if (indexOf(IDS, id) != -1 && !result.contains(id) && !result.contains(-id)) {
          result.add(entry);
        }
      }
    }
    for (int id : IDS) {
      if (!result.contains(id) && !result.contains(-id)) {
        result.add(id);
      }
    }
    int[] order = new int[result.size()];
    for (int i = 0; i < order.length; i++) order[i] = result.get(i);
    return order;
  }

  private SettingsAdapter adapter;

  public Tgx101FormatMenuController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101FormatMenu;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101FormatMenu);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_tgx101BarItem) {
          int bar = (int) item.getLongId() / 100, position = (int) item.getLongId() % 100;
          java.util.List<String> order = Tgx101BarOrder.getOrder(bar);
          view.setData(position < order.size() && order.get(position).startsWith("-") ? Lang.getString(R.string.Tgx101FormatHidden) : Integer.toString(position + 1));
        } else if (item.getId() == R.id.btn_tgx101MenuAction) {
          int[] order = getOrder();
          int position = (int) item.getLongId();
          view.setData(order[position] < 0 ? Lang.getString(R.string.Tgx101FormatHidden) : Integer.toString(position + 1));
        }
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
  }

  private static int indexOf (int[] ids, int id) {
    for (int i = 0; i < ids.length; i++) {
      if (ids[i] == id) return i;
    }
    return -1;
  }

  private List<ListItem> buildItems () {
    int[] order = getOrder();
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    // TGx101: the selection bar's top row — in a message and in the input
    for (int bar : new int[] {Tgx101BarOrder.BAR_MESSAGE, Tgx101BarOrder.BAR_INPUT}) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, bar == Tgx101BarOrder.BAR_MESSAGE ? R.string.Tgx101BarMessageSection : R.string.Tgx101BarInputSection));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      java.util.List<String> barOrder = Tgx101BarOrder.getOrder(bar);
      for (int i = 0; i < barOrder.size(); i++) {
        String key = barOrder.get(i).startsWith("-") ? barOrder.get(i).substring(1) : barOrder.get(i);
        if (i > 0) items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101BarItem, Tgx101BarOrder.iconOf(key), Tgx101BarOrder.nameOf(key)).setLongId(bar * 100 + i));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101BarHint));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101BarFormatSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < order.length; i++) {
      int known = indexOf(IDS, Math.abs(order[i]));
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101MenuAction, ICONS[known], NAMES[known]).setLongId(i));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101FormatMenuHint));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101MenuOrderReset, R.drawable.baseline_undo_24, R.string.Tgx101MessageMenuOrderReset));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    return items;
  }

  private void rebuild () {
    adapter.setItems(buildItems(), false);
  }

  private static void save (ArrayList<Integer> list) {
    int[] result = new int[list.size()];
    for (int i = 0; i < result.length; i++) result[i] = list.get(i);
    Settings.instance().setTgx101FormatMenu(result);
  }

  /** Show / hide / move one item of a selection bar's top row */
  private void showBarOptions (ListItem item) {
    final int bar = (int) item.getLongId() / 100, position = (int) item.getLongId() % 100;
    final java.util.List<String> order = Tgx101BarOrder.getOrder(bar);
    if (position >= order.size()) return;
    final boolean hidden = order.get(position).startsWith("-");
    showOptions(item.getString(),
      new int[] {R.id.btn_tgx101FormatToggle, R.id.btn_moveToTop, R.id.btn_moveUp, R.id.btn_moveDown, R.id.btn_moveToBottom},
      new String[] {Lang.getString(hidden ? R.string.Tgx101FormatShow : R.string.Tgx101FormatHide), Lang.getString(R.string.Tgx101MoveToTop), Lang.getString(R.string.Tgx101MoveUp), Lang.getString(R.string.Tgx101MoveDown), Lang.getString(R.string.Tgx101MoveToBottom)},
      null,
      new int[] {hidden ? R.drawable.baseline_visibility_24 : R.drawable.baseline_eye_off_24, R.drawable.baseline_arrow_upward_24, R.drawable.baseline_arrow_upward_24, R.drawable.baseline_arrow_downward_24, R.drawable.baseline_arrow_downward_24},
      (itemView, optionId) -> {
        java.util.List<String> list = new ArrayList<>(order);
        String entry = list.get(position);
        if (optionId == R.id.btn_tgx101FormatToggle) {
          list.set(position, hidden ? entry.substring(1) : "-" + entry);
        } else {
          int target;
          if (optionId == R.id.btn_moveToTop) target = 0;
          else if (optionId == R.id.btn_moveUp) target = Math.max(0, position - 1);
          else if (optionId == R.id.btn_moveDown) target = Math.min(list.size() - 1, position + 1);
          else if (optionId == R.id.btn_moveToBottom) target = list.size() - 1;
          else return true;
          list.remove(position);
          list.add(target, entry);
        }
        Tgx101BarOrder.setOrder(bar, list);
        rebuild();
        return true;
      });
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_tgx101BarItem) {
      showBarOptions((ListItem) v.getTag());
      return;
    }
    if (id == R.id.btn_tgx101MenuOrderReset) {
      Settings.instance().setTgx101BarOrder(Tgx101BarOrder.BAR_MESSAGE, null);
      Settings.instance().setTgx101BarOrder(Tgx101BarOrder.BAR_INPUT, null);
      Settings.instance().setTgx101FormatMenu(null);
      rebuild();
      return;
    }
    if (id != R.id.btn_tgx101MenuAction) {
      return;
    }
    ListItem item = (ListItem) v.getTag();
    final int position = (int) item.getLongId();
    final int[] order = getOrder();
    final boolean hidden = order[position] < 0;
    showOptions(item.getString(),
      new int[] {R.id.btn_tgx101FormatToggle, R.id.btn_moveToTop, R.id.btn_moveUp, R.id.btn_moveDown, R.id.btn_moveToBottom},
      new String[] {Lang.getString(hidden ? R.string.Tgx101FormatShow : R.string.Tgx101FormatHide), Lang.getString(R.string.Tgx101MoveToTop), Lang.getString(R.string.Tgx101MoveUp), Lang.getString(R.string.Tgx101MoveDown), Lang.getString(R.string.Tgx101MoveToBottom)},
      null,
      new int[] {hidden ? R.drawable.baseline_visibility_24 : R.drawable.baseline_eye_off_24, R.drawable.baseline_arrow_upward_24, R.drawable.baseline_arrow_upward_24, R.drawable.baseline_arrow_downward_24, R.drawable.baseline_arrow_downward_24},
      (itemView, optionId) -> {
        ArrayList<Integer> list = new ArrayList<>();
        for (int x : order) list.add(x);
        if (optionId == R.id.btn_tgx101FormatToggle) {
          list.set(position, -list.get(position));
          save(list);
          rebuild();
          return true;
        }
        int target;
        if (optionId == R.id.btn_moveToTop) {
          target = 0;
        } else if (optionId == R.id.btn_moveUp) {
          target = Math.max(0, position - 1);
        } else if (optionId == R.id.btn_moveDown) {
          target = Math.min(order.length - 1, position + 1);
        } else if (optionId == R.id.btn_moveToBottom) {
          target = order.length - 1;
        } else {
          return true;
        }
        if (target != position) {
          int moved = list.remove(position);
          list.add(target, moved);
          save(list);
          rebuild();
        }
        return true;
      });
  }
}
