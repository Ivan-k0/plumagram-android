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
public class Tgx101SavedTopicsController extends RecyclerViewController<Void> implements View.OnClickListener {
  public Tgx101SavedTopicsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public static void open (ViewController<?> context) {
    context.navigateTo(new Tgx101SavedTopicsController(context.context(), context.tdlib()));
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101SavedTopics;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101SavedByChats);
  }

  private SettingsAdapter adapter;
  private final List<TdApi.SavedMessagesTopic> topics = new ArrayList<>();
  private boolean loaded;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        if (data instanceof TdApi.SavedMessagesTopic) {
          TdApi.Message last = ((TdApi.SavedMessagesTopic) data).lastMessage;
          view.setData(last != null ? ContentPreview.getChatListPreview(tdlib, last.chatId, last, true).buildText(false) : "");
        }
      }
    };
    recyclerView.setAdapter(adapter);
    build();
    // the topics arrive as updates once Telegram is asked for them
    // an error means everything is loaded already
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

  private void build () {
    topics.clear();
    topics.addAll(tdlib.tgx101SavedTopics());
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    if (topics.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, loaded ? R.string.Tgx101SavedEmpty : R.string.LoadingInformation));
    }
    boolean first = true;
    for (TdApi.SavedMessagesTopic topic : topics) {
      if (!first) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      first = false;
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_tgx101SavedTopic, topic.isPinned ? R.drawable.deproko_baseline_pin_24 : R.drawable.baseline_bookmark_24, title(topic)).setData(topic).setLongId(topic.id));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_tgx101SavedTopic) {
      ListItem item = (ListItem) v.getTag();
      long topicId = item.getLongId();
      TdApi.Chat self = tdlib.chat(tdlib.selfChatId());
      if (self == null) return;
      TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters().keepStack();
      params.messageTopicId = new TdApi.MessageTopicSavedMessages(topicId);
      tdlib.ui().openChat(this, self, params);
    }
  }
}
