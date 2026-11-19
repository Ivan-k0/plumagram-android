package org.thunderdog.challegram.player;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.CastMediaOptions;

import java.util.List;

/**
 * TGx101 (user 2026-11-20 «chromecast»): the TV side is Google's default media receiver — it plays a URL it is given.
 * Its own notification and media session are off: our player keeps its own notification and drives the TV itself.
 */
public final class Tgx101CastOptions implements OptionsProvider {
  @NonNull
  @Override
  public CastOptions getCastOptions (@NonNull Context context) {
    CastMediaOptions mediaOptions = new CastMediaOptions.Builder()
      .setNotificationOptions(null)
      .setMediaSessionEnabled(false)
      .build();
    return new CastOptions.Builder()
      .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
      .setCastMediaOptions(mediaOptions)
      .setStopReceiverApplicationWhenEndingSession(true)
      .build();
  }

  @Override
  public List<SessionProvider> getAdditionalSessionProviders (@NonNull Context context) {
    return null;
  }
}
