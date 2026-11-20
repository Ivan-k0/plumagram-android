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

import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.component.chat.WallpaperAdapter;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.EmojiTextView;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101 (user 2026-11-20 «выбор фона должен иметь выбор прямо из меню»): chat ⋮ → «Chat wallpaper» — a sheet with the
 * chat themes in a row (a card each: the theme's wallpaper, two bubbles in its colours, its emoji), «No theme» first;
 * ☀/☾ shows the day or night look of the cards; a tap sets the theme at once and the chat under the sheet changes.
 * «Choose other wallpaper» turns the row into the app's wallpapers, set for this chat only (or, with Premium, for both);
 * «Remove wallpaper» takes the chat's own wallpaper and theme away.
 */
public final class Tgx101ChatThemeSheet {
  private static final float CARD_W = 78f, CARD_H = 108f;

  private final MessagesController c;
  private final Tdlib tdlib;
  private final long chatId;
  private final Context context;

  private Tgx101Sheet.Handle handle;
  private ImageView leftButton, nightButton;
  private TextView title;
  private LinearLayout row;
  private HorizontalScrollView rowScroll;
  private LinearLayout links;

  private boolean previewDark;
  private boolean wallpaperPage;
  private boolean forBoth;
  private final List<Card> cards = new ArrayList<>();
  private @Nullable List<TGBackground> wallpapers;
  /** what was tapped last, marked before the server answers */
  private @Nullable String pendingTheme;
  private boolean pendingNone;

  private Tgx101ChatThemeSheet (MessagesController c, long chatId) {
    this.c = c;
    this.tdlib = c.tdlib();
    this.chatId = chatId;
    this.context = c.context();
    this.previewDark = Theme.isDark();
  }

  public static Tgx101ChatThemeSheet show (MessagesController c, long chatId, Runnable onDismiss) {
    Tgx101ChatThemeSheet s = new Tgx101ChatThemeSheet(c, chatId);
    s.handle = Tgx101Sheet.show(c, () -> {
      for (Card card : s.cards) card.destroy();
      s.cards.clear();
      onDismiss.run();
    }, s::build);
    return s;
  }

  // Building

  private void build (LinearLayout body) {
    LayoutTransition t = new LayoutTransition();
    t.enableTransitionType(LayoutTransition.CHANGING);
    t.setDuration(200);
    body.setLayoutTransition(t);

    FrameLayout header = new FrameLayout(context);
    leftButton = iconButton(R.drawable.baseline_close_24);
    leftButton.setOnClickListener(v -> {
      if (wallpaperPage) {
        setPage(false);
      } else {
        handle.dismiss();
      }
    });
    header.addView(leftButton, new FrameLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f), Gravity.LEFT | Gravity.CENTER_VERTICAL));
    title = new TextView(context);
    title.setTextColor(Theme.textAccentColor());
    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
    title.setTypeface(Fonts.getRobotoMedium());
    title.setGravity(Gravity.CENTER);
    title.setSingleLine(true);
    title.setText(Lang.getString(R.string.Tgx101ChatThemeTitle));
    FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
    tp.leftMargin = tp.rightMargin = Screen.dp(56f);
    header.addView(title, tp);
    nightButton = iconButton(previewDark ? R.drawable.baseline_brightness_5_24 : R.drawable.baseline_brightness_2_24);
    nightButton.setOnClickListener(v -> toggleNight());
    header.addView(nightButton, new FrameLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f), Gravity.RIGHT | Gravity.CENTER_VERTICAL));
    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
    hp.leftMargin = hp.rightMargin = Screen.dp(6f);
    body.addView(header, hp);

    rowScroll = new HorizontalScrollView(context);
    rowScroll.setHorizontalScrollBarEnabled(false);
    rowScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
    row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(Screen.dp(12f), Screen.dp(6f), Screen.dp(12f), Screen.dp(10f));
    rowScroll.addView(row);
    body.addView(rowScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    links = new LinearLayout(context);
    links.setOrientation(LinearLayout.VERTICAL);
    body.addView(links, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    fillThemes();
    fillLinks();
  }

  private ImageView iconButton (int res) {
    ImageView v = new ImageView(context);
    v.setScaleType(ImageView.ScaleType.CENTER);
    v.setImageResource(res);
    v.setColorFilter(Theme.getColor(ColorId.icon));
    Views.setClickable(v);
    RippleSupport.setTransparentSelector(v);
    return v;
  }

  private void clearRow () {
    for (Card card : cards) card.destroy();
    cards.clear();
    row.removeAllViews();
  }

  private void fillThemes () {
    clearRow();
    TdApi.Chat chat = tdlib.chat(chatId);
    String current = currentThemeName(chat);
    Card none = new Card(context, null, null);
    none.setOnClickListener(v -> pickTheme(null));
    addCard(none, current == null);
    for (TdApi.EmojiChatTheme theme : tdlib.tgx101EmojiChatThemes()) {
      Card card = new Card(context, theme, null);
      card.setOnClickListener(v -> pickTheme(theme.name));
      addCard(card, theme.name.equals(current));
    }
    scrollToSelected();
  }

  private void fillWallpapers () {
    clearRow();
    if (wallpapers == null) {
      tdlib.wallpaper().getBackgrounds(list -> UI.post(() -> {
        if (handle == null || handle.popup.isWindowHidden()) return;
        wallpapers = new ArrayList<>(list);
        TGBackground mine = tdlib.settings().getWallpaper(Theme.getWallpaperIdentifier());
        if (mine != null && !mine.isEmpty()) {
          boolean found = false;
          for (TGBackground w : wallpapers) if (TGBackground.compare(w, mine)) { found = true; break; }
          if (!found) wallpapers.add(0, mine); // the app's own (a photo from the gallery, too)
        }
        if (wallpaperPage) fillWallpapers();
      }), Theme.isDark());
      return;
    }
    TdApi.Chat chat = tdlib.chat(chatId);
    TGBackground current = chat != null && chat.background != null && chat.background.background != null ? new TGBackground(tdlib, chat.background.background) : null;
    for (TGBackground w : wallpapers) {
      Card card = new Card(context, null, w);
      card.setOnClickListener(v -> pickWallpaper(w));
      addCard(card, current != null && TGBackground.compare(current, w));
    }
    scrollToSelected();
  }

  private void addCard (Card card, boolean selected) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Screen.dp(CARD_W), Screen.dp(CARD_H));
    p.leftMargin = p.rightMargin = Screen.dp(4f);
    row.addView(card, p);
    card.setChecked(selected, false);
    cards.add(card);
  }

  private void scrollToSelected () {
    rowScroll.post(() -> {
      for (Card card : cards) {
        if (card.checked) {
          int x = card.getLeft() + card.getWidth() / 2 - rowScroll.getWidth() / 2;
          rowScroll.scrollTo(Math.max(0, x), 0);
          return;
        }
      }
      rowScroll.scrollTo(0, 0);
    });
  }

  private void fillLinks () {
    links.removeAllViews();
    TdApi.Chat chat = tdlib.chat(chatId);
    if (!wallpaperPage) {
      links.addView(link(R.string.Tgx101ChatThemeOther, Theme.getColor(ColorId.textLink), v -> setPage(true)));
      boolean hasOwn = chat != null && (chat.background != null || chat.theme != null);
      if (hasOwn) {
        links.addView(link(R.string.Tgx101ChatThemeRemove, Theme.textRedColor(), v -> removeAll()));
      }
    } else if (tdlib.hasPremium()) {
      links.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(R.string.Tgx101ChatThemeBoth), null, forBoth, v -> {
        forBoth = !forBoth;
        fillLinks();
      }));
    } else {
      TextView hint = new TextView(context);
      hint.setText(Lang.getString(R.string.Tgx101ChatThemeSelfOnly));
      hint.setTextColor(Theme.textDecentColor());
      hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
      hint.setGravity(Gravity.CENTER_VERTICAL);
      hint.setMinHeight(Screen.dp(52f));
      hint.setPadding(Screen.dp(20f), 0, Screen.dp(20f), 0);
      links.addView(hint);
    }
  }

  private View link (int text, int color, View.OnClickListener click) {
    TextView v = new TextView(context);
    v.setText(Lang.getString(text));
    v.setTextColor(color);
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    v.setTypeface(Fonts.getRobotoMedium());
    v.setGravity(Gravity.CENTER);
    v.setMinHeight(Screen.dp(48f));
    Views.setClickable(v);
    RippleSupport.setTransparentSelector(v);
    v.setOnClickListener(click);
    return v;
  }

  // Pages and the day / night look

  private void setPage (boolean wallpapers) {
    if (wallpaperPage == wallpapers) return;
    wallpaperPage = wallpapers;
    Tgx101Diag.mark("chat theme sheet: " + (wallpapers ? "wallpapers" : "themes"));
    crossFade(row, () -> {
      if (wallpapers) fillWallpapers(); else fillThemes();
    });
    crossFade(title, () -> title.setText(Lang.getString(wallpapers ? R.string.Tgx101ChatThemeWallpapers : R.string.Tgx101ChatThemeTitle)));
    crossFade(leftButton, () -> leftButton.setImageResource(wallpapers ? R.drawable.baseline_arrow_back_24 : R.drawable.baseline_close_24));
    nightButton.animate().alpha(wallpapers ? 0f : 1f).setDuration(150).start();
    nightButton.setEnabled(!wallpapers);
    fillLinks();
  }

  private void toggleNight () {
    if (wallpaperPage) return;
    previewDark = !previewDark;
    nightButton.animate().cancel();
    nightButton.animate().rotationBy(180f).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
    nightButton.postDelayed(() -> nightButton.setImageResource(previewDark ? R.drawable.baseline_brightness_5_24 : R.drawable.baseline_brightness_2_24), 110);
    for (Card card : cards) card.applyLook(true);
  }

  private static void crossFade (View v, Runnable change) {
    v.animate().cancel();
    v.animate().alpha(0f).setDuration(110).withEndAction(() -> {
      change.run();
      v.animate().alpha(1f).setDuration(150).start();
    }).start();
  }

  // Actions

  private void pickTheme (@Nullable String name) {
    TdApi.Chat chat = tdlib.chat(chatId);
    boolean hasBackground = chat != null && chat.background != null;
    String current = currentThemeName(chat);
    if (!hasBackground && (name == null ? current == null : name.equals(current))) return;
    Tgx101Diag.mark("chat theme: " + (name != null ? "set" : "none") + (hasBackground ? ", wallpaper off" : ""));
    pendingTheme = name;
    pendingNone = name == null;
    markThemes(name);
    // the chat's own wallpaper stands over any theme — it goes, the theme shows
    if (hasBackground) tdlib.send(new TdApi.DeleteChatBackground(chatId, false), tdlib.typedOkHandler());
    tdlib.send(new TdApi.SetChatTheme(chatId, name != null ? new TdApi.InputChatThemeEmoji(name) : null), tdlib.typedOkHandler());
  }

  private void markThemes (@Nullable String name) {
    for (Card card : cards) {
      card.setChecked(card.theme == null ? name == null : card.theme.name.equals(name), true);
    }
  }

  private void pickWallpaper (TGBackground w) {
    for (Card card : cards) card.setChecked(card.wallpaper == w, true);
    c.tgx101SetChatWallpaper(chatId, w, !forBoth);
  }

  private void removeAll () {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null) return;
    Tgx101Diag.mark("chat wallpaper: removed");
    if (chat.background != null) tdlib.send(new TdApi.DeleteChatBackground(chatId, false), tdlib.typedOkHandler());
    if (chat.theme != null) tdlib.send(new TdApi.SetChatTheme(chatId, null), tdlib.typedOkHandler());
    pendingTheme = null;
    pendingNone = true;
    markThemes(null);
  }

  /** the server's answer came (the chat's wallpaper or theme changed) */
  void onChatChanged () {
    if (handle == null || handle.popup.isWindowHidden()) return;
    TdApi.Chat chat = tdlib.chat(chatId);
    String current = currentThemeName(chat);
    if (pendingNone ? current == null : pendingTheme != null && pendingTheme.equals(current)) {
      pendingTheme = null;
      pendingNone = false;
    }
    if (!wallpaperPage) {
      if (!pendingNone && pendingTheme == null) markThemes(current);
    } else {
      TGBackground bg = chat != null && chat.background != null && chat.background.background != null ? new TGBackground(tdlib, chat.background.background) : null;
      for (Card card : cards) card.setChecked(bg != null && card.wallpaper != null && TGBackground.compare(bg, card.wallpaper), true);
    }
    fillLinks();
  }

  private static @Nullable String currentThemeName (@Nullable TdApi.Chat chat) {
    return chat != null && chat.theme instanceof TdApi.ChatThemeEmoji ? ((TdApi.ChatThemeEmoji) chat.theme).name : null;
  }

  // A card

  private final class Card extends FrameLayout {
    final @Nullable TdApi.EmojiChatTheme theme;
    final @Nullable TGBackground wallpaper;
    private final WallpaperAdapter.WallpaperView preview;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean checked;
    private float checkFactor;
    private @Nullable ValueAnimator checkAnimator;
    private int incomingColor, outgoingColor;

    Card (Context context, @Nullable TdApi.EmojiChatTheme theme, @Nullable TGBackground wallpaper) {
      super(context);
      this.theme = theme;
      this.wallpaper = wallpaper;
      setWillNotDraw(false);
      Views.setClickable(this);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        setOutlineProvider(new ViewOutlineProvider() {
          @Override
          public void getOutline (View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Screen.dp(12f));
          }
        });
        setClipToOutline(true);
      }
      preview = new WallpaperAdapter.WallpaperView(context);
      preview.tgx101FreeSize = true;
      addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      ring.setStyle(Paint.Style.STROKE);
      ring.setStrokeWidth(Screen.dp(2.5f));
      ring.setColor(Theme.getColor(ColorId.checkActive));

      if (theme != null) {
        EmojiTextView emoji = new EmojiTextView(context);
        emoji.setText(theme.name);
        emoji.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f);
        emoji.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        ep.bottomMargin = Screen.dp(6f);
        addView(emoji, ep);
      } else if (wallpaper == null) {
        // «No theme»: the app's own look with a cross and the words under it
        LinearLayout face = new LinearLayout(context);
        face.setOrientation(LinearLayout.VERTICAL);
        face.setGravity(Gravity.CENTER);
        ImageView cross = new ImageView(context);
        cross.setImageResource(R.drawable.baseline_close_24);
        cross.setColorFilter(Theme.getColor(ColorId.icon));
        face.addView(cross, new LinearLayout.LayoutParams(Screen.dp(28f), Screen.dp(28f)));
        TextView label = new TextView(context);
        label.setText(Lang.getString(R.string.Tgx101ChatThemeNone));
        label.setTextColor(Theme.textAccentColor());
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
        label.setGravity(Gravity.CENTER);
        label.setPadding(Screen.dp(4f), Screen.dp(6f), Screen.dp(4f), 0);
        face.addView(label);
        addView(face, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      }
      applyLook(false);
    }

    void applyLook (boolean animated) {
      if (wallpaper != null) {
        preview.setWallpaper(wallpaper, false);
        return;
      }
      if (theme == null) {
        preview.setWallpaper(null, false); // drawn in the app's background colour
        return;
      }
      TdApi.ThemeSettings s = previewDark ? theme.darkSettings : theme.lightSettings;
      Runnable apply = () -> {
        preview.setWallpaper(s != null && s.background != null ? new TGBackground(tdlib, s.background) : null, false);
        incomingColor = previewDark ? 0xFF212D3B : 0xFFFFFFFF;
        outgoingColor = s != null ? outgoingOf(s) : Theme.getColor(ColorId.bubbleOut_background);
        invalidate();
      };
      if (animated && getWindowToken() != null) {
        animate().cancel();
        animate().alpha(.35f).setDuration(110).withEndAction(() -> {
          apply.run();
          animate().alpha(1f).setDuration(170).start();
        }).start();
      } else {
        apply.run();
      }
    }

    void setChecked (boolean checked, boolean animated) {
      if (this.checked == checked) return;
      this.checked = checked;
      if (checkAnimator != null) checkAnimator.cancel();
      float to = checked ? 1f : 0f;
      if (!animated) {
        checkFactor = to;
        invalidate();
        return;
      }
      checkAnimator = ValueAnimator.ofFloat(checkFactor, to);
      checkAnimator.setDuration(180);
      checkAnimator.setInterpolator(new DecelerateInterpolator());
      checkAnimator.addUpdateListener(a -> {
        checkFactor = (float) a.getAnimatedValue();
        invalidate();
      });
      checkAnimator.start();
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      preview.attach();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      preview.detach();
    }

    void destroy () {
      if (checkAnimator != null) checkAnimator.cancel();
      preview.performDestroy();
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      super.dispatchDraw(c);
      int w = getWidth(), h = getHeight();
      if (theme != null) {
        // two bubbles in the theme's colours: theirs on the left, mine on the right
        float r = Screen.dp(8f);
        bubble.setColor(incomingColor);
        rect.set(Screen.dp(8f), Screen.dp(14f), w - Screen.dp(22f), Screen.dp(30f));
        c.drawRoundRect(rect, r, r, bubble);
        bubble.setColor(outgoingColor);
        rect.set(Screen.dp(22f), Screen.dp(36f), w - Screen.dp(8f), Screen.dp(52f));
        c.drawRoundRect(rect, r, r, bubble);
      }
      if (checkFactor > 0f) {
        float inset = Screen.dp(1.25f);
        rect.set(inset, inset, w - inset, h - inset);
        ring.setAlpha((int) (255 * checkFactor));
        float rr = Screen.dp(11f);
        c.drawRoundRect(rect, rr, rr, ring);
      }
    }
  }

  /** the colour of my bubbles in a theme: its fill (first colour of a gradient) or its accent */
  private static int outgoingOf (TdApi.ThemeSettings s) {
    TdApi.BackgroundFill fill = s.outgoingMessageFill;
    int color;
    if (fill instanceof TdApi.BackgroundFillSolid) {
      color = ((TdApi.BackgroundFillSolid) fill).color;
    } else if (fill instanceof TdApi.BackgroundFillGradient) {
      color = ((TdApi.BackgroundFillGradient) fill).topColor;
    } else if (fill instanceof TdApi.BackgroundFillFreeformGradient && ((TdApi.BackgroundFillFreeformGradient) fill).colors.length > 0) {
      color = ((TdApi.BackgroundFillFreeformGradient) fill).colors[0];
    } else {
      color = s.accentColor;
    }
    return 0xFF000000 | color;
  }
}
