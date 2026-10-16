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
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TGx101 (user 2026-10-15 «поиск по настройкам, как в системе любого современного телефона»): one field finds any switch
 * in the settings and MagiX by its words, shows where it lives («MagiX → Messages → …»), opens that screen and lights the
 * item up. The index is built once, from the screens themselves, so new settings are found without a list to keep.
 */
public class Tgx101SettingsSearch extends ViewController<Void> {

  /** a settings screen the index reads */
  private interface Factory {
    ViewController<?> make (Context context, Tdlib tdlib);
  }

  private static final class Screen_ {
    final String path;
    final Factory factory;

    Screen_ (String path, Factory factory) {
      this.path = path;
      this.factory = factory;
    }
  }

  private static final class Entry {
    final String title, path, haystack;
    final int screen, itemId;

    Entry (String title, String path, int screen, int itemId) {
      this.title = title;
      this.path = path;
      this.screen = screen;
      this.itemId = itemId;
      this.haystack = (title + " " + path).toLowerCase(Locale.ROOT);
    }
  }

  private static List<Screen_> screens;
  private static List<Entry> index;
  private static String indexLanguage;

  public static void open (ViewController<?> c) {
    c.navigateTo(new Tgx101SettingsSearch(c.context(), c.tdlib()));
  }

  public Tgx101SettingsSearch (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101SettingsSearch;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101SettingsSearch);
  }

  @Override
  protected int getBackButton () {
    return org.thunderdog.challegram.navigation.BackHeaderButton.TYPE_BACK;
  }

  private static List<Screen_> screens (Context context) {
    if (screens != null) return screens;
    List<Screen_> list = new ArrayList<>();
    String settings = Lang.getString(R.string.Settings);
    String magix = Lang.getString(R.string.Tgx101Settings);
    list.add(new Screen_(settings + " → " + Lang.getString(R.string.Notifications), SettingsNotificationController::new));
    list.add(new Screen_(settings + " → " + Lang.getString(R.string.PrivacySettings), SettingsPrivacyController::new));
    list.add(new Screen_(settings + " → " + Lang.getString(R.string.DataSettings), SettingsDataController::new));
    list.add(new Screen_(settings + " → " + Lang.getString(R.string.ThemeSettings), SettingsThemeController::new));
    list.add(new Screen_(settings + " → " + Lang.getString(R.string.TweakSettings), (ctx, tdlib) -> {
      SettingsThemeController c = new SettingsThemeController(ctx, tdlib);
      c.setArguments(new SettingsThemeController.Args(SettingsThemeController.MODE_INTERFACE_OPTIONS));
      return c;
    }));
    list.add(new Screen_(settings + " → " + Lang.getString(R.string.ChatFolders), SettingsFoldersController::new));
    // MagiX's own first screen: its sections and «Settings backup» (user 2026-10-16 «в поиске это не ищется»)
    list.add(new Screen_(Lang.getString(R.string.Settings) + " → " + magix, (ctx, tdlib) -> {
      SettingsDataController c = new SettingsDataController(ctx, tdlib);
      c.setArguments(new SettingsDataController.Args(SettingsDataController.MODE_TGX101));
      return c;
    }));
    list.add(new Screen_(magix + " → " + Lang.getString(R.string.Tgx101Backup), Tgx101BackupController::new));
    int[] sections = SettingsDataController.tgx101SectionTitles();
    for (int i = 0; i < sections.length - 1; i++) { // the last one is Telegram X's own «Data», indexed above
      final int section = i;
      list.add(new Screen_(magix + " → " + Lang.getString(sections[i]), (ctx, tdlib) -> {
        SettingsDataController c = new SettingsDataController(ctx, tdlib);
        c.setArguments(new SettingsDataController.Args(SettingsDataController.MODE_TGX101).setTgx101Section(section));
        return c;
      }));
    }
    list.add(new Screen_(magix + " → " + Lang.getString(R.string.Tgx101Gestures), Tgx101GesturesController::new));
    list.add(new Screen_(Lang.getString(R.string.SavedMessages) + " → " + Lang.getString(R.string.Settings), Tgx101SavedSettingsController::new));
    screens = list;
    return list;
  }

  /** reads each screen's own list once (and again after a language change) */
  private void buildIndex () {
    String language = Lang.packId();
    if (index != null && language.equals(indexLanguage)) return;
    screens = null;
    List<Entry> out = new ArrayList<>();
    List<Screen_> all = screens(context);
    for (int s = 0; s < all.size(); s++) {
      Screen_ screen = all.get(s);
      ViewController<?> c = null;
      try {
        c = screen.factory.make(context, tdlib);
        c.getValue(); // builds the screen's list
        if (!(c instanceof RecyclerViewController)) continue;
        RecyclerView rv = ((RecyclerViewController<?>) c).getRecyclerView();
        if (rv == null || !(rv.getAdapter() instanceof SettingsAdapter)) continue;
        String section = null;
        for (ListItem item : ((SettingsAdapter) rv.getAdapter()).getItems()) {
          CharSequence text = item.getString();
          if (text == null || text.length() == 0) continue;
          String t = text.toString().replace('\n', ' ').trim();
          if (item.getViewType() == ListItem.TYPE_HEADER) {
            section = t;
            continue;
          }
          if (item.getId() == 0 || item.getViewType() == ListItem.TYPE_DESCRIPTION) continue;
          if (t.length() > 80) t = t.substring(0, 80) + "…";
          out.add(new Entry(t, section != null ? screen.path + " → " + section : screen.path, s, item.getId()));
        }
      } catch (Throwable t) {
        org.thunderdog.challegram.Tgx101Diag.mark("settings search: skipped " + screen.path + " — " + t.getClass().getSimpleName());
      } finally {
        if (c != null) {
          try {
            c.destroy();
          } catch (Throwable ignored) { }
        }
      }
    }
    index = out;
    indexLanguage = language;
    org.thunderdog.challegram.Tgx101Diag.mark("settings search: " + out.size() + " items from " + all.size() + " screens");
  }

  private EditText field;
  private LinearLayout results;
  private TextView empty;

  @Override
  protected View onCreateView (Context context) {
    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setBackgroundColor(Theme.backgroundColor());
    field = new EditText(context);
    field.setHint(Lang.getString(R.string.Tgx101SettingsSearchHint));
    field.setSingleLine(true);
    field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    Tgx101SavedHomeController.styleField(field, false);
    LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(46f));
    fp.setMargins(Screen.dp(12f), Screen.dp(10f), Screen.dp(12f), Screen.dp(6f));
    column.addView(field, fp);
    ScrollView scroll = new ScrollView(context);
    results = new LinearLayout(context);
    results.setOrientation(LinearLayout.VERTICAL);
    results.setPadding(0, 0, 0, Screen.dp(24f));
    scroll.addView(results);
    column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    empty = Tgx101SavedHomeController.text(context, 14f, Theme.textDecentColor(), false);
    empty.setGravity(Gravity.CENTER);
    empty.setPadding(Screen.dp(32f), Screen.dp(32f), Screen.dp(32f), 0);
    empty.setText(Lang.getString(R.string.Tgx101SettingsSearchEmpty));
    results.addView(empty);
    field.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
      @Override public void onTextChanged (CharSequence s, int start, int before, int count) { }

      @Override
      public void afterTextChanged (Editable s) {
        search(s.toString());
      }
    });
    field.requestFocus();
    field.postDelayed(() -> org.thunderdog.challegram.tool.Keyboard.show(field), 250);
    UI.post(this::buildIndex, 50);
    return column;
  }

  private void search (String query) {
    if (isDestroyed()) return;
    buildIndex();
    results.removeAllViews();
    String q = query.trim().toLowerCase(Locale.ROOT);
    if (q.isEmpty()) {
      results.addView(empty);
      return;
    }
    String[] words = q.split("\\s+");
    List<Entry> found = new ArrayList<>();
    for (Entry e : index) {
      boolean all = true;
      for (String w : words) if (!e.haystack.contains(w)) all = false;
      if (all) found.add(e);
    }
    // the item's own name first, then where it lives
    java.util.Collections.sort(found, (a, b) -> Integer.compare(rank(a, q), rank(b, q)));
    if (found.isEmpty()) {
      TextView none = Tgx101SavedHomeController.text(context, 14f, Theme.textDecentColor(), false);
      none.setGravity(Gravity.CENTER);
      none.setPadding(Screen.dp(32f), Screen.dp(32f), Screen.dp(32f), 0);
      none.setText(Lang.getString(R.string.Tgx101SettingsSearchNothing));
      results.addView(none);
      return;
    }
    int n = 0;
    for (Entry e : found) {
      if (n++ >= 60) break;
      results.addView(row(e, q));
    }
  }

  private static int rank (Entry e, String q) {
    String t = e.title.toLowerCase(Locale.ROOT);
    if (t.startsWith(q)) return 0;
    if (t.contains(q)) return 1;
    return 2;
  }

  private View row (Entry e, String q) {
    LinearLayout r = new LinearLayout(context);
    r.setOrientation(LinearLayout.VERTICAL);
    r.setPadding(Screen.dp(18f), Screen.dp(10f), Screen.dp(18f), Screen.dp(10f));
    Views.setClickable(r);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(r);
    r.setOnClickListener(v -> openEntry(e));
    TextView t = Tgx101SavedHomeController.text(context, 16f, Theme.textAccentColor(), false);
    t.setText(Tgx101SavedListController.highlighted(e.title, q));
    r.addView(t);
    TextView p = Tgx101SavedHomeController.text(context, 12f, Theme.textDecentColor(), false);
    p.setText(e.path);
    p.setSingleLine(true);
    p.setEllipsize(TextUtils.TruncateAt.START);
    r.addView(p);
    return r;
  }

  /** the screen opens, scrolls to the item and lights it up twice */
  private void openEntry (Entry e) {
    org.thunderdog.challegram.tool.Keyboard.hide(field);
    Screen_ screen = screens(context).get(e.screen);
    ViewController<?> c = screen.factory.make(context, tdlib);
    navigateTo(c);
    UI.post(() -> highlight(c, e.itemId, 0), 420);
  }

  private static void highlight (ViewController<?> c, int itemId, int attempt) {
    if (c.isDestroyed() || !(c instanceof RecyclerViewController)) return;
    RecyclerView rv = ((RecyclerViewController<?>) c).getRecyclerView();
    if (rv == null || !(rv.getAdapter() instanceof SettingsAdapter)) return;
    int position = ((SettingsAdapter) rv.getAdapter()).indexOfViewById(itemId);
    if (position < 0) {
      if (attempt < 5) UI.post(() -> highlight(c, itemId, attempt + 1), 200); // lists that fill in a moment
      return;
    }
    if (rv.getLayoutManager() instanceof LinearLayoutManager) {
      ((LinearLayoutManager) rv.getLayoutManager()).scrollToPositionWithOffset(position, rv.getHeight() / 3);
    }
    rv.postDelayed(() -> {
      RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(position);
      if (h == null) return;
      View v = h.itemView;
      v.setPressed(true);
      v.postDelayed(() -> v.setPressed(false), 450);
      v.postDelayed(() -> v.setPressed(true), 650);
      v.postDelayed(() -> v.setPressed(false), 1100);
    }, 160);
  }
}
