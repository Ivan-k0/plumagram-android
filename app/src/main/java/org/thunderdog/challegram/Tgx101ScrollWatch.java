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
package org.thunderdog.challegram;

import android.view.Choreographer;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.tool.UI;

import java.util.Locale;

/**
 * TGx101 (4PDA 2026-10-24, BlackBerry Z30 / Snapdragon S4, Android 4.3: «список чатов прокручивается рвано, а чаты
 * плавно»): a scroll measure that works on any Android — the frame watch above needs Android 7. Only in builds with the
 * diagnostics log. While a list scrolls, every frame's interval is taken from the display's clock; when it stops, one
 * line: how long, how many frames, how many slow ones and the worst, and for the chats list how long its rows took to
 * fill in (bind — names, texts, the avatar asked for) and to draw, and how many new rows were made.
 */
public final class Tgx101ScrollWatch extends RecyclerView.OnScrollListener implements Choreographer.FrameCallback {
  private static final long SLOW_NS = 20_000_000L;

  private final String name;
  private boolean running;
  private long startNs, lastNs, worstNs;
  private int frames, slow;

  // row costs (the chats list), the watch that is scrolling now
  private static Tgx101ScrollWatch active;
  private long bindNs, bindWorstNs, drawNs, drawWorstNs;
  private int binds, draws, created;

  private Tgx101ScrollWatch (String name) {
    this.name = name;
  }

  public static void attach (RecyclerView list, String name) {
    if (!BuildConfig.TGX101_DIAG || list == null) return;
    list.addOnScrollListener(new Tgx101ScrollWatch(name));
  }

  // TGx101 (user 2026-10-30 17:51, «свайп назад с лагами»): the same measure for a gesture that is no list's scroll —
  // the in-app back swipe; started when the screen begins to slide, reported a while after the finger lets go
  private static Tgx101ScrollWatch gesture;

  public static void gestureStart (String name) {
    if (!BuildConfig.TGX101_DIAG) return;
    if (gesture != null && gesture.running) return;
    gesture = new Tgx101ScrollWatch(name);
    gesture.onScrollStateChanged(null, RecyclerView.SCROLL_STATE_DRAGGING);
  }

  public static void gestureEnd (long afterMs) {
    final Tgx101ScrollWatch w = gesture;
    if (w == null || !w.running) return;
    UI.post(() -> {
      if (w.running) w.onScrollStateChanged(null, RecyclerView.SCROLL_STATE_IDLE);
    }, afterMs);
  }

  public static boolean on () {
    return BuildConfig.TGX101_DIAG && active != null;
  }

  public static void bound (long ns) {
    Tgx101ScrollWatch w = active;
    if (w == null) return;
    w.binds++;
    w.bindNs += ns;
    w.bindWorstNs = Math.max(w.bindWorstNs, ns);
  }

  public static void drawn (long ns) {
    Tgx101ScrollWatch w = active;
    if (w == null) return;
    w.draws++;
    w.drawNs += ns;
    w.drawWorstNs = Math.max(w.drawWorstNs, ns);
  }

  public static void created () {
    Tgx101ScrollWatch w = active;
    if (w != null) w.created++;
  }

  @Override
  public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
    if (newState != RecyclerView.SCROLL_STATE_IDLE) {
      if (!running) {
        running = true;
        startNs = lastNs = 0;
        frames = slow = 0;
        worstNs = 0;
        bindNs = bindWorstNs = drawNs = drawWorstNs = 0;
        binds = draws = created = 0;
        active = this;
        Choreographer.getInstance().postFrameCallback(this);
      }
    } else if (running) {
      running = false;
      Choreographer.getInstance().removeFrameCallback(this);
      if (active == this) active = null;
      report();
    }
  }

  @Override
  public void doFrame (long frameTimeNanos) {
    if (!running) return;
    if (startNs == 0) startNs = frameTimeNanos;
    if (lastNs != 0) {
      long d = frameTimeNanos - lastNs;
      frames++;
      if (d >= SLOW_NS) slow++;
      worstNs = Math.max(worstNs, d);
    }
    lastNs = frameTimeNanos;
    Choreographer.getInstance().postFrameCallback(this);
  }

  private void report () {
    if (frames < 3) return;
    long total = lastNs - startNs;
    StringBuilder b = new StringBuilder(String.format(Locale.US,
      "scroll %s: %.1f s, %d frames (%.0f fps), %d slow ≥20 ms, worst %d ms",
      name, total / 1e9, frames, frames / Math.max(.001, total / 1e9), slow, worstNs / 1_000_000L));
    if (binds > 0) {
      b.append(String.format(Locale.US, "; rows filled %d, avg %.1f ms, worst %.1f ms", binds, bindNs / 1e6 / binds, bindWorstNs / 1e6));
    }
    if (draws > 0) {
      b.append(String.format(Locale.US, "; rows drawn %d, avg %.2f ms, worst %.1f ms", draws, drawNs / 1e6 / draws, drawWorstNs / 1e6));
    }
    if (created > 0) {
      b.append("; new rows made ").append(created);
    }
    Tgx101Diag.mark(b.toString());
  }
}
