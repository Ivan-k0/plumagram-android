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
 * File created on 25/12/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.GridSpacingItemDecoration;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.mediaview.MediaCellView;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.MediaRecyclerView;
import org.thunderdog.challegram.v.RtlGridLayoutManager;
import org.thunderdog.challegram.widget.ForceTouchView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.collection.IntList;
import tgx.td.Td;
import tgx.td.data.MessageWithProperties;

public class SharedMediaController extends SharedBaseController<MediaItem> implements ClickHelper.Delegate, ForceTouchView.ActionListener {
  public SharedMediaController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private GridSpacingItemDecoration decoration;
  private int spanCount;

  public SharedMediaController setFilter (TdApi.SearchMessagesFilter filter) {
    this.filter = filter;
    return this;
  }

  @Override
  public CharSequence getName () {
    if (tgx101MediaTab()) {
      return Lang.getString(R.string.TabMedia);
    }
    switch (provideSearchFilter().getConstructor()) {
      case TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR:
        return Lang.getString(R.string.TabMedia);
      case TdApi.SearchMessagesFilterVideo.CONSTRUCTOR:
        return Lang.getString(R.string.TabVideo);
      case TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR:
        return Lang.getString(R.string.TabPhoto);
      case TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR:
        return Lang.getString(R.string.TabGifs);
      case TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR:
        return Lang.getString(Settings.instance().needSeparateMediaTab() ? R.string.TabVideoMessagesLong : R.string.TabVideoMessages);
    }
    return "";
  }

  @DrawableRes
  @Override
  public int getIcon () {
    if (tgx101MediaTab()) {
      return R.drawable.baseline_image_24;
    }
    switch (provideSearchFilter().getConstructor()) {
      case TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR:
        return R.drawable.baseline_image_24;
      case TdApi.SearchMessagesFilterVideo.CONSTRUCTOR:
        return R.drawable.baseline_videocam_24;
      case TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR:
        return R.drawable.baseline_camera_alt_24;
      case TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR:
        return R.drawable.deproko_baseline_gif_24;
      case TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR:
        return R.drawable.deproko_baseline_msg_video_24;
    }
    return 0;
  }

  @Override
  protected boolean supportsMessageContent () {
    return true;
  }

  @Override
  protected boolean needSelectableAnimation () {
    return true;
  }

  @Override
  protected int getLoadColumnCount () {
    return spanCount;
  }

  @Override
  protected void onCreateView (final Context context, final MediaRecyclerView recyclerView, final SettingsAdapter adapter) {
    recyclerView.setMeasureCallback((recyclerView1, width, height) -> {
      final int newSpanCount = tgx101SpanCount(width, height);
      if (spanCount != newSpanCount) {
        spanCount = newSpanCount;
        decoration.setSpanCount(spanCount);
        recyclerView1.invalidateItemDecorations();
        ((GridLayoutManager) recyclerView1.getLayoutManager()).setSpanCount(spanCount);
      }
    });
    /*recyclerView.setDrawCallback(new MediaRecyclerView.DrawCallback() {
      @Override
      public void onDraw (Canvas c) {
        int height = calculateItemsHeight();
        c.drawRect(0, 0, recyclerView.getMeasuredWidth(), height, Paints.fillingPaint(0xaaff00ff));
      }
    });*/
    adapter.setClickHelperDelegate(this);
    addThemeInvalidateListener(recyclerView);

    GridLayoutManager.SpanSizeLookup lookup = new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        int count = adapter.getItems().size();
        return position >= 0 && position < count && adapter.getItems().get(position).getViewType() == ListItem.TYPE_SMALL_MEDIA ? 1 : spanCount;
      }
    };
    lookup.setSpanIndexCacheEnabled(true);

    spanCount = tgx101SpanCount(Screen.currentWidth(), Screen.currentHeight());
    tgx101AttachPinch(recyclerView);
    decoration = new GridSpacingItemDecoration(spanCount, Screen.dp(3f), false, true, true);
    decoration.setNeedDraw(true, ListItem.TYPE_SMALL_MEDIA);
    decoration.setDrawColorId(ColorId.filling);
    decoration.setSpanSizeLookup(lookup);
    GridLayoutManager manager = new RtlGridLayoutManager(context, spanCount);
    manager.setSpanSizeLookup(lookup);
    recyclerView.addItemDecoration(decoration);
    recyclerView.setLayoutManager(manager);
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (recyclerView != null)
      recyclerView.invalidateItemDecorations();
  }

  @Override
  protected int calculateInitialLoadCount () {
    int minSide = Screen.smallestSide();
    int minWidth = minSide / 3;
    int maxItemCount = Math.max(5, Screen.widestSide() / minWidth);
    int itemWidth = Screen.widestSide() / maxItemCount;
    return Screen.calculateLoadingItems(itemWidth, 18);
  }

  // TGx101 (user 2026-10-11, mockup «Медиа-сетка» approved): the grid is resized with a pinch — spread the fingers for
  // bigger previews (down to 2 columns), pinch for more (up to 5); the choice is remembered. The setting is the columns
  // on a ~360 dp wide phone, wider screens (landscape, tablets) get proportionally more. Above the grid of the «Media»
  // tab — «All / Photos / Videos», a Telegram search by type (nothing extra is loaded).
  private static final float TGX101_FILTER_HEIGHT = 46f;
  private boolean tgx101MediaTabKnown, tgx101MediaTab;

  /** The profile keeps the list's first ~header under its tab strip (it hid the first month header): the row is that
   *  much taller and its chips sit at the bottom, below the tabs (phone check 11.10 19:17 — the chips were covered) */
  private static int tgx101FilterHiddenPart () {
    return SettingHolder.measureHeightForType(ListItem.TYPE_SHADOW_TOP) + SettingHolder.measureHeightForType(ListItem.TYPE_HEADER);
  }

  private static int tgx101FilterRowHeight () {
    return tgx101FilterHiddenPart() + Screen.dp(TGX101_FILTER_HEIGHT);
  }

  private boolean tgx101MediaTab () {
    if (!tgx101MediaTabKnown) {
      tgx101MediaTabKnown = true;
      tgx101MediaTab = filter == null || filter.getConstructor() == TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR;
    }
    return tgx101MediaTab;
  }

  private static int tgx101SpanCount (int width, int height) {
    if (width <= 0) {
      return calculateSpanCount(width, height);
    }
    float widthDp = width / Screen.density();
    int columns = Settings.instance().tgx101MediaColumns();
    return Math.max(Settings.TGX101_MEDIA_COLUMNS_MIN, Math.round(widthDp * columns / 360f));
  }

  private void tgx101ApplyColumns (int columns) {
    Settings.instance().setTgx101MediaColumns(columns);
    if (recyclerView == null) return;
    int newSpanCount = tgx101SpanCount(recyclerView.getMeasuredWidth(), recyclerView.getMeasuredHeight());
    if (newSpanCount != spanCount) {
      spanCount = newSpanCount;
      decoration.setSpanCount(spanCount);
      ((GridLayoutManager) recyclerView.getLayoutManager()).setSpanCount(spanCount);
      recyclerView.invalidateItemDecorations();
      // the grid re-lays out under the fingers: a short fade instead of a hard jump
      recyclerView.setAlpha(.55f);
      recyclerView.animate().alpha(1f).setDuration(180).start();
      recyclerView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
      org.thunderdog.challegram.Tgx101Diag.mark("media grid: " + columns + " columns (" + spanCount + " on this screen)");
    }
    int filterIndex = adapter != null ? adapter.indexOfViewByType(ListItem.TYPE_CUSTOM_SINGLE) : -1;
    if (filterIndex != -1) adapter.notifyItemChanged(filterIndex);
  }

  private void tgx101AttachPinch (MediaRecyclerView list) {
    final android.view.ScaleGestureDetector detector = new android.view.ScaleGestureDetector(list.getContext(), new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
      private float accumulated;

      @Override
      public boolean onScaleBegin (android.view.ScaleGestureDetector d) {
        accumulated = 1f;
        list.stopScroll();
        if (list.getParent() != null) list.getParent().requestDisallowInterceptTouchEvent(true);
        list.setPivotX(d.getFocusX());
        list.setPivotY(d.getFocusY());
        return true;
      }

      @Override
      public boolean onScale (android.view.ScaleGestureDetector d) {
        accumulated *= d.getScaleFactor();
        float feedback = Math.max(.88f, Math.min(1.12f, accumulated));
        list.setScaleX(feedback);
        list.setScaleY(feedback);
        int columns = Settings.instance().tgx101MediaColumns();
        if (accumulated > 1.22f && columns > Settings.TGX101_MEDIA_COLUMNS_MIN) {
          tgx101ApplyColumns(columns - 1);
          accumulated = 1f;
        } else if (accumulated < .82f && columns < Settings.TGX101_MEDIA_COLUMNS_MAX) {
          tgx101ApplyColumns(columns + 1);
          accumulated = 1f;
        }
        return true;
      }

      @Override
      public void onScaleEnd (android.view.ScaleGestureDetector d) {
        list.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
      }
    });
    list.addOnItemTouchListener(new androidx.recyclerview.widget.RecyclerView.OnItemTouchListener() {
      @Override
      public boolean onInterceptTouchEvent (@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView rv, @androidx.annotation.NonNull MotionEvent e) {
        detector.onTouchEvent(e);
        return detector.isInProgress() || e.getPointerCount() > 1;
      }

      @Override
      public void onTouchEvent (@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView rv, @androidx.annotation.NonNull MotionEvent e) {
        detector.onTouchEvent(e);
      }

      @Override
      public void onRequestDisallowInterceptTouchEvent (boolean disallowIntercept) { }
    });
  }

  @Override
  protected void tgx101AddHeaderItems (ArrayList<ListItem> items) {
    if (tgx101MediaTab()) {
      items.add(new ListItem(ListItem.TYPE_CUSTOM_SINGLE));
    }
  }

  private int tgx101FilterMode () {
    switch (provideSearchFilter().getConstructor()) {
      case TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR: return 1;
      case TdApi.SearchMessagesFilterVideo.CONSTRUCTOR: return 2;
    }
    return 0;
  }

  @Override
  protected SettingHolder tgx101InitCustom (android.view.ViewGroup parent) {
    android.widget.LinearLayout row = new android.widget.LinearLayout(parent.getContext());
    row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    row.setPadding(Screen.dp(12f), tgx101FilterHiddenPart(), Screen.dp(12f), 0);
    row.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, tgx101FilterRowHeight()));
    int[] labels = {R.string.Tgx101MediaAll, R.string.Tgx101MediaPhotos, R.string.Tgx101MediaVideos};
    for (int i = 0; i < labels.length; i++) {
      final int mode = i;
      android.widget.TextView chip = new android.widget.TextView(parent.getContext());
      chip.setText(Lang.getString(labels[i]));
      chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13.5f);
      chip.setGravity(android.view.Gravity.CENTER);
      chip.setPadding(Screen.dp(14f), 0, Screen.dp(14f), 0);
      android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(30f));
      if (i > 0) lp.leftMargin = Screen.dp(6f);
      chip.setLayoutParams(lp);
      chip.setOnClickListener(v -> tgx101SetFilterMode(mode));
      row.addView(chip);
    }
    android.view.View space = new android.view.View(parent.getContext());
    space.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, 1, 1f));
    row.addView(space);
    android.widget.TextView columns = new android.widget.TextView(parent.getContext());
    columns.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12.5f);
    columns.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
    row.addView(columns);
    return new SettingHolder(row);
  }

  @Override
  protected void tgx101SetCustom (ListItem item, SettingHolder holder) {
    android.widget.LinearLayout row = (android.widget.LinearLayout) holder.itemView;
    row.setBackgroundColor(org.thunderdog.challegram.theme.Theme.fillingColor());
    int mode = tgx101FilterMode();
    int accent = org.thunderdog.challegram.theme.Theme.getColor(ColorId.circleButtonRegular);
    int text = org.thunderdog.challegram.theme.Theme.getColor(ColorId.text);
    for (int i = 0; i < 3; i++) {
      android.widget.TextView chip = (android.widget.TextView) row.getChildAt(i);
      android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
      bg.setCornerRadius(Screen.dp(15f));
      bg.setColor(i == mode ? accent : me.vkryl.core.ColorUtils.alphaColor(.07f, text));
      chip.setBackground(bg);
      chip.setTextColor(i == mode ? 0xffffffff : text);
    }
    android.widget.TextView columns = (android.widget.TextView) row.getChildAt(row.getChildCount() - 1);
    columns.setTextColor(org.thunderdog.challegram.theme.Theme.getColor(ColorId.textLight));
    columns.setText("▦ " + spanCount);
  }

  private void tgx101SetFilterMode (int mode) {
    if (mode == tgx101FilterMode()) return;
    tgx101MediaTab(); // remember it's the «Media» tab before the filter changes
    switch (mode) {
      case 1: filter = new TdApi.SearchMessagesFilterPhoto(); break;
      case 2: filter = new TdApi.SearchMessagesFilterVideo(); break;
      default: filter = new TdApi.SearchMessagesFilterPhotoAndVideo(); break;
    }
    org.thunderdog.challegram.Tgx101Diag.mark("media filter: " + mode);
    tgx101Reload();
  }

  private static int calculateSpanCount (int width, int height) {
    int minSide = Math.min(width, height);
    int minWidth = minSide / 3;
    if (UI.isPortrait() || minWidth == 0) {
      return 3;
    } else if (width > height) {
      return Math.max(5, width / minWidth);
    } else {
      return width / minWidth;
    }
  }

  @Override
  protected int calculateScrollY (int position) {
    if (position == 0 || recyclerView == null) {
      return 0;
    }

    int scrollY = 0;

    List<ListItem> items = adapter.getItems();
    final int itemCount = items.size();

    for (int i = 0; i < position && i < itemCount; i++) {
      ListItem item = items.get(i);
      switch (item.getViewType()) {
        case ListItem.TYPE_SMALL_MEDIA: {
          int width = recyclerView.getMeasuredWidth();
          int maxItemWidth = width / spanCount;
          int spacing = Screen.dp(3f);

          int remainCount = spanCount - 1;
          while (remainCount > 0 && i < position && i < itemCount) {
            item = items.get(i + 1);
            if (item.getViewType() != ListItem.TYPE_SMALL_MEDIA) {
              break;
            }
            i++;
            remainCount--;
          }

          scrollY += maxItemWidth;
          if (item.getViewType() == ListItem.TYPE_SMALL_MEDIA) { // bottom line
            scrollY -= spacing;
          }

          break;
        }
        case ListItem.TYPE_SHADOW_BOTTOM: {
          scrollY += Screen.dp(3f) + SettingHolder.measureHeightForType(ListItem.TYPE_SHADOW_BOTTOM);
          break;
        }
        case ListItem.TYPE_CUSTOM_SINGLE: { // TGx101: the media filter row
          scrollY += tgx101FilterRowHeight();
          break;
        }
        case ListItem.TYPE_SMART_EMPTY:
        case ListItem.TYPE_SMART_PROGRESS: {
          scrollY += measureBaseItemHeight(item.getViewType());
          break;
        }
        default: {
          scrollY += SettingHolder.measureHeightForType(item.getViewType());
          break;
        }
      }
    }

    return scrollY;
  }

  @Override
  protected boolean supportsMessageClearing (MessageWithProperties message) {
    return message.message.content.getConstructor() == TdApi.MessageVideo.CONSTRUCTOR;
  }

  // Data

  private TdApi.SearchMessagesFilter filter;

  @Override
  protected TdApi.SearchMessagesFilter provideSearchFilter () {
    if (filter == null)
      filter = new TdApi.SearchMessagesFilterPhotoAndVideo();
    return filter;
  }

  @Override
  protected MediaItem parseObject (TdApi.Object object) {
    TdApi.Message message = (TdApi.Message) object;
    if (Td.isSecret(message.content)) {
      return null;
    }
    MediaItem item = MediaItem.valueOf(context(), tdlib, message);
    if (item != null) {
      if (item.isVideo() && Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE && (filter.getConstructor() == TdApi.SearchMessagesFilterVideo.CONSTRUCTOR || filter.getConstructor() == TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR)) {
        item.getFileProgress().setHideDownloadedIcon(true);
        item.getFileProgress().setVideoStreaming(true);
      }

      item.setScaleType(ImageFile.CENTER_CROP);
      item.setSize(Screen.dp(124f, 3f));
      item.setNeedSquare(true);
    }
    return item;
  }

  @Override
  protected int provideViewType () {
    return ListItem.TYPE_SMALL_MEDIA;
  }

  private int cachedTotalCount = -1;
  private int cachedPhotosCount, cachedVideosCount;

  @Override
  protected CharSequence buildTotalCount (ArrayList<MediaItem> data) {
    switch (provideSearchFilter().getConstructor()) {
      case TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR: {
        int photosCount, videosCount;
        if (cachedTotalCount != data.size()) {
          photosCount = 0;
          videosCount = 0;
          for (MediaItem item : data) {
            if (item.isVideo()) {
              videosCount++;
            } else {
              photosCount++;
            }
          }
          cachedPhotosCount = photosCount;
          cachedVideosCount = videosCount;
          cachedTotalCount = data.size();
        } else {
          photosCount = cachedPhotosCount;
          videosCount = cachedVideosCount;
        }
        return Lang.pluralPhotosAndVideos(photosCount, videosCount);
      }
      case TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR:
        return Lang.pluralBold(R.string.xGIFs, data.size());
      case TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR:
        return Lang.pluralBold(R.string.xVideoMessages, data.size());
      case TdApi.SearchMessagesFilterVideo.CONSTRUCTOR:
        return Lang.pluralBold(R.string.xVideos, data.size());
      case TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR:
        return Lang.pluralBold(R.string.xPhotos, data.size());
    }
    throw new IllegalStateException("unsupported filter " + provideSearchFilter());
  }

  // Listeners

  @Override
  protected boolean probablyHasEmoji () {
    return false;
  }

  @Override
  protected boolean needsDefaultOnClick () {
    return false;
  }

  @Override
  protected boolean needsDefaultLongPress () {
    return false;
  }

  // Media viewer

  @Override
  protected MediaItem toMediaItem (int index, MediaItem item, @Nullable TdApi.SearchMessagesFilter filter) {
    return MediaItem.copyOf(item);
  }

  // Base touch events

  @Override
  public void onClick (View v) {
    throw new RuntimeException("Stub!");
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return true;
  }

  @Override
  public void onClickAt (View v, float x, float y) {
    ListItem item = (ListItem) v.getTag();
    if (item != null && item.getViewType() == ListItem.TYPE_SMALL_MEDIA) {
      if (adapter.isInSelectMode()) {
        toggleSelected(item);
        return;
      }

      MediaItem mediaItem = (MediaItem) item.getData();
      if (mediaItem.isVideo()) {
        if (mediaItem.isLoaded()) {
          MediaViewController.openFromMedia(this, mediaItem, null, false);
        } else {
          if (!mediaItem.performClick(v, x, y)) {
            MediaViewController.openFromMedia(this, mediaItem, null, false);
          }
        }
      } else if (mediaItem.getType() == MediaItem.TYPE_VIDEO_MESSAGE) {
        if (mediaItem.isLoaded()) {
          tdlib.context().player().playPauseMessage(tdlib, mediaItem.getMessage(), null);
        } else {
          mediaItem.performClick(v);
        }
      } else {
        MediaViewController.openFromMedia(this, mediaItem, filter, false);
      }
    }
  }

  @Override
  public boolean ignoreHapticFeedbackSettings (float x, float y) {
    return Config.FORCE_TOUCH_ENABLED && !adapter.isInSelectMode();
  }

  @Override
  public boolean forceEnableVibration () {
    return Settings.instance().useCustomVibrations();
  }

  @Override
  public boolean needLongPress (float x, float y) {
    return true;
  }

  @Override
  public long getLongPressDuration () {
    return adapter.isInSelectMode() ? ViewConfiguration.getLongPressTimeout() : 250l;
  }

  @Override
  public boolean onLongPressRequestedAt (View v, float x, float y) {
    if (!Config.FORCE_TOUCH_ENABLED || adapter.isInSelectMode()) {
      return super.onLongClick(v);
    }

    ListItem item = (ListItem) v.getTag();
    if (item != null && item.getViewType() == ListItem.TYPE_SMALL_MEDIA) {
      MediaItem mediaItem = (MediaItem) item.getData();
      TdApi.Message message = mediaItem.getMessage();
      if (message == null || Td.isVideoNote(message.content)) {
        return super.onLongClick(v);
      }

      MediaCellView cellView = new MediaCellView(context());
      ForceTouchView.ForceTouchContext context = new ForceTouchView.ForceTouchContext(tdlib, v, cellView, null);

      cellView.setBoundForceTouchContext(context);

      MediaItem mediaItemCopy = MediaItem.copyOf(mediaItem, false);
      if (!mediaItemCopy.isLoaded()) {
        mediaItemCopy.download(true);
      }

      cellView.setMedia(mediaItemCopy);
      // cellView.autoplayIfNeeded();

      IntList ids = new IntList(3);
      IntList icons = new IntList(3);
      StringList strings = new StringList(3);

      TdApi.MessageProperties properties = tdlib.getMessagePropertiesSync(message);

      if (properties.canBeDeletedOnlyForSelf || properties.canBeDeletedForAllUsers) {
        ids.append(R.id.btn_messageDelete);
        icons.append(R.drawable.baseline_delete_24);
        strings.append(R.string.Delete);
      }

      if (alternateParent == null || !alternateParent.inSearchMode()) {
        ids.append(R.id.btn_messageSelect);
        icons.append(R.drawable.baseline_playlist_add_check_24);
        strings.append(R.string.Select);
      }

      if (properties.canBeForwarded) {
        ids.append(R.id.btn_messageShare);
        icons.append(R.drawable.baseline_forward_24);
        strings.append(R.string.Share);
      }

      ids.append(R.id.btn_showInChat);
      icons.append(R.drawable.baseline_visibility_24);
      strings.append(R.string.ShowInChat);

      context.setButtons(this, item, ids.get(), icons.get(), strings.get());

      if (tdlib.isMultiChat(chatId)) {
        TdlibSender sender = new TdlibSender(tdlib, message.chatId, message.senderId);
        String title = sender.isSelf() ? Lang.getString(R.string.FromYou) : sender.getName();
        context.setHeader(title, Lang.getRelativeTimestamp(mediaItemCopy.getSourceDate(), TimeUnit.SECONDS));
        context.setHeaderAvatar(sender.toSender(), null);
      }
      if (parent != null) {
        context.setAllowFullscreen(!parent.inSearchMode());
      } else if (alternateParent != null) {
        context.setAllowFullscreen(!alternateParent.inSearchMode());
      }

      if (this.context.openForceTouch(context)) {
        getRecyclerView().requestDisallowInterceptTouchEvent(true);
        return true;
      } else {
        cellView.performDestroy();
      }
    }

    return false;
  }

  @Override
  public void onLongPressFinish (View view, float x, float y) {
    getRecyclerView().requestDisallowInterceptTouchEvent(false);
    context().closeForceTouch();
  }

  @Override
  public void onLongPressMove (View view, MotionEvent e, float x, float y, float startX, float startY) {
    context().processForceTouchMoveEvent(x, y, startX, startY);
  }

  @Override
  public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
    if (actionId == R.id.btn_messageDelete) {
      tdlib.ui().showDeleteOptions(this, ((MediaItem) ((ListItem) arg).getData()).getMessage());
    } else if (actionId == R.id.btn_messageSelect) {
      toggleSelected((ListItem) arg);
    } else if (actionId == R.id.btn_messageShare) {
      ShareController c = new ShareController(this.context, tdlib);
      c.setArguments(new ShareController.Args(((MediaItem) ((ListItem) arg).getData()).getMessage()).setAllowCopyLink(true));
      c.show();
    } else if (actionId == R.id.btn_showInChat) {
      TdApi.Message message = ((MediaItem) ((ListItem) arg).getData()).getMessage();
      tdlib.ui().openChat(this, message.chatId, new TdlibUi.ChatOpenParameters().highlightMessage(message).passcodeUnlocked());
    }
  }

  @Override
  public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) { }

  @Override
  protected boolean setThumbLocation (MediaViewThumbLocation location, View view, MediaItem mediaItem) {
    return true;
  }
}
