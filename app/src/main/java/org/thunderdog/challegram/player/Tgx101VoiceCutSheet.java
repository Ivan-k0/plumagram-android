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
package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.ui.Tgx101Sheet;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 (user 2026-11-12, mockup «Голосовое: прослушка и обрезка», Б1/Б2): the voice note's editor, opened by the
 * scissors. Above the wave the two red flags of the piece to cut; on the wave itself the yellow edges that trim its
 * start and end (moving the start puts the listening there, so ▶ tells how the note begins); below it, apart so it isn't
 * caught by the edges, the slider where listening starts, with its line on the wave. ▶ plays from the slider, «▶ piece»
 * the piece between the flags, the scissors cut it out, the arrow brings back the last cut. No hint text.
 * «Cancel» returns the note as it was when the editor opened, «Done» keeps the changes.
 */
public final class Tgx101VoiceCutSheet {
  private Tgx101VoiceCutSheet () { }

  private static final int RED = 0xffe5533f, YELLOW = 0xfff2c14e;

  public static void show (ViewController<?> c, Tgx101VoiceEdit edit, Runnable onClose) {
    final Object snapshot = edit.editorState();
    final boolean[] done = {false};
    final Context context = c.context();
    final Tgx101Sheet.Handle[] handle = new Tgx101Sheet.Handle[1];
    final int baseCuts = countCuts(edit);
    handle[0] = Tgx101Sheet.show(c, () -> {
      edit.stop();
      if (!done[0]) edit.restoreEditorState(snapshot);
      onClose.run();
    }, body -> {
      int pad = Screen.dp(18f);

      LinearLayout header = new LinearLayout(context);
      header.setOrientation(LinearLayout.HORIZONTAL);
      header.setGravity(Gravity.CENTER_VERTICAL);
      header.setPadding(pad, Screen.dp(6f), pad, 0);
      TextView title = text(context, Lang.getString(R.string.Tgx101VoiceEditTitle), 18f, Theme.textAccentColor(), true);
      header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      TextView lengths = text(context, "", 14f, Theme.textDecentColor(), false);
      header.addView(lengths);
      body.addView(header);

      final EditView view = new EditView(context, edit);
      LinearLayout.LayoutParams viewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(EditView.HEIGHT));
      viewParams.leftMargin = viewParams.rightMargin = pad;
      viewParams.topMargin = Screen.dp(14f);
      body.addView(view, viewParams);

      // TGx101 (user 2026-11-12, mockup Ф1 + «Отмена — красная обводка, Готово — синяя обводка, без заливки»): the app's own
      // button shape — rounded rectangles, 14 dp corners, 44 / 40 dp tall; ▶ a blue tint (not like «Done»), the piece a red
      // tint taking the room left, «bring back» grey, the scissors red; the row always fits (the piece shrinks, «bring back»
      // takes no room when there's nothing to bring back)
      final int blue = Theme.getColor(ColorId.textNeutral);
      LinearLayout actions = new LinearLayout(context);
      actions.setOrientation(LinearLayout.HORIZONTAL);
      actions.setGravity(Gravity.CENTER_VERTICAL);
      actions.setPadding(pad, Screen.dp(14f), pad, 0);
      final ImageView play = iconBox(context, R.drawable.baseline_play_arrow_36_white, ColorUtils.alphaColor(.18f, blue), 0, blue);
      play.setContentDescription(Lang.getString(R.string.Tgx101VoiceCutPlayAll));
      actions.addView(play, new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(44f)));
      final TextView playPiece = textBox(context, "", ColorUtils.alphaColor(.14f, RED), 0, 0xfff08a7c);
      LinearLayout.LayoutParams pieceParams = new LinearLayout.LayoutParams(0, Screen.dp(44f), 1f);
      pieceParams.leftMargin = Screen.dp(8f);
      actions.addView(playPiece, pieceParams);
      final ImageView bringBack = iconBox(context, R.drawable.baseline_undo_24, ColorUtils.alphaColor(.07f, Theme.textAccentColor()), 0, Theme.textAccentColor());
      bringBack.setContentDescription(Lang.getString(R.string.Tgx101VoiceCutBringBack));
      LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(Screen.dp(44f), Screen.dp(44f));
      backParams.leftMargin = Screen.dp(8f);
      actions.addView(bringBack, backParams);
      final ImageView cut = iconBox(context, R.drawable.baseline_content_cut_24, RED, 0, 0xffffffff);
      cut.setContentDescription(Lang.getString(R.string.Tgx101VoiceCutDo));
      LinearLayout.LayoutParams cutParams = new LinearLayout.LayoutParams(Screen.dp(56f), Screen.dp(44f));
      cutParams.leftMargin = Screen.dp(8f);
      actions.addView(cut, cutParams);
      body.addView(actions);

      LinearLayout bottom = new LinearLayout(context);
      bottom.setOrientation(LinearLayout.HORIZONTAL);
      bottom.setPadding(pad, Screen.dp(16f), pad, Screen.dp(4f));
      TextView cancel = textBox(context, Lang.getString(R.string.Cancel), 0, ColorUtils.alphaColor(.85f, RED), 0xfff08a7c);
      bottom.addView(cancel, new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f));
      TextView ok = textBox(context, Lang.getString(R.string.Tgx101VoiceCutDone), 0, ColorUtils.alphaColor(.9f, blue), blue);
      LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f);
      okParams.leftMargin = Screen.dp(10f);
      bottom.addView(ok, okParams);
      body.addView(bottom);

      final boolean[] wasPlaying = {false};
      final Runnable refresh = () -> {
        int was = Math.max(1, Math.round(edit.samples / (float) Tgx101VoiceEdit.RATE));
        int now = Math.max(1, Math.round(edit.keptSamples() / (float) Tgx101VoiceEdit.RATE));
        int cuts = countCuts(edit);
        String t = was != now ? Strings.buildDuration(was) + " → " + Strings.buildDuration(now) : Strings.buildDuration(was);
        if (cuts > 0) t += " · " + Lang.getString(R.string.Tgx101VoiceCutCount, cuts);
        lengths.setText(t);
        int piece = Math.abs(view.flag2 - view.flag1);
        playPiece.setText("▶  " + Lang.getString(R.string.Tgx101VoiceCutPiece, Math.max(1, Math.round(piece / (float) Tgx101VoiceEdit.RATE))));
        // nothing chosen yet — the piece and the scissors wait for the flag to be pulled apart
        boolean hasPiece = view.hasPiece();
        playPiece.setEnabled(hasPiece);
        playPiece.setAlpha(hasPiece ? 1f : .4f);
        cut.setEnabled(hasPiece);
        cut.setAlpha(hasPiece ? 1f : .4f);
        if (edit.isPlaying() != wasPlaying[0]) {
          wasPlaying[0] = edit.isPlaying();
          play.setImageResource(edit.isPlaying() ? R.drawable.baseline_pause_24 : R.drawable.baseline_play_arrow_36_white);
        }
        bringBack.setVisibility(edit.canBringBack() ? View.VISIBLE : View.GONE);
        view.invalidate();
      };
      view.onChanged = refresh;
      final Tgx101VoiceEdit.Listener listener = refresh::run;
      edit.addListener(listener);
      refresh.run();

      play.setOnClickListener(v -> {
        if (edit.isPlaying()) edit.pause();
        else edit.playKept();
      });
      playPiece.setOnClickListener(v -> {
        if (edit.isPlaying()) edit.pause();
        else edit.playShown(view.flag1, view.flag2);
      });
      cut.setOnClickListener(v -> {
        edit.stop();
        int a = Math.min(view.flag1, view.flag2), b = Math.max(view.flag1, view.flag2);
        if (edit.cutShown(a, b)) {
          view.resetFlags();
          if (edit.position() > a) edit.setPosition(a);
        }
        refresh.run();
      });
      bringBack.setOnClickListener(v -> {
        edit.stop();
        edit.bringBackLast();
        view.resetFlags();
        refresh.run();
      });
      cancel.setOnClickListener(v -> {
        edit.removeListener(listener);
        if (handle[0] != null) handle[0].dismiss();
      });
      ok.setOnClickListener(v -> {
        done[0] = true;
        edit.removeListener(listener);
        org.thunderdog.challegram.Tgx101Diag.mark("voice edit: editor done, " + (edit.keptSamples() * 1000L / Tgx101VoiceEdit.RATE) + " ms kept, " + countCuts(edit) + " cut(s)");
        if (handle[0] != null) handle[0].dismiss();
      });
    }, true);
  }

  private static int countCuts (Tgx101VoiceEdit edit) {
    return edit.seams().length;
  }

  private static TextView text (Context context, CharSequence s, float size, int color, boolean bold) {
    TextView v = new TextView(context);
    v.setText(s);
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
    v.setTextColor(color);
    v.setTypeface(bold ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
    return v;
  }

  // TGx101 (user 2026-11-12 «кнопки в редакторе в стиле материал?» — «да, переведи»): Material 3 buttons — 40 dp, fully
  // rounded, Medium 14 sp with M3's letter spacing, a ripple from the finger; filled (primary), tonal (a 12 % tint of
  // the accent) and outlined kinds; icon buttons round, filled or outlined
  // (user's screenshot 2026-11-12 19:2x «сливаются отмена и кусок»): each kind is told apart by what it is — «Done» filled in
  // the app's primary button colour, «Cancel» a text button with no box, the piece outlined in the flags' red
  static final int M3_FILLED = 0, M3_TONAL = 1, M3_OUTLINED = 2, M3_TEXT = 3, M3_PIECE = 4;

  private static TextView pill (Context context, CharSequence s, int kind) {
    int accent = Theme.getColor(ColorId.circleButtonRegular);
    int textColor = kind == M3_FILLED ? Theme.getColor(ColorId.circleButtonRegularIcon) : kind == M3_TONAL ? Theme.textAccentColor() : kind == M3_PIECE ? RED : Theme.getColor(ColorId.textNeutral);
    TextView v = text(context, s, 14f, textColor, true);
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) v.setLetterSpacing(.007f);
    v.setGravity(Gravity.CENTER);
    v.setPadding(Screen.dp(20f), 0, Screen.dp(20f), 0);
    v.setMinWidth(Screen.dp(64f));
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(Screen.dp(20f));
    if (kind == M3_FILLED) {
      bg.setColor(accent);
    } else if (kind == M3_TONAL) {
      bg.setColor(ColorUtils.alphaColor(.14f, accent));
    } else if (kind == M3_TEXT) {
      bg.setColor(0);
    } else if (kind == M3_PIECE) {
      bg.setColor(ColorUtils.alphaColor(.08f, RED));
      bg.setStroke(Screen.dp(1f), ColorUtils.alphaColor(.7f, RED));
    } else {
      bg.setColor(0);
      bg.setStroke(Screen.dp(1f), ColorUtils.alphaColor(.35f, Theme.textAccentColor()));
    }
    v.setBackground(ripple(bg, kind == M3_FILLED ? 0xffffffff : Theme.textAccentColor(), false));
    v.setClickable(true);
    v.setFocusable(true);
    return v;
  }

  /** {@code oval}: told, not read back — GradientDrawable#getShape is Android 7+ (lint before 0.2.981) */
  private static android.graphics.drawable.Drawable ripple (GradientDrawable bg, int over, boolean oval) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) return bg;
    GradientDrawable mask = new GradientDrawable();
    mask.setShape(oval ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
    mask.setCornerRadius(oval ? 0 : Screen.dp(20f));
    mask.setColor(0xffffffff);
    return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(ColorUtils.alphaColor(.16f, over)), bg, mask);
  }

  private static final float BOX_RADIUS = 14f;

  private static GradientDrawable box (int fill, int stroke) {
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(Screen.dp(BOX_RADIUS));
    bg.setColor(fill);
    if (stroke != 0) bg.setStroke(Screen.dp(1.2f), stroke);
    return bg;
  }

  private static android.graphics.drawable.Drawable boxRipple (int fill, int stroke, int over) {
    GradientDrawable bg = box(fill, stroke);
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) return bg;
    GradientDrawable mask = box(0xffffffff, 0);
    return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(ColorUtils.alphaColor(.16f, over)), bg, mask);
  }

  private static TextView textBox (Context context, CharSequence s, int fill, int stroke, int textColor) {
    TextView v = text(context, s, 15f, textColor, false);
    v.setGravity(Gravity.CENTER);
    v.setSingleLine(true);
    v.setEllipsize(android.text.TextUtils.TruncateAt.END);
    v.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    v.setBackground(boxRipple(fill, stroke, textColor));
    v.setClickable(true);
    v.setFocusable(true);
    return v;
  }

  private static ImageView iconBox (Context context, int icon, int fill, int stroke, int tint) {
    ImageView v = new ImageView(context);
    v.setImageResource(icon);
    v.setColorFilter(tint);
    v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    int p = Screen.dp(10f);
    v.setPadding(p, p, p, p);
    v.setBackground(boxRipple(fill, stroke, tint));
    v.setClickable(true);
    v.setFocusable(true);
    return v;
  }

  private static ImageView round (Context context, int icon, int fill, int tint, float size, boolean outlined) {
    ImageView v = new ImageView(context);
    v.setImageResource(icon);
    v.setColorFilter(tint);
    v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    int p = Screen.dp(size * .26f);
    v.setPadding(p, p, p, p);
    GradientDrawable bg = new GradientDrawable();
    bg.setShape(GradientDrawable.OVAL);
    if (outlined) {
      bg.setColor(0);
      bg.setStroke(Screen.dp(1f), ColorUtils.alphaColor(.35f, Theme.textAccentColor()));
    } else {
      bg.setColor(fill);
    }
    v.setBackground(ripple(bg, outlined ? Theme.textAccentColor() : 0xffffffff, true));
    v.setClickable(true);
    v.setFocusable(true);
    return v;
  }

  /** flags above, the wave with its yellow edges, the listening slider below */
  static final class EditView extends View {
    static final float HEIGHT = 184f;
    private static final float FLAGS = 30f, WAVE_TOP = 34f, WAVE_H = 96f, SLIDER_Y = 162f, EDGE_W = 14f, SIDE = 14f;

    private final Tgx101VoiceEdit edit;
    int flag1, flag2;
    Runnable onChanged;
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    // the bars' peaks, kept until the length, the cuts or the bar count change (a drag redrew them from the recording)
    private float[] peakCache;
    private int peakLen, peakSeams;

    EditView (Context context, Tgx101VoiceEdit edit) {
      super(context);
      this.edit = edit;
      label.setTypeface(Fonts.getRobotoMedium());
      label.setTextSize(Screen.dp(12f));
      label.setColor(0xffffffff);
      small.setTypeface(Fonts.getRobotoRegular());
      small.setTextSize(Screen.dp(12f));
      dash.setStyle(Paint.Style.STROKE);
      dash.setStrokeWidth(Screen.dp(2f));
      dash.setColor(RED);
      dash.setPathEffect(new DashPathEffect(new float[] {Screen.dp(4f), Screen.dp(3f)}, 0));
      resetFlags();
    }

    /** (user 2026-11-12 «по умолчанию нет выделенной красной полосы… один ползунок, начинаешь тянуть — раздваивается»): one
     *  flag in the middle of what is kept, nothing chosen; the one dragged leaves its twin where it stood */
    void resetFlags () {
      int ts = edit.trimStartShown(), te = edit.trimEndShown();
      flag1 = flag2 = ts + (te - ts) / 2;
      invalidate();
    }

    boolean hasPiece () {
      return Math.abs(flag2 - flag1) >= Tgx101VoiceEdit.RATE / 10; // a tenth of a second
    }

    private float left () {
      return Screen.dp(SIDE);
    }

    private float right () {
      return getMeasuredWidth() - Screen.dp(SIDE);
    }

    private float xOf (int shown) {
      return left() + (right() - left()) * shown / (float) Math.max(1, edit.shownLength());
    }

    private int shownAt (float x) {
      return Math.round(Math.max(0f, Math.min(1f, (x - left()) / Math.max(1f, right() - left()))) * edit.shownLength());
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      int w = getMeasuredWidth();
      float waveTop = Screen.dp(WAVE_TOP), waveBottom = waveTop + Screen.dp(WAVE_H);
      int accent = Theme.getColor(ColorId.waveformActive);
      int inactive = Theme.getColor(ColorId.waveformInactive);
      int text = Theme.textAccentColor();

      // the wave's box
      rect.set(0, waveTop, w, waveBottom);
      c.drawRoundRect(rect, Screen.dp(14f), Screen.dp(14f), Paints.fillingPaint(ColorUtils.alphaColor(.07f, text)));

      int len = edit.shownLength();
      int ts = edit.trimStartShown(), te = edit.trimEndShown(), pos = edit.position();
      int a = Math.min(flag1, flag2), b = Math.max(flag1, flag2);
      float xa = xOf(a), xb = xOf(b), xs = xOf(ts), xe = xOf(te);

      c.drawRect(xa, waveTop, xb, waveBottom, Paints.fillingPaint(ColorUtils.alphaColor(.22f, RED)));

      float step = Screen.dp(5f);
      int bars = Math.max(1, (int) ((right() - left()) / step));
      float centerY = (waveTop + waveBottom) / 2f;
      float maxH = Screen.dp(WAVE_H) - Screen.dp(26f), minH = Screen.dp(3f);
      int seamsKey = java.util.Arrays.hashCode(edit.seams());
      if (peakCache == null || peakCache.length != bars || peakLen != len || peakSeams != seamsKey) {
        peakCache = new float[bars];
        for (int i = 0; i < bars; i++) peakCache[i] = edit.peak((int) ((long) len * i / bars), (int) ((long) len * (i + 1) / bars));
        peakLen = len;
        peakSeams = seamsKey;
      }
      for (int i = 0; i < bars; i++) {
        int from = (int) ((long) len * i / bars), to = (int) ((long) len * (i + 1) / bars);
        int mid = (from + to) / 2;
        float p = peakCache[i];
        float bh = Math.max(minH, Math.min(maxH, minH + (float) Math.sqrt(p) * maxH));
        float x = left() + i * step;
        int color;
        if (mid < ts || mid >= te) color = ColorUtils.alphaColor(.3f, inactive);
        else if (mid >= a && mid < b) color = 0xfff08a7c;
        else if (mid < pos) color = accent;
        else color = inactive;
        rect.set(x, centerY - bh / 2f, x + Screen.dp(3f), centerY + bh / 2f);
        c.drawRoundRect(rect, Screen.dp(1.5f), Screen.dp(1.5f), Paints.fillingPaint(color));
      }
      // what the edges cut off, darker
      Paint shade = Paints.fillingPaint(ColorUtils.alphaColor(.35f, 0xff000000));
      if (xs > 0) c.drawRect(0, waveTop, xs, waveBottom, shade);
      if (xe < w) c.drawRect(xe, waveTop, w, waveBottom, shade);
      // the cut seams
      for (int seam : edit.seams()) {
        float x = xOf(seam);
        c.drawLine(x, waveTop + Screen.dp(6f), x, waveBottom - Screen.dp(6f), dash);
      }
      // where listening starts, on the wave
      float xp = xOf(pos);
      c.drawRect(xp - Screen.dp(1f), waveTop + Screen.dp(6f), xp + Screen.dp(1f), waveBottom - Screen.dp(6f), Paints.fillingPaint(ColorUtils.alphaColor(.9f, text)));

      // the yellow edges on the wave
      Paint yellow = Paints.fillingPaint(YELLOW);
      float bw = Screen.dp(3f);
      c.drawRect(xs, waveTop, xe, waveTop + bw, yellow);
      c.drawRect(xs, waveBottom - bw, xe, waveBottom, yellow);
      float ew = Screen.dp(EDGE_W);
      rect.set(xs - ew, waveTop, xs, waveBottom);
      c.drawRoundRect(rect, Screen.dp(7f), Screen.dp(7f), yellow);
      rect.set(xe, waveTop, xe + ew, waveBottom);
      c.drawRoundRect(rect, Screen.dp(7f), Screen.dp(7f), yellow);
      Paint grip = Paints.fillingPaint(0xff5a4610);
      c.drawRect(xs - ew / 2f - Screen.dp(1f), centerY - Screen.dp(13f), xs - ew / 2f + Screen.dp(1f), centerY + Screen.dp(13f), grip);
      c.drawRect(xe + ew / 2f - Screen.dp(1f), centerY - Screen.dp(13f), xe + ew / 2f + Screen.dp(1f), centerY + Screen.dp(13f), grip);

      // the red flags above
      drawFlag(c, xOf(flag1), flag1, waveBottom);
      drawFlag(c, xOf(flag2), flag2, waveBottom);

      // the listening slider below, apart from the edges
      float sy = Screen.dp(SLIDER_Y);
      rect.set(left(), sy - Screen.dp(2f), right(), sy + Screen.dp(2f));
      c.drawRoundRect(rect, Screen.dp(2f), Screen.dp(2f), Paints.fillingPaint(ColorUtils.alphaColor(.15f, text)));
      rect.set(xs, sy - Screen.dp(2f), Math.max(xs, xp), sy + Screen.dp(2f));
      c.drawRoundRect(rect, Screen.dp(2f), Screen.dp(2f), Paints.fillingPaint(accent));
      c.drawCircle(xp, sy, Screen.dp(10f), Paints.fillingPaint(0xffffffff));
      c.drawCircle(xp, sy, Screen.dp(10f), Paints.strokeSmallPaint(ColorUtils.alphaColor(.25f, 0xff000000)));
      String t = Strings.buildDuration(edit.keptBefore(pos) / Tgx101VoiceEdit.RATE);
      small.setColor(Theme.textDecentColor());
      float tw = small.measureText(t);
      float tx = xp + Screen.dp(16f);
      if (tx + tw > w) tx = xp - Screen.dp(16f) - tw;
      c.drawText(t, tx, sy + Screen.dp(4.5f), small);
    }

    private void drawFlag (Canvas c, float x, int shown, float bottom) {
      c.drawRect(x - Screen.dp(1f), 0, x + Screen.dp(1f), bottom, Paints.fillingPaint(RED));
      String t = Strings.buildDuration(shown / Tgx101VoiceEdit.RATE);
      float tw = label.measureText(t);
      boolean toLeft = x + tw + Screen.dp(16f) > getMeasuredWidth();
      float fx = toLeft ? x - tw - Screen.dp(14f) : x;
      rect.set(fx, 0, fx + tw + Screen.dp(14f), Screen.dp(22f));
      c.drawRoundRect(rect, Screen.dp(6f), Screen.dp(6f), Paints.fillingPaint(RED));
      c.drawText(t, fx + Screen.dp(7f), Screen.dp(15.5f), label);
    }

    private static final int NONE = 0, FLAG1 = 1, FLAG2 = 2, EDGE_START = 3, EDGE_END = 4, SLIDER = 5;
    private int dragging;

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      float x = e.getX(), y = e.getY();
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          float waveTop = Screen.dp(WAVE_TOP), waveBottom = waveTop + Screen.dp(WAVE_H);
          float sliderTop = waveBottom + Screen.dp(12f);
          float xs = xOf(edit.trimStartShown()), xe = xOf(edit.trimEndShown());
          float ew = Screen.dp(EDGE_W), grab = Screen.dp(12f);
          if (y >= sliderTop) {
            dragging = SLIDER;
          } else if (y >= waveTop && x >= xs - ew - grab && x <= xs + grab && Math.abs(x - xs) <= Math.abs(x - xe)) {
            dragging = EDGE_START;
          } else if (y >= waveTop && x >= xe - grab && x <= xe + ew + grab) {
            dragging = EDGE_END;
          } else {
            float d1 = Math.abs(x - xOf(flag1)), d2 = Math.abs(x - xOf(flag2));
            dragging = d1 <= d2 ? FLAG1 : FLAG2;
          }
          if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
          edit.stop(); // listening restarts from the new place on ▶, not on every move
          if (dragging == EDGE_START || dragging == EDGE_END) edit.beginEdges();
          move(x);
          return true;
        }
        case MotionEvent.ACTION_MOVE: {
          move(x);
          return true;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          if (dragging == EDGE_START || dragging == EDGE_END) {
            edit.commitEdges();
            if (onChanged != null) onChanged.run();
            org.thunderdog.challegram.Tgx101Diag.mark("voice edit: edges " + (edit.trimStartShown() * 1000L / Tgx101VoiceEdit.RATE) + "–" + (edit.trimEndShown() * 1000L / Tgx101VoiceEdit.RATE) + " ms of " + (edit.shownLength() * 1000L / Tgx101VoiceEdit.RATE));
          }
          dragging = NONE;
          if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
          return true;
      }
      return true;
    }

    private void move (float x) {
      int s = shownAt(x);
      switch (dragging) {
        case FLAG1: flag1 = s; break;
        case FLAG2: flag2 = s; break;
        case EDGE_START:
          edit.setTrimShown(s, edit.trimEndShown());
          edit.setPosition(edit.trimStartShown()); // ▶ then tells how the note begins
          break;
        case EDGE_END:
          edit.setTrimShown(edit.trimStartShown(), s);
          if (edit.position() > edit.trimEndShown()) edit.setPosition(edit.trimStartShown());
          break;
        case SLIDER:
          edit.setPosition(Math.max(edit.trimStartShown(), Math.min(s, edit.trimEndShown())));
          break;
      }
      invalidate();
      if (onChanged != null) onChanged.run();
    }
  }
}
