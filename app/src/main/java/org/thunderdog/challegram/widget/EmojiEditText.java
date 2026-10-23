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
 * File created on 31/08/2022, 22:07.
 */

package org.thunderdog.challegram.widget;

import android.content.Context;
import android.os.Build;
import android.text.InputFilter;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import org.thunderdog.challegram.emoji.EmojiInputConnection;
import org.thunderdog.challegram.emoji.EmojiUpdater;
import org.thunderdog.challegram.util.DestroySpansWatcher;
import org.thunderdog.challegram.v.EditText;

import me.vkryl.core.lambda.Destroyable;

public class EmojiEditText extends EditText implements Destroyable {
  private EmojiUpdater emojiUpdater;

  public EmojiEditText (Context context) {
    super(context);
    init();
  }

  public EmojiEditText (Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public EmojiEditText (Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init () {
    Tgx101CursorDrawable.apply(this); // one cursor for every field (a header field puts its own white / grey on top)
    tgx101Highlight = getHighlightColor();
    super.setHighlightColor(0); // the selection is drawn here — see tgx101DrawSelection
    addTextChangedListener(new DestroySpansWatcher());
    setFilters(new InputFilter[0]);
  }
  /** (user 2026-10-23 «в поисках, хранилище по прежнему наезжает — по всему приложению»): a field's hint stands off its
   *  start by the cursor's room, so the cursor of an empty field doesn't cover the hint's first letter */
  // TGx101 (user 2026-10-23 «выделение текста — визуально хорошо, как в exteraGram: если текста много, он выделяется
  // равномерным квадратом»): the selected lines make one block with rounded corners — the first from the selection's start
  // to the right edge, the middle ones edge to edge, the last from the left edge to its end — not the system's ragged
  // strips, each as long as its line
  private int tgx101Highlight;
  private final android.graphics.Path tgx101SelectionPath = new android.graphics.Path();
  private final android.graphics.Path tgx101LinePath = new android.graphics.Path();
  private android.graphics.Paint tgx101SelectionPaint;

  @Override
  public void setHighlightColor (int color) {
    tgx101Highlight = color;
    super.setHighlightColor(0);
    invalidate();
  }

  private void tgx101DrawSelection (android.graphics.Canvas c) {
    int start = getSelectionStart(), end = getSelectionEnd();
    if (start == end || start < 0 || end < 0 || android.graphics.Color.alpha(tgx101Highlight) == 0) return;
    if (start > end) { int t = start; start = end; end = t; }
    android.text.Layout layout = getLayout();
    if (layout == null) return;
    int first = layout.getLineForOffset(start), last = layout.getLineForOffset(end);
    if (last > first && end == layout.getLineStart(last)) last--; // the selection stops at a line's very start
    float minLeft = Float.MAX_VALUE, maxRight = 0;
    for (int line = first; line <= last; line++) {
      minLeft = Math.min(minLeft, layout.getLineLeft(line));
      maxRight = Math.max(maxRight, layout.getLineRight(line));
    }
    int voffset = 0;
    int gravity = getGravity() & android.view.Gravity.VERTICAL_GRAVITY_MASK;
    if (gravity != android.view.Gravity.TOP) {
      int box = getHeight() - getExtendedPaddingTop() - getExtendedPaddingBottom(), text = layout.getHeight();
      if (text < box) voffset = gravity == android.view.Gravity.BOTTOM ? box - text : (box - text) / 2;
    }
    float pad = org.thunderdog.challegram.tool.Screen.dp(2f);
    tgx101SelectionPath.reset();
    for (int line = first; line <= last; line++) {
      float left = line == first ? layout.getPrimaryHorizontal(start) : minLeft;
      float right = line == last ? (end >= layout.getLineEnd(line) && line != layout.getLineCount() - 1 ? maxRight : layout.getPrimaryHorizontal(end)) : maxRight;
      if (line == first && first != last) right = maxRight;
      if (right < left) { float t = left; left = right; right = t; }
      float top = layout.getLineTop(line), bottom = layout.getLineBottom(line);
      if (android.os.Build.VERSION.SDK_INT >= 19) {
        tgx101LinePath.reset();
        tgx101LinePath.addRect(left - pad, top, right + pad, bottom, android.graphics.Path.Direction.CW);
        tgx101SelectionPath.op(tgx101LinePath, android.graphics.Path.Op.UNION);
      } else {
        tgx101SelectionPath.addRect(left - pad, top, right + pad, bottom, android.graphics.Path.Direction.CW);
      }
    }
    if (tgx101SelectionPaint == null) {
      tgx101SelectionPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
      tgx101SelectionPaint.setStyle(android.graphics.Paint.Style.FILL);
      tgx101SelectionPaint.setPathEffect(new android.graphics.CornerPathEffect(org.thunderdog.challegram.tool.Screen.dp(6f)));
    }
    tgx101SelectionPaint.setColor(tgx101Highlight);
    int save = c.save();
    c.translate(getCompoundPaddingLeft(), getExtendedPaddingTop() + voffset);
    c.drawPath(tgx101SelectionPath, tgx101SelectionPaint);
    c.restoreToCount(save);
  }

  @Override
  protected void onDraw (android.graphics.Canvas c) {
    tgx101DrawSelection(c);
    int room = Tgx101CursorDrawable.hintRoom();
    CharSequence hint = getHint();
    if (room > 0 && hint != null && hint.length() > 0
      && !(hint instanceof android.text.Spanned && ((android.text.Spanned) hint).getSpans(0, hint.length(), Tgx101CursorDrawable.HintRoom.class).length > 0)) {
      android.text.SpannableString moved = new android.text.SpannableString(hint);
      moved.setSpan(new Tgx101CursorDrawable.HintRoom(room), 0, moved.length(), android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);
      setHint(moved);
    }
    super.onDraw(c);
  }


  @Override
  public final void setFilters (@NonNull InputFilter[] filters) {
    if (emojiUpdater == null)
      emojiUpdater = new EmojiUpdater(this);
    super.setFilters(EmojiTextView.newFilters(this, filters, emojiUpdater));
  }

  @Override
  @CallSuper
  public void performDestroy () {
    emojiUpdater.performDestroy();
  }

  @Override
  public final InputConnection onCreateInputConnection (EditorInfo editorInfo) {
    InputConnection ic = createInputConnection(editorInfo);
    if (ic != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && !(ic instanceof EmojiInputConnection)) {
      return new EmojiInputConnection(this, ic);
    } else {
      return ic;
    }
  }

  protected InputConnection createInputConnection (EditorInfo editorInfo) {
    return super.onCreateInputConnection(editorInfo);
  }
}
