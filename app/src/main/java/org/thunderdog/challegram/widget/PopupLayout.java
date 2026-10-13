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
 *
 * File created on 06/12/2016
 */
package org.thunderdog.challegram.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.BackListener;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.navigation.MenuMoreWrapAbstract;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.RootDrawable;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.SensitiveContentContainer;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class PopupLayout extends RootFrameLayout implements FactorAnimator.Target, BaseActivity.ActivityListener, Keyboard.OnStateChangeListener, ActivityResultHandler, Destroyable {
  public interface TouchSectionProvider {
    boolean shouldTouchOutside (float x, float y);
  }

  private @Nullable PopupWindow window;
  private @Nullable View windowAnchorView;
  private @Nullable BaseActivity.ActivityListener activityListener;
  private @Nullable TouchSectionProvider touchSectionProvider;
  private @Nullable Keyboard.OnStateChangeListener keyboardListener;

  private boolean overlayStatusBar;

  public interface DismissListener {
    void onPopupDismiss (PopupLayout popup);
    default void onPopupDismissPrepare (PopupLayout popup) { }
  }

  public interface ShowListener {
    void onPopupCompletelyShown (PopupLayout popup);
  }

  private @Nullable DismissListener dismissListener;
  private @Nullable ShowListener showListener;
  private @Nullable BackListener backListener;

  public PopupLayout (Context context) {
    super(context);
    setKeyboardListener(this);
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    backgroundView = new BackgroundView(context);
    backgroundView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(backgroundView);
  }

  public void setOverlayStatusBar (boolean overlayStatusBar) {
    this.overlayStatusBar = overlayStatusBar;
  }

  public boolean shouldOverlayStatusBar () {
    return overlayStatusBar;
  }

  public void setBackListener (@Nullable BackListener backListener) {
    this.backListener = backListener;
  }

  public void setDismissListener (@Nullable DismissListener dismissListener) {
    this.dismissListener = dismissListener;
  }

  public void setShowListener (@Nullable ShowListener showListener) {
    this.showListener = showListener;
  }

  public void setKeyboardChangeListener (@Nullable Keyboard.OnStateChangeListener keyboardListener) {
    this.keyboardListener = keyboardListener;
  }

  private boolean isKeyboardVisible;

  @Override
  public void onKeyboardStateChanged (boolean isVisible) {
    if (this.isKeyboardVisible != isVisible) {
      this.isKeyboardVisible = isVisible;
      if (keyboardListener != null) {
        keyboardListener.onKeyboardStateChanged(isVisible);
      } else if (boundController != null) {
        boundController.onKeyboardStateChanged(isVisible);
      }
    }
  }

  @Override
  public void closeAdditionalKeyboards () {
    if (keyboardListener != null) {
      keyboardListener.closeAdditionalKeyboards();
    } else if (boundController != null && boundController instanceof Keyboard.OnStateChangeListener) {
      ((Keyboard.OnStateChangeListener) boundController).closeAdditionalKeyboards();
    }
  }

  public void setActivityListener (@Nullable BaseActivity.ActivityListener activityListener) {
    this.activityListener = activityListener;
  }

  @Override
  public void onActivityPause () {
    if (activityListener != null) {
      activityListener.onActivityPause();
    }
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (boundController instanceof ActivityResultHandler) {
      ((ActivityResultHandler) boundController).onActivityResult(requestCode, resultCode, data);
    } else if (boundView instanceof ActivityResultHandler) {
      ((ActivityResultHandler) boundView).onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public void onActivityResume () {
    if (activityListener != null) {
      activityListener.onActivityResume();
    }
  }

  @Override
  public void onActivityDestroy () {
    if (activityListener != null) {
      activityListener.onActivityDestroy();
    }
    dismissWindow();
  }

  @Override
  public void onActivityPermissionResult (int code, boolean granted) {
    if (activityListener != null) {
      activityListener.onActivityPermissionResult(code, granted);
    }
  }

  public void setTouchProvider (@Nullable TouchSectionProvider touchProvider) {
    this.touchSectionProvider = touchProvider;
  }

  private boolean needRootInsets;

  public void setNeedRootInsets () {
    this.needRootInsets = true;
  }

  private boolean needFullScreen;

  public void setNeedFullScreen (boolean needFullScreen) {
    this.needFullScreen = needFullScreen;
  }

  private boolean hideKeyboard;

  public void setHideKeyboard () {
    this.hideKeyboard = true;
  }

  public boolean canHideKeyboard () {
    return needRootInsets || (softInputMode != 0 && isKeyboardVisible);
  }

  public boolean hideSoftwareKeyboard () {
    if (boundController != null) {
      if (boundController.getKeyboardState() || (boundController instanceof MediaViewController && ((MediaViewController) boundController).isEmojiVisible())) {
        boundController.hideSoftwareKeyboard();
        return true;
      }
    }
    return false;
  }

  private ViewController<?> boundController;

  public boolean performOnBackPressed (boolean byHeaderBackPress, boolean commit) {
    return (backListener != null && backListener.onBackPressed(byHeaderBackPress, commit)) || (boundController != null && boundController.performOnBackPressed(false, commit)) || (boundView != null && boundView instanceof BackListener && ((BackListener) boundView).onBackPressed(byHeaderBackPress, commit));
  }

  public void setBoundController (ViewController<?> boundController) {
    this.boundController = boundController;
  }

  public ViewController<?> getBoundController () {
    return boundController;
  }

  private boolean isTemporarilyHidden;

  private void removeWindow () {
    if (needRootInsets) {
      UI.getContext(getContext()).removeFromRoot(this);
    } else if (window != null) {
      try {
        window.dismiss();
      } catch (Throwable ignored) { }
    }
  }

  public void hideTemporarily () {
    if (!isTemporarilyHidden) {
      isTemporarilyHidden = true;
      removeWindow();
    }
  }

  public void restoreIfHidden () {
    if (isTemporarilyHidden) {
      isTemporarilyHidden = false;
      if (needRootInsets) {
        UI.getContext(getContext()).addToRoot(this, shouldOverlayStatusBar());
      } else if (windowAnchorView != null) {
        showSystemWindow(windowAnchorView);
      }
    }
  }

  public boolean dismissOtherPopUps = true;

  public void setDismissOtherPopUps (boolean dismissOtherPopUps) {
    this.dismissOtherPopUps = dismissOtherPopUps;
  }

  public boolean needDismissOtherPopUps () {
    return dismissOtherPopUps;
  }

  private static boolean patchPopupWindow (View container, boolean needFullScreen, boolean disallowScreenshots) {
    WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();
    int newFlags = p.flags;
    newFlags = BitwiseUtils.setFlag(newFlags, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, needFullScreen);
    newFlags = BitwiseUtils.setFlag(newFlags, WindowManager.LayoutParams.FLAG_SECURE, disallowScreenshots);
    if (p.flags != newFlags) {
      p.flags = newFlags;
      return true;
    }
    return false;
  }

  public void checkWindowFlags () {
    if (isHidden || isDismissed || windowAnchorView == null || isTemporarilyHidden)
      return;
    if (window != null) {
      View rootView = window.getContentView().getRootView();
      ViewGroup.LayoutParams layoutParams = rootView.getLayoutParams();
      boolean disallowScreenShots = shouldDisallowScreenshots();
      if (!(layoutParams instanceof WindowManager.LayoutParams) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && !rootView.isAttachedToWindow())) {
        // TODO: analyze in what situations container parameters become `android.widget.FrameLayout$LayoutParams`
        // after that, uncomment code below, if it's caused by root view, not by window detachment
        /*int windowFlags =
          (needFullScreen ? WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS : 0) |
          (disallowScreenShots ? WindowManager.LayoutParams.FLAG_SECURE : 0);
        if (windowFlags != 0) {
          final BaseActivity context = UI.getContext(getContext());
          WindowManager.LayoutParams newParams = new WindowManager.LayoutParams();
          newParams.flags = windowFlags;
          // WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);?
          context.getWindowManager().updateViewLayout(rootView, newParams);
        }*/
        return;
      }
      if (patchPopupWindow(rootView, needFullScreen, disallowScreenShots)) {
        final BaseActivity context = UI.getContext(getContext());
        // WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);?
        context.getWindowManager().updateViewLayout(rootView, rootView.getLayoutParams());
      }
    }
  }

  private void showSystemWindow (View anchorView) {
    final BaseActivity context = UI.getContext(getContext());
    window = new PopupWindow(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    if (softInputMode != 0) {
      window.setSoftInputMode(softInputMode);
      window.setFocusable(true);
      window.setOutsideTouchable(false);
    } else {
      window.setFocusable(false);
      window.setOutsideTouchable(true);
    }
    UI.post(() -> {
      if (isHidden || isDismissed) {
        return;
      }
      int state = context.getActivityState();
      if (state == UI.State.RESUMED) {
        try {
          window.showAtLocation(windowAnchorView = anchorView, Gravity.NO_GRAVITY, 0, 0);
          window.setBackgroundDrawable(new RootDrawable(UI.getContext(getContext())));
          View rootView = window.getContentView().getRootView();
          boolean updated = patchPopupWindow(rootView, needFullScreen, shouldDisallowScreenshots());
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) rootView.getLayoutParams();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            updated = true;
          }
          if (updated) {
            // WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);?
            context.getWindowManager().updateViewLayout(rootView, rootView.getLayoutParams());
          }
          return;
        } catch (Throwable t) {
          Log.e("Cannot show window", t);
        }
        return;
      }
      context.addSimpleStateListener(new BaseActivity.SimpleStateListener() {
        @Override
        public void onActivityStateChanged (BaseActivity activity, int newState, int prevState) {
          if (isHidden || isDismissed) {
            context.removeSimpleStateListener(this);
            return;
          }
          if (newState == UI.State.RESUMED) {
            context.removeSimpleStateListener(this);
            if (!isTemporarilyHidden) {
              showSystemWindow(anchorView);
            }
          }
        }
      });
    });
  }

  public void showBoundWindow (View anchorView) {
    if (hideKeyboard || Device.HIDE_POPUP_KEYBOARD) {
      ViewController<?> c = UI.getContext(getContext()).navigation().getCurrentStackItem();
      if (c != null) {
        c.hideSoftwareKeyboard();
      }
    }
    // context.hideContextualPopups();
    if (needRootInsets) {
      UI.getContext(getContext()).addToRoot(this, shouldOverlayStatusBar());
    } else {
      showSystemWindow(anchorView);
    }

    runOnceViewBecomesReady(this, () -> {
      if (!isHidden) {
        launchRevealAnimation();
      }
    });
  }

  private int softInputMode;

  public void setSoftInputMode (int method) {
    this.softInputMode = method;
  }

  public boolean isBoundWindowShowing () {
    return needRootInsets ? getParent() != null : window != null && window.isShowing();
  }

  private boolean isDismissed;

  public void onCustomHideAnimationComplete () {
    dismissWindow();
  }

  private void dismissWindow () {
    if (!isDismissed) {
      isDismissed = true;
      ((BaseActivity) getContext()).removeWindowFromList(this);
      removeWindow();

      if (dismissListener != null) {
        dismissListener.onPopupDismiss(this);
      }

      performDestroy();
    }
  }

  private boolean isDestroyed;

  public boolean isDestroyed () {
    return isDestroyed;
  }

  @Override
  public void performDestroy () {
    isDestroyed = true;
    for (int i = getChildCount() - 1; i >= 0; i--) {
      View view = getChildAt(i);
      if (view instanceof Destroyable) {
        ((Destroyable) view).performDestroy();
      } else if (view instanceof ViewGroup) {
        Views.destroy((ViewGroup) view);
      }
      removeViewAt(i);
    }
    if (boundController != null) {
      boundController.destroy();
    }
  }

  @Override
  public View getChildAt (int index) {
    return super.getChildAt(index);
  }

  private boolean disableCancelOnTouchDown;

  public void setDisableCancelOnTouchDown (boolean disable) {
    this.disableCancelOnTouchDown = disable;
  }

  public interface TouchDownInterceptor {
    boolean onBackgroundTouchDown (PopupLayout popupLayout, MotionEvent e);
  }

  private TouchDownInterceptor touchDownInterceptor;

  public void setTouchDownInterceptor (TouchDownInterceptor touchDownInterceptor) {
    this.touchDownInterceptor = touchDownInterceptor;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      if (factor == 1f && !disableCancelOnTouchDown) {
        if (touchDownInterceptor == null || !touchDownInterceptor.onBackgroundTouchDown(this, e))
          hideWindow(true);
      } else {
        return !(touchSectionProvider == null || touchSectionProvider.shouldTouchOutside(e.getX(), e.getY()));
      }
    }
    return true;
  }

  public void showNonAnimatedView (View view) {
    if (view == null || view.getParent() != null) {
      throw new IllegalArgumentException();
    }

    animationType = ANIMATION_TYPE_NONE;
    addView(view);
    if (getContext() instanceof BaseActivity) {
      ((BaseActivity) getContext()).showPopupWindow(this);
    }
  }

  private View boundView;

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(this);
    }
  }

  private boolean needRevealStartDelay;

  public void showSimplePopupView (View view, int height) {
    if (view == null || view.getParent() != null) {
      throw new IllegalArgumentException();
    }

    if (view instanceof OptionsLayout && UI.getContext(getContext()).hasMoreWindow()) {
      needRevealStartDelay = true;
    }

    animationType = ANIMATION_TYPE_SIMPLE;
    // TGx101: a sheet opened from the message menu takes over the menu's dim / blur (see tgx101HandOff)
    PopupLayout from = tgx101HandoffFrom != null ? tgx101HandoffFrom.get() : null;
    boolean blurOn = org.thunderdog.challegram.unsorted.Settings.instance().tgx101MenuBlur();
    if (from != null && !tgx101HandoffClaimed && !from.isDismissed) {
      tgx101HandoffClaimed = true;
      tgx101From = from;
      if (backgroundView != null && from.tgx101Background instanceof org.thunderdog.challegram.ui.Tgx101MessageMenu.BlurDrawable) {
        backgroundView.tgx101Blur = ((org.thunderdog.challegram.ui.Tgx101MessageMenu.BlurDrawable) from.tgx101Background).copy(); // the very same picture
      }
      tgx101SystemBlur = from.tgx101SystemBlur && !needRootInsets;
    } else if (blurOn && !needRootInsets && tgx101SystemBlurAvailable(getContext())) {
      tgx101SystemBlur = true; // a window of its own: the system blurs everything behind it, the keyboard too
    } else
    // TGx101 (variant 3): with MagiX «Blur» on, a sheet blurs the screen behind it like the message menu does
    if (blurOn && getContext() instanceof android.app.Activity && backgroundView != null) {
      android.graphics.Bitmap blur = org.thunderdog.challegram.ui.Tgx101MessageMenu.blurredScreen((android.app.Activity) getContext(), 0x26000000);
      backgroundView.tgx101Blur = blur != null ? new org.thunderdog.challegram.ui.Tgx101MessageMenu.BlurDrawable(blur, org.thunderdog.challegram.ui.Tgx101MessageMenu.imeHeight((android.app.Activity) getContext()), 0x26000000) : null;
    }
    view.setTranslationY(currentHeight = height);
    addView(boundView = view);
    ((BaseActivity) getContext()).showPopupWindow(this);
  }

  public View getBoundView () {
    return boundView;
  }

  // TGx101 (user 2026-10-12 21:10 «что с размытием что без такого быть не должно. Переход должен быть бесшовный»; video
  // 2026-10-13 12:15 «ты не убрал мигание»): a menu action that opens a sheet («Delete?», «Share») closed the menu with
  // its dim at once and the sheet dimmed the screen again from zero. Now the menu window stays (its card hidden) until the
  // sheet comes: its dim / blur melts into the sheet's, then it goes. With no sheet it fades out by itself
  private static @Nullable java.lang.ref.WeakReference<PopupLayout> tgx101HandoffFrom;
  private static boolean tgx101HandoffClaimed;
  private @Nullable PopupLayout tgx101From;
  /** What dims the screen behind this popup (the message menu sets it), for the sheet that takes over */
  public @Nullable android.graphics.drawable.Drawable tgx101Background;

  public static void tgx101HandOff (PopupLayout menu) {
    tgx101HandoffFrom = new java.lang.ref.WeakReference<>(menu);
    tgx101HandoffClaimed = false;
  }

  /** After the menu action ran: no sheet took over — the menu's dim fades out and the menu goes */
  public static void tgx101ReleaseDim () {
    PopupLayout menu = tgx101HandoffFrom != null ? tgx101HandoffFrom.get() : null;
    if (menu == null || tgx101HandoffClaimed) {
      return;
    }
    tgx101HandoffFrom = null;
    android.animation.ValueAnimator fade = android.animation.ValueAnimator.ofFloat(1f, 0f);
    fade.setDuration(180);
    fade.setInterpolator(new android.view.animation.AccelerateInterpolator());
    fade.addUpdateListener(a -> menu.tgx101SetDimFactor((float) a.getAnimatedValue()));
    fade.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        menu.hideWindow(false);
      }
    });
    fade.start();
  }

  /** 0…1: how much of the menu's dim / blur is still there */
  public void tgx101SetDimFactor (float factor) {
    if (tgx101Background != null) {
      tgx101Background.setAlpha(Math.round(255 * factor));
    }
    tgx101SetBlurFactor(factor);
  }

  private void tgx101UpdateHandoff (float factor) {
    if (tgx101From == null) {
      return;
    }
    if (factor >= 1f) {
      PopupLayout from = tgx101From;
      tgx101From = null;
      if (tgx101HandoffFrom != null && tgx101HandoffFrom.get() == from) tgx101HandoffFrom = null;
      from.hideWindow(false);
    } else if (backgroundView != null && backgroundView.tgx101Blur == null) {
      // the menu's dim melts into the sheet's — the darkness only goes down, never through zero
      from().tgx101SetDimFactor(1f - factor);
    } // the same blurred picture: the sheet fades it in over the menu's, the menu goes once it is fully covered
  }

  private PopupLayout from () {
    return tgx101From;
  }

  // System blur behind a window of its own (Android 12+): the only way to blur the keyboard as well — a picture of the
  // window can't have it (user 2026-10-13 «клавиатура должна быть заблюрена как и чат»)
  private boolean tgx101SystemBlur;
  private int tgx101LastBlurRadius = -1;

  public static boolean tgx101SystemBlurAvailable (Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    return wm != null && wm.isCrossWindowBlurEnabled();
  }

  public void tgx101SetSystemBlur (boolean systemBlur) {
    this.tgx101SystemBlur = systemBlur;
  }

  public boolean tgx101HasSystemBlur () {
    return tgx101SystemBlur;
  }

  /** 0…1: the system blur behind this window follows its dim */
  public void tgx101SetBlurFactor (float factor) {
    if (!tgx101SystemBlur || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || window == null || !window.isShowing()) {
      return;
    }
    int radius = Math.round(Screen.dp(18f) * Math.max(0f, Math.min(1f, factor)));
    if (radius == tgx101LastBlurRadius) {
      return;
    }
    tgx101LastBlurRadius = radius;
    try {
      View rootView = window.getContentView().getRootView();
      WindowManager.LayoutParams lp = (WindowManager.LayoutParams) rootView.getLayoutParams();
      if (radius > 0) {
        lp.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
      } else {
        lp.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
      }
      lp.setBlurBehindRadius(radius);
      UI.getContext(getContext()).getWindowManager().updateViewLayout(rootView, lp);
    } catch (Throwable t) {
      Log.e("TGx101 blur behind", t);
    }
  }

  public void showMoreView (MenuMoreWrapAbstract menuWrap) {
    if (menuWrap == null) {
      throw new IllegalArgumentException();
    }

    if (menuWrap.getParent() != null) {
      ((ViewGroup) menuWrap.getParent()).removeView(menuWrap);
    }

    final boolean anchorCenter = menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_CENTER;
    final boolean anchorRight = menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_RIGHT;
    final int padding = Screen.dp(8f);
    final int itemsWidth = menuWrap.getItemsWidth();
    int cx = anchorRight != Lang.rtl() ? itemsWidth - padding : Screen.dp(17f);
    int cy = menuWrap.shouldPivotBottom() ? menuWrap.getItemsHeight() - padding : padding;

    if (Config.REVEAL_ANIMATION_AVAILABLE && anchorRight) {
      animationType = ANIMATION_TYPE_MORE_REVEAL;
      menuWrap.setAlpha(0f);
      menuWrap.setScaleX(1f);
      menuWrap.setScaleY(1f);
    } else {
      animationType = ANIMATION_TYPE_MORE_SCALE;
      menuWrap.setAlpha(0f);
      menuWrap.setScaleX(MenuMoreWrap.START_SCALE);
      menuWrap.setScaleY(MenuMoreWrap.START_SCALE);
    }

    menuWrap.setPivotX(cx);
    menuWrap.setPivotY(cy);

    addView(boundView = menuWrap);
    ((BaseActivity) getContext()).showPopupWindow(this);
  }

  private void hideMoreWrap () {
    MenuMoreWrapAbstract menuWrap = (MenuMoreWrapAbstract) getContentChild();

    if (menuWrap == null) {
      return;
    }

    final AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        dismissWindow();
      }
    };

    Animator menuAnimator = null;

    if (Config.REVEAL_ANIMATION_AVAILABLE && menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_RIGHT) {
      try {
        menuAnimator = android.view.ViewAnimationUtils.createCircularReveal(menuWrap, (int) menuWrap.getPivotX(), (int) menuWrap.getPivotY(), menuWrap.getRevealRadius(), 0);
        menuAnimator.setInterpolator(MenuMoreWrap.REVEAL_INTERPOLATOR);
        menuAnimator.setDuration(MenuMoreWrap.REVEAL_DURATION);
      } catch (Throwable t) {
        Log.w("Cannot create circular reveal", t);
        menuAnimator = null;
      }
    }

    if (menuAnimator != null) {
      menuAnimator.addListener(listener);
      menuAnimator.start();
    } else {
      menuWrap.scaleOut(listener);
    }
  }

  public interface AnimatedPopupProvider {
    void prepareShowAnimation ();
    void launchShowAnimation (PopupLayout popup);
    boolean launchHideAnimation (PopupLayout popup, FactorAnimator originalAnimator);
  }

  private AnimatedPopupProvider customAnimatorProvider;

  public void setAnimationProvider (AnimatedPopupProvider provider) {
    this.customAnimatorProvider = provider;
  }

  public void showAnimatedPopupView (View view, AnimatedPopupProvider provider) {
    this.animationType = ANIMATION_TYPE_CUSTOM;
    this.customAnimatorProvider = provider;
    provider.prepareShowAnimation();
    addView(boundView = view);
    ((BaseActivity) getContext()).showPopupWindow(this);
  }

  public boolean shouldDisallowScreenshots () {
    ViewController<?> c = boundController;
    if (c != null && c.shouldDisallowScreenshots()) {
      return true;
    }
    if (boundView instanceof SensitiveContentContainer) {
      return ((SensitiveContentContainer) boundView).shouldDisallowScreenshots();
    }
    return false;
  }

  private boolean isHidden;

  public boolean isWindowHidden () {
    return isHidden;
  }

  public void hideWindow (boolean animated) {
    if (isHidden) {
      return;
    }

    isHidden = true;
    if (tgx101From != null) { // closed before it finished coming in: the menu goes with it
      PopupLayout from = tgx101From;
      tgx101From = null;
      from.hideWindow(false);
    }

    if (animated) {
      launchHideAnimation();
    } else {
      if (dismissListener != null) {
        dismissListener.onPopupDismissPrepare(this);
      }
      dismissWindow();
    }
  }

  // Animation

  @Override
  protected View getMeasureTarget () {
    return boundController != null ? boundController.getValue() : this;
  }

  private static final int ANIMATION_TYPE_NONE = -1;
  private static final int ANIMATION_TYPE_SIMPLE = 0;
  private static final int ANIMATION_TYPE_CUSTOM = 1;
  private static final int ANIMATION_TYPE_MORE_SCALE = 2;
  private static final int ANIMATION_TYPE_MORE_REVEAL = 3;

  private int animationType;
  private boolean revealAnimationLaunched, hideAnimationLaunched;
  private int currentHeight;

  private boolean hasCompletelyShown;

  private void launchRevealAnimation () {
    if (!revealAnimationLaunched) {
      revealAnimationLaunched = true;
      switch (animationType) {
        case ANIMATION_TYPE_SIMPLE: {
          animateRevealFactor(1f);
          break;
        }
        case ANIMATION_TYPE_CUSTOM: {
          customAnimatorProvider.launchShowAnimation(this);
          break;
        }
        case ANIMATION_TYPE_MORE_SCALE:
        case ANIMATION_TYPE_MORE_REVEAL: {
          Animator.AnimatorListener listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd (Animator animation) {
              factor = 1f;
              onCustomShowComplete();
            }
          };

          MenuMoreWrapAbstract menuWrap = (MenuMoreWrapAbstract) getContentChild();

          if (menuWrap == null) {
            return;
          }

          if (animationType == ANIMATION_TYPE_MORE_SCALE) {
            if (menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_CENTER) {
              final int itemsWidth = menuWrap.getItemsWidth();
              menuWrap.setPivotX(itemsWidth / 2f);
            }
            menuWrap.scaleIn(listener);
            return;
          }

          final boolean anchorRight = menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_RIGHT;
          final int padding = Screen.dp(8f);
          final int itemsWidth = menuWrap.getItemsWidth();
          int cx = anchorRight != Lang.rtl() ? itemsWidth - padding : Screen.dp(17f);
          int cy = menuWrap.shouldPivotBottom() ? menuWrap.getItemsHeight() - padding : padding;

          if (animationType == ANIMATION_TYPE_MORE_REVEAL && Config.REVEAL_ANIMATION_AVAILABLE) {
            try {
              int newCx = Lang.rtl() ? (int) ((float) Screen.dp(49f) * .5f) : itemsWidth - (int) ((float) Screen.dp(49f) * .5f);
              int newCy = Size.getHeaderPortraitSize() / 2;
              if (menuWrap.shouldPivotBottom()) {
                newCy = menuWrap.getItemsHeight() - newCy;
              }
              Animator animator = android.view.ViewAnimationUtils.createCircularReveal(menuWrap, newCx, newCy, 0, menuWrap.getRevealRadius());
              animator.addListener(listener);
              animator.setInterpolator(MenuMoreWrap.REVEAL_INTERPOLATOR);
              animator.setDuration(MenuMoreWrap.REVEAL_DURATION);
              animationType = ANIMATION_TYPE_MORE_REVEAL;
              cx = newCx;
              cy = newCy;

              menuWrap.setPivotX(cx);
              menuWrap.setPivotY(cy);

              animator.start();
              menuWrap.setAlpha(1f);
              return;
            } catch (Throwable t) {
              Log.w("Cannot create circular reveal", t);
            }
          }

          menuWrap.setAlpha(0f);
          menuWrap.setScaleX(MenuMoreWrap.START_SCALE);
          menuWrap.setScaleY(MenuMoreWrap.START_SCALE);

          menuWrap.setPivotX(cx);
          menuWrap.setPivotY(cy);

          animationType = ANIMATION_TYPE_MORE_SCALE;
          menuWrap.scaleIn(listener);


          break;
        }
      }
    }
  }

  public interface PopupHeightProvider {
    int getCurrentPopupHeight ();
  }

  private @Nullable PopupHeightProvider popupHeightProvider;

  public void setPopupHeightProvider (@Nullable PopupHeightProvider popupHeightProvider) {
    this.popupHeightProvider = popupHeightProvider;
  }

  private void launchHideAnimation () {
    if (!hideAnimationLaunched) {
      hideAnimationLaunched = true;

      if (boundController != null) {
        boundController.onPrepareToDismissPopup();
      }
      if (dismissListener != null) {
        dismissListener.onPopupDismissPrepare(this);
      }

      switch (animationType) {
        case ANIMATION_TYPE_SIMPLE: {
          if (customAnimatorProvider != null) {
            animationType = ANIMATION_TYPE_CUSTOM;
            if (customAnimatorProvider.launchHideAnimation(this, animator)) {
              break;
            }
            animationType = ANIMATION_TYPE_SIMPLE;
          }
          if (popupHeightProvider != null) {
            currentHeight = popupHeightProvider.getCurrentPopupHeight();
          } else {
            View view = getContentChild();
            currentHeight = view != null ? view.getMeasuredHeight() : 0;
          }
          animateRevealFactor(0f);
          break;
        }
        case ANIMATION_TYPE_CUSTOM: {
          if (!customAnimatorProvider.launchHideAnimation(this, animator)) {
            if (popupHeightProvider != null) {
              currentHeight = popupHeightProvider.getCurrentPopupHeight();
            } else {
              View view = getContentChild();
              currentHeight = view != null ? view.getMeasuredHeight() : 0;
            }
            animationType = ANIMATION_TYPE_SIMPLE;
            animateRevealFactor(0f);
          }
          break;
        }
        case ANIMATION_TYPE_MORE_REVEAL:
        case ANIMATION_TYPE_MORE_SCALE: {
          hideMoreWrap();
          break;
        }
        case ANIMATION_TYPE_NONE: { // TGx101: views shown without animation used to never close
          dismissWindow();
          break;
        }
      }
    }
  }

  private static final int REVEAL_ANIMATOR = 0;
  private FactorAnimator animator;

  public void animateRevealFactor (float toFactor) {
    if (animator == null) {
      animator = new FactorAnimator(REVEAL_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, factor);
    }
    // TGx101 (user's video 2026-10-12 20:19 «сама анимация появления окна»): a sheet comes in softly — longer, easing
    // out — and leaves quicker, speeding up, instead of the same short 180 ms both ways
    if (animationType == ANIMATION_TYPE_SIMPLE) {
      boolean in = toFactor > factor;
      // exactly the official app's BottomSheet: in — 400 ms «ease out quint», out — 180 ms «ease out»
      animator.setInterpolator(in ? androidx.core.view.animation.PathInterpolatorCompat.create(.23f, 1f, .32f, 1f)
        : androidx.core.view.animation.PathInterpolatorCompat.create(0f, 0f, .58f, 1f));
      animator.setDuration(in ? 400l : 180l);
    }
    if (toFactor == 1f && needRevealStartDelay) {
      animator.setStartDelay(MenuMoreWrap.REVEAL_DURATION);
    } else {
      animator.setStartDelay(0);
    }
    animator.animateTo(toFactor);
  }

  public View getContentChild () {
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      View view = getChildAt(i);
      if (!(view instanceof BackgroundView)) {
        return view;
      }
    }
    return null;
  }

  private BackgroundView backgroundView;


  private class BackgroundView extends View {
    @Nullable org.thunderdog.challegram.ui.Tgx101MessageMenu.BlurDrawable tgx101Blur;

    public BackgroundView (Context context) {
      super(context);
      setAlpha(0f);
    }

    @Override
    public void draw (Canvas c) {
      super.draw(c);
      if (useStatusBar) {
        final int color = ColorUtils.color(animationType == ANIMATION_TYPE_SIMPLE ? 0 : (int) (255f * .3f), 0);
        c.drawRect(0, 0, getMeasuredWidth(), HeaderView.getTopOffset(), Paints.fillingPaint(color));
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (tgx101Blur != null && animationType == ANIMATION_TYPE_SIMPLE) {
        tgx101Blur.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        tgx101Blur.draw(c);
        return; // the dim is already in the picture
      }
      // TGx101: sheets dim like the official app's — 20 % black in any theme; other popups keep the theme's dim
      final int color = ColorUtils.color(animationType == ANIMATION_TYPE_SIMPLE ? 51 : (int) (255f * (Theme.getPopupOverlayAlpha())), 0);
      int offset = HeaderView.getTopOffset();
      if (useStatusBar && offset != 0) {
        c.drawRect(0, offset, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(color));
      } else {
        c.drawColor(color);
      }
    }
  }

  private float factor;

  public void setRevealFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      switch (animationType) {
        case ANIMATION_TYPE_SIMPLE: {
          View view = getContentChild();
          if (view != null) {
            view.setTranslationY(currentHeight * (1f - factor));
            // TGx101: the sheet shows itself at once and grows the last few percent, anchored to its bottom
            // (the official sheet only slides — no fade or growth)
          }
          break;
        }
      }
      backgroundView.setAlpha(factor);
      if (animationType == ANIMATION_TYPE_SIMPLE) {
        tgx101SetBlurFactor(factor);
      }
      tgx101UpdateHandoff(factor);
    }
  }

  public void setHideBackground (boolean hide) {
    backgroundView.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);
  }

  public boolean hadCompletelyShown () {
    return hasCompletelyShown;
  }

  public void onCustomShowComplete () {
    hasCompletelyShown = true;
    if (showListener != null) {
      showListener.onPopupCompletelyShown(this);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case REVEAL_ANIMATOR: {
        setRevealFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case REVEAL_ANIMATOR: {
        if (finalFactor == 0f) {
          dismissWindow();
        } else if (finalFactor == 1f) {
          onCustomShowComplete();
        }
        UI.getContext(getContext()).notifyBackPressAvailabilityChanged();
        break;
      }
    }
  }

  private boolean useStatusBar;

  public void addStatusBar () {
    useStatusBar = true;
  }

  private @Nullable TooltipOverlayView tooltipOverlayView;

  public TooltipOverlayView tooltipManager () {
    if (tooltipOverlayView == null) {
      tooltipOverlayView = new TooltipOverlayView(getContext());
      tooltipOverlayView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      tooltipOverlayView.setAvailabilityListener((overlayView, hasChildren) -> {
        if (hasChildren) {
          if (tooltipOverlayView.getParent() != null)
            return;
          addView(tooltipOverlayView);
        } else {
          removeView(tooltipOverlayView);
        }
      });
    }
    return tooltipOverlayView;
  }

  public final TooltipOverlayView.TooltipInfo showErrorTooltip (ViewController<?> context, View view, CharSequence text) {
    return showTooltip(context, view, R.drawable.baseline_error_24, text);
  }

  public final TooltipOverlayView.TooltipInfo showWarningTooltip (ViewController<?> context, View view, CharSequence text) {
    return showTooltip(context, view, R.drawable.baseline_warning_24, text);
  }

  public final TooltipOverlayView.TooltipInfo showInfoTooltip (ViewController<?> context, View view, CharSequence text) {
    return showTooltip(context, view, R.drawable.baseline_info_24, text);
  }

  public final TooltipOverlayView.TooltipInfo showTooltip (ViewController<?> context, View view, @DrawableRes int icon, CharSequence text) {
    return tooltipManager()
      .builder(view)
      .show(context, null, icon, text);
  }

  public static PopupLayout parentOf (View view) {
    ViewParent parent = view.getParent();
    while (parent != null && !(parent instanceof PopupLayout)) {
      parent = parent.getParent();
    }
    return (PopupLayout) parent;
  }

  // Drawing



  // We're going deeper: popups inside popups


}
