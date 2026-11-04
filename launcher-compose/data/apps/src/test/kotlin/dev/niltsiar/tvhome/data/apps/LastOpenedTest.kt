package dev.niltsiar.tvhome.data.apps

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
class LastOpenedTest {
    private val context = RuntimeEnvironment.getApplication()

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
        val opened = LastOpened(context, QueuedDispatcher())

        opened.record("com.shop/.Main", now = 1_000L)

        assertEquals(mapOf("com.shop/.Main" to 1_000L), opened.times.value)
    }

    @Test
    fun a_launch_is_stored_where_a_new_instance_reads_it() {
        LastOpened(context, Dispatchers.Unconfined).record("com.shop/.Main", now = 1_000L)

        assertEquals(mapOf("com.shop/.Main" to 1_000L), LastOpened(context, Dispatchers.Unconfined).times.value)
    }

    @Test
    fun a_newer_launch_replaces_the_older_one_and_other_tiles_keep_theirs() {
        val opened = LastOpened(context, Dispatchers.Unconfined)
        opened.record("a/.A", now = 1L)
        opened.record("b/.B", now = 2L)
        opened.record("a/.A", now = 3L)

        assertEquals(mapOf("a/.A" to 3L, "b/.B" to 2L), LastOpened(context, Dispatchers.Unconfined).times.value)
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
        val opened = LastOpened(context, io)
        opened.record("a/.A", now = 9L)

        io.runAll()

        assertEquals(mapOf("a/.A" to 9L, "b/.B" to 2L), opened.times.value)
        assertEquals(9L, prefs.getLong("a/.A", 0L))
    }
}
