/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/tgx101-android)
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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.voip.Tgx101GroupCall;

/**
 * TGx101: keeps the microphone working while a voice chat is open and the app is in the background.
 * Runs only while the voice chat is open.
 */
public class Tgx101GroupCallService extends Service {
  public static final String ACTION_OPEN = org.thunderdog.challegram.BuildConfig.APPLICATION_ID + ".TGX101_OPEN_VOICE_CHAT";
  private static final String ACTION_LEAVE = org.thunderdog.challegram.BuildConfig.APPLICATION_ID + ".TGX101_LEAVE_VOICE_CHAT";
  private static final String CHANNEL_ID = "tgx101_voice_chat";
  private static final int NOTIFICATION_ID = 0x7101;

  public static void start (Context context) {
    try {
      ContextCompat.startForegroundService(context, new Intent(context, Tgx101GroupCallService.class));
    } catch (Throwable ignored) { }
  }

  public static void stop (Context context) {
    context.stopService(new Intent(context, Tgx101GroupCallService.class));
  }

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    if (intent != null && ACTION_LEAVE.equals(intent.getAction())) {
      UI.post(() -> {
        Tgx101GroupCall call = Tgx101GroupCall.current();
        if (call != null) {
          call.leave();
        }
      });
      stopSelf();
      return START_NOT_STICKY;
    }
    Tgx101GroupCall call = Tgx101GroupCall.current();
    if (call == null) {
      stopSelf();
      return START_NOT_STICKY;
    }
    postNotification(call.getTitle());
    return START_NOT_STICKY;
  }

  private void postNotification (String title) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, Lang.getString(R.string.Tgx101VoiceChat), NotificationManager.IMPORTANCE_LOW);
      channel.setShowBadge(false);
      NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.createNotificationChannel(channel);
      }
    }
    Intent open = new Intent(this, MainActivity.class).setAction(ACTION_OPEN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    Intent leave = new Intent(this, Tgx101GroupCallService.class).setAction(ACTION_LEAVE);
    Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.baseline_mic_24)
      .setContentTitle(Lang.getString(R.string.Tgx101VoiceChat))
      .setContentText(title)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setShowWhen(false)
      .setOngoing(true)
      .setContentIntent(PendingIntent.getActivity(this, 0, open, Intents.mutabilityFlags(false)))
      .addAction(R.drawable.baseline_call_end_24, Lang.getString(R.string.Tgx101VoiceChatLeave), PendingIntent.getService(this, 1, leave, Intents.mutabilityFlags(false)))
      .build();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    } else {
      startForeground(NOTIFICATION_ID, notification);
    }
  }

  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }
}
