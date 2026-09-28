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

import org.drinkless.tdlib.TdApi;

/** TGx101: Android 4 builds have no calls (no WebRTC), so video calls are a no-op there. */
public final class Tgx101Video {
  private Tgx101Video () { }

  public static void requestVideoCall (long userId) { }
  public static boolean takeVideoRequest (long userId) { return false; }
  static void onInstanceCreated (long ptr, TdApi.Call call) { }
  static void onInstanceDestroyed (long ptr) { }
  static void onRemoteVideoState (int videoState) { }
  public static boolean isCallActive () { return false; }
  public static boolean isCameraEnabled () { return false; }
  public static boolean isRemoteVideoActive () { return false; }
  public static boolean hasCameraPermission () { return false; }
  public static void setCameraEnabled (boolean enabled) { }
  public static void switchCamera () { }
  public static boolean isFrontCamera () { return true; }
}
