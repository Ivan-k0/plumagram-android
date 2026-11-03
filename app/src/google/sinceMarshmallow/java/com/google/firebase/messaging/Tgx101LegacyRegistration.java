package com.google.firebase.messaging;

import com.google.android.gms.tasks.Task;

import java.lang.reflect.Field;

/**
 * TGx101 (user 2026-11-03, people with microG: «FCM Registration failed!»): with Google services 26.12+ the library
 * registers over the new CloudMessaging API. microG 0.3.17 calls itself that new but doesn't have the API and answers
 * API_DISABLED. This registers the way the library does for older Google services (the old Rpc), which microG knows.
 * Lives in Firebase's package to reach its package-private GmsRpc.
 */
public final class Tgx101LegacyRegistration {
  private Tgx101LegacyRegistration () { }

  public static Task<String> register (FirebaseMessaging messaging) throws ReflectiveOperationException {
    // the field's name is changed by R8, its type is not lost
    for (Field field : FirebaseMessaging.class.getDeclaredFields()) {
      if (field.getType() == GmsRpc.class) {
        field.setAccessible(true);
        return ((GmsRpc) field.get(messaging)).getToken(true);
      }
    }
    throw new NoSuchFieldException("GmsRpc");
  }
}
