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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.StatFs;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101AutoClean;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;

/**
 * TGx101 (approved mockup «Кэш» 1, user 2026-10-30; the ring became one bar, 2026-10-31): the top of Storage — the cache by type, tabs All /
 * Personal / Groups / Channels, coloured checks for what to clear and one button with the total. The same colours mark
 * the types everywhere on the screen.
 */
public class Tgx101StorageOverview extends View {
  public static final int CAT_FILES = 0, CAT_VIDEO = 1, CAT_PHOTO = 2, CAT_VOICE = 3, CAT_MUSIC = 4, CAT_STICKERS = 5, CAT_OTHER = 6;
  private static final int CAT_COUNT = 7;
  private static final int[] CAT_COLORS = {0xff5ccb8a, 0xff3f8fe0, 0xff5ec6e8, 0xffa4d65e, 0xffa695e7, 0xfff0a35f, 0xff8d9aab};
  private static final int[] CAT_NAMES = {R.string.Tgx101CleanCatFiles, R.string.Tgx101CleanCatVideo, R.string.Tgx101CleanCatPhoto, R.string.Tgx101CleanTypeVoice, R.string.Tgx101CleanCatMusic, R.string.Tgx101CleanCatStickers, R.string.Tgx101CleanCatOther};
  private static final int[] TAB_NAMES = {R.string.Tgx101CleanTabAll, R.string.Tgx101CleanTabPrivate, R.string.Tgx101CleanTabGroups, R.string.Tgx101CleanTabChannels};
  private static final int TAB_COUNT = 4;

  // (user 2026-10-31 «прочее в кэше. мы можем раскрыть?»): what «Other» holds, each with its own size and check
  private static final int SUB_THUMBS = 0, SUB_AVATARS = 1, SUB_STORIES = 2, SUB_WALLPAPERS = 3, SUB_SOUNDS = 4, SUB_SECRET = 5, SUB_UNKNOWN = 6;
  private static final int SUB_COUNT = 7;
  private static final int[] SUB_NAMES = {R.string.Tgx101CleanSubThumbs, R.string.Tgx101CleanSubAvatars, R.string.Tgx101CleanSubStories, R.string.Tgx101CleanSubWallpapers, R.string.Tgx101CleanSubSounds, R.string.Tgx101CleanSubSecret, R.string.Tgx101CleanSubUnknown};

  private static int subOf (TdApi.FileType type) {
    switch (type.getConstructor()) {
      case TdApi.FileTypeThumbnail.CONSTRUCTOR: return SUB_THUMBS;
      case TdApi.FileTypeProfilePhoto.CONSTRUCTOR: return SUB_AVATARS;
      case TdApi.FileTypePhotoStory.CONSTRUCTOR:
      case TdApi.FileTypeVideoStory.CONSTRUCTOR: return SUB_STORIES;
      case TdApi.FileTypeWallpaper.CONSTRUCTOR: return SUB_WALLPAPERS;
      case TdApi.FileTypeNotificationSound.CONSTRUCTOR: return SUB_SOUNDS;
      case TdApi.FileTypeSecret.CONSTRUCTOR: return SUB_SECRET;
      default: return SUB_UNKNOWN;
    }
  }

  private static TdApi.FileType[] subTypes (int sub) {
    switch (sub) {
      case SUB_THUMBS: return new TdApi.FileType[] {new TdApi.FileTypeThumbnail()};
      case SUB_AVATARS: return new TdApi.FileType[] {new TdApi.FileTypeProfilePhoto()};
      case SUB_STORIES: return new TdApi.FileType[] {new TdApi.FileTypePhotoStory(), new TdApi.FileTypeVideoStory()};
      case SUB_WALLPAPERS: return new TdApi.FileType[] {new TdApi.FileTypeWallpaper()};
      case SUB_SOUNDS: return new TdApi.FileType[] {new TdApi.FileTypeNotificationSound()};
      case SUB_SECRET: return new TdApi.FileType[] {new TdApi.FileTypeSecret()};
      default: return new TdApi.FileType[] {new TdApi.FileTypeUnknown(), new TdApi.FileTypeNone()};
    }
  }

  private final boolean[] subChecked = {true, false, true, false, false, false, true};
  private long[][] subSizes; // [tab][sub]
  private boolean otherExpanded;

  public interface Callback {
    void onClear (TdApi.FileType[] types, @Nullable long[] chatIds, long size);
  }

  private final boolean[] checked = {true, true, true, false, true, false, true};
  private long[][] sizes; // [tab][cat]
  private final ArrayList<ArrayList<Long>> chatIds = new ArrayList<>(); // [tab - 1]
  private long deviceTotal;
  private int tab;
  private boolean busy;
  private @Nullable Callback callback;

  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();

  public Tgx101StorageOverview (Context context) {
    super(context);
    for (int i = 0; i < 3; i++) chatIds.add(new ArrayList<>());
  }

  public void setCallback (@Nullable Callback callback) {
    this.callback = callback;
  }

  public void setBusy (boolean busy) {
    this.busy = busy;
    invalidate();
  }

  public boolean isBusy () {
    return busy;
  }

  // Data

  public static int categoryOf (TdApi.FileType type) {
    switch (type.getConstructor()) {
      case TdApi.FileTypeDocument.CONSTRUCTOR: return CAT_FILES;
      case TdApi.FileTypeVideo.CONSTRUCTOR:
      case TdApi.FileTypeAnimation.CONSTRUCTOR: return CAT_VIDEO;
      case TdApi.FileTypePhoto.CONSTRUCTOR: return CAT_PHOTO;
      case TdApi.FileTypeVoiceNote.CONSTRUCTOR:
      case TdApi.FileTypeVideoNote.CONSTRUCTOR: return CAT_VOICE;
      case TdApi.FileTypeAudio.CONSTRUCTOR: return CAT_MUSIC;
      case TdApi.FileTypeSticker.CONSTRUCTOR: return CAT_STICKERS;
      case TdApi.FileTypeSecretThumbnail.CONSTRUCTOR: return -1; // can't be deleted
      default: return CAT_OTHER;
    }
  }

  private static TdApi.FileType[] typesOf (int cat) {
    switch (cat) {
      case CAT_FILES: return new TdApi.FileType[] {new TdApi.FileTypeDocument()};
      case CAT_VIDEO: return new TdApi.FileType[] {new TdApi.FileTypeVideo(), new TdApi.FileTypeAnimation()};
      case CAT_PHOTO: return new TdApi.FileType[] {new TdApi.FileTypePhoto()};
      case CAT_VOICE: return new TdApi.FileType[] {new TdApi.FileTypeVoiceNote(), new TdApi.FileTypeVideoNote()};
      case CAT_MUSIC: return new TdApi.FileType[] {new TdApi.FileTypeAudio()};
      case CAT_STICKERS: return new TdApi.FileType[] {new TdApi.FileTypeSticker()};
      default: return new TdApi.FileType[] {new TdApi.FileTypeThumbnail(), new TdApi.FileTypeProfilePhoto(), new TdApi.FileTypeWallpaper(), new TdApi.FileTypeUnknown(),
        new TdApi.FileTypeSecret(), new TdApi.FileTypePhotoStory(), new TdApi.FileTypeVideoStory(), new TdApi.FileTypeNotificationSound(), new TdApi.FileTypeNone()};
    }
  }

  /** Called on TDLib's thread with fresh statistics; draws on the UI thread */
  public void setStatistics (Tdlib tdlib, TdApi.StorageStatistics stats) {
    long[][] sizes = new long[TAB_COUNT][CAT_COUNT];
    long[][] subSizes = new long[TAB_COUNT][SUB_COUNT];
    ArrayList<ArrayList<Long>> ids = new ArrayList<>();
    for (int i = 0; i < 3; i++) ids.add(new ArrayList<>());
    for (TdApi.StorageStatisticsByChat chat : stats.byChat) {
      int kindTab = chat.chatId != 0 ? 1 + Tgx101AutoClean.kindOf(tdlib, chat.chatId) : 0;
      if (kindTab != 0) ids.get(kindTab - 1).add(chat.chatId);
      for (TdApi.StorageStatisticsByFileType byType : chat.byFileType) {
        int cat = categoryOf(byType.fileType);
        if (cat == -1) continue;
        sizes[0][cat] += byType.size;
        if (kindTab != 0) sizes[kindTab][cat] += byType.size;
        if (cat == CAT_OTHER) {
          int sub = subOf(byType.fileType);
          subSizes[0][sub] += byType.size;
          if (kindTab != 0) subSizes[kindTab][sub] += byType.size;
        }
      }
    }
    long deviceTotal = 0;
    try {
      StatFs fs = new StatFs(getContext().getFilesDir().getPath());
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
        deviceTotal = fs.getBlockCountLong() * fs.getBlockSizeLong();
      } else { // Android 4.1–4.2
        //noinspection deprecation
        deviceTotal = (long) fs.getBlockCount() * fs.getBlockSize();
      }
    } catch (Throwable ignored) { }
    final long total = deviceTotal;
    UI.post(() -> {
      this.sizes = sizes;
      this.subSizes = subSizes;
      this.chatIds.clear();
      this.chatIds.addAll(ids);
      this.deviceTotal = total;
      this.busy = false;
      requestLayout();
      invalidate();
    });
  }

  private long total () {
    long sum = 0;
    if (sizes != null) for (long s : sizes[tab]) sum += s;
    return sum;
  }

  private long selected () {
    long sum = 0;
    if (sizes != null) for (int i = 0; i < CAT_COUNT; i++) {
      if (i == CAT_OTHER && subSizes != null) {
        for (int k = 0; k < SUB_COUNT; k++) if (subChecked[k]) sum += subSizes[tab][k];
      } else if (checked[i]) {
        sum += sizes[tab][i];
      }
    }
    return sum;
  }

  // Layout

  private static int pad () { return Screen.dp(16f); }
  // (user 2026-10-31, mockup «Кэш вместо круга», variant 1): the total, a line under it, one stacked bar by type
  private static int totalBaseline () { return pad() + Screen.dp(22f); }
  private static int subBaseline () { return totalBaseline() + Screen.dp(20f); }
  private static int barTop () { return subBaseline() + Screen.dp(12f); }
  private static int barHeight () { return Screen.dp(14f); }
  private static int tabsTop () { return barTop() + barHeight() + Screen.dp(16f); }
  private static int tabsHeight () { return Screen.dp(36f); }
  private static int rowsTop () { return tabsTop() + tabsHeight() + Screen.dp(8f); }
  private static int rowHeight () { return Screen.dp(44f); }
  private static int otherHeight () { return Screen.dp(58f); } // the row with its parts on a second line
  private static int subHeight () { return Screen.dp(40f); }

  /** Parts of «Other» with something in them, biggest first */
  private int[] visibleSubs () {
    if (subSizes == null) return new int[0];
    Integer[] order = new Integer[SUB_COUNT];
    for (int i = 0; i < SUB_COUNT; i++) order[i] = i;
    final long[] sz = subSizes[tab];
    java.util.Arrays.sort(order, (a, b) -> Long.compare(sz[b], sz[a]));
    int n = 0;
    for (Integer i : order) if (sz[i] > 0) n++;
    int[] out = new int[n];
    for (int i = 0; i < n; i++) out[i] = order[i];
    return out;
  }

  private boolean hasSubs () {
    return visibleSubs().length > 0;
  }

  private int rowTopOf (int cat) {
    int y = rowsTop();
    for (int i = 0; i < cat; i++) y += i == CAT_OTHER && hasSubs() ? otherHeight() : rowHeight();
    return y;
  }

  private int rowsBottom () {
    int y = rowTopOf(CAT_COUNT - 1) + (CAT_COUNT - 1 == CAT_OTHER && hasSubs() ? otherHeight() : rowHeight());
    if (otherExpanded) y += visibleSubs().length * subHeight();
    return y;
  }

  /** where the parts of «Other» start (they come right after its row) */
  private int subsTop () {
    return rowTopOf(CAT_OTHER) + (hasSubs() ? otherHeight() : rowHeight());
  }

  private int catHeight (int cat) {
    return cat == CAT_OTHER && hasSubs() ? otherHeight() : rowHeight();
  }

  private int buttonTop () { return rowsBottom() + Screen.dp(8f); }
  private static int buttonHeight () { return Screen.dp(44f); }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), buttonTop() + buttonHeight() + pad());
  }

  @Override
  protected void onDraw (Canvas c) {
    int width = getMeasuredWidth();
    c.drawColor(Theme.getColor(ColorId.filling));
    int pad = pad();

    // total, the line under it and one bar by type
    long total = total();
    TextPaint big = Paints.getMediumTextPaint(24f, Theme.getColor(ColorId.text), false);
    TextPaint small = Paints.getRegularTextPaint(13f, Theme.getColor(ColorId.textLight));
    String totalText = sizes == null ? Lang.getString(R.string.Calculating) : Strings.buildSize(total);
    c.drawText(totalText, pad, totalBaseline(), big);
    String sub = tab == 0 && deviceTotal > 0 && total > 0 ? Lang.getString(R.string.Tgx101CleanOfDevice, Math.max(1, Math.round(100f * total / deviceTotal))) : Lang.getString(TAB_NAMES[tab]);
    c.drawText(TextUtils.ellipsize(sub, small, width - pad * 2, TextUtils.TruncateAt.END).toString(), pad, subBaseline(), small);
    int barTop = barTop(), barH = barHeight();
    float barW = width - pad * 2f;
    rect.set(pad, barTop, width - pad, barTop + barH);
    fillPaint.setColor(Theme.getColor(ColorId.background));
    c.drawRoundRect(rect, barH / 2f, barH / 2f, fillPaint);
    if (total > 0) {
      // pieces with round corners, no clipPath (Android 4.1–4.2 can't clip a path on a hardware canvas)
      float gap = Screen.dp(2f), x = pad;
      // the biggest type first, as the list's order on the mockup
      Integer[] order = new Integer[CAT_COUNT];
      for (int i = 0; i < CAT_COUNT; i++) order[i] = i;
      final long[] tabSizes = sizes[tab];
      java.util.Arrays.sort(order, (a, b) -> Long.compare(tabSizes[b], tabSizes[a]));
      for (int k = 0; k < CAT_COUNT; k++) {
        int i = order[k];
        if (tabSizes[i] <= 0) continue;
        float w = Math.max(Screen.dp(2f), barW * tabSizes[i] / total);
        fillPaint.setColor(CAT_COLORS[i]);
        fillPaint.setAlpha(checked[i] ? 255 : 90);
        rect.set(x, barTop, Math.min(width - pad, x + w - gap), barTop + barH);
        c.drawRoundRect(rect, Math.min(barH / 2f, rect.width() / 2f), Math.min(barH / 2f, rect.width() / 2f), fillPaint);
        x += w;
      }
      fillPaint.setAlpha(255);
    }

    // tabs
    int tabsTop = tabsTop(), tabsH = tabsHeight();
    rect.set(pad, tabsTop, width - pad, tabsTop + tabsH);
    fillPaint.setColor(Theme.getColor(ColorId.background));
    c.drawRoundRect(rect, Screen.dp(12f), Screen.dp(12f), fillPaint);
    float tabW = (width - pad * 2f) / TAB_COUNT;
    rect.set(pad + tabW * tab + Screen.dp(3f), tabsTop + Screen.dp(3f), pad + tabW * (tab + 1) - Screen.dp(3f), tabsTop + tabsH - Screen.dp(3f));
    fillPaint.setColor(Theme.getColor(ColorId.checkActive));
    c.drawRoundRect(rect, Screen.dp(9f), Screen.dp(9f), fillPaint);
    for (int i = 0; i < TAB_COUNT; i++) {
      TextPaint p = i == tab ? Paints.getMediumTextPaint(13f, Theme.getColor(ColorId.checkContent), false) : Paints.getRegularTextPaint(13f, Theme.getColor(ColorId.text));
      String name = TextUtils.ellipsize(Lang.getString(TAB_NAMES[i]), p, tabW - Screen.dp(8f), TextUtils.TruncateAt.END).toString();
      c.drawText(name, pad + tabW * i + (tabW - p.measureText(name)) / 2f, tabsTop + tabsH / 2f + Screen.dp(4.5f), p);
    }

    // rows
    TextPaint name = Paints.getRegularTextPaint(15f, Theme.getColor(ColorId.text));
    boolean subs = hasSubs();
    for (int i = 0; i < CAT_COUNT; i++) {
      int top = rowTopOf(i), h = catHeight(i);
      boolean other = i == CAT_OTHER && subs;
      float cy = other ? top + rowHeight() / 2f + Screen.dp(2f) : top + h / 2f;
      int state = other ? otherState() : checked[i] ? 2 : 0;
      boolean on = state != 0;
      drawCheck(c, pad, cy, CAT_COLORS[i], state);
      long catSize = sizes == null ? 0 : sizes[tab][i];
      String size = sizes == null ? "" : Strings.buildSize(catSize);
      TextPaint sizePaint = Paints.getRegularTextPaint(14f, Theme.getColor(on ? ColorId.checkActive : ColorId.textLight));
      float arrowW = other ? Screen.dp(28f) : 0;
      float sizeW = sizePaint.measureText(size);
      c.drawText(size, width - pad - arrowW - sizeW, cy + Screen.dp(5f), sizePaint);
      float nameX = pad + Screen.dp(20f) + Screen.dp(14f);
      c.drawText(TextUtils.ellipsize(Lang.getString(CAT_NAMES[i]), name, width - pad - arrowW - sizeW - Screen.dp(12f) - nameX, TextUtils.TruncateAt.END).toString(), nameX, cy + Screen.dp(5f), name);
      if (other) {
        // ▾ / ▴ — opens what «Other» holds; folded, its three biggest parts on a second line
        Paint arrow = Paints.getProgressPaint(Theme.getColor(ColorId.textLight), Screen.dp(2f));
        float ax = width - pad - Screen.dp(9f), ay = cy, d = Screen.dp(5f);
        float dir = otherExpanded ? -1 : 1;
        c.drawLine(ax - d, ay - d * .5f * dir, ax, ay + d * .5f * dir, arrow);
        c.drawLine(ax, ay + d * .5f * dir, ax + d, ay - d * .5f * dir, arrow);
        if (!otherExpanded) {
          StringBuilder line = new StringBuilder();
          int[] vis = visibleSubs();
          for (int k = 0; k < Math.min(3, vis.length); k++) {
            if (line.length() > 0) line.append(" · ");
            line.append(Lang.getString(SUB_NAMES[vis[k]]).toLowerCase()).append(' ').append(Strings.buildSize(subSizes[tab][vis[k]]));
          }
          TextPaint hint = Paints.getRegularTextPaint(12.5f, Theme.getColor(ColorId.textLight));
          c.drawText(TextUtils.ellipsize(line, hint, width - pad - nameX, TextUtils.TruncateAt.END).toString(), nameX, cy + Screen.dp(24f), hint);
        }
      }
    }
    if (otherExpanded && subs) {
      int[] vis = visibleSubs();
      int top = subsTop();
      TextPaint subName = Paints.getRegularTextPaint(14f, Theme.getColor(ColorId.text));
      float x0 = pad + Screen.dp(34f);
      for (int k = 0; k < vis.length; k++) {
        int part = vis[k];
        float cy = top + subHeight() * k + subHeight() / 2f;
        drawCheck(c, (int) x0, cy, CAT_COLORS[CAT_OTHER], subChecked[part] ? 2 : 0);
        String size = Strings.buildSize(subSizes[tab][part]);
        TextPaint sizePaint = Paints.getRegularTextPaint(13f, Theme.getColor(subChecked[part] ? ColorId.checkActive : ColorId.textLight));
        float sizeW = sizePaint.measureText(size);
        c.drawText(size, width - pad - Screen.dp(28f) - sizeW, cy + Screen.dp(5f), sizePaint);
        float nameX = x0 + Screen.dp(20f) + Screen.dp(14f);
        c.drawText(TextUtils.ellipsize(Lang.getString(SUB_NAMES[part]), subName, width - pad - Screen.dp(28f) - sizeW - Screen.dp(12f) - nameX, TextUtils.TruncateAt.END).toString(), nameX, cy + Screen.dp(5f), subName);
      }
    }

    // button
    int bTop = buttonTop(), bH = buttonHeight();
    long selected = selected();
    boolean enabled = selected > 0 && !busy;
    rect.set(pad, bTop, width - pad, bTop + bH);
    fillPaint.setColor(Theme.getColor(ColorId.checkActive));
    fillPaint.setAlpha(enabled ? 255 : 90);
    c.drawRoundRect(rect, bH / 2f, bH / 2f, fillPaint);
    fillPaint.setAlpha(255);
    String label = busy ? Lang.getString(R.string.CleaningUp) : Lang.getString(R.string.Tgx101CleanSelected, Strings.buildSize(selected));
    TextPaint bp = Paints.getMediumTextPaint(15f, Theme.getColor(ColorId.checkContent), false);
    c.drawText(label, (width - bp.measureText(label)) / 2f, bTop + bH / 2f + Screen.dp(5f), bp);
  }

  /** 0 — none, 1 — some (a dash), 2 — all */
  private int otherState () {
    int[] vis = visibleSubs();
    int on = 0;
    for (int sub : vis) if (subChecked[sub]) on++;
    return on == 0 ? 0 : on == vis.length ? 2 : 1;
  }

  private void drawCheck (Canvas c, int left, float cy, int color, int state) {
    float box = Screen.dp(20f);
    rect.set(left, cy - box / 2f, left + box, cy + box / 2f);
    if (state != 0) {
      fillPaint.setColor(color);
      c.drawRoundRect(rect, Screen.dp(5f), Screen.dp(5f), fillPaint);
      Paint check = Paints.getProgressPaint(0xff0b1620, Screen.dp(2f));
      if (state == 2) {
        c.drawLine(rect.left + box * .25f, cy, rect.left + box * .43f, cy + box * .18f, check);
        c.drawLine(rect.left + box * .43f, cy + box * .18f, rect.left + box * .76f, cy - box * .2f, check);
      } else {
        c.drawLine(rect.left + box * .28f, cy, rect.left + box * .72f, cy, check);
      }
    } else {
      Paint border = Paints.getProgressPaint(color, Screen.dp(2f));
      rect.inset(Screen.dp(1f), Screen.dp(1f));
      c.drawRoundRect(rect, Screen.dp(4f), Screen.dp(4f), border);
    }
  }

  // Touch

  private float downX, downY;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downX = e.getX();
        downY = e.getY();
        return true;
      case MotionEvent.ACTION_UP:
        if (Math.hypot(e.getX() - downX, e.getY() - downY) > Screen.getTouchSlop()) return true;
        onTap(e.getX(), e.getY());
        return true;
    }
    return true;
  }

  private void onTap (float x, float y) {
    int width = getMeasuredWidth(), pad = pad();
    if (y >= tabsTop() && y < tabsTop() + tabsHeight() && x >= pad && x < width - pad) {
      int t = (int) ((x - pad) / ((width - pad * 2f) / TAB_COUNT));
      if (t != tab && t >= 0 && t < TAB_COUNT) {
        tab = t;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        requestLayout();
        invalidate();
      }
    } else if (y >= rowsTop() && y < rowsBottom()) {
      boolean subs = hasSubs();
      if (otherExpanded && subs && y >= subsTop()) {
        int[] vis = visibleSubs();
        int k = (int) ((y - subsTop()) / subHeight());
        if (k >= 0 && k < vis.length) subChecked[vis[k]] = !subChecked[vis[k]];
      } else {
        int i = CAT_COUNT - 1;
        for (int c = 0; c < CAT_COUNT; c++) if (y < rowTopOf(c) + catHeight(c)) { i = c; break; }
        if (i == CAT_OTHER && subs) {
          if (x > width - pad - Screen.dp(48f)) {
            otherExpanded = !otherExpanded; // the arrow
            requestLayout();
          } else {
            boolean all = otherState() != 2;
            for (int sub : visibleSubs()) subChecked[sub] = all;
            checked[CAT_OTHER] = all;
          }
        } else {
          checked[i] = !checked[i];
        }
      }
      performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
      invalidate();
    } else if (y >= buttonTop() && y < buttonTop() + buttonHeight() && !busy && sizes != null && callback != null) {
      long selected = selected();
      if (selected == 0) return;
      ArrayList<TdApi.FileType> types = new ArrayList<>();
      for (int i = 0; i < CAT_COUNT; i++) {
        if (i == CAT_OTHER && hasSubs()) {
          for (int sub = 0; sub < SUB_COUNT; sub++) if (subChecked[sub]) for (TdApi.FileType t : subTypes(sub)) types.add(t);
        } else if (checked[i]) {
          for (TdApi.FileType t : typesOf(i)) types.add(t);
        }
      }
      long[] ids = null;
      if (tab != 0) {
        ArrayList<Long> list = chatIds.get(tab - 1);
        if (list.isEmpty()) return; // an empty list would mean every chat
        ids = new long[list.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = list.get(i);
      }
      callback.onClear(types.toArray(new TdApi.FileType[0]), ids, selected);
    }
  }
}
