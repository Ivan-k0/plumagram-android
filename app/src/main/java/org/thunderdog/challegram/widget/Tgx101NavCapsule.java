package org.thunderdog.challegram.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/**
 * TGx101: floating bottom navigation capsule over the chat list (mockup «Капсула», variant B without labels):
 * white ~92 % capsule with a soft shadow, the selected tab sits on a tinted «pill» that slides to the tapped tab.
 */
public class Tgx101NavCapsule extends View {
  public static final int TAB_CHATS = 0, TAB_CONTACTS = 1, TAB_CALLS = 2, TAB_SETTINGS = 3;

  public static final float HEIGHT_DP = 58f, MARGIN_DP = 14f;

  public interface Callback {
    void onTabClick (int tab);
    default boolean onTabLongClick (int tab) { return false; }
    default void onMenuItem (int id) { }
  }

  private static final int[] ICONS = {
    R.drawable.baseline_chat_bubble_24,
    R.drawable.baseline_person_24,
    R.drawable.baseline_phone_24,
    R.drawable.baseline_settings_24
  };

  private final Drawable[] icons = new Drawable[ICONS.length];
  private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private Callback callback;
  private float selected = TAB_CHATS; // animated position of the pill
  private ValueAnimator animator;
  private int pressedTab = -1;
  private boolean longPressed;
  private final Runnable longPress = () -> {
    if (pressedTab != -1 && callback != null && callback.onTabLongClick(pressedTab)) {
      longPressed = true;
      performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }
  };

  public Tgx101NavCapsule (Context context) {
    super(context);
    for (int i = 0; i < ICONS.length; i++) {
      icons[i] = Drawables.get(getResources(), ICONS[i]);
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
      setLayerType(LAYER_TYPE_SOFTWARE, null); // setShadowLayer on older Android
    }
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  private float padding () {
    return Screen.dp(MARGIN_DP);
  }

  private float tabWidth () {
    return (getMeasuredWidth() - padding() * 2 - Screen.dp(12f)) / ICONS.length;
  }

  private float tabCenterX (float tab) {
    return padding() + Screen.dp(6f) + tabWidth() * (tab + .5f);
  }

  private float capsuleTop () {
    return getMeasuredHeight() - getPaddingBottom() - Screen.dp(MARGIN_DP) - Screen.dp(HEIGHT_DP);
  }

  private int tabAt (float x, float y) {
    float top = capsuleTop();
    if (y < top || y > top + Screen.dp(HEIGHT_DP) || x < padding() || x > getMeasuredWidth() - padding())
      return -1;
    int tab = (int) ((x - padding() - Screen.dp(6f)) / tabWidth());
    return Math.max(0, Math.min(ICONS.length - 1, tab));
  }

  /** Slides the pill; the screen behind the tab opens right after (see MainController). */
  public void setSelectedTab (int tab, boolean animated) {
    if (animator != null) {
      animator.cancel();
      animator = null;
    }
    if (!animated) {
      selected = tab;
      invalidate();
      return;
    }
    animator = ValueAnimator.ofFloat(selected, tab);
    animator.setDuration(180);
    animator.setInterpolator(new DecelerateInterpolator());
    animator.addUpdateListener(a -> {
      selected = (float) a.getAnimatedValue();
      invalidate();
    });
    animator.start();
  }

  // Hold menu (user 2026-10-06): opens over the capsule on a long press, pick by sliding the finger or by a tap

  private int[] menuIds, menuIcons;
  private String[] menuTitles;
  private Drawable[] menuDrawables;
  private float menuFactor;
  private ValueAnimator menuAnimator;
  private int menuHighlight = -1;
  private boolean menuTracking; // the finger that opened the menu (or touched it) is still down
  private final RectF menuRect = new RectF();
  private final android.text.TextPaint textPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);

  private static final float MENU_ROW_DP = 48f;

  public boolean isMenuOpen () {
    return menuIds != null;
  }

  public void openMenu (int[] ids, int[] icons, String[] titles) {
    menuIds = ids;
    menuIcons = icons;
    menuTitles = titles;
    menuDrawables = new Drawable[icons.length];
    for (int i = 0; i < icons.length; i++) {
      menuDrawables[i] = Drawables.get(getResources(), icons[i]);
    }
    menuHighlight = -1;
    menuTracking = true;
    animateMenu(1f, null);
  }

  public void closeMenu () {
    if (menuIds == null)
      return;
    menuTracking = false;
    menuHighlight = -1;
    animateMenu(0f, () -> {
      menuIds = null;
      menuDrawables = null;
      invalidate();
    });
  }

  private void animateMenu (float to, Runnable after) {
    if (menuAnimator != null) {
      menuAnimator.cancel();
    }
    menuAnimator = ValueAnimator.ofFloat(menuFactor, to);
    menuAnimator.setDuration(to == 1f ? 170 : 130);
    menuAnimator.setInterpolator(new DecelerateInterpolator());
    menuAnimator.addUpdateListener(a -> {
      menuFactor = (float) a.getAnimatedValue();
      invalidate();
    });
    if (after != null) {
      menuAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
        private boolean cancelled;
        @Override public void onAnimationCancel (android.animation.Animator animation) { cancelled = true; }
        @Override public void onAnimationEnd (android.animation.Animator animation) { if (!cancelled) after.run(); }
      });
    }
    menuAnimator.start();
  }

  private void layoutMenu () {
    textPaint.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoRegular());
    textPaint.setTextSize(Screen.dp(16f));
    float textWidth = 0;
    for (String t : menuTitles) {
      textWidth = Math.max(textWidth, textPaint.measureText(t));
    }
    float w = Math.min(getMeasuredWidth() - padding() * 2, textWidth + Screen.dp(16f + 24f + 16f + 20f));
    float h = Screen.dp(MENU_ROW_DP) * menuIds.length + Screen.dp(12f);
    float right = getMeasuredWidth() - padding();
    float bottom = capsuleTop() - Screen.dp(8f);
    menuRect.set(right - w, bottom - h, right, bottom);
  }

  private int menuItemAt (float x, float y) {
    if (menuIds == null || !menuRect.contains(x, y))
      return -1;
    int i = (int) ((y - menuRect.top - Screen.dp(6f)) / Screen.dp(MENU_ROW_DP));
    return i >= 0 && i < menuIds.length ? i : -1;
  }

  private void setMenuHighlight (int i) {
    if (menuHighlight != i) {
      menuHighlight = i;
      if (i != -1) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
      }
      invalidate();
    }
  }

  private void pickMenuItem (int i) {
    int id = menuIds[i];
    closeMenu();
    playSoundEffect(android.view.SoundEffectConstants.CLICK);
    if (callback != null) {
      callback.onMenuItem(id);
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    float x = e.getX(), y = e.getY();
    if (menuIds != null && !longPressed) {
      // a fresh touch while the menu is open: pick an item or close it
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN:
          menuTracking = true;
          setMenuHighlight(menuItemAt(x, y));
          if (menuHighlight == -1 && !menuRect.contains(x, y)) {
            menuTracking = false;
            closeMenu();
          }
          return true;
        case MotionEvent.ACTION_MOVE:
          if (menuTracking) setMenuHighlight(menuItemAt(x, y));
          return true;
        case MotionEvent.ACTION_UP:
          if (menuTracking && menuHighlight != -1) {
            pickMenuItem(menuHighlight);
          }
          menuTracking = false;
          return true;
        case MotionEvent.ACTION_CANCEL:
          menuTracking = false;
          setMenuHighlight(-1);
          return true;
      }
      return true;
    }
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        pressedTab = tabAt(x, y);
        if (pressedTab == -1)
          return false; // chats under the transparent area stay touchable
        longPressed = false;
        postDelayed(longPress, 450);
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (longPressed) {
          if (menuIds != null) setMenuHighlight(menuItemAt(x, y)); // slide to an item without lifting the finger
          return true;
        }
        if (pressedTab != -1 && tabAt(x, y) != pressedTab) {
          removeCallbacks(longPress);
          pressedTab = -1;
        }
        return true;
      }
      case MotionEvent.ACTION_UP: {
        removeCallbacks(longPress);
        int tab = pressedTab;
        pressedTab = -1;
        if (longPressed) {
          longPressed = false;
          if (menuIds != null && menuHighlight != -1) {
            pickMenuItem(menuHighlight);
          } else {
            menuTracking = false; // released elsewhere: the menu stays open for a tap
          }
          return true;
        }
        if (tab != -1 && callback != null) {
          playSoundEffect(android.view.SoundEffectConstants.CLICK);
          callback.onTabClick(tab);
        }
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        removeCallbacks(longPress);
        pressedTab = -1;
        if (longPressed) {
          longPressed = false;
          menuTracking = false;
          setMenuHighlight(-1);
        }
        return true;
      }
    }
    return true;
  }

  @Override
  protected void onDraw (Canvas c) {
    float top = capsuleTop();
    float h = Screen.dp(HEIGHT_DP);
    float r = h / 2f;
    rect.set(padding(), top, getMeasuredWidth() - padding(), top + h);

    int filling = Theme.getColor(ColorId.filling);
    boolean dark = Theme.isDark();
    shadowPaint.setColor(ColorUtils.alphaColor(dark ? .94f : .92f, filling));
    shadowPaint.setShadowLayer(Screen.dp(10f), 0, Screen.dp(3f), ColorUtils.alphaColor(dark ? .5f : .18f, 0xff000000));
    c.drawRoundRect(rect, r, r, shadowPaint);

    // the pill behind the selected tab
    int active = Theme.getColor(ColorId.iconActive);
    float cx = tabCenterX(selected);
    float pw = Math.min(tabWidth() - Screen.dp(8f), Screen.dp(64f)) / 2f;
    float ph = Screen.dp(22f);
    float cy = top + h / 2f;
    fillPaint.setColor(ColorUtils.alphaColor(dark ? .22f : .13f, active));
    rect.set(cx - pw, cy - ph, cx + pw, cy + ph);
    c.drawRoundRect(rect, ph, ph, fillPaint);

    int inactive = Theme.getColor(ColorId.icon);
    for (int i = 0; i < icons.length; i++) {
      float f = Math.max(0f, 1f - Math.abs(selected - i));
      int color = ColorUtils.fromToArgb(inactive, active, f);
      Drawables.drawCentered(c, icons[i], tabCenterX(i), cy, Paints.getPorterDuffPaint(color));
    }

    if (menuIds != null && menuFactor > 0f) {
      layoutMenu();
      int alpha = (int) (255 * Math.min(1f, menuFactor));
      float scale = .85f + .15f * menuFactor;
      c.save();
      c.scale(scale, scale, menuRect.right - Screen.dp(24f), menuRect.bottom);
      float mr = Screen.dp(16f);
      shadowPaint.setColor(ColorUtils.alphaColor(menuFactor * (dark ? .98f : .97f), filling));
      shadowPaint.setShadowLayer(Screen.dp(12f), 0, Screen.dp(4f), ColorUtils.alphaColor(menuFactor * (dark ? .5f : .2f), 0xff000000));
      c.drawRoundRect(menuRect, mr, mr, shadowPaint);
      int textColor = Theme.getColor(ColorId.text);
      float row = Screen.dp(MENU_ROW_DP);
      for (int k = 0; k < menuIds.length; k++) {
        float rowTop = menuRect.top + Screen.dp(6f) + row * k;
        if (k == menuHighlight) {
          fillPaint.setColor(ColorUtils.alphaColor(menuFactor * (dark ? .22f : .13f), active));
          rect.set(menuRect.left + Screen.dp(6f), rowTop, menuRect.right - Screen.dp(6f), rowTop + row);
          c.drawRoundRect(rect, Screen.dp(12f), Screen.dp(12f), fillPaint);
        }
        float rowCy = rowTop + row / 2f;
        int iconColor = k == menuHighlight ? active : inactive;
        Paint ip = Paints.getPorterDuffPaint(iconColor);
        int oldAlpha = ip.getAlpha();
        ip.setAlpha(alpha);
        Drawables.drawCentered(c, menuDrawables[k], menuRect.left + Screen.dp(16f + 12f), rowCy, ip);
        ip.setAlpha(oldAlpha);
        textPaint.setColor(ColorUtils.alphaColor(menuFactor, textColor));
        c.drawText(menuTitles[k], menuRect.left + Screen.dp(16f + 24f + 16f), rowCy + Screen.dp(5.5f), textPaint);
      }
      c.restore();
    }
  }
}