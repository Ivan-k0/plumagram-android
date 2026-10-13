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
 * File created on 11/12/2016
 */
package org.thunderdog.challegram.mediaview.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.mediaview.MediaCellView;
import org.thunderdog.challegram.mediaview.crop.CropState;
import org.thunderdog.challegram.mediaview.data.FiltersState;
import org.thunderdog.challegram.mediaview.paint.PaintMode;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.mediaview.paint.SimpleDrawing;
import org.thunderdog.challegram.mediaview.paint.widget.SimpleDrawingView;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

public class EGLEditorView extends ViewGroup {
  private SimpleDrawingView paintView;
  private @Nullable View textureView;
  private @Nullable EGLEditorContext editorContext;
  private @Nullable Bitmap sourceBitmap;
  private FiltersState currentFiltersState;
  private PaintState currentPaintState;
  private ImageGalleryFile currentFile;

  private int croppedWidth, croppedHeight;
  private int centerX, centerY;
  private int normalWidth, normalHeight;

  private class OverlayView extends View {
    public OverlayView (Context context) {
      super(context);
    }

    @Override
    protected void onDraw (Canvas c) {
      int width = croppedWidth;
      int height = croppedHeight;
      int left = centerX - width / 2;
      int right = left + width;
      int top = centerY -  height / 2;
      int bottom = top + height;

      int viewWidth = getMeasuredWidth();
      int viewHeight = getMeasuredHeight();

      Paint paint = Paints.fillingPaint(0xff000000);

      if (left > 0) {
        c.drawRect(0, top, left, bottom, paint);
      }
      if (right < viewWidth) {
        c.drawRect(right, top, viewWidth, bottom, paint);
      }
      if (top > 0) {
        c.drawRect(0, 0, viewWidth, top, paint);
      }
      if (bottom < viewHeight) {
        c.drawRect(0, bottom, viewWidth, viewHeight, paint);
      }
    }
  }

  private FrameLayoutFix textureWrap;
  private OverlayView overlayView;

  private ContentLayout contentWrap;

  public EGLEditorView (Context context) {
    super(context);
    textureWrap = new FrameLayoutFix(context);
    textureWrap.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(textureWrap);

    contentWrap = new ContentLayout(context);
    contentWrap.setParent(this);
    contentWrap.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    contentWrap.setVisibility(View.INVISIBLE);
    textureWrap.addView(contentWrap);

    overlayView = new OverlayView(context);
    overlayView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(overlayView);
  }

  private static final int TYPE_NONE = 0;
  private static final int TYPE_FILTERS = 1;
  private static final int TYPE_PAINT = 2;

  private int initedType;

  public void init (ImageGalleryFile imageFile, Bitmap bitmap, @Nullable FiltersState filtersState, @Nullable PaintState paintState) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      this.currentFile = imageFile;
      this.sourceBitmap = bitmap;

      final int initType;

      if (filtersState != null) {
        this.currentFiltersState = filtersState;
        initType = TYPE_FILTERS;
      } else if (paintState != null) {
        this.currentPaintState = paintState;
        initType = TYPE_PAINT;
      } else {
        throw new IllegalArgumentException("filtersState == null && paintState == null");
      }


      android.view.TextureView textureView = null;

      switch (initType) {
        case TYPE_FILTERS: {
          textureView = new android.view.TextureView(getContext());
          textureView.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
          textureView.setVisibility(textureVisible ? View.VISIBLE : View.INVISIBLE);
          textureView.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable (SurfaceTexture surface, int width, int height) {
              if (surface != null && editorContext == null && sourceBitmap != null && !sourceBitmap.isRecycled() && currentFiltersState != null) {
                editorContext = new EGLEditorContext(surface, sourceBitmap, currentFiltersState, width, height);
                editorContext.requestRender(true, true);
              }
            }

            @Override
            public void onSurfaceTextureSizeChanged (SurfaceTexture surface, int width, int height) {
              if (editorContext != null) {
                editorContext.setSurfaceTextureSize(width, height);
                editorContext.requestRender(false, true);
                UI.post(() -> editorContext.requestRender(false, true));
              }
            }

            @Override
            public boolean onSurfaceTextureDestroyed (SurfaceTexture surface) {
              destroy();
              return true;
            }

            @Override
            public void onSurfaceTextureUpdated (SurfaceTexture surface) { }
          });
          textureView.setScaleX(-1);
          contentWrap.addView(textureView);
          break;
        }
        case TYPE_PAINT: {
          contentWrap.setPaintingGesturesEnabled(true);
          break;
        }
      }
      paintView = new SimpleDrawingView(getContext());
      if (sourceBitmap != null) paintView.setMosaicSource(sourceBitmap); // TGx101: mosaic strokes take their squares from the photo
      paintView.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
      contentWrap.addView(paintView);
      contentWrap.setPaintingState(paintState);
      this.textureView = textureView;
      applyCurrentStyles();
      initedType = initType;
    }
  }

  private int appliedRotation;
  private CropState sourceCropState;

  private void applyCurrentStyles () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      if (appliedRotation != sourceRotation) {
        boolean wasRotated = U.isRotated(appliedRotation);
        appliedRotation = sourceRotation;
        boolean nowRotated = U.isRotated(sourceRotation);
        if (wasRotated != nowRotated) {
          contentWrap.requestLayout();
        }
      }
      if (sourceCropState != null && !sourceCropState.isEmpty()) {
        float degrees = sourceCropState.getDegreesAroundCenter();
        contentWrap.setRotation(degrees);

        double rad = Math.toRadians(degrees);
        float sin = (float) Math.abs(Math.sin(rad));
        float cos = (float) Math.abs(Math.cos(rad));

        float w = sourceWidth;
        float h = sourceHeight;

        // W = w·|cos φ| + h·|sin φ|
        // H = w·|sin φ| + h·|cos φ|

        float W = w * cos + h * sin;
        float H = w * sin + h * cos;

        float scale = Math.max(W / w, H / h);
        contentWrap.setScaleX(scale);
        contentWrap.setScaleY(scale);
        if (textureView != null) {
          textureView.setScaleX(sourceCropState.needMirrorHorizontally() ? -1 : 1);
          textureView.setScaleY(sourceCropState.needMirrorVertically() ? -1 : 1);
        }
      } else {
        contentWrap.setRotation(0);
        contentWrap.setScaleX(1f);
        contentWrap.setScaleY(1f);
        contentWrap.setTranslationX(0f);
        contentWrap.setTranslationY(0f);
        if (textureView != null) {
          textureView.setScaleX(1);
          textureView.setScaleY(1);
        }
      }
      textureWrap.setRotation(sourceRotation);
    }
  }

  public void setViewSizes (int width, int height, int croppedWidth, int croppedHeight) {
    this.normalWidth = width;
    this.normalHeight = height;
    this.croppedWidth = croppedWidth;
    this.croppedHeight = croppedHeight;
    overlayView.invalidate();
  }

  public void setCenter (int centerX, int centerY) {
    this.centerX = centerX;
    this.centerY = centerY;
    overlayView.invalidate();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);

    if (getChildCount() == 0) {
      return;
    }

    int textureWidth, textureHeight;
    textureWidth = normalWidth;
    textureHeight = normalHeight;

    if (U.isRotated(sourceRotation)) {
      textureWrap.measure(MeasureSpec.makeMeasureSpec(textureHeight, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(textureWidth, MeasureSpec.EXACTLY));
    } else {
      textureWrap.measure(MeasureSpec.makeMeasureSpec(textureWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(textureHeight, MeasureSpec.EXACTLY));
    }

    float scale = 1f;
    float translateX = 0;
    float translateY = 0;
    if (sourceCropState != null) {
      double left = sourceCropState.getLeft();
      double top = sourceCropState.getTop();
      double right = sourceCropState.getRight();
      double bottom = sourceCropState.getBottom();

      double width = (right - left);
      double height = (bottom - top);

      scale = Math.max((float) croppedWidth / (float) (textureWidth * width), (float) croppedHeight / (float) (textureHeight * height));

      double cx = ((left + right) / 2 - 0.5);
      double cy = ((top + bottom) / 2 - 0.5);

      textureWidth *= scale;
      textureHeight *= scale;

      float textureCx = (float) (cx * (float) textureWidth);
      float textureCy = (float) (cy * (float) textureHeight);

      translateX = -textureCx;
      translateY = -textureCy;
    }
    textureWrap.setScaleX(scale);
    textureWrap.setScaleY(scale);
    textureWrap.setTranslationX(translateX);
    textureWrap.setTranslationY(translateY);

    overlayView.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    final int childCount = getChildCount();

    /*final int parentWidth = getMeasuredWidth();
    final int parentHeight = getMeasuredHeight();*/

    for (int i = 0; i < childCount; i++) {
      View view = getChildAt(i);
      if (view == null) {
        continue;
      }

      if (view == overlayView) {
        view.layout(l, t, r, b);
      } else {
        int viewWidth = view.getMeasuredWidth();
        int viewHeight = view.getMeasuredHeight();

        /*if (Utils.isRotated(sourceRotation)) {
          int temp = viewWidth;
          viewWidth = viewHeight;
          viewHeight = temp;
        }*/

        int left, top, right, bottom;

        left = centerX - viewWidth / 2;
        top = centerY - viewHeight / 2;

        right = left + viewWidth;
        bottom = top + viewHeight;

        view.layout(left, top, right, bottom);
      }
    }
  }

  public void requestRenderByChange (boolean isBlur) {
    if (editorContext != null) {
      editorContext.requestRender(isBlur);
    }
  }

  public void destroy () {
    if (editorContext != null) {
      editorContext.destroy();
      editorContext = null;
    }
  }

  public void pause () {
    if (editorContext != null) {
      editorContext.pause();
    }
  }

  private boolean textureVisible;

  public void setEditorVisible (boolean isVisible) {
    this.textureVisible = isVisible;
    if (textureView != null) {
      textureView.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    }
    contentWrap.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    overlayView.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
  }

  public boolean isEditorVisible () {
    return textureVisible;
  }

  private int sourceWidth, sourceHeight, sourceRotation;

  /** TGx101: the photo under the drawings for mosaic strokes */
  public void setMosaicSource (@Nullable Bitmap bitmap) {
    if (paintView != null) paintView.setMosaicSource(bitmap);
  }

  public void reset (ImageGalleryFile imageFile, int sourceWidth, int sourceHeight, @Nullable Bitmap bitmap, @Nullable FiltersState filtersState, @Nullable PaintState paintState) {
    this.sourceWidth = sourceWidth;
    this.sourceHeight = sourceHeight;
    this.sourceRotation = imageFile.getVisualRotationWithCropRotation();
    this.sourceCropState = imageFile.getCropState();
    if (initedType == TYPE_NONE) {
      init(imageFile, bitmap, filtersState, paintState);
      return;
    }
    this.sourceBitmap = bitmap;
    if (paintView != null && bitmap != null) paintView.setMosaicSource(bitmap);
    this.currentFiltersState = filtersState;
    this.currentPaintState = paintState;
    this.currentFile = imageFile;
    applyCurrentStyles();
    switch (initedType) {
      case TYPE_FILTERS:
        if (editorContext != null) {
          editorContext.resumeWithData(bitmap, filtersState);
        }
        break;
    }
    contentWrap.setPaintingState(paintState);
  }

  public void setSizes (int width, int height, int rotation, CropState cropState) {
    this.sourceWidth = width;
    this.sourceCropState = cropState;
    this.sourceHeight = height;
    this.sourceRotation = rotation;
  }

  private boolean isReady () {
    switch (initedType) {
      case TYPE_FILTERS:
        return editorContext != null;
      case TYPE_PAINT:
        return false; // TODO
    }
    return false;
  }

  public void getBitmapAsync (BitmapCallback callback) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && isReady()) {
      switch (initedType) {
        case TYPE_FILTERS:
          if (editorContext != null) {
            editorContext.getBitmap(callback);
          } else {
            callback.onBitmapObtained(null);
          }
          break;
      }
    } else {
      callback.onBitmapObtained(null);
    }
  }

  private int paintingMode;

  public void setPaintingMode (int mode) {
    if (this.paintingMode != mode) {
      this.paintingMode = mode;
      contentWrap.setPaintingGesturesEnabled(mode != PaintMode.FREE_MOVEMENT);
      contentWrap.setPaintingMode(mode);
    }
  }

  // ContentLayout

  public ContentLayout getContentWrap () {
    return contentWrap;
  }

  public static class ContentLayout extends FrameLayoutFix implements SimpleDrawing.ChangeListener, Runnable {
    private EGLEditorView parent;

    public ContentLayout (@NonNull Context context) {
      super(context);
    }

    public void setParent (EGLEditorView parent) {
      this.parent = parent;
    }

    private boolean paintingEnabled;

    public void setPaintingGesturesEnabled (boolean isEnabled) {
      this.paintingEnabled = isEnabled;
    }

    private PaintState state;
    private int paintingMode;

    public void setPaintingMode (int mode) {
      this.paintingMode = mode;
    }

    public void setPaintingState (PaintState state) {
      this.state = state;
      parent.paintView.setPaintState(state);
    }

    private int effectiveMode = PaintMode.NONE;
    private boolean needZoomCancel;

    public void cancelDrawingByZoom () {
      needZoomCancel = true;
    }

    public interface DrawingListener {
      void onDrawingStateChanged (View editorView, int paintMode);
    }

    private DrawingListener listener;

    public void setDrawingListener (DrawingListener listener) {
      this.listener = listener;
    }

    public boolean hasEffectiveDrawing () {
      return effectiveMode != PaintMode.NONE;
    }

    private void setEffectiveMode (int mode) {
      if (this.effectiveMode != mode) {
        this.effectiveMode = mode;
        if (this.listener != null) {
          listener.onDrawingStateChanged(this, mode);
        }
      }
    }

    @SuppressWarnings("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (MotionEvent e) {
      int action = e.getAction();

      if (state == null) {
        return false;
      }

      if (action == MotionEvent.ACTION_DOWN) {
        needZoomCancel = false;
      }

      if (tgx101HandleShapeTouch(e)) {
        return true;
      }

      if (action != MotionEvent.ACTION_DOWN && effectiveMode == PaintMode.NONE) {
        return false;
      }

      switch (action) {
        case MotionEvent.ACTION_DOWN: {
          if (!paintingEnabled || paintingMode == PaintMode.FREE_MOVEMENT || e.getPointerCount() > 1 || !beginDrawing(paintingMode, e, getMeasuredWidth(), getMeasuredHeight())) {
            setEffectiveMode(PaintMode.NONE);
            return false;
          }
          setEffectiveMode(paintingMode);
          break;
        }
        case MotionEvent.ACTION_MOVE: {
          if (!moveDrawing(effectiveMode, e, ((MediaCellView) parent.getParent()).getZoomFactor() == 1f)) {
            setEffectiveMode(PaintMode.NONE);
          }
          break;
        }
        case MotionEvent.ACTION_UP: {
          completeDrawing(effectiveMode, e, false);
          setEffectiveMode(PaintMode.NONE);
          break;
        }
        case MotionEvent.ACTION_CANCEL: {
          completeDrawing(effectiveMode, e, needZoomCancel);
          setEffectiveMode(PaintMode.NONE);
          needZoomCancel = false;
          break;
        }
      }

      return true;
    }

    private SimpleDrawing currentSimpleDrawing;

    private int brushColor;
    private float brushRadius;

    public void setBrushParameters (int brushColor, float brushRadius) {
      if (this.brushColor != brushColor || this.brushRadius != brushRadius) {
        this.brushColor = brushColor;
        this.brushRadius = brushRadius;
        if (currentSimpleDrawing != null) {
          currentSimpleDrawing.setBrushParameters(currentSimpleDrawing.isMosaic() ? PaintMode.mosaicStrength : brushColor, brushRadius);
        }
      }
    }

    private boolean beginDrawing (int mode, MotionEvent e, int canvasWidth, int canvasHeight) {
      int type;
      switch (mode) {
        case PaintMode.ARROW:
          type = SimpleDrawing.TYPE_ARROW;
          break;
        case PaintMode.RECTANGLE:
          type = SimpleDrawing.TYPE_RECTANGLE;
          break;
        case PaintMode.ELLIPSE:
          type = SimpleDrawing.TYPE_ELLIPSE;
          break;
        case PaintMode.LINE:
          type = SimpleDrawing.TYPE_LINE;
          break;
        case PaintMode.PATH:
          type = SimpleDrawing.TYPE_PATH;
          break;
        default:
          return false;
      }
      deselectShape();
      SimpleDrawing drawing = new SimpleDrawing(type, canvasWidth, canvasHeight, 1f, parent.sourceCropState != null ? parent.sourceCropState.getDegreesAroundCenter() : 0f);
      if (PaintMode.shapeFill) drawing.setFilled(true); // rectangles and ovals only
      if (type == SimpleDrawing.TYPE_PATH) drawing.setStyle(PaintMode.brushStyle); // TGx101: pen / marker / pencil / neon / arrow / eraser
      drawing.startDrawing(e);
      // a pixel blur keeps its strength where a colour would be
      drawing.setBrushParameters(drawing.isMosaic() ? PaintMode.mosaicStrength : brushColor, brushRadius);
      state.addSimpleDrawing(drawing);
      drawing.addChangeListener(this);
      currentSimpleDrawing = drawing;
      return true;
    }

    private boolean moveDrawing (int mode, MotionEvent e, boolean allowHistory) {
      switch (mode) {
        case PaintMode.ARROW:
        case PaintMode.RECTANGLE:
        case PaintMode.ELLIPSE:
        case PaintMode.LINE:
        case PaintMode.PATH: {
          currentSimpleDrawing.moveDrawing(e, allowHistory);
          return true;
        }
      }
      throw new UnsupportedOperationException("mode == " + mode);
    }

    private void completeDrawing (int mode, MotionEvent e, boolean byPinch) {
      switch (mode) {
        case PaintMode.ARROW:
        case PaintMode.RECTANGLE:
        case PaintMode.ELLIPSE:
        case PaintMode.LINE:
        case PaintMode.PATH: {
          if (currentSimpleDrawing.completeDrawing(e, byPinch)) {
            state.trackSimpleDrawingAction(currentSimpleDrawing);
            tgx101SelectIfShape(currentSimpleDrawing);
          } else {
            state.removeSimpleDrawing(currentSimpleDrawing);
          }
          currentSimpleDrawing.removeChangeListener(this);
          currentSimpleDrawing = null;
          break;
        }
      }
    }

    public boolean isBusy () {
      return effectiveMode != PaintMode.NONE;
    }

    // TGx101 (user 2026-10-13, editor mockup: «появляется фигура с белыми точками»): the shape just drawn stays picked —
    // white dots on its corners (resize), a dot over it to turn it (rectangle, oval), dots on the ends of a line or an
    // arrow; drag inside to move it. Touching anywhere else lets it go and draws as usual. Only the shape drawn now —
    // the shapes of an earlier pass are shared with the state the editor can go back to.
    private static final int HANDLE_NONE = -1, HANDLE_MOVE = 0, HANDLE_CORNER = 1, HANDLE_END1 = 5, HANDLE_END2 = 6, HANDLE_ROTATE = 7;
    private SimpleDrawing selectedShape;
    private int shapeHandle = HANDLE_NONE;
    private float shapeDownX, shapeDownY, shapeAnchorX, shapeAnchorY;
    private float shapeX1, shapeY1, shapeX2, shapeY2;
    private final android.graphics.Paint handlePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Paint framePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final float[] pts = new float[2];

    public interface ShapeSelectionListener {
      void onShapeSelectionChanged (@Nullable SimpleDrawing shape);
    }

    private ShapeSelectionListener shapeSelectionListener;

    public void setShapeSelectionListener (ShapeSelectionListener listener) {
      this.shapeSelectionListener = listener;
    }

    public @Nullable SimpleDrawing getSelectedShape () {
      return selectedShape;
    }

    public void deselectShape () {
      if (selectedShape != null) {
        selectedShape = null;
        shapeHandle = HANDLE_NONE;
        invalidate();
        if (shapeSelectionListener != null) shapeSelectionListener.onShapeSelectionChanged(null);
      }
    }

    private void tgx101SelectIfShape (SimpleDrawing drawing) {
      if (!drawing.isEditableShape()) return;
      float w = getMeasuredWidth(), h = getMeasuredHeight();
      float dx = Math.abs(drawing.getX2() - drawing.getX1()) * w, dy = Math.abs(drawing.getY2() - drawing.getY1()) * h;
      if (Math.max(dx, dy) < Screen.dp(12f)) return; // a tap, not a shape
      selectedShape = drawing;
      shapeHandle = HANDLE_NONE;
      invalidate();
      if (shapeSelectionListener != null) shapeSelectionListener.onShapeSelectionChanged(drawing);
    }

    private boolean isShapeStillThere () {
      if (selectedShape == null) return false;
      if (state == null || !state.contains(selectedShape)) {
        deselectShape(); // undone / cleared
        return false;
      }
      return true;
    }

    // Geometry in this view's pixels: the unturned box, its centre and the turn around it

    private float cx () { return (selectedShape.getX1() + selectedShape.getX2()) / 2f * getMeasuredWidth(); }
    private float cy () { return (selectedShape.getY1() + selectedShape.getY2()) / 2f * getMeasuredHeight(); }
    private float hw () { return Math.abs(selectedShape.getX2() - selectedShape.getX1()) / 2f * getMeasuredWidth(); }
    private float hh () { return Math.abs(selectedShape.getY2() - selectedShape.getY1()) / 2f * getMeasuredHeight(); }

    /** A point of the shape's own frame (from its centre, unturned) on the screen → pts */
    private void toWorld (float lx, float ly) {
      double a = Math.toRadians(selectedShape.getRotation());
      float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
      pts[0] = cx() + lx * cos - ly * sin;
      pts[1] = cy() + lx * sin + ly * cos;
    }

    /** A screen point → the shape's own frame → pts */
    private void toLocal (float x, float y) {
      double a = Math.toRadians(-selectedShape.getRotation());
      float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
      float dx = x - cx(), dy = y - cy();
      pts[0] = dx * cos - dy * sin;
      pts[1] = dx * sin + dy * cos;
    }

    private static final float[][] CORNERS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

    private float rotateHandleOffset () {
      return hh() + Screen.dp(30f);
    }

    private int findHandle (float x, float y) {
      float slop = Screen.dp(24f);
      SimpleDrawing shape = selectedShape;
      float w = getMeasuredWidth(), h = getMeasuredHeight();
      if (shape.getType() == SimpleDrawing.TYPE_LINE || shape.getType() == SimpleDrawing.TYPE_ARROW) {
        float x1 = shape.getX1() * w, y1 = shape.getY1() * h, x2 = shape.getX2() * w, y2 = shape.getY2() * h;
        if (MathUtils.distance(x, y, x2, y2) <= slop) return HANDLE_END2;
        if (MathUtils.distance(x, y, x1, y1) <= slop) return HANDLE_END1;
        // near the line itself: move
        float len2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        float t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / len2));
        float px = x1 + t * (x2 - x1), py = y1 + t * (y2 - y1);
        return MathUtils.distance(x, y, px, py) <= slop ? HANDLE_MOVE : HANDLE_NONE;
      }
      if (shape.canRotate()) {
        toWorld(0, -rotateHandleOffset());
        if (MathUtils.distance(x, y, pts[0], pts[1]) <= slop) return HANDLE_ROTATE;
      }
      float hw = hw(), hh = hh();
      for (int i = 0; i < 4; i++) {
        toWorld(CORNERS[i][0] * hw, CORNERS[i][1] * hh);
        if (MathUtils.distance(x, y, pts[0], pts[1]) <= slop) return HANDLE_CORNER + i;
      }
      toLocal(x, y);
      return Math.abs(pts[0]) <= hw + slop / 2f && Math.abs(pts[1]) <= hh + slop / 2f ? HANDLE_MOVE : HANDLE_NONE;
    }

    private boolean tgx101HandleShapeTouch (MotionEvent e) {
      int action = e.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN) {
        shapeHandle = HANDLE_NONE;
        if (!paintingEnabled || !isShapeStillThere() || e.getPointerCount() > 1) return false;
        int handle = findHandle(e.getX(), e.getY());
        if (handle == HANDLE_NONE) {
          deselectShape(); // and draw as usual
          return false;
        }
        shapeHandle = handle;
        shapeDownX = e.getX();
        shapeDownY = e.getY();
        shapeX1 = selectedShape.getX1(); shapeY1 = selectedShape.getY1();
        shapeX2 = selectedShape.getX2(); shapeY2 = selectedShape.getY2();
        if (handle >= HANDLE_CORNER && handle < HANDLE_CORNER + 4) {
          int opposite = (handle - HANDLE_CORNER + 2) % 4;
          toWorld(CORNERS[opposite][0] * hw(), CORNERS[opposite][1] * hh());
          shapeAnchorX = pts[0];
          shapeAnchorY = pts[1];
        }
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        setEffectiveMode(paintingMode); // the controls step aside as while drawing
        invalidate();
        return true;
      }
      if (shapeHandle == HANDLE_NONE) return false;
      if (action == MotionEvent.ACTION_POINTER_DOWN) {
        return true; // a second finger doesn't zoom in the middle of an edit
      }
      float w = getMeasuredWidth(), h = getMeasuredHeight();
      if (w == 0 || h == 0) return true;
      float x = e.getX(), y = e.getY();
      switch (action) {
        case MotionEvent.ACTION_MOVE: {
          SimpleDrawing shape = selectedShape;
          if (shape == null) break;
          switch (shapeHandle) {
            case HANDLE_MOVE: {
              float dx = (x - shapeDownX) / w, dy = (y - shapeDownY) / h;
              shape.setPoints(shapeX1 + dx, shapeY1 + dy, shapeX2 + dx, shapeY2 + dy);
              break;
            }
            case HANDLE_END1:
              shape.setPoints(x / w, y / h, shapeX2, shapeY2);
              break;
            case HANDLE_END2:
              shape.setPoints(shapeX1, shapeY1, x / w, y / h);
              break;
            case HANDLE_ROTATE: {
              float deg = (float) Math.toDegrees(Math.atan2(y - cy(), x - cx())) + 90f;
              deg = ((deg % 360f) + 360f) % 360f;
              float snap = Math.round(deg / 45f) * 45f;
              if (Math.abs(deg - snap) < 4f) deg = snap; // straight and diagonal stick a little
              if (deg >= 360f) deg -= 360f;
              shape.setRotation(deg);
              break;
            }
            default: { // a corner: the opposite corner stays where it is
              float ncx = (x + shapeAnchorX) / 2f, ncy = (y + shapeAnchorY) / 2f;
              double a = Math.toRadians(-shape.getRotation());
              float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
              float vx = x - shapeAnchorX, vy = y - shapeAnchorY;
              float lw = Math.abs(vx * cos - vy * sin) / 2f, lh = Math.abs(vx * sin + vy * cos) / 2f;
              shape.setPoints((ncx - lw) / w, (ncy - lh) / h, (ncx + lw) / w, (ncy + lh) / h);
              break;
            }
          }
          invalidate();
          break;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          shapeHandle = HANDLE_NONE;
          setEffectiveMode(PaintMode.NONE);
          if (state != null) state.notifySimpleDrawingChanged();
          invalidate();
          break;
        }
      }
      return true;
    }

    @Override
    protected void dispatchDraw (android.graphics.Canvas c) {
      super.dispatchDraw(c);
      if (selectedShape == null) return;
      float w = getMeasuredWidth(), h = getMeasuredHeight();
      float r = Screen.dp(7f);
      handlePaint.setStyle(android.graphics.Paint.Style.FILL);
      handlePaint.setColor(0xffffffff);
      handlePaint.setShadowLayer(Screen.dp(2f), 0, Screen.dp(.5f), 0x80000000);
      framePaint.setStyle(android.graphics.Paint.Style.STROKE);
      framePaint.setStrokeWidth(Screen.dp(1f));
      framePaint.setColor(0xccffffff);
      framePaint.setPathEffect(new android.graphics.DashPathEffect(new float[] {Screen.dp(4f), Screen.dp(3f)}, 0));
      SimpleDrawing shape = selectedShape;
      if (shape.getType() == SimpleDrawing.TYPE_LINE || shape.getType() == SimpleDrawing.TYPE_ARROW) {
        c.drawCircle(shape.getX1() * w, shape.getY1() * h, r, handlePaint);
        c.drawCircle(shape.getX2() * w, shape.getY2() * h, r, handlePaint);
        return;
      }
      c.save();
      c.rotate(shape.getRotation(), cx(), cy());
      float hw = hw(), hh = hh(), x0 = cx(), y0 = cy();
      c.drawRect(x0 - hw, y0 - hh, x0 + hw, y0 + hh, framePaint);
      for (float[] k : CORNERS) {
        c.drawCircle(x0 + k[0] * hw, y0 + k[1] * hh, r, handlePaint);
      }
      if (shape.canRotate()) {
        framePaint.setPathEffect(null);
        c.drawLine(x0, y0 - hh, x0, y0 - rotateHandleOffset() + r, framePaint);
        c.drawCircle(x0, y0 - rotateHandleOffset(), r, handlePaint);
        // the turn arrow inside the dot
        framePaint.setColor(0xff3d8fd8);
        framePaint.setStrokeWidth(Screen.dp(1.5f));
        android.graphics.RectF arc = new android.graphics.RectF(x0 - r * .55f, y0 - rotateHandleOffset() - r * .55f, x0 + r * .55f, y0 - rotateHandleOffset() + r * .55f);
        c.drawArc(arc, -60f, 270f, false, framePaint);
      }
      c.restore();
    }

    private boolean scheduled;

    @Override
    public void run () {
      if (scheduled) {
        scheduled = false;
        parent.paintView.invalidate();
      }
    }

    @Override
    public void onDrawingHasChanged (SimpleDrawing drawing, boolean delayed) {
      if (delayed) {
        if (!scheduled) {
          scheduled = true;
          postDelayed(this, 6);
          return;
        }
      } else if (scheduled) {
        removeCallbacks(this);
      }
      parent.paintView.invalidate();
    }
  }
}
