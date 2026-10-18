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
import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/** TGx101: order of the actions in the compact message menu. "Delete" is always last. */
public class Tgx101MenuOrderController extends RecyclerViewController<Tgx101MenuOrderController.Args> implements View.OnClickListener, Tgx101DragOrder.Callback {
  // TGx101 (user 2026-10-11): the message menu and the swipe column are set up on screens of their own
  public static final int MODE_MENU = 0, MODE_SWIPE = 1, MODE_DRAWER = 2, MODE_CHAT_MENU = 3, MODE_CAPSULE = 4;

  public static class Args {
    final int mode;

    public Args (int mode) {
      this.mode = mode;
    }
  }

  private static final int CUSTOM_PREVIEW = 0, CUSTOM_TABS = 1;

  private SettingsAdapter adapter;
  private Tgx101OrderPreviewView preview;
  private int editProfile = Settings.TGX101_MENU_TEXT; // user 2026-10-06: which menu is being set up

  private void applyProfile () {
    Settings.instance().setTgx101MenuProfile(editProfile);
  }

  public Tgx101MenuOrderController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private int mode () {
    Args args = getArguments();
    return args != null ? args.mode : MODE_MENU;
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101MenuOrder;
  }

  @Override
  public CharSequence getName () {
    switch (mode()) {
      case MODE_SWIPE: return Lang.getString(R.string.Tgx101SwipeMenuSection);
      case MODE_DRAWER: return Lang.getString(R.string.Tgx101DrawerOrder);
      case MODE_CHAT_MENU: return Lang.getString(R.string.Tgx101ChatMenuOrder);
      case MODE_CAPSULE: return Lang.getString(R.string.Tgx101CapsuleTabs);
      default: return Lang.getString(R.string.Tgx101MessageMenuOrder);
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_tgx101SwipeItem) {
          String entry = (String) item.getData();
          view.getToggler().setRadioEnabled(!entry.startsWith("-"), isUpdate);
        }
      }

      @Override
      protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
        View view;
        if (customViewType == CUSTOM_TABS) {
          view = new ProfileTabs(context);
        } else {
          preview = new Tgx101OrderPreviewView(context);
          view = preview;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new SettingHolder(view);
      }

      @Override
      protected void modifyCustom (SettingHolder holder, int position, ListItem item, int customViewType, View view, boolean isUpdate) {
        if (customViewType == CUSTOM_PREVIEW) {
          preview = (Tgx101OrderPreviewView) view;
          updatePreview();
        } else {
          view.invalidate();
        }
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
    Tgx101DragOrder.attach(recyclerView, adapter, this);
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

  private List<ListItem> buildItems () {
    return mode() != MODE_MENU ? buildSwipeItems() : buildMenuItems();
  }

  private static void addSection (List<ListItem> items, CharSequence title, List<ListItem> rows) {
    items.add(new ListItem(ListItem.TYPE_HEADER, R.id.btn_tgx101DragSection, 0, title));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    if (rows.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101DragEmpty, 0, R.string.Tgx101DragHere));
    } else {
      items.addAll(rows);
    }
  }

  private List<ListItem> buildMenuItems () {
    applyProfile();
    int[] order = Tgx101MessageMenu.getOrder();
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_CUSTOM - CUSTOM_TABS));
    items.add(new ListItem(ListItem.TYPE_CUSTOM - CUSTOM_PREVIEW));
    int[] headers = {R.string.Tgx101MenuSectionMenu, R.string.Tgx101MenuSectionMore, R.string.Tgx101MenuSectionHidden};
    for (int section = SECTION_MENU; section <= SECTION_HIDDEN; section++) {
      List<ListItem> rows = new ArrayList<>();
      for (int i = 0; i < order.length; i++) {
        if (sectionOf(order[i]) != section) continue;
        int known = indexOf(Tgx101MessageMenu.ORDERABLE_IDS, order[i]);
        rows.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101MenuAction, Tgx101MessageMenu.ORDERABLE_ICONS[known], Tgx101MessageMenu.ORDERABLE_NAMES[known]).setLongId(i).setData(order[i]));
      }
      CharSequence title = Lang.getString(headers[section]);
      if (section == SECTION_MENU) {
        // «In the menu · 9 of 9» (the long «In the menu: 5 to 9 items · 9 of 9» said it twice)
        title = Lang.getString(R.string.Tgx101MenuSectionMenuShort) + " · " + Lang.getString(R.string.Tgx101MenuCountOf, menuCount(order), Tgx101MessageMenu.MAX_MENU_ITEMS);
      }
      addSection(items, title, rows);
      if (section == SECTION_MENU) {
        // «Select» is always the last item, under a line
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101MenuSelectFixed, R.drawable.baseline_playlist_add_check_24, R.string.Select));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101DragHint));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101MessageMenuOrderHint));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101MenuOrderReset, R.drawable.baseline_undo_24, R.string.Tgx101MessageMenuOrderReset));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    return items;
  }

  // TGx101 (user 2026-10-06; 2026-10-11 own screen): the swipe actions column, top to bottom, and the ones switched off
  private List<ListItem> buildSwipeItems () {
    List<String> swipe = Tgx101BarOrder.getOrder(bar());
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    if (mode() == MODE_SWIPE) items.add(new ListItem(ListItem.TYPE_CUSTOM - CUSTOM_PREVIEW));
    for (int section = 0; section < 2; section++) {
      List<ListItem> rows = new ArrayList<>();
      for (String entry : swipe) {
        boolean hidden = entry.startsWith("-");
        if (hidden != (section == 1)) continue;
        String key = hidden ? entry.substring(1) : entry;
        rows.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SwipeItem, iconOf(key), nameOf(key)).setData(entry));
      }
      int title = section == 1 ? R.string.Tgx101SwipeSectionOff : mode() == MODE_SWIPE ? R.string.Tgx101SwipeSectionOn : R.string.Tgx101BarSectionOn;
      CharSequence titleText = Lang.getString(title);
      if (mode() == MODE_CAPSULE && section == 0) {
        // «After Chats · 3 of 3»
        titleText = Lang.getString(R.string.Tgx101CapsuleTabsOn) + " · " + Lang.getString(R.string.Tgx101MenuCountOf, rows.size(), Tgx101BarOrder.CAPSULE_MAX_TABS);
      }
      addSection(items, titleText, rows);
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, mode() == MODE_SWIPE ? R.string.Tgx101SwipeOrderHint : mode() == MODE_DRAWER ? R.string.Tgx101DrawerOrderHint : mode() == MODE_CAPSULE ? R.string.Tgx101CapsuleTabsHint : R.string.Tgx101ChatMenuOrderHint));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101MenuOrderReset, R.drawable.baseline_undo_24, R.string.Tgx101MessageMenuOrderReset));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    return items;
  }

  private void rebuild () {
    adapter.setItems(buildItems(), false);
    updatePreview();
    if (mode() == MODE_DRAWER && context().getDrawer() != null) context().getDrawer().tgx101RebuildMenu(); // the drawer follows at once
  }

  private int bar () {
    switch (mode()) {
      case MODE_DRAWER: return Tgx101BarOrder.BAR_DRAWER;
      case MODE_CHAT_MENU: return Tgx101BarOrder.BAR_CHAT_MENU;
      case MODE_CAPSULE: return Tgx101BarOrder.BAR_CAPSULE;
      default: return Tgx101BarOrder.BAR_SWIPE;
    }
  }

  private int nameOf (String key) {
    switch (mode()) {
      case MODE_DRAWER: return Tgx101BarOrder.drawerNameOf(key);
      case MODE_CHAT_MENU: return Tgx101BarOrder.chatNameOf(key);
      case MODE_CAPSULE: return Tgx101BarOrder.tabNameOf(key);
      default: return Tgx101BarOrder.swipeNameOf(key);
    }
  }

  private int iconOf (String key) {
    switch (mode()) {
      case MODE_DRAWER: return Tgx101BarOrder.drawerIconOf(key);
      case MODE_CHAT_MENU: return Tgx101BarOrder.chatIconOf(key);
      case MODE_CAPSULE: return Tgx101BarOrder.tabIconOf(key);
      default: return Tgx101BarOrder.swipeIconOf(key);
    }
  }

  /** The entry that can't be switched off: «Reply» of the swipe column, «Settings» of the drawer */
  private @androidx.annotation.Nullable String fixedKey () {
    switch (mode()) {
      case MODE_SWIPE: return Tgx101BarOrder.SWIPE_REPLY;
      case MODE_DRAWER: return Tgx101BarOrder.DRAWER_SETTINGS;
      case MODE_CAPSULE: return Tgx101BarOrder.TAB_SETTINGS; // the only way to MagiX — and back to these switches
      default: return null;
    }
  }

  private int fixedHint () {
    return mode() == MODE_DRAWER || mode() == MODE_CAPSULE ? R.string.Tgx101DrawerSettingsStays : R.string.Tgx101SwipeReplyStays;
  }

  /** The bottom menu keeps 1–3 tabs after «Chats»; false (and a toast) when the list would break that */
  private boolean checkCapsuleCount (List<String> list) {
    if (mode() != MODE_CAPSULE) return true;
    int on = 0;
    for (String entry : list) if (!entry.startsWith("-")) on++;
    if (on > Tgx101BarOrder.CAPSULE_MAX_TABS) {
      UI.showToast(Lang.getString(R.string.Tgx101CapsuleTabsMax, Tgx101BarOrder.CAPSULE_MAX_TABS), android.widget.Toast.LENGTH_SHORT);
      return false;
    }
    if (on == 0) {
      UI.showToast(R.string.Tgx101CapsuleTabsMin, android.widget.Toast.LENGTH_SHORT);
      return false;
    }
    return true;
  }

  private static int[] toArray (List<Integer> list) {
    int[] result = new int[list.size()];
    for (int i = 0; i < result.length; i++) result[i] = list.get(i);
    return result;
  }

  // Dragging

  @Override
  public boolean canDrag (ListItem item) {
    return item.getId() == (mode() != MODE_MENU ? R.id.btn_tgx101SwipeItem : R.id.btn_tgx101MenuAction);
  }

  private static String keyOf (ListItem item) {
    String entry = (String) item.getData();
    return entry.startsWith("-") ? entry.substring(1) : entry;
  }

  /** The preview follows the list as it is now — also in the middle of a drag */
  private void updatePreview () {
    if (preview == null || adapter == null) {
      return;
    }
    List<List<ListItem>> sections = Tgx101DragOrder.sections(adapter.getItems(), this);
    List<Tgx101OrderPreviewView.Entry> entries = new ArrayList<>();
    if (mode() == MODE_DRAWER || mode() == MODE_CHAT_MENU) return;
    if (mode() == MODE_SWIPE) {
      if (!sections.isEmpty()) {
        for (ListItem row : sections.get(0)) {
          String key = keyOf(row);
          boolean reply = Tgx101BarOrder.SWIPE_REPLY.equals(key);
          boolean red = Tgx101BarOrder.SWIPE_DELETE.equals(key) || Tgx101BarOrder.SWIPE_DELETE_ALL.equals(key);
          entries.add(new Tgx101OrderPreviewView.Entry(Tgx101BarOrder.swipeIconOf(key), null, red, reply));
        }
      }
      preview.setColumn(entries, false);
    } else {
      if (!sections.isEmpty()) {
        for (ListItem row : sections.get(0)) {
          int id = (Integer) row.getData();
          int known = indexOf(Tgx101MessageMenu.ORDERABLE_IDS, id);
          entries.add(new Tgx101OrderPreviewView.Entry(Tgx101MessageMenu.ORDERABLE_ICONS[known], Lang.getString(Tgx101MessageMenu.ORDERABLE_NAMES[known]), id == R.id.btn_messageDelete, false));
        }
      }
      preview.setMenu(entries, new Tgx101OrderPreviewView.Entry(R.drawable.baseline_playlist_add_check_24, Lang.getString(R.string.Select), false, false));
    }
  }

  @Override
  public void onOrderChanged (boolean finished) {
    if (!finished) {
      updatePreview();
      return;
    }
    List<List<ListItem>> sections = Tgx101DragOrder.sections(adapter.getItems(), this);
    if (mode() != MODE_MENU) {
      List<String> list = new ArrayList<>();
      for (int section = 0; section < sections.size(); section++) {
        for (ListItem row : sections.get(section)) {
          String key = keyOf(row);
          if (section > 0 && key.equals(fixedKey())) {
            UI.showToast(fixedHint(), android.widget.Toast.LENGTH_SHORT);
            rebuild();
            return;
          }
          list.add(section > 0 ? "-" + key : key);
        }
      }
      if (!checkCapsuleCount(list)) {
        rebuild();
        return;
      }
      Tgx101BarOrder.setOrder(bar(), list);
      org.thunderdog.challegram.Tgx101Diag.mark("order: bar " + bar() + " saved, " + list.size() + " entries");
      rebuild();
      return;
    }
    applyProfile();
    List<Integer> menu = new ArrayList<>(), more = new ArrayList<>(), hidden = new ArrayList<>();
    List<List<Integer>> lists = new ArrayList<>();
    lists.add(menu);
    lists.add(more);
    lists.add(hidden);
    for (int section = 0; section < sections.size() && section < lists.size(); section++) {
      for (ListItem row : sections.get(section)) lists.get(section).add((Integer) row.getData());
    }
    int count = menu.size() + 1; // «Select»
    if (count > Tgx101MessageMenu.MAX_MENU_ITEMS) {
      UI.showToast(Lang.getString(R.string.Tgx101MenuTooMany, Tgx101MessageMenu.MAX_MENU_ITEMS), android.widget.Toast.LENGTH_SHORT);
      rebuild();
      return;
    }
    if (count < Tgx101MessageMenu.MIN_MENU_ITEMS) {
      UI.showToast(Lang.getString(R.string.Tgx101MenuTooFew, Tgx101MessageMenu.MIN_MENU_ITEMS), android.widget.Toast.LENGTH_SHORT);
      rebuild();
      return;
    }
    if (hidden.contains(R.id.btn_tgx101SelectInPlace)) {
      UI.showToast(R.string.Tgx101SelectCantHide, android.widget.Toast.LENGTH_SHORT);
      rebuild();
      return;
    }
    if (!menu.contains(R.id.btn_messageMore)) {
      UI.showToast(R.string.Tgx101MoreStaysInMenu, android.widget.Toast.LENGTH_SHORT);
      rebuild();
      return;
    }
    List<Integer> order = new ArrayList<>(menu);
    order.addAll(more);
    order.addAll(hidden);
    Settings.instance().setTgx101MessageMenuOrder(toArray(order));
    Settings.instance().setTgx101MessageMenuMore(toArray(more));
    Settings.instance().setTgx101MessageMenuHidden(toArray(hidden));
    org.thunderdog.challegram.Tgx101Diag.mark("order: menu " + editProfile + " saved, " + menu.size() + " in the menu, " + more.size() + " in More, " + hidden.size() + " hidden");
    rebuild();
  }

  /** Text / Media / Voice: which of the three menus is set up */
  private class ProfileTabs extends View {
    private final int[] profiles = {Settings.TGX101_MENU_TEXT, Settings.TGX101_MENU_MEDIA, Settings.TGX101_MENU_VOICE};
    private final int[] names = {R.string.Tgx101MenuTabText, R.string.Tgx101MenuTabMedia, R.string.Tgx101MenuTabVoice}; // short: the long ones overlapped (0.1.635)
    private final RectF rect = new RectF();

    ProfileTabs (Context context) {
      super(context);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Screen.dp(52f));
    }

    @Override
    protected void onDraw (Canvas c) {
      int width = getMeasuredWidth();
      c.drawColor(Theme.fillingColor());
      float left = Screen.dp(12f), top = Screen.dp(8f), right = width - Screen.dp(12f), bottom = Screen.dp(44f);
      rect.set(left, top, right, bottom);
      c.drawRoundRect(rect, Screen.dp(10f), Screen.dp(10f), Paints.fillingPaint(Theme.backgroundColor()));
      float tab = (right - left) / profiles.length;
      for (int i = 0; i < profiles.length; i++) {
        boolean selected = profiles[i] == editProfile;
        if (selected) {
          rect.set(left + tab * i + Screen.dp(3f), top + Screen.dp(3f), left + tab * (i + 1) - Screen.dp(3f), bottom - Screen.dp(3f));
          c.drawRoundRect(rect, Screen.dp(8f), Screen.dp(8f), Paints.fillingPaint(Theme.getColor(ColorId.iconActive)));
        }
        TextPaint paint = Paints.getMediumTextPaint(13f, selected ? 0xffffffff : Theme.getColor(ColorId.textLight), false);
        CharSequence name = android.text.TextUtils.ellipsize(Lang.getString(names[i]), paint, tab - Screen.dp(12f), android.text.TextUtils.TruncateAt.END);
        c.drawText(name, 0, name.length(), left + tab * i + (tab - paint.measureText(name, 0, name.length())) / 2, (top + bottom) / 2 + Screen.dp(4.5f), paint);
      }
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (e.getAction() == MotionEvent.ACTION_DOWN) {
        return true;
      }
      if (e.getAction() == MotionEvent.ACTION_UP) {
        float left = Screen.dp(12f), tab = (getMeasuredWidth() - Screen.dp(24f)) / (float) profiles.length;
        int index = (int) ((e.getX() - left) / tab);
        if (index >= 0 && index < profiles.length && profiles[index] != editProfile) {
          editProfile = profiles[index];
          invalidate();
          rebuild();
        }
        return true;
      }
      return super.onTouchEvent(e);
    }
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    applyProfile();
    if (id == R.id.btn_tgx101MenuOrderReset) {
      if (mode() != MODE_MENU) {
        Tgx101BarOrder.setOrder(bar(), null);
      } else {
        Settings.instance().setTgx101MessageMenuOrder(null);
        Settings.instance().setTgx101MessageMenuHidden(null);
        Settings.instance().setTgx101MessageMenuMore(null);
      }
      rebuild();
      return;
    }
    if (id == R.id.btn_tgx101SwipeItem) {
      // the switch: shown in the column or not («Reply» is the plain swipe and stays)
      ListItem item = (ListItem) v.getTag();
      String entry = (String) item.getData();
      if (entry.equals(fixedKey())) {
        UI.showToast(fixedHint(), android.widget.Toast.LENGTH_SHORT);
        adapter.updateValuedSettingById(R.id.btn_tgx101SwipeItem);
        return;
      }
      List<String> list = new ArrayList<>(Tgx101BarOrder.getOrder(bar()));
      int index = list.indexOf(entry);
      if (index != -1) {
        list.set(index, entry.startsWith("-") ? entry.substring(1) : "-" + entry);
        if (!checkCapsuleCount(list)) {
          adapter.updateValuedSettingById(R.id.btn_tgx101SwipeItem);
          return;
        }
        Tgx101BarOrder.setOrder(bar(), list);
      }
      rebuild();
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
}
