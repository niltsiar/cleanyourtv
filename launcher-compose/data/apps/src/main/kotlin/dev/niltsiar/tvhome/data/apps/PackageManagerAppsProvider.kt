package dev.niltsiar.tvhome.data.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.ContextCompat
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val TAG = "TvHome.Apps"
private const val TOP_BAR_ICON_DP = 22

/**
 * The tiles Home shows, read the way the reference launcher reads them: the TV launcher activities, plus phone
 * launcher activities of packages added in the library or through library_apps, ordered by [arrange].
 *
 * [apps] emits the labels first, then the same list with banners or icons decoded to a tile 16:9 at the width
 * [tileWidthPx] gives for the theme's columns, and again, images included, each time the installed apps change. The change comes from package broadcasts while
 * Home is started ([onStart], [onStop]), and from PackageManager's changed-packages log when Home starts again,
 * which catches what happened while it was stopped.
 */
public class PackageManagerAppsProvider(
    private val context: Context,
    private val config: TvConfig,
) {
    private val images =
        with(context.resources.displayMetrics) {
            val width = tileWidthPx(widthPixels, config.columns, density)
            TileImages(context, width, width * 9 / 16)
        }
    private val orderFile = File(context.filesDir, ORDER_FILE_NAME)
    internal val library = LibraryStore(context.filesDir)
    private val refreshes = MutableStateFlow(0)

    /** Counts the [trimImages] calls. */
    private val trims = MutableStateFlow(0)

    /** Set by [trimImages], cleared when a list with images goes out: the next start loads the images again. */
    @Volatile
    private var imagesDropped = false
    private val versions = refreshes.combine(trims, ::Pair)
    private val packageFilter =
        IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
    private val packageReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                // An update sends REMOVED then ADDED: keep the tile until the new APK is in.
                if (intent.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                requestRefresh()
            }
        }
    private var receiving = false

    /** The activities of the list on screen, for [heroArt]. */
    @Volatile
    private var infos: Map<String, ResolveInfo> = emptyMap()
    private val heroArts = ConcurrentHashMap<String, ImageBitmap>()

    /** The PackageManager sequence number the last load started from, or null before the first load. */
    @Volatile
    private var packageSequence: Int? = null

    public val apps: Flow<AppList> =
        flow {
            var seen = refreshes.value
            var trimmed = trims.value
            var loaded = load()
            emit(loaded.toAppList(withImages = false))
            emit(loaded.toAppList(withImages = true))
            imagesDropped = false
            // A refresh shows no bare labels first: that would blank every banner for a frame.
            versions.collect { (version, trim) ->
                if (version != seen) {
                    seen = version
                    trimmed = trim
                    loaded = load()
                    emit(loaded.toAppList(withImages = true))
                    imagesDropped = false
                } else if (trim != trimmed) {
                    trimmed = trim
                    emit(loaded.toAppList(withImages = false))
                }
            }
        }.flowOn(Dispatchers.IO)

    /** The top_bar_apps that can be opened, loaded again each time the installed apps change. */
    public val topBarApps: Flow<List<AppEntry>> =
        flow {
            var seen = refreshes.value
            var trimmed = trims.value
            var bar = loadTopBarApps()
            emit(bar)
            versions.collect { (version, trim) ->
                if (version != seen) {
                    seen = version
                    trimmed = trim
                    bar = loadTopBarApps()
                    emit(bar)
                } else if (trim != trimmed) {
                    trimmed = trim
                    emit(bar.map { it.copy(image = null) })
                }
            }
        }.flowOn(Dispatchers.IO)

    /**
     * The softened art of the tile [key] for the cinematic shelf, made on the first ask and kept until the app list
     * changes; null when the tile is gone or has no art.
     */
    public suspend fun heroArt(key: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            heroArts[key] ?: run {
                val loaded = infos
                val info = loaded[key] ?: return@run null
                val art =
                    try {
                        images.artwork(info.activityInfo) { info.loadIcon(context.packageManager) }?.let(::heroArtFrom)
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "no hero art for $key: $e")
                        null
                    }
                if (art != null && loaded === infos) heroArts[key] = art
                art
            }
        }

    /**
     * Keeps the owner's tile order: [keys] first to last. [docked] says how many lead the dock when fill_dock is off,
     * else it is -1. Loads the tiles again, sorted by it. False, with a log line, when the file could not be written.
     */
    public suspend fun saveOrder(
        keys: List<String>,
        docked: Int,
    ): Boolean =
        withContext(Dispatchers.IO) {
            writeOrder(orderFile, Order(Order.dockKey(config.dockApps), keys, docked)).also { if (it) requestRefresh() }
        }

    /** Counts the loads asked for: package changes and [libraryChanged]. A collector starts at the current count. */
    internal val changes: Flow<Int> get() = refreshes

    /** The owner's library choices changed: load the tiles again. */
    internal fun libraryChanged() = requestRefresh()

    /** The library choices with the config's library_apps added. */
    internal fun libraryChoices(): LibraryChoices = library.load().let { it.copy(added = it.added + config.libraryApps) }

    /**
     * Memory is short while Home is stopped: let go of the hero arts, and send the tiles and top-bar apps on again without
     * their images. Nothing is reordered or saved. The next [onStart] loads the images again. Main thread.
     */
    public fun trimImages() {
        heroArts.clear()
        imagesDropped = true
        trims.update { it + 1 }
    }

    /**
     * Home is on screen: follow package changes, and reload now if some happened while it was not or if [trimImages]
     * dropped the images. Main thread.
     */
    public fun onStart() {
        if (!receiving) {
            ContextCompat.registerReceiver(context, packageReceiver, packageFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiving = true
        }
        val changed = packageSequence?.let { context.packageManager.getChangedPackages(it) }
        if (imagesDropped || (changed != null && changed.packageNames.isNotEmpty())) requestRefresh()
    }

    /** Home left the screen: package changes wait for [onStart]. Main thread. */
    public fun onStop() {
        if (receiving) {
            context.unregisterReceiver(packageReceiver)
            receiving = false
        }
    }

    private fun loadTopBarApps() =
        loadTopBarApps(context, config.topBarApps, (TOP_BAR_ICON_DP * context.resources.displayMetrics.density).roundToInt())

    private fun requestRefresh() = refreshes.update { it + 1 }

    private class Loaded(
        val arrangement: Arrangement,
        val infos: Map<String, ResolveInfo>,
        val tvPackages: Set<String>,
    )

    private fun load(): Loaded {
        val pm = context.packageManager
        val since = packageSequence ?: 0
        packageSequence = pm.getChangedPackages(since)?.sequenceNumber ?: since
        val leanback = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        val tvPackages = leanback.mapTo(HashSet()) { it.activityInfo.packageName }
        val found = leanback.toMutableList()
        val choices = libraryChoices()
        if (choices.added.isNotEmpty()) {
            val seen = HashSet(tvPackages)
            val phone = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            phone.filterTo(found) { it.activityInfo.packageName in choices.added && seen.add(it.activityInfo.packageName) }
        }
        val infos = HashMap<String, ResolveInfo>()
        val candidates =
            found.map { info ->
                val activity = info.activityInfo
                val key = ComponentName(activity.packageName, activity.name).flattenToShortString()
                infos[key] = info
                Candidate(key, activity.packageName, info.loadLabel(pm).toString(), activity.exported)
            }
        val arrangement = arrange(candidates, tvPackages, config, readOrder(orderFile), context.packageName, choices)
        this.infos = infos
        heroArts.clear()
        return Loaded(arrangement, infos, tvPackages)
    }

    private fun Loaded.toAppList(withImages: Boolean): AppList {
        val pm = context.packageManager
        val apps =
            arrangement.tiles.map { tile ->
                val info = infos.getValue(tile.candidate.key)
                val art = if (withImages) tileArt(info, pm) else null
                AppEntry(
                    key = tile.candidate.key,
                    packageName = tile.candidate.packageName,
                    label = tile.label,
                    image = art?.image,
                    accent = art?.let { Color(it.accent) } ?: Color.Unspecified,
                    leanback = tile.candidate.packageName in tvPackages,
                    iconArt = art?.fromIcon == true,
                )
            }
        return AppList(apps, arrangement.docked)
    }

    /** One app's broken artwork must not take Home down: its tile shows the name instead. */
    private fun tileArt(
        info: ResolveInfo,
        pm: PackageManager,
    ) = try {
        images.load(info.activityInfo) { info.loadIcon(pm) }
    } catch (e: RuntimeException) {
        Log.w(TAG, "no artwork for ${info.activityInfo.packageName}: $e")
        TileArt(null, NO_ART_ACCENT)
    }
}
