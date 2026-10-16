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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import java.lang.ref.WeakReference;

/**
 * TGx101 (approved mockup «Недавнее», variant Б): a row that slides. Left shows three buttons — the last folder, the
 * last tag, «More»; pulled all the way it puts the message in that folder at once. Right selects it. The row follows
 * the finger and springs back; one row is open at a time.
 */
public final class Tgx101SwipeRow extends FrameLayout {
  public interface Callback {
    void onFolder (boolean longPress);
    void onTag (boolean longPress);
    void onMore ();
    void onSelect ();
  }

  private static WeakReference<Tgx101SwipeRow> openRow;

  private final View content;
  private final LinearLayout actions;
  private final LinearLayout selectLayer;
  private final TextView folderName, tagName;
  private final Callback callback;
  private final int buttonWidth = Screen.dp(76f);
  private final int touchSlop;
  private boolean enabled = true;

  private float downX, downY, startTx;
  private boolean dragging, crossedFull, crossedSelect;
  private VelocityTracker velocity;

  public Tgx101SwipeRow (Context context, View content, Callback callback) {
    super(context);
    this.content = content;
    this.callback = callback;
    this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

    selectLayer = new LinearLayout(context);
    selectLayer.setOrientation(LinearLayout.HORIZONTAL);
    selectLayer.setGravity(Gravity.CENTER_VERTICAL);
    selectLayer.setPadding(Screen.dp(18f), 0, 0, 0);
    selectLayer.setBackgroundColor(0xff2e9d63);
    ImageView check = new ImageView(context);
    check.setImageResource(R.drawable.baseline_check_24);
    check.setColorFilter(0xffffffff);
    selectLayer.addView(check, new LinearLayout.LayoutParams(Screen.dp(22f), Screen.dp(22f)));
    TextView selectText = Tgx101SavedHomeController.text(context, 13f, 0xffffffff, true);
    selectText.setText(org.thunderdog.challegram.core.Lang.getString(R.string.Tgx101SavedSwipeSelect));
    selectText.setPadding(Screen.dp(8f), 0, 0, 0);
    selectLayer.addView(selectText);
    selectLayer.setVisibility(View.INVISIBLE);
    addView(selectLayer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    actions = new LinearLayout(context);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    actions.setGravity(Gravity.END);
    actions.setBackgroundColor(0xff5b8fbb);
    folderName = new TextView(context);
    tagName = new TextView(context);
    actions.addView(button(context, R.drawable.baseline_folder_24, folderName, 0xff5b8fbb, v -> fire(() -> callback.onFolder(false)), v -> fire(() -> callback.onFolder(true))));
    actions.addView(button(context, R.drawable.baseline_label_24, tagName, 0xff3f7cad, v -> fire(() -> callback.onTag(false)), v -> fire(() -> callback.onTag(true))));
    TextView moreLabel = new TextView(context);
    actions.addView(button(context, R.drawable.baseline_more_horiz_24, moreLabel, 0xff6b7a87, v -> fire(callback::onMore), null));
    moreLabel.setText(org.thunderdog.challegram.core.Lang.getString(R.string.Tgx101SavedMore));
    actions.setVisibility(View.INVISIBLE);
    addView(actions, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  /** «Работа / в папку», «#идеи / метка»: what one tap does (null — a sheet to choose) */
  public void setTargets (@Nullable String folder, @Nullable String tag) {
    String toFolder = org.thunderdog.challegram.core.Lang.getString(R.string.Tgx101SavedSwipeFolder);
    String toTag = org.thunderdog.challegram.core.Lang.getString(R.string.Tgx101SavedSwipeTag);
    folderName.setText(folder != null ? folder + "\n" + toFolder : org.thunderdog.challegram.core.Lang.getString(R.string.Tgx101SavedToFolder));
    tagName.setText(tag != null ? "#" + tag + "\n" + toTag : org.thunderdog.challegram.core.Lang.getString(R.string.Tgx101SavedTag));
  }

  private boolean selectEnabled = true;

  /** off (user 2026-10-16 «убираем свайп на недавних — выбор убрать»): a finger to the right is the screen's «back» */
  public void setSelectEnabled (boolean enabled) {
    this.selectEnabled = enabled;
  }

  public void setSwipeEnabled (boolean enabled) {
    this.enabled = enabled;
    if (!enabled) close(false);
  }

  private View button (Context context, int icon, TextView label, int color, View.OnClickListener click, @Nullable View.OnLongClickListener longClick) {
    LinearLayout b = new LinearLayout(context);
    b.setOrientation(LinearLayout.VERTICAL);
    b.setGravity(Gravity.CENTER);
    Views.setClickable(b);
    org.thunderdog.challegram.support.RippleSupport.setTransparentWhiteSelector(b);
    // the press shows over the button's own colour (the selector had replaced it: three buttons of one colour)
    android.graphics.drawable.Drawable press = b.getBackground();
    b.setBackground(press != null ? new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[] {new android.graphics.drawable.ColorDrawable(color), press}) : new android.graphics.drawable.ColorDrawable(color));
    b.setOnClickListener(click);
    if (longClick != null) b.setOnLongClickListener(longClick);
    ImageView i = new ImageView(context);
    i.setImageResource(icon);
    i.setColorFilter(0xffffffff);
    b.addView(i, new LinearLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f)));
    label.setTextColor(0xffffffff);
    label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11f);
    label.setGravity(Gravity.CENTER);
    label.setMaxLines(2);
    label.setEllipsize(TextUtils.TruncateAt.END);
    label.setPadding(Screen.dp(4f), Screen.dp(3f), Screen.dp(4f), 0);
    b.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    b.setLayoutParams(new LinearLayout.LayoutParams(buttonWidth, ViewGroup.LayoutParams.MATCH_PARENT));
    return b;
  }

  private boolean fire (Runnable r) {
    close(true);
    r.run();
    return true;
  }

  private int actionsWidth () {
    return buttonWidth * 3;
  }

  private float fullThreshold () {
    return Math.max(actionsWidth() + Screen.dp(48f), getWidth() * .62f);
  }

  private float selectThreshold () {
    return Screen.dp(84f);
  }

  public boolean isOpen () {
    return content.getTranslationX() != 0f;
  }

  public void close (boolean animated) {
    move(0f, animated);
  }

  private void move (float tx, boolean animated) {
    content.animate().cancel();
    if (animated) {
      android.view.ViewPropertyAnimator a = content.animate().translationX(tx).setDuration(220).setInterpolator(new DecelerateInterpolator(1.6f)).withEndAction(this::layers);
      if (android.os.Build.VERSION.SDK_INT >= 19) a.setUpdateListener(u -> layers());
      a.start();
    } else {
      content.setTranslationX(tx);
      layers();
    }
  }

  private void layers () {
    float tx = content.getTranslationX();
    actions.setVisibility(tx < 0 ? View.VISIBLE : View.INVISIBLE);
    selectLayer.setVisibility(tx > 0 ? View.VISIBLE : View.INVISIBLE);
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    if (!enabled) return false;
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        downX = e.getX();
        downY = e.getY();
        dragging = false;
        Tgx101SwipeRow open = openRow != null ? openRow.get() : null;
        if (open != null && open != this) open.close(true);
        // a tap on an open row's text closes it instead of opening the message
        if (isOpen() && downX < getWidth() + content.getTranslationX()) {
          startTx = content.getTranslationX();
          dragging = true;
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        float dx = e.getX() - downX, dy = e.getY() - downY;
        if (!selectEnabled && dx > 0 && !isOpen()) break; // left to the screen's swipe back
        if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
          beginDrag(e);
          return true;
        }
        break;
      }
    }
    return false;
  }

  private void beginDrag (MotionEvent e) {
    dragging = true;
    startTx = content.getTranslationX();
    downX = e.getX();
    crossedFull = crossedSelect = false;
    getParent().requestDisallowInterceptTouchEvent(true);
    content.setPressed(false);
    content.cancelLongPress();
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (!enabled) return super.onTouchEvent(e);
    if (velocity == null) velocity = VelocityTracker.obtain();
    velocity.addMovement(e);
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        return true;
      case MotionEvent.ACTION_MOVE: {
        if (!dragging) {
          // (user 2026-10-16 «свайпа вообще нет»): to the right on a closed row is the screen's «back» — the row doesn't
          // take the gesture (it used to ask the parents not to intercept, so «back» never saw it)
          if (!selectEnabled && e.getX() - downX > 0 && !isOpen()) return true;
          if (Math.abs(e.getX() - downX) > touchSlop) beginDrag(e);
          else return true;
        }
        float tx = startTx + (e.getX() - downX);
        float maxRight = selectThreshold() * 1.25f;
        if (tx > maxRight) tx = maxRight + (tx - maxRight) * .25f; // resists past the selection point
        if (!selectEnabled) tx = Math.min(0f, tx);
        tx = Math.max(-getWidth(), tx);
        content.setTranslationX(tx);
        layers();
        boolean full = -tx > fullThreshold();
        if (full != crossedFull) {
          crossedFull = full;
          UI.forceVibrate(this, false);
          actions.animate().alpha(full ? .85f : 1f).setDuration(120).start();
        }
        boolean select = tx > selectThreshold();
        if (select != crossedSelect) {
          crossedSelect = select;
          UI.forceVibrate(this, false);
        }
        return true;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        float tx = content.getTranslationX();
        velocity.computeCurrentVelocity(1000);
        float vx = velocity.getXVelocity();
        velocity.recycle();
        velocity = null;
        boolean moved = Math.abs(e.getX() - downX) > touchSlop;
        actions.setAlpha(1f);
        if (!moved && startTx != 0f) {
          close(true); // a tap on an open row
        } else if (selectEnabled && e.getActionMasked() == MotionEvent.ACTION_UP && tx > selectThreshold()) {
          close(true);
          callback.onSelect();
        } else if (e.getActionMasked() == MotionEvent.ACTION_UP && -tx > fullThreshold()) {
          close(true);
          callback.onFolder(false);
        } else if (tx < 0 && (-tx > actionsWidth() / 2f || vx < -Screen.dp(900f)) && vx < Screen.dp(600f)) {
          move(-actionsWidth(), true);
          openRow = new WeakReference<>(this);
        } else {
          close(true);
        }
        dragging = false;
        return true;
      }
    }
    return true;
  }

  /** closes the row that is open, if any (a scroll, a new screen) */
  public static void closeOpen () {
    Tgx101SwipeRow open = openRow != null ? openRow.get() : null;
    if (open != null) open.close(true);
  }
}
