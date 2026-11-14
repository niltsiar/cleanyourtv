package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

private val Zone = ZoneId.of("UTC")
private const val DAY = 24 * 3_600_000L

val LaunchLogTest by testSuite {
    robolectricTestSuite<LaunchLogTestContent>("default sdk")
}

class LaunchLogTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val folder = rule(TemporaryFolder())

                val now =
                    LocalDateTime
                        .of(2026, 11, 4, 21, 30)
                        .atZone(Zone)
                        .toInstant()
                        .toEpochMilli()

                fun log(
                    file: File = File(folder.root, "launch_log.txt"),
                    enabled: Boolean = true,
                ) = LaunchLog(file, MutableStateFlow(TvConfig(habitHint = enabled)), Dispatchers.Unconfined)

                fun lines(file: File) = file.readLines()
            }
        } asContextForEach {
            test("a launch is stored where a new launch log reads it") {
                val file = File(folder.root, "launch_log.txt")
                listOf(5, 2, 1, 0).forEach { log(file).record("tv.show", now - it * DAY) }

                runBlocking { log(file).habit(now, Zone) } shouldBe "tv.show"
                lines(file) shouldHaveSize 4
            }

            test("launches older than eight weeks are dropped when the next one is written") {
                val file = File(folder.root, "launch_log.txt")
                val log = log(file)
                log.record("old.app", now - DAY - HABIT_KEEP_MS - 1)
                log.record("tv.show", now - DAY)
                lines(file) shouldBe listOf("${now - DAY} tv.show")

                log.record("tv.show", now)
                lines(file) shouldBe listOf("${now - DAY} tv.show", "$now tv.show")
            }

            test("the oldest launches go first so the file stays under 40 kb") {
                val file = File(folder.root, "launch_log.txt")
                val log = log(file)
                repeat(LAUNCH_LOG_MAX + 200) { log.record("com.google.android.youtube.tv", now - (LAUNCH_LOG_MAX + 200 - it) * 60_000L) }

                lines(file) shouldHaveSize LAUNCH_LOG_MAX
                withClue("size ${file.length()}") { file.length() shouldBeLessThan 40_000L }
                lines(file).first() shouldBe "${now - 60_000L * LAUNCH_LOG_MAX} com.google.android.youtube.tv"
            }

            test("a damaged line is skipped and the rest reads") {
                val file = File(folder.root, "launch_log.txt")
                file.writeText("garbage\n${now - 5 * DAY} tv.show\n\n${now - 2 * DAY} tv.show\n12 \n${now - DAY} tv.show\n$now tv.show\n")

                runBlocking { log(file).habit(now, Zone) } shouldBe "tv.show"
            }

            test("with the key off nothing is written and nothing is read") {
                val file = File(folder.root, "launch_log.txt")
                file.writeText(listOf(5, 2, 1, 0).joinToString("") { "${now - it * DAY} tv.show\n" })
                val off = log(file, enabled = false)

                off.record("tv.show", now)

                runBlocking { off.habit(now, Zone) }.shouldBeNull()
                lines(file) shouldHaveSize 4
            }

            test("the same log starts and stops recording with the key and keeps nothing from the time it was off") {
                val file = File(folder.root, "launch_log.txt")
                val config = MutableStateFlow(TvConfig(habitHint = false))
                val log = LaunchLog(file, config, Dispatchers.Unconfined)

                log.record("tv.show", now - 2 * DAY)
                file.exists().shouldBeFalse()
                runBlocking { log.habit(now, Zone) }.shouldBeNull()

                config.value = TvConfig(habitHint = true)
                listOf(5, 2, 1, 0).forEach { log.record("tv.show", now - it * DAY) }
                lines(file) shouldHaveSize 4
                runBlocking { log.habit(now, Zone) } shouldBe "tv.show"

                config.value = TvConfig(habitHint = false)
                log.record("tv.show", now)
                lines(file) shouldHaveSize 4
                runBlocking { log.habit(now, Zone) }.shouldBeNull()
            }

            test("with the key off a launch makes no file") {
                val file = File(folder.root, "launch_log.txt")

                log(file, enabled = false).record("tv.show", now)

                file.exists().shouldBeFalse()
            }
        }
    })
