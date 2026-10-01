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
    final String title;
    final Runnable onClick;

    public Action (String title, Runnable onClick) {
      this.title = title;
      this.onClick = onClick;
    }
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
    if (hiddenWhileDragging) {
      content.setAlpha(0f);
      return;
    }
    reposition();
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
    build(top);
    if (hiddenWhileDragging) {
      content.setAlpha(0f); // still dragging — the system re-asks the menu, don't pop the bar back over the magnifier
    }
    reposition();
  }

  public void dismiss () {
    view.removeCallbacks(showAfterDrag);
    hiddenWhileDragging = false;
    if (window != null) {
      try { window.dismiss(); } catch (Throwable ignored) { }
      window = null;
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
    if (content != null && window != null) {
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

  /** The drag is over (the in-bubble handles know it): the bar comes back right away */
  public void showNow () {
    if (!hiddenWhileDragging) return;
    view.removeCallbacks(showAfterDrag);
    showAfterDrag.run();
  }

  // Colours: the editor window's panel and its white buttons

  private static int panelColor () {
    return ColorUtils.compositeColor(Theme.fillingColor(), ColorUtils.alphaColor(.07f, Theme.textAccentColor()));
  }

  private void build (List<MenuItem> systemItems) {
    Context context = view.getContext();
    int panel = panelColor();
    card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    int pad = Screen.dp(5f);
    card.setPadding(pad, pad, pad, pad);
    card.setBackground(rounded(panel, 14f));
    card.setElevation(Screen.dp(6f));

    HorizontalScrollView scroll = new HorizontalScrollView(context);
    scroll.setHorizontalScrollBarEnabled(false);
    scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
    scroll.setFadingEdgeLength(Screen.dp(22f));
    scroll.setHorizontalFadingEdgeEnabled(true);
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    for (Action action : leadingActions) {
      row.addView(chip(context, action.title, v -> action.onClick.run()), chipParams());
    }
    for (MenuItem item : systemItems) {
      CharSequence title = item.getItemId() == android.R.id.selectAll ? Lang.getString(R.string.Tgx101SelectAllFull) : item.getTitle();
      row.addView(chip(context, title != null ? title.toString() : "", v -> invoke(item)), chipParams());
    }
    scroll.addView(row);
    card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(30f)));

    if (withFormatting && textView instanceof InputView) {
      InputView input = (InputView) view;
      LinearLayout icons = new LinearLayout(context);
      icons.setOrientation(LinearLayout.HORIZONTAL);
      for (int entry : Tgx101FormatMenuController.getOrder()) {
        if (entry <= 0) continue;
        final int id = entry;
        ImageView icon = new ImageView(context);
        icon.setImageResource(Tgx101FormatMenuController.iconOf(id));
        icon.setColorFilter(Theme.textAccentColor());
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setBackground(pressable(0, 9f));
        icon.setContentDescription(Lang.getString(Tgx101FormatMenuController.nameOf(id)));
        icon.setOnClickListener(v -> {
          if (id == R.id.btn_link) {
            onLink(input);
            return;
          }
          input.tgx101SaveUndo();
          if (id == R.id.btn_plain) {
            input.setSpan(id);
          } else {
            input.tgx101ToggleSpan(id);
          }
        });
        icons.addView(icon, new LinearLayout.LayoutParams(0, Screen.dp(30f), 1f));
      }
      LinearLayout.LayoutParams iconsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(30f));
      iconsParams.topMargin = Screen.dp(3f);
      card.addView(icons, iconsParams);

      // «Undo» right under the last icon (Clear formatting), in a cell of the same width
      LinearLayout undoRow = new LinearLayout(context);
      undoRow.setOrientation(LinearLayout.HORIZONTAL);
      undoRow.setGravity(Gravity.END);
      undoRow.setWeightSum(Math.max(1, icons.getChildCount()));
      ImageView undo = new ImageView(context);
      undo.setImageResource(R.drawable.baseline_undo_24);
      undo.setColorFilter(Theme.textAccentColor());
      undo.setScaleType(ImageView.ScaleType.CENTER);
      undo.setBackground(pressable(Theme.fillingColor(), 9f));
      undo.setContentDescription(Lang.getString(R.string.Tgx101Undo));
      undo.setOnClickListener(v -> {
        if (!input.tgx101Undo()) {
          org.thunderdog.challegram.tool.UI.showToast(R.string.Tgx101NothingToUndo, android.widget.Toast.LENGTH_SHORT);
        }
      });
      undoRow.addView(undo, new LinearLayout.LayoutParams(0, Screen.dp(28f), 1f));
      LinearLayout.LayoutParams undoParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(28f));
      undoParams.topMargin = Screen.dp(3f);
      card.addView(undoRow, undoParams);
    }

    content = new FrameLayout(context);
    content.setClipChildren(false);
    content.setClipToPadding(false);
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
    button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
    button.setTypeface(Fonts.getRobotoRegular());
    button.setTextColor(Theme.textAccentColor());
    button.setPadding(Screen.dp(11f), 0, Screen.dp(11f), 0);
    button.setBackground(pressable(Theme.fillingColor(), 9f));
    button.setOnClickListener(onClick);
    return button;
  }

  private static LinearLayout.LayoutParams chipParams () {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(30f));
    params.rightMargin = Screen.dp(4f);
    return params;
  }

  private void invoke (MenuItem item) {
    Menu menu = this.menu;
    TextView view = this.textView;
    if (view == null) return;
    int id = item.getItemId();
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
    int width = Math.min(Screen.dp(300f), screenWidth - Screen.dp(24f));
    content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    int cardHeight = card.getMeasuredHeight();
    int tailHeight = Screen.dp(8f);
    int height = cardHeight + tailHeight;

    int x = Math.max(Screen.dp(12f), Math.min(screenWidth - width - Screen.dp(12f), anchorX - width / 2));
    int y = anchorTop - height - Screen.dp(2f);
    boolean below = y < Screen.getStatusBarHeight() + Screen.dp(4f);
    if (below) {
      y = anchorBottom + Screen.dp(26f); // under the bottom handle
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
      input.removeSpan(new org.drinkless.tdlib.TdApi.TextEntityTypeTextUrl(links[0].getURL()));
      input.setSelection(start, end);
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
    field.setBackground(rounded(panelColor(), 14f));
    field.setElevation(Screen.dp(6f));
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
      input.setSpanLink(link);
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
    return new RippleDrawable(ColorStateList.valueOf(ColorUtils.alphaColor(.18f, Theme.textAccentColor())), rounded(color, radiusDp), rounded(0xffffffff, radiusDp));
  }
}
