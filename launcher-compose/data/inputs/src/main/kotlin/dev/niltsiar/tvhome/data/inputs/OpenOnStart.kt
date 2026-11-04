package dev.niltsiar.tvhome.data.inputs

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The package Launcher settings' Open on start row saves, in the launcher_settings preferences; absent or empty means off. */
internal const val OPEN_ON_START_KEY = "open_on_start"
internal const val SETTINGS_PREFS = "launcher_settings"

/** The boot whose first Home has been handled, shared by boot_to_live_tv and the Open on start app. */
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

/** open_on_start_on_wake needs the Open on start row, which is where the app is picked. */
internal fun openOnStartOnWake(
    choice: Boolean,
    onWake: Boolean,
): Boolean = choice && onWake

/** The app's TV entry point, or its phone one; null when it is uninstalled or disabled, so Home stays. */
internal fun startIntent(
    pm: PackageManager,
    pkg: String,
): Intent? = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)

/**
 * boot_to_live_tv, or the Open on start app: the first time Home comes up after the TV starts, open that instead.
 * Once per boot, keyed on Settings.Global.BOOT_COUNT and committed to disk before the app starts: on a low-memory TV
 * the app can push the launcher out of memory, and a write still in flight is lost then, so every Home press would
 * open the app again. [validated] says whether the TV reaches the internet, for open_on_start_network_wait.
 */
public class OpenOnStart(
    context: Context,
    private val config: TvConfig,
    private val validated: Flow<Boolean>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val context = context.applicationContext
    private val liveTv = LiveTv(this.context, config)

    /** The first Home of a boot: [open] gets the intent of the Live TV app or the picked app, once per boot. */
    public suspend fun openAfterBoot(open: (Intent) -> Unit) {
        val app = pickedApp()
        if (!config.bootToLiveTv && app.isEmpty()) return
        val intent =
            withContext(io) {
                val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
                val prefs = context.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE)
                val target = if (config.bootToLiveTv) config.liveTvPackage else app
                if (!openOnStartDue(boot, prefs.getInt(BOOT_KEY, -1), target, context.packageName)) return@withContext null
                if (!prefs.edit().putInt(BOOT_KEY, boot).commit()) return@withContext null
                if (config.bootToLiveTv) liveTv.appIntent() else startIntent(context.packageManager, app)
            } ?: return
        openWhenReady(intent, open)
    }

    /**
     * open_on_start_on_wake: the screen came back on. The remote's power button is a light sleep on some sets, with
     * no boot for [openAfterBoot] to catch.
     */
    public suspend fun openOnWake(open: (Intent) -> Unit) {
        if (!openOnStartOnWake(config.openOnStartChoice, config.openOnStartOnWake)) return
        val app = pickedApp()
        if (app.isEmpty() || app == context.packageName) return
        startIntent(context.packageManager, app)?.let { openWhenReady(it, open) }
    }

    private suspend fun pickedApp(): String =
        if (config.openOnStartChoice) {
            withContext(
                io,
            ) { context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).getString(OPEN_ON_START_KEY, "").orEmpty() }
        } else {
            ""
        }

    /** Home stays on screen until the network is validated or open_on_start_network_wait is over. Leaving Home cancels the wait. */
    private suspend fun openWhenReady(
        intent: Intent,
        open: (Intent) -> Unit,
    ) {
        val waitMs = config.openOnStartNetworkWait * 1000L
        if (waitMs > 0) withTimeoutOrNull(waitMs) { validated.first { it } }
        open(intent)
    }
}
