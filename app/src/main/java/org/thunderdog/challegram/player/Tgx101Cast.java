package org.thunderdog.challegram.player;

import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaSeekOptions;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.images.WebImage;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101CastServer;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * TGx101 (user 2026-11-20 «через три точки в плеере нужно добавить chromecast, как и меню видеоплеера»): casting the
 * song or the video playing to a TV (Chromecast, a TV or a speaker with Google Cast).
 *
 * Our player stays the one in charge: it keeps playing, silently, and the TV is made to follow it — the same file
 * (served from the phone by {@link Tgx101CastServer}), the same place, playing or paused. So the queue, the next and
 * the previous song, the seek bar and the notification all work as before. What happens on the TV side (paused from
 * its remote, buffering) is brought back: the phone's player pauses too, or moves to the TV's place.
 */
public final class Tgx101Cast {
  private Tgx101Cast () { }

  /** What plays on the phone and is mirrored to the TV */
  public interface Source {
    @Nullable Item castItem ();
    boolean castIsPlaying ();
    long castPosition ();
    void castSeekLocal (long positionMs);
    void castSetPlaying (boolean playing);
    void castSetMuted (boolean muted);
    /** The media viewer's player: the next video the viewer plays takes the TV over */
    default boolean castFollowsViewer () {
      return false;
    }
  }

  public static final class Item {
    public final Tdlib tdlib;
    public final TdApi.File file;
    public final String mime;
    public final boolean isVideo;
    public final @Nullable String title, subtitle;
    public final long durationMs;
    public final @Nullable TdApi.File cover;

    public Item (Tdlib tdlib, TdApi.File file, String mime, boolean isVideo, @Nullable String title, @Nullable String subtitle, long durationMs, @Nullable TdApi.File cover) {
      this.tdlib = tdlib;
      this.file = file;
      this.mime = mime;
      this.isVideo = isVideo;
      this.title = title;
      this.subtitle = subtitle;
      this.durationMs = durationMs;
      this.cover = cover;
    }

    String key () {
      return tdlib.id() + ":" + file.id;
    }
  }

  private static final long TICK_MS = 500;
  private static final long USER_SEEK_MS = 1500;
  private static final long DRIFT_MS = 1200;
  private static final long COMMAND_SETTLE_MS = 2500;

  private static CastContext castContext;
  private static boolean contextFailed;
  private static @Nullable CastSession session;
  private static @Nullable Source source, pendingSource;
  private static @Nullable String deviceName;

  private static String lastKey;
  private static boolean lastPlaying;
  private static long lastLocalPos, lastTickAt, lastCommandAt, lastLoadAt;
  private static boolean errorShown;

  private static MediaRouter router;
  private static MediaRouteSelector selector;
  private static boolean scanning;
  private static final MediaRouter.Callback routerCallback = new MediaRouter.Callback() { };
  private static final Runnable stopScan = Tgx101Cast::stopScan;

  // Availability

  /** Google Play services are there to cast with — the menu item is shown only then */
  public static boolean isAvailable () {
    if (contextFailed) return false;
    try {
      return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(AppContext.get()) == ConnectionResult.SUCCESS;
    } catch (Throwable t) {
      return false;
    }
  }

  private static @Nullable CastContext context () {
    if (castContext == null && !contextFailed) {
      try {
        castContext = CastContext.getSharedInstance(AppContext.get());
        castContext.getSessionManager().addSessionManagerListener(sessionListener, CastSession.class);
        CastSession current = castContext.getSessionManager().getCurrentCastSession();
        if (current != null && current.isConnected()) {
          setSession(current);
        }
      } catch (Throwable t) {
        contextFailed = true;
        castContext = null;
        Tgx101Diag.mark("cast: unavailable " + t);
      }
    }
    return castContext;
  }

  public static boolean isConnected () {
    return session != null && session.isConnected();
  }

  public static boolean isCasting (Source s) {
    return isConnected() && source == s;
  }

  public static @Nullable String deviceName () {
    return deviceName;
  }

  /** The words for the ⋮ menu: «Cast», «Stop casting on …» or, while the other player casts, «Cast to …» */
  public static String menuTitle (Source s) {
    if (isConnected()) {
      String name = !StringUtils.isEmpty(deviceName) ? deviceName : "TV";
      return source == s ? Lang.getString(R.string.Tgx101CastStopOn, name) : Lang.getString(R.string.Tgx101CastHere, name);
    }
    return Lang.getString(R.string.Tgx101Cast);
  }

  /** The ⋮ menu is open: look for devices already, so the list is ready by the time «Cast» is tapped */
  public static void prewarm () {
    if (isAvailable() && !isConnected()) {
      startScan();
    }
  }

  // Menu action

  /** «Cast» in a ⋮ menu: pick a device, stop casting, or move the casting to this player */
  public static void onMenuItem (ViewController<?> c, Source s) {
    if (context() == null) {
      UI.showToast(R.string.Tgx101CastNone, Toast.LENGTH_LONG);
      return;
    }
    if (isConnected()) {
      if (source == s) {
        Tgx101Diag.mark("cast: stop by user");
        castContext.getSessionManager().endCurrentSession(true);
      } else {
        Tgx101Diag.mark("cast: moved to the other player");
        attach(s);
      }
      return;
    }
    if (Tgx101CastServer.localAddress() == null) {
      UI.showToast(R.string.Tgx101CastNoWifi, Toast.LENGTH_LONG);
      return;
    }
    startScan();
    List<MediaRouter.RouteInfo> routes = routes();
    if (!routes.isEmpty()) {
      showPicker(c, s, routes);
    } else {
      UI.showToast(R.string.Tgx101CastSearching, Toast.LENGTH_SHORT);
      waitForRoutes(c, s, SystemClock.uptimeMillis());
    }
  }

  private static void waitForRoutes (ViewController<?> c, Source s, long since) {
    UI.post(() -> {
      if (c.isDestroyed()) return;
      List<MediaRouter.RouteInfo> routes = routes();
      long waited = SystemClock.uptimeMillis() - since;
      if (!routes.isEmpty() && waited >= 1500) {
        showPicker(c, s, routes);
      } else if (waited >= 7000) {
        Tgx101Diag.mark("cast: no devices found");
        UI.showToast(R.string.Tgx101CastNone, Toast.LENGTH_LONG);
      } else {
        waitForRoutes(c, s, since);
      }
    }, 500);
  }

  private static void showPicker (ViewController<?> c, Source s, List<MediaRouter.RouteInfo> routes) {
    final int count = routes.size();
    int[] ids = new int[count];
    String[] names = new String[count];
    int[] icons = new int[count];
    for (int i = 0; i < count; i++) {
      MediaRouter.RouteInfo route = routes.get(i);
      ids[i] = i + 1;
      names[i] = route.getName();
      icons[i] = route.getDeviceType() == MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER ? R.drawable.baseline_speaker_24 : R.drawable.baseline_tv_24;
    }
    Tgx101Diag.mark("cast: picker with " + count + " device(s)");
    c.showOptions(Lang.getString(R.string.Tgx101CastTo), ids, names, null, icons, (itemView, id) -> {
      int index = id - 1;
      if (index >= 0 && index < count) {
        connect(s, routes.get(index));
      }
      return true;
    });
  }

  private static void connect (Source s, MediaRouter.RouteInfo route) {
    pendingSource = s;
    deviceName = route.getName();
    Tgx101Diag.mark("cast: connecting to " + route.getName());
    UI.showToast(Lang.getString(R.string.Tgx101CastConnecting, route.getName()), Toast.LENGTH_SHORT);
    try {
      router.selectRoute(route);
    } catch (Throwable t) {
      Tgx101Diag.mark("cast: select failed " + t);
      pendingSource = null;
      UI.showToast(Lang.getString(R.string.Tgx101CastFailed, route.getName()), Toast.LENGTH_LONG);
    }
  }

  /** A video started in the viewer: while the viewer casts, this video goes to the TV in place of the one before */
  public static void onViewerPlays (Source s) {
    Source current = source;
    if (current != null && current != s && current.castFollowsViewer() && s.castFollowsViewer() && isConnected()) {
      Tgx101Diag.mark("cast: the next video in the viewer");
      attach(s);
    }
  }

  /** A player is going away (the viewer closes): the TV goes on by itself, the phone no longer drives it */
  public static void detach (Source s) {
    if (pendingSource == s) {
      pendingSource = null;
    }
    if (source == s) {
      Tgx101Diag.mark("cast: player left, the TV plays on by itself");
      s.castSetMuted(false);
      source = null;
      lastKey = null;
    }
  }

  // Discovery

  private static List<MediaRouter.RouteInfo> routes () {
    List<MediaRouter.RouteInfo> result = new ArrayList<>();
    if (router == null || selector == null) return result;
    for (MediaRouter.RouteInfo route : router.getRoutes()) {
      if (!route.isDefaultOrBluetooth() && route.isEnabled() && route.matchesSelector(selector)) {
        result.add(route);
      }
    }
    return result;
  }

  private static void startScan () {
    CastContext ctx = context();
    if (ctx == null) return;
    try {
      if (router == null) {
        router = MediaRouter.getInstance(AppContext.get());
        selector = ctx.getMergedSelector();
      }
      if (selector == null) return;
      if (!scanning) {
        scanning = true;
        router.addCallback(selector, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
      }
      UI.cancel(stopScan);
      UI.post(stopScan, 30_000);
    } catch (Throwable t) {
      Tgx101Diag.mark("cast: scan failed " + t);
    }
  }

  private static void stopScan () {
    if (scanning && router != null) {
      scanning = false;
      router.removeCallback(routerCallback);
    }
  }

  // Session

  private static final SessionManagerListener<CastSession> sessionListener = new SessionManagerListener<CastSession>() {
    @Override public void onSessionStarting (@NonNull CastSession s) { }
    @Override public void onSessionStarted (@NonNull CastSession s, @NonNull String sessionId) {
      Tgx101Diag.mark("cast: connected");
      setSession(s);
    }
    @Override public void onSessionStartFailed (@NonNull CastSession s, int error) {
      Tgx101Diag.mark("cast: connect failed " + error);
      String name = deviceName != null ? deviceName : "TV";
      pendingSource = null;
      UI.showToast(Lang.getString(R.string.Tgx101CastFailed, name), Toast.LENGTH_LONG);
    }
    @Override public void onSessionEnding (@NonNull CastSession s) { }
    @Override public void onSessionEnded (@NonNull CastSession s, int error) {
      Tgx101Diag.mark("cast: ended " + error);
      endSession();
    }
    @Override public void onSessionResuming (@NonNull CastSession s, @NonNull String sessionId) { }
    @Override public void onSessionResumed (@NonNull CastSession s, boolean wasSuspended) {
      Tgx101Diag.mark("cast: resumed");
      setSession(s);
    }
    @Override public void onSessionResumeFailed (@NonNull CastSession s, int error) { }
    @Override public void onSessionSuspended (@NonNull CastSession s, int reason) {
      Tgx101Diag.mark("cast: suspended " + reason);
    }
  };

  private static void setSession (CastSession s) {
    session = s;
    stopScan();
    try {
      if (s.getCastDevice() != null) {
        deviceName = s.getCastDevice().getFriendlyName();
      }
    } catch (Throwable ignored) { }
    Source pending = pendingSource;
    pendingSource = null;
    if (pending != null) {
      attach(pending);
      UI.showToast(Lang.getString(R.string.Tgx101CastPlaying, deviceName != null ? deviceName : "TV"), Toast.LENGTH_SHORT);
    }
  }

  private static void endSession () {
    Source s = source;
    session = null;
    source = null;
    pendingSource = null;
    lastKey = null;
    UI.cancel(tick);
    Tgx101CastServer.stop();
    if (s != null) {
      // the sound doesn't jump to the phone's speaker: the player stops where the TV was
      s.castSetPlaying(false);
      s.castSetMuted(false);
    }
  }

  private static void attach (Source s) {
    Source old = source;
    if (old != null && old != s) {
      old.castSetPlaying(false);
      old.castSetMuted(false);
    }
    source = s;
    lastKey = null;
    errorShown = false;
    s.castSetMuted(true);
    UI.cancel(tick);
    UI.post(tick);
  }

  // Mirroring

  private static final Runnable tick = Tgx101Cast::tick;

  private static void tick () {
    final Source s = source;
    final CastSession cs = session;
    if (s == null || cs == null || !cs.isConnected()) {
      return;
    }
    UI.post(tick, TICK_MS);
    RemoteMediaClient client = cs.getRemoteMediaClient();
    Item item = s.castItem();
    if (client == null || item == null) {
      return;
    }
    final long now = SystemClock.uptimeMillis();
    boolean playing = s.castIsPlaying();
    long pos = s.castPosition();
    String key = item.key();
    if (!key.equals(lastKey)) {
      lastKey = key;
      load(client, item, pos, playing);
      lastCommandAt = lastLoadAt = now;
    } else {
      long expected = lastLocalPos + (lastPlaying ? now - lastTickAt : 0);
      if (Math.abs(pos - expected) > USER_SEEK_MS) {
        Tgx101Diag.mark("cast: seek to " + pos / 1000 + " s");
        client.seek(new MediaSeekOptions.Builder().setPosition(pos).build());
        lastCommandAt = now;
      } else if (playing != lastPlaying) {
        Tgx101Diag.mark(playing ? "cast: play" : "cast: pause");
        if (playing) client.play(); else client.pause();
        lastCommandAt = now;
      } else if (now - lastCommandAt > COMMAND_SETTLE_MS) {
        int state = client.getPlayerState();
        if (state == MediaStatus.PLAYER_STATE_PLAYING && playing) {
          long remote = client.getApproximateStreamPosition();
          if (Math.abs(remote - pos) > DRIFT_MS) {
            s.castSeekLocal(remote);
            pos = remote;
          }
        } else if (state == MediaStatus.PLAYER_STATE_PAUSED && playing) {
          Tgx101Diag.mark("cast: paused on the TV");
          s.castSetPlaying(false);
          playing = false;
        } else if (state == MediaStatus.PLAYER_STATE_PLAYING && !playing) {
          Tgx101Diag.mark("cast: played on the TV");
          s.castSetPlaying(true);
          playing = true;
        } else if (state == MediaStatus.PLAYER_STATE_IDLE && client.getIdleReason() == MediaStatus.IDLE_REASON_ERROR && !errorShown) {
          errorShown = true;
          Tgx101Diag.mark("cast: the TV failed to play " + item.mime);
          UI.showToast(R.string.Tgx101CastError, Toast.LENGTH_LONG);
        }
      }
    }
    lastLocalPos = pos;
    lastPlaying = playing;
    lastTickAt = now;
  }

  private static void load (RemoteMediaClient client, Item item, long pos, boolean playing) {
    String url = Tgx101CastServer.urlFor(item.tdlib, item.file, item.mime);
    if (url == null) {
      UI.showToast(R.string.Tgx101CastNoWifi, Toast.LENGTH_LONG);
      return;
    }
    MediaMetadata metadata = new MediaMetadata(item.isVideo ? MediaMetadata.MEDIA_TYPE_MOVIE : MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
    if (!StringUtils.isEmpty(item.title)) metadata.putString(MediaMetadata.KEY_TITLE, item.title);
    if (!StringUtils.isEmpty(item.subtitle)) metadata.putString(item.isVideo ? MediaMetadata.KEY_SUBTITLE : MediaMetadata.KEY_ARTIST, item.subtitle);
    if (item.cover != null && TD.isFileLoaded(item.cover)) {
      String coverUrl = Tgx101CastServer.urlFor(item.tdlib, item.cover, "image/jpeg");
      if (coverUrl != null) metadata.addImage(new WebImage(android.net.Uri.parse(coverUrl)));
    }
    MediaInfo.Builder info = new MediaInfo.Builder(url)
      .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
      .setContentType(item.mime)
      .setMetadata(metadata);
    if (item.durationMs > 0) info.setStreamDuration(item.durationMs);
    Tgx101Diag.mark("cast: load " + (item.isVideo ? "video " : "song ") + item.file.id + " " + item.mime + " at " + pos / 1000 + " s" + (playing ? "" : ", paused"));
    errorShown = false;
    client.load(new MediaLoadRequestData.Builder()
      .setMediaInfo(info.build())
      .setAutoplay(playing)
      .setCurrentTime(Math.max(0, pos))
      .build());
  }

  // Helpers for the sources

  static @Nullable Item itemFor (Tdlib tdlib, @Nullable TdApi.Message message) {
    if (tdlib == null || message == null || message.content == null) return null;
    if (message.content.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR) {
      TdApi.Audio audio = ((TdApi.MessageAudio) message.content).audio;
      TdApi.File cover = audio.albumCoverThumbnail != null ? audio.albumCoverThumbnail.file : null;
      String mime = !StringUtils.isEmpty(audio.mimeType) ? audio.mimeType : "audio/mpeg";
      String title = !StringUtils.isEmpty(audio.title) ? audio.title : audio.fileName;
      return new Item(tdlib, audio.audio, mime, false, title, audio.performer, audio.duration * 1000L, cover);
    }
    if (message.content.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR) {
      TdApi.VoiceNote voice = ((TdApi.MessageVoiceNote) message.content).voiceNote;
      String mime = !StringUtils.isEmpty(voice.mimeType) ? voice.mimeType : "audio/ogg";
      return new Item(tdlib, voice.voice, mime, false, null, null, voice.duration * 1000L, null);
    }
    return null;
  }

}
