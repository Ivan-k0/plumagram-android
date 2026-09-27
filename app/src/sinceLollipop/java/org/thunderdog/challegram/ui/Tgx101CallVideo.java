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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.telegram.messenger.voip.VideoCameraCapturer;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.voip.Tgx101Video;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;

/**
 * TGx101: call screen controls and video. One panel at the bottom (speaker, camera, microphone,
 * chat, end call) replaces Telegram X's controls while the call is outgoing or active; incoming
 * calls keep the original accept/decline swipe. The other person's video fills the screen, your
 * own is a rounded window above the panel with a switch-camera button in its corner.
 */
final class Tgx101CallVideo implements Tgx101Video.Listener {
  /** Actions of the call screen itself (CallController). */
  interface Host {
    boolean isMicMuted ();
    void toggleMicMuted ();
    boolean isSpeakerOn ();
    void toggleSpeaker ();
    void openChat ();
    void hangUp ();
    void onVideoStarted ();
  }

  private static final int COLOR_ACCENT = 0xff3f8ae0, COLOR_END = 0xffe5484d, COLOR_BUTTON = 0x29ffffff;

  private final ViewController<?> controller;
  private final Host host;
  private final View[] hideWhenRemoteVideo;
  private final SurfaceViewRenderer remoteView;
  private final Tgx101TextureVideoView localView;
  private final FrameLayout localWrap;
  private final LinearLayout panel;
  private final ImageView speakerButton, cameraButton, micButton;
  private final TextView qualityButton;
  private boolean videoWasOn, panelVisible;
  private View[] originalControls = new View[0];

  Tgx101CallVideo (ViewController<?> controller, FrameLayout contentView, int index, Host host, View... hideWhenRemoteVideo) {
    this.controller = controller;
    this.host = host;
    this.hideWhenRemoteVideo = hideWhenRemoteVideo;

    remoteView = new SurfaceViewRenderer(controller.context());
    remoteView.init(Tgx101Video.eglContext(), null);
    remoteView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
    remoteView.setEnableHardwareScaler(true);
    remoteView.setVisibility(View.GONE);
    contentView.addView(remoteView, index, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // Bottom panel
    panel = new LinearLayout(controller.context());
    panel.setOrientation(LinearLayout.HORIZONTAL);
    panel.setGravity(Gravity.CENTER_VERTICAL);
    int padding = Screen.dp(10f);
    panel.setPadding(padding, padding, padding, padding);
    GradientDrawable panelBackground = new GradientDrawable();
    panelBackground.setCornerRadius(Screen.dp(40f));
    panelBackground.setColor(0x8c0a0f15);
    panel.setBackground(panelBackground);
    panel.setVisibility(View.GONE);
    FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    panelParams.leftMargin = panelParams.rightMargin = Screen.dp(16f);
    panelParams.bottomMargin = Screen.dp(28f);
    contentView.addView(panel, panelParams);
    speakerButton = addPanelButton(R.drawable.baseline_volume_up_24, v -> {
      host.toggleSpeaker();
      update();
    });
    cameraButton = addPanelButton(R.drawable.baseline_videocam_24, v -> toggleCamera());
    micButton = addPanelButton(R.drawable.baseline_mic_24, v -> {
      host.toggleMicMuted();
      update();
    });
    addPanelButton(R.drawable.baseline_chat_bubble_24, v -> host.openChat());
    ImageView endButton = addPanelButton(R.drawable.baseline_call_end_24, v -> host.hangUp());
    ((GradientDrawable) endButton.getBackground()).setColor(COLOR_END);

    // Own camera: rounded window above the panel, switch camera in its corner
    localWrap = new FrameLayout(controller.context());
    localView = new Tgx101TextureVideoView(controller.context(), Tgx101Video.eglContext(), Screen.dp(14f));
    localView.setMirror(Tgx101Video.isFrontCamera());
    localWrap.addView(localView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    View border = new View(controller.context());
    GradientDrawable borderDrawable = new GradientDrawable();
    borderDrawable.setCornerRadius(Screen.dp(14f));
    borderDrawable.setStroke(Screen.dp(1.5f), 0x33ffffff);
    border.setBackground(borderDrawable);
    localWrap.addView(border, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    ImageView flip = new ImageView(controller.context());
    flip.setImageResource(R.drawable.baseline_camera_front_24);
    flip.setColorFilter(0xffffffff);
    flip.setScaleType(ImageView.ScaleType.CENTER);
    flip.setBackground(circle(0x73000000));
    flip.setOnClickListener(v -> {
      Tgx101Video.switchCamera();
      localView.setMirror(Tgx101Video.isFrontCamera());
    });
    FrameLayout.LayoutParams flipParams = new FrameLayout.LayoutParams(Screen.dp(32f), Screen.dp(32f), Gravity.LEFT | Gravity.TOP);
    flipParams.leftMargin = flipParams.topMargin = Screen.dp(6f);
    localWrap.addView(flip, flipParams);
    localWrap.setVisibility(View.GONE);
    FrameLayout.LayoutParams localParams = new FrameLayout.LayoutParams(Screen.dp(104f), Screen.dp(140f), Gravity.RIGHT | Gravity.BOTTOM);
    localParams.rightMargin = Screen.dp(20f);
    localParams.bottomMargin = Screen.dp(28f + 68f + 14f); // above the panel
    contentView.addView(localWrap, localParams);

    // SD / HD, top right
    qualityButton = new TextView(controller.context());
    qualityButton.setTextColor(0xffffffff);
    qualityButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
    qualityButton.setTypeface(Typeface.DEFAULT_BOLD);
    qualityButton.setGravity(Gravity.CENTER);
    qualityButton.setPadding(Screen.dp(10f), Screen.dp(5f), Screen.dp(10f), Screen.dp(5f));
    GradientDrawable chip = new GradientDrawable();
    chip.setCornerRadius(Screen.dp(20f));
    chip.setColor(0x59000000);
    chip.setStroke(Screen.dp(1f), 0x59ffffff);
    qualityButton.setBackground(chip);
    qualityButton.setOnClickListener(v -> {
      Settings settings = Settings.instance();
      settings.setCallVideoQuality(settings.getCallVideoQuality() == Settings.CALL_VIDEO_HD ? Settings.CALL_VIDEO_SD : Settings.CALL_VIDEO_HD);
      VideoCameraCapturer.applyQuality(); // takes effect during the call
      update();
    });
    FrameLayout.LayoutParams qualityParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP);
    qualityParams.topMargin = Screen.getStatusBarHeight() + Screen.dp(52f);
    qualityParams.rightMargin = Screen.dp(16f);
    contentView.addView(qualityButton, qualityParams);

    // Above the call controls layer, which covers the whole screen
    contentView.post(() -> {
      contentView.bringChildToFront(localWrap);
      contentView.bringChildToFront(qualityButton);
      contentView.bringChildToFront(panel);
    });

    Tgx101Video.remoteSink.setTarget(remoteView);
    Tgx101Video.localSink.setTarget(localView);
    Tgx101Video.addListener(this);
    update();
  }

  /** The original controls (bottom buttons, call controls layer) hidden while the panel is shown. */
  void setOriginalControls (View... views) {
    this.originalControls = views;
  }

  /** Panel for outgoing and active calls; incoming calls keep the accept/decline swipe. */
  void onCallStateChanged (TdApi.Call call) {
    int state = call.state.getConstructor();
    panelVisible = (state == TdApi.CallStatePending.CONSTRUCTOR && call.isOutgoing) ||
      state == TdApi.CallStateExchangingKeys.CONSTRUCTOR || state == TdApi.CallStateReady.CONSTRUCTOR;
    panel.setVisibility(panelVisible ? View.VISIBLE : View.GONE);
    for (View view : originalControls) {
      if (view != null) view.setVisibility(panelVisible ? View.GONE : View.VISIBLE);
    }
    update();
  }

  void updateControls () {
    update();
  }

  private ImageView addPanelButton (int icon, View.OnClickListener onClick) {
    ImageView button = new ImageView(controller.context());
    button.setImageResource(icon);
    button.setColorFilter(0xffffffff);
    button.setScaleType(ImageView.ScaleType.CENTER);
    button.setBackground(circle(COLOR_BUTTON));
    button.setOnClickListener(onClick);
    FrameLayout cell = new FrameLayout(controller.context());
    cell.addView(button, new FrameLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f), Gravity.CENTER));
    panel.addView(cell, new LinearLayout.LayoutParams(0, Screen.dp(48f), 1f));
    return button;
  }

  private static GradientDrawable circle (int color) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setShape(GradientDrawable.OVAL);
    drawable.setColor(color);
    return drawable;
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
      host.onVideoStarted(); // loudspeaker, as in the official app
    }
    videoWasOn = camera || remote;
    localWrap.setVisibility(camera ? View.VISIBLE : View.GONE);
    remoteView.setVisibility(remote ? View.VISIBLE : View.GONE);
    for (View view : hideWhenRemoteVideo) {
      if (view != null) view.setAlpha(remote ? 0f : 1f);
    }
    qualityButton.setVisibility(camera ? View.VISIBLE : View.GONE);
    qualityButton.setText(Settings.instance().getCallVideoQuality() == Settings.CALL_VIDEO_HD ? "HD" : "SD");
    ((GradientDrawable) cameraButton.getBackground()).setColor(camera ? COLOR_ACCENT : COLOR_BUTTON);
    ((GradientDrawable) speakerButton.getBackground()).setColor(host.isSpeakerOn() ? COLOR_ACCENT : COLOR_BUTTON);
    boolean muted = host.isMicMuted();
    ((GradientDrawable) micButton.getBackground()).setColor(muted ? 0xffffffff : COLOR_BUTTON);
    micButton.setColorFilter(muted ? 0xff0a0f15 : 0xffffffff);
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
    Window window = controller.context().getWindow();
    if (keepOn) {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    } else {
      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
