package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.Locale

/** Real glyph widths need the native graphics mode; the default one gives every character the same width. */
val ClockPillWidthTest by testSuite {
    robolectricTestSuite<ClockPillWidthTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class ClockPillWidthTestContent :
    RobolectricTestSuiteContent({
        testFixture { ClockPillWidthTestFixture() } asContextForEach {
            test("the 24 hour pill keeps its width between wide and narrow digits") {
                start(hour24 = true)

                show(0, 0, hour24 = true) shouldBe show(11, 11, hour24 = true)
                show(20, 48, hour24 = true) shouldBe show(11, 11, hour24 = true)
            }

            test("the 12 hour pill keeps its width between one and two digit hours") {
                start(hour24 = false)

                show(22, 0, hour24 = false) shouldBe show(13, 11, hour24 = false)
                show(15, 59, hour24 = false) shouldBe show(13, 11, hour24 = false)
            }
        }
    })

private class ClockPillWidthTestFixture : JUnit4RulesContext() {
    val compose = rule(createComposeRule())

    var state by mutableStateOf(HomeUiState())

    fun show(
        hour: Int,
        minute: Int,
        hour24: Boolean,
    ): Int {
        val now = LocalDateTime.of(2026, 11, 5, hour, minute)
        state = homeUiState(homeApps(), now, null, Locale.ENGLISH, HeaderStyle(hour24 = hour24, showGreeting = false))
        compose.waitForIdle()
        return compose
            .onNodeWithTag("clock")
            .fetchSemanticsNode()
            .size.width
    }

    fun start(hour24: Boolean) {
        state = homeUiState(homeApps(), LocalDateTime.of(2026, 11, 5, 12, 0), null, Locale.ENGLISH, HeaderStyle(hour24 = hour24))
        compose.setContent {
            TvTheme { ClockPill(rememberGlassState(), { state }, dateBelowClock = false) }
        }
    }
}
