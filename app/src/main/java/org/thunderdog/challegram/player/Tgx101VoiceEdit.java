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

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.N;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TGx101 (user 2026-11-12, mockup «Голосовое: прослушка и обрезка»): a paused voice note can be listened to, its edges
 * trimmed and pieces cut out of it before sending.
 *
 * The recording (Ogg Opus, 20 ms packets) is decoded once into PCM for playback and the waveform. Edits are kept in the
 * recording's own time (samples at 48 kHz): the trimmed edges and the pieces cut out. The «shown» timeline is the
 * recording without the cut pieces — what the panel and the editor draw. Sending an edited note writes a new Ogg file
 * from the kept packets as they are (no re-encoding, 20 ms precision).
 */
public final class Tgx101VoiceEdit {
  public static final int RATE = 48000;
  private static final int BLOCK = 480; // 10 ms per waveform peak
  /** longer notes aren't decoded whole (memory): no listening or editing for them */
  public static final int MAX_SAMPLES = RATE * 60 * 6;

  public interface Listener {
    /** playback moved (shown position) or stopped; edits changed */
    void onVoiceEditChanged ();
  }

  public final String path;
  private final short[] pcm;
  public final int samples;
  private final float[] peaks;

  // edits, in the recording's samples
  private int trimStart, trimEnd;
  private final ArrayList<int[]> cutsAdded = new ArrayList<>(); // in the order made, for «bring back»
  private ArrayList<int[]> cuts = new ArrayList<>(); // merged and sorted

  private final ArrayList<Listener> listeners = new ArrayList<>();
  private final Handler ui = new Handler(Looper.getMainLooper());

  private Tgx101VoiceEdit (String path, short[] pcm, int samples) {
    this.path = path;
    this.pcm = pcm;
    this.samples = samples;
    this.trimStart = 0;
    this.trimEnd = samples;
    int blocks = (samples + BLOCK - 1) / BLOCK;
    peaks = new float[Math.max(1, blocks)];
    for (int b = 0; b < blocks; b++) {
      int from = b * BLOCK, to = Math.min(samples, from + BLOCK);
      int max = 0;
      for (int i = from; i < to; i++) {
        int v = Math.abs(pcm[i]);
        if (v > max) max = v;
      }
      peaks[b] = max / 32768f;
    }
  }

  /** Decodes the file; null when it can't be read or is too long. Not on the main thread. */
  public static @Nullable Tgx101VoiceEdit load (String path) {
    try {
      if (path == null || !new File(path).exists() || N.openOpusFile(path) == 0) return null;
      final int capacity = 48000 * 2 / 5; // 100 ms
      ByteBuffer buffer = ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
      int[] args = new int[3];
      short[] out = new short[RATE * 30];
      int count = 0;
      byte[] bytes = new byte[capacity];
      for (int guard = 0; guard < 100000; guard++) {
        buffer.clear();
        N.readOpusFile(buffer, capacity, args);
        int size = args[0];
        if (size > 0) {
          int n = size / 2;
          if (count + n > MAX_SAMPLES) return null;
          if (count + n > out.length) {
            short[] grown = new short[Math.min(MAX_SAMPLES, Math.max(out.length * 2, count + n))];
            System.arraycopy(out, 0, grown, 0, count);
            out = grown;
          }
          buffer.rewind();
          buffer.get(bytes, 0, size);
          for (int i = 0; i < n; i++) {
            out[count + i] = (short) ((bytes[i * 2] & 0xff) | (bytes[i * 2 + 1] << 8));
          }
          count += n;
        }
        if (args[2] == 1 || size == 0) break;
      }
      if (count < RATE / 10) return null;
      org.thunderdog.challegram.Tgx101Diag.mark("voice edit: loaded " + (count * 1000L / RATE) + " ms");
      return new Tgx101VoiceEdit(path, out, count);
    } catch (Throwable t) {
      org.thunderdog.challegram.Tgx101Diag.mark("voice edit: can't load — " + t.getClass().getSimpleName());
      return null;
    }
  }

  public void addListener (Listener l) {
    if (!listeners.contains(l)) listeners.add(l);
  }

  public void removeListener (Listener l) {
    listeners.remove(l);
  }

  private void notifyChanged () {
    for (Listener l : new ArrayList<>(listeners)) l.onVoiceEditChanged();
  }

  // Carrying the edits over a resumed recording (the file grows at its end)

  public void takeEditsFrom (@Nullable Tgx101VoiceEdit other) {
    if (other == null || !other.path.equals(path)) return;
    trimStart = Math.min(other.trimStart, samples);
    trimEnd = samples; // what was recorded after the pause belongs to the note
    cutsAdded.clear();
    for (int[] c : other.cutsAdded) {
      if (c[0] < samples) cutsAdded.add(new int[] {c[0], Math.min(c[1], samples)});
    }
    rebuildCuts();
  }

  // The shown timeline: the recording without the cut pieces

  public int shownLength () {
    int len = samples;
    for (int[] c : cuts) len -= c[1] - c[0];
    return len;
  }

  public int shownToOriginal (int d) {
    int o = Math.max(0, d);
    for (int[] c : cuts) {
      if (o >= c[0]) o += c[1] - c[0];
      else break;
    }
    return Math.min(o, samples);
  }

  public int originalToShown (int o) {
    int d = o;
    for (int[] c : cuts) {
      if (o >= c[1]) d -= c[1] - c[0];
      else if (o > c[0]) d -= o - c[0];
    }
    return Math.max(0, d);
  }

  /** the cut seams on the shown timeline */
  public int[] seams () {
    int[] r = new int[cuts.size()];
    for (int i = 0; i < r.length; i++) r[i] = originalToShown(cuts.get(i)[0]);
    return r;
  }

  public int trimStartShown () {
    return originalToShown(trimStart);
  }

  public int trimEndShown () {
    return originalToShown(trimEnd);
  }

  public void setTrimShown (int startShown, int endShown) {
    int min = RATE / 2; // half a second stays at least
    int len = shownLength();
    startShown = Math.max(0, Math.min(startShown, len - min));
    endShown = Math.min(len, Math.max(endShown, startShown + min));
    trimStart = shownToOriginal(startShown);
    trimEnd = shownToOriginal(endShown);
    notifyChanged();
  }

  public boolean isEdited () {
    return trimStart > 0 || trimEnd < samples || !cuts.isEmpty();
  }

  /** peak 0..1 of the shown piece [fromShown, toShown) */
  public float peak (int fromShown, int toShown) {
    float max = 0;
    for (int d = fromShown; d < toShown; d += BLOCK) {
      int b = shownToOriginal(d) / BLOCK;
      if (b >= 0 && b < peaks.length && peaks[b] > max) max = peaks[b];
    }
    return max;
  }

  // Cutting

  /** cuts the shown piece out; returns false when it would leave less than half a second */
  public boolean cutShown (int fromShown, int toShown) {
    if (toShown < fromShown) {
      int t = fromShown;
      fromShown = toShown;
      toShown = t;
    }
    int o1 = shownToOriginal(fromShown), o2 = shownToOriginal(toShown);
    if (o2 - o1 < BLOCK * 2) return false;
    ArrayList<int[]> trial = new ArrayList<>(cutsAdded);
    trial.add(new int[] {o1, o2});
    if (keptSamples(merge(trial)) < RATE / 2) return false;
    cutsAdded.add(new int[] {o1, o2});
    rebuildCuts();
    org.thunderdog.challegram.Tgx101Diag.mark("voice edit: cut " + (o1 * 1000L / RATE) + "–" + (o2 * 1000L / RATE) + " ms");
    notifyChanged();
    return true;
  }

  public boolean canBringBack () {
    return !cutsAdded.isEmpty();
  }

  public void bringBackLast () {
    if (cutsAdded.isEmpty()) return;
    cutsAdded.remove(cutsAdded.size() - 1);
    rebuildCuts();
    notifyChanged();
  }

  /** a copy of the edits, to return to on «Cancel» in the editor */
  public Object snapshot () {
    ArrayList<int[]> copy = new ArrayList<>();
    for (int[] c : cutsAdded) copy.add(c.clone());
    return new Object[] {trimStart, trimEnd, copy};
  }

  @SuppressWarnings("unchecked")
  public void restore (Object snapshot) {
    Object[] s = (Object[]) snapshot;
    trimStart = (Integer) s[0];
    trimEnd = (Integer) s[1];
    cutsAdded.clear();
    cutsAdded.addAll((ArrayList<int[]>) s[2]);
    rebuildCuts();
    notifyChanged();
  }

  private void rebuildCuts () {
    cuts = merge(cutsAdded);
  }

  private static ArrayList<int[]> merge (List<int[]> list) {
    ArrayList<int[]> sorted = new ArrayList<>();
    for (int[] c : list) sorted.add(c.clone());
    Collections.sort(sorted, (a, b) -> Integer.compare(a[0], b[0]));
    ArrayList<int[]> out = new ArrayList<>();
    for (int[] c : sorted) {
      if (!out.isEmpty() && c[0] <= out.get(out.size() - 1)[1]) {
        int[] last = out.get(out.size() - 1);
        last[1] = Math.max(last[1], c[1]);
      } else {
        out.add(c);
      }
    }
    return out;
  }

  /** the kept pieces of the recording: the trimmed part without the cuts */
  public ArrayList<int[]> keptRanges () {
    return keptRanges(cuts, trimStart, trimEnd);
  }

  private static ArrayList<int[]> keptRanges (List<int[]> cuts, int from, int to) {
    ArrayList<int[]> out = new ArrayList<>();
    int pos = from;
    for (int[] c : cuts) {
      if (c[1] <= pos) continue;
      if (c[0] >= to) break;
      if (c[0] > pos) out.add(new int[] {pos, c[0]});
      pos = Math.max(pos, c[1]);
    }
    if (pos < to) out.add(new int[] {pos, to});
    return out;
  }

  private int keptSamples (List<int[]> cuts) {
    int n = 0;
    for (int[] r : keptRanges(cuts, trimStart, trimEnd)) n += r[1] - r[0];
    return n;
  }

  public int keptSamples () {
    return keptSamples(cuts);
  }

  /** the kept length shown so far up to the shown position (for «0:05 / 0:16») */
  public int keptBefore (int shown) {
    int o = shownToOriginal(shown);
    int n = 0;
    for (int[] r : keptRanges()) {
      if (o <= r[0]) break;
      n += Math.min(o, r[1]) - r[0];
    }
    return n;
  }

  // Playback

  private AudioTrack track;
  private Thread playThread;
  private volatile boolean playing;
  private volatile int playOriginal = -1; // where playback is, in the recording's samples
  private int shownPosition; // the cursor on the shown timeline (kept while paused)

  public boolean isPlaying () {
    return playing;
  }

  /** the cursor on the shown timeline */
  public int position () {
    int o = playOriginal;
    return playing && o >= 0 ? originalToShown(o) : shownPosition;
  }

  public void setPosition (int shown) {
    boolean was = playing;
    stop();
    shownPosition = Math.max(0, Math.min(shown, shownLength()));
    notifyChanged();
    if (was) playKept();
  }

  /** plays the kept note from the cursor (from its start if the cursor is outside it) */
  public void playKept () {
    ArrayList<int[]> ranges = keptRanges();
    int o = shownToOriginal(shownPosition);
    boolean inside = false;
    for (int[] r : ranges) if (o >= r[0] && o < r[1] - BLOCK) inside = true;
    if (!inside) o = ranges.isEmpty() ? 0 : ranges.get(0)[0];
    play(ranges, o);
  }

  /** plays a shown piece (the editor's flags), whatever the trimmed edges */
  public void playShown (int fromShown, int toShown) {
    if (toShown < fromShown) {
      int t = fromShown;
      fromShown = toShown;
      toShown = t;
    }
    ArrayList<int[]> ranges = keptRanges(cuts, shownToOriginal(fromShown), shownToOriginal(toShown));
    play(ranges, ranges.isEmpty() ? 0 : ranges.get(0)[0]);
  }

  /** plays the whole shown note (the editor's big play), whatever the trimmed edges */
  public void playAllShown () {
    ArrayList<int[]> ranges = keptRanges(cuts, 0, samples);
    int o = shownToOriginal(shownPosition);
    if (o >= samples - BLOCK) o = 0;
    play(ranges, o);
  }

  private void play (final ArrayList<int[]> ranges, final int fromOriginal) {
    stop();
    if (ranges.isEmpty()) return;
    final int minBuffer = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
    final AudioTrack t;
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        t = new AudioTrack.Builder()
          .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
          .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
          .setBufferSizeInBytes(Math.max(minBuffer, RATE / 5 * 2))
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build();
      } else {
        t = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuffer, RATE / 5 * 2), AudioTrack.MODE_STREAM);
      }
      t.play();
    } catch (Throwable e) {
      org.thunderdog.challegram.Tgx101Diag.mark("voice edit: no audio track — " + e.getClass().getSimpleName());
      return;
    }
    track = t;
    playing = true;
    playOriginal = fromOriginal;
    // the order in which the samples go out, to tell where the head is
    final int[][] plan;
    {
      ArrayList<int[]> p = new ArrayList<>();
      for (int[] r : ranges) {
        if (r[1] <= fromOriginal) continue;
        p.add(new int[] {Math.max(r[0], fromOriginal), r[1]});
      }
      plan = p.toArray(new int[0][]);
    }
    playThread = new Thread(() -> {
      int written = 0;
      try {
        for (int[] r : plan) {
          int pos = r[0];
          while (pos < r[1] && playing && track == t) {
            int n = Math.min(r[1] - pos, 960 * 4);
            int w = t.write(pcm, pos, n);
            if (w <= 0) break;
            pos += w;
            written += w;
          }
          if (!playing || track != t) break;
        }
        // let the last samples play out
        while (playing && track == t && t.getPlaybackHeadPosition() < written) {
          Thread.sleep(10);
        }
      } catch (Throwable ignored) { }
      ui.post(() -> {
        if (track == t) {
          int end = plan.length > 0 ? plan[plan.length - 1][1] : fromOriginal;
          finishPlayback(t, end >= trimEnd - BLOCK ? -1 : end);
        }
      });
    }, "TGx101VoicePreview");
    playThread.start();
    // where the head is, every frame
    ui.post(new Runnable() {
      @Override
      public void run () {
        if (track != t || !playing) return;
        int head;
        try {
          head = t.getPlaybackHeadPosition();
        } catch (Throwable e) {
          head = 0;
        }
        int left = head;
        int o = plan.length > 0 ? plan[plan.length - 1][1] : fromOriginal;
        for (int[] r : plan) {
          int len = r[1] - r[0];
          if (left < len) {
            o = r[0] + left;
            break;
          }
          left -= len;
        }
        playOriginal = o;
        notifyChanged();
        ui.postDelayed(this, 16);
      }
    });
    notifyChanged();
  }

  private void finishPlayback (AudioTrack t, int stoppedAt) {
    playing = false;
    try {
      t.pause();
      t.flush();
      t.release();
    } catch (Throwable ignored) { }
    if (track == t) track = null;
    shownPosition = stoppedAt < 0 ? trimStartShown() : originalToShown(stoppedAt);
    playOriginal = -1;
    notifyChanged();
  }

  public void pause () {
    if (!playing) return;
    int o = playOriginal;
    AudioTrack t = track;
    playing = false;
    track = null;
    if (t != null) {
      try {
        t.pause();
        t.flush();
        t.release();
      } catch (Throwable ignored) { }
    }
    shownPosition = o >= 0 ? originalToShown(o) : shownPosition;
    playOriginal = -1;
    notifyChanged();
  }

  public void stop () {
    if (playing) pause();
  }

  public void release () {
    stop();
    listeners.clear();
  }

  // Sending: the kept packets into a new file

  public static final class Result {
    public final File file;
    public final int duration;
    public final byte[] waveform;

    Result (File file, int duration, byte[] waveform) {
      this.file = file;
      this.duration = duration;
      this.waveform = waveform;
    }
  }

  /** Writes the edited note next to the recording. Not on the main thread. */
  public @Nullable Result export () {
    try {
      byte[] data = readAll(new File(path));
      List<byte[]> packets = new ArrayList<>();
      long serial = parseOgg(data, packets);
      if (packets.size() < 3) return null;
      byte[] head = packets.get(0), tags = packets.get(1);
      if (head.length < 19 || head[0] != 'O' || head[1] != 'p') return null;
      int preSkip = (head[10] & 0xff) | ((head[11] & 0xff) << 8);

      ArrayList<int[]> kept = keptRanges();
      ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
      int seq = 0;
      writePage(out, serial, seq++, 0x02, 0, Collections.singletonList(head));
      writePage(out, serial, seq++, 0x00, 0, Collections.singletonList(tags));
      ArrayList<byte[]> page = new ArrayList<>();
      int pageBytes = 0, pageSegments = 0;
      long granule = preSkip;
      int keptCount = 0;
      int start = -preSkip; // decoded samples of the next packet, in the recording's time
      for (int i = 2; i < packets.size(); i++) {
        byte[] p = packets.get(i);
        int n = opusSamples(p);
        if (n <= 0) n = 960;
        int center = start + n / 2;
        start += n;
        if (!inside(kept, center)) continue;
        int segments = p.length / 255 + 1;
        if (!page.isEmpty() && (pageSegments + segments > 255 || pageBytes + p.length > 4000)) {
          writePage(out, serial, seq++, 0x00, granule, page);
          page.clear();
          pageBytes = pageSegments = 0;
        }
        page.add(p);
        pageBytes += p.length;
        pageSegments += segments;
        granule += n;
        keptCount += n;
      }
      if (page.isEmpty()) return null;
      writePage(out, serial, seq, 0x04, granule, page);

      File file = new File(path + ".edited.ogg");
      try (FileOutputStream fos = new FileOutputStream(file)) {
        out.writeTo(fos);
      }
      // the waveform of what is sent
      int total = 0;
      for (int[] r : kept) total += r[1] - r[0];
      short[] keptPcm = new short[Math.max(1, total)];
      int k = 0;
      for (int[] r : kept) {
        System.arraycopy(pcm, r[0], keptPcm, k, r[1] - r[0]);
        k += r[1] - r[0];
      }
      byte[] waveform = N.getWaveform2(keptPcm, total);
      int duration = Math.max(1, Math.round(keptCount / (float) RATE));
      org.thunderdog.challegram.Tgx101Diag.mark("voice edit: sent edited — " + (samples * 1000L / RATE) + " ms → " + (keptCount * 1000L / RATE) + " ms, " + kept.size() + " piece(s), " + file.length() + " B");
      return new Result(file, duration, waveform);
    } catch (Throwable t) {
      org.thunderdog.challegram.Tgx101Diag.mark("voice edit: export failed — " + t.getClass().getSimpleName() + ": " + t.getMessage());
      return null;
    }
  }

  private static boolean inside (List<int[]> ranges, int s) {
    for (int[] r : ranges) if (s >= r[0] && s < r[1]) return true;
    return false;
  }

  /** samples at 48 kHz in an Opus packet, from its TOC byte (RFC 6716 3.1) */
  static int opusSamples (byte[] p) {
    if (p.length < 1) return 0;
    int toc = p[0] & 0xff;
    int config = toc >> 3;
    int frame;
    if (config < 12) {
      frame = new int[] {480, 960, 1920, 2880}[config & 3]; // SILK 10/20/40/60 ms
    } else if (config < 16) {
      frame = (config & 1) == 0 ? 480 : 960; // hybrid 10/20 ms
    } else {
      frame = new int[] {120, 240, 480, 960}[config & 3]; // CELT 2.5/5/10/20 ms
    }
    int code = toc & 3;
    int frames;
    if (code == 0) frames = 1;
    else if (code == 3) frames = p.length > 1 ? (p[1] & 0x3f) : 0;
    else frames = 2;
    return frame * frames;
  }

  private static byte[] readAll (File f) throws IOException {
    try (FileInputStream in = new FileInputStream(f)) {
      ByteArrayOutputStream b = new ByteArrayOutputStream((int) Math.max(1024, f.length()));
      byte[] buf = new byte[16384];
      int n;
      while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
      return b.toByteArray();
    }
  }

  /** the packets of the first logical stream; returns its serial */
  private static long parseOgg (byte[] d, List<byte[]> packets) {
    int pos = 0;
    long serial = -1;
    ByteArrayOutputStream current = new ByteArrayOutputStream();
    while (pos + 27 <= d.length) {
      if (d[pos] != 'O' || d[pos + 1] != 'g' || d[pos + 2] != 'g' || d[pos + 3] != 'S') break;
      long s = (d[pos + 14] & 0xffL) | ((d[pos + 15] & 0xffL) << 8) | ((d[pos + 16] & 0xffL) << 16) | ((d[pos + 17] & 0xffL) << 24);
      int segments = d[pos + 26] & 0xff;
      int bodyStart = pos + 27 + segments;
      if (bodyStart > d.length) break;
      if (serial == -1) serial = s;
      int body = bodyStart;
      for (int i = 0; i < segments; i++) {
        int len = d[pos + 27 + i] & 0xff;
        if (body + len > d.length) return serial;
        if (s == serial) current.write(d, body, len);
        body += len;
        if (len < 255 && s == serial) {
          packets.add(current.toByteArray());
          current.reset();
        }
      }
      pos = body;
    }
    return serial;
  }

  private static void writePage (ByteArrayOutputStream out, long serial, int seq, int flags, long granule, List<byte[]> packets) {
    ByteArrayOutputStream lacing = new ByteArrayOutputStream();
    int bodyLen = 0;
    for (byte[] p : packets) {
      int len = p.length;
      while (len >= 255) {
        lacing.write(255);
        len -= 255;
      }
      lacing.write(len);
      bodyLen += p.length;
    }
    byte[] lace = lacing.toByteArray();
    byte[] page = new byte[27 + lace.length + bodyLen];
    page[0] = 'O';
    page[1] = 'g';
    page[2] = 'g';
    page[3] = 'S';
    page[4] = 0;
    page[5] = (byte) flags;
    for (int i = 0; i < 8; i++) page[6 + i] = (byte) (granule >>> (8 * i));
    for (int i = 0; i < 4; i++) page[14 + i] = (byte) (serial >>> (8 * i));
    for (int i = 0; i < 4; i++) page[18 + i] = (byte) (seq >>> (8 * i));
    page[26] = (byte) lace.length;
    System.arraycopy(lace, 0, page, 27, lace.length);
    int at = 27 + lace.length;
    for (byte[] p : packets) {
      System.arraycopy(p, 0, page, at, p.length);
      at += p.length;
    }
    int crc = oggCrc(page);
    for (int i = 0; i < 4; i++) page[22 + i] = (byte) (crc >>> (8 * i));
    out.write(page, 0, page.length);
  }

  private static int[] crcTable;

  private static int oggCrc (byte[] data) {
    if (crcTable == null) {
      int[] t = new int[256];
      for (int i = 0; i < 256; i++) {
        int r = i << 24;
        for (int j = 0; j < 8; j++) r = (r & 0x80000000) != 0 ? (r << 1) ^ 0x04c11db7 : r << 1;
        t[i] = r;
      }
      crcTable = t;
    }
    int crc = 0;
    for (byte b : data) crc = (crc << 8) ^ crcTable[((crc >>> 24) & 0xff) ^ (b & 0xff)];
    return crc;
  }
}
