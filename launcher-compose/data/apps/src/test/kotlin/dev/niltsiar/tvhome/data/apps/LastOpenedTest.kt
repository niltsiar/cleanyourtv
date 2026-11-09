package dev.niltsiar.tvhome.data.apps

import android.content.Context
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.OpenedRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
class LastOpenedTest {
    private val context = RuntimeEnvironment.getApplication()
    private val usageOff = MutableStateFlow(TvConfig())
    private val usageOn = MutableStateFlow(TvConfig(libraryLeastUsed = true))

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            queue.addLast(block)
        }

        fun runAll() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    @Test
    fun a_launch_shows_in_the_times_at_once_whatever_the_disk_is_doing() {
        val opened = LastOpened(context, usageOff, QueuedDispatcher())

        opened.record("com.shop/.Main", now = 1_000L)

        assertEquals(mapOf("com.shop/.Main" to 1_000L), opened.times.value)
    }

    @Test
    fun a_launch_is_stored_where_a_new_instance_reads_it() {
        LastOpened(context, usageOff, Dispatchers.Unconfined).record("com.shop/.Main", now = 1_000L)

        assertEquals(mapOf("com.shop/.Main" to 1_000L), LastOpened(context, usageOff, Dispatchers.Unconfined).times.value)
    }

    @Test
    fun a_newer_launch_replaces_the_older_one_and_other_tiles_keep_theirs() {
        val opened = LastOpened(context, usageOff, Dispatchers.Unconfined)
        opened.record("a/.A", now = 1L)
        opened.record("b/.B", now = 2L)
        opened.record("a/.A", now = 3L)

        assertEquals(mapOf("a/.A" to 3L, "b/.B" to 2L), LastOpened(context, usageOff, Dispatchers.Unconfined).times.value)
    }

    @Test
    fun times_from_an_earlier_run_load_without_overriding_a_launch_made_meanwhile() {
        val prefs = context.getSharedPreferences("last_opened", Context.MODE_PRIVATE)
        prefs
            .edit()
            .putLong("a/.A", 1L)
            .putLong("b/.B", 2L)
            .commit()
        val io = QueuedDispatcher()
        val opened = LastOpened(context, usageOff, io)
        opened.record("a/.A", now = 9L)

        io.runAll()

        assertEquals(mapOf("a/.A" to 9L, "b/.B" to 2L), opened.times.value)
        assertEquals(9L, prefs.getLong("a/.A", 0L))
    }

    @Test
    fun the_record_begins_at_the_first_launch_it_notes_and_a_later_launch_does_not_move_it() {
        val opened = LastOpened(context, usageOff, Dispatchers.Unconfined)
        assertNull(opened.since.value)

        opened.record("a/.A", now = 1_000L)
        opened.record("b/.B", now = 2_000L)

        assertEquals(1_000L, opened.since.value)
        assertEquals(1_000L, LastOpened(context, usageOff, Dispatchers.Unconfined).since.value)
    }

    @Test
    fun with_a_usage_key_on_the_record_begins_when_it_is_first_read_and_a_later_start_keeps_that_moment() {
        assertEquals(5_000L, LastOpened(context, usageOn, Dispatchers.Unconfined, clock = { 5_000L }).since.value)

        assertEquals(5_000L, LastOpened(context, usageOn, Dispatchers.Unconfined, clock = { 9_000L }).since.value)
    }

    @Test
    fun a_usage_key_turned_on_later_begins_the_record_then_and_not_before() {
        val config = MutableStateFlow(TvConfig())
        var now = 5_000L
        val opened = LastOpened(context, config, Dispatchers.Unconfined, clock = { now })
        assertNull(opened.since.value)

        now = 9_000L
        config.value = TvConfig(unusedFadeDays = 30)
        assertEquals(9_000L, opened.since.value)

        now = 12_000L
        config.value = TvConfig()
        config.value = TvConfig(libraryLeastUsed = true)
        assertEquals(9_000L, opened.since.value)
        assertEquals(9_000L, LastOpened(context, config, Dispatchers.Unconfined, clock = { 20_000L }).since.value)
    }

    @Test
    fun without_a_usage_key_an_empty_store_has_no_record_to_date_from() {
        assertNull(LastOpened(context, usageOff, Dispatchers.Unconfined, clock = { 5_000L }).since.value)
    }

    @Test
    fun times_stored_before_the_record_had_a_start_date_start_it_at_the_oldest_of_them() {
        context
            .getSharedPreferences("last_opened", Context.MODE_PRIVATE)
            .edit()
            .putLong("a/.A", 7L)
            .putLong("b/.B", 3L)
            .commit()

        val opened = LastOpened(context, usageOff, Dispatchers.Unconfined)

        assertEquals(3L, opened.since.value)
        assertEquals(mapOf("a/.A" to 7L, "b/.B" to 3L), opened.times.value)
    }

    @Test
    fun the_start_of_the_record_is_not_a_tile_in_the_times() {
        LastOpened(context, usageOff, Dispatchers.Unconfined).record("a/.A", now = 1L)

        assertFalse(
            LastOpened(context, usageOff, Dispatchers.Unconfined)
                .times.value.keys
                .any { it.startsWith("@") },
        )
    }

    @Test
    fun a_snapshot_waits_for_the_disk_and_then_holds_the_times_and_the_start() =
        runBlocking {
            val prefs = context.getSharedPreferences("last_opened", Context.MODE_PRIVATE)
            prefs.edit().putLong("a/.A", 4L).commit()
            val io = QueuedDispatcher()
            val opened = LastOpened(context, usageOff, io)

            val snapshot = async(start = CoroutineStart.UNDISPATCHED) { opened.snapshot() }
            assertFalse(snapshot.isCompleted)
            io.runAll()

            assertEquals(OpenedRecord(mapOf("a/.A" to 4L), since = 4L), snapshot.await())
        }
}
