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
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewController.OptionColor;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

/**
 * TGx101 «Вид Газеты» (user 2026-10-28, mockup approved): a live sample post on top that changes as a slider moves,
 * ☀/☾ — the two profiles, each with its own values; text (size — the app's by default, line spacing, typeface), the
 * posts' colour, pictures (quick filters, brightness, saturation), long posts (9 lines / «Short»), autoscroll speed.
 */
public class Tgx101GazetaLookController extends ViewController<Void> implements org.thunderdog.challegram.navigation.Menu {
  private final @Nullable Tgx101GazetaController feed;

  public Tgx101GazetaLookController (Context context, Tdlib tdlib, @Nullable Tgx101GazetaController feed) {
    super(context, tdlib);
    this.feed = feed;
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101GazetaLook;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101GazetaLook);
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_tgx101GazetaLook;
  }

  @Override
  public void fillMenuItems (int id, org.thunderdog.challegram.navigation.HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_tgx101GazetaLook) {
      header.addButton(menu, R.id.menu_btn_tgx101GazetaReset, R.drawable.baseline_replay_24, 49f, this, getHeaderIconColorId()).tgx101SetForcedColor(Tgx101Gazeta.palette()[2]);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_tgx101GazetaReset) {
      showOptions(Lang.getString(R.string.Tgx101GazetaResetInfo), new int[] {1, 2},
        new String[] {Lang.getString(R.string.Tgx101GazetaReset), Lang.getString(R.string.Cancel)},
        new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_replay_24, R.drawable.baseline_cancel_24}, (v, option) -> {
          if (option == 1) {
            Tgx101Gazeta.resetLook();
            rebuild();
          }
          return true;
        });
    }
  }

  @Override
  protected int tgx101HeaderColor () {
    return Tgx101Gazeta.palette()[1];
  }

  @Override
  protected int tgx101HeaderTextColor () {
    return Tgx101Gazeta.palette()[2];
  }

  @Override
  protected int getHeaderIconColor () {
    return Tgx101Gazeta.palette()[2];
  }

  private LinearLayout page, settings;
  private FrameLayout previewWrap;
  private @Nullable View preview;
  private LinearLayout segment;

  @Override
  protected View onCreateView (Context context) {
    page = new LinearLayout(context);
    page.setOrientation(LinearLayout.VERTICAL);

    segment = new LinearLayout(context);
    segment.setOrientation(LinearLayout.HORIZONTAL);
    segment.setPadding(Screen.dp(3f), Screen.dp(3f), Screen.dp(3f), Screen.dp(3f));
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(40f));
    sp.setMargins(Screen.dp(14f), Screen.dp(10f), Screen.dp(14f), Screen.dp(10f));
    page.addView(segment, sp);

    previewWrap = new FrameLayout(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        // the sample keeps to a part of the screen; the settings scroll under it
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((int) (Screen.currentHeight() * .42f), MeasureSpec.AT_MOST));
      }
    };
    LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    pp.setMargins(Screen.dp(14f), 0, Screen.dp(14f), 0);
    page.addView(previewWrap, pp);
    preview = feed != null ? feed.createPreview(context) : null;
    if (preview != null) {
      previewWrap.addView(preview);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) previewWrap.setClipToOutline(true); // 5.0+ only
    }

    ScrollView scroll = new ScrollView(context);
    settings = new LinearLayout(context);
    settings.setOrientation(LinearLayout.VERTICAL);
    settings.setPadding(Screen.dp(14f), Screen.dp(4f), Screen.dp(14f), Screen.dp(24f));
    scroll.addView(settings, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    rebuild();
    return page;
  }

  private int[] pal () {
    return Tgx101Gazeta.palette();
  }

  private @Nullable View pictureSample;

  private void refresh () {
    if (preview != null && feed != null) feed.bindPreview(preview);
    if (pictureSample != null) pictureSample.invalidate();
    int[] pal = pal();
    page.setBackgroundColor(pal[0]);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      GradientDrawable round = new GradientDrawable();
      round.setCornerRadius(Screen.dp(16f));
      round.setColor(pal[1]);
      previewWrap.setBackground(round);
    }
    if (context().navigation() != null && context().navigation().getHeaderView() != null && isFocused()) {
      context().navigation().getHeaderView().resetColors(this, null);
    }
  }

  private TextView label (Context context, String text, float sp, boolean medium, int color) {
    TextView v = new TextView(context);
    v.setText(text);
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
    v.setTypeface(medium ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
    v.setTextColor(color);
    return v;
  }

  private LinearLayout section (Context context, int titleRes) {
    int[] pal = pal();
    TextView h = label(context, Lang.getString(titleRes).toUpperCase(), 12f, true, me.vkryl.core.ColorUtils.alphaColor(.6f, pal[2]));
    h.setPadding(Screen.dp(2f), Screen.dp(16f), 0, Screen.dp(6f));
    settings.addView(h);
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(Screen.dp(12f), Screen.dp(4f), Screen.dp(12f), Screen.dp(12f));
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(Screen.dp(14f));
    bg.setColor(pal[1]);
    card.setBackground(bg);
    settings.addView(card);
    return card;
  }

  private interface SliderChange {
    void onChange (int value);
  }

  private interface ValueName {
    String name (int value);
  }

  private void slider (LinearLayout card, int titleRes, int min, int max, int value, ValueName name, SliderChange change) {
    Context context = card.getContext();
    int[] pal = pal();
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(Screen.dp(2f), Screen.dp(12f), Screen.dp(2f), Screen.dp(2f));
    row.addView(label(context, Lang.getString(titleRes), 14f, false, pal[2]), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    TextView valueView = label(context, name.name(value), 14f, false, pal[3]);
    row.addView(valueView);
    card.addView(row);
    SeekBar bar = new SeekBar(context);
    bar.setMax(max - min);
    bar.setProgress(value - min);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      bar.setProgressTintList(ColorStateList.valueOf(pal[4]));
      bar.setThumbTintList(ColorStateList.valueOf(pal[4]));
    }
    bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged (SeekBar seekBar, int progress, boolean fromUser) {
        if (!fromUser) return;
        valueView.setText(name.name(progress + min));
        change.onChange(progress + min);
        refresh();
      }

      @Override
      public void onStartTrackingTouch (SeekBar seekBar) { }

      @Override
      public void onStopTrackingTouch (SeekBar seekBar) { }
    });
    card.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(36f)));
  }

  private View chip (Context context, String text, boolean on, View.OnClickListener click) {
    int[] pal = pal();
    TextView v = label(context, text, 13f, false, on ? 0xffffffff : pal[2]);
    v.setGravity(Gravity.CENTER);
    v.setPadding(Screen.dp(12f), Screen.dp(7f), Screen.dp(12f), Screen.dp(7f));
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(Screen.dp(16f));
    bg.setColor(on ? pal[4] : 0);
    if (!on) bg.setStroke(Math.max(1, Screen.dp(1f)), me.vkryl.core.ColorUtils.alphaColor(.4f, pal[3]));
    v.setBackground(bg);
    v.setOnClickListener(click);
    return v;
  }

  private LinearLayout chipRow (Context context) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, Screen.dp(10f), 0, 0);
    return row;
  }

  private void addChip (LinearLayout row, View chip) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.rightMargin = Screen.dp(7f);
    row.addView(chip, lp);
  }

  private void segmentButtons (LinearLayout box, String[] names, int selected, SliderChange pick) {
    int[] pal = pal();
    box.removeAllViews();
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(Screen.dp(12f));
    bg.setColor(box == segment ? pal[1] : pal[0]);
    box.setBackground(bg);
    for (int i = 0; i < names.length; i++) {
      final int index = i;
      boolean on = i == selected;
      TextView b = label(box.getContext(), names[i], 13f, true, on ? 0xffffffff : pal[2]);
      b.setGravity(Gravity.CENTER);
      GradientDrawable fill = new GradientDrawable();
      fill.setCornerRadius(Screen.dp(9f));
      fill.setColor(on ? pal[4] : 0);
      b.setBackground(fill);
      b.setOnClickListener(v -> pick.onChange(index));
      box.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }
  }

  private void rebuild () {
    Context context = context();
    settings.removeAllViews();
    segmentButtons(segment, new String[] {"☀ " + Lang.getString(R.string.Tgx101GazetaLight), "☾ " + Lang.getString(R.string.Tgx101GazetaDark)},
      Tgx101Gazeta.profile(), which -> {
        Tgx101Gazeta.setProfile(which == 1 ? Tgx101Gazeta.PROFILE_DARK : Tgx101Gazeta.PROFILE_LIGHT);
        rebuild();
      });

    // own profiles: a tap puts one on the profile now on (☀ or ☾), a long press deletes it; «+» saves the look now on
    LinearLayout own = section(context, R.string.Tgx101GazetaOwnLooks);
    android.widget.HorizontalScrollView ownScroll = new android.widget.HorizontalScrollView(context);
    ownScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout ownRow = chipRow(context);
    for (String name : Tgx101Gazeta.savedLooks()) {
      View c = chip(context, name, false, v -> {
        Tgx101Gazeta.applyLook(name);
        rebuild();
      });
      c.setOnLongClickListener(v -> {
        showOptions(name, new int[] {1, 2}, new String[] {Lang.getString(R.string.Tgx101GazetaLookOverwrite), Lang.getString(R.string.Delete)}, new int[] {OptionColor.NORMAL, OptionColor.RED}, new int[] {R.drawable.baseline_edit_24, R.drawable.baseline_delete_24}, (view, id) -> {
          if (id == 1) Tgx101Gazeta.saveLook(name);
          else Tgx101Gazeta.deleteLook(name);
          rebuild();
          return true;
        });
        return true;
      });
      addChip(ownRow, c);
    }
    addChip(ownRow, chip(context, "+ " + Lang.getString(R.string.Tgx101GazetaLookSave), false, v ->
      Tgx101SavedActions.askName(this, Lang.getString(R.string.Tgx101GazetaLookSave), null, name -> {
        Tgx101Gazeta.saveLook(name);
        rebuild();
      })));
    ownScroll.addView(ownRow);
    own.addView(ownScroll);
    TextView ownHint = label(context, Lang.getString(R.string.Tgx101GazetaOwnLooksHint), 12.5f, false, pal()[3]);
    ownHint.setPadding(Screen.dp(2f), Screen.dp(8f), Screen.dp(2f), 0);
    own.addView(ownHint);

    // text
    LinearLayout text = section(context, R.string.Tgx101GazetaText);
    float appSize = org.thunderdog.challegram.unsorted.Settings.instance().getChatFontSize();
    int size = Math.round(Tgx101Gazeta.fontSize());
    slider(text, R.string.Tgx101GazetaFont, 12, 24, size,
      v -> v == Math.round(appSize) && Tgx101Gazeta.ownFontSize() == 0f ? Lang.getString(R.string.Tgx101GazetaAsInApp) : v + " sp",
      v -> Tgx101Gazeta.setFontSize(v == Math.round(appSize) ? 0f : v));
    slider(text, R.string.Tgx101GazetaSpacing, 10, 20, Tgx101Gazeta.lineSpacing(),
      v -> String.format(java.util.Locale.getDefault(), "%.1f", v / 10f), Tgx101Gazeta::setLineSpacing);
    LinearLayout fonts = chipRow(context);
    int[] fontNames = {R.string.Tgx101GazetaFontRegular, R.string.Tgx101GazetaFontSerif, R.string.Tgx101GazetaFontNarrow};
    for (int i = 0; i < fontNames.length; i++) {
      final int font = i;
      addChip(fonts, chip(context, Lang.getString(fontNames[i]), Tgx101Gazeta.font() == i, v -> {
        Tgx101Gazeta.setFont(font);
        rebuild();
      }));
    }
    text.addView(fonts);

    // colour of the posts
    LinearLayout colors = section(context, R.string.Tgx101GazetaColor);
    LinearLayout swatches = new LinearLayout(context);
    swatches.setOrientation(LinearLayout.HORIZONTAL);
    swatches.setPadding(0, Screen.dp(10f), 0, 0);
    int[][] palettes = Tgx101Gazeta.profile() == Tgx101Gazeta.PROFILE_DARK ? Tgx101Gazeta.DARK_PALETTES : Tgx101Gazeta.LIGHT_PALETTES;
    for (int i = 0; i < palettes.length; i++) {
      final int index = i;
      View swatch = new View(context);
      GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {palettes[i][1], palettes[i][1], palettes[i][0], palettes[i][0]});
      g.setShape(GradientDrawable.OVAL);
      boolean on = Tgx101Gazeta.paletteIndex() == i;
      g.setStroke(Screen.dp(on ? 3f : 1f), on ? pal()[4] : me.vkryl.core.ColorUtils.alphaColor(.35f, pal()[3]));
      swatch.setBackground(g);
      swatch.setOnClickListener(v -> {
        Tgx101Gazeta.setPaletteIndex(index);
        rebuild();
      });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Screen.dp(38f), Screen.dp(38f));
      lp.rightMargin = Screen.dp(12f);
      swatches.addView(swatch, lp);
    }
    colors.addView(swatches);

    // pictures
    LinearLayout pics = section(context, R.string.Tgx101GazetaPicture);
    // a colourful sample that changes with the filters and sliders at once
    pictureSample = new View(context) {
      private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
      private final android.graphics.RectF r = new android.graphics.RectF();

      @Override
      protected void onDraw (android.graphics.Canvas c) {
        int w = getWidth(), h = getHeight();
        paint.setShader(new android.graphics.LinearGradient(0, 0, w, 0,
          new int[] {0xffff5252, 0xffffb300, 0xffffeb3b, 0xff4caf50, 0xff00bcd4, 0xff3d5afe, 0xffaa00ff, 0xffff4081}, null, android.graphics.Shader.TileMode.CLAMP));
        paint.setColorFilter(Tgx101Gazeta.pictureFilter());
        r.set(0, 0, w, h);
        c.drawRoundRect(r, Screen.dp(10f), Screen.dp(10f), paint);
        paint.setShader(new android.graphics.LinearGradient(0, 0, 0, h, new int[] {0x00ffffff, 0x00ffffff, 0x99000000}, new float[] {0f, .45f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        c.drawRoundRect(r, Screen.dp(10f), Screen.dp(10f), paint);
        paint.setShader(null);
        // faces of colour: skin, sky, leaf — what pictures usually hold
        int[] spots = {0xfff1c27d, 0xff87ceeb, 0xff2e7d32, 0xffffffff, 0xff212121};
        float d = h * .34f;
        for (int i = 0; i < spots.length; i++) {
          paint.setColor(spots[i]);
          c.drawCircle(w * (i + 1) / (spots.length + 1f), h * .66f, d / 2f, paint);
        }
        paint.setColorFilter(null);
      }
    };
    LinearLayout.LayoutParams psp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(64f));
    psp.topMargin = Screen.dp(10f);
    pics.addView(pictureSample, psp);
    LinearLayout photos = chipRow(context);
    addChip(photos, chip(context, Lang.getString(R.string.Tgx101GazetaPhotosAsIs), Tgx101Gazeta.photos() == Tgx101Gazeta.PHOTOS_AS_IS, v -> {
      Tgx101Gazeta.setPhotos(Tgx101Gazeta.PHOTOS_AS_IS);
      rebuild();
    }));
    addChip(photos, chip(context, Lang.getString(R.string.Tgx101GazetaPhotosThumb), Tgx101Gazeta.photos() == Tgx101Gazeta.PHOTOS_THUMB, v -> {
      Tgx101Gazeta.setPhotos(Tgx101Gazeta.PHOTOS_THUMB);
      rebuild();
    }));
    pics.addView(photos);
    android.widget.HorizontalScrollView filterScroll = new android.widget.HorizontalScrollView(context);
    filterScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout filters = chipRow(context);
    int[][] presets = {{100, 100, 0}, {80, 75, 0}, {100, 0, 0}, {105, 140, 0}, {95, 80, 1}};
    int[] presetNames = {R.string.Tgx101GazetaPicAsIs, R.string.Tgx101GazetaPicNight, R.string.Tgx101GazetaPicBw, R.string.Tgx101GazetaPicJuicy, R.string.Tgx101GazetaPicSepia};
    for (int i = 0; i < presets.length; i++) {
      final int[] preset = presets[i];
      boolean on = Tgx101Gazeta.brightness() == preset[0] && Tgx101Gazeta.saturation() == preset[1] && Tgx101Gazeta.sepia() == (preset[2] == 1);
      addChip(filters, chip(context, Lang.getString(presetNames[i]), on, v -> {
        Tgx101Gazeta.setPicture(preset[0], preset[1], preset[2] == 1);
        rebuild();
      }));
    }
    filterScroll.addView(filters);
    pics.addView(filterScroll);
    slider(pics, R.string.Tgx101GazetaBrightness, 20, 120, Tgx101Gazeta.brightness(), v -> v + "%",
      v -> Tgx101Gazeta.setPicture(v, Tgx101Gazeta.saturation(), Tgx101Gazeta.sepia()));
    slider(pics, R.string.Tgx101GazetaSaturation, 0, 150, Tgx101Gazeta.saturation(), v -> v + "%",
      v -> Tgx101Gazeta.setPicture(Tgx101Gazeta.brightness(), v, Tgx101Gazeta.sepia()));

    // long posts
    LinearLayout fold = section(context, R.string.Tgx101GazetaFold);
    LinearLayout foldSegment = new LinearLayout(context);
    foldSegment.setOrientation(LinearLayout.HORIZONTAL);
    foldSegment.setPadding(Screen.dp(3f), Screen.dp(3f), Screen.dp(3f), Screen.dp(3f));
    LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(40f));
    fp.topMargin = Screen.dp(10f);
    fold.addView(foldSegment, fp);
    segmentButtons(foldSegment, new String[] {Lang.getString(R.string.Tgx101GazetaFoldLines, 9), Lang.getString(R.string.Tgx101GazetaFoldFull)},
      Tgx101Gazeta.fold() == Tgx101Gazeta.FOLD_NONE ? 1 : 0, which -> {
        Tgx101Gazeta.setFold(which == 1 ? Tgx101Gazeta.FOLD_NONE : Tgx101Gazeta.FOLD_LINES);
        rebuild();
      });

    // autoscroll
    LinearLayout auto = section(context, R.string.Tgx101GazetaAuto);
    slider(auto, R.string.Tgx101GazetaAutoSpeed, 1, 10, Tgx101Gazeta.autoSpeed(), v -> String.valueOf(v), Tgx101Gazeta::setAutoSpeed);
    TextView hint = label(context, Lang.getString(R.string.Tgx101GazetaAutoHint), 12.5f, false, pal()[3]);
    hint.setPadding(Screen.dp(2f), Screen.dp(6f), Screen.dp(2f), 0);
    auto.addView(hint);

    refresh();
  }
}
