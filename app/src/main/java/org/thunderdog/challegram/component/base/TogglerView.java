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
 * File created on 07/08/2015 at 17:08
 */
package org.thunderdog.challegram.component.base;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

public class TogglerView extends View implements FactorAnimator.Target, TooltipOverlayView.LocationProvider {
  private Paint circlePaint;

  private final BoolAnimator isEnabled = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    updateOrigX();
    invalidate();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      invalidateOutline();
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private BoolAnimator isDisabled;

  private boolean useNegativeState;

  public TogglerView (Context context) {
    super(context);
  }

  /** TGx101 (approved mockup «Окна Material 3» В, user 2026-11-01): the Material 3 switch — a 52×32 dp track, outlined
   *  when off with a small thumb, filled with the accent when on with a bigger thumb holding a ✓. The red / green and
   *  the locked togglers keep their old look */
  public static final boolean TGX101_M3 = true;
  /** How much wider than the old one the Material 3 switch is: rows leave this much more room for it */
  public static final float TGX101_M3_EXTRA_WIDTH = 18f;

  private boolean tgx101M3 () {
    return TGX101_M3 && !useNegativeState && !showLock;
  }

  public void init (boolean isEnabled) {
    setRadioEnabled(isEnabled, false);
    initDrawingItems();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !TGX101_M3) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setRoundRect((int) (origX - origRadius), (int) (origY - origRadius), (int) (origX + origRadius), (int) (origY + origRadius), origRadius);
        }
      });
      setElevation(Math.max(1, Screen.dp(.5f)));
      setTranslationZ(Math.max(1, Screen.dp(.5f)));
      ViewUtils.setBackground(this, new Drawable() {
        @Override
        public void draw (@NonNull Canvas c) {
          final int fromBackgroundColor;
          final int toBackgroundColor;
          if (useNegativeState) {
            fromBackgroundColor = Theme.getColor(ColorId.togglerNegativeBackground);
            toBackgroundColor = Theme.getColor(ColorId.togglerPositiveBackground);
          } else {
            fromBackgroundColor = Theme.togglerInactiveFillingColor();
            toBackgroundColor = Theme.togglerActiveFillingColor();
          }
          final boolean isDisabled = !useNegativeState && TogglerView.this.isDisabled();
          c.drawRoundRect(fillingRect, fillRadius, fillRadius, Paints.fillingPaint(ColorUtils.fromToArgb(fromBackgroundColor, toBackgroundColor, isDisabled ? 0f : TogglerView.this.isEnabled.getFloatValue())));
        }

        @Override
        public void setAlpha (@IntRange(from = 0, to = 255) int i) {

        }

        @Override
        public void setColorFilter (@Nullable ColorFilter colorFilter) {

        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity () {
          return PixelFormat.UNKNOWN;
        }
      });
    } else {
      setLayerType(LAYER_TYPE_SOFTWARE, circlePaint);
    }
    buildLayout();
  }

  public TogglerView setUseNegativeState (boolean useNegativeState) {
    if (this.useNegativeState != useNegativeState) {
      this.useNegativeState = useNegativeState;
      invalidate();
    }
    return this;
  }

  private void initDrawingItems () {
    circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    circlePaint.setStyle(Paint.Style.FILL);
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      float radius = Math.max(1, Screen.dpf(.5f));
      circlePaint.setShadowLayer(radius, 0f, radius, 0x7a000000);
    }
  }

  private boolean isDisabled () {
    return this.isDisabled != null && this.isDisabled.getValue();
  }

  private static final int ANIMATOR_DISABLE = 1;

  public void setDisabled (boolean isDisabled, boolean animated) {
    if (this.isDisabled != null || isDisabled) {
      if (this.isDisabled == null) {
        this.isDisabled = new BoolAnimator(ANIMATOR_DISABLE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 168l);
      }
      this.isDisabled.setValue(isDisabled, animated);
    }
  }

  private boolean showLock;
  private Drawable lockDrawable;

  public void setShowLock (boolean showLock) {
    if (this.showLock != showLock) {
      this.showLock = showLock;
      if (lockDrawable == null) {
        lockDrawable = Drawables.get(getResources(), R.drawable.baseline_lock_14);
      }
      invalidate();
    }
  }

  public void setRadioEnabled (boolean isEnabled, boolean animated) {
    this.isEnabled.setValue(isEnabled, animated);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_DISABLE:
        invalidate();
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  public boolean toggle (boolean animated) {
    return isEnabled.toggleValue(animated);
  }

  /*public boolean isAnimating () {
    return isAnimating;
  }*/

  public boolean isEnabled () {
    return isEnabled.getValue();
  }

  private RectF fillingRect;
  private float fillRadius;
  private float minX, maxX, diffX, origX, origY, origRadius;

  private int currentWidth;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    if (currentWidth != width) {
      currentWidth = width;
      buildLayout();
    }
  }

  public void checkRtl (boolean allowLayout) {
    if (Views.setGravity(this, (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL) && allowLayout) {
      Views.updateLayoutParams(this);
      if (currentWidth > 0) {
        buildLayout();
        invalidate();
      }
    }
  }

  private void buildLayout () {
    if (fillingRect == null) {
      fillingRect = new RectF();
    }

    int start = Screen.dp(58f);
    int left = Screen.dp(4f);
    int right = left + Screen.dp(34f);

    fillingRect.left = currentWidth - start + left;
    fillingRect.right = currentWidth - start + right;

    int top = Screen.dp(20f);
    int bottom = top + Screen.dp(14f);

    fillingRect.top = top;
    fillingRect.bottom = bottom;

    origRadius = Screen.dpf(10f);
    fillRadius = Screen.dp(7f);
    float offset = Screen.dp(3f);

    minX = fillingRect.left - offset + origRadius;
    maxX = fillingRect.right + offset - origRadius;
    diffX = maxX - minX;
    origY = fillingRect.top - offset + origRadius;
    updateOrigX();
  }

  private void updateOrigX () {
    float factor = this.isEnabled.getFloatValue();
    if (Lang.rtl()) {
      origX = factor == 0f ? maxX : factor == 1f ? minX : maxX - factor * diffX;
    } else {
      origX = factor == 0f ? minX : factor == 1f ? maxX : minX + factor * diffX;
    }
  }

  private final RectF tgx101Track = new RectF();
  private Paint tgx101Paint;
  private final android.graphics.Path tgx101Check = new android.graphics.Path();

  private void tgx101DrawM3 (Canvas c, float factor, float disableFactor) {
    if (tgx101Paint == null) {
      tgx101Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      tgx101Paint.setStrokeCap(Paint.Cap.ROUND);
      tgx101Paint.setStrokeJoin(Paint.Join.ROUND);
    }
    float f = factor * (1f - disableFactor);
    int accent = Theme.togglerActiveColor();
    int outline = Theme.togglerInactiveColor();
    int surface = Theme.fillingColor();
    float trackRight = currentWidth - Screen.dp(18f), trackLeft = trackRight - Screen.dp(52f);
    float cy = origY, half = Screen.dp(16f);
    tgx101Track.set(trackLeft, cy - half, trackRight, cy + half);
    float alpha = 1f - .62f * disableFactor; // a disabled switch fades to 38 %, as Material 3 asks
    // track: the surface with an outline when off, the accent when on
    tgx101Paint.setStyle(Paint.Style.FILL);
    tgx101Paint.setColor(ColorUtils.alphaColor(alpha, ColorUtils.fromToArgb(ColorUtils.fromToArgb(surface, outline, .12f), accent, f)));
    c.drawRoundRect(tgx101Track, half, half, tgx101Paint);
    if (f < 1f) {
      float stroke = Screen.dp(2f);
      tgx101Paint.setStyle(Paint.Style.STROKE);
      tgx101Paint.setStrokeWidth(stroke);
      tgx101Paint.setColor(ColorUtils.alphaColor(alpha * (1f - f), outline));
      tgx101Track.inset(stroke / 2f, stroke / 2f);
      c.drawRoundRect(tgx101Track, half - stroke / 2f, half - stroke / 2f, tgx101Paint);
    }
    // thumb: small and grey when off, bigger and in the surface colour when on
    float from = trackLeft + half, to = trackRight - half;
    float x = Lang.rtl() ? to - (to - from) * factor : from + (to - from) * factor;
    float radius = Screen.dp(8f) + Screen.dp(4f) * f;
    tgx101Paint.setStyle(Paint.Style.FILL);
    tgx101Paint.setColor(ColorUtils.alphaColor(alpha, ColorUtils.fromToArgb(outline, surface, f)));
    c.drawCircle(x, cy, radius, tgx101Paint);
    if (f > 0f) {
      float s = Screen.dp(4.5f);
      tgx101Check.reset();
      tgx101Check.moveTo(x - s, cy + s * .05f);
      tgx101Check.lineTo(x - s * .3f, cy + s * .75f);
      tgx101Check.lineTo(x + s, cy - s * .65f);
      tgx101Paint.setStyle(Paint.Style.STROKE);
      tgx101Paint.setStrokeWidth(Screen.dp(2f));
      tgx101Paint.setColor(ColorUtils.alphaColor(alpha * f, accent));
      c.drawPath(tgx101Check, tgx101Paint);
    }
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    final int bound = Screen.dp(2f);
    outRect.set((int) (origX - origRadius - bound), (int) (origY - origRadius - bound), (int) (origX + origRadius + bound), (int) (origY + origRadius + bound));
  }

  @Override
  public void onDraw (Canvas c) {
    final float factor = this.isEnabled.getFloatValue();
    updateOrigX();
    final int fromBackgroundColor, fromColor;
    final int toBackgroundColor, toColor;
    int checkColor;
    if (useNegativeState) {
      fromBackgroundColor = Theme.getColor(ColorId.togglerNegativeBackground); // ThemeRed.getColor(ColorId.togglerFillingActive);
      fromColor = Theme.getColor(ColorId.togglerNegative); // ThemeRed.getColor(ColorId.togglerActive);
      toBackgroundColor = Theme.getColor(ColorId.togglerPositiveBackground); // ThemeBase.getColor(ColorId.togglerFillingActive);
      toColor = Theme.getColor(ColorId.togglerPositive); // ThemeBase.getColor(ColorId.togglerActive);
      checkColor = ColorUtils.fromToArgb(Theme.getColor(ColorId.togglerNegativeContent), Theme.getColor(ColorId.togglerPositiveContent), isEnabled.getFloatValue());
    } else {
      fromBackgroundColor = Theme.togglerInactiveFillingColor();
      fromColor = Theme.togglerInactiveColor();
      toBackgroundColor = Theme.togglerActiveFillingColor();
      toColor = Theme.togglerActiveColor();
      checkColor = 0;
    }

    final float disableFactor = !useNegativeState && this.isDisabled != null ? this.isDisabled.getFloatValue() : 0f;

    if (tgx101M3()) {
      tgx101DrawM3(c, factor, disableFactor);
      return;
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || TGX101_M3) {
      c.drawRoundRect(fillingRect, fillRadius, fillRadius, Paints.fillingPaint(ColorUtils.fromToArgb(fromBackgroundColor, toBackgroundColor, factor * (1f - disableFactor))));
    }
    circlePaint.setColor(ColorUtils.fromToArgb(fromColor, toColor, factor * (1f - disableFactor)));
    c.drawCircle(origX, origY, origRadius, circlePaint);

    if (showLock) {
      Drawables.draw(c, lockDrawable, origX - lockDrawable.getMinimumWidth() / 2f, origY - lockDrawable.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(checkColor));
    } else if (useNegativeState) {
      Paint strokePaint = Paints.getProgressPaint(checkColor, Screen.dp(2f));

      int lineWidth = (int) (origRadius * .75f);
      int linePart = lineWidth / 2;

      /*c.save();
      c.rotate(-90f * (1f - factor), origX, origY);*/

      int offsetGlobal = (int) (Screen.dp(.5f) * factor);
      int offsetGlobalY = (int) (Screen.dp(.5f) * factor);
      int offsetX1 = (int) (Screen.dp(1.5f) * factor);
      c.drawLine(origX - linePart + offsetX1, origY + linePart + offsetGlobalY, origX + linePart + offsetX1, origY - linePart + offsetGlobalY, strokePaint);
      int offsetX = (int) (Screen.dp(-3.5f) * factor);
      int offsetY = (int) (Screen.dp(3f) * factor);
      int removeLeft = (int) (Screen.dp(.5f) * factor);
      c.drawLine(origX - linePart + offsetX + offsetGlobal + removeLeft, origY - linePart + offsetY + offsetGlobalY + removeLeft, origX + linePart * (1f - factor) + offsetX + offsetGlobal, origY + linePart * (1f - factor) + offsetY + offsetGlobalY, strokePaint);

      // c.restore();
    }
  }
}
