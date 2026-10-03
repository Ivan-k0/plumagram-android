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
 * File created on 01/09/2015 at 01:50
 */
package org.thunderdog.challegram.mediaview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.data.MediaStack;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class MediaView extends FrameLayoutFix {
  public static final int DIRECTION_AUTO = 0;
  public static final int DIRECTION_BACKWARD = 1;
  public static final int DIRECTION_FORWARD = 2;
  public static final int DIRECTION_RESET = 3;

  private static final boolean USE_GRADIENTS = false;
  private static final float HEADER_ALPHA = USE_GRADIENTS ? 0xff : 0x99;

  public interface ClickListener {
    void onClick (MediaView mediaView, float x, float y);
  }

  // private final Paint backgroundPaint;
  // private final Paint headerPaint;

  private MediaStack stack;
  private MediaCellView baseCell;
  private @Nullable MediaCellView previewCell;

  private @Nullable MediaGestureDetector detector;

  // View setup

  public boolean isOpen () {
    return boundController != null && boundController.isFullyShown();
  }

  public MediaView (Context context) {
    super(context);

    // Paints

    /*backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    backgroundPaint.setColor(0xff000000);*/

    // Cells
  }

  boolean isKeyboardVisible () {
    return boundController != null && boundController.getKeyboardState();
  }

  public void prepare (boolean needPreview) {
    baseCell = new MediaCellView(getContext());
    if (needPreview) {
      previewCell = new MediaCellView(getContext());
      previewCell.setFactor(-1f);
      addView(previewCell);
    }

    addView(baseCell);

    // Touch components

    if (needPreview) {
      detector = new MediaGestureDetector(getContext());
      detector.setBoundView(this);
    }

    setWillNotDraw(false);
  }

  public MediaCellView findCellForItem (MediaItem item) {
    if (baseCell != null && baseCell.getMedia() == item) {
      return baseCell;
    }
    if (previewCell != null && previewCell.getMedia() == item) {
      return previewCell;
    }
    return null;
  }

  public MediaCellView getBaseCell () {
    return baseCell;
  }

  private MediaViewController boundController;

  public void setBoundController (MediaViewController clickController) {
    this.boundController = clickController;
  }

  public void dispatchOnMediaZoom () {
    if (boundController != null) {
      boundController.onMediaZoomStart();
    }
  }

  public void onMediaClick (float x, float y) {
    if (boundController != null) {
      boundController.onMediaItemClick(this, x, y);
    }
  }

  public void setCellCallback (MediaCellView.Callback callback) {
    baseCell.setCallback(callback);
    if (previewCell != null) {
      previewCell.setCallback(callback);
    }
  }

  public void pauseIfPlaying () {
    baseCell.pauseIfPlaying();
    if (previewCell != null) {
      previewCell.pauseIfPlaying();
    }
  }

  public void autoplayIfNeeded (boolean isSwitch) {
    baseCell.autoplayIfNeeded(isSwitch);
  }

  public void setSeekProgress (float progress) {
    baseCell.setSeekProgress(progress);
    if (previewCell != null) {
      previewCell.setSeekProgress(progress);
    }
  }

  public void resumeIfNeeded (float progress) {
    baseCell.resumeIfNeeded(progress);
    if (previewCell != null) {
      previewCell.resumeIfNeeded(progress);
    }
  }

  private boolean disallowMove;

  public void setDisallowMove (boolean disallowMove) {
    this.disallowMove = disallowMove;
  }

  private boolean disableTouch;

  public void setDisableTouch (boolean disableTouch) {
    this.disableTouch = disableTouch;
  }

  private ClickListener butStillNeedClick;

  public void setButStillNeedClick (ClickListener butStillNeedClick) {
    if (this.butStillNeedClick != butStillNeedClick){
      this.butStillNeedClick = butStillNeedClick;
      this.catchingClick = false;
    }
  }

  public boolean isTouchEnabled (boolean isTouchDown) {
    return !disableTouch && (boundController == null || boundController.allowMediaViewGestures(isTouchDown));
  }

  public void initWithStack (MediaStack stack) {
    this.stack = stack;

    baseCell.setFactor(0f);
    baseCell.setMedia(stack.getCurrent());
  }

  public boolean canZoom (MediaCellView cellView) {
    return baseCell == cellView && factor == 0f;
  }

  public boolean canMoveZoomedView (MediaCellView celView) {
    return boundController == null || boundController.getMode() != MediaViewController.MODE_GALLERY || boundController.allowMovingZoomedView();
  }

  private MediaCellView revealCell;
  private MediaViewThumbLocation thumb;

  public void setTarget (MediaViewThumbLocation thumb, float factor) {
    this.thumb = thumb;
    this.revealCell = baseCell;
    this.revealCell.setTargetLocation(thumb);
    this.revealCell.setRevealFactor(factor);
    this.revealCell.setDisappearing(factor == 1f);
  }

  public void setPendingOpenAnimator (FactorAnimator animator) {
    revealCell.setTargetAnimator(animator);
  }

  public void setRevealFactor (float revealFactor) {
    revealCell.setRevealFactor(revealFactor);
  }

  public boolean isBaseVisible () {
    return baseCell.hasVisibleContent();
  }

  public void setDisableAnimations (boolean disable) {
    baseCell.setDisableAnimations(disable);
    if (previewCell != null) {
      previewCell.setDisableAnimations(disable);
    }
  }

  // Current values

  private int currentWidth, currentHeight;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = MeasureSpec.getSize(heightMeasureSpec); // UI.get().getWindow().getDecorView().getMeasuredHeight();

    boolean sameWidth = currentWidth == width;
    boolean dimensionsChanged = currentHeight != height || !sameWidth;

    if (dimensionsChanged) {
      currentWidth = width;
      currentHeight = height;
      buildLayout(sameWidth);
    }

    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  public void setPaddingHorizontal (int paddingHorizontal) {
    if (this.paddingHorizontal != paddingHorizontal) {
      setOffsets(paddingHorizontal, offsetLeft, offsetTop, offsetRight, offsetBottom);
    }
  }

  public void setOffsets (int paddingHorizontal, int offsetLeft, int offsetTop, int offsetRight, int offsetBottom) {
    if (this.paddingHorizontal != paddingHorizontal || this.offsetTop != offsetTop || this.offsetBottom != offsetBottom) {
      this.offsetTop = offsetTop;
      this.offsetBottom = offsetBottom;
      this.paddingHorizontal = paddingHorizontal;

      if (layoutBuilt) {
        baseCell.setOffsets(paddingHorizontal, offsetLeft, offsetTop, offsetRight, offsetBottom);
        if (previewCell != null) {
          previewCell.setOffsets(paddingHorizontal, offsetLeft, offsetTop, offsetRight, offsetBottom);
        }
      }
    }
  }

  public void setNavigationalOffsets (int left, int right, int bottom) {
    setOffsets(this.paddingHorizontal, left, this.offsetTop, right, bottom);
  }

  public int getPaddingHorizontal () {
    return paddingHorizontal;
  }

  public int getOffsetBottom () {
    return offsetBottom;
  }

  public int getOffsetTop () {
    return offsetTop;
  }

  public boolean isZoomed () {
    return baseCell.isZoomed();
  }

  public void normalizeZoom () {
    baseCell.normalizeZoom();
  }

  private int offsetLeft, offsetTop, offsetRight, offsetBottom, paddingHorizontal;
  private boolean layoutBuilt;

  private void buildLayout (boolean animated) {
    layoutBuilt = true;
    baseCell.getDetector().normalizeZoom(animated);
    if (previewCell != null) {
      previewCell.getDetector().reset();
    }
    dropPendingTouches();
    layoutCells();
  }

  public int getBottomAdd () {
    if (boundController != null && boundController.getKeyboardState() && boundController.getArgumentsStrict().mode == MediaViewController.MODE_GALLERY) {
      return boundController.getBottomInnerMargin();
    }
    return 0;
  }

  public void layoutCells () {
    int currentHeight = this.currentHeight + getBottomAdd();
    baseCell.layoutCell(paddingHorizontal, offsetLeft, offsetTop, offsetRight, offsetBottom, currentWidth, currentHeight);
    if (previewCell != null) {
      previewCell.layoutCell(paddingHorizontal, offsetLeft, offsetTop, offsetRight, offsetBottom, currentWidth, currentHeight);
    }
  }

  // Translation

  private float factor;

  public void switchPhoto (float newFactor, float oldFactor) {
    if (previewCell != null) {
      if (newFactor > 0f) {
        if (oldFactor <= 0f) {
          previewCell.setMedia(stack.getNext());
        }
      } else if (newFactor < 0f) {
        if (oldFactor >= 0f) {
          previewCell.setMedia(stack.getPrevious());
        }
      }
    }
  }

  public void replaceMedia (MediaItem oldItem, MediaItem newItem) {
    if (previewCell != null && previewCell.getMedia() == oldItem) {
      if (newItem != null) {
        previewCell.setMedia(newItem);
      } else {
        dropPreview(DIRECTION_RESET, 0f);
      }
    }
    if (baseCell.getMedia() == oldItem) {
      baseCell.setMedia(newItem);
    }
  }

  public void translate (float factor) { // 0f normal, -1f - left preview is showing, 1f - right preview is showing
    if (this.factor != factor) {
      switchPhoto(factor, this.factor);
      setFactorImpl(factor);
      translateCells();
      invalidate();
    }
  }

  private void translateCells () {
    if (factor == 0f || previewCell == null) {
      baseCell.setFactor(0f);
      if (previewCell != null) {
        previewCell.setFactor(-1f);
      }
      if (indexOfChild(baseCell) != 1) {
        bringChildToFront(baseCell);
      }
    } else {
      if (factor < 0f) { // sliding to left image
        baseCell.setFactor(factor);
        previewCell.setFactor(1f + factor);
        if (indexOfChild(previewCell) != 1) {
          bringChildToFront(previewCell);
        }
      } else { // sliding to right image
        baseCell.setFactor(factor);
        previewCell.setFactor(-1f + factor);
        if (indexOfChild(baseCell) != 1) {
          bringChildToFront(baseCell);
        }
      }
    }
  }

  public interface FactorChangeListener {
    void onFactorChanged (MediaView view, float factor);
  }

  private @Nullable FactorChangeListener factorChangeListener;

  public void setFactorChangeListener (@Nullable FactorChangeListener factorChangeListener) {
    this.factorChangeListener = factorChangeListener;
  }

  private boolean isAnimating;

  private void setFactorImpl (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      if (factorChangeListener != null) {
        factorChangeListener.onFactorChanged(this, factor);
      }
    }
  }

  public float getFactor () {
    return factor;
  }

  public boolean isStill () {
    return factor == 0f;
  }

  private ValueAnimator animator;

  public void dropPreview (int direction, float velocity) { // velocity from 0f to 1f
    float nextFactor;

    switch (direction) {
      case DIRECTION_AUTO: {
        if (factor == 0f) {
          return;
        }
        nextFactor = factor <= -.5f && hasPrevious() ? -1f : factor >= .5f && hasNext() ? 1f : 0f;
        break;
      }
      case DIRECTION_FORWARD: {
        if (factor == 1f) {
          if (!applyPreview()) {
            dropPreview(DIRECTION_AUTO, velocity);
          }
          return;
        }
        nextFactor = hasNext() ? 1f : 0f;
        break;
      }
      case DIRECTION_BACKWARD: {
        if (factor == -1f && applyPreview()) {
          dropPreview(DIRECTION_AUTO, velocity);
          return;
        }
        nextFactor = hasPrevious() ? -1f : 0f;
        break;
      }
      default: {
        nextFactor = 0f;
        break;
      }
    }

    final float startFactor = getFactor();
    final float factorDiff = nextFactor - startFactor;
    animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> translate(startFactor + factorDiff * AnimatorUtils.getFraction(animation)));
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (factor != 0f) {
          applyPreview();
        }
        isAnimating = false;
        if (factor == 0f && previewCell != null) {
          previewCell.setMedia(null);
        }
      }
    });
    animator.setDuration(300);

    isAnimating = true;
    animator.start();
  }

  public void onMediaActivityPause () {
    baseCell.onCellActivityPause();
    if (previewCell != null) {
      previewCell.onCellActivityPause();
    }
  }

  public void onMediaActivityResume () {
    baseCell.onCellActivityResume();
    if (previewCell != null) {
      previewCell.onCellActivityResume();
    }
  }

  public void destroy () {
    baseCell.destroy();
    if (previewCell != null) {
      previewCell.destroy();
    }
  }

  private boolean applyPreview () {
    if (factor <= -.5f) {
      if (!stack.hasPrevious()) {
        return false;
      }
      stack.applyPrevious();
      setFactorImpl(1f + factor);
    } else if (factor >= .5f) {
      if (!stack.hasNext()) {
        return false;
      }
      stack.applyNext();
      setFactorImpl(-1f + factor);
    } else {
      return false;
    }

    MediaCellView cell = baseCell;
    baseCell = previewCell;
    previewCell = cell;

    translateCells();
    invalidate();

    return true;
  }

  // Drawing

  /*private float headerFactor;

  public void setHeaderFactor (float factor) {
    if (this.headerFactor != factor) {
      this.headerFactor = factor;
      headerPaint.setAlpha((int) (HEADER_ALPHA * factor));
      invalidate(0, 0, currentWidth, Size.HEADER_PORTRAIT_SIZE);
    }
  }*/

  /*@Override
  public void draw (Canvas c) {


    *//*if (commonFactor > 0f) {
      if (thumb == null || commonFactor >= 1f || true) {

      } else {
        int fromX = thumb.centerX();
        int fromY = thumb.centerY();

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        int targetX = width / 2;
        int targetY = height / 2;

        float startRadius = Math.min(thumb.width(), thumb.height()) / 2;
        float radius = (float) Math.sqrt(width * width + height * height) * .5f;

        float centerX = fromX + (float) (targetX - fromX) * alpha;
        float centerY = fromY + (float) (targetY - fromY) * alpha;
        float targetRadius = startRadius + (radius - startRadius) * alpha;

        c.drawCircle(centerX, centerY, targetRadius, Paints.fillingPaint(color));
      }
    }*//*

    super.draw(c);

    *//*if (true) {
      return;
    }

    float factor;

    if ((this.factor > 0f && !stack.hasNext()) || (this.factor < 0f && !stack.hasPrevious())) {
      factor = .25f * this.factor;
    } else {
      factor = this.factor;
    }

    if (factor == 0f) {
      baseCell.draw(c);
    } else if (factor > 0f) {
      previewCell.draw(c);
      baseCell.draw(c);
    } else {
      baseCell.draw(c);
      previewCell.draw(c);
    }*//*

    *//*if (headerFactor != 0f) {
      c.drawRect(0, 0, currentWidth, HeaderView.getSize(true), headerPaint);
    }*//*
  }*/

  // Touching

  private boolean dropTouches;
  private float startX;
  private boolean disallowIntercept;

  private float downStartX, downStartY;
  private boolean listenMove, isMoving;
  private boolean ignoreDisallowInterceptTouchEvent;

  public void setIgnoreDisallowInterceptTouchEvent (boolean ignoreDisallowInterceptTouchEvent) {
    this.ignoreDisallowInterceptTouchEvent = ignoreDisallowInterceptTouchEvent;
  }

  @Override
  public void requestDisallowInterceptTouchEvent (boolean disallowIntercept) {
    this.disallowIntercept = disallowIntercept;
    if (disallowIntercept && !ignoreDisallowInterceptTouchEvent) {
      drop();
    }
    super.requestDisallowInterceptTouchEvent(disallowIntercept);
  }

  private void updateValues (float x) {
    if (factor == 0f) {
      startX = x;
      isMoving = false;
    } else {
      startX = x + (float) currentWidth * factor * (Lang.rtl() ? -1f : 1f);
      isMoving = true;
    }
  }

  public boolean isMovingItem () {
    return isMoving;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    if ((disallowIntercept && (e.getAction() != MotionEvent.ACTION_DOWN /*|| !ignoreDisallowInterceptTouchEvent*/)) || disallowMove || disableTouch || detector == null) {
      // Logger.v("no intercept %s %b", MotionEvent.actionToString(e.getAction()), isMoving);
      return false;
    }

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        downStartX = e.getX();
        downStartY = e.getY();

        stopAnimator();
        updateValues(downStartX);

        listenMove = !isMoving && e.getPointerCount() == 1;

        if (!isMoving) {
          return detector.onTouchEvent(e);
        }

        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (listenMove && tgx101IsVideo() && !org.thunderdog.challegram.unsorted.Settings.instance().tgx101PlayerSwipePaging()) {
          listenMove = false; // TGx101: the player settings turned sideways swipes on videos off
        }
        if (listenMove && Math.abs(e.getX() - downStartX) > Screen.getTouchSlopBig() && Math.abs(e.getY() - downStartY) < Screen.getTouchSlopBig()) {
          listenMove = false;
          startX = e.getX();
          isMoving = true;
        }
        break;
      }
    }

    // Logger.v("intercept %s %b", MotionEvent.actionToString(e.getAction()), isMoving);

    return isMoving;
  }

  private void stopAnimator () {
    if (isAnimating) {
      isAnimating = false;
      animator.cancel();
    }
  }

  private void drop () {
    if (isMoving) {
      dropPreview(DIRECTION_AUTO, 0f);
    }
    isMoving = listenMove = false;
  }

  private float clickStartX, clickStartY;
  private boolean catchingClick;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (detector == null) {
      return false;
    }

    if (disableTouch) {
      if (butStillNeedClick != null) {
        switch (e.getAction()) {
          case MotionEvent.ACTION_DOWN: {
            clickStartX = e.getX();
            clickStartY = e.getY();
            catchingClick = true;
            break;
          }
          case MotionEvent.ACTION_CANCEL: {
            catchingClick = false;
            break;
          }
          case MotionEvent.ACTION_MOVE: {
            if (catchingClick && Math.max(Math.abs(e.getX() - clickStartX), Math.abs(e.getY() - clickStartY)) > Screen.getTouchSlop()) {
              catchingClick = false;
            }
            break;
          }
          case MotionEvent.ACTION_UP: {
            if (catchingClick) {
              catchingClick = false;
              playSoundEffect(SoundEffectConstants.CLICK);
              butStillNeedClick.onClick(this, e.getX(), e.getY());
            }
            break;
          }
        }
        return catchingClick;
      }

      drop();
      return false;
    }

    if (e.getPointerCount() > 1 || disallowMove) {
      drop();
      return true;
    }

    boolean res = detector.onTouchEvent(e);

    float x = e.getX();
    // float y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_MOVE: {
        if (isMoving) {
          float factor = (x - startX) / (float) currentWidth * (Lang.rtl() ? 1f : -1f);

          if ((factor > 0f && !stack.hasNext()) || (factor < 0f && !stack.hasPrevious())) {
            factor = factor * .5f;
          }

          translate(factor);
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (res) {
          isMoving = false;
          break;
        }

        if (isMoving) {
          dropPreview(DIRECTION_AUTO, 0f);
          isMoving = false;
        }

        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (isMoving) {
          dropPreview(DIRECTION_AUTO, 0f);
          isMoving = false;
        }
        break;
      }
    }

    return true;
  }

  private boolean doubleTapZoomDisabled;

  public void setDisableDoubleTapZoom (boolean disableDoubleTapZoom) {
    this.doubleTapZoomDisabled = disableDoubleTapZoom;
  }

  public boolean canDoubleTapZoom () {
    return !doubleTapZoomDisabled;
  }

  private void dropPendingTouches () {
    dropTouches = true;
  }

  // event listeners

  public int getActualImageHeight () {
    return getMeasuredHeight() - offsetTop - offsetBottom;
  }

  public int getActualImageWidth () {
    return getMeasuredWidth() - paddingHorizontal - paddingHorizontal;
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    baseCell.attach();
    if (previewCell != null) {
      previewCell.attach();
    }
  }

  // TGx101 player gestures (MagiX switch, off by default): on a playing video a horizontal swipe seeks (with a hint),
  // a vertical swipe on the left half changes the brightness, on the right half the volume; a long press plays at 2×
  // while held. A swipe that starts at the top edge belongs to the notification shade and is left alone.

  private static final int G_NONE = 0, G_PENDING = 1, G_SEEK = 2, G_BRIGHTNESS = 3, G_VOLUME = 4, G_SPEED = 5, G_ARMED = 6;
  // TGx101 gestures, variant В4 (user 2026-10-04): vertical swipes close the video anywhere; brightness / volume only
  // after a hold (0.5 s, light vibration) — left half brightness, right half volume; seek sideways in the middle,
  // paging sideways from the outer 26 %; no 2× on hold
  private int gState = G_NONE;
  private float gDownX, gDownY;
  private long gStartTime;
  private float gStartValue;
  private float gSavedSpeed = 1f;
  private String gHudTitle, gHudValue;
  private float gHudAlpha;
  private final android.graphics.Paint gHudPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  private final android.graphics.RectF gHudRect = new android.graphics.RectF();
  private static boolean gestureOn (int gesture) {
    return org.thunderdog.challegram.unsorted.Settings.instance().tgx101PlayerGesture(gesture);
  }

  private final Runnable gLongPress = () -> {
    if (gState != G_PENDING || !baseCell.tgx101CanGesture()) return;
    boolean leftHalf = gDownX < getMeasuredWidth() / 2f;
    boolean on = leftHalf ? gestureOn(org.thunderdog.challegram.unsorted.Settings.GESTURE_BRIGHTNESS) : gestureOn(org.thunderdog.challegram.unsorted.Settings.GESTURE_VOLUME);
    if (!on) return;
    gState = G_ARMED;
    org.thunderdog.challegram.Tgx101Diag.mark("player: hold → " + (leftHalf ? "brightness" : "volume") + " armed");
    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true); // no swipe-to-close now
    cancelChildren();
    showHud(leftHalf ? "☀ ↕" : "🔊 ↕", null);
    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK, android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
  };
  private final Runnable gHideHud = () -> {
    gHudAlpha = 0f;
    invalidate();
  };

  public void tgx101SetSpeed (float speed) {
    if (baseCell != null) baseCell.tgx101SetSpeed(speed);
  }

  /** TGx101: the player's ‹ › buttons — previous / next media with the usual slide */
  public void tgx101Page (boolean next) {
    if (next ? !hasNext() : !hasPrevious()) return;
    stopAnimator();
    translate(next ? .01f : -.01f);
    dropPreview(next ? DIRECTION_FORWARD : DIRECTION_BACKWARD, 0f);
  }

  /** The current item is a video (not a GIF) */
  public boolean tgx101IsVideo () {
    MediaItem media = baseCell != null ? baseCell.getMedia() : null;
    return media != null && media.isVideo() && !media.isGifType();
  }

  public void tgx101PlayPause () {
    if (baseCell != null) baseCell.tgx101PlayPause();
  }

  public void tgx101ApplyLooping () {
    if (baseCell != null) baseCell.tgx101ApplyLooping();
  }

  /** The viewer's swipe-to-close must not start here: a vertical player gesture owns this third of the screen */
  public boolean tgx101GesturesActive (float x) {
    if (true) return false; // В4: swipe-to-close works over the whole video; brightness / volume need a hold first
    if (baseCell == null || !baseCell.tgx101CanGesture()) return false;
    float width = getMeasuredWidth();
    return (x < width / 3f && gestureOn(org.thunderdog.challegram.unsorted.Settings.GESTURE_BRIGHTNESS)) || (x > width * 2f / 3f && gestureOn(org.thunderdog.challegram.unsorted.Settings.GESTURE_VOLUME));
  }

  private boolean anyGestureActive () {
    return org.thunderdog.challegram.unsorted.Settings.instance().tgx101PlayerGestures() && baseCell != null && baseCell.tgx101CanGesture();
  }

  private void cancelChildren () {
    MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
    super.dispatchTouchEvent(cancel);
    cancel.recycle();
  }

  private void showHud (String title, @Nullable String value) {
    gHudTitle = title;
    gHudValue = value;
    gHudAlpha = 1f;
    removeCallbacks(gHideHud);
    invalidate();
  }

  private static String formatTime (long ms) {
    long s = Math.max(0, ms) / 1000;
    return s >= 3600 ? String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
  }

  @Override
  public boolean dispatchTouchEvent (MotionEvent e) {
    if (handleGesture(e)) {
      return true;
    }
    return super.dispatchTouchEvent(e);
  }

  private boolean handleGesture (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        gState = G_NONE;
        if (anyGestureActive() && e.getRawY() > Screen.getStatusBarHeight() + Screen.dp(32f)) {
          gState = G_PENDING;
          gDownX = e.getX();
          gDownY = e.getY();
          postDelayed(gLongPress, 550);
        }
        return false;
      }
      case MotionEvent.ACTION_POINTER_DOWN: {
        if (gState == G_PENDING) {
          gState = G_NONE; // a pinch: zoom as usual
          removeCallbacks(gLongPress);
        }
        return gState > G_PENDING;
      }
      case MotionEvent.ACTION_MOVE: {
        if (gState == G_NONE) return false;
        float dx = e.getX() - gDownX, dy = e.getY() - gDownY;
        if (gState == G_ARMED) {
          if (Math.abs(dy) < Screen.getTouchSlop()) return true;
          if (gDownX < getMeasuredWidth() / 2f) {
            gState = G_BRIGHTNESS;
            gStartValue = currentBrightness();
          } else {
            gState = G_VOLUME;
            android.media.AudioManager audio = (android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            gStartValue = audio != null ? audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) : 0;
          }
          gDownY = e.getY();
          dy = 0;
          org.thunderdog.challegram.Tgx101Diag.mark("player: gesture " + (gState == G_BRIGHTNESS ? "brightness from " + Math.round(gStartValue * 100) + "%" : "volume from " + Math.round(gStartValue)));
        }
        if (gState == G_PENDING) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) < Screen.getTouchSlop() * 1.5f) return false;
          removeCallbacks(gLongPress);
          // zones: a sideways swipe from the outer 18 % of the width pages the media; vertical swipes on the left third
          // are brightness, on the right third volume, in the middle third they close the viewer as usual
          float width = getMeasuredWidth();
          boolean horizontal = Math.abs(dx) > Math.abs(dy), left = gDownX < width / 3f, right = gDownX > width * 2f / 3f;
          boolean fromEdge = gDownX < width * .26f || gDownX > width * .74f; // wider paging zone (В4)
          boolean pass;
          if (horizontal) {
            pass = fromEdge || !gestureOn(org.thunderdog.challegram.unsorted.Settings.GESTURE_SEEK);
          } else {
            pass = true; // В4: a vertical swipe without a hold closes the video, anywhere
          }
          if (pass) {
            gState = G_NONE; // not a player gesture here: the swipe works as usual (paging / closing)
            org.thunderdog.challegram.Tgx101Diag.mark("player: swipe " + (horizontal ? (fromEdge ? "from the edge" : "sideways") : left ? "left vertical" : right ? "right vertical" : "middle vertical") + " — passed on");
            return false;
          }
          if (horizontal) {
            gState = G_SEEK;
            gStartTime = baseCell.tgx101TimeNow();
          } else if (left) {
            gState = G_BRIGHTNESS;
            gStartValue = currentBrightness();
          } else {
            gState = G_VOLUME;
            android.media.AudioManager audio = (android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            gStartValue = audio != null ? audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) : 0;
          }
          cancelChildren();
          org.thunderdog.challegram.Tgx101Diag.mark("player: gesture " + (gState == G_SEEK ? "seek from " + gStartTime / 1000 + " s" : gState == G_BRIGHTNESS ? "brightness from " + Math.round(gStartValue * 100) + "%" : "volume from " + Math.round(gStartValue)));
        }
        updateGesture(dx, dy, false);
        return true;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        removeCallbacks(gLongPress);
        int state = gState;
        gState = G_NONE;
        if (state == G_ARMED || state == G_BRIGHTNESS || state == G_VOLUME) {
          // the viewer keeps its own copy of the flag; without this, swipe-to-close stayed off after a hold
          if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }
        if (state == G_PENDING || state == G_NONE) return false;
        if (state == G_ARMED) {
          postDelayed(gHideHud, 300); // held and let go without moving: nothing else happens
          return true;
        }
        if (state == G_SEEK && e.getActionMasked() == MotionEvent.ACTION_UP) {
          gState = G_SEEK;
          updateGesture(e.getX() - gDownX, e.getY() - gDownY, true);
          gState = G_NONE;
        } else if (state == G_SPEED) {
          baseCell.tgx101SetSpeed(gSavedSpeed);
        }
        org.thunderdog.challegram.Tgx101Diag.mark("player: gesture end " + (state == G_SEEK ? "seek → " + gHudValue : state == G_SPEED ? "2× released, back to " + gSavedSpeed + "×" : (state == G_BRIGHTNESS ? "brightness " : "volume ") + gHudValue) + (e.getActionMasked() == MotionEvent.ACTION_CANCEL ? " (cancelled)" : ""));
        postDelayed(gHideHud, 600);
        return true;
      }
    }
    return gState > G_PENDING;
  }

  private void updateGesture (float dx, float dy, boolean commit) {
    switch (gState) {
      case G_SEEK: {
        long total = baseCell.tgx101TimeTotal();
        // the whole width is 90 seconds (or the whole video if it is shorter)
        long span = Math.min(total, 90000);
        long delta = (long) (dx / Math.max(1, getMeasuredWidth()) * span);
        long target = Math.max(0, Math.min(total, gStartTime + delta));
        long shown = target - gStartTime;
        showHud((shown >= 0 ? "+" : "−") + formatTime(Math.abs(shown)), formatTime(target) + " / " + formatTime(total));
        if (commit) {
          baseCell.tgx101SeekTo(target);
        }
        break;
      }
      case G_BRIGHTNESS: {
        float value = Math.max(0.01f, Math.min(1f, gStartValue - dy / (getMeasuredHeight() * .7f)));
        if (getContext() instanceof android.app.Activity) {
          android.view.Window window = ((android.app.Activity) getContext()).getWindow();
          android.view.WindowManager.LayoutParams params = window.getAttributes();
          params.screenBrightness = value;
          window.setAttributes(params);
          tgx101BrightnessChanged = true;
        }
        showHud(Lang.getString(org.thunderdog.challegram.R.string.Tgx101PlayerBrightness), Math.round(value * 100) + "%");
        break;
      }
      case G_VOLUME: {
        android.media.AudioManager audio = (android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) break;
        int max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        int value = Math.max(0, Math.min(max, Math.round(gStartValue - dy / (getMeasuredHeight() * .7f) * max)));
        if (value != audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) {
          audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, value, 0);
        }
        showHud(Lang.getString(org.thunderdog.challegram.R.string.Tgx101PlayerVolume), Math.round(value * 100f / Math.max(1, max)) + "%");
        break;
      }
    }
  }

  private boolean tgx101BrightnessChanged;

  private float currentBrightness () {
    if (getContext() instanceof android.app.Activity) {
      float value = ((android.app.Activity) getContext()).getWindow().getAttributes().screenBrightness;
      if (value >= 0) return value;
    }
    try {
      return android.provider.Settings.System.getInt(getContext().getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f;
    } catch (Throwable t) {
      return .5f;
    }
  }

  @Override
  protected void dispatchDraw (android.graphics.Canvas c) {
    super.dispatchDraw(c);
    if (gHudAlpha > 0f && gHudTitle != null) {
      float cx = getMeasuredWidth() / 2f, cy = getMeasuredHeight() * .42f;
      gHudPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
      gHudPaint.setTextSize(Screen.dp(26f));
      gHudPaint.setFakeBoldText(true);
      float titleWidth = gHudPaint.measureText(gHudTitle);
      float valueWidth = 0;
      if (gHudValue != null) {
        gHudPaint.setTextSize(Screen.dp(13f));
        valueWidth = gHudPaint.measureText(gHudValue);
      }
      float w = Math.max(titleWidth, valueWidth) + Screen.dp(36f), h = Screen.dp(gHudValue != null ? 70f : 50f);
      gHudRect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
      gHudPaint.setColor(0x73000000);
      c.drawRoundRect(gHudRect, Screen.dp(16f), Screen.dp(16f), gHudPaint);
      gHudPaint.setColor(0xffffffff);
      gHudPaint.setTextSize(Screen.dp(26f));
      c.drawText(gHudTitle, cx, gHudRect.top + Screen.dp(gHudValue != null ? 34f : 34f), gHudPaint);
      if (gHudValue != null) {
        gHudPaint.setFakeBoldText(false);
        gHudPaint.setTextSize(Screen.dp(13f));
        gHudPaint.setColor(0xccffffff);
        c.drawText(gHudValue, cx, gHudRect.top + Screen.dp(56f), gHudPaint);
      }
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if (tgx101BrightnessChanged && getContext() instanceof android.app.Activity) {
      // the player's brightness only lasts while the viewer is open
      android.view.Window window = ((android.app.Activity) getContext()).getWindow();
      android.view.WindowManager.LayoutParams params = window.getAttributes();
      params.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
      window.setAttributes(params);
      tgx101BrightnessChanged = false;
    }
    baseCell.detach();
    if (previewCell != null) {
      previewCell.detach();
    }
  }

  public Receiver getBaseReceiver () {
    return baseCell.getReceiver();
  }

  // Stack utils

  public boolean hasNext () {
    return !inSingleMode() && stack.hasNext();
  }

  public boolean hasPrevious () {
    return !inSingleMode() && stack.hasPrevious();
  }

  public boolean inSingleMode () {
    return disallowMove;
  }
}
