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
public class Tgx101TopicsController extends RecyclerViewController<Tgx101TopicsController.Args> {
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
  private static final Object ROW_CREATE = new Object(), ROW_ALL_MESSAGES = new Object(), ROW_EMPTY = new Object();
  private TopicsAdapter adapter;
  private boolean loaded, needReload;
  private int loadGeneration;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new TopicsAdapter();
    recyclerView.setAdapter(adapter);
    buildRows();
    load();
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
      if (more) {
        loadPage(generation, result, page.nextOffsetDate, page.nextOffsetMessageId, page.nextOffsetForumTopicId);
      } else {
        topics.clear();
        topics.addAll(result);
        loaded = true;
        buildRows();
      }
    }));
  }

  private void buildRows () {
    rows.clear();
    if (canCreateTopics()) rows.add(ROW_CREATE);
    rows.add(ROW_ALL_MESSAGES);
    rows.addAll(topics);
    if (loaded && topics.isEmpty()) rows.add(ROW_EMPTY);
    if (adapter != null) adapter.notifyDataSetChanged();
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
  }

  private void createTopic () {
    openInputAlert(Lang.getString(R.string.Tgx101TopicNew), Lang.getString(R.string.Tgx101TopicNameHint), R.string.Done, R.string.Cancel, null, (inputView, result) -> {
      String name = result.trim();
      if (StringUtils.isEmpty(name)) return false;
      int color = ICON_COLORS[topics.size() % ICON_COLORS.length];
      tdlib.send(new TdApi.CreateForumTopic(getArgumentsStrict().chat.id, name, false, new TdApi.ForumTopicIcon(color, 0)), (info, error) -> runOnUiThreadOptional(() -> {
        if (info == null) {
          UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
          return;
        }
        load();
        TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters().keepStack();
        params.messageTopicId = new TdApi.MessageTopicForum(info.forumTopicId);
        tdlib.ui().openChat(this, getArgumentsStrict().chat, params);
      }));
      return true;
    }, true);
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
        openInputAlert(Lang.getString(R.string.Tgx101TopicEdit), Lang.getString(R.string.Tgx101TopicNameHint), R.string.Done, R.string.Cancel, topic.info.name, (inputView, result) -> {
          String name = result.trim();
          if (StringUtils.isEmpty(name)) return false;
          send(new TdApi.EditForumTopic(chatId, topicId, name, false, 0));
          return true;
        }, true);
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

  private static final int VIEW_TOPIC = 0, VIEW_ACTION = 1, VIEW_EMPTY = 2;

  private final class TopicsAdapter extends RecyclerView.Adapter<Row> {
    @Override
    public int getItemViewType (int position) {
      Object item = rows.get(position);
      return item == ROW_EMPTY ? VIEW_EMPTY : item instanceof TdApi.ForumTopic ? VIEW_TOPIC : VIEW_ACTION;
    }

    @NonNull
    @Override
    public Row onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return new Row(context(), viewType);
    }

    @Override
    public void onBindViewHolder (@NonNull Row holder, int position) {
      Object item = rows.get(position);
      if (item instanceof TdApi.ForumTopic) {
        holder.bindTopic((TdApi.ForumTopic) item);
      } else if (item == ROW_CREATE) {
        holder.bindAction(R.drawable.baseline_add_24, Lang.getString(R.string.Tgx101TopicNew), v -> createTopic());
      } else if (item == ROW_ALL_MESSAGES) {
        holder.bindAction(R.drawable.baseline_forum_24, Lang.getString(R.string.Tgx101TopicsAllMessages), v -> openAllMessages());
      } else {
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
      GradientDrawable circle = new GradientDrawable();
      circle.setShape(GradientDrawable.OVAL);
      circle.setColor(0xff000000 | (info.isGeneral ? 0x8E9CA8 : info.icon.color));
      icon.setBackground(circle);
      icon.setText(info.isGeneral ? "#" : firstLetter(info.name));

      title.setText(info.isHidden ? info.name + " · " + Lang.getString(R.string.Tgx101TopicHidden) : info.name);
      int stateRes = info.isClosed ? R.drawable.baseline_lock_24 : topic.isPinned ? R.drawable.deproko_baseline_pin_24 : 0;
      if (stateRes != 0) {
        Drawable d = ContextCompat.getDrawable(context(), stateRes);
        if (d != null) d.mutate().setTint(Theme.textDecentColor());
        stateIcon.setImageDrawable(d);
        stateIcon.setVisibility(View.VISIBLE);
      } else {
        stateIcon.setVisibility(View.GONE);
      }

      TdApi.Message last = topic.lastMessage;
      if (last != null) {
        String text = ContentPreview.getChatListPreview(tdlib, last.chatId, last, false).buildText(true);
        String sender = last.isOutgoing ? Lang.getString(R.string.FromYou) : tdlib.senderName(last, false, true);
        subtitle.setText(StringUtils.isEmpty(sender) ? text : sender + ": " + text);
        date.setText(Lang.timeOrDateShort(last.date, TimeUnit.SECONDS));
        date.setVisibility(View.VISIBLE);
      } else {
        subtitle.setText(Lang.getString(R.string.Tgx101TopicNoMessages));
        date.setVisibility(View.GONE);
      }

      int unread = topic.unreadMentionCount > 0 ? topic.unreadMentionCount : topic.unreadCount;
      if (unread > 0) {
        boolean muted = topic.notificationSettings != null && topic.notificationSettings.muteFor > 0 && topic.unreadMentionCount == 0;
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
      if (d != null) d.mutate().setTint(Theme.getColor(ColorId.iconActive));
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
