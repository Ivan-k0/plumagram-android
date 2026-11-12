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

  private final RectF capsule = new RectF();

  private float barLeft () {
    return Screen.dp(2f);
  }

  private float barRight () {
    return getMeasuredWidth() - Screen.dp(SCISSORS_WIDTH) - Screen.dp(4f);
  }

  private float waveStart () {
    return barLeft() + Screen.dp(14f);
  }

  private float waveEnd () {
    return barRight() - Screen.dp(14f);
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

    // TGx101 (user 2026-11-12, a reference screenshot «смотри как грамотно расположены цифры, плэй и пауза тоже на полосе»):
    // the wave lies in a rounded bar of the buttons' colour, the heard part darker; ▶ / ❚❚ and the time sit together in a
    // capsule in the middle of the bar; the scissors stand beside it
    final int primary = Theme.getColor(ColorId.circleButtonRegular);
    final int onPrimary = Theme.getColor(ColorId.circleButtonRegularIcon);
    float bl = barLeft(), br = barRight();
    float bh = Screen.dp(36f);
    rect.set(bl, centerY - bh / 2f, br, centerY + bh / 2f);
    c.drawRoundRect(rect, bh / 2f, bh / 2f, Paints.fillingPaint(ColorUtils.alphaColor(.22f, primary)));
    int len = edit.shownLength();
    int ts = edit.trimStartShown(), te = edit.trimEndShown();
    int pos = edit.position();
    float xp = xOf(pos);
    if (pos > ts || edit.isPlaying()) {
      c.save();
      c.clipRect(bl, centerY - bh / 2f, Math.max(bl, xp), centerY + bh / 2f);
      c.drawRoundRect(rect, bh / 2f, bh / 2f, Paints.fillingPaint(ColorUtils.alphaColor(.55f, primary)));
      c.restore();
    }
    float ws = waveStart(), we = waveEnd();
    float step = Screen.dp(BAR + BAR_GAP);
    int bars = Math.max(1, (int) ((we - ws) / step));
    float maxH = Screen.dp(20f) * expand, minH = Screen.dp(2f);
    int barColor = ColorUtils.alphaColor(.85f, onPrimary == 0 ? 0xffffffff : onPrimary);
    for (int i = 0; i < bars; i++) {
      int from = (int) ((long) len * i / bars), to = (int) ((long) len * (i + 1) / bars);
      float p = edit.peak(from, to);
      float h = Math.max(minH, Math.min(maxH, minH + (float) Math.sqrt(p) * maxH));
      float x = ws + i * step;
      int mid = (from + to) / 2;
      int color = mid < ts || mid >= te ? ColorUtils.alphaColor(.3f, barColor) : barColor;
      rect.set(x, centerY - h / 2f, x + Screen.dp(BAR), centerY + h / 2f);
      c.drawRoundRect(rect, Screen.dp(1f), Screen.dp(1f), Paints.fillingPaint(color));
    }
    for (int seam : edit.seams()) {
      float x = xOf(seam);
      c.drawRect(x - Screen.dp(.75f), centerY - Screen.dp(12f), x + Screen.dp(.75f), centerY + Screen.dp(12f), Paints.fillingPaint(0xffe5533f));
    }
    if (pos > ts || edit.isPlaying()) {
      c.drawRect(xp - Screen.dp(1f), centerY - bh / 2f, xp + Screen.dp(1f), centerY + bh / 2f, Paints.fillingPaint(0xffffffff));
    }

    // the capsule: ▶ / ❚❚ and the time — the note's length, the heard part while listening
    int kept = edit.keptSamples();
    String time = edit.isPlaying()
      ? Strings.buildDuration(edit.keptBefore(pos) / Tgx101VoiceEdit.RATE)
      : Strings.buildDuration(Math.max(1, Math.round(kept / (float) Tgx101VoiceEdit.RATE)));
    textPaint.setColor(onPrimary);
    float tw = textPaint.measureText(time);
    float icon = Screen.dp(12f), padX = Screen.dp(10f), gap = Screen.dp(6f);
    float cw = padX + icon + gap + tw + padX, ch = Screen.dp(26f);
    float cx = (bl + br) / 2f;
    capsule.set(cx - cw / 2f, centerY - ch / 2f, cx + cw / 2f, centerY + ch / 2f);
    c.drawRoundRect(capsule, ch / 2f, ch / 2f, Paints.fillingPaint(primary));
    float ix = capsule.left + padX, icy = centerY;
    Paint on = Paints.fillingPaint(onPrimary);
    if (edit.isPlaying()) {
      float w = Screen.dp(3.5f), hh = Screen.dp(11f);
      rect.set(ix + Screen.dp(1f), icy - hh / 2f, ix + Screen.dp(1f) + w, icy + hh / 2f);
      c.drawRoundRect(rect, Screen.dp(1f), Screen.dp(1f), on);
      rect.set(ix + icon - Screen.dp(1f) - w, icy - hh / 2f, ix + icon - Screen.dp(1f), icy + hh / 2f);
      c.drawRoundRect(rect, Screen.dp(1f), Screen.dp(1f), on);
    } else {
      path.reset();
      path.moveTo(ix + Screen.dp(1f), icy - Screen.dp(6f));
      path.lineTo(ix + icon, icy);
      path.lineTo(ix + Screen.dp(1f), icy + Screen.dp(6f));
      path.close();
      c.drawPath(path, on);
    }
    c.drawText(time, ix + icon + gap, centerY + textOffset, textPaint);

    // ✂
    Drawable scissors = Drawables.get(getResources(), R.drawable.baseline_content_cut_24);
    if (scissors != null) {
      float sx = width - Screen.dp(SCISSORS_WIDTH) / 2f;
      Drawables.draw(c, scissors, sx - scissors.getMinimumWidth() / 2f, centerY - scissors.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(Theme.getColor(ColorId.icon)));
    }
  }

  // Touches

  private static final int TOUCH_NONE = 0, TOUCH_PLAY = 1, TOUCH_SCISSORS = 2, TOUCH_START = 3, TOUCH_END = 4, TOUCH_SEEK = 5;
  private int touch;
  private float downX;
  // TGx101 (user video 2026-11-12 20:05: swipes along the bar that began on the capsule played and stopped the note at
  // each lift — a moved finger seeks, wherever it began; listening holds while dragging and goes on from the new place)
  private boolean dragging, resumeAfterDrag;

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
        dragging = false;
        resumeAfterDrag = false;
        if (x >= capsule.left - Screen.dp(10f) && x <= capsule.right + Screen.dp(10f)) {
          touch = TOUCH_PLAY;
        } else if (x > barRight()) {
          touch = TOUCH_SCISSORS;
        } else if (x >= barLeft()) {
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
        } else if ((touch == TOUCH_SEEK || touch == TOUCH_PLAY) && (dragging || Math.abs(x - downX) > Screen.dp(8f))) {
          if (!dragging) {
            dragging = true;
            touch = TOUCH_SEEK;
            resumeAfterDrag = edit.isPlaying();
            edit.pause();
          }
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
          boolean wasPlaying = edit.isPlaying();
          if (wasPlaying) edit.pause();
          edit.setPosition(shownAt(x));
          if (resumeAfterDrag || wasPlaying) edit.playKept();
          dragging = resumeAfterDrag = false;
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
