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
 * File created on 11/08/2015 at 13:06
 */
package org.thunderdog.challegram.util;

import android.view.View;

public interface OptionDelegate {
  // return true if popup should be closed
  boolean onOptionItemPressed (View optionItemView, int id);

  /** TGx101 (user 2026-11-20 «по удержанию»): a hold on an item; true — handled (the menu closes) */
  default boolean onOptionItemLongPressed (android.view.View optionItemView, int id) {
    return false;
  }

  default Object getTagForItem (int position) {
    return null;
  }
  default boolean disableCancelOnTouchdown () { return false; }
}
