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
package org.thunderdog.challegram.proxy;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Locale;

/**
 * TGx101 (4PDA 2026-11-02: «обновил телефон до Android 17, и к двум домашним прокси (tg-ws-proxy-rs, в локалке) оно
 * подключаться больше не хочет; на 4.3, 9 и 13 всё работает, и на самом телефоне тоже»): from Android 17 an app built for
 * it reaches the devices of the home network only with the «local network» permission — without it the connection to
 * 192.168.x.x just hangs (Pong timeout), while the phone itself (127.0.0.1) stays reachable. Asked for when such a proxy
 * is in use: chosen, added, or back in the app with it.
 */
public final class Tgx101LocalNetwork {
  private Tgx101LocalNetwork () { }

  /** Manifest.permission.ACCESS_LOCAL_NETWORK, API 37 */
  public static final String PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK";
  private static final int SDK_LOCAL_NETWORK = 37;

  /** A server in the home network (private IPv4 / IPv6 ranges, «.local»), not the phone itself and not the internet */
  public static boolean isLocalNetwork (@Nullable TdApi.Proxy proxy) {
    if (proxy == null || proxy.server == null) return false;
    String host = proxy.server.trim().toLowerCase(Locale.ROOT);
    if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
    if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa")) return true;
    if (host.contains(":")) { // IPv6: link-local fe80::/10, unique local fc00::/7
      return host.startsWith("fe8") || host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb") || host.startsWith("fc") || host.startsWith("fd");
    }
    String[] parts = host.split("\\.");
    if (parts.length != 4) return false;
    int a, b;
    try {
      a = Integer.parseInt(parts[0]);
      b = Integer.parseInt(parts[1]);
    } catch (NumberFormatException e) {
      return false;
    }
    return a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168) || (a == 169 && b == 254) || (a == 100 && b >= 64 && b <= 127);
  }

  /** True when this proxy can't be reached until the permission is given */
  public static boolean isBlocked (@Nullable Context context, @Nullable TdApi.Proxy proxy) {
    if (Build.VERSION.SDK_INT < SDK_LOCAL_NETWORK || context == null || !isLocalNetwork(proxy)) return false;
    return androidx.core.content.ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED;
  }

  private static long askedAt;

  /** The proxy in use is in the home network and the permission is missing: ask for it (once in a while, not on every resume) */
  public static void checkCurrent (@Nullable BaseActivity context) {
    if (Build.VERSION.SDK_INT < SDK_LOCAL_NETWORK || context == null) return;
    Settings settings = Settings.instance();
    int id = settings.getEffectiveProxyId();
    Settings.Proxy current = id != Settings.PROXY_ID_NONE ? settings.getProxyConfig(id) : null;
    if (current == null || !isBlocked(context, current.proxy)) return;
    long now = android.os.SystemClock.uptimeMillis();
    if (askedAt != 0 && now - askedAt < 60_000) return;
    askedAt = now;
    Tgx101Diag.mark("proxy: #" + id + " is in the home network — asking for the local network permission");
    context.requestCustomPermissions(new String[] {PERMISSION}, (code, permissions, grantResults, grantCount) -> {
      boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
      Tgx101Diag.mark("proxy: local network permission " + (granted ? "given" : "refused"));
      if (!granted) {
        UI.showToast(R.string.Tgx101ProxyLocalNetworkDenied, Toast.LENGTH_LONG);
      }
    });
  }

  /** The user asked (a tap on «no access to the local network»): the system request right away */
  public static void askNow (@Nullable BaseActivity context) {
    askedAt = 0;
    checkCurrent(context);
  }

  private static final Settings.ProxyChangeListener LISTENER = new Settings.ProxyChangeListener() {
    @Override
    public void onProxyConfigurationChanged (int proxyId, @Nullable TdApi.Proxy proxy, @Nullable String description, boolean isCurrent, boolean isNewAdd) {
      if (isCurrent && isLocalNetwork(proxy)) {
        askedAt = 0; // chosen just now by the user: ask right away
        UI.post(() -> checkCurrent(UI.getUiContext()));
      }
    }

    @Override
    public void onProxyAvailabilityChanged (boolean isAvailable) { }

    @Override
    public void onProxyAdded (Settings.Proxy proxy, boolean isCurrent) { }
  };

  private static boolean listening;

  /** Called as the app comes back: listens to proxy changes from then on, and checks the one in use */
  public static void onAppResumed (@Nullable BaseActivity context) {
    if (Build.VERSION.SDK_INT < SDK_LOCAL_NETWORK) return;
    if (!listening) {
      listening = true;
      Settings.instance().addProxyListener(LISTENER);
    }
    checkCurrent(context);
  }
}
