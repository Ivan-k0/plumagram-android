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
 * File created on 12/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.VideoTimelineView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class VideoControlView extends FrameLayoutFix implements FactorAnimator.Target, Destroyable {
  private final TextView nowView;
  private final TextView totalView;
  private final SliderView sliderView;
  private final PlayPauseButton playPauseButton;

  public VideoControlView (Context context) {
    super(context);

    setWillNotDraw(false);

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.BOTTOM);

    sliderView = new SliderView(context);
    sliderView.setAnchorMode(SliderView.ANCHOR_MODE_START);
    sliderView.setForceBackgroundColorId(ColorId.videoSliderInactive);
    sliderView.setForceSecondaryColorId(ColorId.videoSliderInactive);
    sliderView.setSlideEnabled(true, false);
    sliderView.setColorId(ColorId.videoSliderActive, false);
    sliderView.setPadding(Screen.dp(56f), 0, Screen.dp(56f), 0);
    sliderView.setLayoutParams(params);
    addView(sliderView);

    params = FrameLayoutFix.newParams(Screen.dp(56f), Screen.dp(56f), Gravity.LEFT | Gravity.BOTTOM);
    // params.leftMargin = Screen.dp(2f);

    this.nowView = new NoScrollTextView(context);
    styleText(nowView);
    this.nowView.setLayoutParams(params);
    addView(nowView);

    params = FrameLayoutFix.newParams(Screen.dp(56f), Screen.dp(56f), Gravity.RIGHT | Gravity.BOTTOM);
    // params.rightMargin = Screen.dp(2f);

    this.totalView = new NoScrollTextView(context);
    styleText(totalView);
    this.totalView.setLayoutParams(params);
    addView(totalView);

    playPauseButton = new PlayPauseButton(context);
    playPauseButton.setTranslationX(-Screen.dp(44f));
    playPauseButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(44f), Screen.dp(56f), Gravity.LEFT | Gravity.BOTTOM));
    addView(playPauseButton);
  }

  private @Nullable VideoTimelineView timelineView;

  public void addTrim (VideoTimelineView.TimelineDelegate delegate, ThemeDelegate forcedTheme) {
    setShowPlayPause(true, false);

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.BOTTOM);

    timelineView = new VideoTimelineView(getContext());
    timelineView.setShowSlider(true, false);
    timelineView.setColors(ColorId.white, ColorId.black, ColorId.transparentEditor);
    timelineView.setPadding(Screen.dp(54f) + Screen.dp(32f), Screen.dp(6f), Screen.dp(54f), Screen.dp(6f));
    timelineView.setLayoutParams(params);
    timelineView.setDelegate(delegate);
    timelineView.setForcedTheme(forcedTheme);
    addView(timelineView, 0);
    timelineView.setVisibility(View.GONE);
  }

  private boolean timelineVisible;

  private void setTimelineVisible (boolean isVisible) {
    if (this.timelineVisible != isVisible && timelineView != null) {
      this.timelineVisible = isVisible;
      timelineView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
      sliderView.setVisibility(isVisible ? View.GONE : View.VISIBLE);
    }
  }

  @Override
  public void performDestroy () {
    setFile(null);
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return !Views.onTouchEvent(this, ev) || super.onInterceptTouchEvent(ev);
  }

  private BoolAnimator showPlayPause = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  public void setShowPlayPause (boolean show, boolean animated) {
    if (timelineView == null) {
      showPlayPause.setValue(show || timelineVisible, animated);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    int dx = (int) ((float) Screen.dp(32f) * factor);
    nowView.setTranslationX(dx);
    sliderView.setAddPaddingLeft(dx);
    playPauseButton.setTranslationX(-Screen.dp(44f) * (1f - factor));
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  public void setIsPlaying (boolean isPlaying, boolean animated) {
    playPauseButton.setIsPlaying(isPlaying, animated && showPlayPause.getFloatValue() > 0f);
  }

  public void setOnPlayPauseClick (View.OnClickListener onClickListener) {
    playPauseButton.setOnClickListener(onClickListener);
  }

  public void setSliderListener (SliderView.Listener listener) {
    // TGx101: while the slider is dragged, the time under the finger is shown above the thumb
    sliderView.setListener(new SliderView.Listener() {
      @Override
      public void onSetStateChanged (SliderView view, boolean isSetting) {
        listener.onSetStateChanged(view, isSetting);
        tgx101ShowSeekBubble(isSetting);
      }

      @Override
      public void onValueChanged (SliderView view, float factor) {
        listener.onValueChanged(view, factor);
        tgx101UpdateSeekBubble(factor);
      }

      @Override
      public boolean allowSliderChanges (SliderView view) {
        return listener.allowSliderChanges(view);
      }
    });
  }

  // TGx101: chapters from the caption's timestamps — marks on the track, the current one's title above the bar

  private @Nullable long[] chapterStarts;
  private @Nullable String[] chapterTitles;
  private @Nullable TextView chapterView;
  private int shownChapter = -1;

  public void setTgx101Chapters (@Nullable long[] starts, @Nullable String[] titles, @Nullable View.OnClickListener onClick) {
    boolean has = starts != null && titles != null && starts.length >= 2;
    chapterStarts = has ? starts : null;
    chapterTitles = has ? titles : null;
    shownChapter = -1;
    tgx101ChapterTitle = has ? ms -> {
      int index = chapterIndex(ms);
      return index >= 0 ? chapterTitles[index] : null;
    } : null;
    if (has && chapterView == null) {
      chapterView = new NoScrollTextView(getContext());
      chapterView.setTextColor(0xffffffff);
      chapterView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13f);
      chapterView.setTypeface(Fonts.getRobotoMedium());
      chapterView.setSingleLine(true);
      chapterView.setEllipsize(android.text.TextUtils.TruncateAt.END);
      chapterView.setCompoundDrawablePadding(Screen.dp(4f));
      chapterView.setPadding(Screen.dp(10f), Screen.dp(4f), Screen.dp(8f), Screen.dp(4f));
      android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
      bg.setColor(0xb3161c22);
      bg.setCornerRadius(Screen.dp(12f));
      chapterView.setBackground(bg);
      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM);
      params.leftMargin = Screen.dp(14f);
      params.rightMargin = Screen.dp(14f);
      params.bottomMargin = Screen.dp(62f);
      addView(chapterView, params);
    }
    if (chapterView != null) {
      chapterView.setVisibility(has ? View.VISIBLE : View.GONE);
      chapterView.setOnClickListener(onClick);
      if (has) updateChapter(nowDurationMs);
    }
    updateChapterMarks();
  }

  private int chapterIndex (long ms) {
    if (chapterStarts == null) return -1;
    int index = -1;
    for (int i = 0; i < chapterStarts.length; i++) {
      if (chapterStarts[i] <= ms) index = i; else break;
    }
    return index;
  }

  private void updateChapter (long ms) {
    if (chapterView == null || chapterTitles == null) return;
    int index = Math.max(0, chapterIndex(ms));
    if (index != shownChapter) {
      shownChapter = index;
      chapterView.setText(chapterTitles[index] + "  ›");
    }
  }

  private void updateChapterMarks () {
    if (chapterStarts == null || totalDurationMs <= 0) {
      sliderView.setTgx101Marks(null);
      return;
    }
    float[] marks = new float[chapterStarts.length];
    for (int i = 0; i < marks.length; i++) marks[i] = (float) ((double) chapterStarts[i] / totalDurationMs);
    sliderView.setTgx101Marks(marks);
  }

  private @Nullable TextView seekBubble;

  /** TGx101: the chapter title at a moment of the video (from the caption's timestamps), shown under the time */
  public interface ChapterTitle {
    @Nullable String titleAt (long ms);
  }

  public @Nullable ChapterTitle tgx101ChapterTitle;

  private void tgx101ShowSeekBubble (boolean show) {
    if (show && seekBubble == null) {
      seekBubble = new NoScrollTextView(getContext());
      seekBubble.setTextColor(0xffffffff);
      seekBubble.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14f);
      seekBubble.setTypeface(Fonts.getRobotoMedium());
      seekBubble.setGravity(Gravity.CENTER);
      seekBubble.setMaxLines(2);
      seekBubble.setEllipsize(android.text.TextUtils.TruncateAt.END);
      seekBubble.setMaxWidth(Screen.dp(220f));
      seekBubble.setPadding(Screen.dp(10f), Screen.dp(5f), Screen.dp(10f), Screen.dp(5f));
      android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
      bg.setColor(0xe6161c22);
      bg.setCornerRadius(Screen.dp(12f));
      seekBubble.setBackground(bg);
      seekBubble.setAlpha(0f);
      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM);
      params.bottomMargin = Screen.dp(58f);
      addView(seekBubble, params);
    }
    if (seekBubble != null) {
      if (show) tgx101UpdateSeekBubble(sliderView.getValue());
      seekBubble.animate().cancel();
      seekBubble.animate().alpha(show ? 1f : 0f).setDuration(show ? 100 : 160).start();
    }
  }

  private void tgx101UpdateSeekBubble (float factor) {
    if (seekBubble == null || totalDurationMs <= 0) return;
    long ms = (long) (MathUtils.clamp(factor) * totalDurationMs);
    String text = Strings.buildDuration(Math.round(ms / 1000.0));
    String chapter = tgx101ChapterTitle != null ? tgx101ChapterTitle.titleAt(ms) : null;
    seekBubble.setText(chapter != null ? text + "\n" + chapter : text);
    seekBubble.measure(MeasureSpec.makeMeasureSpec(Screen.dp(220f), MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
    int width = seekBubble.getMeasuredWidth();
    float x = sliderView.getLeft() + sliderView.getTranslationX() + sliderView.tgx101ThumbX() - width / 2f;
    seekBubble.setTranslationX(Math.max(Screen.dp(8f), Math.min(x, getWidth() - width - Screen.dp(8f))));
  }

  public void setInnerAlpha (float alpha) {
    sliderView.setAlpha(alpha);
    if (timelineView != null) {
      timelineView.setAlpha(alpha);
    }
    nowView.setAlpha(alpha);
    totalView.setAlpha(alpha);
  }

  public float getInnerAlpha () {
    return sliderView.getAlpha();
  }

  private static void styleText (TextView textView) {
    textView.setTextColor(0xffffffff);
    textView.setPadding(Screen.dp(2f), 0, Screen.dp(2f), 0);
    textView.setGravity(Gravity.CENTER);
    textView.setSingleLine(true);
    textView.setTypeface(Fonts.getRobotoRegular());
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
    textView.setText(Strings.buildDuration(0));
  }

  private long totalDurationMs;

  public void resetDuration (long totalDurationMs, long nowDurationMs, boolean canSeek, boolean animated) {
    boolean hasSlider = canSeek && totalDurationMs > 0;
    sliderView.setSlideEnabled(hasSlider, animated);
    if (timelineView != null) {
      timelineView.setCanSlide(hasSlider, animated);
      timelineView.setSliderProgress(totalDurationMs > 0 ? (float) ((double) nowDurationMs / (double) totalDurationMs) : 0f);
      timelineView.invalidate();
    }
    setNow(nowDurationMs, animated);
    setTotalMs(totalDurationMs);
  }

  public void setFile (ImageGalleryFile file) {
    if (timelineView != null) {
      String path = file != null ? file.getFilePath() : null;
      boolean visible = !StringUtils.isEmpty(path);
      double startTime, endTime;
      double duration;
      float newStart, newEnd;
      if (file != null && file.hasTrim()) {
        long durationUs = file.getTotalDurationUs();
        long startUs = file.getStartTimeUs();
        long endUs = file.getEndTimeUs();
        duration = (double) durationUs / 1_000_000.0;
        newStart = (float) ((double) startUs / (double) durationUs);
        startTime = (double) startUs / 1_000_000.0;
        if (endUs == -1) {
          newEnd = 1.0f;
          endTime = duration;
        } else {
          newEnd = (float) ((double) endUs / (double) durationUs);
          endTime = (double) endUs / 1_000_000.0;
        }
      } else {
        newStart = 0f;
        newEnd = 1f;
        startTime = endTime = -1;
        duration = 0;
      }
      timelineView.setVideoPath(path, newStart, newEnd, startTime, endTime, duration, timelineVisible && visible);
      timelineView.setSliderProgress(0f);
      setTimelineVisible(visible);
    }
  }

  private long nowDurationMs;

  public void setNow (long nowMs, boolean animated) {
    setNowMs(nowMs);
    updateSlider(animated);
  }

  private boolean slideEnabled;

  public void setSlideEnabled (boolean isEnabled) {
    if (this.slideEnabled != isEnabled) {
      this.slideEnabled = isEnabled;
      updateSliderAvailability();
    }
  }

  private void updateSliderAvailability () {
    boolean hasSlider = slideEnabled && totalDurationMs > 0;
    sliderView.setSlideEnabled(hasSlider, true);
    if (timelineView != null) {
      timelineView.setCanSlide(hasSlider, true);
    }
  }

  private void updateSlider (boolean animated) {
    float progress = MathUtils.clamp(totalDurationMs > 0 ? (float) ((double) nowDurationMs / (double) totalDurationMs) : 0f);
    sliderView.setValue(progress);
    if (timelineView != null) {
      timelineView.setSliderProgress(progress);
    }
  }

  private void setNowMs (long ms) {
    if (this.nowDurationMs != ms) {
      this.nowDurationMs = ms;
      updateChapter(ms);
      nowView.setText(Strings.buildDuration(Math.round(ms / 1000.0)));
    }
  }

  private void setTotalMs (long ms) {
    if (this.totalDurationMs != ms) {
      boolean changedState = (ms == 0 || totalDurationMs == 0);
      this.totalDurationMs = ms;
      totalView.setText(Strings.buildDuration(Math.round(ms / 1000.0)));
      updateChapterMarks();
      if (changedState) {
        updateSliderAvailability();
      }
    }
  }

  public void updateSeek (long nowDurationMs, long durationMs, float progress) {
    setNowMs(nowDurationMs);
    setTotalMs(durationMs);
    progress = MathUtils.clamp(progress);
    if (sliderView != null) {
      sliderView.setValue(progress);
    }
    if (timelineView != null) {
      timelineView.setSliderProgress(progress);
    }
  }

  public void updateSecondarySeek (float offset, float progress) {
    if (sliderView != null) {
      sliderView.setSecondaryValue(offset, progress);
    }
  }

  // TGx101 player (mockup 3): the bar as a floating dark-glass capsule with a speed chip, the child lock and ⋮ settings

  private boolean capsule;
  private TextView speedView;
  private LockIcon lockView;
  private android.widget.ImageView settingsView;
  private final android.graphics.RectF capsuleRect = new android.graphics.RectF();

  public void tgx101EnableCapsule (View.OnClickListener onSpeed, View.OnClickListener onLock, View.OnClickListener onSettings) {
    if (capsule) return;
    capsule = true;
    int side = Screen.dp(10f);
    int toolWidth = Screen.dp(40f);

    settingsView = new android.widget.ImageView(getContext());
    settingsView.setImageResource(org.thunderdog.challegram.R.drawable.baseline_more_vert_24);
    settingsView.setColorFilter(0xffffffff);
    settingsView.setScaleType(android.widget.ImageView.ScaleType.CENTER);
    settingsView.setOnClickListener(onSettings);
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(toolWidth, Screen.dp(56f), Gravity.RIGHT | Gravity.BOTTOM);
    params.rightMargin = side;
    addView(settingsView, params);

    lockView = new LockIcon(getContext());
    lockView.onHeld = () -> onLock.onClick(lockView); // locking takes a 1.5 s hold (a ring fills up), not a tap
    lockView.setContentDescription(org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.Tgx101PlayerLock));
    params = FrameLayoutFix.newParams(toolWidth, Screen.dp(56f), Gravity.RIGHT | Gravity.BOTTOM);
    params.rightMargin = side + toolWidth;
    addView(lockView, params);

    speedView = new NoScrollTextView(getContext());
    styleText(speedView);
    speedView.setTypeface(Fonts.getRobotoMedium());
    speedView.setOnClickListener(onSpeed);
    params = FrameLayoutFix.newParams(toolWidth, Screen.dp(56f), Gravity.RIGHT | Gravity.BOTTOM);
    params.rightMargin = side + toolWidth * 2;
    addView(speedView, params);
    setTgx101Speed(1f);

    prevView = navButton(org.thunderdog.challegram.R.drawable.baseline_skip_previous_24_white, Gravity.LEFT | Gravity.BOTTOM);
    nextView = navButton(org.thunderdog.challegram.R.drawable.baseline_skip_next_24_white, Gravity.RIGHT | Gravity.BOTTOM);
    applyNavLayout();
  }

  // Previous / next file: only in landscape (no room in portrait), only when there is one

  private android.widget.ImageView prevView, nextView;
  private boolean hasPrev, hasNext;

  private android.widget.ImageView navButton (int icon, int gravity) {
    android.widget.ImageView view = new android.widget.ImageView(getContext());
    view.setImageResource(icon);
    view.setScaleType(android.widget.ImageView.ScaleType.CENTER);
    view.setVisibility(View.GONE);
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(36f), Screen.dp(56f), gravity);
    if ((gravity & Gravity.LEFT) == Gravity.LEFT) params.leftMargin = Screen.dp(12f); else params.rightMargin = Screen.dp(12f);
    addView(view, params);
    return view;
  }

  public void setTgx101Nav (boolean hasPrev, boolean hasNext, View.OnClickListener onPrev, View.OnClickListener onNext) {
    this.hasPrev = hasPrev;
    this.hasNext = hasNext;
    if (prevView == null) return;
    prevView.setOnClickListener(onPrev);
    nextView.setOnClickListener(onNext);
    applyNavLayout();
  }

  private void applyNavLayout () {
    if (!capsule) return;
    boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    boolean showPrev = landscape && hasPrev, showNext = landscape && hasNext;
    prevView.setVisibility(showPrev ? View.VISIBLE : View.GONE);
    nextView.setVisibility(showNext ? View.VISIBLE : View.GONE);
    int side = Screen.dp(10f), toolWidth = Screen.dp(40f), nav = Screen.dp(36f);
    int leftShift = showPrev ? nav : 0, rightShift = showNext ? nav : 0;
    ((FrameLayoutFix.LayoutParams) settingsView.getLayoutParams()).rightMargin = side + rightShift;
    ((FrameLayoutFix.LayoutParams) lockView.getLayoutParams()).rightMargin = side + rightShift + toolWidth;
    ((FrameLayoutFix.LayoutParams) speedView.getLayoutParams()).rightMargin = side + rightShift + toolWidth * 2;
    ((FrameLayoutFix.LayoutParams) totalView.getLayoutParams()).rightMargin = side + rightShift + toolWidth * 3;
    ((FrameLayoutFix.LayoutParams) nowView.getLayoutParams()).leftMargin = side + leftShift;
    ((FrameLayoutFix.LayoutParams) playPauseButton.getLayoutParams()).leftMargin = side + leftShift;
    sliderView.setPadding(Screen.dp(56f) + side + leftShift, 0, Screen.dp(56f) + side + rightShift + toolWidth * 3, 0);
    requestLayout();
    invalidate();
  }

  public @Nullable Runnable tgx101OnConfigurationChanged;

  @Override
  protected void onConfigurationChanged (android.content.res.Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    applyNavLayout();
    if (tgx101OnConfigurationChanged != null) tgx101OnConfigurationChanged.run();
  }

  public void setTgx101Speed (float speed) {
    if (speedView != null) {
      String text = (speed == (int) speed ? Integer.toString((int) speed) : Float.toString(speed)) + "×";
      speedView.setText(text);
    }
  }

  // The rotate button lives over the video (above the capsule, on the right), outside this view: it follows the
  // capsule's place, visibility and lock state on every frame

  private @Nullable View companion;
  private final int[] companionLocation = new int[2], parentLocation = new int[2];
  private boolean locked;

  public void tgx101SetCompanion (View companion) {
    this.companion = companion;
    getViewTreeObserver().addOnPreDrawListener(() -> {
      syncCompanion();
      return true;
    });
  }

  private void syncCompanion () {
    View companion = this.companion;
    if (companion == null || !(companion.getParent() instanceof View)) return;
    float alpha = getAlpha() * getInnerAlpha();
    if (alpha <= 0f || !isShown()) {
      companion.setAlpha(0f);
      companion.setVisibility(View.INVISIBLE);
      return;
    }
    companion.setVisibility(View.VISIBLE);
    companion.setAlpha(alpha * (locked ? .35f : 1f));
    companion.setEnabled(!locked);
    getLocationOnScreen(companionLocation);
    ((View) companion.getParent()).getLocationOnScreen(parentLocation);
    float capsuleTop = companionLocation[1] + getMeasuredHeight() - Screen.dp(56f) + getTranslationY() * 0f;
    companion.setTranslationY(capsuleTop - parentLocation[1] - companion.getMeasuredHeight() - Screen.dp(8f));
  }

  /** The child lock: only the lock itself stays active, everything else is dimmed and doesn't react */
  public void setTgx101Locked (boolean locked, boolean animated) {
    this.locked = locked;
    if (lockView == null) return;
    lockView.setLocked(locked, animated);
    float alpha = locked ? .35f : 1f;
    for (View view : new View[] {speedView, settingsView, prevView, nextView}) { // pause keeps working under the lock
      view.setEnabled(!locked);
      view.setClickable(!locked);
      view.animate().alpha(alpha).setDuration(150).start();
    }
    sliderView.setEnabled(!locked);
  }

  /** The pause button's centre on the screen (it works under the lock); false while hidden */
  public boolean tgx101GetPauseCenter (int[] out) {
    if (getAlpha() < .5f || playPauseButton.getTranslationX() < -Screen.dp(20f)) return false;
    playPauseButton.getLocationOnScreen(out);
    out[0] += playPauseButton.getWidth() / 2;
    out[1] += playPauseButton.getHeight() / 2;
    return true;
  }

  /** The lock button's centre on the screen (for the lock layer, which takes every touch); false while hidden */
  public boolean tgx101GetLockCenter (int[] out) {
    if (lockView == null || getAlpha() < .5f) return false;
    lockView.getLocationOnScreen(out);
    out[0] += lockView.getWidth() / 2;
    out[1] += lockView.getHeight() / 2;
    return true;
  }

  /** Padlock like the passcode one: the shackle slides aside when it is open; held 1.5 s while open it locks */
  private static final class LockIcon extends View implements FactorAnimator.Target {
    private static final long HOLD_MS = 1500;
    private final android.graphics.drawable.Drawable top, base;
    private final BoolAnimator open = new BoolAnimator(0, this, new android.view.animation.OvershootInterpolator(3f), 160L, true);
    private final android.graphics.Paint ringPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF ring = new android.graphics.RectF();
    Runnable onHeld;
    private boolean holding;
    private long holdStart;

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (!open.getValue()) return false; // locked: the lock layer above takes the touches (2 s hold to unlock)
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          holding = true;
          holdStart = android.os.SystemClock.uptimeMillis();
          org.thunderdog.challegram.Tgx101Diag.mark("player: lock button held");
          invalidate();
          return true;
        case MotionEvent.ACTION_MOVE:
          if (holding && (e.getX() < -Screen.dp(10f) || e.getY() < -Screen.dp(10f) || e.getX() > getWidth() + Screen.dp(10f) || e.getY() > getHeight() + Screen.dp(10f))) {
            holding = false;
            invalidate();
          }
          return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          if (holding) {
            org.thunderdog.challegram.Tgx101Diag.mark("player: lock button released after " + (android.os.SystemClock.uptimeMillis() - holdStart) + " ms — not locked");
          }
          holding = false;
          invalidate();
          return true;
      }
      return true;
    }

    LockIcon (Context context) {
      super(context);
      top = org.thunderdog.challegram.tool.Drawables.get(getResources(), org.thunderdog.challegram.R.drawable.baseline_lock_top_24);
      base = org.thunderdog.challegram.tool.Drawables.get(getResources(), org.thunderdog.challegram.R.drawable.baseline_lock_base_24);
    }

    void setLocked (boolean locked, boolean animated) {
      open.setValue(!locked, animated);
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      invalidate();
    }

    @Override
    protected void onDraw (Canvas c) {
      int cx = getMeasuredWidth() / 2, cy = getMeasuredHeight() / 2;
      android.graphics.Paint paint = Paints.getPorterDuffPaint(0xffffffff);
      org.thunderdog.challegram.tool.Drawables.draw(c, top, cx - top.getMinimumWidth() / 2 + (int) (Screen.dp(8f) * open.getFloatValue()), cy - top.getMinimumHeight() / 2, paint);
      org.thunderdog.challegram.tool.Drawables.draw(c, base, cx - base.getMinimumWidth() / 2, cy - base.getMinimumHeight() / 2, paint);
      if (holding) {
        float progress = Math.min(1f, (android.os.SystemClock.uptimeMillis() - holdStart) / (float) HOLD_MS);
        ringPaint.setStyle(android.graphics.Paint.Style.STROKE);
        ringPaint.setStrokeWidth(Screen.dp(3f));
        ringPaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        ringPaint.setColor(0xff5b87b0);
        float r = Screen.dp(17f);
        ring.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(ring, -90, 360 * progress, false, ringPaint);
        if (progress >= 1f) {
          holding = false;
          if (onHeld != null) post(onHeld);
          return;
        }
        postInvalidateOnAnimation();
      }
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (capsule) {
      int side = Screen.dp(10f);
      capsuleRect.set(side, getMeasuredHeight() - Screen.dp(56f) + Screen.dp(4f), getMeasuredWidth() - side, getMeasuredHeight() - Screen.dp(4f));
      c.drawRoundRect(capsuleRect, Screen.dp(22f), Screen.dp(22f), Paints.fillingPaint(0xc0141a20));
      return;
    }
    c.drawRect(0, getMeasuredHeight() - Screen.dp(56f), getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.getColor(ColorId.transparentEditor)));
  }
}
