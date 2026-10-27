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
 * File created on 25/09/2015 at 14:40
 */
package org.thunderdog.challegram.v;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.CustomTouchHelper;
import org.thunderdog.challegram.component.chat.MessagesAdapter;
import org.thunderdog.challegram.component.chat.MessagesHolder;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.chat.MessagesTouchHelperCallback;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.MathUtils;

public class MessagesRecyclerView extends RecyclerView implements FactorAnimator.Target {
  // TGx101 diag (2026-10-07 «желе» in the share sheet): the floating-input padding is found at 0 before every change —
  // log who resets it
  @Override
  public void setPadding (int left, int top, int right, int bottom) {
    if (bottom != getPaddingBottom() && bottom == 0 && getPaddingBottom() != 0) {
      StackTraceElement[] st = new Throwable().getStackTrace();
      StringBuilder b = new StringBuilder("chat list padding reset " + getPaddingBottom() + " → 0 by");
      for (int i = 1; i < Math.min(st.length, 7); i++) b.append(' ').append(st[i].getClassName().replaceAll(".*\\.", "")).append('.').append(st[i].getMethodName()).append(':').append(st[i].getLineNumber());
      org.thunderdog.challegram.Tgx101Diag.mark(b.toString());
    }
    super.setPadding(left, top, right, bottom);
    tgx101LastBottom = bottom;
  }

  private int tgx101LastBottom;

  private void tgx101CheckSilentReset (String where) {
    if (tgx101LastBottom != 0 && getPaddingBottom() != tgx101LastBottom) {
      org.thunderdog.challegram.Tgx101Diag.mark("chat list padding changed without setPadding " + tgx101LastBottom + " → " + getPaddingBottom() + " (" + where + ", fitsSystemWindows " + getFitsSystemWindows() + ")");
      tgx101LastBottom = getPaddingBottom();
    }
  }

  public static final long ITEM_ANIMATOR_DURATION = Config.DEBUG_REACTIONS_ANIMATIONS ? 1400l : 140L;

  private MessagesManager manager;
  private CustomTouchHelper touchHelper;
  private MessagesTouchHelperCallback callback;

  public MessagesRecyclerView (Context context) {
    super(context);
    init();
  }

  public MessagesRecyclerView (Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public MessagesRecyclerView (Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init();
  }

  private CustomItemAnimator itemAnimator;

  private boolean animatorEnabled;

  public void setMessageAnimatorEnabled (boolean isEnabled) {
    if (this.animatorEnabled != isEnabled) {
      this.animatorEnabled = isEnabled;
      // setItemAnimator(isEnabled ? itemAnimator : null);
    }
  }

  private int prevWidth, prevHeight;

  @Override
  protected void onMeasure (int widthSpec, int heightSpec) {
    tgx101CheckSilentReset("measure");
    // TGx101 (user 2026-10-08 00:44 «вернулся в чат — он спустился вниз рывком, а должен остаться на том же месте»): a
    // rotation (media viewer in landscape) re-lays every message at another width and the place was lost. Keep the message
    // at the bottom edge where it was
    int newWidth = MeasureSpec.getSize(widthSpec);
    if (true /* all T changes in the release (user 2026-10-08) */ && prevWidth != 0 && newWidth != prevWidth && getLayoutManager() instanceof androidx.recyclerview.widget.LinearLayoutManager) {
      androidx.recyclerview.widget.LinearLayoutManager llm = (androidx.recyclerview.widget.LinearLayoutManager) getLayoutManager();
      int pos = llm.findFirstVisibleItemPosition();
      View anchor = pos != NO_POSITION ? llm.findViewByPosition(pos) : null;
      if (anchor != null && pos > 0) {
        int offset = getHeight() - getPaddingBottom() - anchor.getBottom();
        llm.scrollToPositionWithOffset(pos, offset);
        org.thunderdog.challegram.Tgx101Diag.mark("chat list: width " + prevWidth + " → " + newWidth + ", keeping message #" + pos + " at " + offset + "px");
      }
    }
    super.onMeasure(widthSpec, heightSpec);
    if (manager != null) {
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();
      if (prevWidth != width || prevHeight != height) {
        prevWidth = width;
        prevHeight = height;
        manager.controller().onMessagesFrameChanged();
      } else {
        manager.onViewportMeasure();
      }
    }
  }

  private MessagesItemDecoration itemDecoration;

  private void init () {
    setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS : OVER_SCROLL_NEVER);
    // TGx101 (user 2026-10-27 00:03 «свайп назад в чатах, когда опускаешься в самый низ, — безотказно, как в списке
    // чатов»): a fling into the end leaves the list stretched (Android 12+); RecyclerView «catches» that stretch on the
    // next touch and keeps the gesture from the back swipe. The touch lets the stretch go first (as ChatsRecyclerView)
    setEdgeEffectFactory(new EdgeEffectFactory() {
      @androidx.annotation.NonNull
      @Override
      protected android.widget.EdgeEffect createEdgeEffect (@androidx.annotation.NonNull RecyclerView view, int direction) {
        android.widget.EdgeEffect edge = super.createEdgeEffect(view, direction);
        if (direction == DIRECTION_TOP) tgx101TopEdge = edge;
        else if (direction == DIRECTION_BOTTOM) tgx101BottomEdge = edge;
        return edge;
      }
    });
    itemAnimator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, ITEM_ANIMATOR_DURATION);
    itemAnimator.setSupportsChangeAnimations(false);
    setItemAnimator(null);
    callback = new MessagesTouchHelperCallback();
    touchHelper = new CustomTouchHelper(callback);
    callback.setTouchHelper(touchHelper);
    touchHelper.attachToRecyclerView(this);
    addItemDecoration(itemDecoration = new MessagesItemDecoration());
  }

  public MessagesTouchHelperCallback getMessagesTouchHelper () {
    return callback;
  }

  public void setManager (MessagesManager manager) {
    this.manager = manager;
  }

  public void setController (MessagesController controller) {
    callback.setController(controller);
  }

  public void startSwipe (View view) {
    if (view == null) {
      return;
    }
    RecyclerView.ViewHolder holder;
    try {
      holder = getChildViewHolder(view);
    } catch (IllegalArgumentException ignored) {
      return;
    }
    if (holder != null && holder.getItemViewType() != MessagesHolder.TYPE_EMPTY) {
      touchHelper.startSwipe(holder);
    }
  }

  public MessagesManager getManager () {
    return manager;
  }

  private boolean ignore;

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (!ignore) {
      ignore = true;
      manager.rebuildFirstItem(true, changed);
      ignore = false;
    }
    if (manager != null) {
      manager.controller().onMessagesFrameChanged();
    }
  }

  private boolean disallowIntercept;

  @Override
  public void requestDisallowInterceptTouchEvent (boolean disallowIntercept) {
    if (disallowIntercept && !this.disallowIntercept && org.thunderdog.challegram.BuildConfig.TGX101_DIAG) {
      // TGx101 diag (2026-10-27, back swipe lost right after a fling): who keeps the finger from the back swipe
      StackTraceElement[] st = new Throwable().getStackTrace();
      StringBuilder b = new StringBuilder("messages list: disallow intercept, state " + getScrollState() + " by");
      for (int i = 1; i < Math.min(st.length, 5); i++) b.append(' ').append(st[i].getClassName().substring(st[i].getClassName().lastIndexOf('.') + 1)).append('.').append(st[i].getMethodName()).append(':').append(st[i].getLineNumber());
      org.thunderdog.challegram.Tgx101Diag.mark(b.toString());
    }
    this.disallowIntercept = disallowIntercept;
    super.requestDisallowInterceptTouchEvent(disallowIntercept);
  }

  public boolean disallowInterceptTouchEvent () {
    return disallowIntercept;
  }

  public void invalidateAll () {
    final LayoutManager manager = getLayoutManager();
    if (manager != null && manager instanceof LinearLayoutManager) {
      int first = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
      int last = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
      for (int i = first; i <= last; i++) {
        View v = manager.findViewByPosition(i);
        if (v != null) {
          v.invalidate();
        }
      }
    } else {
      for (int i = 0; i < getChildCount(); i++) {
        View v = getChildAt(i);
        if (v != null) {
          v.invalidate();
        }
      }
    }
  }

  private boolean isScrolling;
  private float scrollFactor;
  private FactorAnimator scrollAnimator;

  private boolean startAnimatorOnAnimationEnd;

  public void setIsScrolling (boolean isScrolling) {
    if (this.isScrolling != isScrolling) {
      this.isScrolling = isScrolling;
      if (scrollAnimator == null) {
        scrollAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.scrollFactor);
      }
      if (scrollAnimator.isAnimating() && !isScrolling) {
        startAnimatorOnAnimationEnd = true;
        return;
      }
      startAnimatorOnAnimationEnd = false;
      scrollAnimator.setStartDelay(isScrolling ? 0 : 1500);
      scrollAnimator.setDuration(isScrolling ? 120l : 180l);
      scrollAnimator.animateTo(isScrolling ? 1f : 0f);
    }
  }

  public final void invalidateDate () {
    int topOffset = getTopOffset();
    invalidate(topOffset, 0, getMeasuredWidth(), topOffset + TGMessage.getDateHeight(manager.useBubbles()));
  }

  public void showDateForcely () {
    if (!isScrolling && false) {
      isScrolling = true;
      if (scrollFactor != 0f) {
        scrollFactor = 1f;
        if (scrollAnimator != null) {
          scrollAnimator.forceFactor(1f);
        }
      }
      invalidateDate();
      setIsScrolling(false);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (this.scrollFactor != factor) {
      this.scrollFactor = factor;
      invalidateDate();
      if (factor == 1f && startAnimatorOnAnimationEnd && !isScrolling) {
        startAnimatorOnAnimationEnd = false;
        scrollAnimator.setStartDelay(1500);
        scrollAnimator.setDuration(180l);
        scrollAnimator.animateTo(0f);
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  // TGx101 (user video 2026-10-23 00:34 «как в офф. Telegram»): a jump that reloads the chat (a pinned message, a quote,
  // the «down» arrow) — the screen that was there doesn't vanish for one frame: a picture of it slides out and the new
  // part of the chat comes in right behind it, as one long scroll. Android 6+: a Picture on a hardware canvas.
  private @androidx.annotation.Nullable android.graphics.Picture tgx101JumpShot;
  private float tgx101JumpShotY; // where the old screen's top is now
  // (user 2026-10-23 «при прокрутке дата сильно резко меняется»): the date stuck at the top runs through every day the
  // slide passes — it fades out for the slide and fades back in, already with the right day, once the chat stands
  private float tgx101StuckDateAlpha = 1f;
  private final FactorAnimator tgx101StuckDateAnimator = new FactorAnimator(1, (id, factor, fraction, callee) -> {
    tgx101StuckDateAlpha = factor;
    invalidateDate();
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L, 1f);

  // TGx101 diagnostics builds (user 2026-10-23 «прыжок чата бывает редко, нужно ловить; с чужими сообщениями»): every
  // frame the bottom message's place is noted; a chat that moves there and back within 0.3 s, or leaps 40 dp in a frame,
  // with no finger on it, writes its last 3 s of moves (and why they might be) to the log and copies its frames
  private long tgx101JumpAnchorId;
  private int tgx101JumpAnchorBottom = Integer.MIN_VALUE, tgx101JumpAnchorPad;
  private int tgx101LastMove;
  private long tgx101LastMoveAt, tgx101LastCatchAt;
  private final java.util.ArrayDeque<String> tgx101Moves = new java.util.ArrayDeque<>();
  private final android.view.ViewTreeObserver.OnPreDrawListener tgx101JumpWatch = () -> {
    tgx101WatchJump();
    return true;
  };

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if (org.thunderdog.challegram.BuildConfig.TGX101_DIAG) getViewTreeObserver().addOnPreDrawListener(tgx101JumpWatch);
  }

  private long tgx101FocusAt, tgx101ListTouchAt;

  @Override
  public void onWindowFocusChanged (boolean hasWindowFocus) {
    super.onWindowFocusChanged(hasWindowFocus);
    if (hasWindowFocus) tgx101FocusAt = android.os.SystemClock.uptimeMillis();
  }

  @Override
  protected void onDetachedFromWindow () {
    if (org.thunderdog.challegram.BuildConfig.TGX101_DIAG) getViewTreeObserver().removeOnPreDrawListener(tgx101JumpWatch);
    super.onDetachedFromWindow();
  }

  private void tgx101WatchJump () {
    if (getChildCount() == 0 || !(getAdapter() instanceof MessagesAdapter)) return;
    View bottom = getChildAt(0);
    int position = getChildAdapterPosition(bottom);
    TGMessage message = position != NO_POSITION ? ((MessagesAdapter) getAdapter()).getMessage(position) : null;
    if (message == null) return;
    // leaving or coming back: the system hides the window then and the keyboard returns with it in one step — the moves
    // aren't seen (six false catches 2026-10-23, the recordings are steady)
    int padMove = getPaddingBottom() - tgx101JumpAnchorPad;
    tgx101JumpAnchorPad = getPaddingBottom();
    if (!hasWindowFocus() || android.os.SystemClock.uptimeMillis() - tgx101FocusAt < 800) {
      tgx101JumpAnchorBottom = Integer.MIN_VALUE;
      return;
    }
    long id = message.getId();
    int at = bottom.getBottom() + (int) getTranslationY();
    long now = android.os.SystemClock.uptimeMillis();
    // the message watched last frame, even if a new one (someone else's, say) came in under it
    int was = Integer.MIN_VALUE;
    if (tgx101JumpAnchorBottom != Integer.MIN_VALUE) {
      if (id == tgx101JumpAnchorId) {
        was = at;
      } else {
        for (int i = 1; i < getChildCount() && i < 4; i++) {
          View child = getChildAt(i);
          int p = getChildAdapterPosition(child);
          TGMessage m = p != NO_POSITION ? ((MessagesAdapter) getAdapter()).getMessage(p) : null;
          if (m != null && m.getId() == tgx101JumpAnchorId) {
            was = child.getBottom() + (int) getTranslationY();
            break;
          }
        }
      }
    }
    int previous = tgx101JumpAnchorBottom;
    // a message came in under the watched one: the list rises by its height in that frame — meant, not a jump (most of
    // the catches 23.10 were sends); the frames after it are still watched
    final boolean inserted = id != tgx101JumpAnchorId;
    tgx101JumpAnchorId = id;
    tgx101JumpAnchorBottom = at;
    if (was == Integer.MIN_VALUE) return;
    int move = was - previous;
    if (move == 0) return;
    // (user 2026-10-23 «чат попрыгивал при печати»): with the keyboard up a lone 12 dp step is caught too — unless it is
    // the list following its own padding (the field grew a line, the keyboard moved), which is meant
    // (2026-10-24) the classic field riding the keyboard: the window is laid out for the keyboard's end first, the list
    // reaches down again before that frame is drawn — meant too
    boolean expected = (padMove != 0 && Math.abs(move + padMove) < Screen.dp(2f)) || (manager != null && (manager.controller().tgx101IsKeyboardRiding() || manager.controller().tgx101IsListSwapping() || (!manager.controller().tgx101FloatingLook() && manager.controller().tgx101IsFieldRevealing())));
    boolean typing = android.os.Build.VERSION.SDK_INT >= 30 && getRootWindowInsets() != null && getRootWindowInsets().isVisible(android.view.WindowInsets.Type.ime());
    // a finger on the list itself — a tap on «Send» or a key isn't one (user 2026-10-23 «прыгает при отправке»)
    boolean touched = tgx101FingerDown || android.os.SystemClock.uptimeMillis() - tgx101ListTouchAt < 400;
    boolean ours = manager != null && manager.tgx101IsAutoScrolling();
    String line = "+" + (now % 100000) + " " + (move > 0 ? "down " : "up ") + Math.abs(move) + " px"
      + (touched ? " finger" : "") + (ours ? " glide" : "") + (getScrollState() != SCROLL_STATE_IDLE ? " scroll" + getScrollState() : "")
      + (isAnimating() ? " items-anim" : "") + (typing ? " keyboard" : "") + (expected ? " follows-pad" : "") + " pad " + getPaddingBottom() + " ty " + (int) getTranslationY() + " h " + getHeight();
    tgx101Moves.addLast(now + "|" + line);
    while (!tgx101Moves.isEmpty() && now - Long.parseLong(tgx101Moves.peekFirst().substring(0, tgx101Moves.peekFirst().indexOf('|'))) > 3000) tgx101Moves.removeFirst();
    boolean reversed = !ours && tgx101LastMove != 0 && Integer.signum(move) != Integer.signum(tgx101LastMove) && now - tgx101LastMoveAt < 300
      && Math.abs(move) >= Screen.dp(4f) && Math.abs(tgx101LastMove) >= Screen.dp(4f);
    boolean leap = !expected && !inserted && !ours && (Math.abs(move) >= Screen.dp(40f) || (typing && !ours && getScrollState() == SCROLL_STATE_IDLE && Math.abs(move) >= Screen.dp(12f)));
    if (expected) { // riding with the keyboard or the field: written down, never a jump by itself
      tgx101LastMove = 0;
      return;
    }
    tgx101LastMove = move;
    tgx101LastMoveAt = now;
    if ((reversed || leap) && !touched && now - tgx101LastCatchAt > 10000) {
      tgx101LastCatchAt = now;
      StringBuilder b = new StringBuilder("chat jump caught (" + (reversed ? "there and back" : "leap") + "), last moves:");
      for (String entry : tgx101Moves) b.append("\n    ").append(entry.substring(entry.indexOf('|') + 1));
      org.thunderdog.challegram.Tgx101Diag.mark(b.toString());
      android.app.Activity activity = org.thunderdog.challegram.tool.UI.getContext(getContext());
      org.thunderdog.challegram.Tgx101FrameTrace.start(activity, "chat-jump");
    }
  }

  public void tgx101TakeJumpShot () {
    tgx101JumpShot = null;
    if (android.os.Build.VERSION.SDK_INT < 23 || getWidth() == 0 || getHeight() == 0) return;
    android.graphics.Picture shot = new android.graphics.Picture();
    Canvas c = shot.beginRecording(getWidth(), getHeight());
    try {
      draw(c);
    } finally {
      shot.endRecording();
    }
    tgx101JumpShot = shot;
    tgx101JumpShotY = 0f;
    tgx101StuckDateAnimator.forceFactor(0f);
    tgx101StuckDateAlpha = 0f;
    invalidate();
  }

  public boolean tgx101HasJumpShot () {
    return tgx101JumpShot != null;
  }

  public void tgx101SetJumpShotY (float y) {
    if (tgx101JumpShot == null) return;
    if (Math.abs(y) >= getHeight()) {
      tgx101ClearJumpShot();
      return;
    }
    tgx101JumpShotY = y;
    invalidate();
  }

  public void tgx101ClearJumpShot () {
    if (tgx101JumpShot != null) {
      tgx101JumpShot = null;
      invalidate();
    }
    if (tgx101StuckDateAlpha < 1f) {
      tgx101StuckDateAnimator.setStartDelay(80);
      tgx101StuckDateAnimator.animateTo(1f);
    }
  }

  /** While the old screen slides out, the new chat's dates are drawn only in the part it has entered */
  private int tgx101ClipToLive (Canvas c) {
    if (tgx101JumpShot == null) return -1;
    int save = c.save();
    if (tgx101JumpShotY >= 0f) {
      c.clipRect(0, 0, getWidth(), tgx101JumpShotY);
    } else {
      c.clipRect(0, getHeight() + tgx101JumpShotY, getWidth(), getHeight());
    }
    return save;
  }

  @Override
  protected void dispatchDraw (Canvas c) {
    if (tgx101JumpShot == null) {
      super.dispatchDraw(c);
      return;
    }
    final int width = getWidth(), height = getHeight();
    final float y = tgx101JumpShotY;
    // the new messages only in the part the old screen has left, the old screen in the rest
    int save = c.save();
    if (y >= 0f) {
      c.clipRect(0, 0, width, y);
    } else {
      c.clipRect(0, height + y, width, height);
    }
    super.dispatchDraw(c);
    c.restoreToCount(save);
    save = c.save();
    c.clipRect(0, Math.max(0f, y), width, Math.min(height, height + y));
    c.translate(0, y);
    c.drawPicture(tgx101JumpShot);
    c.restoreToCount(save);
  }

  @Override
  public void setTranslationY (float translationY) {
    if (scrollFactor == 0f) {
      super.setTranslationY(translationY);
    } else if (getTranslationY() != translationY) {
      super.setTranslationY(translationY);
      invalidateDate();
    }
  }

  private int getTopOffset () {
    int topOffset = manager.getTopOverlayOffset();
    HeaderView headerView = UI.getHeaderView(getContext());
    if (headerView != null) {
      topOffset += headerView.getFilling().getPlayerOffset();
    }
    return topOffset;
  }

  // TGx101 (4PDA request 2026-10-04): at the very end of a channel, a pull up leads to the next unread channel
  private boolean tgx101NextTracking, tgx101NextPast;
  private float tgx101NextDownY, tgx101NextDownX;

  // TGx101 (user 2026-10-23 22:24 «когда внизу чата, быстрый двойной свайп открывает клавиатуру»; MagiX → Message field,
  // off by default): two quick flicks up within 0.6 s while the list is at its end
  private float tgx101FlickDownY, tgx101FlickDownX;
  private long tgx101FlickDownAt, tgx101LastFlickAt;
  private boolean tgx101FlickAtEnd;

  private void tgx101WatchSwipeKeyboard (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        tgx101FlickDownY = e.getY();
        tgx101FlickDownX = e.getX();
        tgx101FlickDownAt = e.getEventTime();
        tgx101FlickAtEnd = !canScrollVertically(1) && !manager.tgx101CanLoadBottom();
        break;
      case MotionEvent.ACTION_UP: {
        float up = tgx101FlickDownY - e.getY(), side = Math.abs(e.getX() - tgx101FlickDownX);
        boolean flick = tgx101FlickAtEnd && !canScrollVertically(1) && up > Screen.dp(40f) && side < up && e.getEventTime() - tgx101FlickDownAt < 350;
        if (!flick) {
          tgx101LastFlickAt = 0;
          break;
        }
        if (tgx101LastFlickAt != 0 && e.getEventTime() - tgx101LastFlickAt < 600) {
          tgx101LastFlickAt = 0;
          manager.controller().tgx101OpenKeyboardBySwipe();
        } else {
          tgx101LastFlickAt = e.getEventTime();
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL:
        tgx101LastFlickAt = 0;
        break;
    }
  }
  private long tgx101LastScrollAt;

  // TGx101 (user 2026-10-04, «как в галерее iPhone»): in select mode a drag that starts sideways selects (or
  // unselects, if the first message was selected) every message the finger passes; near the edges the list scrolls
  private boolean tgx101DragSelect, tgx101DragTracking, tgx101DragTarget;
  private float tgx101DragDownX, tgx101DragDownY, tgx101DragLastY;
  private int tgx101DragStartPos = -1;
  // (user 2026-10-22 «выделение по свайпу с удержанием — с рывками, сделай плавнее как в exteraGram»): the list scrolled
  // 12 dp at once every 16 ms on a timer — on a 120 Hz screen a step every other frame, at full speed the moment the
  // finger touched the edge zone. Now every frame, by the frame's time, faster the deeper the finger is in the zone
  private long tgx101AutoScrollLast;
  private float tgx101AutoScrollRest;
  private final Runnable tgx101AutoScroll = new Runnable() {
    @Override
    public void run () {
      if (!tgx101DragSelect) return;
      long now = android.os.SystemClock.uptimeMillis();
      long dt = tgx101AutoScrollLast == 0 ? 8 : Math.min(32, now - tgx101AutoScrollLast);
      tgx101AutoScrollLast = now;
      float edge = Screen.dp(96f);
      float depth = tgx101DragLastY < edge ? -(edge - tgx101DragLastY) / edge : tgx101DragLastY > getHeight() - edge ? (tgx101DragLastY - (getHeight() - edge)) / edge : 0f;
      depth = Math.max(-1f, Math.min(1f, depth));
      if (depth != 0f) {
        float k = Math.abs(depth);
        float speed = Screen.dp(.12f + 1.3f * k * k); // px per ms: from ~120 to ~1400 dp a second
        tgx101AutoScrollRest += Math.signum(depth) * speed * dt;
        int dy = (int) tgx101AutoScrollRest;
        if (dy != 0) {
          tgx101AutoScrollRest -= dy;
          scrollBy(0, dy);
          tgx101DragApply(tgx101DragLastY);
        }
      } else {
        tgx101AutoScrollRest = 0f;
      }
      postOnAnimation(this);
    }
  };

  private void tgx101StartAutoScroll () {
    removeCallbacks(tgx101AutoScroll);
    tgx101AutoScrollLast = 0;
    tgx101AutoScrollRest = 0f;
    postOnAnimation(tgx101AutoScroll);
  }

  private int tgx101PositionAt (float y) {
    View child = findChildViewUnder(getWidth() / 2f, y);
    return child != null ? getChildAdapterPosition(child) : -1;
  }

  // TGx101 (user 2026-10-05 22:42): the drag works both ways — moving the finger back undoes what it selected beyond
  // the finger; messages keep their state from before the drag outside the range
  private final android.util.LongSparseArray<org.thunderdog.challegram.data.TGMessage> tgx101DragTouched = new android.util.LongSparseArray<>();

  private void tgx101DragApply (float y) {
    int end = tgx101PositionAt(y);
    if (end == -1 || tgx101DragStartPos == -1) return;
    int from = Math.min(end, tgx101DragStartPos), to = Math.max(end, tgx101DragStartPos);
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (!(child instanceof org.thunderdog.challegram.component.chat.MessageView)) continue;
      int pos = getChildAdapterPosition(child);
      org.thunderdog.challegram.data.TGMessage msg = ((org.thunderdog.challegram.component.chat.MessageView) child).getMessage();
      if (msg == null || !msg.canBeSelected()) continue;
      boolean inRange = pos >= from && pos <= to;
      long key = msg.getId();
      if (inRange) {
        if (msg.isCompletelySelected() != tgx101DragTarget) {
          if (tgx101DragTouched.get(key) == null) tgx101DragTouched.put(key, msg);
          manager.controller().selectAllMessages(msg, -1, -1);
        }
      } else if (tgx101DragTouched.get(key) != null) {
        // left the range: back to how it was before this drag
        if (msg.isCompletelySelected() == tgx101DragTarget) manager.controller().selectAllMessages(msg, -1, -1);
        tgx101DragTouched.remove(key);
      }
    }
  }

  private boolean tgx101DownInSelect, tgx101LongPressDrag;

  /** TGx101: a finger is on the message list right now (the menu opened by any long press can follow it) */
  public static boolean tgx101FingerDown;

  @Override
  public boolean dispatchTouchEvent (MotionEvent e) {
    int tgx101Action = e.getActionMasked();
    tgx101ListTouchAt = android.os.SystemClock.uptimeMillis();
    if (tgx101Action == MotionEvent.ACTION_DOWN) tgx101FingerDown = true;
    else if (tgx101Action == MotionEvent.ACTION_UP || tgx101Action == MotionEvent.ACTION_CANCEL) tgx101FingerDown = false;
    if (e.getActionMasked() != MotionEvent.ACTION_DOWN && org.thunderdog.challegram.ui.Tgx101MessageMenu.dragActive()
      && org.thunderdog.challegram.ui.Tgx101MessageMenu.drag(e.getRawX(), e.getRawY(), e.getActionMasked())) {
      if (e.getActionMasked() == MotionEvent.ACTION_UP) {
        MotionEvent cancel = MotionEvent.obtain(e);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel); // the message under the finger does not get a tap
        cancel.recycle();
      }
      return true;
    }
    // user's Vivo video 23:14: they drag straight down the left checkbox column, or keep the finger after the long
    // press that started selecting — both should select, not only a sideways start
    if (manager != null && e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      tgx101DownInSelect = manager.controller().inSelectMode();
      tgx101LongPressDrag = false;
      tgx101DragDownX = e.getX();
      tgx101DragDownY = e.getY();
      tgx101DragStartPos = tgx101PositionAt(e.getY());
    }
    if (manager != null && !tgx101DownInSelect && !tgx101DragSelect && e.getActionMasked() == MotionEvent.ACTION_MOVE
      && manager.controller().inSelectMode() && Math.abs(e.getY() - tgx101DragDownY) > Screen.getTouchSlop() * 2 && tgx101DragStartPos != -1) {
      // the long press has just opened select mode under this finger: continue as a drag selection
      tgx101DragSelect = true;
      tgx101DragTarget = true;
      tgx101DragTouched.clear();
      tgx101DragLastY = e.getY();
      org.thunderdog.challegram.Tgx101Diag.mark("select: drag after long press");
      MotionEvent cancel = MotionEvent.obtain(e);
      cancel.setAction(MotionEvent.ACTION_CANCEL);
      super.dispatchTouchEvent(cancel);
      cancel.recycle();
      tgx101DragApply(e.getY());
      tgx101StartAutoScroll();
      return true;
    }
    if (manager != null && tgx101DragSelect && !tgx101DownInSelect) {
      if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
        tgx101DragLastY = e.getY();
        tgx101DragApply(e.getY());
        return true;
      }
      if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
        tgx101DragSelect = false;
        removeCallbacks(tgx101AutoScroll);
        return true;
      }
    }
    if (manager != null && manager.controller().inSelectMode()) {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          tgx101DragSelect = false;
          tgx101DragTracking = true;
          tgx101DragDownX = e.getX();
          tgx101DragDownY = e.getY();
          tgx101DragStartPos = tgx101PositionAt(e.getY());
          break;
        }
        case MotionEvent.ACTION_MOVE: {
          if (tgx101DragSelect) {
            tgx101DragLastY = e.getY();
            tgx101DragApply(e.getY());
            return true;
          }
          if (tgx101DragTracking) {
            float dx = Math.abs(e.getX() - tgx101DragDownX), dy = Math.abs(e.getY() - tgx101DragDownY);
            boolean inCheckColumn = tgx101DragDownX < Screen.dp(56f) || tgx101DragDownX > getWidth() - Screen.dp(40f);
            if (dy > Screen.getTouchSlop() && dy > dx && !inCheckColumn) {
              tgx101DragTracking = false; // an ordinary scroll
            } else if ((inCheckColumn && dy > Screen.getTouchSlop()) || (dx > Screen.getTouchSlop() * 1.5f && dx > dy * 1.5f) && tgx101DragStartPos != -1) {
              View child = findChildViewUnder(getWidth() / 2f, tgx101DragDownY);
              org.thunderdog.challegram.data.TGMessage msg = child instanceof org.thunderdog.challegram.component.chat.MessageView ? ((org.thunderdog.challegram.component.chat.MessageView) child).getMessage() : null;
              if (msg != null && msg.canBeSelected()) {
                tgx101DragSelect = true;
                // user 2026-10-06 («как в галерее айфона»): the first message decides — unselected → the drag
                // selects, selected → the drag unselects, whatever the previous drag did
                tgx101DragTarget = !msg.isCompletelySelected();
                tgx101DragTouched.clear();
                tgx101DragLastY = e.getY();
                org.thunderdog.challegram.Tgx101Diag.mark("select: drag " + (tgx101DragTarget ? "select" : "unselect"));
                // no second vibration here (user 2026-10-05: the long press already vibrated once)
                MotionEvent cancel = MotionEvent.obtain(e);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                tgx101DragApply(e.getY());
                tgx101StartAutoScroll();
                return true;
              }
              tgx101DragTracking = false;
            }
          }
          break;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          tgx101DragTracking = false;
          if (tgx101DragSelect) {
            tgx101DragSelect = false;
            removeCallbacks(tgx101AutoScroll);
            return true;
          }
          break;
        }
      }
    }
    if (manager != null && org.thunderdog.challegram.unsorted.Settings.instance().tgx101SwipeKeyboard()) tgx101WatchSwipeKeyboard(e);
    if (manager != null) {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          // (4PDA 23.10 «переход в другой канал резкий, можно случайно перейти»): only a fresh pull from a list already at
          // rest at its end — not the touch that stops a fling into the end, not a scroll that just reached it
          tgx101NextTracking = org.thunderdog.challegram.unsorted.Settings.instance().tgx101NextChannelSwipe()
            && manager.controller().isChannel() && !manager.tgx101CanLoadBottom() && !canScrollVertically(1)
            && !manager.controller().inPreviewMode() && getScrollState() == SCROLL_STATE_IDLE; // (user 23.10 22:40 «свайп на другой канал не работает»: a 0.4 s wait after any scroll state change killed it)
          tgx101NextDownX = e.getX();
          tgx101NextPast = false;
          tgx101NextDownY = e.getY();
          if (tgx101NextTracking) manager.controller().tgx101PrepareNextChannel();
          break;
        case MotionEvent.ACTION_MOVE:
          if (tgx101NextTracking) {
            if (canScrollVertically(1)) { // the list moved away from the end
              tgx101NextTracking = false;
              manager.controller().tgx101ShowNextChannel(0f, false);
              break;
            }
            if (Math.abs(e.getX() - tgx101NextDownX) > Screen.dp(48f) && Math.abs(e.getX() - tgx101NextDownX) > Math.abs(tgx101NextDownY - e.getY())) {
              tgx101NextTracking = false; // a sideways swipe (back, reply) — not a pull
              manager.controller().tgx101ShowNextChannel(0f, false);
              break;
            }
            float pull = tgx101NextDownY - e.getY();
            float progress = Math.max(0f, Math.min(1f, pull / Screen.dp(150f))); // was 110 dp — too easy to reach by accident
            boolean past = progress >= 1f;
            if (past && !tgx101NextPast) {
              org.thunderdog.challegram.tool.Tgx101Haptics.play(org.thunderdog.challegram.tool.Tgx101Haptics.CLICK);
            }
            tgx101NextPast = past;
            manager.controller().tgx101ShowNextChannel(progress, past);
          }
          break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          if (tgx101NextTracking) {
            boolean open = tgx101NextPast && e.getActionMasked() == MotionEvent.ACTION_UP;
            tgx101NextTracking = false;
            tgx101NextPast = false;
            manager.controller().tgx101ShowNextChannel(0f, false);
            if (open) manager.controller().tgx101OpenNextChannel();
          }
          break;
      }
    }
    return super.dispatchTouchEvent(e);
  }

  private boolean tgx101StoppedFling;

  /** True for the touch that stopped a fling: message views don't treat it as a tap */
  public boolean tgx101StoppedFling () {
    return tgx101StoppedFling;
  }

  private long tgx101FlingEndTime;

  @Override
  public void onScrollStateChanged (int state) {
    super.onScrollStateChanged(state);
    tgx101LastScrollAt = android.os.SystemClock.uptimeMillis(); // the pull to the next channel waits for the list to rest
    if (state == SCROLL_STATE_IDLE && tgx101LastScrollState == SCROLL_STATE_SETTLING) {
      tgx101FlingEndTime = android.os.SystemClock.uptimeMillis();
    }
    tgx101LastScrollState = state;
    if (state == SCROLL_STATE_DRAGGING && org.thunderdog.challegram.BuildConfig.TGX101_DIAG) {
      org.thunderdog.challegram.Tgx101Diag.mark("messages list: drag starts (stopped fling " + tgx101StoppedFling + ")");
    }
  }

  private int tgx101LastScrollState = SCROLL_STATE_IDLE;

  private android.widget.EdgeEffect tgx101TopEdge, tgx101BottomEdge;
  private float tgx101DownX, tgx101DownY;

  /** (log 2026-10-27 15:33: «messages list: drag starts» 1 ms after the finger landed on a list that was still moving or
   *  stretched): RecyclerView «catches» it on the down — dragging at once and holding the finger from its parents, so the
   *  back swipe never saw it. The catch is undone: the list starts dragging only when the finger really goes up or down */
  private void tgx101InterceptDownNotDragging (MotionEvent e) {
    super.onInterceptTouchEvent(e);
    if (getScrollState() == SCROLL_STATE_DRAGGING) {
      stopScroll();
      if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
      org.thunderdog.challegram.Tgx101Diag.mark("messages list: caught on down — let go for the back swipe");
    }
  }

  private boolean tgx101ReleaseStretch () {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return false;
    boolean released = false;
    for (android.widget.EdgeEffect edge : new android.widget.EdgeEffect[] {tgx101TopEdge, tgx101BottomEdge}) {
      if (edge != null && edge.getDistance() != 0f) {
        edge.finish();
        released = true;
      }
    }
    if (released) invalidate();
    return released;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN && getScrollState() != SCROLL_STATE_SETTLING
      && android.os.SystemClock.uptimeMillis() - tgx101FlingEndTime < 300) {
      // TGx101 (log 2026-10-20 12:01, «ложные касания»): short flicks over a round video's text stop within 0.15 s, and the
      // next finger landing a moment later — a flick that barely moved — opened the message menu, 4 times in 2 s.
      // Right after a fling the touch still belongs to the scrolling, not to the message
      tgx101StoppedFling = true;
      tgx101FlingEndTime = 0;
      tgx101DownX = e.getX();
      tgx101DownY = e.getY();
      tgx101ReleaseStretch();
      tgx101InterceptDownNotDragging(e);
      return true; // the same: right after a fling the finger is the list's, the back swipe can take it
    } else if (e.getActionMasked() == MotionEvent.ACTION_DOWN && (tgx101ReleaseStretch() | getScrollState() == SCROLL_STATE_SETTLING)) {
      // TGx101 (user 2026-10-05): a touch on a still-flinging list caught the gesture for the list
      // (requestDisallowInterceptTouchEvent) — the swipe back to the chat list did not work while scrolling.
      // Stop the fling first: the list then treats it as a fresh touch and the back swipe can take it
      stopScroll();
      tgx101StoppedFling = true; // this touch only stops the list — not a tap on a message (user 2026-10-05 19:54)
      // (user 2026-10-27 «в списке чатов жест назад срабатывает и сразу после прокрутки»): as ChatsRecyclerView — the
      // list keeps this finger itself, a message under it never takes it and so never holds it from the back swipe
      tgx101DownX = e.getX();
      tgx101DownY = e.getY();
      tgx101InterceptDownNotDragging(e);
      return true;
    } else if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      tgx101StoppedFling = false;
    }
    // TGx101 (user 2026-10-27 «свайп назад в чатах должен работать безотказно», log 15:02: back swipes «117 right, 43 up»
    // failed — the list started scrolling at 8 dp of vertical move while the back gesture still waited for its distance):
    // a finger going more right than up or down doesn't start the list's scroll, the back swipe takes it. To the left
    // (reply) nothing changes; left to right is always back here
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      tgx101DownX = e.getX();
      tgx101DownY = e.getY();
    } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE && getScrollState() == SCROLL_STATE_IDLE) {
      float dx = e.getX() - tgx101DownX, dy = e.getY() - tgx101DownY;
      if (dx > 0 && dx > Math.abs(dy) * 1.3f && !org.thunderdog.challegram.core.Lang.rtl()) {
        return false;
      }
    }
    boolean res = super.onInterceptTouchEvent(e);
    if (e.getAction() == MotionEvent.ACTION_DOWN && isInsideDate(e.getX(), e.getY())) {
      return true;
    }
    return res;
  }

  private boolean listeningClick;
  private float listenClickX, listenClickY;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        float x = e.getX();
        float y = e.getY();
        if (listeningClick = isInsideDate(x, y)) {
          listenClickX = x;
          listenClickY = y;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (listeningClick && (Math.abs(listenClickX - e.getX()) > Screen.getTouchSlop() || Math.abs(listenClickY - e.getY()) > Screen.getTouchSlop())) {
          listeningClick = false;
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        listeningClick = false;
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (listeningClick) {
          if (onDateClick()) {
            ViewUtils.onClick(this);
          }
          listeningClick = false;
        }
        break;
      }
    }
    return super.onTouchEvent(e);
  }

  private int jumpToBeginningOfTheDay;

  private boolean isInsideDate (float x, float y) {
    if (itemDecoration == null) {
      return false;
    }
    if (manager.controller().inPreviewMode()) {
      return false;
    }
    // TGx101 (user 2026-10-12 «при нажатии на дату в самом чате не открывается календарь»): every date pill on screen
    // can be tapped — the ones between days too, not only the top one while scrolling
    int pillHeight = Screen.dp(26f), hitBound = Screen.dp(4f), hitCx = getMeasuredWidth() / 2;
    for (int i = itemDecoration.tgx101Hits.size() - 1; i >= 0; i--) {
      Object[] hit = itemDecoration.tgx101Hits.get(i);
      TGMessage m = (TGMessage) hit[0];
      int top = (Integer) hit[1];
      int cyHit = top + Screen.dp(manager.useBubbles() ? 5f : 8f) + pillHeight / 2;
      int half = m.getDateWidth() / 2 + m.getDatePadding() + hitBound;
      if (x >= hitCx - half && x < hitCx + half && y >= cyHit - pillHeight / 2f - hitBound && y < cyHit + pillHeight / 2f + hitBound) {
        jumpToBeginningOfTheDay = m.getDate();
        return true;
      }
    }
    if (itemDecoration.lastMessage == null || itemDecoration.lastAlpha == 0f) {
      return false;
    }
    int dateHeight = Screen.dp(26f);
    final int cx = getMeasuredWidth() / 2;
    final int cy = itemDecoration.lastTop + Screen.dp(manager.useBubbles() ? 5f : 8f) + dateHeight / 2; // + dateHeight / 2;
    int dateWidth = itemDecoration.lastMessage.getDateWidth();
    int padding = itemDecoration.lastMessage.getDatePadding();
    int bound = Screen.dp(4f);
    jumpToBeginningOfTheDay = itemDecoration.lastMessage.getDate();
    int boundHorizontal = dateWidth / 2 + padding + bound;
    int boundVertical = dateHeight / 2 + bound;

    return x >= cx - boundHorizontal && x < cx + boundHorizontal && y >= cy - boundVertical && y < cy + boundVertical;

    // return x >= cx - padding - dateWidth / 2 - bound && x < cx + padding + dateWidth / 2 + bound && y >= cy - padding - dateHeight / 2 - bound && y < cy + padding + dateHeight / 2 + bound;
  }

  private boolean onDateClick () {
    if (jumpToBeginningOfTheDay != 0) {
      // TGx101 (mockup «Поиск-по-дате»): the date pill opens the calendar at its month instead of jumping to the day's start
      if (manager.controller().isSecretChat()) manager.controller().jumpToBeginningOfTheDay(jumpToBeginningOfTheDay);
      else manager.controller().tgx101OpenCalendar(jumpToBeginningOfTheDay);
      return true;
    }
    return false;
  }

  private class MessagesItemDecoration extends ItemDecoration {
    protected TGMessage lastMessage;
    protected int lastTop;
    protected float lastAlpha;
    protected final java.util.ArrayList<Object[]> tgx101Hits = new java.util.ArrayList<>(); // {message with the date, pill top}

    private void tgx101Hit (TGMessage msg, int top, float alpha) {
      if (alpha > .1f) tgx101Hits.add(new Object[] {msg, top});
    }

    @Override
    public void onDrawOver (Canvas c, RecyclerView parent, State state) {
      tgx101Hits.clear();
      this.lastMessage = null;
      this.lastTop = 0;
      this.lastAlpha = 0f;

      MessagesAdapter adapter = (MessagesAdapter) parent.getAdapter();
      if (adapter.getMessageCount() == 0) {
        return;
      }

      final int centerX = parent.getMeasuredWidth() / 2;
      final int tgx101Save = tgx101ClipToLive(c);
      try {
        tgx101DrawDates(c, parent, state, adapter, centerX);
      } finally {
        if (tgx101Save != -1) c.restoreToCount(tgx101Save);
      }
    }

    private void tgx101DrawDates (Canvas c, RecyclerView parent, State state, MessagesAdapter adapter, int centerX) {
      // int lastTop = 0;
      boolean hasDrawn = false;

      int dateHeight = TGMessage.getDateHeight(manager.useBubbles());
      int topOffset = getTopOffset();
      // TGMessage lastMessage = null;

      int lastIndex = parent.getChildCount() - 1;
      while (lastIndex >= 0) {
        View lastView = parent.getChildAt(lastIndex);
        if (lastView.getTop() + lastView.getMeasuredHeight() > topOffset) {
          break;
        }
        lastIndex--;
      }

      // this will iterate over every visible view
      for (int i = 0; i <= lastIndex; i++) {
        // get the view
        final View view = parent.getChildAt(i);
        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();

        // get the position
        final int position = params.getViewAdapterPosition();

        // and finally draw the separator
        if (position < state.getItemCount()) {
          final TGMessage msg = adapter.getMessage(position);
          if (msg != null) {
            int viewTop = view.getTop();
            if (msg.hasDate()) {
              viewTop += msg.getDrawDateY();
            }
            int top = viewTop;
            if (top < topOffset) {
              if (hasDrawn) {
                int maxY = lastTop - dateHeight;
                if (lastMessage != null) {
                  maxY -= lastMessage.getDrawDateY();
                }
                top = Math.min(maxY, topOffset);
              } else {
                top = topOffset;
              }
            }
            // (user 2026-10-23 «если рядом два дня, предыдущий мелькает»): the stuck date pushed up by the next day's
            // goes under the pinned capsule and stayed peeking from under it — it fades out as it's pushed, gone halfway
            float tgx101Pushed = top < topOffset ? MathUtils.clamp(1f - (topOffset - top) / (dateHeight * .5f)) : 1f;
            boolean isLast = i == lastIndex;
            int checkTop = viewTop - topOffset;
            float detachFactor = !isLast || checkTop >= 0 ? 0f : MathUtils.clamp((float) -checkTop / (float) dateHeight);
            float alpha = isLast ? scrollFactor : 1f;
            float drawAlpha = detachFactor == 1f ? alpha : 1f;
            if (top != viewTop || isLast) { // the stuck one: hidden while a jump slides by, and while it's pushed away
              alpha *= tgx101StuckDateAlpha * tgx101Pushed;
              drawAlpha *= tgx101StuckDateAlpha * tgx101Pushed;
            }
            if (msg.drawDate(c, centerX, top, detachFactor, drawAlpha)) {
              tgx101Hit(msg, top, drawAlpha);
              hasDrawn = true;
              lastTop = top;
              lastMessage = msg;
              lastAlpha = drawAlpha;
              continue;
            }
            if (isLast) {
              Object tag = msg.getTag();
              if (tag != null) {
                TGMessage tagMsg = (TGMessage) tag;
                if (!tagMsg.isDestroyed() && tagMsg.drawDate(c, centerX, top, 1f, alpha)) {
                  tgx101Hit(tagMsg, top, alpha);
                  hasDrawn = true;
                  lastTop = top;
                  lastMessage = tagMsg;
                  lastAlpha = alpha;
                  continue;
                }
                msg.setTag(null);
              }
              for (int j = position + 1; j < adapter.getMessageCount(); j++) {
                TGMessage olderMessage = adapter.getMessage(j);
                if (olderMessage != null && olderMessage.drawDate(c, centerX, top, 1f, alpha)) {
                  tgx101Hit(olderMessage, top, alpha);
                  msg.setTag(olderMessage);
                  hasDrawn = true;
                  lastTop = top;
                  lastMessage = msg;
                  lastAlpha = alpha;
                  break;
                }
              }
            }
          }
        }
      }
    }
  }
}
