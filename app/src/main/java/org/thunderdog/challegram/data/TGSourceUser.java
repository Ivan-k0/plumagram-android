/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 20/02/2016 at 18:26
 */
package org.thunderdog.challegram.data;

import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextPart;

public class TGSourceUser extends TGSource implements TdlibCache.UserDataChangeListener {
  private final long senderUserId;
  private TdApi.User user;

  public TGSourceUser (TGMessage msg, TdApi.MessageOriginUser info) {
    super(msg);
    this.senderUserId = info.senderUserId;
  }

  public TdApi.User getUser () {
    return user;
  }

  /**
   * TGx101 (user 2026-10-25 «от Сергея»): who it's forwarded from — the first name only (the full one didn't fit), in
   * Russian put in the genitive by the usual endings; a name the rules don't cover stays as it is
   */
  public String tgx101FromName () {
    if (user == null || user.firstName == null || user.firstName.trim().isEmpty()) return getAuthorName();
    String name = user.firstName.trim();
    String pack = org.thunderdog.challegram.core.Lang.packId();
    if (pack == null || !pack.startsWith("ru")) return name;
    return tgx101Genitive(name);
  }

  static String tgx101Genitive (String name) {
    int space = name.indexOf(' ');
    if (space > 0) return tgx101Genitive(name.substring(0, space)) + name.substring(space);
    if (name.length() < 3) return name;
    for (int i = 0; i < name.length(); i++) {
      char ch = name.charAt(i);
      if (!(ch >= 'А' && ch <= 'я') && ch != 'ё' && ch != 'Ё' && ch != '-') return name; // «Ф.А», emoji, latin — as is
    }
    char last = name.charAt(name.length() - 1);
    char prev = name.charAt(name.length() - 2);
    String stem = name.substring(0, name.length() - 1);
    boolean upper = Character.isUpperCase(last) && Character.isUpperCase(name.charAt(0)) && name.length() > 1 && Character.isUpperCase(name.charAt(1));
    String out;
    switch (Character.toLowerCase(last)) {
      case 'й': case 'ь': out = stem + "я"; break; // Сергей → Сергея, Игорь → Игоря
      case 'я': out = stem + "и"; break; // Мария → Марии, Илья → Ильи
      case 'а': out = stem + ("гкхжчшщ".indexOf(Character.toLowerCase(prev)) != -1 ? "и" : "ы"); break; // Анна → Анны, Маша → Маши
      default:
        if ("бвгджзклмнпрстфхцчшщ".indexOf(Character.toLowerCase(last)) != -1) out = name + "а"; // Иван → Ивана
        else return name; // о, е, и, у, ю, latin… — not declined
    }
    return upper ? out.toUpperCase() : out;
  }

  @Override
  public void load () {
    TdApi.User user = msg.tdlib().cache().user(senderUserId);
    msg.tdlib().cache().addUserDataListener(senderUserId, this);
    if (user != null) {
      this.user = user;
      this.isReady = true;
      msg.rebuildForward();
    }
  }

  @Override
  public boolean open (View view, Text text, TextPart part, TdlibUi.UrlOpenParameters openParameters, Receiver receiver) {
    if (user != null) {
      msg.tdlib().ui().openPrivateProfile(msg.controller(), user.id, openParameters);
      return true;
    }
    return false;
  }

  @Override
  public void destroy () {
    msg.tdlib().cache().removeUserDataListener(senderUserId, this);
  }

  @Override
  public void onUserUpdated (TdApi.User user) {
    this.user = user;
    msg.runOnUiThreadOptional(() -> {
      msg.rebuildForward();
      msg.postInvalidate();
    });
  }

  public long getSenderUserId () {
    return senderUserId;
  }

  @Override
  public String getAuthorName () {
    return user == null ? Lang.getString(R.string.LoadingUser) : TD.getUserName(user);
  }

  @Override
  public TdlibAccentColor getAuthorAccentColor () {
    return msg.tdlib.cache().userAccentColor(senderUserId);
  }

  @Override
  public void requestAvatar (AvatarReceiver receiver) {
    receiver.requestUser(msg.tdlib, senderUserId, AvatarReceiver.Options.NONE);
  }
}
