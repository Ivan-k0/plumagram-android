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
package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.ConnectionState;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.lambda.RunnableInt;

/**
 * TGx101 additions to the proxy screen: paste one or many proxies from the clipboard,
 * copy the whole list, remove proxies that don't respond, show a proxy as a QR code,
 * and go back to the direct connection when it works again after a network change.
 *
 * Kept in one file so that moving to a new Telegram X version only touches a few call sites.
 */
public final class Tgx101Proxies {
  private Tgx101Proxies () { }

  /** Proxy flag: when switching automatically, also go back to the direct connection once it works. */
  public static final int PROXY_FLAG_RETURN_DIRECT = 1 << 6;

  private static final Pattern PROXY_LINK = Pattern.compile(
    "(?:https?://)?(?:www\\.)?(?:t\\.me|telegram\\.me|telegram\\.dog)/(?:proxy|socks)\\?[^\\s<>\"']+|tg://(?:proxy|socks)\\?[^\\s<>\"']+",
    Pattern.CASE_INSENSITIVE
  );

  static List<String> findProxyLinks (@Nullable CharSequence text) {
    Set<String> links = new LinkedHashSet<>();
    if (text != null) {
      Matcher m = PROXY_LINK.matcher(text);
      while (m.find()) {
        String link = m.group();
        // Links in chats and lists are often followed by punctuation.
        while (!link.isEmpty() && ".,;:!?)]}".indexOf(link.charAt(link.length() - 1)) != -1) {
          link = link.substring(0, link.length() - 1);
        }
        if (!link.isEmpty()) {
          links.add(link);
        }
      }
    }
    return new ArrayList<>(links);
  }

  // 1.1 + 1.2: one or many proxy links from the clipboard

  public static void pasteFromClipboard (@NonNull ViewController<?> c) {
    CharSequence pasted = U.getPasteText(c.context());
    int webAdded = org.thunderdog.challegram.proxy.Tgx101WebProxy.addFromText(pasted); // WEB proxy links
    List<String> links = findProxyLinks(pasted);
    if (links.isEmpty() && webAdded > 0) {
      return;
    }
    if (links.isEmpty()) {
      UI.showToast(R.string.ProxyPasteNothing, Toast.LENGTH_SHORT);
      return;
    }
    Tdlib tdlib = c.tdlib();
    TdApi.Proxy[] found = new TdApi.Proxy[links.size()];
    AtomicInteger remaining = new AtomicInteger(links.size());
    for (int i = 0; i < links.size(); i++) {
      final int index = i;
      tdlib.send(new TdApi.GetInternalLinkType(links.get(i)), (linkType, error) -> {
        if (linkType != null && linkType.getConstructor() == TdApi.InternalLinkTypeProxy.CONSTRUCTOR) {
          found[index] = ((TdApi.InternalLinkTypeProxy) linkType).proxy;
        }
        if (remaining.decrementAndGet() == 0) {
          UI.post(() -> addPastedProxies(c, found));
        }
      });
    }
  }

  private static void addPastedProxies (@NonNull ViewController<?> c, TdApi.Proxy[] found) {
    List<TdApi.Proxy> proxies = new ArrayList<>();
    for (TdApi.Proxy proxy : found) {
      if (proxy != null) {
        proxies.add(proxy);
      }
    }
    if (proxies.isEmpty()) {
      UI.showToast(R.string.ProxyPasteNothing, Toast.LENGTH_SHORT);
      return;
    }
    if (proxies.size() == 1) {
      // Same flow as opening a proxy link: the user sees the proxy and decides whether to connect.
      c.tdlib().ui().openProxyAlert(c, proxies.get(0));
      return;
    }
    int before = Settings.instance().getAvailableProxies().size();
    for (TdApi.Proxy proxy : proxies) {
      Settings.instance().addOrUpdateProxy(proxy, null, false);
    }
    int added = Settings.instance().getAvailableProxies().size() - before;
    UI.showToast(Lang.getString(R.string.ProxyPasteResult, added, proxies.size() - added), Toast.LENGTH_LONG);
  }

  // 1.3: the whole list as links, one per line

  static void copyAll (@NonNull ViewController<?> c, @NonNull List<Settings.Proxy> proxies) {
    List<Settings.Proxy> linkable = new ArrayList<>();
    for (Settings.Proxy proxy : proxies) {
      if (proxy.proxy != null && proxy.proxy.type.getConstructor() != TdApi.ProxyTypeHttp.CONSTRUCTOR) {
        linkable.add(proxy);
      }
    }
    int skipped = proxies.size() - linkable.size();
    if (linkable.isEmpty()) {
      UI.showToast(R.string.ProxyCopyAllNothing, Toast.LENGTH_SHORT);
      return;
    }
    String[] urls = new String[linkable.size()];
    AtomicInteger remaining = new AtomicInteger(linkable.size());
    for (int i = 0; i < linkable.size(); i++) {
      final int index = i;
      String webLink = org.thunderdog.challegram.proxy.Tgx101WebProxy.linkFor(linkable.get(i).id);
      RunnableData<String> onLink = url -> {
        urls[index] = url;
        if (remaining.decrementAndGet() == 0) {
          StringBuilder b = new StringBuilder();
          int count = 0;
          for (String u : urls) {
            if (u != null && !u.isEmpty()) {
              if (b.length() > 0) {
                b.append('\n');
              }
              b.append(u);
              count++;
            }
          }
          U.copyText(b.toString());
          UI.showToast(skipped > 0 ?
            Lang.getString(R.string.ProxyCopyAllResultHttp, count, skipped) :
            Lang.getString(R.string.ProxyCopyAllResult, count), Toast.LENGTH_LONG);
        }
      };
      if (webLink != null) {
        onLink.runWithData(webLink);
      } else {
        c.tdlib().getProxyLink(linkable.get(i), onLink::runWithData);
      }
    }
  }

  // 1.4: re-check every proxy, then offer to remove the ones that still don't respond

  static void removeUnavailable (@NonNull ViewController<?> c, @NonNull List<Settings.Proxy> proxies, @NonNull Settings.Proxy directConnection, @NonNull RunnableInt removeProxy) {
    Tdlib tdlib = c.tdlib();
    if (proxies.isEmpty()) {
      return;
    }
    if (tdlib.connectionState() == ConnectionState.WAITING_FOR_NETWORK) {
      UI.showToast(R.string.ProxyCheckNoNetwork, Toast.LENGTH_SHORT);
      return;
    }
    UI.showToast(R.string.ProxyCheckingAll, Toast.LENGTH_SHORT);
    List<Settings.Proxy> checked = new ArrayList<>(proxies);
    checked.add(directConnection);
    AtomicInteger remaining = new AtomicInteger(checked.size());
    for (Settings.Proxy proxy : checked) {
      tdlib.pingProxy(proxy, pingMs -> {
        if (remaining.decrementAndGet() == 0) {
          UI.post(() -> offerRemoval(c, proxies, directConnection, removeProxy));
        }
      });
    }
  }

  private static void offerRemoval (@NonNull ViewController<?> c, @NonNull List<Settings.Proxy> proxies, @NonNull Settings.Proxy directConnection, @NonNull RunnableInt removeProxy) {
    if (c.isDestroyed()) {
      return;
    }
    List<Settings.Proxy> dead = new ArrayList<>();
    int alive = 0;
    for (Settings.Proxy proxy : proxies) {
      if (org.thunderdog.challegram.proxy.Tgx101WebProxy.isWebProxy(proxy.id)) {
        continue; // an inactive WEB proxy can't be checked without starting its carrier
      }
      if (proxy.pingMs == Settings.PROXY_TIME_EMPTY) {
        dead.add(proxy);
      } else if (proxy.pingMs >= 0) {
        alive++;
      }
    }
    if (alive == 0 && directConnection.pingMs < 0) {
      // Nothing answers at all: most likely the internet is down, not the proxies.
      UI.showToast(R.string.ProxyCheckNoNetwork, Toast.LENGTH_LONG);
      return;
    }
    if (dead.isEmpty()) {
      UI.showToast(R.string.ProxyAllAvailable, Toast.LENGTH_SHORT);
      return;
    }
    c.showOptions(Lang.getString(R.string.ProxyRemoveUnavailableConfirm, dead.size()),
      new int[] {R.id.btn_removeUnavailableProxies, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.ProxyRemoveUnavailable), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_removeUnavailableProxies) {
          for (Settings.Proxy proxy : dead) {
            removeProxy.runWithInt(proxy.id);
          }
        }
        return true;
      });
  }

  // 1.5: QR code

  static void showQr (@NonNull ViewController<?> c, @NonNull Settings.Proxy proxy) {
    c.tdlib().getProxyLink(proxy, url -> {
      if (url == null || url.isEmpty() || c.isDestroyed()) {
        return;
      }
      int size = Screen.dp(260f);
      Bitmap bitmap = makeQr(url, size);
      if (bitmap == null) {
        return;
      }
      ImageView view = new ImageView(c.context());
      view.setImageBitmap(bitmap);
      view.setAdjustViewBounds(true);
      int padding = Screen.dp(16f);
      view.setPadding(padding, padding, padding, padding);
      AlertDialog.Builder b = new AlertDialog.Builder(c.context(), Theme.dialogTheme());
      b.setTitle(proxy.getName());
      b.setView(view);
      b.setPositiveButton(Lang.getString(R.string.OK), (dialog, which) -> dialog.dismiss());
      c.showAlert(b);
    });
  }

  private static @Nullable Bitmap makeQr (@NonNull String text, int size) {
    try {
      BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
        Collections.singletonMap(EncodeHintType.MARGIN, 2));
      int width = matrix.getWidth(), height = matrix.getHeight();
      int[] pixels = new int[width * height];
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          pixels[y * width + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
        }
      }
      return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    } catch (Throwable t) {
      return null;
    }
  }

  // Automatic return to the direct connection

  /** Called on the UI thread when the device switches between networks (Wi-Fi, mobile, …). */
  public static void onNetworkTypeChanged () {
    if (!shouldReturnDirect()) {
      return;
    }
    // Give the new network a moment to settle before checking it.
    UI.post(() -> {
      if (!shouldReturnDirect()) {
        return;
      }
      Tdlib tdlib = TdlibManager.instance().current();
      Settings.Proxy direct = Settings.Proxy.noProxy(false);
      tdlib.pingProxy(direct, pingMs -> {
        if (pingMs >= 0) {
          UI.post(() -> {
            if (shouldReturnDirect()) {
              Settings.instance().disableProxy();
            }
          });
        }
      });
    }, 3000);
  }

  private static boolean shouldReturnDirect () {
    Settings settings = Settings.instance();
    return settings.checkProxySetting(Settings.PROXY_FLAG_ENABLED) &&
      settings.checkProxySetting(Settings.PROXY_FLAG_SWITCH_AUTOMATICALLY) &&
      settings.checkProxySetting(PROXY_FLAG_RETURN_DIRECT);
  }

  // Proxy sponsor channel

  /** Chat list filter that leaves out the proxy sponsor channel when the user chose to hide it. */
  public static @Nullable Filter<TdApi.Chat> chatListFilter (@Nullable Filter<TdApi.Chat> filter) {
    if (!Settings.instance().hideProxySponsor()) {
      return filter;
    }
    return chat -> !isProxySponsor(chat) && (filter == null || filter.accept(chat));
  }

  private static boolean isProxySponsor (TdApi.Chat chat) {
    TdApi.ChatPosition[] positions = chat.positions;
    if (positions != null) {
      for (TdApi.ChatPosition position : positions) {
        if (position.source != null && position.source.getConstructor() == TdApi.ChatSourceMtprotoProxy.CONSTRUCTOR) {
          return true;
        }
      }
    }
    return false;
  }
}
