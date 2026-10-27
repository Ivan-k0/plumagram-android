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
      // ☀/☾ — the light or the dark look of the feed (user 2026-10-27: switched here, not in the settings)
      profileButton = header.addButton(menu, R.id.menu_btn_tgx101GazetaProfile, profileIcon(), 49f, this, getHeaderIconColorId());
      header.addButton(menu, R.id.menu_btn_settings, R.drawable.baseline_settings_24, 49f, this, getHeaderIconColorId());
    }
  }

  private @Nullable org.thunderdog.challegram.navigation.HeaderButton profileButton;

  private static int profileIcon () {
    return Tgx101Gazeta.profile() == Tgx101Gazeta.PROFILE_DARK ? R.drawable.tgx101_sun_24 : R.drawable.baseline_brightness_2_24;
  }

  private void applyPalette () {
    if (root != null) root.setBackgroundColor(Tgx101Gazeta.palette()[0]);
    if (empty != null) empty.setTextColor(Tgx101Gazeta.palette()[3]);
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_tgx101GazetaProfile) {
      Tgx101Gazeta.setProfile(Tgx101Gazeta.profile() == Tgx101Gazeta.PROFILE_DARK ? Tgx101Gazeta.PROFILE_LIGHT : Tgx101Gazeta.PROFILE_DARK);
      if (profileButton != null) profileButton.setImageResource(profileIcon());
      applyPalette();
      adapter.notifyDataSetChanged();
    } else if (id == R.id.menu_btn_settings) {
      navigateTo(new Tgx101GazetaSettingsController(context, tdlib));
    }
  }

  private final List<Tgx101Gazeta.Post> posts = new ArrayList<>();
  private RecyclerView list;
  private TextView empty;
  private final Adapter adapter = new Adapter();
  private boolean loaded;
  private FrameLayout root;

  @Override
  protected View onCreateView (Context context) {
    root = new FrameLayout(context);
    list = new org.thunderdog.challegram.v.CustomRecyclerView(context);
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
    applyPalette();
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
        openInChannel(post);
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
          if (posts.get(i).chatId == post.chatId) posts.remove(i);
        }
        adapter.notifyDataSetChanged(); // the neighbours may join into one channel's run
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
      ((Card) holder.itemView).bind(posts.get(position), position);
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

  private static TextView text (Context context, float sp, boolean medium) {
    TextView v = new TextView(context);
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
    v.setTypeface(medium ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
    return v;
  }

  /** chips left to right, a new row when the next doesn't fit (user: «реакции спускаются вторым рядом») */
  private static final class FlowLayout extends ViewGroup {
    private final int gap = Screen.dp(6f);

    FlowLayout (Context context) {
      super(context);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      int x = 0, y = 0, rowHeight = 0, maxX = 0;
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        if (child.getVisibility() == GONE) continue;
        child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
        if (x > 0 && x + w > width) {
          x = 0;
          y += rowHeight + gap;
          rowHeight = 0;
        }
        x += w + gap;
        maxX = Math.max(maxX, x - gap);
        rowHeight = Math.max(rowHeight, h);
      }
      int mode = MeasureSpec.getMode(widthMeasureSpec);
      setMeasuredDimension(mode == MeasureSpec.EXACTLY ? width : Math.max(0, maxX), y + rowHeight);
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      int width = r - l;
      int x = 0, y = 0, rowHeight = 0;
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        if (child.getVisibility() == GONE) continue;
        int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
        if (x > 0 && x + w > width) {
          x = 0;
          y += rowHeight + gap;
          rowHeight = 0;
        }
        child.layout(x, y, x + w, y + h);
        x += w + gap;
        rowHeight = Math.max(rowHeight, h);
      }
    }
  }

  private static int lineCount (CharSequence text, TextView like, int width) {
    if (width <= 0 || text == null || text.length() == 0) return 0;
    android.text.StaticLayout layout = new android.text.StaticLayout(text, like.getPaint(), width, android.text.Layout.Alignment.ALIGN_NORMAL,
      like.getLineSpacingMultiplier(), like.getLineSpacingExtra(), true);
    return layout.getLineCount();
  }

  private final class Card extends LinearLayout {
    final LinearLayout header;
    final AvatarView avatar;
    final TextView title, summary, body, bodyBelow, more, shortBtn, meta;
    final FlowLayout reactions;
    final FrameLayout media, thumbWrap;
    final ImageView mediaImage, thumbImage;
    final TextView mediaBadge, mediaCount, thumbBadge;
    final android.graphics.drawable.Drawable eye;
    @Nullable Tgx101Gazeta.Post post;
    int position;
    int imageToken;
    final int pad = Screen.dp(14f);

    Card (Context context) {
      super(context);
      setOrientation(VERTICAL);
      setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      header = new LinearLayout(context);
      header.setOrientation(HORIZONTAL);
      header.setGravity(Gravity.CENTER_VERTICAL);
      header.setPadding(pad, Screen.dp(10f), pad, Screen.dp(8f));
      avatar = new AvatarView(context);
      header.addView(avatar, new LinearLayout.LayoutParams(Screen.dp(34f), Screen.dp(34f)));
      title = text(context, 15f, true);
      title.setSingleLine(true);
      title.setEllipsize(TextUtils.TruncateAt.END);
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      tp.leftMargin = Screen.dp(10f);
      header.addView(title, tp);
      header.setOnClickListener(v -> {
        if (post != null) tdlib.ui().openChat(Tgx101GazetaController.this, post.chatId, null);
      });
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

      summary = text(context, 15f, false);
      summary.setPadding(Screen.dp(10f), Screen.dp(8f), Screen.dp(10f), Screen.dp(8f));
      LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      sp.leftMargin = sp.rightMargin = pad;
      sp.topMargin = Screen.dp(8f);
      addView(summary, sp);

      // the text beside the thumbnail, and what's left of it under the thumbnail at full width
      LinearLayout row = new LinearLayout(context);
      row.setOrientation(HORIZONTAL);
      row.setPadding(pad, Screen.dp(8f), pad, 0);
      body = text(context, 15f, false);
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
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) thumbWrap.setClipToOutline(true);
      LinearLayout.LayoutParams thp = new LinearLayout.LayoutParams(Screen.dp(THUMB), Screen.dp(THUMB));
      thp.leftMargin = Screen.dp(THUMB_GAP);
      row.addView(thumbWrap, thp);
      addView(row);
      bodyBelow = text(context, 15f, false);
      bodyBelow.setLineSpacing(Screen.dp(2f), 1f);
      bodyBelow.setPadding(pad, Screen.dp(2f), pad, 0);
      addView(bodyBelow);

      LinearLayout links = new LinearLayout(context);
      links.setOrientation(HORIZONTAL);
      links.setPadding(pad, Screen.dp(4f), pad, 0);
      more = text(context, 14f, true);
      more.setText(Lang.getString(R.string.Tgx101GazetaShowFull));
      more.setOnClickListener(v -> {
        if (post != null) {
          post.expanded = true;
          bind(post, position);
        }
      });
      links.addView(more);
      shortBtn = text(context, 14f, true);
      shortBtn.setOnClickListener(v -> {
        if (post != null) toggleShort(post);
      });
      LinearLayout.LayoutParams shp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      shp.leftMargin = Screen.dp(18f);
      links.addView(shortBtn, shp);
      addView(links);

      LinearLayout footer = new LinearLayout(context);
      footer.setOrientation(HORIZONTAL);
      footer.setGravity(Gravity.BOTTOM);
      footer.setPadding(pad, Screen.dp(8f), pad, Screen.dp(10f));
      reactions = new FlowLayout(context);
      LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      rp.rightMargin = Screen.dp(8f);
      footer.addView(reactions, rp);
      meta = text(context, 12.5f, false);
      meta.setGravity(Gravity.CENTER_VERTICAL);
      meta.setCompoundDrawablePadding(Screen.dp(3f));
      footer.addView(meta);
      addView(footer);
      // the views' eye as on channel posts
      eye = org.thunderdog.challegram.tool.Drawables.get(getResources(), R.drawable.baseline_visibility_14).mutate();

      setOnClickListener(v -> {
        if (post != null) openPostMenu(post);
      });
    }

    private TextView badge (Context context) {
      TextView v = text(context, 12f, true);
      v.setTextColor(0xffffffff);
      v.setPadding(Screen.dp(6f), Screen.dp(2f), Screen.dp(6f), Screen.dp(2f));
      GradientDrawable bg = new GradientDrawable();
      bg.setCornerRadius(Screen.dp(10f));
      bg.setColor(0x99000000);
      v.setBackground(bg);
      return v;
    }

    private TextView chip (CharSequence label, boolean chosen, int[] pal, @Nullable View.OnClickListener click) {
      TextView v = text(getContext(), Math.max(12f, Tgx101Gazeta.fontSize() - 2f), false);
      v.setText(label);
      v.setTextColor(chosen ? pal[4] : pal[3]);
      v.setPadding(Screen.dp(9f), Screen.dp(4f), Screen.dp(9f), Screen.dp(4f));
      GradientDrawable bg = new GradientDrawable();
      bg.setCornerRadius(Screen.dp(14f));
      bg.setColor(me.vkryl.core.ColorUtils.alphaColor(chosen ? .18f : .1f, chosen ? pal[4] : pal[2]));
      if (chosen) bg.setStroke(Math.max(1, Screen.dp(1f)), me.vkryl.core.ColorUtils.alphaColor(.6f, pal[4]));
      v.setBackground(bg);
      if (click != null) v.setOnClickListener(click);
      return v;
    }

    void bind (Tgx101Gazeta.Post post, int position) {
      this.post = post;
      this.position = position;
      int[] pal = Tgx101Gazeta.palette();
      float font = Tgx101Gazeta.fontSize();
      setBackgroundColor(pal[1]);

      // posts of one channel in a row: one header for them, a thin line between (user 2026-10-27)
      boolean samePrev = position > 0 && posts.get(position - 1).chatId == post.chatId;
      boolean sameNext = position + 1 < posts.size() && posts.get(position + 1).chatId == post.chatId;
      RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) getLayoutParams();
      if (lp != null) {
        lp.topMargin = samePrev ? Math.max(1, Screen.dp(.5f)) : Screen.dp(GAP / 2f);
        lp.bottomMargin = sameNext ? 0 : Screen.dp(GAP / 2f);
      }
      header.setVisibility(samePrev ? GONE : VISIBLE);
      title.setTextColor(pal[2]);
      title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, font);
      if (!samePrev) {
        avatar.setChat(tdlib, tdlib.chat(post.chatId));
        title.setText(tdlib.chatTitle(post.chatId));
      }

      for (TextView t : new TextView[] {body, bodyBelow, summary}) {
        t.setTextColor(pal[2]);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, font);
      }
      more.setTextColor(pal[4]);
      shortBtn.setTextColor(pal[4]);
      meta.setTextColor(pal[3]);
      eye.setColorFilter(new android.graphics.PorterDuffColorFilter(pal[3], android.graphics.PorterDuff.Mode.SRC_IN));

      TdApi.Message main = post.main();
      TdApi.FormattedText text = Td.textOrCaption(main.content);
      CharSequence str = text != null && !Td.isEmpty(text) ? TD.toCharSequence(text) : describe(main);

      // the AI summary («✨ Short»), above the text while shown
      GradientDrawable sumBg = new GradientDrawable();
      sumBg.setCornerRadius(Screen.dp(10f));
      sumBg.setColor(me.vkryl.core.ColorUtils.alphaColor(.12f, pal[4]));
      summary.setBackground(sumBg);
      summary.setVisibility(post.summaryShown && post.summary != null ? VISIBLE : GONE);
      if (post.summaryShown && post.summary != null) summary.setText(TD.toCharSequence(post.summary));

      // media: as is (full width) or a thumbnail on the right
      TdApi.Message visual = null;
      int visuals = 0;
      for (TdApi.Message m : post.messages) {
        if (isVisual(m)) {
          if (visual == null) visual = m;
          visuals++;
        }
      }
      int extra = Math.max(0, visuals - 1);
      boolean thumbs = Tgx101Gazeta.photos() == Tgx101Gazeta.PHOTOS_THUMB;
      android.graphics.ColorMatrixColorFilter filter = Tgx101Gazeta.pictureFilter();
      mediaImage.setColorFilter(filter);
      thumbImage.setColorFilter(filter);
      int token = ++imageToken;
      mediaImage.setImageDrawable(null);
      thumbImage.setImageDrawable(null);
      int width = list != null && list.getWidth() > 0 ? list.getWidth() : Screen.currentWidth();
      boolean withThumb = visual != null && thumbs;
      if (visual == null) {
        media.setVisibility(GONE);
        thumbWrap.setVisibility(GONE);
      } else if (thumbs) {
        media.setVisibility(GONE);
        thumbWrap.setVisibility(VISIBLE);
        String badgeText = extra > 0 ? "+" + extra : isVideo(visual) ? "▶" : null;
        thumbBadge.setText(badgeText);
        thumbBadge.setVisibility(badgeText != null ? VISIBLE : GONE);
        loadImage(visual, thumbImage, token, Screen.dp(THUMB));
      } else {
        thumbWrap.setVisibility(GONE);
        media.setVisibility(VISIBLE);
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

      // the text; beside a thumbnail only as many lines as its height, the rest goes on under it at full width
      int fold = Tgx101Gazeta.fold();
      int maxLines = fold == Tgx101Gazeta.FOLD_NONE || post.expanded ? Integer.MAX_VALUE : fold == Tgx101Gazeta.FOLD_8 ? 8 : 10;
      int fullWidth = width - pad * 2;
      boolean hasText = str != null && str.length() > 0;
      CharSequence top = str, below = null;
      int topLines = 0;
      if (hasText && withThumb) {
        int besideWidth = fullWidth - Screen.dp(THUMB + THUMB_GAP);
        android.text.StaticLayout layout = new android.text.StaticLayout(str, body.getPaint(), Math.max(1, besideWidth), android.text.Layout.Alignment.ALIGN_NORMAL,
          body.getLineSpacingMultiplier(), body.getLineSpacingExtra(), true);
        int lineHeight = Math.max(1, layout.getHeight() / Math.max(1, layout.getLineCount()));
        int fit = Math.max(1, (int) Math.ceil(Screen.dp(THUMB) / (float) lineHeight));
        if (layout.getLineCount() > fit) {
          int end = layout.getLineEnd(fit - 1);
          top = str.subSequence(0, end);
          below = str.subSequence(end, str.length());
          // a line broken in the middle of a word would be odd: the rest starts with no leading spaces or line breaks
          int skip = 0;
          while (skip < below.length() && (below.charAt(skip) == ' ' || below.charAt(skip) == '\n')) skip++;
          below = below.subSequence(skip, below.length());
          topLines = fit;
        } else {
          topLines = layout.getLineCount();
        }
      }
      body.setText(top);
      body.setVisibility(hasText || withThumb ? VISIBLE : GONE);
      body.setMaxLines(maxLines);
      bodyBelow.setVisibility(below != null && below.length() > 0 ? VISIBLE : GONE);
      bodyBelow.setText(below);
      bodyBelow.setMaxLines(maxLines == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, maxLines - topLines));
      int total = below != null ? topLines + lineCount(below, bodyBelow, fullWidth) : lineCount(str, body, withThumb ? fullWidth - Screen.dp(THUMB + THUMB_GAP) : fullWidth);
      boolean cut = maxLines != Integer.MAX_VALUE && total > maxLines;
      more.setVisibility(cut ? VISIBLE : GONE);
      boolean canShort = fold == Tgx101Gazeta.FOLD_SHORT && hasText && text != null && !Td.isEmpty(text) && total > 10
        && !android.text.TextUtils.isEmpty(main.summaryLanguageCode);
      shortBtn.setVisibility(canShort ? VISIBLE : GONE);
      shortBtn.setText(post.summaryLoading ? "✨ …" : post.summaryShown ? Lang.getString(R.string.Tgx101GazetaShortHide) : "✨ " + Lang.getString(R.string.Tgx101AiShort));
      ((View) more.getParent()).setVisibility(cut || canShort ? VISIBLE : GONE);

      // reactions (each one a tap), stars and comments on the left, wrapping; views and time on the right
      reactions.removeAllViews();
      TdApi.MessageInteractionInfo info = main.interactionInfo;
      if (info != null && info.reactions != null) {
        for (TdApi.MessageReaction r : info.reactions.reactions) {
          boolean paid = r.type.getConstructor() == TdApi.ReactionTypePaid.CONSTRUCTOR;
          if (paid ? !Tgx101Gazeta.stars() : !Tgx101Gazeta.reactions()) continue;
          String emoji = r.type.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR ? ((TdApi.ReactionTypeEmoji) r.type).emoji : paid ? "⭐" : "✨";
          // stars cost money: not sent from here, the tap opens the post
          reactions.addView(chip(emoji + " " + Strings.buildCounter(r.totalCount), r.isChosen, pal, paid ? v -> openInChannel(post) : v -> toggleReaction(post, main, r)));
        }
      }
      if (info != null && info.replyInfo != null && info.replyInfo.replyCount > 0) {
        reactions.addView(chip("💬 " + Strings.buildCounter(info.replyInfo.replyCount), false, pal, v -> openInChannel(post)));
      }
      reactions.setVisibility(reactions.getChildCount() > 0 ? VISIBLE : INVISIBLE);
      StringBuilder right = new StringBuilder();
      boolean views = info != null && info.viewCount > 0;
      if (views) right.append(Strings.buildCounter(info.viewCount)).append("   ");
      right.append(Lang.getMessageTimestamp(main.date, TimeUnit.SECONDS));
      meta.setText(right);
      meta.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Math.max(11f, font - 2.5f));
      meta.setCompoundDrawablesWithIntrinsicBounds(views ? eye : null, null, null, null);
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

  private static final float THUMB = 58f, THUMB_GAP = 10f;

  private void openInChannel (Tgx101Gazeta.Post post) {
    tdlib.ui().openMessage(this, post.chatId, new MessageId(post.chatId, post.main().id), null);
  }

  private void rebindPost (Tgx101Gazeta.Post post) {
    int i = posts.indexOf(post);
    if (i != -1) adapter.notifyItemChanged(i);
  }

  // a tap on a reaction puts it or takes it off, as under the post in the channel
  private void toggleReaction (Tgx101Gazeta.Post post, TdApi.Message main, TdApi.MessageReaction r) {
    TdApi.Function<TdApi.Ok> request = r.isChosen
      ? new TdApi.RemoveMessageReaction(main.chatId, main.id, r.type)
      : new TdApi.AddMessageReaction(main.chatId, main.id, r.type, false, true);
    tdlib.send(request, (ok, error) -> {
      if (error != null) {
        UI.post(() -> UI.showError(error));
        return;
      }
      tdlib.send(new TdApi.GetMessage(main.chatId, main.id), (fresh, e2) -> {
        if (fresh == null) return;
        UI.post(() -> {
          if (isDestroyed()) return;
          int at = post.messages.indexOf(main);
          if (at != -1) post.messages.set(at, fresh);
          rebindPost(post);
        });
      });
    });
  }

  private void toggleShort (Tgx101Gazeta.Post post) {
    if (post.summaryShown) {
      post.summaryShown = false;
      rebindPost(post);
      return;
    }
    if (post.summary != null) {
      post.summaryShown = true;
      rebindPost(post);
      return;
    }
    if (post.summaryLoading) return;
    post.summaryLoading = true;
    rebindPost(post);
    TdApi.Message main = post.main();
    String pack = Lang.packId();
    String to = pack != null && pack.length() >= 2 ? pack.substring(0, 2) : "";
    tdlib.send(new TdApi.SummarizeMessage(main.chatId, main.id, to, ""), (result, error) -> UI.post(() -> {
      post.summaryLoading = false;
      if (result != null) {
        post.summary = result;
        post.summaryShown = true;
      } else if (error != null) {
        UI.showError(error);
      }
      if (!isDestroyed()) rebindPost(post);
    }));
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
