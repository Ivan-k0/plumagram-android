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
package org.thunderdog.challegram.voip;

/**
 * TGx101 (group call logs 2026-10-16: «UI STALL 877–958 ms» in AudioManager.setMode when a 1:1 call grew into a group
 * one, the screen froze mid-transition): the calls' audio mode and speaker switches run on one thread of their own —
 * one thread, so they still happen in the order they were asked for.
 */
public final class Tgx101Audio {
  private Tgx101Audio () { }

  private static final java.util.concurrent.ExecutorService EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "tgx101-audio");
    t.setDaemon(true);
    return t;
  });

  public static void run (Runnable act) {
    EXECUTOR.execute(() -> {
      try {
        act.run();
      } catch (Throwable t) {
        org.thunderdog.challegram.Tgx101Diag.mark("audio: " + t.getClass().getSimpleName() + " " + t.getMessage());
      }
    });
  }
}
