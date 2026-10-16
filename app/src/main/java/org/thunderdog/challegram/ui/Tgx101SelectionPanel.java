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

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.List;

/**
 * TGx101 (approved mockup «Панель действий при выделении», variant 3): at the bottom while messages are selected —
 * a row of reactions (one tap puts it on all of them) and capsule buttons with a sign and a word, scrolling sideways.
 * The words can be turned off in the Saved settings.
 */
public final class Tgx101SelectionPanel extends LinearLayout {
  public interface Host {
    List<TdApi.Message> selectedMessages ();
    void afterAction (boolean clear);
  }

  private final ViewController<?> c;
  private final Host host;
  private final LinearLayout reactionsBox;

  public Tgx101SelectionPanel (Context context, ViewController<?> c, Host host) {
    super(context);
    this.c = c;
    this.host = host;
    setOrientation(VERTICAL);
    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
    bg.setColor(Theme.fillingColor());
    float r = Screen.dp(20f);
    bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
    setBackground(bg);
    if (android.os.Build.VERSION.SDK_INT >= 21) setElevation(Screen.dp(8f));
    setPadding(0, Screen.dp(10f), 0, Screen.dp(12f) + Screen.getNavigationBarHeight());
    setClickable(true);

    reactionsBox = new LinearLayout(context);
    reactionsBox.setOrientation(VERTICAL);
    addView(reactionsBox);

    boolean labels = Tgx101SavedStore.get(c.tdlib()).panelLabels;
    HorizontalScrollView scroll = new HorizontalScrollView(context);
    scroll.setHorizontalScrollBarEnabled(false);
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(HORIZONTAL);
    row.setPadding(Screen.dp(12f), 0, Screen.dp(4f), 0);
    row.addView(capsule(context, R.drawable.baseline_folder_24, R.string.Tgx101SavedToFolder, true, labels, v -> Tgx101SavedActions.organize(c, host.selectedMessages(), () -> host.afterAction(true))));
    row.addView(capsule(context, R.drawable.baseline_label_24, R.string.Tgx101SavedTag, false, labels, v -> Tgx101SavedActions.organize(c, host.selectedMessages(), () -> host.afterAction(true))));
    row.addView(capsule(context, R.drawable.baseline_notifications_active_24, R.string.Tgx101SavedRemind, false, labels, v -> Tgx101SavedActions.remind(c, Tgx101SavedKit.idsOf(host.selectedMessages()), () -> host.afterAction(true))));
    row.addView(capsule(context, R.drawable.baseline_forward_24, R.string.Tgx101SavedForward, false, labels, v -> {
      Tgx101SavedActions.forward(c, host.selectedMessages());
      host.afterAction(true);
    }));
    row.addView(capsule(context, R.drawable.baseline_delete_24, R.string.Tgx101SavedDelete, false, false, v -> Tgx101SavedActions.delete(c, Tgx101SavedKit.idsOf(host.selectedMessages()), () -> host.afterAction(true))));
    scroll.addView(row);
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    sp.topMargin = Screen.dp(2f);
    addView(scroll, sp);
  }

  /** the reactions show what all the selected already have */
  public void update () {
    reactionsBox.removeAllViews();
    List<TdApi.Message> messages = host.selectedMessages();
    if (messages.isEmpty()) return;
    reactionsBox.addView(Tgx101SavedActions.reactionRow(c, messages, () -> host.afterAction(false)));
  }

  private View capsule (Context context, int icon, int label, boolean primary, boolean withLabel, View.OnClickListener click) {
    boolean red = icon == R.drawable.baseline_delete_24;
    LinearLayout b = new LinearLayout(context);
    b.setOrientation(HORIZONTAL);
    b.setGravity(Gravity.CENTER_VERTICAL);
    b.setPadding(Screen.dp(withLabel ? 14f : 12f), 0, Screen.dp(withLabel ? 16f : 12f), 0);
    int fill = primary ? Tgx101SavedHomeController.accent() : Theme.backgroundColor();
    b.setBackground(Tgx101SavedHomeController.card(fill, 20f));
    b.setContentDescription(Lang.getString(label));
    Views.setClickable(b);
    b.setOnClickListener(click);
    int fg = primary ? 0xffffffff : red ? Theme.getColor(ColorId.textNegative) : Theme.textAccentColor();
    ImageView i = new ImageView(context);
    i.setImageResource(icon);
    i.setColorFilter(fg);
    b.addView(i, new LinearLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f)));
    if (withLabel) {
      TextView t = Tgx101SavedHomeController.text(context, 14f, fg, true);
      t.setText(Lang.getString(label));
      t.setSingleLine(true);
      t.setPadding(Screen.dp(8f), 0, 0, 0);
      b.addView(t);
    }
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(40f));
    p.rightMargin = Screen.dp(8f);
    b.setLayoutParams(p);
    return b;
  }
}
