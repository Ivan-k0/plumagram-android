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
  public static final int TAB_CHATS = 0, TAB_CONTACTS = 1, TAB_CALLS = 2, TAB_SETTINGS = 3, TAB_PROFILE = 4;

  public static final float HEIGHT_DP = 58f, MARGIN_DP = 14f, CORNER_DP = 18f;

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
    R.drawable.baseline_settings_24,
    R.drawable.baseline_account_circle_24 // «Profile» — drawn as the account's avatar once it's known
  };

  // TGx101 (user 2026-10-18): which tabs stand in the capsule, left to right (Settings → MagiX → «Bottom menu tabs»).
  // Everything outside speaks of tabs by kind (TAB_*), inside the capsule they are slots 0..tabs.length-1
  private int[] tabs = {TAB_CHATS, TAB_CONTACTS, TAB_CALLS, TAB_SETTINGS};

  public void setTabs (int[] tabs) {
    if (java.util.Arrays.equals(this.tabs, tabs)) return;
    int current = currentTab;
    this.tabs = tabs;
    closeMenu();
    setSelectedTab(slotOf(current) != -1 ? current : TAB_CHATS, false);
  }

  public boolean hasTab (int tab) {
    return slotOf(tab) != -1;
  }

  private int slotOf (int tab) {
    for (int i = 0; i < tabs.length; i++) {
      if (tabs[i] == tab) return i;
    }
    return -1;
  }

  // «Profile» (the approved mockup «Вкладка Профиль», 1): the active account's small avatar in a ring — grey, blue when the tab is chosen
  private final org.thunderdog.challegram.loader.AvatarReceiver profileAvatar = new org.thunderdog.challegram.loader.AvatarReceiver(this);
  private boolean hasProfileAvatar;

  public void setProfileAccount (org.thunderdog.challegram.telegram.Tdlib tdlib) {
    long myUserId = tdlib.myUserId();
    if (myUserId != 0) {
      profileAvatar.requestUser(tdlib, myUserId, org.thunderdog.challegram.loader.AvatarReceiver.Options.NONE); // follows a new photo
    } else {
      profileAvatar.requestAccount(tdlib, tdlib.id(), org.thunderdog.challegram.loader.AvatarReceiver.Options.NONE);
    }
    hasProfileAvatar = true;
    invalidate();
  }

  private final Drawable[] icons = new Drawable[ICONS.length];
  private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final RectF hairRect = new RectF();
  private final Paint blurFill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final android.graphics.Path blurClip = new android.graphics.Path();
  private final int[] blurLoc = new int[2], blurUnderLoc = new int[2];
  private Object blurNode; // android.graphics.RenderNode on Android 12+

  /** Draws what's under the capsule (the views below it in the parent), blurred, clipped to it. False — no blur here. */
  private boolean drawBlurBehind (Canvas c, RectF r, float radius) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || !c.isHardwareAccelerated() || !(getParent() instanceof android.view.ViewGroup))
      return false;
    int w = (int) r.width(), h = (int) r.height();
    if (w <= 0 || h <= 0) return false;
    android.view.ViewGroup parent = (android.view.ViewGroup) getParent();
    int index = parent.indexOfChild(this);
    android.graphics.RenderNode node = (android.graphics.RenderNode) blurNode;
    if (node == null) {
      node = new android.graphics.RenderNode("tgx101CapsuleBlur");
      float b = Screen.dp(14f);
      node.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(b, b, android.graphics.Shader.TileMode.CLAMP));
      blurNode = node;
    }
    node.setPosition(0, 0, w, h);
    getLocationInWindow(blurLoc);
    float x = blurLoc[0] + r.left, y = blurLoc[1] + r.top;
    android.graphics.RecordingCanvas rc = node.beginRecording(w, h);
    try {
      for (int i = 0; i < index; i++) {
        View under = parent.getChildAt(i);
        if (under == null || under.getVisibility() != View.VISIBLE || under.getWidth() == 0 || under.getAlpha() == 0f) continue;
        under.getLocationInWindow(blurUnderLoc);
        // View.draw ignores the view's own alpha: a screen fading in / out between tabs (user video 2026-10-08 18:49) was
        // taken at full strength — the menu showed the gone Contacts, or nothing of the chats coming back
        float alpha = under.getAlpha();
        int save = alpha < 1f ? rc.saveLayerAlpha(null, Math.round(alpha * 255)) : rc.save();
        rc.translate(blurUnderLoc[0] - x, blurUnderLoc[1] - y);
        under.draw(rc);
        rc.restoreToCount(save);
      }
    } finally {
      node.endRecording();
    }
    blurClip.reset();
    blurClip.addRoundRect(r, radius, radius, android.graphics.Path.Direction.CW);
    int save = c.save();
    c.clipPath(blurClip);
    c.translate(r.left, r.top);
    c.drawRenderNode(node);
    c.restoreToCount(save);
    return true;
  }

  // the blur follows the list under it: redraw when anything scrolls (no redraw loop — only on scroll)
  private final android.view.ViewTreeObserver.OnScrollChangedListener blurScroll = () -> {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && Tgx101NavCapsule.this.collapseFactor < 1f) invalidate();
  };

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    getViewTreeObserver().addOnScrollChangedListener(blurScroll);
    profileAvatar.attach();
    if (menuAvatars != null) for (org.thunderdog.challegram.loader.AvatarReceiver r : menuAvatars) if (r != null) r.attach();
  }

  @Override
  protected void onDetachedFromWindow () {
    getViewTreeObserver().removeOnScrollChangedListener(blurScroll);
    profileAvatar.detach();
    if (menuAvatars != null) for (org.thunderdog.challegram.loader.AvatarReceiver r : menuAvatars) if (r != null) r.detach();
    super.onDetachedFromWindow();
  }
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
    scrollAnimator.setDuration(180); // user 2026-10-06 22:1x: comes back as fast as it hides
    scrollAnimator.setInterpolator(new DecelerateInterpolator());
    scrollAnimator.addUpdateListener(a -> {
      scrollHide = (float) a.getAnimatedValue();
      invalidate();
    });
    scrollAnimator.start();
  }

  /** Follows ✎ frame by frame (its factor may overshoot a little — the curve is the same) */
  public void setScrollHideFactor (float factor) {
    if (scrollAnimator != null) {
      scrollAnimator.cancel();
      scrollAnimator = null;
    }
    float f = Math.max(0f, Math.min(1f, factor)); // ✎ overshoots; the capsule doesn't bounce
    if (f > 0f && menuIds != null) return;
    if (scrollHide != f) {
      scrollHide = f;
      invalidate();
    }
  }

  private float scrollHideOffset () {
    return scrollHide * ((Screen.dp(HEIGHT_DP + 12f) + marginPx()) + getPaddingBottom());
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
    if (true) return; // user 2026-10-07 «убрать эту полоску гуляющую по меню, она мешает»: no line, the lit icon shows the tab
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
  private int menuSlot = -1; // the tab the hold menu grows out of
  private final Runnable longPress = () -> {
    if (pressedTab != -1) menuSlot = pressedTab;
    if (pressedTab != -1 && callback != null && callback.onTabLongClick(tabs[pressedTab])) {
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
    return (getMeasuredWidth() - padding() * 2 - Screen.dp(12f)) / tabs.length;
  }

  private float tabCenterX (float tab) {
    return padding() + Screen.dp(6f) + tabWidth() * (tab + .5f);
  }

  // user 2026-10-07 22:46 «опусти его на ту же высоту, как и поле ввода, если отступ выключен»: without the bottom gap the
  // capsule stands 8 dp above the edge, like the floating message field
  // user 2026-10-08 10:3x «если человек поднимает поле ввода нашей опцией, то и меню поднимается вместе с полем»: the same
  // lift as the floating field — the bottom gap's part above the navigation bar plus 8 dp
  private int marginPx () {
    int gap = org.thunderdog.challegram.unsorted.Settings.instance().needBottomGap() ? Math.max(0, Screen.dp(16f) - getPaddingBottom()) : 0;
    return gap + Screen.dp(8f);
  }

  private float capsuleTop () {
    return getMeasuredHeight() - getPaddingBottom() - marginPx() - Screen.dp(HEIGHT_DP);
  }

  private int tabAt (float x, float y) {
    if (collapsed) return -1;
    float top = capsuleTop();
    if (y < top || y > top + Screen.dp(HEIGHT_DP) || x < padding() || x > getMeasuredWidth() - padding())
      return -1;
    int tab = (int) ((x - padding() - Screen.dp(6f)) / tabWidth());
    return Math.max(0, Math.min(tabs.length - 1, tab));
  }

  /** Slides the pill; the screen behind the tab opens right after (see MainController). */
  public void setSelectedTab (int kind, boolean animated) {
    currentTab = kind;
    final int tab = Math.max(0, slotOf(kind)); // a tab that isn't in the capsule (switched off meanwhile): «Chats»
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
  private org.thunderdog.challegram.loader.AvatarReceiver[] menuAvatars; // accounts in the «Profile» hold menu
  private String[] menuBadges; // on the right of a row: the unread count of an account
  private int menuChecked = -1; // the active account: ✓ on the right
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
    openMenu(ids, icons, titles, letters, colors, null, null, null, -1);
  }

  /** The accounts menu of «Profile»: accountIds[i] != -1 → that account's avatar, badges[i] on the right, ✓ on {@code checked} */
  public void openAccountMenu (int[] ids, int[] icons, String[] titles, org.thunderdog.challegram.telegram.Tdlib tdlib, int[] accountIds, String[] badges, int checked) {
    openMenu(ids, icons, titles, null, null, tdlib, accountIds, badges, checked);
  }

  private void openMenu (int[] ids, int[] icons, String[] titles, String[] letters, int[] colors,
                         org.thunderdog.challegram.telegram.Tdlib tdlib, int[] accountIds, String[] badges, int checked) {
    destroyMenuAvatars();
    if (tdlib != null && accountIds != null) {
      menuAvatars = new org.thunderdog.challegram.loader.AvatarReceiver[accountIds.length];
      for (int i = 0; i < accountIds.length; i++) {
        if (accountIds[i] == -1) continue;
        menuAvatars[i] = new org.thunderdog.challegram.loader.AvatarReceiver(this);
        menuAvatars[i].requestAccount(tdlib, accountIds[i], org.thunderdog.challegram.loader.AvatarReceiver.Options.NONE);
        if (isAttachedToWindow()) menuAvatars[i].attach();
      }
    }
    menuBadges = badges;
    menuChecked = checked;
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
      destroyMenuAvatars();
      menuBadges = null;
      menuChecked = -1;
      invalidate();
    });
  }

  private void destroyMenuAvatars () {
    if (menuAvatars != null) {
      for (org.thunderdog.challegram.loader.AvatarReceiver r : menuAvatars) if (r != null) r.destroy();
      menuAvatars = null;
    }
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
    float w = Math.min(getMeasuredWidth() - padding() * 2, textWidth + Screen.dp(16f + 24f + 16f + 20f) + (menuBadges != null || menuChecked != -1 ? Screen.dp(44f) : 0));
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
          tab = Math.max(0f, Math.min(tabs.length - 1, tab));
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
          callback.onTabClick(tabs[tab]);
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

  /** TGx101: draw only the icons (no capsule background) — for the menu → message field transition snapshot */
  public boolean tgx101IconsOnly;

  /** The capsule's rectangle in this view's coordinates, or false while collapsed / hidden */
  /** Set by the chat list: show the menu at once, without its fade (the transition overlay just drew it) */
  public Runnable tgx101OnMorphBack;

  public boolean tgx101CapsuleRect (RectF out) {
    // hidden or not: it hides itself as the chat opens and shows after it closes — the overlay stands in for it meanwhile
    if (collapseFactor > 0f || getWidth() == 0) return false;
    float top = capsuleTop();
    out.set(padding(), top, getMeasuredWidth() - padding(), top + Screen.dp(HEIGHT_DP));
    return scrollHide == 0f;
  }

  // TGx101 (user 2026-10-08 10:44, exteraGram video «просто и понятно»): the menu belongs to the chat list — while a
  // chat slides over the list, the menu stays with the list and the chat's edge covers it
  private float tgx101CoverX = Float.NaN;

  public boolean tgx101IsCovered () {
    return !Float.isNaN(tgx101CoverX);
  }

  /** The x (in screen pixels) where a sliding screen starts covering the menu, NaN — nothing covers it */
  public void tgx101SetCoveredFrom (float x) {
    tgx101SetCoveredFrom(x, false);
  }

  private boolean tgx101CoverFromLeft;

  /** {@code fromLeft} — the sliding screen covers everything to the LEFT of x (back gesture from the right edge: the
   *  chat leaves to the left and uncovers the menu from the right) */
  public void tgx101SetCoveredFrom (float x, boolean fromLeft) {
    if ((Float.isNaN(x) ? !Float.isNaN(tgx101CoverX) : x != tgx101CoverX) || fromLeft != tgx101CoverFromLeft) {
      tgx101CoverX = x;
      tgx101CoverFromLeft = fromLeft;
      invalidate();
    }
  }

  @Override
  public void draw (@androidx.annotation.NonNull Canvas c) {
    if (Float.isNaN(tgx101CoverX)) {
      super.draw(c);
      return;
    }
    int save = c.save();
    float edge = tgx101CoverX - getLeft() - getTranslationX();
    if (tgx101CoverFromLeft) {
      c.clipRect(Math.min(getWidth(), Math.max(0f, edge)), 0, getWidth(), getHeight());
    } else {
      c.clipRect(0, 0, Math.max(0f, edge), getHeight());
    }
    super.draw(c);
    c.restoreToCount(save);
  }

  private void drawCapsule (Canvas c) {
    float top = capsuleTop();
    float h = Screen.dp(HEIGHT_DP);
    // user 2026-10-07 22:46 / 10-08 «меню не исправил углы?»: the corners of the floating message field (18 dp), not a pill
    float r = Math.min(h / 2f, Screen.dp(CORNER_DP));
    int filling = Theme.getColor(ColorId.filling);
    boolean dark = Theme.isDark();
    shadowPaint.setColor(ColorUtils.alphaColor(dark ? .94f : .92f, filling));
    shadowPaint.setShadowLayer(Screen.dp(10f), 0, Screen.dp(3f), ColorUtils.alphaColor(dark ? .5f : .18f, 0xff000000));
    layoutCapsuleRect(rect);
    if (collapseFactor > 0f) {
      // the collapsed bar takes the accent colour so it is seen on a white list
      shadowPaint.setColor(ColorUtils.fromToArgb(shadowPaint.getColor(), ColorUtils.alphaColor(.85f, Theme.getColor(ColorId.iconActive)), collapseFactor));
    }
    float cr = Math.min(rect.height() / 2f, Screen.dp(CORNER_DP));
    // user 2026-10-08 «прозрачность меню делай как в чатах поле ввода»: on Android 12+ the chats under it, blurred, through
    // a 65 % filling with a hairline (no shadow, like the field); 90 % without the blur
    if (tgx101IconsOnly) {
      // a snapshot for the menu → field transition: the morph overlay draws the capsule itself
    } else if (collapseFactor < 1f && drawBlurBehind(c, rect, cr)) {
      blurFill.setColor(ColorUtils.fromToArgb(ColorUtils.alphaColor(.65f, filling), shadowPaint.getColor(), collapseFactor));
      c.drawRoundRect(rect, cr, cr, blurFill);
      float half = Math.max(1, Screen.dp(.5f)) / 2f;
      hairRect.set(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half);
      c.drawRoundRect(hairRect, cr, cr, Paints.strokeSeparatorPaint(Theme.separatorColor()));
    } else {
      if (collapseFactor < 1f) shadowPaint.setColor(ColorUtils.fromToArgb(ColorUtils.alphaColor(.9f, filling), shadowPaint.getColor(), collapseFactor));
      c.drawRoundRect(rect, cr, cr, shadowPaint);
    }
    int contentSave = -1;
    if (collapseFactor > 0f) {
      if (collapseFactor >= 1f) {
        drawMenuIfOpen(c, dark, filling);
        return;
      }
      //noinspection deprecation — the flags overload exists since API 1 (Android 4.x has no 5-argument one)
      contentSave = c.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), (int) (255 * (1f - collapseFactor)), Canvas.ALL_SAVE_FLAG);
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
    rect.set(right - 2 * r, top, right, top + 2 * r);
    outline.arcTo(rect, -90f, 90f, false);
    outline.lineTo(right, bottom - r);
    rect.set(right - 2 * r, bottom - 2 * r, right, bottom);
    outline.arcTo(rect, 0f, 90f, false);
    outline.lineTo(left + r, bottom);
    rect.set(left, bottom - 2 * r, left + 2 * r, bottom);
    outline.arcTo(rect, 90f, 90f, false);
    outline.lineTo(left, top + r);
    rect.set(left, top, left + 2 * r, top + 2 * r);
    outline.arcTo(rect, 180f, 90f, false);
    outline.close();
    outlineMeasure.setPath(outline, false);
    float edge0 = Math.max(0f, 1f - selected), edge3 = Math.max(0f, 1f - Math.abs(selected - (tabs.length - 1)));
    float len = Screen.dp(36f) + Screen.dp(16f) * (edge0 + edge3);
    float x = tabCenterX(selected) - Screen.dp(10f) * edge0 + Screen.dp(10f) * edge3;
    float sCenter = straight + (float) Math.PI * r + (h - 2 * r) + (right - r - x); // distance along the outline, clockwise from the top-left
    line.reset();
    outlineMeasure.getSegment(sCenter - len / 2f, sCenter + len / 2f, line, true);
    if (lineFactor > 0f) {
      linePaint.setColor(ColorUtils.alphaColor(lineFactor, active));
      linePaint.setStrokeWidth(Screen.dp(3f));
      c.drawPath(line, linePaint);
    }

    int inactive = Theme.getColor(ColorId.icon);
    for (int i = 0; i < tabs.length; i++) {
      float f = Math.max(0f, 1f - Math.abs(selected - i));
      float icx = tabCenterX(i);
      // user 2026-10-06 21:2x: no shadow / glow under the selected icon
      float scale = i == popTab ? 1f + .16f * (float) Math.sin(Math.PI * popFactor) : 1f;
      int color = ColorUtils.fromToArgb(inactive, active, f);
      if (scale != 1f) {
        c.save();
        c.scale(scale, scale, icx, cy);
      }
      if (tabs[i] == TAB_PROFILE && hasProfileAvatar) {
        float ar = Screen.dp(12f);
        profileAvatar.setBounds((int) (icx - ar), (int) (cy - ar), (int) (icx + ar), (int) (cy + ar));
        if (profileAvatar.needPlaceholder()) profileAvatar.drawPlaceholder(c);
        profileAvatar.draw(c);
        Paint ring = Paints.getProgressPaint(color, Screen.dp(2f));
        c.drawCircle(icx, cy, ar + Screen.dp(3f), ring);
      } else {
        Drawables.drawCentered(c, icons[tabs[i]], icx, cy, Paints.getPorterDuffPaint(color));
      }
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
      c.scale(scale, scale, tabCenterX(menuSlot != -1 ? menuSlot : tabs.length - 1), menuRect.bottom + Screen.dp(8f)); // grows out of its tab
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
        if (menuAvatars != null && k < menuAvatars.length && menuAvatars[k] != null) {
          org.thunderdog.challegram.loader.AvatarReceiver av = menuAvatars[k];
          int ar = Screen.dp(15f);
          av.setBounds((int) iconCx - ar, (int) rowCy - ar, (int) iconCx + ar, (int) rowCy + ar);
          av.setPaintAlpha(Math.min(1f, menuFactor));
          if (av.needPlaceholder()) av.drawPlaceholder(c);
          av.draw(c);
          av.restorePaintAlpha();
        } else if (menuDrawables[k] != null) {
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
        float titleRight = menuRect.right - Screen.dp(16f);
        if (k == menuChecked) {
          // the active account: a ✓ in the accent colour on the right
          Paint check = Paints.getProgressPaint(ColorUtils.alphaColor(menuFactor, active), Screen.dp(2f));
          float cx0 = menuRect.right - Screen.dp(24f);
          android.graphics.Path p = line;
          p.reset();
          p.moveTo(cx0 - Screen.dp(6f), rowCy);
          p.lineTo(cx0 - Screen.dp(1.5f), rowCy + Screen.dp(4.5f));
          p.lineTo(cx0 + Screen.dp(6.5f), rowCy - Screen.dp(4.5f));
          c.drawPath(p, check);
          titleRight = cx0 - Screen.dp(14f);
        } else if (menuBadges != null && k < menuBadges.length && menuBadges[k] != null) {
          // another account's unread count in a small accent pill
          lettersPaint.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoMedium());
          lettersPaint.setTextSize(Screen.dp(12f));
          float tw = lettersPaint.measureText(menuBadges[k]);
          float bw = Math.max(Screen.dp(22f), tw + Screen.dp(12f)), bh = Screen.dp(20f);
          float br = menuRect.right - Screen.dp(14f);
          rect.set(br - bw, rowCy - bh / 2f, br, rowCy + bh / 2f);
          fillPaint.setColor(ColorUtils.alphaColor(menuFactor, active));
          c.drawRoundRect(rect, bh / 2f, bh / 2f, fillPaint);
          lettersPaint.setColor(ColorUtils.alphaColor(menuFactor, 0xffffffff));
          c.drawText(menuBadges[k], rect.centerX() - tw / 2f, rowCy + Screen.dp(4.2f), lettersPaint);
          titleRight = rect.left - Screen.dp(8f);
        }
        float titleLeft = menuRect.left + Screen.dp(16f + 24f + 16f);
        CharSequence title = android.text.TextUtils.ellipsize(menuTitles[k], textPaint, Math.max(0f, titleRight - titleLeft), android.text.TextUtils.TruncateAt.END);
        c.drawText(title, 0, title.length(), titleLeft, rowCy + Screen.dp(5.5f), textPaint);
      }
      c.restore();
    }
  }
}