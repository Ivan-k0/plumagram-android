/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/tgx101-android)
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

import android.view.View;
import android.widget.FrameLayout;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.navigation.ViewController;

/** TGx101: Android 4 builds have no calls (no WebRTC), so the video panel is a no-op there. */
final class Tgx101CallVideo {
  interface Host {
    boolean isMicMuted ();
    void toggleMicMuted ();
    boolean isSpeakerOn ();
    void toggleSpeaker ();
    void openChat ();
    void hangUp ();
    void onVideoStarted ();
  }

  Tgx101CallVideo (ViewController<?> controller, FrameLayout contentView, int index, Host host, View... hideWhenRemoteVideo) { }

  void setOriginalControls (View... views) { }
  void onCallStateChanged (TdApi.Call call) { }
  void updateControls () { }
  void destroy () { }
}
