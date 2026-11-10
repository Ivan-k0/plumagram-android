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

  // (user video 2026-11-10 11:18 «обложка мелькает в начале и в конце каждый раз»): the picture handed over each way —
  // the feed's frame to the viewer as it opens, the viewer's last frame back to the post as it closes. One of each is
  // enough: only one video opens at a time
  private static @Nullable String startFrameKey;
  private static @Nullable android.graphics.Bitmap startFrame;
  private static int backFrameFileId;
  private static @Nullable android.graphics.Bitmap backFrame;

  public static void startFrame (@Nullable MediaItem item, @Nullable android.graphics.Bitmap frame) {
    String key = key(item);
    synchronized (Tgx101VideoResume.class) {
      startFrameKey = key != null && frame != null ? key : null;
      startFrame = startFrameKey != null ? frame : null;
      startFrameAt = android.os.SystemClock.uptimeMillis();
    }
  }

  private static long startFrameAt;

  /** not used up: the viewer replaces the opened item with its own copy as it loads the chat's media around it, and the
   *  copy asks again — the frame stays for a few seconds, for any item of that video */
  static @Nullable android.graphics.Bitmap takeStartFrame (@Nullable MediaItem item) {
    String key = key(item);
    synchronized (Tgx101VideoResume.class) {
      if (key == null || !key.equals(startFrameKey)) return null;
      if (android.os.SystemClock.uptimeMillis() - startFrameAt > 5000) {
        startFrame = null;
        startFrameKey = null;
        return null;
      }
      return startFrame;
    }
  }

  // (user video 2026-11-10 12:35): the feed's post shows the video's own frame (its thumbnail), the viewer showed the
  // author's cover — every copy of the item the viewer makes as it loads the channel's media did, so the cover came up in
  // the middle of the opening and at the end of the closing. A video opened from the feed uses its own frame everywhere
  private static final java.util.Map<Integer, Long> noCover = new java.util.concurrent.ConcurrentHashMap<>();

  public static void skipCover (int videoFileId) {
    if (noCover.size() > 200) noCover.clear();
    noCover.put(videoFileId, android.os.SystemClock.uptimeMillis());
  }

  /** for half an hour after it was opened from the feed — the same video opened later from its channel keeps its cover */
  public static boolean coverSkipped (int videoFileId) {
    Long at = noCover.get(videoFileId);
    return at != null && android.os.SystemClock.uptimeMillis() - at < 30 * 60_000L;
  }

  public static void backFrame (int fileId, @Nullable android.graphics.Bitmap frame) {
    synchronized (Tgx101VideoResume.class) {
      backFrameFileId = frame != null ? fileId : 0;
      backFrame = frame;
    }
  }

  public static @Nullable android.graphics.Bitmap takeBackFrame (int fileId) {
    synchronized (Tgx101VideoResume.class) {
      if (fileId == 0 || fileId != backFrameFileId) return null;
      android.graphics.Bitmap frame = backFrame;
      backFrame = null;
      backFrameFileId = 0;
      return frame;
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
