package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.OkHold
import dev.niltsiar.tvhome.core.designsystem.holdOk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HoldOkTimerTest {
    @get:Rule
    val compose = createComposeRule()

    private val requester = FocusRequester()
    private var held = 0

    private fun holdOk(holdMs: Long) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .testTag("tile")
                    .focusRequester(requester)
                    .holdOk(OkHold(holdMs), onHold = { held++ })
                    .focusable(),
            )
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { requester.requestFocus() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tile").performKeyInput { keyDown(Key.DirectionCenter) }
    }

    @Test
    fun the_timer_of_a_remote_that_never_repeats_opens_the_menu_when_the_holds_own_time_is_up() {
        holdOk(holdMs = 200)

        compose.mainClock.advanceTimeBy(150)
        assertEquals(0, held)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(1, held)
    }
}
