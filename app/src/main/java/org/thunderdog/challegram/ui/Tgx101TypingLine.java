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
final class Tgx101TypingLine {
  private final View host;   // the field's wrap: it draws the line
  private final TextView field;

  private final long[] keys = new long[16];
  private int keyCount;
  private float energy;      // 0 — idle, 1 — typing fast
  private float kick;        // a short pulse on each key
  private boolean deleting;
  private long lastFrame;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private static final int FAST = 0xffb070f0; // violet the fast hand runs into
  private static final int WARM = 0xfff08a5a; // a deleted letter

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
    c.drawRect(0, y, w, y + thin, paint);

    long now = SystemClock.uptimeMillis();
    float dt = lastFrame != 0 ? Math.min(64, now - lastFrame) : 16;
    lastFrame = now;

    // (user 2026-11-13 «длина с пульсом»): the line fills from the left with the message's length — up to the 4096
    // letters a message holds, square-rooted so the first words show at once; orange near the limit, red at it
    float fill = (float) Math.sqrt(Math.min(1f, length / 4096f));
    boolean easing = Math.abs(fill - shownFill) > .002f;
    shownFill = easing ? shownFill + (fill - shownFill) * Math.min(1f, dt / 90f) : fill;
    int accentFill = Theme.getColor(ColorId.textNeutral);
    int fillColor = shownFill > .97f ? Theme.getColor(ColorId.textNegative) : shownFill > .85f ? WARM : accentFill;
    if (shownFill > 0f) {
      paint.setShader(null);
      paint.setColor(ColorUtils.alphaColor(.85f, fillColor));
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
    float half = Screen.dp(22f) + Screen.dp(170f) * energy;
    int accent = Theme.getColor(ColorId.textNeutral);
    int color = deleting && kick > 0f ? ColorUtils.fromToArgb(accent, WARM, kick) : ColorUtils.fromToArgb(accent, FAST, Math.max(0f, energy - .35f) / .65f);
    int mid = ColorUtils.alphaColor(Math.min(1f, .35f + .65f * glow), color);
    int edge = ColorUtils.alphaColor(0f, color);
    paint.setShader(new LinearGradient(cx - half, 0, cx + half, 0, new int[] {edge, mid, edge}, new float[] {0f, .5f, 1f}, Shader.TileMode.CLAMP));
    float thick = Screen.dp(1.5f) + Screen.dp(1f) * energy;
    c.drawRect(cx - half, y - thick / 2f + thin / 2f, cx + half, y + thick / 2f + thin / 2f, paint);
    // a soft halo below the line, into the room under the field
    paint.setShader(new LinearGradient(0, y, 0, y + Screen.dp(10f), ColorUtils.alphaColor(.22f * glow, color), ColorUtils.alphaColor(0f, color), Shader.TileMode.CLAMP));
    c.drawRect(cx - half * .7f, y, cx + half * .7f, y + Screen.dp(10f), paint);
    paint.setShader(null);
    host.postInvalidateOnAnimation();
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
