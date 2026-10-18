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
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import java.util.List;

/**
 * TGx101 (user 2026-10-15, approved mockups 1 · «Главный экран»): Saved Messages as a storage — search, the kinds
 * (photos, files, links, voice) counted by Telegram, folders and tags as tiles, pinned, recent, and a field that
 * saves a note. «Feed» opens the usual chat.
 */
public class Tgx101SavedHomeController extends ViewController<Void> implements Menu, View.OnClickListener,
  org.thunderdog.challegram.widget.EmojiLayout.Listener, org.thunderdog.challegram.component.chat.EmojiToneHelper.Delegate {

  public static void open (BaseActivity context, Tdlib tdlib) {
    ViewController<?> top = context.navigation().getCurrentStackItem();
    if (top instanceof Tgx101SavedHomeController && top.tdlib() == tdlib) return;
    context.navigation().navigateTo(new Tgx101SavedHomeController(context, tdlib));
  }

  public Tgx101SavedHomeController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101SavedHome;
  }

  @Override
  protected int getBackButton () {
    return org.thunderdog.challegram.navigation.BackHeaderButton.TYPE_BACK;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101SavedOpenHome); // «Storage» (user 2026-10-16 «переименуй его в Хранилище»)
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_tgx101SavedHome;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_tgx101SavedHome) {
      header.addButton(menu, R.id.menu_btn_tgx101SavedSearch, R.drawable.baseline_search_24, getHeaderIconColorId(), this, Screen.dp(49f));
      header.addButton(menu, R.id.menu_btn_tgx101SavedFeed, R.drawable.baseline_forum_24, getHeaderIconColorId(), this, Screen.dp(49f));
      header.addButton(menu, R.id.menu_btn_tgx101SavedSettings, R.drawable.baseline_settings_24, getHeaderIconColorId(), this, Screen.dp(49f));
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_tgx101SavedSearch) {
      Tgx101SavedListController.openSearch(this);
    } else if (id == R.id.menu_btn_tgx101SavedFeed) {
      Tgx101SavedKit.openInFeed(this, null);
    } else if (id == R.id.menu_btn_tgx101SavedSettings) {
      navigateTo(new Tgx101SavedSettingsController(context, tdlib));
    }
  }

  @Override
  public void onClick (View v) {
    onMenuItemPressed(v.getId(), v);
  }

  private LinearLayout column;
  private LinearLayout chips;
  private LinearLayout tiles;
  private TextView foldersTitle;
  private LinearLayout pinnedRow;
  private View pinnedSection;
  private DragList recentList;
  private ScrollView scroll;
  private HorizontalScrollView chipsScroll, pinnedScroll;
  private ImageView pullIcon;
  private LinearLayout selectionTop;
  private Tgx101SelectionPanel selectionPanel;
  private TextView selectionTitle;
  private final java.util.LinkedHashSet<Long> selected = new java.util.LinkedHashSet<>();
  private final java.util.List<RecentRow> recentRows = new java.util.ArrayList<>();

  /** one «Recent» row: the message, its view and the sliding frame around it */
  private static final class RecentRow {
    final TdApi.Message message;
    final Tgx101SavedListController.RowHolder holder;
    final Tgx101SwipeRow swipe;

    RecentRow (TdApi.Message message, Tgx101SavedListController.RowHolder holder, Tgx101SwipeRow swipe) {
      this.message = message;
      this.holder = holder;
      this.swipe = swipe;
    }
  }
  private org.thunderdog.challegram.component.chat.InputView noteField;
  private ImageView emojiButton;
  private FrameLayout rootView;
  private LinearLayout outerView;
  private org.thunderdog.challegram.widget.KeyboardFrameLayout keyboardFrame;
  private boolean emojiShown, emojiWasKeyboard;
  private View noteBar;
  private Tgx101StorageSwitch storageSwitch;

  // Small builders

  static GradientDrawable card (int color, float radiusDp) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(Screen.dp(radiusDp));
    return d;
  }

  /**
   * TGx101 (user 2026-10-16 «общим стилем везде в хранилище, где нужно вводить текст»): the chats' field — a capsule with
   * 18 dp corners; on a sheet (itself the filling colour) it takes the background colour so it still shows
   */
  static android.graphics.drawable.Drawable bubble (boolean onFilling) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(onFilling ? Theme.backgroundColor() : Theme.fillingColor());
    d.setCornerRadius(Screen.dp(18f));
    return d;
  }

  /** a text field in the storage: the bubble, its padding, and an accent cursor that blinks where you can type */
  static void styleField (EditText e, boolean onFilling) {
    e.setBackground(bubble(onFilling));
    e.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
    e.setTextColor(Theme.textAccentColor());
    e.setHintTextColor(Theme.textDecentColor());
    e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    cursor(e);
  }

  static void cursor (EditText e) {
    if (android.os.Build.VERSION.SDK_INT >= 29) {
      GradientDrawable c = new GradientDrawable();
      c.setColor(accent());
      c.setSize(Screen.dp(2f), Screen.dp(20f));
      e.setTextCursorDrawable(c);
    }
  }

  static TextView text (Context context, float sp, int color, boolean medium) {
    // the app's own emoji (as in the chats), redrawn when their pictures are ready — a plain TextView left them blank
    TextView v = new org.thunderdog.challegram.widget.EmojiTextView(context);
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
    v.setTextColor(color);
    if (medium) v.setTypeface(Fonts.getRobotoMedium());
    return v;
  }

  /**
   * A tile's name broken only between words: when its longest word is wider than the tile, the name gets smaller
   * (down to [minSp]) so the word fits whole — a TextView cut it in the middle («Фотосе / ссия с Симбой»)
   */
  static TextView tileName (Context context, float sp, float minSp, int color) {
    TextView v = new org.thunderdog.challegram.widget.EmojiTextView(context) {
      private final android.text.TextPaint probe = new android.text.TextPaint();

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && width > 0) {
          probe.set(getPaint());
          probe.setTextSize(Screen.dp(sp));
          float widest = 0;
          for (String word : getText().toString().split("\\s+")) {
            widest = Math.max(widest, probe.measureText(word));
          }
          float size = Screen.dp(sp);
          if (widest > width) {
            size = Math.max(Screen.dp(minSp), (float) Math.floor(size * width / widest));
          }
          if (Math.abs(getTextSize() - size) >= .5f) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
          }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      }
    };
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
    v.setTextColor(color);
    v.setTypeface(Fonts.getRobotoMedium());
    return v;
  }

  static TextView sectionTitle (Context context, String title) {
    TextView v = text(context, 15f, Theme.textAccentColor(), true);
    v.setText(title);
    v.setPadding(Screen.dp(16f), Screen.dp(18f), Screen.dp(16f), Screen.dp(8f));
    return v;
  }

  static int accent () {
    return Theme.getColor(ColorId.textLink);
  }

  /** (user 2026-10-17 «„Всё“ — не белым, а в нашем цвете»; «поменяй только цвет, стиль не меняй»): a chosen chip /
   *  our capsule — the same solid chip, in the accent instead of white, its words in {@link #onChosen()} */
  static GradientDrawable chosen (float radiusDp) {
    return card(accent(), radiusDp);
  }

  static int onChosen () {
    return 0xffffffff;
  }

  /** a transparent tint of a colour for icon backgrounds */
  static int tint (int color, float alpha) {
    return (Math.round(alpha * 255) << 24) | (color & 0xffffff);
  }

  @Override
  protected View onCreateView (Context context) {
    madeStart = android.os.SystemClock.uptimeMillis();
    FrameLayout root = new FrameLayout(context);
    root.setBackgroundColor(Theme.backgroundColor());
    // the note field doesn't take the focus when the screen opens (popups then sat above an expected keyboard)
    root.setFocusableInTouchMode(true);
    root.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

    LinearLayout outer = new LinearLayout(context);
    outer.setOrientation(LinearLayout.VERTICAL);
    root.addView(outer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // (user 2026-10-16 «поле ввода без подложки, как в чатах»): the note capsule lies over the list, which scrolls under it
    FrameLayout content = new FrameLayout(context);
    outer.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    scroll = new HomeScroll(context);
    scroll.setFillViewport(true);
    scroll.setOverScrollMode(View.OVER_SCROLL_NEVER); // (user 2026-10-16 «дёргается при прокрутке до конца»): no Android 12 stretch at the ends — the pull-down is its own
    content.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setPadding(0, Screen.dp(12f), 0, Screen.dp(16f));
    scroll.addView(column, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    // search pill
    LinearLayout search = new LinearLayout(context);
    search.setOrientation(LinearLayout.HORIZONTAL);
    search.setGravity(Gravity.CENTER_VERTICAL);
    search.setPadding(Screen.dp(14f), 0, Screen.dp(14f), 0);
    search.setBackground(card(Theme.fillingColor(), 21f));
    Views.setClickable(search);
    search.setOnClickListener(v -> Tgx101SavedListController.openSearch(this));
    ImageView glass = new ImageView(context);
    glass.setImageResource(R.drawable.baseline_search_24);
    glass.setColorFilter(Theme.getColor(ColorId.icon));
    search.addView(glass, new LinearLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f)));
    TextView hint = text(context, 15f, Theme.textDecentColor(), false);
    hint.setText(Lang.getString(R.string.Tgx101SavedSearchHint));
    LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    hintParams.leftMargin = Screen.dp(10f);
    search.addView(hint, hintParams);
    LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(42f));
    searchParams.leftMargin = searchParams.rightMargin = Screen.dp(16f);
    column.addView(search, searchParams);

    // kinds
    chipsScroll = new HorizontalScrollView(context);
    chipsScroll.setHorizontalScrollBarEnabled(false);
    chips = new LinearLayout(context);
    chips.setOrientation(LinearLayout.HORIZONTAL);
    chips.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(8f), 0);
    chipsScroll.addView(chips);
    column.addView(chipsScroll);

    // folders and tags
    foldersTitle = sectionTitle(context, Lang.getString(R.string.Tgx101SavedFoldersAndTags));
    column.addView(foldersTitle);
    tiles = new LinearLayout(context);
    tiles.setOrientation(LinearLayout.VERTICAL);
    tiles.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    column.addView(tiles);

    // pinned
    LinearLayout pinnedBox = new LinearLayout(context);
    pinnedBox.setOrientation(LinearLayout.VERTICAL);
    pinnedBox.addView(sectionTitle(context, Lang.getString(R.string.Tgx101SavedPinned)));
    pinnedScroll = new HorizontalScrollView(context);
    pinnedScroll.setHorizontalScrollBarEnabled(false);
    pinnedRow = new LinearLayout(context);
    pinnedRow.setOrientation(LinearLayout.HORIZONTAL);
    pinnedRow.setPadding(Screen.dp(16f), 0, Screen.dp(8f), 0);
    pinnedScroll.addView(pinnedRow);
    pinnedBox.addView(pinnedScroll);
    pinnedBox.setVisibility(View.GONE);
    pinnedSection = pinnedBox;
    column.addView(pinnedBox);

    // recent
    LinearLayout recentHeader = new LinearLayout(context);
    recentHeader.setOrientation(LinearLayout.HORIZONTAL);
    recentHeader.setGravity(Gravity.BOTTOM);
    recentHeader.addView(sectionTitle(context, Lang.getString(R.string.Tgx101SavedRecent)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    column.addView(recentHeader);
    recentList = new DragList(context);
    recentList.setOrientation(LinearLayout.VERTICAL);
    recentList.setBackground(card(Theme.fillingColor(), 14f));
    if (android.os.Build.VERSION.SDK_INT >= 21) { // the sliding rows keep the card's round corners
      recentList.setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Screen.dp(14f));
        }
      });
      recentList.setClipToOutline(true);
    }
    LinearLayout.LayoutParams recentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    recentParams.leftMargin = recentParams.rightMargin = Screen.dp(16f);
    column.addView(recentList, recentParams);
    // (user 2026-10-17, about the end of «Recent»): «More» at the end of the list — bold three dots in an outlined
    // capsule with the message field's corners, no fill
    ImageView all = new ImageView(context);
    all.setImageResource(R.drawable.tgx101_dots_bold_24);
    all.setColorFilter(accent());
    all.setScaleType(ImageView.ScaleType.CENTER);
    all.setContentDescription(Lang.getString(R.string.Tgx101SavedRecentMore));
    // (user 2026-10-17 «контур не видно — сделай современно»): an outlined button as in Material 3 — a clear line in the
    // accent, a faint tint of it inside, a ripple of it under the finger
    GradientDrawable outline = new GradientDrawable();
    outline.setCornerRadius(Screen.dp(20f)); // the chats' field
    outline.setColor(tint(accent(), .06f));
    outline.setStroke(Screen.dp(1.5f), tint(accent(), .65f));
    if (android.os.Build.VERSION.SDK_INT >= 21) {
      GradientDrawable mask = new GradientDrawable();
      mask.setCornerRadius(Screen.dp(20f));
      mask.setColor(0xffffffff);
      all.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(tint(accent(), .22f)), outline, mask));
    } else {
      all.setBackground(outline);
    }
    all.setClickable(true);
    all.setOnClickListener(v -> Tgx101SavedListController.openKind(this, -1));
    LinearLayout.LayoutParams allParams = new LinearLayout.LayoutParams(Screen.dp(96f), Screen.dp(40f));
    allParams.gravity = Gravity.CENTER_HORIZONTAL;
    allParams.topMargin = Screen.dp(12f);
    column.addView(all, allParams);

    // note field
    LinearLayout bar = new LinearLayout(context);
    noteBar = bar;
    bar.setOrientation(LinearLayout.HORIZONTAL);
    // (user 2026-10-16 «как в чатах»): a floating capsule 8 dp off the edges and the bottom, corners like the chats' field;
    // the buttons stay at the bottom while the text grows
    bar.setGravity(Gravity.BOTTOM);
    // (user 2026-10-16 «поле ввода в хранилище сливается с фоном»): as the chats' field — the filling colour with a
    // hairline round it and a soft shadow, so it stands off the list in a dark theme too
    GradientDrawable barBg = new GradientDrawable();
    barBg.setColor(Theme.fillingColor());
    barBg.setCornerRadius(Screen.dp(18f));
    barBg.setStroke(Math.max(1, Screen.dp(.5f)), Theme.separatorColor());
    bar.setBackground(barBg);
    bar.setPadding(Screen.dp(2f), Screen.dp(2f), Screen.dp(2f), Screen.dp(2f));
    rootView = root;
    outerView = outer;
    // TGx101 (approved mockup Д, user 2026-10-16): the chat's own input — emoji, custom emoji, stickers, GIFs, formatting
    // on a selection, and the attachment menu
    emojiButton = new ImageView(context);
    emojiButton.setImageResource(R.drawable.deproko_baseline_insert_emoticon_26);
    emojiButton.setColorFilter(Theme.iconColor());
    emojiButton.setScaleType(ImageView.ScaleType.CENTER);
    emojiButton.setContentDescription(Lang.getString(R.string.Tgx101SavedEmoji));
    Views.setClickable(emojiButton);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(emojiButton, Screen.dp(22f), this);
    emojiButton.setOnClickListener(v -> {
      if (emojiShown) closeEmoji(true);
      else openEmoji();
    });
    bar.addView(emojiButton, new LinearLayout.LayoutParams(Screen.dp(44f), Screen.dp(44f)));
    noteField = new org.thunderdog.challegram.component.chat.InputView(context, tdlib, this);
    noteField.setInputListener(new org.thunderdog.challegram.component.chat.InputView.InputListener() {
      @Override
      public void onInputChanged (org.thunderdog.challegram.component.chat.InputView v, String input) {
        if (keyboardFrame != null) keyboardFrame.contentView.emojiLayout.onTextChanged(input);
        updateSendIcon();
      }

      @Override
      public boolean canSearchInline (org.thunderdog.challegram.component.chat.InputView v) {
        return false;
      }

      @Override
      public TdApi.Chat provideInlineSearchChat (org.thunderdog.challegram.component.chat.InputView v) {
        return null;
      }

      @Override
      public long provideInlineSearchChatId (org.thunderdog.challegram.component.chat.InputView v) {
        return 0;
      }

      @Override
      public long provideInlineSearchChatUserId (org.thunderdog.challegram.component.chat.InputView v) {
        return 0;
      }

      @Override
      public void showInlineResults (org.thunderdog.challegram.component.chat.InputView v, java.util.ArrayList<org.thunderdog.challegram.data.InlineResult<?>> items, boolean isContent) { }

      @Override
      public void addInlineResults (org.thunderdog.challegram.component.chat.InputView v, java.util.ArrayList<org.thunderdog.challegram.data.InlineResult<?>> items) { }
    });
    noteField.setHint(Lang.getString(R.string.Tgx101SavedNoteHint));
    noteField.setHintTextColor(Theme.textDecentColor());
    noteField.setTextColor(Theme.textAccentColor());
    noteField.setHighlightColor(Theme.fillingTextSelectionColor());
    noteField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    noteField.setSingleLine(false);
    noteField.setMaxLines(6);
    noteField.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    noteField.setInputType(noteField.getInputType() | android.view.inputmethod.EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | android.view.inputmethod.EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
    noteField.setBackground(null); // inside the capsule
    noteField.setPadding(Screen.dp(4f), Screen.dp(12f), Screen.dp(4f), Screen.dp(12f));
    cursor(noteField);
    noteField.setOnClickListener(v -> {
      if (emojiShown) closeEmoji(true);
    });
    bar.addView(noteField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    ImageView attach = new ImageView(context);
    attach.setImageResource(R.drawable.deproko_baseline_attach_26);
    attach.setColorFilter(Theme.iconColor());
    attach.setScaleType(ImageView.ScaleType.CENTER);
    attach.setContentDescription(Lang.getString(R.string.Tgx101SavedAttach));
    Views.setClickable(attach);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(attach, Screen.dp(22f), this);
    attach.setOnClickListener(v -> openAttach());
    bar.addView(attach, new LinearLayout.LayoutParams(Screen.dp(44f), Screen.dp(44f)));
    ImageView send = new ImageView(context);
    sendButton = send;
    send.setImageResource(R.drawable.baseline_mic_24); // an empty field: hold to record a voice note (user 2026-10-16)
    send.setOnTouchListener(this::onVoiceTouch);
    send.setColorFilter(accent());
    send.setScaleType(ImageView.ScaleType.CENTER);
    send.setContentDescription(Lang.getString(R.string.Tgx101SavedNoteSave));
    Views.setClickable(send);
    // a round press like the chat's send button (a square showed while held)
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(send, Screen.dp(24f), this);
    send.setOnClickListener(v -> saveNote());
    send.setOnLongClickListener(v -> {
      TdApi.FormattedText note = noteText();
      if (note == null) return false;
      UI.forceVibrate(v, false);
      // over the keyboard at the finger, like the message menu in the chats (approved mockups Е, Е2)
      Tgx101SendMenu.show(this, v, note, this::sendOff, this::afterNoteSaved); // the same «into the button» there
      return true;
    });
    bar.addView(send, new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));
    FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    barParams.setMargins(Screen.dp(8f), 0, Screen.dp(8f), Screen.dp(8f));
    content.addView(bar, barParams);
    // the end of the list stays reachable above the capsule, whatever its height (it grows with the text)
    bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      int bottom = Screen.dp(16f) + (v.getVisibility() == View.VISIBLE ? b - t + Screen.dp(8f) : 0);
      if (column.getPaddingBottom() != bottom) {
        column.setPadding(column.getPaddingLeft(), column.getPaddingTop(), column.getPaddingRight(), bottom);
      }
    });
    if (android.os.Build.VERSION.SDK_INT >= 21) bar.setElevation(Screen.dp(4f));
    storageSwitch = new Tgx101StorageSwitch(context, this, Tgx101StorageSwitch.IN_STORAGE, new Tgx101StorageSwitch.Host() {
      @Override
      public int bottomReserved () {
        return noteBar.getVisibility() == View.VISIBLE ? noteBar.getHeight() + Screen.dp(8f) : 0;
      }

      @Override
      public int usualGap () {
        return Screen.dp(12f);
      }

      @Override
      public View passTarget () {
        return scroll;
      }
    });
    storageSwitch.setVisibility(View.GONE);
    content.addView(storageSwitch, Tgx101StorageSwitch.params(content));
    storageSwitch.updateVisibility();
    buildSelectionBars(context, root, outer);

    // pull down: search (like the chat list)
    pullIcon = new ImageView(context);
    pullIcon.setImageResource(R.drawable.baseline_search_24);
    pullIcon.setColorFilter(0xffffffff);
    pullIcon.setScaleType(ImageView.ScaleType.CENTER);
    pullIcon.setBackground(card(accent(), 20f));
    pullIcon.setAlpha(0f);
    FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(Screen.dp(40f), Screen.dp(40f), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    pp.topMargin = Screen.dp(8f);
    root.addView(pullIcon, pp);

    long t = android.os.SystemClock.uptimeMillis();
    reload();
    openedAt = android.os.SystemClock.uptimeMillis();
    org.thunderdog.challegram.Tgx101Diag.mark("storage home: made in " + (openedAt - madeStart) + " ms (first load " + (openedAt - t) + " ms)");
    root.requestFocus();
    return root;
  }

  private final int[] slideLocation = new int[2];
  private final int[] slideNavLocation = new int[2];

  /**
   * TGx101 (user 2026-10-15 «обычный свайп-назад не должен работать»): the home screen is left by the arrow, the
   * system «back» gesture, or a swipe on the note field — and only while the keyboard is down
   */
  @Override
  public boolean canSlideBackFrom (org.thunderdog.challegram.navigation.NavigationController navigationController, float x, float y) {
    // (user 2026-10-16 18:41 «свайп назад на главной — везде, кроме закреплённых»): anywhere but the «Pinned» row, which
    // slides sideways itself; never with the keyboard up or while something is selected (that is cleared first)
    if (getKeyboardState() || !selected.isEmpty()) return false;
    View nav = navigationController.getValue();
    if (nav == null) return false;
    nav.getLocationOnScreen(slideNavLocation);
    for (View sideways : new View[] {pinnedSection != null && pinnedSection.getVisibility() == View.VISIBLE ? pinnedScroll : null, chipsScroll}) {
      if (sideways == null || !sideways.isShown()) continue;
      if (sideways == chipsScroll && !sideways.canScrollHorizontally(-1)) continue; // the kinds row only when it scrolls back
      sideways.getLocationOnScreen(slideLocation);
      float top = slideLocation[1] - slideNavLocation[1];
      if (y >= top && y <= top + sideways.getHeight()) return false;
    }
    if (inFirstFolderRow(y)) return false;
    return super.canSlideBackFrom(navigationController, x, y);
  }


  /** (user 2026-10-17 «область, где первый ряд папок — без свайпов, чтобы без ложных»): the first row of folders */
  private boolean inFirstFolderRow (float y) {
    if (tiles == null || !tiles.isShown() || tiles.getChildCount() == 0) return false;
    View first = tiles.getChildAt(0);
    first.getLocationOnScreen(slideLocation);
    float top = slideLocation[1] - slideNavLocation[1];
    int rowHeight = first instanceof MosaicGrid ? ((MosaicGrid) first).firstRowHeight() : Screen.dp(CAPSULE_HEIGHT + 6f);
    float from = top - Screen.dp(4f);
    if (chipsScroll != null && chipsScroll.isShown()) { // (user 2026-10-17) and the space between «All» and the folders
      chipsScroll.getLocationOnScreen(slideLocation);
      from = Math.min(from, slideLocation[1] - slideNavLocation[1] + chipsScroll.getHeight());
    }
    return y >= from && y <= top + rowHeight + Screen.dp(4f);
  }

  // (user 2026-10-17 «свайп с главной хранилища в обе стороны; справа налево — список чатов выезжает справа»)
  @Override
  public boolean tgx101SlideBackBothWays () {
    return Tgx101SavedStore.get(tdlib).backSwipe != Tgx101SavedStore.BACK_RIGHT; // the user's choice in the storage's settings
  }

  @Override
  public boolean tgx101SlideBackForward () {
    return Tgx101SavedStore.get(tdlib).backSwipe != Tgx101SavedStore.BACK_LEFT;
  }

  @Override
  public boolean tgx101CanSlideBackReverseFrom (org.thunderdog.challegram.navigation.NavigationController navigationController, float x, float y) {
    if (getKeyboardState() || !selected.isEmpty()) return false;
    View nav = navigationController.getValue();
    if (nav == null) return false;
    nav.getLocationOnScreen(slideNavLocation);
    for (View sideways : new View[] {pinnedSection != null && pinnedSection.getVisibility() == View.VISIBLE ? pinnedScroll : null, chipsScroll}) {
      if (sideways == null || !sideways.isShown()) continue;
      if (sideways == chipsScroll && !sideways.canScrollHorizontally(1)) continue; // the kinds row only while it scrolls on
      sideways.getLocationOnScreen(slideLocation);
      float top = slideLocation[1] - slideNavLocation[1];
      if (y >= top && y <= top + sideways.getHeight()) return false;
    }
    return !inFirstFolderRow(y);
  }

  /** (test 2026-10-16: settings opened with the note's keyboard up kept it over the next screen) */
  @Override
  public void onBlur () {
    super.onBlur();
    // (user 2026-10-16 17:50 «перестала появляться плавающая кнопка»): the keyboard closed while another screen was on
    // top never reached here, the button stayed hidden «for the keyboard» — it forgets the keyboard when the screen goes
    if (storageSwitch != null) storageSwitch.setKeyboardShown(false);
    if (noteField != null) {
      org.thunderdog.challegram.tool.Keyboard.hide(noteField);
      noteField.clearFocus();
    }
  }

  @Override
  public void onFocus () {
    long focusStart = android.os.SystemClock.uptimeMillis();
    super.onFocus();
    if (getValue() != null) getValue().post(() -> org.thunderdog.challegram.Tgx101Diag.mark("storage home: focus work " + (android.os.SystemClock.uptimeMillis() - focusStart) + " ms (to the next message)"));
    if (storageSwitch != null) storageSwitch.updateVisibility();
    if (openedAt != 0) {
      org.thunderdog.challegram.Tgx101Diag.mark("storage home: on screen " + (android.os.SystemClock.uptimeMillis() - openedAt) + " ms after it was made");
      openedAt = 0;
    }
    if (pendingAfterOpen != null) {
      Runnable r = pendingAfterOpen;
      pendingAfterOpen = null;
      if (android.os.SystemClock.uptimeMillis() - madeStart > 5000 && column != null) {
        reload(); // made ahead a while ago (the pull from the chats): what it shows may be old
      } else {
        r.run();
      }
      return; // the first focus: everything else was loaded with the screen
    }
    if (column != null && loadedOnce) reload(); // back from a folder / list / settings: counts and tiles may have changed
  }

  private boolean loadedOnce;
  private long openedAt, madeStart;

  /** Something was saved / filed while the home screen is open */
  void reloadSoon () {
    if (column == null || isDestroyed()) return;
    column.removeCallbacks(reloader);
    column.postDelayed(reloader, 400);
  }

  private final Runnable reloader = () -> {
    if (!isDestroyed()) reload();
  };

  private void reload () {
    loadedOnce = true;
    buildChips();
    buildTiles();
    // the folders' counts as Telegram has them: what was deleted elsewhere leaves the folders, then the counts redraw.
    // (user 2026-10-17 «пролаги при входе в хранилище»: the answers rebuilt every tile while the screen was still sliding
    // in — log 16:01, frames up to 66 ms) Once the screen stands
    afterOpen(this::checkFolders);
    loadReactions();
    loadPinned();
    loadRecent();
  }

  private @Nullable Runnable pendingAfterOpen;

  private void afterOpen (Runnable r) {
    if (isFocused()) {
      r.run();
    } else {
      pendingAfterOpen = r;
    }
  }

  private void checkFolders () {
    java.util.List<Long> filed = new java.util.ArrayList<>();
    for (Tgx101SavedStore.Folder f : Tgx101SavedStore.get(tdlib).folders()) filed.addAll(f.messageIds);
    for (int start = 0; start < filed.size(); start += 100) { // in hundreds, as Telegram hands messages out
      final java.util.List<Long> part = new java.util.ArrayList<>(filed.subList(start, Math.min(filed.size(), start + 100)));
      Tgx101SavedKit.byIds(tdlib, part, found -> {
        if (!isDestroyed() && found.size() < part.size()) buildTiles();
      });
    }
  }

  // Kinds

  private static final int[] KIND_FILTER_KINDS = {Tgx101SavedKit.KIND_PHOTO, Tgx101SavedKit.KIND_VIDEO, Tgx101SavedKit.KIND_FILE, Tgx101SavedKit.KIND_LINK, Tgx101SavedKit.KIND_VOICE};

  static TdApi.SearchMessagesFilter filterFor (int kind) {
    switch (kind) {
      case Tgx101SavedKit.KIND_PHOTO: return new TdApi.SearchMessagesFilterPhoto();
      case Tgx101SavedKit.KIND_VIDEO: return new TdApi.SearchMessagesFilterVideo();
      case Tgx101SavedKit.KIND_FILE: return new TdApi.SearchMessagesFilterDocument();
      case Tgx101SavedKit.KIND_LINK: return new TdApi.SearchMessagesFilterUrl();
      case Tgx101SavedKit.KIND_VOICE: return new TdApi.SearchMessagesFilterVoiceNote();
    }
    return null;
  }

  static String kindPlural (int kind) {
    switch (kind) {
      case Tgx101SavedKit.KIND_PHOTO: return Lang.getString(R.string.Tgx101SavedPhotos);
      case Tgx101SavedKit.KIND_VIDEO: return Lang.getString(R.string.Tgx101SavedVideos);
      case Tgx101SavedKit.KIND_FILE: return Lang.getString(R.string.Tgx101SavedFiles);
      case Tgx101SavedKit.KIND_LINK: return Lang.getString(R.string.Tgx101SavedLinks);
      case Tgx101SavedKit.KIND_VOICE: return Lang.getString(R.string.Tgx101SavedVoices);
    }
    return Lang.getString(R.string.Tgx101SavedEverything);
  }

  private TextView chip (String label, boolean dark, View.OnClickListener click) {
    TextView c = text(context, 14f, dark ? onChosen() : Theme.textAccentColor(), false);
    c.setText(label);
    c.setGravity(Gravity.CENTER);
    c.setPadding(Screen.dp(14f), 0, Screen.dp(14f), 0);
    c.setBackground(dark ? chosen(18f) : card(Theme.fillingColor(), 18f));
    Views.setClickable(c);
    c.setOnClickListener(click);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(36f));
    p.rightMargin = Screen.dp(8f);
    c.setLayoutParams(p);
    return c;
  }

  /** last counts per account and kind: a returning home screen starts with them instead of empty chips */
  private static final android.util.SparseArray<int[]> lastCounts = new android.util.SparseArray<>();
  private final TextView[] kindChips = new TextView[KIND_FILTER_KINDS.length];

  /**
   * The chips are made once and only change their numbers (test 2026-10-15: rebuilt on every return, they all showed
   * up for a moment and then vanished — the «phantoms»)
   */
  private void buildChips () {
    int[] counts = lastCounts.get(tdlib.id());
    if (counts == null) {
      counts = new int[KIND_FILTER_KINDS.length];
      java.util.Arrays.fill(counts, -1);
      lastCounts.put(tdlib.id(), counts);
    }
    if (chips.getChildCount() == 0) {
      chips.addView(chip(Lang.getString(R.string.Tgx101SavedEverything), true, v -> Tgx101SavedListController.openKind(this, -1)));
      for (int i = 0; i < KIND_FILTER_KINDS.length; i++) {
        final int kind = KIND_FILTER_KINDS[i];
        TextView c = chip(kindPlural(kind), false, v -> Tgx101SavedListController.openKind(this, kind));
        kindChips[i] = c;
        chips.addView(c);
        applyCount(i, counts[i], false);
      }
    }
    final int[] cache = counts;
    for (int i = 0; i < KIND_FILTER_KINDS.length; i++) {
      final int index = i;
      Tgx101SavedKit.count(tdlib, filterFor(KIND_FILTER_KINDS[i]), count -> {
        if (isDestroyed() || count < 0 || cache[index] == count) return;
        boolean wasHidden = cache[index] <= 0;
        cache[index] = count;
        applyCount(index, count, wasHidden);
      });
    }
  }

  private void applyCount (int index, int count, boolean fadeIn) {
    TextView c = kindChips[index];
    if (c == null) return;
    if (count <= 0) { // nothing of this kind (or not known yet): no chip
      c.setVisibility(View.GONE);
      return;
    }
    c.setText(kindPlural(KIND_FILTER_KINDS[index]) + " " + count);
    if (c.getVisibility() != View.VISIBLE) {
      c.setVisibility(View.VISIBLE);
      if (fadeIn) {
        c.setAlpha(0f);
        c.animate().alpha(1f).setDuration(180).start();
      }
    }
  }

  // Tiles

  private String tilesShown;
  /** reaction → count (Telegram's tags and the ones kept here), shown as tiles */
  private java.util.LinkedHashMap<String, Integer> reactionCounts = new java.util.LinkedHashMap<>();

  private void loadReactions () {
    Tgx101SavedKit.reactionCounts(tdlib, counts -> {
      if (isDestroyed()) return;
      reactionCounts = counts;
      buildTiles();
    });
  }

  /** what the tiles show, to rebuild them only when it changed (no flicker on every return) */
  private String tilesSignature (Tgx101SavedStore store) {
    StringBuilder b = new StringBuilder().append(store.inboxTile).append('|').append(store.folderView).append('|').append(Theme.getColor(ColorId.filling));
    for (Tgx101SavedStore.Folder f : store.folders()) if (f.onHome) b.append('|').append(f.id).append(':').append(f.name).append(':').append(f.messageIds.size()).append(':').append(f.color()).append(':').append(f.shareChatId).append(':').append(f.tileSize);
    for (String tag : store.homeTags()) b.append("|#").append(tag);
    for (java.util.Map.Entry<String, Integer> e : reactionCounts.entrySet()) b.append("|r").append(e.getKey()).append(e.getValue());
    return b.toString();
  }

  private void buildTiles () {
    Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
    String signature = tilesSignature(store);
    if (signature.equals(tilesShown)) return;
    tilesShown = signature;
    // what's below the tiles stays where it is on screen when a tile row comes or goes (test 2026-10-16: a reaction
    // tile pushed «Recent» down under the finger)
    final int heightBefore = tiles.getHeight();
    if (heightBefore > 0 && scroll != null && scroll.getScrollY() > tiles.getBottom()) {
      tiles.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange (View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
          v.removeOnLayoutChangeListener(this);
          int delta = (b - t) - heightBefore;
          if (delta != 0) scroll.scrollBy(0, delta);
        }
      });
    }
    tiles.removeAllViews();
    if (store.folderView == Tgx101SavedStore.VIEW_CAPSULES) {
      buildCapsules(store, false);
      return;
    }
    if (store.folderView == Tgx101SavedStore.VIEW_MOSAIC) {
      buildCapsules(store, true);
      return;
    }
    foldersTitle.setText(Lang.getString(R.string.Tgx101SavedFoldersAndTags));
    java.util.List<View> cells = new java.util.ArrayList<>();
    if (store.inboxTile) {
      cells.add(tile(R.drawable.baseline_archive_24, null, Lang.getString(R.string.Tgx101SavedInbox), Lang.getString(R.string.Tgx101SavedInboxHint), accent(),
        v -> Tgx101SavedListController.openInbox(this), null));
    }
    for (Tgx101SavedStore.Folder f : store.folders()) {
      if (!f.onHome) continue;
      cells.add(folderTile(f, f.name, String.valueOf(f.messageIds.size()), f.color(),
        v -> Tgx101SavedListController.openFolder(this, f.id),
        v -> {
          folderOptions(f);
          return true;
        }));
    }
    for (String tag : store.homeTags()) {
      cells.add(tile(0, "#", tag, Lang.getString(R.string.Tgx101SavedTagTile), 0xffb53c57,
        v -> Tgx101SavedListController.openTag(this, tag),
        v -> {
          tagOptions(tag);
          return true;
        }));
    }
    for (java.util.Map.Entry<String, Integer> e : reactionCounts.entrySet()) {
      if (e.getValue() <= 0) continue;
      final String emoji = e.getKey();
      cells.add(tile(0, emoji, emoji + " " + e.getValue(), Lang.getString(R.string.Tgx101SavedReactionTile), 0xffc0662a,
        v -> Tgx101SavedListController.openReaction(this, emoji), null));
    }
    cells.add(newTile());
    LinearLayout row = null;
    for (int i = 0; i < cells.size(); i++) {
      if (i % 3 == 0) {
        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        tiles.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      }
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Screen.dp(98f), 1f);
      p.setMargins(Screen.dp(4f), Screen.dp(4f), Screen.dp(4f), Screen.dp(4f));
      row.addView(cells.get(i), p);
    }
    for (int i = cells.size() % 3; i != 0 && i < 3; i++) {
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Screen.dp(98f), 1f);
      p.setMargins(Screen.dp(4f), Screen.dp(4f), Screen.dp(4f), Screen.dp(4f));
      row.addView(new View(context), p); // keeps the last row's tiles the same width
    }
  }

  private void tagOptions (String tag) {
    showOptions("#" + Tgx101SavedStore.normalizeTag(tag), new int[] {R.id.btn_edit, R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.Tgx101SavedTagRename), Lang.getString(R.string.Tgx101SavedRemoveFromHome), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.NORMAL, OptionColor.NORMAL, OptionColor.NORMAL}, new int[] {R.drawable.baseline_edit_24, R.drawable.baseline_close_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_edit) {
        Tgx101SavedActions.renameTag(this, tag, this::forceTiles);
      } else if (id == R.id.btn_delete) {
        Tgx101SavedStore.get(tdlib).setTagOnHome(tag, false);
        buildTiles();
        UI.showToast(R.string.Tgx101SavedTagRemovedHint, android.widget.Toast.LENGTH_LONG); // where it comes back from
      }
      return true;
    });
  }

  // Capsules (approved mockup «Папки Хранилища», 5; user 2026-10-16 «капсулы — под наше фирменное закругление»)

  /** the corner of the chats' field and menus (18 dp on a ~48 dp capsule), on a 34 dp capsule */
  private static final float CAPSULE_HEIGHT = 34f, CAPSULE_RADIUS = 12.5f;

  /** folders as capsules one after another, wrapping like tags; then tags and reactions the same way */
  private void buildCapsules (Tgx101SavedStore store, boolean mosaic) {
    foldersTitle.setText(Lang.getString(R.string.Tgx101SavedFoldersTitle));
    if (mosaic) {
      buildMosaic(store);
    } else {
      buildFolderCapsules(store);
    }
    buildMarks(store);
  }

  private void buildFolderCapsules (Tgx101SavedStore store) {
    CapsuleFlow folders = new CapsuleFlow(context);
    folders.setMover(new CapsuleFlow.Mover() {
      @Override
      public void onHeldStill (long folderId) {
        Tgx101SavedStore.Folder f = Tgx101SavedStore.get(tdlib).folder(folderId);
        if (f != null) folderOptions(f);
      }

      @Override
      public void onMoved (long folderId, long targetId, boolean after) {
        Tgx101SavedStore.get(tdlib).moveFolder(folderId, targetId, after);
        org.thunderdog.challegram.Tgx101Diag.mark("storage: folder " + folderId + " moved " + (after ? "after " : "before ") + targetId);
        forceTiles();
      }
    });
    if (store.inboxTile) {
      ImageView inbox = new ImageView(context);
      inbox.setImageResource(R.drawable.baseline_archive_24);
      inbox.setColorFilter(accent());
      folders.addView(capsule(inbox, Lang.getString(R.string.Tgx101SavedInbox), null, Theme.fillingColor(),
        v -> Tgx101SavedListController.openInbox(this), null));
    }
    for (Tgx101SavedStore.Folder f : store.folders()) {
      if (!f.onHome) continue;
      int color = f.color();
      View swatch = new View(context);
      swatch.setBackground(card(color, 3f));
      int[] copying = Tgx101FolderShare.progressOf(f.id);
      String count = copying != null ? copying[0] + "/" + copying[1] : String.valueOf(f.messageIds.size()); // copying a shared folder: done/all
      View c = capsule(swatch, f.name, count, Theme.isDark() ? tint(color, .30f) : tint(color, .14f),
        v -> Tgx101SavedListController.openFolder(this, f.id), null);
      if (f.isShared()) {
        // the «shared» sign: a small icon after the name (a «↗» became an emoji key in the app's emoji)
        ImageView sharedSign = new ImageView(context);
        sharedSign.setImageResource(R.drawable.baseline_share_24);
        sharedSign.setColorFilter(Theme.textDecentColor());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Screen.dp(13f), Screen.dp(13f));
        sp.leftMargin = Screen.dp(5f);
        LinearLayout row = (LinearLayout) c;
        row.addView(sharedSign, row.getChildCount() - 1, sp); // before the count
      }
      c.setTag(f.id); // held: lifted and carried by the flow; held still: the folder's menu
      folders.addView(c);
    }
    // «+ New»: a dashed capsule
    TextView add = text(context, 13.5f, accent(), true);
    add.setText("+ " + Lang.getString(R.string.Tgx101SavedNew));
    add.setGravity(Gravity.CENTER_VERTICAL);
    add.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    GradientDrawable dashed = new GradientDrawable();
    dashed.setCornerRadius(Screen.dp(CAPSULE_RADIUS));
    dashed.setStroke(Screen.dp(1.5f), Theme.separatorColor(), Screen.dp(6f), Screen.dp(4f));
    add.setBackground(dashed);
    Views.setClickable(add);
    add.setOnClickListener(v -> Tgx101SavedActions.newFolderOrTag(this, this::buildTiles));
    folders.addView(add, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(CAPSULE_HEIGHT)));
    tiles.addView(folders, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  // (a folder's tag and reactions on its tile were tried and dropped — user 2026-10-17 «метка тоже не нужна»)
  // Mosaic (user 2026-10-17 chose mockup «Ж»): three columns; the biggest folder is a tall tile, the next two are wide,
  // the rest are squares — by how many records each has, packed with no holes. The user's order stays the order

  private void buildMosaic (Tgx101SavedStore store) {
    MosaicGrid grid = new MosaicGrid(context);
    java.util.List<Tgx101SavedStore.Folder> shown = new java.util.ArrayList<>();
    for (Tgx101SavedStore.Folder f : store.folders()) if (f.onHome) shown.add(f);
    java.util.List<Tgx101SavedStore.Folder> byWeight = new java.util.ArrayList<>(shown);
    java.util.Collections.sort(byWeight, (a, b) -> Integer.compare(b.messageIds.size(), a.messageIds.size()));
    java.util.Set<Long> tall = new java.util.HashSet<>(), wide = new java.util.HashSet<>();
    for (Tgx101SavedStore.Folder f : shown) if (f.tileSize != Tgx101SavedStore.TILE_AUTO) byWeight.remove(f); // the chosen ones keep their size
    if (shown.size() >= 3 && !byWeight.isEmpty() && byWeight.get(0).messageIds.size() > 0) tall.add(byWeight.get(0).id);
    for (int i = tall.isEmpty() ? 0 : 1, n = 0; i < byWeight.size() && n < 2 && shown.size() >= 2; i++) {
      Tgx101SavedStore.Folder f = byWeight.get(i);
      if (f.messageIds.size() == 0) break;
      wide.add(f.id);
      n++;
    }
    if (store.inboxTile) {
      grid.addView(mosaicTile(null, Lang.getString(R.string.Tgx101SavedInbox), null, Theme.fillingColor(), Theme.textAccentColor(), false,
        v -> Tgx101SavedListController.openInbox(this), null), new MosaicGrid.Cell(1, 1));
    }
    for (Tgx101SavedStore.Folder f : shown) {
      int[] copying = Tgx101FolderShare.progressOf(f.id);
      int count = f.messageIds.size();
      String number = copying != null ? copying[0] + "/" + copying[1] : String.valueOf(count);
      boolean isTall = tall.contains(f.id), isWide = wide.contains(f.id);
      if (f.tileSize != Tgx101SavedStore.TILE_AUTO) { // chosen by the user (mockup «З»)
        isTall = f.tileSize == Tgx101SavedStore.TILE_TALL || f.tileSize == Tgx101SavedStore.TILE_BIG;
        isWide = f.tileSize == Tgx101SavedStore.TILE_WIDE || f.tileSize == Tgx101SavedStore.TILE_BIG;
      }
      View t = mosaicTile(number, f.name, null,
        f.color(), 0xffffffff, isTall, v -> Tgx101SavedListController.openFolder(this, f.id), v -> {
          folderOptions(f);
          return true;
        });
      grid.addView(t, new MosaicGrid.Cell(isWide ? 2 : 1, isTall ? 2 : 1));
    }
    TextView add = text(context, 22f, accent(), false);
    add.setText("+");
    add.setGravity(Gravity.CENTER);
    GradientDrawable dashed = new GradientDrawable();
    dashed.setCornerRadius(Screen.dp(16f));
    dashed.setStroke(Screen.dp(1.5f), Theme.separatorColor(), Screen.dp(6f), Screen.dp(4f));
    add.setBackground(dashed);
    add.setContentDescription(Lang.getString(R.string.Tgx101SavedNew));
    Views.setClickable(add);
    add.setOnClickListener(v -> Tgx101SavedActions.newFolderOrTag(this, this::buildTiles));
    grid.addView(add, new MosaicGrid.Cell(1, 1));
    tiles.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  /** a folder in the mosaic: the number on top (big on the tall one), the name at the bottom on up to two lines */
  private View mosaicTile (@Nullable String number, String name, @Nullable String unit, int background, int textColor, boolean big, View.OnClickListener click, @Nullable View.OnLongClickListener longClick) {
    LinearLayout t = new LinearLayout(context);
    t.setOrientation(LinearLayout.VERTICAL);
    t.setPadding(Screen.dp(12f), Screen.dp(10f), Screen.dp(12f), Screen.dp(10f));
    t.setBackground(card(background, 16f));
    Views.setClickable(t);
    t.setOnClickListener(click);
    if (longClick != null) t.setOnLongClickListener(longClick);
    if (number != null) {
      LinearLayout top = new LinearLayout(context);
      top.setOrientation(LinearLayout.HORIZONTAL);
      top.setGravity(Gravity.BOTTOM);
      TextView k = text(context, big ? 28f : 19f, textColor, true);
      k.setText(number);
      k.setSingleLine(true);
      top.addView(k);
      if (unit != null && !unit.isEmpty()) {
        TextView u = text(context, 12f, (textColor & 0x00ffffff) | 0xbb000000, false);
        u.setText(unit);
        u.setSingleLine(true);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        up.leftMargin = Screen.dp(5f);
        up.bottomMargin = Screen.dp(big ? 5f : 3f);
        top.addView(u, up);
      }
      t.addView(top);
    }
    t.addView(new View(context), new LinearLayout.LayoutParams(1, 0, 1f));
    TextView n = tileName(context, big ? 15f : 13f, big ? 12f : 10.5f, textColor);
    n.setText(name);
    n.setMaxLines(big ? 3 : 2);
    n.setEllipsize(TextUtils.TruncateAt.END);
    n.setLineSpacing(0, 1.05f);
    t.addView(n);
    return t;
  }

  /** three columns of cells; a child spans 1–2 columns and 1–2 rows; each goes to the first place it fits */
  private static final class MosaicGrid extends ViewGroup {
    static final class Cell extends ViewGroup.LayoutParams {
      final int cols, rows;
      int x, y;

      Cell (int cols, int rows) {
        super(WRAP_CONTENT, WRAP_CONTENT);
        this.cols = cols;
        this.rows = rows;
      }
    }

    private static final int COLUMNS = 3;
    private final int gap = Screen.dp(8f);
    private int column, unit;

    MosaicGrid (Context context) {
      super(context);
    }

    int firstRowHeight () {
      return unit > 0 ? unit : Screen.dp(72f);
    }

    @Override
    protected boolean checkLayoutParams (ViewGroup.LayoutParams p) {
      return p instanceof Cell;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams () {
      return new Cell(1, 1);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      column = (width - getPaddingLeft() - getPaddingRight() - gap * (COLUMNS - 1)) / COLUMNS;
      unit = Math.max(Screen.dp(72f), Math.round(column * .72f));
      java.util.List<boolean[]> taken = new java.util.ArrayList<>();
      int rowsUsed = 0;
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        if (child.getVisibility() == GONE) continue;
        Cell c = (Cell) child.getLayoutParams();
        int cols = Math.min(c.cols, COLUMNS);
        for (int y = 0; ; y++) {
          boolean placed = false;
          for (int x = 0; x + cols <= COLUMNS; x++) {
            if (free(taken, x, y, cols, c.rows)) {
              for (int dy = 0; dy < c.rows; dy++) {
                while (taken.size() <= y + dy) taken.add(new boolean[COLUMNS]);
                for (int dx = 0; dx < cols; dx++) taken.get(y + dy)[x + dx] = true;
              }
              c.x = x;
              c.y = y;
              placed = true;
              break;
            }
          }
          if (placed) break;
        }
        rowsUsed = Math.max(rowsUsed, c.y + c.rows);
        int w = cols * column + (cols - 1) * gap, h = c.rows * unit + (c.rows - 1) * gap;
        child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
      }
      int height = getPaddingTop() + getPaddingBottom() + (rowsUsed > 0 ? rowsUsed * unit + (rowsUsed - 1) * gap : 0);
      setMeasuredDimension(width, height);
    }

    private static boolean free (java.util.List<boolean[]> taken, int x, int y, int cols, int rows) {
      for (int dy = 0; dy < rows; dy++) {
        if (y + dy >= taken.size()) continue;
        for (int dx = 0; dx < cols; dx++) if (taken.get(y + dy)[x + dx]) return false;
      }
      return true;
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      boolean rtl = Lang.rtl();
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        if (child.getVisibility() == GONE) continue;
        Cell c = (Cell) child.getLayoutParams();
        int left = getPaddingLeft() + c.x * (column + gap);
        if (rtl) left = getWidth() - getPaddingRight() - (c.x * (column + gap)) - child.getMeasuredWidth();
        int top = getPaddingTop() + c.y * (unit + gap);
        child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
      }
    }
  }

  private void buildMarks (Tgx101SavedStore store) {
    CapsuleFlow marks = new CapsuleFlow(context);
    for (String tag : store.homeTags()) {
      TextView hash = text(context, 14f, 0xffb53c57, true);
      hash.setText("#");
      marks.addView(capsule(hash, tag, null, Theme.fillingColor(),
        v -> Tgx101SavedListController.openTag(this, tag),
        v -> {
          tagOptions(tag);
          return true;
        }));
    }
    for (java.util.Map.Entry<String, Integer> e : reactionCounts.entrySet()) {
      if (e.getValue() <= 0) continue;
      final String emoji = e.getKey();
      marks.addView(capsule(null, emoji, String.valueOf(e.getValue()), Theme.fillingColor(),
        v -> Tgx101SavedListController.openReaction(this, emoji), null));
    }
    if (marks.getChildCount() > 0) {
      TextView title = sectionTitle(context, Lang.getString(R.string.Tgx101SavedTagsAndReactions));
      title.setPadding(Screen.dp(4f), Screen.dp(14f), Screen.dp(4f), Screen.dp(6f)); // the column is already 12 dp in
      tiles.addView(title);
      tiles.addView(marks, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
  }

  /** one capsule: a sign (colour square, «#», an icon), the name, the count; the whole capsule is one press */
  private View capsule (@Nullable View sign, CharSequence name, @Nullable String count, int background, View.OnClickListener click, @Nullable View.OnLongClickListener longClick) {
    LinearLayout c = new LinearLayout(context);
    c.setOrientation(LinearLayout.HORIZONTAL);
    c.setGravity(Gravity.CENTER_VERTICAL);
    c.setPadding(Screen.dp(sign != null ? 10f : 12f), 0, Screen.dp(12f), 0);
    c.setBackground(card(background, CAPSULE_RADIUS));
    c.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(CAPSULE_HEIGHT)));
    Views.setClickable(c);
    c.setOnClickListener(click);
    if (longClick != null) c.setOnLongClickListener(longClick);
    if (sign != null) {
      boolean square = !(sign instanceof TextView) && !(sign instanceof ImageView);
      LinearLayout.LayoutParams sp = square ? new LinearLayout.LayoutParams(Screen.dp(9f), Screen.dp(9f))
        : new LinearLayout.LayoutParams(sign instanceof ImageView ? Screen.dp(16f) : ViewGroup.LayoutParams.WRAP_CONTENT, sign instanceof ImageView ? Screen.dp(16f) : ViewGroup.LayoutParams.WRAP_CONTENT);
      sp.rightMargin = Screen.dp(square ? 7f : 4f);
      c.addView(sign, sp);
    }
    TextView n = text(context, 13.5f, Theme.textAccentColor(), true);
    n.setText(name);
    n.setSingleLine(true);
    n.setEllipsize(TextUtils.TruncateAt.END);
    n.setMaxWidth(Screen.currentWidth() - Screen.dp(120f)); // a long name ends with «…», the capsule never leaves the screen
    c.addView(n);
    if (count != null) {
      TextView k = text(context, 12f, Theme.textDecentColor(), false);
      k.setText(count);
      k.setSingleLine(true);
      LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      kp.leftMargin = Screen.dp(6f);
      c.addView(k, kp);
    }
    return c;
  }

  /** children one after another, onto the next line when a row is full, with even gaps; right to left in RTL */
  private static final class CapsuleFlow extends ViewGroup {
    private final int gap = Screen.dp(6f);

    interface Mover {
      void onHeldStill (long folderId);
      void onMoved (long folderId, long targetId, boolean after);
    }

    private @Nullable Mover mover;

    CapsuleFlow (Context context) {
      super(context);
      setPadding(Screen.dp(4f), Screen.dp(4f), Screen.dp(4f), Screen.dp(4f));
      setClipChildren(false);
    }

    void setMover (Mover mover) {
      this.mover = mover;
    }

    // a folder capsule held: it lifts and follows the finger; let go over another folder, it goes there

    private @Nullable View held, target;
    private boolean holding, moved, targetAfter;
    private float downX, downY;
    private final Runnable hold = this::onHold;

    private @Nullable View folderAt (float x, float y) {
      for (int i = 0; i < getChildCount(); i++) {
        View c = getChildAt(i);
        if (c.getTag() instanceof Long && x >= c.getLeft() && x <= c.getRight() && y >= c.getTop() && y <= c.getBottom()) return c;
      }
      return null;
    }

    private void onHold () {
      if (held == null) return;
      holding = true;
      held.setPressed(false);
      held.cancelLongPress();
      getParent().requestDisallowInterceptTouchEvent(true);
      UI.forceVibrate(held, true);
      held.animate().scaleX(1.08f).scaleY(1.08f).setDuration(140).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
      if (android.os.Build.VERSION.SDK_INT >= 21) held.setElevation(Screen.dp(6f)); // over its neighbours while carried (the children's order is the layout's — no bringToFront)
    }

    private void setTarget (@Nullable View t, boolean after) {
      if (t == target && after == targetAfter) return;
      if (target != null) target.setAlpha(1f);
      target = t;
      targetAfter = after;
      if (target != null) {
        target.setAlpha(.55f); // where it will go: the capsule it lands next to dims
        UI.hapticVibrate(target, false);
      }
    }

    private void finish (boolean apply) {
      removeCallbacks(hold);
      View h = held, t = target;
      boolean after = targetAfter, wasHolding = holding, wasMoved = moved;
      held = null;
      holding = moved = false;
      setTarget(null, false);
      if (h == null) return;
      if (wasHolding) {
        h.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).setDuration(160).start();
        if (android.os.Build.VERSION.SDK_INT >= 21) h.setElevation(0f);
      }
      if (!apply || !wasHolding || mover == null) return;
      long id = (Long) h.getTag();
      if (wasMoved && t != null) mover.onMoved(id, (Long) t.getTag(), after);
      else if (!wasMoved) mover.onHeldStill(id);
    }

    @Override
    public boolean onInterceptTouchEvent (MotionEvent e) {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          finish(false);
          downX = e.getX();
          downY = e.getY();
          held = folderAt(downX, downY);
          if (held != null) postDelayed(hold, android.view.ViewConfiguration.getLongPressTimeout());
          return false;
        }
        case MotionEvent.ACTION_MOVE: {
          if (holding) return true;
          if (held != null && Math.hypot(e.getX() - downX, e.getY() - downY) > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
            removeCallbacks(hold); // a scroll or a swipe, not a hold
            held = null;
          }
          return false;
        }
        case MotionEvent.ACTION_UP: {
          if (holding) {
            finish(true); // held and let go in place: the folder's menu
            return true;
          }
          finish(false);
          return false;
        }
        case MotionEvent.ACTION_CANCEL:
          finish(false);
          return false;
      }
      return false;
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (!holding || held == null) return super.onTouchEvent(e);
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_MOVE: {
          float dx = e.getX() - downX, dy = e.getY() - downY;
          if (!moved && Math.hypot(dx, dy) > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) moved = true;
          held.setTranslationX(dx);
          held.setTranslationY(dy);
          View over = null;
          float cx = held.getLeft() + held.getWidth() / 2f + dx, cy = held.getTop() + held.getHeight() / 2f + dy;
          for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c != held && c.getTag() instanceof Long && cy >= c.getTop() - gap && cy <= c.getBottom() + gap && cx >= c.getLeft() && cx <= c.getRight()) {
              over = c;
              break;
            }
          }
          boolean after = over != null && cx > over.getLeft() + over.getWidth() / 2f;
          if (over == null) {
            // (user 2026-10-16 18:37 «в строке есть место ещё для одной папки, но перетащить туда нельзя»): over the
            // free end of a row — after the row's last folder; before the row's first, past its start
            View rowLast = null, rowFirst = null;
            for (int i = 0; i < getChildCount(); i++) {
              View c = getChildAt(i);
              if (c == held || !(c.getTag() instanceof Long) || cy < c.getTop() - gap || cy > c.getBottom() + gap) continue;
              if (rowFirst == null || c.getLeft() < rowFirst.getLeft()) rowFirst = c;
              if (rowLast == null || c.getRight() > rowLast.getRight()) rowLast = c;
            }
            if (rowLast != null && cx > rowLast.getRight()) {
              over = rowLast;
              after = true;
            } else if (rowFirst != null && cx < rowFirst.getLeft()) {
              over = rowFirst;
              after = false;
            }
            if (over == null) { // below the folders or over «+ New»: to the very end
              View lastFolder = null;
              for (int i = getChildCount() - 1; i >= 0 && lastFolder == null; i--) {
                View c = getChildAt(i);
                if (c != held && c.getTag() instanceof Long) lastFolder = c;
              }
              if (lastFolder != null && cy > lastFolder.getTop()) {
                over = lastFolder;
                after = true;
              }
            }
          }
          if (Lang.rtl()) after = !after;
          setTarget(over, after);
          return true;
        }
        case MotionEvent.ACTION_UP:
          finish(true);
          return true;
        case MotionEvent.ACTION_CANCEL:
          finish(false);
          return true;
      }
      return true;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      int inner = width - getPaddingLeft() - getPaddingRight();
      int x = 0, y = 0, rowHeight = 0;
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        if (child.getVisibility() == GONE) continue;
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        int hSpec = lp != null && lp.height > 0 ? MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        child.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST), hSpec);
        int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
        if (x > 0 && x + w > inner) {
          x = 0;
          y += rowHeight + gap;
          rowHeight = 0;
        }
        x += w + gap;
        rowHeight = Math.max(rowHeight, h);
      }
      setMeasuredDimension(width, y + rowHeight + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      int inner = r - l - getPaddingLeft() - getPaddingRight();
      boolean rtl = Lang.rtl();
      int x = 0, y = 0, rowHeight = 0;
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        if (child.getVisibility() == GONE) continue;
        int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
        if (x > 0 && x + w > inner) {
          x = 0;
          y += rowHeight + gap;
          rowHeight = 0;
        }
        int left = rtl ? getPaddingLeft() + inner - x - w : getPaddingLeft() + x;
        int top = getPaddingTop() + y;
        child.layout(left, top, left + w, top + h);
        x += w + gap;
        rowHeight = Math.max(rowHeight, h);
      }
    }
  }

  private View tile (int iconRes, @Nullable String glyph, String name, String sub, int color, View.OnClickListener click, @Nullable View.OnLongClickListener longClick) {
    LinearLayout t = new LinearLayout(context);
    t.setOrientation(LinearLayout.VERTICAL);
    t.setPadding(Screen.dp(12f), Screen.dp(12f), Screen.dp(10f), Screen.dp(10f));
    t.setBackground(card(Theme.fillingColor(), 14f));
    Views.setClickable(t);
    t.setOnClickListener(click);
    if (longClick != null) t.setOnLongClickListener(longClick);
    FrameLayout icon = new FrameLayout(context);
    icon.setBackground(card(tint(color, .16f), glyph != null ? 17f : 10f));
    if (glyph != null) {
      TextView g = text(context, 18f, color, true);
      g.setText(Tgx101SavedKit.emoji(glyph));
      g.setGravity(Gravity.CENTER);
      icon.addView(g, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    } else {
      ImageView i = new ImageView(context);
      i.setImageResource(iconRes);
      i.setColorFilter(color);
      i.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
      FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f), Gravity.CENTER);
      icon.addView(i, ip);
    }
    t.addView(icon, new LinearLayout.LayoutParams(Screen.dp(34f), Screen.dp(34f)));
    TextView n = text(context, 14f, Theme.textAccentColor(), true);
    n.setText(Tgx101SavedKit.emoji(name));
    n.setSingleLine(true);
    n.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    np.topMargin = Screen.dp(8f);
    t.addView(n, np);
    TextView s = text(context, 12f, Theme.textDecentColor(), false);
    s.setText(sub);
    s.setSingleLine(true);
    s.setEllipsize(TextUtils.TruncateAt.END);
    t.addView(s);
    return t;
  }

  /**
   * A folder (approved mockup «Плитки папок», 3): the tile itself in the folder's colour, the name on top (up to two
   * lines), and below it the last three things in the folder, small, with «+N» for the rest — «empty» when there's none
   */
  private View folderTile (Tgx101SavedStore.Folder f, String name, String sub, int color, View.OnClickListener click, View.OnLongClickListener longClick) {
    LinearLayout t = new LinearLayout(context);
    t.setOrientation(LinearLayout.VERTICAL);
    t.setPadding(Screen.dp(11f), Screen.dp(10f), Screen.dp(10f), Screen.dp(10f));
    t.setBackground(card(Theme.isDark() ? tint(color, .38f) : tint(color, .20f), 14f));
    Views.setClickable(t);
    t.setOnClickListener(click);
    t.setOnLongClickListener(longClick);
    int ink = Theme.isDark() ? Tgx101SavedListController.lighten(color) : color;
    TextView n = tileName(context, 14f, 11f, ink);
    n.setText(name);
    n.setMaxLines(2);
    n.setEllipsize(TextUtils.TruncateAt.END);
    n.setLineSpacing(0, .95f);
    t.addView(n, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    LinearLayout previews = new LinearLayout(context);
    previews.setOrientation(LinearLayout.HORIZONTAL);
    previews.setGravity(Gravity.CENTER_VERTICAL);
    t.addView(previews, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(26f)));
    int[] copying = Tgx101FolderShare.progressOf(f.id);
    if (copying != null) { // approved mockup «Поделиться папкой», В: the copying on the tile
      TextView p = text(context, 11f, ink, false);
      p.setText(Lang.getString(R.string.Tgx101ShareTileProgress, copying[0], copying[1]));
      previews.addView(p);
      android.widget.ProgressBar bar = new android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
      bar.setMax(Math.max(1, copying[1]));
      bar.setProgress(copying[0]);
      t.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(4f)));
      return t;
    }
    if (f.isShared()) { // a small «shared» sign after the name
      n.setText(name + "  ⇗"); // «⇗» is no emoji (a «↗» turned into an emoji key)
    }
    java.util.List<Long> ids = new java.util.ArrayList<>(f.messageIds);
    if (ids.isEmpty()) {
      TextView empty = text(context, 12f, tint(ink, .75f), false);
      empty.setText(Lang.getString(R.string.Tgx101SavedFolderEmptyShort));
      previews.addView(empty);
      return t;
    }
    java.util.Collections.sort(ids, java.util.Collections.reverseOrder()); // the newest first
    final int total = ids.size();
    Tgx101SavedKit.byIds(tdlib, ids.subList(0, Math.min(3, ids.size())), messages -> {
      if (isDestroyed()) return;
      previews.removeAllViews();
      for (TdApi.Message m : messages) {
        ImageView p = new ImageView(context);
        p.setBackground(card(tint(0xff000000, .22f), 6f));
        if (android.os.Build.VERSION.SDK_INT >= 21) p.setClipToOutline(true);
        if (Tgx101SavedKit.thumbFile(m) != null || Tgx101SavedKit.minithumb(m) != null) {
          p.setScaleType(ImageView.ScaleType.CENTER_CROP);
          Tgx101SavedKit.loadThumb(tdlib, p, m, Screen.dp(26f));
        } else {
          p.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
          p.setPadding(Screen.dp(4f), Screen.dp(4f), Screen.dp(4f), Screen.dp(4f));
          p.setImageResource(Tgx101SavedListController.kindIcon(Tgx101SavedKit.kindOf(m)));
          p.setColorFilter(ink);
        }
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f));
        pp.rightMargin = Screen.dp(3f);
        previews.addView(p, pp);
      }
      if (total > messages.size()) {
        TextView more = text(context, 11f, ink, true);
        more.setText("+" + (total - messages.size()));
        more.setPadding(Screen.dp(2f), 0, 0, 0);
        previews.addView(more);
      }
    });
    return t;
  }

  private View newTile () {
    LinearLayout t = new LinearLayout(context);
    t.setOrientation(LinearLayout.VERTICAL);
    t.setGravity(Gravity.CENTER);
    GradientDrawable d = new GradientDrawable();
    d.setCornerRadius(Screen.dp(14f));
    d.setStroke(Screen.dp(1.5f), Theme.separatorColor(), Screen.dp(6f), Screen.dp(4f));
    t.setBackground(d);
    Views.setClickable(t);
    t.setOnClickListener(v -> Tgx101SavedActions.newFolderOrTag(this, this::buildTiles));
    ImageView plus = new ImageView(context);
    plus.setImageResource(R.drawable.baseline_add_24);
    plus.setColorFilter(accent());
    t.addView(plus, new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f)));
    TextView l = text(context, 13f, accent(), false);
    l.setText(Lang.getString(R.string.Tgx101SavedNew));
    l.setGravity(Gravity.CENTER);
    l.setPadding(0, Screen.dp(4f), 0, 0);
    t.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)); // centred under the «+»
    return t;
  }

  private void folderOptions (Tgx101SavedStore.Folder f) {
    // approved mockup «Поделиться папкой»: share it, or — once shared — its link, «Update», «Stop sharing»
    java.util.List<Integer> ids = new java.util.ArrayList<>();
    java.util.List<String> names = new java.util.ArrayList<>();
    java.util.List<Integer> colors = new java.util.ArrayList<>();
    java.util.List<Integer> icons = new java.util.ArrayList<>();
    if (f.isShared()) {
      ids.add(R.id.btn_share); names.add(Lang.getString(R.string.Tgx101ShareLinkItem)); colors.add(OptionColor.NORMAL); icons.add(R.drawable.baseline_link_24);
      ids.add(R.id.btn_tgx101ShareUpdate); names.add(Lang.getString(R.string.Tgx101ShareUpdate)); colors.add(OptionColor.NORMAL); icons.add(R.drawable.baseline_sync_24);
      ids.add(R.id.btn_tgx101ShareStop); names.add(Lang.getString(R.string.Tgx101ShareStop)); colors.add(OptionColor.NORMAL); icons.add(R.drawable.baseline_cancel_24);
    } else {
      ids.add(R.id.btn_share); names.add(Lang.getString(R.string.Tgx101ShareFolder)); colors.add(OptionColor.BLUE); icons.add(R.drawable.baseline_share_24);
    }
    ids.add(R.id.btn_edit); names.add(Lang.getString(R.string.Tgx101SavedRename)); colors.add(OptionColor.NORMAL); icons.add(R.drawable.baseline_edit_24);
    ids.add(R.id.btn_tgx101SavedFolderColor); names.add(Lang.getString(R.string.Tgx101SavedFolderColor)); colors.add(OptionColor.NORMAL); icons.add(R.drawable.baseline_palette_24);
    if (Tgx101SavedStore.get(tdlib).folderView == Tgx101SavedStore.VIEW_MOSAIC) {
      ids.add(R.id.btn_tgx101SavedFolderSize); names.add(Lang.getString(R.string.Tgx101SavedFolderSize)); colors.add(OptionColor.NORMAL); icons.add(R.drawable.tgx101_storage_tiles_24);
    }
    if (!f.messageIds.isEmpty()) { // (user 2026-10-17) every record of the folder gets the tags at once
      ids.add(R.id.btn_tgx101SavedFolderTags); names.add(Lang.getString(R.string.Tgx101SavedFolderTags)); colors.add(OptionColor.NORMAL); icons.add(R.drawable.baseline_label_24);
    }
    ids.add(R.id.btn_close); names.add(Lang.getString(R.string.Tgx101SavedRemoveFromHome)); colors.add(OptionColor.NORMAL); icons.add(R.drawable.baseline_close_24);
    ids.add(R.id.btn_delete); names.add(Lang.getString(R.string.Tgx101SavedDeleteFolder)); colors.add(OptionColor.RED); icons.add(R.drawable.baseline_delete_24);
    int[] idArr = new int[ids.size()], colorArr = new int[ids.size()], iconArr = new int[ids.size()];
    for (int i = 0; i < idArr.length; i++) {
      idArr[i] = ids.get(i);
      colorArr[i] = colors.get(i);
      iconArr[i] = icons.get(i);
    }
    showOptions(f.name, idArr, names.toArray(new String[0]), colorArr, iconArr, (itemView, id) -> {
        Tgx101SavedStore store = Tgx101SavedStore.get(tdlib);
        if (id == R.id.btn_share) {
          if (f.isShared()) Tgx101FolderShare.showLink(this, f);
          else Tgx101FolderShare.setup(this, f, this::forceTiles);
        } else if (id == R.id.btn_tgx101ShareUpdate) {
          Tgx101FolderShare.update(this, f);
        } else if (id == R.id.btn_tgx101ShareStop) {
          Tgx101FolderShare.stop(this, f, this::forceTiles);
        } else if (id == R.id.btn_edit) {
          Tgx101SavedActions.askName(this, Lang.getString(R.string.Tgx101SavedRename), f.name, name -> {
            store.renameFolder(f.id, name);
            buildTiles();
          });
        } else if (id == R.id.btn_tgx101SavedFolderColor) {
          chooseFolderColor(f);
        } else if (id == R.id.btn_tgx101SavedFolderSize) {
          chooseFolderSize(f);
        } else if (id == R.id.btn_tgx101SavedFolderTags) {
          Tgx101SavedKit.byIds(tdlib, new java.util.ArrayList<>(f.messageIds), messages -> {
            if (!isDestroyed() && !messages.isEmpty()) Tgx101SavedActions.tag(this, messages, this::buildTiles);
          });
        } else if (id == R.id.btn_close) {
          store.setFolderOnHome(f.id, false);
          buildTiles();
        } else if (id == R.id.btn_delete) {
          store.deleteFolder(f.id); // only the folder: the messages stay in Saved
          buildTiles();
        }
        return true;
      });
  }

  /** (user 2026-10-17, mockup «З»): the tile's size — automatic by the number of records, or one of four */
  private void chooseFolderSize (Tgx101SavedStore.Folder f) {
    int[] sizes = {Tgx101SavedStore.TILE_AUTO, Tgx101SavedStore.TILE_SMALL, Tgx101SavedStore.TILE_WIDE, Tgx101SavedStore.TILE_TALL, Tgx101SavedStore.TILE_BIG};
    int[] names = {R.string.Tgx101SavedSizeAuto, R.string.Tgx101SavedSizeSmall, R.string.Tgx101SavedSizeWide, R.string.Tgx101SavedSizeTall, R.string.Tgx101SavedSizeBig};
    int[] optionIds = new int[sizes.length];
    String[] labels = new String[sizes.length];
    int[] icons = new int[sizes.length];
    for (int i = 0; i < sizes.length; i++) {
      optionIds[i] = i + 1;
      labels[i] = Lang.getString(names[i]);
      icons[i] = f.tileSize == sizes[i] ? R.drawable.baseline_check_24 : 0;
    }
    showOptions(f.name, optionIds, labels, null, icons, (itemView, id) -> {
      Tgx101SavedStore.get(tdlib).setFolderTileSize(f.id, sizes[id - 1]);
      forceTiles();
      return true;
    });
  }

  /** (user 2026-10-16) twelve colours in two rows; the folder's own is ringed. Its colour lives in one place, so the
   *  tiles, capsules, the records' marks, the lists and the «Send» menu all show the new one */
  private void chooseFolderColor (Tgx101SavedStore.Folder f) {
    final Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, f.name));
      TextView hint = text(context, 13f, Theme.textDecentColor(), false);
      hint.setText(Lang.getString(R.string.Tgx101SavedFolderColorHint));
      hint.setPadding(Screen.dp(20f), 0, Screen.dp(20f), Screen.dp(10f));
      body.addView(hint);
      int perRow = 6;
      int[] palette = Tgx101SavedStore.FOLDER_COLORS;
      for (int start = 0; start < palette.length; start += perRow) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Screen.dp(14f), Screen.dp(4f), Screen.dp(14f), Screen.dp(4f));
        for (int i = start; i < Math.min(palette.length, start + perRow); i++) {
          final int index = i;
          FrameLayout cell = new FrameLayout(context);
          View dot = new View(context);
          GradientDrawable d = new GradientDrawable();
          d.setShape(GradientDrawable.OVAL);
          d.setColor(palette[i]);
          if (Math.abs(f.colorIndex) % palette.length == i) d.setStroke(Screen.dp(3f), Theme.fillingColor()); // a gap ring inside the outer one
          dot.setBackground(d);
          FrameLayout ringBox = new FrameLayout(context); // a true circle, whatever the cell's width
          if (Math.abs(f.colorIndex) % palette.length == i) {
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setStroke(Screen.dp(2f), palette[i]);
            ringBox.setBackground(ring);
          }
          ringBox.addView(dot, new FrameLayout.LayoutParams(Screen.dp(36f), Screen.dp(36f), Gravity.CENTER));
          cell.addView(ringBox, new FrameLayout.LayoutParams(Screen.dp(44f), Screen.dp(44f), Gravity.CENTER));
          Views.setClickable(cell);
          cell.setOnClickListener(v -> {
            Tgx101SavedStore.get(tdlib).setFolderColor(f.id, index);
            h[0].dismiss();
            forceTiles();
          });
          LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, Screen.dp(46f), 1f);
          row.addView(cell, cp);
        }
        body.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      }
    });
  }

  private void forceTiles () {
    tilesShown = null;
    buildTiles();
  }

  /** the copying of a shared folder moved on: its tile shows «copying 17/48» */
  void refreshShareProgress () {
    if (tiles == null || isDestroyed()) return;
    forceTiles();
  }

  // Pinned and recent

  private String pinnedShown;

  private void loadPinned () {
    if (!Tgx101SavedStore.get(tdlib).showPinned) {
      pinnedSection.setVisibility(View.GONE);
      return;
    }
    Tgx101SavedKit.search(tdlib, new TdApi.SearchMessagesFilterPinned(), null, 12, messages -> {
      if (isDestroyed()) return;
      StringBuilder sig = new StringBuilder();
      for (TdApi.Message m : messages) sig.append(m.id).append(':').append(m.editDate).append(',');
      if (!sig.toString().equals(pinnedShown)) {
        pinnedShown = sig.toString();
        pinnedRow.removeAllViews();
        for (TdApi.Message m : messages) pinnedRow.addView(pinnedCard(m));
      }
      pinnedSection.setVisibility(messages.isEmpty() ? View.GONE : View.VISIBLE);
    });
  }

  private View pinnedCard (TdApi.Message m) {
    LinearLayout c = new LinearLayout(context);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setBackground(card(Theme.fillingColor(), 14f));
    if (android.os.Build.VERSION.SDK_INT >= 21) c.setClipToOutline(true); // the picture keeps the card's round corners
    Views.setClickable(c);
    c.setOnClickListener(v -> Tgx101SavedKit.openInFeed(this, m));
    int kind = Tgx101SavedKit.kindOf(m);
    if (Tgx101SavedKit.thumbFile(m) != null || Tgx101SavedKit.minithumb(m) != null) {
      ImageView img = new ImageView(context);
      img.setScaleType(ImageView.ScaleType.CENTER_CROP);
      img.setBackgroundColor(Theme.backgroundColor());
      Tgx101SavedKit.loadThumb(tdlib, img, m, Screen.dp(80f));
      c.addView(img, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(64f)));
    }
    LinearLayout body = new LinearLayout(context);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(Screen.dp(12f), Screen.dp(8f), Screen.dp(12f), Screen.dp(10f));
    if (!Tgx101SavedKit.kindLabel(kind).equals(Tgx101SavedKit.title(m))) { // «Видео / Видео» for a video with no caption
      TextView k = text(context, 12f, Theme.textDecentColor(), false);
      k.setText(Tgx101SavedKit.kindLabel(kind));
      body.addView(k);
    }
    TextView t = text(context, 14f, Theme.textAccentColor(), false);
    t.setText(Tgx101SavedKit.title(m));
    t.setMaxLines(c.getChildCount() > 0 ? 1 : 3);
    t.setEllipsize(TextUtils.TruncateAt.END);
    body.addView(t);
    c.addView(body);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Screen.dp(150f), Screen.dp(118f));
    p.rightMargin = Screen.dp(8f);
    c.setLayoutParams(p);
    if (android.os.Build.VERSION.SDK_INT >= 21) {
      c.setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Screen.dp(14f));
        }
      });
    }
    return c;
  }

  private String recentShown;

  private void loadRecent () {
    Tgx101SavedKit.history(tdlib, 12, messages -> {
      if (isDestroyed()) return;
      int n = Math.min(12, messages.size());
      StringBuilder sig = new StringBuilder();
      for (int i = 0; i < n; i++) {
        TdApi.Message m = messages.get(i);
        sig.append(m.id).append(':').append(m.editDate).append(',');
      }
      if (sig.toString().equals(recentShown) && recentList.getChildCount() > 0) { // the same: nothing redrawn
        for (RecentRow r : recentRows) {
          applyTargets(r.swipe); // the last folder / tag on the buttons
          r.holder.bind(this, r.message, selected.contains(r.message.id), !selected.isEmpty()); // a renamed folder, a new tag
        }
        return;
      }
      recentShown = sig.toString();
      recentList.removeAllViews();
      recentRows.clear();
      if (n == 0) {
        TextView empty = text(context, 14f, Theme.textDecentColor(), false);
        empty.setText(Lang.getString(R.string.Tgx101SavedRecentEmpty));
        empty.setPadding(Screen.dp(14f), Screen.dp(14f), Screen.dp(14f), Screen.dp(14f));
        recentList.addView(empty);
        return;
      }
      // approved mockup «Время в Недавнем», 1: the rows have no time — «Today», «Yesterday», a date above them
      String lastDay = null;
      for (int i = 0; i < n; i++) {
        String day = dayLabel(messages.get(i).date);
        if (!day.equals(lastDay)) {
          lastDay = day;
          TextView h = text(context, 12f, Theme.textDecentColor(), true);
          h.setText(day);
          h.setPadding(Screen.dp(14f), Screen.dp(i == 0 ? 10f : 12f), Screen.dp(14f), Screen.dp(2f));
          recentList.addView(h);
        } else if (i > 0) {
          View sep = new View(context);
          sep.setBackgroundColor(Theme.separatorColor());
          LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Screen.dp(.5f)));
          sp.leftMargin = Screen.dp(66f);
          recentList.addView(sep, sp);
        }
        recentList.addView(recentRow(messages.get(i)));
      }
    });
  }

  private static String dayLabel (int date) {
    java.util.Calendar c = java.util.Calendar.getInstance();
    c.setTimeInMillis(date * 1000L);
    java.util.Calendar today = java.util.Calendar.getInstance();
    if (c.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) && c.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)) return Lang.getString(R.string.Today);
    today.add(java.util.Calendar.DAY_OF_MONTH, -1);
    if (c.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) && c.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)) return Lang.getString(R.string.Yesterday);
    boolean thisYear = c.get(java.util.Calendar.YEAR) == java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
    return new java.text.SimpleDateFormat(thisYear ? "d MMMM" : "d MMMM yyyy", Lang.locale()).format(c.getTime());
  }

  // «Recent»: swipes and selecting (approved mockup «Недавнее», Б–Г)

  private View recentRow (TdApi.Message m) {
    Tgx101SavedListController.RowHolder holder = new Tgx101SavedListController.RowHolder(context);
    holder.withDate = true; // the day is in the header above, and each row has its date in digits too (user 2026-10-16)
    holder.bind(this, m, selected.contains(m.id), !selected.isEmpty());
    // opaque underlay: the swipe buttons lie under the row, and a refresh resets the row's own background (test
    // 2026-10-16 with the keyboard up: the buttons showed through the text)
    FrameLayout face = new FrameLayout(context);
    face.setBackgroundColor(Theme.fillingColor());
    face.addView(holder.itemView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    final RecentRow[] self = new RecentRow[1];
    Tgx101SwipeRow swipe = new Tgx101SwipeRow(context, face, new Tgx101SwipeRow.Callback() {
      @Override
      public void onFolder (boolean longPress) {
        Tgx101SavedActions.swipeFolder(Tgx101SavedHomeController.this, m, longPress, Tgx101SavedHomeController.this::reloadSoon);
      }

      @Override
      public void onTag (boolean longPress) {
        Tgx101SavedActions.swipeTag(Tgx101SavedHomeController.this, m, longPress, Tgx101SavedHomeController.this::reloadSoon);
      }

      @Override
      public void onMore () {
        Tgx101SavedActions.more(Tgx101SavedHomeController.this, m, new Tgx101SavedActions.MoreHost() {
          @Override
          public void startSelection (TdApi.Message message) {
            selected.add(message.id);
            updateSelection();
          }

          @Override
          public void changed () {
            reloadSoon();
          }
        });
      }

      @Override
      public void onSelect () {
        toggleSelected(m);
      }
    });
    swipe.setSelectEnabled(false); // a finger to the right goes back to the chats (user 2026-10-16); selecting is by holding
    holder.itemView.setOnClickListener(v -> {
      if (!selected.isEmpty()) toggleSelected(m);
      else Tgx101SavedKit.openInFeed(this, m);
    });
    holder.itemView.setOnLongClickListener(v -> {
      int index = recentRows.indexOf(self[0]);
      if (index < 0) return false;
      UI.forceVibrate(v, false);
      recentList.beginDrag(index);
      return true;
    });
    self[0] = new RecentRow(m, holder, swipe);
    recentRows.add(self[0]);
    applyTargets(swipe);
    // (user 2026-10-17 «убрал бы свайпы на недавних — будут мешать»): the rows don't slide — a swipe either way is the
    // screen's «back»; a record's actions are by holding it (selection)
    swipe.setSwipeEnabled(false);
    return swipe;
  }

  private void applyTargets (Tgx101SwipeRow swipe) {
    Tgx101SavedActions.applySwipeTargets(tdlib, swipe);
  }

  private static java.util.List<TdApi.Message> one (TdApi.Message m) {
    java.util.List<TdApi.Message> list = new java.util.ArrayList<>();
    list.add(m);
    return list;
  }

  private void toggleSelected (TdApi.Message m) {
    if (!selected.remove(m.id)) selected.add(m.id);
    updateSelection();
  }

  private java.util.List<TdApi.Message> selectedMessages () {
    java.util.List<TdApi.Message> out = new java.util.ArrayList<>();
    for (RecentRow r : recentRows) if (selected.contains(r.message.id)) out.add(r.message);
    return out;
  }

  private void updateSelection () {
    boolean on = !selected.isEmpty();
    for (RecentRow r : recentRows) {
      r.holder.bind(this, r.message, selected.contains(r.message.id), on);
      r.swipe.setSwipeEnabled(false);
    }
    selectionTitle.setText(Lang.getString(R.string.Tgx101SavedSelected, selected.size()));
    if (on) selectionPanel.update();
    boolean shown = selectionTop.getVisibility() == View.VISIBLE;
    if (on && !shown) {
      org.thunderdog.challegram.tool.Keyboard.hide(noteField);
      selectionTop.setVisibility(View.VISIBLE);
      // the bar lies over the top of the list: a selected row under it comes down into view
      for (RecentRow r : recentRows) {
        if (!selected.contains(r.message.id)) continue;
        int rowTop = recentList.getTop() + r.swipe.getTop() - scroll.getScrollY();
        int hidden = Screen.dp(64f) - rowTop;
        if (hidden > 0) scroll.smoothScrollBy(0, -hidden);
        break;
      }
      selectionTop.setTranslationY(-Screen.dp(64f));
      selectionTop.animate().translationY(0f).setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
      noteBar.setVisibility(View.GONE);
      selectionPanel.setVisibility(View.VISIBLE);
      selectionPanel.setTranslationY(Screen.dp(80f));
      selectionPanel.animate().translationY(0f).setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    } else if (!on && shown) {
      selectionTop.animate().translationY(-selectionTop.getHeight()).setDuration(160).withEndAction(() -> selectionTop.setVisibility(View.GONE)).start();
      selectionPanel.animate().translationY(selectionPanel.getHeight()).setDuration(160).withEndAction(() -> {
        selectionPanel.setVisibility(View.GONE);
        noteBar.setVisibility(View.VISIBLE);
      }).start();
    }
  }

  private void clearSelection () {
    if (selected.isEmpty()) return;
    selected.clear();
    updateSelection();
  }

  private void afterSelectionAction () {
    clearSelection();
    reloadSoon();
  }

  private void buildSelectionBars (Context context, FrameLayout root, LinearLayout outer) {
    selectionTop = new LinearLayout(context);
    selectionTop.setOrientation(LinearLayout.HORIZONTAL);
    selectionTop.setGravity(Gravity.CENTER_VERTICAL);
    selectionTop.setBackgroundColor(Theme.fillingColor());
    selectionTop.setPadding(Screen.dp(4f), 0, Screen.dp(8f), 0);
    if (android.os.Build.VERSION.SDK_INT >= 21) selectionTop.setElevation(Screen.dp(4f));
    selectionTop.setClickable(true);
    ImageView close = new ImageView(context);
    close.setImageResource(R.drawable.baseline_close_24);
    close.setColorFilter(Theme.getColor(ColorId.icon));
    close.setScaleType(ImageView.ScaleType.CENTER);
    close.setContentDescription(Lang.getString(R.string.Cancel));
    Views.setClickable(close);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(close, Screen.dp(24f), this);
    close.setOnClickListener(v -> clearSelection());
    selectionTop.addView(close, new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));
    selectionTitle = text(context, 16f, Theme.textAccentColor(), true);
    selectionTop.addView(selectionTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    TextView all = text(context, 14f, accent(), false);
    all.setText(Lang.getString(R.string.Tgx101SavedSelectAll));
    all.setPadding(Screen.dp(10f), Screen.dp(12f), Screen.dp(10f), Screen.dp(12f));
    Views.setClickable(all);
    all.setOnClickListener(v -> {
      for (RecentRow r : recentRows) selected.add(r.message.id);
      updateSelection();
    });
    selectionTop.addView(all);
    selectionTop.setVisibility(View.GONE);
    root.addView(selectionTop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.TOP));

    selectionPanel = new Tgx101SelectionPanel(context, this, new Tgx101SelectionPanel.Host() {
      @Override
      public java.util.List<TdApi.Message> selectedMessages () {
        return Tgx101SavedHomeController.this.selectedMessages();
      }

      @Override
      public void afterAction (boolean clear) {
        if (clear) afterSelectionAction();
        else reloadSoon();
      }
    });
    selectionPanel.setPadding(0, Screen.dp(10f), 0, Screen.dp(12f)); // in place of the note field: no system bar under it
    selectionPanel.setVisibility(View.GONE);
    outer.addView(selectionPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    // (user 2026-10-16 «жест назад должен всегда сначала закрывать клавиатуру») — the keyboard first, then the screen
    if (noteField != null && getKeyboardState()) {
      if (commit) {
        org.thunderdog.challegram.tool.Keyboard.hide(noteField);
        noteField.clearFocus();
      }
      return true;
    }
    if (emojiShown) {
      if (commit) closeEmoji(false); // «back» first closes the emoji panel
      return true;
    }
    if (!selected.isEmpty()) {
      if (commit) clearSelection(); // «back» first leaves the selection
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  /**
   * TGx101 (user 2026-10-15): the home screen's own gestures — pulled down at the top it opens the search (a tick when
   * far enough, like the chat list); a sideways swipe outside the rows and the scrolling strips opens the kinds
   */
  private final class HomeScroll extends ScrollView {
    private float downX, downY;
    private boolean pulling, swiping, armed;
    // (user 2026-10-17 «при свайпе по недавним, где нет жеста назад, случайно открываются заметки»): a finger gone sideways
    // is no press — the rows get a cancel. Not over the rows that slide sideways themselves
    private boolean sideways, overSideways;
    private final int slop;
    private final android.graphics.Rect hit = new android.graphics.Rect();
    private final int[] at = new int[2];

    HomeScroll (Context context) {
      super(context);
      slop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    protected void onScrollChanged (int l, int t, int oldl, int oldt) {
      super.onScrollChanged(l, t, oldl, oldt);
      if (Math.abs(t - oldt) > 2) Tgx101SwipeRow.closeOpen(); // scrolling closes an open row
    }

    private boolean inside (View v, MotionEvent e) {
      if (v == null || v.getVisibility() != View.VISIBLE) return false;
      v.getLocationOnScreen(at);
      hit.set(at[0], at[1], at[0] + v.getWidth(), at[1] + v.getHeight());
      return hit.contains((int) e.getRawX(), (int) e.getRawY());
    }

    @Override
    public boolean onInterceptTouchEvent (MotionEvent e) {
      if (!selected.isEmpty()) return super.onInterceptTouchEvent(e);
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downX = e.getRawX();
          downY = e.getRawY();
          pulling = swiping = armed = sideways = false;
          overSideways = inside(chipsScroll, e) || (pinnedSection != null && pinnedSection.getVisibility() == View.VISIBLE && inside(pinnedScroll, e));
          break;
        case MotionEvent.ACTION_MOVE: {
          float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
          if (getScrollY() <= 0 && dy > slop && dy > Math.abs(dx)) { // (user's video 2026-10-17: a slant start missed it)
            pulling = true;
            downY = e.getRawY() - slop; // no jump: the pull starts from here
            return true;
          }
          // (user 2026-10-16) no sideways swipes through the kinds here: the pull-down panel has them
          if (!overSideways && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
            sideways = true;
            return true;
          }
          break;
        }
      }
      return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (sideways) { // eaten: nothing opens, nothing scrolls
        if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) sideways = false;
        return true;
      }
      if (!pulling && !swiping && selected.isEmpty() && e.getActionMasked() == MotionEvent.ACTION_MOVE && getScrollY() <= 0) {
        // the list took the touch first (a child let it go, or the start was slanted) — at the top a pull down is
        // still the search (user's video 2026-10-17: «свайп поиска срабатывает не всегда»)
        float ddy = e.getRawY() - downY, ddx = e.getRawX() - downX;
        if (ddy > slop && ddy > Math.abs(ddx)) {
          pulling = true;
          armed = false;
          downY = e.getRawY() - slop;
          getParent().requestDisallowInterceptTouchEvent(true);
        }
      }
      if (!pulling && !swiping) return super.onTouchEvent(e);
      float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
      float threshold = Screen.dp(64f);
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_MOVE:
          if (pulling) {
            float pull = Math.max(0f, dy) * .5f;
            column.setTranslationY(pull);
            pullIcon.setAlpha(Math.min(1f, pull / threshold));
            float sc = .6f + .4f * Math.min(1f, pull / threshold);
            pullIcon.setScaleX(sc);
            pullIcon.setScaleY(sc);
            boolean far = pull >= threshold;
            if (far != armed) {
              armed = far;
              UI.forceVibrate(this, false);
            }
          } else {
            column.setTranslationX(dx * .25f);
          }
          return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
          if (pulling) {
            if (up && armed) Tgx101SavedListController.openSearchReach(Tgx101SavedHomeController.this);
            column.animate().translationY(0f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
            pullIcon.animate().alpha(0f).setDuration(180).start();
          } else {
            column.animate().translationX(0f).setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
            if (up && Math.abs(dx) > Screen.dp(80f)) openKindSwipe(dx < 0);
          }
          pulling = swiping = false;
          return true;
        }
      }
      return true;
    }
  }

  /** a swipe left opens the first kind that has something, right — the last; inside, swipes go on through the kinds */
  private void openKindSwipe (boolean forward) {
    int[] counts = lastCounts.get(tdlib.id());
    int pick = -1;
    for (int i = 0; i < KIND_FILTER_KINDS.length; i++) {
      int index = forward ? i : KIND_FILTER_KINDS.length - 1 - i;
      if (counts == null || counts[index] != 0) {
        pick = KIND_FILTER_KINDS[index];
        break;
      }
    }
    if (pick != -1) Tgx101SavedListController.openKind(this, pick);
  }

  /**
   * The «Recent» card: after a long press the finger selects every row it passes without lifting (user 2026-10-15:
   * smoother than in chats); near the edges the screen scrolls by itself
   */
  private final class DragList extends LinearLayout {
    private boolean dragging;
    private int anchor = -1, last = -1;
    private boolean selecting;
    private java.util.Set<Long> before;
    private float lastRawY;
    private final int[] location = new int[2], scrollLocation = new int[2];

    DragList (Context context) {
      super(context);
    }

    void beginDrag (int index) {
      if (index < 0 || index >= recentRows.size()) return;
      Tgx101SwipeRow.closeOpen();
      dragging = true;
      anchor = last = index;
      before = new java.util.HashSet<>(selected);
      long id = recentRows.get(index).message.id;
      selecting = !selected.contains(id);
      if (selecting) selected.add(id); else selected.remove(id);
      getParent().requestDisallowInterceptTouchEvent(true);
      updateSelection();
    }

    @Override
    public boolean onInterceptTouchEvent (MotionEvent e) {
      return dragging || super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (!dragging) return super.onTouchEvent(e);
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_MOVE:
          lastRawY = e.getRawY();
          follow();
          autoScroll();
          return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          dragging = false;
          removeCallbacks(scroller);
          getParent().requestDisallowInterceptTouchEvent(false);
          return true;
      }
      return true;
    }

    private int indexAt (float rawY) {
      getLocationOnScreen(location);
      float y = rawY - location[1];
      int index = -1;
      for (int i = 0; i < recentRows.size(); i++) {
        View v = recentRows.get(i).swipe;
        if (y >= v.getTop()) index = i;
      }
      return Math.max(0, index);
    }

    private void follow () {
      int index = indexAt(lastRawY);
      if (index == last) return;
      last = index;
      int from = Math.min(anchor, index), to = Math.max(anchor, index);
      boolean changed = false;
      for (int i = 0; i < recentRows.size(); i++) {
        long id = recentRows.get(i).message.id;
        boolean want = i >= from && i <= to ? selecting : before.contains(id);
        if (want != selected.contains(id)) {
          changed = true;
          if (want) selected.add(id); else selected.remove(id);
        }
      }
      if (changed) {
        UI.forceVibrate(this, false);
        updateSelection();
      }
    }

    private final Runnable scroller = new Runnable() {
      @Override
      public void run () {
        if (!dragging) return;
        int step = scrollStep();
        if (step == 0) return;
        scroll.scrollBy(0, step);
        follow();
        postDelayed(this, 16);
      }
    };

    private int scrollStep () {
      scroll.getLocationOnScreen(scrollLocation);
      float top = scrollLocation[1], bottom = top + scroll.getHeight(), edge = Screen.dp(64f);
      if (lastRawY < top + edge) return -Math.round(Screen.dp(14f) * Math.min(1f, (top + edge - lastRawY) / edge));
      if (lastRawY > bottom - edge) return Math.round(Screen.dp(14f) * Math.min(1f, (lastRawY - (bottom - edge)) / edge));
      return 0;
    }

    private void autoScroll () {
      removeCallbacks(scroller);
      if (scrollStep() != 0) post(scroller);
    }
  }

  // Note

  /** The typed note with its formatting and custom emoji (links and #hashtags are found by Telegram), or null */
  @Nullable
  private TdApi.FormattedText noteText () {
    TdApi.FormattedText text = noteField.getOutputText(true);
    if (text == null || text.text == null || text.text.trim().isEmpty()) return null;
    return text;
  }

  private void saveNote () {
    TdApi.FormattedText note = noteText();
    if (note == null) return;
    sendOff();
    Tgx101SavedActions.sendNote(this, note, null, saved -> afterNoteSaved()); // the check on the button says it (no toast)
  }

  private boolean checkShown;

  /**
   * TGx101 (approved mockup «Отправка заметки», 3; user 2026-10-16 «текст просто резко исчезает»): the text shrinks into
   * the send button, the button turns into a check for a moment, then back to what it is for the empty field
   */
  private void sendOff () {
    CharSequence text = noteField.getText() != null ? noteField.getText().toString() : "";
    noteField.setText("");
    if (rootView == null || sendButton == null || text.length() == 0 || org.thunderdog.challegram.unsorted.Settings.instance().needReduceMotion()) {
      showCheck();
      return;
    }
    int[] rootAt = new int[2], fieldAt = new int[2], sendAt = new int[2];
    rootView.getLocationInWindow(rootAt);
    noteField.getLocationInWindow(fieldAt);
    sendButton.getLocationInWindow(sendAt);
    TextView flying = text(context, 16f, Theme.textAccentColor(), false);
    flying.setText(text);
    flying.setSingleLine(true);
    flying.setEllipsize(TextUtils.TruncateAt.END);
    flying.setGravity(Gravity.CENTER_VERTICAL);
    flying.setPadding(noteField.getPaddingLeft(), 0, noteField.getPaddingRight(), 0);
    FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(noteField.getWidth(), noteField.getHeight(), Gravity.TOP | Gravity.LEFT);
    fp.leftMargin = fieldAt[0] - rootAt[0];
    fp.topMargin = fieldAt[1] - rootAt[1];
    rootView.addView(flying, fp);
    if (android.os.Build.VERSION.SDK_INT >= 21) flying.setElevation(Screen.dp(5f)); // over the field capsule
    float sendCenter = sendAt[0] - rootAt[0] + sendButton.getWidth() / 2f;
    float textRight = fp.leftMargin + noteField.getWidth();
    flying.setPivotX(noteField.getWidth());
    flying.setPivotY(noteField.getHeight() / 2f);
    flying.animate().scaleX(.15f).scaleY(.15f).translationX(sendCenter - textRight).alpha(0f).setDuration(280)
      .setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f))
      .withEndAction(() -> {
        rootView.removeView(flying);
        showCheck();
      }).start();
  }

  /** the send button: a check for a moment, a small pulse, then the field's own icon again */
  private void showCheck () {
    if (sendButton == null) return;
    checkShown = true;
    sendButton.setImageResource(R.drawable.baseline_check_24);
    sendButton.animate().cancel();
    sendButton.setScaleX(.8f);
    sendButton.setScaleY(.8f);
    sendButton.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator(2.5f)).start();
    UI.hapticVibrate(sendButton, false);
    sendButton.postDelayed(() -> {
      checkShown = false;
      updateSendIcon();
    }, 750);
  }

  private void afterNoteSaved () {
    if (isDestroyed()) return;
    loadRecent();
    buildChips();
  }

  // Voice notes (user 2026-10-16 «в поле ввода нет записи голосового»): the field empty — the button is a microphone;
  // held, it records; released, the voice note goes to Saved; slid left past the hint, it's thrown away

  private ImageView sendButton;
  private boolean recording, recordCanceled;
  private float recordDownX;
  private long recordStart;
  private TextView recordLabel;

  private void updateSendIcon () {
    if (sendButton == null || recording || checkShown) return;
    boolean empty = noteField.getText().toString().trim().isEmpty();
    sendButton.setImageResource(empty ? R.drawable.baseline_mic_24 : R.drawable.baseline_send_24);
  }

  private boolean onVoiceTouch (View v, MotionEvent e) {
    boolean empty = noteField.getText().toString().trim().isEmpty();
    if (!empty && !recording) return false; // a typed note: the usual tap / hold
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        recordDownX = e.getRawX();
        if (android.os.Build.VERSION.SDK_INT >= 23 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
          context.requestMicPermissionForCall(null);
          return true;
        }
        startRecording();
        return true;
      case MotionEvent.ACTION_MOVE:
        if (recording && !recordCanceled && recordDownX - e.getRawX() > Screen.dp(110f)) {
          recordCanceled = true;
          UI.forceVibrate(v, false);
          stopRecording(false);
        }
        return true;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (recording) stopRecording(e.getActionMasked() == MotionEvent.ACTION_UP && System.currentTimeMillis() - recordStart > 700);
        return true;
    }
    return true;
  }

  private void startRecording () {
    recording = true;
    recordCanceled = false;
    recordStart = System.currentTimeMillis();
    UI.forceVibrate(sendButton, false);
    sendButton.setColorFilter(0xffe5484d);
    if (recordLabel == null) {
      recordLabel = text(context, 15f, Theme.textAccentColor(), false);
      recordLabel.setGravity(Gravity.CENTER_VERTICAL);
      recordLabel.setPadding(Screen.dp(4f), 0, Screen.dp(4f), 0);
      ((ViewGroup) noteField.getParent()).addView(recordLabel, ((ViewGroup) noteField.getParent()).indexOfChild(noteField), new LinearLayout.LayoutParams(0, Screen.dp(44f), 1f));
    }
    noteField.setVisibility(View.GONE);
    recordLabel.setVisibility(View.VISIBLE);
    recordTicker.run();
    org.thunderdog.challegram.helper.Recorder.instance().record(tdlib, false, new org.thunderdog.challegram.helper.Recorder.Listener() {
      @Override
      public void onAmplitude (float amplitude) { }

      @Override
      public void onFail () {
        UI.post(() -> {
          UI.showToast(R.string.Tgx101SavedVoiceFailed, android.widget.Toast.LENGTH_SHORT);
          resetRecordingUi();
        });
      }

      @Override
      public void onSave (org.thunderdog.challegram.telegram.Tdlib.Generation generation, int duration, byte[] waveform) {
        UI.post(() -> {
          org.thunderdog.challegram.data.TGRecord record = new org.thunderdog.challegram.data.TGRecord(tdlib, generation, duration, waveform);
          Tgx101SavedStore.suppressBanner(15000);
          tdlib.send(new TdApi.SendMessage(tdlib.selfChatId(), null, null, null, null,
            new TdApi.InputMessageVoiceNote(new TdApi.InputVoiceNote(record.toInputFile(), record.getDuration(), record.getWaveform()), null, null)), (sent, error) -> UI.post(() -> {
            if (error != null) UI.showError(error);
            else if (!isDestroyed()) UI.post(Tgx101SavedHomeController.this::afterNoteSaved, 600);
          }));
        });
      }
    });
  }

  private final Runnable recordTicker = new Runnable() {
    @Override
    public void run () {
      if (!recording || recordLabel == null) return;
      long sec = (System.currentTimeMillis() - recordStart) / 1000;
      recordLabel.setText("● " + String.format(java.util.Locale.ROOT, "%d:%02d", sec / 60, sec % 60) + "   " + Lang.getString(R.string.Tgx101SavedVoiceCancelHint));
      recordLabel.postDelayed(this, 500);
    }
  };

  private void stopRecording (boolean send) {
    if (!recording) return;
    if (send) org.thunderdog.challegram.helper.Recorder.instance().save(false);
    else org.thunderdog.challegram.helper.Recorder.instance().cancel();
    resetRecordingUi();
  }

  private void resetRecordingUi () {
    recording = false;
    if (recordLabel != null) recordLabel.setVisibility(View.GONE);
    noteField.setVisibility(View.VISIBLE);
    sendButton.setColorFilter(accent());
    updateSendIcon();
  }

  // Emoji keyboard (the chat's own, as in the share sheet)

  private void openEmoji () {
    if (emojiShown) return;
    if (keyboardFrame == null) {
      keyboardFrame = new org.thunderdog.challegram.widget.KeyboardFrameLayout(context);
      keyboardFrame.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      keyboardFrame.setParentView(outerView, rootView, rootView);
      keyboardFrame.contentView.emojiLayout.initWithMediasEnabled(this, true, this, this, false);
      // (test 2026-10-16: crashed — the panel keeps the params its init gave it unless set again, as the share sheet does)
      keyboardFrame.contentView.emojiLayout.setLayoutParams(me.vkryl.android.widget.FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      outerView.addView(keyboardFrame);
    }
    keyboardFrame.setVisible(true);
    emojiWasKeyboard = getKeyboardState();
    emojiShown = true;
    emojiButton.setImageResource(R.drawable.baseline_keyboard_24);
    if (emojiWasKeyboard) keyboardFrame.hideKeyboard(noteField);
  }

  private void closeEmoji (boolean showKeyboard) {
    if (!emojiShown) return;
    emojiShown = false;
    if (keyboardFrame != null) {
      keyboardFrame.setVisible(false);
      if (showKeyboard) keyboardFrame.showKeyboard(noteField);
    }
    emojiButton.setImageResource(R.drawable.deproko_baseline_insert_emoticon_26);
  }

  @Override
  public boolean onKeyboardStateChanged (boolean visible) {
    if (visible && emojiShown) closeEmoji(false); // the system keyboard took its place
    boolean result = super.onKeyboardStateChanged(visible);
    if (keyboardFrame != null) keyboardFrame.onKeyboardStateChanged(visible);
    if (storageSwitch != null) storageSwitch.setKeyboardShown(visible); // not needed while typing (user 2026-10-16)
    return result;
  }

  @Override
  public void onEnterEmoji (String emoji) {
    noteField.onEmojiSelected(emoji);
  }

  @Override
  public void onEnterCustomEmoji (org.thunderdog.challegram.component.sticker.TGStickerObj sticker) {
    noteField.onCustomEmojiSelected(sticker);
  }

  @Override
  public void onDeleteEmoji () {
    if (noteField.length() > 0) noteField.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL));
  }

  @Override
  public boolean isEmojiInputEmpty () {
    return noteField.getText().length() == 0;
  }

  @Override
  public boolean onSendSticker (@Nullable View view, org.thunderdog.challegram.component.sticker.TGStickerObj sticker, TdApi.MessageSendOptions sendOptions) {
    if (sticker == null || sticker.getSticker() == null) return false;
    sendContent(new TdApi.InputMessageSticker(new TdApi.InputSticker(new TdApi.InputFileId(sticker.getSticker().sticker.id), null, 0, 0), sticker.getAllEmoji()));
    return true;
  }

  @Override
  public boolean onSendGIF (@Nullable View view, TdApi.Animation animation) {
    sendContent(new TdApi.InputMessageAnimation(new TdApi.InputAnimation(new TdApi.InputFileId(animation.animation.id), null, null, animation.duration, animation.width, animation.height), null, false, false));
    return true;
  }

  @Override
  public long getOutputChatId () {
    return tdlib.selfChatId();
  }

  /** a sticker or a GIF from the panel goes straight into Saved */
  private void sendContent (TdApi.InputMessageContent content) {
    Tgx101SavedStore.suppressBanner(15000);
    tdlib.send(new TdApi.SendMessage(tdlib.selfChatId(), null, null, null, null, content), (sent, error) -> UI.post(() -> {
      if (error != null) UI.showError(error);
      else if (!isDestroyed()) UI.post(this::afterNoteSaved, 400);
    }));
  }

  @Override
  public int[] displayBaseViewWithAnchor (org.thunderdog.challegram.component.chat.EmojiToneHelper context, View anchorView, View viewToDisplay, int viewWidth, int viewHeight, int horizontalMargin, int horizontalOffset, int verticalOffset) {
    return org.thunderdog.challegram.component.chat.EmojiToneHelper.defaultDisplay(context, anchorView, viewToDisplay, viewWidth, viewHeight, horizontalMargin, horizontalOffset, verticalOffset, rootView, outerView, keyboardFrame);
  }

  @Override
  public void removeView (org.thunderdog.challegram.component.chat.EmojiToneHelper context, View displayedView) {
    rootView.removeView(displayedView);
  }

  /** The chat's attachment menu: the Saved feed opens with it (it lives on the chat screen); «back» comes home */
  private void openAttach () {
    closeEmoji(false);
    org.thunderdog.challegram.tool.Keyboard.hide(noteField);
    Tgx101SavedKit.openInFeed(this, null);
    UI.post(() -> {
      ViewController<?> top = context.navigation().getCurrentStackItem();
      if (top instanceof MessagesController && ((MessagesController) top).getChatId() == tdlib.selfChatId()) {
        ((MessagesController) top).tgx101OpenAttach();
      }
    }, 450);
  }

  @Override
  public void destroy () {
    super.destroy();
    if (noteField != null) noteField.performDestroy();
  }
}
