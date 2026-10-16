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
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101 (user 2026-10-11, approved mockup «Жесты-и-порядок-меню»): MagiX → «Message gestures» — what a double tap,
 * a swipe to the left and a long press do, with the swipe column drawn and explained.
 */
public class Tgx101GesturesController extends RecyclerViewController<Void> implements View.OnClickListener {
  private static final int[] DOUBLE_TAP_IDS = {
    R.id.btn_tgx101DoubleTapOff, R.id.btn_tgx101DoubleTapReaction, R.id.btn_tgx101DoubleTapTranslate,
    R.id.btn_tgx101DoubleTapSave, R.id.btn_tgx101DoubleTapDelete
  };
  private static final int[] DOUBLE_TAP_ACTIONS = {
    Settings.DOUBLE_TAP_OFF, Settings.DOUBLE_TAP_REACTION, Settings.DOUBLE_TAP_TRANSLATE,
    Settings.DOUBLE_TAP_SAVE, Settings.DOUBLE_TAP_DELETE_ALL
  };
  private static final int[] DOUBLE_TAP_NAMES = {
    R.string.Tgx101DoubleTapOff, R.string.Tgx101DoubleTapReaction, R.string.Tgx101DoubleTapTranslate,
    R.string.Tgx101DoubleTapSave, R.string.Tgx101DoubleTapDelete
  };

  private SettingsAdapter adapter;
  private Tgx101OrderPreviewView preview;

  public Tgx101GesturesController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101Gestures;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101Gestures);
  }

  /** «Reaction №1» etc. — for the MagiX row */
  public static String doubleTapName (int action) {
    for (int i = 0; i < DOUBLE_TAP_ACTIONS.length; i++) {
      if (DOUBLE_TAP_ACTIONS[i] == action) return Lang.getString(DOUBLE_TAP_NAMES[i]);
    }
    return Lang.getString(R.string.Tgx101DoubleTapOff);
  }

  /** «👍» — reaction №1 of the quick reactions (Settings → Stickers and Emoji → Quick Reaction) */
  private String firstQuickReactionName () {
    String[] quick = Settings.instance().getQuickReactions(tdlib);
    if (quick == null || quick.length == 0) {
      return Lang.getString(R.string.QuickReactionDisabled);
    }
    org.drinkless.tdlib.TdApi.ReactionType type = org.thunderdog.challegram.data.TD.toReactionType(quick[0]);
    if (type instanceof org.drinkless.tdlib.TdApi.ReactionTypeEmoji) {
      return ((org.drinkless.tdlib.TdApi.ReactionTypeEmoji) type).emoji;
    }
    org.thunderdog.challegram.data.TGReaction reaction = tdlib.getReaction(type);
    return reaction != null ? reaction.getTitle() : quick[0];
  }

  private static int shownSwipeActions () {
    int count = 0;
    for (String entry : Tgx101BarOrder.getOrder(Tgx101BarOrder.BAR_SWIPE)) {
      if (!entry.startsWith("-") && !Tgx101BarOrder.SWIPE_REPLY.equals(entry)) count++;
    }
    return count;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        if (id == R.id.btn_tgx101SwipeToStorage) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101SwipeToStorage(), isUpdate);
        } else if (id == R.id.btn_tgx101SwipeReactions) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101SwipeReactions(), isUpdate);
        } else if (id == R.id.btn_tgx101SwipeActions) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101SwipeActions(), isUpdate);
        } else if (id == R.id.btn_tgx101DoubleTapWhich) {
          view.setData(firstQuickReactionName());
        } else if (id == R.id.btn_tgx101SwipeOrder) {
          view.setData(Settings.instance().tgx101SwipeActions() ? Lang.getString(R.string.Tgx101SwipeColumnCount, shownSwipeActions()) : Lang.getString(R.string.Tgx101SwipeColumnOff));
        }
      }

      @Override
      protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
        preview = new Tgx101OrderPreviewView(context);
        preview.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new SettingHolder(preview);
      }

      @Override
      protected void modifyCustom (SettingHolder holder, int position, ListItem item, int customViewType, View view, boolean isUpdate) {
        preview = (Tgx101OrderPreviewView) view;
        updatePreview();
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
  }

  private static CharSequence lines (int... strings) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < strings.length; i += 2) {
      if (b.length() > 0) b.append('\n');
      b.append(Lang.getString(strings[i])).append(" — ").append(Lang.getString(strings[i + 1]).toLowerCase());
    }
    return b;
  }

  private List<ListItem> buildItems () {
    List<ListItem> items = new ArrayList<>();
    final int doubleTap = Settings.instance().tgx101DoubleTapAction();

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101DoubleTapSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < DOUBLE_TAP_IDS.length; i++) {
      if (i > 0) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, DOUBLE_TAP_IDS[i], 0, DOUBLE_TAP_NAMES[i], R.id.btn_tgx101DoubleTapGroup, DOUBLE_TAP_ACTIONS[i] == doubleTap));
    }
    // user 2026-10-11 «что за реакция номер один? как выбрать?»: which one it is, and the way to change it, right here
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101DoubleTapWhich, 0, R.string.Tgx101DoubleTapWhich));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SwipeReactions, 0, R.string.Tgx101SwipeReactionsToggle));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, lines(
      R.string.Tgx101DoubleTapReaction, R.string.Tgx101DoubleTapReactionHint,
      R.string.Tgx101DoubleTapTranslate, R.string.Tgx101DoubleTapTranslateHint,
      R.string.Tgx101DoubleTapSave, R.string.Tgx101DoubleTapSaveHint,
      R.string.Tgx101DoubleTapDelete, R.string.Tgx101DoubleTapDeleteHint
    ) + "\n\n" + Lang.getString(R.string.Tgx101DoubleTapHint), false));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SwipeLeftSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_CUSTOM));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101SwipeHowTo));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SwipeActions, R.drawable.baseline_reply_24, R.string.Tgx101SwipeColumn));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101SwipeOrder, R.drawable.baseline_drag_handle_24, R.string.Tgx101SwipeOrder));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SwipeToStorageSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SwipeToStorage, R.drawable.baseline_bookmark_24, R.string.Tgx101SwipeToStorage));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101SwipeToStorageHint));

    final boolean menu = Settings.instance().tgx101LongPressMenu();
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101LongPressSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101LongPressSelect, 0, R.string.Tgx101LongPressSelect, R.id.btn_tgx101LongPressGroup, !menu));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101LongPressMenuOpen, 0, R.string.Tgx101LongPressOpenMenu, R.id.btn_tgx101LongPressGroup, menu));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, lines(
      R.string.Tgx101LongPressSelect, R.string.Tgx101LongPressSelectHint,
      R.string.Tgx101LongPressOpenMenu, R.string.Tgx101LongPressOpenMenuHint
    ), false));
    return items;
  }

  /** The column as it is set up, «Reply» under the finger, with «move up / release / move down» */
  private void updatePreview () {
    if (preview == null) {
      return;
    }
    List<Tgx101OrderPreviewView.Entry> entries = new ArrayList<>();
    boolean column = Settings.instance().tgx101SwipeActions();
    for (String entry : Tgx101BarOrder.getOrder(Tgx101BarOrder.BAR_SWIPE)) {
      if (entry.startsWith("-")) continue;
      boolean reply = Tgx101BarOrder.SWIPE_REPLY.equals(entry);
      if (!column && !reply) continue;
      boolean red = Tgx101BarOrder.SWIPE_DELETE.equals(entry) || Tgx101BarOrder.SWIPE_DELETE_ALL.equals(entry);
      entries.add(new Tgx101OrderPreviewView.Entry(Tgx101BarOrder.swipeIconOf(entry), null, red, reply));
    }
    preview.setColumn(entries, true);
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (adapter != null) {
      adapter.updateValuedSettingById(R.id.btn_tgx101SwipeOrder);
      adapter.updateValuedSettingById(R.id.btn_tgx101DoubleTapWhich); // back from the quick reaction choice
      updatePreview(); // back from the column setup
    }
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    for (int i = 0; i < DOUBLE_TAP_IDS.length; i++) {
      if (DOUBLE_TAP_IDS[i] == id) {
        adapter.processToggle(v);
        Settings.instance().setTgx101DoubleTapAction(DOUBLE_TAP_ACTIONS[i]);
        org.thunderdog.challegram.Tgx101Diag.mark("settings: double tap → " + DOUBLE_TAP_ACTIONS[i]);
        return;
      }
    }
    if (id == R.id.btn_tgx101DoubleTapWhich) {
      EditEnabledReactionsController c = new EditEnabledReactionsController(context, tdlib);
      c.setArguments(new EditEnabledReactionsController.Args(null, EditEnabledReactionsController.TYPE_QUICK_REACTION));
      navigateTo(c);
      return;
    }
    if (id == R.id.btn_tgx101SwipeToStorage) {
      Settings.instance().setTgx101SwipeToStorage(adapter.toggleView(v));
    } else if (id == R.id.btn_tgx101SwipeReactions) {
      Settings.instance().setTgx101SwipeReactions(adapter.toggleView(v));
    } else if (id == R.id.btn_tgx101SwipeActions) {
      Settings.instance().setTgx101SwipeActions(adapter.toggleView(v));
      adapter.updateValuedSettingById(R.id.btn_tgx101SwipeOrder);
      updatePreview();
    } else if (id == R.id.btn_tgx101SwipeOrder) {
      Tgx101MenuOrderController c = new Tgx101MenuOrderController(context, tdlib);
      c.setArguments(new Tgx101MenuOrderController.Args(Tgx101MenuOrderController.MODE_SWIPE));
      navigateTo(c);
    } else if (id == R.id.btn_tgx101LongPressSelect || id == R.id.btn_tgx101LongPressMenuOpen) {
      adapter.processToggle(v);
      Settings.instance().setTgx101LongPressMenu(id == R.id.btn_tgx101LongPressMenuOpen);
    }
  }
}
