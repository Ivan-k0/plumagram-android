package org.thunderdog.challegram.service

import android.app.Service
import org.drinkless.tdlib.TdApi.DeviceToken
import org.thunderdog.challegram.Log
import org.thunderdog.challegram.TDLib
import org.thunderdog.challegram.Tgx101Diag
import org.thunderdog.challegram.telegram.TdlibManager
import org.thunderdog.challegram.unsorted.AppContext
import org.thunderdog.challegram.unsorted.Settings
import tgx.bridge.PushManager
import tgx.td.stringify

class PushHandler : PushManager {
  override fun onNewToken(service: Service, token: DeviceToken) {
    AppContext.init(service.applicationContext)
    log("onNewToken %s, sending to all accounts", token)
    Tgx101Diag.mark("push: new token from " + token.javaClass.simpleName + ", sending to accounts")
    TdlibManager.instance().runWithWakeLock { manager ->
      manager.setDeviceToken(token)
    }
  }

  override fun onMessageReceived(service: Service, message: Map<String, Any>, sentTime: Long, ttl: Int) {
    AppContext.init(service.applicationContext)
    val pushId = Settings.instance().newPushId()
    Tgx101Diag.mark("push: arrived #" + pushId + ", sent " + (if (sentTime > 0) ((System.currentTimeMillis() - sentTime) / 1000).toString() + " s ago" else "?") + ", ttl " + ttl + ", " + message.size + " fields")

    val payload = stringify(message)

    val pushProcessor = PushProcessor(service)
    pushProcessor.processPush(pushId, payload, sentTime, ttl)
  }

  override fun log(format: String, vararg args: Any) {
    TDLib.Tag.notifications(format, args)
    Tgx101Diag.mark("push: " + Tgx101Diag.hideTokens(try { String.format(format, *args) } catch (e: Throwable) { format }))
  }

  override fun error(message: String, error: Throwable?) {
    TDLib.Tag.notifications("$message: ${Log.toString(error)}")
    Tgx101Diag.mark("push: ERROR " + message + ": " + Tgx101Diag.hideTokens(error?.let { it.javaClass.name + ": " + it.message + (it.cause?.let { c -> " / cause " + c.javaClass.name + ": " + c.message } ?: "") } ?: "null"))
  }
}