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
package org.thunderdog.challegram.voip;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.tool.UI;
import org.webrtc.EglBase;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import me.vkryl.core.reference.ReferenceList;
import org.thunderdog.challegram.voip.annotation.VideoState;

/**
 * TGx101: video in one-to-one calls. The camera runs only while it is on during a call.
 * Native part: jni/tgvoip/tgx101_video.cpp.
 */
public final class Tgx101Video {
  private Tgx101Video () { }

  public interface Listener {
    default void onLocalVideoChanged (boolean enabled) { }
    default void onRemoteVideoChanged (boolean active) { }
  }

  /** Forwards frames to whatever renderer the call screen currently shows. */
  public static final class ProxySink implements VideoSink {
    private volatile @Nullable VideoSink target;

    public void setTarget (@Nullable VideoSink target) {
      this.target = target;
    }

    @Override
    public void onFrame (VideoFrame frame) {
      VideoSink sink = target;
      if (sink != null) {
        sink.onFrame(frame);
      }
    }
  }

  private static EglBase eglBase;
  public static final ProxySink localSink = new ProxySink();
  public static final ProxySink remoteSink = new ProxySink();
  private static final ReferenceList<Listener> listeners = new ReferenceList<>(true);

  private static long instancePtr;
  private static long capturePtr;
  private static boolean frontCamera = true;
  private static boolean remoteActive;
  private static long requestedVideoUserId, requestedVideoTime;

  public static synchronized EglBase.Context eglContext () {
    if (eglBase == null) {
      eglBase = EglBase.create();
    }
    return eglBase.getEglBaseContext();
  }

  public static void addListener (Listener listener) {
    listeners.add(listener);
  }

  public static void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  // Outgoing video calls

  /** The next call to this user is a video call. */
  public static void requestVideoCall (long userId) {
    requestedVideoUserId = userId;
    requestedVideoTime = android.os.SystemClock.elapsedRealtime();
  }

  public static boolean takeVideoRequest (long userId) {
    // A request older than 30 s is stale (the call didn't happen), so a later audio call stays audio
    boolean isVideo = requestedVideoUserId == userId && android.os.SystemClock.elapsedRealtime() - requestedVideoTime < 30_000;
    requestedVideoUserId = 0;
    return isVideo;
  }

  // Call lifecycle (TgCallsController)

  static void onInstanceCreated (long ptr, TdApi.Call call) {
    instancePtr = ptr;
    remoteActive = false;
    nativeSetRemoteSink(ptr, remoteSink);
    if (call.isVideo && hasCameraPermission()) {
      UI.post(() -> setCameraEnabled(true));
    }
  }

  static void onInstanceDestroyed (long ptr) {
    if (instancePtr != ptr) return;
    stopCamera();
    nativeSetRemoteSink(ptr, null);
    instancePtr = 0;
    UI.post(() -> {
      setRemoteActive(false);
      for (Listener listener : listeners) listener.onLocalVideoChanged(false);
    });
  }

  static void onRemoteVideoState (int videoState) {
    UI.post(() -> setRemoteActive(videoState == VideoState.ACTIVE));
  }

  private static void setRemoteActive (boolean active) {
    if (remoteActive != active) {
      remoteActive = active;
      for (Listener listener : listeners) listener.onRemoteVideoChanged(active);
    }
  }

  // Camera

  public static boolean isCallActive () {
    return instancePtr != 0;
  }

  public static boolean isCameraEnabled () {
    return capturePtr != 0;
  }

  public static boolean isRemoteVideoActive () {
    return remoteActive;
  }

  public static boolean hasCameraPermission () {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(UI.getAppContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
  }

  public static void setCameraEnabled (boolean enabled) {
    if (instancePtr == 0 || enabled == isCameraEnabled()) return;
    if (enabled) {
      long ptr = nativeCreateCapture(frontCamera);
      if (ptr == 0) return;
      capturePtr = ptr;
      nativeSetCaptureOutput(ptr, localSink);
      nativeAttachCapture(instancePtr, ptr);
      nativeSetCaptureActive(ptr, true);
    } else {
      stopCamera();
    }
    for (Listener listener : listeners) listener.onLocalVideoChanged(enabled);
  }

  public static void switchCamera () {
    if (capturePtr != 0) {
      frontCamera = !frontCamera;
      nativeSwitchCamera(capturePtr, frontCamera);
    }
  }

  public static boolean isFrontCamera () {
    return frontCamera;
  }

  private static void stopCamera () {
    long ptr = capturePtr;
    if (ptr == 0) return;
    capturePtr = 0;
    if (instancePtr != 0) {
      nativeAttachCapture(instancePtr, 0);
    }
    nativeDestroyCapture(ptr);
  }

  // Native

  private static native long nativeCreateCapture (boolean front);
  private static native void nativeSetCaptureOutput (long ptr, @Nullable VideoSink sink);
  private static native void nativeSetCaptureActive (long ptr, boolean active);
  private static native void nativeSwitchCamera (long ptr, boolean front);
  private static native void nativeDestroyCapture (long ptr);
  private static native void nativeAttachCapture (long instancePtr, long capturePtr);
  private static native void nativeSetRemoteSink (long instancePtr, @Nullable VideoSink sink);
}
