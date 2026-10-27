/*
 * This file is a part of PlumaGram
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
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TGx101 «Газета» (user 2026-10-27): the posts of the subscribed channels in one feed, oldest at the top, newest coming
 * in at the bottom as in a chat; it opens on the first unread post, a post scrolled past is read in its channel too.
 * Which channels go in (all by default, the archived ones only when switched on), how photos show and whether long
 * posts fold are kept per account.
 */
public final class Tgx101Gazeta {
  private Tgx101Gazeta () { }

  /** posts of one channel taken at most, unread ones; and how many read ones stand above them */
  static final int MAX_UNREAD_PER_CHANNEL = 40, READ_CONTEXT = 3;

  public static final int PHOTOS_AS_IS = 0, PHOTOS_THUMB = 1;

  private static String key (Tdlib tdlib, String name) {
    return "tgx101_gazeta_" + name + "_" + tdlib.id();
  }

  public static Set<Long> excluded (Tdlib tdlib) {
    long[] ids = Settings.instance().pmc().getLongArray(key(tdlib, "off"));
    Set<Long> out = new HashSet<>();
    if (ids != null) for (long id : ids) out.add(id);
    return out;
  }

  public static void setExcluded (Tdlib tdlib, Set<Long> ids) {
    long[] out = new long[ids.size()];
    int i = 0;
    for (long id : ids) out[i++] = id;
    Settings.instance().pmc().putLongArray(key(tdlib, "off"), out);
    cache.remove(tdlib.id());
  }

  public static boolean withArchive (Tdlib tdlib) {
    return Settings.instance().pmc().getBoolean(key(tdlib, "archive"), false);
  }

  public static void setWithArchive (Tdlib tdlib, boolean value) {
    Settings.instance().pmc().putBoolean(key(tdlib, "archive"), value);
    cache.remove(tdlib.id());
  }

  // ---- two profiles of the look: light and dark (user 2026-10-27), switched by ☀/☾ in the feed's header ----

  public static final int PROFILE_LIGHT = 0, PROFILE_DARK = 1;

  public static int profile () {
    int p = Settings.instance().pmc().getInt("tgx101_gazeta_profile", -1);
    return p == -1 ? (org.thunderdog.challegram.theme.Theme.isDark() ? PROFILE_DARK : PROFILE_LIGHT) : p;
  }

  public static void setProfile (int profile) {
    Settings.instance().pmc().putInt("tgx101_gazeta_profile", profile);
  }

  private static String pkey (String name) {
    return "tgx101_gazeta_" + name + "_p" + profile();
  }

  public static int photos () {
    return Settings.instance().pmc().getInt(pkey("photos"), PHOTOS_AS_IS);
  }

  public static void setPhotos (int value) {
    Settings.instance().pmc().putInt(pkey("photos"), value);
  }

  public static final int FOLD_NONE = 0, FOLD_8 = 8, FOLD_10 = 10, FOLD_SHORT = 1;

  /** how long posts fold: not at all, to 8 or 10 lines, or to 10 lines with «✨ Short» (the AI summary) */
  public static int fold () {
    return Settings.instance().pmc().getInt(pkey("fold"), FOLD_10);
  }

  public static void setFold (int value) {
    Settings.instance().pmc().putInt(pkey("fold"), value);
  }

  public static final float[] FONT_SIZES = {13f, 14f, 15f, 16f, 17f, 18f, 20f, 22f};

  public static float fontSize () {
    return Settings.instance().pmc().getFloat(pkey("font"), 15f);
  }

  public static void setFontSize (float sp) {
    Settings.instance().pmc().putFloat(pkey("font"), sp);
  }

  /** {page, card, text, secondary text, link} — reading colours, as in e-readers */
  public static final int[][] LIGHT_PALETTES = {
    {0xffeef1f4, 0xffffffff, 0xff1b1f23, 0xff7b8794, 0xff2a7bd1}, // white
    {0xffe9dfc9, 0xfff6eedc, 0xff3b2f20, 0xff8a7a62, 0xff9a5b1e}, // sepia
    {0xfff1ece0, 0xfffffbf0, 0xff2b2925, 0xff8c867a, 0xff3a72b0}, // cream
    {0xffd9dcdf, 0xffeceef0, 0xff202326, 0xff6f767d, 0xff2f6fae}  // grey
  };
  public static final int[][] DARK_PALETTES = {
    {0xff0e1621, 0xff17212b, 0xffe9eef2, 0xff7d8b99, 0xff6ab3f3}, // dark
    {0xff1a1611, 0xff26201a, 0xffe6d9c3, 0xff9c8c74, 0xffd9a46a}, // night sepia
    {0xff000000, 0xff0d0d0d, 0xffdedede, 0xff7a7a7a, 0xff6ab3f3}, // black
    {0xff1c1d1f, 0xff2a2b2e, 0xffe4e4e6, 0xff8b8d91, 0xff8ab4f8}  // graphite
  };
  public static final int[] LIGHT_PALETTE_NAMES = {R.string.Tgx101GazetaColorWhite, R.string.Tgx101GazetaColorSepia, R.string.Tgx101GazetaColorCream, R.string.Tgx101GazetaColorGrey};
  public static final int[] DARK_PALETTE_NAMES = {R.string.Tgx101GazetaColorDark, R.string.Tgx101GazetaColorNightSepia, R.string.Tgx101GazetaColorBlack, R.string.Tgx101GazetaColorGraphite};

  public static int paletteIndex () {
    return Math.max(0, Math.min(3, Settings.instance().pmc().getInt(pkey("palette"), 0)));
  }

  public static void setPaletteIndex (int index) {
    Settings.instance().pmc().putInt(pkey("palette"), index);
  }

  public static int[] palette () {
    return (profile() == PROFILE_DARK ? DARK_PALETTES : LIGHT_PALETTES)[paletteIndex()];
  }

  /** brightness and saturation of the posts' pictures, percent */
  public static int brightness () {
    return Settings.instance().pmc().getInt(pkey("bright"), 100);
  }

  public static int saturation () {
    return Settings.instance().pmc().getInt(pkey("sat"), 100);
  }

  public static void setPicture (int brightness, int saturation) {
    Settings.instance().pmc().edit().putInt(pkey("bright"), brightness).putInt(pkey("sat"), saturation).apply();
  }

  /** quick sets: as is, soft for the night, black and white, juicy */
  public static final int[][] PICTURE_PRESETS = {{100, 100}, {80, 75}, {100, 0}, {105, 140}};
  public static final int[] PICTURE_PRESET_NAMES = {R.string.Tgx101GazetaPicAsIs, R.string.Tgx101GazetaPicNight, R.string.Tgx101GazetaPicBw, R.string.Tgx101GazetaPicJuicy};

  @Nullable
  public static android.graphics.ColorMatrixColorFilter pictureFilter () {
    int b = brightness(), s = saturation();
    if (b == 100 && s == 100) return null;
    android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
    m.setSaturation(s / 100f);
    float k = b / 100f;
    android.graphics.ColorMatrix scale = new android.graphics.ColorMatrix();
    scale.setScale(k, k, k, 1f);
    m.postConcat(scale);
    return new android.graphics.ColorMatrixColorFilter(m);
  }

  // ---- for both profiles ----

  public static boolean reactions () {
    return Settings.instance().pmc().getBoolean("tgx101_gazeta_reactions", true);
  }

  public static void setReactions (boolean value) {
    Settings.instance().pmc().putBoolean("tgx101_gazeta_reactions", value);
  }

  public static boolean stars () {
    return Settings.instance().pmc().getBoolean("tgx101_gazeta_stars", true);
  }

  public static void setStars (boolean value) {
    Settings.instance().pmc().putBoolean("tgx101_gazeta_stars", value);
  }

  /** the bird over the feed (off unless switched on) */
  public static boolean bird () {
    return Settings.instance().pmc().getBoolean("tgx101_gazeta_bird", false);
  }

  public static void setBird (boolean value) {
    Settings.instance().pmc().putBoolean("tgx101_gazeta_bird", value);
  }

  // the channels last found, per account: the side menu counts their unread posts without asking TDLib again
  private static final Map<Integer, long[]> cache = new HashMap<>();

  /** unread posts in the channels of the feed (0 until the channels were looked up once) */
  public static int unreadCount (@Nullable Tdlib tdlib) {
    if (tdlib == null) return 0;
    long[] ids = cache.get(tdlib.id());
    if (ids == null) {
      loadChannels(tdlib, result -> { });
      return 0;
    }
    int count = 0;
    for (long id : ids) {
      TdApi.Chat chat = tdlib.chat(id);
      if (chat != null) count += chat.unreadCount;
    }
    return count;
  }

  public interface ChannelsCallback {
    void onChannels (long[] chatIds);
  }

  /** all subscribed channels (main list, and the archive when asked), in the chat list's order; on the UI thread */
  public static void allChannels (Tdlib tdlib, boolean archive, ChannelsCallback callback) {
    List<Long> out = new ArrayList<>();
    AtomicInteger pending = new AtomicInteger(archive ? 2 : 1);
    Runnable done = () -> {
      if (pending.decrementAndGet() == 0) {
        long[] ids = new long[out.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = out.get(i);
        UI.post(() -> callback.onChannels(ids));
      }
    };
    TdApi.ChatList[] lists = archive ? new TdApi.ChatList[] {new TdApi.ChatListMain(), new TdApi.ChatListArchive()} : new TdApi.ChatList[] {new TdApi.ChatListMain()};
    for (TdApi.ChatList list : lists) {
      tdlib.send(new TdApi.GetChats(list, 1000), (chats, error) -> {
        if (chats != null) {
          synchronized (out) {
            for (long chatId : chats.chatIds) {
              TdApi.Chat chat = tdlib.chat(chatId);
              if (chat != null && TD.isChannel(chat.type) && !out.contains(chatId)) out.add(chatId);
            }
          }
        }
        done.run();
      });
    }
  }

  /** the channels of the feed: all but the switched-off ones */
  public static void loadChannels (Tdlib tdlib, ChannelsCallback callback) {
    Set<Long> off = excluded(tdlib);
    allChannels(tdlib, withArchive(tdlib), all -> {
      List<Long> on = new ArrayList<>();
      for (long id : all) if (!off.contains(id)) on.add(id);
      long[] ids = new long[on.size()];
      for (int i = 0; i < ids.length; i++) ids[i] = on.get(i);
      cache.put(tdlib.id(), ids);
      callback.onChannels(ids);
    });
  }

  /** One card of the feed: a post, or an album as one */
  public static final class Post {
    public final long chatId;
    public final List<TdApi.Message> messages = new ArrayList<>();
    public boolean unread;
    public boolean expanded;
    // the AI summary («✨ Short»)
    public @Nullable TdApi.FormattedText summary;
    public boolean summaryShown, summaryLoading;

    Post (long chatId) {
      this.chatId = chatId;
    }

    public TdApi.Message main () {
      // the album's caption usually sits on one of its messages
      for (TdApi.Message m : messages) {
        TdApi.FormattedText text = tgx.td.Td.textOrCaption(m.content);
        if (text != null && !tgx.td.Td.isEmpty(text)) return m;
      }
      return messages.get(0);
    }

    public TdApi.Message first () {
      return messages.get(0);
    }

    public long[] ids () {
      long[] ids = new long[messages.size()];
      for (int i = 0; i < ids.length; i++) ids[i] = messages.get(i).id;
      return ids;
    }
  }

  public interface FeedCallback {
    void onFeed (List<Post> posts, int firstUnread);
  }

  private static boolean isPost (TdApi.Message m) {
    if (m == null || m.content == null) return false;
    switch (m.content.getConstructor()) {
      case TdApi.MessageText.CONSTRUCTOR:
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessageAnimation.CONSTRUCTOR:
      case TdApi.MessageDocument.CONSTRUCTOR:
      case TdApi.MessageAudio.CONSTRUCTOR:
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
      case TdApi.MessageVideoNote.CONSTRUCTOR:
      case TdApi.MessagePoll.CONSTRUCTOR:
      case TdApi.MessageSticker.CONSTRUCTOR:
      case TdApi.MessageLocation.CONSTRUCTOR:
      case TdApi.MessageVenue.CONSTRUCTOR:
      case TdApi.MessageContact.CONSTRUCTOR:
      case TdApi.MessagePaidMedia.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  /** the unread posts of every channel (with a few read ones above), merged by time; on the UI thread */
  public static void loadFeed (Tdlib tdlib, long[] chatIds, FeedCallback callback) {
    if (chatIds.length == 0) {
      UI.post(() -> callback.onFeed(new ArrayList<>(), -1));
      return;
    }
    List<TdApi.Message> all = new ArrayList<>();
    Map<Long, Long> lastRead = new HashMap<>();
    AtomicInteger pending = new AtomicInteger(chatIds.length);
    Runnable done = () -> {
      if (pending.decrementAndGet() != 0) return;
      List<Post> posts = build(tdlib, all, lastRead);
      int firstUnread = -1;
      for (int i = 0; i < posts.size(); i++) {
        if (posts.get(i).unread) {
          firstUnread = i;
          break;
        }
      }
      final int at = firstUnread;
      UI.post(() -> callback.onFeed(posts, at));
    };
    for (long chatId : chatIds) {
      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat == null) {
        done.run();
        continue;
      }
      int unread = Math.min(chat.unreadCount, MAX_UNREAD_PER_CHANNEL);
      long read = chat.lastReadInboxMessageId;
      synchronized (all) {
        lastRead.put(chatId, read);
      }
      TdApi.GetChatHistory request = unread > 0 && read != 0
        ? new TdApi.GetChatHistory(chatId, read, -unread, unread + READ_CONTEXT, false)
        : new TdApi.GetChatHistory(chatId, 0, 0, READ_CONTEXT + Math.max(0, unread), false);
      fetch(tdlib, request, 2, messages -> {
        synchronized (all) {
          for (TdApi.Message m : messages) {
            if (isPost(m) && org.thunderdog.challegram.data.Tgx101MessageFilters.match(tdlib, m, chat) == null) all.add(m);
          }
        }
        done.run();
      });
    }
  }

  private interface MessagesCallback {
    void onMessages (TdApi.Message[] messages);
  }

  // TDLib gives at first only what it has at hand (often one message): asked again, it brings the rest
  private static void fetch (Tdlib tdlib, TdApi.GetChatHistory request, int attempts, MessagesCallback callback) {
    tdlib.send(request, (messages, error) -> {
      TdApi.Message[] got = messages != null ? messages.messages : new TdApi.Message[0];
      if (attempts > 1 && got.length <= 1 && request.limit > 1) {
        fetch(tdlib, request, attempts - 1, more -> callback.onMessages(more.length >= got.length ? more : got));
        return;
      }
      callback.onMessages(got);
    });
  }

  private static List<Post> build (Tdlib tdlib, List<TdApi.Message> all, Map<Long, Long> lastRead) {
    List<TdApi.Message> sorted;
    synchronized (all) {
      sorted = new ArrayList<>(all);
    }
    Collections.sort(sorted, (a, b) -> a.date != b.date ? Integer.compare(a.date, b.date) : Long.compare(a.id, b.id));
    List<Post> posts = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Post last = null;
    for (TdApi.Message m : sorted) {
      if (!seen.add(m.chatId + ":" + m.id)) continue;
      if (last != null && m.mediaAlbumId != 0 && last.chatId == m.chatId && last.first().mediaAlbumId == m.mediaAlbumId) {
        last.messages.add(m);
      } else {
        last = new Post(m.chatId);
        last.messages.add(m);
        posts.add(last);
      }
      Long read = lastRead.get(m.chatId);
      if (read != null && m.id > read) last.unread = true;
    }
    return posts;
  }

  /** opens the feed over whatever is on top */
  public static void open (BaseActivity context, Tdlib tdlib) {
    NavigationController navigation = context.navigation();
    ViewController<?> current = navigation != null ? navigation.getCurrentStackItem() : null;
    if (current != null) {
      if (current instanceof Tgx101GazetaController) return;
      current.navigateTo(new Tgx101GazetaController(context, tdlib));
    }
  }
}
