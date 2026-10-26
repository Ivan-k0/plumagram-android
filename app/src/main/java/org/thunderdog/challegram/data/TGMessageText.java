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
 * File created on 03/05/2015 at 10:53
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextWrapper;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.android.animator.VariableFloat;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import tgx.td.Td;

public class TGMessageText extends TGMessage {
  private TdApi.FormattedText text;
  private final VariableFloat lastLineWidth = new VariableFloat(0f);
  private final ReplaceAnimator<TextWrapper> visibleText = new ReplaceAnimator<>(new ReplaceAnimator.Callback() {
    @Override
    public void onItemChanged (ReplaceAnimator<?> animator) {
      if (rebuildContentDimensions()) {
        requestLayout();
      } else {
        invalidate();
      }
    }

    @Override
    public boolean hasChanges (ReplaceAnimator<?> animator) {
      int width = effectiveWrapper != null ? effectiveWrapper.getLastLineWidth() : 0;
      return lastLineWidth.differs(width);
    }

    @Override
    public void onForceApplyChanges (ReplaceAnimator<?> animator) {
      int width = effectiveWrapper != null ? effectiveWrapper.getLastLineWidth() : 0;
      lastLineWidth.set(width);
    }

    @Override
    public void onPrepareMetadataAnimation (ReplaceAnimator<?> animator) {
      int width = effectiveWrapper != null ? effectiveWrapper.getLastLineWidth() : 0;
      lastLineWidth.setTo(width);
    }

    @Override
    public void onFinishMetadataAnimation (ReplaceAnimator<?> animator, boolean applyFuture) {
      lastLineWidth.finishAnimation(applyFuture);
    }

    @Override
    public boolean onApplyMetadataAnimation (ReplaceAnimator<?> animator, float factor) {
      return lastLineWidth.applyAnimation(factor);
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, TEXT_CROSS_FADE_DURATION_MS);

  private TGWebPage linkPreview;
  private TdApi.MessageText currentMessageText, pendingMessageText;
  private final BoolAnimator linkPreviewAboveText = new BoolAnimator(0,
    (id, factor, fraction, callee) -> invalidate(),
    AnimatorUtils.DECELERATE_INTERPOLATOR, 180L
  );

  public TGMessageText (MessagesManager context, TdApi.Message msg, TdApi.MessageText text, @Nullable TdApi.MessageText pendingMessageText) {
    super(context, msg);
    this.currentMessageText = text;
    this.pendingMessageText = pendingMessageText;
    if (this.pendingMessageText != null) {
      setText(this.pendingMessageText.text, false);
      setLinkPreview(this.pendingMessageText.linkPreview, this.pendingMessageText.linkPreviewOptions);
    } else {
      setText(text.text, false);
      setLinkPreview(text.linkPreview, text.linkPreviewOptions);
    }
  }

  public TGMessageText (MessagesManager context, TdApi.SponsoredMessage sponsoredMessage, long inChatId, boolean isBelowAllMessages) {
    super(context, sponsoredMessage, inChatId, isBelowAllMessages);
    this.currentMessageText = (TdApi.MessageText) sponsoredMessage.content;
    setText(currentMessageText.text, false);
    // TODO button
  }

  public TGMessageText (MessagesManager context, TdApi.Message msg, TdApi.FormattedText text) {
    this(context, msg, new TdApi.MessageText(text, null, null), null);
  }

  public TdApi.File getTargetFile () {
    return linkPreview != null ? linkPreview.getTargetFile() : null;
  }

  @Override
  public MediaViewThumbLocation getMediaThumbLocation (long messageId, View view, int viewTop, int viewBottom, int top) {
    if (linkPreview == null || linkPreview.getMediaWrapper() == null) {
      return null;
    }
    MediaViewThumbLocation location = linkPreview.getMediaWrapper().getMediaThumbLocation(view, viewTop, viewBottom, top);
    if (location != null) {
      location.setColorId(useBubbles() && isOutgoing() ? ColorId.bubbleOut_background : ColorId.filling);
    }
    return location;
  }

  public TdApi.FormattedText getText () {
    return text;
  }

  @Nullable
  public String findUriFragment (TdApi.LinkPreview linkPreview) {
    if (text.entities == null || text.entities.length == 0)
      return null;
    Uri lookupUri = Strings.wrapHttps(linkPreview.url);
    if (lookupUri == null)
      return null;
    int count = 0;
    Uri uri = null;
    for (TdApi.TextEntity entity : text.entities) {
      String url;
      //noinspection SwitchIntDef
      switch (entity.type.getConstructor()) {
        case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
          url = Td.substring(text.text, entity);
          break;
        case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
          url = ((TdApi.TextEntityTypeTextUrl) entity.type).url;
          break;
        default:
          continue;
      }
      count++;

      uri = Strings.wrapHttps(url);
      if (uri != null && uri.buildUpon().fragment(null).build().equals(lookupUri)) {
        return uri.getEncodedFragment();
      }
    }
    return count == 1 && uri != null ? uri.getEncodedFragment() : null;
  }

  @Override
  protected int onMessagePendingContentChanged (long chatId, long messageId, int oldHeight) {
    if (currentMessageText != null) {
      TdApi.MessageContent messageContent = tdlib.getPendingMessageText(chatId, messageId);
      final @EmojiMessageContentType int contentType = getEmojiMessageContentType(messageContent);
      boolean allowEmoji = !Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI);
      if (contentType != EmojiMessageContentType.NOT_EMOJI) {
        return MESSAGE_REPLACE_REQUIRED;
      }
      if (messageContent != null && Td.isAnimatedEmoji(messageContent) && !allowEmoji) {
        messageContent = new TdApi.MessageText(Td.textOrCaption(messageContent), null, null);
      }
      if (this.pendingMessageText != messageContent) {
        if (messageContent != null && !Td.isText(messageContent))
          return MESSAGE_REPLACE_REQUIRED;
        TdApi.MessageText messageText = (TdApi.MessageText) messageContent;
        this.pendingMessageText = messageText;
        final boolean textChanged, linkPreviewChanged;
        if (messageText != null) {
          textChanged = setText(messageText.text, false);
          linkPreviewChanged = setLinkPreview(messageText.linkPreview, messageText.linkPreviewOptions);
        } else {
          textChanged = setText(currentMessageText.text, false);
          linkPreviewChanged = setLinkPreview(currentMessageText.linkPreview, currentMessageText.linkPreviewOptions);
        }
        if (!textChanged && !linkPreviewChanged) {
          return MESSAGE_NOT_CHANGED;
        }
        if (linkPreviewChanged) {
          rebuildContent();
        }
        return (getHeight() == oldHeight ? MESSAGE_INVALIDATED : MESSAGE_CHANGED);
      }
    }
    return MESSAGE_NOT_CHANGED;
  }

  @Nullable
  @Override
  protected TdApi.LinkPreview findLinkPreview (String link) {
    return linkPreview != null && linkPreview.isPreviewOf(link) ? linkPreview.getLinkPreview() : null;
  }

  @Override
  protected boolean hasInstantView (String link) {
    if (linkPreview == null || !linkPreview.needInstantView())
      return false;
    if (link.equals(linkPreview.getLinkPreview().url))
      return true;
    boolean found = false;
    for (TdApi.TextEntity entity : text.entities) {
      String url;
      if (Td.isUrl(entity.type)) {
        url = text.text.substring(entity.offset, entity.offset + entity.length);
      } else if (Td.isTextUrl(entity.type)) {
        url = ((TdApi.TextEntityTypeTextUrl) entity.type).url;
      } else {
        continue;
      }
      if (link.equals(url)) {
        found = true;
      } else {
        found = false;
        break;
      }
    }
    return found;
  }

  @Override
  protected boolean isBeingEdited () {
    return pendingMessageText != null;
  }

  private boolean setText (TdApi.FormattedText text, boolean parseEntities) {
    return setText(text, parseEntities, false, true);
  }

  private boolean setText (TdApi.FormattedText text, boolean parseEntities, boolean forceUpdate, boolean animated) {
    if (text == null) text = new TdApi.FormattedText("", new TdApi.TextEntity[0]); // TGx101: never null here (see updateMessageContent)
    if (this.text == null || !Td.equalsTo(this.text, text) || forceUpdate) {
      animated = animated && Config.ENABLE_TEXT_ANIMATIONS && needAnimateChanges();

      this.text = text;
      TextColorSet colorSet = isErrorMessage() ? TextColorSets.Regular.NEGATIVE : getTextColorSet();
      TextWrapper.TextMediaListener textMediaListener = (wrapper, updatedText, specificTextMedia) -> {
        if (effectiveWrapper == wrapper) {
          invalidateTextMediaReceiver(updatedText, specificTextMedia);
        }
      };
      TextWrapper wrapper;
      if (translatedText != null) {
        wrapper = new TextWrapper(translatedText.text, getTextStyleProvider(), colorSet)
          .setEntities(TextEntity.valueOf(tdlib, translatedText, openParameters()), textMediaListener)
          .setHighlightText(getHighlightedText(Highlight.Pool.KEY_TEXT, translatedText.text))
          .setClickCallback(clickCallback());
      } else if (text.entities != null || !parseEntities) {
        TdApi.FormattedText displayText = Tgx101Text.displayText(this, text); // TGx101: without the channel's "Subscribe" line
        displayText = tgx101WithSummary(displayText); // TGx101: «✨ Short» under a long post, or the summary on top
        wrapper = new TextWrapper(displayText.text, getTextStyleProvider(), colorSet)
          .setEntities(TextEntity.valueOf(tdlib, displayText, openParameters()), textMediaListener)
          .setHighlightText(getHighlightedText(Highlight.Pool.KEY_TEXT, displayText.text))
          .setClickCallback(clickCallback());
      } else {
        wrapper = new TextWrapper(text.text, getTextStyleProvider(), colorSet)
          .setEntities(Text.makeEntities(text.text, Text.ENTITY_FLAGS_ALL, null, tdlib, openParameters()), textMediaListener)
          .setHighlightText(getHighlightedText(Highlight.Pool.KEY_TEXT, text.text))
          .setClickCallback(clickCallback());
      }
      wrapper.addTextFlags(Text.FLAG_BIG_EMOJI);
      if (tgx101CanSummarize()) {
        // TGx101 (user 2026-10-25, mockup «К1» for every bubble): «Кратко» sits inside the bubble at the top right, the
        // first line ends before it — above the text it went out of a headerless post and onto a discussion's channel icon
        final int room = Screen.dp(TGX101_SHORT_SIZE + 6f);
        wrapper.setLineWidthProvider((lineIndex, y, defaultMaxWidth, lineHeight) -> y < Screen.dp(TGX101_SHORT_SIZE) ? defaultMaxWidth - room : defaultMaxWidth);
      }
      if (useBubbles()) {
        wrapper.addTextFlags(Text.FLAG_ADJUST_TO_CURRENT_WIDTH);
      }
      if (Config.USE_NONSTRICT_TEXT_ALWAYS || !useBubbles()) {
        wrapper.addTextFlags(Text.FLAG_BOUNDS_NOT_STRICT);
      }
      wrapper.setViewProvider(currentViews);
      TextWrapper lastWrapper = effectiveWrapper;
      boolean hadMedia = lastWrapper != null && lastWrapper.hasMedia();
      if (hadMedia) {
        textMediaKeyOffset += lastWrapper.getMaxMediaCount();
      }
      wrapper.prepare(getContentMaxWidth());
      this.effectiveWrapper = wrapper;
      this.visibleText.replace(wrapper, animated);
      this.visibleText.measure(animated);

      if (hadMedia || wrapper.hasMedia()) {
        invalidateTextMediaReceiver();
      }
      return true;
    }
    return false;
  }

  @Override
  protected void onUpdateHighlightedText () {
    if (this.text != null) {
      setText(this.text, false, true, false);
      rebuildContent();
    }
  }

  private TextWrapper effectiveWrapper;

  private final java.util.ArrayList<android.graphics.RectF> tgx101QuoteRects = new java.util.ArrayList<>();

  @Override
  protected boolean tgx101DrawQuoteHighlight (Canvas c, float factor, long elapsed) {
    org.thunderdog.challegram.util.text.Text text = effectiveWrapper != null ? effectiveWrapper.getCurrent() : null;
    String quote = tgx101QuoteText;
    String all = text != null ? text.getText() : null;
    if (all == null || quote == null) return false;
    int from = tgx101QuotePosition >= 0 && tgx101QuotePosition + quote.length() <= all.length() && all.startsWith(quote, tgx101QuotePosition) ? tgx101QuotePosition : all.indexOf(quote);
    if (from < 0) return false;
    tgx101QuoteRects.clear();
    text.tgx101RangeRects(from, from + quote.length(), tgx101QuoteRects);
    if (tgx101QuoteRects.isEmpty()) return false;
    boolean dark = Theme.isDark();
    int base = dark ? 0xff6fbcff : 0xff2f8ae0;
    float pad = Screen.dp(2f), r = Screen.dp(5f);
    // the words: a soft tint while the highlight lasts
    for (android.graphics.RectF rect : tgx101QuoteRects) {
      android.graphics.RectF rr = Paints.getRectF();
      rr.set(rect.left - pad, rect.top, rect.right + pad, rect.bottom);
      c.drawRoundRect(rr, r, r, Paints.fillingPaint(me.vkryl.core.ColorUtils.alphaColor(factor * (dark ? .30f : .22f), base)));
    }
    // the wave: a brighter band runs once over the quoted words, line by line (~0.9 s)
    float total = 0f;
    for (android.graphics.RectF rect : tgx101QuoteRects) total += rect.width();
    float t = Math.min(1f, elapsed / 900f);
    if (t < 1f && total > 0f) {
      float pos = total * (t * (2f - t)), band = Screen.dp(28f), passed = 0f;
      for (android.graphics.RectF rect : tgx101QuoteRects) {
        float x = rect.left + (pos - passed);
        float left = Math.max(rect.left - pad, x - band), right = Math.min(rect.right + pad, x);
        if (right > left) {
          android.graphics.RectF rr = Paints.getRectF();
          rr.set(left, rect.top, right, rect.bottom);
          c.drawRoundRect(rr, r, r, Paints.fillingPaint(me.vkryl.core.ColorUtils.alphaColor(.35f * (1f - t), base)));
        }
        passed += rect.width();
      }
    }
    return true;
  }

  private static final int MAX_WEB_PAGE_MEDIA_COUNT = Integer.MAX_VALUE / 4;
  private long textMediaKeyOffset;

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    if (effectiveWrapper == null && linkPreview == null) {
      textMediaReceiver.clear();
      return;
    }
    if (linkPreview != null) {
      linkPreview.requestTextMedia(textMediaReceiver, 0);
    } else {
      textMediaReceiver.clearReceiversRange(0, MAX_WEB_PAGE_MEDIA_COUNT);
    }
    if (effectiveWrapper != null) {
      long startKey = MAX_WEB_PAGE_MEDIA_COUNT + textMediaKeyOffset;
      effectiveWrapper.requestMedia(textMediaReceiver, startKey, Long.MAX_VALUE - startKey);
    } else {
      textMediaReceiver.clearReceiversWithHigherKey(MAX_WEB_PAGE_MEDIA_COUNT);
    }
  }

  @Override
  protected int getBubbleContentPadding () {
    return xBubblePadding + xBubblePaddingSmall;
  }

  private int maxWidth;

  @Override
  protected void buildContent (int maxWidth) {
    maxWidth = Math.max(maxWidth, computeBubbleTimePartWidth(false));

    this.maxWidth = maxWidth;

    for (ListAnimator.Entry<TextWrapper> entry : visibleText) {
      entry.item.prepare(maxWidth);
    }
    visibleText.measure(false);

    int linkPreviewMaxWidth = getSmallestMaxContentWidth();
    if (pendingMessageText != null) {
      if (setLinkPreview(pendingMessageText.linkPreview, pendingMessageText.linkPreviewOptions))
        linkPreview.buildLayout(linkPreviewMaxWidth);
    } else if (Td.isText(msg.content) && setLinkPreview(((TdApi.MessageText) msg.content).linkPreview, ((TdApi.MessageText) msg.content).linkPreviewOptions)) {
      linkPreview.buildLayout(linkPreviewMaxWidth);
    } else if (linkPreview != null && linkPreview.getMaxWidth() != linkPreviewMaxWidth) {
      linkPreview.buildLayout(linkPreviewMaxWidth);
    }
  }

  private boolean setLinkPreview (TdApi.LinkPreview linkPreview, @Nullable TdApi.LinkPreviewOptions linkPreviewOptions) {
    if (linkPreview != null) {
      String url = null;
      if (text != null) {
        url = Td.findUrl(text, linkPreview.url, false);
      }
      if (StringUtils.isEmpty(url)) {
        url = linkPreview.url;
      }
      this.linkPreview = new TGWebPage(this, linkPreview, url, linkPreviewOptions);
      this.linkPreview.setViewProvider(currentViews);
      this.linkPreviewAboveText.setValue(linkPreviewOptions != null && linkPreviewOptions.showAboveText, needAnimateChanges());
      return true;
    } else {
      this.linkPreview = null;
      this.linkPreviewAboveText.setValue(false, false);
    }
    return false;
  }

  @Override
  protected void onMessageIdChanged (long oldMessageId, long newMessageId, boolean success) {
    if (linkPreview != null) {
      linkPreview.updateMessageId(oldMessageId, newMessageId, success);
    }
  }

  @Override
  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) {
    if (linkPreview != null) {
      linkPreview.notifyInvalidateTargetsChanged();
    }
  }

  private int getWebY () {
    float linkPreviewAboveText = this.linkPreviewAboveText.getFloatValue();
    return getContentY() + getTextTopOffset() + (int) ((visibleText.getMetadata().getTotalHeight() + Screen.dp(6f) * visibleText.getMetadata().getTotalVisibility()) * (1f - linkPreviewAboveText));
  }

  @Override
  protected boolean isSupportedMessageContent (TdApi.Message message, TdApi.MessageContent messageContent) {
    final @EmojiMessageContentType int contentType = getEmojiMessageContentType(messageContent);
    if (messageContent.getConstructor() == TdApi.MessageText.CONSTRUCTOR || messageContent.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR) {
      return contentType == EmojiMessageContentType.NOT_EMOJI;
    }
    return super.isSupportedMessageContent(message, messageContent);
  }

  @Override
  protected boolean onMessageContentChanged (TdApi.Message message, TdApi.MessageContent oldContent, TdApi.MessageContent newContent, boolean isBottomMessage) {
    TdApi.MessageText oldMessageText = Td.isText(oldContent) ? (TdApi.MessageText) oldContent : null;
    TdApi.MessageText newMessageText = Td.isText(newContent) ? (TdApi.MessageText) newContent : null;
    if (!Td.equalsTo(Td.textOrCaption(oldContent), Td.textOrCaption(newContent)) ||
        !Td.equalsTo(oldMessageText != null ? oldMessageText.linkPreview : null,
                     newMessageText != null ? newMessageText.linkPreview : null) ||
        !Td.equalsTo(oldMessageText != null ? oldMessageText.linkPreviewOptions : null,
                     newMessageText != null ? newMessageText.linkPreviewOptions : null)
    ) {
      updateMessageContent(msg, newContent, isBottomMessage);
      return true;
    }
    return false;
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    this.msg.content = newContent;
    TdApi.FormattedText newTextOrCaption = Td.textOrCaption(newContent);
    // TGx101 (crash report 2026-10-23 14:31, Xiaomi, «вылеты при свайпах в каналах»): a post whose new content has neither
    // text nor caption — setText read the entities of null; it reads an empty text now
    TdApi.MessageText newText = Td.isText(newContent) ? (TdApi.MessageText) newContent : new TdApi.MessageText(newTextOrCaption != null ? newTextOrCaption : new TdApi.FormattedText("", new TdApi.TextEntity[0]), null, null);
    this.currentMessageText = newText;
    if (!isBeingEdited()) {
      boolean textChanged = setText(newText.text, false);
      boolean linkPreviewChanged = setLinkPreview(newText.linkPreview, newText.linkPreviewOptions);
      if (linkPreviewChanged) {
        rebuildContent();
        invalidateContent(this);
        invalidatePreviewReceiver();
      }
      if (linkPreviewChanged || textChanged) {
        invalidate();
      }
    }
    return true;
  }

  @Override
  public boolean needImageReceiver () {
    return linkPreview != null;
  }

  @Override
  public boolean needGifReceiver () {
    return linkPreview != null && linkPreview.needGif();
  }

  @Override
  public int getImageContentRadius (boolean isPreview) {
    return linkPreview != null ? linkPreview.getImageContentRadius(isPreview) : 0;
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    if (linkPreview != null) {
      linkPreview.requestContent(receiver, getContentX(), getWebY());
    } else {
      receiver.requestFile(null);
    }
  }

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    if (linkPreview != null) {
      linkPreview.autodownloadContent(type);
    }
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    if (linkPreview != null) {
      linkPreview.requestPreview(receiver);
    } else {
      receiver.clear();
    }
  }

  @Override
  public void requestGif (GifReceiver receiver) {
    if (linkPreview != null) {
      linkPreview.requestGif(receiver, getContentX(), getWebY());
    } else {
      receiver.requestFile(null);
    }
  }

  // Text without any trash

  private int getStartXRtl (TextWrapper wrapper, int startX, int maxWidth) {
    return useBubbles() ? (Config.MOVE_BUBBLE_TIME_RTL_TO_LEFT || wrapper.getLineCount() > 1 ? getActualRightContentEdge() - getBubbleContentPadding() : startX) : startX + maxWidth;
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    float alpha = getTranslationLoadingAlphaValue();
    final int endXPadding = Config.MOVE_BUBBLE_TIME_RTL_TO_LEFT ? 0 : getBubbleTimePartWidth();
    float linkPreviewAboveText = this.linkPreviewAboveText.getFloatValue();
    ComplexReceiver textMediaReceiver = view.getTextMediaReceiver();
    int linkPreviewY = getWebY();
    final int topTextY = startY + getTextTopOffset();
    final int bottomTextY = linkPreview == null ? topTextY : linkPreviewY + linkPreview.getHeight() + Screen.dp(6f) + Screen.dp(2f);
    for (ListAnimator.Entry<TextWrapper> entry : visibleText) {
      final int startXRtl = getStartXRtl(entry.item, startX, maxWidth);
      boolean needClip = entry.getVisibility() != 1f && useBubbles();
      int saveToCount = -1;
      if (needClip) {
        saveToCount = Views.save(c);
        c.clipRect(bubblePathRect);
      }
      float textAlpha = alpha * entry.getVisibility();
      if (linkPreviewAboveText == 0f || linkPreview == null || receiver == null) {
        entry.item.draw(c, startX, startXRtl, endXPadding, topTextY, null, textAlpha, textMediaReceiver);
      } else if (linkPreviewAboveText == 1f) {
        entry.item.draw(c, startX, startXRtl, endXPadding, bottomTextY, null, textAlpha, textMediaReceiver);
      } else {
        entry.item.beginDrawBatch(textMediaReceiver, 1);

        // top text
        int topTextHeight = (int) ((float) (entry.item.getHeight() + Screen.dp(6f)) * MathUtils.clamp(linkPreviewAboveText));
        entry.item.draw(c, startX, startXRtl, endXPadding, topTextY - topTextHeight, null, textAlpha * MathUtils.clamp(1f - linkPreviewAboveText), textMediaReceiver);

        // bottom text
        entry.item.draw(c, startX, startXRtl, endXPadding, bottomTextY, null, textAlpha * MathUtils.clamp(linkPreviewAboveText), textMediaReceiver);

        entry.item.finishDrawBatch(textMediaReceiver, 1);
      }
      if (needClip) {
        Views.restore(c, saveToCount);
      }
    }
    if (linkPreview != null && receiver != null) {
      int linkPreviewX = Lang.rtl() ? startX + maxWidth - linkPreview.getWidth() : startX;
      linkPreview.draw(view, c, linkPreviewX, linkPreviewY, preview, receiver, alpha, textMediaReceiver);
    }
    tgx101DrawShortButton(c, startX, startY, maxWidth);
  }

  // TGx101 (user 2026-10-21 «кнопку вверху справа, как в оффе, чтобы не листать до конца», mockup «Кратко-значки» 1 —
  // «сжатие»): a small circle at the post's top right, in the line of its header; lit while the summary is shown
  private static final float TGX101_SHORT_SIZE = 30f; // (user 2026-10-25 «иконка "кратко" маловата»: was 24)
  private final android.graphics.RectF tgx101ShortRect = new android.graphics.RectF();
  private boolean tgx101ShortPressed;

  private void tgx101DrawShortButton (Canvas c, int startX, int startY, int maxWidth) {
    if (!tgx101CanSummarize()) {
      tgx101ShortRect.setEmpty();
      return;
    }
    float size = Screen.dp(TGX101_SHORT_SIZE);
    float right = startX + maxWidth, top = startY;
    tgx101ShortRect.set(right - size, top, right, top + size);
    int accent = Theme.getColor(ColorId.textLink);
    boolean lit = tgx101SummaryShown || tgx101ShortPressed;
    android.graphics.Paint fill = Paints.fillingPaint(me.vkryl.core.ColorUtils.alphaColor(lit ? 1f : .16f, accent));
    c.drawCircle(tgx101ShortRect.centerX(), tgx101ShortRect.centerY(), size / 2f, fill);
    android.graphics.Paint line = Paints.getProgressPaint(lit ? 0xffffffff : accent, Screen.dp(1.6f));
    // (user 2026-10-25, mockup «И2»): a magic wand over two short lines; the icon is drawn on a 24-unit grid
    float u = size * .78f / 24f, ox = tgx101ShortRect.centerX() - 12 * u, oy = tgx101ShortRect.centerY() - 12 * u;
    android.graphics.Paint thin = Paints.getProgressPaint(lit ? 0xffffffff : accent, 2f * u);
    c.drawLine(ox + 4 * u, oy + 15 * u, ox + 13 * u, oy + 15 * u, thin);
    c.drawLine(ox + 4 * u, oy + 19 * u, ox + 10 * u, oy + 19 * u, thin);
    c.drawLine(ox + 11 * u, oy + 11 * u, ox + 19 * u, oy + 3 * u, Paints.getProgressPaint(lit ? 0xffffffff : accent, 2.2f * u));
    android.graphics.Paint spark = Paints.fillingPaint(lit ? 0xffffffff : accent);
    tgx101Spark(c, ox + 6.5f * u, oy + 6.2f * u, 2.2f * u, spark);
    tgx101Spark(c, ox + 19f * u, oy + 13.1f * u, 2.1f * u, spark);
  }

  private static final android.graphics.Path tgx101SparkPath = new android.graphics.Path();

  /** a four-pointed star centred at (cx, cy) reaching {@code r} */
  private static void tgx101Spark (Canvas c, float cx, float cy, float r, android.graphics.Paint paint) {
    float k = r * .28f;
    android.graphics.Path p = tgx101SparkPath;
    p.reset();
    p.moveTo(cx, cy - r);
    p.lineTo(cx + k, cy - k);
    p.lineTo(cx + r, cy);
    p.lineTo(cx + k, cy + k);
    p.lineTo(cx, cy + r);
    p.lineTo(cx - k, cy + k);
    p.lineTo(cx - r, cy);
    p.lineTo(cx - k, cy - k);
    p.close();
    c.drawPath(p, paint);
  }

  private boolean tgx101ShortTouch (MessageView view, MotionEvent e) {
    if (tgx101ShortRect.isEmpty()) return false;
    float x = e.getX(), y = e.getY();
    float slop = Screen.dp(6f);
    boolean inside = x >= tgx101ShortRect.left - slop && x <= tgx101ShortRect.right + slop && y >= tgx101ShortRect.top - slop && y <= tgx101ShortRect.bottom + slop;
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        if (!inside) return false;
        tgx101ShortPressed = true;
        view.invalidate();
        return true;
      case MotionEvent.ACTION_MOVE:
        if (tgx101ShortPressed && !inside) {
          tgx101ShortPressed = false;
          view.invalidate();
        }
        return tgx101ShortPressed;
      case MotionEvent.ACTION_UP:
        if (tgx101ShortPressed) {
          tgx101ShortPressed = false;
          view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
          tgx101ToggleSummary();
          view.invalidate();
          return true;
        }
        return false;
      case MotionEvent.ACTION_CANCEL:
        if (tgx101ShortPressed) {
          tgx101ShortPressed = false;
          view.invalidate();
          return true;
        }
        return false;
    }
    return false;
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    drawContent(view, c, startX, startY, maxWidth, null, null);
  }

  @Override
  protected int getContentHeight () {
    int height = Math.round(visibleText.getMetadata().getTotalHeight() + getTextTopOffset() * visibleText.getMetadata().getTotalVisibility());
    if (linkPreview != null) {
      if (height > 0)
        height += Screen.dp(6f);
      height += linkPreview.getHeight() + Screen.dp(2f);
    }
    return height;
  }

  @Override
  protected int getFooterPaddingTop () {
    return Td.isEmpty(text) ? -Screen.dp(3f) : Screen.dp(7f);
  }

  private int calculateTextLastLineWidth () {
    if (effectiveWrapper != null && Lang.rtl() == effectiveWrapper.getLastLineIsRtl()) {
      // TODO: support for rtl <-> non-rtl transition
      return Math.round(lastLineWidth.get());
    }
    return BOTTOM_LINE_EXPAND_HEIGHT;
  }

  @Override
  protected int getBottomLineContentWidth () {
    int textLastLineWidth = calculateTextLastLineWidth();
    float linkPreviewAboveText = this.linkPreviewAboveText.getFloatValue();
    if (linkPreviewAboveText == 0f || linkPreview == null) {
      if (linkPreview != null) {
        return linkPreview.getLastLineWidth();
      } else {
        return textLastLineWidth;
      }
    } else if (linkPreviewAboveText == 1f) {
      return textLastLineWidth;
    } else {
      // Animated
      return BOTTOM_LINE_DEFINE_BY_FACTOR;
    }
  }

  @Override
  protected float getIntermediateBubbleExpandFactor () {
    int textLastLineWidth = calculateTextLastLineWidth();
    int linkPreviewLastLineWidth = linkPreview != null ? linkPreview.getLastLineWidth() : textLastLineWidth;
    float fromExpandFactor = linkPreviewLastLineWidth == BOTTOM_LINE_EXPAND_HEIGHT ? 1f : 0f;
    float toExpandFactor = textLastLineWidth == BOTTOM_LINE_EXPAND_HEIGHT ? 1f : 0f;
    return MathUtils.fromTo(fromExpandFactor, toExpandFactor, linkPreviewAboveText.getFloatValue());
  }

  @Override
  protected int getAnimatedBottomLineWidth (int bubbleTimePartWidth) {
    int textLastLineWidth = calculateTextLastLineWidth();
    int linkPreviewLastLineWidth = linkPreview != null ? linkPreview.getLastLineWidth() : textLastLineWidth;
    float factor = linkPreviewAboveText.getFloatValue();
    if (factor == 1f || linkPreview == null) {
      return textLastLineWidth;
    } else if (factor == 0f) {
      return linkPreviewLastLineWidth;
    }
    int fromLastLineWidth = linkPreviewLastLineWidth == BOTTOM_LINE_EXPAND_HEIGHT ? linkPreview.getWidth() - bubbleTimePartWidth : linkPreviewLastLineWidth;
    int toLastLineWidth = /*textLastLineWidth == BOTTOM_LINE_KEEP_WIDTH ? wrapper.getWidth() - bubbleTimePartWidth : */textLastLineWidth;
    return MathUtils.fromTo(fromLastLineWidth, toLastLineWidth, factor);
  }

  /** TGx101: the text the bubble draws, for the in-bubble selection; null with a link preview above the text */
  @Override
  public @androidx.annotation.Nullable org.thunderdog.challegram.util.text.Text tgx101SelectableText () {
    if (linkPreview != null && linkPreviewAboveText.getFloatValue() != 0f) return null;
    org.thunderdog.challegram.util.text.Text result = null;
    for (ListAnimator.Entry<TextWrapper> entry : visibleText) {
      if (entry.getVisibility() == 1f) result = entry.item.getCurrent();
    }
    return result;
  }

  /** TGx101: where the text is drawn inside the message view — {x, y, width}; null with a link preview above the text or a translation */
  public @androidx.annotation.Nullable int[] tgx101TextFrame () {
    if (linkPreview != null && linkPreviewAboveText.getFloatValue() != 0f) return null;
    // {x, y, width, height, line height} — the overlay matches the line height so it covers the bubble's text exactly
    int lineHeight = 0;
    for (ListAnimator.Entry<TextWrapper> entry : visibleText) {
      lineHeight = entry.item.getLineHeight();
      break;
    }
    return new int[] {getContentX(), getContentY() + getTextTopOffset(), Math.round(visibleText.getMetadata().getTotalWidth()),
      Math.round(visibleText.getMetadata().getTotalHeight()), lineHeight};
  }

  @Override
  protected int getContentWidth () {
    int textWidth = Math.round(visibleText.getMetadata().getTotalWidth());
    if (linkPreview != null) {
      return Math.max(textWidth, linkPreview.getWidth());
    }
    return textWidth;
  }

  private boolean forceExpand = false;

  @Override
  protected void buildReactions (boolean animated) {
    if (linkPreview != null || !useBubble() || visibleText.isEmpty() || !useReactionBubbles() /*|| replyData != null*/) {
      super.buildReactions(animated);
    } else {
      final float maxWidthMultiply = replyData != null ? 1f : 0.7f;
      final float textWidth = Math.max(visibleText.getMetadata().getTotalWidth(), computeBubbleTimePartWidth(false));
      forceExpand = replyData == null && (textWidth < (int)(maxWidth * maxWidthMultiply)) && messageReactions.getBubblesCount() > 1 && messageReactions.getHeight() <= TGReactions.getReactionBubbleHeight();
      messageReactions.measureReactionBubbles(Math.max(Math.round(textWidth), (int) (maxWidth * maxWidthMultiply)), computeBubbleTimePartWidth(true, true));
      messageReactions.resetReactionsAnimator(animated);
    }
  }

  @Override
  public boolean getForceTimeExpandHeightByReactions () {
    return forceExpand;
  }

  public TdApi.LinkPreview getLinkPreview () {
    return linkPreview != null ? linkPreview.getLinkPreview() : null;
  }

  public TGWebPage getParsedLinkPreview () {
    return linkPreview;
  }

  @Override
  public boolean performLongPress (View view, float x, float y) {
    boolean res = super.performLongPress(view, x, y);
    TextWrapper wrapper = effectiveWrapper;
    return (wrapper != null && wrapper.performLongPress(view)) || (linkPreview != null && linkPreview.performLongPress(view, this)) || res;
  }

  @Override
  protected void onMessageContainerDestroyed () {
    visibleText.clear(false);
    if (linkPreview != null) {
      linkPreview.performDestroy();
    }
  }

  // private int touchX, touchY;

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (tgx101ShortTouch(view, e)) {
      return true;
    }
    if (super.onTouchEvent(view, e)) {
      return true;
    }
    TextWrapper wrapper = effectiveWrapper;
    if (wrapper != null && wrapper.onTouchEvent(view, e)) {
      return true;
    }
    return (linkPreview != null && linkPreview.onTouchEvent(view, e, getContentX(), getWebY(), clickCallback()));
  }

  private TdApi.FormattedText translatedText;

  // TGx101 (user 2026-10-21, mockup «ИИ/Кратко-и-Пропущенное» 1): Telegram's own summary of a long post. Under a post
  // Telegram can sum up — the link «✨ Short»; pressed, the summary stands on top in a quote, the start of the post
  // under it and «Show in full». It is the post's own text, so it fits every channel look (bubbles, blocks, journal, classic)
  public static final String TGX101_SUMMARY_LINK = "tgx101-summary:show", TGX101_FULL_LINK = "tgx101-summary:full";
  private @Nullable TdApi.FormattedText tgx101Summary;
  private boolean tgx101SummaryShown, tgx101SummaryLoading;

  @Override
  public boolean tgx101CanSummarize () {
    return Settings.instance().tgx101AiSummary() && !StringUtils.isEmpty(msg.summaryLanguageCode) && translatedText == null && text != null;
  }

  @Override
  public boolean tgx101SummaryShown () {
    return tgx101SummaryShown;
  }

  private static TdApi.TextEntity entity (int offset, int length, TdApi.TextEntityType type) {
    return new TdApi.TextEntity(offset, length, type);
  }

  private static final java.util.Set<Long> tgx101SummaryLogged = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

  private TdApi.FormattedText tgx101WithSummary (TdApi.FormattedText t) {
    if (t.text.length() > 500 && tgx101SummaryLogged.add(msg.chatId ^ msg.id)) {
      org.thunderdog.challegram.Tgx101Diag.mark("ai short: " + msg.chatId + "/" + msg.id + " " + t.text.length() + " chars, summary language '" + msg.summaryLanguageCode + "'");
    }
    if (!tgx101CanSummarize()) return t;
    java.util.List<TdApi.TextEntity> list = new java.util.ArrayList<>();
    StringBuilder b = new StringBuilder();
    if (tgx101SummaryShown && tgx101Summary != null) {
      String head = "✨ " + Lang.getString(R.string.Tgx101AiShort);
      b.append(head).append("\n\n"); // (user 2026-10-21 «нужно больше места» between the title, the text and «Show in full»)
      int sumStart = b.length();
      String sum = tgx101Summary.text.trim();
      b.append(sum);
      list.add(entity(0, head.length(), new TdApi.TextEntityTypeBold()));
      if (tgx101Summary.entities != null) {
        for (TdApi.TextEntity e : tgx101Summary.entities) {
          if (e.offset + e.length <= sum.length()) list.add(entity(sumStart + e.offset, e.length, e.type));
        }
      }
      // (user 2026-10-21 «в офф. телеге пересказ заменяет текст»): the summary takes the post's place, as the translation does
      b.append("\n\n");
      String more = Lang.getString(R.string.Tgx101AiShowFull);
      list.add(entity(b.length(), more.length(), new TdApi.TextEntityTypeTextUrl(TGX101_FULL_LINK)));
      b.append(more);
    } else {
      b.append(t.text);
      if (t.entities != null) java.util.Collections.addAll(list, t.entities);
      b.append('\n');
      String chip = "✨ " + Lang.getString(tgx101SummaryLoading ? R.string.Tgx101AiShortLoading : R.string.Tgx101AiShort);
      list.add(entity(b.length(), chip.length(), new TdApi.TextEntityTypeTextUrl(TGX101_SUMMARY_LINK)));
      b.append(chip);
    }
    return new TdApi.FormattedText(b.toString(), list.toArray(new TdApi.TextEntity[0]));
  }

  @Override
  protected void tgx101OnQuoteToggled () {
    visibleText.measure(false); // the text's height is the folding quote's of this frame
    super.tgx101OnQuoteToggled();
  }

  private void tgx101Rebuild () {
    setText(this.text, false, true, true);
    rebuildAndUpdateContent();
    invalidateTextMediaReceiver();
  }

  @Override
  public boolean tgx101ToggleSummary () {
    if (!tgx101CanSummarize()) return false;
    if (tgx101SummaryShown) {
      tgx101SummaryShown = false;
      tgx101Rebuild();
      return true;
    }
    if (tgx101Summary != null) {
      tgx101SummaryShown = true;
      tgx101Rebuild();
      return true;
    }
    if (tgx101SummaryLoading) return true;
    tgx101SummaryLoading = true;
    tgx101Rebuild();
    String pack = Lang.packId(); String lang = pack != null && pack.length() >= 2 ? pack.substring(0, 2) : ""; // the summary in the app's language
    String to = lang != null && !lang.isEmpty() && !lang.equalsIgnoreCase(msg.summaryLanguageCode) ? lang : "";
    org.thunderdog.challegram.Tgx101Diag.mark("ai short: asked for " + msg.chatId + "/" + msg.id + " (" + msg.summaryLanguageCode + (to.isEmpty() ? "" : " → " + to) + ")");
    tdlib.client().send(new TdApi.SummarizeMessage(msg.chatId, msg.id, to, ""), result -> runOnUiThreadOptional(() -> {
      tgx101SummaryLoading = false;
      if (result.getConstructor() == TdApi.FormattedText.CONSTRUCTOR) {
        tgx101Summary = (TdApi.FormattedText) result;
        tgx101SummaryShown = true;
        org.thunderdog.challegram.Tgx101Diag.mark("ai short: " + tgx101Summary.text.length() + " chars");
      } else {
        org.thunderdog.challegram.Tgx101Diag.mark("ai short: failed " + result);
        org.thunderdog.challegram.tool.UI.showError(result);
      }
      tgx101Rebuild();
    }));
    return true;
  }

  @Nullable
  @Override
  public TdApi.FormattedText getTextToTranslateImpl () {
    return text;
  }

  @Override
  protected void setTranslationResult (@Nullable TdApi.FormattedText text) {
    translatedText = text;
    setText(this.text, false, true, true);
    rebuildAndUpdateContent();
    invalidateTextMediaReceiver();
    super.setTranslationResult(text);
  }
}
