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
 *
 * File created on 21/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.view.Gravity;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class MediaGalleryCameraView extends FrameLayoutFix implements Destroyable, TextureView.SurfaceTextureListener {
  private TextureView textureView;

  public MediaGalleryCameraView (Context context) {
    super(context);
    setId(R.id.btn_camera);
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    setBackgroundColor(0xff1c2733); // TGx101: a placeholder tile, no live preview (light on battery)

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(36f), Screen.dp(36f), Gravity.CENTER);

    ImageView imageView = new ImageView(context);
    imageView.setScaleType(ImageView.ScaleType.CENTER);
    imageView.setImageResource(R.drawable.baseline_camera_alt_24);
    imageView.setColorFilter(0xffffffff);
    imageView.setLayoutParams(params);
    addView(imageView);
  }

  // Texture stuff

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  private void openCamera () {

  }

  @Override
  public void onSurfaceTextureAvailable (SurfaceTexture surface, int width, int height) {
    openCamera();
  }

  @Override
  public void onSurfaceTextureSizeChanged (SurfaceTexture surface, int width, int height) {

  }

  @Override
  public boolean onSurfaceTextureDestroyed (SurfaceTexture surface) {
    return false;
  }

  @Override
  public void onSurfaceTextureUpdated (SurfaceTexture surface) {

  }


  // Etc

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, widthMeasureSpec);
  }

  @Override
  public void performDestroy () {
    // TODO
  }

  public void attach () {

  }

  public void detach () {

  }
}
