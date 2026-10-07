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

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TGx101: diagnostics log for the developer's test builds. Only in builds made with -Ptgx101Diag=true,
 * public builds skip everything here. Writes PlumaGram-diagnostics.txt to Download: startup, crashes,
 * previous process exit reason, activity lifecycle, screen on/off/unlock, memory trims, UI stalls
 * (with the main thread stack) and every event other classes report through {@link #mark}.
 * Writing happens on a background thread in batches; the file is rotated at {@link #MAX_SIZE}
 * (previous part kept as PlumaGram-diagnostics-old.txt). No message texts, names or numbers: IDs and types only.
 */
public final class Tgx101Diag {
  private Tgx101Diag () { }

  // the test build (-Ptgx101Test, «PlumaGram Т») installs next to the main app — its own log file
  private static final String FILE_PREFIX = "com.plumagram.app".equals(BuildConfig.APPLICATION_ID) ? "PlumaGram" : "PlumaGram-T";
  private static final String FILE_NAME = FILE_PREFIX + "-diagnostics.txt";
  private static final String OLD_FILE_NAME = FILE_PREFIX + "-diagnostics-old.txt";
  private static final long MAX_SIZE = 8L * 1024 * 1024;
  private static final long STALL_MS = 100, STALL_STACK_MS = 80;

  private static File file;
  private static Uri uri; // Android 10+: the file in Download, visible to any file manager
  private static ContentResolver resolver;
  private static long size;

  private static final StringBuilder pending = new StringBuilder();
  private static Handler writer;

  public static void start (Context context) {
    if (!BuildConfig.TGX101_DIAG || file != null || uri != null) {
      return;
    }
    if (Build.VERSION.SDK_INT >= 29) {
      resolver = context.getContentResolver();
      uri = pickDownloadsUri(resolver, FILE_NAME);
    }
    if (uri == null) {
      file = pickFile(context);
    }
    size = currentSize();
    HandlerThread thread = new HandlerThread("TGx101Diag");
    thread.start();
    writer = new Handler(thread.getLooper());
    mark("\n===== Launch " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + " =====\n" +
      "App: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n" +
      "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ", " + Build.PRODUCT + ")\n" +
      "Android: " + Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT + "\n" +
      "ABI: " + Arrays.toString(abis()) + "\n" +
      "Fingerprint: " + Build.FINGERPRINT);
    logExitReasons(context);
    final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread1, error) -> {
      StringWriter trace = new StringWriter();
      error.printStackTrace(new PrintWriter(trace));
      synchronized (pending) {
        pending.append(time()).append("  CRASH in thread ").append(thread1.getName()).append(":\n").append(trace).append('\n');
      }
      flush(); // synchronously: the process dies right after
      if (previous != null) {
        previous.uncaughtException(thread1, error);
      }
    });
    // A native crash can't be caught here: the log of the previous launch shows it
    writer.post(Tgx101Diag::dumpSystemLog);
  }

  private static java.lang.ref.WeakReference<Activity> topActivity = new java.lang.ref.WeakReference<>(null);

  /**
   * Diagnostics builds only: `adb shell am broadcast -a com.plumagram.app.DIAG_TEXT --es text "…"` types the text into
   * the focused input (adb can't type Cyrillic). Only senders holding DUMP (the adb shell) can send it.
   */
  private static void registerTextInput (Application app) {
    android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
      @Override
      public void onReceive (android.content.Context context, android.content.Intent intent) {
        String text = intent.getStringExtra("text");
        if (text == null) return;
        Activity activity = topActivity.get();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
          android.view.View focus = activity.getCurrentFocus();
          if (focus instanceof android.widget.EditText) {
            android.widget.EditText edit = (android.widget.EditText) focus;
            int start = Math.max(0, edit.getSelectionStart()), end = Math.max(0, edit.getSelectionEnd());
            edit.getText().replace(Math.min(start, end), Math.max(start, end), text);
            mark("diag text input: " + text.length() + " chars");
          }
        });
      }
    };
    android.content.IntentFilter filter = new android.content.IntentFilter(app.getPackageName() + ".DIAG_TEXT");
    if (android.os.Build.VERSION.SDK_INT >= 33) {
      app.registerReceiver(receiver, filter, android.Manifest.permission.DUMP, null, android.content.Context.RECEIVER_EXPORTED);
    } else {
      app.registerReceiver(receiver, filter, android.Manifest.permission.DUMP, null);
    }
  }

  /** Diagnostics builds only: `adb shell am broadcast -a com.plumagram.app.DIAG_STORY --es user durov` opens that chat's
   * active stories in the story viewer (to test the viewer on an account whose strip is empty). DUMP-protected. */
  private static void registerStoryOpener (Application app) {
    android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
      @Override
      public void onReceive (android.content.Context context, android.content.Intent intent) {
        String user = intent.getStringExtra("user");
        if (user == null) return;
        org.thunderdog.challegram.telegram.Tdlib tdlib = org.thunderdog.challegram.telegram.TdlibManager.instance().current();
        tdlib.send(new org.drinkless.tdlib.TdApi.SearchPublicChat(user), (chat, error) -> {
          if (chat == null) { mark("diag story: no chat " + user); return; }
          tdlib.send(new org.drinkless.tdlib.TdApi.GetChatActiveStories(chat.id), (stories, error2) -> {
            int count = stories != null && stories.stories != null ? stories.stories.length : -1;
            mark("diag story: " + user + " has " + count + " active stories");
            if (count <= 0) return;
            Activity activity = topActivity.get();
            if (activity == null) return;
            activity.runOnUiThread(() -> new org.thunderdog.challegram.ui.Tgx101StoryViewer(activity, tdlib, java.util.Collections.singletonList(stories), 0, intent.getBooleanExtra("first", false)).show());
          });
        });
      }
    };
    android.content.IntentFilter filter = new android.content.IntentFilter(app.getPackageName() + ".DIAG_STORY");
    if (android.os.Build.VERSION.SDK_INT >= 33) {
      app.registerReceiver(receiver, filter, android.Manifest.permission.DUMP, null, android.content.Context.RECEIVER_EXPORTED);
    } else {
      app.registerReceiver(receiver, filter, android.Manifest.permission.DUMP, null);
    }
  }

  /** Lifecycle, screen, memory and UI stall tracking. Called from Application.onCreate. */
  public static void attach (Application app) {
    if (!BuildConfig.TGX101_DIAG || writer == null) {
      return;
    }
    registerTextInput(app);
    registerStoryOpener(app);
    app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
      private int resumed;
      @Override public void onActivityCreated (Activity a, Bundle state) { mark("activity " + a.getClass().getSimpleName() + " created" + (state != null ? " (restored)" : "")); }
      @Override public void onActivityStarted (Activity a) {
        mark("activity " + a.getClass().getSimpleName() + " started [" + state(a) + "]");
        StartWatch.onStarted(a);
      }
      @Override public void onActivityResumed (Activity a) {
        topActivity = new java.lang.ref.WeakReference<>(a);
        mark("activity " + a.getClass().getSimpleName() + " resumed [" + state(a) + "]");
        if (resumed++ == 0) StallWatch.setEnabled(true);
        FrameWatch.attach(a);
      }
      @Override public void onActivityPaused (Activity a) {
        mark("activity " + a.getClass().getSimpleName() + " paused [" + state(a) + "]");
        FrameWatch.detach(a);
        if (--resumed <= 0) { resumed = 0; StallWatch.setEnabled(false); }
      }
      @Override public void onActivityStopped (Activity a) { mark("activity " + a.getClass().getSimpleName() + " stopped [" + state(a) + (a.isChangingConfigurations() ? ", config change" : "") + "]"); }
      @Override public void onActivitySaveInstanceState (Activity a, Bundle outState) { }
      @Override public void onActivityDestroyed (Activity a) { mark("activity " + a.getClass().getSimpleName() + " destroyed"); }
    });
    app.registerComponentCallbacks(new ComponentCallbacks2() {
      @Override public void onTrimMemory (int level) { mark("trim memory level " + level); }
      @Override public void onConfigurationChanged (Configuration config) { mark("configuration changed: orientation " + config.orientation + ", night " + (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) + ", fontScale " + config.fontScale); }
      @Override public void onLowMemory () { mark("LOW MEMORY"); }
    });
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_SCREEN_ON);
    filter.addAction(Intent.ACTION_SCREEN_OFF);
    filter.addAction(Intent.ACTION_USER_PRESENT);
    filter.addAction(Intent.ACTION_POWER_CONNECTED);
    filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
    filter.addAction(Intent.ACTION_HEADSET_PLUG);
    try {
      app.registerReceiver(new BroadcastReceiver() {
        @Override
        public void onReceive (Context context, Intent intent) {
          String action = intent.getAction();
          if (action == null) return;
          String event = action.substring(action.lastIndexOf('.') + 1);
          if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
            event += " state=" + intent.getIntExtra("state", -1);
          }
          mark("system " + event);
        }
      }, filter);
    } catch (Throwable t) {
      mark("system receiver failed: " + t);
    }
    mark("diagnostics attached");
  }

  public static void mark (String step) {
    if (!BuildConfig.TGX101_DIAG || writer == null) {
      return;
    }
    boolean schedule;
    synchronized (pending) {
      schedule = pending.length() == 0;
      pending.append(time()).append("  ").append(step).append('\n');
    }
    if (schedule) {
      writer.postDelayed(Tgx101Diag::flush, 500);
    }
  }

  /** Current network, for the call log: "wifi", "mobile LTE", "none" */
  @SuppressWarnings("deprecation")
  public static String network () {
    if (!BuildConfig.TGX101_DIAG) {
      return "";
    }
    try {
      android.net.ConnectivityManager cm = (android.net.ConnectivityManager) org.thunderdog.challegram.unsorted.AppContext.get().getSystemService(Context.CONNECTIVITY_SERVICE);
      android.net.NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
      if (info == null || !info.isConnected()) {
        return "none";
      }
      return info.getTypeName().toLowerCase(Locale.US) + (info.getSubtypeName() != null && !info.getSubtypeName().isEmpty() ? " " + info.getSubtypeName() : "");
    } catch (Throwable t) {
      return "?";
    }
  }

  /** Runs a periodic task on the diagnostics thread (e.g. summaries) */
  public static void postDelayed (Runnable task, long delayMs) {
    if (BuildConfig.TGX101_DIAG && writer != null) {
      writer.postDelayed(task, delayMs);
    }
  }

  private static String time () {
    return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
  }

  // UI stalls: every main thread message longer than STALL_MS, with its stack when it lasts STALL_STACK_MS+

  private static final class StallWatch {
    private static volatile long dispatchStart; // 0 = idle
    private static volatile String stack;
    private static volatile boolean enabled;
    private static Thread sampler;

    static void setEnabled (boolean enable) {
      if (enabled == enable) return;
      enabled = enable;
      Looper main = Looper.getMainLooper();
      if (enable) {
        main.setMessageLogging(line -> {
          if (line.startsWith(">")) {
            stack = null;
            dispatchStart = SystemClock.uptimeMillis();
          } else if (dispatchStart != 0) {
            long duration = SystemClock.uptimeMillis() - dispatchStart;
            dispatchStart = 0;
            if (duration >= STALL_MS) {
              String s = stack;
              mark("UI STALL " + duration + " ms: " + line.replace("<<<<< Finished to ", "") + (s != null ? "\n" + s : ""));
            }
          }
        });
        if (sampler == null || !sampler.isAlive()) {
          sampler = new Thread(StallWatch::sample, "TGx101Stall");
          sampler.setDaemon(true);
          sampler.start();
        }
      } else {
        main.setMessageLogging(null);
        dispatchStart = 0;
      }
    }

    private static void sample () {
      Thread mainThread = Looper.getMainLooper().getThread();
      while (enabled) {
        try {
          Thread.sleep(25);
        } catch (InterruptedException e) {
          return;
        }
        long start = dispatchStart;
        if (start != 0 && stack == null && SystemClock.uptimeMillis() - start >= STALL_STACK_MS) {
          StackTraceElement[] trace = mainThread.getStackTrace();
          StringBuilder b = new StringBuilder("    main thread at:");
          for (int i = 0; i < Math.min(trace.length, 18); i++) {
            b.append("\n      ").append(trace[i]);
          }
          stack = b.toString();
        }
      }
    }
  }

  // Dropped frames: one line per second with janky frames, and the phases of the worst one.
  // unknownDelay = the frame waited for the main thread (busy with other work), anim/layout/draw = our own UI code.

  // Blink diagnostics: lock / screen / focus / rotation state at each lifecycle step
  private static volatile String topScreen = "?";

  static void setTopScreen (String name) {
    topScreen = name;
  }

  private static String state (Activity a) {
    StringBuilder b = new StringBuilder();
    try {
      android.app.KeyguardManager keyguard = (android.app.KeyguardManager) a.getSystemService(Context.KEYGUARD_SERVICE);
      android.os.PowerManager power = (android.os.PowerManager) a.getSystemService(Context.POWER_SERVICE);
      b.append(keyguard != null && keyguard.isKeyguardLocked() ? "locked" : "unlocked");
      b.append(power != null && isInteractive(power) ? ", screen on" : ", screen off");
      b.append(a.hasWindowFocus() ? ", focus" : ", no focus");
      android.view.Display display = a.getWindowManager().getDefaultDisplay();
      b.append(", rotation ").append(display.getRotation() * 90);
      b.append(", top ").append(topScreen);
    } catch (Throwable t) {
      b.append("state failed");
    }
    return b.toString();
  }

  /** After each start: window focus changes, the first drawn frame and every frame for 1.5 s */
  private static final class StartWatch {
    private static final java.util.WeakHashMap<Activity, Boolean> focusWatched = new java.util.WeakHashMap<>();
    static volatile long startedAt, burstUntil;

    static void onStarted (Activity a) {
      startedAt = SystemClock.uptimeMillis();
      burstUntil = startedAt + 1500;
      try {
        final android.view.View decor = a.getWindow().getDecorView();
        if (!focusWatched.containsKey(a)) {
          focusWatched.put(a, Boolean.TRUE);
          decor.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus ->
            mark("window focus " + (hasFocus ? "gained" : "lost") + " +" + (SystemClock.uptimeMillis() - startedAt) + " ms after start"));
        }
        decor.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
          @Override
          public boolean onPreDraw () {
            decor.getViewTreeObserver().removeOnPreDrawListener(this);
            mark("first draw +" + (SystemClock.uptimeMillis() - startedAt) + " ms after start, top " + topScreen + ", window " + decor.getWidth() + "x" + decor.getHeight());
            return true;
          }
        });
      } catch (Throwable t) {
        mark("start watch failed: " + t);
      }
    }
  }

  private static final class FrameWatch {
    private static final long JANK_NS = 20_000_000L; // missed at least one 60 Hz frame
    private static final java.util.WeakHashMap<Activity, Object> listeners = new java.util.WeakHashMap<>();
    private static int total, janky;
    private static long windowStart, worst;
    private static String worstPhases;

    static void attach (Activity activity) {
      if (Build.VERSION.SDK_INT < 24 || listeners.containsKey(activity)) return;
      android.view.Window.OnFrameMetricsAvailableListener listener = (window, metrics, dropCount) -> onFrame(metrics);
      try {
        activity.getWindow().addOnFrameMetricsAvailableListener(listener, writer);
        listeners.put(activity, listener);
      } catch (Throwable t) {
        mark("frame watch failed: " + t);
      }
    }

    static void detach (Activity activity) {
      if (Build.VERSION.SDK_INT < 24) return;
      Object listener = listeners.remove(activity);
      if (listener != null) {
        try {
          activity.getWindow().removeOnFrameMetricsAvailableListener((android.view.Window.OnFrameMetricsAvailableListener) listener);
        } catch (Throwable ignored) { }
      }
      writer.post(FrameWatch::report);
    }

    // Runs on the diagnostics thread
    private static void onFrame (android.view.FrameMetrics m) {
      if (Build.VERSION.SDK_INT < 24) return;
      long now = SystemClock.uptimeMillis();
      if (now < StartWatch.burstUntil) {
        // every frame right after a start: when it was drawn and how long it took
        mark("frame +" + (now - StartWatch.startedAt) + " ms: " + (m.getMetric(android.view.FrameMetrics.TOTAL_DURATION) / 1_000_000L) + " ms" +
          (m.getMetric(android.view.FrameMetrics.FIRST_DRAW_FRAME) == 1 ? " (first)" : "") + ", top " + topScreen);
      }
      if (m.getMetric(android.view.FrameMetrics.FIRST_DRAW_FRAME) == 1) return;
      if (windowStart == 0) windowStart = now;
      if (now - windowStart >= 1000) report();
      if (windowStart == 0) windowStart = now;
      total++;
      long duration = m.getMetric(android.view.FrameMetrics.TOTAL_DURATION);
      if (duration >= JANK_NS) {
        janky++;
        if (duration > worst) {
          worst = duration;
          worstPhases = "wait " + ms(m, android.view.FrameMetrics.UNKNOWN_DELAY_DURATION) +
            " input " + ms(m, android.view.FrameMetrics.INPUT_HANDLING_DURATION) +
            " anim " + ms(m, android.view.FrameMetrics.ANIMATION_DURATION) +
            " layout " + ms(m, android.view.FrameMetrics.LAYOUT_MEASURE_DURATION) +
            " draw " + ms(m, android.view.FrameMetrics.DRAW_DURATION) +
            " sync " + ms(m, android.view.FrameMetrics.SYNC_DURATION) +
            " gpu " + (ms(m, android.view.FrameMetrics.COMMAND_ISSUE_DURATION) + ms(m, android.view.FrameMetrics.SWAP_BUFFERS_DURATION));
        }
      }
    }

    private static long ms (android.view.FrameMetrics m, int id) {
      return Build.VERSION.SDK_INT >= 24 ? m.getMetric(id) / 1_000_000L : 0;
    }

    private static void report () {
      if (janky > 0) {
        mark("JANK " + janky + "/" + total + " frames ≥20 ms, worst " + (worst / 1_000_000L) + " ms (" + worstPhases + ")");
      }
      total = janky = 0;
      worst = 0;
      worstPhases = null;
      windowStart = 0;
    }
  }

  // Previous process exits: native crashes, ANRs, low-memory kills

  private static void logExitReasons (Context context) {
    if (Build.VERSION.SDK_INT < 30) {
      return;
    }
    try {
      ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      List<android.app.ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(null, 0, 3);
      StringBuilder b = new StringBuilder("Previous exits:");
      SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);
      for (android.app.ApplicationExitInfo exit : exits) {
        b.append("\n  ").append(format.format(new Date(exit.getTimestamp())))
          .append(" reason=").append(exit.getReason())
          .append(" status=").append(exit.getStatus())
          .append(" importance=").append(exit.getImportance())
          .append(" pss=").append(exit.getPss() / 1024).append("MB");
        if (exit.getDescription() != null) {
          b.append(" ").append(exit.getDescription());
        }
      }
      b.append("\n  (reasons: 1 exit self, 2 signaled, 3 low memory, 4 crash, 5 native crash, 6 ANR, 10 user requested, 13 other)");
      mark(b.toString());
    } catch (Throwable t) {
      mark("Previous exits unavailable: " + t);
    }
  }

  private static String[] abis () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return Build.SUPPORTED_ABIS;
    }
    //noinspection deprecation
    return new String[] {Build.CPU_ABI, Build.CPU_ABI2};
  }

  private static Uri pickDownloadsUri (ContentResolver resolver, String name) {
    if (Build.VERSION.SDK_INT < 29) {
      return null;
    }
    try {
      Uri existing = findDownload(resolver, name);
      if (existing != null) {
        return existing;
      }
      ContentValues values = new ContentValues();
      values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
      values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
      values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
      return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
    } catch (Throwable t) {
      return null;
    }
  }

  private static Uri findDownload (ContentResolver resolver, String name) {
    if (Build.VERSION.SDK_INT < 29) {
      return null;
    }
    Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
    Cursor cursor = resolver.query(collection, new String[] {MediaStore.MediaColumns._ID},
      MediaStore.MediaColumns.DISPLAY_NAME + " = ?", new String[] {name}, null);
    if (cursor != null) {
      try {
        if (cursor.moveToFirst()) {
          return ContentUris.withAppendedId(collection, cursor.getLong(0));
        }
      } finally {
        cursor.close();
      }
    }
    return null;
  }

  private static File pickFile (Context context) {
    File root = Environment.getExternalStorageDirectory();
    File candidate = new File(root, FILE_NAME);
    if (canWrite(candidate)) {
      return candidate;
    }
    File dir = context.getExternalFilesDir(null);
    if (dir != null) {
      candidate = new File(dir, FILE_NAME);
      if (canWrite(candidate)) {
        return candidate;
      }
    }
    return new File(context.getFilesDir(), FILE_NAME);
  }

  private static boolean canWrite (File candidate) {
    try {
      new FileOutputStream(candidate, true).close();
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  private static long currentSize () {
    if (uri != null) {
      ParcelFileDescriptor fd = null;
      try {
        fd = resolver.openFileDescriptor(uri, "r");
        return fd != null ? fd.getStatSize() : 0;
      } catch (Throwable t) {
        return 0;
      } finally {
        if (fd != null) {
          try { fd.close(); } catch (Throwable ignored) { }
        }
      }
    }
    return file != null ? file.length() : 0;
  }

  /** Keeps the current part below MAX_SIZE: current → -old (replacing the previous -old), new empty current */
  private static void rotate () {
    try {
      if (uri != null) {
        Uri old = findDownload(resolver, OLD_FILE_NAME);
        if (old != null) {
          resolver.delete(old, null, null);
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, OLD_FILE_NAME);
        resolver.update(uri, values, null, null);
        Uri fresh = pickDownloadsUri(resolver, FILE_NAME);
        if (fresh != null) {
          uri = fresh;
        }
      } else if (file != null) {
        File old = new File(file.getParentFile(), OLD_FILE_NAME);
        //noinspection ResultOfMethodCallIgnored
        old.delete();
        //noinspection ResultOfMethodCallIgnored
        file.renameTo(old);
      }
    } catch (Throwable ignored) { }
    size = currentSize();
  }

  private static synchronized void flush () {
    String text;
    synchronized (pending) {
      if (pending.length() == 0) return;
      text = pending.toString();
      pending.setLength(0);
    }
    if (size > MAX_SIZE) {
      rotate();
    }
    byte[] bytes = text.getBytes(Charset.forName("UTF-8"));
    // No try-with-resources: AutoCloseable is missing before Android 4.4
    OutputStream out = null;
    try {
      if (uri != null) {
        out = resolver.openOutputStream(uri, "wa");
        if (out == null) return;
        out.write(bytes);
        out.flush();
      } else {
        FileOutputStream fileOut = new FileOutputStream(file, true);
        out = fileOut;
        fileOut.write(bytes);
        fileOut.getFD().sync(); // survive an immediate native crash
      }
      size += bytes.length;
    } catch (Throwable ignored) {
    } finally {
      if (out != null) {
        try { out.close(); } catch (Throwable ignored) { }
      }
    }
  }

  private static void dumpSystemLog () {
    try {
      Process process = Runtime.getRuntime().exec(new String[] {"logcat", "-d", "-v", "time", "-t", "600"});
      StringBuilder b = new StringBuilder("--- System log (includes the previous launch) ---\n");
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          // Only crash and native loading lines: the app's own log may contain personal data (contacts)
          if (line.contains("AndroidRuntime") || line.contains("FATAL") || line.contains("libc") || line.contains("DEBUG") ||
            line.contains("linker") || line.contains("dalvikvm") || line.contains("ReLinker") || line.contains("UnsatisfiedLink") ||
            line.contains("Loaded lib") || (line.contains("tgx") && line.contains("Loaded "))) {
            b.append(line).append('\n');
          }
        }
      } finally {
        reader.close();
      }
      b.append("--- end of system log ---");
      mark(b.toString());
    } catch (Throwable t) {
      mark("System log unavailable: " + t);
    }
  }

  /** TGx101: PowerManager.isInteractive() exists from Android 4.4W (API 20) — on Android 4.1–4.4 it crashed
   *  (NoSuchMethodError in onPause, user's log 2026-10-06); older systems have isScreenOn() */
  @SuppressWarnings("deprecation")
  public static boolean isInteractive (@androidx.annotation.Nullable android.os.PowerManager pm) {
    if (pm == null) return true;
    return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH ? pm.isInteractive() : pm.isScreenOn();
  }
}
