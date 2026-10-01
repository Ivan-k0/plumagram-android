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

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.InputView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import tgx.td.Td;

/**
 * TGx101: the message text window in the middle of the screen (mockup «3E в окне»).
 * «Изменить» on an own message: the text is editable, with Style / Blocks / Edit tabs of formatting buttons and «Сохранить».
 * «Цитировать» on any message: read-only selectable text with «Скопировать», «Скопировать всё», «Отмена» and «Ответить цитатой».
 * The window grows with the text; a long text scrolls inside it.
 */
public final class Tgx101TextEditor {
  private Tgx101TextEditor () { }

  /** Text and caption edits only; anything else (rich posts etc.) keeps the stock editing in the input field. */
  public static boolean canEdit (TdApi.Message message) {
    if (message == null || !Settings.instance().useTgx101TextEditor()) return false;
    switch (message.content.getConstructor()) {
      case TdApi.MessageText.CONSTRUCTOR:
      case TdApi.MessageAnimatedEmoji.CONSTRUCTOR:
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessageAudio.CONSTRUCTOR:
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
      case TdApi.MessageDocument.CONSTRUCTOR:
      case TdApi.MessageAnimation.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  // Edit

  public static void showEdit (MessagesController controller, Tdlib tdlib, TdApi.Message message) {
    Context context = controller.context();
    TdApi.FormattedText text = Td.textOrCaption(message.content);
    TdApi.FormattedText pending = tdlib.getPendingFormattedText(message.chatId, message.id);
    if (pending != null) text = pending;
    if (text == null) text = new TdApi.FormattedText("", new TdApi.TextEntity[0]);
    final boolean markdown = Settings.instance().getNewSetting(Settings.SETTING_FLAG_EDIT_MARKDOWN);

    LinearLayout root = newRoot(context);
    TextView counter = addHeader(root, Lang.getString(R.string.Tgx101EditorEdit));

    InputView input = new InputView(context, tdlib, controller);
    input.setTgx101FallbackController(controller);
    input.setBackground(null);
    input.setPadding(Screen.dp(18f), Screen.dp(4f), Screen.dp(14f), Screen.dp(8f));
    input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
    input.setTextColor(Theme.textAccentColor());
    input.setHighlightColor(ColorUtils.alphaColor(.25f, Theme.getColor(ColorId.textLink)));
    input.setMinimumHeight(Screen.dp(40f));
    input.setMaxLines(Integer.MAX_VALUE);
    input.setMaxHeight(textMaxHeight());
    setupScrollbar(input);
    input.setMaxCodePointCount(message.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR || message.content.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR ? tdlib.maxMessageTextLength() : tdlib.maxCaptionLength());
    input.setInput(markdown ? TD.toMarkdown(text) : TD.toCharSequence(text), true, false);
    root.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    updateCounter(counter, input.getText());
    input.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
      @Override public void onTextChanged (CharSequence s, int start, int before, int count) { }
      @Override public void afterTextChanged (Editable s) { updateCounter(counter, s); }
    });

    LinearLayout panel = newPanel(context);
    root.addView(panel);

    final Card[] dialog = new Card[1];
    LinearLayout grid = new LinearLayout(context);
    grid.setOrientation(LinearLayout.HORIZONTAL);
    Runnable[] showTab = new Runnable[3];
    TextView[] tabs = new TextView[3];
    LinearLayout segment = new LinearLayout(context);
    segment.setOrientation(LinearLayout.HORIZONTAL);
    segment.setPadding(Screen.dp(3f), Screen.dp(3f), Screen.dp(3f), Screen.dp(3f));
    segment.setBackground(rounded(ColorUtils.alphaColor(.08f, Theme.textAccentColor()), 10f));
    int[] tabNames = {R.string.Tgx101EditorTabStyle, R.string.Tgx101EditorTabBlocks, R.string.Tgx101EditorTabEdit};
    for (int i = 0; i < 3; i++) {
      final int index = i;
      TextView tab = new TextView(context);
      tab.setText(Lang.getString(tabNames[i]));
      tab.setGravity(Gravity.CENTER);
      tab.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
      tab.setTypeface(Fonts.getRobotoMedium());
      tab.setPadding(0, Screen.dp(6f), 0, Screen.dp(6f));
      tab.setOnClickListener(v -> showTab[index].run());
      tabs[i] = tab;
      segment.addView(tab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }
    panel.addView(segment, matchWrap(0));
    panel.addView(grid, matchWrap(Screen.dp(8f)));

    final Object[][] style = {
      {R.drawable.baseline_format_bold_24, R.string.Tgx101EditorBold, R.id.btn_bold},
      {R.drawable.baseline_format_italic_24, R.string.Tgx101EditorItalic, R.id.btn_italic},
      {R.drawable.baseline_format_underlined_24, R.string.Tgx101EditorUnderline, R.id.btn_underline},
      {R.drawable.tgx101_format_strikethrough_24, R.string.Tgx101EditorStrike, R.id.btn_strikethrough}
    };
    final Object[][] blocks = {
      {R.drawable.baseline_format_quote_close_24, R.string.Tgx101EditorQuote, R.id.btn_quote},
      {R.drawable.baseline_code_24, R.string.Tgx101EditorMono, R.id.btn_monospace},
      {R.drawable.infanf_baseline_incognito_24, R.string.Tgx101EditorSpoiler, R.id.btn_spoiler},
      {R.drawable.baseline_link_24, R.string.Tgx101EditorLink, R.id.btn_link}
    };
    final Object[][] edit = {
      {null, R.string.Tgx101EditorPlain, R.id.btn_plain},
      {null, R.string.Tgx101EditorSelectAll, 1},
      {null, R.string.Tgx101EditorCopy, 2}
    };
    final Object[][][] groups = {style, blocks, edit};
    final Object[] plainUndo = new Object[3]; // text before «Обычный», selection start, end
    for (int i = 0; i < 3; i++) {
      final int index = i;
      showTab[i] = () -> {
        for (int t = 0; t < 3; t++) {
          boolean selected = t == index;
          tabs[t].setBackground(selected ? rounded(Theme.fillingColor(), 8f) : null);
          tabs[t].setTextColor(selected ? Theme.textAccentColor() : Theme.textDecentColor());
        }
        grid.removeAllViews();
        for (Object[] item : groups[index]) {
          final int action = (int) item[2];
          addToolButton(grid, item[0], (int) item[1], groups[index].length, v -> onFormatAction(controller, input, action, plainUndo));
        }
      };
    }
    showTab[0].run();

    addPrimaryButton(panel, Lang.getString(R.string.Tgx101EditorSave), v -> {
      TdApi.FormattedText newText = input.getOutputText(markdown);
      if (controller.tgx101SaveEditedText(message, newText)) {
        if (dialog[0] != null) dialog[0].dismiss();
      }
    });

    dialog[0] = showCard(controller, root, input::performDestroy);
    root.findViewWithTag("close").setOnClickListener(v -> dialog[0].dismiss());
    fitAboveKeyboard(root, input);
    input.requestFocus();
    input.post(() -> {
      input.requestFocus();
      org.thunderdog.challegram.tool.Keyboard.show(input);
    });
  }

  /** Whether [start, end) is fully covered by spans of this entity type */
  private static boolean isFullyStyled (android.text.Spanned text, int start, int end, TdApi.TextEntityType type) {
    Object[] spans = text.getSpans(start, end, Object.class);
    java.util.List<int[]> ranges = new java.util.ArrayList<>();
    if (spans != null) {
      for (Object span : spans) {
        if (!TD.canConvertToEntityType(span)) continue;
        TdApi.TextEntityType[] types = TD.toEntityType(span);
        if (types == null) continue;
        for (TdApi.TextEntityType t : types) {
          if (t.getConstructor() == type.getConstructor()) {
            ranges.add(new int[] {text.getSpanStart(span), text.getSpanEnd(span)});
            break;
          }
        }
      }
    }
    java.util.Collections.sort(ranges, (a, b) -> Integer.compare(a[0], b[0]));
    int covered = start;
    for (int[] range : ranges) {
      if (range[0] > covered) break;
      covered = Math.max(covered, range[1]);
      if (covered >= end) return true;
    }
    return covered >= end;
  }

  private static TdApi.TextEntityType typeOf (int action) {
    if (action == R.id.btn_bold) return new TdApi.TextEntityTypeBold();
    if (action == R.id.btn_italic) return new TdApi.TextEntityTypeItalic();
    if (action == R.id.btn_underline) return new TdApi.TextEntityTypeUnderline();
    if (action == R.id.btn_strikethrough) return new TdApi.TextEntityTypeStrikethrough();
    if (action == R.id.btn_quote) return new TdApi.TextEntityTypeBlockQuote();
    if (action == R.id.btn_monospace) return new TdApi.TextEntityTypeCode();
    if (action == R.id.btn_spoiler) return new TdApi.TextEntityTypeSpoiler();
    return null;
  }

  /** A button applies its action; pressed again on the same selection it takes the action back. */
  private static void onFormatAction (MessagesController controller, InputView input, int action, Object[] plainUndo) {
    int start = input.getSelectionStart(), end = input.getSelectionEnd();
    boolean hasSelection = start >= 0 && end > start;
    if (action == 1) { // select all / unselect
      input.requestFocus();
      if (hasSelection && start == 0 && end == input.getText().length()) {
        input.setSelection(end);
      } else {
        input.selectAll();
      }
      return;
    }
    if (action == 2) { // copy
      CharSequence all = input.getText();
      UI.copyText(hasSelection ? all.subSequence(start, end) : all, R.string.CopiedText);
      return;
    }
    if (action == R.id.btn_plain) {
      if (plainUndo[0] != null && plainUndo[0].toString().equals(input.getText().toString())) {
        // second press: the formatting comes back
        CharSequence before = (CharSequence) plainUndo[0];
        plainUndo[0] = null;
        input.setInput(before, false, true);
        input.setSelection((int) plainUndo[1], (int) plainUndo[2]);
        return;
      }
      if (!hasSelection) {
        UI.showToast(R.string.Tgx101EditorSelectFirst, android.widget.Toast.LENGTH_SHORT);
        return;
      }
      plainUndo[0] = new android.text.SpannableStringBuilder(input.getText());
      plainUndo[1] = start;
      plainUndo[2] = end;
      input.setSpan(R.id.btn_plain);
      return;
    }
    plainUndo[0] = null;
    if (!hasSelection) {
      UI.showToast(R.string.Tgx101EditorSelectFirst, android.widget.Toast.LENGTH_SHORT);
      return;
    }
    if (action == R.id.btn_link) {
      android.text.style.URLSpan[] links = input.getText().getSpans(start, end, android.text.style.URLSpan.class);
      if (links != null && links.length > 0) {
        input.removeSpan(new TdApi.TextEntityTypeTextUrl(links[0].getURL())); // second press: the link is removed
      } else {
        askLink(controller, input);
      }
      return;
    }
    TdApi.TextEntityType type = typeOf(action);
    if (type != null && isFullyStyled(input.getText(), start, end, type)) {
      // the whole selection already has this style → the second press takes it back
      input.removeSpan(type);
      input.setSelection(start, end);
    } else {
      input.setSpan(action);
    }
  }

  private static void askLink (MessagesController controller, InputView input) {
    Context context = controller.context();
    EditText url = new EditText(context);
    url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    url.setHint(Lang.getString(R.string.URL));
    url.setSingleLine(true);
    LinearLayout wrap = new LinearLayout(context);
    wrap.setPadding(Screen.dp(20f), Screen.dp(8f), Screen.dp(20f), 0);
    wrap.addView(url, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.CreateLink));
    b.setView(wrap);
    b.setNegativeButton(Lang.getString(R.string.CreateLinkCancel), (d, w) -> d.dismiss());
    b.setPositiveButton(Lang.getString(R.string.CreateLinkDone), (d, w) -> {
      String link = url.getText().toString().trim();
      if (!StringUtils.isEmpty(link)) {
        input.setSpanLink(link);
      }
    });
    controller.showAlert(b);
  }

  // Quote

  public static void showQuote (MessagesController controller, Tdlib tdlib, TGMessage message, TdApi.FormattedText formattedText) {
    if (formattedText == null || StringUtils.isEmpty(formattedText.text)) return;
    Context context = controller.context();
    LinearLayout root = newRoot(context);
    TextView counter = addHeader(root, Lang.getString(R.string.Tgx101EditorQuoteTitle));

    TextView text = new TextView(context);
    text.setText(formattedText.text);
    text.setTextIsSelectable(true);
    text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
    text.setTextColor(Theme.textAccentColor());
    text.setTypeface(Fonts.getRobotoRegular());
    text.setLineSpacing(0, 1.1f);
    text.setPadding(Screen.dp(18f), Screen.dp(4f), Screen.dp(14f), Screen.dp(10f));
    text.setMaxHeight(textMaxHeight());
    setupScrollbar(text);
    root.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    updateCounter(counter, formattedText.text);

    final Card[] dialog = new Card[1];
    LinearLayout panel = newPanel(context);
    root.addView(panel);
    LinearLayout grid = new LinearLayout(context);
    grid.setOrientation(LinearLayout.HORIZONTAL);
    panel.addView(grid, matchWrap(0));
    addToolButton(grid, R.drawable.baseline_content_copy_24, R.string.Tgx101EditorCopy, 3, v -> {
      int start = text.getSelectionStart(), end = text.getSelectionEnd();
      if (start < 0 || end <= start) {
        UI.showToast(R.string.Tgx101EditorSelectFirst, android.widget.Toast.LENGTH_SHORT);
        return;
      }
      UI.copyText(formattedText.text.substring(start, end), R.string.CopiedText);
      if (dialog[0] != null) dialog[0].dismiss();
    });
    addToolButton(grid, R.drawable.tgx101_select_all_24, R.string.Tgx101EditorCopyAll, 3, v -> {
      UI.copyText(formattedText.text, R.string.CopiedText);
      if (dialog[0] != null) dialog[0].dismiss();
    });
    addToolButton(grid, R.drawable.baseline_close_24, R.string.Cancel, 3, v -> {
      if (dialog[0] != null) dialog[0].dismiss();
    });
    addPrimaryButton(panel, Lang.getString(R.string.Tgx101EditorReplyQuote), v -> {
      int start = text.getSelectionStart(), end = text.getSelectionEnd();
      if (dialog[0] != null) dialog[0].dismiss();
      SelectTextForQuoteDialog.onReplyRequested(controller, tdlib, message, formattedText, start, end);
    });

    dialog[0] = showCard(controller, root, null);
    root.findViewWithTag("close").setOnClickListener(v -> dialog[0].dismiss());
  }

  // Views

  /**
   * The window is a layer inside the chat screen (PopupLayout), not a separate dialog window: the keyboard moves
   * from the chat input to the editor and back without hiding, so the chat doesn't jump.
   */
  private static final class Card {
    private final org.thunderdog.challegram.widget.PopupLayout popup;

    Card (org.thunderdog.challegram.widget.PopupLayout popup) {
      this.popup = popup;
    }

    void dismiss () {
      popup.hideWindow(true);
    }
  }

  private static Card showCard (MessagesController controller, LinearLayout card, @androidx.annotation.Nullable Runnable onDismiss) {
    org.thunderdog.challegram.BaseActivity activity = controller.context();
    final View previousFocus = activity.getCurrentFocus();
    final org.thunderdog.challegram.widget.PopupLayout popup = new org.thunderdog.challegram.widget.PopupLayout(activity);
    final Card result = new Card(popup);

    android.widget.FrameLayout wrap = new android.widget.FrameLayout(activity);
    wrap.setBackgroundColor(0x80000000);
    wrap.setClickable(true); // closes only with ✕ — taps around the card do nothing
    card.setClickable(true);
    card.setBackground(rounded(Theme.fillingColor(), 20f));
    card.setClipToOutline(true);
    card.setElevation(Screen.dp(12f));
    // right above the keyboard, over the (unused) chat input
    android.widget.FrameLayout.LayoutParams cardParams = new android.widget.FrameLayout.LayoutParams(Math.min(Screen.currentWidth() - Screen.dp(12f), Screen.dp(480f)), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    cardParams.bottomMargin = Screen.dp(6f);
    wrap.addView(card, cardParams);
    wrap.setLayoutParams(new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // keep the card centred in the space above the keyboard
    final android.graphics.Rect frame = new android.graphics.Rect();
    final int[] location = new int[2];
    wrap.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
      if (wrap.getHeight() == 0) return;
      wrap.getWindowVisibleDisplayFrame(frame);
      wrap.getLocationOnScreen(location);
      int bottomInset = Math.max(0, location[1] + wrap.getHeight() - frame.bottom);
      int topInset = Math.max(0, frame.top - location[1]);
      if (wrap.getPaddingBottom() != bottomInset || wrap.getPaddingTop() != topInset) {
        wrap.setPadding(0, topInset, 0, bottomInset);
      }
    });

    popup.setNeedRootInsets(); // embedded in the activity's own window (not a separate PopupWindow) — the keyboard works and stays put
    popup.setBackListener((fromTop, commit) -> true); // the back key doesn't close it either (it only hides the keyboard)
    popup.setDismissListener(p -> {
      if (onDismiss != null) onDismiss.run();
      // the keyboard goes back to the chat input if it had it
      if (previousFocus != null && previousFocus.isAttachedToWindow() && previousFocus instanceof android.widget.EditText) {
        previousFocus.requestFocus();
      } else {
        View focus = activity.getCurrentFocus();
        if (focus != null) org.thunderdog.challegram.tool.Keyboard.hide(focus);
      }
    });
    wrap.setAlpha(0f);
    popup.showNonAnimatedView(wrap);
    wrap.animate().alpha(1f).setDuration(150).start();
    return result;
  }

  /**
   * The text area has a fixed height — six lines (it scrolls inside), so the window doesn't jump while typing;
   * on a small screen with the keyboard open it gets lower so the buttons and «Save» stay visible.
   */
  private static void fitAboveKeyboard (View root, android.widget.TextView text) {
    final android.graphics.Rect frame = new android.graphics.Rect();
    root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
      if (root.getHeight() == 0) return;
      root.getWindowVisibleDisplayFrame(frame);
      int others = root.getHeight() - text.getHeight();
      int sixLines = text.getLineHeight() * 6 + text.getPaddingTop() + text.getPaddingBottom();
      int available = frame.height() - others - Screen.dp(20f);
      int height = Math.max(text.getLineHeight() * 2 + text.getPaddingTop() + text.getPaddingBottom(), Math.min(sixLines, available));
      if (text.getMaxHeight() != height || text.getMinHeight() != height) {
        text.setMinHeight(height);
        text.setMaxHeight(height);
      }
    });
  }

  /** Views made in code have no scrollbar drawable (it comes from a style), so the bar is set explicitly; hidden before Android 10. */
  private static void setupScrollbar (View view) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
      GradientDrawable thumb = rounded(ColorUtils.alphaColor(.35f, Theme.textAccentColor()), 2f);
      thumb.setSize(Screen.dp(4f), Screen.dp(24f));
      GradientDrawable track = rounded(ColorUtils.alphaColor(.08f, Theme.textAccentColor()), 2f);
      track.setSize(Screen.dp(4f), Screen.dp(24f));
      view.setVerticalScrollbarThumbDrawable(thumb);
      view.setVerticalScrollbarTrackDrawable(track);
      view.setVerticalScrollBarEnabled(true);
      view.setScrollbarFadingEnabled(false);
    } else {
      view.setVerticalScrollBarEnabled(false);
    }
  }

  private static int textMaxHeight () {
    return Math.min(Screen.dp(300f), (int) (Screen.currentHeight() * .38f));
  }

  private static LinearLayout newRoot (Context context) {
    LinearLayout root = new LinearLayout(context);
    root.setOrientation(LinearLayout.VERTICAL);
    return root;
  }

  private static LinearLayout newPanel (Context context) {
    LinearLayout panel = new LinearLayout(context);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(Screen.dp(12f), Screen.dp(10f), Screen.dp(12f), Screen.dp(12f));
    panel.setBackgroundColor(ColorUtils.alphaColor(.04f, Theme.textAccentColor()));
    return panel;
  }

  private static TextView addHeader (LinearLayout root, String title) {
    Context context = root.getContext();
    LinearLayout header = new LinearLayout(context);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(Screen.dp(18f), Screen.dp(14f), Screen.dp(10f), Screen.dp(4f));
    TextView titleView = new TextView(context);
    titleView.setText(title);
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
    titleView.setTypeface(Fonts.getRobotoBold());
    titleView.setTextColor(Theme.textAccentColor());
    header.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    TextView counter = new TextView(context);
    counter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
    counter.setTextColor(Theme.textDecentColor());
    counter.setTypeface(Fonts.getRobotoRegular());
    counter.setPadding(0, 0, Screen.dp(8f), 0);
    header.addView(counter);
    ImageView close = new ImageView(context);
    close.setTag("close");
    close.setImageResource(R.drawable.baseline_close_24);
    close.setColorFilter(Theme.textDecentColor());
    close.setScaleType(ImageView.ScaleType.CENTER);
    close.setBackground(rounded(ColorUtils.alphaColor(.07f, Theme.textAccentColor()), 17f));
    close.setContentDescription(Lang.getString(R.string.Cancel));
    header.addView(close, new LinearLayout.LayoutParams(Screen.dp(34f), Screen.dp(34f)));
    root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return counter;
  }

  private static void updateCounter (TextView counter, CharSequence text) {
    int length = text != null ? Character.codePointCount(text, 0, text.length()) : 0;
    counter.setText(Lang.getString(R.string.Tgx101EditorChars, length));
  }

  private static void addToolButton (LinearLayout grid, Object icon, int label, int columns, View.OnClickListener onClick) {
    Context context = grid.getContext();
    LinearLayout button = new LinearLayout(context);
    button.setOrientation(LinearLayout.VERTICAL);
    button.setGravity(Gravity.CENTER);
    button.setMinimumHeight(Screen.dp(52f));
    button.setPadding(0, Screen.dp(6f), 0, Screen.dp(5f));
    button.setBackground(pressable(Theme.fillingColor(), 12f));
    button.setOnClickListener(onClick);
    button.setContentDescription(Lang.getString(label));
    if (icon == null) {
      // text-only button (the «Edit» tab)
      TextView name = new TextView(context);
      name.setText(Lang.getString(label));
      name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
      name.setTypeface(Fonts.getRobotoMedium());
      name.setTextColor(Theme.textAccentColor());
      name.setGravity(Gravity.CENTER);
      name.setSingleLine(true);
      if (label == R.string.Tgx101EditorSelectAll) {
        // «Выбрать всё» → «Всё» when the button is too narrow
        name.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
          int available = button.getWidth() - Screen.dp(10f);
          String full = Lang.getString(R.string.Tgx101EditorSelectAll);
          if (available > 0 && name.getPaint().measureText(full) > available && full.contentEquals(name.getText())) {
            name.post(() -> name.setText(Lang.getString(R.string.Tgx101EditorSelectAllShort)));
          }
        });
      }
      button.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
      params.leftMargin = params.rightMargin = Screen.dp(3f);
      grid.addView(button, params);
      grid.setWeightSum(Math.max(columns, grid.getChildCount()));
      return;
    }
    if (icon instanceof Integer) {
      ImageView image = new ImageView(context);
      image.setImageResource((Integer) icon);
      image.setColorFilter(Theme.textAccentColor());
      button.addView(image, new LinearLayout.LayoutParams(Screen.dp(22f), Screen.dp(22f)));
    } else {
      TextView glyph = new TextView(context);
      glyph.setText((String) icon);
      glyph.setGravity(Gravity.CENTER);
      glyph.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
      glyph.setTypeface(Fonts.getRobotoMedium());
      glyph.setTextColor(Theme.textAccentColor());
      glyph.setIncludeFontPadding(false);
      if ("S".equals(icon)) glyph.setPaintFlags(glyph.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
      button.addView(glyph, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(22f)));
    }
    TextView caption = new TextView(context);
    caption.setText(Lang.getString(label));
    caption.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f);
    caption.setTypeface(Fonts.getRobotoRegular());
    caption.setTextColor(Theme.textDecentColor());
    caption.setSingleLine(true);
    caption.setGravity(Gravity.CENTER);
    button.addView(caption, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    int gap = Screen.dp(3f);
    params.leftMargin = params.rightMargin = gap;
    grid.addView(button, params);
    // keep the width of one cell the same in the three-button row of the «Edit» tab
    grid.setWeightSum(Math.max(columns, grid.getChildCount()));
  }

  private static void addPrimaryButton (LinearLayout panel, String text, View.OnClickListener onClick) {
    TextView button = new TextView(panel.getContext());
    button.setText(text);
    button.setGravity(Gravity.CENTER);
    button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    button.setTypeface(Fonts.getRobotoMedium());
    button.setTextColor(0xffffffff);
    button.setBackground(pressable(Theme.getColor(ColorId.fillingPositive), 12f));
    button.setOnClickListener(onClick);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(44f));
    params.topMargin = Screen.dp(10f);
    params.leftMargin = params.rightMargin = Screen.dp(3f);
    panel.addView(button, params);
  }

  private static LinearLayout.LayoutParams matchWrap (int topMargin) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = topMargin;
    return params;
  }

  /** A short highlight on tap */
  private static android.graphics.drawable.Drawable pressable (int color, float radiusDp) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(ColorUtils.alphaColor(.22f, Theme.textAccentColor())), rounded(color, radiusDp), rounded(0xffffffff, radiusDp));
    }
    return rounded(color, radiusDp);
  }

  private static GradientDrawable rounded (int color, float radiusDp) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(Screen.dp(radiusDp));
    return drawable;
  }
}
