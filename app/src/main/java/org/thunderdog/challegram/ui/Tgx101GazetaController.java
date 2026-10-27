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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.AvatarView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import tgx.td.Td;
import tgx.td.MessageId;

/**
 * TGx101 «Газета» (user 2026-10-27, mockup approved): the channels' posts as full-width cards — avatar and channel on
 * top left, the photo as is or a thumbnail on the right, reactions bottom left, views and time bottom right. Oldest
 * at the top, it opens on the first unread post; a post that came into view is read in its channel too. A tap — the
 * post's menu.
 */
public class Tgx101GazetaController extends ViewController<Void> implements Menu {
  public Tgx101GazetaController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101Gazeta;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101Gazeta);
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_tgx101Gazeta;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_tgx101Gazeta) {
      header.addButton(menu, R.id.menu_btn_settings, R.drawable.baseline_settings_24, 49f, this, getHeaderIconColorId());
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_settings) {
      navigateTo(new Tgx101GazetaSettingsController(context, tdlib));
    }
  }

  private final List<Tgx101Gazeta.Post> posts = new ArrayList<>();
  private RecyclerView list;
  private TextView empty;
  private final Adapter adapter = new Adapter();
  private boolean loaded;

  @Override
  protected View onCreateView (Context context) {
    FrameLayout root = new FrameLayout(context);
    ViewSupport.setThemedBackground(root, ColorId.background, this);
    list = new RecyclerView(context);
    list.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    list.setItemAnimator(null);
    list.setAdapter(adapter);
    list.setClipToPadding(false);
    list.setPadding(0, Screen.dp(4f), 0, Screen.dp(4f) + MainController.tgx101CapsuleSpace());
    list.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        markVisibleRead();
      }
    });
    root.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    empty = new TextView(context);
    empty.setGravity(Gravity.CENTER);
    empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    empty.setTextColor(Theme.getColor(ColorId.textLight));
    empty.setPadding(Screen.dp(32f), 0, Screen.dp(32f), 0);
    empty.setText(Lang.getString(R.string.Tgx101GazetaLoading));
    root.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    load();
    return root;
  }

  private boolean needReload;

  @Override
  public void onFocus () {
    super.onFocus();
    if (needReload) {
      needReload = false;
      load();
    }
  }

  @Override
  public void onBlur () {
    super.onBlur();
    needReload = true; // the settings may change the channels; the feed comes back on the first unread
  }

  private void load () {
    Tgx101Gazeta.loadChannels(tdlib, chatIds -> {
      if (isDestroyed()) return;
      Tgx101Gazeta.loadFeed(tdlib, chatIds, (feed, firstUnread) -> {
        if (isDestroyed()) return;
        loaded = true;
        posts.clear();
        posts.addAll(feed);
        adapter.notifyDataSetChanged();
        empty.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(Lang.getString(chatIds.length == 0 ? R.string.Tgx101GazetaNoChannels : R.string.Tgx101GazetaEmpty));
        org.thunderdog.challegram.Tgx101Diag.mark("gazeta: " + chatIds.length + " channels, " + posts.size() + " posts, first unread " + firstUnread);
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
        if (manager != null && !posts.isEmpty()) {
          manager.scrollToPositionWithOffset(firstUnread != -1 ? firstUnread : posts.size() - 1, Screen.dp(4f));
        }
        list.post(this::markVisibleRead);
      });
    });
  }

  // a post that came into view is read, in its channel too (user: «пролистанный пост = прочитан»)
  private void markVisibleRead () {
    if (!loaded || list == null) return;
    LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
    if (manager == null) return;
    int first = manager.findFirstVisibleItemPosition(), last = manager.findLastVisibleItemPosition();
    if (first == RecyclerView.NO_POSITION) return;
    Map<Long, List<Long>> byChat = new HashMap<>();
    for (int i = first; i <= last && i < posts.size(); i++) {
      Tgx101Gazeta.Post post = posts.get(i);
      if (!post.unread) continue;
      post.unread = false;
      List<Long> ids = byChat.get(post.chatId);
      if (ids == null) byChat.put(post.chatId, ids = new ArrayList<>());
      for (long id : post.ids()) ids.add(id);
    }
    for (Map.Entry<Long, List<Long>> e : byChat.entrySet()) {
      long[] ids = new long[e.getValue().size()];
      for (int i = 0; i < ids.length; i++) ids[i] = e.getValue().get(i);
      tdlib.client().send(new TdApi.ViewMessages(e.getKey(), ids, new TdApi.MessageSourceChatHistory(), true), r -> { });
    }
  }

  // the post's menu (user's п.7/9): open in the channel, reply, copy, link, forward, hide similar, hide the channel
  private void openPostMenu (Tgx101Gazeta.Post post) {
    TdApi.Message main = post.main();
    TdApi.FormattedText text = Td.textOrCaption(main.content);
    boolean hasText = text != null && !Td.isEmpty(text);
    List<Integer> ids = new ArrayList<>(), icons = new ArrayList<>();
    List<String> titles = new ArrayList<>();
    ids.add(R.id.btn_tgx101GazetaOpen); titles.add(Lang.getString(R.string.Tgx101GazetaOpenInChannel)); icons.add(R.drawable.baseline_open_in_browser_24);
    ids.add(R.id.btn_messageReply); titles.add(Lang.getString(R.string.Reply)); icons.add(R.drawable.baseline_reply_24);
    if (hasText && main.content.getConstructor() != TdApi.MessagePaidMedia.CONSTRUCTOR) {
      ids.add(R.id.btn_messageCopy); titles.add(Lang.getString(R.string.Copy)); icons.add(R.drawable.baseline_content_copy_24);
    }
    ids.add(R.id.btn_messageCopyLink); titles.add(Lang.getString(R.string.CopyLink)); icons.add(R.drawable.baseline_link_24);
    ids.add(R.id.btn_messageShare); titles.add(Lang.getString(R.string.Share)); icons.add(R.drawable.baseline_forward_24);
    if (org.thunderdog.challegram.data.Tgx101MessageFilters.suggestion(main) != null) {
      ids.add(R.id.btn_tgx101FilterSimilar); titles.add(Lang.getString(R.string.Tgx101FilterSimilar)); icons.add(R.drawable.baseline_filter_variant_remove_24);
    }
    ids.add(R.id.btn_tgx101GazetaHideChannel); titles.add(Lang.getString(R.string.Tgx101GazetaHideChannel)); icons.add(R.drawable.baseline_eye_off_24);
    int[] outIds = new int[ids.size()], outIcons = new int[ids.size()];
    for (int i = 0; i < outIds.length; i++) {
      outIds[i] = ids.get(i);
      outIcons[i] = icons.get(i);
    }
    showOptions(tdlib.chatTitle(post.chatId), outIds, titles.toArray(new String[0]), null, outIcons, (v, id) -> {
      if (id == R.id.btn_tgx101GazetaOpen) {
        tdlib.ui().openMessage(this, post.chatId, new MessageId(post.chatId, main.id), null);
      } else if (id == R.id.btn_messageReply || id == R.id.btn_messageShare) {
        // a comment written in the share sheet goes as a reply to the post (the usual way of replying elsewhere)
        ShareController c = new ShareController(context, tdlib);
        c.setArguments(new ShareController.Args(post.messages.toArray(new TdApi.Message[0])));
        c.show();
      } else if (id == R.id.btn_messageCopy) {
        if (text != null) UI.copyText(TD.toCopyText(text), R.string.CopiedText);
      } else if (id == R.id.btn_messageCopyLink) {
        tdlib.send(new TdApi.GetMessageLink(post.chatId, main.id, 0, 0, null, post.messages.size() > 1, false), (link, error) -> {
          if (link != null) UI.post(() -> UI.copyText(link.link, R.string.CopiedLink));
        });
      } else if (id == R.id.btn_tgx101FilterSimilar) {
        Tgx101FiltersController.addRule(this, org.thunderdog.challegram.data.Tgx101MessageFilters.suggestion(main), post.chatId, tdlib.chatTitle(post.chatId));
      } else if (id == R.id.btn_tgx101GazetaHideChannel) {
        java.util.Set<Long> off = Tgx101Gazeta.excluded(tdlib);
        off.add(post.chatId);
        Tgx101Gazeta.setExcluded(tdlib, off);
        for (int i = posts.size() - 1; i >= 0; i--) {
          if (posts.get(i).chatId == post.chatId) {
            posts.remove(i);
            adapter.notifyItemRemoved(i);
          }
        }
        UI.showToast(Lang.getString(R.string.Tgx101GazetaChannelHidden, tdlib.chatTitle(post.chatId)), android.widget.Toast.LENGTH_SHORT);
      }
      return true;
    });
  }

  // ---- cards ----

  private static final int GAP = 6;

  private final class Adapter extends RecyclerView.Adapter<Holder> {
    @NonNull
    @Override
    public Holder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return new Holder(new Card(parent.getContext()));
    }

    @Override
    public void onBindViewHolder (@NonNull Holder holder, int position) {
      ((Card) holder.itemView).bind(posts.get(position));
    }

    @Override
    public int getItemCount () {
      return posts.size();
    }

    @Override
    public void onViewAttachedToWindow (@NonNull Holder holder) {
      ((Card) holder.itemView).avatar.attach();
    }

    @Override
    public void onViewDetachedFromWindow (@NonNull Holder holder) {
      ((Card) holder.itemView).avatar.detach();
    }
  }

  private static final class Holder extends RecyclerView.ViewHolder {
    Holder (View itemView) {
      super(itemView);
    }
  }

  private static TextView text (Context context, float sp, int colorId, boolean medium) {
    TextView v = new TextView(context);
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
    v.setTextColor(Theme.getColor(colorId));
    v.setTypeface(medium ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
    return v;
  }

  private static final int FOLD_LINES = 8;

  private final class Card extends LinearLayout {
    final AvatarView avatar;
    final TextView title, body, more, reactions, meta;
    final FrameLayout media, thumbWrap;
    final ImageView mediaImage, thumbImage;
    final TextView mediaBadge, mediaCount, thumbBadge;
    @Nullable Tgx101Gazeta.Post post;
    int imageToken;

    Card (Context context) {
      super(context);
      setOrientation(VERTICAL);
      setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      int pad = Screen.dp(14f);

      LinearLayout header = new LinearLayout(context);
      header.setOrientation(HORIZONTAL);
      header.setGravity(Gravity.CENTER_VERTICAL);
      header.setPadding(pad, Screen.dp(10f), pad, Screen.dp(8f));
      avatar = new AvatarView(context);
      header.addView(avatar, new LinearLayout.LayoutParams(Screen.dp(34f), Screen.dp(34f)));
      title = text(context, 15f, ColorId.text, true);
      title.setSingleLine(true);
      title.setEllipsize(TextUtils.TruncateAt.END);
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      tp.leftMargin = Screen.dp(10f);
      header.addView(title, tp);
      addView(header);

      media = new FrameLayout(context);
      mediaImage = new ImageView(context);
      mediaImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
      media.addView(mediaImage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      mediaBadge = badge(context);
      FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM);
      bp.leftMargin = bp.bottomMargin = Screen.dp(8f);
      media.addView(mediaBadge, bp);
      mediaCount = badge(context);
      FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP);
      cp.rightMargin = cp.topMargin = Screen.dp(8f);
      media.addView(mediaCount, cp);
      addView(media, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));

      LinearLayout row = new LinearLayout(context);
      row.setOrientation(HORIZONTAL);
      row.setPadding(pad, Screen.dp(8f), pad, 0);
      body = text(context, 15f, ColorId.text, false);
      body.setLineSpacing(Screen.dp(2f), 1f);
      row.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      thumbWrap = new FrameLayout(context);
      thumbImage = new ImageView(context);
      thumbImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
      thumbWrap.addView(thumbImage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      thumbBadge = badge(context);
      thumbBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f);
      thumbWrap.addView(thumbBadge, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
      GradientDrawable round = new GradientDrawable();
      round.setCornerRadius(Screen.dp(8f));
      round.setColor(0x22000000);
      thumbWrap.setBackground(round);
      thumbWrap.setClipToOutline(true);
      LinearLayout.LayoutParams thp = new LinearLayout.LayoutParams(Screen.dp(58f), Screen.dp(58f));
      thp.leftMargin = Screen.dp(10f);
      row.addView(thumbWrap, thp);
      addView(row);

      more = text(context, 14f, ColorId.textLink, true);
      more.setPadding(pad, Screen.dp(4f), pad, 0);
      more.setText(Lang.getString(R.string.Tgx101GazetaShowFull));
      more.setOnClickListener(v -> {
        if (post != null) {
          post.expanded = true;
          bind(post);
        }
      });
      addView(more);

      LinearLayout footer = new LinearLayout(context);
      footer.setOrientation(HORIZONTAL);
      footer.setGravity(Gravity.CENTER_VERTICAL);
      footer.setPadding(pad, Screen.dp(8f), pad, Screen.dp(10f));
      reactions = text(context, 13f, ColorId.textLight, false);
      reactions.setSingleLine(true);
      reactions.setEllipsize(TextUtils.TruncateAt.END);
      footer.addView(reactions, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      meta = text(context, 12.5f, ColorId.textLight, false);
      footer.addView(meta);
      addView(footer);

      setOnClickListener(v -> {
        if (post != null) openPostMenu(post);
      });
      ViewSupport.setThemedBackground(this, ColorId.filling, Tgx101GazetaController.this);
    }

    private TextView badge (Context context) {
      TextView v = text(context, 12f, ColorId.white, true);
      v.setTextColor(0xffffffff);
      v.setPadding(Screen.dp(6f), Screen.dp(2f), Screen.dp(6f), Screen.dp(2f));
      GradientDrawable bg = new GradientDrawable();
      bg.setCornerRadius(Screen.dp(10f));
      bg.setColor(0x99000000);
      v.setBackground(bg);
      return v;
    }

    void bind (Tgx101Gazeta.Post post) {
      this.post = post;
      RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) getLayoutParams();
      if (lp != null) {
        lp.topMargin = Screen.dp(GAP / 2f);
        lp.bottomMargin = Screen.dp(GAP / 2f);
      }
      title.setTextColor(Theme.getColor(ColorId.text));
      body.setTextColor(Theme.getColor(ColorId.text));
      reactions.setTextColor(Theme.getColor(ColorId.textLight));
      meta.setTextColor(Theme.getColor(ColorId.textLight));
      more.setTextColor(Theme.getColor(ColorId.textLink));
      avatar.setChat(tdlib, tdlib.chat(post.chatId));
      title.setText(tdlib.chatTitle(post.chatId));

      TdApi.Message main = post.main();
      TdApi.FormattedText text = Td.textOrCaption(main.content);
      CharSequence str = text != null && !Td.isEmpty(text) ? TD.toCharSequence(text) : describe(main);
      body.setText(str);
      body.setVisibility(str != null && str.length() > 0 ? VISIBLE : GONE);
      boolean fold = Tgx101Gazeta.foldLong() && !post.expanded;
      body.setMaxLines(fold ? FOLD_LINES : Integer.MAX_VALUE);
      more.setVisibility(GONE);
      if (fold) {
        body.post(() -> {
          if (this.post == post && body.getLayout() != null && body.getLineCount() >= FOLD_LINES) {
            android.text.Layout layout = body.getLayout();
            boolean cut = layout.getEllipsisCount(FOLD_LINES - 1) > 0 || layout.getLineCount() > FOLD_LINES || (body.getText().length() > layout.getLineEnd(FOLD_LINES - 1));
            more.setVisibility(cut ? VISIBLE : GONE);
          }
        });
      }

      // media: as is (full width) or a thumbnail on the right
      TdApi.Message visual = null;
      for (TdApi.Message m : post.messages) {
        if (isVisual(m)) {
          visual = m;
          break;
        }
      }
      int extra = 0;
      for (TdApi.Message m : post.messages) if (isVisual(m)) extra++;
      extra = Math.max(0, extra - 1);
      boolean thumbs = Tgx101Gazeta.photos() == Tgx101Gazeta.PHOTOS_THUMB;
      int token = ++imageToken;
      mediaImage.setImageDrawable(null);
      thumbImage.setImageDrawable(null);
      if (visual == null) {
        media.setVisibility(GONE);
        thumbWrap.setVisibility(GONE);
      } else if (thumbs) {
        media.setVisibility(GONE);
        thumbWrap.setVisibility(VISIBLE);
        String badgeText = extra > 0 ? "+" + extra : isVideo(visual) ? "▶" : null;
        thumbBadge.setText(badgeText);
        thumbBadge.setVisibility(badgeText != null ? VISIBLE : GONE);
        loadImage(visual, thumbImage, token, Screen.dp(58f));
      } else {
        thumbWrap.setVisibility(GONE);
        media.setVisibility(VISIBLE);
        int width = getWidth() > 0 ? getWidth() : Screen.currentWidth();
        int[] size = sizeOf(visual);
        int height = size[0] > 0 && size[1] > 0 ? (int) ((long) width * size[1] / size[0]) : width * 9 / 16;
        height = Math.max(Screen.dp(120f), Math.min(height, (int) (Screen.currentHeight() * .6f)));
        LinearLayout.LayoutParams mp = (LinearLayout.LayoutParams) media.getLayoutParams();
        mp.height = height;
        media.setLayoutParams(mp);
        String duration = durationOf(visual);
        mediaBadge.setText(duration != null ? "▶ " + duration : null);
        mediaBadge.setVisibility(duration != null ? VISIBLE : GONE);
        mediaCount.setText(extra > 0 ? "+" + extra : null);
        mediaCount.setVisibility(extra > 0 ? VISIBLE : GONE);
        loadImage(visual, mediaImage, token, width);
      }

      // reactions and comments on the left; views and time on the right
      StringBuilder left = new StringBuilder();
      TdApi.MessageInteractionInfo info = main.interactionInfo;
      if (info != null && info.reactions != null) {
        for (TdApi.MessageReaction r : info.reactions.reactions) {
          if (left.length() > 0) left.append("  ");
          left.append(r.type.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR ? ((TdApi.ReactionTypeEmoji) r.type).emoji : r.type.getConstructor() == TdApi.ReactionTypePaid.CONSTRUCTOR ? "⭐" : "❤").append(' ').append(Strings.buildCounter(r.totalCount));
        }
      }
      if (info != null && info.replyInfo != null && info.replyInfo.replyCount > 0) {
        if (left.length() > 0) left.append("  ");
        left.append("💬 ").append(Strings.buildCounter(info.replyInfo.replyCount));
      }
      reactions.setText(left);
      StringBuilder right = new StringBuilder();
      if (info != null && info.viewCount > 0) right.append("👁 ").append(Strings.buildCounter(info.viewCount)).append("  ");
      right.append(Lang.getMessageTimestamp(main.date, TimeUnit.SECONDS));
      meta.setText(right);
    }

    private void loadImage (TdApi.Message message, ImageView target, int token, int targetWidth) {
      TdApi.Minithumbnail mini = minithumbnailOf(message);
      if (mini != null && mini.data != null) {
        Bitmap b = BitmapFactory.decodeByteArray(mini.data, 0, mini.data.length);
        if (b != null) target.setImageBitmap(b);
      }
      TdApi.File file = fileOf(message, targetWidth);
      if (file == null) return;
      tdlib.send(new TdApi.DownloadFile(file.id, 16, 0, 0, true), (f, error) -> {
        if (f == null || !f.local.isDownloadingCompleted) return;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.local.path, bounds);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 1;
        while (bounds.outWidth / (o.inSampleSize * 2) >= targetWidth) o.inSampleSize *= 2;
        Bitmap b = BitmapFactory.decodeFile(f.local.path, o);
        if (b == null) return;
        UI.post(() -> {
          if (imageToken == token) target.setImageBitmap(b);
        });
      });
    }
  }

  private static boolean isVisual (TdApi.Message m) {
    switch (m.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessageAnimation.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  private static boolean isVideo (TdApi.Message m) {
    return m.content.getConstructor() == TdApi.MessageVideo.CONSTRUCTOR || m.content.getConstructor() == TdApi.MessageAnimation.CONSTRUCTOR;
  }

  private static int[] sizeOf (TdApi.Message m) {
    switch (m.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.PhotoSize[] sizes = ((TdApi.MessagePhoto) m.content).photo.sizes;
        if (sizes.length > 0) return new int[] {sizes[sizes.length - 1].width, sizes[sizes.length - 1].height};
        break;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        TdApi.Video v = ((TdApi.MessageVideo) m.content).video;
        return new int[] {v.width, v.height};
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        TdApi.Animation a = ((TdApi.MessageAnimation) m.content).animation;
        return new int[] {a.width, a.height};
      }
    }
    return new int[] {0, 0};
  }

  @Nullable
  private static String durationOf (TdApi.Message m) {
    if (m.content.getConstructor() == TdApi.MessageVideo.CONSTRUCTOR) {
      return Strings.buildDuration(((TdApi.MessageVideo) m.content).video.duration);
    }
    if (m.content.getConstructor() == TdApi.MessageAnimation.CONSTRUCTOR) {
      return "GIF";
    }
    return null;
  }

  @Nullable
  private static TdApi.Minithumbnail minithumbnailOf (TdApi.Message m) {
    switch (m.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR:
        return ((TdApi.MessagePhoto) m.content).photo.minithumbnail;
      case TdApi.MessageVideo.CONSTRUCTOR:
        return ((TdApi.MessageVideo) m.content).video.minithumbnail;
      case TdApi.MessageAnimation.CONSTRUCTOR:
        return ((TdApi.MessageAnimation) m.content).animation.minithumbnail;
    }
    return null;
  }

  /** the smallest photo size that still fills the width; for a video — its cover */
  @Nullable
  private static TdApi.File fileOf (TdApi.Message m, int width) {
    switch (m.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.PhotoSize[] sizes = ((TdApi.MessagePhoto) m.content).photo.sizes;
        TdApi.PhotoSize best = null;
        for (TdApi.PhotoSize size : sizes) {
          if (best == null || (best.width < width && size.width > best.width) || (size.width >= width && size.width < best.width)) best = size;
        }
        return best != null ? best.photo : null;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        TdApi.Thumbnail t = ((TdApi.MessageVideo) m.content).video.thumbnail;
        return t != null ? t.file : null;
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        TdApi.Thumbnail t = ((TdApi.MessageAnimation) m.content).animation.thumbnail;
        return t != null && t.format.getConstructor() != TdApi.ThumbnailFormatMpeg4.CONSTRUCTOR ? t.file : null;
      }
    }
    return null;
  }

  /** a post with no text: what it is, in a word */
  private static String describe (TdApi.Message m) {
    switch (m.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessageAnimation.CONSTRUCTOR:
        return "";
      case TdApi.MessagePoll.CONSTRUCTOR:
        return "📊 " + ((TdApi.MessagePoll) m.content).poll.question.text;
      case TdApi.MessageDocument.CONSTRUCTOR:
        return "📄 " + ((TdApi.MessageDocument) m.content).document.fileName;
      case TdApi.MessageAudio.CONSTRUCTOR: {
        TdApi.Audio a = ((TdApi.MessageAudio) m.content).audio;
        return "🎵 " + (a.performer.isEmpty() ? a.title : a.performer + " — " + a.title);
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return "🎤 " + Lang.getString(R.string.Tgx101GazetaVoice);
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        return "⏺ " + Lang.getString(R.string.Tgx101GazetaVideoNote);
      case TdApi.MessageSticker.CONSTRUCTOR:
        return ((TdApi.MessageSticker) m.content).sticker.emoji;
      case TdApi.MessageLocation.CONSTRUCTOR:
      case TdApi.MessageVenue.CONSTRUCTOR:
        return "📍";
    }
    return "";
  }
}
