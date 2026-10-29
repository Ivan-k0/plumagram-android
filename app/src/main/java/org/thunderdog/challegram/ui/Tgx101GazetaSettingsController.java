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
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * TGx101 «Газета» settings (user 2026-10-27). The look belongs to the profile switched on by ☀/☾ in the feed's header
 * (light or dark): font size, colour of the posts, photos as is or a thumbnail, pictures' brightness and saturation,
 * how long posts fold. For both: reactions, stars, the bird, the archived channels, and every subscribed channel
 * with a tick — «Select all» / «Deselect all» on top of the list.
 */
public class Tgx101GazetaSettingsController extends RecyclerViewController<Void> implements View.OnClickListener {
  public Tgx101GazetaSettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private @androidx.annotation.Nullable Tgx101GazetaController feed;

  public Tgx101GazetaSettingsController setFeed (Tgx101GazetaController feed) {
    this.feed = feed;
    return this;
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101GazetaSettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101GazetaSettings);
  }

  private SettingsAdapter adapter;
  private long[] channels = new long[0];

  private static int[] paletteNames () {
    return Tgx101Gazeta.profile() == Tgx101Gazeta.PROFILE_DARK ? Tgx101Gazeta.DARK_PALETTE_NAMES : Tgx101Gazeta.LIGHT_PALETTE_NAMES;
  }

  private static String foldName (int fold) {
    return fold == Tgx101Gazeta.FOLD_NONE ? Lang.getString(R.string.Tgx101GazetaFoldFull) : Lang.getString(R.string.Tgx101GazetaFoldLines, 9);
  }

  private static String pictureName () {
    int b = Tgx101Gazeta.brightness(), s = Tgx101Gazeta.saturation();
    for (int i = 0; i < Tgx101Gazeta.PICTURE_PRESETS.length; i++) {
      if (Tgx101Gazeta.PICTURE_PRESETS[i][0] == b && Tgx101Gazeta.PICTURE_PRESETS[i][1] == s) return Lang.getString(Tgx101Gazeta.PICTURE_PRESET_NAMES[i]);
    }
    return Lang.getString(R.string.Tgx101GazetaPicCustom, b, s);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        if (id == R.id.btn_tgx101GazetaColor) {
          view.setData(Lang.getString(paletteNames()[Tgx101Gazeta.paletteIndex()]));
        } else if (id == R.id.btn_tgx101GazetaPhotos) {
          view.setData(Lang.getString(Tgx101Gazeta.photos() == Tgx101Gazeta.PHOTOS_THUMB ? R.string.Tgx101GazetaPhotosThumb : Tgx101Gazeta.photos() == Tgx101Gazeta.PHOTOS_PAPER ? R.string.Tgx101GazetaPhotosPaper : R.string.Tgx101GazetaPhotosAsIs));
        } else if (id == R.id.btn_tgx101GazetaPicture) {
          view.setData(pictureName());
        } else if (id == R.id.btn_tgx101GazetaFold) {
          view.setData(foldName(Tgx101Gazeta.fold()));
        } else if (id == R.id.btn_tgx101GazetaReactions) {
          view.getToggler().setRadioEnabled(Tgx101Gazeta.reactions(), isUpdate);
        } else if (id == R.id.btn_tgx101GazetaStars) {
          view.getToggler().setRadioEnabled(Tgx101Gazeta.stars(), isUpdate);
        } else if (id == R.id.btn_tgx101GazetaComments) {
          view.getToggler().setRadioEnabled(Tgx101Gazeta.comments(), isUpdate);
        } else if (id == R.id.btn_tgx101GazetaBird) {
          view.getToggler().setRadioEnabled(Tgx101Gazeta.bird(), isUpdate);
        } else if (id == R.id.btn_tgx101GazetaArchive) {
          view.getToggler().setRadioEnabled(Tgx101Gazeta.withArchive(tdlib), isUpdate);
        }
      }
    };
    recyclerView.setAdapter(adapter);
    build();
  }

  private void build () {
    Tgx101Gazeta.allChannels(tdlib, Tgx101Gazeta.withArchive(tdlib), all -> {
      if (isDestroyed()) return;
      channels = all;
      Set<Long> off = Tgx101Gazeta.excluded(tdlib);
      List<ListItem> items = new ArrayList<>();
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101GazetaLook, R.drawable.baseline_palette_24, R.string.Tgx101GazetaLook));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101GazetaLookHint));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101GazetaReactions, 0, R.string.Tgx101GazetaReactions));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101GazetaStars, 0, R.string.Tgx101GazetaStars));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101GazetaComments, 0, R.string.Tgx101GazetaComments));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101GazetaBird, 0, R.string.Tgx101GazetaBird));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101GazetaArchive, 0, R.string.Tgx101GazetaArchive));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      java.util.List<Tgx101Gazeta.Topic> topics = Tgx101Gazeta.topics(tdlib);
      if (!topics.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101GazetaTopics));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        for (int i = 0; i < topics.size(); i++) {
          if (i > 0) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101GazetaTopic, R.drawable.baseline_close_24, topics.get(i).title).setIntValue(i));
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101GazetaTopicsHint));
      }
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, Lang.getString(R.string.Tgx101GazetaChannelsN, all.length)));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101GazetaSelectAll, R.drawable.baseline_done_all_24, R.string.Tgx101GazetaSelectAll));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101GazetaSelectNone, R.drawable.baseline_remove_circle_24, R.string.Tgx101GazetaSelectNone));
      for (long chatId : all) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR, R.id.btn_tgx101GazetaChannel, 0, tdlib.chatTitle(chatId), !off.contains(chatId)).setLongId(chatId).setLongValue(chatId));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      adapter.setItems(items, false);
    });
  }

  private void setAll (boolean on) {
    Set<Long> off = Tgx101Gazeta.excluded(tdlib);
    for (long id : channels) {
      if (on) off.remove(id);
      else off.add(id);
    }
    Tgx101Gazeta.setExcluded(tdlib, off);
    for (ListItem item : adapter.getItems()) {
      if (item.getId() == R.id.btn_tgx101GazetaChannel) item.setSelected(on);
    }
    adapter.notifyDataSetChanged();
  }

  private interface Choice {
    void onChoice (int which);
  }

  private void choose (int titleRes, String[] names, int current, Choice choice) {
    int[] ids = new int[names.length], icons = new int[names.length];
    for (int i = 0; i < names.length; i++) {
      ids[i] = i + 1;
      icons[i] = i == current ? R.drawable.baseline_check_24 : 0;
    }
    showOptions(Lang.getString(titleRes), ids, names, null, icons, (v, id) -> {
      choice.onChoice(id - 1);
      return true;
    });
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_tgx101GazetaLook) {
      navigateTo(new Tgx101GazetaLookController(context, tdlib, feed));
    } else if (id == R.id.btn_tgx101GazetaColor) {
      int[] res = paletteNames();
      String[] names = new String[res.length];
      for (int i = 0; i < res.length; i++) names[i] = Lang.getString(res[i]);
      choose(R.string.Tgx101GazetaColor, names, Tgx101Gazeta.paletteIndex(), which -> {
        Tgx101Gazeta.setPaletteIndex(which);
        adapter.updateValuedSettingById(id);
      });
    } else if (id == R.id.btn_tgx101GazetaPhotos) {
      choose(R.string.Tgx101GazetaPhotos, new String[] {Lang.getString(R.string.Tgx101GazetaPhotosAsIs), Lang.getString(R.string.Tgx101GazetaPhotosThumb), Lang.getString(R.string.Tgx101GazetaPhotosPaper)}, Tgx101Gazeta.photos(), which -> {
        Tgx101Gazeta.setPhotos(which == 2 ? Tgx101Gazeta.PHOTOS_PAPER : which == 1 ? Tgx101Gazeta.PHOTOS_THUMB : Tgx101Gazeta.PHOTOS_AS_IS);
        adapter.updateValuedSettingById(id);
      });
    } else if (id == R.id.btn_tgx101GazetaPicture) {
      // quick sets first, then brightness and saturation one by one
      int n = Tgx101Gazeta.PICTURE_PRESETS.length;
      String[] names = new String[n + 2];
      int current = -1;
      for (int i = 0; i < n; i++) {
        names[i] = Lang.getString(Tgx101Gazeta.PICTURE_PRESET_NAMES[i]);
        if (Tgx101Gazeta.PICTURE_PRESETS[i][0] == Tgx101Gazeta.brightness() && Tgx101Gazeta.PICTURE_PRESETS[i][1] == Tgx101Gazeta.saturation()) current = i;
      }
      names[n] = Lang.getString(R.string.Tgx101GazetaBrightness) + ": " + Tgx101Gazeta.brightness() + "%";
      names[n + 1] = Lang.getString(R.string.Tgx101GazetaSaturation) + ": " + Tgx101Gazeta.saturation() + "%";
      choose(R.string.Tgx101GazetaPicture, names, current, which -> {
        if (which < n) {
          Tgx101Gazeta.setPicture(Tgx101Gazeta.PICTURE_PRESETS[which][0], Tgx101Gazeta.PICTURE_PRESETS[which][1]);
          adapter.updateValuedSettingById(id);
        } else {
          boolean bright = which == n;
          int[] values = bright ? new int[] {20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120} : new int[] {0, 25, 50, 75, 100, 125, 150};
          String[] labels = new String[values.length];
          int now = bright ? Tgx101Gazeta.brightness() : Tgx101Gazeta.saturation(), at = -1;
          for (int i = 0; i < values.length; i++) {
            labels[i] = values[i] + "%";
            if (values[i] == now) at = i;
          }
          choose(bright ? R.string.Tgx101GazetaBrightness : R.string.Tgx101GazetaSaturation, labels, at, w -> {
            if (bright) Tgx101Gazeta.setPicture(values[w], Tgx101Gazeta.saturation());
            else Tgx101Gazeta.setPicture(Tgx101Gazeta.brightness(), values[w]);
            adapter.updateValuedSettingById(id);
          });
        }
      });
    } else if (id == R.id.btn_tgx101GazetaFold) {
      int[] folds = {Tgx101Gazeta.FOLD_LINES, Tgx101Gazeta.FOLD_NONE};
      String[] names = new String[folds.length];
      int current = -1;
      for (int i = 0; i < folds.length; i++) {
        names[i] = foldName(folds[i]);
        if (folds[i] == Tgx101Gazeta.fold()) current = i;
      }
      choose(R.string.Tgx101GazetaFold, names, current, which -> {
        Tgx101Gazeta.setFold(folds[which]);
        adapter.updateValuedSettingById(id);
      });
    } else if (id == R.id.btn_tgx101GazetaReactions) {
      Tgx101Gazeta.setReactions(adapter.toggleView(v));
    } else if (id == R.id.btn_tgx101GazetaStars) {
      Tgx101Gazeta.setStars(adapter.toggleView(v));
    } else if (id == R.id.btn_tgx101GazetaComments) {
      Tgx101Gazeta.setComments(adapter.toggleView(v));
    } else if (id == R.id.btn_tgx101GazetaBird) {
      Tgx101Gazeta.setBird(adapter.toggleView(v));
    } else if (id == R.id.btn_tgx101GazetaArchive) {
      Tgx101Gazeta.setWithArchive(tdlib, adapter.toggleView(v));
      build();
    } else if (id == R.id.btn_tgx101GazetaTopic) {
      ListItem item = (ListItem) v.getTag();
      Tgx101Gazeta.removeTopic(tdlib, item.getIntValue());
      build();
    } else if (id == R.id.btn_tgx101GazetaSelectAll) {
      setAll(true);
    } else if (id == R.id.btn_tgx101GazetaSelectNone) {
      setAll(false);
    } else if (id == R.id.btn_tgx101GazetaChannel) {
      ListItem item = (ListItem) v.getTag();
      boolean on = adapter.toggleView(v);
      Set<Long> off = Tgx101Gazeta.excluded(tdlib);
      if (on) off.remove(item.getLongId());
      else off.add(item.getLongId());
      Tgx101Gazeta.setExcluded(tdlib, off);
    }
  }
}
