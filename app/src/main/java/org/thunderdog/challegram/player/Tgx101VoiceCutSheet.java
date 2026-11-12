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
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
 * TGx101 (user 2026-11-12, mockup «Голосовое: прослушка и обрезка», variant Б): the scissors' editor — two red flags on
 * the note's wave, the piece between them can be listened to and cut out; «Bring back» returns the last cut piece;
 * «Cancel» returns the note as it was when the editor opened, «Done» keeps the cuts.
 */
public final class Tgx101VoiceCutSheet {
  private Tgx101VoiceCutSheet () { }

  private static final int RED = 0xffe5533f;

  public static void show (ViewController<?> c, Tgx101VoiceEdit edit, Runnable onClose) {
    final Object snapshot = edit.snapshot();
    final boolean[] done = {false};
    final Context context = c.context();
    final Tgx101Sheet.Handle[] handle = new Tgx101Sheet.Handle[1];
    handle[0] = Tgx101Sheet.show(c, () -> {
      edit.stop();
      if (!done[0]) edit.restore(snapshot);
      onClose.run();
    }, body -> {
      int pad = Screen.dp(18f);

      LinearLayout header = new LinearLayout(context);
      header.setOrientation(LinearLayout.HORIZONTAL);
      header.setGravity(Gravity.CENTER_VERTICAL);
      header.setPadding(pad, Screen.dp(6f), pad, 0);
      TextView title = text(context, Lang.getString(R.string.Tgx101VoiceCutTitle), 18f, Theme.textAccentColor(), true);
      header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      TextView lengths = text(context, "", 14f, Theme.textDecentColor(), false);
      header.addView(lengths);
      body.addView(header);

      TextView hint = text(context, Lang.getString(R.string.Tgx101VoiceCutHint), 14f, Theme.textDecentColor(), false);
      hint.setPadding(pad, Screen.dp(6f), pad, Screen.dp(4f));
      body.addView(hint);

      final CutView cut = new CutView(context, edit);
      LinearLayout.LayoutParams cutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(124f));
      cutParams.leftMargin = cutParams.rightMargin = pad;
      cutParams.topMargin = Screen.dp(6f);
      body.addView(cut, cutParams);

      LinearLayout actions = new LinearLayout(context);
      actions.setOrientation(LinearLayout.HORIZONTAL);
      actions.setGravity(Gravity.CENTER_VERTICAL);
      actions.setPadding(pad, Screen.dp(16f), pad, 0);
      final TextView playAll = pill(context, "▶", Theme.getColor(ColorId.waveformActive), 0xffffffff, true);
      playAll.setContentDescription(Lang.getString(R.string.Tgx101VoiceCutPlayAll));
      actions.addView(playAll, new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));
      final TextView playPiece = pill(context, "", 0, Theme.textAccentColor(), false);
      LinearLayout.LayoutParams pieceParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(42f));
      pieceParams.leftMargin = Screen.dp(10f);
      actions.addView(playPiece, pieceParams);
      final TextView cutButton = pill(context, "✂  " + Lang.getString(R.string.Tgx101VoiceCutDo), RED, 0xffffffff, true);
      LinearLayout.LayoutParams cutButtonParams = new LinearLayout.LayoutParams(0, Screen.dp(44f), 1f);
      cutButtonParams.leftMargin = Screen.dp(10f);
      actions.addView(cutButton, cutButtonParams);
      body.addView(actions);

      final TextView bringBack = pill(context, "↶  " + Lang.getString(R.string.Tgx101VoiceCutBringBack), 0, Theme.textAccentColor(), false);
      LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(44f));
      backParams.leftMargin = backParams.rightMargin = pad;
      backParams.topMargin = Screen.dp(10f);
      body.addView(bringBack, backParams);

      LinearLayout bottom = new LinearLayout(context);
      bottom.setOrientation(LinearLayout.HORIZONTAL);
      bottom.setPadding(pad, Screen.dp(16f), pad, Screen.dp(4f));
      TextView cancel = pill(context, Lang.getString(R.string.Cancel), ColorUtils.alphaColor(.08f, Theme.textAccentColor()), Theme.textAccentColor(), false);
      bottom.addView(cancel, new LinearLayout.LayoutParams(0, Screen.dp(48f), 1f));
      TextView ok = pill(context, Lang.getString(R.string.Tgx101VoiceCutDone), Theme.getColor(ColorId.waveformActive), 0xffffffff, true);
      LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(0, Screen.dp(48f), 1f);
      okParams.leftMargin = Screen.dp(10f);
      bottom.addView(ok, okParams);
      body.addView(bottom);

      final Runnable refresh = () -> {
        int was = Math.round(edit.samples / (float) Tgx101VoiceEdit.RATE);
        int now = Math.max(1, Math.round(edit.keptSamples() / (float) Tgx101VoiceEdit.RATE));
        lengths.setText(edit.canBringBack() || was != now
          ? Lang.getString(R.string.Tgx101VoiceCutLengths, Strings.buildDuration(was), Strings.buildDuration(now))
          : Strings.buildDuration(was));
        int piece = Math.abs(cut.flag2 - cut.flag1);
        playPiece.setText("▶  " + Lang.getString(R.string.Tgx101VoiceCutPiece, Math.max(1, Math.round(piece / (float) Tgx101VoiceEdit.RATE))));
        playAll.setText(edit.isPlaying() ? "❚❚" : "▶");
        bringBack.setVisibility(edit.canBringBack() ? View.VISIBLE : View.GONE);
        cut.invalidate();
      };
      cut.onChanged = refresh;
      final Tgx101VoiceEdit.Listener listener = refresh::run;
      edit.addListener(listener);
      refresh.run();

      playAll.setOnClickListener(v -> {
        if (edit.isPlaying()) edit.pause();
        else edit.playAllShown();
      });
      playPiece.setOnClickListener(v -> {
        if (edit.isPlaying()) edit.pause();
        else edit.playShown(cut.flag1, cut.flag2);
      });
      cutButton.setOnClickListener(v -> {
        edit.stop();
        int a = Math.min(cut.flag1, cut.flag2), b = Math.max(cut.flag1, cut.flag2);
        if (edit.cutShown(a, b)) {
          cut.resetFlags();
          edit.setPosition(a);
        }
        refresh.run();
      });
      bringBack.setOnClickListener(v -> {
        edit.stop();
        edit.bringBackLast();
        cut.resetFlags();
        refresh.run();
      });
      cancel.setOnClickListener(v -> {
        edit.removeListener(listener);
        if (handle[0] != null) handle[0].dismiss();
      });
      ok.setOnClickListener(v -> {
        done[0] = true;
        edit.removeListener(listener);
        org.thunderdog.challegram.Tgx101Diag.mark("voice edit: editor done, " + (edit.keptSamples() * 1000L / Tgx101VoiceEdit.RATE) + " ms kept");
        if (handle[0] != null) handle[0].dismiss();
      });
    });
  }

  private static TextView text (Context context, CharSequence s, float size, int color, boolean bold) {
    TextView v = new TextView(context);
    v.setText(s);
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
    v.setTextColor(color);
    v.setTypeface(bold ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
    return v;
  }

  private static TextView pill (Context context, CharSequence s, int fill, int textColor, boolean bold) {
    TextView v = text(context, s, 15f, textColor, bold);
    v.setGravity(Gravity.CENTER);
    v.setPadding(Screen.dp(14f), 0, Screen.dp(14f), 0);
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(Screen.dp(24f));
    if (fill != 0) {
      bg.setColor(fill);
    } else {
      bg.setColor(0);
      bg.setStroke(Screen.dp(1f), ColorUtils.alphaColor(.25f, Theme.textAccentColor()));
    }
    v.setBackground(bg);
    v.setClickable(true);
    v.setFocusable(true);
    return v;
  }

  /** the big wave with the two red flags */
  static final class CutView extends View {
    private final Tgx101VoiceEdit edit;
    int flag1, flag2;
    Runnable onChanged;
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    CutView (Context context, Tgx101VoiceEdit edit) {
      super(context);
      this.edit = edit;
      label.setTypeface(Fonts.getRobotoMedium());
      label.setTextSize(Screen.dp(12f));
      label.setColor(0xffffffff);
      resetFlags();
    }

    void resetFlags () {
      int len = edit.shownLength();
      flag1 = len * 35 / 100;
      flag2 = len * 60 / 100;
      invalidate();
    }

    private float top () {
      return Screen.dp(28f);
    }

    private float xOf (int shown) {
      float l = Screen.dp(12f), r = getMeasuredWidth() - Screen.dp(12f);
      return l + (r - l) * shown / (float) Math.max(1, edit.shownLength());
    }

    private int shownAt (float x) {
      float l = Screen.dp(12f), r = getMeasuredWidth() - Screen.dp(12f);
      return Math.round(Math.max(0f, Math.min(1f, (x - l) / Math.max(1f, r - l))) * edit.shownLength());
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      int w = getMeasuredWidth(), h = getMeasuredHeight();
      float top = top();
      int accent = Theme.getColor(ColorId.waveformActive);
      int inactive = Theme.getColor(ColorId.waveformInactive);
      rect.set(0, top, w, h);
      c.drawRoundRect(rect, Screen.dp(14f), Screen.dp(14f), Paints.fillingPaint(ColorUtils.alphaColor(.06f, Theme.textAccentColor())));

      int a = Math.min(flag1, flag2), b = Math.max(flag1, flag2);
      float xa = xOf(a), xb = xOf(b);
      c.drawRect(xa, top, xb, h, Paints.fillingPaint(ColorUtils.alphaColor(.22f, RED)));

      int len = edit.shownLength();
      int ts = edit.trimStartShown(), te = edit.trimEndShown(), pos = edit.position();
      float l = Screen.dp(12f), r = w - Screen.dp(12f);
      float step = Screen.dp(5f);
      int bars = Math.max(1, (int) ((r - l) / step));
      float centerY = (top + h) / 2f;
      float maxH = (h - top) - Screen.dp(28f), minH = Screen.dp(3f);
      for (int i = 0; i < bars; i++) {
        int from = (int) ((long) len * i / bars), to = (int) ((long) len * (i + 1) / bars);
        int mid = (from + to) / 2;
        float p = edit.peak(from, to);
        float bh = Math.max(minH, Math.min(maxH, minH + (float) Math.sqrt(p) * maxH));
        float x = l + i * step;
        int color;
        if (mid >= a && mid < b) color = 0xfff08a7c;
        else if (mid < ts || mid >= te) color = ColorUtils.alphaColor(.35f, inactive);
        else if (edit.isPlaying() && mid < pos) color = accent;
        else color = inactive;
        rect.set(x, centerY - bh / 2f, x + Screen.dp(3f), centerY + bh / 2f);
        c.drawRoundRect(rect, Screen.dp(1.5f), Screen.dp(1.5f), Paints.fillingPaint(color));
      }
      // cut seams
      for (int seam : edit.seams()) {
        float x = xOf(seam);
        c.drawRect(x - Screen.dp(1f), top + Screen.dp(4f), x + Screen.dp(1f), h - Screen.dp(4f), Paints.fillingPaint(RED));
      }
      // cursor
      if (edit.isPlaying()) {
        float x = xOf(pos);
        c.drawRect(x - Screen.dp(1f), top - Screen.dp(2f), x + Screen.dp(1f), h, Paints.fillingPaint(Theme.textAccentColor()));
      }
      drawFlag(c, xOf(flag1), flag1, h);
      drawFlag(c, xOf(flag2), flag2, h);
    }

    private void drawFlag (Canvas c, float x, int shown, int h) {
      c.drawRect(x - Screen.dp(1f), 0, x + Screen.dp(1f), h, Paints.fillingPaint(RED));
      String t = Strings.buildDuration(shown / Tgx101VoiceEdit.RATE);
      float tw = label.measureText(t);
      boolean left = x + tw + Screen.dp(16f) > getMeasuredWidth();
      float fx = left ? x - tw - Screen.dp(14f) : x;
      rect.set(fx, 0, fx + tw + Screen.dp(14f), Screen.dp(22f));
      c.drawRoundRect(rect, Screen.dp(6f), Screen.dp(6f), Paints.fillingPaint(RED));
      c.drawText(t, fx + Screen.dp(7f), Screen.dp(15.5f), label);
    }

    private int dragging; // 0 none, 1 or 2 a flag

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      float x = e.getX();
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          float d1 = Math.abs(x - xOf(flag1)), d2 = Math.abs(x - xOf(flag2));
          dragging = d1 <= d2 ? 1 : 2;
          if (Math.min(d1, d2) > Screen.dp(40f)) {
            // a tap far from both flags moves the nearer one there
            if (dragging == 1) flag1 = shownAt(x);
            else flag2 = shownAt(x);
          }
          if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
          edit.stop();
          changed();
          return true;
        }
        case MotionEvent.ACTION_MOVE: {
          if (dragging == 1) flag1 = shownAt(x);
          else if (dragging == 2) flag2 = shownAt(x);
          changed();
          return true;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          dragging = 0;
          if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
          return true;
      }
      return true;
    }

    private void changed () {
      invalidate();
      if (onChanged != null) onChanged.run();
    }
  }
}
