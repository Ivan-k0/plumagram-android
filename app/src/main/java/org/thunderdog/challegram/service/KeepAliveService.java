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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Keeps TDLib connected in background so new messages arrive without Firebase push.
 * Needed because the bundled google-services.json only accepts the official Telegram X signature.
 *
 * Runs as a foreground service (otherwise Android kills it within a minute), but its notification
 * uses an IMPORTANCE_MIN channel: no status bar icon, one collapsed line at the bottom of the shade.
 * Gated by {@link Settings#SETTING_FLAG_KEEP_ALIVE_CONNECTION}.
 */
public class KeepAliveService extends Service {
  private static final String CHANNEL_ID = "keep_alive";
  private static final int NOTIFICATION_ID = Integer.MAX_VALUE - 20;

  /** Starts, refreshes (picks up newly logged in accounts) or stops the service according to the setting. */
  public static void sync (Context context) {
    Context appContext = context.getApplicationContext();
    Intent intent = new Intent(appContext, KeepAliveService.class);
    try {
      if (Settings.instance().getNewSetting(Settings.SETTING_FLAG_KEEP_ALIVE_CONNECTION)) {
        ContextCompat.startForegroundService(appContext, intent);
      } else {
        appContext.stopService(intent);
      }
    } catch (Throwable t) {
      // e.g. ForegroundServiceStartNotAllowedException when called from background without battery exemption
      Log.e("Unable to sync keep-alive service", t);
    }
  }

  public static boolean isIgnoringBatteryOptimizations (Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return true;
    }
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
  }

  /** Shows the system "Let app always run in background?" dialog, if not granted yet. */
  @SuppressLint("BatteryLife")
  public static void requestIgnoreBatteryOptimizations (Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isIgnoringBatteryOptimizations(context)) {
      return;
    }
    try {
      Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
      intent.setData(Uri.parse("package:" + context.getPackageName()));
      if (!(context instanceof android.app.Activity)) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      }
      context.startActivity(intent);
    } catch (Throwable t) {
      Log.e("Unable to request battery optimization exemption", t);
    }
  }

  /** Asks for the battery exemption once, since the setting is on by default and may never be toggled. */
  public static void requestIgnoreBatteryOptimizationsOnce (Context context) {
    if (!Settings.instance().getNewSetting(Settings.SETTING_FLAG_KEEP_ALIVE_CONNECTION) || isIgnoringBatteryOptimizations(context)) {
      return;
    }
    android.content.SharedPreferences prefs = context.getSharedPreferences("keep_alive", Context.MODE_PRIVATE);
    if (prefs.getBoolean("battery_asked", false)) {
      return;
    }
    prefs.edit().putBoolean("battery_asked", true).apply();
    requestIgnoreBatteryOptimizations(context);
  }

  // accountId -> tdlib we hold a keep-alive reference on
  private final Map<Integer, Tdlib> heldAccounts = new HashMap<>();

  @Override
  public void onCreate () {
    super.onCreate();
    UI.initApp(getApplicationContext());
  }

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    try {
      postNotification();
    } catch (Throwable t) {
      Log.e("Unable to start keep-alive foreground service", t);
      stopSelf();
      return START_NOT_STICKY;
    }
    if (!Settings.instance().getNewSetting(Settings.SETTING_FLAG_KEEP_ALIVE_CONNECTION)) {
      stopSelf();
      return START_NOT_STICKY;
    }
    syncAccounts();
    return START_STICKY;
  }

  private void syncAccounts () {
    Set<Integer> authorized = new HashSet<>();
    for (TdlibAccount account : TdlibManager.instance()) {
      int mode = account.tdlibInstanceMode();
      if ((mode == Tdlib.Mode.NORMAL || mode == Tdlib.Mode.DEBUG) && !account.isUnauthorized()) {
        authorized.add(account.id);
        if (!heldAccounts.containsKey(account.id)) {
          Tdlib tdlib = account.tdlib();
          tdlib.incrementKeepAliveReferenceCount();
          heldAccounts.put(account.id, tdlib);
        }
      }
    }
    Iterator<Map.Entry<Integer, Tdlib>> it = heldAccounts.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Integer, Tdlib> entry = it.next();
      if (!authorized.contains(entry.getKey())) {
        entry.getValue().decrementKeepAliveReferenceCount();
        it.remove();
      }
    }
  }

  private void postNotification () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, Lang.getString(R.string.KeepAliveChannel), NotificationManager.IMPORTANCE_MIN);
      channel.setShowBadge(false);
      NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.createNotificationChannel(channel);
      }
    }
    Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.baseline_sync_white_24)
      .setContentTitle(Lang.getString(R.string.KeepAliveNotification))
      .setPriority(NotificationCompat.PRIORITY_MIN)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setShowWhen(false)
      .setOngoing(true)
      .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), Intents.mutabilityFlags(false)))
      .build();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    } else {
      startForeground(NOTIFICATION_ID, notification);
    }
  }

  @Override
  public void onDestroy () {
    for (Tdlib tdlib : heldAccounts.values()) {
      tdlib.decrementKeepAliveReferenceCount();
    }
    heldAccounts.clear();
    super.onDestroy();
  }

  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }
}
