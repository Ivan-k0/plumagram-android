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

import android.os.SystemClock;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.VelocityTracker;

import androidx.annotation.NonNull;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.util.Locale;

/**
 * TGx101 (4PDA 2026-10-24, BlackBerry Z30 / Snapdragon S4, Android 4.3: «список чатов прокручивается рвано, а чаты
 * плавно»): a scroll measure that works on any Android — the frame watch above needs Android 7. Only in builds with the
 * diagnostics log. While a list scrolls, every frame's interval is taken from the display's clock; when it stops, one
 * line: how long, how many frames, how many slow ones and the worst, and for the chats list how long its rows took to
 * fill in (bind — names, texts, the avatar asked for) and to draw, and how many new rows were made.
 */
public final class Tgx101ScrollWatch extends RecyclerView.OnScrollListener implements Choreographer.FrameCallback, RecyclerView.OnItemTouchListener {
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
    Tgx101ScrollWatch w = new Tgx101ScrollWatch(name);
    w.list = list;
    list.addOnScrollListener(w);
    list.addOnItemTouchListener(w); // only watches: never takes the touch
  }

  // TGx101 (user 2026-11-11 «свайпы в чатах и списке чатов иногда как желе»): how the list follows the finger.
  // Per gesture, frame by frame: the finger's way and the list's way — how much of the finger the list took while
  // dragged, the widest gap between them, frames where the finger moved and the list stood (stalls) or stepped far
  // off the finger (uneven), the finger's speed at letting go against the speed the fling starts with, how long and
  // far the fling ran, frames with row animations running under the finger, frames pressed against the list's end
  // (the stretch), and the worst delay of a touch. The frame steps go to the log only when something looks off.
  private RecyclerView list;
  private VelocityTracker velocity;
  private boolean fingerDown, dragged;
  private int drags;
  private float lastFingerY, fingerPx, fingerFramePx;
  private float listPx, listFramePx, dragListPx, flingPx;
  private float maxGapPx, gapPx;
  private int stalls, uneven, animFrames, edgeFrames, dragFrames;
  private long touchDelayWorstMs, releaseNs, dragNs, dragStartNs;
  private float releaseSpeed, flingStartPx;
  private int flingStartFrames;
  private long flingT0, flingT1;
  private long flingEndNs;
  private boolean flingStopped;
  private final StringBuilder steps = new StringBuilder();
  private int stepCount;

  private void resetTouch () {
    drags = 0;
    fingerPx = listPx = dragListPx = flingPx = 0;
    fingerFramePx = listFramePx = 0;
    maxGapPx = gapPx = 0;
    stalls = uneven = animFrames = edgeFrames = dragFrames = 0;
    touchDelayWorstMs = 0;
    releaseNs = dragNs = dragStartNs = 0;
    releaseSpeed = flingStartPx = 0;
    flingStartFrames = 0;
    flingT0 = flingT1 = 0;
    flingEndNs = 0;
    flingStopped = false;
    steps.setLength(0);
    stepCount = 0;
  }

  @Override
  public boolean onInterceptTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) {
    watchTouch(e);
    return false;
  }

  @Override
  public void onTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) { }

  @Override
  public void onRequestDisallowInterceptTouchEvent (boolean disallowIntercept) { }

  private void watchTouch (MotionEvent e) {
    long delay = SystemClock.uptimeMillis() - e.getEventTime();
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        if (!running) resetTouch();
        if (running && releaseNs != 0) flingStopped = true;
        fingerDown = true;
        dragged = false;
        lastFingerY = e.getRawY();
        if (velocity != null) velocity.recycle();
        velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (!fingerDown) break;
        float y = e.getRawY();
        float d = y - lastFingerY;
        lastFingerY = y;
        if (velocity != null) velocity.addMovement(e);
        if (running && list != null && list.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) {
          if (!dragged) {
            dragged = true;
            drags++;
            dragStartNs = System.nanoTime();
            releaseNs = 0;
          }
          fingerPx += Math.abs(d);
          fingerFramePx += Math.abs(d);
          touchDelayWorstMs = Math.max(touchDelayWorstMs, delay);
        }
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (!fingerDown) break;
        fingerDown = false;
        if (velocity != null) {
          velocity.addMovement(e);
          velocity.computeCurrentVelocity(1000);
          if (dragged) releaseSpeed = Math.abs(velocity.getYVelocity()) / Screen.density();
          velocity.recycle();
          velocity = null;
        }
        if (dragged) {
          releaseNs = System.nanoTime();
          dragNs += releaseNs - dragStartNs;
          flingStartPx = 0;
          flingStartFrames = 0;
        }
        break;
      }
    }
  }

  @Override
  public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
    if (!running) return;
    float d = Math.abs(dy);
    listPx += d;
    listFramePx += d;
    if (fingerDown && dragged) {
      dragListPx += d;
    } else if (releaseNs != 0) {
      flingPx += d;
    }
  }

  /** once a frame while the list moves: compare the finger's step with the list's */
  private void frameStep (long frameTimeNanos) {
    if (list == null) return;
    boolean dragging = fingerDown && dragged;
    if (dragging) {
      dragFrames++;
      gapPx += fingerFramePx - listFramePx;
      maxGapPx = Math.max(maxGapPx, Math.abs(gapPx));
      if (fingerFramePx > Screen.dp(1f) && listFramePx < .5f) {
        if (list.canScrollVertically(1) && list.canScrollVertically(-1)) stalls++;
        else edgeFrames++;
      } else if (fingerFramePx > Screen.dp(2f) && Math.abs(listFramePx - fingerFramePx) > Math.max(Screen.dp(2f), fingerFramePx * .4f)) {
        uneven++;
      }
    } else if (releaseNs != 0 && !fingerDown) {
      if (flingStartFrames < 3) {
        if (flingStartFrames == 0) flingT0 = lastNs != 0 ? lastNs : frameTimeNanos;
        flingStartPx += listFramePx;
        flingStartFrames++;
        flingT1 = frameTimeNanos;
      }
      if (listFramePx > 0) flingEndNs = frameTimeNanos;
    }
    if (list.isAnimating()) animFrames++;
    if (stepCount < 80 && (dragging || releaseNs != 0)) {
      if (stepCount > 0) steps.append(' ');
      steps.append(dragging ? "" : "~").append(Math.round(fingerFramePx)).append('/').append(Math.round(listFramePx));
      stepCount++;
    }
    fingerFramePx = listFramePx = 0;
  }

  // TGx101 (user 2026-10-30 17:51, «свайп назад с лагами»): the same measure for a gesture that is no list's scroll —
  // the in-app back swipe; started when the screen begins to slide, reported a while after the finger lets go
  private static Tgx101ScrollWatch gesture;

  /** A gesture is being measured: layout requests during it are logged (who asked), a few per gesture */
  public static boolean isGestureRunning () {
    return gesture != null && gesture.running;
  }

  private static int layoutRequestsLogged;

  /** (user 2026-11-03 «жест назад с пролагом»: 6–8 ms of layout in each slow frame of the slide) — whose layout it is */
  public static void onLayoutRequested () {
    if (!BuildConfig.TGX101_DIAG || !isGestureRunning() || layoutRequestsLogged >= 4) return;
    layoutRequestsLogged++;
    StringBuilder b = new StringBuilder();
    StackTraceElement[] stack = new Throwable().getStackTrace();
    int shown = 0;
    for (StackTraceElement e : stack) {
      String c = e.getClassName();
      if (c.startsWith("android.") || c.startsWith("androidx.") || c.startsWith("java.") || c.contains("Tgx101ScrollWatch") || c.endsWith("RootFrameLayout")) continue;
      if (b.length() > 0) b.append(" ← ");
      b.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber());
      if (++shown == 4) break;
    }
    Tgx101Diag.mark("  layout asked during " + gesture.name + ": " + b);
  }

  public static void gestureStart (String name) {
    if (!BuildConfig.TGX101_DIAG) return;
    if (gesture != null && gesture.running) return;
    layoutRequestsLogged = 0;
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
    frameStep(frameTimeNanos);
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
    reportSwipe();
  }

  private void reportSwipe () {
    if (list == null || drags == 0 || fingerPx < Screen.dp(8f)) {
      resetTouch();
      return;
    }
    float dp = Screen.density();
    int follow = fingerPx > 0 ? Math.round(100f * dragListPx / fingerPx) : 0;
    StringBuilder b = new StringBuilder(String.format(Locale.US,
      "  swipe %s: %d drag(s) %.0f dp in %.2f s, the list took %d%% (widest gap %.0f dp), stalls %d, uneven %d",
      name, drags, fingerPx / dp, dragNs / 1e9, follow, maxGapPx / dp, stalls, uneven));
    float flingStartSpeed = 0;
    if (releaseSpeed > 0) {
      // the first three frames after letting go, as a speed
      flingStartSpeed = flingStartFrames > 0 && flingT1 > flingT0 ? flingStartPx / dp / ((flingT1 - flingT0) / 1e9f) : 0;
      b.append(String.format(Locale.US, "; let go at %.0f dp/s, the fling starts at %.0f dp/s (%d%%)",
        releaseSpeed, flingStartSpeed, releaseSpeed > 0 ? Math.round(100f * flingStartSpeed / releaseSpeed) : 0));
      if (flingPx > 0) {
        b.append(String.format(Locale.US, ", ran %.0f dp%s", flingPx / dp, flingEndNs > 0 && releaseNs > 0 ? String.format(Locale.US, " for %.2f s", Math.max(0, (flingEndNs - releaseNs)) / 1e9) : ""));
      }
      if (flingStopped) b.append(", caught by a finger");
    }
    if (animFrames > 0) b.append("; row animations on ").append(animFrames).append(" frames");
    if (edgeFrames > 0) b.append("; against the end ").append(edgeFrames).append(" frames");
    b.append("; touch delay worst ").append(touchDelayWorstMs).append(" ms");
    boolean off = follow < 90 || follow > 110 || stalls > 0 || uneven > 1 || animFrames > 0
      || (releaseSpeed > 300 && flingStartSpeed > 0 && Math.abs(flingStartSpeed - releaseSpeed) > releaseSpeed * .35f);
    if (off && steps.length() > 0) {
      b.append(" | steps finger/list px, ~ after letting go: ").append(steps);
    }
    Tgx101Diag.mark(b.toString());
    resetTouch();
  }


}
