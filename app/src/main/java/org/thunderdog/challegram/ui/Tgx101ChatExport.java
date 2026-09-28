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
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.TypedValue;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.Permissions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tgx.td.Td;

/**
 * TGx101: exports the history of one chat to Downloads/<app name> as an HTML page (for reading)
 * or a plain text file. Text only: media are listed as "[Photo]", "[Voice message]" etc.
 * Android 7.0+ only.
 */
public final class Tgx101ChatExport {
  private Tgx101ChatExport () { }

  private static final int FORMAT_HTML = 0, FORMAT_TXT = 1;
  private static final int BATCH = 100;

  public static boolean canExport (@Nullable TdApi.Chat chat) {
    return chat != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
      !TD.isSecretChat(chat.type) && !chat.hasProtectedContent;
  }

  public static void start (@NonNull ViewController<?> c, @NonNull TdApi.Chat chat) {
    if (!canExport(chat)) {
      return;
    }
    c.showOptions(Lang.getString(R.string.ChatExportFormatHint),
      new int[] {R.id.btn_exportChatHtml, R.id.btn_exportChatTxt},
      new String[] {Lang.getString(R.string.ChatExportHtml), Lang.getString(R.string.ChatExportTxt)},
      new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_language_24, R.drawable.baseline_file_download_24},
      (itemView, id) -> {
        int format = id == R.id.btn_exportChatTxt ? FORMAT_TXT : FORMAT_HTML;
        if (!c.context().permissions().requestWriteExternalStorage(Permissions.WriteType.DOWNLOADS, granted -> {
          if (granted) {
            new Job(c, chat, format).run();
          }
        })) {
          new Job(c, chat, format).run();
        }
        return true;
      });
  }

  private static final class Entry {
    final long id, replyToId;
    final int date;
    final String sender, forwardedFrom, text, media;

    Entry (long id, long replyToId, int date, String sender, String forwardedFrom, String text, String media) {
      this.id = id;
      this.replyToId = replyToId;
      this.date = date;
      this.sender = sender;
      this.forwardedFrom = forwardedFrom;
      this.text = text;
      this.media = media;
    }
  }

  private static final class Job {
    private final ViewController<?> c;
    private final Tdlib tdlib;
    private final TdApi.Chat chat;
    private final int format;
    private final List<Entry> entries = new ArrayList<>();
    private final Set<Long> seen = new HashSet<>();
    private volatile boolean cancelled;
    private AlertDialog dialog;
    private TextView progress;

    Job (ViewController<?> c, TdApi.Chat chat, int format) {
      this.c = c;
      this.tdlib = c.tdlib();
      this.chat = chat;
      this.format = format;
    }

    void run () {
      progress = new TextView(c.context());
      progress.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
      progress.setTextColor(Theme.textAccentColor());
      int padding = Screen.dp(20f);
      progress.setPadding(padding, padding, padding, padding);
      progress.setText(Lang.getString(R.string.ChatExportProgress, 0));
      AlertDialog.Builder b = new AlertDialog.Builder(c.context(), Theme.dialogTheme());
      b.setTitle(Lang.getString(R.string.ChatExport));
      b.setView(progress);
      b.setCancelable(false);
      b.setNegativeButton(Lang.getString(R.string.Cancel), (d, which) -> {
        cancelled = true;
        d.dismiss();
      });
      dialog = c.showAlert(b);
      load(0);
    }

    private void load (long fromMessageId) {
      tdlib.send(new TdApi.GetChatHistory(chat.id, fromMessageId, 0, BATCH, false), (messages, error) -> {
        if (cancelled) {
          return;
        }
        if (error != null) {
          UI.post(() -> fail(TD.toErrorString(error)));
          return;
        }
        long oldestId = 0;
        int added = 0;
        for (TdApi.Message message : messages.messages) {
          if (message == null || !seen.add(message.id)) {
            continue;
          }
          entries.add(toEntry(message));
          oldestId = message.id;
          added++;
        }
        final int count = entries.size();
        UI.post(() -> {
          if (!cancelled && progress != null) {
            progress.setText(Lang.getString(R.string.ChatExportProgress, count));
          }
        });
        if (added == 0) {
          Background.instance().post(this::write);
        } else {
          load(oldestId);
        }
      });
    }

    private Entry toEntry (TdApi.Message message) {
      String sender = tdlib.senderName(message.senderId, false);
      String forwardedFrom = null;
      if (message.forwardInfo != null) {
        switch (message.forwardInfo.origin.getConstructor()) {
          case TdApi.MessageOriginUser.CONSTRUCTOR:
            forwardedFrom = tdlib.cache().userDisplayName(((TdApi.MessageOriginUser) message.forwardInfo.origin).senderUserId, false, false);
            break;
          case TdApi.MessageOriginChat.CONSTRUCTOR:
            forwardedFrom = tdlib.chatTitle(((TdApi.MessageOriginChat) message.forwardInfo.origin).senderChatId);
            break;
          case TdApi.MessageOriginChannel.CONSTRUCTOR:
            forwardedFrom = tdlib.chatTitle(((TdApi.MessageOriginChannel) message.forwardInfo.origin).chatId);
            break;
          case TdApi.MessageOriginHiddenUser.CONSTRUCTOR:
            forwardedFrom = ((TdApi.MessageOriginHiddenUser) message.forwardInfo.origin).senderName;
            break;
        }
      }
      long replyToId = 0;
      if (message.replyTo != null && message.replyTo.getConstructor() == TdApi.MessageReplyToMessage.CONSTRUCTOR) {
        TdApi.MessageReplyToMessage reply = (TdApi.MessageReplyToMessage) message.replyTo;
        if (reply.chatId == 0 || reply.chatId == message.chatId) {
          replyToId = reply.messageId;
        }
      }
      String text = null, media = null;
      TdApi.FormattedText formattedText = Td.textOrCaption(message.content);
      if (formattedText != null && formattedText.text != null && !formattedText.text.isEmpty()) {
        text = formattedText.text;
      }
      if (message.content.getConstructor() != TdApi.MessageText.CONSTRUCTOR || text == null) {
        try {
          ContentPreview preview = ContentPreview.getNotificationPreview(tdlib, message.chatId, message, false);
          media = preview.toString();
        } catch (Throwable t) {
          media = null;
        }
        if (media != null && text != null && media.contains(text)) {
          // The preview already ends with the caption; keep only the media label.
          media = media.replace(text, "").replaceAll("[,:\\s]+$", "");
        }
        if (media != null && media.isEmpty()) {
          media = null;
        }
      }
      return new Entry(message.id, replyToId, message.date, sender, forwardedFrom, text, media);
    }

    private void write () {
      if (cancelled) {
        return;
      }
      Collections.reverse(entries); // oldest first
      File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BuildConfig.PROJECT_NAME);
      String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date());
      String title = chat.title != null && !chat.title.isEmpty() ? chat.title : "chat";
      String safeTitle = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
      if (safeTitle.length() > 60) {
        safeTitle = safeTitle.substring(0, 60).trim();
      }
      File file = new File(dir, safeTitle + "_" + stamp + (format == FORMAT_HTML ? ".html" : ".txt"));
      try {
        if (!dir.exists() && !dir.mkdirs()) {
          throw new IllegalStateException("Can't create " + dir);
        }
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
          if (format == FORMAT_HTML) {
            writeHtml(w, title);
          } else {
            writeTxt(w, title);
          }
        }
        U.scanFile(file);
      } catch (Throwable t) {
        UI.post(() -> fail(t.getMessage()));
        return;
      }
      UI.post(() -> done(file));
    }

    private void writeTxt (BufferedWriter w, String title) throws Exception {
      SimpleDateFormat f = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
      w.write(title);
      w.write("\n");
      w.write(Lang.getString(R.string.ChatExportHeader, entries.size(), f.format(new Date())));
      w.write("\n\n");
      for (Entry e : entries) {
        w.write("[" + f.format(new Date(e.date * 1000L)) + "] " + e.sender + ":");
        if (e.forwardedFrom != null) {
          w.write(" (" + Lang.getString(R.string.ChatExportForwarded, e.forwardedFrom) + ")");
        }
        if (e.media != null) {
          w.write(" [" + e.media + "]");
        }
        if (e.text != null) {
          w.write(" " + e.text);
        }
        w.write("\n");
      }
    }

    private void writeHtml (BufferedWriter w, String title) throws Exception {
      SimpleDateFormat day = new SimpleDateFormat("d MMMM yyyy", Locale.getDefault());
      SimpleDateFormat time = new SimpleDateFormat("HH:mm", Locale.getDefault());
      SimpleDateFormat full = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
      Map<Long, Entry> byId = new HashMap<>();
      for (Entry e : entries) {
        byId.put(e.id, e);
      }
      w.write("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
        "<title>" + esc(title) + "</title><style>" +
        ":root{--bg:#fff;--fg:#1d2733;--muted:#7a8a99;--line:#e6ebf0;--accent:#3e5570;--quote:#f1f4f7}" +
        "@media (prefers-color-scheme:dark){:root{--bg:#1d2733;--fg:#e6ebf0;--muted:#8a9aa9;--line:#2c3a48;--accent:#8fb3d9;--quote:#243140}}" +
        "body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.45 -apple-system,Roboto,sans-serif}" +
        ".wrap{max-width:760px;margin:0 auto;padding:16px}" +
        "h1{font-size:20px;margin:0 0 4px}.sub{color:var(--muted);font-size:13px;margin-bottom:16px}" +
        ".day{text-align:center;color:var(--muted);font-size:13px;margin:20px 0 8px}" +
        ".m{padding:8px 0;border-top:1px solid var(--line)}.h{font-size:13px}.n{font-weight:600;color:var(--accent)}" +
        ".t{color:var(--muted);margin-left:6px}.f,.md{color:var(--muted);font-size:13px}" +
        ".r{border-left:3px solid var(--accent);background:var(--quote);padding:2px 8px;margin:4px 0;font-size:13px;color:var(--muted)}" +
        ".x{white-space:pre-wrap;word-wrap:break-word}</style></head><body><div class=\"wrap\">\n");
      w.write("<h1>" + esc(title) + "</h1><div class=\"sub\">" +
        esc(Lang.getString(R.string.ChatExportHeader, entries.size(), full.format(new Date()))) + "</div>\n");
      String lastDay = null;
      for (Entry e : entries) {
        Date date = new Date(e.date * 1000L);
        String d = day.format(date);
        if (!d.equals(lastDay)) {
          w.write("<div class=\"day\">" + esc(d) + "</div>\n");
          lastDay = d;
        }
        w.write("<div class=\"m\" id=\"m" + e.id + "\"><div class=\"h\"><span class=\"n\">" + esc(e.sender) +
          "</span><span class=\"t\">" + time.format(date) + "</span></div>");
        if (e.forwardedFrom != null) {
          w.write("<div class=\"f\">" + esc(Lang.getString(R.string.ChatExportForwarded, e.forwardedFrom)) + "</div>");
        }
        if (e.replyToId != 0) {
          Entry r = byId.get(e.replyToId);
          String snippet = r == null ? "…" : (r.sender + ": " + shorten(r.text != null ? r.text : r.media));
          w.write("<a class=\"r\" style=\"display:block;text-decoration:none\" href=\"#m" + e.replyToId + "\">" + esc(snippet) + "</a>");
        }
        if (e.media != null) {
          w.write("<div class=\"md\">[" + esc(e.media) + "]</div>");
        }
        if (e.text != null) {
          w.write("<div class=\"x\">" + esc(e.text) + "</div>");
        }
        w.write("</div>\n");
      }
      w.write("</div></body></html>\n");
    }

    private static String shorten (@Nullable String s) {
      if (s == null) {
        return "";
      }
      s = s.replace('\n', ' ');
      return s.length() > 90 ? s.substring(0, 90) + "…" : s;
    }

    private static String esc (@Nullable String s) {
      if (s == null) {
        return "";
      }
      StringBuilder b = new StringBuilder(s.length() + 16);
      for (int i = 0; i < s.length(); i++) {
        char ch = s.charAt(i);
        switch (ch) {
          case '<': b.append("&lt;"); break;
          case '>': b.append("&gt;"); break;
          case '&': b.append("&amp;"); break;
          case '"': b.append("&quot;"); break;
          default: b.append(ch);
        }
      }
      return b.toString();
    }

    private void fail (@Nullable String reason) {
      if (dialog != null) {
        dialog.dismiss();
      }
      UI.showToast(Lang.getString(R.string.ChatExportFailed, reason != null ? reason : "?"), Toast.LENGTH_LONG);
    }

    private void done (File file) {
      if (dialog != null) {
        dialog.dismiss();
      }
      if (c.isDestroyed()) {
        UI.showToast(Lang.getString(R.string.ChatExportDone, entries.size(), file.getPath()), Toast.LENGTH_LONG);
        return;
      }
      String mime = format == FORMAT_HTML ? "text/html" : "text/plain";
      c.showOptions(Lang.getString(R.string.ChatExportDone, entries.size(), file.getPath()),
        new int[] {R.id.btn_open, R.id.btn_share},
        new String[] {Lang.getString(R.string.Open), Lang.getString(R.string.Share)},
        new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
        new int[] {R.drawable.baseline_open_in_browser_24, R.drawable.baseline_share_24},
        (itemView, id) -> {
          Uri uri = U.contentUriFromFile(file);
          if (uri == null) {
            return true;
          }
          Intent intent;
          if (id == R.id.btn_share) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mime);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent = Intent.createChooser(intent, Lang.getString(R.string.Share));
          } else {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
          }
          intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          try {
            c.context().startActivity(intent);
          } catch (Throwable t) {
            UI.showToast(Lang.getString(R.string.ChatExportDone, entries.size(), file.getPath()), Toast.LENGTH_LONG);
          }
          return true;
        });
    }
  }
}
