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
package org.thunderdog.challegram.data;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import org.thunderdog.challegram.unsorted.Settings;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;

import me.vkryl.leveldb.LevelDB;

/**
 * TGx101 «Не подглядывай!» (user 2026-10-22): the chat list blurred for eyes over the shoulder — avatars, names and the
 * last message, each with its own strength, in every chat or in the chosen ones. One switch (the eye) turns it on and off.
 *
 * The blur is drawn by hand — the part is drawn small into a bitmap, softened and drawn back stretched — so it works on
 * every Android the app runs on (4.1+), with no RenderEffect.
 */
public final class Tgx101Peek {
  private Tgx101Peek () { }

  private static final String KEY_ON = "tgx101_peek_on";
  private static final String KEY_ALL = "tgx101_peek_all";
  private static final String KEY_CHATS = "tgx101_peek_chats";
  private static final String KEY_AVATAR = "tgx101_peek_avatar";
  private static final String KEY_TEXT = "tgx101_peek_text";
  private static final String KEY_TITLE = "tgx101_peek_title";

  public static final int PART_AVATAR = 0, PART_TITLE = 1, PART_TEXT = 2;

  /** The slider's steps, % */
  public static final int[] STEPS = {0, 20, 40, 60, 80, 100};

  private static boolean loaded, on, all;
  private static int avatar, title, text;
  private static Set<Long> chats = Collections.emptySet();

  private static LevelDB pmc () {
    return Settings.instance().pmc();
  }

  private static void load () {
    if (loaded) return;
    loaded = true;
    LevelDB pmc = pmc();
    on = pmc.getBoolean(KEY_ON, false);
    all = pmc.getBoolean(KEY_ALL, true);
    avatar = pmc.getInt(KEY_AVATAR, 60);
    text = pmc.getInt(KEY_TEXT, 80);
    title = pmc.getInt(KEY_TITLE, 0);
    long[] ids = pmc.getLongArray(KEY_CHATS);
    Set<Long> set = new LinkedHashSet<>();
    if (ids != null) for (long id : ids) set.add(id);
    chats = set;
  }

  /** After a settings restore */
  public static void reload () {
    loaded = false;
    invalidateAll();
  }

  public static boolean isOn () {
    load();
    return on;
  }

  public static void setOn (boolean value) {
    load();
    on = value;
    pmc().putBoolean(KEY_ON, value);
    invalidateAll();
  }

  public static boolean toggle () {
    setOn(!isOn());
    return on;
  }

  public static boolean isAll () {
    load();
    return all;
  }

  public static void setAll (boolean value) {
    load();
    all = value;
    pmc().putBoolean(KEY_ALL, value);
    invalidateAll();
  }

  public static Set<Long> chats () {
    load();
    return Collections.unmodifiableSet(chats);
  }

  public static void setChats (Set<Long> ids) {
    load();
    chats = new LinkedHashSet<>(ids);
    long[] array = new long[chats.size()];
    int i = 0;
    for (long id : chats) array[i++] = id;
    if (array.length == 0) {
      pmc().remove(KEY_CHATS);
    } else {
      pmc().putLongArray(KEY_CHATS, array);
    }
    invalidateAll();
  }

  public static int strength (int part) {
    load();
    switch (part) {
      case PART_AVATAR: return avatar;
      case PART_TITLE: return title;
      default: return text;
    }
  }

  public static void setStrength (int part, int value) {
    load();
    switch (part) {
      case PART_AVATAR: avatar = value; pmc().putInt(KEY_AVATAR, value); break;
      case PART_TITLE: title = value; pmc().putInt(KEY_TITLE, value); break;
      default: text = value; pmc().putInt(KEY_TEXT, value); break;
    }
    invalidateAll();
  }

  public static int stepIndex (int value) {
    int best = 0;
    for (int i = 0; i < STEPS.length; i++) {
      if (Math.abs(STEPS[i] - value) < Math.abs(STEPS[best] - value)) best = i;
    }
    return best;
  }

  /** Strength of the blur for this part of this chat's row, 0 — drawn as is */
  public static int strengthFor (long chatId, int part) {
    load();
    if (!on || (!all && !chats.contains(chatId))) return 0;
    return strength(part);
  }

  // The rows on screen, redrawn at once when anything changes

  private static final Set<View> views = Collections.newSetFromMap(new WeakHashMap<>());

  public static void register (View view) {
    views.add(view);
  }

  public static void unregister (View view) {
    views.remove(view);
  }

  private static void invalidateAll () {
    for (View view : views.toArray(new View[0])) {
      if (view != null) view.invalidate();
    }
  }

  // Drawing

  public interface Drawer {
    void draw (Canvas c);
  }

  /** One part of one row: its own bitmap, since a hardware-drawn row keeps a reference to it until the next frame */
  public static final class Painter {
    private Bitmap bitmap;
    private Canvas canvas;
    private int[] pixels, buffer;
    private final Rect src = new Rect(), dst = new Rect();
    private static final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    /** Draws what {@code drawer} draws inside left..bottom (the part's bounds, with room for the blur to spread) blurred */
    public void draw (Canvas c, int left, int top, int right, int bottom, int strength, Drawer drawer) {
      if (strength <= 0 || right <= left || bottom <= top) {
        drawer.draw(c);
        return;
      }
      if (android.os.Build.VERSION.SDK_INT >= 31 && c.isHardwareAccelerated()) {
        // Android 12+: the blur on the GPU, nothing for the main thread to do (it made the list scroll heavier)
        if (gpu == null) gpu = new Gpu();
        gpu.draw(c, left, top, right, bottom, strength, drawer);
        return;
      }
      // smaller copy for a stronger blur: 1/3 … 1/12
      float scale = 1f / (3f + 9f * Math.min(100, strength) / 100f);
      int w = Math.max(4, (int) Math.ceil((right - left) * scale));
      int h = Math.max(4, (int) Math.ceil((bottom - top) * scale));
      // the bitmap only grows (rows are reused for chats with names of other widths): no new one on every bind
      if (bitmap == null || bitmap.getWidth() < w || bitmap.getHeight() < h) {
        int bw = Math.max(w, bitmap != null ? bitmap.getWidth() : 0) + 15 & ~15;
        int bh = Math.max(h, bitmap != null ? bitmap.getHeight() : 0) + 7 & ~7;
        if (bitmap != null) bitmap.recycle();
        try {
          bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
          bitmap = null;
          return; // no memory — better nothing than the text in clear
        }
        canvas = new Canvas(bitmap);
        pixels = new int[bw * bh];
        buffer = new int[bw * bh];
      }
      bitmap.eraseColor(0);
      int save = canvas.save();
      canvas.clipRect(0, 0, w, h);
      canvas.scale(w / (float) (right - left), h / (float) (bottom - top));
      canvas.translate(-left, -top);
      drawer.draw(canvas);
      canvas.restoreToCount(save);
      bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
      int radius = strength >= 50 ? 2 : 1;
      boxBlur(pixels, buffer, w, h, radius);
      boxBlur(pixels, buffer, w, h, radius);
      bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
      src.set(0, 0, w, h);
      dst.set(left, top, right, bottom);
      c.drawBitmap(bitmap, src, dst, paint);
    }

    private Gpu gpu;

    public void release () {
      if (gpu != null) {
        gpu.release();
        gpu = null;
      }
      if (bitmap != null) {
        bitmap.recycle();
        bitmap = null;
        canvas = null;
        pixels = buffer = null;
      }
    }
  }

  /** Apart, so Android 4 never loads the classes of Android 12 */
  @androidx.annotation.RequiresApi(31)
  private static final class Gpu {
    private final android.graphics.RenderNode node = new android.graphics.RenderNode("tgx101peek");
    private float radius = -1f;

    void draw (Canvas c, int left, int top, int right, int bottom, int strength, Drawer drawer) {
      // the same look as the hand-made blur: up to 10 dp
      float r = Math.max(1f, org.thunderdog.challegram.tool.Screen.dp(10f) * Math.min(100, strength) / 100f);
      if (r != radius) {
        radius = r;
        node.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.DECAL));
      }
      node.setPosition(left, top, right, bottom);
      android.graphics.RecordingCanvas rc = node.beginRecording();
      try {
        rc.translate(-left, -top);
        drawer.draw(rc);
      } finally {
        node.endRecording();
      }
      c.drawRenderNode(node);
    }

    void release () {
      node.discardDisplayList();
    }
  }

  /** Box blur over all four channels (premultiplied, so the edges fade out cleanly): horizontal, then vertical; a running
   *  sum — each pixel costs the same whatever the radius */
  private static void boxBlur (int[] px, int[] tmp, int w, int h, int r) {
    int div = r * 2 + 1;
    for (int y = 0; y < h; y++) {
      int row = y * w;
      int a = 0, rr = 0, g = 0, b = 0;
      for (int k = -r; k <= r; k++) {
        int p = px[row + Math.max(0, Math.min(w - 1, k))];
        a += p >>> 24; rr += (p >> 16) & 0xff; g += (p >> 8) & 0xff; b += p & 0xff;
      }
      for (int x = 0; x < w; x++) {
        tmp[row + x] = ((a / div) << 24) | ((rr / div) << 16) | ((g / div) << 8) | (b / div);
        int out = px[row + Math.max(0, x - r)], in = px[row + Math.min(w - 1, x + r + 1)];
        a += (in >>> 24) - (out >>> 24);
        rr += ((in >> 16) & 0xff) - ((out >> 16) & 0xff);
        g += ((in >> 8) & 0xff) - ((out >> 8) & 0xff);
        b += (in & 0xff) - (out & 0xff);
      }
    }
    for (int x = 0; x < w; x++) {
      int a = 0, rr = 0, g = 0, b = 0;
      for (int k = -r; k <= r; k++) {
        int p = tmp[Math.max(0, Math.min(h - 1, k)) * w + x];
        a += p >>> 24; rr += (p >> 16) & 0xff; g += (p >> 8) & 0xff; b += p & 0xff;
      }
      for (int y = 0; y < h; y++) {
        px[y * w + x] = ((a / div) << 24) | ((rr / div) << 16) | ((g / div) << 8) | (b / div);
        int out = tmp[Math.max(0, y - r) * w + x], in = tmp[Math.min(h - 1, y + r + 1) * w + x];
        a += (in >>> 24) - (out >>> 24);
        rr += ((in >> 16) & 0xff) - ((out >> 16) & 0xff);
        g += ((in >> 8) & 0xff) - ((out >> 8) & 0xff);
        b += (in & 0xff) - (out & 0xff);
      }
    }
  }
}
