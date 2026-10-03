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
package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101: chats pinned on this phone only, beyond Telegram's pinned-chat limit. They sit right under the chats pinned
 * in Telegram (their order in the list is replaced locally), per account and chat list (main, archive, folders).
 */
public final class Tgx101LocalPins {
  private Tgx101LocalPins () { }

  // Telegram's pinned chats have orders from (2147000000 << 32) up; regular chats are below (date << 32)
  private static final long LOCAL_BASE = (2146999999L << 32);

  private static String key (Tdlib tdlib, TdApi.ChatList chatList) {
    return "tgx101_local_pins_" + tdlib.id() + "_" + TD.makeChatListKey(chatList);
  }

  public static List<Long> ids (Tdlib tdlib, TdApi.ChatList chatList) {
    List<Long> ids = new ArrayList<>();
    String value = Settings.instance().pmc().getString(key(tdlib, chatList), "");
    if (value != null && !value.isEmpty()) {
      for (String part : value.split(",")) {
        try {
          ids.add(Long.parseLong(part));
        } catch (NumberFormatException ignored) { }
      }
    }
    return ids;
  }

  public static boolean isPinned (Tdlib tdlib, TdApi.ChatList chatList, long chatId) {
    return chatList != null && ids(tdlib, chatList).contains(chatId);
  }

  public static void setPinned (Tdlib tdlib, TdApi.ChatList chatList, long chatId, boolean pinned) {
    List<Long> ids = ids(tdlib, chatList);
    ids.remove(chatId);
    if (pinned) ids.add(chatId);
    StringBuilder b = new StringBuilder();
    for (long id : ids) {
      if (b.length() > 0) b.append(',');
      b.append(id);
    }
    Settings.instance().pmc().putString(key(tdlib, chatList), b.toString());
    tdlib.runOnTdlibThread(() -> tdlib.chatList(chatList).tgx101RefreshLocalPin(chatId));
  }

  /** The order the chat list sorts by: Telegram's one, or the local pin's place right under the pinned chats */
  static long effectiveOrder (Tdlib tdlib, TdApi.ChatList chatList, long chatId, TdApi.ChatPosition position) {
    if (position == null || position.order == 0 || position.isPinned) {
      return position != null ? position.order : 0;
    }
    int index = ids(tdlib, chatList).indexOf(chatId);
    return index == -1 ? position.order : LOCAL_BASE - index;
  }
}
