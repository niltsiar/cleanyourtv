package dev.niltsiar.tvhome.feature.clock

import androidx.activity.ComponentActivity
import androidx.compose.runtime.State
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe

val ColonBreathTest by testSuite {
    robolectricTestSuite<ColonBreathTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class ColonBreathTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())

                fun breathAfter(
                    motion: Motion,
                    millis: Long,
                ): Float {
                    lateinit var breath: State<Float>
                    compose.mainClock.autoAdvance = false
                    compose.setContent { TvTheme(motion = motion) { breath = rememberColonBreath() } }
                    compose.mainClock.advanceTimeBy(millis)
                    return breath.value
                }
            }
        } asContextForEach {
            test("the colon dims and returns by default") {
                breathAfter(Motion(), millis = 1_800) shouldBeLessThan 0.5f
            }

            test("the colon stays at full opacity with reduced motion") {
                breathAfter(Motion(reducedMotion = true), millis = 1_800) shouldBe (1f plusOrMinus 0f)
            }

            test("the colon stays at full opacity with animations off") {
                breathAfter(Motion(animations = false), millis = 1_800) shouldBe (1f plusOrMinus 0f)
            }
        }
    })
