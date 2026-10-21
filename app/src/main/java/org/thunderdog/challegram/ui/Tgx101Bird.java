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

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.Tgx101BirdView;

import java.util.ArrayList;
import java.util.List;

import tgx.td.ChatId;

/**
 * TGx101 (user 2026-10-21 11:50): what the bird ({@link Tgx101BirdView}) does. The ring by default: Storage, Proxy,
 * Accounts, Calls, Settings; at most 5, chosen in MagiX → Gestures. Tap — the side menu.
 */
public final class Tgx101Bird {
  private Tgx101Bird () { }

  public static final int ACTION_STORAGE = 0, ACTION_PROXY = 1, ACTION_ACCOUNTS = 2, ACTION_CALLS = 3, ACTION_SETTINGS = 4,
    ACTION_COMPOSE = 5, ACTION_SEARCH = 6, ACTION_NEXT_ACCOUNT = 7, ACTION_NOTE = 8;
  public static final int MAX = 5;

  /** the order of the bits in {@link Settings#tgx101BirdRing()} */
  public static final int[] TITLES = {
    R.string.Tgx101BirdStorage, R.string.Tgx101BirdProxy, R.string.Tgx101BirdAccounts, R.string.Tgx101BirdCalls, R.string.Tgx101BirdSettings,
    R.string.Tgx101BirdCompose, R.string.Tgx101BirdSearch, R.string.Tgx101BirdNextAccount, R.string.Tgx101BirdNote
  };
  private static final int[] ICONS = {
    R.drawable.baseline_bookmark_24, R.drawable.baseline_security_24, R.drawable.baseline_group_24, R.drawable.baseline_phone_24, R.drawable.baseline_settings_24,
    R.drawable.baseline_chat_bubble_24, R.drawable.baseline_search_24, R.drawable.baseline_swap_horiz_24, R.drawable.baseline_create_24
  };
  // the mockup's colours
  private static final int[] COLORS = {
    0xff5aa9e6, 0xff2f9e8f, 0xffa695e7, 0xff47c56b, 0xff6b7f95,
    0xff4fae4e, 0xff6b7f95, 0xffe17076, 0xfffaa774
  };

  /** on every chat list while the side menu is used (not the bottom capsule) and ✎ is hidden */
  public static boolean enabled () {
    Settings s = Settings.instance();
    return s.tgx101Bird() && !s.tgx101NavCapsule() && s.tgx101HideCompose();
  }

  public static Tgx101BirdView.Item[] items () {
    int mask = Settings.instance().tgx101BirdRing();
    List<Tgx101BirdView.Item> out = new ArrayList<>();
    for (int i = 0; i < TITLES.length && out.size() < MAX; i++) {
      if ((mask & (1 << i)) != 0) out.add(new Tgx101BirdView.Item(i, ICONS[i], COLORS[i], Lang.getString(TITLES[i])));
    }
    return out.toArray(new Tgx101BirdView.Item[0]);
  }

  /** made and put over a chat list's content */
  public static Tgx101BirdView create (ViewController<?> host, @Nullable Runnable search) {
    Tgx101BirdView bird = new Tgx101BirdView(host.context());
    bird.setItems(items());
    bird.setCallback(new Tgx101BirdView.Callback() {
      @Override
      public void onBirdTap () {
        BaseActivity context = host.context();
        if (context.getDrawer() != null && !context.getDrawer().isVisible()) {
          org.thunderdog.challegram.Tgx101Diag.mark("bird: tap → side menu");
          context.getDrawer().open();
        }
      }

      @Override
      public void onBirdAction (int action) {
        run(host, action, search);
      }

      @Override
      public void onBirdAccount (int accountId) {
        if (accountId == -1) {
          host.tdlib().ui().addAccount(host.context(), true, false);
          return;
        }
        if (accountId != host.tdlib().id()) {
          org.thunderdog.challegram.Tgx101Diag.mark("bird: switch account " + host.tdlib().id() + " → " + accountId);
          host.tdlib().context().changePreferredAccountId(accountId, TdlibManager.SWITCH_REASON_USER_CLICK);
        }
      }
    }, host.tdlib());
    bird.setActive(enabled());
    return bird;
  }

  /** the settings may have changed while away: shown or not, and the ring */
  public static void refresh (@Nullable Tgx101BirdView bird) {
    if (bird == null) return;
    bird.setActive(enabled());
    if (!bird.isRingOpen()) bird.setItems(items());
  }

  public static void run (ViewController<?> host, int action, @Nullable Runnable search) {
    if (host.isDestroyed()) return;
    Tdlib tdlib = host.tdlib();
    BaseActivity context = host.context();
    org.thunderdog.challegram.Tgx101Diag.mark("bird: action " + action);
    switch (action) {
      case ACTION_STORAGE:
      case ACTION_NOTE: {
        if (org.thunderdog.challegram.data.Tgx101SavedStore.get(tdlib).storageOn) {
          if (action == ACTION_NOTE) Tgx101SavedHomeController.openForNote(context, tdlib);
          else Tgx101SavedHomeController.open(context, tdlib);
        } else {
          long myUserId = tdlib.myUserId();
          if (myUserId != 0) tdlib.ui().openChat(host, ChatId.fromUserId(myUserId), null);
        }
        break;
      }
      case ACTION_PROXY:
        tdlib.ui().openProxySettings(host, false);
        break;
      case ACTION_CALLS:
        host.navigateTo(new CallListController(context, tdlib));
        break;
      case ACTION_SETTINGS:
        host.navigateTo(new SettingsController(context, tdlib));
        break;
      case ACTION_COMPOSE: {
        ContactsController c = new ContactsController(context, tdlib);
        c.initWithMode(ContactsController.MODE_NEW_CHAT);
        host.navigateTo(c);
        break;
      }
      case ACTION_SEARCH:
        if (search != null) search.run();
        break;
      case ACTION_NEXT_ACCOUNT: {
        List<TdlibAccount> accounts = TdlibManager.instance().getActiveAccounts();
        if (accounts.size() < 2) break;
        int at = 0;
        for (int i = 0; i < accounts.size(); i++) if (accounts.get(i).id == tdlib.id()) at = i;
        TdlibAccount next = accounts.get((at + 1) % accounts.size());
        next.context().changePreferredAccountId(next.id, TdlibManager.SWITCH_REASON_USER_CLICK);
        break;
      }
    }
  }
}
