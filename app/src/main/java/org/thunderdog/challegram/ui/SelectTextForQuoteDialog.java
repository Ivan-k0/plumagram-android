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
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import tgx.td.data.MessageWithProperties;

/**
 * "Select Text" message-options entry: shows the message's plain text in a native, selectable
 * (not editable) {@link TextView} inside the app's sheet -- real drag-selection handles, the
 * magnifier loupe, and the system's own copy/select-all action bar all come for free from the
 * Android framework this way, instead of re-implementing character-precise touch/selection math
 * on top of this app's own hand-rolled canvas text engine (util/text/Text.java). That inline
 * approach was judged too risky to get right with no compiler available in the environment this
 * was originally built in -- see the commit message for the fuller reasoning. Functionally this
 * still delivers real free-form drag-to-select, just via an overlay rather than inline in the
 * bubble.
 *
 * On tapping "Reply" with an active selection, builds a {@link TdApi.InputTextQuote} from the
 * selected range (resolving its exact UTF-16 position via the synchronous/local
 * {@link TdApi.SearchQuote} call -- no manual offset bookkeeping needed) and hands off to
 * {@link MessagesController#showReply} exactly like the existing whole-message Reply action. If
 * nothing is selected when "Reply" is tapped, falls back to a normal (non-quoted) reply rather
 * than doing nothing.
 */
public class SelectTextForQuoteDialog {

  public static void show (MessagesController controller, Tdlib tdlib, TGMessage message, TdApi.FormattedText formattedText) {
    if (controller == null || formattedText == null || formattedText.text.isEmpty()) {
      return;
    }
    showSheet(controller, formattedText.text, (start, end) ->
      onReplyRequested(controller, tdlib, message, formattedText, start, end));
  }

  private interface OnReply {
    void onReply (int selectionStart, int selectionEnd);
  }

  /**
   * (user 2026-11-04 «старое окно сделай современней на всякий случай»): the app's own sheet instead of the system dialog.
   * Nothing selected: «Reply» answers the whole message and «Copy» takes all of the text.
   */
  private static void showSheet (MessagesController controller, String text, OnReply reply) {
    final Tgx101Sheet.Handle[] handle = new Tgx101Sheet.Handle[1];
    handle[0] = Tgx101Sheet.show(controller, null, body -> {
      Context context = controller.context();
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.SelectText)));

      final TextView textView = new TextView(context);
      textView.setText(text);
      textView.setTextIsSelectable(true);
      textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
      textView.setTextColor(Theme.textAccentColor());
      textView.setLineSpacing(Screen.dp(2f), 1f);
      textView.setPadding(Screen.dp(20f), 0, Screen.dp(20f), Screen.dp(16f));
      body.addView(textView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      TextView button = new TextView(context);
      button.setText(Lang.getString(R.string.Reply));
      button.setTextColor(Theme.getColor(ColorId.fillingPositiveContent));
      button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      button.setTypeface(Fonts.getRobotoMedium());
      button.setGravity(Gravity.CENTER);
      GradientDrawable buttonBg = new GradientDrawable();
      buttonBg.setColor(Theme.getColor(ColorId.fillingPositive));
      buttonBg.setCornerRadius(Screen.dp(14f));
      button.setBackground(buttonBg);
      Views.setClickable(button);
      button.setOnClickListener(v -> {
        int start = textView.getSelectionStart(), end = textView.getSelectionEnd();
        handle[0].dismiss();
        reply.onReply(start, end);
      });
      LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(50f));
      buttonParams.setMargins(Screen.dp(20f), 0, Screen.dp(20f), Screen.dp(8f));
      body.addView(button, buttonParams);

      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_content_copy_24, 0, Lang.getString(R.string.Copy), null, false, v -> {
        int start = textView.getSelectionStart(), end = textView.getSelectionEnd();
        UI.copyText(start >= 0 && end > start && end <= text.length() ? text.substring(start, end) : text, R.string.CopiedText);
        handle[0].dismiss();
      }));

      TextView quiet = new TextView(context);
      quiet.setText(Lang.getString(R.string.Cancel));
      quiet.setTextColor(Theme.textDecentColor());
      quiet.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      quiet.setGravity(Gravity.CENTER);
      Views.setClickable(quiet);
      RippleSupport.setTransparentSelector(quiet);
      quiet.setOnClickListener(v -> handle[0].dismiss());
      body.addView(quiet, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(46f)));
    });
  }

  /**
   * TGx101: the same selectable text for a voice/round message transcription. Telegram can quote only the
   * message's own text, so "Reply" answers the voice message and puts the selected fragment into the input
   * as a block quote; "Copy" copies the selection (or everything).
   */
  /** A transcription isn't in the message, the server can't quote it: a reply, with the part as a quote in the field */
  public static void replyWithTranscriptionPart (MessagesController controller, TGMessage message, String part) {
    final String selected = part != null ? part.trim() : null;
    TdApi.Message newestMessage = message.getNewestMessage();
    message.getMessageProperties(newestMessage.id, properties -> {
      if (properties == null) return;
      controller.runOnUiThreadOptional(() -> {
        controller.showReply(new MessageWithProperties(newestMessage, properties), null, 0, "", true, true);
        if (selected != null && !selected.isEmpty()) {
          String draft = selected + "\n";
          controller.fillDraft(new TdApi.FormattedText(draft, new TdApi.TextEntity[] {
            new TdApi.TextEntity(0, selected.length(), new TdApi.TextEntityTypeBlockQuote())
          }), true);
        }
      });
    });
  }

  public static void showForTranscription (MessagesController controller, Tdlib tdlib, TGMessage message, String text) {
    if (controller == null || text == null || text.isEmpty()) {
      return;
    }
    showSheet(controller, text, (start, end) ->
      replyWithTranscriptionPart(controller, message, start >= 0 && end > start && end <= text.length() ? text.substring(start, end) : null));
  }

  public static void onReplyRequested (MessagesController controller, Tdlib tdlib, TGMessage message, TdApi.FormattedText fullText, int selStart, int selEnd) {
    TdApi.InputTextQuote quote = null;

    if (selStart >= 0 && selEnd > selStart && selEnd <= fullText.text.length()) {
      String quoteText = fullText.text.substring(selStart, selEnd);
      TdApi.FormattedText quoteFormatted = new TdApi.FormattedText(quoteText, new TdApi.TextEntity[0]);
      int position = selStart;
      try {
        TdApi.FoundPosition found = tdlib.clientExecuteT(new TdApi.SearchQuote(fullText, quoteFormatted, selStart), false);
        if (found != null) {
          position = found.position;
        }
      } catch (Throwable ignored) {
        // Fall back to the raw selection offset -- TDLib will still accept the quote,
        // it just won't be pixel/offset-perfect if the guess was off (rare: only matters
        // when the same substring appears more than once in the message).
      }
      quote = new TdApi.InputTextQuote(quoteFormatted, null, position);
    }
    // else: nothing selected -- fall through with quote == null, i.e. a normal whole-message reply.
    final TdApi.InputTextQuote finalQuote = quote;

    TdApi.Message newestMessage = message.getNewestMessage();
    message.getMessageProperties(newestMessage.id, properties -> {
      if (properties == null) {
        org.thunderdog.challegram.Tgx101Diag.mark("quote: no message properties — reply not shown");
        return;
      }
      org.thunderdog.challegram.Tgx101Diag.mark("quote: reply with " + (finalQuote != null ? "a quote of " + finalQuote.text.text.length() + " chars" : "the whole message"));
      controller.runOnUiThreadOptional(() ->
        controller.showReply(new MessageWithProperties(newestMessage, properties), finalQuote, 0, "", true, true)
      );
    });
  }
}
