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

import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;

/**
 * TGx101 (group call video, 2026-10-19): a view that shows one participant's video in a group call — the same renderer
 * as one-to-one video calls. The view itself is the sink given to Tgx101GroupCall.setVideoSink.
 */
public final class Tgx101GroupVideo {
  private Tgx101GroupVideo () { }

  public static boolean isSupported () {
    return true;
  }

  public static @Nullable View createView (Context context) {
    SurfaceViewRenderer view = new SurfaceViewRenderer(context);
    view.init(Tgx101Video.eglContext(), null);
    view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
    view.setEnableHardwareScaler(false); // a changing resolution resized the surface and blinked (as in Tgx101CallVideo)
    view.setZOrderMediaOverlay(true); // over the call's background
    return view;
  }

  public static @Nullable Object sinkOf (@Nullable View view) {
    return view instanceof SurfaceViewRenderer ? view : null;
  }

  /** the camera for a group call, its picture also in the preview (a view from createView), 0 — failed */
  public static long createCapture (boolean front, @Nullable View preview) {
    return Tgx101Video.groupCaptureCreate(front, preview instanceof SurfaceViewRenderer ? (SurfaceViewRenderer) preview : null);
  }

  /** the screen for screen sharing, from the system's «Start recording?» permission data; 0 — failed */
  public static long createScreenCapture (android.content.Intent projectionData) {
    return Tgx101Video.groupScreenCaptureCreate(projectionData);
  }

  public static void switchCamera (long capture, boolean front) {
    Tgx101Video.groupCaptureSwitch(capture, front);
  }

  public static void destroyCapture (long capture) {
    Tgx101Video.groupCaptureDestroy(capture);
  }

  public static void release (@Nullable View view) {
    if (view instanceof SurfaceViewRenderer) {
      ((SurfaceViewRenderer) view).release();
    }
  }
}
