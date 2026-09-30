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
package org.thunderdog.challegram

import android.app.Application
import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.thunderdog.challegram.push.FirebaseDeviceTokenRetriever
import org.thunderdog.challegram.service.PushHandler
import org.thunderdog.challegram.telegram.TdlibNotificationUtils
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.unsorted.Settings
import tgx.bridge.DeviceTokenRetriever
import tgx.bridge.DeviceTokenRetrieverFactory
import tgx.bridge.PushManagerBridge
import tgx.extension.TelegramXExtension

/** TGx101: what BaseApplication.onCreate used to do; called by name from there (see the comment in BaseApplication). */
object BaseApplicationStartup {
  lateinit var scope: CoroutineScope

  @JvmStatic
  fun onCreate(application: Application) {
    val applicationContext = application.applicationContext
    scope = MainScope()

    // TGx101: OpenStreetMap tiles require an identifying user agent
    org.osmdroid.config.Configuration.getInstance().userAgentValue = application.packageName

    PushManagerBridge.initialize(
      scope,

      PushHandler(),
      object : DeviceTokenRetrieverFactory {
        override fun onCreateNewTokenRetriever(context: Context): DeviceTokenRetriever {
          val defaultTokenRetriever = FirebaseDeviceTokenRetriever()
          val tokenRetriever = TelegramXExtension.createNewTokenRetriever(context)
          return tokenRetriever?.takeIf {
            !BuildConfig.EXPERIMENTAL && (
              Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_FORCE_ALTERNATIVE_PUSH_SERVICE) ||
              !defaultTokenRetriever.isAvailable(applicationContext)
            )
          } ?: defaultTokenRetriever
        }
      }
    )

    UI.initApp(applicationContext)
    org.thunderdog.challegram.proxy.Tgx101WebProxy.init() // TGx101: WEB proxy carrier follows the current proxy
    org.thunderdog.challegram.service.Tgx101LockScreenNotifications.register(application) // TGx101: Vivo lock screen
    Tgx101Diag.mark("Application.onCreate: UI ready")

    if (!BuildConfig.EXPERIMENTAL) {
      val deviceTokenRetriever = TdlibNotificationUtils.getDeviceTokenRetriever()
      TelegramXExtension.configure(application, deviceTokenRetriever)
      if (deviceTokenRetriever !is FirebaseDeviceTokenRetriever) {
        FirebaseMessaging.getInstance().isAutoInitEnabled = false
      }
    }
  }
}
