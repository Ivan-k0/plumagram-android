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
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import java.util.List;

/**
 * TGx101 (user 2026-10-15, approved mockups 1 · «Главный экран»): Saved Messages as a storage — search, the kinds
 * (photos, files, links, voice) counted by Telegram, folders and tags as tiles, pinned, recent, and a field that
 * saves a note. «Feed» opens the usual chat.
 */
public class Tgx101SavedHomeController extends ViewController<Void> implements Menu, View.OnClickListener {

  public static void open (BaseActivity context, Tdlib tdlib) {
    ViewController<?> top = context.navigation().getCurrentStackItem();
    if (top instanceof Tgx101SavedHomeController && top.tdlib() == tdlib) return;
    context.navigation().navigateTo(new Tgx101SavedHomeController(context, tdlib));
  }

  public Tgx101SavedHomeController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101SavedHome;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.SavedMessages);
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_tgx101SavedHome;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_tgx101SavedHome) {
      header.addButton(menu, R.id.menu_btn_tgx101SavedSearch, R.drawable.baseline_search_24, getHeaderIconColorId(), this, Screen.dp(49f));
      header.addButton(menu, R.id.menu_btn_tgx101SavedFeed, R.drawable.baseline_forum_24, getHeaderIconColorId(), this, Screen.dp(49f));
      header.addButton(menu, R.id.menu_btn_tgx101SavedSettings, R.drawable.baseline_settings_24, getHeaderIconColorId(), this, Screen.dp(49f));
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_tgx101SavedSearch) {
      Tgx101SavedListController.openSearch(this);
    } else if (id == R.id.menu_btn_tgx101SavedFeed) {
      Tgx101SavedKit.openInFeed(this, null);
    } else if (id == R.id.menu_btn_tgx101SavedSettings) {
      navigateTo(new Tgx101SavedSettingsController(context, tdlib));
    }
  }

  @Override
  public void onClick (View v) {
    onMenuItemPressed(v.getId(), v);
  }

  private LinearLayout column;
  private LinearLayout chips;
  private LinearLayout tiles;
  private LinearLayout pinnedRow;
  private View pinnedSection;
  private LinearLayout recentList;
  private EditText noteField;

  // Small builders

  static GradientDrawable card (int color, float radiusDp) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(Screen.dp(radiusDp));
    return d;
  }

  static TextView text (Context context, float sp, int color, boolean medium) {
    TextView v = new TextView(context);
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
    v.setTextColor(color);
    if (medium) v.setTypeface(Fonts.getRobotoMedium());
    return v;
  }

  static TextView sectionTitle (Context context, String title) {
    TextView v = text(context, 15f, Theme.textAccentColor(), true);
    v.setText(title);
    v.setPadding(Screen.dp(16f), Screen.dp(18f), Screen.dp(16f), Screen.dp(8f));
    return v;
  }

  static int accent () {
    return Theme.getColor(ColorId.textLink);
  }

  /** a transparent tint of a colour for icon backgrounds */
  static int tint (int color, float alpha) {
    return (Math.round(alpha * 255) << 24) | (color & 0xffffff);
  }

  @Override
  protected View onCreateView (Context context) {
    FrameLayout root = new FrameLayout(context);
    root.setBackgroundColor(Theme.backgroundColor());

    LinearLayout outer = new LinearLayout(context);
    outer.setOrientation(LinearLayout.VERTICAL);
    root.addView(outer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    ScrollView scroll = new ScrollView(context);
    scroll.setFillViewport(true);
    scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
    outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setPadding(0, Screen.dp(12f), 0, Screen.dp(16f));
    scroll.addView(column, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    // search pill
    LinearLayout search = new LinearLayout(context);
    search.setOrientation(LinearLayout.HORIZONTAL);
    search.setGravity(Gravity.CENTER_VERTICAL);
    search.setPadding(Screen.dp(14f), 0, Screen.dp(14f), 0);
    search.setBackground(card(Theme.fillingColor(), 21f));
    Views.setClickable(search);
    search.setOnClickListener(v -> Tgx101SavedListController.openSearch(this));
    ImageView glass = new ImageView(context);
    glass.setImageResource(R.drawable.baseline_search_24);
    glass.setColorFilter(Theme.getColor(ColorId.icon));
    search.addView(glass, new LinearLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f)));
    TextView hint = text(context, 15f, Theme.textDecentColor(), false);
    hint.setText(Lang.getString(R.string.Tgx101SavedSearchHint));
    LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    hintParams.leftMargin = Screen.dp(10f);
    search.addView(hint, hintParams);
    LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(42f));
    searchParams.leftMargin = searchParams.rightMargin = Screen.dp(16f);
    column.addView(search, searchParams);

    // kinds
    HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
    chipsScroll.setHorizontalScrollBarEnabled(false);
    chips = new LinearLayout(context);
    chips.setOrientation(LinearLayout.HORIZONTAL);
    chips.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(8f), 0);
    chipsScroll.addView(chips);
    column.addView(chipsScroll);

    // folders and tags
    column.addView(sectionTitle(context, Lang.getString(R.string.Tgx101SavedFoldersAndTags)));
    tiles = new LinearLayout(context);
    tiles.setOrientation(LinearLayout.VERTICAL);
    tiles.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    column.addView(tiles);

    // pinned
    LinearLayout pinnedBox = new LinearLayout(context);
    pinnedBox.setOrientation(LinearLayout.VERTICAL);
    pinnedBox.addView(sectionTitle(context, Lang.getString(R.string.Tgx101SavedPinned)));
    HorizontalScrollView pinnedScroll = new HorizontalScrollView(context);
    pinnedScroll.setHorizontalScrollBarEnabled(false);
    pinnedRow = new LinearLayout(context);
    pinnedRow.setOrientation(LinearLayout.HORIZONTAL);
    pinnedRow.setPadding(Screen.dp(16f), 0, Screen.dp(8f), 0);
    pinnedScroll.addView(pinnedRow);
    pinnedBox.addView(pinnedScroll);
    pinnedBox.setVisibility(View.GONE);
    pinnedSection = pinnedBox;
    column.addView(pinnedBox);

    // recent
    LinearLayout recentHeader = new LinearLayout(context);
    recentHeader.setOrientation(LinearLayout.HORIZONTAL);
    recentHeader.setGravity(Gravity.BOTTOM);
    recentHeader.addView(sectionTitle(context, Lang.getString(R.string.Tgx101SavedRecent)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    TextView all = text(context, 14f, accent(), false);
    all.setText(Lang.getString(R.string.Tgx101SavedAll));
    all.setPadding(Screen.dp(16f), Screen.dp(18f), Screen.dp(16f), Screen.dp(8f));
    Views.setClickable(all);
    all.setOnClickListener(v -> Tgx101SavedListController.openKind(this, -1));
    recentHeader.addView(all);
    column.addView(recentHeader);
    recentList = new LinearLayout(context);
    recentList.setOrientation(LinearLayout.VERTICAL);
    recentList.setBackground(card(Theme.fillingColor(), 14f));
    LinearLayout.LayoutParams recentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    recentParams.leftMargin = recentParams.rightMargin = Screen.dp(16f);
    column.addView(recentList, recentParams);

    // note field
    LinearLayout bar = new LinearLayout(context);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setBackgroundColor(Theme.fillingColor());
    bar.setPadding(Screen.dp(12f), Screen.dp(8f), Screen.dp(6f), Screen.dp(8f));
    noteField = new EditText(context);
    noteField.setHint(Lang.getString(R.string.Tgx101SavedNoteHint));
    noteField.setHintTextColor(Theme.textDecentColor());
    noteField.setTextColor(Theme.textAccentColor());
    noteField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    noteField.setMaxLines(5);
    noteField.setBackground(card(Theme.backgroundColor(), 20f));
    noteField.setPadding(Screen.dp(14f), Screen.dp(9f), Screen.dp(14f), Screen.dp(9f));
    bar.addView(noteField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    ImageView send = new ImageView(context);
    send.setImageResource(R.drawable.baseline_send_24);
    send.setColorFilter(accent());
    send.setScaleType(ImageView.ScaleType.CENTER);
    send.setContentDescription(Lang.getString(R.string.Tgx101SavedNoteSave));
    Views.setClickable(send);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(send);
    send.setOnClickListener(v -> saveNote());
    bar.addView(send, new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));
    outer.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    reload();
    return root;
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (column != null && loadedOnce) reload(); // back from a folder / list / settings: counts and tiles may have changed
  }

  private boolean loadedOnce;

  private void reload () {
    loadedOnce = true;
    buildChips();
    buildTiles();
    loadPinned();
    loadRecent();
  }

  // Kinds

  private static final int[] KIND_FILTER_KINDS = {Tgx101SavedKit.KIND_PHOTO, Tgx101SavedKit.KIND_VIDEO, Tgx101SavedKit.KIND_FILE, Tgx101SavedKit.KIND_LINK, Tgx101SavedKit.KIND_VOICE};

  static TdApi.SearchMessagesFilter filterFor (int kind) {
    switch (kind) {
      case Tgx101SavedKit.KIND_PHOTO: return new TdApi.SearchMessagesFilterPhoto();
      case Tgx101SavedKit.KIND_VIDEO: return new TdApi.SearchMessagesFilterVideo();
      case Tgx101SavedKit.KIND_FILE: return new TdApi.SearchMessagesFilterDocument();
      case Tgx101SavedKit.KIND_LINK: return new TdApi.SearchMessagesFilterUrl();
      case Tgx101SavedKit.KIND_VOICE: return new TdApi.SearchMessagesFilterVoiceNote();
    }
    return null;
  }

  static String kindPlural (int kind) {
    switch (kind) {
      case Tgx101SavedKit.KIND_PHOTO: return Lang.getString(R.string.Tgx101SavedPhotos);
      case Tgx101SavedKit.KIND_VIDEO: return Lang.getString(R.string.Tgx101SavedVideos);
      case Tgx101SavedKit.KIND_FILE: return Lang.getString(R.string.Tgx101SavedFiles);
      case Tgx101SavedKit.KIND_LINK: return Lang.getString(R.string.Tgx101SavedLinks);
      case Tgx101SavedKit.KIND_VOICE: return Lang.getString(R.string.Tgx101SavedVoices);
    }
    return Lang.getString(R.string.Tgx101SavedEverything);
  }

  private TextView chip (String label, boolean dark, View.OnClickListener click) {
    TextView c = text(context, 14f, dark ? Theme.fillingColor() : Theme.textAccentColor(), false);
    c.setText(label);
    c.setGravity(Gravity.CENTER);
    c.setPadding(Screen.dp(14f), 0, Screen.dp(14f), 0);
    c.setBackground(card(dark ? Theme.textAccentColor() : Theme.fillingColor(), 18f));
    Views.setClickable(c);
    c.setOnClickListener(click);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(36f));
    p.rightMargin = Screen.dp(8f);
    c.setLayoutParams(p);
    return c;
  }

  private void buildChips () {
    chips.removeAllViews();
    chips.addView(chip(Lang.getString(R.string.Tgx101SavedEverything), true, v -> Tgx101SavedListController.openKind(this, -1)));
    for (int kind : KIND_FILTER_KINDS) {
      final TextView c = chip(kindPlural(kind), false, v -> Tgx101SavedListController.openKind(this, kind));
      chips.addView(c);
      Tgx101SavedKit.count(tdlib, filterFor(kind), count -> {
        if (isDestroyed()) return;
        if (count == 0) {
          c.setVisibility(View.GONE); // nothing of this kind: no empty chip
        } else if (count > 0) {
          c.setText(kindPlural(kind) + " " + count);
        }
      });
    }
  }

  // Tiles

  private void buildTiles () {
    tiles.removeAllViews();
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    java.util.List<View> cells = new java.util.ArrayList<>();
    if (store.inboxTile) {
      cells.add(tile(R.drawable.baseline_archive_24, null, Lang.getString(R.string.Tgx101SavedInbox), Lang.getString(R.string.Tgx101SavedInboxHint), accent(),
        v -> Tgx101SavedListController.openInbox(this), null));
    }
    for (Tgx101SavedStore.Folder f : store.folders()) {
      if (!f.onHome) continue;
      cells.add(tile(R.drawable.baseline_folder_24, null, f.name, String.valueOf(f.messageIds.size()), f.color(),
        v -> Tgx101SavedListController.openFolder(this, f.id),
        v -> {
          folderOptions(f);
          return true;
        }));
    }
    for (String tag : store.homeTags()) {
      cells.add(tile(0, "#", tag, Lang.getString(R.string.Tgx101SavedTagTile), 0xffb53c57,
        v -> Tgx101SavedListController.openTag(this, tag),
        v -> {
          showOptions(null, new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.Tgx101SavedRemoveFromHome), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.NORMAL, OptionColor.NORMAL}, new int[] {R.drawable.baseline_close_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
            if (id == R.id.btn_delete) {
              Tgx101SavedStore.get(tdlib).setTagOnHome(tag, false);
              buildTiles();
            }
            return true;
          });
          return true;
        }));
    }
    cells.add(newTile());
    LinearLayout row = null;
    for (int i = 0; i < cells.size(); i++) {
      if (i % 3 == 0) {
        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        tiles.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      }
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Screen.dp(98f), 1f);
      p.setMargins(Screen.dp(4f), Screen.dp(4f), Screen.dp(4f), Screen.dp(4f));
      row.addView(cells.get(i), p);
    }
    for (int i = cells.size() % 3; i != 0 && i < 3; i++) {
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Screen.dp(98f), 1f);
      p.setMargins(Screen.dp(4f), Screen.dp(4f), Screen.dp(4f), Screen.dp(4f));
      row.addView(new View(context), p); // keeps the last row's tiles the same width
    }
  }

  private View tile (int iconRes, @Nullable String glyph, String name, String sub, int color, View.OnClickListener click, @Nullable View.OnLongClickListener longClick) {
    LinearLayout t = new LinearLayout(context);
    t.setOrientation(LinearLayout.VERTICAL);
    t.setPadding(Screen.dp(12f), Screen.dp(12f), Screen.dp(10f), Screen.dp(10f));
    t.setBackground(card(Theme.fillingColor(), 14f));
    Views.setClickable(t);
    t.setOnClickListener(click);
    if (longClick != null) t.setOnLongClickListener(longClick);
    FrameLayout icon = new FrameLayout(context);
    icon.setBackground(card(tint(color, .16f), glyph != null ? 17f : 10f));
    if (glyph != null) {
      TextView g = text(context, 18f, color, true);
      g.setText(glyph);
      g.setGravity(Gravity.CENTER);
      icon.addView(g, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    } else {
      ImageView i = new ImageView(context);
      i.setImageResource(iconRes);
      i.setColorFilter(color);
      i.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
      FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f), Gravity.CENTER);
      icon.addView(i, ip);
    }
    t.addView(icon, new LinearLayout.LayoutParams(Screen.dp(34f), Screen.dp(34f)));
    TextView n = text(context, 14f, Theme.textAccentColor(), true);
    n.setText(name);
    n.setSingleLine(true);
    n.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    np.topMargin = Screen.dp(8f);
    t.addView(n, np);
    TextView s = text(context, 12f, Theme.textDecentColor(), false);
    s.setText(sub);
    s.setSingleLine(true);
    s.setEllipsize(TextUtils.TruncateAt.END);
    t.addView(s);
    return t;
  }

  private View newTile () {
    LinearLayout t = new LinearLayout(context);
    t.setOrientation(LinearLayout.VERTICAL);
    t.setGravity(Gravity.CENTER);
    GradientDrawable d = new GradientDrawable();
    d.setCornerRadius(Screen.dp(14f));
    d.setStroke(Screen.dp(1.5f), Theme.separatorColor(), Screen.dp(6f), Screen.dp(4f));
    t.setBackground(d);
    Views.setClickable(t);
    t.setOnClickListener(v -> Tgx101SavedActions.newFolderOrTag(this, this::buildTiles));
    ImageView plus = new ImageView(context);
    plus.setImageResource(R.drawable.baseline_add_24);
    plus.setColorFilter(accent());
    t.addView(plus, new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f)));
    TextView l = text(context, 13f, accent(), false);
    l.setText(Lang.getString(R.string.Tgx101SavedNew));
    l.setPadding(0, Screen.dp(4f), 0, 0);
    t.addView(l);
    return t;
  }

  private void folderOptions (Tgx101SavedStore.Folder f) {
    showOptions(f.name, new int[] {R.id.btn_edit, R.id.btn_close, R.id.btn_delete}, new String[] {Lang.getString(R.string.Tgx101SavedRename), Lang.getString(R.string.Tgx101SavedRemoveFromHome), Lang.getString(R.string.Tgx101SavedDeleteFolder)},
      new int[] {OptionColor.NORMAL, OptionColor.NORMAL, OptionColor.RED}, new int[] {R.drawable.baseline_edit_24, R.drawable.baseline_close_24, R.drawable.baseline_delete_24}, (itemView, id) -> {
        Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
        if (id == R.id.btn_edit) {
          Tgx101SavedActions.askName(this, Lang.getString(R.string.Tgx101SavedRename), f.name, name -> {
            store.renameFolder(f.id, name);
            buildTiles();
          });
        } else if (id == R.id.btn_close) {
          store.setFolderOnHome(f.id, false);
          buildTiles();
        } else if (id == R.id.btn_delete) {
          store.deleteFolder(f.id); // only the folder: the messages stay in Saved
          buildTiles();
        }
        return true;
      });
  }

  // Pinned and recent

  private void loadPinned () {
    if (!Tgx101SavedStore.get(tdlib).showPinned) {
      pinnedSection.setVisibility(View.GONE);
      return;
    }
    Tgx101SavedKit.search(tdlib, new TdApi.SearchMessagesFilterPinned(), null, 12, messages -> {
      if (isDestroyed()) return;
      pinnedRow.removeAllViews();
      for (TdApi.Message m : messages) pinnedRow.addView(pinnedCard(m));
      pinnedSection.setVisibility(messages.isEmpty() ? View.GONE : View.VISIBLE);
    });
  }

  private View pinnedCard (TdApi.Message m) {
    LinearLayout c = new LinearLayout(context);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setBackground(card(Theme.fillingColor(), 14f));
    if (android.os.Build.VERSION.SDK_INT >= 21) c.setClipToOutline(true); // the picture keeps the card's round corners
    Views.setClickable(c);
    c.setOnClickListener(v -> Tgx101SavedKit.openInFeed(this, m));
    int kind = Tgx101SavedKit.kindOf(m);
    if (Tgx101SavedKit.thumbFile(m) != null || Tgx101SavedKit.minithumb(m) != null) {
      ImageView img = new ImageView(context);
      img.setScaleType(ImageView.ScaleType.CENTER_CROP);
      img.setBackgroundColor(Theme.backgroundColor());
      Tgx101SavedKit.loadThumb(tdlib, img, m, Screen.dp(80f));
      c.addView(img, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(64f)));
    }
    LinearLayout body = new LinearLayout(context);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(Screen.dp(12f), Screen.dp(8f), Screen.dp(12f), Screen.dp(10f));
    TextView k = text(context, 12f, Theme.textDecentColor(), false);
    k.setText(Tgx101SavedKit.kindLabel(kind));
    body.addView(k);
    TextView t = text(context, 14f, Theme.textAccentColor(), false);
    t.setText(Tgx101SavedKit.title(m));
    t.setMaxLines(c.getChildCount() > 0 ? 1 : 3);
    t.setEllipsize(TextUtils.TruncateAt.END);
    body.addView(t);
    c.addView(body);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Screen.dp(150f), Screen.dp(118f));
    p.rightMargin = Screen.dp(8f);
    c.setLayoutParams(p);
    if (android.os.Build.VERSION.SDK_INT >= 21) {
      c.setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Screen.dp(14f));
        }
      });
    }
    return c;
  }

  private void loadRecent () {
    Tgx101SavedKit.history(tdlib, 12, messages -> {
      if (isDestroyed()) return;
      recentList.removeAllViews();
      int n = Math.min(12, messages.size());
      if (n == 0) {
        TextView empty = text(context, 14f, Theme.textDecentColor(), false);
        empty.setText(Lang.getString(R.string.Tgx101SavedRecentEmpty));
        empty.setPadding(Screen.dp(14f), Screen.dp(14f), Screen.dp(14f), Screen.dp(14f));
        recentList.addView(empty);
        return;
      }
      for (int i = 0; i < n; i++) {
        if (i > 0) {
          View sep = new View(context);
          sep.setBackgroundColor(Theme.separatorColor());
          LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Screen.dp(.5f)));
          sp.leftMargin = Screen.dp(66f);
          recentList.addView(sep, sp);
        }
        TdApi.Message m = messages.get(i);
        recentList.addView(Tgx101SavedListController.rowView(this, m, v -> Tgx101SavedKit.openInFeed(this, m), null, false));
      }
    });
  }

  // Note

  private void saveNote () {
    String text = noteField.getText().toString().trim();
    if (text.isEmpty()) return;
    noteField.setText("");
    TdApi.FormattedText formatted = new TdApi.FormattedText(text, new TdApi.TextEntity[0]);
    try {
      // links and #hashtags in the note become clickable (and searchable) like in a typed message
      TdApi.TextEntities found = org.drinkless.tdlib.Client.execute(new TdApi.GetTextEntities(text));
      if (found != null) formatted.entities = found.entities;
    } catch (Throwable ignored) { }
    Tgx101SavedStore.suppressBanner(3000); // a note typed here needs no «put in folder?» banner
    tdlib.send(new TdApi.SendMessage(tdlib.selfChatId(), null, null, null, null, new TdApi.InputMessageText(formatted, null, false)), (sent, error) -> UI.post(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      UI.showToast(R.string.Tgx101SavedNoteSaved, android.widget.Toast.LENGTH_SHORT);
      if (!isDestroyed()) {
        UI.post(this::loadRecent, 300);
        UI.post(this::buildChips, 300);
      }
    }));
  }

  @Override
  public void destroy () {
    super.destroy();
  }
}
