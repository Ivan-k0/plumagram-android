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
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101MessageFilters;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101: one message filter, step by step (approved mockup «Фильтры и живые примеры» А, user 2026-10-31 «здесь ничего
 * не понятно, что за что отвечает»): a live example on top, what to look for as tags, where, what to do; the fine
 * options are under «More». Every change is saved at once; a filter left without words is removed on the way out.
 */
public class Tgx101FilterEditController extends ViewController<Integer> {
  private LinearLayout column;

  public Tgx101FilterEditController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101FilterEdit;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101FilterEdit);
  }

  private int index () {
    Integer index = getArguments();
    return index != null ? index : -1;
  }

  /** A copy of the rule being edited, or null when it is gone */
  @Nullable
  Tgx101MessageFilters.Rule rule () {
    List<Tgx101MessageFilters.Rule> rules = Tgx101MessageFilters.getRules();
    int index = index();
    return index >= 0 && index < rules.size() ? rules.get(index).copy() : null;
  }

  void store (Tgx101MessageFilters.Rule rule) {
    List<Tgx101MessageFilters.Rule> rules = new ArrayList<>(Tgx101MessageFilters.getRules());
    int index = index();
    if (index >= 0 && index < rules.size()) {
      rule.invalidate();
      rules.set(index, rule);
      Tgx101MessageFilters.setRules(rules);
    }
  }

  void delete () {
    List<Tgx101MessageFilters.Rule> rules = new ArrayList<>(Tgx101MessageFilters.getRules());
    int index = index();
    if (index >= 0 && index < rules.size()) {
      rules.remove(index);
      Tgx101MessageFilters.setRules(rules);
    }
  }

  @Override
  protected View onCreateView (Context context) {
    ScrollView scroll = new ScrollView(context);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(Theme.backgroundColor());
    column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setPadding(0, Screen.dp(8f), 0, Screen.dp(24f));
    scroll.addView(column, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    rebuild();
    return scroll;
  }

  @Override
  public void onFocus () {
    super.onFocus();
    rebuild(); // back from «More»
  }

  @Override
  public void destroy () {
    super.destroy();
    Tgx101MessageFilters.Rule rule = rule();
    if (rule != null && rule.words().isEmpty()) {
      delete(); // nothing to look for: no filter
    }
  }

  private void rebuild () {
    if (column == null) return;
    column.removeAllViews();
    Tgx101MessageFilters.Rule rule = rule();
    if (rule == null) return;
    Context context = context();
    List<String> words = rule.words();
    String firstWord = words.isEmpty() ? "…" : Tgx101MessageFilters.label(words.get(0));

    // 1. the live example
    column.addView(header(context, Lang.getString(R.string.Tgx101FilterPreview)));
    LinearLayout preview = card(context);
    preview.setPadding(Screen.dp(14f), Screen.dp(12f), Screen.dp(14f), Screen.dp(12f));
    LinearLayout post = new LinearLayout(context);
    post.setOrientation(LinearLayout.VERTICAL);
    post.setPadding(Screen.dp(12f), Screen.dp(8f), Screen.dp(12f), Screen.dp(8f));
    post.setBackground(round(Theme.getColor(ColorId.bubbleIn_background), 14f));
    TextView channel = text(context, 13f, Theme.getColor(ColorId.textLink), true);
    channel.setText(Lang.getString(R.string.Tgx101FilterPreviewChannel));
    post.addView(channel);
    TextView postText = text(context, 14f, Theme.textAccentColor(), false);
    postText.setText(Lang.getString(R.string.Tgx101FilterPreviewPost));
    post.addView(postText);
    preview.addView(post, wrapWidth());
    if (rule.hideFully) {
      TextView gone = text(context, 13f, Theme.textDecentColor(), false);
      gone.setText(Lang.getString(R.string.Tgx101FilterPreviewGone));
      gone.setGravity(Gravity.CENTER);
      gone.setPadding(0, Screen.dp(10f), 0, Screen.dp(2f));
      preview.addView(gone, matchWidth());
    } else {
      TextView strip = text(context, 13f, Theme.getColor(ColorId.bubble_dateText_noWallpaper), false);
      strip.setText(Lang.getString(R.string.Tgx101FilterHiddenBy, firstWord));
      strip.setSingleLine(true);
      strip.setEllipsize(TextUtils.TruncateAt.END);
      strip.setPadding(Screen.dp(12f), Screen.dp(5f), Screen.dp(12f), Screen.dp(5f));
      strip.setBackground(round(Theme.getColor(ColorId.bubble_date_noWallpaper), 14f));
      LinearLayout.LayoutParams sp = wrapWidth();
      sp.gravity = Gravity.CENTER_HORIZONTAL;
      sp.topMargin = Screen.dp(10f);
      preview.addView(strip, sp);
    }
    column.addView(preview, cardParams());

    // 2. what to look for: tags
    column.addView(header(context, Lang.getString(rule.regex ? R.string.Tgx101FilterPattern : R.string.Tgx101FilterWhat)));
    LinearLayout what = card(context);
    what.setPadding(0, Screen.dp(12f), 0, Screen.dp(8f));
    Tgx101SavedActions.FlowLayout tags = new Tgx101SavedActions.FlowLayout(context);
    for (String word : words) {
      tags.addView(tag(context, word, () -> {
        if (rule.regex) {
          editPattern(rule);
        } else {
          List<String> list = rule.words();
          list.remove(word);
          rule.setWords(list);
          store(rule);
          rebuild();
        }
      }));
    }
    if (!rule.regex || words.isEmpty()) {
      EditText input = new EditText(context);
      input.setHint(Lang.getString(R.string.Tgx101FilterAddWord));
      input.setHintTextColor(Theme.textDecentColor());
      input.setTextColor(Theme.textAccentColor());
      input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      input.setBackground(null);
      input.setSingleLine(true);
      input.setPadding(Screen.dp(6f), 0, Screen.dp(6f), 0);
      input.setMinWidth(Screen.dp(150f));
      input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
      input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
      input.setOnEditorActionListener((v, actionId, event) -> {
        String word = v.getText().toString().trim();
        if (word.isEmpty()) return false;
        List<String> list = rule.words();
        if (!list.contains(word)) list.add(word);
        if (rule.regex) {
          rule.text = word;
        } else {
          rule.setWords(list);
        }
        store(rule);
        rebuild();
        UI.post(() -> {
          View next = column.findViewWithTag("tgx101FilterInput");
          if (next != null) {
            next.requestFocus();
            UI.showKeyboardDelayed(next);
          }
        });
        return true;
      });
      input.setTag("tgx101FilterInput");
      tags.addView(input);
    }
    what.addView(tags, matchWidth());
    TextView week = text(context, 13f, Theme.textDecentColor(), false);
    int count = Tgx101MessageFilters.weekCount(rule);
    week.setText(count > 0 ? Lang.getString(R.string.Tgx101FilterWeek, count) : Lang.getString(R.string.Tgx101FilterWeekNone));
    week.setPadding(Screen.dp(16f), Screen.dp(6f), Screen.dp(16f), 0);
    what.addView(week, matchWidth());
    column.addView(what, cardParams());
    if (!rule.regex) {
      column.addView(hint(context, Lang.getString(R.string.Tgx101FilterWhatHint)));
    }

    // 3. where
    column.addView(header(context, Lang.getString(R.string.Tgx101FilterWhere)));
    LinearLayout segments = new LinearLayout(context);
    segments.setOrientation(LinearLayout.HORIZONTAL);
    segments.setPadding(Screen.dp(3f), Screen.dp(3f), Screen.dp(3f), Screen.dp(3f));
    segments.setBackground(round(Theme.fillingColor(), 14f));
    boolean channels = rule.scope == Tgx101MessageFilters.SCOPE_CHANNELS || rule.scope == Tgx101MessageFilters.SCOPE_CHANNELS_GROUPS;
    boolean groups = rule.scope == Tgx101MessageFilters.SCOPE_GROUPS || rule.scope == Tgx101MessageFilters.SCOPE_CHANNELS_GROUPS;
    segments.addView(segment(context, Lang.getString(R.string.Tgx101FilterScopeEverywhere), rule.scope == Tgx101MessageFilters.SCOPE_ALL, () -> setScope(rule, Tgx101MessageFilters.SCOPE_ALL)), segmentParams());
    segments.addView(segment(context, Lang.getString(R.string.Tgx101FilterScopeChannelsShort), channels, () -> setScope(rule, Tgx101MessageFilters.SCOPE_CHANNELS)), segmentParams());
    segments.addView(segment(context, Lang.getString(R.string.Tgx101FilterScopeGroups), groups, () -> setScope(rule, Tgx101MessageFilters.SCOPE_GROUPS)), segmentParams());
    segments.addView(segment(context, Lang.getString(R.string.Tgx101FilterScopeOneChat), rule.scope == Tgx101MessageFilters.SCOPE_CHAT, () -> {
      if (rule.chatId == 0) {
        UI.showToast(Lang.getString(R.string.Tgx101FilterOneChatNone), android.widget.Toast.LENGTH_LONG);
      } else {
        setScope(rule, Tgx101MessageFilters.SCOPE_CHAT);
      }
    }), segmentParams());
    column.addView(segments, cardParams());
    if (rule.scope == Tgx101MessageFilters.SCOPE_CHAT && rule.chatTitle != null) {
      column.addView(hint(context, Lang.getString(R.string.Tgx101FilterScopeChat, rule.chatTitle)));
    }

    // 4. what to do
    column.addView(header(context, Lang.getString(R.string.Tgx101FilterAction)));
    LinearLayout action = card(context);
    action.addView(radio(context, Lang.getString(R.string.Tgx101FilterActionStrip), !rule.hideFully, () -> {
      rule.hideFully = false;
      store(rule);
      rebuild();
    }), matchWidth());
    action.addView(radio(context, Lang.getString(R.string.Tgx101FilterActionRemove), rule.hideFully, () -> {
      rule.hideFully = true;
      store(rule);
      rebuild();
    }), matchWidth());
    column.addView(action, cardParams());

    // 5. more
    LinearLayout more = card(context);
    TextView moreRow = text(context, 15f, Theme.getColor(ColorId.textNeutral), false);
    moreRow.setText(Lang.getString(R.string.Tgx101FilterMore) + "  ›");
    moreRow.setSingleLine(true);
    moreRow.setEllipsize(TextUtils.TruncateAt.END);
    moreRow.setGravity(Gravity.CENTER_VERTICAL);
    moreRow.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
    moreRow.setMinHeight(Screen.dp(52f));
    Views.setClickable(moreRow);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(moreRow);
    moreRow.setOnClickListener(v -> {
      Tgx101FilterMoreController c = new Tgx101FilterMoreController(context(), tdlib, this);
      navigateTo(c);
    });
    more.addView(moreRow, matchWidth());
    LinearLayout.LayoutParams mp = cardParams();
    mp.topMargin = Screen.dp(16f);
    column.addView(more, mp);
  }

  private void setScope (Tgx101MessageFilters.Rule rule, int scope) {
    rule.scope = scope;
    store(rule);
    rebuild();
  }

  private void editPattern (Tgx101MessageFilters.Rule rule) {
    Tgx101FiltersController.tgx101MultilineInput(openInputAlert(Lang.getString(R.string.Tgx101FilterPatternEdit), Lang.getString(R.string.Tgx101FilterPattern), R.string.Done, R.string.Cancel, rule.text, (inputView, result) -> {
      String text = result.trim();
      if (text.isEmpty()) return false;
      rule.text = text;
      store(rule);
      rebuild();
      return true;
    }, true));
  }

  // Small builders

  private static TextView text (Context context, float size, int color, boolean medium) {
    TextView v = new TextView(context);
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
    v.setTextColor(color);
    if (medium) v.setTypeface(Fonts.getRobotoMedium());
    return v;
  }

  private static GradientDrawable round (int color, float radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(Screen.dp(radius));
    return d;
  }

  private static TextView header (Context context, String title) {
    TextView v = text(context, 14f, Theme.getColor(ColorId.textNeutral), true);
    v.setText(title);
    v.setPadding(Screen.dp(20f), Screen.dp(16f), Screen.dp(20f), Screen.dp(8f));
    return v;
  }

  private static TextView hint (Context context, String text) {
    TextView v = text(context, 13f, Theme.textDecentColor(), false);
    v.setText(text);
    v.setPadding(Screen.dp(20f), Screen.dp(6f), Screen.dp(20f), 0);
    return v;
  }

  private static LinearLayout card (Context context) {
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(round(Theme.fillingColor(), 16f));
    return card;
  }

  private static LinearLayout.LayoutParams cardParams () {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    p.leftMargin = p.rightMargin = Screen.dp(12f);
    return p;
  }

  private static LinearLayout.LayoutParams matchWidth () {
    return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
  }

  private static LinearLayout.LayoutParams wrapWidth () {
    return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
  }

  private static LinearLayout.LayoutParams segmentParams () {
    return new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f);
  }

  /** A tag: the word and ✕; a word cut with «…» is shown whole on a hold */
  private View tag (Context context, String word, Runnable remove) {
    TextView v = text(context, 14f, Theme.textAccentColor(), false);
    v.setText(word + "   ✕");
    v.setSingleLine(true);
    v.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    v.setMaxWidth(Screen.currentWidth() - Screen.dp(80f));
    v.setGravity(Gravity.CENTER_VERTICAL);
    v.setPadding(Screen.dp(12f), 0, Screen.dp(10f), 0);
    v.setBackground(round(Theme.getColor(ColorId.fillingPressed), 18f));
    Views.setClickable(v);
    v.setOnClickListener(x -> remove.run());
    v.setOnLongClickListener(x -> {
      context().tooltipManager().builder(x).show(tdlib, word).hideDelayed();
      return true;
    });
    return v;
  }

  private static View segment (Context context, String title, boolean selected, Runnable click) {
    TextView v = text(context, 14f, selected ? Theme.getColor(ColorId.fillingPositiveContent) : Theme.textAccentColor(), selected);
    v.setText(title);
    v.setGravity(Gravity.CENTER);
    v.setSingleLine(true);
    v.setEllipsize(TextUtils.TruncateAt.END);
    if (selected) v.setBackground(round(Theme.getColor(ColorId.fillingPositive), 11f));
    Views.setClickable(v);
    v.setOnClickListener(x -> click.run());
    return v;
  }

  private static View radio (Context context, String title, boolean selected, Runnable click) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
    row.setMinimumHeight(Screen.dp(52f));
    Views.setClickable(row);
    org.thunderdog.challegram.support.RippleSupport.setTransparentSelector(row);
    row.setOnClickListener(x -> click.run());
    View mark = new View(context);
    int accent = Theme.getColor(ColorId.checkActive);
    mark.setBackground(selected ? new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[] {ring(accent), dot(accent)}) : ring(Theme.textDecentColor()));
    LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f));
    mp.rightMargin = Screen.dp(16f);
    row.addView(mark, mp);
    TextView label = text(context, 15f, Theme.textAccentColor(), false);
    label.setText(title);
    row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    return row;
  }

  private static GradientDrawable ring (int color) {
    GradientDrawable d = new GradientDrawable();
    d.setShape(GradientDrawable.OVAL);
    d.setStroke(Screen.dp(2f), color);
    return d;
  }

  private static android.graphics.drawable.Drawable dot (int color) {
    GradientDrawable d = new GradientDrawable();
    d.setShape(GradientDrawable.OVAL);
    d.setColor(color);
    return new android.graphics.drawable.InsetDrawable(d, Screen.dp(5f));
  }
}
