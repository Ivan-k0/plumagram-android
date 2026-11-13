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
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;

import android.app.AlertDialog;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101OwnAi;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TGx101 «Свой ИИ» → «Модель» (user 2026-11-13 «забить доступными моделями… по удержанию описание преимуществ и
 * минусов», a screenshot of such a list): the service's own models, asked with the person's key, with a search in the
 * header; a tap picks one, holding one tells what it is good and bad at; typing the name by hand stays.
 */
public class Tgx101OwnAiModelsController extends RecyclerViewController<Runnable> implements View.OnClickListener, View.OnLongClickListener {
  public Tgx101OwnAiModelsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101OwnAiModels;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101OwnAiModels);
  }

  private SettingsAdapter adapter;
  private @androidx.annotation.Nullable List<Tgx101OwnAi.ModelInfo> models;
  private @androidx.annotation.Nullable String error;
  private String query = "";

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        if (data instanceof Tgx101OwnAi.ModelInfo) {
          Tgx101OwnAi.ModelInfo m = (Tgx101OwnAi.ModelInfo) data;
          boolean current = m.id.equals(Tgx101OwnAi.model(Tgx101OwnAi.providerId()));
          String hint = shortHint(m);
          if (!m.name.equals(m.id)) hint = TextUtils.isEmpty(hint) ? m.id : m.id + " · " + hint;
          if (current) hint = "✓ " + Lang.getString(R.string.Tgx101OwnAiModelCurrent) + (TextUtils.isEmpty(hint) ? "" : " · " + hint);
          view.setData(hint);
          view.setDataColorId(current ? ColorId.textNeutral : 0);
        }
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
    Tgx101OwnAi.listModels(result -> {
      if (isDestroyed()) return;
      if (result instanceof List) {
        //noinspection unchecked
        models = (List<Tgx101OwnAi.ModelInfo>) result;
      } else {
        error = String.valueOf(result);
      }
      adapter.setItems(buildItems(), false);
    });
  }

  private List<ListItem> buildItems () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101OwnAiModelManual, R.drawable.baseline_edit_24, R.string.Tgx101OwnAiModelManual));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    if (models == null) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, error != null ? Lang.getString(R.string.Tgx101OwnAiModelsFailed, error) : Lang.getString(R.string.Tgx101OwnAiModelsLoading), false));
      return items;
    }
    if (models.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101OwnAiModelsEmpty));
      return items;
    }
    String q = query.trim().toLowerCase(Locale.ROOT);
    boolean first = true;
    for (Tgx101OwnAi.ModelInfo m : models) {
      if (!q.isEmpty() && !m.id.toLowerCase(Locale.ROOT).contains(q) && !m.name.toLowerCase(Locale.ROOT).contains(q)) continue;
      items.add(new ListItem(first ? ListItem.TYPE_SHADOW_TOP : ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101OwnAiModelPick, 0, m.name, false).setData(m));
      first = false;
    }
    if (!first) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101OwnAiModelsHold));
    }
    return items;
  }

  // What a model is good and bad at: its name says how fast or smart it is, the service tells how much it holds and costs

  private static String shortHint (Tgx101OwnAi.ModelInfo m) {
    List<String> parts = new ArrayList<>();
    int t = Tgx101OwnAi.modelTraits(m.id);
    if ((t & Tgx101OwnAi.MODEL_THINKING) != 0) parts.add(firstWords(R.string.Tgx101OwnAiModelThinking));
    if ((t & Tgx101OwnAi.MODEL_FAST) != 0) parts.add(firstWords(R.string.Tgx101OwnAiModelFast));
    else if ((t & Tgx101OwnAi.MODEL_SMART) != 0) parts.add(firstWords(R.string.Tgx101OwnAiModelSmart));
    else if ((t & Tgx101OwnAi.MODEL_THINKING) == 0) parts.add(Lang.getString(R.string.Tgx101OwnAiModelBalanced)); // never an empty row
    String ctx = shortContext(m.contextTokens);
    if (ctx != null) parts.add(ctx);
    if ("0".equals(m.priceIn) && "0".equals(m.priceOut)) parts.add(Lang.getString(R.string.Tgx101OwnAiModelFree));
    return TextUtils.join(" · ", parts);
  }

  /** «быстрая и дешёвая, ответы попроще» → «быстрая и дешёвая» for the row */
  private static String firstWords (int res) {
    String s = Lang.getString(res);
    int cut = s.indexOf(':');
    if (cut < 0) cut = s.indexOf(',');
    return cut > 0 ? s.substring(0, cut) : s;
  }

  /** «131 тыс. токенов» for the row, the free mark still fits after it */
  private static @androidx.annotation.Nullable String shortContext (long tokens) {
    if (tokens <= 0) return null;
    if (tokens >= 1_000_000) {
      double m = tokens / 1_000_000d;
      String v = m == Math.floor(m) ? String.valueOf((long) m) : String.format(Locale.US, "%.1f", m).replace(".0", "");
      return Lang.getString(R.string.Tgx101OwnAiModelTokensM, v);
    }
    return Lang.getString(R.string.Tgx101OwnAiModelTokensK, String.valueOf(Math.round(tokens / 1000d)));
  }

  private static @androidx.annotation.Nullable String context (long tokens) {
    if (tokens <= 0) return null;
    if (tokens >= 1_000_000) {
      double m = tokens / 1_000_000d;
      String v = m == Math.floor(m) ? String.valueOf((long) m) : String.format(Locale.US, "%.1f", m).replace(".0", "");
      return Lang.getString(R.string.Tgx101OwnAiModelContextM, v);
    }
    return Lang.getString(R.string.Tgx101OwnAiModelContextK, String.valueOf(Math.round(tokens / 1000d)));
  }

  private void showAbout (Tgx101OwnAi.ModelInfo m) {
    StringBuilder b = new StringBuilder();
    int t = Tgx101OwnAi.modelTraits(m.id);
    if ((t & Tgx101OwnAi.MODEL_THINKING) != 0) b.append("• ").append(Lang.getString(R.string.Tgx101OwnAiModelThinking)).append('\n');
    if ((t & Tgx101OwnAi.MODEL_FAST) != 0) b.append("• ").append(Lang.getString(R.string.Tgx101OwnAiModelFast)).append('\n');
    else if ((t & Tgx101OwnAi.MODEL_SMART) != 0) b.append("• ").append(Lang.getString(R.string.Tgx101OwnAiModelSmart)).append('\n');
    else if ((t & Tgx101OwnAi.MODEL_THINKING) == 0) b.append("• ").append(Lang.getString(R.string.Tgx101OwnAiModelBalanced)).append('\n');
    String ctx = context(m.contextTokens);
    if (ctx != null) b.append("• ").append(ctx).append('\n');
    if (m.priceIn != null && m.priceOut != null) {
      b.append("• ").append("0".equals(m.priceIn) && "0".equals(m.priceOut) ? Lang.getString(R.string.Tgx101OwnAiModelFree) : Lang.getString(R.string.Tgx101OwnAiModelPrice, m.priceIn, m.priceOut)).append('\n');
    }
    b.append('\n').append(Lang.getString(R.string.Tgx101OwnAiModelGuess));
    if (m.about != null) {
      String about = m.about.length() > 600 ? m.about.substring(0, 600) + "…" : m.about;
      b.append("\n\n").append(Lang.getString(R.string.Tgx101OwnAiModelAboutService)).append('\n').append(about);
    }
    AlertDialog.Builder d = new AlertDialog.Builder(context, Theme.dialogTheme());
    d.setTitle(m.name.equals(m.id) ? m.id : m.name + "\n" + m.id);
    d.setMessage(b.toString());
    d.setPositiveButton(Lang.getString(R.string.Tgx101OwnAiModelPickThis), (dialog, which) -> pick(m.id));
    d.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
    showAlert(d);
  }

  private void pick (String modelId) {
    int provider = Tgx101OwnAi.providerId();
    Tgx101OwnAi.setModel(provider, modelId);
    org.thunderdog.challegram.Tgx101Diag.mark("own ai: model picked from the list — " + modelId);
    Runnable after = getArguments();
    if (after != null) after.run();
    navigateBack();
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_tgx101OwnAiModelManual) {
      int provider = Tgx101OwnAi.providerId();
      openInputAlert(Lang.getString(R.string.Tgx101OwnAiModel), Lang.getString(R.string.Tgx101OwnAiModelHint), R.string.Done, R.string.Cancel, Tgx101OwnAi.model(provider), (inputView, result) -> {
        String name = result.trim();
        if (name.isEmpty()) return false;
        pick(name);
        return true;
      }, true);
    } else if (id == R.id.btn_tgx101OwnAiModelPick) {
      Object data = ((ListItem) v.getTag()).getData();
      if (data instanceof Tgx101OwnAi.ModelInfo) pick(((Tgx101OwnAi.ModelInfo) data).id);
    }
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getId() == R.id.btn_tgx101OwnAiModelPick && v.getTag() instanceof ListItem) {
      Object data = ((ListItem) v.getTag()).getData();
      if (data instanceof Tgx101OwnAi.ModelInfo) {
        showAbout((Tgx101OwnAi.ModelInfo) data);
        return true;
      }
    }
    return false;
  }

  // Search in the header

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_search) {
      header.addSearchButton(menu, this);
    } else if (id == R.id.menu_clear) {
      header.addClearButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_search) {
      if (headerView != null) headerView.openSearchMode();
    } else if (id == R.id.menu_btn_clear) {
      clearSearchInput();
    }
  }

  @Override
  protected void onSearchInputChanged (String query) {
    super.onSearchInputChanged(query);
    this.query = query;
    if (adapter != null) adapter.setItems(buildItems(), false);
  }

  @Override
  protected void onLeaveSearchMode () {
    this.query = "";
    if (adapter != null) adapter.setItems(buildItems(), false);
  }
}
