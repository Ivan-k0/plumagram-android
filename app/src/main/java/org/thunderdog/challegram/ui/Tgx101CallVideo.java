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
package org.thunderdog.challegram.ui;

import android.Manifest;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.voip.Tgx101Video;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;

/**
 * TGx101: video on the call screen. The other person's video fills the screen when they turn the
 * camera on, your own camera is a small window in the corner. Renderers exist only on this screen.
 */
final class Tgx101CallVideo implements Tgx101Video.Listener {
  private final ViewController<?> controller;
  private final SurfaceViewRenderer remoteView, localView;
  private final ImageView cameraButton, switchButton;
  private final android.widget.TextView qualityButton;
  private final View[] hideWhenRemoteVideo;
  private final Runnable onVideoStarted;
  private boolean videoWasOn;

  /** Adds the video views to the call screen at the given index (above the background and photo). */
  Tgx101CallVideo (ViewController<?> controller, FrameLayout contentView, int index, Runnable onVideoStarted, View... hideWhenRemoteVideo) {
    this.controller = controller;
    this.onVideoStarted = onVideoStarted;
    this.hideWhenRemoteVideo = hideWhenRemoteVideo;

    remoteView = new SurfaceViewRenderer(controller.context());
    remoteView.init(Tgx101Video.eglContext(), null);
    remoteView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
    remoteView.setEnableHardwareScaler(true);
    remoteView.setVisibility(View.GONE);
    contentView.addView(remoteView, index, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    localView = new SurfaceViewRenderer(controller.context());
    localView.init(Tgx101Video.eglContext(), null);
    localView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
    localView.setZOrderMediaOverlay(true);
    localView.setMirror(Tgx101Video.isFrontCamera());
    localView.setVisibility(View.GONE);
    FrameLayout.LayoutParams localParams = new FrameLayout.LayoutParams(Screen.dp(108f), Screen.dp(160f), Gravity.RIGHT | Gravity.TOP);
    localParams.topMargin = Screen.getStatusBarHeight() + Screen.dp(56f);
    localParams.rightMargin = Screen.dp(16f);
    contentView.addView(localView, localParams);

    LinearLayout buttons = new LinearLayout(controller.context());
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    buttons.setGravity(Gravity.CENTER);
    FrameLayout.LayoutParams buttonsParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    buttonsParams.bottomMargin = Screen.dp(196f); // above the end call button
    contentView.addView(buttons, buttonsParams);
    contentView.post(() -> contentView.bringChildToFront(buttons)); // above the call controls layer, which covers the screen
    cameraButton = addButton(buttons, R.drawable.baseline_videocam_24, v -> toggleCamera());
    switchButton = addButton(buttons, R.drawable.baseline_camera_front_24, v -> {
      Tgx101Video.switchCamera();
      localView.setMirror(Tgx101Video.isFrontCamera());
    });
    qualityButton = new android.widget.TextView(controller.context());
    qualityButton.setTextColor(0xffffffff);
    qualityButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
    qualityButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    qualityButton.setGravity(Gravity.CENTER);
    GradientDrawable qualityBackground = new GradientDrawable();
    qualityBackground.setShape(GradientDrawable.OVAL);
    qualityBackground.setColor(0x33ffffff);
    qualityButton.setBackground(qualityBackground);
    qualityButton.setOnClickListener(v -> {
      org.thunderdog.challegram.unsorted.Settings settings = org.thunderdog.challegram.unsorted.Settings.instance();
      boolean hd = settings.getCallVideoQuality() == org.thunderdog.challegram.unsorted.Settings.CALL_VIDEO_HD;
      settings.setCallVideoQuality(hd ? org.thunderdog.challegram.unsorted.Settings.CALL_VIDEO_SD : org.thunderdog.challegram.unsorted.Settings.CALL_VIDEO_HD);
      org.telegram.messenger.voip.VideoCameraCapturer.applyQuality(); // takes effect during the call
      update();
    });
    LinearLayout.LayoutParams qualityParams = new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f));
    qualityParams.leftMargin = qualityParams.rightMargin = Screen.dp(12f);
    buttons.addView(qualityButton, qualityParams);

    Tgx101Video.remoteSink.setTarget(remoteView);
    Tgx101Video.localSink.setTarget(localView);
    Tgx101Video.addListener(this);
    update();
  }

  private ImageView addButton (LinearLayout row, int icon, View.OnClickListener onClick) {
    ImageView button = new ImageView(controller.context());
    button.setImageResource(icon);
    button.setColorFilter(0xffffffff);
    button.setScaleType(ImageView.ScaleType.CENTER);
    GradientDrawable background = new GradientDrawable();
    background.setShape(GradientDrawable.OVAL);
    background.setColor(0x33ffffff);
    button.setBackground(background);
    button.setOnClickListener(onClick);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f));
    params.leftMargin = params.rightMargin = Screen.dp(12f);
    row.addView(button, params);
    return button;
  }

  private void toggleCamera () {
    if (!Tgx101Video.isCallActive()) {
      UI.showToast(R.string.Tgx101VideoWaitCall, Toast.LENGTH_SHORT);
      return;
    }
    if (Tgx101Video.isCameraEnabled()) {
      Tgx101Video.setCameraEnabled(false);
      return;
    }
    if (Tgx101Video.hasCameraPermission()) {
      Tgx101Video.setCameraEnabled(true);
    } else {
      controller.context().requestCustomPermissions(new String[] {Manifest.permission.CAMERA}, (code, permissions, grantResults, grantCount) -> {
        if (grantCount == permissions.length) {
          Tgx101Video.setCameraEnabled(true);
        } else {
          UI.showToast(R.string.Tgx101VideoNoCamera, Toast.LENGTH_LONG);
        }
      });
    }
  }

  private void update () {
    boolean camera = Tgx101Video.isCameraEnabled();
    boolean remote = Tgx101Video.isRemoteVideoActive();
    setKeepScreenOn(camera || remote);
    if ((camera || remote) && !videoWasOn) {
      onVideoStarted.run(); // loudspeaker, as in the official app
    }
    videoWasOn = camera || remote;
    localView.setVisibility(camera ? View.VISIBLE : View.GONE);
    remoteView.setVisibility(remote ? View.VISIBLE : View.GONE);
    for (View view : hideWhenRemoteVideo) {
      if (view != null) view.setAlpha(remote ? 0f : 1f);
    }
    cameraButton.setAlpha(camera ? 1f : .6f);
    ((GradientDrawable) cameraButton.getBackground()).setColor(camera ? 0xff3f8ae0 : 0x33ffffff);
    switchButton.setVisibility(camera ? View.VISIBLE : View.GONE);
    qualityButton.setVisibility(camera ? View.VISIBLE : View.GONE);
    qualityButton.setText(org.thunderdog.challegram.unsorted.Settings.instance().getCallVideoQuality() == org.thunderdog.challegram.unsorted.Settings.CALL_VIDEO_HD ? "HD" : "SD");
  }

  @Override
  public void onLocalVideoChanged (boolean enabled) {
    update();
  }

  @Override
  public void onRemoteVideoChanged (boolean active) {
    update();
  }

  private void setKeepScreenOn (boolean keepOn) {
    android.view.Window window = controller.context().getWindow();
    if (keepOn) {
      window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    } else {
      window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
  }

  void destroy () {
    setKeepScreenOn(false);
    Tgx101Video.removeListener(this);
    Tgx101Video.remoteSink.setTarget(null);
    Tgx101Video.localSink.setTarget(null);
    remoteView.release();
    localView.release();
  }
}
