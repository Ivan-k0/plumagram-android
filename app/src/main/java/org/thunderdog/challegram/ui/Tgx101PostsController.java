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
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101 (user 2026-10-26 «в профиле нет публикаций»; screenshot of the official app: «Публикации», «Архив публикаций»):
 * the stories posted to a profile (or the own archive) as a grid; a tap opens them in our story viewer from that one on
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
  private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;
  private TextView empty;
  private boolean loading, ended;

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
    RecyclerView list = new RecyclerView(context);
    GridLayoutManager grid = new GridLayoutManager(context, 3);
    list.setLayoutManager(grid);
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
        ImageView iv = new ImageView(parent.getContext()) {
          @Override
          protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
            setMeasuredDimension(getMeasuredWidth(), Math.round(getMeasuredWidth() * 16f / 9f * .75f));
          }
        };
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(Theme.getColor(ColorId.placeholder));
        iv.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerView.ViewHolder(iv) { };
      }

      @Override
      public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) {
        ImageView iv = (ImageView) holder.itemView;
        TdApi.Story story = stories.get(position);
        iv.setImageBitmap(null);
        iv.setTag(story.id);
        loadThumb(iv, story);
        iv.setOnClickListener(v -> open(holder.getBindingAdapterPosition()));
        if (position > stories.size() - 7) loadMore();
      }

      @Override
      public int getItemCount () {
        return stories.size();
      }
    };
    list.setAdapter(adapter);
    list.setPadding(0, 0, 0, MainController.tgx101CapsuleSpace());
    list.setClipToPadding(false);
    root.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    empty = new TextView(context);
    empty.setTextColor(Theme.textDecentColor());
    empty.setTextSize(15f);
    empty.setGravity(android.view.Gravity.CENTER);
    empty.setPadding(Screen.dp(24f), 0, Screen.dp(24f), 0);
    empty.setVisibility(View.GONE);
    root.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    loadMore();
    return root;
  }

  private void loadMore () {
    if (loading || ended) return;
    loading = true;
    Args args = getArgumentsStrict();
    int from = stories.isEmpty() ? 0 : stories.get(stories.size() - 1).id;
    TdApi.Function<TdApi.Stories> f = args.archive ? new TdApi.GetChatArchivedStories(args.chatId, from, 30) : new TdApi.GetChatPostedToChatPageStories(args.chatId, from, 30);
    tdlib.send(f, (result, error) -> runOnUiThreadOptional(() -> {
      loading = false;
      if (error != null || result == null || result.stories.length == 0) {
        ended = true;
        if (stories.isEmpty()) {
          empty.setText(Lang.getString(args.archive ? R.string.Tgx101PostsArchiveEmpty : R.string.Tgx101PostsEmpty));
          empty.setVisibility(View.VISIBLE);
        }
        return;
      }
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

  private void loadThumb (ImageView iv, TdApi.Story story) {
    TdApi.Minithumbnail mini = Tgx101Stories.minithumbnail(story.content);
    if (mini != null && mini.data != null) {
      iv.setImageBitmap(BitmapFactory.decodeByteArray(mini.data, 0, mini.data.length));
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
        if (Integer.valueOf(storyId).equals(iv.getTag())) iv.setImageBitmap(b);
      });
    });
  }
}
