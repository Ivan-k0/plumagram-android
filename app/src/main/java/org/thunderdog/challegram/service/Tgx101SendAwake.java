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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
  // TGx101 (22:39 the same day: vivo's fast freezer froze the app 5 s after the screen went off, the minimal notification
  // didn't keep it): an ordinary «Sending» notification with a progress bar, as a sending app shows — a new channel id,
  // since a channel's importance can't be raised once made
  private static final String CHANNEL_ID = "tgx101_sending_progress";
  private static final int NOTIFICATION_ID = Integer.MAX_VALUE - 21;
  private static final long LINGER_MS = 8_000;
  private static final long WAKE_TIMEOUT_MS = 2 * 60 * 60 * 1000L;

  private static final Object sync = new Object();
  private static final Set<String> active = new HashSet<>();
  private static final Map<String, Float> progress = new HashMap<>();
  // TGx101 (user 2026-11-12 «для голосовых и кружков системное уведомление об отправке не нужно»): their preparing and
  // upload keep the CPU but show no notification
  private static final Set<String> quiet = new HashSet<>();
  private static final long NOTIFY_AFTER_MS = 700;
  private static final Runnable startLater = Tgx101SendAwake::startIfLoud;

  // TGx101 (user 2026-11-12 «при нажатии на уведомление должно отправлять в тот чат и к тому видео»): the media
  // messages being sent, newest last — a tap on the notification opens the newest one's chat at it
  private static final java.util.ArrayList<long[]> sending = new java.util.ArrayList<>(); // {accountId, chatId, messageId}

  public static void onSendingMedia (int accountId, TdApi.Message message) {
    if (message == null || message.content == null) return;
    // voice notes and round videos sent as a file of their own (an edited voice note) are quiet too
    if (message.content.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR) {
      markQuietFile(accountId, ((TdApi.MessageVoiceNote) message.content).voiceNote.voice.id);
      return;
    } else if (message.content.getConstructor() == TdApi.MessageVideoNote.CONSTRUCTOR) {
      markQuietFile(accountId, ((TdApi.MessageVideoNote) message.content).videoNote.video.id);
      return;
    }
    switch (message.content.getConstructor()) {
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageAnimation.CONSTRUCTOR:
      case TdApi.MessageDocument.CONSTRUCTOR:
      case TdApi.MessageAudio.CONSTRUCTOR:
        break;
      default:
        return;
    }
    if (message.content.getConstructor() == TdApi.MessageVideo.CONSTRUCTOR) {
      warnAboutBackgroundOnce();
    }
    synchronized (sync) {
      sending.add(new long[] {accountId, message.chatId, message.id});
      if (sending.size() > 50) sending.remove(0);
    }
    refreshNotification();
  }

  public static void onSendingDone (int accountId, long chatId, long oldMessageId, long newMessageId, boolean succeeded) {
    synchronized (sync) {
      for (int i = sending.size() - 1; i >= 0; i--) {
        long[] m = sending.get(i);
        if (m[0] == accountId && m[1] == chatId && m[2] == oldMessageId) {
          sending.remove(i);
        }
      }
    }
    refreshNotification();
  }

  /**
   * TGx101 (user 2026-11-12 «один раз можно об этом предупредить и больше не нужно»): vivo's fast freezer stops a
   * video's compression with the screen off unless the app may run in the background (proven on the user's phone the
   * same day) — the first video sent without that permission says so once, with a button to the system's ask
   */
  private static void warnAboutBackgroundOnce () {
    Context context = AppContext.get();
    if (KeepAliveService.isIgnoringBatteryOptimizations(context)) return;
    android.content.SharedPreferences prefs = context.getSharedPreferences("tgx101_sending", Context.MODE_PRIVATE);
    if (prefs.getBoolean("background_warned", false)) return;
    prefs.edit().putBoolean("background_warned", true).apply();
    org.thunderdog.challegram.tool.UI.post(() -> {
      org.thunderdog.challegram.navigation.ViewController<?> c = org.thunderdog.challegram.tool.UI.getCurrentStackItem();
      if (c == null) return;
      c.showOptions(Lang.getString(R.string.Tgx101SendingBackgroundInfo), new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.Tgx101SendingBackgroundAllow), Lang.getString(R.string.Tgx101SendingBackgroundLater)}, null, new int[] {R.drawable.baseline_check_24, R.drawable.baseline_cancel_24}, (v, id) -> {
        if (id == R.id.btn_done) {
          KeepAliveService.requestIgnoreBatteryOptimizations(c.context());
        }
        return true;
      });
    });
  }

  private static void refreshNotification () {
    handler().post(() -> {
      Tgx101SendAwake service = instance;
      if (service != null) {
        try {
          service.postNotification();
        } catch (Throwable ignored) { }
      }
    });
  }

  private PendingIntent contentIntent () {
    long[] target;
    synchronized (sync) {
      target = sending.isEmpty() ? null : sending.get(sending.size() - 1);
    }
    if (target != null) {
      try {
        org.thunderdog.challegram.telegram.Tdlib tdlib = org.thunderdog.challegram.telegram.TdlibManager.getTdlib((int) target[0]);
        long localChatId = tdlib.settings().getLocalChatId(target[1]);
        Intent intent = Intents.valueOfLocalChatId((int) target[0], localChatId, target[2]);
        return PendingIntent.getActivity(this, (int) (target[2] & 0x7fffffff), intent, PendingIntent.FLAG_UPDATE_CURRENT | Intents.mutabilityFlags(false));
      } catch (Throwable t) {
        Log.e("Sending notification: no chat intent", t);
      }
    }
    return PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), Intents.mutabilityFlags(false));
  }
  private static Tgx101SendAwake instance;
  private static long lastNotifyMs;
  private static PowerManager.WakeLock wakeLock;
  private static boolean serviceAsked;
  private static long heldSince;
  private static Handler handler;

  private static final Runnable stopLater = Tgx101SendAwake::stopIfIdle;

  // TDLib hooks

  public static void onGenerationStart (int accountId, long generationId, String conversion) {
    String key = "g" + accountId + ":" + generationId;
    if (isQuietConversion(conversion)) {
      synchronized (sync) {
        quiet.add(key);
      }
    }
    add(key);
  }

  public static boolean isQuietConversion (@Nullable String conversion) {
    return conversion != null && (conversion.startsWith("voice") || conversion.startsWith("round"));
  }

  /** a voice note's or a round video's file: its upload shows no notification */
  public static void markQuietFile (int accountId, int fileId) {
    synchronized (sync) {
      if (quiet.size() > 500) quiet.clear();
      quiet.add("u" + accountId + ":" + fileId);
    }
  }

  public static void onGenerationStop (int accountId, long generationId) {
    remove("g" + accountId + ":" + generationId);
  }

  public static void onFile (int accountId, TdApi.File file) {
    String key = "u" + accountId + ":" + file.id;
    if (file.remote.isUploadingActive) {
      add(key);
      long total = Math.max(file.expectedSize, file.size);
      if (total > 0) setProgress(key, (float) file.remote.uploadedSize / total);
    } else {
      remove(key);
    }
  }

  /** a video's compression, 0..1 */
  public static void onGenerationProgress (int accountId, long generationId, double value) {
    setProgress("g" + accountId + ":" + generationId, (float) value);
  }

  private static void setProgress (String key, float value) {
    synchronized (sync) {
      if (!active.contains(key)) return;
      progress.put(key, Math.max(0f, Math.min(1f, value)));
    }
    long now = SystemClock.elapsedRealtime();
    if (now - lastNotifyMs < 1000) return;
    lastNotifyMs = now;
    handler().post(() -> {
      Tgx101SendAwake service = instance;
      if (service != null) {
        try {
          service.postNotification();
        } catch (Throwable ignored) { }
      }
    });
  }

  // TGx101: what the notification says — a compression first (the slowest one), then the uploads together
  private static float[] state () {
    synchronized (sync) {
      float gen = -1, upSum = 0;
      int ups = 0;
      for (String key : active) {
        Float p = progress.get(key);
        if (quiet.contains(key)) continue;
        if (key.startsWith("g")) {
          float v = p != null ? p : 0f;
          gen = gen < 0 ? v : Math.min(gen, v);
        } else {
          upSum += p != null ? p : 0f;
          ups++;
        }
      }
      if (gen >= 0) return new float[] {1, gen};
      if (ups > 0) return new float[] {0, upSum / ups};
      return new float[] {0, -1};
    }
  }

  /** TGx101: runs when the screen is on — at once if it is, else on the next SCREEN_ON (a frozen app may not compress) */
  public static void whenScreenOn (Runnable r) {
    Context context = AppContext.get();
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    // isInteractive is Android 4.4W+; isScreenOn before it (lint before 0.2.981)
    if (pm == null || (android.os.Build.VERSION.SDK_INT >= 20 ? pm.isInteractive() : pm.isScreenOn())) {
      r.run();
      return;
    }
    android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
      @Override
      public void onReceive (Context c, Intent intent) {
        try {
          c.getApplicationContext().unregisterReceiver(this);
        } catch (Throwable ignored) { }
        r.run();
      }
    };
    try {
      // (Android 4 builds: their older androidx has no ContextCompat.RECEIVER_NOT_EXPORTED — lint before 0.2.980) the platform
      // flag on 13+, plain before
      android.content.IntentFilter filter = new android.content.IntentFilter(Intent.ACTION_SCREEN_ON);
      if (android.os.Build.VERSION.SDK_INT >= 33) {
        context.getApplicationContext().registerReceiver(receiver, filter, 0x4 /* Context.RECEIVER_NOT_EXPORTED */);
      } else {
        context.getApplicationContext().registerReceiver(receiver, filter);
      }
    } catch (Throwable t) {
      r.run();
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
    }
    // the notification waits a moment: a voice note's upload starts before its file is known as a voice note's
    handler().removeCallbacks(startLater);
    handler().postDelayed(startLater, NOTIFY_AFTER_MS);
  }

  private static void startIfLoud () {
    boolean loud = false;
    synchronized (sync) {
      for (String k : active) {
        if (!quiet.contains(k)) {
          loud = true;
          break;
        }
      }
    }
    if (loud) startService();
  }

  private static void remove (String key) {
    synchronized (sync) {
      if (!active.remove(key)) {
        return;
      }
      progress.remove(key);
      // the quiet mark stays: a voice note's upload stops and starts again as its file grows while recording, and the next
      // start came back «loud» (user 2026-11-12 «отправка голосового до сих пор висит в системных уведомлениях»)
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
  public void onCreate () {
    super.onCreate();
    instance = this;
  }

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
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, Lang.getString(R.string.Tgx101SendingChannel), NotificationManager.IMPORTANCE_LOW);
      channel.setShowBadge(false);
      NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.createNotificationChannel(channel);
      }
    }
    float[] st = state();
    int percent = st[1] >= 0 ? Math.round(st[1] * 100f) : -1;
    String title = Lang.getString(st[0] == 1 ? R.string.Tgx101SendingPreparing : R.string.Tgx101SendingTitle);
    Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.baseline_arrow_upward_24)
      .setContentTitle(percent >= 0 ? title + " · " + percent + "%" : title)
      .setSilent(true)
      .setOnlyAlertOnce(true)
      .setLocalOnly(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setShowWhen(false)
      .setOngoing(true)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .setProgress(100, Math.max(0, percent), percent < 0)
      .setContentIntent(contentIntent())
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
    if (instance == this) instance = null;
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
