/*
 * This file is a part of PlumaGram
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

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.core.Background;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.lambda.RunnableData;

/**
 * (user 2026-11-05) Hints for the wave from outside Telegram, only when switched on with a warning: what is similar, as names to look
 * for in the account's chats. No files come from there. Deezer has an open API for similar artists; the radio of YouTube Music has
 * only its site's internal API, which may change or stop at any moment: any failure means just no hints.
 */
final class Tgx101WaveHints {
  private static final int TIMEOUT_MS = 10000;
  private static final String YOUTUBE_CLIENT = "{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20241202.01.00\"}}";

  private Tgx101WaveHints () { }

  /** Search queries, on a background thread; the callback gets them on the same */
  static void find (@Nullable String artist, @Nullable String title, RunnableData<List<String>> callback) {
    Background.instance().post(() -> {
      List<String> hints = new ArrayList<>();
      if (artist != null && !artist.isEmpty()) {
        try {
          similarArtists(artist, hints);
        } catch (Throwable ignored) { }
        try {
          youtubeRadio(title != null && !title.isEmpty() ? artist + " " + title : artist, hints);
        } catch (Throwable ignored) { }
      }
      callback.runWithData(hints);
    });
  }

  // Deezer

  private static void similarArtists (String artist, List<String> hints) throws Exception {
    JSONObject search = new JSONObject(get("https://api.deezer.com/search/artist?limit=1&q=" + URLEncoder.encode(artist, "UTF-8")));
    JSONArray found = search.optJSONArray("data");
    if (found == null || found.length() == 0) return;
    long id = found.getJSONObject(0).getLong("id");
    JSONObject related = new JSONObject(get("https://api.deezer.com/artist/" + id + "/related?limit=8"));
    JSONArray artists = related.optJSONArray("data");
    if (artists == null) return;
    for (int i = 0; i < artists.length(); i++) {
      String name = artists.getJSONObject(i).optString("name");
      if (!name.isEmpty()) hints.add(name);
    }
  }

  // YouTube Music

  private static void youtubeRadio (String query, List<String> hints) throws Exception {
    JSONObject search = new JSONObject(post("search", "{\"context\":" + YOUTUBE_CLIENT + ",\"query\":" + JSONObject.quote(query) + ",\"params\":\"EgWKAQIIAWoKEAoQCRADEAQQBQ==\"}"));
    String videoId = firstVideoId(search);
    if (videoId == null) return;
    JSONObject radio = new JSONObject(post("next", "{\"context\":" + YOUTUBE_CLIENT + ",\"videoId\":" + JSONObject.quote(videoId) + ",\"playlistId\":" + JSONObject.quote("RDAMVM" + videoId) + ",\"isAudioOnly\":true}"));
    List<JSONObject> tracks = new ArrayList<>();
    collect(radio, "playlistPanelVideoRenderer", tracks);
    for (JSONObject track : tracks) {
      String title = firstRun(track.optJSONObject("title")), author = firstRun(track.optJSONObject("longBylineText"));
      if (title != null) hints.add(author != null ? author + " " + title : title);
      if (hints.size() >= 40) break;
    }
  }

  private static @Nullable String firstRun (@Nullable JSONObject text) {
    JSONArray runs = text != null ? text.optJSONArray("runs") : null;
    JSONObject run = runs != null ? runs.optJSONObject(0) : null;
    String value = run != null ? run.optString("text") : null;
    return value == null || value.isEmpty() ? null : value;
  }

  private static @Nullable String firstVideoId (JSONObject search) {
    List<JSONObject> endpoints = new ArrayList<>();
    collect(search, "watchEndpoint", endpoints);
    for (JSONObject endpoint : endpoints) {
      String id = endpoint.optString("videoId");
      if (!id.isEmpty()) return id;
    }
    return null;
  }

  /** The answers are deep and change often: by the name of the piece, wherever it is */
  private static void collect (Object node, String name, List<JSONObject> out) {
    if (node instanceof JSONObject) {
      JSONObject object = (JSONObject) node;
      for (java.util.Iterator<String> it = object.keys(); it.hasNext(); ) {
        String key = it.next();
        Object value = object.opt(key);
        if (key.equals(name) && value instanceof JSONObject) {
          out.add((JSONObject) value);
        } else {
          collect(value, name, out);
        }
      }
    } else if (node instanceof JSONArray) {
      JSONArray array = (JSONArray) node;
      for (int i = 0; i < array.length(); i++) collect(array.opt(i), name, out);
    }
  }

  // Requests

  private static String get (String url) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    try {
      connection.setConnectTimeout(TIMEOUT_MS);
      connection.setReadTimeout(TIMEOUT_MS);
      connection.setRequestProperty("User-Agent", "PlumaGram");
      return read(connection);
    } finally {
      connection.disconnect();
    }
  }

  private static String post (String method, String body) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL("https://music.youtube.com/youtubei/v1/" + method + "?prettyPrint=false").openConnection();
    try {
      connection.setConnectTimeout(TIMEOUT_MS);
      connection.setReadTimeout(TIMEOUT_MS);
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("User-Agent", "Mozilla/5.0");
      try (OutputStream out = connection.getOutputStream()) {
        out.write(body.getBytes("UTF-8"));
      }
      return read(connection);
    } finally {
      connection.disconnect();
    }
  }

  private static String read (HttpURLConnection connection) throws Exception {
    if (connection.getResponseCode() != 200) throw new java.io.IOException("HTTP " + connection.getResponseCode());
    try (InputStream in = connection.getInputStream()) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int count;
      while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
      return new String(out.toByteArray(), "UTF-8");
    }
  }
}
