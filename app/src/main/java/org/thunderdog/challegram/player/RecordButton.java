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
 * File created on 20/08/2015 at 02:59
 */
package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class RecordButton extends View implements FactorAnimator.Target, ClickHelper.Delegate {

  private static final float MAX_FACTOR = 3f;

  private int radiusAdd;
  private int radius;
  private int center;

  private final ClickHelper helper = new ClickHelper(this);

  public RecordButton (Context context) {
    super(context);

    radiusAdd = Screen.dp(20f);
    radius = Screen.dp(41f);

    center = (int) (radius + radiusAdd * MAX_FACTOR);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(center * 2, center * 2, Gravity.TOP | Gravity.LEFT);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          if (expand <= 0f) {
            outline.setEmpty();
          } else {
            int radius = (int) (RecordButton.this.radius * expand);
            outline.setRoundRect(center - radius, center - radius, center + radius, center + radius, radius);
          }
        }
      });
      setElevation(Screen.dp(1f));
      setTranslationZ(Screen.dp(2f));
    }

    setLayoutParams(params);
  }

  private View.OnClickListener onClickListener;

  @Override
  public void setOnClickListener (@Nullable OnClickListener l) {
    super.setOnClickListener(l);
    this.onClickListener = l;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return onClickListener != null && (e.getAction() != MotionEvent.ACTION_DOWN || (expand == 1f && U.isInside(e.getX(), e.getY(), getMeasuredWidth() / 2, getMeasuredHeight() / 2, radius * expand))) && helper.onTouchEvent(this, e);
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return U.isInside(x, y, getMeasuredWidth() / 2, getMeasuredHeight() / 2, radius * expand);
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (U.isInside(x, y, getMeasuredWidth() / 2, getMeasuredHeight() / 2, radius * expand)) {
      onClickListener.onClick(this);
    }
  }

  public float getCenter () {
    return center;
  }

  public int getSize () {
    return center * 2;
  }

  // Animators

  private float expand;

  public void setExpand (float expand) {
    if (this.expand != expand) {
      this.expand = expand;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        invalidateOutline();
      }
      invalidate();
    }
  }

  public float getExpand () {
    return expand;
  }

  private float volume;
  private FactorAnimator animator;

  private boolean scheduledToVolume;
  private float currentToVolume;

  public void setVolume (float toVolume, boolean animated) {
    toVolume = Math.min(MAX_FACTOR, toVolume / 150f);
    if (animated) {
      if (Math.round((float) radiusAdd * currentToVolume) != Math.round((float) radiusAdd * toVolume)) {
        if (animator == null) {
          if (volume == toVolume) {
            return;
          }
          this.animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 190l, volume);
        }
        currentToVolume = toVolume;
        if (toVolume >= currentToVolume || toVolume > 0 || !animator.isAnimating()) {
          scheduledToVolume = false;
          animator.animateTo(toVolume);
        } else {
          scheduledToVolume = true;
        }
      }
    } else {
      scheduledToVolume = false;
      if (animator != null) {
        animator.forceFactor(toVolume);
      }
      setVolume(toVolume);
    }
  }

  private void setVolume (float volume) {
    if (this.volume != volume) {
      this.volume = volume;
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setVolume(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (scheduledToVolume) {
      animator.animateTo(currentToVolume);
    }
  }

  // TGx101 (user 2026-11-16, mockups «вокруг кнопки записи», after Telegram for Mac): «если линия активна — просто кнопка
  // и линия; если не активна — капля в два слоя; для всех полей ввода». With the field's live line on, the button only
  // breathes a little with the voice and the line carries the wave; otherwise a two-layer drop round the button — the
  // near layer quick, the far one slow and paler — its edge swaying with the voice. Since 2026-11-20 a soft glow instead
  public static final int TGX101_HALO_DROP = 0, TGX101_HALO_NONE = 1;
  private int tgx101Halo = TGX101_HALO_DROP;
  private float tgx101Slow;
  private long tgx101LastFrame;
  private android.graphics.Paint tgx101Glow;
  private int tgx101GlowColor, tgx101GlowBase;

  public void tgx101SetHalo (int halo) {
    if (tgx101Halo != halo) {
      tgx101Halo = halo;
      invalidate();
    }
  }

  @Override
  public void onDraw (Canvas c) {
    int color = Theme.getColor(ColorId.circleButtonRegular);
    float loud = Math.min(1f, volume);
    if (tgx101Halo == TGX101_HALO_NONE) {
      c.drawCircle(center, center, radius * expand * (1f + .06f * loud), Paints.fillingPaint(color));
      return;
    }
    long now = android.os.SystemClock.uptimeMillis();
    float dt = tgx101LastFrame == 0 ? 16f : Math.min(64f, now - tgx101LastFrame);
    tgx101LastFrame = now;
    tgx101Slow += (loud - tgx101Slow) * (1f - (float) Math.exp(-dt / 260f));
    if (expand > 0f) {
      float r = radius * expand;
      // (user 2026-11-20, mockups «Анимация записи голосового» → «Частицы»): the two-layer drop gave way to a soft glow
      // that breathes with the voice; the dots flying out of the button are Tgx101RecordParticles. The glow is one cached
      // gradient scaled to size — nothing is made while drawing
      int glowBase = Screen.dp(100f);
      if (tgx101Glow == null || tgx101GlowColor != color || tgx101GlowBase != glowBase) {
        tgx101GlowColor = color;
        tgx101GlowBase = glowBase;
        tgx101Glow = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        tgx101Glow.setShader(new android.graphics.RadialGradient(0, 0, glowBase,
          new int[] {ColorUtils.alphaColor(.25f, color), ColorUtils.alphaColor(.25f, color), ColorUtils.alphaColor(0f, color)},
          new float[] {0f, .6f, 1f}, android.graphics.Shader.TileMode.CLAMP));
      }
      float glow = r + (Screen.dp(18f) + Screen.dp(22f) * tgx101Slow) * expand; // solid to about the edge, then fading out
      float k = glow / glowBase;
      c.save();
      c.translate(center, center);
      c.scale(k, k);
      c.drawCircle(0, 0, glowBase, tgx101Glow);
      c.restore();
      // the glow follows the voice 30 times a second, as the line's wave (user 2026-11-16
      // «не будет грузить процессор больше, чем было?»): every frame of a 120 Hz screen for the whole recording was too much
      postInvalidateDelayed(33);
    } else {
      tgx101LastFrame = 0;
      tgx101Slow = 0f;
    }
    c.drawCircle(center, center, radius * expand, Paints.fillingPaint(color));
  }
}
