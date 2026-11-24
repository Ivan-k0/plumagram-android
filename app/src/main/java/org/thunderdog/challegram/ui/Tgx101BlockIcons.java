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
package org.thunderdog.challegram.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101 (user 2026-11-24 «настройку меню с блоками по макету не вижу»): the row of the «blocks» menu's icons on the
 * screen of the menu's items — a tap picks another action in a sheet (the ones in the row greyed), a hold and drag
 * moves an icon along the row.
 */
public final class Tgx101BlockIcons {
  private Tgx101BlockIcons () { }

  static int place (int itemId) {
    for (int k = 0; k < Tgx101MessageMenu.ORDERABLE_IDS.length; k++) if (Tgx101MessageMenu.ORDERABLE_IDS[k] == itemId) return k;
    return -1;
  }

  static void save (List<Integer> ids) {
    StringBuilder places = new StringBuilder();
    for (int itemId : ids) {
      int k = place(itemId);
      if (k < 0) continue;
      if (places.length() > 0) places.append(',');
      places.append(k);
    }
    Settings.instance().setTgx101MessageMenuBlockIcons(places.toString());
  }

  /** the row, as wide as its parent; {@code changed} runs after a pick or a move */
  public static View row (ViewController<?> c, Runnable changed) {
    final List<Integer> ids = new ArrayList<>();
    for (int itemId : Tgx101MessageMenu.blockIconIds()) ids.add(itemId);
    LinearLayout row = new LinearLayout(c.context());
    row.setOrientation(LinearLayout.HORIZONTAL);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Theme.fillingColor());
    bg.setCornerRadius(Screen.dp(16f));
    row.setBackground(bg);
    for (int place = 0; place < Tgx101MessageMenu.MAX_BLOCK_ICONS; place++) {
      final int slot = place;
      ImageView b = new ImageView(c.context());
      b.setScaleType(ImageView.ScaleType.CENTER);
      if (slot < ids.size()) {
        int k = place(ids.get(slot));
        b.setImageResource(Tgx101MessageMenu.outlined(Tgx101MessageMenu.ORDERABLE_ICONS[k]));
        b.setColorFilter(Theme.getColor(ColorId.text));
        b.setContentDescription(Lang.getString(Tgx101MessageMenu.ORDERABLE_NAMES[k]));
      } else {
        b.setImageResource(R.drawable.baseline_add_24);
        b.setColorFilter(Theme.getColor(ColorId.icon));
      }
      org.thunderdog.challegram.tool.Views.setClickable(b);
      org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(b);
      b.setOnClickListener(v -> pick(c, ids, Math.min(slot, ids.size()), changed));
      if (slot < ids.size()) {
        final float[] drag = {Float.NaN, 0f};
        b.setOnLongClickListener(v -> {
          drag[0] = drag[1];
          if (android.os.Build.VERSION.SDK_INT >= 21) v.setElevation(Screen.dp(6f));
          v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start();
          UI.forceVibrate(v, false);
          return true;
        });
        b.setOnTouchListener((v, e) -> {
          switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
              drag[1] = e.getRawX();
              drag[0] = Float.NaN;
              break;
            case MotionEvent.ACTION_MOVE:
              if (!Float.isNaN(drag[0])) {
                v.setTranslationX(e.getRawX() - drag[0]);
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
              }
              break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
              if (!Float.isNaN(drag[0])) {
                float dx = e.getRawX() - drag[0];
                int to = Math.max(0, Math.min(ids.size() - 1, slot + Math.round(dx / Math.max(1, v.getWidth()))));
                drag[0] = Float.NaN;
                v.animate().scaleX(1f).scaleY(1f).translationX(0f).setDuration(120).start();
                if (android.os.Build.VERSION.SDK_INT >= 21) v.setElevation(0f);
                if (to != slot) {
                  Integer moved = ids.remove(slot);
                  ids.add(to, moved);
                  save(ids);
                  changed.run();
                }
                return true;
              }
              break;
          }
          return false;
        });
      }
      row.addView(b, new LinearLayout.LayoutParams(0, Screen.dp(56f), 1f));
    }
    return row;
  }

  private static void pick (ViewController<?> c, List<Integer> ids, int slot, Runnable changed) {
    final Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(c.context(), Lang.getString(R.string.Tgx101MessageMenuBlockIcons) + " · " + (slot + 1)));
      final Integer current = slot < ids.size() ? ids.get(slot) : null;
      for (int k = 0; k < Tgx101MessageMenu.ORDERABLE_IDS.length; k++) {
        final int itemId = Tgx101MessageMenu.ORDERABLE_IDS[k];
        if (itemId == R.id.btn_messageMore || itemId == R.id.btn_tgx101SelectInPlace) continue;
        boolean used = ids.contains(itemId) && (current == null || current != itemId);
        boolean isCurrent = current != null && current == itemId;
        View r = Tgx101Sheet.row(c.context(), Tgx101MessageMenu.outlined(Tgx101MessageMenu.ORDERABLE_ICONS[k]), 0, Lang.getString(Tgx101MessageMenu.ORDERABLE_NAMES[k]),
          used ? Lang.getString(R.string.Tgx101InRow) : null, isCurrent, used ? null : v -> {
            if (slot < ids.size()) ids.set(slot, itemId); else ids.add(itemId);
            save(ids);
            if (h[0] != null) h[0].dismiss();
            changed.run();
          });
        if (used) {
          r.setAlpha(.4f);
          r.setClickable(false);
        }
        body.addView(r);
      }
    });
  }

  /** for the sample: the row's icons */
  static List<Integer> ids () {
    List<Integer> out = new ArrayList<>();
    for (int itemId : Tgx101MessageMenu.blockIconIds()) out.add(itemId);
    return out;
  }

  static ViewGroup.LayoutParams params () {
    return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
  }
}
