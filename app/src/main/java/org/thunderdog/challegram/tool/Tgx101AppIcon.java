package org.thunderdog.challegram.tool;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.thunderdog.challegram.R;

/**
 * TGx101 (user 2026-10-14): the app icon to choose from — the launcher entry is one of the {@code .Tgx101Icon*} aliases
 * in the manifest, exactly one enabled. Android shows it on the home screen, in the app drawer and in recents; the app's
 * own entry in the phone's settings keeps the standard icon (that one is fixed in the APK).
 */
public final class Tgx101AppIcon {
  private Tgx101AppIcon () { }

  private static final String[] ALIASES = {"Default", "Paper", "Graphite", "Oled", "Facets", "Rainbow"};
  public static final int[] NAMES = {
    R.string.Tgx101AppIconDefault, R.string.Tgx101AppIconPaper, R.string.Tgx101AppIconGraphite,
    R.string.Tgx101AppIconOled, R.string.Tgx101AppIconFacets, R.string.Tgx101AppIconRainbow
  };

  /** Adaptive icons only: before Android 8 the choice isn't offered */
  public static boolean isAvailable () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
  }

  private static ComponentName component (Context context, int index) {
    return new ComponentName(context.getPackageName(), "org.thunderdog.challegram.Tgx101Icon" + ALIASES[index]);
  }

  public static int current (Context context) {
    PackageManager pm = context.getPackageManager();
    for (int i = 0; i < ALIASES.length; i++) {
      int state;
      try {
        state = pm.getComponentEnabledSetting(component(context, i));
      } catch (Throwable t) {
        continue;
      }
      boolean enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && i == 0);
      if (enabled) return i;
    }
    return 0;
  }

  /** The chosen one is enabled first, so there is never a moment without any launcher entry */
  public static void apply (Context context, int index) {
    if (index < 0 || index >= ALIASES.length || index == current(context)) return;
    PackageManager pm = context.getPackageManager();
    pm.setComponentEnabledSetting(component(context, index), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    for (int i = 0; i < ALIASES.length; i++) {
      if (i == index) continue;
      // the standard alias is enabled in the manifest: switched off explicitly; the others back to their default (off)
      pm.setComponentEnabledSetting(component(context, i), i == 0 ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
    }
  }
}
