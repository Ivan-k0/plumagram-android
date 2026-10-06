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
 * white ~92 % capsule with a soft shadow; the selected icon lights up, a line under it slides along the bottom edge (animation variant A).
 */
public class Tgx101NavCapsule extends View {
  public static final int TAB_CHATS = 0, TAB_CONTACTS = 1, TAB_CALLS = 2, TAB_SETTINGS = 3;

  public static final float HEIGHT_DP = 58f, MARGIN_DP = 14f;

  public interface Callback {
    void onTabClick (int tab);
    default boolean onTabLongClick (int tab) { return false; }
    default void onMenuItem (int id) { }
    default void onCollapsedChanged (boolean collapsed) { }
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
  private final android.graphics.Path outline = new android.graphics.Path(), line = new android.graphics.Path();
  private final android.graphics.PathMeasure outlineMeasure = new android.graphics.PathMeasure();
  private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG), glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private int popTab = -1;
  private float popFactor;
  private Callback callback;
  private float selected = TAB_CHATS; // animated position of the pill
  private ValueAnimator animator;
  private int pressedTab = -1;
  private boolean longPressed;
  private boolean dragging;
  private float downX;
  private int dragNearest = -1;
  private int currentTab = TAB_CHATS;

  // user 2026-10-06 21:3x «добавь функцию сворачивания нижнего меню»: swipe down → a small bar at the bottom, tap or swipe
  // up on it → the capsule again (remembered between launches)
  private boolean collapsed = org.thunderdog.challegram.unsorted.Settings.instance().tgx101CapsuleCollapsed();
  private float collapseFactor = collapsed ? 1f : 0f;
  private ValueAnimator collapseAnimator;
  private boolean verticalDrag;
  private float downY;

  public boolean isCollapsed () {
    return collapsed;
  }

  public void setCollapsed (boolean collapse) {
    if (collapsed == collapse) return;
    collapsed = collapse;
    org.thunderdog.challegram.unsorted.Settings.instance().setTgx101CapsuleCollapsed(collapse);
    if (collapseAnimator != null) collapseAnimator.cancel();
    collapseAnimator = ValueAnimator.ofFloat(collapseFactor, collapse ? 1f : 0f);
    collapseAnimator.setDuration(collapse ? 220 : 260);
    collapseAnimator.setInterpolator(androidx.core.view.animation.PathInterpolatorCompat.create(.2f, .9f, .3f, 1f));
    collapseAnimator.addUpdateListener(a -> {
      collapseFactor = (float) a.getAnimatedValue();
      invalidate();
    });
    collapseAnimator.start();
    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    if (callback != null) callback.onCollapsedChanged(collapse);
  }

  private final RectF handleRect = new RectF();

  // user 2026-10-06 21:5x «должна уходить и заходить одновременно с ручкой»: scrolling the chats down hides the capsule
  // together with ✎, scrolling up brings both back
  private float scrollHide;
  private ValueAnimator scrollAnimator;

  public void setScrollHidden (boolean hide) {
    float to = hide ? 1f : 0f;
    if (scrollAnimator != null) scrollAnimator.cancel();
    if (scrollHide == to) return;
    if (hide && menuIds != null) return; // not while its menu is open
    scrollAnimator = ValueAnimator.ofFloat(scrollHide, to);
    scrollAnimator.setDuration(hide ? 180 : 220); // same pace as ✎ (it hides in 180 ms)
    scrollAnimator.setInterpolator(new DecelerateInterpolator());
    scrollAnimator.addUpdateListener(a -> {
      scrollHide = (float) a.getAnimatedValue();
      invalidate();
    });
    scrollAnimator.start();
  }

  private float scrollHideOffset () {
    return scrollHide * (Screen.dp(HEIGHT_DP + MARGIN_DP + 12f) + getPaddingBottom());
  }

  // user 2026-10-06 21:4x «полоса под кнопкой тоже пусть исчезает, пока её не трогаешь»: the line shows up on touch,
  // slide and tab change, and fades out ~0.6 s after the capsule is left alone
  private float lineFactor;
  private ValueAnimator lineAnimator;
  private final Runnable hideLine = () -> animateLine(0f);

  private void animateLine (float to) {
    if (lineAnimator != null) lineAnimator.cancel();
    if (lineFactor == to) return;
    if (to > 0f) { // user 2026-10-06 21:43: «должна появляться быстрее» — at once on touch
      lineFactor = to;
      invalidate();
      return;
    }
    lineAnimator = ValueAnimator.ofFloat(lineFactor, to);
    lineAnimator.setDuration(250);
    lineAnimator.addUpdateListener(a -> {
      lineFactor = (float) a.getAnimatedValue();
      invalidate();
    });
    lineAnimator.start();
  }

  private void showLine (boolean autoHide) {
    removeCallbacks(hideLine);
    animateLine(1f);
    if (autoHide) postDelayed(hideLine, 350);
  }

  private void layoutCapsuleRect (RectF out) {
    float top = capsuleTop(), h = Screen.dp(HEIGHT_DP);
    float l = padding(), r = getMeasuredWidth() - padding(), b = top + h;
    float hw = Screen.dp(36f), hh = Screen.dp(10f), cx = getMeasuredWidth() / 2f, hb = b - Screen.dp(2f);
    float f = collapseFactor;
    out.set(l + (cx - hw - l) * f, top + (hb - hh - top) * f, r + (cx + hw - r) * f, b + (hb - b) * f);
  }
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
    linePaint.setStyle(Paint.Style.STROKE);
    linePaint.setStrokeCap(Paint.Cap.ROUND);
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
    if (collapsed) return -1;
    float top = capsuleTop();
    if (y < top || y > top + Screen.dp(HEIGHT_DP) || x < padding() || x > getMeasuredWidth() - padding())
      return -1;
    int tab = (int) ((x - padding() - Screen.dp(6f)) / tabWidth());
    return Math.max(0, Math.min(ICONS.length - 1, tab));
  }

  /** Slides the pill; the screen behind the tab opens right after (see MainController). */
  public void setSelectedTab (int tab, boolean animated) {
    currentTab = tab;
    if (animator != null) {
      animator.cancel();
      animator = null;
    }
    if (!animated) {
      selected = tab;
      popTab = -1;
      invalidate();
      return;
    }
    final float from = selected;
    popTab = tab;
    showLine(true);
    animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(380);
    animator.addUpdateListener(a -> {
      float t = (float) a.getAnimatedValue();
      float e = 1f - (float) Math.pow(1f - Math.min(1f, t / .9f), 3); // ease-out cubic
      selected = from + (tab - from) * e;
      popFactor = Math.min(1f, t * 1.2f);
      invalidate();
    });
    animator.start();
  }

  // Hold menu (user 2026-10-06): opens over the capsule on a long press, pick by sliding the finger or by a tap

  private int[] menuIds, menuIcons;
  private String[] menuTitles;
  private Drawable[] menuDrawables;
  private float menuFactor;
  private String[] menuLetters;
  private int[] menuColors;
  private final android.text.TextPaint lettersPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
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
    openMenu(ids, icons, titles, null, null);
  }

  /** icons[i] == 0 → a round «avatar» with letters[i] on colors[i] (quick call contacts) */
  public void openMenu (int[] ids, int[] icons, String[] titles, String[] letters, int[] colors) {
    menuIds = ids;
    menuIcons = icons;
    menuTitles = titles;
    menuLetters = letters;
    menuColors = colors;
    menuDrawables = new Drawable[icons.length];
    for (int i = 0; i < icons.length; i++) {
      menuDrawables[i] = icons[i] != 0 ? Drawables.get(getResources(), icons[i]) : null;
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
    menuAnimator.setDuration(to == 1f ? 150 : 140); // user 2026-10-06 22:0x: «окошко меню должно вылазить резче, ускорь»
    menuAnimator.setInterpolator(to == 1f ? androidx.core.view.animation.PathInterpolatorCompat.create(.2f, .9f, .3f, 1f) : androidx.core.view.animation.PathInterpolatorCompat.create(.4f, 0f, .6f, 1f));
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
    if (scrollHide > .5f && menuIds == null) {
      return false; // hidden by scrolling: the list under it gets the touches
    }
    if (collapsed) {
      layoutCapsuleRect(handleRect);
      handleRect.inset(-Screen.dp(40f), -Screen.dp(22f)); // a comfortable target around the small bar
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN:
          downY = y;
          return handleRect.contains(x, y);
        case MotionEvent.ACTION_UP:
          setCollapsed(false); // tap or swipe up on the bar
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
        dragging = false;
        verticalDrag = false;
        downX = x;
        downY = y;
        showLine(false);
        postDelayed(longPress, 450);
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (longPressed) {
          if (menuIds != null) setMenuHighlight(menuItemAt(x, y)); // slide to an item without lifting the finger
          return true;
        }
        if (pressedTab != -1 && !dragging && !verticalDrag && y - downY > Screen.getTouchSlop() && y - downY > Math.abs(x - downX)) {
          verticalDrag = true; // swipe down: collapse
          removeCallbacks(longPress);
          if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (verticalDrag) return true;
        if (pressedTab != -1 && !dragging && Math.abs(x - downX) > Screen.getTouchSlop()) {
          // user 2026-10-06: slide along the capsule, the line follows the finger, the tab opens on release
          dragging = true;
          removeCallbacks(longPress);
          if (animator != null) {
            animator.cancel();
            animator = null;
          }
          if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (dragging) {
          float tab = (x - padding() - Screen.dp(6f)) / tabWidth() - .5f;
          tab = Math.max(0f, Math.min(ICONS.length - 1, tab));
          int near = Math.round(tab);
          if (near != dragNearest) {
            dragNearest = near;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
          }
          popTab = -1;
          selected = tab;
          invalidate();
        }
        return true;
      }
      case MotionEvent.ACTION_UP: {
        removeCallbacks(longPress);
        int tab = pressedTab;
        pressedTab = -1;
        showLine(true);
        if (verticalDrag) {
          verticalDrag = false;
          if (y - downY > Screen.dp(16f)) setCollapsed(true);
          return true;
        }
        if (longPressed) {
          longPressed = false;
          if (menuIds != null && menuHighlight != -1) {
            pickMenuItem(menuHighlight);
          } else {
            menuTracking = false; // released elsewhere: the menu stays open for a tap
          }
          return true;
        }
        if (dragging) {
          dragging = false;
          dragNearest = -1;
          tab = Math.round(selected);
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
        showLine(true);
        if (dragging) {
          dragging = false;
          dragNearest = -1;
          setSelectedTab(currentTab, true);
        }
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
    float hideOffset = scrollHideOffset();
    if (hideOffset > 0f) {
      int save = c.save();
      c.translate(0, hideOffset);
      drawCapsule(c);
      c.restoreToCount(save);
      return;
    }
    drawCapsule(c);
  }

  private void drawCapsule (Canvas c) {
    float top = capsuleTop();
    float h = Screen.dp(HEIGHT_DP);
    float r = h / 2f;
    int filling = Theme.getColor(ColorId.filling);
    boolean dark = Theme.isDark();
    shadowPaint.setColor(ColorUtils.alphaColor(dark ? .94f : .92f, filling));
    shadowPaint.setShadowLayer(Screen.dp(10f), 0, Screen.dp(3f), ColorUtils.alphaColor(dark ? .5f : .18f, 0xff000000));
    layoutCapsuleRect(rect);
    if (collapseFactor > 0f) {
      // the collapsed bar takes the accent colour so it is seen on a white list
      shadowPaint.setColor(ColorUtils.fromToArgb(shadowPaint.getColor(), ColorUtils.alphaColor(.85f, Theme.getColor(ColorId.iconActive)), collapseFactor));
    }
    float cr = rect.height() / 2f;
    c.drawRoundRect(rect, cr, cr, shadowPaint);
    int contentSave = -1;
    if (collapseFactor > 0f) {
      if (collapseFactor >= 1f) {
        drawMenuIfOpen(c, dark, filling);
        return;
      }
      contentSave = c.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), (int) (255 * (1f - collapseFactor)));
    }
    rect.set(padding(), top, getMeasuredWidth() - padding(), top + h);

    // variant A (user 2026-10-06 «нижнее меню первый вариант»): the selected icon lights up in the accent colour with a soft
    // shadow and a small hop; a line along the bottom edge slides under it and rounds the corner at the edge tabs
    int active = Theme.getColor(ColorId.iconActive);
    float cy = top + h / 2f;
    float left = padding(), right = getMeasuredWidth() - padding(), bottom = top + h;
    float straight = right - left - 2 * r;
    outline.reset();
    outline.moveTo(left + r, top);
    outline.lineTo(right - r, top);
    rect.set(right - 2 * r, top, right, bottom);
    outline.arcTo(rect, -90f, 180f, false);
    outline.lineTo(left + r, bottom);
    rect.set(left, top, left + 2 * r, bottom);
    outline.arcTo(rect, 90f, 180f, false);
    outline.close();
    outlineMeasure.setPath(outline, false);
    float edge0 = Math.max(0f, 1f - selected), edge3 = Math.max(0f, 1f - Math.abs(selected - (ICONS.length - 1)));
    float len = Screen.dp(36f) + Screen.dp(16f) * (edge0 + edge3);
    float x = tabCenterX(selected) - Screen.dp(10f) * edge0 + Screen.dp(10f) * edge3;
    float sCenter = straight + (float) Math.PI * r + (right - r - x); // distance along the outline, clockwise from the top-left
    line.reset();
    outlineMeasure.getSegment(sCenter - len / 2f, sCenter + len / 2f, line, true);
    if (lineFactor > 0f) {
      linePaint.setColor(ColorUtils.alphaColor(lineFactor, active));
      linePaint.setStrokeWidth(Screen.dp(3f));
      c.drawPath(line, linePaint);
    }

    int inactive = Theme.getColor(ColorId.icon);
    for (int i = 0; i < icons.length; i++) {
      float f = Math.max(0f, 1f - Math.abs(selected - i));
      float icx = tabCenterX(i);
      // user 2026-10-06 21:2x: no shadow / glow under the selected icon
      float scale = i == popTab ? 1f + .16f * (float) Math.sin(Math.PI * popFactor) : 1f;
      int color = ColorUtils.fromToArgb(inactive, active, f);
      if (scale != 1f) {
        c.save();
        c.scale(scale, scale, icx, cy);
      }
      Drawables.drawCentered(c, icons[i], icx, cy, Paints.getPorterDuffPaint(color));
      if (scale != 1f) {
        c.restore();
      }
    }
    if (contentSave != -1) {
      c.restoreToCount(contentSave);
    }
    drawMenuIfOpen(c, dark, filling);
  }

  private void drawMenuIfOpen (Canvas c, boolean dark, int filling) {
    int active = Theme.getColor(ColorId.iconActive), inactive = Theme.getColor(ColorId.icon);

    if (menuIds != null && menuFactor > 0f) {
      layoutMenu();
      int alpha = (int) (255 * Math.min(1f, menuFactor));
      float scale = .7f + .3f * menuFactor;
      c.save();
      c.scale(scale, scale, tabCenterX(TAB_SETTINGS), menuRect.bottom + Screen.dp(8f)); // grows out of «Settings»
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
        float iconCx = menuRect.left + Screen.dp(16f + 12f);
        if (menuDrawables[k] != null) {
          Drawables.drawCentered(c, menuDrawables[k], iconCx, rowCy, ip);
        } else if (menuLetters != null && menuColors != null) {
          fillPaint.setColor(ColorUtils.alphaColor(menuFactor, menuColors[k]));
          c.drawCircle(iconCx, rowCy, Screen.dp(15f), fillPaint);
          lettersPaint.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoMedium());
          lettersPaint.setTextSize(Screen.dp(12f));
          lettersPaint.setColor(ColorUtils.alphaColor(menuFactor, 0xffffffff));
          String l = menuLetters[k] != null ? menuLetters[k] : "";
          c.drawText(l, iconCx - lettersPaint.measureText(l) / 2f, rowCy + Screen.dp(4.5f), lettersPaint);
        }
        ip.setAlpha(oldAlpha);
        textPaint.setColor(ColorUtils.alphaColor(menuFactor, textColor));
        c.drawText(menuTitles[k], menuRect.left + Screen.dp(16f + 24f + 16f), rowCy + Screen.dp(5.5f), textPaint);
      }
      c.restore();
    }
  }
}