/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/tgx101-android)
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
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import tgx.td.Td;

/**
 * Telegram Stars: paying bot invoices and paid media with Stars, and showing invoices and paid
 * media in chats. Card payments are not supported: they need the official payment UI.
 */
public final class Tgx101Stars {
  private Tgx101Stars () { }

  public static final String CURRENCY_STARS = "XTR";
  public static final String FRAGMENT_URL = "https://fragment.com/stars/buy";

  public static String formatStars (long count) {
    return Strings.buildCounter(count) + " ⭐";
  }

  public static String formatAmount (String currency, long amount) {
    return CURRENCY_STARS.equals(currency) ? formatStars(amount) : CurrencyUtils.buildAmount(currency, amount);
  }

  // Chat messages

  public static TGMessage invoiceMessage (MessagesManager context, TdApi.Message msg, TdApi.MessageInvoice invoice) {
    List<TdApi.TextEntity> entities = new ArrayList<>();
    StringBuilder b = new StringBuilder();
    String title = invoice.productInfo != null ? invoice.productInfo.title : null;
    if (!StringUtils.isEmpty(title)) {
      b.append(title);
      entities.add(new TdApi.TextEntity(0, title.length(), new TdApi.TextEntityTypeBold()));
    }
    TdApi.FormattedText description = invoice.productInfo != null ? invoice.productInfo.description : null;
    if (!Td.isEmpty(description)) {
      if (b.length() > 0) b.append("\n\n");
      int offset = b.length();
      b.append(description.text);
      if (description.entities != null) {
        for (TdApi.TextEntity e : description.entities) {
          entities.add(new TdApi.TextEntity(e.offset + offset, e.length, e.type));
        }
      }
    }
    if (b.length() > 0) b.append("\n\n");
    String price = Lang.getString(invoice.receiptMessageId != 0 ? R.string.InvoicePaid : R.string.InvoicePrice, formatAmount(invoice.currency, invoice.totalAmount));
    entities.add(new TdApi.TextEntity(b.length(), price.length(), new TdApi.TextEntityTypeItalic()));
    b.append(price);
    return new TGMessageText(context, msg, new TdApi.FormattedText(b.toString(), entities.toArray(new TdApi.TextEntity[0])));
  }

  public static TGMessage paidMediaMessage (MessagesManager context, TdApi.Message msg, TdApi.MessagePaidMedia paidMedia) {
    TdApi.MessageContent unlocked = unlockedContent(paidMedia);
    if (unlocked != null) {
      TdApi.Message copy = copyOf(msg);
      if (copy != null) {
        copy.content = unlocked;
        TdApi.FormattedText caption = Td.textOrCaption(unlocked);
        return unlocked.getConstructor() == TdApi.MessageVideo.CONSTRUCTOR ?
          new TGMessageMedia(context, copy, (TdApi.MessageVideo) unlocked, caption) :
          new TGMessageMedia(context, copy, (TdApi.MessagePhoto) unlocked, caption);
      }
    }
    String locked = Lang.getString(R.string.PaidMediaLocked, formatStars(paidMedia.starCount));
    List<TdApi.TextEntity> entities = new ArrayList<>();
    entities.add(new TdApi.TextEntity(0, locked.length(), new TdApi.TextEntityTypeBold()));
    StringBuilder b = new StringBuilder(locked);
    if (!Td.isEmpty(paidMedia.caption)) {
      b.append("\n\n");
      int offset = b.length();
      b.append(paidMedia.caption.text);
      if (paidMedia.caption.entities != null) {
        for (TdApi.TextEntity e : paidMedia.caption.entities) {
          entities.add(new TdApi.TextEntity(e.offset + offset, e.length, e.type));
        }
      }
    }
    return new TGMessageText(context, msg, new TdApi.FormattedText(b.toString(), entities.toArray(new TdApi.TextEntity[0])));
  }

  /** The first bought (or own) item as a normal photo or video message, or null while locked. */
  private static @Nullable TdApi.MessageContent unlockedContent (TdApi.MessagePaidMedia paidMedia) {
    if (paidMedia.media == null || paidMedia.media.length == 0) {
      return null;
    }
    TdApi.FormattedText caption = paidMedia.caption;
    if (paidMedia.media.length > 1) {
      String more = Lang.getString(R.string.PaidMediaPartial, paidMedia.media.length);
      String text = Td.isEmpty(caption) ? more : caption.text + "\n\n" + more;
      List<TdApi.TextEntity> entities = new ArrayList<>();
      if (!Td.isEmpty(caption) && caption.entities != null) {
        for (TdApi.TextEntity e : caption.entities) entities.add(e);
      }
      entities.add(new TdApi.TextEntity(text.length() - more.length(), more.length(), new TdApi.TextEntityTypeItalic()));
      caption = new TdApi.FormattedText(text, entities.toArray(new TdApi.TextEntity[0]));
    } else if (caption == null) {
      caption = new TdApi.FormattedText("", new TdApi.TextEntity[0]);
    }
    TdApi.PaidMedia media = paidMedia.media[0];
    switch (media.getConstructor()) {
      case TdApi.PaidMediaPhoto.CONSTRUCTOR: {
        TdApi.PaidMediaPhoto photo = (TdApi.PaidMediaPhoto) media;
        return new TdApi.MessagePhoto(photo.photo, photo.video, caption, paidMedia.showCaptionAboveMedia, false, false);
      }
      case TdApi.PaidMediaVideo.CONSTRUCTOR: {
        TdApi.PaidMediaVideo video = (TdApi.PaidMediaVideo) media;
        return new TdApi.MessageVideo(video.video, new TdApi.AlternativeVideo[0], new TdApi.VideoStoryboard[0], video.cover, video.startTimestamp, caption, paidMedia.showCaptionAboveMedia, false, false);
      }
    }
    return null;
  }

  private static boolean isLocked (TdApi.MessagePaidMedia paidMedia) {
    if (paidMedia.media == null || paidMedia.media.length == 0) {
      return true;
    }
    int constructor = paidMedia.media[0].getConstructor();
    return constructor != TdApi.PaidMediaPhoto.CONSTRUCTOR && constructor != TdApi.PaidMediaVideo.CONSTRUCTOR;
  }

  /** Buying changes paid media and invoices without changing the content type: rebuild the message. */
  public static boolean needsReplace (TdApi.MessageContent oldContent, TdApi.MessageContent newContent) {
    if (oldContent.getConstructor() != newContent.getConstructor()) {
      return false;
    }
    switch (newContent.getConstructor()) {
      case TdApi.MessagePaidMedia.CONSTRUCTOR:
        return isLocked((TdApi.MessagePaidMedia) oldContent) != isLocked((TdApi.MessagePaidMedia) newContent);
      case TdApi.MessageInvoice.CONSTRUCTOR:
        return ((TdApi.MessageInvoice) oldContent).receiptMessageId != ((TdApi.MessageInvoice) newContent).receiptMessageId;
    }
    return false;
  }

  private static @Nullable TdApi.Message copyOf (TdApi.Message msg) {
    TdApi.Message copy = new TdApi.Message();
    try {
      for (Field field : TdApi.Message.class.getFields()) {
        if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
          field.set(copy, field.get(msg));
        }
      }
    } catch (IllegalAccessException e) {
      return null;
    }
    return copy;
  }

  // Message menu

  /** Menu label for paying for this message, or null when there is nothing to pay. */
  public static @Nullable String payLabel (@Nullable TdApi.Message message) {
    if (message == null || message.content == null) {
      return null;
    }
    switch (message.content.getConstructor()) {
      case TdApi.MessageInvoice.CONSTRUCTOR: {
        TdApi.MessageInvoice invoice = (TdApi.MessageInvoice) message.content;
        return invoice.receiptMessageId == 0 ? Lang.getString(R.string.InvoicePay, formatAmount(invoice.currency, invoice.totalAmount)) : null;
      }
      case TdApi.MessagePaidMedia.CONSTRUCTOR: {
        TdApi.MessagePaidMedia paidMedia = (TdApi.MessagePaidMedia) message.content;
        return isLocked(paidMedia) ? Lang.getString(R.string.PaidMediaUnlock, formatStars(paidMedia.starCount)) : null;
      }
    }
    return null;
  }

  public static void payForMessage (@NonNull ViewController<?> c, @NonNull TdApi.Message message) {
    pay(c, new TdApi.InputInvoiceMessage(message.chatId, message.id));
  }

  // Payment

  public static void requestBalance (Tdlib tdlib, RunnableData<Long> callback) {
    tdlib.send(new TdApi.GetStarTransactions(new TdApi.MessageSenderUser(tdlib.myUserId()), "", null, "", 1), (transactions, error) -> {
      long count = transactions != null && transactions.starAmount != null ? transactions.starAmount.starCount : -1;
      UI.post(() -> callback.runWithData(count));
    });
  }

  public static void pay (@NonNull ViewController<?> c, @NonNull TdApi.InputInvoice invoice) {
    Tdlib tdlib = c.tdlib();
    tdlib.send(new TdApi.GetPaymentForm(invoice, null), (form, error) -> {
      if (error != null) {
        UI.post(() -> UI.showToast(Lang.getString(R.string.StarsPayFailed, TD.toErrorString(error)), Toast.LENGTH_LONG));
        return;
      }
      final long price;
      final boolean isSubscription;
      switch (form.type.getConstructor()) {
        case TdApi.PaymentFormTypeStars.CONSTRUCTOR:
          price = ((TdApi.PaymentFormTypeStars) form.type).starCount;
          isSubscription = false;
          break;
        case TdApi.PaymentFormTypeStarSubscription.CONSTRUCTOR:
          price = ((TdApi.PaymentFormTypeStarSubscription) form.type).pricing.starCount;
          isSubscription = true;
          break;
        case TdApi.PaymentFormTypeRegular.CONSTRUCTOR:
          UI.post(() -> {
            if (!c.isDestroyed()) {
              org.thunderdog.challegram.ui.Tgx101CardPayment.start(c, invoice, form);
            }
          });
          return;
        default:
          UI.post(() -> UI.showToast(R.string.StarsCardUnsupported, Toast.LENGTH_LONG));
          return;
      }
      requestBalance(tdlib, balance -> {
        if (!c.isDestroyed()) {
          confirm(c, invoice, form, price, isSubscription, balance);
        }
      });
    });
  }

  private static void confirm (ViewController<?> c, TdApi.InputInvoice invoice, TdApi.PaymentForm form, long price, boolean isSubscription, long balance) {
    String title = form.productInfo != null && !StringUtils.isEmpty(form.productInfo.title) ? form.productInfo.title : c.tdlib().cache().userName(form.sellerBotUserId);
    String balanceText = balance >= 0 ? formatStars(balance) : "?";
    if (balance >= 0 && balance < price) {
      c.showOptions(Lang.getString(R.string.StarsNotEnough, formatStars(price), balanceText),
        new int[] {R.id.btn_openLink, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.StarsOpenFragment), Lang.getString(R.string.Cancel)},
        new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
        new int[] {R.drawable.baseline_open_in_browser_24, R.drawable.baseline_cancel_24},
        (itemView, id) -> {
          if (id == R.id.btn_openLink) {
            c.tdlib().ui().openUrl(c, FRAGMENT_URL, null);
          }
          return true;
        });
      return;
    }
    String info = Lang.getString(isSubscription ? R.string.StarsSubscribeConfirm : R.string.StarsPayConfirm, title, formatStars(price), balanceText);
    c.showOptions(info,
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.InvoicePay, formatStars(price)), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_check_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_done) {
          send(c, invoice, form);
        }
        return true;
      });
  }

  private static void send (ViewController<?> c, TdApi.InputInvoice invoice, TdApi.PaymentForm form) {
    c.tdlib().send(new TdApi.SendPaymentForm(invoice, form.id, "", "", null, 0), (result, error) -> UI.post(() -> {
      if (error != null) {
        UI.showToast(Lang.getString(R.string.StarsPayFailed, TD.toErrorString(error)), Toast.LENGTH_LONG);
      } else if (!StringUtils.isEmpty(result.verificationUrl)) {
        c.tdlib().ui().openUrl(c, result.verificationUrl, null);
      } else {
        UI.showToast(R.string.StarsPaid, Toast.LENGTH_SHORT);
      }
    }));
  }

  // Paid reactions: support a channel post with Stars

  private static final int[] PAID_REACTION_AMOUNTS = {1, 5, 10, 50, 100};

  public static void sendPaidReaction (@NonNull ViewController<?> c, long chatId, long messageId) {
    requestBalance(c.tdlib(), balance -> {
      if (c.isDestroyed()) {
        return;
      }
      int[] ids = new int[PAID_REACTION_AMOUNTS.length + 1];
      String[] titles = new String[ids.length];
      int[] colors = new int[ids.length];
      int[] icons = new int[ids.length];
      for (int i = 0; i < PAID_REACTION_AMOUNTS.length; i++) {
        ids[i] = PAID_REACTION_AMOUNTS[i];
        titles[i] = formatStars(PAID_REACTION_AMOUNTS[i]);
        colors[i] = ViewController.OptionColor.NORMAL;
        icons[i] = R.drawable.baseline_star_24;
      }
      ids[ids.length - 1] = R.id.btn_cancel;
      titles[ids.length - 1] = Lang.getString(R.string.Cancel);
      colors[ids.length - 1] = ViewController.OptionColor.NORMAL;
      icons[ids.length - 1] = R.drawable.baseline_cancel_24;
      c.showOptions(Lang.getString(R.string.PaidReactionHint, balance >= 0 ? formatStars(balance) : "?"), ids, titles, colors, icons, (itemView, id) -> {
        if (id == R.id.btn_cancel) {
          return true;
        }
        if (balance >= 0 && id > balance) {
          c.showOptions(Lang.getString(R.string.StarsNotEnough, formatStars(id), formatStars(balance)),
            new int[] {R.id.btn_openLink, R.id.btn_cancel},
            new String[] {Lang.getString(R.string.StarsOpenFragment), Lang.getString(R.string.Cancel)},
            new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
            new int[] {R.drawable.baseline_open_in_browser_24, R.drawable.baseline_cancel_24},
            (v, which) -> {
              if (which == R.id.btn_openLink) {
                c.tdlib().ui().openUrl(c, FRAGMENT_URL, null);
              }
              return true;
            });
          return true;
        }
        final int count = id;
        c.tdlib().send(new TdApi.AddPendingPaidMessageReaction(chatId, messageId, count, new TdApi.PaidReactionTypeRegular()), (ok, error) -> {
          if (error != null) {
            UI.post(() -> UI.showToast(Lang.getString(R.string.StarsPayFailed, TD.toErrorString(error)), Toast.LENGTH_LONG));
            return;
          }
          c.tdlib().send(new TdApi.CommitPendingPaidMessageReactions(chatId, messageId), (ok2, error2) -> UI.post(() -> {
            if (error2 != null) {
              UI.showToast(Lang.getString(R.string.StarsPayFailed, TD.toErrorString(error2)), Toast.LENGTH_LONG);
            } else {
              UI.showToast(Lang.getString(R.string.PaidReactionSent, formatStars(count)), Toast.LENGTH_SHORT);
            }
          }));
        });
        return true;
      });
    });
  }
}
