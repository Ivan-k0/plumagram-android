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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101 (approved mockups Е, Е2, user 2026-10-16): holding «Send» under the note field opens a menu over the keyboard,
 * at the finger, like the message menu in the chats — the keyboard stays where it is, blurred. The same finger slides to
 * an item and lifts to pick it; resting on «To folder…» / «With a tag…» / «Remind…» turns the menu into that list in the
 * same place.
 */
public final class Tgx101SendMenu {
  private final ViewController<?> c;
  private final TdApi.FormattedText note;
  private final Runnable picked, sentHere;
  private final PopupLayout popup;
  private final Drawable background;
  private final LinearLayout card;
  private final List<View> rows = new ArrayList<>();
  private final List<Runnable> hovers = new ArrayList<>();
  private boolean dismissing, armed = true, moved;
  private View hit;
  private float startX, startY;
  private Runnable hoverOpen;
  private View hoverRow;

  public static Tgx101SendMenu show (ViewController<?> c, View anchor, TdApi.FormattedText note, Runnable picked, Runnable sentHere) {
    return new Tgx101SendMenu(c, anchor, note, picked, sentHere);
  }

  private Tgx101SendMenu (ViewController<?> c, View anchor, TdApi.FormattedText note, Runnable picked, Runnable sentHere) {
    this.c = c;
    this.note = note;
    this.picked = picked;
    this.sentHere = sentHere;
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

    FrameLayout root = new FrameLayout(context);
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
    // left of the send button, at its height: where the thumb is (over the keyboard)
    int[] at = new int[2];
    anchor.getLocationOnScreen(at);
    int navigationInset = Settings.instance().useEdgeToEdge() && c.context().getRootView().getSystemInsetsWithoutIme() != null ? c.context().getRootView().getSystemInsetsWithoutIme().bottom : 0;
    FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Math.min(Screen.dp(250f), Screen.currentWidth() - Screen.dp(80f)), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
    cp.rightMargin = Math.max(Screen.dp(12f), Screen.currentWidth() - at[0] + Screen.dp(4f));
    cp.bottomMargin = Screen.dp(24f) + navigationInset;
    root.addView(card, cp);

    mainPage();

    card.setPivotX(cp.width);
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
        v.setOnTouchListener(null);
        v.setPressed(false); // the button never saw its finger lift (test 2026-10-16: it stayed pressed)
      }
      return true;
    });
  }

  // Pages

  private void page (Runnable fill) {
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

  private void mainPage () {
    Context context = c.context();
    Tgx101SavedStore store = Tgx101SavedStore.get(c.tdlib());
    LinearLayout reactions = new LinearLayout(context);
    reactions.setOrientation(LinearLayout.HORIZONTAL);
    reactions.setPadding(Screen.dp(8f), Screen.dp(2f), Screen.dp(8f), Screen.dp(6f));
    for (String e : Tgx101SavedKit.QUICK_REACTIONS) {
      TextView b = Tgx101SavedHomeController.text(context, 19f, Theme.textAccentColor(), false);
      b.setText(e);
      b.setGravity(Gravity.CENTER);
      b.setBackground(Tgx101SavedHomeController.card(Theme.backgroundColor(), 18f));
      Views.setClickable(b);
      b.setOnClickListener(v -> pick(() -> Tgx101SavedActions.sendNote(c, note, null, saved -> Tgx101SavedKit.toggleReaction(c.tdlib(), saved, e, added -> sentHere.run()))));
      LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, Screen.dp(38f), 1f);
      rp.setMargins(Screen.dp(3f), 0, Screen.dp(3f), 0);
      reactions.addView(b, rp);
      rows.add(b);
      hovers.add(null);
    }
    card.addView(reactions);

    Tgx101SavedStore.Folder last = store.folder(store.lastFolderId);
    if (last != null) {
      row(0, last.color(), Lang.getString(R.string.Tgx101SendToFolder, last.name), false, () -> pick(() -> toFolder(last)), null);
    }
    row(R.drawable.baseline_folder_24, 0, Lang.getString(R.string.Tgx101SendToFolderMore), true, this::foldersPage, this::foldersPage);
    if (store.lastTag != null && !store.lastTag.isEmpty()) {
      final String tag = store.lastTag;
      row(R.drawable.baseline_label_24, 0, Lang.getString(R.string.Tgx101SendWithTag, tag), false, () -> pick(() -> withTag(tag)), null);
    }
    row(R.drawable.baseline_label_24, 0, Lang.getString(R.string.Tgx101SendWithTagMore), true, this::tagsPage, this::tagsPage);
    separator();
    row(R.drawable.baseline_schedule_24, 0, Lang.getString(R.string.Tgx101SavedNoteRemind), true, this::remindPage, this::remindPage);
  }

  private void foldersPage () {
    page(() -> {
      back(R.string.Tgx101SavedToFolder);
      for (Tgx101SavedStore.Folder f : Tgx101SavedStore.get(c.tdlib()).folders()) {
        row(0, f.color(), f.name, false, () -> pick(() -> toFolder(f)), null);
      }
      separator();
      row(R.drawable.baseline_create_new_folder_24, 0, Lang.getString(R.string.Tgx101SavedNewFolder), false, () -> {
        dismiss();
        Tgx101SavedActions.askName(c, Lang.getString(R.string.Tgx101SavedNewFolder), null, name -> {
          picked.run();
          toFolder(Tgx101SavedStore.get(c.tdlib()).createFolder(name));
        });
      }, null);
    });
  }

  private void tagsPage () {
    page(() -> {
      back(R.string.Tgx101SavedTag);
      for (String t : Tgx101SavedStore.get(c.tdlib()).knownTags()) {
        row(0, Tgx101SavedListController.TAG_COLOR, "#" + t, false, () -> pick(() -> withTag(t)), null);
      }
      separator();
      row(R.drawable.baseline_add_24, 0, Lang.getString(R.string.Tgx101SendNewTag), false, () -> pick(() -> Tgx101SavedActions.sendNote(c, note, null, saved -> {
        sentHere.run();
        Tgx101SavedActions.tag(c, saved, sentHere);
      })), null);
    });
  }

  private void remindPage () {
    page(() -> {
      back(R.string.Tgx101SavedRemind);
      long[] at = Tgx101SavedActions.remindTimes();
      int[] names = Tgx101SavedActions.remindNames(at.length);
      for (int i = 0; i < at.length; i++) {
        final long when = at[i];
        row(R.drawable.baseline_schedule_24, 0, Lang.getString(names[i]) + " · " + Lang.getRelativeTimestamp(when, java.util.concurrent.TimeUnit.SECONDS), false, () -> pick(() -> {
          TdApi.MessageSendOptions options = new TdApi.MessageSendOptions();
          options.schedulingState = new TdApi.MessageSchedulingStateSendAtDate((int) when, 0);
          Tgx101SavedActions.sendNote(c, note, options, saved -> UI.showToast(Lang.getString(R.string.Tgx101SavedRemindDone, Lang.getRelativeTimestamp(when, java.util.concurrent.TimeUnit.SECONDS)), android.widget.Toast.LENGTH_SHORT));
        }), null);
      }
    });
  }

  private void back (int title) {
    row(R.drawable.baseline_arrow_back_24, 0, Lang.getString(title), false, () -> page(this::mainPage), null);
    separator();
  }

  // Picks

  private void toFolder (Tgx101SavedStore.Folder f) {
    Tgx101SavedActions.sendNote(c, note, null, saved -> {
      Tgx101SavedStore.get(c.tdlib()).putInFolder(f.id, Tgx101SavedKit.idsOf(saved));
      UI.showToast(Lang.getString(R.string.Tgx101SavedPutDone, f.name), android.widget.Toast.LENGTH_SHORT);
      sentHere.run();
    });
  }

  private void withTag (String tag) {
    Tgx101SavedActions.sendNote(c, note, null, saved -> {
      Tgx101SavedActions.applyTag(c, saved, tag, false);
      sentHere.run();
    });
  }

  /** the field is emptied only when the note goes somewhere */
  private void pick (Runnable action) {
    dismiss();
    picked.run();
    action.run();
  }

  // Views

  private void row (int icon, int swatch, String text, boolean more, Runnable click, @Nullable Runnable hover) {
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

  private void separator () {
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

  public void dismiss () {
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
      }
    });
    card.animate().cancel();
    card.animate().alpha(0f).scaleX(.96f).scaleY(.96f).setDuration(140).start();
    fadeOut.start();
  }
}
