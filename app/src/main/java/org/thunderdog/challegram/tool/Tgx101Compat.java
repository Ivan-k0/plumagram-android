/*
 * This file is a part of PlumaGram
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
package org.thunderdog.challegram.tool;

import android.graphics.Canvas;
import android.os.Build;
import android.widget.GridLayout;

/** TGx101: calls that are Android 5+ in their short form — the Android 4 build (minSdk 16) crashed on them */
public final class Tgx101Compat {
  private Tgx101Compat () { }

  @SuppressWarnings("deprecation")
  public static int saveLayer (Canvas c, float left, float top, float right, float bottom) {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
      ? c.saveLayer(left, top, right, bottom, null)
      : c.saveLayer(left, top, right, bottom, null, Canvas.ALL_SAVE_FLAG);
  }

  @SuppressWarnings("deprecation")
  public static int saveLayerAlpha (Canvas c, float left, float top, float right, float bottom, int alpha) {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
      ? c.saveLayerAlpha(left, top, right, bottom, alpha)
      : c.saveLayerAlpha(left, top, right, bottom, alpha, Canvas.ALL_SAVE_FLAG);
  }

  /** Equal-width column: by weight on Android 5+; before that GridLayout has no weights — the cell fills its column */
  public static void weightedColumn (GridLayout.LayoutParams lp) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      lp.width = 0;
      lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
    } else {
      lp.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
      lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL);
    }
  }
}
