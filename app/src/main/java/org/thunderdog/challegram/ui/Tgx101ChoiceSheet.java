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

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.List;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.RunnableData;

/**
 * TGx101 (a 4PDA reader 2026-11-14, user «во всех подобных окнах нужен редизайн», mockup «Окна выбора в Material 3»):
 * the old wheel pickers become a bottom sheet — a title and one line of explanation, the values as big pills in a grid
 * (the usual ones at once, the rest under «Other…»), the chosen one in the accent colour, one main button. A swipe down
 * or a tap outside closes it without a change.
 */
public final class Tgx101ChoiceSheet {
  private Tgx101ChoiceSheet () { }

  public interface DoneLabel {
    String labelFor (int index);
  }

  public static final class Builder {
    final Context context;
    CharSequence title, subtitle;
    List<String> labels;
    @Nullable boolean[] usual; // which values show before «Other…»; null — all
    int selected;
    int columns = 3;
    boolean dark; // the photo editor: always the night colours
    RunnableData<Integer> onDone;
    boolean list; // a column of rows with a radio dot (chat auto-delete) instead of the grid
    @Nullable DoneLabel doneLabel;

    public Builder (Context context) {
      this.context = context;
    }

    public Builder title (CharSequence title) { this.title = title; return this; }
    public Builder subtitle (@Nullable CharSequence subtitle) { this.subtitle = subtitle; return this; }
    public Builder options (List<String> labels, int selected) { this.labels = labels; this.selected = selected; return this; }
    public Builder usual (@Nullable boolean[] usual) { this.usual = usual; return this; }
    public Builder columns (int columns) { this.columns = columns; return this; }
    public Builder dark (boolean dark) { this.dark = dark; return this; }
    public Builder onDone (RunnableData<Integer> onDone) { this.onDone = onDone; return this; }
    public Builder list (boolean list) { this.list = list; return this; }
    public Builder doneLabel (@Nullable DoneLabel doneLabel) { this.doneLabel = doneLabel; return this; }

    public void show () {
      Tgx101ChoiceSheet.show(this);
    }
  }

  private static void show (Builder b) {
    final Context context = b.context;
    final boolean dark = b.dark;
    final int surface = dark ? 0xff1f2124 : Theme.fillingColor();
    final int text = dark ? 0xffe9eef2 : Theme.textAccentColor();
    final int decent = dark ? 0xff9aa3ab : Theme.textDecentColor();
    final int accent = dark ? 0xff6ab3f3 : Theme.getColor(ColorId.textNeutral);
    final int onAccent = dark ? 0xff0e1a26 : Theme.fillingColor();

    final PopupLayout popup = new PopupLayout(context);
    // above the status bar like the photo viewer: under it the sheet opened behind the viewer, unseen, and taps went
    // to the viewer (journal 2026-11-15 00:50: the timer tapped four times, four sheets, none seen)
    popup.setOverlayStatusBar(true);
    popup.init(true);
    popup.setNeedRootInsets();
    popup.tgx101AllowWhileBusy = true;

    LinearLayout sheet = new LinearLayout(context);
    sheet.setOrientation(LinearLayout.VERTICAL);
    sheet.setPadding(Screen.dp(20f), Screen.dp(10f), Screen.dp(20f), Screen.dp(20f));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(surface);
    float r = Screen.dp(28f);
    bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
    sheet.setBackground(bg);

    View handle = new View(context);
    GradientDrawable handleBg = new GradientDrawable();
    handleBg.setColor(ColorUtils.alphaColor(.25f, text));
    handleBg.setCornerRadius(Screen.dp(2f));
    handle.setBackground(handleBg);
    LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(Screen.dp(36f), Screen.dp(4f));
    handleParams.gravity = Gravity.CENTER_HORIZONTAL;
    handleParams.bottomMargin = Screen.dp(14f);
    sheet.addView(handle, handleParams);

    TextView title = new TextView(context);
    title.setText(b.title);
    title.setTextColor(text);
    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
    title.setTypeface(Fonts.getRobotoMedium());
    sheet.addView(title);
    if (!TextUtils.isEmpty(b.subtitle)) {
      TextView subtitle = new TextView(context);
      subtitle.setText(b.subtitle);
      subtitle.setTextColor(decent);
      subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
      subtitle.setLineSpacing(0, 1.15f);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      p.topMargin = Screen.dp(4f);
      sheet.addView(subtitle, p);
    }

    final int[] selected = {Math.max(0, Math.min(b.labels.size() - 1, b.selected))};
    final boolean[] showAll = {b.usual == null || !b.usual[selected[0]]};
    final LinearLayout grid = new LinearLayout(context);
    grid.setOrientation(LinearLayout.VERTICAL);
    ScrollView scroll = new ScrollView(context);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.addView(grid);
    LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    scrollParams.topMargin = Screen.dp(16f);
    // on a short screen with every value shown the list gives way and scrolls — it took all the height and pushed
    // «Cancel» / the done button off the sheet
    scrollParams.weight = 1f;
    sheet.addView(scroll, scrollParams);

    final TextView more = new TextView(context);
    more.setText(Lang.getString(R.string.Tgx101ChoiceMore));
    more.setTextColor(accent);
    more.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    more.setGravity(Gravity.CENTER_VERTICAL);
    more.setMinHeight(Screen.dp(44f));
    sheet.addView(more);

    final Runnable[] fill = new Runnable[1];
    final TextView[] doneView = new TextView[1];
    fill[0] = () -> {
      grid.removeAllViews();
      if (b.list) {
        GradientDrawable box = new GradientDrawable();
        box.setColor(ColorUtils.alphaColor(.05f, text));
        box.setCornerRadius(Screen.dp(18f));
        grid.setBackground(box);
        if (android.os.Build.VERSION.SDK_INT >= 21) grid.setClipToOutline(true);
        for (int i = 0; i < b.labels.size(); i++) {
          if (!showAll[0] && b.usual != null && !b.usual[i]) continue;
          final int index = i;
          boolean on = i == selected[0];
          LinearLayout rowView = new LinearLayout(context);
          rowView.setOrientation(LinearLayout.HORIZONTAL);
          rowView.setGravity(Gravity.CENTER_VERTICAL);
          rowView.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
          if (on) rowView.setBackgroundColor(ColorUtils.alphaColor(.12f, accent));
          TextView label = new TextView(context);
          label.setText(b.labels.get(i));
          label.setTextColor(text);
          label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
          rowView.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
          View dot = new View(context);
          GradientDrawable dd = new GradientDrawable();
          dd.setShape(GradientDrawable.OVAL);
          dd.setColor(0);
          dd.setStroke(on ? Screen.dp(6f) : Screen.dp(2f), on ? accent : ColorUtils.alphaColor(.35f, text));
          dot.setBackground(dd);
          rowView.addView(dot, new LinearLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f)));
          rowView.setOnClickListener(v -> {
            selected[0] = index;
            fill[0].run();
          });
          if (grid.getChildCount() > 0) {
            View sep = new View(context);
            sep.setBackgroundColor(ColorUtils.alphaColor(.06f, text));
            grid.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Screen.dp(.5f))));
          }
          grid.addView(rowView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(52f)));
        }
        more.setVisibility(showAll[0] || b.usual == null ? View.GONE : View.VISIBLE);
        if (doneView[0] != null && b.doneLabel != null) doneView[0].setText(b.doneLabel.labelFor(selected[0]));
        return;
      }
      LinearLayout row = null;
      int inRow = 0;
      for (int i = 0; i < b.labels.size(); i++) {
        if (!showAll[0] && b.usual != null && !b.usual[i]) continue;
        if (row == null || inRow == b.columns) {
          row = new LinearLayout(context);
          row.setOrientation(LinearLayout.HORIZONTAL);
          LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
          if (grid.getChildCount() > 0) rp.topMargin = Screen.dp(8f);
          grid.addView(row, rp);
          inRow = 0;
        }
        final int index = i;
        boolean on = i == selected[0];
        TextView pill = new TextView(context);
        pill.setText(b.labels.get(i));
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setEllipsize(TextUtils.TruncateAt.END);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        pill.setTextColor(on ? onAccent : text);
        if (on) pill.setTypeface(Fonts.getRobotoMedium());
        GradientDrawable pb = new GradientDrawable();
        pb.setCornerRadius(Screen.dp(16f));
        if (on) {
          pb.setColor(accent);
        } else {
          pb.setColor(ColorUtils.alphaColor(.05f, text));
          pb.setStroke(Math.max(1, Screen.dp(1f)), ColorUtils.alphaColor(.14f, text));
        }
        pill.setBackground(pb);
        pill.setOnClickListener(v -> {
          selected[0] = index;
          fill[0].run();
        });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (inRow > 0) pp.leftMargin = Screen.dp(8f);
        row.addView(pill, pp);
        inRow++;
      }
      // the last row keeps the grid's widths
      if (row != null) while (inRow < b.columns) {
        row.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f) {{ leftMargin = Screen.dp(8f); }});
        inRow++;
      }
      more.setVisibility(showAll[0] || b.usual == null ? View.GONE : View.VISIBLE);
      if (doneView[0] != null && b.doneLabel != null) doneView[0].setText(b.doneLabel.labelFor(selected[0]));
    };
    more.setOnClickListener(v -> {
      showAll[0] = true;
      fill[0].run();
    });

    // Cancel · Done
    final boolean[] answered = {false};
    LinearLayout buttons = new LinearLayout(context);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    TextView cancel = button(context, Lang.getString(R.string.Cancel), 0, text, ColorUtils.alphaColor(.18f, text));
    cancel.setOnClickListener(v -> popup.hideWindow(true));
    buttons.addView(cancel, new LinearLayout.LayoutParams(0, Screen.dp(48f), 1f));
    TextView done = button(context, b.doneLabel != null ? b.doneLabel.labelFor(selected[0]) : Lang.getString(R.string.Done), accent, onAccent, 0);
    doneView[0] = done;
    done.setOnClickListener(v -> {
      if (answered[0]) return;
      answered[0] = true;
      popup.hideWindow(true);
      Tgx101Diag.mark("choice sheet: «" + b.title + "» → " + b.labels.get(selected[0]));
      if (b.onDone != null) b.onDone.runWithData(selected[0]);
    });
    LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(0, Screen.dp(48f), 1.4f);
    doneParams.leftMargin = Screen.dp(10f);
    buttons.addView(done, doneParams);
    LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    buttonsParams.topMargin = Screen.dp(8f);
    sheet.addView(buttons, buttonsParams);
    fill[0].run();

    // a swipe down closes it
    final float[] downY = {0f};
    sheet.setOnTouchListener((v, e) -> {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downY[0] = e.getRawY();
          return true;
        case MotionEvent.ACTION_MOVE:
          v.setTranslationY(Math.max(0f, e.getRawY() - downY[0]));
          return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          if (v.getTranslationY() > Screen.dp(64f)) popup.hideWindow(true);
          else v.animate().translationY(0f).setDuration(150).start();
          return true;
      }
      return false;
    });

    FrameLayout wrap = new FrameLayout(context);
    ViewController<?> c = UI.getCurrentStackItem(context);
    sheet.setPadding(sheet.getPaddingLeft(), sheet.getPaddingTop(), sheet.getPaddingRight(), sheet.getPaddingBottom() + (c != null ? Tgx101Sheet.tgx101NavigationInset(c) : 0));
    wrap.addView(sheet, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    wrap.setClickable(true);
    wrap.setOnClickListener(v -> popup.hideWindow(true));
    popup.showSimplePopupView(wrap, Screen.dp(420f));
  }

  private static TextView button (Context context, String label, int fill, int textColor, int stroke) {
    TextView b = new TextView(context);
    b.setText(label);
    b.setGravity(Gravity.CENTER);
    b.setTextColor(textColor);
    b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    b.setTypeface(Fonts.getRobotoMedium());
    GradientDrawable d = new GradientDrawable();
    d.setCornerRadius(Screen.dp(24f));
    d.setColor(fill);
    if (stroke != 0) d.setStroke(Math.max(1, Screen.dp(1f)), stroke);
    b.setBackground(d);
    return b;
  }
}
