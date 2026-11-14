package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

val WifiLightTest by testSuite {
    val parts = 0..2

    test("all three parts are lit at both ends of the run") {
        for (part in parts) {
            wifiLight(0f, part) shouldBe (1f plusOrMinus 0f)
            wifiLight(1f, part) shouldBe (1f plusOrMinus 0f)
        }
    }

    test("all three parts fade down together before any lights again") {
        val early = parts.map { wifiLight(0.15f, it) }
        (early.all { it < 1f }).shouldBeTrue()
        early.distinct().size shouldBe 1
        for (part in parts) wifiLight(0.23f, part) shouldBe (0.2f plusOrMinus 0f)
    }

    test("the dot then the inner arc then the outer arc light in turn") {
        val lit = { part: Int -> (0..100).first { wifiLight(it / 100f, part) > 0.9f && it > 25 } }

        lit(0) shouldBeLessThan lit(1)
        lit(1) shouldBeLessThan lit(2)
    }

    test("each part only brightens once it has started to light") {
        for (part in parts) {
            val values = (24..100).map { wifiLight(it / 100f, part) }
            values shouldBe values.sorted()
        }
    }

    test("the inputs arrow starts 7 units before its place and invisible") {
        inputsArrowOffset(0f) shouldBe (-7f plusOrMinus 1e-4f)
        inputsArrowAlpha(0f) shouldBe (0f plusOrMinus 0f)
    }

    test("the inputs arrow passes its place by one unit and rests there at the end") {
        val offsets = (0..100).map { inputsArrowOffset(it / 100f) }

        offsets.max() shouldBe (1f plusOrMinus 0.05f)
        offsets.indexOf(offsets.max()) shouldBeIn 50..90
        inputsArrowOffset(1f) shouldBe (0f plusOrMinus 1e-4f)
    }

    test("the inputs arrow fades in over the first 30 percent and stays opaque") {
        inputsArrowAlpha(0.15f) shouldBe (0.5f plusOrMinus 1e-4f)
        inputsArrowAlpha(0.3f) shouldBe (1f plusOrMinus 0f)
        inputsArrowAlpha(1f) shouldBe (1f plusOrMinus 0f)
    }
}

val FocusTimelineTest by testSuite {
    robolectricTestSuite<FocusTimelineTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class FocusTimelineTestContent :
    RobolectricTestSuiteContent({
        testFixture { FocusTimelineTestFixture() } asContextForEach {
            test("it rests at one and runs from zero to one in 750 ms when focus arrives") {
                show()
                value() shouldBe (1f plusOrMinus 0f)

                emit(focus)
                compose.mainClock.advanceTimeBy(160)
                value() shouldBeIn 0.1f..0.6f

                compose.mainClock.advanceTimeBy(500)
                value() shouldBeLessThan 1f

                compose.mainClock.advanceTimeBy(750 - 660L + 20)
                value() shouldBe (1f plusOrMinus 0f)
            }

            test("losing focus mid run settles to one without going back") {
                show()
                emit(focus)
                compose.mainClock.advanceTimeBy(150)
                val before = value()

                emit(FocusInteraction.Unfocus(focus))
                val seen = mutableListOf<Float>()
                repeat(20) {
                    compose.mainClock.advanceTimeBy(20)
                    seen += value()
                }

                before shouldBeLessThan 1f
                seen shouldBe seen.sorted()
                seen.last() shouldBe (1f plusOrMinus 0f)
            }

            test("losing focus after the run replays nothing") {
                show()
                emit(focus)
                compose.mainClock.advanceTimeBy(750 + 50L)

                emit(FocusInteraction.Unfocus(focus))
                compose.mainClock.advanceTimeBy(300)

                value() shouldBe (1f plusOrMinus 0f)
            }

            test("it stays at one with animations off") {
                show(animated = false)

                emit(focus)
                compose.mainClock.advanceTimeBy(300)

                value() shouldBe (1f plusOrMinus 0f)
            }
        }
    })

private class FocusTimelineTestFixture : JUnit4RulesContext() {
    val compose = rule(createComposeRule())

    val source = MutableInteractionSource()

    val focus = FocusInteraction.Focus()

    lateinit var value: () -> Float

    fun show(animated: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val timeline = rememberFocusTimeline(source, animated = animated)
            SideEffect { value = { timeline.value } }
        }
        compose.mainClock.advanceTimeBy(50)
    }

    fun emit(interaction: Interaction) {
        runBlocking { source.emit(interaction) }
        compose.mainClock.advanceTimeBy(20)
    }
}
