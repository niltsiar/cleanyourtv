package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

/** The hero scrim's alpha follows whether a line other than the tile's is on the band, in the time the line takes to swap. */
val HeroScrimFadeTest by testSuite {
    robolectricTestSuite<HeroScrimFadeTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

class HeroScrimFadeTestContent :
    RobolectricTestSuiteContent({
        testFixture { HeroScrimFadeTestFixture() } asContextForEach {
            test("the scrim starts out with no line on the band") {
                show()

                fade.value shouldBe (0f plusOrMinus 0f)
            }

            test("a top bar line fades the scrim in and out between off and on") {
                show()

                val rising = moveTo(HeroSource.Bar(BarControl.Network), millis = 64)
                withClue("fade $rising") { (rising > 0.05f && rising < 0.95f).shouldBeTrue() }
                fade.value shouldBe (1f plusOrMinus 0f)

                val falling = moveTo(HeroSource.Quiet, millis = 64)
                withClue("fade $falling") { (falling > 0.05f && falling < 0.95f).shouldBeTrue() }
                fade.value shouldBe (0f plusOrMinus 0f)
            }

            test("a chrome line fades in the quick swap time and a reorder line in the longer one") {
                show()
                moveTo(HeroSource.Library, millis = 170) shouldBe (1f plusOrMinus 0f)

                moveTo(HeroSource.Quiet, millis = 1)
                val reorder = moveTo(HeroSource.Mode(ReorderHint.Reorder), millis = 170)
                withClue("fade $reorder") { reorder shouldBeLessThan 0.95f }
            }

            test("a move between lines that all want the scrim leaves it where it is") {
                show()
                moveTo(HeroSource.Library, millis = 400)

                moveTo(HeroSource.Bar(BarControl.Network), millis = 16) shouldBe (1f plusOrMinus 0f)
            }

            test("the scrim switches at once with animations off") {
                show(Motion(animations = false))

                moveTo(HeroSource.Library, millis = 16) shouldBe (1f plusOrMinus 0f)
                moveTo(HeroSource.Quiet, millis = 16) shouldBe (0f plusOrMinus 0f)
            }

            test("the scrim switches at once with reduced motion") {
                show(Motion(reducedMotion = true))

                moveTo(HeroSource.Library, millis = 16) shouldBe (1f plusOrMinus 0f)
                moveTo(HeroSource.Quiet, millis = 16) shouldBe (0f plusOrMinus 0f)
            }
        }
    })

private class HeroScrimFadeTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val source = mutableStateOf<HeroSource>(HeroSource.Quiet)

    lateinit var fade: State<Float>

    fun show(motion: Motion = Motion()) {
        compose.setContent {
            TvTheme(motion = motion) {
                fade = rememberHeroScrimFade(source = { source.value }, wanted = { source.value != HeroSource.Quiet })
            }
        }
        compose.waitForIdle()
    }

    /** Puts [next] on the band and returns the fade [millis] later, then lets everything settle. */
    fun moveTo(
        next: HeroSource,
        millis: Long,
    ): Float {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            source.value = next
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(millis)
        val during = fade.value
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        return during
    }
}
