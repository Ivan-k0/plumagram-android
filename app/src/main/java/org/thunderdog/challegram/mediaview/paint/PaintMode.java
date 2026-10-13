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
 * File created on 11/06/2017
 */
package org.thunderdog.challegram.mediaview.paint;

public class PaintMode {
  public static final int NONE = -1;

  public static final int PATH = 1;
  public static final int ARROW = 2;
  public static final int RECTANGLE = 3;
  // TGx101 (editor mockup variant Б): more shapes, and the brush kind of a free stroke
  public static final int ELLIPSE = 4;
  public static final int LINE = 5;

  /** The kind of a free stroke (SimpleDrawing.STYLE_*): pen, marker, pencil, neon, free arrow, eraser */
  public static int brushStyle = SimpleDrawing.STYLE_PEN;
  /** Pixel blur strength 1…5 */
  public static int mosaicStrength = 3;

  public static final int FREE_MOVEMENT = 100;

  public static void save (int mode) {
    /*if (mode != FREE_MOVEMENT) {
      mode = clean(mode);
      if (mode == PATH) {
        Prefs.instance().remove("paint_mode");
      } else {
        Prefs.instance().putInt("paint_mode", mode);
      }
    }*/
  }

  public static int restore () {
    /*int mode = Prefs.instance().getInt("paint_mode", PATH);
    return clean(mode);*/
    return PATH;
  }

  public static int clean (int mode) {
    switch (mode) {
      case PATH:
      case ARROW:
      case RECTANGLE:
      case ELLIPSE:
      case LINE:
        return mode;
    }
    return PATH;
  }
}
