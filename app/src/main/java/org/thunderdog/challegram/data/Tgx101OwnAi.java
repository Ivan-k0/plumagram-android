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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TGx101 «Свой ИИ» (user 2026-11-13 «дать возможность людям привязывать свои ИИ… у кого нет премиум»): the AI features
 * (fix, shorten, style, translate, own request, «Кратко», «Что пропустил», the Storage sorting and tags) answered by an AI
 * service the person has a key for, when Telegram's own AI says no (no Premium, the free requests are over) — or always.
 *
 * Every request goes through {@link #send}: it asks Telegram first (or not, by the chosen mode), and on its refusal asks the
 * person's service, answering the caller with a {@link TdApi.FormattedText} — what every caller already reads.
 *
 * The key never leaves the phone except to its own service: kept in a private file apart from the settings (so the
 * settings backup never carries it), encrypted with a key of the Android keystore (Android 6+), never written to the log.
 */
public final class Tgx101OwnAi {
  private Tgx101OwnAi () { }

  // Services

  public static final int KIND_OPENAI = 0, KIND_ANTHROPIC = 1;

  public static final class Provider {
    public final int id;
    public final String name;
    public final String baseUrl;
    public final String defaultModel;
    public final int kind;
    public final boolean needsKey;
    public final String keyHint;

    Provider (int id, String name, String baseUrl, String defaultModel, int kind, boolean needsKey, String keyHint) {
      this.id = id;
      this.name = name;
      this.baseUrl = baseUrl;
      this.defaultModel = defaultModel;
      this.kind = kind;
      this.needsKey = needsKey;
      this.keyHint = keyHint;
    }
  }

  public static final int PROVIDER_OPENAI = 1, PROVIDER_GEMINI = 2, PROVIDER_CLAUDE = 3, PROVIDER_DEEPSEEK = 4,
    PROVIDER_OPENROUTER = 5, PROVIDER_CUSTOM = 6;

  public static final Provider[] PROVIDERS = {
    new Provider(PROVIDER_OPENAI, "OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini", KIND_OPENAI, true, "sk-…"),
    new Provider(PROVIDER_GEMINI, "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", KIND_OPENAI, true, "AIza…"),
    new Provider(PROVIDER_CLAUDE, "Claude", "https://api.anthropic.com/v1", "claude-opus-5-5", KIND_ANTHROPIC, true, "sk-ant-…"),
    new Provider(PROVIDER_DEEPSEEK, "DeepSeek", "https://api.deepseek.com", "deepseek-chat", KIND_OPENAI, true, "sk-…"),
    new Provider(PROVIDER_OPENROUTER, "OpenRouter", "https://openrouter.ai/api/v1", "openrouter/auto", KIND_OPENAI, true, "sk-or-…"),
    new Provider(PROVIDER_CUSTOM, null, "", "", KIND_OPENAI, false, "")
  };

  public static @Nullable Provider provider (int id) {
    for (Provider p : PROVIDERS) if (p.id == id) return p;
    return null;
  }

  private static final java.nio.charset.Charset UTF8 = java.nio.charset.Charset.forName("UTF-8"); // StandardCharsets: Android 4.4+

  // Settings (no secrets here: these go into the settings backup)

  public static final int MODE_TELEGRAM_FIRST = 0, MODE_ALWAYS_OWN = 1, MODE_TELEGRAM_ONLY = 2;

  private static final String KEY_PROVIDER = "tgx101_ownai_provider";
  private static final String KEY_MODE = "tgx101_ownai_mode";
  private static final String KEY_MODEL = "tgx101_ownai_model_";
  private static final String KEY_URL = "tgx101_ownai_url";

  public static int providerId () {
    return Settings.instance().pmc().getInt(KEY_PROVIDER, 0);
  }

  public static void setProviderId (int id) {
    Settings.instance().pmc().putInt(KEY_PROVIDER, id);
  }

  public static int mode () {
    return Settings.instance().pmc().getInt(KEY_MODE, MODE_TELEGRAM_FIRST);
  }

  public static void setMode (int mode) {
    Settings.instance().pmc().putInt(KEY_MODE, mode);
  }

  public static String model (int providerId) {
    String m = Settings.instance().pmc().getString(KEY_MODEL + providerId, null);
    Provider p = provider(providerId);
    return !TextUtils.isEmpty(m) ? m : p != null ? p.defaultModel : "";
  }

  public static void setModel (int providerId, @Nullable String model) {
    if (TextUtils.isEmpty(model)) Settings.instance().pmc().remove(KEY_MODEL + providerId);
    else Settings.instance().pmc().putString(KEY_MODEL + providerId, model.trim());
  }

  public static String customUrl () {
    return Settings.instance().pmc().getString(KEY_URL, "");
  }

  public static void setCustomUrl (@Nullable String url) {
    Settings.instance().pmc().putString(KEY_URL, url != null ? url.trim() : "");
  }

  /** (user 2026-11-13 «кратко должно работать и с чужими ИИ»): with the person's own AI any long post can be summed up,
   *  not only those Telegram marks */
  public static boolean canSummarizeLong (@Nullable String text) {
    return text != null && text.length() >= 600 && mode() != MODE_TELEGRAM_ONLY && isReady();
  }

  /** A service is chosen and has what it needs to answer */
  public static boolean isReady () {
    int id = providerId();
    Provider p = provider(id);
    if (p == null) return false;
    if (id == PROVIDER_CUSTOM) return !TextUtils.isEmpty(customUrl()) && !TextUtils.isEmpty(model(id));
    return !TextUtils.isEmpty(key(id)) && !TextUtils.isEmpty(model(id));
  }

  public static String displayName (Context context, int id) {
    Provider p = provider(id);
    if (p == null) return "";
    return p.name != null ? p.name : org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.Tgx101OwnAiCustom);
  }

  // The key: a private file of its own, encrypted on Android 6+

  private static final String PREFS = "tgx101_ownai_secret";
  private static final String KEYSTORE_ALIAS = "tgx101_ownai";

  private static SharedPreferences secrets () {
    return org.thunderdog.challegram.unsorted.AppContext.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  public static boolean hasKey (int providerId) {
    return secrets().contains("k" + providerId);
  }

  /** «sk-…a1b2» — what the settings show; never the key */
  public static String maskedKey (int providerId) {
    String k = key(providerId);
    if (TextUtils.isEmpty(k)) return "";
    String tail = k.length() > 4 ? k.substring(k.length() - 4) : "";
    return "••••" + tail;
  }

  public static void setKey (int providerId, @Nullable String key) {
    SharedPreferences.Editor e = secrets().edit();
    if (TextUtils.isEmpty(key)) {
      e.remove("k" + providerId);
    } else {
      String stored = encrypt(key.trim());
      if (stored == null) return;
      e.putString("k" + providerId, stored);
    }
    e.apply();
  }

  private static @Nullable String key (int providerId) {
    String stored = secrets().getString("k" + providerId, null);
    return stored != null ? decrypt(stored) : null;
  }

  private static @Nullable String encrypt (String plain) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return "p:" + Base64.encodeToString(plain.getBytes(UTF8), Base64.NO_WRAP); // no keystore AES before 6
    }
    try {
      javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey());
      byte[] iv = cipher.getIV();
      byte[] data = cipher.doFinal(plain.getBytes(UTF8));
      return "g:" + Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP);
    } catch (Throwable t) {
      diag("key not stored — " + t.getClass().getSimpleName());
      return null;
    }
  }

  private static @Nullable String decrypt (String stored) {
    try {
      if (stored.startsWith("p:")) {
        return new String(Base64.decode(stored.substring(2), Base64.NO_WRAP), UTF8);
      }
      if (stored.startsWith("g:") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        String[] parts = stored.substring(2).split(":", 2);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey(), new javax.crypto.spec.GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), UTF8);
      }
    } catch (Throwable t) {
      diag("key not read — " + t.getClass().getSimpleName());
    }
    return null;
  }

  @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
  private static javax.crypto.SecretKey secretKey () throws Exception {
    java.security.KeyStore store = java.security.KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    java.security.Key existing = store.getKey(KEYSTORE_ALIAS, null);
    if (existing instanceof javax.crypto.SecretKey) return (javax.crypto.SecretKey) existing;
    javax.crypto.KeyGenerator generator = javax.crypto.KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(new android.security.keystore.KeyGenParameterSpec.Builder(KEYSTORE_ALIAS,
      android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
      .build());
    return generator.generateKey();
  }

  // Requests

  private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "tgx101-own-ai");
    t.setDaemon(true);
    return t;
  });

  private static void diag (String what) {
    org.thunderdog.challegram.Tgx101Diag.mark("own ai: " + what); // never the key, never the texts
  }

  /** Telegram's AI with the person's own as the way out — or the own one only, or Telegram's only, by the mode */
  public static void send (Tdlib tdlib, TdApi.Function<?> function, Client.ResultHandler handler) {
    int mode = mode();
    if (mode == MODE_ALWAYS_OWN && isReady()) {
      runOwn(tdlib, function, handler, null);
      return;
    }
    tdlib.client().send(function, result -> {
      if (result.getConstructor() == TdApi.Error.CONSTRUCTOR && mode != MODE_TELEGRAM_ONLY && isReady()) {
        diag("Telegram said " + ((TdApi.Error) result).message + " — asking " + providerId());
        runOwn(tdlib, function, handler, (TdApi.Error) result);
      } else {
        handler.onResult(result);
      }
    });
  }

  /** The same for callers of {@code tdlib.send(function, (result, error) -> …)} */
  @SuppressWarnings("unchecked")
  public static <T extends TdApi.Object> void send (Tdlib tdlib, TdApi.Function<T> function, Tdlib.ResultHandler<T> handler) {
    send(tdlib, (TdApi.Function<?>) function, (Client.ResultHandler) result -> {
      if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) handler.onResult(null, (TdApi.Error) result);
      else handler.onResult((T) result, null);
    });
  }

  private static final String TAIL = " Reply with the result only — no introduction, no quotes around it, no explanations.";

  private static void runOwn (Tdlib tdlib, TdApi.Function<?> function, Client.ResultHandler handler, @Nullable TdApi.Error telegramError) {
    switch (function.getConstructor()) {
      case TdApi.FixTextWithAi.CONSTRUCTOR: {
        TdApi.FixTextWithAi f = (TdApi.FixTextWithAi) function;
        ask("Fix spelling, punctuation, grammar and typos in the user's text. Keep its meaning, words, tone and language; change nothing else." + TAIL,
          f.text != null ? f.text.text : "", handler, telegramError);
        break;
      }
      case TdApi.ComposeTextWithAi.CONSTRUCTOR: {
        TdApi.ComposeTextWithAi f = (TdApi.ComposeTextWithAi) function;
        ask(composeSystem(f.translateToLanguageCode, f.styleName, null, f.addEmojis), f.text != null ? f.text.text : "", handler, telegramError);
        break;
      }
      case TdApi.ComposeRichMessageWithAi.CONSTRUCTOR: {
        TdApi.ComposeRichMessageWithAi f = (TdApi.ComposeRichMessageWithAi) function;
        String text = f.message != null && f.message.source instanceof TdApi.RichMessageSourceMarkdown ? ((TdApi.RichMessageSourceMarkdown) f.message.source).text : "";
        ask(composeSystem(f.translateToLanguageCode, f.styleName, f.customPrompt, f.addEmojis), text, handler, telegramError);
        break;
      }
      case TdApi.SummarizeMessage.CONSTRUCTOR: {
        TdApi.SummarizeMessage f = (TdApi.SummarizeMessage) function;
        tdlib.client().send(new TdApi.GetMessage(f.chatId, f.messageId), m -> {
          if (m.getConstructor() != TdApi.Message.CONSTRUCTOR) {
            handler.onResult(telegramError != null ? telegramError : m);
            return;
          }
          TdApi.FormattedText t = org.thunderdog.challegram.ui.Tgx101SavedKit.text((TdApi.Message) m);
          String to = !TextUtils.isEmpty(f.translateToLanguageCode) ? " Write it in the language with the code \"" + f.translateToLanguageCode + "\"." : " Write it in the language of the text.";
          ask("Summarize the user's text briefly: the main points in 2–5 short sentences or bullet lines." + to + TAIL,
            t != null ? t.text : "", handler, telegramError);
        });
        break;
      }
      default:
        handler.onResult(telegramError != null ? telegramError : new TdApi.Error(400, "UNSUPPORTED"));
    }
  }

  private static String composeSystem (@Nullable String translateTo, @Nullable String style, @Nullable String customPrompt, boolean emojis) {
    StringBuilder b = new StringBuilder();
    if (!TextUtils.isEmpty(customPrompt)) {
      b.append("Do what this request asks with the user's text: ").append(customPrompt.trim()).append('.');
    } else if (!TextUtils.isEmpty(translateTo)) {
      b.append("Translate the user's text into the language with the code \"").append(translateTo).append("\". Keep the meaning, tone and formatting.");
    } else if (!TextUtils.isEmpty(style)) {
      b.append("Rewrite the user's text in the style \"").append(style).append("\". Keep its meaning and language.");
    } else {
      b.append("Improve the user's text: clearer and shorter, same meaning and language.");
    }
    if (!TextUtils.isEmpty(customPrompt) && !TextUtils.isEmpty(translateTo)) {
      b.append(" Write the answer in the language with the code \"").append(translateTo).append("\".");
    } else if (!TextUtils.isEmpty(customPrompt)) {
      b.append(" Unless the request says otherwise, answer in the language of the request.");
    }
    if (emojis) b.append(" Add a few fitting emoji.");
    return b.append(TAIL).toString();
  }

  private static void ask (String system, String text, Client.ResultHandler handler, @Nullable TdApi.Error telegramError) {
    if (TextUtils.isEmpty(text)) {
      handler.onResult(telegramError != null ? telegramError : new TdApi.Error(400, "EMPTY_TEXT"));
      return;
    }
    worker.execute(() -> {
      try {
        String answer = complete(system, text);
        diag("answered, " + answer.length() + " chars");
        handler.onResult(new TdApi.FormattedText(answer.trim(), new TdApi.TextEntity[0]));
      } catch (Throwable t) {
        diag("failed — " + safeMessage(t));
        handler.onResult(new TdApi.Error(400, safeMessage(t)));
      }
    });
  }

  /** «Проверить»: a tiny request; null — it works, else what went wrong */
  public static void test (me.vkryl.core.lambda.RunnableData<String> after) {
    worker.execute(() -> {
      String result;
      try {
        complete("Reply with the single word OK.", "ping");
        result = null;
        diag("test ok, provider " + providerId());
      } catch (Throwable t) {
        result = safeMessage(t);
        diag("test failed — " + result);
      }
      final String r = result;
      org.thunderdog.challegram.tool.UI.post(() -> after.runWithData(r));
    });
  }

  // The service's own list of models (user 2026-11-13 «забить доступными моделями… по удержанию описание преимуществ и
  // минусов»): asked with the person's key, the same way the answers are — nothing else is sent

  public static final class ModelInfo {
    public final String id, name;
    public final @Nullable String about; // the service's own words, when it has them
    public final long contextTokens; // 0 — unknown
    public final @Nullable String priceIn, priceOut; // dollars per million tokens, when the service tells

    ModelInfo (String id, @Nullable String name, @Nullable String about, long contextTokens, @Nullable String priceIn, @Nullable String priceOut) {
      this.id = id;
      this.name = TextUtils.isEmpty(name) ? id : name;
      this.about = TextUtils.isEmpty(about) ? null : about.trim();
      this.contextTokens = contextTokens;
      this.priceIn = priceIn;
      this.priceOut = priceOut;
    }
  }

  /** the list, or a String saying what went wrong */
  public static void listModels (me.vkryl.core.lambda.RunnableData<Object> after) {
    worker.execute(() -> {
      Object result;
      try {
        java.util.List<ModelInfo> list = fetchModels();
        diag("models: " + list.size() + " from provider " + providerId());
        result = list;
      } catch (Throwable t) {
        result = safeMessage(t);
        diag("models failed — " + result);
      }
      final Object r = result;
      org.thunderdog.challegram.tool.UI.post(() -> after.runWithData(r));
    });
  }

  private static java.util.List<ModelInfo> fetchModels () throws Exception {
    int id = providerId();
    Provider p = provider(id);
    if (p == null) throw new IllegalStateException("no service chosen");
    String key = key(id);
    String base = id == PROVIDER_CUSTOM ? customUrl() : p.baseUrl;
    if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    if (TextUtils.isEmpty(base)) throw new IllegalStateException("no address");
    if (!base.startsWith("https://") && !(id == PROVIDER_CUSTOM && (base.startsWith("http://localhost") || base.startsWith("http://127.0.0.1")))) {
      throw new IllegalStateException(org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.Tgx101OwnAiHttpsOnly));
    }
    java.util.List<ModelInfo> list = new java.util.ArrayList<>();
    if (p.kind == KIND_ANTHROPIC) {
      JSONObject reply = get(base + "/models?limit=1000", new String[] {"x-api-key", key, "anthropic-version", "2023-06-01"});
      JSONArray data = reply.optJSONArray("data");
      if (data != null) for (int i = 0; i < data.length(); i++) {
        JSONObject m = data.optJSONObject(i);
        if (m != null && !TextUtils.isEmpty(m.optString("id"))) list.add(new ModelInfo(m.optString("id"), m.optString("display_name", null), null, 0, null, null));
      }
      return list; // newest first, as the service gives them
    }
    if (id == PROVIDER_GEMINI) {
      // Google's own list says what each model can do and how much it holds (its OpenAI-style list says neither)
      JSONObject reply = get("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000", new String[] {"x-goog-api-key", key});
      JSONArray models = reply.optJSONArray("models");
      if (models != null) for (int i = 0; i < models.length(); i++) {
        JSONObject m = models.optJSONObject(i);
        if (m == null) continue;
        JSONArray methods = m.optJSONArray("supportedGenerationMethods");
        if (methods == null || !methods.toString().contains("\"generateContent\"")) continue; // embeddings, image makers
        String name = m.optString("name", "");
        if (name.startsWith("models/")) name = name.substring("models/".length());
        if (name.isEmpty()) continue;
        list.add(new ModelInfo(name, m.optString("displayName", null), m.optString("description", null), m.optLong("inputTokenLimit", 0), null, null));
      }
      return list;
    }
    JSONObject reply = get(base + "/models", TextUtils.isEmpty(key) ? new String[0] : new String[] {"Authorization", "Bearer " + key});
    JSONArray data = reply.optJSONArray("data");
    if (data == null) data = reply.optJSONArray("models"); // some local servers
    if (data != null) for (int i = 0; i < data.length(); i++) {
      JSONObject m = data.optJSONObject(i);
      if (m == null) continue;
      String mid = m.optString("id", m.optString("name", ""));
      if (mid.isEmpty() || !looksLikeChatModel(mid)) continue;
      String in = null, out = null;
      JSONObject pricing = m.optJSONObject("pricing"); // OpenRouter: dollars per token
      if (pricing != null) {
        in = perMillion(pricing.optString("prompt", null));
        out = perMillion(pricing.optString("completion", null));
      }
      list.add(new ModelInfo(mid, id == PROVIDER_OPENROUTER ? m.optString("name", null) : null, m.optString("description", null), m.optLong("context_length", 0), in, out));
    }
    if (id != PROVIDER_OPENROUTER) {
      java.util.Collections.sort(list, (a, b) -> a.id.compareToIgnoreCase(b.id));
    }
    return list;
  }

  /** OpenAI's list has voices, pictures, embeddings — none of them answer a text */
  private static boolean looksLikeChatModel (String id) {
    String s = id.toLowerCase(java.util.Locale.ROOT);
    for (String no : new String[] {"embed", "tts", "whisper", "dall-e", "moderation", "image", "audio", "realtime", "transcribe", "davinci", "babbage", "sora", "search", "rerank"}) {
      if (s.contains(no)) return false;
    }
    return true;
  }

  private static @Nullable String perMillion (@Nullable String perToken) {
    if (TextUtils.isEmpty(perToken)) return null;
    try {
      double v = Double.parseDouble(perToken) * 1_000_000d;
      if (v < 0) return null;
      String text = v >= 10 ? String.format(java.util.Locale.US, "%.0f", v) : String.format(java.util.Locale.US, "%.2f", v);
      if (text.contains(".")) text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
      return text;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static final int MODEL_FAST = 1, MODEL_SMART = 2, MODEL_THINKING = 4;

  /** what kind of model the name says it is — the services' own naming, there's no field for it */
  public static int modelTraits (String id) {
    String s = id.toLowerCase(java.util.Locale.ROOT);
    int t = 0;
    if (s.contains("reason") || s.contains("thinking") || s.contains("-r1") || s.matches("^(openai/)?o\\d.*")) t |= MODEL_THINKING;
    for (String w : new String[] {"mini", "nano", "flash", "lite", "haiku", "small", "fast", "instant", "turbo", "-8b", "-7b", "-3b", "-1b"}) {
      if (s.contains(w)) { t |= MODEL_FAST; break; }
    }
    if ((t & MODEL_FAST) == 0) {
      for (String w : new String[] {"opus", "-pro", "ultra", "large", "-max", "405b"}) {
        if (s.contains(w)) { t |= MODEL_SMART; break; }
      }
    }
    return t;
  }

  private static JSONObject get (String url, String[] headers) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    try {
      c.setRequestMethod("GET");
      c.setConnectTimeout(15000);
      c.setReadTimeout(30000);
      c.setRequestProperty("Accept", "application/json");
      for (int i = 0; i + 1 < headers.length; i += 2) c.setRequestProperty(headers[i], headers[i + 1]);
      int code = c.getResponseCode();
      InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
      String text = in != null ? read(in) : "";
      if (code < 200 || code >= 300) {
        String reason = text;
        try {
          JSONObject e = new JSONObject(text);
          Object err = e.opt("error");
          if (err instanceof JSONObject) reason = ((JSONObject) err).optString("message", text);
          else if (err instanceof String) reason = (String) err;
        } catch (Throwable ignored) { }
        throw new IllegalStateException("HTTP " + code + (TextUtils.isEmpty(reason) ? "" : ": " + reason));
      }
      return new JSONObject(text);
    } finally {
      c.disconnect();
    }
  }

  private static String safeMessage (Throwable t) {
    String m = t.getMessage();
    if (TextUtils.isEmpty(m)) m = t.getClass().getSimpleName();
    String k = key(providerId());
    if (!TextUtils.isEmpty(k) && m.contains(k)) m = m.replace(k, "••••"); // a service echoing the key back
    return m.length() > 300 ? m.substring(0, 300) + "…" : m;
  }

  private static String complete (String system, String user) throws Exception {
    int id = providerId();
    Provider p = provider(id);
    if (p == null) throw new IllegalStateException("no service chosen");
    String key = key(id);
    String model = model(id);
    String base = id == PROVIDER_CUSTOM ? customUrl() : p.baseUrl;
    if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    if (TextUtils.isEmpty(base) || TextUtils.isEmpty(model)) throw new IllegalStateException("no address or model");
    // plain http only to this very phone (an AI running on it); anything else goes encrypted
    if (!base.startsWith("https://") && !(id == PROVIDER_CUSTOM && (base.startsWith("http://localhost") || base.startsWith("http://127.0.0.1")))) {
      throw new IllegalStateException(org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.Tgx101OwnAiHttpsOnly));
    }

    if (p.kind == KIND_ANTHROPIC) {
      // Claude Messages API (raw HTTP: the SDK needs Java 8 / OkHttp 4, past what this app's Android 4 builds carry)
      JSONObject body = new JSONObject();
      body.put("model", model);
      body.put("max_tokens", 16000);
      body.put("system", system);
      JSONArray messages = new JSONArray();
      messages.put(new JSONObject().put("role", "user").put("content", user));
      body.put("messages", messages);
      JSONObject reply = post(base + "/messages", body, new String[] {"x-api-key", key, "anthropic-version", "2023-06-01"});
      if ("refusal".equals(reply.optString("stop_reason"))) throw new IllegalStateException("the model declined this request");
      JSONArray content = reply.optJSONArray("content");
      StringBuilder out = new StringBuilder();
      if (content != null) for (int i = 0; i < content.length(); i++) {
        JSONObject block = content.optJSONObject(i);
        if (block != null && "text".equals(block.optString("type"))) out.append(block.optString("text"));
      }
      if (out.length() == 0) throw new IllegalStateException("empty answer");
      return out.toString();
    }

    JSONObject body = new JSONObject();
    body.put("model", model);
    JSONArray messages = new JSONArray();
    messages.put(new JSONObject().put("role", "system").put("content", system));
    messages.put(new JSONObject().put("role", "user").put("content", user));
    body.put("messages", messages);
    JSONObject reply = post(base + "/chat/completions", body, TextUtils.isEmpty(key) ? new String[0] : new String[] {"Authorization", "Bearer " + key});
    JSONArray choices = reply.optJSONArray("choices");
    JSONObject first = choices != null ? choices.optJSONObject(0) : null;
    JSONObject message = first != null ? first.optJSONObject("message") : null;
    String text = message != null ? message.optString("content", "") : "";
    if (TextUtils.isEmpty(text)) throw new IllegalStateException("empty answer");
    return text;
  }

  private static JSONObject post (String url, JSONObject body, String[] headers) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    try {
      c.setRequestMethod("POST");
      c.setConnectTimeout(15000);
      c.setReadTimeout(120000);
      c.setDoOutput(true);
      c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      c.setRequestProperty("Accept", "application/json");
      for (int i = 0; i + 1 < headers.length; i += 2) c.setRequestProperty(headers[i], headers[i + 1]);
      byte[] out = body.toString().getBytes(UTF8);
      try (OutputStream os = c.getOutputStream()) {
        os.write(out);
      }
      int code = c.getResponseCode();
      InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
      String text = in != null ? read(in) : "";
      if (code < 200 || code >= 300) {
        String reason = text;
        try {
          JSONObject e = new JSONObject(text);
          Object err = e.opt("error");
          if (err instanceof JSONObject) reason = ((JSONObject) err).optString("message", text);
          else if (err instanceof String) reason = (String) err;
        } catch (Throwable ignored) { }
        throw new IllegalStateException("HTTP " + code + (TextUtils.isEmpty(reason) ? "" : ": " + reason));
      }
      return new JSONObject(text);
    } finally {
      c.disconnect();
    }
  }

  private static String read (InputStream in) throws Exception {
    try (InputStream s = in) {
      ByteArrayOutputStream b = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int n;
      while ((n = s.read(buf)) > 0) {
        b.write(buf, 0, n);
        if (b.size() > 4 * 1024 * 1024) throw new IllegalStateException("answer too long");
      }
      return b.toString("UTF-8");
    }
  }
}
