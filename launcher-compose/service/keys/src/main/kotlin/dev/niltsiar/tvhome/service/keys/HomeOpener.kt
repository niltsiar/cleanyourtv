package dev.niltsiar.tvhome.service.keys

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.niltsiar.tvhome.core.model.startActivitySafely

/** This app's Home activity, found the way Android finds it, so the service needs no class from the app module. */
internal fun Context.homeComponent(): ComponentName? =
    packageManager
        .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName), 0)
        .firstOrNull()
        ?.activityInfo
        ?.let { ComponentName(it.packageName, it.name) }

/** An intent for Home itself with [action], addressed to its activity. Null when the app has no Home activity. */
internal fun Context.homeIntent(action: String): Intent? =
    homeComponent()?.let { Intent(action).setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

/**
 * Brings this launcher to the front. With [stockPackage] (stock_home_redirect) still the resolved Home on Android 10 or
 * later, a Home intent would put this launcher in the home task and make it the home process, and the stock launcher, no
 * longer exempt from the limits on background activity starts, could not open apps for the remote's YouTube and Netflix
 * buttons (#1274). Home then starts like an app: CATEGORY_HOME without ACTION_MAIN is not a Home intent, but still sends
 * Home back to the first tile. With this launcher or Android's chooser as Home, the Home intent stays.
 */
internal class HomeOpener(
    private val context: Context,
    private val stockPackage: String?,
    private val stockActivities: Set<String>,
) {
    fun open() {
        val component = if (stockHomeStays()) context.homeComponent() else null
        val intent = if (component != null) Intent().setComponent(component) else Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
        context.startActivitySafely(
            intent
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    private fun stockHomeStays(): Boolean {
        if (stockActivities.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val home =
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        return home != null && home.activityInfo.packageName == stockPackage
    }
}

/** The stock launcher's HOME activities, disabled ones included, by class name. Empty for no [stockPackage]. */
@Suppress("DEPRECATION")
internal fun stockHomeActivities(
    context: Context,
    stockPackage: String?,
): Set<String> {
    if (stockPackage == null) return emptySet()
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(stockPackage)
    return context.packageManager
        .queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)
        .map { it.activityInfo.name }
        .toSet()
}
