/*
 * This file is a part of PlumaGram
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

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Intents;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

/**
 * TGx101 (user 2026-10-19 «отчёт о вылете — это хорошо»): crashes on other people's phones can't be found without their
 * text, and the app's own crash file is out of their reach. On the next start after a crash — every build, not only the
 * test one — the app says it closed with an error and offers to send the report (the crash file, or what Android tells
 * about a native crash on 11+), through the share sheet: to the developer in Telegram or anywhere else
 */
public final class Tgx101CrashReport {
  private Tgx101CrashReport () { }

  private static final String PREFS = "tgx101", KEY_SEEN = "crash_report_seen";
  private static final int MAX_TEXT = 30_000;

  private static SharedPreferences prefs (Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  /** after the main screen is up: if the last run ended with a crash nobody has been asked about yet, ask */
  public static void checkAndOffer (ViewController<?> c) {
    Context context = c.context();
    try {
      long seen = prefs(context).getLong(KEY_SEEN, 0);
      if (seen == 0) { // the first start with this feature: older crashes aren't news
        prefs(context).edit().putLong(KEY_SEEN, System.currentTimeMillis()).apply();
        return;
      }
      String report = null;
      long when = 0;
      File crash = newestCrashFile();
      if (crash != null && crash.lastModified() > seen) {
        report = read(crash);
        when = crash.lastModified();
      }
      if (report == null && Build.VERSION.SDK_INT >= 30) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ApplicationExitInfo> exits = am != null ? am.getHistoricalProcessExitReasons(null, 0, 3) : null;
        if (exits != null) {
          for (ApplicationExitInfo exit : exits) {
            if (exit.getTimestamp() <= seen) break;
            if (exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE || exit.getReason() == ApplicationExitInfo.REASON_CRASH || exit.getReason() == ApplicationExitInfo.REASON_ANR) {
              report = header() + "\nExit: reason " + exit.getReason() + " status " + exit.getStatus() + " at " + exit.getTimestamp()
                + "\n" + exit.getDescription() + trace(exit);
              when = exit.getTimestamp();
              break;
            }
          }
        }
      }
      if (report == null) return;
      prefs(context).edit().putLong(KEY_SEEN, Math.max(when, System.currentTimeMillis())).apply();
      final String text = report.length() > MAX_TEXT ? report.substring(0, MAX_TEXT) + "\n…" : report;
      c.showOptions(Lang.getString(R.string.Tgx101CrashReportTitle),
        new int[] {R.id.btn_share, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.Tgx101CrashReportSend), Lang.getString(R.string.Tgx101CrashReportSkip)},
        new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
        new int[] {R.drawable.baseline_share_arrow_24, R.drawable.baseline_cancel_24}, (v, id) -> {
          if (id == R.id.btn_share) Intents.shareText(text);
          return true;
        });
    } catch (Throwable ignored) {
      // the report must never be a crash of its own
    }
  }

  private static String header () {
    return "PlumaGram " + BuildConfig.VERSION_NAME + " (" + BuildConfig.COMMIT + ")"
      + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
  }

  private static @Nullable File newestCrashFile () {
    File dir = Log.getLogDir();
    File[] files = dir != null ? dir.listFiles((d, name) -> name.startsWith("crash.") && name.endsWith(".log")) : null;
    File newest = null;
    if (files != null) {
      for (File f : files) if (newest == null || f.lastModified() > newest.lastModified()) newest = f;
    }
    return newest;
  }

  private static String read (File f) throws Exception {
    try (InputStream in = new FileInputStream(f)) {
      return header() + "\n\n" + readAll(in);
    }
  }

  private static String trace (ApplicationExitInfo exit) {
    if (Build.VERSION.SDK_INT < 30) return "";
    try (InputStream in = exit.getTraceInputStream()) {
      if (in == null) return "";
      String t = readAll(in);
      // a native crash's trace is a binary tombstone on Android 12+: keep the readable lines only
      StringBuilder b = new StringBuilder("\n\n");
      for (String line : t.split("\n")) {
        boolean readable = true;
        for (int i = 0; i < line.length() && readable; i++) {
          char ch = line.charAt(i);
          if (ch < 0x20 && ch != '\t') readable = false;
        }
        if (readable && line.trim().length() > 3) b.append(line).append('\n');
        if (b.length() > MAX_TEXT) break;
      }
      return b.toString();
    } catch (Throwable ignored) {
      return "";
    }
  }

  private static String readAll (InputStream in) throws Exception {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buf = new byte[16 * 1024];
    int n;
    while ((n = in.read(buf)) != -1 && out.size() < MAX_TEXT * 2) out.write(buf, 0, n);
    return out.toString("UTF-8");
  }
}
