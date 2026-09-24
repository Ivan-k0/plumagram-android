/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui.camera.legacy;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.ui.camera.CameraFeatures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.lambda.RunnableData;

/**
 * Camera2 backend used only for video messages, to get the phone's own preview
 * stabilization (CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION, Android 13+).
 *
 * Anything it can't do makes prepareCamera() fail, and CameraApi then asks the
 * manager to switch back to {@link CameraApiLegacy}. Photos and regular video are
 * not supported: this API is never used outside of video messages.
 *
 * Like Camera1 with setDisplayOrientation(), Camera2 sets the SurfaceTexture
 * transform so frames come out upright, so the round recorder can treat both
 * the same way.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class CameraApiCamera2 extends CameraApi {
  private static volatile boolean unsupported;

  /** Whether this device can use preview stabilization at all. Cheap after the first failure. */
  public static boolean isAvailable (Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || unsupported) {
      return false;
    }
    try {
      CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
      for (String id : cameraManager.getCameraIdList()) {
        if (supportsPreviewStabilization(cameraManager.getCameraCharacteristics(id))) {
          return true;
        }
      }
    } catch (Throwable t) {
      Log.w(Log.TAG_CAMERA, "Camera2 availability check failed", t);
    }
    unsupported = true;
    return false;
  }

  private static boolean supportsPreviewStabilization (CameraCharacteristics characteristics) {
    int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
    if (modes != null) {
      for (int mode : modes) {
        if (mode == CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION) {
          return true;
        }
      }
    }
    return false;
  }

  private static Handler callbackHandler;

  private static synchronized Handler callbackHandler () {
    if (callbackHandler == null) {
      HandlerThread thread = new HandlerThread("Camera2Callbacks");
      thread.start();
      callbackHandler = new Handler(thread.getLooper());
    }
    return callbackHandler;
  }

  private final CameraManager cameraManager;

  private final List<String> cameraIds = new ArrayList<>();
  private String rearCameraId, frontCameraId;

  private CameraDevice device;
  private CameraCaptureSession session;
  private CameraCharacteristics characteristics;
  private boolean isFrontFace;
  private int sensorOrientation;
  private Size previewSize;
  private Surface previewSurface;
  private boolean renderedFirstFrame;

  public CameraApiCamera2 (Context context, CameraManagerLegacy manager) {
    super(context, manager);
    this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
  }

  @Override
  public boolean hasHardwarePreviewStabilization () {
    return true;
  }

  // Camera selection

  private void loadCameraIds () throws CameraAccessException {
    rearCameraId = frontCameraId = null;
    for (String id : cameraManager.getCameraIdList()) {
      Integer facing = cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
      if (facing == null) {
        continue;
      }
      if (facing == CameraCharacteristics.LENS_FACING_FRONT && frontCameraId == null) {
        frontCameraId = id;
      } else if (facing == CameraCharacteristics.LENS_FACING_BACK && rearCameraId == null) {
        rearCameraId = id;
      }
    }
    cameraIds.clear();
    String first = manager.preferFrontFacingCamera() ? frontCameraId : rearCameraId;
    String second = manager.preferFrontFacingCamera() ? rearCameraId : frontCameraId;
    if (first != null) {
      cameraIds.add(first);
    }
    if (second != null) {
      cameraIds.add(second);
    }
  }

  @Override
  protected boolean prepareCamera () throws Throwable {
    loadCameraIds();
    setNumberOfCameras(cameraIds.size());
    if (cameraIds.isEmpty()) {
      unsupported = true;
      return false;
    }
    String cameraId = cameraIds.get(Math.min(getRequestedCameraIndex(), cameraIds.size() - 1));
    characteristics = cameraManager.getCameraCharacteristics(cameraId);
    if (!supportsPreviewStabilization(characteristics)) {
      Log.i(Log.TAG_CAMERA, "Camera2: no preview stabilization on camera %s", cameraId);
      unsupported = true;
      return false;
    }
    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
    isFrontFace = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
    Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
    sensorOrientation = orientation != null ? orientation : 90;

    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
    Size[] sizes = map != null ? map.getOutputSizes(SurfaceTexture.class) : null;
    if (sizes == null || sizes.length == 0) {
      unsupported = true;
      return false;
    }
    List<Size> sortedSizes = new ArrayList<>(Arrays.asList(sizes));
    int maxResolution = manager.getMaxResolution() != 0 ? manager.getMaxResolution() : 720;
    long desiredSquare = (long) maxResolution * maxResolution;
    Collections.sort(sortedSizes, (a, b) -> comparePreviewSizes(a.getWidth(), a.getHeight(), b.getWidth(), b.getHeight(), desiredSquare, 16f / 9f));
    previewSize = sortedSizes.get(0);
    Log.i(Log.TAG_CAMERA, "Camera2: camera %s, preview %dx%d, sensor orientation %d", cameraId, previewSize.getWidth(), previewSize.getHeight(), sensorOrientation);

    if (!openDevice(cameraId)) {
      return false;
    }

    CameraFeatures features = new CameraFeatures(false);
    if (isFrontFace) {
      features.add(CameraFeatures.FEATURE_FACING_FRONT);
    }
    features.add(CameraFeatures.FEATURE_FLASH_OFF);
    setCameraDeviceFeatures(features);

    if (U.isRotated(calculateDisplayOrientation())) {
      manager.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
    } else {
      manager.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
    }
    return true;
  }

  @SuppressLint("MissingPermission") // Checked by the round video controller before the camera is opened
  private boolean openDevice (String cameraId) throws Throwable {
    final CountDownLatch latch = new CountDownLatch(1);
    final CameraDevice[] result = new CameraDevice[1];
    cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
      @Override
      public void onOpened (@NonNull CameraDevice camera) {
        result[0] = camera;
        latch.countDown();
      }

      @Override
      public void onDisconnected (@NonNull CameraDevice camera) {
        camera.close();
        latch.countDown();
      }

      @Override
      public void onError (@NonNull CameraDevice camera, int error) {
        Log.w(Log.TAG_CAMERA, "Camera2: device error %d", error);
        camera.close();
        latch.countDown();
      }
    }, callbackHandler());
    if (!latch.await(3, TimeUnit.SECONDS) || result[0] == null) {
      Log.w(Log.TAG_CAMERA, "Camera2: failed to open camera %s", cameraId);
      return false;
    }
    device = result[0];
    return true;
  }

  @Override
  protected boolean openCamera (SurfaceTexture texture) throws Throwable {
    if (device == null) {
      return false;
    }
    texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
    previewSurface = new Surface(texture);

    final CountDownLatch latch = new CountDownLatch(1);
    final CameraCaptureSession[] result = new CameraCaptureSession[1];
    //noinspection deprecation
    device.createCaptureSession(Collections.singletonList(previewSurface), new CameraCaptureSession.StateCallback() {
      @Override
      public void onConfigured (@NonNull CameraCaptureSession session) {
        result[0] = session;
        latch.countDown();
      }

      @Override
      public void onConfigureFailed (@NonNull CameraCaptureSession session) {
        Log.w(Log.TAG_CAMERA, "Camera2: session configuration failed");
        latch.countDown();
      }
    }, callbackHandler());
    if (!latch.await(3, TimeUnit.SECONDS) || result[0] == null) {
      return false;
    }
    session = result[0];

    CaptureRequest.Builder request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
    request.addTarget(previewSurface);
    request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION);
    request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
    Range<Integer> fps = pickFpsRange();
    if (fps != null) {
      request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps);
    }
    renderedFirstFrame = false;
    session.setRepeatingRequest(request.build(), new CameraCaptureSession.CaptureCallback() {
      @Override
      public void onCaptureCompleted (@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
        if (!renderedFirstFrame) {
          renderedFirstFrame = true;
          manager.onRenderedFirstFrame();
        }
      }
    }, callbackHandler());
    return true;
  }

  private Range<Integer> pickFpsRange () {
    Range<Integer>[] ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
    if (ranges == null) {
      return null;
    }
    Range<Integer> best = null;
    for (Range<Integer> range : ranges) {
      if (range.getUpper() == 30 && (best == null || range.getLower() > best.getLower())) {
        best = range;
      }
    }
    return best;
  }

  @Override
  protected void closeCamera () throws Throwable {
    if (session != null) {
      try {
        session.close();
      } catch (Throwable t) {
        Log.w(Log.TAG_CAMERA, "Camera2: cannot close session", t);
      }
      session = null;
    }
    if (device != null) {
      manager.resetRenderState(false);
      try {
        device.close();
      } catch (Throwable t) {
        Log.w(Log.TAG_CAMERA, "Camera2: cannot close device", t);
      }
      device = null;
      setCameraDeviceFeatures(null);
    }
    if (previewSurface != null) {
      previewSurface.release();
      previewSurface = null;
    }
  }

  // Orientation

  @Override
  protected int calculateDisplayOrientation () {
    // Same meaning as in CameraApiLegacy: rotation between the sensor and the display.
    int degrees = this.mDisplayOrientation;
    if (isFrontFace) {
      return (360 - (sensorOrientation + degrees) % 360) % 360;
    } else {
      return (sensorOrientation - degrees + 360) % 360;
    }
  }

  @Override
  protected int getSensorOrientation () {
    return sensorOrientation;
  }

  @Override
  protected int getCameraOutputWidth () {
    return previewSize.getWidth();
  }

  @Override
  protected int getCameraOutputHeight () {
    return previewSize.getHeight();
  }

  @Override
  protected void onDisplayOrientationChanged () { }

  @Override
  protected void onForcedOrientationChange () { }

  // Switching cameras

  @Override
  protected void onNextCameraSourceRequested () {
    final boolean isActive = isCameraActive;
    if (mNumberOfCameras > 1) {
      manager.resetRenderState(true);
      int nextCameraIndex = getNextCameraIndex();
      boolean forward = nextCameraIndex >= getRequestedCameraIndex();
      boolean toFrontFace = cameraIds.get(nextCameraIndex).equals(frontCameraId);
      manager.onCameraSourceChange(false, forward, toFrontFace);
      if (isActive) {
        setCameraActive(false);
      }
      setRequestedCameraIndex(nextCameraIndex);
      if (isActive) {
        setCameraActive(true);
      }
      manager.onCameraSourceChange(true, forward, toFrontFace);
    }
  }

  @Override
  protected void onResetRequestedSettings () {
    rearCameraId = frontCameraId = null;
  }

  // Not used for video messages

  @Override
  protected void onPreviewSizeChanged (int newWidth, int newHeight) { }

  @Override
  protected void onFlashModeChange (int newFlashMode) { }

  @Override
  protected void onZoomChanged (float zoom) { }

  @Override
  protected void onTakePhoto (int trimWidth, int trimHeight, int orientation) {
    manager.onTakeMediaError(false);
  }

  @Override
  public boolean isVideoRecordSupported () {
    return false;
  }

  @Override
  protected void onStartVideoCapture () {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void onFinishVideoCapture (boolean saveFile, RunnableData<ImageGalleryFile> callback) { }
}
