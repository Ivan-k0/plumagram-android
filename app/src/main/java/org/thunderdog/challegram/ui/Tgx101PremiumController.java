/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/tgx101-android)
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
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.Tgx101Stars;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

import me.vkryl.core.CurrencyUtils;
import tgx.td.Td;

/**
 * TGx101: Telegram Premium. The app can't use Google Play billing, so Premium is bought through
 * the payment link Telegram gives for each option (@PremiumBot), on Fragment, or gifted with Stars.
 */
public class Tgx101PremiumController extends RecyclerViewController<Void> implements View.OnClickListener {
  public static final String FRAGMENT_PREMIUM_URL = "https://fragment.com/premium";

  public Tgx101PremiumController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101Premium;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101Premium);
  }

  private SettingsAdapter adapter;
  private TdApi.PremiumState state;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_tgx101PremiumOption) {
          TdApi.PremiumStatePaymentOption option = (TdApi.PremiumStatePaymentOption) item.getData();
          TdApi.PremiumPaymentOption payment = option.paymentOption;
          String price = CurrencyUtils.buildAmount(payment.currency, payment.amount);
          if (payment.discountPercentage > 0) {
            price += " (−" + payment.discountPercentage + "%)";
          }
          if (option.isCurrent) {
            price += " · " + Lang.getString(R.string.Tgx101PremiumCurrent);
          }
          view.setData(price);
        }
      }
    };
    buildCells();
    recyclerView.setAdapter(adapter);
    tdlib.send(new TdApi.GetPremiumState(), (result, error) -> runOnUiThreadOptional(() -> {
      stateLoaded = true;
      if (error != null) {
        UI.showError(error);
      } else {
        state = result;
        buildCells();
      }
      executeScheduledAnimation();
    }));
  }

  private boolean stateLoaded;

  // Open once the options are in, so the list isn't rebuilt mid-animation (flash).
  @Override
  public boolean needAsynchronousAnimation () {
    return !stateLoaded;
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101PremiumBuy));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    if (state != null) {
      for (TdApi.PremiumStatePaymentOption option : state.paymentOptions) {
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101PremiumOption, R.drawable.baseline_star_24, Lang.plural(R.string.xMonths, option.paymentOption.monthCount)).setData(option));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
    }
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_openLink, R.drawable.baseline_open_in_browser_24, R.string.Tgx101PremiumFragment));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, stateText(), false));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101PremiumGift));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101PremiumGift, R.drawable.baseline_star_24, R.string.Tgx101PremiumGiftStars));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101PremiumGiftHint));
    adapter.setItems(items, false);
  }

  private CharSequence stateText () {
    StringBuilder b = new StringBuilder();
    if (state != null && !Td.isEmpty(state.state)) {
      b.append(state.state.text).append("\n\n");
    } else if (state != null) {
      b.append(Lang.getString(tdlib.hasPremium() ? R.string.Tgx101PremiumActive : R.string.Tgx101PremiumInactive)).append("\n\n");
    }
    b.append(Lang.getString(R.string.Tgx101PremiumBuyHint));
    return b.toString();
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_tgx101PremiumOption) {
      TdApi.PremiumStatePaymentOption option = (TdApi.PremiumStatePaymentOption) ((ListItem) v.getTag()).getData();
      if (option.paymentOption.paymentLink != null) {
        tdlib.ui().openInternalLinkType(this, null, option.paymentOption.paymentLink, null, null);
      } else {
        UI.showToast(R.string.Tgx101PremiumNoLink, Toast.LENGTH_LONG);
      }
    } else if (id == R.id.btn_openLink) {
      tdlib.ui().openUrl(this, FRAGMENT_PREMIUM_URL, null);
    } else if (id == R.id.btn_tgx101PremiumGift) {
      pickGiftUser();
    }
  }

  // Gift Premium to another user with Stars

  private void pickGiftUser () {
    ChatsController picker = new ChatsController(context, tdlib);
    picker.setArguments(new ChatsController.Arguments(new ChatsController.PickerDelegate() {
      @Override
      public boolean onChatPicked (TdApi.Chat chat, Runnable onDone) {
        long userId = TD.getUserId(chat);
        if (userId == 0 || tdlib.isSelfUserId(userId) || tdlib.isBotChat(chat)) {
          UI.showToast(R.string.Tgx101PremiumGiftPickUser, Toast.LENGTH_SHORT);
          return false;
        }
        showGiftOptions(Tgx101PremiumController.this, userId);
        return true;
      }

      @Override
      public int getTitleStringRes () {
        return R.string.Tgx101PremiumGiftStars;
      }
    }));
    navigateTo(picker);
  }

  private static void showGiftOptions (ViewController<?> c, long userId) {
    Tdlib tdlib = c.tdlib();
    tdlib.send(new TdApi.GetPremiumGiftPaymentOptions(), (result, error) -> {
      if (error != null) {
        UI.post(() -> UI.showError(error));
        return;
      }
      ArrayList<TdApi.PremiumGiftPaymentOption> options = new ArrayList<>();
      for (TdApi.PremiumGiftPaymentOption option : result.options) {
        if (option.starCount > 0) {
          options.add(option);
        }
      }
      Tgx101Stars.requestBalance(tdlib, balance -> {
        if (c.isDestroyed()) return;
        if (options.isEmpty()) {
          UI.showToast(R.string.Tgx101PremiumGiftUnavailable, Toast.LENGTH_LONG);
          return;
        }
        int[] ids = new int[options.size() + 1];
        String[] titles = new String[ids.length];
        int[] colors = new int[ids.length];
        int[] icons = new int[ids.length];
        for (int i = 0; i < options.size(); i++) {
          TdApi.PremiumGiftPaymentOption option = options.get(i);
          ids[i] = i + 1;
          titles[i] = Lang.plural(R.string.xMonths, option.monthCount) + " — " + Tgx101Stars.formatStars(option.starCount);
          colors[i] = ViewController.OptionColor.NORMAL;
          icons[i] = R.drawable.baseline_star_24;
        }
        ids[ids.length - 1] = R.id.btn_cancel;
        titles[ids.length - 1] = Lang.getString(R.string.Cancel);
        colors[ids.length - 1] = ViewController.OptionColor.NORMAL;
        icons[ids.length - 1] = R.drawable.baseline_cancel_24;
        String info = Lang.getString(R.string.Tgx101PremiumGiftConfirm, tdlib.cache().userName(userId), balance >= 0 ? Tgx101Stars.formatStars(balance) : "?");
        c.showOptions(info, ids, titles, colors, icons, (itemView, id) -> {
          if (id == R.id.btn_cancel || id < 1 || id > options.size()) {
            return true;
          }
          TdApi.PremiumGiftPaymentOption option = options.get(id - 1);
          if (balance >= 0 && option.starCount > balance) {
            UI.showToast(Lang.getString(R.string.StarsNotEnough, Tgx101Stars.formatStars(option.starCount), Tgx101Stars.formatStars(balance)), Toast.LENGTH_LONG);
            return true;
          }
          tdlib.send(new TdApi.GiftPremiumWithStars(userId, option.starCount, option.monthCount, new TdApi.FormattedText("", new TdApi.TextEntity[0])), (ok, giftError) -> UI.post(() -> {
            if (giftError != null) {
              UI.showToast(Lang.getString(R.string.StarsPayFailed, TD.toErrorString(giftError)), Toast.LENGTH_LONG);
            } else {
              UI.showToast(R.string.Tgx101PremiumGiftSent, Toast.LENGTH_SHORT);
            }
          }));
          return true;
        });
      });
    });
  }
}
