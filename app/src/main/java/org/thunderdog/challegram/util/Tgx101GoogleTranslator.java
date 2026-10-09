/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/plumagram-android)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.util;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * TGx101 (user 2026-10-09 «завезём новый гугл»): online Google translation without a key — the public
 * translate.googleapis.com «gtx» endpoint, the one browsers' translate extensions use. Called only when the user taps
 * «Translate». Formatting survives: the text is cut at every entity boundary, each piece is translated on its own and
 * the entities are laid over the translated pieces again.
 */
public final class Tgx101GoogleTranslator {
  private Tgx101GoogleTranslator () { }

  public interface Callback {
    void onResult (@Nullable TdApi.FormattedText translated);
  }

  private static final int MAX_PIECES = 40;

  public static void translate (TdApi.FormattedText text, String toLanguage, Callback callback) {
    new Thread(() -> {
      TdApi.FormattedText result = null;
      try {
        result = translateImpl(text, toLanguage);
      } catch (Throwable t) {
        org.thunderdog.challegram.Tgx101Diag.mark("translate: google failed " + t.getClass().getSimpleName());
      }
      callback.onResult(result);
    }, "Tgx101GoogleTranslate").start();
  }

  private static TdApi.FormattedText translateImpl (TdApi.FormattedText source, String to) throws Exception {
    String text = source.text;
    TdApi.TextEntity[] entities = source.entities != null ? source.entities : new TdApi.TextEntity[0];
    TreeSet<Integer> cuts = new TreeSet<>();
    cuts.add(0);
    cuts.add(text.length());
    for (TdApi.TextEntity e : entities) {
      cuts.add(Math.max(0, Math.min(text.length(), e.offset)));
      cuts.add(Math.max(0, Math.min(text.length(), e.offset + e.length)));
    }
    if (cuts.size() - 1 > MAX_PIECES) {
      // too many pieces: the text alone, without formatting
      return new TdApi.FormattedText(request(text, to), new TdApi.TextEntity[0]);
    }
    List<Integer> bounds = new ArrayList<>(cuts);
    int[] newStart = new int[bounds.size()];
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < bounds.size() - 1; i++) {
      newStart[i] = out.length();
      String piece = text.substring(bounds.get(i), bounds.get(i + 1));
      out.append(piece.trim().isEmpty() ? piece : keepSpaces(piece, request(piece.trim(), to)));
    }
    newStart[bounds.size() - 1] = out.length();
    List<TdApi.TextEntity> mapped = new ArrayList<>();
    for (TdApi.TextEntity e : entities) {
      int a = bounds.indexOf(Math.max(0, Math.min(text.length(), e.offset)));
      int b = bounds.indexOf(Math.max(0, Math.min(text.length(), e.offset + e.length)));
      if (a < 0 || b < 0 || newStart[b] <= newStart[a]) continue;
      mapped.add(new TdApi.TextEntity(newStart[a], newStart[b] - newStart[a], e.type));
    }
    return new TdApi.FormattedText(out.toString(), mapped.toArray(new TdApi.TextEntity[0]));
  }

  /** the piece's own leading / trailing spaces and line breaks stay */
  private static String keepSpaces (String original, String translated) {
    int start = 0, end = original.length();
    while (start < end && Character.isWhitespace(original.charAt(start))) start++;
    while (end > start && Character.isWhitespace(original.charAt(end - 1))) end--;
    return original.substring(0, start) + translated + original.substring(end);
  }

  private static String request (String text, String to) throws Exception {
    URL url = new URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" + URLEncoder.encode(to, "UTF-8") + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8"));
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(10000);
    connection.setReadTimeout(15000);
    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
    try {
      if (connection.getResponseCode() != 200) throw new java.io.IOException("HTTP " + connection.getResponseCode());
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (InputStream in = connection.getInputStream()) {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) buffer.write(b, 0, n);
      }
      JSONArray sentences = new JSONArray(buffer.toString("UTF-8")).getJSONArray(0);
      StringBuilder result = new StringBuilder();
      for (int i = 0; i < sentences.length(); i++) {
        JSONArray s = sentences.optJSONArray(i);
        if (s != null && !s.isNull(0)) result.append(s.getString(0));
      }
      return result.toString();
    } finally {
      connection.disconnect();
    }
  }
}
