/*
 * This file is a part of PlumaGram
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.lambda.RunnableData;
import tgx.td.MessageId;

/** TGx101: what the Saved screens know about a saved message, and how they load them */
public final class Tgx101SavedKit {
  private Tgx101SavedKit () { }

  public static final int KIND_NOTE = 0, KIND_PHOTO = 1, KIND_VIDEO = 2, KIND_FILE = 3, KIND_LINK = 4, KIND_VOICE = 5, KIND_OTHER = 6;

  public static int kindOf (TdApi.Message m) {
    switch (m.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: return KIND_PHOTO;
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessageAnimation.CONSTRUCTOR:
      case TdApi.MessageVideoNote.CONSTRUCTOR: return KIND_VIDEO;
      case TdApi.MessageDocument.CONSTRUCTOR:
      case TdApi.MessageAudio.CONSTRUCTOR: return KIND_FILE;
      case TdApi.MessageVoiceNote.CONSTRUCTOR: return KIND_VOICE;
      case TdApi.MessageText.CONSTRUCTOR: return url(m) != null ? KIND_LINK : KIND_NOTE;
    }
    return KIND_OTHER;
  }

  public static String kindLabel (int kind) {
    switch (kind) {
      case KIND_PHOTO: return Lang.getString(R.string.Tgx101SavedKindPhoto);
      case KIND_VIDEO: return Lang.getString(R.string.Tgx101SavedKindVideo);
      case KIND_FILE: return Lang.getString(R.string.Tgx101SavedKindFile);
      case KIND_LINK: return Lang.getString(R.string.Tgx101SavedKindLink);
      case KIND_VOICE: return Lang.getString(R.string.Tgx101SavedKindVoice);
      case KIND_NOTE: return Lang.getString(R.string.Tgx101SavedKindNote);
    }
    return Lang.getString(R.string.Tgx101SavedKindOther);
  }

  public static @Nullable TdApi.FormattedText text (TdApi.Message m) {
    switch (m.content.getConstructor()) {
      case TdApi.MessageText.CONSTRUCTOR: return ((TdApi.MessageText) m.content).text;
      case TdApi.MessagePhoto.CONSTRUCTOR: return ((TdApi.MessagePhoto) m.content).caption;
      case TdApi.MessageVideo.CONSTRUCTOR: return ((TdApi.MessageVideo) m.content).caption;
      case TdApi.MessageAnimation.CONSTRUCTOR: return ((TdApi.MessageAnimation) m.content).caption;
      case TdApi.MessageDocument.CONSTRUCTOR: return ((TdApi.MessageDocument) m.content).caption;
      case TdApi.MessageAudio.CONSTRUCTOR: return ((TdApi.MessageAudio) m.content).caption;
      case TdApi.MessageVoiceNote.CONSTRUCTOR: return ((TdApi.MessageVoiceNote) m.content).caption;
    }
    return null;
  }

  public static @Nullable String url (TdApi.Message m) {
    if (m.content.getConstructor() != TdApi.MessageText.CONSTRUCTOR) return null;
    TdApi.MessageText t = (TdApi.MessageText) m.content;
    if (t.linkPreview != null && t.linkPreview.url != null && !t.linkPreview.url.isEmpty()) return t.linkPreview.url;
    if (t.text.entities != null) {
      for (TdApi.TextEntity e : t.text.entities) {
        if (e.type.getConstructor() == TdApi.TextEntityTypeTextUrl.CONSTRUCTOR) return ((TdApi.TextEntityTypeTextUrl) e.type).url;
        if (e.type.getConstructor() == TdApi.TextEntityTypeUrl.CONSTRUCTOR) {
          int end = Math.min(t.text.text.length(), e.offset + e.length);
          if (e.offset >= 0 && e.offset < end) return t.text.text.substring(e.offset, end);
        }
      }
    }
    return null;
  }

  public static @Nullable TdApi.LinkPreview linkPreview (TdApi.Message m) {
    return m.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR ? ((TdApi.MessageText) m.content).linkPreview : null;
  }

  public static String site (TdApi.Message m) {
    TdApi.LinkPreview p = linkPreview(m);
    if (p != null && p.siteName != null && !p.siteName.isEmpty()) return p.siteName;
    String u = url(m);
    if (u == null) return "";
    try {
      String host = android.net.Uri.parse(u.contains("://") ? u : "https://" + u).getHost();
      if (host == null) return u;
      return host.startsWith("www.") ? host.substring(4) : host;
    } catch (Throwable t) {
      return u;
    }
  }

  private static String firstLine (@Nullable String s) {
    if (s == null) return "";
    s = s.trim();
    int nl = s.indexOf('\n');
    return nl >= 0 ? s.substring(0, nl).trim() : s;
  }

  /** The row's main line: link title, file name, the text's first line, or the kind */
  public static String title (TdApi.Message m) {
    TdApi.LinkPreview p = linkPreview(m);
    if (p != null && p.title != null && !p.title.isEmpty()) return p.title;
    switch (m.content.getConstructor()) {
      case TdApi.MessageDocument.CONSTRUCTOR: {
        String name = ((TdApi.MessageDocument) m.content).document.fileName;
        if (name != null && !name.isEmpty()) return name;
        break;
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        TdApi.Audio a = ((TdApi.MessageAudio) m.content).audio;
        if (a.title != null && !a.title.isEmpty()) return (a.performer != null && !a.performer.isEmpty() ? a.performer + " — " : "") + a.title;
        if (a.fileName != null && !a.fileName.isEmpty()) return a.fileName;
        break;
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return Lang.getString(R.string.Tgx101SavedVoiceTitle, org.thunderdog.challegram.tool.Strings.buildDuration(((TdApi.MessageVoiceNote) m.content).voiceNote.duration));
    }
    TdApi.FormattedText t = text(m);
    String line = t != null ? firstLine(t.text) : "";
    return !line.isEmpty() ? line : kindLabel(kindOf(m));
  }

  public static long size (TdApi.Message m) {
    TdApi.File f = mainFile(m);
    return f != null ? Math.max(f.size, f.expectedSize) : (text(m) != null ? text(m).text.length() : 0);
  }

  public static @Nullable TdApi.File mainFile (TdApi.Message m) {
    switch (m.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.PhotoSize[] sizes = ((TdApi.MessagePhoto) m.content).photo.sizes;
        return sizes.length > 0 ? sizes[sizes.length - 1].photo : null;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: return ((TdApi.MessageVideo) m.content).video.video;
      case TdApi.MessageAnimation.CONSTRUCTOR: return ((TdApi.MessageAnimation) m.content).animation.animation;
      case TdApi.MessageDocument.CONSTRUCTOR: return ((TdApi.MessageDocument) m.content).document.document;
      case TdApi.MessageAudio.CONSTRUCTOR: return ((TdApi.MessageAudio) m.content).audio.audio;
      case TdApi.MessageVoiceNote.CONSTRUCTOR: return ((TdApi.MessageVoiceNote) m.content).voiceNote.voice;
      case TdApi.MessageVideoNote.CONSTRUCTOR: return ((TdApi.MessageVideoNote) m.content).videoNote.video;
    }
    return null;
  }

  /** Where it was saved from: the original chat or author, or «My notes» */
  public static String source (Tdlib tdlib, TdApi.Message m) {
    if (m.forwardInfo == null || m.forwardInfo.origin == null) return Lang.getString(R.string.Tgx101SavedMyNotes);
    TdApi.MessageOrigin o = m.forwardInfo.origin;
    switch (o.getConstructor()) {
      case TdApi.MessageOriginUser.CONSTRUCTOR: return tdlib.cache().userName(((TdApi.MessageOriginUser) o).senderUserId);
      case TdApi.MessageOriginChat.CONSTRUCTOR: return tdlib.chatTitle(((TdApi.MessageOriginChat) o).senderChatId);
      case TdApi.MessageOriginChannel.CONSTRUCTOR: return tdlib.chatTitle(((TdApi.MessageOriginChannel) o).chatId);
      case TdApi.MessageOriginHiddenUser.CONSTRUCTOR: return ((TdApi.MessageOriginHiddenUser) o).senderName;
    }
    return "";
  }

  /** The original date (a forwarded post's own date), else the date it was saved */
  public static int originalDate (TdApi.Message m) {
    return m.forwardInfo != null && m.forwardInfo.date != 0 ? m.forwardInfo.date : m.date;
  }

  /** «2,4 МБ · from «Сети» · 12:40» */
  public static String meta (Tdlib tdlib, TdApi.Message m) {
    StringBuilder b = new StringBuilder();
    int kind = kindOf(m);
    if (kind == KIND_LINK) {
      b.append(site(m));
    } else if (kind == KIND_FILE || kind == KIND_VIDEO) {
      long size = size(m);
      if (size > 0) b.append(org.thunderdog.challegram.tool.Strings.buildSize(size));
    } else if (kind == KIND_PHOTO || kind == KIND_VOICE || kind == KIND_OTHER || kind == KIND_NOTE) {
      b.append(kindLabel(kind));
    }
    if (m.forwardInfo != null) {
      String src = source(tdlib, m);
      if (!src.isEmpty()) {
        if (b.length() > 0) b.append(" · ");
        b.append(src);
      }
    }
    if (b.length() > 0) b.append(" · ");
    b.append(Lang.getRelativeTimestamp(m.date, java.util.concurrent.TimeUnit.SECONDS));
    return b.toString();
  }

  public static boolean isOwnEditable (TdApi.Message m) {
    return m.forwardInfo == null && m.isOutgoing && m.importInfo == null && (m.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR || text(m) != null);
  }

  // Thumbnails

  public static @Nullable TdApi.File thumbFile (TdApi.Message m) {
    switch (m.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: return smallPhoto(((TdApi.MessagePhoto) m.content).photo);
      case TdApi.MessageVideo.CONSTRUCTOR: return jpegThumb(((TdApi.MessageVideo) m.content).video.thumbnail);
      case TdApi.MessageAnimation.CONSTRUCTOR: return jpegThumb(((TdApi.MessageAnimation) m.content).animation.thumbnail);
      case TdApi.MessageVideoNote.CONSTRUCTOR: return jpegThumb(((TdApi.MessageVideoNote) m.content).videoNote.thumbnail);
      case TdApi.MessageDocument.CONSTRUCTOR: return jpegThumb(((TdApi.MessageDocument) m.content).document.thumbnail);
      case TdApi.MessageText.CONSTRUCTOR: {
        TdApi.LinkPreview p = ((TdApi.MessageText) m.content).linkPreview;
        if (p == null) return null;
        switch (p.type.getConstructor()) {
          case TdApi.LinkPreviewTypeArticle.CONSTRUCTOR: return smallPhoto(((TdApi.LinkPreviewTypeArticle) p.type).photo);
          case TdApi.LinkPreviewTypePhoto.CONSTRUCTOR: return smallPhoto(((TdApi.LinkPreviewTypePhoto) p.type).photo);
        }
        return null;
      }
    }
    return null;
  }

  public static @Nullable TdApi.Minithumbnail minithumb (TdApi.Message m) {
    switch (m.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: return ((TdApi.MessagePhoto) m.content).photo.minithumbnail;
      case TdApi.MessageVideo.CONSTRUCTOR: return ((TdApi.MessageVideo) m.content).video.minithumbnail;
      case TdApi.MessageAnimation.CONSTRUCTOR: return ((TdApi.MessageAnimation) m.content).animation.minithumbnail;
      case TdApi.MessageDocument.CONSTRUCTOR: return ((TdApi.MessageDocument) m.content).document.minithumbnail;
    }
    return null;
  }

  private static @Nullable TdApi.File smallPhoto (@Nullable TdApi.Photo photo) {
    if (photo == null || photo.sizes.length == 0) return null;
    TdApi.PhotoSize best = null;
    for (TdApi.PhotoSize s : photo.sizes) {
      if (best == null || (Math.max(s.width, s.height) >= 240 && Math.max(s.width, s.height) < Math.max(best.width, best.height)) || Math.max(best.width, best.height) < 240) best = s;
    }
    return best != null ? best.photo : null;
  }

  private static @Nullable TdApi.File jpegThumb (@Nullable TdApi.Thumbnail t) {
    return t != null && t.format.getConstructor() == TdApi.ThumbnailFormatJpeg.CONSTRUCTOR ? t.file : null;
  }

  private static final android.util.LruCache<String, Bitmap> thumbs = new android.util.LruCache<String, Bitmap>(12 * 1024 * 1024) { // key: account + file (file ids repeat across accounts)
    @Override
    protected int sizeOf (String key, Bitmap value) {
      return value.getByteCount();
    }
  };

  /** Sets the message's thumbnail on {@code view}: the tiny blurred preview at once, then the real one when it's here */
  public static void loadThumb (Tdlib tdlib, ImageView view, TdApi.Message m, int targetPx) {
    TdApi.File file = thumbFile(m);
    final Object tag = file != null ? (Object) (tdlib.id() + ":" + file.id) : (Object) ("m" + m.id);
    view.setTag(R.id.tgx101SavedThumb, tag);
    Bitmap cached = file != null ? thumbs.get(tdlib.id() + ":" + file.id) : null;
    if (cached != null) {
      view.setImageBitmap(cached);
      return;
    }
    TdApi.Minithumbnail mini = minithumb(m);
    if (mini != null && mini.data != null) {
      try {
        view.setImageBitmap(BitmapFactory.decodeByteArray(mini.data, 0, mini.data.length));
      } catch (Throwable ignored) {
        view.setImageDrawable(null);
      }
    } else {
      view.setImageDrawable(null);
    }
    if (file == null) return;
    tdlib.send(new TdApi.DownloadFile(file.id, 8, 0, 0, true), (f, error) -> {
      if (f == null || !f.local.isDownloadingCompleted) return;
      org.thunderdog.challegram.core.Media.instance().post(() -> {
        Bitmap b = decode(f.local.path, targetPx);
        if (b == null) return;
        thumbs.put(tdlib.id() + ":" + f.id, b);
        UI.post(() -> {
          if (tag.equals(view.getTag(R.id.tgx101SavedThumb))) view.setImageBitmap(b);
        });
      });
    });
  }

  private static @Nullable Bitmap decode (String path, int target) {
    try {
      BitmapFactory.Options o = new BitmapFactory.Options();
      o.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(path, o);
      int sample = 1;
      while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= target) sample *= 2;
      o.inJustDecodeBounds = false;
      o.inSampleSize = sample;
      return BitmapFactory.decodeFile(path, o);
    } catch (Throwable t) {
      return null;
    }
  }

  // Loading

  /** Saved messages matching a filter / query, newest first, up to {@code max} (pages of 100) */
  public static void search (Tdlib tdlib, @Nullable TdApi.SearchMessagesFilter filter, @Nullable String query, int max, RunnableData<List<TdApi.Message>> after) {
    List<TdApi.Message> out = new ArrayList<>();
    searchPage(tdlib, filter, query, 0, max, out, after);
  }

  private static void searchPage (Tdlib tdlib, @Nullable TdApi.SearchMessagesFilter filter, @Nullable String query, long from, int max, List<TdApi.Message> out, RunnableData<List<TdApi.Message>> after) {
    tdlib.send(new TdApi.SearchChatMessages(tdlib.selfChatId(), null, query != null ? query : "", null, from, 0, 100, filter), (found, error) -> {
      if (found == null || found.messages.length == 0) {
        UI.post(() -> after.runWithData(out));
        return;
      }
      for (TdApi.Message m : found.messages) out.add(m);
      if (out.size() >= max || found.nextFromMessageId == 0) {
        UI.post(() -> after.runWithData(out));
      } else {
        searchPage(tdlib, filter, query, found.nextFromMessageId, max, out, after);
      }
    });
  }

  /** The whole Saved history, newest first, up to {@code max} */
  public static void history (Tdlib tdlib, int max, RunnableData<List<TdApi.Message>> after) {
    List<TdApi.Message> out = new ArrayList<>();
    historyPage(tdlib, 0, max, out, after, 0);
  }

  private static void historyPage (Tdlib tdlib, long from, int max, List<TdApi.Message> out, RunnableData<List<TdApi.Message>> after, int emptyTries) {
    tdlib.send(new TdApi.GetChatHistory(tdlib.selfChatId(), from, 0, 100, false), (messages, error) -> {
      if (messages == null || messages.messages.length == 0) {
        // the first answer may be empty while the history loads from the server: one more try
        if (messages != null && emptyTries < 1 && out.isEmpty()) {
          historyPage(tdlib, from, max, out, after, emptyTries + 1);
        } else {
          UI.post(() -> after.runWithData(out));
        }
        return;
      }
      long last = from;
      for (TdApi.Message m : messages.messages) {
        if (m == null || m.id == from) continue;
        out.add(m);
        last = m.id;
      }
      if (out.size() >= max || last == from) {
        UI.post(() -> after.runWithData(out));
      } else {
        historyPage(tdlib, last, max, out, after, 0);
      }
    });
  }

  public static void byIds (Tdlib tdlib, List<Long> ids, RunnableData<List<TdApi.Message>> after) {
    if (ids.isEmpty()) {
      UI.post(() -> after.runWithData(new ArrayList<>()));
      return;
    }
    long[] arr = new long[ids.size()];
    for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
    tdlib.send(new TdApi.GetMessages(tdlib.selfChatId(), arr), (messages, error) -> {
      List<TdApi.Message> out = new ArrayList<>();
      if (messages != null) for (TdApi.Message m : messages.messages) if (m != null) out.add(m);
      java.util.Collections.sort(out, (a, b) -> Long.compare(b.id, a.id));
      UI.post(() -> after.runWithData(out));
    });
  }

  public static void count (Tdlib tdlib, TdApi.SearchMessagesFilter filter, RunnableData<Integer> after) {
    tdlib.send(new TdApi.GetChatMessageCount(tdlib.selfChatId(), null, filter, false), (count, error) -> UI.post(() -> after.runWithData(count != null ? count.count : -1)));
  }

  /** Opens Saved Messages as the usual chat, at this message */
  public static void openInFeed (ViewController<?> c, @Nullable TdApi.Message m) {
    TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters().keepStack().tgx101Feed();
    if (m != null) params.highlightMessage(new MessageId(m.chatId, m.id)).ensureHighlightAvailable();
    c.tdlib().ui().openChat(c, c.tdlib().selfChatId(), params);
  }
}
