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
 * File created on 19/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.widget.EmptyTextView;

import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public abstract class MediaBottomBaseController<T> extends ViewController<T> {
  protected final MediaLayout mediaLayout;
  private final int titleRes;
  private String titleString;

  protected MediaBottomBaseController (MediaLayout context, int titleResource) {
    super(context.getContext(), context.tdlib());
    this.titleRes = titleResource;
    this.titleString = "";
    this.mediaLayout = context;
    initMetrics();
  }

  protected MediaBottomBaseController (MediaLayout context, String titleString) {
    super(context.getContext(), context.tdlib());
    this.titleRes = 0;
    this.titleString = titleString;
    this.mediaLayout = context;
    initMetrics();
  }

  public @TdApi.ChatAction.Constructors int getBroadcastingAction () {
    return TdApi.ChatActionCancel.CONSTRUCTOR;
  }

  @Override
  public CharSequence getName () {
    return titleRes != 0 ? Lang.getString(titleRes) : titleString;
  }

  public void setName (String name) {
    titleString = name;
    mediaLayout.getHeaderView().setTitle(this);
  }

  // Settings

  public boolean allowSpoiler () {
    return false;
  }

  @Override
  protected final int getHeaderTextColorId () {
    return ColorId.text;
  }

  @Override
  protected final int getHeaderColorId () {
    return ColorId.filling;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected final int getHeaderIconColorId () {
    return ColorId.headerLightIcon;
  }

  @Override
  protected boolean useGraySearchHeader () {
    return true;
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (inSearchMode()) {
      if (commit) {
        mediaLayout.getHeaderView().closeSearchMode(true, null);
      }
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  public boolean canMoveRecycler () {
    return !inTransformMode(); // aka isAnimating
  }

  public boolean supportsMediaGrouping () {
    return false;
  }

  public boolean ignoreStartHeightLimits() {
    return false;
  }

  // Metrics

  private int contentHeight, startHeight, currentHeight;

  private int lastHeightIncreaseCheck;
  private boolean isHeightIncreasing; // true if it increased

  protected final void initMetrics () { // FIXME make private again and move calling from constructor to some proper place
    contentHeight = getInitialContentHeight();
    resetStartHeights(true);
  }

  private int getBarHeightIfAvailable () {
    return mediaLayout.inSpecificMode() && mediaLayout.getMode() != MediaLayout.MODE_CUSTOM_ADAPTER ? 0 : MediaBottomBar.getBarHeight() + (Settings.instance().useEdgeToEdge() ? context.getRootView().getSystemInsetsWithoutIme().bottom : 0);
  }

  private void resetStartHeights (boolean initial) {
    startHeight = Math.min(contentHeight + getBarHeightIfAvailable() + HeaderView.getSize(false), Math.min(getMaxStartHeight(), getMaxHeight()));
    setCurrentHeight(getRecyclerScrollY() > 0 || lastIsExpanded ? getMaxHeight() : startHeight, !initial);
  }

  public final int getStartHeight () {
    return startHeight;
  }

  public final int getCurrentWidth () {
    return mediaLayout.getCurrentContentWidth();
  }

  public final int getCurrentHeight () {
    return currentHeight;
  }

  public final int getContentHeight () {
    return contentHeight;
  }

  // Metrics (internal)

  protected int getInitialContentHeight () {
    return getMaxInitialContentHeight();
  }

  protected final int getMaxInitialContentHeight () {
    return Screen.smallestSide() - HeaderView.getSize(false);
  }

  private int getMaxStartHeight () {
    if (ignoreStartHeightLimits()) return Integer.MAX_VALUE;
    return Math.min(getContentHeight() + getBarHeightIfAvailable() + HeaderView.getSize(false), Math.min(getCurrentWidth() + getBarHeightIfAvailable(), getMaxStartHeightLimit()));
  }

  protected int getMaxStartHeightLimit () {
    if (ignoreStartHeightLimits()) return Integer.MAX_VALUE;
    return getMaxHeight() - MediaBottomBar.getBarHeight() / 4;
  }

  protected int getRecyclerHeaderOffset () {
    return 0;
  }

  protected boolean canExpandHeight () {
    return true;
  }

  protected boolean canMinimizeHeight () {
    return true;
  }

  public boolean showExitWarning (boolean isExitingSelection, boolean commit) {
    // override
    return false;
  }

  private int findYForStaticView (int viewHeight) {
    return (int) recyclerView.getTranslationY() + getRecyclerHeaderOffset() + (Math.max(startHeight, getCurrentHeight()) - getRecyclerHeaderOffset()) / 2 - getBarHeightIfAvailable() - viewHeight / 2;
  }

  // Recycler position

  public void onViewportChanged (int width, int height) {
    if (recyclerView != null) {
      resetStartHeights(false);
      updateRecyclerTop(height);
    }
  }

  private void updateRecyclerTop () {
    if (contentView != null) {
      int currentHeight = contentView.getMeasuredHeight();
      updateRecyclerTop(currentHeight == 0 ? getTargetHeight() : currentHeight);
    }
  }

  private int getTargetHeight () {
    return context.getRootView().getMeasuredHeight() - HeaderView.getTopOffset();
  }

  private void updateRecyclerTop (int height) {
    if (recyclerView != null) {
      float top = height - currentHeight;
      recyclerView.setTranslationY(top);
      onRecyclerTopUpdate(top);
    }
  }

  // General pattern

  protected MediaContentView contentView;
  private View progressView;
  protected MediaBottomBaseRecyclerView recyclerView;
  private EmptyTextView emptyView;

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
    Views.applyBottomInset(recyclerView, extraBottomInsetWithoutIme);
  }

  protected final MediaContentView buildContentView (boolean needProgress) {
    contentView = new MediaContentView(context());
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.setBoundController(this);

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.topMargin = HeaderView.getSize(false);
    params.bottomMargin = HeaderView.getTopOffset();

    recyclerView = new MediaBottomBaseRecyclerView(context());
    Views.applyBottomInset(recyclerView, extraBottomInsetWithoutIme);
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position != RecyclerView.NO_POSITION && recyclerView.getAdapter() != null && position == recyclerView.getAdapter().getItemCount() - 1 && mediaLayout.getCounterFactor() == 1f) {
          outRect.set(0, 0, 0, MediaBottomBar.getBarHeight());
        } else {
          outRect.setEmpty();
        }
      }
    });
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    ViewSupport.setThemedBackground(recyclerView, getRecyclerBackgroundColorId());
    addThemeInvalidateListener(recyclerView);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 150l));
    recyclerView.setLayoutParams(params);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
        if (newState != RecyclerView.SCROLL_STATE_IDLE) {
          hideSoftwareKeyboard();
        }
      }

      // TGx101 (user 2026-11-18 «проверь это в другом клиенте и перенеси жесты к нам», recorded there): a flick down on
      // the scrolled grid of the open sheet — the grid comes back to its top and the rest of the flick takes the sheet
      // down to half, in one flick (it stayed open)
      @Override
      public void onScrolled (RecyclerView view, int dx, int dy) {
        if (dy < 0 && view.getScrollState() == RecyclerView.SCROLL_STATE_SETTLING && !isMoving && !animatingHeight
          && isExpanded() && canMinimizeHeight() && !view.canScrollVertically(-1)) {
          mediaLayout.prepareHeader();
          animateCurrentHeight(startHeight, false);
        }
      }
    });
    contentView.addView(recyclerView);

    if (needProgress) {
      params = FrameLayoutFix.newParams(Screen.dp(48f), Screen.dp(48f), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      params.topMargin = HeaderView.getSize(false);
      progressView = Views.simpleProgressView(context(), params);
      progressView.setTranslationY(findYForStaticView(Screen.dp(48f)));
      contentView.addView(progressView);
    }

    updateRecyclerTop();

    return contentView;
  }

  public void onRecyclerFirstMovement () {

  }

  protected @ColorId int getRecyclerBackgroundColorId () {
    return ColorId.filling;
  }

  public void dispatchRecyclerTouchEvent (MotionEvent e) {
    recyclerView.processEvent(e);
  }

  public int getRecyclerScrollY () {
    if (recyclerView == null) {
      return 0;
    }
    RecyclerView.LayoutManager manager = getLayoutManager();
    if (!(manager instanceof LinearLayoutManager)) {
      return 0;
    }
    RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
    if (!(adapter instanceof MeasuredAdapterDelegate)) {
      return 0;
    }
    int firstPosition = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
    if (firstPosition == RecyclerView.NO_POSITION) {
      return 0;
    }
    int scrollTop = ((MeasuredAdapterDelegate) adapter).measureScrollTop(firstPosition);
    View view = manager.findViewByPosition(firstPosition);
    if (view != null) {
      scrollTop -= view.getTop();
    }
    return scrollTop;
  }

  protected final void setLayoutManager (RecyclerView.LayoutManager manager) {
    recyclerView.setLayoutManager(manager);
  }

  protected final RecyclerView.LayoutManager getLayoutManager () {
    return recyclerView.getLayoutManager();
  }

  protected final void addItemDecoration (RecyclerView.ItemDecoration decoration) {
    recyclerView.addItemDecoration(decoration);
  }

  protected final void setAdapter (RecyclerView.Adapter<?> adapter) {
    recyclerView.setAdapter(adapter);
  }

  protected final void dispatchError (final String error, final String resolveErrorButtonText, final View.OnClickListener onResolveButtonClick, final boolean animated) {
    runOnUiThread(() -> showError(error, resolveErrorButtonText, onResolveButtonClick, animated));
  }

  public void updateExtraSpacingDecoration () {
    recyclerView.invalidateItemDecorations();
  }

  protected final void showError (@StringRes int errorRes, @StringRes int resolveErrorButtonRes, View.OnClickListener onResolveButtonClick, boolean animated) {
    showError(Lang.getString(errorRes), resolveErrorButtonRes != 0 ? Lang.getString(resolveErrorButtonRes) : null, onResolveButtonClick, animated);
  }

  protected void hideError () {
    if (emptyView != null) {
      emptyView.setAlpha(0f);
    }
  }

  // Called when removed from view tree
  public final void resetState () {
    // force scroll to top
  }

  protected void showError (String error, @Nullable String resolveButtonText, @Nullable View.OnClickListener onResolveButtonClick, boolean animated) {
    if (emptyView == null) {
      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      params.topMargin = HeaderView.getSize(false);

      emptyView = new EmptyTextView(context());
      emptyView.setLayoutParams(params);
      emptyView.setTranslationY(findYForStaticView(Screen.dp(18f)));
      contentView.addView(emptyView);
    } else {
      animated = false;
    }
    emptyView.setText(error);
    if (!animated || progressView == null) {
      emptyView.setAlpha(1f);
      return;
    }
    emptyView.setAlpha(0f);
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> {
      float factor = AnimatorUtils.getFraction(animation);
      if (factor <= .5f) {
        factor = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(factor / .5f);
        progressView.setAlpha(1f - factor);
      } else {
        if (progressView.getAlpha() != 0f) {
          progressView.setAlpha(0f);
        }
        factor = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((factor - .5f) / .5f);
        emptyView.setAlpha(factor);
      }
    });
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        contentView.removeView(progressView);
        progressView = null;
      }
    });
    animator.setDuration(300l);
    animator.start();
  }

  protected static final long HIDE_PROGRESS_DURATION = 140l;

  protected void hideProgress () {
    hideProgress(null);
  }

  protected void hideProgress (final Runnable after) {
    if (progressView == null) {
      return;
    }
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> progressView.setAlpha(1f - AnimatorUtils.getFraction(animation)));
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        contentView.removeView(progressView);
        progressView = null;
        if (after != null) {
          after.run();
        }
      }
    });
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.setDuration(HIDE_PROGRESS_DURATION);
    animator.start();
  }

  private void applyStartHeight (int newStartHeight) {
    if (this.startHeight != newStartHeight) {
      this.startHeight = this.currentHeight = newStartHeight;
      updateRecyclerTop();
      mediaLayout.onContentHeightChanged();
    }
  }

  protected void expandStartHeight (MeasuredAdapterDelegate adapter) {
    this.contentHeight = adapter.measureHeight(-1);
    int newStartHeight = getMaxStartHeight();

    if (newStartHeight <= startHeight) {
      return;
    }

    if (isMoving || animatingHeight || currentHeight > startHeight) {
      startHeight = newStartHeight;
      return;
    }

    final float fromHeight = startHeight;
    final float heightDiff = newStartHeight - startHeight;

    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> applyStartHeight(Math.round(fromHeight + heightDiff * AnimatorUtils.getFraction(animation))));
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.setDuration(150l);
    animator.start();
  }

  private boolean lastIsExpanded;

  private void setCurrentHeight (int height, boolean updateMediaLayout) {
    if (this.currentHeight != height) {
      this.currentHeight = height;

      final int maxHeight = getMaxHeight();

      lastIsExpanded = height == maxHeight;

      if (Math.abs(height - lastHeightIncreaseCheck) >= Screen.getTouchSlop()) {
        isHeightIncreasing = height > lastHeightIncreaseCheck;
        lastHeightIncreaseCheck = height;
      }

      updateRecyclerTop();
      mediaLayout.setContentVisible(currentHeight < maxHeight);

      if (updateMediaLayout) {
        if (currentHeight == startHeight) {
          mediaLayout.setBottomBarFactor(1f);
          mediaLayout.setHeaderFactor(0f);
        } else if (currentHeight < startHeight) {
          mediaLayout.setBottomBarFactor((float) currentHeight / (float) startHeight);
          mediaLayout.setHeaderFactor(0f);
        } else {
          float barFactor = (float) (getMaxHeight() - currentHeight) / (float) (maxHeight - startHeight);
          mediaLayout.setBottomBarFactor(barFactor);
          // TGx101 (recorded next to the other client): the header comes in only as the sheet reaches the top — over the
          // last 30 % of the way; it showed over the chat from the first frames of the rise
          mediaLayout.setHeaderFactor(Math.max(0f, (1f - barFactor - .7f) / .3f));
        }
      }
    }
  }

  protected void onUpdateBottomBarFactor (float bottomBarFactor, float counterFactor, float y) {
  }

  private boolean animatingHeight;
  private ValueAnimator heightAnimator;

  protected void animateCurrentHeight (int toHeight, boolean fast) {
    animateCurrentHeight(toHeight, fast, 0f);
  }

  /**
   * TGx101 (user 2026-11-18, recorded next to the other client: «галерея должна следовать за пальцем так же плавно»): let
   * go with a speed, the sheet goes on at that same speed and slows down softly — a quadratic ease-out whose first speed is
   * the finger's (duration 2 × distance / speed). It jumped: a strong decelerate put 60 % of the way into the first 50 ms
   */
  protected void animateCurrentHeight (int toHeight, boolean fast, float velocity) {
    animateCurrentHeight(toHeight, fast, velocity, null);
  }

  protected void animateCurrentHeight (int toHeight, boolean fast, float velocity, @androidx.annotation.Nullable Runnable after) {
    long duration = fast ? 180l : 300l;
    android.view.animation.Interpolator curve = new android.view.animation.DecelerateInterpolator(2.2f);
    int distance = Math.abs(toHeight - currentHeight);
    if (velocity != 0f && distance > 0) {
      // (recorded next to the other client, sharp flick): there the sheet and the grid are one scroll — the sheet goes up
      // at about the finger's speed in near-even steps (~9 % of the screen a frame, ~170 ms for a hard flick) and at the
      // top the move goes on in the grid. Hardly any slowing down on the way, the grid takes the rest (see handleFling)
      duration = Math.max(160l, Math.min(380l, Math.round(1600.0 * distance / Math.abs(velocity))));
      curve = new android.view.animation.DecelerateInterpolator(.6f);
    } else if (!fast) {
      duration = 340l; // let go slowly: no sudden start either
      curve = new android.view.animation.DecelerateInterpolator(1.2f);
    }
    org.thunderdog.challegram.Tgx101Diag.mark("attach sheet: " + currentHeight + " → " + toHeight + " px, finger " + Math.round(velocity) + " px/s, " + duration + " ms" + (animatingHeight ? " (one already running)" : ""));
    animateCurrentHeightImpl(toHeight, duration, curve, after);
  }

  private void animateCurrentHeightImpl (int toHeight, long duration, android.view.animation.Interpolator curve, @androidx.annotation.Nullable Runnable after) {
    if (animatingHeight) {
      animatingHeight = false;
      if (heightAnimator != null) {
        heightAnimator.cancel();
        heightAnimator = null;
      }
    }

    if (currentHeight == toHeight) {
      return;
    }

    animatingHeight = true;

    final float fromHeight = this.currentHeight;
    final float heightDiff = toHeight - fromHeight;

    heightAnimator = AnimatorUtils.simpleValueAnimator();
    heightAnimator.addUpdateListener(animation -> {
      if (animatingHeight) {
        float factor = AnimatorUtils.getFraction(animation);
        setCurrentHeight(Math.round(fromHeight + heightDiff * factor), true);
      }
    });
    heightAnimator.addListener(new AnimatorListenerAdapter() {
      private boolean cancelled;

      @Override
      public void onAnimationCancel (Animator animation) {
        cancelled = true;
      }

      @Override
      public void onAnimationEnd (Animator animation) {
        animatingHeight = false;
        if (after != null && !cancelled) after.run();
      }
    });
    heightAnimator.setInterpolator(curve);
    heightAnimator.setDuration(duration);
    heightAnimator.start();
  }

  // Height expanding

  public boolean isAnimating () {
    return animatingHeight || useBottomBarChange;
  }

  public boolean isInsideRecyclerView (float x, float y) {
    return y >= recyclerView.getTranslationY() && y <= recyclerView.getTranslationY() + recyclerView.getMeasuredHeight();
  }

  public final int getMaxHeight () {
    return getTargetHeight(); //  - HeaderView.getSize();
  }

  private int moveStartHeight;
  private boolean isMoving;

  private boolean useBottomBarChange;
  private int barChangeFromHeight;
  private int barChangeHeightDiff;

  public void onBottomBarAnimationStart (boolean beforeExit) {
    if (beforeExit) {
      useBottomBarChange = true;
      barChangeFromHeight = currentHeight;
      barChangeHeightDiff = -currentHeight;
    }
  }

  public void onBottomBarAnimationFraction (float factor) {
    if (useBottomBarChange) {
      setCurrentHeight(barChangeFromHeight + (int) ((float) barChangeHeightDiff * factor), false);
    }
  }

  public void onBottomBarAnimationComplete () {
    useBottomBarChange = false;
  }

  public void onRecyclerMovementStarted () {
    onRecyclerMovementStarted(false);
  }

  // TGx101 (recorded in the other client, 2026-11-18): the sheet taken down in the same move that brought the grid back
  // to its top stops at half — closing it is a swipe of its own
  private boolean stopAtStart;

  public void onRecyclerMovementStarted (boolean stopAtStart) {
    org.thunderdog.challegram.Tgx101Diag.mark("attach sheet: the finger takes it at " + currentHeight + " px" + (stopAtStart ? " (from the grid, stops at half)" : ""));
    moveStartHeight = currentHeight;
    isMoving = true;
    this.stopAtStart = stopAtStart;
  }

  public boolean handleFling (boolean toUp) {
    return handleFling(toUp, 0f);
  }

  /** TGx101 (user 2026-10-12 «второй режим лишний»): a flick up opens the sheet in one gesture; the grid itself stays put */
  public boolean handleFling (boolean toUp, float velocityY) {
    if (isMoving) {
      isMoving = false;
      useBottomBarChange = false;

      float expandFactor = currentHeight < startHeight ? 0f : 1f - (float) (getMaxHeight() - currentHeight) / (float) (getMaxHeight() - startHeight);

      mediaLayout.prepareHeader();
      if (stopAtStart && !toUp) {
        stopAtStart = false;
        animateCurrentHeight(startHeight, false, velocityY); // a flick down in the move that came from the grid: to half, not away
      } else if (toUp) {
        stopAtStart = false;
        if (canExpandHeight()) {
          // (user 2026-10-12 21:56, exteraGram video «убери этот сдвиг вовсе»): the grid stayed where it was.
          // (user 2026-11-18, variant 2 «без остановки»): a flick from the half sheet opens it and the grid goes on
          // scrolling by the rest of the flick, as one move
          // the grid takes the rest of the flick once the sheet is at the top, as one move
          final int fling = velocityY < 0 ? Math.round(-velocityY * .7f) : 0;
          animateCurrentHeight(getMaxHeight(), false, velocityY, fling > 0 && recyclerView != null ? () -> recyclerView.fling(0, fling) : null);
        } else {
          animateCurrentHeight(startHeight, true);
        }
      } else if (expandFactor >= .2f || !canMinimizeHeight()) {
        animateCurrentHeight(startHeight, false);
      } else if (showExitWarning(false, true)) {
        // TGx101 (user 2026-10-25 «смахиваешь окно — подтверждения нет»): photos are chosen — the sheet comes back and asks
        animateCurrentHeight(startHeight, false);
      } else {
        useBottomBarChange = true;
        barChangeFromHeight = currentHeight;
        barChangeHeightDiff = -currentHeight;
        mediaLayout.hideBottomBarAndDismiss();
      }

      return true;
    }
    return false;
  }

  public void onRecyclerMovementFinished () {
    if (isMoving) {
      isMoving = false;
      useBottomBarChange = false;

      float openFactor = currentHeight >= startHeight ? 1f : (float) currentHeight / (float) startHeight;
      float expandFactor = currentHeight < startHeight ? 0f : 1f - (float) (getMaxHeight() - currentHeight) / (float) (getMaxHeight() - startHeight);

      mediaLayout.prepareHeader();

      if (stopAtStart) {
        stopAtStart = false;
        animateCurrentHeight(isHeightIncreasing && expandFactor >= .08f ? getMaxHeight() : startHeight, false);
      } else if (openFactor <= .45f && !isHeightIncreasing && showExitWarning(false, true)) {
        animateCurrentHeight(startHeight, false); // photos are chosen: the sheet comes back and asks first
      } else if (openFactor <= .45f && !isHeightIncreasing) {
        useBottomBarChange = true;
        barChangeFromHeight = currentHeight;
        barChangeHeightDiff = -currentHeight;
        mediaLayout.hideBottomBarAndDismiss();
      } else if (expandFactor >= .08f && isHeightIncreasing && canExpandHeight()) {
        // TGx101 (user 2026-10-12 «нужно два свайпа, чтобы её вытащить — нужен один обычный»): any real pull up opens it
        animateCurrentHeight(getMaxHeight(), false);
      } else {
        animateCurrentHeight(startHeight, false);
      }
    }
  }

  private boolean recyclerEverMoved;

  public boolean moveRecyclerView (float diffY) {
    int maxHeight = canExpandHeight() ? getMaxHeight() : startHeight;
    int newHeight = Math.min(maxHeight, moveStartHeight - (int) diffY);
    if (stopAtStart) newHeight = Math.max(newHeight, startHeight);

    if (newHeight < startHeight && !canMinimizeHeight()) {
      return false;
    }

    if (currentHeight == newHeight) {
      return newHeight == maxHeight;
    }

    if (!recyclerEverMoved && newHeight > startHeight) {
      recyclerEverMoved = true;
      onRecyclerFirstMovement();
    }

    if (currentHeight > startHeight) {
      mediaLayout.prepareHeader();
    }
    setCurrentHeight(newHeight, true);

    return newHeight == maxHeight;
  }

  // Override

  protected void preload (Runnable after, long timeout) {
    // do heavy load
    if (after != null) {
      after.run();
    }
  }

  @CallSuper
  protected void onRecyclerTopUpdate (float top) {
    if (progressView != null) {
      progressView.setTranslationY(findYForStaticView(progressView.getLayoutParams().height));
    }
    if (emptyView != null) {
      emptyView.setTranslationY(findYForStaticView(Screen.dp(18f)));
    }
    // Use to align overlay views
  }

  public void expandFully () {
    mediaLayout.prepareHeader();
    animateCurrentHeight(getMaxHeight(), false);
  }

  public boolean isExpanded () {
    return currentHeight == getMaxHeight();
  }

  public void collapseToStart () {
    int scrollY = getRecyclerScrollY();
    if (scrollY != 0) {
      recyclerView.smoothScrollBy(0, -scrollY);
    }
    animateCurrentHeight(startHeight, false);
  }

  protected void forceScrollRecyclerToTop () {
    RecyclerView.LayoutManager manager = getLayoutManager();
    if (manager != null && manager instanceof LinearLayoutManager) {
      ((LinearLayoutManager) manager).scrollToPositionWithOffset(0, 0);
    }
  }

  protected void onCompleteShow (boolean isPopup) {
    // Do all heavy work like layout or etc, no animation will lag
  }

  protected void onMultiSendPress (View view, @NonNull TdApi.MessageSendOptions options, boolean disableMarkdown) {
    // Send all selected shit
  }

  protected void addCustomItems (View view, @NonNull List<HapticMenuHelper.MenuItem> hapticItems) {
    // Add specific items
  }

  protected boolean canRemoveMarkdown () {
    return false;
  }

  protected void onCancelMultiSelection () {
    // unselect all selected shit
  }

  protected ViewGroup createCustomBottomBar () {
    return null;
  }

  @Override
  public void destroy () {
    super.destroy();
    if (recyclerView != null) {
      Views.destroyRecyclerView(recyclerView);
    }
  }
}