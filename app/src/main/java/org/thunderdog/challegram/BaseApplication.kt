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
 *
 * File created on 05/04/2015 at 08:53
 */
package org.thunderdog.challegram

import android.content.Context
import androidx.work.Configuration
import tgx.flavor.TgxApplication

class BaseApplication : TgxApplication(), Configuration.Provider {
  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    diag("start", Context::class.java, base) // TGx101: only in diagnostics builds
  }

  override fun onCreate() {
    diag("mark", String::class.java, "Application.onCreate …")
    super.onCreate()
    diag("mark", String::class.java, "Application.onCreate: base ready")
    diag("attach", android.app.Application::class.java, this) // TGx101: lifecycle, screen and UI stall log (diagnostics builds only)
    // TGx101: startup lives in BaseApplicationStartup and is called by name. On Android 4 this class is
    // verified before MultiDex adds the secondary dex files, so it must not reference the rest of the app.
    Class.forName(javaClass.name + "Startup").getMethod("onCreate", android.app.Application::class.java).invoke(null, this)
  }

  /** TGx101 (BlackBerry Z30, Android 4.3, 2026-10-06: NoClassDefFoundError at start): Tgx101Diag is called by name too —
   *  a direct reference failed verification whenever the class landed outside the main dex. */
  private fun diag(method: String, type: Class<*>, arg: Any) {
    try {
      Class.forName("org.thunderdog.challegram.Tgx101Diag").getMethod(method, type).invoke(null, arg)
    } catch (t: Throwable) {
      android.util.Log.w("tgx", "diag $method", t)
    }
  }

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().build()
}
