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
    final boolean[] answered = {false};
    final Tgx101Sheet.Handle[] handle = new Tgx101Sheet.Handle[1];
    final String site = StringUtils.isEmpty(displayUrl) ? siteOf(url) : null;
    final Runnable openNow = () -> {
      answered[0] = true;
      handle[0].dismiss();
      open.run();
    };
    Tgx101Diag.mark("link prompt: shown, can remember the site " + (site != null));
    handle[0] = Tgx101Sheet.show(c, () -> {
      if (!answered[0]) {
        Tgx101Diag.mark("link prompt: closed without opening");
        if (cancelled != null) cancelled.run();
      }
    }, body -> {
      Context context = c.context();
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101LinkOpenTitle)));

      TextView address = new TextView(context);
      address.setText(StringUtils.isEmpty(displayUrl) ? url : displayUrl);
      address.setTextColor(Theme.textDecentColor());
      address.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      address.setLineSpacing(Screen.dp(2f), 1f);
      address.setMaxLines(5);
      address.setEllipsize(TextUtils.TruncateAt.END);
      address.setPadding(Screen.dp(20f), 0, Screen.dp(20f), Screen.dp(16f));
      body.addView(address, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      TextView button = new TextView(context);
      button.setText(Lang.getString(R.string.Open));
      button.setTextColor(Theme.getColor(ColorId.fillingPositiveContent));
      button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      button.setTypeface(Fonts.getRobotoMedium());
      button.setGravity(Gravity.CENTER);
      GradientDrawable buttonBg = new GradientDrawable();
      buttonBg.setColor(Theme.getColor(ColorId.fillingPositive));
      buttonBg.setCornerRadius(Screen.dp(14f));
      button.setBackground(buttonBg);
      Views.setClickable(button);
      button.setOnClickListener(v -> openNow.run());
      LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(50f));
      buttonParams.setMargins(Screen.dp(20f), 0, Screen.dp(20f), Screen.dp(8f));
      body.addView(button, buttonParams);

      if (site != null) {
        body.addView(Tgx101Sheet.row(context, R.drawable.baseline_public_24, 0, Lang.getString(R.string.Tgx101LinkOpenAlways), null, false, v -> {
          Settings.instance().addTgx101SiteWithoutPrompt(site);
          Tgx101Diag.mark("link prompt: site remembered");
          openNow.run();
        }));
      }
      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_content_copy_24, 0, Lang.getString(R.string.CopyLink), null, false, v -> {
        UI.copyText(url, R.string.CopiedLink);
        handle[0].dismiss();
      }));

      TextView quiet = new TextView(context);
      quiet.setText(Lang.getString(R.string.Cancel));
      quiet.setTextColor(Theme.textDecentColor());
      quiet.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      quiet.setGravity(Gravity.CENTER);
      Views.setClickable(quiet);
      RippleSupport.setTransparentSelector(quiet);
      quiet.setOnClickListener(v -> handle[0].dismiss());
      body.addView(quiet, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(46f)));
    });
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
