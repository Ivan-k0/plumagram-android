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
 * File created on 01/07/2024
 */
package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnticipateOvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.N;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.Waveform;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.data.TGRecord;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

/**
 * The paused voice note between «Delete» and «Send».
 * TGx101 (user 2026-11-12, mockup «Голосовое: прослушка и обрезка», variant А): ▶ / pause to listen, the yellow edges to
 * trim it, the time, and the scissors that open the editor for cutting pieces out (variant Б, {@link Tgx101VoiceCutSheet}).
 */
public class VoiceWaveformView extends View implements FactorAnimator.Target, Tgx101VoiceEdit.Listener {
  public interface Callback {
    void onVoiceScissors (Tgx101VoiceEdit edit);
  }

  private final Paint textPaint;
  private final int textOffset, textRight;
  private final int waveLeft;

  private final Waveform waveform;

  private @Nullable Callback callback;

  public VoiceWaveformView (Context context) {
    super(context);

    textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    textPaint.setTypeface(Fonts.getRobotoRegular());
    textPaint.setTextSize(Screen.dp(15f));
    textOffset = Screen.dp(5f);
    textRight = Screen.dp(39f);
    waveLeft = Screen.dp(10f);

    waveform = new Waveform(null, Waveform.MODE_RECT, false);
  }

  public void setCallback (@Nullable Callback callback) {
    this.callback = callback;
  }

  public void clearData () {
    waveform.setData(null);
    setEdit(null);
    record = null;
    invalidate();
  }

  private TGRecord record;
  private String seekStr;

  private @Nullable Tgx101VoiceEdit edit;
  private int loadGeneration;

  public @Nullable Tgx101VoiceEdit getEdit () {
    return edit;
  }

  private void setEdit (@Nullable Tgx101VoiceEdit e) {
    if (this.edit == e) return;
    if (this.edit != null) {
      this.edit.release();
    }
    this.edit = e;
    if (e != null) e.addListener(this);
    invalidate();
  }

  /** stops listening (sending, deleting, recording again) */
  public void stopPlayback () {
    if (edit != null) edit.stop();
  }

  public void processRecord (final TGRecord record) {
    this.record = record;
    this.seekStr = Strings.buildDuration(record.getDuration());
    final Tgx101VoiceEdit previous = edit;
    if (previous != null) previous.stop();
    final int gen = ++loadGeneration;

    Background.instance().post(() -> {
      final byte[] waveform = record.getWaveform() != null ? record.getWaveform() : N.getWaveform(record.getPath());
      if (waveform != null) {
        UI.post(() -> setWaveform(record, waveform));
      }
      // TGx101: decoded for listening and editing
      final Tgx101VoiceEdit loaded = Tgx101VoiceEdit.load(record.getPath());
      if (loaded != null) loaded.takeEditsFrom(previous);
      UI.post(() -> {
        if (gen != loadGeneration || this.record != record) {
          if (loaded != null) loaded.release();
          return;
        }
        setEdit(loaded);
      });
    });
  }

  private final FactorAnimator waveformAnimator = new FactorAnimator(0, this, overshoot, OPEN_DURATION);

  private void setWaveform (final TGRecord record, byte[] waveform) {
    if (this.record == null || !this.record.equals(record)) {
      return;
    }
    record.setWaveform(waveform);
    this.waveform.setData(waveform);
    waveformAnimator.forceFactor(0f);
    waveformAnimator.setStartDelay(80L);
    waveformAnimator.animateTo(1f);
    invalidate();
  }

  private float expand = 1f;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    waveform.setExpand(factor);
    expand = factor;
    invalidate();
  }

  @Override
  public void onVoiceEditChanged () {
    invalidate();
  }

  private int calculateWaveformWidth () {
    return getMeasuredWidth() - waveLeft - Screen.dp(110f) + Screen.dp(55f);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (getMeasuredWidth() != 0) {
      waveform.layout(calculateWaveformWidth());
    }
  }

  // TGx101 layout: [▶][ wave with the yellow edges ][time][✂]

  private static final float PLAY_RADIUS = 15f, SCISSORS_WIDTH = 36f, HANDLE_WIDTH = 6f, BAR = 2f, BAR_GAP = 2f;
  private static final int EDGE_COLOR = 0xfff2c14e;

  private float playCx () {
    return Screen.dp(PLAY_RADIUS + 2f);
  }

  private float waveStart () {
    return Screen.dp(PLAY_RADIUS * 2 + 12f);
  }

  private float waveEnd () {
    return getMeasuredWidth() - Screen.dp(SCISSORS_WIDTH) - timeWidth() - Screen.dp(8f);
  }

  private float timeWidth () {
    return textPaint.measureText(edit != null && edit.isPlaying() ? "0:00 / 0:00" : "0:00") + Screen.dp(4f);
  }

  private float xOf (int shown) {
    float s = waveStart(), e = waveEnd();
    int len = edit != null ? Math.max(1, edit.shownLength()) : 1;
    return s + (e - s) * shown / (float) len;
  }

  private int shownAt (float x) {
    float s = waveStart(), e = waveEnd();
    int len = edit != null ? edit.shownLength() : 0;
    return Math.round(Math.max(0f, Math.min(1f, (x - s) / Math.max(1f, e - s))) * len);
  }

  private final Path path = new Path();
  private final RectF rect = new RectF();

  @Override
  protected void onDraw (@NonNull Canvas c) {
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    int centerY = (int) ((float) height * .5f);
    final Tgx101VoiceEdit edit = this.edit;

    if (edit == null) {
      if (seekStr != null) {
        textPaint.setColor(Theme.textAccentColor());
        c.drawText(seekStr, width - textRight, centerY + textOffset, textPaint);
      }
      waveform.draw(c, 1f, waveLeft, centerY);
      return;
    }

    final int accent = Theme.getColor(ColorId.waveformActive);
    final int inactive = Theme.getColor(ColorId.waveformInactive);

    // ▶ / pause
    float pcx = playCx();
    c.drawCircle(pcx, centerY, Screen.dp(PLAY_RADIUS), Paints.fillingPaint(accent));
    Paint white = Paints.fillingPaint(0xffffffff);
    if (edit.isPlaying()) {
      float w = Screen.dp(3f), h = Screen.dp(10f), gap = Screen.dp(2.5f);
      rect.set(pcx - gap - w, centerY - h / 2f, pcx - gap, centerY + h / 2f);
      c.drawRoundRect(rect, Screen.dp(1f), Screen.dp(1f), white);
      rect.set(pcx + gap, centerY - h / 2f, pcx + gap + w, centerY + h / 2f);
      c.drawRoundRect(rect, Screen.dp(1f), Screen.dp(1f), white);
    } else {
      float s = Screen.dp(6f);
      path.reset();
      path.moveTo(pcx - s * .6f, centerY - s);
      path.lineTo(pcx + s, centerY);
      path.lineTo(pcx - s * .6f, centerY + s);
      path.close();
      c.drawPath(path, white);
    }

    // the wave: grey outside the edges, the played part in the accent colour
    float ws = waveStart(), we = waveEnd();
    int len = edit.shownLength();
    int ts = edit.trimStartShown(), te = edit.trimEndShown();
    int pos = edit.position();
    float step = Screen.dp(BAR + BAR_GAP);
    int bars = Math.max(1, (int) ((we - ws) / step));
    float maxH = Screen.dp(22f) * expand, minH = Screen.dp(2f);
    for (int i = 0; i < bars; i++) {
      int from = (int) ((long) len * i / bars), to = (int) ((long) len * (i + 1) / bars);
      float p = edit.peak(from, to);
      float h = Math.max(minH, Math.min(maxH, minH + (float) Math.sqrt(p) * maxH));
      float x = ws + i * step;
      int mid = (from + to) / 2;
      int color = mid < ts || mid >= te ? ColorUtils.alphaColor(.35f, inactive) : (mid < pos && (edit.isPlaying() || pos > ts) ? accent : inactive);
      rect.set(x, centerY - h / 2f, x + Screen.dp(BAR), centerY + h / 2f);
      c.drawRoundRect(rect, Screen.dp(1f), Screen.dp(1f), Paints.fillingPaint(color));
    }
    // the cut seams
    for (int seam : edit.seams()) {
      float x = xOf(seam);
      c.drawRect(x - Screen.dp(.75f), centerY - Screen.dp(13f), x + Screen.dp(.75f), centerY + Screen.dp(13f), Paints.fillingPaint(0xffe5533f));
    }
    // the yellow edges
    float hx1 = xOf(ts), hx2 = xOf(te), top = centerY - Screen.dp(16f), bottom = centerY + Screen.dp(16f);
    Paint edge = Paints.fillingPaint(EDGE_COLOR);
    c.drawRect(hx1, top, hx2, top + Screen.dp(2f), edge);
    c.drawRect(hx1, bottom - Screen.dp(2f), hx2, bottom, edge);
    rect.set(hx1 - Screen.dp(HANDLE_WIDTH), top, hx1, bottom);
    c.drawRoundRect(rect, Screen.dp(3f), Screen.dp(3f), edge);
    rect.set(hx2, top, hx2 + Screen.dp(HANDLE_WIDTH), bottom);
    c.drawRoundRect(rect, Screen.dp(3f), Screen.dp(3f), edge);
    // the cursor while listening
    if (edit.isPlaying()) {
      float x = xOf(pos);
      c.drawRect(x - Screen.dp(1f), top + Screen.dp(2f), x + Screen.dp(1f), bottom - Screen.dp(2f), Paints.fillingPaint(Theme.textAccentColor()));
    }

    // the time: the note's length, «heard / length» while listening
    int kept = edit.keptSamples();
    String time = edit.isPlaying()
      ? Strings.buildDuration(edit.keptBefore(pos) / Tgx101VoiceEdit.RATE) + " / " + Strings.buildDuration(Math.round(kept / (float) Tgx101VoiceEdit.RATE))
      : Strings.buildDuration(Math.max(1, Math.round(kept / (float) Tgx101VoiceEdit.RATE)));
    textPaint.setColor(Theme.textAccentColor());
    c.drawText(time, we + Screen.dp(8f), centerY + textOffset, textPaint);

    // ✂
    Drawable scissors = Drawables.get(getResources(), R.drawable.baseline_content_cut_24);
    if (scissors != null) {
      float cx = width - Screen.dp(SCISSORS_WIDTH) / 2f;
      Drawables.draw(c, scissors, cx - scissors.getMinimumWidth() / 2f, centerY - scissors.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(Theme.getColor(ColorId.icon)));
    }
  }

  // Touches

  private static final int TOUCH_NONE = 0, TOUCH_PLAY = 1, TOUCH_SCISSORS = 2, TOUCH_START = 3, TOUCH_END = 4, TOUCH_SEEK = 5;
  private int touch;
  private float downX;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    final Tgx101VoiceEdit edit = this.edit;
    if (edit == null || getAlpha() < 1f) {
      return super.onTouchEvent(e);
    }
    float x = e.getX();
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        downX = x;
        float hx1 = xOf(edit.trimStartShown()), hx2 = xOf(edit.trimEndShown());
        float grab = Screen.dp(18f);
        if (x < waveStart() - Screen.dp(4f)) {
          touch = TOUCH_PLAY;
        } else if (x > getMeasuredWidth() - Screen.dp(SCISSORS_WIDTH) - Screen.dp(4f)) {
          touch = TOUCH_SCISSORS;
        } else if (Math.abs(x - hx1) <= grab && Math.abs(x - hx1) <= Math.abs(x - hx2)) {
          touch = TOUCH_START;
        } else if (Math.abs(x - hx2) <= grab) {
          touch = TOUCH_END;
        } else if (x <= waveEnd() + Screen.dp(4f)) {
          touch = TOUCH_SEEK;
        } else {
          touch = TOUCH_NONE;
          return false;
        }
        if (touch == TOUCH_START || touch == TOUCH_END) {
          edit.stop();
        }
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (touch == TOUCH_START) {
          edit.setTrimShown(shownAt(x), edit.trimEndShown());
          edit.setPosition(edit.trimStartShown());
        } else if (touch == TOUCH_END) {
          edit.setTrimShown(edit.trimStartShown(), shownAt(x));
        } else if (touch == TOUCH_SEEK && Math.abs(x - downX) > Screen.dp(6f)) {
          edit.setPosition(shownAt(x));
        }
        return true;
      }
      case MotionEvent.ACTION_UP: {
        int t = touch;
        touch = TOUCH_NONE;
        if (t == TOUCH_PLAY) {
          performClick();
          if (edit.isPlaying()) edit.pause();
          else edit.playKept();
        } else if (t == TOUCH_SCISSORS) {
          performClick();
          edit.stop();
          if (callback != null) callback.onVoiceScissors(edit);
        } else if (t == TOUCH_SEEK) {
          edit.setPosition(shownAt(x));
        } else if (t == TOUCH_START || t == TOUCH_END) {
          org.thunderdog.challegram.Tgx101Diag.mark("voice edit: edges " + (edit.trimStartShown() * 1000L / Tgx101VoiceEdit.RATE) + "–" + (edit.trimEndShown() * 1000L / Tgx101VoiceEdit.RATE) + " ms of " + (edit.shownLength() * 1000L / Tgx101VoiceEdit.RATE));
        }
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        touch = TOUCH_NONE;
        return true;
      }
    }
    return true;
  }

  @Override
  public boolean performClick () {
    return super.performClick();
  }

  private static final AnticipateOvershootInterpolator overshoot = new AnticipateOvershootInterpolator(3.0f);

  private static final long OPEN_DURATION = 350L;
}
