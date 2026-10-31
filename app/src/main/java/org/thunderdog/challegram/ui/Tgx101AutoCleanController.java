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
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101AutoClean;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.SliderWrapView;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101 (approved mockup «Кэш» 2, user 2026-10-30): Storage → «Auto clean». For personal chats, groups and channels —
 * «delete if not opened for» and «when the cache is bigger than»; what may be deleted; pinned chats left alone.
 * The rules are applied when the screen is left, if they changed.
 */
public class Tgx101AutoCleanController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;
  private long[] sizes;
  private boolean changed;

  private static final int[] TTL_IDS = {R.id.btn_tgx101CleanTtlPrivate, R.id.btn_tgx101CleanTtlGroup, R.id.btn_tgx101CleanTtlChannel};
  private static final int[] LIMIT_IDS = {R.id.btn_tgx101CleanLimitPrivate, R.id.btn_tgx101CleanLimitGroup, R.id.btn_tgx101CleanLimitChannel};
  private static final int[] KIND_NAMES = {R.string.Tgx101CleanPrivate, R.string.Tgx101CleanGroups, R.string.Tgx101CleanChannels};
  private static final int[] TYPE_IDS = {R.id.btn_tgx101CleanVideo, R.id.btn_tgx101CleanFiles, R.id.btn_tgx101CleanPhoto, R.id.btn_tgx101CleanVoice, R.id.btn_tgx101CleanStickers};
  private static final int[] TYPE_NAMES = {R.string.Tgx101CleanTypeVideo, R.string.Tgx101CleanTypeFiles, R.string.Tgx101CleanTypePhoto, R.string.Tgx101CleanTypeVoice, R.string.Tgx101CleanTypeStickers};
  private static final int[] TYPES = {Tgx101AutoClean.TYPE_VIDEO, Tgx101AutoClean.TYPE_FILES, Tgx101AutoClean.TYPE_PHOTO, Tgx101AutoClean.TYPE_VOICE, Tgx101AutoClean.TYPE_STICKERS};

  public Tgx101AutoCleanController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101AutoClean;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101AutoClean);
  }

  /** For the row on the storage screen */
  public static String value () {
    if (!Tgx101AutoClean.hasRules()) return Lang.getString(R.string.Tgx101CleanOff);
    StringBuilder b = new StringBuilder();
    for (int kind = 0; kind < Tgx101AutoClean.KIND_COUNT; kind++) {
      if (b.length() > 0) b.append(" · ");
      int ttl = Tgx101AutoClean.ttl(kind), limit = Tgx101AutoClean.limitMb(kind);
      b.append(ttl != 0 ? ttlName(ttl) : limit != 0 ? limitName(limit) : "∞");
    }
    return b.toString();
  }

  private static String ttlName (int seconds) {
    if (seconds == 0) return Lang.getString(R.string.Tgx101CleanNever);
    int days = seconds / (60 * 60 * 24);
    if (days >= 30) return Lang.plural(R.string.xMonths, days / 30);
    if (days >= 7) return Lang.plural(R.string.xWeeks, days / 7);
    return Lang.plural(R.string.xDays, days);
  }

  private static String limitName (int mb) {
    if (mb == 0) return Lang.getString(R.string.Tgx101CleanNoLimit);
    return Strings.buildSize(mb * 1024L * 1024L);
  }

  private static String[] names (int[] steps, boolean ttl) {
    String[] values = new String[steps.length];
    for (int i = 0; i < steps.length; i++) values[i] = ttl ? ttlName(steps[i]) : limitName(steps[i]);
    return values;
  }

  private static int kindOf (int id, int[] ids) {
    for (int i = 0; i < ids.length; i++) if (ids[i] == id) return i;
    return -1;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_tgx101CleanSkipPinned) {
          view.getToggler().setRadioEnabled(Tgx101AutoClean.skipPinned(), isUpdate);
        }
      }

      @Override
      protected void setSliderValues (ListItem item, SliderWrapView view) {
        super.setSliderValues(item, view);
        // user 2026-10-30: the sliders reached the screen's edges — dragging them often became a «back» swipe
        view.setPadding(Screen.dp(20f), 0, Screen.dp(36f), 0);
      }

      @Override
      protected void onSliderValueChanged (ListItem item, SliderWrapView view, int value, int oldValue) {
        if (item.getId() == R.id.btn_tgx101CleanTtlStories) {
          Tgx101AutoClean.setStoryTtl(Tgx101AutoClean.STORY_TTL_STEPS[value]);
          changed = true;
          return;
        }
        int kind = kindOf(item.getId(), TTL_IDS);
        if (kind != -1) {
          Tgx101AutoClean.setTtl(kind, Tgx101AutoClean.TTL_STEPS[value]);
        } else if ((kind = kindOf(item.getId(), LIMIT_IDS)) != -1) {
          Tgx101AutoClean.setLimitMb(kind, Tgx101AutoClean.LIMIT_STEPS_MB[value]);
          updateUsage(kind);
        }
        changed = true;
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
    tdlib.send(new TdApi.GetStorageStatistics(1000), (stats, error) -> {
      if (stats == null) return;
      long[] sizes = Tgx101AutoClean.sizesByKind(tdlib, stats);
      runOnUiThreadOptional(() -> {
        this.sizes = sizes;
        for (int kind = 0; kind < Tgx101AutoClean.KIND_COUNT; kind++) updateUsage(kind);
      });
    });
  }

  private String usageText (int kind) {
    if (sizes == null) return Lang.getString(R.string.Calculating);
    int limit = Tgx101AutoClean.limitMb(kind);
    String now = Strings.buildSize(sizes[kind]);
    return limit != 0 ? Lang.getString(R.string.Tgx101CleanUsageOf, now, limitName(limit)) : Lang.getString(R.string.Tgx101CleanUsage, now);
  }

  private void updateUsage (int kind) {
    if (adapter == null) return;
    int index = adapter.indexOfViewById(usageId(kind));
    if (index == -1) return;
    adapter.getItems().get(index).setString(usageText(kind));
    adapter.notifyItemChanged(index);
  }

  private static int usageId (int kind) {
    return kind == Tgx101AutoClean.KIND_PRIVATE ? R.id.btn_tgx101CleanUsagePrivate : kind == Tgx101AutoClean.KIND_GROUP ? R.id.btn_tgx101CleanUsageGroup : R.id.btn_tgx101CleanUsageChannel;
  }

  private List<ListItem> buildItems () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    for (int kind = 0; kind < Tgx101AutoClean.KIND_COUNT; kind++) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, KIND_NAMES[kind]));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SLIDER, TTL_IDS[kind], 0, R.string.Tgx101CleanTtl)
        .setSliderInfo(names(Tgx101AutoClean.TTL_STEPS, true), Tgx101AutoClean.stepOf(Tgx101AutoClean.TTL_STEPS, Tgx101AutoClean.ttl(kind))));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SLIDER, LIMIT_IDS[kind], 0, R.string.Tgx101CleanLimit)
        .setSliderInfo(names(Tgx101AutoClean.LIMIT_STEPS_MB, false), Tgx101AutoClean.stepOf(Tgx101AutoClean.LIMIT_STEPS_MB, Tgx101AutoClean.limitMb(kind))));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, usageId(kind), 0, usageText(kind), false));
    }
    // (user 2026-10-31): stories have their own time, 2 days by default
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101CleanStories));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SLIDER, R.id.btn_tgx101CleanTtlStories, 0, R.string.Tgx101CleanTtl)
      .setSliderInfo(names(Tgx101AutoClean.STORY_TTL_STEPS, true), Tgx101AutoClean.stepOf(Tgx101AutoClean.STORY_TTL_STEPS, Tgx101AutoClean.storyTtl())));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101CleanStoriesHint));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101CleanWhat));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < TYPE_IDS.length; i++) {
      if (i > 0) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, TYPE_IDS[i], 0, TYPE_NAMES[i], Tgx101AutoClean.hasType(TYPES[i])));
    }
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101CleanSkipPinned, 0, R.string.Tgx101CleanSkipPinned));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101CleanHint));
    return items;
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_tgx101CleanSkipPinned) {
      Tgx101AutoClean.setSkipPinned(adapter.toggleView(v));
      changed = true;
      return;
    }
    for (int i = 0; i < TYPE_IDS.length; i++) {
      if (TYPE_IDS[i] == id) {
        Tgx101AutoClean.setType(TYPES[i], adapter.toggleView(v));
        changed = true;
        return;
      }
    }
  }

  @Override
  public boolean canSlideBackFrom (org.thunderdog.challegram.navigation.NavigationController navigationController, float x, float y) {
    // a drag on a slider row is the slider's, not a «back» swipe
    CustomRecyclerView recyclerView = getRecyclerView();
    View root = navigationController.getValue();
    if (recyclerView == null || root == null) return true;
    int[] rootAt = new int[2], rowAt = new int[2];
    root.getLocationOnScreen(rootAt);
    float screenY = rootAt[1] + y;
    for (int i = 0; i < recyclerView.getChildCount(); i++) {
      View child = recyclerView.getChildAt(i);
      if (!(child instanceof SliderWrapView)) continue;
      child.getLocationOnScreen(rowAt);
      if (screenY >= rowAt[1] && screenY < rowAt[1] + child.getHeight()) return false;
    }
    return true;
  }

  @Override
  public void destroy () {
    super.destroy();
    if (changed) Tgx101AutoClean.run(tdlib, null);
  }
}
