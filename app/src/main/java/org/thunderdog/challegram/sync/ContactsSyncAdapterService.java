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
package org.thunderdog.challegram.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class ContactsSyncAdapterService extends Service {
  private static ContactsSyncAdapter sSyncAdapter = null;
  private static final Object sSyncAdapterLock = new Object();

  @Override
  public void onCreate () {
    synchronized (sSyncAdapterLock) {
      if (sSyncAdapter == null) {
        sSyncAdapter = new ContactsSyncAdapter(getApplicationContext(), true);
      }
    }
  }

  @Override
  public IBinder onBind (Intent intent) {
    return sSyncAdapter.getSyncAdapterBinder();
  }
}
