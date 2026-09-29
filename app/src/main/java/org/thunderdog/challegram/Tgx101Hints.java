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
package org.thunderdog.challegram;

import android.content.Context;
import android.content.SharedPreferences;

import org.thunderdog.challegram.tool.UI;

/**
 * TGx101: black tutorial hints ("Hold to record audio…", "Drag chat to reorder"…) are shown at most
 * twice each, then never again.
 */
public final class Tgx101Hints {
  private Tgx101Hints () { }

  public static final String HOLD_TO_RECORD = "hold_to_record";
  public static final String DRAG_CHATS = "drag_chats";
  public static final String CALL_EMOJI = "call_emoji";
  public static final String HOLD_MEDIA = "hold_media";
  public static final String HOLD_TO_SCHEDULE = "hold_to_schedule";

  private static final int MAX_SHOWS = 2;

  /** Counts one more show and returns whether the hint may be shown this time. */
  public static boolean take (String key) {
    SharedPreferences prefs = UI.getAppContext().getSharedPreferences("tgx101_hints", Context.MODE_PRIVATE);
    int shown = prefs.getInt(key, 0);
    if (shown >= MAX_SHOWS) {
      return false;
    }
    prefs.edit().putInt(key, shown + 1).apply();
    return true;
  }
}
