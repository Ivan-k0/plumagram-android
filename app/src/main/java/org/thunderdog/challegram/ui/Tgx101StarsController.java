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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101Stars;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;

/** TGx101: Telegram Stars balance and history. Buying Stars is possible only on Fragment or in the official app. */
public class Tgx101StarsController extends RecyclerViewController<Void> implements View.OnClickListener {
  private static final int PAGE_SIZE = 50;

  public Tgx101StarsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101Stars;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101Stars);
  }

  private SettingsAdapter adapter;
  private long balance = -1;
  private final List<TdApi.StarTransaction> transactions = new ArrayList<>();
  private String nextOffset = "";
  private boolean loading, loaded;
  private final List<TdApi.StarPaymentOption> buyOptions = new ArrayList<>();
  private boolean hasMoreBuyOptions;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_tgx101StarsBalance) {
          view.setData(balance >= 0 ? Tgx101Stars.formatStars(balance) : Lang.getString(R.string.LoadingInformation));
        } else if (item.getId() == R.id.btn_tgx101StarsBuyOption) {
          TdApi.StarPaymentOption option = (TdApi.StarPaymentOption) item.getData();
          view.setData(me.vkryl.core.CurrencyUtils.buildAmount(option.currency, option.amount));
        } else if (item.getId() == R.id.btn_tgx101StarsTransaction) {
          TdApi.StarTransaction transaction = (TdApi.StarTransaction) item.getData();
          view.setData(Lang.dateYearShortTime(transaction.date, TimeUnit.SECONDS) + (transaction.isRefund ? " · " + Lang.getString(R.string.Tgx101StarsRefund) : ""));
        }
      }
    };
    buildCells();
    recyclerView.setAdapter(adapter);
    loadMore();
    tdlib.send(new TdApi.GetStarPaymentOptions(), (result, error) -> runOnUiThreadOptional(() -> {
      optionsLoaded = true;
      if (result != null) {
        buyOptions.clear();
        for (TdApi.StarPaymentOption option : result.options) {
          buyOptions.add(option);
        }
        buildCells();
      }
      checkReady();
    }));
  }

  private boolean optionsLoaded, firstPageLoaded;

  // Open once balance, options and history are in, so the list isn't rebuilt mid-animation (flash).
  @Override
  public boolean needAsynchronousAnimation () {
    return !optionsLoaded || !firstPageLoaded;
  }

  private void checkReady () {
    if (optionsLoaded && firstPageLoaded) {
      executeScheduledAnimation();
    }
  }

  private boolean needRefresh;

  @Override
  public void onFocus () {
    super.onFocus();
    if (needRefresh) { // back from a purchase: fresh balance and history
      needRefresh = false;
      transactions.clear();
      nextOffset = "";
      loaded = false;
      loadMore();
    }
  }

  @Override
  public void onBlur () {
    super.onBlur();
    needRefresh = true;
  }

  /** Stars bought with a card: an invoice from Telegram itself, paid through the card form. */
  private void buy (TdApi.StarPaymentOption option) {
    Tgx101Stars.pay(this, new TdApi.InputInvoiceTelegram(new TdApi.TelegramPaymentPurposeStars(option.currency, option.amount, option.starCount, 0)));
  }

  private void showAllBuyOptions () {
    int[] ids = new int[buyOptions.size()];
    String[] titles = new String[ids.length];
    int[] icons = new int[ids.length];
    for (int i = 0; i < ids.length; i++) {
      TdApi.StarPaymentOption option = buyOptions.get(i);
      ids[i] = i + 1;
      titles[i] = Tgx101Stars.formatStars(option.starCount) + " — " + me.vkryl.core.CurrencyUtils.buildAmount(option.currency, option.amount);
      icons[i] = R.drawable.baseline_star_24;
    }
    showOptions(null, ids, titles, null, icons, (itemView, id) -> {
      if (id >= 1 && id <= buyOptions.size()) {
        buy(buyOptions.get(id - 1));
      }
      return true;
    });
  }

  private void loadMore () {
    if (loading) return;
    loading = true;
    tdlib.send(new TdApi.GetStarTransactions(new TdApi.MessageSenderUser(tdlib.myUserId()), "", null, nextOffset, PAGE_SIZE), (result, error) -> runOnUiThreadOptional(() -> {
      loading = false;
      firstPageLoaded = true;
      if (error != null) {
        UI.showError(error);
        checkReady();
        return;
      }
      loaded = true;
      if (result.starAmount != null) {
        balance = result.starAmount.starCount;
      }
      for (TdApi.StarTransaction transaction : result.transactions) {
        transactions.add(transaction);
      }
      nextOffset = result.nextOffset;
      buildCells();
      checkReady();
    }));
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101StarsBalance, R.drawable.baseline_star_24, R.string.Tgx101StarsBalance));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101StarsBuyHeader));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    hasMoreBuyOptions = false;
    for (TdApi.StarPaymentOption option : buyOptions) {
      if (option.isAdditional) {
        hasMoreBuyOptions = true;
        continue;
      }
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101StarsBuyOption, R.drawable.baseline_star_24, Tgx101Stars.formatStars(option.starCount)).setData(option));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    }
    if (hasMoreBuyOptions) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101StarsBuyMore, 0, R.string.Tgx101StarsBuyMore));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    }
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101StarsPremiumBot, R.drawable.baseline_premium_star_24, R.string.Tgx101StarsPremiumBot));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_openLink, R.drawable.baseline_open_in_browser_24, R.string.Tgx101StarsBuy));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101StarsBuyHint));

    if (loaded) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101StarsHistory));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      if (transactions.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.Tgx101StarsHistoryEmpty));
      }
      boolean first = true;
      for (TdApi.StarTransaction transaction : transactions) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        first = false;
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101StarsTransaction, 0, describe(transaction)).setData(transaction));
      }
      if (!StringUtils.isEmpty(nextOffset)) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101StarsMore, 0, R.string.Tgx101StarsMore).setTextColorId(ColorId.textNeutral));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    adapter.setItems(items, false);
  }

  /** "+50 ⭐ · Purchase · Bot name". Kinds come from the type name, so new TDLib types still read well. */
  private String describe (TdApi.StarTransaction transaction) {
    long count = transaction.starAmount != null ? transaction.starAmount.starCount : 0;
    StringBuilder b = new StringBuilder(count > 0 ? "+" : "").append(Tgx101Stars.formatStars(count));
    String kind = transaction.type != null ? transaction.type.getClass().getSimpleName() : "";
    int kindRes = kindOf(kind);
    if (kindRes != 0) {
      b.append(" · ").append(Lang.getString(kindRes));
    }
    String name = transaction.type != null ? partyName(transaction.type) : null;
    if (!StringUtils.isEmpty(name)) {
      b.append(" · ").append(name);
    }
    return b.toString();
  }

  private static int kindOf (String kind) {
    if (kind.contains("Deposit") || kind.contains("GiveawayDeposit")) return R.string.Tgx101StarsKindDeposit;
    if (kind.contains("Withdrawal")) return R.string.Tgx101StarsKindWithdrawal;
    if (kind.contains("Subscription")) return R.string.Tgx101StarsKindSubscription;
    if (kind.contains("Gift")) return R.string.Tgx101StarsKindGift;
    if (kind.contains("Reaction")) return R.string.Tgx101StarsKindReaction;
    if (kind.contains("PaidMedia")) return R.string.Tgx101StarsKindPaidMedia;
    if (kind.contains("Sale")) return R.string.Tgx101StarsKindSale;
    if (kind.contains("Purchase") || kind.contains("Invoice")) return R.string.Tgx101StarsKindPurchase;
    return 0;
  }

  private @Nullable String partyName (TdApi.StarTransactionType type) {
    for (Field field : type.getClass().getFields()) {
      try {
        Object value = field.get(type);
        String name = field.getName();
        if (value instanceof Long && (Long) value != 0) {
          if (name.endsWith("UserId") || name.equals("userId")) {
            return tdlib.cache().userName((Long) value);
          }
          if (name.endsWith("ChatId") || name.equals("chatId")) {
            return tdlib.chatTitle((Long) value);
          }
        } else if (value instanceof TdApi.MessageSender) {
          return tdlib.senderName((TdApi.MessageSender) value);
        }
      } catch (IllegalAccessException ignored) { }
    }
    return null;
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_openLink) {
      tdlib.ui().openUrl(this, Tgx101Stars.FRAGMENT_URL, null);
    } else if (id == R.id.btn_tgx101StarsBuyOption) {
      buy((TdApi.StarPaymentOption) ((ListItem) v.getTag()).getData());
    } else if (id == R.id.btn_tgx101StarsPremiumBot) {
      tdlib.ui().openUrl(this, "https://t.me/PremiumBot", null);
    } else if (id == R.id.btn_tgx101StarsBuyMore) {
      showAllBuyOptions();
    } else if (id == R.id.btn_tgx101StarsMore) {
      loadMore();
    }
  }
}
