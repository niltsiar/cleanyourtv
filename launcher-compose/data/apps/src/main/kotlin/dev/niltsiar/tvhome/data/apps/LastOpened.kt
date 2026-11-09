package dev.niltsiar.tvhome.data.apps

import android.content.Context
import android.content.SharedPreferences
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.OpenedRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PREFS = "last_opened"

/** The preference that holds when the record began. No tile key starts with "@". */
private const val SINCE = "@since"

/** library_least_used or unused_fade_days: Home keeps the record of when it opened each app, and of when the record began. */
internal val TvConfig.recordsUsage: Boolean get() = libraryLeastUsed || unusedFadeDays > 0

/**
 * When Home last opened each app, for the cinematic shelf's "Last opened" line: Home notes the time whenever it opens
 * an app. It reads nothing from other apps, so an app started some other way shows the last time Home opened it.
 * Files are read and written on [io]; [times] is current at once. The record begins when [recordsUsage] first holds, also
 * after the start, and not before: nothing is filled in for the time it was off.
 */
public class LastOpened(
    context: Context,
    private val config: StateFlow<TvConfig>,
    io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val prefs: SharedPreferences by lazy { appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val saved = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val began = MutableStateFlow<Long?>(null)
    private val loaded = CompletableDeferred<Unit>()

    /** Tile key to the milliseconds since the epoch of the last launch; a tile Home never opened is missing. */
    public val times: StateFlow<Map<String, Long>> = saved.asStateFlow()

    /** Milliseconds since the epoch when the record began, null while it has not. */
    public val since: StateFlow<Long?> = began.asStateFlow()

    init {
        scope.launch {
            val stored = prefs.all.mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }.toMap()
            val times = stored - SINCE
            val start = stored[SINCE] ?: times.values.minOrNull()
            if (start != null && SINCE !in stored) prefs.edit().putLong(SINCE, start).apply()
            saved.update { times + it }
            began.update { listOfNotNull(it, start).minOrNull() }
            if (config.value.recordsUsage) beginRecord()
            loaded.complete(Unit)
            config.map { it.recordsUsage }.distinctUntilChanged().collect { if (it) beginRecord() }
        }
    }

    private fun beginRecord() {
        val now = clock()
        if (began.getAndUpdate { it ?: now } == null) prefs.edit().putLong(SINCE, now).apply()
    }

    /** The times and the start of the record, once they are read from the disk. */
    public suspend fun snapshot(): OpenedRecord {
        loaded.await()
        return OpenedRecord(saved.value, began.value)
    }

    /** Notes that Home just opened the tile [key]. Main thread. */
    public fun record(
        key: String,
        now: Long = System.currentTimeMillis(),
    ) {
        saved.update { it + (key to now) }
        began.update { it ?: now }
        scope.launch {
            val edit = prefs.edit().putLong(key, now)
            if (!prefs.contains(SINCE)) edit.putLong(SINCE, began.value ?: now)
            edit.apply()
        }
    }
}
