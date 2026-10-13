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
 * File created on 11/05/2017
 */
package org.thunderdog.challegram.mediaview.paint;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import me.vkryl.core.MathUtils;
import me.vkryl.core.util.Blob;
import me.vkryl.core.util.LocalVar;

public class SimpleDrawing {
  public interface ChangeListener {
    void onDrawingHasChanged (SimpleDrawing drawing, boolean delayed);
  }

  private static LocalVar<Paint> paint;

  public static final int TYPE_PATH = 0;
  public static final int TYPE_ARROW = 1;
  public static final int TYPE_RECTANGLE = 2;
  public static final int TYPE_FILLING = 3;
  // TGx101 (user 2026-10-13, editor mockup variant Б): an oval and a straight line among the shapes
  public static final int TYPE_ELLIPSE = 4;
  public static final int TYPE_LINE = 5;

  // TGx101: brush kinds of a TYPE_PATH stroke (kept in the high half of the saved type byte — old saves read as PEN)
  public static final int STYLE_PEN = 0, STYLE_MARKER = 1, STYLE_PENCIL = 2, STYLE_NEON = 3, STYLE_FREE_ARROW = 4, STYLE_ERASER = 5;
  private int style = STYLE_PEN;

  public void setStyle (int style) {
    this.style = style;
  }

  public int getStyle () {
    return style;
  }

  public boolean isEraser () {
    return type == TYPE_PATH && style == STYLE_ERASER;
  }

  private static boolean isTwoPointShape (int type) {
    return type == TYPE_ARROW || type == TYPE_RECTANGLE || type == TYPE_ELLIPSE || type == TYPE_LINE;
  }

  private final int type;

  final int canvasWidth, canvasHeight;
  private final float canvasScale, canvasRotation;

  private int color;
  private float strokeRadius;

  private float x1, x2;
  private float y1, y2;

  private CustomPath path;

  private ArrayList<ChangeListener> changeListeners;

  public SimpleDrawing (int type) {
    this(type, 0, 0, 0f, 0f);
  }

  public SimpleDrawing (int type, int canvasWidth, int canvasHeight, float canvasScale, float canvasRotation) {
    this.type = type;
    this.canvasWidth = canvasWidth;
    this.canvasHeight = canvasHeight;
    this.canvasScale = canvasScale;
    this.x1 = x2 = x1;
    this.y1 = y2 = y1;
    this.canvasRotation = canvasRotation;
    this.path = type == TYPE_PATH ? new CustomPath(this) : null;
    if (paint == null) {
      paint = new LocalVar<>();
      paint.set(newPaint());
    }
  }

  private static Paint newPaint () {
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeCap(Paint.Cap.ROUND);
    return p;
  }

  float getRelativeX (float x) {
    return x / (float) canvasWidth;
  }

  float getRelativeY (float y) {
    return y / (float) canvasHeight;
  }

  public void startDrawing (MotionEvent e) { // touchDown
    float x = getRelativeX(e.getX());
    float y = getRelativeY(e.getY());
    this.x1 = this.x2 = x;
    this.y1 = this.y2 = y;
    if (type == TYPE_PATH) {
      trackPoint(e, false, true);
    }
  }

  public void moveDrawing (MotionEvent e, boolean allowHistory) { // touchMove
    float x = getRelativeX(e.getX());
    float y = getRelativeY(e.getY());
    switch (type) {
      case TYPE_ARROW:
      case TYPE_RECTANGLE:
      case TYPE_ELLIPSE:
      case TYPE_LINE: {
        if (this.x2 != x || this.y2 != y) {
          this.x2 = x;
          this.y2 = y;
          invalidate(true);
        }
        break;
      }
      case TYPE_PATH: {
        trackPoint(e, false, allowHistory);
        break;
      }
    }
  }

  public boolean completeDrawing (MotionEvent e, boolean byPinch) { // touchUp
    switch (type) {
      case TYPE_ARROW:
      case TYPE_RECTANGLE:
      case TYPE_ELLIPSE:
      case TYPE_LINE: {
        return !byPinch || (x1 == x2 && y1 == y2);
      }
      case TYPE_PATH: {
        if (!byPinch || path.getActionCount() > 3) {
          trackPoint(e, true, true);
          path.trim();
          return true;
        }
        return false;
      }
    }
    return true;
  }

  private void trackPoint (MotionEvent e, boolean finish, boolean allowHistory) {
    buildPath(e, finish, allowHistory);
    invalidate(!finish);
  }

  private int totalEventCount;
  private float pastX, pastY, pastDx, pastDy, prevX, prevY, prevDx, prevDy;
  private float checkX, checkY;

  private void buildPath (MotionEvent e, boolean finish, boolean allowHistory) {
    final boolean useHistory = allowHistory && e.getAction() == MotionEvent.ACTION_MOVE;
    final int eventCount = useHistory ? e.getHistorySize() : 1;

    for (int i = 0; i < eventCount; i++) {
      float nextX, nextY;

      if (useHistory) {
        nextX = e.getHistoricalX(i);
        nextY = e.getHistoricalY(i);
      } else {
        nextX = e.getX();
        nextY = e.getY();
      }
      if (finish) {
        if (totalEventCount == 0) {
          path.moveTo(checkX = nextX, checkY = nextY);
        }
        if (checkX == nextX && checkY == nextY) {
          path.lineTo(nextX + 1, nextY);
        } else {
          path.quadTo(checkX, checkY, nextX, nextY);
        }
        return;
      }
      if (checkX == nextX && checkY == nextY && totalEventCount > 0) {
        continue;
      }
      checkX = nextX;
      checkY = nextY;
      float dx = 0, dy = 0;
      int eventIndex = totalEventCount++;
      if (eventIndex == 0) {
        path.moveTo(nextX, nextY);
      } else {
        dx = (nextX - this.prevX) / 3f;
        dy = (nextY - this.prevY) / 3f;
      }

      if (eventIndex > 3) {
        float pointX = prevX;
        float pointY = prevY;
        float pointDx = prevDx;
        float pointDy = prevDy;
        float prevX = pastX;
        float prevY = pastY;
        float prevDx = pastDx;
        float prevDy = pastDy;
        path.cubicTo(prevX + prevDx, prevY + prevDy, pointX - pointDx, pointY - pointDy, pointX, pointY);
      } else if (eventIndex == 2) {
        //prevDx = (nextX - prevX) / 3f;
        //prevDy = (nextY - prevY) / 3f;

        float pointX = nextX;
        float pointY = nextY;
        //dx = (pointX - prevX) / 3;
        //dy = (pointY - prevY) / 3;

        // `path.cubicTo(prevX - prevDx, prevY + prevDy, pointX - dx, pointY - dy, pointX, pointY);
      }

      pastX = prevX;
      pastY = prevY;
      pastDx = prevDx;
      pastDy = prevDy;
      prevX = nextX;
      prevY = nextY;
      prevDx = dx;
      prevDy = dy;
    }

    /*final int size = points.size();
    if (size > 1) {
      for (int i = Math.max(0, size - 2); i < size; i++){
        Point point = points.get(i);
        if (i == 0) {
          Point next = points.get(i + 1);
          point.dx = ((next.x - point.x) / 3);
          point.dy = ((next.y - point.y) / 3);
        } else if (i == size - 1) {
          Point prev = points.get(i - 1);
          point.dx = ((point.x - prev.x) / 3);
          point.dy = ((point.y - prev.y) / 3);
        } else {
          Point next = points.get(i + 1);
          Point prev = points.get(i - 1);
          point.dx = ((next.x - prev.x) / 3);
          point.dy = ((next.y - prev.y) / 3);
        }
      }
    }

    boolean first = true;
    int i = 0;
    for (Point point : points) {
      if (first) {
        first = false;
        path.moveTo(point.x, point.y);
      } else {
        Point prev = points.get(i - 1);
        path.cubicTo(prev.x + prev.dx, prev.y + prev.dy, point.x - point.dx, point.y - point.dy, point.x, point.y);
      }
      i++;
    }*/
  }

  private static boolean needsCanvasRotation (int type) {
    switch (type) {
      case TYPE_RECTANGLE:
      case TYPE_ELLIPSE:
        return true;
    }
    return false;
  }

  private static boolean needsStrokeRadius (int type) {
    switch (type) {
      case TYPE_FILLING:
        return false;
    }
    return true;
  }

  public void addChangeListener (ChangeListener listener) {
    if (changeListeners == null) {
      changeListeners = new ArrayList<>();
    }
    changeListeners.add(listener);
  }

  public void removeChangeListener (ChangeListener listener) {
    if (changeListeners != null) {
      changeListeners.remove(listener);
    }
  }

  private int dp (float dp, float width, float height) {
    float scale = width / canvasWidth; // , height /canvasHeight);
    return (int) (Screen.dpf(dp) * scale * canvasScale);
  }

  public boolean compare (SimpleDrawing b) {
    return b != null &&
      b.type == type &&
      b.style == style &&
      b.color == color &&
      (!needsStrokeRadius(type) || b.strokeRadius == strokeRadius) &&
      (type == TYPE_FILLING || (b.canvasWidth == canvasWidth && b.canvasHeight == canvasHeight) || (b.canvasWidth / b.canvasHeight) == (canvasWidth / canvasHeight)) && // checking only for aspect ratio, since result will be the same
      (!needsCanvasRotation(type) || b.canvasRotation == canvasRotation) &&
      b.x1 == x1 && b.x2 == x2 &&
      b.y1 == y1 && b.y2 == y2 && (type != TYPE_PATH || b.path.compare(path));
  }

  public void setBrushParameters (int color, float strokeRadius) {
    if (this.color != color || this.strokeRadius != strokeRadius) {
      this.color = color;
      this.strokeRadius = strokeRadius;
      invalidate(false);
    }
  }

  private void invalidate (boolean delayed) {
    if (this.changeListeners != null) {
      for (ChangeListener listener : changeListeners) {
        listener.onDrawingHasChanged(this, delayed);
      }
    }
  }

  private float getCanvasX (double x) {
    return (float) ((double) canvasWidth * x);
  }

  private float getCanvasY (double y) {
    return (float) ((double) canvasHeight * y);
  }

  public void draw (Canvas c, int viewLeft, int viewTop, int viewRight, int viewBottom) {
    int width = viewRight - viewLeft; //  (viewLeft + viewRight) / 2;
    int height = viewBottom - viewTop; // (viewTop + viewBottom) / 2;

    float x1 = (float) ((float) viewLeft + Math.ceil((double) width * this.x1));
    float x2 = (float) ((float) viewLeft + Math.floor((double) width * this.x2));
    if (x1 == x2) {
      if (x2 >= width) {
        x1--;
      } else {
        x2--;
      }
    }
    float y1 = (float) ((float) viewTop + Math.ceil((double) height * this.y1));
    float y2 = (float) ((float) viewTop + Math.floor((double) height * this.y2));
    if (y1 == y2) {
      if (y2 >= height) {
        y1--;
      } else {
        y2--;
      }
    }

    float left = Math.min(x1, x2);
    float top = Math.min(y1, y2);
    float right = Math.max(x1, x2);
    float bottom = Math.max(y1, y2);
    float strokeRadius = this.strokeRadius;

    float strokeSize = dp(strokeRadius, width, height);
    Paint paint = SimpleDrawing.paint.get();
    if (paint == null) {
      SimpleDrawing.paint.set(paint = newPaint());
    }
    paint.setStrokeWidth(strokeSize);
    paint.setColor(color);
    // TGx101: a shared paint — every stroke starts from the plain pen
    paint.setXfermode(null);
    paint.setMaskFilter(null);
    paint.setPathEffect(null);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);

    switch (type) {
      case TYPE_ELLIPSE: {
        RectF rectF = Paints.getRectF();
        rectF.set(left, top, right, bottom);
        final boolean saved = canvasRotation != 0f;
        if (saved) {
          c.save();
          c.rotate(-canvasRotation, left, top);
        }
        c.drawOval(rectF, paint);
        if (saved) {
          c.restore();
        }
        break;
      }
      case TYPE_LINE: {
        c.drawLine(x1, y1, x2, y2, paint);
        break;
      }
      case TYPE_RECTANGLE: {
        RectF rectF = Paints.getRectF();
        rectF.set(left, top, right, bottom);
        final boolean saved = canvasRotation != 0f;
        if (saved) {
          c.save();
          c.rotate(-canvasRotation, left, top);
        }
        // float radius = dp(2f, width, height);
        c.drawRect(rectF, paint);
        if (saved) {
          c.restore();
        }
        break;
      }
      case TYPE_FILLING: {
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(viewLeft, viewTop, viewRight, viewBottom, paint);
        paint.setStyle(Paint.Style.STROKE);
        break;
      }
      case TYPE_ARROW: {
        c.drawLine(x1, y1, x2, y2, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(x1, y1, strokeSize / 2, paint);
        c.drawCircle(x2, y2, strokeSize / 2, paint);
        paint.setStyle(Paint.Style.STROKE);

        float diffX = (x1 - x2) / 2f;
        float diffY = (y1 - y2) / 2f;

        float maxDistance = dp(24f, width, height);

        float angle = 35f;

        float distance = Math.abs(MathUtils.distance(0, 0, diffX, diffY));
        if (distance > maxDistance) {
          float scale = maxDistance / distance;
          diffX *= scale;
          diffY *= scale;
        } else if (distance < maxDistance) {
          float minAngle = 15f;
          angle = minAngle + (angle - minAngle) * distance / maxDistance;
        }

        // double angle = Math.atan2(y2 - y1, x2 - x1);
        c.save();
        c.rotate(-angle, x2, y2);
        c.drawLine(x2, y2, x2 + diffX, y2 + diffY, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(x2 + diffX, y2 + diffY, strokeSize / 2, paint);
        paint.setStyle(Paint.Style.STROKE);
        c.rotate(angle * 2, x2, y2);
        c.drawLine(x2, y2, x2 + diffX, y2 + diffY, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(x2 + diffX, y2 + diffY, strokeSize / 2, paint);
        paint.setStyle(Paint.Style.STROKE);
        c.restore();

        break;
      }
      case TYPE_PATH: {
        c.save();
        c.clipRect(viewLeft, viewTop, viewRight, viewBottom);
        if (viewLeft != 0 || viewTop != 0) {
          c.translate(viewLeft, viewTop);
        }
        android.graphics.Path p = path.getPath(width, height);
        drawStyledPath(c, p, paint, strokeSize, width, height);
        c.restore();
        break;
      }
    }
  }

  /** TGx101: the five brushes and the eraser (user 2026-10-13, editor mockup variant Б) */
  private void drawStyledPath (Canvas c, android.graphics.Path p, Paint paint, float strokeSize, int width, int height) {
    switch (style) {
      case STYLE_MARKER: {
        // wide, see-through, flat tip — like a highlighter
        paint.setStrokeWidth(strokeSize * 2.4f);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setColor(me.vkryl.core.ColorUtils.alphaColor(.5f, color));
        c.drawPath(p, paint);
        break;
      }
      case STYLE_PENCIL: {
        // thin and grainy
        paint.setStrokeWidth(Math.max(1f, strokeSize * .6f));
        paint.setColor(me.vkryl.core.ColorUtils.alphaColor(.85f, color));
        paint.setPathEffect(new android.graphics.DiscretePathEffect(dp(1.6f, width, height), dp(.45f, width, height)));
        c.drawPath(p, paint);
        break;
      }
      case STYLE_NEON: {
        // a glow in the colour with a light core
        paint.setStrokeWidth(strokeSize * 1.6f);
        paint.setMaskFilter(new android.graphics.BlurMaskFilter(Math.max(1f, strokeSize * 1.4f), android.graphics.BlurMaskFilter.Blur.NORMAL));
        c.drawPath(p, paint);
        paint.setMaskFilter(null);
        paint.setStrokeWidth(Math.max(1f, strokeSize * .55f));
        paint.setColor(me.vkryl.core.ColorUtils.fromToArgb(color, 0xffffffff, .7f));
        c.drawPath(p, paint);
        break;
      }
      case STYLE_FREE_ARROW: {
        // a free line with a head where the finger left
        c.drawPath(p, paint);
        android.graphics.PathMeasure measure = new android.graphics.PathMeasure(p, false);
        float length = measure.getLength();
        if (length > strokeSize) {
          float[] end = new float[2], tan = new float[2];
          measure.getPosTan(length, end, tan);
          float[] back = new float[2];
          measure.getPosTan(Math.max(0, length - Math.max(strokeSize * 3f, dp(12f, width, height))), back, null);
          double angle = Math.atan2(end[1] - back[1], end[0] - back[0]);
          float head = Math.min(length * .5f, Math.max(strokeSize * 3f, dp(16f, width, height)));
          for (int side = -1; side <= 1; side += 2) {
            double a = angle + Math.PI - side * Math.toRadians(32);
            c.drawLine(end[0], end[1], end[0] + (float) (Math.cos(a) * head), end[1] + (float) (Math.sin(a) * head), paint);
          }
        }
        break;
      }
      case STYLE_ERASER: {
        // clears what was drawn below it (the drawings sit on a layer of their own, the photo stays)
        paint.setStrokeWidth(strokeSize * 1.5f);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
        c.drawPath(p, paint);
        paint.setXfermode(null);
        break;
      }
      default: {
        c.drawPath(p, paint);
        break;
      }
    }
  }

  // Blob

  public void save (RandomAccessFile file) throws IOException {
    file.writeByte((byte) (type | (style << 4)));

    Blob.writeVarint(file, canvasWidth);
    Blob.writeVarint(file, canvasHeight);
    file.writeFloat(canvasScale);
    if (needsCanvasRotation(type)) {
      file.writeFloat(canvasRotation);
    }

    file.writeInt(color);
    if (needsStrokeRadius(type)) {
      file.writeFloat(strokeRadius);
    }

    savePositionData(file);
  }

  public void save (Blob blob) {
    blob.writeByte((byte) (type | (style << 4)));

    blob.writeVarint(canvasWidth);
    blob.writeVarint(canvasHeight);
    blob.writeFloat(canvasScale);
    if (needsCanvasRotation(type)) {
      blob.writeFloat(canvasRotation);
    }

    blob.writeInt(color);
    if (needsStrokeRadius(type)) {
      blob.writeFloat(strokeRadius);
    }

    savePositionData(blob);
  }

  private void savePositionData (RandomAccessFile file) throws IOException {
    switch (type) {
      case TYPE_ARROW:
      case TYPE_RECTANGLE:
      case TYPE_ELLIPSE:
      case TYPE_LINE:
        file.writeFloat(x1);
        file.writeFloat(x2);
        file.writeFloat(y1);
        file.writeFloat(y2);
        break;
      case TYPE_PATH: {
        path.saveData(file);
        break;
      }
    }
  }

  private void savePositionData (Blob blob) {
    switch (type) {
      case TYPE_ARROW:
      case TYPE_RECTANGLE:
      case TYPE_ELLIPSE:
      case TYPE_LINE: {
        blob.writeFloat(x1);
        blob.writeFloat(x2);
        blob.writeFloat(y1);
        blob.writeFloat(y2);
        break;
      }
      case TYPE_PATH: {
        path.saveData(blob);
        break;
      }
    }
  }

  public int getEstimatedOutputSize () {
    return 1 + Blob.sizeOf(canvasWidth) + Blob.sizeOf(canvasHeight) + 4 + (needsCanvasRotation(type) ? 4 : 0) + 4 + (needsStrokeRadius(type) ? 4 : 0) + getEstimatedPositionSaveSize();
  }

  private int getEstimatedPositionSaveSize () {
    switch (type) {
      case TYPE_ARROW:
      case TYPE_RECTANGLE:
      case TYPE_ELLIPSE:
      case TYPE_LINE: {
        return 8 * 4;
      }
      case TYPE_PATH: {
        return path.getEstimatedOutputSize();
      }
    }
    return 0;
  }

  public static SimpleDrawing restore (RandomAccessFile file) throws IOException {
    int rawType = file.read() & 0xff;
    int type = rawType & 0x0f, style = rawType >> 4;

    int canvasWidth = Blob.readVarint(file);
    int canvasHeight = Blob.readVarint(file);
    float canvasScale = file.readFloat();
    float canvasRotation = 0f;
    if (needsCanvasRotation(type)) {
      canvasRotation = file.readFloat();
    }

    int color = file.readInt();
    float strokeRadius;
    if (needsStrokeRadius(type)) {
      strokeRadius = file.readFloat();
    } else {
      strokeRadius = 0f;
    }

    SimpleDrawing drawing = new SimpleDrawing(type, canvasWidth, canvasHeight, canvasScale, canvasRotation);
    drawing.setBrushParameters(color, strokeRadius);
    drawing.style = style;

    restorePositionData(file, drawing);

    return drawing;
  }

  public static SimpleDrawing restore (Blob blob) {
    int rawType = blob.readByte() & 0xff;
    int type = rawType & 0x0f, style = rawType >> 4;

    int canvasWidth = blob.readVarint();
    int canvasHeight = blob.readVarint();
    float canvasScale = blob.readFloat();
    float canvasRotation = 0f;
    if (needsCanvasRotation(type)) {
      canvasRotation = blob.readFloat();
    }

    int color = blob.readInt();
    float strokeRadius;
    if (needsStrokeRadius(type)) {
      strokeRadius = blob.readFloat();
    } else {
      strokeRadius = 0f;
    }

    SimpleDrawing drawing = new SimpleDrawing(type, canvasWidth, canvasHeight, canvasScale, canvasRotation);
    drawing.setBrushParameters(color, strokeRadius);
    drawing.style = style;

    restorePositionData(blob, drawing);

    return drawing;
  }

  private static void restorePositionData (RandomAccessFile file, SimpleDrawing out) throws IOException {
    switch (out.type) {
      case TYPE_ARROW:
      case TYPE_RECTANGLE:
      case TYPE_ELLIPSE:
      case TYPE_LINE: {
        out.x1 = file.readFloat();
        out.x2 = file.readFloat();
        out.y1 = file.readFloat();
        out.y2 = file.readFloat();
        break;
      }
      case TYPE_PATH: {
        out.path = new CustomPath(out, file);
        break;
      }
    }
  }

  private static void restorePositionData (Blob blob, SimpleDrawing out) {
    switch (out.type) {
      case TYPE_ARROW:
      case TYPE_RECTANGLE:
      case TYPE_ELLIPSE:
      case TYPE_LINE: {
        out.x1 = blob.readFloat();
        out.x2 = blob.readFloat();
        out.y1 = blob.readFloat();
        out.y2 = blob.readFloat();
        break;
      }
      case TYPE_PATH: {
        out.path = new CustomPath(out, blob);
        break;
      }
    }
  }
}
