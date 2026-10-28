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
import org.thunderdog.challegram.unsorted.Size;
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
public class Tgx101GazetaController extends ViewController<Void> implements Menu, org.thunderdog.challegram.mediaview.MediaViewDelegate {
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
      autoButton = header.addButton(menu, R.id.menu_btn_tgx101GazetaAuto, autoIcon(autoScroll), 49f, this, getHeaderIconColorId());
      // ☀/☾ — the light or the dark look of the feed (user 2026-10-27: switched here, not in the settings)
      profileButton = header.addButton(menu, R.id.menu_btn_tgx101GazetaProfile, profileIcon(), 49f, this, getHeaderIconColorId());
      settingsButton = header.addButton(menu, R.id.menu_btn_settings, R.drawable.baseline_settings_24, 49f, this, getHeaderIconColorId());
      tintHeaderButtons();
    }
  }

  private @Nullable org.thunderdog.challegram.navigation.HeaderButton profileButton, settingsButton;

  // (user 2026-10-28): the header in the feed's own colours
  @Override
  protected int tgx101HeaderColor () {
    return Tgx101Gazeta.palette()[1];
  }

  @Override
  protected int tgx101HeaderTextColor () {
    return Tgx101Gazeta.palette()[2];
  }

  @Override
  protected int getHeaderIconColor () {
    return Tgx101Gazeta.palette()[2];
  }

  private void tintHeaderButtons () {
    int color = Tgx101Gazeta.palette()[2];
    if (profileButton != null) profileButton.tgx101SetForcedColor(color);
    if (autoButton != null) autoButton.tgx101SetForcedColor(color);
    if (settingsButton != null) settingsButton.tgx101SetForcedColor(color);
  }

  private void repaintHeader () {
    tintHeaderButtons();
    if (context().navigation() != null && context().navigation().getHeaderView() != null && isFocused()) {
      context().navigation().getHeaderView().resetColors(this, null);
    }
  }

  // (user 2026-10-28 «шапка скрывается при прокрутке»; video 01:50 «дёргание текста» — the feed moving with the header
  // jerked): the feed lies under the header and never moves for it — only the header slides away over the posts going
  // up and back with them coming down, settling fully shown or hidden when the finger lets go
  private int headerHide;
  private FrameLayout inner;
  private @Nullable android.animation.ValueAnimator headerAnimator;

  private void setHeaderHide (int value) {
    int max = Size.getHeaderPortraitSize();
    value = Math.max(0, Math.min(max, value));
    if (value == headerHide) return;
    headerHide = value;
    applyHeaderHide();
  }

  private void settleHeader () {
    int max = Size.getHeaderPortraitSize();
    LinearLayoutManager manager = list != null ? (LinearLayoutManager) list.getLayoutManager() : null;
    boolean atTop = manager != null && !list.canScrollVertically(-1);
    int target = atTop || headerHide < max / 2 ? 0 : max;
    if (target == headerHide) return;
    if (headerAnimator != null) headerAnimator.cancel();
    headerAnimator = android.animation.ValueAnimator.ofInt(headerHide, target);
    headerAnimator.setDuration(180);
    headerAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
    headerAnimator.addUpdateListener(a -> setHeaderHide((int) a.getAnimatedValue()));
    headerAnimator.start();
  }

  private void applyHeaderHide () {
    int max = Size.getHeaderPortraitSize();
    org.thunderdog.challegram.navigation.HeaderView header = context().navigation() != null ? context().navigation().getHeaderView() : null;
    if (header != null && (isFocused() || headerHide == 0)) {
      header.setTranslationY(-headerHide);
      float alpha = 1f - headerHide / (float) max;
      for (int i = 0; i < header.getChildCount(); i++) header.getChildAt(i).setAlpha(alpha);
    }
  }

  /** the feed reaches up under the header only while it's on screen; the window's layout is left as it was */
  private void setUnderHeader (boolean under) {
    if (root != null && root.getParent() instanceof ViewGroup) {
      ViewGroup parent = (ViewGroup) root.getParent();
      parent.setClipToPadding(!under);
      parent.setClipChildren(!under);
    }
  }

  private static int profileIcon () {
    return Tgx101Gazeta.profile() == Tgx101Gazeta.PROFILE_DARK ? R.drawable.tgx101_sun_24 : R.drawable.baseline_brightness_2_24;
  }

  private void applyPalette () {
    if (root != null) root.setBackgroundColor(Tgx101Gazeta.palette()[0]);
    if (empty != null) empty.setTextColor(Tgx101Gazeta.palette()[3]);
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_tgx101GazetaAuto) {
      setAutoScroll(!autoScroll);
    } else if (id == R.id.menu_btn_tgx101GazetaProfile) {
      Tgx101Gazeta.setProfile(Tgx101Gazeta.profile() == Tgx101Gazeta.PROFILE_DARK ? Tgx101Gazeta.PROFILE_LIGHT : Tgx101Gazeta.PROFILE_DARK);
      if (profileButton != null) profileButton.setImageResource(profileIcon());
      applyPalette();
      repaintHeader();
      adapter.notifyDataSetChanged();
    } else if (id == R.id.menu_btn_settings) {
      needReload = true;
      navigateTo(new Tgx101GazetaSettingsController(context, tdlib).setFeed(this));
    }
  }

  private final List<Tgx101Gazeta.Post> posts = new ArrayList<>();

  // a new post of a channel in the feed comes in at the bottom (an album's next part joins its card); the feed
  // follows it only when it was at the very end
  private final Tgx101Gazeta.NewPostListener newPosts = this::onNewPost;

  private void onNewPost (TdApi.Message message) {
    if (isDestroyed() || !loaded) return;
    if (org.thunderdog.challegram.data.Tgx101MessageFilters.match(tdlib, message, tdlib.chat(message.chatId)) != null) return;
    boolean atEnd = list != null && !list.canScrollVertically(1);
    Tgx101Gazeta.Post last = posts.isEmpty() ? null : posts.get(posts.size() - 1);
    if (last != null && message.mediaAlbumId != 0 && last.chatId == message.chatId && last.first().mediaAlbumId == message.mediaAlbumId) {
      last.messages.add(message);
      adapter.notifyItemChanged(posts.size() - 1);
    } else {
      Tgx101Gazeta.Post post = Tgx101Gazeta.newPost(message);
      posts.add(post);
      adapter.notifyItemInserted(posts.size() - 1);
      if (posts.size() > 1) adapter.notifyItemChanged(posts.size() - 2); // a channel's run: one header
    }
    empty.setVisibility(View.GONE);
    if (atEnd && list != null) list.post(() -> list.smoothScrollToPosition(posts.size() - 1));
  }
  private RecyclerView list;
  private TextView empty;
  private final Adapter adapter = new Adapter();
  private boolean loaded;
  private FrameLayout root;

  @Override
  protected View onCreateView (Context context) {
    root = new FrameLayout(context);
    root.setClipChildren(false);
    inner = new FrameLayout(context);
    FrameLayout.LayoutParams innerParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    innerParams.topMargin = -Size.getHeaderPortraitSize(); // under the header: it slides away over the posts
    root.addView(inner, innerParams);
    list = new org.thunderdog.challegram.v.CustomRecyclerView(context);
    list.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    list.setItemAnimator(null);
    list.setAdapter(adapter);
    list.setClipToPadding(false);
    list.setPadding(0, Screen.dp(4f) + Size.getHeaderPortraitSize(), 0, Screen.dp(4f) + MainController.tgx101CapsuleSpace());
    list.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
      @Override
      public boolean onInterceptTouchEvent (@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
        if (autoScroll) {
          if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) tapPausedAuto = autoMoving();
          autoPausedUntil = android.os.SystemClock.uptimeMillis() + 3000;
        }
        return false;
      }
    });
    list.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        markVisibleRead();
        if (loaded && isFocused() && (headerAnimator == null || !headerAnimator.isRunning())) setHeaderHide(headerHide + dy);
        rememberPlace();
      }

      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE && isFocused()) settleHeader();
      }
    });
    inner.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    empty = new TextView(context);
    empty.setGravity(Gravity.CENTER);
    empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    empty.setTextColor(Theme.getColor(ColorId.textLight));
    empty.setPadding(Screen.dp(32f), 0, Screen.dp(32f), 0);
    empty.setText(Lang.getString(R.string.Tgx101GazetaLoading));
    inner.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    applyPalette();
    Tgx101Gazeta.addNewPostListener(tdlib, newPosts);
    load();
    return root;
  }

  private boolean needReload;

  @Override
  public void onFocus () {
    super.onFocus();
    setUnderHeader(true);
    repaintHeader();
    applyPalette();
    adapter.notifyDataSetChanged(); // the look may have changed in the settings
    if (needReload) {
      needReload = false;
      load();
    }
  }

  @Override
  public void destroy () {
    Tgx101Gazeta.removeNewPostListener(newPosts);
    headerHide = 0;
    applyHeaderHide();
    super.destroy();
  }

  @Override
  public void onBlur () {
    setAutoScroll(false);
    // the header is the whole window's: it is back in place before another screen takes it
    if (headerAnimator != null) headerAnimator.cancel();
    headerHide = 0;
    applyHeaderHide();
    setUnderHeader(false);
    super.onBlur();
    // (video 01:50 «логика возврата в газету»): back from a channel or comments the feed stays where it was; it is
    // gathered again only after its settings, where the channels may have changed
  }

  private void load () {
    Tgx101Gazeta.loadChannels(tdlib, chatIds -> {
      if (isDestroyed()) return;
      Tgx101Gazeta.loadFeed(tdlib, chatIds, (feed, firstUnread) -> {
        if (isDestroyed()) return;
        loaded = true;
        rememberedIndex = -1;
        posts.clear();
        posts.addAll(feed);
        adapter.notifyDataSetChanged();
        empty.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(Lang.getString(chatIds.length == 0 ? R.string.Tgx101GazetaNoChannels : R.string.Tgx101GazetaEmpty));
        org.thunderdog.challegram.Tgx101Diag.mark("gazeta: " + chatIds.length + " channels, " + posts.size() + " posts, first unread " + firstUnread);
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
        if (manager != null && !posts.isEmpty()) {
          // the first unread post; with none — the post the feed was left on; else the newest
          int at = firstUnread;
          if (at == -1) at = placeIndex();
          if (at == -1) at = posts.size() - 1;
          manager.scrollToPositionWithOffset(at, Screen.dp(4f) + Size.getHeaderPortraitSize());
        }
        list.post(this::markVisibleRead);
      });
    });
  }

  private String placeKey () {
    return "tgx101_gazeta_at_" + tdlib.id();
  }

  private int rememberedIndex = -1;

  /** the post at the top of the feed, kept for the next opening */
  private void rememberPlace () {
    LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
    if (manager == null || !loaded) return;
    int first = manager.findFirstCompletelyVisibleItemPosition();
    if (first == RecyclerView.NO_POSITION) first = manager.findFirstVisibleItemPosition();
    if (first < 0 || first >= posts.size() || first == rememberedIndex) return;
    rememberedIndex = first; // written once per post, not on every scrolled pixel
    Tgx101Gazeta.Post post = posts.get(first);
    org.thunderdog.challegram.unsorted.Settings.instance().pmc().putLongArray(placeKey(), new long[] {post.chatId, post.first().id});
  }

  private int placeIndex () {
    long[] at = org.thunderdog.challegram.unsorted.Settings.instance().pmc().getLongArray(placeKey());
    if (at == null || at.length != 2) return -1;
    for (int i = 0; i < posts.size(); i++) {
      Tgx101Gazeta.Post post = posts.get(i);
      if (post.chatId != at[0]) continue;
      for (TdApi.Message m : post.messages) if (m.id == at[1]) return i;
    }
    return -1;
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
    showMiniMenu(post, main, outIds, titles.toArray(new String[0]), outIcons, id -> {
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
    });
  }

  private static android.graphics.drawable.Drawable ripple () {
    int pressed = me.vkryl.core.ColorUtils.alphaColor(.12f, Theme.getColor(ColorId.text));
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(pressed), null, new android.graphics.drawable.ColorDrawable(0xffffffff));
    }
    android.graphics.drawable.StateListDrawable d = new android.graphics.drawable.StateListDrawable();
    d.addState(new int[] {android.R.attr.state_pressed}, new android.graphics.drawable.ColorDrawable(pressed));
    return d;
  }

  private interface MenuPick {
    void onPick (int id);
  }

  private float touchRawX = -1, touchRawY = -1;
  private @Nullable android.widget.PopupWindow miniMenu;

  /**
   * (user 2026-10-28 «выпадающее меню — как у нас в системе, и расположение тоже»): the message menu's look and place —
   * a dim (or blur) over the screen, the reactions in a pill above a 16 dp-round card of 44 dp rows, the card at the
   * right edge (the left one for the left hand), at the finger when MagiX says so, else above the bottom
   */
  private void showMiniMenu (Tgx101Gazeta.Post post, TdApi.Message main, int[] ids, String[] titles, int[] icons, MenuPick pick) {
    if (miniMenu != null) miniMenu.dismiss();
    Context ctx = context();
    org.thunderdog.challegram.unsorted.Settings settings = org.thunderdog.challegram.unsorted.Settings.instance();
    boolean leftHand = settings.isTgx101MessageMenuLeftHand();
    int maxCardWidth = Math.min(Screen.dp(268f), Screen.currentWidth() - Screen.dp(24f));
    int cardWidth = Math.max(Math.min(Screen.dp(200f), maxCardWidth), maxCardWidth - Screen.dp(52f));
    int itemWidth = Screen.dp(38f);
    int reactionCount = Math.max(1, (cardWidth + Screen.dp(32f) - Screen.dp(18f)) / itemWidth);
    int pillWidth = Screen.dp(18f) + reactionCount * itemWidth;
    int overhang = Math.max(0, (pillWidth - cardWidth) / 2);
    int cardEdgeGap = Math.max(overhang, Math.min(Screen.dp(52f), maxCardWidth - cardWidth + overhang));

    FrameLayout rootView = new FrameLayout(ctx);
    android.graphics.drawable.Drawable dim = Tgx101MessageMenu.blurredBackground(this);
    dim.setAlpha(0);
    rootView.setBackground(dim);
    android.widget.PopupWindow popup = new android.widget.PopupWindow(rootView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
    miniMenu = popup;
    popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
    popup.setClippingEnabled(false);
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) popup.setAttachedInDecor(false);

    LinearLayout column = new LinearLayout(ctx);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setGravity(leftHand ? Gravity.LEFT : Gravity.RIGHT);
    int navInset = settings.useEdgeToEdge() && context().getRootView().getSystemInsetsWithoutIme() != null ? context().getRootView().getSystemInsetsWithoutIme().bottom : 0;
    FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | (leftHand ? Gravity.LEFT : Gravity.RIGHT));
    columnParams.setMargins(Screen.dp(12f), Screen.dp(24f), Screen.dp(12f), Screen.dp(68f) + navInset);
    rootView.addView(column, columnParams);
    rootView.setOnClickListener(v -> popup.dismiss());

    boolean canReact = Tgx101Gazeta.reactions() && main.interactionInfo != null && main.interactionInfo.reactions != null;
    LinearLayout pill = new LinearLayout(ctx);
    pill.setOrientation(LinearLayout.HORIZONTAL);
    pill.setGravity(Gravity.CENTER);
    pill.setPadding(Screen.dp(9f), 0, Screen.dp(9f), 0);
    pill.setBackground(Tgx101MessageMenu.rounded(Theme.getColor(ColorId.filling), Screen.dp(26f)));
    Tgx101MessageMenu.elevate(pill, Screen.dp(26f));
    pill.setClickable(true);
    LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(pillWidth, Screen.dp(52f));
    pillParams.bottomMargin = Screen.dp(8f);
    if (leftHand) pillParams.leftMargin = cardEdgeGap - overhang;
    else pillParams.rightMargin = cardEdgeGap - overhang;
    if (canReact) {
      pill.setVisibility(View.INVISIBLE); // shown once the channel's reactions are known
      column.addView(pill, pillParams);
    }

    LinearLayout card = new LinearLayout(ctx);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(Tgx101MessageMenu.rounded(Theme.getColor(ColorId.filling), Screen.dp(16f)));
    Tgx101MessageMenu.elevate(card, Screen.dp(16f));
    card.setClickable(true);
    TextView head = new TextView(ctx);
    head.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    head.setTypeface(Fonts.getRobotoRegular());
    head.setTextColor(Theme.getColor(ColorId.textLight));
    head.setSingleLine(true);
    head.setEllipsize(TextUtils.TruncateAt.END);
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.setPadding(Screen.dp(16f), Screen.dp(10f), Screen.dp(16f), Screen.dp(10f));
    head.setMinHeight(Screen.dp(40f));
    head.setText(tdlib.chatTitle(post.chatId));
    card.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    card.addView(Tgx101MessageMenu.divider(ctx));
    for (int i = 0; i < ids.length; i++) {
      final int id = ids[i];
      TextView row = new TextView(ctx);
      row.setTypeface(Fonts.getRobotoRegular());
      row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      row.setSingleLine(true);
      row.setEllipsize(TextUtils.TruncateAt.END);
      row.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
      row.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
      row.setCompoundDrawablePadding(Screen.dp(14f));
      row.setTextColor(Theme.getColor(ColorId.text));
      row.setText(titles[i]);
      org.thunderdog.challegram.unsorted.Settings.tgx101StyleRow(row, 15f);
      android.graphics.drawable.Drawable icon = org.thunderdog.challegram.tool.Drawables.get(ctx.getResources(), icons[i]);
      if (icon != null) {
        icon = icon.mutate();
        icon.setColorFilter(org.thunderdog.challegram.tool.Paints.getColorFilter(Theme.getColor(ColorId.icon)));
        icon.setBounds(0, 0, Screen.dp(21f), Screen.dp(21f));
        row.setCompoundDrawables(icon, null, null, null);
      }
      org.thunderdog.challegram.tool.Views.setClickable(row);
      org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(row);
      row.setOnClickListener(v -> {
        popup.dismiss();
        pick.onPick(id);
      });
      card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(44f)));
    }
    // (user 2026-10-28 «шире на 10%, но чтобы не сместилось вправо — там место под палец»): it grows away from the edge
    int wideCard = Math.min((int) (cardWidth * 1.1f), Screen.currentWidth() - cardEdgeGap - Screen.dp(36f));
    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(wideCard, ViewGroup.LayoutParams.WRAP_CONTENT);
    if (leftHand) cardParams.leftMargin = cardEdgeGap;
    else cardParams.rightMargin = cardEdgeGap;
    column.addView(card, cardParams);

    if (canReact) {
      tdlib.send(new TdApi.GetMessageAvailableReactions(main.chatId, main.id, reactionCount), (available, error) -> {
        if (available == null) return;
        UI.post(() -> {
          if (!popup.isShowing()) return;
          // (user 2026-10-28 «иконки, что разрешены каналом, а не случайные»): what the channel allows — the ones already
          // under the post first, then the channel's own list in its order; no «popular» or «recent» from elsewhere
          java.util.List<TdApi.ReactionType> types = new ArrayList<>();
          java.util.Set<String> seen = new java.util.HashSet<>();
          java.util.List<TdApi.ReactionType> allowed = new ArrayList<>();
          for (TdApi.AvailableReaction r : available.topReactions) if (!r.needsPremium) allowed.add(r.type);
          java.util.Set<String> allowedKeys = new java.util.HashSet<>();
          for (TdApi.ReactionType t : allowed) allowedKeys.add(reactionKey(t));
          if (main.interactionInfo != null && main.interactionInfo.reactions != null) {
            for (TdApi.MessageReaction mr : main.interactionInfo.reactions.reactions) {
              if (mr.type.getConstructor() == TdApi.ReactionTypePaid.CONSTRUCTOR) continue;
              if (allowedKeys.contains(reactionKey(mr.type)) && seen.add(reactionKey(mr.type))) types.add(mr.type);
            }
          }
          for (TdApi.ReactionType t : allowed) if (seen.add(reactionKey(t))) types.add(t);
          int count = 0;
          for (TdApi.ReactionType type : types) {
            if (count >= reactionCount) break;
            ReactionIcon e = new ReactionIcon(ctx);
            e.set(tdlib, type);
            boolean isChosen = false;
            if (main.interactionInfo != null && main.interactionInfo.reactions != null) {
              for (TdApi.MessageReaction mr : main.interactionInfo.reactions.reactions) if (mr.isChosen && reactionKey(mr.type).equals(reactionKey(type))) isChosen = true;
            }
            e.setOnClickListener(v -> {
              popup.dismiss();
              TdApi.MessageReaction chosen = null;
              for (TdApi.MessageReaction mr : main.interactionInfo.reactions.reactions) {
                if (reactionKey(mr.type).equals(reactionKey(type))) chosen = mr;
              }
              toggleReaction(post, main, chosen != null ? chosen : new TdApi.MessageReaction(type, 0, false, null, new TdApi.MessageSender[0]));
            });
            FrameLayout cell = new FrameLayout(ctx);
            if (isChosen) {
              GradientDrawable on = new GradientDrawable();
              on.setCornerRadius(Screen.dp(19f));
              on.setColor(Theme.getColor(ColorId.fillingPositive));
              View bubble = new View(ctx);
              bubble.setBackground(on);
              cell.addView(bubble, new FrameLayout.LayoutParams(Screen.dp(38f), Screen.dp(38f), Gravity.CENTER));
            }
            cell.addView(e, new FrameLayout.LayoutParams(Screen.dp(40f), Screen.dp(40f), Gravity.CENTER));
            cell.setClipChildren(false);
            cell.setOnClickListener(v -> e.performClick());
            pill.addView(cell, new LinearLayout.LayoutParams(itemWidth, Screen.dp(52f)));
            pill.setClipChildren(false);
            count++;
          }
          if (count > 0) pill.setVisibility(View.VISIBLE);
          else column.removeView(pill);
        });
      });
    }

    if (settings.tgx101MenuAtFinger() && touchRawY >= 0) {
      final float touchY = touchRawY;
      final int bottomLimit = columnParams.bottomMargin;
      column.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange (View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
          v.removeOnLayoutChangeListener(this);
          int minTop = Screen.getStatusBarHeight() + Screen.dp(24f);
          int maxTop = rootView.getHeight() - bottomLimit - v.getHeight();
          int top = Math.max(minTop, Math.min(maxTop, (int) touchY - Screen.dp(64f)));
          v.setTranslationY(top - v.getTop());
        }
      });
    }

    column.setAlpha(0f);
    column.setScaleX(.92f);
    column.setScaleY(.92f);
    column.post(() -> {
      column.setPivotX(leftHand ? 0 : column.getWidth());
      column.setPivotY(column.getHeight());
      column.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    });
    android.animation.ValueAnimator fade = android.animation.ValueAnimator.ofInt(0, 255);
    fade.setDuration(220);
    fade.addUpdateListener(a -> dim.setAlpha((int) a.getAnimatedValue()));
    fade.start();
    popup.showAtLocation(list, Gravity.NO_GRAVITY, 0, 0);
  }

  /** the look screen's sample: a card of the feed, bound to the post on top (or the first one) */
  @Nullable
  public View createPreview (Context context) {
    if (posts.isEmpty()) return null;
    Card card = new Card(context);
    card.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    bindPreview(card);
    return card;
  }

  public void bindPreview (View preview) {
    if (!(preview instanceof Card) || posts.isEmpty()) return;
    Card card = (Card) preview;
    if (card.post != null && posts.contains(card.post)) {
      card.bindSample(card.post); // the same post while the screen is open — only the look changes
      return;
    }
    int at = 0;
    LinearLayoutManager manager = list != null ? (LinearLayoutManager) list.getLayoutManager() : null;
    if (manager != null && manager.findFirstVisibleItemPosition() >= 0) at = Math.min(posts.size() - 1, manager.findFirstVisibleItemPosition() + 1);
    // (user 2026-10-28 «нет примера, как будет меняться картинка»): a post with a picture and text first, so the
    // filters show on it; a picture alone, then text alone, if the feed has nothing better
    int best = -1, rank = -1;
    for (int k = 0; k < posts.size() && rank < 2; k++) {
      int i = (at + k) % posts.size();
      Tgx101Gazeta.Post p = posts.get(i);
      boolean visual = false;
      for (TdApi.Message m : p.messages) if (isVisual(m)) visual = true;
      TdApi.FormattedText t = Td.textOrCaption(p.main().content);
      boolean text = t != null && !Td.isEmpty(t);
      // (user 2026-10-28 «фото вообще убери из примера»): text only — the pictures have their colour strip
      int r = text && !visual ? 2 : text ? 1 : -1;
      if (r > rank) {
        rank = r;
        best = i;
      }
    }
    if (best != -1) at = best;
    ((Card) preview).bindSample(posts.get(at));
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

  /**
   * chips left to right, a new row when the next doesn't fit (user: «реакции спускаются вторым рядом»); the last child
   * — views and time — keeps to the right of the last row, or below it on the right, as under a post in a channel
   */
  private static final class FlowLayout extends ViewGroup {
    private final int gap = Screen.dp(6f);
    @Nullable View trailing;

    FlowLayout (Context context) {
      super(context);
    }

    private int place (int width, boolean layout) {
      int left = getPaddingLeft(), top = getPaddingTop();
      width -= getPaddingLeft() + getPaddingRight();
      int x = 0, y = 0, rowHeight = 0;
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        if (child.getVisibility() == GONE) continue;
        int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
        if (child == trailing) {
          int tx = width - w;
          if (x > 0 && x > tx - gap) {
            y += rowHeight + gap;
            rowHeight = 0;
          }
          int ty = y + Math.max(0, (rowHeight - h) / 2);
          if (layout) child.layout(left + tx, top + (rowHeight > 0 ? ty : y), left + width, top + (rowHeight > 0 ? ty : y) + h);
          rowHeight = Math.max(rowHeight, h);
          continue;
        }
        if (x > 0 && x + w > width) {
          x = 0;
          y += rowHeight + gap;
          rowHeight = 0;
        }
        if (layout) child.layout(left + x, top + y, left + x + w, top + y + h);
        x += w + gap;
        rowHeight = Math.max(rowHeight, h);
      }
      return y + rowHeight;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      int inner = Math.max(0, width - getPaddingLeft() - getPaddingRight());
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        if (child.getVisibility() != GONE) child.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
      }
      setMeasuredDimension(width, place(width, false) + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      place(r - l, true);
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
    final TextView title, summary, body, bodyBelow, more, shortBtn, meta, commentsText;
    final FlowLayout reactions;
    final LinearLayout comments, repliers;
    final ImageView commentsIcon, commentsArrow;
    final View commentsLine;
    final FrameLayout media, thumbWrap;
    final ImageView mediaImage, thumbImage;
    final TextView mediaBadge, mediaCount, thumbBadge;
    final android.graphics.drawable.Drawable eye;
    ShortIcon shortIcon;
    @Nullable Tgx101Gazeta.Post post;
    int position;
    int imageToken;
    @Nullable String loadedImageKey;
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
      shortIcon = new ShortIcon(context);
      shortIcon.setOnClickListener(v -> {
        if (post != null) toggleShort(post);
      });
      LinearLayout.LayoutParams sip = new LinearLayout.LayoutParams(Screen.dp(30f), Screen.dp(30f));
      sip.leftMargin = Screen.dp(8f);
      header.addView(shortIcon, sip);
      header.setOnClickListener(v -> {
        if (post != null) tdlib.ui().openChat(Tgx101GazetaController.this, post.chatId, null);
      });
      addView(header);

      media = new FrameLayout(context);
      mediaImage = new ImageView(context);
      mediaImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
      media.setOnClickListener(v -> {
        if (post != null && !sample) openMedia(post);
      });
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
      thumbWrap.setOnClickListener(v -> {
        if (post != null && !sample) openMedia(post);
      });
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
          smoothly();
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

      reactions = new FlowLayout(context);
      reactions.setClipChildren(false);
      reactions.setClipToPadding(false);
      setClipChildren(false);
      reactions.setPadding(pad, Screen.dp(8f), pad, Screen.dp(10f));
      meta = text(context, 12.5f, false);
      meta.setGravity(Gravity.CENTER_VERTICAL);
      meta.setCompoundDrawablePadding(Screen.dp(3f));
      addView(reactions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      // «Leave a comment» / «N comments» at the bottom of the post, as in channels
      comments = new LinearLayout(context);
      comments.setOrientation(HORIZONTAL);
      comments.setGravity(Gravity.CENTER_VERTICAL);
      comments.setPadding(pad, 0, Screen.dp(8f), 0);
      commentsIcon = new ImageView(context);
      commentsIcon.setImageResource(R.drawable.outline_forum_24);
      comments.addView(commentsIcon, new LinearLayout.LayoutParams(Screen.dp(22f), Screen.dp(22f)));
      repliers = new LinearLayout(context);
      repliers.setOrientation(HORIZONTAL);
      LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      rlp.leftMargin = Screen.dp(10f);
      comments.addView(repliers, rlp);
      commentsText = text(context, 15f, true);
      LinearLayout.LayoutParams ctp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      ctp.leftMargin = Screen.dp(10f);
      comments.addView(commentsText, ctp);
      commentsArrow = new ImageView(context);
      commentsArrow.setImageResource(R.drawable.round_keyboard_arrow_right_24);
      comments.addView(commentsArrow, new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f)));
      comments.setOnClickListener(v -> {
        if (post != null) openComments(post);
      });
      commentsLine = new View(context);
      addView(commentsLine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Screen.dp(.5f))));
      addView(comments, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(46f)));
      // the views' eye as on channel posts
      eye = org.thunderdog.challegram.tool.Drawables.get(getResources(), R.drawable.baseline_visibility_14).mutate();

      setOnClickListener(v -> {
        if (tapPausedAuto) {
          tapPausedAuto = false; // the tap that paused the autoscroll opens nothing
          return;
        }
        if (post != null) openPostMenu(post);
      });
      setOnTouchListener((v, e) -> {
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
          touchRawX = e.getRawX();
          touchRawY = e.getRawY();
        }
        return false;
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

    private View chip (TdApi.ReactionType type, CharSequence count, boolean chosen, @Nullable View.OnClickListener click) {
      ReactionChip v = new ReactionChip(getContext());
      v.set(tdlib, type, count.toString(), chosen);
      if (click != null) v.setOnClickListener(click);
      return v;
    }

    boolean sample;

    void bindSample (Tgx101Gazeta.Post post) {
      sample = true;
      bind(post, 0);
      media.setVisibility(GONE);
      thumbWrap.setVisibility(GONE);
      header.setVisibility(VISIBLE);
      avatar.setVisibility(VISIBLE);
      title.setVisibility(VISIBLE);
      avatar.setChat(tdlib, tdlib.chat(post.chatId));
      avatar.attach();
      title.setText(tdlib.chatTitle(post.chatId));
      setOnClickListener(null);
      setClickable(false);
      comments.setOnClickListener(null);
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
      // (crash 02:09): the look screen's sample sits in a plain layout, not in the list
      RecyclerView.LayoutParams lp = getLayoutParams() instanceof RecyclerView.LayoutParams ? (RecyclerView.LayoutParams) getLayoutParams() : null;
      if (lp != null) {
        lp.topMargin = samePrev ? Math.max(1, Screen.dp(.5f)) : Screen.dp(GAP / 2f);
        lp.bottomMargin = sameNext ? 0 : Screen.dp(GAP / 2f);
      }
      title.setTextColor(pal[2]);
      title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, font);
      if (!samePrev) {
        avatar.setChat(tdlib, tdlib.chat(post.chatId));
        title.setText(tdlib.chatTitle(post.chatId));
      }

      float spacing = Tgx101Gazeta.lineSpacing() / 14f;
      android.graphics.Typeface face = Tgx101Gazeta.typeface(false);
      for (TextView t : new TextView[] {body, bodyBelow, summary}) {
        t.setTextColor(pal[2]);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, font);
        t.setTypeface(face);
        t.setLineSpacing(Screen.dp(2f), spacing);
      }
      title.setTypeface(Tgx101Gazeta.typeface(true));
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
      // the same picture again (the look screen's sample on every slider move): kept, only its colours change
      String imageKey = visual != null ? visual.chatId + ":" + visual.id : null;
      boolean sameImage = imageKey != null && imageKey.equals(loadedImageKey);
      loadedImageKey = imageKey;
      int token = sameImage ? imageToken : ++imageToken;
      if (!sameImage) {
        mediaImage.setImageDrawable(null);
        thumbImage.setImageDrawable(null);
      } else if (thumbs && thumbImage.getDrawable() == null) {
        thumbImage.setImageDrawable(mediaImage.getDrawable());
      } else if (!thumbs && mediaImage.getDrawable() == null) {
        mediaImage.setImageDrawable(thumbImage.getDrawable());
      }
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
        if (!sameImage || thumbImage.getDrawable() == null) loadImage(visual, thumbImage, token, Screen.dp(THUMB));
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
        if (!sameImage || mediaImage.getDrawable() == null) loadImage(visual, mediaImage, token, width);
      }

      // the text; beside a thumbnail only as many lines as its height, the rest goes on under it at full width
      int fold = Tgx101Gazeta.fold();
      int maxLines = post.expanded ? Integer.MAX_VALUE : 9;
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
      boolean summarizable = summarizable(main);
      boolean shortInstead = cut && fold == Tgx101Gazeta.FOLD_SHORT && summarizable && !post.summaryShown;
      more.setVisibility(cut && !shortInstead ? VISIBLE : GONE);
      shortBtn.setVisibility(shortInstead ? VISIBLE : GONE);
      shortBtn.setText(post.summaryLoading ? Lang.getString(R.string.Tgx101AiShortLoading) : Lang.getString(R.string.Tgx101AiShort));
      ((LinearLayout.LayoutParams) shortBtn.getLayoutParams()).leftMargin = 0;
      ((View) more.getParent()).setVisibility(cut ? VISIBLE : GONE);
      // the «Short» sign top right, as on a channel's long post — whatever the folding
      shortIcon.setVisibility(summarizable ? VISIBLE : GONE);
      shortIcon.setState(post.summaryShown, post.summaryLoading, pal[4]);
      header.setVisibility(!samePrev || summarizable ? VISIBLE : GONE);
      avatar.setVisibility(samePrev ? GONE : VISIBLE);
      title.setVisibility(samePrev ? INVISIBLE : VISIBLE);
      header.setPadding(pad, samePrev ? Screen.dp(4f) : Screen.dp(10f), Screen.dp(10f), samePrev ? 0 : Screen.dp(8f));

      // reactions (each one a tap) and stars, wrapping; views and time on the right of the last row — as in a channel
      reactions.removeAllViews();
      TdApi.MessageInteractionInfo info = main.interactionInfo;
      if (info != null && info.reactions != null) {
        for (TdApi.MessageReaction r : info.reactions.reactions) {
          boolean paid = r.type.getConstructor() == TdApi.ReactionTypePaid.CONSTRUCTOR;
          if (paid ? !Tgx101Gazeta.stars() : !Tgx101Gazeta.reactions()) continue;
          // stars cost money: not sent from here, the tap opens the post
          reactions.addView(chip(r.type, Strings.buildCounter(r.totalCount), r.isChosen, paid ? v -> openInChannel(post) : v -> toggleReaction(post, main, r)));
        }
      }
      StringBuilder right = new StringBuilder();
      boolean views = info != null && info.viewCount > 0;
      if (views) right.append(Strings.buildCounter(info.viewCount)).append("   ");
      right.append(Lang.getMessageTimestamp(main.date, TimeUnit.SECONDS));
      meta.setText(right);
      meta.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Math.max(11f, font - 2.5f));
      meta.setCompoundDrawablesWithIntrinsicBounds(views ? eye : null, null, null, null);
      reactions.addView(meta);
      reactions.trailing = meta;

      TdApi.MessageReplyInfo replyInfo = info != null ? info.replyInfo : null;
      boolean hasComments = replyInfo != null && Tgx101Gazeta.comments();
      comments.setVisibility(hasComments ? VISIBLE : GONE);
      commentsLine.setVisibility(hasComments ? VISIBLE : GONE);
      reactions.setPadding(pad, Screen.dp(8f), pad, Screen.dp(hasComments ? 8f : 10f));
      if (hasComments) {
        commentsLine.setBackgroundColor(me.vkryl.core.ColorUtils.alphaColor(.12f, pal[2]));
        commentsText.setTextColor(pal[4]);
        commentsText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Math.max(13f, font - 1f));
        commentsText.setText(replyInfo.replyCount > 0 ? Lang.plural(R.string.xComments, replyInfo.replyCount) : Lang.getString(R.string.LeaveComment));
        commentsIcon.setColorFilter(pal[4]);
        commentsArrow.setColorFilter(pal[3]);
        repliers.removeAllViews();
        int n = replyInfo.recentReplierIds != null ? Math.min(3, replyInfo.recentReplierIds.length) : 0;
        for (int i = 0; i < n; i++) {
          AvatarView a = new AvatarView(getContext());
          a.setMessageSender(tdlib, replyInfo.recentReplierIds[i]);
          LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Screen.dp(22f), Screen.dp(22f));
          if (i > 0) alp.leftMargin = -Screen.dp(6f);
          repliers.addView(a, alp);
          a.attach();
        }
        repliers.setVisibility(n > 0 ? VISIBLE : GONE);
        commentsIcon.setVisibility(n > 0 ? GONE : VISIBLE);
      }
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

  /**
   * (user 2026-10-28, screenshots of a channel and of the feed side by side): a reaction drawn as TGReactions draws it
   * under a channel post — the bubble of {@code getReactionBubbleHeight()}, the reaction's picture of
   * {@code getReactionImageSize()} sticking out of it on the left, the count in the reactions' text size after it
   */
  static final class ReactionChip extends View {
    private final org.thunderdog.challegram.loader.ImageReceiver receiver;
    private float scale = 1f;
    private @Nullable String fallback;
    private String count = "";
    private boolean chosen;
    private final android.text.TextPaint textPaint = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF rect = new android.graphics.RectF();

    ReactionChip (Context context) {
      super(context);
      receiver = new org.thunderdog.challegram.loader.ImageReceiver(this, 0);
      textPaint.setTypeface(Fonts.getRobotoRegular());
    }

    void set (Tdlib tdlib, TdApi.ReactionType type, String count, boolean chosen) {
      this.count = count;
      this.chosen = chosen;
      org.thunderdog.challegram.data.TGReaction reaction = tdlib.getReaction(type);
      org.thunderdog.challegram.component.sticker.TGStickerObj sticker = reaction != null ? reaction.staticCenterAnimationSicker() : null;
      org.thunderdog.challegram.loader.ImageFile image = sticker != null ? sticker.getImage() : null;
      fallback = image == null ? (type.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR ? ((TdApi.ReactionTypeEmoji) type).emoji : type.getConstructor() == TdApi.ReactionTypePaid.CONSTRUCTOR ? "⭐" : "❤") : null;
      scale = sticker != null ? sticker.getDisplayScale() : 1f;
      receiver.requestFile(image);
      requestLayout();
      invalidate();
    }

    private static float textSize () {
      return org.thunderdog.challegram.data.TGMessage.reactionsTextStyleProvider().getTextSizeInDp();
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      textPaint.setTextSize(Screen.dp(textSize()));
      int height = org.thunderdog.challegram.data.TGReactions.getReactionBubbleHeight();
      int addW = Screen.dp((textSize() + 1f) / 3f);
      int width = (int) (textPaint.measureText(count) + org.thunderdog.challegram.data.TGReactions.getReactionImageSize() + addW);
      setMeasuredDimension(width, height);
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      receiver.attach();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      receiver.detach();
    }

    @Override
    protected void onDraw (android.graphics.Canvas c) {
      int height = getHeight();
      float radius = height / 2f;
      rect.set(0, 0, getWidth(), height);
      c.drawRoundRect(rect, radius, radius, org.thunderdog.challegram.tool.Paints.fillingPaint(Theme.getColor(chosen ? ColorId.fillingPositive : ColorId.fillingActive)));
      textPaint.setTextSize(Screen.dp(textSize()));
      textPaint.setColor(Theme.getColor(chosen ? ColorId.fillingPositiveContent : ColorId.fillingActiveContent));
      float textX = height + Screen.dp(1f) + Screen.dp(3f);
      android.graphics.Paint.FontMetrics fm = textPaint.getFontMetrics();
      c.drawText(count, textX, height / 2f - (fm.ascent + fm.descent) / 2f, textPaint);
      int imageSize = org.thunderdog.challegram.data.TGReactions.getReactionImageSize();
      int imgY = (height - imageSize) / 2;
      if (fallback != null) {
        android.text.TextPaint e = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        e.setTextSize(imageSize * .42f);
        float w = e.measureText(fallback);
        c.drawText(fallback, Screen.dp(-1f) + (imageSize - w) / 2f, height / 2f + imageSize * .15f, e);
        return;
      }
      receiver.setBounds(Screen.dp(-1f), imgY, Screen.dp(-1f) + imageSize, imgY + imageSize);
      receiver.drawScaled(c, scale);
    }
  }

  /** the static picture Telegram gives a reaction (what channel posts show), or its emoji while that isn't there */
  static final class ReactionIcon extends View {
    private final org.thunderdog.challegram.loader.ImageReceiver receiver;
    private float scale = 1f;
    private @Nullable String fallback;
    private final android.text.TextPaint paint = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);

    ReactionIcon (Context context) {
      super(context);
      receiver = new org.thunderdog.challegram.loader.ImageReceiver(this, 0);
    }

    void set (Tdlib tdlib, TdApi.ReactionType type) {
      org.thunderdog.challegram.data.TGReaction reaction = tdlib.getReaction(type);
      org.thunderdog.challegram.component.sticker.TGStickerObj sticker = reaction != null ? reaction.staticCenterAnimationSicker() : null;
      org.thunderdog.challegram.loader.ImageFile image = sticker != null ? sticker.getImage() : null;
      fallback = image == null ? (type.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR ? ((TdApi.ReactionTypeEmoji) type).emoji : type.getConstructor() == TdApi.ReactionTypePaid.CONSTRUCTOR ? "⭐" : "❤") : null;
      scale = sticker != null ? sticker.getDisplayScale() : 1f;
      receiver.requestFile(image);
      invalidate();
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      receiver.attach();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      receiver.detach();
    }

    @Override
    protected void onDraw (android.graphics.Canvas c) {
      if (fallback != null) {
        paint.setTextSize(getHeight() * .8f);
        float w = paint.measureText(fallback);
        c.drawText(fallback, (getWidth() - w) / 2f, getHeight() * .8f, paint);
        return;
      }
      receiver.setBounds(0, 0, getWidth(), getHeight());
      receiver.drawScaled(c, scale);
    }
  }

  // ---- autoscroll (user 2026-10-28): the feed moves by itself at the speed set in its look; a finger may still swipe;
  // any touch pauses it, and it goes on after 3 s with the screen left alone. A tap on a moving feed only pauses it

  private boolean autoScroll;
  private long autoPausedUntil, autoLastFrame;
  private float autoCarry;
  private boolean tapPausedAuto;
  private @Nullable org.thunderdog.challegram.navigation.HeaderButton autoButton;

  private final Runnable autoTick = new Runnable() {
    @Override
    public void run () {
      if (!autoScroll || list == null || isDestroyed()) return;
      long now = android.os.SystemClock.uptimeMillis();
      long dt = autoLastFrame == 0 ? 16 : Math.min(64, now - autoLastFrame);
      autoLastFrame = now;
      if (now >= autoPausedUntil && list.getScrollState() == RecyclerView.SCROLL_STATE_IDLE && isFocused()) {
        autoCarry += Screen.dp(Tgx101Gazeta.AUTO_DP_PER_SECOND[Tgx101Gazeta.autoSpeed() - 1]) * dt / 1000f;
        int step = (int) autoCarry;
        if (step > 0) {
          autoCarry -= step;
          if (!list.canScrollVertically(1)) {
            setAutoScroll(false); // the newest post reached
            return;
          }
          list.scrollBy(0, step);
        }
      }
      list.postOnAnimation(this);
    }
  };

  private static int autoIcon (boolean on) {
    return on ? R.drawable.baseline_pause_24 : R.drawable.tgx101_autoscroll_24;
  }

  private void setAutoScroll (boolean on) {
    if (autoScroll == on) return;
    autoScroll = on;
    autoLastFrame = 0;
    autoCarry = 0;
    autoPausedUntil = 0;
    if (autoButton != null) autoButton.setImageResource(autoIcon(on));
    if (on && list != null) list.postOnAnimation(autoTick);
  }

  private boolean autoMoving () {
    return autoScroll && android.os.SystemClock.uptimeMillis() >= autoPausedUntil;
  }

  private static String reactionKey (TdApi.ReactionType t) {
    switch (t.getConstructor()) {
      case TdApi.ReactionTypeEmoji.CONSTRUCTOR: return "e" + ((TdApi.ReactionTypeEmoji) t).emoji;
      case TdApi.ReactionTypeCustomEmoji.CONSTRUCTOR: return "c" + ((TdApi.ReactionTypeCustomEmoji) t).customEmojiId;
      default: return "p";
    }
  }

  private static boolean summarizable (TdApi.Message m) {
    TdApi.FormattedText text = Td.textOrCaption(m.content);
    return org.thunderdog.challegram.unsorted.Settings.instance().tgx101AiSummary() && text != null && !Td.isEmpty(text)
      && !android.text.TextUtils.isEmpty(m.summaryLanguageCode);
  }

  /** the «Short» sign of channel posts: a wand over two lines in a round, lit while the summary is shown or coming */
  private static final class ShortIcon extends View {
    private boolean lit, loading;
    private int accent;
    private final android.graphics.Path spark = new android.graphics.Path();

    ShortIcon (Context context) {
      super(context);
    }

    void setState (boolean shown, boolean loading, int accent) {
      this.lit = shown || loading;
      this.loading = loading;
      this.accent = accent;
      invalidate();
    }

    private void drawSpark (android.graphics.Canvas c, float cx, float cy, float r, android.graphics.Paint paint) {
      float k = r * .28f;
      spark.reset();
      spark.moveTo(cx, cy - r);
      spark.lineTo(cx + k, cy - k);
      spark.lineTo(cx + r, cy);
      spark.lineTo(cx + k, cy + k);
      spark.lineTo(cx, cy + r);
      spark.lineTo(cx - k, cy + k);
      spark.lineTo(cx - r, cy);
      spark.lineTo(cx - k, cy - k);
      spark.close();
      c.drawPath(spark, paint);
    }

    @Override
    protected void onDraw (android.graphics.Canvas c) {
      float size = Math.min(getWidth(), getHeight());
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      c.drawCircle(cx, cy, size / 2f, org.thunderdog.challegram.tool.Paints.fillingPaint(me.vkryl.core.ColorUtils.alphaColor(lit ? 1f : .16f, accent)));
      int ink = lit ? 0xffffffff : accent;
      float u = size * .78f / 24f, ox = cx - 12 * u, oy = cy - 12 * u;
      android.graphics.Paint thin = org.thunderdog.challegram.tool.Paints.getProgressPaint(ink, 2f * u);
      c.drawLine(ox + 4 * u, oy + 15 * u, ox + 13 * u, oy + 15 * u, thin);
      c.drawLine(ox + 4 * u, oy + 19 * u, ox + 10 * u, oy + 19 * u, thin);
      c.drawLine(ox + 11 * u, oy + 11 * u, ox + 19 * u, oy + 3 * u, org.thunderdog.challegram.tool.Paints.getProgressPaint(ink, 2.2f * u));
      android.graphics.Paint fill = org.thunderdog.challegram.tool.Paints.fillingPaint(ink);
      drawSpark(c, ox + 6.5f * u, oy + 6.2f * u, 2.2f * u, fill);
      drawSpark(c, ox + 19f * u, oy + 13.1f * u, 2.1f * u, fill);
      if (loading) {
        float inset = Screen.dp(1.5f);
        android.graphics.RectF arc = new android.graphics.RectF(cx - size / 2f + inset, cy - size / 2f + inset, cx + size / 2f - inset, cy + size / 2f - inset);
        float angle = (android.os.SystemClock.uptimeMillis() % 900L) / 900f * 360f;
        c.drawArc(arc, angle, 100f, false, org.thunderdog.challegram.tool.Paints.getProgressPaint(lit ? 0xffffffff : accent, Screen.dp(2f)));
        invalidate();
      }
    }
  }

  /** (task: «просмотр медиа в ленте») the post's photos and videos in the viewer, from the first; an album as a whole */
  private void openMedia (Tgx101Gazeta.Post post) {
    if (tapPausedAuto) {
      tapPausedAuto = false;
      return;
    }
    List<org.thunderdog.challegram.mediaview.data.MediaItem> items = new ArrayList<>();
    for (TdApi.Message m : post.messages) {
      if (!isVisual(m)) continue;
      org.thunderdog.challegram.mediaview.data.MediaItem item = org.thunderdog.challegram.mediaview.data.MediaItem.valueOf(context(), tdlib, m);
      if (item != null) items.add(item);
    }
    if (items.isEmpty()) {
      openInChannel(post);
      return;
    }
    org.thunderdog.challegram.mediaview.data.MediaStack stack = new org.thunderdog.challegram.mediaview.data.MediaStack(context(), tdlib);
    stack.set(0, items);
    // (user 2026-10-28 «сворачивание — супер плавным, как везде»): the viewer flies out of the picture and back into it
    viewerPost = post;
    org.thunderdog.challegram.mediaview.MediaViewController.Args args = new org.thunderdog.challegram.mediaview.MediaViewController.Args(this,
      org.thunderdog.challegram.mediaview.MediaViewController.MODE_SIMPLE, this, null, null, stack);
    args.setForceThumbs(items.size() > 1);
    org.thunderdog.challegram.mediaview.MediaViewController viewer = new org.thunderdog.challegram.mediaview.MediaViewController(context(), tdlib);
    viewer.setArguments(args);
    viewer.open();
  }

  private @Nullable Tgx101Gazeta.Post viewerPost;
  private final org.thunderdog.challegram.mediaview.MediaViewThumbLocation viewerLocation = new org.thunderdog.challegram.mediaview.MediaViewThumbLocation();

  /** the card's picture the viewer opened from: the big one, or the thumbnail */
  @Nullable
  private View viewerTarget () {
    if (viewerPost == null || list == null) return null;
    int i = posts.indexOf(viewerPost);
    if (i == -1) return null;
    RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(i);
    if (holder == null || !(holder.itemView instanceof Card)) return null;
    Card card = (Card) holder.itemView;
    if (card.media.getVisibility() == View.VISIBLE) return card.mediaImage;
    if (card.thumbWrap.getVisibility() == View.VISIBLE) return card.thumbImage;
    return null;
  }

  @Override
  public org.thunderdog.challegram.mediaview.MediaViewThumbLocation getTargetLocation (int indexInStack, org.thunderdog.challegram.mediaview.data.MediaItem item) {
    View target = viewerTarget();
    if (target == null || target.getWidth() == 0) return null;
    int[] loc = new int[2];
    target.getLocationInWindow(loc);
    viewerLocation.set(loc[0], loc[1], loc[0] + target.getWidth(), loc[1] + target.getHeight());
    // what the header or the screen's edge covers is cut off, as in a chat
    int[] listLoc = new int[2];
    list.getLocationInWindow(listLoc);
    int visibleTop = listLoc[1] + Size.getHeaderPortraitSize() - headerHide;
    int clipTop = Math.max(0, visibleTop - loc[1]);
    int clipBottom = Math.max(0, loc[1] + target.getHeight() - (listLoc[1] + list.getHeight()));
    viewerLocation.setClip(0, Math.min(clipTop, target.getHeight()), 0, Math.min(clipBottom, target.getHeight()));
    viewerLocation.setRoundings(isThumb(target) ? Screen.dp(8f) : 0);
    return viewerLocation;
  }

  private boolean isThumb (View target) {
    RecyclerView.ViewHolder holder = viewerPost != null ? list.findViewHolderForAdapterPosition(posts.indexOf(viewerPost)) : null;
    return holder != null && holder.itemView instanceof Card && ((Card) holder.itemView).thumbImage == target;
  }

  @Override
  public void setMediaItemVisible (int index, org.thunderdog.challegram.mediaview.data.MediaItem item, boolean isVisible) {
    View target = viewerTarget();
    if (target != null) target.setAlpha(isVisible ? 1f : 0f); // the picture's place is empty while it's in the viewer
  }

  // the post's comments, as its «Leave a comment» bar in the channel opens them
  private void openComments (Tgx101Gazeta.Post post) {
    TdApi.Message main = post.main();
    tdlib.send(new TdApi.GetMessageThread(main.chatId, main.id), (thread, error) -> UI.post(() -> {
      if (isDestroyed()) return;
      if (thread == null) {
        if (error != null) UI.showError(error);
        return;
      }
      org.thunderdog.challegram.data.ThreadInfo info = org.thunderdog.challegram.data.ThreadInfo.openedFromChat(tdlib, thread, main.chatId);
      tdlib.ui().openChat(this, thread.chatId, new org.thunderdog.challegram.telegram.TdlibUi.ChatOpenParameters().keepStack().messageThread(info));
    }));
  }

  private void openInChannel (Tgx101Gazeta.Post post) {
    tdlib.ui().openMessage(this, post.chatId, new MessageId(post.chatId, post.main().id), null);
  }

  /** (user 2026-10-28 «сворачивание — супер плавно, как везде»): the next change of a card's height glides */
  private void smoothly () {
    if (list != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
      android.transition.AutoTransition t = new android.transition.AutoTransition();
      t.setDuration(260);
      t.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
      android.transition.TransitionManager.beginDelayedTransition(list, t);
    }
  }

  private void rebindPost (Tgx101Gazeta.Post post) {
    smoothly();
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
