package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Views;

public class ClippingSubsamplingImageView extends SubsamplingScaleImageView {
  private float imageWidth, imageHeight;
  private final RectF clip = new RectF();
  private float topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius;

  public ClippingSubsamplingImageView (Context context) {
    super(context);
  }

  public void setClipping (float imageWidth, float imageHeight,
                           float clipLeft, float clipTop, float clipRight, float clipBottom,
                           float topLeftRadius, float topRightRadius,
                           float bottomRightRadius, float bottomLeftRadius) {
    if (this.imageWidth != imageWidth || this.imageHeight != imageHeight ||
      U.setRect(clip, clipLeft, clipTop, clipRight, clipBottom) ||
      this.topLeftRadius != topLeftRadius || this.topRightRadius != topRightRadius ||
      this.bottomRightRadius != bottomRightRadius || this.bottomLeftRadius != bottomLeftRadius) {
      this.imageWidth = imageWidth;
      this.imageHeight = imageHeight;
      this.topLeftRadius = topLeftRadius;
      this.topRightRadius = topRightRadius;
      this.bottomRightRadius = bottomRightRadius;
      this.bottomLeftRadius = bottomLeftRadius;
      invalidate();
    }
  }

  public void resetClipping () {
    setClipping(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  private final Path path = new Path();

  // TGx101 (user 2026-11-16 «приближённое медиа идёт лесенкой», journal 15:56: while the finger moves the picture goes
  // evenly, 29 33 28 25 px a frame; once let go the library's own glide went 33 20 36 39 34 18 29 36 21 51 10 — it counts
  // its time by the wall clock at draw, not by the frame's time, so a frame drawn a bit late or early takes half or double
  // a step). The library's glide is turned off at the lift and the same one — the finger's speed × 0.25 s, ease-out over
  // 500 ms, as the library's — is played here by the frames' own times
  private android.view.VelocityTracker tgx101Velocity;
  private float tgx101DownX, tgx101DownY;
  private boolean tgx101OneFinger;
  private float tgx101GlideScale, tgx101GlideDx, tgx101GlideDy;
  private android.graphics.PointF tgx101GlideFrom;
  private long tgx101GlideStart;
  private boolean tgx101Gliding;
  private final android.view.Choreographer.FrameCallback tgx101GlideFrame = this::tgx101GlideStep;

  public interface Tgx101LibraryTouch {
    boolean onTouch (android.view.MotionEvent e);
  }

  /** Feeds the library the event; at a lift that would fling, the library's glide is skipped and this one plays instead. */
  public boolean tgx101TouchWithGlide (android.view.MotionEvent e, Tgx101LibraryTouch library) {
    int action = e.getActionMasked();
    if (action == android.view.MotionEvent.ACTION_DOWN) {
      tgx101StopGlide();
      if (tgx101Velocity != null) tgx101Velocity.clear();
      tgx101DownX = e.getX();
      tgx101DownY = e.getY();
      tgx101OneFinger = true;
    }
    if (e.getPointerCount() > 1) tgx101OneFinger = false;
    if (tgx101Velocity == null) tgx101Velocity = android.view.VelocityTracker.obtain();
    tgx101Velocity.addMovement(e);
    if (action != android.view.MotionEvent.ACTION_UP) {
      return library.onTouch(e);
    }
    android.view.ViewConfiguration vc = android.view.ViewConfiguration.get(getContext());
    tgx101Velocity.computeCurrentVelocity(1000, vc.getScaledMaximumFlingVelocity());
    float vx = tgx101Velocity.getXVelocity(), vy = tgx101Velocity.getYVelocity();
    tgx101Velocity.clear();
    boolean glide = tgx101OneFinger && isReady() && isPanEnabled() && getCenter() != null
      && (Math.abs(e.getX() - tgx101DownX) > 50 || Math.abs(e.getY() - tgx101DownY) > 50)
      && (Math.abs(vx) > 500 || Math.abs(vy) > 500)
      && (Math.abs(vx) > vc.getScaledMinimumFlingVelocity() || Math.abs(vy) > vc.getScaledMinimumFlingVelocity());
    if (!glide) {
      return library.onTouch(e);
    }
    setPanEnabled(false); // the library checks it in its fling: no glide of its own
    boolean res;
    try {
      res = library.onTouch(e);
    } finally {
      setPanEnabled(true);
    }
    tgx101GlideScale = getScale();
    tgx101GlideFrom = new android.graphics.PointF(getCenter().x, getCenter().y);
    tgx101GlideDx = vx * .25f;
    tgx101GlideDy = vy * .25f;
    tgx101GlideStart = 0;
    tgx101Gliding = true;
    android.view.Choreographer.getInstance().postFrameCallback(tgx101GlideFrame);
    return res;
  }

  private void tgx101StopGlide () {
    if (tgx101Gliding) {
      tgx101Gliding = false;
      android.view.Choreographer.getInstance().removeFrameCallback(tgx101GlideFrame);
    }
  }

  private void tgx101GlideStep (long frameTimeNanos) {
    if (!tgx101Gliding) return;
    if (!isReady() || getWindowToken() == null || Math.abs(getScale() - tgx101GlideScale) > .001f) {
      tgx101Gliding = false;
      return;
    }
    long frameMs = frameTimeNanos / 1000000L;
    if (tgx101GlideStart == 0) {
      float hz = org.thunderdog.challegram.tool.Screen.refreshRate();
      tgx101GlideStart = frameMs - Math.round(1000f / (hz > 1f ? hz : 60f)); // the first frame already moves
    }
    float t = Math.min(1f, (frameMs - tgx101GlideStart) / 500f);
    float f = t * (2f - t); // ease out, as the library's
    setScaleAndCenter(tgx101GlideScale, new android.graphics.PointF(tgx101GlideFrom.x - tgx101GlideDx * f / tgx101GlideScale, tgx101GlideFrom.y - tgx101GlideDy * f / tgx101GlideScale));
    if (t < 1f) {
      android.view.Choreographer.getInstance().postFrameCallback(tgx101GlideFrame);
    } else {
      tgx101Gliding = false;
    }
  }

  // TGx101 (user 2026-11-16 «приближённое медиа идёт лесенкой»: on the recording a zoomed photo moved 12, 36, 48 px in
  // neighbouring frames while the finger went evenly) — the big photos are this view's, panned by the library itself:
  // per drawn frame the finger's way since the last frame and in how many touches, and the picture's step; once the
  // finger is lifted, its glide on (the library's own fling) up to 1.5 s more
  private final StringBuilder tgx101Pan = new StringBuilder();
  private float tgx101FingerX, tgx101FingerY, tgx101FrameFinger, tgx101LastImageX = Float.NaN, tgx101LastImageY = Float.NaN;
  private int tgx101FrameTouches, tgx101PanFrames, tgx101PanNoTouch;
  private long tgx101PanStart, tgx101UpAt, tgx101LastDraw;
  private boolean tgx101FingerDown;

  public void tgx101OnTouch (android.view.MotionEvent e) {
    if (!org.thunderdog.challegram.BuildConfig.TGX101_DIAG) return;
    int action = e.getActionMasked();
    if (action == android.view.MotionEvent.ACTION_DOWN) {
      tgx101Report();
      tgx101PanStart = android.os.SystemClock.uptimeMillis();
      tgx101Pan.setLength(0);
      tgx101PanFrames = tgx101PanNoTouch = tgx101FrameTouches = 0;
      tgx101FrameFinger = 0;
      tgx101UpAt = 0;
      tgx101FingerDown = true;
      tgx101FingerX = e.getX();
      tgx101FingerY = e.getY();
      tgx101LastImageX = tgx101LastImageY = Float.NaN;
    } else if (tgx101PanStart != 0 && tgx101FingerDown) {
      if (action == android.view.MotionEvent.ACTION_MOVE && e.getPointerCount() == 1) {
        float dx = e.getX() - tgx101FingerX, dy = e.getY() - tgx101FingerY;
        tgx101FrameFinger += Math.abs(dx) >= Math.abs(dy) ? dx : dy;
        tgx101FrameTouches += 1 + e.getHistorySize();
      }
      tgx101FingerX = e.getX();
      tgx101FingerY = e.getY();
      if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
        tgx101FingerDown = false;
        tgx101UpAt = android.os.SystemClock.uptimeMillis();
        if (tgx101Pan.length() < 4000) tgx101Pan.append(" | up");
        postDelayed(this::tgx101Report, 1500);
      }
    }
  }

  private void tgx101OnFrame () {
    if (tgx101PanStart == 0) return;
    android.graphics.PointF at = sourceToViewCoord(0f, 0f);
    if (at == null) return;
    long now = android.os.SystemClock.uptimeMillis();
    float stepX = Float.isNaN(tgx101LastImageX) ? 0 : at.x - tgx101LastImageX, stepY = Float.isNaN(tgx101LastImageY) ? 0 : at.y - tgx101LastImageY;
    tgx101LastImageX = at.x;
    tgx101LastImageY = at.y;
    tgx101PanFrames++;
    if (tgx101FingerDown && tgx101FrameTouches == 0) tgx101PanNoTouch++;
    if (tgx101Pan.length() < 4000) {
      float step = Math.abs(stepX) >= Math.abs(stepY) ? stepX : stepY;
      tgx101Pan.append(' ');
      if (tgx101FingerDown) tgx101Pan.append(Math.round(tgx101FrameFinger)).append('/').append(tgx101FrameTouches).append('>');
      tgx101Pan.append(Math.round(step));
      if (tgx101LastDraw != 0 && now - tgx101LastDraw > 12) tgx101Pan.append('(').append(now - tgx101LastDraw).append("ms)");
    }
    tgx101LastDraw = now;
    tgx101FrameFinger = 0;
    tgx101FrameTouches = 0;
  }

  private void tgx101Report () {
    if (tgx101PanStart == 0 || tgx101PanFrames < 3) {
      tgx101PanStart = 0;
      return;
    }
    org.thunderdog.challegram.Tgx101Diag.mark("big photo pan: " + (android.os.SystemClock.uptimeMillis() - tgx101PanStart) + " ms, scale " + Math.round(getScale() * 100) / 100f
      + ", image " + getSWidth() + "×" + getSHeight() + ", frames " + tgx101PanFrames + " (finger on, no touch: " + tgx101PanNoTouch + ")"
      + " | per frame: finger px/touches>picture px, (gap) when over 12 ms:" + tgx101Pan);
    tgx101PanStart = 0;
    tgx101Pan.setLength(0);
    tgx101LastDraw = 0;
  }

  @Override
  public void onDraw (Canvas canvas) {
    if (org.thunderdog.challegram.BuildConfig.TGX101_DIAG) tgx101OnFrame();
    if (imageWidth == 0 || imageHeight == 0 || (clip.left == 0 && clip.top == 0 && clip.right == 0 && clip.bottom == 0 && topLeftRadius == 0 && topRightRadius == 0 && bottomRightRadius == 0 && bottomLeftRadius == 0)) {
      super.onDraw(canvas);
      return;
    }
    int paddingLeft = getPaddingLeft();
    int paddingTop = getPaddingTop();
    int paddingRight = getPaddingRight();
    int paddingBottom = getPaddingBottom();
    float centerX = paddingLeft + (getMeasuredWidth() - paddingLeft - paddingRight) / 2f;
    float centerY = paddingTop + (getMeasuredHeight() - paddingTop - paddingBottom) / 2f;
    int saveCount = Views.save(canvas);
    RectF rectF = Paints.getRectF();
    rectF.set(
      centerX - imageWidth / 2f + clip.left,
      centerY - imageHeight / 2f + clip.top,
      centerX + imageWidth / 2f - clip.right,
      centerY + imageHeight / 2f - clip.bottom
    );
    canvas.clipRect(rectF);
    if (topLeftRadius != 0 || topRightRadius != 0 || bottomRightRadius != 0 || bottomLeftRadius != 0) {
      int imageWidth = getSWidth();
      int imageHeight = getSHeight();
      if (U.isRotated(getOrientation())) {
        int temp = imageHeight;
        imageHeight = imageWidth;
        imageWidth = temp;
      }
      float scale = Math.min(
        (float) getMeasuredWidth() / (float) imageWidth,
        (float) getMeasuredHeight() / (float) imageHeight
      );
      imageWidth *= scale;
      imageHeight *= scale;
      if (imageWidth > 0 && imageHeight > 0) {
        rectF.left = Math.max(rectF.left, centerX - imageWidth / 2f);
        rectF.top = Math.max(rectF.top, centerY - imageHeight / 2f);
        rectF.right = Math.min(rectF.right, centerX + imageWidth / 2f);
        rectF.bottom = Math.min(rectF.bottom, centerY + imageHeight / 2f);
      }

      path.reset();
      DrawAlgorithms.buildPath(path, rectF, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
      ViewSupport.clipPath(canvas, path);
    }
    super.onDraw(canvas);

    Views.restore(canvas, saveCount);
  }
}
