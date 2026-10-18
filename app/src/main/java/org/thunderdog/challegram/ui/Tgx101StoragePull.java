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
import android.view.MotionEvent;
import android.view.View;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * TGx101 (user 2026-10-15/16): on the first chat folder the finger pulled to the right — where there are no folders — opens
 * Saved as a storage, the way pulling down opens the search: a circle with a bookmark slides in from the left edge at the
 * finger, its ring fills, one short vibration at the threshold, and it opens when released past it. Off in MagiX → Gestures.
 */
public final class Tgx101StoragePull extends View {
  public interface Host {
    boolean canPull (); // on the first folder, nothing else going on
    /** the storage pushed and ready to follow the finger; false — it can't now */
    boolean startDrag ();
    void drag (float progress);
    void endDrag (boolean open, float velocity);
  }

  private final Host host;
  private final int slop;
  private boolean tracking, active;
  private float startX, startY;
  private @Nullable VelocityTracker velocity;

  public Tgx101StoragePull (Context context, Host host) {
    super(context);
    this.host = host;
    this.slop = ViewConfiguration.get(context).getScaledTouchSlop();
  }

  /**
   * TGx101 (user 2026-10-18 «открытие хранилища должно выглядеть как выход из чата в список свайпом, супер плавно и
   * бесшумно»): the storage lies left of the chats — pulled right, it comes in from the left following the finger, no
   * circle and no buzz; let go past a third (or flung) — it opens, else it slides back. From the pager's dispatch: true —
   * the pager doesn't get this gesture any more
   */
  public boolean onPagerTouch (View pager, MotionEvent e) {
    if (velocity != null) velocity.addMovement(e);
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        tracking = Settings.instance().tgx101SwipeToStorage() && host.canPull();
        active = false;
        startX = e.getX();
        startY = e.getY();
        if (velocity != null) velocity.recycle();
        velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
        return false;
      case MotionEvent.ACTION_MOVE: {
        float dx = e.getX() - startX, dy = e.getY() - startY;
        if (active) {
          host.drag(Math.max(0f, dx - slop) / Math.max(1, pager.getWidth()));
          return true;
        }
        if (!tracking) return false;
        if (dx < -slop || Math.abs(dy) > Math.max(slop, dx)) {
          tracking = false; // left goes through the folders, vertical scrolls the chats
          return false;
        }
        if (dx > slop * 1.5f && dx > Math.abs(dy) * 2f) {
          tracking = false;
          if (!host.startDrag()) return false;
          active = true;
          MotionEvent cancel = MotionEvent.obtain(e);
          cancel.setAction(MotionEvent.ACTION_CANCEL);
          pager.onTouchEvent(cancel); // the list under the finger lets go (no tap, no long press)
          for (int i = 0; i < ((android.view.ViewGroup) pager).getChildCount(); i++) ((android.view.ViewGroup) pager).getChildAt(i).dispatchTouchEvent(cancel);
          cancel.recycle();
          host.drag(Math.max(0f, dx - slop) / Math.max(1, pager.getWidth()));
          return true;
        }
        return false;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        tracking = false;
        if (!active) return false;
        active = false;
        float vx = 0f;
        if (velocity != null) {
          velocity.computeCurrentVelocity(1000);
          vx = velocity.getXVelocity();
        }
        float progress = Math.max(0f, e.getX() - startX - slop) / Math.max(1, pager.getWidth());
        boolean fling = Math.abs(vx) > Screen.dp(600f);
        boolean open = e.getActionMasked() == MotionEvent.ACTION_UP && (fling ? vx > 0 : progress > .33f);
        host.endDrag(open, Math.abs(vx));
        return true;
      }
    }
    return false;
  }
}
