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

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.Tgx101OwnAi;
import org.thunderdog.challegram.data.Tgx101RichMessage;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.vkryl.core.lambda.RunnableData;

/**
 * (user 2026-11-14, the mockup «ИИ: Хранилище и музыка», «всё по порядку») The AI for the music: the mood and the style of a track,
 * a choice by a request in words, the taste in one phrase. Only «artist — title» goes to it — no files, no chats; what it said is kept
 * on the phone and never asked again. The AI brings no music from outside: it only picks among the tracks of the account's chats.
 * Off (or when it fails) everything goes on as before, by the scores of the artists.
 */
public final class Tgx101MusicAi {
  private Tgx101MusicAi () { }

  /** What is sent to the AI and shown, in this order; the screens show them by {@link #MOOD_LABELS} */
  public static final String[] MOODS = {"спокойное", "бодрое", "грустное", "для работы", "без слов"};
  public static final int[] MOOD_LABELS = {
    org.thunderdog.challegram.R.string.Tgx101MoodCalm, org.thunderdog.challegram.R.string.Tgx101MoodUpbeat,
    org.thunderdog.challegram.R.string.Tgx101MoodSad, org.thunderdog.challegram.R.string.Tgx101MoodWork,
    org.thunderdog.challegram.R.string.Tgx101MoodInstrumental
  };

  private static final int MAX_CACHE = 800, BATCH = 40, PICK_MAX = 80, LINE_MAX = 90;
  private static final String TASTE_KEY = "_taste";

  public static boolean isOn () {
    Settings s = Settings.instance();
    return s.tgx101Ai() && s.tgx101MusicAi();
  }

  public static boolean isMood (@Nullable String value) {
    if (value == null) return false;
    for (String mood : MOODS) if (mood.equals(value)) return true;
    return false;
  }

  public static String moodLabel (String mood) {
    for (int i = 0; i < MOODS.length; i++) {
      if (MOODS[i].equals(mood)) return org.thunderdog.challegram.core.Lang.getString(MOOD_LABELS[i]);
    }
    return mood;
  }

  // What is known: the track → [mood, style]

  private static final Map<Integer, LinkedHashMap<String, String[]>> CACHES = new HashMap<>();

  private static synchronized LinkedHashMap<String, String[]> cache (Tdlib tdlib) {
    LinkedHashMap<String, String[]> cache = CACHES.get(tdlib.id());
    if (cache == null) {
      cache = new LinkedHashMap<>();
      String encoded = Settings.instance().tgx101MusicAiCache(tdlib.id());
      if (encoded != null) {
        try {
          JSONObject o = new JSONObject(encoded);
          for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String key = it.next();
            JSONArray a = o.getJSONArray(key);
            cache.put(key, new String[] {a.optString(0, ""), a.optString(1, "")});
          }
        } catch (Throwable ignored) { }
      }
      CACHES.put(tdlib.id(), cache);
    }
    return cache;
  }

  private static synchronized void saveCache (Tdlib tdlib) {
    LinkedHashMap<String, String[]> cache = cache(tdlib);
    Iterator<String> it = cache.keySet().iterator();
    while (cache.size() > MAX_CACHE && it.hasNext()) {
      it.next();
      it.remove();
    }
    try {
      JSONObject o = new JSONObject();
      for (Map.Entry<String, String[]> entry : cache.entrySet()) {
        o.put(entry.getKey(), new JSONArray().put(entry.getValue()[0]).put(entry.getValue()[1]));
      }
      Settings.instance().setTgx101MusicAiCache(tdlib.id(), o.toString());
    } catch (Throwable ignored) { }
  }

  public static synchronized @Nullable String moodOf (Tdlib tdlib, @Nullable TdApi.Message track) {
    String key = Tgx101MusicTaste.keyOf(track);
    String[] note = key != null ? cache(tdlib).get(key) : null;
    return note != null && !note[0].isEmpty() ? note[0] : null;
  }

  public static synchronized @Nullable String styleOf (Tdlib tdlib, @Nullable TdApi.Message track) {
    String key = Tgx101MusicTaste.keyOf(track);
    String[] note = key != null ? cache(tdlib).get(key) : null;
    return note != null && !note[1].isEmpty() ? note[1] : null;
  }

  public static synchronized @Nullable String taste (Tdlib tdlib) {
    String[] note = cache(tdlib).get(TASTE_KEY);
    return note != null && !note[0].isEmpty() ? note[0] : null;
  }

  public static synchronized void forget (Tdlib tdlib) {
    cache(tdlib).clear();
    saveCache(tdlib);
  }

  // Asking

  static String nameOf (TdApi.Message track) {
    TdApi.Audio audio = ((TdApi.MessageAudio) track.content).audio;
    String title = TD.getTitle(audio), artist = TD.getSubtitle(audio);
    String name = artist == null || artist.isEmpty() ? String.valueOf(title) : artist + " — " + title;
    name = name.replace('\n', ' ').replace('[', '(').replace(']', ')').replace('*', ' ').replace('_', ' ').replace('`', '\'').replaceAll("\\s+", " ").trim();
    return name.length() > LINE_MAX ? name.substring(0, LINE_MAX) + "…" : name;
  }

  private static final Pattern NUMBER = Pattern.compile("(?<![\\d\\p{L}])(\\d{1,3})\\s*[:.)]\\s*");

  /** «number: rest» lines of an answer, the number from 0 */
  private static Map<Integer, String> numbered (String answer) {
    Map<Integer, String> lines = new LinkedHashMap<>();
    Matcher matcher = NUMBER.matcher(answer);
    List<int[]> starts = new ArrayList<>();
    while (matcher.find()) starts.add(new int[] {matcher.start(), matcher.end(), Integer.parseInt(matcher.group(1))});
    for (int i = 0; i < starts.size(); i++) {
      int end = i + 1 < starts.size() ? starts.get(i + 1)[0] : answer.length();
      String rest = answer.substring(starts.get(i)[1], end).trim();
      int newline = rest.indexOf('\n');
      if (newline >= 0) rest = rest.substring(0, newline).trim();
      if (!lines.containsKey(starts.get(i)[2] - 1)) lines.put(starts.get(i)[2] - 1, rest);
    }
    return lines;
  }

  private static @Nullable String textOf (TdApi.Object result) {
    if (result.getConstructor() == TdApi.RichMessage.CONSTRUCTOR) {
      TdApi.FormattedText f = Tgx101RichMessage.toText((TdApi.RichMessage) result);
      return f != null ? f.text : null;
    }
    if (result.getConstructor() == TdApi.FormattedText.CONSTRUCTOR) return ((TdApi.FormattedText) result).text;
    return null;
  }

  private static void ask (Tdlib tdlib, String what, String text, String prompt, RunnableData<String> after) {
    org.thunderdog.challegram.Tgx101Diag.mark("music ai: " + what + ", " + text.length() + " chars");
    TdApi.InputRichMessage message = new TdApi.InputRichMessage(new TdApi.RichMessageSourceMarkdown(text, new TdApi.InputRichMessageMedia[0]), false, false);
    Tgx101OwnAi.send(tdlib, new TdApi.ComposeRichMessageWithAi(message, "", "", prompt, false), result -> UI.post(() -> {
      String answer = textOf(result);
      if (answer == null) {
        org.thunderdog.challegram.Tgx101Diag.mark("music ai: " + what + " failed — " + (result instanceof TdApi.Error ? ((TdApi.Error) result).message : result.getClass().getSimpleName()));
      }
      after.runWithData(answer);
    }));
  }

  private static String clean (String s) {
    return s.replaceAll("[«»\"*_`#]", "").trim();
  }

  /** The mood and the style of the tracks it doesn't know yet, {@link #BATCH} at a time; {@code after} on the UI thread, whatever happens */
  public static void note (Tdlib tdlib, List<TdApi.Message> tracks, Runnable after) {
    final List<TdApi.Message> unknown = new ArrayList<>();
    synchronized (Tgx101MusicAi.class) {
      LinkedHashMap<String, String[]> cache = cache(tdlib);
      java.util.HashSet<String> seen = new java.util.HashSet<>();
      for (TdApi.Message track : tracks) {
        String key = Tgx101MusicTaste.keyOf(track);
        if (key != null && !cache.containsKey(key) && seen.add(key) && unknown.size() < BATCH) unknown.add(track);
      }
    }
    if (unknown.isEmpty() || !isOn()) {
      UI.post(after);
      return;
    }
    StringBuilder text = new StringBuilder("Треки:\n");
    for (int i = 0; i < unknown.size(); i++) text.append(i + 1).append(") ").append(nameOf(unknown.get(i))).append('\n');
    StringBuilder moods = new StringBuilder();
    for (String mood : MOODS) moods.append(moods.length() > 0 ? ", " : "").append(mood);
    String prompt = "Для каждого трека назови настроение — ровно одно из: " + moods + " — и стиль в 2–4 словах (жанр, звучание). " +
      "Трек не знаешь — суди по исполнителю; совсем не понятно — «номер: -». Ответь только строками «номер: настроение; стиль».";
    ask(tdlib, "moods of " + unknown.size(), text.toString(), prompt, answer -> {
      if (answer != null) {
        int known = 0;
        synchronized (Tgx101MusicAi.class) {
          LinkedHashMap<String, String[]> cache = cache(tdlib);
          for (Map.Entry<Integer, String> line : numbered(answer).entrySet()) {
            int n = line.getKey();
            if (n < 0 || n >= unknown.size()) continue;
            String rest = line.getValue();
            String mood = "", style = "";
            if (!rest.startsWith("-") && !rest.startsWith("—")) {
              int semicolon = rest.indexOf(';');
              String first = clean(semicolon >= 0 ? rest.substring(0, semicolon) : rest).toLowerCase(java.util.Locale.ROOT);
              for (String m : MOODS) if (first.startsWith(m)) mood = m;
              if (semicolon >= 0) style = clean(rest.substring(semicolon + 1));
              if (style.length() > 40) style = style.substring(0, 40).trim() + "…";
            }
            cache.put(Tgx101MusicTaste.keyOf(unknown.get(n)), new String[] {mood, style}); // «-» too: not asked again
            known++;
          }
          saveCache(tdlib);
        }
        org.thunderdog.challegram.Tgx101Diag.mark("music ai: " + known + " of " + unknown.size() + " noted");
      }
      after.run();
    });
  }

  private static final int MOOD_ROUNDS = 3;

  /** {@link #note} again while something is left unknown, up to this many requests */
  private static void noteRounds (Tdlib tdlib, List<TdApi.Message> tracks, int rounds, Runnable after) {
    final int before = cache(tdlib).size();
    note(tdlib, tracks, () -> {
      boolean grew = cache(tdlib).size() > before;
      if (rounds > 1 && grew && hasUnknown(tdlib, tracks)) {
        noteRounds(tdlib, tracks, rounds - 1, after);
      } else {
        after.run();
      }
    });
  }

  private static synchronized boolean hasUnknown (Tdlib tdlib, List<TdApi.Message> tracks) {
    LinkedHashMap<String, String[]> cache = cache(tdlib);
    for (TdApi.Message track : tracks) {
      String key = Tgx101MusicTaste.keyOf(track);
      if (key != null && !cache.containsKey(key)) return true;
    }
    return false;
  }

  /** Of these, the ones that fit the mood: by what is noted, asking what isn't; null — the AI is off or failed */
  public static void withMood (Tdlib tdlib, List<TdApi.Message> tracks, String mood, RunnableData<List<TdApi.Message>> after) {
    if (!isOn()) {
      UI.post(() -> after.runWithData(null));
      return;
    }
    if (!isMood(mood)) {
      pick(tdlib, tracks, mood, PICK_MAX, after); // written by the person: chosen by the AI each time
      return;
    }
    noteRounds(tdlib, tracks, MOOD_ROUNDS, () -> {
      List<TdApi.Message> fit = new ArrayList<>();
      for (TdApi.Message track : tracks) {
        if (mood.equals(moodOf(tdlib, track))) fit.add(track);
      }
      after.runWithData(fit);
    });
  }

  /** By a request in words («спокойное в дорогу, минут на 40»): the tracks that fit, in the order to play; null — failed */
  public static void pick (Tdlib tdlib, List<TdApi.Message> tracks, String request, int limit, RunnableData<List<TdApi.Message>> after) {
    if (!isOn() || tracks.isEmpty()) {
      UI.post(() -> after.runWithData(null));
      return;
    }
    final List<TdApi.Message> offered = new ArrayList<>(tracks.subList(0, Math.min(PICK_MAX, tracks.size())));
    StringBuilder text = new StringBuilder("Запрос: ").append(clean(request.replace('\n', ' '))).append("\n\nТреки:\n");
    for (int i = 0; i < offered.size(); i++) {
      TdApi.Message track = offered.get(i);
      text.append(i + 1).append(") ").append(nameOf(track));
      int seconds = ((TdApi.MessageAudio) track.content).audio.duration;
      if (seconds > 0) text.append(" (").append(seconds / 60).append(':').append(String.format(java.util.Locale.ROOT, "%02d", seconds % 60)).append(')');
      text.append('\n');
    }
    String prompt = "Выбери из списка треки, которые подходят под запрос, и расставь их в порядке прослушивания (не больше " + limit + "). " +
      "Если в запросе есть длительность — уложись в неё. Других треков не придумывай. Ответь только номерами через запятую.";
    ask(tdlib, "pick for a request, " + offered.size() + " tracks", text.toString(), prompt, answer -> {
      if (answer == null) {
        after.runWithData(null);
        return;
      }
      List<TdApi.Message> chosen = new ArrayList<>();
      java.util.HashSet<Integer> seen = new java.util.HashSet<>();
      Matcher m = Pattern.compile("\\d{1,3}").matcher(answer);
      while (m.find() && chosen.size() < limit) {
        int n = Integer.parseInt(m.group()) - 1;
        if (n >= 0 && n < offered.size() && seen.add(n)) chosen.add(offered.get(n));
      }
      org.thunderdog.challegram.Tgx101Diag.mark("music ai: picked " + chosen.size() + " of " + offered.size());
      after.runWithData(chosen);
    });
  }

  /** The taste in one phrase, from what the account likes and skips; kept until asked again */
  public static void describe (Tdlib tdlib, List<String> liked, List<String> skipped, List<String> likedTracks, RunnableData<String> after) {
    if (!isOn() || (liked.isEmpty() && likedTracks.isEmpty())) {
      UI.post(() -> after.runWithData(null));
      return;
    }
    StringBuilder text = new StringBuilder();
    if (!liked.isEmpty()) text.append("Дослушиваю: ").append(android.text.TextUtils.join(", ", liked)).append('\n');
    if (!skipped.isEmpty()) text.append("Обычно пропускаю: ").append(android.text.TextUtils.join(", ", skipped)).append('\n');
    if (!likedTracks.isEmpty()) text.append("Нравятся треки:\n").append(android.text.TextUtils.join("\n", likedTracks)).append('\n');
    String prompt = "Опиши мой музыкальный вкус одной фразой до 20 слов, обращаясь ко мне на «вы»: что я слушаю и что обычно пропускаю. Без вступления и кавычек.";
    ask(tdlib, "taste", text.toString(), prompt, answer -> {
      String phrase = answer != null ? clean(answer.replace('\n', ' ')) : null;
      if (phrase != null && !phrase.isEmpty()) {
        if (phrase.length() > 200) phrase = phrase.substring(0, 200).trim() + "…";
        synchronized (Tgx101MusicAi.class) {
          cache(tdlib).put(TASTE_KEY, new String[] {phrase, String.valueOf(System.currentTimeMillis())});
          saveCache(tdlib);
        }
      }
      after.runWithData(phrase != null && !phrase.isEmpty() ? phrase : null);
    });
  }
}
