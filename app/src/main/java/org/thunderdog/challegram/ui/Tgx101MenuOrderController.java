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
import org.thunderdog.challegram.tool.UI;
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
          view.setData(hidden || inMore ? "" : Integer.toString(numberInSection(order, position) + 1));
        } else if (item.getId() == R.id.btn_tgx101BarItem) {
          int position = (int) item.getLongId();
          List<String> swipe = Tgx101BarOrder.getOrder(Tgx101BarOrder.BAR_SWIPE);
          view.setData(position < swipe.size() && swipe.get(position).startsWith("-") ? Lang.getString(R.string.Tgx101FormatHidden) : Integer.toString(position + 1));
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

  // TGx101 (user 2026-10-06): the list is split into «In the menu», «In More…» and «Hidden»; each keeps its own order
  private static final int SECTION_MENU = 0, SECTION_MORE = 1, SECTION_HIDDEN = 2;

  private static int sectionOf (int id) {
    if (Tgx101MessageMenu.isHidden(id)) return SECTION_HIDDEN;
    if (Tgx101MessageMenu.isInMore(id)) return SECTION_MORE;
    return SECTION_MENU;
  }

  /** Items in the menu itself, «Select» included */
  private static int menuCount (int[] order) {
    int n = 1;
    for (int id : order) if (sectionOf(id) == SECTION_MENU) n++;
    return n;
  }

  /** user 2026-10-06: 5–9 items stay in the menu */
  private boolean checkMenuCount (int[] order, int position, boolean toMenu) {
    boolean inMenu = sectionOf(order[position]) == SECTION_MENU;
    if (toMenu == inMenu) return true;
    int count = menuCount(order);
    if (toMenu && count >= Tgx101MessageMenu.MAX_MENU_ITEMS) {
      UI.showToast(Lang.getString(R.string.Tgx101MenuTooMany, Tgx101MessageMenu.MAX_MENU_ITEMS), android.widget.Toast.LENGTH_SHORT);
      return false;
    }
    if (!toMenu && count <= Tgx101MessageMenu.MIN_MENU_ITEMS) {
      UI.showToast(Lang.getString(R.string.Tgx101MenuTooFew, Tgx101MessageMenu.MIN_MENU_ITEMS), android.widget.Toast.LENGTH_SHORT);
      return false;
    }
    return true;
  }

  private static int numberInSection (int[] order, int position) {
    int section = sectionOf(order[position]), n = 0;
    for (int i = 0; i < position; i++) if (sectionOf(order[i]) == section) n++;
    return n;
  }

  private List<ListItem> buildItems () {
    int[] order = Tgx101MessageMenu.getOrder();
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    int[] headers = {R.string.Tgx101MenuSectionMenu, R.string.Tgx101MenuSectionMore, R.string.Tgx101MenuSectionHidden};
    for (int section = SECTION_MENU; section <= SECTION_HIDDEN; section++) {
      boolean any = false;
      for (int i = 0; i < order.length; i++) {
        if (sectionOf(order[i]) != section) continue;
        if (!any) {
          items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, headers[section]));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          any = true;
        } else {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        int known = indexOf(Tgx101MessageMenu.ORDERABLE_IDS, order[i]);
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101MenuAction, Tgx101MessageMenu.ORDERABLE_ICONS[known], Tgx101MessageMenu.ORDERABLE_NAMES[known]).setLongId(i));
      }
      if (section == SECTION_MENU) {
        // «Select» is always the last item, under a line
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101MenuSelectFixed, R.drawable.baseline_playlist_add_check_24, R.string.Select));
      }
      if (any || section == SECTION_MENU) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
    }
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101MessageMenuOrderHint));
    // TGx101 (user 2026-10-06): the swipe actions column, top to bottom
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SwipeMenuSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    List<String> swipe = Tgx101BarOrder.getOrder(Tgx101BarOrder.BAR_SWIPE);
    for (int i = 0; i < swipe.size(); i++) {
      String key = swipe.get(i).startsWith("-") ? swipe.get(i).substring(1) : swipe.get(i);
      if (i > 0) items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101BarItem, Tgx101BarOrder.swipeIconOf(key), Tgx101BarOrder.swipeNameOf(key)).setLongId(i));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101SwipeMenuHint));
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
      Tgx101BarOrder.setOrder(Tgx101BarOrder.BAR_SWIPE, null);
      rebuild();
      return;
    }
    if (id == R.id.btn_tgx101BarItem) {
      showSwipeOptions((ListItem) v.getTag());
      return;
    }
    if (id == R.id.btn_tgx101MenuSelectFixed) {
      return; // «Select» can't be moved or hidden
    }
    if (id != R.id.btn_tgx101MenuAction) {
      return;
    }
    ListItem item = (ListItem) v.getTag();
    final int position = (int) item.getLongId();
    final int[] order = Tgx101MessageMenu.getOrder();
    final boolean hidden = Tgx101MessageMenu.isHidden(order[position]);
    final boolean inMore = Tgx101MessageMenu.isInMore(order[position]);
    String mark = "  ✓";
    if (order[position] == R.id.btn_tgx101SelectInPlace) {
      // «Select» text can't be hidden: in the menu or under «More…», and moved
      showOptions(item.getString(),
        new int[] {R.id.btn_tgx101MenuShow, R.id.btn_tgx101MenuToMore, R.id.btn_moveToTop, R.id.btn_moveUp, R.id.btn_moveDown, R.id.btn_moveToBottom},
        new String[] {Lang.getString(R.string.Tgx101MenuShowInMenu) + (!inMore ? mark : ""), Lang.getString(R.string.Tgx101MenuMoveToMore) + (inMore ? mark : ""),
          Lang.getString(R.string.Tgx101MoveToTop), Lang.getString(R.string.Tgx101MoveUp), Lang.getString(R.string.Tgx101MoveDown), Lang.getString(R.string.Tgx101MoveToBottom)},
        null,
        new int[] {R.drawable.baseline_visibility_24, R.drawable.baseline_more_horiz_24, R.drawable.baseline_arrow_upward_24, R.drawable.baseline_arrow_upward_24, R.drawable.baseline_arrow_downward_24, R.drawable.baseline_arrow_downward_24},
        (itemView, optionId) -> {
          if (optionId == R.id.btn_tgx101MenuShow || optionId == R.id.btn_tgx101MenuToMore) {
            if (!checkMenuCount(order, position, optionId == R.id.btn_tgx101MenuShow)) return true;
            int key = order[position];
            ArrayList<Integer> moreIds = new ArrayList<>();
            for (int x : Tgx101MessageMenu.moreIds()) if (x != key) moreIds.add(x);
            if (optionId == R.id.btn_tgx101MenuToMore) moreIds.add(key);
            Settings.instance().setTgx101MessageMenuMore(toArray(moreIds));
            rebuild();
            return true;
          }
          moveTo(order, position, optionId);
          return true;
        });
      return;
    }
    if (order[position] == R.id.btn_messageMore) {
      // «More…» itself can only be moved (it shows up only when something is moved into it)
      showOptions(item.getString(),
        new int[] {R.id.btn_moveToTop, R.id.btn_moveUp, R.id.btn_moveDown, R.id.btn_moveToBottom},
        new String[] {Lang.getString(R.string.Tgx101MoveToTop), Lang.getString(R.string.Tgx101MoveUp), Lang.getString(R.string.Tgx101MoveDown), Lang.getString(R.string.Tgx101MoveToBottom)},
        null,
        new int[] {R.drawable.baseline_arrow_upward_24, R.drawable.baseline_arrow_upward_24, R.drawable.baseline_arrow_downward_24, R.drawable.baseline_arrow_downward_24},
        (itemView, optionId) -> {
          moveTo(order, position, optionId);
          return true;
        });
      return;
    }
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
          if (!checkMenuCount(order, position, optionId == R.id.btn_tgx101MenuShow)) return true;
          // where this action goes: the menu itself, under «More…», or nowhere
          Integer key = order[position];
          ArrayList<Integer> hiddenIds = new ArrayList<>(), moreIds = new ArrayList<>();
          for (int x : Settings.instance().getTgx101MessageMenuHidden()) if (x != key) hiddenIds.add(x);
          for (int x : Tgx101MessageMenu.moreIds()) if (x != key) moreIds.add(x);
          if (optionId == R.id.btn_tgx101MenuHide) hiddenIds.add(key);
          if (optionId == R.id.btn_tgx101MenuToMore) moreIds.add(key);
          Settings.instance().setTgx101MessageMenuHidden(toArray(hiddenIds));
          Settings.instance().setTgx101MessageMenuMore(toArray(moreIds));
          rebuild();
          return true;
        }
        moveTo(order, position, optionId);
        return true;
      });
  }

  private void moveTo (int[] order, int position, int optionId) {
    // the neighbours are the items of the same section («In the menu», «In More…», «Hidden»)
    int section = sectionOf(order[position]);
    ArrayList<Integer> same = new ArrayList<>();
    for (int i = 0; i < order.length; i++) if (sectionOf(order[i]) == section) same.add(i);
    int at = same.indexOf(position);
    int targetAt;
    if (optionId == R.id.btn_moveToTop) {
      targetAt = 0;
    } else if (optionId == R.id.btn_moveUp) {
      targetAt = Math.max(0, at - 1);
    } else if (optionId == R.id.btn_moveDown) {
      targetAt = Math.min(same.size() - 1, at + 1);
    } else if (optionId == R.id.btn_moveToBottom) {
      targetAt = same.size() - 1;
    } else {
      return;
    }
    if (targetAt == at) return;
    int target = same.get(targetAt);
    ArrayList<Integer> list = new ArrayList<>();
    for (int x : order) list.add(x);
    int moved = list.remove(position);
    list.add(target, moved);
    Settings.instance().setTgx101MessageMenuOrder(toArray(list));
    rebuild();
  }


  /** Show / hide / move one action of the swipe column; «Reply» (the plain swipe) can only be moved */
  private void showSwipeOptions (ListItem item) {
    final int position = (int) item.getLongId();
    final List<String> order = Tgx101BarOrder.getOrder(Tgx101BarOrder.BAR_SWIPE);
    if (position >= order.size()) return;
    final String entry = order.get(position);
    final boolean hidden = entry.startsWith("-");
    final boolean canHide = !Tgx101BarOrder.SWIPE_REPLY.equals(entry);
    ArrayList<Integer> ids = new ArrayList<>(), icons = new ArrayList<>();
    ArrayList<String> names = new ArrayList<>();
    if (canHide) {
      ids.add(R.id.btn_tgx101FormatToggle);
      names.add(Lang.getString(hidden ? R.string.Tgx101FormatShow : R.string.Tgx101FormatHide));
      icons.add(hidden ? R.drawable.baseline_visibility_24 : R.drawable.baseline_eye_off_24);
    }
    int[] moveIds = {R.id.btn_moveToTop, R.id.btn_moveUp, R.id.btn_moveDown, R.id.btn_moveToBottom};
    int[] moveNames = {R.string.Tgx101MoveToTop, R.string.Tgx101MoveUp, R.string.Tgx101MoveDown, R.string.Tgx101MoveToBottom};
    int[] moveIcons = {R.drawable.baseline_arrow_upward_24, R.drawable.baseline_arrow_upward_24, R.drawable.baseline_arrow_downward_24, R.drawable.baseline_arrow_downward_24};
    for (int i = 0; i < moveIds.length; i++) {
      ids.add(moveIds[i]);
      names.add(Lang.getString(moveNames[i]));
      icons.add(moveIcons[i]);
    }
    showOptions(item.getString(), toArray(ids), names.toArray(new String[0]), null, toArray(icons), (itemView, optionId) -> {
      List<String> list = new ArrayList<>(order);
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
      Tgx101BarOrder.setOrder(Tgx101BarOrder.BAR_SWIPE, list);
      rebuild();
      return true;
    });
  }
}
