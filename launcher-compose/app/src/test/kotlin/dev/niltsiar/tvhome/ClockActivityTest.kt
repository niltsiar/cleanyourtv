package dev.niltsiar.tvhome

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.Look
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

val ClockActivityTest by testSuite {
    robolectricTestSuite<ClockActivityTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                application = TvHomeApplication::class
            },
    )
}

class ClockActivityTestContent :
    RobolectricTestSuiteContent({
        fun clock() =
            Robolectric
                .buildActivity(ClockActivity::class.java)
                .create()
                .start()
                .resume()

        fun key(action: Int) = KeyEvent(action, KeyEvent.KEYCODE_DPAD_CENTER)

        test("a key is consumed and its release closes the clock") {
            val controller = clock()
            val activity = controller.get()

            activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN)).shouldBeTrue()
            activity.isFinishing.shouldBeFalse()
            activity.dispatchKeyEvent(key(KeyEvent.ACTION_UP)).shouldBeTrue()
            activity.isFinishing.shouldBeTrue()
        }

        test("a remote motion closes the clock") {
            val activity = clock().get()

            activity.dispatchGenericMotionEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_MOVE, 0f, 0f, 0)).shouldBeTrue()

            activity.isFinishing.shouldBeTrue()
        }

        test("leaving the clock closes it so it is never left in the back stack") {
            val controller = clock()

            controller.pause()

            controller.get().isFinishing.shouldBeTrue()
        }

        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())
                val graph get() = (compose.activity.application as TvHomeApplication).graph

                /** The date's colour as drawn, without the fade: Glass's own white, or a role of the house scheme in Expressive. */
                fun dateColour(): Color = compose.textStyle("clock:date").color.copy(alpha = 1f)

                fun frames(until: () -> Boolean) {
                    repeat(600) {
                        if (until()) return
                        compose.mainClock.advanceTimeByFrame()
                    }
                }
            }
        } asContextForEach {
            test("the clock is drawn in the stored look") {
                shadowOf(
                    compose.activity.application,
                ).grantPermissions("${compose.activity.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
                compose.mainClock.autoAdvance = false
                compose.setContent { ClockHost(graph) }
                frames { compose.onAllNodesWithTag("clock:date").fetchSemanticsNodes().isNotEmpty() }
                val glass = dateColour()
                glass shouldBe Color.White

                runBlocking { graph.configRepository.set(Choices.Look, Look.Expressive) }
                frames { dateColour() != glass }

                dateColour() shouldNotBe glass
                runBlocking { graph.configRepository.set(Choices.Look, Look.Glass) }
                frames { dateColour() == glass }
                dateColour() shouldBe glass
            }
        }
    })
