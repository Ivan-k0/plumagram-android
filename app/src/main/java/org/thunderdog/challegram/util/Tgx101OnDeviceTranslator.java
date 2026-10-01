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
package org.thunderdog.challegram.util;

import androidx.annotation.Nullable;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.tool.UI;

/**
 * TGx101: translation on the phone with Google ML Kit (the «Google» translator option). The source language is
 * detected on the phone too; each language model (~30 MB) is downloaded once, after that it works offline.
 * The result comes back on the UI thread; null means "can't" (unknown or unsupported language, no model, error) —
 * the caller then uses Telegram's translation.
 */
public final class Tgx101OnDeviceTranslator {
  private Tgx101OnDeviceTranslator () { }

  public interface Callback {
    void onResult (@Nullable String translated);
  }

  public static void translate (String text, String toLanguageTag, Callback callback) {
    final String target = TranslateLanguage.fromLanguageTag(baseLanguage(toLanguageTag));
    if (target == null || text == null || text.trim().isEmpty()) {
      done(callback, null, "target language not supported: " + toLanguageTag);
      return;
    }
    try {
      LanguageIdentification.getClient().identifyLanguage(text)
        .addOnSuccessListener(language -> {
          String source = "und".equals(language) ? null : TranslateLanguage.fromLanguageTag(baseLanguage(language));
          if (source == null) {
            done(callback, null, "source language unknown: " + language);
            return;
          }
          if (source.equals(target)) {
            done(callback, text, null);
            return;
          }
          Translator translator = Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build());
          translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
            .addOnSuccessListener(ignored -> translator.translate(text)
              .addOnSuccessListener(result -> {
                translator.close();
                done(callback, result, null);
              })
              .addOnFailureListener(e -> {
                translator.close();
                done(callback, null, "translate failed: " + e.getClass().getSimpleName());
              }))
            .addOnFailureListener(e -> {
              translator.close();
              done(callback, null, "model download failed: " + e.getClass().getSimpleName());
            });
        })
        .addOnFailureListener(e -> done(callback, null, "language id failed: " + e.getClass().getSimpleName()));
    } catch (Throwable t) {
      done(callback, null, "ML Kit unavailable: " + t.getClass().getSimpleName());
    }
  }

  private static String baseLanguage (String tag) {
    if (tag == null) return "";
    int dash = tag.indexOf('-');
    if (dash == -1) dash = tag.indexOf('_');
    return (dash > 0 ? tag.substring(0, dash) : tag).toLowerCase(java.util.Locale.ROOT);
  }

  private static void done (Callback callback, @Nullable String result, @Nullable String failure) {
    if (failure != null) {
      Tgx101Diag.mark("translate on device: " + failure + " — Telegram translation instead");
    }
    UI.post(() -> callback.onResult(result));
  }
}
