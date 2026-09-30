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
package org.thunderdog.challegram.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import org.thunderdog.challegram.Tgx101Diag;

/**
 * TGx101: some lock screens (Vivo) show only notifications posted after the screen was locked, so unread
 * message notifications posted earlier are missing there. When the screen goes off they are posted again,
 * unchanged and silently (FLAG_ONLY_ALERT_ONCE: no sound, vibration or pop-up).
 */
public final class Tgx101LockScreenNotifications {
  private Tgx101LockScreenNotifications () { }

  private static boolean registered;

  public static void register (Context context) {
    if (registered || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return;
    }
    registered = true;
    context.getApplicationContext().registerReceiver(new BroadcastReceiver() {
      @Override
      public void onReceive (Context context, Intent intent) {
        repost(context);
      }
    }, new IntentFilter(Intent.ACTION_SCREEN_OFF));
  }

  private static void repost (Context context) {
    try {
      NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager == null) {
        return;
      }
      StatusBarNotification[] active = manager.getActiveNotifications();
      int count = 0;
      // Children first, group summaries last, so the system keeps the grouping
      for (int pass = 0; pass < 2; pass++) {
        for (StatusBarNotification sbn : active) {
          Notification notification = sbn.getNotification();
          if (!Notification.CATEGORY_MESSAGE.equals(notification.category)) {
            continue; // calls, services, downloads
          }
          boolean isSummary = (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
          if (isSummary != (pass == 1)) {
            continue;
          }
          notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
          manager.notify(sbn.getTag(), sbn.getId(), notification);
          count++;
        }
      }
      Tgx101Diag.mark("lock screen: re-posted " + count + " message notifications");
    } catch (Throwable t) {
      Tgx101Diag.mark("lock screen re-post failed: " + t);
    }
  }
}
