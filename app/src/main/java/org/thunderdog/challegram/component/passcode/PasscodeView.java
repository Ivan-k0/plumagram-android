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
 * File created on 07/08/2015 at 20:23
 */
package org.thunderdog.challegram.component.passcode;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.PasscodeBuilder;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ColorUtils;

public class PasscodeView extends View {
  private static final int TEXT_PASSWORD_TOP = 114;
  private static final int TEXT_PIN_TOP = 82;
  private static final int TEXT_PATTERN_TOP = 138;
  private static final int TEXT_GESTURE_TOP = 74;

  private static final int LINE_PASSWORD_TOP = 170;
  private static final int LINE_PIN_TOP = 148;

  private static final int LINE_PASSWORD_PADDING = 44;
  private static final int LINE_PIN_PADDING = 20;

  private boolean isIntercepted;

  private int orientation;
  private int state;
  private int mode;
  private String text;

  private Callback callback;

  public PasscodeView (Context context) {
    super(context);
    this.orientation = UI.getOrientation();
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (changed) {
      buildLayout();
    }
  }

  private float textLeft, textTop, textWidth;

  private float lineLeft, lineTop, lineRight;
  public void setModeAndState (int mode, int state) {
    if (this.mode != mode) {
      this.state = state;
      setMode(mode);
    } else {
      setState(state);
    }
  }

  public void setMode (int mode) {
    if (this.mode != mode) {
      this.mode = mode;
      updateText();
      layoutLine();
      buildLayout();
      invalidate();
    }
  }

  private int extraData;

  public void setExtraData (int extraData) {
    if (this.extraData != extraData) {
      this.extraData = extraData;
    }
  }

  public void setState (int state) {
    if (this.state != state) {
      this.state = state;
      updateText();
      invalidate();
      if (state == Passcode.STATE_CONFIRM) {
        callback.updateConfirmMode();
      }
    }
  }

  public int getState () {
    return state;
  }

  public String getText () {
    return text;
  }

  public boolean isIntercepted () {
    return isIntercepted;
  }

  private void layoutLine () {
    switch (mode) {
      case Passcode.MODE_PASSWORD: {
        lineLeft = Screen.dpf(LINE_PASSWORD_PADDING);
        lineTop = Screen.dpf(LINE_PASSWORD_TOP);
        break;
      }
      case Passcode.MODE_PINCODE: {
        lineLeft = UI.isLandscape() ? Screen.dpf(LINE_PIN_PADDING) : (Screen.currentWidth() - (Screen.dp(PinInputLayout.BUTTON_WIDTH) * 3)) / 2;
        lineTop = Screen.dpf(LINE_PIN_TOP);
        break;
      }
      default: {
        lineTop = -1;
        break;
      }
    }
    if (lineTop != -1) {
      lineTop += getPaddingTop();
      lineRight = mode == Passcode.MODE_PINCODE && orientation == Configuration.ORIENTATION_LANDSCAPE ? ((float) getMeasuredWidth() * .5f) - lineLeft : getMeasuredWidth() - lineLeft;
    }
  }

  // private Paint iconPaint;
  private Drawable icon;
  private float iconLeft, iconTop;

  public void setDisplayLogo () {
    // iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    icon = Drawables.load(R.drawable.deproko_logo_telegram_passcode_56);
  }

  public void layoutIcon () {
    if (icon != null) {
      iconLeft = textLeft + textWidth * .5f - (float) icon.getMinimumWidth() * .5f;
      iconTop = Math.max(textTop * .5f - (float) icon.getMinimumHeight() * .5f, Screen.dp(40f));
    }
  }

  private void layoutContent (int totalWidth, int totalHeight) {
    switch (mode) {
      case Passcode.MODE_PATTERN: {
        layoutPattern(totalWidth, totalHeight);
        break;
      }
      case Passcode.MODE_PINCODE: {
        layoutPincode();
        break;
      }
    }
  }

  private float getTextTop () {
    float offset = 9f;
    switch (mode) {
      case Passcode.MODE_PASSWORD: {
        return Screen.dpf(TEXT_PASSWORD_TOP + offset) + getPaddingTop();
      }
      case Passcode.MODE_PINCODE: {
        return Screen.dpf(TEXT_PIN_TOP + offset) + getPaddingTop();
      }
      case Passcode.MODE_PATTERN: {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
          return Screen.dpf(TEXT_PATTERN_TOP + offset) + getPaddingTop();
        }
        float height = Screen.currentActualHeight() - HeaderView.getSize(false) - getPaddingTop();
        return height * .5f + getPaddingTop();
      }
      case Passcode.MODE_GESTURE:
      case Passcode.MODE_BIOMETRICS: {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
          return Screen.dpf(TEXT_GESTURE_TOP + offset) + getPaddingTop();
        }
        float height = Screen.currentActualHeight() - HeaderView.getSize(false) - getPaddingTop();
        return height * .5f + getPaddingTop();
      }
      default: {
        return 0f;
      }
    }
  }

  public void updateTextAndInvalidate () {
    updateText();
    invalidate();
  }

  private void updateText () {
    String suffix = callback.makeBruteForceSuffix();
    int seconds = Settings.instance().getPasscodeBlockSeconds(suffix);
    if (seconds > 0) {
      text = Lang.getString(R.string.format_PasscodeTooManyAttempts, Lang.plural(R.string.TryAgainSeconds, seconds));
    } else {
      text = Passcode.getActionName(mode, state, true, extraData);
      if (mode == Passcode.MODE_PATTERN && tgx101Symbols()) {
        text = Lang.getString(state == Passcode.STATE_UNLOCK ? R.string.Tgx101PasscodeSignsUnlock : state == Passcode.STATE_CONFIRM ? R.string.Tgx101PasscodeSignsConfirm : R.string.Tgx101PasscodeSignsChoose);
      }
    }

    if (text == null) {
      return;
    }
    if (state == Passcode.STATE_CONFIRM && Passcode.canBeInvisible(mode) && !callback.isPasscodeVisible()) {
      text = Lang.getString(R.string.passcode_confirm_invisible, text);
    }
    textWidth = U.measureText(text, Paints.getRegularTextPaint(13f));
    textTop = getTextTop();
    textLeft = getTextLeft();
  }

  private float getTextLeft () {
    float center = (float) getMeasuredWidth() * .5f;
    return (mode == Passcode.MODE_PINCODE || mode == Passcode.MODE_PATTERN || mode == Passcode.MODE_GESTURE || mode == Passcode.MODE_BIOMETRICS) && orientation == Configuration.ORIENTATION_LANDSCAPE ? center * .5f - textWidth * .5f : center - textWidth * .5f;
  }

  private void buildLayout () {
    int totalWidth = getMeasuredWidth();
    if (totalWidth == 0) return;

    textLeft = getTextLeft();
    layoutLine();
    layoutIcon();

    layoutContent(totalWidth, getMeasuredHeight());
  }

  @Override
  protected void onDraw (Canvas c) {
    // int decentColor = ;
    if (lineTop != -1) {
      c.drawRect(lineLeft, lineTop, lineRight, lineTop + Screen.dp(1f), Paints.fillingPaint(ColorUtils.alphaColor(.3f, Theme.getColor(ColorId.passcodeText))));
    }
    switch (mode) {
      case Passcode.MODE_PATTERN: {
        drawPattern(c);
        break;
      }
      case Passcode.MODE_PINCODE: {
        drawPincode(c);
        break;
      }
    }
    if (text != null) {
      c.drawText(text, textLeft, textTop, Paints.getRegularTextPaint(13f, Theme.passcodeSubtitleColor()));
    }
    if (icon != null) {
      Drawables.draw(c, icon, iconLeft, iconTop, Paints.getPasscodeIconPaint());
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    switch (mode) {
      case Passcode.MODE_PATTERN: {
        return onPatternTouch(event);
      }
      default: {
        return super.onTouchEvent(event);
      }
    }
  }

  // Orientation

  public void setOrientation (int orientation) {
    this.orientation = orientation;
    switch (mode) {
      case Passcode.MODE_PATTERN: {
        textTop = getTextTop();
        clearPattern(false);
        break;
      }
      case Passcode.MODE_GESTURE:
      case Passcode.MODE_BIOMETRICS: {
        textTop = getTextTop();
        break;
      }
      case Passcode.MODE_PINCODE: {
        layoutPincode();
        break;
      }
    }
  }

  // Pattern

  private static final int PATTERN_TOP = 206;
  private static final int PATTERN_PADDING = 72;
  private static final int PATTERN_CELL_SIZE = 102;

  private ArrayList<PatternLine> patternLines;
  private PatternDot[] patternDots;

  private float patternOffset;

  private PatternLine patternLine;
  private @Nullable PasscodeBuilder pattern;

  @SuppressWarnings ("SuspiciousNameCombination")
  private void layoutPattern (int totalWidth, int totalHeight) {
    if (patternDots == null) {
      pattern = new PasscodeBuilder();
      patternLine = new PatternLine(0, 0, 0, 0);
      patternLines = new ArrayList<>();
      patternDots =  new PatternDot[] {
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this)
      };
    }

    float cellSize = Screen.dpf(PATTERN_CELL_SIZE);
    float paddingLeft = Screen.dpf(PATTERN_PADDING);
    float paddingTop, paddingBottom;

    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
      paddingTop = Screen.dp(12f);
      paddingBottom = paddingTop;
    } else {
      paddingTop = Screen.dpf(PATTERN_TOP);
      paddingBottom = Screen.dpf(88f);
    }

    patternOffset = Screen.dpf(26f);

    float factor = 1f;

    float width = cellSize * 2f;
    float height = width;
    float fullWidth = width + paddingLeft * 2f;
    float fullHeight = height + paddingTop + paddingBottom;

    if (fullWidth != totalWidth) {
      factor = totalWidth / fullWidth;
    }

    if (fullHeight != totalHeight) {
      factor = Math.min(factor, totalHeight / fullHeight);
    }

    if (factor != 1f) {
      if (factor < 1f) {
        cellSize *= factor;
        patternOffset = Math.max(Screen.dpf(PatternDot.RADIUS * 2f), patternOffset * factor);
        width = cellSize * 2f;
        height = width;
        paddingTop *= factor;
        // paddingBottom *= factor;
      }
      if (orientation == Configuration.ORIENTATION_PORTRAIT) {
        paddingLeft = (totalWidth - width) * .5f;
      } else {
        paddingTop = (totalHeight - height) * .5f;
      }
    }

    float patternLeft;
    float patternTop;
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
      patternLeft = totalWidth - width - paddingLeft;
      patternTop = paddingTop;
    } else {
      patternLeft = paddingLeft;
      patternTop = paddingTop + getPaddingTop();
    }


    float cx = patternLeft;
    float cy = patternTop;
    int i = 0;

    for (PatternDot dot : patternDots) {
      dot.setXY(cx, cy);
      i++;
      if (i % 3 == 0) {
        cx = patternLeft;
        cy += cellSize;
      } else {
        cx += cellSize;
      }
    }
  }

  private boolean selectPatternDot (float x, float y, boolean noLine) {
    int i = 0;
    for (PatternDot dot : patternDots) {
      if (dot.isMatching(x, y, patternOffset)) {
        dot.setSelected(true);
        appendPatternPoint(i, noLine);
        if (!noLine) {
          patternLine.setFromXY(x, y);
          patternLine.setFromXYAnimated(dot.getX(), dot.getY());
        }
        return true;
      }
      i++;
    }
    return false;
  }

  private void appendPatternPoint (int dot, boolean noLine) {
    if (pattern == null) {
      return;
    }

    if (!noLine && pattern.getSize() > 0) {
      PatternDot prev = patternDots[pattern.getLastDigit()];
      PatternDot cur = patternDots[dot];

      float x = prev.getX() + (cur.getX() - prev.getX()) * .5f;
      float y = prev.getY() + (cur.getY() - prev.getY()) * .5f;
      selectPatternDot(x, y, true);

      PatternLine line = new PatternLine(prev.getX(), prev.getY(), patternLine.getToX(), patternLine.getToY());
      line.setParentView(this);
      line.setToXYAnimated(cur.getX(), cur.getY());
      patternLines.add(line);
    }

    pattern.append(dot);
  }

  private float patternFactor;
  private boolean patternAnimating;

  public void setPatternFactor (float factor) {
    if (this.patternFactor != factor) {
      this.patternFactor = factor;
      PatternLine.setAlpha(1f - factor);
      invalidate();
    }
  }

  public void clearPattern (boolean animated) {
    isIntercepted = false;
    if (!animated) {
      if (pattern != null) {
        pattern.clear();
        patternLines.clear();
        for (PatternDot dot : patternDots) {
          dot.setSelected(false);
        }
      }
    } else {
      this.patternFactor = 0f;
      this.patternAnimating = true;
      ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
      // FIXME do with implements
      animator.addUpdateListener(animation -> setPatternFactor(AnimatorUtils.getFraction(animation)));
      animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      animator.setDuration(160l);
      animator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          clearPattern(false);
          PatternLine.setAlpha(1f);
          patternAnimating = false;
          invalidate();
        }
      });
      animator.start();
    }
  }

  private boolean onPatternTouch (MotionEvent e) {
    if (tgx101Symbols()) {
      return tgx101OnSignTouch(e);
    }
    if (patternAnimating) {
      return false;
    }
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        float x = e.getX();
        float y = e.getY();
        patternLine.setToXY(x, y);
        clearPattern(false);
        if (selectPatternDot(x, y, false)) {
          isIntercepted = true;
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (isIntercepted) {
          float x = e.getX();
          float y = e.getY();
          patternLine.setToXY(x, y);
          if (!selectPatternDot(x, y, false)) {
            invalidate();
          }
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (isIntercepted) {
          if (callback != null) {
            callback.handlePattern(new PasscodeBuilder(pattern));
          }
          clearPattern(true);
          invalidate();
          return true;
        }
        break;
      }
    }

    return false;
  }

  private void drawPattern (Canvas c) {
    boolean signs = tgx101Symbols();
    if (signs) {
      tgx101DrawProgress(c);
      long since = android.os.SystemClock.uptimeMillis() - tgx101FlashAt;
      if (tgx101FlashDot != -1 && since < 220) {
        PatternDot d = patternDots[tgx101FlashDot];
        c.drawCircle(d.getX(), d.getY(), Screen.dp(30f), Paints.fillingPaint(ColorUtils.alphaColor(.22f * (1f - since / 220f), Theme.getColor(ColorId.passcodeIcon))));
        postInvalidateOnAnimation();
      }
    }
    for (int i = 0; i < patternDots.length; i++) {
      PatternDot dot = patternDots[i];
      if (signs) {
        tgx101DrawSign(c, tgx101Signs()[i], dot.getX(), dot.getY());
      } else {
        dot.draw(c);
      }
    }
    if (callback.isPasscodeVisible()) {
      for (PatternLine line : patternLines) {
        line.draw(c);
      }
      if (isIntercepted || patternAnimating) {
        patternLine.draw(c);
      }
    }
  }

  // TGx101 (user 2026-11-15, mockup «Код-пароль: жест, который не подсмотреть», variant Б): signs on the dots, shuffled
  // on each try; the key is the order of the signs, so the line drawn looks different every time

  private int[] tgx101Signs; // tgx101Signs[dot] = the sign standing on that dot

  // (user 2026-11-15 «палец когда едет задевает другие»): the signs are tapped one by one, not drawn — a finger sliding
  // between two signs crossed others, and with the signs moving each time the key came out different. Unlock goes
  // as soon as the key's length is tapped; setting it up, a pause of a second after 4 or more taps finishes it

  private int tgx101DownDot = -1;
  private long tgx101FlashAt;
  private int tgx101FlashDot = -1;
  private final Runnable tgx101Finish = this::tgx101Submit;

  private int tgx101DotAt (float x, float y) {
    float r = Screen.dp(30f);
    for (int i = 0; i < patternDots.length; i++) {
      PatternDot d = patternDots[i];
      if (Math.abs(x - d.getX()) <= r && Math.abs(y - d.getY()) <= r) return i;
    }
    return -1;
  }

  private boolean tgx101OnSignTouch (MotionEvent e) {
    if (pattern == null || patternDots == null) return false;
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        tgx101DownDot = tgx101DotAt(e.getX(), e.getY());
        return tgx101DownDot != -1;
      case MotionEvent.ACTION_UP: {
        int dot = tgx101DotAt(e.getX(), e.getY());
        if (dot != -1 && dot == tgx101DownDot) tgx101Tap(dot);
        tgx101DownDot = -1;
        return true;
      }
      case MotionEvent.ACTION_CANCEL:
        tgx101DownDot = -1;
        return true;
    }
    return true;
  }

  private void tgx101Tap (int dot) {
    String typed = pattern.toString();
    if (pattern.getSize() >= PasscodeBuilder.PATTERN_MAX_SIZE || typed.indexOf((char) ('0' + dot)) >= 0) return; // each sign once
    pattern.append(dot);
    UI.hapticVibrate(this, false);
    tgx101FlashDot = callback.isPasscodeVisible() ? dot : -1; // with «invisible», no sign lights up under the finger
    tgx101FlashAt = android.os.SystemClock.uptimeMillis();
    invalidate();
    removeCallbacks(tgx101Finish);
    int need = callback.inSetupMode() ? 0 : callback.tgx101SignsLength();
    if (need > 0 && pattern.getSize() >= need) {
      tgx101Submit();
    } else if (need <= 0 && pattern.getSize() >= Passcode.MIN_PATTERN_SIZE) {
      postDelayed(tgx101Finish, 1200);
    }
  }

  private void tgx101Submit () {
    removeCallbacks(tgx101Finish);
    if (pattern == null || pattern.getSize() == 0) return;
    PasscodeBuilder signs = tgx101ToSigns(pattern);
    pattern.clear();
    tgx101FlashDot = -1;
    tgx101Shuffle(); // the next try: the signs in new places
    if (callback != null) callback.handlePattern(signs);
  }

  private void tgx101DrawProgress (Canvas c) {
    if (pattern == null || patternDots == null) return;
    int n = pattern.getSize();
    if (n == 0) return;
    float r = Screen.dp(4.5f), gap = Screen.dp(14f);
    float cy = patternDots[0].getY() - Screen.dp(62f);
    float cx = (patternDots[0].getX() + patternDots[2].getX()) / 2f - (n - 1) * gap / 2f;
    android.graphics.Paint p = Paints.fillingPaint(Theme.getColor(ColorId.passcodeIcon));
    for (int i = 0; i < n; i++) c.drawCircle(cx + i * gap, cy, r, p);
  }

  private boolean tgx101Symbols () {
    return callback != null && callback.tgx101Symbols();
  }

  private int[] tgx101Signs () {
    if (tgx101Signs == null) tgx101Shuffle();
    return tgx101Signs;
  }

  public void tgx101Shuffle () {
    int[] a = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    java.security.SecureRandom random = new java.security.SecureRandom();
    for (int i = a.length - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      int t = a[i]; a[i] = a[j]; a[j] = t;
    }
    tgx101Signs = a;
    invalidate();
  }

  private PasscodeBuilder tgx101ToSigns (PasscodeBuilder dots) {
    int[] signs = tgx101Signs();
    PasscodeBuilder out = new PasscodeBuilder();
    String s = dots.toString();
    for (int i = 0; i < s.length(); i++) out.append(signs[s.charAt(i) - '0']);
    return out;
  }

  private final android.graphics.Path tgx101Path = new android.graphics.Path();

  private void tgx101DrawSign (Canvas c, int sign, float cx, float cy) {
    int color = Theme.getColor(ColorId.passcodeIcon);
    float r = Screen.dp(12f);
    // the faint disc first: fillingPaint is one shared paint — taken for the disc after the sign's, it turned every
    // filled sign faint and only the ring and the frame (their own stroke paint) stood out (user 2026-11-15)
    c.drawCircle(cx, cy, Screen.dp(26f), Paints.fillingPaint(ColorUtils.alphaColor(.08f, color)));
    android.graphics.Paint fill = Paints.fillingPaint(color);
    android.graphics.Paint stroke = Paints.getProgressPaint(color, Screen.dp(3f));
    android.graphics.Path p = tgx101Path;
    p.reset();
    switch (sign) {
      case 0: c.drawCircle(cx, cy, r, fill); return; // circle
      case 1: c.drawRect(cx - r * .85f, cy - r * .85f, cx + r * .85f, cy + r * .85f, fill); return; // square
      case 2: c.drawCircle(cx, cy, r - Screen.dp(1.5f), stroke); return; // ring
      case 3: p.moveTo(cx, cy - r); p.lineTo(cx + r, cy + r * .8f); p.lineTo(cx - r, cy + r * .8f); break; // triangle
      case 4: p.moveTo(cx, cy - r); p.lineTo(cx + r, cy); p.lineTo(cx, cy + r); p.lineTo(cx - r, cy); break; // rhombus
      case 5: { // plus
        float w = r * .36f;
        c.drawRect(cx - w, cy - r, cx + w, cy + r, fill);
        c.drawRect(cx - r, cy - w, cx + r, cy + w, fill);
        return;
      }
      case 6: c.drawRect(cx - r * .8f, cy - r * .8f, cx + r * .8f, cy + r * .8f, stroke); return; // frame
      case 7: p.moveTo(cx, cy + r); p.lineTo(cx + r, cy - r * .8f); p.lineTo(cx - r, cy - r * .8f); break; // triangle down
      default: { // star
        for (int i = 0; i < 10; i++) {
          double a = Math.PI / 2 + i * Math.PI / 5;
          float rr = i % 2 == 0 ? r * 1.1f : r * .45f;
          float x = cx + (float) (Math.cos(a) * rr), y = cy - (float) (Math.sin(a) * rr);
          if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        break;
      }
    }
    p.close();
    c.drawPath(p, fill);
  }

  private PincodeOutputView pincodeView;

  private int tgx101PinLength = Passcode.PINCODE_SIZE;

  public void tgx101SetPinLength (int length) {
    tgx101PinLength = length;
    if (pincodeView != null) pincodeView.tgx101SetLength(length);
  }

  private void layoutPincode () {
    if (pincodeView == null) {
      pincodeView = new PincodeOutputView();
      pincodeView.setParentView(this);
      pincodeView.tgx101SetLength(tgx101PinLength);
    }
    pincodeView.setRect(lineLeft, lineTop - Screen.dp(52f) - 1, lineRight, lineTop - 1);
  }

  private void drawPincode (Canvas c) {
    pincodeView.draw(c);
  }

  public PincodeOutputView getPincodeOutput () {
    return pincodeView;
  }

  public Callback getCallback () {
    return callback;
  }

  public interface Callback {
    boolean isPasscodeVisible ();
    default boolean tgx101Symbols () { return false; }
    default int tgx101SignsLength () { return 0; }
    void updateConfirmMode ();
    void handlePattern (PasscodeBuilder pattern);
    void handlePincode (PasscodeBuilder pincode);
    boolean inSetupMode ();
    String makeBruteForceSuffix ();
  }
}
