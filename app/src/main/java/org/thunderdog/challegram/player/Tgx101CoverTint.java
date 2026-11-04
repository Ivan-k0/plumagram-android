/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import androidx.annotation.Nullable;
import androidx.palette.graphics.Palette;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageApicFile;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageLoader;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.core.Background;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

/**
 * (user 2026-11-04, player mockup) The colours of a track's cover for the player and its bar: a flat background of the cover's
 * main hue, dark or light by the theme, so the theme's text stays readable on it, and an accent for the seek bar and the active
 * buttons. A track without a cover keeps the theme's colours.
 */
public final class Tgx101CoverTint {
  private Tgx101CoverTint () { }

  public static final class Colors {
    private final int darkBackground, lightBackground, darkAccent, lightAccent;

    Colors (int darkBackground, int lightBackground, int darkAccent, int lightAccent) {
      this.darkBackground = darkBackground;
      this.lightBackground = lightBackground;
      this.darkAccent = darkAccent;
      this.lightAccent = lightAccent;
    }

    int background () {
      return Theme.isDark() ? darkBackground : lightBackground;
    }

    int accent () {
      return Theme.isDark() ? darkAccent : lightAccent;
    }
  }

  public interface Callback {
    void onColors (@Nullable Colors colors);
  }

  private static final Object NO_COVER = new Object();
  private static final LruCache<String, Object> CACHE = new LruCache<>(64);

  private static String key (Tdlib tdlib, TdApi.Message message) {
    return tdlib.id() + "_" + message.chatId + "_" + TD.getFile(message).id;
  }

  /** Answers at once when the colours are known, on the UI thread later otherwise */
  public static boolean request (Tdlib tdlib, TdApi.Message message, Callback callback) {
    if (!(message.content instanceof TdApi.MessageAudio)) {
      callback.onColors(null);
      return true;
    }
    final String key = key(tdlib, message);
    final Object known = CACHE.get(key);
    if (known != null) {
      callback.onColors(known instanceof Colors ? (Colors) known : null);
      return true;
    }
    final TdApi.Audio audio = ((TdApi.MessageAudio) message.content).audio;
    final Callback done = colors -> UI.post(() -> {
      // A file still loading may hold its picture inside: that answer isn't final
      if (colors != null || TD.isFileLoaded(audio.audio)) {
        CACHE.put(key, colors != null ? colors : NO_COVER);
      }
      callback.onColors(colors);
    });
    Background.instance().post(() -> {
      if (audio.albumCoverMinithumbnail != null) {
        Colors colors = fromBytes(audio.albumCoverMinithumbnail.data);
        if (colors != null) {
          done.onColors(colors);
          return;
        }
      }
      fromThumbnail(tdlib, message, audio, done);
    });
    return false;
  }

  private static void fromThumbnail (Tdlib tdlib, TdApi.Message message, TdApi.Audio audio, Callback done) {
    ImageFile thumbnail = TD.toImageFile(tdlib, audio.albumCoverThumbnail);
    if (thumbnail == null) {
      fromFile(tdlib, message, audio, done);
      return;
    }
    thumbnail.setNoCache();
    ImageLoader.instance().loadFile(thumbnail, (success, bitmap) -> {
      Colors colors = success ? fromBitmap(bitmap) : null;
      if (colors != null) {
        done.onColors(colors);
      } else {
        fromFile(tdlib, message, audio, done);
      }
    });
  }

  private static void fromFile (Tdlib tdlib, TdApi.Message message, TdApi.Audio audio, Callback done) {
    if (!TD.isFileLoaded(audio.audio)) {
      done.onColors(null);
      return;
    }
    // Its own key: the big cover of the player is a bitmap of the player's, this one is read and forgotten
    ImageApicFile picture = new ImageApicFile(tdlib, message) {
      @Override
      protected String buildImageKey () {
        return super.buildImageKey() + "_tint";
      }
    };
    picture.setSize(96);
    picture.setNoCache();
    ImageLoader.instance().loadFile(picture, (success, bitmap) -> done.onColors(success ? fromBitmap(bitmap) : null));
  }

  private static Colors fromBytes (byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return null;
    }
    try {
      return fromBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Colors fromBitmap (Bitmap bitmap) {
    if (bitmap == null || bitmap.isRecycled()) {
      return null;
    }
    try {
      Palette palette = Palette.from(bitmap).maximumColorCount(16).generate();
      Palette.Swatch main = palette.getDominantSwatch();
      if (main == null) {
        return null;
      }
      Palette.Swatch accent = palette.getVibrantSwatch();
      if (accent == null) accent = palette.getLightVibrantSwatch();
      if (accent == null) accent = palette.getDarkVibrantSwatch();
      if (accent == null) accent = palette.getLightMutedSwatch();
      if (accent == null) accent = main;
      float[] mainHsl = main.getHsl(), accentHsl = accent.getHsl();
      return new Colors(
        hsl(mainHsl[0], Math.min(mainHsl[1], .42f), .13f),
        hsl(mainHsl[0], Math.min(mainHsl[1], .5f), .93f),
        hsl(accentHsl[0], Math.min(accentHsl[1], .8f), clamp(accentHsl[2], .62f, .78f)),
        hsl(accentHsl[0], Math.max(accentHsl[1], Math.min(accentHsl[1] * 1.2f, .7f)), clamp(accentHsl[2], .32f, .44f))
      );
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static float clamp (float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  private static int hsl (float h, float s, float l) {
    return androidx.core.graphics.ColorUtils.HSLToColor(new float[] {h, s, l});
  }

  /** The colours in use by a screen: a change of a track cross-fades them */
  public static final class Animated implements FactorAnimator.Target {
    private final Runnable onChange;
    private final FactorAnimator animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 320L);
    private Colors from, to;
    private float factor = 1f;

    public Animated (Runnable onChange) {
      this.onChange = onChange;
    }

    public void set (@Nullable Colors colors, boolean animated) {
      if (to == colors) {
        return;
      }
      if (animated) {
        // In the middle of a fade: the new one starts from the nearer of the two
        from = factor < .5f ? from : to;
        to = colors;
        factor = 0f;
        animator.forceFactor(0f);
        animator.animateTo(1f);
      } else {
        animator.forceFactor(1f);
        from = to = colors;
        factor = 1f;
        onChange.run();
      }
    }

    public int background (int themeColor) {
      return ColorUtils.fromToArgb(from != null ? from.background() : themeColor, to != null ? to.background() : themeColor, factor);
    }

    public int accent (int themeColor) {
      return ColorUtils.fromToArgb(from != null ? from.accent() : themeColor, to != null ? to.accent() : themeColor, factor);
    }

    /** A translucent shade of the accent for the bar's unplayed part; the theme's own color without a cover */
    public int accentShade (int themeColor, float alpha) {
      int fromColor = from != null ? ColorUtils.alphaColor(alpha, from.accent()) : themeColor;
      int toColor = to != null ? ColorUtils.alphaColor(alpha, to.accent()) : themeColor;
      return ColorUtils.fromToArgb(fromColor, toColor, factor);
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      this.factor = factor;
      onChange.run();
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }
  }
}
