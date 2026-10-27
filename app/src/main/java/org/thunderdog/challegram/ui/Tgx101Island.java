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
package org.thunderdog.challegram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.HashMap;
import java.util.Map;

import me.vkryl.android.AnimatorUtils;

/**
 * TGx101 (approved mockup «Уведомления», variant 2 «Капсула-остров», user 2026-10-27): a silent in-app notification — a
 * small dark capsule under the camera with the chat's photo and name. No sound, no vibration; it goes by itself in 4 s;
 * a tap opens it into a card with two lines of the message, a second tap opens the chat; a swipe up takes it away, two
 * swipes in a row from one chat keep that chat's capsules away for 5 minutes. Nothing for the chat that is open now.
 */
public final class Tgx101Island {
  private Tgx101Island () { }

  private static final long HIDE_MS = 4000, HIDE_EXPANDED_MS = 6000, SNOOZE_MS = 5 * 60 * 1000;

  private static final Map<Long, Long> snoozedUntil = new HashMap<>();
  private static long lastDismissedChatId;
  private static int dismissedInRow;

  /**
   * from the TDLib thread: a new message. (user 2026-10-27 «капсула приходит с задержкой, должно в тот же миг»): TDLib holds
   * its notification groups back while one is online (in case the message is read elsewhere) — the capsule, the sound
   * and the vibration go off the message itself, with the same rules: incoming, notifications of the chat on (or a
   * mention of me), not the open chat
   */
  public static void onUpdate (Tdlib tdlib, TdApi.Update update) {
    if (update.getConstructor() != TdApi.UpdateNewMessage.CONSTRUCTOR) return;
    final TdApi.Message message = ((TdApi.UpdateNewMessage) update).message;
    if (message == null || message.isOutgoing || message.sendingState != null || message.schedulingState != null) return;
    if (System.currentTimeMillis() / 1000L - message.date > 60) return; // not «now»
    final Settings settings = Settings.instance();
    if (settings.tgx101FgShow() == Settings.TGX101_FG_SYSTEM) return; // the system notification does it all
    final long chatId = message.chatId;
    if (tdlib.isSelfChat(chatId)) return;
    if (!tdlib.chatNotificationsEnabled(chatId) && !message.containsUnreadMention) return;
    UI.post(() -> {
      if (!passes(tdlib, chatId)) return;
      // the app's own sound / vibration — the shade gets nothing while the app is open
      if (settings.tgx101FgSound()) tdlib.notifications().tgx101PlayInAppSound();
      if (settings.tgx101FgVibrate()) vibrate();
      if (settings.tgx101FgShow() == Settings.TGX101_FG_CAPSULE) show(tdlib, chatId, message);
    });
  }

  /** the app on screen with this account, the chat not the open one, not quieted by two swipes */
  private static boolean passes (Tdlib tdlib, long chatId) {
    BaseActivity context = UI.getUiContext();
    if (context == null || !UI.isResumed() || context.isPasscodeShowing()) return false;
    Long until = snoozedUntil.get(chatId);
    if (until != null && until > SystemClock.uptimeMillis()) return false;
    ViewController<?> top = context.navigation() != null ? context.navigation().getCurrentStackItem() : null;
    if (top == null || top.tdlib() != tdlib) return false; // only the account on screen
    return !(top instanceof MessagesController && ((MessagesController) top).getChatId() == chatId);
  }

  private static void vibrate () {
    try {
      android.os.Vibrator vibrator = (android.os.Vibrator) org.thunderdog.challegram.unsorted.AppContext.get().getSystemService(Context.VIBRATOR_SERVICE);
      if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(40);
    } catch (Throwable ignored) { }
  }

  private static void show (Tdlib tdlib, long chatId, TdApi.Message message) {
    BaseActivity context = UI.getUiContext();
    if (context == null) return;
    String title = tdlib.chatTitle(chatId);
    String text;
    try {
      ContentPreview preview = ContentPreview.getNotificationPreview(tdlib, chatId, message, true);
      text = preview != null ? preview.buildText(false) : "";
    } catch (Throwable t) {
      text = "";
    }
    if (!tdlib.isUserChat(chatId) && !tdlib.isChannel(chatId) && message.senderId != null) {
      String sender = tdlib.senderName(message.senderId, true);
      if (!TextUtils.isEmpty(sender)) text = sender + ": " + text;
    }
    IslandView view = find(context);
    if (view == null) {
      view = new IslandView(context);
      view.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      context.addToRoot(view, true);
    }
    org.thunderdog.challegram.Tgx101Diag.mark("island: chat " + chatId);
    view.present(tdlib, chatId, title, text);
  }

  private static @Nullable IslandView find (BaseActivity context) {
    ViewGroup root = context.getRootView();
    if (root == null) return null;
    for (int i = root.getChildCount() - 1; i >= 0; i--) {
      if (root.getChildAt(i) instanceof IslandView) return (IslandView) root.getChildAt(i);
    }
    return null;
  }

  private static void onDismissedBySwipe (Tdlib tdlib, long chatId, String title) {
    if (chatId == lastDismissedChatId) {
      dismissedInRow++;
    } else {
      lastDismissedChatId = chatId;
      dismissedInRow = 1;
    }
    if (dismissedInRow >= 2) {
      snoozedUntil.put(chatId, SystemClock.uptimeMillis() + SNOOZE_MS);
      dismissedInRow = 0;
      lastDismissedChatId = 0;
      UI.showToast(Lang.getString(R.string.Tgx101IslandSnoozed, title), android.widget.Toast.LENGTH_SHORT);
    }
  }

  private static int Color_alpha (int color) {
    return color >>> 24;
  }

  private static final class IslandView extends View {
    private final AvatarReceiver avatar = new AvatarReceiver(this);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shadeRect = new RectF();
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private @Nullable Tdlib tdlib;
    private long chatId;
    private String title = "", text = "";
    private String line1 = "", line2 = "";
    private String shortTitle = "";

    private float shown; // 0..1
    private float expanded; // 0..1
    private float dragY;
    private @Nullable ValueAnimator showAnimator, expandAnimator, dragAnimator;

    IslandView (Context context) {
      super(context);
      titlePaint.setTypeface(Fonts.getRobotoMedium());
      textPaint.setTypeface(Fonts.getRobotoRegular());
      border.setStyle(Paint.Style.STROKE);
      border.setStrokeWidth(Math.max(1, Screen.dp(.5f)));
    }

    private final Runnable hide = () -> animateShown(false, null);

    void present (Tdlib tdlib, long chatId, String title, String text) {
      boolean same = this.tdlib == tdlib && this.chatId == chatId && shown > 0f;
      this.tdlib = tdlib;
      this.chatId = chatId;
      this.title = title != null ? title : "";
      this.text = text != null ? text.replace('\n', ' ') : "";
      if (!same) {
        avatar.requestChat(tdlib, chatId, AvatarReceiver.Options.NONE);
        setExpanded(0f);
        dragY = 0f;
      }
      layoutTexts();
      removeCallbacks(hide);
      postDelayed(hide, expanded > 0f ? HIDE_EXPANDED_MS : HIDE_MS);
      animateShown(true, null);
      invalidate();
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      avatar.attach();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      avatar.detach();
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      layoutTexts();
    }

    private float cardWidth () {
      return Math.min(getWidth() - Screen.dp(24f), Screen.dp(420f));
    }

    private void layoutTexts () {
      if (getWidth() == 0) return;
      titlePaint.setTextSize(Screen.dp(13f));
      shortTitle = TextUtils.ellipsize(title, titlePaint, getWidth() * .55f, TextUtils.TruncateAt.END).toString();
      textPaint.setTextSize(Screen.dp(13.5f));
      float textWidth = cardWidth() - Screen.dp(12f + 40f + 12f + 14f);
      int n = textPaint.breakText(text, true, textWidth, null);
      if (n >= text.length()) {
        line1 = text;
        line2 = "";
      } else {
        int cut = text.lastIndexOf(' ', n);
        if (cut <= n / 2) cut = n;
        line1 = text.substring(0, cut);
        line2 = TextUtils.ellipsize(text.substring(cut).trim(), textPaint, textWidth, TextUtils.TruncateAt.END).toString();
      }
    }

    private void computeRect () {
      titlePaint.setTextSize(Screen.dp(13f));
      float collapsedH = Screen.dp(34f);
      float collapsedW = Screen.dp(5f + 24f + 8f + 8f + 6f + 10f) + titlePaint.measureText(shortTitle);
      float expandedH = Screen.dp(12f + 18f + 4f + 17f + (line2.isEmpty() ? 0f : 17f) + 12f);
      float expandedW = cardWidth();
      float w = collapsedW + (expandedW - collapsedW) * expanded;
      float h = collapsedH + (expandedH - collapsedH) * expanded;
      float top = Screen.getStatusBarHeight() + Screen.dp(6f) + dragY;
      float cx = getWidth() / 2f;
      rect.set(cx - w / 2f, top, cx + w / 2f, top + h);
    }

    @Override
    protected void onDraw (Canvas c) {
      if (shown <= 0f) return;
      computeRect();
      int save = c.save();
      float scale = .7f + .3f * shown;
      c.scale(scale, scale, rect.centerX(), rect.top);
      int alpha = (int) (255 * Math.min(1f, shown * 1.4f));
      // (user 2026-10-27 «в цвет темы и мягче»): the theme's own card colour, a soft shadow under it and a hairline
      // edge instead of the black island
      float a = Math.min(1f, shown * 1.4f);
      float radius = Screen.dp(17f) + Screen.dp(7f) * expanded;
      fill.setColor(org.thunderdog.challegram.theme.Theme.fillingColor());
      fill.setAlpha((int) (255 * a));
      c.drawRoundRect(rect, radius, radius, fill);
      border.setColor(org.thunderdog.challegram.theme.Theme.separatorColor());
      border.setAlpha((int) (Color_alpha(org.thunderdog.challegram.theme.Theme.separatorColor()) * a));
      c.drawRoundRect(rect, radius, radius, border);
      titlePaint.setColor(org.thunderdog.challegram.theme.Theme.textAccentColor());
      textPaint.setColor(org.thunderdog.challegram.theme.Theme.textDecentColor());

      // the photo: 24 dp in the capsule, 40 dp in the card
      float ar = Screen.dp(12f) + Screen.dp(8f) * expanded;
      float ax = rect.left + Screen.dp(5f) + ar + Screen.dp(7f) * expanded;
      float ay = rect.top + Screen.dp(17f) + (Screen.dp(12f) + ar - Screen.dp(17f)) * expanded;
      avatar.setBounds((int) (ax - ar), (int) (ay - ar), (int) (ax + ar), (int) (ay + ar));
      avatar.setPaintAlpha(alpha / 255f);
      if (avatar.needPlaceholder()) avatar.drawPlaceholder(c);
      avatar.draw(c);
      avatar.restorePaintAlpha();

      // the name: the capsule's line, then the card's first line
      titlePaint.setAlpha(alpha);
      float tx = ax + ar + Screen.dp(8f) + Screen.dp(4f) * expanded;
      float ty = rect.top + Screen.dp(21.5f) + (Screen.dp(12f + 13f) - Screen.dp(21.5f)) * expanded;
      c.drawText(expanded > .5f ? TextUtils.ellipsize(title, titlePaint, rect.right - tx - Screen.dp(14f), TextUtils.TruncateAt.END).toString() : shortTitle, tx, ty, titlePaint);

      // the dot of the capsule
      if (expanded < 1f) {
        dot.setColor(org.thunderdog.challegram.theme.Theme.getColor(org.thunderdog.challegram.theme.ColorId.textLink));
        dot.setAlpha((int) (alpha * (1f - expanded)));
        c.drawCircle(rect.right - Screen.dp(13f), rect.top + Screen.dp(17f), Screen.dp(3.5f), dot);
      }

      // two lines of the message in the card
      if (expanded > 0f) {
        textPaint.setAlpha((int) (alpha * Math.max(0f, (expanded - .4f) / .6f)));
        float ly = rect.top + Screen.dp(12f + 18f + 4f + 12f);
        c.drawText(line1, tx, ly, textPaint);
        if (!line2.isEmpty()) c.drawText(line2, tx, ly + Screen.dp(17f), textPaint);
      }
      c.restoreToCount(save);
    }

    // touches: only on the capsule / card itself, the rest goes through

    private float downX, downY, downDrag;
    private boolean tracking, moved;

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          if (shown < .9f) return false;
          computeRect();
          RectF hit = new RectF(rect);
          hit.inset(-Screen.dp(8f), -Screen.dp(8f));
          if (!hit.contains(e.getX(), e.getY())) return false;
          tracking = true;
          moved = false;
          downX = e.getX();
          downY = e.getY();
          downDrag = dragY;
          removeCallbacks(hide);
          if (dragAnimator != null) dragAnimator.cancel();
          return true;
        }
        case MotionEvent.ACTION_MOVE: {
          if (!tracking) return false;
          float dy = e.getY() - downY;
          if (!moved && Math.hypot(e.getX() - downX, dy) > Screen.getTouchSlop()) moved = true;
          if (moved) {
            dragY = Math.min(Screen.dp(12f), downDrag + dy) * (dy > 0 ? .3f : 1f);
            invalidate();
          }
          return true;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          if (!tracking) return false;
          tracking = false;
          if (e.getActionMasked() == MotionEvent.ACTION_UP && !moved) {
            onTap();
          } else if (dragY < -Screen.dp(20f)) {
            // swiped up: away; twice in a row from one chat — that chat quiet for 5 minutes
            if (tdlib != null) onDismissedBySwipe(tdlib, chatId, title);
            animateShown(false, null);
          } else {
            settleDrag();
            postDelayed(hide, expanded > 0f ? HIDE_EXPANDED_MS : HIDE_MS);
          }
          return true;
        }
      }
      return tracking;
    }

    private void onTap () {
      if (expanded < 1f) {
        animateExpanded();
        postDelayed(hide, HIDE_EXPANDED_MS);
      } else if (tdlib != null) {
        final Tdlib t = tdlib;
        final long id = chatId;
        BaseActivity context = UI.getUiContext();
        ViewController<?> top = context != null && context.navigation() != null ? context.navigation().getCurrentStackItem() : null;
        lastDismissedChatId = 0;
        dismissedInRow = 0;
        animateShown(false, null);
        if (top != null) t.ui().openChat(top, id, null);
      }
    }

    private void setExpanded (float value) {
      if (expandAnimator != null) expandAnimator.cancel();
      expanded = value;
    }

    private void animateExpanded () {
      if (expandAnimator != null) expandAnimator.cancel();
      ValueAnimator a = ValueAnimator.ofFloat(expanded, 1f);
      a.setDuration(260);
      a.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      a.addUpdateListener(an -> {
        expanded = (float) an.getAnimatedValue();
        invalidate();
      });
      expandAnimator = a;
      a.start();
    }

    private void settleDrag () {
      if (dragAnimator != null) dragAnimator.cancel();
      ValueAnimator a = ValueAnimator.ofFloat(dragY, 0f);
      a.setDuration(180);
      a.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      a.addUpdateListener(an -> {
        dragY = (float) an.getAnimatedValue();
        invalidate();
      });
      dragAnimator = a;
      a.start();
    }

    private void animateShown (boolean show, @Nullable Runnable after) {
      float to = show ? 1f : 0f;
      if (showAnimator != null) showAnimator.cancel();
      if (shown == to) {
        if (after != null) after.run();
        return;
      }
      ValueAnimator a = ValueAnimator.ofFloat(shown, to);
      a.setDuration(show ? 240 : 200);
      a.setInterpolator(show ? AnimatorUtils.DECELERATE_INTERPOLATOR : AnimatorUtils.ACCELERATE_INTERPOLATOR);
      a.addUpdateListener(an -> {
        shown = (float) an.getAnimatedValue();
        if (!show) dragY -= Screen.dp(1.5f); // it leaves upwards
        invalidate();
      });
      a.addListener(new android.animation.AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (android.animation.Animator animation) {
          if (showAnimator != animation) return;
          if (!show) {
            dragY = 0f;
            setExpanded(0f);
          }
          if (after != null) after.run();
        }
      });
      showAnimator = a;
      a.start();
    }
  }
}
