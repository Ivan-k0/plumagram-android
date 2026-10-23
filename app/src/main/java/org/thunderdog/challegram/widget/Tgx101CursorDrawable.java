/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/plumagram-android)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.widget;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

/**
 * TGx101 (user 2026-10-23 «мигающая полоска не наезжает на первую букву, отодвинь, сделай не такой острой»): the text
 * cursor of every field — 1.5 dp, a hair right of the caret, so it never touches the letter just typed (left of the
 * caret it was clipped away at the field's start and covered that letter), round ends, one height whatever the line
 * holds, in the colour of the send arrow (read at draw time: follows the theme), like the mic. The chat field's own
 * placeholder moves right by {@link #hintRoom()} so the cursor of an empty field doesn't cover its first letter.
 */
public final class Tgx101CursorDrawable extends Drawable {
  private static final float WIDTH = 1.5f, GAP = .5f, SHORTER = 1.5f; // as in the official app: 1.5 dp (user 2026-10-23 «сильно толстая»)

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final int fixedColor; // 0: the send arrow's colour of the current theme
  private final java.lang.ref.WeakReference<EditText> field;

  private Tgx101CursorDrawable (EditText field, int fixedColor) {
    this.field = new java.lang.ref.WeakReference<>(field);
    this.fixedColor = fixedColor;
  }

  /** Android 10+ lets a field have its own cursor drawable; older ones keep the system cursor */
  public static void apply (EditText e) {
    apply(e, 0);
  }

  /** a header field: its own white / grey on the coloured header, the same shape */
  public static void apply (EditText e, int fixedColor) {
    if (android.os.Build.VERSION.SDK_INT >= 29) {
      e.setTextCursorDrawable(new Tgx101CursorDrawable(e, fixedColor));
    }
  }

  @Override
  public void draw (@NonNull Canvas c) {
    Rect b = getBounds();
    float inset = Screen.dp(SHORTER);
    // (user 2026-10-23 «в оф. телеге курсор что без текста, что с текстом одного размера»): the height comes from the
    // field's font, not from the line — an empty line (the hint) and a line with emoji were taller or shorter
    EditText e = field.get();
    float height = b.height() - inset * 2;
    if (e != null) height = Math.min(height, e.getTextSize() * 1.25f);
    float cy = b.exactCenterY();
    float left = b.left + Screen.dp(GAP);
    rect.set(left, cy - height / 2f, left + Screen.dp(WIDTH), cy + height / 2f);
    float radius = rect.width() / 2f;
    paint.setColor(fixedColor != 0 ? fixedColor : Theme.chatSendButtonColor());
    c.drawRoundRect(rect, radius, radius, paint);
  }

  @Override
  public int getIntrinsicWidth () {
    return Screen.dp(GAP) + Screen.dp(WIDTH);
  }

  /** marks a hint already moved off the field's start (so it's done once) */
  public static final class HintRoom extends android.text.style.LeadingMarginSpan.Standard {
    public HintRoom (int room) {
      super(room);
    }
  }

  /** how far a field's own placeholder stands off its start while this cursor is in use: past the bar and a gap */
  public static int hintRoom () {
    return android.os.Build.VERSION.SDK_INT >= 29 ? Screen.dp(GAP) * 2 + Screen.dp(WIDTH) : 0;
  }

  @Override
  public void setAlpha (int alpha) {
    paint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter (@Nullable ColorFilter colorFilter) {
    paint.setColorFilter(colorFilter);
  }

  @Override
  public int getOpacity () {
    return PixelFormat.TRANSLUCENT;
  }
}
