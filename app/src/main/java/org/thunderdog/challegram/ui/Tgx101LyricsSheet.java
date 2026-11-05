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

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.Tgx101Lyrics;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

/**
 * (user 2026-11-05, third batch of the music player) A song's words in a sheet. Synced words follow the song, a tap on a line goes there.
 */
final class Tgx101LyricsSheet {
  private final ViewController<?> c;
  private final Tgx101Sheet.Handle handle;
  private final Runnable onDismiss;
  private final List<TextView> lineViews = new ArrayList<>();
  private InlineResultCommon item;
  private @Nullable Tgx101Lyrics lyrics;
  private int token, currentLine = -1;
  private boolean closed;

  Tgx101LyricsSheet (ViewController<?> c, InlineResultCommon item, Runnable onDismiss) {
    this.c = c;
    this.onDismiss = onDismiss;
    this.handle = Tgx101Sheet.show(c, this::onClosed, body -> { });
    setItem(item);
  }

  private void onClosed () {
    closed = true;
    token++;
    onDismiss.run();
  }

  void dismiss () {
    handle.dismiss();
  }

  /** The next track, while the sheet is open: its words */
  void setItem (InlineResultCommon item) {
    if (closed || this.item == item) return;
    this.item = item;
    this.lyrics = null;
    this.currentLine = -1;
    final int request = ++token;
    showMessage(R.string.Tgx101LyricsLoading);
    final TdApi.Audio audio = item.getTrackAudio();
    final String path = TD.isFileLoaded(item.getTrackFile()) ? item.getTrackFile().local.path : null;
    Background.instance().post(() -> {
      Tgx101Lyrics found = Tgx101Lyrics.fromFile(path);
      UI.post(() -> {
        if (request != token) return;
        if (found != null) {
          show(found);
        } else {
          showMissing(audio, path != null);
        }
      });
    });
  }

  private void header () {
    handle.body.removeAllViews();
    lineViews.clear();
    String title = item.getTrackTitle();
    handle.body.addView(Tgx101Sheet.title(c.context(), title == null || title.isEmpty() ? Lang.getString(R.string.Tgx101Lyrics) : title));
  }

  private void showMessage (int res) {
    header();
    handle.body.addView(Tgx101Sheet.row(c.context(), 0, 0, Lang.getString(res), null, false, null));
  }

  private void showMissing (TdApi.Audio audio, boolean fileLoaded) {
    showMessage(fileLoaded ? R.string.Tgx101LyricsNone : R.string.Tgx101LyricsNotLoaded);
  }

  private void show (Tgx101Lyrics lyrics) {
    this.lyrics = lyrics;
    header();
    for (Tgx101Lyrics.Line line : lyrics.lines) {
      TextView view = Tgx101SavedHomeController.text(c.context(), 17f, lyrics.synced ? Theme.textDecentColor() : Theme.textAccentColor(), false);
      view.setText(line.text.isEmpty() ? "♪" : line.text);
      view.setGravity(Gravity.LEFT);
      view.setPadding(Screen.dp(20f), Screen.dp(5f), Screen.dp(20f), Screen.dp(5f));
      if (lyrics.synced) {
        org.thunderdog.challegram.tool.Views.setClickable(view);
        org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(view);
        final long time = line.timeMs;
        view.setOnClickListener(v -> c.tdlib().context().player().seekTrack(item.getMessage(), time));
      } else {
        view.setTextIsSelectable(true);
      }
      handle.body.addView(view);
      lineViews.add(view);
    }
    onProgress(c.tdlib().context().player().getPlayPosition(item.tdlib(), item.getMessage()));
  }

  /** The song went on */
  void onProgress (long positionMs) {
    if (closed || lyrics == null || !lyrics.synced) return;
    int line = lyrics.lineAt(positionMs);
    if (line == currentLine) return;
    if (currentLine >= 0 && currentLine < lineViews.size()) {
      lineViews.get(currentLine).setTextColor(Theme.textDecentColor());
    }
    currentLine = line;
    if (line < 0 || line >= lineViews.size()) return;
    TextView view = lineViews.get(line);
    view.setTextColor(Theme.textAccentColor());
    if (handle.body.getParent() instanceof ScrollView) {
      ScrollView scroll = (ScrollView) handle.body.getParent();
      view.post(() -> scroll.smoothScrollTo(0, Math.max(0, view.getTop() - scroll.getHeight() / 2 + view.getHeight() / 2)));
    }
  }
}
