/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.widget.FrameLayoutFix;

public class TopBarView extends FrameLayoutFix {
  private final ImageView topDismissButton;
  private final LinearLayout actionsList;

  private boolean canDismiss;

  public interface DismissListener {
    void onDismissRequest (TopBarView barView);
  }

  public static class Item {
    final int id;
    final int stringRes;
    final View.OnClickListener onClickListener;

    boolean isNegative;
    boolean noDismiss;

    public Item (int id, int stringRes, View.OnClickListener onClickListener) {
      this.id = id;
      this.stringRes = stringRes;
      this.onClickListener = onClickListener;
    }

    public Item setIsNegative () {
      this.isNegative = true;
      return this;
    }

    public Item setNoDismiss () {
      this.noDismiss = true;
      return this;
    }
  }

  private DismissListener dismissListener;

  public TopBarView (@NonNull Context context) {
    super(context);

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(36f)));
    ViewSupport.setThemedBackground(this, ColorId.filling, null);

    actionsList = new LinearLayout(context);
    actionsList.setOrientation(LinearLayout.HORIZONTAL);
    actionsList.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Lang.gravity() | Gravity.TOP));
    addView(actionsList);

    topDismissButton = new ImageView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        return Views.isValid(this) && super.onTouchEvent(event);
      }
    };
    topDismissButton.setOnClickListener(view -> {
      if (dismissListener != null) {
        dismissListener.onDismissRequest(this);
      }
    });
    topDismissButton.setScaleType(ImageView.ScaleType.CENTER);
    topDismissButton.setColorFilter(Theme.iconColor());
    topDismissButton.setImageResource(R.drawable.baseline_close_18);
    topDismissButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(40f), ViewGroup.LayoutParams.MATCH_PARENT, Lang.gravity() | Gravity.TOP));
    topDismissButton.setBackgroundResource(R.drawable.bg_btn_header);
    Views.setClickable(topDismissButton);
    topDismissButton.setVisibility(View.INVISIBLE);
    addView(topDismissButton);
  }

  public void setDismissListener (DismissListener dismissListener) {
    this.dismissListener = dismissListener;
  }

  private @Nullable ViewController<?> themeProvider;

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    this.themeProvider = themeProvider;
    if (themeProvider != null) {
      themeProvider.addThemeFilterListener(topDismissButton, ColorId.icon);
      themeProvider.addThemeInvalidateListener(this);
    }
  }

  public void setCanDismiss (boolean canDismiss) {
    if (this.canDismiss != canDismiss) {
      this.canDismiss = canDismiss;
      topDismissButton.setVisibility(canDismiss ? View.VISIBLE : View.GONE);
    }
  }

  // TGx101 (user 2026-10-05, variant 1): «Block» / «Add contact» as two short chips with icons in normal case,
  // centered — the capitalised full-width labels were cut («ЗАБЛОКИРОВА…», «ДОБАВИТЬ КОНТА…»)

  private static boolean tgx101Chip (Item item) {
    return item.id == R.id.btn_addContact || (item.id == R.id.btn_reportChat && item.stringRes == R.string.BlockContact);
  }

  private View tgx101NewChip (Item item, int textColorId) {
    boolean block = item.id == R.id.btn_reportChat;
    int color = Theme.getColor(textColorId);
    TextView chip = new TextView(getContext());
    chip.setId(item.id);
    chip.setText(Lang.getString(block ? R.string.Tgx101ChipBlock : R.string.Tgx101ChipAddContact));
    chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13f);
    chip.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoMedium());
    chip.setTextColor(color);
    chip.setSingleLine(true);
    chip.setGravity(Gravity.CENTER);
    android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(getContext(), block ? R.drawable.baseline_block_18 : R.drawable.baseline_person_add_24);
    if (icon != null) {
      icon = icon.mutate();
      int size = Screen.dp(16f);
      icon.setBounds(0, 0, size, size);
      icon.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
      chip.setCompoundDrawables(icon, null, null, null);
      chip.setCompoundDrawablePadding(Screen.dp(5f));
    }
    chip.setPadding(Screen.dp(10f), 0, Screen.dp(12f), 0);
    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
    bg.setColor(me.vkryl.core.ColorUtils.alphaColor(.12f, color));
    bg.setCornerRadius(Screen.dp(14f));
    if (android.os.Build.VERSION.SDK_INT >= 21) {
      chip.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(me.vkryl.core.ColorUtils.alphaColor(.2f, color)), bg, null));
    } else {
      chip.setBackgroundDrawable(bg); // Android 4.x: no ripple
    }
    chip.setOnClickListener(item.onClickListener);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(28f));
    params.gravity = Gravity.CENTER_VERTICAL;
    params.leftMargin = params.rightMargin = Screen.dp(4f);
    chip.setLayoutParams(params);
    return chip;
  }

  public void setItems (Item... items) {
    for (int i = 0; i < actionsList.getChildCount(); i++) {
      View view = actionsList.getChildAt(i);
      if (view != null && themeProvider != null) {
        themeProvider.removeThemeListenerByTarget(view);
      }
    }
    actionsList.removeAllViews();
    boolean tgx101AllChips = items.length > 0;
    for (Item item : items) if (!tgx101Chip(item)) tgx101AllChips = false;
    actionsList.setGravity(tgx101AllChips ? Gravity.CENTER : Gravity.NO_GRAVITY);
    if (tgx101AllChips) {
      // chips only: centered, no spacers
    } else if (items.length > 1) {
      View offsetView = new View(getContext());
      offsetView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, .75f));
      actionsList.addView(offsetView);
    }
    boolean canDismiss = false;
    for (Item item : items) {
      if (!item.noDismiss) {
        canDismiss = true;
      }
      int textColorId = item.isNegative ? ColorId.textNegative : ColorId.textNeutral;
      if (tgx101Chip(item)) {
        actionsList.addView(tgx101NewChip(item, textColorId));
        continue;
      }
      TextView button = Views.newTextView(getContext(), 15f, Theme.getColor(textColorId), Gravity.CENTER, Views.TEXT_FLAG_BOLD | Views.TEXT_FLAG_HORIZONTAL_PADDING);
      button.setId(item.id);
      if (themeProvider != null) {
        themeProvider.addThemeTextColorListener(button, textColorId);
      }
      button.setEllipsize(TextUtils.TruncateAt.END);
      button.setSingleLine(true);
      button.setBackgroundResource(R.drawable.bg_btn_header);
      button.setOnClickListener(item.onClickListener);
      Views.setMediumText(button, Lang.uppercase(Lang.getString(item.stringRes)));
      Views.setClickable(button);
      button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 2f));
      actionsList.addView(button);
    }
    if (items.length > 1 && !tgx101AllChips) {
      View offsetView = new View(getContext());
      offsetView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, .75f));
      actionsList.addView(offsetView);
    }
    setCanDismiss(canDismiss);
  }
}
