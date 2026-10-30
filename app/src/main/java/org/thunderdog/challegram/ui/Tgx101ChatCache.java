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

import android.util.SparseIntArray;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Tgx101AutoClean;
import org.thunderdog.challegram.loader.ImageLoader;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;

/**
 * TGx101 (approved mockup «Кэш» 3, user 2026-10-30): a chat's own cache, from its profile's ⋮ → «Cache» — what of this
 * chat lies in the cache by type, clearing only this chat, and leaving it out of the auto clean.
 */
public final class Tgx101ChatCache {
  private Tgx101ChatCache () { }

  private static final class Group {
    final int id, pluralRes;
    final boolean byDefault;
    final ArrayList<TdApi.FileType> types = new ArrayList<>();
    long size;
    int count;

    Group (int id, int pluralRes, boolean byDefault) {
      this.id = id;
      this.pluralRes = pluralRes;
      this.byDefault = byDefault;
    }
  }

  private static Group groupOf (ArrayList<Group> groups, TdApi.FileType type) {
    int id, plural;
    boolean byDefault = true;
    switch (type.getConstructor()) {
      case TdApi.FileTypePhoto.CONSTRUCTOR: id = R.id.btn_photos; plural = R.string.xPhotos; break;
      case TdApi.FileTypeVideo.CONSTRUCTOR: id = R.id.btn_video; plural = R.string.xVideos; break;
      case TdApi.FileTypeAnimation.CONSTRUCTOR: id = R.id.btn_gifs; plural = R.string.xGIFs; break;
      case TdApi.FileTypeDocument.CONSTRUCTOR: id = R.id.btn_files; plural = R.string.xFiles; break;
      case TdApi.FileTypeAudio.CONSTRUCTOR: id = R.id.btn_music; plural = R.string.xMusicFiles; break;
      case TdApi.FileTypeVoiceNote.CONSTRUCTOR: id = R.id.btn_voice; plural = R.string.xVoiceMessages; break;
      case TdApi.FileTypeVideoNote.CONSTRUCTOR: id = R.id.btn_videoNote; plural = R.string.xVideoMessages; break;
      case TdApi.FileTypeSticker.CONSTRUCTOR: id = R.id.btn_stickers; plural = R.string.xStickers; byDefault = false; break;
      case TdApi.FileTypeProfilePhoto.CONSTRUCTOR: id = R.id.btn_profilePhotos; plural = R.string.xProfilePhotos; byDefault = false; break;
      case TdApi.FileTypeSecretThumbnail.CONSTRUCTOR: return null; // can't be deleted
      default: id = R.id.btn_otherFiles; plural = 0; byDefault = false; break;
    }
    for (Group g : groups) if (g.id == id) return g;
    Group g = new Group(id, plural, byDefault);
    groups.add(g);
    return g;
  }

  public static void show (ViewController<?> c, long chatId) {
    c.tdlib().send(new TdApi.GetStorageStatistics(100000), (stats, error) -> {
      ArrayList<Group> groups = new ArrayList<>();
      long total = 0;
      if (stats != null) {
        for (TdApi.StorageStatisticsByChat chat : stats.byChat) {
          if (chat.chatId != chatId) continue;
          for (TdApi.StorageStatisticsByFileType byType : chat.byFileType) {
            if (byType.size == 0) continue;
            Group g = groupOf(groups, byType.fileType);
            if (g == null) continue;
            g.types.add(byType.fileType);
            g.size += byType.size;
            g.count += byType.count;
            total += byType.size;
          }
        }
      }
      final long totalSize = total;
      c.runOnUiThreadOptional(() -> showSheet(c, chatId, groups, totalSize));
    });
  }

  private static void showSheet (ViewController<?> c, long chatId, ArrayList<Group> groups, long total) {
    java.util.Collections.sort(groups, (a, b) -> a.byDefault != b.byDefault ? (a.byDefault ? -1 : 1) : Long.compare(b.size, a.size));
    ArrayList<ListItem> items = new ArrayList<>();
    long selected = 0;
    for (Group g : groups) {
      String name = g.pluralRes != 0 ? Lang.plural(g.pluralRes, g.count) : Lang.getString(R.string.Other);
      ListItem item = new ListItem(ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE, g.id, 0, name, g.byDefault);
      item.setStringValue(Strings.buildSize(g.size));
      items.add(item);
      if (g.byDefault) selected += g.size;
    }
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_tgx101CleanKeep, 0, R.string.Tgx101CleanKeep, Tgx101AutoClean.isKept(chatId)));
    String header = total > 0 ? Lang.getString(R.string.Tgx101ChatCacheTitle, Strings.buildSize(total)) : Lang.getString(R.string.Tgx101ChatCacheEmpty);
    c.showSettings(new SettingsWrapBuilder(R.id.more_btn_tgx101Cache)
      .addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, header, false))
      .setRawItems(items)
      .setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter, window) -> doneButton.setText(Lang.uppercase(saveText(groups, settingsAdapter.getCheckIntResults()))))
      .setIntDelegate((id, result) -> apply(c, chatId, groups, result))
      .setSaveStr(selected > 0 ? Lang.getString(R.string.ClearX, Strings.buildSize(selected, false)) : Lang.getString(R.string.Tgx101ChatCacheSave))
      .setSaveColorId(selected > 0 ? ColorId.textNegative : ColorId.textNeutral)
      .setAllowResize(items.size() >= 6));
  }

  private static boolean checked (SparseIntArray result, int id) {
    return result.get(id) != 0;
  }

  private static String saveText (ArrayList<Group> groups, SparseIntArray result) {
    long size = 0;
    for (Group g : groups) if (checked(result, g.id)) size += g.size;
    return size > 0 ? Lang.getString(R.string.ClearX, Strings.buildSize(size, false)) : Lang.getString(R.string.Tgx101ChatCacheSave);
  }

  private static void apply (ViewController<?> c, long chatId, ArrayList<Group> groups, SparseIntArray result) {
    Tgx101AutoClean.setKept(chatId, checked(result, R.id.btn_tgx101CleanKeep));
    ArrayList<TdApi.FileType> types = new ArrayList<>();
    long size = 0;
    for (Group g : groups) {
      if (checked(result, g.id)) {
        types.addAll(g.types);
        size += g.size;
      }
    }
    if (types.isEmpty()) return;
    final long freed = size;
    ImageLoader.instance().clear(c.tdlib().id(), false);
    c.tdlib().send(new TdApi.OptimizeStorage(0, 0, 0, 0, types.toArray(new TdApi.FileType[0]), new long[] {chatId}, null, true, 0), (deleted, error) -> {
      Tgx101Diag.mark("chat cache: chat " + chatId + " cleared " + (error != null ? "error " + error.message : Strings.buildSize(deleted != null ? deleted.size : 0)));
      if (error == null) UI.showToast(Lang.getString(R.string.Tgx101ChatCacheCleared, Strings.buildSize(deleted != null && deleted.size > 0 ? deleted.size : freed)), Toast.LENGTH_SHORT);
    });
  }
}
