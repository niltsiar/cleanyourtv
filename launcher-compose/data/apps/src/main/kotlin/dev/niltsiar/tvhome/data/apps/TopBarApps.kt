package dev.niltsiar.tvhome.data.apps

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.niltsiar.tvhome.core.model.AppEntry

private const val TAG = "TvHome.TopBarApps"

/**
 * The top_bar_apps that can be opened: each package's TV launch intent, else its phone one; a package with neither
 * (not installed, or disabled) is left out. The entry is the activity that intent starts, with its icon drawn
 * [iconPx] square, so it opens through [AppLauncher] like a tile and launch_actions apply to it.
 */
internal fun loadTopBarApps(
    context: Context,
    packages: List<String>,
    iconPx: Int,
): List<AppEntry> {
    val pm = context.packageManager
    return packages.distinct().mapNotNull { pkg ->
        val tv = pm.getLeanbackLaunchIntentForPackage(pkg)
        val component = (tv ?: pm.getLaunchIntentForPackage(pkg))?.component ?: return@mapNotNull null
        try {
            val info = pm.getApplicationInfo(pkg, 0)
            AppEntry(
                key = component.flattenToShortString(),
                packageName = pkg,
                label = info.loadLabel(pm).toString(),
                image = info.loadIcon(pm).toBitmap(iconPx, iconPx).asImageBitmap(),
                leanback = tv != null,
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "no icon for $pkg: $e")
            null
        }
    }
}
