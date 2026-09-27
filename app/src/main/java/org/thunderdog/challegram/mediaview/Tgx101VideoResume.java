package org.thunderdog.challegram.mediaview;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.mediaview.data.MediaItem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TGx101: remembers where a video was left off in the media viewer, so reopening it
 * continues from there instead of starting over. In-memory only, lives while the process does.
 */
final class Tgx101VideoResume {
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
    synchronized (positions) {
      if (positionMs < MIN_POSITION_MS || (durationMs > 0 && positionMs > durationMs - END_MARGIN_MS)) {
        positions.remove(key);
      } else {
        positions.put(key, positionMs);
      }
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
