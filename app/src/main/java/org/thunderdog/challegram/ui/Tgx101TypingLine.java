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
package org.thunderdog.challegram.ui;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.Layout;
import android.view.View;
import android.widget.TextView;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 (user 2026-11-13, variant Б made interactive, mockup «Живая линия поля», «длина с пульсом»): the classic
 * field's bottom edge is a hairline between the field and the room under it (or the screen's bottom with no gap). The
 * At the cursor a pulse answers the typing: a slow hand gives a short calm blue glow, a fast one spreads it wider,
 * brighter and towards violet, a deleted letter warms it for a moment; the pulse fades a second after the last key.
 * (The fill that showed the message's length was taken out on 2026-11-15.)
 */
public final class Tgx101TypingLine {
  private final View host;   // the field's wrap: it draws the line
  private final TextView field;


  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Runnable wake = this::invalidateHost;

  private void invalidateHost () {
    host.invalidate();
  }

  private final org.thunderdog.challegram.telegram.Tdlib tdlib;

  Tgx101TypingLine (View host, TextView field, org.thunderdog.challegram.telegram.Tdlib tdlib) {
    this.host = host;
    this.field = field;
    this.tdlib = tdlib;
    // the field taking or losing the focus changes what the line shows
    // only while the host is in a window: the observer belongs to the whole window, and a listener left on it kept
    // every chat screen ever opened (previews too) in memory until the app was closed
    final android.view.ViewTreeObserver.OnGlobalFocusChangeListener focus = (oldFocus, newFocus) -> {
      if (oldFocus == field || newFocus == field) host.invalidate();
    };
    host.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow (View v) {
        v.getViewTreeObserver().addOnGlobalFocusChangeListener(focus);
      }

      @Override
      public void onViewDetachedFromWindow (View v) {
        v.getViewTreeObserver().removeOnGlobalFocusChangeListener(focus);
      }
    });
    if (host.getWindowToken() != null) host.getViewTreeObserver().addOnGlobalFocusChangeListener(focus);
  }

  // (user 2026-11-15 23:1x «синюю полосу вообще убери из кода, подсветка при наборе остаётся»): the fill that showed the
  // text's length is gone; the pulse answering the typing stays, at the cursor

  // (user 2026-11-18 «убери вообще эту линию скорости печати в поле ввода, функция не нужна»): the pulse that ran after
  // the cursor and took colour from the typing speed is gone, with its switch and the day's pace kept for it

  private boolean keyboardUp () {
    try {
      return org.thunderdog.challegram.tool.UI.getContext(host.getContext()).isKeyboardVisible();
    } catch (Throwable t) {
      return field.isFocused();
    }
  }

  /** the line along y (the field's bottom), width w */
  void draw (Canvas c, float y, int w) {
    float thin = Math.max(1, Screen.dp(.5f));
    paint.setShader(null);
    // (user 2026-11-15): with a voice being recorded or sound playing, a light wave runs along the line with the
    // sound; the thin line gives way to it and comes back when it is quiet
    // (user 2026-11-15 17:30 «полоса такая же серая как и сейчас, толще не становится»): the wave is the line itself,
    // bent — the same grey, the same thickness
    // with the keyboard up the wave is not shown and is not asked for either — it would redraw the field for nothing
    boolean sound = !keyboardUp() && Sound.live(host, false);
    float room = host.getHeight() - y;
    // recording with no room under the line: the recording panel draws the wave over its own bottom edge
    // (user 2026-11-15 «когда пишу голосовое с открытой клавиатурой, линии звука не должны пробиваться снизу»): with
    // the keyboard up the wave swinging down came out from under the panel, over the keyboard's edge — there too the
    // panel draws it, upwards
    if (sound && Sound.recording() && room < Screen.dp(10f)) sound = false;
    // (user 2026-11-15 23:0x «полоса до сих пор активна, когда клавиатура открыта… её не должно быть видно»): with the
    // keyboard up there is no sound wave at all — not while recording, not while something plays (see above)
    paint.setColor(Theme.separatorColor());
    // (user 2026-11-14 23:46 «при открытой клавиатуре внизу поля ввода не должна быть видна полоса»): while typing — the
    // plain line, the word only at rest. By the keyboard, not the field's focus: a chat keeps the cursor in the field
    // with the keyboard down, and the word never showed (user 2026-11-15 «морзе не работает?»)
    // (user 2026-11-16 22:16 «эта полоса работает только на аудиозапись и проигрывание; если включена — в покое просто
    // ровная полоса; если выключена — полосы вообще быть не должно»): «Интерактивная полоса ввода» on — the wave with
    // the sound, a plain whole line at rest (no Morse word, no gaps for the time in the app); off — no line at all
    boolean lineOn = org.thunderdog.challegram.unsorted.Settings.instance().tgx101MorseLine();
    if (sound) {
      // (phone check 2026-11-15 17:34): the recording panel lies over the field down to the line and hid the wave's upper
      // half — while recording the wave swings down from the line only
      Sound.draw(c, y, w, thin, Sound.recording() ? 1 : 0);
    } else if (lineOn) {
      c.drawRect(0, y, w, y + thin, paint);
    }
    // (log 2026-11-14 19:44: an idle chat drew 1828 frames in 15 s, 70 % of a core) every drawing posted one more delayed
    // invalidate, each of them posted another when it fired — the chains piled up into a redraw without end. One pending
    // wake-up only, replaced, never added to
    long next = 0; // the line's gaps by the time in the app are gone (2026-11-16): nothing changes on its own any more
    host.removeCallbacks(wake);
    if (next > 0) host.postDelayed(wake, next);

  }

  /**
   * MagiX's live example (user 2026-11-13 «полоску и пульс нужно показать наглядно людям»): the same line for a field
   * running from left to right at y
   */
  static void drawDemo (Canvas c, Paint paint, float left, float right, float y) {
    paint.setShader(null);
    paint.setColor(Theme.separatorColor());
    c.drawRect(left, y, right, y + Math.max(1, Screen.dp(.5f)), paint); // whole, as the field's own at rest (user 2026-11-16)
  }

  /**
   * (user 2026-11-13 «используем тонкую линию: чем меньше сижу — тем ровнее, чем дольше за сеанс — тем больше обрывов,
   * без фанатизма, красиво»): the line at rest is whole for the first 3 minutes of a sitting; then at 5, 7, 10, 12, 14,
   * 18, 21, 25, 30, 45 and 60 minutes one more small gap opens — softly, over half a minute — at its own place along the line, up to 12 after an hour. Leaving the
   * app for more than 5 minutes ends the sitting: the next visit starts whole again. Only the time on the phone, kept in
   * memory, nothing stored.
   */
  /**
   * TGx101 (user 2026-11-14, mockup «Морзе PLUMAGRAM в цветах собеседников»; a separate switch, off by default): the
   * line's middle spells a word in Morse, letter by letter as the day goes — time in the app and the words of one's own
   * sent messages add up. (user 2026-11-15: the word is PLUMA FREEDOM, 12 letters; 30 minutes a letter, 45 written words
   * a letter — it was PLUMAGRAM, 10 minutes and about 4.5 messages of any length.) A letter brought by words takes the
   * name colour of the chat written to most while it came; a letter brought by time stays grey. The word whole: a light runs along the line once
   * and it stays the colour of the day's most written-to chat till midnight. The sides are plain grey. Holding the line
   * taps the word out by vibration only. Everything is counted on the phone.
   */
  public static final class Morse {
    /** (user 2026-11-15 17:30 «Морзе быть не должно, просто полоса»): the word is off for everyone; its code is kept */
    public static final boolean ENABLED = false;
    // PLUMA FREEDOM
    private static final String[] CODE = {".--.", ".-..", "..-", "--", ".-", "..-.", ".-.", ".", ".", "-..", "---", "--"};
    private static final int SECOND_WORD = 5; // the letter the second word starts with: 7 units before it, not 3
    private static final float LETTER_MS = 30 * 60_000f, LETTER_WORDS = 45f;
    /** units of silence after letter i (none after the last) */
    private static float gapAfter (int i) {
      return i >= CODE.length - 1 ? 0f : i + 1 == SECOND_WORD ? 7f : 3f;
    }
    private static final String KEY = "tgx101_morse_day";
    private static int day = -1, words, letters;
    private static final long[] letterChat = new long[CODE.length]; // 0 — brought by time
    private static final java.util.HashMap<Long, Integer> sinceLetter = new java.util.HashMap<>(), today = new java.util.HashMap<>();
    private static boolean swept;
    private static long sweepStart;

    private static void load () {
      int t = Session.todayKey();
      if (day == t) return;
      day = t;
      words = 0;
      letters = 0;
      swept = false;
      java.util.Arrays.fill(letterChat, 0);
      sinceLetter.clear();
      today.clear();
      String saved = org.thunderdog.challegram.unsorted.Settings.instance().pmc().getString(KEY, null);
      if (saved == null) return;
      try {
        String[] parts = saved.split("\\|", -1);
        if (Integer.parseInt(parts[0]) != t) return;
        words = Integer.parseInt(parts[1]);
        swept = "1".equals(parts[2]);
        String[] ls = parts[3].isEmpty() ? new String[0] : parts[3].split(",");
        letters = Math.min(CODE.length, ls.length);
        for (int i = 0; i < letters; i++) letterChat[i] = Long.parseLong(ls[i]);
        if (parts.length > 4 && !parts[4].isEmpty()) {
          for (String kv : parts[4].split(",")) {
            String[] a = kv.split(":");
            today.put(Long.parseLong(a[0]), Integer.parseInt(a[1]));
          }
        }
      } catch (Throwable ignored) { }
    }

    private static void save () {
      StringBuilder b = new StringBuilder().append(day).append('|').append(words).append('|').append(swept ? 1 : 0).append('|');
      for (int i = 0; i < letters; i++) {
        if (i > 0) b.append(',');
        b.append(letterChat[i]);
      }
      b.append('|');
      boolean first = true;
      for (java.util.Map.Entry<Long, Integer> e : today.entrySet()) {
        if (!first) b.append(',');
        first = false;
        b.append(e.getKey()).append(':').append(e.getValue());
      }
      org.thunderdog.challegram.unsorted.Settings.instance().pmc().putString(KEY, b.toString());
    }

    /** words of a text: runs of letters or digits («don't» and «что-то» are one word each) */
    static int countWords (CharSequence text) {
      if (text == null) return 0;
      int count = 0;
      boolean in = false;
      for (int i = 0, n = text.length(); i < n; ) {
        int cp = Character.codePointAt(text, i);
        i += Character.charCount(cp);
        if (Character.isLetterOrDigit(cp)) {
          if (!in) count++;
          in = true;
        } else if (cp != '\'' && cp != '\u2019' && cp != '-') {
          in = false;
        }
      }
      return count;
    }

    /** one's own message has gone (from any account): its text's or caption's words count; one with no words adds nothing */
    public static synchronized void onSent (long chatId, CharSequence text) {
      if (!ENABLED || !org.thunderdog.challegram.unsorted.Settings.instance().tgx101MorseLine()) return;
      int n = countWords(text);
      if (n == 0) return;
      load();
      words += n;
      Integer since = sinceLetter.get(chatId);
      sinceLetter.put(chatId, since == null ? n : since + n);
      Integer d = today.get(chatId);
      today.put(chatId, d == null ? n : d + n);
      advance();
      save();
    }

    /** letters the day has earned so far: 30 minutes or 45 written words each */
    private static void advance () {
      float units = Session.todayMs() / LETTER_MS + words / LETTER_WORDS;
      int earned = Math.min(CODE.length, (int) units);
      boolean changed = false;
      while (letters < earned) {
        long best = 0;
        int bestN = 0;
        for (java.util.Map.Entry<Long, Integer> e : sinceLetter.entrySet()) {
          if (e.getValue() > bestN) {
            bestN = e.getValue();
            best = e.getKey();
          }
        }
        letterChat[letters++] = best;
        sinceLetter.clear();
        changed = true;
      }
      if (changed) save();
    }

    private static long topChat () {
      long best = 0;
      int bestN = 0;
      for (java.util.Map.Entry<Long, Integer> e : today.entrySet()) {
        if (e.getValue() > bestN) {
          bestN = e.getValue();
          best = e.getKey();
        }
      }
      return best;
    }

    private static int chatColor (org.thunderdog.challegram.telegram.Tdlib tdlib, long chatId) {
      if (tdlib == null || chatId == 0) return 0;
      try {
        return tdlib.chatAccentColor(chatId).getNameColor();
      } catch (Throwable t) {
        return 0;
      }
    }

    static synchronized void draw (Canvas c, Paint paint, org.thunderdog.challegram.telegram.Tdlib tdlib, View host, float left, float right, float y, float thin) {
      load();
      advance();
      float w = right - left;
      float units = 0;
      for (int i = 0; i < CODE.length; i++) {
        for (int j = 0; j < CODE[i].length(); j++) units += (CODE[i].charAt(j) == '.' ? 1f : 3f) + (j < CODE[i].length() - 1 ? 1f : 0f);
        units += gapAfter(i);
      }
      // the word in the middle 60 %, the plain grey line at the sides
      float span = w * .6f, unit = span / units, from = left + (w - span) / 2f;
      int rest = paint.getColor();
      c.drawRect(left, y, from, y + thin, paint);
      c.drawRect(from + span, y, right, y + thin, paint);
      boolean done = letters == CODE.length;
      int topColor = done ? chatColor(tdlib, topChat()) : 0;
      if (done && !swept) {
        swept = true;
        sweepStart = SystemClock.uptimeMillis();
        save();
      }
      float x = from;
      // (user 2026-11-14 23:45 «не шире серой полосы, меняется только цвет, форма плавная, а не квадратная»): a mark is the
      // grey line's own thickness, only coloured, and its colour fades in and out at the ends
      float soft = Screen.dp(3f);
      for (int i = 0; i < CODE.length; i++) {
        boolean shown = i < letters;
        int col = !shown ? rest : done && topColor != 0 ? topColor : letterChat[i] != 0 ? chatColor(tdlib, letterChat[i]) : 0;
        if (shown && col == 0) col = rest; // a letter brought by time
        float letterStart = x;
        for (int j = 0; j < CODE[i].length(); j++) {
          float len = (CODE[i].charAt(j) == '.' ? 1f : 3f) * unit;
          if (shown) {
            paint.setColor(rest);
            c.drawRect(x, y, x + len, y + thin, paint);
            if (col != rest) {
              float edge = Math.min(soft, len / 2f) / len;
              paint.setShader(new LinearGradient(x, 0, x + len, 0,
                new int[] {ColorUtils.alphaColor(0f, col), col, col, ColorUtils.alphaColor(0f, col)},
                new float[] {0f, edge, 1f - edge, 1f}, Shader.TileMode.CLAMP));
              c.drawRect(x, y, x + len, y + thin, paint);
              paint.setShader(null);
            }
          }
          x += len;
          if (j < CODE[i].length() - 1) x += unit;
        }
        if (!shown) { // not come yet: the line stays whole there
          paint.setColor(rest);
          c.drawRect(letterStart, y, x + gapAfter(i) * unit, y + thin, paint);
        }
        x += gapAfter(i) * unit;
      }
      paint.setColor(rest);
      // the word just completed: a light runs along it once
      long since = SystemClock.uptimeMillis() - sweepStart;
      if (sweepStart != 0 && since < 1400) {
        float k = since / 1400f;
        float half = Screen.dp(30f);
        float cx = from - half + (span + half * 2f) * k;
        int glow = topColor != 0 ? topColor : 0xffffffff;
        paint.setShader(new LinearGradient(cx - half, 0, cx + half, 0, new int[] {ColorUtils.alphaColor(0f, glow), ColorUtils.alphaColor(1f - k, 0xffffffff), ColorUtils.alphaColor(0f, glow)}, null, Shader.TileMode.CLAMP));
        c.drawRect(Math.max(from, cx - half), y, Math.min(from + span, cx + half), y + thin, paint);
        paint.setShader(null);
        host.postInvalidateOnAnimation();
      }
    }

    /** the words tapped out by vibration only: dot 70 ms, dash 210 ms; 70 ms between signs, 210 between letters, 490 between the words */
    static void vibrate (android.content.Context context) {
      java.util.ArrayList<Long> pattern = new java.util.ArrayList<>();
      pattern.add(0L);
      for (int i = 0; i < CODE.length; i++) {
        for (int j = 0; j < CODE[i].length(); j++) {
          pattern.add(CODE[i].charAt(j) == '.' ? 70L : 210L);
          pattern.add(j < CODE[i].length() - 1 ? 70L : (long) (gapAfter(i) * 70f));
        }
      }
      pattern.remove(pattern.size() - 1);
      long[] p = new long[pattern.size()];
      for (int i = 0; i < p.length; i++) p[i] = pattern.get(i);
      try {
        android.os.Vibrator v = (android.os.Vibrator) context.getSystemService(android.content.Context.VIBRATOR_SERVICE);
        if (v == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createWaveform(p, -1));
        else v.vibrate(p, -1);
      } catch (Throwable ignored) { }
    }
  }


  /**
   * TGx101 (user 2026-11-15 «когда записываешь голосовое идёт волна типа эквалайзера лёгкая, которая реагирует на
   * голос… или когда проигрывается музыка или голосовое; в простое тонкая линия как сейчас»): how loud it is right now,
   * 0…1 — from the microphone while recording, from the player while it plays (Tgx101SoundLevel). On with «Interactive
   * input line» (the Morse switch).
   */
  public static final class Sound {
    private static volatile float target;
    private static volatile long at;
    private static volatile boolean wanted;
    private static volatile long wantedAt;
    private static volatile long recordingAt; // the last level from the microphone
    private static volatile long tintAt;      // when this recording's colour set out from the middle, 0 — not yet
    private static float shown;
    private static long shownAt;
    private static java.lang.ref.WeakReference<View> hostRef, panelRef;
    private static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final android.graphics.Path wavePath = new android.graphics.Path();

    /** asked on the player's thread for every piece of sound: the settings are read once a second at most */
    public static boolean wanted () {
      long now = SystemClock.uptimeMillis();
      if (wantedAt == 0 || now - wantedAt > 1000) {
        wantedAt = now;
        try {
          org.thunderdog.challegram.unsorted.Settings s = org.thunderdog.challegram.unsorted.Settings.instance();
          wanted = s.tgx101MorseLine();
        } catch (Throwable t) {
          wanted = false;
        }
      }
      return wanted;
    }

    private static volatile long liveAt; // the last time a view drew the line alive: it asks for its next frame itself

    private static void wake (java.lang.ref.WeakReference<View> ref) {
      // only to start it: a line already alive keeps its own pace — every piece of sound asking for one more frame
      // (20 a second) would break the cap below
      if (SystemClock.uptimeMillis() - liveAt < 200 || calm()) return;
      View view = ref != null ? ref.get() : null;
      if (view != null) view.postInvalidateOnAnimation();
    }

    // (phone 2026-11-15 22:39: while a voice note played with the chat open the app took 40–59 % of a core — the field's
    // bottom drawn 24 times a second, each frame costing the same however small; user «да, через минуту»): the wave
    // goes with the first minute of an unbroken sound, then the line rests until the next track or voice note —
    // music playing for hours no longer redraws the chat
    private static final long CALM_AFTER_MS = 60_000L;
    private static volatile long soundSince; // when the sound playing now began, 0 — nothing yet

    /** a new track, voice note or a jump inside one: its minute starts over */
    public static void newTrack () {
      soundSince = 0;
    }

    private static boolean calm () {
      long since = soundSince;
      return since != 0 && SystemClock.uptimeMillis() - since > CALM_AFTER_MS && !recording();
    }

    /** from any thread */
    public static void push (float level) {
      if (!wanted()) return;
      long now = SystemClock.uptimeMillis();
      if (level > .02f && (soundSince == 0 || now - at > 2000)) soundSince = now; // after two seconds of silence it is a new sound
      target = Math.max(0f, Math.min(1f, level));
      at = now;
      wake(hostRef);
      wake(panelRef);
    }

    /**
     * the microphone's level while a voice note is being recorded. (user 2026-11-15 «во время записи голосового линия
     * начинала перекрашиваться в цвет темы, в цвет микрофона. Как волна начинается с середины — так и окрашивается, с
     * первых волн»): the first sound of a recording sends the colour out from the middle
     */
    public static void pushRecording (float level) {
      long now = SystemClock.uptimeMillis();
      if (now - recordingAt > 1500) tintAt = 0; // a new recording
      recordingAt = now;
      if (tintAt == 0 && level > .04f && wanted()) tintAt = now;
      push(level);
    }

    /** the level to show in {@code delay} ms */
    public static void pushAt (float level, long delay) {
      if (delay <= 0) push(level);
      else main.postDelayed(() -> push(level), delay);
    }

    static boolean recording () {
      return SystemClock.uptimeMillis() - recordingAt < 600;
    }

    /** how much of the recording's colour the line has: whole while recording, fading for a third of a second after */
    private static float tint (long now) {
      if (tintAt == 0) return 0f;
      long since = now - recordingAt;
      return since < 600 ? 1f : Math.max(0f, 1f - (since - 600) / 300f);
    }

    /**
     * true — the line is alive now (a sound, its rings still running out, or a recording's colour) and is drawn by
     * {@link #draw}; the view is asked for the next frame. The level rises fast and falls softly
     */
    static boolean live (View view, boolean panel) {
      java.lang.ref.WeakReference<View> ref = panel ? panelRef : hostRef;
      if (ref == null || ref.get() != view) {
        ref = new java.lang.ref.WeakReference<>(view);
        if (panel) panelRef = ref; else hostRef = ref;
      }
      long now = SystemClock.uptimeMillis();
      float dt = shownAt != 0 ? Math.min(64, now - shownAt) : 16;
      shownAt = now;
      boolean calm = calm();
      float to = calm || now - at > 250 ? 0f : target;
      shown += (to - shown) * Math.min(1f, dt / (to > shown ? 60f : 240f));
      if (shown < .012f && to == 0f) shown = 0f;
      // the rings already on their way run out to the ends first (about a second and a half)
      long quietSince = calm ? soundSince + CALM_AFTER_MS : at + 250;
      boolean alive = at != 0 && (shown > 0f || now - quietSince < 1800 || tint(now) > 0f);
      if (!alive) {
        if (historyAt != 0) {
          java.util.Arrays.fill(history, 0f);
          historyAt = 0;
        }
        return false;
      }
      liveAt = now;
      // (user 2026-11-15 «не залипает ли процессор с новой линией?»): music can play for hours with a chat open — the
      // line is redrawn 30 times a second then, not on every frame of a 120 Hz screen; a recording is short and
      // follows the voice at the screen's own rate
      if (recording()) view.postInvalidateOnAnimation(); else view.postInvalidateDelayed(33);
      return true;
    }

    // (user 2026-11-15, mockup «Волна под полем ввода», variant В «От середины»): each loud moment runs from the
    // middle of the line to its ends, like rings on water — the level as it was a moment ago stands further out
    private static final int HISTORY = 160;
    private static final float[] history = new float[HISTORY]; // history[i] — the level i steps of 16 ms ago
    private static long historyAt;

    private static void remember (float level) {
      long now = SystemClock.uptimeMillis();
      int steps = historyAt == 0 ? 1 : (int) Math.min(HISTORY, (now - historyAt) / 16);
      if (steps <= 0) return;
      historyAt = historyAt == 0 || steps == HISTORY ? now : historyAt + steps * 16L;
      System.arraycopy(history, 0, history, steps, HISTORY - steps);
      for (int i = 0; i < steps; i++) history[i] = level;
    }

    /** @param way 0 — the wave swings both ways from the line, 1 — down only, -1 — up only */
    static void draw (Canvas c, float y, int w, float thin, int way) {
      remember(shown);
      long now = SystemClock.uptimeMillis();
      float t = now / 1000f;
      float amp = Screen.dp(6f);
      float step = Screen.dp(3f), perStep = Screen.dp(2.6f), length = Screen.dp(30f);
      float mid = w / 2f;
      wavePath.reset();
      for (float x = 0; x <= w; x += step) {
        float d = Math.abs(x - mid);
        float past = history[Math.min(HISTORY - 1, Math.round(d / perStep))];
        float fade = 1f - d / mid * .75f; // lower towards the ends
        fade *= Math.min(1f, d / Screen.dp(14f)); // and still in the very middle: the two halves met at a sharp point
        float swing = (float) Math.sin(2 * Math.PI * d / length - t * 7.0);
        float yy = y + thin / 2f + (way == 0 ? swing : way * (swing + 1f) * .75f) * past * amp * fade;
        if (x == 0) wavePath.moveTo(x, yy); else wavePath.lineTo(x, yy);
      }
      wavePaint.setStyle(Paint.Style.STROKE);
      wavePaint.setStrokeWidth(thin);
      wavePaint.setStrokeJoin(Paint.Join.ROUND);
      int grey = Theme.separatorColor();
      wavePaint.setColor(grey);
      float tint = tint(now);
      if (tint > 0f) {
        // the colour of the round recording button, running out from the middle at the rings' pace
        int color = ColorUtils.fromToArgb(grey, Theme.getColor(ColorId.circleButtonRegular), tint);
        float front = (now - tintAt) / 16f * perStep, soft = Screen.dp(26f);
        if (front >= mid + soft) {
          wavePaint.setColor(color);
        } else {
          float a = Math.max(0f, (mid - front - soft) / w), b = Math.max(a, (mid - front) / w);
          float e = Math.min(1f, (mid + front + soft) / w), d = Math.min(e, (mid + front) / w);
          wavePaint.setShader(new LinearGradient(0, 0, w, 0, new int[] {grey, color, color, grey}, new float[] {a, b, Math.max(b, d), Math.max(b, e)}, Shader.TileMode.CLAMP));
        }
      }
      c.drawPath(wavePath, wavePaint);
      wavePaint.setShader(null);
    }

    /**
     * (user 2026-11-15, mockup «Запись голосового: кнопка и линия», «Н»): a field with no room under it (three
     * navigation buttons) — while recording the wave swings up only and is drawn by the recording panel itself, over
     * its bottom edge: the panel lies on the field down to the line
     */
    public static boolean keyboardUp (View view) {
      try {
        return org.thunderdog.challegram.tool.UI.getContext(view.getContext()).isKeyboardVisible();
      } catch (Throwable t) {
        return false;
      }
    }

    public static void drawOverPanel (Canvas c, View panel, float bottom, int w) {
      if (!recording() && tint(SystemClock.uptimeMillis()) <= 0f) return;
      if (!live(panel, true)) return;
      float thin = Math.max(1, Screen.dp(.5f));
      draw(c, bottom - thin, w, thin, -1);
    }
  }

  public static final class Session {
    private static long start, pausedAt;
    // (user 2026-11-13 «10 минут мало: первая через 3 минуты, потом 5-7-10-12-14-18-21-25-30-45-60»)
    private static final int[] AT_MINUTES = {3, 5, 7, 10, 12, 14, 18, 21, 25, 30, 45, 60};
    private static final int MAX_GAPS = AT_MINUTES.length;
    private static final long OPEN_MS = 30_000L, BREAK_MS = 5 * 60_000L;
    // the gaps' places: spread over the line but not evenly — fixed, so a gap never moves once it is there
    private static final float[] PLACES = {.47f, .18f, .74f, .31f, .88f, .09f, .6f, .39f, .81f, .24f, .67f, .54f};

    // (user 2026-11-13 «давай суточную линию»): the gaps count the whole day in the app, not one sitting — time open
    // today, kept on the phone (one number and the day), from midnight to midnight
    private static final boolean DAILY = true;
    private static final int[] DAY_MINUTES = {15, 30, 45, 60, 80, 100, 120, 150, 180, 240, 300, 360};
    private static final String KEY_DAY = "tgx101_line_day", KEY_DAY_MS = "tgx101_line_day_ms";
    private static long dayMs = -1, resumedAt;
    private static int day;

    static int todayKey () {
      return today();
    }

    private static int today () {
      java.util.Calendar cal = java.util.Calendar.getInstance();
      return cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private static void loadDay () {
      if (dayMs < 0) {
        day = org.thunderdog.challegram.unsorted.Settings.instance().pmc().getInt(KEY_DAY, 0);
        dayMs = org.thunderdog.challegram.unsorted.Settings.instance().pmc().getLong(KEY_DAY_MS, 0);
      }
      int t = today();
      if (t != day) {
        day = t;
        dayMs = 0;
        if (resumedAt != 0) resumedAt = SystemClock.elapsedRealtime(); // past midnight the new day counts from now
      }
    }

    private static void saveDay () {
      org.thunderdog.challegram.unsorted.Settings.instance().pmc().putInt(KEY_DAY, day);
      org.thunderdog.challegram.unsorted.Settings.instance().pmc().putLong(KEY_DAY_MS, dayMs);
    }

    public static synchronized void resume () {
      long now = SystemClock.elapsedRealtime();
      if (start == 0 || (pausedAt != 0 && now - pausedAt > BREAK_MS)) start = now;
      pausedAt = 0;
      loadDay();
      resumedAt = now;
    }

    public static synchronized void pause () {
      long now = SystemClock.elapsedRealtime();
      pausedAt = now;
      loadDay();
      if (resumedAt != 0) {
        dayMs += now - resumedAt;
        resumedAt = 0;
        saveDay();
      }
    }

    private static synchronized long sat () {
      if (start == 0) resume();
      if (DAILY) {
        loadDay();
        return dayMs + (resumedAt != 0 ? SystemClock.elapsedRealtime() - resumedAt : 0);
      }
      return SystemClock.elapsedRealtime() - start;
    }

    /** time in the app today, ms */
    static synchronized long todayMs () {
      // no resume() here: a message sent with the app in the background (a reply from a notification) asks for this on
      // TDLib's thread — it started «time in the app» with nobody in it, counted until the next visit ended
      loadDay();
      return dayMs + (resumedAt != 0 ? SystemClock.elapsedRealtime() - resumedAt : 0);
    }

    private static long gapAt (int i) {
      return (DAILY ? DAY_MINUTES[i] : AT_MINUTES[i]) * 60_000L;
    }

    /** how open gap i is now, 0…1 */
    private static float open (int i, long sat) {
      long at = gapAt(i);
      return sat <= at ? 0f : Math.min(1f, (sat - at) / (float) OPEN_MS);
    }

    /** when the line next changes (a gap starts or is opening), 0 — never more */
    static long nextChangeIn () {
      if (!ENABLED) return 0;
      long sat = sat();
      for (int i = 0; i < MAX_GAPS; i++) {
        long at = gapAt(i);
        if (sat < at) return Math.min(at - sat + 50, 60_000L);
        if (sat < at + OPEN_MS) return 500;
      }
      return 0;
    }

    /** the thin line from left to right with the sitting's gaps (soft ends, a few dp wide) */
    // (user 2026-11-15 «да, сделать сплошной»): no gaps for the time in the app any more — a plain whole line; code kept
    static final boolean ENABLED = false;

    static void drawLine (Canvas c, Paint paint, float left, float right, float y, float thin) {
      if (!ENABLED) {
        c.drawRect(left, y, right, y + thin, paint);
        return;
      }
      long sat = sat();
      float w = right - left;
      float[] from = new float[MAX_GAPS], to = new float[MAX_GAPS];
      int n = 0;
      for (int i = 0; i < MAX_GAPS; i++) {
        float o = open(i, sat);
        if (o <= 0f) break;
        float half = (Screen.dp(3f) + Screen.dp(4f) * o) / 2f; // 3 → 7 dp (user 2026-11-13 «давай шире чуть»)
        float cx = left + w * PLACES[i];
        from[n] = cx - half;
        to[n] = cx + half;
        n++;
      }
      // sort the gaps along the line, then draw the pieces between them
      for (int a = 1; a < n; a++) for (int b = a; b > 0 && from[b] < from[b - 1]; b--) {
        float f = from[b]; from[b] = from[b - 1]; from[b - 1] = f;
        float t = to[b]; to[b] = to[b - 1]; to[b - 1] = t;
      }
      float x = left;
      int base = paint.getColor();
      for (int i = 0; i <= n; i++) {
        float end = i < n ? from[i] : right;
        if (end > x) c.drawRect(x, y, end, y + thin, paint);
        if (i < n) {
          // a soft edge each side, so a gap looks cut cleanly, not chipped
          paint.setColor(ColorUtils.alphaColor(.45f * android.graphics.Color.alpha(base) / 255f, base | 0xff000000));
          c.drawRect(from[i] - Screen.dp(1f), y, from[i], y + thin, paint);
          c.drawRect(to[i], y, to[i] + Screen.dp(1f), y + thin, paint);
          paint.setColor(base);
          x = to[i] + Screen.dp(1f);
        }
      }
    }
  }

}
