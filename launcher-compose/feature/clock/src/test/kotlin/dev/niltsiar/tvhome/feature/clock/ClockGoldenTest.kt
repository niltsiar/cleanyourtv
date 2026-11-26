package dev.niltsiar.tvhome.feature.clock

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.focus.sameInBothLooks
import dev.niltsiar.tvhome.core.designsystem.focus.taggedLeafPixels
import dev.niltsiar.tvhome.core.designsystem.golden.assertMatchesGolden
import dev.niltsiar.tvhome.core.designsystem.golden.bands
import dev.niltsiar.tvhome.core.designsystem.motion
import io.kotest.matchers.collections.shouldBeEmpty
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

                /** The look the scene draws in; a new value builds the scene again, from the same random spot. */
                var shownLook by mutableStateOf(Look.Glass)

                /** A dark picture in two bands, as Home's darkened wallpaper. */
                val picture = bands(32, 18, listOf(Color(0xFF1B2A3A), Color(0xFF3A2A1B)))

                /**
                 * The scene in [look] with clock_glow [glow], 2 s after it starts: the clock has faded in and the colon is
                 * mid-breath where it breathes.
                 */
                fun show(
                    glow: Boolean,
                    look: Look = Look.Glass,
                    config: TvConfig = TvConfig(),
                    over: ImageBitmap? = null,
                ) {
                    val face = clockFace(LocalDateTime.of(2026, 11, 4, 21, 41), hour24 = false, "EEEE  d MMMM", Locale.ENGLISH)
                    compose.mainClock.autoAdvance = false
                    shownLook = look
                    compose.setContent {
                        key(shownLook) {
                            TvTheme(
                                look = shownLook,
                                seed = { Ember },
                                motion = config.motion(),
                            ) { ClockScene(face, glow = glow, picture = over, random = Random(2)) }
                        }
                    }
                    compose.mainClock.advanceTimeBy(2_000)
                    compose.waitForIdle()
                }

                /** The tagged leaves of the scene [show] put up in Glass that draw the same when it is built in Expressive. */
                fun sameLeaves(): List<String> {
                    val glass = compose.taggedLeafPixels()
                    compose.runOnUiThread { shownLook = Look.Expressive }
                    compose.waitForIdle()
                    compose.mainClock.advanceTimeBy(2_000)
                    compose.waitForIdle()
                    return sameInBothLooks(glass, compose.taggedLeafPixels())
                }

                fun golden(scene: String) =
                    compose
                        .onRoot()
                        .captureToImage()
                        .assertMatchesGolden("ClockGoldenTest", scene)
            }
        } asContextForEach {
            for (look in Look.entries) {
                val prefix = if (look == Look.Expressive) "expressive-" else ""
                val named = if (look == Look.Expressive) ", in Expressive" else ""

                test("the screensaver with the glow$named") {
                    show(glow = true, look)
                    golden("${prefix}glow-on")
                }

                test("the screensaver without the glow$named") {
                    show(glow = false, look)
                    golden("${prefix}glow-off")
                }

                test("the screensaver over the wallpaper$named") {
                    show(glow = true, look, over = picture)
                    golden("${prefix}picture")
                }

                test("the screensaver with animations off$named") {
                    show(glow = true, look, TvConfig(animations = false))
                    golden("${prefix}still")
                }

                test("the glow a minute and ten seconds in, after one colour change$named") {
                    show(glow = true, look)
                    compose.mainClock.advanceTimeBy(GLOW_CYCLE_MS)
                    compose.waitForIdle()
                    golden("${prefix}glow-change-1")
                }
            }

            test("every tagged leaf of the screensaver with the glow draws differently in Expressive") {
                show(glow = true)
                sameLeaves().shouldBeEmpty()
            }

            test("every tagged leaf of the screensaver without the glow draws differently in Expressive") {
                show(glow = false)
                sameLeaves().shouldBeEmpty()
            }

            test("every tagged leaf of the screensaver over the wallpaper draws differently in Expressive") {
                show(glow = true, over = picture)
                sameLeaves().shouldBeEmpty()
            }

            test("every tagged leaf of the screensaver with animations off draws differently in Expressive") {
                show(glow = true, config = TvConfig(animations = false))
                sameLeaves().shouldBeEmpty()
            }

            // Each colour of the Glass glow palette, one change apart; the first is in glow-on, the second in glow-change-1.
            test("the glow takes each colour of its palette in turn") {
                show(glow = true)
                compose.mainClock.advanceTimeBy(GLOW_CYCLE_MS)
                for (change in 2..4) {
                    compose.mainClock.advanceTimeBy(GLOW_CYCLE_MS)
                    compose.waitForIdle()
                    golden("glow-change-$change")
                }
            }
        }
    })

/** From one glow colour fully in to the next: the wait between moves, then the change's steps. */
private const val GLOW_CYCLE_MS = MOVE_MS + GLOW_STEPS * GLOW_STEP_MS

/** The house seed of the Expressive scenes, the warm one the design-system goldens use. */
private val Ember = Color(0xFFFF6A3D)
