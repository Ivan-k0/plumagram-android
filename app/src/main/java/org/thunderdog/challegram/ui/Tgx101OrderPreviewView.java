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
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 (user 2026-10-11, approved mockup «Жесты-и-порядок-меню»): «How it looks in a chat» — the message menu, the
 * swipe column or the selection bars, drawn from the order being edited, so a drag shows its result right away.
 */
public class Tgx101OrderPreviewView extends View {
  public static final int MODE_MENU = 0, MODE_COLUMN = 1, MODE_BARS = 2;

  public static final class Entry {
    final int icon;
    final String name;
    final boolean red, highlight;

    public Entry (int icon, String name, boolean red, boolean highlight) {
      this.icon = icon;
      this.name = name;
      this.red = red;
      this.highlight = highlight;
    }
  }

  private int mode = MODE_MENU;
  private final List<Entry> entries = new ArrayList<>();
  private Entry fixedBottom;
  private boolean columnHints;
  private final List<String> barTitles = new ArrayList<>();
  private final List<List<Entry>> bars = new ArrayList<>();
  private final RectF rect = new RectF();

  public Tgx101OrderPreviewView (Context context) {
    super(context);
  }

  /** Menu items top to bottom; {@code fixedBottom} — «Select» under a line */
  public void setMenu (List<Entry> items, Entry fixedBottom) {
    this.mode = MODE_MENU;
    this.entries.clear();
    this.entries.addAll(items);
    this.fixedBottom = fixedBottom;
    changed();
  }

  /** Column icons top to bottom; the highlighted one is «Reply», under the finger */
  public void setColumn (List<Entry> icons, boolean hints) {
    this.mode = MODE_COLUMN;
    this.entries.clear();
    this.entries.addAll(icons);
    this.columnHints = hints;
    changed();
  }

  public void setBars (List<String> titles, List<List<Entry>> rows) {
    this.mode = MODE_BARS;
    this.barTitles.clear();
    this.barTitles.addAll(titles);
    this.bars.clear();
    this.bars.addAll(rows);
    changed();
  }

  private void changed () {
    int height = desiredHeight();
    if (getLayoutParams() != null && getMeasuredHeight() != height) {
      requestLayout();
    }
    invalidate();
  }

  private int desiredHeight () {
    switch (mode) {
      case MODE_COLUMN:
        return Screen.dp(36f) + Math.max(3, entries.size()) * Screen.dp(40f) + Screen.dp(12f);
      case MODE_BARS:
        return Screen.dp(36f) + bars.size() * Screen.dp(64f);
      case MODE_MENU:
      default:
        return Screen.dp(44f) + entries.size() * Screen.dp(34f) + (fixedBottom != null ? Screen.dp(43f) : 0) + Screen.dp(20f);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), desiredHeight());
  }

  private void drawIcon (Canvas c, int res, float cx, float cy, int color) {
    if (res == 0) return;
    Drawable d = Drawables.get(getResources(), res);
    if (d != null) {
      Drawables.drawCentered(c, d, cx, cy, Paints.getPorterDuffPaint(color));
    }
  }

  /** A received message: a bubble with two grey lines of «text» */
  private void drawBubble (Canvas c, float left, float top, float width) {
    float height = Screen.dp(52f);
    rect.set(left, top, left + width, top + height);
    c.drawRoundRect(rect, Screen.dp(14f), Screen.dp(14f), Paints.fillingPaint(Theme.getColor(ColorId.bubbleIn_background)));
    int line = ColorUtils.alphaColor(.25f, Theme.getColor(ColorId.text));
    float lineHeight = Screen.dp(6f);
    rect.set(left + Screen.dp(12f), top + Screen.dp(15f), left + width - Screen.dp(22f), top + Screen.dp(15f) + lineHeight);
    c.drawRoundRect(rect, lineHeight / 2, lineHeight / 2, Paints.fillingPaint(line));
    rect.set(left + Screen.dp(12f), top + Screen.dp(31f), left + width * .55f, top + Screen.dp(31f) + lineHeight);
    c.drawRoundRect(rect, lineHeight / 2, lineHeight / 2, Paints.fillingPaint(line));
  }

  @Override
  protected void onDraw (Canvas c) {
    int width = getMeasuredWidth(), height = getMeasuredHeight();
    c.drawColor(Theme.getColor(ColorId.chatBackground));
    TextPaint caption = Paints.getRegularTextPaint(12f, Theme.getColor(ColorId.textLight));
    c.drawText(Lang.getString(R.string.Tgx101OrderPreview), Screen.dp(16f), Screen.dp(22f), caption);
    switch (mode) {
      case MODE_MENU: drawMenu(c, width); break;
      case MODE_COLUMN: drawColumn(c, width, height); break;
      case MODE_BARS: drawBars(c, width); break;
    }
  }

  private void drawMenu (Canvas c, int width) {
    float top = Screen.dp(36f);
    drawBubble(c, Screen.dp(16f), top, width * .3f);
    float left = width * .38f, right = width - Screen.dp(16f);
    float rowHeight = Screen.dp(34f);
    float bottom = top + Screen.dp(4f) + entries.size() * rowHeight + (fixedBottom != null ? Screen.dp(9f) + rowHeight : 0) + Screen.dp(4f);
    rect.set(left, top, right, bottom);
    c.drawRoundRect(rect, Screen.dp(14f), Screen.dp(14f), Paints.fillingPaint(Theme.fillingColor()));
    Paint stroke = Paints.strokeSmallPaint(Theme.separatorColor());
    c.drawRoundRect(rect, Screen.dp(14f), Screen.dp(14f), stroke);
    float y = top + Screen.dp(4f);
    for (Entry entry : entries) {
      drawMenuRow(c, entry, left, right, y, rowHeight);
      y += rowHeight;
    }
    if (fixedBottom != null) {
      y += Screen.dp(4f);
      c.drawRect(left, y, right, y + Math.max(1, Screen.dp(.5f)), Paints.fillingPaint(Theme.separatorColor()));
      y += Screen.dp(5f);
      drawMenuRow(c, fixedBottom, left, right, y, rowHeight);
    }
  }

  private void drawMenuRow (Canvas c, Entry entry, float left, float right, float y, float rowHeight) {
    if (entry.highlight) {
      rect.set(left, y, right, y + rowHeight);
      c.drawRect(rect, Paints.fillingPaint(ColorUtils.alphaColor(.18f, Theme.getColor(ColorId.iconActive))));
    }
    int iconColor = Theme.getColor(entry.red ? ColorId.iconNegative : ColorId.icon);
    drawIcon(c, entry.icon, left + Screen.dp(22f), y + rowHeight / 2, iconColor);
    TextPaint text = Paints.getRegularTextPaint(14f, Theme.getColor(entry.red ? ColorId.textNegative : ColorId.text));
    float textLeft = left + Screen.dp(44f);
    CharSequence name = TextUtils.ellipsize(entry.name, text, right - textLeft - Screen.dp(8f), TextUtils.TruncateAt.END);
    c.drawText(name, 0, name.length(), textLeft, y + rowHeight / 2 + Screen.dp(5f), text);
  }

  private void drawColumn (Canvas c, int width, int height) {
    int count = entries.size();
    float step = Screen.dp(40f);
    float columnTop = Screen.dp(36f) + step / 2;
    float cx = width - Screen.dp(34f);
    int replyIndex = 0;
    for (int i = 0; i < count; i++) if (entries.get(i).highlight) replyIndex = i;
    float replyY = columnTop + replyIndex * step;
    drawBubble(c, Screen.dp(16f), replyY - Screen.dp(26f), width * (columnHints ? .42f : .55f));
    for (int i = 0; i < count; i++) {
      Entry entry = entries.get(i);
      float cy = columnTop + i * step;
      float radius = Screen.dp(entry.highlight ? 18f : 14f);
      int fill = entry.highlight ? Theme.getColor(ColorId.iconActive) : ColorUtils.alphaColor(.12f, Theme.getColor(ColorId.text));
      c.drawCircle(cx, cy, radius, Paints.fillingPaint(fill));
      drawIcon(c, entry.icon, cx, cy, entry.highlight ? 0xffffffff : Theme.getColor(entry.red ? ColorId.iconNegative : ColorId.icon));
    }
    if (columnHints && count > 0) {
      TextPaint hint = Paints.getRegularTextPaint(11f, Theme.getColor(ColorId.textLight));
      TextPaint accent = Paints.getRegularTextPaint(11f, Theme.getColor(ColorId.textNeutral));
      float textRight = cx - Screen.dp(30f);
      if (replyIndex > 0) {
        String up = Lang.getString(R.string.Tgx101SwipeUp);
        c.drawText(up, textRight - hint.measureText(up), columnTop + Screen.dp(4f), hint);
      }
      String release = Lang.getString(R.string.Tgx101SwipeRelease);
      c.drawText(release, textRight - accent.measureText(release), replyY + Screen.dp(4f), accent);
      if (replyIndex < count - 1) {
        String down = Lang.getString(R.string.Tgx101SwipeDown);
        c.drawText(down, textRight - hint.measureText(down), columnTop + (count - 1) * step + Screen.dp(4f), hint);
      }
    }
  }

  private void drawBars (Canvas c, int width) {
    float y = Screen.dp(36f);
    TextPaint title = Paints.getRegularTextPaint(12f, Theme.getColor(ColorId.textLight));
    for (int b = 0; b < bars.size(); b++) {
      if (b < barTitles.size()) {
        c.drawText(barTitles.get(b), Screen.dp(16f), y + Screen.dp(12f), title);
      }
      List<Entry> row = bars.get(b);
      float top = y + Screen.dp(18f), barHeight = Screen.dp(40f);
      float barWidth = Math.min(width - Screen.dp(32f), Math.max(1, row.size()) * Screen.dp(44f) + Screen.dp(8f));
      rect.set(Screen.dp(16f), top, Screen.dp(16f) + barWidth, top + barHeight);
      c.drawRoundRect(rect, barHeight / 2, barHeight / 2, Paints.fillingPaint(Theme.fillingColor()));
      float cx = Screen.dp(16f) + Screen.dp(4f) + Screen.dp(22f);
      for (Entry entry : row) {
        if (entry.highlight) {
          c.drawCircle(cx, top + barHeight / 2, Screen.dp(17f), Paints.fillingPaint(ColorUtils.alphaColor(.18f, Theme.getColor(ColorId.iconActive))));
        }
        drawIcon(c, entry.icon, cx, top + barHeight / 2, Theme.getColor(ColorId.icon));
        cx += Screen.dp(44f);
      }
      y += Screen.dp(64f);
    }
  }
}
