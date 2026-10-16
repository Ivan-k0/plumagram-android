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
import android.view.MotionEvent;
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

  static final int MODE_KIND = 0, MODE_INBOX = 1, MODE_FOLDER = 2, MODE_TAG = 3, MODE_SEARCH = 4, MODE_REACTION = 5;
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

  /** everything marked with this reaction (Telegram's tag or kept here) */
  public static void openReaction (ViewController<?> c, String emoji) {
    Tgx101SavedListController l = new Tgx101SavedListController(c.context(), c.tdlib());
    l.mode = MODE_REACTION;
    l.reaction = emoji;
    c.navigateTo(l);
  }

  /**
   * TGx101 (approved mockup «Свайп вниз», А–Г): pulled down from the home screen — the search and big kind buttons at
   * the bottom, under the thumb, and the messages of the kind above them; a sideways swipe goes through the kinds
   */
  public static void openSearchReach (ViewController<?> c) {
    Tgx101SavedListController l = new Tgx101SavedListController(c.context(), c.tdlib());
    l.mode = MODE_SEARCH;
    l.reach = true;
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
  private String reaction;
  private boolean reach;

  @Override
  protected boolean forceFadeMode () {
    return reach || super.forceFadeMode(); // it comes down from the home screen in place, not from the side
  }

  @Override
  public boolean tgx101SlideBackInFadeMode () {
    return reach; // (user 2026-10-16) the search is left by the swipe «back» too
  }

  private final int[] slideLocation = new int[2], slideNavLocation = new int[2];

  /** (user 2026-10-16 19:40) never while something is selected — that is cleared first; in the search not over the
   *  kinds and refinements at the bottom, which slide sideways themselves */
  @Override
  public boolean canSlideBackFrom (org.thunderdog.challegram.navigation.NavigationController navigationController, float x, float y) {
    if (!selected.isEmpty()) return false;
    if (reachBottom != null && reachBottom.isShown()) {
      View nav = navigationController.getValue();
      if (nav != null) {
        reachBottom.getLocationOnScreen(slideLocation);
        nav.getLocationOnScreen(slideNavLocation);
        if (y >= slideLocation[1] - slideNavLocation[1]) return false;
      }
    }
    return super.canSlideBackFrom(navigationController, x, y);
  }

  private @Nullable View reachBottom;

  // search refinements
  private int searchKind = -1;
  private @Nullable String searchTag;
  private @Nullable String searchReaction;
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
  protected int getBackButton () {
    return org.thunderdog.challegram.navigation.BackHeaderButton.TYPE_BACK;
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
      case MODE_REACTION: return reaction;
      case MODE_SEARCH: return Lang.getString(R.string.Tgx101SavedSearchTitle);
    }
    return Tgx101SavedHomeController.kindPlural(kind);
  }

  private String screenKey () {
    switch (mode) {
      case MODE_INBOX: return "inbox";
      case MODE_FOLDER: return "f" + folderId;
      case MODE_TAG: return "t" + tag;
      case MODE_REACTION: return "r" + reaction;
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

    if (mode == MODE_SEARCH && !reach) buildSearchBar(context, column);
    if (mode == MODE_KIND) buildKindTabs(context, column);

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

    list = new RecyclerView(context) {
      @Override
      public boolean onInterceptTouchEvent (MotionEvent e) {
        // (user 2026-10-16 «жест назад в папках и метках — во время прокрутки, как в обычных чатах»): a touch on a still
        // flinging list kept the gesture for the list (it asks the parents not to intercept) — as in the chats, the
        // fling stops first, so the screen's «back» can take the swipe
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && getScrollState() == SCROLL_STATE_SETTLING) stopScroll();
        return super.onInterceptTouchEvent(e);
      }
    };
    list.setClipToPadding(false);
    list.setPadding(Screen.dp(12f), Screen.dp(4f), Screen.dp(12f), Screen.dp(96f));
    list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
    adapter = new Adapter();
    list.setAdapter(adapter);
    // a selection change only redraws the check: no cross-fade of the whole row (the «twitch» seen in chats)
    if (list.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
      ((androidx.recyclerview.widget.SimpleItemAnimator) list.getItemAnimator()).setSupportsChangeAnimations(false);
    }
    list.addOnItemTouchListener(dragSelect);
    list.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
      private float downX, downY;
      private boolean tracking;

      @Override
      public boolean onInterceptTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        if ((mode != MODE_KIND && !reach) || !selected.isEmpty()) return false;
        switch (e.getActionMasked()) {
          case MotionEvent.ACTION_DOWN: {
            downX = e.getX();
            downY = e.getY();
            // (user 2026-10-16) rows don't slide here: a sideways swipe anywhere switches the kind
            tracking = true;
            break;
          }
          case MotionEvent.ACTION_MOVE: {
            float dx = e.getX() - downX, dy = e.getY() - downY;
            if (tracking && Math.abs(dx) > Screen.dp(24f) && Math.abs(dx) > Math.abs(dy) * 2f) {
              tracking = false;
              swipeKind(dx);
              return true;
            }
            break;
          }
        }
        return false;
      }

      @Override
      public void onTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) { }

      @Override
      public void onRequestDisallowInterceptTouchEvent (boolean disallowIntercept) { }
    });
    list.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) Tgx101SwipeRow.closeOpen(); // scrolling closes an open row
      }
    });
    applyLayoutManager();
    FrameLayout listBox = new FrameLayout(context);
    listBox.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    emptyView = Tgx101SavedHomeController.text(context, 15f, Theme.textDecentColor(), false);
    emptyView.setGravity(Gravity.CENTER);
    emptyView.setPadding(Screen.dp(32f), 0, Screen.dp(32f), 0);
    emptyView.setText(Lang.getString(R.string.LoadingInformation));
    listBox.addView(emptyView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(200f), Gravity.TOP));
    final java.text.SimpleDateFormat dayFormat = new java.text.SimpleDateFormat("d MMMM yyyy", Lang.locale());
    listBox.addView(new Tgx101FastScroller(context, list, position -> {
      if (position < 0 || position >= items.size()) return null;
      Object o = items.get(position);
      if (o instanceof String) return (String) o;
      TdApi.Message m = (TdApi.Message) o;
      if (sort == SORT_NAME || sort == SORT_SOURCE || sort == SORT_KIND) { // not by date: the first letter / the source
        String key = sort == SORT_SOURCE ? Tgx101SavedKit.source(tdlib, m) : sort == SORT_KIND ? Tgx101SavedHomeController.kindPlural(Tgx101SavedKit.kindOf(m)) : Tgx101SavedKit.title(m);
        return sort == SORT_NAME ? (key.isEmpty() ? "" : key.substring(0, 1).toUpperCase(Lang.locale())) : key;
      }
      if (sort == SORT_SIZE) return org.thunderdog.challegram.tool.Strings.buildSize(Tgx101SavedKit.size(m));
      return dayFormat.format(new java.util.Date((sort == SORT_ORIGINAL ? Tgx101SavedKit.originalDate(m) : m.date) * 1000L));
    }), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    if (mode == MODE_FOLDER) {
      View shareBar = shareBar(context);
      if (shareBar != null) column.addView(shareBar);
    }
    column.addView(listBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    if (reach) {
      // under the thumb: the kinds, the other refinements, the field at the very bottom
      LinearLayout bottom = new LinearLayout(context);
      reachBottom = bottom;
      bottom.setOrientation(LinearLayout.VERTICAL);
      bottom.setBackgroundColor(Theme.backgroundColor());
      bottom.setPadding(0, 0, 0, Screen.dp(10f));
      buildKindTabs(context, bottom);
      buildSearchBar(context, bottom);
      ViewGroup box = (ViewGroup) searchField.getParent();
      box.removeView(searchField);
      LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(46f));
      fp.topMargin = Screen.dp(6f);
      box.addView(searchField, fp);
      if (searchChipViews != null) searchChipViews[0].setVisibility(View.GONE); // the kind is the big buttons now
      column.addView(bottom);
    }

    buildSelectionBar(context, root);

    if (mode == MODE_SEARCH && reach) {
      runSearch(); // the kind's messages at once; the keyboard comes when the field is tapped
      searchField.requestFocus(); // the cursor blinks there: it's clear you can type (user 2026-10-16)
    } else if (mode == MODE_SEARCH) {
      showSearchHints();
      searchField.requestFocus();
      searchField.postDelayed(() -> org.thunderdog.challegram.tool.Keyboard.show(searchField), 250);
    } else {
      load();
    }
    return root;
  }

  /**
   * TGx101 (user 2026-10-16 «внутри вверху нужна стилизованная кнопка — перейти в канал или группу»; «убрал файлы из
   * папки, а в канале они остались — это нужно помечать»): a shared folder opens with its channel / group a tap away,
   * and says how many copies are there that the folder no longer has
   */
  private @Nullable View shareBar (Context context) {
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    Tgx101SavedStore.Folder f = store.folder(folderId);
    if (f == null || !f.isShared()) return null;
    final long chatId = f.shareChatId;
    LinearLayout bar = new LinearLayout(context);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(Screen.dp(12f), Screen.dp(9f), Screen.dp(12f), Screen.dp(9f));
    bar.setBackground(Tgx101SavedHomeController.card(Tgx101SavedHomeController.tint(Tgx101SavedHomeController.accent(), .12f), 12.5f));
    Views.setClickable(bar);
    bar.setOnClickListener(v -> tdlib.ui().openChat(this, chatId, null));
    ImageView icon = new ImageView(context);
    icon.setImageResource(f.shareChannel ? R.drawable.baseline_bullhorn_24 : R.drawable.baseline_group_24);
    icon.setColorFilter(Tgx101SavedHomeController.accent());
    bar.addView(icon, new LinearLayout.LayoutParams(Screen.dp(22f), Screen.dp(22f)));
    LinearLayout texts = new LinearLayout(context);
    texts.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    tp.leftMargin = Screen.dp(10f);
    bar.addView(texts, tp);
    TextView title = Tgx101SavedHomeController.text(context, 14f, Tgx101SavedHomeController.accent(), true);
    title.setText(Lang.getString(f.shareChannel ? R.string.Tgx101ShareOpenChannel : R.string.Tgx101ShareOpenGroup));
    texts.addView(title);
    TextView sub = Tgx101SavedHomeController.text(context, 12f, Theme.textDecentColor(), false);
    int extra = store.sharedNotInFolder(f.id);
    String name = tdlib.chatTitle(chatId);
    sub.setText(extra > 0 ? Lang.getString(R.string.Tgx101ShareFolderExtra, extra) : Lang.getString(R.string.Tgx101ShareFolderBanner, name != null && !name.isEmpty() ? name : f.name));
    sub.setMaxLines(2);
    texts.addView(sub);
    ImageView arrow = new ImageView(context);
    arrow.setImageResource(R.drawable.round_keyboard_arrow_right_24);
    arrow.setColorFilter(Theme.getColor(ColorId.icon));
    bar.addView(arrow, new LinearLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f)));
    LinearLayout box = new LinearLayout(context);
    box.setPadding(Screen.dp(12f), Screen.dp(8f), Screen.dp(12f), Screen.dp(2f));
    box.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return box;
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

  // Kinds: tabs and swipes (user 2026-10-15 «свайпы переключают типы»)

  private static final int[] KIND_ORDER = {-1, Tgx101SavedKit.KIND_PHOTO, Tgx101SavedKit.KIND_VIDEO, Tgx101SavedKit.KIND_FILE, Tgx101SavedKit.KIND_LINK, Tgx101SavedKit.KIND_VOICE};
  private TextView[] kindTabs;
  private HorizontalScrollView kindTabsScroll;

  private void buildKindTabs (Context context, LinearLayout column) {
    kindTabsScroll = new HorizontalScrollView(context);
    kindTabsScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(Screen.dp(12f), Screen.dp(10f), Screen.dp(8f), Screen.dp(2f));
    kindTabs = new TextView[KIND_ORDER.length];
    for (int i = 0; i < KIND_ORDER.length; i++) {
      final int k = KIND_ORDER[i];
      TextView t = chip(Tgx101SavedHomeController.kindPlural(k), false, v -> switchKind(k));
      if (reach) {
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15f);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.getLayoutParams();
        lp.height = Screen.dp(40f); // big: under the thumb
      }
      kindTabs[i] = t;
      row.addView(t);
    }
    kindTabsScroll.addView(row);
    column.addView(kindTabsScroll);
    paintKindTabs();
  }

  private void paintKindTabs () {
    if (kindTabs == null) return;
    for (int i = 0; i < KIND_ORDER.length; i++) {
      setChip(kindTabs[i], Tgx101SavedHomeController.kindPlural(KIND_ORDER[i]), KIND_ORDER[i] == currentKind());
      if (KIND_ORDER[i] == currentKind()) {
        final View tab = kindTabs[i];
        kindTabsScroll.post(() -> kindTabsScroll.smoothScrollTo(Math.max(0, tab.getLeft() - Screen.dp(40f)), 0));
      }
    }
  }

  private int currentKind () {
    return reach ? searchKind : kind;
  }

  private int kindIndex () {
    for (int i = 0; i < KIND_ORDER.length; i++) if (KIND_ORDER[i] == currentKind()) return i;
    return 0;
  }

  /** the neighbour kind slides in from the side it was swiped to */
  private void switchKind (int newKind) {
    if (reach) {
      if (newKind == searchKind || isDestroyed()) return;
      final boolean forward = indexOfKind(newKind) > kindIndex();
      clearSelection();
      searchKind = newKind;
      paintKindTabs();
      float w = list.getWidth();
      list.animate().cancel();
      list.animate().translationX(forward ? -w * .25f : w * .25f).alpha(0f).setDuration(120).withEndAction(() -> {
        if (isDestroyed()) return;
        loaded.clear();
        items.clear();
        adapter.notifyDataSetChanged();
        runSearch();
        list.setTranslationX(forward ? w * .25f : -w * .25f);
        list.animate().translationX(0f).alpha(1f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
      }).start();
      return;
    }
    if (mode != MODE_KIND || newKind == kind || isDestroyed()) return;
    final boolean forward = indexOfKind(newKind) > kindIndex();
    clearSelection();
    Tgx101SwipeRow.closeOpen();
    kind = newKind;
    paintKindTabs();
    if (navigationController() != null && navigationController().getHeaderView() != null) {
      navigationController().getHeaderView().updateTextTitle(getId(), getName());
    }
    float w = list.getWidth();
    list.animate().cancel();
    list.animate().translationX(forward ? -w * .25f : w * .25f).alpha(0f).setDuration(120).withEndAction(() -> {
      if (isDestroyed()) return;
      loadPrefs();
      buildViewSwitch();
      applyLayoutManager();
      loaded.clear();
      items.clear();
      adapter.notifyDataSetChanged();
      load();
      list.setTranslationX(forward ? w * .25f : -w * .25f);
      list.animate().translationX(0f).alpha(1f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }).start();
  }

  private static int indexOfKind (int k) {
    for (int i = 0; i < KIND_ORDER.length; i++) if (KIND_ORDER[i] == k) return i;
    return 0;
  }

  /** a sideways swipe on the list (outside the sliding rows) goes to the next / previous kind */
  private boolean swipeKind (float dx) {
    if (mode != MODE_KIND && !reach) return false;
    int i = kindIndex() + (dx < 0 ? 1 : -1);
    if (i < 0 || i >= KIND_ORDER.length) return false;
    UI.forceVibrate(list, false);
    switchKind(KIND_ORDER[i]);
    return true;
  }

  // Search bar

  private void buildSearchBar (Context context, LinearLayout column) {
    LinearLayout box = new LinearLayout(context);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(Screen.dp(12f), Screen.dp(10f), Screen.dp(12f), 0);
    searchField = new EditText(context);
    searchField.setHint(Lang.getString(R.string.Tgx101SavedSearchHint));
    searchField.setSingleLine(true);
    searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    Tgx101SavedHomeController.styleField(searchField, false); // the chats' field bubble, like every field in the storage
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

  private TextView[] searchChipViews;

  /**
   * The four filter chips are made once and change in place (test 2026-10-15: rebuilt on every pick, the row jumped
   * and a pressed chip stayed as a phantom under the closing sheet)
   */
  private void buildSearchChips () {
    if (searchChips == null) return;
    if (searchChipViews == null) {
      searchChipViews = new TextView[] {
        chip("", false, v -> pickFilter(this::pickKind)),
        chip("", false, v -> pickFilter(this::pickTag)),
        chip("", false, v -> pickFilter(this::pickSource)),
        chip("", false, v -> pickFilter(this::pickPeriod)),
        chip("", false, v -> pickFilter(this::pickReaction))
      };
      for (TextView c : searchChipViews) searchChips.addView(c);
    }
    setChip(searchChipViews[0], searchKind == -1 ? Lang.getString(R.string.Tgx101SavedFilterKind) : Lang.getString(R.string.Tgx101SavedFilterKindValue, Tgx101SavedHomeController.kindPlural(searchKind)), searchKind != -1);
    setChip(searchChipViews[1], searchTag == null ? Lang.getString(R.string.Tgx101SavedFilterTag) : "#" + searchTag, searchTag != null);
    setChip(searchChipViews[2], searchSource == null ? Lang.getString(R.string.Tgx101SavedFilterSource) : searchSource, searchSource != null);
    setChip(searchChipViews[3], searchPeriodDays == 0 ? Lang.getString(R.string.Tgx101SavedFilterPeriod) : periodName(searchPeriodDays), searchPeriodDays != 0);
    setChip(searchChipViews[4], searchReaction == null ? Lang.getString(R.string.Tgx101SavedReaction) : Tgx101SavedKit.emoji(searchReaction), searchReaction != null);
  }

  /** the keyboard goes down first: otherwise it closes under the sheet and everything jumps */
  private void pickFilter (Runnable pick) {
    if (searchField != null && searchField.hasFocus()) {
      searchField.clearFocus();
      org.thunderdog.challegram.tool.Keyboard.hide(searchField);
      searchField.postDelayed(pick, 160);
    } else {
      pick.run();
    }
  }

  private void setChip (TextView c, CharSequence label, boolean on) {
    c.setText(label);
    c.setTextColor(on ? Theme.fillingColor() : Theme.textAccentColor());
    // on the pull-down panel the bar itself is the filling colour: the chips stand out on the background colour
    c.setBackground(Tgx101SavedHomeController.card(on ? Theme.textAccentColor() : Theme.fillingColor(), 17f));
    c.setPressed(false);
  }

  /** The chip changes at once; the list after the sheet has gone down, so the closing animation stays smooth */
  private void applyFilter (Tgx101Sheet.Handle h, boolean search) {
    h.dismiss();
    buildSearchChips();
    if (searchChips != null) {
      searchChips.postDelayed(() -> {
        if (isDestroyed()) return;
        if (search) runSearch();
        else rebuildItems();
      }, 240);
    }
  }

  private void pickKind () {
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101SavedFilterKind)));
      body.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(R.string.Tgx101SavedEverything), null, searchKind == -1, v -> {
        searchKind = -1;
        applyFilter(h[0], true);
      }));
      for (int k : new int[] {Tgx101SavedKit.KIND_PHOTO, Tgx101SavedKit.KIND_VIDEO, Tgx101SavedKit.KIND_FILE, Tgx101SavedKit.KIND_LINK, Tgx101SavedKit.KIND_VOICE}) {
        body.addView(Tgx101Sheet.row(context, 0, 0, Tgx101SavedHomeController.kindPlural(k), null, searchKind == k, v -> {
          searchKind = k;
          applyFilter(h[0], true);
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
        applyFilter(h[0], true);
      }));
      for (String t : tags) {
        body.addView(Tgx101Sheet.row(context, 0, 0, "#" + t, null, t.equals(searchTag), v -> {
          searchTag = t;
          applyFilter(h[0], true);
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
        applyFilter(h[0], false);
      }));
      int n = 0;
      for (String s : sources) {
        if (s.isEmpty() || n++ >= 12) continue;
        body.addView(Tgx101Sheet.row(context, 0, 0, s, null, s.equals(searchSource), v -> {
          searchSource = s;
          applyFilter(h[0], false);
        }));
      }
    });
  }

  private void pickReaction () {
    Tgx101SavedKit.reactionCounts(tdlib, counts -> {
      if (isDestroyed()) return;
      java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>(counts.keySet());
      for (String e : Tgx101SavedKit.QUICK_REACTIONS) all.add(e);
      Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
      h[0] = Tgx101Sheet.show(this, null, body -> {
        body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101SavedReaction)));
        body.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(R.string.Tgx101SavedAnyReaction), null, searchReaction == null, v -> {
          searchReaction = null;
          applyFilter(h[0], true);
        }));
        for (String e : all) {
          Integer n = counts.get(e);
          body.addView(Tgx101Sheet.row(context, 0, 0, Tgx101SavedKit.emoji(e), n != null ? String.valueOf(n) : null, e.equals(searchReaction), v -> {
            searchReaction = e;
            applyFilter(h[0], true);
          }));
        }
      });
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
          applyFilter(h[0], false);
        }));
      }
    });
  }

  private int searchGeneration;

  private void runSearch () {
    if (mode != MODE_SEARCH || isDestroyed()) return;
    final int gen = ++searchGeneration;
    if (query.isEmpty() && searchKind == -1 && searchTag == null && searchReaction != null) {
      emptyView.setText(Lang.getString(R.string.LoadingInformation));
      emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : emptyView.getVisibility());
      Tgx101SavedKit.searchReaction(tdlib, searchReaction, messages -> {
        if (gen == searchGeneration && !isDestroyed()) setLoaded(messages);
      });
      return;
    }
    if (reach && query.isEmpty() && searchTag == null && searchReaction == null) {
      // pulled down: nothing typed yet — the chosen kind's messages, newest first
      me.vkryl.core.lambda.RunnableData<List<TdApi.Message>> browse = messages -> {
        if (gen == searchGeneration && !isDestroyed()) setLoaded(messages);
      };
      if (searchKind == -1) Tgx101SavedKit.history(tdlib, 300, browse);
      else Tgx101SavedKit.search(tdlib, Tgx101SavedHomeController.filterFor(searchKind), null, 500, browse);
      return;
    }
    if (query.isEmpty() && searchKind == -1 && searchTag == null) {
      loaded.clear();
      showSearchHints();
      return;
    }
    if (items.isEmpty()) { // what is shown stays until the new results come (no blink to «Loading» and back)
      emptyView.setText(Lang.getString(R.string.LoadingInformation));
      emptyView.setVisibility(View.VISIBLE);
    }
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
      case MODE_REACTION:
        Tgx101SavedKit.searchReaction(tdlib, reaction, this::setLoaded);
        break;
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
        if (searchReaction != null && !Tgx101SavedKit.reactionsOf(tdlib, m).contains(searchReaction)) continue;
      }
      shown.add(m);
    }
    Collections.sort(shown, comparator());
    items.clear();
    // by date: tiles by month; rows and cards by «Today / Yesterday / month» — the rows don't repeat the date
    boolean byMonth = sort == SORT_SAVED || sort == SORT_ORIGINAL;
    String lastMonth = null;
    for (TdApi.Message m : shown) {
      if (byMonth) {
        int date = sort == SORT_ORIGINAL ? Tgx101SavedKit.originalDate(m) : m.date;
        String month = viewMode == VIEW_GRID ? monthOf(date) : dayOrMonth(date);
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

  private static String dayOrMonth (int date) {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(date * 1000L);
    Calendar today = Calendar.getInstance();
    if (c.get(Calendar.YEAR) == today.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) return Lang.getString(R.string.Today);
    today.add(Calendar.DAY_OF_MONTH, -1);
    if (c.get(Calendar.YEAR) == today.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) return Lang.getString(R.string.Yesterday);
    return monthOf(date);
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

  private Tgx101SelectionPanel selectionPanel;

  /** approved mockup «Панель действий при выделении», 3: the count and «All» on top, reactions, capsules */
  private void buildSelectionBar (Context context, FrameLayout root) {
    selectionPanel = new Tgx101SelectionPanel(context, this, new Tgx101SelectionPanel.Host() {
      @Override
      public List<TdApi.Message> selectedMessages () {
        return Tgx101SavedListController.this.selectedMessages();
      }

      @Override
      public void afterAction (boolean clear) {
        if (clear) {
          Tgx101SavedListController.this.afterAction();
        } else {
          if (mode == MODE_REACTION) load();
          else adapter.notifyItemRangeChanged(0, items.size());
        }
      }
    });
    selectionBar = selectionPanel;
    LinearLayout top = new LinearLayout(context);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(Screen.dp(4f), 0, Screen.dp(12f), Screen.dp(4f));
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
    selectionBar.addView(top, 0);
    selectionBar.setVisibility(View.GONE);
    root.addView(selectionBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
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
    if (on) selectionPanel.update();
    adapter.notifyItemRangeChanged(0, items.size(), "select");
  }

  private void clearSelection () {
    if (selected.isEmpty()) return;
    selected.clear();
    updateSelection();
  }

  private void afterAction () {
    clearSelection();
    if (mode == MODE_FOLDER || mode == MODE_INBOX || mode == MODE_TAG || mode == MODE_REACTION) load(); // the action may move things out of this list
  }

  private void removeLoaded (long id) {
    for (int i = loaded.size() - 1; i >= 0; i--) if (loaded.get(i).id == id) loaded.remove(i);
  }

  /**
   * TGx101 (user 2026-10-15): after a long press the finger selects every item it passes without lifting, in rows,
   * tiles and cards; near the top or bottom the list scrolls by itself
   */
  private final DragSelect dragSelect = new DragSelect();

  private final class DragSelect implements RecyclerView.OnItemTouchListener {
    private boolean active;
    private int anchor, last;
    private boolean selecting;
    private java.util.Set<Long> before;
    private float lastX, lastY;

    void begin (int position) {
      Object o = items.get(position);
      if (!(o instanceof TdApi.Message)) return;
      active = true;
      anchor = last = position;
      before = new java.util.HashSet<>(selected);
      long id = ((TdApi.Message) o).id;
      selecting = !selected.contains(id);
      if (selecting) selected.add(id); else selected.remove(id);
      list.getParent().requestDisallowInterceptTouchEvent(true);
      updateSelection();
    }

    @Override
    public boolean onInterceptTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) {
      if (active && (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
        end();
        return false;
      }
      return active;
    }

    @Override
    public void onTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_MOVE:
          lastX = e.getX();
          lastY = e.getY();
          follow();
          rv.removeCallbacks(scroller);
          if (scrollStep() != 0) rv.post(scroller);
          break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          end();
          break;
      }
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent (boolean disallowIntercept) { }

    private void end () {
      active = false;
      list.removeCallbacks(scroller);
    }

    private void follow () {
      View child = list.findChildViewUnder(Math.max(0, Math.min(list.getWidth() - 1, lastX)), Math.max(0, Math.min(list.getHeight() - 1, lastY)));
      if (child == null) return;
      int position = list.getChildAdapterPosition(child);
      if (position == RecyclerView.NO_POSITION || position == last) return;
      last = position;
      int from = Math.min(anchor, position), to = Math.max(anchor, position);
      boolean changed = false;
      for (int i = 0; i < items.size(); i++) {
        Object o = items.get(i);
        if (!(o instanceof TdApi.Message)) continue;
        long id = ((TdApi.Message) o).id;
        boolean want = i >= from && i <= to ? selecting : before.contains(id);
        if (want != selected.contains(id)) {
          changed = true;
          if (want) selected.add(id); else selected.remove(id);
        }
      }
      if (changed) {
        UI.forceVibrate(list, false);
        updateSelection();
      }
    }

    private int scrollStep () {
      float edge = Screen.dp(64f);
      if (lastY < edge) return -Math.round(Screen.dp(16f) * Math.min(1f, (edge - lastY) / edge));
      if (lastY > list.getHeight() - edge) return Math.round(Screen.dp(16f) * Math.min(1f, (lastY - (list.getHeight() - edge)) / edge));
      return 0;
    }

    private final Runnable scroller = new Runnable() {
      @Override
      public void run () {
        if (!active) return;
        int step = scrollStep();
        if (step == 0) return;
        list.scrollBy(0, step);
        follow();
        list.postDelayed(this, 16);
      }
    };
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    // (user 2026-10-16 «жест назад должен всегда сначала закрывать клавиатуру») — the keyboard first, then the screen
    if (searchField != null && getKeyboardState()) {
      if (commit) {
        org.thunderdog.challegram.tool.Keyboard.hide(searchField);
        searchField.clearFocus();
      }
      return true;
    }
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

  static int kindIcon (int kind) {
    switch (kind) {
      case Tgx101SavedKit.KIND_FILE: return R.drawable.baseline_insert_drive_file_24;
      case Tgx101SavedKit.KIND_LINK: return R.drawable.baseline_link_24;
      case Tgx101SavedKit.KIND_VOICE: return R.drawable.baseline_mic_24;
      case Tgx101SavedKit.KIND_VIDEO: return R.drawable.baseline_videocam_24;
      case Tgx101SavedKit.KIND_PHOTO: return R.drawable.baseline_image_24;
    }
    return R.drawable.baseline_chat_bubble_24;
  }

  /** a small rounded label: the tag (crimson) or the folder (its own colour) */
  static TextView badge (Context context) {
    TextView b = Tgx101SavedHomeController.text(context, 11f, 0xffffffff, true);
    b.setSingleLine(true);
    b.setEllipsize(TextUtils.TruncateAt.END);
    b.setGravity(Gravity.CENTER_VERTICAL);
    b.setPadding(Screen.dp(7f), 0, Screen.dp(7f), 0);
    return b;
  }

  static void paintBadge (TextView b, @Nullable String text, int color) {
    if (text == null) {
      b.setVisibility(View.GONE);
      return;
    }
    b.setVisibility(View.VISIBLE);
    b.setText(text);
    b.setTextColor(Theme.isDark() ? lighten(color) : color);
    b.setBackground(Tgx101SavedHomeController.card(Tgx101SavedHomeController.tint(color, Theme.isDark() ? .30f : .16f), 9f));
  }

  static final int TAG_COLOR = 0xffb53c57;

  /** the label's colour on a dark theme: halfway to white, readable on its own dark tint */
  static int lighten (int color) {
    int r = (color >> 16) & 0xff, g = (color >> 8) & 0xff, b = color & 0xff;
    return 0xff000000 | ((r + (255 - r) / 2) << 16) | ((g + (255 - g) / 2) << 8) | (b + (255 - b) / 2);
  }

  /** The folder sign on a thumbnail's corner, in the folder's colour */
  static ImageView folderSign (Context context) {
    ImageView f = new ImageView(context);
    f.setImageResource(R.drawable.baseline_folder_24);
    f.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    f.setPadding(Screen.dp(2f), Screen.dp(2f), Screen.dp(2f), Screen.dp(2f));
    return f;
  }

  static final class RowHolder extends RecyclerView.ViewHolder {
    final ImageView thumb, check, folderSign;
    final TextView title, meta, tagBadge, folderBadge, reaction;
    boolean withDate = true;

    RowHolder (Context context) {
      super(new LinearLayout(context));
      LinearLayout row = (LinearLayout) itemView;
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(Screen.dp(12f), Screen.dp(10f), Screen.dp(12f), Screen.dp(10f));
      row.setMinimumHeight(Screen.dp(60f));
      Views.setClickable(row);
      org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(row);
      FrameLayout thumbBox = new FrameLayout(context);
      thumbBox.setClipChildren(false);
      thumb = new ImageView(context);
      thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
      thumbBox.addView(thumb, new FrameLayout.LayoutParams(Screen.dp(42f), Screen.dp(42f)));
      folderSign = folderSign(context);
      FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f), Gravity.BOTTOM | Gravity.RIGHT);
      thumbBox.addView(folderSign, fp);
      row.setClipChildren(false);
      row.addView(thumbBox, new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));
      LinearLayout texts = new LinearLayout(context);
      texts.setOrientation(LinearLayout.VERTICAL);
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      tp.leftMargin = Screen.dp(12f);
      row.addView(texts, tp);
      title = Tgx101SavedHomeController.text(context, 15f, Theme.textAccentColor(), true);
      title.setSingleLine(true);
      title.setEllipsize(TextUtils.TruncateAt.END);
      texts.addView(title);
      LinearLayout metaRow = new LinearLayout(context);
      metaRow.setOrientation(LinearLayout.HORIZONTAL);
      metaRow.setGravity(Gravity.CENTER_VERTICAL);
      meta = Tgx101SavedHomeController.text(context, 12f, Theme.textDecentColor(), false);
      meta.setSingleLine(true);
      meta.setEllipsize(TextUtils.TruncateAt.END);
      metaRow.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      tagBadge = badge(context);
      LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(18f));
      bp.leftMargin = Screen.dp(6f);
      metaRow.addView(tagBadge, bp);
      folderBadge = badge(context);
      LinearLayout.LayoutParams fbp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(18f));
      fbp.leftMargin = Screen.dp(6f);
      metaRow.addView(folderBadge, fbp); // the last one: shortened or cut when the line is full
      LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      mp.topMargin = Screen.dp(2f);
      texts.addView(metaRow, mp);
      reaction = Tgx101SavedHomeController.text(context, 20f, Theme.textAccentColor(), false);
      reaction.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      rp.leftMargin = Screen.dp(8f);
      row.addView(reaction, rp);
      check = new ImageView(context);
      check.setImageResource(R.drawable.baseline_check_24);
      check.setScaleType(ImageView.ScaleType.CENTER);
      LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Screen.dp(26f), Screen.dp(26f));
      cp.leftMargin = Screen.dp(8f);
      row.addView(check, cp);
    }

    void bind (ViewController<?> c, TdApi.Message m, boolean selected, boolean selecting) {
      bind(c, m, selected, selecting, null);
    }

    /** the ring around the folder sign: the row's own fill */
    private int itemViewFill () {
      return Theme.fillingColor();
    }

    void bind (ViewController<?> c, TdApi.Message m, boolean selected, boolean selecting, @Nullable String highlight) {
      int kind = Tgx101SavedKit.kindOf(m);
      title.setText(highlighted(Tgx101SavedKit.title(m), highlight));
      meta.setText(Tgx101SavedKit.meta(c.tdlib(), m, withDate));
      Tgx101SavedStore.Folder folder = Tgx101SavedStore.get(c.tdlib()).folderOf(m.id);
      paintBadge(tagBadge, Tgx101SavedKit.firstTagShort(c.tdlib(), m), TAG_COLOR);
      paintBadge(folderBadge, folder != null ? folder.name : null, folder != null ? folder.color() : 0);
      folderSign.setVisibility(folder != null ? View.VISIBLE : View.GONE);
      if (folder != null) {
        folderSign.setColorFilter(folder.color());
        folderSign.setBackground(Tgx101SavedHomeController.card(itemViewFill(), 7f));
      }
      List<String> reactions = Tgx101SavedKit.reactionsOf(c.tdlib(), m);
      reaction.setText(reactions.isEmpty() ? "" : Tgx101SavedKit.emoji(reactions.get(0)));
      reaction.setVisibility(reactions.isEmpty() ? View.GONE : View.VISIBLE); // stays while selecting (user 2026-10-16)
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
      if (selected) {
        itemView.setBackground(Tgx101SavedHomeController.card(Tgx101SavedHomeController.tint(Tgx101SavedHomeController.accent(), .12f), 12f));
      } else {
        org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(itemView); // keeps the press feedback
      }
      // (user 2026-10-16) no ticks on rows: a selected row is lit, and its reaction stays in view
      check.setVisibility(View.GONE);
    }
  }

  /** The searched word marked in a result (mockup 2) */
  static CharSequence highlighted (String text, @Nullable String query) {
    if (query == null || query.isEmpty() || text == null) return text;
    String lower = text.toLowerCase(java.util.Locale.ROOT), q = query.toLowerCase(java.util.Locale.ROOT);
    int at = lower.indexOf(q);
    if (at < 0) return text;
    android.text.SpannableString s = new android.text.SpannableString(text);
    while (at >= 0) {
      s.setSpan(new android.text.style.BackgroundColorSpan(Tgx101SavedHomeController.tint(0xfff2c94c, .45f)), at, at + q.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      at = lower.indexOf(q, at + q.length());
    }
    return s;
  }

  static final class GridHolder extends RecyclerView.ViewHolder {
    final ImageView image, kindIcon;
    final TextView badge, check, text, date, reaction, tag;
    final View folderDot;
    final LinearLayout info;

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
      // (user 2026-10-16 «вместо пустых плашек должен быть виден текст и название файлов»): a tile without a picture
      // shows a small sign of its kind and its own words — the note, the file's name, the link's title
      kindIcon = new ImageView(context);
      kindIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
      FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(Screen.dp(16f), Screen.dp(16f), Gravity.TOP | Gravity.LEFT);
      kp.setMargins(Screen.dp(8f), Screen.dp(8f), 0, 0);
      cell.addView(kindIcon, kp);
      text = new TextView(context);
      text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12f);
      text.setTextColor(Theme.textAccentColor());
      text.setLineSpacing(0, 1.05f);
      text.setMaxLines(4);
      text.setEllipsize(android.text.TextUtils.TruncateAt.END);
      FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
      xp.setMargins(Screen.dp(8f), Screen.dp(28f), Screen.dp(8f), Screen.dp(26f));
      cell.addView(text, xp);
      badge = new TextView(context);
      badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11f);
      badge.setTextColor(0xffffffff);
      badge.setBackground(Tgx101SavedHomeController.card(0x80000000, 6f));
      badge.setPadding(Screen.dp(6f), Screen.dp(2f), Screen.dp(6f), Screen.dp(2f));
      FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
      bp.setMargins(0, 0, Screen.dp(6f), Screen.dp(6f));
      cell.addView(badge, bp);
      // bottom left (user 2026-10-16): the folder's colour, the date in digits and the reaction it is marked with
      info = new LinearLayout(context);
      info.setOrientation(LinearLayout.HORIZONTAL);
      info.setGravity(Gravity.CENTER_VERTICAL);
      info.setPadding(Screen.dp(5f), Screen.dp(2f), Screen.dp(6f), Screen.dp(2f));
      folderDot = new View(context);
      LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Screen.dp(8f), Screen.dp(8f));
      dp.rightMargin = Screen.dp(4f);
      info.addView(folderDot, dp);
      tag = new TextView(context);
      tag.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 10.5f);
      tag.setSingleLine(true);
      tag.setPadding(0, 0, Screen.dp(4f), 0);
      info.addView(tag);
      date = new TextView(context);
      date.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 10.5f);
      date.setSingleLine(true);
      info.addView(date);
      reaction = new TextView(context);
      reaction.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12f);
      reaction.setPadding(Screen.dp(4f), 0, 0, 0);
      info.addView(reaction);
      FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
      ip.setMargins(Screen.dp(4f), 0, 0, Screen.dp(5f));
      cell.addView(info, ip);
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
      boolean picture = Tgx101SavedKit.thumbFile(m) != null || Tgx101SavedKit.minithumb(m) != null;
      if (picture) {
        image.clearColorFilter();
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Tgx101SavedKit.loadThumb(c.tdlib(), image, m, Screen.dp(120f));
        kindIcon.setVisibility(View.GONE);
        text.setVisibility(View.GONE);
      } else {
        image.setTag(R.id.tgx101SavedThumb, null);
        image.setImageDrawable(null);
        kindIcon.setVisibility(View.VISIBLE);
        kindIcon.setImageResource(kindIcon(kind));
        kindIcon.setColorFilter(Tgx101SavedHomeController.accent());
        String words = Tgx101SavedKit.title(m);
        text.setVisibility(View.VISIBLE);
        text.setText(words != null && !words.isEmpty() ? words : Tgx101SavedKit.kindLabel(kind));
      }
      String dur = null;
      if (m.content.getConstructor() == TdApi.MessageVideo.CONSTRUCTOR) dur = org.thunderdog.challegram.tool.Strings.buildDuration(((TdApi.MessageVideo) m.content).video.duration);
      else if (m.content.getConstructor() == TdApi.MessageAnimation.CONSTRUCTOR) dur = "GIF";
      badge.setVisibility(dur != null ? View.VISIBLE : View.GONE);
      badge.setText(dur);
      check.setVisibility(selecting ? View.VISIBLE : View.GONE);
      // the bottom-left line: light text on a dark pill over a picture, quiet grey text on a plain tile
      int infoColor = picture ? 0xffffffff : Theme.textDecentColor();
      info.setBackground(picture ? Tgx101SavedHomeController.card(0x80000000, 8f) : null);
      Tgx101SavedStore.Folder folder = Tgx101SavedStore.get(c.tdlib()).folderOf(m.id);
      folderDot.setVisibility(folder != null ? View.VISIBLE : View.GONE);
      if (folder != null) folderDot.setBackground(Tgx101SavedHomeController.card(folder.color(), 2.5f));
      String tagShort = folder == null ? Tgx101SavedKit.firstTagShort(c.tdlib(), m) : null;
      tag.setVisibility(tagShort != null ? View.VISIBLE : View.GONE);
      if (tagShort != null) {
        tag.setText(tagShort.startsWith("#") ? tagShort : "#" + tagShort);
        tag.setTextColor(picture ? 0xffffffff : 0xffb53c57);
      }
      date.setText(Tgx101SavedKit.shortDate(m.date));
      date.setTextColor(infoColor);
      List<String> reactions = Tgx101SavedKit.reactionsOf(c.tdlib(), m);
      reaction.setText(reactions.isEmpty() ? "" : Tgx101SavedKit.emoji(reactions.get(0)));
      reaction.setVisibility(reactions.isEmpty() ? View.GONE : View.VISIBLE);
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

  /** A row or a link card inside a sliding frame (approved mockup «Недавнее» Б — on every list, user 2026-10-15) */
  final class SwipeHolder extends RecyclerView.ViewHolder {
    final RecyclerView.ViewHolder inner;
    final Tgx101SwipeRow swipe;
    TdApi.Message message;

    SwipeHolder (RecyclerView.ViewHolder inner, float radiusDp) {
      super(new FrameLayout(inner.itemView.getContext()));
      this.inner = inner;
      ViewGroup.LayoutParams innerParams = inner.itemView.getLayoutParams();
      swipe = new Tgx101SwipeRow(inner.itemView.getContext(), inner.itemView, new Tgx101SwipeRow.Callback() {
        @Override
        public void onFolder (boolean longPress) {
          if (message != null) Tgx101SavedActions.swipeFolder(Tgx101SavedListController.this, message, longPress, Tgx101SavedListController.this::afterSwipe);
        }

        @Override
        public void onTag (boolean longPress) {
          if (message != null) Tgx101SavedActions.swipeTag(Tgx101SavedListController.this, message, longPress, Tgx101SavedListController.this::afterSwipe);
        }

        @Override
        public void onMore () {
          if (message == null) return;
          Tgx101SavedActions.more(Tgx101SavedListController.this, message, new Tgx101SavedActions.MoreHost() {
            @Override
            public void startSelection (TdApi.Message m) {
              selected.add(m.id);
              updateSelection();
            }

            @Override
            public void changed () {
              afterSwipe();
            }
          });
        }

        @Override
        public void onSelect () {
          if (message != null) toggle(message);
        }
      });
      if (android.os.Build.VERSION.SDK_INT >= 21) { // the sliding layers keep the row's round corners
        final float radius = Screen.dp(radiusDp);
        swipe.setOutlineProvider(new android.view.ViewOutlineProvider() {
          @Override
          public void getOutline (View view, android.graphics.Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
          }
        });
        swipe.setClipToOutline(true);
      }
      FrameLayout frame = (FrameLayout) itemView;
      RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      if (innerParams instanceof ViewGroup.MarginLayoutParams) {
        ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) innerParams;
        lp.setMargins(m.leftMargin, m.topMargin, m.rightMargin, m.bottomMargin);
      } else {
        lp.setMargins(0, Screen.dp(3f), 0, Screen.dp(3f));
      }
      frame.setLayoutParams(lp);
      frame.addView(swipe, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
  }

  /** after a swipe button: the list may lose the message (a folder, the inbox) or show it differently */
  private void afterSwipe () {
    if (mode == MODE_FOLDER || mode == MODE_INBOX || mode == MODE_TAG || mode == MODE_REACTION) load();
    else adapter.notifyItemRangeChanged(0, items.size());
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

    String highlightQuery;

    void bind (ViewController<?> c, TdApi.Message m, boolean selected) {
      boolean hasImage = Tgx101SavedKit.thumbFile(m) != null || Tgx101SavedKit.minithumb(m) != null;
      image.setVisibility(hasImage ? View.VISIBLE : View.GONE);
      if (hasImage) Tgx101SavedKit.loadThumb(c.tdlib(), image, m, Screen.dp(180f));
      title.setText(highlighted(Tgx101SavedKit.title(m), highlightQuery));
      TdApi.LinkPreview p = Tgx101SavedKit.linkPreview(m);
      String d = p != null && p.description != null ? Tgx101SavedKit.unescape(p.description.text) : null;
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
        case TYPE_CARD: return new SwipeHolder(new CardHolder(parent.getContext()), 16f);
      }
      RowHolder h = new RowHolder(parent.getContext());
      return new SwipeHolder(h, 12f);
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
      }
      View tap = holder.itemView;
      if (holder instanceof SwipeHolder) {
        SwipeHolder sh = (SwipeHolder) holder;
        sh.message = m;
        tap = sh.inner.itemView;
        if (sh.inner instanceof CardHolder) {
          ((CardHolder) sh.inner).highlightQuery = mode == MODE_SEARCH ? query : null;
          ((CardHolder) sh.inner).bind(Tgx101SavedListController.this, m, isSelected);
        } else {
          ((RowHolder) sh.inner).withDate = true; // the date in digits on every row, headers or not (user 2026-10-16)
          ((RowHolder) sh.inner).bind(Tgx101SavedListController.this, m, isSelected, selecting, mode == MODE_SEARCH ? query : null);
          sh.inner.itemView.setBackground(isSelected ? Tgx101SavedHomeController.card(Tgx101SavedHomeController.tint(Tgx101SavedHomeController.accent(), .12f), 12f) : Tgx101SavedHomeController.card(Theme.fillingColor(), 12f));
        }
        sh.swipe.setSwipeEnabled(false); // user 2026-10-16: on the lists a swipe is for the kinds; actions are on a long press
        Tgx101SavedActions.applySwipeTargets(tdlib, sh.swipe);
      }
      final RecyclerView.ViewHolder bound = holder;
      tap.setOnClickListener(v -> {
        if (!selected.isEmpty()) {
          toggle(m);
        } else {
          Tgx101SavedKit.openInFeed(Tgx101SavedListController.this, m);
        }
      });
      tap.setOnLongClickListener(v -> {
        int at = bound.getAdapterPosition();
        if (at == RecyclerView.NO_POSITION) return false;
        UI.forceVibrate(v, false);
        dragSelect.begin(at);
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
    if (reach && searchField != null && !searchField.hasFocus()) {
      // (user 2026-10-16 «добавь мигающую полосу, как в обычном поле ввода»): asked while the screen was being built,
      // the focus was lost on the way in — the bottom field takes it once the screen is shown, the keyboard stays down
      searchField.post(() -> {
        searchField.setCursorVisible(true);
        searchField.requestFocus();
        searchField.setSelection(searchField.length());
      });
    }
    if (list != null && mode != MODE_SEARCH && loadedOnce) load(); // back from the feed: messages may have been deleted
    loadedOnce = true;
  }

  private boolean loadedOnce;

  /** Something was saved / filed while this list is open */
  void reloadSoon () {
    if (list == null || isDestroyed() || mode == MODE_SEARCH) return;
    list.removeCallbacks(reloader);
    list.postDelayed(reloader, 400); // Telegram has the new message in the history by then
  }

  private final Runnable reloader = () -> {
    if (!isDestroyed()) load();
  };
}
