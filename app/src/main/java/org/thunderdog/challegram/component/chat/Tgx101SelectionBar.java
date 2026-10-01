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
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.Tgx101FormatMenuController;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.ColorUtils;

/**
 * TGx101: the text selection bar of the message input (mockup B3) — a fixed-width card instead of the system
 * floating toolbar whose width jumped with its ◀ ▶ pages. The top row scrolls sideways: Cut, Copy, Paste,
 * Select all, then every item the system or other apps added (Translate, Web search, Share…); the bottom row
 * holds the formatting icons in the user's order. The system items stay in the action mode's menu (hidden) and
 * are invoked from there, so they keep working exactly as in the system toolbar.
 */
final class Tgx101SelectionBar {
  private final InputView input;
  private PopupWindow window;
  private Menu menu;

  Tgx101SelectionBar (InputView input) {
    this.input = input;
  }

  private static boolean isStandard (int id) {
    return id == android.R.id.cut || id == android.R.id.copy || id == android.R.id.paste || id == android.R.id.selectAll;
  }

  /** Called from onPrepareActionMode: hides the system toolbar's items and shows / refreshes the bar */
  void update (Menu menu) {
    this.menu = menu;
    List<MenuItem> standard = new ArrayList<>();
    List<MenuItem> others = new ArrayList<>();
    for (int i = 0; i < menu.size(); i++) {
      MenuItem item = menu.getItem(i);
      int id = item.getItemId();
      boolean format = false;
      for (int formatId : Tgx101FormatMenuController.IDS) {
        if (formatId == id) { format = true; break; }
      }
      if (!format && !TextUtils.isEmpty(item.getTitle()) && (item.isVisible() || item.isEnabled())) {
        if (isStandard(id)) standard.add(item); else others.add(item);
      }
      item.setVisible(false); // the system toolbar shows nothing
    }
    // Cut, Copy, Paste, Select all in this order
    List<MenuItem> top = new ArrayList<>();
    for (int id : new int[] {android.R.id.cut, android.R.id.copy, android.R.id.paste, android.R.id.selectAll}) {
      for (MenuItem item : standard) if (item.getItemId() == id) top.add(item);
    }
    top.addAll(others);
    show(build(top));
  }

  void dismiss () {
    if (window != null) {
      try { window.dismiss(); } catch (Throwable ignored) { }
      window = null;
    }
    menu = null;
  }

  private View build (List<MenuItem> topItems) {
    Context context = input.getContext();
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    int pad = Screen.dp(6f);
    card.setPadding(pad, pad, pad, pad);
    card.setBackground(rounded(Theme.fillingColor(), 18f));
    card.setElevation(Screen.dp(8f));

    // top row: scrolls sideways
    HorizontalScrollView scroll = new HorizontalScrollView(context);
    scroll.setHorizontalScrollBarEnabled(false);
    scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
    scroll.setFadingEdgeLength(Screen.dp(24f));
    scroll.setHorizontalFadingEdgeEnabled(true);
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    for (MenuItem item : topItems) {
      TextView button = new TextView(context);
      CharSequence title = item.getItemId() == android.R.id.selectAll ? Lang.getString(R.string.Tgx101SelectAllFull) : item.getTitle();
      button.setText(title != null ? title.toString() : "");
      button.setSingleLine(true);
      button.setGravity(Gravity.CENTER);
      button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
      button.setTypeface(Fonts.getRobotoMedium());
      button.setTextColor(Theme.textAccentColor());
      button.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
      button.setBackground(pressable(ColorUtils.alphaColor(.06f, Theme.textAccentColor()), 10f));
      button.setOnClickListener(v -> invoke(item));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(34f));
      params.rightMargin = Screen.dp(4f);
      row.addView(button, params);
    }
    scroll.addView(row);
    card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(34f)));

    // bottom row: formatting icons in the user's order
    LinearLayout icons = new LinearLayout(context);
    icons.setOrientation(LinearLayout.HORIZONTAL);
    for (int entry : Tgx101FormatMenuController.getOrder()) {
      if (entry <= 0) continue;
      final int id = entry;
      ImageView icon = new ImageView(context);
      icon.setImageResource(Tgx101FormatMenuController.iconOf(id));
      icon.setColorFilter(Theme.textAccentColor());
      icon.setScaleType(ImageView.ScaleType.CENTER);
      icon.setBackground(pressable(0, 10f));
      icon.setContentDescription(Lang.getString(Tgx101FormatMenuController.nameOf(id)));
      icon.setOnClickListener(v -> {
        if (id == R.id.btn_link || id == R.id.btn_plain) {
          input.setSpan(id);
        } else {
          input.tgx101ToggleSpan(id);
        }
      });
      icons.addView(icon, new LinearLayout.LayoutParams(0, Screen.dp(38f), 1f));
    }
    LinearLayout.LayoutParams iconsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(38f));
    iconsParams.topMargin = Screen.dp(4f);
    card.addView(icons, iconsParams);
    return card;
  }

  private void invoke (MenuItem item) {
    Menu menu = this.menu;
    int id = item.getItemId();
    if (isStandard(id)) {
      input.onTextContextMenuItem(id);
      return;
    }
    Intent intent = item.getIntent();
    if (intent != null && Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
      int start = input.getSelectionStart(), end = input.getSelectionEnd();
      if (start >= 0 && end > start) {
        Intent copy = new Intent(intent);
        copy.putExtra(Intent.EXTRA_PROCESS_TEXT, input.getText().subSequence(Math.min(start, end), Math.max(start, end)).toString());
        copy.putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false);
        try { input.getContext().startActivity(copy); } catch (Throwable ignored) { }
      }
      return;
    }
    if (menu != null && id != 0) {
      menu.performIdentifierAction(id, 0);
    } else if (intent != null) {
      try { input.getContext().startActivity(intent); } catch (Throwable ignored) { }
    }
  }

  private void show (View content) {
    int width = Math.min(Screen.currentWidth() - Screen.dp(12f), Screen.dp(480f));
    content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    int height = content.getMeasuredHeight();
    int[] location = new int[2];
    input.getLocationOnScreen(location);
    int x = (Screen.currentWidth() - width) / 2;
    int y = location[1] - height - Screen.dp(6f);
    if (y < Screen.getStatusBarHeight()) {
      y = location[1] + input.getHeight() + Screen.dp(6f);
    }
    if (window != null) {
      window.setContentView(content);
      window.update(x, y, width, height);
      return;
    }
    window = new PopupWindow(content, width, height, false);
    window.setTouchable(true);
    window.setOutsideTouchable(false);
    window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    window.setClippingEnabled(false);
    try {
      window.showAtLocation(input, Gravity.TOP | Gravity.LEFT, x, y);
    } catch (Throwable t) {
      window = null;
    }
  }

  private static GradientDrawable rounded (int color, float radiusDp) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(Screen.dp(radiusDp));
    return drawable;
  }

  private static Drawable pressable (int color, float radiusDp) {
    return new RippleDrawable(ColorStateList.valueOf(ColorUtils.alphaColor(.18f, Theme.textAccentColor())), rounded(color, radiusDp), rounded(0xffffffff, radiusDp));
  }
}
