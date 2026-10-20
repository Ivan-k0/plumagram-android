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
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.unsorted.AppContext;

/**
 * TGx101 (user 2026-10-20 18:24 «во время звонка нужно дать возможность включать фонарик»): the phone's flashlight,
 * on and off from the call screen. Turned off again when the call ends. While the back camera films for a video call
 * the system does not lend the flash — then a short note says so.
 */
public final class Tgx101Torch {
  private Tgx101Torch () { }

  private static boolean on;
  private static String cameraId;

  public static boolean isAvailable () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && findCamera() != null;
  }

  public static boolean isOn () {
    return on;
  }

  /** @return the new state */
  public static boolean toggle () {
    set(!on);
    return on;
  }

  public static void set (boolean enable) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
    if (!enable && !on) return;
    String id = findCamera();
    if (id == null) return;
    try {
      CameraManager manager = (CameraManager) AppContext.get().getSystemService(Context.CAMERA_SERVICE);
      manager.setTorchMode(id, enable);
      on = enable;
      org.thunderdog.challegram.Tgx101Diag.mark("torch: " + (enable ? "on" : "off"));
    } catch (Throwable t) {
      on = false;
      org.thunderdog.challegram.Tgx101Diag.mark("torch: failed " + t.getClass().getSimpleName() + " " + t.getMessage());
      if (enable) UI.showToast(Lang.getString(R.string.Tgx101TorchBusy), android.widget.Toast.LENGTH_SHORT);
    }
  }

  private static String findCamera () {
    if (cameraId != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return cameraId;
    try {
      CameraManager manager = (CameraManager) AppContext.get().getSystemService(Context.CAMERA_SERVICE);
      for (String id : manager.getCameraIdList()) {
        CameraCharacteristics c = manager.getCameraCharacteristics(id);
        Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        Integer facing = c.get(CameraCharacteristics.LENS_FACING);
        if (Boolean.TRUE.equals(flash) && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
          return cameraId = id;
        }
      }
    } catch (Throwable ignored) { }
    return null;
  }
}
