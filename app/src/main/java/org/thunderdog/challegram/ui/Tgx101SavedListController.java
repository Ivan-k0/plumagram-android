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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TGx101 (user 2026-10-15, approved mockups 2–6): one screen for every Saved list — a kind (photos, files, links…),
 * «Inbox» (not in a folder), a folder, a tag, and search with refinements (kind, tag, source, period). Three views
 * (list, tiles by month, link cards), six sorts in both directions — both kept per screen — and selecting several
 * with actions at the bottom (to folder, tag, remind, forward, delete).
 */
public class Tgx101SavedListController extends ViewController<Void> {

  static final int MODE_KIND = 0, MODE_INBOX = 1, MODE_FOLDER = 2, MODE_TAG = 3, MODE_SEARCH = 4;
  static final int VIEW_LIST = 0, VIEW_GRID = 1, VIEW_CARDS = 2;
  static final int SORT_SAVED = 0, SORT_ORIGINAL = 1, SORT_NAME = 2, SORT_SIZE = 3, SORT_SOURCE = 4, SORT_KIND = 5;

  public static void openKind (ViewController<?> c, int kind) {
    Tgx101SavedListController l = new Tgx101SavedListController(c.context(), c.tdlib());
    l.mode = MODE_KIND;
    l.kind = kind;
    c.navigateTo(l);
  }

  public static void openInbox (ViewController<?> c) {
    Tgx101SavedListController l = new Tgx101SavedListController(c.context(), c.tdlib());
    l.mode = MODE_INBOX;
    c.navigateTo(l);
  }

  public static void openFolder (ViewController<?> c, long folderId) {
    Tgx101SavedListController l = new Tgx101SavedListController(c.context(), c.tdlib());
    l.mode = MODE_FOLDER;
    l.folderId = folderId;
    c.navigateTo(l);
  }

  public static void openTag (ViewController<?> c, String tag) {
    Tgx101SavedListController l = new Tgx101SavedListController(c.context(), c.tdlib());
    l.mode = MODE_TAG;
    l.tag = Tgx101SavedStore.normalizeTag(tag);
    c.navigateTo(l);
  }

  public static void openSearch (ViewController<?> c) {
    Tgx101SavedListController l = new Tgx101SavedListController(c.context(), c.tdlib());
    l.mode = MODE_SEARCH;
    c.navigateTo(l);
  }

  public Tgx101SavedListController (Context context, org.thunderdog.challegram.telegram.Tdlib tdlib) {
    super(context, tdlib);
  }

  private int mode;
  private int kind = -1;
  private long folderId;
  private String tag;

  // search refinements
  private int searchKind = -1;
  private @Nullable String searchTag;
  private @Nullable String searchSource;
  private int searchPeriodDays;
  private String query = "";

  private int viewMode = VIEW_LIST;
  private int sort = SORT_SAVED;
  private boolean ascending;

  private final List<TdApi.Message> loaded = new ArrayList<>();
  private final List<Object> items = new ArrayList<>(); // TdApi.Message or String (month header)
  private final LinkedHashSet<Long> selected = new LinkedHashSet<>();

  private RecyclerView list;
  private Adapter adapter;
  private TextView countView, emptyView;
  private LinearLayout viewSwitch;
  private LinearLayout selectionBar, toolbar;
  private TextView selectionTitle;
  private LinearLayout searchChips;
  private EditText searchField;

  @Override
  public int getId () {
    return R.id.controller_tgx101SavedList;
  }

  @Override
  public CharSequence getName () {
    switch (mode) {
      case MODE_INBOX: return Lang.getString(R.string.Tgx101SavedInbox);
      case MODE_FOLDER: {
        Tgx101SavedStore.Folder f = Tgx101SavedStore.get(tdlib).folder(folderId);
        return f != null ? f.name : Lang.getString(R.string.Tgx101SavedFolder);
      }
      case MODE_TAG: return "#" + tag;
      case MODE_SEARCH: return Lang.getString(R.string.Tgx101SavedSearchTitle);
    }
    return Tgx101SavedHomeController.kindPlural(kind);
  }

  private String screenKey () {
    switch (mode) {
      case MODE_INBOX: return "inbox";
      case MODE_FOLDER: return "f" + folderId;
      case MODE_TAG: return "t" + tag;
      case MODE_SEARCH: return "search";
    }
    return "k" + kind;
  }

  private String prefKey () {
    return "tgx101_saved_view_" + tdlib.id() + "_" + screenKey();
  }

  private void loadPrefs () {
    int defView = (mode == MODE_KIND && (kind == Tgx101SavedKit.KIND_PHOTO || kind == Tgx101SavedKit.KIND_VIDEO)) ? VIEW_GRID : (mode == MODE_KIND && kind == Tgx101SavedKit.KIND_LINK) ? VIEW_CARDS : VIEW_LIST;
    int v = Settings.instance().pmc().getInt(prefKey(), -1);
    if (v == -1) {
      viewMode = defView;
    } else {
      viewMode = v & 0xf;
      sort = (v >> 4) & 0xf;
      ascending = ((v >> 8) & 1) != 0;
    }
  }

  private void savePrefs () {
    Settings.instance().pmc().putInt(prefKey(), viewMode | (sort << 4) | ((ascending ? 1 : 0) << 8));
  }

  // View

  @Override
  protected View onCreateView (Context context) {
    loadPrefs();
    FrameLayout root = new FrameLayout(context);
    root.setBackgroundColor(Theme.backgroundColor());

    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    if (mode == MODE_SEARCH) buildSearchBar(context, column);

    // count · view switch · sort
    toolbar = new LinearLayout(context);
    toolbar.setOrientation(LinearLayout.HORIZONTAL);
    toolbar.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.setPadding(Screen.dp(16f), Screen.dp(8f), Screen.dp(8f), Screen.dp(4f));
    countView = Tgx101SavedHomeController.text(context, 13f, Theme.textDecentColor(), false);
    toolbar.addView(countView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    viewSwitch = new LinearLayout(context);
    viewSwitch.setOrientation(LinearLayout.HORIZONTAL);
    viewSwitch.setBackground(Tgx101SavedHomeController.card(Theme.fillingColor(), 10f));
    viewSwitch.setPadding(Screen.dp(3f), Screen.dp(3f), Screen.dp(3f), Screen.dp(3f));
    toolbar.addView(viewSwitch);
    ImageView sortButton = iconButton(context, R.drawable.dotvhs_baseline_folders_reorder_24, Lang.getString(R.string.Tgx101SavedSort), v -> showSortSheet());
    toolbar.addView(sortButton);
    column.addView(toolbar);
    buildViewSwitch();

    list = new RecyclerView(context);
    list.setClipToPadding(false);
    list.setPadding(Screen.dp(12f), Screen.dp(4f), Screen.dp(12f), Screen.dp(96f));
    list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
    adapter = new Adapter();
    list.setAdapter(adapter);
    applyLayoutManager();
    FrameLayout listBox = new FrameLayout(context);
    listBox.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    emptyView = Tgx101SavedHomeController.text(context, 15f, Theme.textDecentColor(), false);
    emptyView.setGravity(Gravity.CENTER);
    emptyView.setPadding(Screen.dp(32f), 0, Screen.dp(32f), 0);
    emptyView.setText(Lang.getString(R.string.LoadingInformation));
    listBox.addView(emptyView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(200f), Gravity.TOP));
    column.addView(listBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    buildSelectionBar(context, root);

    if (mode == MODE_SEARCH) {
      showSearchHints();
      searchField.requestFocus();
      searchField.postDelayed(() -> org.thunderdog.challegram.tool.Keyboard.show(searchField), 250);
    } else {
      load();
    }
    return root;
  }

  private ImageView iconButton (Context context, int icon, String description, View.OnClickListener click) {
    ImageView b = new ImageView(context);
    b.setImageResource(icon);
    b.setColorFilter(Theme.getColor(ColorId.icon));
    b.setScaleType(ImageView.ScaleType.CENTER);
    b.setContentDescription(description);
    Views.setClickable(b);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(b);
    b.setOnClickListener(click);
    b.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(44f), Screen.dp(44f)));
    return b;
  }

  private void buildViewSwitch () {
    viewSwitch.removeAllViews();
    int[] modes = {VIEW_LIST, VIEW_GRID, VIEW_CARDS};
    int[] icons = {R.drawable.baseline_format_list_bulleted_type_24, R.drawable.baseline_apps_24, R.drawable.baseline_link_24};
    int[] names = {R.string.Tgx101SavedViewList, R.string.Tgx101SavedViewGrid, R.string.Tgx101SavedViewCards};
    for (int i = 0; i < modes.length; i++) {
      final int m = modes[i];
      ImageView b = new ImageView(context);
      b.setImageResource(icons[i]);
      b.setScaleType(ImageView.ScaleType.CENTER);
      b.setContentDescription(Lang.getString(names[i]));
      boolean on = viewMode == m;
      b.setColorFilter(on ? Tgx101SavedHomeController.accent() : Theme.getColor(ColorId.icon));
      b.setBackground(on ? Tgx101SavedHomeController.card(Tgx101SavedHomeController.tint(Tgx101SavedHomeController.accent(), .14f), 8f) : null);
      Views.setClickable(b);
      b.setOnClickListener(v -> {
        if (viewMode == m) return;
        viewMode = m;
        savePrefs();
        buildViewSwitch();
        list.setAlpha(0f); // the new view fades in instead of popping
        applyLayoutManager();
        rebuildItems();
        list.animate().alpha(1f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
      });
      viewSwitch.addView(b, new LinearLayout.LayoutParams(Screen.dp(40f), Screen.dp(34f)));
    }
  }

  private void applyLayoutManager () {
    GridLayoutManager lm = new GridLayoutManager(context, viewMode == VIEW_GRID ? 3 : 1);
    lm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return position < items.size() && items.get(position) instanceof String ? lm.getSpanCount() : 1;
      }
    });
    list.setLayoutManager(lm);
  }

  // Search bar

  private void buildSearchBar (Context context, LinearLayout column) {
    LinearLayout box = new LinearLayout(context);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(Screen.dp(12f), Screen.dp(10f), Screen.dp(12f), 0);
    searchField = new EditText(context);
    searchField.setHint(Lang.getString(R.string.Tgx101SavedSearchHint));
    searchField.setHintTextColor(Theme.textDecentColor());
    searchField.setTextColor(Theme.textAccentColor());
    searchField.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16f);
    searchField.setSingleLine(true);
    searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    searchField.setBackground(Tgx101SavedHomeController.card(Theme.fillingColor(), 21f));
    searchField.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
    box.addView(searchField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(44f)));
    HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
    chipsScroll.setHorizontalScrollBarEnabled(false);
    searchChips = new LinearLayout(context);
    searchChips.setOrientation(LinearLayout.HORIZONTAL);
    searchChips.setPadding(0, Screen.dp(10f), 0, Screen.dp(2f));
    chipsScroll.addView(searchChips);
    box.addView(chipsScroll);
    column.addView(box);
    buildSearchChips();
    final Runnable runSearch = this::runSearch;
    searchField.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
      @Override public void onTextChanged (CharSequence s, int start, int before, int count) { }

      @Override
      public void afterTextChanged (Editable s) {
        query = s.toString().trim();
        searchField.removeCallbacks(runSearch);
        searchField.postDelayed(runSearch, 350); // typing pauses: search, not on every letter
      }
    });
    searchField.setOnEditorActionListener((v, actionId, event) -> {
      searchField.removeCallbacks(runSearch);
      runSearch();
      org.thunderdog.challegram.tool.Keyboard.hide(searchField);
      return true;
    });
  }

  private TextView chip (String label, boolean on, View.OnClickListener click) {
    TextView c = Tgx101SavedHomeController.text(context, 13f, on ? Theme.fillingColor() : Theme.textAccentColor(), false);
    c.setText(label);
    c.setGravity(Gravity.CENTER);
    c.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    c.setBackground(Tgx101SavedHomeController.card(on ? Theme.textAccentColor() : Theme.fillingColor(), 17f));
    Views.setClickable(c);
    c.setOnClickListener(click);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(34f));
    p.rightMargin = Screen.dp(8f);
    c.setLayoutParams(p);
    return c;
  }

  private void buildSearchChips () {
    if (searchChips == null) return;
    searchChips.removeAllViews();
    searchChips.addView(chip(searchKind == -1 ? Lang.getString(R.string.Tgx101SavedFilterKind) : Lang.getString(R.string.Tgx101SavedFilterKindValue, Tgx101SavedHomeController.kindPlural(searchKind)), searchKind != -1, v -> pickKind()));
    searchChips.addView(chip(searchTag == null ? Lang.getString(R.string.Tgx101SavedFilterTag) : "#" + searchTag, searchTag != null, v -> pickTag()));
    searchChips.addView(chip(searchSource == null ? Lang.getString(R.string.Tgx101SavedFilterSource) : searchSource, searchSource != null, v -> pickSource()));
    searchChips.addView(chip(searchPeriodDays == 0 ? Lang.getString(R.string.Tgx101SavedFilterPeriod) : periodName(searchPeriodDays), searchPeriodDays != 0, v -> pickPeriod()));
  }

  private void pickKind () {
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101SavedFilterKind)));
      body.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(R.string.Tgx101SavedEverything), null, searchKind == -1, v -> {
        searchKind = -1;
        h[0].dismiss();
        buildSearchChips();
        runSearch();
      }));
      for (int k : new int[] {Tgx101SavedKit.KIND_PHOTO, Tgx101SavedKit.KIND_VIDEO, Tgx101SavedKit.KIND_FILE, Tgx101SavedKit.KIND_LINK, Tgx101SavedKit.KIND_VOICE}) {
        body.addView(Tgx101Sheet.row(context, 0, 0, Tgx101SavedHomeController.kindPlural(k), null, searchKind == k, v -> {
          searchKind = k;
          h[0].dismiss();
          buildSearchChips();
          runSearch();
        }));
      }
    });
  }

  private void pickTag () {
    List<String> tags = Tgx101SavedStore.get(tdlib).knownTags();
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101SavedFilterTag)));
      body.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(R.string.Tgx101SavedAnyTag), null, searchTag == null, v -> {
        searchTag = null;
        h[0].dismiss();
        buildSearchChips();
        runSearch();
      }));
      for (String t : tags) {
        body.addView(Tgx101Sheet.row(context, 0, 0, "#" + t, null, t.equals(searchTag), v -> {
          searchTag = t;
          h[0].dismiss();
          buildSearchChips();
          runSearch();
        }));
      }
    });
  }

  private void pickSource () {
    LinkedHashSet<String> sources = new LinkedHashSet<>();
    for (TdApi.Message m : loaded) sources.add(Tgx101SavedKit.source(tdlib, m));
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101SavedFilterSource)));
      body.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(R.string.Tgx101SavedAnySource), null, searchSource == null, v -> {
        searchSource = null;
        h[0].dismiss();
        buildSearchChips();
        rebuildItems();
      }));
      int n = 0;
      for (String s : sources) {
        if (s.isEmpty() || n++ >= 12) continue;
        body.addView(Tgx101Sheet.row(context, 0, 0, s, null, s.equals(searchSource), v -> {
          searchSource = s;
          h[0].dismiss();
          buildSearchChips();
          rebuildItems();
        }));
      }
    });
  }

  private static String periodName (int days) {
    switch (days) {
      case 1: return Lang.getString(R.string.Tgx101SavedPeriodDay);
      case 7: return Lang.getString(R.string.Tgx101SavedPeriodWeek);
      case 30: return Lang.getString(R.string.Tgx101SavedPeriodMonth);
      case 365: return Lang.getString(R.string.Tgx101SavedPeriodYear);
    }
    return Lang.getString(R.string.Tgx101SavedFilterPeriod);
  }

  private void pickPeriod () {
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101SavedFilterPeriod)));
      for (int d : new int[] {0, 1, 7, 30, 365}) {
        body.addView(Tgx101Sheet.row(context, 0, 0, d == 0 ? Lang.getString(R.string.Tgx101SavedAnyTime) : periodName(d), null, searchPeriodDays == d, v -> {
          searchPeriodDays = d;
          h[0].dismiss();
          buildSearchChips();
          rebuildItems();
        }));
      }
    });
  }

  private int searchGeneration;

  private void runSearch () {
    if (mode != MODE_SEARCH || isDestroyed()) return;
    final int gen = ++searchGeneration;
    if (query.isEmpty() && searchKind == -1 && searchTag == null) {
      loaded.clear();
      showSearchHints();
      return;
    }
    emptyView.setText(Lang.getString(R.string.LoadingInformation));
    emptyView.setVisibility(View.VISIBLE);
    String q = query;
    if (searchTag != null) q = (q.isEmpty() ? "" : q + " ") + "#" + searchTag;
    final String finalQuery = q;
    me.vkryl.core.lambda.RunnableData<List<TdApi.Message>> done = messages -> {
      if (gen != searchGeneration || isDestroyed()) return;
      if (searchTag != null) {
        // tags given by name on this phone are found too
        List<Long> local = Tgx101SavedStore.get(tdlib).localTagged(searchTag);
        Tgx101SavedKit.byIds(tdlib, local, more -> {
          if (gen != searchGeneration || isDestroyed()) return;
          setLoaded(merge(messages, more, query));
        });
      } else {
        setLoaded(messages);
      }
    };
    if (finalQuery.isEmpty()) {
      Tgx101SavedKit.search(tdlib, Tgx101SavedHomeController.filterFor(searchKind), null, 500, done);
    } else {
      Tgx101SavedKit.search(tdlib, searchKind != -1 ? Tgx101SavedHomeController.filterFor(searchKind) : null, finalQuery, 300, done);
    }
  }

  private List<TdApi.Message> merge (List<TdApi.Message> a, List<TdApi.Message> b, String mustContain) {
    Map<Long, TdApi.Message> map = new LinkedHashMap<>();
    for (TdApi.Message m : a) map.put(m.id, m);
    for (TdApi.Message m : b) {
      if (!mustContain.isEmpty() && !Tgx101SavedKit.title(m).toLowerCase(java.util.Locale.ROOT).contains(mustContain.toLowerCase(java.util.Locale.ROOT))) continue;
      if (searchKind != -1 && Tgx101SavedKit.kindOf(m) != searchKind) continue;
      map.put(m.id, m);
    }
    List<TdApi.Message> out = new ArrayList<>(map.values());
    Collections.sort(out, (x, y) -> Long.compare(y.id, x.id));
    return out;
  }

  private void showSearchHints () {
    items.clear();
    adapter.notifyDataSetChanged();
    countView.setText("");
    List<String> tags = Tgx101SavedStore.get(tdlib).knownTags();
    emptyView.setText(tags.isEmpty() ? Lang.getString(R.string.Tgx101SavedSearchEmptyHint) : Lang.getString(R.string.Tgx101SavedSearchTagsHint, "#" + TextUtils.join("  #", tags.subList(0, Math.min(8, tags.size())))));
    emptyView.setVisibility(View.VISIBLE);
  }

  // Loading

  private void load () {
    emptyView.setText(Lang.getString(R.string.LoadingInformation));
    emptyView.setVisibility(View.VISIBLE);
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    switch (mode) {
      case MODE_KIND:
        if (kind == -1) {
          Tgx101SavedKit.history(tdlib, 1000, this::setLoaded);
        } else {
          Tgx101SavedKit.search(tdlib, Tgx101SavedHomeController.filterFor(kind), null, 1000, this::setLoaded);
        }
        break;
      case MODE_INBOX: {
        Set<Long> filed = store.filedIds();
        Tgx101SavedKit.history(tdlib, 1000, messages -> {
          List<TdApi.Message> out = new ArrayList<>();
          for (TdApi.Message m : messages) if (!filed.contains(m.id)) out.add(m);
          setLoaded(out);
        });
        break;
      }
      case MODE_FOLDER: {
        Tgx101SavedStore.Folder f = store.folder(folderId);
        Tgx101SavedKit.byIds(tdlib, f != null ? new ArrayList<>(f.messageIds) : new ArrayList<>(), this::setLoaded);
        break;
      }
      case MODE_TAG: {
        List<Long> local = store.localTagged(tag);
        Tgx101SavedKit.search(tdlib, null, "#" + tag, 500, found -> Tgx101SavedKit.byIds(tdlib, local, more -> setLoaded(merge(found, more, ""))));
        break;
      }
    }
  }

  private void setLoaded (List<TdApi.Message> messages) {
    if (isDestroyed()) return;
    loaded.clear();
    loaded.addAll(messages);
    rebuildItems();
  }

  private Comparator<TdApi.Message> comparator () {
    Comparator<TdApi.Message> c;
    switch (sort) {
      case SORT_ORIGINAL: c = (a, b) -> Integer.compare(Tgx101SavedKit.originalDate(a), Tgx101SavedKit.originalDate(b)); break;
      case SORT_NAME: c = (a, b) -> Tgx101SavedKit.title(a).compareToIgnoreCase(Tgx101SavedKit.title(b)); break;
      case SORT_SIZE: c = (a, b) -> Long.compare(Tgx101SavedKit.size(a), Tgx101SavedKit.size(b)); break;
      case SORT_SOURCE: c = (a, b) -> Tgx101SavedKit.source(tdlib, a).compareToIgnoreCase(Tgx101SavedKit.source(tdlib, b)); break;
      case SORT_KIND: c = (a, b) -> Integer.compare(Tgx101SavedKit.kindOf(a), Tgx101SavedKit.kindOf(b)); break;
      default: c = (a, b) -> Long.compare(a.id, b.id); break;
    }
    // «newest first» is the natural order for dates and sizes; names, sources and kinds go A→Z first
    boolean naturalDesc = sort == SORT_SAVED || sort == SORT_ORIGINAL || sort == SORT_SIZE;
    boolean desc = naturalDesc != ascending;
    final Comparator<TdApi.Message> base = c;
    return (a, b) -> {
      int r = desc ? base.compare(b, a) : base.compare(a, b);
      return r != 0 ? r : Long.compare(b.id, a.id);
    };
  }

  private void rebuildItems () {
    if (adapter == null) return;
    List<TdApi.Message> shown = new ArrayList<>();
    int now = (int) (System.currentTimeMillis() / 1000);
    for (TdApi.Message m : loaded) {
      if (mode == MODE_SEARCH) {
        if (searchSource != null && !searchSource.equals(Tgx101SavedKit.source(tdlib, m))) continue;
        if (searchPeriodDays != 0 && m.date < now - searchPeriodDays * 86400) continue;
      }
      shown.add(m);
    }
    Collections.sort(shown, comparator());
    items.clear();
    boolean byMonth = viewMode == VIEW_GRID && (sort == SORT_SAVED || sort == SORT_ORIGINAL);
    String lastMonth = null;
    for (TdApi.Message m : shown) {
      if (byMonth) {
        String month = monthOf(sort == SORT_ORIGINAL ? Tgx101SavedKit.originalDate(m) : m.date);
        if (!month.equals(lastMonth)) {
          items.add(month);
          lastMonth = month;
        }
      }
      items.add(m);
    }
    adapter.notifyDataSetChanged();
    countView.setText(Lang.getString(R.string.Tgx101SavedCount, shown.size()));
    if (shown.isEmpty()) {
      emptyView.setText(Lang.getString(mode == MODE_FOLDER ? R.string.Tgx101SavedFolderEmpty : mode == MODE_INBOX ? R.string.Tgx101SavedInboxEmpty : R.string.Tgx101SavedNothing));
      emptyView.setVisibility(View.VISIBLE);
    } else {
      emptyView.setVisibility(View.GONE);
    }
  }

  private static String monthOf (int date) {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(date * 1000L);
    // stand-alone month name («Октябрь», not «октября»)
    String name = new java.text.SimpleDateFormat("LLLL", Lang.locale()).format(c.getTime());
    int year = c.get(Calendar.YEAR);
    Calendar now = Calendar.getInstance();
    String s = name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    return year == now.get(Calendar.YEAR) ? s : s + " " + year;
  }

  // Sort sheet

  private void showSortSheet () {
    int[] sorts = {SORT_SAVED, SORT_ORIGINAL, SORT_NAME, SORT_SIZE, SORT_SOURCE, SORT_KIND};
    int[] names = {R.string.Tgx101SavedSortSaved, R.string.Tgx101SavedSortOriginal, R.string.Tgx101SavedSortName, R.string.Tgx101SavedSortSize, R.string.Tgx101SavedSortSource, R.string.Tgx101SavedSortKind};
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101SavedSort)));
      for (int i = 0; i < sorts.length; i++) {
        final int s = sorts[i];
        body.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(names[i]), null, sort == s, v -> {
          sort = s;
          savePrefs();
          h[0].dismiss();
          rebuildItems();
        }));
      }
      // order
      LinearLayout seg = new LinearLayout(context);
      seg.setOrientation(LinearLayout.HORIZONTAL);
      seg.setPadding(Screen.dp(4f), Screen.dp(4f), Screen.dp(4f), Screen.dp(4f));
      seg.setBackground(Tgx101SavedHomeController.card(Theme.backgroundColor(), 12f));
      boolean naturalDesc = sort == SORT_SAVED || sort == SORT_ORIGINAL || sort == SORT_SIZE;
      String first = Lang.getString(naturalDesc ? R.string.Tgx101SavedOrderNewest : R.string.Tgx101SavedOrderAZ);
      String second = Lang.getString(naturalDesc ? R.string.Tgx101SavedOrderOldest : R.string.Tgx101SavedOrderZA);
      for (int i = 0; i < 2; i++) {
        final boolean asc = i == 1;
        TextView b = Tgx101SavedHomeController.text(context, 14f, ascending == asc ? Theme.textAccentColor() : Theme.textDecentColor(), ascending == asc);
        b.setText(i == 0 ? first : second);
        b.setGravity(Gravity.CENTER);
        b.setBackground(ascending == asc ? Tgx101SavedHomeController.card(Theme.fillingColor(), 9f) : null);
        Views.setClickable(b);
        b.setOnClickListener(v -> {
          ascending = asc;
          savePrefs();
          h[0].dismiss();
          rebuildItems();
        });
        seg.addView(b, new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f));
      }
      LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      sp.setMargins(Screen.dp(16f), Screen.dp(8f), Screen.dp(16f), 0);
      body.addView(seg, sp);
    });
  }

  // Selection

  private void buildSelectionBar (Context context, FrameLayout root) {
    selectionBar = new LinearLayout(context);
    selectionBar.setOrientation(LinearLayout.VERTICAL);
    selectionBar.setBackgroundColor(Theme.fillingColor());
    if (android.os.Build.VERSION.SDK_INT >= 21) selectionBar.setElevation(Screen.dp(8f));
    LinearLayout top = new LinearLayout(context);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(Screen.dp(4f), 0, Screen.dp(12f), 0);
    top.addView(iconButton(context, R.drawable.baseline_close_24, Lang.getString(R.string.Cancel), v -> clearSelection()));
    selectionTitle = Tgx101SavedHomeController.text(context, 15f, Theme.textAccentColor(), true);
    top.addView(selectionTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    TextView all = Tgx101SavedHomeController.text(context, 14f, Tgx101SavedHomeController.accent(), false);
    all.setText(Lang.getString(R.string.Tgx101SavedSelectAll));
    all.setPadding(Screen.dp(8f), Screen.dp(10f), Screen.dp(8f), Screen.dp(10f));
    Views.setClickable(all);
    all.setOnClickListener(v -> {
      for (Object o : items) if (o instanceof TdApi.Message) selected.add(((TdApi.Message) o).id);
      updateSelection();
    });
    top.addView(all);
    selectionBar.addView(top);
    LinearLayout actions = new LinearLayout(context);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    actions.setPadding(0, 0, 0, Screen.dp(6f) + Screen.getNavigationBarHeight());
    actions.addView(action(context, R.drawable.baseline_folder_24, R.string.Tgx101SavedToFolder, v -> Tgx101SavedActions.toFolder(this, selectedIds(), this::afterAction)));
    actions.addView(action(context, R.drawable.baseline_label_24, R.string.Tgx101SavedTag, v -> Tgx101SavedActions.tag(this, selectedMessages(), this::afterAction)));
    actions.addView(action(context, R.drawable.baseline_notifications_active_24, R.string.Tgx101SavedRemind, v -> Tgx101SavedActions.remind(this, selectedIds(), this::clearSelection)));
    actions.addView(action(context, R.drawable.baseline_forward_24, R.string.Tgx101SavedForward, v -> {
      Tgx101SavedActions.forward(this, selectedMessages());
      clearSelection();
    }));
    actions.addView(action(context, R.drawable.baseline_delete_24, R.string.Tgx101SavedDelete, v -> Tgx101SavedActions.delete(this, selectedIds(), () -> {
      for (long id : selectedIds()) removeLoaded(id);
      clearSelection();
    })));
    selectionBar.addView(actions);
    selectionBar.setVisibility(View.GONE);
    root.addView(selectionBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
  }

  private View action (Context context, int icon, int label, View.OnClickListener click) {
    LinearLayout b = new LinearLayout(context);
    b.setOrientation(LinearLayout.VERTICAL);
    b.setGravity(Gravity.CENTER);
    Views.setClickable(b);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(b);
    b.setOnClickListener(click);
    ImageView i = new ImageView(context);
    i.setImageResource(icon);
    i.setColorFilter(icon == R.drawable.baseline_delete_24 ? Theme.getColor(ColorId.textNegative) : Theme.getColor(ColorId.icon));
    b.addView(i, new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f)));
    TextView t = Tgx101SavedHomeController.text(context, 12f, icon == R.drawable.baseline_delete_24 ? Theme.getColor(ColorId.textNegative) : Theme.textAccentColor(), false);
    t.setText(Lang.getString(label));
    t.setSingleLine(true);
    t.setPadding(0, Screen.dp(4f), 0, 0);
    b.addView(t);
    b.setLayoutParams(new LinearLayout.LayoutParams(0, Screen.dp(64f), 1f));
    return b;
  }

  private long[] selectedIds () {
    long[] ids = new long[selected.size()];
    int i = 0;
    for (long id : selected) ids[i++] = id;
    return ids;
  }

  private List<TdApi.Message> selectedMessages () {
    List<TdApi.Message> out = new ArrayList<>();
    for (TdApi.Message m : loaded) if (selected.contains(m.id)) out.add(m);
    return out;
  }

  private void toggle (TdApi.Message m) {
    if (!selected.remove(m.id)) selected.add(m.id);
    updateSelection();
  }

  private void updateSelection () {
    boolean on = !selected.isEmpty();
    if (on && selectionBar.getVisibility() != View.VISIBLE) {
      selectionBar.setVisibility(View.VISIBLE);
      selectionBar.setTranslationY(Screen.dp(120f));
      selectionBar.animate().translationY(0f).setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    } else if (!on && selectionBar.getVisibility() == View.VISIBLE) {
      selectionBar.animate().translationY(selectionBar.getHeight()).setDuration(160).withEndAction(() -> selectionBar.setVisibility(View.GONE)).start();
    }
    selectionTitle.setText(Lang.getString(R.string.Tgx101SavedSelected, selected.size()));
    adapter.notifyItemRangeChanged(0, items.size(), "select");
  }

  private void clearSelection () {
    if (selected.isEmpty()) return;
    selected.clear();
    updateSelection();
  }

  private void afterAction () {
    clearSelection();
    if (mode == MODE_FOLDER || mode == MODE_INBOX || mode == MODE_TAG) load(); // the action may move things out of this list
  }

  private void removeLoaded (long id) {
    for (int i = loaded.size() - 1; i >= 0; i--) if (loaded.get(i).id == id) loaded.remove(i);
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (!selected.isEmpty()) {
      if (commit) clearSelection(); // «back» first leaves the selection
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  // Item views

  static View rowView (ViewController<?> c, TdApi.Message m, View.OnClickListener click, @Nullable View.OnLongClickListener longClick, boolean selected) {
    RowHolder h = new RowHolder(c.context());
    h.bind(c, m, selected, false);
    h.itemView.setOnClickListener(click);
    if (longClick != null) h.itemView.setOnLongClickListener(longClick);
    return h.itemView;
  }

  private static int kindIcon (int kind) {
    switch (kind) {
      case Tgx101SavedKit.KIND_FILE: return R.drawable.baseline_insert_drive_file_24;
      case Tgx101SavedKit.KIND_LINK: return R.drawable.baseline_link_24;
      case Tgx101SavedKit.KIND_VOICE: return R.drawable.baseline_mic_24;
      case Tgx101SavedKit.KIND_VIDEO: return R.drawable.baseline_videocam_24;
      case Tgx101SavedKit.KIND_PHOTO: return R.drawable.baseline_image_24;
    }
    return R.drawable.baseline_chat_bubble_24;
  }

  static final class RowHolder extends RecyclerView.ViewHolder {
    final ImageView thumb, check;
    final TextView title, meta;

    RowHolder (Context context) {
      super(new LinearLayout(context));
      LinearLayout row = (LinearLayout) itemView;
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(Screen.dp(12f), Screen.dp(10f), Screen.dp(12f), Screen.dp(10f));
      row.setMinimumHeight(Screen.dp(60f));
      Views.setClickable(row);
      org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(row);
      thumb = new ImageView(context);
      thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
      row.addView(thumb, new LinearLayout.LayoutParams(Screen.dp(42f), Screen.dp(42f)));
      LinearLayout texts = new LinearLayout(context);
      texts.setOrientation(LinearLayout.VERTICAL);
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      tp.leftMargin = Screen.dp(12f);
      row.addView(texts, tp);
      title = Tgx101SavedHomeController.text(context, 15f, Theme.textAccentColor(), true);
      title.setSingleLine(true);
      title.setEllipsize(TextUtils.TruncateAt.END);
      texts.addView(title);
      meta = Tgx101SavedHomeController.text(context, 12f, Theme.textDecentColor(), false);
      meta.setSingleLine(true);
      meta.setEllipsize(TextUtils.TruncateAt.END);
      texts.addView(meta);
      check = new ImageView(context);
      check.setImageResource(R.drawable.baseline_check_24);
      check.setScaleType(ImageView.ScaleType.CENTER);
      LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Screen.dp(26f), Screen.dp(26f));
      cp.leftMargin = Screen.dp(8f);
      row.addView(check, cp);
    }

    void bind (ViewController<?> c, TdApi.Message m, boolean selected, boolean selecting) {
      int kind = Tgx101SavedKit.kindOf(m);
      title.setText(Tgx101SavedKit.title(m));
      meta.setText(Tgx101SavedKit.meta(c.tdlib(), m));
      boolean hasThumb = Tgx101SavedKit.thumbFile(m) != null || Tgx101SavedKit.minithumb(m) != null;
      int color = kind == Tgx101SavedKit.KIND_VOICE ? 0xff2e8a4a : kind == Tgx101SavedKit.KIND_FILE ? 0xffb53c57 : Tgx101SavedHomeController.accent();
      thumb.setBackground(Tgx101SavedHomeController.card(Tgx101SavedHomeController.tint(color, .14f), kind == Tgx101SavedKit.KIND_VOICE ? 21f : 10f));
      if (android.os.Build.VERSION.SDK_INT >= 21) thumb.setClipToOutline(true);
      if (hasThumb) {
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.clearColorFilter();
        Tgx101SavedKit.loadThumb(c.tdlib(), thumb, m, Screen.dp(42f));
      } else {
        thumb.setTag(R.id.tgx101SavedThumb, null);
        thumb.setScaleType(ImageView.ScaleType.CENTER);
        thumb.setImageResource(kindIcon(kind));
        thumb.setColorFilter(color);
      }
      itemView.setBackground(selected ? Tgx101SavedHomeController.card(Tgx101SavedHomeController.tint(Tgx101SavedHomeController.accent(), .12f), 12f) : null);
      check.setVisibility(selecting ? View.VISIBLE : View.GONE);
      check.setColorFilter(selected ? Tgx101SavedHomeController.accent() : Theme.separatorColor());
    }
  }

  static final class GridHolder extends RecyclerView.ViewHolder {
    final ImageView image;
    final TextView badge, check;

    GridHolder (Context context) {
      super(new FrameLayout(context));
      FrameLayout cell = (FrameLayout) itemView;
      int size = (Screen.currentWidth() - Screen.dp(24f)) / 3;
      cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size));
      cell.setPadding(Screen.dp(1f), Screen.dp(1f), Screen.dp(1f), Screen.dp(1f));
      image = new ImageView(context);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      image.setBackgroundColor(Theme.fillingColor());
      cell.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      badge = new TextView(context);
      badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11f);
      badge.setTextColor(0xffffffff);
      badge.setBackground(Tgx101SavedHomeController.card(0x80000000, 6f));
      badge.setPadding(Screen.dp(6f), Screen.dp(2f), Screen.dp(6f), Screen.dp(2f));
      FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
      bp.setMargins(Screen.dp(6f), 0, 0, Screen.dp(6f));
      cell.addView(badge, bp);
      check = new TextView(context);
      check.setGravity(Gravity.CENTER);
      check.setTextColor(0xffffffff);
      check.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12f);
      FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f), Gravity.TOP | Gravity.RIGHT);
      cp.setMargins(0, Screen.dp(6f), Screen.dp(6f), 0);
      cell.addView(check, cp);
      Views.setClickable(cell);
    }

    void bind (ViewController<?> c, TdApi.Message m, int selectedIndex, boolean selecting) {
      int kind = Tgx101SavedKit.kindOf(m);
      if (Tgx101SavedKit.thumbFile(m) != null || Tgx101SavedKit.minithumb(m) != null) {
        image.clearColorFilter();
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Tgx101SavedKit.loadThumb(c.tdlib(), image, m, Screen.dp(120f));
      } else {
        image.setTag(R.id.tgx101SavedThumb, null);
        image.setScaleType(ImageView.ScaleType.CENTER);
        image.setImageResource(kindIcon(kind));
        image.setColorFilter(Theme.getColor(ColorId.icon));
      }
      String dur = null;
      if (m.content.getConstructor() == TdApi.MessageVideo.CONSTRUCTOR) dur = org.thunderdog.challegram.tool.Strings.buildDuration(((TdApi.MessageVideo) m.content).video.duration);
      else if (m.content.getConstructor() == TdApi.MessageAnimation.CONSTRUCTOR) dur = "GIF";
      else if (kind != Tgx101SavedKit.KIND_PHOTO) dur = Tgx101SavedKit.kindLabel(kind);
      badge.setVisibility(dur != null ? View.VISIBLE : View.GONE);
      badge.setText(dur);
      check.setVisibility(selecting ? View.VISIBLE : View.GONE);
      boolean on = selectedIndex > 0;
      android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
      ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
      ring.setColor(on ? Tgx101SavedHomeController.accent() : 0x33000000);
      ring.setStroke(Screen.dp(2f), 0xffffffff);
      check.setBackground(ring);
      check.setText(on ? String.valueOf(selectedIndex) : "");
      image.setAlpha(on ? .82f : 1f);
    }
  }

  static final class CardHolder extends RecyclerView.ViewHolder {
    final ImageView image;
    final TextView title, site, desc;

    CardHolder (Context context) {
      super(new LinearLayout(context));
      LinearLayout card = (LinearLayout) itemView;
      card.setOrientation(LinearLayout.VERTICAL);
      RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.setMargins(Screen.dp(4f), Screen.dp(5f), Screen.dp(4f), Screen.dp(5f));
      card.setLayoutParams(lp);
      card.setBackground(Tgx101SavedHomeController.card(Theme.fillingColor(), 16f));
      if (android.os.Build.VERSION.SDK_INT >= 21) card.setClipToOutline(true);
      Views.setClickable(card);
      image = new ImageView(context);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      card.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(150f)));
      LinearLayout body = new LinearLayout(context);
      body.setOrientation(LinearLayout.VERTICAL);
      body.setPadding(Screen.dp(14f), Screen.dp(10f), Screen.dp(14f), Screen.dp(12f));
      title = Tgx101SavedHomeController.text(context, 15f, Theme.textAccentColor(), true);
      title.setMaxLines(2);
      title.setEllipsize(TextUtils.TruncateAt.END);
      body.addView(title);
      desc = Tgx101SavedHomeController.text(context, 13f, Theme.textAccentColor(), false);
      desc.setMaxLines(2);
      desc.setEllipsize(TextUtils.TruncateAt.END);
      desc.setPadding(0, Screen.dp(3f), 0, 0);
      body.addView(desc);
      site = Tgx101SavedHomeController.text(context, 12f, Theme.textDecentColor(), false);
      site.setSingleLine(true);
      site.setEllipsize(TextUtils.TruncateAt.END);
      site.setPadding(0, Screen.dp(5f), 0, 0);
      body.addView(site);
      card.addView(body);
    }

    void bind (ViewController<?> c, TdApi.Message m, boolean selected) {
      boolean hasImage = Tgx101SavedKit.thumbFile(m) != null || Tgx101SavedKit.minithumb(m) != null;
      image.setVisibility(hasImage ? View.VISIBLE : View.GONE);
      if (hasImage) Tgx101SavedKit.loadThumb(c.tdlib(), image, m, Screen.dp(180f));
      title.setText(Tgx101SavedKit.title(m));
      TdApi.LinkPreview p = Tgx101SavedKit.linkPreview(m);
      String d = p != null && p.description != null ? p.description.text : null;
      desc.setVisibility(d != null && !d.isEmpty() ? View.VISIBLE : View.GONE);
      desc.setText(d);
      site.setText(Tgx101SavedKit.meta(c.tdlib(), m));
      itemView.setBackground(Tgx101SavedHomeController.card(selected ? Tgx101SavedHomeController.tint(Tgx101SavedHomeController.accent(), .14f) : Theme.fillingColor(), 16f));
    }
  }

  private static final int TYPE_HEADER = 0, TYPE_ROW = 1, TYPE_GRID = 2, TYPE_CARD = 3;

  private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    @Override
    public int getItemViewType (int position) {
      if (items.get(position) instanceof String) return TYPE_HEADER;
      return viewMode == VIEW_GRID ? TYPE_GRID : viewMode == VIEW_CARDS ? TYPE_CARD : TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      switch (viewType) {
        case TYPE_HEADER: {
          TextView t = Tgx101SavedHomeController.text(parent.getContext(), 14f, Theme.textAccentColor(), true);
          t.setPadding(Screen.dp(6f), Screen.dp(14f), Screen.dp(6f), Screen.dp(8f));
          t.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          return new RecyclerView.ViewHolder(t) { };
        }
        case TYPE_GRID: return new GridHolder(parent.getContext());
        case TYPE_CARD: return new CardHolder(parent.getContext());
      }
      RowHolder h = new RowHolder(parent.getContext());
      h.itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      return h;
    }

    @Override
    public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) {
      Object o = items.get(position);
      if (o instanceof String) {
        ((TextView) holder.itemView).setText((String) o);
        return;
      }
      TdApi.Message m = (TdApi.Message) o;
      boolean selecting = !selected.isEmpty();
      boolean isSelected = selected.contains(m.id);
      if (holder instanceof GridHolder) {
        int index = 0;
        if (isSelected) {
          int i = 0;
          for (long id : selected) {
            i++;
            if (id == m.id) {
              index = i;
              break;
            }
          }
        }
        ((GridHolder) holder).bind(Tgx101SavedListController.this, m, index, selecting);
      } else if (holder instanceof CardHolder) {
        ((CardHolder) holder).bind(Tgx101SavedListController.this, m, isSelected);
      } else {
        ((RowHolder) holder).bind(Tgx101SavedListController.this, m, isSelected, selecting);
        ((RowHolder) holder).itemView.setBackground(isSelected ? Tgx101SavedHomeController.card(Tgx101SavedHomeController.tint(Tgx101SavedHomeController.accent(), .12f), 12f) : Tgx101SavedHomeController.card(Theme.fillingColor(), 12f));
        RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
        lp.setMargins(0, Screen.dp(3f), 0, Screen.dp(3f));
      }
      holder.itemView.setOnClickListener(v -> {
        if (!selected.isEmpty()) {
          toggle(m);
        } else {
          Tgx101SavedKit.openInFeed(Tgx101SavedListController.this, m);
        }
      });
      holder.itemView.setOnLongClickListener(v -> {
        toggle(m);
        return true;
      });
    }

    @Override
    public int getItemCount () {
      return items.size();
    }
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (list != null && mode != MODE_SEARCH && loadedOnce) load(); // back from the feed: messages may have been deleted
    loadedOnce = true;
  }

  private boolean loadedOnce;
}
