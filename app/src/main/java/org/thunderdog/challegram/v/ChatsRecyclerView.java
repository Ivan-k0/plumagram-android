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
 * File created on 26/04/2015 at 12:52
 */
package org.thunderdog.challegram.v;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.component.dialogs.ChatsAdapter;
import org.thunderdog.challegram.helper.LiveLocationHelper;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ChatsController;

import me.vkryl.android.util.ClickHelper;

public class ChatsRecyclerView extends CustomRecyclerView implements ClickHelper.Delegate {
  private static final int PRELOAD_SIZE = 15;

  private int initialLoadCount;
  private int loadCount;

  private ChatsController controller;
  private ChatsAdapter adapter;
  private LinearLayoutManager manager;

  public ChatsRecyclerView (Context context) {
    super(context);
    init(context);
  }

  public ChatsRecyclerView (Context context, AttributeSet set) {
    super(context, set);
    init(context);
  }

  public ChatsRecyclerView (Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  private LoadMoreCallback loadCallback;

  public interface LoadMoreCallback {
    boolean ableToLoadMore ();
    void requestLoadMore ();
  }

  private void init (Context context) {
    initialLoadCount = Screen.calculateLoadingItems(Screen.dp(72f), 5) + 5;
    loadCount = Screen.calculateLoadingItems(Screen.dp(72f), 25);

    // setItemAnimator(null);

    setLayoutManager(manager = new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    addOnScrollListener(new OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (dy > 0) {
          if (controller != null && controller.isInForceTouchMode() && !isVerticalScrollBarEnabled()) {
            setVerticalScrollBarEnabled(true);
            controller.onInteractedWithContent();
          }
          if (loadCallback != null && loadCallback.ableToLoadMore() && manager.findLastVisibleItemPosition() + PRELOAD_SIZE >= adapter.getItemCount()) {
            loadCallback.requestLoadMore();
          }
        }
      }
    });
  }

  private final ClickHelper helper = new ClickHelper(this);

  // Pulling down once more when the list is already at the top opens search.
  // The gesture has to start at the top, so the swipe that brings the list
  // there doesn't count. Long presses (pinned chat drag, previews) and
  // mostly-horizontal swipes (folder switching) are ignored.
  // TGx101: a circle with a magnifier slides down from the top and its ring fills while pulling; at the
  // threshold it turns blue with one short vibration. Search opens only when the finger is released past
  // the threshold; pulling back or releasing earlier cancels.
  private static final float PULL_THRESHOLD_DP = 96f, PULL_SLOP_DP = 10f;
  private boolean pullToSearchTracking, pullToSearchActive, pullToSearchReady, tgx101PullConsumed;
  private float pullToSearchStartX, pullToSearchStartY, pullDistance;
  private android.animation.ValueAnimator pullReturnAnimator;
  private final android.graphics.Paint pullPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  private android.graphics.drawable.Drawable pullIcon;

  @Override
  public boolean dispatchTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        pullToSearchTracking = controller != null && controller.canOpenSearchByPull() && getScrollState() == SCROLL_STATE_IDLE && !canScrollVertically(-1);
        pullToSearchActive = pullToSearchReady = false;
        pullToSearchStartX = e.getX();
        pullToSearchStartY = e.getY();
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (tgx101PullConsumed) return true;
        if (pullToSearchActive) {
          setPullDistance(Math.max(0f, e.getY() - pullToSearchStartY));
          return true; // the list stays still while pulling
        }
        if (!pullToSearchTracking)
          break;
        if (e.getPointerCount() > 1 || e.getEventTime() - e.getDownTime() > ViewConfiguration.getLongPressTimeout()) {
          pullToSearchTracking = false;
          break;
        }
        float dx = Math.abs(e.getX() - pullToSearchStartX);
        float dy = e.getY() - pullToSearchStartY;
        if (dy < -Screen.dp(8f) || dx > Screen.dp(24f)) {
          pullToSearchTracking = false;
        } else if (dy >= Screen.dp(PULL_SLOP_DP) && dy > dx * 2f && controller != null && controller.tgx101StoriesFolded()) {
          // TGx101: the first pull unfolds the stories, the next one is search (like the official app)
          pullToSearchTracking = false;
          tgx101PullConsumed = true;
          MotionEvent cancel = MotionEvent.obtain(e);
          cancel.setAction(MotionEvent.ACTION_CANCEL);
          super.dispatchTouchEvent(cancel);
          cancel.recycle();
          if (org.thunderdog.challegram.unsorted.Settings.instance().tgx101Haptics()) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
          controller.tgx101SetStripExpanded(true, true);
          return true;
        } else if (dy >= Screen.dp(PULL_SLOP_DP) && dy > dx * 2f) {
          // the pull starts: the list doesn't get this gesture any more (no tap, no long press)
          pullToSearchTracking = false;
          pullToSearchActive = true;
          if (pullReturnAnimator != null) pullReturnAnimator.cancel();
          MotionEvent cancel = MotionEvent.obtain(e);
          cancel.setAction(MotionEvent.ACTION_CANCEL);
          super.dispatchTouchEvent(cancel);
          cancel.recycle();
          setPullDistance(dy);
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        pullToSearchTracking = false;
        if (tgx101PullConsumed) {
          tgx101PullConsumed = false;
          return true;
        }
        if (pullToSearchActive) {
          pullToSearchActive = false;
          boolean open = e.getActionMasked() == MotionEvent.ACTION_UP && pullToSearchReady && controller != null && controller.canOpenSearchByPull();
          animatePullBack();
          if (open) {
            controller.openSearchByPull();
          }
          return true;
        }
        break;
      }
    }
    return super.dispatchTouchEvent(e);
  }

  private void setPullDistance (float distance) {
    pullDistance = distance;
    boolean ready = distance >= Screen.dp(PULL_THRESHOLD_DP);
    if (ready != pullToSearchReady) {
      pullToSearchReady = ready;
      if (ready && org.thunderdog.challegram.unsorted.Settings.instance().tgx101Haptics()) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
      }
    }
    invalidate();
  }

  private void animatePullBack () {
    if (pullReturnAnimator != null) pullReturnAnimator.cancel();
    if (pullDistance <= 0f) return;
    pullReturnAnimator = android.animation.ValueAnimator.ofFloat(pullDistance, 0f);
    pullReturnAnimator.setDuration(200);
    pullReturnAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
    pullReturnAnimator.addUpdateListener(a -> {
      pullDistance = (float) a.getAnimatedValue();
      invalidate();
    });
    pullReturnAnimator.start();
  }

  @Override
  public void draw (@NonNull android.graphics.Canvas c) {
    super.draw(c);
    if (pullDistance <= 0f) return;
    float threshold = Screen.dp(PULL_THRESHOLD_DP);
    float progress = Math.min(1f, pullDistance / threshold);
    float radius = Screen.dp(20f);
    float cx = getWidth() / 2f;
    // slides down from above the top edge, slower than the finger
    // below the stories strip (it covered the circle — «пропала анимация поиска», user 2026-10-04)
    float cy = getPaddingTop() - radius + Math.min(pullDistance, threshold * 1.15f) * .62f;
    float alpha = Math.min(1f, pullDistance / Screen.dp(24f));
    boolean ready = pullToSearchReady && pullToSearchActive || pullDistance >= threshold;
    int accent = org.thunderdog.challegram.theme.Theme.getColor(org.thunderdog.challegram.theme.ColorId.fillingPositive);
    int filling = org.thunderdog.challegram.theme.Theme.getColor(org.thunderdog.challegram.theme.ColorId.filling);
    // shadow + circle
    pullPaint.setStyle(android.graphics.Paint.Style.FILL);
    pullPaint.setColor(me.vkryl.core.ColorUtils.alphaColor(.18f * alpha, 0xff000000));
    c.drawCircle(cx, cy + Screen.dp(1.5f), radius + Screen.dp(1f), pullPaint);
    pullPaint.setColor(me.vkryl.core.ColorUtils.alphaColor(alpha, ready ? accent : filling));
    c.drawCircle(cx, cy, radius, pullPaint);
    // progress ring
    if (!ready) {
      float ringRadius = radius - Screen.dp(4f);
      pullPaint.setStyle(android.graphics.Paint.Style.STROKE);
      pullPaint.setStrokeWidth(Screen.dp(2.5f));
      pullPaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
      pullPaint.setColor(me.vkryl.core.ColorUtils.alphaColor(alpha * .25f, accent));
      c.drawCircle(cx, cy, ringRadius, pullPaint);
      pullPaint.setColor(me.vkryl.core.ColorUtils.alphaColor(alpha, accent));
      c.drawArc(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius, -90f, 360f * progress, false, pullPaint);
    }
    // magnifier
    if (pullIcon == null) {
      pullIcon = org.thunderdog.challegram.tool.Drawables.get(getResources(), org.thunderdog.challegram.R.drawable.baseline_search_24);
      if (pullIcon != null) pullIcon = pullIcon.mutate();
    }
    if (pullIcon != null) {
      int size = Screen.dp(ready ? 22f : 18f);
      pullIcon.setColorFilter(org.thunderdog.challegram.tool.Paints.getColorFilter(ready ? 0xffffffff : accent));
      pullIcon.setAlpha((int) (255 * alpha));
      pullIcon.setBounds((int) (cx - size / 2f), (int) (cy - size / 2f), (int) (cx + size / 2f), (int) (cy + size / 2f));
      pullIcon.draw(c);
    }
  }

  // Touching the list while it's still flinging makes RecyclerView call
  // requestDisallowInterceptTouchEvent(true) on its parent, so the chat folders
  // pager can't pick up a horizontal swipe until scrolling fully stops.
  // Stop the fling ourselves first so RecyclerView stays idle and the pager
  // still gets to intercept; keep the gesture so the tap doesn't open a chat.
  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN && getScrollState() == SCROLL_STATE_SETTLING) {
      stopScroll();
      super.onInterceptTouchEvent(e);
      return true;
    }
    return super.onInterceptTouchEvent(e);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    boolean res = super.onTouchEvent(e);
    if (controller.needLiveLocationClick()) {
      helper.onTouchEvent(this, e);
    }
    return res;
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    int i = controller.getLiveLocationPosition();
    View boundView = getLayoutManager().findViewByPosition(i);
    if (boundView != null) {
      int decoratedTop = getLayoutManager().getDecoratedTop(boundView);
      return y >= decoratedTop && y < decoratedTop + LiveLocationHelper.height();
    }
    return false;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    controller.onLiveLocationClick(x, y);
  }

  public ChatsAdapter initWithController (ChatsController controller, LoadMoreCallback callback) {
    this.controller = controller;
    this.loadCallback = callback;
    this.adapter = new ChatsAdapter(controller, manager);
    setAdapter(adapter);
    return adapter;
  }

  public void setTotalRes (int totalRes) {
    this.adapter.setTotalRes(totalRes);
  }

  public int getInitialLoadCount () {
    return initialLoadCount;
  }

  public int getLoadCount () {
    return loadCount;
  }

  public void updateMessageInteractionInfo (long chatId, long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
    int updated = adapter.updateMessageInteractionInfo(chatId, messageId, interactionInfo);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateMessageContent (long chatId, long messageId, TdApi.MessageContent newContent) {
    int updated = adapter.updateMessageContent(chatId, messageId, newContent);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void refreshLastMessage (long chatId, long messageId, boolean needRebuild) {
    int updated = adapter.refreshLastMessage(chatId, messageId, needRebuild);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateMessagesDeleted (long chatId, long[] messageIds) {
    int updated = adapter.updateMessagesDeleted(chatId, messageIds);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatReadInbox (long chatId, final long lastReadInboxMessageId, final int unreadCount) {
    int updated = adapter.updateChatReadInbox(chatId, lastReadInboxMessageId, unreadCount);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatUnreadReactionCount (long chatId, int unreadReactionCount) {
    int updated = adapter.updateChatUnreadReactionCount(chatId, unreadReactionCount);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatUnreadMentionCount (long chatId, int unreadMentionCount) {
    int updated = adapter.updateChatUnreadMentionCount(chatId, unreadMentionCount);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatHasScheduledMessages (long chatId, boolean hasScheduledMessages) {
    int updated = adapter.updateChatHasScheduledMessages(chatId, hasScheduledMessages);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateRelativeDate () {
    adapter.updateRelativeDate();
    invalidateAll();
  }
  public void updateMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    int updated = adapter.updateMessageSendSucceeded(message, oldMessageId);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatDraftMessage (long chatId, TdApi.DraftMessage draftMessage) {
    int updated = adapter.updateChatDraftMessage(chatId, draftMessage);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatReadOutbox (long chatId, final long lastReadOutboxMessageId) {
    int updated = adapter.updateChatReadOutbox(chatId, lastReadOutboxMessageId);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateUser (TdApi.User user) {
    adapter.updateUser(this, user);
  }

  public void updateUserStatus (long userId) {
    int startIndex = 0, updated;
    while (true) {
      updated = adapter.updateUserStatus(userId, startIndex);
      if (updated == -1)
        break;
      View view = manager.findViewByPosition(updated);
      if (view instanceof ChatView && ((ChatView) view).getChatId() == adapter.getChatByItemPosition(updated).getChatId()) {
        // ((ChatView) view).updateOnline();
        view.invalidate();
      } else {
        adapter.notifyItemChanged(updated);
      }
      startIndex = adapter.getChatIndexByItemPosition(updated) + 1;
    }
  }

  public void updateChatTitle (long chatId, String title) {
    int updated = adapter.updateChatTitle(chatId, title);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatPermissionsChanged (long chatId, TdApi.ChatPermissions permissions) {
    int updated = adapter.updateChatPermissions(chatId, permissions);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatClientData (long chatId, String clientData) {
    int updated = adapter.updateChatClientData(chatId, clientData);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatMarkedAsUnread (long chatId, boolean isMarkedAsUnread) {
    int updated = adapter.updateChatMarkedAsUnread(chatId, isMarkedAsUnread);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatTopMessage (long chatId, TdApi.Message topMessage) {
    int updated = adapter.updateChatTopMessage(chatId, topMessage);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateArchive (int updateReason) {
    int updated = adapter.updateArchive(updateReason);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatSelectionState (long chatId, boolean isSelected) {
    int index = adapter.findChatItemPosition(chatId);
    if (index != -1) {
      View view = getLayoutManager().findViewByPosition(index);
      if (view instanceof ChatView && ((ChatView) view).getChatId() == chatId) {
        ((ChatView) view).setIsSelected(isSelected, true);
      } else {
        adapter.notifyItemChanged(index);
      }
    }
  }

  public void updateChatPosition (long chatId, TdApi.ChatPosition position, boolean orderChanged, boolean sourceChanged, boolean pinStateChanged) {
    if (sourceChanged) {
      int i = adapter.findChatItemPosition(chatId);
      if (i != -1) {
        invalidateViewAt(i);
      }
    }
  }

  public void processChatUpdate (int flags) {
    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    int viewTop;
    if (firstVisiblePosition != -1) {
      View view = manager.findViewByPosition(firstVisiblePosition);
      viewTop = view != null ? view.getTop() : 0;
    } else {
      viewTop = 0;
    }
    if ((flags & ChatsAdapter.ORDER_REMAIN_SCROLL) != 0 && firstVisiblePosition != -1) {
      manager.scrollToPositionWithOffset(firstVisiblePosition, viewTop);
    }
    if ((flags & ChatsAdapter.ORDER_INVALIDATE_DECORATIONS) != 0) {
      adapter.invalidateAttachedItemDecorations();
    }
  }

  public void updateChatPhoto (long chatId, TdApi.ChatPhotoInfo photo) {
    int updated = adapter.updateChatPhoto(chatId, photo);
    if (updated != -1) {
      View view = manager.findViewByPosition(updated);
      if (view instanceof ChatView) {
        ((ChatView) view).invalidateAvatarReceiver();
      } else {
        adapter.notifyItemChanged(updated);
      }
    }
  }

  public void updateNotificationSettings (long chatId, final TdApi.ChatNotificationSettings settings) {
    int updated = adapter.updateChatSettings(chatId, settings);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateNotificationSettings (final TdApi.NotificationSettingsScope scope, final TdApi.ScopeNotificationSettings settings) {
    adapter.updateNotificationSettings(scope, settings);
  }

  public void updateSecretChat (TdApi.SecretChat secretChat) {
    int updated = adapter.updateSecretChat(secretChat);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateLocale (boolean forceText) {
    adapter.updateLocale(forceText);
    invalidateAll();
  }
}
