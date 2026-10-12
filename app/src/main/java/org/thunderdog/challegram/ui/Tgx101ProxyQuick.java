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

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

/** TGx101: the quick proxy sheet — ⋮ «Прокси» in a chat and holding the chat list search (user 2026-10-12) */
public final class Tgx101ProxyQuick {
  private Tgx101ProxyQuick () { }

  public static void show (ViewController<?> c) {
    boolean available = Settings.instance().getAvailableProxyCount() > 0;
    boolean enabled = Settings.instance().getEffectiveProxyId() != Settings.PROXY_ID_NONE;
    if (!available) {
      c.tdlib().ui().openProxySettings(c, false);
      return;
    }
    c.showOptions(null, new int[] {R.id.btn_tgx101ChatProxyToggle, R.id.btn_proxy},
      new String[] {Lang.getString(enabled ? R.string.Tgx101ProxyOff : R.string.Tgx101ProxyOn), Lang.getString(R.string.Tgx101ProxyList)},
      new int[] {enabled ? ViewController.OptionColor.RED : ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_security_24, R.drawable.baseline_settings_24}, (v, optionId) -> {
        if (optionId == R.id.btn_tgx101ChatProxyToggle) {
          boolean on = Settings.instance().toggleProxySetting(Settings.PROXY_FLAG_ENABLED);
          UI.showToast(on ? R.string.Tgx101ProxyIsOn : R.string.Tgx101ProxyIsOff, android.widget.Toast.LENGTH_SHORT);
        } else if (optionId == R.id.btn_proxy) {
          c.tdlib().ui().openProxySettings(c, false);
        }
        return true;
      });
  }

  /** Holding the chat list search: the proxy sheet when chosen in MagiX, otherwise the usual hint */
  public static void bindSearchHold (ViewController<?> c, android.view.View searchButton, org.thunderdog.challegram.navigation.HeaderView header) {
    if (searchButton == null) return;
    searchButton.setOnLongClickListener(v -> {
      if (Settings.instance().tgx101SearchHoldProxy()) {
        org.thunderdog.challegram.Tgx101Diag.mark("search hold: proxy");
        UI.forceVibrate(v, true);
        show(c);
        return true;
      }
      return header.onLongClick(v);
    });
  }
}
