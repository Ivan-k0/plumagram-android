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
package org.thunderdog.challegram.telegram;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TGx101: keeps message text in notifications short, so a long message with links doesn't push the
 * «Reply» / «Mark as read» buttons out of the pop-up. Line breaks become spaces, links are reduced
 * to their domain («youtube.com/…») and the text is cut at ~280 characters. The chat itself is unchanged.
 */
public final class Tgx101NotificationText {
  private Tgx101NotificationText () { }

  static final int MAX_LENGTH = 280;

  // scheme or www., a domain, an optional path; a bare "domain/path" counts too, a bare "file.txt" doesn't
  private static final Pattern LINK = Pattern.compile("(?i)\\b(https?://)?(www\\.)?((?:[a-z0-9-]+\\.)+[a-z]{2,})(/\\S*)?");

  public static CharSequence shorten (CharSequence text) {
    if (text == null || text.length() == 0) {
      return text;
    }
    String s = text.toString();
    boolean changed = false;

    if (s.indexOf('\n') != -1 || s.indexOf('\r') != -1) {
      s = s.replaceAll("\\s*[\\r\\n]+\\s*", " ").trim();
      changed = true;
    }

    Matcher m = LINK.matcher(s);
    StringBuffer b = null;
    while (m.find()) {
      boolean isLink = m.group(1) != null || m.group(2) != null || m.group(4) != null;
      if (!isLink) {
        continue;
      }
      String path = m.group(4);
      String shortLink = m.group(3) + (path != null && path.length() > 1 ? "/…" : "");
      if (b == null) b = new StringBuffer(s.length());
      m.appendReplacement(b, Matcher.quoteReplacement(shortLink));
    }
    if (b != null) {
      m.appendTail(b);
      s = b.toString();
      changed = true;
    }

    if (s.length() > MAX_LENGTH) {
      int cut = s.lastIndexOf(' ', MAX_LENGTH);
      if (cut < MAX_LENGTH * 2 / 3) {
        cut = MAX_LENGTH;
      }
      if (Character.isHighSurrogate(s.charAt(cut - 1))) {
        cut--; // don't split an emoji
      }
      s = s.substring(0, cut).trim() + "…";
      changed = true;
    }
    return changed ? s : text;
  }
}
