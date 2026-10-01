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
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.PopupLayout;

/**
 * TGx101: «How to use» for the video player — a drawn phone screen with the gesture zones, arrows and numbers,
 * and below it the same numbers explained in words.
 */
final class Tgx101PlayerGuide {
  private Tgx101PlayerGuide () { }

  static void show (BaseActivity context) {
    PopupLayout popup = new PopupLayout(context);
    popup.setNeedRootInsets();

    FrameLayout wrap = new FrameLayout(context);
    wrap.setBackgroundColor(0x99000000);
    wrap.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    wrap.setOnClickListener(v -> popup.hideWindow(true));

    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setClickable(true);
    GradientDrawable background = new GradientDrawable();
    background.setColor(0xff1b222a);
    background.setCornerRadius(Screen.dp(20f));
    card.setBackground(background);
    int pad = Screen.dp(16f);
    card.setPadding(pad, pad, pad, pad);

    TextView title = new TextView(context);
    title.setText(Lang.getString(R.string.Tgx101GuideTitle));
    title.setTextColor(0xffffffff);
    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
    title.setTypeface(Fonts.getRobotoBold());
    card.addView(title);

    ScrollView scroll = new ScrollView(context);
    LinearLayout content = new LinearLayout(context);
    content.setOrientation(LinearLayout.VERTICAL);
    Diagram diagram = new Diagram(context);
    LinearLayout.LayoutParams diagramParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(400f));
    diagramParams.topMargin = Screen.dp(12f);
    content.addView(diagram, diagramParams);

    String[] lines = Lang.getString(R.string.Tgx101GuideText).split("\n");
    for (int i = 0; i < lines.length; i++) {
      LinearLayout row = new LinearLayout(context);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setPadding(0, Screen.dp(8f), 0, 0);
      TextView number = new TextView(context);
      number.setText(Integer.toString(i + 1));
      number.setGravity(Gravity.CENTER);
      number.setTextColor(0xffffffff);
      number.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
      number.setTypeface(Fonts.getRobotoBold());
      GradientDrawable badge = new GradientDrawable();
      badge.setShape(GradientDrawable.OVAL);
      badge.setColor(Diagram.COLORS[i % Diagram.COLORS.length]);
      number.setBackground(badge);
      row.addView(number, new LinearLayout.LayoutParams(Screen.dp(22f), Screen.dp(22f)));
      TextView text = new TextView(context);
      text.setText(lines[i]);
      text.setTextColor(0xe6ffffff);
      text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
      text.setLineSpacing(Screen.dp(2f), 1f);
      LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      textParams.leftMargin = Screen.dp(10f);
      row.addView(text, textParams);
      content.addView(row);
    }
    scroll.addView(content);
    card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    TextView ok = new TextView(context);
    ok.setText(Lang.getString(R.string.Tgx101GuideOk));
    ok.setGravity(Gravity.CENTER);
    ok.setTextColor(0xffffffff);
    ok.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    ok.setTypeface(Fonts.getRobotoMedium());
    GradientDrawable okBackground = new GradientDrawable();
    okBackground.setColor(0xff5b87b0);
    okBackground.setCornerRadius(Screen.dp(12f));
    ok.setBackground(okBackground);
    ok.setOnClickListener(v -> popup.hideWindow(true));
    LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(44f));
    okParams.topMargin = Screen.dp(12f);
    card.addView(ok, okParams);

    FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(Math.min(Screen.currentWidth() - Screen.dp(24f), Screen.dp(440f)), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL);
    cardParams.topMargin = Screen.getStatusBarHeight() + Screen.dp(12f);
    cardParams.bottomMargin = Screen.dp(24f);
    wrap.addView(card, cardParams);
    popup.showNonAnimatedView(wrap);
  }

  /** A phone screen with the zones; numbers match the list below */
  private static final class Diagram extends View {
    static final int[] COLORS = {0xffe0a43a, 0xff5b87b0, 0xff8e6bbf, 0xff3fa65a, 0xffd9534f, 0xff2fa0a0, 0xffc76b98, 0xff7a8a99, 0xff607080};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    Diagram (Context context) {
      super(context);
      line.setStyle(Paint.Style.STROKE);
      line.setStrokeCap(Paint.Cap.ROUND);
      line.setStrokeJoin(Paint.Join.ROUND);
      text.setTextAlign(Paint.Align.CENTER);
      text.setFakeBoldText(true);
    }

    private void zone (Canvas c, float l, float t, float r, float b, int color) {
      fill.setColor((color & 0x00ffffff) | 0x33000000);
      rect.set(l, t, r, b);
      c.drawRect(rect, fill);
    }

    private void arrow (Canvas c, float x1, float y1, float x2, float y2, int color, boolean both) {
      line.setColor(color);
      line.setStrokeWidth(Screen.dp(3f));
      line.setPathEffect(null);
      c.drawLine(x1, y1, x2, y2, line);
      head(c, x2, y2, x1, y1, color);
      if (both) head(c, x1, y1, x2, y2, color);
    }

    private void head (Canvas c, float x, float y, float fromX, float fromY, int color) {
      double angle = Math.atan2(y - fromY, x - fromX);
      float len = Screen.dp(9f);
      path.reset();
      path.moveTo(x, y);
      path.lineTo((float) (x - len * Math.cos(angle - .5)), (float) (y - len * Math.sin(angle - .5)));
      path.moveTo(x, y);
      path.lineTo((float) (x - len * Math.cos(angle + .5)), (float) (y - len * Math.sin(angle + .5)));
      c.drawPath(path, line);
    }

    private void badge (Canvas c, float x, float y, int number) {
      fill.setColor(COLORS[(number - 1) % COLORS.length]);
      c.drawCircle(x, y, Screen.dp(11f), fill);
      text.setColor(0xffffffff);
      text.setTextSize(Screen.dp(12f));
      c.drawText(Integer.toString(number), x, y + Screen.dp(4.5f), text);
    }

    private void dot (Canvas c, float x, float y, int color, float radius) {
      fill.setColor(color);
      c.drawCircle(x, y, radius, fill);
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      float h = getMeasuredHeight(), w = Math.min(getMeasuredWidth(), h * .52f);
      float left = (getMeasuredWidth() - w) / 2f, top = 0, right = left + w, bottom = h;
      // the phone
      fill.setColor(0xff0e1318);
      rect.set(left, top, right, bottom);
      c.drawRoundRect(rect, Screen.dp(22f), Screen.dp(22f), fill);
      line.setColor(0xff3a4652);
      line.setStrokeWidth(Screen.dp(2f));
      line.setPathEffect(null);
      c.drawRoundRect(rect, Screen.dp(22f), Screen.dp(22f), line);
      float third = w / 3f, shade = h * .09f;
      // zones: brightness (left third), volume (right third), the top edge for the notification shade
      zone(c, left, top + shade, left + third, bottom - Screen.dp(8f), COLORS[0]);
      zone(c, right - third, top + shade, right, bottom - Screen.dp(8f), COLORS[1]);
      fill.setColor(0x33ffffff);
      rect.set(left + Screen.dp(8f), top + Screen.dp(6f), right - Screen.dp(8f), top + shade);
      c.drawRoundRect(rect, Screen.dp(8f), Screen.dp(8f), fill);
      // edge strips for paging
      line.setColor(COLORS[4]);
      line.setStrokeWidth(Screen.dp(1.5f));
      line.setPathEffect(new DashPathEffect(new float[] {Screen.dp(4f), Screen.dp(4f)}, 0));
      c.drawLine(left + w * .12f, top + shade, left + w * .12f, bottom - Screen.dp(10f), line);
      c.drawLine(right - w * .12f, top + shade, right - w * .12f, bottom - Screen.dp(10f), line);

      float midY = top + h * .5f;
      // 1 brightness, 2 volume — vertical double arrows
      arrow(c, left + third / 2f, midY - h * .16f, left + third / 2f, midY + h * .16f, COLORS[0], true);
      badge(c, left + third / 2f, midY - h * .16f - Screen.dp(18f), 1);
      arrow(c, right - third / 2f, midY - h * .16f, right - third / 2f, midY + h * .16f, COLORS[1], true);
      badge(c, right - third / 2f, midY - h * .16f - Screen.dp(18f), 2);
      // 3 close — down arrow in the middle third
      float cx = left + w / 2f;
      arrow(c, cx, top + h * .2f, cx, top + h * .36f, COLORS[2], false);
      badge(c, cx, top + h * .2f - Screen.dp(16f), 3);
      // 4 seek — sideways double arrow across the middle
      arrow(c, left + third * .7f, midY + h * .24f, right - third * .7f, midY + h * .24f, COLORS[3], true);
      badge(c, cx, midY + h * .24f - Screen.dp(18f), 4);
      // 5 paging from the edge
      arrow(c, left + Screen.dp(6f), bottom - h * .12f, left + w * .2f, bottom - h * .12f, COLORS[4], false);
      arrow(c, right - Screen.dp(6f), bottom - h * .12f, right - w * .2f, bottom - h * .12f, COLORS[4], false);
      badge(c, left + w * .12f, bottom - h * .12f - Screen.dp(20f), 5);
      // 6 double tap in the middle
      dot(c, cx - Screen.dp(7f), midY, COLORS[5], Screen.dp(6f));
      dot(c, cx + Screen.dp(7f), midY, COLORS[5], Screen.dp(6f));
      badge(c, cx, midY - Screen.dp(22f), 6);
      // 7 hold
      fill.setColor((COLORS[6] & 0x00ffffff) | 0x55000000);
      c.drawCircle(cx, midY + h * .1f, Screen.dp(13f), fill);
      dot(c, cx, midY + h * .1f, COLORS[6], Screen.dp(6f));
      badge(c, cx + Screen.dp(24f), midY + h * .1f, 7);
      // 8 one tap — the controls (the capsule at the bottom)
      fill.setColor(0xcc1f2830);
      rect.set(left + Screen.dp(10f), bottom - Screen.dp(36f), right - Screen.dp(10f), bottom - Screen.dp(12f));
      c.drawRoundRect(rect, Screen.dp(12f), Screen.dp(12f), fill);
      badge(c, cx, bottom - Screen.dp(24f), 8);
      // 9 the top edge
      badge(c, cx, top + shade / 2f + Screen.dp(3f), 9);
    }
  }
}
