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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;
import tgx.td.Td;

/**
 * TGx101 (approved mockup «Профиль и Настройки» Я3, user 2026-10-26): «Edit» — the account screen. Your name, about,
 * phone, @username, birthday, name colour; add an account, log out. Everything that hung under the big photo in Settings.
 */
public class Tgx101AccountController extends RecyclerViewController<Void> implements View.OnClickListener, TdlibCache.MyUserDataChangeListener, TdlibCache.UserDataChangeListener {
  public Tgx101AccountController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101Account;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101Account);
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        TdApi.User me = tdlib.myUser();
        TdApi.UserFullInfo full = tdlib.myUserFull();
        int id = item.getId();
        if (id == R.id.btn_tgx101EditName) {
          view.setData(me != null ? TD.getUserName(me) : Lang.getString(R.string.LoadingInformation));
        } else if (id == R.id.btn_bio) {
          String bio = full != null && full.bio != null ? full.bio.text : null;
          view.setData(StringUtils.isEmpty(bio) ? Lang.getString(R.string.BioNone) : bio);
        } else if (id == R.id.btn_phone) {
          view.setData(me != null && !StringUtils.isEmpty(me.phoneNumber) ? Strings.formatPhone(me.phoneNumber) : Lang.getString(R.string.LoadingInformation));
        } else if (id == R.id.btn_username) {
          view.setData(me != null && Td.hasUsername(me) ? "@" + Td.primaryUsername(me) : Lang.getString(R.string.SetUpUsername));
        } else if (id == R.id.btn_birthdate) {
          view.setData(full != null && full.birthdate != null ? Lang.getBirthdate(full.birthdate, true, true) : Lang.getString(R.string.SetBirthdate));
        } else if (id == R.id.btn_tgx101NameColor) {
          view.setData(SettingsController.tgx101NameColorValue(me));
        }
      }
    };
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101YourName));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_tgx101EditName, R.drawable.baseline_person_24, R.string.Tgx101YourName));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_bio, R.drawable.baseline_info_24, R.string.UserBio));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101BioHint));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101AboutYou));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_phone, R.drawable.baseline_phone_24, R.string.Phone));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_username, R.drawable.baseline_alternate_email_24, R.string.Username));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_birthdate, R.drawable.baseline_cake_variant_24, R.string.Birthdate));
    if (tdlib.hasPremium()) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      items.add(new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_tgx101NameColor, R.drawable.baseline_palette_24, R.string.Tgx101NameColor));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_addAccount, R.drawable.baseline_add_24, R.string.AddAccount).setTextColorId(org.thunderdog.challegram.theme.ColorId.textNeutral));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_logout, R.drawable.baseline_logout_24, R.string.LogOut).setTextColorId(org.thunderdog.challegram.theme.ColorId.textNegative));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
    tdlib.cache().addMyUserListener(this);
    tdlib.cache().addUserDataListener(tdlib.myUserId(), this);
  }

  private void refresh () {
    runOnUiThreadOptional(() -> {
      for (int id : new int[] {R.id.btn_tgx101EditName, R.id.btn_bio, R.id.btn_phone, R.id.btn_username, R.id.btn_birthdate, R.id.btn_tgx101NameColor}) {
        adapter.updateValuedSettingById(id);
      }
    });
  }

  @Override
  public void onMyUserUpdated (TdApi.User myUser) {
    if (myUser != null) refresh();
  }

  @Override
  public void onMyUserBioUpdated (@Nullable TdApi.FormattedText newBio) {
    refresh();
  }

  @Override
  public void onUserFullUpdated (long userId, TdApi.UserFullInfo userFull) {
    refresh();
  }

  @Override
  public void onFocus () {
    super.onFocus();
    refresh();
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (tdlib.ui().handleProfileClick(this, v, id, tdlib.myUser(), true)) {
      return;
    }
    if (id == R.id.btn_tgx101EditName) {
      EditNameController c = new EditNameController(context, tdlib);
      c.setMode(EditNameController.Mode.RENAME_SELF);
      navigateTo(c);
    } else if (id == R.id.btn_bio) {
      TdApi.UserFullInfo full = tdlib.myUserFull();
      EditBioController c = new EditBioController(context, tdlib);
      c.setArguments(new EditBioController.Arguments(full != null && full.bio != null ? full.bio.text : "", 0));
      navigateTo(c);
    } else if (id == R.id.btn_birthdate) {
      tdlib.ui().openBirthdateEditor(this, v, TdlibUi.BirthdateOpenOrigin.PROFILE);
    } else if (id == R.id.btn_tgx101NameColor) {
      SettingsController.tgx101PickNameColor(this, this::refresh);
    } else if (id == R.id.btn_addAccount) {
      tdlib.ui().addAccount(context, true, false);
    } else if (id == R.id.btn_logout) {
      tdlib.ui().handleProfileOption(this, R.id.more_btn_logout, tdlib.myUser());
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.cache().removeMyUserListener(this);
    tdlib.cache().removeUserDataListener(tdlib.myUserId(), this);
  }
}
