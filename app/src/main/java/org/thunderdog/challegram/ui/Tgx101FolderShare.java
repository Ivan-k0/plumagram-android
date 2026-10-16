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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TGx101 (user 2026-10-15, approved mockup «Поделиться папкой» 2026-10-16): a Saved folder shared like a Google Drive
 * folder — a private channel (or a group) with the folder's name, pinned in the chats; everything in the folder is copied
 * there in the background, in small batches with pauses, and goes on after a break; an invite link (and a QR code) for
 * whoever should see it. The folder menu keeps the link, «Update» and «Stop sharing».
 */
public final class Tgx101FolderShare {
  private Tgx101FolderShare () { }

  private static final String CHANNEL_ID = "tgx101_folder_share";
  private static final int BATCH = 20;
  private static final long PAUSE_MS = 1500;

  /** folder id → {copied, total} while copying (the home tile draws it) */
  private static final Map<Long, int[]> progress = new HashMap<>();
  private static final Map<Long, Boolean> running = new HashMap<>();

  public static synchronized @Nullable int[] progressOf (long folderId) {
    int[] p = progress.get(folderId);
    return p != null ? p.clone() : null;
  }

  /** «add new from the folder» and going on after a restart */
  public static void init () {
    Tgx101SavedStore.onShareChanged = data -> {
      Tdlib tdlib = TdlibManager.getTdlib((int) data[0]);
      if (tdlib != null) UI.post(() -> copyPending(tdlib, data[1]), 800);
    };
  }

  public static void resumeAll (Tdlib tdlib) {
    for (Tgx101SavedStore.Folder f : Tgx101SavedStore.get(tdlib).folders()) {
      if (f.isShared() && !f.unshared().isEmpty()) copyPending(tdlib, f.id);
    }
  }

  // Setup (mockup Б)

  public static void setup (ViewController<?> c, Tgx101SavedStore.Folder f, @Nullable Runnable after) {
    Context context = c.context();
    final boolean[] channel = {true}, auto = {true}, copies = {true}, approval = {false};
    final EditText[] nameField = new EditText[1];
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101ShareFolder)));
      EditText name = new EditText(context);
      nameField[0] = name;
      name.setText(f.name);
      name.setSingleLine(true);
      Tgx101SavedHomeController.styleField(name, true);
      LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
      np.setMargins(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(8f));
      body.addView(name, np);

      final View[] kinds = new View[2];
      final Runnable paintKinds = () -> {
        ((TextView) kinds[0].getTag()).setText(channel[0] ? "◉" : "○");
        ((TextView) kinds[1].getTag()).setText(channel[0] ? "○" : "◉");
      };
      kinds[0] = choice(context, Lang.getString(R.string.Tgx101ShareViewOnly), Lang.getString(R.string.Tgx101ShareViewOnlyHint), v -> {
        channel[0] = true;
        paintKinds.run();
      });
      kinds[1] = choice(context, Lang.getString(R.string.Tgx101ShareViewAdd), Lang.getString(R.string.Tgx101ShareViewAddHint), v -> {
        channel[0] = false;
        paintKinds.run();
      });
      body.addView(kinds[0]);
      body.addView(kinds[1]);
      paintKinds.run();

      body.addView(toggle(context, Lang.getString(R.string.Tgx101ShareAuto), Lang.getString(R.string.Tgx101ShareAutoHint), auto));
      body.addView(toggle(context, Lang.getString(R.string.Tgx101ShareCopies), Lang.getString(R.string.Tgx101ShareCopiesHint), copies));
      body.addView(toggle(context, Lang.getString(R.string.Tgx101ShareApproval), Lang.getString(R.string.Tgx101ShareApprovalHint), approval));

      int n = f.messageIds.size();
      TextView warn = Tgx101SavedHomeController.text(context, 12f, Theme.textDecentColor(), false);
      warn.setText(Lang.getString(R.string.Tgx101ShareTime, n, Math.max(1, (int) Math.ceil(n / (double) BATCH * PAUSE_MS / 60000d))));
      warn.setPadding(Screen.dp(20f), Screen.dp(4f), Screen.dp(20f), Screen.dp(6f));
      body.addView(warn);
      body.addView(Tgx101SavedActions.primaryButton(context, Lang.getString(R.string.Tgx101ShareCreate, n), v -> {
        String title = name.getText().toString().trim();
        if (title.isEmpty()) title = f.name;
        org.thunderdog.challegram.tool.Keyboard.hide(name);
        h[0].dismiss();
        create(c, f, title, channel[0], auto[0], copies[0], approval[0], after);
      }));
    });
  }

  private static View choice (Context context, String title, String hint, View.OnClickListener click) {
    LinearLayout r = new LinearLayout(context);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setPadding(Screen.dp(20f), Screen.dp(8f), Screen.dp(16f), Screen.dp(8f));
    Views.setClickable(r);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(r);
    r.setOnClickListener(click);
    TextView mark = Tgx101SavedHomeController.text(context, 24f, Tgx101SavedHomeController.accent(), false);
    r.addView(mark, new LinearLayout.LayoutParams(Screen.dp(28f), ViewGroup.LayoutParams.WRAP_CONTENT));
    r.setTag(mark);
    LinearLayout texts = new LinearLayout(context);
    texts.setOrientation(LinearLayout.VERTICAL);
    TextView t = Tgx101SavedHomeController.text(context, 15f, Theme.textAccentColor(), false);
    t.setText(title);
    texts.addView(t);
    TextView s = Tgx101SavedHomeController.text(context, 12f, Theme.textDecentColor(), false);
    s.setText(hint);
    texts.addView(s);
    r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    return r;
  }

  private static View toggle (Context context, String title, String hint, boolean[] value) {
    LinearLayout r = new LinearLayout(context);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setPadding(Screen.dp(20f), Screen.dp(6f), Screen.dp(12f), Screen.dp(6f));
    LinearLayout texts = new LinearLayout(context);
    texts.setOrientation(LinearLayout.VERTICAL);
    TextView t = Tgx101SavedHomeController.text(context, 15f, Theme.textAccentColor(), false);
    t.setText(title);
    texts.addView(t);
    TextView s = Tgx101SavedHomeController.text(context, 12f, Theme.textDecentColor(), false);
    s.setText(hint);
    texts.addView(s);
    r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    android.widget.CheckBox box = new android.widget.CheckBox(context);
    box.setChecked(value[0]);
    box.setOnCheckedChangeListener((b, checked) -> value[0] = checked);
    r.addView(box);
    Views.setClickable(r);
    r.setOnClickListener(v -> box.toggle());
    return r;
  }

  // Creating

  private static void create (ViewController<?> c, Tgx101SavedStore.Folder f, String title, boolean channel, boolean auto, boolean copies, boolean approval, @Nullable Runnable after) {
    Tdlib tdlib = c.tdlib();
    org.thunderdog.challegram.Tgx101Diag.mark("folder share: creating «" + title + "», channel " + channel + ", " + f.messageIds.size() + " items");
    UI.showToast(R.string.Tgx101ShareCreating, android.widget.Toast.LENGTH_SHORT);
    tdlib.send(new TdApi.CreateNewSupergroupChat(title, false, channel, Lang.getString(R.string.Tgx101ShareDescription), null, 0, false), (chat, error) -> {
      if (error != null) {
        UI.post(() -> UI.showError(error));
        return;
      }
      tdlib.send(new TdApi.CreateChatInviteLink(chat.id, "PlumaGram", 0, 0, approval), (link, linkError) -> UI.post(() -> {
        Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
        f.shareChannel = channel;
        f.shareAuto = auto;
        f.shareCopies = copies;
        f.shareApproval = approval;
        store.setShare(f.id, chat.id, link != null ? link.inviteLink : null);
        if (linkError != null) UI.showError(linkError);
        tdlib.send(new TdApi.ToggleChatIsPinned(new TdApi.ChatListMain(), chat.id, true), (ok, pinError) -> { });
        copyPending(tdlib, f.id);
        if (after != null) after.run();
        if (!c.isDestroyed()) showLink(c, f);
      }));
    });
  }

  // Copying (mockup В)

  public static void copyPending (Tdlib tdlib, long folderId) {
    synchronized (Tgx101FolderShare.class) {
      if (Boolean.TRUE.equals(running.get(folderId))) return;
      running.put(folderId, true);
    }
    Tgx101SavedStore.Folder f = Tgx101SavedStore.get(tdlib).folder(folderId);
    if (f == null || !f.isShared()) {
      finish(tdlib, folderId, 0, 0, false);
      return;
    }
    List<Long> pending = f.unshared();
    int total = f.sharedIds.size() + pending.size();
    if (pending.isEmpty()) {
      finish(tdlib, folderId, total, total, false);
      return;
    }
    org.thunderdog.challegram.Tgx101Diag.mark("folder share: copying " + pending.size() + " to " + f.shareChatId);
    step(tdlib, f, pending, 0, total, f.sharedIds.size());
  }

  private static void step (Tdlib tdlib, Tgx101SavedStore.Folder f, List<Long> pending, int from, int total, int done) {
    setProgress(tdlib, f, done, total);
    if (from >= pending.size()) {
      finish(tdlib, f.id, done, total, true);
      return;
    }
    int to = Math.min(pending.size(), from + BATCH);
    long[] ids = new long[to - from];
    for (int i = from; i < to; i++) ids[i - from] = pending.get(i);
    TdApi.MessageSendOptions options = new TdApi.MessageSendOptions();
    options.disableNotification = true; // the members aren't pinged for each copy
    tdlib.send(new TdApi.ForwardMessages(f.shareChatId, null, tdlib.selfChatId(), ids, options, f.shareCopies, false), (result, error) -> {
      if (error != null) {
        int wait = floodWait(error);
        if (wait > 0) { // Telegram asked to slow down: the same batch again after the pause it named
          org.thunderdog.challegram.Tgx101Diag.mark("folder share: flood wait " + wait + " s");
          UI.post(() -> step(tdlib, f, pending, from, total, done), wait * 1000L + 500);
          return;
        }
        if (error.code == 400 && error.message != null && error.message.contains("CHAT")) { // the channel is gone
          org.thunderdog.challegram.Tgx101Diag.mark("folder share: stopped — " + error.message);
          finish(tdlib, f.id, done, total, false);
          return;
        }
        // something in this batch can't be copied (deleted, protected): skipped, the rest goes on
        org.thunderdog.challegram.Tgx101Diag.mark("folder share: batch skipped — " + error.message);
      }
      Tgx101SavedStore.get(tdlib).markShared(f.id, ids);
      UI.post(() -> step(tdlib, f, pending, to, total, done + ids.length), PAUSE_MS);
    });
  }

  private static int floodWait (TdApi.Error error) {
    if (error.code != 429 || error.message == null) return 0;
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(error.message);
    return m.find() ? Math.min(600, Integer.parseInt(m.group(1))) : 5;
  }

  private static void setProgress (Tdlib tdlib, Tgx101SavedStore.Folder f, int done, int total) {
    synchronized (Tgx101FolderShare.class) {
      progress.put(f.id, new int[] {done, total});
    }
    notifyProgress(f, done, total, false);
    UI.post(() -> refreshHome(tdlib));
  }

  private static void finish (Tdlib tdlib, long folderId, int done, int total, boolean announce) {
    synchronized (Tgx101FolderShare.class) {
      running.remove(folderId);
      progress.remove(folderId);
    }
    Tgx101SavedStore.Folder f = Tgx101SavedStore.get(tdlib).folder(folderId);
    if (announce && f != null) {
      org.thunderdog.challegram.Tgx101Diag.mark("folder share: done " + done + "/" + total);
      notifyProgress(f, done, total, true);
    }
    UI.post(() -> refreshHome(tdlib));
  }

  private static void refreshHome (Tdlib tdlib) {
    org.thunderdog.challegram.BaseActivity a = UI.getUiContext();
    ViewController<?> top = a != null ? a.navigation().getCurrentStackItem() : null;
    if (top instanceof Tgx101SavedHomeController && top.tdlib() == tdlib) ((Tgx101SavedHomeController) top).refreshShareProgress();
  }

  private static void notifyProgress (Tgx101SavedStore.Folder f, int done, int total, boolean finished) {
    Context context = UI.getContext();
    if (context == null) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, Lang.getString(R.string.Tgx101ShareChannel), NotificationManager.IMPORTANCE_LOW));
      }
    }
    NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.baseline_sync_white_24)
      .setOnlyAlertOnce(true)
      .setContentTitle(Lang.getString(finished ? R.string.Tgx101ShareDoneTitle : R.string.Tgx101ShareCopyingTitle, f.name))
      .setContentText(Lang.getString(finished ? R.string.Tgx101ShareDoneText : R.string.Tgx101ShareCopyingText, done, total))
      .setOngoing(!finished)
      .setAutoCancel(finished);
    if (!finished) b.setProgress(Math.max(1, total), done, false);
    try {
      NotificationManagerCompat.from(context).notify(7000 + (int) (f.id % 1000), b.build());
    } catch (SecurityException ignored) { }
  }

  // The link (mockup Г)

  public static void showLink (ViewController<?> c, Tgx101SavedStore.Folder f) {
    Context context = c.context();
    String link = f.shareLink;
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101ShareReady)));
      TextView sub = Tgx101SavedHomeController.text(context, 13f, Theme.textDecentColor(), false);
      sub.setText(Lang.getString(R.string.Tgx101ShareReadyHint, f.name, f.messageIds.size()));
      sub.setGravity(Gravity.CENTER);
      sub.setPadding(Screen.dp(20f), 0, Screen.dp(20f), Screen.dp(10f));
      body.addView(sub);
      if (link != null) {
        Bitmap qr = makeQr(link, Screen.dp(160f));
        if (qr != null) {
          ImageView q = new ImageView(context);
          q.setImageBitmap(qr);
          LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(Screen.dp(160f), Screen.dp(160f));
          qp.gravity = Gravity.CENTER_HORIZONTAL;
          qp.bottomMargin = Screen.dp(12f);
          body.addView(q, qp);
        }
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Tgx101SavedHomeController.bubble(true));
        row.setPadding(Screen.dp(16f), 0, Screen.dp(8f), 0);
        TextView l = Tgx101SavedHomeController.text(context, 14f, Theme.textAccentColor(), false);
        l.setText(link);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView copy = Tgx101SavedHomeController.text(context, 14f, Tgx101SavedHomeController.accent(), true);
        copy.setText(Lang.getString(R.string.Tgx101SavedCopy));
        copy.setPadding(Screen.dp(10f), Screen.dp(12f), Screen.dp(8f), Screen.dp(12f));
        Views.setClickable(copy);
        copy.setOnClickListener(v -> UI.copyText(link, R.string.CopiedLink));
        row.addView(copy);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
        rp.setMargins(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(8f));
        body.addView(row, rp);
        body.addView(Tgx101SavedActions.primaryButton(context, Lang.getString(R.string.Tgx101ShareSend), v -> {
          ShareController share = new ShareController(context, c.tdlib());
          share.setArguments(new ShareController.Args(link));
          share.show();
        }));
      }
      TextView open = Tgx101SavedHomeController.text(context, 15f, Tgx101SavedHomeController.accent(), true);
      open.setText(Lang.getString(f.shareChannel ? R.string.Tgx101ShareOpenChannel : R.string.Tgx101ShareOpenGroup));
      open.setGravity(Gravity.CENTER);
      Views.setClickable(open);
      open.setOnClickListener(v -> {
        h[0].dismiss(); // (test 2026-10-16: the sheet stayed over the opened channel)
        c.tdlib().ui().openChat(c, f.shareChatId, new org.thunderdog.challegram.telegram.TdlibUi.ChatOpenParameters().keepStack());
      });
      LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
      op.topMargin = Screen.dp(4f);
      body.addView(open, op);
    });
  }

  /** «Update»: what was put in the folder since */
  public static void update (ViewController<?> c, Tgx101SavedStore.Folder f) {
    int n = f.unshared().size();
    if (n == 0) {
      UI.showToast(R.string.Tgx101ShareNothingNew, android.widget.Toast.LENGTH_SHORT);
      return;
    }
    UI.showToast(Lang.getString(R.string.Tgx101ShareUpdating, n), android.widget.Toast.LENGTH_SHORT);
    copyPending(c.tdlib(), f.id);
  }

  /** «Stop sharing»: the link goes dead, or the channel is deleted for everyone */
  public static void stop (ViewController<?> c, Tgx101SavedStore.Folder f, @Nullable Runnable after) {
    Tdlib tdlib = c.tdlib();
    c.showOptions(Lang.getString(R.string.Tgx101ShareStopAsk), new int[] {R.id.btn_close, R.id.btn_delete, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Tgx101ShareRevoke), Lang.getString(f.shareChannel ? R.string.Tgx101ShareDeleteChannel : R.string.Tgx101ShareDeleteGroup), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_link_24, R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
        if (id == R.id.btn_close) {
          if (f.shareLink != null) tdlib.send(new TdApi.RevokeChatInviteLink(f.shareChatId, f.shareLink), (r, e) -> { });
          store.setShare(f.id, 0, null); // the channel stays as it is, without the link and without updates
          if (after != null) after.run();
        } else if (id == R.id.btn_delete) {
          tdlib.send(new TdApi.DeleteChat(f.shareChatId), (r, e) -> UI.post(() -> {
            if (e != null) UI.showError(e);
          }));
          store.setShare(f.id, 0, null);
          if (after != null) after.run();
        }
        return true;
      });
  }

  private static @Nullable Bitmap makeQr (String text, int size) {
    try {
      com.google.zxing.common.BitMatrix matrix = new com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
        java.util.Collections.singletonMap(com.google.zxing.EncodeHintType.MARGIN, 2));
      int width = matrix.getWidth(), height = matrix.getHeight();
      int[] pixels = new int[width * height];
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          pixels[y * width + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
        }
      }
      return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    } catch (Throwable t) {
      return null;
    }
  }
}
