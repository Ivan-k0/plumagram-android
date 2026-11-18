package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageText;

/**
 * TGx101 (user 2026-11-18 «нужно сделать точно как в оффе», frame by frame next to the official app): the text just sent
 * leaves the field at once and flies into its new bubble, meeting it on the way — instead of the field emptying and the
 * bubble coming up from under it a moment later.
 *
 * No bitmaps: the field's text is laid out once more (a copy of its words and paint) and drawn by this transparent view
 * lying over the chat and the field. Each frame it moves from where the words stood in the field towards where the new
 * message draws its text right now (the message itself glides up meanwhile), shrinking or growing to the bubble's text
 * size, and fades out over the last part as the bubble's own text is there.
 */
final class Tgx101SendFlight extends View {
  static final long DURATION = 200;
  private static final long WAIT_LIMIT = 450; // the message never showed: give up softly

  private final ViewGroup list;
  private StaticLayout layout;
  private float startX, startY, lineHeight;
  private long messageId;
  private @Nullable TGMessage message; // the message itself: its id changes when the server takes it (local → real)
  private long armedAt, flyingAt;
  private float lastX, lastY, lastScale = 1f, drawnY;

  private static final android.view.animation.Interpolator CURVE =
    androidx.core.view.animation.PathInterpolatorCompat.create(.2f, 0f, 0f, 1f);

  Tgx101SendFlight (Context context, ViewGroup list) {
    super(context);
    this.list = list;
    setWillNotDraw(false);
    setClickable(false);
    setFocusable(false);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  /** Takes the words out of the field (call right before it is emptied); false when there is nothing to fly */
  boolean arm (TextView field) {
    Layout old = field.getLayout();
    CharSequence text = field.getText();
    if (old == null || text == null || text.length() == 0 || old.getLineCount() > 12) return false;
    try {
      TextPaint paint = new TextPaint(field.getPaint());
      paint.setColor(field.getCurrentTextColor());
      SpannableStringBuilder words = new SpannableStringBuilder(text);
      // the keyboard's underline under the word being typed (and its suggestions) stays in the field
      for (Object span : words.getSpans(0, words.length(), Object.class)) {
        if ((words.getSpanFlags(span) & android.text.Spanned.SPAN_COMPOSING) != 0 || span instanceof android.text.style.UnderlineSpan || span instanceof android.text.style.SuggestionSpan) {
          words.removeSpan(span);
        }
      }
      layout = new StaticLayout(words, paint, Math.max(1, old.getWidth()), Layout.Alignment.ALIGN_NORMAL,
        field.getLineSpacingMultiplier(), field.getLineSpacingExtra(), false);
    } catch (Throwable t) {
      layout = null;
      return false;
    }
    int[] at = new int[2], me = new int[2];
    field.getLocationInWindow(at);
    getLocationInWindow(me);
    startX = at[0] - me[0] + field.getTotalPaddingLeft() - field.getScrollX();
    startY = at[1] - me[1] + field.getTotalPaddingTop() - field.getScrollY();
    lineHeight = layout.getLineCount() > 0 ? layout.getLineBottom(0) - layout.getLineTop(0) : 1;
    this.messageId = 0; // bound when the new message comes into the chat (bind)
    armedAt = SystemClock.uptimeMillis();
    flyingAt = 0;
    lastX = startX;
    lastY = startY;
    drawnY = startY;
    lastScale = 1f;
    org.thunderdog.challegram.Tgx101Diag.mark("send flight: armed, " + layout.getLineCount() + " line(s) from " + Math.round(startX) + "," + Math.round(startY));
    invalidate();
    return true;
  }

  /** The message these words became has come into the chat: they fly to it. False if there is no flight waiting */
  boolean bind (TGMessage message) {
    if (layout == null || this.messageId != 0 || SystemClock.uptimeMillis() - armedAt > WAIT_LIMIT) return false;
    this.messageId = message.getId();
    this.message = message;
    org.thunderdog.challegram.Tgx101Diag.mark("send flight: bound to " + messageId + " +" + (SystemClock.uptimeMillis() - armedAt) + " ms");
    return true;
  }

  /** where the new message draws its text now, in this view's coordinates: {x, y, line height}; null if not on screen */
  private @Nullable float[] target () {
    for (int i = list.getChildCount() - 1; i >= 0; i--) {
      View child = list.getChildAt(i);
      if (!(child instanceof MessageView)) continue;
      TGMessage msg = ((MessageView) child).getMessage();
      if (msg == null || !(msg == message || msg.isDescendantOrSelf(messageId)) || !(msg instanceof TGMessageText)) continue;
      int[] frame = ((TGMessageText) msg).tgx101TextFrame();
      if (frame == null) return null;
      int[] at = new int[2], me = new int[2];
      child.getLocationInWindow(at);
      getLocationInWindow(me);
      return new float[] {at[0] - me[0] + frame[0], at[1] - me[1] + frame[1], frame[4]};
    }
    return null;
  }

  private void finish (String why) {
    org.thunderdog.challegram.Tgx101Diag.mark("send flight: " + why);
    layout = null;
    messageId = 0;
    message = null;
    invalidate();
  }

  @Override
  protected void onDraw (Canvas c) {
    if (layout == null) return;
    long now = SystemClock.uptimeMillis();
    float[] to = messageId != 0 ? target() : null;
    if (to != null && flyingAt == 0) flyingAt = now;
    float alpha;
    float x, y, scale;
    if (flyingAt == 0) {
      // the message is not in the chat yet (a frame or two): the words stay where they are, over the field's own
      if (now - armedAt > WAIT_LIMIT) {
        finish("the message never showed");
        return;
      }
      x = startX;
      y = startY;
      scale = 1f;
      alpha = 1f;
    } else {
      float t = Math.min(1f, (now - flyingAt) / (float) DURATION);
      float e = CURVE.getInterpolation(t);
      if (to != null) {
        lastX = to[0];
        lastY = to[1];
        lastScale = to[2] > 0 && lineHeight > 0 ? to[2] / lineHeight : 1f;
      }
      x = startX + (lastX - startX) * e;
      // the new message comes up from under the field: the words never go down to meet it, they wait and go up
      y = Math.min(drawnY, startY + (lastY - startY) * e);
      scale = 1f + (lastScale - 1f) * e;
      // the bubble's own text is under it from the start: the flying words give way over the second half
      // (phone check 2026-11-18): over half the flight two texts showed side by side — the words give way sooner, and
      // several lines (wrapped differently in the field and the bubble) sooner still
      float from = layout.getLineCount() > 1 ? .12f : .25f, span = layout.getLineCount() > 1 ? .33f : .45f;
      alpha = t < from ? 1f : Math.max(0f, 1f - (t - from) / span);
      if (alpha <= 0f && t > .5f) {
        finish("landed");
        return;
      }
      if (t >= 1f) {
        finish("landed");
        return;
      }
    }
    drawnY = y;
    int save = c.save();
    c.translate(x, y);
    c.scale(scale, scale);
    TextPaint paint = layout.getPaint();
    int oldAlpha = paint.getAlpha();
    paint.setAlpha(Math.round(oldAlpha * alpha));
    layout.draw(c);
    paint.setAlpha(oldAlpha);
    c.restoreToCount(save);
    postInvalidateOnAnimation();
  }
}
