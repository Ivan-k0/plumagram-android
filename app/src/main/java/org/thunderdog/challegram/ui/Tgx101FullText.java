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
package org.thunderdog.challegram.ui;

import android.text.Layout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.telegram.Tdlib;

/**
 * TGx101 (user rule, again 2026-10-31 «везде, где текст не помещается, долгое удержание должно показывать его
 * полностью»): a hold on a one-line label cut with «…» shows the whole text over it. A label that isn't cut keeps the
 * hold for whatever it does (nothing is taken from it).
 */
public final class Tgx101FullText {
  private Tgx101FullText () { }

  public static boolean isCut (TextView view) {
    Layout layout = view.getLayout();
    if (layout == null) return false;
    for (int i = 0; i < layout.getLineCount(); i++) {
      if (layout.getEllipsisCount(i) > 0) return true;
    }
    return false;
  }

  /** {@code full} — the text to show, when the label holds a shortened form of it; null for the label's own text */
  public static void attach (TextView view, @Nullable Tdlib tdlib, @Nullable CharSequence full) {
    view.setOnLongClickListener(v -> {
      if (!isCut(view) || !(view.getContext() instanceof BaseActivity)) return false;
      CharSequence text = full != null ? full : view.getText();
      ((BaseActivity) view.getContext()).tooltipManager().builder(view).show(tdlib, text.toString()).hideDelayed();
      return true;
    });
  }
}
