package dev.niltsiar.tvhome.data.inputs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The boot whose first Home has been handled. */
internal const val BOOT_PREFS = "boot"
internal const val BOOT_KEY = "live_tv_boot"

/**
 * Whether the first Home of this boot opens [pkg]: a known boot not handled yet, and an app that is not the launcher
 * itself ([self]), which would only reopen Home.
 */
internal fun openOnStartDue(
    boot: Int,
    handledBoot: Int,
    pkg: String?,
    self: String,
): Boolean = boot >= 0 && boot != handledBoot && !pkg.isNullOrEmpty() && pkg != self

/**
 * The app Home opens instead of itself: the Live TV app of boot_to_live_tv, or the app picked in Launcher settings with
 * open_on_start_choice, which also opens on each wake with open_on_start_on_wake. boot_to_live_tv wins at boot.
 * Once per boot, keyed on Settings.Global.BOOT_COUNT and committed to disk before the app starts: on a low-memory TV
 * the app can push the launcher out of memory, and a write still in flight is lost then, so every Home press would
 * open the app again. [pick] is the picked package, empty for Off. [validated] says whether the TV reaches the internet,
 * for open_on_start_network_wait. Everything reads [config] and [pick] when it is used.
 */
public class OpenOnStart(
    context: Context,
    private val config: StateFlow<TvConfig>,
    private val pick: StateFlow<String>,
    private val validated: Flow<Boolean>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val context = context.applicationContext
    private val liveTv = LiveTv(this.context, config)

    /** The first Home of a boot: [open] gets the intent of the app to open, once per boot. */
    public suspend fun openAfterBoot(open: (Intent) -> Unit) {
        val current = config.value
        val picked = pick.value.takeIf { current.openOnStartChoice }
        val target = if (current.bootToLiveTv) current.liveTvPackage else picked
        if (target.isNullOrEmpty()) return
        val intent =
            withContext(io) {
                val boot = bootCount()
                val prefs = bootPrefs()
                if (!openOnStartDue(boot, prefs.getInt(BOOT_KEY, -1), target, context.packageName)) return@withContext null
                if (!prefs.edit().putInt(BOOT_KEY, boot).commit()) return@withContext null
                if (current.bootToLiveTv) liveTv.appIntent() else pickIntent(target)
            } ?: return
        openWhenReady(intent, open)
    }

    /** A pick takes effect from the next boot: the Home that follows it stays Home. */
    public suspend fun skipThisBoot() {
        withContext(io) { bootPrefs().edit().putInt(BOOT_KEY, bootCount()).commit() }
    }

    /**
     * One item for each wake of the screen (ACTION_SCREEN_ON) while open_on_start_choice and open_on_start_on_wake are
     * both on. The receiver exists only while someone collects and both are on, so turning them on or off applies at once.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public val wakes: Flow<Unit> =
        config
            .map { it.openOnStartChoice && it.openOnStartOnWake }
            .distinctUntilChanged()
            .transformLatest { on -> if (on) emitAll(screenOn()) }

    /** A wake: [open] gets the intent of the picked app, after the network wait. Nothing without a pick or when it is gone. */
    public suspend fun openOnWake(open: (Intent) -> Unit) {
        val current = config.value
        val app = pick.value
        if (!current.openOnStartChoice || !current.openOnStartOnWake || app.isEmpty() || app == context.packageName) return
        val intent = withContext(io) { pickIntent(app) } ?: return
        openWhenReady(intent, open)
    }

    /** The apps with a TV entry point except this launcher, one entry per package, by label. */
    public suspend fun apps(): List<OpenOnStartApp> =
        withContext(io) {
            val pm = context.packageManager
            val tv = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            pm
                .queryIntentActivities(tv, 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { OpenOnStartApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }

    /** The label of [pkg], or null when it is gone. */
    public fun label(pkg: String): String? =
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    private fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun bootPrefs() = context.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE)

    /** The app's TV entry point, or its phone one; null when it is uninstalled or disabled, so Home stays. */
    private fun pickIntent(pkg: String): Intent? =
        context.packageManager.let { it.getLeanbackLaunchIntentForPackage(pkg) ?: it.getLaunchIntentForPackage(pkg) }

    private fun screenOn(): Flow<Unit> =
        callbackFlow {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        trySend(Unit)
                    }
                }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
            awaitClose { context.unregisterReceiver(receiver) }
        }

    /** Home stays on screen until the network is validated or open_on_start_network_wait is over. Leaving Home cancels the wait. */
    private suspend fun openWhenReady(
        intent: Intent,
        open: (Intent) -> Unit,
    ) {
        val waitMs = config.value.openOnStartNetworkWait * 1000L
        if (waitMs > 0) withTimeoutOrNull(waitMs) { validated.first { it } }
        open(intent)
    }
}
