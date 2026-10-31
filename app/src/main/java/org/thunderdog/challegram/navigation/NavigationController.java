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
 * File created on 23/04/2015 at 17:36
 */
package org.thunderdog.challegram.navigation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.BackEventCompat;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.SimpleShapeDrawable;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.widget.ShadowView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Future;

public class NavigationController implements Future<View>, ThemeChangeListener, Lang.Listener, SystemBackEventListener {
  static final boolean DROP_SHADOW_ENABLED = true;
  static final boolean USE_PREVIEW_FADE = true;
  static final boolean USE_LAYOUT_LIMITS = false;

  static final int SLOW_HARD_TRANSITION_DURATION = 500;
  static final int HARD_TRANSITION_DURATION = 400; // 400; //400

  static final long TGX101_OPEN_DURATION = 220;
  static final long TGX101_CLOSE_DURATION = 180;
  static final android.view.animation.Interpolator TGX101_OPEN_INTERPOLATOR = new android.view.animation.DecelerateInterpolator(1.5f);
  static final android.view.animation.Interpolator TGX101_CLOSE_INTERPOLATOR = new android.view.animation.DecelerateInterpolator(1.1f);

  /** Progress counted from frames, not the clock: each frame adds at most ~one frame's time, so a slow frame never makes the screen jump. */
  static final class Tgx101FrameSteps {
    private final long duration;
    private final android.animation.TimeInterpolator interpolator;
    private final long maxStep;
    private long lastTime = -1;
    private float progress;
    private boolean endClaimed;

    Tgx101FrameSteps (long duration, android.animation.TimeInterpolator interpolator) {
      this.duration = Math.max(1, duration);
      this.interpolator = interpolator;
      float hz = Screen.refreshRate();
      this.maxStep = Math.max(8, Math.round(1000f / (hz > 1f ? hz : 60f) * 1.35f));
    }

    float next (ValueAnimator animation) {
      long now = animation.getCurrentPlayTime();
      if (lastTime < 0) {
        lastTime = now;
        return interpolator.getInterpolation(0f);
      }
      long dt = Math.max(0, Math.min(now - lastTime, maxStep));
      lastTime = now;
      progress = Math.min(1f, progress + dt / (float) duration);
      return interpolator.getInterpolation(progress);
    }

    boolean isFinished () {
      return progress >= 1f;
    }

    boolean claimEnd () {
      if (endClaimed) return false;
      endClaimed = true;
      return true;
    }
  }

  interface Tgx101FrameValue {
    void apply (float value);
  }

  /** Drives {@code animator} by {@link Tgx101FrameSteps}: {@code apply} gets the eased value 0..1, the animator ends once it reaches 1. */
  static void tgx101FrameStepped (ValueAnimator animator, long duration, android.animation.TimeInterpolator interpolator, Tgx101FrameValue apply) {
    final Tgx101FrameSteps steps = new Tgx101FrameSteps(duration, interpolator);
    final boolean[] ended = new boolean[1];
    animator.setDuration(Math.max(1, duration) * 4);
    animator.setInterpolator(AnimatorUtils.LINEAR_INTERPOLATOR);
    animator.addUpdateListener(animation -> {
      if (ended[0]) return;
      apply.apply(steps.next(animation));
      if (steps.isFinished() && steps.claimEnd()) {
        animation.end();
      }
    });
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        ended[0] = true; // the owner's own end listener puts the screens in place
      }
    });
  }

  static final int MINIMUM_DROP_DURATION = 60; //60
  static final int MAXIMUM_DROP_DURATION = 200; //200
  static final int TGX101_DRAG_DROP_MIN = 180, TGX101_DRAG_DROP_MAX = 320;

  static final int MINIMUM_VERTICAL_DROP_DURATION = 160;
  static final int MAXIMUM_VERTICAL_DROP_DURATION = 300;

  static final float PREVIEW_FADED_FACTOR       = .92f;
  static final float PREVIEW_FADE_CHANGE_FACTOR = .08f;

  static final boolean USE_PREVIEW_TRANSLATION = true;

  private NavigationProcessor processor;

  private RootLayout rootView;
  private NavigationLayout contentWrapper;
  private HeaderView headerView;
  private ShadowView shadowView, shadowTop;
  private View fadeView;
  private FloatingButton floatingButton;

  private boolean isAnimating;

  private ArrayList<ViewController<?>> childWrappers;
  private final Context context;

  public NavigationController (Context context) {
    this.context = context;
    processor = new NavigationProcessor(this);
  }

  public Context getContext () {
    return context;
  }

 /* @Override
  public void onActivityPause () {
    if (childWrappers != null) {
      for (ViewController c : childWrappers) {
        c.onActivityPause();
      }
    }
  }

  @Override
  public void onActivityResume () {
    if (childWrappers != null) {
      for (ViewController c : childWrappers) {
        c.onActivityResume();
      }
    }
  }

  @Override
  public void onActivityDestroy () {
    if (childWrappers != null) {
      for (ViewController c : childWrappers) {
        c.onActivityDestroy();
      }
    }
  }

  @Override
  public void onActivityPermissionResult (int code, boolean granted) {
    if (childWrappers != null) {
      for (ViewController c : childWrappers) {
        c.onActivityPermissionResult(code, granted);
      }
    }
  }*/

  public void onFocus () {
    ViewController<?> c = getStack().getCurrent();
    if (c != null) {
      c.onFocus();
    }
  }

  public void onBlur () {
    ViewController<?> c = getStack().getCurrent();
    if (c != null) {
      c.onBlur();
    }
  }

  public void onPrepareToShow () {
    ViewController<?> c = getStack().getCurrent();
    if (c != null) {
      c.onPrepareToShow();
    }
  }

  public NavigationStack getStack () {
    return processor.getStack();
  }

  public int getStackSize () {
    return processor.getStackSize();
  }

  public @Nullable ViewController<?> getCurrentStackItem () {
    return getStack().getCurrent();
  }

  public @Nullable ViewController<?> getPreviousStackItem () {
    return getStack().getPrevious();
  }

  public boolean isAnimating () {
    return isAnimating;
  }

  public boolean isAnimatingWithEffect () {
    return isAnimating && translationMode != TRANSLATION_NONE;
  }

  public boolean isAnimatingBackward () {
    return isAnimating && !translatingForward && lastTranslation != 0f && lastTranslation != 1f && animatorRunning;
  }

  public boolean isEmpty () {
    return processor.getStack().size() == 0;
  }

  public HeaderView getHeaderView () {
    return headerView;
  }

  public void setShadowsVisibility (int visibility) {
    shadowTop.setVisibility(visibility);
    shadowView.setVisibility(visibility);
  }

  public FloatingButton getFloatingButton () {
    return floatingButton;
  }

  public void showFadeView () {
    if (fadeView.getParent() != null) {
      contentWrapper.removeView(fadeView);
    }
    fadeView.setVisibility(View.VISIBLE);
    contentWrapper.addView(fadeView, 1);
  }

  public void removeFadeView () {
    if (USE_PREVIEW_FADE) {
      fadeView.setVisibility(View.GONE);
      contentWrapper.removeView(fadeView);
    }
  }

  protected final void clearChildWrappers () {
    if (childWrappers != null) {
      childWrappers.clear();
      childWrappers = null;
    }
  }

  public void addChildWrapper (ViewController<?> controller) {
    if (childWrappers == null) {
      childWrappers = new ArrayList<>();
    }
    childWrappers.add(controller);

    controller.applyPlayerOffset(currentPlayerFactor, currentPlayerOffset);
    if (controller.usePopupMode()) {
      if (controller.preventRootInteractions()) {
        rootView.addView(controller.getValue(), 0);
      } else {
        rootView.addView(controller.getValue(), rootView.indexOfChild(headerView)); // TGx101 (user 2026-10-26 «нет кнопки ⋮ в плеере»): right under the header — «two from the end» put the player over it once tgx101Morph came after the header
      }
    } else {
      contentWrapper.addView(controller.getValue());
    }
    controller.attachNavigationController(this);
    syncKeyboardState(controller); // Telegram X ee48c65a: stale keyboard state
    controller.onPrepareToShow();
    org.thunderdog.challegram.ui.Tgx101Bird.sync(controller); // TGx101: the bird goes or stays as the screen starts to show
    controller.onAttachStateChanged(this, true);
    checkDisallowScreenshots();
  }

  public void addChildWrapper (ViewController<?> controller, int index) {
    if (childWrappers == null) {
      childWrappers = new ArrayList<>();
    }
    childWrappers.add(index, controller);

    if (controller.usePopupMode()) {
      rootView.addView(controller.getValue(), 0);
    } else {
      contentWrapper.addView(controller.getValue(), index);
    }
    controller.attachNavigationController(this);
    syncKeyboardState(controller); // Telegram X ee48c65a: stale keyboard state
    controller.onPrepareToShow();
    org.thunderdog.challegram.ui.Tgx101Bird.sync(controller); // TGx101: the bird goes or stays as the screen starts to show
    controller.onAttachStateChanged(this, true);
    checkDisallowScreenshots();
  }

  private void syncKeyboardState (ViewController<?> controller) {
    if (controller.getKeyboardState() && !UI.getContext(context).isKeyboardVisible()) {
      controller.onKeyboardStateChanged(false);
    }
  }

  public void removeChildWrapper (ViewController<?> controller) {
    if (controller == null)
      throw new NullPointerException();

    if (childWrappers != null) {
      childWrappers.remove(controller);
    }

    if (controller.usePopupMode()) {
      rootView.removeView(controller.getValue());
    } else {
      contentWrapper.removeView(controller.getValue());
    }
    controller.onCleanAfterHide();
    controller.detachNavigationController();
    controller.onAttachStateChanged(this, false);
    checkDisallowScreenshots();
  }

  public void removeChildren () {
    if (childWrappers != null) {
      for (int i = childWrappers.size() - 1; i >= 0; i--) {
        removeChildWrapper(childWrappers.get(i));
      }
    }

    contentWrapper.removeAllViews();
    checkDisallowScreenshots();
  }

  public void onKeyboardStateChanged (boolean visible) {
    if (childWrappers != null) {
      for (ViewController<?> c : childWrappers) {
        c.onKeyboardStateChanged(visible);
      }
    }
  }

  private void checkDisallowScreenshots () {
    UI.getContext(context).checkDisallowScreenshots();
  }

  public int getId () {
    return R.id.controller_navigation;
  }

  private View wrap;
  private boolean isAttachedToWindow;

  @Override
  public View getValue () {
    if (wrap == null) {
      wrap = onCreateView(context);
      wrap.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow (View v) {
          isAttachedToWindow = true;
        }

        @Override
        public void onViewDetachedFromWindow (View v) {
          isAttachedToWindow = false;
          // TODO force end animations?
        }
      });
      wrap.setTag(this);
      ThemeManager.instance().addThemeListener(this);
    }
    return wrap;
  }

  public boolean shouldDisallowScreenshots () {
    if (childWrappers != null) {
      for (ViewController<?> controller : childWrappers) {
        if (controller.shouldDisallowScreenshots()) {
          return true;
        }
      }
    }
    return false;
  }

  protected View onCreateView (Context context) {
    rootView = new RootLayout(context);
    rootView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    rootView.setId(R.id.nav_root);

    if (DROP_SHADOW_ENABLED) {
      FrameLayoutFix.LayoutParams params;

      params = FrameLayoutFix.newParams(Size.getNavigationShadowSize(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT);
      params.setMargins(0, HeaderView.getSize(true), 0, 0);

      shadowView = new ShadowView(context);
      shadowView.setLayoutParams(params);
      checkShadowDirection();
      if (needRtl()) {
        shadowView.setSimpleRightShadow(false);
      } else {
        shadowView.setSimpleLeftShadow(false);
      }
      shadowView.setVisibility(View.GONE);

      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getNavigationShadowvertSize(), Gravity.TOP);

      shadowTop = new ShadowView(context);
      shadowTop.setSimpleTopShadow(true);
      shadowTop.setVisibility(View.GONE);
      shadowTop.setLayoutParams(params);
    }

    if (USE_PREVIEW_FADE) {
      fadeView = new View(context);
      fadeView.setBackgroundColor(0xff000000);
      fadeView.setVisibility(View.GONE);
      fadeView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    contentWrapper = new NavigationLayout(context);
    contentWrapper.setController(this);
    contentWrapper.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentWrapper.setId(R.id.nav_wrapper);

    headerView = new HeaderView(context);
    headerView.initWithController(this);
    headerView.setId(R.id.nav_header);

    rootView.addView(contentWrapper);

    if (DROP_SHADOW_ENABLED) {
      rootView.addView(shadowView);
      rootView.addView(shadowTop);
    }

    rootView.addView(headerView);

    tgx101Morph = new Tgx101CapsuleMorph(context);
    tgx101Morph.setVisibility(View.GONE);
    rootView.addView(tgx101Morph, new android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));

    floatingButton = new FloatingButton(context);
    floatingButton.setOnClickListener(v -> {
      ViewController<?> c = processor.getStack().getCurrent();
      if (c != null) {
        c.onFloatingButtonPressed();
      }
    });
    rootView.addView(floatingButton);

    Lang.addLanguageListener(this);

    return rootView;
  }

  public void addViewUnderHeader (View view) {
    rootView.addView(view, rootView.indexOfChild(headerView));
  }

  private ThemeListenerList themeListeners;

  public ThemeListenerList getThemeListeners () {
    if (themeListeners == null) {
      themeListeners = new ThemeListenerList();
    }
    return themeListeners;
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    ViewController<?> c;
    if (isAnimating && ((translatingForward && translationFactor == 1f) || (!translatingForward && translationFactor == 0f))) {
      c = getPreviousStackItem();
    } else {
      c = getCurrentStackItem();
    }
    if (c != null) {
      headerView.resetColors(c, null);
    }
    if (themeListeners != null) {
      themeListeners.onThemeColorsChanged(areTemp);
    }
  }

  private boolean isDestroyed;

  public boolean isDestroyed () {
    return isDestroyed;
  }

  public void reuse () {
    getStack().clear(this);
    clearChildWrappers();
  }

  public void destroy () {
    isDestroyed = true;
    ThemeManager.instance().removeThemeListener(this);
    getStack().clear(this);
    clearChildWrappers();
    Lang.removeLanguageListener(this);
    if (headerView != null)
      headerView.performDestroy();
    if (contentWrapper != null)
      contentWrapper.performDestroy();
    if (floatingButton != null)
      floatingButton.performDestroy();
  }

  // Single

  public void insertController (ViewController<?> controller, int index) {
    if (!isAnimating && controller != null) {
      processor.insertController(controller, index);
    }
  }

  public void initController (ViewController<?> with) {
    if (!isAnimating && with != null) {
      NavigationStack stack = getStack();

      with.postOnAnimationExecute(() ->
        with.addOneShotFocusListener(with.context()::markActivityReady)
      );

      with.getValue();

      stack.clear(this);
      stack.push(with, true);

      removeChildren();
      addChildWrapper(with);
      processor.callFocusDelayed(with);
      getHeaderView().setTitle(with);

      if (with.usePopupMode()) {
        with.applyCustomHeaderAnimations(1f);
      }
    }
  }

  public final void setController (ViewController<?> controller) {
    if (!isAnimating && controller != null) {
      if (getStack().isEmpty()) {
        initController(controller);
      } else {
        processor.setController(controller);
      }
    }
  }

  public ViewController<?> getPendingController () {
    return processor.getFutureRebaseController();
  }

  public void setControllerAnimated (ViewController<?> controller, boolean asForward, boolean saveFirst) {
    if (controller != null) {
      clearStackLock();
      if (getStack().isEmpty()) {
        initController(controller);
      } else {
        processor.setControllerAnimated(controller, asForward, saveFirst);
      }
    }
  }

  void rebaseStack (final ViewController<?> item, boolean asForward, final boolean saveFirst) {
    if (asForward) {
      navigate(item, saveFirst ? MODE_REBASE | MODE_ARG_SAVE_FIRST : MODE_REBASE);
    } else {
      navigate(item, saveFirst ? MODE_REBASE | MODE_ARG_SAVE_FIRST | MODE_FADE : MODE_REBASE | MODE_FADE);
    }
  }

  // Pairs

  public boolean passBackPressToActivity (boolean fromTop) {
    ViewController<?> c = getStack().getCurrent();
    return c != null && c.needPassBackPressToActivity(fromTop);
  }

  public @BackPressMode int performOnBackPressed (boolean fromTop, boolean commit) {
    if (headerView.inSelectMode()) {
      if (commit) {
        headerView.closeSelectMode(true, true);
      }
      return BackPressMode.CUSTOM_ACTION_PERFORMED;
    }
    ViewController<?> c = getStack().getCurrent();
    if (headerView.inSearchMode()) {
      if (c != null && c.closeSearchModeByBackPress(fromTop, commit)) {
        return BackPressMode.CUSTOM_ACTION_PERFORMED;
      }
      if (commit) {
        headerView.closeSearchMode(true, null); // delayed: (c != null && c.inChatSearchMode())
      }
      return BackPressMode.CUSTOM_ACTION_PERFORMED;
    }
    /*if (headerView.inCustomMode()) {
      headerView.closeCustomMode();
      return true;
    }*/
    if (c != null) {
      if (c.performOnBackPressed(fromTop, commit)) {
        return BackPressMode.CUSTOM_ACTION_PERFORMED;
      }
      if (getStackSize() > 1) {
        if (commit) {
          navigateBack();
        }
        return BackPressMode.NAVIGATE_BACK_IN_STACK;
      }
    }
    return BackPressMode.SYSTEM_ACTION_REQUIRED;
  }

  private boolean isCurrentControllerAnimating () {
    ViewController<?> c = getStack().getCurrent();
    return c != null && c.isTransforming();
  }

  final void hideContextualPopups () {
    UI.getContext(context).hideContextualPopups(true);
    DrawerController c = UI.getDrawer(context);
    if (c != null) {
      c.close(0f, null);
    }
  }

  private void clearStackLock () {
    if (getStack().isLocked()) {
      getStack().setIsLocked(false);
    }
  }

  // TGx101 (user 2026-10-06 21:2x, variant C): switching the capsule tabs — the old screen blurs and fades out, the new
  // one comes out of the blur, 180 ms (the blur needs Android 12+, older ones just dissolve)
  private boolean tgx101BlurFade, tgx101BlurForward;

  public void setTgx101BlurFade (boolean blurFade) {
    this.tgx101BlurFade = blurFade;
  }

  private void tgx101ApplyBlurFade (float factor) {
    // factor 1 → the right screen is hidden; the left one fades out as the right comes in, and the other way round
    if (leftWrap != null) leftWrap.setAlpha(factor);
    // the bottom menu blurs these screens: it must redraw with them on every frame, not only on scroll (video 18:49 —
    // after «Contacts → Chats» it kept the empty picture taken when the chats were still invisible)
    for (ViewController<?> c : getStack().getAll()) {
      if (c instanceof org.thunderdog.challegram.ui.MainController) ((org.thunderdog.challegram.ui.MainController) c).tgx101InvalidateCapsule();
    }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
      // only the screen that goes away blurs: blurring the new one too made its first frame expensive (Contacts with
      // 1 265 rows: 146 ms on a Vivo, user 2026-10-06 21:4x «зависание на 12 секунде»)
      float max = Screen.dp(6f);
      if (tgx101BlurForward) {
        tgx101Blur(leftWrap, max * (1f - factor));
      } else {
        tgx101Blur(rightWrap, max * factor);
      }
    }
  }

  private static void tgx101Blur (View view, float radius) {
    if (view == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return;
    view.setRenderEffect(radius < .5f ? null : android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP));
  }

  private void tgx101ClearBlurFade () {
    if (leftWrap != null) leftWrap.setAlpha(1f);
    tgx101Blur(leftWrap, 0f);
    tgx101Blur(rightWrap, 0f);
  }

  /** TGx101: where the running transition goes when {@code blurred} loses focus (the bottom capsule stays put between tabs) */
  public @Nullable ViewController<?> tgx101TransitionTarget (ViewController<?> blurred) {
    if (blurred == currentLeft) return currentRight;
    if (blurred == currentRight) return currentLeft;
    return null;
  }

  public final boolean navigateTo (ViewController<?> controller) {
    if (!isAnimating && getStackSize() > 0 && controller != null && !isCurrentControllerAnimating()) {
      isAnimating = true;
      UI.getContext(context).notifyBackPressAvailabilityChanged();
      clearStackLock();
      processor.navigateTo(controller);
      return true;
    } else {
      return false;
    }
  }

  public final boolean navigateBack () {
    if (!isAnimating && getStack().getCurrentIndex() > 0 && !isCurrentControllerAnimating()) {
      processor.navigateBack();
      return true;
    } else {
      return false;
    }
  }

  private static final int TRANSLATION_HORIZONTAL = 1;
  private static final int TRANSLATION_VERTICAL = 2;

  // TGx101 (user 2026-10-31 «чтобы новый канал выходил снизу, а не сбоку»): the next forward screen rises from below
  private boolean tgx101NextVertical;

  public void tgx101OpenNextFromBelow () {
    tgx101NextVertical = true;
    // only the opening it was asked for: if the channel didn't open, the next screen slides in as usual
    org.thunderdog.challegram.tool.UI.post(() -> tgx101NextVertical = false, 1500);
  }
  private static final int TRANSLATION_FADE = 3;
  private static final int TRANSLATION_NONE = 4;

  private int translationMode;
  private boolean translatingForward;
  private float fromHeaderHeight;
  private float currentWidth, currentHeight, currentPrevWidth;
  private float lastTranslation, lastHeaderFactor;
  private ViewController<?> currentLeft, currentRight;
  private View leftWrap, rightWrap;

  private void preventPopupMode (ViewController<?> current) {
    if (!current.preventRootInteractions()) {
      rootView.removeView(current.getValue());
      rootView.addView(current.getValue(), 0);
    }
    headerView.getFilling().setCollapsed(false);
  }

  private void applyPopupMode (ViewController<?> current) {
    if (!current.preventRootInteractions()) {
      rootView.removeView(current.getValue());
      rootView.addView(current.getValue(), rootView.indexOfChild(headerView));
    }
    headerView.getFilling().setCollapsed(true);
  }

  private float currentPlayerFactor;
  private float currentPlayerOffset;

  protected boolean applyPlayerOffset (float factor, float top) {
    if (currentPlayerFactor != factor) {
      currentPlayerFactor = factor;
      currentPlayerOffset = top;
      if (childWrappers != null) {
        for (ViewController<?> c : childWrappers) {
          c.applyPlayerOffset(factor, top);
        }
      }
      return true;
    }
    return false;
  }

  private void prepareHeaderAnimation (ViewController<?> left, ViewController<?> right, boolean forward, int direction, boolean forceRtl) {
    lastHeaderFactor = forward ? 1f : 0f;
    headerView.openPreview(left, right, forward, direction, lastHeaderFactor, forceRtl);

    if (!forward && right.usePopupMode()) {
      fromHeaderHeight = left.getHeaderHeight();
    } else {
      fromHeaderHeight = headerView.getCurrentHeight();
    }
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    PredictiveGesture.NONE,
    PredictiveGesture.BACK_FROM_LEFT_EDGE,
    PredictiveGesture.BACK_FROM_RIGHT_EDGE
  })
  public @interface PredictiveGesture {
    int
      NONE = 0,
      BACK_FROM_LEFT_EDGE = 1,
      BACK_FROM_RIGHT_EDGE = 2;
  }

  // TGx101 (log 2026-10-21: chats opening with 14–26 ms of GPU on their first frames — both screens rendered into
  // layers): on a 90 Hz+ screen (a strong phone) the screens slide as they are; the layers stay for 60 Hz phones
  private static Boolean tgx101FastScreen;

  private boolean tgx101FastScreen () {
    if (tgx101FastScreen == null) {
      float rate = 60f;
      try {
        android.view.Display d = android.os.Build.VERSION.SDK_INT >= 30 ? getValue().getContext().getDisplay() : null;
        if (d == null) d = ((android.view.WindowManager) getValue().getContext().getSystemService(android.content.Context.WINDOW_SERVICE)).getDefaultDisplay();
        if (android.os.Build.VERSION.SDK_INT >= 23) {
          for (android.view.Display.Mode m : d.getSupportedModes()) rate = Math.max(rate, m.getRefreshRate());
        } else {
          rate = d.getRefreshRate();
        }
      } catch (Throwable ignored) { }
      tgx101FastScreen = rate >= 89f;
      org.thunderdog.challegram.Tgx101Diag.mark("navigation: screen up to " + Math.round(rate) + " Hz — layers " + (tgx101FastScreen ? "off" : "on"));
    }
    return tgx101FastScreen;
  }

  public void prepareFactorAnimation (ViewController<?> left, ViewController<?> right, boolean forward, int direction, @PredictiveGesture int predictiveGestureMode) {
    preventLayout();
    // TGx101 (trace 2026-10-21: the storage pull's first frames spent 7 ms each rendering both screens into layers):
    // the pull following the finger draws them as they are — a sliding screen costs little each frame at 120 Hz
    if (Views.HARDWARE_LAYER_ENABLED && !tgx101ForceRtlOnce && !tgx101FastScreen()) {
      setLayerType(View.LAYER_TYPE_HARDWARE, left, right);
    }

    boolean forceRtl = tgx101ForceRtlOnce;

    if (predictiveGestureMode != PredictiveGesture.NONE) {
      if (Settings.instance().needReduceMotion()) {
        direction = TRANSLATION_FADE;
      } else if (direction == TRANSLATION_HORIZONTAL && predictiveGestureMode == PredictiveGesture.BACK_FROM_RIGHT_EDGE) {
        forceRtl = !Settings.instance().getNewSetting(Settings.SETTING_FLAG_FORCE_DEFAULT_ANIMATION_FOR_RIGHT_SWIPE_EDGE);
      }
    }

    if (forceRtlAnimation != forceRtl) {
      forceRtlAnimation = forceRtl;
      checkShadowDirection();
    }

    if (forward) {
      right.applyPlayerOffset(currentPlayerFactor, currentPlayerOffset);
      addChildWrapper(right);
      if (left.usePopupMode()) {
        preventPopupMode(left);
      }
    } else {
      left.applyPlayerOffset(currentPlayerFactor, currentPlayerOffset);
      addChildWrapper(left, 0);
    }

    if (USE_PREVIEW_FADE) {
      if (direction != TRANSLATION_FADE && direction != TRANSLATION_NONE && !left.usePopupMode()) {
        showFadeView();
      }
    }

    translationFactor = forward ? 1f : 0f;

    currentLeft = left;
    currentRight = right;
    leftWrap = left.getValue();
    rightWrap = right.getValue();
    leftWrap.setAlpha(1f); // TGx101: a fade-through may have left them faded / shrunk
    leftWrap.setScaleX(1f);
    leftWrap.setScaleY(1f);
    rightWrap.setScaleX(1f);
    rightWrap.setScaleY(1f);
    translationMode = direction;
    translatingForward = forward;

    currentWidth = rootView.getMeasuredWidth();
    currentHeight = rootView.getMeasuredHeight();
    currentPrevWidth = -(currentWidth / Size.NAVIGATION_PREVIEW_TRANSLATE_FACTOR);

    if (forward) {
      currentLeft.onBlur();

      switch (translationMode) {
        case TRANSLATION_NONE: {
          lastTranslation = 0;
          leftWrap.setTranslationX(0f);
          rightWrap.setTranslationX(0f);
          rightWrap.setTranslationY(0f);
          rightWrap.setAlpha(1f);
          leftWrap.setAlpha(1f);
          headerView.getFilling().setRestorePixels(false, 0f, 0);
          break;
        }
        case TRANSLATION_FADE: {
          lastTranslation = 0;
          leftWrap.setTranslationX(0f);
          rightWrap.setTranslationX(0f);
          rightWrap.setTranslationY(0f);
          rightWrap.setAlpha(0f);
          headerView.getFilling().setRestorePixels(false, 0f, 0);
          break;
        }
        case TRANSLATION_VERTICAL: {
          lastTranslation = currentHeight;

          leftWrap.setTranslationX(0f);
          rightWrap.setTranslationX(0f);
          rightWrap.setTranslationY(currentHeight);

          headerView.getFilling().setRestorePixels(false, 0f, 0);

          if (USE_PREVIEW_FADE) {
            if (!left.usePopupMode()) {
              fadeView.setAlpha(0f);
              fadeView.setVisibility(View.VISIBLE);
            }
          }

          if (DROP_SHADOW_ENABLED) {
            shadowTop.setTranslationY(currentHeight - Size.getNavigationShadowvertSize());
            shadowTop.setAlpha(1f);
            shadowTop.setVisibility(right.useDropShadow() ? View.VISIBLE : View.GONE);
          }

          break;
        }
        case TRANSLATION_HORIZONTAL: {
          lastTranslation = currentWidth;
          leftWrap.setTranslationX(0f);
          rightWrap.setTranslationX(currentWidth);
          rightWrap.setTranslationY(0f);
          rightWrap.setAlpha(1f);

          headerView.getFilling().setRestorePixels(left.usePopupMode(), 0f, left.getPopupRestoreColor());

          if (USE_PREVIEW_FADE) {
            if (!left.usePopupMode()) {
              fadeView.setAlpha(0f);
              fadeView.setVisibility(View.VISIBLE);
            }
          }

          if (DROP_SHADOW_ENABLED) {
            shadowView.setTranslationX(currentWidth - Size.getNavigationShadowSize());
            shadowView.setAlpha(PREVIEW_FADED_FACTOR);
            shadowView.setVisibility(View.VISIBLE);
          }

          break;
        }
      }
    } else {
      lastTranslation = 0f;
      currentRight.onBlur();

      leftWrap.setVisibility(View.VISIBLE);

      switch (translationMode) {
        case TRANSLATION_NONE: {
          leftWrap.setTranslationX(0f);
          rightWrap.setTranslationX(0f);
          rightWrap.setTranslationY(0f);
          rightWrap.setAlpha(1f);
          leftWrap.setAlpha(1f);

          headerView.getFilling().setRestorePixels(false, 0f, 0);

          break;
        }
        case TRANSLATION_FADE: {
          leftWrap.setTranslationX(0f);
          rightWrap.setTranslationX(0f);
          rightWrap.setTranslationY(0f);

          headerView.getFilling().setRestorePixels(false, 0f, 0);
          break;
        }
        case TRANSLATION_VERTICAL: {
          rightWrap.setTranslationY(0f);
          rightWrap.setTranslationX(0f);
          leftWrap.setTranslationX(0f);
          leftWrap.setTranslationY(0f);

          headerView.getFilling().setRestorePixels(false, 0f, 0);

          if (USE_PREVIEW_FADE) {
            if (!left.usePopupMode()) {
              fadeView.setAlpha(PREVIEW_FADE_CHANGE_FACTOR);
              fadeView.setVisibility(View.VISIBLE);
            }
          }

          if (DROP_SHADOW_ENABLED) {
            shadowTop.setAlpha(1f);
            shadowTop.setTranslationY(-Size.getNavigationShadowvertSize());
            shadowTop.setVisibility(right.useDropShadow() ? View.VISIBLE : View.GONE);
          }

          break;
        }
        case TRANSLATION_HORIZONTAL: {
          rightWrap.setTranslationX(0f);
          if (USE_PREVIEW_TRANSLATION) {
            leftWrap.setTranslationX(currentPrevWidth);
          } else {
            leftWrap.setTranslationX(0f);
          }
          leftWrap.setAlpha(1f);

          headerView.getFilling().setRestorePixels(left.usePopupMode(), -currentPrevWidth, left.getPopupRestoreColor());

          if (USE_PREVIEW_FADE) {
            if (!left.usePopupMode()) {
              fadeView.setAlpha(PREVIEW_FADE_CHANGE_FACTOR);
              fadeView.setVisibility(View.VISIBLE);
            }
          }

          if (DROP_SHADOW_ENABLED) {
            shadowView.setAlpha(1f);
            shadowView.setTranslationX(-Size.getNavigationShadowSize());
            shadowView.setVisibility(View.VISIBLE);
          }

          break;
        }
      }
    }

    prepareHeaderAnimation(left, right, forward, direction, forceRtlAnimation);

    layoutIfRequested();
    onAfterShow();

    preventNextLayouts(forward ? 1 : 2);
  }

  private void onAfterShow () {
    if (childWrappers != null) {
      int size = childWrappers.size();
      for (int i = size - 1; i >= 0; i--) {
        childWrappers.get(i).onAfterShow();
      }
    }
  }

  void navigate (final ViewController<?> controller, int mode) {
    hideContextualPopups();

    final boolean forward = (mode & MODE_BACKWARD) != MODE_BACKWARD;
    final boolean fastAnimation;
    int direction;

    if ((mode & MODE_FADE) == MODE_FADE) {
      direction = TRANSLATION_FADE;
      fastAnimation = false;
    } else {
      if (forward) {
        fastAnimation = controller.forceFastAnimation();
        direction = fastAnimation || controller.forceFadeMode() ? TRANSLATION_FADE : controller.usePopupMode() ? TRANSLATION_VERTICAL : TRANSLATION_HORIZONTAL;
        if (tgx101NextVertical && !fastAnimation) {
          // TGx101: the next channel comes up from below, as the pull that opened it. Not the popup's vertical slide —
          // that one repaints the header and the status bar as a popup's (user 2026-10-31 «шапки испортились»): the
          // screens cross-fade and the new one rises 120 dp at the same time
          direction = TRANSLATION_FADE;
          View view = controller.getValue();
          view.setTranslationY(Screen.dp(120f));
          view.animate().translationY(0f).setDuration(260).setInterpolator(me.vkryl.android.AnimatorUtils.DECELERATE_INTERPOLATOR).start();
        }
        tgx101NextVertical = false;
      } else {
        ViewController<?> current = getStack().getCurrent();
        fastAnimation = controller.forceFastAnimation();
        direction = fastAnimation || current == null || current.forceFadeMode() ? TRANSLATION_FADE : current.usePopupMode() ? TRANSLATION_VERTICAL : TRANSLATION_HORIZONTAL;
      }
      if (Settings.instance().needReduceMotion() && (direction == TRANSLATION_HORIZONTAL)) {
        direction = TRANSLATION_FADE;
      }
    }
    if (tgx101BlurFade) {
      direction = TRANSLATION_FADE; // capsule tabs: «Back to chats» dissolves too instead of sliding
    }
    tgx101BlurForward = forward;

    int rebase = 0;

    if ((mode & MODE_REBASE) == MODE_REBASE) {
      rebase = MODE_REBASE;
      if ((mode & MODE_ARG_SAVE_FIRST) == MODE_ARG_SAVE_FIRST) {
        rebase |= MODE_ARG_SAVE_FIRST;
      }
    }

    if (USE_ASYNC_IN_STEP_2 || !controller.needAsynchronousAnimation() || !isAttachedToWindow) {
      controller.executeAnimationReadyListeners();
      animateNavigate(controller, direction, forward, rebase, fastAnimation);
    } else {
      final int directionFinal = direction;
      final int rebaseFinal = rebase;
      final long timeout = controller.getAsynchronousAnimationTimeout(fastAnimation);
      controller.scheduleAnimation(new Runnable() {
        @Override
        public void run () {
          if (controller.getScheduledAnimation() == this) {
            controller.resetScheduledAnimation();
            if (isDestroyed) {
              controller.destroy();
            } else {
              controller.executeAnimationReadyListeners();
              animateNavigate(controller, directionFinal, forward, rebaseFinal, fastAnimation);
            }
          }
        }
      }, timeout);
    }
  }

  static final int MODE_BACKWARD = 0x01;
  static final int MODE_FORWARD = 0x02;
  static final int MODE_REBASE = 0x04;
  static final int MODE_FADE = 0x08;
  static final int MODE_ARG_SAVE_FIRST = 0x10;

  private boolean animatorRunning;

  private static final boolean USE_ASYNC_IN_STEP_2 = false;

  void animateNavigate (final ViewController<?> controller, int direction, final boolean forward, final int rebase, final boolean fastAnimation) {
    final ViewController<?> left, right;

    if (forward) {
      prepareFactorAnimation(left = getStack().getPrevious(), right = controller, true, direction, PredictiveGesture.NONE);
    } else {
      prepareFactorAnimation(left = controller, right = getStack().getCurrent(), false, direction, PredictiveGesture.NONE);
    }

    final boolean[] isDone = new boolean[1];
    Runnable onDone = () -> {
      setFactor(forward ? 0f : 1f);
      if (tgx101BlurFade) {
        tgx101BlurFade = false;
        tgx101ClearBlurFade();
      }
      isDone[0] = true;
      if (forward) {
        removeFadeView();
        if (rebase != 0) {
          headerView.applyPreview(controller);
          processor.rebaseStack(controller, (rebase & MODE_ARG_SAVE_FIRST) == MODE_ARG_SAVE_FIRST);
        } else {
          headerView.applyPreview(getStack().getCurrent());
          processor.setNextAsCurrent();
        }
        completeNextLayout();
        if (Views.HARDWARE_LAYER_ENABLED) {
          setLayerType(View.LAYER_TYPE_NONE, left, right);
        }
        updateHackyViews();
      } else {
        finishTransaction(FINISH_NAVIGATION);
      }
    };

    if (!isAttachedToWindow) {
      onDone.run();
      return;
    }

    final ValueAnimator animator;

    this.animatorRunning = true;

    animator = AnimatorUtils.simpleValueAnimator();

    final long duration;
    final android.animation.TimeInterpolator interpolator;
    switch (direction) {
      case TRANSLATION_FADE: {
        duration = Config.DEBUG_NAV_ANIM ? 1000l : fastAnimation ? 120l : 180l;
        interpolator = AnimatorUtils.DECELERATE_INTERPOLATOR;
        break;
      }
      case TRANSLATION_VERTICAL: {
        duration = forward ? HARD_TRANSITION_DURATION : SLOW_HARD_TRANSITION_DURATION;
        interpolator = AnimatorUtils.NAVIGATION_INTERPOLATOR;
        break;
      }
      case TRANSLATION_HORIZONTAL:
      default: {
        // (recorded 2026-10-22 against exteraGram at 120 Hz: its chat comes in over ~200 ms easing out, leaves in ~150 ms;
        // ours took 400 ms with a long tail) — user «extera плавнее, анимации приятнее, не такие резкие»
        // (user video 2026-10-27 13:54 «такие анимации плавнее»): a screen whose header grows into a big photo (a profile)
        // gets more time — in 220 ms the header shot up in ~4 frames
        boolean tall = right != null && right.tgx101TallHeaderTransition();
        duration = forward ? (tall ? 320 : TGX101_OPEN_DURATION) : (tall ? 260 : TGX101_CLOSE_DURATION);
        interpolator = forward ? TGX101_OPEN_INTERPOLATOR : TGX101_CLOSE_INTERPOLATOR;
        break;
      }
    }
    // The screens move by frames, each frame at most one frame's step: the chat's heavy first frame (20–40 ms)
    // made the time-driven animation jump 16–21% of the width at once. A late frame now only slows it down.
    final Tgx101FrameSteps steps = new Tgx101FrameSteps(duration, interpolator);
    animator.setDuration(duration * 4);
    animator.setInterpolator(AnimatorUtils.LINEAR_INTERPOLATOR);
    animator.addUpdateListener(animation -> {
      if (!isDone[0]) {
        float value = steps.next(animation);
        setFactor(forward ? 1f - value : value);
        if (steps.isFinished() && steps.claimEnd()) {
          animation.end();
        }
      }
    });

    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        onDone.run();
      }
    });

    if (!fastAnimation) {
      if (forward) {
        animator.setStartDelay(left != null && left.usePopupMode() ? 120l : controller.getStartDelay(true));
      } else {
        animator.setStartDelay(right != null && right.usePopupMode() ? 120l : controller.getStartDelay(false));
      }
    }

    if (USE_ASYNC_IN_STEP_2 && forward && right.needAsynchronousAnimation()) {
      final long timeout = right.getAsynchronousAnimationTimeout(fastAnimation);
      controller.scheduleAnimation(new Runnable() {
        @Override
        public void run () {
          if (controller.getScheduledAnimation() == this) {
            controller.resetScheduledAnimation();
            AnimatorUtils.startAnimator(right.getValue(), animator);
          }
        }
      }, timeout);
    } else {
      AnimatorUtils.startAnimator(forward ? right.getValue() : left.getValue(), animator);
    }
  }

  // Anim

  void setIsAnimating (boolean animating) {
    if (isAnimating != animating) {
      isAnimating = animating;
      ((BaseActivity) context).notifyBackPressAvailabilityChanged();
      ((BaseActivity) context).setIsKeyboardBlocked(animating);
      UI.getContext(context).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_NAVIGATING, animating);
      if (actionMode != null) {
        ((android.view.ActionMode) actionMode).finish();
      }
      if (!animating) {
        processor.checkRebaseMessage();
      }
    }
  }

  private void clearPreview () {
    setFactor(0f);

    headerView.clearPreview();

    removeChildWrapper(currentLeft);

    if (DROP_SHADOW_ENABLED) {
      switch (translationMode) {
        case TRANSLATION_HORIZONTAL: {
          shadowView.setVisibility(View.GONE);
          break;
        }
        case TRANSLATION_VERTICAL: {
          shadowTop.setVisibility(View.GONE);
          break;
        }
      }
    }

    if (USE_PREVIEW_FADE) {
      if (!currentLeft.usePopupMode()) {
        removeFadeView();
      }
    }

    currentRight.onFocus();

    currentLeft = null;
    currentRight = null;

    leftWrap = null;
    rightWrap = null;
  }

  boolean openPreview (float y) {
    return openPreviewImpl(y, false, PredictiveGesture.NONE);
  }

  /** TGx101: the swipe «back» made right to left — the screen below comes in from the right (as in RTL) */
  boolean tgx101OpenPreviewReverse (float y) {
    tgx101ForceRtlOnce = true;
    boolean opened = openPreviewImpl(y, false, PredictiveGesture.NONE);
    tgx101ForceRtlOnce = false;
    return opened;
  }

  public boolean forcePreviewPreviouewItem () {
    return openPreviewImpl(0, true, PredictiveGesture.NONE);
  }

  public void closePreviousPreviewItem () {
    closePreview(0f);
  }

  private boolean forceRtlAnimation, tgx101ForceRtlOnce;

  // TGx101 (user 2026-10-18 «открытие хранилища — как выход из чата свайпом»): a forward step that follows the finger.
  // The new screen is pushed at once and drawn by the factor: 1 — still off screen, 0 — in place; {@code fromLeft} — it
  // comes from the left (the storage lies left of the chats: its own «back» is right to left)
  private @Nullable ViewController<?> tgx101DragForward;

  public boolean tgx101StartDragForward (ViewController<?> controller, boolean fromLeft) {
    if (isAnimating || isEmpty() || controller == null || getStack().getCurrent() == null) return false;
    long t0 = android.os.SystemClock.uptimeMillis();
    View made = controller.getValue();
    View cur = getStack().getCurrent().getValue();
    org.thunderdog.challegram.Tgx101Diag.mark("drag forward: sizes — made " + made.getMeasuredWidth() + "×" + made.getMeasuredHeight() + (made.isLayoutRequested() ? " (layout asked)" : "") + ", current " + (cur != null ? cur.getWidth() + "×" + cur.getHeight() + " in " + (cur.getParent() instanceof View ? ((View) cur.getParent()).getWidth() + "×" + ((View) cur.getParent()).getHeight() : "?") : "?") + ", root " + getValue().getWidth() + "×" + getValue().getHeight());
    hideContextualPopups();
    setIsAnimating(true);
    controller.getValue();
    getStack().push(controller, true);
    controller.executeAnimationReadyListeners();
    long t1 = android.os.SystemClock.uptimeMillis();
    tgx101ForceRtlOnce = fromLeft;
    prepareFactorAnimation(getStack().getPrevious(), controller, true, TRANSLATION_HORIZONTAL, PredictiveGesture.NONE);
    tgx101ForceRtlOnce = false;
    long t2 = android.os.SystemClock.uptimeMillis();
    tgx101DragForward = controller;
    setFactor(1f);
    org.thunderdog.challegram.Tgx101Diag.mark("drag forward: push " + (t1 - t0) + " ms, prepare " + (t2 - t1) + " ms, factor " + (android.os.SystemClock.uptimeMillis() - t2) + " ms");
    return true;
  }

  public boolean tgx101IsDraggingForward () {
    return tgx101DragForward != null;
  }

  /** 0 — where it started, 1 — the new screen in place */
  public void tgx101DragForward (float progress) {
    if (tgx101DragForward != null) setFactor(1f - Math.max(0f, Math.min(1f, progress)));
  }

  /** let go: the rest of the way (open) or back (the new screen goes and is destroyed) */
  public void tgx101EndDragForward (boolean open, float velocity) {
    if (tgx101DragForward == null) return;
    tgx101DragForward = null;
    final float start = getFactor(), end = open ? 0f : 1f;
    final ViewController<?> left = currentLeft, right = currentRight;
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> setFactor(start + (end - start) * AnimatorUtils.getFraction(animation)));
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    float distance = Math.abs(end - start) * currentWidth;
    // (user 2026-10-21 «переход в хранилище через свайп дёрганый — плавным, как в чат и из чата»; on the phone the rest
    // of the way took 1–3 frames: 60–200 ms, half that at its 0.5 animation scale) — the rest of the way as a chat opens
    animator.setDuration(calculateDropDuration(distance, velocity, TGX101_DRAG_DROP_MAX, TGX101_DRAG_DROP_MIN));
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (open) {
          // (trace 2026-10-21: ~80 ms without a frame when the pull finished) — each step timed for the log
          long t0 = android.os.SystemClock.uptimeMillis();
          removeFadeView();
          headerView.applyPreview(getStack().getCurrent());
          long t1 = android.os.SystemClock.uptimeMillis();
          processor.setNextAsCurrent();
          long t2 = android.os.SystemClock.uptimeMillis();
          completeNextLayout();
          long t3 = android.os.SystemClock.uptimeMillis();
          if (Views.HARDWARE_LAYER_ENABLED) {
            setLayerType(View.LAYER_TYPE_NONE, left, right);
          }
          updateHackyViews();
          org.thunderdog.challegram.Tgx101Diag.mark("drag forward done: header " + (t1 - t0) + " ms, next as current " + (t2 - t1) + " ms, layout " + (t3 - t2) + " ms, layers+hacky " + (android.os.SystemClock.uptimeMillis() - t3) + " ms");
        } else {
          // the pushed screen leaves the stack; the header keeps its own title — the new screen's was only its preview
          // (user 2026-10-19: a pull to the storage not taken all the way left the chats' header with no text)
          finishTransaction(FINISH_TGX101_FORWARD_CANCEL);
        }
      }
    });
    animator.start();
  }

  private boolean needRtl () {
    return forceRtlAnimation || Lang.rtl();
  }

  private boolean shadowIsRtl;
  private void checkShadowDirection () {
    boolean rtl = needRtl();
    if (shadowView != null && shadowIsRtl != rtl) {
      shadowIsRtl = rtl;
      if (rtl) {
        shadowView.setSimpleRightShadow(false);
      } else {
        shadowView.setSimpleLeftShadow(false);
      }
    }
  }

  private boolean openPreviewImpl (float y, boolean force, int predictiveGestureMode) {
    if (getStack().getCurrentIndex() <= 0) {
      processor.clearAnimation();
      return false;
    }

    if (isAnimating) {
      return false;
    }

    final ViewController<?> right = getStack().getCurrent();

    if (right == null) {
      return false;
    }

    setIsAnimating(true);

    if (right.usePopupMode()) {
      prepareFactorAnimation(getStack().getPrevious(), right, false, force ? TRANSLATION_NONE : TRANSLATION_VERTICAL, predictiveGestureMode);
    } else {
      prepareFactorAnimation(getStack().getPrevious(), right, false, force ? TRANSLATION_NONE : TRANSLATION_HORIZONTAL, predictiveGestureMode);
    }

    return true;
  }

  void closePreview (float velocity) {
    if (isAnimating && getStack().getCurrentIndex() > 0) {
      if (lastTranslation == 0f || translationMode == TRANSLATION_NONE) {
        forceClosePreview();
        return;
      }

      if (translationMode == TRANSLATION_VERTICAL) {
        translationFactor = lastTranslation / currentHeight;
      } else {
        translationFactor = lastTranslation / currentWidth;
      }

      ValueAnimator animator;

      final float startFactor = getFactor();
      animator = AnimatorUtils.simpleValueAnimator();
      animator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          finishTransaction(FINISH_PREVIEW);
        }
      });

      long duration;
      if (translationMode == TRANSLATION_VERTICAL) {
        duration = calculateDropDuration(lastTranslation, velocity, MAXIMUM_VERTICAL_DROP_DURATION, MINIMUM_VERTICAL_DROP_DURATION);
      } else {
        duration = calculateDropDuration(lastTranslation, velocity, MAXIMUM_DROP_DURATION, MINIMUM_DROP_DURATION);
      }
      tgx101FrameStepped(animator, duration, AnimatorUtils.DECELERATE_INTERPOLATOR, value -> setFactor(startFactor - startFactor * value));
      animator.start();
    }
  }

  private int swipeNavigationLocked = 0;

  public void lockSwipeNavigation () {
    this.swipeNavigationLocked += 1;
  }

  public void unlockSwipeNavigation () {
    if (swipeNavigationLocked > 0) {
      this.swipeNavigationLocked -= 1;
    }
  }

  public boolean swipeNavigationEnabled () {
    return swipeNavigationLocked == 0;
  }

  void forceClosePreview () {
    finishTransaction(FINISH_PREVIEW_FORCE);
  }

  public void preventNextLayouts (int limit) {
    if (USE_LAYOUT_LIMITS) {
      rootView.preventNextLayouts(limit);
      headerView.preventNextLayouts(limit);
      contentWrapper.preventNextLayouts(limit);
    }
  }

  public void completeNextLayout () {
    if (USE_LAYOUT_LIMITS) {
      rootView.completeNextLayout();
      headerView.completeNextLayout();
      contentWrapper.completeNextLayout();
    }
  }

  public void preventLayout () {
    rootView.preventLayout();
    headerView.preventLayout();
    contentWrapper.preventLayout();
  }

  public void setLayerType (final int type, final ViewController<?> left, final ViewController<?> right) {
    if (shadowView != null) {
      Views.setLayerType(shadowView, type);
    }
    if (fadeView != null/* && (left == null || left.allowHardwareMode() || type != View.LAYER_TYPE_HARDWARE) && (right == null || right.allowHardwareMode() || type != View.LAYER_TYPE_HARDWARE)*/) {
      Views.setLayerType(fadeView, type);
    }
    if (shadowTop != null) {
      Views.setLayerType(shadowTop, type);
    }
    if (floatingButton != null && !SimpleShapeDrawable.USE_SOFTWARE_SHADOW) {
      Views.setLayerType(floatingButton, type == View.LAYER_TYPE_HARDWARE && SimpleShapeDrawable.USE_SOFTWARE_SHADOW ? View.LAYER_TYPE_SOFTWARE : type);
    }
    if (left != null && left.allowLayerTypeChanges()) {
      Views.setLayerType(left.getValue(), type);
    }
    if (right != null && right.allowLayerTypeChanges()) {
      Views.setLayerType(right.getValue(), type);
    }
  }

  public void layoutIfRequested () {
    rootView.layoutIfRequested();
    headerView.layoutIfRequested();
    contentWrapper.layoutIfRequested();
  }

  public void cancelLayout () {
    rootView.cancelLayout();
    headerView.cancelLayout();
    contentWrapper.cancelLayout();
  }

  private static final int FINISH_NAVIGATION = 1;
  private static final int FINISH_PREVIEW = 2;
  private static final int FINISH_PREVIEW_FORCE = 3;
  private static final int FINISH_TGX101_FORWARD_CANCEL = 4;

  private void finishTransaction (int mode) {
    rootView.post(() -> {
      ViewController<?> cur = getCurrentStackItem();
      for (ViewController<?> c : getStack().getAll()) {
        if (c instanceof org.thunderdog.challegram.ui.MainController) {
          ((org.thunderdog.challegram.ui.MainController) c).tgx101SyncCapsuleAfterTransition(cur);
        }
      }
    });
    completeNextLayout();

    preventLayout();
    ViewController<?> current = getStack().getPrevious();
    removeFadeView();

    if (Views.HARDWARE_LAYER_ENABLED) {
      setLayerType(View.LAYER_TYPE_NONE, currentLeft, currentRight);
    }

    switch (mode) {
      case FINISH_NAVIGATION: {
        headerView.applyPreview(current);
        if (current != null && current.usePopupMode()) {
          applyPopupMode(current);
        }
        processor.setPrevAsCurrent();
        break;
      }
      case FINISH_PREVIEW: {
        headerView.clearPreview();
        final ViewController<?> previous = getStack().getCurrent();
        if (previous != null) {
          previous.onFocus();
        }
        processor.removePrevious(current);
        break;
      }
      case FINISH_PREVIEW_FORCE: {
        clearPreview();
        processor.removePrevious(current);
        break;
      }
      case FINISH_TGX101_FORWARD_CANCEL: {
        processor.setPrevAsCurrent();
        headerView.clearPreview(); // after the pop: it reads the back button and the colour off the screen that stays
        break;
      }
    }

    layoutIfRequested();

    currentLeft = null;
    currentRight = null;

    leftWrap = null;
    rightWrap = null;

    updateHackyViews();
  }

  void applyPreview (float velocity) {
    if (isAnimating && getStack().getCurrentIndex() > 0) {
      if (translationMode == TRANSLATION_VERTICAL) {
        translationFactor = lastTranslation / currentHeight;
      } else {
        translationFactor = lastTranslation / currentWidth;
      }

      ValueAnimator animator;

      final float startFactor = getFactor();
      final float diffFactor = 1f - startFactor;
      animator = AnimatorUtils.simpleValueAnimator();
      animator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          finishTransaction(FINISH_NAVIGATION);
        }
      });

      long duration;
      android.animation.TimeInterpolator interpolator = AnimatorUtils.DECELERATE_INTERPOLATOR;
      if (translationMode == TRANSLATION_VERTICAL) {
        duration = calculateDropDuration(currentHeight - lastTranslation, velocity, MAXIMUM_VERTICAL_DROP_DURATION, MINIMUM_VERTICAL_DROP_DURATION);
      } else if (velocity <= 0 && startFactor < .05f) {
        // the back button / a quick system back (it reports no movement): the whole way, as exteraGram leaves a chat (~150 ms)
        duration = TGX101_CLOSE_DURATION;
        interpolator = TGX101_CLOSE_INTERPOLATOR;
      } else {
        duration = calculateDropDuration(currentWidth - lastTranslation, velocity, MAXIMUM_DROP_DURATION, MINIMUM_DROP_DURATION);
      }
      // by frames: the first frame of the back (the chat list redrawn under it) took ~17 ms and the chat jumped 16% at once
      tgx101FrameStepped(animator, duration, interpolator, value -> setFactor(startFactor + diffFactor * value));
      animator.start();
    }
  }

  private float translationFactor;
  private Tgx101CapsuleMorph tgx101Morph;

  public void setFactor (float factor) {
    // TGx101 (Vivo, user 2026-10-08 «экран чернеет / назад без анимации»): a quick system back gesture reports NaN progress
    // — the screens were moved to NaN (nothing drawn: black) and the closing slide had nothing to animate
    if (Float.isNaN(factor)) {
      org.thunderdog.challegram.Tgx101Diag.mark("navigation: NaN factor ignored");
      return;
    }
    if (this.translationFactor == factor) return;

    factor = MathUtils.clamp(factor);

    this.translationFactor = factor;

    float px;
    if (translationMode == TRANSLATION_VERTICAL) {
      px = Math.round(currentHeight * factor);
    } else {
      px = currentWidth * factor;
    }

    lastTranslation = px;

    switch (translationMode) {
      case TRANSLATION_FADE: {
        headerView.setTranslation(factor);
        if (currentRight != null && currentRight.tgx101FadeThrough()) {
          float p = 1f - factor; // 0: the screen below, 1: this one
          float out = .35f;
          leftWrap.setAlpha(Math.max(0f, 1f - p / out));
          float in = p <= out ? 0f : (p - out) / (1f - out);
          rightWrap.setAlpha(in);
          float scale = .94f + .06f * in;
          rightWrap.setScaleX(scale);
          rightWrap.setScaleY(scale);
          if (in >= 1f) leftWrap.setAlpha(1f); // done: the one below is hidden anyway, and ready for the way back
          break;
        }
        rightWrap.setAlpha(1f - factor);
        if (tgx101BlurFade) {
          tgx101ApplyBlurFade(factor);
        }

        break;
      }
      case TRANSLATION_HORIZONTAL: {
        headerView.setTranslation(factor);
        if (currentLeft instanceof org.thunderdog.challegram.ui.MainController) {
          // the menu stays with the list, the sliding screen covers it; TGx101 (user's video 2026-10-12 19:35): the back
          // gesture from the RIGHT edge slides the chat to the left — the menu is uncovered from the right then
          float listShift = USE_PREVIEW_TRANSLATION ? (1f - factor) * currentPrevWidth : 0f;
          if (needRtl()) {
            ((org.thunderdog.challegram.ui.MainController) currentLeft).tgx101FollowSlide(listShift, currentWidth - px, 1f - factor, true);
          } else {
            ((org.thunderdog.challegram.ui.MainController) currentLeft).tgx101FollowSlide(listShift, px, 1f - factor);
          }
        }
        factor = 1f - factor;

        float px2 = factor * currentPrevWidth;

        if (needRtl()) {
          rightWrap.setTranslationX(-px);
          currentRight.onTranslationChanged(-px);

          if (USE_PREVIEW_TRANSLATION) {
            currentLeft.getValue().setTranslationX(-px2);
            currentLeft.onTranslationChanged(-px2);
          }
        } else {
          rightWrap.setTranslationX(px);
          currentRight.onTranslationChanged(px);

          if (USE_PREVIEW_TRANSLATION) {
            currentLeft.getValue().setTranslationX(px2);
            currentLeft.onTranslationChanged(px2);
          }
        }

        if (currentLeft.usePopupMode()) {
          headerView.getFilling().restorePixels(-px2);
        }

        if (USE_PREVIEW_FADE) {
          if (!currentLeft.usePopupMode()) {
            fadeView.setAlpha(factor * PREVIEW_FADE_CHANGE_FACTOR);
          }
        }

        if (DROP_SHADOW_ENABLED) {
          //noinspection ResourceType
          shadowView.setTranslationX(needRtl() ? -px + currentWidth : px - Size.getNavigationShadowSize());
          shadowView.setAlpha(.65f + .45f * factor);
        }


        break;
      }
      case TRANSLATION_VERTICAL: {
        rightWrap.setTranslationY(px);

        if (USE_PREVIEW_FADE) {
          if (!currentLeft.usePopupMode()) {
            fadeView.setAlpha((1f - factor) * PREVIEW_FADE_CHANGE_FACTOR);
          }
        }

        if (DROP_SHADOW_ENABLED) {
          shadowTop.setTranslationY(px - Size.getNavigationShadowvertSize());
          shadowTop.setAlpha(1f);
        }

        headerView.getFilling().collapseFilling(px);
        if (px < fromHeaderHeight + HeaderView.getTopOffset()) {
          if (currentRight.getPopupRestoreColor() == 0xff000000) { // FIXME: 01/09/15 find out why really this line appears
            leftWrap.setAlpha(0f);
          }
          headerView.setTranslation(lastHeaderFactor = px / (fromHeaderHeight + HeaderView.getTopOffset()));
        } else if (lastHeaderFactor != 1f) {
          leftWrap.setAlpha(1f);
          lastHeaderFactor = 1f;
          headerView.setTranslation(1f);
        }

        // currentRight.applyCustomAnimations(1f - factor);

        break;
      }
    }

    updateHackyViews();
  }

  private void updateHackyViews () {
    ((BaseActivity) context).updateHackyOverlaysPositions();
  }

  public int getTotalTranslate () {
    return rootView.getMeasuredWidth();
  }

  public int getHorizontalTranslate () {
    if (isAnimating && translationMode == TRANSLATION_HORIZONTAL) {
      return (int) ((float) rootView.getMeasuredWidth() * translationFactor);
    }
    return 0;
  }

  public float getFadeAlpha () {
    return isAnimating && translationMode == TRANSLATION_FADE ? 1f - lastTranslation : 1f;
  }

  public float getFactor () {
    return translationFactor;
  }

  final void translatePreview (float px) {
    if (isAnimating) {
      if (needRtl() && translationMode == TRANSLATION_HORIZONTAL) {
        setFactor(-px / currentWidth);
      } else {
        setFactor(px / currentWidth);
      }
    }
  }

  // (user video 2026-10-29 12:32 «свайпы вверх-вниз по краям не работают» — channels, groups, «Газета»): Android 14 starts
  // its back gesture on any touch at the screen's edge, a scroll up or down too, and cancels it a moment later; the preview
  // opened at once, took the screen and the list under the finger lost its scroll. The preview now waits until the finger
  // has really gone sideways; a gesture cancelled before that leaves the screen and the scroll alone
  private boolean tgx101BackPending, tgx101BackHidesKeyboard;
  private float tgx101BackPendingY;
  private int tgx101BackPendingGesture;

  @Override
  public boolean onSystemBackStarted (@NonNull BackEventCompat backEvent) {
    tgx101BackStartX = Float.NaN;
    tgx101BackPending = false;
    org.thunderdog.challegram.Tgx101Diag.mark("system back: started, swipe nav " + swipeNavigationEnabled() + ", animating " + isAnimating + ", top " + (getCurrentStackItem() != null ? getCurrentStackItem().getClass().getSimpleName() : "?"));
    tgx101BackHidesKeyboard = false;
    if (context instanceof BaseActivity && ((BaseActivity) context).isKeyboardVisible()) {
      // (user 2026-10-29 «свайп назад системы не убирает клавиатуру» — it left the feed instead): with the keyboard up
      // the system's back only takes the keyboard away, as everywhere
      tgx101BackHidesKeyboard = true;
      org.thunderdog.challegram.Tgx101Diag.mark("system back: the keyboard is up — it only hides it");
      return true;
    }
    if (swipeNavigationEnabled()) {
      tgx101BackPending = true;
      tgx101BackPendingY = backEvent.getTouchY();
      tgx101BackPendingGesture = backEvent.getSwipeEdge() == BackEventCompat.EDGE_RIGHT ? PredictiveGesture.BACK_FROM_RIGHT_EDGE : PredictiveGesture.BACK_FROM_LEFT_EDGE;
      tgx101BackPreview(backEvent); // notes where the finger started
      return true;
    }
    return false;
  }

  private boolean tgx101OpenPendingBack (@NonNull BackEventCompat backEvent) {
    tgx101BackPending = false;
    if (openPreviewImpl(tgx101BackPendingY, false, tgx101BackPendingGesture)) {
      setFactor(tgx101BackPreview(backEvent));
      org.thunderdog.challegram.Tgx101Diag.mark("system back: preview opened, mode " + translationMode + ", left " + (currentLeft != null ? currentLeft.getClass().getSimpleName() : "?"));
      return true;
    }
    org.thunderdog.challegram.Tgx101Diag.mark("system back: preview NOT opened");
    return false;
  }

  @Override
  public void onSystemBackProgressed (@NonNull BackEventCompat backEvent) {
    if (tgx101BackPending && !isAnimating) {
      float x = backEvent.getTouchX();
      boolean sideways = !Float.isNaN(x) && !Float.isNaN(tgx101BackStartX) && Math.abs(x - tgx101BackStartX) >= Screen.dp(14f);
      if (sideways || (Float.isNaN(x) && backEvent.getProgress() > .05f)) {
        tgx101OpenPendingBack(backEvent);
      }
      return;
    }
    if (isAnimating) {
      setFactor(tgx101BackPreview(backEvent));
    }
  }

  /** TGx101: the system gesture's progress jumps to 1 within ~80 ms on Vivo — the screen was already gone when the finger
   *  lifted and the back looked like no animation. The gesture shows up to a third of the slide, the release animates the rest */
  // user 2026-10-08 «системный жест должен работать так же, как свайп назад»: the screen follows the finger itself (its x),
  // like the in-app swipe — Vivo's progress value jumped to 1 within ~80 ms
  // user 2026-10-08 20:03 «опять поломалась анимация системного жеста»: on 0.1.610 every gesture was at 98–100 % within
  // 40 ms — the edge Vivo reports doesn't match the finger's x, so «width − x» gave a full slide at once. Now the distance
  // from where the finger started, whatever edge is reported
  private float tgx101BackStartX = Float.NaN;

  private float tgx101BackPreview (@NonNull BackEventCompat e) {
    float x = e.getTouchX();
    if (Float.isNaN(x) || currentWidth <= 0) return Float.isNaN(e.getProgress()) ? 0f : MathUtils.clamp(e.getProgress()) / 3f;
    if (Float.isNaN(tgx101BackStartX)) {
      tgx101BackStartX = x;
      org.thunderdog.challegram.Tgx101Diag.mark("system back: finger at " + Math.round(x) + " of " + currentWidth + ", edge " + (e.getSwipeEdge() == BackEventCompat.EDGE_RIGHT ? "right" : "left") + ", progress " + e.getProgress());
    }
    // never more than two thirds while the finger is down: if the reported x jumps too, the release still slides the rest
    return Math.min(.66f, MathUtils.clamp(Math.abs(x - tgx101BackStartX) / currentWidth));
  }

  @Override
  public void onSystemBackCancelled () {
    tgx101BackHidesKeyboard = false;
    boolean wasPending = tgx101BackPending;
    tgx101BackPending = false;
    org.thunderdog.challegram.Tgx101Diag.mark("system back: cancelled at " + translationFactor + (wasPending ? " (no preview — the finger didn't go sideways)" : ""));
    if (isAnimating) {
      closePreview(0f);
    }
  }

  @Override
  public boolean onSystemBackPressed () {
    org.thunderdog.challegram.Tgx101DiagHooks.onInputAction(); // the response to the system back is timed from here
    org.thunderdog.challegram.Tgx101Diag.mark("system back: pressed at " + translationFactor + ", animating " + isAnimating + (tgx101BackPending ? ", no preview yet" : ""));
    boolean wasPending = tgx101BackPending;
    tgx101BackPending = false;
    if (tgx101BackHidesKeyboard) {
      tgx101BackHidesKeyboard = false;
      ViewController<?> current = getCurrentStackItem();
      if (current != null) current.hideSoftwareKeyboard();
      return true;
    }
    // (black box 2026-10-29 20:38 «свайп назад с лагами»): a quick flick ended before the finger went far enough sideways
    // for the preview — the screen was swapped in one frame. It now opens the preview at that moment and slides it off
    if (wasPending && !isAnimating && openPreviewImpl(tgx101BackPendingY, false, tgx101BackPendingGesture)) {
      org.thunderdog.challegram.Tgx101Diag.mark("system back: quick flick — the preview opened on release and slides off");
    }
    if (isAnimating) {
      applyPreview(0f);
      return true;
    }
    return false;
  }

  public final void configurationChanged (Configuration newConfig) {
    for (ViewController<?> controller : getStack().getAll()) {
      controller.onConfigurationChanged(newConfig);
    }
  }

  private Object actionMode;

  public void setActionMode (Object mode) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
      actionMode = mode;
    }
  }

  /*// Instance state

  private static final String KEY_STACK_SIZE = "_n_sz_";
  private static final String KEY_STACK_ITEM_ID = "_n_sid_";

  public void saveInstanceState (Bundle out) {
    NavigationStack stack = getStack();
    int stackSize = stack.size();

    out.putInt(KEY_STACK_SIZE, stackSize);

    if (stackSize > 1) {
      for (int i = 0; i < stackSize; i++) {
        ViewController c;
        c = stack.get(i);
        if (c == null) {
          throw new RuntimeException("Error saving stack: stack.get is null");
        }
        out.putInt(KEY_STACK_ITEM_ID + i, c.getId());
        c.onSaveInstanceState(i, out);
      }
    }
  }

  public int restoreStackSize (Bundle in) {
    return in.getInt(KEY_STACK_SIZE, 0);
  }

  public int restoreStackItemId (Bundle in, int id) {
    return in.getInt(KEY_STACK_ITEM_ID + id, 0);
  }

  public boolean restoreStackItem (int id, ViewController c, Bundle in) {
    return c.onRestoreInstanceState(id, in);
  }*/

  // Utils

  public static int calculateDropDuration (float length, float velocity, int max, int min) {
    return velocity <= 0 ? max : Math.min(Math.max(Math.round(length / (velocity / 1000f)), min), max);
  }

  // Language handling

  /*@Override
  public void onRtlChange (boolean isRtl) {
    for (ViewController controller : getStack().getAll()) {
      controller.onRtlChange(isRtl);
    }
    if (shadowView != null) {
      if (isRtl) {
        shadowView.setSimpleRightShadow(false);
      } else {
        shadowView.setSimpleLeftShadow(false);
      }
    }
    DrawerController drawer = UI.getDrawer(context);
    if (drawer != null) {
      drawer.onRtlChange(isRtl);
    }
  }*/

  @Override
  public void onLanguagePackEvent (int event, int arg1) {
    boolean directionChanged = Lang.hasDirectionChanged(event, arg1);

    if (directionChanged) {
      if (Views.setGravity(floatingButton, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)))
        Views.updateLayoutParams(floatingButton);
      checkShadowDirection();
    }

    if (headerView != null) {
      headerView.onLanguagePackEvent(event, arg1);
    }
    // TODO more
  }

  private int bottomInset, bottomInsetWithoutIme;

  public void applyBottomInset (ViewController<?> controller) {
    if (controller.supportsBottomInset()) {
      controller.setBottomInset(bottomInset, bottomInsetWithoutIme);
    } else {
      View view = controller.getValue();
      Views.setPaddingBottom(view, bottomInset);
    }
  }

  private void setBottomInset (int bottomInset, int bottomInsetWithoutIme) {
    if (this.bottomInset != bottomInset || this.bottomInsetWithoutIme != bottomInsetWithoutIme) {
      this.bottomInset = bottomInset;
      this.bottomInsetWithoutIme = bottomInsetWithoutIme;
      if (childWrappers != null) {
        for (ViewController<?> controller : childWrappers) {
          applyBottomInset(controller);
        }
      }
    }
  }

  public void dispatchSystemInsets (View child, ViewGroup.MarginLayoutParams layoutParams, Rect legacyInsets, Rect systemInsets, Rect systemInsetWithoutIme) {
    Views.setMargins(layoutParams, legacyInsets.left, legacyInsets.top, legacyInsets.right, 0);
    setBottomInset(systemInsets.bottom, systemInsetWithoutIme.bottom);
  }
}
