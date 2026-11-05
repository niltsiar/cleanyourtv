package dev.niltsiar.tvhome.data.inputs

import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
 * boot_to_live_tv: the first time Home comes up after the TV starts, open the Live TV app instead.
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

    /** The first Home of a boot: [open] gets the intent of the Live TV app, once per boot. */
    public suspend fun openAfterBoot(open: (Intent) -> Unit) {
        if (!config.bootToLiveTv) return
        val intent =
            withContext(io) {
                val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
                val prefs = context.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE)
                if (!openOnStartDue(boot, prefs.getInt(BOOT_KEY, -1), config.liveTvPackage, context.packageName)) return@withContext null
                if (!prefs.edit().putInt(BOOT_KEY, boot).commit()) return@withContext null
                liveTv.appIntent()
            } ?: return
        openWhenReady(intent, open)
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
