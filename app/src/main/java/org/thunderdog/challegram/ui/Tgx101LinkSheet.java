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
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Locale;

import me.vkryl.core.StringUtils;

/**
 * The question before a link opens, in the app's own sheet instead of the system dialog
 * (group 2026-11-03 22:19 «поменять окно под наш стиль материал, снизу предложение скопировать ссылку»).
 * The question stays for safety — the text of a link can hide any address — but a site can be
 * answered once and for all (user 2026-11-04); Settings → Privacy shows those sites and forgets them.
 */
public final class Tgx101LinkSheet {
  private Tgx101LinkSheet () { }

  /** The exact host of an http(s) link, null for anything else: only such a site can be remembered */
  private static @Nullable String siteOf (String url) {
    Uri uri = Strings.wrapHttps(url);
    if (uri == null || uri.getHost() == null) return null;
    String scheme = uri.getScheme();
    if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return null;
    return uri.getHost().toLowerCase(Locale.ROOT);
  }

  /** {@code displayUrl} differs from the real address (a login link): such a link always asks */
  public static boolean isAnswered (String url, @Nullable String displayUrl) {
    if (!StringUtils.isEmpty(displayUrl)) return false;
    String site = siteOf(url);
    return site != null && Settings.instance().isTgx101SiteWithoutPrompt(site);
  }

  public static void show (ViewController<?> c, String url, @Nullable String displayUrl, Runnable open, @Nullable Runnable cancelled) {
    // TGx101 (user 2026-11-21, mockup «Окно "Перейти по ссылке?"», variant В «меню у ссылки»): a small menu at the
    // finger instead of the big sheet — the site in bold and the path under it, «Open» first, no dimmed «Cancel»: a tap
    // beside the menu closes it
    final boolean[] answered = {false};
    final String site = StringUtils.isEmpty(displayUrl) ? siteOf(url) : null;
    Tgx101Diag.mark("link prompt: shown, can remember the site " + (site != null));

    String shown = StringUtils.isEmpty(displayUrl) ? url : displayUrl;
    android.text.SpannableStringBuilder head = new android.text.SpannableStringBuilder();
    Uri uri = Strings.wrapHttps(shown);
    String host = uri != null ? uri.getHost() : null;
    if (!StringUtils.isEmpty(host)) {
      head.append(host);
      head.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, head.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      head.setSpan(new android.text.style.ForegroundColorSpan(Theme.textAccentColor()), 0, head.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      int at = shown.indexOf(host);
      String rest = at >= 0 ? shown.substring(at + host.length()) : "";
      if (rest.length() > 1) head.append('\n').append(rest.length() > 90 ? rest.substring(0, 89) + "…" : rest);
    } else {
      head.append(shown.length() > 120 ? shown.substring(0, 119) + "…" : shown);
    }

    java.util.List<Integer> ids = new java.util.ArrayList<>();
    java.util.List<String> titles = new java.util.ArrayList<>();
    java.util.List<Integer> colors = new java.util.ArrayList<>();
    java.util.List<Integer> icons = new java.util.ArrayList<>();
    ids.add(R.id.btn_open); titles.add(Lang.getString(R.string.Open)); colors.add(ViewController.OptionColor.BLUE); icons.add(R.drawable.baseline_open_in_browser_24);
    if (site != null) {
      ids.add(R.id.btn_tgx101LinkAlways); titles.add(Lang.getString(R.string.Tgx101LinkOpenAlways)); colors.add(ViewController.OptionColor.NORMAL); icons.add(R.drawable.baseline_public_24);
    }
    ids.add(R.id.btn_copyLink); titles.add(Lang.getString(R.string.CopyLink)); colors.add(ViewController.OptionColor.NORMAL); icons.add(R.drawable.baseline_content_copy_24);
    ids.add(R.id.btn_share); titles.add(Lang.getString(R.string.Share)); colors.add(ViewController.OptionColor.NORMAL); icons.add(R.drawable.baseline_share_24);

    org.thunderdog.challegram.widget.PopupLayout popup = c.showOptions(head, toArray(ids), titles.toArray(new String[0]), toArray(colors), toArray(icons), (itemView, id) -> {
      if (id == R.id.btn_open) {
        answered[0] = true;
        open.run();
      } else if (id == R.id.btn_tgx101LinkAlways) {
        answered[0] = true;
        Settings.instance().addTgx101SiteWithoutPrompt(site);
        Tgx101Diag.mark("link prompt: site remembered");
        open.run();
      } else if (id == R.id.btn_copyLink) {
        UI.copyText(url, R.string.CopiedLink);
      } else if (id == R.id.btn_share) {
        org.thunderdog.challegram.tool.Intents.shareText(url);
      }
      return true;
    });
    if (popup != null) {
      popup.setDismissListener(p -> {
        if (!answered[0]) {
          Tgx101Diag.mark("link prompt: closed without opening");
          if (cancelled != null) cancelled.run();
        }
      });
    } else if (cancelled != null) {
      cancelled.run();
    }
  }

  private static int[] toArray (java.util.List<Integer> list) {
    int[] array = new int[list.size()];
    for (int i = 0; i < array.length; i++) array[i] = list.get(i);
    return array;
  }

  /** Settings → Privacy: the sites answered once and for all, and forgetting them */
  public static void showRemembered (ViewController<?> c, Runnable changed) {
    String sites = Settings.instance().tgx101SitesWithoutPrompt();
    if (StringUtils.isEmpty(sites)) {
      UI.showToast(R.string.Tgx101LinkSitesNone, Toast.LENGTH_SHORT);
      return;
    }
    c.showOptions(sites, new int[] {R.id.btn_tgx101SitesWithoutPrompt, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Tgx101LinkSitesForget), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_tgx101SitesWithoutPrompt) {
          Settings.instance().clearTgx101SitesWithoutPrompt();
          changed.run();
        }
        return true;
      });
  }
}
