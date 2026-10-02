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
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.data.Tgx101MessageFilters;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/** TGx101: MagiX → Message filters — the switch and the list of rules. */
public class Tgx101FiltersController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;

  public Tgx101FiltersController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101Filters;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101Filters);
  }

  /** Where a rule works, for lists */
  static String scopeName (Tgx101MessageFilters.Rule rule) {
    switch (rule.scope) {
      case Tgx101MessageFilters.SCOPE_CHANNELS:
        return Lang.getString(R.string.Tgx101FilterScopeChannels);
      case Tgx101MessageFilters.SCOPE_CHANNELS_GROUPS:
        return Lang.getString(R.string.Tgx101FilterScopeChannelsGroups);
      case Tgx101MessageFilters.SCOPE_CHAT:
        return Lang.getString(R.string.Tgx101FilterScopeChat, rule.chatTitle != null ? rule.chatTitle : "?");
      case Tgx101MessageFilters.SCOPE_ALL:
      default:
        return Lang.getString(R.string.Tgx101FilterScopeAll);
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        if (id == R.id.btn_tgx101FiltersEnabled) {
          view.getToggler().setRadioEnabled(Tgx101MessageFilters.isEnabled(), isUpdate);
        } else if (id == R.id.btn_tgx101FilterItem) {
          List<Tgx101MessageFilters.Rule> rules = Tgx101MessageFilters.getRules();
          int index = (int) item.getLongId();
          if (index < rules.size()) {
            Tgx101MessageFilters.Rule rule = rules.get(index);
            view.setData(scopeName(rule) + (rule.regex ? " · regex" : ""));
          }
        }
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (adapter != null) {
      adapter.setItems(buildItems(), false); // back from a filter's page
    }
  }

  private List<ListItem> buildItems () {
    List<Tgx101MessageFilters.Rule> rules = Tgx101MessageFilters.getRules();
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101FiltersEnabled, 0, R.string.Tgx101FiltersEnabled));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, Lang.getString(R.string.Tgx101FiltersHeader) + (rules.isEmpty() ? "" : " · " + rules.size()), false));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < rules.size(); i++) {
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101FilterItem, 0, Tgx101MessageFilters.label(rules.get(i).text), false).setLongId(i));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101FilterAdd, R.drawable.baseline_add_24, R.string.Tgx101FiltersAdd).setTextColorId(org.thunderdog.challegram.theme.ColorId.textNeutral));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101FiltersHint));
    return items;
  }

  /** Asks for the text of a new rule, then opens its page; scope = this chat when started from a message */
  static void addRule (ViewController<?> parent, String suggestion, long chatId, String chatTitle) {
    parent.openInputAlert(Lang.getString(R.string.Tgx101FilterNew), Lang.getString(R.string.Tgx101FilterText), R.string.Done, R.string.Cancel, suggestion, (inputView, result) -> {
      String text = result.trim();
      if (text.isEmpty()) {
        return false;
      }
      Tgx101MessageFilters.Rule rule = new Tgx101MessageFilters.Rule();
      rule.text = text;
      rule.wholeWord = false;
      if (chatId != 0) {
        rule.scope = Tgx101MessageFilters.SCOPE_CHAT;
        rule.chatId = chatId;
        rule.chatTitle = chatTitle;
      }
      List<Tgx101MessageFilters.Rule> rules = new ArrayList<>(Tgx101MessageFilters.getRules());
      rules.add(rule);
      Tgx101MessageFilters.setRules(rules);
      Tgx101FilterEditController c = new Tgx101FilterEditController(parent.context(), parent.tdlib());
      c.setArguments(rules.size() - 1);
      parent.navigateTo(c);
      return true;
    }, true);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_tgx101FiltersEnabled) {
      Settings.instance().setTgx101MessageFiltersEnabled(adapter.toggleView(v)); // applies to chats opened afterwards
    } else if (id == R.id.btn_tgx101FilterAdd) {
      addRule(this, null, 0, null);
    } else if (id == R.id.btn_tgx101FilterItem) {
      ListItem item = (ListItem) v.getTag();
      Tgx101FilterEditController c = new Tgx101FilterEditController(context, tdlib);
      c.setArguments((int) item.getLongId());
      navigateTo(c);
    }
  }
}
