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
 * File created on 11/05/2019
 */
package org.thunderdog.challegram.emoji;

import android.os.Build;
import android.text.Editable;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.TextView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

public class EmojiInputConnection extends InputConnectionWrapper {
  private final TextView mTextView;

  public EmojiInputConnection (
    @NonNull final TextView textView,
    @NonNull final InputConnection inputConnection) {
    super(inputConnection, false);
    mTextView = textView;
  }

  // TGx101 diagnostics (user 2026-10-07 «баг при выборе т9 в режиме редактирования»): what the keyboard does with the
  // field — lengths and positions only, never the text
  private void tgx101Ime (String what) {
    if (org.thunderdog.challegram.BuildConfig.TGX101_DIAG) {
      org.thunderdog.challegram.Tgx101Diag.mark("ime " + what + " (len " + mTextView.length() + ", sel " + mTextView.getSelectionStart() + "-" + mTextView.getSelectionEnd() + ")");
    }
  }

  @Override
  public boolean commitText (CharSequence text, int newCursorPosition) {
    tgx101Ime("commitText " + (text != null ? text.length() : -1) + " cursor " + newCursorPosition);
    return super.commitText(text, newCursorPosition);
  }

  @Override
  public boolean setComposingText (CharSequence text, int newCursorPosition) {
    tgx101Ime("setComposingText " + (text != null ? text.length() : -1) + " cursor " + newCursorPosition);
    return super.setComposingText(text, newCursorPosition);
  }

  @Override
  public boolean setComposingRegion (int start, int end) {
    tgx101Ime("setComposingRegion " + start + "-" + end);
    return super.setComposingRegion(start, end);
  }

  @Override
  public boolean finishComposingText () {
    tgx101Ime("finishComposingText");
    return super.finishComposingText();
  }

  @Override
  public boolean setSelection (int start, int end) {
    tgx101Ime("setSelection " + start + "-" + end);
    return super.setSelection(start, end);
  }

  @Override
  public boolean sendKeyEvent (android.view.KeyEvent event) {
    tgx101Ime("sendKeyEvent " + event.getKeyCode() + " action " + event.getAction());
    return super.sendKeyEvent(event);
  }

  @Override
  public boolean performEditorAction (int editorAction) {
    tgx101Ime("performEditorAction " + editorAction);
    return super.performEditorAction(editorAction);
  }

  @Override
  public boolean commitCorrection (android.view.inputmethod.CorrectionInfo correctionInfo) {
    tgx101Ime("commitCorrection");
    return super.commitCorrection(correctionInfo);
  }

  @Override
  public boolean replaceText (int start, int end, @NonNull CharSequence text, int newCursorPosition, android.view.inputmethod.TextAttribute textAttribute) {
    tgx101Ime("replaceText " + start + "-" + end + " → " + text.length());
    return super.replaceText(start, end, text, newCursorPosition, textAttribute);
  }

  @Override
  public boolean deleteSurroundingText(final int beforeLength, final int afterLength) {
    tgx101Ime("deleteSurroundingText " + beforeLength + " " + afterLength);
    final boolean result = handleDeleteSurroundingText(this, getEditable(),
      beforeLength, afterLength, false /*inCodePoints*/);
    return result || super.deleteSurroundingText(beforeLength, afterLength);
  }

  @Override
  public boolean deleteSurroundingTextInCodePoints(final int beforeLength,
                                                   final int afterLength) {
    final boolean result = handleDeleteSurroundingText(this, getEditable(),
      beforeLength, afterLength, true /*inCodePoints*/);
    return result || super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
  }

  private Editable getEditable() {
    return mTextView.getEditableText();
  }

  private static boolean handleDeleteSurroundingText(
    @NonNull final InputConnection inputConnection, @NonNull final Editable editable,
    @IntRange(from = 0) final int beforeLength, @IntRange(from = 0) final int afterLength,
    final boolean inCodePoints) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      return EmojiUtils.handleDeleteSurroundingText(inputConnection, editable,
        beforeLength, afterLength, inCodePoints);
    } else {
      return false;
    }
  }
}
