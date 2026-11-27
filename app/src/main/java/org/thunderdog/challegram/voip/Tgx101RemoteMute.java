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
package org.thunderdog.challegram.voip;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101 (user 2026-11-27 «во время звонка не отображается, что собеседник выключил микрофон»): tgcalls tells the other
 * side's microphone state (the journal showed it, nobody listened). Kept here for the call screen and the call bar. UI thread
 */
public final class Tgx101RemoteMute {
  private Tgx101RemoteMute () { }

  private static int callId;
  private static boolean muted;
  private static final List<Runnable> listeners = new ArrayList<>();

  public static void set (int forCallId, boolean isMuted) {
    if (callId == forCallId && muted == isMuted) return;
    callId = forCallId;
    muted = isMuted;
    for (Runnable r : new ArrayList<>(listeners)) r.run();
  }

  /** the other side's microphone is off in this call */
  public static boolean isMuted (int forCallId) {
    return muted && callId == forCallId;
  }

  public static void addListener (Runnable r) {
    if (!listeners.contains(r)) listeners.add(r);
  }

  public static void removeListener (Runnable r) {
    listeners.remove(r);
  }
}
