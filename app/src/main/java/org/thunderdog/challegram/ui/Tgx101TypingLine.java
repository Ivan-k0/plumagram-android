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

  // (user 2026-11-13 «на светлой теме вообще не видно — цвета темнее и насыщеннее»): a palette for each theme
  private static int accent () { return Theme.isDark() ? Theme.getColor(ColorId.textNeutral) : 0xff1565c0; }
  private static int fast () { return Theme.isDark() ? 0xffb070f0 : 0xff6a1bc8; } // violet the fast hand runs into
  private static int warm () { return Theme.isDark() ? 0xfff08a5a : 0xffd84315; } // a deleted letter, near the limit
  private static int limit () { return Theme.isDark() ? Theme.getColor(ColorId.textNegative) : 0xffc62828; }

  Tgx101TypingLine (View host, TextView field) {
    this.host = host;
    this.field = field;
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

  /** the line along y (the field's bottom), width w */
  void draw (Canvas c, float y, int w) {
    float thin = Math.max(1, Screen.dp(.5f));
    paint.setShader(null);
    paint.setColor(Theme.separatorColor());
    Session.drawLine(c, paint, 0, w, y, thin);
    long next = Session.nextChangeIn();
    if (next > 0) host.postInvalidateDelayed(next);

    long now = SystemClock.uptimeMillis();
    float dt = lastFrame != 0 ? Math.min(64, now - lastFrame) : 16;
    lastFrame = now;

    // (user 2026-11-13 «длина с пульсом»): the line fills from the left with the message's length — up to the 4096
    // letters a message holds, square-rooted so the first words show at once; orange near the limit, red at it
    float fill = (float) Math.sqrt(Math.min(1f, length / 4096f));
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
  public static final class Session {
    private static long start, pausedAt;
    // (user 2026-11-13 «10 минут мало: первая через 3 минуты, потом 5-7-10-12-14-18-21-25-30-45-60»)
    private static final int[] AT_MINUTES = {3, 5, 7, 10, 12, 14, 18, 21, 25, 30, 45, 60};
    private static final int MAX_GAPS = AT_MINUTES.length;
    private static final long OPEN_MS = 30_000L, BREAK_MS = 5 * 60_000L;
    // the gaps' places: spread over the line but not evenly — fixed, so a gap never moves once it is there
    private static final float[] PLACES = {.47f, .18f, .74f, .31f, .88f, .09f, .6f, .39f, .81f, .24f, .67f, .54f};

    public static void resume () {
      long now = SystemClock.elapsedRealtime();
      if (start == 0 || (pausedAt != 0 && now - pausedAt > BREAK_MS)) start = now;
      pausedAt = 0;
    }

    public static void pause () {
      pausedAt = SystemClock.elapsedRealtime();
    }

    private static long sat () {
      if (start == 0) resume();
      return SystemClock.elapsedRealtime() - start;
    }

    /** how open gap i is now, 0…1 */
    private static float open (int i, long sat) {
      long at = AT_MINUTES[i] * 60_000L;
      return sat <= at ? 0f : Math.min(1f, (sat - at) / (float) OPEN_MS);
    }

    /** when the line next changes (a gap starts or is opening), 0 — never more */
    static long nextChangeIn () {
      if (!ENABLED) return 0;
      long sat = sat();
      for (int i = 0; i < MAX_GAPS; i++) {
        long at = AT_MINUTES[i] * 60_000L;
        if (sat < at) return Math.min(at - sat + 50, 60_000L);
        if (sat < at + OPEN_MS) return 500;
      }
      return 0;
    }

    /** the thin line from left to right with the sitting's gaps (soft ends, a few dp wide) */
    /** (user 2026-11-13 «я думал это как суточную обыграть — в задачи на потом»): the sitting's gaps are off for now */
    static final boolean ENABLED = true; // (user 2026-11-13 «та нет. по сеансу доделай уже»)

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
