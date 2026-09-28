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
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;

import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.GlRectDrawer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

/**
 * TGx101: video in a TextureView, so the small self view can have real rounded corners
 * (a SurfaceView can't be clipped). Frames are cropped to fill the view.
 */
final class Tgx101TextureVideoView extends TextureView implements TextureView.SurfaceTextureListener, VideoSink {
  private final EglRenderer renderer = new EglRenderer("TGx101SelfView");

  Tgx101TextureVideoView (Context context, EglBase.Context eglContext, float cornerRadius) {
    super(context);
    renderer.init(eglContext, EglBase.CONFIG_PLAIN, new GlRectDrawer());
    setSurfaceTextureListener(this);
    setOutlineProvider(new ViewOutlineProvider() {
      @Override
      public void getOutline (View view, Outline outline) {
        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
      }
    });
    setClipToOutline(true);
  }

  void setMirror (boolean mirror) {
    renderer.setMirror(mirror);
  }

  void release () {
    renderer.release();
  }

  @Override
  public void onFrame (VideoFrame frame) {
    renderer.onFrame(frame);
  }

  @Override
  public void onSurfaceTextureAvailable (@NonNull SurfaceTexture surface, int width, int height) {
    renderer.createEglSurface(surface);
    if (height > 0) {
      renderer.setLayoutAspectRatio(width / (float) height);
    }
  }

  @Override
  public void onSurfaceTextureSizeChanged (@NonNull SurfaceTexture surface, int width, int height) {
    if (height > 0) {
      renderer.setLayoutAspectRatio(width / (float) height);
    }
  }

  @Override
  public boolean onSurfaceTextureDestroyed (@NonNull SurfaceTexture surface) {
    renderer.releaseEglSurface(() -> { });
    return true;
  }

  @Override
  public void onSurfaceTextureUpdated (@NonNull SurfaceTexture surface) { }
}
