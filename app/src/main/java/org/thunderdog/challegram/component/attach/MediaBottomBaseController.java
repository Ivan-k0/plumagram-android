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

import android.os.SystemClock;
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
        long now = SystemClock.uptimeMillis();
        // the grid's speed as the sheet would carry it (shrinking), over at least a frame: two steps in the same
        // millisecond counted as 27 000 px/s and the sheet flew down in 220 ms (journal 2026-11-19 20:51)
        // (recordings 2026-11-22: the grid came back at ~25–32 px a frame and the sheet left at 60–64 — a jolt. The speed
        // was averaged over the last 100 ms, when the fling was still fast.) Now it is the grid's last full step: the
        // step that hits the top is cut short, so there the step before it counts
        if (dy < 0 && view.getScrollState() == RecyclerView.SCROLL_STATE_SETTLING) {
          int offset = view.computeVerticalScrollOffset();
          long dt = now - tgx101GridAt;
          if (tgx101GridAt != 0 && dt >= 5 && dt <= 40) {
            float v = Math.min(12f, Math.max(0, tgx101GridDy - offset) / (float) dt);
            float use = !view.canScrollVertically(-1) && tgx101GridStep > 0f ? tgx101GridStep : v;
            tgx101GridStep = v;
            // measured on the phone (recording 2026-11-22, 120 fps): the grid's own figures ran ~1.7× what is seen on the
            // screen (24–26 px a frame seen, 5556 px/s counted) while the sheet moves exactly as told — so ×0.6
            tgx101Speed = -use; // (the user's own flick 2026-11-23: ×0.45 left the sheet at 44 after the grid's 72–96 — «медленный тормоз»)
            tgx101SpeedAt = now;
          } else {
            tgx101GridStep = 0f;
          }
          tgx101GridAt = now;
          tgx101GridDy = offset;
        } else {
          tgx101GridAt = 0;
          tgx101GridStep = 0f;
        }
        if (dy < 0 && view.getScrollState() == RecyclerView.SCROLL_STATE_SETTLING && isExpanded() && canMinimizeHeight()
          && view.computeVerticalScrollOffset() < Screen.dp(160f)) {
          tgx101PrepareLeaveTop(); // the grid is about to hand over to the sheet: the chat is shown frames ahead
        }
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
    animateCurrentHeight(toHeight, fast, velocity, after, 0f);
  }

  /** endVelocity: the speed (px/s) the move goes on with once the sheet is there — the grid's fling — so it doesn't stop first */
  protected void animateCurrentHeight (int toHeight, boolean fast, float velocity, @androidx.annotation.Nullable Runnable after, float endVelocity) {
    int distance = Math.abs(toHeight - currentHeight);
    // TGx101 (user 2026-11-19, recording: «открытие ещё более-менее, а скрытие плохо. Анимация должна быть без рывков.
    // Полностью»): frame by frame the grid came back to its top at ~28 px a frame and the sheet then left at 70–100 — it
    // started at a speed of its own (the grid's was never passed, «finger 0 px/s»), and so did a sheet let go without a
    // flick. Now the sheet always starts at the speed the content had right before — the finger's, the grid's or the
    // sheet's own under the finger — and slows down evenly to its place; with no speed it starts from rest
    float speed = Math.abs(velocity) / 1000f; // px per ms
    if (speed == 0f && SystemClock.uptimeMillis() - tgx101SpeedAt < 80 && distance > 0) {
      float toward = tgx101Speed * Math.signum(toHeight - currentHeight);
      if (toward > 0f) speed = toward;
    }
    long duration;
    if (distance <= 0) {
      duration = fast ? 180l : 300l;
    } else if (speed > .05f) {
      // up: about the finger's speed in near-even steps (as recorded next to the other client); down: a softer landing
      // (recording 2026-11-22, six flicks up: 18 → 36 → 16 px a frame — the sheet sped up to twice the finger and slowed
      // again, a hump felt as a jolt: 380 ms was too short for the way at the finger's speed.) Going on into the grid,
      // the sheet keeps the finger's speed the whole way (start and end alike), as long as that takes, up to 1 s
      boolean even = toHeight > currentHeight && endVelocity > 0f;
      double t = (even ? 1.0 : toHeight > currentHeight ? 1.6 : 2.0) * distance / speed;
      // down up to 700 ms (recorded next to the reference 2026-11-22: capped at 420 the sheet had to speed up from the grid's
      // 28 px a frame to 40 to make it; there it only slows down)
      duration = even ? Math.max(160l, Math.min(1000l, Math.round(t))) : toHeight > currentHeight ? Math.max(160l, Math.min(380l, Math.round(t))) : Math.max(220l, Math.min(700l, Math.round(t)));
    } else {
      duration = fast ? 220l : 320l;
    }
    float slope = distance > 0 ? Math.min(3f, speed * duration / distance) : 0f;
    // (user 2026-11-19 «а свайпом вверх рывок есть?», same recording): at the top the sheet came to rest and the grid
    // then set off again — it now gets there at the speed the grid goes on with
    float endSlope = distance > 0 ? Math.min(3f, Math.abs(endVelocity) / 1000f * duration / distance) : 0f;
    if (toHeight < currentHeight && endSlope == 0f && slope > 0f) {
      // (user 2026-11-23 «в конце затуп, когда опускал галерею», recording: every way down ended in ~10 frames of crawling,
      // −8 −8 −4 −4 −4 px) as in the reference, the sheet lands still moving — about a third of its start speed — and stops
      endSlope = slope * .35f;
    }
    android.view.animation.Interpolator curve = new Tgx101GlideCurve(slope, endSlope);
    org.thunderdog.challegram.Tgx101Diag.mark("attach sheet: " + currentHeight + " → " + toHeight + " px, finger " + Math.round(velocity) + " px/s, start " + Math.round(speed * 1000f) + " px/s, " + duration + " ms" + (animatingHeight ? " (one already running)" : ""));
    if (after != null && endVelocity > 0f && distance > 0) {
      // (user 2026-11-23 «наверху ускорение»: a quick flick — the sheet, held to 160 ms at least and its animator run at the
      // phone's animation scale, came to the top at another speed than the one the grid then set off with) the grid goes
      // on at the speed the sheet really arrives with — its end slope over the duration as the phone plays it
      tgx101ArrivalSpeed = endSlope * distance / duration * 1000f; // the sheet plays at its own duration here (recorded)
    } else {
      tgx101ArrivalSpeed = 0f;
    }
    animateCurrentHeightImpl(toHeight, duration, curve, after);
  }

  /** Starts at the given speed (slope at 0, in distance per duration) and ends at the other one (0: comes to rest), no jolt on either end */
  private static final class Tgx101GlideCurve implements android.view.animation.Interpolator {
    private final float m0, m1;
    Tgx101GlideCurve (float m0, float m1) { this.m0 = m0; this.m1 = m1; }
    @Override
    public float getInterpolation (float s) {
      if (s <= 0f) return 0f;
      if (s >= 1f) return 1f;
      float r = 1f - s;
      return m0 * s * r * r + s * s * (3f - 2f * s) - m1 * s * s * r; // cubic Hermite: speeds m0 at the start, m1 at the end
    }
  }

  // TGx101 (user 2026-11-22, recording: «в первую секунду происходит „доезд“ галереи, а должно быть плавно с одинаковой
  // скоростью»): the sheet reached the top at ~40 px a frame and the grid's own fling took over at half that, then crawled
  // for a second (8 → 6 → 4 → 2 → 1 px). Now the grid goes on at exactly the sheet's speed and comes to rest in 0.3 s —
  // a quadratic ease-out starts at its full speed, so the hand-over shows no step. A touch on the grid stops it
  private ValueAnimator tgx101GridGlide;
  private float tgx101ArrivalSpeed; // px/s the sheet comes to the top with, as really played
  private boolean tgx101GridGlideHooked;

  private void tgx101GlideGrid (float velocity) {
    if (tgx101ArrivalSpeed > 0f) velocity = tgx101ArrivalSpeed;
    if (recyclerView == null || velocity <= 0f) return;
    if (tgx101GridGlide != null) tgx101GridGlide.cancel();
    if (!tgx101GridGlideHooked) {
      tgx101GridGlideHooked = true;
      recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
        @Override
        public boolean onInterceptTouchEvent (@androidx.annotation.NonNull RecyclerView rv, @androidx.annotation.NonNull MotionEvent e) {
          if (e.getActionMasked() == MotionEvent.ACTION_DOWN && tgx101GridGlide != null) {
            tgx101GridGlide.cancel();
            tgx101GridGlide = null;
          }
          return false;
        }
      });
    }
    // (user 2026-11-22 «почему скролл останавливается? он должен продолжаться»): it goes on as a fling does — the speed
    // fades exponentially (time constant tau) from exactly the sheet's speed; RecyclerView's own fling started at about
    // half the speed it was given on this phone, which was the step seen at the top
    final float tau = .7f; // s — set by recording next to the reference app (its grid halves every ~0.3 s, stops after ~1.6 s)
    final float v = velocity / 1000f; // px per ms
    final long duration = Math.max(300l, Math.round(tau * 1000f * Math.log(Math.max(1.01f, velocity / 12f))));
    final float k = duration / (tau * 1000f);
    final float norm = 1f - (float) Math.exp(-k);
    final float total = v * tau * 1000f * norm; // the speed at the start is total × k / norm / duration = v
    final int[] done = {0};
    ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
    a.setDuration(duration);
    a.setInterpolator(t -> (1f - (float) Math.exp(-k * t)) / norm);
    // (frame counter 2026-11-22: at the hand-over a whole vsync went by with no frame — the animator's first tick came out at
    // the very place already shown, nothing moved, nothing was drawn) the glide runs on the real clock from a frame before
    // its start, so every tick moves on
    final long t0 = android.view.animation.AnimationUtils.currentAnimationTimeMillis() - 8; // the frame's own time, not the moment the code runs
    final android.animation.TimeInterpolator curveOnClock = a.getInterpolator();
    a.addUpdateListener(an -> {
      if (recyclerView == null) return;
      float fraction = Math.min(1f, (android.view.animation.AnimationUtils.currentAnimationTimeMillis() - t0) / (float) duration);
      int target = Math.round(total * curveOnClock.getInterpolation(fraction));
      int dy = target - done[0];
      if (dy != 0) {
        if (!recyclerView.canScrollVertically(1)) {
          an.cancel(); // the end of the grid
          return;
        }
        recyclerView.scrollBy(0, dy);
        done[0] = target;
      }
    });
    tgx101GridGlide = a;
    a.start();
    org.thunderdog.challegram.Tgx101Diag.mark("attach grid: goes on at " + Math.round(velocity) + " px/s for " + Math.round(total) + " px in " + duration + " ms");
  }

  // the sheet's own speed a moment ago (px per ms, + = growing): under the finger, or the grid's as it reached its top
  private float tgx101Speed;
  private long tgx101SpeedAt;
  private int tgx101SpeedHeight = -1;

  private void tgx101TrackSpeed (int height) {
    long now = SystemClock.uptimeMillis();
    if (tgx101SpeedHeight >= 0 && now - tgx101SpeedAt < 8) return; // over a frame at least, not two events at once
    if (tgx101SpeedHeight >= 0 && now - tgx101SpeedAt < 100) {
      float v = Math.max(-12f, Math.min(12f, (height - tgx101SpeedHeight) / (float) (now - tgx101SpeedAt)));
      tgx101Speed = tgx101Speed == 0f ? v : tgx101Speed * .4f + v * .6f;
    } else {
      tgx101Speed = 0f;
    }
    tgx101SpeedHeight = height;
    tgx101SpeedAt = now;
  }

  private long tgx101GridAt;
  private int tgx101GridDy; // the grid's scroll offset at tgx101GridAt
  private float tgx101GridStep; // px per ms of the grid's previous step

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

  /** A finger came down while the sheet moves by itself (not while it closes): it stops there, the finger takes it */
  public void tgx101StopForFinger () {
    if (animatingHeight && !useBottomBarChange) {
      animatingHeight = false;
      if (heightAnimator != null) {
        heightAnimator.cancel(); // its «after» (the grid's glide) is not run on a cancel
        heightAnimator = null;
      }
      org.thunderdog.challegram.Tgx101Diag.mark("attach sheet: a finger stopped it at " + currentHeight + " px");
    }
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
    tgx101DismissSpeed = 0f;
  }

  private float tgx101DismissSpeed; // px per ms the sheet was flicked away with, 0 when not flicked

  /** How long the hide takes to go the rest of the way at the flick's speed, or 0 to use the usual hide */
  public long tgx101DismissDuration () {
    if (tgx101DismissSpeed < .5f || currentHeight <= 0) return 0;
    return Math.max(120l, Math.min(360l, Math.round(currentHeight / Math.min(8f, tgx101DismissSpeed))));
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
    tgx101SpeedHeight = -1;
    tgx101Speed = 0f;
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
        // a flick down in the move that came from the grid: to half, not away. (Recording 2026-11-22: the finger's full speed
        // took it from −20 to −38 px a frame at the let-go; half of it, as everywhere else, keeps the pace)
        animateCurrentHeight(startHeight, false, velocityY * .5f);
      } else if (toUp) {
        stopAtStart = false;
        if (canExpandHeight()) {
          // (user 2026-10-12 21:56, exteraGram video «убери этот сдвиг вовсе»): the grid stayed where it was.
          // (user 2026-11-18, variant 2 «без остановки»): a flick from the half sheet opens it and the grid goes on
          // scrolling by the rest of the flick, as one move
          // the grid takes the rest of the flick once the sheet is at the top, as one move
          // (recorded next to the reference app 2026-11-22, the same 80 ms flick): there the sheet rises evenly at about half
          // the finger's speed (~20 px a frame) and the grid goes on at that very speed — here it rose at the full speed,
          // 20–42 px a frame. Half the flick for both, start and end alike, so the sheet goes up evenly
          // (recording 2026-11-22, a slow flick: 6 → 14 px a frame — the way at the finger's speed took longer than allowed,
          // so the sheet sped up) the sheet goes evenly at the finger's speed, or faster when that would take over 0.7 s
          // (user's own flicks recorded next to the reference 2026-11-23: with half the flick the sheet sagged to 28–40 px a
          // frame after a 68 px finger, then the grid sped off at 64 — «ускорение вверху». The halves were fitted to adb
          // swipes and are wrong for a real finger) the sheet goes on at the flick's own speed
          float cruise = velocityY < 0 ? Math.max(-velocityY, Math.max(0, getMaxHeight() - currentHeight) / .7f) : 0f;
          final int fling = Math.round(cruise);
          animateCurrentHeight(getMaxHeight(), false, -cruise, fling > 0 && recyclerView != null ? () -> tgx101GlideGrid(fling) : null, fling);
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
        // (user 2026-11-22 «посмотри анимацию вниз, там тоже рывок»; recorded next to the reference app): flicked away,
        // the sheet went on at ~3× the finger's speed for its last two frames. The reference keeps the finger's speed to
        // the end, evenly — the hide now does the same
        float fingerSpeed = SystemClock.uptimeMillis() - tgx101SpeedAt < 120 ? Math.abs(tgx101Speed) : 0f;
        // ×0.55: set by recording (the full figure went 52–58 px a frame after the finger's 28; the reference keeps 28)
        tgx101DismissSpeed = Math.max(fingerSpeed, Math.abs(velocityY) / 1000f * .5f) * .55f;
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
    tgx101TrackSpeed(newHeight);
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

  /** The chat under the sheet is shown ahead of the sheet leaving the top: showing it is a heavy frame of its own */
  public void tgx101PrepareLeaveTop () {
    mediaLayout.setContentVisible(true);
  }

  public boolean tgx101SwipeSection (boolean previous) {
    return !isExpanded() && mediaLayout.tgx101SwipeSection(previous);
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