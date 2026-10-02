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

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101: order and visibility of the text selection bar's top row — in a message (Quote, Copy, Select all,
 * Editor, other apps) and in the message input (Cut, Copy, Paste, Select all, other apps). Other apps' items
 * (Translate, Search…) move and hide as one group. Stored as "key,-key,…" (minus = hidden).
 */
public final class Tgx101BarOrder {
  private Tgx101BarOrder () { }

  public static final int BAR_MESSAGE = 0, BAR_INPUT = 1;

  public static final String QUOTE = "quote", COPY = "copy", SELECT_ALL = "selectAll", EDITOR = "editor",
    CUT = "cut", PASTE = "paste", APPS = "apps";

  static final String[][] KEYS = {
    {QUOTE, COPY, SELECT_ALL, EDITOR, APPS},
    {CUT, COPY, PASTE, SELECT_ALL, APPS}
  };

  static int nameOf (String key) {
    switch (key) {
      case QUOTE: return R.string.Tgx101EditorQuote;
      case COPY: return R.string.Copy;
      case SELECT_ALL: return R.string.Tgx101SelectAllFull;
      case EDITOR: return R.string.Tgx101EditorMenu;
      case CUT: return R.string.Tgx101BarCut;
      case PASTE: return R.string.Tgx101BarPaste;
      case APPS: default: return R.string.Tgx101BarApps;
    }
  }

  static int iconOf (String key) {
    switch (key) {
      case QUOTE: return R.drawable.baseline_format_quote_close_24;
      case COPY: return R.drawable.baseline_content_copy_24;
      case SELECT_ALL: return R.drawable.baseline_done_all_24;
      case EDITOR: return R.drawable.baseline_format_text_24;
      case CUT: return R.drawable.baseline_content_cut_24;
      case PASTE: return R.drawable.baseline_content_paste_24;
      case APPS: default: return R.drawable.baseline_apps_24;
    }
  }

  /** Keys in the user's order; hidden ones start with "-" */
  public static List<String> getOrder (int bar) {
    List<String> result = new ArrayList<>();
    String saved = Settings.instance().getTgx101BarOrder(bar);
    if (saved != null) {
      for (String entry : saved.split(",")) {
        String key = entry.startsWith("-") ? entry.substring(1) : entry;
        if (indexOf(KEYS[bar], key) != -1 && !result.contains(key) && !result.contains("-" + key)) {
          result.add(entry);
        }
      }
    }
    for (String key : KEYS[bar]) {
      if (!result.contains(key) && !result.contains("-" + key)) {
        result.add(key);
      }
    }
    return result;
  }

  static void setOrder (int bar, List<String> order) {
    Settings.instance().setTgx101BarOrder(bar, order != null ? android.text.TextUtils.join(",", order) : null);
  }

  private static int indexOf (String[] keys, String key) {
    for (int i = 0; i < keys.length; i++) {
      if (keys[i].equals(key)) return i;
    }
    return -1;
  }

  public interface KeyOf<T> {
    String keyOf (T item);
  }

  /** Sorts the bar's entries into the user's order and drops the hidden ones; entries of one key keep their order */
  public static <T> List<T> apply (int bar, List<T> entries, KeyOf<T> keyOf) {
    List<String> order = getOrder(bar);
    List<T> result = new ArrayList<>();
    for (String entry : order) {
      if (entry.startsWith("-")) continue;
      for (T item : entries) {
        String key = keyOf.keyOf(item);
        if (entry.equals(indexOf(KEYS[bar], key) != -1 ? key : APPS)) {
          result.add(item);
        }
      }
    }
    return result;
  }
}
