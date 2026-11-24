package dev.niltsiar.tvhome.feature.clock

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.golden.assertMatchesGolden
import dev.niltsiar.tvhome.core.designsystem.motion
import java.time.LocalDateTime
import java.util.Locale
import kotlin.random.Random

/** Golden images of the clock screensaver at a fixed time, spot and glow colour (docs/testing.md, "Golden tests"). */
val ClockGoldenTest by testSuite {
    robolectricTestSuite<ClockGoldenTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class ClockGoldenTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())

                /** The scene with clock_glow [glow], 2 s after it starts: the clock has faded in and the colon is mid-breath. */
                fun show(glow: Boolean) {
                    val face = clockFace(LocalDateTime.of(2026, 11, 4, 21, 41), hour24 = false, "EEEE  d MMMM", Locale.ENGLISH)
                    compose.mainClock.autoAdvance = false
                    compose.setContent {
                        TvTheme(motion = TvConfig().motion()) { ClockScene(face, glow = glow, picture = null, random = Random(2)) }
                    }
                    compose.mainClock.advanceTimeBy(2_000)
                    compose.waitForIdle()
                }

                fun golden(scene: String) =
                    compose
                        .onRoot()
                        .captureToImage()
                        .assertMatchesGolden("ClockGoldenTest", scene)
            }
        } asContextForEach {
            test("the screensaver with the glow") {
                show(glow = true)
                golden("glow-on")
            }

            test("the screensaver without the glow") {
                show(glow = false)
                golden("glow-off")
            }
        }
    })
