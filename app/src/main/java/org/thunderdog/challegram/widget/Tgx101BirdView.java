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
package org.thunderdog.challegram.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * TGx101 (user 2026-10-20/21, mockup «Кнопка-птица» №8 «Язычок у края» + «Веер» Б, first ring): a half-capsule with the
 * bird pressed to the screen's edge, over every chat list, when the side menu is on and ✎ is off.
 * Tap — the side menu. Hold — a ring of up to 5 actions around the finger; the finger slides to one and lets go.
 * Holding 2.5 s without moving lifts the bird: it follows the finger and sticks to the nearer edge where it is let go.
 * A hint at the bottom says so until the bird was moved once.
 */
public class Tgx101BirdView extends View {
  public interface Callback {
    /** an action of the ring ({@link org.thunderdog.challegram.ui.Tgx101Bird#ACTION_STORAGE} etc.) */
    void onBirdAction (int action);
    /** the accounts ring: an account was picked ({@code accountId}) or «Add account» ({@code -1}) */
    void onBirdAccount (int accountId);
    /** room taken at the bottom by the host (folders at the bottom etc.) */
    default int birdBottomInset () { return 0; }
    /** the ring's items as they are now (null — keep) */
    default @Nullable Item[] birdItems () { return null; }
  }

  public static final class Item {
    final int id, icon, color;
    final String title;
    final int accountId; // != 0 → the account's avatar instead of the icon
    @Nullable AvatarReceiver avatar;
    @Nullable Drawable drawable;

    public Item (int id, int icon, int color, String title) {
      this(id, icon, color, title, 0);
    }

    public Item (int id, int icon, int color, String title, int accountId) {
      this.id = id; this.icon = icon; this.color = color; this.title = title; this.accountId = accountId;
    }
  }

  private static final float TAB_W = 38f, TAB_H = 50f, RING_R = 84f, ITEM = 46f;
  private static final long HOLD_MS = 400, MOVE_MS = 2500;
  // the bird of the mockup, 24×24
  private static final String BIRD = "M22.96,9.56 L20.39,8.82 L18.58,7.12 L16.47,7.24 L14.19,9.11 L14.03,9.11 L11.42,4.31 L6.79,1.41 L6.46,1.29 L7.00,3.56 L6.95,3.89 L6.38,3.73 L1.95,1.87 L2.32,2.70 L8.15,13.08 L8.32,13.57 L1.25,22.30 L1.00,22.71 L16.47,14.23 L16.71,13.61 L17.91,9.31 L18.08,9.15 L18.66,9.15 L18.66,9.44 L18.24,10.84 L18.33,11.17 L22.26,12.21 L22.38,12.08 L23.00,9.77 Z";

  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), tabPaint = new Paint(Paint.ANTI_ALIAS_FLAG),
    textPaint = new Paint(Paint.ANTI_ALIAS_FLAG), birdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path birdPath = new Path(), birdScaled = new Path();
  private final Matrix matrix = new Matrix();
  private final RectF rect = new RectF();
  private final LinearGradient gradient;
  private final int touchSlop;

  private @Nullable Callback callback;
  private @Nullable Tdlib tdlib;
  private boolean active = true;

  // place: side and height (a share of the room), per orientation
  private boolean right = true;
  private float yShare = .62f;

  // the ring
  private Item[] items = new Item[0];
  private float[] highlight = new float[0];
  private int highlighted = -1;
  private boolean ringOpen, accountsRing, ringByFinger;
  private float ring; // 0..1
  private float ringCx, ringCy;
  private long ringAnimStart;
  private float ringFrom;

  // the finger
  private boolean pressed, moved, moving;
  private float downX, downY, fingerX, fingerY;
  private float press; // 0..1
  private float lift; // 0..1 — off the edge, following the finger
  private float liftX, liftY; // where the lifted bird is
  private long liftAnimStart = -1;
  private boolean liftOpening;
  private float hint; // 0..1
  private long lastFrame;
  // (user 2026-10-21, variant 1 «как AssistiveTouch»): 3 s untouched the bird pales to 40%; a finger brings it back
  private static final long IDLE_MS = 3000;
  // (user 2026-10-21 «ещё бледнее, но только фон»): the half-capsule pales to 20%, the bird itself stays whole —
  // turning blue, so on a light list it doesn't melt away white on white
  private static final float IDLE_ALPHA = .2f;
  private long lastTouch = SystemClock.uptimeMillis();
  private float shown = 1f;

  private final Runnable holdRunnable = () -> {
    if (pressed && !moving && !ringOpen) openRing(false, true);
  };
  private final Runnable moveRunnable = () -> {
    if (pressed && !moved) startMove();
  };

  public Tgx101BirdView (Context context) {
    super(context);
    touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    gradient = new LinearGradient(0, 0, Screen.dp(TAB_W), 0, 0xff3d8fd6, 0xff5eb0ef, Shader.TileMode.CLAMP);
    tabPaint.setShader(gradient);
    if (Build.VERSION.SDK_INT >= 28) tabPaint.setShadowLayer(Screen.dp(6f), 0, Screen.dp(2f), 0x55000000);
    birdPaint.setColor(0xffffffff);
    textPaint.setTypeface(Fonts.getRobotoMedium());
    textPaint.setTextSize(Screen.dp(12f));
    try {
      Path p = androidx.core.graphics.PathParser.createPathFromPathData(BIRD);
      if (p != null) birdPath.set(p);
    } catch (Throwable ignored) { }
    hint = Settings.instance().tgx101BirdMoveLearned() ? 0f : 1f;
    readPlace();
  }

  public void setCallback (@Nullable Callback callback, @Nullable Tdlib tdlib) {
    this.callback = callback;
    this.tdlib = tdlib;
  }

  public void setActive (boolean active) {
    if (this.active == active) return;
    this.active = active;
    if (!active) closeRing(true);
    if (!active && Build.VERSION.SDK_INT >= 29) { exclusion.setEmpty(); setSystemGestureExclusionRects(java.util.Collections.emptyList()); }
    setVisibility(active ? VISIBLE : GONE);
  }

  public boolean isActive () {
    return active && getVisibility() == VISIBLE;
  }

  public void setItems (Item[] items) {
    this.items = items;
    this.highlight = new float[items.length];
  }

  public boolean isRingOpen () {
    return ringOpen;
  }

  /** back pressed: the ring closes */
  public boolean onBackPressed () {
    if (ringOpen) {
      closeRing(false);
      return true;
    }
    return false;
  }

  @Override
  protected void onConfigurationChanged (Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    readPlace();
    invalidate();
  }

  private boolean landscape () {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  private void readPlace () {
    int value = Settings.instance().tgx101BirdPlace(landscape());
    right = value >= 0;
    yShare = Math.max(0f, Math.min(1f, Math.abs(value) / 10000f));
  }

  // room

  private int bottomRoom () {
    int bottom = getHeight();
    if (Build.VERSION.SDK_INT >= 23 && getRootWindowInsets() != null) {
      int nav = getRootWindowInsets().getSystemWindowInsetBottom();
      int[] at = new int[2];
      getLocationInWindow(at);
      int windowBottom = getRootView().getHeight() - nav;
      bottom = Math.min(bottom, windowBottom - at[1]);
    }
    if (callback != null) bottom -= callback.birdBottomInset();
    return bottom;
  }

  private float minCy () {
    return Screen.dp(TAB_H / 2f + 12f);
  }

  private float maxCy () {
    return Math.max(minCy(), bottomRoom() - Screen.dp(TAB_H / 2f + 12f));
  }

  private float tabCy () {
    float min = minCy(), max = maxCy();
    return min + (max - min) * yShare;
  }

  private void tabRect (RectF out, boolean right, float cy) {
    float h = Screen.dp(TAB_H), w = Screen.dp(TAB_W);
    if (right) {
      out.set(getWidth() - w, cy - h / 2f, getWidth() + h / 2f, cy + h / 2f);
    } else {
      out.set(-h / 2f, cy - h / 2f, w, cy + h / 2f);
    }
  }

  private boolean hitsTab (float x, float y) {
    tabRect(rect, right, tabCy());
    rect.inset(-Screen.dp(10f), -Screen.dp(10f));
    return rect.contains(x, y);
  }

  // touches

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (!isActive()) return false;
    if (forwardTarget != null) {
      forward(forwardTarget, e);
      int action = e.getActionMasked();
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) forwardTarget = null;
      return true;
    }
    float x = e.getX(), y = e.getY();
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        if (ringOpen && !ringByFinger) {
          // the accounts ring waits for a tap
          pressed = true;
          fingerX = x; fingerY = y;
          updateHighlight();
          return true;
        }
        if (ringOpen || !hitsTab(x, y)) return false;
        pressed = true;
        moved = moving = false;
        downX = fingerX = x; downY = fingerY = y;
        if (downEvent != null) downEvent.recycle();
        downEvent = MotionEvent.obtain(e);
        forwardTarget = null;
        animatePress();
        postDelayed(holdRunnable, HOLD_MS);
        postDelayed(moveRunnable, MOVE_MS);
        getParent().requestDisallowInterceptTouchEvent(true);
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (!pressed) return false;
        fingerX = x; fingerY = y;
        if (!moved && Math.hypot(x - downX, y - downY) > touchSlop) {
          moved = true;
          removeCallbacks(moveRunnable);
          if (!ringOpen && !moving) {
            // (user 2026-10-21 «только по удержанию, случайные свайпы не должны её активировать»): moved before the
            // hold — nothing happens, the finger is let go
            removeCallbacks(holdRunnable);
            pressed = false;
            animatePress();
            // (user 2026-10-21 «свайп по птице должен срабатывать, как будто её нет»): the gesture goes on to what lies
            // under the bird — the list, the folders, the side menu's or the system's swipe
            forwardTarget = findUnder(downEvent);
            if (forwardTarget != null) {
              forward(forwardTarget, e);
            }
            getParent().requestDisallowInterceptTouchEvent(false);
            return true;
          }
        }
        if (moving) {
          liftX = x; liftY = y;
          invalidate();
        } else if (ringOpen) {
          updateHighlight();
        }
        return true;
      }
      case MotionEvent.ACTION_UP: {
        if (!pressed) return false;
        pressed = false;
        removeCallbacks(holdRunnable);
        removeCallbacks(moveRunnable);
        animatePress();
        if (moving) {
          finishMove(x, y);
        } else if (ringOpen) {
          fingerX = x; fingerY = y;
          updateHighlight();
          if (highlighted >= 0) {
            pick(highlighted);
          } else if (ringByFinger && !moved) {
            // held and let go on the bird: the ring stays for a tap
            ringByFinger = false;
          } else {
            closeRing(false);
          }
        }
        // (user 2026-10-21 «активация только по удержанию, одиночное касание убери»): a tap does nothing
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (!pressed) return false;
        pressed = false;
        removeCallbacks(holdRunnable);
        removeCallbacks(moveRunnable);
        animatePress();
        if (moving) finishMove(x, y); else closeRing(false);
        return true;
      }
    }
    return pressed;
  }

  private @Nullable MotionEvent downEvent;
  private @Nullable View forwardTarget;

  /** the view under the bird that takes this finger: its siblings below it, top first */
  private @Nullable View findUnder (@Nullable MotionEvent down) {
    if (down == null || !(getParent() instanceof android.view.ViewGroup)) return null;
    android.view.ViewGroup parent = (android.view.ViewGroup) getParent();
    float px = down.getX() + getLeft(), py = down.getY() + getTop();
    for (int i = parent.indexOfChild(this) - 1; i >= 0; i--) {
      View child = parent.getChildAt(i);
      if (child.getVisibility() != VISIBLE) continue;
      float cx = px + parent.getScrollX() - child.getLeft() - child.getTranslationX();
      float cy = py + parent.getScrollY() - child.getTop() - child.getTranslationY();
      if (cx < 0 || cy < 0 || cx >= child.getWidth() || cy >= child.getHeight()) continue;
      if (forward(child, down)) return child;
    }
    return null;
  }

  private boolean forward (View child, MotionEvent e) {
    android.view.ViewGroup parent = (android.view.ViewGroup) getParent();
    MotionEvent copy = MotionEvent.obtain(e);
    copy.offsetLocation(getLeft() + parent.getScrollX() - child.getLeft() - child.getTranslationX(),
      getTop() + parent.getScrollY() - child.getTop() - child.getTranslationY());
    boolean handled = child.dispatchTouchEvent(copy);
    copy.recycle();
    return handled;
  }

  private void pick (int index) {
    Item item = items[index];
    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    if (accountsRing) {
      closeRing(false);
      if (callback != null) callback.onBirdAccount(item.accountId == 0 ? -1 : item.accountId);
      return;
    }
    if (item.id == org.thunderdog.challegram.ui.Tgx101Bird.ACTION_ACCOUNTS && tdlib != null) {
      openAccounts();
      return;
    }
    closeRing(false);
    if (callback != null) callback.onBirdAction(item.id);
  }

  private void updateHighlight () {
    int best = -1;
    float d = (float) Math.hypot(fingerX - ringCx, fingerY - ringCy);
    if (d > Screen.dp(30f) || !ringByFinger) {
      float bestD = Float.MAX_VALUE;
      for (int i = 0; i < items.length; i++) {
        float ix = itemX(i, 1f), iy = itemY(i, 1f);
        float di = (float) Math.hypot(fingerX - ix, fingerY - iy);
        if (di < bestD) { bestD = di; best = i; }
      }
      float limit = ringByFinger ? Screen.dp(RING_R * .8f) : Screen.dp(ITEM * .75f);
      if (bestD > limit) best = -1;
    }
    if (best != highlighted) {
      highlighted = best;
      if (best >= 0) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
      invalidate();
    }
  }

  // the ring

  private float angleOf (int i) {
    int n = items.length;
    float from = 100f, to = 260f; // opens away from the edge
    if (n <= 1) return 180f;
    float spread = Math.min(160f, (accountsRing ? 60f : 40f) * (n - 1));
    from = 180f - spread / 2f; to = 180f + spread / 2f;
    float a = from + (to - from) * i / (n - 1);
    return right ? a : 180f - a;
  }

  private float itemX (int i, float open) {
    double a = Math.toRadians(angleOf(i));
    return ringCx + (float) Math.cos(a) * Screen.dp(RING_R) * open;
  }

  private float itemY (int i, float open) {
    double a = Math.toRadians(angleOf(i));
    return ringCy - (float) Math.sin(a) * Screen.dp(RING_R) * open;
  }

  private void openRing (boolean accounts, boolean byFinger) {
    if (!accounts && !ringOpen && callback != null) {
      Item[] fresh = callback.birdItems();
      if (fresh != null) setItems(fresh);
    }
    if (items.length == 0) return;
    accountsRing = accounts;
    ringByFinger = byFinger;
    float r = Screen.dp(RING_R + ITEM / 2f + 6f);
    float cy = tabCy();
    ringCy = Math.max(r, Math.min(bottomRoom() - r, cy));
    ringCx = right ? getWidth() - Screen.dp(TAB_W / 2f) : Screen.dp(TAB_W / 2f);
    highlighted = -1;
    if (!ringOpen) {
      ringOpen = true;
      ringFrom = ring;
      ringAnimStart = SystemClock.uptimeMillis();
      UI.forceVibrate(this, false);
    }
    org.thunderdog.challegram.Tgx101Diag.mark("bird: " + (accounts ? "accounts ring" : "ring") + " open");
    invalidate();
  }

  private void openAccounts () {
    java.util.List<org.thunderdog.challegram.telegram.TdlibAccount> accounts = org.thunderdog.challegram.telegram.TdlibManager.instance().getActiveAccounts();
    int n = Math.min(accounts.size(), 4);
    Item[] next = new Item[n + 1];
    for (int i = 0; i < n; i++) {
      org.thunderdog.challegram.telegram.TdlibAccount account = accounts.get(i);
      Item item = new Item(i, 0, 0xffa695e7, account.getName(), account.id);
      AvatarReceiver avatar = new AvatarReceiver(this);
      avatar.requestAccount(tdlib, account.id, AvatarReceiver.Options.NONE);
      if (isAttachedToWindow()) avatar.attach();
      item.avatar = avatar;
      next[i] = item;
    }
    next[n] = new Item(-1, org.thunderdog.challegram.R.drawable.baseline_add_24, 0xff6b7f95, org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.AddAccount));
    savedItems = items;
    setItems(next);
    ringOpen = false;
    ring = 0f;
    openRing(true, false);
  }

  private @Nullable Item[] savedItems;

  private void closeRing (boolean now) {
    if (!ringOpen && ring == 0f) return;
    ringOpen = false;
    highlighted = -1;
    ringFrom = ring;
    ringAnimStart = SystemClock.uptimeMillis();
    if (now) ring = 0f;
    invalidate();
  }

  private void restoreItems () {
    if (savedItems != null) {
      for (Item item : items) if (item.avatar != null) item.avatar.destroy();
      setItems(savedItems);
      savedItems = null;
      accountsRing = false;
    }
  }

  // moving

  private void startMove () {
    moving = true;
    closeRing(false);
    liftX = fingerX; liftY = fingerY;
    liftOpening = true;
    liftAnimStart = SystemClock.uptimeMillis();
    UI.forceVibrate(this, true);
    org.thunderdog.challegram.Tgx101Diag.mark("bird: lifted to move");
    invalidate();
  }

  private void finishMove (float x, float y) {
    moving = false;
    liftX = x; liftY = y;
    right = x > getWidth() / 2f;
    float min = minCy(), max = maxCy();
    float cy = Math.max(min, Math.min(max, y));
    yShare = max > min ? (cy - min) / (max - min) : .5f;
    Settings.instance().setTgx101BirdPlace(landscape(), (right ? 1 : -1) * Math.max(1, Math.round(yShare * 10000)));
    if (!Settings.instance().tgx101BirdMoveLearned()) Settings.instance().setTgx101BirdMoveLearned(true);
    liftOpening = false;
    liftAnimStart = SystemClock.uptimeMillis();
    org.thunderdog.challegram.Tgx101Diag.mark("bird: put " + (right ? "right" : "left") + " at " + Math.round(yShare * 100) + "%");
    invalidate();
  }

  private void animatePress () {
    invalidate();
  }

  // drawing

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    for (Item item : items) if (item.avatar != null) item.avatar.attach();
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    for (Item item : items) if (item.avatar != null) item.avatar.detach();
  }

  private static float decelerate (float x) {
    x = Math.max(0f, Math.min(1f, x));
    return 1f - (1f - x) * (1f - x) * (1f - x);
  }

  private boolean step () {
    long now = SystemClock.uptimeMillis();
    float dt = lastFrame == 0 ? 16f : Math.min(48f, now - lastFrame);
    lastFrame = now;
    boolean more = false;
    float k = 1f - (float) Math.exp(-dt / 45f);

    float pressTarget = 0f; // (user 2026-10-21 «свайпаю по птичке, и она активируется»): a touch alone shows nothing — only the hold
    press += (pressTarget - press) * k;
    if (Math.abs(pressTarget - press) > .005f) more = true; else press = pressTarget;

    // ring: 240 ms open, 170 ms close
    long dur = ringOpen ? 240 : 170;
    float t = Math.min(1f, (now - ringAnimStart) / (float) dur);
    float target = ringOpen ? 1f : 0f;
    ring = ringOpen ? ringFrom + (target - ringFrom) * decelerate(t) : ringFrom * (1f - decelerate(t));
    if (t < 1f) more = true;
    else {
      ring = target;
      if (!ringOpen) restoreItems();
    }
    for (int i = 0; i < highlight.length; i++) {
      float h = i == highlighted ? 1f : 0f;
      highlight[i] += (h - highlight[i]) * k * 1.4f;
      if (Math.abs(h - highlight[i]) > .005f) more = true; else highlight[i] = h;
    }

    if (liftAnimStart >= 0) {
      float lt = Math.min(1f, (now - liftAnimStart) / (liftOpening ? 180f : 260f));
      lift = liftOpening ? decelerate(lt) : 1f - decelerate(lt);
      if (lt < 1f) more = true; else liftAnimStart = -1;
    } else {
      lift = moving ? 1f : 0f;
    }

    boolean busy = moving || ringOpen || lift > 0f || ring > 0f;
    if (busy) lastTouch = now;
    float shownTarget = busy || now - lastTouch < IDLE_MS ? 1f : IDLE_ALPHA;
    shown += (shownTarget - shown) * (1f - (float) Math.exp(-dt / (shownTarget > shown ? 40f : 220f)));
    if (Math.abs(shownTarget - shown) > .005f) more = true; else shown = shownTarget;
    float hintTarget = Settings.instance().tgx101BirdMoveLearned() ? 0f : 1f;
    hint += (hintTarget - hint) * k * .5f;
    if (Math.abs(hintTarget - hint) > .005f) more = true; else hint = hintTarget;
    return more;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (!active) return;
    boolean more = step();
    int width = getWidth();

    // dim
    if (ring > 0f) {
      fill.setColor(org.thunderdog.challegram.theme.Theme.isDark() ? ((int) (0x8c * ring) << 24) | 0x070b10 : ((int) (0x66 * ring) << 24) | 0x0b1520);
      c.drawRect(0, 0, width, getHeight(), fill);
    }

    // the ring
    if (ring > 0f) {
      for (int i = 0; i < items.length; i++) {
        float o = decelerate((ring - i * .06f) / .76f);
        if (o <= 0f) continue;
        Item item = items[i];
        float x = itemX(i, o), y = itemY(i, o);
        float scale = (.5f + .5f * o) * (1f + .22f * highlight[i]);
        float r = Screen.dp(ITEM / 2f) * scale;
        int alpha = (int) (255 * Math.min(1f, o * 1.4f));
        if (highlight[i] > 0f) {
          fill.setColor(((int) (alpha * highlight[i]) << 24) | 0xffffff);
          c.drawCircle(x, y, r + Screen.dp(3f), fill);
        }
        if (item.avatar != null) {
          int ar = (int) r;
          item.avatar.setBounds((int) x - ar, (int) y - ar, (int) x + ar, (int) y + ar);
          item.avatar.setPaintAlpha(alpha / 255f);
          if (item.avatar.needPlaceholder()) item.avatar.drawPlaceholder(c);
          item.avatar.draw(c);
          item.avatar.restorePaintAlpha();
        } else {
          fill.setColor((alpha << 24) | (item.color & 0xffffff));
          c.drawCircle(x, y, r, fill);
          if (item.icon != 0) {
            if (item.drawable == null) item.drawable = Drawables.get(getResources(), item.icon);
            Paint ip = Paints.getPorterDuffPaint(0xffffffff);
            int old = ip.getAlpha();
            ip.setAlpha(alpha);
            c.save();
            c.scale(scale, scale, x, y);
            Drawables.drawCentered(c, item.drawable, x, y, ip);
            c.restore();
            ip.setAlpha(old);
          }
        }
        // the name over the one under the finger (all of them in the accounts ring)
        float labelAlpha = accountsRing ? o : highlight[i];
        if (labelAlpha > .02f && item.title != null) {
          boolean hl = highlight[i] > .5f;
          if (accountsRing) {
            // every name, beside its circle on the screen's side — over it they covered the next one (2026-10-21 test)
            drawSideLabel(c, item.title, right ? x - r - Screen.dp(8f) : x + r + Screen.dp(8f), y, labelAlpha * Math.min(1f, o), hl);
          } else {
            drawLabel(c, item.title, x, y - r - Screen.dp(16f), labelAlpha * Math.min(1f, o), hl);
          }
        }
      }
    }

    // the bird
    float cy = tabCy();
    tabRect(rect, right, cy);
    float iconX = right ? width - Screen.dp(TAB_W / 2f) + Screen.dp(1f) : Screen.dp(TAB_W / 2f) - Screen.dp(1f);
    float iconY = cy;
    if (lift > 0f) {
      float h = Screen.dp(TAB_H) * (1f + .08f * lift);
      float l = liftX - h / 2f, t = liftY - h / 2f;
      rect.set(lerp(rect.left, l, lift), lerp(rect.top, t, lift), lerp(rect.right, l + h, lift), lerp(rect.bottom, t + h, lift));
      iconX = lerp(iconX, liftX, lift);
      iconY = lerp(iconY, liftY, lift);
    }
    tabPaint.setAlpha((int) (255 * shown));
    float pale = (1f - shown) / (1f - IDLE_ALPHA); // 0 lit … 1 idle
    birdPaint.setColor(me.vkryl.core.ColorUtils.fromToArgb(0xffffffff, 0xff3d8fd6, pale));
    float s = 1f - .08f * press;
    c.save();
    c.scale(s, s, right ? width : 0, cy);
    matrix.setTranslate(rect.left, 0);
    gradient.setLocalMatrix(matrix);
    float radius = rect.height() / 2f;
    c.drawRoundRect(rect, radius, radius, tabPaint);
    float bird = Screen.dp(24f) / 24f;
    matrix.setScale(bird, bird);
    matrix.postTranslate(iconX - Screen.dp(12f), iconY - Screen.dp(12f));
    birdPath.transform(matrix, birdScaled);
    c.drawPath(birdScaled, birdPaint);
    c.restore();

    // the hint
    if (hint > 0.01f && !ringOpen) {
      String text = org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.Tgx101BirdMoveHint);
      textPaint.setTextSize(Screen.dp(13f));
      float tw = Math.min(width - Screen.dp(48f), textPaint.measureText(text));
      float hh = Screen.dp(34f);
      float bottom = bottomRoom() - Screen.dp(14f);
      rect.set(width / 2f - tw / 2f - Screen.dp(14f), bottom - hh, width / 2f + tw / 2f + Screen.dp(14f), bottom);
      fill.setColor(((int) (0xe6 * hint) << 24) | 0x2a3644);
      c.drawRoundRect(rect, hh / 2f, hh / 2f, fill);
      textPaint.setColor(((int) (255 * hint) << 24) | 0xdfe7f0);
      c.save();
      c.clipRect(rect);
      c.drawText(text, width / 2f - tw / 2f, rect.centerY() + Screen.dp(4.5f), textPaint);
      c.restore();
      textPaint.setTextSize(Screen.dp(12f));
    }

    if (more || pressed) {
      postInvalidateOnAnimation();
    } else {
      lastFrame = 0;
      long idleIn = lastTouch + IDLE_MS - SystemClock.uptimeMillis();
      if (shown > IDLE_ALPHA && idleIn > -1) postInvalidateDelayed(Math.max(16, idleIn + 1));
    }
  }

  private final android.graphics.Rect exclusion = new android.graphics.Rect();
  private final java.util.List<android.graphics.Rect> exclusions = java.util.Collections.singletonList(exclusion);

  /** with the gesture navigation the edge is the system's «back»: not where the bird sits */
  private void updateExclusion (float cy) {
    if (Build.VERSION.SDK_INT < 29) return;
    int half = Screen.dp(TAB_H / 2f + 8f);
    int left = right ? getWidth() - Screen.dp(TAB_W + 8f) : 0, r = right ? getWidth() : Screen.dp(TAB_W + 8f);
    if (exclusion.left != left || exclusion.right != r || exclusion.top != (int) cy - half) {
      exclusion.set(left, (int) cy - half, r, (int) cy + half);
      setSystemGestureExclusionRects(exclusions);
    }
  }

  /** a name ending at {@code edgeX} (right side) or starting there (left side) */
  private void drawSideLabel (Canvas c, String text, float edgeX, float cy, float alpha, boolean accent) {
    textPaint.setTextSize(Screen.dp(12f));
    float tw = Math.min(textPaint.measureText(text), getWidth() * .5f);
    float w = tw + Screen.dp(16f), h = Screen.dp(22f);
    float left = right ? edgeX - w : edgeX;
    rect.set(left, cy - h / 2f, left + w, cy + h / 2f);
    int a = (int) (255 * Math.max(0f, Math.min(1f, alpha)));
    fill.setColor((a << 24) | (accent ? 0x5eb0ef : 0x1d2733));
    c.drawRoundRect(rect, h / 2f, h / 2f, fill);
    textPaint.setColor((a << 24) | (accent ? 0x08131e : 0xe8edf3));
    c.save();
    c.clipRect(rect);
    c.drawText(text, rect.left + Screen.dp(8f), cy + Screen.dp(4f), textPaint);
    c.restore();
  }

  private void drawLabel (Canvas c, String text, float cx, float cy, float alpha, boolean accent) {
    textPaint.setTextSize(Screen.dp(12f));
    float tw = textPaint.measureText(text);
    float h = Screen.dp(22f);
    float left = Math.max(Screen.dp(6f), Math.min(getWidth() - Screen.dp(6f) - tw - Screen.dp(16f), cx - tw / 2f - Screen.dp(8f)));
    rect.set(left, cy - h / 2f, left + tw + Screen.dp(16f), cy + h / 2f);
    int a = (int) (255 * Math.max(0f, Math.min(1f, alpha)));
    fill.setColor((a << 24) | (accent ? 0x5eb0ef : 0x1d2733));
    c.drawRoundRect(rect, h / 2f, h / 2f, fill);
    textPaint.setColor((a << 24) | (accent ? 0x08131e : 0xe8edf3));
    c.drawText(text, rect.left + Screen.dp(8f), cy + Screen.dp(4f), textPaint);
  }

  private static float lerp (float a, float b, float f) {
    return a + (b - a) * f;
  }
}
