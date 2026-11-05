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

import androidx.annotation.Nullable;

import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * (user 2026-11-05, third batch of the music player) A song's words: what the file itself holds. ID3v2 (USLT, SYLT), FLAC and Ogg
 * (Vorbis comments), MP4 (©lyr). Synced when there are times, in the frame or as LRC in the text.
 */
public final class Tgx101Lyrics {
  public static final class Line {
    /** -1: not synced */
    public final long timeMs;
    public final String text;

    Line (long timeMs, String text) {
      this.timeMs = timeMs;
      this.text = text;
    }
  }

  public final List<Line> lines;
  public final boolean synced;

  private Tgx101Lyrics (List<Line> lines, boolean synced) {
    this.lines = lines;
    this.synced = synced;
  }

  /** The line playing at this moment, -1 before the first */
  public int lineAt (long positionMs) {
    if (!synced) return -1;
    int found = -1;
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).timeMs > positionMs) break;
      found = i;
    }
    return found;
  }

  private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
  private static final Pattern TAG = Pattern.compile("^\\[[a-zA-Z]+:.*]$");

  /** LRC or plain words */
  public static @Nullable Tgx101Lyrics parse (@Nullable String text) {
    if (text == null || text.trim().isEmpty()) return null;
    List<Line> timed = new ArrayList<>(), plain = new ArrayList<>();
    for (String raw : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
      String line = raw.trim();
      if (TAG.matcher(line).matches()) continue; // [ar:…], [length:…]
      Matcher m = STAMP.matcher(line);
      int end = 0;
      List<Long> times = new ArrayList<>();
      while (m.find() && m.start() == end) {
        end = m.end();
        long ms = Long.parseLong(m.group(1)) * 60000L + Long.parseLong(m.group(2)) * 1000L;
        String fraction = m.group(3);
        if (fraction != null) {
          ms += fraction.length() == 3 ? Long.parseLong(fraction) : fraction.length() == 2 ? Long.parseLong(fraction) * 10L : Long.parseLong(fraction) * 100L;
        }
        times.add(ms);
      }
      if (times.isEmpty()) {
        plain.add(new Line(-1, line));
      } else {
        String words = line.substring(end).trim();
        for (long time : times) timed.add(new Line(time, words));
      }
    }
    if (!timed.isEmpty()) return synced(timed);
    while (!plain.isEmpty() && plain.get(plain.size() - 1).text.isEmpty()) plain.remove(plain.size() - 1);
    return plain.isEmpty() ? null : new Tgx101Lyrics(plain, false);
  }

  private static Tgx101Lyrics synced (List<Line> lines) {
    Collections.sort(lines, (a, b) -> Long.compare(a.timeMs, b.timeMs));
    return new Tgx101Lyrics(lines, true);
  }

  // The file

  private static final int MAX_FRAME = 2 * 1024 * 1024;

  /** Reads the file: not on the UI thread */
  public static @Nullable Tgx101Lyrics fromFile (@Nullable String path) {
    if (path == null || path.isEmpty()) return null;
    try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
      byte[] head = new byte[12];
      if (file.read(head) < 12) return null;
      file.seek(0);
      if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') return fromId3(file);
      if (head[0] == 'f' && head[1] == 'L' && head[2] == 'a' && head[3] == 'C') return fromFlac(file);
      if (head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S') return fromOgg(file);
      if (head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') return fromMp4(file, 0, file.length(), 0);
    } catch (Throwable ignored) { }
    return null;
  }

  // ID3v2

  private static int syncSafe (byte[] b, int at) {
    return (b[at] & 0x7f) << 21 | (b[at + 1] & 0x7f) << 14 | (b[at + 2] & 0x7f) << 7 | (b[at + 3] & 0x7f);
  }

  private static int plain32 (byte[] b, int at) {
    return (b[at] & 0xff) << 24 | (b[at + 1] & 0xff) << 16 | (b[at + 2] & 0xff) << 8 | (b[at + 3] & 0xff);
  }

  private static Tgx101Lyrics fromId3 (RandomAccessFile file) throws Exception {
    byte[] header = new byte[10];
    file.readFully(header);
    final int version = header[3];
    final long tagEnd = 10L + syncSafe(header, 6);
    if (version < 2 || version > 4) return null;
    if (version > 2 && (header[5] & 0x40) != 0) { // an extended header
      byte[] extended = new byte[4];
      file.readFully(extended);
      int size = version == 4 ? syncSafe(extended, 0) : plain32(extended, 0) + 4;
      file.seek(file.getFilePointer() + size - 4);
    }
    final int headerSize = version == 2 ? 6 : 10;
    byte[] frame = new byte[headerSize];
    Tgx101Lyrics unsynced = null;
    while (file.getFilePointer() + headerSize <= tagEnd) {
      file.readFully(frame);
      if (frame[0] == 0) break; // the padding
      String id;
      int size;
      if (version == 2) {
        id = new String(frame, 0, 3, "ISO-8859-1");
        size = (frame[3] & 0xff) << 16 | (frame[4] & 0xff) << 8 | (frame[5] & 0xff);
      } else {
        id = new String(frame, 0, 4, "ISO-8859-1");
        size = version == 4 ? syncSafe(frame, 4) : plain32(frame, 4);
      }
      if (size < 0 || file.getFilePointer() + size > tagEnd) break;
      final boolean words = id.equals("USLT") || id.equals("ULT");
      final boolean timed = id.equals("SYLT") || id.equals("SLT");
      final boolean custom = id.equals("TXXX") || id.equals("TXX"); // many tools keep them here: «lyrics-eng», «UNSYNCEDLYRICS»
      if ((words || timed || custom) && size <= MAX_FRAME && size > 4) {
        byte[] data = new byte[size];
        file.readFully(data);
        if (timed) {
          Tgx101Lyrics sylt = parseSylt(data);
          if (sylt != null) return sylt;
        } else if (custom) {
          Tgx101Lyrics found = parseCustom(data);
          if (found != null && found.synced) return found;
          if (found != null && unsynced == null) unsynced = found;
        } else if (unsynced == null) {
          unsynced = parseUslt(data);
          if (unsynced != null && unsynced.synced) return unsynced; // LRC inside
        }
      } else {
        file.seek(file.getFilePointer() + size); // the cover and all the rest
      }
    }
    return unsynced;
  }

  private static Charset charsetOf (int encoding) {
    switch (encoding) {
      case 0: return Charset.forName("ISO-8859-1");
      case 1: return Charset.forName("UTF-16");
      case 2: return Charset.forName("UTF-16BE");
      default: return Charset.forName("UTF-8");
    }
  }

  /** The end of a terminated string from {@code from} in this encoding: the index of its terminator */
  private static int terminator (byte[] data, int from, int encoding) {
    if (encoding == 1 || encoding == 2) {
      for (int i = from; i + 1 < data.length; i += 2) {
        if (data[i] == 0 && data[i + 1] == 0) return i;
      }
    } else {
      for (int i = from; i < data.length; i++) {
        if (data[i] == 0) return i;
      }
    }
    return data.length;
  }

  private static Tgx101Lyrics parseUslt (byte[] data) {
    int encoding = data[0];
    int at = terminator(data, 4, encoding); // after the encoding and the language: a description
    at += encoding == 1 || encoding == 2 ? 2 : 1;
    if (at >= data.length) return null;
    return parse(new String(data, at, data.length - at, charsetOf(encoding)));
  }

  private static Tgx101Lyrics parseCustom (byte[] data) {
    int encoding = data[0];
    int end = terminator(data, 1, encoding);
    String name = new String(data, 1, end - 1, charsetOf(encoding)).toLowerCase(java.util.Locale.ROOT);
    if (!name.startsWith("lyrics") && !name.startsWith("unsyncedlyrics") && !name.startsWith("syncedlyrics")) return null;
    int at = end + (encoding == 1 || encoding == 2 ? 2 : 1);
    return at >= data.length ? null : parse(new String(data, at, data.length - at, charsetOf(encoding)));
  }

  private static Tgx101Lyrics parseSylt (byte[] data) {
    if (data.length < 7) return null;
    int encoding = data[0];
    if (data[4] != 2) return null; // times in frames of MPEG: not worth it
    final int width = encoding == 1 || encoding == 2 ? 2 : 1;
    int at = terminator(data, 6, encoding) + width; // after the language, the format, the type: a description
    Charset charset = charsetOf(encoding);
    List<Line> lines = new ArrayList<>();
    while (at < data.length) {
      int end = terminator(data, at, encoding);
      if (end + width + 4 > data.length) break;
      String text = new String(data, at, end - at, charset).trim();
      lines.add(new Line(plain32(data, end + width) & 0xffffffffL, text));
      at = end + width + 4;
    }
    return lines.isEmpty() ? null : synced(lines);
  }

  // Vorbis comments

  private static int little32 (byte[] b, int at) {
    return (b[at] & 0xff) | (b[at + 1] & 0xff) << 8 | (b[at + 2] & 0xff) << 16 | (b[at + 3] & 0xff) << 24;
  }

  private static Tgx101Lyrics fromComments (byte[] block, int at) {
    if (at + 4 > block.length) return null;
    at += 4 + little32(block, at); // the vendor
    if (at < 0 || at + 4 > block.length) return null;
    int count = little32(block, at);
    at += 4;
    Tgx101Lyrics found = null;
    Charset utf8 = Charset.forName("UTF-8");
    for (int i = 0; i < count && at + 4 <= block.length; i++) {
      int size = little32(block, at);
      at += 4;
      if (size < 0 || at + size > block.length) break;
      String comment = new String(block, at, Math.min(size, 16), utf8).toUpperCase(java.util.Locale.ROOT);
      if (comment.startsWith("LYRICS=") || comment.startsWith("UNSYNCEDLYRICS=") || comment.startsWith("SYNCEDLYRICS=")) {
        String whole = new String(block, at, size, utf8);
        Tgx101Lyrics lyrics = parse(whole.substring(whole.indexOf('=') + 1));
        if (lyrics != null && (found == null || lyrics.synced)) found = lyrics;
      }
      at += size;
    }
    return found;
  }

  private static Tgx101Lyrics fromFlac (RandomAccessFile file) throws Exception {
    file.seek(4);
    byte[] header = new byte[4];
    while (true) {
      file.readFully(header);
      boolean last = (header[0] & 0x80) != 0;
      int type = header[0] & 0x7f;
      int size = (header[1] & 0xff) << 16 | (header[2] & 0xff) << 8 | (header[3] & 0xff);
      if (type == 4) {
        if (size > 4 * MAX_FRAME) return null;
        byte[] block = new byte[size];
        file.readFully(block);
        return fromComments(block, 0);
      }
      if (last) return null;
      file.seek(file.getFilePointer() + size);
    }
  }

  /** The comments are in the second packet: pages are glued together without their headers */
  private static Tgx101Lyrics fromOgg (RandomAccessFile file) throws Exception {
    java.io.ByteArrayOutputStream packets = new java.io.ByteArrayOutputStream();
    byte[] header = new byte[27];
    for (int page = 0; page < 64 && packets.size() < 4 * MAX_FRAME; page++) {
      if (file.getFilePointer() + 27 > file.length()) break;
      file.readFully(header);
      if (header[0] != 'O' || header[1] != 'g' || header[2] != 'g' || header[3] != 'S') break;
      byte[] segments = new byte[header[26] & 0xff];
      file.readFully(segments);
      int size = 0;
      for (byte segment : segments) size += segment & 0xff;
      byte[] body = new byte[size];
      file.readFully(body);
      packets.write(body);
    }
    byte[] all = packets.toByteArray();
    // Vorbis: 0x03 "vorbis"; Opus: "OpusTags"
    int at = indexOf(all, new byte[] {3, 'v', 'o', 'r', 'b', 'i', 's'});
    int skip = 7;
    if (at == -1) {
      at = indexOf(all, new byte[] {'O', 'p', 'u', 's', 'T', 'a', 'g', 's'});
      skip = 8;
    }
    return at == -1 ? null : fromComments(all, at + skip);
  }

  private static int indexOf (byte[] data, byte[] what) {
    outer: for (int i = 0; i + what.length <= data.length; i++) {
      for (int j = 0; j < what.length; j++) {
        if (data[i + j] != what[j]) continue outer;
      }
      return i;
    }
    return -1;
  }

  // MP4: moov / udta / meta / ilst / ©lyr / data

  private static Tgx101Lyrics fromMp4 (RandomAccessFile file, long from, long to, int depth) throws Exception {
    long at = from;
    byte[] header = new byte[8];
    while (at + 8 <= to && depth < 6) {
      file.seek(at);
      file.readFully(header);
      long size = plain32(header, 0) & 0xffffffffL;
      String type = new String(header, 4, 4, "ISO-8859-1");
      int headerSize = 8;
      if (size == 1) {
        byte[] big = new byte[8];
        file.readFully(big);
        size = (plain32(big, 0) & 0xffffffffL) << 32 | (plain32(big, 4) & 0xffffffffL);
        headerSize = 16;
      } else if (size == 0) {
        size = to - at;
      }
      if (size < headerSize || at + size > to) return null;
      switch (type) {
        case "moov":
        case "udta":
        case "ilst": {
          Tgx101Lyrics found = fromMp4(file, at + headerSize, at + size, depth + 1);
          if (found != null) return found;
          break;
        }
        case "meta": {
          Tgx101Lyrics found = fromMp4(file, at + headerSize + 4, at + size, depth + 1); // version and flags
          if (found != null) return found;
          break;
        }
        case "©lyr": {
          // inside: data, 8 of its header, 8 of type and locale
          if (size - headerSize > 16 && size <= MAX_FRAME) {
            byte[] data = new byte[(int) (size - headerSize - 16)];
            file.seek(at + headerSize + 16);
            file.readFully(data);
            return parse(new String(data, "UTF-8"));
          }
          break;
        }
      }
      at += size;
    }
    return null;
  }
}
