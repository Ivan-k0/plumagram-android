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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.leveldb.LevelDB;

/**
 * TGx101: order of the Share sheet. Saved Messages first, then two rows of private chats you
 * share with most (then chat with most), then any chats you share with most, then the chat list.
 * "Share with most" = this app's own count of shares per chat plus Telegram's top forward chats.
 */
final class Tgx101ShareOrder {
  private Tgx101ShareOrder () { }

  private static final int TOP_LIMIT = 30;
  private static final int COUNTED_LIMIT = 60;

  private static String countKey (Tdlib tdlib, long chatId) {
    return "tgx101_share_" + tdlib.id() + "_" + chatId;
  }

  private static String idsKey (Tdlib tdlib) {
    return "tgx101_share_ids_" + tdlib.id();
  }

  /** Counts a share sent through the Share sheet. */
  static void recordShare (Tdlib tdlib, long chatId) {
    LevelDB pmc = Settings.instance().pmc();
    pmc.putInt(countKey(tdlib, chatId), pmc.getInt(countKey(tdlib, chatId), 0) + 1);
    List<Long> ids = countedIds(tdlib);
    ids.remove(chatId);
    ids.add(0, chatId);
    while (ids.size() > COUNTED_LIMIT) {
      ids.remove(ids.size() - 1);
    }
    StringBuilder b = new StringBuilder();
    for (long id : ids) {
      if (b.length() > 0) b.append(',');
      b.append(id);
    }
    pmc.putString(idsKey(tdlib), b.toString());
  }

  private static List<Long> countedIds (Tdlib tdlib) {
    List<Long> ids = new ArrayList<>();
    String value = Settings.instance().pmc().getString(idsKey(tdlib), "");
    if (value != null && !value.isEmpty()) {
      for (String part : value.split(",")) {
        try {
          ids.add(Long.parseLong(part));
        } catch (NumberFormatException ignored) { }
      }
    }
    return ids;
  }

  /** Chats in priority order; loaded chats only. Calls back on the UI thread. */
  static void load (Tdlib tdlib, int privateSlots, RunnableList callback) {
    long[][] top = new long[2][];
    AtomicInteger remaining = new AtomicInteger(2);
    Runnable done = () -> {
      if (remaining.decrementAndGet() == 0) {
        List<Long> order = build(tdlib, privateSlots, top[0], top[1]);
        ensureChats(tdlib, order, () -> UI.post(() -> callback.run(order)));
      }
    };
    tdlib.send(new TdApi.GetTopChats(new TdApi.TopChatCategoryForwardChats(), TOP_LIMIT), (chats, error) -> {
      top[0] = chats != null ? chats.chatIds : new long[0];
      done.run();
    });
    tdlib.send(new TdApi.GetTopChats(new TdApi.TopChatCategoryUsers(), TOP_LIMIT), (chats, error) -> {
      top[1] = chats != null ? chats.chatIds : new long[0];
      done.run();
    });
  }

  interface RunnableList {
    void run (List<Long> chatIds);
  }

  private static List<Long> build (Tdlib tdlib, int privateSlots, long[] forwardTop, long[] usersTop) {
    // Own share counts first (most shared), then Telegram's top forward chats
    List<Long> counted = countedIds(tdlib);
    LevelDB pmc = Settings.instance().pmc();
    List<long[]> scored = new ArrayList<>();
    for (long id : counted) {
      scored.add(new long[] {id, pmc.getInt(countKey(tdlib, id), 0)});
    }
    Collections.sort(scored, (a, b) -> Long.compare(b[1], a[1]));
    Set<Long> shareRank = new LinkedHashSet<>();
    for (long[] item : scored) shareRank.add(item[0]);
    for (long id : forwardTop) shareRank.add(id);

    long selfChatId = tdlib.selfChatId();
    Set<Long> result = new LinkedHashSet<>();
    // Two rows of private chats: shared with most, then chatted with most
    for (long id : shareRank) {
      if (result.size() >= privateSlots) break;
      if (id != selfChatId && tdlib.isUserChat(id)) result.add(id);
    }
    for (long id : usersTop) {
      if (result.size() >= privateSlots) break;
      if (id != selfChatId && tdlib.isUserChat(id)) result.add(id);
    }
    // Then any chats shared with most
    for (long id : shareRank) {
      if (id != selfChatId) result.add(id);
    }
    return new ArrayList<>(result);
  }

  private static void ensureChats (Tdlib tdlib, List<Long> chatIds, Runnable after) {
    List<Long> missing = new ArrayList<>();
    for (long id : chatIds) {
      if (tdlib.chat(id) == null) missing.add(id);
    }
    if (missing.isEmpty()) {
      after.run();
      return;
    }
    AtomicInteger remaining = new AtomicInteger(missing.size());
    for (long id : missing) {
      tdlib.send(new TdApi.GetChat(id), (chat, error) -> {
        if (remaining.decrementAndGet() == 0) after.run();
      });
    }
  }

  static @Nullable TdApi.Chat chat (Tdlib tdlib, long chatId) {
    return tdlib.chat(chatId);
  }
}
