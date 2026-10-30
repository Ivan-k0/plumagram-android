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

import android.os.SystemClock;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import me.vkryl.leveldb.LevelDB;

/**
 * TGx101 auto clean (user 2026-10-30, approved mockup «Кэш» 2): the cache is cleaned apart for personal chats, groups and
 * channels — each with its own «not opened for» time and its own size limit; when a kind's files outgrow the limit, the
 * longest unopened ones go first. Which file types may go is shared; pinned chats can be left alone. TDLib's own
 * optimizer (one «Keep media» for everything) is switched off once these rules are set — they take its place.
 * Runs on the app's resume, at most once an hour per account, and right after the rules change.
 */
public final class Tgx101AutoClean {
  public static final int KIND_PRIVATE = 0, KIND_GROUP = 1, KIND_CHANNEL = 2;
  public static final int KIND_COUNT = 3;

  /** «Not opened for» steps, seconds; 0 — never */
  public static final int[] TTL_STEPS = {60 * 60 * 24, 60 * 60 * 24 * 3, 60 * 60 * 24 * 7, 60 * 60 * 24 * 30, 0};
  /** Size limit steps, MB; 0 — no limit */
  public static final int[] LIMIT_STEPS_MB = {512, 1024, 2048, 5120, 0};

  public static final int TYPE_VIDEO = 1, TYPE_FILES = 1 << 1, TYPE_PHOTO = 1 << 2, TYPE_VOICE = 1 << 3, TYPE_STICKERS = 1 << 4;
  private static final int DEFAULT_TYPES = TYPE_VIDEO | TYPE_FILES | TYPE_PHOTO;

  private static final String KEY_TTL = "tgx101_clean_ttl_", KEY_LIMIT = "tgx101_clean_limit_", KEY_TYPES = "tgx101_clean_types",
    KEY_SKIP_PINNED = "tgx101_clean_skip_pinned", KEY_MIGRATED = "tgx101_clean_migrated", KEY_KEEP = "tgx101_clean_keep";

  private static final long RUN_INTERVAL_MS = 60 * 60 * 1000L;
  private static final Map<Integer, Long> lastRun = new HashMap<>();

  private static LevelDB pmc () {
    return Settings.instance().pmc();
  }

  // Rules

  public static int ttl (int kind) {
    return pmc().getInt(KEY_TTL + kind, 0);
  }

  public static void setTtl (int kind, int seconds) {
    pmc().putInt(KEY_TTL + kind, seconds);
  }

  public static int limitMb (int kind) {
    return pmc().getInt(KEY_LIMIT + kind, 0);
  }

  public static void setLimitMb (int kind, int mb) {
    pmc().putInt(KEY_LIMIT + kind, mb);
  }

  public static int types () {
    return pmc().getInt(KEY_TYPES, DEFAULT_TYPES);
  }

  public static boolean hasType (int type) {
    return (types() & type) != 0;
  }

  public static void setType (int type, boolean on) {
    int types = types();
    pmc().putInt(KEY_TYPES, on ? types | type : types & ~type);
  }

  public static boolean skipPinned () {
    return pmc().getBoolean(KEY_SKIP_PINNED, true);
  }

  public static void setSkipPinned (boolean value) {
    pmc().putBoolean(KEY_SKIP_PINNED, value);
  }

  /** Chats the user left out of any cleaning (the chat's own cache window, mockup 3) */
  public static boolean isKept (long chatId) {
    long[] ids = pmc().getLongArray(KEY_KEEP);
    if (ids != null) for (long id : ids) if (id == chatId) return true;
    return false;
  }

  public static void setKept (long chatId, boolean keep) {
    long[] ids = pmc().getLongArray(KEY_KEEP);
    ArrayList<Long> list = new ArrayList<>();
    if (ids != null) for (long id : ids) if (id != chatId) list.add(id);
    if (keep) list.add(chatId);
    long[] result = new long[list.size()];
    for (int i = 0; i < result.length; i++) result[i] = list.get(i);
    pmc().putLongArray(KEY_KEEP, result);
  }

  public static boolean hasRules () {
    for (int kind = 0; kind < KIND_COUNT; kind++) {
      if (ttl(kind) != 0 || limitMb(kind) != 0) return true;
    }
    return false;
  }

  public static int stepOf (int[] steps, int value) {
    for (int i = 0; i < steps.length; i++) if (steps[i] == value) return i;
    if (value == 0) return steps.length - 1;
    for (int i = 0; i < steps.length - 1; i++) if (value <= steps[i]) return i; // the nearest shorter-or-equal step
    return steps.length - 2;
  }

  /** The old single «Keep media» becomes the same time for every kind, once; TDLib's optimizer is switched off then */
  public static void migrate (Tdlib tdlib, long oldTtl) {
    if (pmc().getBoolean(KEY_MIGRATED, false)) return;
    pmc().putBoolean(KEY_MIGRATED, true);
    if (oldTtl > 0) {
      int step = TTL_STEPS[stepOf(TTL_STEPS, (int) oldTtl)];
      for (int kind = 0; kind < KIND_COUNT; kind++) setTtl(kind, step);
      Tgx101Diag.mark("auto clean: «Keep media» " + oldTtl + " s became " + step + " s for every kind");
    }
    switchOffTdlibOptimizer(tdlib);
  }

  public static boolean isMigrated () {
    return pmc().getBoolean(KEY_MIGRATED, false);
  }

  private static void switchOffTdlibOptimizer (Tdlib tdlib) {
    tdlib.send(new TdApi.SetOption("use_storage_optimizer", new TdApi.OptionValueBoolean(false)), tdlib.typedOkHandler());
    tdlib.send(new TdApi.SetOption("storage_max_time_from_last_access", new TdApi.OptionValueInteger(0)), tdlib.typedOkHandler());
  }

  // Kinds

  public static int kindOf (Tdlib tdlib, long chatId) {
    if (tdlib.isUserChat(chatId)) return KIND_PRIVATE;
    if (tdlib.isChannel(chatId)) return KIND_CHANNEL;
    return KIND_GROUP;
  }

  private static boolean isPinned (Tdlib tdlib, long chatId) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null || chat.positions == null) return false;
    for (TdApi.ChatPosition position : chat.positions) if (position.isPinned) return true;
    return false;
  }

  /** Sizes of the cache by kind, bytes (chats only) */
  public static long[] sizesByKind (Tdlib tdlib, TdApi.StorageStatistics stats) {
    long[] sizes = new long[KIND_COUNT];
    if (stats != null && stats.byChat != null) {
      for (TdApi.StorageStatisticsByChat chat : stats.byChat) {
        if (chat.chatId != 0) sizes[kindOf(tdlib, chat.chatId)] += chat.size;
      }
    }
    return sizes;
  }

  private static TdApi.FileType[] fileTypes () {
    ArrayList<TdApi.FileType> list = new ArrayList<>();
    int types = types();
    if ((types & TYPE_VIDEO) != 0) {
      list.add(new TdApi.FileTypeVideo());
      list.add(new TdApi.FileTypeAnimation());
    }
    if ((types & TYPE_FILES) != 0) {
      list.add(new TdApi.FileTypeDocument());
      list.add(new TdApi.FileTypeAudio());
    }
    if ((types & TYPE_PHOTO) != 0) list.add(new TdApi.FileTypePhoto());
    if ((types & TYPE_VOICE) != 0) {
      list.add(new TdApi.FileTypeVoiceNote());
      list.add(new TdApi.FileTypeVideoNote());
    }
    if ((types & TYPE_STICKERS) != 0) list.add(new TdApi.FileTypeSticker());
    return list.toArray(new TdApi.FileType[0]);
  }

  // Running

  /** On the app's resume: at most once an hour per account */
  public static void maybeRun (@Nullable Tdlib tdlib) {
    if (tdlib == null || tdlib.account().isUnauthorized() || !hasRules()) return;
    Long last = lastRun.get(tdlib.id());
    long now = SystemClock.elapsedRealtime();
    if (last != null && now - last < RUN_INTERVAL_MS) return;
    run(tdlib, null);
  }

  /** Right now (the rules changed); {@code after} gets the fresh statistics on the UI thread */
  public static void run (Tdlib tdlib, @Nullable Runnable after) {
    lastRun.put(tdlib.id(), SystemClock.elapsedRealtime());
    TdApi.FileType[] fileTypes = fileTypes();
    if (fileTypes.length == 0) {
      if (after != null) tdlib.ui().post(after);
      return;
    }
    tdlib.send(new TdApi.GetStorageStatistics(1000), (stats, error) -> {
      if (error != null || stats == null) {
        Tgx101Diag.mark("auto clean: no statistics, " + (error != null ? error.message : "null"));
        if (after != null) tdlib.ui().post(after);
        return;
      }
      boolean skipPinned = skipPinned();
      ArrayList<ArrayList<Long>> chats = new ArrayList<>();
      for (int kind = 0; kind < KIND_COUNT; kind++) chats.add(new ArrayList<>());
      for (TdApi.StorageStatisticsByChat chat : stats.byChat) {
        if (chat.chatId == 0 || chat.size == 0) continue;
        if (isKept(chat.chatId) || (skipPinned && isPinned(tdlib, chat.chatId))) continue;
        chats.get(kindOf(tdlib, chat.chatId)).add(chat.chatId);
      }
      int[] pending = {0};
      ArrayList<Runnable> calls = new ArrayList<>();
      for (int kind = 0; kind < KIND_COUNT; kind++) {
        final int k = kind;
        int ttl = ttl(kind), limitMb = limitMb(kind);
        ArrayList<Long> ids = chats.get(kind);
        if ((ttl == 0 && limitMb == 0) || ids.isEmpty()) continue; // an empty list would mean every chat
        long[] chatIds = new long[ids.size()];
        for (int i = 0; i < chatIds.length; i++) chatIds[i] = ids.get(i);
        long size = limitMb != 0 ? limitMb * 1024L * 1024L : 1L << 50;
        int ttlArg = ttl != 0 ? ttl : Integer.MAX_VALUE;
        pending[0]++;
        calls.add(() -> tdlib.send(new TdApi.OptimizeStorage(size, ttlArg, Integer.MAX_VALUE, 60 * 60, fileTypes, chatIds, null, true, 0), (deleted, err) -> {
          Tgx101Diag.mark("auto clean: kind " + k + ", " + chatIds.length + " chats, ttl " + ttl + " s, limit " + limitMb + " MB → " +
            (err != null ? "error " + err.message : "freed " + Strings.buildSize(deleted != null ? deleted.size : 0) + " in " + (deleted != null ? deleted.count : 0) + " files"));
          if (--pending[0] == 0 && after != null) tdlib.ui().post(after);
        }));
      }
      if (calls.isEmpty()) {
        if (after != null) tdlib.ui().post(after);
        return;
      }
      for (Runnable call : calls) call.run();
    });
  }
}
