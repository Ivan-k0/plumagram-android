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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 (user 2026-10-26, screenshots 21:31 of the official app): the stories kept on a profile — album chips on top
 * («All stories», the albums, «+ Add album» on one's own), the pinned ones first with a pin, each tile with its video
 * length and views; on one's own a long press adds to / removes from an album or (un)pins, and «Add» posts a new story
 * (into the album that is open). The own archive is the same grid without albums.
 */
public class Tgx101PostsController extends ViewController<Tgx101PostsController.Args> {
  public static class Args {
    final long chatId;
    boolean archive;

    public Args (long chatId, boolean archive) {
      this.chatId = chatId;
      this.archive = archive;
    }
  }

  public Tgx101PostsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private final List<TdApi.Story> stories = new ArrayList<>();
  private final Set<Integer> pinned = new HashSet<>();
  private final List<TdApi.StoryAlbum> albums = new ArrayList<>();
  private int albumId; // 0 — all stories
  private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;
  private @Nullable RecyclerView listView;
  private LinearLayout chips;
  private TextView empty;
  private boolean loading, ended;
  private int generation;
  private View chipScroll, addButton;
  private TextView tabPosts, tabArchive;

  private boolean isOwn () {
    return getArgumentsStrict().chatId == tdlib.selfChatId();
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101Posts;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(getArgumentsStrict().archive ? R.string.Tgx101PostsArchive : R.string.Tgx101Posts);
  }

  // TGx101 (user 2026-10-26 «архив вынести в окно самих публикаций отдельным переключением вверху, нативно, по тапу»):
  // on one's own profile the title is a two-way switch «Posts | Archive»
  private LinearLayout headerSwitch;

  @Override
  public View getCustomHeaderCell () {
    if (!isOwn()) return null;
    if (headerSwitch == null) {
      headerSwitch = new LinearLayout(context);
      headerSwitch.setOrientation(LinearLayout.HORIZONTAL);
      headerSwitch.setGravity(Gravity.CENTER_VERTICAL);
      GradientDrawable bg = new GradientDrawable();
      bg.setColor(0x26ffffff);
      bg.setCornerRadius(Screen.dp(18f));
      headerSwitch.setBackground(bg);
      headerSwitch.setPadding(Screen.dp(3f), Screen.dp(3f), Screen.dp(3f), Screen.dp(3f));
      tabPosts = headerTab(R.string.Tgx101Posts, false);
      tabArchive = headerTab(R.string.Tgx101PostsArchiveShort, true);
      FrameLayout wrap = new FrameLayout(context);
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(36f), Gravity.CENTER_VERTICAL | Gravity.START);
      lp.leftMargin = Screen.dp(68f);
      wrap.addView(headerSwitch, lp);
      // (crash 2026-10-26 21:52): the header sets a top margin on its custom cell — the params must take margins
      wrap.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, org.thunderdog.challegram.navigation.HeaderView.getSize(false)));
      paintHeaderTabs();
      return wrap;
    }
    return (View) headerSwitch.getParent();
  }

  private TextView headerTab (int text, boolean archive) {
    TextView t = new TextView(context);
    t.setText(Lang.getString(text));
    t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    t.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoMedium());
    t.setGravity(Gravity.CENTER);
    t.setPadding(Screen.dp(14f), 0, Screen.dp(14f), 0);
    t.setOnClickListener(v -> setArchive(archive));
    headerSwitch.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return t;
  }

  private void paintHeaderTabs () {
    if (tabPosts == null) return;
    boolean archive = getArgumentsStrict().archive;
    int headerColor = Theme.headerColor();
    for (TextView t : new TextView[] {tabPosts, tabArchive}) {
      boolean on = (t == tabArchive) == archive;
      GradientDrawable bg = new GradientDrawable();
      bg.setColor(on ? 0xffffffff : 0);
      bg.setCornerRadius(Screen.dp(15f));
      t.setBackground(bg);
      t.setTextColor(on ? headerColor : 0xccffffff);
    }
  }

  private void setArchive (boolean archive) {
    Args args = getArgumentsStrict();
    if (args.archive == archive) return;
    args.archive = archive;
    paintHeaderTabs();
    if (chipScroll != null) chipScroll.setVisibility(archive || (albums.isEmpty() && !isOwn()) ? View.GONE : View.VISIBLE);
    if (addButton != null) addButton.setVisibility(archive ? View.GONE : View.VISIBLE);
    albumId = 0;
    if (!archive) buildChips();
    reload();
  }

  // (user 2026-10-26 «свайп в публикациях должен переключать папки; назад — только на первой»)
  private void swipeAlbum (int dir) {
    if (getArgumentsStrict().archive || albums.isEmpty()) return;
    int index = 0;
    for (int i = 0; i < albums.size(); i++) if (albums.get(i).id == albumId) index = i + 1;
    int next = Math.max(0, Math.min(albums.size(), index + dir));
    if (next != index) selectAlbum(next == 0 ? 0 : albums.get(next - 1).id);
  }

  @Override
  public boolean canSlideBackFrom (org.thunderdog.challegram.navigation.NavigationController navigationController, float x, float y) {
    return getArgumentsStrict().archive || albumId == 0 || albums.isEmpty() || x <= Screen.dp(16f);
  }

  @Override
  protected View onCreateView (Context context) {
    FrameLayout root = new FrameLayout(context);
    org.thunderdog.challegram.support.ViewSupport.setThemedBackground(root, ColorId.background, this);
    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    HorizontalScrollView chipScroll = new HorizontalScrollView(context);
    this.chipScroll = chipScroll;
    chipScroll.setHorizontalScrollBarEnabled(false);
    chips = new LinearLayout(context);
    chips.setOrientation(LinearLayout.HORIZONTAL);
    chips.setPadding(Screen.dp(8f), Screen.dp(8f), Screen.dp(8f), Screen.dp(8f));
    chipScroll.addView(chips);
    chipScroll.setVisibility(View.GONE);
    column.addView(chipScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    RecyclerView list = new RecyclerView(context) {
      private float downX, downY;
      private boolean swiped;

      @Override
      public boolean onInterceptTouchEvent (android.view.MotionEvent e) {
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
          downX = e.getX();
          downY = e.getY();
          swiped = false;
        } else if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE && !swiped) {
          float dx = e.getX() - downX, dy = e.getY() - downY;
          if (Math.abs(dx) > Screen.dp(48f) && Math.abs(dx) > Math.abs(dy) * 2f && !(dx > 0 && albumId == 0)) {
            swiped = true;
            swipeAlbum(dx < 0 ? 1 : -1);
            return true;
          }
        }
        return super.onInterceptTouchEvent(e);
      }

      @Override
      public boolean onTouchEvent (android.view.MotionEvent e) {
        if (swiped) {
          if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) swiped = false;
          return true;
        }
        return super.onTouchEvent(e);
      }
    };
    listView = list;
    list.setLayoutManager(new GridLayoutManager(context, 3));
    int gap = Screen.dp(1.5f);
    list.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        outRect.set(gap, gap, gap, gap);
      }
    });
    adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
      @NonNull
      @Override
      public RecyclerView.ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
        Tile tile = new Tile(parent.getContext());
        tile.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerView.ViewHolder(tile) { };
      }

      @Override
      public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) {
        Tile tile = (Tile) holder.itemView;
        TdApi.Story story = stories.get(position);
        tile.bind(story, pinned.contains(story.id));
        loadThumb(tile, story);
        tile.setOnClickListener(v -> open(holder.getBindingAdapterPosition()));
        tile.setOnLongClickListener(v -> {
          storyOptions(holder.getBindingAdapterPosition());
          return true;
        });
        if (position > stories.size() - 7) loadMore();
      }

      @Override
      public int getItemCount () {
        return stories.size();
      }
    };
    list.setAdapter(adapter);
    list.setPadding(0, 0, 0, Screen.dp(72f) + MainController.tgx101CapsuleSpace());
    list.setClipToPadding(false);
    FrameLayout listWrap = new FrameLayout(context);
    listWrap.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    empty = new TextView(context);
    empty.setTextColor(Theme.textDecentColor());
    empty.setTextSize(15f);
    empty.setGravity(Gravity.CENTER);
    empty.setPadding(Screen.dp(24f), 0, Screen.dp(24f), 0);
    empty.setVisibility(View.GONE);
    listWrap.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    column.addView(listWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    if (isOwn()) {
      TextView add = new TextView(context);
      addButton = add;
      if (getArgumentsStrict().archive) add.setVisibility(View.GONE);
      add.setText(Lang.getString(R.string.Tgx101PostsAdd));
      add.setTextColor(0xffffffff);
      add.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      add.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoMedium());
      add.setGravity(Gravity.CENTER);
      add.setPadding(Screen.dp(22f), 0, Screen.dp(22f), 0);
      add.setCompoundDrawablePadding(Screen.dp(8f));
      android.graphics.drawable.Drawable cam = org.thunderdog.challegram.tool.Drawables.get(R.drawable.baseline_camera_alt_24);
      if (cam != null) {
        cam = cam.mutate();
        cam.setColorFilter(new android.graphics.PorterDuffColorFilter(0xffffffff, android.graphics.PorterDuff.Mode.SRC_IN));
        add.setCompoundDrawablesWithIntrinsicBounds(cam, null, null, null);
      }
      GradientDrawable bg = new GradientDrawable();
      bg.setColor(Theme.getColor(ColorId.fillingPositive));
      bg.setCornerRadius(Screen.dp(24f));
      add.setBackground(bg);
      add.setOnClickListener(v -> {
        Tgx101StoryEditor.tgx101TargetAlbumId = albumId;
        Tgx101StoryEditor.pickAndOpen((BaseActivity) context, tdlib);
      });
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(48f), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      lp.bottomMargin = Screen.dp(16f) + MainController.tgx101CapsuleSpace();
      root.addView(add, lp);
    }

    loadAlbums(chipScroll);
    reload();
    return root;
  }

  // Albums

  private void loadAlbums (View chipScroll) {
    tdlib.send(new TdApi.GetChatStoryAlbums(getArgumentsStrict().chatId), (result, error) -> runOnUiThreadOptional(() -> {
      albums.clear();
      if (result != null) java.util.Collections.addAll(albums, result.albums);
      chipScroll.setVisibility(getArgumentsStrict().archive || (albums.isEmpty() && !isOwn()) ? View.GONE : View.VISIBLE);
      buildChips();
    }));
  }

  private void buildChips () {
    chips.removeAllViews();
    addChip(Lang.getString(R.string.Tgx101PostsAll), albumId == 0, v -> selectAlbum(0));
    for (TdApi.StoryAlbum album : albums) {
      addChip(album.name, albumId == album.id, v -> selectAlbum(album.id)).setOnLongClickListener(v -> {
        if (isOwn()) albumOptions(album);
        return isOwn();
      });
    }
    if (isOwn()) addChip("+ " + Lang.getString(R.string.Tgx101PostsAddAlbum), false, v -> askAlbumName(null, null));
  }

  private TextView addChip (String text, boolean selected, View.OnClickListener onClick) {
    TextView chip = new TextView(context);
    chip.setText(text);
    chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    chip.setSingleLine(true);
    chip.setPadding(Screen.dp(14f), Screen.dp(7f), Screen.dp(14f), Screen.dp(7f));
    chip.setTextColor(selected ? Theme.textAccentColor() : Theme.textDecentColor());
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(selected ? ColorUtils.alphaColor(.12f, Theme.textAccentColor()) : 0);
    bg.setCornerRadius(Screen.dp(18f));
    chip.setBackground(bg);
    chip.setOnClickListener(onClick);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.rightMargin = Screen.dp(4f);
    chips.addView(chip, lp);
    return chip;
  }

  private int albumIndex (int id) {
    for (int i = 0; i < albums.size(); i++) if (albums.get(i).id == id) return i + 1;
    return 0;
  }

  private void selectAlbum (int id) {
    if (albumId == id) return;
    // (user 2026-10-26 «анимации влево при переключении папок нет»): the grid slides out to the side and the next one in
    int dir = albumIndex(id) > albumIndex(albumId) ? 1 : -1;
    albumId = id;
    buildChips();
    RecyclerView list = listView;
    if (list == null || list.getWidth() == 0) {
      reload();
      return;
    }
    float shift = list.getWidth() * .25f;
    list.animate().cancel();
    list.animate().translationX(-dir * shift).alpha(0f).setDuration(110).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(() -> {
      reload();
      list.setTranslationX(dir * shift);
      list.animate().translationX(0f).alpha(1f).setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator()).withEndAction(null);
    });
  }

  private void askAlbumName (@Nullable TdApi.StoryAlbum album, @Nullable int[] storyIds) {
    EditText input = new EditText(context);
    input.setSingleLine(true);
    input.setHint(Lang.getString(R.string.Tgx101PostsAlbumName));
    if (album != null) input.setText(album.name);
    FrameLayout box = new FrameLayout(context);
    box.setPadding(Screen.dp(20f), Screen.dp(8f), Screen.dp(20f), 0);
    box.addView(input);
    new android.app.AlertDialog.Builder(context)
      .setTitle(album != null ? R.string.Tgx101PostsRenameAlbum : R.string.Tgx101PostsAddAlbum)
      .setView(box)
      .setPositiveButton(R.string.Done, (d, w) -> {
        String name = input.getText().toString().trim();
        if (name.isEmpty()) return;
        long chatId = getArgumentsStrict().chatId;
        if (album != null) {
          tdlib.send(new TdApi.SetStoryAlbumName(chatId, album.id, name), (r, e) -> afterAlbumChange(e));
        } else {
          tdlib.send(new TdApi.CreateStoryAlbum(chatId, name, storyIds != null ? storyIds : new int[0]), (r, e) -> afterAlbumChange(e));
        }
      })
      .setNegativeButton(R.string.Cancel, null)
      .show();
  }

  private void afterAlbumChange (@Nullable TdApi.Error error) {
    runOnUiThreadOptional(() -> {
      if (error != null) UI.showError(error);
      View scroll = (View) chips.getParent();
      loadAlbums(scroll);
      reload();
    });
  }

  private void albumOptions (TdApi.StoryAlbum album) {
    showOptions(album.name, new int[] {R.id.btn_edit, R.id.btn_delete}, new String[] {Lang.getString(R.string.Tgx101PostsRenameAlbum), Lang.getString(R.string.Tgx101PostsDeleteAlbum)},
      new int[] {OptionColor.NORMAL, OptionColor.RED}, new int[] {R.drawable.baseline_edit_24, R.drawable.baseline_delete_24}, (v, id) -> {
        if (id == R.id.btn_edit) {
          askAlbumName(album, null);
        } else if (id == R.id.btn_delete) {
          if (albumId == album.id) albumId = 0;
          tdlib.send(new TdApi.DeleteStoryAlbum(getArgumentsStrict().chatId, album.id), (r, e) -> afterAlbumChange(e));
        }
        return true;
      });
  }

  // Stories

  private void reload () {
    generation++;
    stories.clear();
    pinned.clear();
    ended = false;
    loading = false;
    if (adapter != null) adapter.notifyDataSetChanged();
    if (empty != null) empty.setVisibility(View.GONE);
    loadMore();
  }

  private void loadMore () {
    if (loading || ended) return;
    loading = true;
    final int gen = generation;
    Args args = getArgumentsStrict();
    int from = stories.isEmpty() ? 0 : stories.get(stories.size() - 1).id;
    TdApi.Function<TdApi.Stories> f = args.archive ? new TdApi.GetChatArchivedStories(args.chatId, from, 30)
      : albumId != 0 ? new TdApi.GetStoryAlbumStories(args.chatId, albumId, stories.size(), 30)
      : new TdApi.GetChatPostedToChatPageStories(args.chatId, from, 30);
    tdlib.send(f, (result, error) -> runOnUiThreadOptional(() -> {
      if (gen != generation) return;
      loading = false;
      if (error != null || result == null || result.stories.length == 0) {
        ended = true;
        if (stories.isEmpty()) {
          empty.setText(Lang.getString(args.archive ? R.string.Tgx101PostsArchiveEmpty : R.string.Tgx101PostsEmpty));
          empty.setVisibility(View.VISIBLE);
        }
        return;
      }
      if (result.pinnedStoryIds != null) for (int id : result.pinnedStoryIds) pinned.add(id);
      int start = stories.size();
      for (TdApi.Story s : result.stories) stories.add(s);
      adapter.notifyItemRangeInserted(start, result.stories.length);
      if (stories.size() >= result.totalCount) ended = true;
    }));
  }

  private void open (int position) {
    if (position < 0 || position >= stories.size()) return;
    List<TdApi.StoryInfo> infos = new ArrayList<>();
    for (int i = position; i < stories.size(); i++) {
      TdApi.Story s = stories.get(i);
      infos.add(new TdApi.StoryInfo(s.id, s.date, false, false));
    }
    TdApi.ChatActiveStories person = new TdApi.ChatActiveStories(getArgumentsStrict().chatId, null, 0, false, 0, infos.toArray(new TdApi.StoryInfo[0]));
    List<TdApi.ChatActiveStories> people = new ArrayList<>();
    people.add(person);
    new Tgx101StoryViewer(context, tdlib, people, 0, true).show();
  }

  private void storyOptions (int position) {
    if (!isOwn() || getArgumentsStrict().archive || position < 0 || position >= stories.size()) return;
    TdApi.Story story = stories.get(position);
    long chatId = getArgumentsStrict().chatId;
    List<Integer> ids = new ArrayList<>();
    List<String> names = new ArrayList<>();
    List<Integer> icons = new ArrayList<>();
    boolean isPinned = pinned.contains(story.id);
    ids.add(R.id.btn_pinUnpin); names.add(Lang.getString(isPinned ? R.string.Tgx101PostsUnpin : R.string.Tgx101PostsPin)); icons.add(R.drawable.deproko_baseline_pin_24);
    ids.add(R.id.btn_addToFolder); names.add(Lang.getString(R.string.Tgx101PostsToAlbum)); icons.add(R.drawable.baseline_folder_24);
    if (albumId != 0) {
      ids.add(R.id.btn_removeFromFolder); names.add(Lang.getString(R.string.Tgx101PostsFromAlbum)); icons.add(R.drawable.baseline_remove_circle_24);
    }
    int[] idArr = new int[ids.size()], iconArr = new int[ids.size()];
    for (int i = 0; i < idArr.length; i++) { idArr[i] = ids.get(i); iconArr[i] = icons.get(i); }
    showOptions(null, idArr, names.toArray(new String[0]), null, iconArr, (v, id) -> {
      if (id == R.id.btn_pinUnpin) {
        List<Integer> now = new ArrayList<>(pinned);
        if (isPinned) now.remove(Integer.valueOf(story.id)); else now.add(0, story.id);
        int[] arr = new int[now.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = now.get(i);
        tdlib.send(new TdApi.SetChatPinnedStories(chatId, arr), (r, e) -> afterAlbumChange(e));
      } else if (id == R.id.btn_addToFolder) {
        chooseAlbum(story);
      } else if (id == R.id.btn_removeFromFolder) {
        tdlib.send(new TdApi.RemoveStoryAlbumStories(chatId, albumId, new int[] {story.id}), (r, e) -> afterAlbumChange(e));
      }
      return true;
    });
  }

  private void chooseAlbum (TdApi.Story story) {
    int n = albums.size() + 1;
    int[] ids = new int[n], icons = new int[n];
    String[] names = new String[n];
    for (int i = 0; i < albums.size(); i++) {
      ids[i] = i + 1;
      names[i] = albums.get(i).name;
      icons[i] = R.drawable.baseline_folder_24;
    }
    ids[n - 1] = -1;
    names[n - 1] = "+ " + Lang.getString(R.string.Tgx101PostsAddAlbum);
    icons[n - 1] = R.drawable.baseline_add_24;
    showOptions(Lang.getString(R.string.Tgx101PostsToAlbum), ids, names, null, icons, (v, id) -> {
      if (id == -1) {
        askAlbumName(null, new int[] {story.id});
      } else {
        TdApi.StoryAlbum album = albums.get(id - 1);
        tdlib.send(new TdApi.AddStoryAlbumStories(getArgumentsStrict().chatId, album.id, new int[] {story.id}), (r, e) -> runOnUiThreadOptional(() -> {
          if (e != null) UI.showError(e);
          else UI.showToast(Lang.getString(R.string.Tgx101PostsAdded, album.name), android.widget.Toast.LENGTH_SHORT);
        }));
      }
      return true;
    });
  }

  // Tiles

  private static final class Tile extends ImageView {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();
    private String duration, views;
    private boolean isPinned;
    int storyId;

    Tile (Context context) {
      super(context);
      setScaleType(ScaleType.CENTER_CROP);
      setBackgroundColor(Theme.getColor(ColorId.placeholder));
      text.setColor(0xffffffff);
      text.setTextSize(Screen.dp(12.5f));
      text.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoMedium());
      shade.setColor(0x66000000);
    }

    void bind (TdApi.Story story, boolean pinned) {
      storyId = story.id;
      setImageBitmap(null);
      isPinned = pinned;
      duration = story.content instanceof TdApi.StoryContentVideo && ((TdApi.StoryContentVideo) story.content).video != null
        ? Strings.buildDuration(Math.round(((TdApi.StoryContentVideo) story.content).video.duration)) : null;
      views = story.interactionInfo != null && story.interactionInfo.viewCount > 0 ? Strings.buildCounter(story.interactionInfo.viewCount) : null;
      invalidate();
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, widthMeasureSpec);
      setMeasuredDimension(getMeasuredWidth(), Math.round(getMeasuredWidth() * 1.25f));
    }

    @Override
    protected void onDraw (Canvas c) {
      super.onDraw(c);
      float pad = Screen.dp(6f), h = getHeight(), w = getWidth(), base = h - pad - Screen.dp(4f);
      if (duration != null) drawPill(c, "▶ " + duration, pad, base, false);
      if (views != null) drawPill(c, "◉ " + views, w - pad, base, true);
      if (isPinned) {
        pill.set(w - Screen.dp(26f), Screen.dp(4f), w - Screen.dp(4f), Screen.dp(26f));
        c.drawRoundRect(pill, Screen.dp(11f), Screen.dp(11f), shade);
        android.graphics.drawable.Drawable pin = org.thunderdog.challegram.tool.Drawables.get(R.drawable.deproko_baseline_pin_24);
        if (pin != null) {
          pin.setColorFilter(new android.graphics.PorterDuffColorFilter(0xffffffff, android.graphics.PorterDuff.Mode.SRC_IN));
          int s = Screen.dp(16f), cx = (int) pill.centerX(), cy = (int) pill.centerY();
          pin.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
          pin.draw(c);
        }
      }
    }

    private void drawPill (Canvas c, String s, float x, float baseline, boolean alignRight) {
      float tw = text.measureText(s), padX = Screen.dp(6f);
      float left = alignRight ? x - tw - padX * 2 : x;
      pill.set(left, baseline - Screen.dp(14f), left + tw + padX * 2, baseline + Screen.dp(5f));
      c.drawRoundRect(pill, Screen.dp(9f), Screen.dp(9f), shade);
      c.drawText(s, left + padX, baseline, text);
    }
  }

  private static @Nullable TdApi.File thumbFile (TdApi.Story story) {
    TdApi.StoryContent content = story.content;
    if (content instanceof TdApi.StoryContentPhoto) {
      TdApi.Photo photo = ((TdApi.StoryContentPhoto) content).photo;
      if (photo == null || photo.sizes == null || photo.sizes.length == 0) return null;
      TdApi.PhotoSize best = null;
      for (TdApi.PhotoSize size : photo.sizes) {
        if (best == null || (Math.min(size.width, size.height) >= 320 && size.width * size.height < best.width * best.height) || Math.min(best.width, best.height) < 320 && size.width > best.width) best = size;
      }
      return best != null ? best.photo : null;
    }
    if (content instanceof TdApi.StoryContentVideo) {
      TdApi.StoryVideo video = ((TdApi.StoryContentVideo) content).video;
      return video != null && video.thumbnail != null ? video.thumbnail.file : null;
    }
    return null;
  }

  private void loadThumb (Tile tile, TdApi.Story story) {
    TdApi.Minithumbnail mini = Tgx101Stories.minithumbnail(story.content);
    if (mini != null && mini.data != null) {
      tile.setImageBitmap(BitmapFactory.decodeByteArray(mini.data, 0, mini.data.length));
    }
    TdApi.File file = thumbFile(story);
    if (file == null) return;
    final int storyId = story.id;
    tdlib.send(new TdApi.DownloadFile(file.id, 16, 0, 0, true), (f, error) -> {
      if (f == null || !f.local.isDownloadingCompleted) return;
      BitmapFactory.Options o = new BitmapFactory.Options();
      o.inSampleSize = 2;
      Bitmap b = BitmapFactory.decodeFile(f.local.path, o);
      if (b == null) return;
      UI.post(() -> {
        if (tile.storyId == storyId) tile.setImageBitmap(b);
      });
    });
  }
}
