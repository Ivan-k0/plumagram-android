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
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;

import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * TGx101 (user 2026-10-23 «мигание из уведомления — редко, чаще с заблокированного экрана, на видео не поймать, может
 * мало логов»): a black box for the moments that flicker. Only in builds with the diagnostics log (-Ptgx101Diag=true):
 * after a tap on a notification and after coming back from the lock screen it copies the first frames of the window —
 * small, ~270 px wide — into Download/PlumaGram-frames/<time>/, and logs for each what was on screen (the top screen,
 * whether the navigation was moving, the cover over the window, the lock screen, the window's focus).
 * The pictures stay on the phone (they show the chats); nothing is sent anywhere.
 */
public final class Tgx101FrameTrace {
  private Tgx101FrameTrace () { }

  /** when the copies are taken, ms after the start: dense at first, where a blink lives */
  private static final int[] AT = {0, 16, 33, 50, 75, 100, 150, 200, 300, 400, 550, 750, 1000, 1500};
  private static final int WIDTH = 270;

  private static HandlerThread thread;
  private static long lastStart;

  public static void start (Activity activity, String reason) {
    if (!BuildConfig.TGX101_DIAG || Build.VERSION.SDK_INT < 29 || activity == null) return;
    long now = SystemClock.uptimeMillis();
    if (now - lastStart < 3000) return; // one trace at a time (a notification tap also resumes the activity)
    lastStart = now;
    if (thread == null) {
      thread = new HandlerThread("Tgx101FrameTrace");
      thread.start();
    }
    final Handler io = new Handler(thread.getLooper());
    final String folder = "PlumaGram-frames/" + new SimpleDateFormat("MM-dd_HH-mm-ss", Locale.US).format(new Date()) + "_" + reason;
    Tgx101Diag.mark("frames: tracing «" + reason + "» → Download/" + folder);
    final View decor = activity.getWindow().getDecorView();
    for (int i = 0; i < AT.length; i++) {
      final int index = i;
      decor.postDelayed(() -> capture(activity, folder, index, now, io), AT[i]);
    }
  }

  private static void capture (Activity activity, String folder, int index, long start, Handler io) {
    if (activity.isFinishing()) return;
    final long at = SystemClock.uptimeMillis() - start;
    final String state = describe(activity);
    Tgx101Diag.mark("frames: " + index + " +" + at + " ms " + state);
    Window window = activity.getWindow();
    View decor = window.getDecorView();
    if (decor.getWidth() == 0 || decor.getHeight() == 0 || !decor.isAttachedToWindow()) return;
    int width = WIDTH, height = Math.round(WIDTH * decor.getHeight() / (float) decor.getWidth());
    final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    try {
      PixelCopy.request(window, bitmap, result -> {
        if (result != PixelCopy.SUCCESS) {
          Tgx101Diag.mark("frames: " + index + " not copied (" + result + "), drawing the views instead");
          bitmap.recycle();
          // the first ~0.4 s after a return the window gives no pixels: the views drawn by hand show what the app holds
          decor.post(() -> drawViews(activity, folder, String.format(Locale.US, "%02d_%04dms_views.frame", index, at), io));
          return;
        }
        io.post(() -> save(activity.getApplicationContext(), folder, String.format(Locale.US, "%02d_%04dms.frame", index, at), bitmap));
      }, io);
    } catch (Throwable t) {
      Tgx101Diag.mark("frames: " + index + " failed — " + t);
      bitmap.recycle();
    }
  }

  private static void drawViews (Activity activity, String folder, String name, Handler io) {
    View decor = activity.getWindow().getDecorView();
    if (activity.isFinishing() || decor.getWidth() == 0 || decor.getHeight() == 0) return;
    float scale = WIDTH / (float) decor.getWidth();
    Bitmap bitmap = null;
    try {
      bitmap = Bitmap.createBitmap(WIDTH, Math.round(decor.getHeight() * scale), Bitmap.Config.ARGB_8888);
      android.graphics.Canvas c = new android.graphics.Canvas(bitmap);
      c.scale(scale, scale);
      decor.draw(c);
      final Bitmap done = bitmap;
      io.post(() -> save(activity.getApplicationContext(), folder, name, done));
    } catch (Throwable t) {
      Tgx101Diag.mark("frames: " + name + " not drawn — " + t);
      if (bitmap != null) bitmap.recycle();
    }
  }

  private static String describe (Activity activity) {
    StringBuilder b = new StringBuilder();
    try {
      KeyguardManager km = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
      b.append(km != null && km.isKeyguardLocked() ? "locked" : "unlocked");
      b.append(activity.hasWindowFocus() ? ", focus" : ", no focus");
      if (activity instanceof BaseActivity) {
        NavigationController navigation = ((BaseActivity) activity).navigation();
        if (navigation != null) {
          ViewController<?> top = navigation.getCurrentStackItem();
          b.append(", top ").append(top != null ? top.getClass().getSimpleName() + "#" + Integer.toHexString(System.identityHashCode(top)) : "none");
          if (navigation.isAnimating()) b.append(", moving");
        }
      }
      Drawable cover = activity.getWindow().getDecorView().getForeground();
      if (cover != null) b.append(", cover alpha ").append(cover.getAlpha());
    } catch (Throwable t) {
      b.append(", ").append(t.getClass().getSimpleName());
    }
    return b.toString();
  }

  private static void save (Context context, String folder, String name, Bitmap bitmap) {
    try {
      ContentResolver resolver = context.getContentResolver();
      ContentValues values = new ContentValues();
      values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
      // (user 2026-10-24 «чёрный ящик забил мне всю галерею»): not an image to the system — the gallery doesn't show it; a
      // JPEG inside all the same (rename to .jpg to look)
      values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
      values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + folder);
      Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
      if (uri == null) return;
      try (OutputStream out = resolver.openOutputStream(uri)) {
        if (out != null) bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out);
      }
    } catch (Throwable t) {
      Tgx101Diag.mark("frames: " + name + " not saved — " + t);
    } finally {
      bitmap.recycle();
    }
  }
}
