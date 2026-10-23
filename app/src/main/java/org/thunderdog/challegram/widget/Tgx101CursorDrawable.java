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
 * cursor of every field — centred on the caret instead of starting on it (it covered the next letter's edge), round
 * ends, a little shorter than the line, in the colour of the send arrow (read at draw time: follows the theme).
 */
public final class Tgx101CursorDrawable extends Drawable {
  private static final float WIDTH = 2f, SHORTER = 1.5f;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();

  /** Android 10+ lets a field have its own cursor drawable; older ones keep the system cursor */
  public static void apply (EditText e) {
    if (android.os.Build.VERSION.SDK_INT >= 29) {
      e.setTextCursorDrawable(new Tgx101CursorDrawable());
    }
  }

  @Override
  public void draw (@NonNull Canvas c) {
    Rect b = getBounds();
    float inset = Screen.dp(SHORTER);
    rect.set(b.left, b.top + inset, b.right, b.bottom - inset);
    float radius = rect.width() / 2f;
    paint.setColor(Theme.chatSendButtonColor());
    c.drawRoundRect(rect, radius, radius, paint);
  }

  /** half its width to the left: the caret's line runs through the bar's middle, between the two letters */
  @Override
  public boolean getPadding (@NonNull Rect padding) {
    padding.set(Screen.dp(WIDTH) / 2, 0, 0, 0);
    return true;
  }

  @Override
  public int getIntrinsicWidth () {
    return Screen.dp(WIDTH);
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
