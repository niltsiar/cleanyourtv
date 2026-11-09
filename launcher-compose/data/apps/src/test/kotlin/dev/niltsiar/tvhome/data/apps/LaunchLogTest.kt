package dev.niltsiar.tvhome.data.apps

import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

private val Zone = ZoneId.of("UTC")
private const val DAY = 24 * 3_600_000L

@RunWith(RobolectricTestRunner::class)
class LaunchLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now =
        LocalDateTime
            .of(2026, 11, 4, 21, 30)
            .atZone(Zone)
            .toInstant()
            .toEpochMilli()

    private fun log(
        file: File = File(folder.root, "launch_log.txt"),
        enabled: Boolean = true,
    ) = LaunchLog(file, MutableStateFlow(TvConfig(habitHint = enabled)), Dispatchers.Unconfined)

    private fun lines(file: File) = file.readLines()

    @Test
    fun a_launch_is_stored_where_a_new_instance_reads_it() {
        val file = File(folder.root, "launch_log.txt")
        listOf(5, 2, 1, 0).forEach { log(file).record("tv.show", now - it * DAY) }

        assertEquals("tv.show", runBlocking { log(file).habit(now, Zone) })
        assertEquals(4, lines(file).size)
    }

    @Test
    fun launches_older_than_eight_weeks_are_dropped_when_the_next_one_is_written() {
        val file = File(folder.root, "launch_log.txt")
        val log = log(file)
        log.record("old.app", now - DAY - HABIT_KEEP_MS - 1)
        log.record("tv.show", now - DAY)
        assertEquals(listOf("${now - DAY} tv.show"), lines(file))

        log.record("tv.show", now)
        assertEquals(listOf("${now - DAY} tv.show", "$now tv.show"), lines(file))
    }

    @Test
    fun the_oldest_launches_go_first_so_the_file_stays_under_40_kb() {
        val file = File(folder.root, "launch_log.txt")
        val log = log(file)
        repeat(LAUNCH_LOG_MAX + 200) { log.record("com.google.android.youtube.tv", now - (LAUNCH_LOG_MAX + 200 - it) * 60_000L) }

        assertEquals(LAUNCH_LOG_MAX, lines(file).size)
        assertTrue("size ${file.length()}", file.length() < 40_000)
        assertEquals("${now - 60_000L * LAUNCH_LOG_MAX} com.google.android.youtube.tv", lines(file).first())
    }

    @Test
    fun a_damaged_line_is_skipped_and_the_rest_reads() {
        val file = File(folder.root, "launch_log.txt")
        file.writeText("garbage\n${now - 5 * DAY} tv.show\n\n${now - 2 * DAY} tv.show\n12 \n${now - DAY} tv.show\n$now tv.show\n")

        assertEquals("tv.show", runBlocking { log(file).habit(now, Zone) })
    }

    @Test
    fun with_the_key_off_nothing_is_written_and_nothing_is_read() {
        val file = File(folder.root, "launch_log.txt")
        file.writeText(listOf(5, 2, 1, 0).joinToString("") { "${now - it * DAY} tv.show\n" })
        val off = log(file, enabled = false)

        off.record("tv.show", now)

        assertNull(runBlocking { off.habit(now, Zone) })
        assertEquals(4, lines(file).size)
    }

    @Test
    fun the_same_log_starts_and_stops_recording_with_the_key_and_keeps_nothing_from_the_time_it_was_off() {
        val file = File(folder.root, "launch_log.txt")
        val config = MutableStateFlow(TvConfig(habitHint = false))
        val log = LaunchLog(file, config, Dispatchers.Unconfined)

        log.record("tv.show", now - 2 * DAY)
        assertFalse(file.exists())
        assertNull(runBlocking { log.habit(now, Zone) })

        config.value = TvConfig(habitHint = true)
        listOf(5, 2, 1, 0).forEach { log.record("tv.show", now - it * DAY) }
        assertEquals(4, lines(file).size)
        assertEquals("tv.show", runBlocking { log.habit(now, Zone) })

        config.value = TvConfig(habitHint = false)
        log.record("tv.show", now)
        assertEquals(4, lines(file).size)
        assertNull(runBlocking { log.habit(now, Zone) })
    }

    @Test
    fun with_the_key_off_a_launch_makes_no_file() {
        val file = File(folder.root, "launch_log.txt")

        log(file, enabled = false).record("tv.show", now)

        assertFalse(file.exists())
    }
}
