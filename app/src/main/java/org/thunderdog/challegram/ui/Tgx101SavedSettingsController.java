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
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101SavedStore;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/** TGx101 (approved mockup 10): Saved settings — the «Saved» banner, what opens, home screen tiles, tags */
public class Tgx101SavedSettingsController extends RecyclerViewController<Void> implements View.OnClickListener {
  public Tgx101SavedSettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101SavedSettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101SavedSettings);
  }

  private SettingsAdapter adapter;

  private Tgx101SavedStore store () {
    return Tgx101SavedStore.get(tdlib);
  }

  private static int sheetName (int mode) {
    switch (mode) {
      case Tgx101SavedStore.SHEET_EXTERNAL_ONLY: return R.string.Tgx101SavedBannerExternal;
      case Tgx101SavedStore.SHEET_NEVER: return R.string.Tgx101SavedBannerNever;
    }
    return R.string.Tgx101SavedBannerAlways;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Tgx101SavedStore s = store();
        int id = item.getId();
        if (id == R.id.btn_tgx101SavedQuickReactions) {
          view.setData(android.text.TextUtils.join(" ", Tgx101SavedKit.quickReactions()));
        } else if (id == R.id.btn_tgx101SavedBanner) {
          view.setData(Lang.getString(sheetName(s.sheetMode)));
        } else if (id == R.id.btn_tgx101SavedOpen) {
          view.setData(Lang.getString(s.openMode == Tgx101SavedStore.OPEN_FEED ? R.string.Tgx101SavedOpenFeed : R.string.Tgx101SavedOpenHome));
        } else if (id == R.id.btn_tgx101SavedFolderView) {
          view.setData(Lang.getString(s.folderView == Tgx101SavedStore.VIEW_TILES ? R.string.Tgx101SavedFolderViewTiles : s.folderView == Tgx101SavedStore.VIEW_MOSAIC ? R.string.Tgx101SavedFolderViewMosaic : R.string.Tgx101SavedFolderViewCapsules));
        } else if (id == R.id.btn_tgx101StorageRow) {
          view.getToggler().setRadioEnabled(org.thunderdog.challegram.unsorted.Settings.instance().tgx101StorageRow(), isUpdate);
        } else if (id == R.id.btn_tgx101SwipeToStorage) {
          view.getToggler().setRadioEnabled(org.thunderdog.challegram.unsorted.Settings.instance().tgx101SwipeToStorage(), isUpdate);
        } else if (id == R.id.btn_tgx101AiAutoTags) {
          view.getToggler().setRadioEnabled(org.thunderdog.challegram.unsorted.Settings.instance().tgx101AiAutoTags(), isUpdate);
        } else if (id == R.id.btn_tgx101SavedStorageOn) {
          view.getToggler().setRadioEnabled(s.storageOn, isUpdate);
        } else if (id == R.id.btn_tgx101SavedSwitch) {
          view.getToggler().setRadioEnabled(s.switchButton, isUpdate);
        } else if (id == R.id.btn_tgx101SavedInboxTile) {
          view.getToggler().setRadioEnabled(s.inboxTile, isUpdate);
        } else if (id == R.id.btn_tgx101SavedShowPinned) {
          view.getToggler().setRadioEnabled(s.showPinned, isUpdate);
        } else if (id == R.id.btn_tgx101SavedHashtag) {
          view.getToggler().setRadioEnabled(s.hashtagOwn, isUpdate);
        } else if (id == R.id.btn_tgx101SavedPanelLabels) {
          view.getToggler().setRadioEnabled(s.panelLabels, isUpdate);
        } else if (id == R.id.btn_tgx101SavedBackSwipe) {
          view.setData(Lang.getString(s.backSwipe == Tgx101SavedStore.BACK_RIGHT ? R.string.Tgx101SavedBackRight : s.backSwipe == Tgx101SavedStore.BACK_LEFT ? R.string.Tgx101SavedBackLeft : R.string.Tgx101SavedBackBoth));
        }
      }
    };
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    // (approved mockup 8 Г, user 2026-10-16)
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SavedStorageSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SavedStorageOn, 0, R.string.Tgx101SavedStorageOn));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101AiAutoTags, 0, R.string.Tgx101AiAutoTags));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101StorageRow, 0, R.string.Tgx101StorageRowSetting));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SwipeToStorage, 0, R.string.Tgx101SwipeToStorage)); // (user 2026-10-24 21:26)
    if (Tgx101SavedStore.get(tdlib).hasHiddenRows()) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101StorageRowShowAll, 0, R.string.Tgx101StorageRowShowAll));
    }
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101SavedQuickReactions, 0, R.string.Tgx101SavedQuickReactions));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101SavedOpen, 0, R.string.Tgx101SavedOpenSetting));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SavedSwitch, 0, R.string.Tgx101SavedSwitchSetting));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101SavedSwitchReset, 0, R.string.Tgx101SavedSwitchReset));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101SavedStorageDesc));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SavedBannerSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101SavedBanner, 0, R.string.Tgx101SavedBannerSetting));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101SavedBannerDesc));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SavedHomeSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101SavedFolderView, 0, R.string.Tgx101SavedFolderView));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SavedInboxTile, 0, R.string.Tgx101SavedInboxTileSetting));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SavedShowPinned, 0, R.string.Tgx101SavedShowPinnedSetting));
    // (user 2026-11-08) no choice of the back swipe: left to right only
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101SavedHomeDesc));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SavedTagsSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SavedHashtag, 0, R.string.Tgx101SavedHashtagSetting));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101SavedHashtagDesc));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SavedSelectSection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SavedPanelLabels, 0, R.string.Tgx101SavedPanelLabelsSetting));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  // (user 2026-11-05, mockup): up to seven pinned in one row: a tap on one below pins or unpins it, a tap on a pinned one
  // unpins it, dragging along the row moves it
  private void showQuickReactions () {
    final java.util.ArrayList<String> pinned = new java.util.ArrayList<>(java.util.Arrays.asList(Tgx101SavedKit.quickReactions()));
    final java.util.ArrayList<String> all = new java.util.ArrayList<>(java.util.Arrays.asList(Tgx101SavedKit.QUICK_REACTIONS));
    all.addAll(java.util.Arrays.asList(Tgx101SavedKit.MORE_REACTIONS));
    final int accent = Tgx101SavedHomeController.accent();
    final android.widget.TextView[] slots = new android.widget.TextView[Tgx101SavedKit.QUICK_REACTIONS_MAX];
    final java.util.ArrayList<android.widget.TextView> cells = new java.util.ArrayList<>();
    final android.widget.TextView[] count = new android.widget.TextView[1];
    final Runnable paint = () -> {
      count[0].setText(Lang.getString(R.string.Tgx101SavedQuickReactionsCount, pinned.size(), Tgx101SavedKit.QUICK_REACTIONS_MAX));
      for (int i = 0; i < slots.length; i++) {
        slots[i].setText(i < pinned.size() ? pinned.get(i) : "");
        slots[i].setVisibility(i < pinned.size() ? View.VISIBLE : View.GONE);
      }
      for (android.widget.TextView cell : cells) {
        boolean on = pinned.contains((String) cell.getTag());
        cell.setBackground(Tgx101SavedHomeController.card(on ? Tgx101SavedHomeController.tint(accent, .28f) : Tgx101SavedHomeController.tint(accent, .08f), 12f));
      }
    };
    final Runnable changed = () -> {
      Tgx101SavedKit.setQuickReactions(pinned);
      paint.run();
      adapter.updateValuedSettingById(R.id.btn_tgx101SavedQuickReactions);
    };
    Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101SavedQuickReactions)));
      android.widget.TextView hint = Tgx101SavedHomeController.text(context, 13f, org.thunderdog.challegram.theme.Theme.textDecentColor(), false);
      count[0] = hint;
      hint.setPadding(org.thunderdog.challegram.tool.Screen.dp(20f), 0, org.thunderdog.challegram.tool.Screen.dp(20f), org.thunderdog.challegram.tool.Screen.dp(8f));
      body.addView(hint);
      android.widget.LinearLayout row = new android.widget.LinearLayout(context) {
        private int dragged = -1;
        private float downX;
        private boolean moved;

        @Override
        public boolean onInterceptTouchEvent (android.view.MotionEvent e) {
          return true;
        }

        private int indexAt (float x) {
          int count = Math.max(1, pinned.size());
          int inner = getWidth() - getPaddingLeft() - getPaddingRight();
          return Math.max(0, Math.min(count - 1, (int) ((x - getPaddingLeft()) / (inner / (float) count))));
        }

        @Override
        public boolean onTouchEvent (android.view.MotionEvent e) {
          switch (e.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
              dragged = indexAt(e.getX());
              downX = e.getX();
              moved = false;
              getParent().requestDisallowInterceptTouchEvent(true);
              return true;
            case android.view.MotionEvent.ACTION_MOVE:
              if (Math.abs(e.getX() - downX) > org.thunderdog.challegram.tool.Screen.dp(8f)) moved = true;
              if (moved && dragged != -1) {
                int to = indexAt(e.getX());
                if (to != dragged) {
                  pinned.add(to, pinned.remove(dragged));
                  dragged = to;
                  changed.run();
                }
              }
              return true;
            case android.view.MotionEvent.ACTION_UP:
              if (!moved && dragged != -1 && pinned.size() > 1) {
                pinned.remove(dragged);
                changed.run();
              }
              dragged = -1;
              return true;
            case android.view.MotionEvent.ACTION_CANCEL:
              dragged = -1;
              return true;
          }
          return super.onTouchEvent(e);
        }
      };
      row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
      row.setPadding(org.thunderdog.challegram.tool.Screen.dp(16f), 0, org.thunderdog.challegram.tool.Screen.dp(16f), org.thunderdog.challegram.tool.Screen.dp(12f));
      for (int i = 0; i < slots.length; i++) {
        android.widget.TextView slot = Tgx101SavedHomeController.text(context, 19f, org.thunderdog.challegram.theme.Theme.textAccentColor(), false);
        slot.setGravity(android.view.Gravity.CENTER);
        slot.setBackground(Tgx101SavedHomeController.card(Tgx101SavedHomeController.tint(accent, .28f), 12f));
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, org.thunderdog.challegram.tool.Screen.dp(44f), 1f);
        lp.setMargins(org.thunderdog.challegram.tool.Screen.dp(2f), 0, org.thunderdog.challegram.tool.Screen.dp(2f), 0);
        row.addView(slot, lp);
        slots[i] = slot;
      }
      body.addView(row);
      final int perRow = 7;
      android.widget.LinearLayout line = null;
      for (int i = 0; i < all.size(); i++) {
        if (i % perRow == 0) {
          line = new android.widget.LinearLayout(context);
          line.setOrientation(android.widget.LinearLayout.HORIZONTAL);
          line.setPadding(org.thunderdog.challegram.tool.Screen.dp(16f), 0, org.thunderdog.challegram.tool.Screen.dp(16f), org.thunderdog.challegram.tool.Screen.dp(4f));
          body.addView(line);
        }
        final String e = all.get(i);
        android.widget.TextView cell = Tgx101SavedHomeController.text(context, 19f, org.thunderdog.challegram.theme.Theme.textAccentColor(), false);
        cell.setText(e);
        cell.setTag(e);
        cell.setGravity(android.view.Gravity.CENTER);
        org.thunderdog.challegram.tool.Views.setClickable(cell);
        cell.setOnClickListener(v -> {
          if (pinned.contains(e)) {
            if (pinned.size() > 1) pinned.remove(e);
          } else if (pinned.size() < Tgx101SavedKit.QUICK_REACTIONS_MAX) {
            pinned.add(e);
          }
          changed.run();
        });
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, org.thunderdog.challegram.tool.Screen.dp(42f), 1f);
        lp.setMargins(org.thunderdog.challegram.tool.Screen.dp(2f), 0, org.thunderdog.challegram.tool.Screen.dp(2f), 0);
        line.addView(cell, lp);
        cells.add(cell);
      }
      // an even last line
      for (int i = all.size() % perRow; i != 0 && i < perRow; i++) {
        line.addView(new View(context), new android.widget.LinearLayout.LayoutParams(0, 1, 1f));
      }
      android.widget.TextView how = Tgx101SavedHomeController.text(context, 12f, org.thunderdog.challegram.theme.Theme.textDecentColor(), false);
      how.setText(Lang.getString(R.string.Tgx101SavedQuickReactionsHint));
      how.setPadding(org.thunderdog.challegram.tool.Screen.dp(20f), org.thunderdog.challegram.tool.Screen.dp(8f), org.thunderdog.challegram.tool.Screen.dp(20f), org.thunderdog.challegram.tool.Screen.dp(12f));
      body.addView(how);
      paint.run();
    });
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    Tgx101SavedStore s = store();
    if (id == R.id.btn_tgx101SavedQuickReactions) {
      showQuickReactions();
    } else if (id == R.id.btn_tgx101SavedBanner) {
      int[] modes = {Tgx101SavedStore.SHEET_ALWAYS, Tgx101SavedStore.SHEET_EXTERNAL_ONLY, Tgx101SavedStore.SHEET_NEVER};
      showOptions(Lang.getString(R.string.Tgx101SavedBannerSetting), new int[] {R.id.btn_done, R.id.btn_edit, R.id.btn_close},
        new String[] {Lang.getString(sheetName(modes[0])), Lang.getString(sheetName(modes[1])), Lang.getString(sheetName(modes[2]))}, null, null, (itemView, optionId) -> {
          s.sheetMode = optionId == R.id.btn_edit ? modes[1] : optionId == R.id.btn_close ? modes[2] : modes[0];
          s.save();
          adapter.updateValuedSettingById(R.id.btn_tgx101SavedBanner);
          return true;
        });
    } else if (id == R.id.btn_tgx101SavedOpen) {
      showOptions(Lang.getString(R.string.Tgx101SavedOpenSetting), new int[] {R.id.btn_done, R.id.btn_edit},
        new String[] {Lang.getString(R.string.Tgx101SavedOpenHome), Lang.getString(R.string.Tgx101SavedOpenFeed)}, null, null, (itemView, optionId) -> {
          s.openMode = optionId == R.id.btn_edit ? Tgx101SavedStore.OPEN_FEED : Tgx101SavedStore.OPEN_HOME;
          s.save();
          adapter.updateValuedSettingById(R.id.btn_tgx101SavedOpen);
          return true;
        });
    } else if (id == R.id.btn_tgx101SavedFolderView) {
      showOptions(Lang.getString(R.string.Tgx101SavedFolderView), new int[] {R.id.btn_share, R.id.btn_done, R.id.btn_edit},
        new String[] {Lang.getString(R.string.Tgx101SavedFolderViewMosaic), Lang.getString(R.string.Tgx101SavedFolderViewCapsules), Lang.getString(R.string.Tgx101SavedFolderViewTiles)}, null, null, (itemView, optionId) -> {
          s.folderView = optionId == R.id.btn_edit ? Tgx101SavedStore.VIEW_TILES : optionId == R.id.btn_share ? Tgx101SavedStore.VIEW_MOSAIC : Tgx101SavedStore.VIEW_CAPSULES;
          s.save();
          adapter.updateValuedSettingById(R.id.btn_tgx101SavedFolderView);
          return true;
        });
    } else if (id == R.id.btn_tgx101StorageRow) {
      org.thunderdog.challegram.unsorted.Settings.instance().setTgx101StorageRow(adapter.toggleView(v));
    } else if (id == R.id.btn_tgx101SwipeToStorage) {
      org.thunderdog.challegram.unsorted.Settings.instance().setTgx101SwipeToStorage(adapter.toggleView(v));
    } else if (id == R.id.btn_tgx101StorageRowShowAll) {
      Tgx101SavedStore.get(tdlib).showRowEverywhere();
      org.thunderdog.challegram.tool.UI.showToast(R.string.Done, android.widget.Toast.LENGTH_SHORT);
    } else if (id == R.id.btn_tgx101AiAutoTags) {
      boolean on = adapter.toggleView(v);
      org.thunderdog.challegram.unsorted.Settings.instance().setTgx101AiAutoTags(on);
      if (on) org.thunderdog.challegram.tool.UI.showToast(R.string.Tgx101AiAutoTagsHint, android.widget.Toast.LENGTH_LONG);
    } else if (id == R.id.btn_tgx101SavedStorageOn) {
      s.storageOn = adapter.toggleView(v);
      s.save();
    } else if (id == R.id.btn_tgx101SavedSwitch) {
      s.switchButton = adapter.toggleView(v);
      s.save();
    } else if (id == R.id.btn_tgx101SavedSwitchReset) {
      s.switchButton = true;
      s.switchLeft[0] = s.switchLeft[1] = false;
      s.switchY[0] = s.switchY[1] = -1f;
      s.save();
      adapter.updateValuedSettingById(R.id.btn_tgx101SavedSwitch);
      UI.showToast(R.string.Done, android.widget.Toast.LENGTH_SHORT);
    } else if (id == R.id.btn_tgx101SavedInboxTile) {
      s.inboxTile = adapter.toggleView(v);
      s.save();
    } else if (id == R.id.btn_tgx101SavedShowPinned) {
      s.showPinned = adapter.toggleView(v);
      s.save();
    } else if (id == R.id.btn_tgx101SavedHashtag) {
      s.hashtagOwn = adapter.toggleView(v);
      s.save();
    } else if (id == R.id.btn_tgx101SavedPanelLabels) {
      s.panelLabels = adapter.toggleView(v);
      s.save();
    } else if (id == R.id.btn_tgx101SavedBackSwipe) {
      showOptions(Lang.getString(R.string.Tgx101SavedBackSwipe), new int[] {R.id.btn_done, R.id.btn_edit, R.id.btn_share},
        new String[] {Lang.getString(R.string.Tgx101SavedBackBoth), Lang.getString(R.string.Tgx101SavedBackRight), Lang.getString(R.string.Tgx101SavedBackLeft)}, null, null, (itemView, optionId) -> {
          s.backSwipe = optionId == R.id.btn_edit ? Tgx101SavedStore.BACK_RIGHT : optionId == R.id.btn_share ? Tgx101SavedStore.BACK_LEFT : Tgx101SavedStore.BACK_BOTH;
          s.save();
          adapter.updateValuedSettingById(R.id.btn_tgx101SavedBackSwipe);
          return true;
        });
    }
  }
}
