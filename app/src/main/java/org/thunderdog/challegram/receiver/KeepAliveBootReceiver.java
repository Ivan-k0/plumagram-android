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
 */
package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thunderdog.challegram.service.KeepAliveService;
import org.thunderdog.challegram.tool.UI;

/** Restarts {@link KeepAliveService} after reboot and after the app is updated. */
public class KeepAliveBootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive (Context context, Intent intent) {
    String action = intent != null ? intent.getAction() : null;
    if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
      UI.initApp(context.getApplicationContext());
      KeepAliveService.sync(context);
    }
  }
}
