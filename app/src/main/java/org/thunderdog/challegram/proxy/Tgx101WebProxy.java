/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/tgx101-android)
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

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.util.Base64;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Locale;
import java.util.regex.Pattern;

import me.vkryl.leveldb.LevelDB;

/**
 * TGx101: WEB proxies (Telegram's WEB proxy protocol, as in Telegram Desktop and the official
 * Android proof of concept). A WEB proxy is stored in the normal proxy list as an MTProto proxy on
 * 127.0.0.1 with the user's secret; this class remembers its relay address and runs the carrier
 * while it is the current proxy.
 */
public final class Tgx101WebProxy {
  private Tgx101WebProxy () { }

  public static final int LOCAL_PORT = 27101;
  private static final String KEY_PREFIX = "tgx101_webproxy_";
  private static final Pattern HOST = Pattern.compile("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z][a-z0-9-]{0,61}[a-z0-9]$");
  private static final Pattern PATH = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]*(/[A-Za-z0-9][A-Za-z0-9_-]*)*$");

  public static final class Address {
    public final String host, path;
    public final byte[] secret;

    Address (String host, String path, byte[] secret) {
      this.host = host;
      this.path = path;
      this.secret = secret;
    }

    public String display () {
      return path.isEmpty() ? host : host + "/" + path;
    }

    String secretHex () {
      StringBuilder b = new StringBuilder();
      for (byte x : secret) b.append(String.format(Locale.US, "%02x", x));
      return b.toString();
    }
  }

  private static @Nullable Tgx101WebCarrier carrier;
  private static int carrierProxyId;
  private static boolean initialized;

  // Startup: follow the current proxy

  public static void init () {
    if (initialized) return;
    initialized = true;
    Settings.instance().addProxyListener(new Settings.ProxyChangeListener() {
      @Override
      public void onProxyConfigurationChanged (int proxyId, @Nullable TdApi.Proxy proxy, @Nullable String description, boolean isCurrent, boolean isNewAdd) {
        if (isCurrent) {
          UI.post(() -> activate(proxy != null ? proxyId : Settings.PROXY_ID_NONE));
        }
      }

      @Override
      public void onProxyAvailabilityChanged (boolean isAvailable) { }

      @Override
      public void onProxyAdded (Settings.Proxy proxy, boolean isCurrent) { }
    });
    activate(Settings.instance().getEffectiveProxyId());
  }

  private static void activate (int proxyId) {
    Address address = proxyId != Settings.PROXY_ID_NONE ? load(proxyId) : null;
    if (address == null) {
      if (carrier != null) {
        carrier.stop();
        carrier = null;
        carrierProxyId = Settings.PROXY_ID_NONE;
      }
      return;
    }
    if (carrier != null && carrierProxyId == proxyId) {
      return;
    }
    if (carrier != null) {
      carrier.stop();
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      UI.showToast(R.string.Tgx101WebProxyUnsupported, Toast.LENGTH_LONG);
      return;
    }
    carrierProxyId = proxyId;
    carrier = new Tgx101WebCarrier(UI.getAppContext(), address.host, address.path, address.secret, LOCAL_PORT, state -> {
      if (state == Tgx101WebCarrier.STATE_UNSUPPORTED) {
        UI.showToast(R.string.Tgx101WebProxyUnsupported, Toast.LENGTH_LONG);
      }
    });
    carrier.start();
  }

  // Storage

  private static @Nullable Address load (int proxyId) {
    String value = Settings.instance().pmc().getString(KEY_PREFIX + proxyId, null);
    if (value == null) return null;
    String[] parts = value.split("\\|", -1);
    if (parts.length != 3) return null;
    byte[] secret = decodeHex(parts[2]);
    return secret != null ? new Address(parts[0], parts[1], secret) : null;
  }

  public static boolean isWebProxy (int proxyId) {
    return proxyId != Settings.PROXY_ID_NONE && Settings.instance().pmc().getString(KEY_PREFIX + proxyId, null) != null;
  }

  public static void add (Address address) {
    TdApi.Proxy proxy = new TdApi.Proxy("127.0.0.1", LOCAL_PORT, new TdApi.ProxyTypeMtproto(address.secretHex()));
    String description = "WEB · " + address.display();
    int proxyId = Settings.instance().addOrUpdateProxy(proxy, description, false);
    LevelDB pmc = Settings.instance().pmc();
    pmc.putString(KEY_PREFIX + proxyId, address.host + "|" + address.path + "|" + address.secretHex());
    Settings.instance().addOrUpdateProxy(proxy, description, true, proxyId); // current: starts the carrier
    UI.showToast(Lang.getString(R.string.Tgx101WebProxyAdded, address.display()), Toast.LENGTH_SHORT);
  }

  // Parsing

  /** Accepts "host" or "host/path", with an optional pasted https:// prefix. */
  public static @Nullable Address parse (@Nullable String server, @Nullable String secretText, boolean fromLink) {
    if (server == null || secretText == null) return null;
    String value = server.trim();
    if (value.regionMatches(true, 0, "https://", 0, 8)) {
      value = value.substring(8);
    }
    while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
    int slash = value.indexOf('/');
    String host = (slash >= 0 ? value.substring(0, slash) : value).toLowerCase(Locale.US);
    String path = slash >= 0 ? value.substring(slash + 1) : "";
    if (!HOST.matcher(host).matches() || (!path.isEmpty() && (path.length() > 128 || !PATH.matcher(path).matches()))) {
      return null;
    }
    byte[] secret = decodeSecret(secretText.trim(), !path.isEmpty() && fromLink);
    return secret != null ? new Address(host, path, secret) : null;
  }

  /** 16 bytes, or 17 starting with 0xdd. Hex or base64url; 0x70 marks a link carrying a base path. */
  private static @Nullable byte[] decodeSecret (String text, boolean requireMarker) {
    byte[] bytes = decodeHex(text);
    boolean marked = false;
    if (bytes == null) {
      try {
        bytes = Base64.decode(text, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
      } catch (IllegalArgumentException e) {
        return null;
      }
      if (bytes.length >= 17 && (bytes[0] & 0xff) == 0x70) {
        byte[] stripped = new byte[bytes.length - 1];
        System.arraycopy(bytes, 1, stripped, 0, stripped.length);
        bytes = stripped;
        marked = true;
      }
    }
    if (requireMarker && !marked) return null;
    boolean plain = bytes.length == 16;
    boolean padded = bytes.length == 17 && (bytes[0] & 0xff) == 0xdd;
    return plain || padded ? bytes : null; // ee (TLS emulation) secrets are not used by WEB proxies
  }

  private static @Nullable byte[] decodeHex (String text) {
    if (text.length() % 2 != 0 || !text.matches("[0-9a-fA-F]+")) return null;
    byte[] result = new byte[text.length() / 2];
    for (int i = 0; i < result.length; i++) {
      result[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
    }
    return result;
  }

  // Links: tg://webproxy?server=…&secret=… and https://t.me/webproxy?server=…&secret=…

  public static @Nullable Address parseLink (@Nullable String url) {
    if (url == null) return null;
    Uri uri;
    try {
      uri = Uri.parse(url.trim());
    } catch (Throwable t) {
      return null;
    }
    String scheme = uri.getScheme();
    boolean tg = "tg".equalsIgnoreCase(scheme) && "webproxy".equalsIgnoreCase(uri.getHost());
    boolean web = ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) && uri.getHost() != null &&
      (uri.getHost().equalsIgnoreCase("t.me") || uri.getHost().equalsIgnoreCase("telegram.me")) && "/webproxy".equalsIgnoreCase(uri.getPath());
    if (!tg && !web) return null;
    String server = uri.getQueryParameter("server");
    if (server == null) server = uri.getQueryParameter("host");
    return parse(server, uri.getQueryParameter("secret"), true);
  }

  /** Opens a WEB proxy link with one connect action. Returns false if the URL is not such a link. */
  public static boolean tryOpenLink (@Nullable ViewController<?> c, String url) {
    if (url == null || !url.toLowerCase(Locale.US).contains("webproxy")) return false;
    Address address = parseLink(url);
    if (address == null) {
      if (url.toLowerCase(Locale.US).startsWith("tg://webproxy")) {
        UI.showToast(R.string.Tgx101WebProxyInvalid, Toast.LENGTH_LONG);
        return true;
      }
      return false;
    }
    if (c == null) return false;
    confirm(c, address);
    return true;
  }

  private static void confirm (ViewController<?> c, Address address) {
    c.showOptions(Lang.getString(R.string.Tgx101WebProxyConfirm, address.display()),
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Tgx101WebProxyConnect), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_security_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_done) {
          add(address);
        }
        return true;
      });
  }

  /** Shareable link: plain secret at the host root, 0x70-marked base64url when a base path is used. */
  public static @Nullable String linkFor (int proxyId) {
    Address address = load(proxyId);
    if (address == null) return null;
    String secret;
    if (address.path.isEmpty()) {
      secret = address.secretHex();
    } else {
      byte[] marked = new byte[address.secret.length + 1];
      marked[0] = 0x70;
      System.arraycopy(address.secret, 0, marked, 1, address.secret.length);
      secret = Base64.encodeToString(marked, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
    return "https://t.me/webproxy?server=" + Uri.encode(address.display()) + "&secret=" + secret;
  }

  private static final Pattern LINK = Pattern.compile("(?i)(tg://webproxy\\?|https?://(t|telegram)\\.me/webproxy\\?)[^\\s]+");

  /** Adds every WEB proxy link found in pasted text. */
  public static int addFromText (@Nullable CharSequence text) {
    if (text == null) return 0;
    java.util.regex.Matcher m = LINK.matcher(text);
    int added = 0;
    while (m.find()) {
      Address address = parseLink(m.group());
      if (address != null) {
        add(address);
        added++;
      }
    }
    return added;
  }

  // Manual entry: proxy address and secret

  public static void showAddDialog (ViewController<?> c) {
    Context context = c.context();
    LinearLayout layout = new LinearLayout(context);
    layout.setOrientation(LinearLayout.VERTICAL);
    int padding = Screen.dp(20f);
    layout.setPadding(padding, Screen.dp(8f), padding, 0);
    EditText server = new EditText(context);
    server.setHint(Lang.getString(R.string.Tgx101WebProxyAddress));
    server.setSingleLine(true);
    server.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    layout.addView(server, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    EditText secret = new EditText(context);
    secret.setHint(Lang.getString(R.string.Tgx101WebProxySecret));
    secret.setSingleLine(true);
    secret.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    layout.addView(secret, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.Tgx101WebProxyAdd));
    b.setMessage(Lang.getString(R.string.Tgx101WebProxyAddHint));
    b.setView(layout);
    b.setPositiveButton(Lang.getString(R.string.Tgx101WebProxyConnect), (dialog, which) -> {
      Address address = parse(server.getText().toString(), secret.getText().toString(), false);
      if (address == null) {
        UI.showToast(R.string.Tgx101WebProxyInvalid, Toast.LENGTH_LONG);
      } else {
        add(address);
      }
    });
    b.setNegativeButton(Lang.getString(R.string.Cancel), null);
    c.showAlert(b);
  }
}
