package dev.niltsiar.tvhome.data.apps

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PREFS = "last_opened"

/**
 * When Home last opened each app, for the cinematic shelf's "Last opened" line: Home notes the time whenever it opens
 * an app. It reads nothing from other apps, so an app started some other way shows the last time Home opened it.
 * Files are read and written on [io]; [times] is current at once.
 */
public class LastOpened(
    context: Context,
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val prefs: SharedPreferences by lazy { appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val saved = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Tile key to the milliseconds since the epoch of the last launch; a tile Home never opened is missing. */
    public val times: StateFlow<Map<String, Long>> = saved.asStateFlow()

    init {
        scope.launch {
            val stored = prefs.all.mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }.toMap()
            saved.update { stored + it }
        }
    }

    /** Notes that Home just opened the tile [key]. Main thread. */
    public fun record(
        key: String,
        now: Long = System.currentTimeMillis(),
    ) {
        saved.update { it + (key to now) }
        scope.launch { prefs.edit().putLong(key, now).apply() }
    }
}
