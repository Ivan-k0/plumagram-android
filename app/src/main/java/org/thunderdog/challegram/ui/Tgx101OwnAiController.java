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
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101OwnAi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101 «Свой ИИ» (user 2026-11-13): the person's own AI service for the AI features — which service, its key, model and
 * (for «Другой») address, a test request, and when it is used: after Telegram's own AI says no, always, or never.
 */
public class Tgx101OwnAiController extends RecyclerViewController<Void> implements View.OnClickListener {
  public Tgx101OwnAiController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101OwnAi;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101OwnAi);
  }

  private static final int[] PROVIDER_BUTTONS = {
    R.id.btn_tgx101OwnAiNone, R.id.btn_tgx101OwnAiP1, R.id.btn_tgx101OwnAiP2, R.id.btn_tgx101OwnAiP3,
    R.id.btn_tgx101OwnAiP4, R.id.btn_tgx101OwnAiP5, R.id.btn_tgx101OwnAiP6
  };
  private static final int[] MODE_BUTTONS = {R.id.btn_tgx101OwnAiMode0, R.id.btn_tgx101OwnAiMode1, R.id.btn_tgx101OwnAiMode2};

  private SettingsAdapter adapter;
  private String testResult;
  private boolean testing;

  /** what «Функции ИИ» shows next to «Свой ИИ» */
  public static String value (Context context) {
    int id = Tgx101OwnAi.providerId();
    if (id == 0 || !Tgx101OwnAi.isReady()) return Lang.getString(R.string.Tgx101OwnAiNotSet);
    return Tgx101OwnAi.displayName(context, id) + " · " + Tgx101OwnAi.model(id);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        int provider = Tgx101OwnAi.providerId();
        if (id == R.id.btn_tgx101OwnAiKey) {
          view.setData(Tgx101OwnAi.hasKey(provider) ? Tgx101OwnAi.maskedKey(provider) : Lang.getString(R.string.Tgx101OwnAiKeyNone));
        } else if (id == R.id.btn_tgx101OwnAiModel) {
          view.setData(Tgx101OwnAi.model(provider));
        } else if (id == R.id.btn_tgx101OwnAiUrl) {
          String url = Tgx101OwnAi.customUrl();
          view.setData(TextUtils.isEmpty(url) ? Lang.getString(R.string.Tgx101OwnAiKeyNone) : url);
        } else if (id == R.id.btn_tgx101OwnAiTest) {
          view.setData(testing ? Lang.getString(R.string.Tgx101OwnAiTesting)
            : testResult == null ? Lang.getString(R.string.Tgx101OwnAiTestHint)
            : testResult.isEmpty() ? Lang.getString(R.string.Tgx101OwnAiTestOk)
            : Lang.getString(R.string.Tgx101OwnAiTestFailed, testResult));
          view.setEnabledAnimated(Tgx101OwnAi.isReady() && !testing, isUpdate);
        }
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
  }

  private List<ListItem> buildItems () {
    int provider = Tgx101OwnAi.providerId();
    int mode = Tgx101OwnAi.mode();
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101OwnAiService));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, PROVIDER_BUTTONS[0], 0, R.string.Tgx101OwnAiNoService, PROVIDER_BUTTONS[0], provider == 0));
    for (Tgx101OwnAi.Provider p : Tgx101OwnAi.PROVIDERS) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, PROVIDER_BUTTONS[p.id], 0, Tgx101OwnAi.displayName(context, p.id), PROVIDER_BUTTONS[0], provider == p.id));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (provider != 0) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101OwnAiConnection));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      if (provider == Tgx101OwnAi.PROVIDER_CUSTOM) {
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101OwnAiUrl, 0, R.string.Tgx101OwnAiUrl));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101OwnAiKey, 0, R.string.Tgx101OwnAiKey));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101OwnAiModel, 0, R.string.Tgx101OwnAiModel));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101OwnAiTest, 0, R.string.Tgx101OwnAiTest));
      if (Tgx101OwnAi.hasKey(provider)) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101OwnAiForget, 0, R.string.Tgx101OwnAiForget).setTextColorId(org.thunderdog.challegram.theme.ColorId.textNegative));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(provider == Tgx101OwnAi.PROVIDER_CUSTOM ? R.string.Tgx101OwnAiCustomHint : R.string.Tgx101OwnAiKeyWhere, Tgx101OwnAi.displayName(context, provider)), false));

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101OwnAiWhen));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, MODE_BUTTONS[0], 0, R.string.Tgx101OwnAiModeFirst, MODE_BUTTONS[0], mode == Tgx101OwnAi.MODE_TELEGRAM_FIRST));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, MODE_BUTTONS[1], 0, R.string.Tgx101OwnAiModeAlways, MODE_BUTTONS[0], mode == Tgx101OwnAi.MODE_ALWAYS_OWN));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, MODE_BUTTONS[2], 0, R.string.Tgx101OwnAiModeTelegram, MODE_BUTTONS[0], mode == Tgx101OwnAi.MODE_TELEGRAM_ONLY));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101OwnAiPrivacy));
    return items;
  }

  private void rebuild () {
    if (adapter != null) adapter.setItems(buildItems(), false);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    int provider = Tgx101OwnAi.providerId();
    for (int i = 0; i < PROVIDER_BUTTONS.length; i++) {
      if (id == PROVIDER_BUTTONS[i]) {
        Tgx101OwnAi.setProviderId(i);
        testResult = null;
        rebuild();
        return;
      }
    }
    for (int i = 0; i < MODE_BUTTONS.length; i++) {
      if (id == MODE_BUTTONS[i]) {
        Tgx101OwnAi.setMode(i);
        rebuild();
        return;
      }
    }
    if (id == R.id.btn_tgx101OwnAiKey) {
      Tgx101OwnAi.Provider p = Tgx101OwnAi.provider(provider);
      MaterialEditTextGroup group = openInputAlert(Lang.getString(R.string.Tgx101OwnAiKey), p != null && !TextUtils.isEmpty(p.keyHint) ? p.keyHint : Lang.getString(R.string.Tgx101OwnAiKey),
        R.string.Done, R.string.Cancel, null, (inputView, result) -> {
          String key = result.trim();
          if (key.isEmpty() && provider != Tgx101OwnAi.PROVIDER_CUSTOM) return false;
          Tgx101OwnAi.setKey(provider, key);
          testResult = null;
          rebuild();
          return true;
        }, true);
      if (group != null) {
        // the key typed hidden, not kept by the keyboard
        group.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
      }
    } else if (id == R.id.btn_tgx101OwnAiModel) {
      openInputAlert(Lang.getString(R.string.Tgx101OwnAiModel), Lang.getString(R.string.Tgx101OwnAiModelHint), R.string.Done, R.string.Cancel, Tgx101OwnAi.model(provider), (inputView, result) -> {
        Tgx101OwnAi.setModel(provider, result.trim());
        testResult = null;
        adapter.updateValuedSettingById(R.id.btn_tgx101OwnAiModel);
        adapter.updateValuedSettingById(R.id.btn_tgx101OwnAiTest);
        return true;
      }, true);
    } else if (id == R.id.btn_tgx101OwnAiUrl) {
      openInputAlert(Lang.getString(R.string.Tgx101OwnAiUrl), "https://…/v1", R.string.Done, R.string.Cancel, Tgx101OwnAi.customUrl(), (inputView, result) -> {
        String url = result.trim();
        if (!url.isEmpty() && !url.startsWith("https://") && !url.startsWith("http://")) return false;
        Tgx101OwnAi.setCustomUrl(url);
        testResult = null;
        adapter.updateValuedSettingById(R.id.btn_tgx101OwnAiUrl);
        adapter.updateValuedSettingById(R.id.btn_tgx101OwnAiTest);
        return true;
      }, true);
    } else if (id == R.id.btn_tgx101OwnAiTest) {
      if (testing || !Tgx101OwnAi.isReady()) return;
      testing = true;
      adapter.updateValuedSettingById(R.id.btn_tgx101OwnAiTest);
      Tgx101OwnAi.test(error -> {
        if (isDestroyed()) return;
        testing = false;
        testResult = error != null ? error : "";
        adapter.updateValuedSettingById(R.id.btn_tgx101OwnAiTest);
      });
    } else if (id == R.id.btn_tgx101OwnAiForget) {
      Tgx101OwnAi.setKey(provider, null);
      testResult = null;
      rebuild();
    }
  }
}
