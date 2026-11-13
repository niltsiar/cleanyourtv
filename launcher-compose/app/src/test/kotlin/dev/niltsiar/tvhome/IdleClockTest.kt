package dev.niltsiar.tvhome

import android.os.Looper
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.shouldBe
import org.robolectric.Shadows.shadowOf
import java.time.Duration

val IdleClockTest by testSuite {
    robolectricTestSuite<IdleClockTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

class IdleClockTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                var resumed = true
                var shown = 0
                val minute = 60_000L

                var idleMs = 0L

                fun idleClock(idleMs: Long): IdleClock {
                    this.idleMs = idleMs
                    return IdleClock({ this.idleMs }, { resumed }, { shown++ })
                }

                fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
            }
        } asContextForEach {
            test("the clock starts after the idle time without input") {
                val idle = idleClock(2 * minute)
                idle.restart()

                pass(2 * minute - 1)
                shown shouldBe 0
                pass(1)
                shown shouldBe 1
            }

            test("input restarts the count") {
                val idle = idleClock(2 * minute)
                idle.restart()
                pass(minute + 30_000)
                idle.restart()
                pass(minute + 30_000)
                shown shouldBe 0

                pass(30_000)
                shown shouldBe 1
            }

            test("a key that reaches a paused home does not start the clock") {
                val idle = idleClock(minute)
                idle.restart()
                resumed = false
                idle.cancel() // onPause
                idle.restart() // the UP of the key that opened another app
                pass(10 * minute)

                shown shouldBe 0
            }

            test("zero minutes is off") {
                idleClock(0).restart()
                pass(60 * minute)

                shown shouldBe 0
            }

            test("a changed idle time applies from the next restart") {
                val idle = idleClock(5 * minute)
                idle.restart()
                idleMs = minute
                idle.restart()

                pass(minute)

                shown shouldBe 1
            }
        }
    })
