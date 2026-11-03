package org.thunderdog.challegram.push

import android.content.Context
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Tgx101LegacyRegistration
import org.drinkless.tdlib.TdApi.DeviceTokenFirebaseCloudMessaging
import org.thunderdog.challegram.service.DefaultFirebaseTokenRetriever
import tgx.bridge.PushManagerBridge
import tgx.bridge.TokenRetrieverListener

class FirebaseDeviceTokenRetriever : DefaultFirebaseTokenRetriever() {
  override fun fetchDeviceToken(context: Context, listener: TokenRetrieverListener) {
    try {
      PushManagerBridge.log("FirebaseMessaging: registering...")
      val onRegistered = {
        PushManagerBridge.log("FirebaseMessaging: successfully registered, obtaining installation ID...")
        FirebaseInstallations.getInstance().id
          .addOnSuccessListener { installationId ->
            PushManagerBridge.log("FirebaseMessaging: successfully fetched installation ID: \"%s\"", installationId)
            listener.onTokenRetrievalSuccess(DeviceTokenFirebaseCloudMessaging(installationId, true))
          }
          .addOnFailureListener { e: Exception? ->
            val errorName = extractFirebaseErrorName(e!!)
            PushManagerBridge.error(
              "FirebaseMessaging: installation ID fetch failed ($errorName)",
              e
            )
            listener.onTokenRetrievalError(errorName, e)
          }
      }
      FirebaseMessaging.getInstance().register().addOnSuccessListener(OnSuccessListener { _ ->
        onRegistered()
      }).addOnFailureListener { e: Exception? ->
        val errorName = extractFirebaseErrorName(e!!)
        PushManagerBridge.error(
          "FirebaseMessaging: registration failed ($errorName)",
          e
        )
        if (isNewApiMissing(e)) {
          // TGx101: microG — the new registration API isn't there, the old way is
          try {
            PushManagerBridge.log("FirebaseMessaging: new API missing, registering the old way...")
            Tgx101LegacyRegistration.register(FirebaseMessaging.getInstance())
              .addOnSuccessListener { _ -> onRegistered() }
              .addOnFailureListener { e2: Exception ->
                val errorName2 = extractFirebaseErrorName(e2)
                PushManagerBridge.error("FirebaseMessaging: old way registration failed ($errorName2)", e2)
                listener.onTokenRetrievalError(errorName2, e2)
              }
          } catch (t: Throwable) {
            PushManagerBridge.error("FirebaseMessaging: old way unavailable", t)
            listener.onTokenRetrievalError(errorName, e)
          }
        } else {
          listener.onTokenRetrievalError(errorName, e)
        }
      }
    } catch (e: Throwable) {
      PushManagerBridge.error("FirebaseMessaging: token fetch failed with error", e)
      listener.onTokenRetrievalError("FIREBASE_REQUEST_ERROR", e)
    }
  }

  private fun isNewApiMissing(e: Throwable): Boolean {
    var t: Throwable? = e
    while (t != null) {
      val message = t.message ?: ""
      if (message.contains("CloudMessaging.API") || message.contains("API_DISABLED")) return true
      t = t.cause
    }
    return false
  }
}
