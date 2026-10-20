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
  // (user 2026-10-20 «стабилизацию гироскопом — красиво, по уму») the angles that use up the whole margin
  private static final float LIMIT_TILT = MAX_SHIFT / SHIFT_PER_RADIAN;
  // Near the margin the picture comes back to the centre up to this many times faster: after a turn it no longer
  // sits at the edge for half a second, where the shake passed through uncorrected
  private static final float EDGE_PULL = 6f;

  private final SensorManager sensorManager;
  private final Sensor gyroscope;
  private boolean isPortrait;
  private boolean started;

  private long lastTimestampNs;
  private float angleX, angleY, angleZ;

  // Filtered angles with their sensor timestamps, so each camera frame can be corrected
  // with the rotation at the moment it was captured rather than at the moment it's drawn
  // (camera frames arrive ~50-100 ms late, enough to turn correction into amplification).
  private static final int HISTORY_SIZE = 512;
  private final long[] historyTime = new long[HISTORY_SIZE];
  private final float[] historyAngles = new float[HISTORY_SIZE * 3];
  private int historyCount, historyHead;
  private final Object historyLock = new Object();

  public RoundStabilizer () {
    Context context = org.thunderdog.challegram.unsorted.AppContext.get();
    sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    gyroscope = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
  }

  public void start () {
    if (started || gyroscope == null) {
      return;
    }
    WindowManager windowManager = (WindowManager) org.thunderdog.challegram.unsorted.AppContext.get().getSystemService(Context.WINDOW_SERVICE);
    isPortrait = windowManager == null || windowManager.getDefaultDisplay().getRotation() == Surface.ROTATION_0;
    angleX = angleY = angleZ = 0;
    lastTimestampNs = 0;
    synchronized (historyLock) {
      historyCount = historyHead = 0;
    }
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
        angleX = follow(angleX + event.values[0] * dt, LIMIT_TILT, dt);
        angleY = follow(angleY + event.values[1] * dt, LIMIT_TILT, dt);
        angleZ = follow(angleZ + event.values[2] * dt, MAX_ROLL_RADIANS, dt);
      }
    }
    lastTimestampNs = event.timestamp;
    synchronized (historyLock) {
      historyTime[historyHead] = event.timestamp;
      historyAngles[historyHead * 3] = angleX;
      historyAngles[historyHead * 3 + 1] = angleY;
      historyAngles[historyHead * 3 + 2] = angleZ;
      historyHead = (historyHead + 1) % HISTORY_SIZE;
      historyCount = Math.min(historyCount + 1, HISTORY_SIZE);
    }
  }

  /** High-pass step: still slow in the middle, the faster the closer the angle is to the edge of the margin */
  private static float follow (float angle, float limit, float dt) {
    float edge = Math.min(1f, Math.abs(angle) / limit);
    float seconds = FOLLOW_SECONDS / (1f + EDGE_PULL * edge * edge);
    return angle * (float) Math.exp(-dt / seconds);
  }

  @Override
  public void onAccuracyChanged (Sensor sensor, int accuracy) { }

  /**
   * Writes {@code stabilization * base} to {@code out}, or copies {@code base} if inactive.
   * Signs assume the front camera picture is mirrored, like the preview.
   */
  public void apply (float[] out, float[] base, boolean isFrontFacing, long frameTimestampNs) {
    apply(out, base, isFrontFacing, frameTimestampNs, null);
  }

  /** @param debugOut if not null, receives {shiftX, shiftY, roll, lagMs} actually applied */
  public void apply (float[] out, float[] base, boolean isFrontFacing, long frameTimestampNs, @androidx.annotation.Nullable float[] debugOut) {
    if (!isActive()) {
      System.arraycopy(base, 0, out, 0, 16);
      return;
    }
    float[] angles = new float[3];
    long lagNs = anglesAt(toSensorClock(frameTimestampNs), angles);
    float ax = angles[0], ay = angles[1], az = angles[2];
    // Tilting the top towards the user makes the rear camera look up (scene moves down)
    // and the front camera look down (scene moves up).
    float shiftY = soft((isFrontFacing ? -ax : ax) * SHIFT_PER_RADIAN, MAX_SHIFT);
    // Turning left/right: the rear camera and the mirrored front picture both move the same way.
    float shiftX = soft(-ay * SHIFT_PER_RADIAN, MAX_SHIFT);
    float roll = soft(az, MAX_ROLL_RADIANS);

    // Called from both the preview and the encoder threads, so no shared scratch matrix.
    float[] stabilization = new float[16];
    Matrix.setIdentityM(stabilization, 0);
    Matrix.translateM(stabilization, 0, shiftX, shiftY, 0);
    Matrix.rotateM(stabilization, 0, (float) Math.toDegrees(roll), 0, 0, 1);
    Matrix.scaleM(stabilization, 0, ZOOM, ZOOM, 1);
    Matrix.multiplyMM(out, 0, stabilization, 0, base, 0);
    if (debugOut != null) {
      debugOut[0] = shiftX;
      debugOut[1] = shiftY;
      debugOut[2] = roll;
      debugOut[3] = lagNs / 1_000_000f;
    }
  }

  // Camera frame timestamps are either CLOCK_BOOTTIME (same as sensor events) or
  // CLOCK_MONOTONIC depending on the device; bring monotonic ones to boot time.
  private static long toSensorClock (long frameTimestampNs) {
    long boot = android.os.Build.VERSION.SDK_INT >= 17 ? android.os.SystemClock.elapsedRealtimeNanos() : android.os.SystemClock.elapsedRealtime() * 1_000_000L;
    long mono = System.nanoTime();
    if (frameTimestampNs <= 0) {
      return boot;
    }
    if (Math.abs(boot - frameTimestampNs) <= Math.abs(mono - frameTimestampNs)) {
      return frameTimestampNs;
    }
    return frameTimestampNs + (boot - mono);
  }

  /** Fills angles at the given sensor time (between the two samples around it); returns how far back it looked. */
  private long anglesAt (long timeNs, float[] outAngles) {
    synchronized (historyLock) {
      if (historyCount == 0) {
        return 0;
      }
      int newest = (historyHead - 1 + HISTORY_SIZE) % HISTORY_SIZE;
      int index = newest, later = -1;
      for (int i = 0; i < historyCount; i++) {
        int candidate = (newest - i + HISTORY_SIZE) % HISTORY_SIZE;
        if (historyTime[candidate] <= timeNs) {
          index = candidate;
          break;
        }
        later = candidate;
        index = candidate;
      }
      float t = 0f;
      if (later != -1 && later != index && historyTime[index] <= timeNs) {
        // the frame was captured between two gyroscope samples: no steps of a whole sample
        t = (float) (timeNs - historyTime[index]) / (float) (historyTime[later] - historyTime[index]);
      }
      for (int k = 0; k < 3; k++) {
        float a = historyAngles[index * 3 + k];
        outAngles[k] = later != -1 && t > 0f ? a + (historyAngles[later * 3 + k] - a) * t : a;
      }
      return historyTime[newest] - historyTime[index];
    }
  }

  /** A soft limit instead of a wall: almost linear in the middle, easing into the margin — no stop at the edge */
  private static float soft (float value, float max) {
    return max * (float) Math.tanh(value / max);
  }
}
