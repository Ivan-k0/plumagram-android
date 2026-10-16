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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.data.Tgx101SettingsBackup;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.Permissions;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * TGx101 (user 2026-10-16, approved mockup «Копия настроек», variant В): save the app's settings to a file in Downloads
 * or to Saved Messages, pick which parts go in, restore from a file (also by tapping a «.plumagram» file in a chat).
 */
public class Tgx101BackupController extends RecyclerViewController<Void> implements View.OnClickListener {
  private static final String KEY_PARTS = "tgx101_backup_parts";
  private static final String KEY_LAST_DATE = "tgx101_backup_last_date";
  private static final String KEY_LAST_WHERE = "tgx101_backup_last_where";
  private static final int WHERE_DOWNLOADS = 0, WHERE_SAVED = 1;
  private static final int REQUEST_PICK = 7161;

  private static final int[] PART_IDS = {
    R.id.btn_tgx101BackupPartAppearance, R.id.btn_tgx101BackupPartMenus, R.id.btn_tgx101BackupPartOther, R.id.btn_tgx101BackupPartSaved
  };
  private static final int[] PART_NAMES = {
    R.string.Tgx101BackupPartAppearance, R.string.Tgx101BackupPartMenus, R.string.Tgx101BackupPartOther, R.string.Tgx101BackupPartSaved
  };

  public Tgx101BackupController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101Backup;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101Backup);
  }

  private SettingsAdapter adapter;
  private Map<Integer, Integer> counts;

  private static int parts () {
    int p = Settings.instance().pmc().getInt(KEY_PARTS, Tgx101SettingsBackup.PART_ALL);
    return p & Tgx101SettingsBackup.PART_ALL;
  }

  /** «Last: 16 Oct, 14:20» or «not saved yet» */
  public static String lastBackupText () {
    long date = Settings.instance().pmc().getLong(KEY_LAST_DATE, 0);
    if (date == 0) return Lang.getString(R.string.Tgx101BackupNever);
    return Lang.getString(R.string.Tgx101BackupLast, formatDate(date));
  }

  private static String formatDate (long seconds) {
    return new SimpleDateFormat("d MMM, HH:mm", Lang.locale()).format(new Date(seconds * 1000L));
  }

  private static String partCount (int part, int count) {
    return part == Tgx101SettingsBackup.PART_SAVED ? Lang.getString(R.string.Tgx101BackupFolders, count) : Lang.getString(R.string.Tgx101BackupCount, count);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    counts = Tgx101SettingsBackup.countHere();
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        if (id == R.id.btn_tgx101BackupDownloads) {
          long date = Settings.instance().pmc().getLong(KEY_LAST_DATE, 0);
          view.setData(date != 0 && Settings.instance().pmc().getInt(KEY_LAST_WHERE, WHERE_DOWNLOADS) == WHERE_DOWNLOADS ? lastBackupText() : Lang.getString(R.string.Tgx101BackupToDownloadsHint));
        } else if (id == R.id.btn_tgx101BackupSaved) {
          long date = Settings.instance().pmc().getLong(KEY_LAST_DATE, 0);
          view.setData(date != 0 && Settings.instance().pmc().getInt(KEY_LAST_WHERE, WHERE_DOWNLOADS) == WHERE_SAVED ? lastBackupText() : Lang.getString(R.string.Tgx101BackupToSavedHint));
        } else {
          for (int i = 0; i < PART_IDS.length; i++) {
            if (PART_IDS[i] == id) {
              int part = Tgx101SettingsBackup.PARTS[i];
              Integer c = counts.get(part);
              view.setData(partCount(part, c != null ? c : 0));
            }
          }
        }
      }
    };
    int parts = parts();
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101BackupSaveSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_tgx101BackupDownloads, R.drawable.baseline_file_download_24, R.string.Tgx101BackupToDownloads));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_tgx101BackupSaved, R.drawable.baseline_bookmark_24, R.string.Tgx101BackupToSaved));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101BackupPartsSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < PART_IDS.length; i++) {
      if (i > 0) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE, PART_IDS[i], 0, Lang.getString(PART_NAMES[i]), (parts & Tgx101SettingsBackup.PARTS[i]) != 0));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101BackupNeverDesc));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101BackupRestoreSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101BackupRestore, R.drawable.baseline_history_24, R.string.Tgx101BackupRestore));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101BackupRestoreDesc));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_tgx101BackupDownloads) {
      if (!context().permissions().requestWriteExternalStorage(Permissions.WriteType.DOWNLOADS, granted -> {
        if (granted) save(WHERE_DOWNLOADS);
      })) {
        save(WHERE_DOWNLOADS);
      }
    } else if (id == R.id.btn_tgx101BackupSaved) {
      save(WHERE_SAVED);
    } else if (id == R.id.btn_tgx101BackupRestore) {
      pick(context(), tdlib);
    } else {
      for (int i = 0; i < PART_IDS.length; i++) {
        if (PART_IDS[i] == id) {
          ListItem item = (ListItem) v.getTag();
          boolean on = adapter.toggleView(v);
          if (item != null) item.setSelected(on);
          int p = parts();
          p = on ? p | Tgx101SettingsBackup.PARTS[i] : p & ~Tgx101SettingsBackup.PARTS[i];
          Settings.instance().pmc().putInt(KEY_PARTS, p);
        }
      }
    }
  }

  // Save

  private boolean saving;

  private void save (int where) {
    int parts = parts();
    if (parts == 0) {
      UI.showToast(R.string.Tgx101BackupNothing, Toast.LENGTH_SHORT);
      return;
    }
    if (saving) return;
    saving = true;
    final Tdlib tdlib = this.tdlib;
    new Thread(() -> {
      try {
        String text = Tgx101SettingsBackup.export(parts);
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(new Date());
        String name = "PlumaGram-settings-" + stamp + "." + Tgx101SettingsBackup.EXTENSION;
        File dir = where == WHERE_DOWNLOADS
          ? new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BuildConfig.PROJECT_NAME)
          : new File(org.thunderdog.challegram.unsorted.AppContext.get().getFilesDir(), "tgx101_backup");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("can't create " + dir);
        if (where == WHERE_SAVED) {
          File[] old = dir.listFiles();
          if (old != null) for (File f : old) if (System.currentTimeMillis() - f.lastModified() > 7L * 24 * 60 * 60 * 1000) f.delete();
        }
        File file = new File(dir, name);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
          w.write(text);
        }
        Tgx101Diag.mark("backup: saved " + text.length() + " chars, parts " + parts + ", " + (where == WHERE_SAVED ? "saved" : "downloads"));
        if (where == WHERE_DOWNLOADS) {
          U.scanFile(file);
          UI.post(() -> {
            saving = false;
            remember(where);
            UI.showToast(Lang.getString(R.string.Tgx101BackupSavedTo, "Download/" + BuildConfig.PROJECT_NAME + "/" + name), Toast.LENGTH_LONG);
          });
        } else {
          Tgx101SavedStore.suppressBanner(10000);
          TdApi.FormattedText caption = new TdApi.FormattedText(Lang.getString(R.string.Tgx101BackupCaption, BuildConfig.VERSION_NAME), null);
          TdApi.InputMessageContent content = new TdApi.InputMessageDocument(new TdApi.InputDocument(TD.createInputFile(file.getPath()), null, true), caption);
          tdlib.send(new TdApi.SendMessage(tdlib.selfChatId(), null, null, null, null, content), (sent, error) -> UI.post(() -> {
            saving = false;
            if (error != null) {
              UI.showToast(Lang.getString(R.string.Tgx101BackupFailed, TD.toErrorString(error)), Toast.LENGTH_LONG);
            } else {
              remember(where);
              UI.showToast(R.string.Tgx101BackupSentToSaved, Toast.LENGTH_SHORT);
            }
          }));
        }
      } catch (Throwable t) {
        Tgx101Diag.mark("backup: save failed " + t);
        UI.post(() -> {
          saving = false;
          UI.showToast(Lang.getString(R.string.Tgx101BackupFailed, t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()), Toast.LENGTH_LONG);
        });
      }
    }, "tgx101-backup").start();
  }

  private void remember (int where) {
    Settings.instance().pmc().edit()
      .putLong(KEY_LAST_DATE, System.currentTimeMillis() / 1000L)
      .putInt(KEY_LAST_WHERE, where)
      .apply();
    if (adapter != null) {
      adapter.updateValuedSettingById(R.id.btn_tgx101BackupDownloads);
      adapter.updateValuedSettingById(R.id.btn_tgx101BackupSaved);
    }
  }

  // Restore

  /** The system's file picker (works from Android 4.1 on) */
  public static void pick (BaseActivity activity, Tdlib tdlib) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.setType("*/*");
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    activity.putActivityResultHandler(REQUEST_PICK, (requestCode, resultCode, data) -> {
      activity.putActivityResultHandler(REQUEST_PICK, null);
      if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
      Uri uri = data.getData();
      new Thread(() -> {
        String text = null;
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
          text = readText(in);
        } catch (Throwable t) {
          Tgx101Diag.mark("backup: read failed " + t);
        }
        final String result = text;
        UI.post(() -> confirm(activity, result));
      }, "tgx101-backup-read").start();
    });
    try {
      activity.startActivityForResult(intent, REQUEST_PICK);
    } catch (Throwable t) {
      activity.putActivityResultHandler(REQUEST_PICK, null);
      UI.showToast(R.string.NoAppToOpen, Toast.LENGTH_SHORT);
    }
  }

  /** A «.plumagram» file tapped in a chat */
  public static boolean isBackupFile (File file) {
    String path = file.getPath();
    return path.toLowerCase(java.util.Locale.ROOT).endsWith("." + Tgx101SettingsBackup.EXTENSION);
  }

  public static void openFile (BaseActivity activity, File file) {
    new Thread(() -> {
      String text = null;
      try (InputStream in = new FileInputStream(file)) {
        text = readText(in);
      } catch (Throwable t) {
        Tgx101Diag.mark("backup: read failed " + t);
      }
      final String result = text;
      UI.post(() -> confirm(activity, result));
    }, "tgx101-backup-read").start();
  }

  private static @Nullable String readText (InputStream in) throws Exception {
    if (in == null) return null;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[16 * 1024];
    int n;
    while ((n = in.read(buf)) != -1) {
      out.write(buf, 0, n);
      if (out.size() > 8 * 1024 * 1024) return null; // not a settings file
    }
    return new String(out.toByteArray(), "UTF-8");
  }

  private static void confirm (BaseActivity activity, @Nullable String text) {
    Tgx101SettingsBackup.Backup b = text != null ? Tgx101SettingsBackup.read(text) : null;
    ViewController<?> c = activity.navigation().getCurrentStackItem();
    if (b == null || c == null) {
      UI.showToast(R.string.Tgx101BackupNotOurs, Toast.LENGTH_LONG);
      return;
    }
    StringBuilder info = new StringBuilder(Lang.getString(R.string.Tgx101BackupConfirm));
    info.append("\n\n").append(Lang.getString(R.string.Tgx101BackupConfirmFrom, b.appVersion, formatDate(b.date)));
    for (int i = 0; i < Tgx101SettingsBackup.PARTS.length; i++) {
      int part = Tgx101SettingsBackup.PARTS[i];
      Integer count = b.counts.get(part);
      if (count == null) continue;
      String value = part == Tgx101SettingsBackup.PART_SAVED
        ? Lang.getString(R.string.Tgx101BackupFolders, b.savedFolders) + (b.savedTags > 0 ? ", " + Lang.getString(R.string.Tgx101BackupTags, b.savedTags) : "")
        : partCount(part, count);
      info.append("\n").append(Lang.getString(PART_NAMES[i])).append(" — ").append(value);
    }
    if (b.missingAccounts > 0) info.append("\n\n").append(Lang.getString(R.string.Tgx101BackupMissingAccount));
    info.append("\n\n").append(Lang.getString(R.string.Tgx101BackupConfirmWarn));
    c.showOptions(info.toString(), new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Tgx101BackupRestoreBtn), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_history_24, R.drawable.baseline_cancel_24}, (itemView, optionId) -> {
        if (optionId == R.id.btn_done) restore(activity, b);
        return true;
      });
  }

  private static void restore (BaseActivity activity, Tgx101SettingsBackup.Backup b) {
    try {
      Tgx101SettingsBackup.apply(b);
      Tgx101Diag.mark("backup: restored, restarting");
    } catch (Throwable t) {
      Tgx101Diag.mark("backup: restore failed " + t);
      UI.showToast(Lang.getString(R.string.Tgx101BackupRestoreFailed, t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()), Toast.LENGTH_LONG);
      return;
    }
    UI.showToast(R.string.Tgx101BackupRestored, Toast.LENGTH_SHORT);
    // the settings are read once, at start: a new process with the main screen on top
    UI.post(() -> {
      Intent intent = Intent.makeRestartActivityTask(new ComponentName(activity, MainActivity.class));
      activity.startActivity(intent);
      Runtime.getRuntime().exit(0);
    }, 600);
  }
}
