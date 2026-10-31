package org.thunderdog.challegram.mediaview;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.mediaview.data.MediaItem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TGx101: remembers where a video was left off in the media viewer, so reopening it
 * continues from there instead of starting over. In-memory only, lives while the process does.
 */
public final class Tgx101VideoResume {
  private static final int MAX_ENTRIES = 32;
  private static final long MIN_POSITION_MS = 3000;
  private static final long END_MARGIN_MS = 3000;

  private static final Map<String, Long> positions = new LinkedHashMap<String, Long>(16, .75f, true) {
    @Override
    protected boolean removeEldestEntry (Map.Entry<String, Long> eldest) {
      return size() > MAX_ENTRIES;
    }
  };

  private Tgx101VideoResume () { }

  @Nullable
  private static String key (@Nullable MediaItem item) {
    if (item == null || item.getType() != MediaItem.TYPE_VIDEO || item.isSecret() || item.tdlib() == null) {
      return null;
    }
    return item.tdlib().id() + ":" + item.getFileId();
  }

  static void save (@Nullable MediaItem item, long positionMs, long durationMs) {
    String key = key(item);
    if (key == null) {
      return;
    }
    // (user 2026-10-31): back in the feed or the post, the video goes on from where the viewer left it
    if (positionMs > 0) org.thunderdog.challegram.loader.gif.GifActor.tgx101SetPosition(item.getFileId(), positionMs);
    synchronized (positions) {
      if (positionMs < MIN_POSITION_MS || (durationMs > 0 && positionMs > durationMs - END_MARGIN_MS)) {
        positions.remove(key);
      } else {
        positions.put(key, positionMs);
      }
    }
  }

  // (user 2026-10-30) once: the video opened from where it was playing by itself in the feed
  private static final Map<String, Long> startOnce = new java.util.HashMap<>();

  public static void startAt (@Nullable MediaItem item, long positionMs) {
    String key = key(item);
    if (key == null || positionMs <= 0) return;
    synchronized (startOnce) {
      startOnce.put(key, positionMs);
    }
  }

  static long takeStart (@Nullable MediaItem item) {
    String key = key(item);
    if (key == null) return 0;
    synchronized (startOnce) {
      Long at = startOnce.remove(key);
      return at != null ? at : 0;
    }
  }

  static long get (@Nullable MediaItem item) {
    String key = key(item);
    if (key == null) {
      return 0;
    }
    synchronized (positions) {
      Long position = positions.get(key);
      return position != null ? position : 0;
    }
  }
}
