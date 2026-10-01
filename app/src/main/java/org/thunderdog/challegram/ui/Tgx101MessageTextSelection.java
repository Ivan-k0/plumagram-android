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
    wrap.setOnClickListener(v -> popup.hideWindow(true)); // a tap aside closes

    TextView text = new TextView(activity) {
      private float downX, downY;

      @Override
      public boolean onTouchEvent (MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
          downX = e.getX();
          downY = e.getY();
        }
        return super.onTouchEvent(e);
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
        if (!popup.isWindowHidden()) popup.hideWindow(true); // selection gone → back to the chat
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
    popup.setDismissListener(p -> {
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
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, lx, ly, 0);
        text.dispatchTouchEvent(down);
        down.recycle();
        UI.post(() -> {
          MotionEvent up = MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, lx, ly, 0);
          text.dispatchTouchEvent(up);
          up.recycle();
        }, ViewConfiguration.getLongPressTimeout() + 80);
        // fallback: no selection after a second → select the word under the finger directly
        UI.post(() -> {
          if (popup.isWindowHidden() || text.hasSelection()) return;
          int offset = text.getOffsetForPosition(lx, ly);
          CharSequence value = text.getText();
          java.text.BreakIterator words = java.text.BreakIterator.getWordInstance();
          words.setText(value.toString());
          int from = words.preceding(Math.min(value.length(), offset + 1));
          int to = words.following(Math.max(0, offset));
          if (from == java.text.BreakIterator.DONE) from = 0;
          if (to == java.text.BreakIterator.DONE) to = value.length();
          org.thunderdog.challegram.Tgx101Diag.mark("select: long press didn't start the selection, selecting the word directly");
          if (value instanceof android.text.Spannable && to > from) {
            android.text.Selection.setSelection((android.text.Spannable) value, from, to);
            text.setTag("allowLongClick");
            text.performLongClick(); // shows the handles and the bar for the current selection
            text.setTag(null);
          }
        }, 1000);
      }
    });
    return true;
  }
}
