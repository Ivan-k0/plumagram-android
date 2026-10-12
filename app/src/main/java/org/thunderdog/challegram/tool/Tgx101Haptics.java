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
package org.thunderdog.challegram.tool;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * TGx101 (user 2026-10-12 «нужно добавить вибрацию в систему… делай всё»): one place for the app's taps under the finger.
 * It drives the vibration motor itself — the system «touch feedback» switch (off on many phones) silenced the view-based
 * haptics — and obeys MagiX «Вибрация» and its strength (light / normal / strong).
 */
public final class Tgx101Haptics {
  private Tgx101Haptics () { }

  /** A step: a slider notch, an item taking a new place while dragging */
  public static final int TICK = 0;
  /** Something happened: swipe-reply threshold, a reaction set, lock, copy, delete */
  public static final int CLICK = 1;
  /** A big moment: recording starts or is thrown away */
  public static final int HEAVY = 2;
  /** Something went wrong */
  public static final int ERROR = 3;

  public static final int STRENGTH_LIGHT = 0, STRENGTH_NORMAL = 1, STRENGTH_STRONG = 2;

  private static Vibrator vibrator;
  private static long lastTick;

  private static Vibrator vibrator () {
    if (vibrator == null) {
      vibrator = (Vibrator) AppContext.get().getSystemService(Context.VIBRATOR_SERVICE);
    }
    return vibrator;
  }

  public static void play (int kind) {
    if (!Settings.instance().tgx101Haptics()) return;
    if (kind == TICK) {
      long now = SystemClock.uptimeMillis();
      if (now - lastTick < 35) return; // a fast slider must not buzz
      lastTick = now;
    }
    Vibrator v = vibrator();
    if (v == null || !v.hasVibrator()) return;
    int strength = Settings.instance().tgx101HapticStrength();
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        int effect;
        switch (kind) {
          case TICK: effect = strength == STRENGTH_STRONG ? VibrationEffect.EFFECT_CLICK : VibrationEffect.EFFECT_TICK; break;
          case HEAVY: effect = strength == STRENGTH_LIGHT ? VibrationEffect.EFFECT_CLICK : VibrationEffect.EFFECT_HEAVY_CLICK; break;
          case ERROR: effect = VibrationEffect.EFFECT_DOUBLE_CLICK; break;
          default: effect = strength == STRENGTH_LIGHT ? VibrationEffect.EFFECT_TICK : strength == STRENGTH_STRONG ? VibrationEffect.EFFECT_HEAVY_CLICK : VibrationEffect.EFFECT_CLICK;
        }
        v.vibrate(VibrationEffect.createPredefined(effect));
      } else {
        int ms = kind == TICK ? 8 : kind == HEAVY ? 30 : kind == ERROR ? 45 : 15;
        if (strength == STRENGTH_LIGHT) ms = Math.max(5, ms * 2 / 3);
        if (strength == STRENGTH_STRONG) ms = ms * 3 / 2;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          if (kind == ERROR) {
            v.vibrate(VibrationEffect.createWaveform(new long[] {0, ms, 60, ms}, -1));
          } else {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
          }
        } else {
          //noinspection deprecation
          v.vibrate(kind == ERROR ? ms * 2 : ms);
        }
      }
    } catch (Throwable ignored) { }
  }
}
