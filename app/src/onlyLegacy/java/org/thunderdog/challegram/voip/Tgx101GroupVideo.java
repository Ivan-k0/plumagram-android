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
package org.thunderdog.challegram.voip;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

/** TGx101: Android 4 builds have no WebRTC — no group call video there. */
public final class Tgx101GroupVideo {
  private Tgx101GroupVideo () { }

  public static boolean isSupported () {
    return false;
  }

  public static @Nullable View createView (Context context) {
    return null;
  }

  public static @Nullable Object sinkOf (@Nullable View view) {
    return null;
  }

  public static long createCapture (boolean front, @Nullable View preview) {
    return 0;
  }

  public static long createScreenCapture (android.content.Intent projectionData) {
    return 0;
  }

  public static void switchCamera (long capture, boolean front) { }

  public static void destroyCapture (long capture) { }

  public static void release (@Nullable View view) { }
}
