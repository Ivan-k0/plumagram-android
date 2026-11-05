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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The menu of a held button (approved mockups Е, Е2, user 2026-10-16): a window of its own over everything, blurred and dimmed
 * behind, a card beside the finger. The same finger slides to an item and lifts to pick it; resting on an item with a list
 * turns the card into that list in the same place. Held «Send» under the note field and the music player's ♥.
 */
abstract class Tgx101HoldMenu {
  protected final ViewController<?> c;
  private final View anchor;
  private final @Nullable View.OnTouchListener anchorTouch;
  private final PopupLayout popup;
  private final Drawable background;
  private final FrameLayout root;
  protected final LinearLayout card;
  private final List<View> rows = new ArrayList<>();
  private final List<Runnable> hovers = new ArrayList<>();
  private boolean dismissing, armed = true, moved;
  private View hit;
  private float startX, startY;
  private Runnable hoverOpen;
  private View hoverRow;

  /**
   * @param beside false: at the bottom of the screen, by the button that is there with the keyboard; true: at the height of the button
   * @param anchorTouch what the button listens to once the finger is gone
   */
  Tgx101HoldMenu (ViewController<?> c, View anchor, boolean beside, @Nullable View.OnTouchListener anchorTouch) {
    this(c, anchor, beside, anchorTouch, false);
  }

  /** @param rightOfAnchor the button stands at the left edge: the card goes to the right of it */
  Tgx101HoldMenu (ViewController<?> c, View anchor, boolean beside, @Nullable View.OnTouchListener anchorTouch, boolean rightOfAnchor) {
    this.c = c;
    this.anchor = anchor;
    this.anchorTouch = anchorTouch;
    Context context = c.context();
    popup = new PopupLayout(context);
    popup.init(true);
    // a window of its own, over the keyboard (not above it): nothing to reach for
    popup.setOverlayStatusBar(true);
    final boolean systemBlur = Settings.instance().tgx101MenuBlur() && PopupLayout.tgx101SystemBlurAvailable(context);
    popup.tgx101SetSystemBlur(systemBlur);
    background = new android.graphics.drawable.ColorDrawable(systemBlur ? 0x33000000 : 0x66000000);
    popup.tgx101Background = background;
    background.setAlpha(0);

    root = new FrameLayout(context);
    root.setBackground(background);
    root.setOnClickListener(v -> dismiss());

    card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Theme.getColor(ColorId.filling));
    bg.setCornerRadius(Screen.dp(16f));
    bg.setStroke(Math.max(1, Screen.dp(.5f)), Theme.getColor(ColorId.separator));
    card.setBackground(bg);
    card.setPadding(0, Screen.dp(6f), 0, Screen.dp(6f));
    card.setClickable(true);
    // left of the button, at its height: where the thumb is
    int[] at = new int[2];
    anchor.getLocationOnScreen(at);
    int navigationInset = Settings.instance().useEdgeToEdge() && c.context().getRootView().getSystemInsetsWithoutIme() != null ? c.context().getRootView().getSystemInsetsWithoutIme().bottom : 0;
    int cardWidth = Math.min(Screen.dp(250f), Screen.currentWidth() - Screen.dp(80f));
    FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, (beside ? Gravity.TOP : Gravity.BOTTOM) | (rightOfAnchor ? Gravity.LEFT : Gravity.RIGHT));
    if (rightOfAnchor) {
      cp.leftMargin = Math.min(at[0] + anchor.getWidth() + Screen.dp(4f), Math.max(Screen.dp(12f), Screen.currentWidth() - cardWidth - Screen.dp(12f)));
    } else {
      cp.rightMargin = Math.max(Screen.dp(12f), Screen.currentWidth() - at[0] + Screen.dp(4f));
    }
    if (beside) {
      // A longer list grows up from the finger and stays on the screen whatever its length
      final int cardBottom = at[1] + anchor.getHeight() / 2 + Screen.dp(56f);
      card.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
        int[] rootAt = new int[2];
        root.getLocationOnScreen(rootAt);
        int height = bottom - top;
        int minY = org.thunderdog.challegram.navigation.HeaderView.getTopOffset() + Screen.dp(12f);
        int maxY = root.getHeight() - height - navigationInset - Screen.dp(12f);
        v.setTranslationY(Math.max(minY, Math.min(maxY, cardBottom - rootAt[1] - height)));
      });
    } else {
      cp.bottomMargin = Screen.dp(24f) + navigationInset;
    }
    root.addView(card, cp);
  }

  /** Called once by the menu itself, when it's ready to fill the first page */
  protected final void open () {
    mainPage();

    FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) card.getLayoutParams();
    card.setPivotX((cp.gravity & Gravity.LEFT) == Gravity.LEFT && (cp.gravity & Gravity.RIGHT) != Gravity.RIGHT ? 0 : cp.width);
    card.setAlpha(0f);
    card.setScaleX(.92f);
    card.setScaleY(.92f);
    card.post(() -> {
      card.setPivotY(card.getHeight());
      card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    });
    android.animation.ValueAnimator fadeIn = android.animation.ValueAnimator.ofInt(0, 255);
    fadeIn.setDuration(200);
    fadeIn.addUpdateListener(a -> {
      background.setAlpha((int) a.getAnimatedValue());
      popup.tgx101SetBlurFactor((int) a.getAnimatedValue() / 255f);
    });
    fadeIn.start();
    popup.showNonAnimatedView(root);

    // the finger that held the button goes on to the menu
    startX = -1;
    anchor.setOnTouchListener((v, e) -> {
      if (!armed) return false;
      int action = e.getActionMasked();
      drag(e.getRawX(), e.getRawY(), action);
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        armed = false;
        v.setOnTouchListener(anchorTouch);
        v.setPressed(false); // the button never saw its finger lift (test 2026-10-16: it stayed pressed)
      }
      return true;
    });
  }

  // Pages

  protected abstract void mainPage ();

  protected final void page (Runnable fill) {
    if (card.getChildCount() == 0) {
      fill.run();
      return;
    }
    // the new list takes the old one's place (user: the keyboard doesn't blink or jump — only the card changes)
    card.animate().cancel();
    card.animate().alpha(0f).setDuration(90).withEndAction(() -> {
      card.removeAllViews();
      rows.clear();
      hovers.clear();
      fill.run();
      card.animate().alpha(1f).setDuration(120).start();
    }).start();
  }

  protected final void back (int title) {
    row(R.drawable.baseline_arrow_back_24, 0, Lang.getString(title), false, () -> page(this::mainPage), null);
    separator();
  }

  /** The quick reactions over the first page */
  protected final void reactions (me.vkryl.core.lambda.RunnableData<String> click) {
    Context context = c.context();
    LinearLayout reactions = new LinearLayout(context);
    reactions.setOrientation(LinearLayout.HORIZONTAL);
    reactions.setPadding(Screen.dp(8f), Screen.dp(2f), Screen.dp(8f), Screen.dp(6f));
    for (String e : Tgx101SavedKit.quickReactions()) {
      // seven in a menu 250 dp wide: smaller and closer than four
      final boolean many = Tgx101SavedKit.quickReactions().length > 5;
      TextView b = Tgx101SavedHomeController.text(context, many ? 16f : 19f, Theme.textAccentColor(), false);
      b.setText(Tgx101SavedKit.emoji(e));
      b.setGravity(Gravity.CENTER);
      b.setBackground(Tgx101SavedHomeController.card(Theme.backgroundColor(), 18f));
      Views.setClickable(b);
      b.setOnClickListener(v -> click.runWithData(e));
      LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, Screen.dp(38f), 1f);
      rp.setMargins(Screen.dp(many ? 1.5f : 3f), 0, Screen.dp(many ? 1.5f : 3f), 0);
      reactions.addView(b, rp);
      rows.add(b);
      hovers.add(null);
    }
    card.addView(reactions);
  }

  // Views

  protected final void row (int icon, int swatch, String text, boolean more, Runnable click, @Nullable Runnable hover) {
    Context context = c.context();
    LinearLayout r = new LinearLayout(context);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setPadding(Screen.dp(16f), 0, Screen.dp(12f), 0);
    Views.setClickable(r);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(r);
    r.setOnClickListener(v -> click.run());
    if (swatch != 0) {
      ImageView f = new ImageView(context);
      f.setImageResource(R.drawable.baseline_folder_24);
      f.setColorFilter(swatch);
      r.addView(f, new LinearLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f)));
    } else {
      ImageView i = new ImageView(context);
      i.setImageResource(icon);
      i.setColorFilter(Theme.getColor(ColorId.icon));
      r.addView(i, new LinearLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f)));
    }
    TextView t = Tgx101SavedHomeController.text(context, 15f, Theme.textAccentColor(), false);
    t.setText(text);
    t.setSingleLine(true);
    t.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    tp.leftMargin = Screen.dp(14f);
    r.addView(t, tp);
    if (more) {
      ImageView m = new ImageView(context);
      m.setImageResource(R.drawable.round_keyboard_arrow_right_24);
      m.setColorFilter(Theme.textDecentColor());
      r.addView(m, new LinearLayout.LayoutParams(Screen.dp(18f), Screen.dp(18f)));
    }
    card.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(44f)));
    rows.add(r);
    hovers.add(hover);
  }

  protected final void separator () {
    View s = new View(c.context());
    s.setBackgroundColor(Theme.separatorColor());
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Screen.dp(.5f)));
    p.setMargins(0, Screen.dp(4f), 0, Screen.dp(4f));
    card.addView(s, p);
  }

  // The finger

  private void drag (float rawX, float rawY, int action) {
    if (dismissing) return;
    if (startX < 0) {
      startX = rawX;
      startY = rawY;
    }
    if (!moved && Math.hypot(rawX - startX, rawY - startY) > Screen.dp(12f)) moved = true;
    View found = null;
    int foundIndex = -1;
    int[] loc = new int[2];
    for (int i = 0; i < rows.size(); i++) {
      View r = rows.get(i);
      if (!r.isShown()) continue;
      r.getLocationOnScreen(loc);
      if (rawX >= loc[0] && rawX <= loc[0] + r.getWidth() && rawY >= loc[1] && rawY <= loc[1] + r.getHeight()) {
        found = r;
        foundIndex = i;
        break;
      }
    }
    if (found != hit) {
      if (hit != null) hit.setPressed(false);
      hit = found;
      if (hoverOpen != null && hoverRow != null) hoverRow.removeCallbacks(hoverOpen);
      hoverOpen = null;
      hoverRow = null;
      if (found != null) {
        found.setPressed(true);
        org.thunderdog.challegram.tool.Tgx101Haptics.play(org.thunderdog.challegram.tool.Tgx101Haptics.TICK);
        Runnable hover = hovers.get(foundIndex);
        if (hover != null) { // resting on «…» opens its list in the same place
          hoverRow = found;
          hoverOpen = () -> {
            hoverOpen = null;
            if (!dismissing && hit == hoverRow) {
              hoverRow.setPressed(false);
              hit = null;
              hover.run();
            }
          };
          found.postDelayed(hoverOpen, 380);
        }
      }
    }
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      if (hoverOpen != null && hoverRow != null) hoverRow.removeCallbacks(hoverOpen);
      View r = hit;
      hit = null;
      if (r != null) {
        r.setPressed(false);
        if (moved && action == MotionEvent.ACTION_UP) r.performClick();
      }
    }
  }

  /** (user 2026-11-05 «не могу создать папку из лайка»): a sheet asked for while the menu was fading went with the menu's window */
  protected final void dismissThen (Runnable after) {
    afterDismiss = after;
    dismiss();
  }

  private @Nullable Runnable afterDismiss;

  public final void dismiss () {
    if (dismissing) return;
    dismissing = true;
    armed = false;
    android.animation.ValueAnimator fadeOut = android.animation.ValueAnimator.ofInt(255, 0);
    fadeOut.setDuration(160);
    fadeOut.addUpdateListener(a -> {
      background.setAlpha((int) a.getAnimatedValue());
      popup.tgx101SetBlurFactor((int) a.getAnimatedValue() / 255f);
    });
    fadeOut.addListener(new android.animation.AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (android.animation.Animator animation) {
        popup.hideWindow(false);
        Runnable after = afterDismiss;
        afterDismiss = null;
        if (after != null) org.thunderdog.challegram.tool.UI.post(after);
      }
    });
    card.animate().cancel();
    card.animate().alpha(0f).scaleX(.96f).scaleY(.96f).setDuration(140).start();
    fadeOut.start();
  }
}
