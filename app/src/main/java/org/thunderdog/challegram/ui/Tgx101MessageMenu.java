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
    R.id.btn_messageEdit,
    R.id.btn_messageShare,
    R.id.btn_messagePin,
    R.id.btn_messageSelectText,
    R.id.btn_chatTranslate,
    R.id.btn_messageCopyLink,
    R.id.btn_saveFile,
    R.id.btn_messageReport,
    R.id.btn_tgx101FilterSimilar,
    R.id.btn_tgx101EditorWindow,
    R.id.btn_messageViewList
  };
  public static final int[] ORDERABLE_NAMES = {
    R.string.Reply, R.string.Copy, R.string.edit, R.string.Share, R.string.MessagePin,
    R.string.Tgx101MenuSelectText, R.string.Translate, R.string.CopyLink, R.string.Save, R.string.MessageReport,
    R.string.Tgx101FilterSimilar, R.string.Tgx101MenuEditorOwn, R.string.Tgx101MenuMessagesFrom
  };
  public static final int[] ORDERABLE_ICONS = {
    R.drawable.baseline_reply_24, R.drawable.baseline_content_copy_24, R.drawable.baseline_edit_24,
    R.drawable.baseline_forward_24, R.drawable.deproko_baseline_pin_24, R.drawable.baseline_format_quote_close_24,
    R.drawable.baseline_translate_24, R.drawable.baseline_link_24, R.drawable.baseline_file_download_24,
    R.drawable.baseline_report_24, R.drawable.baseline_filter_variant_remove_24, R.drawable.baseline_format_text_24,
    R.drawable.baseline_person_24
  };

  private static int orderKey (int id) {
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
    for (int known : ORDERABLE_IDS) {
      if (!result.contains(known)) {
        result.add(known);
      }
    }
    int[] out = new int[result.size()];
    for (int i = 0; i < out.length; i++) out[i] = result.get(i);
    return out;
  }

  /** Hidden in Settings → MagiX → message menu (Pin hides Unpin too) */
  public static boolean isHidden (int id) {
    int key = orderKey(id);
    for (int hidden : Settings.instance().getTgx101MessageMenuHidden()) {
      if (hidden == key) return true;
    }
    return false;
  }

  /** Moved under «More…» in Settings → MagiX → message menu */
  public static boolean isInMore (int id) {
    int key = orderKey(id);
    for (int more : Settings.instance().getTgx101MessageMenuMore()) {
      if (more == key) return true;
    }
    return false;
  }

  // The actions moved under «More…» for the menu being shown, and what the menu itself shows
  private static List<ViewController.OptionItem> pendingMore = new ArrayList<>();
  private static final java.util.HashSet<Integer> shownIds = new java.util.HashSet<>();

  /** For the «More…» list: the user's moved actions (taken once) */
  public static List<ViewController.OptionItem> takePendingMore () {
    List<ViewController.OptionItem> result = pendingMore;
    pendingMore = new ArrayList<>();
    return result;
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

  private static class Host {
    PopupLayout popup;
    View content;
    boolean dismissing;
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

  public static PopupLayout show (MessagesController c, TGMessage message, ViewController.Options options, OptionDelegate delegate,
                                  boolean readDatePending, Runnable onExpandReactions, Runnable onDismissPrepare, Runnable onDismiss) {
    Context context = c.context();
    tgx101LogTextShape(message);
    Host host = new Host();
    PopupLayout popup = new PopupLayout(context);
    host.popup = popup;
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
    root.setBackground(blurredBackground(c));
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
        expand.setOnClickListener(v -> {
          dismiss(host);
          onExpandReactions.run();
        });
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
        if (item.id == R.id.btn_messageDelete) {
          deleteItem = item;
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
      sorted.add(moreItem); // «More…» stays last, above «Delete»
    }
    shownIds.clear();
    for (ViewController.OptionItem item : sorted) shownIds.add(item.id);

    LinearLayout list = new LinearLayout(context);
    list.setOrientation(LinearLayout.VERTICAL);
    for (ViewController.OptionItem item : sorted) {
      list.addView(row(context, host, item, delegate, false));
    }
    ScrollView scroll = new ScrollView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int maxHeight = (int) (Screen.currentHeight() * 0.55f);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
      }
    };
    scroll.setVerticalScrollBarEnabled(false);
    scroll.addView(list);
    card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    if (deleteItem != null) {
      if (!sorted.isEmpty()) {
        card.addView(divider(context));
      }
      card.addView(row(context, host, deleteItem, delegate, true));
    }

    // A short scale-in from the corner
    column.setPivotX(leftHand ? 0 : pillWidth + cardEdgeGap - overhang);
    column.setAlpha(0f);
    column.setScaleX(.92f);
    column.setScaleY(.92f);
    column.post(() -> {
      column.setPivotY(column.getHeight());
      column.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
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
    if (host.content != null) {
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
    int textColorId = isDelete ? ColorId.textNegative : OptionsLayoutColor.text(item);
    row.setTextColor(Theme.getColor(textColorId));
    row.setText(shortName(item));
    if (item.icon != 0) {
      Drawable icon = Drawables.get(context.getResources(), item.icon);
      if (icon != null) {
        icon = icon.mutate();
        icon.setColorFilter(Paints.getColorFilter(Theme.getColor(isDelete ? ColorId.iconNegative : ColorId.icon)));
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
      // Close right away, then act: "Share" and "Pin" open their own windows, which the closing
      // menu used to take down with it
      if (host.dismissing) return;
      host.dismissing = true;
      host.popup.hideWindow(false);
      UI.post(() -> delegate.onOptionItemPressed(v, v.getId()));
    });
    return row;
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
