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
        result = translateHtml(text, toLanguage);
      } catch (Throwable t) {
        org.thunderdog.challegram.Tgx101Diag.mark("translate: new google failed " + t.getClass().getSimpleName() + ", the old one");
      }
      if (result == null) {
        try {
          result = translateImpl(text, toLanguage);
        } catch (Throwable t) {
          org.thunderdog.challegram.Tgx101Diag.mark("translate: google failed " + t.getClass().getSimpleName());
        }
      }
      callback.onResult(result);
    }, "Tgx101GoogleTranslate").start();
  }

  // (4PDA ausypher 2026-10-20 «криво переводит, то слово обрежет», user «делай уже»): the new Google engine — the one
  // Chrome's page translation uses (and NagramX's «new Google»). The whole text goes at once, the formatting rides
  // inside it as <a i=N> tags, so no piece is translated without its context any more
  private static final String HTML_URL = "https://translate-pa.googleapis.com/v1/translateHtml";
  private static final String HTML_KEY = "AIzaSyATBXajvzQLTDHEQbcpq0Ihe0vWDHmO520"; // Chrome's public translate key

  private static TdApi.FormattedText translateHtml (TdApi.FormattedText source, String to) throws Exception {
    String text = source.text;
    TdApi.TextEntity[] entities = source.entities != null ? source.entities : new TdApi.TextEntity[0];
    TreeSet<Integer> cuts = new TreeSet<>();
    cuts.add(0);
    cuts.add(text.length());
    for (TdApi.TextEntity e : entities) {
      cuts.add(Math.max(0, Math.min(text.length(), e.offset)));
      cuts.add(Math.max(0, Math.min(text.length(), e.offset + e.length)));
    }
    List<Integer> bounds = new ArrayList<>(cuts);
    int pieces = bounds.size() - 1;
    // a piece covered by any entity is tagged with its number, the rest goes as plain text
    boolean[] tagged = new boolean[pieces];
    for (TdApi.TextEntity e : entities) {
      for (int i = 0; i < pieces; i++) {
        if (bounds.get(i) >= e.offset && bounds.get(i + 1) <= e.offset + e.length) tagged[i] = true;
      }
    }
    StringBuilder html = new StringBuilder();
    for (int i = 0; i < pieces; i++) {
      String piece = escapeHtml(text.substring(bounds.get(i), bounds.get(i + 1)));
      if (tagged[i]) html.append("<a i=").append(i).append('>').append(piece).append("</a>");
      else html.append(piece);
    }
    org.json.JSONArray body = new org.json.JSONArray()
      .put(new org.json.JSONArray().put(new org.json.JSONArray().put(html.toString())).put("auto").put(to))
      .put("te_lib");
    HttpURLConnection connection = (HttpURLConnection) new URL(HTML_URL).openConnection();
    connection.setConnectTimeout(10000);
    connection.setReadTimeout(15000);
    connection.setDoOutput(true);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/json+protobuf");
    connection.setRequestProperty("X-Goog-API-Key", HTML_KEY);
    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
    String translatedHtml;
    try {
      try (java.io.OutputStream out = connection.getOutputStream()) {
        out.write(body.toString().getBytes("UTF-8"));
      }
      if (connection.getResponseCode() != 200) throw new java.io.IOException("HTTP " + connection.getResponseCode());
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (InputStream in = connection.getInputStream()) {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) buffer.write(b, 0, n);
      }
      translatedHtml = new JSONArray(buffer.toString("UTF-8")).getJSONArray(0).getString(0);
    } finally {
      connection.disconnect();
    }
    // back to text: where each tagged piece landed
    int[] start = new int[pieces], end = new int[pieces];
    java.util.Arrays.fill(start, -1);
    StringBuilder out = new StringBuilder();
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("<a i=(\\d+)>|</a>|<br\\s*/?>").matcher(translatedHtml);
    int last = 0, open = -1;
    while (m.find()) {
      out.append(unescapeHtml(translatedHtml.substring(last, m.start())));
      last = m.end();
      String tag = m.group();
      if (tag.startsWith("<br")) {
        out.append('\n');
        if (last < translatedHtml.length() && translatedHtml.charAt(last) == ' ') last++; // «<br> Собрать»
      } else if (m.group(1) != null) {
        open = Integer.parseInt(m.group(1));
        if (open >= 0 && open < pieces) start[open] = out.length();
      } else {
        if (open >= 0 && open < pieces) end[open] = out.length();
        open = -1;
        // «потребичек</a> .» — no space Google puts between a tag and the punctuation after it
        if (last < translatedHtml.length() - 1 && translatedHtml.charAt(last) == ' ' && ".,!?:;)»".indexOf(translatedHtml.charAt(last + 1)) != -1) last++;
      }
    }
    out.append(unescapeHtml(translatedHtml.substring(last)));
    List<TdApi.TextEntity> mapped = new ArrayList<>();
    for (TdApi.TextEntity e : entities) {
      int from = Integer.MAX_VALUE, until = -1;
      for (int i = 0; i < pieces; i++) {
        if (!tagged[i] || start[i] < 0 || bounds.get(i) < e.offset || bounds.get(i + 1) > e.offset + e.length) continue;
        from = Math.min(from, start[i]);
        until = Math.max(until, end[i]);
      }
      if (until > from) mapped.add(new TdApi.TextEntity(from, until - from, e.type));
    }
    org.thunderdog.challegram.Tgx101Diag.mark("translate: new google, " + text.length() + " → " + out.length() + " chars, " + mapped.size() + "/" + entities.length + " entities");
    return new TdApi.FormattedText(out.toString(), mapped.toArray(new TdApi.TextEntity[0]));
  }

  private static String escapeHtml (String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
  }

  private static String unescapeHtml (String text) {
    return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
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
