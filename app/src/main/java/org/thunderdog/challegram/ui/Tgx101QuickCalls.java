package org.thunderdog.challegram.ui;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;

import tgx.td.ChatId;

/**
 * TGx101 (user 2026-10-06): up to 5 quick call contacts in the hold menu of the capsule's «Calls».
 * Until the user picks their own, the 5 people called most often (TDLib top chats, category «calls») are shown.
 */
public final class Tgx101QuickCalls {
  public static final int MAX = 5;

  private static long[] topCalls = new long[0];

  private Tgx101QuickCalls () { }

  /** The chosen contacts, or the most called ones; refreshes the latter in the background */
  public static long[] userIds (Tdlib tdlib) {
    long[] chosen = Settings.instance().tgx101QuickCalls();
    refreshTop(tdlib);
    return chosen.length > 0 ? chosen : topCalls;
  }

  /** True when the menu has nothing to show yet: the most called contacts are still being fetched */
  public static boolean needsFetch () {
    return Settings.instance().tgx101QuickCalls().length == 0 && topCalls.length == 0;
  }

  public static void refreshTop (Tdlib tdlib) {
    refreshTop(tdlib, null);
  }

  // user 2026-10-08 20:54 «зажал кнопку звонков и ничего не показалось, спустя 3 секунды появились»: the list was fetched
  // only on the first hold — now also when the menu is created, and a hold with nothing yet waits for it
  public static void refreshTop (Tdlib tdlib, @androidx.annotation.Nullable Runnable after) {
    tdlib.send(new TdApi.GetTopChats(new TdApi.TopChatCategoryCalls(), MAX), (chats, error) -> {
      if (chats == null) {
        if (after != null) tdlib.ui().post(after);
        return;
      }
      List<Long> users = new ArrayList<>();
      for (long chatId : chats.chatIds) {
        long userId = ChatId.toUserId(chatId);
        if (userId != 0 && users.size() < MAX) users.add(userId);
      }
      long[] out = new long[users.size()];
      for (int i = 0; i < out.length; i++) out[i] = users.get(i);
      topCalls = out;
      if (after != null) tdlib.ui().post(after);
    });
  }

  /** Remove a contact / add one / go back to «most called» */
  public static void openEditor (ViewController<?> c, Tdlib tdlib) {
    long[] chosen = Settings.instance().tgx101QuickCalls();
    List<Integer> ids = new ArrayList<>();
    List<String> titles = new ArrayList<>();
    for (int i = 0; i < chosen.length; i++) {
      ids.add(i);
      titles.add(Lang.getString(R.string.Tgx101QuickCallsRemove, TD.getUserName(chosen[i], tdlib.cache().user(chosen[i]))));
    }
    if (chosen.length < MAX) {
      ids.add(100);
      titles.add(Lang.getString(R.string.Tgx101QuickCallsAdd));
    }
    if (chosen.length > 0) {
      ids.add(101);
      titles.add(Lang.getString(R.string.Tgx101QuickCallsReset));
    }
    int[] outIds = new int[ids.size()];
    for (int i = 0; i < outIds.length; i++) outIds[i] = ids.get(i);
    c.showOptions(Lang.getString(R.string.Tgx101QuickCallsHint), outIds, titles.toArray(new String[0]), null, null, (v, id) -> {
      if (id == 100) {
        pick(c, tdlib);
      } else if (id == 101) {
        Settings.instance().setTgx101QuickCalls(new long[0]);
      } else if (id >= 0 && id < chosen.length) {
        long[] next = new long[chosen.length - 1];
        for (int i = 0, k = 0; i < chosen.length; i++) if (i != id) next[k++] = chosen[i];
        Settings.instance().setTgx101QuickCalls(next);
      }
      return true;
    });
  }

  private static void pick (ViewController<?> c, Tdlib tdlib) {
    ContactsController picker = new ContactsController(c.context(), tdlib);
    picker.setArguments(new ContactsController.Args(new org.thunderdog.challegram.util.SenderPickerDelegate() {
      @Override
      public boolean onSenderPick (ContactsController context, android.view.View view, TdApi.MessageSender senderId) {
        if (senderId instanceof TdApi.MessageSenderUser) {
          long userId = ((TdApi.MessageSenderUser) senderId).userId;
          long[] chosen = Settings.instance().tgx101QuickCalls();
          for (long id : chosen) if (id == userId) { context.navigateBack(); return true; }
          if (chosen.length < MAX) {
            long[] next = new long[chosen.length + 1];
            System.arraycopy(chosen, 0, next, 0, chosen.length);
            next[chosen.length] = userId;
            Settings.instance().setTgx101QuickCalls(next);
            UI.showToast(Lang.getString(R.string.Tgx101QuickCallsAdded, TD.getUserName(userId, tdlib.cache().user(userId))), android.widget.Toast.LENGTH_SHORT);
          }
          context.navigateBack();
        }
        return true;
      }

      @Override
      public String getUserPickTitle () {
        return Lang.getString(R.string.Tgx101QuickCallsAdd);
      }
    }));
    c.navigateTo(picker);
  }
}
