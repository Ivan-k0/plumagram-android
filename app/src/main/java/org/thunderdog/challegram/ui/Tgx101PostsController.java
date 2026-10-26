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
    final boolean archive;

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
  private LinearLayout chips;
  private TextView empty;
  private boolean loading, ended;
  private int generation;

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

  @Override
  protected View onCreateView (Context context) {
    FrameLayout root = new FrameLayout(context);
    org.thunderdog.challegram.support.ViewSupport.setThemedBackground(root, ColorId.background, this);
    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    HorizontalScrollView chipScroll = new HorizontalScrollView(context);
    chipScroll.setHorizontalScrollBarEnabled(false);
    chips = new LinearLayout(context);
    chips.setOrientation(LinearLayout.HORIZONTAL);
    chips.setPadding(Screen.dp(8f), Screen.dp(8f), Screen.dp(8f), Screen.dp(8f));
    chipScroll.addView(chips);
    chipScroll.setVisibility(View.GONE);
    column.addView(chipScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    RecyclerView list = new RecyclerView(context);
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

    if (isOwn() && !getArgumentsStrict().archive) {
      TextView add = new TextView(context);
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

    if (!getArgumentsStrict().archive) loadAlbums(chipScroll);
    reload();
    return root;
  }

  // Albums

  private void loadAlbums (View chipScroll) {
    tdlib.send(new TdApi.GetChatStoryAlbums(getArgumentsStrict().chatId), (result, error) -> runOnUiThreadOptional(() -> {
      albums.clear();
      if (result != null) java.util.Collections.addAll(albums, result.albums);
      chipScroll.setVisibility(albums.isEmpty() && !isOwn() ? View.GONE : View.VISIBLE);
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

  private void selectAlbum (int id) {
    if (albumId == id) return;
    albumId = id;
    buildChips();
    reload();
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
