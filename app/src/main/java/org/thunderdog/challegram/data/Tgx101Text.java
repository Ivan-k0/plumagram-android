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
package org.thunderdog.challegram.data;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101: hides the "Subscribe" line that channels append to every post.
 *
 * Only the displayed text changes (copy, edit and translation still see the original).
 * A line is removed only when it is the last line of the post, consists of one link
 * (plus emoji/punctuation) and that link points to the same channel.
 */
public final class Tgx101Text {
  private Tgx101Text () { }

  public static @Nullable TdApi.FormattedText displayText (@NonNull TGMessage msg, @Nullable TdApi.FormattedText text) {
    if (text == null || text.text == null || text.entities == null || text.entities.length == 0 ||
      !msg.isChannel() || !Settings.instance().hideChannelSubscribeLink()) {
      return text;
    }
    try {
      TdApi.FormattedText stripped = stripOwnChannelLink(msg.tdlib(), msg.getChatId(), text);
      return stripped != null ? stripped : text;
    } catch (Throwable t) {
      // Never break message rendering because of this cosmetic feature.
      return text;
    }
  }

  private static @Nullable TdApi.FormattedText stripOwnChannelLink (Tdlib tdlib, long chatId, TdApi.FormattedText text) {
    String s = text.text;
    int end = s.length();
    while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
      end--;
    }
    int lineStart = s.lastIndexOf('\n', end - 1) + 1;
    if (lineStart <= 0) {
      // The link is the whole post: keep it.
      return null;
    }

    // The last line must be one link to this channel; anything else on it may only be emoji or punctuation.
    TdApi.Usernames usernames = tdlib.chatUsernames(chatId);
    boolean hasOwnLink = false;
    boolean[] covered = new boolean[end - lineStart];
    for (TdApi.TextEntity entity : text.entities) {
      int from = Math.max(entity.offset, lineStart), to = Math.min(entity.offset + entity.length, end);
      if (from >= to) {
        continue;
      }
      String target = linkTarget(s, entity);
      if (target == null) {
        continue; // formatting (bold, italic, …) is fine
      }
      if (!pointsToChannel(target, usernames)) {
        return null; // a link somewhere else: this is real content
      }
      hasOwnLink = true;
      for (int i = from; i < to; i++) {
        covered[i - lineStart] = true;
      }
    }
    if (!hasOwnLink) {
      return null;
    }
    for (int i = lineStart; i < end; ) {
      int codePoint = s.codePointAt(i);
      if (!covered[i - lineStart] && Character.isLetterOrDigit(codePoint)) {
        return null;
      }
      i += Character.charCount(codePoint);
    }

    int cut = lineStart;
    while (cut > 0 && Character.isWhitespace(s.charAt(cut - 1))) {
      cut--;
    }
    if (cut == 0) {
      return null;
    }
    List<TdApi.TextEntity> entities = new ArrayList<>(text.entities.length);
    for (TdApi.TextEntity entity : text.entities) {
      if (entity.offset >= cut) {
        continue;
      }
      int length = Math.min(entity.length, cut - entity.offset);
      entities.add(new TdApi.TextEntity(entity.offset, length, entity.type));
    }
    return new TdApi.FormattedText(s.substring(0, cut), entities.toArray(new TdApi.TextEntity[0]));
  }

  private static @Nullable String linkTarget (String s, TdApi.TextEntity entity) {
    switch (entity.type.getConstructor()) {
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
        return ((TdApi.TextEntityTypeTextUrl) entity.type).url;
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
      case TdApi.TextEntityTypeMention.CONSTRUCTOR:
        return s.substring(entity.offset, Math.min(s.length(), entity.offset + entity.length));
      default:
        return null;
    }
  }

  private static boolean pointsToChannel (String target, @Nullable TdApi.Usernames usernames) {
    String username = null;
    String t = target.trim();
    if (t.startsWith("@")) {
      username = t.substring(1);
    } else {
      if (!t.contains("://")) {
        t = "https://" + t;
      }
      Uri uri = Uri.parse(t);
      String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
      if ("tg".equals(uri.getScheme())) {
        if ("resolve".equals(host)) {
          username = uri.getQueryParameter("domain");
        }
      } else if (host.equals("t.me") || host.equals("www.t.me") || host.equals("telegram.me") || host.equals("telegram.dog")) {
        List<String> segments = uri.getPathSegments();
        if (!segments.isEmpty()) {
          username = segments.get(0);
        }
      } else if (host.endsWith(".t.me")) {
        username = host.substring(0, host.length() - ".t.me".length());
      }
    }
    if (username == null || username.isEmpty() || usernames == null) {
      return false;
    }
    if (usernames.activeUsernames != null) {
      for (String u : usernames.activeUsernames) {
        if (username.equalsIgnoreCase(u)) {
          return true;
        }
      }
    }
    return usernames.editableUsername != null && username.equalsIgnoreCase(usernames.editableUsername);
  }
}
