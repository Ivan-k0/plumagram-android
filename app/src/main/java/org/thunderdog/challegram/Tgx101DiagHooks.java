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

import android.view.MotionEvent;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.tool.Screen;

import java.util.HashMap;
import java.util.Map;

/**
 * TGx101: diagnostics hooks that need app classes (TDLib updates, UI). Kept apart from {@link Tgx101Diag},
 * which BaseApplication loads before MultiDex on Android 4. Every method returns at once in public builds.
 * IDs and types only: no message texts, names or phone numbers.
 */
public final class Tgx101DiagHooks {
  private Tgx101DiagHooks () { }

  private static final long SUMMARY_MS = 10_000;
  private static final Map<String, int[]> updateCounts = new HashMap<>();
  private static boolean summaryScheduled;

  // TDLib

  public static void onUpdate (TdApi.Update update) {
    if (!BuildConfig.TGX101_DIAG) {
      return;
    }
    try {
      switch (update.getConstructor()) {
        case TdApi.UpdateUserStatus.CONSTRUCTOR: {
          TdApi.UpdateUserStatus u = (TdApi.UpdateUserStatus) update;
          Tgx101Diag.mark("td user " + u.userId + " status " + status(u.status));
          return;
        }
        case TdApi.UpdateConnectionState.CONSTRUCTOR:
          Tgx101Diag.mark("td connection " + ((TdApi.UpdateConnectionState) update).state.getClass().getSimpleName());
          return;
        case TdApi.UpdateAuthorizationState.CONSTRUCTOR:
          Tgx101Diag.mark("td authorization " + ((TdApi.UpdateAuthorizationState) update).authorizationState.getClass().getSimpleName());
          return;
        case TdApi.UpdateNewMessage.CONSTRUCTOR: {
          TdApi.Message m = ((TdApi.UpdateNewMessage) update).message;
          Tgx101Diag.mark("td new message chat " + m.chatId + " id " + m.id + (m.isOutgoing ? " out" : " in") +
            " " + m.content.getClass().getSimpleName() + " delay " + (nowSec() - m.date) + "s");
          return;
        }
        case TdApi.UpdateNotificationGroup.CONSTRUCTOR: {
          TdApi.UpdateNotificationGroup u = (TdApi.UpdateNotificationGroup) update;
          Tgx101Diag.mark("td notification group " + u.notificationGroupId + " chat " + u.chatId + " type " + u.type.getClass().getSimpleName() +
            " +" + u.addedNotifications.length + " -" + u.removedNotificationIds.length + " total " + u.totalCount + (u.notificationSoundId != 0 ? " sound" : " silent"));
          return;
        }
        case TdApi.UpdateCall.CONSTRUCTOR: {
          TdApi.Call call = ((TdApi.UpdateCall) update).call;
          Tgx101Diag.mark("td call " + call.id + " user " + call.userId + (call.isOutgoing ? " out " : " in ") + call.state.getClass().getSimpleName() + (call.isVideo ? " video" : ""));
          return;
        }
      }
      count(update.getClass().getSimpleName());
    } catch (Throwable t) {
      Tgx101Diag.mark("td update log failed: " + t);
    }
  }

  private static String status (TdApi.UserStatus status) {
    switch (status.getConstructor()) {
      case TdApi.UserStatusOnline.CONSTRUCTOR:
        return "online until +" + (((TdApi.UserStatusOnline) status).expires - nowSec()) + "s";
      case TdApi.UserStatusOffline.CONSTRUCTOR:
        return "offline, was online " + (nowSec() - ((TdApi.UserStatusOffline) status).wasOnline) + "s ago";
      default:
        return status.getClass().getSimpleName();
    }
  }

  private static long nowSec () {
    return System.currentTimeMillis() / 1000;
  }

  /** High-frequency updates are counted and written as one summary line every 10 s */
  private static void count (String type) {
    synchronized (updateCounts) {
      int[] c = updateCounts.get(type);
      if (c == null) {
        updateCounts.put(type, new int[] {1});
      } else {
        c[0]++;
      }
      if (!summaryScheduled) {
        summaryScheduled = true;
        Tgx101Diag.postDelayed(Tgx101DiagHooks::writeSummary, SUMMARY_MS);
      }
    }
  }

  private static void writeSummary () {
    StringBuilder b = new StringBuilder("td updates 10s:");
    synchronized (updateCounts) {
      summaryScheduled = false;
      for (Map.Entry<String, int[]> e : updateCounts.entrySet()) {
        b.append(' ').append(e.getKey().replace("Update", "")).append('×').append(e.getValue()[0]);
      }
      updateCounts.clear();
    }
    Tgx101Diag.mark(b.toString());
  }

  // UI

  public static void onChatHeaderStatus (long chatId, CharSequence subtitle) {
    if (BuildConfig.TGX101_DIAG) {
      // Status strings («в сети», «был(а) 10 минут назад», «печатает…») contain no personal data
      Tgx101Diag.mark("header chat " + chatId + " status «" + subtitle + "»");
    }
  }

  public static void onControllerFocus (Object controller, boolean focused) {
    if (BuildConfig.TGX101_DIAG) {
      Tgx101Diag.mark((focused ? "screen → " : "screen ← ") + controller.getClass().getSimpleName());
    }
  }

  public static void onKeyboard (boolean visible) {
    if (BuildConfig.TGX101_DIAG) {
      Tgx101Diag.mark("keyboard " + (visible ? "shown" : "hidden"));
    }
  }

  public static void onPopup (Object popup, Object content, boolean shown) {
    if (BuildConfig.TGX101_DIAG) {
      Tgx101Diag.mark("popup " + (shown ? "shown " : "removed ") + (content != null ? content.getClass().getSimpleName() : popup.getClass().getSimpleName()));
    }
  }

  public static void onBack (String source) {
    if (BuildConfig.TGX101_DIAG) {
      Tgx101Diag.mark("back (" + source + ")");
    }
  }

  public static void onNotificationShown (int id, long chatId, String category) {
    if (BuildConfig.TGX101_DIAG) {
      Tgx101Diag.mark("notification shown id " + id + " chat " + chatId + " " + category);
    }
  }

  public static void onLog (String tag, int level, String message, Throwable t) {
    if (BuildConfig.TGX101_DIAG) {
      // Log lines may contain phone numbers (contact sync): mask every long run of digits
      String text = message.replaceAll("\\+?\\d[\\d ()-]{5,}\\d", "<number>");
      if (text.length() > 300) text = text.substring(0, 300) + "…";
      Tgx101Diag.mark("[" + (level <= Log.LEVEL_ERROR ? "E" : "W") + (tag != null ? "/" + tag : "") + "] " + text + (t != null ? " " + t : ""));
    }
  }

  private static float downX, downY;
  private static long downTime;
  private static float maxDistance;

  private static int dp (float px) {
    return Math.round(px / Screen.density());
  }

  /** Touches: position and gesture shape only (dp), to replay taps and swipes that went wrong */
  public static void onTouch (MotionEvent e, Object topController) {
    if (!BuildConfig.TGX101_DIAG) {
      return;
    }
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downX = e.getRawX();
        downY = e.getRawY();
        downTime = e.getEventTime();
        maxDistance = 0;
        break;
      case MotionEvent.ACTION_MOVE:
        maxDistance = Math.max(maxDistance, Math.abs(e.getRawX() - downX) + Math.abs(e.getRawY() - downY));
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
        String kind = maxDistance < Screen.dp(8) ? (e.getEventTime() - downTime >= 400 ? "long press" : "tap") : "swipe";
        Tgx101Diag.mark("touch " + kind + (e.getActionMasked() == MotionEvent.ACTION_CANCEL ? " (cancelled)" : "") +
          " at " + dp(downX) + "," + dp(downY) + "dp" +
          (kind.equals("swipe") ? " by " + dp(dx) + "," + dp(dy) + "dp" : "") +
          " " + (e.getEventTime() - downTime) + "ms" +
          (topController != null ? " on " + topController.getClass().getSimpleName() : ""));
        break;
      }
    }
  }
}
