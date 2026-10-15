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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TGx101 (user 2026-10-12, «как в Nova VPN / NewPipe»): updates from the fork's GitHub releases inside the app.
 * Once a day (when the app is opened) the latest release is checked; a newer one shows a notification and the
 * «Обновить PlumaGram» row at the bottom of the side menu and in Settings → Interface → Updates. The APK for
 * this very build variant (arm64 / arm7 / universal / android4–6 / test T) is downloaded only on a tap, checked
 * (same package, newer version, same signing key) and handed to the system installer.
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
  private static final String KEY_FOUND = "update_found"; // JSON of the newer release found last time
  private static final long DAY_MS = 24L * 60 * 60 * 1000;
  private static final String CHANNEL_ID = "tgx101_updates";
  private static final int NOTIFICATION_ID = 0x7101;

  private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

  public static final int STATE_NONE = 0, STATE_CHECKING = 1, STATE_AVAILABLE = 2, STATE_DOWNLOADING = 3, STATE_READY = 4;

  public interface Listener {
    void onTgx101UpdateStateChanged ();
  }

  public static final class Release {
    public final String version, apkUrl, apkName, pageUrl, notes;
    public final long size;

    Release (String version, String apkUrl, String apkName, long size, String pageUrl, String notes) {
      this.version = version;
      this.apkUrl = apkUrl;
      this.apkName = apkName;
      this.size = size;
      this.pageUrl = pageUrl;
      this.notes = notes;
    }

    JSONObject toJson () throws Exception {
      return new JSONObject().put("v", version).put("url", apkUrl).put("name", apkName).put("size", size).put("page", pageUrl).put("notes", notes);
    }

    static @Nullable Release fromJson (@Nullable String json) {
      if (json == null || json.isEmpty()) return null;
      try {
        JSONObject o = new JSONObject(json);
        return new Release(o.getString("v"), o.getString("url"), o.getString("name"), o.optLong("size"), o.optString("page", RELEASES_URL), o.optString("notes", ""));
      } catch (Throwable t) {
        return null;
      }
    }
  }

  private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
  private static volatile int state = STATE_NONE;
  private static volatile @Nullable Release release;
  private static volatile long downloaded;
  private static volatile boolean restored;

  public static void addListener (Listener l) {
    listeners.addIfAbsent(l);
  }

  public static void removeListener (Listener l) {
    listeners.remove(l);
  }

  public static int state () {
    return state;
  }

  public static @Nullable Release release () {
    return release;
  }

  public static long downloaded () {
    return downloaded;
  }

  /** Download progress 0..100 */
  public static int progress () {
    Release r = release;
    return r != null && r.size > 0 ? (int) Math.min(100, downloaded * 100 / r.size) : 0;
  }

  private static void setState (int newState) {
    state = newState;
    UI.post(() -> {
      for (Listener l : listeners) {
        l.onTgx101UpdateStateChanged();
      }
    });
  }

  private static SharedPreferences prefs (Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  public static boolean isEnabled (Context context) {
    return prefs(context).getBoolean(KEY_ENABLED, true);
  }

  public static void setEnabled (Context context, boolean enabled) {
    prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
  }

  /** The newer release found earlier stays offered after a restart (and an already downloaded APK stays ready). */
  public static void restore (Context context) {
    if (restored) return;
    restored = true;
    Release r = Release.fromJson(prefs(context).getString(KEY_FOUND, null));
    if (r == null || !isNewer(r.version)) {
      prefs(context).edit().remove(KEY_FOUND).apply();
      clearDownloads(context, null);
      return;
    }
    release = r;
    File apk = apkFile(context, r);
    if (apk.exists() && (r.size <= 0 || apk.length() == r.size)) {
      downloaded = apk.length();
      state = STATE_READY;
    } else {
      state = STATE_AVAILABLE;
    }
  }

  /** Called when the app comes to the foreground. Cheap: does nothing until a day has passed. */
  public static void checkIfNeeded (Context context) {
    final Context app = context.getApplicationContext();
    restore(app);
    if (!isEnabled(app) || state == STATE_CHECKING || state == STATE_DOWNLOADING) {
      return;
    }
    if (System.currentTimeMillis() - prefs(app).getLong(KEY_LAST_CHECK, 0) < DAY_MS) {
      return;
    }
    startCheck(app, false);
  }

  /** «Проверить обновления» in Settings */
  public static void checkNow (Context context) {
    final Context app = context.getApplicationContext();
    restore(app);
    if (state == STATE_CHECKING || state == STATE_DOWNLOADING) return;
    startCheck(app, true);
  }

  private static void startCheck (Context app, boolean manual) {
    final int before = state;
    setState(STATE_CHECKING);
    new Thread(() -> {
      try {
        Release r = fetch(app);
        if (r != null) {
          boolean same = release != null && release.version.equals(r.version) && before == STATE_READY;
          release = r;
          prefs(app).edit().putString(KEY_FOUND, r.toJson().toString()).apply();
          Tgx101Diag.mark("update: " + r.version + " found, " + r.apkName);
          setState(same ? STATE_READY : STATE_AVAILABLE);
          maybeNotify(app, r);
        } else {
          release = null;
          prefs(app).edit().remove(KEY_FOUND).apply();
          setState(STATE_NONE);
          if (manual) UI.showToast(R.string.Tgx101UpdateLatest, android.widget.Toast.LENGTH_SHORT);
        }
      } catch (Throwable t) {
        Log.w("TGx101 update check failed", t);
        Tgx101Diag.mark("update: check failed " + t.getClass().getSimpleName());
        setState(release != null ? (before == STATE_READY ? STATE_READY : STATE_AVAILABLE) : STATE_NONE);
        if (manual) UI.showToast(R.string.Tgx101UpdateCheckFailed, android.widget.Toast.LENGTH_SHORT);
      }
    }, "Tgx101Updates").start();
  }

  private static String readAll (HttpURLConnection connection) throws Exception {
    StringBuilder body = new StringBuilder();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(connection.getInputStream(), java.nio.charset.Charset.forName("UTF-8")))) {
      String line;
      while ((line = r.readLine()) != null) {
        body.append(line).append('\n');
      }
    }
    return body.toString();
  }

  /** The newer release with an APK for this build variant, or null when this build is the latest */
  private static @Nullable Release fetch (Context context) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
    connection.setConnectTimeout(15_000);
    connection.setReadTimeout(15_000);
    connection.setRequestProperty("Accept", "application/vnd.github+json");
    String body;
    try {
      if (connection.getResponseCode() != 200) {
        throw new IllegalStateException("HTTP " + connection.getResponseCode());
      }
      body = readAll(connection);
    } finally {
      connection.disconnect();
    }
    prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();

    JSONObject json = new JSONObject(body);
    String tag = json.optString("tag_name", "");
    int[] latest = parse(tag);
    if (latest == null || !isNewer(tag)) {
      return null;
    }
    String suffix = assetSuffix();
    JSONArray assets = json.optJSONArray("assets");
    if (assets != null) {
      for (int i = 0; i < assets.length(); i++) {
        JSONObject a = assets.getJSONObject(i);
        String name = a.optString("name", "");
        if (!name.endsWith(suffix) || (!BuildConfig.TGX101_TEST && name.contains("-T-"))) continue;
        return new Release(latest[0] + "." + latest[1] + "." + latest[2], a.getString("browser_download_url"), name,
          a.optLong("size"), json.optString("html_url", RELEASES_URL), json.optString("body", ""));
      }
    }
    Tgx101Diag.mark("update: " + tag + " has no " + suffix);
    return null;
  }

  /** Release asset names, see scripts/tgx101/build-public.sh and build-test.sh */
  private static String assetSuffix () {
    if (BuildConfig.TGX101_TEST) {
      return BuildConfig.LEGACY_FLAVOR ? "-T-android4.apk" : "-T-universal.apk";
    }
    if (BuildConfig.LEGACY_FLAVOR) return "-android4.apk";
    if (BuildConfig.LOLLIPOP_FLAVOR) return "-android5.apk";
    if (BuildConfig.MARSHMALLOW_FLAVOR) return "-android6.apk";
    if (BuildConfig.UNIVERSAL_FLAVOR) return "-universal.apk";
    if (BuildConfig.ARM64_FLAVOR) return "-arm64.apk";
    if (BuildConfig.ARM32_FLAVOR) return "-arm7.apk";
    return "-universal.apk";
  }

  private static boolean isNewer (String version) {
    int[] v = parse(version), current = parse(BuildConfig.VERSION_NAME);
    return v != null && current != null && compare(v, current) > 0;
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

  // Download

  private static File apkFile (Context context, Release r) {
    return new File(new File(context.getFilesDir(), "updates"), r.apkName);
  }

  private static void clearDownloads (Context context, @Nullable File keep) {
    File[] files = new File(context.getFilesDir(), "updates").listFiles();
    if (files == null) return;
    for (File f : files) {
      if (keep == null || !f.equals(keep)) {
        //noinspection ResultOfMethodCallIgnored
        f.delete();
      }
    }
  }

  public static void download (Context context) {
    final Context app = context.getApplicationContext();
    final Release r = release;
    if (r == null || state == STATE_DOWNLOADING || state == STATE_CHECKING) return;
    downloaded = 0;
    setState(STATE_DOWNLOADING);
    Tgx101Diag.mark("update: downloading " + r.apkName);
    new Thread(() -> {
      File target = apkFile(app, r);
      File part = new File(target.getPath() + ".part");
      try {
        File dir = target.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) throw new IllegalStateException("no dir");
        clearDownloads(app, null);
        HttpURLConnection c = (HttpURLConnection) new URL(r.apkUrl).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(20_000);
        c.setReadTimeout(30_000);
        try {
          if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
          long lastPost = 0;
          try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
              out.write(buf, 0, n);
              downloaded += n;
              long now = SystemClock.uptimeMillis();
              if (now - lastPost > 250) {
                lastPost = now;
                setState(STATE_DOWNLOADING);
              }
            }
          }
        } finally {
          c.disconnect();
        }
        if (r.size > 0 && part.length() != r.size) throw new IllegalStateException("size " + part.length() + " != " + r.size);
        if (!part.renameTo(target)) throw new IllegalStateException("rename");
        String problem = verify(app, target);
        if (problem != null) {
          //noinspection ResultOfMethodCallIgnored
          target.delete();
          Tgx101Diag.mark("update: rejected, " + problem);
          setState(STATE_AVAILABLE);
          UI.showToast(R.string.Tgx101UpdateBadFile, android.widget.Toast.LENGTH_LONG);
          return;
        }
        Tgx101Diag.mark("update: downloaded and verified");
        setState(STATE_READY);
        UI.post(() -> install(app));
      } catch (Throwable t) {
        //noinspection ResultOfMethodCallIgnored
        part.delete();
        Log.w("TGx101 update download failed", t);
        Tgx101Diag.mark("update: download failed " + t.getClass().getSimpleName() + " " + t.getMessage());
        setState(STATE_AVAILABLE);
        UI.showToast(R.string.Tgx101UpdateDownloadFailed, android.widget.Toast.LENGTH_SHORT);
      }
    }, "Tgx101UpdateDownload").start();
  }

  // Verify & install

  /** null when the APK is ours (same package, newer version, same key), otherwise what is wrong */
  @SuppressWarnings("deprecation")
  private static @Nullable String verify (Context context, File apk) {
    try {
      PackageManager pm = context.getPackageManager();
      int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
      PackageInfo archive = pm.getPackageArchiveInfo(apk.getPath(), flags);
      if (archive == null) return "not an apk";
      if (!context.getPackageName().equals(archive.packageName)) return "package " + archive.packageName;
      PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
      long archiveCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(archive);
      long installedCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(installed);
      if (archiveCode <= installedCode) return "version " + archiveCode + " <= " + installedCode;
      String a = certOf(archive), b = certOf(installed);
      if (a != null && b != null && !a.equals(b)) return "signed by another key";
      return null;
    } catch (Throwable t) {
      return t.getClass().getSimpleName();
    }
  }

  @SuppressWarnings("deprecation")
  private static @Nullable String certOf (PackageInfo info) throws Exception {
    Signature[] signatures = null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
      signatures = info.signingInfo.getApkContentsSigners();
    }
    if (signatures == null || signatures.length == 0) {
      signatures = info.signatures;
    }
    if (signatures == null || signatures.length == 0) return null;
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
    StringBuilder s = new StringBuilder();
    for (byte x : digest) s.append(String.format("%02x", x));
    return s.toString();
  }

  public static void install (Context context) {
    Release r = release;
    if (r == null || state != STATE_READY) return;
    File apk = apkFile(context, r);
    if (!apk.exists()) {
      setState(STATE_AVAILABLE);
      return;
    }
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.getPackageManager().canRequestPackageInstalls()) {
        // The system asks once to allow installing apps from PlumaGram; after that «Установить» works directly
        UI.showToast(R.string.Tgx101UpdateAllowInstall, android.widget.Toast.LENGTH_LONG);
        context.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
          Uri.parse("package:" + context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return;
      }
      Uri uri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        ? FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", apk)
        : Uri.fromFile(apk);
      Intent intent = new Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
      Tgx101Diag.mark("update: installer opened for " + r.version);
      context.startActivity(intent);
    } catch (Throwable t) {
      Log.w("TGx101 update install failed", t);
      Tgx101Diag.mark("update: installer failed " + t.getClass().getSimpleName());
      UI.openUrl(r.pageUrl);
    }
  }

  // Texts for the side menu / settings rows

  public static String rowTitle () {
    Release r = release;
    String v = r != null ? r.version : "";
    switch (state) {
      case STATE_DOWNLOADING:
        return Lang.getString(R.string.Tgx101UpdateDownloading, progress() + "%");
      case STATE_READY:
        return Lang.getString(R.string.Tgx101UpdateInstall, v);
      case STATE_CHECKING:
        return Lang.getString(R.string.Tgx101UpdateChecking);
      default:
        return r != null && r.size > 0 ? Lang.getString(R.string.Tgx101UpdateRowSize, v, Strings.buildSize(r.size)) : Lang.getString(R.string.Tgx101UpdateRow, v);
    }
  }

  /** Tap on the side menu / settings row */
  public static void onRowClick (org.thunderdog.challegram.navigation.ViewController<?> c) {
    Release r = release;
    if (r == null) return;
    if (state == STATE_READY) {
      install(c.context());
    } else if (state == STATE_AVAILABLE) {
      String notes = r.notes != null ? r.notes.trim() : "";
      if (notes.length() > 700) notes = notes.substring(0, 700).trim() + "…";
      CharSequence info = Lang.getString(R.string.Tgx101UpdateAbout, r.version) + (notes.isEmpty() ? "" : "\n\n" + notes);
      c.showOptions(info, new int[] {R.id.btn_update, R.id.btn_tgx101UpdatePage},
        new String[] {r.size > 0 ? Lang.getString(R.string.Tgx101UpdateDownloadSize, Strings.buildSize(r.size)) : Lang.getString(R.string.DownloadUpdate), Lang.getString(R.string.Tgx101UpdateWhatsNew)},
        new int[] {org.thunderdog.challegram.navigation.ViewController.OptionColor.BLUE, org.thunderdog.challegram.navigation.ViewController.OptionColor.NORMAL},
        new int[] {R.drawable.baseline_system_update_24, R.drawable.baseline_open_in_browser_24}, (v, id) -> {
          if (id == R.id.btn_update) {
            download(c.context());
          } else if (id == R.id.btn_tgx101UpdatePage) {
            UI.openUrl(r.pageUrl);
          }
          return true;
        });
    }
  }

  // Notification

  private static void maybeNotify (Context context, Release r) {
    SharedPreferences p = prefs(context);
    long now = System.currentTimeMillis();
    if (now - p.getLong(KEY_LAST_NOTIFY, 0) < DAY_MS) {
      return;
    }
    p.edit().putLong(KEY_LAST_NOTIFY, now).apply();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, Lang.getString(R.string.Tgx101UpdateChannel), NotificationManager.IMPORTANCE_DEFAULT);
      NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.createNotificationChannel(channel);
      }
    }
    Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    PendingIntent open = launch != null ? PendingIntent.getActivity(context, 3, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), Intents.mutabilityFlags(false)) : null;
    PendingIntent github = PendingIntent.getActivity(context, 1,
      new Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), Intents.mutabilityFlags(false));
    PendingIntent forum = PendingIntent.getActivity(context, 2,
      new Intent(Intent.ACTION_VIEW, Uri.parse(FORUM_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), Intents.mutabilityFlags(false));
    NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.baseline_sync_white_24)
      .setContentTitle(Lang.getString(R.string.Tgx101UpdateTitle, r.version))
      .setContentText(Lang.getString(R.string.Tgx101UpdateText))
      .setAutoCancel(true)
      .setContentIntent(open != null ? open : github)
      .addAction(0, "GitHub", github)
      .addAction(0, "4PDA", forum);
    try {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, b.build());
    } catch (SecurityException ignored) {
      // Notifications are not allowed: nothing to do.
    }
  }
}
