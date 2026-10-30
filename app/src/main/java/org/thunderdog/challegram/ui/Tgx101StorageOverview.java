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
 * TGx101 (approved mockup «Кэш» 1, user 2026-10-30): the top of Storage — a ring of the cache by type, tabs All /
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

  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();

  public Tgx101StorageOverview (Context context) {
    super(context);
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setStrokeWidth(Screen.dp(10f));
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
      this.chatIds.clear();
      this.chatIds.addAll(ids);
      this.deviceTotal = total;
      this.busy = false;
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
    if (sizes != null) for (int i = 0; i < CAT_COUNT; i++) if (checked[i]) sum += sizes[tab][i];
    return sum;
  }

  // Layout

  private static int pad () { return Screen.dp(16f); }
  private static int ringSize () { return Screen.dp(76f); }
  private static int tabsTop () { return pad() + ringSize() + Screen.dp(14f); }
  private static int tabsHeight () { return Screen.dp(36f); }
  private static int rowsTop () { return tabsTop() + tabsHeight() + Screen.dp(8f); }
  private static int rowHeight () { return Screen.dp(44f); }
  private int buttonTop () { return rowsTop() + rowHeight() * CAT_COUNT + Screen.dp(8f); }
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

    // ring
    float stroke = Screen.dp(10f);
    rect.set(pad + stroke / 2f, pad + stroke / 2f, pad + ringSize() - stroke / 2f, pad + ringSize() - stroke / 2f);
    ringPaint.setColor(Theme.getColor(ColorId.background));
    c.drawArc(rect, 0, 360, false, ringPaint);
    long total = total();
    if (total > 0) {
      float angle = -90f;
      for (int i = 0; i < CAT_COUNT; i++) {
        float sweep = 360f * sizes[tab][i] / total;
        if (sweep <= 0) continue;
        ringPaint.setColor(CAT_COLORS[i]);
        c.drawArc(rect, angle, Math.max(.5f, sweep - (sweep > 3 ? 1.5f : 0)), false, ringPaint);
        angle += sweep;
      }
    }
    int textX = pad + ringSize() + Screen.dp(16f);
    TextPaint big = Paints.getMediumTextPaint(22f, Theme.getColor(ColorId.text), false);
    TextPaint small = Paints.getRegularTextPaint(13f, Theme.getColor(ColorId.textLight));
    String totalText = sizes == null ? Lang.getString(R.string.Calculating) : Strings.buildSize(total);
    c.drawText(totalText, textX, pad + ringSize() / 2f - Screen.dp(2f), big);
    String sub = tab == 0 && deviceTotal > 0 && total > 0 ? Lang.getString(R.string.Tgx101CleanOfDevice, Math.max(1, Math.round(100f * total / deviceTotal))) : Lang.getString(TAB_NAMES[tab]);
    c.drawText(TextUtils.ellipsize(sub, small, width - textX - pad, TextUtils.TruncateAt.END).toString(), textX, pad + ringSize() / 2f + Screen.dp(18f), small);

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
    int rowTop = rowsTop(), rowH = rowHeight();
    TextPaint name = Paints.getRegularTextPaint(15f, Theme.getColor(ColorId.text));
    for (int i = 0; i < CAT_COUNT; i++) {
      float cy = rowTop + rowH * i + rowH / 2f;
      float box = Screen.dp(20f);
      rect.set(pad, cy - box / 2f, pad + box, cy + box / 2f);
      if (checked[i]) {
        fillPaint.setColor(CAT_COLORS[i]);
        c.drawRoundRect(rect, Screen.dp(5f), Screen.dp(5f), fillPaint);
        Paint check = Paints.getProgressPaint(0xff0b1620, Screen.dp(2f));
        c.drawLine(rect.left + box * .25f, cy, rect.left + box * .43f, cy + box * .18f, check);
        c.drawLine(rect.left + box * .43f, cy + box * .18f, rect.left + box * .76f, cy - box * .2f, check);
      } else {
        Paint border = Paints.getProgressPaint(CAT_COLORS[i], Screen.dp(2f));
        rect.inset(Screen.dp(1f), Screen.dp(1f));
        c.drawRoundRect(rect, Screen.dp(4f), Screen.dp(4f), border);
      }
      String size = sizes == null ? "" : Strings.buildSize(sizes[tab][i]);
      TextPaint sizePaint = Paints.getRegularTextPaint(14f, Theme.getColor(checked[i] ? ColorId.checkActive : ColorId.textLight));
      float sizeW = sizePaint.measureText(size);
      c.drawText(size, width - pad - sizeW, cy + Screen.dp(5f), sizePaint);
      float nameX = pad + box + Screen.dp(14f);
      c.drawText(TextUtils.ellipsize(Lang.getString(CAT_NAMES[i]), name, width - pad - sizeW - Screen.dp(12f) - nameX, TextUtils.TruncateAt.END).toString(), nameX, cy + Screen.dp(5f), name);
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
        invalidate();
      }
    } else if (y >= rowsTop() && y < rowsTop() + rowHeight() * CAT_COUNT) {
      int i = (int) ((y - rowsTop()) / rowHeight());
      checked[i] = !checked[i];
      performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
      invalidate();
    } else if (y >= buttonTop() && y < buttonTop() + buttonHeight() && !busy && sizes != null && callback != null) {
      long selected = selected();
      if (selected == 0) return;
      ArrayList<TdApi.FileType> types = new ArrayList<>();
      for (int i = 0; i < CAT_COUNT; i++) if (checked[i]) for (TdApi.FileType t : typesOf(i)) types.add(t);
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
