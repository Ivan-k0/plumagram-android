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

  public static final int BAR_MESSAGE = 0, BAR_INPUT = 1, BAR_SWIPE = 2, BAR_DRAWER = 3, BAR_CHAT_MENU = 4, BAR_CAPSULE = 5;

  // TGx101 (user 2026-10-18 «давай меню дадим настраивать»): the bottom capsule's tabs after «Chats» (always first), up to
  // three, in the user's order; «Profile» (the active account, hold — switch accounts) is off until switched on
  public static final String TAB_CONTACTS = "contacts", TAB_CALLS = "calls", TAB_SETTINGS = "settings", TAB_PROFILE = "profile", TAB_GAZETA = "gazeta";
  public static final int CAPSULE_MAX_TABS = 3;
  private static final String[] CAPSULE_OFF_BY_DEFAULT = {TAB_PROFILE, TAB_GAZETA};

  // TGx101 (user 2026-10-12 00:15): the side drawer is editable — order and which items are shown
  public static final String DRAWER_CONTACTS = "contacts", DRAWER_CALLS = "calls", DRAWER_SAVED = "saved", DRAWER_SAVED_BY_CHATS = "savedByChats",
    DRAWER_SETTINGS = "settings", DRAWER_PROXY = "proxy", DRAWER_NIGHT = "night", DRAWER_STORY = "story",
    DRAWER_PEEK = "peek", // «Не подглядывай!» (user 2026-10-22)
    DRAWER_GAZETA = "gazeta"; // «Газета» (user 2026-10-27)
  // TGx101 (user 2026-10-12): the chat's ⋮ menu is editable too, with «Proxy» to add (off until switched on)
  public static final String CHAT_CALL = "call", CHAT_VIDEO_CALL = "videoCall", CHAT_SEARCH = "search", CHAT_DATE = "date",
    CHAT_SAVED_BY_CHATS = "savedByChats", CHAT_BACKGROUND = "background", CHAT_MUTE = "mute", CHAT_NOTIFICATIONS = "notifications",
    CHAT_TRANSLATE = "translate", CHAT_PINNED = "pinned", CHAT_LINKED = "linked", CHAT_SCHEDULED = "scheduled", CHAT_VOICE_CHAT = "voiceChat",
    CHAT_TOPICS = "topics", CHAT_REPORT = "report", CHAT_PASSCODE = "passcode", CHAT_PROXY = "proxy", CHAT_OTHER = "other",
    CHAT_MEDIA = "media", // «Медиа в этом канале» (user 2026-10-27)
    CHAT_LEAVE = "leave"; // TGx101 (user 2026-10-16): «Leave» / «Delete chat» on its own, not among «the rest»
  private static final String[] CHAT_OFF_BY_DEFAULT = {CHAT_PROXY};

  public static final String QUOTE = "quote", COPY = "copy", SELECT_ALL = "selectAll", EDITOR = "editor",
    CUT = "cut", PASTE = "paste", APPS = "apps";

  // TGx101 (user 2026-10-06): the swipe actions column, top to bottom; «Reply» is the plain swipe and can't be hidden
  public static final String SWIPE_PIN = "pin", SWIPE_TRANSLATE = "translate", SWIPE_SAVE = "save", SWIPE_FORWARD = "forward",
    SWIPE_REPLY = "reply", SWIPE_COPY = "copy", SWIPE_EDIT = "edit", SWIPE_SELECT = "select", SWIPE_DELETE = "delete";
  // TGx101 (user 2026-10-11): more actions for the column, off until switched on
  public static final String SWIPE_REACTION = "reaction", SWIPE_DELETE_ALL = "deleteAll", SWIPE_LINK = "link",
    SWIPE_SAVE_FILE = "saveFile", SWIPE_REPORT = "report";
  private static final String[] SWIPE_OFF_BY_DEFAULT = {SWIPE_REACTION, SWIPE_DELETE_ALL, SWIPE_LINK, SWIPE_SAVE_FILE, SWIPE_REPORT};
  static final String[][] KEYS = {
    {QUOTE, COPY, SELECT_ALL, EDITOR, APPS},
    {CUT, COPY, PASTE, SELECT_ALL, APPS},
    {SWIPE_PIN, SWIPE_TRANSLATE, SWIPE_SAVE, SWIPE_FORWARD, SWIPE_REPLY, SWIPE_COPY, SWIPE_EDIT, SWIPE_SELECT, SWIPE_DELETE,
      SWIPE_REACTION, SWIPE_DELETE_ALL, SWIPE_LINK, SWIPE_SAVE_FILE, SWIPE_REPORT},
    {DRAWER_GAZETA, DRAWER_STORY, DRAWER_CONTACTS, DRAWER_CALLS, DRAWER_SAVED, DRAWER_SAVED_BY_CHATS, DRAWER_SETTINGS, DRAWER_PROXY, DRAWER_NIGHT, DRAWER_PEEK},
    {CHAT_CALL, CHAT_VIDEO_CALL, CHAT_SEARCH, CHAT_DATE, CHAT_SAVED_BY_CHATS, CHAT_BACKGROUND, CHAT_MUTE, CHAT_NOTIFICATIONS, CHAT_MEDIA, CHAT_TRANSLATE,
      CHAT_PINNED, CHAT_LINKED, CHAT_SCHEDULED, CHAT_VOICE_CHAT, CHAT_TOPICS, CHAT_REPORT, CHAT_PASSCODE, CHAT_OTHER, CHAT_PROXY, CHAT_LEAVE},
    {TAB_GAZETA, TAB_CONTACTS, TAB_CALLS, TAB_SETTINGS, TAB_PROFILE}
  };

  public static int tabNameOf (String key) {
    switch (key) {
      case TAB_CONTACTS: return R.string.Contacts;
      case TAB_CALLS: return R.string.Calls;
      case TAB_PROFILE: return R.string.Tgx101TabProfile;
      case TAB_GAZETA: return R.string.Tgx101Gazeta;
      case TAB_SETTINGS: default: return R.string.Settings;
    }
  }

  public static int tabIconOf (String key) {
    switch (key) {
      case TAB_CONTACTS: return R.drawable.baseline_person_24;
      case TAB_CALLS: return R.drawable.baseline_phone_24;
      case TAB_PROFILE: return R.drawable.baseline_account_circle_24;
      case TAB_GAZETA: return R.drawable.tgx101_gazeta_24;
      case TAB_SETTINGS: default: return R.drawable.baseline_settings_24;
    }
  }

  private static int tabOf (String key) {
    switch (key) {
      case TAB_CONTACTS: return org.thunderdog.challegram.widget.Tgx101NavCapsule.TAB_CONTACTS;
      case TAB_CALLS: return org.thunderdog.challegram.widget.Tgx101NavCapsule.TAB_CALLS;
      case TAB_PROFILE: return org.thunderdog.challegram.widget.Tgx101NavCapsule.TAB_PROFILE;
      case TAB_GAZETA: return org.thunderdog.challegram.widget.Tgx101NavCapsule.TAB_GAZETA;
      case TAB_SETTINGS: default: return org.thunderdog.challegram.widget.Tgx101NavCapsule.TAB_SETTINGS;
    }
  }

  /** The capsule's tabs left to right: «Chats», then the switched-on ones (at most {@link #CAPSULE_MAX_TABS}) */
  public static int[] capsuleTabs () {
    List<Integer> tabs = new ArrayList<>();
    tabs.add(org.thunderdog.challegram.widget.Tgx101NavCapsule.TAB_CHATS);
    for (String entry : getOrder(BAR_CAPSULE)) {
      if (entry.startsWith("-") || tabs.size() > CAPSULE_MAX_TABS) continue;
      tabs.add(tabOf(entry));
    }
    int[] result = new int[tabs.size()];
    for (int i = 0; i < result.length; i++) result[i] = tabs.get(i);
    return result;
  }

  /** Bumped on every change of the capsule's tabs — the chat list rebuilds its capsule when it sees a new one */
  private static int capsuleVersion;

  public static int capsuleVersion () {
    return capsuleVersion;
  }

  public static int drawerIdOf (String key) {
    switch (key) {
      case DRAWER_GAZETA: return R.id.btn_tgx101Gazeta;
      case DRAWER_PEEK: return R.id.btn_tgx101Peek;
      case DRAWER_STORY: return R.id.btn_tgx101NewStory;
      case DRAWER_CONTACTS: return R.id.btn_contacts;
      case DRAWER_CALLS: return R.id.btn_calls;
      case DRAWER_SAVED: return R.id.btn_savedMessages;
      case DRAWER_SAVED_BY_CHATS: return R.id.btn_tgx101SavedByChats;
      case DRAWER_SETTINGS: return R.id.btn_settings;
      case DRAWER_PROXY: return R.id.btn_proxy;
      case DRAWER_NIGHT: default: return R.id.btn_night;
    }
  }

  public static int drawerNameOf (String key) {
    switch (key) {
      case DRAWER_GAZETA: return R.string.Tgx101Gazeta;
      case DRAWER_PEEK: return R.string.Tgx101Peek;
      case DRAWER_STORY: return R.string.Tgx101NewStory;
      case DRAWER_CONTACTS: return R.string.Contacts;
      case DRAWER_CALLS: return R.string.Calls;
      case DRAWER_SAVED: return R.string.SavedMessages;
      case DRAWER_SAVED_BY_CHATS: return R.string.Tgx101SavedByChats;
      case DRAWER_SETTINGS: return R.string.Settings;
      case DRAWER_PROXY: return R.string.Proxy;
      case DRAWER_NIGHT: default: return R.string.NightMode;
    }
  }

  public static int drawerIconOf (String key) {
    switch (key) {
      case DRAWER_GAZETA: return R.drawable.tgx101_gazeta_24;
      case DRAWER_PEEK: return org.thunderdog.challegram.data.Tgx101Peek.isOn() ? R.drawable.baseline_eye_off_24 : R.drawable.baseline_visibility_24;
      case DRAWER_STORY: return R.drawable.baseline_camera_alt_24;
      case DRAWER_CONTACTS: return R.drawable.baseline_perm_contact_calendar_24;
      case DRAWER_CALLS: return R.drawable.baseline_call_24;
      case DRAWER_SAVED: return R.drawable.baseline_bookmark_24;
      case DRAWER_SAVED_BY_CHATS: return R.drawable.baseline_format_list_bulleted_type_24;
      case DRAWER_SETTINGS: return R.drawable.baseline_settings_24;
      case DRAWER_PROXY: return R.drawable.baseline_security_24;
      case DRAWER_NIGHT: default: return R.drawable.baseline_brightness_2_24;
    }
  }

  /** The ⋮ menu entry an item id belongs to; ids it doesn't know (delete, leave, bot commands…) are «other» */
  public static String chatKeyOf (int id) {
    if (id == R.id.btn_phone_call) return CHAT_CALL;
    if (id == R.id.btn_tgx101VideoCall) return CHAT_VIDEO_CALL;
    if (id == R.id.btn_search) return CHAT_SEARCH;
    if (id == R.id.btn_tgx101JumpToDate) return CHAT_DATE;
    if (id == R.id.btn_tgx101SavedByChats) return CHAT_SAVED_BY_CHATS;
    if (id == R.id.btn_tgx101ChatBackground) return CHAT_BACKGROUND;
    if (id == R.id.btn_mute) return CHAT_MUTE;
    if (id == R.id.btn_tgx101ChatNotifications) return CHAT_NOTIFICATIONS;
    if (id == R.id.btn_tgx101ChatMedia) return CHAT_MEDIA;
    if (id == R.id.btn_translateWholeChat) return CHAT_TRANSLATE;
    if (id == R.id.btn_showPinnedMessage) return CHAT_PINNED;
    if (id == R.id.btn_openLinkedChat || id == R.id.btn_openDirectMessages) return CHAT_LINKED;
    if (id == R.id.btn_viewScheduled) return CHAT_SCHEDULED;
    if (id == R.id.btn_tgx101VoiceChat || id == R.id.btn_tgx101VoiceChatStart) return CHAT_VOICE_CHAT;
    if (id == R.id.btn_tgx101ShowTopics) return CHAT_TOPICS;
    if (id == R.id.btn_reportChat) return CHAT_REPORT;
    if (id == R.id.btn_setPasscode) return CHAT_PASSCODE;
    if (id == R.id.btn_tgx101ChatProxy) return CHAT_PROXY;
    if (id == R.id.btn_removeChatFromList || id == R.id.btn_removeChatFromListAndStop || id == R.id.btn_removeChatFromListOrClearHistory || id == R.id.btn_returnToChat) return CHAT_LEAVE; // «Join» stands where «Leave» would
    return CHAT_OTHER;
  }

  public static int chatNameOf (String key) {
    switch (key) {
      case CHAT_CALL: return R.string.Call;
      case CHAT_VIDEO_CALL: return R.string.Tgx101VideoCall;
      case CHAT_SEARCH: return R.string.Search;
      case CHAT_DATE: return R.string.Tgx101JumpToDate;
      case CHAT_SAVED_BY_CHATS: return R.string.Tgx101SavedByChats;
      case CHAT_BACKGROUND: return R.string.Tgx101ChatBackground;
      case CHAT_MUTE: return R.string.Tgx101ChatMenuMute;
      case CHAT_NOTIFICATIONS: return R.string.Notifications;
      case CHAT_MEDIA: return R.string.Tgx101ChatMedia;
      case CHAT_TRANSLATE: return R.string.Tgx101ChatMenuTranslate;
      case CHAT_PINNED: return R.string.PinnedMessage;
      case CHAT_LINKED: return R.string.Tgx101ChatMenuLinked;
      case CHAT_SCHEDULED: return R.string.ScheduledMessages;
      case CHAT_VOICE_CHAT: return R.string.Tgx101ChatMenuVoiceChat;
      case CHAT_TOPICS: return R.string.Tgx101TopicsShow;
      case CHAT_REPORT: return R.string.Report;
      case CHAT_PASSCODE: return R.string.PasscodeTitle;
      case CHAT_PROXY: return R.string.Proxy;
      case CHAT_LEAVE: return R.string.Tgx101ChatMenuLeave;
      case CHAT_OTHER: default: return R.string.Tgx101ChatMenuOther;
    }
  }

  public static int chatIconOf (String key) {
    switch (key) {
      case CHAT_CALL: return R.drawable.baseline_call_24;
      case CHAT_VIDEO_CALL: return R.drawable.baseline_videocam_24;
      case CHAT_SEARCH: return R.drawable.baseline_search_24;
      case CHAT_DATE: return R.drawable.baseline_date_range_24;
      case CHAT_SAVED_BY_CHATS: return R.drawable.baseline_format_list_bulleted_type_24;
      case CHAT_BACKGROUND: return R.drawable.baseline_image_24;
      case CHAT_MUTE: return R.drawable.baseline_notifications_off_24;
      case CHAT_NOTIFICATIONS: return R.drawable.baseline_notifications_24;
      case CHAT_MEDIA: return R.drawable.baseline_image_24;
      case CHAT_TRANSLATE: return R.drawable.baseline_translate_24;
      case CHAT_PINNED: return R.drawable.deproko_baseline_pin_24;
      case CHAT_LINKED: return R.drawable.baseline_forum_24;
      case CHAT_SCHEDULED: return R.drawable.baseline_date_range_24;
      case CHAT_VOICE_CHAT: return R.drawable.baseline_mic_24;
      case CHAT_TOPICS: return R.drawable.baseline_format_list_bulleted_type_24;
      case CHAT_REPORT: return R.drawable.baseline_report_24;
      case CHAT_PASSCODE: return R.drawable.baseline_lock_24;
      case CHAT_PROXY: return R.drawable.baseline_security_24;
      case CHAT_OTHER: default: return R.drawable.baseline_more_horiz_24;
    }
  }

  /** Sorts the ⋮ menu's (id, title) pairs into the user's order and drops the hidden entries */
  public static void applyToChatMenu (me.vkryl.core.collection.IntList ids, org.thunderdog.challegram.util.StringList strings) {
    List<String> order = getOrder(BAR_CHAT_MENU);
    int n = ids.size();
    int[] srcIds = ids.get().clone(); // a copy: IntList.get() hands out its own array, which the appends below overwrite (log 2026-10-28 01:27: «media true → false»)
    String[] srcStrings = strings.get();
    ids.clear();
    strings.clear();
    // (user 2026-10-21 «как переключить назад?»): Saved Messages' «as chats» is always there and first, as in the
    // official app — even if hidden in an older ⋮ setup
    for (int i = 0; i < n; i++) {
      if (srcIds[i] == R.id.btn_tgx101SavedByChats) {
        ids.append(srcIds[i]);
        strings.append(srcStrings[i]);
      }
    }
    for (String entry : order) {
      if (entry.startsWith("-")) continue;
      if (CHAT_SAVED_BY_CHATS.equals(entry)) continue;
      for (int i = 0; i < n; i++) {
        if (chatKeyOf(srcIds[i]).equals(entry)) {
          ids.append(srcIds[i]);
          strings.append(srcStrings[i]);
        }
      }
    }
  }

  public static int swipeNameOf (String key) {
    switch (key) {
      case SWIPE_PIN: return R.string.MessagePin;
      case SWIPE_TRANSLATE: return R.string.Translate;
      case SWIPE_SAVE: return R.string.Tgx101SwipeSave;
      case SWIPE_FORWARD: return R.string.Share;
      case SWIPE_REPLY: return R.string.Reply;
      case SWIPE_COPY: return R.string.Copy;
      case SWIPE_EDIT: return R.string.edit;
      case SWIPE_SELECT: return R.string.Select;
      case SWIPE_REACTION: return R.string.Tgx101SwipeReaction;
      case SWIPE_DELETE_ALL: return R.string.Tgx101DeleteForAll;
      case SWIPE_LINK: return R.string.CopyLink;
      case SWIPE_SAVE_FILE: return R.string.Tgx101SwipeSaveFile;
      case SWIPE_REPORT: return R.string.MessageReport;
      case SWIPE_DELETE: default: return R.string.Delete;
    }
  }

  public static int swipeIconOf (String key) {
    switch (key) {
      case SWIPE_PIN: return R.drawable.deproko_baseline_pin_24;
      case SWIPE_TRANSLATE: return R.drawable.baseline_translate_24;
      case SWIPE_SAVE: return R.drawable.baseline_bookmark_24;
      case SWIPE_FORWARD: return R.drawable.baseline_forward_24;
      case SWIPE_REPLY: return R.drawable.baseline_reply_24;
      case SWIPE_COPY: return R.drawable.baseline_content_copy_24;
      case SWIPE_EDIT: return R.drawable.baseline_edit_24;
      case SWIPE_SELECT: return R.drawable.baseline_playlist_add_check_24;
      case SWIPE_REACTION: return R.drawable.baseline_favorite_24;
      case SWIPE_DELETE_ALL: return R.drawable.baseline_delete_forever_24;
      case SWIPE_LINK: return R.drawable.baseline_link_24;
      case SWIPE_SAVE_FILE: return R.drawable.baseline_file_download_24;
      case SWIPE_REPORT: return R.drawable.baseline_report_24;
      case SWIPE_DELETE: default: return R.drawable.baseline_delete_24;
    }
  }

  public static int nameOf (String key) {
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

  public static int iconOf (String key) {
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
        boolean off = (bar == BAR_SWIPE && indexOf(SWIPE_OFF_BY_DEFAULT, key) != -1) || (bar == BAR_CHAT_MENU && indexOf(CHAT_OFF_BY_DEFAULT, key) != -1)
          || (bar == BAR_CAPSULE && indexOf(CAPSULE_OFF_BY_DEFAULT, key) != -1);
        result.add(off ? "-" + key : key);
      }
    }
    return result;
  }

  public static void setOrder (int bar, List<String> order) {
    Settings.instance().setTgx101BarOrder(bar, order != null ? android.text.TextUtils.join(",", order) : null);
    if (bar == BAR_CAPSULE) capsuleVersion++;
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
