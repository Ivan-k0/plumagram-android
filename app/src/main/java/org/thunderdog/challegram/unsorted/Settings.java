/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 15/11/2016
 */
package org.thunderdog.challegram.unsorted;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.Gravity;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.drinkmore.Tracer;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.BiometricAuthentication;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.RecentEmoji;
import org.thunderdog.challegram.emoji.RecentInfo;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.PlaybackSpeedLayout;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.ChatFolderOptions;
import org.thunderdog.challegram.telegram.ChatFolderStyle;
import org.thunderdog.challegram.telegram.EmojiMediaType;
import org.thunderdog.challegram.telegram.SessionSnapshot;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationManager;
import org.thunderdog.challegram.telegram.TdlibNotificationUtils;
import org.thunderdog.challegram.telegram.TdlibProvider;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColors;
import org.thunderdog.challegram.theme.ThemeCustom;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeInfo;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.theme.ThemeProperties;
import org.thunderdog.challegram.theme.ThemeSet;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.AppBuildInfo;
import org.thunderdog.challegram.util.Crash;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.DeviceStorageError;
import org.thunderdog.challegram.util.DeviceTokenType;
import org.thunderdog.challegram.util.FeatureAvailability;
import org.thunderdog.challegram.util.StringList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.FileUtils;
import me.vkryl.core.ObjectUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.reference.ReferenceUtils;
import me.vkryl.core.unit.BitUnit;
import me.vkryl.core.unit.ByteUnit;
import me.vkryl.core.util.Blob;
import me.vkryl.core.util.BlobEntry;
import me.vkryl.leveldb.LevelDB;
import tgx.td.ChatId;
import tgx.td.MessageId;
import tgx.td.Td;
import tgx.td.TdConstants;

/**
 * All app-related settings.
 * <p>
 * SharedPreferences is no longer used at all for the following reasons:
 * 1. Application launch speed;
 * 2. Storage usage;
 * 3. OEM-specific bugs.
 */
@SuppressWarnings("deprecation")
public class Settings {
  private static final int LEGACY_VERSION_1 = 1; // Added video notes
  private static final int LEGACY_VERSION_2 = 2; // Turn on albums, if disabled
  private static final int LEGACY_VERSION_3 = 3; // Turn off quick share
  private static final int LEGACY_VERSION_4 = 4; // Migrate night mode setting
  private static final int LEGACY_VERSION_5 = 5; // Remove "record_id"
  private static final int LEGACY_VERSION_6 = 6; // Turn off quick share (again)
  private static final int LEGACY_VERSION_7 = 7; // Remove "settings_oreo_fix"
  private static final int LEGACY_VERSION_8 = 8; // Move push_user_id to push_user_ids
  private static final int LEGACY_VERSION_9 = 9; // Remove FLAG_OTHER_DISABLE_CALLS_PROXY
  private static final int LEGACY_VERSION = LEGACY_VERSION_9; // Do not change this value ever again. Use PMC_VERSION

  private static final int VERSION_10 = 10; // Turn on Reduce Motion by default
  private static final int VERSION_11 = 11; // Reset silent broadcast settings
  private static final int VERSION_12 = 12; // Move passcodeHash for MODE_FINGERPRINT to fingerprintHash
  private static final int VERSION_13 = 13; // Remove language unlock
  private static final int VERSION_14 = 14; // Auto-save edited photos to the gallery
  private static final int VERSION_15 = 15; // Auto-save edited photos to the gallery off
  private static final int VERSION_16 = 16; // Auto-save edited photos to the gallery off
  private static final int VERSION_17 = 17; // header
  private static final int VERSION_18 = 18; // Remove notifications stack
  private static final int VERSION_19 = 19; // New badge settings
  private static final int VERSION_20 = 20; // Moved TDLib log path
  private static final int VERSION_21 = 21; // Added FLAG_OTHER_HIDE_SECRET_CHATS
  private static final int VERSION_22 = 22; // Cleared KEY_RTL
  private static final int VERSION_23 = 23; // Removed KEY_PUSH_USER_IDS
  private static final int VERSION_24 = 24; // Dropped push registrations to re-register again all users
  private static final int VERSION_25 = 25; // added use system fonts setting
  private static final int VERSION_26 = 26; // added disable big emoji setting
  private static final int VERSION_27 = 27; // wallpapers -> backgrounds api
  private static final int VERSION_28 = 28; // wallpapers -> backgrounds api
  private static final int VERSION_29 = 29; // delete markdownMode
  private static final int VERSION_30 = 30; // delete prefer_legacy_api
  private static final int VERSION_31 = 31; // move photos & videos taken in secret chats from unsecure location
  private static final int VERSION_32 = 32; // Delete ZoomTables.data
  private static final int VERSION_33 = 33; // Add NIGHT_MODE_SYSTEM
  private static final int VERSION_34 = 34; // scrollToMessageId stack
  private static final int VERSION_35 = 35; // clear known conversions
  private static final int VERSION_36 = 36; // removed TON
  private static final int VERSION_37 = 37; // removed weird "wallpaper_" + file.remote.id unused legacy cache
  private static final int VERSION_38 = 38; // int32 -> int64
  private static final int VERSION_39 = 39; // drop all previously stored crashes
  private static final int VERSION_40 = 40; // drop legacy crash management ids
  private static final int VERSION_41 = 41; // clear all application log files
  private static final int VERSION_42 = 42; // drop __
  private static final int VERSION_43 = 43; // optimize recent custom emoji
  private static final int VERSION_44 = 44; // 8-bit -> 32-bit account flags
  private static final int VERSION_45 = 45; // Reset "Big emoji" setting to default
  private static final int VERSION_46 = 46; // Remove folders experimental setting
  private static final int VERSION_47 = 47; // Force reset released features list
  private static final int VERSION_48 = 48; // Force strong sensor, if user has it.
  private static final int VERSION = VERSION_48;

  private static final AtomicBoolean hasInstance = new AtomicBoolean(false);
  private static volatile Settings instance;

  public static Settings instance () {
    if (instance == null) {
      synchronized (Settings.class) {
        if (instance == null) {
          if (hasInstance.getAndSet(true))
            throw new AssertionError();
          instance = new Settings();
        }
      }
    }
    return instance;
  }

  private static final String KEY_VERSION = "version";
  private static final String KEY_FEATURES = "features";
  private static final String KEY_FEATURES_ADDED_NOTIFICATIONS = "features_new";
  private static final String KEY_FEATURES_REMOVED_NOTIFICATIONS = "features_gone";
  private static final String KEY_OTHER = "settings_other";
  private static final String KEY_OTHER_NEW = "settings_other2";
  private static final String KEY_EXPERIMENTS = "settings_experiments";
  private static final @Deprecated String KEY_MARKDOWN_MODE = "settings_markdown";
  private static final String KEY_MAP_PROVIDER_TYPE = "settings_map_provider";
  private static final String KEY_MAP_PROVIDER_TYPE_CLOUD = "settings_map_provider_cloud";
  private static final String KEY_STICKER_MODE = "settings_sticker";
  private static final String KEY_EMOJI_MODE = "settings_emoji";
  private static final String KEY_REACTION_AVATARS_MODE = "settings_reaction_avatars";
  private static final String KEY_AUTO_UPDATE_MODE = "settings_auto_update";
  private static final String KEY_INCOGNITO = "settings_incognito";
  private static final String KEY_NIGHT_MODE = "settings_night_mode";
  private static final String KEY_VIDEO_LIMIT = "settings_video_limit";
  private static final String KEY_EARPIECE_MODE = "settings_earpiece_mode";
  private static final String KEY_EMOJI_PACK = "settings_emoji_pack";
  private static final String KEY_EMOJI_INSTALLED_PREFIX = "settings_emoji_installed_";
  private static final String KEY_EARPIECE_VIDEO_MODE = "settings_earpiece_video_mode";
  private static final String KEY_MAX_NIGHT_LUX = "night_lux_max";
  private static final String KEY_MAX_NIGHT_LUX_MULTIPLY = "night_lux_max_multiply";
  private static final String KEY_NIGHT_MODE_SCHEDULED_TIME = "settings_night_mode_schedule";
  private static final String KEY_BADGE_FLAGS = "settings_badge_flags";
  private static final String KEY_NOTIFICATION_FLAGS = "settings_notification_flags";
  private static final @Deprecated String KEY_BADGE_MODE = "settings_badge_mode";
  private static final String KEY_THEME_POSITION = "settings_theme_position";
  private static final String KEY_EMOJI_POSITION = "emoji_vp_position";
  private static final String KEY_EMOJI_MEDIA_SECTION = "emoji_vp_mediasection";
  private static final String KEY_TUTORIAL = "settings_tutorial";
  private static final String KEY_TUTORIAL_PSA = "settings_tutorial_psa";
  private static final String KEY_CHAT_FONT_SIZE = "settings_font_size";
  private static final String KEY_CHAT_LIST_MODE = "settings_chat_list_mode";
  private static final String KEY_CHAT_TRANSLATE_MODE = "settings_chat_translate_mode";
  private static final String KEY_CHAT_DO_NOT_TRANSLATE_MODE = "settings_chat_do_not_translate_mode";
  private static final String KEY_CHAT_DO_NOT_TRANSLATE_LIST = "settings_chat_do_not_translate_list";
  private static final String KEY_CHAT_TRANSLATE_RECENTS = "language_recents";
  private static final String KEY_DEFAULT_LANGUAGE_FOR_TRANSLATE_DRAFT = "language_draft_translate";
  private static final String KEY_INSTANT_VIEW = "settings_iv_mode";
  private static final String KEY_RESTRICT_CONTENT = "settings_restrict_content";
  private static final String KEY_CAMERA_ASPECT_RATIO = "settings_camera_ratio";
  private static final String KEY_CAMERA_TYPE = "settings_camera_type";
  private static final String KEY_CAMERA_VOLUME_CONTROL = "settings_camera_control";
  private static final String KEY_CHAT_FOLDER_STYLE = "settings_folders_style";
  private static final String KEY_CHAT_FOLDER_OPTIONS = "settings_folders_options";

  private static final String KEY_TDLIB_VERBOSITY = "settings_tdlib_verbosity";
  private static final String KEY_TDLIB_DEBUG_PREFIX = "settings_tdlib_allow_debug";
  private static final String KEY_TDLIB_OTHER = "settings_tdlib_other";
  private static final String KEY_TDLIB_LOG_SIZE = "settings_tdlib_log_size";
  private static final @Deprecated String KEY_TON_VERBOSITY = "settings_ton_verbosity";
  private static final @Deprecated String KEY_TON_OTHER = "settings_ton_other";
  private static final @Deprecated String KEY_TON_LOG_SIZE = "settings_ton_log_size";

  private static final String KEY_ACCOUNT_INFO = "account";
  public static final String KEY_ACCOUNT_INFO_SUFFIX_ID = ""; // user_id
  public static final String KEY_ACCOUNT_INFO_SUFFIX_FLAGS = "flags"; // premium, verified, etc
  public static final String KEY_ACCOUNT_INFO_SUFFIX_NAME1 = "name1"; // first_name
  public static final String KEY_ACCOUNT_INFO_SUFFIX_NAME2 = "name2"; // last_name
  public static final String KEY_ACCOUNT_INFO_SUFFIX_ACCENT_COLOR_ID = "accent_id"; // accent_color_id
  public static final String KEY_ACCOUNT_INFO_SUFFIX_ACCENT_BUILT_IN_ACCENT_COLOR_ID = "accent_builtin"; // accent_color_id
  public static final String KEY_ACCOUNT_INFO_SUFFIX_LIGHT_THEME_COLORS = "accent_light"; // accent_light
  public static final String KEY_ACCOUNT_INFO_SUFFIX_DARK_THEME_COLORS = "accent_dark"; // accent_dark
  public static final String KEY_ACCOUNT_INFO_SUFFIX_MIN_CHAT_BOOST_LEVEL = "min_boost_level"; // min_chat_boost_level
  public static final String KEY_ACCOUNT_INFO_SUFFIX_USERNAME = "username"; // username
  public static final String KEY_ACCOUNT_INFO_SUFFIX_USERNAMES_ACTIVE = "usernames_active"; // username
  public static final String KEY_ACCOUNT_INFO_SUFFIX_USERNAMES_DISABLED = "usernames_disabled"; // last_name
  public static final String KEY_ACCOUNT_INFO_SUFFIX_PHONE = "phone"; // phone
  public static final String KEY_ACCOUNT_INFO_SUFFIX_PHOTO = "photo"; // path, if loaded
  public static final String KEY_ACCOUNT_INFO_SUFFIX_PHOTO_FULL = "photo_full"; // path, if loaded
  public static final String KEY_ACCOUNT_INFO_SUFFIX_COUNTER = "counter_"; // counter

  public static final String KEY_ACCOUNT_INFO_SUFFIX_EMOJI_STATUS_PREFIX = "emoji_"; // emoji status
  public static final String KEY_EMOJI_STATUS_SUFFIX_ID = "id";
  public static final String KEY_EMOJI_STATUS_SUFFIX_METADATA = "data";
  public static final String KEY_EMOJI_STATUS_SUFFIX_THUMBNAIL = "thumb";
  public static final String KEY_EMOJI_STATUS_SUFFIX_STICKER = "sticker";

  public static String accountInfoPrefix (int accountId) {
    return KEY_ACCOUNT_INFO + accountId + "_";
  }

  private static final String KEY_TDLIB_AUTHENTICATION_TOKENS = "settings_authentication_token";
  private static final String KEY_TDLIB_CRASH_PREFIX = "settings_tdlib_crash";

  private static final String KEY_APP_COMMIT_DATE = "app_commit_date";
  private static final String KEY_APP_INSTALLATION_ID = "app_install_id";
  private static final String KEY_APP_INSTALLATION_PREFIX = "installation";

  private static final String KEY_KNOWN_SIZE = "known_size_for_";
  private static final String KEY_LANGUAGE_CURRENT = "settings_language_code";
  private static final String KEY_LANGUAGE_CODE_SUFFIX_BASE = "base";
  private static final String KEY_LANGUAGE_CODE_SUFFIX_PLURAL = "plural";
  private static final String KEY_LANGUAGE_CODE_SUFFIX_RTL = "rtl";
  private static final String KEY_SUGGESTED_LANGUAGE_CODE = "settings_language_code_suggested";
  private static final String KEY_PREFIX_RTL = "settings_rtl";
  private static final String KEY_UTILITY_FEATURES = "debug_features";
  private static final String KEY_COLOR_FORMAT = "settings_color_format";
  private static final String KEY_PREFERRED_PLAYBACK_MODE = "preferred_audio_mode";
  public static final String KEY_LOG_SETTINGS = "log_settings";
  public static final String KEY_LOG_LEVEL = "log_level";
  public static final String KEY_LOG_TAGS = "log_tags";
  private static final @Deprecated String KEY_PREFER_LEGACY_API = "camera_legacy";
  private static final String KEY_INTRO_ATTEMPTED = "intro_attempt";
  private static final String KEY_MAP_TYPE = "map_type";
  private static final String KEY_LAST_LOCATION = "last_view_location";
  private static final String KEY_LAST_INLINE_LOCATION = "last_inline_location";
  private static final @Deprecated String KEY_PIP_X = "pip_x";
  private static final @Deprecated String KEY_PIP_Y = "pip_y";
  private static final @Deprecated String KEY_SILENT_CHANNEL_PREFIX = "channel_silent";
  private static final String KEY_PIP = "pip";
  private static final String KEY_PAINT_ID = "paint_id";
  private static final String KEY_PIP_GRAVITY = "pip_gravity";
  private static final String KEY_PLAYER_FLAGS = "player_flags";
  private static final String KEY_HIDE_BOT_KEYBOARD_PREFIX = "hide_bot_keyboard_";
  private static final String KEY_SCROLL_CHAT_PREFIX = "scroll_chat";
  private static final String KEY_SCROLL_CHAT_ALIASES = "_aliases";
  private static final String KEY_SCROLL_CHAT_MESSAGE_ID = "_message";
  private static final String KEY_SCROLL_CHAT_MESSAGE_CHAT_ID = "_chat";
  private static final @Deprecated String KEY_SCROLL_CHAT_RETURN_TO_MESSAGE_ID = "_return";
  private static final String KEY_SCROLL_CHAT_RETURN_TO_MESSAGE_IDS_STACK = "_stack";
  private static final String KEY_SCROLL_CHAT_OFFSET = "_offset";
  private static final String KEY_SCROLL_CHAT_READ_FULLY = "_read";
  private static final String KEY_SCROLL_CHAT_TOP_END = "_top";
  private static final @Deprecated String KEY_PUSH_USER_IDS = "push_user_ids";
  private static final @Deprecated String KEY_PUSH_USER_ID = "push_user_id";
  private static final String KEY_PUSH_DEVICE_TOKEN_TYPE = "push_device_token_type";
  private static final String KEY_PUSH_DEVICE_TOKEN_OR_ENDPOINT = "push_device_token";
  private static final String KEY_PUSH_STATS_TOTAL_COUNT = "push_stats_total";
  private static final String KEY_PUSH_STATS_CURRENT_APP_VERSION_COUNT = "push_stats_app";
  private static final String KEY_PUSH_STATS_CURRENT_TOKEN_COUNT = "push_stats_token";
  private static final String KEY_PUSH_LAST_RECEIVED_TIME = "push_last_received_time";
  private static final String KEY_PUSH_LAST_SENT_TIME = "push_last_sent_time";
  private static final String KEY_PUSH_LAST_TTL = "push_last_ttl";
  private static final String KEY_PUSH_REPORTED_ERROR = "push_reported_error";
  private static final String KEY_PUSH_REPORTED_ERROR_DATE = "push_reported_error_date";
  private static final String KEY_CRASH_DEVICE_ID = "crash_device_id";
  private static final String KEY_IS_EMULATOR = "is_emulator";
  private static final String KEY_EMULATOR_DETECTION_RESULT = "emulator";
  private static final String KEY_PLAYBACK_SPEED = "playback_speed";

  private static final @Deprecated String KEY_EMOJI_COUNTERS_OLD = "counters_v2";
  private static final @Deprecated String KEY_EMOJI_RECENTS_OLD = "recents_v2";
  private static final @Deprecated String KEY_EMOJI_COLORS_OLD = "colors_v2";
  private static final @Deprecated String KEY_EMOJI_DEFAULT_COLOR_OLD = "default_v2";

  private static final String KEY_EMOJI_COUNTERS = "emoji_counters";
  private static final String KEY_EMOJI_RECENTS = "emoji_recents";
  private static final String KEY_EMOJI_COLORS = "emoji_colors";
  private static final String KEY_EMOJI_OTHER_COLORS = "emoji_other_colors";
  private static final String KEY_EMOJI_DEFAULT_COLOR = "emoji_default";

  private static final String KEY_QUICK_REACTION = "quick_reaction";
  private static final String KEY_QUICK_REACTIONS = "quick_reactions";
  private static final String KEY_BIG_REACTIONS_IN_CHANNELS = "big_reactions_in_channels";
  private static final String KEY_BIG_REACTIONS_IN_CHATS = "big_reactions_in_chats";

  private static final String KEY_WALLPAPER_PREFIX = "wallpaper";
  private static final String KEY_WALLPAPER_CUSTOM = "_custom";
  private static final String KEY_WALLPAPER_EMPTY = "_empty";
  private static final String KEY_WALLPAPER_PATH = "_path";
  private static final String KEY_WALLPAPER_ID = "_id";

  private static String key (String key, int accountId) {
    return accountId != 0 ? accountId + "_" + key : key;
  }

  public static final @Deprecated String STORAGE_MAIN = "main";
  public static final @Deprecated String STORAGE_EMOJI = "emoji";
  public static final @Deprecated String STORAGE_BOTS = "bots";
  public static final @Deprecated String STORAGE_KEYBOARD = "keyboard";

  private static final int FLAG_OTHER_AUTOPLAY_GIFS = 1;
  private static final int FLAG_OTHER_SAVE_TO_GALLERY = 1 << 1;
  private static final int FLAG_OTHER_ACCOUNT_LIST_OPENED = 1 << 2;
  private static final int FLAG_OTHER_HIDE_SECRET_CHATS = 1 << 3;
  private static final int FLAG_OTHER_REDUCE_MOTION = 1 << 4;
  private static final int FLAG_OTHER_FORCE_ARABIC_NUMBERS = 1 << 5;
  private static final int FLAG_OTHER_FONT_SCALING = 1 << 6;
  private static final int FLAG_OTHER_REMEMBER_ALBUM_SETTING = 1 << 7;
  private static final int FLAG_OTHER_USE_SYSTEM_EMOJI = 1 << 8;
  private static final int FLAG_OTHER_DONT_READ_MESSAGES = 1 << 9;
  private static final int FLAG_OTHER_NO_CHAT_QUICK_SHARE = 1 << 10;
  private static final int FLAG_OTHER_NO_CHAT_QUICK_REPLY = 1 << 11;
  private static final int FLAG_OTHER_SEND_BY_ENTER = 1 << 12;
  private static final int FLAG_OTHER_HIDE_CHAT_KEYBOARD = 1 << 13;
  private static final int FLAG_OTHER_USE_QUICK_TRANSLATION = 1 << 14;
  private static final int FLAG_OTHER_DISABLE_PREVIEW_CHATS_ON_HOLD = 1 << 15;
  private static final int FLAG_OTHER_NEED_GROUP_MEDIA = 1 << 16;
  private static final int FLAG_OTHER_DISABLE_INAPP_BROWSER = 1 << 17;
  private static final int FLAG_OTHER_SEPARATE_MEDIA_TAB = 1 << 18;
  private static final int FLAG_OTHER_SPLIT_CHAT_NOTIFICATIONS = 1 << 20;
  private static final int FLAG_OTHER_OUTBOUND_CALLS_PROMPT = 1 << 21;
  private static final int FLAG_OTHER_DISABLE_CUSTOM_VIBRATIONS = 1 << 22;
  private static final int FLAG_OTHER_DISABLE_ADDITIONAL_SYNC = 1 << 23;
  private static final int FLAG_OTHER_PREFER_VIDEO_MODE = 1 << 25;
  private static final int FLAG_OTHER_HQ_ROUND_VIDEOS = 1 << 26;
  private static final int FLAG_OTHER_USE_SYSTEM_FONTS = 1 << 27;
  private static final int FLAG_OTHER_START_ROUND_REAR = 1 << 28;
  private static final int FLAG_OTHER_DISABLE_BIG_EMOJI = 1 << 29;
  private static final int FLAG_OTHER_DISABLE_SECRET_LINK_PREVIEWS = 1 << 30;

  public static final long SETTING_FLAG_BATMAN_POLL_TRANSITIONS = 1 << 1;
  public static final long SETTING_FLAG_EDIT_MARKDOWN = 1 << 2;
  public static final long SETTING_FLAG_NO_ANIMATED_STICKERS_LOOP = 1 << 3;
  public static final long SETTING_FLAG_NO_ANIMATED_EMOJI = 1 << 4;
  public static final long SETTING_FLAG_EXPLICIT_DICE = 1 << 5;
  public static final long SETTING_FLAG_USE_METRIC_FILE_SIZE_UNITS = 1 << 6;
  public static final long SETTING_FLAG_FORCE_EXO_PLAYER_EXTENSIONS = 1 << 7;
  public static final long SETTING_FLAG_NO_AUDIO_COMPRESSION = 1 << 8;
  public static final long SETTING_FLAG_DOWNLOAD_BETAS = 1 << 9;
  public static final long SETTING_FLAG_NO_ANIMATED_EMOJI_LOOP = 1 << 10;

  public static final long SETTING_FLAG_CAMERA_NO_FLIP = 1 << 10;
  public static final long SETTING_FLAG_CAMERA_KEEP_DISCARDED_MEDIA = 1 << 11;
  public static final long SETTING_FLAG_CAMERA_SHOW_GRID = 1 << 12;

  public static final long SETTING_FLAG_NO_EMBEDS = 1 << 13;
  public static final long SETTING_FLAG_LIMIT_STICKERS_FPS = 1 << 14;
  public static final long SETTING_FLAG_EXPAND_RECENT_STICKERS = 1 << 15;
  public static final long SETTING_FLAG_FOREGROUND_SERVICE_ENABLED = 1 << 16;
  public static final long SETTING_FLAG_DYNAMIC_ORDER_STICKER_PACKS = 1 << 17;
  public static final long SETTING_FLAG_DYNAMIC_ORDER_EMOJI_PACKS = 1 << 18;
  public static final long SETTING_FLAG_FORCE_DEFAULT_ANIMATION_FOR_RIGHT_SWIPE_EDGE = 1 << 19;
  public static final long SETTING_FLAG_FORCE_DISABLE_HLS_VIDEO = 1 << 20;
  public static final long SETTING_FLAG_PAUSE_MEDIA_ON_RECORD = 1 << 21;
  public static final long SETTING_FLAG_SEND_PHOTOS_IN_HD = 1 << 22;
  public static final long SETTING_FLAG_WRITE_CONTACTS_TO_PHONEBOOK = 1 << 23;
  public static final long SETTING_FLAG_SELECT_TEXT_TO_QUOTE = 1 << 24;
  public static final long SETTING_FLAG_KEEP_ALIVE_CONNECTION = 1 << 25;

  public static final long EXPERIMENT_FLAG_ALLOW_EXPERIMENTS = 1;
  public static final long EXPERIMENT_FLAG_SHOW_PEER_IDS = 1 << 2;
  public static final long EXPERIMENT_FLAG_NO_EDGE_TO_EDGE = 1 << 3;
  public static final long EXPERIMENT_FLAG_FORCE_ALTERNATIVE_PUSH_SERVICE = 1 << 4;

  public static final long REMOVED_EXPERIMENT_FLAG_ENABLE_FOLDERS = 1 << 1;

  private static final @Deprecated int DISABLED_FLAG_OTHER_NEED_RAISE_TO_SPEAK = 1 << 2;
  private static final @Deprecated int DISABLED_FLAG_OTHER_AUTODOWNLOAD_IN_BACKGROUND = 1 << 3;
  private static final @Deprecated int DISABLED_FLAG_OTHER_DEFAULT_CRASH_MANAGER = 1 << 5;
  private static final @Deprecated int DISABLED_FLAG_OTHER_USE_VOICE_DRAFT = 1 << 6;
  private static final @Deprecated int DISABLED_FLAG_OTHER_NO_CHAT_SWIPES = 1 << 7;
  private static final @Deprecated int DISABLED_FLAG_OTHER_SHOW_FORWARD_OPTIONS = 1 << 14;
  private static final @Deprecated int DISABLED_FLAG_OTHER_USE_DIFFERENT_MEDIA_PICKER_LAYOUT = 1 << 16;
  private static final @Deprecated int DISABLED_FLAG_OTHER_USE_AUTO_NIGHT_MODE = 1 << 18;
  private static final @Deprecated int DISABLED_FLAG_OTHER_NO_ONLINE = 1 << 23;
  private static final @Deprecated int DISABLED_FLAG_OTHER_CAMERA_FORCE_16_9 = 1 << 24;
  private static final @Deprecated int DISABLED_FLAG_OTHER_ENABLE_RAISE_TO_SPEAK = 1 << 27;
  private static final @Deprecated int DISABLED_FLAG_OTHER_GROUP_MEDIA = 1 << 29;
  private static final @Deprecated int DISABLED_FLAG_OTHER_DISABLE_CALLS_PROXY = 1 << 20;
  private static final @Deprecated int DISABLED_FLAG_OTHER_DISABLE_CUSTOM_TEXT_ACTIONS = 1 << 19;

  @Nullable
  private Integer _settings;
  @Nullable
  private Long _newSettings, _experiments;

  public static final int NIGHT_MODE_NONE = 0;
  public static final int NIGHT_MODE_AUTO = 1;
  public static final int NIGHT_MODE_SCHEDULED = 2;
  public static final int NIGHT_MODE_SYSTEM = 3;
  public static final int NIGHT_MODE_DEFAULT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? NIGHT_MODE_SYSTEM : NIGHT_MODE_NONE;

  @Nullable
  private Integer _nightMode;
  @Nullable
  private Float _nightModeAutoLux;
  @Nullable
  private Long _nightModeSchedule;

  private VideoLimit preferredVideoLimit;

  public static final int STICKER_MODE_ALL = 0;
  public static final int STICKER_MODE_ONLY_INSTALLED = 1;
  public static final int STICKER_MODE_NONE = 2;

  @Nullable private Integer _stickerMode;
  @Nullable private Integer _emojiMode;

  public static final int REACTION_AVATARS_MODE_NEVER = 0;
  public static final int REACTION_AVATARS_MODE_SMART_FILTER = 1;
  public static final int REACTION_AVATARS_MODE_ALWAYS = 2;

  @Nullable private Integer _reactionAvatarsMode;

  public static final int AUTO_UPDATE_MODE_PROMPT = 0;
  public static final int AUTO_UPDATE_MODE_NEVER = 1;
  public static final int AUTO_UPDATE_MODE_WIFI_ONLY = 2;
  public static final int AUTO_UPDATE_MODE_ALWAYS = 3;

  @Nullable
  private Integer _autoUpdateMode;

  public static final int INCOGNITO_CHAT_SECRET = 1;

  @Nullable
  private Integer _incognitoMode;

  public static final long TUTORIAL_INLINE_SEARCH_SECRECY = 1;
  public static final long TUTORIAL_SECRET_LINK_PREVIEWS = 1 << 1;
  public static final long TUTORIAL_YOUTUBE_ROTATION = 1 << 2;
  public static final long TUTORIAL_CHAT_DEMO_TRANSLATION = 1 << 3;
  public static final long TUTORIAL_DEVELOPER_MODE = 1 << 4;
  public static final long TUTORIAL_TESTER_MODE = 1 << 5;
  public static final long TUTORIAL_EMOJI_TONE_ALL = 1 << 6;
  public static final long TUTORIAL_SYNC_SETTINGS = 1 << 7;
  public static final long TUTORIAL_EMOJI_PACKS = 1 << 8;
  public static final long TUTORIAL_SCHEDULE = 1 << 9;
  public static final long TUTORIAL_SET_REMINDER = 1 << 10;
  public static final long TUTORIAL_SEND_WITHOUT_MARKDOWN = 1 << 11;
  public static final long TUTORIAL_SEND_AS_FILE = 1 << 12;
  public static final long TUTORIAL_FORWARD_SCHEDULE = 1 << 13;
  public static final long TUTORIAL_FORWARD_COPY = 1 << 14;
  public static final long TUTORIAL_HOLD_VIDEO = 1 << 15;
  public static final long TUTORIAL_PROXY_SPONSOR = 1 << 16;
  public static final long TUTORIAL_BRUSH_COLOR_TONE = 1 << 17;
  public static final long TUTORIAL_QR_SCAN = 1 << 18;
  public static final long TUTORIAL_SELECT_LANGUAGE_INLINE_MODE = 1 << 19;
  public static final long TUTORIAL_MULTIPLE_LINK_PREVIEWS = 1 << 20;
  public static final long TUTORIAL_PLAYBACK_SPEED_HOLD = 1 << 21;
  public static final long TUTORIAL_PLAYBACK_SPEED_SWIPE = 1 << 22;
  public static final long TUTORIAL_NOTIFICATION_PERMISSION = 1 << 23; // TGx101: ask for notifications once

  @Nullable
  private Long _tutorialFlags;

  public static final int BADGE_FLAG_MESSAGES = 1;
  public static final int BADGE_FLAG_MUTED = 1 << 1;
  public static final int BADGE_FLAG_ARCHIVED = 1 << 2;

  @Nullable
  private Integer _badgeFlags;

  public static final int NOTIFICATION_FLAG_INCLUDE_PRIVATE = 1;
  public static final int NOTIFICATION_FLAG_INCLUDE_GROUPS = 1 << 1;
  public static final int NOTIFICATION_FLAG_INCLUDE_CHANNELS = 1 << 2;
  public static final int NOTIFICATION_FLAG_ONLY_ACTIVE_ACCOUNT = 1 << 3;
  public static final int NOTIFICATION_FLAG_ONLY_SELECTED_ACCOUNTS = 1 << 4;
  public static final int NOTIFICATION_FLAGS_DEFAULT = NOTIFICATION_FLAG_INCLUDE_PRIVATE;

  @Nullable
  private Integer _notificationFlags;

  private static final long DEFAULT_LOG_SIZE = ByteUnit.MIB.toBytes(50);
  private static final int DEFAULT_LOG_GLOBAL_VERBOSITY_LEVEL = 0;

  public class TdlibLogSettings {
    private final String settingsKey, maxSizeKey, verbosityKey;

    public TdlibLogSettings (String settingsKey, String maxSizeKey, String verbosityKey) {
      this.settingsKey = settingsKey;
      this.maxSizeKey = maxSizeKey;
      this.verbosityKey = verbosityKey;
    }

    public void disable () {
      setVerbosity(null, 0);
    }

    public boolean isEnabled () {
      return getVerbosity(null) > 0;
    }

    @Nullable
    private Integer _settings;
    private Map<String, int[]> _modules;

    private int getSettings () {
      if (_settings == null) {
        _settings = pmc.getInt(settingsKey, UI.isTestLab() ? FLAG_TDLIB_OTHER_ENABLE_ANDROID_LOG : 0);
      }
      return _settings;
    }

    private boolean checkLogSetting (int flag) {
      if (BuildConfig.LAB_FLAVOR) {
        if (flag == FLAG_TDLIB_OTHER_ENABLE_ANDROID_LOG) {
          return true;
        }
      }
      return BitwiseUtils.hasFlag(getSettings(), flag);
    }

    private boolean setLogSetting (int flag, boolean enabled) {
      int flags = getSettings();
      int newFlags = BitwiseUtils.setFlag(flags, flag, enabled);
      if (flags != newFlags) {
        _settings = newFlags;
        pmc.putInt(settingsKey, newFlags);
        apply(false);
        return true;
      }
      return false;
    }

    public boolean needAndroidLog () {
      return checkLogSetting(FLAG_TDLIB_OTHER_ENABLE_ANDROID_LOG);
    }

    public void setNeedAndroidLog (boolean needTdlibAndroidLog) {
      setLogSetting(FLAG_TDLIB_OTHER_ENABLE_ANDROID_LOG, needTdlibAndroidLog);
    }

    public long getLogMaxFileSize () {
      return getLong(maxSizeKey, DEFAULT_LOG_SIZE);
    }

    public void setMaxFileSize (long bytes) {
      if (bytes == DEFAULT_LOG_SIZE)
        pmc.remove(maxSizeKey);
      else
        pmc.putLong(maxSizeKey, bytes);
      apply(false);
    }

    public List<String> getModules () {
      List<String> modules;
      try {
        TdApi.LogTags logTags = Client.execute(new TdApi.GetLogTags());
        String[] tags = logTags.tags;
        modules = new ArrayList<>(tags.length + (_modules != null ? _modules.size() : 0));
        Collections.addAll(modules, tags);
      } catch (Client.ExecutionException error) {
        modules = new ArrayList<>(_modules != null ? _modules.size() : 0);
      }
      if (_modules != null) {
        for (String key : _modules.keySet()) {
          if (!modules.contains(key)) {
            modules.add(key);
          }
        }
      }
      return modules;
    }

    private boolean setLogTagVerbosityLevel (String module, int verbosityLevel) {
      try {
        Client.execute(new TdApi.SetLogTagVerbosityLevel(module, verbosityLevel));
        return true;
      } catch (Client.ExecutionException error) {
        return false;
      }
    }

    private boolean setLogVerbosityLevel (int globalVerbosityLevel) {
      try {
        Client.execute(new TdApi.SetLogVerbosityLevel(globalVerbosityLevel));
        return true;
      } catch (Client.ExecutionException error) {
        return false;
      }
    }

    public int getVerbosity (@Nullable String module) {
      if (!StringUtils.isEmpty(module)) {
        int[] value = _modules != null ? _modules.get(module) : null;
        if (value != null) {
          return value[0];
        } else {
          int defaultLogVerbosity = queryLogVerbosityLevel(module);
          _modules.put(module, new int[] {defaultLogVerbosity, defaultLogVerbosity});
          return defaultLogVerbosity;
        }
      }
      return queryLogVerbosityLevel(module);
    }

    public int getDefaultVerbosity (@NonNull String module) {
      int[] value = _modules != null ? _modules.get(module) : null;
      if (value == null) {
        int defaultLogVerbosity = queryLogVerbosityLevel(module);
        _modules.put(module, new int[] {defaultLogVerbosity, defaultLogVerbosity});
        return defaultLogVerbosity;
      }
      return value[1];
    }

    public void setVerbosity (@Nullable String module, int verbosity) {
      if (StringUtils.isEmpty(module)) {
        if (verbosity != DEFAULT_LOG_GLOBAL_VERBOSITY_LEVEL) {
          putInt(verbosityKey, verbosity);
        } else {
          remove(verbosityKey);
        }
        setLogVerbosityLevel(verbosity);
      } else {
        if (_modules == null)
          _modules = new HashMap<>();
        int[] value = _modules.get(module);
        int defaultVerbosityLevel = value != null ? value[1] : queryLogVerbosityLevel(module);
        int currentVerbosityLevel = value != null ? value[0] : defaultVerbosityLevel;
        if (verbosity != currentVerbosityLevel) {
          try {
            Client.execute(new TdApi.SetLogTagVerbosityLevel(module, verbosity));

            if (value != null)
              value[0] = verbosity;
            else
              _modules.put(module, value = new int[] {verbosity, defaultVerbosityLevel});
            if (value[0] == value[1])
              remove(verbosityKey + "_" + module);
            else
              putInt(verbosityKey + "_" + module, verbosity);
          } catch (Client.ExecutionException ignored) { }
        }
      }
    }

    public void reset () {
      pmc.edit();
      setVerbosity(null, 0);
      if (_modules != null) {
        for (Map.Entry<String, int[]> entry : _modules.entrySet()) {
          setVerbosity(entry.getKey(), entry.getValue()[1]);
        }
      }
      setMaxFileSize(DEFAULT_LOG_SIZE);
      pmc.apply();
    }

    private int queryLogVerbosityLevel (@Nullable String module) {
      try {
        TdApi.Function<TdApi.LogVerbosityLevel> function = StringUtils.isEmpty(module) ? new TdApi.GetLogVerbosityLevel() : new TdApi.GetLogTagVerbosityLevel(module);
        TdApi.LogVerbosityLevel logVerbosityLevel = Client.execute(function);
        return logVerbosityLevel.verbosityLevel;
      } catch (Client.ExecutionException error) {
        return TDLIB_LOG_VERBOSITY_UNKNOWN;
      }
    }

    public void apply (boolean async) {
      if (UI.isTestLab())
        return;
      int globalVerbosityLevel = DEFAULT_LOG_GLOBAL_VERBOSITY_LEVEL;
      if (_modules == null)
        _modules = new HashMap<>();
      for (final LevelDB.Entry entry : pmc.find(verbosityKey)) {
        final String key = entry.key();
        int verbosityLevel = entry.asInt();
        if (verbosityKey.length() == key.length()) {
          globalVerbosityLevel = Math.max(0, verbosityLevel); // Can't be negative
        } else if (key.length() > verbosityKey.length() + 1) {
          verbosityLevel = Math.max(1, verbosityLevel); // At least error
          String module = key.substring(verbosityKey.length() + 1);
          int[] value = _modules.get(module);
          int defaultVerbosityLevel = value != null ? value[1] : queryLogVerbosityLevel(module);
          if (setLogTagVerbosityLevel(module, verbosityLevel)) {
            if (value != null) {
              value[0] = verbosityLevel;
            } else {
              _modules.put(module, new int[] {verbosityLevel, defaultVerbosityLevel});
            }
          }
        }
      }
      setLogVerbosityLevel(globalVerbosityLevel);

      TdApi.LogStream stream;
      if (needAndroidLog()) {
        stream = new TdApi.LogStreamDefault();
      } else {
        File logFile = TdlibManager.getLogFile(false);
        if (logFile != null) {
          stream = new TdApi.LogStreamFile(logFile.getPath(), getLogMaxFileSize(), false);
        } else {
          stream = new TdApi.LogStreamEmpty();
        }
      }
      try {
        Client.execute(new TdApi.SetLogStream(stream));
      } catch (Client.ExecutionException error) {
        Runnable act = () -> {
          Tracer.onTdlibFatalError(null, TdApi.SetLogStream.class, error.error, new RuntimeException().getStackTrace());
        };
        if (async) {
          UI.post(act);
        } else {
          act.run();
        }
      }
    }
  }

  // private static final int FLAG_TDLIB_OTHER_USE_DEBUG_DC = 1;
  private static final int FLAG_TDLIB_OTHER_ENABLE_ANDROID_LOG = 1 << 1;
  public static final int TDLIB_LOG_VERBOSITY_UNKNOWN = -1;

  private final TdlibLogSettings tdlibLogSettings = new TdlibLogSettings(KEY_TDLIB_OTHER, KEY_TDLIB_LOG_SIZE, KEY_TDLIB_VERBOSITY);

  public static final float[] CHAT_FONT_SIZES = {12f, 13f, 14f, 15f, 16f, 18f, 20f, 22f, 24f, 26f};
  public static final float CHAT_FONT_SIZE_DEFAULT = 15f;
  private static final float CHAT_FONT_SIZE_MIN = CHAT_FONT_SIZES[0];
  private static final float CHAT_FONT_SIZE_MAX = CHAT_FONT_SIZES[CHAT_FONT_SIZES.length - 1];

  public static final int INSTANT_VIEW_MODE_NONE = 0;
  public static final int INSTANT_VIEW_MODE_INTERNAL = 1;
  public static final int INSTANT_VIEW_MODE_ALL = 2;

  @Nullable
  private Float _chatFontSize;

  @Nullable
  private Integer _preferredAudioPlaybackMode;

  public static final float MAX_NIGHT_LUX_DEFAULT = 1.5f;

  @Nullable
  private Integer _mapProviderType, _mapProviderTypeCloud;

  public static final int MAP_PROVIDER_UNSET = -1;
  public static final int MAP_PROVIDER_NONE = 0;
  public static final int MAP_PROVIDER_TELEGRAM = 1;
  public static final int MAP_PROVIDER_GOOGLE = 2;

  public static final int MAP_PROVIDER_DEFAULT_CLOUD = MAP_PROVIDER_TELEGRAM;

  private static class ScheduleHandler extends Handler {
    private final Settings context;

    public ScheduleHandler (Settings context) {
      super(Looper.getMainLooper());
      this.context = context;
    }

    @Override
    public void handleMessage (Message msg) {
      context.handleMessage(msg);
    }
  }

  private final ScheduleHandler handler = new ScheduleHandler(this);

  public File getDirectory () {
    File pmcDir = new File(UI.getAppContext().getFilesDir(), "pmc");
    return new File(pmcDir, "db");
  }

  private Settings () {
    File pmcDir = new File(UI.getAppContext().getFilesDir(), "pmc");
    boolean didNotExist = !pmcDir.exists();
    if (Config.ENABLE_BASELINE_PROFILE_HOOKS && didNotExist) {
      if (SessionSnapshot.restoreSnapshot()) {
        didNotExist = false;
      }
    }
    boolean fatalError;
    try {
      fatalError = !FileUtils.createDirectory(pmcDir);
    } catch (SecurityException e) {
      e.printStackTrace();
      fatalError = true;
    }
    if (fatalError) {
      throw new DeviceStorageError("Unable to create working directory");
    }
    long ms = SystemClock.uptimeMillis();
    pmc = new LevelDB(new File(pmcDir, "db").getPath(), true, new LevelDB.ErrorHandler() {
      @Override
      public boolean onFatalError (LevelDB levelDB, Throwable error) {
        Tracer.onDatabaseError(error);
        return true;
      }

      @Override
      public void onError (LevelDB levelDB, String message, @Nullable Throwable error) {
        // Cannot use custom Log, since settings are not yet loaded
        android.util.Log.e(Log.LOG_TAG, message, error);
      }
    });
    Log.load(pmc);
    int pmcVersion;
    if (didNotExist) {
      pmcVersion = VERSION;
      pmc.putInt(KEY_VERSION, VERSION);
    } else {
      pmcVersion = 0;
      try {
        pmcVersion = Math.max(0, pmc.tryGetInt(KEY_VERSION));
      } catch (FileNotFoundException e) {
        if (isFreshAppInstallation()) {
          pmcVersion = VERSION;
          pmc.putInt(KEY_VERSION, pmcVersion);
        } else {
          migratePrefsToPmc();
        }
      }
      if (pmcVersion > VERSION) {
        Log.e("Downgrading database version: %d -> %d", pmcVersion, VERSION);
        pmc.putInt(KEY_VERSION, VERSION);
      }
    }
    for (int version = pmcVersion + 1; version <= VERSION; version++) {
      SharedPreferences.Editor editor = pmc.edit();
      upgradePmc(pmc, editor, version);
      editor.putInt(KEY_VERSION, version);
      editor.apply();
    }
    /*if (BuildConfig.DEBUG) {
      int accountNum = TdlibManager.readAccountNum();
      pmc.edit();
      for (int accountId = 0; accountId < accountNum; accountId++) {
        String key = key(TdlibSettingsManager.DEVICE_TDLIB_VERSION_KEY, accountId);
        pmc.remove(key);
      }
      pmc.apply();
    }*/
    if (BuildConfig.DEBUG) {
      pmc.remove(KEY_TUTORIAL);
      pmc.removeByPrefix(KEY_TUTORIAL_PSA);
    }
    if (Config.TEST_NEW_FEATURES_PROMPTS) {
      forceRevokeAllFeaturePrompts();
    }
    trackInstalledApkVersion();
    trackChangesInAvailableFeatures();
    Log.i("Opened database in %dms", SystemClock.uptimeMillis() - ms);
    checkPendingPasscodeLocks();
    applyLogSettings(true);
  }

  public void forceRevokeAllFeaturePrompts () {
    pmc
      .putLong(KEY_FEATURES, 0 /*no features were available*/)
      .remove(KEY_FEATURES_ADDED_NOTIFICATIONS)
      .remove(KEY_FEATURES_REMOVED_NOTIFICATIONS);
  }

  // Schedule

  private static final int MSG_DISPATCH_NIGHT_SCHEDULE_CHECK = 0;

  private void handleMessage (Message msg) {
    switch (msg.what) {
      case MSG_DISPATCH_NIGHT_SCHEDULE_CHECK:
        checkNightModeScheduler(false);
        break;
    }
  }

  public void scheduleNightScheduleCheck (long timeTillNextChange) {
    handler.sendMessageDelayed(Message.obtain(handler, MSG_DISPATCH_NIGHT_SCHEDULE_CHECK), timeTillNextChange);
  }

  public void cancelNightScheduleCheck () {
    handler.removeMessages(MSG_DISPATCH_NIGHT_SCHEDULE_CHECK);
  }

  // Modification listeners

  public void reset () {
    setTutorialFlags(0);
    pmc.removeByPrefix(KEY_TUTORIAL_PSA);
    resetOther();
  }

  public LevelDB edit () {
    return pmc.edit();
  }

  public void remove (String key) {
    pmc.remove(key);
  }

  public void putLong (String key, long value) {
    pmc.putLong(key, value);
  }

  public long getLong (String key, long defValue) {
    return pmc.getLong(key, defValue);
  }

  public long[] getLongArray (String key) {
    return pmc.getLongArray(key);
  }

  public void putLongArray (String key, long[] value) {
    pmc.putLongArray(key, value);
  }

  public void putInt (String key, int value) {
    pmc.putInt(key, value);
  }

  // How many times the user has put each reaction, used to order quick reactions.
  private static final String KEY_REACTION_USAGE_PREFIX = "reaction_usage_";

  public void incrementReactionUsage (String reactionKey) {
    String key = KEY_REACTION_USAGE_PREFIX + reactionKey;
    pmc.putInt(key, pmc.getInt(key, 0) + 1);
  }

  public int getReactionUsage (String reactionKey) {
    return pmc.getInt(KEY_REACTION_USAGE_PREFIX + reactionKey, 0);
  }

  public int getInt (String key, int defValue) {
    return pmc.getInt(key, defValue);
  }

  public int[] getIntArray (String key) {
    return pmc.getIntArray(key);
  }

  public void putIntArray (String key, int[] value) {
    pmc.putIntArray(key, value);
  }

  public void putFloat (String key, float value) {
    pmc.putFloat(key, value).apply();
  }

  public void putBoolean (String key, boolean value) {
    pmc.putBoolean(key, value);
  }

  public boolean getBoolean (String key, boolean defValue) {
    return pmc.getBoolean(key, defValue);
  }

  public void putVoid (String key) {
    pmc.putVoid(key);
  }

  public boolean containsKey (String key) {
    return pmc.contains(key);
  }

  public void putString (String key, @NonNull String value) {
    pmc.putString(key, value);
  }

  public String getString (String key, String defValue) {
    return pmc.getString(key, defValue);
  }

  public void removeByPrefix (String prefix, @Nullable SharedPreferences.Editor editor) {
    pmc.removeByPrefix(prefix); // editor
  }

  public void removeByAnyPrefix (String[] prefixes, @Nullable SharedPreferences.Editor editor) {
    pmc.removeByAnyPrefix(prefixes); // , editor
  }

  private void resetOther () {
    remove(KEY_OTHER);
    this._settings = makeDefaultSettings();
  }

  @Deprecated
  private void upgradeSharedPreferences (SharedPreferences prefs, SharedPreferences.Editor editor, int version) {
    switch (version) {
      case LEGACY_VERSION_1: {
        // Adding video note autodownload setting if voice autodownload is enabled
        TdlibFilesManager.upgradeSharedPreferences(prefs, editor);
        break;
      }
      case LEGACY_VERSION_2: {
        /*int otherSettings = prefs.getInt(OTHER_KEY, -1);
        if (otherSettings != -1 && (otherSettings & FLAG_OTHER_GROUP_MEDIA) == 0) {
          otherSettings |= FLAG_OTHER_GROUP_MEDIA;
          prefs.edit().putInt(OTHER_KEY, otherSettings).apply();
        }*/
        break;
      }
      case LEGACY_VERSION_3:
      case LEGACY_VERSION_6: {
        int otherSettings = prefs.getInt(KEY_OTHER, -1);
        if (otherSettings != -1 && (otherSettings & FLAG_OTHER_NO_CHAT_QUICK_SHARE) == 0) {
          otherSettings |= FLAG_OTHER_NO_CHAT_QUICK_SHARE;
          prefs.edit().putInt(KEY_OTHER, otherSettings).apply();
        }
        break;
      }
      case LEGACY_VERSION_4: {
        /*
        public static final float MAX_NIGHT_LUX_MULTIPLY_DEFAULT = 1.0f;
        public static final float[] MAX_NIGHT_LUX_MULTIPLIES = {
          0.5f,
          MAX_NIGHT_LUX_MULTIPLY_DEFAULT,
          1.5f,
          2f,
          5f,
          10f,
          20f
        };
        private float maxNightLux;
        private float maxNightLuxMultiply;
        * */

        float maxNightLux = prefs.getFloat(KEY_MAX_NIGHT_LUX, MAX_NIGHT_LUX_DEFAULT);
        float maxNightLuxMultiply = Math.max(0.5f, Math.min(20f, prefs.getFloat(KEY_MAX_NIGHT_LUX_MULTIPLY, 1f)));

        float previousNightLux = maxNightLux * maxNightLuxMultiply;
        if (previousNightLux != MAX_NIGHT_LUX_DEFAULT) {
          editor.putFloat(KEY_MAX_NIGHT_LUX, previousNightLux);
        }

        editor.remove(KEY_MAX_NIGHT_LUX_MULTIPLY);

        if (!prefs.contains(KEY_OTHER)) {
          return;
        }

        final int otherSettings = prefs.getInt(KEY_OTHER, 0);

        int newOtherSettings = otherSettings;

        if ((otherSettings & DISABLED_FLAG_OTHER_USE_AUTO_NIGHT_MODE) != 0) {
          editor.putInt(KEY_NIGHT_MODE, NIGHT_MODE_AUTO);
        }

        newOtherSettings &= ~DISABLED_FLAG_OTHER_NEED_RAISE_TO_SPEAK;
        newOtherSettings &= ~DISABLED_FLAG_OTHER_AUTODOWNLOAD_IN_BACKGROUND;
        newOtherSettings &= ~DISABLED_FLAG_OTHER_DEFAULT_CRASH_MANAGER;
        newOtherSettings &= ~DISABLED_FLAG_OTHER_USE_VOICE_DRAFT;
        newOtherSettings &= ~DISABLED_FLAG_OTHER_NO_CHAT_SWIPES;
        newOtherSettings &= ~DISABLED_FLAG_OTHER_USE_DIFFERENT_MEDIA_PICKER_LAYOUT;
        newOtherSettings &= ~DISABLED_FLAG_OTHER_USE_AUTO_NIGHT_MODE;
        newOtherSettings &= ~DISABLED_FLAG_OTHER_NO_ONLINE;
        newOtherSettings &= ~DISABLED_FLAG_OTHER_ENABLE_RAISE_TO_SPEAK;
        newOtherSettings &= ~DISABLED_FLAG_OTHER_GROUP_MEDIA;
        newOtherSettings &= ~DISABLED_FLAG_OTHER_DISABLE_CUSTOM_TEXT_ACTIONS;
        newOtherSettings &= ~DISABLED_FLAG_OTHER_SHOW_FORWARD_OPTIONS;

        if (newOtherSettings != otherSettings) {
          editor.putInt(KEY_OTHER, newOtherSettings);
        }

        break;
      }
      case LEGACY_VERSION_5: {
        editor.remove("record_id");
        break;
      }
      case LEGACY_VERSION_7: {
        editor.remove("settings_oreo_fix");
        break;
      }
      case LEGACY_VERSION_8: {
        int userId = prefs.getInt(KEY_PUSH_USER_ID, 0);
        String userIdsStr = prefs.getString(KEY_PUSH_USER_IDS, null);
        if (userId != 0) {
          editor.remove(KEY_PUSH_USER_ID);
          if (StringUtils.isEmpty(userIdsStr)) {
            editor.putString(KEY_PUSH_USER_IDS, String.valueOf(userId));
          }
        }
        break;
      }
      case LEGACY_VERSION_9: {
        if (prefs.contains(KEY_OTHER)) {
          int flags = prefs.getInt(KEY_OTHER, 0);
          if ((flags & DISABLED_FLAG_OTHER_DISABLE_CALLS_PROXY) != 0) {
            flags &= ~DISABLED_FLAG_OTHER_DISABLE_CALLS_PROXY;
            editor.putInt(KEY_OTHER, flags);
          }
        }
        break;
      }
    }
  }

  public HashMap<String, Boolean> _chatDoNotTranslateLanguages;

  private void loadNotTranslatableLanguages () {
    if (_chatDoNotTranslateLanguages != null) return;
    _chatDoNotTranslateLanguages = new HashMap<>();
    String[] result = pmc.getStringArray(KEY_CHAT_DO_NOT_TRANSLATE_LIST);
    if (result == null) return;
    for (String lang : result) {
      _chatDoNotTranslateLanguages.put(lang, true);
    }
  }

  private void saveNotTranslatableLanguages () {
    pmc.putStringArray(KEY_CHAT_DO_NOT_TRANSLATE_LIST, getAllNotTranslatableLanguages());
  }

  public String[] getAllNotTranslatableLanguages () {
    loadNotTranslatableLanguages();
    StringList list = new StringList(_chatDoNotTranslateLanguages.size());
    for (Map.Entry<String, Boolean> entry : _chatDoNotTranslateLanguages.entrySet()) {
      list.append(entry.getKey());
    }
    return list.get();
  }

  public boolean isNotTranslatableLanguage (String lang) {
    if (getChatDoNotTranslateMode() == DO_NOT_TRANSLATE_MODE_APP_LANG) {
      return StringUtils.equalsOrBothEmpty(getLanguage().packInfo.pluralCode, lang);
    } else {
      return containsInNotTranslatableLanguageList(lang);
    }
  }

  public boolean containsInNotTranslatableLanguageList (String lang) {
    loadNotTranslatableLanguages();
    if (lang == null) return false;
    return _chatDoNotTranslateLanguages.containsKey(lang);
  }

  public void setIsNotTranslatableLanguage (String lang, boolean isNotTranslatable) {
    if (isNotTranslatable == containsInNotTranslatableLanguageList(lang)) return;
    if (isNotTranslatable) {
      _chatDoNotTranslateLanguages.put(lang, true);
    } else {
      _chatDoNotTranslateLanguages.remove(lang);
    }
    saveNotTranslatableLanguages();
  }


  public static final int DO_NOT_TRANSLATE_MODE_APP_LANG = 1;
  public static final int DO_NOT_TRANSLATE_MODE_SELECTED = 2;
  private Integer _chatDoNotTranslateMode;

  public int getChatDoNotTranslateMode () {
    if (_chatDoNotTranslateMode == null) {
      _chatDoNotTranslateMode = pmc.getInt(KEY_CHAT_DO_NOT_TRANSLATE_MODE, DO_NOT_TRANSLATE_MODE_APP_LANG);
    }
    return _chatDoNotTranslateMode;
  }

  public void setChatDoNotTranslateMode (int mode) {
    if (getChatDoNotTranslateMode() != mode) {
      pmc.putInt(KEY_CHAT_DO_NOT_TRANSLATE_MODE, _chatDoNotTranslateMode = mode);
    }
  }

  public static final int TRANSLATE_MODE_NONE = 1;
  public static final int TRANSLATE_MODE_POPUP = 2;
  public static final int TRANSLATE_MODE_INLINE = 3;
  private Integer _chatTranslateMode;

  public int getChatTranslateMode () {
    if (_chatTranslateMode == null) {
      _chatTranslateMode = pmc.getInt(KEY_CHAT_TRANSLATE_MODE, TRANSLATE_MODE_POPUP);
    }
    return _chatTranslateMode;
  }

  public void setChatTranslateMode (int mode) {
    if (getChatTranslateMode() != mode) {
      pmc.putInt(KEY_CHAT_TRANSLATE_MODE, _chatTranslateMode = mode);
    }
  }

  public void setTranslateLanguageRecents (String[] recents) {
    pmc.putStringArray(KEY_CHAT_TRANSLATE_RECENTS, recents);
  }

  public void setTranslateLanguageRecents (List<String> recents) {
    String[] out = new String[recents.size()];
    int i = 0;
    for (String recent : recents) {
      out[i++] = recent;
    }
    setTranslateLanguageRecents(out);
  }

  public ArrayList<String> getTranslateLanguageRecents () {
    String[] result = pmc.getStringArray(KEY_CHAT_TRANSLATE_RECENTS);
    if (result != null) {
      return new ArrayList<>(Arrays.asList(result));
    }
    return new ArrayList<>();
  }

  public void clearTranslateLanguageRecents () {
    pmc.remove(KEY_CHAT_TRANSLATE_RECENTS);
  }

  public static final int CHAT_MODE_2LINE = 1;
  public static final int CHAT_MODE_3LINE = 2;
  public static final int CHAT_MODE_3LINE_BIG = 3;

  public interface ChatListModeChangeListener {
    void onChatListModeChanged (int newChatListMode);
  }

  private Integer _chatListMode;
  private ReferenceList<ChatListModeChangeListener> chatListModeListeners;

  public int getChatListMode () {
    if (_chatListMode == null) {
      int defaultMode = CHAT_MODE_3LINE; // TODO determine based on display settings
      _chatListMode = pmc.getInt(KEY_CHAT_LIST_MODE, defaultMode);
    }
    return _chatListMode;
  }

  public void addChatListModeListener (ChatListModeChangeListener listener) {
    if (chatListModeListeners == null) {
      chatListModeListeners = new ReferenceList<>();
    }
    chatListModeListeners.add(listener);
  }

  public void removeChatListModeListener (ChatListModeChangeListener listModeChangeListener) {
    if (chatListModeListeners != null)
      chatListModeListeners.remove(listModeChangeListener);
  }

  public void setChatListMode (int mode) {
    if (getChatListMode() != mode) {
      pmc.putInt(KEY_CHAT_LIST_MODE, _chatListMode = mode);
      if (chatListModeListeners != null) {
        for (ChatListModeChangeListener listener : chatListModeListeners) {
          listener.onChatListModeChanged(mode);
        }
      }
    }
  }


  public interface ChatFolderSettingsListener {
    default void onChatFolderOptionsChanged (@ChatFolderOptions int newOptions) {}
    default void onChatFolderStyleChanged (@ChatFolderStyle int newStyle) {}
  }
  private final ReferenceList<ChatFolderSettingsListener> chatFolderSettingsListeners = new ReferenceList<>();

  public void addChatFolderSettingsListener (ChatFolderSettingsListener listener) {
    chatFolderSettingsListeners.add(listener);
  }

  public void removeChatFolderSettingsListener (ChatFolderSettingsListener listener) {
    chatFolderSettingsListeners.remove(listener);
  }

  private Integer _chatFolderOptions, _chatFolderStyle;

  public void setChatFolderOptions (@ChatFolderOptions int options) {
    if (getChatFolderOptions() != options) {
      if (options == TdlibSettingsManager.DEFAULT_CHAT_FOLDER_OPTIONS) {
        pmc.remove(KEY_CHAT_FOLDER_OPTIONS);
      } else {
        pmc.putInt(KEY_CHAT_FOLDER_OPTIONS, options);
      }
      _chatFolderOptions = options;
      for (ChatFolderSettingsListener listener : chatFolderSettingsListeners) {
        listener.onChatFolderOptionsChanged(options);
      }
    }
  }

  public @ChatFolderOptions int getChatFolderOptions () {
    if (_chatFolderOptions == null) {
      _chatFolderOptions = pmc.getInt(KEY_CHAT_FOLDER_OPTIONS, TdlibSettingsManager.DEFAULT_CHAT_FOLDER_OPTIONS);
    }
    return _chatFolderOptions;
  }

  public void setChatFolderStyle (@ChatFolderStyle int style) {
    if (getChatFolderStyle() != style) {
      if (style == TdlibSettingsManager.DEFAULT_CHAT_FOLDER_STYLE) {
        pmc.remove(KEY_CHAT_FOLDER_STYLE);
      } else {
        pmc.putInt(KEY_CHAT_FOLDER_STYLE, style);
      }
      _chatFolderStyle = style;
      for (ChatFolderSettingsListener listener : chatFolderSettingsListeners) {
        listener.onChatFolderStyleChanged(style);
      }
    }
  }

  public @ChatFolderStyle int getChatFolderStyle () {
    if (_chatFolderStyle == null) {
      _chatFolderStyle = pmc.getInt(KEY_CHAT_FOLDER_STYLE, TdlibSettingsManager.DEFAULT_CHAT_FOLDER_STYLE);
    }
    return _chatFolderStyle;
  }

  private long makeDefaultNewSettings () {
    long settings = 0;

    settings |= SETTING_FLAG_PAUSE_MEDIA_ON_RECORD;
    settings |= SETTING_FLAG_SELECT_TEXT_TO_QUOTE;
    // SETTING_FLAG_KEEP_ALIVE_CONNECTION is off by default: push notifications work
    // through the mod's own Firebase project, the background connection is optional.

    return settings;
  }

  private long getNewSettings () {
    if (_newSettings == null)
      _newSettings = pmc.getLong(KEY_OTHER_NEW, makeDefaultNewSettings());
    return _newSettings;
  }

  public boolean getNewSetting (long key) {
    return BitwiseUtils.hasFlag(getNewSettings(), key);
  }

  private boolean setNewSettings (long newSettings) {
    long oldSettings = getNewSettings();
    if (oldSettings != newSettings) {
      this._newSettings = newSettings;
      pmc.putLong(KEY_OTHER_NEW, newSettings);
      if (newSettingsListeners != null) {
        for (SettingsChangeListener listener : newSettingsListeners) {
          listener.onSettingsChanged(newSettings, oldSettings);
        }
      }
      return true;
    }
    return false;
  }

  private static long makeDefaultExperiments () {
    // TODO: this flag allows implementing later a global toggle that enables/disables all experiments
    // while preserving specific experiments toggle values.
    return EXPERIMENT_FLAG_ALLOW_EXPERIMENTS;
  }

  private long getExperiments () {
    if (_experiments == null) {
      _experiments = pmc.getLong(KEY_EXPERIMENTS, makeDefaultExperiments());
    }
    return _experiments;
  }

  public boolean isExperimentEnabled (long key) {
    long experiments = getExperiments();
    return BitwiseUtils.hasAllFlags(experiments, EXPERIMENT_FLAG_ALLOW_EXPERIMENTS | key);
  }

  public boolean setExperimentEnabled (long key, boolean enabled) {
    long oldExperiments = getExperiments();
    long newExperiments = BitwiseUtils.setFlag(oldExperiments, key, enabled);
    if (oldExperiments != newExperiments) {
      this._experiments = newExperiments;
      pmc.putLong(KEY_EXPERIMENTS, newExperiments);
      return true;
    }
    return false;
  }

  public interface SettingsChangeListener {
    void onSettingsChanged (long newSettings, long oldSettings);
  }

  private ReferenceList<SettingsChangeListener> newSettingsListeners;

  public void addNewSettingsListener (SettingsChangeListener listener) {
    if (newSettingsListeners == null)
      newSettingsListeners = new ReferenceList<>();
    newSettingsListeners.add(listener);
  }

  public void removeNewSettingsListener (SettingsChangeListener listener) {
    if (newSettingsListeners != null) {
      newSettingsListeners.remove(listener);
    }
  }

  public boolean setNewSetting (long key, boolean value) {
    return setNewSettings(BitwiseUtils.setFlag(getNewSettings(), key, value));
  }

  public boolean toggleNewSetting (long key) {
    boolean enabled = !getNewSetting(key);
    setNewSetting(key, enabled);
    return enabled;
  }

  private int getSettings () {
    if (_settings == null)
      _settings = pmc.getInt(KEY_OTHER, makeDefaultSettings());
    return _settings;
  }

  private boolean setSettings (int newSettings) {
    if (getSettings() != newSettings) {
      this._settings = newSettings;
      pmc.putInt(KEY_OTHER, newSettings);
      return true;
    }
    return false;
  }

  private boolean setSetting (int flag, boolean value) {
    return setSettings(BitwiseUtils.setFlag(getSettings(), flag, value));
  }

  private boolean setNegativeSetting (int flag, boolean value) {
    return setSetting(flag, !value);
  }

  private boolean checkSetting (int flag) {
    return BitwiseUtils.hasFlag(getSettings(), flag);
  }

  private boolean checkNegativeSetting (int flag) {
    return !checkSetting(flag);
  }

  private static int makeDefaultSettings () {
    int defaultSettings = 0;

    /*autoplayGIFs = true;
    autoplayGIFs = (settings & FLAG_OTHER_AUTOPLAY_GIFS) != 0;*/
    defaultSettings |= FLAG_OTHER_AUTOPLAY_GIFS;

    /*saveEditedMediaToGallery = false;
    saveEditedMediaToGallery = (settings & FLAG_OTHER_SAVE_TO_GALLERY) != 0;*/

    /*forceArabicNumbers = false;
    forceArabicNumbers = (settings & FLAG_OTHER_FORCE_ARABIC_NUMBERS) != 0;*/

    /*reduceMotion = false;
    reduceMotion = (settings & FLAG_OTHER_REDUCE_MOTION) != 0;*/

    /*useSystemEmoji = false;
    useSystemEmoji = Config.ALLOW_SYSTEM_EMOJI && (settings & FLAG_OTHER_USE_SYSTEM_EMOJI) != 0;*/

    /*dontReadMessages = false;
    dontReadMessages = BuildConfig.DEBUG && (settings & FLAG_OTHER_DONT_READ_MESSAGES) != 0;*/

    /*disableChatQuickShare = true;
    disableChatQuickShare = (settings & FLAG_OTHER_NO_CHAT_QUICK_SHARE) != 0;*/
    defaultSettings |= FLAG_OTHER_NO_CHAT_QUICK_SHARE;

    /*disableChatQuickReply = false;
    disableChatQuickReply = (settings & FLAG_OTHER_NO_CHAT_QUICK_REPLY) != 0;*/

    /*sendByEnter = false;
    sendByEnter = (settings & FLAG_OTHER_SEND_BY_ENTER) != 0;*/

    /*hideChatKeyboard = false;
    hideChatKeyboard = (settings & FLAG_OTHER_HIDE_CHAT_KEYBOARD) != 0;*/

    /*needForwardOptions = false;
    needForwardOptions = (settings & FLAG_OTHER_SHOW_FORWARD_OPTIONS) != 0;*/

    /*needPreviewChatsOnHold = true;
    needPreviewChatsOnHold = (settings & FLAG_OTHER_DISABLE_PREVIEW_CHATS_ON_HOLD) == 0;*/

    /*useInAppBrowser = true;
    useInAppBrowser = (settings & FLAG_OTHER_DISABLE_INAPP_BROWSER) == 0;*/

    /*useCustomTextActions = true;
    useCustomTextActions = (settings & FLAG_OTHER_DISABLE_CUSTOM_TEXT_ACTIONS) == 0;*/

    /*needOutboundCallsPrompt = false;
    needOutboundCallsPrompt = (settings & FLAG_OTHER_OUTBOUND_CALLS_PROMPT) != 0;*/

    /*useCustomVibrations = true;
    useCustomVibrations = (settings & FLAG_OTHER_DISABLE_CUSTOM_VIBRATIONS) == 0;*/

    /*force169Camera = false;
    force169Camera = (settings & FLAG_OTHER_CAMERA_FORCE_16_9) != 0;*/

    /*preferVideoMode = Config.ROUND_VIDEOS_RECORD_SUPPORTED;
    preferVideoMode = Config.ROUND_VIDEOS_RECORD_SUPPORTED && (settings & FLAG_OTHER_PREFER_VIDEO_MODE) != 0;*/
    if (Config.ROUND_VIDEOS_RECORD_SUPPORTED) {
      defaultSettings |= FLAG_OTHER_PREFER_VIDEO_MODE;
    }

    /*needHqRoundVideos = false;
    needHqRoundVideos = (settings & FLAG_OTHER_HQ_ROUND_VIDEOS) != 0;*/

    /*startRoundRear = false;
    startRoundRear = (settings & FLAG_OTHER_START_ROUND_REAR) != 0;*/

    /*disableSecretLinkPreviews = false;
    disableSecretLinkPreviews = (settings & FLAG_OTHER_DISABLE_SECRET_LINK_PREVIEWS) != 0;*/

    /*accountListOpened = false;
    accountListOpened = (settings & FLAG_OTHER_ACCOUNT_LIST_OPENED) != 0;*/

    /*chatFontSizeScaling = false;
    chatFontSizeScaling = (settings & FLAG_OTHER_FONT_SCALING) != 0;*/

    /*rememberAlbumSetting = false;
    rememberAlbumSetting = (settings & FLAG_OTHER_REMEMBER_ALBUM_SETTING) != 0;*/

    /*needGroupMedia = true;
    needGroupMedia = (settings & FLAG_OTHER_NEED_GROUP_MEDIA) != 0;*/
    defaultSettings |= FLAG_OTHER_NEED_GROUP_MEDIA;

    /*separateMediaTab = false;
    separateMediaTab = (settings & FLAG_OTHER_SEPARATE_MEDIA_TAB) != 0;*/

    /*splitChatNotifications = true;
    splitChatNotifications = (settings & FLAG_OTHER_SPLIT_CHAT_NOTIFICATIONS) != 0;*/
    defaultSettings |= FLAG_OTHER_SPLIT_CHAT_NOTIFICATIONS;

    return defaultSettings;

    /*if (needDefault) {
      autoplayGIFs = true;
      saveEditedMediaToGallery = false;
      reduceMotion = false;
      disableChatQuickShare = true;
      disableChatQuickReply = false;
      useSystemEmoji = false;
      sendByEnter = false;
      hideChatKeyboard = false;
      needForwardOptions = false;
      needPreviewChatsOnHold = true;
      useInAppBrowser = true;
      useCustomTextActions = true;
      needOutboundCallsPrompt = false;
      useCustomVibrations = true;
      force169Camera = false;
      preferVideoMode = Config.ROUND_VIDEOS_RECORD_SUPPORTED;
      needHqRoundVideos = false;
      startRoundRear = false;
      disableSecretLinkPreviews = false;
      accountListOpened = false;
      forceArabicNumbers = false;
      chatFontSizeScaling = false;
      rememberAlbumSetting = false;
      needGroupMedia = true;
      separateMediaTab = false;
      splitChatNotifications = true;
    } else {
      autoplayGIFs = (settings & FLAG_OTHER_AUTOPLAY_GIFS) != 0;
      saveEditedMediaToGallery = (settings & FLAG_OTHER_SAVE_TO_GALLERY) != 0;
      forceArabicNumbers = (settings & FLAG_OTHER_FORCE_ARABIC_NUMBERS) != 0;
      reduceMotion = (settings & FLAG_OTHER_REDUCE_MOTION) != 0;
      useSystemEmoji = Config.ALLOW_SYSTEM_EMOJI && (settings & FLAG_OTHER_USE_SYSTEM_EMOJI) != 0;
      dontReadMessages = BuildConfig.DEBUG && (settings & FLAG_OTHER_DONT_READ_MESSAGES) != 0;
      disableChatQuickShare = (settings & FLAG_OTHER_NO_CHAT_QUICK_SHARE) != 0;
      disableChatQuickReply = (settings & FLAG_OTHER_NO_CHAT_QUICK_REPLY) != 0;
      sendByEnter = (settings & FLAG_OTHER_SEND_BY_ENTER) != 0;
      hideChatKeyboard = (settings & FLAG_OTHER_HIDE_CHAT_KEYBOARD) != 0;
      needForwardOptions = (settings & FLAG_OTHER_SHOW_FORWARD_OPTIONS) != 0;
      needPreviewChatsOnHold = (settings & FLAG_OTHER_DISABLE_PREVIEW_CHATS_ON_HOLD) == 0;
      useInAppBrowser = (settings & FLAG_OTHER_DISABLE_INAPP_BROWSER) == 0;
      useCustomTextActions = (settings & FLAG_OTHER_DISABLE_CUSTOM_TEXT_ACTIONS) == 0;
      needOutboundCallsPrompt = (settings & FLAG_OTHER_OUTBOUND_CALLS_PROMPT) != 0;
      useCustomVibrations = (settings & FLAG_OTHER_DISABLE_CUSTOM_VIBRATIONS) == 0;
      force169Camera = (settings & FLAG_OTHER_CAMERA_FORCE_16_9) != 0;
      preferVideoMode = Config.ROUND_VIDEOS_RECORD_SUPPORTED && (settings & FLAG_OTHER_PREFER_VIDEO_MODE) != 0;
      needHqRoundVideos = (settings & FLAG_OTHER_HQ_ROUND_VIDEOS) != 0;
      startRoundRear = (settings & FLAG_OTHER_START_ROUND_REAR) != 0;
      disableSecretLinkPreviews = (settings & FLAG_OTHER_DISABLE_SECRET_LINK_PREVIEWS) != 0;
      accountListOpened = (settings & FLAG_OTHER_ACCOUNT_LIST_OPENED) != 0;
      chatFontSizeScaling = (settings & FLAG_OTHER_FONT_SCALING) != 0;
      rememberAlbumSetting = (settings & FLAG_OTHER_REMEMBER_ALBUM_SETTING) != 0;
      needGroupMedia = (settings & FLAG_OTHER_NEED_GROUP_MEDIA) != 0;
      separateMediaTab = (settings & FLAG_OTHER_SEPARATE_MEDIA_TAB) != 0;
      splitChatNotifications = (settings & FLAG_OTHER_SPLIT_CHAT_NOTIFICATIONS) != 0;
    }*/
  }

   /*private void saveOtherSettings () {
    int flags = 0;
    if (autoplayGIFs) {
      flags |= FLAG_OTHER_AUTOPLAY_GIFS;
    }
    if (saveEditedMediaToGallery) {
      flags |= FLAG_OTHER_SAVE_TO_GALLERY;
    }
    if (reduceMotion) {
      flags |= FLAG_OTHER_REDUCE_MOTION;
    }
    if (useSystemEmoji) {
      flags |= FLAG_OTHER_USE_SYSTEM_EMOJI;
    }
    if (dontReadMessages) {
      flags |= FLAG_OTHER_DONT_READ_MESSAGES;
    }
    if (disableChatQuickShare) {
      flags |= FLAG_OTHER_NO_CHAT_QUICK_SHARE;
    }
    if (disableChatQuickReply) {
      flags |= FLAG_OTHER_NO_CHAT_QUICK_REPLY;
    }
    if (sendByEnter) {
      flags |= FLAG_OTHER_SEND_BY_ENTER;
    }
    if (hideChatKeyboard) {
      flags |= FLAG_OTHER_HIDE_CHAT_KEYBOARD;
    }
    if (needForwardOptions) {
      flags |= FLAG_OTHER_SHOW_FORWARD_OPTIONS;
    }
    if (!needPreviewChatsOnHold) {
      flags |= FLAG_OTHER_DISABLE_PREVIEW_CHATS_ON_HOLD;
    }
    if (!useInAppBrowser) {
      flags |= FLAG_OTHER_DISABLE_INAPP_BROWSER;
    }
    if (!useCustomTextActions) {
      flags |= FLAG_OTHER_DISABLE_CUSTOM_TEXT_ACTIONS;
    }
    if (needOutboundCallsPrompt) {
      flags |= FLAG_OTHER_OUTBOUND_CALLS_PROMPT;
    }
    if (!useCustomVibrations) {
      flags |= FLAG_OTHER_DISABLE_CUSTOM_VIBRATIONS;
    }
    if (force169Camera) {
      flags |= FLAG_OTHER_CAMERA_FORCE_16_9;
    }
    if (preferVideoMode) {
      flags |= FLAG_OTHER_PREFER_VIDEO_MODE;
    }
    if (needHqRoundVideos) {
      flags |= FLAG_OTHER_HQ_ROUND_VIDEOS;
    }
    if (startRoundRear) {
      flags |= FLAG_OTHER_START_ROUND_REAR;
    }
    if (disableSecretLinkPreviews) {
      flags |= FLAG_OTHER_DISABLE_SECRET_LINK_PREVIEWS;
    }
    if (accountListOpened) {
      flags |= FLAG_OTHER_ACCOUNT_LIST_OPENED;
    }
    if (forceArabicNumbers) {
      flags |= FLAG_OTHER_FORCE_ARABIC_NUMBERS;
    }
    if (chatFontSizeScaling) {
      flags |= FLAG_OTHER_FONT_SCALING;
    }
    if (rememberAlbumSetting) {
      flags |= FLAG_OTHER_REMEMBER_ALBUM_SETTING;
    }
    if (needGroupMedia) {
      flags |= FLAG_OTHER_NEED_GROUP_MEDIA;
    }
    if (separateMediaTab) {
      flags |= FLAG_OTHER_SEPARATE_MEDIA_TAB;
    }
    if (splitChatNotifications) {
      flags |= FLAG_OTHER_SPLIT_CHAT_NOTIFICATIONS;
    }
    putInt(KEY_OTHER, flags);
  }*/

  private final LevelDB pmc;

  public LevelDB pmc () {
    return pmc;
  }

  private boolean ignoreFurtherAccountConfigUpgrades;

  private void upgradePmc (LevelDB pmc, SharedPreferences.Editor editor, int version) {
    switch (version) {
      case VERSION_10: {
        /*try {
          int settings = pmc.tryGetInt(KEY_OTHER);
          if ((settings & FLAG_OTHER_REDUCE_MOTION) == 0) {
            settings |= FLAG_OTHER_REDUCE_MOTION;
            editor.putInt(KEY_OTHER, settings);
          }
        } catch (FileNotFoundException ignored) { }*/
        break;
      }
      case VERSION_11: {
        List<String> prefixes = new ArrayList<>();
        int accountNum = TdlibManager.readAccountNum();
        for (int accountId = 0; accountId < accountNum; accountId++)
          prefixes.add(accountId != 0 ? accountId + "_" + KEY_SILENT_CHANNEL_PREFIX : KEY_SILENT_CHANNEL_PREFIX);
        pmc.removeByAnyPrefix(prefixes);
        break;
      }
      case VERSION_12: {
        int mode = pmc.getInt(Passcode.KEY_PASSCODE_MODE, Passcode.MODE_NONE);
        if (mode == Passcode.MODE_BIOMETRICS) {
          String passcodeHash = pmc.getString(Passcode.KEY_PASSCODE_HASH, null);
          if (passcodeHash != null) {
            editor.putString(Passcode.KEY_PASSCODE_BIOMETRICS_HASH, passcodeHash);
          }
        }
        break;
      }
      case VERSION_13: {
        editor.remove("debug_lang");
        break;
      }
      case VERSION_14:
      case VERSION_15: {
        changeDefaultOtherFlag(pmc, editor, FLAG_OTHER_SAVE_TO_GALLERY, false); // at first, it was true for VERSION_14
        break;
      }
      case VERSION_16: {
        // Removing cloud map provider setting, if it's equal to Google
        try {
          int type = pmc.tryGetInt(KEY_MAP_PROVIDER_TYPE_CLOUD);
          if (type == MAP_PROVIDER_GOOGLE) {
            editor.remove(KEY_MAP_PROVIDER_TYPE_CLOUD);
          }
        } catch (FileNotFoundException ignored) {
        }
        // Removing secret chat map provider setting, if it's equal to Google
        try {
          int type = pmc.tryGetInt(KEY_MAP_PROVIDER_TYPE);
          if (type == MAP_PROVIDER_GOOGLE) {
            editor.remove(KEY_MAP_PROVIDER_TYPE);
          }
        } catch (FileNotFoundException ignored) {
        }
        break;
      }
      case VERSION_17: {
        for (final LevelDB.Entry entry : pmc.find(KEY_THEME_NAME)) {
          String key = entry.key();
          final int customThemeId = Integer.parseInt(key.substring(KEY_THEME_NAME.length()));
          if (customThemeId >= 0) {
            int activeColor;
            try {
              activeColor = pmc.tryGetInt(themeColorKey(customThemeId, ColorId.headerText));
            } catch (Throwable ignored) {
              continue;
            }
            String activeKey = themeColorKey(customThemeId, ColorId.headerTabActive);
            String activeTextKey = themeColorKey(customThemeId, ColorId.headerTabActiveText);
            String inactiveTextKey = themeColorKey(customThemeId, ColorId.headerTabInactiveText);
            int barColor = ColorUtils.alphaColor(.9f, activeColor);
            int inactiveColor = ColorUtils.alphaColor(.8f, activeColor);
            if (!pmc.contains(activeKey)) {
              pmc.putInt(activeKey, barColor);
            }
            if (!pmc.contains(activeTextKey)) {
              pmc.putInt(activeTextKey, activeColor);
            }
            if (!pmc.contains(inactiveTextKey)) {
              pmc.putInt(inactiveTextKey, inactiveColor);
            }
          }
        }
        break;
      }
      case VERSION_18: {
        int accountNum = TdlibManager.readAccountNum();
        if (accountNum > 0) {
          ArrayList<String> prefixes = new ArrayList<>(accountNum * 6);
          for (int accountId = 0; accountId < accountNum; accountId++) {
            pmc.remove(TdlibNotificationManager.key(TdlibNotificationManager._NOTIFICATIONS_STACK_KEY, accountId));

            // key(_CUSTOM_PRIORITY_KEY + _CUSTOM_USER_SUFFIX + userId)
            prefixes.add(TdlibNotificationManager.key(TdlibNotificationManager._CUSTOM_PRIORITY_KEY + TdlibNotificationManager._CUSTOM_USER_SUFFIX /*+ userId*/, accountId));

            // key(_CUSTOM_VIBRATE_KEY + _CUSTOM_USER_SUFFIX + userId)
            prefixes.add(TdlibNotificationManager.key(TdlibNotificationManager._CUSTOM_VIBRATE_KEY + TdlibNotificationManager._CUSTOM_USER_SUFFIX /*+ userId*/, accountId));

            // key(_CUSTOM_VIBRATE_ONLYSILENT_KEY + _CUSTOM_USER_SUFFIX + userId)
            prefixes.add(TdlibNotificationManager.key(TdlibNotificationManager._CUSTOM_VIBRATE_ONLYSILENT_KEY + TdlibNotificationManager._CUSTOM_USER_SUFFIX /*+ userId*/, accountId));

            // key(_CUSTOM_SOUND_KEY + _CUSTOM_USER_SUFFIX + userId)
            prefixes.add(TdlibNotificationManager.key(TdlibNotificationManager._CUSTOM_SOUND_KEY + TdlibNotificationManager._CUSTOM_USER_SUFFIX /*+ userId*/, accountId));

            // key(_CUSTOM_SOUND_NAME_KEY + _CUSTOM_USER_SUFFIX + userId)
            prefixes.add(TdlibNotificationManager.key(TdlibNotificationManager._CUSTOM_SOUND_NAME_KEY + TdlibNotificationManager._CUSTOM_USER_SUFFIX /*+ userId*/, accountId));

            // key(_CUSTOM_PINNED_NOTIFICATIONS_KEY + chatId)
            prefixes.add(TdlibNotificationManager.key(TdlibNotificationManager.__CUSTOM_PINNED_NOTIFICATIONS_KEY /*+ chatId*/, accountId));

            editor.remove(TdlibNotificationManager.key(TdlibNotificationManager.__PINNED_MESSAGE_NOTIFICATION_KEY, accountId));

            editor.remove(TdlibNotificationManager.key(TdlibNotificationManager.__PRIVATE_MUTE_KEY, accountId));
            editor.remove(TdlibNotificationManager.key(TdlibNotificationManager.__PRIVATE_PREVIEW_KEY, accountId));
            editor.remove(TdlibNotificationManager.key(TdlibNotificationManager.__GROUP_MUTE_KEY, accountId));
            editor.remove(TdlibNotificationManager.key(TdlibNotificationManager.__GROUP_PREVIEW_KEY, accountId));
            editor.remove(TdlibNotificationManager.key(TdlibNotificationManager.__PINNED_MESSAGE_NOTIFICATION_KEY, accountId));

            editor.remove(TdlibSettingsManager.key(TdlibSettingsManager.__PEER_TO_PEER_KEY, accountId));
          }
          pmc.removeByAnyPrefix(prefixes);
        }
        break;
      }
      case VERSION_19: {
        try {
          int badgeMode = pmc.tryGetInt(KEY_BADGE_MODE);
          int newBadgeMode = 0;
          switch (badgeMode) {
            case 1:
              newBadgeMode = BADGE_FLAG_MESSAGES;
              break;
            case 2:
              newBadgeMode = BADGE_FLAG_MESSAGES | BADGE_FLAG_MUTED;
              break;
          }
          if (newBadgeMode != 0)
            editor.putInt(KEY_BADGE_FLAGS, newBadgeMode);
          editor.remove(KEY_BADGE_MODE);
        } catch (Throwable ignored) {
        }
        changeDefaultOtherFlag(pmc, editor, FLAG_OTHER_SPLIT_CHAT_NOTIFICATIONS, true);
        break;
      }
      case VERSION_20: {
        try {
          File file = new File(TdlibManager.getLegacyLogFilePath(false));
          if (file.exists() && !file.delete()) {
            // nothing?
          }
        } catch (Throwable ignored) {
        }
        try {
          File file = new File(TdlibManager.getLegacyLogFilePath(true));
          if (file.exists() && !file.delete()) {
            // nothing?
          }
        } catch (Throwable ignored) {
        }
        break;
      }
      case VERSION_21: {
        changeDefaultOtherFlag(pmc, editor, FLAG_OTHER_HIDE_SECRET_CHATS, false);
        changeDefaultOtherFlag(pmc, editor, FLAG_OTHER_DISABLE_ADDITIONAL_SYNC, false);
        break;
      }
      case VERSION_22: {
        if (getBoolean("debug_hide_number", false))
          pmc.putInt(KEY_UTILITY_FEATURES, UTILITY_FEATURE_HIDE_NUMBER | pmc.getInt(KEY_UTILITY_FEATURES, 0));
        pmc.removeByAnyPrefix(KEY_PREFIX_RTL, "debug_pinned_notification", "debug_hide_number", "debug_encrypted_push");
        break;
      }
      case VERSION_23: {
        pmc.remove(KEY_PUSH_USER_IDS);
        break;
      }
      case VERSION_24: {
        int accountNum = TdlibManager.readAccountNum();
        for (int accountId = 0; accountId < accountNum; accountId++) {
          editor.remove(TdlibSettingsManager.key(TdlibSettingsManager.DEVICE_TOKEN_OR_ENDPOINT_KEY, accountId));
          editor.remove(TdlibSettingsManager.key(TdlibSettingsManager.DEVICE_UID_KEY, accountId));
          editor.remove(TdlibSettingsManager.key(TdlibSettingsManager.DEVICE_OTHER_UID_KEY, accountId));
        }
        break;
      }
      case VERSION_25: {
        changeDefaultOtherFlag(pmc, editor, FLAG_OTHER_USE_SYSTEM_FONTS, false);
        break;
      }
      case VERSION_26: {
        changeDefaultOtherFlag(pmc, editor, FLAG_OTHER_DISABLE_BIG_EMOJI, false);
        break;
      }
      case VERSION_27: {
        int accountNum = TdlibManager.readAccountNum();
        for (int accountId = 0; accountId < accountNum; accountId++) {
          final String globalPrefix = (accountId != 0 ? KEY_WALLPAPER_PREFIX + "_" + accountId : KEY_WALLPAPER_PREFIX);
          for (int usageIdentifier = 0; usageIdentifier < 2; usageIdentifier++) {
            String prefix = globalPrefix + getWallpaperIdentifierSuffix(usageIdentifier);
            if (getBoolean(prefix + KEY_WALLPAPER_EMPTY, false) || getBoolean(prefix + KEY_WALLPAPER_CUSTOM, false)) {
              editor.remove(prefix);
              continue;
            }
            final long id = getLong(prefix, 0);
            editor.remove(prefix);
            if (id != 0) {
              final String persistentId = getString(prefix + KEY_WALLPAPER_ID, null);
              editor.remove(prefix + KEY_WALLPAPER_ID);
              TGBackground.migrateLegacyWallpaper(editor, prefix, BitwiseUtils.splitLongToFirstInt(id), BitwiseUtils.splitLongToSecondInt(id), persistentId);
            }
          }
          for (LevelDB.Entry entry : pmc.find(globalPrefix + "_other")) {
            final String prefix = entry.key();
            if (prefix.matches(globalPrefix + "_other\\d+")) {
              final long id = getLong(prefix, 0);
              editor.remove(prefix);
              if (id != 0) {
                final String persistentId = getString(prefix + KEY_WALLPAPER_ID, null);
                editor.remove(prefix + KEY_WALLPAPER_ID);
                TGBackground.migrateLegacyWallpaper(editor, prefix, BitwiseUtils.splitLongToFirstInt(id), BitwiseUtils.splitLongToSecondInt(id), persistentId);
              }
            }
          }
        }
        /*if (target != null || isSolid() || isEmpty()) {
          if (target != null && !TD.isFileLoadedAndExists(target.getFile())) {
            TdlibAccount account = TdlibManager.instance().account(accountId);
            if (!account.isUnauthorized()) {
              account.tdlib().files().downloadFile(target.getFile());
            }
          }
          SharedPreferences.Editor editor = Settings.instance().edit();
          final String key = (accountId != 0 ? "wallpaper_" + accountId : "wallpaper") + Settings.getWallpaperIdentifierSuffix(usageIdentifier);
          if (isEmpty) {
            editor.putBoolean(key + "_empty", true);
          } else {
            editor.remove(key + "_empty");
          }
          editor.putBoolean(key + "_custom", customPath != null);
          if (customPath != null) {
            editor.putString(key + "_path", customPath);
          } else {
            editor.remove(key + "_path");
            editor.putLong(key, U.mergeLong(id, color));
            if (target == null) {
              editor.remove(key + "_id");
            } else {
              editor.putString(key + "_id", target.getRemoteId());
            }
          }
          editor.apply();
        }

        public TGWallpaper restoreWallpaper (Tdlib tdlib, int usageIdentifier) {
          final int accountId = tdlib.id();
          final String prefix = (accountId != 0 ? KEY_WALLPAPER_PREFIX + "_" + accountId : KEY_WALLPAPER_PREFIX) + getWallpaperIdentifierSuffix(usageIdentifier);

          boolean isCustom = getBoolean(prefix + KEY_WALLPAPER_CUSTOM, false);
          if (isCustom) {
            String path = getString(prefix + KEY_WALLPAPER_PATH, null);
            if (path != null && new java.io.File(path).exists()) {
              return new TGWallpaper(tdlib, path);
            }
          }
          long id = getLong(prefix, 0);
          if (id != 0) {
            int wallpaperId = U.splitLongToFirstInt(id);
            int color = U.splitLongToSecondInt(id);
            String persistentId = getString(prefix + KEY_WALLPAPER_ID, null);
            return new TGWallpaper(tdlib, wallpaperId, color, persistentId);
          }
          return null;
        }*/
        break;
      }
      case VERSION_28: {
        // changeDefaultOtherFlag(pmc, editor, FLAG_OTHER_DISABLE_RESTRICTIONS, false);
        break;
      }
      case VERSION_29: {
        try {
          int markdownMode = pmc.tryGetInt(KEY_MARKDOWN_MODE);
          if (markdownMode == 1) { // MARKDOWN_MODE_TEXT_ONLY
            setNewSetting(SETTING_FLAG_EDIT_MARKDOWN, true);
          }
          editor.remove(KEY_MARKDOWN_MODE);
        } catch (FileNotFoundException ignored) {
        }
        break;
      }
      case VERSION_30: {
        editor.remove(KEY_PREFER_LEGACY_API);
        changeDefaultOtherFlag(pmc, editor, DISABLED_FLAG_OTHER_CAMERA_FORCE_16_9, false);
        break;
      }
      case VERSION_31: {
        U.moveUnsafePrivateMedia();
        break;
      }
      case VERSION_32: {
        File zoomTables = new File(UI.getAppContext().getFilesDir(), "ZoomTables.data");
        if (zoomTables.exists() && !zoomTables.delete()) {

        }
        break;
      }
      case VERSION_33: {
        if (NIGHT_MODE_DEFAULT == NIGHT_MODE_SYSTEM) {
          int mode = pmc.getInt(KEY_NIGHT_MODE, NIGHT_MODE_SYSTEM);
          if (mode == NIGHT_MODE_NONE || mode == NIGHT_MODE_SYSTEM) {
            int preferredAccountId = TdlibManager.readPreferredAccountId();
            int globalThemeDaylight = TdlibSettingsManager.getThemeId(this, preferredAccountId, false);
            int globalThemeNight = TdlibSettingsManager.getThemeId(this, preferredAccountId, true);
            if (ThemeManager.isCustomTheme(globalThemeNight) || ThemeManager.isCustomTheme(globalThemeDaylight)) {
              editor.putInt(KEY_NIGHT_MODE, NIGHT_MODE_NONE);
            } else {
              editor.remove(KEY_NIGHT_MODE);
            }
          }
        }
        break;
      }
      case VERSION_34: {
        int accountNum = TdlibManager.readAccountNum();
        for (int accountId = 0; accountId < accountNum; accountId++) {
          String prefix = key(KEY_SCROLL_CHAT_PREFIX, accountId);
          for (final LevelDB.Entry entry : pmc.find(prefix)) {
            String suffix = entry.key().substring(prefix.length()).replaceAll("^\\d+_([^_]+).*$", "$1");
            if (suffix.equals(KEY_SCROLL_CHAT_RETURN_TO_MESSAGE_ID)) {
              long returnToMessageId = entry.asLong();
              editor.remove(entry.key());
              if (returnToMessageId != 0) {
                String newKey = entry.key().replace(KEY_SCROLL_CHAT_RETURN_TO_MESSAGE_ID, KEY_SCROLL_CHAT_RETURN_TO_MESSAGE_IDS_STACK);
                pmc.putLongArray(newKey, new long[] {returnToMessageId});
              }
            }
          }
        }
        break;
      }
      case VERSION_35: {
        int accountNum = TdlibManager.readAccountNum();
        for (int accountId = 0; accountId < accountNum; accountId++) {
          String prefix = key(TdlibSettingsManager.CONVERSION_PREFIX, accountId);
          pmc.removeByPrefix(prefix);
        }
        break;
      }
      case VERSION_36: {
        editor
          .remove(KEY_TON_LOG_SIZE)
          .remove(KEY_TON_OTHER)
          .remove(KEY_TON_VERBOSITY);
        break;
      }
      case VERSION_37: {
        final boolean needLog = Log.checkLogLevel(Log.LEVEL_VERBOSE);

        final String[] whitelist = {
          "name",
          "type",
          "custom",

          "blurred",
          "moving",
          "intensity",

          "empty",
          "vector",

          "color",
          "colors",
          "fill"
        };
        // remove: any other key matching "wallpaper_[a-zA-Z0-9]+"
        for (final LevelDB.Entry entry : pmc.find("wallpaper_")) {
          final String suffix = entry.key().substring("wallpaper_".length());
          if (!StringUtils.isNumeric(suffix) &&
            suffix.matches("^[a-zA-Z0-9]+$") &&
            !suffix.startsWith("other") &&
            !ArrayUtils.contains(whitelist, suffix)
          ) {
            if (needLog) {
              Log.v("Removing rudimentary key: %s", entry.key());
            }
            editor.remove(entry.key());
          }
        }
        break;
      }
      case VERSION_38: {
        int accountNum = TdlibManager.readAccountNum();
        for (int accountId = 0; accountId < accountNum; accountId++) {
          String[] intToLongKeys = {
            accountInfoPrefix(accountId) + Settings.KEY_ACCOUNT_INFO_SUFFIX_ID,
            TdlibSettingsManager.key(TdlibSettingsManager.DEVICE_UID_KEY, accountId)
          };
          String[] intToLongArrayKeys = {
            TdlibSettingsManager.key(TdlibSettingsManager.DEVICE_OTHER_UID_KEY, accountId)
          };
          for (String key : intToLongKeys) {
            long int32 = pmc.getIntOrLong(key, 0);
            if (int32 != 0) {
              editor.putLong(key, int32);
            } else {
              editor.remove(key);
            }
          }
          for (String key : intToLongArrayKeys) {
            int[] int32Array = null;
            try {
              int32Array = pmc.getIntArray(key);
            } catch (IllegalStateException ignored) {
              // Since it's just DEVICE_OTHER_UID_KEY, it's not critical
            }
            if (int32Array != null) {
              long[] int64Array = new long[int32Array.length];
              for (int i = 0; i < int32Array.length; i++) {
                int64Array[i] = int32Array[i];
              }
              pmc.putLongArray(key, int64Array);
            } else {
              editor.remove(key);
            }
          }
        }

        upgradeAccountsConfig(TdlibAccount.VERSION_1);
        break;
      }
      case VERSION_39: {
        pmc.removeByPrefix(KEY_TDLIB_CRASH_PREFIX);
        break;
      }
      case VERSION_40: {
        pmc
          .remove("crash_id_debug")
          .remove("crash_id_release")
          .remove("crash_id_reported_debug")
          .remove("crash_id_reported_release");
        break;
      }
      case VERSION_41: {
        deleteAllLogs(false, null);
        break;
      }
      case VERSION_42: {
        int accountNum = TdlibManager.readAccountNum();
        for (int accountId = 0; accountId < accountNum; accountId++) {
          editor.remove(TdlibSettingsManager.key(TdlibSettingsManager.__DEVICE_TDLIB_VERSION_KEY, accountId));
        }
        break;
      }
      case VERSION_43: {
        String[] emojis = pmc.getStringArray(KEY_EMOJI_RECENTS);
        if (emojis != null && emojis.length > 0) {
          Map<String, RecentInfo> infos = new HashMap<>();
          getBinaryMap(KEY_EMOJI_COUNTERS, infos, RecentInfo.class);

          int changedCount = 0;
          int changedEmojiCounters = 0;
          for (int index = 0; index < emojis.length; index++) {
            final String oldEmoji = emojis[index];
            // Save 15*2 bytes per recent custom emoji by simply reducing prefix size
            if (oldEmoji.startsWith(Emoji.CUSTOM_EMOJI_CACHE_OLD)) {
              String newEmoji = Emoji.CUSTOM_EMOJI_CACHE + oldEmoji.substring(Emoji.CUSTOM_EMOJI_CACHE_OLD.length());
              emojis[index] = newEmoji;
              changedCount++;

              RecentInfo recentInfo = infos.remove(oldEmoji);
              if (recentInfo != null) {
                infos.put(newEmoji, recentInfo);
                changedEmojiCounters++;
              }
            }
          }
          if (changedCount > 0) {
            pmc.putStringArray(KEY_EMOJI_RECENTS, emojis);
          }
          if (changedEmojiCounters > 0) {
            saveBinaryMap(KEY_EMOJI_COUNTERS, infos);
          }
        }
        break;
      }
      case VERSION_44: {
        upgradeAccountsConfig(TdlibAccount.VERSION_2);
        break;
      }
      case VERSION_45: {
        resetOtherFlag(pmc, editor, FLAG_OTHER_DISABLE_BIG_EMOJI, false);
        break;
      }
      case VERSION_46: {
        long experiments = pmc.getLong(KEY_EXPERIMENTS, makeDefaultExperiments());
        if (BitwiseUtils.hasFlag(experiments, REMOVED_EXPERIMENT_FLAG_ENABLE_FOLDERS)) {
          experiments &= ~REMOVED_EXPERIMENT_FLAG_ENABLE_FOLDERS;
          editor.putLong(KEY_EXPERIMENTS, experiments);
        }
        break;
      }
      case VERSION_47: {
        // No features were officially available before VERSION_46.
        // Reset just once in VERSION_47 right prior to stable release.
        editor.putLong(KEY_FEATURES, 0);
        break;
      }
      case VERSION_48: {
        int passcodeMode = pmc.getInt(Passcode.KEY_PASSCODE_MODE, Passcode.MODE_NONE);
        if (Passcode.isValidMode(passcodeMode) && passcodeMode != Passcode.MODE_NONE) {
          String extraBiometricsHash = passcodeMode != Passcode.MODE_BIOMETRICS ? pmc.getString(Passcode.KEY_PASSCODE_BIOMETRICS_HASH, null) : null;
          boolean usesBiometrics = passcodeMode == Passcode.MODE_BIOMETRICS || extraBiometricsHash != null;
          if (usesBiometrics) {
            boolean strongEnrolled = BiometricAuthentication.isStrongAvailable(true);
            pmc.putInt(Passcode.KEY_PASSCODE_BIOMETRICS_OPTIONS, BitwiseUtils.optional(Passcode.BIOMETRICS_OPTION_ONLY_STRONG, strongEnrolled));
          }
        }
        break;
      }
    }
  }

  private void resetOtherFlag (LevelDB pmc, SharedPreferences.Editor editor, int flag, boolean value) {
    int defaultSettings = makeDefaultSettings();
    int oldSettings = pmc.getInt(KEY_OTHER, defaultSettings);
    int newSettings = BitwiseUtils.setFlag(oldSettings, flag, value);
    if (oldSettings != newSettings) {
      if (newSettings != defaultSettings) {
        editor.putInt(KEY_OTHER, newSettings);
      } else {
        editor.remove(KEY_OTHER);
      }
    }
  }

  private void upgradeAccountsConfig (int fromConfigVersion) {
    if (ignoreFurtherAccountConfigUpgrades) {
      return;
    }
    File oldConfigFile = TdlibManager.getAccountConfigFile();
    File backupFile = new File(oldConfigFile.getParentFile(), oldConfigFile.getName() + ".bak." + fromConfigVersion);
    if (oldConfigFile.exists() && !backupFile.exists()) {
      TdlibManager.AccountConfig config = null;
      try (RandomAccessFile r = new RandomAccessFile(oldConfigFile, TdlibManager.MODE_R)) {
        config = TdlibManager.readAccountConfig(null, r, fromConfigVersion, false);
      } catch (IOException e) {
        Log.e(e);
      }
      if (config != null) {
        File newConfigFile = new File(oldConfigFile.getParentFile(), oldConfigFile.getName() + ".tmp");
        try {
          if (newConfigFile.exists() || newConfigFile.createNewFile()) {
            try (RandomAccessFile r = new RandomAccessFile(newConfigFile, TdlibManager.MODE_RW)) {
              TdlibManager.writeAccountConfigFully(r, config);
              ignoreFurtherAccountConfigUpgrades = true;
            } catch (IOException e) {
              Tracer.onLaunchError(e);
              throw new DeviceStorageError(e);
            }
          }
          if (!oldConfigFile.renameTo(backupFile))
            throw new DeviceStorageError("Cannot backup old config");
          if (!newConfigFile.renameTo(oldConfigFile))
            throw new DeviceStorageError("Cannot save new config");
        } catch (Throwable t) {
          Tracer.onLaunchError(t);
          throw new DeviceStorageError(t);
        }
      }
    }
  }

  private void changeDefaultOtherFlag (LevelDB pmc, SharedPreferences.Editor editor, int flag, boolean value) {
    try {
      int settings = pmc.tryGetInt(KEY_OTHER);
      int newSettings = BitwiseUtils.setFlag(settings, flag, value);
      if (settings != newSettings) {
        editor.putInt(KEY_OTHER, newSettings);
      }
    } catch (FileNotFoundException ignored) {
    }
  }

  private boolean needProxyLegacyMigrateCheck;

  public boolean needProxyLegacyMigrateCheck () {
    if (needProxyLegacyMigrateCheck) {
      needProxyLegacyMigrateCheck = false;
      return true;
    }
    return false;
  }

  private boolean isFreshAppInstallation () {
    return !TdlibManager.getAccountConfigFile().exists() && pmc.getLong(KEY_APP_INSTALLATION_ID, 0) == 0;
  }

  private void migratePrefsToPmc () {
    // Main

    SharedPreferences main = UI.getAppContext().getSharedPreferences(STORAGE_MAIN, Context.MODE_PRIVATE);
    Log.load(main);

    final int settingsVersion = main.getInt(KEY_VERSION, 0);
    if (settingsVersion != LEGACY_VERSION) {
      for (int i = settingsVersion + 1; i <= LEGACY_VERSION; i++) {
        SharedPreferences.Editor editor = main.edit();
        upgradeSharedPreferences(main, editor, i);
        editor.putInt(KEY_VERSION, i);
        editor.apply();
      }
    }

    SharedPreferences.Editor editor = null;
    Blob blob = null;

    Map<String, ?> mainItems = main.getAll();
    Integer pipX = null, pipY = null;

    if (mainItems != null && !mainItems.isEmpty()) {
      editor = pmc.edit();
      for (Map.Entry<String, ?> entry : mainItems.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        switch (key) {
          case KEY_PIP_X:
            if (value instanceof Integer)
              pipX = (Integer) value;
            continue;
          case KEY_PIP_Y:
            if (value instanceof Integer)
              pipY = (Integer) value;
            continue;
          case KEY_LAST_LOCATION:
          case KEY_LAST_INLINE_LOCATION:
            if (value instanceof String) {
              LastLocation location = parseLocation((String) value, KEY_LAST_LOCATION.equals(key) ? "," : "x");
              if (location != null) {
                if (blob == null) {
                  blob = new Blob(8 + 8 + 4);
                } else {
                  blob.seekToStart();
                  blob.ensureCapacity(8 + 8 + 4);
                }
                location.saveTo(blob);
                pmc.putByteArray(key, blob.toByteArray());
              }
            }
            continue;
          case KEY_PUSH_USER_IDS:
            if (value instanceof String) {
              int[] array = StringUtils.parseIntArray((String) value, ",");
              if (array != null) {
                pmc.putIntArray(key, array);
              }
            }
            continue;
          case KEY_PUSH_USER_ID:
            continue;
          default:
            if (key.startsWith(KEY_SCROLL_CHAT_PREFIX)) {
              int lastIndex = key.lastIndexOf('_');
              if (lastIndex == -1)
                break;
              String suffix = key.substring(lastIndex + 1);
              if (StringUtils.isNumeric(suffix)) {
                int accountId = StringUtils.parseInt(suffix);
                key = key(key.substring(0, lastIndex), accountId);
              }
              if (key.endsWith(KEY_SCROLL_CHAT_ALIASES)) {
                if (value instanceof String) {
                  long[] aliasMessageIds = StringUtils.parseLongArray((String) value, ",");
                  if (aliasMessageIds != null && aliasMessageIds.length > 0) {
                    pmc.putLongArray(key, aliasMessageIds);
                  }
                }
                continue;
              }
            } else if (key.startsWith(TdlibNotificationManager._NOTIFICATIONS_STACK_KEY)) {
              if (value instanceof String) {
                String[] items = ((String) value).split(",");
                if (items.length > 0) {
                  int estimatedSize = (8 + 8 + 4) * items.length;
                  if (blob == null) {
                    blob = new Blob(estimatedSize);
                  } else {
                    blob.seekToStart();
                    blob.ensureCapacity(estimatedSize);
                  }

                  for (String item : items) {
                    String[] data = item.split("_");
                    int flags = data.length > 2 ? Integer.valueOf(data[2]) : 0;
                    long chatId = Long.valueOf(data[0]);
                    long messageId = Long.valueOf(data[1]);
                    blob.writeLong(chatId);
                    blob.writeLong(messageId);
                    blob.writeVarint(flags);
                  }
                  pmc.putByteArray(key, blob.toByteArray());
                }
              }
              continue;
            } else if (key.contains(KEY_SILENT_CHANNEL_PREFIX)) {
              continue;
            }
            break;
        }
        if (value instanceof Integer) {
          editor.putInt(key, (int) value);
        } else if (value instanceof Long) {
          editor.putLong(key, (long) value);
        } else if (value instanceof Float) {
          editor.putFloat(key, (float) value);
        } else if (value instanceof Boolean) {
          editor.putBoolean(key, (boolean) value);
        } else if (value instanceof String) {
          editor.putString(key, (String) value);
        } else {
          Log.e("Unknown preferences value, key:%s, value:%s", key, value);
          throw new UnsupportedOperationException("key = " + key + " value = " + value + " (" + (value != null ? value.getClass().getSimpleName() : "null"));
        }
      }
      if (pipX != null && pipY != null) {
        long pip = BitwiseUtils.mergeLong(pipX, pipY);
        editor.putLong(KEY_PIP, pip);
      }
    }

    // Bots. Moving as is

    SharedPreferences bots = null;
    File botsPrefs = U.sharedPreferencesFile(STORAGE_BOTS);
    if (botsPrefs != null) {
      bots = UI.getAppContext().getSharedPreferences(STORAGE_BOTS, Context.MODE_PRIVATE);
      editor = movePreferences(bots, pmc, editor, null);
    }

    // Keyboard. Moving with prefix, e.g. "keyboard_size0"

    SharedPreferences keyboard = null;
    File keyboardPrefs = U.sharedPreferencesFile(STORAGE_KEYBOARD);
    if (keyboardPrefs != null) {
      keyboard = UI.getAppContext().getSharedPreferences(STORAGE_KEYBOARD, Context.MODE_PRIVATE);
      editor = movePreferences(keyboard, pmc, editor, "keyboard_");
    }

    // Emoji

    SharedPreferences emoji = UI.getAppContext().getSharedPreferences(STORAGE_EMOJI, Context.MODE_PRIVATE);
    Map<String, ?> allEmoji = emoji.getAll();
    if (allEmoji != null && !allEmoji.isEmpty()) {
      if (editor == null) {
        editor = pmc.edit();
      }

      Map<String, RecentInfo> infos = null;
      List<RecentEmoji> recents = null;
      Object countersRaw = allEmoji.get(KEY_EMOJI_COUNTERS_OLD);
      if (countersRaw != null && countersRaw instanceof String) {
        infos = new HashMap<>();
        parseEmojiCounters(infos, (String) countersRaw);
      }
      if (infos != null) {
        Object recentsRaw = allEmoji.get(KEY_EMOJI_RECENTS_OLD);
        if (recentsRaw != null && recentsRaw instanceof String) {
          recents = new ArrayList<>();
          parseEmojiRecents(infos, recents, (String) recentsRaw);
        }
      }
      if (infos != null && !infos.isEmpty() && recents != null && !recents.isEmpty()) {
        setEmojiCounters(infos);
        setEmojiRecents(recents);
      }

      Object colorsRaw = allEmoji.get(KEY_EMOJI_COLORS_OLD);
      if (colorsRaw != null && colorsRaw instanceof String) {
        Map<String, String> colors = new HashMap<>();
        parseEmojiColors(colors, (String) colorsRaw);
        setEmojiColors(colors, editor);
      }

      Object defaultColorRaw = allEmoji.get(KEY_EMOJI_DEFAULT_COLOR_OLD);
      if (defaultColorRaw != null && defaultColorRaw instanceof String) {
        editor.putString(KEY_EMOJI_DEFAULT_COLOR, (String) defaultColorRaw);
      }
    }

    if (editor != null) {
      editor.apply();
    }

    if (mainItems != null && BuildConfig.DEBUG) {
      for (Map.Entry<String, ?> entry : mainItems.entrySet()) {
        String key = entry.getKey();
        switch (key) {
          case KEY_PIP_X:
          case KEY_PIP_Y:

          case KEY_LAST_LOCATION:
          case KEY_LAST_INLINE_LOCATION:

          case KEY_PUSH_USER_IDS:
          case KEY_PUSH_USER_ID:
            continue;

          default:
            if (key.startsWith(KEY_SCROLL_CHAT_PREFIX) ||
              key.startsWith(TdlibNotificationManager._NOTIFICATIONS_STACK_KEY)) {
              continue;
            }
            break;
        }
        Object value = entry.getValue();
        try {
          if (value instanceof Integer) {
            int newValue = pmc.tryGetInt(key);
            Test.assertEquals((int) value, newValue);
          } else if (value instanceof Long) {
            long newValue = pmc.tryGetLong(key);
            Test.assertEquals((long) value, newValue);
          } else if (value instanceof Float) {
            float newValue = pmc.tryGetFloat(key);
            Test.assertEquals((float) value, newValue);
          } else if (value instanceof Boolean) {
            boolean newValue = pmc.tryGetBoolean(key);
            Test.assertEquals((boolean) value, newValue);
          } else if (value instanceof String) {
            String newValue = pmc.tryGetString(key);
            Test.assertEquals((String) value, newValue);
          } else {
            throw new UnsupportedOperationException("key = " + key + " value = " + value + " (" + (value != null ? value.getClass().getSimpleName() : "null"));
          }
        } catch (FileNotFoundException e) {
          throw new RuntimeException(key + " not found");
        } catch (AssertionError e) {
          throw new RuntimeException(key + " not equals: " + value + " (" + (value != null ? value.getClass().getSimpleName() : "null") + ")");
        }
      }
    }

    if (bots != null) {
      bots.edit().clear().apply();
    }
    U.deleteSharedPreferences(STORAGE_BOTS);
    if (keyboard != null) {
      keyboard.edit().clear().apply();
    }
    U.deleteSharedPreferences(STORAGE_KEYBOARD);
    emoji.edit().clear().apply();
    U.deleteSharedPreferences(STORAGE_EMOJI);
    main.edit().clear().apply();
    U.deleteSharedPreferences(STORAGE_MAIN);

    File debugProxyFile = getProxyConfigFile();
    if (debugProxyFile.exists() && !debugProxyFile.delete()) {
      Log.w("Cannot delete debug proxy config file");
    }

    File proxyFile = getProxyConfigFile();
    if (proxyFile.exists()) {
      if (proxyFile.length() > 0) {
        TdApi.Proxy proxy = null;
        try (RandomAccessFile r = new RandomAccessFile(proxyFile, "r")) {
          proxy = readProxy(r);
        } catch (IOException e) {
          Log.e(e);
        }
        if (proxy != null) {
          addOrUpdateProxy(proxy, null, true);
        }
      }
      if (!proxyFile.delete()) {
        Log.w("Cannot delete proxy config file");
      }
    } else {
      needProxyLegacyMigrateCheck = true;
    }
  }

  @Deprecated
  public static File getProxyConfigFile () {
    return new File(UI.getAppContext().getFilesDir(), /*debug ? "tdlib_proxy_debug.bin" :*/ "tdlib_proxy.bin");
  }

  @Deprecated
  private static TdApi.Proxy readProxy (RandomAccessFile file) throws IOException {
    switch (Blob.readVarint(file)) {
      case 1456461592: {
        String server = Blob.readString(file);
        int port = Blob.readVarint(file);
        byte flags = Blob.readByte(file);
        String username = (flags & 1) != 0 ? Blob.readString(file) : "";
        String password = (flags & 2) != 0 ? Blob.readString(file) : "";
        return new TdApi.Proxy(
          server,
          port,
          new TdApi.ProxyTypeSocks5(username, password)
        );
      }
      default:
        return null;
    }
  }

  private static SharedPreferences.Editor movePreferences (SharedPreferences from, SharedPreferences to, SharedPreferences.Editor editor, String keyPrefix) {
    if (from == null) {
      return editor;
    }
    Map<String, ?> all = from.getAll();
    if (all == null || all.isEmpty()) {
      return editor;
    }
    if (editor == null) {
      editor = to.edit();
    }
    for (Map.Entry<String, ?> entry : all.entrySet()) {
      String key = entry.getKey();
      if (keyPrefix != null) {
        key = keyPrefix + key;
      }
      Object value = entry.getValue();
      if (value instanceof Boolean) {
        editor.putBoolean(key, (boolean) value);
      } else if (value instanceof Integer) {
        editor.putInt(key, (int) value);
      } else if (value instanceof Long) {
        editor.putLong(key, (long) value);
      } else if (value instanceof String) {
        editor.putString(key, (String) value);
      } else if (value instanceof Float) {
        editor.putFloat(key, (float) value);
      } else {
        Log.e("Unknown value type, key:%s value:%b", key, value);
      }
    }
    return editor;
  }

  public void resetTutorials () {
    setTutorialFlags(0);
  }

  public boolean needTutorial (long flag) {
    return !BitwiseUtils.hasFlag(getTutorialFlags(), flag) && !BitwiseUtils.hasFlag(shownTutorials, flag);
  }

  public boolean needTutorial (@NonNull TdApi.ChatSource source) {
    switch (source.getConstructor()) {
      case TdApi.ChatSourcePublicServiceAnnouncement.CONSTRUCTOR:
        String type = ((TdApi.ChatSourcePublicServiceAnnouncement) source).type;
        return !pmc.contains(StringUtils.isEmpty(type) ? KEY_TUTORIAL_PSA : KEY_TUTORIAL_PSA + type);
      case TdApi.ChatSourceMtprotoProxy.CONSTRUCTOR:
        return needTutorial(TUTORIAL_PROXY_SPONSOR);
      default:
        Td.assertChatSource_12b21238();
        throw Td.unsupported(source);
    }
  }

  public void markTutorialAsComplete (@NonNull TdApi.ChatSource source) {
    switch (source.getConstructor()) {
      case TdApi.ChatSourcePublicServiceAnnouncement.CONSTRUCTOR:
        String type = ((TdApi.ChatSourcePublicServiceAnnouncement) source).type;
        pmc.putVoid(StringUtils.isEmpty(type) ? KEY_TUTORIAL_PSA : KEY_TUTORIAL_PSA + type);
        break;
      case TdApi.ChatSourceMtprotoProxy.CONSTRUCTOR:
        markTutorialAsComplete(TUTORIAL_PROXY_SPONSOR);
        break;
    }
  }

  public boolean inDeveloperMode () {
    return BuildConfig.DEBUG || !needTutorial(TUTORIAL_DEVELOPER_MODE);
  }

  private long shownTutorials;

  public void markTutorialAsShown (long flag) {
    shownTutorials |= flag;
  }

  public void markTutorialAsComplete (long flag) {
    setTutorialFlags(getTutorialFlags() | flag);
  }

  private long getTutorialFlags () {
    if (_tutorialFlags == null)
      _tutorialFlags = pmc.getLong(KEY_TUTORIAL, 0);
    return _tutorialFlags;
  }

  private void setTutorialFlags (long flags) {
    this._tutorialFlags = flags;
    if (flags == 0)
      remove(KEY_TUTORIAL);
    else
      putLong(KEY_TUTORIAL, flags);
  }

  public boolean needSecretLinkPreviews () {
    return checkNegativeSetting(FLAG_OTHER_DISABLE_SECRET_LINK_PREVIEWS);
  }

  public void setUseSecretLinkPreviews (boolean use) {
    if (setNegativeSetting(FLAG_OTHER_DISABLE_SECRET_LINK_PREVIEWS, use)) {
      markTutorialAsComplete(TUTORIAL_SECRET_LINK_PREVIEWS);
    }
  }

  public int getPreferredAudioPlaybackMode () {
    if (_preferredAudioPlaybackMode == null)
      _preferredAudioPlaybackMode = pmc.getInt(KEY_PREFERRED_PLAYBACK_MODE, 0);
    return _preferredAudioPlaybackMode;
  }

  public boolean needSaveEditedMediaToGallery () {
    return checkSetting(FLAG_OTHER_SAVE_TO_GALLERY);
  }

  public void setSaveEditedMediaToGallery (boolean saveToGallery) {
    setSetting(FLAG_OTHER_SAVE_TO_GALLERY, saveToGallery);
  }

  public boolean rememberAlbumSetting () {
    return checkSetting(FLAG_OTHER_REMEMBER_ALBUM_SETTING);
  }

  public void setRememberAlbumSetting (boolean remember) {
    setSetting(FLAG_OTHER_REMEMBER_ALBUM_SETTING, remember);
  }

  public boolean needGroupMedia () {
    return checkSetting(FLAG_OTHER_NEED_GROUP_MEDIA);
  }

  public void setNeedGroupMedia (boolean group) {
    setSetting(FLAG_OTHER_NEED_GROUP_MEDIA, group);
  }

  public boolean needSeparateMediaTab () {
    return checkSetting(FLAG_OTHER_SEPARATE_MEDIA_TAB);
  }

  public void setNeedSeparateMediaTab (boolean separate) {
    setSetting(FLAG_OTHER_SEPARATE_MEDIA_TAB, separate);
  }

  public boolean needAutoplayGIFs () {
    return checkSetting(FLAG_OTHER_AUTOPLAY_GIFS);
  }

  public void setAutoplayGIFs (boolean autoplayGIFs) {
    setSetting(FLAG_OTHER_AUTOPLAY_GIFS, autoplayGIFs);
  }

  public void setUseQuickTranslation (boolean useQuickTranslation) {
    setSetting(FLAG_OTHER_USE_QUICK_TRANSLATION, useQuickTranslation);
  }

  public boolean needUseQuickTranslation () {
    return checkSetting(FLAG_OTHER_USE_QUICK_TRANSLATION);
  }

  public boolean forceArabicNumbers () {
    return checkSetting(FLAG_OTHER_FORCE_ARABIC_NUMBERS);
  }

  public void setForceArabicNumbers (boolean force) {
    setSetting(FLAG_OTHER_FORCE_ARABIC_NUMBERS, force);
  }

  private Boolean needRestrictContent;

  public boolean needRestrictContent () {
    return needRestrictContent != null ? needRestrictContent : (needRestrictContent = pmc.getBoolean(KEY_RESTRICT_CONTENT, true));
  }

  public void setRestrictContent (boolean restrict) {
    needRestrictContent = restrict;
    if (restrict)
      pmc.remove(KEY_RESTRICT_CONTENT);
    else
      pmc.putBoolean(KEY_RESTRICT_CONTENT, false);
  }

  public boolean needReduceMotion () {
    return checkSetting(FLAG_OTHER_REDUCE_MOTION);
  }

  public void setReduceMotion (boolean reduceMotion) {
    setSetting(FLAG_OTHER_REDUCE_MOTION, reduceMotion);
  }

  public void toggleReduceMotion () {
    setReduceMotion(!needReduceMotion());
  }

  public boolean useSystemFonts () {
    return checkSetting(FLAG_OTHER_USE_SYSTEM_FONTS);
  }

  public void setUseSystemFonts (boolean useSystemFonts) {
    setSetting(FLAG_OTHER_USE_SYSTEM_FONTS, useSystemFonts);
  }

  public boolean useEdgeToEdge () {
    if (Config.EDGE_TO_EDGE_AVAILABLE) {
      if (Config.EDGE_TO_EDGE_CUSTOMIZABLE) {
        return !isExperimentEnabled(EXPERIMENT_FLAG_NO_EDGE_TO_EDGE);
      }
      return true;
    }
    return false;
  }

  public boolean useBigEmoji () {
    return checkNegativeSetting(FLAG_OTHER_DISABLE_BIG_EMOJI);
  }

  public void setUseBigEmoji (boolean useBigEmoji) {
    setNegativeSetting(FLAG_OTHER_DISABLE_BIG_EMOJI, useBigEmoji);
  }

  public int getInstantViewMode () {
    return getInt(KEY_INSTANT_VIEW, INSTANT_VIEW_MODE_INTERNAL);
  }

  public void setInstantViewMode (int mode) {
    if (mode == INSTANT_VIEW_MODE_INTERNAL) {
      remove(KEY_INSTANT_VIEW);
    } else {
      putInt(KEY_INSTANT_VIEW, mode);
    }
  }

  public int getStickerMode () {
    if (_stickerMode == null)
      _stickerMode = pmc.getInt(KEY_STICKER_MODE, STICKER_MODE_ALL);
    return _stickerMode;
  }

  public void setStickerMode (int mode) {
    this._stickerMode = mode;
    if (mode == STICKER_MODE_ALL) {
      remove(KEY_STICKER_MODE);
    } else {
      putInt(KEY_STICKER_MODE, mode);
    }
  }

  public int getEmojiMode () {
    if (_emojiMode == null)
      _emojiMode = pmc.getInt(KEY_EMOJI_MODE, STICKER_MODE_ALL);
    return _emojiMode;
  }

  public void setEmojiMode (int mode) {
    this._emojiMode = mode;
    if (mode == STICKER_MODE_ALL) {
      remove(KEY_EMOJI_MODE);
    } else {
      putInt(KEY_EMOJI_MODE, mode);
    }
  }

  public int getReactionAvatarsMode () {
    if (_reactionAvatarsMode == null)
      _reactionAvatarsMode = pmc.getInt(KEY_REACTION_AVATARS_MODE, REACTION_AVATARS_MODE_SMART_FILTER);
    return _reactionAvatarsMode;
  }

  public void setReactionAvatarsMode (int mode) {
    this._reactionAvatarsMode = mode;
    putInt(KEY_REACTION_AVATARS_MODE, mode);
  }

  public int getAutoUpdateMode () {
    if (_autoUpdateMode == null)
      _autoUpdateMode = pmc.getInt(KEY_AUTO_UPDATE_MODE, AUTO_UPDATE_MODE_PROMPT);
    return _autoUpdateMode;
  }

  public void setAutoUpdateMode (int mode) {
    this._autoUpdateMode = mode;
    if (mode == AUTO_UPDATE_MODE_PROMPT) {
      remove(KEY_AUTO_UPDATE_MODE);
    } else {
      putInt(KEY_AUTO_UPDATE_MODE, mode);
    }
  }

  public int getBadgeFlags () {
    if (_badgeFlags == null)
      _badgeFlags = pmc.getInt(KEY_BADGE_FLAGS, 0);
    return _badgeFlags;
  }

  public boolean setBadgeFlags (int badgeFlags) {
    if (getBadgeFlags() != badgeFlags) {
      this._badgeFlags = badgeFlags;
      if (badgeFlags == 0) {
        remove(KEY_BADGE_FLAGS);
      } else {
        putInt(KEY_BADGE_FLAGS, badgeFlags);
      }
      return true;
    }
    return false;
  }

  public boolean resetBadge () {
    boolean updated;
    updated = setBadgeFlags(0);
    return updated;
  }

  private int getNotificationFlags () {
    if (_notificationFlags == null) {
      int flags = pmc.getInt(KEY_NOTIFICATION_FLAGS, NOTIFICATION_FLAGS_DEFAULT);
      if (BitwiseUtils.hasFlag(flags, NOTIFICATION_FLAG_ONLY_ACTIVE_ACCOUNT) && BitwiseUtils.hasFlag(flags, NOTIFICATION_FLAG_ONLY_SELECTED_ACCOUNTS)) {
        flags = BitwiseUtils.setFlag(flags, NOTIFICATION_FLAG_ONLY_ACTIVE_ACCOUNT, false);
        flags = BitwiseUtils.setFlag(flags, NOTIFICATION_FLAG_ONLY_SELECTED_ACCOUNTS, false);
      }
      _notificationFlags = flags;
    }
    return _notificationFlags;
  }

  public boolean checkNotificationFlag (int flag) {
    return BitwiseUtils.hasFlag(getNotificationFlags(), flag);
  }

  public boolean setNotificationFlag (int flag, boolean enabled) {
    int flags = getNotificationFlags();
    if (enabled) {
      if (flag == NOTIFICATION_FLAG_ONLY_ACTIVE_ACCOUNT) {
        flags = BitwiseUtils.setFlag(flags, NOTIFICATION_FLAG_ONLY_SELECTED_ACCOUNTS, false);
      } else if (flag == NOTIFICATION_FLAG_ONLY_SELECTED_ACCOUNTS) {
        flags = BitwiseUtils.setFlag(flags, NOTIFICATION_FLAG_ONLY_ACTIVE_ACCOUNT, false);
      }
    }
    return setNotificationFlags(BitwiseUtils.setFlag(flags, flag, enabled));
  }

  public boolean resetNotificationFlags () {
    return setNotificationFlags(NOTIFICATION_FLAGS_DEFAULT);
  }

  private boolean setNotificationFlags (int flags) {
    if (getNotificationFlags() != flags) {
      this._notificationFlags = flags;
      if (flags == NOTIFICATION_FLAGS_DEFAULT) {
        remove(KEY_NOTIFICATION_FLAGS);
      } else {
        putInt(KEY_NOTIFICATION_FLAGS, flags);
      }
      return true;
    }
    return false;
  }

  public boolean needsIncognitoMode () {
    return getIncognitoMode() == INCOGNITO_CHAT_SECRET; // TODO more chat options ?
  }

  public boolean needsIncognitoMode (TdApi.Chat chat) {
    return (getIncognitoMode() == INCOGNITO_CHAT_SECRET && chat != null && ChatId.isSecret(chat.id));
  }

  private int getIncognitoMode () {
    if (_incognitoMode == null)
      _incognitoMode = pmc.getInt(KEY_INCOGNITO, INCOGNITO_CHAT_SECRET);
    return _incognitoMode;
  }

  public void setIncognitoMode (int incognitoMode) {
    this._incognitoMode = incognitoMode;
    if (incognitoMode == INCOGNITO_CHAT_SECRET) {
      remove(KEY_INCOGNITO);
    } else {
      putInt(KEY_INCOGNITO, incognitoMode);
    }
  }

  public interface VideoModePreferenceListener {
    void onPreferVideoModeChanged (boolean preferVideoMode);

    default void onRecordAudioVideoError (boolean preferVideoMode) {}
  }

  private final List<Reference<VideoModePreferenceListener>> videoPreferenceChangeListeners = new ArrayList<>();

  private void notifyVideoPreferenceListeners (boolean preferVideoMode) {
    final int size = videoPreferenceChangeListeners.size();
    for (int i = size - 1; i >= 0; i--) {
      VideoModePreferenceListener listener = videoPreferenceChangeListeners.get(i).get();
      if (listener != null) {
        listener.onPreferVideoModeChanged(preferVideoMode);
      } else {
        videoPreferenceChangeListeners.remove(i);
      }
    }
  }

  public void notifyRecordAudioVideoError () {
    boolean preferVideoMode = preferVideoMode();
    final int size = videoPreferenceChangeListeners.size();
    for (int i = size - 1; i >= 0; i--) {
      VideoModePreferenceListener listener = videoPreferenceChangeListeners.get(i).get();
      if (listener != null) {
        listener.onRecordAudioVideoError(preferVideoMode);
      } else {
        videoPreferenceChangeListeners.remove(i);
      }
    }
  }

  public void addVideoPreferenceChangeListener (VideoModePreferenceListener listener) {
    ReferenceUtils.addReference(videoPreferenceChangeListeners, listener);
  }

  public void removeVideoPreferenceChangeListener (VideoModePreferenceListener listener) {
    ReferenceUtils.removeReference(videoPreferenceChangeListeners, listener);
  }

  public boolean preferVideoMode () {
    return Config.ROUND_VIDEOS_RECORD_SUPPORTED && checkSetting(FLAG_OTHER_PREFER_VIDEO_MODE);
  }

  public void setPreferVideoMode (boolean preferVideoMode) {
    preferVideoMode = Config.ROUND_VIDEOS_RECORD_SUPPORTED && preferVideoMode;
    if (setSetting(FLAG_OTHER_PREFER_VIDEO_MODE, preferVideoMode)) {
      notifyVideoPreferenceListeners(preferVideoMode);
    }
  }

  public boolean needHqRoundVideos () {
    return getRoundVideoQuality() >= ROUND_VIDEO_QUALITY_HQ;
  }

  // Video message (round video) quality. Video notes must stay square and at most
  // 640px on a side, otherwise they can't be sent as video messages.
  public static final int ROUND_VIDEO_QUALITY_SD = 0;
  public static final int ROUND_VIDEO_QUALITY_HQ = 1;
  public static final int ROUND_VIDEO_QUALITY_HIGH = 2;
  public static final int ROUND_VIDEO_QUALITY_MAX = 3;
  private static final String KEY_ROUND_VIDEO_QUALITY = "settings_round_video_quality";

  public int getRoundVideoQuality () {
    int quality = pmc.getInt(KEY_ROUND_VIDEO_QUALITY, checkSetting(FLAG_OTHER_HQ_ROUND_VIDEOS) ? ROUND_VIDEO_QUALITY_HQ : ROUND_VIDEO_QUALITY_HIGH);
    quality = Math.max(ROUND_VIDEO_QUALITY_SD, Math.min(ROUND_VIDEO_QUALITY_MAX, quality));
    if (Device.NEED_HQ_ROUND_VIDEOS) {
      quality = Math.max(quality, ROUND_VIDEO_QUALITY_HQ);
    }
    return quality;
  }

  public void setRoundVideoQuality (int quality) {
    pmc.putInt(KEY_ROUND_VIDEO_QUALITY, quality);
  }

  // TGx101: size of big emoji (emoji-only messages), in percent of the stock size.
  // TGx101: compact message menu (on by default), the hand it sits under, and the order of actions
  private static final String KEY_TGX101_MESSAGE_MENU = "tgx101_message_menu";
  private static final String KEY_TGX101_MESSAGE_MENU_LEFT = "tgx101_message_menu_left";
  private static final String KEY_TGX101_MESSAGE_MENU_ORDER = "tgx101_message_menu_order";
  private static final String KEY_TGX101_FORMAT_MENU = "tgx101_format_menu";

  /** TGx101: formatting items of the text selection menu in the user's order; hidden ones are negative ids */
  public @androidx.annotation.Nullable int[] getTgx101FormatMenu () {
    String saved = pmc.getString(KEY_TGX101_FORMAT_MENU, null);
    if (saved == null || saved.isEmpty()) return null;
    android.content.res.Resources res = org.thunderdog.challegram.tool.UI.getAppContext().getResources();
    java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
    for (String name : saved.split(",")) {
      boolean hidden = name.startsWith("-");
      int id = res.getIdentifier(hidden ? name.substring(1) : name, "id", org.thunderdog.challegram.tool.UI.getAppContext().getPackageName());
      if (id != 0) ids.add(hidden ? -id : id);
    }
    int[] result = new int[ids.size()];
    for (int i = 0; i < result.length; i++) result[i] = ids.get(i);
    return result;
  }

  public void setTgx101FormatMenu (@androidx.annotation.Nullable int[] ids) {
    if (ids == null) {
      pmc.remove(KEY_TGX101_FORMAT_MENU);
      return;
    }
    android.content.res.Resources res = org.thunderdog.challegram.tool.UI.getAppContext().getResources();
    StringBuilder b = new StringBuilder();
    for (int id : ids) {
      if (b.length() > 0) b.append(',');
      if (id < 0) b.append('-');
      b.append(res.getResourceEntryName(Math.abs(id)));
    }
    pmc.putString(KEY_TGX101_FORMAT_MENU, b.toString());
  }

  public boolean useTgx101MessageMenu () {
    return pmc.getBoolean(KEY_TGX101_MESSAGE_MENU, true);
  }

  public void setUseTgx101MessageMenu (boolean use) {
    pmc.putBoolean(KEY_TGX101_MESSAGE_MENU, use);
  }

  public boolean isTgx101MessageMenuLeftHand () {
    return pmc.getBoolean(KEY_TGX101_MESSAGE_MENU_LEFT, false);
  }

  public void setTgx101MessageMenuLeftHand (boolean left) {
    pmc.putBoolean(KEY_TGX101_MESSAGE_MENU_LEFT, left);
  }

  private static final String KEY_TGX101_MESSAGE_MENU_HIDDEN = "tgx101_message_menu_hidden";
  private static final String KEY_TGX101_MESSAGE_MENU_MORE = "tgx101_message_menu_more";

  /** TGx101: the text selection bar's top row (Tgx101BarOrder): "key,-key,…" per bar */
  public @androidx.annotation.Nullable String getTgx101BarOrder (int bar) {
    return pmc.getString("tgx101_bar_order_" + bar, null);
  }

  public void setTgx101BarOrder (int bar, @androidx.annotation.Nullable String order) {
    if (order == null || order.isEmpty()) {
      pmc.remove("tgx101_bar_order_" + bar);
    } else {
      pmc.putString("tgx101_bar_order_" + bar, order);
    }
  }

  /** TGx101: message menu actions moved under «More…» (resource names stored, like the order) */
  private static final String KEY_TGX101_MESSAGE_MENU_MORE_REPLIES = "tgx101_message_menu_more_replies";

  /** By default «View in topic / thread» sits under «More…» (user 2026-10-03); "none" = the user emptied «More…» */
  public int[] getTgx101MessageMenuMore () {
    String saved = pmc.getString(KEY_TGX101_MESSAGE_MENU_MORE, null);
    if (saved == null) {
      return new int[] {org.thunderdog.challegram.R.id.btn_messageReplies};
    }
    if (!pmc.getBoolean(KEY_TGX101_MESSAGE_MENU_MORE_REPLIES, false)) {
      // a «More…» list saved before the item existed: the item joins it once
      pmc.putBoolean(KEY_TGX101_MESSAGE_MENU_MORE_REPLIES, true);
      if (!saved.contains("btn_messageReplies") && !String.valueOf(pmc.getString(KEY_TGX101_MESSAGE_MENU_HIDDEN, "")).contains("btn_messageReplies")) {
        saved = saved.isEmpty() || saved.equals("none") ? "btn_messageReplies" : saved + ",btn_messageReplies";
        pmc.putString(KEY_TGX101_MESSAGE_MENU_MORE, saved);
      }
    }
    if (saved.isEmpty() || saved.equals("none")) {
      return new int[0];
    }
    android.content.res.Resources res = org.thunderdog.challegram.tool.UI.getAppContext().getResources();
    java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
    for (String name : saved.split(",")) {
      int id = res.getIdentifier(name, "id", org.thunderdog.challegram.tool.UI.getAppContext().getPackageName());
      if (id != 0) ids.add(id);
    }
    int[] result = new int[ids.size()];
    for (int i = 0; i < result.length; i++) result[i] = ids.get(i);
    return result;
  }

  public void setTgx101MessageMenuMore (int[] ids) {
    pmc.putBoolean(KEY_TGX101_MESSAGE_MENU_MORE_REPLIES, true);
    if (ids == null) {
      pmc.remove(KEY_TGX101_MESSAGE_MENU_MORE); // reset: the default list
      return;
    }
    if (ids.length == 0) {
      pmc.putString(KEY_TGX101_MESSAGE_MENU_MORE, "none");
      return;
    }
    android.content.res.Resources res = org.thunderdog.challegram.tool.UI.getAppContext().getResources();
    StringBuilder b = new StringBuilder();
    for (int id : ids) {
      if (b.length() > 0) b.append(',');
      b.append(res.getResourceEntryName(id));
    }
    pmc.putString(KEY_TGX101_MESSAGE_MENU_MORE, b.toString());
  }

  /** TGx101: message menu actions the user hid (resource names stored, like the order) */
  public int[] getTgx101MessageMenuHidden () {
    String saved = pmc.getString(KEY_TGX101_MESSAGE_MENU_HIDDEN, null);
    if (saved == null || saved.isEmpty()) {
      return new int[0];
    }
    android.content.res.Resources res = org.thunderdog.challegram.tool.UI.getAppContext().getResources();
    java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
    for (String name : saved.split(",")) {
      int id = res.getIdentifier(name, "id", org.thunderdog.challegram.tool.UI.getAppContext().getPackageName());
      if (id != 0) ids.add(id);
    }
    int[] result = new int[ids.size()];
    for (int i = 0; i < result.length; i++) result[i] = ids.get(i);
    return result;
  }

  public void setTgx101MessageMenuHidden (int[] ids) {
    if (ids == null || ids.length == 0) {
      pmc.remove(KEY_TGX101_MESSAGE_MENU_HIDDEN);
      return;
    }
    android.content.res.Resources res = org.thunderdog.challegram.tool.UI.getAppContext().getResources();
    StringBuilder b = new StringBuilder();
    for (int id : ids) {
      if (b.length() > 0) b.append(',');
      b.append(res.getResourceEntryName(id));
    }
    pmc.putString(KEY_TGX101_MESSAGE_MENU_HIDDEN, b.toString());
  }

  /** Resource ids in the saved order; names are stored so the order survives app updates */
  @androidx.annotation.Nullable
  public int[] getTgx101MessageMenuOrder () {
    String saved = pmc.getString(KEY_TGX101_MESSAGE_MENU_ORDER, null);
    if (saved == null || saved.isEmpty()) {
      return null;
    }
    android.content.res.Resources res = org.thunderdog.challegram.tool.UI.getAppContext().getResources();
    String[] names = saved.split(",");
    java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
    for (String name : names) {
      int id = res.getIdentifier(name, "id", org.thunderdog.challegram.tool.UI.getAppContext().getPackageName());
      if (id != 0) {
        ids.add(id);
      }
    }
    int[] result = new int[ids.size()];
    for (int i = 0; i < result.length; i++) result[i] = ids.get(i);
    return result;
  }

  public void setTgx101MessageMenuOrder (@androidx.annotation.Nullable int[] ids) {
    if (ids == null) {
      pmc.remove(KEY_TGX101_MESSAGE_MENU_ORDER);
      return;
    }
    android.content.res.Resources res = org.thunderdog.challegram.tool.UI.getAppContext().getResources();
    StringBuilder b = new StringBuilder();
    for (int id : ids) {
      if (b.length() > 0) b.append(',');
      b.append(res.getResourceEntryName(id));
    }
    pmc.putString(KEY_TGX101_MESSAGE_MENU_ORDER, b.toString());
  }

  // TGx101: chat list text size (percent of the stock size) and text weight
  private static final String KEY_TGX101_CHAT_LIST_TEXT_SIZE = "tgx101_chat_list_text_size";
  private static final String KEY_TGX101_TEXT_WEIGHT = "tgx101_text_weight";
  private static final String KEY_TGX101_FONT_ROBOTO = "tgx101_font_roboto";
  private static final String KEY_TGX101_TEXT_EDITOR = "tgx101_text_editor";
  private static final String KEY_TGX101_CALL_BAR = "tgx101_call_bar";
  private static final String KEY_TGX101_RING_RAMP_SECONDS = "tgx101_ring_ramp_seconds";
  public static final int[] TGX101_RING_RAMP_OPTIONS = {5, 10, 15, 20, 30};

  /** TGx101: seconds the incoming call ringtone takes to grow from quiet to full volume */
  public int getTgx101RingRampSeconds () {
    int value = pmc.getInt(KEY_TGX101_RING_RAMP_SECONDS, 30);
    for (int option : TGX101_RING_RAMP_OPTIONS) if (option == value) return value;
    return 30;
  }

  public void setTgx101RingRampSeconds (int seconds) {
    pmc.putInt(KEY_TGX101_RING_RAMP_SECONDS, seconds);
  }

  /** TGx101: the «ongoing call» bar over the chats (the status bar chip also leads back to the call) */
  public boolean showTgx101CallBar () {
    return pmc.getBoolean(KEY_TGX101_CALL_BAR, true);
  }

  public void setShowTgx101CallBar (boolean show) {
    pmc.putBoolean(KEY_TGX101_CALL_BAR, show);
  }

  /** TGx101: «Изменить» opens the message text window (formatting buttons) instead of editing in the input field */
  public boolean useTgx101TextEditor () {
    return pmc.getBoolean(KEY_TGX101_TEXT_EDITOR, true);
  }

  public void setUseTgx101TextEditor (boolean use) {
    pmc.putBoolean(KEY_TGX101_TEXT_EDITOR, use);
  }
  private static final String KEY_TGX101_APP_FONT = "tgx101_app_font";
  public static final int APP_FONT_MANROPE = 0, APP_FONT_ROBOTO = 1, APP_FONT_SYSTEM = 2;
  private int _appFont = -1;

  /** TGx101: Manrope, the stock Roboto or the phone's system font (only the typeface; size and weight settings stay). Applies after a restart. */
  public int getAppFont () {
    if (_appFont < 0) {
      int font = pmc.getInt(KEY_TGX101_APP_FONT, pmc.getBoolean(KEY_TGX101_FONT_ROBOTO, false) ? APP_FONT_ROBOTO : APP_FONT_MANROPE);
      _appFont = font >= APP_FONT_MANROPE && font <= APP_FONT_SYSTEM ? font : APP_FONT_MANROPE;
    }
    return _appFont;
  }

  public void setAppFont (int font) {
    pmc.putInt(KEY_TGX101_APP_FONT, font);
    _appFont = font;
  }

  public boolean useRobotoFont () {
    return getAppFont() == APP_FONT_ROBOTO;
  }

  public boolean useSystemFont () {
    return getAppFont() == APP_FONT_SYSTEM;
  }
  public static final int TEXT_WEIGHT_NORMAL = 0, TEXT_WEIGHT_SLIGHTLY_BOLDER = 1, TEXT_WEIGHT_SEMIBOLD = 2;
  private float _chatListTextScale = -1f;
  private int _textWeight = -1;

  public int getChatListTextSize () {
    int size = pmc.getInt(KEY_TGX101_CHAT_LIST_TEXT_SIZE, 100);
    return size >= 100 && size <= 130 ? size : 100;
  }

  public float getChatListTextScale () {
    if (_chatListTextScale < 0f) {
      _chatListTextScale = getChatListTextSize() / 100f;
    }
    return _chatListTextScale;
  }

  public void setChatListTextSize (int size) {
    if (size != getChatListTextSize()) {
      pmc.putInt(KEY_TGX101_CHAT_LIST_TEXT_SIZE, size);
      _chatListTextScale = size / 100f;
      if (chatListModeListeners != null) {
        int mode = getChatListMode();
        for (ChatListModeChangeListener listener : chatListModeListeners) {
          listener.onChatListModeChanged(mode);
        }
      }
    }
  }

  public int getTextWeight () {
    if (_textWeight < 0) {
      int weight = pmc.getInt(KEY_TGX101_TEXT_WEIGHT, TEXT_WEIGHT_NORMAL);
      _textWeight = weight >= TEXT_WEIGHT_NORMAL && weight <= TEXT_WEIGHT_SEMIBOLD ? weight : TEXT_WEIGHT_NORMAL;
    }
    return _textWeight;
  }

  /** Applies after the app restarts: fonts are loaded once. */
  public void setTextWeight (int weight) {
    pmc.putInt(KEY_TGX101_TEXT_WEIGHT, weight);
    _textWeight = weight;
  }

  // 0 means big emoji are off; it's kept in sync with the stock "Big Emoji" toggle.
  public static final int BIG_EMOJI_SIZE_OFF = 0;
  public static final int BIG_EMOJI_SIZE_DEFAULT = 100;
  private static final String KEY_BIG_EMOJI_SIZE = "settings_big_emoji_size";

  public int getBigEmojiSize () {
    if (!useBigEmoji()) {
      return BIG_EMOJI_SIZE_OFF;
    }
    int size = pmc.getInt(KEY_BIG_EMOJI_SIZE, BIG_EMOJI_SIZE_DEFAULT);
    return size > 0 ? size : BIG_EMOJI_SIZE_DEFAULT;
  }

  public float getBigEmojiScale () {
    int size = getBigEmojiSize();
    return size > 0 ? size / 100f : 1f;
  }

  public void setBigEmojiSize (int size) {
    if (size > 0) {
      pmc.putInt(KEY_BIG_EMOJI_SIZE, size);
    }
    setUseBigEmoji(size > 0);
  }

  // Pull down at the top of the chat list to open search. A separate key (not a
  // new-settings flag) so it's on by default for existing installs too.
  private static final String KEY_PULL_TO_SEARCH = "settings_pull_to_search";

  public boolean isPullToSearchEnabled () {
    return pmc.getBoolean(KEY_PULL_TO_SEARCH, true);
  }

  public void setPullToSearchEnabled (boolean enabled) {
    pmc.putBoolean(KEY_PULL_TO_SEARCH, enabled);
  }

  // TGx101: status bar icon of message notifications — PlumaGram bird (default) or the Telegram plane
  private static final String KEY_TGX101_NOTIFICATION_PLANE = "tgx101_notification_plane";

  public boolean useTgx101NotificationPlane () {
    return pmc.getBoolean(KEY_TGX101_NOTIFICATION_PLANE, false);
  }

  public void setTgx101NotificationPlane (boolean plane) {
    pmc.putBoolean(KEY_TGX101_NOTIFICATION_PLANE, plane);
  }

  public int getNotificationIconRes () {
    return useTgx101NotificationPlane() ? R.mipmap.app_notification_plane : R.mipmap.app_notification;
  }

  // Channels: the bottom "Discuss" button and the comments button under posts can be hidden.
  // Separate keys (not new-settings flags) so both stay on for existing installs.
  private static final String KEY_SHOW_DISCUSS_BUTTON = "settings_show_discuss_button";
  private static final String KEY_SHOW_COMMENTS_BUTTON = "settings_show_comments_button";

  public boolean showDiscussButton () {
    return pmc.getBoolean(KEY_SHOW_DISCUSS_BUTTON, true);
  }

  public void setShowDiscussButton (boolean show) {
    pmc.putBoolean(KEY_SHOW_DISCUSS_BUTTON, show);
  }

  // TGx101: call screen pattern. Stored as a number so more backgrounds, colours and patterns can be added later
  public static final int CALL_PATTERN_NONE = 0;
  public static final int CALL_PATTERN_PAPER_PLANES = 1;
  private static final String KEY_CALL_PATTERN = "settings_call_pattern";

  // TGx101: the message field floats over the chat as a rounded capsule with a shadow (approved input variant 5)
  private static final String KEY_TGX101_FLOATING_INPUT = "tgx101_floating_input";

  public boolean useFloatingInput () {
    return pmc.getBoolean(KEY_TGX101_FLOATING_INPUT, false); // user 2026-09-30: classic (variant 1) by default, the capsule stays an option
  }

  public void setUseFloatingInput (boolean use) {
    pmc.putBoolean(KEY_TGX101_FLOATING_INPUT, use);
  }

  // TGx101: the camera button leaves the message input for the attach (paperclip) menu, in place of «Inline bot»
  private static final String KEY_TGX101_NEXT_CHANNEL_SWIPE = "tgx101_next_channel_swipe";

  /** TGx101: a pull up at the end of a channel opens the next unread channel (on by default, as in official apps) */
  public boolean tgx101NextChannelSwipe () {
    return pmc.getBoolean(KEY_TGX101_NEXT_CHANNEL_SWIPE, true);
  }

  public void setTgx101NextChannelSwipe (boolean value) {
    pmc.putBoolean(KEY_TGX101_NEXT_CHANNEL_SWIPE, value);
  }

  private static final String KEY_TGX101_HIDE_CHANNEL_REACTIONS = "tgx101_hide_channel_reactions";

  /** TGx101: no reaction row under channel posts (off by default) */
  public boolean tgx101HideChannelReactions () {
    return pmc.getBoolean(KEY_TGX101_HIDE_CHANNEL_REACTIONS, false);
  }

  public void setTgx101HideChannelReactions (boolean value) {
    pmc.putBoolean(KEY_TGX101_HIDE_CHANNEL_REACTIONS, value);
  }

  private static final String KEY_TGX101_UI_TEXT = "tgx101_ui_text";
  private static final String KEY_TGX101_UI_BOLD = "tgx101_ui_bold";

  /** TGx101 (user 2026-10-05, low vision): «Large interface» — text size of menus, lists, popups, 100–160 % */
  public int tgx101UiTextSize () {
    int v = pmc.getInt(KEY_TGX101_UI_TEXT, 100);
    return v >= 100 && v <= 160 ? v : 100;
  }

  public void setTgx101UiTextSize (int v) {
    pmc.putInt(KEY_TGX101_UI_TEXT, v);
  }

  public boolean tgx101UiBold () {
    return pmc.getBoolean(KEY_TGX101_UI_BOLD, false);
  }

  public void setTgx101UiBold (boolean v) {
    pmc.putBoolean(KEY_TGX101_UI_BOLD, v);
  }

  /** Our size × the system font size slider (the app's dp text ignored it), capped so nothing explodes */
  public static float tgx101UiScale () {
    float system = 1f;
    try { system = org.thunderdog.challegram.tool.UI.getAppContext().getResources().getConfiguration().fontScale; } catch (Throwable ignored) { }
    return Math.min(2f, instance().tgx101UiTextSize() / 100f * Math.max(1f, system));
  }

  /** Text of a menu / list row: scaled size, bolder if chosen, one line that scrolls (marquee) when it doesn't fit */
  public static void tgx101StyleRow (android.widget.TextView view, float baseDp) {
    float scale = tgx101UiScale();
    view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, baseDp * scale);
    if (instance().tgx101UiBold()) {
      view.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoMedium());
    }
    if (scale > 1f) {
      view.setSingleLine(true);
      view.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
      view.setMarqueeRepeatLimit(1);
      view.setHorizontalFadingEdgeEnabled(true);
      view.postDelayed(() -> view.setSelected(true), 1000); // starts after a second, as in the mockup
    }
  }

  private static final String KEY_TGX101_SNOOZE_MINUTES = "tgx101_snooze_minutes";

  /** TGx101 (user 2026-10-05): «Snooze» in a message notification mutes that chat for this many minutes; 0 = no button */
  public int tgx101SnoozeMinutes () {
    return pmc.getInt(KEY_TGX101_SNOOZE_MINUTES, 30);
  }

  public void setTgx101SnoozeMinutes (int minutes) {
    pmc.putInt(KEY_TGX101_SNOOZE_MINUTES, minutes);
  }

  private static final String KEY_TGX101_ZOOM_PULL_CLOSE = "tgx101_zoom_pull_close";

  /** TGx101: a zoomed photo can be pulled down from its top edge to close (on by default) */
  public boolean tgx101ZoomPullClose () {
    return pmc.getBoolean(KEY_TGX101_ZOOM_PULL_CLOSE, true);
  }

  public void setTgx101ZoomPullClose (boolean value) {
    pmc.putBoolean(KEY_TGX101_ZOOM_PULL_CLOSE, value);
  }

  private static final String KEY_TGX101_CAMERA_IN_ATTACH = "tgx101_camera_in_attach";

  public boolean isCameraInAttach () {
    return pmc.getBoolean(KEY_TGX101_CAMERA_IN_ATTACH, true); // variant 1: emoji · field · paperclip · mic
  }

  public void setCameraInAttach (boolean inAttach) {
    pmc.putBoolean(KEY_TGX101_CAMERA_IN_ATTACH, inAttach);
  }

  // TGx101: after a voice or video message ends, the next one plays automatically; optionally skipping my own
  private static final String KEY_TGX101_VOICE_SKIP_OWN = "tgx101_voice_skip_own";

  public boolean skipOwnVoiceInQueue () {
    return pmc.getBoolean(KEY_TGX101_VOICE_SKIP_OWN, false);
  }

  public void setSkipOwnVoiceInQueue (boolean skip) {
    pmc.putBoolean(KEY_TGX101_VOICE_SKIP_OWN, skip);
  }

  // TGx101: message taps — stock / double tap sets reaction №1 (swipe only replies) / double tap and swipe reactions
  public static final int TAP_MODE_STOCK = 0, TAP_MODE_DOUBLE = 1, TAP_MODE_DOUBLE_SWIPE = 2;
  private static final String KEY_TGX101_TAP_MODE = "tgx101_tap_mode";

  public int getTapMode () {
    return pmc.getInt(KEY_TGX101_TAP_MODE, TAP_MODE_STOCK);
  }

  public void setTapMode (int mode) {
    pmc.putInt(KEY_TGX101_TAP_MODE, mode);
  }

  // TGx101: new call screen (card with 2×3 labelled buttons, TruePhone-style incoming call); off = the previous one
  private static final String KEY_TGX101_NEW_CALL_SCREEN = "tgx101_new_call_screen";

  public boolean useNewCallScreen () {
    return pmc.getBoolean(KEY_TGX101_NEW_CALL_SCREEN, true);
  }

  public void setUseNewCallScreen (boolean use) {
    pmc.putBoolean(KEY_TGX101_NEW_CALL_SCREEN, use);
  }

  // TGx101: five quick reply templates for incoming calls; the first three are offered on the call screen
  public static final int QUICK_REPLY_COUNT = 5, QUICK_REPLY_SHOWN = 3;
  private static final String KEY_TGX101_QUICK_REPLY = "tgx101_quick_reply_";

  public String getQuickReply (int index) {
    String saved = pmc.getString(KEY_TGX101_QUICK_REPLY + index, null);
    if (saved != null && !saved.trim().isEmpty()) {
      return saved;
    }
    int[] defaults = {R.string.Tgx101QuickReply1, R.string.Tgx101QuickReply2, R.string.Tgx101QuickReply3, R.string.Tgx101QuickReply4, R.string.Tgx101QuickReply5};
    return org.thunderdog.challegram.core.Lang.getString(defaults[index]);
  }

  public void setQuickReply (int index, String text) {
    if (text == null || text.trim().isEmpty()) {
      pmc.remove(KEY_TGX101_QUICK_REPLY + index);
    } else {
      pmc.putString(KEY_TGX101_QUICK_REPLY + index, text.trim());
    }
  }

  // TGx101: the incoming call ringtone starts at 20 % and rises to 100 % over 20 seconds
  private static final String KEY_TGX101_RING_RAMP = "tgx101_ring_ramp";

  public boolean isRingRampEnabled () {
    return pmc.getBoolean(KEY_TGX101_RING_RAMP, false);
  }

  public void setRingRampEnabled (boolean enabled) {
    pmc.putBoolean(KEY_TGX101_RING_RAMP, enabled);
  }

  public int getCallPattern () {
    return pmc.getInt(KEY_CALL_PATTERN, CALL_PATTERN_PAPER_PLANES);
  }

  public void setCallPattern (int pattern) {
    pmc.putInt(KEY_CALL_PATTERN, pattern);
  }

  // TGx101: camera quality in video calls
  public static final int CALL_VIDEO_SD = 0; // 960x540
  public static final int CALL_VIDEO_HD = 1; // 1280x720
  private static final String KEY_CALL_VIDEO_QUALITY = "settings_call_video_quality";

  public int getCallVideoQuality () {
    return pmc.getInt(KEY_CALL_VIDEO_QUALITY, CALL_VIDEO_HD) == CALL_VIDEO_SD ? CALL_VIDEO_SD : CALL_VIDEO_HD; // HD by default, as the official app
  }

  public void setCallVideoQuality (int quality) {
    pmc.putInt(KEY_CALL_VIDEO_QUALITY, quality);
  }

  // TGx101: photo on the call screen: a big circle (new), the original full-screen photo, or none
  public static final int CALL_PHOTO_CIRCLE = 0;
  public static final int CALL_PHOTO_FULL_SCREEN = 1;
  public static final int CALL_PHOTO_NONE = 2;
  private static final String KEY_CALL_PHOTO_MODE = "settings_call_photo_mode";

  public int getCallPhotoMode () {
    int mode = pmc.getInt(KEY_CALL_PHOTO_MODE, CALL_PHOTO_CIRCLE);
    return mode >= CALL_PHOTO_CIRCLE && mode <= CALL_PHOTO_NONE ? mode : CALL_PHOTO_CIRCLE;
  }

  public void setCallPhotoMode (int mode) {
    pmc.putInt(KEY_CALL_PHOTO_MODE, mode);
  }

  // TGx101: the bottom "Mute / Unmute" button in channels (it collapses into a bell while scrolling)
  private static final String KEY_TGX101_TRANSLATE_ON_DEVICE = "tgx101_translate_on_device";

  /** TGx101: translate with Google ML Kit on the phone instead of Telegram's server translation — off by default */
  public boolean tgx101TranslateOnDevice () {
    return pmc.getBoolean(KEY_TGX101_TRANSLATE_ON_DEVICE, false);
  }

  public void setTgx101TranslateOnDevice (boolean value) {
    pmc.putBoolean(KEY_TGX101_TRANSLATE_ON_DEVICE, value);
  }

  private static final String KEY_TGX101_SPEECH_MODEL = "tgx101_speech_model";

  /** TGx101: id of the own speech recognition model (Tgx101SpeechModels), null = none */
  public @Nullable String tgx101SpeechModel () {
    return pmc.getString(KEY_TGX101_SPEECH_MODEL, null);
  }

  public void setTgx101SpeechModel (@Nullable String id) {
    if (id == null) {
      pmc.remove(KEY_TGX101_SPEECH_MODEL);
    } else {
      pmc.putString(KEY_TGX101_SPEECH_MODEL, id);
    }
  }

  private static final String KEY_TGX101_FAKE_NO_PREMIUM = "tgx101_fake_no_premium";

  /** TGx101 diagnostics builds only: the app acts as if the account had no Premium (UI tests; the server still knows) */
  public boolean tgx101FakeNoPremium () {
    return org.thunderdog.challegram.BuildConfig.TGX101_DIAG && pmc.getBoolean(KEY_TGX101_FAKE_NO_PREMIUM, false);
  }

  public void setTgx101FakeNoPremium (boolean value) {
    pmc.putBoolean(KEY_TGX101_FAKE_NO_PREMIUM, value);
  }

  private static final String KEY_TGX101_PLAYER_SWIPE_CLOSE = "tgx101_player_swipe_close";
  private static final String KEY_TGX101_PLAYER_SWIPE_PAGING = "tgx101_player_swipe_paging";

  /** TGx101 player: a swipe down closes a video */
  public boolean tgx101PlayerSwipeClose () {
    return pmc.getBoolean(KEY_TGX101_PLAYER_SWIPE_CLOSE, true);
  }

  public void setTgx101PlayerSwipeClose (boolean value) {
    pmc.putBoolean(KEY_TGX101_PLAYER_SWIPE_CLOSE, value);
  }

  /** TGx101 player: a sideways swipe on a video goes to the next / previous media */
  public boolean tgx101PlayerSwipePaging () {
    return pmc.getBoolean(KEY_TGX101_PLAYER_SWIPE_PAGING, true);
  }

  public void setTgx101PlayerSwipePaging (boolean value) {
    pmc.putBoolean(KEY_TGX101_PLAYER_SWIPE_PAGING, value);
  }

  private static final String KEY_TGX101_PLAYER_PAUSE_MUSIC = "tgx101_player_pause_music";

  /** TGx101 player: music (the app's and other players') pauses while a video plays — on by default */
  public boolean tgx101PlayerPauseMusic () {
    return pmc.getBoolean(KEY_TGX101_PLAYER_PAUSE_MUSIC, true);
  }

  public void setTgx101PlayerPauseMusic (boolean value) {
    pmc.putBoolean(KEY_TGX101_PLAYER_PAUSE_MUSIC, value);
  }

  private static final String KEY_TGX101_PLAYER_ORIENTATION = "tgx101_player_orientation";
  public static final int PLAYER_ORIENTATION_SYSTEM = 0, PLAYER_ORIENTATION_AUTO = 1, PLAYER_ORIENTATION_PORTRAIT = 2;

  /** TGx101 player: screen orientation in the viewer — as the system, always turning with the phone, or portrait only */
  public int tgx101PlayerOrientation () {
    return pmc.getInt(KEY_TGX101_PLAYER_ORIENTATION, PLAYER_ORIENTATION_SYSTEM);
  }

  public void setTgx101PlayerOrientation (int mode) {
    pmc.putInt(KEY_TGX101_PLAYER_ORIENTATION, mode);
  }

  private static final String KEY_TGX101_PLAYER_SEEK_STEP = "tgx101_player_seek_step";
  private static final String KEY_TGX101_PLAYER_LOOP = "tgx101_player_loop";
  private static final String KEY_TGX101_PLAYER_RESUME = "tgx101_player_resume";

  /** TGx101 player: seconds per double tap on the sides */
  public int tgx101PlayerSeekStep () {
    return pmc.getInt(KEY_TGX101_PLAYER_SEEK_STEP, 5);
  }

  public void setTgx101PlayerSeekStep (int seconds) {
    pmc.putInt(KEY_TGX101_PLAYER_SEEK_STEP, seconds);
  }

  public boolean tgx101PlayerLoop () {
    return pmc.getBoolean(KEY_TGX101_PLAYER_LOOP, false);
  }

  public void setTgx101PlayerLoop (boolean value) {
    pmc.putBoolean(KEY_TGX101_PLAYER_LOOP, value);
  }

  /** Continue a video from where it was stopped */
  public boolean tgx101PlayerResume () {
    return pmc.getBoolean(KEY_TGX101_PLAYER_RESUME, true);
  }

  public void setTgx101PlayerResume (boolean value) {
    pmc.putBoolean(KEY_TGX101_PLAYER_RESUME, value);
  }

  private static final String KEY_TGX101_PLAYER_SPEED = "tgx101_player_speed";

  /** TGx101: the video player's speed, remembered between videos */
  public float tgx101PlayerSpeed () {
    return pmc.getFloat(KEY_TGX101_PLAYER_SPEED, 1f);
  }

  public void setTgx101PlayerSpeed (float speed) {
    pmc.putFloat(KEY_TGX101_PLAYER_SPEED, speed);
  }

  private static final String KEY_TGX101_PLAYER_GESTURES = "tgx101_player_gestures";

  /** TGx101: any of the video player gestures is on */
  public boolean tgx101PlayerGestures () {
    return tgx101PlayerGesture(GESTURE_SEEK) || tgx101PlayerGesture(GESTURE_BRIGHTNESS) || tgx101PlayerGesture(GESTURE_VOLUME) || tgx101PlayerGesture(GESTURE_SPEED);
  }

  public static final int GESTURE_SEEK = 1, GESTURE_BRIGHTNESS = 2, GESTURE_VOLUME = 3, GESTURE_SPEED = 4;

  /** TGx101: one player gesture — seek / brightness / volume swipes, hold for 2× — each off by default */
  public boolean tgx101PlayerGesture (int gesture) {
    return pmc.getBoolean(KEY_TGX101_PLAYER_GESTURES + "_" + gesture, false);
  }

  public void setTgx101PlayerGesture (int gesture, boolean value) {
    pmc.putBoolean(KEY_TGX101_PLAYER_GESTURES + "_" + gesture, value);
  }

  // TGx101: message filters — rules as JSON (Tgx101MessageFilters), on by default once there are rules
  private static final String KEY_TGX101_MESSAGE_FILTERS = "tgx101_message_filters";
  private static final String KEY_TGX101_MESSAGE_FILTERS_ON = "tgx101_message_filters_on";

  public String getTgx101MessageFilters () {
    return pmc.getString(KEY_TGX101_MESSAGE_FILTERS, null);
  }

  public void setTgx101MessageFilters (String json) {
    if (json == null) {
      pmc.remove(KEY_TGX101_MESSAGE_FILTERS);
    } else {
      pmc.putString(KEY_TGX101_MESSAGE_FILTERS, json);
    }
  }

  public boolean tgx101MessageFiltersEnabled () {
    return pmc.getBoolean(KEY_TGX101_MESSAGE_FILTERS_ON, true);
  }

  public void setTgx101MessageFiltersEnabled (boolean value) {
    pmc.putBoolean(KEY_TGX101_MESSAGE_FILTERS_ON, value);
  }

  private static final String KEY_SHOW_CHANNEL_MUTE_BUTTON = "settings_show_channel_mute_button";

  public boolean showChannelMuteButton () {
    return pmc.getBoolean(KEY_SHOW_CHANNEL_MUTE_BUTTON, true);
  }

  public void setShowChannelMuteButton (boolean show) {
    pmc.putBoolean(KEY_SHOW_CHANNEL_MUTE_BUTTON, show);
  }

  public boolean showCommentsButton () {
    return pmc.getBoolean(KEY_SHOW_COMMENTS_BUTTON, true);
  }

  public void setShowCommentsButton (boolean show) {
    pmc.putBoolean(KEY_SHOW_COMMENTS_BUTTON, show);
  }

  // TGx101: keep the input bar and message menus 16dp above the screen edge
  private static final String KEY_BOTTOM_GAP = "settings_tgx101_bottom_gap";

  public boolean bottomGapEnabled () {
    return pmc.getBoolean(KEY_BOTTOM_GAP, true);
  }

  /** The 16dp gap is only for gesture navigation; with navigation buttons it isn't needed. */
  public boolean needBottomGap () {
    return bottomGapEnabled() && isGestureNavigation();
  }

  private static Boolean gestureNavigation;
  private static long gestureNavigationCheckTime;

  /**
   * Gesture navigation, including OEM implementations that don't report it the AOSP way
   * (Vivo, Xiaomi, Samsung, Huawei). Cached for a few seconds.
   */
  public static boolean isGestureNavigation () {
    long now = android.os.SystemClock.uptimeMillis();
    if (gestureNavigation != null && now - gestureNavigationCheckTime < 5000) {
      return gestureNavigation;
    }
    boolean result = false;
    try {
      android.content.ContentResolver cr = org.thunderdog.challegram.tool.UI.getAppContext().getContentResolver();
      result = org.thunderdog.challegram.tool.Screen.isGesturalNavigationEnabled(org.thunderdog.challegram.tool.UI.getResources()) ||
        android.provider.Settings.Secure.getInt(cr, "navigation_mode", 0) == 2 ||
        android.provider.Settings.Secure.getInt(cr, "navigation_gesture_on", 0) != 0 ||        // Vivo
        android.provider.Settings.Global.getInt(cr, "force_fsg_nav_bar", 0) != 0 ||            // Xiaomi
        android.provider.Settings.Global.getInt(cr, "navigation_bar_gesture_while_hidden", 0) != 0 || // Samsung
        android.provider.Settings.Secure.getInt(cr, "secure_gesture_navigation", 0) != 0;      // Huawei
    } catch (Throwable ignored) { }
    gestureNavigation = result;
    gestureNavigationCheckTime = now;
    return result;
  }

  public void setBottomGapEnabled (boolean enabled) {
    pmc.putBoolean(KEY_BOTTOM_GAP, enabled);
  }

  // TGx101: extra space and a line between channel posts in the flat layout
  private static final String KEY_SEPARATE_CHANNEL_POSTS = "settings_separate_channel_posts";

  public boolean separateChannelPosts () {
    return pmc.getBoolean(KEY_SEPARATE_CHANNEL_POSTS, true);
  }

  public void setSeparateChannelPosts (boolean separate) {
    pmc.putBoolean(KEY_SEPARATE_CHANNEL_POSTS, separate);
  }

  // TGx101: hide the "Subscribe" link that channels append to their own posts
  private static final String KEY_HIDE_CHANNEL_SUBSCRIBE_LINK = "settings_hide_channel_subscribe_link";

  public boolean hideChannelSubscribeLink () {
    return pmc.getBoolean(KEY_HIDE_CHANNEL_SUBSCRIBE_LINK, true);
  }

  public void setHideChannelSubscribeLink (boolean hide) {
    pmc.putBoolean(KEY_HIDE_CHANNEL_SUBSCRIBE_LINK, hide);
  }

  // TGx101: hide the channel that an MTProto proxy owner pins to the chat list
  private static final String KEY_HIDE_PROXY_SPONSOR = "settings_hide_proxy_sponsor";

  public boolean hideProxySponsor () {
    return pmc.getBoolean(KEY_HIDE_PROXY_SPONSOR, false);
  }

  public void setHideProxySponsor (boolean hide) {
    pmc.putBoolean(KEY_HIDE_PROXY_SPONSOR, hide);
  }

  // TGx101: translate a whole chat. Stores the target language per account and chat
  private static final String KEY_CHAT_TRANSLATE_LANGUAGE_PREFIX = "tgx101_chat_translate_";

  public @Nullable String getWholeChatTranslateLanguage (int accountId, long chatId) {
    return pmc.getString(KEY_CHAT_TRANSLATE_LANGUAGE_PREFIX + accountId + "_" + chatId, null);
  }

  public void setWholeChatTranslateLanguage (int accountId, long chatId, @Nullable String language) {
    String key = KEY_CHAT_TRANSLATE_LANGUAGE_PREFIX + accountId + "_" + chatId;
    if (StringUtils.isEmpty(language)) {
      pmc.remove(key);
    } else {
      pmc.putString(key, language);
    }
  }

  // Video message stabilization. CAMERA2 uses Camera2 preview stabilization (Android 13+)
  // and falls back to GYRO when the device doesn't support it.
  public static final int ROUND_STABILIZATION_OFF = 0;
  public static final int ROUND_STABILIZATION_SYSTEM = 1;
  public static final int ROUND_STABILIZATION_GYRO = 2;
  public static final int ROUND_STABILIZATION_CAMERA2 = 3;
  private static final String KEY_ROUND_STABILIZATION = "settings_round_stabilization";

  public int getRoundStabilizationMode () {
    int mode = pmc.getInt(KEY_ROUND_STABILIZATION, ROUND_STABILIZATION_GYRO);
    return Math.max(ROUND_STABILIZATION_OFF, Math.min(ROUND_STABILIZATION_CAMERA2, mode));
  }

  public void setRoundStabilizationMode (int mode) {
    pmc.putInt(KEY_ROUND_STABILIZATION, mode);
  }

  // Encoded size of the square video, px.
  public static int roundVideoResolution (int quality) {
    switch (quality) {
      case ROUND_VIDEO_QUALITY_MAX: return 640;
      case ROUND_VIDEO_QUALITY_HIGH: return 480;
      case ROUND_VIDEO_QUALITY_HQ: return 320;
      default: return 240;
    }
  }

  public static int roundVideoBitrate (int quality) {
    switch (quality) {
      case ROUND_VIDEO_QUALITY_MAX: return 1_150_000; // encoders overshoot ~20%: a minute is ~10.5 MB, under Telegram's 12 MB limit for video notes
      case ROUND_VIDEO_QUALITY_HIGH: return 1_200_000;
      case ROUND_VIDEO_QUALITY_HQ: return 600_000;
      default: return 400_000;
    }
  }

  public static int roundAudioBitrate (int quality) {
    return quality >= ROUND_VIDEO_QUALITY_HIGH ? 64_000 : 32_000;
  }

  // Camera preview is picked by area closest to this value squared (16:9), and the
  // square is cropped from its short side, so it must be >= roundVideoResolution.
  public static int roundCameraMaxResolution (int quality) {
    switch (quality) {
      case ROUND_VIDEO_QUALITY_MAX: return 960; // ~1280x720
      case ROUND_VIDEO_QUALITY_HIGH: return 720; // ~960x540
      case ROUND_VIDEO_QUALITY_HQ: return 480; // ~640x360
      default: return 280;
    }
  }

  public void setNeedHqRoundVideos (boolean needRoundVideos) {
    setSetting(FLAG_OTHER_HQ_ROUND_VIDEOS, needRoundVideos);
  }

  public boolean startRoundWithRear () {
    return checkSetting(FLAG_OTHER_START_ROUND_REAR);
  }

  public void setStartRoundWithRear (boolean startWithRear) {
    setSetting(FLAG_OTHER_START_ROUND_REAR, startWithRear);
  }

  public boolean needChatQuickShare () {
    return checkNegativeSetting(FLAG_OTHER_NO_CHAT_QUICK_SHARE);
  }

  public boolean needChatQuickReply () {
    return checkNegativeSetting(FLAG_OTHER_NO_CHAT_QUICK_REPLY);
  }

  public void setDisableChatQuickActions (boolean disableChatQuickShare, boolean disableChatQuickReply) {
    int newSettings = getSettings();
    newSettings = BitwiseUtils.setFlag(newSettings, FLAG_OTHER_NO_CHAT_QUICK_SHARE, disableChatQuickShare);
    newSettings = BitwiseUtils.setFlag(newSettings, FLAG_OTHER_NO_CHAT_QUICK_REPLY, disableChatQuickReply);
    setSettings(newSettings);
  }

  public boolean useSystemEmoji () {
    return Config.ALLOW_SYSTEM_EMOJI && checkSetting(FLAG_OTHER_USE_SYSTEM_EMOJI);
  }

  public void setUseSystemEmoji (boolean useSystemEmoji) {
    setSetting(FLAG_OTHER_USE_SYSTEM_EMOJI, useSystemEmoji);
  }

  public boolean dontReadMessages () {
    return BuildConfig.DEBUG && checkSetting(FLAG_OTHER_DONT_READ_MESSAGES);
  }

  public void setDontReadMessages (boolean dontReadMessages) {
    if (dontReadMessages) {
      if (!BuildConfig.DEBUG || !Log.isEnabled(Log.TAG_MESSAGES_LOADER))
        dontReadMessages = false;
    }
    setSetting(FLAG_OTHER_DONT_READ_MESSAGES, dontReadMessages);
  }

  public boolean needSendByEnter () {
    return checkSetting(FLAG_OTHER_SEND_BY_ENTER);
  }

  public void setNeedSendByEnter (boolean needSendByEnter) {
    setSetting(FLAG_OTHER_SEND_BY_ENTER, needSendByEnter);
  }

  public boolean needHideChatKeyboardOnScroll () {
    return checkSetting(FLAG_OTHER_HIDE_CHAT_KEYBOARD);
  }

  public void setNeedHideChatKeyboardOnScroll (boolean needHideChatKeyboardOnScroll) {
    setSetting(FLAG_OTHER_HIDE_CHAT_KEYBOARD, needHideChatKeyboardOnScroll);
  }

  public boolean needPreviewChatOnHold () {
    return checkNegativeSetting(FLAG_OTHER_DISABLE_PREVIEW_CHATS_ON_HOLD);
  }

  public void setNeedPreviewChatsOnHold (boolean needPreviewChatsOnHold) {
    setNegativeSetting(FLAG_OTHER_DISABLE_PREVIEW_CHATS_ON_HOLD, needPreviewChatsOnHold);
  }

  public boolean useInAppBrowser () {
    return checkNegativeSetting(FLAG_OTHER_DISABLE_INAPP_BROWSER);
  }

  public void setUseInAppBrowser (boolean useInAppBrowser) {
    setNegativeSetting(FLAG_OTHER_DISABLE_INAPP_BROWSER, useInAppBrowser);
  }

  public int getNightMode () {
    if (_nightMode == null) {
      int nightMode = pmc.getInt(KEY_NIGHT_MODE, NIGHT_MODE_DEFAULT);
      if (nightMode == NIGHT_MODE_AUTO) {
        try {
          SensorManager sensorManager = (SensorManager) UI.getAppContext().getSystemService(Context.SENSOR_SERVICE);
          if (sensorManager != null) {
            if (sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) == null) {
              Log.e("Disabling night mode, because light sensor is unavailable");
              nightMode = NIGHT_MODE_DEFAULT;
              remove(KEY_NIGHT_MODE);
            }
          }
        } catch (Throwable t) {
          Log.w(t);
        }
      }
      _nightMode = nightMode;
    }
    return _nightMode;
  }

  private List<VideoLimit> defaultLimits;

  public List<VideoLimit> videoLimits () {
    if (defaultLimits != null) {
      return defaultLimits;
    }
    int maxTextureSize = U.getMaxTextureSize();
    VideoLimit[] limits = new VideoLimit[] {
      new VideoLimit(new VideoSize(256, 144)), // 144p
      new VideoLimit(new VideoSize(480, 360)), // 360p
      new VideoLimit(new VideoSize(854, 480)), // 480p
      new VideoLimit(new VideoSize(1024, 640)), // 640p
      new VideoLimit(new VideoSize(1280, 720), 60), // 720p | HD
      new VideoLimit(new VideoSize(1920, 1080), 60), // 1080p | FullHD
      new VideoLimit(new VideoSize(3840, 2160), 60), // 2160p | 4K
      new VideoLimit(new VideoSize(7680, 4320), 60) // 4320p | 8K
    };
    if (maxTextureSize <= 0) {
      return Arrays.asList(limits);
    }
    ArrayList<VideoLimit> result = new ArrayList<>(limits.length);
    for (VideoLimit limit : limits) {
      if (limit.size.majorSize > maxTextureSize) {
        float scale = (float) maxTextureSize / (float) limit.size.majorSize;
        int majorSize = (int) ((float) limit.size.majorSize * scale);
        int minorSize = (int) ((float) limit.size.minorSize * scale);
        majorSize -= majorSize % 2;
        minorSize -= minorSize % 2;
        if (result.isEmpty() || result.get(result.size() - 1).size.majorSize < majorSize) {
          result.add(limit.changeSize(new VideoSize(majorSize, minorSize)));
        }
        break;
      } else {
        result.add(limit);
      }
    }
    defaultLimits = result;
    return result;
  }

  public static final int DEFAULT_VIDEO_LIMIT = 854;
  public static final int DEFAULT_FRAME_RATE = 29; // DefaultVideoStrategy.DEFAULT_FRAME_RATE;

  public static class VideoSize {
    public final int majorSize, minorSize;

    public VideoSize (int majorSize, int minorSize) {
      this.majorSize = Math.max(majorSize, minorSize);
      this.minorSize = Math.min(minorSize, majorSize);
    }

    public VideoSize (int size) {
      this(size, size);
    }

    @Override
    public boolean equals (Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      VideoSize videoSize = (VideoSize) o;
      return majorSize == videoSize.majorSize &&
        minorSize == videoSize.minorSize;
    }

    public boolean isDefault () {
      return majorSize == minorSize && majorSize == DEFAULT_VIDEO_LIMIT;
    }

    public boolean isUnlimited () {
      return majorSize == minorSize && majorSize == -1;
    }

    @Override
    public int hashCode () {
      return ObjectUtils.hash(majorSize, minorSize);
    }
  }

  public static class VideoLimit {
    public static final int BITRATE_UNKNOWN = -1;

    public final @NonNull VideoSize size;
    public final int fps;
    public final long bitrate;

    public VideoLimit (VideoSize size) {
      this(size, DEFAULT_FRAME_RATE);
    }

    public VideoLimit (VideoSize size, int fps) {
      this(size, fps, BITRATE_UNKNOWN);
    }

    public VideoLimit (@NonNull VideoSize size, int fps, long bitrate) {
      this.size = size;
      this.fps = fps;
      this.bitrate = bitrate;
    }

    public VideoLimit unlimited () {
      return new VideoLimit(new VideoSize(-1), fps, bitrate);
    }

    public boolean isDefault () {
      return
        size.isDefault() &&
          fps == DEFAULT_FRAME_RATE &&
          bitrate == BITRATE_UNKNOWN;
    }

    @Override
    public boolean equals (Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      VideoLimit that = (VideoLimit) o;
      return fps == that.fps &&
        bitrate == that.bitrate &&
        size.equals(that.size);
    }

    @Override
    public int hashCode () {
      return ObjectUtils.hashCode(size, fps, bitrate);
    }

    public VideoLimit () {
      this((int[]) null);
    }

    public VideoLimit (VideoLimit copy) {
      this.size = copy.size;
      this.fps = copy.fps;
      this.bitrate = copy.bitrate;
    }

    public VideoLimit changeSize (VideoSize newSize) {
      return new VideoLimit(newSize, this.fps, this.bitrate);
    }

    public VideoLimit (@Nullable int[] data) {
      if (data != null && data.length > 0) {
        this.size = new VideoSize(data[0], data.length > 1 ? data[1] : data[0]);
        this.fps = data.length > 2 ? data[2] : DEFAULT_FRAME_RATE;
        this.bitrate = data.length > 3 ? (long) BitUnit.KBIT.toBits(data[3]) : BITRATE_UNKNOWN;
      } else {
        this.size = new VideoSize(DEFAULT_VIDEO_LIMIT);
        this.fps = DEFAULT_FRAME_RATE;
        this.bitrate = BITRATE_UNKNOWN;
      }
    }

    public int getOutputFrameRate (int frameRate) {
      return Math.min(
        frameRate > 0 ? frameRate : DEFAULT_FRAME_RATE,
        fps > 0 ? fps : DEFAULT_FRAME_RATE
      );
    }

    public static final double BITRATE_SCALE = 0.109;

    public long getOutputBitrate (Settings.VideoSize size, int frameRate, long inputBitrate) {
      return Math.round((size.majorSize * size.minorSize * frameRate) * BITRATE_SCALE);
    }

    @Nullable
    public VideoSize getOutputSize (int width, int height) {
      int majorSize = Math.max(width, height);
      int minorSize = Math.min(width, height);
      float ratio = Math.min(
        (float) this.size.majorSize / (float) majorSize,
        (float) this.size.minorSize / (float) minorSize
      );
      if (ratio > 1f)
        return null;
      majorSize = (int) ((float) majorSize * ratio);
      minorSize = (int) ((float) minorSize * ratio);
      if (majorSize % 2 == 1) majorSize--;
      if (minorSize % 2 == 1) minorSize--;
      return new VideoSize(majorSize, minorSize);
    }

    public int[] toIntArray () {
      if (isDefault())
        return null;
      int dataSize =
        /*bitrate != DefaultVideoStrategy.BITRATE_UNKNOWN ? 4 :*/ // never saving the bitrate as it is calculated automatically
        fps != DEFAULT_FRAME_RATE ? 3 :
          size.majorSize != size.minorSize ? 2 :
            size.majorSize != 0 ? 1 : 0;
      if (dataSize == 0)
        return null;
      int[] result = new int[dataSize];
      result[0] = size.majorSize;
      if (result.length > 1)
        result[1] = size.minorSize;
      if (result.length > 2)
        result[2] = fps;
      if (result.length > 3)
        result[3] = (int) Math.round(BitUnit.BIT.toKbit(bitrate));
      return result;
    }
  }

  @NonNull
  public VideoLimit getPreferredVideoLimit () {
    if (preferredVideoLimit == null) {
      preferredVideoLimit = new VideoLimit(pmc.getIntArray(KEY_VIDEO_LIMIT));
    }
    return preferredVideoLimit;
  }

  public void setPreferredVideoLimit (@Nullable VideoLimit videoLimit) {
    int[] data = videoLimit != null ? videoLimit.toIntArray() : null;
    this.preferredVideoLimit = videoLimit;
    if (data != null) {
      pmc.putIntArray(KEY_VIDEO_LIMIT, data);
    } else {
      pmc.remove(KEY_VIDEO_LIMIT);
    }
  }

  public void setAutoNightMode (int autoNightMode) {
    int oldNightMode = getNightMode();
    if (oldNightMode != autoNightMode) {
      this._nightMode = autoNightMode;
      if (autoNightMode == NIGHT_MODE_DEFAULT) {
        remove(KEY_NIGHT_MODE);
      } else {
        putInt(KEY_NIGHT_MODE, autoNightMode);
      }
      ThemeManager.instance().notifyAutoNightModeChanged(autoNightMode);
      if (autoNightMode == NIGHT_MODE_SCHEDULED || oldNightMode == NIGHT_MODE_SCHEDULED) {
        checkNightModeScheduler(true);
      }
    }
  }

  public float getMaxNightLux () {
    if (_nightModeAutoLux == null)
      _nightModeAutoLux = pmc.getFloat(KEY_MAX_NIGHT_LUX, MAX_NIGHT_LUX_DEFAULT);
    return _nightModeAutoLux;
  }

  public boolean setNightModeMaxLux (float lux, boolean save) {
    boolean changed = getMaxNightLux() != lux;
    this._nightModeAutoLux = lux;
    if (save) {
      putFloat(KEY_MAX_NIGHT_LUX, lux);
    }
    return changed;
  }

  public int getNightModeScheduleOn () {
    return BitwiseUtils.splitLongToFirstInt(getNightModeSchedule());
  }

  public int getNightModeScheduleOff () {
    return BitwiseUtils.splitLongToSecondInt(getNightModeSchedule());
  }

  public boolean inNightSchedule () {
    if (getNightMode() == NIGHT_MODE_SCHEDULED) {
      long nightModeSchedule = getNightModeSchedule();
      int startTime = BitwiseUtils.splitLongToFirstInt(nightModeSchedule);
      int endTime = BitwiseUtils.splitLongToSecondInt(nightModeSchedule);
      if (startTime != endTime) {
        Calendar c = DateUtils.getNowCalendar();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        int second = c.get(Calendar.SECOND);
        int time = BitwiseUtils.mergeTimeToInt(hour, minute, second);
        return BitwiseUtils.belongsToSchedule(time, startTime, endTime);
      }
    }
    return false;
  }

  public void checkNightModeScheduler (boolean needCancel) {
    if (needCancel) {
      cancelNightScheduleCheck();
    }
    if (getNightMode() != NIGHT_MODE_SCHEDULED) {
      return;
    }
    long nightModeSchedule = getNightModeSchedule();
    int startTime = BitwiseUtils.splitLongToFirstInt(nightModeSchedule);
    int endTime = BitwiseUtils.splitLongToSecondInt(nightModeSchedule);
    if (startTime == endTime) {
      ThemeManager.instance().setInNightMode(false, true);
      if (needCancel) {
        cancelNightScheduleCheck();
      }
      return;
    }

    long now = System.currentTimeMillis();
    Calendar c = DateUtils.calendarInstance(now);
    int hour = c.get(Calendar.HOUR_OF_DAY);
    int minute = c.get(Calendar.MINUTE);
    int second = c.get(Calendar.SECOND);
    int time = BitwiseUtils.mergeTimeToInt(hour, minute, second);

    boolean inNightSchedule = BitwiseUtils.belongsToSchedule(time, startTime, endTime);
    ThemeManager.instance().setInNightMode(inNightSchedule, true);
    int targetTime = inNightSchedule ? endTime : startTime;

    if (time > targetTime) {
      c.add(Calendar.DAY_OF_MONTH, 1);
    }
    c.set(Calendar.HOUR_OF_DAY, BitwiseUtils.splitIntToHour(targetTime));
    c.set(Calendar.MINUTE, BitwiseUtils.splitIntToMinute(targetTime));
    c.set(Calendar.SECOND, BitwiseUtils.splitIntToSecond(targetTime));

    long future = c.getTimeInMillis();
    if (future < now) {
      throw new RuntimeException("Theme schedule failed [time: " + U.timeToString(time) + ", startTime: " + U.timeToString(startTime) + ", endTime: " + U.timeToString(endTime) + "]");
    }

    scheduleNightScheduleCheck(future - now);
  }

  public void setNightModeSchedule (int time, boolean isOn) {
    long nightModeSchedule = getNightModeSchedule();
    setNightModeSchedule(BitwiseUtils.mergeLong(isOn ? time : BitwiseUtils.splitLongToFirstInt(nightModeSchedule), isOn ? BitwiseUtils.splitLongToSecondInt(nightModeSchedule) : time));
  }

  private long getNightModeSchedule () {
    if (_nightModeSchedule == null)
      _nightModeSchedule = pmc.getLong(KEY_NIGHT_MODE_SCHEDULED_TIME, BitwiseUtils.mergeLong(BitwiseUtils.mergeTimeToInt(22, 0, 0), BitwiseUtils.mergeTimeToInt(7, 0, 0)));
    return _nightModeSchedule;
  }

  public boolean setNightModeSchedule (long newSchedule) {
    if (getNightModeSchedule() != newSchedule) {
      this._nightModeSchedule = newSchedule;
      putLong(KEY_NIGHT_MODE_SCHEDULED_TIME, newSchedule);
      if (getNightMode() == NIGHT_MODE_SCHEDULED) {
        checkNightModeScheduler(true);
      }
      return true;
    }
    return false;
  }

  public boolean needOutboundCallsPrompt () {
    return checkSetting(FLAG_OTHER_OUTBOUND_CALLS_PROMPT);
  }

  public void setNeedOutboundCallsPrompt (boolean needPrompt) {
    setSetting(FLAG_OTHER_OUTBOUND_CALLS_PROMPT, needPrompt);
  }

  public boolean useCustomVibrations () {
    return checkNegativeSetting(FLAG_OTHER_DISABLE_CUSTOM_VIBRATIONS);
  }

  public void setUseCustomVibrations (boolean useCustomVibrations) {
    setNegativeSetting(FLAG_OTHER_DISABLE_CUSTOM_VIBRATIONS, useCustomVibrations);
  }

  // Camera

  public static final int CAMERA_RATIO_16_9 = 0;
  public static final int CAMERA_RATIO_4_3 = 1;
  public static final int CAMERA_RATIO_1_1 = 2;
  public static final int CAMERA_RATIO_FULL_SCREEN = 3;

  public int getCameraAspectRatioMode () {
    return pmc.getInt(KEY_CAMERA_ASPECT_RATIO, CAMERA_RATIO_16_9);
  }

  public float getCameraAspectRatio () {
    int mode = getCameraAspectRatioMode();
    switch (mode) {
      case CAMERA_RATIO_1_1:
        return 1f;
      case CAMERA_RATIO_4_3:
        return 4f / 3f;
      case CAMERA_RATIO_FULL_SCREEN:
        return 0f;
      case CAMERA_RATIO_16_9:
      default:
        return 16f / 9f;
    }
  }

  public void setCameraAspectRatioMode (int ratioMode) {
    if (ratioMode == CAMERA_RATIO_16_9) {
      pmc.remove(KEY_CAMERA_ASPECT_RATIO);
    } else {
      pmc.putInt(KEY_CAMERA_ASPECT_RATIO, ratioMode);
    }
  }

  public static final int CAMERA_TYPE_LEGACY = 0;
  public static final int CAMERA_TYPE_X = 1;
  public static final int CAMERA_TYPE_SYSTEM = 2;

  public static final int CAMERA_TYPE_DEFAULT = /*Config.CAMERA_X_AVAILABLE ? CAMERA_TYPE_X : */CAMERA_TYPE_LEGACY;

  public int getCameraType () {
    if (!Config.CUSTOM_CAMERA_AVAILABLE) {
      return CAMERA_TYPE_SYSTEM;
    }
    int type = pmc.getInt(KEY_CAMERA_TYPE, CAMERA_TYPE_DEFAULT);
    return type != CAMERA_TYPE_X || Config.CAMERA_X_AVAILABLE ? type : CAMERA_TYPE_DEFAULT;
  }

  public void setCameraType (int type) {
    if (type == CAMERA_TYPE_DEFAULT) {
      pmc.remove(KEY_CAMERA_TYPE);
    } else {
      pmc.putInt(KEY_CAMERA_TYPE, type);
    }
  }

  public static final int CAMERA_VOLUME_CONTROL_SHOOT = 0;
  public static final int CAMERA_VOLUME_CONTROL_ZOOM = 1;
  public static final int CAMERA_VOLUME_CONTROL_NONE = 2;

  public void setCameraVolumeControl (int type) {
    if (type == CAMERA_VOLUME_CONTROL_SHOOT) {
      pmc.remove(KEY_CAMERA_VOLUME_CONTROL);
    } else {
      pmc.putInt(KEY_CAMERA_VOLUME_CONTROL, type);
    }
  }

  public int getCameraVolumeControl () {
    return pmc.getInt(KEY_CAMERA_VOLUME_CONTROL, CAMERA_VOLUME_CONTROL_SHOOT);
  }

  // Font sizes

  public interface FontSizeChangeListener {
    void onFontSizeChanged (float newSizeDp);
  }

  private final List<Reference<FontSizeChangeListener>> chatFontSizeChangeListeners = new ArrayList<>();

  public float getChatFontSize () {
    if (_chatFontSize == null) {
      float chatFontSize = pmc.getFloat(KEY_CHAT_FONT_SIZE, CHAT_FONT_SIZE_DEFAULT);
      if (!isGoodChatFontSize(chatFontSize)) {
        chatFontSize = CHAT_FONT_SIZE_DEFAULT;
      }
      _chatFontSize = chatFontSize;
    }
    return _chatFontSize;
  }

  public boolean canResetChatFontSize () {
    return getChatFontSize() != CHAT_FONT_SIZE_DEFAULT || needChatFontSizeScaling();
  }

  public boolean needChatFontSizeScaling () {
    return checkSetting(FLAG_OTHER_FONT_SCALING);
  }

  public void setNeedChatFontSizeScaling (boolean need) {
    if (setSetting(FLAG_OTHER_FONT_SCALING, need)) {
      notifyFontSizeListeners(chatFontSizeChangeListeners, getChatFontSize());
    }
  }

  public void toggleChatFontSizeScaling () {
    setNeedChatFontSizeScaling(!needChatFontSizeScaling());
  }

  public void resetChatFontSize () {
    setNeedChatFontSizeScaling(false);
    setChatFontSize(CHAT_FONT_SIZE_DEFAULT);
  }

  public static boolean isGoodChatFontSize (float dp) {
    return dp >= CHAT_FONT_SIZE_MIN && dp <= CHAT_FONT_SIZE_MAX;
  }

  public boolean setChatFontSize (float fontSize) {
    if (!isGoodChatFontSize(fontSize))
      return false;
    float oldFontSize = getChatFontSize();
    if (oldFontSize != fontSize) {
      this._chatFontSize = fontSize;
      putFloat(KEY_CHAT_FONT_SIZE, fontSize);
      notifyFontSizeListeners(chatFontSizeChangeListeners, fontSize);
      return true;
    }
    return false;
  }

  private static void notifyFontSizeListeners (List<Reference<FontSizeChangeListener>> list, float newSizeDp) {
    final int size = list.size();
    for (int i = size - 1; i >= 0; i--) {
      FontSizeChangeListener listener = list.get(i).get();
      if (listener != null) {
        listener.onFontSizeChanged(newSizeDp);
      } else {
        list.remove(i);
      }
    }
  }

  public void addChatFontSizeChangeListener (FontSizeChangeListener listener) {
    ReferenceUtils.addReference(chatFontSizeChangeListeners, listener);
  }

  // Logs

  public boolean hasLogsEnabled () {
    return tdlibLogSettings.isEnabled() || Log.getLogLevel() > Log.LEVEL_ASSERT;
  }

  public void applyLogSettings (boolean async) {
    tdlibLogSettings.apply(async);
  }

  public void disableAllLogs () {
    tdlibLogSettings.disable();
    Log.setLogLevel(Log.LEVEL_ASSERT);
  }

  public void deleteAllLogs (boolean withTdlibLogs, Runnable after) {
    Background.instance().post(() -> {
      Log.deleteAll(Log.getLogFiles(), futureLogs -> {
        if (withTdlibLogs) {
          TdlibManager.deleteAllLogFiles();
        }
        if (after != null)
          after.run();
      }, null);
    });
  }

  // TDLib

  public TdlibLogSettings getLogSettings () {
    return tdlibLogSettings;
  }

  public TdlibLogSettings getTdlibLogSettings () {
    return tdlibLogSettings;
  }

  public void setAllowSpecialTdlibInstanceMode (int accountId, @Tdlib.Mode int instanceMode) {
    // Additional protection against corrupted accounts list file
    final boolean allowSpecialInstanceMode =
      instanceMode == Tdlib.Mode.SERVICE || instanceMode == Tdlib.Mode.DEBUG;
    String key = KEY_TDLIB_DEBUG_PREFIX + accountId;
    if (allowSpecialInstanceMode) {
      pmc.putVoid(key);
    } else {
      pmc.remove(key);
    }
  }

  public boolean allowSpecialTdlibInstanceMode (int accountId) {
    return pmc.contains(KEY_TDLIB_DEBUG_PREFIX + accountId);
  }

  // EmojiLayout

  public int getEmojiPosition () {
    return getInt(KEY_EMOJI_POSITION, 0);
  }

  public int getEmojiMediaSection () {
    return getInt(KEY_EMOJI_MEDIA_SECTION, EmojiMediaType.STICKER);
  }

  public void setEmojiPosition (int position) {
    putInt(KEY_EMOJI_POSITION, position);
  }

  public void setEmojiMediaSection (int section) {
    putInt(KEY_EMOJI_MEDIA_SECTION, section);
  }

  // Intro

  public void setIntroAttempted (boolean isAttempted) {
    if (isAttempted) {
      putBoolean(KEY_INTRO_ATTEMPTED, true);
    } else {
      remove(KEY_INTRO_ATTEMPTED);
    }
  }

  public boolean isIntroAttempted () {
    return getBoolean(KEY_INTRO_ATTEMPTED, false);
  }

  // Map

  public static final int MAP_TYPE_UNSET = -1;
  public static final int MAP_TYPE_DEFAULT = 0;
  public static final int MAP_TYPE_DARK = 1;
  public static final int MAP_TYPE_SATELLITE = 2;
  public static final int MAP_TYPE_TERRAIN = 3;
  public static final int MAP_TYPE_HYBRID = 4;

  public int getMapType () {
    return getInt(KEY_MAP_TYPE, MAP_TYPE_UNSET);
  }

  public void setMapType (int mapType) {
    if (mapType == MAP_TYPE_UNSET) {
      remove(KEY_MAP_TYPE);
    } else {
      putInt(KEY_MAP_TYPE, mapType);
    }
  }

  public int getMapProviderType (boolean cloud) {
    if (cloud) {
      if (_mapProviderTypeCloud == null)
        _mapProviderTypeCloud = pmc.getInt(KEY_MAP_PROVIDER_TYPE_CLOUD, MAP_PROVIDER_DEFAULT_CLOUD);
      return _mapProviderTypeCloud;
    } else {
      if (_mapProviderType == null)
        _mapProviderType = pmc.getInt(KEY_MAP_PROVIDER_TYPE, MAP_PROVIDER_UNSET);
      return _mapProviderType;
    }
  }

  public void setMapProviderType (int mapProviderType, boolean cloud) {
    if (cloud) {
      this._mapProviderTypeCloud = mapProviderType;
      putInt(KEY_MAP_PROVIDER_TYPE_CLOUD, mapProviderType);
    } else {
      this._mapProviderType = mapProviderType;
      putInt(KEY_MAP_PROVIDER_TYPE, mapProviderType);
    }
  }

  // Wallpaper

  public static String getWallpaperIdentifierSuffix (int identifier) {
    switch (identifier) {
      case 0:
        return "";
      case 1:
        return "_dark";
    }
    return "_other" + identifier;
  }

  public void deleteWallpapers (Tdlib tdlib, SharedPreferences.Editor editor) {
    final int accountId = tdlib.id();
    deleteWallpaper(tdlib, editor, 0);
    deleteWallpaper(tdlib, editor, 1);
    String key = (accountId != 0 ? KEY_WALLPAPER_PREFIX + "_" + accountId : KEY_WALLPAPER_PREFIX) + "_other";
    pmc.removeByPrefix(key);
    key = (accountId != 0 ? KEY_WALLPAPER_PREFIX + "_" + accountId : KEY_WALLPAPER_PREFIX) + "_chat";
    pmc.removeByPrefix(key);
  }

  public void deleteWallpaper (Tdlib tdlib, SharedPreferences.Editor editor, int wallpaperIdentifier) {
    deleteWallpaper(tdlib.id(), editor, wallpaperIdentifier);
  }

  public void deleteWallpaper (int accountId, SharedPreferences.Editor editor, int wallpaperIdentifier) {
    final String key = (accountId != 0 ? KEY_WALLPAPER_PREFIX + "_" + accountId : KEY_WALLPAPER_PREFIX) + getWallpaperIdentifierSuffix(wallpaperIdentifier);
    editor
      .remove(key)
      .remove(key + KEY_WALLPAPER_EMPTY)
      .remove(key + KEY_WALLPAPER_CUSTOM)
      .remove(key + KEY_WALLPAPER_PATH)
      .remove(key + KEY_WALLPAPER_ID);
  }

  // Last known location TODO: Binary format

  public static class LastLocation implements BlobEntry {
    public double latitude;
    public double longitude;
    public float zoomOrAccuracy;

    public LastLocation () {}

    public LastLocation (double latitude, double longitude, float zoomOrAccuracy) {
      this.latitude = latitude;
      this.longitude = longitude;
      this.zoomOrAccuracy = zoomOrAccuracy;
    }

    @Override
    public int estimatedBinarySize () {
      return 8 + 8 + 4;
    }

    @Override
    public void saveTo (Blob blob) {
      blob.writeDouble(latitude);
      blob.writeDouble(longitude);
      blob.writeFloat(zoomOrAccuracy);
    }

    @Override
    public void restoreFrom (Blob blob) {
      latitude = blob.readDouble();
      longitude = blob.readDouble();
      zoomOrAccuracy = blob.readFloat();
    }
  }

  public @Nullable LastLocation getViewedLocation () {
    return parseLocation(pmc.getByteArray(KEY_LAST_LOCATION));
  }

  @Deprecated
  private static @Nullable LastLocation parseLocation (String value, String delimiter) {
    if (StringUtils.isEmpty(value)) {
      return null;
    }
    String[] data = value.split(delimiter);
    try {
      double latitude = Double.parseDouble(data[0]);
      double longitude = Double.parseDouble(data[1]);
      float floatValue = data.length > 2 ? Float.parseFloat(data[2]) : 0;
      return new LastLocation(latitude, longitude, floatValue);
    } catch (Throwable t) {
      Log.e("Cannot read location", t);
    }
    return null;
  }

  private static @Nullable LastLocation parseLocation (byte[] data) {
    if (data != null && data.length == 8 + 8 + 4) {
      return new LastLocation(Blob.readDouble(data, 0), Blob.readDouble(data, 8), Blob.readFloat(data, 8 + 8));
    }
    return null;
  }

  public void setViewedLocation (double latitude, double longitude, float zoom) {
    byte[] buffer = new byte[8 + 8 + 4];
    Blob.writeDouble(buffer, 0, latitude);
    Blob.writeDouble(buffer, 8, longitude);
    Blob.writeFloat(buffer, 8 + 8, zoom);
    pmc.putByteArray(KEY_LAST_LOCATION, buffer);
  }

  public void saveLastKnownLocation (double latitude, double longitude, float accuracy) {
    byte[] buffer = new byte[8 + 8 + 4];
    Blob.writeDouble(buffer, 0, latitude);
    Blob.writeDouble(buffer, 8, longitude);
    Blob.writeFloat(buffer, 8 + 8, accuracy);
    pmc.putByteArray(KEY_LAST_INLINE_LOCATION, buffer);
  }

  public @Nullable LastLocation getLastKnownLocation () {
    return parseLocation(pmc.getByteArray(KEY_LAST_INLINE_LOCATION));
  }

  // PiP

  public long getPipPosition () {
    return getLong(KEY_PIP, BitwiseUtils.mergeLong(Lang.rtl() ? -1 : 1, -1));
  }

  public void setPipPosition (float x, float y) {
    putLong(KEY_PIP, BitwiseUtils.mergeLong((int) Math.signum(x), (int) Math.signum(y)));
  }

  public int getPipGravity () {
    return getInt(KEY_PIP_GRAVITY, Gravity.TOP | Gravity.RIGHT);
  }

  public void setPipGravity (int gravity) {
    if (gravity == (Gravity.TOP | Gravity.RIGHT)) {
      remove(KEY_PIP_GRAVITY);
    } else {
      putInt(KEY_PIP_GRAVITY, gravity);
    }
  }

  // Paint

  public int getPaintId () {
    return getInt(KEY_PAINT_ID, 0);
  }

  public void setPaintId (int paintId) {
    putInt(KEY_PAINT_ID, paintId);
  }

  // TGPlayerController

  public int getPlayerFlags () {
    return getInt(KEY_PLAYER_FLAGS, TGPlayerController.PLAY_FLAGS_DEFAULT);
  }

  public void setPlayerFlags (int flags) {
    putInt(KEY_PLAYER_FLAGS, flags);
  }

  // Whether user requested to close a keyboard

  public boolean shouldKeepKeyboardClosed (int accountId, long chatId, long messageId) {
    return getLong(key(KEY_HIDE_BOT_KEYBOARD_PREFIX + chatId, accountId), 0) == messageId;
  }

  public void onRequestKeyboardClose (int accountId, long chatId, long messageId, boolean close) {
    if (close) {
      putLong(key(KEY_HIDE_BOT_KEYBOARD_PREFIX + chatId, accountId), messageId);
    } else {
      remove(key(KEY_HIDE_BOT_KEYBOARD_PREFIX + chatId, accountId));
    }
  }

  // Bots

  public boolean allowLocationForBot (long userId) {
    return getBoolean("allow_location_" + userId, false);
  }

  public void setAllowLocationForBot (long userId) {
    putBoolean("allow_location_" + userId, true);
  }

  // Keyboard

  public void setKeyboardSize (int orientation, int size) {
    putInt("keyboard_size" + orientation, size);
  }

  public int getKeyboardSize (int orientation, int defSize) {
    int size = getInt("keyboard_size" + orientation, 0);
    if (size <= 0) {
      size = defSize;
    }
    return Math.max(size, Screen.dp(75f));
  }

  // Emoji

  private void saveBinaryMap (String storageKey, Map<String, ? extends BlobEntry> map) {
    int mapSize = map.size();
    int binarySize = Blob.sizeOf(mapSize);
    for (Map.Entry<String, ? extends BlobEntry> entry : map.entrySet()) {
      String key = entry.getKey();
      BlobEntry value = entry.getValue();
      binarySize += key.length() + value.estimatedBinarySize();
    }
    Blob blob = new Blob(binarySize);
    blob.writeVarint(mapSize);
    for (Map.Entry<String, ? extends BlobEntry> entry : map.entrySet()) {
      blob.writeString(entry.getKey());
      entry.getValue().saveTo(blob);
    }
    pmc.putByteArray(storageKey, blob.toByteArray());
  }

  private <T extends BlobEntry> void getBinaryMap (String storageKey, Map<String, T> out, Class<T> clazz) {
    byte[] data = pmc.getByteArray(storageKey);
    if (data == null || data.length == 0) {
      return;
    }
    try {
      Blob blob = new Blob(data);
      int size = blob.readVarint();
      for (int i = 0; i < size; i++) {
        String key = blob.readString();
        T value = clazz.newInstance();
        value.restoreFrom(blob);
        out.put(key, value);
      }
    } catch (Throwable t) {
      Log.e("Unable to get binary map", t);
    }
  }

  public void saveBinaryList (String storageKey, @NonNull List<BlobEntry> list) {
    int listSize = list.size();
    int binarySize = Blob.sizeOf(listSize);
    for (BlobEntry item : list) {
      binarySize += item.estimatedBinarySize();
    }
    Blob blob = new Blob(binarySize);
    blob.writeVarint(binarySize);
    for (BlobEntry entry : list) {
      entry.saveTo(blob);
    }
    pmc.putByteArray(storageKey, blob.toByteArray());
  }

  public @Nullable List<BlobEntry> getBinaryList (String storageKey, Class<? extends BlobEntry> clazz) {
    byte[] data = pmc.getByteArray(storageKey);
    if (data == null) {
      return null;
    }
    try {
      Blob blob = new Blob(data);
      int listSize = blob.readVarint();
      List<BlobEntry> list = new ArrayList<>(listSize);
      for (int i = 0; i < listSize; i++) {
        BlobEntry entry = clazz.newInstance();
        entry.restoreFrom(blob);
        list.add(entry);
      }
      return list;
    } catch (Throwable t) {
      Log.w("Cannot read binary list, key:%s", storageKey);
    }
    return null;
  }

  public void setEmojiCounters (Map<String, RecentInfo> infos) {
    saveBinaryMap(KEY_EMOJI_COUNTERS, infos);
  }

  public void setEmojiRecents (List<RecentEmoji> recents) {
    String[] out = new String[recents.size()];
    int i = 0;
    for (RecentEmoji recent : recents) {
      out[i++] = recent.emoji;
    }
    pmc.putStringArray(KEY_EMOJI_RECENTS, out);
  }

  public void clearEmojiRecents () {
    pmc.edit().remove(KEY_EMOJI_COUNTERS).remove(KEY_EMOJI_RECENTS).apply();
  }

  public void getEmojiCounters (Map<String, RecentInfo> infos) {
    getBinaryMap(KEY_EMOJI_COUNTERS, infos, RecentInfo.class);
  }

  public void getEmojiRecents (Map<String, RecentInfo> infos, List<RecentEmoji> recents) {
    String[] emojis = pmc.getStringArray(KEY_EMOJI_RECENTS);
    if (emojis != null && emojis.length > 0) {
      for (String emoji : emojis) {
        RecentInfo info = infos.get(emoji);
        if (info != null) {
          recents.add(new RecentEmoji(emoji, info));
        }
      }
    }
  }

  @Deprecated
  private static void parseEmojiCounters (Map<String, RecentInfo> infos, String cachedInfos) {
    if (cachedInfos != null) {
      String[] items = cachedInfos.split(",");
      for (String cachedInfo : items) {
        String[] info = cachedInfo.split(":");
        if (info.length == 3 && StringUtils.isNumeric(info[1]) && StringUtils.isNumeric(info[2])) {
          RecentInfo recentInfo = new RecentInfo(StringUtils.parseInt(info[1]), StringUtils.parseInt(info[2]));
          infos.put(info[0], recentInfo);
        }
      }
    }
  }

  @Deprecated
  private static void parseEmojiRecents (Map<String, RecentInfo> infos, List<RecentEmoji> recents, String cachedRecents) {
    if (cachedRecents != null) {
      String[] items = cachedRecents.split(",");
      for (String emoji : items) {
        RecentInfo info = infos.get(emoji);
        if (info != null) {
          recents.add(new RecentEmoji(emoji, info));
        }
      }
    }
  }

  @Deprecated
  private static void parseEmojiColors (Map<String, String> colors, String cachedColors) {
    if (cachedColors != null) {
      String[] items = cachedColors.split(",");
      for (String data : items) {
        String[] info = data.split(":");
        if (info.length == 2) {
          colors.put(info[0], info[1]);
        } else if (info.length == 1) {
          colors.put(info[0], "");
        }
      }
    }
  }

  private static final String EMOJI_OTHER_COLORS_SEPARATOR = ",";

  public void getEmojiOtherColors (Map<String, String[]> otherColors) {
    String[] array = pmc.getStringArray(KEY_EMOJI_OTHER_COLORS);
    if (array != null && array.length > 0) {
      String key = null;
      for (String value : array) {
        if (key == null) {
          key = value == null ? "" : value;
        } else {
          otherColors.put(key, value.split(EMOJI_OTHER_COLORS_SEPARATOR));
          key = null;
        }
      }
    }
  }

  public void setEmojiOtherColors (Map<String, String[]> otherColors, @Nullable SharedPreferences.Editor editor) {
    boolean ownsEditor = false;
    if (editor == null) {
      editor = edit();
      ownsEditor = true;
    }
    int size = otherColors.size();
    if (size > 0) {
      String[] result = new String[size * 2];
      int i = 0;
      for (Map.Entry<String, String[]> entry : otherColors.entrySet()) {
        result[i++] = entry.getKey();
        result[i++] = Strings.join(EMOJI_OTHER_COLORS_SEPARATOR, (Object[]) entry.getValue());
      }
      pmc.putStringArray(KEY_EMOJI_OTHER_COLORS, result);
    } else {
      editor.remove(KEY_EMOJI_OTHER_COLORS);
    }
    if (ownsEditor) {
      editor.apply();
    }
  }

  public void getEmojiColors (Map<String, String> colors) {
    String[] array = pmc.getStringArray(KEY_EMOJI_COLORS);
    if (array != null && array.length > 0) {
      String key = null;
      for (String value : array) {
        if (key == null) {
          key = value == null ? "" : value;
        } else {
          colors.put(key, value);
          key = null;
        }
      }
    }
  }

  public void setEmojiColors (Map<String, String> colors, @Nullable SharedPreferences.Editor editor) {
    boolean ownsEditor = false;
    if (editor == null) {
      editor = edit();
      ownsEditor = true;
    }
    int size = colors.size();
    if (size > 0) {
      String[] result = new String[size * 2];
      int i = 0;
      for (Map.Entry<String, String> entry : colors.entrySet()) {
        result[i++] = entry.getKey();
        result[i++] = entry.getValue();
      }
      pmc.putStringArray(KEY_EMOJI_COLORS, result);
    } else {
      editor.remove(KEY_EMOJI_COLORS);
    }
    if (ownsEditor) {
      editor.apply();
    }
  }

  public String getEmojiDefaultTone () {
    return getString(KEY_EMOJI_DEFAULT_COLOR, null);
  }

  public void setEmojiDefaultTone (String defaultTone, Map<String, String> colors) {
    if (colors.size() > 0) {
      SharedPreferences.Editor editor = edit();
      if (StringUtils.isEmpty(defaultTone)) {
        editor.remove(KEY_EMOJI_DEFAULT_COLOR);
      } else {
        editor.putString(KEY_EMOJI_DEFAULT_COLOR, defaultTone);
      }
      colors.clear();
      setEmojiColors(colors, editor);
      editor.apply();
    } else {
      if (StringUtils.isEmpty(defaultTone)) {
        remove(KEY_EMOJI_DEFAULT_COLOR);
      } else {
        putString(KEY_EMOJI_DEFAULT_COLOR, defaultTone);
      }
    }
  }

  // Scroll offsets

  public static class SavedMessageId {
    public final MessageId id;
    public final int offsetPixels;
    public final long[] returnToMessageIds;
    public final boolean readFully;
    public final long topEndMessageId;

    public static class Builder {
      private long messageChatId, messageId, topEndMessageId;
      private long[] otherMessageIds;

      private int offsetPixels;
      private long[] returnToMessageIds;
      private boolean readFully;

      public Builder (long chatId) {
        this.messageChatId = chatId;
      }

      public SavedMessageId build () {
        return new SavedMessageId(new MessageId(messageChatId, messageId, otherMessageIds), offsetPixels, returnToMessageIds, readFully, topEndMessageId);
      }
    }

    public SavedMessageId (MessageId id, int offsetPixels, long[] returnToMessageId, boolean readFully, long topEndMessageId) {
      this.id = id;
      this.offsetPixels = offsetPixels;
      this.returnToMessageIds = returnToMessageId;
      this.readFully = readFully;
      this.topEndMessageId = topEndMessageId;
    }
  }

  public void removeScrollPositions (int accountId, @Nullable SharedPreferences.Editor editor) {
    removeByPrefix(key(KEY_SCROLL_CHAT_PREFIX, accountId), editor);
  }

  public void setScrollMessageId (int accountId, long chatId, @Nullable TdApi.MessageTopic topicId, @Nullable SavedMessageId savedMessageId) {
    String keyId = makeScrollChatKey(KEY_SCROLL_CHAT_MESSAGE_ID, accountId, chatId, topicId);
    String keyChatId = makeScrollChatKey(KEY_SCROLL_CHAT_MESSAGE_CHAT_ID, accountId, chatId, topicId);
    String keyReturnToIds = makeScrollChatKey(KEY_SCROLL_CHAT_RETURN_TO_MESSAGE_IDS_STACK, accountId, chatId, topicId);
    String keyAliases = makeScrollChatKey(KEY_SCROLL_CHAT_ALIASES, accountId, chatId, topicId);
    String keyOffset = makeScrollChatKey(KEY_SCROLL_CHAT_OFFSET, accountId, chatId, topicId);
    String keyReadFully = makeScrollChatKey(KEY_SCROLL_CHAT_READ_FULLY, accountId, chatId, topicId);
    String keyTopEnd = makeScrollChatKey(KEY_SCROLL_CHAT_TOP_END, accountId, chatId, topicId);
    SharedPreferences.Editor editor = edit();
    if (savedMessageId == null) {
      editor
        .remove(keyId)
        .remove(keyChatId)
        .remove(keyReturnToIds)
        .remove(keyOffset)
        .remove(keyAliases)
        .remove(keyReadFully)
        .remove(keyTopEnd);
    } else {
      editor.putLong(keyId, savedMessageId.id.getMessageId());
      if (savedMessageId.id.getChatId() != chatId) {
        editor.putLong(keyChatId, savedMessageId.id.getChatId());
      } else {
        editor.remove(keyChatId);
      }
      if (savedMessageId.id.getOtherMessageIds() != null && savedMessageId.id.getOtherMessageIds().length > 0) {
        ((LevelDB) editor).putLongArray(keyAliases, savedMessageId.id.getOtherMessageIds());
      } else {
        editor.remove(keyAliases);
      }

      if (savedMessageId.offsetPixels != 0) {
        editor.putInt(keyOffset, savedMessageId.offsetPixels);
      } else {
        editor.remove(keyOffset);
      }

      if (savedMessageId.returnToMessageIds != null && savedMessageId.returnToMessageIds.length > 0)
        ((LevelDB) editor).putLongArray(keyReturnToIds, savedMessageId.returnToMessageIds);
      else
        editor.remove(keyReturnToIds);

      if (savedMessageId.readFully) {
        editor.putBoolean(keyReadFully, true);
      } else {
        editor.remove(keyReadFully);
      }

      if (savedMessageId.topEndMessageId != 0) {
        editor.putLong(keyTopEnd, savedMessageId.topEndMessageId);
      } else {
        editor.remove(keyTopEnd);
      }
    }
    editor.apply();
  }

  @Nullable
  public SavedMessageId getScrollMessageId (int accountId, long chatId, @Nullable TdApi.MessageTopic topicId) {
    String prefix = makeScrollChatKey(null, accountId, chatId, null);
    String topicSuffix = topicId != null ? "_" + Td.cacheKey(topicId) : null;
    SavedMessageId.Builder b = null;
    for (LevelDB.Entry entry : pmc.find(prefix)) {
      String key = entry.key();
      boolean mismatch;
      if (StringUtils.isEmpty(topicSuffix)) {
        if (TdConstants.COMPILE_CHECK) {
          Td.assertMessageTopic_98b4a9a3();
        }
        mismatch = key.matches("^.+_(?:thread|forum|direct|saved)+\\d+$");
      } else {
        mismatch = !key.endsWith(topicSuffix);
      }
      if (mismatch) {
        continue;
      }
      if (b == null) {
        b = new SavedMessageId.Builder(chatId);
      }
      String dataKey = key.substring(prefix.length(), key.length() - StringUtils.length(topicSuffix));
      switch (dataKey) {
        case KEY_SCROLL_CHAT_MESSAGE_ID:
          b.messageId = entry.asLong();
          break;
        case KEY_SCROLL_CHAT_MESSAGE_CHAT_ID:
          b.messageChatId = entry.asLong();
          break;
        case KEY_SCROLL_CHAT_ALIASES:
          b.otherMessageIds = entry.asLongArray();
          break;
        case KEY_SCROLL_CHAT_OFFSET:
          b.offsetPixels = entry.asInt();
          break;
        case KEY_SCROLL_CHAT_RETURN_TO_MESSAGE_IDS_STACK:
          b.returnToMessageIds = entry.asLongArray();
          break;
        case KEY_SCROLL_CHAT_READ_FULLY:
          b.readFully = entry.asBoolean();
          break;
        case KEY_SCROLL_CHAT_TOP_END:
          b.topEndMessageId = entry.asLong();
          break;
      }
    }
    if (b != null && b.messageChatId != chatId) {
      b.messageId = 0; // TODO support on chat level
    }
    return b != null ? b.build() : null;
  }

  public void updateScrollMessageId (int accountId, long chatId, long oldMessageId, long newMessageId) {
    String prefix = key(KEY_SCROLL_CHAT_PREFIX + chatId, accountId);
    SharedPreferences.Editor editor = null;
    for (LevelDB.Entry entry : pmc.find(prefix)) {
      String suffix = entry.key().substring(prefix.length()).replaceAll("_thread[\\d]+$", "");
      switch (suffix) {
        case KEY_SCROLL_CHAT_MESSAGE_ID: {
          if (entry.asLong() == oldMessageId) {
            if (editor == null) {
              editor = edit();
            }
            editor.putLong(entry.key(), newMessageId);
          }
          break;
        }
        case KEY_SCROLL_CHAT_ALIASES:
        case KEY_SCROLL_CHAT_RETURN_TO_MESSAGE_IDS_STACK: {
          long[] messageIds = entry.asLongArray();
          int index = messageIds != null ? ArrayUtils.indexOf(messageIds, oldMessageId) : -1;
          if (index >= 0) {
            messageIds[index] = newMessageId;
            Arrays.sort(messageIds);
            if (editor == null) {
              editor = edit();
            }
            ((LevelDB) editor).putLongArray(entry.key(), messageIds);
          }
          break;
        }
      }
    }
    if (editor != null) {
      editor.apply();
    }
  }

  private static String makeScrollChatKey (String key, int accountId, long chatId, @Nullable TdApi.MessageTopic topicId) {
    StringBuilder b = new StringBuilder(KEY_SCROLL_CHAT_PREFIX)
      .append(chatId);
    if (key != null) {
      b.append(key);
    }
    if (topicId != null) {
      b.append("_").append(Td.cacheKey(topicId));
    }
    return key(b.toString(), accountId);
  }

  // Other settings

  public boolean isAccountListOpened () {
    return checkSetting(FLAG_OTHER_ACCOUNT_LIST_OPENED);
  }

  public void setAccountListOpened (boolean isOpened) {
    setSetting(FLAG_OTHER_ACCOUNT_LIST_OPENED, isOpened);
  }

  // Proxy

  // type:int   description: Proxy id
  private static final String KEY_PROXY_LAST_ID = "proxy_id"; // autoincrement
  // type:int description: Current proxy
  private static final String KEY_PROXY_CURRENT = "proxy_current";
  // type:byte description: Global proxy settings
  private static final String KEY_PROXY_SETTINGS = "proxy_settings";
  // type:int[] description: Proxy order
  private static final String KEY_PROXY_ORDER = "proxy_order";

  private static final String KEY_PROXY_ITEM_PREFIX = "proxy_item_";
  // type:byte[] description: Proxy configuration (constructor, server, port, ?username, ?password)
  private static final String KEY_PROXY_PREFIX_CONFIG = KEY_PROXY_ITEM_PREFIX + "config_"; // + proxy_id
  // type:long   description: Last connection time (in milliseconds)
  @Deprecated
  private static final String KEY_PROXY_PREFIX_CONNECTION_TIME = KEY_PROXY_ITEM_PREFIX + "time_"; // + proxy_id + "_" + accountId
  // type:string description: Proxy description
  private static final String KEY_PROXY_PREFIX_DESCRIPTION = KEY_PROXY_ITEM_PREFIX + "desc_";
  // type:int description: Amount of times proxy was connected
  private static final String KEY_PROXY_PREFIX_CONNECTED_COUNT = KEY_PROXY_ITEM_PREFIX + "success_";
  // type:long[] description: {time proxy connected, time elapsed since connection attempted}
  private static final String KEY_PROXY_PREFIX_LAST_CONNECTION = KEY_PROXY_ITEM_PREFIX + "connect_";
  // type:long[] description: {time pong received, ping value}
  private static final String KEY_PROXY_PREFIX_LAST_PING = KEY_PROXY_ITEM_PREFIX + "ping_";

  public static final int PROXY_FLAG_ENABLED = 1;
  public static final int PROXY_FLAG_USE_FOR_CALLS = 1 << 1;
  public static final int PROXY_FLAG_SHOW_ERRORS = 1 << 2;
  public static final int PROXY_FLAG_SWITCH_AUTOMATICALLY = 1 << 3;
  public static final int PROXY_FLAG_SWITCH_ALLOW_DIRECT = 1 << 4;

  public static final int PROXY_ID_UNKNOWN = -1;
  public static final int PROXY_ID_NONE = 0;

  public static final int PROXY_TIME_UNSET = -1;
  public static final int PROXY_TIME_LOADING = -2;
  public static final int PROXY_TIME_EMPTY = -3;

  /**
   * @return Identifier of proxy to be applied to TDLib client instances.
   * Returns {@link #PROXY_ID_NONE} in case {@link #PROXY_FLAG_ENABLED} is not set.
   */
  public int getEffectiveProxyId () {
    if (BitwiseUtils.hasFlag(getProxySettings(), PROXY_FLAG_ENABLED)) {
      return getAvailableProxyId();
    }
    return PROXY_ID_NONE;
  }

  /**
   * @return Identifier of proxy to be applied to libtgvoip
   */
  public int getEffectiveCallsProxyId () {
    int settings = getProxySettings();
    if (BitwiseUtils.hasFlag(settings, PROXY_FLAG_ENABLED) && BitwiseUtils.hasFlag(settings, PROXY_FLAG_USE_FOR_CALLS)) {
      return getAvailableProxyId();
    }
    return PROXY_ID_NONE;
  }

  /**
   * @return Identifier of proxy to be applied to TDLib client instances.
   * Returns proxy identifier even when {@link #PROXY_FLAG_ENABLED} is not set.
   */
  public int getAvailableProxyId () {
    return pmc.getInt(KEY_PROXY_CURRENT, PROXY_ID_NONE);
  }

  /**
   * @return Number of available proxy configurations
   */
  public int getAvailableProxyCount () {
    return (int) pmc.getSizeByPrefix(KEY_PROXY_PREFIX_CONFIG);
  }

  /**
   * @return true if there is at least one available proxy configuration
   */
  public boolean hasProxyConfiguration () {
    return getAvailableProxyCount() > 0;
  }

  /**
   * @return Current proxy flags.
   */
  public int getProxySettings () {
    return pmc.getByte(KEY_PROXY_SETTINGS, (byte) 0);
  }

  /**
   * @param proxyFlag Proxy flag to check
   * @return Whether proxy flag is turned on
   */
  public boolean checkProxySetting (int proxyFlag) {
    return (getProxySettings() & proxyFlag) != 0;
  }

  /**
   * Toggles proxy setting.
   *
   * @param setting flag to be toggled
   * @return changed value of the flag.
   */
  public boolean toggleProxySetting (int setting) {
    int settings = getProxySettings();
    return setProxySettingImpl(settings, setting, (settings & setting) == 0);
  }

  /**
   * Changes proxy setting value.
   *
   * @param setting flag to be changed
   * @param enabled desired flag state
   */
  public void setProxySetting (int setting, boolean enabled) {
    setProxySettingImpl(getProxySettings(), setting, enabled);
  }

  /**
   * Disables proxy, if any configuration is in use.
   */
  public void disableProxy () {
    int settings = getProxySettings();
    if ((settings & PROXY_FLAG_ENABLED) != 0) {
      setProxySettingImpl(settings, PROXY_FLAG_ENABLED, false);
    }
  }

  private boolean setProxySettingImpl (final int oldSettings, int setting, boolean enabled) {
    int newSettings = BitwiseUtils.setFlag(oldSettings, setting, enabled);
    if (newSettings == oldSettings) {
      return enabled;
    }
    if (setting == PROXY_FLAG_ENABLED) {
      int proxyId;
      Proxy proxy;
      if (enabled) {
        proxyId = getAvailableProxyId();
        if (proxyId <= PROXY_ID_NONE) {
          return false;
        }
        proxy = getProxyConfig(proxyId);
        if (proxy == null) {
          return false;
        }
      } else {
        proxyId = PROXY_ID_NONE;
        proxy = null;
      }
      pmc.putByte(KEY_PROXY_SETTINGS, (byte) newSettings);
      if (proxy != null) {
        dispatchProxyConfiguration(proxyId, proxy.proxy, proxy.description, true, false);
      } else {
        dispatchProxyConfiguration(PROXY_ID_NONE, null, null, true, false);
      }
    } else {
      pmc.putByte(KEY_PROXY_SETTINGS, (byte) newSettings);
    }
    return enabled;
  }

  /**
   * @param proxyId Proxy identifier
   * @return Proxy configuration, such as server,port,username,password,etc
   */
  public @Nullable Proxy getProxyConfig (int proxyId) {
    if (proxyId != PROXY_ID_NONE) {
      Proxy proxy = readProxy(proxyId, pmc.getByteArray(KEY_PROXY_PREFIX_CONFIG + proxyId), null);
      if (proxy == null) {
        Log.e("Configuration unavailable, proxyId:%d", proxyId);
      }
      return proxy;
    }
    return null;
  }

  /**
   * @param proxyId Proxy identifier
   * @return Proxy name
   */
  public @Nullable String getProxyName (int proxyId) {
    Proxy proxy = getProxyConfig(proxyId);
    return proxy != null ? proxy.getName().toString() : null;
  }

  private static @Nullable Proxy readProxy (int proxyId, @Nullable byte[] data, @Nullable Blob blob) {
    if (data == null || data.length == 0) {
      return null;
    }
    try {
      if (blob == null) {
        blob = new Blob(data);
      } else {
        blob.reset(data);
      }
      final String server = blob.readString();
      final int port = blob.readInt();
      final @Proxy.Type int typeId = blob.readByte();
      final TdApi.ProxyType type;

      switch (typeId) {
        case Proxy.TYPE_SOCKS5: {
          TdApi.ProxyTypeSocks5 socks5 = new TdApi.ProxyTypeSocks5("", "");
          int flags = blob.readByte();
          if ((flags & Proxy.INTERNAL_FLAG_HAS_USERNAME) != 0)
            socks5.username = blob.readString();
          if ((flags & Proxy.INTERNAL_FLAG_HAS_PASSWORD) != 0)
            socks5.password = blob.readString();
          type = socks5;
          break;
        }
        case Proxy.TYPE_MTPROTO: {
          type = new TdApi.ProxyTypeMtproto(blob.readString());
          break;
        }
        case Proxy.TYPE_HTTP: {
          TdApi.ProxyTypeHttp http = new TdApi.ProxyTypeHttp("", "", false);
          int flags = blob.readByte();
          if ((flags & Proxy.INTERNAL_FLAG_HAS_USERNAME) != 0)
            http.username = blob.readString();
          if ((flags & Proxy.INTERNAL_FLAG_HAS_PASSWORD) != 0)
            http.password = blob.readString();
          http.httpOnly = blob.readByte() == (byte) 1;
          type = http;
          break;
        }
        default:
          throw new UnsupportedOperationException(Integer.toString(typeId));
      }

      return new Proxy(proxyId, new TdApi.Proxy(server, port, type), null);
    } catch (Throwable t) {
      Log.w("Unable to read proxy configuration", t);
    }
    return null;
  }

  public static String getProxyUsername (@NonNull TdApi.ProxyType type) {
    switch (type.getConstructor()) {
      case TdApi.ProxyTypeSocks5.CONSTRUCTOR:
        return ((TdApi.ProxyTypeSocks5) type).username;
      case TdApi.ProxyTypeHttp.CONSTRUCTOR:
        return ((TdApi.ProxyTypeHttp) type).username;
      case TdApi.ProxyTypeMtproto.CONSTRUCTOR:
        return null;
      default: {
        Td.assertProxyType_bc1a1076();
        throw Td.unsupported(type);
      }
    }
  }

  public static String getProxyPassword (@NonNull TdApi.ProxyType type) {
    switch (type.getConstructor()) {
      case TdApi.ProxyTypeSocks5.CONSTRUCTOR:
        return ((TdApi.ProxyTypeSocks5) type).password;
      case TdApi.ProxyTypeHttp.CONSTRUCTOR:
        return ((TdApi.ProxyTypeHttp) type).password;
      case TdApi.ProxyTypeMtproto.CONSTRUCTOR:
        return null;
      default: {
        Td.assertProxyType_bc1a1076();
        throw Td.unsupported(type);
      }
    }
  }

  public static int getProxyDefaultOrder (@NonNull TdApi.ProxyType type) {
    switch (type.getConstructor()) {
      case TdApi.ProxyTypeMtproto.CONSTRUCTOR:
        return 1;
      case TdApi.ProxyTypeSocks5.CONSTRUCTOR:
        return 2;
      case TdApi.ProxyTypeHttp.CONSTRUCTOR:
        return 3;
      default:
        Td.assertProxyType_bc1a1076();
        throw Td.unsupported(type);
    }
  }

  private static @Proxy.Type int getProxyType (@NonNull TdApi.ProxyType type) {
    switch (type.getConstructor()) {
      case TdApi.ProxyTypeSocks5.CONSTRUCTOR:
        return Proxy.TYPE_SOCKS5;
      case TdApi.ProxyTypeMtproto.CONSTRUCTOR:
        return Proxy.TYPE_MTPROTO;
      case TdApi.ProxyTypeHttp.CONSTRUCTOR:
        return Proxy.TYPE_HTTP;
      default:
        Td.assertProxyType_bc1a1076();
        throw Td.unsupported(type);
    }
  }

  private static byte[] serializeProxy (@NonNull TdApi.Proxy proxy) {
    @Proxy.Type int typeId = getProxyType(proxy.type);

    final Blob blob;
    int size = 0;

    size += Blob.sizeOf(proxy.server, false);
    size += 4 /*port*/ + 1 /*typeId*/;

    switch (typeId) {
      case Proxy.TYPE_SOCKS5: {
        TdApi.ProxyTypeSocks5 socks5 = (TdApi.ProxyTypeSocks5) proxy.type;

        size += calculateProxyCredentialsSize(socks5.username, socks5.password);

        break;
      }
      case Proxy.TYPE_MTPROTO: {
        TdApi.ProxyTypeMtproto mtproto = (TdApi.ProxyTypeMtproto) proxy.type;

        size += Blob.sizeOf(mtproto.secret != null ? mtproto.secret : "", true);

        break;
      }
      case Proxy.TYPE_HTTP: {
        TdApi.ProxyTypeHttp http = (TdApi.ProxyTypeHttp) proxy.type;

        size += calculateProxyCredentialsSize(http.username, http.password);
        size += 1;

        break;
      }
    }

    blob = new Blob(size);
    blob.writeString(proxy.server);
    blob.writeInt(proxy.port);
    blob.writeByte((byte) typeId);

    switch (typeId) {
      case Proxy.TYPE_SOCKS5: {
        TdApi.ProxyTypeSocks5 socks5 = (TdApi.ProxyTypeSocks5) proxy.type;
        writeProxyCredentials(blob, socks5.username, socks5.password);
        break;
      }
      case Proxy.TYPE_MTPROTO: {
        TdApi.ProxyTypeMtproto mtproto = (TdApi.ProxyTypeMtproto) proxy.type;

        blob.writeString(mtproto.secret != null ? mtproto.secret : "");

        break;
      }
      case Proxy.TYPE_HTTP: {
        TdApi.ProxyTypeHttp http = (TdApi.ProxyTypeHttp) proxy.type;

        writeProxyCredentials(blob, http.username, http.password);
        blob.writeByte((byte) (http.httpOnly ? 1 : 0));

        break;
      }
    }

    return blob.toByteArray();
  }

  private static int calculateProxyCredentialsSize (String username, String password) {
    return 1 + Blob.sizeOf(username, false) + Blob.sizeOf(password, false);
  }

  private static void writeProxyCredentials (Blob blob, String username, String password) {
    int flags = 0;
    if (!StringUtils.isEmpty(username))
      flags |= Proxy.INTERNAL_FLAG_HAS_USERNAME;
    if (!StringUtils.isEmpty(password))
      flags |= Proxy.INTERNAL_FLAG_HAS_PASSWORD;
    blob.writeByte((byte) flags);
    if ((flags & Proxy.INTERNAL_FLAG_HAS_USERNAME) != 0)
      blob.writeString(username);
    if ((flags & Proxy.INTERNAL_FLAG_HAS_PASSWORD) != 0)
      blob.writeString(password);
  }

  /**
   * Get existing proxy identifier
   *
   * @param proxy Proxy information
   * @return Proxy identifier, or {@link #PROXY_ID_NONE} if not found
   */
  public int getExistingProxyId (@NonNull TdApi.Proxy proxy) {
    final byte[] data = serializeProxy(proxy);
    if (data != null) {
      String existingKey = pmc.findByValue(KEY_PROXY_PREFIX_CONFIG, data);
      if (existingKey != null) {
        return StringUtils.parseInt(existingKey.substring(KEY_PROXY_PREFIX_CONFIG.length()));
      }
    }
    return PROXY_ID_NONE;
  }

  public void trackSuccessfulConnection (int proxyId, long timestampMs, long resultMs, boolean isPing) {
    if (proxyId <= Settings.PROXY_ID_UNKNOWN)
      throw new IllegalArgumentException(Integer.toString(proxyId));
    if (isPing) {
      pmc.putLongArray(KEY_PROXY_PREFIX_LAST_PING + proxyId, new long[] {timestampMs, resultMs});
    } else {
      int connectedCount =
        pmc.getInt(KEY_PROXY_PREFIX_CONNECTED_COUNT + proxyId, 0)
          + 1;
      pmc.edit()
        .putLongArray(KEY_PROXY_PREFIX_LAST_CONNECTION + proxyId, new long[] {timestampMs, resultMs})
        .putInt(KEY_PROXY_PREFIX_CONNECTED_COUNT + proxyId, connectedCount)
        .apply();
    }
  }

  public int addOrUpdateProxy (@NonNull TdApi.Proxy proxy, @Nullable String proxyDescription, boolean setAsCurrent) {
    return addOrUpdateProxy(proxy, proxyDescription, setAsCurrent, PROXY_ID_NONE);
  }

  /**
   * Adds proxy configuration or returns identifier of existing one.
   *
   * @param proxy            Proxy server
   * @param proxyDescription Nullable alias for the proxy.
   * @param setAsCurrent     If set to false, proxy will be saved for later use.
   * @param existingProxyId  Existing proxy identifier to be modified or {@link #PROXY_ID_NONE}
   * @return proxy identifier
   */
  public int addOrUpdateProxy (@NonNull TdApi.Proxy proxy, @Nullable String proxyDescription, boolean setAsCurrent, int existingProxyId) {
    final byte[] data = serializeProxy(proxy);
    final int proxyId;
    if (proxyDescription != null) {
      proxyDescription = proxyDescription.trim();
    }

    final long availableProxyId = getAvailableProxyId();
    int proxySettings = getProxySettings();
    boolean abort = false;

    final LevelDB editor = pmc.edit();
    boolean isNewAdd = false;

    if (existingProxyId != PROXY_ID_NONE) {
      proxyId = existingProxyId;
      editor.putByteArray(KEY_PROXY_PREFIX_CONFIG + proxyId, data);
    } else {
      String existingKey = pmc.findByValue(KEY_PROXY_PREFIX_CONFIG, data);
      if (existingKey != null) {
        proxyId = StringUtils.parseInt(existingKey.substring(KEY_PROXY_PREFIX_CONFIG.length()));
        abort = availableProxyId == proxyId && (proxySettings & PROXY_FLAG_ENABLED) != 0;
      } else {
        proxyId = getInt(KEY_PROXY_LAST_ID, PROXY_ID_NONE) + 1;

        editor.putInt(KEY_PROXY_LAST_ID, proxyId); // incrementing
        editor.putByteArray(KEY_PROXY_PREFIX_CONFIG + proxyId, data);
        editor.removeByPrefix(KEY_PROXY_PREFIX_CONNECTION_TIME + proxyId);
        isNewAdd = true;
      }
    }

    if (!StringUtils.isEmpty(proxyDescription)) {
      editor.putString(KEY_PROXY_PREFIX_DESCRIPTION + proxyId, proxyDescription);
    } else {
      editor.remove(KEY_PROXY_PREFIX_DESCRIPTION + proxyId);
    }

    if (abort) {
      editor.apply();
      return proxyId;
    }

    if (setAsCurrent) {
      if ((proxySettings & PROXY_FLAG_ENABLED) == 0) {
        proxySettings |= PROXY_FLAG_ENABLED;
        editor.putByte(KEY_PROXY_SETTINGS, (byte) proxySettings);
      }
      if (proxyId != availableProxyId) {
        editor.putInt(KEY_PROXY_CURRENT, proxyId);
      }
    } else if (availableProxyId == PROXY_ID_NONE) {
      if ((proxySettings & PROXY_FLAG_ENABLED) != 0) {
        proxySettings &= ~PROXY_FLAG_ENABLED;
        editor.putByte(KEY_PROXY_SETTINGS, (byte) proxySettings);
      }
      editor.putInt(KEY_PROXY_CURRENT, proxyId);
    }

    editor.apply();

    if (isNewAdd) {
      dispatchProxyAdded(new Proxy(proxyId, proxy, proxyDescription), setAsCurrent);
    }
    dispatchProxyConfiguration(proxyId, proxy, proxyDescription, setAsCurrent || (availableProxyId == proxyId && (proxySettings & PROXY_FLAG_ENABLED) != 0), isNewAdd);
    if (availableProxyId == PROXY_ID_NONE) {
      dispatchProxyAvailabilityChanged(true);
    }

    return proxyId;
  }

  /**
   * Removes proxy configuration. Does nothing, if proxy is currently in use.
   *
   * @param proxyId proxy identifier
   * @return true if proxy has been successfully deleted
   */
  public boolean removeProxy (int proxyId) {
    if (proxyId <= PROXY_ID_NONE)
      throw new IllegalArgumentException(Integer.toString(proxyId));

    int availableProxyId = getAvailableProxyId();
    int proxySettings = getProxySettings();

    if (availableProxyId == proxyId && (proxySettings & PROXY_FLAG_ENABLED) != 0) {
      return false;
    }

    pmc.edit();
    pmc.remove(KEY_PROXY_PREFIX_CONFIG + proxyId);
    pmc.removeByPrefix(KEY_PROXY_PREFIX_CONNECTION_TIME + proxyId);
    pmc.apply();

    if (availableProxyId == proxyId) {
      int newProxyId = PROXY_ID_NONE;
      String firstConfigKey = pmc.findFirst(KEY_PROXY_PREFIX_CONFIG);
      if (firstConfigKey != null) {
        int i = firstConfigKey.lastIndexOf('_');
        if (i != -1) {
          newProxyId = StringUtils.parseInt(firstConfigKey.substring(i + 1));
        }
      }
      pmc.putInt(KEY_PROXY_CURRENT, newProxyId);
      if (newProxyId == PROXY_ID_NONE) {
        dispatchProxyAvailabilityChanged(false);
      }
    }

    return true;
  }

  public static final double PROXY_UPDATE_AWAIT_SECONDS = 1.5;
  public static final double PROXY_UPDATE_PERIOD_SECONDS = 60.0;

  /**
   * Trace proxy connection time.
   * Call this method periodically when TDLib connection is established
   * <p>
   * See {@link #PROXY_UPDATE_PERIOD_SECONDS}
   *
   * @param proxyId Proxy identifier. {@link #PROXY_ID_NONE} means connection without proxy.
   * @param time    Last successful connection time. Seconds
   */
  @Deprecated
  public void traceProxyConnected (int proxyId, int accountId, int time) {
    if (proxyId >= PROXY_ID_NONE) {
      pmc.putInt(KEY_PROXY_PREFIX_CONNECTION_TIME + proxyId + "_" + accountId, time);
    }
  }

  /**
   * @param proxyId Proxy identifier. {@link #PROXY_ID_NONE} means connection without proxy.
   * @return Last connection establishment time. 0 means never been connected yet.
   */
  @Deprecated
  public int getProxyConnectionTime (int proxyId, int accountId) {
    if (proxyId < PROXY_ID_NONE)
      throw new IllegalArgumentException(Integer.toString(proxyId));
    return pmc.getInt(KEY_PROXY_PREFIX_CONNECTION_TIME + proxyId + "_" + accountId, 0);
  }

  public static class Proxy implements Comparable<Proxy> {
    private static final int INTERNAL_FLAG_HAS_USERNAME = 1;
    private static final int INTERNAL_FLAG_HAS_PASSWORD = 1 << 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      TYPE_SOCKS5,
      TYPE_MTPROTO,
      TYPE_HTTP
    })
    public @interface Type {
    }

    private static final int TYPE_SOCKS5 = 1;
    private static final int TYPE_MTPROTO = 2;
    private static final int TYPE_HTTP = 3;

    private static final int ORDER_UNSET = -1;

    public final int id;

    public @Nullable TdApi.Proxy proxy;

    public int order = ORDER_UNSET;
    public @Nullable String description;
    public int successfulConnectionsCount;
    public long lastConnectionTime, lastConnectionDuration;
    public long lastPingTime, lastPingResult;

    public int pingCount;
    public long pingMs = PROXY_TIME_UNSET;
    public @Nullable TdApi.Error pingError;
    public int pingErrorCount;
    public int winState;

    public Proxy (int id, @Nullable TdApi.Proxy proxy, @Nullable String description) {
      if (id != PROXY_ID_NONE && proxy == null)
        throw new IllegalArgumentException();
      this.id = id;
      this.proxy = proxy;
      this.description = description;
    }

    public boolean hasPong () {
      return pingMs >= 0;
    }

    public static boolean canUseForCalls (@Nullable TdApi.ProxyType type) {
      if (type != null) {
        switch (type.getConstructor()) {
          case TdApi.ProxyTypeSocks5.CONSTRUCTOR:
            return true;
        }
      }
      return false;
    }

    public boolean canUseForCalls () {
      return proxy != null && canUseForCalls(proxy.type);
    }

    public CharSequence getName () {
      if (proxy == null) {
        return null;
      }
      final String name = StringUtils.isEmpty(description) ? proxy.server + ":" + proxy.port : description;
      int stringRes;
      switch (proxy.type.getConstructor()) {
        case TdApi.ProxyTypeSocks5.CONSTRUCTOR: {
          TdApi.ProxyTypeSocks5 socks5 = (TdApi.ProxyTypeSocks5) proxy.type;
          if (proxy.port == 9050 && StringUtils.isEmpty(socks5.username) && StringUtils.isEmpty(socks5.password) && U.isLocalhost(proxy.server)) {
            stringRes = R.string.ProxyTorNetwork;
          } else {
            stringRes = R.string.ProxySocks5;
          }
          break;
        }
        case TdApi.ProxyTypeMtproto.CONSTRUCTOR:
          stringRes = R.string.ProxyMtproto;
          break;
        case TdApi.ProxyTypeHttp.CONSTRUCTOR:
          stringRes = R.string.ProxyHttp;
          break;
        default:
          Td.assertProxyType_bc1a1076();
          throw Td.unsupported(proxy.type);
      }
      return Lang.getString(stringRes, (target, argStart, argEnd, argIndex, needFakeBold) -> new CustomTypefaceSpan(null, ColorId.textLight), name);
    }

    @Override
    public int compareTo (@NonNull Proxy o) {
      if (order != o.order)
        return Integer.compare(order, o.order);
      else
        return Integer.compare(o.id, id);
    }

    public int defaultOrder () {
      return proxy != null ? Settings.getProxyDefaultOrder(proxy.type) : -1;
    }

    public boolean isDirect () {
      return proxy == null;
    }

    @Override
    public String toString () {
      CharSequence name = getName();
      return name != null ? name.toString() : super.toString();
    }

    public static Proxy noProxy (boolean loadStats) {
      Proxy proxy = new Proxy(Settings.PROXY_ID_NONE, null, null);
      if (loadStats) {
        proxy.successfulConnectionsCount = Settings.instance().getInt(
          KEY_PROXY_PREFIX_CONNECTED_COUNT + Settings.PROXY_ID_NONE, 0
        );
        long[] lastPingResult = Settings.instance().getLongArray(
          KEY_PROXY_PREFIX_LAST_PING + Settings.PROXY_ID_NONE
        );
        if (lastPingResult != null) {
          proxy.lastPingTime = lastPingResult.length > 0 ? lastPingResult[0] : 0;
          proxy.lastPingResult = lastPingResult.length > 1 ? lastPingResult[1] : 0;
        }
        long[] lastConnectionInfo = Settings.instance().getLongArray(
          KEY_PROXY_PREFIX_LAST_CONNECTION + Settings.PROXY_ID_NONE
        );
        if (lastConnectionInfo != null) {
          proxy.lastConnectionTime = lastConnectionInfo.length > 0 ? lastConnectionInfo[0] : 0;
          proxy.lastConnectionDuration = lastConnectionInfo.length > 0 ? lastConnectionInfo[1] : 0;
        }
      }
      return proxy;
    }
  }

  /**
   * Sets order of proxy list
   *
   * @param proxyIds Array of proxy identifiers
   */
  public void setProxyOrder (@Nullable int[] proxyIds) {
    if (proxyIds != null) {
      pmc.putIntArray(KEY_PROXY_ORDER, proxyIds);
    } else {
      pmc.remove(KEY_PROXY_ORDER);
    }
  }

  /**
   * Get list of all available proxies in the descending order (last added first).
   * <p>
   * This operation may take time, if there are too many proxies,
   * maybe it's good idea to invoke this method on background thread.
   *
   * @return list of available proxy configurations
   */
  public @NonNull List<Proxy> getAvailableProxies () {
    // TODO cache
    return loadAvailableProxies();
  }

  private @NonNull List<Proxy> loadAvailableProxies () {
    List<Proxy> proxies = new ArrayList<>();
    Blob blob = null;
    int[] order = pmc.getIntArray(KEY_PROXY_ORDER);
    for (final LevelDB.Entry entry : pmc.find(KEY_PROXY_ITEM_PREFIX)) {
      final String key = entry.key();
      int i = key.lastIndexOf('_');
      if (i == -1) {
        continue;
      }
      String subKey = key.substring(0, i + 1);
      int proxyId = StringUtils.parseInt(key.substring(i + 1));
      if (proxyId < PROXY_ID_NONE) {
        Log.w("Unknown proxy id entry:%d", proxyId);
        continue;
      }
      if (proxyId == PROXY_ID_NONE) {
        continue;
      }
      if (subKey.equals(KEY_PROXY_PREFIX_CONFIG)) {
        // Config itself
        byte[] data = entry.asByteArray();
        if (blob == null)
          blob = new Blob();
        Proxy proxy = readProxy(proxyId, data, blob);
        if (proxy != null) {
          proxy.order = order != null ? ArrayUtils.indexOf(order, proxyId) : Proxy.ORDER_UNSET;
          proxies.add(proxy);
        } else {
          Log.w("Removing proxy configuration, because it cannot be read, proxyId:%d", proxyId);
          removeProxy(proxyId);
        }
      } else if (!proxies.isEmpty()) {
        Proxy lastProxy = proxies.get(proxies.size() - 1);
        if (lastProxy.id == proxyId) {
          try {
            switch (key) {
              case KEY_PROXY_PREFIX_DESCRIPTION: {
                lastProxy.description = entry.asString();
                break;
              }
              case KEY_PROXY_PREFIX_CONNECTED_COUNT: {
                lastProxy.successfulConnectionsCount = entry.asInt();
                break;
              }
              case KEY_PROXY_PREFIX_LAST_CONNECTION: {
                long[] lastConnectionInfo = entry.asLongArray();
                lastProxy.lastConnectionTime = lastConnectionInfo.length > 0 ? lastConnectionInfo[0] : 0;
                lastProxy.lastConnectionDuration = lastConnectionInfo.length > 1 ? lastConnectionInfo[1] : 0;
                break;
              }
              case KEY_PROXY_PREFIX_LAST_PING: {
                long[] lastPingInfo = entry.asLongArray();
                lastProxy.lastPingTime = lastPingInfo.length > 0 ? lastPingInfo[0] : 0;
                lastProxy.lastPingResult = lastPingInfo.length > 1 ? lastPingInfo[1] : 0;
                break;
              }
            }
          } catch (IllegalArgumentException ignored) {
          }
        }
      }
    }
    Collections.sort(proxies);
    return proxies;
  }

  public interface ProxyChangeListener {
    void onProxyConfigurationChanged (int proxyId, @Nullable TdApi.Proxy proxy, @Nullable String description, boolean isCurrent, boolean isNewAdd);

    void onProxyAvailabilityChanged (boolean isAvailable);

    void onProxyAdded (Proxy proxy, boolean isCurrent);
  }

  private final ReferenceList<ProxyChangeListener> proxyListeners = new ReferenceList<>();

  public void addProxyListener (ProxyChangeListener listener) {
    proxyListeners.add(listener);
  }

  public void removeProxyListener (ProxyChangeListener listener) {
    proxyListeners.remove(listener);
  }

  /**
   * Notifies that all TDLib instances must change proxy configuration.
   *
   * @param id        Proxy identifier
   * @param proxy     Proxy details
   * @param isCurrent True when this proxy is applied to TDLib instances
   * @param isNewAdd
   */
  private void dispatchProxyConfiguration (int id, @Nullable TdApi.Proxy proxy, @Nullable String description, boolean isCurrent, boolean isNewAdd) {
    for (ProxyChangeListener listener : proxyListeners) {
      listener.onProxyConfigurationChanged(id, proxy, description, isCurrent, isNewAdd);
    }
  }

  /**
   * Notifies that at least one proxy configuration became available.
   *
   * @param isAvailable true if at least one proxy configuration is available.
   */
  private void dispatchProxyAvailabilityChanged (boolean isAvailable) {
    for (ProxyChangeListener listener : proxyListeners) {
      listener.onProxyAvailabilityChanged(isAvailable);
    }
  }

  /**
   * Notifies that new proxy configuration has been added
   *
   * @param proxy Proxy information
   */
  private void dispatchProxyAdded (Proxy proxy, boolean isCurrent) {
    for (ProxyChangeListener listener : proxyListeners) {
      listener.onProxyAdded(proxy, isCurrent);
    }
  }

  // Earpiece mode

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({EARPIECE_MODE_NEVER, EARPIECE_MODE_PROXIMITY, EARPIECE_MODE_ALWAYS})
  public @interface EarpieceMode {
  }

  public static final int EARPIECE_MODE_NEVER = 0;
  public static final int EARPIECE_MODE_PROXIMITY = 1;
  public static final int EARPIECE_MODE_ALWAYS = 2;

  public interface RaiseToSpeakListener {
    void onEarpieceModeChanged (boolean isVideo, @EarpieceMode int newMode);
  }

  private final ReferenceList<RaiseToSpeakListener> raiseToSpeakListeners = new ReferenceList<>();

  public void addRaiseToSpeakListener (RaiseToSpeakListener listener) {
    raiseToSpeakListeners.add(listener);
  }

  public void removeRaiseToSpeakListener (RaiseToSpeakListener listener) {
    raiseToSpeakListeners.remove(listener);
  }

  public @EarpieceMode int getEarpieceMode (boolean isVideo) {
    @EarpieceMode int earpieceMode;
    if (isVideo) {
      earpieceMode = getInt(KEY_EARPIECE_VIDEO_MODE, EARPIECE_MODE_NEVER);
    } else {
      earpieceMode = getInt(KEY_EARPIECE_MODE, EARPIECE_MODE_PROXIMITY);
    }
    switch (earpieceMode) {
      case EARPIECE_MODE_ALWAYS:
        return isVideo ? EARPIECE_MODE_NEVER : earpieceMode;
      case EARPIECE_MODE_NEVER:
      case EARPIECE_MODE_PROXIMITY:
        return earpieceMode;
    }
    return EARPIECE_MODE_NEVER;
  }

  public void setEarpieceMode (boolean isVideo, @EarpieceMode int earpieceMode) {
    int mode = getEarpieceMode(isVideo);
    if (mode != earpieceMode) {
      putInt(isVideo ? KEY_EARPIECE_VIDEO_MODE : KEY_EARPIECE_MODE, earpieceMode);
      for (RaiseToSpeakListener listener : raiseToSpeakListeners) {
        listener.onEarpieceModeChanged(isVideo, earpieceMode);
      }
    }
  }

  // Passcode brute force

  private static final String KEY_BRUT_FORCE_BLOCK_SECONDS = "brut_force_seconds";
  private static final String KEY_BRUT_FORCE_ERROR_PREFIX = "brut_force_errors";

  private void checkPendingPasscodeLocks () {
    for (LevelDB.Entry entry : pmc.find(KEY_BRUT_FORCE_BLOCK_SECONDS)) {
      startPasscodeBlockTimer(/*entry.asInt(),*/ entry.key().substring(KEY_BRUT_FORCE_BLOCK_SECONDS.length()));
    }
  }

  public void forgetPasscodeErrors (int mode, @Nullable String suffix) {
    String key = suffix != null ? KEY_BRUT_FORCE_ERROR_PREFIX + suffix : "global_" + KEY_BRUT_FORCE_ERROR_PREFIX;
    pmc.removeByPrefix(key);
  }

  @AnyThread
  public boolean isPasscodeBlocked (int mode, @Nullable String suffix) {
    return pmc.contains(suffix != null ? KEY_BRUT_FORCE_BLOCK_SECONDS + suffix : KEY_BRUT_FORCE_BLOCK_SECONDS);
  }

  public int getPasscodeBlockSeconds (String suffix) {
    String key = suffix != null ? KEY_BRUT_FORCE_BLOCK_SECONDS + suffix : KEY_BRUT_FORCE_BLOCK_SECONDS;
    return pmc.getInt(key, 0);
  }

  private void blockPasscode (int mode, int level, @Nullable String suffix) {
    int seconds;
    if (level <= 1) {
      seconds = 30;
    } else {
      seconds = Math.min((int) TimeUnit.MINUTES.toSeconds(5), 30 + 15 * (level - 1));
    }
    String key = suffix != null ? KEY_BRUT_FORCE_BLOCK_SECONDS + suffix : KEY_BRUT_FORCE_BLOCK_SECONDS;
    pmc.putInt(key, seconds);
    startPasscodeBlockTimer(suffix != null ? suffix : "");
  }

  private boolean tickPasscode (@Nullable String suffix) {
    String key = suffix != null ? KEY_BRUT_FORCE_BLOCK_SECONDS + suffix : KEY_BRUT_FORCE_BLOCK_SECONDS;
    int seconds = pmc.getInt(key, 0);
    if (--seconds > 0) {
      pmc.putInt(key, seconds);
      return true;
    } else {
      pmc.remove(key);
      return false;
    }
  }

  public interface PasscodeTickListener {
    void onPasscodeTick (String suffix);
  }

  private ReferenceList<PasscodeTickListener> passcodeTickListeners;

  public void addPasscodeTickLister (PasscodeTickListener listener) {
    if (passcodeTickListeners == null) {
      passcodeTickListeners = new ReferenceList<>();
    }
    passcodeTickListeners.add(listener);
  }

  public void removePasscodeTickListener (PasscodeTickListener listener) {
    if (passcodeTickListeners != null) {
      passcodeTickListeners.remove(listener);
    }
  }

  @UiThread
  private void onPasscodeTick (String suffix) {
    if (passcodeTickListeners != null) {
      for (PasscodeTickListener listener : passcodeTickListeners) {
        listener.onPasscodeTick(suffix);
      }
    }
  }

  private HashMap<String, CancellableRunnable> passcodeTimers;

  private void startPasscodeBlockTimer (final @NonNull String suffix) {
    if (passcodeTimers == null) {
      passcodeTimers = new HashMap<>();
    } else if (passcodeTimers.containsKey(suffix)) {
      return;
    }
    CancellableRunnable actor = new CancellableRunnable() {
      @Override
      public void act () {
        if (tickPasscode(suffix)) {
          Background.instance().post(this, 1000);
        } else {
          passcodeTimers.remove(suffix);
        }
        UI.post(() -> onPasscodeTick(suffix));
      }
    };
    actor.removeOnCancel(UI.getAppHandler());
    passcodeTimers.put(suffix, actor);
    Background.instance().post(actor, 1000);
    UI.post(() -> onPasscodeTick(suffix));
  }

  // BackgroundThread
  public void tracePasscodeError (int mode, @Nullable String error, @Nullable String suffix) {
    int maximumErrorCount;
    switch (mode) {
      case Passcode.MODE_PASSWORD:
        maximumErrorCount = 7;
        break;
      case Passcode.MODE_PINCODE:
        maximumErrorCount = 4;
        break;
      case Passcode.MODE_PATTERN:
      case Passcode.MODE_GESTURE:
      default:
        maximumErrorCount = 5;
        break;
    }
    String key = suffix != null ? KEY_BRUT_FORCE_ERROR_PREFIX + suffix : "global_" + KEY_BRUT_FORCE_ERROR_PREFIX;
    int num = 0;
    String errorHash = error != null ? Passcode.getPasscodeHash(error) : null;
    for (LevelDB.Entry entry : pmc.find(key)) {
      if (errorHash != null && StringUtils.equalsOrBothEmpty(errorHash, entry.asString())) {
        entry.release();
        return;
      }
      num++;
    }
    pmc.putString(key + "_" + num, error != null ? Passcode.getPasscodeHash(error) : "");
    num++;
    if ((num % maximumErrorCount) == 0) {
      blockPasscode(mode, num / maximumErrorCount, suffix);
    }
  }

  // Language pack

  public static class Language {
    public final TdApi.LanguagePackInfo packInfo;
    public final int pluralCode;
    public final Locale locale;

    private Language (TdApi.LanguagePackInfo packInfo) {
      this.packInfo = packInfo;
      this.pluralCode = Lang.makeLanguageCode(packInfo.pluralCode);
      this.locale = new Locale(Lang.normalizeLanguageCode(!StringUtils.isEmpty(packInfo.baseLanguagePackId) ? packInfo.pluralCode : packInfo.id));
    }
  }

  private Language language;

  private void setLanguageImpl (TdApi.LanguagePackInfo languagePack) {
    this.language = new Language(languagePack);
  }

  public int getLanguagePluralCode () {
    return getLanguage().pluralCode;
  }

  public TdApi.LanguagePackInfo getLanguagePackInfo () {
    return getLanguage().packInfo;
  }

  public Language getLanguage () {
    if (language == null)
      setLanguageImpl(readLanguage(pmc, KEY_LANGUAGE_CURRENT, true));
    return language;
  }

  public static TdApi.LanguagePackInfo readLanguage (LevelDB pmc, String saveKey, boolean needDefault) {
    String languagePackId = null;
    String pluralCode = null;
    String baseLanguagePackId = null;
    boolean isRtl = false;
    for (LevelDB.Entry entry : pmc.find(saveKey)) {
      String key = entry.key();
      if (key.length() == saveKey.length()) {
        languagePackId = entry.asString();
      } else {
        switch (key.substring(saveKey.length())) {
          case KEY_LANGUAGE_CODE_SUFFIX_BASE:
            baseLanguagePackId = entry.asString();
            break;
          case KEY_LANGUAGE_CODE_SUFFIX_PLURAL:
            pluralCode = entry.asString();
            break;
          case KEY_LANGUAGE_CODE_SUFFIX_RTL:
            isRtl = entry.asBoolean();
            break;
        }
      }
    }
    if (!StringUtils.isEmpty(languagePackId)) {
      if (StringUtils.isEmpty(baseLanguagePackId) && TD.isLocalLanguagePackId(languagePackId)) {
        baseLanguagePackId = Lang.getBuiltinLanguagePackId();
      }
      if (StringUtils.isEmpty(pluralCode)) {
        pluralCode = Lang.normalizeLanguageCode(baseLanguagePackId != null ? baseLanguagePackId : languagePackId);
      }
      return Lang.newLanguagePackInfo(languagePackId, baseLanguagePackId, pluralCode, isRtl);
    }
    return needDefault ? Lang.getBuiltinLanguage() : null;
  }

  public static void saveLanguage (LevelDB pmc, String saveKey, @Nullable TdApi.LanguagePackInfo languagePackInfo) {
    if (languagePackInfo == null || Lang.isBuiltinLanguage(languagePackInfo.id)) {
      SharedPreferences.Editor editor = pmc.edit();
      editor
        .remove(saveKey)
        .remove(saveKey + KEY_LANGUAGE_CODE_SUFFIX_BASE)
        .remove(saveKey + KEY_LANGUAGE_CODE_SUFFIX_PLURAL)
        .remove(saveKey + KEY_LANGUAGE_CODE_SUFFIX_RTL)
        .apply();
    } else {
      SharedPreferences.Editor editor = pmc.edit();
      editor.putString(saveKey, languagePackInfo.id);

      if (!StringUtils.isEmpty(languagePackInfo.baseLanguagePackId) && !TD.isLocalLanguagePackId(languagePackInfo.id))
        editor.putString(saveKey + KEY_LANGUAGE_CODE_SUFFIX_BASE, languagePackInfo.baseLanguagePackId);
      else
        editor.remove(saveKey + KEY_LANGUAGE_CODE_SUFFIX_BASE);

      if (!StringUtils.isEmpty(languagePackInfo.pluralCode) && !languagePackInfo.pluralCode.equals(languagePackInfo.id))
        editor.putString(saveKey + KEY_LANGUAGE_CODE_SUFFIX_PLURAL, languagePackInfo.pluralCode);
      else
        editor.remove(saveKey + KEY_LANGUAGE_CODE_SUFFIX_PLURAL);

      if (languagePackInfo.isRtl)
        editor.putBoolean(saveKey + KEY_LANGUAGE_CODE_SUFFIX_RTL, true);
      else
        editor.remove(saveKey + KEY_LANGUAGE_CODE_SUFFIX_RTL);

      editor.apply();
    }
  }

  public void setLanguage (@NonNull TdApi.LanguagePackInfo languagePackInfo) {
    saveLanguage(pmc, KEY_LANGUAGE_CURRENT, languagePackInfo);
    setLanguageImpl(languagePackInfo);
  }

  public @Nullable String getLastRecommendedLanguagePackId () {
    return getString(KEY_SUGGESTED_LANGUAGE_CODE, null);
  }

  public void setRecommendedLanguagePackId (String languagePackId) {
    if (StringUtils.isEmpty(languagePackId))
      pmc.remove(KEY_SUGGESTED_LANGUAGE_CODE);
    else
      pmc.putString(KEY_SUGGESTED_LANGUAGE_CODE, languagePackId);
  }

  public TdApi.LanguagePackInfo suggestedLanguagePackInfo (String suggestedLanguagePackId, Tdlib tdlib) {
    if (!StringUtils.isEmpty(suggestedLanguagePackId) && !suggestedLanguagePackId.equals(getLanguagePackInfo().id) && !suggestedLanguagePackId.equals(getLastRecommendedLanguagePackId())) {
      return tdlib.suggestedLanguagePackInfo();
    }
    return null;
  }

  public boolean needRtl (String languageCode, boolean defaultValue) {
    return getBoolean(KEY_PREFIX_RTL + Lang.cleanLanguageCode(languageCode), defaultValue);
  }

  public void setNeedRtl (String languageCode, boolean value) {
    putBoolean(KEY_PREFIX_RTL + Lang.cleanLanguageCode(languageCode), value);
    Lang.checkLanguageSettings();
  }

  // Themes

  private static final String KEY_THEMES_CREATED_COUNT = "settings_theme_count";

  private static final String KEY_THEME_FULL = "theme";
  private static final String KEY_THEME_NAME = "theme_name";
  private static final String KEY_THEME_AUTHOR = "theme_author";
  private static final String KEY_THEME_WALLPAPER = "theme_wallpaper";
  private static final String KEY_THEME_FLAGS = "theme_flags";
  private static final String KEY_THEME_HISTORY = "theme_history";

  private static String themePropertyKey (int customThemeId, @PropertyId int propertyId) {
    return themePropertyKey(customThemeId, Theme.getPropertyName(propertyId));
  }

  private static String themePropertyKey (int customThemeId, String colorName) {
    return KEY_THEME_FULL + customThemeId + "_p_" + colorName;
  }

  private static String themeColorKey (int customThemeId, @ColorId int colorId) {
    return themeColorKey(customThemeId, Theme.getColorName(colorId));
  }

  private static String themeColorKey (int customThemeId, String colorName) {
    return KEY_THEME_FULL + customThemeId + "_c_" + colorName;
  }

  private static String themeColorHistoryKey (int customThemeId, @ColorId int colorId) {
    return KEY_THEME_HISTORY + customThemeId + "_" + Theme.getColorName(colorId);
  }

  private int newThemeId () {
    return getInt(KEY_THEMES_CREATED_COUNT, 0) + 1;
  }

  public int addNewTheme (@NonNull String name, @ThemeId int parentThemeId, int inheritFromCustomThemeId, @Nullable TdlibUi.ImportedTheme theme) {
    final int newThemeId = newThemeId();
    String installationId = null;

    if (theme != null) {
      StringBuilder b = new StringBuilder("theme_installation_");
      theme.theme = new ThemeCustom(ThemeManager.serializeCustomThemeId(newThemeId));
      if (!StringUtils.isEmpty(theme.wallpaper)) {
        theme.theme.setWallpaper(theme.wallpaper);
        b.append('w').append(theme.wallpaper);
      }
      if (!theme.colorsList.isEmpty())
        b.append('c');
      for (TdlibUi.ImportedTheme.Value value : theme.colorsList) {
        theme.theme.setColor(value.id, value.intValue);
        b.append(',');
        b.append(value.name).append(":").append(value.intValue);
      }
      if (!theme.propertiesList.isEmpty())
        b.append('p');
      for (TdlibUi.ImportedTheme.Value value : theme.propertiesList) {
        theme.theme.setProperty(value.id, value.floatValue);
        b.append(',');
        b.append(value.name).append(":").append(U.formatFloat(value.floatValue, true));
      }
      installationId = b.toString();
      int existingCustomThemeId = pmc.getInt(installationId, 0);
      if (hasCustomTheme(existingCustomThemeId)) {
        theme.theme.setId(ThemeManager.serializeCustomThemeId(existingCustomThemeId));

        // pmc.putString(KEY_THEME_NAME + existingCustomThemeId, name); // Replace old theme name?
        return existingCustomThemeId;
      } else if (existingCustomThemeId > 0) {
        pmc.remove(installationId);
      }
    }

    pmc.edit();
    putInt(KEY_THEMES_CREATED_COUNT, newThemeId);
    putString(KEY_THEME_NAME + newThemeId, name);
    boolean hasParentTheme = false;
    if (inheritFromCustomThemeId > 0) {
      String prefix = KEY_THEME_FULL + inheritFromCustomThemeId + "_";
      for (LevelDB.Entry entry : pmc.find(prefix)) {
        try {
          String key = entry.key();
          String newKey = KEY_THEME_FULL + newThemeId + key.substring(prefix.length() - 1);
          char type = key.charAt(prefix.length());
          switch (type) {
            case 'p':
              pmc.putFloat(newKey, entry.asFloat());
              break;
            case 'c':
              pmc.putInt(newKey, entry.asInt());
              break;
            default:
              Log.e("Unknown theme key: %s", key);
              break;
          }
        } catch (Throwable t) {
          Log.e("Error while copying", t);
        }
      }
      int flags = getCustomThemeFlags(inheritFromCustomThemeId);
      if ((flags & THEME_FLAG_INSTALLED) != 0) {
        flags |= THEME_FLAG_COPY;
        pmc.putByte(KEY_THEME_FLAGS + newThemeId, (byte) flags);
        String author = pmc.getString(KEY_THEME_AUTHOR + inheritFromCustomThemeId, null);
        if (!StringUtils.isEmpty(author)) {
          pmc.putString(KEY_THEME_AUTHOR + newThemeId, author);
        }
        String wallpaper = pmc.getString(KEY_THEME_WALLPAPER + inheritFromCustomThemeId, null);
        if (!StringUtils.isEmpty(wallpaper)) {
          pmc.putString(KEY_THEME_WALLPAPER + newThemeId, wallpaper);
        }
      }
    } else if (theme != null) {
      for (TdlibUi.ImportedTheme.Value value : theme.colorsList) {
        putInt(themeColorKey(newThemeId, value.name), value.intValue);
      }
      for (TdlibUi.ImportedTheme.Value value : theme.propertiesList) {
        putFloat(themePropertyKey(newThemeId, value.name), value.floatValue);
        if (value.id == PropertyId.PARENT_THEME)
          hasParentTheme = true;
      }
      if (!StringUtils.isEmpty(theme.author)) {
        pmc.putString(KEY_THEME_AUTHOR + newThemeId, theme.author);
      }
      if (!StringUtils.isEmpty(theme.wallpaper)) {
        pmc.putString(KEY_THEME_WALLPAPER + newThemeId, theme.wallpaper);
      }
      pmc.putByte(KEY_THEME_FLAGS + newThemeId, (byte) THEME_FLAG_INSTALLED);
      if (!StringUtils.isEmpty(installationId)) {
        pmc.putInt(installationId, newThemeId);
      }
    }
    if (!hasParentTheme) {
      putFloat(themePropertyKey(newThemeId, PropertyId.PARENT_THEME), parentThemeId);
    }
    pmc.apply();
    return newThemeId;
  }

  public int installTheme (@NonNull TdlibUi.ImportedTheme theme) {
    try {
      return addNewTheme(theme.name, theme.parentThemeId, 0, theme);
    } catch (Throwable t) {
      Log.e("Cannot install theme", t);
      return 0;
    }
  }

  public void removeCustomTheme (int customThemeId) {
    List<String> installationIds = null;
    if ((getCustomThemeFlags(customThemeId) & THEME_FLAG_INSTALLED) != 0) {
      for (LevelDB.Entry entry : pmc.find("theme_installation_")) {
        if (entry.asInt() == customThemeId) {
          if (installationIds == null)
            installationIds = new ArrayList<>();
          installationIds.add(entry.key());
        }
      }
    }
    pmc.edit();
    pmc.remove(KEY_THEME_NAME + customThemeId);
    pmc.remove(KEY_THEME_AUTHOR + customThemeId);
    pmc.remove(KEY_THEME_WALLPAPER + customThemeId);
    pmc.remove(KEY_THEME_FLAGS + customThemeId);
    pmc.removeByAnyPrefix(
      KEY_THEME_FULL + customThemeId + "_",
      KEY_THEME_HISTORY + customThemeId + "_"
    );
    if (installationIds != null) {
      for (String installationId : installationIds) {
        pmc.remove(installationId);
      }
    }
    pmc.apply();
  }

  public float getThemeProperty (int customThemeId, @PropertyId int propertyId, float defValue) {
    return pmc.getFloat(themePropertyKey(customThemeId, propertyId), defValue);
  }

  private ThemeInfo processThemeEntry (LevelDB.Entry entry, @Nullable ThemeInfo theme) {
    String key = entry.key();
    final int customThemeId = Integer.parseInt(key.substring(KEY_THEME_NAME.length()));
    if (customThemeId <= 0) {
      return theme;
    }
    int themeId = ThemeManager.serializeCustomThemeId(customThemeId);
    if (theme == null || theme.getId() != themeId) {
      int parentThemeId = (int) getThemeProperty(customThemeId, PropertyId.PARENT_THEME, ThemeId.BLUE);
      theme = new ThemeInfo(themeId, entry.asString(), getCustomThemeWallpaper(customThemeId), parentThemeId, getCustomThemeFlags(customThemeId));
    }
    return theme;
  }

  private static void processThemeEntry (LevelDB.Entry entry, final int startIndex, final @NonNull ThemeCustom theme, final Map<String, Integer> colorsMap, final Map<String, Integer> propsMap) {
    String key = entry.key();
    char type = key.charAt(startIndex);
    String name = key.substring(startIndex + 2);
    switch (type) {
      case 'c': {
        Integer id = colorsMap.get(name);
        if (id != null) {
          theme.setColor(id, entry.asInt());
        } else {
          Log.w("Unknown theme color: %s", name);
        }
        break;
      }
      case 'p': {
        Integer id = propsMap.get(name);
        if (id != null) {
          theme.setProperty(id, entry.asFloat());
        } else {
          Log.w("Unknown theme property: %s", name);
        }
        break;
      }
      default:
        Log.w("Unknown theme key: %s", key);
        break;
    }
  }

  private static void processThemeEntry (LevelDB.Entry entry, final int startIndex, final @NonNull ThemeExportInfo theme, Map<String, Integer> colors, Map<String, Integer> properties) {
    String key = entry.key();
    char type = key.charAt(startIndex);
    String name = key.substring(startIndex + 2);
    switch (type) {
      case 'c': {
        theme.addColor(name, entry.asInt());
        if (colors != null)
          colors.remove(name);
        break;
      }
      case 'p': {
        float value = entry.asFloat();
        theme.addProperty(name, value);
        if (theme.parentThemeId == ThemeId.NONE && ThemeProperties.getName(PropertyId.PARENT_THEME).equals(name)) {
          theme.parentThemeId = (int) value;
        }
        if (properties != null)
          properties.remove(name);
        break;
      }
      default:
        Log.w("Unknown theme key: %s", key);
        break;
    }
  }

  public boolean hasCustomTheme (int customThemeId) {
    return customThemeId > 0 && pmc.contains(themePropertyKey(customThemeId, PropertyId.PARENT_THEME));
  }

  public static class ThemeExportInfo {
    public final String name, wallpaper;
    public final Map<Integer, List<String>> colors = new HashMap<>();
    public final Map<Float, List<String>> properties = new HashMap<>();
    public int parentThemeId = ThemeId.NONE;

    public ThemeExportInfo (String name, String wallpaper) {
      this.name = name;
      this.wallpaper = wallpaper;
    }

    public void addColor (String name, int valueRaw) {
      // String value = Strings.getHexColor(valueRaw, true);
      Integer value = valueRaw;
      List<String> list = colors.get(value);
      if (list == null) {
        list = new ArrayList<>();
        colors.put(value, list);
      }
      list.add(name);
    }

    public void addProperty (String name, float valueRaw) {
      // String value = U.formatFloat(valueRaw, true);
      Float value = valueRaw;
      List<String> list = properties.get(value);
      if (list == null) {
        list = new ArrayList<>();
        properties.put(value, list);
      }
      list.add(name);
    }
  }

  @Nullable
  public ThemeCustom loadCustomTheme (int customThemeId) {
    String prefix = KEY_THEME_FULL + customThemeId + "_";
    int themeId = ThemeManager.serializeCustomThemeId(customThemeId);
    ThemeCustom theme = new ThemeCustom(themeId);
    int startIndex = prefix.length(), entryCount = 0;
    Map<String, Integer> colorsMap = ThemeColors.getMap();
    Map<String, Integer> propsMap = ThemeProperties.getMap();
    for (final LevelDB.Entry entry : pmc.find(prefix)) {
      try {
        processThemeEntry(entry, startIndex, theme, colorsMap, propsMap);
        entryCount++;
      } catch (Throwable t) {
        Log.e("Cannot parse theme entry, key: %s", t, entry.key());
      }
    }
    if (entryCount > 0) {
      theme.setWallpaper(getCustomThemeWallpaper(customThemeId));
      return theme;
    }
    return null;
  }

  public ThemeExportInfo exportTheme (int themeId, boolean needDefault) {
    final int _customThemeId = ThemeManager.resolveCustomThemeId(themeId);
    final ThemeExportInfo theme;

    Map<String, Integer> colors, properties;
    if (needDefault || _customThemeId == ThemeId.NONE) {
      colors = ThemeColors.getMap();
      properties = ThemeProperties.getMap();
    } else {
      colors = properties = null;
    }

    if (_customThemeId != ThemeId.NONE) {
      String prefix = KEY_THEME_FULL + _customThemeId + "_";
      theme = new ThemeExportInfo(getCustomThemeName(_customThemeId), getCustomThemeWallpaper(_customThemeId));
      int startIndex = prefix.length();
      for (final LevelDB.Entry entry : pmc.find(prefix)) {
        try {
          processThemeEntry(entry, startIndex, theme, colors, properties);
        } catch (Throwable t) {
          Log.e("Cannot parse theme entry, key: %s", t, entry.key());
        }
      }
    } else {
      theme = new ThemeExportInfo(Lang.getString(ThemeManager.getBuiltinThemeName(themeId)), null);
      ThemeDelegate currentTheme = ThemeSet.getBuiltinTheme(themeId);
      theme.parentThemeId = (int) currentTheme.getProperty(PropertyId.PARENT_THEME);
      if (theme.parentThemeId != ThemeId.NONE) {
        ThemeDelegate parentTheme = ThemeSet.getBuiltinTheme(theme.parentThemeId);
        for (Map.Entry<String, Integer> entry : colors.entrySet()) {
          int colorId = entry.getValue();
          if (parentTheme.getColor(colorId) != currentTheme.getColor(colorId)) {
            theme.addColor(entry.getKey(), currentTheme.getColor(colorId));
          }
        }

        for (Map.Entry<String, Integer> entry : properties.entrySet()) {
          int propertyId = entry.getValue();
          if (entry.getValue() == PropertyId.WALLPAPER_ID && currentTheme.getProperty(propertyId) == TGBackground.getDefaultWallpaperId(themeId))
            continue;
          if (parentTheme.getProperty(propertyId) != currentTheme.getProperty(propertyId)) {
            theme.addProperty(entry.getKey(), currentTheme.getProperty(propertyId));
          }
        }
      }
    }
    int parentThemeId = theme.parentThemeId != ThemeId.NONE ? theme.parentThemeId : !ThemeManager.isCustomTheme(themeId) ? themeId : ThemeId.NONE;
    if (needDefault && parentThemeId != ThemeId.NONE) {
      for (Map.Entry<String, Integer> entry : colors.entrySet()) {
        theme.addColor(entry.getKey(), Theme.getColor(entry.getValue(), parentThemeId));
      }
      for (Map.Entry<String, Integer> entry : properties.entrySet()) {
        float value = Theme.getProperty(entry.getValue(), parentThemeId);
        if (!ThemeManager.isCustomTheme(themeId) && entry.getValue() == PropertyId.WALLPAPER_ID && value == TGBackground.getDefaultWallpaperId(themeId)) {
          continue;
        }
        theme.addProperty(entry.getKey(), value);
      }
    }
    return theme;
  }

  public @NonNull List<ThemeInfo> getCustomThemes () {
    List<ThemeInfo> themes = new ArrayList<>();
    ThemeInfo theme = null;
    for (final LevelDB.Entry entry : pmc.find(KEY_THEME_NAME)) {
      try {
        ThemeInfo currentTheme = processThemeEntry(entry, theme);
        if (theme != currentTheme) {
          themes.add(theme = currentTheme);
        }
      } catch (Throwable t) {
        Log.e("Cannot parse theme entry, key: %s", t, entry.key());
      }
    }
    return themes;
  }

  public void setCustomThemeColor (int customThemeId, @ColorId int colorId, @Nullable Integer newColor) {
    if (newColor == null)
      pmc.remove(themeColorKey(customThemeId, colorId));
    else
      pmc.putInt(themeColorKey(customThemeId, colorId), newColor);
  }

  public void setCustomThemeProperty (int customThemeId, @PropertyId int propertyId, @Nullable Float newValue) {
    if (newValue == null)
      pmc.remove(themePropertyKey(customThemeId, propertyId));
    else
      pmc.putFloat(themePropertyKey(customThemeId, propertyId), newValue);
  }

  public int getCustomThemeColor (int customThemeId, @ColorId int colorId) {
    try {
      return pmc.tryGetInt(themeColorKey(customThemeId, colorId));
    } catch (FileNotFoundException e) {
      return ThemeSet.getColor((int) getCustomThemeProperty(customThemeId, PropertyId.PARENT_THEME), colorId);
    }
  }

  public float getCustomThemeProperty (int customThemeId, @PropertyId int propertyId) {
    try {
      return pmc.tryGetFloat(themePropertyKey(customThemeId, propertyId));
    } catch (FileNotFoundException e) {
      if (propertyId == PropertyId.PARENT_THEME)
        return ThemeId.BLUE;
      return ThemeSet.getProperty((int) getCustomThemeProperty(customThemeId, PropertyId.PARENT_THEME), propertyId);
    }
  }

  public void setCustomThemeName (int customThemeId, String name) {
    pmc.putString(KEY_THEME_NAME + customThemeId, name);
  }

  public void setCustomThemeWallpaper (int customThemeId, String name) {
    if (StringUtils.isEmpty(name)) {
      pmc.remove(KEY_THEME_WALLPAPER + customThemeId);
    } else {
      pmc.putString(KEY_THEME_WALLPAPER + customThemeId, name);
    }
  }

  private int colorFormat = -1;

  public static final int COLOR_FORMAT_HEX = 0;
  public static final int COLOR_FORMAT_RGB = 1;
  public static final int COLOR_FORMAT_HSL = 2;

  public int getColorFormat () {
    if (colorFormat == -1) {
      colorFormat = pmc.getByte(KEY_COLOR_FORMAT, (byte) COLOR_FORMAT_HEX);
      if (colorFormat < COLOR_FORMAT_HEX || colorFormat > COLOR_FORMAT_HSL)
        colorFormat = COLOR_FORMAT_HEX;
    }
    return colorFormat;
  }

  public boolean setColorFormat (int colorFormat) {
    if (getColorFormat() != colorFormat) {
      if (colorFormat == COLOR_FORMAT_HEX)
        pmc.remove(KEY_COLOR_FORMAT);
      else
        pmc.putByte(KEY_COLOR_FORMAT, (byte) colorFormat);
      this.colorFormat = colorFormat;
      return true;
    }
    return false;
  }

  public int[] getColorHistory (int customThemeId, int colorId) {
    return pmc.getIntArray(themeColorHistoryKey(customThemeId, colorId));
  }

  public boolean hasColorHistory (int customThemeId, int colorId) {
    return pmc.contains(themeColorHistoryKey(customThemeId, colorId));
  }

  public void setColorHistory (int customThemeId, int colorId, int[] newHistory) {
    String key = themeColorHistoryKey(customThemeId, colorId);
    if (newHistory == null || newHistory.length == 0) {
      pmc.remove(key);
    } else {
      pmc.putIntArray(key, newHistory);
    }
  }

  public static final int THEME_FLAG_INSTALLED = 1;
  public static final int THEME_FLAG_COPY = 1 << 1;

  public int getCustomThemeFlags (int customThemeId) {
    return pmc.getByte(KEY_THEME_FLAGS + customThemeId, (byte) 0);
  }

  public boolean hasThemeOwnership (int customThemeId) {
    int flags = getCustomThemeFlags(customThemeId);
    return (flags & THEME_FLAG_INSTALLED) == 0 || (flags & THEME_FLAG_COPY) != 0;
  }

  public String getThemeAuthor (int customThemeId) {
    return pmc.getString(KEY_THEME_AUTHOR + customThemeId, null);
  }

  public String getCustomThemeName (int customThemeId) {
    return pmc.getString(KEY_THEME_NAME + customThemeId, null);
  }

  public String getCustomThemeWallpaper (int customThemeId) {
    return pmc.getString(KEY_THEME_WALLPAPER + customThemeId, null);
  }

  public boolean canEditAuthor (int customThemeId) {
    return (getCustomThemeFlags(customThemeId) & THEME_FLAG_INSTALLED) == 0;
  }

  public int getMinimizedThemeLocation () {
    return getInt(KEY_THEME_POSITION, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
  }

  public void setMinimizedThemeLocation (int gravity) {
    int defaultValue = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
    if (gravity == defaultValue) {
      remove(KEY_THEME_POSITION);
    } else {
      putInt(KEY_THEME_POSITION, gravity);
    }
  }

  public boolean needSplitNotificationCategories () {
    return checkSetting(FLAG_OTHER_SPLIT_CHAT_NOTIFICATIONS);
  }

  public boolean setNeedSplitNotificationCategories (boolean split) {
    return setSetting(FLAG_OTHER_SPLIT_CHAT_NOTIFICATIONS, split);
  }

  public boolean needHideSecretChats () {
    return checkSetting(FLAG_OTHER_HIDE_SECRET_CHATS);
  }

  public boolean setNeedHideSecretChats (boolean need) {
    return setSetting(FLAG_OTHER_HIDE_SECRET_CHATS, need);
  }

  public boolean needAdditionalSync () {
    return checkNegativeSetting(FLAG_OTHER_DISABLE_ADDITIONAL_SYNC);
  }

  // Notification

  public long getProcessedPushCount () {
    return getLong("notifications_count", 0);
  }

  public void setProcessedPushCount (long count) {
    putLong("notifications_count", count);
  }

  private Long _lastPushId;

  public synchronized long newPushId () {
    if (_lastPushId == null) {
      _lastPushId = getLong("notifications_count", 0);
    }
    long pushId = _lastPushId = _lastPushId + 1;
    putLong("notifications_count", pushId);
    return pushId;
  }

  public void putNotificationReceiverId (long receiverId, int accountId) {
    putInt("receiver_" + receiverId, accountId);
  }

  public int findAccountByReceiverId (long receiverId) {
    return getInt("receiver_" + receiverId, TdlibAccount.NO_ID);
  }

  public boolean needNotificationAppVersionUpdate (int accountId) {
    String key = accountId + "_notifications_version";
    if (getInt(key, 0) != BuildConfig.VERSION_CODE) {
      putInt(key, BuildConfig.VERSION_CODE);
      return true;
    }
    return false;
  }

  // Testing Utilities

  private static final int UTILITY_FEATURE_HIDE_NUMBER = 1;
  // private static final int UTILITY_FEATURE_ENCRYPTED_PUSHES = 1 << 1;
  private static final int UTILITY_FEATURE_FORCE_TCP_IN_CALLS = 1 << 2;
  private static final int UTILITY_FEATURE_INSTANT_TDLIB_RESTART = 1 << 3;
  private static final int UTILITY_FEATURE_NO_NETWORK = 1 << 4;
  private static final int UTILITY_FEATURE_TABS = 1 << 5;
  private static final int UTILITY_FEATURE_NO_QR_PROCESS = 1 << 6;
  private static final int UTILITY_FEATURE_QR_ZXING = 1 << 7;
  private static final int UTILITY_FEATURE_QR_REGION_DEBUG = 1 << 8;

  private int getUtilityFeatures () {
    return pmc.getInt(KEY_UTILITY_FEATURES, 0);
  }

  private void setUtilityFeatures (int features) {
    if (features == 0)
      pmc.remove(KEY_UTILITY_FEATURES);
    else
      pmc.putInt(KEY_UTILITY_FEATURES, features);
  }

  private void toggleUtilityFeature (int feature, boolean enabled) {
    int features = getUtilityFeatures();
    int newFeatures = BitwiseUtils.setFlag(features, feature, enabled);
    if (features != newFeatures) {
      setUtilityFeatures(newFeatures);
    }
  }

  public boolean checkUtilityFeature (int feature) {
    return BitwiseUtils.hasFlag(getUtilityFeatures(), feature);
  }

  public boolean forceDisableNetwork () {
    return checkUtilityFeature(UTILITY_FEATURE_NO_NETWORK);
  }

  public void setDisableNetwork (boolean disable) {
    toggleUtilityFeature(UTILITY_FEATURE_NO_NETWORK, disable);
  }

  public boolean needHidePhoneNumber () {
    return checkUtilityFeature(UTILITY_FEATURE_HIDE_NUMBER);
  }

  public void setHidePhoneNumber (boolean enabled) {
    toggleUtilityFeature(UTILITY_FEATURE_HIDE_NUMBER, enabled);
  }

  public boolean needDisableQrProcessing () {
    return checkUtilityFeature(UTILITY_FEATURE_NO_QR_PROCESS);
  }

  public void setDisableQrProcessing (boolean enabled) {
    toggleUtilityFeature(UTILITY_FEATURE_NO_QR_PROCESS, enabled);
  }

  public boolean needShowQrRegions () {
    return checkUtilityFeature(UTILITY_FEATURE_QR_REGION_DEBUG);
  }

  public void setShowQrRegions (boolean enabled) {
    toggleUtilityFeature(UTILITY_FEATURE_QR_REGION_DEBUG, enabled);
  }

  public boolean needForceZxingQrProcessing () {
    return checkUtilityFeature(UTILITY_FEATURE_QR_ZXING);
  }

  public void setForceZxingQrProcessing (boolean enabled) {
    toggleUtilityFeature(UTILITY_FEATURE_QR_ZXING, enabled);
  }

  public void setForceTcpInCalls (boolean enabled) {
    toggleUtilityFeature(UTILITY_FEATURE_FORCE_TCP_IN_CALLS, enabled);
  }

  public boolean forceTcpInCalls () {
    return checkUtilityFeature(UTILITY_FEATURE_FORCE_TCP_IN_CALLS);
  }

  public void setForceTdlibRestart (boolean enabled) {
    toggleUtilityFeature(UTILITY_FEATURE_INSTANT_TDLIB_RESTART, enabled);
  }

  public boolean forceTdlibRestart () {
    return checkUtilityFeature(UTILITY_FEATURE_INSTANT_TDLIB_RESTART);
  }

  /*public boolean needEncryptedPush () {
    return checkUtilityFeature(UTILITY_FEATURE_ENCRYPTED_PUSHES);
  }

  public void setNeedEncryptedPush (boolean enabled) {
    toggleUtilityFeature(UTILITY_FEATURE_ENCRYPTED_PUSHES, enabled);
  }*/

  public boolean isEmulator () {
    return pmc.getBoolean(KEY_IS_EMULATOR, false);
  }

  public static class EmulatorDetectionResult {
    public final long time, installationId, elapsed, result;

    public EmulatorDetectionResult (long time, long installationId, long elapsed, long result) {
      this.time = time;
      this.installationId = installationId;
      this.elapsed = elapsed;
      this.result = result;
    }

    public boolean isEmulatorDetected () {
      return result != 0;
    }

    @SuppressWarnings("all")
    public boolean mayBeFalsePositive () {
      int testId = BitwiseUtils.splitLongToSecondInt(result);
      switch (BuildConfig.FLAVOR) {
        case "x86":
        case "x64":
          return false;
        case "arm64":
        case "arm32":
        default:
          return testId == 2;
      }
    }

    public String toHumanReadableFormat () {
      return "0x" + Long.toString(result, 16);
    }

    public long[] toLongArray () {
      return new long[] {
        time,
        installationId,
        elapsed,
        result
      };
    }

    public static EmulatorDetectionResult restore (long[] array) {
      if (array == null || array.length != 4) {
        return null;
      }
      return new EmulatorDetectionResult(
        array[0],
        array[1],
        array[2],
        array[3]
      );
    }
  }

  @Nullable
  public EmulatorDetectionResult getLastEmulatorDetectionResult () {
    long[] emulatorDetectionResult = pmc.getLongArray(KEY_EMULATOR_DETECTION_RESULT);
    if (emulatorDetectionResult == null) {
      return null;
    }
    return EmulatorDetectionResult.restore(emulatorDetectionResult);
  }

  @NonNull
  public EmulatorDetectionResult trackEmulatorDetectionResult (long installationId, long elapsed, long emulatorCheckResult) {
    EmulatorDetectionResult result = new EmulatorDetectionResult(
      System.currentTimeMillis(),
      installationId,
      elapsed,
      emulatorCheckResult
    );
    long[] data = result.toLongArray();
    pmc.putLongArray(KEY_EMULATOR_DETECTION_RESULT, data);
    boolean wasEmulator = isEmulator();
    if (wasEmulator != result.isEmulatorDetected()) {
      if (result.isEmulatorDetected()) {
        putBoolean(KEY_IS_EMULATOR, true);
      } else {
        pmc.remove(KEY_IS_EMULATOR);
      }
    }
    return result;
  }

  private List<String> authenticationTokens;

  public void trackAuthenticationToken (String token) {
    List<String> tokens = getAuthenticationTokensList();
    if (!tokens.contains(token)) {
      tokens.add(token);
      while (tokens.size() > 20) {
        tokens.remove(0);
      }
      pmc.putStringArray(KEY_TDLIB_AUTHENTICATION_TOKENS, tokens.toArray(new String[0]));
    }
  }

  public List<String> getAuthenticationTokensList () {
    if (authenticationTokens == null) {
      authenticationTokens = new ArrayList<>();
      String[] tokens = pmc.getStringArray(KEY_TDLIB_AUTHENTICATION_TOKENS);
      if (tokens != null) {
        Collections.addAll(authenticationTokens, tokens);
      }
    }
    return authenticationTokens;
  }

  public String[] getAuthenticationTokens () {
    return getAuthenticationTokensList().toArray(new String[0]);
  }

  // Tdlib crash

  private long getLastCrashId () {
    return pmc.getLong(KEY_TDLIB_CRASH_PREFIX, 0) - 1;
  }

  public Crash findRecoveryCrash () {
    long lastCrashId = getLastCrashId();
    return getCrash(lastCrashId, true);
  }

  public void storeTestCrash (Crash.Builder builder) {
    AppState.resetUptime();
    storeCrash(builder);
  }

  private static String makeCrashPrefix (long crashId) {
    return KEY_TDLIB_CRASH_PREFIX + crashId + "_";
  }

  public void storeCrash (Crash.Builder crashBuilder) {
    final long crashId = pmc.getLong(KEY_TDLIB_CRASH_PREFIX, 0);
    final Crash crash = crashBuilder
      .id(crashId)
      .uptime(AppState.uptime())
      .appBuildInfo(Settings.instance().getCurrentBuildInformation())
      .build();

    final String keyPrefix = makeCrashPrefix(crashId);

    pmc.edit();
    // increment crashId
    pmc.putLong(KEY_TDLIB_CRASH_PREFIX, crashId + 1);
    // save crash
    crash.saveTo(pmc, keyPrefix);
    // apply & flush
    pmc.apply();
    pmc.flush();
  }

  public boolean setCrashFlag (Crash info, int flag, boolean enabled) {
    if (info.setFlag(flag, enabled)) {
      info.saveFlags(pmc, makeCrashPrefix(info.id));
      return true;
    }
    return false;
  }

  public void markCrashAsResolved (Crash info) {
    if (setCrashFlag(info, Crash.Flags.RESOLVED, true)) {
      AppState.resetUptime();
    }
  }

  public void markCrashAsSaved (Crash info) {
    setCrashFlag(info, Crash.Flags.APPLICATION_LOG_EVENT_SAVED, true);
  }

  public @Nullable List<Crash> getCrashesToSave () {
    final long lastCrashId = getLastCrashId();
    if (lastCrashId < 0)
      return null;

    final long currentInstallationId = getCurrentBuildInformation().getInstallationId();
    List<Crash> result = null;

    AppBuildInfo crashedBuildInfo = null;

    for (long crashId = lastCrashId; crashId >= 0; crashId--) {
      final String keyPrefix = makeCrashPrefix(crashId);
      @Crash.Flags int flags = Crash.restoreFlags(pmc, keyPrefix);
      final long crashedInstallationId = Crash.restoreInstallationId(pmc, keyPrefix);
      if (crashedInstallationId != currentInstallationId) {
        if (crashedBuildInfo == null || crashedInstallationId != crashedBuildInfo.getInstallationId()) {
          crashedBuildInfo = getBuildInformation(crashedInstallationId);
        }
        // Forget about crashes from previously installed versions, except if it's the same TDLib commit
        String crashedTdlibCommit = crashedBuildInfo != null ? crashedBuildInfo.getTdlibCommitFull() : null;
        if (StringUtils.isEmpty(crashedTdlibCommit) ||
          !crashedTdlibCommit.equalsIgnoreCase(getCurrentBuildInformation().getTdlibCommitFull()) ||
          !BitwiseUtils.hasFlag(flags, Crash.Flags.SOURCE_TDLIB | Crash.Flags.SOURCE_TDLIB_PARAMETERS)) {
          break;
        }
      }
      if (!BitwiseUtils.hasFlag(flags, Crash.Flags.SAVE_APPLICATION_LOG_EVENT)) {
        continue;
      }
      if (BitwiseUtils.hasFlag(flags, Crash.Flags.APPLICATION_LOG_EVENT_SAVED)) {
        break;
      }
      Crash crash = getCrash(crashId, false);
      if (crash != null) {
        if (result == null) {
          result = new ArrayList<>();
        }
        result.add(crash);
      }
    }

    if (result != null) {
      // Sort crashes from older to newer
      Collections.reverse(result);
    }

    return result;
  }

  public Crash getCrash (long crashId, boolean forApplicationStart) {
    if (crashId < 0)
      return null;
    final String keyPrefix = makeCrashPrefix(crashId);
    if (forApplicationStart) {
      @Crash.Flags int flags = Crash.restoreFlags(pmc, keyPrefix);
      if (BitwiseUtils.hasFlag(flags, Crash.Flags.RESOLVED)) {
        // Do not attempt to read any other fields, if user pressed "Launch App"
        return null;
      }
      final long crashedInstallationId = Crash.restoreInstallationId(pmc, keyPrefix);
      final AppBuildInfo currentBuildInformation = getCurrentBuildInformation();
      if (crashedInstallationId != 0 && currentBuildInformation.getInstallationId() > crashedInstallationId) {
        // User has installed a newer version. Ignore crashes from previous APKs
        return null;
      }
    }
    Crash.Builder builder = new Crash.Builder().id(crashId);
    boolean nonEmpty = false;
    for (LevelDB.Entry entry : pmc.find(keyPrefix)) {
      if (builder.restoreField(entry, keyPrefix, this::getBuildInformation)) {
        nonEmpty = true;
      }
    }
    if (nonEmpty) {
      Crash crash = builder.build();
      if (crash.getType() != Crash.Type.UNKNOWN && (!forApplicationStart || crash.shouldShowAtApplicationStart())) {
        return crash;
      }
    }
    return null;
  }

  // Sync

  public long getPeriodicSyncFrequencySeconds () {
    // TODO
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? TimeUnit.MINUTES.toSeconds(30) : TimeUnit.HOURS.toSeconds(1);
  }

  // Push token

  public static void storeDeviceToken (@NonNull TdApi.DeviceToken deviceToken, SharedPreferences.Editor editor, final String keyTokenType, final String keyTokenOrEndpoint) {
    @DeviceTokenType int tokenType = TdlibNotificationUtils.getDeviceTokenType(deviceToken);
    final String tokenOrEndpoint;
    switch (tokenType) {
      case DeviceTokenType.FIREBASE_CLOUD_MESSAGING:
        tokenOrEndpoint = ((TdApi.DeviceTokenFirebaseCloudMessaging) deviceToken).token;
        break;
      case DeviceTokenType.HUAWEI_PUSH_SERVICE:
        tokenOrEndpoint = ((TdApi.DeviceTokenHuaweiPush) deviceToken).token;
        break;
      case DeviceTokenType.SIMPLE_PUSH_SERVICE:
        tokenOrEndpoint = ((TdApi.DeviceTokenSimplePush) deviceToken).endpoint;
        break;
      default:
        Td.assertDeviceToken_de4a4f61();
        throw Td.unsupported(deviceToken);
    }
    editor
      .putInt(keyTokenType, tokenType)
      .putString(keyTokenOrEndpoint, tokenOrEndpoint);
  }

  public void setDeviceToken (TdApi.DeviceToken token) {
    if (token == null) {
      pmc.edit()
        .remove(KEY_PUSH_DEVICE_TOKEN_TYPE)
        .remove(KEY_PUSH_DEVICE_TOKEN_OR_ENDPOINT)
        .apply();
    } else if (!Td.equalsTo(token, getDeviceToken())) {
      resetTokenPushMessageCount();
      SharedPreferences.Editor editor = pmc.edit();
      Settings.storeDeviceToken(token, editor,
        KEY_PUSH_DEVICE_TOKEN_TYPE,
        KEY_PUSH_DEVICE_TOKEN_OR_ENDPOINT
      );
      editor.apply();
    }
  }

  public static TdApi.DeviceToken newDeviceToken (@DeviceTokenType int tokenType, @Nullable String tokenOrEndpoint) {
    if (StringUtils.isEmpty(tokenOrEndpoint)) {
      return null;
    }
    switch (tokenType) {
      case DeviceTokenType.FIREBASE_CLOUD_MESSAGING:
        return new TdApi.DeviceTokenFirebaseCloudMessaging(tokenOrEndpoint, true);
      case DeviceTokenType.SIMPLE_PUSH_SERVICE:
        return new TdApi.DeviceTokenSimplePush(tokenOrEndpoint);
      case DeviceTokenType.HUAWEI_PUSH_SERVICE:
        return new TdApi.DeviceTokenHuaweiPush(tokenOrEndpoint, true);
    }
    return null;
  }

  @Nullable
  public TdApi.DeviceToken getDeviceToken () {
    @DeviceTokenType int tokenType = pmc.getInt(KEY_PUSH_DEVICE_TOKEN_TYPE, DeviceTokenType.FIREBASE_CLOUD_MESSAGING);
    String tokenOrEndpoint = pmc.getString(KEY_PUSH_DEVICE_TOKEN_OR_ENDPOINT, null);
    return newDeviceToken(tokenType, tokenOrEndpoint);
  }

  // Device ID used to anonymously identify crashes from the same client

  private String crashDeviceId;

  public String crashDeviceId () {
    if (crashDeviceId == null) {
      crashDeviceId = pmc.getString(KEY_CRASH_DEVICE_ID, null);
    }
    if (StringUtils.isEmpty(crashDeviceId)) {
      crashDeviceId = U.sha256(
        U.getUsefulMetadata(null) + "\n" +
          StringUtils.random("abcdefABCDEF0123456789", 16) + "\n" +
          (long) ((double) Long.MAX_VALUE * Math.random())
      );
      pmc.putString(KEY_CRASH_DEVICE_ID, crashDeviceId);
    }
    return crashDeviceId;
  }

  // Interface

  public abstract static class CloudSetting implements Comparable<CloudSetting> {
    public final String identifier;
    public int version;
    public int date;

    public String displayName;
    public int position = -1;
    public TdApi.File file;

    @Nullable
    public ImageFile previewFile;

    public CloudSetting (String identifier, int version, String displayName, int position) {
      this.identifier = identifier;
      this.version = version;
      this.displayName = displayName;
      this.position = position;
    }

    public CloudSetting (String identifier) {
      this.identifier = U.getSecureFileName(identifier);
    }

    public CloudSetting (TdApi.Message message, String requiredHashtag, int builtInStringRes) {
      TdApi.MessageDocument document = (TdApi.MessageDocument) message.content;
      this.identifier = U.getSecureFileName(document.document.fileName);
      this.date = Math.max(message.date, message.editDate);
      boolean isValidSetting = false, hideName = false;
      if (document.caption != null && document.caption.entities != null && document.caption.entities.length > 0) {
        for (TdApi.TextEntity entity : document.caption.entities) {
          //noinspection SwitchIntDef
          switch (entity.type.getConstructor()) {
            case TdApi.TextEntityTypeHashtag.CONSTRUCTOR: {
              String hashtag = Td.substring(document.caption.text, entity);
              if (hashtag.equals(requiredHashtag)) {
                isValidSetting = true;
              } else if (hashtag.equals("#hide")) {
                boolean isSystem = BuildConfig.DEBUG || (Device.MANUFACTURER == Device.SAMSUNG && identifier.equals("samsung"));
                if (!isSystem)
                  throw new IllegalArgumentException();
              } else if (hashtag.equals("#noname")) {
                hideName = true;
              } else if (hashtag.startsWith("#v")) {
                version = StringUtils.parseInt(hashtag.substring(2), -1);
              } else if (hashtag.startsWith("#p")) {
                position = StringUtils.parseInt(hashtag.substring(2), -1);
              }
              break;
            }
            case TdApi.TextEntityTypeCode.CONSTRUCTOR:
            case TdApi.TextEntityTypePre.CONSTRUCTOR:
            case TdApi.TextEntityTypePreCode.CONSTRUCTOR: {
              displayName = Td.substring(document.caption.text, entity);
              break;
            }
          }
        }
      }
      if (!isValidSetting || version != BuildConfig.EMOJI_VERSION || displayName == null)
        throw new IllegalArgumentException();
      this.file = document.document.document;
      if (isBuiltIn()) {
        if (hideName)
          this.displayName = Lang.getString(builtInStringRes);
        this.file = null;
      }
    }

    public final TdApi.File getFile () {
      return file;
    }

    @Nullable
    public final ImageFile getPreviewFile () {
      return previewFile;
    }

    public final String getDisplayName () {
      return displayName;
    }

    public final void setPreviewFile (TdlibProvider tdlib, TdApi.File file) {
      if (file != null) {
        int desiredSize = Screen.dp(64f);
        this.previewFile = new ImageFile(tdlib, file);
        this.previewFile.setScaleType(ImageFile.FIT_CENTER);
        this.previewFile.setSize(desiredSize); // FIXME improve
      } else {
        this.previewFile = null;
      }
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      return obj instanceof CloudSetting && ((CloudSetting) obj).identifier.equals(this.identifier);
    }

    @Override
    public int hashCode () {
      return this.identifier.hashCode();
    }

    @Override
    public final int compareTo (CloudSetting o) {
      if (StringUtils.equalsOrBothEmpty(identifier, o.identifier))
        return 0;
      boolean h1 = position != -1, h2 = o.position != -1;
      if (h1 != h2)
        return Boolean.compare(h2, h1);
      if (h1 && position != o.position)
        return Integer.compare(position, o.position);
      if (!StringUtils.equalsOrBothEmpty(displayName, o.displayName))
        return displayName.compareTo(o.displayName);
      return identifier.compareTo(o.identifier);
    }

    public abstract void install (@NonNull RunnableBool callback);

    public abstract boolean isBuiltIn ();

    public static final int STATE_NOT_INSTALLED = 0;
    public static final int STATE_INSTALLED = 1;
    public static final int STATE_UPDATE_NEEDED = 2;

    public abstract int getInstallState (boolean fast);

    public final boolean isInstalled () {
      return isBuiltIn() || getInstallState(false) == STATE_INSTALLED;
    }
  }

  // Emoji pack

  private EmojiPack emojiPack, outdatedEmojiPack;

  public EmojiPack getOutdatedEmojiPack () {
    return outdatedEmojiPack;
  }

  public void revokeOutdatedEmojiPack () {
    setEmojiPack(emojiPack);
  }

  public EmojiPack getEmojiPack () {
    if (emojiPack == null) {
      EmojiPack pack = null;
      for (LevelDB.Entry entry : pmc.find(KEY_EMOJI_PACK)) {
        if (entry.key().length() == KEY_EMOJI_PACK.length()) {
          pack = new EmojiPack(entry.asString());
        } else {
          if (pack == null)
            continue;
          switch (entry.key().substring(KEY_EMOJI_PACK.length())) {
            case "_version":
              pack.version = entry.asInt();
              break;
            case "_name":
              pack.displayName = entry.asString();
              break;
            case "_date":
              pack.date = entry.asInt();
              break;
          }
        }
      }
      if (pack != null) {
        if (pack.version != BuildConfig.EMOJI_VERSION) {
          outdatedEmojiPack = pack;
          emojiPack = new EmojiPack();
        } else {
          emojiPack = pack;
        }
      } else {
        emojiPack = new EmojiPack();
      }
    }
    return emojiPack;
  }

  public String getEmojiPackIdentifier () {
    return getEmojiPack().identifier;
  }

  public void setEmojiPack (EmojiPack emojiPack) {
    if (emojiPack.version != BuildConfig.EMOJI_VERSION)
      throw new IllegalArgumentException("emojiPack.version == " + emojiPack.version);
    this.emojiPack = emojiPack;
    this.outdatedEmojiPack = null;
    if (emojiPack.identifier.equals(BuildConfig.EMOJI_BUILTIN_ID)) {
      pmc.removeByPrefix(KEY_EMOJI_PACK);
    } else {
      pmc.edit()
        .putString(KEY_EMOJI_PACK, emojiPack.identifier)
        .putString(KEY_EMOJI_PACK + "_name", emojiPack.displayName)
        .putInt(KEY_EMOJI_PACK + "_date", emojiPack.date)
        .putInt(KEY_EMOJI_PACK + "_version", emojiPack.version)
        .apply();
    }
  }

  public int getEmojiPackInstallState (CloudSetting setting, boolean fast) {
    if (BuildConfig.EMOJI_BUILTIN_ID.equals(setting.identifier))
      return CloudSetting.STATE_INSTALLED;
    int installedVersion = pmc.getInt(KEY_EMOJI_INSTALLED_PREFIX + setting.identifier, 0);
    boolean hasFile = installedVersion > 0 && (fast || new File(Emoji.getEmojiPackDirectory(), setting.identifier).exists());
    return hasFile ? (installedVersion == setting.date ? CloudSetting.STATE_INSTALLED : CloudSetting.STATE_UPDATE_NEEDED) : CloudSetting.STATE_NOT_INSTALLED;
  }

  private String[] quickReactions;

  public void setQuickReactions (String reactions[]) {
    pmc.putStringArray(KEY_QUICK_REACTIONS, reactions);
    quickReactions = reactions;
  }

  public String[] getQuickReactions (Tdlib tdlib) {
    if (quickReactions == null) {
      quickReactions = pmc.getStringArray(KEY_QUICK_REACTIONS);
      if (quickReactions == null) {
        quickReactions = new String[] {
          tdlib.defaultEmojiReaction()
        };
      }
    }
    return quickReactions;
  }

  public void setBigReactionsInChannels (boolean inChannels) {
    pmc.putBoolean(KEY_BIG_REACTIONS_IN_CHANNELS, inChannels);
  }

  public void setBigReactionsInChats (boolean inChats) {
    pmc.putBoolean(KEY_BIG_REACTIONS_IN_CHATS, inChats);
  }

  public boolean getBigReactionsInChannels () {
    return getBoolean(KEY_BIG_REACTIONS_IN_CHANNELS, true);
  }

  public boolean getBigReactionsInChats () {
    return getBoolean(KEY_BIG_REACTIONS_IN_CHATS, true);
  }

  public void markEmojiPackInstalled (EmojiPack emojiPack) {
    pmc.putInt(KEY_EMOJI_INSTALLED_PREFIX + emojiPack.identifier, emojiPack.date);
  }

  public void uninstallEmojiPacks (String exceptIdentifier) {
    SharedPreferences.Editor editor = null;
    for (LevelDB.Entry entry : pmc.find(KEY_EMOJI_INSTALLED_PREFIX)) {
      if (exceptIdentifier != null && entry.key().substring(KEY_EMOJI_INSTALLED_PREFIX.length()).equals(exceptIdentifier))
        continue;
      if (editor == null)
        editor = edit();
      editor.remove(entry.key());
    }
    if (editor != null)
      editor.apply();
  }

  public static class EmojiPack extends CloudSetting {
    public EmojiPack () {
      super(BuildConfig.EMOJI_BUILTIN_ID, BuildConfig.EMOJI_VERSION, Lang.getString(R.string.EmojiBuiltIn), 0);
    }

    public EmojiPack (String identifier) {
      super(identifier);
    }

    public EmojiPack (TdApi.Message message) {
      super(message, "#emoji", R.string.EmojiBuiltIn);
    }

    @Override
    public boolean isBuiltIn () {
      return identifier.equals(BuildConfig.EMOJI_BUILTIN_ID);
    }

    @Override
    public int getInstallState (boolean fast) {
      /*if (fast) {
        return instance().isEmojiPackInstalledFast(this.identifier);
      } else {
        return Settings.instance().isEmojiPackInstalled(this);
      }*/
      return Settings.instance().getEmojiPackInstallState(this, fast);
    }

    @Override
    public void install (@NonNull RunnableBool callback) {
      Emoji.instance().install(this, callback);
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      return obj instanceof EmojiPack && super.equals(obj);
    }
  }

  // Icon Packs

  public static class IconPack extends CloudSetting {
    public IconPack () {
      super(Config.ICONS_BUILTIN_ID, 1, Lang.getString(R.string.IconsBuiltIn), 0);
    }

    public IconPack (String identifier) {
      super(identifier);
    }

    public IconPack (TdApi.Message message) {
      super(message, "#icons", R.string.IconsBuiltIn);
    }

    @Override
    public boolean isBuiltIn () {
      return Config.ICONS_BUILTIN_ID.equals(this.identifier);
    }

    @Override
    public int getInstallState (boolean fast) {
      return STATE_NOT_INSTALLED;
    }

    @Override
    public void install (@NonNull RunnableBool callback) {
      // TODO

      callback.runWithBool(false);
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      return obj instanceof IconPack && super.equals(obj);
    }
  }

  private IconPack iconPack;

  public IconPack getIconPack () {
    if (iconPack == null) {
      iconPack = new IconPack();
    }
    return iconPack;
  }

  public long getKnownSize (String path, long length, long lastModified) throws FileNotFoundException {
    long[] data = pmc.getLongArray(KEY_KNOWN_SIZE + path);
    if (data == null || data.length < 3 || data[0] != length || data[1] != lastModified) {
      throw new FileNotFoundException();
    }
    return data[2];
  }

  public void forgetKnownSize (String path) {
    pmc.remove(KEY_KNOWN_SIZE + path);
  }

  public void putKnownSize (String path, long length, long lastModified, int width, int height) {
    pmc.putLongArray(KEY_KNOWN_SIZE + path, new long[] {length, lastModified, BitwiseUtils.mergeLong(width, height)});
  }

  private long nextInstallationId () {
    return pmc.getLong(KEY_APP_INSTALLATION_ID, 0) + 1;
  }

  private AppBuildInfo currentBuildInformation;

  public long installationId () {
    if (BuildConfig.DEBUG) {
      return 0;
    }
    if (currentBuildInformation != null) {
      return currentBuildInformation.getInstallationId();
    }
    return pmc.getLong(KEY_APP_INSTALLATION_ID, 0);
  }

  private void trackInstalledApkVersion () {
    final long knownCommitDate = pmc.getLong(KEY_APP_COMMIT_DATE, 0);
    if (AppBuildInfo.maxBuiltInCommitDate() <= knownCommitDate) {
      // Track only updates with more recent commits.
      return;
    }
    final long installationId = nextInstallationId();
    AppBuildInfo buildInfo = new AppBuildInfo(installationId);
    pmc.edit()
      .putLong(KEY_APP_INSTALLATION_ID, installationId)
      .putLong(KEY_APP_COMMIT_DATE, buildInfo.maxCommitDate());
    buildInfo.saveTo(pmc, KEY_APP_INSTALLATION_PREFIX + installationId);
    pmc.apply();
    this.currentBuildInformation = buildInfo;
    resetAppVersionPushMessageCount();
  }

  private void trackChangesInAvailableFeatures () {
    final long currentlyAvailableFeatures = FeatureAvailability.currentlyAvailableFeatures();
    long previouslyAvailableFeatures;
    boolean saveFeatures = false;
    try {
      previouslyAvailableFeatures = pmc.tryGetLong(KEY_FEATURES);
    } catch (FileNotFoundException e) {
      long previousInstallationId = currentBuildInformation != null ? currentBuildInformation.getInstallationId() - 1 : -1;
      int previouslyInstalledVersionCode = previousInstallationId != -1 ?
        AppBuildInfo.restoreVersionCode(pmc, KEY_APP_INSTALLATION_PREFIX + previousInstallationId) : 0;
      if (previouslyInstalledVersionCode != 0) {
        previouslyAvailableFeatures = FeatureAvailability.recoverAvailableFeaturesForAppVersionCode(previouslyInstalledVersionCode);
      } else {
        // Do not bombard with a dozen of pop-ups on clean app installations
        previouslyAvailableFeatures = currentlyAvailableFeatures;
      }
      saveFeatures = true;
    }
    if (currentlyAvailableFeatures != previouslyAvailableFeatures) {
      final long recentlyAddedFeatures = currentlyAvailableFeatures & (~previouslyAvailableFeatures);
      final long recentlyRemovedFeatures = previouslyAvailableFeatures & (~currentlyAvailableFeatures);

      long addedFeaturesNotifications = pmc.getLong(KEY_FEATURES_ADDED_NOTIFICATIONS, 0);
      long removedFeaturesNotifications = pmc.getLong(KEY_FEATURES_REMOVED_NOTIFICATIONS, 0);

      addedFeaturesNotifications &= ~recentlyRemovedFeatures;
      addedFeaturesNotifications |= recentlyAddedFeatures;

      removedFeaturesNotifications &= ~recentlyAddedFeatures;
      removedFeaturesNotifications |= recentlyRemovedFeatures;

      pmc.edit()
        .putLong(KEY_FEATURES_ADDED_NOTIFICATIONS, addedFeaturesNotifications)
        .putLong(KEY_FEATURES_REMOVED_NOTIFICATIONS, removedFeaturesNotifications)
        .apply();

      this._addedFeaturesNotifications = addedFeaturesNotifications;
      this._removedFeaturesNotifications = removedFeaturesNotifications;

      saveFeatures = true;
    }
    if (saveFeatures) {
      pmc.putLong(KEY_FEATURES, currentlyAvailableFeatures);
    }
  }

  public interface FeatureAvailabilityNotificationDismissListener {
    void onDismissFeatureAvailabilityNotification (@FeatureAvailability.Feature long feature, boolean wasAdded, boolean wasRemoved);
  }

  private ReferenceList<FeatureAvailabilityNotificationDismissListener> featureNotificationDismissListeners;

  public void addFeatureAvailabilityNotificationDismissListener (FeatureAvailabilityNotificationDismissListener listener) {
    if (this.featureNotificationDismissListeners == null) {
      this.featureNotificationDismissListeners = new ReferenceList<>();
    }
    this.featureNotificationDismissListeners.add(listener);
  }

  public void removeFeatureAvailabilityNotificationDismissListener (FeatureAvailabilityNotificationDismissListener listener) {
    if (this.featureNotificationDismissListeners != null) {
      this.featureNotificationDismissListeners.remove(listener);
    }
  }

  private Long _addedFeaturesNotifications, _removedFeaturesNotifications;

  public long getAddedFeaturesNotifications () {
    if (_addedFeaturesNotifications == null) {
      _addedFeaturesNotifications = pmc.getLong(KEY_FEATURES_ADDED_NOTIFICATIONS, 0);
    }
    return _addedFeaturesNotifications;
  }

  public long getRemovedFeaturesNotifications () {
    if (_removedFeaturesNotifications == null) {
      _removedFeaturesNotifications = pmc.getLong(KEY_FEATURES_REMOVED_NOTIFICATIONS, 0);
    }
    return _removedFeaturesNotifications;
  }

  public void revokeFeatureNotifications (@FeatureAvailability.Feature long feature) {
    long addedFeaturesNotifications = getAddedFeaturesNotifications();
    long removedFeaturesNotifications = getRemovedFeaturesNotifications();
    boolean wasAdded = BitwiseUtils.hasFlag(addedFeaturesNotifications, feature);
    boolean wasRemoved = BitwiseUtils.hasFlag(removedFeaturesNotifications, feature);
    if (wasAdded || wasRemoved) {
      addedFeaturesNotifications &= ~feature;
      removedFeaturesNotifications &= ~feature;
      pmc.edit()
        .putLong(KEY_FEATURES_ADDED_NOTIFICATIONS, addedFeaturesNotifications)
        .putLong(KEY_FEATURES_REMOVED_NOTIFICATIONS, removedFeaturesNotifications)
        .apply();
      this._addedFeaturesNotifications = addedFeaturesNotifications;
      this._removedFeaturesNotifications = removedFeaturesNotifications;
      if (featureNotificationDismissListeners != null) {
        for (FeatureAvailabilityNotificationDismissListener listener : featureNotificationDismissListeners) {
          listener.onDismissFeatureAvailabilityNotification(feature, wasAdded, wasRemoved);
        }
      }
    }
  }

  public boolean hasPendingFeatureAddedNotification (@FeatureAvailability.Feature long feature) {
    return BitwiseUtils.hasAllFlags(getAddedFeaturesNotifications(), feature);
  }

  public AppBuildInfo getFirstBuildInformation () {
    AppBuildInfo appBuildInfo = getBuildInformation(1);
    return appBuildInfo != null ? appBuildInfo : getCurrentBuildInformation();
  }

  public AppBuildInfo getCurrentBuildInformation () {
    if (currentBuildInformation == null) {
      long installationId = pmc.getLong(KEY_APP_INSTALLATION_ID, 0);
      this.currentBuildInformation = AppBuildInfo.restoreFrom(pmc, installationId, KEY_APP_INSTALLATION_PREFIX + installationId);
    }
    return this.currentBuildInformation;
  }

  @Nullable
  public AppBuildInfo getBuildInformation (long installationId) {
    return installationId > 0 ? AppBuildInfo.restoreFrom(pmc, installationId, KEY_APP_INSTALLATION_PREFIX + installationId) : null;
  }

  @Nullable
  public AppBuildInfo getPreviousBuildInformation () {
    AppBuildInfo currentBuild = getCurrentBuildInformation();
    long previousInstallationId = (currentBuild.getInstallationId() - 1);
    return getBuildInformation(previousInstallationId);
  }

  public String getPushMessageStats () {
    return
      "total: " + getReceivedPushMessageCountTotal() + " " +
      "by_token: " + getReceivedPushMessageCountByToken() + " " +
      "by_app_version: " + getReceivedPushMessageCountByAppVersion() + " ";
  }

  public long getReceivedPushMessageCountTotal () {
    return pmc.getLong(KEY_PUSH_STATS_TOTAL_COUNT, 0);
  }

  public long getReceivedPushMessageCountByAppVersion () {
    return pmc.getLong(KEY_PUSH_STATS_CURRENT_APP_VERSION_COUNT, 0);
  }

  public long getReceivedPushMessageCountByToken () {
    return pmc.getLong(KEY_PUSH_STATS_CURRENT_TOKEN_COUNT, 0);
  }

  public long getLastReceivedPushMessageSentTime () {
    return pmc.getLong(KEY_PUSH_LAST_SENT_TIME, 0);
  }

  public long getLastReceivedPushMessageReceivedTime () {
    return pmc.getLong(KEY_PUSH_LAST_RECEIVED_TIME, 0);
  }

  public int getLastReceivedPushMessageTtl () {
    return pmc.getInt(KEY_PUSH_LAST_TTL, 0);
  }

  public interface PushStatsListener {
    void onNewPushReceived ();
  }

  private final ReferenceList<PushStatsListener> pushStatsListeners = new ReferenceList<>(true);

  public void addPushStatsListener (PushStatsListener listener) {
    pushStatsListeners.add(listener);
  }

  public void removePushStatsListener (PushStatsListener listener) {
    pushStatsListeners.remove(listener);
  }

  public void trackPushMessageReceived (long sentTime, long receivedTime, int ttl) {
    final long totalReceivedCount = getReceivedPushMessageCountTotal() + 1;
    final long currentVersionReceivedCount = getReceivedPushMessageCountByAppVersion() + 1;
    final long currentTokenReceivedCount = getReceivedPushMessageCountByToken() + 1;
    pmc.edit()
      .putLong(KEY_PUSH_STATS_TOTAL_COUNT, totalReceivedCount)
      .putLong(KEY_PUSH_STATS_CURRENT_APP_VERSION_COUNT, currentVersionReceivedCount)
      .putLong(KEY_PUSH_STATS_CURRENT_TOKEN_COUNT, currentTokenReceivedCount)
      .putLong(KEY_PUSH_LAST_SENT_TIME, sentTime)
      .putLong(KEY_PUSH_LAST_RECEIVED_TIME, receivedTime)
      .putInt(KEY_PUSH_LAST_TTL, ttl)
      .apply();
    for (PushStatsListener listener : pushStatsListeners) {
      listener.onNewPushReceived();
    }
  }

  public void resetAppVersionPushMessageCount () {
    pmc.remove(KEY_PUSH_STATS_CURRENT_APP_VERSION_COUNT);
  }

  public void resetTokenPushMessageCount () {
    pmc.remove(KEY_PUSH_STATS_CURRENT_TOKEN_COUNT);
  }

  public void setReportedPushServiceError (@Nullable String error) {
    if (!StringUtils.isEmpty(error)) {
      pmc.edit()
        .putString(KEY_PUSH_REPORTED_ERROR, error)
        .putLong(KEY_PUSH_REPORTED_ERROR_DATE, System.currentTimeMillis())
        .apply();
    } else {
      pmc.edit()
        .remove(KEY_PUSH_REPORTED_ERROR)
        .remove(KEY_PUSH_REPORTED_ERROR_DATE)
        .apply();
    }
  }

  @Nullable
  public String getReportedPushServiceError () {
    return pmc.getString(KEY_PUSH_REPORTED_ERROR, null);
  }

  public long getReportedPushServiceErrorDate () {
    return pmc.getLong(KEY_PUSH_REPORTED_ERROR_DATE, 0);
  }

  public String getDefaultLanguageForTranslateDraft () {
    return pmc.getString(KEY_DEFAULT_LANGUAGE_FOR_TRANSLATE_DRAFT, "en");
  }

  public void setDefaultLanguageForTranslateDraft (String language) {
    pmc.putString(KEY_DEFAULT_LANGUAGE_FOR_TRANSLATE_DRAFT, language);
  }

  public boolean chatFoldersEnabled () {
    return FeatureAvailability.Released.CHAT_FOLDERS;
  }

  public boolean showPeerIds () {
    return isExperimentEnabled(EXPERIMENT_FLAG_SHOW_PEER_IDS);
  }

  @Nullable
  private Integer _playbackSpeed;

  public void setPlaybackSpeed (int speed) {
    if (speed <= 0)
      throw new IllegalArgumentException(Integer.toString(speed));
    pmc.putInt(KEY_PLAYBACK_SPEED, _playbackSpeed = speed);
  }

  public int getPlaybackSpeed () {
    if (_playbackSpeed == null) {
      _playbackSpeed = PlaybackSpeedLayout.normalizeSpeed(pmc.getInt(KEY_PLAYBACK_SPEED, 100));
    }
    return _playbackSpeed;
  }
}
