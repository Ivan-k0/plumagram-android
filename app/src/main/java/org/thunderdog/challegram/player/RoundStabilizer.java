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
package org.thunderdog.challegram.player;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;
import android.view.Surface;
import android.view.WindowManager;

import org.thunderdog.challegram.tool.UI;

/**
 * Gyroscope-based digital stabilization for video messages.
 *
 * Hand shake is integrated from the gyroscope and high-pass filtered, so slow
 * intentional movement passes through and only fast jitter is compensated.
 * The picture is zoomed in slightly and shifted/rotated against the shake in
 * output (NDC) space. Video messages are shown as a circle, so rotation never
 * exposes the edges, and a shift of up to ZOOM - 1 stays inside the picture.
 *
 * Assumes portrait recording; does nothing when the display is rotated.
 */
public class RoundStabilizer implements SensorEventListener {
  private static final float ZOOM = 1.1f;
  private static final float MAX_SHIFT = ZOOM - 1f;
  // NDC shift per radian: 1 / tan(half of the square's field of view, ~50 degrees).
  private static final float SHIFT_PER_RADIAN = 2.0f;
  private static final float MAX_ROLL_RADIANS = (float) Math.toRadians(10);
  // High-pass time constant: movement slower than this is treated as intentional.
  private static final float FOLLOW_SECONDS = 0.5f;

  private final SensorManager sensorManager;
  private final Sensor gyroscope;
  private boolean isPortrait;
  private boolean started;

  private long lastTimestampNs;
  private volatile float angleX, angleY, angleZ;

  public RoundStabilizer () {
    Context context = UI.getAppContext();
    sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    gyroscope = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
  }

  public void start () {
    if (started || gyroscope == null) {
      return;
    }
    WindowManager windowManager = (WindowManager) UI.getAppContext().getSystemService(Context.WINDOW_SERVICE);
    isPortrait = windowManager == null || windowManager.getDefaultDisplay().getRotation() == Surface.ROTATION_0;
    angleX = angleY = angleZ = 0;
    lastTimestampNs = 0;
    started = sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
  }

  public void stop () {
    if (started) {
      sensorManager.unregisterListener(this);
      started = false;
    }
  }

  public boolean isActive () {
    return started && isPortrait;
  }

  @Override
  public void onSensorChanged (SensorEvent event) {
    if (lastTimestampNs != 0) {
      float dt = (event.timestamp - lastTimestampNs) / 1_000_000_000f;
      if (dt > 0 && dt < 0.1f) {
        float decay = (float) Math.exp(-dt / FOLLOW_SECONDS);
        angleX = (angleX + event.values[0] * dt) * decay;
        angleY = (angleY + event.values[1] * dt) * decay;
        angleZ = (angleZ + event.values[2] * dt) * decay;
      }
    }
    lastTimestampNs = event.timestamp;
  }

  @Override
  public void onAccuracyChanged (Sensor sensor, int accuracy) { }

  /**
   * Writes {@code stabilization * base} to {@code out}, or copies {@code base} if inactive.
   * Signs assume the front camera picture is mirrored, like the preview.
   */
  public void apply (float[] out, float[] base, boolean isFrontFacing) {
    if (!isActive()) {
      System.arraycopy(base, 0, out, 0, 16);
      return;
    }
    float ax = angleX, ay = angleY, az = angleZ;
    // Tilting the top towards the user makes the rear camera look up (scene moves down)
    // and the front camera look down (scene moves up).
    float shiftY = clamp((isFrontFacing ? -ax : ax) * SHIFT_PER_RADIAN, MAX_SHIFT);
    // Turning left/right: the rear camera and the mirrored front picture both move the same way.
    float shiftX = clamp(-ay * SHIFT_PER_RADIAN, MAX_SHIFT);
    float roll = clamp(az, MAX_ROLL_RADIANS);

    // Called from both the preview and the encoder threads, so no shared scratch matrix.
    float[] stabilization = new float[16];
    Matrix.setIdentityM(stabilization, 0);
    Matrix.translateM(stabilization, 0, shiftX, shiftY, 0);
    Matrix.rotateM(stabilization, 0, (float) Math.toDegrees(roll), 0, 0, 1);
    Matrix.scaleM(stabilization, 0, ZOOM, ZOOM, 1);
    Matrix.multiplyMM(out, 0, stabilization, 0, base, 0);
  }

  private static float clamp (float value, float max) {
    return Math.max(-max, Math.min(max, value));
  }
}
