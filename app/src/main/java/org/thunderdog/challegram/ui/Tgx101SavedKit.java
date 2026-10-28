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
import org.thunderdog.challegram.data.Tgx101SavedCloud;
import org.thunderdog.challegram.data.Tgx101SavedStore;
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

  /** Sites put «&laquo;» and the like into their previews as text (test 2026-10-15): shown decoded */
  public static String unescape (@Nullable String s) {
    if (s == null || s.indexOf('&') < 0) return s;
    s = s.replace("&laquo;", "«").replace("&raquo;", "»").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
      .replace("&nbsp;", " ").replace("&mdash;", "—").replace("&ndash;", "–").replace("&hellip;", "…").replace("&lt;", "<").replace("&gt;", ">");
    java.util.regex.Matcher num = java.util.regex.Pattern.compile("&#(\\d{2,6});").matcher(s);
    StringBuffer b = new StringBuffer();
    while (num.find()) {
      int code;
      try {
        code = Integer.parseInt(num.group(1));
      } catch (NumberFormatException e) {
        code = '?';
      }
      num.appendReplacement(b, java.util.regex.Matcher.quoteReplacement(new String(Character.toChars(code))));
    }
    num.appendTail(b);
    return b.toString().replace("&amp;", "&");
  }

  /** The row's main line: link title, file name, the text's first line, or the kind */
  public static String title (TdApi.Message m) {
    TdApi.LinkPreview p = linkPreview(m);
    if (p != null && p.title != null && !p.title.isEmpty()) return unescape(p.title);
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

  /** (user 2026-10-17 «дату на всех заметках — шрифт жирнее»): the meta line with its date in the medium weight */
  public static CharSequence metaStyled (Tdlib tdlib, TdApi.Message m, boolean withDate) {
    String text = meta(tdlib, m, withDate);
    if (!withDate) return text;
    String date = shortDate(m.date);
    int at = text.lastIndexOf(date);
    if (at < 0) return text;
    android.text.SpannableString s = new android.text.SpannableString(text);
    s.setSpan(new org.thunderdog.challegram.util.CustomTypefaceSpan(org.thunderdog.challegram.tool.Fonts.getRobotoMedium(), 0), at, at + date.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return s;
  }

  /** The original date (a forwarded post's own date), else the date it was saved */
  public static int originalDate (TdApi.Message m) {
    return m.forwardInfo != null && m.forwardInfo.date != 0 ? m.forwardInfo.date : m.date;
  }

  /** «2,4 МБ · from «Сети» · 12:40» */
  public static String meta (Tdlib tdlib, TdApi.Message m) {
    return meta(tdlib, m, true);
  }

  /** {@code withDate} false: the lists show the date in their day headers (approved mockup «Метки на записях», 3) */
  public static String meta (Tdlib tdlib, TdApi.Message m, boolean withDate) {
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
    if (!withDate) return b.toString();
    if (b.length() > 0) b.append(" · ");
    b.append(shortDate(m.date));
    return b.toString();
  }

  /** (user 2026-10-16 «дату и месяц цифрами»): «16.10», with the year when it isn't this one («16.10.25») */
  public static String shortDate (int unixTime) {
    java.util.Calendar c = java.util.Calendar.getInstance();
    int year = c.get(java.util.Calendar.YEAR);
    c.setTimeInMillis(unixTime * 1000L);
    String s = String.format(java.util.Locale.ROOT, "%02d.%02d", c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1);
    return c.get(java.util.Calendar.YEAR) == year ? s : s + "." + String.format(java.util.Locale.ROOT, "%02d", c.get(java.util.Calendar.YEAR) % 100);
  }

  /** The first tag: a #hashtag in the text, else one given by name here; without «#», up to five letters */
  public static @Nullable String firstTagShort (Tdlib tdlib, TdApi.Message m) {
    String tag = null;
    TdApi.FormattedText text = text(m);
    if (text != null && text.entities != null) {
      for (TdApi.TextEntity e : text.entities) {
        if (e.type.getConstructor() == TdApi.TextEntityTypeHashtag.CONSTRUCTOR && e.offset + e.length <= text.text.length()) {
          tag = text.text.substring(e.offset, e.offset + e.length);
          break;
        }
      }
    }
    if (tag == null) {
      List<String> local = Tgx101SavedStore.get(tdlib).tagsOf(m.id);
      if (!local.isEmpty()) tag = local.get(0);
    }
    if (tag == null) return null;
    while (tag.startsWith("#")) tag = tag.substring(1);
    if (tag.isEmpty()) return null;
    return tag.codePointCount(0, tag.length()) > 5 ? tag.substring(0, tag.offsetByCodePoints(0, 5)) : tag;
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
          if (!tag.equals(view.getTag(R.id.tgx101SavedThumb))) return;
          // (user 2026-10-17 «экран мигает на „Фото и видео“»): the blurred preview melts into the photo, no pop
          android.graphics.drawable.Drawable before = view.getDrawable();
          if (before == null || !view.isShown()) {
            view.setImageBitmap(b);
            return;
          }
          android.graphics.drawable.TransitionDrawable melt = new android.graphics.drawable.TransitionDrawable(new android.graphics.drawable.Drawable[] {
            before, new android.graphics.drawable.BitmapDrawable(view.getResources(), b)
          });
          melt.setCrossFadeEnabled(true);
          view.setImageDrawable(melt);
          melt.startTransition(160);
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
      for (TdApi.Message m : found.messages) if (!Tgx101SavedCloud.isCloudFile(m)) out.add(m); // the folders' service file
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
        last = m.id;
        if (Tgx101SavedCloud.isCloudFile(m)) continue; // the folders' service file
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
      if (messages != null) for (TdApi.Message m : messages.messages) if (m != null && !Tgx101SavedCloud.isCloudFile(m)) out.add(m);
      if (messages != null && messages.messages.length == arr.length) {
        // (user 2026-10-16 «папки пустые, но счётчик не сбросился»): Telegram has no such message any more (deleted on
        // another device, or while the app was closed) — it leaves the folders, tags and reactions here too
        List<Long> gone = new ArrayList<>();
        for (int i = 0; i < arr.length; i++) if (messages.messages[i] == null && (arr[i] & 0xFFFFF) == 0) gone.add(arr[i]);
        if (!gone.isEmpty()) {
          long[] g = new long[gone.size()];
          for (int i = 0; i < g.length; i++) g[i] = gone.get(i);
          org.thunderdog.challegram.Tgx101Diag.mark("saved store: " + g.length + " gone from Telegram, forgotten here");
          Tgx101SavedStore.get(tdlib).forgetGone(g);
        }
      }
      java.util.Collections.sort(out, (a, b) -> Long.compare(b.id, a.id));
      UI.post(() -> after.runWithData(out));
    });
  }

  /**
   * What a «Saved» banner announced, as Telegram has it now. The banner gets the ids a message has while it's being
   * sent, and the «sent» update doesn't always reach us (test 2026-10-15: the forward to Saved): what isn't found by
   * id is taken from the newest messages saved since the banner appeared.
   */
  public static void resolveSaved (Tdlib tdlib, long[] ids, int sinceDate, RunnableData<List<TdApi.Message>> after) {
    List<Long> list = new ArrayList<>();
    for (long id : ids) list.add(id);
    byIds(tdlib, list, found -> {
      if (found.size() >= ids.length) {
        after.runWithData(found);
        return;
      }
      tdlib.send(new TdApi.GetChatHistory(tdlib.selfChatId(), 0, 0, Math.max(20, ids.length * 2), false), (history, error) -> {
        List<TdApi.Message> out = new ArrayList<>(found);
        if (history != null) {
          for (TdApi.Message m : history.messages) {
            if (out.size() >= ids.length) break;
            if (m == null || m.date < sinceDate - 10 || m.sendingState != null || m.schedulingState != null) continue;
            boolean dup = false;
            for (TdApi.Message o : out) if (o.id == m.id) dup = true;
            if (!dup) out.add(m);
          }
        }
        java.util.Collections.sort(out, (a, b) -> Long.compare(b.id, a.id));
        UI.post(() -> after.runWithData(out));
      });
    });
  }

  static long[] idsOf (List<TdApi.Message> messages) {
    long[] out = new long[messages.size()];
    for (int i = 0; i < out.length; i++) out[i] = messages.get(i).id;
    return out;
  }

  // Reactions as tags (user 2026-10-15 «реакции как в офф. клиенте»)

  /** the quick row; «+» opens the rest */
  // only Telegram's own reactions (test 2026-10-16: «❤️» with the variation sign and «⭐» were refused); ⚡ stands
  // for the star of the mockup
  /** (user 2026-10-16 «реакции — в обычном стиле Telegram»): reactions drawn with the app's own emoji, as in the chats, not the phone's font */
  public static CharSequence emoji (@Nullable CharSequence s) {
    // the storage's texts are EmojiTextViews: they draw the app's emoji themselves and redraw them once loaded
    // (replaced spans in a plain TextView stayed blank until something redrew it)
    return s;
  }

  public static final String[] QUICK_REACTIONS = {"❤", "🔥", "👍", "⚡"};
  public static final String[] MORE_REACTIONS = {"😁", "🤔", "🎉", "💯", "😢", "🙏", "👎", "😍", "🏆", "💔", "👀", "🤝", "✍", "🆒", "😎", "🤯"};

  /** Telegram's reaction tags in Saved Messages are a Premium feature: without it they are kept on this phone */
  public static boolean serverReactions (Tdlib tdlib) {
    return tdlib.hasPremium();
  }

  /** The reactions this message carries — Telegram's tags and the ones kept here */
  public static List<String> reactionsOf (Tdlib tdlib, TdApi.Message m) {
    List<String> out = new ArrayList<>();
    if (m.interactionInfo != null && m.interactionInfo.reactions != null) {
      for (TdApi.MessageReaction r : m.interactionInfo.reactions.reactions) {
        if (r.isChosen && r.type.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR) out.add(((TdApi.ReactionTypeEmoji) r.type).emoji);
      }
    }
    for (String e : Tgx101SavedStore.get(tdlib).reactionsOf(m.id)) if (!out.contains(e)) out.add(e);
    return out;
  }

  /**
   * Puts the reaction on all of them; if all already have it, takes it off. {@code done} gets whether it was added.
   */
  public static void toggleReaction (Tdlib tdlib, List<TdApi.Message> messages, String emoji, @Nullable RunnableData<Boolean> done) {
    if (messages.isEmpty()) return;
    boolean all = true;
    for (TdApi.Message m : messages) if (!reactionsOf(tdlib, m).contains(emoji)) all = false;
    final boolean add = !all;
    if (!serverReactions(tdlib)) {
      long[] ids = idsOf(messages);
      Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
      // the store toggles by its own set: bring it to the wanted state
      boolean added = store.toggleLocalReaction(emoji, ids);
      if (added != add) store.toggleLocalReaction(emoji, ids);
      if (done != null) UI.post(() -> done.runWithData(add));
      return;
    }
    TdApi.ReactionType type = new TdApi.ReactionTypeEmoji(emoji);
    Tgx101SavedStore localStore = Tgx101SavedStore.get(tdlib);
    for (TdApi.Message m : messages) {
      // one kept here goes with the server one (test 2026-10-16: a removal «succeeded» on Telegram, the local stayed)
      if (!add && localStore.reactionsOf(m.id).contains(emoji)) localStore.toggleLocalReaction(emoji, new long[] {m.id});
      boolean has = reactionsOf(tdlib, m).contains(emoji);
      if (add == has) continue;
      TdApi.Function<TdApi.Ok> f = add ? new TdApi.AddMessageReaction(m.chatId, m.id, type, false, false) : new TdApi.RemoveMessageReaction(m.chatId, m.id, type);
      tdlib.send(f, (ok, error) -> {
        if (error != null) {
          org.thunderdog.challegram.Tgx101Diag.mark("saved reaction: " + error.message);
          // refused by Telegram: an added one is kept here instead
          if (add && !localStore.reactionsOf(m.id).contains(emoji)) localStore.toggleLocalReaction(emoji, new long[] {m.id});
        }
      });
    }
    if (done != null) UI.post(() -> done.runWithData(add));
  }

  /** emoji → count, Telegram's tags first, then the ones kept here */
  public static void reactionCounts (Tdlib tdlib, RunnableData<java.util.LinkedHashMap<String, Integer>> after) {
    java.util.LinkedHashMap<String, Integer> local = Tgx101SavedStore.get(tdlib).localReactionCounts();
    if (!serverReactions(tdlib)) {
      UI.post(() -> after.runWithData(local));
      return;
    }
    tdlib.send(new TdApi.GetSavedMessagesTags(0), (tags, error) -> {
      java.util.LinkedHashMap<String, Integer> out = new java.util.LinkedHashMap<>();
      if (tags != null) {
        for (TdApi.SavedMessagesTag t : tags.tags) {
          if (t.tag.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR) out.put(((TdApi.ReactionTypeEmoji) t.tag).emoji, t.count);
        }
      }
      for (java.util.Map.Entry<String, Integer> e : local.entrySet()) {
        Integer c = out.get(e.getKey());
        out.put(e.getKey(), (c != null ? c : 0) + e.getValue());
      }
      UI.post(() -> after.runWithData(out));
    });
  }

  /** Everything marked with this reaction, newest first */
  public static void searchReaction (Tdlib tdlib, String emoji, RunnableData<List<TdApi.Message>> after) {
    List<Long> local = Tgx101SavedStore.get(tdlib).localReacted(emoji);
    if (!serverReactions(tdlib)) {
      byIds(tdlib, local, after);
      return;
    }
    tdlib.send(new TdApi.SearchSavedMessages(0, new TdApi.ReactionTypeEmoji(emoji), "", 0, 0, 100), (found, error) -> byIds(tdlib, local, more -> {
      java.util.LinkedHashMap<Long, TdApi.Message> map = new java.util.LinkedHashMap<>();
      if (found != null) for (TdApi.Message m : found.messages) if (m != null) map.put(m.id, m);
      for (TdApi.Message m : more) map.put(m.id, m);
      List<TdApi.Message> out = new ArrayList<>(map.values());
      java.util.Collections.sort(out, (a, b) -> Long.compare(b.id, a.id));
      after.runWithData(out);
    }));
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
