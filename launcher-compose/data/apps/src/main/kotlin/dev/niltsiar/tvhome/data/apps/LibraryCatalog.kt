package dev.niltsiar.tvhome.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.LibraryApp
import dev.niltsiar.tvhome.core.model.LibraryArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val TAG = "TvHome.Library"
private const val ART_WIDTH_DP = 112

/** An app's label as of its last update, so a label is read from the app's resources once, not at each opening. */
private class KnownLabel(
    val updated: Long,
    val label: String,
)

/**
 * Every app the library offers: the TV and the phone launcher activities that can be started from outside, the first
 * entry of a package winning and the TV one preferred, less this launcher and library_excluded_apps. Each app says
 * whether it has a tile on Home.
 */
public class LibraryCatalog(
    private val context: Context,
    private val config: TvConfig,
    private val apps: PackageManagerAppsProvider,
) {
    private val labels = ConcurrentHashMap<String, KnownLabel>()
    private val arts = ConcurrentHashMap<String, LibraryArt>()

    @Volatile
    private var infos: Map<String, ResolveInfo> = emptyMap()
    private val images =
        with(context.resources.displayMetrics) {
            val width = (ART_WIDTH_DP * density).roundToInt()
            TileImages(context, width, width * 9 / 16)
        }

    /** Emits each time the installed apps or the Home choices change after the collection starts. */
    public val changes: Flow<Int> get() = apps.changes.drop(1)

    public suspend fun load(): List<LibraryApp> = withContext(Dispatchers.IO) { read() }

    /**
     * The art of one app for its card, made on the first ask and kept until the catalogue loads again or [release].
     * An app whose art fails to load gets none.
     */
    public suspend fun art(key: String): LibraryArt? =
        withContext(Dispatchers.IO) {
            arts[key] ?: run {
                val info = infos[key] ?: return@run null
                val art =
                    try {
                        images
                            .load(
                                info.activityInfo,
                            ) { info.loadIcon(context.packageManager) }
                            .let { LibraryArt(it.image, Color(it.accent)) }
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "no artwork for $key: $e")
                        return@run null
                    }
                if (infos[key] === info) arts[key] = art
                art
            }
        }

    /** The library closed: the art is not needed. */
    public fun release() {
        arts.clear()
    }

    /** Puts a package on Home or takes it off, and has Home load its tiles again. False when the choice was not saved. */
    public suspend fun setOnHome(
        packageName: String,
        onHome: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            apps.library.save(packageName, onHome).also { if (it) apps.libraryChanged() }
        }

    private fun read(): List<LibraryApp> {
        val pm = context.packageManager
        val hidden = config.hiddenApps.toSet()
        val excluded = config.libraryExcludedApps.toSet()
        val choices = apps.libraryChoices()
        val tvPackages = HashSet<String>()
        val unique = LinkedHashMap<String, ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (info in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val activity = info.activityInfo
                if (activity == null || !activity.exported) continue
                val pkg = activity.packageName
                if (category == Intent.CATEGORY_LEANBACK_LAUNCHER) tvPackages.add(pkg)
                if (pkg != context.packageName && pkg !in excluded) unique.putIfAbsent(pkg, info)
            }
        }
        arts.clear()
        infos = unique.values.associateBy { ComponentName(it.activityInfo.packageName, it.activityInfo.name).flattenToShortString() }
        val result =
            unique.map { (pkg, info) ->
                var installed = 0L
                var updated = 0L
                try {
                    val packageInfo = pm.getPackageInfo(pkg, 0)
                    installed = packageInfo.firstInstallTime
                    updated = packageInfo.lastUpdateTime
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w(TAG, "$pkg has no package info: $e")
                }
                val known =
                    labels[pkg]?.takeIf { it.updated == updated } ?: KnownLabel(updated, labelOf(pm, info)).also { labels[pkg] = it }
                val key = ComponentName(pkg, info.activityInfo.name).flattenToShortString()
                LibraryApp(
                    app = AppEntry(key = key, packageName = pkg, label = known.label, leanback = pkg in tvPackages),
                    onHome = choices.showsOnHome(pkg, tvApp = pkg in tvPackages, hidden = pkg in hidden),
                    installedAt = installed,
                )
            }
        labels.keys.retainAll(unique.keys)
        return result
    }

    private fun labelOf(
        pm: PackageManager,
        info: ResolveInfo,
    ): String =
        try {
            cleanLabel(info.loadLabel(pm).toString(), info.activityInfo.packageName)
        } catch (e: RuntimeException) {
            Log.w(TAG, "no label for ${info.activityInfo.packageName}: $e")
            info.activityInfo.packageName
        }
}
