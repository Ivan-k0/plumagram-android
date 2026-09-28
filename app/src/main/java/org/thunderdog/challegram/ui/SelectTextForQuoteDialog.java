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

import android.app.AlertDialog;
import android.content.Context;
import android.util.TypedValue;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import tgx.td.data.MessageWithProperties;

/**
 * "Select Text" message-options entry: shows the message's plain text in a native, selectable
 * (not editable) {@link TextView} inside a themed dialog -- real drag-selection handles, the
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
    Context context = controller.context();

    final TextView textView = new TextView(context);
    textView.setText(formattedText.text);
    textView.setTextIsSelectable(true);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
    int paddingH = Screen.dp(20f), paddingV = Screen.dp(14f);
    textView.setPadding(paddingH, paddingV, paddingH, paddingV);

    AlertDialog.Builder builder = new AlertDialog.Builder(context, Theme.dialogTheme());
    builder.setTitle(Lang.getString(R.string.SelectText));
    builder.setView(textView);
    builder.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
    builder.setPositiveButton(Lang.getString(R.string.Reply), (dialog, which) -> {
      dialog.dismiss();
      onReplyRequested(controller, tdlib, message, formattedText, textView.getSelectionStart(), textView.getSelectionEnd());
    });

    controller.showAlert(builder);
  }

  /**
   * TGx101: the same selectable text for a voice/round message transcription. Telegram can quote only the
   * message's own text, so "Reply" answers the voice message and puts the selected fragment into the input
   * as a block quote; "Copy" copies the selection (or everything).
   */
  public static void showForTranscription (MessagesController controller, Tdlib tdlib, TGMessage message, String text) {
    if (controller == null || text == null || text.isEmpty()) {
      return;
    }
    Context context = controller.context();
    final TextView textView = new TextView(context);
    textView.setText(text);
    textView.setTextIsSelectable(true);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
    textView.setTextColor(Theme.textAccentColor());
    int paddingH = Screen.dp(20f), paddingV = Screen.dp(14f);
    textView.setPadding(paddingH, paddingV, paddingH, paddingV);

    AlertDialog.Builder builder = new AlertDialog.Builder(context, Theme.dialogTheme());
    builder.setTitle(Lang.getString(R.string.SelectText));
    builder.setView(textView);
    builder.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
    builder.setNeutralButton(Lang.getString(R.string.Copy), (dialog, which) -> {
      int start = textView.getSelectionStart(), end = textView.getSelectionEnd();
      String copy = start >= 0 && end > start && end <= text.length() ? text.substring(start, end) : text;
      UI.copyText(copy, R.string.CopiedText);
      dialog.dismiss();
    });
    builder.setPositiveButton(Lang.getString(R.string.Reply), (dialog, which) -> {
      int start = textView.getSelectionStart(), end = textView.getSelectionEnd();
      String selected = start >= 0 && end > start && end <= text.length() ? text.substring(start, end).trim() : null;
      dialog.dismiss();
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
    });
    controller.showAlert(builder);
  }

  private static void onReplyRequested (MessagesController controller, Tdlib tdlib, TGMessage message, TdApi.FormattedText fullText, int selStart, int selEnd) {
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
      quote = new TdApi.InputTextQuote(quoteFormatted, position);
    }
    // else: nothing selected -- fall through with quote == null, i.e. a normal whole-message reply.
    final TdApi.InputTextQuote finalQuote = quote;

    TdApi.Message newestMessage = message.getNewestMessage();
    message.getMessageProperties(newestMessage.id, properties -> {
      if (properties == null) {
        return;
      }
      controller.runOnUiThreadOptional(() ->
        controller.showReply(new MessageWithProperties(newestMessage, properties), finalQuote, 0, "", true, true)
      );
    });
  }
}
