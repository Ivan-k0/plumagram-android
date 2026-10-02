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
import org.thunderdog.challegram.data.Tgx101MessageFilters;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/** TGx101: one message filter — text, options, where it works. Every change is saved at once. */
public class Tgx101FilterEditController extends RecyclerViewController<Integer> implements View.OnClickListener {
  private SettingsAdapter adapter;

  public Tgx101FilterEditController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101FilterEdit;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101FilterEdit);
  }

  private int index () {
    Integer index = getArguments();
    return index != null ? index : -1;
  }

  /** A copy of the rule being edited, or null when it is gone */
  private Tgx101MessageFilters.Rule rule () {
    List<Tgx101MessageFilters.Rule> rules = Tgx101MessageFilters.getRules();
    int index = index();
    return index >= 0 && index < rules.size() ? rules.get(index).copy() : null;
  }

  private void store (Tgx101MessageFilters.Rule rule) {
    List<Tgx101MessageFilters.Rule> rules = new ArrayList<>(Tgx101MessageFilters.getRules());
    int index = index();
    if (index >= 0 && index < rules.size()) {
      rules.set(index, rule);
      Tgx101MessageFilters.setRules(rules);
    }
  }

  /** Error text when the rule is a broken regular expression */
  private static String patternError (Tgx101MessageFilters.Rule rule) {
    try {
      Tgx101MessageFilters.compile(rule);
      return null;
    } catch (PatternSyntaxException e) {
      return e.getDescription();
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Tgx101MessageFilters.Rule rule = rule();
        if (rule == null) return;
        int id = item.getId();
        if (id == R.id.btn_tgx101FilterText) {
          String error = patternError(rule);
          view.setData(error != null ? Lang.getString(R.string.Tgx101FilterBadRegex, error) : rule.text);
        } else if (id == R.id.btn_tgx101FilterRegex) {
          view.getToggler().setRadioEnabled(rule.regex, isUpdate);
        } else if (id == R.id.btn_tgx101FilterCase) {
          view.getToggler().setRadioEnabled(rule.matchCase, isUpdate);
        } else if (id == R.id.btn_tgx101FilterWholeWord) {
          view.getToggler().setRadioEnabled(rule.wholeWord, isUpdate);
        } else if (id == R.id.btn_tgx101FilterAuthor) {
          view.getToggler().setRadioEnabled(rule.checkAuthor, isUpdate);
        } else if (id == R.id.btn_tgx101FilterScope) {
          view.setData(Tgx101FiltersController.scopeName(rule));
        }
      }
    };
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_tgx101FilterText, 0, R.string.Tgx101FilterText));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101FilterRegex, 0, R.string.Tgx101FilterRegex));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101FilterCase, 0, R.string.Tgx101FilterCase));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101FilterWholeWord, 0, R.string.Tgx101FilterWholeWord));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101FilterRegexHint));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101FilterWhere));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101FilterScope, 0, R.string.Tgx101FilterWhere));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101FilterAuthor, 0, R.string.Tgx101FilterCheckAuthor));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101FilterCheckHint));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101FilterDelete, R.drawable.baseline_delete_24, R.string.Tgx101FilterDelete).setTextColorId(ColorId.textNegative));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    Tgx101MessageFilters.Rule rule = rule();
    if (rule == null) return;
    if (id == R.id.btn_tgx101FilterText) {
      openInputAlert(Lang.getString(R.string.Tgx101FilterEdit), Lang.getString(R.string.Tgx101FilterText), R.string.Done, R.string.Cancel, rule.text, (inputView, result) -> {
        String text = result.trim();
        if (text.isEmpty()) return false;
        rule.text = text;
        rule.invalidate();
        store(rule);
        adapter.updateValuedSettingById(R.id.btn_tgx101FilterText);
        return true;
      }, true);
    } else if (id == R.id.btn_tgx101FilterRegex || id == R.id.btn_tgx101FilterCase || id == R.id.btn_tgx101FilterWholeWord || id == R.id.btn_tgx101FilterAuthor) {
      boolean value = adapter.toggleView(v);
      if (id == R.id.btn_tgx101FilterRegex) rule.regex = value;
      else if (id == R.id.btn_tgx101FilterCase) rule.matchCase = value;
      else if (id == R.id.btn_tgx101FilterWholeWord) rule.wholeWord = value;
      else rule.checkAuthor = value;
      rule.invalidate();
      store(rule);
      adapter.updateValuedSettingById(R.id.btn_tgx101FilterText); // a broken expression shows its error
    } else if (id == R.id.btn_tgx101FilterScope) {
      showScopes(rule);
    } else if (id == R.id.btn_tgx101FilterDelete) {
      List<Tgx101MessageFilters.Rule> rules = new ArrayList<>(Tgx101MessageFilters.getRules());
      int index = index();
      if (index >= 0 && index < rules.size()) {
        rules.remove(index);
        Tgx101MessageFilters.setRules(rules);
      }
      navigateBack();
    }
  }

  private void showScopes (Tgx101MessageFilters.Rule rule) {
    boolean hasChat = rule.chatId != 0;
    int count = hasChat ? 4 : 3;
    int[] ids = new int[count];
    String[] names = new String[count];
    int[] scopes = new int[count];
    ids[0] = R.id.btn_tgx101FilterScopeAll; names[0] = Lang.getString(R.string.Tgx101FilterScopeAll); scopes[0] = Tgx101MessageFilters.SCOPE_ALL;
    ids[1] = R.id.btn_tgx101FilterScopeChannels; names[1] = Lang.getString(R.string.Tgx101FilterScopeChannels); scopes[1] = Tgx101MessageFilters.SCOPE_CHANNELS;
    ids[2] = R.id.btn_tgx101FilterScopeChannelsGroups; names[2] = Lang.getString(R.string.Tgx101FilterScopeChannelsGroups); scopes[2] = Tgx101MessageFilters.SCOPE_CHANNELS_GROUPS;
    if (hasChat) {
      ids[3] = R.id.btn_tgx101FilterScopeChat; names[3] = Lang.getString(R.string.Tgx101FilterScopeChat, rule.chatTitle != null ? rule.chatTitle : "?"); scopes[3] = Tgx101MessageFilters.SCOPE_CHAT;
    }
    for (int i = 0; i < count; i++) {
      if (scopes[i] == rule.scope) names[i] = names[i] + "  ✓";
    }
    showOptions(Lang.getString(R.string.Tgx101FilterWhere), ids, names, null, null, (itemView, optionId) -> {
      for (int i = 0; i < count; i++) {
        if (ids[i] == optionId) {
          rule.scope = scopes[i];
          store(rule);
          adapter.updateValuedSettingById(R.id.btn_tgx101FilterScope);
          break;
        }
      }
      return true;
    });
  }
}
