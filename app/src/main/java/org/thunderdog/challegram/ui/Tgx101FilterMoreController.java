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

/**
 * TGx101: a filter's fine options (approved mockup «Фильтры и живые примеры» Б): how to compare — case, whole word,
 * pattern — and where else to look, each with an example under it; deleting the filter.
 */
public class Tgx101FilterMoreController extends RecyclerViewController<Void> implements View.OnClickListener {
  private final Tgx101FilterEditController editor;
  private SettingsAdapter adapter;

  public Tgx101FilterMoreController (Context context, Tdlib tdlib, Tgx101FilterEditController editor) {
    super(context, tdlib);
    this.editor = editor;
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101FilterMore;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101FilterMoreTitle);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Tgx101MessageFilters.Rule rule = editor.rule();
        if (rule == null) return;
        int id = item.getId();
        if (id == R.id.btn_tgx101FilterMoreCase) {
          view.setData(R.string.Tgx101FilterCaseHint);
          view.getToggler().setRadioEnabled(rule.matchCase, isUpdate);
        } else if (id == R.id.btn_tgx101FilterWholeWord) {
          view.setData(R.string.Tgx101FilterWordHint);
          view.getToggler().setRadioEnabled(rule.wholeWord, isUpdate);
        } else if (id == R.id.btn_tgx101FilterRegex) {
          view.setData(R.string.Tgx101FilterPatternHint);
          view.getToggler().setRadioEnabled(rule.regex, isUpdate);
        } else if (id == R.id.btn_tgx101FilterAuthor) {
          view.setData(R.string.Tgx101FilterAuthorHint);
          view.getToggler().setRadioEnabled(rule.checkAuthor, isUpdate);
        }
      }
    };
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101FilterCompare));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_tgx101FilterMoreCase, 0, R.string.Tgx101FilterCaseShort));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_tgx101FilterWholeWord, 0, R.string.Tgx101FilterWholeWord));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_tgx101FilterRegex, 0, R.string.Tgx101FilterPatternShort));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101FilterWhereElse));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_tgx101FilterAuthor, 0, R.string.Tgx101FilterAuthorShort));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101FilterAlwaysHint));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101FilterDelete, R.drawable.baseline_delete_24, R.string.Tgx101FilterDelete).setTextColorId(ColorId.textNegative));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    Tgx101MessageFilters.Rule rule = editor.rule();
    if (rule == null) return;
    if (id == R.id.btn_tgx101FilterDelete) {
      editor.delete();
      navigateBack();
      editor.navigateBack();
      return;
    }
    boolean value = adapter.toggleView(v);
    if (id == R.id.btn_tgx101FilterMoreCase) {
      rule.matchCase = value;
    } else if (id == R.id.btn_tgx101FilterWholeWord) {
      rule.wholeWord = value;
    } else if (id == R.id.btn_tgx101FilterRegex) {
      // the tags become one pattern «a|b» and back
      List<String> words = rule.words();
      rule.regex = value;
      if (value) {
        StringBuilder b = new StringBuilder();
        for (String word : words) {
          if (b.length() > 0) b.append('|');
          b.append(word.matches("[\\p{L}\\p{N} ]+") ? word : java.util.regex.Pattern.quote(word));
        }
        rule.text = b.toString();
      } else {
        List<String> back = new ArrayList<>();
        for (String part : rule.text.split("\\|")) {
          String word = part.trim();
          if (word.startsWith("\\Q") && word.endsWith("\\E")) word = word.substring(2, word.length() - 2);
          if (!word.isEmpty() && !back.contains(word)) back.add(word);
        }
        rule.setWords(back);
      }
    } else if (id == R.id.btn_tgx101FilterAuthor) {
      rule.checkAuthor = value;
    }
    editor.store(rule);
  }
}
