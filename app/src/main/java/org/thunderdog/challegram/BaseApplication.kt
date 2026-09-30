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
    Tgx101Diag.start(base) // TGx101: only in diagnostics builds
  }

  override fun onCreate() {
    Tgx101Diag.mark("Application.onCreate …")
    super.onCreate()
    Tgx101Diag.mark("Application.onCreate: base ready")
    Tgx101Diag.attach(this) // TGx101: lifecycle, screen and UI stall log (diagnostics builds only)
    // TGx101: startup lives in BaseApplicationStartup and is called by name. On Android 4 this class is
    // verified before MultiDex adds the secondary dex files, so it must not reference the rest of the app.
    Class.forName(javaClass.name + "Startup").getMethod("onCreate", android.app.Application::class.java).invoke(null, this)
  }

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().build()
}
