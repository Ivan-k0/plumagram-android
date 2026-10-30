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

import org.drinkless.tdlib.TdApi;
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
 * Accounts, Calls, Settings; at most 5, chosen in MagiX → Gestures (the side menu and night mode too). Only a hold opens it — a tap does nothing (user 2026-10-21).
 */
public final class Tgx101Bird {
  private Tgx101Bird () { }

  public static final int ACTION_STORAGE = 0, ACTION_PROXY = 1, ACTION_ACCOUNTS = 2, ACTION_CALLS = 3, ACTION_SETTINGS = 4,
    ACTION_COMPOSE = 5, ACTION_SEARCH = 6, ACTION_NEXT_ACCOUNT = 7, ACTION_NOTE = 8, ACTION_MENU = 9, ACTION_NIGHT = 10, ACTION_PEEK = 11, ACTION_GAZETA = 12;
  public static final int MAX = 7; // (user 2026-10-22 «7 возможных кнопок вместо 5, в два ряда — 4 и 3»)

  /** the order of the bits in {@link Settings#tgx101BirdRing()} */
  public static final int[] TITLES = {
    R.string.Tgx101BirdStorage, R.string.Tgx101BirdProxy, R.string.Tgx101BirdAccounts, R.string.Tgx101BirdCalls, R.string.Tgx101BirdSettings,
    R.string.Tgx101BirdCompose, R.string.Tgx101BirdSearch, R.string.Tgx101BirdNextAccount, R.string.Tgx101BirdNote,
    R.string.Tgx101BirdMenu, R.string.Tgx101BirdNight, R.string.Tgx101Peek, R.string.Tgx101Gazeta
  };
  private static final int[] ICONS = {
    R.drawable.tgx101_storage_tiles_24, R.drawable.baseline_security_24, R.drawable.baseline_group_24, R.drawable.baseline_phone_24, R.drawable.baseline_settings_24,
    R.drawable.baseline_chat_bubble_24, R.drawable.baseline_search_24, R.drawable.baseline_swap_horiz_24, R.drawable.baseline_create_24,
    R.drawable.tgx101_menu_24, R.drawable.baseline_brightness_2_24, R.drawable.baseline_visibility_24, R.drawable.tgx101_gazeta_24
  };
  // one colour per action, none alike (user 2026-10-22 «нужно больше цветов, попадаются одинаковые»)
  private static final int[] COLORS = {
    0xff3d9be9 /* storage: blue */, 0xff26a69a /* proxy: teal */, 0xff9c6ade /* accounts: purple */, 0xff43a047 /* calls: green */,
    0xff78909c /* settings: grey */, 0xff00acc1 /* compose: cyan */, 0xffafb42b /* search: lime */, 0xffe57373 /* next account: red */,
    0xffffa726 /* note: orange */, 0xff8d6e63 /* menu: brown */, 0xff3f51b5 /* night: indigo */, 0xffd81b60 /* peek: crimson */,
    0xffc49a45 /* gazeta: newsprint yellow */
  };

  /** on every chat list while the side menu is used (not the bottom capsule) and ✎ is hidden */
  public static boolean enabled () {
    Settings s = Settings.instance();
    return s.tgx101Bird() && !s.tgx101NavCapsule() && s.tgx101HideCompose();
  }

  /** over a chat (MagiX → Features → Bird → «In chats»): whatever the side menu / capsule */
  public static boolean enabledInChats () {
    Settings s = Settings.instance();
    return s.tgx101Bird() && s.tgx101BirdInChats();
  }

  public static Tgx101BirdView.Item[] items () {
    int mask = Settings.instance().tgx101BirdRing();
    List<Tgx101BirdView.Item> out = new ArrayList<>();
    for (int i = 0; i < TITLES.length && out.size() < MAX; i++) {
      if ((mask & (1 << i)) == 0) continue;
      if (i == ACTION_NIGHT) {
        // (user 2026-10-21): what it will turn to — the moon while it's light, the sun while it's dark
        boolean dark = org.thunderdog.challegram.theme.Theme.isDark();
        out.add(new Tgx101BirdView.Item(i, dark ? R.drawable.tgx101_sun_24 : R.drawable.baseline_brightness_2_24, dark ? 0xffe65100 /* day: darker, more orange than the note (user 2026-10-28) */ : COLORS[i],
          Lang.getString(dark ? R.string.Tgx101BirdDay : R.string.Tgx101BirdNightOn)));
        continue;
      }
      if (i == ACTION_PEEK) {
        // «Не подглядывай!» (user 2026-10-22): the eye crossed out while it's on
        out.add(new Tgx101BirdView.Item(i, org.thunderdog.challegram.data.Tgx101Peek.isOn() ? R.drawable.baseline_eye_off_24 : R.drawable.baseline_visibility_24, COLORS[i], Lang.getString(TITLES[i])));
        continue;
      }
      out.add(new Tgx101BirdView.Item(i, ICONS[i], COLORS[i], Lang.getString(TITLES[i])));
    }
    return out.toArray(new Tgx101BirdView.Item[0]);
  }

  // TGx101 (user 2026-10-26 «птичка не должна перезапускаться, мигать при переходах — вкопана, как системная кнопка»):
  // one bird per window, over the navigation and under the side menu; the screen on top only says whether it is there
  // (chat lists, and chats when «In chats» is on) and is the one its actions act on
  private static final java.util.WeakHashMap<BaseActivity, Tgx101BirdView> birds = new java.util.WeakHashMap<>();
  private static final java.util.WeakHashMap<BaseActivity, java.lang.ref.WeakReference<ViewController<?>>> hosts = new java.util.WeakHashMap<>();
  private static final java.util.WeakHashMap<ViewController<?>, Runnable> searches = new java.util.WeakHashMap<>();

  /** put once into the window's content, right above the navigation */
  public static void install (BaseActivity context, android.view.ViewGroup content, android.view.View navigation) {
    try {
      installImpl(context, content, navigation);
    } catch (Throwable t) {
      org.thunderdog.challegram.Tgx101Diag.mark("bird: install failed " + t);
    }
  }

  private static void installImpl (BaseActivity context, android.view.ViewGroup content, android.view.View navigation) {
    Tgx101BirdView bird = new Tgx101BirdView(context);
    bird.setItems(items());
    bird.setActive(false);
    bird.setLayoutParams(new android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
    content.addView(bird, content.indexOfChild(navigation) + 1);
    birds.put(context, bird);
  }

  /** what «Search» in the ring opens on this screen */
  public static void setSearch (ViewController<?> host, Runnable search) {
    searches.put(host, search);
  }

  private static @Nullable ViewController<?> host (BaseActivity context) {
    java.lang.ref.WeakReference<ViewController<?>> ref = hosts.get(context);
    ViewController<?> host = ref != null ? ref.get() : null;
    return host != null && !host.isDestroyed() ? host : null;
  }

  private static boolean wants (ViewController<?> c, boolean leavingSearch) {
    if (c.inSearchMode() && !leavingSearch) return false;
    if (c instanceof MainController) return enabled();
    if (c instanceof ChatsController) return ((ChatsController) c).tgx101IsOwnList() && enabled();
    if (c instanceof MessagesController) return ((MessagesController) c).tgx101WantsBird();
    if (c instanceof Tgx101GazetaController) return Tgx101Gazeta.bird() && Settings.instance().tgx101Bird(); // (user 2026-10-27) if switched on in its settings
    return false;
  }

  /** a screen of the window's stack is about to show or got the focus: the bird stays, goes or comes */
  public static void sync (ViewController<?> c) {
    // called from every screen's focus: whatever goes wrong here must not take the screen down (release safety, 26.10)
    try {
      syncImpl(c, false);
    } catch (Throwable t) {
      org.thunderdog.challegram.Tgx101Diag.mark("bird: sync failed " + t);
    }
  }

  /** (user 2026-10-27 00:06 «из поиска назад — птичка появляется не сразу»): back as the search starts to close */
  public static void showLeavingSearch (ViewController<?> c) {
    try {
      syncImpl(c, true);
    } catch (Throwable t) {
      org.thunderdog.challegram.Tgx101Diag.mark("bird: sync failed " + t);
    }
  }

  private static void syncImpl (ViewController<?> c, boolean leavingSearch) {
    BaseActivity context = c.context();
    Tgx101BirdView bird = birds.get(context);
    if (bird == null || context.navigation() == null || context.navigation().getStack().indexOf(c) < 0) return;
    if (!wants(c, leavingSearch)) {
      bird.setActive(false);
      return;
    }
    ViewController<?> old = host(context);
    hosts.put(context, new java.lang.ref.WeakReference<>(c));
    if (old != c || !bird.isActive()) {
      bird.setCallback(callback(context), c.tdlib());
    }
    if (!bird.isRingOpen()) bird.setItems(items());
    bird.setActive(true);
  }

  /** the screen's search took the place: no bird */
  public static void hideFor (ViewController<?> c) {
    Tgx101BirdView bird = birds.get(c.context());
    if (bird != null && host(c.context()) == c) bird.setActive(false);
  }

  /** back pressed while the ring is open */
  public static boolean closeRing (BaseActivity context, boolean commit) {
    Tgx101BirdView bird = birds.get(context);
    if (bird == null || !bird.isActive() || !bird.isRingOpen()) return false;
    if (commit) bird.onBackPressed();
    return true;
  }

  private static Tgx101BirdView.Callback callback (BaseActivity context) {
    return new Tgx101BirdView.Callback() {
      @Override
      public Tgx101BirdView.Item[] birdItems () {
        return items(); // made at the moment the ring opens: the theme, the settings as they are now
      }

      @Override
      public void onBirdAction (int action) {
        ViewController<?> host = host(context);
        if (host != null) run(host, action, searches.get(host));
      }

      @Override
      public boolean birdHasDoubleTap () {
        return Settings.instance().tgx101BirdDoubleTap() != DOUBLE_TAP_NONE;
      }

      @Override
      public void onBirdDoubleTap () {
        ViewController<?> host = host(context);
        if (host != null) doubleTap(host);
      }

      @Override
      public void onBirdAccount (int accountId) {
        ViewController<?> host = host(context);
        if (host == null) return;
        if (accountId == -1) {
          host.tdlib().ui().addAccount(host.context(), true, false);
          return;
        }
        if (accountId != host.tdlib().id()) {
          org.thunderdog.challegram.Tgx101Diag.mark("bird: switch account " + host.tdlib().id() + " → " + accountId);
          host.tdlib().context().changePreferredAccountId(accountId, TdlibManager.SWITCH_REASON_USER_CLICK);
        }
      }
    };
  }

  // TGx101 (user 2026-10-26): what two taps on the bird do
  public static final int DOUBLE_TAP_NONE = 0, DOUBLE_TAP_LAST_NOTIFICATION = 1, DOUBLE_TAP_NEXT_ACCOUNT = 2, DOUBLE_TAP_PROXY = 3, DOUBLE_TAP_NOTE = 4, DOUBLE_TAP_GAZETA = 5;
  public static final int[] DOUBLE_TAP_TITLES = {
    R.string.Tgx101BirdTapNone, R.string.Tgx101BirdTapLastChat, R.string.Tgx101BirdTapNextAccount, R.string.Tgx101BirdTapProxy, R.string.Tgx101BirdTapNote, R.string.Tgx101Gazeta
  };

  public static void doubleTap (ViewController<?> host) {
    if (host.isDestroyed()) return;
    int mode = Settings.instance().tgx101BirdDoubleTap();
    org.thunderdog.challegram.Tgx101Diag.mark("bird: double tap " + mode);
    switch (mode) {
      case DOUBLE_TAP_LAST_NOTIFICATION:
        if (!tgx101ReturnBack(host)) openLastNotification(host);
        break;
      case DOUBLE_TAP_NEXT_ACCOUNT:
        if (TdlibManager.instance().getActiveAccounts().size() < 2) {
          org.thunderdog.challegram.tool.UI.showToast(R.string.Tgx101BirdTapOneAccount, android.widget.Toast.LENGTH_SHORT);
        } else {
          run(host, ACTION_NEXT_ACCOUNT, null);
        }
        break;
      case DOUBLE_TAP_PROXY:
        run(host, ACTION_PROXY, null);
        break;
      case DOUBLE_TAP_NOTE:
        run(host, ACTION_NOTE, null);
        break;
      case DOUBLE_TAP_GAZETA:
        // (user 2026-10-28 09:52 «двойной тап на птичку — и в газету, и из газеты»): into «Газета», and from it back
        // to the screen it was opened over
        if (host instanceof Tgx101GazetaController) host.navigateBack();
        else host.navigateTo(new Tgx101GazetaController(host.context(), host.tdlib()));
        break;
    }
  }

  /**
   * (user 2026-10-26 «последнее уведомление: в личных чатах, в группах, где меня отметили — ответ, цитата»): the newest of
   * — an incoming message in a private or secret chat (not mine, not a bot's), an unread mention / reply / quote of me
   * in a group, an unread reaction to my message; a group or a reaction opens at that message
   */
  // (user 2026-10-27 «второе двойное нажатие возвращает туда, откуда пришёл по первому»): where the first double tap was
  // made and which chat it opened; a second one in that chat goes back there — the screen below, or that chat (its
  // place in the list is kept by the chat itself)
  private static java.lang.ref.WeakReference<ViewController<?>> tgx101ReturnTo;
  private static long tgx101ReturnChatId, tgx101JumpedChatId;

  // (user's recording 2026-10-30 15:00: chat → double tap back to «Газета» → double tap again opened the same chat at the
  // bottom): the chat just left by a double tap is reopened by the next one at the place it was left, unless something
  // new came there meanwhile
  private static long tgx101LeftChatId, tgx101LeftLastMessageId;

  private static void tgx101NoteLeft (ViewController<?> host) {
    if (!(host instanceof MessagesController)) return;
    long chatId = ((MessagesController) host).getChatId();
    TdApi.Chat chat = host.tdlib().chat(chatId);
    tgx101LeftChatId = chatId;
    tgx101LeftLastMessageId = chat != null && chat.lastMessage != null ? chat.lastMessage.id : 0;
  }

  /** the open parameters keeping the place a chat was left at by a double tap, or null */
  private static @Nullable org.thunderdog.challegram.telegram.TdlibUi.ChatOpenParameters tgx101KeepPlace (Tdlib tdlib, long chatId, @Nullable org.thunderdog.challegram.telegram.TdlibUi.ChatOpenParameters params) {
    if (chatId == 0 || chatId != tgx101LeftChatId) return params;
    TdApi.Chat chat = tdlib.chat(chatId);
    long last = chat != null && chat.lastMessage != null ? chat.lastMessage.id : 0;
    tgx101LeftChatId = 0;
    if (last != tgx101LeftLastMessageId) return params; // something new came: as before, at it
    org.thunderdog.challegram.unsorted.Settings.SavedMessageId saved = org.thunderdog.challegram.unsorted.Settings.instance().getScrollMessageId(tdlib.id(), chatId, null);
    if (saved == null || saved.id.getMessageId() == 0) return params;
    org.thunderdog.challegram.Tgx101Diag.mark("bird: the chat left by a double tap opens at its place");
    return (params != null ? params : new org.thunderdog.challegram.telegram.TdlibUi.ChatOpenParameters()).highlightMessage(org.thunderdog.challegram.component.chat.MessagesManager.HIGHLIGHT_MODE_POSITION_RESTORE, saved.id);
  }

  private static boolean tgx101ReturnBack (ViewController<?> host) {
    ViewController<?> back = tgx101ReturnTo != null ? tgx101ReturnTo.get() : null;
    long jumped = tgx101JumpedChatId, returnChat = tgx101ReturnChatId;
    if (jumped == 0 || !(host instanceof MessagesController) || ((MessagesController) host).getChatId() != jumped) {
      return false;
    }
    tgx101ReturnTo = null;
    tgx101JumpedChatId = tgx101ReturnChatId = 0;
    org.thunderdog.challegram.navigation.NavigationController navigation = host.context().navigation();
    if (navigation == null) return false;
    int at = back != null && navigation != null ? navigation.getStack().indexOf(back) : -1;
    org.thunderdog.challegram.Tgx101Diag.mark("bird: double tap back (" + (at == navigation.getStackSize() - 2 ? "the screen below" : returnChat != 0 ? "chat " + returnChat : "nothing") + ")");
    if (at >= 0 && at == navigation.getStackSize() - 2) {
      tgx101NoteLeft(host);
      host.navigateBack();
      return true;
    }
    if (returnChat != 0) {
      // (user 2026-10-30 11:52 «возвращает вниз чата, а должно — на то же место»): the chat is reopened at the place it
      // was left at (saved when leaving), whatever MagiX «Remember the place in a chat» says
      org.thunderdog.challegram.unsorted.Settings.SavedMessageId saved = org.thunderdog.challegram.unsorted.Settings.instance().getScrollMessageId(host.tdlib().id(), returnChat, null);
      org.thunderdog.challegram.telegram.TdlibUi.ChatOpenParameters params = null;
      if (saved != null && saved.id.getMessageId() != 0) {
        params = new org.thunderdog.challegram.telegram.TdlibUi.ChatOpenParameters().highlightMessage(org.thunderdog.challegram.component.chat.MessagesManager.HIGHLIGHT_MODE_POSITION_RESTORE, saved.id);
      }
      host.tdlib().ui().openChat(host, returnChat, params);
      return true;
    }
    if (at >= 0) {
      host.navigateBack();
      return true;
    }
    return false;
  }

  private static void tgx101RememberJump (ViewController<?> from, long toChatId) {
    tgx101ReturnTo = new java.lang.ref.WeakReference<>(from);
    tgx101ReturnChatId = from instanceof MessagesController ? ((MessagesController) from).getChatId() : 0;
    tgx101JumpedChatId = toChatId;
  }

  private static void openLastNotification (ViewController<?> host) {
    Tdlib tdlib = host.tdlib();
    tdlib.send(new TdApi.GetChats(new TdApi.ChatListMain(), 100), (chats, error) -> {
      if (chats == null) return;
      final long[] best = new long[3]; // date, chat id, message id
      List<TdApi.SearchChatMessages> searches = new ArrayList<>();
      for (long chatId : chats.chatIds) {
        TdApi.Chat chat = tdlib.chat(chatId);
        if (chat == null) continue;
        boolean personal = chat.type instanceof TdApi.ChatTypePrivate || chat.type instanceof TdApi.ChatTypeSecret;
        // (user 2026-10-27 09:49 «не должно учитывать входящие или исходящие звонки»)
        if (personal && chat.lastMessage != null && !chat.lastMessage.isOutgoing && !tdlib.isSelfChat(chat) && !tdlib.isBotChat(chat)
          && !(chat.lastMessage.content instanceof TdApi.MessageCall) && !(chat.lastMessage.content instanceof TdApi.MessageGroupCall)
          && chat.lastMessage.date > best[0]) {
          best[0] = chat.lastMessage.date;
          best[1] = chatId;
          best[2] = 0;
        }
        if (chat.unreadMentionCount > 0) {
          searches.add(new TdApi.SearchChatMessages(chatId, null, "", null, 0, 0, 1, new TdApi.SearchMessagesFilterUnreadMention()));
        }
        if (chat.unreadReactionCount > 0) {
          searches.add(new TdApi.SearchChatMessages(chatId, null, "", null, 0, 0, 1, new TdApi.SearchMessagesFilterUnreadReaction()));
        }
      }
      final int[] left = {searches.size() + 1};
      Runnable done = () -> {
        synchronized (best) {
          if (--left[0] > 0) return;
        }
        final long chatId = best[1], messageId = best[2];
        tdlib.ui().post(() -> {
          if (host.isDestroyed()) return;
          if (chatId == 0) {
            org.thunderdog.challegram.tool.UI.showToast(R.string.Tgx101BirdTapNoChat, android.widget.Toast.LENGTH_SHORT);
          } else {
            if (host instanceof MessagesController && ((MessagesController) host).getChatId() == chatId) {
              tgx101ReturnTo = null; // already here: nothing to go back to
              tgx101JumpedChatId = 0;
            } else {
              tgx101RememberJump(host, chatId);
            }
            // (log 11:08: from «Газета» the chat replaced it in the stack — the second double tap had nowhere to go back
            // to): over the feed the chat keeps it underneath
            boolean keep = host instanceof Tgx101GazetaController;
            org.thunderdog.challegram.telegram.TdlibUi.ChatOpenParameters params = keep ? new org.thunderdog.challegram.telegram.TdlibUi.ChatOpenParameters().keepStack() : null;
            org.thunderdog.challegram.telegram.TdlibUi.ChatOpenParameters kept = tgx101KeepPlace(tdlib, chatId, params);
            if (kept != params) {
              tdlib.ui().openChat(host, chatId, kept);
            } else if (messageId != 0) {
              if (keep) tdlib.ui().openChat(host, chatId, params.highlightMessage(new tgx.td.MessageId(chatId, messageId)).ensureHighlightAvailable());
              else tdlib.ui().openMessage(host, chatId, new tgx.td.MessageId(chatId, messageId), null);
            } else {
              tdlib.ui().openChat(host, chatId, params);
            }
          }
        });
      };
      for (TdApi.SearchChatMessages search : searches) {
        tdlib.send(search, (found, err) -> {
          if (found != null && found.messages.length > 0 && found.messages[0] != null) {
            TdApi.Message message = found.messages[0];
            synchronized (best) {
              if (message.date > best[0]) {
                best[0] = message.date;
                best[1] = message.chatId;
                best[2] = message.id;
              }
            }
          }
          done.run();
        });
      }
      done.run();
    });
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
      case ACTION_MENU:
        if (context.getDrawer() != null && !context.getDrawer().isVisible()) context.getDrawer().open();
        break;
      case ACTION_NIGHT:
        org.thunderdog.challegram.theme.ThemeManager.instance().toggleNightMode();
        break;
      case ACTION_PEEK:
        Tgx101PeekController.toggleWithToast();
        break;
      case ACTION_GAZETA:
        host.navigateTo(new Tgx101GazetaController(context, tdlib));
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
