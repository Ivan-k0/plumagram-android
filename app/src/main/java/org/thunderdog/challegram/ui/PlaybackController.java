/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 14/01/2018
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.MediaPreview;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageApicFile;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.ProgressComponent;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.SparseDrawableView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.InvalidateContentProvider;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;

public class PlaybackController extends ViewController<Void> implements Menu, MoreDelegate, View.OnClickListener, TGPlayerController.TrackListChangeListener, TGPlayerController.TrackListener, TdlibFilesManager.FileListener, TGPlayerController.PlayListBuilder, TGLegacyManager.EmojiLoadListener {
  public PlaybackController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return "";
  }

  @Override
  public int getId () {
    return R.id.controller_playback;
  }

  private int preparedTrackIndex;

  private int buildList (List<ListItem> out, Tdlib tdlib, TdApi.Message currentTrack, List<TdApi.Message> trackList, long playListChatId, int playFlags) {
    final boolean reverseOrder = (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;
    int remaining = trackList.size();
    long totalDuration = 0l;
    int trackIndex = -1;
    while (--remaining >= 0) {
      TdApi.Message track = trackList.get(reverseOrder ? remaining : trackList.size() - 1 - remaining);
      InlineResult<?> result = InlineResult.valueOf(context, tdlib, track);
      if (!(result instanceof InlineResultCommon) || result.getType() != InlineResult.TYPE_AUDIO) {
        out.clear();
        return -1;
      }
      if (TGPlayerController.compareTracks(currentTrack, track)) {
        if (trackIndex != -1) {
          throw new IllegalStateException();
        }
        trackIndex = out.size() - 1;
      }
      InlineResultCommon common = (InlineResultCommon) result;
      out.add(valueOf(common));
      totalDuration += common.getTrackAudio().duration;
    }
    if (trackIndex == -1) {
      throw new IllegalStateException();
    }

    this.preparedTrackIndex = trackIndex;
    this.totalTracksDuration = totalDuration;
    this.playListChatId = playListChatId;
    setPlayFlagsImpl(playFlags);

    return trackIndex;
  }

  public int prepare () {
    List<TdApi.Message> trackList = tdlib.context().player().getTrackList();
    if (trackList == null || trackList.isEmpty()) {
      return -1;
    }
    TdApi.Message currentTrack = tdlib.context().player().getCurrentTrack();
    if (currentTrack == null) {
      return -1;
    }
    long playListChatId = tdlib.context().player().getPlayListChatId();
    int playFlags = tdlib.context().player().getPlaybackSessionFlags();
    ArrayList<ListItem> tracks = new ArrayList<>(trackList.size() + 2);
    tracks.add(new ListItem(ListItem.TYPE_ZERO_VIEW));
    int trackIndex = buildList(tracks, tdlib, currentTrack, trackList, playListChatId, playFlags);
    if (trackIndex == -1) {
      return -1;
    }
    tracks.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW, R.id.btn_info));

    this.adapter = new SettingsAdapter(this) {
      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        if (tdlib.context().player().isTrackListEndReached()) {
          infoView.showInfo(Lang.getCharSequence(R.string.format_tracksAndDuration, Lang.pluralBold(R.string.xAudios, getTrackCount()), Strings.buildDuration(totalTracksDuration)));
        } else {
          infoView.showProgress();
        }
      }
    };
    this.adapter.setItems(tracks, false);
    this.tracks = adapter.getItems();

    return trackIndex;
  }

  private SettingsAdapter adapter;
  private List<ListItem> tracks;
  private long totalTracksDuration;

  private PlayListRecyclerView recyclerView;
  private PlayOverlayLayout overlayLayout;
  private CoverView coverView;
  private ShadowView shadowView;

  private ImageView shuffleButton, repeatButton, nextButton, prevButton;
  private PlayPauseButton playPauseButton;

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
    Views.applyBottomInset(recyclerView, extraBottomInset);
  }

  private ImageView newButton (int id, int image, boolean isActive) {
    ImageView imageView = new ImageView(context());
    imageView.setId(id);
    imageView.setScaleType(ImageView.ScaleType.CENTER);
    imageView.setImageResource(image);
    imageView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(64f), Screen.dp(48f)));
    int colorId = isActive ? ColorId.playerButtonActive : ColorId.playerButton;
    imageView.setColorFilter(Theme.getColor(colorId));
    imageView.setOnClickListener(this);
    addThemeFilterListener(imageView, colorId);
    return imageView;
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    if (coverView != null) {
      coverView.invalidate();
    }
  }

  @Override
  protected View onCreateView (Context context) {
    if (adapter == null) {
      throw new IllegalStateException("Using PlaybackController without calling prepare()");
    }

    FrameLayoutFix frameLayout = new FrameLayoutFix(context);
    frameLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    ViewSupport.setThemedBackground(frameLayout, ColorId.filling, this);

    recyclerView = new PlayListRecyclerView(context);
    Views.applyBottomInset(recyclerView, extraBottomInset);
    recyclerView.initWithController(this);
    recyclerView.setVerticalScrollBarEnabled(false);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    recyclerView.addItemDecoration(new OffsetDecoration(this));
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    recyclerView.setAdapter(adapter);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
    recyclerView.setAlpha(isTrackListLess() ? 0f : 1f);
    frameLayout.addView(recyclerView);

    final ItemTouchHelper[] helper = new ItemTouchHelper[1];
    helper[0] = new ItemTouchHelper(new ItemTouchHelper.Callback() {
      @Override
      public int getMovementFlags (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        if (isTrackListLess()) {
          return 0;
        }
        int position = viewHolder.getBindingAdapterPosition();
        int headerItemCount = 1;
        if (position == -1 || position < headerItemCount || viewHolder.getItemViewType() != ListItem.TYPE_CUSTOM_INLINE) {
          return 0;
        }
        return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT);
      }

      @Override
      public boolean isItemViewSwipeEnabled () {
        return true;
      }

      @Override
      public boolean isLongPressDragEnabled () {
        return true;
      }

      @Override
      public void onMoved (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int fromPos, RecyclerView.ViewHolder target, int toPos, int x, int y) {
        super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
        viewHolder.itemView.invalidate();
        target.itemView.invalidate();
      }

      /*@Override
      public float getSwipeThreshold (RecyclerView.ViewHolder viewHolder) {
        return (float) Screen.dp(RemoveHelper.SWIPE_THRESHOLD_WIDTH) / (float) viewHolder.itemView.getMeasuredWidth();
      }*/

      @Override
      public void onChildDraw (Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
          super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
        if (viewHolder.itemView instanceof RemoveHelper.RemoveDelegate) {
          RemoveHelper.RemoveDelegate removeView = (RemoveHelper.RemoveDelegate) viewHolder.itemView;
          removeView.setRemoveDx(dX);
        }
      }

      @Override
      public boolean onMove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        int fromPosition = viewHolder.getBindingAdapterPosition();
        int toPosition = target.getBindingAdapterPosition();

        int headerItemCount = 1;
        int trackCount = getTrackCount();
        if (fromPosition >= headerItemCount && fromPosition < headerItemCount + trackCount && toPosition >= headerItemCount && toPosition < headerItemCount + trackCount) {
          fromPosition -= headerItemCount;
          toPosition -= headerItemCount;
          if ((playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0) {
            fromPosition = trackCount - fromPosition - 1;
            toPosition = trackCount - toPosition - 1;
          }
          moveTrack(fromPosition, toPosition, true);
          return true;
        }

        return false;
      }

      @Override
      public void onSwiped (RecyclerView.ViewHolder viewHolder, int direction) {
        if ((direction & (ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT)) != 0) {
          helper[0].onChildViewDetachedFromWindow(viewHolder.itemView);
        }
        if (direction == ItemTouchHelper.LEFT) {
          RemoveHelper.RemoveDelegate removeView = (RemoveHelper.RemoveDelegate) viewHolder.itemView;
          removeView.onRemoveSwipe();
          InlineResultCommon result = (InlineResultCommon) ((ListItem) viewHolder.itemView.getTag()).getData();
          removeTrack(result);
        }
      }
    });
    helper[0].attachToRecyclerView(recyclerView);

    overlayLayout = new PlayOverlayLayout(context);
    overlayLayout.initWithController(this);
    overlayLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    frameLayout.addView(overlayLayout);

    coverView = new CoverView(context);
    coverView.initWithController(this);
    coverView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    overlayLayout.addView(coverView);
    addThemeInvalidateListener(coverView);

    shadowView = new ShadowView(context);
    shadowView.setSimpleBottomTransparentShadow(true);
    shadowView.setAlpha(0f);
    shadowView.setLayoutParams(FrameLayoutFix.newParams(shadowView.getLayoutParams()));
    frameLayout.addView(shadowView);
    addThemeInvalidateListener(shadowView);

    shuffleButton = newButton(R.id.btn_shuffle, R.drawable.round_shuffle_24, isShuffleActive());
    nextButton = newButton(R.id.btn_next, R.drawable.round_skip_next_36, false);
    prevButton = newButton(R.id.btn_previous, R.drawable.round_skip_previous_36, false);
    repeatButton = newButton(R.id.btn_repeat, getRepeatIcon(), isRepeatActive());
    tgx101Heart = newButton(R.id.btn_tgx101SaveFavorite, R.drawable.tgx101_heart_outline_24, false);
    tgx101Heart.setContentDescription(Lang.getString(R.string.Tgx101PlayerSave));
    tgx101Heart.setOnClickListener(v -> tgx101ToggleSaved());
    tgx101AttachFolderMenu(tgx101Heart);
    tgx101Dislike = newButton(R.id.btn_tgx101Dislike, R.drawable.tgx101_thumb_down_outline_24, false);
    tgx101Dislike.setContentDescription(Lang.getString(R.string.Tgx101Dislike));
    tgx101Dislike.setOnClickListener(v -> tgx101ToggleDislike());
    tgx101AttachDislikeMenu(tgx101Dislike);
    org.thunderdog.challegram.player.Tgx101MusicTaste.addListener(tgx101TasteListener);
    tgx101HoldToSeek(nextButton, 1);
    tgx101HoldToSeek(prevButton, -1);
    playPauseButton = new PlayPauseButton(context);
    playPauseButton.setId(R.id.btn_play);
    playPauseButton.setOnClickListener(this);
    playPauseButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(64f), Screen.dp(64f)));
    addThemeInvalidateListener(playPauseButton);

    frameLayout.addView(shuffleButton);
    frameLayout.addView(repeatButton);
    frameLayout.addView(prevButton);
    frameLayout.addView(nextButton);
    frameLayout.addView(playPauseButton);

    frameLayout.addView(tgx101Heart);
    frameLayout.addView(tgx101Dislike);

    tgx101SpeedPill = new Tgx101Pill(context);
    tgx101SpeedPill.setTint(tgx101Tint);
    tgx101SpeedPill.setController(this);
    FrameLayoutFix.LayoutParams speedParams = FrameLayoutFix.newParams(Screen.dp(64f), Screen.dp(40f));
    speedParams.leftMargin = Screen.dp(16f);
    tgx101SpeedPill.setLayoutParams(speedParams);
    tgx101SpeedPill.setContentDescription(Lang.getString(R.string.PlaybackSpeed));
    tgx101SpeedPill.setOnClickListener(v -> tgx101ShowSpeedSheet());
    addThemeInvalidateListener(tgx101SpeedPill);
    frameLayout.addView(tgx101SpeedPill);
    tgx101LyricsPill = new Tgx101Pill(context);
    tgx101LyricsPill.setTint(tgx101Tint);
    tgx101LyricsPill.setController(this);
    tgx101LyricsPill.setIcon(R.drawable.baseline_format_text_24);
    FrameLayoutFix.LayoutParams lyricsParams = FrameLayoutFix.newParams(Screen.dp(48f), Screen.dp(40f));
    lyricsParams.leftMargin = Screen.dp(16f + 64f + 8f);
    tgx101LyricsPill.setLayoutParams(lyricsParams);
    tgx101LyricsPill.setContentDescription(Lang.getString(R.string.Tgx101Lyrics));
    tgx101LyricsPill.setOnClickListener(v -> tgx101ShowLyrics());
    addThemeInvalidateListener(tgx101LyricsPill);
    frameLayout.addView(tgx101LyricsPill);
    tgx101NextPill = new Tgx101Pill(context);
    tgx101NextPill.setTint(tgx101Tint);
    tgx101NextPill.setController(this);
    tgx101NextPill.setIcon(R.drawable.baseline_queue_music_24);
    FrameLayoutFix.LayoutParams nextParams = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(40f));
    nextParams.leftMargin = Screen.dp(16f + 64f + 8f + 48f + 8f);
    nextParams.rightMargin = Screen.dp(16f);
    tgx101NextPill.setLayoutParams(nextParams);
    tgx101NextPill.setOnClickListener(v -> {
      if (indexOfCurrentTrack() != -1) highlightCurrentItem();
    });
    tgx101NextPill.setOnLongClickListener(v -> {
      tgx101ShowQueueSearch();
      return true;
    });
    addThemeInvalidateListener(tgx101NextPill);
    frameLayout.addView(tgx101NextPill);
    tgx101UpdateSpeedPill(tdlib.context().player().getSpeed());
    tdlib.context().player().addTrackChangeListener(tgx101SpeedListener);

    tdlib.context().player().addTrackListChangeListener(this, false);
    setItem((InlineResultCommon) tracks.get(preparedTrackIndex + 1).getData(), true);
    TGLegacyManager.instance().addEmojiListener(this);
    TGLegacyManager.instance().addEmojiListener(adapter);

    final boolean isPlaying = isPlaying();
    playPauseButton.setIsPlaying(isPlaying, false);
    coverView.tgx101SetWaving(isPlaying, false);
    currentItem.setIsTrackPlaying(isPlaying);

    return frameLayout;
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.context().player().removeTrackListChangeListener(this);
    tdlib.context().player().removeTrackChangeListener(tgx101SpeedListener);
    org.thunderdog.challegram.player.Tgx101MusicTaste.removeListener(tgx101TasteListener);
    TGLegacyManager.instance().removeEmojiListener(this);
    TGLegacyManager.instance().removeEmojiListener(adapter);
    Views.destroyRecyclerView(recyclerView);
    coverView.setItem(null);
  }

  // Handler

  private static class PlaybackHandler extends Handler {
    private final PlaybackController context;

    public PlaybackHandler (PlaybackController context) {
      super(Looper.getMainLooper());
      this.context = context;
    }

    @Override
    public void handleMessage (Message msg) {
      context.processMessage(msg);
    }
  }

  private PlaybackHandler handler;
  private PlaybackHandler getHandler () {
    if (handler == null) {
      synchronized (this) {
        if (handler == null) {
          handler = new PlaybackHandler(this);
        }
      }
    }
    return handler;
  }

  private static final int ACTION_DISPATCH_PROGRESS = 0;
  private static final int ACTION_SET_COVER_DURATIONS = 1;
  private static final int ACTION_CHECK_SCROLLING = 2;
  private static final int ACTION_ENSURE_POSITION = 3;

  private void processMessage (Message msg) {
    switch (msg.what) {
      case ACTION_DISPATCH_PROGRESS: {
        setFileProgress((TdApi.File) msg.obj);
        break;
      }
      case ACTION_SET_COVER_DURATIONS: {
        coverView.setDurationTextsDelayed();
        break;
      }
      case ACTION_CHECK_SCROLLING: {
        checkScrolling(msg.arg1 == 1);
        break;
      }
      case ACTION_ENSURE_POSITION: {
        ensureScroll(msg.arg1 == 1);
        break;
      }
    }
  }

  // track list restoration

  private boolean ignoreNextReset;

  private boolean canRestoreList;
  private long restorePlayListChatId, restorePlayListMaxMessageId, restorePlayListMinMessageId;
  private boolean restoreNewEndReached, restoreOldEndReached;
  private List<TdApi.Message> restoreRemovedMessages;

  private void savePlayListInformation (long playListChatId, long playListMaxMessageId, long playListMinMessageId, boolean newEndReached, boolean oldEndReached, List<TdApi.Message> removedMessages) {
    if (canRestoreList) {
      throw new IllegalStateException();
    }
    this.canRestoreList = true;
    this.restorePlayListChatId = playListChatId;
    this.restorePlayListMaxMessageId = playListMaxMessageId;
    this.restorePlayListMinMessageId = playListMinMessageId;
    this.restoreNewEndReached = newEndReached;
    this.restoreOldEndReached = oldEndReached;
    if (removedMessages != null && !removedMessages.isEmpty()) {
      this.restoreRemovedMessages = new ArrayList<>(removedMessages.size());
      this.restoreRemovedMessages.addAll(removedMessages);
    } else {
      this.restoreRemovedMessages = null;
    }
  }

  @Nullable
  @Override
  public TGPlayerController.PlayList buildPlayList (TdApi.Message fromMessage) {
    if (!canRestoreList) {
      throw new IllegalStateException();
    }
    int trackListCount = getTrackCount();
    if (trackListCount <= 0) {
      throw new IllegalStateException();
    }
    ArrayList<TdApi.Message> trackList = new ArrayList<>(trackListCount);
    int remaining = trackListCount;
    boolean reverseOrder = (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;
    int foundIndex = -1;
    while (--remaining >= 0) {
      ListItem item = tracks.get(reverseOrder ? remaining + 1 : trackListCount - remaining);
      TdApi.Message message = ((InlineResultCommon) item.getData()).getMessage();
      if (fromMessage == message || TGPlayerController.compareTracks(fromMessage, message)) {
        if (foundIndex != -1) {
          throw new IllegalStateException();
        }
        foundIndex = trackList.size();
      }
      trackList.add(message);
    }
    ignoreNextReset = true;
    TGPlayerController.PlayList playList = new TGPlayerController.PlayList(trackList, foundIndex);
    playList.setPlayListFlags(playFlags & TGPlayerController.PLAYLIST_FLAGS_MASK);
    playList.setPlayListSource(restorePlayListChatId, restorePlayListMaxMessageId, restorePlayListMinMessageId);
    playList.setRemovedMessages(restoreRemovedMessages);
    playList.setReachedEnds(restoreNewEndReached, restoreOldEndReached);
    canRestoreList = false;
    return playList;
  }

  @Override
  public boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId) {
    return true;
  }

  // item

  private static final int MINIMUM_TRACKS_COUNT = 1;

  private int indexOfCurrentTrack () {
    if (currentItem == null) {
      return -1;
    }
    final int size = tracks.size();
    TdApi.Message currentMessage = currentItem.getMessage();
    for (int i = 1; i < size - 1; i++) {
      ListItem item = tracks.get(i);
      if (item.getData() == currentItem || TGPlayerController.compareTracks(currentMessage, ((InlineResult<?>) item.getData()).getMessage())) {
        return i - 1;
      }
    }
    return -1;
  }

  private int getTrackCount () {
    return tracks.size() - 2; // top view + bottom progress
  }

  private boolean isTrackListLess () {
    return getTrackCount() <= MINIMUM_TRACKS_COUNT;
  }

  private void onTrackListSizeChanged () {
    final int count = getTrackCount();
    recyclerView.setAlpha(count <= MINIMUM_TRACKS_COUNT ? 0f : 1f);
    adapter.updateValuedSettingById(R.id.btn_info);
  }

  private void addDuration (long duration) {
    if (duration != 0) {
      totalTracksDuration += duration;
    }
    onTrackListSizeChanged();
  }

  private void highlightCurrentItem () {
    highlightItem(indexOfCurrentTrack(), true);
  }

  private boolean isScrollUnlocked = true; // TODO ?
  // private float unlockFactor;

  private void dispatchEnsureScroll (boolean animated) {
    getHandler().sendMessage(Message.obtain(getHandler(), ACTION_ENSURE_POSITION, animated ? 1 : 0, 0));
  }

  private void unlockScroll () {
    if (!isScrollUnlocked) {
      isScrollUnlocked = true;
      /*if (scrollFactor == 0f || !mayExpandOverlay()) {
        setUnlockFactor(1f);
        return;
      }
      FactorAnimator animator = new FactorAnimator(0, new FactorAnimator.Target() {
        @Override
        public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
          setUnlockFactor(factor);
        }
        @Override
        public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }
      }, Anim.DECELERATE_INTERPOLATOR, 180l);
      animator.animateTo(1f);*/
    }
  }

  private void ensureScroll (boolean animated) {
    if (isScrollUnlocked) {
      return;
    }
    final int viewWidth = recyclerView.getMeasuredWidth();
    final int viewHeight = recyclerView.getMeasuredHeight();
    if (Math.min(viewWidth, viewHeight) <= 0) {
      return;
    }

    int position = indexOfCurrentTrack();
    if (position == -1) {
      throw new IllegalStateException();
    }

    final int overlayHeight = getAvailableOverlayHeight();
    final int parentHeight = recyclerView.getMeasuredHeight();
    final int itemHeight = Screen.dp(65f);
    final int totalItemCount = getTrackCount();
    final int minimumOverlayHeight = getMinimumOverlayHeight();

    final int headerItemCount = 1;

    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();

    int desiredPosition = headerItemCount + position;
    int desiredOffset = (position != 0 ? itemHeight : 0);

    int scrollY = itemHeight * position;
    int maxScrollY = itemHeight * totalItemCount - (parentHeight - overlayHeight - itemHeight);
    if (scrollY > maxScrollY) {
      desiredOffset += scrollY - maxScrollY; // maxScrollY - scrollY;
    }

    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    View firstView = firstVisiblePosition != -1 ? manager.findViewByPosition(firstVisiblePosition) : null;
    int offsetTop = firstView != null ? manager.getDecoratedTop(firstView) : 0;

    if (!animated || firstView == null) {
      manager.scrollToPositionWithOffset(desiredPosition, overlayHeight + desiredOffset);
      return;
    }

    int currentScrollY = -offsetTop + itemHeight * Math.max(0, firstVisiblePosition - 1) + (firstVisiblePosition > 0 ? overlayHeight : 0);
    int desiredScrollY = itemHeight * Math.max(0, desiredPosition - 1) - desiredOffset;

    recyclerView.smoothScrollBy(0,  desiredScrollY - currentScrollY);
  }

  /*private void setUnlockFactor (float factor) {
    if (this.unlockFactor != factor) {
      float oldScrollFactor = getScrollFactor();
      this.unlockFactor = factor;
      float newScrollFactor = getScrollFactor();
      onScrollFactorChanged(oldScrollFactor, newScrollFactor);
    }
  }*/

  private void highlightItem (int position, boolean highlight) {
    if (position == -1) {
      return;
    }

    final int overlayHeight = getAvailableOverlayHeight();
    final int parentHeight = recyclerView.getMeasuredHeight();
    final int itemHeight = Screen.dp(65f);
    final int totalItemCount = getTrackCount();
    final int minimumOverlayHeight = getMinimumOverlayHeight();

    final int maxScrollY = overlayHeight + itemHeight * totalItemCount - parentHeight + SettingHolder.measureHeightForType(ListItem.TYPE_LIST_INFO_VIEW);

    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();

    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    if (firstVisiblePosition == -1) {
      return;
    }

    recyclerView.stopScroll();

    View firstView = manager.findViewByPosition(firstVisiblePosition);
    int offset = firstView != null ? manager.getDecoratedTop(firstView) : 0;

    int currentScrollY;
    if (firstVisiblePosition == 0) {
      currentScrollY = -offset;
    } else {
      currentScrollY = -offset + overlayHeight + (firstVisiblePosition - 1) * itemHeight;
    }
    int desiredScrollY = Math.max(0, Math.min(maxScrollY, overlayHeight + position * itemHeight - minimumOverlayHeight - (parentHeight - minimumOverlayHeight) / 2 + itemHeight / 2));

    if (highlight) {
      ((InlineResult<?>) tracks.get(position + 1).getData()).highlight();
    }

    recyclerView.smoothScrollBy(0, desiredScrollY - currentScrollY);
  }

  // (user 2026-11-05 «когда переходишь в плейлист или «следующие» список раскрывается, но вернуться в прежнее состояние нельзя; свайп
  // назад от края должен возвращать сначала назад, свайп сверху вниз, где плеер, тоже»): the opened list is a step of this screen

  /** Over the big cover itself, and the list isn't opened */
  private boolean tgx101CoverSwipeAllowed (float y) {
    if (recyclerView == null || !mayExpandOverlay() || scrollFactor != 0f || coverView.getActualCollapseFactor() > 0f) return false;
    int coverBottom = getCurrentOverlayHeight() - getMinimumOverlayHeight();
    return y > HeaderView.getTopOffset() + HeaderView.getSize(false) && y < coverBottom;
  }

  private boolean tgx101ListOpened () {
    return scrollFactor != 0f && recyclerView != null && mayExpandOverlay();
  }

  // (user 2026-11-05 «сверху вниз слишком чувствителен, подёргивания, когда свайп не завершён»): it takes a real pull to bring the cover
  // down; a pull let go early goes smoothly back to where it began, with no fraction of a pixel lost on the way

  private static final float TGX101_PULL_RETURN_DP = 96f, TGX101_PULL_FLICK_DP = 48f, TGX101_PULL_FLICK_DP_PER_MS = 1.2f;
  private float tgx101PullRest;
  private int tgx101PullScrolled;
  private long tgx101PullStartedAt;

  private static float tgx101PullSlop () {
    return Screen.getTouchSlop() * 2;
  }

  private void tgx101BeginPull () {
    recyclerView.stopScroll();
    tgx101PullRest = 0f;
    tgx101PullScrolled = 0;
    tgx101PullStartedAt = android.os.SystemClock.uptimeMillis();
  }

  private void tgx101PullList (float dy) {
    tgx101PullRest -= dy;
    int by = (int) tgx101PullRest;
    if (by != 0) {
      tgx101PullRest -= by;
      tgx101PullScrolled += by;
      recyclerView.scrollBy(0, by);
    }
  }

  private void tgx101EndPull (float total) {
    final long time = Math.max(1L, android.os.SystemClock.uptimeMillis() - tgx101PullStartedAt);
    final boolean flick = total >= Screen.dp(TGX101_PULL_FLICK_DP) && total / time >= Screen.dp(TGX101_PULL_FLICK_DP_PER_MS);
    if (total >= Screen.dp(TGX101_PULL_RETURN_DP) || flick) {
      tgx101ReturnToCover();
    } else if (tgx101PullScrolled != 0) {
      recyclerView.smoothScrollBy(0, -tgx101PullScrolled);
    }
    tgx101PullScrolled = 0;
  }

  // Closing the player down is a longer way than the other popup screens

  /** (video 2026-11-05 23:23) short drags of 40–100 dp, the list's or a tap's, started the close and the screen jerked down and back */
  @Override
  public float tgx101PopupStartDp () {
    return 32f;
  }

  @Override
  public float tgx101PopupCloseFlingDp () {
    return 1800f;
  }

  @Override
  public float tgx101PopupCloseDistanceDp () {
    return 160f;
  }

  @Override
  public float tgx101PopupDropFactor () {
    return .4f;
  }

  private void tgx101ReturnToCover () {
    recyclerView.stopScroll();
    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    if (manager.findFirstVisibleItemPosition() > 8) { // far down the queue: not seconds of rows flying by
      manager.scrollToPosition(8);
    }
    recyclerView.smoothScrollToPosition(0);
  }

  @Override
  public boolean tgx101TakesSystemBack () {
    return tgx101ListOpened();
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (tgx101ListOpened()) {
      if (commit) tgx101ReturnToCover();
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  private InlineResultCommon currentItem;

  // The colours of the current track's cover (user 2026-11-04, player mockup)
  private final org.thunderdog.challegram.player.Tgx101CoverTint.Animated tgx101Tint = new org.thunderdog.challegram.player.Tgx101CoverTint.Animated(this::tgx101InvalidateTinted);
  private final android.util.SparseIntArray tgx101ButtonTints = new android.util.SparseIntArray();

  private void tgx101InvalidateTinted () {
    if (coverView != null) coverView.invalidate();
    tgx101MatchStatusIcons();
    if (tgx101SpeedPill != null) tgx101SpeedPill.invalidate();
    if (tgx101NextPill != null) tgx101NextPill.invalidate();
    if (tgx101LyricsPill != null) tgx101LyricsPill.invalidate();
  }

  private void tgx101UpdateTint (InlineResultCommon item, boolean animated) {
    if (item == null) return;
    final TdApi.Message message = item.getMessage();
    boolean[] sync = {true};
    org.thunderdog.challegram.player.Tgx101CoverTint.request(item.tdlib(), message, colors -> {
      if (currentItem == item) {
        tgx101Tint.set(colors, animated || !sync[0]);
      }
    });
    sync[0] = false;
  }

  /** The active shuffle, repeat and ♥ take the cover's accent; called from drawing, so a change of a theme comes here too */
  private void tgx101TintButton (ImageView button, boolean isActive) {
    if (!isActive) return;
    int theme = Theme.getColor(ColorId.playerButtonActive);
    int color = tgx101Tint.accent(theme);
    int key = color * 31 + theme;
    if (tgx101ButtonTints.get(button.getId()) != key) {
      tgx101ButtonTints.put(button.getId(), key);
      button.setColorFilter(color);
    }
  }

  private void setItem (InlineResultCommon item, boolean setCurrent) {
    if (this.currentItem == item) {
      if (item != null && setCurrent) {
        item.setIsTrackCurrent(true);
        item.setIsTrackPlaying(true);
      }
      return;
    }
    boolean hadItem = currentItem != null;
    if (hadItem) {
      tdlib.context().player().removeTrackListener(tdlib, currentItem.getMessage(), this);
      tdlib.files().unsubscribe(currentItem.getTrackFile().id, this);
      if (setCurrent) {
        currentItem.setIsTrackCurrent(false);
      }
    }
    this.currentItem = item;
    tgx101UpdateTint(item, hadItem);
    coverView.setItem(item);
    float prefixProgress = 0f;
    float progress = 0f;
    if (item != null) {
      if (setCurrent) {
        item.setIsTrackCurrent(true);
      }
      tdlib.context().player().addTrackListener(tdlib, item.getMessage(), this);
      tdlib.files().subscribe(item.getTrackFile(), this);
      prefixProgress = TD.getFileOffsetProgress(item.getTrackFile());
      progress = TD.getFilePrefixProgress(item.getTrackFile());
    }
    coverView.setFileProgress(prefixProgress, progress, hadItem);
    ensureScroll(hadItem);
    tgx101UpdateNextPill();
    tgx101UpdateHeart();
    if (tgx101LyricsSheet != null && item != null) tgx101LyricsSheet.setItem(item);
  }

  // ♥: to the Storage, held to a folder of it (user 2026-11-04, player mockup)

  private ImageView tgx101Heart, tgx101Dislike;
  private final Runnable tgx101TasteListener = this::tgx101UpdateHeart;

  private org.thunderdog.challegram.player.Tgx101MusicTaste tgx101Taste () {
    return org.thunderdog.challegram.player.Tgx101MusicTaste.get(tdlib);
  }

  private void tgx101UpdateHeart () {
    if (tgx101Heart == null || currentItem == null) return;
    final TdApi.Message message = currentItem.getMessage();
    boolean canSave = message.chatId != 0;
    tgx101Heart.setVisibility(canSave ? View.VISIBLE : View.GONE);
    boolean saved = tgx101Taste().isLiked(message);
    tgx101Heart.setImageResource(saved ? R.drawable.baseline_favorite_24 : R.drawable.tgx101_heart_outline_24);
    setActive(tgx101Heart, saved);
    boolean disliked = tgx101Taste().isDisliked(message);
    tgx101Dislike.setImageResource(disliked ? R.drawable.tgx101_thumb_down_24 : R.drawable.tgx101_thumb_down_outline_24);
    setActive(tgx101Dislike, disliked);
    if (coverView != null) coverView.invalidate();
  }

  private void tgx101ToggleSaved () {
    if (currentItem != null) tgx101Taste().toggleLike(currentItem.getMessage());
  }

  // 👎 (user 2026-11-05): the next track, this one isn't offered by the wave any more, with «Undo» for a slip of a finger; pressed again
  // on a hidden one: it's back. Nothing else changes: an artist goes only by a choice in the menu of the held button

  private void tgx101ToggleDislike () {
    if (currentItem == null) return;
    final TdApi.Message message = currentItem.getMessage();
    if (tgx101Taste().isDisliked(message)) {
      tgx101Taste().setDisliked(message, false);
      return;
    }
    tgx101Taste().setDisliked(message, true);
    tdlib.context().player().skip(true);
    tgx101ShowUndo(message);
  }

  // (user 2026-11-05, screenshot «отмена налазит на текст, сделай в нашем стиле»): a capsule over the list; the words give way to the button

  private View tgx101UndoCapsule;
  private Runnable tgx101UndoHide;

  private void tgx101HideUndo (boolean animated) {
    final View capsule = tgx101UndoCapsule;
    tgx101UndoCapsule = null;
    if (tgx101UndoHide != null) {
      UI.removePendingRunnable(tgx101UndoHide);
      tgx101UndoHide = null;
    }
    if (capsule == null) return;
    final Runnable remove = () -> {
      if (capsule.getParent() instanceof ViewGroup) ((ViewGroup) capsule.getParent()).removeView(capsule);
    };
    if (animated) {
      capsule.animate().alpha(0f).translationY(Screen.dp(12f)).setDuration(160).withEndAction(remove).start();
    } else {
      remove.run();
    }
  }

  private void tgx101ShowUndo (TdApi.Message message) {
    tgx101HideUndo(false);
    final android.widget.LinearLayout capsule = new android.widget.LinearLayout(context);
    capsule.setOrientation(android.widget.LinearLayout.HORIZONTAL);
    capsule.setGravity(android.view.Gravity.CENTER_VERTICAL);
    capsule.setPadding(Screen.dp(16f), 0, Screen.dp(4f), 0);
    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
    bg.setColor(Theme.getColor(ColorId.filling));
    bg.setCornerRadius(Screen.dp(16f));
    bg.setStroke(Math.max(1, Screen.dp(.5f)), Theme.getColor(ColorId.separator));
    capsule.setBackground(bg);
    capsule.setClickable(true);
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) capsule.setElevation(Screen.dp(4f));

    android.widget.TextView text = Tgx101SavedHomeController.text(context, 14f, Theme.textAccentColor(), false);
    text.setText(Lang.getString(R.string.Tgx101DislikeDone));
    text.setSingleLine(true);
    text.setEllipsize(android.text.TextUtils.TruncateAt.END);
    capsule.addView(text, new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    android.widget.TextView undo = Tgx101SavedHomeController.text(context, 14f, tgx101Tint.accent(Theme.getColor(ColorId.textLink)), true);
    undo.setText(Lang.getString(R.string.Tgx101Undo));
    undo.setGravity(android.view.Gravity.CENTER);
    undo.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    Views.setClickable(undo);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(undo);
    undo.setOnClickListener(v -> {
      tgx101HideUndo(true);
      tgx101Taste().setDisliked(message, false);
      if (!isDestroyed()) tdlib.context().player().playPauseMessage(tdlib, message, this);
    });
    capsule.addView(undo, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(48f)));

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f), android.view.Gravity.BOTTOM);
    params.leftMargin = params.rightMargin = Screen.dp(12f);
    params.bottomMargin = Screen.dp(12f) + extraBottomInsetWithoutIme;
    capsule.setLayoutParams(params);
    capsule.setAlpha(0f);
    capsule.setTranslationY(Screen.dp(12f));
    ((ViewGroup) getValue()).addView(capsule);
    capsule.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    tgx101UndoCapsule = capsule;
    tgx101UndoHide = () -> tgx101HideUndo(true);
    UI.post(tgx101UndoHide, 5000);
  }

  /** (user 2026-11-05 «не вижу, где хранятся дизлайкнутые аудио») all that is hidden, a tap brings it back */
  private void tgx101ShowHidden () {
    final Tgx101Sheet.Handle[] handle = new Tgx101Sheet.Handle[1];
    handle[0] = Tgx101Sheet.show(this, null, body -> tgx101FillHidden(body));
  }

  private void tgx101FillHidden (android.widget.LinearLayout body) {
    body.removeAllViews();
    body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101DislikeHidden)));
    final List<String> artists = tgx101Taste().hiddenArtists();
    final List<String[]> tracks = tgx101Taste().hiddenTracks();
    if (artists.isEmpty() && tracks.isEmpty()) {
      body.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(R.string.Tgx101DislikeHiddenNone), null, false, null));
      return;
    }
    android.widget.TextView hint = Tgx101SavedHomeController.text(context, 13f, Theme.textDecentColor(), false);
    hint.setText(Lang.getString(R.string.Tgx101DislikeHiddenHint));
    hint.setPadding(Screen.dp(20f), 0, Screen.dp(20f), Screen.dp(6f));
    body.addView(hint);
    for (String artist : artists) {
      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_block_24, 0, artist, null, false, v -> {
        tgx101Taste().setArtistHidden(artist, false);
        tgx101FillHidden(body);
      }));
    }
    for (int i = tracks.size() - 1; i >= 0; i--) { // the latest first
      final String[] track = tracks.get(i);
      body.addView(Tgx101Sheet.row(context, R.drawable.tgx101_thumb_down_outline_24, 0, track[1], null, false, v -> {
        tgx101Taste().restoreTrack(track[0]);
        tgx101FillHidden(body);
      }));
    }
  }

  private void tgx101AttachDislikeMenu (View button) {
    final View.OnTouchListener keepFinger = (v, e) -> {
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN && v.getParent() != null) {
        v.getParent().requestDisallowInterceptTouchEvent(true);
      }
      return false;
    };
    button.setOnTouchListener(keepFinger);
    button.setOnLongClickListener(v -> {
      if (currentItem == null) return false;
      UI.forceVibrate(v, false);
      new Tgx101DislikeMenu(this, v, keepFinger, currentItem.getMessage());
      return true;
    });
  }

  // (user 2026-11-05 «зажатие на сердце должно вести по пальцу, а не свайпать экран плеера»): the finger stays on ♥ and goes on to
  // its menu; the menu is the usual one of a held button

  private void tgx101AttachFolderMenu (View heart) {
    final View.OnTouchListener keepFinger = (v, e) -> {
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN && v.getParent() != null) {
        v.getParent().requestDisallowInterceptTouchEvent(true); // the screen doesn't go down with it
      }
      return false;
    };
    heart.setOnTouchListener(keepFinger);
    heart.setOnLongClickListener(v -> {
      if (currentItem == null) return false;
      UI.forceVibrate(v, false);
      new Tgx101HeartMenu(this, v, keepFinger, new Tgx101HeartMenu.Host() {
        @Override
        public void withSavedCopy (me.vkryl.core.lambda.RunnableData<java.util.List<TdApi.Message>> action) {
          if (currentItem != null) tgx101Taste().withCopy(currentItem.getMessage(), action);
        }

        @Override
        public void saveToFolder (long folderId) {
          if (currentItem == null) return;
          long id = folderId != 0 ? folderId : org.thunderdog.challegram.data.Tgx101SavedStore.get(tdlib).playerFolder(Lang.getString(R.string.Tgx101PlayerMusicFolder)).id;
          tgx101Taste().saveToFolder(currentItem.getMessage(), id);
        }
      });
      return true;
    });
  }

  // Queue search and the file's details (user 2026-11-04, player mockup): sheets over the list, the list itself stays as it is

  private void tgx101ShowQueueSearch () {
    final Tgx101Sheet.Handle[] handle = new Tgx101Sheet.Handle[1];
    final android.widget.EditText[] field = new android.widget.EditText[1];
    handle[0] = Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101PlayerSearch)));
      android.widget.EditText e = new android.widget.EditText(context);
      field[0] = e;
      e.setSingleLine(true);
      e.setHint(Lang.getString(R.string.Search));
      Tgx101SavedHomeController.styleField(e, true);
      e.setGravity(android.view.Gravity.CENTER_VERTICAL);
      e.setIncludeFontPadding(false); // the word in the middle of the field, whatever the font
      android.widget.LinearLayout.LayoutParams ep = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
      ep.setMargins(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(8f));
      body.addView(e, ep);
      android.widget.LinearLayout results = new android.widget.LinearLayout(context);
      results.setOrientation(android.widget.LinearLayout.VERTICAL);
      body.addView(results);
      android.widget.LinearLayout everywhere = new android.widget.LinearLayout(context);
      everywhere.setOrientation(android.widget.LinearLayout.VERTICAL);
      body.addView(everywhere);
      final Tgx101AllChatsSearch allChats = new Tgx101AllChatsSearch(everywhere, () -> {
        org.thunderdog.challegram.tool.Keyboard.hide(e);
        handle[0].dismiss();
      });
      Runnable fill = () -> {
        results.removeAllViews();
        String query = e.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        final boolean searching = query.length() >= Tgx101AllChatsSearch.MIN_QUERY;
        allChats.setQuery(query);
        int shown = 0;
        for (int i = 1; i < tracks.size() - 1 && shown < (searching ? 8 : 40); i++) {
          Object data = tracks.get(i).getData();
          if (!(data instanceof InlineResultCommon)) continue;
          InlineResultCommon track = (InlineResultCommon) data;
          String title = StringUtils.isEmpty(track.getTrackTitle()) ? "" : track.getTrackTitle();
          String artist = StringUtils.isEmpty(track.getTrackSubtitle()) ? "" : track.getTrackSubtitle();
          if (!query.isEmpty() && !(title + " " + artist).toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
          results.addView(Tgx101Sheet.row(context, track == currentItem ? R.drawable.baseline_volume_up_24 : R.drawable.baseline_queue_music_24, 0, title.isEmpty() ? artist : title, title.isEmpty() ? null : artist, false, v -> {
            org.thunderdog.challegram.tool.Keyboard.hide(e);
            handle[0].dismiss();
            if (track != currentItem) tdlib.context().player().playPauseMessage(tdlib, track.getMessage(), this);
          }));
          shown++;
        }
        if (shown == 0 && !searching) {
          results.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(R.string.Tgx101PlayerNothing), null, false, null));
        } else if (shown != 0 && searching) {
          results.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101PlayerQueue)), 0);
        }
      };
      e.addTextChangedListener(new android.text.TextWatcher() {
        @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged (CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged (android.text.Editable s) { fill.run(); }
      });
      fill.run();
    });
    if (field[0] != null) {
      field[0].requestFocus();
      field[0].postDelayed(() -> {
        if (androidx.core.view.ViewCompat.isAttachedToWindow(field[0])) org.thunderdog.challegram.tool.Keyboard.show(field[0]);
      }, 200);
    }
  }

  // (user 2026-11-05 «глобальный поиск музыки») in all the chats and channels of the account: Telegram has no search of music in all of
  // its public channels. What was found becomes the queue

  private final class Tgx101AllChatsSearch {
    static final int MIN_QUERY = 2;
    private static final int PAGE = 50;
    private final android.widget.LinearLayout box;
    private final Runnable close;
    private final List<TdApi.Message> found = new java.util.ArrayList<>();
    private final java.util.Set<String> seen = new java.util.HashSet<>();
    private String query = "", nextOffset;
    private int token;
    private Runnable scheduled;
    private View moreRow;

    Tgx101AllChatsSearch (android.widget.LinearLayout box, Runnable close) {
      this.box = box;
      this.close = close;
    }

    void setQuery (String query) {
      if (query.equals(this.query)) return;
      this.query = query;
      token++;
      if (scheduled != null) UI.removePendingRunnable(scheduled);
      scheduled = null;
      box.removeAllViews();
      found.clear();
      seen.clear();
      nextOffset = null;
      moreRow = null;
      if (query.length() >= MIN_QUERY) {
        box.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101PlayerSearchAll)));
        scheduled = this::load;
        UI.post(scheduled, 400); // not for each letter
      }
    }

    private void load () {
      final int request = token;
      tdlib.send(new TdApi.SearchMessages(null, query, nextOffset != null ? nextOffset : "", PAGE, new TdApi.SearchMessagesFilterAudio(), null, 0, 0), (result, error) -> UI.post(() -> {
        if (request != token || isDestroyed()) return;
        if (moreRow != null) {
          box.removeView(moreRow);
          moreRow = null;
        }
        if (error != null) {
          UI.showError(error);
          return;
        }
        int added = 0;
        for (TdApi.Message message : result.messages) {
          if (!tgx.td.Td.isAudio(message.content)) continue;
          TdApi.Audio audio = ((TdApi.MessageAudio) message.content).audio;
          // the same song is in many chats
          String key = audio.audio.remote != null && !StringUtils.isEmpty(audio.audio.remote.uniqueId) ? audio.audio.remote.uniqueId : message.chatId + "_" + message.id;
          if (!seen.add(key)) continue;
          found.add(message);
          box.addView(row(message, audio));
          added++;
        }
        nextOffset = result.nextOffset;
        if (found.isEmpty()) {
          box.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(R.string.Tgx101PlayerNothing), null, false, null));
        } else if (!StringUtils.isEmpty(nextOffset) && result.messages.length > 0) {
          moreRow = Tgx101Sheet.row(context, R.drawable.baseline_search_24, 0, Lang.getString(R.string.Tgx101PlayerSearchMore), null, false, v -> load());
          box.addView(moreRow);
        }
      }));
    }

    private View row (TdApi.Message message, TdApi.Audio audio) {
      String title = TD.getTitle(audio), artist = TD.getSubtitle(audio);
      if (StringUtils.isEmpty(title)) title = audio.fileName;
      return Tgx101Sheet.row(context, R.drawable.baseline_music_note_24, 0, title, artist, false, v -> {
        close.run();
        if (currentItem != null && TGPlayerController.compareTracks(currentItem.getMessage(), message)) return;
        final List<TdApi.Message> queue = new java.util.ArrayList<>(found);
        tdlib.context().player().playPauseMessage(tdlib, message, new TGPlayerController.PlayListBuilder() {
          @Override
          public TGPlayerController.PlayList buildPlayList (TdApi.Message fromMessage) {
            int origin = 0;
            for (int i = 0; i < queue.size(); i++) {
              if (TGPlayerController.compareTracks(queue.get(i), fromMessage)) origin = i;
            }
            return new TGPlayerController.PlayList(queue, origin).setReachedEnds(true, true); // only what was found: it isn't one chat to go on in
          }

          @Override
          public boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId) {
            return false;
          }
        });
      });
    }
  }

  /** (user 2026-11-05) «Wave», and the similar tracks from outside: off until the warning is read and it's switched on */
  private void tgx101ShowWaveSheet () {
    final Tgx101Sheet.Handle[] handle = new Tgx101Sheet.Handle[1];
    handle[0] = Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101Wave)));
      final TdApi.Message left = org.thunderdog.challegram.player.Tgx101Wave.resumable(tdlib);
      if (left != null && left.content instanceof TdApi.MessageAudio) {
        String leftTitle = TD.getTitle(((TdApi.MessageAudio) left.content).audio);
        body.addView(Tgx101Sheet.row(context, R.drawable.baseline_play_arrow_36_white, 0, Lang.getString(R.string.Tgx101WaveResume), leftTitle, false, v -> {
          handle[0].dismiss();
          org.thunderdog.challegram.player.Tgx101Wave.resume(tdlib);
        }));
      }
      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_music_note_24, 0, Lang.getString(left != null ? R.string.Tgx101WaveStartNew : R.string.Tgx101WaveStart), null, false, v -> {
        handle[0].dismiss();
        tgx101StartWave();
      }));
      final org.thunderdog.challegram.unsorted.Settings settings = org.thunderdog.challegram.unsorted.Settings.instance();
      final boolean on = settings.tgx101WaveHints();
      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_search_24, 0, Lang.getString(on ? R.string.Tgx101WaveHintsOff : R.string.Tgx101WaveHintsOn), null, on, v -> {
        settings.setTgx101WaveHints(!on);
        handle[0].dismiss();
        tgx101ShowWaveSheet();
      }));
      android.widget.TextView warning = Tgx101SavedHomeController.text(context, 13f, Theme.textDecentColor(), false);
      warning.setText(Lang.getString(R.string.Tgx101WaveHintsWarning));
      warning.setPadding(Screen.dp(20f), Screen.dp(2f), Screen.dp(20f), Screen.dp(6f));
      body.addView(warning);
    });
  }

  private void tgx101StartWave () {
    String artist = currentItem != null ? org.thunderdog.challegram.player.Tgx101MusicTaste.artistOf(currentItem.getMessage()) : null;
    org.thunderdog.challegram.player.Tgx101Wave.start(tdlib, artist, started -> {
      if (!started) UI.showToast(R.string.Tgx101WaveEmpty, android.widget.Toast.LENGTH_SHORT);
    });
  }

  private void tgx101ShowFileDetails () {
    if (currentItem == null) return;
    final TdApi.Audio audio = currentItem.getTrackAudio();
    final TdApi.File file = currentItem.getTrackFile();
    Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101PlayerInfo)));
      if (audio != null && !StringUtils.isEmpty(audio.fileName)) {
        body.addView(Tgx101Sheet.row(context, 0, 0, audio.fileName, null, false, null));
      }
      if (audio != null && !StringUtils.isEmpty(audio.mimeType)) {
        String mime = audio.mimeType;
        int slash = mime.indexOf('/');
        body.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(R.string.Tgx101PlayerInfoFormat), (slash != -1 ? mime.substring(slash + 1) : mime).toUpperCase(java.util.Locale.ROOT), false, null));
      }
      if (audio != null && audio.duration > 0) {
        body.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(R.string.Tgx101PlayerInfoLength), org.thunderdog.challegram.tool.Strings.buildDuration(audio.duration), false, null));
      }
      if (file != null && file.size > 0) {
        body.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(R.string.Tgx101PlayerInfoSize), org.thunderdog.challegram.tool.Strings.buildSize(file.size), false, null));
        if (audio != null && audio.duration > 0) {
          // from the size and the length: the real figure lies in the file's header, this is what it averages to
          long kbps = file.size * 8 / audio.duration / 1000;
          body.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(R.string.Tgx101PlayerInfoBitrate), Lang.getString(R.string.Tgx101PlayerInfoKbps, kbps), false, null));
        }
      }
    });
  }

  // ⏮ ⏭ held: rewinds, faster the longer it is held (user 2026-11-04, player mockup)

  private static final long TGX101_HOLD_MS = 350, TGX101_HOLD_TICK_MS = 180;
  private boolean tgx101SeekHeld;
  private Runnable tgx101HoldTick;

  @android.annotation.SuppressLint("ClickableViewAccessibility")
  private void tgx101HoldToSeek (View button, int direction) {
    button.setOnTouchListener((v, e) -> {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          tgx101SeekHeld = false;
          final long startedAt = android.os.SystemClock.uptimeMillis();
          tgx101HoldTick = new Runnable() {
            @Override
            public void run () {
              if (tgx101HoldTick != this || isDestroyed() || currentItem == null || coverView == null) return;
              TdApi.Message message = currentItem.getMessage();
              if (!tdlib.context().player().canSeekTrack(message) || coverView.durationMillis <= 0) return;
              if (!tgx101SeekHeld) {
                tgx101SeekHeld = true;
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
              }
              long held = android.os.SystemClock.uptimeMillis() - startedAt;
              long step = held < 1800 ? 1500 : held < 4000 ? 4000 : 9000;
              // stops a second before the end: reaching it would start the next track under the finger
              long position = Math.max(0, Math.min(coverView.positionMillis + direction * step, coverView.durationMillis - 1000));
              tdlib.context().player().seekTrack(message, position);
              coverView.setDurations(position, coverView.durationMillis);
              v.postDelayed(this, TGX101_HOLD_TICK_MS);
            }
          };
          v.postDelayed(tgx101HoldTick, TGX101_HOLD_MS);
          break;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          if (tgx101HoldTick != null) {
            v.removeCallbacks(tgx101HoldTick);
            tgx101HoldTick = null;
          }
          if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) tgx101SeekHeld = false;
          break;
      }
      return false; // the click still goes through
    });
  }

  // The row under the buttons (user 2026-11-04, player mockup)

  private Tgx101Pill tgx101SpeedPill, tgx101NextPill, tgx101LyricsPill;
  private Tgx101LyricsSheet tgx101LyricsSheet;

  private void tgx101ShowLyrics () {
    if (currentItem != null && tgx101LyricsSheet == null) {
      tgx101LyricsSheet = new Tgx101LyricsSheet(this, currentItem, () -> tgx101LyricsSheet = null);
    }
  }

  private final TGPlayerController.TrackChangeListener tgx101SpeedListener = new TGPlayerController.TrackChangeListener() {
    @Override
    public void onTrackChanged (Tdlib tdlib, @Nullable TdApi.Message newTrack, int fileId, int state, float progress, boolean byUser) { }

    @Override
    public void onPlaybackSpeedChanged (int newSpeed) {
      UI.execute(() -> tgx101UpdateSpeedPill(newSpeed));
    }
  };

  private static String tgx101SpeedText (int speed) {
    return org.thunderdog.challegram.navigation.PlaybackSpeedLayout.getSpeedText(speed) + "×";
  }

  private void tgx101UpdateSpeedPill (int speed) {
    if (tgx101SpeedPill != null) tgx101SpeedPill.setText(tgx101SpeedText(speed));
  }

  private void tgx101UpdateNextPill () {
    if (tgx101NextPill == null) return;
    TdApi.Message next = tdlib.context().player().tgx101PeekNextTrack();
    String title = null;
    if (next != null) {
      for (int i = 1; i < tracks.size() - 1; i++) {
        Object data = tracks.get(i).getData();
        if (data instanceof InlineResultCommon && TGPlayerController.compareTracks(next, ((InlineResultCommon) data).getMessage())) {
          title = ((InlineResultCommon) data).getTrackTitle();
          break;
        }
      }
    }
    tgx101NextPill.setText(StringUtils.isEmpty(title) ? Lang.getString(R.string.Tgx101PlayerQueue) : Lang.getString(R.string.Tgx101PlayerNext, title));
  }

  private static final int[] TGX101_SPEEDS = {75, 100, 125, 150, 200};

  private void tgx101ShowSpeedSheet () {
    final Tgx101Sheet.Handle[] handle = new Tgx101Sheet.Handle[1];
    handle[0] = Tgx101Sheet.show(this, null, body -> {
      final int current = tdlib.context().player().getSpeed();
      android.widget.TextView title = Tgx101Sheet.title(context, Lang.getString(R.string.PlaybackSpeed) + " · " + tgx101SpeedText(current));
      body.addView(title);
      android.widget.SeekBar bar = new android.widget.SeekBar(context);
      // from 0.2× to 3× by 0.05, the player's own range
      bar.setMax((300 - 20) / 5);
      bar.setProgress((org.thunderdog.challegram.navigation.PlaybackSpeedLayout.normalizeSpeed(current) - 20) / 5);
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        android.content.res.ColorStateList accent = android.content.res.ColorStateList.valueOf(Theme.getColor(ColorId.seekDone));
        bar.setProgressTintList(accent);
        bar.setThumbTintList(accent);
      }
      bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged (android.widget.SeekBar seekBar, int progress, boolean fromUser) {
          if (!fromUser) return;
          int speed = 20 + progress * 5;
          tdlib.context().player().setPlaybackSpeed(speed);
          title.setText(Lang.getString(R.string.PlaybackSpeed) + " · " + tgx101SpeedText(speed));
        }
        @Override public void onStartTrackingTouch (android.widget.SeekBar seekBar) { }
        @Override public void onStopTrackingTouch (android.widget.SeekBar seekBar) { }
      });
      android.widget.LinearLayout.LayoutParams barParams = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(44f));
      barParams.leftMargin = barParams.rightMargin = Screen.dp(8f);
      body.addView(bar, barParams);
      for (int speed : TGX101_SPEEDS) {
        final int value = speed;
        body.addView(Tgx101Sheet.row(context, 0, 0, tgx101SpeedText(value), null, value == current, v -> {
          tdlib.context().player().setPlaybackSpeed(value);
          handle[0].dismiss();
        }));
      }
    });
  }

  /** A pill of the row: its own drawing follows the theme by a plain invalidate */
  private static class Tgx101Pill extends View {
    private CharSequence text = "";
    private int iconRes;
    private org.thunderdog.challegram.player.Tgx101CoverTint.Animated tint;
    private PlaybackController controller;
    private boolean pulling;
    private float startX, startY, lastY;

    void setTint (org.thunderdog.challegram.player.Tgx101CoverTint.Animated tint) {
      this.tint = tint;
    }

    void setController (PlaybackController controller) {
      this.controller = controller;
    }

    /** (user 2026-11-05) over an opened list a swipe down on the pill brings the cover down, as on the player itself */
    private boolean pull (MotionEvent e) {
      // on the screen, not in the pill: the pill goes down with the player
      final float x = e.getRawX(), y = e.getRawY();
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          pulling = false;
          startX = x;
          startY = lastY = y;
          return false;
        case MotionEvent.ACTION_MOVE:
          if (!pulling && controller != null && controller.tgx101ListOpened() && y - startY > tgx101PullSlop() && y - startY > Math.abs(x - startX)) {
            pulling = true;
            lastY = y;
            controller.tgx101BeginPull();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            MotionEvent cancel = MotionEvent.obtain(e);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(cancel); // no press and no hold of the pill any more
            cancel.recycle();
            animate().scaleX(1f).scaleY(1f).setDuration(180).start();
          }
          if (pulling) {
            controller.tgx101PullList(y - lastY);
            lastY = y;
          }
          return pulling;
        case MotionEvent.ACTION_UP:
          if (pulling) {
            pulling = false;
            controller.tgx101EndPull(y - startY);
            return true;
          }
          return false;
        case MotionEvent.ACTION_CANCEL:
          if (pulling) {
            pulling = false;
            controller.tgx101EndPull(0f);
            return true;
          }
          return false;
      }
      return false;
    }

    Tgx101Pill (Context context) {
      super(context);
      org.thunderdog.challegram.tool.Views.setClickable(this);
    }

    void setText (CharSequence text) {
      this.text = text;
      invalidate();
    }

    void setIcon (int iconRes) {
      this.iconRes = iconRes;
      invalidate();
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (pull(e)) {
        return true;
      }
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          animate().scaleX(.96f).scaleY(.96f).setDuration(90).start();
          break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          animate().scaleX(1f).scaleY(1f).setDuration(180).start();
          break;
      }
      return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw (Canvas c) {
      RectF rect = Paints.getRectF();
      rect.set(0, 0, getWidth(), getHeight());
      float r = getHeight() / 2f;
      final int accent = tint != null ? tint.accent(Theme.textAccentColor()) : Theme.textAccentColor();
      c.drawRoundRect(rect, r, r, Paints.fillingPaint(ColorUtils.alphaColor(.09f, accent)));
      int left = Screen.dp(14f);
      if (iconRes != 0) {
        android.graphics.drawable.Drawable icon = Drawables.get(getResources(), iconRes);
        if (icon != null) {
          // with no words: in the middle of the pill
          Drawables.drawCentered(c, icon, text.length() == 0 ? getWidth() / 2f : left + Screen.dp(9f), getHeight() / 2f, Paints.getIconGrayPorterDuffPaint());
        }
        left += Screen.dp(26f);
      }
      android.text.TextPaint paint = Paints.getRegularTextPaint(13f, accent);
      int available = getWidth() - left - Screen.dp(14f);
      if (available <= 0) return;
      CharSequence shown = android.text.TextUtils.ellipsize(text, paint, available, android.text.TextUtils.TruncateAt.END);
      float textWidth = paint.measureText(shown, 0, shown.length());
      float x = iconRes != 0 ? left : (getWidth() - textWidth) / 2f;
      float y = getHeight() / 2f - (paint.descent() + paint.ascent()) / 2f;
      c.drawText(shown, 0, shown.length(), x, y, paint);
    }
  }

  // Track listener

  @Override
  public void onTrackStateChanged (Tdlib tdlib, long chatId, long messageId, int fileId, int state) {
    if ((state == TGPlayerController.STATE_PAUSED || state == TGPlayerController.STATE_PLAYING) && currentItem != null && TGPlayerController.compareTracks(currentItem.getMessage(), chatId, messageId, fileId)) {
      boolean isPlaying = state == TGPlayerController.STATE_PLAYING;
      playPauseButton.setIsPlaying(isPlaying, true);
      coverView.tgx101SetWaving(isPlaying, true);
      if (currentItem != null) {
        currentItem.setIsTrackPlaying(isPlaying);
      }
    }
  }

  @Override
  public void onTrackPlayProgress (Tdlib tdlib, long chatId, long messageId, int fileId, float progress, long playPosition, long playDuration, boolean isBuffering) {
    if (currentItem != null && TGPlayerController.compareTracks(currentItem.getMessage(), chatId, messageId, fileId)) {
      coverView.setDurations(playPosition, playDuration);
      if (tgx101LyricsSheet != null) tgx101LyricsSheet.onProgress(playPosition);
    }
  }

  // Current file progress listener (used for seek)

  private void setFileProgress (TdApi.File file) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      Handler handler = getHandler();
      handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_PROGRESS, file));
      return;
    }
    if (currentItem != null && file.id == currentItem.getTrackFile().id) {
      coverView.setFileProgress(TD.getFileOffsetProgress(file), TD.getFilePrefixProgress(file), true);
    }
  }

  @Override
  public void onFileLoadProgress (TdApi.File file) {
    setFileProgress(file);
  }

  @Override
  public void onFileLoadStateChanged (Tdlib tdlib, int fileId, int state, @Nullable TdApi.File downloadedFile) {
    if (state == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED && downloadedFile != null) {
      setFileProgress(downloadedFile);
    }
  }

  // Track list

  private static ListItem valueOf (InlineResultCommon common) {
    common.setIsTrack(true);
    return new ListItem(ListItem.TYPE_CUSTOM_INLINE, R.id.btn_custom).setData(common);
  }

  private int playFlags;
  private long playListChatId;

  @Override
  public void onTrackListReset (Tdlib tdlib, @NonNull TdApi.Message currentTrack, int trackIndex, List<TdApi.Message> trackList, long playListChatId, int playFlags, int playState) {
    UI.post(this::tgx101UpdateNextPill);
    if (ignoreNextReset) {
      ignoreNextReset = false;
      setItemByPosition(trackIndex, true);
      return;
    }

    ListItem headerItem = tracks.get(0);
    ListItem footerItem = tracks.get(tracks.size() - 1);
    int prevSize = tracks.size() - 2;

    canRestoreList = false;

    tracks.clear();
    ArrayUtils.ensureCapacity(tracks, trackList.size() + 1);

    tracks.add(headerItem);
    int foundIndex = buildList(tracks, tdlib, currentTrack, trackList, playListChatId, playFlags);
    if (foundIndex == -1) {
      if (isAttachedToNavigationController()) {
        throw new IllegalStateException();
      }
      context.navigation().getStack().destroy(this);
      return;
    }
    tracks.add(footerItem);

    int newSize = tracks.size() - 2;
    adapter.notifyItemRangeChanged(1, Math.min(newSize, prevSize));
    if (newSize > prevSize) {
      adapter.notifyItemRangeInserted(1 + prevSize, newSize - prevSize);
    } else if (newSize < prevSize) {
      adapter.notifyItemRangeRemoved(1 + prevSize, prevSize - newSize);
    }

    setItem((InlineResultCommon) tracks.get(preparedTrackIndex + 1).getData(), true);
    onTrackListSizeChanged();
  }

  private void setActive (ImageView imageView, boolean isActive) {
    removeThemeListenerByTarget(imageView);
    int colorId = isActive ? ColorId.playerButtonActive : ColorId.playerButton;
    imageView.setColorFilter(Theme.getColor(colorId));
    addThemeFilterListener(imageView, colorId);
    tgx101ButtonTints.delete(imageView.getId());
    if (coverView != null) coverView.invalidate();
  }

  @Override
  public void onTrackListFlagsChanged (int newPlayFlags) {
    UI.post(this::tgx101UpdateNextPill);
    boolean wasShuffling = (playFlags & TGPlayerController.PLAY_FLAG_SHUFFLE) != 0;
    boolean nowShuffling = (newPlayFlags & TGPlayerController.PLAY_FLAG_SHUFFLE) != 0;
    if (wasShuffling != nowShuffling) {
      setActive(shuffleButton, nowShuffling);
    }

    int prevRepeatMode = TGPlayerController.getPlayRepeatFlag(playFlags);
    int newRepeatMode = TGPlayerController.getPlayRepeatFlag(newPlayFlags);
    if (prevRepeatMode == 0 == (newRepeatMode != 0)) {
      setActive(repeatButton, newRepeatMode != 0);
    }
    if (prevRepeatMode == TGPlayerController.PLAY_FLAG_REPEAT_ONE == (newRepeatMode != TGPlayerController.PLAY_FLAG_REPEAT_ONE)) {
      repeatButton.setImageResource(newRepeatMode == TGPlayerController.PLAY_FLAG_REPEAT_ONE ? R.drawable.round_repeat_one_24 : R.drawable.round_repeat_24);
    }

    boolean wasReverse = (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;
    boolean isReverse = (newPlayFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;

    if (wasReverse != isReverse) {
      int totalCount = getTrackCount();
      int currentIndex = indexOfCurrentTrack();
      if (currentIndex == -1) {
        throw new IllegalStateException();
      }

      int headerItemCount = 1;
      /*for (int i = currentIndex - 1; i >= 0; i--) {
        adapter.moveItem(i + headerItemCount, i + (currentIndex - i) + headerItemCount);
      }
      for (int i = currentIndex + 1; i < totalCount; i++) {
        adapter.moveItem(i + headerItemCount, headerItemCount);
      }*/

      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();

      boolean notify;
      boolean keepScrollPosition = false;
      int keepScrollPositionOffset = 0;

      if (getScrollFactor() < .8f) {
        notify = false;
      } else {
        int firstVisiblePosition = manager.findFirstVisibleItemPosition();
        int lastVisiblePosition = manager.findLastVisibleItemPosition();
        if (firstVisiblePosition == -1 || lastVisiblePosition == -1) {
          notify = false;
        } else {
          if (currentIndex + headerItemCount >= firstVisiblePosition && currentIndex + headerItemCount <= lastVisiblePosition) {
            keepScrollPosition = true;
            View view = manager.findViewByPosition(currentIndex + headerItemCount);
            if (view != null) {
              keepScrollPositionOffset = manager.getDecoratedTop(view);
            }
            int itemCountAfter = lastVisiblePosition - currentIndex + headerItemCount;
            int itemCountBefore = currentIndex + headerItemCount - firstVisiblePosition;
            notify = false; // Math.abs(itemCountBefore - itemCountAfter) < 4;
          } else {
            notify = false;
          }
        }
      }

      for (int i = currentIndex + 1; i < totalCount; i++) {
        adapter.moveItem(i + headerItemCount, headerItemCount, notify);
      }
      for (int i = currentIndex - 1; i >= 0; i--) {
        int offset = (totalCount - currentIndex - 1);
        adapter.moveItem(i + headerItemCount + offset, i + (currentIndex - i) + headerItemCount + offset, notify);
      }
      if (!notify) {
        adapter.notifyItemRangeChanged(headerItemCount, headerItemCount + totalCount);
      }
      if (keepScrollPosition) {
        int newIndex = totalCount - currentIndex - 1;
        manager.scrollToPositionWithOffset(newIndex + headerItemCount, keepScrollPositionOffset);
      }
    }

    setPlayFlagsImpl(newPlayFlags);
  }

  @Override
  public void onTrackListLoadStateChanged () {
    adapter.updateValuedSettingById(R.id.btn_info);
  }

  private void setPlayFlagsImpl (int playFlags) {
    if (this.playFlags != playFlags) {
      this.playFlags = playFlags;
      // TODO?
    }
  }

  private int reversePosition (int position, int totalCount, boolean reverse) {
    return reverse ? totalCount - 1 - position : position;
  }

  private int reversePosition (int position) {
    return reversePosition(position, getTrackCount(), (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0);
  }

  private void setItemByPosition (int trackPosition, boolean setCurrent) {
    setItem((InlineResultCommon) this.tracks.get(reversePosition(trackPosition) + 1).getData(), setCurrent);
  }

  @Override
  public void onTrackListPositionChange (Tdlib tdlib, TdApi.Message newTrack, int newIndex, List<TdApi.Message> trackList, boolean byUserRequest, int playState) {
    setItemByPosition(newIndex, true);
    currentItem.setIsTrackCurrent(true);
  }

  @Override
  public void onTrackListClose (Tdlib tdlib, long playListChatId, long playListMaxMessageId, long playListMinMessageId, boolean newEndReached, boolean oldEndReached, List<TdApi.Message> removedMessages) {
    playPauseButton.setIsPlaying(false, true);
    coverView.tgx101SetWaving(false, true);
    if (currentItem != null) {
      currentItem.setIsTrackCurrent(false);
      setItem((InlineResultCommon) this.tracks.get(1).getData(), false);
      coverView.setDurations(-1, -1);
    }
    savePlayListInformation(playListChatId, playListMaxMessageId, playListMinMessageId, newEndReached, oldEndReached, removedMessages);
  }

  private void removeTrack (final InlineResultCommon common) {
    if (currentItem != null) {
      showOptions(Lang.getStringBold(R.string.PlayListRemoveTrack, common.getTrackTitle() + " – " + common.getTrackSubtitle()), new int[]{R.id.btn_delete, R.id.btn_cancel}, new String[]{Lang.getString(R.string.PlayListRemove), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_delete) {
          tdlib.context().player().removeTrack(common.getMessage(), true);
        }
        return true;
      });
    }
  }

  @Override
  public void onTrackListItemRemoved (Tdlib tdlib, TdApi.Message track, int position, boolean isCurrent) {
    UI.post(this::tgx101UpdateNextPill);
    if ((playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0) {
      int count = getTrackCount();
      position = count - position - 1;
    }
    int headerItemCount = 1;
    adapter.removeItem(position + headerItemCount);
    addDuration(-((TdApi.MessageAudio) track.content).audio.duration);
  }

  private void moveTrack (int fromPosition, int toPosition, boolean dispatchUpdate) {
    if (fromPosition == toPosition) {
      return;
    }
    if (dispatchUpdate) {
      ignoreMoveUpdate = true;
      tdlib.context().player().moveTrack(fromPosition, toPosition);
      ignoreMoveUpdate = false;
    }
    if ((playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0) {
      int count = getTrackCount();
      fromPosition = count - fromPosition - 1;
      toPosition = count - toPosition - 1;
    }
    int headerItemCount = 1;
    adapter.moveItem(fromPosition + headerItemCount, toPosition + headerItemCount);
  }

  private boolean ignoreMoveUpdate;

  @Override
  public void onTrackListItemMoved (Tdlib tdlib, TdApi.Message track, int fromPosition, int toPosition) {
    UI.post(this::tgx101UpdateNextPill);
    if (!ignoreMoveUpdate) {
      moveTrack(fromPosition, toPosition, false);
    }
  }

  @Override
  public void onTrackListItemAdded (Tdlib tdlib, TdApi.Message newTrack, int position) {
    UI.post(this::tgx101UpdateNextPill);
    InlineResult<?> result = InlineResult.valueOf(context, tdlib, newTrack);
    if (!(result instanceof InlineResultCommon) || result.getType() != InlineResult.TYPE_AUDIO) {
      return;
    }
    InlineResultCommon common = (InlineResultCommon) result;
    ListItem item = valueOf(common);
    int tracksCount = getTrackCount();
    int i = (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0 ? 1 + tracksCount - position : 1 + position;
    adapter.addItem(i, item);

    addDuration(common.getTrackAudio().duration);
  }

  @Override
  public void onTrackListItemRangeAdded (Tdlib tdlib, List<TdApi.Message> addedItems, boolean areNew) {
    UI.post(this::tgx101UpdateNextPill);
    ArrayList<ListItem> newItems = new ArrayList<>(addedItems.size());

    long addedDuration = 0;
    int remaining = addedItems.size();
    boolean reverseOrder = (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;
    while (--remaining >= 0) {
      TdApi.Message addedTrack = addedItems.get(reverseOrder ? remaining : addedItems.size() - 1 - remaining);
      InlineResult<?> result = InlineResult.valueOf(context, tdlib, addedTrack);
      if (!(result instanceof InlineResultCommon) || result.getType() != InlineResult.TYPE_AUDIO) {
        return;
      }
      InlineResultCommon common = (InlineResultCommon) result;
      newItems.add(valueOf(common));
      addedDuration += common.getTrackAudio().duration;
    }
    if (newItems.isEmpty()) {
      return;
    }

    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    int headerItemCount = 1;
    int position = firstVisiblePosition != -1 ? Math.max(headerItemCount, firstVisiblePosition) : -1;
    View firstView = manager.getChildAt(position);
    int offset = firstView != null ? manager.getDecoratedTop(firstView) : 0;

    boolean addOnBottom = reverseOrder != areNew;
    int i = addOnBottom ? tracks.size() - 1 : 1;
    tracks.addAll(i, newItems);
    adapter.notifyItemRangeInserted(i, newItems.size());

    if (!addOnBottom && position != -1) {
      manager.scrollToPositionWithOffset(position + addedItems.size(), offset);
    }

    addDuration(addedDuration);
  }

  // State

  private boolean isShuffleActive () {
    return (playFlags & TGPlayerController.PLAY_FLAG_SHUFFLE) != 0;
  }

  private boolean isRepeatActive () {
    return (playFlags & (TGPlayerController.PLAY_FLAG_REPEAT_ONE | TGPlayerController.PLAY_FLAG_REPEAT)) != 0;
  }

  private boolean isPlaying () {
    return currentItem != null && tdlib.context().player().getPlayState(tdlib, currentItem.getMessage()) == TGPlayerController.STATE_PLAYING;
  }

  private int getRepeatIcon () {
    return (TGPlayerController.getPlayRepeatFlag(playFlags) == TGPlayerController.PLAY_FLAG_REPEAT_ONE) ? R.drawable.round_repeat_one_24 : R.drawable.round_repeat_24;
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    if (tgx101ListOpened()) {
      return false; // first back to the cover
    }
    if (y < getMinimumOverlayHeight() + HeaderView.getTopOffset()) {
      return true;
    }
    if (scrollFactor == 0f) {
      return true;
    }
    return !isScrollUnlocked && y < getCurrentOverlayHeight();
  }

  // Events

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_custom) {
      ListItem item = (ListItem) v.getTag();
      InlineResultCommon resultCommon = (InlineResultCommon) item.getData();
      tdlib.context().player().playPauseMessage(tdlib, resultCommon.getMessage(), this);
    } else if (viewId == R.id.btn_play) {
      if (currentItem != null) {
        tdlib.context().player().playPauseMessage(tdlib, currentItem.getMessage(), this);
      }
    } else if (viewId == R.id.btn_next) {
      if (tgx101SeekHeld) { // the finger was rewinding, not asking for another track
        tgx101SeekHeld = false;
      } else {
        tdlib.context().player().skip(true);
      }
    } else if (viewId == R.id.btn_previous) {
      if (tgx101SeekHeld) {
        tgx101SeekHeld = false;
      } else {
        tdlib.context().player().skip(false);
      }
    } else if (viewId == R.id.btn_repeat) {
      tdlib.context().player().toggleRepeatMode();
    } else if (viewId == R.id.btn_shuffle) {
      tdlib.context().player().togglePlaybackFlag(TGPlayerController.PLAY_FLAG_SHUFFLE);
    }
  }

  // Measurements

  private float scrollFactor;

  private void dispatchCheckScrolling (boolean byLayout) {
    getHandler().sendMessage(Message.obtain(getHandler(), ACTION_CHECK_SCROLLING, byLayout ? 1 : 0, 0));
  }

  private int scrollPivotY;

  private void checkScrolling (boolean byLayout) {
    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    final int lastVisiblePosition = manager.findLastVisibleItemPosition();
    if (lastVisiblePosition != RecyclerView.NO_POSITION && lastVisiblePosition + TGPlayerController.SCROLL_LOAD_THRESHOLD >= getTrackCount()) {
      tdlib.context().player().requestMoreTracks();
    }
    final int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    if (firstVisiblePosition == RecyclerView.NO_POSITION) {
      return;
    }

    if (firstVisiblePosition > 0) {
      setScrollFactor(1f, 1f);
      if (isScrollUnlocked) {
        coverView.setCollapsed(true, !byLayout);
      }
      return;
    }
    View view = manager.findViewByPosition(firstVisiblePosition);
    if (view == null) {
      return;
    }

    int viewTop = view.getTop();
    int decorationHeight = manager.getTopDecorationHeight(view);
    int realDecorationHeight = getAvailableOverlayHeight();
    if (decorationHeight != realDecorationHeight && viewTop > 0 && decorationHeight != 0) {
      viewTop *= (float) realDecorationHeight / (float) decorationHeight;
    }

    int minimumHeight = getMinimumOverlayHeight() + HeaderView.getTopOffset();
    viewTop -= minimumHeight;
    if (viewTop < 0) {
      setScrollFactor(1f, MathUtils.clamp(-viewTop / (float) Screen.dp(12f)));
      if (isScrollUnlocked) {
        coverView.setCollapsed(true, !byLayout);
      }
      return;
    }

    int totalHeight = getAvailableOverlayHeight();
    int distance = totalHeight - minimumHeight;
    float factor = viewTop > distance || distance == 0 ? 0f : 1f - (float) viewTop / (float) distance;
    setScrollFactor(MathUtils.clamp(factor), 0f);

    if (mayExpandOverlay()) {
      int threshold = HeaderView.getTopOffset() + HeaderView.getSize(false) / 2;
      if (isScrollUnlocked) {
        coverView.setCollapsed(viewTop <= threshold, !byLayout);
      }
    }

    /*View view = manager.findViewByPosition(firstVisiblePosition);
    int topOffset = view != null ? manager.getDecoratedTop(view) : 0;

    final int overlayHeight = getAvailableOverlayHeight();
    final int itemHeight = Screen.dp(65f);
    final int factorDistance = overlayHeight - getMinimumOverlayHeight() - HeaderView.getTopOffset();

    int totalScrollY = -topOffset;
    if (firstVisiblePosition > 0) {
      totalScrollY += overlayHeight + (firstVisiblePosition - 1) * itemHeight;
    }

    if (!isScrollUnlocked) {
      scrollPivotY = totalScrollY;
      return;
    }

    if (!mayExpandOverlay()) {
      setScrollFactor(scrollFactor, 1f);
      coverView.setCollapsed(true, false);
      return;
    }

    float scrollFactor;

    if (totalScrollY < scrollPivotY) {
      scrollPivotY = totalScrollY;
      scrollFactor = 0f;
    } else {
      scrollFactor = (float) Math.min(factorDistance, totalScrollY - scrollPivotY) / (float) factorDistance;
      if (scrollFactor == 1f) {
        scrollPivotY = 0;
      }
    }

    setScrollFactor(scrollFactor, scrollFactor);
    if (isScrollUnlocked) {
      coverView.setCollapsed((1f - scrollFactor) * factorDistance <= HeaderView.getSize(false) * .9f, !byLayout);
    } else if (scrollFactor < 1f && !coverView.isCollapsed && byLayout) {
      coverView.setCollapsed(false, false);
    }*/
  }

  private float shadowFactor;

  private void setScrollFactor (float scrollFactor, float shadowFactor) {
    if (this.scrollFactor != scrollFactor) {
      float oldScrollFactor = getScrollFactor();
      this.scrollFactor = scrollFactor;
      float newScrollFactor = getScrollFactor();
      onScrollFactorChanged(oldScrollFactor, newScrollFactor);
    }
    if (this.shadowFactor != shadowFactor) {
      this.shadowFactor = shadowFactor;
      if (!mayExpandOverlay()) {
        shadowView.setAlpha(shadowFactor);
      }
    }
  }

  private void onScrollFactorChanged (float oldScrollFactor, float newScrollFactor) {
    if (mayExpandOverlay(recyclerView.getMeasuredWidth(), recyclerView.getMeasuredHeight())) {
      if (oldScrollFactor != newScrollFactor && ((oldScrollFactor >= 0f && oldScrollFactor <= 1f) || (newScrollFactor >= 0f && newScrollFactor <= 1f))) {
        coverView.invalidate();
      }
    }
  }

  private int getAvailableOverlayHeight () {
    return getAvailableOverlayHeight(recyclerView.getMeasuredWidth(), recyclerView.getMeasuredHeight());
  }
  private int getCurrentOverlayHeight () {
    return getCurrentOverlayHeight(recyclerView.getMeasuredWidth(), recyclerView.getMeasuredHeight(), getScrollFactor());
  }
  private float getScrollFactor () {
    return MathUtils.clamp(scrollFactor);
  }
  /*private float getTotalCoverExpandFactor () {
    float desiredCoverFactor = 1f - coverView.collapseFactor;
    float requiredCoverFactor = mayExpandOverlay() ? 1f : 0f;
    return requiredCoverFactor + (desiredCoverFactor - requiredCoverFactor) * unlockFactor;
  }*/
  private boolean mayExpandOverlay () {
    return mayExpandOverlay(recyclerView.getMeasuredWidth(), recyclerView.getMeasuredHeight());
  }

  /** (user 2026-11-04, player mockup) a row under the buttons: the speed and what plays next */
  private static final float TGX101_ROW_DP = 52f;

  private static int getMinimumOverlayHeight () {
    return Screen.dp(112f + TGX101_ROW_DP) + HeaderView.getSize(false);
  }

  private int getAvailableOverlayHeight (int totalWidth, int totalHeight) {
    int height = getMinimumOverlayHeight();
    float ratio = totalHeight != 0 ? (float) totalWidth / (float) totalHeight : 0f;
    if (ratio <= .7f) {
      int offset = HeaderView.getTopOffset();
      // int desiredHeight = offset + (totalHeight - offset) / 2;
      int desiredHeight = totalWidth;
      if (desiredHeight + height <= totalHeight) { // the taller controls must stay on a short screen too
        return desiredHeight + height;
      }
    }
    return height + HeaderView.getTopOffset();
  }

  private boolean mayExpandOverlay (int totalWidth, int totalHeight) {
    int height = getMinimumOverlayHeight();
    float ratio = totalHeight != 0 ? (float) totalWidth / (float) totalHeight : 0f;
    if (ratio <= .7f) {
      int offset = HeaderView.getTopOffset();
      int desiredHeight = offset + (totalHeight - offset) / 2;
      return desiredHeight + height > height / 2;
    }
    return false;
  }

  private float getCoverExpandFactor (int totalWidth, int totalHeight, float scrollFactor) {
    if (scrollFactor >= 1f || !mayExpandOverlay(totalWidth, totalHeight)) {
      return 0f;
    } else {
      return (1f - scrollFactor) * (1f - coverView.collapseFactor);
    }
  }

  private int getCurrentOverlayHeight (int totalWidth, int totalHeight, float scrollFactor) {
    int availableHeight = getAvailableOverlayHeight(totalWidth, totalHeight);
    if (mayExpandOverlay(totalWidth, totalHeight)) {
      float expandFactor = getCoverExpandFactor(totalWidth, totalHeight, scrollFactor);
      int minHeight = getMinimumOverlayHeight() + HeaderView.getTopOffset();
      return minHeight + (int) ((float) (availableHeight - minHeight) * expandFactor);
    }
    return availableHeight;
  }

  // Custom views

  private static class OffsetDecoration extends RecyclerView.ItemDecoration {
    private final PlaybackController context;

    public OffsetDecoration (PlaybackController context) {
      this.context = context;
    }

    @Override
    public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
      RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
      ListItem item = (ListItem) view.getTag();
      if ((holder != null && holder.getBindingAdapterPosition() == 0) || (item != null && item.getViewType() == ListItem.TYPE_ZERO_VIEW)) {
        outRect.top = context.getAvailableOverlayHeight(parent.getMeasuredWidth(), parent.getMeasuredHeight());
      } else {
        outRect.top = 0;
      }
    }
  }

  private static class PlayPauseButton extends View implements FactorAnimator.Target {
    private final Path path;
    private float drawFactor = -1f;
    private BoolAnimator animator;

    public PlayPauseButton (Context context) {
      super(context);

      this.path = new Path();
      this.animator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 160l);
    }

    public void setIsPlaying (boolean isPlaying, boolean animated) {
      animator.setValue(isPlaying, animated);
    }

    private float factor;

    public void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setFactor(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

    @Override
    protected void onDraw (Canvas c) {
      int cx = getMeasuredWidth() / 2;
      int cy = getMeasuredHeight() / 2;

      DrawAlgorithms.drawPlayPause(c, cx, cy, Screen.dp(18f), path, drawFactor, drawFactor = factor, 1f, Theme.getColor(ColorId.playerButton));
    }
  }

  private static class CoverView extends SparseDrawableView implements FactorAnimator.Target, InvalidateContentProvider {
    private final ComplexReceiver preview;
    private final ImageReceiver receiver, tgx101SourceA, tgx101SourceB;
    private ImageReceiver source, tgx101OldSource;
    private PlaybackController controller;
    private boolean disableAnimations;

    private Paint topShadowPaint, radialShadowPaint;
    private ProgressComponent progress;

    private final Drawable icon;

    public void initWithController (PlaybackController controller) {
      this.controller = controller;
    }

    public void setDisableAnimation (boolean disableAnimation) {
      this.disableAnimations = disableAnimation;
      preview.setAnimationDisabled(disableAnimation);
      receiver.setAnimationDisabled(disableAnimation);
      tgx101SourceA.setAnimationDisabled(disableAnimation);
      tgx101SourceB.setAnimationDisabled(disableAnimation);
    }

    private static int getRadialGradientRadius () {
      return Screen.dp(28f);
    }

    public CoverView (Context context) {
      super(context);

      icon = Drawables.get(getResources(), R.drawable.baseline_music_note_48);

      this.progress = new ProgressComponent(UI.getContext(context), Screen.dp(4f));
      this.progress.forceColor(0x00ffffff);
      this.progress.attachToView(this);

      this.topShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      int[] topShadowColors = ShadowView.topShadowColors();
      topShadowPaint.setShader(new LinearGradient(0, 0, 0, ShadowView.simpleTopShadowHeight(), topShadowColors, null, Shader.TileMode.CLAMP));

      radialShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      int radialRadius = getRadialGradientRadius();
      radialShadowPaint.setShader(new RadialGradient(radialRadius, radialRadius, radialRadius, 0x10000000, 0x00000000, Shader.TileMode.CLAMP));

      this.preview = new ComplexReceiver(this);
      this.receiver = new ImageReceiver(this, 0);
      this.source = this.tgx101SourceA = new ImageReceiver(this, 0);
      this.tgx101OldSource = this.tgx101SourceB = new ImageReceiver(this, 0);
      setDisableAnimation(true);

      ViewUtils.setBackground(this, new Drawable() {
        @Override
        public void draw (@NonNull Canvas c) {
          int height = controller.getCurrentOverlayHeight();
          c.drawRect(0, 0, getBounds().right, height, Paints.fillingPaint(controller.tgx101Tint.background(Theme.fillingColor())));
          if (controller.shadowView.getTranslationY() != height) {
            controller.shadowView.setTranslationY(height);
          }
          if (controller.mayExpandOverlay()) {
            controller.shadowView.setAlpha(getActualCollapseFactor());
          }
        }

        @Override
        public void setAlpha (int alpha) {

        }

        @Override
        public void setColorFilter (@Nullable ColorFilter colorFilter) {

        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity () {
          return PixelFormat.UNKNOWN;
        }
      });
    }

    // Events

    private boolean isSeeking;
    private BoolAnimator seekAnimator;
    private float seekDesireFactor;
    private static final int ANIMATOR_SEEK = 3;
    private static final int ANIMATOR_SEEK_DISPLAY = 4;

    private float calculatePositionProgress () {
      return durationMillis <= 0 || positionMillis <= 0 ? 0 : MathUtils.clamp((float) ((double) positionMillis / (double) durationMillis));
    }

    // (user 2026-11-04, player mockup) the played part of the bar waves while the track plays, and is straight on pause and under
    // the finger. Short lines, not a path: a path is rasterized anew for each frame. Not on Android 4: no round caps on lines there
    private static final int ANIMATOR_WAVE = 7;
    private BoolAnimator tgx101WaveAnimator;
    private float tgx101WaveFactor;
    private float[] tgx101WaveLines;

    void tgx101SetWaving (boolean isWaving, boolean animated) {
      if (tgx101WaveAnimator == null) {
        tgx101WaveAnimator = new BoolAnimator(ANIMATOR_WAVE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 320L);
      }
      boolean allowed = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP && !org.thunderdog.challegram.unsorted.Settings.instance().needReduceMotion();
      tgx101WaveAnimator.setValue(isWaving && allowed, animated && !disableAnimations);
    }

    private void tgx101DrawPlayed (Canvas c, float x, float y, float distance, int color) {
      final Paint paint = Paints.getProgressPaint(color, Screen.dp(3f));
      final float amplitude = Screen.dp(3f) * tgx101WaveFactor * (1f - seekDesireFactor);
      if (amplitude < Screen.dp(.25f) || distance < Screen.dp(8f)) {
        c.drawLine(x, y, x + distance, y, paint);
        return;
      }
      final int segments = Math.max(2, (int) Math.ceil(distance / Screen.dp(3f)));
      if (tgx101WaveLines == null || tgx101WaveLines.length < segments * 4) {
        tgx101WaveLines = new float[segments * 4];
      }
      final double frequency = Math.PI * 2 / Screen.dp(34f);
      final double phase = Math.PI * 2 * (android.os.SystemClock.uptimeMillis() % 1900L) / 1900.0;
      final float taper = Screen.dp(14f); // it grows out of the start and into the knob
      float lastX = x, lastY = y;
      for (int i = 1; i <= segments; i++) {
        float dx = distance * i / segments;
        float edge = Math.min(1f, Math.min(dx, distance - dx) / taper);
        float pointX = x + dx;
        float pointY = y + (float) Math.sin(dx * frequency - phase) * amplitude * edge;
        int at = (i - 1) * 4;
        tgx101WaveLines[at] = lastX;
        tgx101WaveLines[at + 1] = lastY;
        tgx101WaveLines[at + 2] = pointX;
        tgx101WaveLines[at + 3] = pointY;
        lastX = pointX;
        lastY = pointY;
      }
      c.drawLines(tgx101WaveLines, 0, segments * 4, paint);
      postInvalidateOnAnimation(); // nothing asks for the next frame when this view isn't drawn
    }

    private float seekDisplayProgress;
    private FactorAnimator seekDisplayAnimator;

    private void setSeekDisplayProgress (float factor, boolean animated) {
      if (animated) {
        if (seekDisplayAnimator == null) {
          seekDisplayAnimator = new FactorAnimator(ANIMATOR_SEEK_DISPLAY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 90l, this.seekDisplayProgress);
        }
        seekDisplayAnimator.animateTo(factor);
      } else {
        if (seekDisplayAnimator != null) {
          seekDisplayAnimator.forceFactor(factor);
        }
        setSeekDisplayProgress(factor);
      }
    }

    private float tgx101CoverDx;
    private android.animation.ValueAnimator tgx101CoverAnimator2;

    void tgx101DragCover (float dx) {
      if (tgx101CoverAnimator2 != null) tgx101CoverAnimator2.cancel();
      boolean next = dx < 0;
      // no track that way: it gives a little
      tgx101SetCoverDx(org.thunderdog.challegram.telegram.TdlibManager.instance().audio().tgx101CanSkip(next) ? dx : dx * .3f);
    }

    private void tgx101SetCoverDx (float dx) {
      if (tgx101CoverDx != dx) {
        tgx101CoverDx = dx;
        invalidate();
      }
    }

    private void tgx101AnimateCover (float to, long duration, @Nullable Runnable after) {
      if (tgx101CoverAnimator2 != null) tgx101CoverAnimator2.cancel();
      android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(tgx101CoverDx, to);
      animator.setDuration(duration);
      animator.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
      animator.addUpdateListener(a -> tgx101SetCoverDx((float) a.getAnimatedValue()));
      if (after != null) {
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
          private boolean cancelled;

          @Override
          public void onAnimationCancel (android.animation.Animator animation) {
            cancelled = true;
          }

          @Override
          public void onAnimationEnd (android.animation.Animator animation) {
            if (!cancelled) after.run();
          }
        });
      }
      tgx101CoverAnimator2 = animator;
      animator.start();
    }

    void tgx101ReleaseCover (float dx, float velocity, boolean apply) {
      final boolean next = dx < 0;
      final int width = getMeasuredWidth();
      final boolean far = Math.abs(dx) > width * .25f || (Math.abs(velocity) > Screen.dp(800f) && Math.signum(velocity) == Math.signum(dx));
      if (!apply || !far || width == 0 || !org.thunderdog.challegram.telegram.TdlibManager.instance().audio().tgx101CanSkip(next)) {
        tgx101AnimateCover(0f, 200L, null);
        return;
      }
      tgx101AnimateCover(Math.signum(dx) * width, 140L, () -> {
        controller.tdlib().context().player().skip(next);
        tgx101SetCoverDx(-Math.signum(dx) * width * .35f); // the next one comes in from the other side
        tgx101AnimateCover(0f, 220L, null);
      });
    }

    private void setSeekDisplayProgress (float progress) {
      if (this.seekDisplayProgress != progress) {
        this.seekDisplayProgress = progress;
        invalidate();
      }
    }

    private void setSeekDesireFactor (float factor) {
      if (this.seekDesireFactor != factor) {
        this.seekDesireFactor = factor;
        invalidate();
      }
    }

    private float desiredSeekPosition;

    private void setDesiredSeekPosition (float position) {
      if (this.desiredSeekPosition != position) {
        this.desiredSeekPosition = position;
        if (isSeeking) {
          setTextsDelayed();
        }
        if (seekDesireFactor > 0f) {
          invalidate();
        }
      }
    }

    private boolean textsSetDelayed;

    private void setTextsDelayed () {
      if (!textsSetDelayed) {
        textsSetDelayed = true;
        controller.getHandler().sendMessageDelayed(Message.obtain(controller.getHandler(), ACTION_SET_COVER_DURATIONS), 38l);
      }
    }

    private void setDurationTextsDelayed () {
      if (setDurationTexts()) {
        invalidate();
      }
      textsSetDelayed = false;
    }

    private float getSeekFactor () {
      float seekDoneProgress = calculatePositionProgress();
      if (seekDesireFactor == 0f) {
        return seekDoneProgress;
      }
      return seekDoneProgress + (desiredSeekPosition - seekDoneProgress) * seekDesireFactor;
    }

    private long calculateDesiredSeekPosition () {
      return (long) (Math.max(0, Math.min(durationMillis, (double) durationMillis * (double) desiredSeekPosition)));
    }

    private void setSeeking (boolean isSeeking, boolean apply) {
      if (this.isSeeking != isSeeking) {
        this.isSeeking = isSeeking;
        if (seekAnimator == null) {
          seekAnimator = new BoolAnimator(ANIMATOR_SEEK, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, !isSeeking);
        }
        getParent().requestDisallowInterceptTouchEvent(isSeeking);
        if (!isSeeking && apply) {
          long position = calculateDesiredSeekPosition();
          controller.tdlib().context().player().seekTrack(item.getMessage(), position);
          setDurations(position, durationMillis);
        }
        seekAnimator.setValue(isSeeking, true);
        if (setDurationTexts()) {
          invalidate();
        }
      }
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      final int viewWidth = getMeasuredWidth();

      // Values copy-paste from drawing
      final int overlayHeight = controller.getCurrentOverlayHeight();
      final int buttonsY = overlayHeight - Screen.dp(34f + TGX101_ROW_DP);
      final int durationsY = buttonsY - Screen.dp(30f);
      final int workingDistance = Math.min(viewWidth, Screen.dp(480f));
      final int startX = viewWidth != workingDistance ? viewWidth / 2 - workingDistance / 2 : 0;

      final int seekMargin = Screen.dp(12f);
      final int seekStartX = startX + seekMargin;
      final int seekDistance = workingDistance - seekMargin * 2;
      final int seekY = durationsY - Screen.dp(22f);

      final float x = e.getX();
      final float y = e.getY();

      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          int height = controller.getCurrentOverlayHeight();

          boolean isSeeking = y < height && durationMillis > 0;
          if (isSeeking) {
            float currentSeekX = seekStartX + seekDistance * getSeekFactor();
            isSeeking = U.isInsideAreaOf(currentSeekX, seekY, x, y, Screen.dp(22f));
          }

          if (isSeeking) {
            isSeeking = controller.tdlib().context().player().canSeekTrack(item.getMessage());
          }

          if (isSeeking) {
            setDesiredSeekPosition(MathUtils.clamp((x - seekStartX) / (float) seekDistance));
          }

          setSeeking(isSeeking, false);
          tgx101PullX = x;
          tgx101PullStartY = tgx101PullLastY = y;
          tgx101Pulling = false;
          tgx101FreeTap = !isSeeking && controller.tgx101ListOpened();
          tgx101DurationTap = !isSeeking && y >= durationsY - Screen.dp(20f) && y <= durationsY + Screen.dp(10f) &&
            x >= startX + workingDistance - durationWidth - Screen.dp(24f) && x <= startX + workingDistance;
          if (tgx101DurationTap) return true;
          return isSeeking || (y < height && (isCollapsed || tgx101FreeTap || controller.recyclerView.findChildViewUnder(x, y) != null));
        }
        case MotionEvent.ACTION_MOVE: {
          if (isSeeking) {
            setDesiredSeekPosition(controller.tdlib().context().player().normalizeSeekPosition(durationMillis, MathUtils.clamp(((x - seekStartX) / (float) seekDistance))));
          }
          if (Math.hypot(x - tgx101PullX, y - tgx101PullStartY) > Screen.getTouchSlop()) {
            tgx101FreeTap = false;
          }
          if (!isSeeking && !tgx101Pulling && controller.tgx101ListOpened() && Math.abs(y - tgx101PullStartY) > tgx101PullSlop() && Math.abs(y - tgx101PullStartY) > Math.abs(x - tgx101PullX)) {
            tgx101Pulling = true; // down over the player: the list goes down with the finger, letting go brings the cover
            tgx101DurationTap = false;
            tgx101PullLastY = y;
            controller.tgx101BeginPull();
            getParent().requestDisallowInterceptTouchEvent(true);
          }
          if (tgx101Pulling) {
            controller.tgx101PullList(y - tgx101PullLastY);
            tgx101PullLastY = y;
            return true;
          }
          return isSeeking;
        }
        case MotionEvent.ACTION_UP: {
          if (tgx101Pulling) {
            tgx101Pulling = false;
            controller.tgx101EndPull(y - tgx101PullStartY);
            return true;
          }
          if (tgx101DurationTap) {
            tgx101DurationTap = false;
            org.thunderdog.challegram.unsorted.Settings.instance().setTgx101PlayerShowsTotal(!tgx101ShowsTotal());
            if (setDurationTexts()) invalidate();
            return true;
          }
          if (tgx101FreeTap) { // (user 2026-11-05) a tap on a free place of the player brings the cover down too
            tgx101FreeTap = false;
            controller.tgx101ReturnToCover();
            return true;
          }
          if (isSeeking) {
            setSeeking(false, true);
            return true;
          }
          return false;
        }
        case MotionEvent.ACTION_CANCEL: {
          tgx101DurationTap = false;
          if (tgx101Pulling) controller.tgx101EndPull(0f);
          tgx101Pulling = false;
          tgx101FreeTap = false;
          if (isSeeking) {
            setSeeking(false, false);
            return true;
          }
          return false;
        }
      }
      return false;
    }


    // Impl

    private String title, subtitle;
    private Text trimmedTitle, trimmedSubtitle;

    private boolean setTitle (String title) {
      if (!StringUtils.equalsOrBothEmpty(this.title, title)) {
        this.title = title;
        trimTitle();
        return true;
      }
      return false;
    }

    private boolean setSubtitle (String subtitle) {
      if (!StringUtils.equalsOrBothEmpty(this.subtitle, subtitle)) {
        this.subtitle = subtitle;
        trimSubtitle();
        return true;
      }
      return false;
    }

    private void trimTitle () {
      int availWidth = getAvailWidth();
      this.trimmedTitle = StringUtils.isEmpty(title) ? null : new Text.Builder(title, availWidth, Paints.getTitleStyleProvider(), TextColorSets.Regular.NORMAL).allBold().singleLine().build();
    }

    private void trimSubtitle () {
      int availWidth = getAvailWidth();
      this.trimmedSubtitle = StringUtils.isEmpty(subtitle) ? null : new Text.Builder(subtitle, availWidth, Paints.getSubtitleStyleProvider(), TextColorSets.Regular.LIGHT).singleLine().build();
    }

    private int lastWidth;

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      int width = getMeasuredWidth();
      if (lastWidth != width) {
        lastWidth = width;
        trimTitle();
        trimSubtitle();
      }
    }

    private float collapseFactor;
    private boolean isCollapsed;
    private FactorAnimator collapseAnimator;
    private static final int ANIMATOR_COLLAPSE = 1;

    private void setCollapsed (boolean isCollapsed, boolean animated) {
      animated = animated && !disableAnimations && controller.isScrollUnlocked;
      if (this.isCollapsed != isCollapsed || !animated) {
        this.isCollapsed = isCollapsed;
        final float toFactor = isCollapsed ? 1f : 0f;
        if (animated) {
          if (collapseAnimator == null) {
            collapseAnimator = new FactorAnimator(ANIMATOR_COLLAPSE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220l, this.collapseFactor);
          }
          collapseAnimator.animateTo(toFactor);
        } else {
          if (collapseAnimator != null) {
            collapseAnimator.forceFactor(toFactor);
          }
          setCollapseFactor(toFactor);
        }
      }
    }

    private float getActualCollapseFactor () {
      return collapseFactor;
    }

    private void setCollapseFactor (float factor) {
      if (this.collapseFactor != factor) {
        this.collapseFactor = factor;
        controller.updateButtonColors();
        controller.tgx101MatchStatusIcons();
        invalidate();
      }
    }

    private String positionStr, durationStr;
    private float durationWidth;
    private long positionMillis = -1, durationMillis = -1;
    private int positionSeconds = -1, durationSeconds = -1;

    private float fileProgress, fileOffset;
    private FactorAnimator progressAnimator, offsetAnimator;
    private static final int ANIMATOR_PROGRESS = 0, ANIMATOR_PROGRESS_PREFIX = 6;

    private void setOffsetFactor (float factor) {
      if (this.fileOffset != factor) {
        this.fileOffset = factor;
        invalidate();
      }
    }

    private void setProgressFactor (float factor) {
      if (this.fileProgress != factor) {
        this.fileProgress = factor;
        invalidate();
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      switch (id) {
        case ANIMATOR_PROGRESS: {
          setProgressFactor(factor);
          break;
        }
        case ANIMATOR_COLLAPSE: {
          setCollapseFactor(factor);
          break;
        }
        case ANIMATOR_SEEK: {
          setSeekDesireFactor(factor);
          break;
        }
        case ANIMATOR_SEEK_DISPLAY: {
          setSeekDisplayProgress(factor);
          break;
        }
        case ANIMATOR_BUFFERING: {
          setBufferFactor(factor);
          break;
        }
        case ANIMATOR_PROGRESS_PREFIX: {
          setOffsetFactor(factor);
          break;
        }
        case ANIMATOR_WAVE: {
          tgx101WaveFactor = factor;
          invalidate();
          break;
        }
        case ANIMATOR_TEXT_SWAP: {
          tgx101TextSwap = factor;
          invalidate();
          break;
        }
        case ANIMATOR_COVER_SWAP: {
          tgx101CoverFade = factor;
          invalidate();
          break;
        }
      }
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      if (id == ANIMATOR_TEXT_SWAP && finalFactor == 1f) {
        tgx101OldTitle = tgx101OldSubtitle = null;
      } else if (id == ANIMATOR_COVER_SWAP && finalFactor == 1f) {
        tgx101FinishCoverSwap();
      }
    }

    public void setFileProgress (float offset, float progress, boolean animated) {
      progress += offset;
      if (animated) {
        long duration;

        if (progressAnimator == null || progressAnimator.getToFactor() != progress) {
          duration = progress > this.fileProgress ? 160l : 120l;
          if (progressAnimator == null) {
            progressAnimator = new FactorAnimator(ANIMATOR_PROGRESS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, duration, this.fileProgress);
          } else {
            progressAnimator.setDuration(duration);
          }
          progressAnimator.animateTo(progress);
        }

        if (offsetAnimator == null || offsetAnimator.getToFactor() != offset) {
          duration = offset > this.fileOffset ? 160l : 120l;
          if (offsetAnimator == null) {
            offsetAnimator = new FactorAnimator(ANIMATOR_PROGRESS_PREFIX, this, AnimatorUtils.DECELERATE_INTERPOLATOR, duration, this.fileOffset);
          } else {
            offsetAnimator.setDuration(duration);
          }
          offsetAnimator.animateTo(offset);
        }
      } else {
        if (progressAnimator != null)
          progressAnimator.forceFactor(progress);
        setProgressFactor(progress);
        if (offsetAnimator != null)
          offsetAnimator.forceFactor(offset);
        setOffsetFactor(offset);
      }
    }

    private boolean setPosition (int seconds) {
      if (positionSeconds != seconds) {
        this.positionSeconds = seconds;
        this.positionStr = Strings.buildDuration(seconds);
        return true;
      }
      return false;
    }

    // (user 2026-11-04, player mockup) a tap on the time on the right: how much is left, or the whole length
    private boolean tgx101DurationTap, tgx101DurationShownTotal;
    private boolean tgx101Pulling, tgx101FreeTap;
    private float tgx101PullX, tgx101PullStartY, tgx101PullLastY;

    private static boolean tgx101ShowsTotal () {
      return org.thunderdog.challegram.unsorted.Settings.instance().tgx101PlayerShowsTotal();
    }

    private boolean setDuration (int seconds) {
      boolean total = tgx101ShowsTotal();
      if (this.durationSeconds != seconds || this.tgx101DurationShownTotal != total) {
        this.durationSeconds = seconds;
        this.tgx101DurationShownTotal = total;
        StringBuilder b = new StringBuilder(5);
        if (!total) b.append('-');
        Strings.buildDuration(durationSeconds, TimeUnit.SECONDS, false, b);
        String str = b.toString();
        this.durationStr = str;
        this.durationWidth = U.measureText(str, Paints.getRegularTextPaint(12f));
        return true;
      }
      return false;
    }

    public void setDurations (long position, long duration) {
      if (item == null) {
        return;
      }
      if (duration == -1) {
        duration = item.getTrackAudio().duration * 1000l;
      }
      if (position == -1) {
        position = 0;
      }
      if (setDurationsImpl(position, duration)) {
        invalidate();
      }
    }

    private boolean setDurationTexts () {
      long position, duration = this.durationMillis;

      if (isSeeking) {
        position = calculateDesiredSeekPosition();
      } else {
        position = this.positionMillis;
      }

      int positionSeconds = Math.max(0, (int) Math.floor((double) position / 1000.0));
      int totalSeconds = Math.max(positionSeconds, (int) Math.floor((double) duration / 1000.0));
      int durationSeconds = totalSeconds - positionSeconds;

      boolean changed;
      changed = setPosition(positionSeconds);
      changed = setDuration(tgx101ShowsTotal() ? totalSeconds : durationSeconds) || changed;

      return changed;
    }

    private boolean setDurationsImpl (long position, long duration) {
      if (this.positionMillis != position || this.durationMillis != duration) {
        this.positionMillis = position;
        this.durationMillis = duration;

        if (!isSeeking) {
          setDurationTexts();
        }

        float newSeek = calculatePositionProgress();

        setSeekDisplayProgress(newSeek, false);

        return true;
      }
      return false;
    }

    private InlineResultCommon item;
    private MediaPreview mediaPreview;

    @Override
    public boolean invalidateContent (Object cause) {
      requestFiles(true);
      return true;
    }

    public void setItem (InlineResultCommon item) {
      if (this.item == item) {
        return;
      }
      if (this.item != null) {
        this.item.detachFromView(this);
      }
      final boolean swap = this.item != null && item != null && !disableAnimations;
      this.item = item;
      setSeeking(false, false);
      if (swap) {
        tgx101PrepareTextSwap();
        tgx101PrepareCoverSwap();
      } else {
        tgx101FinishCoverSwap();
      }
      if (item != null) {
        item.attachToView(this);
        boolean changed;
        changed = setTitle(item.getTrackTitle());
        changed = setSubtitle(item.getTrackSubtitle()) || changed;
        long progressMillis = controller.tdlib().context().player().getPlayPosition(item.tdlib(), item.getMessage());
        long durationMillis = controller.tdlib().context().player().getPlayDuration(item.tdlib(), item.getMessage());
        if (durationMillis == -1) {
          durationMillis = item.getTrackAudio().duration * 1000l;
        }
        if (progressMillis == -1) {
          progressMillis = 0;
        }
        changed = setDurationsImpl(progressMillis, durationMillis) || changed;
        if (changed) {
          invalidate();
        }
      } else {
        boolean changed;
        changed = setTitle(null);
        changed = setSubtitle(null) || changed;
        changed = setDurationsImpl(0, 0) || changed;
        if (changed) {
          invalidate();
        }
      }
      requestFiles(false);
    }

    private void requestFiles (boolean invalidate) {
      this.mediaPreview = item != null ? item.getMediaPreview() : null;
      if (item == null) {
        preview.clear();
        receiver.clear();
        source.clear();
        tgx101FinishCoverSwap();
        return;
      }
      if (!invalidate) {
        if (mediaPreview != null) {
          mediaPreview.requestFiles(preview, false);
        } else {
          preview.clear();
        }

        ImageApicFile bigFile = new ImageApicFile(item.tdlib(), item.getMessage());
        bigFile.setNoCache();
        bigFile.setSize(Screen.smallestSide());
        bigFile.setScaleType(ImageFile.CENTER_CROP);
        source.requestFile(bigFile);
      }

      /*TODO
      ImageFile fullPreview = item.getFullPreview();
      if (fullPreview != null) {
        ImageMp3File smallFile = new ImageMp3File(fullPreview.getFilePath());
        smallFile.setScaleType(ImageFile.CENTER_CROP);
        smallFile.setSize(fullPreview.getSize());
        smallFile.setCacheOnly();
        receiver.requestFile(smallFile);
      } else {
        receiver.requestFile(null);
      }*/
    }

    // (user 2026-11-04, player mockup) the next track doesn't jump in: the old title leaves upwards as the new one comes from below,
    // the old cover stays over the new one until that is ready and then fades away
    private static final int ANIMATOR_TEXT_SWAP = 8, ANIMATOR_COVER_SWAP = 9;
    private Text tgx101OldTitle, tgx101OldSubtitle;
    private float tgx101TextSwap = 1f;
    private FactorAnimator tgx101TextSwapAnimator;

    private void tgx101PrepareTextSwap () {
      tgx101OldTitle = trimmedTitle;
      tgx101OldSubtitle = trimmedSubtitle;
      if (tgx101TextSwapAnimator == null) {
        tgx101TextSwapAnimator = new FactorAnimator(ANIMATOR_TEXT_SWAP, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 260L);
      }
      tgx101TextSwap = 0f;
      tgx101TextSwapAnimator.forceFactor(0f);
      tgx101TextSwapAnimator.animateTo(1f);
    }

    private final Paint tgx101ScrimPaint = new Paint(Paint.DITHER_FLAG);
    private int tgx101ScrimHeight;
    private boolean tgx101CoverSwapping, tgx101CoverFading;
    private long tgx101CoverSwapStart;
    private float tgx101CoverFade;
    private FactorAnimator tgx101CoverAnimator;
    private static final long TGX101_COVER_WAIT = 320L;

    private void tgx101PrepareCoverSwap () {
      tgx101FinishCoverSwap();
      if (source.needPlaceholder()) {
        return; // no big picture to keep; the new one fades in by itself
      }
      ImageReceiver old = source;
      source = tgx101OldSource;
      tgx101OldSource = old;
      source.setAnimationDisabled(true); // it appears whole under the old one
      tgx101CoverSwapping = true;
      tgx101CoverSwapStart = android.os.SystemClock.uptimeMillis();
      postInvalidateDelayed(TGX101_COVER_WAIT);
    }

    private void tgx101FinishCoverSwap () {
      if (tgx101CoverAnimator != null) {
        tgx101CoverAnimator.forceFactor(0f);
      }
      tgx101CoverFade = 0f;
      tgx101CoverFading = false;
      if (tgx101CoverSwapping) {
        tgx101CoverSwapping = false;
        source.setAnimationDisabled(disableAnimations);
        tgx101OldSource.clear();
        invalidate();
      }
    }

    private void tgx101DrawOldCover (Canvas c, int x, int y, int width, int height, int radius) {
      if (!tgx101CoverSwapping) {
        return;
      }
      if (!tgx101CoverFading && (!source.needPlaceholder() || android.os.SystemClock.uptimeMillis() - tgx101CoverSwapStart >= TGX101_COVER_WAIT)) {
        tgx101CoverFading = true;
        if (tgx101CoverAnimator == null) {
          tgx101CoverAnimator = new FactorAnimator(ANIMATOR_COVER_SWAP, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 280L);
        }
        tgx101CoverAnimator.animateTo(1f);
      }
      tgx101OldSource.setRadius(radius);
      tgx101OldSource.setBounds(x, y, x + width, y + height);
      tgx101OldSource.setPaintAlpha(1f - tgx101CoverFade);
      tgx101OldSource.draw(c);
      tgx101OldSource.restorePaintAlpha();
    }

    private int getAvailWidth () {
      final int coverLeft = Screen.dp(58f);
      final int coverWidth = Screen.dp(50f);
      final int textMargin = Screen.dp(13f);
      return getMeasuredWidth() - (coverLeft + coverWidth + textMargin + Screen.dp(30));
    }

    private float bufferingFactor;
    private BoolAnimator bufferAnimator;
    private static final int ANIMATOR_BUFFERING = 5;

    private void setBufferFactor (float factor) {
      if (this.bufferingFactor != factor) {
        this.bufferingFactor = factor;
        this.progress.setAlpha(bufferingFactor);
        invalidate();
      }
    }

    private void setIsBuffering (boolean isBuffering) {
      if (true) {
        return;
      }
      if (bufferAnimator == null) {
        bufferAnimator = new BoolAnimator(ANIMATOR_BUFFERING, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      bufferAnimator.setStartDelay(isBuffering ? 1000l : 0);
      bufferAnimator.setValue(isBuffering, true);
    }

    private int getTitleWidth () {
      return trimmedTitle != null ? trimmedTitle.getWidth() : 0;
    }

    private int getSubtitleWidth () {
      return trimmedSubtitle != null ? trimmedSubtitle.getWidth() : 0;
    }

    @Override
    protected void onDraw (Canvas c) {
      if (item == null) {
        return;
      }

      final int viewWidth = getMeasuredWidth();

      // Values
      final int overlayHeight = controller.getCurrentOverlayHeight();
      final int buttonsY = overlayHeight - Screen.dp(34f + TGX101_ROW_DP);
      final int durationsY = buttonsY - Screen.dp(30f);
      final int workingDistance = Math.min(viewWidth, Screen.dp(480f));
      final int startX = viewWidth != workingDistance ? viewWidth / 2 - workingDistance / 2 : 0;

      final int seekMargin = Screen.dp(12f);
      final int seekX = startX + seekMargin;
      final int seekDistance = workingDistance - seekMargin * 2;
      final int seekY = durationsY - Screen.dp(22f);

      final int durationsMargin = Screen.dp(10f);

      final float collapseFactor = getActualCollapseFactor();

      float coverFactor = 1f - collapseFactor;
      final int coverLeft = Screen.dp(58f);
      final int coverWidth = Screen.dp(50f);
      final int coverY = HeaderView.getTopOffset() + Screen.dp(5f);
      final int coverHeight = overlayHeight - getMinimumOverlayHeight();

      final int textStartX = coverLeft + coverWidth + Screen.dp(13f);
      final int textStartY = coverY + Screen.dp(19f);
      final int textStartY2 = coverY + Screen.dp(5f);

      final int textEndCenterX = viewWidth / 2;
      final int textEndY =  buttonsY - Screen.dp(98f);

      // Title & subtitle

      final float textY = textStartY + (textEndY - textStartY) * coverFactor;
      final float textY2 = textStartY2 + (textEndY - textStartY) * coverFactor;
      final float titleX = textStartX + ((textEndCenterX - getTitleWidth() / 2) - textStartX) * coverFactor;
      final float subtitleX = textStartX + ((textEndCenterX - getSubtitleWidth() / 2) - textStartX) * coverFactor;

      final boolean coverMoved = tgx101CoverDx != 0f;
      if (coverMoved) {
        c.save();
        c.translate(tgx101CoverDx, 0f);
      }
      final float textSwap = tgx101TextSwap;
      final int swapDistance = Screen.dp(10f);
      if (textSwap < 1f) {
        final int oldY = (int) textY2 - (int) (swapDistance * textSwap);
        if (tgx101OldTitle != null) {
          int oldX = (int) (textStartX + ((textEndCenterX - tgx101OldTitle.getWidth() / 2) - textStartX) * coverFactor);
          tgx101OldTitle.draw(c, oldX, oldY, null, 1f - textSwap);
        }
        if (tgx101OldSubtitle != null) {
          int oldX = (int) (textStartX + ((textEndCenterX - tgx101OldSubtitle.getWidth() / 2) - textStartX) * coverFactor);
          tgx101OldSubtitle.draw(c, oldX, oldY + Screen.dp(24f), null, 1f - textSwap);
        }
      }
      final int newY = (int) textY2 + (int) (swapDistance * (1f - textSwap));
      if (trimmedTitle != null) {
        trimmedTitle.draw(c, (int) titleX, newY, null, textSwap);
      }
      if (trimmedSubtitle != null) {
        trimmedSubtitle.draw(c, (int) subtitleX, newY + Screen.dp(24f), null, textSwap);
      }
      if (coverMoved) {
        c.restore();
      }

      // Seekbar

      int color = Theme.textDecentColor();
      c.drawText(positionStr, startX + durationsMargin, durationsY, Paints.getRegularTextPaint(12f, color));
      c.drawText(durationStr, startX + workingDistance - durationWidth - durationsMargin, durationsY, Paints.getRegularTextPaint(12f, color));

      // final float seekDoneProgress = getDoneProgress();
      final float seekDistanceDone = seekDistance * seekDisplayProgress;
      final float seekDistanceReadyStart = seekDistance * fileOffset;
      final float seekDistanceReadyEnd = seekDistance * fileProgress;
      final int seekStroke = Screen.dp(2f);
      int doneColor = controller.tgx101Tint.accent(Theme.getColor(ColorId.seekDone));
      if (seekDistanceReadyStart > 0 || seekDistanceReadyEnd < seekDistance) {
        c.drawLine(seekX, seekY, seekX + seekDistance, seekY, Paints.getProgressPaint(controller.tgx101Tint.accentShade(Theme.getColor(ColorId.seekEmpty), .2f), seekStroke));
      }
      c.drawLine(seekX + seekDistanceReadyStart, seekY, seekX + seekDistanceReadyEnd, seekY, Paints.getProgressPaint(controller.tgx101Tint.accentShade(Theme.getColor(ColorId.seekReady), .36f), seekStroke));
      tgx101DrawPlayed(c, seekX, seekY, seekDistanceDone, doneColor);
      float seekCenterX = seekX + seekDistance * getSeekFactor();
      c.drawCircle(seekCenterX, seekY, Screen.dp(6f) + Screen.dp(4f) * seekDesireFactor, Paints.fillingPaint(doneColor));
      if (bufferingFactor > 0f) {
        int radius = Screen.dp(4f);
        progress.setBounds((int) seekCenterX - radius, seekY - radius, (int) seekCenterX + radius, seekY + radius);
        progress.draw(c);
      }

      // Cover image

      if (coverMoved) {
        c.save();
        c.translate(tgx101CoverDx, 0f);
      }
      int x = (int) ((float) coverLeft * (1f - coverFactor));
      int y = (int) ((float) coverY * (1f - coverFactor));
      int width = coverWidth + (int) ((float) (viewWidth - coverWidth) * coverFactor);
      int height = coverWidth + (int) ((float) (coverHeight - coverWidth) * coverFactor);

      int radius = (int) (Screen.dp(4f) * (1f - coverFactor));

      boolean ignoreSource = coverFactor < .5f;

      if (source.needPlaceholder() || ignoreSource) {
        receiver.setRadius(radius);
        receiver.setBounds(x, y, x + width, y + height);
        if (receiver.needPlaceholder()) {
          if (mediaPreview == null || mediaPreview.needPlaceholder(preview)) {
            if (radius == 0) {
              c.drawRect(x, y, x + width, y + height, Paints.fillingPaint(Theme.getColor(ColorId.playerCoverPlaceholder)));
            } else {
              RectF rectF = Paints.getRectF();
              rectF.set(x, y, x + width, y + height);
              c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(Theme.getColor(ColorId.playerCoverPlaceholder)));
            }
            c.save();
            c.clipRect(x, y, x + width, y + height);
            int cx = receiver.centerX();
            int cy = receiver.centerY();
            float scale = Math.max((float) receiver.getWidth() / (float) getMeasuredWidth(), (float) receiver.getHeight() / (float) getMeasuredHeight()) * (2f + 1.5f * collapseFactor);
            if (scale != 1f) {
              c.scale(scale, scale, cx, cy);
            }
            Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2, Paints.getNotePorterDuffPaint());
            c.restore();
          }
          if (mediaPreview != null) {
            mediaPreview.draw(this, c, preview, x, y, width, height, radius, 1f);
          }
        }
        receiver.draw(c);
      }
      source.setRadius(radius);
      source.setBounds(x, y, x + width, y + height);
      source.draw(c);
      tgx101DrawOldCover(c, x, y, width, height, radius);
      if (coverMoved) {
        c.restore();
      }
      // (user 2026-11-05, screenshots «не видны кнопки вверху и строка состояния, когда обложка светлая»): white on any cover
      if (coverFactor > 0f) {
        final int scrimHeight = Math.min(height, HeaderView.getTopOffset() + HeaderView.getSize(false) + Screen.dp(28f));
        if (tgx101ScrimHeight != scrimHeight) {
          tgx101ScrimHeight = scrimHeight;
          tgx101ScrimPaint.setShader(new LinearGradient(0, 0, 0, scrimHeight, 0x80000000, 0x00000000, Shader.TileMode.CLAMP));
        }
        tgx101ScrimPaint.setAlpha((int) (255f * coverFactor));
        c.save();
        c.translate(x, y);
        c.drawRect(0, 0, width, scrimHeight, tgx101ScrimPaint);
        c.restore();
      }

      int shadowAlpha = (int) (255f * coverFactor);
      if (coverFactor > 0f) {
        topShadowPaint.setAlpha((int) (shadowAlpha * .4f));
        c.save();
        int shadowHeight = ShadowView.simpleTopShadowHeight();
        c.clipRect(x, y, x + width, y + height);
        int fromY = y + height - shadowHeight;
        c.translate(x, fromY);
        c.drawRect(0, 0, width, shadowHeight, topShadowPaint);

        int radialRadius = getRadialGradientRadius();
        int leftX = Screen.dp(56f) / 2 - radialRadius;
        int rightX = viewWidth - Screen.dp(49f) / 2 - radialRadius;

        radialShadowPaint.setAlpha(shadowAlpha);

        c.translate(leftX - x, (HeaderView.getTopOffset() + HeaderView.getSize(false) / 2 - radialRadius) - fromY);
        c.drawRect(0, 0, radialRadius * 2, radialRadius * 2, radialShadowPaint);
        c.translate(rightX - leftX, 0);
        c.drawRect(0, 0, radialRadius * 2, radialRadius * 2, radialShadowPaint);

        c.restore();
      }

      if (radius > 0) {
        RectF rectF = Paints.getRectF();
        int strokeSize = radius / 2;
        rectF.set(x - strokeSize / 2, y - strokeSize / 2, x + width + strokeSize / 2, y + height + strokeSize / 2);
        c.drawRoundRect(rectF, radius, radius, Paints.getProgressPaint(controller.tgx101Tint.background(Theme.fillingColor()), strokeSize));
      }

      // Buttons

      int buttonsDistance = workingDistance;

      int cx = viewWidth / 2 - buttonsDistance / 2;
      int perx = buttonsDistance / 5;
      cx += perx / 6;
      buttonsDistance -= (perx / 6) * 2;
      perx = buttonsDistance / 5;

      for (int i = 0; i < 5; i++) {
        View view;
        switch (i) {
          case 0: view = controller.shuffleButton; break;
          case 1: view = controller.prevButton; break;
          case 2: view = controller.playPauseButton; break;
          case 3: view = controller.nextButton; break;
          case 4: view = controller.repeatButton; break;
          default: throw new IllegalArgumentException("i == " + i);
        }
        int buttonX = cx + perx / 2 - view.getMeasuredWidth() / 2;
        int buttonY = buttonsY - view.getMeasuredHeight() / 2;
        view.setTranslationX(buttonX);
        view.setTranslationY(buttonY);
        cx += perx;
      }
      controller.tgx101TintButton(controller.shuffleButton, controller.isShuffleActive());
      controller.tgx101TintButton(controller.repeatButton, controller.isRepeatActive());
      controller.tgx101TintButton(controller.tgx101Heart, controller.currentItem != null && controller.tgx101Taste().isLiked(controller.currentItem.getMessage()));
      controller.tgx101TintButton(controller.tgx101Dislike, controller.currentItem != null && controller.tgx101Taste().isDisliked(controller.currentItem.getMessage()));
      float rowY = overlayHeight - Screen.dp(TGX101_ROW_DP) / 2f - controller.tgx101SpeedPill.getMeasuredHeight() / 2f - Screen.dp(4f);
      controller.tgx101SpeedPill.setTranslationY(rowY);
      controller.tgx101NextPill.setTranslationY(rowY);
      controller.tgx101LyricsPill.setTranslationY(rowY);
      // beside the title of the big cover; the collapsed header has its own ⋮ there
      View heart = controller.tgx101Heart;
      heart.setTranslationX(startX + workingDistance - heart.getMeasuredWidth());
      heart.setTranslationY(buttonsY - Screen.dp(98f) + Screen.dp(11f) - heart.getMeasuredHeight() / 2f);
      float heartAlpha = coverFactor;
      heart.setAlpha(heartAlpha);
      heart.setEnabled(heartAlpha > .5f);
      // 👎 across the title from it
      View dislike = controller.tgx101Dislike;
      dislike.setTranslationX(startX);
      dislike.setTranslationY(heart.getTranslationY());
      dislike.setAlpha(heartAlpha);
      dislike.setEnabled(heartAlpha > .5f);
    }
  }

  private static class PlayOverlayLayout extends FrameLayoutFix {
    private PlaybackController controller;

    public PlayOverlayLayout (@NonNull Context context) {
      super(context);
    }

    public void initWithController (PlaybackController controller) {
      this.controller = controller;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int availableHeight = controller.getAvailableOverlayHeight(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.EXACTLY));
    }
  }

  private static class PlayListRecyclerView extends RecyclerView {
    public PlayListRecyclerView (Context context) {
      super(context);
    }

    private PlaybackController controller;

    public void initWithController (final PlaybackController controller) {
      this.controller = controller;
      addOnScrollListener(new OnScrollListener() {
        private boolean scrollingByTouch;

        @Override
        public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
          scrollingByTouch = newState == RecyclerView.SCROLL_STATE_DRAGGING;
        }

        @Override
        public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
          if (dy != 0 && scrollingByTouch) {
            controller.unlockScroll();
          }
          controller.checkScrolling(dx == 0 && dy == 0);
        }
      });
    }

    // (user 2026-11-05 «свайпы влево и вправо по обложке пускай меняют треки»): to a side over the big cover — the next track (left) or
    // the previous (right), the cover follows the finger; up and down stay the list's and the screen's
    private static final int COVER_NONE = 0, COVER_WATCHING = 1, COVER_DRAGGING = 2;
    private int coverMode;
    private float coverX, coverY;
    private android.view.VelocityTracker coverVelocity;

    /** True while the cover has the finger */
    private boolean trackCover (MotionEvent e) {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          coverMode = controller.tgx101CoverSwipeAllowed(e.getY()) ? COVER_WATCHING : COVER_NONE;
          coverX = e.getX();
          coverY = e.getY();
          if (coverVelocity == null) coverVelocity = android.view.VelocityTracker.obtain();
          coverVelocity.clear();
          coverVelocity.addMovement(e);
          return false;
        }
        case MotionEvent.ACTION_MOVE: {
          if (coverMode == COVER_NONE) return false;
          coverVelocity.addMovement(e);
          float dx = e.getX() - coverX, dy = e.getY() - coverY;
          if (coverMode == COVER_WATCHING && Math.hypot(dx, dy) > Screen.getTouchSlop()) {
            if (Math.abs(dx) > Math.abs(dy) * 1.3f) {
              coverMode = COVER_DRAGGING;
              coverX = e.getX(); // no jump: from here
              if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            } else {
              coverMode = COVER_NONE;
            }
          }
          if (coverMode == COVER_DRAGGING) {
            controller.coverView.tgx101DragCover(e.getX() - coverX);
            return true;
          }
          return false;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          if (coverMode == COVER_DRAGGING) {
            coverMode = COVER_NONE;
            float velocity = 0f;
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
              coverVelocity.addMovement(e);
              coverVelocity.computeCurrentVelocity(1000);
              velocity = coverVelocity.getXVelocity();
            }
            controller.coverView.tgx101ReleaseCover(e.getX() - coverX, velocity, e.getActionMasked() == MotionEvent.ACTION_UP);
            return true;
          }
          coverMode = COVER_NONE;
          return false;
        }
      }
      return coverMode == COVER_DRAGGING;
    }

    @Override
    public boolean onInterceptTouchEvent (MotionEvent e) {
      if (trackCover(e)) {
        return true;
      }
      if (e.getAction() == MotionEvent.ACTION_DOWN) {
        if (getAlpha() == 0f) {
          return true;
        }
        if (!controller.isScrollUnlocked && e.getY() <= controller.getCurrentOverlayHeight()) {
          return true;
        }
      }
      return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (coverMode == COVER_DRAGGING || (coverMode == COVER_WATCHING && e.getActionMasked() != MotionEvent.ACTION_DOWN)) {
        boolean wasDragging = coverMode == COVER_DRAGGING;
        if (trackCover(e)) {
          if (!wasDragging) {
            MotionEvent cancel = MotionEvent.obtain(e);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(cancel); // the list's own scroll lets go
            cancel.recycle();
          }
          return true;
        }
      }
      return !(getAlpha() == 0f && e.getAction() == MotionEvent.ACTION_DOWN) && super.onTouchEvent(e);
    }

    private int oldHeight;

    @Override
    protected void onMeasure (int widthSpec, int heightSpec) {
      super.onMeasure(widthSpec, heightSpec);
      int height = getMeasuredHeight();
      if (oldHeight != height) {
        if (!controller.isScrollUnlocked) {
          controller.dispatchEnsureScroll(false);
        }
        if (oldHeight != 0) {
          invalidateItemDecorations();
          if (!controller.mayExpandOverlay(getMeasuredWidth(), getMeasuredHeight())) {
            controller.coverView.setCollapsed(true, false);
          } else {
            controller.dispatchCheckScrolling(true);
          }
        } else {
          if (!controller.mayExpandOverlay(getMeasuredWidth(), getMeasuredHeight())) {
            controller.coverView.setCollapsed(true, false);
          }
        }
        oldHeight = height;
      }
    }
  }

  // ViewController

  // (user 2026-11-05, screenshot «в таком положении тоже не читается строка»): over the cover the icons are white on its shade; with the
  // cover gone up they stand on the player's tone, which may be light

  @Override
  public Boolean tgx101LightStatusBar () {
    if (coverView == null || coverView.getActualCollapseFactor() < .5f) return false;
    int ground = tgx101Tint.background(Theme.fillingColor());
    return (0.299f * android.graphics.Color.red(ground) + 0.587f * android.graphics.Color.green(ground) + 0.114f * android.graphics.Color.blue(ground)) / 255f > .6f;
  }

  private Boolean tgx101StatusIconsDark;

  private void tgx101MatchStatusIcons () {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || !isFocused()) return;
    Boolean dark = tgx101LightStatusBar();
    if (dark.equals(tgx101StatusIconsDark)) return;
    tgx101StatusIconsDark = dark;
    UI.setLightSystemBars(context().getWindow(), !Theme.isDark(), dark, 0, false);
  }

  @Override
  public void onBlur () {
    super.onBlur();
    tgx101StatusIconsDark = null;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
      UI.setLightSystemBars(context().getWindow(), !Theme.isDark(), Theme.needLightStatusBar(), 0, false);
    }
  }

  @Override
  public void onFocus () {
    super.onFocus();
    tgx101StatusIconsDark = null;
    tgx101MatchStatusIcons();
    coverView.setDisableAnimation(false);
    destroyAllStackItemsById(R.id.controller_playback);
  }

  @Override
  public boolean usePopupMode () {
    return true;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  public void updateButtonColors () {
    if (headerView != null && coverView != null) {
      headerView.updateButtonColorFactor(this, getMenuId(), coverView.getActualCollapseFactor());
      headerView.updateBackButtonColor(this, getHeaderIconColor());
    }
  }

  @Override
  protected int getHeaderIconColor () {
    return ColorUtils.fromToArgb(0xffffffff, Theme.getColor(ColorId.headerLightIcon), coverView.getActualCollapseFactor());
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_player;
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (headerView != null && coverView != null) {
      headerView.updateButtonColorFactor(this, getMenuId(), coverView.getActualCollapseFactor());
    }
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_player) {
      header.addMoreButton(menu, this, ColorId.NONE)
        .setThemeColorId(ColorId.white, ColorId.headerLightIcon, coverView.getActualCollapseFactor());
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_more) {
      IntList ids = new IntList(3);
      StringList strings = new StringList(3);

      TdApi.Message message = currentItem.getMessage();

      TdApi.File file = TD.getFile(message);
      if (TD.isFileLoaded(file)) {
        ids.append(R.id.btn_saveFile);
        strings.append(R.string.SaveToMusic);
      }

      if (message.chatId != 0) {
        ids.append(R.id.btn_share);
        strings.append(R.string.Share);

        ids.append(R.id.btn_showInChat);
        strings.append(R.string.ShowInChat);
      }

      if (tracks.size() > 5 && isScrollUnlocked) {
        ids.append(R.id.btn_showInPlaylist);
        strings.append(R.string.PlayListHighlight);
      }

      if (tdlib.context().player().canReverseOrder()) {
        ids.append(R.id.btn_reverseOrder);
        strings.append(R.string.PlayListReverse);
      }

      ids.append(R.id.btn_tgx101Wave);
      strings.append(R.string.Tgx101Wave);

      ids.append(R.id.btn_tgx101Playlists);
      strings.append(R.string.Tgx101Playlists);

      ids.append(R.id.btn_tgx101QueueSearch);
      strings.append(R.string.Tgx101PlayerSearch);

      ids.append(R.id.btn_tgx101HiddenMusic);
      strings.append(R.string.Tgx101DislikeHidden);

      ids.append(R.id.btn_tgx101TrackInfo);
      strings.append(R.string.Tgx101PlayerInfo);

      showMore(ids.get(), strings.get(), 0);
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    if (id == R.id.btn_tgx101Playlists) {
      Tgx101Playlists.show(this);
    } else if (id == R.id.btn_tgx101HiddenMusic) {
      tgx101ShowHidden();
    } else if (id == R.id.btn_tgx101Wave) {
      tgx101ShowWaveSheet();
    } else if (id == R.id.btn_tgx101QueueSearch) {
      tgx101ShowQueueSearch();
    } else if (id == R.id.btn_tgx101TrackInfo) {
      tgx101ShowFileDetails();
    } else if (id == R.id.btn_share) {
      ShareController c = new ShareController(context, tdlib);
      c.setArguments(new ShareController.Args(currentItem.getMessage()).setAllowCopyLink(true));
      c.show();
    } else if (id == R.id.btn_showInPlaylist) {
      highlightCurrentItem();
    } else if (id == R.id.btn_reverseOrder) {
      tdlib.context().player().toggleReverseMode();
    } else if (id == R.id.btn_showInChat) {
      TdApi.Message message = currentItem.getMessage();
      if (TD.isScheduled(message)) {
        tdlib.ui().openScheduledMessage(this, message);
      } else {
        tdlib.ui().openMessage(this, message, null);
      }
    } else if (id == R.id.btn_saveFile) {
      TD.DownloadedFile downloadedFile = TD.getDownloadedFile(currentItem.tdlib(), currentItem.getMessage());
      ;
      if (downloadedFile != null) {
        TD.saveFile(context, downloadedFile);
      }
    }
  }
}
