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
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101: «Saved by chats» — Saved Messages grouped by the chat each message was saved from (official apps show
 * Saved Messages this way). A row opens Saved Messages limited to that chat.
 */
public class Tgx101SavedTopicsController extends RecyclerViewController<Void> implements View.OnClickListener, org.thunderdog.challegram.navigation.Menu, org.thunderdog.challegram.navigation.MoreDelegate {
  public Tgx101SavedTopicsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public static void open (ViewController<?> context) {
    context.navigateTo(new Tgx101SavedTopicsController(context.context(), context.tdlib()));
  }

  /** (user 2026-10-21 «Избранное — через ⋮ два режима: как чаты / как сообщения»): Saved Messages switched to «as chats» —
   *  this screen takes the feed's place (back leads where the feed's did) */
  public static void switchTo (ViewController<?> feed) {
    org.thunderdog.challegram.unsorted.Settings.instance().setTgx101SavedAsChats(true);
    Tgx101SavedTopicsController c = new Tgx101SavedTopicsController(feed.context(), feed.tdlib());
    c.replacesFeed = true;
    feed.navigateTo(c);
  }

  private boolean replacesFeed;

  @Override
  public void onFocus () {
    super.onFocus();
    if (replacesFeed) {
      replacesFeed = false;
      destroyStackItemByIdExcludingLast(R.id.controller_messages);
    }
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_more;
  }

  @Override
  public void fillMenuItems (int id, org.thunderdog.challegram.navigation.HeaderView header, android.widget.LinearLayout menu) {
    if (id == R.id.menu_more) header.addMoreButton(menu, this);
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_more) {
      showMore(new int[] {R.id.btn_tgx101SavedAsMessages}, new String[] {Lang.getString(R.string.Tgx101SavedAsMessages)}, 0);
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    if (id == R.id.btn_tgx101SavedAsMessages) {
      org.thunderdog.challegram.unsorted.Settings.instance().setTgx101SavedAsChats(false);
      tdlib.ui().openChat(this, tdlib.selfChatId(), new TdlibUi.ChatOpenParameters().keepStack().tgx101Feed().after(chatId ->
        org.thunderdog.challegram.tool.UI.post(() -> destroyStackItemByIdExcludingLast(R.id.controller_tgx101SavedTopics), 450)));
    }
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101SavedTopics;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.SavedMessages); // as the official app: «Saved Messages», shown as chats
  }

  // (user 2026-10-21 «Избранное должно выглядеть как обычное избранное из офа»): «as chats» is a list like the chat
  // list — the avatar of where the records came from, its name, the last record and its time
  private final List<TdApi.SavedMessagesTopic> topics = new ArrayList<>();
  private boolean loaded;
  private androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder> adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
      @Override
      public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder (android.view.ViewGroup parent, int viewType) {
        return new androidx.recyclerview.widget.RecyclerView.ViewHolder(new Row(context)) { };
      }

      @Override
      public void onBindViewHolder (androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
        ((Row) holder.itemView).bind(topics.get(position));
      }

      @Override
      public int getItemCount () {
        return topics.size();
      }
    };
    recyclerView.setAdapter(adapter);
    build();
    // the topics arrive as updates once Telegram is asked for them; an error means everything is loaded already
    tdlib.send(new TdApi.LoadSavedMessagesTopics(100), (ok, error) -> runOnUiThreadOptional(() -> {
      loaded = true;
      build();
    }));
  }

  private String title (TdApi.SavedMessagesTopic topic) {
    switch (topic.type.getConstructor()) {
      case TdApi.SavedMessagesTopicTypeMyNotes.CONSTRUCTOR:
        return Lang.getString(R.string.Tgx101SavedMyNotes);
      case TdApi.SavedMessagesTopicTypeAuthorHidden.CONSTRUCTOR:
        return Lang.getString(R.string.Tgx101SavedAuthorHidden);
      case TdApi.SavedMessagesTopicTypeSavedFromChat.CONSTRUCTOR:
        return tdlib.chatTitle(((TdApi.SavedMessagesTopicTypeSavedFromChat) topic.type).chatId);
    }
    return "";
  }

  @android.annotation.SuppressLint("NotifyDataSetChanged")
  private void build () {
    topics.clear();
    topics.addAll(tdlib.tgx101SavedTopics());
    adapter.notifyDataSetChanged();
  }

  private final class Row extends android.widget.LinearLayout {
    private final TopicAvatar avatar;
    private final android.widget.TextView name, time, preview;
    private TdApi.SavedMessagesTopic topic;

    Row (Context context) {
      super(context);
      setOrientation(HORIZONTAL);
      setGravity(android.view.Gravity.CENTER_VERTICAL);
      setPadding(org.thunderdog.challegram.tool.Screen.dp(10f), 0, org.thunderdog.challegram.tool.Screen.dp(14f), 0);
      setMinimumHeight(org.thunderdog.challegram.tool.Screen.dp(72f));
      setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, org.thunderdog.challegram.tool.Screen.dp(72f)));
      org.thunderdog.challegram.tool.Views.setClickable(this);
      org.thunderdog.challegram.support.RippleSupport.setSimpleWhiteBackground(this, Tgx101SavedTopicsController.this);
      avatar = new TopicAvatar(context);
      LayoutParams ap = new LayoutParams(org.thunderdog.challegram.tool.Screen.dp(52f), org.thunderdog.challegram.tool.Screen.dp(52f));
      ap.rightMargin = org.thunderdog.challegram.tool.Screen.dp(12f);
      addView(avatar, ap);
      android.widget.LinearLayout column = new android.widget.LinearLayout(context);
      column.setOrientation(VERTICAL);
      android.widget.LinearLayout top = new android.widget.LinearLayout(context);
      top.setOrientation(HORIZONTAL);
      name = new org.thunderdog.challegram.widget.EmojiTextView(context);
      name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16f);
      name.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoMedium());
      name.setTextColor(org.thunderdog.challegram.theme.Theme.textAccentColor());
      name.setSingleLine(true);
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
      top.addView(name, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
      time = new android.widget.TextView(context);
      time.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13f);
      time.setTextColor(org.thunderdog.challegram.theme.Theme.textDecentColor());
      top.addView(time);
      column.addView(top);
      preview = new org.thunderdog.challegram.widget.EmojiTextView(context);
      preview.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15f);
      preview.setTextColor(org.thunderdog.challegram.theme.Theme.textDecentColor());
      preview.setSingleLine(true);
      preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
      LayoutParams pp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
      pp.topMargin = org.thunderdog.challegram.tool.Screen.dp(3f);
      column.addView(preview, pp);
      addView(column, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
      setOnClickListener(v -> open(topic));
    }

    void bind (TdApi.SavedMessagesTopic topic) {
      this.topic = topic;
      name.setText(title(topic));
      switch (topic.type.getConstructor()) {
        case TdApi.SavedMessagesTopicTypeMyNotes.CONSTRUCTOR: // as the official app: a notes icon, not the own photo
          avatar.receiver.requestPlaceholder(tdlib, new org.thunderdog.challegram.data.AvatarPlaceholder.Metadata(tdlib.accentColor(org.thunderdog.challegram.telegram.TdlibAccentColor.InternalId.SAVED_MESSAGES), R.drawable.baseline_create_24), org.thunderdog.challegram.loader.AvatarReceiver.Options.NONE);
          break;
        case TdApi.SavedMessagesTopicTypeSavedFromChat.CONSTRUCTOR:
          avatar.receiver.requestChat(tdlib, ((TdApi.SavedMessagesTopicTypeSavedFromChat) topic.type).chatId, org.thunderdog.challegram.loader.AvatarReceiver.Options.NONE);
          break;
        default: // the sender hid their account
          avatar.receiver.requestPlaceholder(tdlib, new org.thunderdog.challegram.data.AvatarPlaceholder.Metadata(tdlib.accentColor(org.thunderdog.challegram.telegram.TdlibAccentColor.InternalId.INACTIVE), R.drawable.baseline_person_24), org.thunderdog.challegram.loader.AvatarReceiver.Options.NONE);
          break;
      }
      avatar.invalidate();
      TdApi.Message last = topic.lastMessage;
      preview.setText(last != null ? ContentPreview.getChatListPreview(tdlib, last.chatId, last, true).buildText(false) : "");
      time.setText(last != null ? Lang.timeOrDateShort(last.date, java.util.concurrent.TimeUnit.SECONDS) : "");
    }
  }

  private static final class TopicAvatar extends View {
    final org.thunderdog.challegram.loader.AvatarReceiver receiver = new org.thunderdog.challegram.loader.AvatarReceiver(this);

    TopicAvatar (Context context) {
      super(context);
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      receiver.attach();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      receiver.detach();
    }

    @Override
    protected void onDraw (android.graphics.Canvas c) {
      receiver.setBounds(0, 0, getWidth(), getHeight());
      if (receiver.needPlaceholder()) receiver.drawPlaceholder(c);
      receiver.draw(c);
    }
  }

  private void open (TdApi.SavedMessagesTopic topic) {
    if (topic == null) return;
    TdApi.Chat self = tdlib.chat(tdlib.selfChatId());
    if (self == null) return;
    TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters().keepStack();
    params.messageTopicId = new TdApi.MessageTopicSavedMessages(topic.id);
    tdlib.ui().openChat(this, self, params);
  }

  @Override
  public void onClick (View v) { }
}
