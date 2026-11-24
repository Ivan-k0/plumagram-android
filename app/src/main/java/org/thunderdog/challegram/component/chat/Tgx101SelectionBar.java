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
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Layout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.Tgx101FormatMenuController;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.ColorUtils;

/**
 * TGx101: the text selection bar (mockups B3 / bubble 2) — a compact card in the editor window's colours that
 * sits right above the start of the selection and follows the top handle, with a tail pointing at it. Used by
 * the message input (with the formatting row) and by the in-message text selection (actions only).
 * The top row scrolls sideways: the leading actions, Cut / Copy / Paste / Select all, then every item the
 * system or other apps added (Translate, Web search, Share…). Those stay in the action mode's menu, hidden,
 * and are invoked from there, so they work exactly as in the system toolbar.
 */
public final class Tgx101SelectionBar {
  public static final class Action {
    final String key; // TGx101: place in the user's order (Tgx101BarOrder), "apps" for other apps' items
    final String title;
    final Runnable onClick;

    public Action (String title, Runnable onClick) {
      this(org.thunderdog.challegram.ui.Tgx101BarOrder.APPS, title, onClick);
    }

    public Action (String key, String title, Runnable onClick) {
      this.key = key;
      this.title = title;
      this.onClick = onClick;
    }
  }

  private static String keyOf (MenuItem item) {
    int id = item.getItemId();
    if (id == android.R.id.cut) return org.thunderdog.challegram.ui.Tgx101BarOrder.CUT;
    if (id == android.R.id.copy) return org.thunderdog.challegram.ui.Tgx101BarOrder.COPY;
    if (id == android.R.id.paste) return org.thunderdog.challegram.ui.Tgx101BarOrder.PASTE;
    if (id == android.R.id.selectAll) return org.thunderdog.challegram.ui.Tgx101BarOrder.SELECT_ALL;
    return org.thunderdog.challegram.ui.Tgx101BarOrder.APPS;
  }

  private final View view;
  private final @Nullable TextView textView; // null: anchored to a point (the in-bubble selection), actions only
  private final boolean withFormatting;
  private final List<Action> leadingActions;
  private final boolean followSelection; // in a message: follows the top handle; in the input: static above the field
  private PopupWindow window;
  private Menu menu;
  private FrameLayout content;
  private LinearLayout card;
  private TailView tail;

  public Tgx101SelectionBar (TextView view, boolean withFormatting, @Nullable List<Action> leadingActions) {
    this.view = view;
    this.textView = view;
    this.withFormatting = withFormatting;
    this.leadingActions = leadingActions != null ? leadingActions : new ArrayList<>();
    this.followSelection = !(view instanceof InputView);
  }

  /** A bar over any view, pointing at a spot set with {@link #showAnchored} — only the given actions */
  public Tgx101SelectionBar (View anchorView, List<Action> actions) {
    this.view = anchorView;
    this.textView = null;
    this.withFormatting = false;
    this.leadingActions = actions;
    this.followSelection = true;
  }

  private int anchorX, anchorTop, anchorBottom;

  /** Anchored mode: shows (or moves) the bar above a spot on the screen — x, line top and the selection's bottom */
  public void showAnchored (int x, int top, int bottom) {
    anchorX = x;
    anchorTop = top;
    anchorBottom = bottom;
    if (content == null) {
      build(new ArrayList<>());
    }
    if (hiddenWhileDragging || hiddenForScroll) {
      content.setAlpha(0f);
      return;
    }
    reposition();
  }

  // TGx101 (user 2026-11-16 «меню над выделением должно исчезать, когда я свайпаю по чату, и появляться, только когда
  // текст в пределах видимости экрана»): the chat moves — the bar fades away; it is back once the chat stands still
  // and the selection is on the screen
  private boolean hiddenForScroll;

  public void hideForScroll () {
    if (hiddenForScroll) return;
    hiddenForScroll = true;
    if (content != null && window != null) {
      org.thunderdog.challegram.Tgx101Diag.mark("select: bar hidden — the chat moves");
      content.animate().cancel();
      content.animate().alpha(0f).setDuration(100).start();
    }
  }

  public void showAfterScroll () {
    if (!hiddenForScroll) return;
    hiddenForScroll = false;
    if (content != null && window != null && !hiddenWhileDragging) {
      org.thunderdog.challegram.Tgx101Diag.mark("select: bar back — the chat stands, the selection on the screen");
      reposition();
      content.animate().cancel();
      pivotAtBottom();
      content.setScaleX(.96f);
      content.setScaleY(.96f);
      content.animate().alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(BACK).setDuration(HANDLE_BACK).start();
    }
  }

  private static boolean isStandard (int id) {
    return id == android.R.id.cut || id == android.R.id.copy || id == android.R.id.paste || id == android.R.id.selectAll;
  }

  private static boolean isFormat (int id) {
    for (int formatId : Tgx101FormatMenuController.IDS) {
      if (formatId == id) return true;
    }
    return false;
  }

  /** From onPrepareActionMode: hides the system toolbar's items and shows / refreshes the bar */
  public void update (Menu menu) {
    this.menu = menu;
    List<MenuItem> standard = new ArrayList<>();
    List<MenuItem> others = new ArrayList<>();
    for (int i = 0; i < menu.size(); i++) {
      MenuItem item = menu.getItem(i);
      int id = item.getItemId();
      if (!isFormat(id) && !TextUtils.isEmpty(item.getTitle())) {
        if (isStandard(id)) standard.add(item); else others.add(item);
      }
      item.setVisible(false); // the system toolbar shows nothing
    }
    List<MenuItem> top = new ArrayList<>();
    for (int id : new int[] {android.R.id.cut, android.R.id.copy, android.R.id.paste, android.R.id.selectAll}) {
      for (MenuItem item : standard) if (item.getItemId() == id) top.add(item);
    }
    top.addAll(others);
    if (view instanceof InputView) {
      top = org.thunderdog.challegram.ui.Tgx101BarOrder.apply(org.thunderdog.challegram.ui.Tgx101BarOrder.BAR_INPUT, top, Tgx101SelectionBar::keyOf); // TGx101: user's order
    }
    build(top);
    if (hiddenWhileDragging) {
      content.setAlpha(0f); // still dragging — the system re-asks the menu, don't pop the bar back over the magnifier
    }
    reposition();
  }

  public void dismiss () {
    view.removeCallbacks(showAfterDrag);
    view.removeCallbacks(fadeForHandle);
    hiddenWhileDragging = false;
    hiddenForScroll = false;
    handleDragging = false;
    if (window != null) {
      try { window.dismiss(); } catch (Throwable ignored) { }
      window = null;
      if (content != null) {
        content.animate().cancel();
        content.setAlpha(1f);
        content.setScaleX(1f);
        content.setScaleY(1f);
        content.setTranslationY(0f);
      }
    }
    menu = null;
  }

  public boolean isShowing () {
    return window != null && window.isShowing();
  }

  private boolean hiddenWhileDragging;
  private final Runnable showAfterDrag = () -> {
    hiddenWhileDragging = false;
    org.thunderdog.challegram.Tgx101Diag.mark("select: bar shown again");
    if (content != null && window != null && !hiddenForScroll) {
      reposition();
      content.animate().alpha(1f).setDuration(120).start();
    }
  };

  /**
   * The selection is changing (a handle is being dragged): hide the bar so it doesn't cover the system magnifier,
   * show it again a moment after the selection settles.
   */
  public void onSelectionChanging () {
    if (content == null || window == null || !followSelection) return;
    if (!hiddenWhileDragging) {
      hiddenWhileDragging = true;
      org.thunderdog.challegram.Tgx101Diag.mark("select: bar hidden while the selection changes");
      content.animate().cancel();
      content.setAlpha(0f);
    }
    view.removeCallbacks(showAfterDrag);
    view.postDelayed(showAfterDrag, 900);
  }

  // TGx101 (user 2026-10-13 20:30, «лупу не видно, когда включается редактор»): in the message input the bar sits right
  // where the system magnifier shows. While a selection handle is dragged it fades away a moment after the drag starts
  // and comes back once the finger is lifted. Only handles — a swipe over the selected text leaves it alone.
  private static final long HANDLE_HIDE_DELAY = 250, HANDLE_FADE = 160;
  private boolean handleDragging;
  // user 2026-10-13 23:45 «сделай современно плавной»: Material 3 motion — away: a slight shrink towards the field
  // with the emphasized-accelerate curve; back: it grows from 96 % and settles with the emphasized-decelerate curve
  private static final long HANDLE_BACK = 280;
  private static final android.view.animation.Interpolator AWAY = androidx.core.view.animation.PathInterpolatorCompat.create(.3f, 0f, .8f, .15f);
  private static final android.view.animation.Interpolator BACK = androidx.core.view.animation.PathInterpolatorCompat.create(.05f, .7f, .1f, 1f);

  private void pivotAtBottom () {
    content.setPivotX(content.getWidth() / 2f);
    content.setPivotY(content.getHeight());
  }

  private final Runnable fadeForHandle = () -> {
    if (!handleDragging || content == null || window == null) return;
    hiddenWhileDragging = true;
    org.thunderdog.challegram.Tgx101Diag.mark("select: bar fades for the handle (magnifier)");
    content.animate().cancel();
    pivotAtBottom();
    content.animate().alpha(0f).scaleX(.96f).scaleY(.96f).translationY(Screen.dp(4f)).setInterpolator(AWAY).setDuration(HANDLE_FADE).start();
  };

  /** A selection handle started (or keeps) moving */
  public void onHandleDragging () {
    if (handleDragging || content == null || window == null) return;
    handleDragging = true;
    view.removeCallbacks(showAfterDrag);
    view.postDelayed(fadeForHandle, HANDLE_HIDE_DELAY);
  }

  /** The handle was let go */
  public void onHandleReleased () {
    if (!handleDragging) return;
    handleDragging = false;
    view.removeCallbacks(fadeForHandle);
    if (hiddenWhileDragging) {
      hiddenWhileDragging = false;
      org.thunderdog.challegram.Tgx101Diag.mark("select: bar back after the handle");
      if (content != null && window != null && !hiddenForScroll) {
        reposition();
        content.animate().cancel();
        pivotAtBottom();
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setInterpolator(BACK).setDuration(HANDLE_BACK).start();
      }
    }
  }

  /** The drag is over (the in-bubble handles know it): the bar comes back right away */
  public void showNow () {
    if (!hiddenWhileDragging) return;
    view.removeCallbacks(showAfterDrag);
    showAfterDrag.run();
  }

  // Colours: the editor window's panel and its white buttons

  // (user 2026-11-24, mockup «Выделение текста», variant В): a white card as the message menu's, the words in the theme's
  // colour, thin lines between them, a thin line of the theme's colour round it instead of a shadow (the shadow of a
  // square window came out as a grey box past the rounded corners)
  // (user 2026-11-24 «для людей с плохим зрением „Копировать, Вырезать, Вставить“ плохо видно — увеличить вдвое»): the bar
  // grows with the text: the message field's size (18 dp → as before, 27 dp → twice) or the menus' text size, the larger
  private static float tgx101Scale () {
    int input = org.thunderdog.challegram.unsorted.Settings.instance().tgx101InputTextSize();
    float byInput = 1f + Math.max(0, input - 18) / 9f;
    return Math.max(1f, Math.min(2f, Math.max(byInput, org.thunderdog.challegram.unsorted.Settings.tgx101UiScale())));
  }

  // (user 2026-11-24 screenshot: the formatting icons ran into each other) — the icons share the row's width: they grow
  // only while ten of them still keep a gap, then stay at that
  private static float tgx101IconScale () {
    float cell = (Screen.currentWidth() - Screen.dp(32f)) / 10f;
    float fit = cell / Screen.dp(24f + 12f);
    return Math.max(1f, Math.min(tgx101Scale(), fit));
  }

  private static int row () {
    return Math.round(Screen.dp(30f) * tgx101Scale());
  }

  private static int panelColor () {
    return Theme.fillingColor();
  }

  private static int wordColor () {
    return Theme.getColor(org.thunderdog.challegram.theme.ColorId.textLink);
  }

  private static Drawable card (int color) {
    GradientDrawable drawable = rounded(color, 16f);
    drawable.setStroke(Math.max(1, Screen.dp(1f)), ColorUtils.alphaColor(.18f, wordColor()));
    return drawable;
  }

  private static View wordDivider (Context context) {
    View divider = new View(context);
    divider.setBackgroundColor(Theme.separatorColor());
    return divider;
  }

  private void build (List<MenuItem> systemItems) {
    Context context = view.getContext();
    int panel = panelColor();
    card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    int pad = Screen.dp(5f);
    card.setPadding(pad, pad, pad, pad);
    card.setBackground(card(panel));

    HorizontalScrollView scroll = new HorizontalScrollView(context);
    scroll.setHorizontalScrollBarEnabled(false);
    scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
    scroll.setFadingEdgeLength(Screen.dp(22f));
    scroll.setHorizontalFadingEdgeEnabled(true);
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    // TGx101: in a message the actions follow the user's order (MagiX → Message input → Mini editor buttons)
    List<Action> actions = view instanceof InputView ? leadingActions :
      org.thunderdog.challegram.ui.Tgx101BarOrder.apply(org.thunderdog.challegram.ui.Tgx101BarOrder.BAR_MESSAGE, leadingActions, a -> a.key);
    for (Action action : actions) {
      if (row.getChildCount() > 0) row.addView(wordDivider(context), dividerParams());
      row.addView(chip(context, action.title, v -> action.onClick.run()), chipParams());
    }
    for (MenuItem item : systemItems) {
      CharSequence title = item.getItemId() == android.R.id.selectAll ? Lang.getString(R.string.Tgx101SelectAllFull) : item.getTitle();
      if (row.getChildCount() > 0) row.addView(wordDivider(context), dividerParams());
      row.addView(chip(context, title != null ? title.toString() : "", v -> invoke(item)), chipParams());
    }
    scroll.addView(row);
    card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, row()));

    if (withFormatting && textView instanceof InputView) {
      InputView input = (InputView) view;
      LinearLayout icons = new LinearLayout(context);
      icons.setOrientation(LinearLayout.HORIZONTAL);
      for (int entry : Tgx101FormatMenuController.getOrder()) {
        if (entry <= 0 || entry == R.id.btn_plain) continue; // «Clear formatting» gave its place to «Undo» (user's choice)
        final int id = entry;
        ImageView icon = new ImageView(context);
        icon.setImageResource(Tgx101FormatMenuController.iconOf(id));
        icon.setColorFilter(wordColor());
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setBackground(pressable(0, 9f));
        icon.setContentDescription(Lang.getString(Tgx101FormatMenuController.nameOf(id)));
        icon.setOnClickListener(v -> {
          if (id == R.id.btn_link) {
            onLink(input);
            return;
          }
          input.tgx101SaveUndo();
          input.tgx101RunBarAction(() -> {
            if (id == R.id.btn_plain) {
              input.setSpan(id);
            } else {
              input.tgx101ToggleSpan(id);
            }
          });
        });
        icon.setScaleX(tgx101IconScale());
        icon.setScaleY(tgx101IconScale());
        icons.addView(icon, new LinearLayout.LayoutParams(0, row(), 1f));
      }
      // «Undo» — the last one in the row, behind a thin line (mockup 3)
      View divider = new View(context);
      divider.setBackgroundColor(ColorUtils.alphaColor(.2f, Theme.textDecentColor()));
      LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(Math.max(1, Screen.dp(1f) / 2), Math.round(Screen.dp(20f) * tgx101Scale()));
      dividerParams.gravity = Gravity.CENTER_VERTICAL;
      icons.addView(divider, dividerParams);
      ImageView undo = new ImageView(context);
      undo.setImageResource(R.drawable.baseline_undo_24);
      undo.setColorFilter(wordColor());
      undo.setScaleType(ImageView.ScaleType.CENTER);
      undo.setBackground(pressable(0, 9f));
      undo.setContentDescription(Lang.getString(R.string.Tgx101Undo));
      undo.setOnClickListener(v -> input.tgx101RunBarAction(() -> {
        if (!input.tgx101Undo()) {
          org.thunderdog.challegram.tool.UI.showToast(R.string.Tgx101NothingToUndo, android.widget.Toast.LENGTH_SHORT);
        }
      }));
      undo.setScaleX(tgx101IconScale());
      undo.setScaleY(tgx101IconScale());
      icons.addView(undo, new LinearLayout.LayoutParams(0, row(), 1f));
      LinearLayout.LayoutParams iconsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, row());
      iconsParams.topMargin = Screen.dp(3f);
      card.addView(icons, iconsParams);
    }

    // TGx101: a showing PopupWindow ignores setContentView — the bar refreshes inside the content it is showing, otherwise
    // the new items (and the fade for the magnifier) went to a view nobody saw
    if (content == null || window == null) {
      content = new FrameLayout(context);
      content.setClipChildren(false);
      content.setClipToPadding(false);
    } else {
      content.removeAllViews();
    }
    FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    content.addView(card, cardParams);
    tail = new TailView(context, panel);
    content.addView(tail, new FrameLayout.LayoutParams(Screen.dp(16f), Screen.dp(8f)));
  }

  private static TextView chip (Context context, String title, View.OnClickListener onClick) {
    TextView button = new TextView(context);
    button.setText(title);
    button.setSingleLine(true);
    button.setGravity(Gravity.CENTER);
    button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f * tgx101Scale());
    button.setTypeface(Fonts.getRobotoRegular());
    button.setTextColor(wordColor());
    button.setPadding(Math.round(Screen.dp(11f) * tgx101Scale()), 0, Math.round(Screen.dp(11f) * tgx101Scale()), 0);
    button.setBackground(pressable(0, 9f));
    button.setOnClickListener(onClick);
    return button;
  }

  private static LinearLayout.LayoutParams chipParams () {
    return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, row());
  }

  private static LinearLayout.LayoutParams dividerParams () {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Math.max(1, Screen.dp(1f) / 2), Screen.dp(16f));
    params.gravity = Gravity.CENTER_VERTICAL;
    return params;
  }

  private void invoke (MenuItem item) {
    Menu menu = this.menu;
    TextView view = this.textView;
    if (view == null) return;
    int id = item.getItemId();
    if (id == android.R.id.selectAll && view instanceof android.widget.EditText && view.length() > 0) {
      // (user 2026-10-27 23:56 «после „Выделить всё“ редактор не должен гаснуть»): the system command restarts the
      // selection mode and the bar went with it — the selection is widened here, the bar stays and moves to it
      ((android.widget.EditText) view).setSelection(0, view.length());
      view.post(this::reposition);
      return;
    }
    if (isStandard(id)) {
      if (view instanceof InputView && (id == android.R.id.cut || id == android.R.id.paste)) {
        ((InputView) view).tgx101SaveUndo();
      }
      view.onTextContextMenuItem(id);
      return;
    }
    Intent intent = item.getIntent();
    if (intent != null && Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
      int start = view.getSelectionStart(), end = view.getSelectionEnd();
      if (start >= 0 && end != start) {
        Intent copy = new Intent(intent);
        copy.putExtra(Intent.EXTRA_PROCESS_TEXT, view.getText().subSequence(Math.min(start, end), Math.max(start, end)).toString());
        copy.putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, !(view instanceof InputView));
        try { view.getContext().startActivity(copy); } catch (Throwable ignored) { }
      }
      return;
    }
    if (menu != null && id != 0) {
      menu.performIdentifierAction(id, 0);
    } else if (intent != null) {
      try { view.getContext().startActivity(intent); } catch (Throwable ignored) { }
    }
  }

  // Position: right above the start of the selection, following the top handle

  public void reposition () {
    if (content == null) return;
    if (textView == null) {
      positionFollowing(anchorX, anchorTop, anchorBottom);
      return;
    }
    TextView view = textView;
    Layout layout = view.getLayout();
    int start = Math.min(view.getSelectionStart(), view.getSelectionEnd());
    int end = Math.max(view.getSelectionStart(), view.getSelectionEnd());
    if (layout == null || start < 0) return;
    int[] location = new int[2];
    view.getLocationOnScreen(location);
    int originX = location[0] + view.getTotalPaddingLeft() - view.getScrollX();
    int originY = location[1] + view.getTotalPaddingTop() - view.getScrollY();
    int startLine = layout.getLineForOffset(start);
    int anchorX = originX + (int) layout.getPrimaryHorizontal(start);
    int anchorTop = originY + layout.getLineTop(startLine);
    int endLine = layout.getLineForOffset(end);
    int anchorBottom = originY + layout.getLineBottom(endLine);

    int screenWidth = Screen.currentWidth();
    if (!followSelection) {
      // the message input: a static bar right above the input field, full width, no tail
      int width = Math.min(screenWidth - Screen.dp(12f), Screen.dp(480f));
      tail.setVisibility(View.GONE);
      ((FrameLayout.LayoutParams) card.getLayoutParams()).topMargin = 0;
      content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      // room under the card for its shadow and rounded bottom corners (a window of exactly the card's height cuts them off)
      int shadow = Screen.dp(8f);
      int height = card.getMeasuredHeight() + shadow;
      int[] inputLocation = new int[2];
      view.getLocationOnScreen(inputLocation);
      int x = (screenWidth - width) / 2;
      int y = inputLocation[1] - height + shadow - Screen.dp(6f);
      if (y < Screen.getStatusBarHeight()) y = inputLocation[1] + view.getHeight() + Screen.dp(6f);
      showAt(x, y, width, height);
      return;
    }
    positionFollowing(anchorX, anchorTop, anchorBottom);
  }

  private void positionFollowing (int anchorX, int anchorTop, int anchorBottom) {
    int screenWidth = Screen.currentWidth();
    // (bigger text: the bar is wider as well, so «Копировать» isn't scrolled out of a 300 dp bar)
    int width = Math.min(Math.round(Screen.dp(300f) * tgx101Scale()), screenWidth - Screen.dp(24f));
    content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    int cardHeight = card.getMeasuredHeight();
    int tailHeight = Screen.dp(8f);
    int height = cardHeight + tailHeight;

    int x = Math.max(Screen.dp(12f), Math.min(screenWidth - width - Screen.dp(12f), anchorX - width / 2));
    int y = anchorTop - height - Screen.dp(2f);
    boolean below = y < Screen.getStatusBarHeight() + Screen.dp(4f);
    if (below) {
      y = anchorBottom + Screen.dp(26f); // under the bottom handle
      // (user 2026-11-24 video «куда делось меню»: a long selection reaching under the field put the bar over the
      // keyboard, half hidden) — never below what the chat shows: then it stays at the top of the screen
      android.graphics.Rect visible = new android.graphics.Rect();
      view.getWindowVisibleDisplayFrame(visible);
      int limit = visible.bottom - Screen.dp(64f) * Math.round(Math.max(1f, tgx101Scale()));
      if (y + height > limit) {
        y = Screen.getStatusBarHeight() + Screen.dp(4f);
        below = false; // no tail: it points at nothing on screen
        tail.setVisibility(View.INVISIBLE);
      } else {
        tail.setVisibility(View.VISIBLE);
      }
    } else {
      tail.setVisibility(View.VISIBLE);
    }
    // the tail points at the start of the selection
    FrameLayout.LayoutParams cardParams = (FrameLayout.LayoutParams) card.getLayoutParams();
    cardParams.topMargin = below ? tailHeight : 0;
    FrameLayout.LayoutParams tailParams = (FrameLayout.LayoutParams) tail.getLayoutParams();
    tailParams.leftMargin = Math.max(Screen.dp(12f), Math.min(width - Screen.dp(28f), anchorX - x - Screen.dp(8f)));
    tailParams.topMargin = below ? 0 : cardHeight - Screen.dp(1f);
    tail.setPointingUp(below);

    showAt(x, y, width, height);
  }

  // The link: typed right here, in a field in place of the bar — the keyboard stays up (no dialog window)

  private void onLink (InputView input) {
    int start = input.getSelectionStart(), end = input.getSelectionEnd();
    if (start < 0 || end <= start) return;
    android.text.style.URLSpan[] links = input.getText().getSpans(start, end, android.text.style.URLSpan.class);
    if (links != null && links.length > 0) {
      // second press: the link is taken off
      input.tgx101SaveUndo();
      input.tgx101RunBarAction(() -> input.removeSpan(new org.drinkless.tdlib.TdApi.TextEntityTypeTextUrl(links[0].getURL())));
      return;
    }
    Context context = view.getContext();
    org.thunderdog.challegram.BaseActivity activity = org.thunderdog.challegram.tool.UI.getContext(context);
    if (activity == null) return;
    final int barX = lastX, barY = lastY, barWidth = lastWidth;
    final org.thunderdog.challegram.widget.PopupLayout popup = new org.thunderdog.challegram.widget.PopupLayout(activity);
    popup.setNeedRootInsets();
    FrameLayout wrap = new FrameLayout(activity);
    wrap.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    wrap.setOnClickListener(v -> popup.hideWindow(false)); // a tap aside: no link

    LinearLayout field = new LinearLayout(activity);
    field.setOrientation(LinearLayout.HORIZONTAL);
    field.setGravity(Gravity.CENTER_VERTICAL);
    int pad = Screen.dp(5f);
    field.setPadding(pad, pad, pad, pad);
    field.setBackground(card(panelColor()));
    field.setClickable(true);
    android.widget.EditText url = new android.widget.EditText(activity);
    url.setSingleLine(true);
    url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
    url.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    url.setHint(Lang.getString(R.string.URL));
    url.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    url.setTextColor(Theme.textAccentColor());
    url.setHintTextColor(Theme.textDecentColor());
    url.setBackground(rounded(Theme.fillingColor(), 9f));
    url.setPadding(Screen.dp(10f), 0, Screen.dp(10f), 0);
    field.addView(url, new LinearLayout.LayoutParams(0, Screen.dp(34f), 1f));
    final boolean[] applied = {false};
    Runnable apply = () -> {
      String link = url.getText().toString().trim();
      if (link.isEmpty()) {
        popup.hideWindow(false);
        return;
      }
      if (!org.thunderdog.challegram.tool.Strings.isValidLink(link)) {
        url.setError(Lang.getString(R.string.URL));
        return;
      }
      applied[0] = true;
      input.tgx101LinkOpen = false;
      input.requestFocus();
      input.setSelection(start, end);
      input.tgx101SaveUndo();
      input.tgx101RunBarAction(() -> input.setSpanLink(link));
      popup.hideWindow(false);
    };
    TextView done = chip(activity, Lang.getString(R.string.CreateLinkDone), v -> apply.run());
    LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(34f));
    doneParams.leftMargin = Screen.dp(4f);
    field.addView(done, doneParams);
    TextView cancel = chip(activity, "✕", v -> popup.hideWindow(false));
    field.addView(cancel, doneParams);
    url.setOnEditorActionListener((v, actionId, event) -> {
      apply.run();
      return true;
    });

    FrameLayout.LayoutParams fieldParams = new FrameLayout.LayoutParams(barWidth > 0 ? barWidth : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    wrap.addView(field, fieldParams);
    // where the bar was (screen coordinates → this layer's)
    wrap.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
      @Override
      public void onLayoutChange (View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
        int[] location = new int[2];
        wrap.getLocationOnScreen(location);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) field.getLayoutParams();
        int left = barX - location[0], top = barY - location[1];
        if (params.leftMargin != left || params.topMargin != top) {
          params.leftMargin = left;
          params.topMargin = top;
          field.setLayoutParams(params);
        }
      }
    });

    input.tgx101LinkOpen = true;
    if (window != null) {
      try { window.dismiss(); } catch (Throwable ignored) { }
      window = null;
    }
    popup.setDismissListener(p -> {
      input.tgx101LinkOpen = false;
      if (!applied[0]) {
        input.requestFocus(); // the keyboard goes straight back to the message
        input.setSelection(start, end);
      }
      if (input.hasSelection()) {
        reposition(); // the bar comes back to its place
      }
    });
    popup.showNonAnimatedView(wrap);
    url.requestFocus();
  }

  private int lastX, lastY, lastWidth;

  private void showAt (int x, int y, int width, int height) {
    lastX = x;
    lastY = y;
    lastWidth = width;
    if (window != null) {
      window.setContentView(content);
      window.update(x, y, width, height);
      content.requestLayout();
      return;
    }
    window = new PopupWindow(content, width, height, false);
    window.setTouchable(true);
    window.setOutsideTouchable(false);
    window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    window.setClippingEnabled(false);
    // no window of the system's own under the card: its square shadow showed past the rounded corners
    window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
    if (android.os.Build.VERSION.SDK_INT >= 21) window.setElevation(0f);
    try {
      window.showAtLocation(view, Gravity.TOP | Gravity.LEFT, x, y);
    } catch (Throwable t) {
      window = null;
    }
  }

  private static final class TailView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private boolean up;

    TailView (Context context, int color) {
      super(context);
      paint.setColor(color);
    }

    void setPointingUp (boolean up) {
      if (this.up != up) {
        this.up = up;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      float w = getWidth(), h = getHeight();
      path.reset();
      if (up) {
        path.moveTo(0, h); path.lineTo(w / 2f, 0); path.lineTo(w, h);
      } else {
        path.moveTo(0, 0); path.lineTo(w / 2f, h); path.lineTo(w, 0);
      }
      path.close();
      c.drawPath(path, paint);
    }
  }

  private static GradientDrawable rounded (int color, float radiusDp) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(Screen.dp(radiusDp));
    return drawable;
  }

  private static Drawable pressable (int color, float radiusDp) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
      return rounded(color, radiusDp); // Android 4: no ripple
    }
    return new RippleDrawable(ColorStateList.valueOf(ColorUtils.alphaColor(.18f, Theme.textAccentColor())), rounded(color, radiusDp), rounded(0xffffffff, radiusDp));
  }
}
