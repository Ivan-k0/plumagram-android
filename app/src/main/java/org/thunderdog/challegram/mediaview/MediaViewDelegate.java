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
 * File created on 09/12/2016
 */
package org.thunderdog.challegram.mediaview;

import org.thunderdog.challegram.mediaview.data.MediaItem;

public interface MediaViewDelegate {
  MediaViewThumbLocation getTargetLocation (int indexInStack, MediaItem item); // null if item is not presented on screen
  void setMediaItemVisible (int index, MediaItem item, boolean isVisible); // called when opening and closing photo viewer
  /** TGx101 (user 2026-10-12): the viewer paged to {@code item} — the source quietly brings it on screen behind the
   *  viewer, so closing goes back into that post / cell and the place where the user stopped */
  default void tgx101OnMediaFocused (int index, MediaItem item) { }
}
