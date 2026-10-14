package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Tgx101AppIcon;
import org.thunderdog.challegram.tool.UI;

/**
 * TGx101 (user 2026-10-14 «выбор должен быть не списком, а видом иконок, как в Telegram»): MagiX → Icon — the app icons
 * in a row, as they look on the home screen; a ring marks the current one, a tap switches it.
 */
public class Tgx101AppIconPicker extends LinearLayout {
  private static final int[] ICONS = {
    R.mipmap.app_launcher, R.mipmap.tgx101_icon_paper, R.mipmap.tgx101_icon_graphite,
    R.mipmap.tgx101_icon_oled, R.mipmap.tgx101_icon_facets, R.mipmap.tgx101_icon_rainbow
  };

  private final Cell[] cells = new Cell[ICONS.length];
  private final TextView title, hint;

  public Tgx101AppIconPicker (Context context) {
    super(context);
    setOrientation(VERTICAL);
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    title = new TextView(context);
    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    title.setTypeface(Fonts.getRobotoRegular());
    title.setText(Lang.getString(R.string.Tgx101AppIcon));
    title.setPadding(Screen.dp(16f), Screen.dp(14f), Screen.dp(16f), Screen.dp(4f));
    addView(title);

    HorizontalScrollView scroll = new HorizontalScrollView(context);
    scroll.setHorizontalScrollBarEnabled(false);
    scroll.setOverScrollMode(OVER_SCROLL_NEVER);
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(HORIZONTAL);
    row.setPadding(Screen.dp(8f), Screen.dp(8f), Screen.dp(8f), Screen.dp(4f));
    boolean available = Tgx101AppIcon.isAvailable();
    for (int i = 0; i < ICONS.length; i++) {
      final int index = i;
      Cell cell = new Cell(context, ICONS[i], Lang.getString(Tgx101AppIcon.NAMES[i]));
      cell.setEnabled(available || i == 0);
      cell.setOnClickListener(v -> choose(index));
      cells[i] = cell;
      row.addView(cell);
    }
    scroll.addView(row);
    addView(scroll);

    hint = new TextView(context);
    hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    hint.setTypeface(Fonts.getRobotoRegular());
    hint.setText(Lang.getString(available ? R.string.Tgx101AppIconHint : R.string.Tgx101AppIconOld));
    hint.setPadding(Screen.dp(16f), Screen.dp(2f), Screen.dp(16f), Screen.dp(14f));
    addView(hint);

    updateColors();
    update();
  }

  private void choose (int index) {
    if (!Tgx101AppIcon.isAvailable() || index == Tgx101AppIcon.current(getContext())) return;
    Tgx101AppIcon.apply(getContext(), index);
    org.thunderdog.challegram.Tgx101Diag.mark("app icon: " + index);
    update();
    UI.showToast(R.string.Tgx101AppIconChanged, android.widget.Toast.LENGTH_SHORT);
  }

  /** The ring follows what the system has enabled now */
  public void update () {
    int current = Tgx101AppIcon.current(getContext());
    for (int i = 0; i < cells.length; i++) cells[i].setChecked(i == current);
  }

  public void updateColors () {
    setBackgroundColor(Theme.fillingColor());
    title.setTextColor(Theme.textAccentColor());
    hint.setTextColor(Theme.textDecentColor());
    for (Cell cell : cells) cell.updateColors();
  }

  private static class Cell extends LinearLayout {
    private final FrameLayout frame;
    private final View icon;
    private final TextView label;
    private boolean checked;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

    Cell (Context context, int iconRes, String name) {
      super(context);
      setOrientation(VERTICAL);
      setGravity(Gravity.CENTER_HORIZONTAL);
      setPadding(Screen.dp(6f), Screen.dp(4f), Screen.dp(6f), Screen.dp(4f));
      setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(96f), ViewGroup.LayoutParams.WRAP_CONTENT));

      frame = new FrameLayout(context) {
        @Override
        protected void dispatchDraw (Canvas c) {
          super.dispatchDraw(c);
          if (checked) {
            float r = getWidth() / 2f - Screen.dp(1.5f);
            c.drawCircle(getWidth() / 2f, getHeight() / 2f, r, ring);
          }
        }
      };
      frame.setWillNotDraw(false);
      int size = Screen.dp(68f);
      frame.setLayoutParams(new LinearLayout.LayoutParams(size, size));

      icon = new View(context);
      Drawable d = context.getResources().getDrawable(iconRes, null);
      icon.setBackground(d);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        icon.setOutlineProvider(new ViewOutlineProvider() {
          @Override
          public void getOutline (View view, Outline outline) {
            outline.setOval(0, 0, view.getWidth(), view.getHeight());
          }
        });
        icon.setClipToOutline(true);
      }
      int iconSize = Screen.dp(56f);
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
      icon.setLayoutParams(lp);
      frame.addView(icon);
      addView(frame);

      label = new TextView(context);
      label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
      label.setTypeface(Fonts.getRobotoRegular());
      label.setGravity(Gravity.CENTER_HORIZONTAL);
      label.setMaxLines(2);
      // (user's screenshot 20:35: «Стандартна / я») — lines break between words only, never inside one
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        label.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        label.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
      }
      label.setText(name);
      label.setPadding(0, Screen.dp(6f), 0, 0);
      addView(label);

      ring.setStyle(Paint.Style.STROKE);
      ring.setStrokeWidth(Screen.dp(2.5f));
    }

    void setChecked (boolean checked) {
      if (this.checked != checked) {
        this.checked = checked;
        frame.invalidate();
      }
      label.setTypeface(checked ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
      label.setTextColor(checked ? Theme.getColor(ColorId.checkActive) : Theme.textAccentColor());
    }

    @Override
    public void setEnabled (boolean enabled) {
      super.setEnabled(enabled);
      setAlpha(enabled ? 1f : .4f);
    }

    void updateColors () {
      ring.setColor(Theme.getColor(ColorId.checkActive));
      label.setTextColor(Theme.textAccentColor());
      frame.invalidate();
    }
  }
}
