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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONObject;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Intents;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TGx101: once a day (when the app is opened) asks GitHub for the latest release and, if it is
 * newer than the installed build, shows a notification with links to GitHub and 4PDA.
 * Nothing is downloaded or installed automatically.
 */
public final class Tgx101Updates {
  private Tgx101Updates () { }

  public static final String RELEASES_URL = "https://github.com/Ivan-k0/plumagram-android/releases/latest";
  public static final String FORUM_URL = "https://4pda.to/forum/index.php?showtopic=1126862";
  private static final String API_URL = "https://api.github.com/repos/Ivan-k0/plumagram-android/releases/latest";

  private static final String PREFS = "tgx101";
  private static final String KEY_ENABLED = "update_check_enabled";
  private static final String KEY_LAST_CHECK = "update_last_check";
  private static final String KEY_LAST_NOTIFY = "update_last_notify";
  private static final long DAY_MS = 24L * 60 * 60 * 1000;
  private static final String CHANNEL_ID = "tgx101_updates";
  private static final int NOTIFICATION_ID = 0x7101;

  private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

  private static boolean running;

  private static SharedPreferences prefs (Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  public static boolean isEnabled (Context context) {
    return prefs(context).getBoolean(KEY_ENABLED, true);
  }

  public static void setEnabled (Context context, boolean enabled) {
    prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
  }

  /** Called when the app comes to the foreground. Cheap: does nothing until a day has passed. */
  public static void checkIfNeeded (Context context) {
    final Context app = context.getApplicationContext();
    if (!isEnabled(app) || running) {
      return;
    }
    long now = System.currentTimeMillis();
    SharedPreferences p = prefs(app);
    if (now - p.getLong(KEY_LAST_CHECK, 0) < DAY_MS) {
      return;
    }
    running = true;
    new Thread(() -> {
      try {
        check(app);
      } catch (Throwable t) {
        Log.w("TGx101 update check failed", t);
      } finally {
        running = false;
      }
    }, "Tgx101Updates").start();
  }

  private static void check (Context context) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
    connection.setConnectTimeout(10_000);
    connection.setReadTimeout(10_000);
    connection.setRequestProperty("Accept", "application/vnd.github+json");
    StringBuilder body = new StringBuilder();
    try {
      if (connection.getResponseCode() != 200) {
        return;
      }
      try (BufferedReader r = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = r.readLine()) != null) {
          body.append(line);
        }
      }
    } finally {
      connection.disconnect();
    }
    SharedPreferences p = prefs(context);
    long now = System.currentTimeMillis();
    p.edit().putLong(KEY_LAST_CHECK, now).apply();

    JSONObject release = new JSONObject(body.toString());
    String tag = release.optString("tag_name", "");
    int[] latest = parse(tag);
    int[] current = parse(BuildConfig.VERSION_NAME);
    if (latest == null || current == null || compare(latest, current) <= 0) {
      return;
    }
    if (now - p.getLong(KEY_LAST_NOTIFY, 0) < DAY_MS) {
      return;
    }
    String version = latest[0] + "." + latest[1] + "." + latest[2];
    notify(context, version, release.optString("html_url", RELEASES_URL));
    p.edit().putLong(KEY_LAST_NOTIFY, now).apply();
  }

  private static @Nullable int[] parse (String s) {
    Matcher m = VERSION.matcher(s != null ? s : "");
    if (!m.find()) {
      return null;
    }
    return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
  }

  private static int compare (int[] a, int[] b) {
    for (int i = 0; i < 3; i++) {
      if (a[i] != b[i]) {
        return Integer.compare(a[i], b[i]);
      }
    }
    return 0;
  }

  private static void notify (Context context, String version, String releaseUrl) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, Lang.getString(R.string.Tgx101UpdateChannel), NotificationManager.IMPORTANCE_DEFAULT);
      NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.createNotificationChannel(channel);
      }
    }
    PendingIntent github = PendingIntent.getActivity(context, 1,
      new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), Intents.mutabilityFlags(false));
    PendingIntent forum = PendingIntent.getActivity(context, 2,
      new Intent(Intent.ACTION_VIEW, Uri.parse(FORUM_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), Intents.mutabilityFlags(false));
    NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.baseline_sync_white_24)
      .setContentTitle(Lang.getString(R.string.Tgx101UpdateTitle, version))
      .setContentText(Lang.getString(R.string.Tgx101UpdateText))
      .setAutoCancel(true)
      .setContentIntent(github)
      .addAction(0, "GitHub", github)
      .addAction(0, "4PDA", forum);
    try {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, b.build());
    } catch (SecurityException ignored) {
      // Notifications are not allowed: nothing to do.
    }
  }
}
