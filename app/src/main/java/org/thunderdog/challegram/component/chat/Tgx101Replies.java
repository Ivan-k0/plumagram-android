package org.thunderdog.challegram.component.chat;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;

import tgx.td.Td;

/**
 * TGx101 (user 2026-11-16, 4PDA wish «В группах нужно дать возможность просматривать все ответы на конкретное
 * сообщение. Поместить этот пункт в «Ещё»», mockup «Ответы на сообщение»): every reply to one message of a group, shown
 * in the chat's own search — the counter, the arrows, the list, the sender and date buttons as for a searched word.
 * A group has no server request for a message's replies (only channel comments and forum topics do), so the history is
 * read on from the message, page by page, and the replies picked out here.
 */
public final class Tgx101Replies {
  /** Enough for weeks of a busy group; a reply further on than this isn't looked for. */
  private static final int SCAN_LIMIT = 20000;
  private static final int PAGE = 100;

  public final long chatId;
  public final long messageId;
  /** newest first, as search results go */
  private TdApi.Message[] found = new TdApi.Message[0];
  private boolean done;
  private boolean cut;

  public interface Progress {
    void onProgress (int found, int scanned);
    void onDone (Tgx101Replies replies);
  }

  private Tgx101Replies (long chatId, long messageId) {
    this.chatId = chatId;
    this.messageId = messageId;
  }

  public boolean isDone () {
    return done;
  }

  /** the reading stopped at {@link #SCAN_LIMIT} messages: later replies may be missing */
  public boolean wasCut () {
    return cut;
  }

  public int count () {
    return found.length;
  }

  public static boolean isReplyTo (TdApi.Message message, long chatId, long messageId) {
    if (message == null || !(message.replyTo instanceof TdApi.MessageReplyToMessage)) return false;
    TdApi.MessageReplyToMessage reply = (TdApi.MessageReplyToMessage) message.replyTo;
    return reply.messageId == messageId && (reply.chatId == 0 || reply.chatId == chatId);
  }

  /** Reads the history on from the message; progress and the end come on the UI thread. */
  public static Tgx101Replies collect (Tdlib tdlib, long chatId, long messageId, Progress progress) {
    Tgx101Replies replies = new Tgx101Replies(chatId, messageId);
    final ArrayList<TdApi.Message> list = new ArrayList<>();
    final int[] scanned = new int[1];
    final long[] from = {messageId};
    final Client.ResultHandler[] page = new Client.ResultHandler[1];
    page[0] = result -> {
      long newest = from[0];
      int newer = 0;
      if (result instanceof TdApi.Messages) {
        for (TdApi.Message message : ((TdApi.Messages) result).messages) {
          if (message == null || message.id <= from[0]) continue; // the page also holds the start and older ones
          newer++;
          newest = Math.max(newest, message.id);
          if (isReplyTo(message, chatId, messageId)) list.add(message);
        }
      }
      scanned[0] += newer;
      boolean end = newer == 0 || !(result instanceof TdApi.Messages);
      boolean limit = scanned[0] >= SCAN_LIMIT;
      final int foundNow = list.size(), scannedNow = scanned[0];
      if (end || limit) {
        java.util.Collections.sort(list, (a, b) -> Long.compare(b.id, a.id));
        replies.found = list.toArray(new TdApi.Message[0]);
        replies.cut = limit && !end;
        replies.done = true;
        org.thunderdog.challegram.Tgx101Diag.mark("replies: message " + chatId + "/" + messageId + " — " + foundNow + " found in " + scannedNow + " messages" + (replies.cut ? ", stopped at the limit" : ""));
        UI.post(() -> progress.onDone(replies));
      } else {
        UI.post(() -> progress.onProgress(foundNow, scannedNow));
        from[0] = newest;
        tdlib.client().send(new TdApi.GetChatHistory(chatId, newest, -(PAGE - 1), PAGE, false), page[0]);
      }
    };
    tdlib.client().send(new TdApi.GetChatHistory(chatId, messageId, -(PAGE - 1), PAGE, false), page[0]);
    return replies;
  }

  /**
   * Answers a {@link TdApi.SearchChatMessages} from the found replies, as TDLib would: newest first, from
   * {@code fromMessageId} (0 — the newest), a negative {@code offset} adding newer ones; the sender, the type and the
   * words of the search narrow them.
   */
  public TdApi.FoundChatMessages answer (Tdlib tdlib, TdApi.SearchChatMessages query) {
    ArrayList<TdApi.Message> list = new ArrayList<>(found.length);
    String words = query.query != null ? query.query.trim().toLowerCase() : "";
    for (TdApi.Message message : found) {
      if (query.senderId != null && !MessagesSearchManagerMiddleware.filterBySender(message, query.senderId)) continue;
      if (query.filter != null && query.filter.getConstructor() != TdApi.SearchMessagesFilterEmpty.CONSTRUCTOR && !MessagesSearchManagerMiddleware.filterByType(tdlib, message, query.filter)) continue;
      if (!words.isEmpty()) {
        TdApi.FormattedText text = Td.textOrCaption(message.content);
        if (text == null || text.text == null || !text.text.toLowerCase().contains(words)) continue;
      }
      list.add(message);
    }
    int start = 0;
    if (query.fromMessageId != 0) {
      start = list.size();
      for (int i = 0; i < list.size(); i++) {
        if (list.get(i).id <= query.fromMessageId) {
          start = i;
          break;
        }
      }
    }
    start = Math.max(0, start + Math.min(0, query.offset));
    int end = Math.min(list.size(), start + Math.max(1, query.limit));
    TdApi.Message[] page = start < end ? list.subList(start, end).toArray(new TdApi.Message[0]) : new TdApi.Message[0];
    long next = end < list.size() ? list.get(end).id : 0;
    return new TdApi.FoundChatMessages(list.size(), page, next);
  }

  public @Nullable TdApi.Message newest () {
    return found.length > 0 ? found[0] : null;
  }
}
