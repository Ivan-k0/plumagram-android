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
package org.thunderdog.challegram.sync;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Bundle;

// Stub authenticator for the "Telegram contacts" phonebook-sync account (see
// TdlibPhoneBookSync). Not user-creatable -- the account is only ever added
// programmatically via AccountManager#addAccountExplicitly.
public class ContactsPhoneBookAuthenticator extends AbstractAccountAuthenticator {
  public ContactsPhoneBookAuthenticator (Context context) {
    super(context);
  }

  @Override
  public Bundle editProperties (AccountAuthenticatorResponse r, String s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Bundle addAccount (AccountAuthenticatorResponse r, String s, String s2, String[] strings, Bundle bundle) throws NetworkErrorException {
    return null;
  }

  @Override
  public Bundle confirmCredentials (AccountAuthenticatorResponse r, Account account, Bundle bundle) throws NetworkErrorException {
    return null;
  }

  @Override
  public Bundle getAuthToken (AccountAuthenticatorResponse r, Account account, String s, Bundle bundle) throws NetworkErrorException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getAuthTokenLabel (String s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Bundle updateCredentials (AccountAuthenticatorResponse r, Account account, String s, Bundle bundle) throws NetworkErrorException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Bundle hasFeatures (AccountAuthenticatorResponse r, Account account, String[] strings) throws NetworkErrorException {
    throw new UnsupportedOperationException();
  }
}
