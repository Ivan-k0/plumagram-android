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
package org.thunderdog.challegram.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.vkryl.leveldb.LevelDB;

/**
 * TGx101 (user 2026-10-16, approved mockup «Копия настроек», variant В): the app's settings to a «.plumagram» file and
 * back. The file is JSON: the settings database's values byte for byte (base64 — the database doesn't keep their types)
 * for the keys that are settings, never passcode, sign-in, push or proxy data. What belongs to one account (Saved
 * folders and tags, an account's own theme) carries that account's user id and comes back to the same account only.
 */
public final class Tgx101SettingsBackup {
  public static final String EXTENSION = "plumagram";
  private static final String FORMAT_KEY = "plumagram_settings";
  private static final int FORMAT = 1;

  public static final int PART_APPEARANCE = 1, PART_MENUS = 1 << 1, PART_OTHER = 1 << 2, PART_SAVED = 1 << 3;
  public static final int PART_ALL = PART_APPEARANCE | PART_MENUS | PART_OTHER | PART_SAVED;
  public static final int[] PARTS = {PART_APPEARANCE, PART_MENUS, PART_OTHER, PART_SAVED};

  /** Settings kept outside the settings database (SharedPreferences files), all of them part «other» */
  private static final String[] PREFS_FILES = {"tgx101_stories"};

  private static final String[] INCLUDE = {
    "settings_", "tgx101_", "theme", "night_lux", "wallpaper", "emoji_", "quick_reaction", "big_reactions_",
    "playback_speed", "pip_gravity", "player_flags", "preferred_audio_mode", "map_type"
  };
  private static final String[] EXCLUDE = {
    "settings_authentication_token", "settings_tdlib_", "settings_language_code", "settings_emoji_installed_",
    "settings_emoji_pack", "settings_tutorial", "settings_theme_position", "tgx101_backup_"
  };
  private static final String[] NEVER_WORDS = {"token", "secret", "password", "passcode", "hash", "pc_"};

  private static final Pattern ACCOUNT_PREFIX = Pattern.compile("^(\\d+)_(.+)$");
  private static final String SAVED_PREFIX = "tgx101_saved_store_";

  // What a key is

  private static final class KeyInfo {
    final int accountId; // -1: the app's, not an account's
    final String base;
    final int part;

    KeyInfo (int accountId, String base, int part) {
      this.accountId = accountId;
      this.base = base;
      this.part = part;
    }
  }

  /** null: not a setting, or never leaves the phone */
  private static @Nullable KeyInfo describe (String key) {
    String lower = key.toLowerCase(java.util.Locale.ROOT);
    for (String w : NEVER_WORDS) if (lower.contains(w)) return null;
    int accountId = -1;
    String base = key;
    if (key.startsWith(SAVED_PREFIX)) {
      try {
        accountId = Integer.parseInt(key.substring(SAVED_PREFIX.length()));
      } catch (NumberFormatException e) {
        return null;
      }
      return new KeyInfo(accountId, SAVED_PREFIX, PART_SAVED);
    }
    Matcher m = ACCOUNT_PREFIX.matcher(key);
    if (m.matches()) {
      accountId = Integer.parseInt(m.group(1));
      base = m.group(2);
      if (!base.startsWith("settings_")) return null; // an account's settings; its other keys are notification / push state
    }
    if (!startsWithAny(base, INCLUDE) || startsWithAny(base, EXCLUDE)) return null;
    return new KeyInfo(accountId, base, partOf(base));
  }

  private static int partOf (String base) {
    if (base.startsWith("theme") || base.startsWith("settings_theme") || base.startsWith("settings_night") || base.startsWith("night_lux")
      || base.startsWith("settings_chat_style") || base.startsWith("settings_global_theme") || base.startsWith("wallpaper")
      || base.startsWith("settings_font_size") || base.startsWith("settings_big_emoji_size") || base.startsWith("settings_color_format")
      || base.startsWith("settings_chat_list_mode") || base.startsWith("settings_folders_style") || base.startsWith("tgx101_app_font")
      || base.startsWith("tgx101_font_") || base.startsWith("tgx101_text_weight") || base.startsWith("tgx101_chat_list_text_size")
      || base.startsWith("tgx101_ui_") || base.startsWith("tgx101_input_s") || base.startsWith("tgx101_channel_plain_style")
      || base.startsWith("tgx101_app_icon")) {
      return PART_APPEARANCE;
    }
    if (base.contains("menu") || base.contains("order") || base.contains("swipe") || base.contains("haptic") || base.contains("tap_")
      || base.contains("gesture") || base.contains("capsule") || base.contains("quick_calls") || base.contains("pull") || base.contains("long_press")
      || base.startsWith("tgx101_hide_") || base.contains("floating_input") || base.contains("bottom_gap")) {
      return PART_MENUS;
    }
    return PART_OTHER;
  }

  private static boolean startsWithAny (String s, String[] prefixes) {
    for (String p : prefixes) if (s.startsWith(p)) return true;
    return false;
  }

  private static long userIdOf (int accountId) {
    try {
      TdlibManager m = TdlibManager.instance();
      for (TdlibAccount account : m) {
        if (account.id == accountId) return account.getKnownUserId();
      }
    } catch (Throwable ignored) { }
    return 0;
  }

  /**
   * Every entry of the settings store. (user 2026-10-16 «крашится»: LevelDB refuses an empty prefix — «prefix.empty()»
   * crashed the app on «Save») The keys are ASCII: one search per first character covers them all
   */
  private static Iterable<LevelDB.Entry> allEntries (LevelDB pmc) {
    return () -> new Iterator<LevelDB.Entry>() {
      private char next = ' ';
      private @androidx.annotation.Nullable Iterator<LevelDB.Entry> current;

      @Override
      public boolean hasNext () {
        while (current == null || !current.hasNext()) {
          if (next > '~') return false;
          current = pmc.find(String.valueOf(next++)).iterator();
        }
        return true;
      }

      @Override
      public LevelDB.Entry next () {
        if (!hasNext()) throw new java.util.NoSuchElementException();
        return current.next();
      }
    };
  }

  // Save

  /** The settings as the file's text */
  public static String export (int parts) throws Exception {
    LevelDB pmc = Settings.instance().pmc();
    JSONObject o = new JSONObject();
    o.put(FORMAT_KEY, FORMAT);
    o.put("app", BuildConfig.VERSION_NAME);
    o.put("date", System.currentTimeMillis() / 1000L);
    o.put("parts", parts);
    JSONObject values = new JSONObject();
    Set<Integer> accounts = new HashSet<>();
    int seen = 0;
    StringBuilder skipped = new StringBuilder(); // diag: names only, to see what the filter leaves out
    for (LevelDB.Entry entry : allEntries(pmc)) {
      String key = entry.key();
      seen++;
      KeyInfo info = describe(key);
      if (info == null && skipped.length() < 6000) skipped.append(key, 0, Math.min(key.length(), 40)).append(' ');
      if (info == null || (info.part & parts) == 0) continue;
      if (info.accountId >= 0) {
        if (userIdOf(info.accountId) == 0) continue; // a signed-out slot
        accounts.add(info.accountId);
      }
      byte[] value = entry.asByteArray();
      values.put(key, value != null ? Base64.encodeToString(value, Base64.NO_WRAP) : "");
    }
    o.put("values", values);
    org.thunderdog.challegram.Tgx101Diag.mark("backup: " + seen + " keys in the store, " + values.length() + " taken; left out: " + skipped);
    JSONObject users = new JSONObject();
    for (int id : accounts) users.put(Integer.toString(id), userIdOf(id));
    o.put("accounts", users);
    if ((parts & PART_OTHER) != 0) {
      JSONObject prefs = new JSONObject();
      for (String name : PREFS_FILES) prefs.put(name, prefsToJson(AppContext.get().getSharedPreferences(name, Context.MODE_PRIVATE)));
      o.put("prefs", prefs);
    }
    return o.toString(1);
  }

  /** How much of each part is set on this phone now (for the «what goes in» list); Saved: the number of folders */
  public static Map<Integer, Integer> countHere () {
    Map<Integer, Integer> out = new HashMap<>();
    for (LevelDB.Entry entry : allEntries(Settings.instance().pmc())) {
      KeyInfo info = describe(entry.key());
      if (info == null) continue;
      if (info.accountId >= 0 && userIdOf(info.accountId) == 0) continue;
      int add = 1;
      if (info.part == PART_SAVED) {
        add = 0;
        try {
          JSONArray folders = new JSONObject(entry.asString()).optJSONArray("folders");
          if (folders != null) add = folders.length();
        } catch (Throwable ignored) { }
      }
      Integer c = out.get(info.part);
      out.put(info.part, c == null ? add : c + add);
    }
    return out;
  }

  private static JSONObject prefsToJson (SharedPreferences prefs) throws Exception {
    JSONObject out = new JSONObject();
    for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
      Object v = e.getValue();
      JSONObject item = new JSONObject();
      if (v instanceof Boolean) item.put("t", "b");
      else if (v instanceof Integer) item.put("t", "i");
      else if (v instanceof Long) item.put("t", "l");
      else if (v instanceof Float) item.put("t", "f");
      else if (v instanceof String) item.put("t", "s");
      else if (v instanceof Set) {
        item.put("t", "set");
        JSONArray a = new JSONArray();
        for (Object s : (Set<?>) v) a.put(String.valueOf(s));
        item.put("v", a);
        out.put(e.getKey(), item);
        continue;
      } else continue;
      item.put("v", v instanceof Float ? (double) (Float) v : v);
      out.put(e.getKey(), item);
    }
    return out;
  }

  // Read

  public static final class Backup {
    public final String appVersion;
    public final long date;
    public final int parts;
    /** settings in the file per part (Saved: the number of folders instead) */
    public final Map<Integer, Integer> counts = new LinkedHashMap<>();
    public int savedFolders, savedTags;
    /** accounts in the file (by user id) not signed in here — their Saved folders and own settings are skipped */
    public int missingAccounts;
    final JSONObject json;

    Backup (JSONObject json) {
      this.json = json;
      this.appVersion = json.optString("app", "");
      this.date = json.optLong("date");
      this.parts = json.optInt("parts", PART_ALL);
    }
  }

  /** null: not a PlumaGram settings file */
  public static @Nullable Backup read (String text) {
    try {
      JSONObject o = new JSONObject(text);
      if (o.optInt(FORMAT_KEY, 0) < 1) return null;
      Backup b = new Backup(o);
      JSONObject values = o.optJSONObject("values");
      Map<Integer, Integer> mapping = accountMapping(o);
      Set<Integer> missing = new HashSet<>();
      if (values != null) {
        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
          String key = keys.next();
          KeyInfo info = describe(key);
          if (info == null) continue;
          if (info.accountId >= 0 && !mapping.containsKey(info.accountId)) {
            missing.add(info.accountId);
            continue;
          }
          if (info.part == PART_SAVED) {
            try {
              JSONObject store = new JSONObject(values.getString(key).isEmpty() ? "{}" : decodeString(values.getString(key)));
              JSONArray folders = store.optJSONArray("folders");
              JSONObject tags = store.optJSONObject("tags");
              b.savedFolders += folders != null ? folders.length() : 0;
              b.savedTags += tags != null ? tags.length() : 0;
            } catch (Throwable ignored) { }
          }
          Integer c = b.counts.get(info.part);
          b.counts.put(info.part, c == null ? 1 : c + 1);
        }
      }
      JSONObject prefs = o.optJSONObject("prefs");
      if (prefs != null) {
        int n = 0;
        Iterator<String> files = prefs.keys();
        while (files.hasNext()) {
          JSONObject f = prefs.optJSONObject(files.next());
          if (f != null) n += f.length();
        }
        if (n > 0) {
          Integer c = b.counts.get(PART_OTHER);
          b.counts.put(PART_OTHER, (c == null ? 0 : c) + n);
        }
      }
      b.missingAccounts = missing.size();
      return b;
    } catch (Throwable t) {
      return null;
    }
  }

  /** A string value as the database keeps it: UTF-16 code units, little end first (Android's byte order) */
  private static String decodeString (String base64) {
    byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
    char[] chars = new char[bytes.length / 2];
    for (int i = 0; i < chars.length; i++) {
      chars[i] = (char) ((bytes[i * 2] & 0xff) | ((bytes[i * 2 + 1] & 0xff) << 8));
    }
    return new String(chars);
  }

  /** account number in the file → the same user's account number on this phone */
  private static Map<Integer, Integer> accountMapping (JSONObject o) {
    Map<Integer, Integer> out = new HashMap<>();
    JSONObject users = o.optJSONObject("accounts");
    if (users == null) return out;
    Iterator<String> keys = users.keys();
    while (keys.hasNext()) {
      String k = keys.next();
      long userId = users.optLong(k);
      if (userId == 0) continue;
      int here = -1;
      try {
        here = TdlibManager.instance().accountIdForUserId(userId, 0);
      } catch (Throwable ignored) { }
      if (here >= 0) {
        try {
          out.put(Integer.parseInt(k), here);
        } catch (NumberFormatException ignored) { }
      }
    }
    return out;
  }

  private static String keyHere (KeyInfo info, int accountHere) {
    if (info.accountId < 0) return info.base;
    if (info.part == PART_SAVED) return SAVED_PREFIX + accountHere;
    return accountHere != 0 ? accountHere + "_" + info.base : info.base;
  }

  // Restore

  /** Writes the file's settings over this phone's (the parts the file has; what is set here and not in the file goes
   *  back to its default). The app has to restart afterwards: the settings are read once at start. */
  public static void apply (Backup b) throws Exception {
    JSONObject o = b.json;
    Map<Integer, Integer> mapping = accountMapping(o);
    JSONObject values = o.optJSONObject("values");
    LevelDB pmc = Settings.instance().pmc();
    // 1. what this phone has for the same parts (and the accounts the file has) goes
    List<String> remove = new ArrayList<>();
    Set<Integer> accountsHere = new HashSet<>(mapping.values());
    for (LevelDB.Entry entry : allEntries(pmc)) {
      String key = entry.key();
      KeyInfo info = describe(key);
      if (info == null || (info.part & b.parts) == 0) continue;
      if (info.accountId >= 0 && !accountsHere.contains(info.accountId)) continue;
      remove.add(key);
    }
    pmc.edit();
    for (String key : remove) pmc.remove(key);
    // 2. the file's values
    if (values != null) {
      Iterator<String> keys = values.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        KeyInfo info = describe(key);
        if (info == null) continue;
        int accountHere = -1;
        if (info.accountId >= 0) {
          Integer here = mapping.get(info.accountId);
          if (here == null) continue;
          accountHere = here;
        }
        String raw = values.getString(key);
        byte[] bytes = raw.isEmpty() ? new byte[0] : Base64.decode(raw, Base64.NO_WRAP);
        String target = keyHere(info, accountHere);
        if (bytes.length == 0) {
          pmc.putVoid(target);
        } else {
          pmc.putByteArray(target, bytes);
        }
      }
    }
    pmc.apply();
    pmc.flush();
    JSONObject prefs = o.optJSONObject("prefs");
    if (prefs != null) {
      Iterator<String> files = prefs.keys();
      while (files.hasNext()) {
        String name = files.next();
        boolean known = false;
        for (String n : PREFS_FILES) known |= n.equals(name);
        if (!known) continue;
        jsonToPrefs(prefs.getJSONObject(name), AppContext.get().getSharedPreferences(name, Context.MODE_PRIVATE));
      }
    }
  }

  private static void jsonToPrefs (JSONObject json, SharedPreferences prefs) throws Exception {
    SharedPreferences.Editor e = prefs.edit().clear();
    Iterator<String> keys = json.keys();
    while (keys.hasNext()) {
      String k = keys.next();
      JSONObject item = json.getJSONObject(k);
      switch (item.optString("t")) {
        case "b": e.putBoolean(k, item.getBoolean("v")); break;
        case "i": e.putInt(k, item.getInt("v")); break;
        case "l": e.putLong(k, item.getLong("v")); break;
        case "f": e.putFloat(k, (float) item.getDouble("v")); break;
        case "s": e.putString(k, item.getString("v")); break;
        case "set": {
          Set<String> set = new HashSet<>();
          JSONArray a = item.getJSONArray("v");
          for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
          e.putStringSet(k, set);
          break;
        }
      }
    }
    e.commit();
  }
}
