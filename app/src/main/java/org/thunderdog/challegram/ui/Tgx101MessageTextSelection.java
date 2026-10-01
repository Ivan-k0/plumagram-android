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

import android.os.SystemClock;
import android.text.Layout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.Tgx101SelectionBar;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageText;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;
import java.util.List;

import tgx.td.Td;

/**
 * TGx101: text selection right inside a message bubble (double long press), like the official app.
 * A selectable copy of the message text is laid exactly over the bubble's text (same font, size and bubble
 * colour), the word under the finger gets selected, and the selection bar (bubble style) follows the top handle:
 * Quote, Copy, Select all, Translate…, plus Edit on own messages. No keyboard. A tap aside or Back closes it.
 */
public final class Tgx101MessageTextSelection {
  private Tgx101MessageTextSelection () { }

  private static void forwardToList (androidx.recyclerview.widget.RecyclerView list, long downTime, long eventTime, int action, float rawX, float rawY) {
    int[] location = new int[2];
    list.getLocationOnScreen(location);
    MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, rawX - location[0], rawY - location[1], 0);
    list.dispatchTouchEvent(event);
    event.recycle();
  }

  private static boolean fail (String reason) {
    org.thunderdog.challegram.Tgx101Diag.mark("select: no in-bubble selection — " + reason);
    return false;
  }

  /** Returns false when this message can't be selected in place (then the caller uses the quote window) */
  public static boolean show (MessagesController controller, Tdlib tdlib, MessageView view, TGMessage msg, float touchX, float touchY, Runnable onClose) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return fail("old Android");
    if (!(msg instanceof TGMessageText)) return fail("not a text message: " + msg.getClass().getSimpleName());
    int[] frame = ((TGMessageText) msg).tgx101TextFrame();
    TdApi.Message message = msg.getNewestMessage();
    TdApi.FormattedText formatted = Td.textOrCaption(message.content);
    if (frame == null) return fail("link preview above the text");
    if (formatted == null || formatted.text.isEmpty()) return fail("no text");
    if (msg.isTranslated()) return fail("translated");
    org.thunderdog.challegram.Tgx101Diag.mark("select: in-bubble selection at " + Math.round(touchX) + "," + Math.round(touchY));

    org.thunderdog.challegram.BaseActivity activity = controller.context();
    final PopupLayout popup = new PopupLayout(activity);
    popup.setNeedRootInsets();

    FrameLayout wrap = new FrameLayout(activity);
    wrap.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    // Around the text: a swipe scrolls the chat (forwarded to the message list), a tap is a safety tap —
    // the first one keeps the selection, a second one within 2 s closes it
    final long[] lastOutsideTap = {0};
    final androidx.recyclerview.widget.RecyclerView list = controller.getMessagesView();
    final int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
    final float[] downRaw = new float[2];
    final boolean[] forwarding = {false};
    final long[] downTime = {0};
    wrap.setOnTouchListener((v, e) -> {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downRaw[0] = e.getRawX();
          downRaw[1] = e.getRawY();
          downTime[0] = e.getDownTime();
          forwarding[0] = false;
          return true;
        case MotionEvent.ACTION_MOVE:
          if (!forwarding[0] && list != null && Math.hypot(e.getRawX() - downRaw[0], e.getRawY() - downRaw[1]) > slop) {
            forwarding[0] = true;
            forwardToList(list, downTime[0], downTime[0], MotionEvent.ACTION_DOWN, downRaw[0], downRaw[1]);
          }
          if (forwarding[0]) forwardToList(list, downTime[0], e.getEventTime(), MotionEvent.ACTION_MOVE, e.getRawX(), e.getRawY());
          return true;
        case MotionEvent.ACTION_UP:
          if (forwarding[0]) {
            forwardToList(list, downTime[0], e.getEventTime(), MotionEvent.ACTION_UP, e.getRawX(), e.getRawY());
            forwarding[0] = false;
          } else {
            long now = SystemClock.uptimeMillis();
            if (now - lastOutsideTap[0] < 2000) {
              popup.hideWindow(true);
            } else {
              lastOutsideTap[0] = now;
              org.thunderdog.challegram.Tgx101Diag.mark("select: first tap aside — kept (a second one closes)");
            }
          }
          return true;
        case MotionEvent.ACTION_CANCEL:
          if (forwarding[0]) forwardToList(list, downTime[0], e.getEventTime(), MotionEvent.ACTION_CANCEL, e.getRawX(), e.getRawY());
          forwarding[0] = false;
          return true;
      }
      return true;
    });

    TextView text = new TextView(activity) {
      private float downX, downY;
      private boolean seenDown;

      @Override
      public boolean onTouchEvent (MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
          downX = e.getX();
          downY = e.getY();
          seenDown = true;
        } else if (!seenDown) {
          // the finger of the long press that opened this layer is lifted: its stray UP would read as a tap and drop the selection
          return true;
        }
        boolean result = super.onTouchEvent(e);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
          seenDown = false;
        }
        return result;
      }

      @Override
      public boolean performLongClick () {
        // a long press on the selected text would start dragging it (a floating copy of the text) — not needed here
        int start = Math.min(getSelectionStart(), getSelectionEnd()), end = Math.max(getSelectionStart(), getSelectionEnd());
        if (hasSelection() && !"allowLongClick".equals(getTag())) {
          int offset = getOffsetForPosition(downX, downY);
          if (offset >= start && offset <= end) return true;
        }
        return super.performLongClick();
      }
    };
    CharSequence styled = TD.toCharSequence(formatted);
    text.setText(styled != null && styled.toString().equals(formatted.text) ? styled : formatted.text);
    TextPaint reference = TGMessage.getTextStyleProvider().preparePaint(new TextPaint());
    text.setTextSize(TypedValue.COMPLEX_UNIT_PX, reference.getTextSize());
    text.setTypeface(Fonts.getRobotoRegular());
    text.setTextColor(msg.getTextColor());
    text.setHighlightColor(msg.getTextLinkHighlightColor());
    text.setIncludeFontPadding(false);
    if (frame[4] > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
      text.setLineHeight(frame[4]); // same line spacing as the bubble, so the lines sit over the original ones
    }
    if (frame[3] > 0) {
      text.setMinHeight(frame[3]); // never shorter than the original text — nothing of it shows below
    }
    text.setPadding(0, 0, 0, 0);
    text.setBackgroundColor(Theme.getColor(msg.useBubbles() ? (msg.isOutgoingBubble() ? ColorId.bubbleOut_background : ColorId.bubbleIn_background) : ColorId.chatBackground));
    text.setTextIsSelectable(true);

    // exactly over the bubble's text
    int[] viewLocation = new int[2];
    view.getLocationOnScreen(viewLocation);
    int[] rootLocation = new int[2];
    activity.getWindow().getDecorView().getLocationOnScreen(rootLocation);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(frame[2] + org.thunderdog.challegram.tool.Screen.dp(3f), ViewGroup.LayoutParams.WRAP_CONTENT);
    params.leftMargin = viewLocation[0] - rootLocation[0] + frame[0];
    params.topMargin = viewLocation[1] - rootLocation[1] + frame[1];
    wrap.addView(text, params);

    List<Tgx101SelectionBar.Action> actions = new ArrayList<>();
    actions.add(new Tgx101SelectionBar.Action(Lang.getString(R.string.Tgx101EditorQuote), () -> {
      int start = text.getSelectionStart(), end = text.getSelectionEnd();
      popup.hideWindow(true);
      SelectTextForQuoteDialog.onReplyRequested(controller, tdlib, msg, formatted, Math.min(start, end), Math.max(start, end));
    }));
    if (Tgx101TextEditor.canEdit(message) && msg.canEditText()) {
      actions.add(new Tgx101SelectionBar.Action(Lang.getString(R.string.Tgx101EditorMenu), () -> {
        popup.hideWindow(true);
        Tgx101TextEditor.showEdit(controller, tdlib, message);
      }));
    }
    final Tgx101SelectionBar bar = new Tgx101SelectionBar(text, false, actions);
    text.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
      @Override public boolean onCreateActionMode (ActionMode mode, Menu menu) { return true; }
      @Override public boolean onPrepareActionMode (ActionMode mode, Menu menu) {
        bar.update(menu);
        return true;
      }
      @Override public boolean onActionItemClicked (ActionMode mode, MenuItem item) { return false; }
      @Override public void onDestroyActionMode (ActionMode mode) {
        bar.dismiss();
        // the system ends the selection mode e.g. when a handle is dragged past the text: keep the layer, and if the
        // selection is still there bring the handles and the bar back
        boolean hasSelection = text.hasSelection();
        org.thunderdog.challegram.Tgx101Diag.mark("select: action mode ended, selection kept=" + hasSelection);
        if (hasSelection && !popup.isWindowHidden()) {
          text.post(() -> {
            if (popup.isWindowHidden() || !text.hasSelection()) return;
            int start = text.getSelectionStart(), end = text.getSelectionEnd();
            // re-enter the selection mode on the same range
            android.text.Selection.removeSelection((android.text.Spannable) text.getText());
            text.setTag("allowLongClick");
            Layout layout = text.getLayout();
            if (layout != null) {
              int line = layout.getLineForOffset(start);
              float lx = layout.getPrimaryHorizontal(start) + 1f, ly = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f;
              long now = SystemClock.uptimeMillis();
              MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, lx, ly, 0);
              text.dispatchTouchEvent(down);
              down.recycle();
              text.performLongClick();
              MotionEvent up = MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, lx, ly, 0);
              text.dispatchTouchEvent(up);
              up.recycle();
              if (text.getText() instanceof android.text.Spannable) {
                android.text.Selection.setSelection((android.text.Spannable) text.getText(), start, end);
              }
            }
            text.setTag(null);
          });
        }
      }
    });
    final int[] lastSelection = {-1, -1};
    text.getViewTreeObserver().addOnPreDrawListener(() -> {
      int s = text.getSelectionStart(), e = text.getSelectionEnd();
      if (bar.isShowing() && (s != lastSelection[0] || e != lastSelection[1])) {
        lastSelection[0] = s;
        lastSelection[1] = e;
        bar.onSelectionChanging(); // hidden while a handle is dragged (the magnifier), back above the selection after
      }
      return true;
    });
    final int[] baseTop = {params.topMargin};
    final int[] viewTopOnScreen = {viewLocation[1]};
    final androidx.recyclerview.widget.RecyclerView.OnScrollListener follow = new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
        if (popup.isWindowHidden()) return;
        View current = msg.findCurrentView();
        if (current == null || !current.isAttachedToWindow()) {
          popup.hideWindow(true); // the message scrolled away
          return;
        }
        int[] location = new int[2];
        current.getLocationOnScreen(location);
        text.setTranslationY(location[1] - viewTopOnScreen[0]);
        if (bar.isShowing()) bar.reposition();
      }
    };
    if (list != null) list.addOnScrollListener(follow);
    popup.setDismissListener(p -> {
      if (list != null) list.removeOnScrollListener(follow);
      bar.dismiss();
      if (onClose != null) onClose.run();
    });
    popup.showNonAnimatedView(wrap);

    // select the word under the finger: the same long press the system expects — once the text is laid out
    final float x = touchX - frame[0], y = touchY - frame[1];
    final boolean[] started = {false};
    text.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout () {
        if (started[0] || text.getWidth() == 0 || text.getLayout() == null) return;
        started[0] = true;
        text.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        final float lx = Math.max(1f, Math.min(text.getWidth() - 1f, x));
        final float ly = Math.max(1f, Math.min(text.getHeight() - 1f, y));
        // a touch at the word (the editor remembers where), then the editor's own "long click": selects the word under
        // the finger with handles and the action bar
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, lx, ly, 0);
        text.dispatchTouchEvent(down);
        down.recycle();
        text.setTag("allowLongClick");
        boolean handled = text.performLongClick();
        text.setTag(null);
        MotionEvent up = MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, lx, ly, 0);
        text.dispatchTouchEvent(up);
        up.recycle();
        org.thunderdog.challegram.Tgx101Diag.mark("select: word selection started=" + text.hasSelection() + " handled=" + handled);
      }
    });
    return true;
  }
}
