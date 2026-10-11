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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

/**
 * TGx101 (user 2026-10-11, approved mockup «Жесты-и-порядок-меню»): hold an item of a settings list and drag it — up,
 * down or into another section. Sections start at headers with the id {@code btn_tgx101DragSection}; an empty
 * section shows a {@code btn_tgx101DragEmpty} row to drop on. The icon at the start (and the «≡» at the end of rows
 * without a switch) picks an item up at once, a long press anywhere on it too; a tap stays a tap.
 */
public final class Tgx101DragOrder {
  private Tgx101DragOrder () { }

  public interface Callback {
    boolean canDrag (ListItem item);

    /** Whether the dragged item may go where {@code target} is (e.g. only within its own group) */
    default boolean canDrop (ListItem dragged, ListItem target) {
      return true;
    }

    /** Live update while the item moves (the preview); {@code finished} — the finger is up, save the order */
    void onOrderChanged (boolean finished);
  }

  public static boolean isDropTarget (ListItem item, Callback callback) {
    return item != null && (item.getId() == R.id.btn_tgx101DragEmpty || callback.canDrag(item));
  }

  /** The draggable rows of each section, top to bottom */
  public static List<List<ListItem>> sections (List<ListItem> items, Callback callback) {
    List<List<ListItem>> result = new ArrayList<>();
    List<ListItem> current = null;
    for (ListItem item : items) {
      if (item.getViewType() == ListItem.TYPE_HEADER && item.getId() == R.id.btn_tgx101DragSection) {
        current = new ArrayList<>();
        result.add(current);
      } else if (current != null && callback.canDrag(item)) {
        current.add(item);
      }
    }
    return result;
  }

  public static ItemTouchHelper attach (RecyclerView recyclerView, SettingsAdapter adapter, Callback callback) {
    final boolean[] moved = new boolean[1];
    ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
      private ListItem itemAt (RecyclerView.ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        List<ListItem> items = adapter.getItems();
        return position >= 0 && position < items.size() ? items.get(position) : null;
      }

      @Override
      public int getMovementFlags (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
        ListItem item = itemAt(holder);
        return item != null && callback.canDrag(item) ? makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) : 0;
      }

      @Override
      public boolean isLongPressDragEnabled () {
        return true;
      }

      @Override
      public boolean isItemViewSwipeEnabled () {
        return false;
      }

      @Override
      public boolean canDropOver (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder current, @NonNull RecyclerView.ViewHolder target) {
        ListItem dragged = itemAt(current), over = itemAt(target);
        return isDropTarget(over, callback) && dragged != null && callback.canDrop(dragged, over);
      }

      @Override
      public boolean onMove (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
        int from = source.getBindingAdapterPosition(), to = target.getBindingAdapterPosition();
        ListItem dragged = itemAt(source), over = itemAt(target);
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || !isDropTarget(over, callback) || dragged == null || !callback.canDrop(dragged, over)) {
          return false;
        }
        adapter.moveItem(from, to, true);
        moved[0] = true;
        callback.onOrderChanged(false);
        return true;
      }

      @Override
      public void onSwiped (@NonNull RecyclerView.ViewHolder holder, int direction) { }

      @Override
      public void onSelectedChanged (RecyclerView.ViewHolder holder, int actionState) {
        super.onSelectedChanged(holder, actionState);
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && holder != null) {
          holder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
          holder.itemView.setAlpha(.92f);
          org.thunderdog.challegram.Tgx101Diag.mark("order: drag started at " + holder.getBindingAdapterPosition());
        }
      }

      @Override
      public void clearView (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
        super.clearView(recyclerView, holder);
        holder.itemView.setAlpha(1f);
        if (moved[0]) {
          moved[0] = false;
          org.thunderdog.challegram.Tgx101Diag.mark("order: dropped at " + holder.getBindingAdapterPosition());
          UI.post(() -> callback.onOrderChanged(true)); // not while the list is laying out
        }
      }
    });
    helper.attachToRecyclerView(recyclerView);
    // the icon (and the «≡» of rows without a switch) picks the item up right away
    recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
      @Override
      public boolean onInterceptTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN) {
          return false;
        }
        View child = rv.findChildViewUnder(e.getX(), e.getY());
        if (child == null) {
          return false;
        }
        RecyclerView.ViewHolder holder = rv.getChildViewHolder(child);
        int position = holder.getBindingAdapterPosition();
        List<ListItem> items = adapter.getItems();
        if (position < 0 || position >= items.size() || !callback.canDrag(items.get(position))) {
          return false;
        }
        ListItem item = items.get(position);
        float x = e.getX() - child.getLeft();
        float start = Lang.rtl() ? child.getWidth() - x : x;
        boolean atHandle = start < Screen.dp(64f) || (item.getViewType() != ListItem.TYPE_RADIO_SETTING && start > child.getWidth() - Screen.dp(56f));
        if (atHandle) {
          helper.startDrag(holder);
        }
        return false;
      }
    });
    recyclerView.addItemDecoration(new Separators(adapter, callback));
    return helper;
  }

  /** Lines between the rows of a section (separator items would get in the way of dragging) */
  private static class Separators extends RecyclerView.ItemDecoration {
    private final SettingsAdapter adapter;
    private final Callback callback;

    Separators (SettingsAdapter adapter, Callback callback) {
      this.adapter = adapter;
      this.callback = callback;
    }

    @Override
    public void onDrawOver (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      List<ListItem> items = adapter.getItems();
      Paint paint = Paints.fillingPaint(Theme.separatorColor());
      int height = Math.max(1, Screen.dp(.5f));
      int offset = Screen.dp(72f);
      boolean rtl = Lang.rtl();
      for (int i = 0; i < parent.getChildCount(); i++) {
        View child = parent.getChildAt(i);
        int position = parent.getChildAdapterPosition(child);
        if (position < 0 || position + 1 >= items.size() || child.getTranslationY() != 0f) {
          continue;
        }
        if (!isDropTarget(items.get(position), callback) || !isDropTarget(items.get(position + 1), callback)) {
          continue;
        }
        int bottom = child.getBottom();
        if (rtl) {
          c.drawRect(0, bottom - height, child.getWidth() - offset, bottom, paint);
        } else {
          c.drawRect(offset, bottom - height, child.getWidth(), bottom, paint);
        }
      }
    }
  }
}
