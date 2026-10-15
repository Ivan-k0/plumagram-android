package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.HashSet;
import java.util.Set;

/**
 * TGx101 (user 2026-10-06 23:56, like AyuGram): messages from blocked users and chats are not shown anywhere
 * (MagiX → «Chat list» → «Hide messages from blocked»). The blocked list is fetched from Telegram and kept in memory.
 */
public final class Tgx101BlockedSenders {
  // Per account (review 2026-10-15): one shared set showed account A's blocks in account B, and a block in one
  // account's update landed in the other's list
  private static final android.util.SparseArray<Set<Long>> idsByAccount = new android.util.SparseArray<>();
  private static final java.util.Map<Integer, Long> loadedAt = new java.util.HashMap<>(); // not SparseLongArray: API 18, the Android 4 build is 16

  private static Set<Long> ids (int accountId) {
    Set<Long> set = idsByAccount.get(accountId);
    if (set == null) {
      set = new HashSet<>();
      idsByAccount.put(accountId, set);
    }
    return set;
  }

  private Tgx101BlockedSenders () { }

  public static boolean isEnabled () {
    return Settings.instance().tgx101HideBlocked();
  }

  /** Called when a chat opens and when the setting is turned on: reloads at most once a minute */
  public static void refresh (Tdlib tdlib, boolean force) {
    if (!isEnabled()) return;
    long now = android.os.SystemClock.uptimeMillis();
    synchronized (idsByAccount) {
      Long at = loadedAt.get(tdlib.id());
      if (!force && at != null && now - at < 60_000L) return;
      loadedAt.put(tdlib.id(), now);
    }
    load(tdlib, 0, new HashSet<>());
  }

  private static void load (Tdlib tdlib, int offset, Set<Long> collected) {
    tdlib.send(new TdApi.GetBlockedMessageSenders(new TdApi.BlockListMain(), offset, 100), (result, error) -> {
      if (result == null) return;
      for (TdApi.MessageSender sender : result.senders) {
        collected.add(idOf(sender));
      }
      if (result.senders.length == 100 && offset + 100 < result.totalCount) {
        load(tdlib, offset + 100, collected);
      } else {
        synchronized (idsByAccount) {
          Set<Long> ids = ids(tdlib.id());
          ids.clear();
          ids.addAll(collected);
        }
        org.thunderdog.challegram.Tgx101Diag.mark("blocked senders: " + collected.size());
      }
    });
  }

  /** A block or unblock right now (UpdateChatBlockList) */
  public static void onBlockListChanged (Tdlib tdlib, long chatId, TdApi.BlockList blockList) {
    long userId = tgx.td.ChatId.toUserId(chatId); long id = userId != 0 ? userId : chatId;
    synchronized (idsByAccount) {
      Set<Long> ids = ids(tdlib.id());
      if (blockList instanceof TdApi.BlockListMain) ids.add(id); else ids.remove(id);
    }
  }

  private static long idOf (TdApi.MessageSender sender) {
    return sender instanceof TdApi.MessageSenderUser ? ((TdApi.MessageSenderUser) sender).userId : ((TdApi.MessageSenderChat) sender).chatId;
  }

  public static boolean isBlocked (Tdlib tdlib, TdApi.Message msg) {
    if (msg == null || msg.isOutgoing || msg.senderId == null || !isEnabled()) return false;
    synchronized (idsByAccount) {
      Set<Long> ids = idsByAccount.get(tdlib.id());
      return ids != null && !ids.isEmpty() && ids.contains(idOf(msg.senderId));
    }
  }
}
