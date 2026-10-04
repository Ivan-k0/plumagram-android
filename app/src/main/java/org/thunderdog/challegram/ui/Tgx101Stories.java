/*
 * TGx101 / PlumaGram: stories — ordering of the strip, «show less», hide, notifications, incognito.
 */
package org.thunderdog.challegram.ui;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Tgx101Stories {
  private Tgx101Stories () { }

  private static final String PREFS = "tgx101_stories";
  private static final String KEY_LESS = "less";

  private static SharedPreferences prefs () {
    return UI.getAppContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
  }

  /** «Show less»: the chat always goes to the end of the strip (local, this phone only) */
  public static boolean isShownLess (long chatId) {
    return prefs().getStringSet(KEY_LESS, Collections.emptySet()).contains(Long.toString(chatId));
  }

  public static void setShownLess (long chatId, boolean less) {
    Set<String> set = new HashSet<>(prefs().getStringSet(KEY_LESS, Collections.emptySet()));
    if (less) set.add(Long.toString(chatId)); else set.remove(Long.toString(chatId));
    prefs().edit().putStringSet(KEY_LESS, set).apply();
  }

  public static boolean hasUnread (TdApi.ChatActiveStories stories) {
    if (stories.stories == null || stories.stories.length == 0) return false;
    return stories.stories[stories.stories.length - 1].storyId > stories.maxReadStoryId;
  }

  /** Strip order: contacts first, then the rest; inside each group unseen before seen, then Telegram's order; «show less» last */
  public static List<TdApi.ChatActiveStories> order (Tdlib tdlib, List<TdApi.ChatActiveStories> source) {
    List<TdApi.ChatActiveStories> list = new ArrayList<>(source);
    final long selfChatId = tdlib.selfChatId();
    Collections.sort(list, (a, b) -> {
      int ga = group(tdlib, a, selfChatId), gb = group(tdlib, b, selfChatId);
      if (ga != gb) return Integer.compare(ga, gb);
      boolean ua = hasUnread(a), ub = hasUnread(b);
      if (ua != ub) return ua ? -1 : 1;
      return tdlib.storiesComparator().compare(a, b);
    });
    return list;
  }

  private static int group (Tdlib tdlib, TdApi.ChatActiveStories stories, long selfChatId) {
    if (stories.chatId == selfChatId) return 0;
    if (isShownLess(stories.chatId)) return 3;
    long userId = tdlib.chatUserId(stories.chatId);
    if (userId != 0) {
      TdApi.User user = tdlib.cache().user(userId);
      if (user != null && user.isContact) return 1;
    }
    return 2;
  }

  // Notifications about new stories of this chat

  public static boolean notifiesNewStories (Tdlib tdlib, long chatId) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null || chat.notificationSettings == null) return false;
    return !chat.notificationSettings.useDefaultMuteStories && !chat.notificationSettings.muteStories;
  }

  public static void setNotifyNewStories (Tdlib tdlib, long chatId, boolean notify) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null || chat.notificationSettings == null) return;
    TdApi.ChatNotificationSettings s = chat.notificationSettings;
    TdApi.ChatNotificationSettings copy = new TdApi.ChatNotificationSettings(
      s.useDefaultMuteFor, s.muteFor, s.useDefaultSound, s.soundId, s.useDefaultShowPreview, s.showPreview,
      false, !notify, s.useDefaultStorySound, s.storySoundId, s.useDefaultShowStoryPoster, s.showStoryPoster,
      s.useDefaultDisablePinnedMessageNotifications, s.disablePinnedMessageNotifications,
      s.useDefaultDisableMentionNotifications, s.disableMentionNotifications);
    tdlib.send(new TdApi.SetChatNotificationSettings(chatId, copy), (ok, error) -> { });
  }

  public static void setHidden (Tdlib tdlib, long chatId, boolean hidden) {
    tdlib.send(new TdApi.SetChatActiveStoriesList(chatId, hidden ? new TdApi.StoryListArchive() : new TdApi.StoryListMain()), (ok, error) -> {
      if (error != null) UI.showError(error);
    });
  }

  @Nullable
  public static TdApi.File bestFile (TdApi.StoryContent content) {
    if (content instanceof TdApi.StoryContentPhoto) {
      TdApi.Photo photo = ((TdApi.StoryContentPhoto) content).photo;
      if (photo == null || photo.sizes == null || photo.sizes.length == 0) return null;
      TdApi.PhotoSize best = photo.sizes[0];
      for (TdApi.PhotoSize size : photo.sizes) {
        if (size.width * size.height > best.width * best.height) best = size;
      }
      return best.photo;
    }
    if (content instanceof TdApi.StoryContentVideo) {
      TdApi.StoryVideo video = ((TdApi.StoryContentVideo) content).video;
      return video != null ? video.video : null;
    }
    return null;
  }
}
