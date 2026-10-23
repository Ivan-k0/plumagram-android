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
package org.thunderdog.challegram.tool;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.U;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * TGx101 (user 2026-10-23 «большое видео открывается 1–2 секунды»): the size, length and turn of an mp4 / mov read
 * straight from its index (moov → mvhd, trak → tkhd, hdlr) — a few small reads wherever the index sits, where the
 * system's retriever took 2 s on a 700 MB file with the index at its end. Null when the file isn't one or reads odd:
 * the retriever is used then.
 */
public final class Tgx101Mp4Meta {
  private Tgx101Mp4Meta () { }

  private static final int MAX_MOOV = 64 * 1024 * 1024;

  // (user 2026-10-23, the same file still 2 s: a TS stream named .mp4 — no index, the system scans it) the answer is
  // kept per file, and worked out ahead in the background when a video file shows up in a chat
  private static final android.util.LruCache<String, U.MediaMetadata> known = new android.util.LruCache<>(64);
  private static final java.util.concurrent.ExecutorService ahead = java.util.concurrent.Executors.newSingleThreadExecutor();

  private static String key (String path) {
    java.io.File f = new java.io.File(path);
    return path + "|" + f.length() + "|" + f.lastModified();
  }

  /** the size, length and turn of a video file: kept, from its index, or from the system's retriever */
  @Nullable
  public static U.MediaMetadata get (String path) {
    final String key = key(path);
    U.MediaMetadata m = known.get(key);
    if (m != null) return m;
    m = read(path);
    if (m == null) m = U.getMediaMetadata(path);
    if (m != null) known.put(key, m);
    return m;
  }

  public static boolean isKnown (String path) {
    return known.get(key(path)) != null;
  }

  /** works {@link #get} out ahead, so the tap opens at once */
  public static void prefetch (String path) {
    if (path == null || path.isEmpty()) return;
    ahead.execute(() -> {
      try {
        if (!isKnown(path)) get(path);
      } catch (Throwable ignored) { }
    });
  }

  @Nullable
  public static U.MediaMetadata read (String path) {
    try (RandomAccessFile f = new RandomAccessFile(path, "r")) {
      final long length = f.length();
      long at = 0;
      boolean typed = false;
      while (at + 8 <= length) {
        f.seek(at);
        long size = f.readInt() & 0xffffffffL;
        int type = f.readInt();
        int header = 8;
        if (size == 1) {
          size = f.readLong();
          header = 16;
        } else if (size == 0) {
          size = length - at;
        }
        if (size < header || at + size > length) return null;
        if (!typed) {
          if (type != fourcc("ftyp")) return null; // not an mp4 / mov
          typed = true;
        } else if (type == fourcc("moov")) {
          if (size - header > MAX_MOOV) return null;
          byte[] moov = new byte[(int) (size - header)];
          f.readFully(moov);
          return parseMoov(moov, length);
        }
        at += size;
      }
    } catch (IOException | RuntimeException ignored) { }
    return null;
  }

  private static U.MediaMetadata parseMoov (byte[] b, long fileLength) {
    long timescale = 0, duration = 0;
    int width = 0, height = 0, rotation = 0;
    boolean hasVideo = false, hasAudio = false;
    int at = 0;
    while (at + 8 <= b.length) {
      int size = i32(b, at), type = i32(b, at + 4);
      if (size < 8 || at + size > b.length) break;
      if (type == fourcc("mvhd")) {
        int v = b[at + 8];
        if (v == 1) {
          timescale = u32(b, at + 8 + 4 + 16);
          duration = i64(b, at + 8 + 4 + 20);
        } else {
          timescale = u32(b, at + 8 + 4 + 8);
          duration = u32(b, at + 8 + 4 + 12);
        }
      } else if (type == fourcc("trak")) {
        int[] track = parseTrak(b, at + 8, at + size);
        if (track != null) {
          if (track[0] == fourcc("vide") && track[1] > 0 && track[2] > 0 && !hasVideo) {
            hasVideo = true;
            width = track[1];
            height = track[2];
            rotation = track[3];
          } else if (track[0] == fourcc("soun")) {
            hasAudio = true;
          }
        }
      }
      at += size;
    }
    if (!hasVideo || timescale <= 0) return null;
    long durationMs = duration * 1000L / timescale;
    long bitrate = durationMs > 0 ? fileLength * 8000L / durationMs : 0;
    return new U.MediaMetadata(width, height, rotation, durationMs, bitrate, true, hasAudio, null, null);
  }

  /** {handler, width, height, rotation} of a track, or null */
  @Nullable
  private static int[] parseTrak (byte[] b, int from, int to) {
    int handler = 0, width = 0, height = 0, rotation = 0;
    int at = from;
    while (at + 8 <= to) {
      int size = i32(b, at), type = i32(b, at + 4);
      if (size < 8 || at + size > to) return null;
      if (type == fourcc("tkhd")) {
        int v = b[at + 8];
        int p = at + 8 + 4 + (v == 1 ? 32 : 20) + 8 + 8; // times, track id, duration; reserved; layer, group, volume
        int a = i32(b, p), bb = i32(b, p + 4), c = i32(b, p + 12), d = i32(b, p + 16);
        width = i32(b, p + 36) >> 16;
        height = i32(b, p + 40) >> 16;
        final int one = 0x10000;
        if (a == 0 && bb == one && c == -one && d == 0) rotation = 90;
        else if (a == -one && d == -one) rotation = 180;
        else if (a == 0 && bb == -one && c == one && d == 0) rotation = 270;
      } else if (type == fourcc("mdia")) {
        int in = at + 8;
        while (in + 8 <= at + size) {
          int s = i32(b, in);
          if (s < 8 || in + s > at + size) break;
          if (i32(b, in + 4) == fourcc("hdlr")) handler = i32(b, in + 16);
          in += s;
        }
      }
      at += size;
    }
    return new int[] {handler, width, height, rotation};
  }

  private static int fourcc (String s) {
    return (s.charAt(0) << 24) | (s.charAt(1) << 16) | (s.charAt(2) << 8) | s.charAt(3);
  }

  private static int i32 (byte[] b, int at) {
    return ((b[at] & 0xff) << 24) | ((b[at + 1] & 0xff) << 16) | ((b[at + 2] & 0xff) << 8) | (b[at + 3] & 0xff);
  }

  private static long u32 (byte[] b, int at) {
    return i32(b, at) & 0xffffffffL;
  }

  private static long i64 (byte[] b, int at) {
    return (u32(b, at) << 32) | u32(b, at + 4);
  }
}
