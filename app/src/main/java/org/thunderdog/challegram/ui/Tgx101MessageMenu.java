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
import android.graphics.Outline;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ReactionsSelectorRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101: compact message menu in the style of Telegram for iOS — reactions in a pill, then a
 * card with the read time, the actions in the user's order and "Delete" at the bottom. The card
 * sits in the lower right or left corner, for the hand chosen in MagiX.
 */
public final class Tgx101MessageMenu {
  private Tgx101MessageMenu () { }

  /** Actions the user can reorder, in the default order. Pin and Unpin share one place. */
  public static final int[] ORDERABLE_IDS = {
    R.id.btn_messageReply,
    R.id.btn_messageCopy,
    R.id.btn_tgx101SelectInPlace, // user 2026-10-06: «Select» text in the bubble; can't be hidden
    R.id.btn_messageEdit,
    R.id.btn_messageShare,
    R.id.btn_tgx101SaveFavorite, // user 2026-10-06: «Save» to Saved Messages
    R.id.btn_messagePin,
    R.id.btn_messageSelectText,
    R.id.btn_chatTranslate,
    R.id.btn_messageCopyLink,
    R.id.btn_saveFile,
    R.id.btn_messageReport,
    R.id.btn_tgx101FilterSimilar,
    R.id.btn_tgx101EditorWindow,
    R.id.btn_messageViewList,
    R.id.btn_messageReplies,
    R.id.btn_messageShowSource, // «To the original» / «Clear cache» (user's video 22:42): orderable, can go to «More…» / be hidden
    R.id.btn_deleteFile,
    R.id.btn_messageMore, // user 2026-10-06: «More…» is ordered too, by default third from the end: More, Delete, Select
    R.id.btn_messageDelete // TGx101 (user 2026-10-05): «Delete» is ordered like the rest; «Select» is always last
  };
  public static final int[] ORDERABLE_NAMES = {
    R.string.Reply, R.string.Copy, R.string.Tgx101SelectInPlace, R.string.edit, R.string.Share, R.string.Tgx101SaveFavorite, R.string.MessagePin,
    R.string.Tgx101MenuSelectText, R.string.Translate, R.string.CopyLink, R.string.Save, R.string.MessageReport,
    R.string.Tgx101FilterSimilar, R.string.Tgx101MenuEditorOwn, R.string.Tgx101MenuMessagesFrom,
    R.string.Tgx101MenuThread, R.string.Tgx101MenuToOriginal, R.string.DeleteFromCache, R.string.MoreMessageOptions, R.string.Delete
  };
  public static final int[] ORDERABLE_ICONS = {
    R.drawable.baseline_reply_24, R.drawable.baseline_content_copy_24, R.drawable.tgx101_select_all_24, R.drawable.baseline_edit_24,
    R.drawable.baseline_forward_24, R.drawable.baseline_bookmark_24, R.drawable.deproko_baseline_pin_24, R.drawable.baseline_format_quote_close_24,
    R.drawable.baseline_translate_24, R.drawable.baseline_link_24, R.drawable.baseline_file_download_24,
    R.drawable.baseline_report_24, R.drawable.baseline_filter_variant_remove_24, R.drawable.baseline_format_text_24,
    R.drawable.baseline_person_24, R.drawable.outline_forum_24, R.drawable.baseline_forum_24, R.drawable.templarian_baseline_broom_24,
    R.drawable.baseline_more_horiz_24, R.drawable.baseline_delete_24
  };

  /** user 2026-10-06: a light menu by default — these stay in it, everything else orderable goes under «More…» */
  public static final int[] DEFAULT_SHOWN = {
    R.id.btn_messageReply, R.id.btn_messageCopy, R.id.btn_tgx101SelectInPlace, R.id.btn_messageEdit, R.id.btn_messageShare,
    R.id.btn_tgx101SaveFavorite, R.id.btn_messageMore, R.id.btn_messageDelete
  };

  /** user 2026-10-06: photos, videos and files have their own menu — «Save» always there, «Copy» / «Select» not */
  public static final int[] DEFAULT_SHOWN_MEDIA = {
    R.id.btn_messageReply, R.id.btn_saveFile, R.id.btn_messageShare, R.id.btn_tgx101SaveFavorite, R.id.btn_messageEdit,
    R.id.btn_messageMore, R.id.btn_messageDelete
  };

  /** user 2026-10-06 12:04: voice and video messages have their own menu — «Save to music», transcription in «More…» */
  public static final int[] DEFAULT_SHOWN_VOICE = {
    R.id.btn_messageReply, R.id.btn_saveFile, R.id.btn_messageShare, R.id.btn_tgx101SaveFavorite,
    R.id.btn_messageMore, R.id.btn_messageDelete
  };

  /** Which menu (and settings) a message uses: text, media / files, or voice / video messages */
  public static int menuProfileOf (TGMessage message) {
    TdApi.MessageContent content = message.getMessage().content;
    int type = content != null ? content.getConstructor() : 0;
    if (type == TdApi.MessageVoiceNote.CONSTRUCTOR || type == TdApi.MessageVideoNote.CONSTRUCTOR) return Settings.TGX101_MENU_VOICE;
    if (message instanceof org.thunderdog.challegram.data.TGMessageMedia || message instanceof org.thunderdog.challegram.data.TGMessageFile
      || message instanceof org.thunderdog.challegram.data.TGMessageVideo) return Settings.TGX101_MENU_MEDIA;
    return Settings.TGX101_MENU_TEXT;
  }

  public static boolean isMediaMessage (TGMessage message) {
    return menuProfileOf(message) != Settings.TGX101_MENU_TEXT;
  }

  private static int[] defaultShown () {
    switch (Settings.instance().getTgx101MenuProfile()) {
      case Settings.TGX101_MENU_MEDIA: return DEFAULT_SHOWN_MEDIA;
      case Settings.TGX101_MENU_VOICE: return DEFAULT_SHOWN_VOICE;
      default: return DEFAULT_SHOWN;
    }
  }

  /** The default order of the current profile: its shown items first (More and Delete last), the rest in between */
  private static int[] defaultOrder () {
    if (!Settings.instance().isTgx101MenuMediaProfile()) return ORDERABLE_IDS;
    ArrayList<Integer> result = new ArrayList<>();
    for (int id : defaultShown()) if (id != R.id.btn_messageMore && id != R.id.btn_messageDelete) result.add(id);
    for (int id : ORDERABLE_IDS) if (!result.contains(id) && id != R.id.btn_messageMore && id != R.id.btn_messageDelete) result.add(id);
    result.add(R.id.btn_messageMore);
    result.add(R.id.btn_messageDelete);
    int[] out = new int[result.size()];
    for (int i = 0; i < out.length; i++) out[i] = result.get(i);
    return out;
  }

  /** Can't be hidden (they can still be moved, and «Select text» can go under «More…») */
  public static boolean canHide (int id) {
    return id != R.id.btn_tgx101SelectInPlace && id != R.id.btn_messageMore;
  }

  /** user 2026-10-06: the menu keeps 5–9 items (with «Select»), the user decides; the rest under «More…» or hidden.
   *  Only a menu over the limit (an old setup, or the app's own extra items) scrolls. */
  public static final int MIN_MENU_ITEMS = 5, MAX_MENU_ITEMS = 9, MAX_VISIBLE_ROWS = MAX_MENU_ITEMS;

  /** The «More…» list in effect (the user's, or the default one) */
  public static int[] moreIds () {
    int[] saved = Settings.instance().getTgx101MessageMenuMore();
    if (saved != null) return saved;
    ArrayList<Integer> result = new ArrayList<>();
    for (int id : ORDERABLE_IDS) {
      boolean shown = false;
      for (int s : defaultShown()) if (s == id) shown = true;
      if (!shown) result.add(id);
    }
    int[] out = new int[result.size()];
    for (int i = 0; i < out.length; i++) out[i] = result.get(i);
    return out;
  }

  private static int orderKey (int id) {
    if (id == R.id.btn_tgx101DownloadSave) return R.id.btn_saveFile; // «Save» before the file is downloaded
    return id == R.id.btn_messageUnpin ? R.id.btn_messagePin : id;
  }

  /** Orderable ids in the user's order */
  public static int[] getOrder () {
    int[] saved = Settings.instance().getTgx101MessageMenuOrder();
    ArrayList<Integer> result = new ArrayList<>();
    if (saved != null) {
      for (int id : saved) {
        for (int known : ORDERABLE_IDS) {
          if (known == id && !result.contains(id)) {
            result.add(id);
          }
        }
      }
    }
    // actions added in later versions go above «Delete» (where the default order has them), not under it
    for (int known : defaultOrder()) {
      if (!result.contains(known)) {
        int deleteAt = result.indexOf(R.id.btn_messageDelete);
        if (deleteAt != -1 && known != R.id.btn_messageDelete) {
          result.add(deleteAt, known);
        } else {
          result.add(known);
        }
      }
    }
    int[] out = new int[result.size()];
    for (int i = 0; i < out.length; i++) out[i] = result.get(i);
    return out;
  }

  /** Hidden in Settings → MagiX → message menu (Pin hides Unpin too) */
  public static boolean isHidden (int id) {
    if (!canHide(id)) return false;
    int key = orderKey(id);
    for (int hidden : Settings.instance().getTgx101MessageMenuHidden()) {
      if (hidden == key) return true;
    }
    return false;
  }

  /** Moved under «More…» in Settings → MagiX → message menu */
  public static boolean isInMore (int id) {
    int key = orderKey(id);
    for (int more : moreIds()) {
      if (more == key) return true;
    }
    return false;
  }

  // The actions moved under «More…» for the menu being shown, and what the menu itself shows
  private static List<ViewController.OptionItem> pendingMore = new ArrayList<>();
  private static final java.util.HashSet<Integer> shownIds = new java.util.HashSet<>();

  /** For the «More…» list: the user's moved actions of the menu shown last (kept: «More…» can be opened again after «Back») */
  public static List<ViewController.OptionItem> takePendingMore () {
    return Settings.instance().useTgx101MessageMenu() ? new ArrayList<>(pendingMore) : new ArrayList<>();
  }

  public static boolean isShownInMenu (int id) {
    return shownIds.contains(id);
  }

  private static int rank (int[] order, int id) {
    int key = orderKey(id);
    for (int i = 0; i < order.length; i++) {
      if (order[i] == key) return i;
    }
    return order.length; // not orderable: after the ordered ones, in the app's own order
  }

  /** «More…» items arrive (maybe after a server request) with their own handler */
  public interface MoreCallback {
    void onMoreLoaded (List<ViewController.OptionItem> items, OptionDelegate delegate);
  }

  public interface MoreLoader {
    void load (MoreCallback callback);
  }

  private static class Host {
    PopupLayout popup;
    View content;
    boolean dismissing;
    @Nullable MoreLoader moreLoader;
    LinearLayout list; // the card's actions
    android.widget.ScrollView scroll; // the actions scroll when there are more than MAX_VISIBLE_ROWS
    @Nullable Drawable background; // the blurred screen behind the menu, faded in and out
    @Nullable Runnable selectInPlace; // «Select» text: the in-bubble selection where the finger first touched
    final List<View> mainRows = new ArrayList<>();
    @Nullable ViewGroup reactions; // the reactions pill: sliding the finger there picks a reaction too (user 2026-10-06)
    // all reactions (user 2026-10-06, variants 1 + 5): ⌄ unfolds a grid of still pictures right under the pill and
    // hides the actions; ⌃ folds it back. In the iOS slide, holding the finger on ⌄ unfolds it too
    @Nullable View card, expandView;
    @Nullable LinearLayout grid;
    @Nullable ViewGroup gridReactions;
    @Nullable LinearLayout column;
    @Nullable Runnable hoverExpand;
    final List<View> bottomViews = new ArrayList<>(); // «Delete» and its divider: hidden inside «More…»
    boolean moreShown, moreLoading;
    TextView readDateView;
    View readDateDivider;
  }

  /** TGx101 diagnostics: what invisible characters / formatting a message has (counts only, never the text) */
  private static void tgx101LogTextShape (TGMessage message) {
    try {
      org.drinkless.tdlib.TdApi.FormattedText text = tgx.td.Td.textOrCaption(message.getNewestMessage().content);
      if (text == null || text.text == null) return;
      String t = text.text;
      int tabs = 0, cr = 0, lf = 0, nbsp = 0, ls = 0, ps = 0, vt = 0, ff = 0, zw = 0, wide = 0, maxSpaces = 0, run = 0;
      for (int i = 0; i < t.length(); i++) {
        char ch = t.charAt(i);
        if (ch == ' ') { run++; maxSpaces = Math.max(maxSpaces, run); continue; }
        run = 0;
        switch (ch) {
          case '\t': tabs++; break;
          case '\r': cr++; break;
          case '\n': lf++; break;
          case '\u00A0': nbsp++; break;
          case '\u2028': ls++; break;
          case '\u2029': ps++; break;
          case '\u000B': vt++; break;
          case '\u000C': ff++; break;
          case '\u200B': case '\u200C': case '\u200D': case '\uFEFF': zw++; break;
          default:
            if (Character.getType(ch) == Character.SPACE_SEPARATOR) wide++;
        }
      }
      StringBuilder entities = new StringBuilder();
      if (text.entities != null) {
        for (org.drinkless.tdlib.TdApi.TextEntity e : text.entities) {
          String name = e.type.getClass().getSimpleName().replace("TextEntityType", "");
          if (entities.indexOf(name) < 0) entities.append(name).append(' ');
        }
      }
      org.thunderdog.challegram.Tgx101Diag.mark("text shape: len=" + t.length() + " lf=" + lf + " cr=" + cr + " tab=" + tabs + " nbsp=" + nbsp +
        " ls=" + ls + " ps=" + ps + " vt=" + vt + " ff=" + ff + " zw=" + zw + " otherSpaces=" + wide + " maxSpaceRun=" + maxSpaces +
        " entities=[" + entities.toString().trim() + "] type=" + message.getClass().getSimpleName());
    } catch (Throwable ignored) { }
  }

  // TGx101 (user 2026-10-05, «как iOS»): the menu opened by a long press follows the same finger — slide to an item
  // and lift the finger to run it

  private static java.lang.ref.WeakReference<Host> dragHost = new java.lang.ref.WeakReference<>(null);
  public static boolean dragArmed;
  private static View dragRow;
  private static boolean dragMoved;
  private static float dragStartX, dragStartY;

  public static boolean dragActive () {
    return dragArmed && dragHost.get() != null;
  }

  /** The finger of the long press moved / lifted; true — consumed */
  public static boolean drag (float rawX, float rawY, int action) {
    Host host = dragHost.get();
    if (!dragArmed || host == null || host.dismissing) {
      dragArmed = false;
      return false;
    }
    if (dragStartX < 0) dragStartX = rawX;
    if (!dragMoved && Math.hypot(rawX - dragStartX, rawY - dragStartY) > Screen.dp(12f)) dragMoved = true;
    if (host.scroll != null && action == android.view.MotionEvent.ACTION_MOVE) {
      // sliding to the card's top / bottom edge scrolls the actions (user 2026-10-06: «удобный свайп»)
      int[] sl = new int[2];
      host.scroll.getLocationOnScreen(sl);
      int edge = Screen.dp(28f);
      if (rawX >= sl[0] && rawX <= sl[0] + host.scroll.getWidth()) {
        if (rawY < sl[1] + edge && rawY > sl[1] - Screen.dp(40f)) host.scroll.scrollBy(0, -Screen.dp(10f));
        else if (rawY > sl[1] + host.scroll.getHeight() - edge && rawY < sl[1] + host.scroll.getHeight() + Screen.dp(40f)) host.scroll.scrollBy(0, Screen.dp(10f));
      }
    }
    View hit = null;
    for (View row : host.mainRows) {
      if (!row.isShown()) continue; // also skips the actions hidden behind the reactions grid
      int[] loc = new int[2];
      row.getLocationOnScreen(loc);
      if (rawX >= loc[0] && rawX <= loc[0] + row.getWidth() && rawY >= loc[1] && rawY <= loc[1] + row.getHeight()) {
        hit = row;
        break;
      }
    }
    if (hit == null && host.gridReactions != null && host.grid != null && host.grid.getVisibility() == View.VISIBLE) {
      for (int i = 0; i < host.gridReactions.getChildCount(); i++) {
        View child = host.gridReactions.getChildAt(i);
        int[] loc = new int[2];
        child.getLocationOnScreen(loc);
        if (rawX >= loc[0] && rawX <= loc[0] + child.getWidth() && rawY >= loc[1] && rawY <= loc[1] + child.getHeight()) {
          hit = child;
          break;
        }
      }
    }
    // variant 5: the finger rests on ⌄ → the grid unfolds under it, the slide goes on to a reaction there
    boolean onExpand = false;
    if (hit == null && host.expandView != null && (host.grid == null || host.grid.getVisibility() != View.VISIBLE)) {
      int[] loc = new int[2];
      host.expandView.getLocationOnScreen(loc);
      int pad = Screen.dp(8f);
      onExpand = rawX >= loc[0] - pad && rawX <= loc[0] + host.expandView.getWidth() + pad && rawY >= loc[1] - pad && rawY <= loc[1] + host.expandView.getHeight() + pad;
    }
    if (onExpand && host.hoverExpand == null) {
      final Host h = host;
      host.hoverExpand = () -> {
        h.hoverExpand = null;
        if (h.expandView != null && !h.dismissing) h.expandView.performClick();
      };
      host.expandView.postDelayed(host.hoverExpand, 350);
    } else if (!onExpand && host.hoverExpand != null && host.expandView != null) {
      host.expandView.removeCallbacks(host.hoverExpand);
      host.hoverExpand = null;
    }
    if (hit == null && !onExpand && host.reactions != null) {
      // only inside the visible part of the row: a reaction half hidden under ⌄ must not light up (user 2026-10-06 11:26)
      int[] rv = new int[2];
      host.reactions.getLocationOnScreen(rv);
      int visibleRight = rv[0] + host.reactions.getWidth();
      if (host.expandView != null) {
        int[] ex = new int[2];
        host.expandView.getLocationOnScreen(ex);
        visibleRight = Math.min(visibleRight, ex[0] - Screen.dp(8f));
      }
      for (int i = 0; i < host.reactions.getChildCount() && rawX >= rv[0] && rawX <= visibleRight; i++) {
        View child = host.reactions.getChildAt(i);
        int[] loc = new int[2];
        child.getLocationOnScreen(loc);
        if (rawX >= loc[0] && rawX <= loc[0] + child.getWidth() && rawY >= loc[1] - Screen.dp(8f) && rawY <= loc[1] + child.getHeight() + Screen.dp(8f)) {
          hit = child;
          break;
        }
      }
    }
    if (hit != dragRow) {
      if (dragRow != null) {
        dragRow.setPressed(false);
      }
      dragRow = hit;
      if (hit != null) {
        hit.setPressed(true);
        if (org.thunderdog.challegram.unsorted.Settings.instance().tgx101Haptics()) hit.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
      }
    }
    magnifyReactions(host.reactions, rawX, rawY, true, action);
    magnifyReactions(host.gridReactions, rawX, rawY, false, action);
    if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
      dragArmed = false;
      View row = dragRow;
      dragRow = null;
      if (row != null) {
        row.setPressed(false);
        if (dragMoved && action == android.view.MotionEvent.ACTION_UP) {
          org.thunderdog.challegram.Tgx101Diag.mark("menu: item picked by sliding");
          row.performClick();
        }
      }
    }
    return true;
  }

  /** Where the finger touched the message (raw screen Y) — the menu opens next to it (user 2026-10-05, variant 1) */
  public static float lastTouchRawY = -1, lastTouchRawX = -1;
  public static long lastTouchAt;

  public static PopupLayout show (MessagesController c, TGMessage message, ViewController.Options options, OptionDelegate delegate,
                                  boolean readDatePending, @Nullable MoreLoader moreLoader, Runnable onExpandReactions, Runnable onDismissPrepare, Runnable onDismiss) {
    Context context = c.context();
    tgx101LogTextShape(message);
    Settings.instance().setTgx101MenuProfile(menuProfileOf(message)); // text, media and voice have separate menus
    Host host = new Host();
    host.moreLoader = moreLoader;
    PopupLayout popup = new PopupLayout(context);
    host.popup = popup;
    dragHost = new java.lang.ref.WeakReference<>(host);
    // TGx101 (user 2026-10-06 12:09, voice message with a transcription): the menu can be opened by another long-press
    // path (text inside the bubble) that didn't arm the slide — arm it whenever the finger is still down in iOS mode
    if (!dragArmed && Settings.instance().tgx101LongPressMenu() && org.thunderdog.challegram.v.MessagesRecyclerView.tgx101FingerDown) {
      dragArmed = true;
      org.thunderdog.challegram.Tgx101Diag.mark("menu: slide armed (finger still down)");
    }
    dragRow = null;
    dragMoved = false;
    dragStartX = -1;
    dragStartY = lastTouchRawY;
    popup.init(true);
    popup.setNeedRootInsets();
    popup.setOverlayStatusBar(true);

    boolean leftHand = Settings.instance().isTgx101MessageMenuLeftHand();
    // TGx101: the reactions keep their width; the action card is narrower and moved toward the centre,
    // leaving a free strip at the screen edge — a tap there closes the menu (like the official app)
    // The reactions stick out of the card by REACTIONS_OVERHANG on both sides
    int maxCardWidth = Math.min(Screen.dp(268f), Screen.currentWidth() - Screen.dp(24f));
    int cardWidth = Math.max(Math.min(Screen.dp(200f), maxCardWidth), maxCardWidth - Screen.dp(52f));
    // The card keeps its width (user 2026-09-30); long items use short names in this menu, see shortName()
    // The reactions pill fits whole reactions and sticks out of the card by ~16–20 dp on each side
    int overhangMin = Screen.dp(REACTIONS_OVERHANG);
    int pillChrome = Screen.dp(18f) + Screen.dp(44f);
    int reactionCount = Math.max(1, (int) Math.ceil((cardWidth + overhangMin * 2 - pillChrome) / (float) Screen.dp(REACTION_ITEM_WIDTH)));
    int pillWidth = pillChrome + reactionCount * Screen.dp(REACTION_ITEM_WIDTH);
    int overhang = (pillWidth - cardWidth) / 2;
    // Free strip at the edge (tap closes the menu): up to 52 dp, never less than the reactions' overhang
    int cardEdgeGap = Math.max(overhang, Math.min(Screen.dp(52f), maxCardWidth - cardWidth + overhang));

    FrameLayout root = new FrameLayout(context);
    // TGx101 (user 2026-10-06 12:05 «блюр слишком резко»): the blurred background fades in / out with the menu
    final Drawable background = blurredBackground(c);
    background.setAlpha(0);
    root.setBackground(background);
    host.background = background;
    android.animation.ValueAnimator fadeIn = android.animation.ValueAnimator.ofInt(0, 255);
    fadeIn.setDuration(220);
    fadeIn.setInterpolator(new android.view.animation.DecelerateInterpolator());
    fadeIn.addUpdateListener(a -> background.setAlpha((int) a.getAnimatedValue()));
    fadeIn.start();
    root.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    root.setOnClickListener(v -> dismiss(host));
    // TGx101: a long press on the message itself (under the blurred menu) closes the menu and selects the word under
    // the finger — text selection on the second long press instead of the third
    final float[] down = new float[2];
    root.setOnTouchListener((v, e) -> {
      if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
        down[0] = e.getRawX();
        down[1] = e.getRawY();
      }
      return false;
    });
    final float firstX = lastTouchRawX, firstY = lastTouchRawY;
    host.selectInPlace = () -> {
      View messageView = message.findCurrentView();
      if (!(messageView instanceof org.thunderdog.challegram.component.chat.MessageView)) {
        c.tgx101OpenSelectText(message);
        return;
      }
      int[] location = new int[2];
      messageView.getLocationOnScreen(location);
      org.thunderdog.challegram.Tgx101Diag.mark("select: «Select» in the menu");
      c.tgx101OpenSelectText(message, (org.thunderdog.challegram.component.chat.MessageView) messageView, firstX - location[0], firstY - location[1]);
    };
    root.setOnLongClickListener(v -> {
      View messageView = message.findCurrentView();
      if (!(messageView instanceof org.thunderdog.challegram.component.chat.MessageView)) return false;
      int[] location = new int[2];
      messageView.getLocationOnScreen(location);
      float x = down[0] - location[0], y = down[1] - location[1];
      if (x < 0 || y < 0 || x > messageView.getWidth() || y > messageView.getHeight()) return false;
      org.thunderdog.challegram.Tgx101Diag.mark("select: long press on the message under the menu");
      dismiss(host);
      c.tgx101OpenSelectText(message, (org.thunderdog.challegram.component.chat.MessageView) messageView, x, y);
      return true;
    });

    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setGravity(leftHand ? Gravity.LEFT : Gravity.RIGHT);
    int navigationInset = Settings.instance().useEdgeToEdge() ? c.context().getRootView().getSystemInsetsWithoutIme().bottom : 0;
    int keyboardHeight = 0;
    if (Settings.instance().useEdgeToEdge()) {
      android.graphics.Rect all = c.context().getRootView().getSystemInsets();
      android.graphics.Rect withoutIme = c.context().getRootView().getSystemInsetsWithoutIme();
      if (all != null && withoutIme != null) {
        keyboardHeight = Math.max(0, all.bottom - withoutIme.bottom);
      }
    }
    FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
      Gravity.BOTTOM | (leftHand ? Gravity.LEFT : Gravity.RIGHT));
    columnParams.setMargins(Screen.dp(12f), Screen.dp(12f), Screen.dp(12f), (keyboardHeight > 0 ? Screen.dp(12f) + keyboardHeight : Screen.dp(68f) + navigationInset)); // above the message input or the keyboard
    column.setLayoutParams(columnParams);
    if (Settings.instance().tgx101MenuAtFinger() && lastTouchRawY >= 0 && android.os.SystemClock.uptimeMillis() - lastTouchAt < 3000) {
      // variant 1: the menu opens at the finger — its top a little above the touch, kept inside the screen with
      // margins from the top and the bottom edges
      final float touchY = lastTouchRawY;
      final int bottomLimit = columnParams.bottomMargin;
      column.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange (View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
          v.removeOnLayoutChangeListener(this);
          int[] loc = new int[2];
          root.getLocationOnScreen(loc);
          int screenH = root.getHeight();
          int h = v.getHeight();
          int minTop = Screen.getStatusBarHeight() + Screen.dp(24f);
          int maxTop = screenH - bottomLimit - h;
          int wantTop = (int) (touchY - loc[1]) - Screen.dp(64f);
          int top = Math.max(minTop, Math.min(maxTop, wantTop));
          v.setTranslationY(top - v.getTop());
        }
      });
    }
    column.setOnClickListener(v -> dismiss(host)); // the free strip beside the card closes the menu; the pill and the card consume their own taps
    root.addView(column);
    host.content = column;

    // Reactions pill
    if (message.canBeReacted() && message.getMessageAvailableReactions() != null && message.getMessageAvailableReactions().length > 0) {
      LinearLayout pill = new LinearLayout(context);
      pill.setOrientation(LinearLayout.HORIZONTAL);
      pill.setGravity(Gravity.CENTER_VERTICAL);
      pill.setBackground(rounded(Theme.getColor(ColorId.filling), Screen.dp(26f)));
      elevate(pill, Screen.dp(26f));
      MessageOptionsPagerController.State state = new MessageOptionsPagerController.State(message, options, (v, reaction, isLongClick) -> onReaction(c, host, message, v, reaction, isLongClick));
      ReactionsSelectorRecyclerView reactions = new ReactionsSelectorRecyclerView(context, state);
      host.reactions = reactions;
      reactions.setNeedDrawBorderGradient(false);
      boolean canExpand = state.needShowReactionsPopupPicker && onExpandReactions != null;
      // Whole reactions only: the one that would be cut under the ⌄ button is left out (still reachable by scrolling or ⌄)
      int reactionsWidth = Screen.dp(18f) + (reactionCount + (canExpand ? 0 : 1)) * Screen.dp(REACTION_ITEM_WIDTH);
      pill.setClickable(true);
      pill.addView(reactions, new LinearLayout.LayoutParams(reactionsWidth, Screen.dp(52f)));
      if (canExpand) {
        ImageView expand = new ImageView(context);
        Drawable arrow = Drawables.get(context.getResources(), R.drawable.baseline_keyboard_arrow_down_24);
        if (arrow != null) {
          arrow = arrow.mutate();
          arrow.setColorFilter(Paints.getColorFilter(Theme.getColor(ColorId.icon)));
          expand.setImageDrawable(arrow);
        }
        expand.setScaleType(ImageView.ScaleType.CENTER);
        expand.setBackground(rounded(Theme.getColor(ColorId.background), Screen.dp(16f)));
        host.expandView = expand;
        expand.setOnClickListener(v -> toggleReactionsGrid(context, host, state, onExpandReactions));
        LinearLayout.LayoutParams expandParams = new LinearLayout.LayoutParams(Screen.dp(32f), Screen.dp(32f));
        expandParams.rightMargin = Screen.dp(10f);
        expandParams.leftMargin = Screen.dp(2f);
        pill.addView(expand, expandParams);
      }
      LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(pillWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
      pill.setGravity(Gravity.CENTER);
      pillParams.bottomMargin = Screen.dp(8f);
      if (leftHand) {
        pillParams.leftMargin = cardEdgeGap - overhang;
      } else {
        pillParams.rightMargin = cardEdgeGap - overhang;
      }
      column.addView(pill, pillParams);
    }

    // Card
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(rounded(Theme.getColor(ColorId.filling), Screen.dp(16f)));
    elevate(card, Screen.dp(16f));
    card.setClickable(true);
    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
    if (leftHand) {
      cardParams.leftMargin = cardEdgeGap;
    } else {
      cardParams.rightMargin = cardEdgeGap;
    }
    column.addView(card, cardParams);
    host.card = card;
    host.column = column;

    // Header: read time (filled in later if it arrives after the menu is shown), or the message info
    TextView header = new TextView(context);
    header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    header.setTypeface(Fonts.getRobotoRegular());
    header.setTextColor(Theme.getColor(ColorId.textLight));
    header.setMaxLines(3);
    header.setEllipsize(TextUtils.TruncateAt.END);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setCompoundDrawablePadding(Screen.dp(12f));
    header.setPadding(Screen.dp(16f), Screen.dp(10f), Screen.dp(16f), Screen.dp(10f));
    header.setMinHeight(Screen.dp(40f));
    card.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    View headerDivider = divider(context);
    card.addView(headerDivider);
    host.readDateView = header;
    host.readDateDivider = headerDivider;
    if (options.subtitle != null) {
      setHeader(host, options.subtitle);
    } else if (readDatePending) {
      setHeader(host, new ViewController.OptionItem(0, "…", ViewController.OptionColor.NORMAL, R.drawable.deproko_baseline_check_double_24));
    } else if (!TextUtils.isEmpty(options.info)) {
      header.setText(options.info);
    } else {
      header.setVisibility(View.GONE);
      headerDivider.setVisibility(View.GONE);
    }

    // Actions in the user's order; "Delete" goes to the bottom
    List<ViewController.OptionItem> items = new ArrayList<>();
    List<ViewController.OptionItem> moreItems = new ArrayList<>();
    ViewController.OptionItem deleteItem = null, moreItem = null;
    if (options.items != null) {
      for (ViewController.OptionItem item : options.items) {
        if (item == null || item.id == 0) continue;
        if (item.id == R.id.btn_messageDelete && isHidden(item.id)) {
          // TGx101: «Delete» removed from the menu by the user (still in «More…» of the select mode)
        } else if (item.id == R.id.btn_messageMore) {
          moreItem = item;
        } else if (isHidden(item.id)) {
          // TGx101: actions the user hid in «Message menu»
        } else if (isInMore(item.id)) {
          moreItems.add(item); // TGx101: moved under «More…»
        } else {
          items.add(item);
        }
      }
    }
    final int[] order = getOrder();
    ArrayList<ViewController.OptionItem> sorted = new ArrayList<>(items);
    java.util.Collections.sort(sorted, (a, b) -> Integer.compare(rank(order, a.id), rank(order, b.id))); // stable: unknown items keep their order
    java.util.Collections.sort(moreItems, (a, b) -> Integer.compare(rank(order, a.id), rank(order, b.id)));
    pendingMore = moreItems;
    if (!moreItems.isEmpty() && moreItem == null) {
      moreItem = new ViewController.OptionItem(R.id.btn_messageMore, Lang.getString(R.string.MoreMessageOptions), ViewController.OptionColor.NORMAL, R.drawable.baseline_more_horiz_24);
    }
    if (moreItem != null) {
      sorted.add(moreItem);
    }
    // «More…» in its place in the user's order (by default above «Delete»)
    java.util.Collections.sort(sorted, (a, b) -> Integer.compare(rank(order, a.id), rank(order, b.id)));
    // user 2026-10-05 22:42: «Select» always at the very bottom, under a line
    final boolean addSelect = message.canBeSelected() && !c.inSelectMode();
    shownIds.clear();
    for (ViewController.OptionItem item : sorted) shownIds.add(item.id);

    LinearLayout list = new LinearLayout(context);
    list.setOrientation(LinearLayout.VERTICAL);
    host.list = list;
    for (ViewController.OptionItem item : sorted) {
      View row = row(context, host, item, delegate, item.id == R.id.btn_messageDelete);
      host.mainRows.add(row);
      list.addView(row);
    }
    if (addSelect) {
      list.addView(divider(context));
      View row = row(context, host, new ViewController.OptionItem(R.id.btn_messageSelect, Lang.getString(R.string.Select), ViewController.OptionColor.NORMAL, R.drawable.baseline_playlist_add_check_24), delegate, false);
      host.mainRows.add(row);
      list.addView(row);
    }
    ScrollView scroll = new ScrollView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
          super.onMeasure(widthMeasureSpec, heightMeasureSpec); // shortened to fit beside the message
          return;
        }
        int maxHeight = (int) (Screen.currentHeight() * 0.55f);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
        // user 2026-10-06: a light menu — at most MAX_VISIBLE_ROWS rows at once, the rest scroll
        int rows = 0, limit = 0;
        for (int i = 0; i < list.getChildCount() && rows < MAX_VISIBLE_ROWS; i++) {
          View child = list.getChildAt(i);
          limit += child.getMeasuredHeight();
          if (host.mainRows.contains(child)) rows++;
        }
        if (rows == MAX_VISIBLE_ROWS && limit < getMeasuredHeight()) {
          setMeasuredDimension(getMeasuredWidth(), limit + Screen.dp(18f)); // a peek of the next row says «scroll»
        }
      }
    };
    host.scroll = scroll;
    scroll.setVerticalScrollBarEnabled(false);
    scroll.addView(list);
    card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    // A short scale-in from the corner
    column.setPivotX(leftHand ? 0 : pillWidth + cardEdgeGap - overhang);
    column.setAlpha(0f);
    column.setScaleX(.92f);
    column.setScaleY(.92f);
    column.post(() -> {
      column.setPivotY(column.getHeight());
      column.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    });

    popup.setDismissListener(new PopupLayout.DismissListener() {
      @Override
      public void onPopupDismiss (PopupLayout p) {
        if (onDismiss != null) onDismiss.run();
      }

      @Override
      public void onPopupDismissPrepare (PopupLayout p) {
        if (onDismissPrepare != null) onDismissPrepare.run();
      }
    });
    popup.setTag(host);
    popup.showNonAnimatedView(root);
    return popup;
  }

  /** Popups without their own hide animation are closed right away, after a short fade */
  private static void dismiss (Host host) {
    if (host.dismissing) {
      return;
    }
    host.dismissing = true;
    final Drawable background = host.background;
    if (background != null) {
      // the blur melts away together with the menu instead of vanishing in one frame
      android.animation.ValueAnimator fadeOut = android.animation.ValueAnimator.ofInt(255, 0);
      fadeOut.setDuration(180);
      fadeOut.setInterpolator(new android.view.animation.AccelerateInterpolator());
      fadeOut.addUpdateListener(a -> background.setAlpha((int) a.getAnimatedValue()));
      fadeOut.addListener(new android.animation.AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (android.animation.Animator animation) {
          host.popup.hideWindow(false);
        }
      });
      if (host.content != null) {
        host.content.animate().cancel();
        host.content.animate().alpha(0f).scaleX(.96f).scaleY(.96f).setDuration(150).start();
      }
      fadeOut.start();
    } else if (host.content != null) {
      host.content.animate().cancel();
      host.content.animate().alpha(0f).setDuration(90).withEndAction(() -> host.popup.hideWindow(false)).start();
    } else {
      host.popup.hideWindow(false);
    }
  }

  /** The read time arrived after the menu was shown */
  public static void setReadDate (@Nullable PopupLayout popup, ViewController.OptionItem item) {
    if (popup != null && !popup.isDestroyed() && popup.getTag() instanceof Host) {
      setHeader((Host) popup.getTag(), item);
    }
  }

  /** The read time can't be shown: replace the "…" placeholder with a short reason, keeping the height */
  public static void setReadDateUnavailable (@Nullable PopupLayout popup, TdApi.MessageReadDate readDate) {
    if (popup == null || popup.isDestroyed() || !(popup.getTag() instanceof Host)) {
      return;
    }
    int text;
    int icon = R.drawable.deproko_baseline_check_double_24;
    switch (readDate.getConstructor()) {
      case TdApi.MessageReadDateUnread.CONSTRUCTOR:
        text = R.string.Tgx101ReadDateUnread;
        icon = R.drawable.deproko_baseline_check_single_24;
        break;
      case TdApi.MessageReadDateTooOld.CONSTRUCTOR:
        text = R.string.Tgx101ReadDateRead;
        break;
      default:
        text = R.string.Tgx101ReadDateHidden;
        break;
    }
    setHeader((Host) popup.getTag(), new ViewController.OptionItem(0, Lang.getString(text), ViewController.OptionColor.NORMAL, icon));
  }

  /**
   * iOS-like frosted background at no running cost: the screen is drawn once into a tiny bitmap,
   * which is then stretched with filtering and dimmed a little.
   */
  /** One horizontal and one vertical box-blur pass over a small bitmap (a couple of ms, once) */
  private static void boxBlur (android.graphics.Bitmap bitmap, int radius) {
    int w = bitmap.getWidth(), h = bitmap.getHeight();
    int[] src = new int[w * h];
    int[] dst = new int[w * h];
    bitmap.getPixels(src, 0, w, 0, 0, w, h);
    for (int pass = 0; pass < 2; pass++) {
      boolean horizontal = pass == 0;
      int lines = horizontal ? h : w, length = horizontal ? w : h;
      for (int line = 0; line < lines; line++) {
        int r = 0, g = 0, b = 0, count = 0;
        for (int i = -radius; i <= radius; i++) {
          int k = Math.max(0, Math.min(length - 1, i));
          int p = src[horizontal ? line * w + k : k * w + line];
          r += (p >> 16) & 0xff; g += (p >> 8) & 0xff; b += p & 0xff; count++;
        }
        for (int i = 0; i < length; i++) {
          dst[horizontal ? line * w + i : i * w + line] = 0xff000000 | ((r / count) << 16) | ((g / count) << 8) | (b / count);
          int outK = Math.max(0, Math.min(length - 1, i - radius));
          int inK = Math.max(0, Math.min(length - 1, i + radius + 1));
          int po = src[horizontal ? line * w + outK : outK * w + line];
          int pi = src[horizontal ? line * w + inK : inK * w + line];
          r += ((pi >> 16) & 0xff) - ((po >> 16) & 0xff);
          g += ((pi >> 8) & 0xff) - ((po >> 8) & 0xff);
          b += (pi & 0xff) - (po & 0xff);
        }
      }
      int[] t = src; src = dst; dst = t;
    }
    bitmap.setPixels(src, 0, w, 0, 0, w, h);
  }

  /** TGx101 (user's videos 11:36 / 11:40: «плавнее, бесшовно, без подпрыгиваний»): while the finger slides over the
   *  reactions they grow smoothly with the distance to the finger, like a dock — no per-item jumps */
  private static void magnifyReactions (@Nullable ViewGroup group, float rawX, float rawY, boolean row, int action) {
    if (group == null || !group.isShown()) return;
    int[] g = new int[2];
    group.getLocationOnScreen(g);
    boolean over = action != android.view.MotionEvent.ACTION_UP && action != android.view.MotionEvent.ACTION_CANCEL
      && rawY >= g[1] - Screen.dp(24f) && rawY <= g[1] + group.getHeight() + Screen.dp(24f)
      && rawX >= g[0] - Screen.dp(12f) && rawX <= g[0] + group.getWidth() + Screen.dp(12f);
    for (int i = 0; i < group.getChildCount(); i++) {
      View child = group.getChildAt(i);
      float scale = 1f;
      if (over) {
        int[] c = new int[2];
        child.getLocationOnScreen(c);
        float cx = c[0] + child.getWidth() / 2f, cy = c[1] + child.getHeight() / 2f;
        float d = row ? Math.abs(rawX - cx) / child.getWidth() : (float) Math.hypot(rawX - cx, rawY - cy) / child.getWidth();
        float k = Math.max(0f, 1f - d / 1.5f); // neighbours grow a little too
        scale = 1f + .28f * k * k * (3f - 2f * k); // smoothstep: no kink, no jump
      }
      child.setPivotX(child.getWidth() / 2f);
      child.setPivotY(child.getHeight() / 2f);
      if (over) {
        child.animate().cancel();
        child.setScaleX(scale);
        child.setScaleY(scale);
      } else if (child.getScaleX() != 1f) {
        child.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
      }
    }
  }

  /** ⌄ / ⌃: the grid of all reactions in place of the actions (no new window, still pictures, a short height change) */
  private static void toggleReactionsGrid (Context context, Host host, MessageOptionsPagerController.State state, Runnable onExpandReactions) {
    if (host.column == null || host.card == null) return;
    boolean show = host.grid == null || host.grid.getVisibility() != View.VISIBLE;
    if (show && host.grid == null) {
      LinearLayout grid = new LinearLayout(context);
      grid.setOrientation(LinearLayout.VERTICAL);
      grid.setBackground(rounded(Theme.getColor(ColorId.filling), Screen.dp(16f)));
      elevate(grid, Screen.dp(16f));
      grid.setClickable(true);
      ReactionsSelectorRecyclerView all = new ReactionsSelectorRecyclerView(context, state, 6);
      all.setNeedDrawBorderGradient(false);
      all.setVerticalScrollBarEnabled(false);
      host.gridReactions = all;
      grid.addView(all, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.min(Screen.dp(40f) * 5 + Screen.dp(14f), Math.round(Screen.currentHeight() * .4f))));
      // the full picker (custom emoji packs) stays one tap away
      TextView more = new TextView(context);
      more.setText(Lang.getString(R.string.Tgx101AllEmoji));
      more.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
      more.setTextColor(Theme.getColor(ColorId.textLink));
      more.setGravity(Gravity.CENTER);
      more.setPadding(0, Screen.dp(10f), 0, Screen.dp(12f));
      Views.setClickable(more);
      RippleSupport.setTransparentSelector(more);
      more.setOnClickListener(v -> {
        dismiss(host);
        if (onExpandReactions != null) onExpandReactions.run();
      });
      grid.addView(more, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      // the same width and margins as the actions card — the grid takes exactly its place (user's video 11:17)
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) host.card.getLayoutParams());
      host.column.addView(grid, host.column.indexOfChild(host.card), params);
      host.grid = grid;
    }
    // the reactions row stays where it is: the column is pinned to the bottom, so a taller / shorter block under the
    // row moved the whole menu — keep the column's top in place instead
    final View column = host.column;
    final int topBefore = column.getTop() + Math.round(column.getTranslationY());
    host.grid.setVisibility(show ? View.VISIBLE : View.GONE);
    host.card.setVisibility(show ? View.GONE : View.VISIBLE);
    column.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
      @Override
      public void onLayoutChange (View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
        v.removeOnLayoutChangeListener(this);
        int translation = topBefore - v.getTop();
        int overflow = v.getBottom() + translation - (((View) v.getParent()).getHeight() - Screen.dp(12f));
        if (overflow > 0) translation -= overflow; // only when the grid would go off the screen does the row move up
        v.setTranslationY(translation);
      }
    });
    View block = show ? host.grid : host.card;
    block.setAlpha(0f);
    block.animate().alpha(1f).setDuration(120).start();
    if (host.expandView != null) host.expandView.animate().rotation(show ? 180f : 0f).setDuration(150).start();
    org.thunderdog.challegram.Tgx101Diag.mark("menu: all reactions " + (show ? "unfolded" : "folded"));
  }

  private static Drawable blurredBackground (MessagesController c) {
    try {
      View source = c.context().getWindow().getDecorView();
      int width = source.getWidth(), height = source.getHeight();
      if (width > 0 && height > 0) {
        final float scale = 1f / 8f;
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        canvas.scale(scale, scale);
        source.draw(canvas);
        boxBlur(bitmap, 3);
        boxBlur(bitmap, 3);
        new android.graphics.Canvas(bitmap).drawColor(0x40000000);
        android.graphics.drawable.BitmapDrawable drawable = new android.graphics.drawable.BitmapDrawable(c.context().getResources(), bitmap);
        drawable.setFilterBitmap(true);
        return drawable;
      }
    } catch (Throwable ignored) { }
    return new android.graphics.drawable.ColorDrawable(0x59000000);
  }

  private static void setHeader (Host host, ViewController.OptionItem item) {
    TextView header = host.readDateView;
    header.setText(item.name);
    Drawable icon = item.icon != 0 ? Drawables.get(header.getResources(), item.icon) : null;
    if (icon != null) {
      icon = icon.mutate();
      icon.setColorFilter(Paints.getColorFilter(Theme.getColor(ColorId.textLight)));
    }
    if (Lang.rtl()) {
      header.setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
    } else {
      header.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
    }
    header.setVisibility(View.VISIBLE);
    host.readDateDivider.setVisibility(View.VISIBLE);
  }

  private static TextView row (Context context, Host host, ViewController.OptionItem item, OptionDelegate delegate, boolean isDelete) {
    TextView row = new TextView(context);
    row.setId(item.id);
    row.setTypeface(Fonts.getRobotoRegular());
    row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    row.setSingleLine(true);
    row.setEllipsize(TextUtils.TruncateAt.END);
    row.setGravity(Gravity.CENTER_VERTICAL | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    row.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
    row.setCompoundDrawablePadding(Screen.dp(14f));
    // TGx101 (user 2026-10-05): «Delete» — red icon, the word in the normal text colour; «Select» — icon in the brand blue
    int textColorId = isDelete ? ColorId.text : OptionsLayoutColor.text(item);
    row.setTextColor(Theme.getColor(textColorId));
    row.setText(shortName(item));
    Settings.tgx101StyleRow(row, 15f);
    if (item.icon != 0) {
      Drawable icon = Drawables.get(context.getResources(), item.icon);
      if (icon != null) {
        icon = icon.mutate();
        icon.setColorFilter(Paints.getColorFilter(isDelete ? Theme.getColor(ColorId.iconNegative) : item.id == R.id.btn_messageSelect ? Theme.chatSendButtonColor() : Theme.getColor(ColorId.icon)));
        int size = Screen.dp(21f);
        icon.setBounds(0, 0, size, size);
        if (Lang.rtl()) {
          row.setCompoundDrawables(null, null, icon, null);
        } else {
          row.setCompoundDrawables(icon, null, null, null);
        }
      }
    }
    row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(44f)));
    Views.setClickable(row);
    RippleSupport.setTransparentSelector(row);
    row.setOnClickListener(v -> {
      if (item.id == R.id.btn_tgx101SelectInPlace && host.selectInPlace != null) {
        // close the menu at once, then select: the menu closing later took the selection down with it
        if (host.dismissing) return;
        host.dismissing = true;
        host.popup.hideWindow(false);
        UI.post(host.selectInPlace, 120);
        return;
      }
      if (item.id == R.id.btn_messageMore && host.moreLoader != null) {
        showMore(context, host); // inside this card: the dimmed screen behind stays as it is
        return;
      }
      // Close right away, then act: "Share" and "Pin" open their own windows, which the closing
      // menu used to take down with it
      if (host.dismissing) return;
      host.dismissing = true;
      host.popup.hideWindow(false);
      UI.post(() -> delegate.onOptionItemPressed(v, v.getId()));
    });
    return row;
  }

  /** «More…»: the card's actions are swapped for the extra ones (with «Back»), in the same window */
  private static void showMore (Context context, Host host) {
    if (host.moreShown || host.moreLoading || host.dismissing) return;
    host.moreLoading = true;
    host.moreLoader.load((items, moreDelegate) -> {
      host.moreLoading = false;
      if (host.dismissing || host.popup.isDestroyed() || items.isEmpty()) return;
      List<View> moreRows = new ArrayList<>();
      ViewController.OptionItem back = new ViewController.OptionItem(R.id.btn_back, Lang.getString(R.string.Tgx101MenuBack), ViewController.OptionColor.NORMAL, R.drawable.baseline_arrow_back_24);
      TextView backRow = row(context, host, back, null, false);
      backRow.setOnClickListener(v -> swapRows(host, host.mainRows, true, false));
      moreRows.add(backRow);
      moreRows.add(divider(context));
      for (ViewController.OptionItem item : items) {
        if (item.id == R.id.btn_messageMore) continue;
        moreRows.add(row(context, host, item, moreDelegate, item.id == R.id.btn_messageDelete));
      }
      swapRows(host, moreRows, false, true);
    });
  }

  private static void swapRows (Host host, List<View> rows, boolean showBottom, boolean moreShown) {
    host.moreShown = moreShown;
    LinearLayout list = host.list;
    list.animate().cancel();
    list.animate().alpha(0f).setDuration(70).setStartDelay(0).withEndAction(() -> {
      // the card's height changes smoothly (the reactions above it used to jump)
      View card = (View) list.getParent().getParent();
      int fromHeight = card.getHeight();
      list.removeAllViews();
      for (View row : rows) {
        if (row.getParent() instanceof ViewGroup) ((ViewGroup) row.getParent()).removeView(row);
        list.addView(row);
      }
      for (View view : host.bottomViews) view.setVisibility(showBottom ? View.VISIBLE : View.GONE);
      card.measure(View.MeasureSpec.makeMeasureSpec(card.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      int toHeight = Math.min(card.getMeasuredHeight(), (int) (Screen.currentHeight() * 0.62f));
      ViewGroup.LayoutParams params = card.getLayoutParams();
      android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(fromHeight, toHeight);
      animator.setDuration(170);
      animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
      animator.addUpdateListener(a -> {
        params.height = (int) a.getAnimatedValue();
        card.setLayoutParams(params);
      });
      animator.addListener(new android.animation.AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (android.animation.Animator animation) {
          params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
          card.setLayoutParams(params);
        }
      });
      animator.start();
      list.animate().alpha(1f).setDuration(130).setStartDelay(40).start();
    }).start();
  }

  private static final class OptionsLayoutColor {
    static int text (ViewController.OptionItem item) {
      return org.thunderdog.challegram.navigation.OptionsLayout.getOptionColorId(item.textColor);
    }
  }

  private static View divider (Context context) {
    View divider = new View(context);
    divider.setBackgroundColor(Theme.getColor(ColorId.separator));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Screen.dp(.5f)));
    params.leftMargin = Screen.dp(12f);
    params.rightMargin = Screen.dp(12f);
    divider.setLayoutParams(params);
    return divider;
  }

  /** TGx101: shorter names for the narrow card; the full names stay everywhere else */
  private static CharSequence shortName (ViewController.OptionItem item) {
    if (item.id == R.id.btn_saveFile && item.name != null && item.name.toString().equals(org.thunderdog.challegram.core.Lang.getString(R.string.SaveToGallery))) {
      return org.thunderdog.challegram.core.Lang.getString(R.string.Tgx101MenuToGallery);
    }
    if (item.id == R.id.btn_messageShowSource) {
      return org.thunderdog.challegram.core.Lang.getString(R.string.Tgx101MenuToOriginal);
    }
    return item.name;
  }

  private static final float REACTIONS_OVERHANG = 16f, REACTION_ITEM_WIDTH = 38f; // item: 40dp view with -1dp decoration on each side

  private static GradientDrawable rounded (int color, float radius) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(radius);
    // A thin edge instead of an elevation shadow: some firmwares (Vivo) draw that shadow with square corners
    drawable.setStroke(Math.max(1, Screen.dp(.5f)), Theme.getColor(ColorId.separator));
    return drawable;
  }

  private static void elevate (View view, float radius) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      view.setOutlineProvider(new ViewOutlineProvider() {
        @Override
        public void getOutline (View v, Outline outline) {
          outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
        }
      });
      view.setClipToOutline(true);
    }
  }

  private static void onReaction (MessagesController c, Host host, TGMessage message, View v, TGReaction reaction, boolean isLongClick) {
    int[] position = new int[2];
    v.getLocationOnScreen(position);
    Point start = new Point(position[0] + v.getMeasuredWidth() / 2, position[1] + v.getMeasuredHeight() / 2);
    boolean hasReaction = message.getMessageReactions().hasReaction(reaction.type);
    if (message.getMessageReactions().toggleReaction(reaction.type, isLongClick, true, result -> {
      if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        UI.showError(result);
      }
    })) {
      if (isLongClick) {
        message.scheduleSetReactionAnimationFullscreenFromBottomSheet(reaction, start);
      } else if (!hasReaction) {
        message.scheduleSetReactionAnimationFromBottomSheet(reaction, start);
      }
    }
    dismiss(host);
  }
}
