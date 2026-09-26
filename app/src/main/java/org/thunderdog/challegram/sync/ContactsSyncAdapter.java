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

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

// Sync adapter registered against the real "com.android.contacts" authority so our
// account is a valid/syncable source in Android's Contacts app. All the actual writing
// happens on demand (TdlibPhoneBookSync, driven by TDLib contact-list events and by the
// Settings toggle) -- onPerformSync is intentionally a no-op, this class only exists
// because AbstractThreadedSyncAdapter is required for an account to be considered
// syncable against that authority.
public class ContactsSyncAdapter extends AbstractThreadedSyncAdapter {
  ContactsSyncAdapter (Context context, boolean autoInitialize) {
    super(context, autoInitialize);
  }

  @Override
  public void onPerformSync (Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
    // no-op by design, see class comment
  }
}
