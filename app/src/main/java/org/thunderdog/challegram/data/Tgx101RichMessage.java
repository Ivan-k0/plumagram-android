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
package org.thunderdog.challegram.data;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.InstantViewController;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * TGx101: rich messages (MessageRichMessage) — posts made of blocks, like Instant View pages.
 * The chat shows their text; the whole post (photos, buttons, formatting) opens in the built-in
 * Instant View viewer.
 */
public final class Tgx101RichMessage {
  private Tgx101RichMessage () { }

  private static final int MAX_CHAT_TEXT = 3000;

  public static TGMessage valueOf (MessagesManager context, TdApi.Message msg, TdApi.MessageRichMessage content) {
    TdApi.FormattedText text = toText(content.message);
    if (text.text.isEmpty()) {
      String hint = Lang.getString(R.string.RichMessageOpenHint);
      text = new TdApi.FormattedText(hint, new TdApi.TextEntity[] {new TdApi.TextEntity(0, hint.length(), new TdApi.TextEntityTypeItalic())});
    }
    return new TGMessageText(context, msg, text);
  }

  public static boolean isRichMessage (@Nullable TdApi.Message message) {
    return message != null && message.content != null && message.content.getConstructor() == TdApi.MessageRichMessage.CONSTRUCTOR;
  }

  // Text for the chat

  private static final class Builder {
    final StringBuilder text = new StringBuilder();
    final List<TdApi.TextEntity> entities = new ArrayList<>();

    void paragraph (@Nullable String s, boolean bold) {
      if (s == null || (s = s.trim()).isEmpty() || text.length() >= MAX_CHAT_TEXT) {
        return;
      }
      if (text.length() > 0) {
        text.append("\n\n");
      }
      int start = text.length();
      text.append(s);
      if (bold) {
        entities.add(new TdApi.TextEntity(start, s.length(), new TdApi.TextEntityTypeBold()));
      }
    }
  }

  public static TdApi.FormattedText toText (@Nullable TdApi.RichMessage message) {
    Builder b = new Builder();
    if (message != null && message.blocks != null) {
      for (TdApi.PageBlock block : message.blocks) {
        append(b, block, 0);
      }
    }
    String s = b.text.toString();
    if (s.length() > MAX_CHAT_TEXT) {
      s = s.substring(0, MAX_CHAT_TEXT) + "…";
    }
    List<TdApi.TextEntity> entities = new ArrayList<>();
    for (TdApi.TextEntity e : b.entities) {
      if (e.offset + e.length <= s.length()) {
        entities.add(e);
      }
    }
    return new TdApi.FormattedText(s, entities.toArray(new TdApi.TextEntity[0]));
  }

  private static boolean isHeading (TdApi.PageBlock block) {
    switch (block.getConstructor()) {
      case TdApi.PageBlockTitle.CONSTRUCTOR:
      case TdApi.PageBlockHeader.CONSTRUCTOR:
      case TdApi.PageBlockSubheader.CONSTRUCTOR:
      case TdApi.PageBlockSectionHeading.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  /**
   * Walks any block generically (all RichText, caption, list item and nested block fields), so
   * block types added to TDLib later still show their text.
   */
  private static void append (Builder b, @Nullable Object block, int depth) {
    if (block == null || depth > 6) {
      return;
    }
    boolean heading = block instanceof TdApi.PageBlock && isHeading((TdApi.PageBlock) block);
    for (Field field : block.getClass().getFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Object value;
      try {
        value = field.get(block);
      } catch (IllegalAccessException e) {
        continue;
      }
      if (value instanceof TdApi.RichText) {
        b.paragraph(TD.getText((TdApi.RichText) value), heading);
      } else if (value instanceof TdApi.PageBlockCaption) {
        TdApi.PageBlockCaption caption = (TdApi.PageBlockCaption) value;
        b.paragraph(TD.getText(caption.text), false);
      } else if (value instanceof TdApi.PageBlock) {
        append(b, value, depth + 1);
      } else if (value instanceof Object[]) {
        for (Object item : (Object[]) value) {
          if (item instanceof TdApi.PageBlock || item instanceof TdApi.PageBlockListItem ||
            item instanceof TdApi.PageBlockTableCell || item instanceof TdApi.RichText) {
            if (item instanceof TdApi.RichText) {
              b.paragraph(TD.getText((TdApi.RichText) item), false);
            } else {
              append(b, item, depth + 1);
            }
          } else if (item instanceof Object[]) {
            for (Object cell : (Object[]) item) {
              append(b, cell, depth + 1);
            }
          }
        }
      }
    }
  }

  // Full post

  public static void open (@NonNull ViewController<?> c, @NonNull TdApi.Message message) {
    if (!isRichMessage(message)) {
      return;
    }
    TdApi.RichMessage rich = ((TdApi.MessageRichMessage) message.content).message;
    Tdlib tdlib = c.tdlib();
    if (rich.isFull) {
      show(c, message, rich);
    } else {
      tdlib.send(new TdApi.GetFullRichMessage(message.chatId, message.id), (full, error) -> UI.post(() -> {
        if (full != null) {
          show(c, message, full);
        } else {
          UI.showToast(Lang.getString(R.string.RichMessageOpenFailed, TD.toErrorString(error)), Toast.LENGTH_LONG);
        }
      }));
    }
  }

  private static void show (ViewController<?> c, TdApi.Message message, TdApi.RichMessage rich) {
    Tdlib tdlib = c.tdlib();
    String title = tdlib.chatTitle(message.chatId);
    String username = tdlib.chatUsername(message.chatId);
    String url = username != null ? "https://t.me/" + username + "/" + (message.id >> 20) : "";
    TdApi.LinkPreview preview = new TdApi.LinkPreview(url, url, title, title, null, null, null,
      false, false, false, false, false, Config.SUPPORTED_INSTANT_VIEW_VERSION);
    TdApi.WebPageInstantView instantView = new TdApi.WebPageInstantView(rich.blocks, 0, Config.SUPPORTED_INSTANT_VIEW_VERSION, rich.isRtl, true, null);
    InstantViewController controller = new InstantViewController(c.context(), tdlib);
    controller.setArguments(new InstantViewController.Args(preview, instantView, null));
    try {
      controller.show();
    } catch (Throwable t) {
      Log.w("TGx101: can't show rich message", t);
      controller.destroy();
      UI.showToast(R.string.RichMessageOpenUnsupported, Toast.LENGTH_LONG);
    }
  }
}
