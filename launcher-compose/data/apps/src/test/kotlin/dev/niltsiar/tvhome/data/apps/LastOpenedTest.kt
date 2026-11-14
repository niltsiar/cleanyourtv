package dev.niltsiar.tvhome.data.apps

import android.content.Context
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.OpenedRecord
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import kotlin.coroutines.CoroutineContext

val LastOpenedTest by testSuite {
    robolectricTestSuite<LastOpenedTestContent>("default sdk")
}

class LastOpenedTestContent :
    RobolectricTestSuiteContent({
        class QueuedDispatcher : CoroutineDispatcher() {
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

        testFixture {
            object {
                val context = RuntimeEnvironment.getApplication()
                val usageOff = MutableStateFlow(TvConfig())
                val usageOn = MutableStateFlow(TvConfig(libraryLeastUsed = true))
            }
        } asContextForEach {
            test("a launch shows in the times at once whatever the disk is doing") {
                val opened = LastOpened(context, usageOff, QueuedDispatcher())

                opened.record("com.shop/.Main", now = 1_000L)

                opened.times.value shouldBe mapOf("com.shop/.Main" to 1_000L)
            }

            test("a launch is stored where a new instance reads it") {
                LastOpened(context, usageOff, Dispatchers.Unconfined).record("com.shop/.Main", now = 1_000L)

                LastOpened(context, usageOff, Dispatchers.Unconfined).times.value shouldBe mapOf("com.shop/.Main" to 1_000L)
            }

            test("a newer launch replaces the older one and other tiles keep theirs") {
                val opened = LastOpened(context, usageOff, Dispatchers.Unconfined)
                opened.record("a/.A", now = 1L)
                opened.record("b/.B", now = 2L)
                opened.record("a/.A", now = 3L)

                LastOpened(context, usageOff, Dispatchers.Unconfined).times.value shouldBe mapOf("a/.A" to 3L, "b/.B" to 2L)
            }

            test("times from an earlier run load without overriding a launch made meanwhile") {
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

                opened.times.value shouldBe mapOf("a/.A" to 9L, "b/.B" to 2L)
                prefs.getLong("a/.A", 0L) shouldBe 9L
            }

            test("the record begins at the first launch it notes and a later launch does not move it") {
                val opened = LastOpened(context, usageOff, Dispatchers.Unconfined)
                opened.since.value.shouldBeNull()

                opened.record("a/.A", now = 1_000L)
                opened.record("b/.B", now = 2_000L)

                opened.since.value shouldBe 1_000L
                LastOpened(context, usageOff, Dispatchers.Unconfined).since.value shouldBe 1_000L
            }

            test("with a usage key on the record begins when it is first read and a later start keeps that moment") {
                LastOpened(context, usageOn, Dispatchers.Unconfined, clock = { 5_000L }).since.value shouldBe 5_000L

                LastOpened(context, usageOn, Dispatchers.Unconfined, clock = { 9_000L }).since.value shouldBe 5_000L
            }

            test("a usage key turned on later begins the record then and not before") {
                val config = MutableStateFlow(TvConfig())
                var now = 5_000L
                val opened = LastOpened(context, config, Dispatchers.Unconfined, clock = { now })
                opened.since.value.shouldBeNull()

                now = 9_000L
                config.value = TvConfig(unusedFadeDays = 30)
                opened.since.value shouldBe 9_000L

                now = 12_000L
                config.value = TvConfig()
                config.value = TvConfig(libraryLeastUsed = true)
                opened.since.value shouldBe 9_000L
                LastOpened(context, config, Dispatchers.Unconfined, clock = { 20_000L }).since.value shouldBe 9_000L
            }

            test("without a usage key an empty store has no record to date from") {
                LastOpened(context, usageOff, Dispatchers.Unconfined, clock = { 5_000L }).since.value.shouldBeNull()
            }

            test("times stored before the record had a start date start it at the oldest of them") {
                context
                    .getSharedPreferences("last_opened", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("a/.A", 7L)
                    .putLong("b/.B", 3L)
                    .commit()

                val opened = LastOpened(context, usageOff, Dispatchers.Unconfined)

                opened.since.value shouldBe 3L
                opened.times.value shouldBe mapOf("a/.A" to 7L, "b/.B" to 3L)
            }

            test("the start of the record is not a tile in the times") {
                LastOpened(context, usageOff, Dispatchers.Unconfined).record("a/.A", now = 1L)

                LastOpened(context, usageOff, Dispatchers.Unconfined)
                    .times.value.keys
                    .any { it.startsWith("@") }
                    .shouldBeFalse()
            }

            test("a snapshot waits for the disk and then holds the times and the start") {
                runBlocking {
                    val prefs = context.getSharedPreferences("last_opened", Context.MODE_PRIVATE)
                    prefs.edit().putLong("a/.A", 4L).commit()
                    val io = QueuedDispatcher()
                    val opened = LastOpened(context, usageOff, io)

                    val snapshot = async(start = CoroutineStart.UNDISPATCHED) { opened.snapshot() }
                    snapshot.isCompleted.shouldBeFalse()
                    io.runAll()

                    snapshot.await() shouldBe OpenedRecord(mapOf("a/.A" to 4L), since = 4L)
                }
            }
        }
    })
