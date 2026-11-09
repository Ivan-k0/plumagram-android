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
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

/**
 * TGx101 (approved mockup «Окна Material 3», user 2026-11-01): the option windows in the Material 3 manner.
 * <ul>
 *   <li>A question with one or two actions and «Cancel» is a dialog in the middle of the screen: 28 dp corners, the
 *   action's icon on top, the question large, text buttons at the bottom right, a dangerous action in red.</li>
 *   <li>A choice (one of the titles ends with «✓» — the current one) is a sheet from the bottom with radio buttons,
 *   the current choice highlighted; it stands above the navigation buttons.</li>
 * </ul>
 * Every other options window keeps the card from the bottom.
 */
public final class Tgx101M3Sheets {
  public static final boolean ENABLED = true;

  private static final String CHECK_MARK = "✓";

  private Tgx101M3Sheets () { }

  /** The window for these options, or null when they stay the usual card */
  public static @Nullable PopupLayout show (ViewController<?> controller, ViewController.Options options, @Nullable OptionDelegate delegate, @Nullable ThemeDelegate forcedTheme, int bottomInset) {
    if (!ENABLED || options.subtitle != null || options.items == null) return null;
    List<ViewController.OptionItem> items = new ArrayList<>();
    for (ViewController.OptionItem item : options.items) {
      if (item == null || item == ViewController.OptionItem.SEPARATOR || item.name == null) return null; // groups keep the card
      items.add(item);
    }
    if (items.isEmpty()) return null;
    boolean choice = false;
    for (ViewController.OptionItem item : items) {
      if (item.name.toString().trim().endsWith(CHECK_MARK)) {
        choice = true;
        break;
      }
    }
    if (choice && items.size() >= 2) {
      return showChoice(controller, options, items, delegate, forcedTheme, bottomInset);
    }
    if (!TextUtils.isEmpty(options.info) && options.title == null && items.size() >= 2 && items.size() <= 3) {
      int cancel = -1;
      for (int i = 0; i < items.size(); i++) {
        if (isCancel(items.get(i))) {
          cancel = i;
          break;
        }
      }
      if (cancel != -1) {
        return showConfirm(controller, options, items, cancel, delegate, forcedTheme);
      }
    }
    return null;
  }

  private static boolean isCancel (ViewController.OptionItem item) {
    if (item.id == R.id.btn_cancel) return true;
    String name = item.name.toString().trim();
    return name.equalsIgnoreCase(Lang.getString(R.string.Cancel));
  }

  // Colours

  private static int color (@Nullable ThemeDelegate forcedTheme, int colorId) {
    return forcedTheme != null ? forcedTheme.getColor(colorId) : Theme.getColor(colorId);
  }

  private static boolean isDark (@Nullable ThemeDelegate forcedTheme) {
    return forcedTheme != null ? forcedTheme.isDark() : Theme.isDark();
  }

  /** The window's surface: a tone lighter than the screen on a dark theme, so it doesn't melt into it */
  private static int surface (@Nullable ThemeDelegate forcedTheme) {
    int filling = color(forcedTheme, ColorId.filling);
    return isDark(forcedTheme) ? ColorUtils.fromToArgb(filling, 0xffffffff, .08f) : filling;
  }

  private static int accent (@Nullable ThemeDelegate forcedTheme) {
    return color(forcedTheme, ColorId.textNeutral);
  }

  private static int itemColor (@Nullable ThemeDelegate forcedTheme, ViewController.OptionItem item, int fallback) {
    switch (item.textColor) {
      case ViewController.OptionColor.RED:
        return color(forcedTheme, ColorId.textNegative);
      case ViewController.OptionColor.GREEN:
        return color(forcedTheme, ColorId.iconPositive);
      default:
        return fallback;
    }
  }

  // The press, as the card does it

  private static View.OnClickListener clickListener (ViewController<?> controller, PopupLayout popup, @Nullable OptionDelegate delegate) {
    return v -> {
      if (delegate != null) {
        if (delegate.onOptionItemPressed(v, v.getId())) popup.hideWindow(true);
      } else {
        ViewController<?> c = controller.context().navigation().getCurrentStackItem();
        if (c instanceof OptionDelegate && ((OptionDelegate) c).onOptionItemPressed(v, v.getId())) popup.hideWindow(true);
      }
    };
  }

  private static PopupLayout newPopup (ViewController<?> controller, ViewController.Options options, @Nullable OptionDelegate delegate) {
    PopupLayout popup = new PopupLayout(controller.context());
    popup.setTag(controller);
    popup.init(true);
    popup.setDismissOtherPopUps(!options.ignoreOtherPopUps);
    popup.setNeedFullScreen(true);
    popup.tgx101SetStrongDim(true);
    if (delegate != null) popup.setDisableCancelOnTouchDown(delegate.disableCancelOnTouchdown());
    return popup;
  }

  private static TextView textButton (Context context, ViewController.OptionItem item, int index, int textColor, @Nullable OptionDelegate delegate, View.OnClickListener onClick) {
    TextView button = new TextView(context);
    button.setId(item.id);
    button.setText(item.name);
    button.setTextColor(textColor);
    button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    button.setTypeface(Fonts.getRobotoMedium());
    button.setGravity(Gravity.CENTER);
    button.setMinHeight(Screen.dp(40f));
    button.setPadding(Screen.dp(14f), 0, Screen.dp(14f), 0);
    button.setSingleLine(true);
    button.setEllipsize(TextUtils.TruncateAt.END);
    button.setBackground(pressable(ColorUtils.alphaColor(.14f, textColor), Screen.dp(20f)));
    if (delegate != null) button.setTag(delegate.getTagForItem(index));
    button.setOnClickListener(onClick);
    return button;
  }

  /** A press highlight with round corners: a ripple on Android 5+, a plain tint on older ones */
  private static Drawable pressable (int pressedColor, float radius) {
    GradientDrawable mask = new GradientDrawable();
    mask.setColor(0xffffffff);
    mask.setCornerRadius(radius);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(pressedColor), null, mask);
    }
    GradientDrawable pressed = new GradientDrawable();
    pressed.setColor(pressedColor);
    pressed.setCornerRadius(radius);
    android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
    states.addState(new int[] {android.R.attr.state_pressed}, pressed);
    return states;
  }

  // A — the dialog in the middle

  private static PopupLayout showConfirm (ViewController<?> controller, ViewController.Options options, List<ViewController.OptionItem> items, int cancelIndex, @Nullable OptionDelegate delegate, @Nullable ThemeDelegate forcedTheme) {
    Context context = controller.context();
    final PopupLayout popup = newPopup(controller, options, delegate);
    View.OnClickListener onClick = clickListener(controller, popup, delegate);

    final FrameLayoutFix root = new FrameLayoutFix(context);
    root.setOnClickListener(v -> popup.hideWindow(true)); // a tap beside the dialog
    root.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    final LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setClickable(true);
    card.setPadding(Screen.dp(24f), Screen.dp(24f), Screen.dp(24f), Screen.dp(18f));
    GradientDrawable background = new GradientDrawable();
    background.setColor(surface(forcedTheme));
    background.setCornerRadius(Screen.dp(28f));
    card.setBackground(background);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) card.setElevation(Screen.dp(6f));

    ViewController.OptionItem main = null;
    for (int i = 0; i < items.size(); i++) {
      if (i != cancelIndex) {
        main = items.get(i);
        break;
      }
    }
    int text = color(forcedTheme, ColorId.text);
    int accent = accent(forcedTheme);
    int mainColor = main != null ? itemColor(forcedTheme, main, accent) : accent;

    if (main != null && main.icon != 0) {
      Drawable icon = Drawables.get(context.getResources(), main.icon);
      if (icon != null) {
        icon = icon.mutate();
        icon.setColorFilter(Paints.getColorFilter(mainColor));
        ImageView iconView = new ImageView(context);
        iconView.setImageDrawable(icon);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(24f));
        iconParams.bottomMargin = Screen.dp(16f);
        card.addView(iconView, iconParams);
      }
    }

    // a short question is the headline; a long text reads as a paragraph
    boolean headline = options.info.length() <= 80;
    TextView infoView = new TextView(context);
    infoView.setText(options.info);
    infoView.setTextColor(headline ? text : ColorUtils.alphaColor(.82f, text));
    infoView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, headline ? 21f : 16f);
    infoView.setLineSpacing(Screen.dp(headline ? 4f : 3f), 1f);
    infoView.setGravity(headline && main != null && main.icon != 0 ? Gravity.CENTER_HORIZONTAL : Lang.gravity());
    infoView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    infoView.setLinkTextColor(accent);
    if (options.maxLineCount > 0) {
      infoView.setMaxLines(options.maxLineCount);
      infoView.setEllipsize(TextUtils.TruncateAt.END);
    }
    android.widget.ScrollView infoScroll = new android.widget.ScrollView(context); // a very long text scrolls inside
    infoScroll.setVerticalScrollBarEnabled(false);
    infoScroll.addView(infoView);
    card.addView(infoScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    // the buttons: «Cancel» and one action in a row at the right; two actions stack, «Cancel» last
    boolean stacked = items.size() > 2;
    LinearLayout buttons = new LinearLayout(context);
    buttons.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    buttons.setGravity(Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT);
    List<Integer> order = new ArrayList<>();
    if (stacked) {
      for (int i = 0; i < items.size(); i++) if (i != cancelIndex) order.add(i);
      order.add(cancelIndex);
    } else {
      order.add(cancelIndex);
      for (int i = 0; i < items.size(); i++) if (i != cancelIndex) order.add(i);
    }
    for (int k = 0; k < order.size(); k++) {
      int index = order.get(k);
      ViewController.OptionItem item = items.get(index);
      TextView button = textButton(context, item, index, index == cancelIndex ? accent : itemColor(forcedTheme, item, accent), delegate, onClick);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      if (stacked) {
        // (user's screenshot 2026-11-09, the proxy's «Включить / Сохранить прокси / Отмена»): stacked buttons stood
        // line to line — a finger's height each and a gap between them
        params.gravity = Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT;
        if (k > 0) params.topMargin = Screen.dp(4f);
        button.setMinHeight(Screen.dp(44f));
      } else if (k > 0) {
        params.leftMargin = Screen.dp(8f);
      }
      buttons.addView(button, params);
    }
    LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    buttonsParams.topMargin = Screen.dp(20f);
    buttonsParams.rightMargin = -Screen.dp(10f); // the text of the last button lines up with the text above
    buttonsParams.leftMargin = -Screen.dp(10f);
    card.addView(buttons, buttonsParams);

    int width = Math.min(Screen.currentWidth() - Screen.dp(48f), Screen.dp(400f));
    FrameLayoutFix.LayoutParams cardParams = FrameLayoutFix.newParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
    cardParams.topMargin = cardParams.bottomMargin = Screen.dp(48f);
    root.addView(card, cardParams);

    popup.showAnimatedPopupView(root, new PopupLayout.AnimatedPopupProvider() {
      private android.animation.ValueAnimator animator;
      private float factor;

      private void apply (float f) {
        factor = f;
        card.setAlpha(Math.min(1f, f * 1.6f));
        float scale = .9f + .1f * f;
        card.setScaleX(scale);
        card.setScaleY(scale);
        popup.setRevealFactor(f);
      }

      private void run (float to, long duration, android.animation.TimeInterpolator interpolator, Runnable end) {
        if (animator != null) animator.cancel();
        animator = android.animation.ValueAnimator.ofFloat(factor, to);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(a -> apply((float) a.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd (android.animation.Animator animation) {
            end.run();
          }
        });
        animator.start();
      }

      @Override
      public void prepareShowAnimation () {
        apply(0f);
      }

      @Override
      public void launchShowAnimation (PopupLayout p) {
        run(1f, 220, new android.view.animation.DecelerateInterpolator(2f), p::onCustomShowComplete);
      }

      @Override
      public boolean launchHideAnimation (PopupLayout p, me.vkryl.android.animator.FactorAnimator originalAnimator) {
        run(0f, 160, new android.view.animation.AccelerateInterpolator(1.5f), p::onCustomHideAnimationComplete);
        return true;
      }
    });
    return popup;
  }

  // The «Delete» under a header button: it stays at the top, by the finger (user 2026-11-01), in the new look

  public interface HeaderMenuCallback {
    void onItem (int index, boolean checked);
  }

  /** A card under the header at the end side: an optional check row (e.g. «Delete for everyone»), then the buttons */
  public static PopupLayout showHeaderMenu (ViewController<?> controller, @Nullable CharSequence checkLabel, boolean checked, CharSequence[] labels, int[] icons, boolean[] red, HeaderMenuCallback callback) {
    Context context = controller.context();
    final PopupLayout popup = new PopupLayout(context);
    popup.setTag(controller);
    popup.init(true);
    popup.setNeedFullScreen(true);
    popup.tgx101SetStrongDim(true);

    final FrameLayoutFix root = new FrameLayoutFix(context);
    root.setOnClickListener(v -> popup.hideWindow(true));
    root.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    final LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setClickable(true);
    card.setPadding(0, Screen.dp(8f), 0, Screen.dp(8f));
    GradientDrawable background = new GradientDrawable();
    background.setColor(surface(null));
    background.setCornerRadius(Screen.dp(20f));
    card.setBackground(background);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) card.setElevation(Screen.dp(8f));

    int text = Theme.getColor(ColorId.text);
    int accent = accent(null);
    int negative = Theme.getColor(ColorId.textNegative);
    final boolean[] state = {checked};

    if (checkLabel != null) {
      final CheckBox box = new CheckBox(context, checked, accent, Theme.getColor(ColorId.iconLight), surface(null));
      LinearLayout row = row(context, ColorUtils.alphaColor(.14f, accent));
      row.addView(box, new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f)));
      row.addView(label(context, checkLabel, text, false));
      row.setOnClickListener(v -> {
        state[0] = !state[0];
        box.setChecked(state[0]);
      });
      card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(52f)));
    }
    for (int i = 0; i < labels.length; i++) {
      final int index = i;
      int color = red[i] ? negative : text;
      LinearLayout row = row(context, ColorUtils.alphaColor(.14f, color));
      Drawable icon = icons[i] != 0 ? Drawables.get(context.getResources(), icons[i]) : null;
      if (icon != null) {
        icon = icon.mutate();
        icon.setColorFilter(Paints.getColorFilter(red[i] ? negative : Theme.getColor(ColorId.icon)));
        ImageView iconView = new ImageView(context);
        iconView.setImageDrawable(icon);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        row.addView(iconView, new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f)));
      }
      row.addView(label(context, labels[i], color, red[i]));
      row.setOnClickListener(v -> {
        popup.hideWindow(true);
        callback.onItem(index, state[0]);
      });
      card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(52f)));
    }

    // under the header, with a gap — it no longer runs into the header
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT));
    params.topMargin = HeaderView.getTopOffset() + HeaderView.getSize(false) + Screen.dp(6f);
    params.leftMargin = params.rightMargin = Screen.dp(8f);
    card.setMinimumWidth(Screen.dp(220f));
    root.addView(card, params);

    popup.showAnimatedPopupView(root, new PopupLayout.AnimatedPopupProvider() {
      private android.animation.ValueAnimator animator;
      private float factor;

      private void apply (float f) {
        factor = f;
        card.setAlpha(Math.min(1f, f * 1.6f));
        card.setPivotX(Lang.rtl() ? 0 : card.getWidth());
        card.setPivotY(0);
        float scale = .92f + .08f * f;
        card.setScaleX(scale);
        card.setScaleY(scale);
        popup.setRevealFactor(f);
      }

      private void run (float to, long duration, android.animation.TimeInterpolator interpolator, Runnable end) {
        if (animator != null) animator.cancel();
        animator = android.animation.ValueAnimator.ofFloat(factor, to);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(a -> apply((float) a.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd (android.animation.Animator animation) {
            end.run();
          }
        });
        animator.start();
      }

      @Override
      public void prepareShowAnimation () {
        apply(0f);
      }

      @Override
      public void launchShowAnimation (PopupLayout p) {
        run(1f, 200, new android.view.animation.DecelerateInterpolator(2f), p::onCustomShowComplete);
      }

      @Override
      public boolean launchHideAnimation (PopupLayout p, me.vkryl.android.animator.FactorAnimator originalAnimator) {
        run(0f, 150, new android.view.animation.AccelerateInterpolator(1.5f), p::onCustomHideAnimationComplete);
        return true;
      }
    });
    return popup;
  }

  private static LinearLayout row (Context context, int pressedColor) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(Screen.dp(16f), 0, Screen.dp(20f), 0);
    row.setBackground(pressable(pressedColor, 0));
    return row;
  }

  private static TextView label (Context context, CharSequence text, int color, boolean medium) {
    TextView label = new TextView(context);
    label.setText(text);
    label.setTextColor(color);
    label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    if (medium) label.setTypeface(Fonts.getRobotoMedium());
    label.setSingleLine(true);
    label.setEllipsize(TextUtils.TruncateAt.END);
    label.setMaxWidth(Screen.dp(240f));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    if (Lang.rtl()) params.rightMargin = Screen.dp(16f); else params.leftMargin = Screen.dp(16f);
    label.setLayoutParams(params);
    return label;
  }

  /** The Material 3 check box: a rounded square, filled with the accent and a ✓ when checked */
  private static final class CheckBox extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF rect = new android.graphics.RectF();
    private final android.graphics.Path check = new android.graphics.Path();
    private boolean checked;
    private final int onColor, offColor, markColor;

    CheckBox (Context context, boolean checked, int onColor, int offColor, int markColor) {
      super(context);
      this.checked = checked;
      this.onColor = onColor;
      this.offColor = offColor;
      this.markColor = markColor;
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void setChecked (boolean checked) {
      if (this.checked != checked) {
        this.checked = checked;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      float cx = getWidth() / 2f, cy = getHeight() / 2f, half = Screen.dp(9f), radius = Screen.dp(2.5f);
      rect.set(cx - half, cy - half, cx + half, cy + half);
      if (checked) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(onColor);
        c.drawRoundRect(rect, radius, radius, paint);
        float s = Screen.dp(4.5f);
        check.reset();
        check.moveTo(cx - s, cy);
        check.lineTo(cx - s * .3f, cy + s * .7f);
        check.lineTo(cx + s, cy - s * .7f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Screen.dp(2f));
        paint.setColor(markColor);
        c.drawPath(check, paint);
      } else {
        float stroke = Screen.dp(2f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(offColor);
        rect.inset(stroke / 2f, stroke / 2f);
        c.drawRoundRect(rect, radius, radius, paint);
      }
    }
  }

  // B — the choice sheet from the bottom

  private static final class Radio extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean checked;
    private final int onColor, offColor;

    Radio (Context context, boolean checked, int onColor, int offColor) {
      super(context);
      this.checked = checked;
      this.onColor = onColor;
      this.offColor = offColor;
    }

    void setChecked (boolean checked) {
      if (this.checked != checked) {
        this.checked = checked;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      float stroke = Screen.dp(2f);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(stroke);
      paint.setColor(checked ? onColor : offColor);
      c.drawCircle(cx, cy, Screen.dp(10f) - stroke / 2f, paint);
      if (checked) {
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, Screen.dp(5f), paint);
      }
    }
  }

  private static PopupLayout showChoice (ViewController<?> controller, ViewController.Options options, List<ViewController.OptionItem> items, @Nullable OptionDelegate delegate, @Nullable ThemeDelegate forcedTheme, int bottomInset) {
    Context context = controller.context();
    final PopupLayout popup = newPopup(controller, options, delegate);
    View.OnClickListener onClick = clickListener(controller, popup, delegate);

    final FrameLayoutFix root = new FrameLayoutFix(context);
    root.setOnClickListener(v -> popup.hideWindow(true));
    root.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    final LinearLayout sheet = new LinearLayout(context);
    sheet.setOrientation(LinearLayout.VERTICAL);
    sheet.setClickable(true);
    // above the navigation buttons or the gesture bar — the rows never go under them (the screen's own inset is 0 when
    // the app draws under the bar, so the window's navigation bar inset is asked too)
    int navigationBar = 0;
    android.view.Window window = controller.context().getWindow();
    androidx.core.view.WindowInsetsCompat insets = window != null ? androidx.core.view.ViewCompat.getRootWindowInsets(window.getDecorView()) : null;
    if (insets != null) navigationBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
    sheet.setPadding(0, Screen.dp(24f), 0, Screen.dp(16f) + Math.max(Math.max(0, bottomInset), navigationBar));
    GradientDrawable background = new GradientDrawable();
    background.setColor(surface(forcedTheme));
    float r = Screen.dp(28f);
    background.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
    sheet.setBackground(background);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) sheet.setElevation(Screen.dp(8f));

    int text = color(forcedTheme, ColorId.text);
    int textLight = color(forcedTheme, ColorId.textLight);
    int accent = accent(forcedTheme);

    CharSequence title = options.title != null ? options.title : options.info;
    CharSequence subtitle = options.title != null ? options.info : null;
    if (!TextUtils.isEmpty(title)) {
      TextView titleView = new TextView(context);
      titleView.setText(title);
      titleView.setTextColor(text);
      titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 21f);
      titleView.setGravity(Lang.gravity());
      titleView.setPadding(Screen.dp(24f), 0, Screen.dp(24f), Screen.dp(TextUtils.isEmpty(subtitle) ? 12f : 4f));
      sheet.addView(titleView);
    }
    if (!TextUtils.isEmpty(subtitle)) {
      TextView subtitleView = new TextView(context);
      subtitleView.setText(subtitle);
      subtitleView.setTextColor(textLight);
      subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
      subtitleView.setGravity(Lang.gravity());
      subtitleView.setPadding(Screen.dp(24f), 0, Screen.dp(24f), Screen.dp(12f));
      sheet.addView(subtitleView);
    }

    final List<Radio> radios = new ArrayList<>();
    final List<View> rows = new ArrayList<>();
    final int highlight = ColorUtils.alphaColor(.14f, accent);
    LinearLayout list = new LinearLayout(context);
    list.setOrientation(LinearLayout.VERTICAL);
    for (int i = 0; i < items.size(); i++) {
      ViewController.OptionItem item = items.get(i);
      String name = item.name.toString().trim();
      boolean checked = name.endsWith(CHECK_MARK);
      if (checked) name = name.substring(0, name.length() - CHECK_MARK.length()).trim();

      LinearLayout row = new LinearLayout(context);
      row.setId(item.id);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setMinimumHeight(Screen.dp(56f));
      row.setPadding(Screen.dp(24f), Screen.dp(8f), Screen.dp(24f), Screen.dp(8f));
      if (delegate != null) row.setTag(delegate.getTagForItem(i));
      row.setBackgroundColor(checked ? highlight : 0);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        row.setForeground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(highlight), null, new android.graphics.drawable.ColorDrawable(0xffffffff)));
      }

      Radio radio = new Radio(context, checked, accent, color(forcedTheme, ColorId.iconLight));
      row.addView(radio, new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f)));
      radios.add(radio);

      TextView label = new TextView(context);
      label.setText(name);
      label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      label.setTextColor(itemColor(forcedTheme, item, text));
      if (checked) label.setTypeface(Fonts.getRobotoMedium());
      label.setGravity(Lang.gravity());
      LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      if (Lang.rtl()) labelParams.rightMargin = Screen.dp(16f); else labelParams.leftMargin = Screen.dp(16f);
      row.addView(label, labelParams);

      final int index = i;
      row.setOnClickListener(v -> {
        // the choice moves to the pressed row at once, the sheet goes a moment later
        for (int k = 0; k < radios.size(); k++) {
          radios.get(k).setChecked(k == index);
          rows.get(k).setBackgroundColor(k == index ? highlight : 0);
        }
        v.setEnabled(false);
        UI.post(() -> onClick.onClick(v), 140);
      });
      rows.add(row);
      list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    android.widget.ScrollView scroll = new android.widget.ScrollView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        // a long list scrolls within ~⅔ of the screen
        int max = Math.round(Screen.currentHeight() * .62f);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
      }
    };
    scroll.setVerticalScrollBarEnabled(false);
    scroll.addView(list);
    sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    root.addView(sheet, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

    popup.showAnimatedPopupView(root, new PopupLayout.AnimatedPopupProvider() {
      private android.animation.ValueAnimator animator;
      private float factor;

      private float distance () {
        return sheet.getHeight() > 0 ? sheet.getHeight() : Screen.currentHeight() / 2f;
      }

      private void apply (float f) {
        factor = f;
        sheet.setTranslationY(distance() * (1f - f));
        popup.setRevealFactor(f);
      }

      private void run (float to, long duration, android.animation.TimeInterpolator interpolator, Runnable end) {
        if (animator != null) animator.cancel();
        animator = android.animation.ValueAnimator.ofFloat(factor, to);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(a -> apply((float) a.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd (android.animation.Animator animation) {
            end.run();
          }
        });
        animator.start();
      }

      @Override
      public void prepareShowAnimation () {
        sheet.setTranslationY(Screen.currentHeight());
      }

      @Override
      public void launchShowAnimation (PopupLayout p) {
        apply(0f);
        run(1f, 280, new android.view.animation.DecelerateInterpolator(2.2f), p::onCustomShowComplete);
      }

      @Override
      public boolean launchHideAnimation (PopupLayout p, me.vkryl.android.animator.FactorAnimator originalAnimator) {
        run(0f, 220, new androidx.interpolator.view.animation.FastOutSlowInInterpolator(), p::onCustomHideAnimationComplete);
        return true;
      }
    });
    return popup;
  }
}
