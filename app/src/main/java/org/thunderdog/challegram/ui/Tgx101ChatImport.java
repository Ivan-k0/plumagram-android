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
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * TGx101: imports a chat exported from WhatsApp (and other messengers Telegram supports) when it
 * is shared to the app. The export is recognized by the Telegram server (GetMessageFileType);
 * anything else is shared as usual. Android 7.0+ only.
 */
public final class Tgx101ChatImport {
  private Tgx101ChatImport () { }

  private static final long MAX_TOTAL_BYTES = 1500L * 1024 * 1024;

  /**
   * Called on a background thread with a share intent.
   *
   * @param fallback runs the usual share flow; called on a background thread
   * @return true if the intent was taken over (the user will be asked what to do)
   */
  public static boolean tryHandle (@NonNull ViewController<?> c, @NonNull Tdlib tdlib, @NonNull Intent intent, @NonNull Runnable fallback) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      return false;
    }
    List<Uri> uris = streams(intent);
    if (uris.isEmpty()) {
      return false;
    }
    ContentResolver cr = c.context().getContentResolver();
    // A chat export has exactly one text file (the history) or is a single zip archive.
    Uri textUri = null, zipUri = null;
    for (Uri uri : uris) {
      String name = displayName(cr, uri).toLowerCase(Locale.ROOT);
      if (name.endsWith(".txt")) {
        if (textUri != null) {
          return false;
        }
        textUri = uri;
      } else if (name.endsWith(".zip") && uris.size() == 1) {
        zipUri = uri;
      }
    }
    if (textUri == null && zipUri == null) {
      return false;
    }
    try {
      // Ask the server first, using only the beginning of the history file, so that ordinary
      // shared files are not copied or unpacked for nothing.
      String head = null;
      if (zipUri != null) {
        try (InputStream in = cr.openInputStream(zipUri); ZipInputStream zip = new ZipInputStream(in)) {
          ZipEntry entry;
          while ((entry = zip.getNextEntry()) != null) {
            if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) {
              head = head(zip);
              break;
            }
          }
        }
      } else {
        try (InputStream in = cr.openInputStream(textUri)) {
          if (in != null) {
            head = head(in);
          }
        }
      }
      if (head == null || head.isEmpty()) {
        return false;
      }
      TdApi.MessageFileType type = detect(tdlib, head);
      if (type == null || type.getConstructor() == TdApi.MessageFileTypeUnknown.CONSTRUCTOR) {
        return false;
      }

      File dir = new File(c.context().getCacheDir(), "tgx101_import/" + System.currentTimeMillis());
      if (!dir.mkdirs()) {
        return false;
      }
      File messageFile = null;
      List<File> attached = new ArrayList<>();
      if (zipUri != null) {
        long total = 0;
        try (InputStream in = cr.openInputStream(zipUri); ZipInputStream zip = new ZipInputStream(in)) {
          ZipEntry entry;
          while ((entry = zip.getNextEntry()) != null) {
            if (entry.isDirectory()) {
              continue;
            }
            String name = new File(entry.getName()).getName();
            if (name.isEmpty() || name.startsWith(".")) {
              continue;
            }
            File out = new File(dir, name);
            total += copy(zip, out);
            if (total > MAX_TOTAL_BYTES) {
              return false;
            }
            if (name.toLowerCase(Locale.ROOT).endsWith(".txt") && messageFile == null) {
              messageFile = out;
            } else {
              attached.add(out);
            }
          }
        }
      } else {
        long total = 0;
        for (Uri uri : uris) {
          String name = displayName(cr, uri);
          File out = new File(dir, name);
          try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) {
              return false;
            }
            total += copy(in, out);
          }
          if (total > MAX_TOTAL_BYTES) {
            return false;
          }
          if (uri.equals(textUri)) {
            messageFile = out;
          } else {
            attached.add(out);
          }
        }
      }
      if (messageFile == null) {
        return false;
      }
      final File finalMessageFile = messageFile;
      UI.post(() -> offer(c, tdlib, type, finalMessageFile, attached, fallback));
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  private static List<Uri> streams (Intent intent) {
    List<Uri> uris = new ArrayList<>();
    if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
      ArrayList<Parcelable> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
      if (list != null) {
        for (Parcelable p : list) {
          Uri uri = U.getUri(p);
          if (uri != null) {
            uris.add(uri);
          }
        }
      }
    } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
      Parcelable p = intent.getParcelableExtra(Intent.EXTRA_STREAM);
      Uri uri = p != null ? U.getUri(p) : null;
      if (uri != null) {
        uris.add(uri);
      }
    }
    return uris;
  }

  private static String displayName (ContentResolver cr, Uri uri) {
    String name = null;
    try (Cursor cursor = cr.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        name = cursor.getString(0);
      }
    } catch (Throwable ignored) { }
    if (name == null || name.isEmpty()) {
      name = uri.getLastPathSegment();
    }
    if (name == null || name.isEmpty()) {
      name = "file";
    }
    return name.replaceAll("[\\\\/:*?\"<>|]", "_");
  }

  private static long copy (InputStream in, File out) throws Exception {
    long total = 0;
    byte[] buffer = new byte[64 * 1024];
    try (OutputStream os = new FileOutputStream(out)) {
      int n;
      while ((n = in.read(buffer)) != -1) {
        os.write(buffer, 0, n);
        total += n;
      }
    }
    return total;
  }

  /** First 100 lines. Doesn't close the stream (it may be a zip entry). */
  private static String head (InputStream in) throws Exception {
    StringBuilder b = new StringBuilder();
    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String line;
    int count = 0;
    while (count < 100 && (line = r.readLine()) != null) {
      b.append(line).append('\n');
      count++;
    }
    return b.toString();
  }

  private static @Nullable TdApi.MessageFileType detect (Tdlib tdlib, String head) throws InterruptedException {
    AtomicReference<TdApi.MessageFileType> result = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    tdlib.send(new TdApi.GetMessageFileType(head), (type, error) -> {
      result.set(type);
      latch.countDown();
    });
    latch.await(15, TimeUnit.SECONDS);
    return result.get();
  }

  private static void offer (ViewController<?> c, Tdlib tdlib, TdApi.MessageFileType type, File messageFile, List<File> attached, Runnable fallback) {
    String info;
    if (type.getConstructor() == TdApi.MessageFileTypePrivate.CONSTRUCTOR) {
      info = Lang.getString(R.string.ChatImportPrivateHint, ((TdApi.MessageFileTypePrivate) type).name);
    } else if (type.getConstructor() == TdApi.MessageFileTypeGroup.CONSTRUCTOR) {
      info = Lang.getString(R.string.ChatImportGroupHint, ((TdApi.MessageFileTypeGroup) type).title);
    } else {
      info = Lang.getString(R.string.ChatImportHint);
    }
    c.showOptions(info,
      new int[] {R.id.btn_importChat, R.id.btn_send},
      new String[] {Lang.getString(R.string.ChatImport), Lang.getString(R.string.ChatImportSendAsFiles)},
      new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_file_download_24, R.drawable.baseline_forward_24},
      (itemView, id) -> {
        if (id == R.id.btn_importChat) {
          pickChat(c, tdlib, type, messageFile, attached);
        } else {
          new Thread(fallback).start();
        }
        return true;
      });
  }

  private static void pickChat (ViewController<?> c, Tdlib tdlib, TdApi.MessageFileType type, File messageFile, List<File> attached) {
    boolean isPrivate = type.getConstructor() == TdApi.MessageFileTypePrivate.CONSTRUCTOR;
    ChatsController picker = new ChatsController(c.context(), tdlib);
    picker.setArguments(new ChatsController.Arguments(new ChatsController.PickerDelegate() {
      @Override
      public boolean onChatPicked (TdApi.Chat chat, Runnable onDone) {
        boolean isPrivateChat = chat.type.getConstructor() == TdApi.ChatTypePrivate.CONSTRUCTOR;
        boolean isGroup = chat.type.getConstructor() == TdApi.ChatTypeBasicGroup.CONSTRUCTOR ||
          (chat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR && !((TdApi.ChatTypeSupergroup) chat.type).isChannel);
        if (isPrivate ? !isPrivateChat : !isGroup) {
          UI.showToast(isPrivate ? R.string.ChatImportPickPrivate : R.string.ChatImportPickGroup, Toast.LENGTH_SHORT);
          return false;
        }
        confirm(c, tdlib, chat, messageFile, attached);
        return true;
      }

      @Override
      public int getTitleStringRes () {
        return R.string.ChatImportPick;
      }
    }));
    c.navigateTo(picker);
  }

  private static void confirm (ViewController<?> c, Tdlib tdlib, TdApi.Chat chat, File messageFile, List<File> attached) {
    tdlib.send(new TdApi.GetMessageImportConfirmationText(chat.id), (text, error) -> UI.post(() -> {
      if (error != null) {
        UI.showToast(Lang.getString(R.string.ChatImportFailed, TD.toErrorString(error)), Toast.LENGTH_LONG);
        return;
      }
      String message = text.text + "\n\n" + Lang.getString(R.string.ChatImportConfirm, tdlib.chatTitle(chat.id));
      ViewController<?> current = UI.getContext(c.context()).navigation().getCurrentStackItem();
      ViewController<?> host = current != null ? current : c;
      host.showOptions(message,
        new int[] {R.id.btn_importChat, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.ChatImport), Lang.getString(R.string.Cancel)},
        new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
        new int[] {R.drawable.baseline_file_download_24, R.drawable.baseline_cancel_24},
        (itemView, id) -> {
          if (id == R.id.btn_importChat) {
            runImport(host, tdlib, chat, messageFile, attached);
          }
          return true;
        });
    }));
  }

  private static void runImport (ViewController<?> c, Tdlib tdlib, TdApi.Chat chat, File messageFile, List<File> attached) {
    TextView progress = new TextView(c.context());
    progress.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
    progress.setTextColor(Theme.textAccentColor());
    int padding = Screen.dp(20f);
    progress.setPadding(padding, padding, padding, padding);
    progress.setText(Lang.getString(R.string.ChatImportProgress, attached.size()));
    AlertDialog.Builder b = new AlertDialog.Builder(c.context(), Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.ChatImport));
    b.setView(progress);
    b.setCancelable(false);
    AlertDialog dialog = c.showAlert(b);

    TdApi.InputFile[] files = new TdApi.InputFile[attached.size()];
    for (int i = 0; i < files.length; i++) {
      files[i] = new TdApi.InputFileLocal(attached.get(i).getPath());
    }
    tdlib.send(new TdApi.ImportMessages(chat.id, new TdApi.InputFileLocal(messageFile.getPath()), files), (ok, error) -> UI.post(() -> {
      if (dialog != null) {
        dialog.dismiss();
      }
      if (error != null) {
        UI.showToast(Lang.getString(R.string.ChatImportFailed, TD.toErrorString(error)), Toast.LENGTH_LONG);
      } else {
        UI.showToast(Lang.getString(R.string.ChatImportDone, tdlib.chatTitle(chat.id)), Toast.LENGTH_LONG);
        tdlib.ui().openChat(c, chat.id, null);
      }
    }));
  }
}
