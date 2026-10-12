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
package org.thunderdog.challegram;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.UI;

import java.security.MessageDigest;

/**
 * TGx101 (user 2026-10-12, a re-signed «MOD» of 0.1.661 went around): an APK signed with somebody else's key is not
 * our build — it may be changed in any way and still works under our api_id. Say so once per launch, don't block.
 */
public final class Tgx101BuildCheck {
  private Tgx101BuildCheck () { }

  /** SHA-256 of the certificate all PlumaGram builds are signed with */
  private static final String OFFICIAL_CERT = "9d4c7dbc248171a1bbb5c964cd5312e837188b777cd566ca1cc2c6875e638eb1";
  private static final String OFFICIAL_PAGE = "https://4pda.to/forum/index.php?showtopic=1126862";
  private static boolean shown;

  public static void checkLater (Activity activity) {
    if (BuildConfig.DEBUG || shown) return;
    UI.post(() -> {
      if (shown || activity.isFinishing()) return;
      String cert = certificateOf(activity);
      if (cert == null || OFFICIAL_CERT.equals(cert)) return;
      shown = true;
      Tgx101Diag.mark("build check: signed by another key " + cert.substring(0, 16));
      try {
        new android.app.AlertDialog.Builder(activity, Theme.dialogTheme())
          .setTitle(Lang.getString(R.string.Tgx101UnofficialTitle))
          .setMessage(Lang.getString(R.string.Tgx101UnofficialText))
          .setPositiveButton(Lang.getString(R.string.Tgx101UnofficialOk), null)
          .setNeutralButton(Lang.getString(R.string.Tgx101UnofficialGet), (d, w) -> {
            try {
              activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_PAGE)));
            } catch (Throwable ignored) { }
          })
          .show();
      } catch (Throwable ignored) { }
    }, 2500);
  }

  @SuppressWarnings("deprecation")
  private static String certificateOf (Activity activity) {
    try {
      PackageManager pm = activity.getPackageManager();
      Signature[] signatures;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageInfo info = pm.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        signatures = info.signingInfo != null ? info.signingInfo.getApkContentsSigners() : null;
      } else {
        signatures = pm.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
      }
      if (signatures == null || signatures.length == 0) return null;
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
      StringBuilder b = new StringBuilder();
      for (byte x : digest) b.append(String.format("%02x", x));
      return b.toString();
    } catch (Throwable t) {
      return null;
    }
  }
}
