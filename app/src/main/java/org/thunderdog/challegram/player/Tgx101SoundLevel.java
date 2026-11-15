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
package org.thunderdog.challegram.player;

import android.os.SystemClock;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;

import org.thunderdog.challegram.ui.Tgx101TypingLine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * TGx101 (user 2026-11-15 «волна типа эквалайзера лёгкая, которая реагирует на голос… или когда проигрывается музыка или
 * голосовое»): how loud the sound a player is putting out is, for the field's line. The player's own decoded sound is
 * listened to on its way to the speaker — nothing is recorded and nothing leaves the phone. The sound is handed over a
 * little ahead of being heard (the speaker's buffer), so each piece is shown one after another at the pace it plays.
 */
public final class Tgx101SoundLevel implements TeeAudioProcessor.AudioBufferSink {
  private static final int WINDOW_MS = 50;

  private int sampleRate, channels, encoding;
  private long nextAt; // uptime the next piece is shown at

  private Tgx101SoundLevel () { }

  public static AudioProcessor[] processors () {
    return new AudioProcessor[] {new TeeAudioProcessor(new Tgx101SoundLevel())};
  }

  @Override
  public void flush (int sampleRateHz, int channelCount, int encoding) {
    this.sampleRate = sampleRateHz;
    this.channels = channelCount;
    this.encoding = encoding;
    this.nextAt = 0; // a new track or a seek: the line starts over from now
    Tgx101TypingLine.Sound.newTrack();
  }

  @Override
  public void handleBuffer (ByteBuffer buffer) {
    if (encoding != C.ENCODING_PCM_16BIT || sampleRate <= 0 || channels <= 0 || !Tgx101TypingLine.Sound.wanted()) return;
    ByteBuffer b = buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder()); // its own position: the player's is not touched
    int frame = 2 * channels;
    int frames = b.remaining() / frame;
    if (frames <= 0) return;
    int windowFrames = Math.max(1, sampleRate * WINDOW_MS / 1000);
    long now = SystemClock.uptimeMillis();
    // far behind (the speed is above 1×: the sound is taken faster than it is counted here) or long silent — from now
    if (nextAt < now || nextAt > now + 700) nextAt = now;
    int start = b.position();
    int step = Math.max(1, windowFrames / 64); // 64 samples a window are enough for a level
    for (int from = 0; from < frames; from += windowFrames) {
      int count = Math.min(windowFrames, frames - from);
      int peak = 0;
      for (int i = 0; i < count; i += step) {
        int v = Math.abs(b.getShort(start + (from + i) * frame));
        if (v > peak) peak = v;
      }
      float level = (float) Math.sqrt(Math.min(1f, peak / 20000f)); // quiet passages still move the line
      Tgx101TypingLine.Sound.pushAt(level, nextAt - now);
      nextAt += count * 1000L / sampleRate;
    }
  }
}
