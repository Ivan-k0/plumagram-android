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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.Tgx101SelectionBar;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageText;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.PopupLayout;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

import tgx.td.Td;

/**
 * TGx101: text selection right inside a message bubble (double long press), like the official app.
 * The bubble's own text engine draws the highlight; a transparent layer over the chat draws the two handles
 * and takes their drags (with the system magnifier); the bubble bar follows the top handle: Quote, Copy,
 * Select all, Editor (own messages), then Translate and the other "process text" apps. No keyboard.
 * Around the text a swipe scrolls the chat, the first tap is ignored (safety), a second one closes; Back closes.
 */
public final class Tgx101MessageTextSelection {
  private Tgx101MessageTextSelection () { }

  private static boolean fail (String reason) {
    Tgx101Diag.mark("select: no in-bubble selection — " + reason);
    return false;
  }

  /** Returns false when this message can't be selected in place (then the caller uses the quote window) */
  public static boolean show (MessagesController controller, Tdlib tdlib, MessageView view, TGMessage msg, float touchX, float touchY, Runnable onClose) {
    Text text = msg.tgx101SelectableText(); // text, or a caption under media / files (user 2026-10-06)
    TdApi.Message message = msg.tgx101SelectableMessage();
    TdApi.FormattedText formatted = Td.textOrCaption(message.content);
    if (text == null) return fail(msg instanceof TGMessageText ? "link preview above the text" : "no text or caption: " + msg.getClass().getSimpleName());
    if (formatted == null || formatted.text.isEmpty() || text.getText() == null || text.getText().isEmpty()) return fail("no text");
    if (msg.isTranslated()) return fail("translated");
    int offset = text.tgx101OffsetAt(touchX, touchY);
    if (offset < 0) return fail("no offset at " + Math.round(touchX) + "," + Math.round(touchY));
    new Session(controller, tdlib, msg, text, formatted, message, onClose).start(offset);
    return true;
  }

  private static final class Session {
    private final MessagesController controller;
    private final Tdlib tdlib;
    private final TGMessage msg;
    private final Text text;
    private final TdApi.FormattedText formatted;
    private final TdApi.Message message;
    private final Runnable onClose;
    private final PopupLayout popup;
    private final Overlay overlay;
    private final Tgx101SelectionBar bar;
    private final @Nullable RecyclerView list;
    private final RecyclerView.OnScrollListener follow;
    private int start, end;
    private boolean closed;

    Session (MessagesController controller, Tdlib tdlib, TGMessage msg, Text text, TdApi.FormattedText formatted, TdApi.Message message, Runnable onClose) {
      this.controller = controller;
      this.tdlib = tdlib;
      this.msg = msg;
      this.text = text;
      this.formatted = formatted;
      this.message = message;
      this.onClose = onClose;
      Context context = controller.context();
      this.popup = new PopupLayout(context);
      popup.setNeedRootInsets();
      this.overlay = new Overlay(context, this);
      overlay.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      this.list = controller.getMessagesView();
      this.bar = new Tgx101SelectionBar(overlay, buildActions(context));
      this.follow = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
          if (closed) return;
          if (currentView() == null) {
            close("the message scrolled away");
            return;
          }
          overlay.postInvalidateOnAnimation();
        }
      };
    }

    private @Nullable View currentView () {
      View view = msg.findCurrentView();
      return view != null && androidx.core.view.ViewCompat.isAttachedToWindow(view) ? view : null;
    }

    void start (int offset) {
      int[] word = wordAt(offset);
      controller.tgx101SetInputQuiet(true);
      setSelection(word[0], word[1]);
      if (list != null) list.addOnScrollListener(follow);
      popup.setDismissListener(p -> close("dismissed"));
      popup.showNonAnimatedView(overlay);
      Tgx101Diag.mark("select: in-bubble selection " + start + ".." + end + " of " + text.getText().length());
    }

    void close (String reason) {
      if (closed) return;
      closed = true;
      Tgx101Diag.mark("select: closed — " + reason);
      text.tgx101ClearSelection();
      View view = currentView();
      if (view != null) view.invalidate();
      overlay.dismissMagnifier();
      bar.dismiss();
      if (list != null) list.removeOnScrollListener(follow);
      if (!popup.isWindowHidden()) popup.hideWindow(true);
      controller.tgx101SetInputQuiet(false);
      if (onClose != null) onClose.run();
    }

    void setSelection (int start, int end) {
      int length = text.getText().length();
      this.start = Math.max(0, Math.min(length, Math.min(start, end)));
      this.end = Math.max(0, Math.min(length, Math.max(start, end)));
      text.tgx101SetSelection(this.start, this.end, Theme.getColor(ColorId.textSelectionHighlight));
      View view = currentView();
      if (view != null) view.invalidate();
      overlay.invalidate();
    }

    int[] wordAt (int offset) {
      String s = text.getText();
      int length = s.length();
      if (length == 0) return new int[] {0, 0};
      int o = Math.max(0, Math.min(length - 1, offset));
      // on a space or a line break: the word before it, if any
      if (Character.isWhitespace(s.charAt(o)) && o > 0 && !Character.isWhitespace(s.charAt(o - 1))) o--;
      BreakIterator iterator = BreakIterator.getWordInstance();
      iterator.setText(s);
      int wordEnd = iterator.following(o);
      if (wordEnd == BreakIterator.DONE) wordEnd = length;
      int wordStart = iterator.previous();
      if (wordStart == BreakIterator.DONE) wordStart = 0;
      if (wordEnd <= wordStart || s.substring(wordStart, wordEnd).trim().isEmpty()) {
        wordStart = o;
        wordEnd = Math.min(length, o + Character.charCount(s.codePointAt(o)));
      }
      return new int[] {wordStart, wordEnd};
    }

    String selectedText () {
      return text.getText().substring(start, end);
    }

    // Bar actions

    private List<Tgx101SelectionBar.Action> buildActions (Context context) {
      List<Tgx101SelectionBar.Action> actions = new ArrayList<>();
      actions.add(new Tgx101SelectionBar.Action(Tgx101BarOrder.QUOTE, Lang.getString(R.string.Tgx101EditorQuote), () -> {
        int[] range = mapToMessage();
        close("quote");
        SelectTextForQuoteDialog.onReplyRequested(controller, tdlib, msg, formatted, range[0], range[1]);
      }));
      actions.add(new Tgx101SelectionBar.Action(Tgx101BarOrder.COPY, Lang.getString(R.string.Copy), () -> {
        UI.copyText(selectedText(), R.string.CopiedText);
        close("copy");
      }));
      actions.add(new Tgx101SelectionBar.Action(Tgx101BarOrder.SELECT_ALL, Lang.getString(R.string.Tgx101SelectAllFull), () -> {
        setSelection(0, text.getText().length());
        overlay.updateBar();
      }));
      if (Tgx101TextEditor.canEdit(message) && msg.canEditText()) {
        actions.add(new Tgx101SelectionBar.Action(Tgx101BarOrder.EDITOR, Lang.getString(R.string.Tgx101EditorMenu), () -> {
          close("editor");
          Tgx101TextEditor.showEdit(controller, tdlib, message);
        }));
      }
      // Translate, Web search and the other apps that take selected text — as in the system toolbar
      try {
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain");
        for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
          if (info.activityInfo == null || context.getPackageName().equals(info.activityInfo.packageName)) continue;
          CharSequence label = info.loadLabel(pm);
          final String packageName = info.activityInfo.packageName, className = info.activityInfo.name;
          actions.add(new Tgx101SelectionBar.Action(label != null ? label.toString() : className, () -> {
            Intent intent = new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").setClassName(packageName, className);
            intent.putExtra(Intent.EXTRA_PROCESS_TEXT, selectedText());
            intent.putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
            try {
              context.startActivity(intent);
            } catch (Throwable t) {
              Tgx101Diag.mark("select: process text failed — " + t.getClass().getSimpleName());
            }
          }));
        }
      } catch (Throwable t) {
        Tgx101Diag.mark("select: no process text apps — " + t.getClass().getSimpleName());
      }
      return actions;
    }

    /** The selection in the message's own text (the bubble may draw a slightly different string) */
    private int[] mapToMessage () {
      String shown = text.getText();
      if (shown.equals(formatted.text)) return new int[] {start, end};
      String selected = selectedText();
      int index = formatted.text.indexOf(selected, Math.max(0, Math.min(start, formatted.text.length()) / 2));
      if (index == -1) index = formatted.text.indexOf(selected);
      if (index == -1) return new int[] {0, formatted.text.length()};
      return new int[] {index, index + selected.length()};
    }
  }

  /** The transparent layer over the chat: the handles, their drags and the gestures around the text */
  private static final class Overlay extends View {
    private static final int NONE = 0, START = 1, END = 2;

    private final Session session;
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int slop;
    private final int[] location = new int[2], viewLocation = new int[2];
    // handle tips in this layer's coordinates: x and the line's top / bottom; NaN when not visible
    private float startX = Float.NaN, startTop, startBottom, endX = Float.NaN, endTop, endBottom;

    private int dragging = NONE;
    private float dragDx, dragDy;
    private boolean forwarding, movedAside;
    private float downRawX, downRawY;
    private long downTime;
    private long lastAsideTap;
    private @Nullable Object magnifier;

    Overlay (Context context, Session session) {
      super(context);
      this.session = session;
      this.slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private static int radius () {
      return Screen.dp(10f);
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      View view = session.currentView();
      startX = endX = Float.NaN;
      if (view == null || !session.text.tgx101HasSelection()) return;
      getLocationOnScreen(location);
      view.getLocationOnScreen(viewLocation);
      // user 2026-10-07 20:29 «ручки не на том месте»: with messages selected the bubble sits 28 dp to the right (checkboxes)
      float ox = viewLocation[0] - location[0] + session.msg.tgx101ContentShiftX(), oy = viewLocation[1] - location[1];
      float[] a = session.text.tgx101LocateOffset(session.start, false);
      float[] b = session.text.tgx101LocateOffset(session.end, true);
      if (a == null || b == null) return;
      startX = a[0] + ox;
      startTop = a[1] + oy;
      startBottom = a[2] + oy;
      endX = b[0] + ox;
      endTop = b[1] + oy;
      endBottom = b[2] + oy;
      // the app's own colour (the header blue); the accent in a dark theme, where the header is grey
      handlePaint.setColor(Theme.isDark() ? Theme.textAccentColor() : Theme.getColor(ColorId.headerBackground));
      int r = radius();
      // the drop shape: a circle hanging below the line with a square corner at the tip
      c.drawCircle(startX - r, startBottom + r, r, handlePaint);
      c.drawRect(startX - r, startBottom, startX, startBottom + r, handlePaint);
      c.drawCircle(endX + r, endBottom + r, r, handlePaint);
      c.drawRect(endX, endBottom, endX + r, endBottom + r, handlePaint);
      if (dragging == NONE) {
        post(this::updateBar);
      }
    }

    void updateBar () {
      if (session.closed || Float.isNaN(startX)) return;
      getLocationOnScreen(location);
      session.bar.showAnchored(Math.round(startX + location[0]), Math.round(startTop + location[1]), Math.round(endBottom + location[1]));
    }

    private int hitHandle (float x, float y) {
      if (Float.isNaN(startX)) return NONE;
      int r = radius();
      float touch = Screen.dp(26f);
      double toStart = Math.hypot(x - (startX - r), y - (startBottom + r));
      double toEnd = Math.hypot(x - (endX + r), y - (endBottom + r));
      if (toStart > touch && toEnd > touch) return NONE;
      return toStart <= toEnd ? START : END;
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          dragging = hitHandle(e.getX(), e.getY());
          if (dragging != NONE) {
            // the finger holds the handle below the line: track the point in the middle of the line it marks
            float tipX = dragging == START ? startX : endX;
            float lineY = dragging == START ? (startTop + startBottom) / 2f : (endTop + endBottom) / 2f;
            dragDx = tipX - e.getX();
            dragDy = lineY - e.getY();
            Tgx101Diag.mark("select: handle " + (dragging == START ? "start" : "end") + " grabbed");
            return true;
          }
          downRawX = e.getRawX();
          downRawY = e.getRawY();
          downTime = e.getDownTime();
          forwarding = movedAside = false;
          return true;
        }
        case MotionEvent.ACTION_MOVE: {
          if (dragging != NONE) {
            dragTo(e.getX() + dragDx, e.getY() + dragDy);
            return true;
          }
          if (!forwarding && Math.hypot(e.getRawX() - downRawX, e.getRawY() - downRawY) > slop) {
            forwarding = movedAside = true;
            forwardToList(downTime, MotionEvent.ACTION_DOWN, downRawX, downRawY);
          }
          if (forwarding) forwardToList(e.getEventTime(), MotionEvent.ACTION_MOVE, e.getRawX(), e.getRawY());
          return true;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
          if (dragging != NONE) {
            dragging = NONE;
            dismissMagnifier();
            updateBar(); // the spot where the bar comes back
            session.bar.showNow(); // right away, not after the drag pause
            Tgx101Diag.mark("select: handle released at " + session.start + ".." + session.end);
            invalidate();
            return true;
          }
          if (forwarding) {
            forwardToList(e.getEventTime(), e.getActionMasked(), e.getRawX(), e.getRawY());
            forwarding = false;
          } else if (up && !movedAside) {
            long now = SystemClock.uptimeMillis();
            if (now - lastAsideTap < 2000) {
              session.close("second tap aside");
            } else {
              lastAsideTap = now;
              Tgx101Diag.mark("select: first tap aside — kept (a second one closes)");
            }
          }
          return true;
        }
      }
      return true;
    }

    private void dragTo (float x, float y) {
      View view = session.currentView();
      if (view == null) return;
      getLocationOnScreen(location);
      view.getLocationOnScreen(viewLocation);
      float vx = x + location[0] - viewLocation[0] - session.msg.tgx101ContentShiftX(), vy = y + location[1] - viewLocation[1];
      int offset = session.text.tgx101OffsetAt(vx, vy);
      if (offset < 0) return;
      int start = session.start, end = session.end;
      if (dragging == START) {
        if (offset >= end) {
          if (offset == end) return;
          // crossed the other handle: they swap
          start = end;
          end = offset;
          dragging = END;
        } else {
          start = offset;
        }
      } else {
        if (offset <= start) {
          if (offset == start) return;
          end = start;
          start = offset;
          dragging = START;
        } else {
          end = offset;
        }
      }
      if (start != session.start || end != session.end) {
        session.setSelection(start, end);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
          if (org.thunderdog.challegram.unsorted.Settings.instance().tgx101Haptics()) performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
        }
      }
      session.bar.onSelectionChanging(); // hidden while dragging (the magnifier), back above the selection after
      showMagnifier(view, vx + session.msg.tgx101ContentShiftX());
    }

    private void showMagnifier (View view, float xInView) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
      try {
        if (magnifier == null) magnifier = new android.widget.Magnifier(view);
        // centred on the line of the dragged handle
        float[] tip = dragging == START ? session.text.tgx101LocateOffset(session.start, false) : session.text.tgx101LocateOffset(session.end, true);
        if (tip == null) return;
        ((android.widget.Magnifier) magnifier).show(xInView, (tip[1] + tip[2]) / 2f);
      } catch (Throwable ignored) { }
    }

    void dismissMagnifier () {
      if (magnifier != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try { ((android.widget.Magnifier) magnifier).dismiss(); } catch (Throwable ignored) { }
      }
      magnifier = null;
    }

    private void forwardToList (long eventTime, int action, float rawX, float rawY) {
      RecyclerView list = session.list;
      if (list == null) return;
      int[] listLocation = new int[2];
      list.getLocationOnScreen(listLocation);
      MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, rawX - listLocation[0], rawY - listLocation[1], 0);
      list.dispatchTouchEvent(event);
      event.recycle();
    }
  }
}
