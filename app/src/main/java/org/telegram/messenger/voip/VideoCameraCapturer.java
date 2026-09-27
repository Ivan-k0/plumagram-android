/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/tgx101-android)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.telegram.messenger.voip;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.Keep;

import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.voip.Tgx101Video;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.SurfaceTextureHelper;

/**
 * TGx101: camera for video calls. tgcalls' Android platform code (platform/android/AndroidContext.cpp,
 * VideoCameraCapturer.cpp) looks this class up by this exact name and calls init, onStateChanged,
 * onAspectRatioRequested and onDestroy. Frames go to tgcalls through the native capturer observer.
 */
@Keep
public class VideoCameraCapturer {
  private static final int WIDTH = 960, HEIGHT = 540, FPS = 30; // frames are copied to memory for encoding: 540p keeps calls smooth
  private static final int STATE_ACTIVE = 2; // tgcalls::VideoState::Active

  private static HandlerThread thread;
  private final Handler handler;

  private CameraVideoCapturer capturer;
  private SurfaceTextureHelper textureHelper;
  private long nativePtr;
  private boolean capturing;

  @Keep
  public VideoCameraCapturer () {
    synchronized (VideoCameraCapturer.class) {
      if (thread == null) {
        thread = new HandlerThread("TGx101Camera");
        thread.start();
      }
    }
    handler = new Handler(thread.getLooper());
  }

  /** Called again with a new pointer when the camera is switched. */
  @Keep
  public void init (long ptr, boolean useFrontCamera) {
    handler.post(() -> {
      release();
      nativePtr = ptr;
      Context context = UI.getAppContext();
      CameraEnumerator enumerator = Camera2Enumerator.isSupported(context) ? new Camera2Enumerator(context) : new Camera1Enumerator(true);
      String device = null;
      for (String name : enumerator.getDeviceNames()) {
        if (enumerator.isFrontFacing(name) == useFrontCamera) {
          device = name;
          break;
        }
      }
      if (device == null && enumerator.getDeviceNames().length > 0) {
        device = enumerator.getDeviceNames()[0];
      }
      if (device == null) {
        return;
      }
      capturer = enumerator.createCapturer(device, null);
      textureHelper = SurfaceTextureHelper.create("TGx101CameraTexture", Tgx101Video.eglContext());
      capturer.initialize(textureHelper, context, nativeGetJavaVideoCapturerObserver(ptr));
    });
  }

  @Keep
  public void onStateChanged (long ptr, int state) {
    handler.post(() -> {
      if (ptr != nativePtr || capturer == null) return;
      if (state == STATE_ACTIVE && !capturing) {
        capturer.startCapture(WIDTH, HEIGHT, FPS);
        capturing = true;
      } else if (state != STATE_ACTIVE && capturing) {
        stopCapture();
      }
    });
  }

  @Keep
  public void onAspectRatioRequested (float aspectRatio) { }

  @Keep
  public void onDestroy () {
    handler.post(this::release);
  }

  private void stopCapture () {
    try {
      capturer.stopCapture();
    } catch (InterruptedException ignored) { }
    capturing = false;
  }

  private void release () {
    if (capturer != null) {
      if (capturing) {
        stopCapture();
      }
      capturer.dispose();
      capturer = null;
    }
    if (textureHelper != null) {
      textureHelper.dispose();
      textureHelper = null;
    }
    nativePtr = 0;
  }

  private static native CapturerObserver nativeGetJavaVideoCapturerObserver (long ptr);
}
