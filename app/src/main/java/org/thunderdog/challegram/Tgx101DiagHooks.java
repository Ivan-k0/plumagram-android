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
          String extra = "";
          if (call.state instanceof TdApi.CallStateDiscarded) {
            TdApi.CallStateDiscarded d = (TdApi.CallStateDiscarded) call.state;
            extra = " reason " + (d.reason != null ? d.reason.getClass().getSimpleName() : "null") + (d.needDebugInformation ? " needDebug" : "");
          } else if (call.state instanceof TdApi.CallStateError) {
            TdApi.Error e = ((TdApi.CallStateError) call.state).error;
            extra = " error " + e.code + " " + e.message;
          }
          Tgx101Diag.mark("td call " + call.id + " user " + call.userId + (call.isOutgoing ? " out " : " in ") + call.state.getClass().getSimpleName() + (call.isVideo ? " video" : "") + extra);
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
      // a screen is focused once its opening animation is over: ~300 ms is the animation itself
      Tgx101Diag.mark((focused ? "screen → " : "screen ← ") + controller.getClass().getSimpleName() + (focused ? response("screen " + controller.getClass().getSimpleName(), 450) : ""));
      if (focused) Tgx101Diag.setTopScreen(controller.getClass().getSimpleName());
    }
  }

  /** How the app was opened again: a notification, a share, a link (action and extra names only) */
  public static void onNewIntent (android.content.Intent intent) {
    if (BuildConfig.TGX101_DIAG && intent != null) {
      android.os.Bundle extras = intent.getExtras();
      android.net.Uri data = intent.getData();
      Tgx101Diag.mark("new intent " + intent.getAction() + (data != null ? " " + data.getScheme() + "://" + data.getHost() : "") + (extras != null ? " extras " + extras.keySet() : ""));
    }
  }

  public static void onKeyboard (boolean visible) {
    if (BuildConfig.TGX101_DIAG) {
      Tgx101Diag.mark("keyboard " + (visible ? "shown" : "hidden") + (visible ? response("keyboard", 250) : ""));
    }
  }

  public static void onPopup (Object popup, Object content, boolean shown) {
    if (BuildConfig.TGX101_DIAG) {
      String name = content != null ? content.getClass().getSimpleName() : popup.getClass().getSimpleName();
      Tgx101Diag.mark("popup " + (shown ? "shown " : "removed ") + name + (shown ? response("popup " + name, 150) : ""));
      // (user 2026-11-04 «логи всё пишут?»): JANK sees only the activity's window, a popup has its own — every frame while it comes in
      if (shown) Tgx101Diag.watchMainFrames("popup open " + name, 500);
    }
  }

  public static void onBack (String source) {
    if (BuildConfig.TGX101_DIAG) {
      onInputAction();
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

  // TGx101 (user 2026-10-28 «наше приложение стало работать хуже оригинала»): response time — from the finger leaving the
  // screen to what it set off (a menu, a screen, the keyboard). Normal use fills the log; slow ones get their own line
  private static long lastUpAt;

  /** An action that didn't come through our touch (the system back gesture, a key): the response is timed from now */
  public static void onInputAction () {
    if (BuildConfig.TGX101_DIAG) lastUpAt = android.os.SystemClock.uptimeMillis();
  }

  private static String response (String what, long slowMs) {
    if (lastUpAt == 0) return "";
    long ms = android.os.SystemClock.uptimeMillis() - lastUpAt;
    if (ms > 1500) return "";
    if (ms > slowMs) Tgx101Diag.mark("SLOW RESPONSE: " + what + " " + ms + " ms after the finger left (norm ≤ " + slowMs + ")");
    return " (+" + ms + " ms after the finger)";
  }
  private static float maxDistance;

  private static int dp (float px) {
    return Math.round(px / Screen.density());
  }

  /**
   * (4PDA 2026-10-29 … 11-02 «весь экран с чатами уезжает влево, пропадает половина»): where a screen stands if it's off
   * its place — its left edge on the screen, and every view up to the window moved, scrolled, faded or scaled. Empty
   * when all is in place
   */
  public static String offPlace (android.view.View v) {
    if (v == null) return "";
    StringBuilder b = new StringBuilder();
    int[] at = new int[2];
    v.getLocationOnScreen(at);
    if (Math.abs(at[0]) > Screen.dp(2)) b.append("left edge at ").append(dp(at[0])).append("dp");
    if (!v.isShown()) b.append(b.length() > 0 ? ", " : "").append("not shown");
    for (android.view.View p = v; p != null; p = p.getParent() instanceof android.view.View ? (android.view.View) p.getParent() : null) {
      boolean moved = Math.abs(p.getTranslationX()) >= 1f || Math.abs(p.getTranslationY()) >= 1f;
      boolean scrolled = p.getScrollX() != 0 && !(p instanceof androidx.viewpager.widget.ViewPager || p instanceof org.thunderdog.challegram.widget.ViewPager);
      boolean faded = p.getAlpha() < .99f;
      boolean scaled = Math.abs(p.getScaleX() - 1f) > .01f;
      if (moved || scrolled || faded || scaled) {
        b.append(b.length() > 0 ? "; " : "").append(p.getClass().getSimpleName());
        if (moved) b.append(" moved ").append(dp(p.getTranslationX())).append(",").append(dp(p.getTranslationY())).append("dp");
        if (scrolled) b.append(" scrolled ").append(dp(p.getScrollX())).append("dp");
        if (faded) b.append(" alpha ").append(Math.round(p.getAlpha() * 100)).append("%");
        if (scaled) b.append(" scale ").append(Math.round(p.getScaleX() * 100)).append("%");
      }
    }
    return b.toString();
  }

  /**
   * (4PDA 2026-11-02 «добавил 2 локальных и попробовал (безуспешно) между ними попереключаться» — not a line about proxies
   * in the journal): a proxy by its kind only — the type, and the port of one on this phone. Never a server's address
   */
  /** An error text without the addresses in it (IPs, host names with a port) — journals are posted in public */
  public static String noAddress (String text) {
    if (text == null) return "";
    return text.replaceAll("\\[?[0-9a-fA-F:.]*\\d[0-9a-fA-F:.]*\\]?:\\d+|\\b\\d{1,3}(\\.\\d{1,3}){3}\\b|[\\w-]+(\\.[\\w-]+)+\\.[a-zA-Z]{2,}", "<address>");
  }

  public static String proxyKind (@androidx.annotation.Nullable TdApi.Proxy p) {
    if (p == null) return "none";
    String type = p.type instanceof TdApi.ProxyTypeSocks5 ? "socks5" : p.type instanceof TdApi.ProxyTypeHttp ? "http" : p.type instanceof TdApi.ProxyTypeMtproto ? "mtproto" : "?";
    String host = p.server != null ? p.server.trim().toLowerCase(java.util.Locale.ROOT) : "";
    boolean local = host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1") || host.equals("[::1]") || host.equals("0.0.0.0");
    return type + (local ? " on this phone :" + p.port : " remote");
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
        Tgx101Diag.watchTouch(true, topController != null ? topController.getClass().getSimpleName() : "?");
        break;
      case MotionEvent.ACTION_MOVE:
        maxDistance = Math.max(maxDistance, Math.abs(e.getRawX() - downX) + Math.abs(e.getRawY() - downY));
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) lastUpAt = android.os.SystemClock.uptimeMillis();
        Tgx101Diag.watchTouch(false, topController != null ? topController.getClass().getSimpleName() : "?");
        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
        String kind = maxDistance < Screen.dp(8) ? (e.getEventTime() - downTime >= 400 ? "long press" : "tap") : "swipe";
        Tgx101Diag.mark("touch " + kind + (e.getActionMasked() == MotionEvent.ACTION_CANCEL ? " (cancelled)" : "") +
          " at " + dp(downX) + "," + dp(downY) + "dp" +
          (kind.equals("swipe") ? " by " + dp(dx) + "," + dp(dy) + "dp" : "") +
          " " + (e.getEventTime() - downTime) + "ms" +
          (topController != null ? " on " + topController.getClass().getSimpleName() : ""));
        // a sideways swipe: was the screen under it where it should be? (4PDA: swipes that did nothing on a shifted screen)
        if (kind.equals("swipe") && Math.abs(dx) > Math.abs(dy) * 2f && topController instanceof org.thunderdog.challegram.navigation.ViewController) {
          String off = offPlace(((org.thunderdog.challegram.navigation.ViewController<?>) topController).getWrapUnchecked());
          if (!off.isEmpty()) Tgx101Diag.mark("  screen under the swipe off its place: " + off);
        }
        break;
      }
    }
  }
}
