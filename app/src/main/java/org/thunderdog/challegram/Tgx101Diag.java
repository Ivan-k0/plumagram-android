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
package org.thunderdog.challegram;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * TGx101: startup diagnostics for devices where the app closes at launch (old Android, BlackBerry).
 * Only in builds made with -Ptgx101Diag=true. Writes each startup step, Java crashes and the
 * previous run's system log to TGx101-diagnostics.txt in the root of the phone storage.
 */
public final class Tgx101Diag {
  private Tgx101Diag () { }

  private static File file;

  public static void start (Context context) {
    if (!BuildConfig.TGX101_DIAG || file != null) {
      return;
    }
    file = pickFile(context);
    write("\n===== Launch " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + " =====\n" +
      "App: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n" +
      "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ", " + Build.PRODUCT + ")\n" +
      "Android: " + Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT + "\n" +
      "ABI: " + Arrays.toString(abis()) + "\n" +
      "Fingerprint: " + Build.FINGERPRINT + "\n");
    final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
      StringWriter trace = new StringWriter();
      error.printStackTrace(new PrintWriter(trace));
      write("CRASH in thread " + thread.getName() + ":\n" + trace + "\n");
      if (previous != null) {
        previous.uncaughtException(thread, error);
      }
    });
    // A native crash can't be caught here: the log of the previous launch shows it
    new Thread(Tgx101Diag::dumpSystemLog, "TGx101Diag").start();
  }

  public static void mark (String step) {
    if (BuildConfig.TGX101_DIAG && file != null) {
      write(new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()) + "  " + step + "\n");
    }
  }

  private static String[] abis () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return Build.SUPPORTED_ABIS;
    }
    //noinspection deprecation
    return new String[] {Build.CPU_ABI, Build.CPU_ABI2};
  }

  private static File pickFile (Context context) {
    File root = Environment.getExternalStorageDirectory();
    File candidate = new File(root, "TGx101-diagnostics.txt");
    if (canWrite(candidate)) {
      return candidate;
    }
    File dir = context.getExternalFilesDir(null);
    if (dir != null) {
      candidate = new File(dir, "TGx101-diagnostics.txt");
      if (canWrite(candidate)) {
        return candidate;
      }
    }
    return new File(context.getFilesDir(), "TGx101-diagnostics.txt");
  }

  private static boolean canWrite (File candidate) {
    try {
      new FileOutputStream(candidate, true).close();
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  private static synchronized void write (String text) {
    // No try-with-resources: AutoCloseable is missing before Android 4.4
    FileOutputStream out = null;
    try {
      out = new FileOutputStream(file, true);
      out.write(text.getBytes(Charset.forName("UTF-8")));
      out.getFD().sync(); // survive an immediate native crash
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
      b.append("--- end of system log ---\n");
      write(b.toString());
    } catch (Throwable t) {
      write("System log unavailable: " + t + "\n");
    }
  }
}
