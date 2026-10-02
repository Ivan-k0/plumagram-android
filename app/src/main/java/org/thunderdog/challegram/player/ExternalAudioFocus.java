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
package org.thunderdog.challegram.player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * Holds transient audio focus while a voice/round message is being recorded or played,
 * so other apps (Spotify, YouTube Music, car Bluetooth audio, ...) pause and resume on their own.
 * Gated by {@link Settings#SETTING_FLAG_PAUSE_MEDIA_ON_RECORD}.
 */
public class ExternalAudioFocus implements AudioManager.OnAudioFocusChangeListener {
  public static final int REASON_RECORD = 1;
  public static final int REASON_PLAYBACK = 1 << 1;
  /** TGx101: a video plays in the viewer (player settings «Pause music», on by default) */
  public static final int REASON_VIDEO = 1 << 2;

  private static ExternalAudioFocus instance;

  public static ExternalAudioFocus instance () {
    if (instance == null) {
      synchronized (ExternalAudioFocus.class) {
        if (instance == null) {
          instance = new ExternalAudioFocus();
        }
      }
    }
    return instance;
  }

  private int reasons;
  private boolean isHeld;
  private AudioFocusRequest focusRequest;

  private ExternalAudioFocus () { }

  /** True while this class holds focus: our own music player must not treat that as focus loss. */
  public synchronized boolean isHeld () {
    return isHeld;
  }

  public synchronized void setActive (int reason, boolean active) {
    reasons = active ? (reasons | reason) : (reasons & ~reason);
    boolean needFocus = ((reasons & ~REASON_VIDEO) != 0 && Settings.instance().getNewSetting(Settings.SETTING_FLAG_PAUSE_MEDIA_ON_RECORD))
      || ((reasons & REASON_VIDEO) != 0 && Settings.instance().tgx101PlayerPauseMusic());
    if (needFocus == isHeld) {
      return;
    }
    AudioManager am = (AudioManager) UI.getAppContext().getSystemService(Context.AUDIO_SERVICE);
    if (am == null) {
      return;
    }
    try {
      if (needFocus) {
        isHeld = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          if (focusRequest == null) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
              .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
              .setOnAudioFocusChangeListener(this)
              .build();
          }
          am.requestAudioFocus(focusRequest);
        } else {
          am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
      } else {
        isHeld = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          if (focusRequest != null) {
            am.abandonAudioFocusRequest(focusRequest);
          }
        } else {
          am.abandonAudioFocus(this);
        }
      }
    } catch (Throwable t) {
      Log.e("Failed to change external audio focus", t);
    }
  }

  @Override
  public void onAudioFocusChange (int focusChange) {
    // Nothing to do: we only hold focus to make other apps pause.
  }
}
