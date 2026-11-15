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
 * message's length fills it from the left; at the fill's end a pulse answers the typing: a slow hand gives a short calm
 * blue glow, a fast one spreads it wider, brighter and towards violet, a deleted letter warms it for a moment; the pulse
 * fades a second after the last key, the fill stays while the text does.
 */
public final class Tgx101TypingLine {
  private final View host;   // the field's wrap: it draws the line
  private final TextView field;

  private final long[] keys = new long[16];
  private int keyCount;
  private float energy;      // 0 — idle, 1 — typing fast
  private float kick;        // a short pulse on each key
  private boolean deleting;
  private long lastFrame;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Runnable wake = this::invalidateHost;

  private void invalidateHost () {
    host.invalidate();
  }

  // (user 2026-11-13 «на светлой теме вообще не видно — цвета темнее и насыщеннее»): a palette for each theme
  private static int accent () { return Theme.isDark() ? Theme.getColor(ColorId.textNeutral) : 0xff1565c0; }
  private static int fast () { return Theme.isDark() ? 0xffb070f0 : 0xff6a1bc8; } // violet the fast hand runs into
  private static int warm () { return Theme.isDark() ? 0xfff08a5a : 0xffd84315; } // a deleted letter, near the limit
  private static int limit () { return Theme.isDark() ? Theme.getColor(ColorId.textNegative) : 0xffc62828; }

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

  /** the text changed by the person: longer — a key, shorter — a deletion */
  private int length;
  private float shownFill; // the fill as drawn, easing to the length

  /** the text the field holds now (a message is up to 4096 letters) */
  void setLength (int length) {
    this.length = length;
    host.invalidate();
  }

  void onTyped (boolean longer) {
    long now = SystemClock.uptimeMillis();
    if (longer) {
      keys[keyCount++ % keys.length] = now;
      deleting = false;
    } else {
      deleting = true;
    }
    int recent = 0;
    for (int i = 0; i < Math.min(keyCount, keys.length); i++) if (now - keys[i] < 1500) recent++;
    if (longer && recent >= 3) Tempo.add(recent / 1.5f);
    float speed = Math.min(1f, (recent / 1.5f) / 7f); // ~7 keys a second is «fast»
    energy = Math.max(energy * .6f + speed * .4f, longer ? .18f : energy);
    kick = 1f;
    lastFrame = now;
    host.invalidate();
  }

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
    float sound = Sound.shown(host);
    paint.setColor(Theme.separatorColor());
    // (user 2026-11-14 23:46 «при открытой клавиатуре внизу поля ввода не должна быть видна полоса»): while typing — the
    // plain line, the word only at rest. By the keyboard, not the field's focus: a chat keeps the cursor in the field
    // with the keyboard down, and the word never showed (user 2026-11-15 «морзе не работает?»)
    if (sound > 0f) {
      Sound.draw(c, y, w, sound, thin, host.getHeight() - y);
    } else if (Morse.ENABLED && org.thunderdog.challegram.unsorted.Settings.instance().tgx101MorseLine() && !keyboardUp()) {
      Morse.draw(c, paint, tdlib, host, 0, w, y, thin);
    } else {
      Session.drawLine(c, paint, 0, w, y, thin);
    }
    // (log 2026-11-14 19:44: an idle chat drew 1828 frames in 15 s, 70 % of a core) every drawing posted one more delayed
    // invalidate, each of them posted another when it fired — the chains piled up into a redraw without end. One pending
    // wake-up only, replaced, never added to
    long next = Session.nextChangeIn();
    host.removeCallbacks(wake);
    if (next > 0) host.postDelayed(wake, next);

    long now = SystemClock.uptimeMillis();
    float dt = lastFrame != 0 ? Math.min(64, now - lastFrame) : 16;
    lastFrame = now;

    // (user 2026-11-13 «длина с пульсом»): the line fills from the left with the message's length — up to the 4096
    // letters a message holds, square-rooted so the first words show at once; orange near the limit, red at it
    // (user 2026-11-14 «в простое полоса должна быть только рваной»): the length and the pulse only while the field is
    // being typed in; at rest just the torn thin line
    boolean active = field.isFocused();
    if (!active) energy = kick = 0f;
    float fill = active ? (float) Math.sqrt(Math.min(1f, length / 4096f)) : 0f;
    boolean easing = Math.abs(fill - shownFill) > .002f;
    shownFill = easing ? shownFill + (fill - shownFill) * Math.min(1f, dt / 90f) : fill;
    int fillColor = shownFill > .97f ? limit() : shownFill > .85f ? warm() : accent();
    if (shownFill > 0f) {
      paint.setShader(null);
      paint.setColor(ColorUtils.alphaColor(Theme.isDark() ? .85f : 1f, fillColor));
      c.drawRect(0, y, w * shownFill, y + Math.max(thin, Screen.dp(1.5f)), paint);
    }
    if (easing) host.postInvalidateOnAnimation();
    boolean idle = keyCount == 0 || now - keys[(keyCount - 1) % keys.length] > 350;
    if (idle) energy = Math.max(0f, energy - dt / 900f);
    kick = Math.max(0f, kick - dt / 260f);
    float glow = Math.max(energy, kick * .55f);
    if (glow <= .01f) {
      energy = kick = 0f;
      return;
    }

    float cx = shownFill > 0f ? w * shownFill : caretX(w); // the pulse rides the fill's end
    // (user 2026-11-13 «пульс не доходит дальше половины поля — продлить, но не до самого конца»): fast typing carries it
    // ahead up to 85 % of the field, a little behind the fill's end too
    float right = Math.min(w * .85f, cx + Screen.dp(24f) + w * .85f * energy);
    float left = Math.max(0f, cx - Screen.dp(24f) - Screen.dp(90f) * energy);
    if (right <= left + 1f) right = left + Screen.dp(24f);
    int color = deleting && kick > 0f ? ColorUtils.fromToArgb(accent(), warm(), kick) : ColorUtils.fromToArgb(accent(), fast(), Math.max(0f, energy - .35f) / .65f);
    float strength = Theme.isDark() ? .35f + .65f * glow : .55f + .45f * glow; // light: denser, it must show on white
    int mid = ColorUtils.alphaColor(Math.min(1f, strength), color);
    int edge = ColorUtils.alphaColor(0f, color);
    float at = Math.max(0f, Math.min(1f, (cx - left) / (right - left)));
    paint.setShader(new LinearGradient(left, 0, right, 0, new int[] {edge, mid, edge}, new float[] {0f, at, 1f}, Shader.TileMode.CLAMP));
    float thick = Screen.dp(Theme.isDark() ? 1.5f : 2f) + Screen.dp(1f) * energy;
    c.drawRect(left, y - thick / 2f + thin / 2f, right, y + thick / 2f + thin / 2f, paint);
    // a soft halo below the line, into the room under the field
    paint.setShader(new LinearGradient(0, y, 0, y + Screen.dp(10f), ColorUtils.alphaColor((Theme.isDark() ? .22f : .16f) * glow, color), ColorUtils.alphaColor(0f, color), Shader.TileMode.CLAMP));
    c.drawRect(left + (cx - left) * .3f, y, cx + (right - cx) * .7f, y + Screen.dp(10f), paint);
    paint.setShader(null);
    host.postInvalidateOnAnimation();
  }

  /**
   * MagiX's live example (user 2026-11-13 «полоску и пульс нужно показать наглядно людям»): the same line for a field
   * running from left to right at y — the fill (0…1 of the width) and the pulse at its end (energy 0…1)
   */
  static void drawDemo (Canvas c, Paint paint, float left, float right, float y, float fill, float energy) {
    float w = right - left;
    float thin = Math.max(1, Screen.dp(.5f));
    paint.setShader(null);
    paint.setColor(Theme.separatorColor());
    Session.drawLine(c, paint, left, right, y, thin);
    if (fill > 0f) {
      paint.setColor(ColorUtils.alphaColor(Theme.isDark() ? .85f : 1f, accent()));
      c.drawRect(left, y, left + w * fill, y + Math.max(thin, Screen.dp(1.5f)), paint);
    }
    if (energy <= .01f) return;
    float cx = left + w * Math.max(fill, .02f);
    float r = Math.min(left + w * .85f, cx + Screen.dp(24f) + w * .85f * energy);
    float l = Math.max(left, cx - Screen.dp(24f) - Screen.dp(90f) * energy);
    int color = ColorUtils.fromToArgb(accent(), fast(), Math.max(0f, energy - .35f) / .65f);
    float strength = Theme.isDark() ? .35f + .65f * energy : .55f + .45f * energy;
    float at = Math.max(0f, Math.min(1f, (cx - l) / Math.max(1f, r - l)));
    paint.setShader(new LinearGradient(l, 0, r, 0, new int[] {ColorUtils.alphaColor(0f, color), ColorUtils.alphaColor(Math.min(1f, strength), color), ColorUtils.alphaColor(0f, color)}, new float[] {0f, at, 1f}, Shader.TileMode.CLAMP));
    float thick = Screen.dp(Theme.isDark() ? 1.5f : 2f) + Screen.dp(1f) * energy;
    c.drawRect(l, y - thick / 2f + thin / 2f, r, y + thick / 2f + thin / 2f, paint);
    paint.setShader(null);
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
    private static float shown;
    private static long shownAt;
    private static java.lang.ref.WeakReference<View> hostRef;
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
          wanted = s.tgx101MorseLine() && s.tgx101TypingLine();
        } catch (Throwable t) {
          wanted = false;
        }
      }
      return wanted;
    }

    /** from any thread */
    public static void push (float level) {
      if (!wanted()) return;
      target = Math.max(0f, Math.min(1f, level));
      at = SystemClock.uptimeMillis();
      java.lang.ref.WeakReference<View> ref = hostRef;
      View host = ref != null ? ref.get() : null;
      if (host != null) host.postInvalidateOnAnimation();
    }

    private static volatile long recordingAt;

    /** the microphone's level while a voice note is being recorded */
    public static void pushRecording (float level) {
      recordingAt = SystemClock.uptimeMillis();
      push(level);
    }

    /** the level to show in {@code delay} ms */
    public static void pushAt (float level, long delay) {
      if (delay <= 0) push(level);
      else main.postDelayed(() -> push(level), delay);
    }

    /** the level as drawn: rises fast, falls softly, gone a quarter of a second after the last sound */
    static float shown (View host) {
      java.lang.ref.WeakReference<View> ref = hostRef;
      if (ref == null || ref.get() != host) hostRef = new java.lang.ref.WeakReference<>(host);
      long now = SystemClock.uptimeMillis();
      float dt = shownAt != 0 ? Math.min(64, now - shownAt) : 16;
      shownAt = now;
      float to = now - at > 250 ? 0f : target;
      shown += (to - shown) * Math.min(1f, dt / (to > shown ? 60f : 240f));
      if (shown < .012f && to == 0f) {
        shown = 0f;
        // the rings already on their way run out to the ends first (about a second and a half)
        if (now - at > 250 + 1800) {
          java.util.Arrays.fill(history, 0f);
          historyAt = 0;
          return 0f;
        }
        host.postInvalidateOnAnimation();
        return .0001f;
      }
      host.postInvalidateOnAnimation();
      return shown;
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

    static void draw (Canvas c, float y, int w, float level, float thin, float roomBelow) {
      remember(level);
      // (phone check 2026-11-15 17:34): the recording panel lies over the field down to the line and hid the wave's upper
      // half — while recording, with room under the line, the wave swings down from the line only
      boolean down = SystemClock.uptimeMillis() - recordingAt < 2500 && roomBelow >= Screen.dp(10f);
      float t = SystemClock.uptimeMillis() / 1000f;
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
        float yy = y + thin / 2f + (down ? (swing + 1f) * .75f : swing) * past * amp * fade;
        if (x == 0) wavePath.moveTo(x, yy); else wavePath.lineTo(x, yy);
      }
      wavePaint.setStyle(Paint.Style.STROKE);
      wavePaint.setStrokeWidth(thin);
      wavePaint.setStrokeJoin(Paint.Join.ROUND);
      wavePaint.setColor(Theme.separatorColor());
      c.drawPath(wavePath, wavePaint);
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

  /**
   * The day's typing pace: letters a second while typing, averaged over about a day (older bursts count less and less),
   * kept on the phone only — no text, no times of messages, just one number
   */
  static final class Tempo {
    private static final String KEY = "tgx101_typing_tempo", KEY_AT = "tgx101_typing_tempo_at";
    private static float rate = -1f;
    private static long at;
    private static int unsaved;

    private static void load () {
      if (rate >= 0f) return;
      rate = org.thunderdog.challegram.unsorted.Settings.instance().pmc().getFloat(KEY, 0f);
      at = org.thunderdog.challegram.unsorted.Settings.instance().pmc().getLong(KEY_AT, 0);
    }

    static void add (float lettersPerSecond) {
      load();
      long now = System.currentTimeMillis();
      // a day-long memory: what was typed a day ago weighs about a third
      float keep = at == 0 ? 0f : (float) Math.exp(-(now - at) / 86_400_000d) * .97f;
      rate = rate * keep + Math.min(15f, lettersPerSecond) * (1f - keep);
      at = now;
      if (++unsaved >= 20) save();
    }

    static void save () {
      if (rate < 0f) return;
      unsaved = 0;
      org.thunderdog.challegram.unsorted.Settings.instance().pmc().putFloat(KEY, rate);
      org.thunderdog.challegram.unsorted.Settings.instance().pmc().putLong(KEY_AT, at);
    }

    static float rate () {
      load();
      return rate;
    }

    /** blue below ~2.5 letters a second, violet around 5, pink past 7 */
    static int color () {
      float r = rate();
      int blue = accent(), violet = Theme.isDark() ? 0xff9a7af0 : 0xff6a1bc8, pink = Theme.isDark() ? 0xffe77fb5 : 0xffc2185b;
      if (r <= 2.5f) return blue;
      if (r <= 5f) return ColorUtils.fromToArgb(blue, violet, (r - 2.5f) / 2.5f);
      return ColorUtils.fromToArgb(violet, pink, Math.min(1f, (r - 5f) / 2.5f));
    }
  }

  /** where the caret stands, in the wrap's coordinates; the field's middle if unknown */
  private float caretX (int w) {
    float x = w / 2f;
    try {
      Layout l = field.getLayout();
      int sel = field.getSelectionEnd();
      if (l != null && sel >= 0) {
        x = l.getPrimaryHorizontal(Math.min(sel, l.getText().length())) + field.getTotalPaddingLeft() - field.getScrollX();
        View v = field;
        while (v != null && v != host) {
          x += v.getLeft() + v.getTranslationX();
          v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
      }
    } catch (Throwable ignored) { }
    return Math.max(0f, Math.min(w, x));
  }
}
