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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.unsorted.AppContext;

import java.util.HashSet;
import java.util.Set;

/**
 * TGx101 (user 2026-11-11 «если отправляются файлы или медиа — приложение спать не должно»):
 * while a file is being prepared (a video compressed, a photo scaled) or uploaded, the app holds the CPU
 * and runs as a foreground service with a quiet «Sending…» notification — whatever the keep-alive setting.
 * A 4K video sent to a chat at 17:17 was being compressed when the screen went off; the phone froze the app
 * for 9 minutes and the compression died with a muxer error on waking, the video never left.
 *
 * Fed by TDLib: file generation start/stop and file updates with an upload in progress, for every account.
 * Stops 8 s after the last one ends (generation hands over to the upload with a short gap), and never
 * holds longer than 2 h without a fresh sign of work.
 */
public class Tgx101SendAwake extends Service {
  private static final String CHANNEL_ID = "tgx101_sending";
  private static final int NOTIFICATION_ID = Integer.MAX_VALUE - 21;
  private static final long LINGER_MS = 8_000;
  private static final long WAKE_TIMEOUT_MS = 2 * 60 * 60 * 1000L;

  private static final Object sync = new Object();
  private static final Set<String> active = new HashSet<>();
  private static PowerManager.WakeLock wakeLock;
  private static boolean serviceAsked;
  private static long heldSince;
  private static Handler handler;

  private static final Runnable stopLater = Tgx101SendAwake::stopIfIdle;

  // TDLib hooks

  public static void onGenerationStart (int accountId, long generationId) {
    add("g" + accountId + ":" + generationId);
  }

  public static void onGenerationStop (int accountId, long generationId) {
    remove("g" + accountId + ":" + generationId);
  }

  public static void onFile (int accountId, TdApi.File file) {
    String key = "u" + accountId + ":" + file.id;
    if (file.remote.isUploadingActive) {
      add(key);
    } else {
      remove(key);
    }
  }

  // State

  private static Handler handler () {
    if (handler == null) {
      handler = new Handler(Looper.getMainLooper());
    }
    return handler;
  }

  private static void add (String key) {
    boolean first;
    synchronized (sync) {
      if (!active.add(key)) {
        return;
      }
      first = active.size() == 1;
      handler().removeCallbacks(stopLater);
      acquire();
    }
    if (first) {
      org.thunderdog.challegram.Tgx101Diag.mark("sending awake: on (" + key + ")");
      startService();
    }
  }

  private static void remove (String key) {
    synchronized (sync) {
      if (!active.remove(key)) {
        return;
      }
      if (active.isEmpty()) {
        handler().removeCallbacks(stopLater);
        handler().postDelayed(stopLater, LINGER_MS);
      }
    }
  }

  private static void stopIfIdle () {
    long held;
    synchronized (sync) {
      if (!active.isEmpty()) {
        return;
      }
      held = heldSince != 0 ? SystemClock.elapsedRealtime() - heldSince : 0;
      heldSince = 0;
      if (wakeLock != null && wakeLock.isHeld()) {
        try {
          wakeLock.release();
        } catch (Throwable t) {
          Log.e("Cannot release the sending wake lock", t);
        }
      }
      serviceAsked = false;
    }
    org.thunderdog.challegram.Tgx101Diag.mark("sending awake: off after " + (held / 1000) + " s");
    Context context = AppContext.get();
    try {
      context.stopService(new Intent(context, Tgx101SendAwake.class));
    } catch (Throwable ignored) { }
  }

  @SuppressWarnings("WakelockTimeout")
  private static void acquire () {
    try {
      if (wakeLock == null) {
        PowerManager pm = (PowerManager) AppContext.get().getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
          return;
        }
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tgx:sending");
        wakeLock.setReferenceCounted(false);
      }
      // re-acquiring a non-counted lock moves its timeout on: each new sign of work gives another 2 h
      wakeLock.acquire(WAKE_TIMEOUT_MS);
      if (heldSince == 0) {
        heldSince = SystemClock.elapsedRealtime();
      }
    } catch (Throwable t) {
      Log.e("Cannot acquire the sending wake lock", t);
    }
  }

  private static void startService () {
    synchronized (sync) {
      if (serviceAsked) {
        return;
      }
      serviceAsked = true;
    }
    Context context = AppContext.get();
    try {
      ContextCompat.startForegroundService(context, new Intent(context, Tgx101SendAwake.class));
    } catch (Throwable t) {
      // Android 12+ refuses a foreground start from the background without the battery exemption:
      // the wake lock still keeps the CPU on
      synchronized (sync) {
        serviceAsked = false;
      }
      org.thunderdog.challegram.Tgx101Diag.mark("sending awake: no foreground service — " + t.getClass().getSimpleName());
    }
  }

  // Service

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    try {
      postNotification();
    } catch (Throwable t) {
      org.thunderdog.challegram.Tgx101Diag.mark("sending awake: notification refused — " + t.getClass().getSimpleName());
      synchronized (sync) {
        serviceAsked = false;
      }
      stopSelf();
      return START_NOT_STICKY;
    }
    boolean idle;
    synchronized (sync) {
      idle = active.isEmpty();
    }
    if (idle) {
      handler().removeCallbacks(stopLater);
      handler().postDelayed(stopLater, LINGER_MS);
    }
    return START_NOT_STICKY;
  }

  private void postNotification () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, Lang.getString(R.string.Tgx101SendingChannel), NotificationManager.IMPORTANCE_MIN);
      channel.setShowBadge(false);
      NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.createNotificationChannel(channel);
      }
    }
    Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.baseline_arrow_upward_24)
      .setContentTitle(Lang.getString(R.string.Tgx101SendingTitle))
      .setSilent(true)
      .setLocalOnly(true)
      .setPriority(NotificationCompat.PRIORITY_MIN)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setShowWhen(false)
      .setOngoing(true)
      .setProgress(0, 0, true)
      .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), Intents.mutabilityFlags(false)))
      .build();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    } else {
      startForeground(NOTIFICATION_ID, notification);
    }
  }

  @Override
  public void onTimeout (int startId, int fgsType) {
    // Android 15 limits dataSync to 6 h a day: let it go, the wake lock stays until the work ends
    org.thunderdog.challegram.Tgx101Diag.mark("sending awake: the system ended the service (time limit)");
    stopSelf();
  }

  @Override
  public void onDestroy () {
    synchronized (sync) {
      serviceAsked = false;
    }
    super.onDestroy();
  }

  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }
}
