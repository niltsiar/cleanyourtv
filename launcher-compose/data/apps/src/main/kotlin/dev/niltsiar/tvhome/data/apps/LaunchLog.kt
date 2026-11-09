package dev.niltsiar.tvhome.data.apps

import android.util.Log
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.ZoneId

private const val TAG = "TvHome.Habit"

/** The most records kept: under 40 KB on disk for package names up to 35 characters. */
internal const val LAUNCH_LOG_MAX = 800

/**
 * The launches Home makes, for the habit_hint mark: one line per launch, the time and the package, in a file only Home
 * reads. A launch older than eight weeks is dropped when the next one is written, and with more than [LAUNCH_LOG_MAX]
 * the oldest go. While habit_hint is off nothing is written or read; turning it on starts the record from then. The file
 * is read on [io], on first use.
 */
public class LaunchLog(
    private val file: File,
    private val config: StateFlow<TvConfig>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val lock = Any()
    private var records: List<LaunchRecord>? = null

    /** Notes that Home just opened [packageName]. Main thread. */
    public fun record(
        packageName: String,
        now: Long = System.currentTimeMillis(),
    ) {
        if (!config.value.habitHint) return
        scope.launch {
            synchronized(lock) {
                val kept = (loaded() + LaunchRecord(packageName, now)).kept(now).takeLast(LAUNCH_LOG_MAX)
                try {
                    file.writeText(kept.joinToString("") { "${it.atMillis} ${it.packageName}\n" })
                } catch (e: IOException) {
                    Log.w(TAG, "cannot write the launch log: $e")
                }
                records = kept
            }
        }
    }

    /** The package the household usually opens at [now] ([habitApp]); null when the log is off or no app is one. */
    public suspend fun habit(
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        if (!config.value.habitHint) return null
        return withContext(io) { habitApp(synchronized(lock) { loaded() }, now, zone) }
    }

    private fun loaded(): List<LaunchRecord> = records ?: readFile().also { records = it }

    private fun readFile(): List<LaunchRecord> =
        try {
            if (file.exists()) file.readLines().mapNotNull(::parse) else emptyList()
        } catch (e: IOException) {
            Log.w(TAG, "cannot read the launch log: $e")
            emptyList()
        }

    private fun parse(line: String): LaunchRecord? {
        val at = line.substringBefore(' ').toLongOrNull()
        val name = line.substringAfter(' ', "")
        return if (at != null && name.isNotEmpty()) LaunchRecord(name, at) else null
    }
}
