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

import android.graphics.Canvas;
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
 * Android 12+ blurs on the GPU; older ones get stand-ins — plates over the texts and plain circles for the avatars.
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
    return all || chats.isEmpty();
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
    // «only the chosen ones» with none chosen works everywhere: the switch must never do nothing (user 2026-10-22)
    if (!on || (!all && !chats.isEmpty() && !chats.contains(chatId))) return 0;
    // (crash log 2026-10-25, Moto Z2 Play 0.2.295): the Storage row isn't a chat — its stand-in id made TDLib's ChatId.getType
    // throw while the hidden avatar's letters were drawn
    if (chatId == TGChat.TGX101_STORAGE_ID) return 0;
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

  /** Blur needs the GPU (Android 12+, hardware drawing); without it the row draws plain stand-ins — a plate over the text, a
   *  circle for the avatar (user 2026-10-22 «заглушки для старых»: the hand-made blur cost the main thread on every bind) */
  public static boolean canBlur (Canvas c) {
    return android.os.Build.VERSION.SDK_INT >= 31 && c.isHardwareAccelerated();
  }

  /** One part of one row */
  public static final class Painter {
    private Gpu gpu;

    /** Draws what {@code drawer} draws inside left..bottom (the part's bounds, with room for the blur to spread): as is,
     *  blurred, or — where there is no blur — {@code standIn} instead */
    public void draw (Canvas c, int left, int top, int right, int bottom, int strength, Drawer drawer, Drawer standIn) {
      if (strength <= 0 || right <= left || bottom <= top) {
        drawer.draw(c);
      } else if (canBlur(c)) {
        if (gpu == null) gpu = new Gpu();
        gpu.draw(c, left, top, right, bottom, strength, drawer);
      } else {
        standIn.draw(c);
      }
    }

    public void release () {
      if (gpu != null) {
        gpu.release();
        gpu = null;
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

}
