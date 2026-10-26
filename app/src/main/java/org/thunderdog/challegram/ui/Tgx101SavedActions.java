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
      e.setImeOptions(EditorInfo.IME_ACTION_DONE);
      Tgx101SavedHomeController.styleField(e, true);
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
      field[0].postDelayed(() -> { if (androidx.core.view.ViewCompat.isAttachedToWindow(field[0])) org.thunderdog.challegram.tool.Keyboard.show(field[0]); }, 280);
    }
  }

  /** like {@link #askName}, but an empty answer is allowed (it clears) and the field has its own hint */
  public static void askText (ViewController<?> c, String title, @Nullable String initial, String hint, RunnableData<String> done) {
    Context context = c.context();
    final EditText[] field = new EditText[1];
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(context, title));
      EditText e = new EditText(context);
      field[0] = e;
      e.setText(initial);
      e.setSingleLine(true);
      e.setHint(hint);
      e.setImeOptions(EditorInfo.IME_ACTION_DONE);
      Tgx101SavedHomeController.styleField(e, true);
      LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
      ep.setMargins(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(12f));
      body.addView(e, ep);
      Runnable submit = () -> {
        org.thunderdog.challegram.tool.Keyboard.hide(e);
        h[0].dismiss();
        done.runWithData(e.getText().toString().trim());
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
      field[0].postDelayed(() -> { if (androidx.core.view.ViewCompat.isAttachedToWindow(field[0])) org.thunderdog.challegram.tool.Keyboard.show(field[0]); }, 280);
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
      // (user 2026-10-21 «3 вариант с ИИ»): the AI puts the records without a folder into folders
      if (org.thunderdog.challegram.unsorted.Settings.instance().tgx101AiSort()) body.addView(Tgx101Sheet.row(c.context(), R.drawable.baseline_stars_24, 0, Lang.getString(R.string.Tgx101SavedAiSort), null, false, v -> {
        h[0].dismiss();
        c.navigateTo(new Tgx101SavedAiSortController(c.context(), c.tdlib()));
      }));
      Tgx101SavedAiSortController.Undo undo = Tgx101SavedAiSortController.pending(c.tdlib());
      if (undo != null) {
        body.addView(Tgx101Sheet.row(c.context(), R.drawable.baseline_undo_24, 0, Lang.getString(R.string.Tgx101SavedAiUndo, undo.count), null, false, v -> {
          h[0].dismiss();
          int n = Tgx101SavedAiSortController.undo(c.tdlib());
          UI.showToast(Lang.getString(R.string.Tgx101SavedAiUndone, n), android.widget.Toast.LENGTH_SHORT);
          after.run();
        }));
      }
      // (user 2026-10-17 «если убрать метку с главного экрана, то как её вернуть?»): the tags that aren't tiles, one tap back
      Tgx101SavedStore store = Tgx101SavedStore.get(c.tdlib());
      List<String> hidden = new ArrayList<>();
      for (String t : store.knownTags()) if (!store.isTagOnHome(t)) hidden.add(t);
      if (!hidden.isEmpty()) {
        body.addView(Tgx101Sheet.title(c.context(), Lang.getString(R.string.Tgx101SavedTagsBack)));
        for (String t : hidden) {
          body.addView(Tgx101Sheet.row(c.context(), R.drawable.baseline_label_24, 0, "#" + t, null, false, v -> {
            h[0].dismiss();
            store.setTagOnHome(t, true);
            after.run();
          }));
        }
      }
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
      input.setBackground(Tgx101SavedHomeController.bubble(true));
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
      Tgx101SavedHomeController.size(e, 16f);
      Tgx101SavedHomeController.cursor(e);
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
      field[0].postDelayed(() -> { if (androidx.core.view.ViewCompat.isAttachedToWindow(field[0])) org.thunderdog.challegram.tool.Keyboard.show(field[0]); }, 280);
    }
  }

  static void applyTag (ViewController<?> c, List<TdApi.Message> messages, String tag, boolean onHome) {
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

  /**
   * (user 2026-10-17 «нужно дать возможность переименовывать теги в хранилище»): #old becomes #new in one's own texts and
   * captions; messages that can't be edited (forwarded) keep it as a tag of this phone under the new name
   */
  public static void renameTag (ViewController<?> c, String tag, @Nullable Runnable after) {
    String old = Tgx101SavedStore.normalizeTag(tag);
    askName(c, Lang.getString(R.string.Tgx101SavedTagRename), "#" + old, name -> {
      String renamed = Tgx101SavedStore.normalizeTag(name);
      if (renamed.isEmpty() || renamed.equals(old)) return;
      Tdlib tdlib = c.tdlib();
      Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
      store.renameLocalTag(old, renamed);
      if (after != null) after.run();
      Tgx101SavedKit.search(tdlib, null, "#" + old, 1000, found -> {
        int edited = 0;
        List<Long> local = new ArrayList<>();
        for (TdApi.Message m : found) {
          TdApi.FormattedText text = Tgx101SavedKit.text(m);
          TdApi.FormattedText replaced = text != null ? replaceHashtag(text, old, renamed) : null;
          if (replaced == null) continue; // the search's own fuzziness: no such word there
          if (Tgx101SavedKit.isOwnEditable(m)) {
            sendEdited(tdlib, m, replaced, renamed);
            edited++;
          } else {
            local.add(m.id);
          }
        }
        if (!local.isEmpty()) {
          long[] ids = new long[local.size()];
          for (int i = 0; i < ids.length; i++) ids[i] = local.get(i);
          store.addLocalTag(renamed, ids);
        }
        org.thunderdog.challegram.Tgx101Diag.mark("storage: tag renamed, " + edited + " texts edited, " + local.size() + " kept on the phone");
        UI.showToast(Lang.getString(R.string.Tgx101SavedTagRenamed, "#" + renamed), android.widget.Toast.LENGTH_SHORT);
        if (after != null) after.run();
      });
    });
  }

  /** Every whole «#old» (any case) → «#renamed», the entities after it moved by the difference; null — none there */
  static @Nullable TdApi.FormattedText replaceHashtag (TdApi.FormattedText text, String old, String renamed) {
    String src = text.text;
    String lower = src.toLowerCase(java.util.Locale.ROOT);
    String needle = "#" + old, replacement = "#" + renamed;
    StringBuilder out = new StringBuilder();
    List<int[]> changes = new ArrayList<>(); // [start in the old text, end in the old text]
    int prev = 0, from = 0;
    while (true) {
      int at = lower.indexOf(needle, from);
      if (at < 0) break;
      int end = at + needle.length();
      from = end;
      boolean whole = (end >= src.length() || !(Character.isLetterOrDigit(src.charAt(end)) || src.charAt(end) == '_'))
        && (at == 0 || !(Character.isLetterOrDigit(src.charAt(at - 1)) || src.charAt(at - 1) == '_'));
      if (!whole) continue;
      out.append(src, prev, at).append(replacement);
      prev = end;
      changes.add(new int[] {at, end});
    }
    if (changes.isEmpty()) return null;
    out.append(src, prev, src.length());
    int delta = replacement.length() - needle.length();
    List<TdApi.TextEntity> moved = new ArrayList<>();
    if (text.entities != null) {
      for (TdApi.TextEntity e : text.entities) {
        int start = e.offset, stop = e.offset + e.length, newStart = start, newStop = stop;
        for (int[] ch : changes) {
          if (ch[1] <= start) newStart += delta; // wholly before the entity
          if (ch[0] < stop) newStop += delta; // before its end (or the hashtag itself)
        }
        if (newStop > newStart) moved.add(new TdApi.TextEntity(newStart, newStop - newStart, e.type));
      }
    }
    return new TdApi.FormattedText(out.toString(), moved.toArray(new TdApi.TextEntity[0]));
  }

  private static void sendEdited (Tdlib tdlib, TdApi.Message m, TdApi.FormattedText formatted, String tag) {
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
    pickTime(c, when -> {
      TdApi.MessageSendOptions options = new TdApi.MessageSendOptions();
      options.schedulingState = new TdApi.MessageSchedulingStateSendAtDate((int) (long) when, 0);
      c.tdlib().send(new TdApi.ForwardMessages(c.tdlib().selfChatId(), null, c.tdlib().selfChatId(), messageIds, options, true, false), (r, error) -> UI.post(() -> {
        if (error != null) {
          UI.showError(error);
        } else {
          UI.showToast(Lang.getString(R.string.Tgx101SavedRemindDone, Lang.getRelativeTimestamp(when, java.util.concurrent.TimeUnit.SECONDS)), android.widget.Toast.LENGTH_SHORT);
          if (after != null) after.run();
        }
      }));
    });
  }

  /** In an hour, (this evening), tomorrow morning, in a week — in seconds */
  static long[] remindTimes () {
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
    boolean eveningSoon = evening.getTimeInMillis() - now.getTimeInMillis() < 2 * 3600 * 1000L;
    long n = now.getTimeInMillis() / 1000;
    return eveningSoon ? new long[] {n + 3600, morning.getTimeInMillis() / 1000, n + 7 * 86400}
      : new long[] {n + 3600, evening.getTimeInMillis() / 1000, morning.getTimeInMillis() / 1000, n + 7 * 86400};
  }

  static int[] remindNames (int count) {
    return count == 3 ? new int[] {R.string.Tgx101SavedRemindHour, R.string.Tgx101SavedRemindMorning, R.string.Tgx101SavedRemindWeek}
      : new int[] {R.string.Tgx101SavedRemindHour, R.string.Tgx101SavedRemindEvening, R.string.Tgx101SavedRemindMorning, R.string.Tgx101SavedRemindWeek};
  }

  /** In an hour, this evening, tomorrow morning, in a week — the time in seconds */
  static void pickTime (ViewController<?> c, RunnableData<Long> picked) {
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
          picked.runWithData(when);
        }));
      }
    });
  }

  // Note: where to

  /**
   * TGx101 (user 2026-10-15): holding «Send» under the note field asks where the note goes — straight into a folder,
   * with a tag, as a reminder, or to another chat
   */
  public static void noteDestinations (ViewController<?> c, TdApi.FormattedText note, Runnable picked, Runnable sentHere) {
    Context context = c.context();
    Tdlib tdlib = c.tdlib();
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101SavedNoteWhere)));
      // a reaction: the note is saved with it at once
      LinearLayout reactions = new LinearLayout(context);
      reactions.setOrientation(LinearLayout.HORIZONTAL);
      reactions.setPadding(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(6f));
      for (String e : Tgx101SavedKit.QUICK_REACTIONS) {
        TextView b = Tgx101SavedHomeController.text(context, 20f, Theme.textAccentColor(), false);
        b.setText(Tgx101SavedKit.emoji(e));
        b.setGravity(Gravity.CENTER);
        b.setBackground(Tgx101SavedHomeController.card(Theme.backgroundColor(), 20f));
        Views.setClickable(b);
        b.setOnClickListener(v -> {
          h[0].dismiss();
          picked.run(); // the field is emptied only when the note goes somewhere
          sendNote(c, note, null, saved -> Tgx101SavedKit.toggleReaction(tdlib, saved, e, added -> sentHere.run()));
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, Screen.dp(42f), 1f);
        rp.rightMargin = Screen.dp(8f);
        reactions.addView(b, rp);
      }
      body.addView(reactions);
      for (Tgx101SavedStore.Folder f : store.folders()) {
        body.addView(Tgx101Sheet.row(context, 0, f.color(), f.name, null, false, v -> {
          h[0].dismiss();
          picked.run(); // the field is emptied only when the note goes somewhere
          sendNote(c, note, null, saved -> {
            store.putInFolder(f.id, Tgx101SavedKit.idsOf(saved));
            UI.showToast(Lang.getString(R.string.Tgx101SavedPutDone, f.name), android.widget.Toast.LENGTH_SHORT);
            sentHere.run();
          });
        }));
      }
      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_label_24, 0, Lang.getString(R.string.Tgx101SavedNoteWithTag), null, false, v -> {
        h[0].dismiss();
          picked.run(); // the field is emptied only when the note goes somewhere
        sendNote(c, note, null, saved -> {
          sentHere.run();
          tag(c, saved, sentHere);
        });
      }));
      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_schedule_24, 0, Lang.getString(R.string.Tgx101SavedNoteRemind), null, false, v -> {
        h[0].dismiss();
          picked.run(); // the field is emptied only when the note goes somewhere
        pickTime(c, when -> {
          TdApi.MessageSendOptions options = new TdApi.MessageSendOptions();
          options.schedulingState = new TdApi.MessageSchedulingStateSendAtDate((int) (long) when, 0);
          sendNote(c, note, options, saved -> UI.showToast(Lang.getString(R.string.Tgx101SavedRemindDone, Lang.getRelativeTimestamp(when, java.util.concurrent.TimeUnit.SECONDS)), android.widget.Toast.LENGTH_SHORT));
        });
      }));
      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_forward_24, 0, Lang.getString(R.string.Tgx101SavedNoteOtherChat), null, false, v -> {
        h[0].dismiss();
          picked.run(); // the field is emptied only when the note goes somewhere
        ShareController share = new ShareController(context, tdlib);
        share.setArguments(new ShareController.Args(note));
        share.show();
      }));
    });
  }

  private static void findSent (Tdlib tdlib, TdApi.Message sent, int since, RunnableData<List<TdApi.Message>> done, int attempt) {
    UI.post(() -> Tgx101SavedKit.resolveSaved(tdlib, new long[] {sent.id}, since, saved -> {
      boolean real = !saved.isEmpty() && saved.get(0).sendingState == null && (saved.get(0).id & ((1L << 20) - 1)) == 0;
      if (real) {
        done.runWithData(saved);
      } else if (attempt < 8) {
        findSent(tdlib, sent, since, done, attempt + 1);
      } else {
        org.thunderdog.challegram.Tgx101Diag.mark("saved note: the sent note wasn't found");
      }
    }), attempt == 0 ? 400 : 700);
  }

  /** Sends the note to Saved; {@code done} gets it with its real id (scheduled ones as Telegram answered) */
  static void sendNote (ViewController<?> c, TdApi.FormattedText note, @Nullable TdApi.MessageSendOptions options, RunnableData<List<TdApi.Message>> done) {
    Tdlib tdlib = c.tdlib();
    Tgx101SavedStore.suppressBanner(15000); // a note typed here needs no «put in folder?» banner
    final int since = (int) (System.currentTimeMillis() / 1000L) - 5;
    tdlib.send(new TdApi.SendMessage(tdlib.selfChatId(), null, null, options, null, new TdApi.InputMessageText(note, null, false)), (sent, error) -> UI.post(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      if (options != null) {
        List<TdApi.Message> one = new ArrayList<>();
        one.add(sent);
        done.runWithData(one);
        return;
      }
      // the answer has the id the note has while it's being sent; the «sent» update for Saved can come before we start
      // waiting for it (test 2026-10-16: the note never reached its folder) — the real one is looked up a few times
      findSent(tdlib, sent, since, done, 0);
    }));
  }

  // Reactions, «More», organize (approved mockups «Недавнее», А–Г)

  /** ❤️ 🔥 👍 ⭐ + : lit when every one of the messages has it; a tap puts it on all / takes it off */
  static View reactionRow (ViewController<?> c, List<TdApi.Message> messages, @Nullable Runnable changed) {
    Context context = c.context();
    Tdlib tdlib = c.tdlib();
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(Screen.dp(16f), Screen.dp(4f), Screen.dp(16f), Screen.dp(8f));
    final List<String> shown = new ArrayList<>();
    for (String e : Tgx101SavedKit.QUICK_REACTIONS) shown.add(e);
    // a reaction already on them that isn't in the quick row is shown too
    for (TdApi.Message m : messages) for (String e : Tgx101SavedKit.reactionsOf(tdlib, m)) if (!shown.contains(e) && shown.size() < 6) shown.add(e);
    final List<TextView> buttons = new ArrayList<>();
    final Runnable refresh = () -> {
      for (TextView b : buttons) {
        String e = (String) b.getTag();
        boolean all = true;
        for (TdApi.Message m : messages) if (!Tgx101SavedKit.reactionsOf(tdlib, m).contains(e)) all = false;
        b.setBackground(Tgx101SavedHomeController.card(all ? Tgx101SavedHomeController.tint(Tgx101SavedHomeController.accent(), .22f) : Theme.backgroundColor(), 20f));
      }
    };
    for (String e : shown) {
      TextView b = Tgx101SavedHomeController.text(context, 20f, Theme.textAccentColor(), false);
      b.setText(Tgx101SavedKit.emoji(e));
      b.setTag(e);
      b.setGravity(Gravity.CENTER);
      Views.setClickable(b);
      b.setOnClickListener(v -> {
        UI.forceVibrate(v, false);
        Tgx101SavedKit.toggleReaction(tdlib, messages, e, added -> {
          markLocally(tdlib, messages, e, added);
          refresh.run();
          if (changed != null) changed.run();
        });
      });
      buttons.add(b);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Screen.dp(42f), 1f);
      p.rightMargin = Screen.dp(8f);
      row.addView(b, p);
    }
    TextView more = Tgx101SavedHomeController.text(context, 18f, Theme.textDecentColor(), false);
    more.setText("+");
    more.setGravity(Gravity.CENTER);
    more.setContentDescription(Lang.getString(R.string.Tgx101SavedMoreReactions));
    more.setBackground(Tgx101SavedHomeController.card(Theme.backgroundColor(), 20f));
    Views.setClickable(more);
    more.setOnClickListener(v -> morePicker(c, emoji -> Tgx101SavedKit.toggleReaction(tdlib, messages, emoji, added -> {
      markLocally(tdlib, messages, emoji, added);
      UI.showToast(emoji, android.widget.Toast.LENGTH_SHORT);
      if (changed != null) changed.run();
    })));
    row.addView(more, new LinearLayout.LayoutParams(0, Screen.dp(42f), 1f));
    refresh.run();
    return row;
  }

  /** the shown messages get the reaction at once (Telegram's update comes later) */
  private static void markLocally (Tdlib tdlib, List<TdApi.Message> messages, String emoji, boolean added) {
    if (!Tgx101SavedKit.serverReactions(tdlib)) return;
    for (TdApi.Message m : messages) {
      if (m.interactionInfo == null) m.interactionInfo = new TdApi.MessageInteractionInfo();
      if (m.interactionInfo.reactions == null) m.interactionInfo.reactions = new TdApi.MessageReactions(new TdApi.MessageReaction[0], true, new TdApi.PaidReactor[0], false);
      List<TdApi.MessageReaction> list = new ArrayList<>();
      for (TdApi.MessageReaction r : m.interactionInfo.reactions.reactions) {
        boolean same = r.type.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR && ((TdApi.ReactionTypeEmoji) r.type).emoji.equals(emoji);
        if (!same) list.add(r);
      }
      if (added) list.add(new TdApi.MessageReaction(new TdApi.ReactionTypeEmoji(emoji), 1, true, null, new TdApi.MessageSender[0]));
      m.interactionInfo.reactions.reactions = list.toArray(new TdApi.MessageReaction[0]);
    }
  }

  private static void morePicker (ViewController<?> c, RunnableData<String> picked) {
    Context context = c.context();
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101SavedReaction)));
      LinearLayout grid = new LinearLayout(context);
      grid.setOrientation(LinearLayout.VERTICAL);
      grid.setPadding(Screen.dp(12f), 0, Screen.dp(12f), Screen.dp(8f));
      LinearLayout line = null;
      String[] all = Tgx101SavedKit.MORE_REACTIONS;
      for (int i = 0; i < all.length; i++) {
        if (i % 8 == 0) {
          line = new LinearLayout(context);
          line.setOrientation(LinearLayout.HORIZONTAL);
          grid.addView(line);
        }
        final String e = all[i];
        TextView b = Tgx101SavedHomeController.text(context, 24f, Theme.textAccentColor(), false);
        b.setText(Tgx101SavedKit.emoji(e));
        b.setGravity(Gravity.CENTER);
        Views.setClickable(b);
        org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(b);
        b.setOnClickListener(v -> {
          h[0].dismiss();
          picked.runWithData(e);
        });
        line.addView(b, new LinearLayout.LayoutParams(0, Screen.dp(48f), 1f));
      }
      body.addView(grid);
    });
  }

  // Swipe buttons (one tap: the last folder / tag; hold: the sheet for everything)

  static void swipeFolder (ViewController<?> c, TdApi.Message m, boolean longPress, Runnable changed) {
    Tgx101SavedStore store = Tgx101SavedStore.get(c.tdlib());
    Tgx101SavedStore.Folder last = store.folder(store.lastFolderId);
    List<TdApi.Message> one = new ArrayList<>();
    one.add(m);
    if (longPress) {
      organize(c, one, changed);
    } else if (last != null) {
      store.putInFolder(last.id, new long[] {m.id});
      UI.showToast(Lang.getString(R.string.Tgx101SavedPutDone, last.name), android.widget.Toast.LENGTH_SHORT);
      changed.run();
    } else {
      toFolder(c, new long[] {m.id}, changed);
    }
  }

  static void swipeTag (ViewController<?> c, TdApi.Message m, boolean longPress, Runnable changed) {
    String last = Tgx101SavedStore.get(c.tdlib()).lastTag;
    List<TdApi.Message> one = new ArrayList<>();
    one.add(m);
    if (longPress) {
      organize(c, one, changed);
    } else if (last != null && !last.isEmpty()) {
      applyTag(c, one, last, false);
      changed.run();
    } else {
      tag(c, one, changed);
    }
  }

  /** «Работа», «#идеи» on the swipe buttons */
  static void applySwipeTargets (Tdlib tdlib, Tgx101SwipeRow swipe) {
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    Tgx101SavedStore.Folder last = store.folder(store.lastFolderId);
    swipe.setTargets(last != null ? last.name : null, store.lastTag != null && !store.lastTag.isEmpty() ? store.lastTag : null);
  }

  /** What «More» on a «Recent» row can do */
  public interface MoreHost {
    void startSelection (TdApi.Message m);
    void changed ();
  }

  /** Mockup А: the message, the reactions, and the actions in a grid of three */
  public static void more (ViewController<?> c, TdApi.Message m, MoreHost host) {
    Context context = c.context();
    Tdlib tdlib = c.tdlib();
    List<TdApi.Message> one = new ArrayList<>();
    one.add(m);
    long[] ids = {m.id};
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(c, null, body -> {
      View preview = Tgx101SavedListController.rowView(c, m, v -> {
        h[0].dismiss();
        Tgx101SavedKit.openInFeed(c, m);
      }, null, false);
      body.addView(preview);
      body.addView(reactionRow(c, one, host::changed));
      String text = Tgx101SavedKit.text(m) != null ? Tgx101SavedKit.text(m).text : "";
      List<Object[]> actions = new ArrayList<>();
      actions.add(new Object[] {R.drawable.baseline_folder_24, R.string.Tgx101SavedToFolder, (Runnable) () -> toFolder(c, ids, host::changed)});
      actions.add(new Object[] {R.drawable.baseline_label_24, R.string.Tgx101SavedTag, (Runnable) () -> tag(c, one, host::changed)});
      actions.add(new Object[] {R.drawable.baseline_notifications_active_24, R.string.Tgx101SavedRemind, (Runnable) () -> remind(c, ids, null)});
      if (!text.isEmpty()) {
        actions.add(new Object[] {R.drawable.baseline_content_copy_24, R.string.Tgx101SavedCopy, (Runnable) () -> UI.copyText(text, R.string.CopiedText)});
      }
      actions.add(new Object[] {R.drawable.baseline_forward_24, R.string.Tgx101SavedForward, (Runnable) () -> forward(c, one)});
      if (!text.isEmpty()) {
        actions.add(new Object[] {R.drawable.baseline_translate_24, R.string.Tgx101SavedTranslate, (Runnable) () -> translate(c, Tgx101SavedKit.text(m))});
      }
      actions.add(new Object[] {R.drawable.baseline_check_circle_24, R.string.Tgx101SavedSelect, (Runnable) () -> host.startSelection(m)});
      actions.add(new Object[] {R.drawable.baseline_forum_24, R.string.Tgx101SavedInFeed, (Runnable) () -> Tgx101SavedKit.openInFeed(c, m)});
      actions.add(new Object[] {R.drawable.baseline_delete_24, R.string.Tgx101SavedDelete, (Runnable) () -> delete(c, ids, host::changed)});
      LinearLayout grid = new LinearLayout(context);
      grid.setOrientation(LinearLayout.VERTICAL);
      grid.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
      LinearLayout line = null;
      for (int i = 0; i < actions.size(); i++) {
        if (i % 3 == 0) {
          line = new LinearLayout(context);
          line.setOrientation(LinearLayout.HORIZONTAL);
          grid.addView(line);
        }
        Object[] a = actions.get(i);
        boolean red = (int) a[0] == R.drawable.baseline_delete_24;
        LinearLayout b = new LinearLayout(context);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Tgx101SavedHomeController.card(Theme.backgroundColor(), 14f));
        Views.setClickable(b);
        android.widget.ImageView icon = new android.widget.ImageView(context);
        icon.setImageResource((int) a[0]);
        icon.setColorFilter(red ? Theme.getColor(ColorId.textNegative) : Theme.getColor(ColorId.icon));
        b.addView(icon, new LinearLayout.LayoutParams(Screen.dp(22f), Screen.dp(22f)));
        TextView t = Tgx101SavedHomeController.text(context, 12f, red ? Theme.getColor(ColorId.textNegative) : Theme.textAccentColor(), false);
        t.setText(Lang.getString((int) a[1]));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Screen.dp(4f), Screen.dp(6f), Screen.dp(4f), 0);
        b.addView(t);
        final Runnable run = (Runnable) a[2];
        b.setOnClickListener(v -> {
          h[0].dismiss();
          v.postDelayed(run, 200); // after the sheet has gone: the next one rises cleanly
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Screen.dp(68f), 1f);
        p.setMargins(Screen.dp(3f), Screen.dp(3f), Screen.dp(3f), Screen.dp(3f));
        line.addView(b, p);
      }
      for (int i = actions.size() % 3; i != 0 && i < 3; i++) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Screen.dp(68f), 1f);
        p.setMargins(Screen.dp(3f), Screen.dp(3f), Screen.dp(3f), Screen.dp(3f));
        line.addView(new View(context), p);
      }
      body.addView(grid);
    });
  }

  private static void translate (ViewController<?> c, TdApi.FormattedText text) {
    String to = Lang.locale().getLanguage();
    c.tdlib().send(new TdApi.TranslateText(text, to, tgx.td.TdConstants.TEXT_FORMAT_NEUTRAL), (result, error) -> UI.post(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      Tgx101Sheet.show(c, null, body -> {
        body.addView(Tgx101Sheet.title(c.context(), Lang.getString(R.string.Tgx101SavedTranslate)));
        TextView t = Tgx101SavedHomeController.text(c.context(), 15f, Theme.textAccentColor(), false);
        t.setText(result.text);
        t.setTextIsSelectable(true);
        t.setPadding(Screen.dp(20f), 0, Screen.dp(20f), Screen.dp(12f));
        body.addView(t);
        body.addView(primaryButton(c.context(), Lang.getString(R.string.Tgx101SavedCopy), v -> UI.copyText(result.text, R.string.CopiedText)));
      });
    }));
  }

  /**
   * Mockup Г: one sheet for selected messages — one folder, several tags (the ones some of them have are marked),
   * a reaction
   */
  public static void organize (ViewController<?> c, List<TdApi.Message> messages, @Nullable Runnable after) {
    if (messages.isEmpty()) return;
    Context context = c.context();
    Tdlib tdlib = c.tdlib();
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    long[] ids = Tgx101SavedKit.idsOf(messages);
    // the folder all of them are in, if any
    Tgx101SavedStore.Folder common = store.folderOf(ids[0]);
    for (long id : ids) if (common != null && store.folderOf(id) != common) common = null;
    final long[] folderPick = {common != null ? common.id : 0};
    final long initialFolder = folderPick[0];
    final java.util.LinkedHashMap<String, Integer> tagCounts = new java.util.LinkedHashMap<>();
    for (String t : store.knownTags()) tagCounts.put(t, 0);
    for (TdApi.Message m : messages) {
      List<String> has = new ArrayList<>(store.tagsOf(m.id));
      TdApi.FormattedText text = Tgx101SavedKit.text(m);
      if (text != null && text.entities != null) {
        for (TdApi.TextEntity e : text.entities) {
          if (e.type.getConstructor() == TdApi.TextEntityTypeHashtag.CONSTRUCTOR) {
            String t = Tgx101SavedStore.normalizeTag(text.text.substring(e.offset, e.offset + e.length));
            if (!has.contains(t)) has.add(t);
          }
        }
      }
      for (String t : has) {
        Integer n = tagCounts.get(t);
        tagCounts.put(t, (n != null ? n : 0) + 1);
      }
    }
    final java.util.LinkedHashSet<String> tagPick = new java.util.LinkedHashSet<>();
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(c, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101SavedSelected, messages.size())));

      body.addView(label(context, Lang.getString(R.string.Tgx101SavedOrganizeFolder)));
      FlowLayout folders = new FlowLayout(context);
      final List<TextView> folderChips = new ArrayList<>();
      final Runnable paintFolders = () -> {
        for (TextView chip : folderChips) paintChip(chip, (long) chip.getTag() == folderPick[0]);
      };
      for (Tgx101SavedStore.Folder f : store.folders()) {
        TextView chip = chip(context, f.name);
        chip.setTag(f.id);
        chip.setOnClickListener(v -> {
          folderPick[0] = folderPick[0] == f.id ? 0 : f.id;
          paintFolders.run();
        });
        folderChips.add(chip);
        folders.addView(chip);
      }
      TextView newFolder = chip(context, "+ " + Lang.getString(R.string.Tgx101SavedNew));
      paintChip(newFolder, false);
      newFolder.setOnClickListener(v -> askName(c, Lang.getString(R.string.Tgx101SavedNewFolder), null, name -> {
        Tgx101SavedStore.Folder f = store.createFolder(name);
        store.putInFolder(f.id, ids);
        UI.showToast(Lang.getString(R.string.Tgx101SavedPutDone, f.name), android.widget.Toast.LENGTH_SHORT);
        h[0].dismiss();
        if (after != null) after.run();
      }));
      folders.addView(newFolder);
      paintFolders.run();
      body.addView(folders);

      body.addView(label(context, Lang.getString(R.string.Tgx101SavedOrganizeTags)));
      FlowLayout tags = new FlowLayout(context);
      List<String> partial = new ArrayList<>();
      for (java.util.Map.Entry<String, Integer> e : tagCounts.entrySet()) {
        final String t = e.getKey();
        int n = e.getValue();
        if (n == messages.size()) tagPick.add(t);
        else if (n > 0) partial.add("#" + t + " — " + n + "/" + messages.size());
        TextView chip = chip(context, "#" + t);
        paintChip(chip, tagPick.contains(t));
        chip.setOnClickListener(v -> {
          if (!tagPick.remove(t)) tagPick.add(t);
          paintChip(chip, tagPick.contains(t));
        });
        tags.addView(chip);
      }
      TextView newTag = chip(context, "+ " + Lang.getString(R.string.Tgx101SavedNew));
      paintChip(newTag, false);
      newTag.setOnClickListener(v -> {
        h[0].dismiss();
        tag(c, messages, after);
      });
      tags.addView(newTag);
      body.addView(tags);
      if (!partial.isEmpty()) {
        TextView hint = Tgx101SavedHomeController.text(context, 12f, Theme.textDecentColor(), false);
        hint.setText(Lang.getString(R.string.Tgx101SavedOrganizePartial, TextUtils.join(", ", partial)));
        hint.setPadding(Screen.dp(20f), Screen.dp(2f), Screen.dp(20f), 0);
        body.addView(hint);
      }

      body.addView(label(context, Lang.getString(R.string.Tgx101SavedReaction)));
      body.addView(reactionRow(c, messages, null));

      final java.util.Set<String> tagInitial = new java.util.HashSet<>(tagPick);
      body.addView(primaryButton(context, Lang.getString(R.string.Done), v -> {
        h[0].dismiss();
        if (folderPick[0] != initialFolder) {
          if (folderPick[0] == 0) store.removeFromFolders(ids);
          else store.putInFolder(folderPick[0], ids);
        }
        for (String t : tagPick) if (!tagInitial.contains(t)) applyTag(c, messages, t, false);
        if (after != null) after.run();
      }));
    });
  }

  private static TextView label (Context context, String text) {
    TextView l = Tgx101SavedHomeController.text(context, 12f, Theme.textDecentColor(), true);
    l.setText(text.toUpperCase(Lang.locale()));
    if (android.os.Build.VERSION.SDK_INT >= 21) l.setLetterSpacing(.04f);
    l.setPadding(Screen.dp(20f), Screen.dp(10f), Screen.dp(20f), Screen.dp(6f));
    return l;
  }

  private static TextView chip (Context context, String text) {
    TextView chip = Tgx101SavedHomeController.text(context, 14f, Theme.textAccentColor(), false);
    chip.setText(text);
    chip.setGravity(Gravity.CENTER);
    chip.setSingleLine(true);
    chip.setPadding(Screen.dp(14f), 0, Screen.dp(14f), 0);
    Views.setClickable(chip);
    chip.setLayoutParams(new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(36f)));
    return chip;
  }

  private static void paintChip (TextView chip, boolean on) {
    chip.setTextColor(on ? 0xffffffff : Theme.textAccentColor());
    chip.setBackground(Tgx101SavedHomeController.card(on ? Tgx101SavedHomeController.accent() : Theme.backgroundColor(), 18f));
  }

  /** Chips that wrap to the next line */
  static final class FlowLayout extends ViewGroup {
    FlowLayout (Context context) {
      super(context);
      setPadding(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(4f));
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      int max = width - getPaddingLeft() - getPaddingRight();
      int x = 0, y = 0, lineHeight = 0, gap = Screen.dp(8f);
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        child.measure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(Screen.dp(36f), MeasureSpec.EXACTLY));
        if (x > 0 && x + child.getMeasuredWidth() > max) {
          x = 0;
          y += lineHeight + gap;
          lineHeight = 0;
        }
        x += child.getMeasuredWidth() + gap;
        lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
      }
      setMeasuredDimension(width, getPaddingTop() + y + lineHeight + getPaddingBottom());
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      int max = r - l - getPaddingLeft() - getPaddingRight();
      int x = 0, y = 0, lineHeight = 0, gap = Screen.dp(8f);
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        if (x > 0 && x + child.getMeasuredWidth() > max) {
          x = 0;
          y += lineHeight + gap;
          lineHeight = 0;
        }
        child.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + child.getMeasuredWidth(), getPaddingTop() + y + child.getMeasuredHeight());
        x += child.getMeasuredWidth() + gap;
        lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
      }
    }
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
    if (store.bannerOff() || (store.sheetMode == Tgx101SavedStore.SHEET_EXTERNAL_ONLY && !external)) return;
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
      // (user 2026-10-23 «плашка всегда тёмная, а должна быть в цвет темы»): the theme's card colour — on a dark theme a
      // step lighter than the screen, so it doesn't melt into it; its words and buttons in the theme's colours too
      final int ink = Theme.textAccentColor(), accent = Tgx101SavedHomeController.accent();
      card.setBackground(Tgx101SavedHomeController.card(Theme.isDark() ? androidx.core.graphics.ColorUtils.compositeColors(0x14ffffff, Theme.fillingColor()) : Theme.fillingColor(), 18f));
      card.setPadding(Screen.dp(14f), Screen.dp(8f), Screen.dp(14f), Screen.dp(12f));
      if (android.os.Build.VERSION.SDK_INT >= 21) card.setElevation(Screen.dp(6f));
      card.setClickable(true);

      View grip = new View(context);
      grip.setBackground(Tgx101SavedHomeController.card(Tgx101SavedHomeController.tint(ink, .25f), 2f));
      LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Screen.dp(36f), Screen.dp(4f));
      gp.gravity = Gravity.CENTER_HORIZONTAL;
      gp.bottomMargin = Screen.dp(8f);
      card.addView(grip, gp);

      LinearLayout head = new LinearLayout(context);
      head.setOrientation(LinearLayout.HORIZONTAL);
      head.setGravity(Gravity.CENTER_VERTICAL);
      android.widget.ImageView icon = new android.widget.ImageView(context);
      icon.setImageResource(R.drawable.tgx101_storage_tiles_24); // the storage (user 2026-10-21: the filing cabinet everywhere)
      icon.setColorFilter(Tgx101SavedHomeController.onChosen());
      icon.setScaleType(android.widget.ImageView.ScaleType.CENTER);
      icon.setBackground(Tgx101SavedHomeController.card(accent, 12f));
      head.addView(icon, new LinearLayout.LayoutParams(Screen.dp(40f), Screen.dp(40f)));
      LinearLayout texts = new LinearLayout(context);
      texts.setOrientation(LinearLayout.VERTICAL);
      texts.setPadding(Screen.dp(12f), 0, 0, 0);
      TextView t1 = Tgx101SavedHomeController.text(context, 15f, ink, true);
      t1.setText(Lang.getString(R.string.Tgx101SavedBannerTitle));
      texts.addView(t1);
      TextView t2 = Tgx101SavedHomeController.text(context, 12f, Theme.textDecentColor(), false);
      t2.setText(Lang.getString(R.string.Tgx101SavedBannerHint));
      texts.addView(t2);
      head.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      card.addView(head);

      LinearLayout buttons = new LinearLayout(context);
      buttons.setOrientation(LinearLayout.HORIZONTAL);
      buttons.setPadding(0, Screen.dp(12f), 0, 0);
      card.addView(buttons);
      LinearLayout reactions = new LinearLayout(context);
      reactions.setOrientation(LinearLayout.HORIZONTAL);
      reactions.setPadding(0, Screen.dp(8f), 0, 0);
      card.addView(reactions);

      final Runnable[] close = new Runnable[1];
      close[0] = () -> {
        holder.removeCallbacks(close[0]);
        card.animate().translationY(card.getHeight() + Screen.dp(40f)).alpha(0f).setDuration(200)
          .setInterpolator(new android.view.animation.AccelerateInterpolator(1.2f))
          .withEndAction(() -> Views.removeFromParent(holder)).start();
      };
      card.setTag(close[0]);
      final int shownAt = (int) (System.currentTimeMillis() / 1000L) - 5;
      int[] labels = {R.string.Tgx101SavedToFolder, R.string.Tgx101SavedTag, R.string.Tgx101SavedRemind};
      for (int i = 0; i < labels.length; i++) {
        final int which = i;
        TextView b = Tgx101SavedHomeController.text(context, 14f, accent, false);
        b.setText(Lang.getString(labels[i]));
        b.setGravity(Gravity.CENTER);
        b.setBackground(Tgx101SavedHomeController.card(Tgx101SavedHomeController.tint(accent, .12f), 12f));
        Views.setClickable(b);
        b.setOnClickListener(v -> {
          close[0].run();
          // the banner may have been shown while the messages still had their sending ids (test 2026-10-15:
          // «Tag» found nothing and did nothing)
          long[] known = Tgx101SavedStore.get(tdlib).resolve(ids);
          Tgx101SavedKit.resolveSaved(tdlib, known, shownAt, messages -> {
            org.thunderdog.challegram.Tgx101Diag.mark("saved banner: button " + which + ", found " + messages.size() + " of " + known.length);
            if (messages.isEmpty()) return;
            long[] real = Tgx101SavedKit.idsOf(messages);
            if (which == 0) {
              toFolder(c, real, () -> refreshOpenSaved(c));
            } else if (which == 1) {
              tag(c, messages, () -> refreshOpenSaved(c));
            } else {
              remind(c, real, null);
            }
          });
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f);
        if (i > 0) bp.leftMargin = Screen.dp(8f);
        buttons.addView(b, bp);
      }

      // a reaction straight from the banner (user 2026-10-15: «реакции … при пересылке»)
      for (String e : Tgx101SavedKit.QUICK_REACTIONS) {
        TextView b = Tgx101SavedHomeController.text(context, 18f, ink, false);
        b.setText(Tgx101SavedKit.emoji(e));
        b.setGravity(Gravity.CENTER);
        b.setBackground(Tgx101SavedHomeController.card(Tgx101SavedHomeController.tint(accent, .12f), 12f));
        Views.setClickable(b);
        b.setOnClickListener(v -> {
          close[0].run();
          long[] known = Tgx101SavedStore.get(tdlib).resolve(ids);
          Tgx101SavedKit.resolveSaved(tdlib, known, shownAt, messages -> Tgx101SavedKit.toggleReaction(tdlib, messages, e, added -> {
            UI.showToast(e, android.widget.Toast.LENGTH_SHORT);
            refreshOpenSaved(c);
          }));
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, Screen.dp(36f), 1f);
        if (reactions.getChildCount() > 0) rp.leftMargin = Screen.dp(8f);
        reactions.addView(b, rp);
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
