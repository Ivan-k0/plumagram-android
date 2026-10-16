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
package org.thunderdog.challegram.data;

import android.util.Base64;

import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

/**
 * TGx101 (user 2026-10-17 «прикрути Cloudflare для голосовых»): voice messages recognized by Whisper on Cloudflare
 * Workers AI, with the user's own Cloudflare account (its ID and an API token with «Workers AI» rights — the free
 * daily allowance is plenty for voice messages). No key is built into the app: a public APK can't keep one secret.
 */
public final class Tgx101Cloudflare {
  private Tgx101Cloudflare () { }

  private static final String MODEL = "@cf/openai/whisper-large-v3-turbo";
  private static final int CHUNK_SECONDS = 120; // long voice messages go in parts: one request stays small
  private static final int SAMPLE_RATE = 16000;

  public static boolean isConfigured () {
    return !isEmpty(Settings.instance().tgx101CloudflareAccount()) && !isEmpty(Settings.instance().tgx101CloudflareToken());
  }

  private static boolean isEmpty (@Nullable String s) {
    return s == null || s.trim().isEmpty();
  }

  /** {@code pcm}: 16 kHz mono 16-bit. Blocking — call off the main thread */
  public static String recognize (byte[] pcm) throws IOException {
    String account = Settings.instance().tgx101CloudflareAccount().trim();
    String token = Settings.instance().tgx101CloudflareToken().trim();
    int chunkBytes = CHUNK_SECONDS * SAMPLE_RATE * 2;
    StringBuilder out = new StringBuilder();
    for (int offset = 0; offset < pcm.length; offset += chunkBytes) {
      int length = Math.min(chunkBytes, pcm.length - offset);
      if (length < SAMPLE_RATE / 5 * 2 && offset > 0) break; // a tail shorter than 0.2 s
      String text = recognizeChunk(account, token, wav(pcm, offset, length));
      if (!text.isEmpty()) {
        if (out.length() > 0) out.append(' ');
        out.append(text.trim());
      }
    }
    return out.toString();
  }

  private static String recognizeChunk (String account, String token, byte[] wav) throws IOException {
    URL url = new URL("https://api.cloudflare.com/client/v4/accounts/" + account + "/ai/run/" + MODEL);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    try {
      connection.setRequestMethod("POST");
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(90000);
      connection.setDoOutput(true);
      connection.setRequestProperty("Authorization", "Bearer " + token);
      connection.setRequestProperty("Content-Type", "application/json");
      JSONObject body = new JSONObject();
      body.put("audio", Base64.encodeToString(wav, Base64.NO_WRAP));
      byte[] bytes = body.toString().getBytes(Charset.forName("UTF-8"));
      connection.setFixedLengthStreamingMode(bytes.length);
      try (OutputStream os = connection.getOutputStream()) {
        os.write(bytes);
      }
      int code = connection.getResponseCode();
      String response = read(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
      if (code != 200) {
        throw new IOException("Cloudflare HTTP " + code + ": " + (response.length() > 200 ? response.substring(0, 200) : response));
      }
      JSONObject json = new JSONObject(response);
      JSONObject result = json.optJSONObject("result");
      if (!json.optBoolean("success", true) || result == null) {
        throw new IOException("Cloudflare: " + (response.length() > 200 ? response.substring(0, 200) : response));
      }
      return result.optString("text", "");
    } catch (org.json.JSONException e) {
      throw new IOException(e);
    } finally {
      connection.disconnect();
    }
  }

  private static String read (@Nullable InputStream is) throws IOException {
    if (is == null) return "";
    try (InputStream in = is) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int n;
      while ((n = in.read(buffer)) != -1) bos.write(buffer, 0, n);
      return new String(bos.toByteArray(), Charset.forName("UTF-8"));
    }
  }

  private static byte[] wav (byte[] pcm, int offset, int length) {
    ByteBuffer b = ByteBuffer.allocate(44 + length).order(ByteOrder.LITTLE_ENDIAN);
    b.put(new byte[] {'R', 'I', 'F', 'F'}).putInt(36 + length).put(new byte[] {'W', 'A', 'V', 'E'});
    b.put(new byte[] {'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1)
      .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort((short) 2).putShort((short) 16);
    b.put(new byte[] {'d', 'a', 't', 'a'}).putInt(length).put(pcm, offset, length);
    return b.array();
  }
}
