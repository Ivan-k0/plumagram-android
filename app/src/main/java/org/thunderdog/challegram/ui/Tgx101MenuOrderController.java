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

/** TGx101: order of the actions in the compact message menu. "Delete" is always last. */
public class Tgx101MenuOrderController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;

  public Tgx101MenuOrderController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101MenuOrder;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101MessageMenuOrder);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_tgx101MenuAction) {
          int position = (int) item.getLongId();
          int[] order = Tgx101MessageMenu.getOrder();
          boolean hidden = position < order.length && Tgx101MessageMenu.isHidden(order[position]);
          boolean inMore = position < order.length && Tgx101MessageMenu.isInMore(order[position]);
          view.setData(hidden ? Lang.getString(R.string.Tgx101FormatHidden) : inMore ? Lang.getString(R.string.Tgx101MenuInMore) : Integer.toString(position + 1));
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
    int[] order = Tgx101MessageMenu.getOrder();
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < order.length; i++) {
      int known = indexOf(Tgx101MessageMenu.ORDERABLE_IDS, order[i]);
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101MenuAction, Tgx101MessageMenu.ORDERABLE_ICONS[known], Tgx101MessageMenu.ORDERABLE_NAMES[known]).setLongId(i));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101MessageMenuOrderHint));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101MenuOrderReset, R.drawable.baseline_undo_24, R.string.Tgx101MessageMenuOrderReset));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    return items;
  }

  private void rebuild () {
    adapter.setItems(buildItems(), false);
  }

  private static int[] toArray (List<Integer> list) {
    int[] result = new int[list.size()];
    for (int i = 0; i < result.length; i++) result[i] = list.get(i);
    return result;
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_tgx101MenuOrderReset) {
      Settings.instance().setTgx101MessageMenuOrder(null);
      Settings.instance().setTgx101MessageMenuHidden(null);
      Settings.instance().setTgx101MessageMenuMore(null);
      rebuild();
      return;
    }
    if (id != R.id.btn_tgx101MenuAction) {
      return;
    }
    ListItem item = (ListItem) v.getTag();
    final int position = (int) item.getLongId();
    final int[] order = Tgx101MessageMenu.getOrder();
    if (order[position] == R.id.btn_messageSelect) {
      // TGx101 (user 2026-10-05): «Select» always stays in the menu — only its place can change (drag)
      org.thunderdog.challegram.tool.UI.showToast(R.string.Tgx101MenuSelectAlways, android.widget.Toast.LENGTH_SHORT);
      return;
    }
    final boolean hidden = Tgx101MessageMenu.isHidden(order[position]);
    final boolean inMore = Tgx101MessageMenu.isInMore(order[position]);
    String mark = "  ✓";
    showOptions(item.getString(),
      new int[] {R.id.btn_tgx101MenuShow, R.id.btn_tgx101MenuToMore, R.id.btn_tgx101MenuHide, R.id.btn_moveToTop, R.id.btn_moveUp, R.id.btn_moveDown, R.id.btn_moveToBottom},
      new String[] {
        Lang.getString(R.string.Tgx101MenuShowInMenu) + (!hidden && !inMore ? mark : ""),
        Lang.getString(R.string.Tgx101MenuMoveToMore) + (inMore && !hidden ? mark : ""),
        Lang.getString(R.string.Tgx101FormatHide) + (hidden ? mark : ""),
        Lang.getString(R.string.Tgx101MoveToTop), Lang.getString(R.string.Tgx101MoveUp), Lang.getString(R.string.Tgx101MoveDown), Lang.getString(R.string.Tgx101MoveToBottom)},
      null,
      new int[] {R.drawable.baseline_visibility_24, R.drawable.baseline_more_horiz_24, R.drawable.baseline_eye_off_24, R.drawable.baseline_arrow_upward_24, R.drawable.baseline_arrow_upward_24, R.drawable.baseline_arrow_downward_24, R.drawable.baseline_arrow_downward_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_tgx101MenuShow || optionId == R.id.btn_tgx101MenuToMore || optionId == R.id.btn_tgx101MenuHide) {
          // where this action goes: the menu itself, under «More…», or nowhere
          Integer key = order[position];
          ArrayList<Integer> hiddenIds = new ArrayList<>(), moreIds = new ArrayList<>();
          for (int x : Settings.instance().getTgx101MessageMenuHidden()) if (x != key) hiddenIds.add(x);
          for (int x : Settings.instance().getTgx101MessageMenuMore()) if (x != key) moreIds.add(x);
          if (optionId == R.id.btn_tgx101MenuHide) hiddenIds.add(key);
          if (optionId == R.id.btn_tgx101MenuToMore) moreIds.add(key);
          Settings.instance().setTgx101MessageMenuHidden(toArray(hiddenIds));
          Settings.instance().setTgx101MessageMenuMore(toArray(moreIds));
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
          ArrayList<Integer> list = new ArrayList<>();
          for (int x : order) list.add(x);
          int moved = list.remove(position);
          list.add(target, moved);
          int[] result = new int[list.size()];
          for (int i = 0; i < result.length; i++) result[i] = list.get(i);
          Settings.instance().setTgx101MessageMenuOrder(result);
          rebuild();
        }
        return true;
      });
  }
}
