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
package org.thunderdog.challegram.data;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.InstantViewController;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * TGx101: rich messages (MessageRichMessage) — posts made of blocks, like Instant View pages.
 * The chat shows their text; the whole post (photos, buttons, formatting) opens in the built-in
 * Instant View viewer.
 */
public final class Tgx101RichMessage {
  private Tgx101RichMessage () { }

  private static final int MAX_CHAT_TEXT = 3500;
  public static final String OPEN_LINK_PREFIX = "tgx101rich://open?";

  public static TGMessage valueOf (MessagesManager context, TdApi.Message msg, TdApi.MessageRichMessage content) {
    Builder b = new Builder();
    b.blocks(content.message != null ? content.message.blocks : null);
    TdApi.FormattedText text = b.build();
    if (text.text.isEmpty()) {
      String hint = Lang.getString(R.string.RichMessageOpenHint);
      text = new TdApi.FormattedText(hint, new TdApi.TextEntity[] {new TdApi.TextEntity(0, hint.length(), new TdApi.TextEntityTypeItalic())});
    }
    if (b.hasHiddenContent || (content.message != null && !content.message.isFull)) {
      // A link at the end opens the whole post: photos, tables and buttons as the author made them
      String open = Lang.getString(R.string.RichMessageOpenFull);
      String url = OPEN_LINK_PREFIX + "chat=" + msg.chatId + "&msg=" + msg.id;
      List<TdApi.TextEntity> entities = new ArrayList<>(java.util.Arrays.asList(text.entities));
      String full = text.text + "\n\n" + open;
      entities.add(new TdApi.TextEntity(full.length() - open.length(), open.length(), new TdApi.TextEntityTypeTextUrl(url)));
      text = new TdApi.FormattedText(full, entities.toArray(new TdApi.TextEntity[0]));
    }
    return new TGMessageText(context, msg, text);
  }

  public static boolean isRichMessage (@Nullable TdApi.Message message) {
    return message != null && message.content != null && message.content.getConstructor() == TdApi.MessageRichMessage.CONSTRUCTOR;
  }

  // Chat list preview: "Photo" + beginning of the text

  public static ContentPreview preview (TdApi.MessageRichMessage content) {
    TdApi.PageBlock[] blocks = content.message != null ? content.message.blocks : null;
    String text = toText(content.message).text.replace('\n', ' ').trim();
    if (text.length() > 200) {
      text = text.substring(0, 200);
    }
    int mediaType = findMediaType(blocks, 0);
    switch (mediaType) {
      case TdApi.PageBlockPhoto.CONSTRUCTOR:
        return new ContentPreview(ContentPreview.EMOJI_PHOTO, R.string.ChatContentPhoto, text, true);
      case TdApi.PageBlockVideo.CONSTRUCTOR:
        return new ContentPreview(ContentPreview.EMOJI_VIDEO, R.string.ChatContentVideo, text, true);
      case TdApi.PageBlockAnimation.CONSTRUCTOR:
        return new ContentPreview(ContentPreview.EMOJI_GIF, R.string.ChatContentAnimation, text, true);
    }
    return new ContentPreview(text, true);
  }

  private static int findMediaType (@Nullable TdApi.PageBlock[] blocks, int depth) {
    if (blocks == null || depth > 3) {
      return 0;
    }
    for (TdApi.PageBlock block : blocks) {
      switch (block.getConstructor()) {
        case TdApi.PageBlockPhoto.CONSTRUCTOR:
        case TdApi.PageBlockVideo.CONSTRUCTOR:
        case TdApi.PageBlockAnimation.CONSTRUCTOR:
          return block.getConstructor();
        case TdApi.PageBlockCover.CONSTRUCTOR: {
          int type = findMediaType(new TdApi.PageBlock[] {((TdApi.PageBlockCover) block).cover}, depth + 1);
          if (type != 0) return type;
          break;
        }
        case TdApi.PageBlockCollage.CONSTRUCTOR: {
          int type = findMediaType(((TdApi.PageBlockCollage) block).blocks, depth + 1);
          if (type != 0) return type;
          break;
        }
        case TdApi.PageBlockSlideshow.CONSTRUCTOR: {
          int type = findMediaType(((TdApi.PageBlockSlideshow) block).blocks, depth + 1);
          if (type != 0) return type;
          break;
        }
      }
    }
    return 0;
  }

  // Text for the chat, with the author's formatting

  private static final class Builder {
    final StringBuilder text = new StringBuilder();
    final List<TdApi.TextEntity> entities = new ArrayList<>();
    boolean hasHiddenContent;

    boolean isFull () {
      return text.length() >= MAX_CHAT_TEXT;
    }

    void separate (String separator) {
      if (text.length() == 0) return;
      if (separator.equals("\n\n")) {
        int trailing = 0;
        while (trailing < 2 && trailing < text.length() && text.charAt(text.length() - 1 - trailing) == '\n') {
          trailing++;
        }
        for (int k = trailing; k < 2; k++) text.append('\n');
      } else {
        text.append(separator);
      }
    }

    void entity (int start, TdApi.TextEntityType type) {
      int end = text.length();
      // Keep entities off trailing line breaks
      while (end > start && text.charAt(end - 1) == '\n') {
        end--;
      }
      if (end > start) {
        entities.add(new TdApi.TextEntity(start, end - start, type));
      }
    }

    void rich (@Nullable TdApi.RichText rt) {
      if (rt == null) return;
      int start = text.length();
      switch (rt.getConstructor()) {
        case TdApi.RichTextPlain.CONSTRUCTOR:
          text.append(((TdApi.RichTextPlain) rt).text);
          return;
        case TdApi.RichTexts.CONSTRUCTOR:
          for (TdApi.RichText t : ((TdApi.RichTexts) rt).texts) rich(t);
          return;
        case TdApi.RichTextBold.CONSTRUCTOR:
          rich(((TdApi.RichTextBold) rt).text);
          entity(start, new TdApi.TextEntityTypeBold());
          return;
        case TdApi.RichTextItalic.CONSTRUCTOR:
          rich(((TdApi.RichTextItalic) rt).text);
          entity(start, new TdApi.TextEntityTypeItalic());
          return;
        case TdApi.RichTextUnderline.CONSTRUCTOR:
          rich(((TdApi.RichTextUnderline) rt).text);
          entity(start, new TdApi.TextEntityTypeUnderline());
          return;
        case TdApi.RichTextStrikethrough.CONSTRUCTOR:
          rich(((TdApi.RichTextStrikethrough) rt).text);
          entity(start, new TdApi.TextEntityTypeStrikethrough());
          return;
        case TdApi.RichTextSpoiler.CONSTRUCTOR:
          rich(((TdApi.RichTextSpoiler) rt).text);
          entity(start, new TdApi.TextEntityTypeSpoiler());
          return;
        case TdApi.RichTextFixed.CONSTRUCTOR:
          rich(((TdApi.RichTextFixed) rt).text);
          entity(start, new TdApi.TextEntityTypeCode());
          return;
        case TdApi.RichTextUrl.CONSTRUCTOR: {
          TdApi.RichTextUrl url = (TdApi.RichTextUrl) rt;
          rich(url.text);
          if (url.url != null && !url.url.isEmpty()) entity(start, new TdApi.TextEntityTypeTextUrl(url.url));
          return;
        }
        case TdApi.RichTextReferenceLink.CONSTRUCTOR:
          rich(((TdApi.RichTextReferenceLink) rt).text);
          return;
        case TdApi.RichTextAnchorLink.CONSTRUCTOR:
          rich(((TdApi.RichTextAnchorLink) rt).text);
          return;
        case TdApi.RichTextMention.CONSTRUCTOR: {
          TdApi.RichTextMention mention = (TdApi.RichTextMention) rt;
          rich(mention.text);
          if (mention.username != null && !mention.username.isEmpty()) entity(start, new TdApi.TextEntityTypeTextUrl("https://t.me/" + mention.username));
          return;
        }
        case TdApi.RichTextMentionName.CONSTRUCTOR: {
          TdApi.RichTextMentionName mention = (TdApi.RichTextMentionName) rt;
          rich(mention.text);
          entity(start, new TdApi.TextEntityTypeMentionName(mention.userId));
          return;
        }
        case TdApi.RichTextHashtag.CONSTRUCTOR:
          text.append(TD.getText(rt));
          entity(start, new TdApi.TextEntityTypeHashtag());
          return;
        case TdApi.RichTextCashtag.CONSTRUCTOR:
          text.append(TD.getText(rt));
          entity(start, new TdApi.TextEntityTypeCashtag());
          return;
        case TdApi.RichTextCustomEmoji.CONSTRUCTOR: {
          TdApi.RichTextCustomEmoji emoji = (TdApi.RichTextCustomEmoji) rt;
          if (emoji.alternativeText != null && !emoji.alternativeText.isEmpty()) {
            text.append(emoji.alternativeText);
            entity(start, new TdApi.TextEntityTypeCustomEmoji(emoji.customEmojiId));
          }
          return;
        }
        case TdApi.RichTextIcon.CONSTRUCTOR:
        case TdApi.RichTextAnchor.CONSTRUCTOR:
          return;
        case TdApi.RichTextButton.CONSTRUCTOR:
          hasHiddenContent = true;
          return;
        case TdApi.RichTextMathematicalExpression.CONSTRUCTOR:
          text.append(((TdApi.RichTextMathematicalExpression) rt).expression);
          entity(start, new TdApi.TextEntityTypeCode());
          return;
        case TdApi.RichTextDiff.CONSTRUCTOR:
          rich(((TdApi.RichTextDiff) rt).text);
          return;
      }
      // Marked, sub/superscript, date, e-mail, phone and anything newer: plain text
      String plain = TD.getText(rt);
      if (plain != null) text.append(plain);
    }

    void paragraph (@Nullable TdApi.RichText rt, @Nullable TdApi.TextEntityType type) {
      if (rt == null || isFull()) return;
      separate("\n\n");
      int start = text.length();
      rich(rt);
      if (text.length() == start) {
        return;
      }
      if (type != null) {
        entity(start, type);
      }
    }

    void caption (@Nullable TdApi.PageBlockCaption caption) {
      if (caption != null) {
        paragraph(caption.text, new TdApi.TextEntityTypeItalic());
      }
    }

    void blocks (@Nullable TdApi.PageBlock[] blocks) {
      if (blocks == null) return;
      for (TdApi.PageBlock block : blocks) {
        if (isFull()) {
          hasHiddenContent = true;
          return;
        }
        block(block);
      }
    }

    void block (TdApi.PageBlock block) {
      switch (block.getConstructor()) {
        case TdApi.PageBlockTitle.CONSTRUCTOR:
          paragraph(((TdApi.PageBlockTitle) block).title, new TdApi.TextEntityTypeBold());
          return;
        case TdApi.PageBlockHeader.CONSTRUCTOR:
          paragraph(((TdApi.PageBlockHeader) block).header, new TdApi.TextEntityTypeBold());
          return;
        case TdApi.PageBlockSubheader.CONSTRUCTOR:
          paragraph(((TdApi.PageBlockSubheader) block).subheader, new TdApi.TextEntityTypeBold());
          return;
        case TdApi.PageBlockSectionHeading.CONSTRUCTOR:
          paragraph(((TdApi.PageBlockSectionHeading) block).text, new TdApi.TextEntityTypeBold());
          return;
        case TdApi.PageBlockKicker.CONSTRUCTOR:
          paragraph(((TdApi.PageBlockKicker) block).kicker, new TdApi.TextEntityTypeBold());
          return;
        case TdApi.PageBlockSubtitle.CONSTRUCTOR:
          paragraph(((TdApi.PageBlockSubtitle) block).subtitle, new TdApi.TextEntityTypeItalic());
          return;
        case TdApi.PageBlockParagraph.CONSTRUCTOR:
          paragraph(((TdApi.PageBlockParagraph) block).text, null);
          return;
        case TdApi.PageBlockFooter.CONSTRUCTOR:
          paragraph(((TdApi.PageBlockFooter) block).footer, new TdApi.TextEntityTypeItalic());
          return;
        case TdApi.PageBlockThinking.CONSTRUCTOR:
          paragraph(((TdApi.PageBlockThinking) block).text, new TdApi.TextEntityTypeItalic());
          return;
        case TdApi.PageBlockPreformatted.CONSTRUCTOR: {
          TdApi.PageBlockPreformatted pre = (TdApi.PageBlockPreformatted) block;
          paragraph(pre.text, pre.language != null && !pre.language.isEmpty() ? new TdApi.TextEntityTypePreCode(pre.language) : new TdApi.TextEntityTypePre());
          return;
        }
        case TdApi.PageBlockMathematicalExpression.CONSTRUCTOR:
          paragraph(new TdApi.RichTextPlain(((TdApi.PageBlockMathematicalExpression) block).expression), new TdApi.TextEntityTypeCode());
          return;
        case TdApi.PageBlockAuthorDate.CONSTRUCTOR:
          paragraph(((TdApi.PageBlockAuthorDate) block).author, new TdApi.TextEntityTypeItalic());
          return;
        case TdApi.PageBlockDivider.CONSTRUCTOR:
        case TdApi.PageBlockAnchor.CONSTRUCTOR:
          return;
        case TdApi.PageBlockBlockQuote.CONSTRUCTOR: {
          TdApi.PageBlockBlockQuote quote = (TdApi.PageBlockBlockQuote) block;
          separate("\n\n");
          int start = text.length();
          Builder inner = new Builder();
          inner.blocks(quote.blocks);
          if (quote.credit != null) inner.paragraph(quote.credit, new TdApi.TextEntityTypeItalic());
          appendBuilder(inner);
          entity(start, new TdApi.TextEntityTypeBlockQuote());
          return;
        }
        case TdApi.PageBlockPullQuote.CONSTRUCTOR: {
          TdApi.PageBlockPullQuote quote = (TdApi.PageBlockPullQuote) block;
          quote(quote.text, quote.credit, new TdApi.TextEntityTypeBlockQuote());
          return;
        }
        case TdApi.PageBlockExpandableBlockQuote.CONSTRUCTOR: {
          TdApi.PageBlockExpandableBlockQuote quote = (TdApi.PageBlockExpandableBlockQuote) block;
          quote(quote.text, quote.credit, new TdApi.TextEntityTypeExpandableBlockQuote());
          return;
        }
        case TdApi.PageBlockList.CONSTRUCTOR: {
          separate("\n\n");
          boolean first = true;
          for (TdApi.PageBlockListItem item : ((TdApi.PageBlockList) block).items) {
            if (isFull()) break;
            if (!first) text.append('\n');
            first = false;
            String label = item.hasCheckbox ? (item.isChecked ? "☑" : "☐") : (item.label != null && !item.label.isEmpty() ? item.label : "•");
            text.append(label).append(' ');
            Builder inner = new Builder();
            inner.blocks(item.blocks);
            appendBuilder(inner, " ");
          }
          return;
        }
        case TdApi.PageBlockTable.CONSTRUCTOR:
          table((TdApi.PageBlockTable) block);
          return;
        case TdApi.PageBlockDetails.CONSTRUCTOR: {
          TdApi.PageBlockDetails details = (TdApi.PageBlockDetails) block;
          paragraph(details.header, new TdApi.TextEntityTypeBold());
          blocks(details.blocks);
          return;
        }
        case TdApi.PageBlockCover.CONSTRUCTOR:
          block(((TdApi.PageBlockCover) block).cover);
          return;
        case TdApi.PageBlockPhoto.CONSTRUCTOR:
          hasHiddenContent = true;
          caption(((TdApi.PageBlockPhoto) block).caption);
          return;
        case TdApi.PageBlockVideo.CONSTRUCTOR:
          hasHiddenContent = true;
          caption(((TdApi.PageBlockVideo) block).caption);
          return;
        case TdApi.PageBlockAnimation.CONSTRUCTOR:
          hasHiddenContent = true;
          caption(((TdApi.PageBlockAnimation) block).caption);
          return;
        case TdApi.PageBlockCollage.CONSTRUCTOR:
          hasHiddenContent = true;
          caption(((TdApi.PageBlockCollage) block).caption);
          return;
        case TdApi.PageBlockSlideshow.CONSTRUCTOR:
          hasHiddenContent = true;
          caption(((TdApi.PageBlockSlideshow) block).caption);
          return;
      }
      // Audio, documents, maps, buttons, embeds and anything newer: whatever text they carry
      hasHiddenContent = true;
      Builder plain = new Builder();
      appendPlain(plain, block, 0);
      if (plain.text.length() > 0) {
        separate("\n\n");
        appendBuilder(plain);
      }
    }

    void quote (TdApi.RichText quoteText, @Nullable TdApi.RichText credit, TdApi.TextEntityType type) {
      if (isFull()) return;
      separate("\n\n");
      int start = text.length();
      rich(quoteText);
      if (credit != null) {
        text.append('\n');
        int creditStart = text.length();
        rich(credit);
        entity(creditStart, new TdApi.TextEntityTypeItalic());
      }
      entity(start, type);
    }

    /** Tables become readable lines: the row's first cell in bold, then "column: value". */
    void table (TdApi.PageBlockTable table) {
      if (table.caption != null) {
        paragraph(table.caption, new TdApi.TextEntityTypeBold());
      }
      if (table.cells == null) return;
      String[] headers = null;
      for (TdApi.PageBlockTableCell[] row : table.cells) {
        if (row == null || row.length == 0 || isFull()) continue;
        boolean headerRow = true;
        for (TdApi.PageBlockTableCell cell : row) {
          if (!cell.isHeader) { headerRow = false; break; }
        }
        if (headerRow && headers == null) {
          headers = new String[row.length];
          for (int i = 0; i < row.length; i++) {
            String h = row[i].text != null ? TD.getText(row[i].text) : null;
            headers[i] = h != null ? h.replace('\n', ' ').trim() : "";
          }
          continue;
        }
        separate("\n\n");
        int start = text.length();
        if (row[0].text != null) rich(row[0].text);
        entity(start, new TdApi.TextEntityTypeBold());
        for (int i = 1; i < row.length; i++) {
          TdApi.PageBlockTableCell cell = row[i];
          if (cell.text == null) continue;
          Builder value = new Builder();
          value.rich(cell.text);
          if (value.text.toString().trim().isEmpty()) continue;
          text.append('\n');
          String header = headers != null && i < headers.length ? headers[i] : "";
          if (!header.isEmpty() && headers.length > 2) {
            text.append(header).append(": ");
          }
          appendBuilder(value, ", ");
        }
      }
    }

    void appendBuilder (Builder other) {
      appendBuilder(other, null);
    }

    void appendBuilder (Builder other, @Nullable String newlineReplacement) {
      int offset = text.length();
      String src = other.text.toString().trim();
      int lead = other.text.indexOf(src.isEmpty() ? "\u0000" : src.substring(0, 1));
      if (lead < 0) lead = 0;
      // Old index -> new index, so the formatting stays on the right characters
      int[] map = new int[other.text.length() + 1];
      StringBuilder out = new StringBuilder();
      for (int k = 0; k < other.text.length(); k++) {
        map[k] = out.length();
        if (k < lead || k >= lead + src.length()) continue;
        char ch = other.text.charAt(k);
        if (ch == '\n' && newlineReplacement != null) {
          if (out.length() > 0 && !out.toString().endsWith(newlineReplacement)) out.append(newlineReplacement);
        } else {
          out.append(ch);
        }
      }
      map[other.text.length()] = out.length();
      text.append(out);
      for (TdApi.TextEntity e : other.entities) {
        int from = map[Math.min(e.offset, other.text.length())];
        int to = map[Math.min(e.offset + e.length, other.text.length())];
        if (to > from) {
          entities.add(new TdApi.TextEntity(offset + from, to - from, e.type));
        }
      }
      hasHiddenContent |= other.hasHiddenContent;
    }

    TdApi.FormattedText build () {
      int shift = 0;
      while (shift < text.length() && Character.isWhitespace(text.charAt(shift))) shift++;
      String s = text.toString().trim();
      if (s.length() > MAX_CHAT_TEXT) {
        s = s.substring(0, MAX_CHAT_TEXT) + "…";
        hasHiddenContent = true;
      }
      List<TdApi.TextEntity> result = new ArrayList<>();
      for (TdApi.TextEntity e : entities) {
        int from = e.offset - shift, to = from + e.length;
        from = Math.max(0, from);
        to = Math.min(s.length(), to);
        if (to > from) {
          result.add(new TdApi.TextEntity(from, to - from, e.type));
        }
      }
      java.util.Collections.sort(result, (a, b) -> a.offset != b.offset ? Integer.compare(a.offset, b.offset) : Integer.compare(b.length, a.length));
      return new TdApi.FormattedText(s, result.toArray(new TdApi.TextEntity[0]));
    }
  }

  public static TdApi.FormattedText toText (@Nullable TdApi.RichMessage message) {
    Builder b = new Builder();
    if (message != null) {
      b.blocks(message.blocks);
    }
    return b.build();
  }

  /**
   * Walks any block generically (all RichText, caption, list item and nested block fields), so
   * block types added to TDLib later still show their text.
   */
  private static void appendPlain (Builder b, @Nullable Object block, int depth) {
    if (block == null || depth > 6) {
      return;
    }
    for (Field field : block.getClass().getFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Object value;
      try {
        value = field.get(block);
      } catch (IllegalAccessException e) {
        continue;
      }
      if (value instanceof TdApi.RichText) {
        b.paragraph((TdApi.RichText) value, null);
      } else if (value instanceof TdApi.PageBlockCaption) {
        b.paragraph(((TdApi.PageBlockCaption) value).text, null);
      } else if (value instanceof TdApi.PageBlock) {
        appendPlain(b, value, depth + 1);
      } else if (value instanceof Object[]) {
        for (Object item : (Object[]) value) {
          if (item instanceof TdApi.RichText) {
            b.paragraph((TdApi.RichText) item, null);
          } else if (item instanceof Object[]) {
            for (Object cell : (Object[]) item) {
              appendPlain(b, cell, depth + 1);
            }
          } else if (item != null && !(item instanceof String)) {
            appendPlain(b, item, depth + 1);
          }
        }
      }
    }
  }

  // Link at the end of the chat text

  public static boolean openLink (@NonNull ViewController<?> c, @Nullable String url) {
    if (url == null || !url.startsWith(OPEN_LINK_PREFIX)) {
      return false;
    }
    long chatId = 0, messageId = 0;
    for (String part : url.substring(OPEN_LINK_PREFIX.length()).split("&")) {
      String[] kv = part.split("=", 2);
      if (kv.length != 2) continue;
      try {
        if (kv[0].equals("chat")) chatId = Long.parseLong(kv[1]);
        else if (kv[0].equals("msg")) messageId = Long.parseLong(kv[1]);
      } catch (NumberFormatException ignored) { }
    }
    if (chatId == 0 || messageId == 0) {
      return true;
    }
    c.tdlib().send(new TdApi.GetMessage(chatId, messageId), (message, error) -> UI.post(() -> {
      if (message != null && !c.isDestroyed()) {
        open(c, message);
      }
    }));
    return true;
  }

  // Full post

  public static void open (@NonNull ViewController<?> c, @NonNull TdApi.Message message) {
    if (!isRichMessage(message)) {
      return;
    }
    TdApi.RichMessage rich = ((TdApi.MessageRichMessage) message.content).message;
    Tdlib tdlib = c.tdlib();
    if (rich.isFull) {
      show(c, message, rich);
    } else {
      tdlib.send(new TdApi.GetFullRichMessage(message.chatId, message.id), (full, error) -> UI.post(() -> {
        if (full != null) {
          show(c, message, full);
        } else {
          UI.showToast(Lang.getString(R.string.RichMessageOpenFailed, TD.toErrorString(error)), Toast.LENGTH_LONG);
        }
      }));
    }
  }

  private static void show (ViewController<?> c, TdApi.Message message, TdApi.RichMessage rich) {
    Tdlib tdlib = c.tdlib();
    String title = tdlib.chatTitle(message.chatId);
    String username = tdlib.chatUsername(message.chatId);
    String url = username != null ? "https://t.me/" + username + "/" + (message.id >> 20) : "";
    TdApi.LinkPreview preview = new TdApi.LinkPreview(url, url, title, title, null, null, null,
      false, false, false, false, false, Config.SUPPORTED_INSTANT_VIEW_VERSION);
    TdApi.WebPageInstantView instantView = new TdApi.WebPageInstantView(rich.blocks, 0, Config.SUPPORTED_INSTANT_VIEW_VERSION, rich.isRtl, true, null);
    InstantViewController controller = new InstantViewController(c.context(), tdlib);
    controller.setArguments(new InstantViewController.Args(preview, instantView, null));
    try {
      controller.show();
    } catch (Throwable t) {
      Log.w("TGx101: can't show rich message", t);
      controller.destroy();
      UI.showToast(R.string.RichMessageOpenUnsupported, Toast.LENGTH_LONG);
    }
  }
}
