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
package org.thunderdog.challegram;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.HashMap;
import java.util.Map;

/**
 * Diagnostics only (2026-11-14, «расход при активном экране»: an idle chat drew ~120 frames a second, 70 % of a core,
 * whatever was on the screen): before each frame, which views asked to be drawn again. Every 3 s while frames keep
 * coming, the journal tells the most frequent ones — class, id and parent — so the one that never stops can be named.
 */
public final class Tgx101RedrawWatch {
  private Tgx101RedrawWatch () { }

  private static final Map<String, int[]> counts = new HashMap<>();
  private static long windowStart;
  private static int frames;
  private static int minuteFrames;
  private static long lastCpuTicks = -1, lastCpuAt;
  private static boolean loadLoopOn;

  public static void install (View decor) {
    if (!BuildConfig.TGX101_DIAG || decor == null) return;
    if (android.os.Build.VERSION.SDK_INT < 16) return;
    decor.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
      @Override
      public void onDraw () {
        try {
          frames++;
          minuteFrames++;
          collect(decor, 0);
          long now = SystemClock.uptimeMillis();
          if (windowStart == 0) windowStart = now;
          if (now - windowStart >= 3000) {
            if (frames >= 60) report(frames, now - windowStart);
            counts.clear();
            frames = 0;
            windowStart = now;
          }
        } catch (Throwable ignored) { }
      }
    });
    startLoadLoop(decor);
  }

  // (4PDA 2026-11-15 «с последними версиями существенно потяжелело… пропала лёгкость»): once a minute while the app is
  // on the screen — how much of one CPU core the app took, how many frames it drew, memory and the screen on top.
  // The minutes when the phone felt heavy can then be compared with the light ones
  private static void startLoadLoop (View decor) {
    if (loadLoopOn) return;
    loadLoopOn = true;
    decor.postDelayed(new Runnable() {
      @Override
      public void run () {
        try {
          long ticks = cpuTicks();
          long now = SystemClock.uptimeMillis();
          if (lastCpuTicks >= 0 && ticks >= 0 && org.thunderdog.challegram.tool.UI.getContext(decor.getContext()).getActivityState() == org.thunderdog.challegram.tool.UI.State.RESUMED) { // (window focus was off with a sheet up — no line came)
            long ms = Math.max(1, now - lastCpuAt);
            long cpuPercent = (ticks - lastCpuTicks) * 10 * 100 / ms; // ticks are 10 ms
            Object top = org.thunderdog.challegram.tool.UI.getCurrentStackItem(decor.getContext());
            Runtime r = Runtime.getRuntime();
            long mb = 1024 * 1024;
            Tgx101Diag.mark("load: last " + (ms / 1000) + " s — CPU " + cpuPercent + " % of a core, " + minuteFrames + " frames (" + (minuteFrames * 1000L / ms) + "/s), heap " +
              (r.totalMemory() - r.freeMemory()) / mb + "/" + r.maxMemory() / mb + " MB, native " + android.os.Debug.getNativeHeapAllocatedSize() / mb + " MB, threads " + Thread.activeCount() +
              ", on " + (top != null ? top.getClass().getSimpleName() : "?"));
          }
          lastCpuTicks = ticks;
          lastCpuAt = now;
          minuteFrames = 0;
        } catch (Throwable ignored) { }
        decor.postDelayed(this, 60_000);
      }
    }, 60_000);
  }

  /** utime + stime of this process, in clock ticks */
  private static long cpuTicks () {
    try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader("/proc/self/stat"))) {
      String line = reader.readLine();
      if (line == null) return -1;
      String[] f = line.substring(line.lastIndexOf(')') + 2).split(" ");
      return Long.parseLong(f[11]) + Long.parseLong(f[12]);
    } catch (Throwable t) {
      return -1;
    }
  }

  /** the deepest dirty views: a dirty parent with a dirty child counts only the child */
  private static boolean collect (View v, int depth) {
    if (v.getVisibility() != View.VISIBLE || depth > 40) return false;
    boolean childDirty = false;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        if (collect(g.getChildAt(i), depth + 1)) childDirty = true;
      }
    }
    boolean dirty = android.os.Build.VERSION.SDK_INT >= 29 ? v.isDirty() : v.isDirty();
    if (dirty && !childDirty) {
      String parent = v.getParent() instanceof View ? ((View) v.getParent()).getClass().getSimpleName() : "-";
      String key = v.getClass().getName().replace("org.thunderdog.challegram.", "") + idName(v) + " in " + parent;
      int[] c = counts.get(key);
      if (c == null) counts.put(key, c = new int[1]);
      c[0]++;
    }
    return dirty || childDirty;
  }

  private static String idName (View v) {
    int id = v.getId();
    if (id == View.NO_ID) return "";
    try {
      return "#" + v.getResources().getResourceEntryName(id);
    } catch (Throwable t) {
      return "#" + Integer.toHexString(id);
    }
  }

  private static void report (int frames, long ms) {
    StringBuilder b = new StringBuilder("redraw: ").append(frames).append(" frames in ").append(ms).append(" ms, dirty:");
    java.util.List<Map.Entry<String, int[]>> list = new java.util.ArrayList<>(counts.entrySet());
    java.util.Collections.sort(list, (a, c) -> Integer.compare(c.getValue()[0], a.getValue()[0]));
    for (int i = 0; i < Math.min(6, list.size()); i++) {
      b.append(i == 0 ? " " : " | ").append(list.get(i).getKey()).append(" ×").append(list.get(i).getValue()[0]);
    }
    Tgx101Diag.mark(b.toString());
  }
}
