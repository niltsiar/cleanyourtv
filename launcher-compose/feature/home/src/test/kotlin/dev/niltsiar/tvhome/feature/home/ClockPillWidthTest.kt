package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.util.Locale

/** Real glyph widths need the native graphics mode; the default one gives every character the same width. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class ClockPillWidthTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(HomeUiState())

    private fun show(
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

    private fun start(hour24: Boolean) {
        state = homeUiState(homeApps(), LocalDateTime.of(2026, 11, 5, 12, 0), null, Locale.ENGLISH, HeaderStyle(hour24 = hour24))
        compose.setContent {
            TvTheme { ClockPill(rememberGlassState(), { state }, dateBelowClock = false) }
        }
    }

    @Test
    fun the_24_hour_pill_keeps_its_width_between_wide_and_narrow_digits() {
        start(hour24 = true)

        assertEquals(show(11, 11, hour24 = true), show(0, 0, hour24 = true))
        assertEquals(show(11, 11, hour24 = true), show(20, 48, hour24 = true))
    }

    @Test
    fun the_12_hour_pill_keeps_its_width_between_one_and_two_digit_hours() {
        start(hour24 = false)

        assertEquals(show(13, 11, hour24 = false), show(22, 0, hour24 = false))
        assertEquals(show(13, 11, hour24 = false), show(15, 59, hour24 = false))
    }
}
