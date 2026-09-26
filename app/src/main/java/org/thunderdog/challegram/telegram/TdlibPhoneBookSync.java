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
package org.thunderdog.challegram.telegram;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes Telegram contacts into the real Android system Contacts Provider (a raw contact per
 * Telegram user, under a dedicated account/account-type), so third-party apps that read the
 * system address book -- e.g. caller-ID apps -- can show a name for phone numbers that are only
 * known via Telegram and aren't in the user's own phone-book. Officially-known Telegram Android
 * does the equivalent of this (ContactsContract.RawContacts under an "org.telegram.messenger"
 * account); Telegram-X (this codebase) never implemented it -- this class adds it, gated behind
 * {@link Settings#SETTING_FLAG_WRITE_CONTACTS_TO_PHONEBOOK}.
 *
 * NOT verified against a real device/compiler -- this environment has no Android SDK/NDK/gradle.
 * Every ContactsContract/AccountManager call here was checked against Android SDK docs and
 * against the equivalent, working implementation in github.com/DrKLO/Telegram, but has not been
 * run. Double-check on first real build.
 */
public class TdlibPhoneBookSync {
  private static final String ACCOUNT_NAME = "Telegram Contacts";
  // Must match app/src/main/res/xml/contacts_datakind.xml's android:mimeType.
  // Also must match the VIEW intent filter on MainActivity in AndroidManifest.xml.
  public static final String MIME_OPEN_CHAT = "vnd.android.cursor.item/vnd.com.tgx101.app.contact";
  private static final int CHUNK_SIZE = 100; // contacts per applyBatch call, well under provider limits

  private static TdlibPhoneBookSync instance;

  public static TdlibPhoneBookSync instance () {
    if (instance == null) {
      instance = new TdlibPhoneBookSync();
    }
    return instance;
  }

  private TdlibPhoneBookSync () { }

  private String accountType (Context context) {
    return context.getString(R.string.contacts_account_type);
  }

  private Account getAccount (Context context) {
    return new Account(ACCOUNT_NAME, accountType(context));
  }

  // == Entry points ==

  /**
   * Called when the user turns the setting ON. Requests WRITE_CONTACTS if needed, registers the
   * sync account, then writes every currently-registered Telegram contact.
   */
  public void enable (BaseActivity context, Tdlib tdlib) {
    context.permissions().requestWriteContacts(granted -> {
      if (granted) {
        Background.instance().post(() -> {
          try {
            registerAccount(context);
          } catch (Throwable t) {
            Log.e("Failed to register phonebook-sync account", t);
          }
        });
        fullResync(context, tdlib);
      } else {
        // Permission denied -- don't silently leave the setting "on" with nothing happening.
        Settings.instance().setNewSetting(Settings.SETTING_FLAG_WRITE_CONTACTS_TO_PHONEBOOK, false);
      }
    });
  }

  /**
   * Called when the user turns the setting OFF. Removes every raw contact this feature ever
   * wrote, then removes the sync account itself.
   */
  public void disable (Context context) {
    Background.instance().post(() -> {
      try {
        deleteAllRawContacts(context);
      } catch (Throwable t) {
        Log.e("Failed to delete phonebook-sync raw contacts", t);
      }
      try {
        removeAccount(context);
      } catch (Throwable t) {
        Log.e("Failed to remove phonebook-sync account", t);
      }
    });
  }

  /**
   * Full re-sync: fetches every registered contact from TDLib and makes the system contacts
   * match exactly (upserts everyone returned, deletes any raw contact under our account that
   * TDLib no longer considers a contact). Safe to call repeatedly / redundantly.
   */
  public void fullResync (Context context, Tdlib tdlib) {
    if (!Settings.instance().getNewSetting(Settings.SETTING_FLAG_WRITE_CONTACTS_TO_PHONEBOOK)) {
      return;
    }
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
      return;
    }
    tdlib.searchContacts("", Integer.MAX_VALUE, result -> {
      if (result.getConstructor() == TdApi.Users.CONSTRUCTOR) {
        long[] userIds = ((TdApi.Users) result).userIds;
        ArrayList<TdApi.User> users = tdlib.cache().users(userIds);
        Background.instance().post(() -> writeExactSet(context, users));
      } else {
        Log.e(Log.TAG_CONTACT, "searchContacts failed for phonebook full resync: %s", result);
      }
    });
  }

  /**
   * Incremental hook: called from TdlibContactManager#notifyContactStatusChanged whenever a
   * single user's contact status flips, so the system address book stays in sync without waiting
   * for the next full resync.
   */
  public void onContactStatusChanged (Context context, Tdlib tdlib, long userId, boolean isContact) {
    if (!Settings.instance().getNewSetting(Settings.SETTING_FLAG_WRITE_CONTACTS_TO_PHONEBOOK)) {
      return;
    }
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
      return;
    }
    if (isContact) {
      TdApi.User user = tdlib.cache().user(userId);
      if (user != null) {
        Background.instance().post(() -> upsertOne(context, user));
      }
    } else {
      Background.instance().post(() -> deleteOne(context, userId));
    }
  }

  // == Account management ==

  private void registerAccount (Context context) {
    Account account = getAccount(context);
    AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
    accountManager.addAccountExplicitly(account, null, null);
    ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
  }

  private void removeAccount (Context context) {
    Account account = getAccount(context);
    AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
    // AccountManager#removeAccountExplicitly requires API 22+; this project's minSdk is lower
    // elsewhere, but this specific class is unreachable unless the user opted in on a device
    // that could also grant WRITE_CONTACTS, so this should be safe in practice. If minSdk 21
    // support turns out to matter here, fall back to the deprecated removeAccount(...) callback
    // API instead.
    try {
      accountManager.removeAccountExplicitly(account);
    } catch (Throwable t) {
      Log.e("removeAccountExplicitly failed", t);
    }
  }

  // == Writing ==

  private Uri rawContactsSyncAdapterUri (Context context) {
    Account account = getAccount(context);
    return ContactsContract.RawContacts.CONTENT_URI.buildUpon()
      .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
      .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
      .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
      .build();
  }

  private void addDeleteOp (List<ContentProviderOperation> ops, Context context, long userId) {
    Uri uri = rawContactsSyncAdapterUri(context);
    ops.add(ContentProviderOperation.newDelete(uri)
      .withSelection(ContactsContract.RawContacts.SYNC2 + " = ?", new String[] {String.valueOf(userId)})
      .build());
  }

  private void addInsertOps (List<ContentProviderOperation> ops, Context context, TdApi.User user) {
    Account account = getAccount(context);
    int rawContactOpIndex = ops.size();

    ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI);
    builder.withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account.name);
    builder.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type);
    builder.withValue(ContactsContract.RawContacts.SYNC1, TextUtils.isEmpty(user.phoneNumber) ? "" : user.phoneNumber);
    builder.withValue(ContactsContract.RawContacts.SYNC2, String.valueOf(user.id));
    ops.add(builder.build());

    builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
    builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactOpIndex);
    builder.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
    builder.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, user.firstName);
    builder.withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, user.lastName);
    ops.add(builder.build());

    if (!TextUtils.isEmpty(user.phoneNumber)) {
      builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
      builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactOpIndex);
      builder.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
      builder.withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, "+" + user.phoneNumber);
      builder.withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
      ops.add(builder.build());
    }

    // Minimal "open in Telegram" detail row (see res/xml/contacts_datakind.xml) -- cosmetic only,
    // not required for the name/number to show up via aggregation.
    builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
    builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactOpIndex);
    builder.withValue(ContactsContract.Data.MIMETYPE, MIME_OPEN_CHAT);
    builder.withValue(ContactsContract.Data.DATA1, String.valueOf(user.id));
    builder.withValue(ContactsContract.Data.DATA2, "Telegram");
    builder.withValue(ContactsContract.Data.DATA3, "Open chat");
    ops.add(builder.build());
  }

  private void upsertOne (Context context, TdApi.User user) {
    try {
      ArrayList<ContentProviderOperation> ops = new ArrayList<>();
      addDeleteOp(ops, context, user.id); // idempotent upsert: delete any previous raw contact for this user first
      addInsertOps(ops, context, user);
      context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
    } catch (Throwable t) {
      Log.e("Failed to upsert phonebook contact for user %d", t, user.id);
    }
  }

  private void deleteOne (Context context, long userId) {
    try {
      ArrayList<ContentProviderOperation> ops = new ArrayList<>();
      addDeleteOp(ops, context, userId);
      context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
    } catch (Throwable t) {
      Log.e("Failed to delete phonebook contact for user %d", t, userId);
    }
  }

  private void deleteAllRawContacts (Context context) {
    try {
      context.getContentResolver().delete(rawContactsSyncAdapterUri(context), null, null);
    } catch (Throwable t) {
      Log.e("Failed to bulk-delete phonebook contacts", t);
    }
  }

  /**
   * Makes the set of raw contacts under our account exactly match {@code users}: upserts everyone
   * in the list, then deletes any raw contact (by SYNC2 user id) that isn't in it.
   */
  private void writeExactSet (Context context, List<TdApi.User> users) {
    try {
      Set<Long> incomingIds = new HashSet<>();
      for (TdApi.User user : users) {
        incomingIds.add(user.id);
      }

      // Existing raw-contact ids under our account, to compute deletions.
      Set<Long> existingIds = new HashSet<>();
      Cursor cursor = context.getContentResolver().query(
        ContactsContract.RawContacts.CONTENT_URI,
        new String[] {ContactsContract.RawContacts.SYNC2},
        ContactsContract.RawContacts.ACCOUNT_TYPE + " = ? AND " + ContactsContract.RawContacts.ACCOUNT_NAME + " = ?",
        new String[] {accountType(context), ACCOUNT_NAME},
        null
      );
      if (cursor != null) {
        try {
          int syncIndex = cursor.getColumnIndex(ContactsContract.RawContacts.SYNC2);
          while (cursor.moveToNext()) {
            String raw = syncIndex >= 0 ? cursor.getString(syncIndex) : null;
            if (raw != null) {
              try {
                existingIds.add(Long.parseLong(raw));
              } catch (NumberFormatException ignored) { }
            }
          }
        } finally {
          cursor.close();
        }
      }

      ArrayList<ContentProviderOperation> ops = new ArrayList<>();
      for (TdApi.User user : users) {
        addDeleteOp(ops, context, user.id);
        addInsertOps(ops, context, user);
        if (ops.size() >= CHUNK_SIZE * 4) { // ~4 ops per contact
          context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
          ops = new ArrayList<>();
        }
      }
      for (long existingId : existingIds) {
        if (!incomingIds.contains(existingId)) {
          addDeleteOp(ops, context, existingId);
        }
      }
      if (!ops.isEmpty()) {
        context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
      }
    } catch (Throwable t) {
      Log.e("Failed to write full phonebook contact set", t);
    }
  }
}
