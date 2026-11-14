package dev.niltsiar.tvhome.service.keys

import android.view.KeyEvent
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

val VolumeKeysTest by testSuite {
    robolectricTestSuite<VolumeKeysTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

class VolumeKeysTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val raise = 1
                val lower = -1

                fun key(
                    action: Int,
                    code: Int,
                    repeat: Int = 0,
                ) = KeyEvent(0, 0, action, code, repeat)
            }
        } asContextForEach {
            test("volume up and down are handled") {
                volumeDirection(KeyEvent.KEYCODE_VOLUME_UP) shouldBe raise
                volumeDirection(KeyEvent.KEYCODE_VOLUME_DOWN) shouldBe lower
            }

            test("other keys pass through") {
                // Mute is one press with nothing to repeat, and the firmware did not double it.
                volumeDirection(KeyEvent.KEYCODE_VOLUME_MUTE) shouldBe 0
                volumeDirection(KeyEvent.KEYCODE_DPAD_UP) shouldBe 0
            }

            test("only the first down and the up change the press") {
                changesPress(KeyEvent.ACTION_DOWN, 0).shouldBeTrue()
                changesPress(KeyEvent.ACTION_UP, 0).shouldBeTrue()
                // A remote that sends its own repeats must not restart the 500 ms delay on every one.
                changesPress(KeyEvent.ACTION_DOWN, 1).shouldBeFalse()
                changesPress(KeyEvent.ACTION_DOWN, 12).shouldBeFalse()
            }

            test("a held volume key repeats after 500 ms every 60 ms until it is released") {
                val timer = FakeTimer()
                val steps = mutableListOf<Int>()
                val volume = VolumeRepeat(timer) { steps += it }

                volume.handle(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)).shouldBeTrue()
                timer.advance(499)
                steps shouldBe emptyList<Int>()
                timer.advance(1)
                steps shouldBe listOf(raise)
                timer.advance(120)
                steps shouldBe listOf(raise, raise, raise)
                volume.handle(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)).shouldBeTrue()
                timer.advance(1000)
                steps.size shouldBe 3
                timer.pending shouldBe 0
            }

            test("a remotes own repeats do not restart the delay and other keys are left alone") {
                val timer = FakeTimer()
                val steps = mutableListOf<Int>()
                val volume = VolumeRepeat(timer) { steps += it }

                volume.handle(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN))
                timer.advance(400)
                volume.handle(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeat = 1)).shouldBeTrue()
                timer.advance(100)
                steps shouldBe listOf(lower)
                volume.handle(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)).shouldBeFalse()
                volume.stop()
                timer.pending shouldBe 0
            }
        }
    })
