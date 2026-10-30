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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * TGx101 (user 2026-10-27, «Каналы» п.5): media of one channel apart from the general rules — photo download
 * (default / always / never), video download (default / always / Wi‑Fi only / never) with a size limit, and autoplay
 * of videos and GIFs (default / on / off). Plus the general autoplay: everywhere / only in the chosen channels (those
 * with «on» here) / nowhere. Kept with the app's settings, so it goes into the settings copy.
 */
public final class Tgx101ChatMedia {
  private Tgx101ChatMedia () { }

  public static final int DEFAULT = 0;
  public static final int PHOTO_ALWAYS = 1, PHOTO_NEVER = 2;
  public static final int VIDEO_ALWAYS = 1, VIDEO_WIFI = 2, VIDEO_NEVER = 3;
  public static final int AUTOPLAY_ON = 1, AUTOPLAY_OFF = 2;
  /** video size limits, MB; 0 — none */
  public static final int[] LIMITS_MB = {0, 10, 50, 100};

  public static final int MODE_EVERYWHERE = 0, MODE_CHOSEN = 1, MODE_NOWHERE = 2;

  private static final String CHAT_KEY_PREFIX = "tgx101_chat_media_";

  private static String key (Tdlib tdlib, long chatId) {
    return CHAT_KEY_PREFIX + tdlib.id() + "_" + chatId;
  }

  /** The chats where autoplay is «On» of their own — for «Only chosen» in the settings (user 2026-10-31) */
  public static java.util.List<String> chosenTitles (Tdlib tdlib) {
    java.util.List<String> titles = new java.util.ArrayList<>();
    String prefix = CHAT_KEY_PREFIX + tdlib.id() + "_";
    try {
      for (me.vkryl.leveldb.LevelDB.Entry entry : Settings.instance().pmc().find(prefix)) {
        if (autoplayBits(entry.asInt()) != AUTOPLAY_ON) continue;
        long chatId = Long.parseLong(entry.key().substring(prefix.length()));
        String title = tdlib.chatTitle(chatId);
        titles.add(title != null && !title.isEmpty() ? title : String.valueOf(chatId));
      }
    } catch (Throwable ignored) { }
    return titles;
  }

  // TGx101 (user 2026-10-28): the same rules for all channels / groups / private chats at once (MagiX → «Channels»);
  // a chat's own rules come first
  public static final int TYPE_CHANNELS = 0, TYPE_GROUPS = 1, TYPE_PRIVATE = 2;

  private static String typeKey (int type) {
    return "tgx101_type_media_" + type;
  }

  private static int typeOf (Tdlib tdlib, long chatId) {
    if (tdlib.isChannel(chatId)) return TYPE_CHANNELS;
    if (tdlib.isMultiChat(chatId)) return TYPE_GROUPS;
    return TYPE_PRIVATE;
  }

  /** bits: photo 0..3, video 4..7, limit index 8..11, autoplay 12..15 */
  private static int raw (String key) {
    return Settings.instance().pmc().getInt(key, 0);
  }

  private static void put (String key, int photo, int video, int limit, int autoplay) {
    int value = (photo & 0xf) | ((video & 0xf) << 4) | ((limit & 0xf) << 8) | ((autoplay & 0xf) << 12);
    if (value == 0) Settings.instance().pmc().remove(key);
    else Settings.instance().pmc().putInt(key, value);
  }

  /** the chat's own rules, or its kind's when it has none */
  private static int get (Tdlib tdlib, long chatId) {
    int own = raw(key(tdlib, chatId));
    return own != 0 ? own : raw(typeKey(typeOf(tdlib, chatId)));
  }

  private static int photoOf (int v) { return v & 0xf; }
  private static int videoOf (int v) { return (v >> 4) & 0xf; }
  private static int limitOf (int v) { return Math.min(LIMITS_MB.length - 1, (v >> 8) & 0xf); }
  private static int autoplayBits (int v) { return (v >> 12) & 0xf; }

  public static int autoplayOf (Tdlib tdlib, long chatId) { return autoplayBits(get(tdlib, chatId)); }



  // ---- general autoplay ----

  public static int autoplayMode () {
    int mode = Settings.instance().pmc().getInt("tgx101_autoplay_mode", -1);
    if (mode == -1) mode = Settings.instance().needAutoplayGIFs() ? MODE_EVERYWHERE : MODE_NOWHERE;
    return mode;
  }

  public static void setAutoplayMode (int mode) {
    Settings.instance().pmc().putInt("tgx101_autoplay_mode", mode);
    Settings.instance().setAutoplayGIFs(mode == MODE_EVERYWHERE); // the viewer and everything else that asks the old switch
  }

  public static String autoplayModeName (int mode) {
    switch (mode) {
      case MODE_CHOSEN: return Lang.getString(R.string.Tgx101AutoplayChosen);
      case MODE_NOWHERE: return Lang.getString(R.string.Tgx101AutoplayNowhere);
      case MODE_EVERYWHERE: default: return Lang.getString(R.string.Tgx101AutoplayEverywhere);
    }
  }

  /** whether videos and GIFs of this chat play by themselves */
  public static boolean autoplay (@Nullable Tdlib tdlib, long chatId) {
    // (user 2026-10-31 «я выключил воспроизведение, но газета играет»): «Nowhere» means nowhere — a channel's own «On»
    // counts only with «Everywhere» or «Only chosen»
    if (autoplayMode() == MODE_NOWHERE) return false;
    if (tdlib != null && chatId != 0) {
      int own = autoplayOf(tdlib, chatId);
      if (own == AUTOPLAY_ON) return true;
      if (own == AUTOPLAY_OFF) return false;
    }
    int mode = Settings.instance().pmc().getInt("tgx101_autoplay_mode", -1);
    if (mode == -1) return Settings.instance().needAutoplayGIFs();
    return mode == MODE_EVERYWHERE;
  }

  /** the chat's own word on downloading this file: TRUE / FALSE, or null — the general rules decide */
  @Nullable
  public static Boolean download (Tdlib tdlib, long chatId, int fileType, long fileSize) {
    if (chatId == 0) return null;
    int value = get(tdlib, chatId);
    if (value == 0) return null;
    if (fileType == TdlibFilesManager.DOWNLOAD_FLAG_PHOTO) {
      int photo = value & 0xf;
      return photo == PHOTO_ALWAYS ? Boolean.TRUE : photo == PHOTO_NEVER ? Boolean.FALSE : null;
    }
    if (fileType == TdlibFilesManager.DOWNLOAD_FLAG_VIDEO || fileType == TdlibFilesManager.DOWNLOAD_FLAG_GIF || fileType == TdlibFilesManager.DOWNLOAD_FLAG_VIDEO_NOTE) {
      int video = (value >> 4) & 0xf;
      if (video == DEFAULT) return null;
      if (video == VIDEO_NEVER) return Boolean.FALSE;
      int limitMb = LIMITS_MB[Math.min(LIMITS_MB.length - 1, (value >> 8) & 0xf)];
      if (limitMb > 0 && fileSize > (long) limitMb * 1024 * 1024) return Boolean.FALSE;
      if (video == VIDEO_WIFI && tdlib.context().watchDog().getConnectionType() != org.thunderdog.challegram.core.WatchDog.CONNECTION_TYPE_WIFI) return Boolean.FALSE;
      return Boolean.TRUE;
    }
    return null;
  }

  // ---- the sheet: ⋮ → «Media in this channel», or MagiX → «Channels» for a kind of chats ----

  public static void openSheet (ViewController<?> c, long chatId) {
    openSheet(c, key(c.tdlib(), chatId), Lang.getString(R.string.Tgx101ChatMediaHint, autoplayModeName(autoplayMode())));
  }

  /** MagiX → «Channels» → «Media in channels, groups and chats»: which kind first */
  public static void openTypes (ViewController<?> c) {
    int[] ids = {1, 2, 3};
    String[] names = {Lang.getString(R.string.Tgx101TypeMediaChannels), Lang.getString(R.string.Tgx101TypeMediaGroups), Lang.getString(R.string.Tgx101TypeMediaPrivate)};
    int[] icons = {R.drawable.baseline_bullhorn_24, R.drawable.baseline_group_24, R.drawable.baseline_person_24};
    c.showOptions(Lang.getString(R.string.Tgx101TypeMediaHint), ids, names, null, icons, (v, id) -> {
      openSheet(c, typeKey(id - 1), names[id - 1] + ". " + Lang.getString(R.string.Tgx101TypeMediaSheetHint));
      return true;
    });
  }

  private static void openSheet (ViewController<?> c, String key, String hint) {
    int value = raw(key);
    int photo = photoOf(value), video = videoOf(value), limit = limitOf(value), autoplay = autoplayBits(value);
    String[] titles = {
      Lang.getString(R.string.Tgx101ChatMediaPhoto) + ": " + photoName(photo),
      Lang.getString(R.string.Tgx101ChatMediaVideo) + ": " + videoName(video),
      Lang.getString(R.string.Tgx101ChatMediaLimit) + ": " + limitName(limit),
      Lang.getString(R.string.Tgx101ChatMediaAutoplay) + ": " + autoplayName(autoplay),
      Lang.getString(R.string.Tgx101ChatMediaReset)
    };
    int[] ids = {R.id.btn_tgx101ChatMediaPhoto, R.id.btn_tgx101ChatMediaVideo, R.id.btn_tgx101ChatMediaLimit, R.id.btn_tgx101ChatMediaAutoplay, R.id.btn_tgx101ChatMediaReset};
    int[] icons = {R.drawable.baseline_image_24, R.drawable.baseline_videocam_24, R.drawable.baseline_sd_storage_24, R.drawable.baseline_play_circle_filled_24_white, R.drawable.baseline_replay_24};
    int n = value != 0 ? 5 : 4;
    String[] t = new String[n];
    int[] i2 = new int[n], ic = new int[n];
    System.arraycopy(titles, 0, t, 0, n);
    System.arraycopy(ids, 0, i2, 0, n);
    System.arraycopy(icons, 0, ic, 0, n);
    c.showOptions(hint, i2, t, null, ic, (v, id) -> {
      if (id == R.id.btn_tgx101ChatMediaPhoto) {
        choose(c, R.string.Tgx101ChatMediaPhoto, new String[] {photoName(0), photoName(1), photoName(2)}, photo, which -> {
          put(key, which, video, limit, autoplay);
          openSheet(c, key, hint);
        });
      } else if (id == R.id.btn_tgx101ChatMediaVideo) {
        choose(c, R.string.Tgx101ChatMediaVideo, new String[] {videoName(0), videoName(1), videoName(2), videoName(3)}, video, which -> {
          put(key, photo, which, limit, autoplay);
          openSheet(c, key, hint);
        });
      } else if (id == R.id.btn_tgx101ChatMediaLimit) {
        String[] names = new String[LIMITS_MB.length];
        for (int k = 0; k < names.length; k++) names[k] = limitName(k);
        choose(c, R.string.Tgx101ChatMediaLimit, names, limit, which -> {
          put(key, photo, video, which, autoplay);
          openSheet(c, key, hint);
        });
      } else if (id == R.id.btn_tgx101ChatMediaAutoplay) {
        choose(c, R.string.Tgx101ChatMediaAutoplay, new String[] {autoplayName(0), autoplayName(1), autoplayName(2)}, autoplay, which -> {
          put(key, photo, video, limit, which);
          // (user 2026-10-31 «если я включаю через три точки, то настройки автоматом переключаются на выбранные»): «On»
          // in a chat switches the general rule to «Only chosen», from «Everywhere» too — the chosen ones play, and the
          // chat shows up in that setting's list
          if (which == AUTOPLAY_ON && autoplayMode() != MODE_CHOSEN && key.startsWith(CHAT_KEY_PREFIX)) {
            setAutoplayMode(MODE_CHOSEN);
            org.thunderdog.challegram.tool.UI.showToast(Lang.getString(R.string.Tgx101AutoplayVideos) + ": " + autoplayModeName(MODE_CHOSEN), android.widget.Toast.LENGTH_SHORT);
          }
          openSheet(c, key, hint);
        });
      } else if (id == R.id.btn_tgx101ChatMediaReset) {
        put(key, 0, 0, 0, 0);
      }
      return true;
    });
  }

  private interface Choice {
    void onChoice (int which);
  }

  private static void choose (ViewController<?> c, int titleRes, String[] names, int current, Choice choice) {
    int[] ids = new int[names.length];
    String[] titles = new String[names.length];
    int[] icons = new int[names.length];
    for (int i = 0; i < names.length; i++) {
      ids[i] = i + 1;
      titles[i] = names[i];
      icons[i] = i == current ? R.drawable.baseline_check_24 : 0;
    }
    c.showOptions(Lang.getString(titleRes), ids, titles, null, icons, (v, id) -> {
      choice.onChoice(id - 1);
      return true;
    });
  }

  private static String defaultName () {
    return Lang.getString(R.string.Tgx101ChatMediaDefault);
  }

  private static String photoName (int v) {
    switch (v) {
      case PHOTO_ALWAYS: return Lang.getString(R.string.Tgx101ChatMediaAlways);
      case PHOTO_NEVER: return Lang.getString(R.string.Tgx101ChatMediaNever);
      default: return defaultName();
    }
  }

  private static String videoName (int v) {
    switch (v) {
      case VIDEO_ALWAYS: return Lang.getString(R.string.Tgx101ChatMediaAlways);
      case VIDEO_WIFI: return Lang.getString(R.string.Tgx101ChatMediaWifi);
      case VIDEO_NEVER: return Lang.getString(R.string.Tgx101ChatMediaNever);
      default: return defaultName();
    }
  }

  private static String limitName (int index) {
    int mb = LIMITS_MB[Math.max(0, Math.min(LIMITS_MB.length - 1, index))];
    return mb == 0 ? Lang.getString(R.string.Tgx101ChatMediaNoLimit) : Lang.getString(R.string.Tgx101ChatMediaMb, mb);
  }

  private static String autoplayName (int v) {
    switch (v) {
      case AUTOPLAY_ON: return Lang.getString(R.string.Tgx101ChatMediaOn);
      case AUTOPLAY_OFF: return Lang.getString(R.string.Tgx101ChatMediaOff);
      default: return defaultName();
    }
  }
}
