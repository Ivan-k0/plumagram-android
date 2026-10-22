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
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.ChatHeaderView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;

/**
 * TGx101: forum groups (groups with topics) open as a list of topics, like in the official apps.
 * A topic opens as a regular chat limited to that topic. Admins and members with the right can
 * create topics; admins can rename, pin, close, hide General and delete them.
 */
public class Tgx101TopicsController extends RecyclerViewController<Tgx101TopicsController.Args> implements org.thunderdog.challegram.navigation.MoreDelegate, ChatHeaderView.Callback {
  public static class Args {
    public final TdApi.Chat chat;
    public final @Nullable TdApi.ChatList chatList;

    public Args (TdApi.Chat chat, @Nullable TdApi.ChatList chatList) {
      this.chat = chat;
      this.chatList = chatList;
    }
  }

  // The colours Telegram offers for topic icons
  private static final int[] ICON_COLORS = {0x6FB9F0, 0xFFD67E, 0xCB86DB, 0x8EEE98, 0xFF93B2, 0xFB6F5F};
  private static final int PAGE_SIZE = 100;

  // Entry points

  /** Whether opening this chat should show its topics instead of one mixed feed. */
  public static boolean shouldOpenTopics (Tdlib tdlib, TdApi.Chat chat) {
    return chat != null && chat.viewAsTopics && tdlib.isForum(chat.id);
  }

  public static void open (TdlibDelegate context, TdApi.Chat chat, @Nullable TdApi.ChatList chatList) {
    Tgx101TopicsController c = new Tgx101TopicsController(context.context(), context.tdlib());
    c.setArguments(new Args(chat, chatList));
    context.context().navigation().navigateTo(c);
  }

  /** "Show topics" from a forum opened as one feed: remembers the choice and opens the list. */
  public static void showTopics (ViewController<?> context, TdApi.Chat chat) {
    context.tdlib().send(new TdApi.ToggleChatViewAsTopics(chat.id, true), (ok, error) -> { });
    open(context, chat, null);
  }

  /**
   * (approved mockup «Группы с темами», Б — user 2026-10-22): «Topics | Feed» swaps the two screens in place — the one being
   * left is taken out of the stack once the other has come, so back leads to the chat list from either
   */
  public static void dropWhenReplaced (ViewController<?> from) {
    final long until = android.os.SystemClock.uptimeMillis() + 4000;
    Runnable check = new Runnable() {
      @Override
      public void run () {
        if (from.isDestroyed() || from.navigationController() == null) return;
        org.thunderdog.challegram.navigation.NavigationController nav = from.navigationController();
        org.thunderdog.challegram.navigation.NavigationStack stack = nav.getStack();
        int index = stack.indexOf(from);
        if (index == -1) return;
        if (!nav.isAnimating() && index < stack.size() - 1) {
          stack.destroy(index);
          return;
        }
        if (android.os.SystemClock.uptimeMillis() < until) UI.post(this, 50);
      }
    };
    UI.post(check, 50);
  }

  /** From a forum's one feed to its topics: the feed is replaced */
  public static void switchToTopics (ViewController<?> feed, TdApi.Chat chat) {
    feed.tdlib().send(new TdApi.ToggleChatViewAsTopics(chat.id, true), (ok, error) -> { });
    Tgx101TopicsController c = new Tgx101TopicsController(feed.context(), feed.tdlib());
    c.setArguments(new Args(chat, null));
    c.forceFastAnimationOnce();
    feed.context().navigation().navigateTo(c);
    dropWhenReplaced(feed);
  }

  // The last topics seen per group: the topics screen opens with them at once (0.2.242: «Feed → Topics» waited ~0.4 s)
  private static final java.util.Map<String, List<TdApi.ForumTopic>> cachedTopics = new java.util.concurrent.ConcurrentHashMap<>();

  private static String cacheKey (Tdlib tdlib, long chatId) {
    return tdlib.id() + ":" + chatId;
  }

  /** The first page of a group's topics, fetched while its feed is open — «Topics» then opens with them in place */
  public static void prefetch (Tdlib tdlib, long chatId) {
    String key = cacheKey(tdlib, chatId);
    if (cachedTopics.containsKey(key)) return;
    tdlib.send(new TdApi.GetForumTopics(chatId, "", 0, 0, 0, PAGE_SIZE), (page, error) -> {
      if (page == null) return;
      List<TdApi.ForumTopic> list = new ArrayList<>();
      for (TdApi.ForumTopic topic : page.topics) list.add(topic);
      cachedTopics.put(key, list);
    });
  }

  // The chat list's line of a group's latest topics (as in the official app): «⁉️ НЕПОЛАДКИ   # ПОЛЕМИКА   💰 КУПЛЮ», the last
  // message's topic first; by account and group, rebuilt when the group's last message changes
  private static final java.util.Map<String, Object[]> recentLines = new java.util.concurrent.ConcurrentHashMap<>(); // {lastMessageId, line}
  private static final java.util.Set<String> recentLoading = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

  public static @Nullable String recentTopicsLine (Tdlib tdlib, TdApi.Chat chat, @Nullable Runnable onLoaded) {
    if (chat == null || chat.lastMessage == null) return null;
    final String key = cacheKey(tdlib, chat.id);
    final long lastId = chat.lastMessage.id;
    Object[] entry = recentLines.get(key);
    if (entry != null && (Long) entry[0] == lastId) return (String) entry[1];
    final TdApi.MessageTopic lastTopic = chat.lastMessage.topicId;
    if (recentLoading.add(key)) {
      tdlib.send(new TdApi.GetForumTopics(chat.id, "", 0, 0, 0, 4), (page, error) -> UI.post(() -> { // the emoji cache is the UI thread's
        recentLoading.remove(key);
        if (page == null || page.topics.length == 0) return;
        List<TdApi.ForumTopic> list = new ArrayList<>();
        for (TdApi.ForumTopic topic : page.topics) list.add(topic);
        // the last message's topic leads
        if (lastTopic instanceof TdApi.MessageTopicForum) {
          int id = ((TdApi.MessageTopicForum) lastTopic).forumTopicId;
          for (int i = 0; i < list.size(); i++) {
            if (list.get(i).info.forumTopicId == id) {
              list.add(0, list.remove(i));
              break;
            }
          }
        }
        Runnable build = () -> {
          StringBuilder b = new StringBuilder();
          for (int i = 0; i < Math.min(3, list.size()); i++) {
            TdApi.ForumTopicInfo info = list.get(i).info;
            if (b.length() > 0) b.append("   ");
            String emoji = info.icon != null && info.icon.customEmojiId != 0 ? iconEmoji.get(info.icon.customEmojiId) : null;
            b.append(emoji != null ? emoji : "#").append(' ').append(info.name);
          }
          recentLines.put(key, new Object[] {lastId, b.toString()});
          if (onLoaded != null) UI.post(onLoaded);
        };
        // their emoji first, so the line is built once
        List<Long> missing = new ArrayList<>();
        for (TdApi.ForumTopic topic : list) {
          long id = topic.info.icon != null ? topic.info.icon.customEmojiId : 0;
          if (id != 0 && iconEmoji.get(id) == null) missing.add(id);
        }
        if (missing.isEmpty()) {
          build.run();
          return;
        }
        long[] ids = new long[missing.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = missing.get(i);
        tdlib.send(new TdApi.GetCustomEmojiStickers(ids), (stickers, error2) -> {
          if (stickers != null) {
            UI.post(() -> {
              for (TdApi.Sticker sticker : stickers.stickers) {
                if (sticker.fullType instanceof TdApi.StickerFullTypeCustomEmoji && !StringUtils.isEmpty(sticker.emoji)) {
                  iconEmoji.put(((TdApi.StickerFullTypeCustomEmoji) sticker.fullType).customEmojiId, sticker.emoji);
                }
              }
              build.run();
            });
          } else {
            build.run();
          }
        });
      }));
    }
    return entry != null ? (String) entry[1] : null; // the old line until the new one comes
  }

  // Topic names for the chat list and the feed (mockup «Группы с темами»: «who · topic» under a forum's name), by account
  private static final java.util.Map<String, String> topicNames = new java.util.concurrent.ConcurrentHashMap<>();
  private static final java.util.Set<String> topicNamesLoading = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

  /** The topic's name, or null while it loads — {@code onLoaded} runs on the UI thread once it's known */
  public static @Nullable String topicName (Tdlib tdlib, long chatId, @Nullable TdApi.MessageTopic topicId, @Nullable Runnable onLoaded) {
    if (!(topicId instanceof TdApi.MessageTopicForum)) return null;
    int id = ((TdApi.MessageTopicForum) topicId).forumTopicId;
    String key = tdlib.id() + ":" + chatId + ":" + id;
    String name = topicNames.get(key);
    if (name != null) return name;
    if (topicNamesLoading.add(key)) {
      tdlib.send(new TdApi.GetForumTopic(chatId, id), (topic, error) -> {
        topicNamesLoading.remove(key);
        if (topic == null || StringUtils.isEmpty(topic.info.name)) return;
        topicNames.put(key, topic.info.name);
        if (onLoaded != null) UI.post(onLoaded);
      });
    }
    return null;
  }

  /** In a chat opened for one topic, the header shows the topic name with the group name below. */
  public static void bindTopicHeader (ViewController<?> context, ChatHeaderView header, TdApi.Chat chat, @Nullable TdApi.MessageTopic topicId) {
    if (!(topicId instanceof TdApi.MessageTopicForum) || header == null) return;
    int forumTopicId = ((TdApi.MessageTopicForum) topicId).forumTopicId;
    context.tdlib().send(new TdApi.GetForumTopic(chat.id, forumTopicId), (topic, error) -> {
      if (topic == null) return;
      context.runOnUiThreadOptional(() -> header.setText(topic.info.name, context.tdlib().chatTitle(chat)));
    });
  }

  public Tgx101TopicsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101Topics;
  }

  @Override
  public long getChatId () {
    return getArgumentsStrict().chat.id;
  }

  @Override
  public CharSequence getName () {
    return tdlib.chatTitle(getArgumentsStrict().chat);
  }

  private final List<TdApi.ForumTopic> topics = new ArrayList<>();
  private final List<Object> rows = new ArrayList<>();
  private static final Object ROW_SWITCH = new Object(), ROW_EMPTY = new Object();
  private TopicsAdapter adapter;
  private boolean loaded, needReload;
  private int loadGeneration;
  private ChatHeaderView headerCell;
  private @Nullable String searchQuery;
  // a topic's own emoji (its custom emoji icon's base emoji), by custom emoji id
  private static final android.util.LongSparseArray<String> iconEmoji = new android.util.LongSparseArray<>(); // shared: no letters flashing on the next visit

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    // the header as in the group's chat: its photo, name and members
    headerCell = new ChatHeaderView(context, tdlib, this);
    headerCell.setCallback(this);
    headerCell.setInnerMargins(Screen.dp(56f), Screen.dp(100f)); // room for search and ⋮ (the name ran under the search, 0.2.242)
    headerCell.setChat(tdlib, getArgumentsStrict().chat, null);
    // topics fetched ahead (from the feed's switch or an earlier visit): the list is there at once, refreshed behind it
    List<TdApi.ForumTopic> cached = cachedTopics.get(cacheKey(tdlib, getArgumentsStrict().chat.id));
    if (cached != null && !cached.isEmpty()) {
      topics.addAll(cached);
      loaded = true;
    }
    adapter = new TopicsAdapter();
    recyclerView.setAdapter(adapter);
    // a swipe to the left over the topics opens the feed, as the switch does
    recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
      private float downX, downY;
      private boolean tracking;

      @Override
      public boolean onInterceptTouchEvent (@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
        switch (e.getActionMasked()) {
          case android.view.MotionEvent.ACTION_DOWN:
            downX = e.getX();
            downY = e.getY();
            tracking = !inSearchMode();
            break;
          case android.view.MotionEvent.ACTION_MOVE: {
            float dx = e.getX() - downX, dy = e.getY() - downY;
            if (tracking && dx < -Screen.dp(72f) && Math.abs(dx) > Math.abs(dy) * 2f) {
              tracking = false;
              if (topicsSwitch != null) topicsSwitch.setFeed(true, true);
              rv.postDelayed(() -> openAllMessages(), 140);
              return true;
            }
            if (Math.abs(dy) > Screen.dp(16f)) tracking = false;
            break;
          }
        }
        return false;
      }
    });
    buildRows();
    load();
  }

  private @Nullable org.thunderdog.challegram.component.chat.Tgx101TopicsSwitch topicsSwitch;

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  public void onChatHeaderClick () {
    tdlib.ui().openChatProfile(this, getArgumentsStrict().chat, null, null);
  }

  // Header: search and ⋮

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  protected int getSearchHint () {
    return R.string.Tgx101TopicsSearchHint;
  }

  @Override
  public void fillMenuItems (int id, org.thunderdog.challegram.navigation.HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_search) {
      header.addSearchButton(menu, this);
      header.addMoreButton(menu, this);
    } else if (id == R.id.menu_clear) {
      header.addClearButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_search) {
      if (headerView != null) headerView.openSearchMode();
    } else if (id == R.id.menu_btn_clear) {
      clearSearchInput();
    } else if (id == R.id.menu_btn_more) {
      List<Integer> ids = new ArrayList<>();
      List<String> titles = new ArrayList<>();
      List<Integer> icons = new ArrayList<>();
      if (canCreateTopics()) {
        ids.add(R.id.btn_tgx101TopicNew); titles.add(Lang.getString(R.string.Tgx101TopicNew)); icons.add(R.drawable.baseline_add_24);
      }
      ids.add(R.id.btn_tgx101TopicsInfo); titles.add(Lang.getString(R.string.GroupInfo)); icons.add(R.drawable.baseline_info_24);
      ids.add(R.id.btn_reportChat); titles.add(Lang.getString(R.string.Report)); icons.add(R.drawable.baseline_report_24);
      ids.add(R.id.btn_tgx101TopicsLeave); titles.add(Lang.getString(R.string.LeaveMegaMenu)); icons.add(R.drawable.baseline_logout_24);
      showMore(toArray(ids), titles.toArray(new String[0]), toArray(icons));
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    TdApi.Chat chat = getArgumentsStrict().chat;
    if (id == R.id.btn_tgx101TopicNew) {
      createTopic();
    } else if (id == R.id.btn_tgx101TopicsInfo) {
      onChatHeaderClick();
    } else if (id == R.id.btn_reportChat) {
      TdlibUi.reportChats(this, new long[] {chat.id}, null, null);
    } else if (id == R.id.btn_tgx101TopicsLeave) {
      tdlib.ui().showDeleteChatConfirm(this, chat.id);
    }
  }

  @Override
  protected void onSearchInputChanged (String query) {
    super.onSearchInputChanged(query);
    String q = query != null ? query.trim() : "";
    searchQuery = q.isEmpty() ? null : q;
    buildRows();
  }

  @Override
  protected void onLeaveSearchMode () {
    searchQuery = null;
    buildRows();
  }

  // The screen comes in with its topics already in place, not empty
  @Override
  public boolean needAsynchronousAnimation () {
    return !loaded;
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return 400L;
  }

  private void tgx101ShowWhenReady () {
    if (getScheduledAnimation() == null) return;
    View view = getRecyclerView();
    if (view != null) {
      view.post(this::executeScheduledAnimation); // after the rows' layout
    } else {
      executeScheduledAnimation();
    }
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (needReload) { // back from a topic: fresh counters and last messages
      needReload = false;
      load();
    }
  }

  @Override
  public void onBlur () {
    super.onBlur();
    needReload = true;
  }

  // Rights

  private @Nullable TdApi.ChatMemberStatus status () {
    return tdlib.chatStatus(getArgumentsStrict().chat.id);
  }

  private boolean canManageTopics () {
    TdApi.ChatMemberStatus status = status();
    if (status == null) return false;
    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.ChatMemberStatusAdministrator) status).rights.canManageTopics;
    }
    return false;
  }

  private boolean canCreateTopics () {
    if (canManageTopics()) return true;
    TdApi.ChatMemberStatus status = status();
    if (status == null) return false;
    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusMember.CONSTRUCTOR: {
        TdApi.Chat chat = tdlib.chat(getArgumentsStrict().chat.id);
        return chat != null && chat.permissions.canCreateTopics;
      }
      case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
        return ((TdApi.ChatMemberStatusRestricted) status).permissions.canCreateTopics;
    }
    return false;
  }

  // Loading

  private void load () {
    final int generation = ++loadGeneration;
    final List<TdApi.ForumTopic> result = new ArrayList<>();
    loadPage(generation, result, 0, 0, 0);
  }

  private void loadPage (int generation, List<TdApi.ForumTopic> result, int offsetDate, long offsetMessageId, int offsetTopicId) {
    long chatId = getArgumentsStrict().chat.id;
    tdlib.send(new TdApi.GetForumTopics(chatId, "", offsetDate, offsetMessageId, offsetTopicId, PAGE_SIZE), (page, error) -> runOnUiThreadOptional(() -> {
      if (generation != loadGeneration) return;
      if (page == null) {
        if (!loaded) UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
        return;
      }
      for (TdApi.ForumTopic topic : page.topics) result.add(topic);
      boolean more = page.topics.length > 0 && result.size() < page.totalCount && (page.nextOffsetDate != 0 || page.nextOffsetMessageId != 0 || page.nextOffsetForumTopicId != 0);
      if (!loaded || !more) {
        // (user 2026-10-22 «как впервые открываются темы — чтобы не было пустого экрана в первые секунды»): the first page
        // is shown at once, and the screen slides in only once it's there (BaseActivity waits up to 400 ms); the rest
        // of the pages come in behind it
        topics.clear();
        topics.addAll(result);
        loaded = true;
        cachedTopics.put(cacheKey(tdlib, getArgumentsStrict().chat.id), new ArrayList<>(result));
        buildRows();
        tgx101ShowWhenReady();
      }
      if (more) {
        loadPage(generation, result, page.nextOffsetDate, page.nextOffsetMessageId, page.nextOffsetForumTopicId);
      }
    }));
  }

  private void buildRows () {
    rows.clear();
    if (searchQuery == null) {
      rows.add(ROW_SWITCH);
      rows.addAll(topics);
    } else {
      String q = searchQuery.toLowerCase();
      for (TdApi.ForumTopic topic : topics) {
        if (topic.info.name != null && topic.info.name.toLowerCase().contains(q)) rows.add(topic);
      }
    }
    if (loaded && rows.size() == (searchQuery == null ? 1 : 0)) rows.add(ROW_EMPTY);
    if (adapter != null) adapter.notifyDataSetChanged();
    loadIconEmoji();
  }

  /** The custom emoji of the topics' icons → their base emoji, shown as the icon */
  private void loadIconEmoji () {
    List<Long> missing = new ArrayList<>();
    for (TdApi.ForumTopic topic : topics) {
      long id = topic.info.icon != null ? topic.info.icon.customEmojiId : 0;
      if (id != 0 && iconEmoji.get(id) == null && !missing.contains(id)) missing.add(id);
    }
    if (missing.isEmpty()) return;
    long[] ids = new long[missing.size()];
    for (int i = 0; i < ids.length; i++) ids[i] = missing.get(i);
    tdlib.send(new TdApi.GetCustomEmojiStickers(ids), (stickers, error) -> runOnUiThreadOptional(() -> {
      if (stickers == null) return;
      for (TdApi.Sticker sticker : stickers.stickers) {
        if (sticker.fullType instanceof TdApi.StickerFullTypeCustomEmoji && !StringUtils.isEmpty(sticker.emoji)) {
          iconEmoji.put(((TdApi.StickerFullTypeCustomEmoji) sticker.fullType).customEmojiId, sticker.emoji);
        }
      }
      if (adapter != null) adapter.notifyDataSetChanged();
    }));
  }

  // Actions

  private void openTopic (TdApi.ForumTopic topic) {
    TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters().keepStack();
    params.messageTopicId = new TdApi.MessageTopicForum(topic.info.forumTopicId);
    params.chatList = getArgumentsStrict().chatList;
    tdlib.ui().openChat(this, getArgumentsStrict().chat, params);
  }

  private void openAllMessages () {
    TdApi.Chat chat = getArgumentsStrict().chat;
    tdlib.send(new TdApi.ToggleChatViewAsTopics(chat.id, false), (ok, error) -> { });
    TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters().keepStack();
    params.chatList = getArgumentsStrict().chatList;
    params.tgx101IgnoreTopics = true;
    tdlib.ui().openChat(this, chat, params);
    dropWhenReplaced(this); // «Feed» takes this screen's place
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (topicsSwitch != null) topicsSwitch.setFeed(false, false); // back here: the thumb on «Topics»
  }

  private void createTopic () {
    editTopic(null);
  }

  // The default icons Telegram offers for topics (custom emoji) → their emoji, shared
  private static final List<Long> defaultIconIds = new ArrayList<>();

  /**
   * (user 2026-10-22 «как в официальном»): a topic is created or edited in one sheet — its name, and as in the official app
   * its colour (only when it's created: Telegram fixes it then) and an emoji icon from Telegram's set for topics
   */
  private void editTopic (@Nullable TdApi.ForumTopic topic) {
    final boolean creating = topic == null;
    final int[] color = {creating ? ICON_COLORS[topics.size() % ICON_COLORS.length] : topic.info.icon.color};
    final long[] emojiId = {creating ? 0 : topic.info.icon.customEmojiId};
    final android.widget.EditText[] field = new android.widget.EditText[1];
    final TextView[] preview = new TextView[1];
    final Runnable[] refreshPreview = new Runnable[1];
    final Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(this, null, body -> {
      Context ctx = context();
      body.addView(Tgx101Sheet.title(ctx, Lang.getString(creating ? R.string.Tgx101TopicNew : R.string.Tgx101TopicEdit)));
      // the icon as it will be, beside the name
      LinearLayout nameRow = new LinearLayout(ctx);
      nameRow.setOrientation(LinearLayout.HORIZONTAL);
      nameRow.setGravity(Gravity.CENTER_VERTICAL);
      TextView icon = new TextView(ctx);
      icon.setGravity(Gravity.CENTER);
      icon.setTypeface(Typeface.DEFAULT_BOLD);
      preview[0] = icon;
      LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Screen.dp(44f), Screen.dp(44f));
      ip.leftMargin = Screen.dp(16f);
      ip.rightMargin = Screen.dp(10f);
      nameRow.addView(icon, ip);
      android.widget.EditText e = new android.widget.EditText(ctx);
      field[0] = e;
      e.setSingleLine(true);
      e.setHint(Lang.getString(R.string.Tgx101TopicNameHint));
      if (!creating) {
        e.setText(topic.info.name);
        e.setSelection(e.getText().length());
      }
      Tgx101SavedHomeController.styleField(e, true);
      LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, Screen.dp(48f), 1f);
      ep.rightMargin = Screen.dp(16f);
      nameRow.addView(e, ep);
      LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      rp.bottomMargin = Screen.dp(10f);
      body.addView(nameRow, rp);
      refreshPreview[0] = () -> {
        String emoji = emojiId[0] != 0 ? iconEmoji.get(emojiId[0]) : null;
        GradientDrawable square = new GradientDrawable();
        square.setCornerRadius(Screen.dp(12f));
        if (emoji != null) {
          square.setColor(me.vkryl.core.ColorUtils.alphaColor(.16f, 0xff000000 | color[0]));
          icon.setText(emoji);
          icon.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22f);
          icon.setTextColor(0xff000000);
        } else {
          square.setColor(0xff000000 | color[0]);
          String name = field[0] != null ? field[0].getText().toString().trim() : "";
          icon.setText(name.isEmpty() ? "#" : firstLetter(name));
          icon.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
          icon.setTextColor(0xffffffff);
        }
        icon.setBackground(square);
      };
      e.addTextChangedListener(new android.text.TextWatcher() {
        @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged (CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged (android.text.Editable s) { refreshPreview[0].run(); }
      });
      // colours — only when creating
      if (creating) {
        LinearLayout colors = new LinearLayout(ctx);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        colors.setGravity(Gravity.CENTER);
        for (int c : ICON_COLORS) {
          View dot = new View(ctx);
          GradientDrawable d = new GradientDrawable();
          d.setShape(GradientDrawable.OVAL);
          d.setColor(0xff000000 | c);
          dot.setBackground(d);
          LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Screen.dp(32f), Screen.dp(32f));
          dp.leftMargin = dp.rightMargin = Screen.dp(7f);
          dot.setOnClickListener(v -> {
            color[0] = c;
            refreshPreview[0].run();
          });
          colors.addView(dot, dp);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(44f));
        cp.bottomMargin = Screen.dp(6f);
        body.addView(colors, cp);
      }
      // Telegram's icons for topics, 7 a row; the first cell — no icon (the colour and the letter)
      LinearLayout grid = new LinearLayout(ctx);
      grid.setOrientation(LinearLayout.VERTICAL);
      LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      gp.leftMargin = gp.rightMargin = Screen.dp(12f);
      gp.bottomMargin = Screen.dp(8f);
      body.addView(grid, gp);
      Runnable fillGrid = () -> {
        grid.removeAllViews();
        List<Long> ids = new ArrayList<>();
        ids.add(0L);
        ids.addAll(defaultIconIds);
        LinearLayout row = null;
        for (int k = 0; k < ids.size() && k < 7 * 6; k++) {
          if (k % 7 == 0) {
            row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(44f)));
          }
          final long id = ids.get(k);
          TextView cell = new TextView(ctx);
          cell.setGravity(Gravity.CENTER);
          cell.setTextSize(TypedValue.COMPLEX_UNIT_DIP, id == 0 ? 15f : 22f);
          cell.setText(id == 0 ? "Aa" : StringUtils.isEmpty(iconEmoji.get(id)) ? "·" : iconEmoji.get(id));
          cell.setTextColor(Theme.textDecentColor());
          cell.setOnClickListener(v -> {
            emojiId[0] = id;
            refreshPreview[0].run();
          });
          row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
      };
      fillGrid.run();
      if (defaultIconIds.isEmpty()) {
        tdlib.send(new TdApi.GetForumTopicDefaultIcons(), (stickers, error) -> runOnUiThreadOptional(() -> {
          if (stickers == null) return;
          defaultIconIds.clear();
          for (TdApi.Sticker sticker : stickers.stickers) {
            if (sticker.fullType instanceof TdApi.StickerFullTypeCustomEmoji) {
              long id = ((TdApi.StickerFullTypeCustomEmoji) sticker.fullType).customEmojiId;
              defaultIconIds.add(id);
              if (!StringUtils.isEmpty(sticker.emoji)) iconEmoji.put(id, sticker.emoji);
            }
          }
          fillGrid.run();
        }));
      }
      Runnable submit = () -> {
        String name = field[0].getText().toString().trim();
        if (name.isEmpty()) return;
        org.thunderdog.challegram.tool.Keyboard.hide(field[0]);
        h[0].dismiss();
        long chatId = getArgumentsStrict().chat.id;
        if (creating) {
          tdlib.send(new TdApi.CreateForumTopic(chatId, name, false, new TdApi.ForumTopicIcon(color[0], emojiId[0])), (info, error) -> runOnUiThreadOptional(() -> {
            if (info == null) {
              UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
              return;
            }
            load();
            TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters().keepStack();
            params.messageTopicId = new TdApi.MessageTopicForum(info.forumTopicId);
            tdlib.ui().openChat(this, getArgumentsStrict().chat, params);
          }));
        } else {
          send(new TdApi.EditForumTopic(chatId, topic.info.forumTopicId, name, emojiId[0] != topic.info.icon.customEmojiId, emojiId[0]));
        }
      };
      e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
      e.setOnEditorActionListener((v, actionId, event) -> {
        submit.run();
        return true;
      });
      body.addView(Tgx101SavedActions.primaryButton(ctx, Lang.getString(R.string.Done), v -> submit.run()));
      refreshPreview[0].run();
    });
    if (field[0] != null) {
      field[0].requestFocus();
      field[0].postDelayed(() -> {
        if (androidx.core.view.ViewCompat.isAttachedToWindow(field[0])) org.thunderdog.challegram.tool.Keyboard.show(field[0]);
      }, 280);
    }
  }

  private void showTopicOptions (TdApi.ForumTopic topic) {
    boolean manage = canManageTopics();
    boolean own = topic.info.isOutgoing && canCreateTopics();
    if (!manage && !own) return;
    List<Integer> ids = new ArrayList<>();
    List<String> titles = new ArrayList<>();
    List<Integer> colors = new ArrayList<>();
    List<Integer> icons = new ArrayList<>();
    if (manage || own) {
      ids.add(R.id.btn_tgx101TopicEdit); titles.add(Lang.getString(R.string.Tgx101TopicEdit)); colors.add(OptionColor.NORMAL); icons.add(R.drawable.baseline_edit_24);
    }
    if (manage) {
      ids.add(R.id.btn_tgx101TopicPin); titles.add(Lang.getString(topic.isPinned ? R.string.Tgx101TopicUnpin : R.string.Tgx101TopicPin)); colors.add(OptionColor.NORMAL); icons.add(topic.isPinned ? R.drawable.deproko_baseline_pin_undo_24 : R.drawable.deproko_baseline_pin_24);
    }
    if (manage || own) {
      ids.add(R.id.btn_tgx101TopicClose); titles.add(Lang.getString(topic.info.isClosed ? R.string.Tgx101TopicReopen : R.string.Tgx101TopicClose)); colors.add(OptionColor.NORMAL); icons.add(R.drawable.baseline_lock_24);
    }
    if (manage && topic.info.isGeneral) {
      ids.add(R.id.btn_tgx101TopicHide); titles.add(Lang.getString(topic.info.isHidden ? R.string.Tgx101TopicUnhide : R.string.Tgx101TopicHide)); colors.add(OptionColor.NORMAL); icons.add(R.drawable.baseline_visibility_24);
    }
    if (manage && !topic.info.isGeneral) {
      ids.add(R.id.btn_tgx101TopicDelete); titles.add(Lang.getString(R.string.Tgx101TopicDelete)); colors.add(OptionColor.RED); icons.add(R.drawable.baseline_delete_24);
    }
    showOptions(topic.info.name, toArray(ids), titles.toArray(new String[0]), toArray(colors), toArray(icons), (itemView, id) -> {
      long chatId = getArgumentsStrict().chat.id;
      int topicId = topic.info.forumTopicId;
      if (id == R.id.btn_tgx101TopicEdit) {
        editTopic(topic);
      } else if (id == R.id.btn_tgx101TopicPin) {
        send(new TdApi.ToggleForumTopicIsPinned(chatId, topicId, !topic.isPinned));
      } else if (id == R.id.btn_tgx101TopicClose) {
        send(new TdApi.ToggleForumTopicIsClosed(chatId, topicId, !topic.info.isClosed));
      } else if (id == R.id.btn_tgx101TopicHide) {
        send(new TdApi.ToggleGeneralForumTopicIsHidden(chatId, !topic.info.isHidden));
      } else if (id == R.id.btn_tgx101TopicDelete) {
        showOptions(Lang.getString(R.string.Tgx101TopicDeleteConfirm, topic.info.name), new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.Tgx101TopicDelete), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (v, confirm) -> {
          if (confirm == R.id.btn_done) send(new TdApi.DeleteForumTopic(chatId, topicId));
          return true;
        });
      }
      return true;
    });
  }

  private void send (TdApi.Function<TdApi.Ok> function) {
    tdlib.send(function, (ok, error) -> runOnUiThreadOptional(() -> {
      if (ok == null) UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
      load();
    }));
  }

  private static int[] toArray (List<Integer> list) {
    int[] array = new int[list.size()];
    for (int i = 0; i < array.length; i++) array[i] = list.get(i);
    return array;
  }

  // List

  private static final int VIEW_TOPIC = 0, VIEW_ACTION = 1, VIEW_EMPTY = 2, VIEW_SWITCH = 3;

  private final class TopicsAdapter extends RecyclerView.Adapter<Row> {
    @Override
    public int getItemViewType (int position) {
      Object item = rows.get(position);
      return item == ROW_SWITCH ? VIEW_SWITCH : item == ROW_EMPTY ? VIEW_EMPTY : item instanceof TdApi.ForumTopic ? VIEW_TOPIC : VIEW_ACTION;
    }

    @NonNull
    @Override
    public Row onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      if (viewType == VIEW_SWITCH) {
        org.thunderdog.challegram.component.chat.Tgx101TopicsSwitch view = new org.thunderdog.challegram.component.chat.Tgx101TopicsSwitch(context(), false);
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(org.thunderdog.challegram.component.chat.Tgx101TopicsSwitch.HEIGHT + 4f)));
        view.setPadding(0, Screen.dp(4f), 0, 0);
        view.setListener(toFeed -> {
          if (toFeed) openAllMessages();
        });
        topicsSwitch = view;
        addThemeInvalidateListener(view);
        return new Row(view);
      }
      return new Row(context(), viewType);
    }

    @Override
    public void onBindViewHolder (@NonNull Row holder, int position) {
      Object item = rows.get(position);
      if (item instanceof TdApi.ForumTopic) {
        holder.bindTopic((TdApi.ForumTopic) item);
      } else if (item == ROW_EMPTY) {
        holder.bindEmpty();
      }
    }

    @Override
    public int getItemCount () {
      return rows.size();
    }
  }

  private final class Row extends RecyclerView.ViewHolder {
    private final LinearLayout layout;
    private final TextView icon, title, subtitle, date, badge;
    private final ImageView actionIcon, stateIcon;

    Row (View view) {
      super(view);
      layout = null;
      icon = title = subtitle = date = badge = null;
      actionIcon = stateIcon = null;
    }

    Row (Context context, int viewType) {
      super(new LinearLayout(context));
      layout = (LinearLayout) itemView;
      layout.setOrientation(LinearLayout.HORIZONTAL);
      layout.setGravity(Gravity.CENTER_VERTICAL);
      layout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, viewType == VIEW_TOPIC ? Screen.dp(72f) : Screen.dp(56f)));
      layout.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);

      icon = new TextView(context);
      icon.setGravity(Gravity.CENTER);
      icon.setTextColor(0xffffffff);
      icon.setTypeface(Typeface.DEFAULT_BOLD);
      icon.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
      actionIcon = new ImageView(context);
      actionIcon.setScaleType(ImageView.ScaleType.CENTER);
      int iconSize = Screen.dp(viewType == VIEW_TOPIC ? 44f : 24f);
      LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
      iconParams.rightMargin = Screen.dp(viewType == VIEW_TOPIC ? 14f : 24f);
      layout.addView(viewType == VIEW_TOPIC ? icon : actionIcon, iconParams);

      LinearLayout texts = new LinearLayout(context);
      texts.setOrientation(LinearLayout.VERTICAL);
      layout.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

      LinearLayout titleLine = new LinearLayout(context);
      titleLine.setOrientation(LinearLayout.HORIZONTAL);
      titleLine.setGravity(Gravity.CENTER_VERTICAL);
      texts.addView(titleLine);
      title = newText(context, 16f, viewType == VIEW_TOPIC);
      titleLine.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      stateIcon = new ImageView(context);
      LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(Screen.dp(16f), Screen.dp(16f));
      stateParams.leftMargin = Screen.dp(6f);
      titleLine.addView(stateIcon, stateParams);
      date = newText(context, 13f, false);
      LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      dateParams.leftMargin = Screen.dp(8f);
      titleLine.addView(date, dateParams);

      LinearLayout subtitleLine = new LinearLayout(context);
      subtitleLine.setOrientation(LinearLayout.HORIZONTAL);
      subtitleLine.setGravity(Gravity.CENTER_VERTICAL);
      texts.addView(subtitleLine);
      subtitle = newText(context, 14f, false);
      subtitleLine.addView(subtitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      badge = newText(context, 12f, true);
      badge.setGravity(Gravity.CENTER);
      badge.setMinWidth(Screen.dp(22f));
      badge.setPadding(Screen.dp(6f), Screen.dp(1f), Screen.dp(6f), Screen.dp(1f));
      LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(22f));
      badgeParams.leftMargin = Screen.dp(8f);
      subtitleLine.addView(badge, badgeParams);

      if (viewType == VIEW_EMPTY) {
        layout.setGravity(Gravity.CENTER);
        actionIcon.setVisibility(View.GONE);
      }
    }

    private TextView newText (Context context, float sizeDp, boolean bold) {
      TextView view = new TextView(context);
      view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
      view.setSingleLine(true);
      view.setEllipsize(TextUtils.TruncateAt.END);
      if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
      return view;
    }

    private void applyTheme () {
      layout.setBackgroundColor(Theme.fillingColor());
      RippleSupport.setSimpleWhiteBackground(layout, Tgx101TopicsController.this);
      title.setTextColor(Theme.textAccentColor());
      subtitle.setTextColor(Theme.textDecentColor());
      date.setTextColor(Theme.textDecentColor());
    }

    void bindTopic (TdApi.ForumTopic topic) {
      applyTheme();
      TdApi.ForumTopicInfo info = topic.info;
      // (mockup «Группы с темами»): the topic's own emoji on a soft square of its colour; without one — its letter
      int color = 0xff000000 | (info.isGeneral ? 0x8E9CA8 : info.icon.color);
      String emoji = info.icon != null && info.icon.customEmojiId != 0 ? iconEmoji.get(info.icon.customEmojiId) : null;
      GradientDrawable square = new GradientDrawable();
      square.setCornerRadius(Screen.dp(12f));
      if (emoji != null) {
        square.setColor(me.vkryl.core.ColorUtils.alphaColor(.16f, color));
        icon.setText(emoji);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22f);
        icon.setTextColor(0xff000000);
      } else {
        square.setColor(color);
        icon.setText(info.isGeneral ? "#" : firstLetter(info.name));
        icon.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
        icon.setTextColor(0xffffffff);
      }
      icon.setBackground(square);

      title.setText(info.isHidden ? info.name + " · " + Lang.getString(R.string.Tgx101TopicHidden) : info.name);
      int stateRes = info.isClosed ? R.drawable.baseline_lock_24 : topic.isPinned ? R.drawable.deproko_baseline_pin_24 : 0;
      if (stateRes != 0) {
        Drawable d = ContextCompat.getDrawable(context(), stateRes);
        if (d != null) d.mutate().setColorFilter(Paints.getColorFilter(Theme.textDecentColor())); // TGx101: setTint needs Android 5
        stateIcon.setImageDrawable(d);
        stateIcon.setVisibility(View.VISIBLE);
      } else {
        stateIcon.setVisibility(View.GONE);
      }

      TdApi.Message last = topic.lastMessage;
      if (last != null) {
        String text = ContentPreview.getChatListPreview(tdlib, last.chatId, last, false).buildText(true);
        String sender = last.isOutgoing ? Lang.getString(R.string.FromYou) : tdlib.senderName(last, false, true);
        if (StringUtils.isEmpty(sender)) {
          subtitle.setText(text);
        } else {
          // the author in the accent colour, as in the chat list
          android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(sender).append(": ").append(text);
          b.setSpan(new android.text.style.ForegroundColorSpan(Theme.getColor(ColorId.textLink)), 0, sender.length() + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          subtitle.setText(b);
        }
        date.setText(Lang.timeOrDateShort(last.date, TimeUnit.SECONDS));
        date.setVisibility(View.VISIBLE);
      } else {
        subtitle.setText(Lang.getString(R.string.Tgx101TopicNoMessages));
        date.setVisibility(View.GONE);
      }

      int unread = topic.unreadMentionCount > 0 ? topic.unreadMentionCount : topic.unreadCount;
      if (unread > 0) {
        // muted: its own setting, or the group's when it uses the default — grey, as in the chat list
        boolean topicMuted = topic.notificationSettings != null && (topic.notificationSettings.useDefaultMuteFor ? !tdlib.chatNotificationsEnabled(getArgumentsStrict().chat.id) : topic.notificationSettings.muteFor > 0);
        boolean muted = topicMuted && topic.unreadMentionCount == 0;
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(Screen.dp(11f));
        pill.setColor(Theme.getColor(muted ? ColorId.badgeMuted : ColorId.badge));
        badge.setBackground(pill);
        badge.setTextColor(Theme.getColor(muted ? ColorId.badgeMutedText : ColorId.badgeText));
        badge.setText(topic.unreadMentionCount > 0 ? "@" : unread > 999 ? "999+" : String.valueOf(unread));
        badge.setVisibility(View.VISIBLE);
      } else {
        badge.setVisibility(View.GONE);
      }

      layout.setOnClickListener(v -> openTopic(topic));
      layout.setOnLongClickListener(v -> {
        showTopicOptions(topic);
        return true;
      });
    }

    void bindAction (int iconRes, String text, View.OnClickListener listener) {
      applyTheme();
      Drawable d = ContextCompat.getDrawable(context(), iconRes);
      if (d != null) d.mutate().setColorFilter(Paints.getColorFilter(Theme.getColor(ColorId.iconActive))); // TGx101: setTint needs Android 5
      actionIcon.setImageDrawable(d);
      title.setText(text);
      title.setTextColor(Theme.getColor(ColorId.textNeutral));
      subtitle.setVisibility(View.GONE);
      date.setVisibility(View.GONE);
      badge.setVisibility(View.GONE);
      stateIcon.setVisibility(View.GONE);
      layout.setOnClickListener(listener);
      layout.setOnLongClickListener(null);
    }

    void bindEmpty () {
      layout.setBackgroundColor(Theme.backgroundColor());
      title.setText(Lang.getString(R.string.Tgx101TopicsEmpty));
      title.setGravity(Gravity.CENTER);
      title.setTextColor(Theme.textDecentColor());
      subtitle.setVisibility(View.GONE);
      date.setVisibility(View.GONE);
      badge.setVisibility(View.GONE);
      stateIcon.setVisibility(View.GONE);
      layout.setOnClickListener(null);
    }
  }

  private static String firstLetter (String name) {
    if (StringUtils.isEmpty(name)) return "#";
    int cp = name.codePointAt(0);
    return new String(Character.toChars(Character.toUpperCase(cp)));
  }
}
