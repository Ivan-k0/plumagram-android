/*
 * This file is a part of PlumaGram
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
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 (approved mockup «Плашка перевода», 3; user 2026-10-18): the chat translation offer as a small round button at
 * the top right, under the header, like the chat's «down» arrow — a see-through circle with a hairline and the translate
 * sign; blue when the chat is translated, a running ring while it is being translated
 */
public final class Tgx101TranslateButton extends View {
  public static final int STATE_OFFER = 0, STATE_LOADING = 1, STATE_ON = 2;
  public static final float SIZE_DP = 42f;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final @Nullable Drawable icon;
  private int state = STATE_OFFER;
  private boolean shown;
  private float spin;
  private final Runnable spinner = new Runnable() {
    @Override
    public void run () {
      if (state != STATE_LOADING) return;
      spin = (spin + 8f) % 360f;
      invalidate();
      postOnAnimation(this);
    }
  };

  public Tgx101TranslateButton (Context context) {
    super(context);
    Drawable d = ContextCompat.getDrawable(context, R.drawable.baseline_translate_24);
    icon = d != null ? d.mutate() : null;
    setVisibility(GONE);
    if (android.os.Build.VERSION.SDK_INT >= 21) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setOval(0, 0, view.getWidth(), view.getHeight());
        }
      });
    }
  }

  public void setState (int state) {
    if (this.state == state) return;
    this.state = state;
    if (state == STATE_LOADING) postOnAnimation(spinner); else removeCallbacks(spinner);
    invalidate();
  }

  public int getState () {
    return state;
  }

  public boolean isShown2 () {
    return shown;
  }

  /** comes with a little growth (~0.25 s), goes the same way */
  public void setShown (boolean show, boolean animated) {
    if (shown == show) return;
    shown = show;
    animate().cancel();
    if (!animated) {
      setVisibility(show ? VISIBLE : GONE);
      setAlpha(1f);
      setScaleX(1f);
      setScaleY(1f);
      return;
    }
    if (show) {
      setVisibility(VISIBLE);
      setAlpha(0f);
      setScaleX(.7f);
      setScaleY(.7f);
      animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).withEndAction(null).start();
    } else {
      animate().alpha(0f).scaleX(.7f).scaleY(.7f).setDuration(180).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(() -> {
        if (!shown) setVisibility(GONE);
      }).start();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    float r = Math.min(getWidth(), getHeight()) / 2f;
    float cx = getWidth() / 2f, cy = getHeight() / 2f;
    int accent = Theme.getColor(ColorId.iconActive);
    boolean on = state == STATE_ON;
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(on ? accent : ColorUtils.alphaColor(.9f, Theme.fillingColor()));
    c.drawCircle(cx, cy, r, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(Math.max(1, Screen.dp(.5f)));
    paint.setColor(Theme.separatorColor());
    c.drawCircle(cx, cy, r - paint.getStrokeWidth() / 2f, paint);
    if (state == STATE_LOADING) {
      paint.setStrokeWidth(Screen.dp(2f));
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setColor(accent);
      float inset = Screen.dp(3f);
      rect.set(cx - r + inset, cy - r + inset, cx + r - inset, cy + r - inset);
      c.drawArc(rect, spin, 100f, false, paint);
    }
    paint.setStyle(Paint.Style.FILL);
    if (icon != null) {
      int s = Screen.dp(22f);
      int left = Math.round(cx - s / 2f), top = Math.round(cy - s / 2f);
      icon.setColorFilter(new PorterDuffColorFilter(on ? 0xffffffff : accent, PorterDuff.Mode.SRC_IN));
      icon.setBounds(left, top, left + s, top + s);
      icon.draw(c);
    }
  }
}
