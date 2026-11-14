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
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.Tgx101MusicAi;
import org.thunderdog.challegram.player.Tgx101MusicTaste;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.ColorUtils;

/**
 * (user 2026-11-14, the mockup «ИИ: Хранилище и музыка», variant В «Мой вкус») What the player has learnt of the account's music, in
 * plain sight: the artists it finishes and the ones it skips, what each signal does, the switches, what is hidden, a reset.
 */
final class Tgx101TasteSheet {
  private static final int TOP = 5;

  private final ViewController<?> c;
  private final Context context;
  private final Runnable showHidden;
  private final Tgx101Sheet.Handle handle;
  private boolean isAsking;

  static void show (ViewController<?> c, Runnable showHidden) {
    new Tgx101TasteSheet(c, showHidden);
  }

  private Tgx101TasteSheet (ViewController<?> c, Runnable showHidden) {
    this.c = c;
    this.context = c.context();
    this.showHidden = showHidden;
    this.handle = Tgx101Sheet.show(c, null, this::fill);
  }

  private Tgx101MusicTaste taste () {
    return Tgx101MusicTaste.get(c.tdlib());
  }

  private TextView hint (CharSequence text) {
    TextView v = Tgx101SavedHomeController.text(context, 13f, Theme.textDecentColor(), false);
    v.setText(text);
    v.setPadding(Screen.dp(20f), Screen.dp(2f), Screen.dp(20f), Screen.dp(8f));
    return v;
  }

  private TextView section (int res) {
    TextView v = Tgx101SavedHomeController.text(context, 14f, Theme.getColor(ColorId.textLink), true);
    v.setText(Lang.getString(res));
    v.setPadding(Screen.dp(20f), Screen.dp(12f), Screen.dp(20f), Screen.dp(6f));
    return v;
  }

  /** The artist, a bar as long as its score, and a word when it's mostly skipped */
  private View bar (String name, int score, int max, boolean skipped) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(Screen.dp(20f), Screen.dp(5f), Screen.dp(20f), Screen.dp(5f));
    TextView label = Tgx101SavedHomeController.text(context, 14.5f, skipped ? Theme.textDecentColor() : Theme.textAccentColor(), false);
    label.setText(name);
    label.setSingleLine(true);
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    row.addView(label, new LinearLayout.LayoutParams(Screen.dp(120f), ViewGroup.LayoutParams.WRAP_CONTENT));

    LinearLayout track = new LinearLayout(context);
    track.setOrientation(LinearLayout.HORIZONTAL);
    GradientDrawable back = new GradientDrawable();
    back.setColor(ColorUtils.alphaColor(0.14f, Theme.textDecentColor()));
    back.setCornerRadius(Screen.dp(4f));
    track.setBackground(back);
    View fill = new View(context);
    GradientDrawable front = new GradientDrawable();
    front.setColor(skipped ? Theme.getColor(ColorId.textNegative) : Theme.getColor(ColorId.textLink));
    front.setCornerRadius(Screen.dp(4f));
    fill.setBackground(front);
    float part = Math.max(0.06f, Math.min(1f, Math.abs(score) / (float) Math.max(1, max)));
    track.addView(fill, new LinearLayout.LayoutParams(0, Screen.dp(8f), part));
    track.addView(new View(context), new LinearLayout.LayoutParams(0, Screen.dp(8f), 1f - part));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Screen.dp(8f), 1f);
    p.leftMargin = Screen.dp(10f);
    row.addView(track, p);
    if (skipped) {
      TextView word = Tgx101SavedHomeController.text(context, 12.5f, Theme.textDecentColor(), false);
      word.setText(Lang.getString(R.string.Tgx101TasteSkipped));
      word.setPadding(Screen.dp(10f), 0, 0, 0);
      row.addView(word);
    }
    return row;
  }

  private void fill (LinearLayout body) {
    body.removeAllViews();
    final Tgx101MusicTaste taste = taste();
    final Settings settings = Settings.instance();
    body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101Taste)));
    body.addView(hint(Lang.getString(R.string.Tgx101TasteCounts, taste.listenCount(), taste.skipCount(), taste.likeCount())));

    final List<String> favorites = taste.favoriteArtists(TOP);
    final List<String> skipped = taste.skippedArtists(2);
    if (favorites.isEmpty() && skipped.isEmpty()) {
      body.addView(hint(Lang.getString(R.string.Tgx101TasteEmpty)));
    } else {
      int max = 1;
      for (String a : favorites) max = Math.max(max, taste.artistScore(a));
      for (String a : skipped) max = Math.max(max, -taste.artistScore(a));
      for (String a : favorites) body.addView(bar(taste.artistName(a), taste.artistScore(a), max, false));
      for (String a : skipped) body.addView(bar(taste.artistName(a), taste.artistScore(a), max, true));
    }

    // In words, by the AI: only when it's on, and only on a tap
    if (Tgx101MusicAi.isOn() && !favorites.isEmpty()) {
      String phrase = Tgx101MusicAi.taste(c.tdlib());
      if (phrase != null) {
        TextView v = Tgx101SavedHomeController.text(context, 14.5f, Theme.textAccentColor(), false);
        v.setText(phrase);
        v.setPadding(Screen.dp(20f), Screen.dp(8f), Screen.dp(20f), Screen.dp(4f));
        body.addView(v);
      }
      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_stars_24, 0, Lang.getString(isAsking ? R.string.Tgx101TasteAsking : phrase != null ? R.string.Tgx101TasteDescribeAgain : R.string.Tgx101TasteDescribe), null, false, v -> {
        if (isAsking) return;
        isAsking = true;
        fill(body);
        List<String> liked = new ArrayList<>(), bad = new ArrayList<>();
        for (String a : taste.favoriteArtists(10)) liked.add(taste.artistName(a));
        for (String a : taste.skippedArtists(5)) bad.add(taste.artistName(a));
        Tgx101MusicAi.describe(c.tdlib(), liked, bad, new ArrayList<>(), result -> {
          isAsking = false;
          if (result == null) UI.showToast(R.string.Tgx101TasteFailed, android.widget.Toast.LENGTH_SHORT);
          if (!c.isDestroyed()) fill(body);
        });
      }));
    }

    body.addView(section(R.string.Tgx101TasteSignals));
    body.addView(hint(Lang.getString(R.string.Tgx101TasteSignalsText)));

    body.addView(section(R.string.Tgx101TasteSwitches));
    final boolean skips = settings.tgx101MusicSkips();
    body.addView(Tgx101Sheet.row(context, R.drawable.baseline_skip_next_24_white, 0, Lang.getString(R.string.Tgx101TasteSkips), null, skips, v -> {
      settings.setTgx101MusicSkips(!skips);
      fill(body);
    }));
    final boolean ai = settings.tgx101MusicAi();
    body.addView(Tgx101Sheet.row(context, R.drawable.baseline_stars_24, 0, Lang.getString(R.string.Tgx101TasteAi), null, ai && settings.tgx101Ai(), v -> {
      if (!settings.tgx101Ai()) {
        UI.showToast(R.string.Tgx101AiOff, android.widget.Toast.LENGTH_SHORT);
        return;
      }
      settings.setTgx101MusicAi(!ai);
      fill(body);
    }));
    body.addView(hint(Lang.getString(R.string.Tgx101TasteAiHint)));
    final boolean hints = settings.tgx101WaveHints();
    body.addView(Tgx101Sheet.row(context, R.drawable.baseline_search_24, 0, Lang.getString(R.string.Tgx101TasteHints), null, hints, v -> {
      settings.setTgx101WaveHints(!hints);
      fill(body);
    }));
    body.addView(hint(Lang.getString(R.string.Tgx101WaveHintsWarning)));

    int hidden = taste.hiddenTracks().size() + taste.hiddenArtists().size();
    body.addView(Tgx101Sheet.row(context, R.drawable.baseline_visibility_24, 0, Lang.getString(R.string.Tgx101DislikeHidden), String.valueOf(hidden), false, v -> {
      handle.dismiss();
      UI.post(showHidden, 200);
    }));
    View reset = Tgx101Sheet.row(context, R.drawable.baseline_delete_24, 0, Lang.getString(R.string.Tgx101TasteReset), null, false, v -> {
      handle.dismiss();
      UI.post(() -> c.showConfirm(Lang.getString(R.string.Tgx101TasteResetInfo), Lang.getString(R.string.Tgx101TasteResetDo), R.drawable.baseline_delete_24,
        ViewController.OptionColor.RED, () -> {
          taste.forgetAll();
          Tgx101MusicAi.forget(c.tdlib());
          UI.showToast(R.string.Tgx101TasteResetDone, android.widget.Toast.LENGTH_SHORT);
        }), 200);
    });
    body.addView(reset);
  }
}
