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

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import me.vkryl.core.lambda.RunnableData;

/** TGx101 (approved mockups 6–10): the Saved actions — to folder, tag, remind, forward, delete — and the «Saved» banner */
public final class Tgx101SavedActions {
  private Tgx101SavedActions () { }

  // Names

  /** A sheet with a text field and a button (new folder, rename) */
  public static void askName (ViewController<?> c, String title, @Nullable String initial, RunnableData<String> done) {
    Context context = c.context();
    final EditText[] field = new EditText[1];
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(context, title));
      EditText e = new EditText(context);
      field[0] = e;
      e.setText(initial);
      e.setSingleLine(true);
      e.setHint(Lang.getString(R.string.Tgx101SavedNameHint));
      e.setHintTextColor(Theme.textDecentColor());
      e.setTextColor(Theme.textAccentColor());
      e.setImeOptions(EditorInfo.IME_ACTION_DONE);
      e.setBackground(Tgx101SavedHomeController.card(Theme.backgroundColor(), 14f));
      e.setPadding(Screen.dp(14f), 0, Screen.dp(14f), 0);
      LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
      ep.setMargins(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(12f));
      body.addView(e, ep);
      Runnable submit = () -> {
        String name = e.getText().toString().trim();
        if (name.isEmpty()) return;
        org.thunderdog.challegram.tool.Keyboard.hide(e);
        h[0].dismiss();
        done.runWithData(name);
      };
      e.setOnEditorActionListener((v, actionId, event) -> {
        submit.run();
        return true;
      });
      body.addView(primaryButton(context, Lang.getString(R.string.Done), v -> submit.run()));
    });
    if (field[0] != null) {
      field[0].requestFocus();
      if (initial != null) field[0].setSelection(initial.length());
      field[0].postDelayed(() -> org.thunderdog.challegram.tool.Keyboard.show(field[0]), 280);
    }
  }

  static TextView primaryButton (Context context, String label, View.OnClickListener click) {
    TextView b = Tgx101SavedHomeController.text(context, 15f, 0xffffffff, true);
    b.setText(label);
    b.setGravity(Gravity.CENTER);
    b.setBackground(Tgx101SavedHomeController.card(Tgx101SavedHomeController.accent(), 14f));
    Views.setClickable(b);
    b.setOnClickListener(click);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
    p.setMargins(Screen.dp(16f), Screen.dp(4f), Screen.dp(16f), 0);
    b.setLayoutParams(p);
    return b;
  }

  /** «+ New» on the home screen: a folder or a tag */
  public static void newFolderOrTag (ViewController<?> c, Runnable after) {
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(c.context(), Lang.getString(R.string.Tgx101SavedNew)));
      body.addView(Tgx101Sheet.row(c.context(), R.drawable.baseline_create_new_folder_24, 0, Lang.getString(R.string.Tgx101SavedNewFolder), null, false, v -> {
        h[0].dismiss();
        askName(c, Lang.getString(R.string.Tgx101SavedNewFolder), null, name -> {
          Tgx101SavedStore.get(c.tdlib()).createFolder(name);
          after.run();
        });
      }));
      body.addView(Tgx101Sheet.row(c.context(), R.drawable.baseline_label_24, 0, Lang.getString(R.string.Tgx101SavedNewTagTile), null, false, v -> {
        h[0].dismiss();
        askName(c, Lang.getString(R.string.Tgx101SavedNewTagTile), "#", name -> {
          Tgx101SavedStore store = Tgx101SavedStore.get(c.tdlib());
          store.rememberTag(name);
          store.setTagOnHome(name, true);
          after.run();
        });
      }));
    });
  }

  // To folder

  public static void toFolder (ViewController<?> c, long[] messageIds, @Nullable Runnable after) {
    if (messageIds.length == 0) return;
    Tgx101SavedStore store = Tgx101SavedStore.get(c.tdlib());
    Tgx101SavedStore.Folder current = messageIds.length == 1 ? store.folderOf(messageIds[0]) : null;
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(c.context(), Lang.getString(R.string.Tgx101SavedPutInFolder)));
      for (Tgx101SavedStore.Folder f : store.folders()) {
        body.addView(Tgx101Sheet.row(c.context(), 0, f.color(), f.name, String.valueOf(f.messageIds.size()), current != null && current.id == f.id, v -> {
          store.putInFolder(f.id, messageIds);
          h[0].dismiss();
          UI.showToast(Lang.getString(R.string.Tgx101SavedPutDone, f.name), android.widget.Toast.LENGTH_SHORT);
          if (after != null) after.run();
        }));
      }
      if (current != null) {
        body.addView(Tgx101Sheet.row(c.context(), R.drawable.baseline_archive_24, 0, Lang.getString(R.string.Tgx101SavedTakeOut), null, false, v -> {
          store.removeFromFolders(messageIds);
          h[0].dismiss();
          if (after != null) after.run();
        }));
      }
      body.addView(Tgx101Sheet.row(c.context(), R.drawable.baseline_create_new_folder_24, 0, Lang.getString(R.string.Tgx101SavedNewFolder), null, false, v -> {
        h[0].dismiss();
        askName(c, Lang.getString(R.string.Tgx101SavedNewFolder), null, name -> {
          Tgx101SavedStore.Folder f = store.createFolder(name);
          store.putInFolder(f.id, messageIds);
          UI.showToast(Lang.getString(R.string.Tgx101SavedPutDone, f.name), android.widget.Toast.LENGTH_SHORT);
          if (after != null) after.run();
        });
      }));
    });
  }

  // Tags

  /**
   * A tag: in one's own messages it's written as a #hashtag (seen on every device, found by Telegram's search);
   * forwarded messages can't be edited, so they keep the tag by name on this phone (searchable the same way)
   */
  public static void tag (ViewController<?> c, List<TdApi.Message> messages, @Nullable Runnable after) {
    if (messages.isEmpty()) return;
    Context context = c.context();
    Tgx101SavedStore store = Tgx101SavedStore.get(c.tdlib());
    boolean anyForwarded = false;
    for (TdApi.Message m : messages) if (!Tgx101SavedKit.isOwnEditable(m)) anyForwarded = true;
    final boolean someLocal = anyForwarded || !store.hashtagOwn;
    final EditText[] field = new EditText[1];
    final boolean[] onHome = {false};
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101SavedTag)));
      LinearLayout input = new LinearLayout(context);
      input.setOrientation(LinearLayout.HORIZONTAL);
      input.setGravity(Gravity.CENTER_VERTICAL);
      input.setBackground(Tgx101SavedHomeController.card(Theme.backgroundColor(), 14f));
      input.setPadding(Screen.dp(14f), 0, Screen.dp(14f), 0);
      TextView hash = Tgx101SavedHomeController.text(context, 18f, Tgx101SavedHomeController.accent(), true);
      hash.setText("#");
      input.addView(hash);
      EditText e = new EditText(context);
      field[0] = e;
      e.setSingleLine(true);
      e.setBackground(null);
      e.setHint(Lang.getString(R.string.Tgx101SavedTagHint));
      e.setHintTextColor(Theme.textDecentColor());
      e.setTextColor(Theme.textAccentColor());
      e.setImeOptions(EditorInfo.IME_ACTION_DONE);
      e.setPadding(Screen.dp(6f), 0, 0, 0);
      input.addView(e, new LinearLayout.LayoutParams(0, Screen.dp(46f), 1f));
      LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(46f));
      ip.setMargins(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(8f));
      body.addView(input, ip);

      TextView explain = Tgx101SavedHomeController.text(context, 12f, Theme.textDecentColor(), false);
      explain.setText(Lang.getString(someLocal ? R.string.Tgx101SavedTagExplainLocal : R.string.Tgx101SavedTagExplainHashtag));
      explain.setPadding(Screen.dp(20f), 0, Screen.dp(20f), Screen.dp(8f));
      body.addView(explain);

      List<String> known = store.knownTags();
      if (!known.isEmpty()) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(Screen.dp(16f), 0, Screen.dp(8f), Screen.dp(8f));
        for (String t : known) {
          TextView chip = Tgx101SavedHomeController.text(context, 13f, Theme.textAccentColor(), false);
          chip.setText("#" + t);
          chip.setGravity(Gravity.CENTER);
          chip.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
          chip.setBackground(Tgx101SavedHomeController.card(Theme.backgroundColor(), 16f));
          Views.setClickable(chip);
          chip.setOnClickListener(v -> {
            e.setText(t);
            e.setSelection(t.length());
          });
          LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(32f));
          cp.rightMargin = Screen.dp(8f);
          chips.addView(chip, cp);
        }
        scroll.addView(chips);
        body.addView(scroll);
      }

      LinearLayout homeRow = new LinearLayout(context);
      homeRow.setOrientation(LinearLayout.HORIZONTAL);
      homeRow.setGravity(Gravity.CENTER_VERTICAL);
      homeRow.setPadding(Screen.dp(20f), 0, Screen.dp(12f), 0);
      TextView homeLabel = Tgx101SavedHomeController.text(context, 15f, Theme.textAccentColor(), false);
      homeLabel.setText(Lang.getString(R.string.Tgx101SavedTagOnHome));
      homeRow.addView(homeLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      android.widget.CheckBox box = new android.widget.CheckBox(context);
      box.setOnCheckedChangeListener((b, checked) -> onHome[0] = checked);
      homeRow.addView(box);
      body.addView(homeRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f)));

      Runnable submit = () -> {
        String t = Tgx101SavedStore.normalizeTag(e.getText().toString());
        if (t.isEmpty()) return;
        org.thunderdog.challegram.tool.Keyboard.hide(e);
        h[0].dismiss();
        applyTag(c, messages, t, onHome[0]);
        if (after != null) after.run();
      };
      e.setOnEditorActionListener((v, actionId, event) -> {
        submit.run();
        return true;
      });
      body.addView(primaryButton(context, Lang.getString(R.string.Tgx101SavedTagApply), v -> submit.run()));
    });
    if (field[0] != null) {
      field[0].requestFocus();
      field[0].postDelayed(() -> org.thunderdog.challegram.tool.Keyboard.show(field[0]), 280);
    }
  }

  private static void applyTag (ViewController<?> c, List<TdApi.Message> messages, String tag, boolean onHome) {
    Tdlib tdlib = c.tdlib();
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    store.rememberTag(tag);
    if (onHome) store.setTagOnHome(tag, true);
    List<Long> local = new ArrayList<>();
    for (TdApi.Message m : messages) {
      if (store.hashtagOwn && Tgx101SavedKit.isOwnEditable(m) && appendHashtag(tdlib, m, tag)) continue;
      local.add(m.id);
    }
    if (!local.isEmpty()) {
      long[] ids = new long[local.size()];
      for (int i = 0; i < ids.length; i++) ids[i] = local.get(i);
      store.addLocalTag(tag, ids);
    }
    UI.showToast(Lang.getString(R.string.Tgx101SavedTagDone, "#" + tag), android.widget.Toast.LENGTH_SHORT);
  }

  /** «… #tag» at the end of one's own text / caption (already there — nothing to do) */
  private static boolean appendHashtag (Tdlib tdlib, TdApi.Message m, String tag) {
    TdApi.FormattedText text = Tgx101SavedKit.text(m);
    String hashtag = "#" + tag;
    String current = text != null ? text.text : "";
    if (current.toLowerCase(java.util.Locale.ROOT).contains(hashtag)) return true;
    String sep = current.isEmpty() ? "" : (current.endsWith("\n") ? "" : "\n");
    String newText = current + sep + hashtag;
    TdApi.TextEntity[] old = text != null && text.entities != null ? text.entities : new TdApi.TextEntity[0];
    TdApi.TextEntity[] entities = new TdApi.TextEntity[old.length + 1];
    System.arraycopy(old, 0, entities, 0, old.length);
    entities[old.length] = new TdApi.TextEntity(current.length() + sep.length(), hashtag.length(), new TdApi.TextEntityTypeHashtag());
    TdApi.FormattedText formatted = new TdApi.FormattedText(newText, entities);
    if (m.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR) {
      TdApi.MessageText t = (TdApi.MessageText) m.content;
      tdlib.send(new TdApi.EditMessageText(m.chatId, m.id, null, new TdApi.InputMessageText(formatted, t.linkPreviewOptions, false)), (r, error) -> {
        if (error != null) UI.post(() -> Tgx101SavedStore.get(tdlib).addLocalTag(tag, new long[] {m.id}));
      });
    } else {
      tdlib.send(new TdApi.EditMessageCaption(m.chatId, m.id, null, formatted, false), (r, error) -> {
        if (error != null) UI.post(() -> Tgx101SavedStore.get(tdlib).addLocalTag(tag, new long[] {m.id}));
      });
    }
    return true;
  }

  // Remind

  /** A reminder is Telegram's own: a copy scheduled into Saved Messages at the chosen time */
  public static void remind (ViewController<?> c, long[] messageIds, @Nullable Runnable after) {
    if (messageIds.length == 0) return;
    Context context = c.context();
    Calendar now = Calendar.getInstance();
    Calendar evening = (Calendar) now.clone();
    evening.set(Calendar.HOUR_OF_DAY, 20);
    evening.set(Calendar.MINUTE, 0);
    evening.set(Calendar.SECOND, 0);
    if (!evening.after(now)) evening.add(Calendar.DAY_OF_MONTH, 1);
    Calendar morning = (Calendar) now.clone();
    morning.add(Calendar.DAY_OF_MONTH, 1);
    morning.set(Calendar.HOUR_OF_DAY, 9);
    morning.set(Calendar.MINUTE, 0);
    morning.set(Calendar.SECOND, 0);
    // «This evening» less than two hours away is almost «in an hour» (test 2026-10-15 at 19:00: 19:59 and 20:00)
    boolean eveningSoon = evening.getTimeInMillis() - now.getTimeInMillis() < 2 * 3600 * 1000L;
    final long[] at = eveningSoon ? new long[] {
      now.getTimeInMillis() / 1000 + 3600,
      morning.getTimeInMillis() / 1000,
      now.getTimeInMillis() / 1000 + 7 * 86400
    } : new long[] {
      now.getTimeInMillis() / 1000 + 3600,
      evening.getTimeInMillis() / 1000,
      morning.getTimeInMillis() / 1000,
      now.getTimeInMillis() / 1000 + 7 * 86400
    };
    int[] names = eveningSoon ? new int[] {R.string.Tgx101SavedRemindHour, R.string.Tgx101SavedRemindMorning, R.string.Tgx101SavedRemindWeek}
      : new int[] {R.string.Tgx101SavedRemindHour, R.string.Tgx101SavedRemindEvening, R.string.Tgx101SavedRemindMorning, R.string.Tgx101SavedRemindWeek};
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101SavedRemind)));
      for (int i = 0; i < at.length; i++) {
        final long when = at[i];
        String value = Lang.getRelativeTimestamp(when, java.util.concurrent.TimeUnit.SECONDS);
        body.addView(Tgx101Sheet.row(context, R.drawable.baseline_schedule_24, 0, Lang.getString(names[i]), value, false, v -> {
          h[0].dismiss();
          TdApi.MessageSendOptions options = new TdApi.MessageSendOptions();
          options.schedulingState = new TdApi.MessageSchedulingStateSendAtDate((int) when, 0);
          c.tdlib().send(new TdApi.ForwardMessages(c.tdlib().selfChatId(), null, c.tdlib().selfChatId(), messageIds, options, true, false), (r, error) -> UI.post(() -> {
            if (error != null) {
              UI.showError(error);
            } else {
              UI.showToast(Lang.getString(R.string.Tgx101SavedRemindDone, Lang.getRelativeTimestamp(when, java.util.concurrent.TimeUnit.SECONDS)), android.widget.Toast.LENGTH_SHORT);
              if (after != null) after.run();
            }
          }));
        }));
      }
    });
  }

  // Forward, delete

  public static void forward (ViewController<?> c, List<TdApi.Message> messages) {
    if (messages.isEmpty()) return;
    ShareController share = new ShareController(c.context(), c.tdlib());
    share.setArguments(new ShareController.Args(messages.toArray(new TdApi.Message[0])));
    share.show();
  }

  public static void delete (ViewController<?> c, long[] messageIds, @Nullable Runnable after) {
    if (messageIds.length == 0) return;
    c.showOptions(Lang.getString(R.string.Tgx101SavedDeleteAsk, messageIds.length), new int[] {R.id.btn_delete, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Tgx101SavedDelete), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_delete) {
          c.tdlib().send(new TdApi.DeleteMessages(c.tdlib().selfChatId(), messageIds, true), (ok, error) -> UI.post(() -> {
            if (error != null) UI.showError(error);
            else if (after != null) after.run();
          }));
        }
        return true;
      });
  }

  // «Saved» banner

  /**
   * TGx101 (mockups 7 and 9): after something is saved to Saved Messages — from another app or inside PlumaGram —
   * a dark banner at the bottom offers a folder, a tag or a reminder. It doesn't block the screen; it goes after 4 s
   * (held while touched) or on a swipe down; the Saved settings can turn it off.
   */
  public static void showSavedBanner (Tdlib tdlib, long[] messageIds, boolean external) {
    if (messageIds == null || messageIds.length == 0) return;
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    org.thunderdog.challegram.Tgx101Diag.mark("saved banner: " + messageIds.length + " saved" + (external ? " from another app" : "") + ", mode " + store.sheetMode);
    if (store.sheetMode == Tgx101SavedStore.SHEET_NEVER || (store.sheetMode == Tgx101SavedStore.SHEET_EXTERNAL_ONLY && !external)) return;
    UI.post(() -> {
      BaseActivity activity = UI.getUiContext();
      if (activity == null || activity.isFinishing()) return;
      ViewController<?> c = activity.navigation().getCurrentStackItem();
      if (c == null) return;
      refreshOpenSaved(c);
      Banner.show(activity, c, tdlib, messageIds);
    });
  }

  private static final class Banner {
    static void show (BaseActivity activity, ViewController<?> c, Tdlib tdlib, long[] ids) {
      // the window's top layer: the app draws its own root over the standard content frame (the banner was hidden there)
      ViewGroup content = (ViewGroup) activity.getWindow().getDecorView();
      if (content == null) return;
      org.thunderdog.challegram.Tgx101Diag.mark("saved banner: shown for " + ids.length);
      Context context = activity;
      final FrameLayout holder = new FrameLayout(context);
      final LinearLayout card = new LinearLayout(context) {
        float downY;
        boolean dragging;

        @Override
        public boolean onInterceptTouchEvent (MotionEvent e) {
          if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downY = e.getRawY();
            dragging = false;
            holder.removeCallbacks((Runnable) getTag());
          } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE && e.getRawY() - downY > Screen.dp(8f)) {
            dragging = true;
            return true;
          }
          return false;
        }

        @Override
        public boolean onTouchEvent (MotionEvent e) {
          switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
              downY = e.getRawY();
              holder.removeCallbacks((Runnable) getTag());
              return true;
            case MotionEvent.ACTION_MOVE:
              setTranslationY(Math.max(0f, e.getRawY() - downY));
              setAlpha(1f - Math.min(.6f, getTranslationY() / (getHeight() * 1.6f)));
              return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
              if (getTranslationY() > getHeight() / 3f) {
                ((Runnable) getTag()).run();
              } else {
                animate().translationY(0f).alpha(1f).setDuration(160).start();
                holder.postDelayed((Runnable) getTag(), 4000);
              }
              return true;
          }
          return super.onTouchEvent(e);
        }
      };
      card.setOrientation(LinearLayout.VERTICAL);
      // dark ink on a light theme; on a dark one a step lighter than the screen, so it doesn't melt into it
      card.setBackground(Tgx101SavedHomeController.card(Theme.isDark() ? 0xff33414f : 0xff17212b, 18f));
      card.setPadding(Screen.dp(14f), Screen.dp(8f), Screen.dp(14f), Screen.dp(12f));
      if (android.os.Build.VERSION.SDK_INT >= 21) card.setElevation(Screen.dp(6f));
      card.setClickable(true);

      View grip = new View(context);
      grip.setBackground(Tgx101SavedHomeController.card(0x59ffffff, 2f));
      LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Screen.dp(36f), Screen.dp(4f));
      gp.gravity = Gravity.CENTER_HORIZONTAL;
      gp.bottomMargin = Screen.dp(8f);
      card.addView(grip, gp);

      LinearLayout head = new LinearLayout(context);
      head.setOrientation(LinearLayout.HORIZONTAL);
      head.setGravity(Gravity.CENTER_VERTICAL);
      android.widget.ImageView icon = new android.widget.ImageView(context);
      icon.setImageResource(R.drawable.baseline_bookmark_24);
      icon.setColorFilter(0xffffffff);
      icon.setScaleType(android.widget.ImageView.ScaleType.CENTER);
      icon.setBackground(Tgx101SavedHomeController.card(0xff3f7cad, 12f));
      head.addView(icon, new LinearLayout.LayoutParams(Screen.dp(40f), Screen.dp(40f)));
      LinearLayout texts = new LinearLayout(context);
      texts.setOrientation(LinearLayout.VERTICAL);
      texts.setPadding(Screen.dp(12f), 0, 0, 0);
      TextView t1 = Tgx101SavedHomeController.text(context, 15f, 0xffffffff, true);
      t1.setText(Lang.getString(R.string.Tgx101SavedBannerTitle));
      texts.addView(t1);
      TextView t2 = Tgx101SavedHomeController.text(context, 12f, 0xffb9c4cd, false);
      t2.setText(Lang.getString(R.string.Tgx101SavedBannerHint));
      texts.addView(t2);
      head.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      card.addView(head);

      LinearLayout buttons = new LinearLayout(context);
      buttons.setOrientation(LinearLayout.HORIZONTAL);
      buttons.setPadding(0, Screen.dp(12f), 0, 0);
      card.addView(buttons);

      final Runnable[] close = new Runnable[1];
      close[0] = () -> {
        holder.removeCallbacks(close[0]);
        card.animate().translationY(card.getHeight() + Screen.dp(40f)).alpha(0f).setDuration(200)
          .setInterpolator(new android.view.animation.AccelerateInterpolator(1.2f))
          .withEndAction(() -> Views.removeFromParent(holder)).start();
      };
      card.setTag(close[0]);
      int[] labels = {R.string.Tgx101SavedToFolder, R.string.Tgx101SavedTag, R.string.Tgx101SavedRemind};
      for (int i = 0; i < labels.length; i++) {
        final int which = i;
        TextView b = Tgx101SavedHomeController.text(context, 14f, 0xffffffff, false);
        b.setText(Lang.getString(labels[i]));
        b.setGravity(Gravity.CENTER);
        b.setBackground(Tgx101SavedHomeController.card(0x1fffffff, 12f));
        Views.setClickable(b);
        b.setOnClickListener(v -> {
          close[0].run();
          if (which == 0) {
            toFolder(c, ids, () -> refreshOpenSaved(c));
          } else if (which == 1) {
            Tgx101SavedKit.byIds(tdlib, toList(ids), messages -> tag(c, messages, null));
          } else {
            remind(c, ids, null);
          }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f);
        if (i > 0) bp.leftMargin = Screen.dp(8f);
        buttons.addView(b, bp);
      }

      FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
      cp.setMargins(Screen.dp(12f), 0, Screen.dp(12f), Screen.dp(20f) + Screen.getNavigationBarHeight());
      holder.addView(card, cp);
      content.addView(holder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      // the empty part of the holder lets touches through to the screen below
      holder.setClickable(false);
      card.setTranslationY(Screen.dp(160f));
      card.setAlpha(0f);
      card.animate().translationY(0f).alpha(1f).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
      holder.postDelayed(close[0], 4000);
    }
  }

  /** An open Saved screen shows what was just saved / filed (test 2026-10-15: the list stayed as it was) */
  static void refreshOpenSaved (@Nullable ViewController<?> c) {
    if (c instanceof Tgx101SavedListController) {
      ((Tgx101SavedListController) c).reloadSoon();
    } else if (c instanceof Tgx101SavedHomeController) {
      ((Tgx101SavedHomeController) c).reloadSoon();
    }
  }

  static List<Long> toList (long[] ids) {
    List<Long> out = new ArrayList<>(ids.length);
    for (long id : ids) out.add(id);
    return out;
  }
}
