package dev.niltsiar.tvhome.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.robolectric.Shadows.shadowOf

/** Installs [pkg] with one exported LEANBACK_LAUNCHER activity, `pkg/.Main`, into Robolectric's PackageManager. */
internal fun installTvApp(
    context: Context,
    pkg: String,
) {
    val component = ComponentName(pkg, "$pkg.Main")
    val application = ApplicationInfo().apply { packageName = pkg }
    val activity =
        ActivityInfo().apply {
            packageName = pkg
            name = component.className
            exported = true
            applicationInfo = application
            nonLocalizedLabel = pkg
        }
    shadowOf(context.packageManager).apply {
        installPackage(
            PackageInfo().apply {
                packageName = pkg
                applicationInfo = application
            },
        )
        addOrUpdateActivity(activity)
        addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) })
    }
}
