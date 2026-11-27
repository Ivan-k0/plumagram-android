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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Tgx101Haptics;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.camera.legacy.CameraManagerLegacy;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

/**
 * TGx101 (user 2026-11-26, mockup «Кружок: зум, фонарик, пауза»): round video controls.
 * — Zoom: «1 · 2 · 5» under the circle; a pinch on the circle (or a drag along the bar) opens a ruler 1…10 with the value
 *   on a badge; let go — the ruler folds back and the value stays in the middle («1 · 3,8× · 5»).
 * — Light: one button in the column; on the rear camera it is the torch, on the front one the screen around the circle
 *   lights warm white at full brightness. Holding it on the front camera opens a drum of the light's strength.
 * Zoom and light last until the round is closed (a pause keeps them).
 */
public class Tgx101RoundControls {
  // (lint for Android 4 before 0.3.330): Canvas#drawRoundRect with four floats is Android 5+; the RectF form works everywhere
  private static final android.graphics.RectF TGX101_TMP_RECT = new android.graphics.RectF();

  static android.graphics.RectF tgx101Rect (float l, float t, float r, float b) {
    TGX101_TMP_RECT.set(l, t, r, b);
    return TGX101_TMP_RECT;
  }

  public interface Host {
    boolean isFrontCamera ();
    @Nullable CameraManagerLegacy camera ();
    void onControlsChanged ();
  }

  private static final String KEY_LIGHT = "tgx101_round_light";
  private static final float[] LEVELS = {.2f, .4f, .6f, .8f, 1f};

  private final Context context;
  private final Host host;

  public final View frontLight;
  public final ZoomBar zoomBar;
  public final RecordControllerButton lightButton;
  public final DrumView drum;
  private final ImageView lightIcon;
  private final ScaleGestureDetector scaleDetector;

  private float zoom = 1f;
  private ValueAnimator zoomAnimator;
  private boolean lightOn;
  private int level; // index in LEVELS
  private float videoRange, recordingRange;
  private boolean brightnessForced;

  public Tgx101RoundControls (Context context, Host host) {
    this.context = context;
    this.host = host;
    // (user 2026-11-26 «по умолчанию яркость фонарика фронта 20%»)
    level = org.thunderdog.challegram.unsorted.Settings.instance().pmc().getInt(KEY_LIGHT, 0);
    if (level < 0 || level >= LEVELS.length) level = 0;

    frontLight = new View(context);
    frontLight.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    frontLight.setBackgroundColor(0xfffff4e2);
    frontLight.setAlpha(0f);

    zoomBar = new ZoomBar(context);
    zoomBar.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ZoomBar.HEIGHT), Gravity.TOP));
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      // (user 2026-11-26 «цифра зума скрыта за кружком»): the circle is lifted by 2.5 dp — the bar and its badge go over it
      zoomBar.setTranslationZ(Screen.dp(4f));
    }

    drum = new DrumView(context);
    drum.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) drum.setTranslationZ(Screen.dp(4f));

    lightButton = new RecordControllerButton(context) {
      @Override
      protected int tgx101ActiveColor () {
        return 0xffffb300;
      }

      @Override
      protected void onActiveFactorChanged (float factor) {
        lightIcon.setColorFilter(ColorUtils.fromToArgb(Theme.iconColor(), 0xffffffff, factor));
      }
    };
    lightButton.init(null);
    lightIcon = new ImageView(context);
    lightIcon.setScaleType(ImageView.ScaleType.CENTER);
    lightIcon.setImageResource(R.drawable.baseline_flash_on_24);
    lightIcon.setColorFilter(Theme.iconColor());
    lightIcon.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    lightButton.addView(lightIcon);
    lightButton.setOnTouchListener(this::onLightTouch);

    scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
      @Override
      public boolean onScaleBegin (ScaleGestureDetector detector) {
        zoomBar.setExpanded(true);
        return true;
      }

      @Override
      public boolean onScale (ScaleGestureDetector detector) {
        setZoom(zoom * detector.getScaleFactor(), false);
        return true;
      }

      @Override
      public void onScaleEnd (ScaleGestureDetector detector) {
        zoomBar.setExpanded(false);
      }
    });
  }

  // ——— shared state ———

  private float maxZoom () {
    CameraManagerLegacy camera = host.camera();
    float max = camera != null ? camera.tgx101MaxZoomRatio() : 1f;
    return Math.max(1f, Math.min(10f, max));
  }

  /** called with the alphas of the round video UI: {@code videoRange} — the whole round UI, {@code recording} — not the preview */
  public void setVisibility (float videoRange, float recording) {
    this.videoRange = videoRange;
    this.recordingRange = recording;
    float zoomAlpha = videoRange * recording * (maxZoom() > 1.01f ? 1f : 0f);
    zoomBar.setAlpha(zoomAlpha);
    lightButton.setAlpha(videoRange);
    updateFrontLight();
  }

  /** the circle's bottom edge in the root's coordinates — the bar goes under it */
  public void layoutUnder (float circleBottom) {
    zoomBar.setTranslationY(circleBottom + Screen.dp(12f) - Screen.dp(ZoomBar.BADGE_AREA));
  }

  /** the camera came (back) — zoom and light as they were */
  public void reapply () {
    CameraManagerLegacy camera = host.camera();
    if (camera == null) return;
    if (zoom > 1.001f) camera.tgx101SetZoomRatio(zoom);
    camera.tgx101SetTorch(lightOn && !host.isFrontCamera());
    setVisibility(videoRange, recordingRange); // the zoom bar shows once the camera told its zoom range
    zoomBar.invalidate();
  }

  /** the round is closed — everything back */
  public void reset () {
    if (zoomAnimator != null) zoomAnimator.cancel();
    zoom = 1f;
    if (lightOn) {
      lightOn = false;
      CameraManagerLegacy camera = host.camera();
      if (camera != null) camera.tgx101SetTorch(false);
    }
    lightButton.setActive(false, false);
    drum.hide();
    zoomBar.setExpanded(false);
    updateFrontLight();
    zoomBar.invalidate();
  }

  // ——— zoom ———

  public boolean onCircleTouch (MotionEvent e) {
    if (recordingRange <= 0f) return false;
    scaleDetector.onTouchEvent(e);
    return scaleDetector.isInProgress() || e.getPointerCount() > 1;
  }

  private void setZoom (float value, boolean animated) {
    float target = Math.max(1f, Math.min(maxZoom(), value));
    if (zoomAnimator != null) zoomAnimator.cancel();
    if (animated && Math.abs(target - zoom) > .01f) {
      zoomAnimator = ValueAnimator.ofFloat(zoom, target);
      zoomAnimator.setDuration((long) (260 / animScale()));
      zoomAnimator.setInterpolator(androidx.core.view.animation.PathInterpolatorCompat.create(.2f, 0f, 0f, 1f));
      zoomAnimator.addUpdateListener(a -> applyZoom((float) a.getAnimatedValue()));
      zoomAnimator.start();
    } else {
      applyZoom(target);
    }
  }

  private int lastZoomTick = -1;

  private void applyZoom (float value) {
    zoom = value;
    CameraManagerLegacy camera = host.camera();
    if (camera != null) camera.tgx101SetZoomRatio(value);
    int tick = Math.round(value * 10f);
    if (zoomBar.expanded && tick != lastZoomTick && (tick == 10 || tick == 20 || tick == 50 || tick == 100)) {
      Tgx101Haptics.play(Tgx101Haptics.TICK);
    }
    lastZoomTick = tick;
    zoomBar.invalidate();
  }

  private float animScale () {
    float s = U.getAnimationScale(context);
    return s > 0f ? s : 1f;
  }

  private static String zoomText (float z, boolean withX) {
    String s;
    if (Math.abs(z - Math.round(z)) < .05f) {
      s = Integer.toString(Math.round(z));
    } else {
      s = String.format(java.util.Locale.US, "%.1f", z).replace('.', ',');
    }
    return withX ? s + "×" : s;
  }

  public class ZoomBar extends View {
    static final int HEIGHT = 96, BADGE_AREA = 40, BAR = 46;

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean expanded;
    private float expandFactor;
    private ValueAnimator expandAnimator;
    private final float[] stops = new float[3];
    private final RectF[] stopRects = {new RectF(), new RectF(), new RectF()};

    ZoomBar (Context context) {
      super(context);
      textPaint.setTypeface(Fonts.getRobotoMedium());
    }

    void setExpanded (boolean expanded) {
      if (this.expanded == expanded) return;
      this.expanded = expanded;
      if (expanded) Tgx101Haptics.play(Tgx101Haptics.TICK);
      if (expandAnimator != null) expandAnimator.cancel();
      expandAnimator = ValueAnimator.ofFloat(expandFactor, expanded ? 1f : 0f);
      expandAnimator.setDuration((long) ((expanded ? 220 : 300) / animScale()));
      expandAnimator.setInterpolator(androidx.core.view.animation.PathInterpolatorCompat.create(.2f, 0f, 0f, 1f));
      expandAnimator.addUpdateListener(a -> {
        expandFactor = (float) a.getAnimatedValue();
        invalidate();
      });
      expandAnimator.start();
    }

    private void computeStops () {
      boolean onStop = Math.abs(zoom - 1f) < .05f || Math.abs(zoom - 2f) < .05f || Math.abs(zoom - 5f) < .05f;
      float max = maxZoom();
      stops[0] = 1f;
      stops[1] = onStop ? Math.min(2f, max) : zoom;
      stops[2] = Math.min(5f, max);
    }

    private float pillWidth () {
      return Screen.dp(4f) * 2 + Screen.dp(44f) * 3 + Screen.dp(4f) * 2;
    }

    private float rulerWidth () {
      return Math.min(getWidth() - Screen.dp(40f), Screen.dp(236f)); // (user 2026-11-26 «сам барабан меньше сделай»): 320 → 236
    }

    private float barTop () {
      return Screen.dp(BADGE_AREA);
    }

    private float xForZoom (float z, float left, float right) {
      float max = maxZoom();
      float f = max > 1f ? (float) (Math.log(z) / Math.log(max)) : 0f;
      return left + (right - left) * f;
    }

    private float zoomForX (float x, float left, float right) {
      float max = maxZoom();
      float f = Math.max(0f, Math.min(1f, (x - left) / (right - left)));
      return (float) Math.exp(f * Math.log(max));
    }

    @Override
    protected void onDraw (Canvas c) {
      if (getAlpha() <= 0f) return;
      computeStops();
      int surface = RecordLockView.tgx101Surface();
      int text = Theme.getColor(ColorId.text);
      int sub = Theme.getColor(ColorId.textLight);
      int accent = Theme.getColor(ColorId.circleButtonRegular);
      int accentText = Theme.getColor(ColorId.circleButtonRegularIcon);

      float cx = getWidth() / 2f;
      float top = barTop(), h = Screen.dp(BAR);
      float w = pillWidth() + (rulerWidth() - pillWidth()) * expandFactor;
      rect.set(cx - w / 2f, top, cx + w / 2f, top + h);

      // the bar
      paint.setColor(0x33000000);
      c.drawRoundRect(tgx101Rect(rect.left, rect.top + Screen.dp(2f), rect.right, rect.bottom + Screen.dp(2f)), h / 2f, h / 2f, paint);
      paint.setColor(surface);
      c.drawRoundRect(rect, h / 2f, h / 2f, paint);

      float pill = 1f - expandFactor;
      if (pill > 0f) {
        // three stops; the one in use is an accent circle with «×»
        float slot = Screen.dp(44f);
        float left = cx - slot * 1.5f;
        float cy = rect.centerY();
        for (int i = 0; i < 3; i++) {
          float scx = left + slot * i + slot / 2f;
          stopRects[i].set(scx - slot / 2f, rect.top, scx + slot / 2f, rect.bottom);
          boolean on = Math.abs(stops[i] - zoom) < .05f;
          if (on) {
            paint.setColor(ColorUtils.alphaColor(pill, accent));
            c.drawCircle(scx, cy, Screen.dp(19f), paint);
          }
          textPaint.setTextSize(Screen.dp(on ? 14f : 15f));
          textPaint.setColor(ColorUtils.alphaColor(pill, on ? accentText : text));
          String s = zoomText(stops[i], on);
          c.drawText(s, scx - textPaint.measureText(s) / 2f, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
        }
      }
      if (expandFactor > 0f) {
        // the ruler: log scale 1…max, big ticks at 1, 2, 5, 10 with numbers
        float left = rect.left + Screen.dp(20f), right = rect.right - Screen.dp(20f);
        float max = maxZoom();
        float base = rect.top + Screen.dp(10f);
        for (int z10 = 10; z10 <= Math.round(max * 10); z10 += z10 < 20 ? 2 : z10 < 50 ? 5 : 10) {
          float z = z10 / 10f;
          boolean big = z10 == 10 || z10 == 20 || z10 == 50 || z10 == 100;
          float x = xForZoom(z, left, right);
          paint.setColor(ColorUtils.alphaColor(expandFactor, big ? accent : sub));
          float th = Screen.dp(big ? 14f : 8f);
          c.drawRoundRect(tgx101Rect(x - Screen.dp(1f), base, x + Screen.dp(1f), base + th), Screen.dp(1f), Screen.dp(1f), paint);
          if (big) {
            textPaint.setTextSize(Screen.dp(12f));
            textPaint.setColor(ColorUtils.alphaColor(expandFactor, accent));
            String s = Integer.toString(Math.round(z));
            c.drawText(s, x - textPaint.measureText(s) / 2f, rect.bottom - Screen.dp(7f), textPaint);
          }
        }
        float x = xForZoom(zoom, left, right);
        paint.setColor(ColorUtils.alphaColor(expandFactor, accent));
        c.drawRoundRect(tgx101Rect(x - Screen.dp(2f), base - Screen.dp(2f), x + Screen.dp(2f), base + Screen.dp(20f)), Screen.dp(2f), Screen.dp(2f), paint);

        // the badge over the bar, on the circle's edge
        String s = zoomText(zoom, true);
        textPaint.setTextSize(Screen.dp(15f));
        float tw = textPaint.measureText(s);
        float bh = Screen.dp(30f), bw = tw + Screen.dp(24f);
        float by = top - Screen.dp(6f) - bh;
        RectF b = new RectF(cx - bw / 2f, by, cx + bw / 2f, by + bh);
        paint.setColor(ColorUtils.alphaColor(expandFactor, accent));
        c.drawRoundRect(b, bh / 2f, bh / 2f, paint);
        textPaint.setColor(ColorUtils.alphaColor(expandFactor, accentText));
        c.drawText(s, cx - tw / 2f, b.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
      }
    }

    private float downX, dragFromX, dragFromZoom;
    private boolean dragging, touching;
    private final Runnable holdOpen = () -> {
      if (touching && !dragging) {
        dragging = true;
        dragFromX = downX;
        dragFromZoom = zoom;
        setExpanded(true);
      }
    };

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (getAlpha() < .5f) return false;
      float x = e.getX(), y = e.getY();
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          if (!rect.contains(x, y)) return false;
          downX = x;
          dragging = false;
          touching = true;
          getParent().requestDisallowInterceptTouchEvent(true);
          // (user 2026-11-26 «зажатие для зума должно быть сразу очевидным»): a hold opens the ruler without waiting
          // for the finger to move; a quick tap still picks a stop
          removeCallbacks(holdOpen);
          postDelayed(holdOpen, 180);
          return true;
        case MotionEvent.ACTION_MOVE:
          if (!touching) return false;
          if (!dragging && Math.abs(x - downX) > Screen.getTouchSlop()) {
            removeCallbacks(holdOpen);
            dragging = true;
            dragFromX = x;
            dragFromZoom = zoom;
            setExpanded(true);
          }
          if (dragging) {
            // from where the value was, not a jump to the finger; (user 2026-11-26 «у эталона можно за один свайп пройти
            // весь путь, у нас нужно два-три раза палец отрывать»): 1× → the top in a swipe of ~40 % of the screen
            float max = maxZoom();
            float f0 = max > 1f ? (float) (Math.log(dragFromZoom) / Math.log(max)) : 0f;
            float f = Math.max(0f, Math.min(1f, f0 + (x - dragFromX) / (getWidth() * .4f)));
            if (f <= 0f || f >= 1f) { // at an end: what goes past it counts back from the end at once
              dragFromX = x;
              dragFromZoom = f <= 0f ? 1f : max;
            }
            setZoom((float) Math.exp(f * Math.log(max)), false);
          }
          return true;
        case MotionEvent.ACTION_UP:
          if (!touching) return false;
          touching = false;
          removeCallbacks(holdOpen);
          if (dragging) {
            setExpanded(false);
          } else {
            for (int i = 0; i < 3; i++) {
              if (stopRects[i].contains(x, y)) {
                Tgx101Haptics.play(Tgx101Haptics.TICK);
                setZoom(stops[i], true);
                break;
              }
            }
          }
          return true;
        case MotionEvent.ACTION_CANCEL:
          touching = false;
          removeCallbacks(holdOpen);
          if (dragging) setExpanded(false);
          return true;
      }
      return touching;
    }
  }

  // ——— light ———

  private void updateFrontLight () {
    boolean front = host.isFrontCamera();
    float on = lightOn && front ? 1f : 0f;
    frontLight.setAlpha(on * videoRange * LEVELS[level]);
    boolean wantBright = lightOn && front && videoRange > 0f;
    if (wantBright != brightnessForced) {
      brightnessForced = wantBright;
      BaseActivity activity = UI.getContext(context);
      if (activity != null) {
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        params.screenBrightness = wantBright ? 1f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        activity.getWindow().setAttributes(params);
      }
    }
  }

  private void toggleLight () {
    lightOn = !lightOn;
    Tgx101Haptics.play(Tgx101Haptics.CLICK);
    lightButton.setActive(lightOn, true);
    CameraManagerLegacy camera = host.camera();
    if (camera != null) camera.tgx101SetTorch(lightOn && !host.isFrontCamera());
    updateFrontLight();
    org.thunderdog.challegram.Tgx101Diag.mark("round: light " + (lightOn ? "on" : "off") + (host.isFrontCamera() ? " (screen " + Math.round(LEVELS[level] * 100) + "%)" : " (torch)"));
  }

  /** the camera switched: the light goes with it — the torch on the rear, the screen on the front */
  public void onCameraSwitched () {
    updateFrontLight();
    // the other camera is opened anew: zoom and torch once it is up
    frontLight.removeCallbacks(reapplyLater);
    frontLight.postDelayed(reapplyLater, 700);
  }

  private final Runnable reapplyLater = this::reapply;

  private long downTime;
  private float downY;
  private boolean longPressed;
  private Runnable longPress;

  private boolean onLightTouch (View v, MotionEvent e) {
    if (v.getAlpha() < .5f) return false;
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downTime = SystemClock.uptimeMillis();
        downY = e.getRawY();
        longPressed = false;
        v.setPressed(true);
        if (host.isFrontCamera()) {
          longPress = () -> {
            longPressed = true;
            v.setPressed(false);
            if (!lightOn) toggleLight();
            drum.show(v, level);
          };
          v.postDelayed(longPress, 380);
        }
        v.getParent().requestDisallowInterceptTouchEvent(true);
        return true;
      case MotionEvent.ACTION_MOVE:
        if (longPressed) {
          int steps = Math.round((downY - e.getRawY()) / Screen.dp(26f));
          drum.setLevelFromDrag(steps);
        }
        return true;
      case MotionEvent.ACTION_UP:
        if (longPress != null) v.removeCallbacks(longPress);
        v.setPressed(false);
        if (longPressed) {
          drum.release();
        } else {
          toggleLight();
        }
        return true;
      case MotionEvent.ACTION_CANCEL:
        if (longPress != null) v.removeCallbacks(longPress);
        v.setPressed(false);
        if (longPressed) drum.release();
        return true;
    }
    return true;
  }

  private void setLevel (int newLevel) {
    newLevel = Math.max(0, Math.min(LEVELS.length - 1, newLevel));
    if (newLevel == level) return;
    level = newLevel; // (user 2026-11-26 «вибрацию в барабане убери»)
    org.thunderdog.challegram.unsorted.Settings.instance().pmc().putInt(KEY_LIGHT, level);
    updateFrontLight();
  }

  /** the drum of the screen light's strength: a capsule left of the button, the level in the middle */
  public class DrumView extends View {
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int[] loc = new int[2], own = new int[2];
    private View anchor;
    private int startLevel;
    private float shown, scroll; // scroll — the level drawn in the middle, eased
    private ValueAnimator showAnimator, scrollAnimator;

    DrumView (Context context) {
      super(context);
      textPaint.setTypeface(Fonts.getRobotoMedium());
    }

    void show (View anchor, int level) {
      this.anchor = anchor;
      this.startLevel = level;
      this.scroll = level;
      animateShown(1f);
    }

    void setLevelFromDrag (int steps) {
      int target = Math.max(0, Math.min(LEVELS.length - 1, startLevel + steps));
      if (target != level) {
        setLevel(target);
        if (scrollAnimator != null) scrollAnimator.cancel();
        scrollAnimator = ValueAnimator.ofFloat(scroll, target);
        scrollAnimator.setDuration((long) (160 / animScale()));
        scrollAnimator.addUpdateListener(a -> {
          scroll = (float) a.getAnimatedValue();
          invalidate();
        });
        scrollAnimator.start();
      }
    }

    void release () {
      postDelayed(() -> animateShown(0f), 450);
    }

    void hide () {
      if (showAnimator != null) showAnimator.cancel();
      shown = 0f;
      invalidate();
    }

    private void animateShown (float to) {
      if (showAnimator != null) showAnimator.cancel();
      showAnimator = ValueAnimator.ofFloat(shown, to);
      showAnimator.setDuration((long) ((to > 0f ? 220 : 180) / animScale()));
      showAnimator.setInterpolator(androidx.core.view.animation.PathInterpolatorCompat.create(.2f, 0f, 0f, 1f));
      showAnimator.addUpdateListener(a -> {
        shown = (float) a.getAnimatedValue();
        invalidate();
      });
      showAnimator.start();
    }

    @Override
    protected void onDraw (Canvas c) {
      if (shown <= 0f || anchor == null) return;
      anchor.getLocationOnScreen(loc);
      getLocationOnScreen(own);
      float ax = loc[0] - own[0], ay = loc[1] - own[1] + anchor.getHeight() / 2f;
      float w = Screen.dp(48f), h = Screen.dp(150f); // smaller (user 2026-11-26)
      float right = ax - Screen.dp(6f) + Screen.dp(12f) * (1f - shown);
      rect.set(right - w, ay - h / 2f, right, ay + h / 2f);
      int alpha = (int) (255 * shown);
      paint.setColor(0x40000000);
      paint.setAlpha((int) (0x40 * shown));
      c.drawRoundRect(tgx101Rect(rect.left, rect.top + Screen.dp(2f), rect.right, rect.bottom + Screen.dp(2f)), w / 2f, w / 2f, paint);
      paint.setColor(0xf01e2228);
      paint.setAlpha((int) (0xf0 * shown));
      c.drawRoundRect(rect, w / 2f, w / 2f, paint);

      // the middle mark
      float rowH = Screen.dp(26f);
      RectF mid = new RectF(rect.left + Screen.dp(6f), rect.centerY() - rowH / 2f, rect.right - Screen.dp(6f), rect.centerY() + rowH / 2f);
      paint.setColor(0xffffb300);
      paint.setAlpha(alpha);
      c.drawRoundRect(mid, rowH / 2f, rowH / 2f, paint);

      int save = c.save();
      c.clipRect(rect);
      for (int i = 0; i < LEVELS.length; i++) {
        float y = rect.centerY() - (i - scroll) * rowH; // higher level — higher up
        float dist = Math.abs(i - scroll);
        if (dist > 2.6f) continue;
        boolean on = dist < .5f;
        textPaint.setTextSize(Screen.dp(on ? 15f : 13f));
        textPaint.setColor(on ? 0xffffffff : 0xffa9b4bf);
        textPaint.setAlpha((int) (alpha * Math.max(0f, 1f - dist / 2.6f)));
        String s = Integer.toString(Math.round(LEVELS[i] * 100));
        c.drawText(s, rect.centerX() - textPaint.measureText(s) / 2f, y - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
      }
      c.restoreToCount(save);
    }
  }
}
