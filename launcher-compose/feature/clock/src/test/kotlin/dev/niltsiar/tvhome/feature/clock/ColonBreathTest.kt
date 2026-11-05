package dev.niltsiar.tvhome.feature.clock

import androidx.activity.ComponentActivity
import androidx.compose.runtime.State
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class ColonBreathTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun breathAfter(
        motion: Motion,
        millis: Long,
    ): Float {
        lateinit var breath: State<Float>
        compose.mainClock.autoAdvance = false
        compose.setContent { TvTheme(motion = motion) { breath = rememberColonBreath() } }
        compose.mainClock.advanceTimeBy(millis)
        return breath.value
    }

    @Test
    fun the_colon_dims_and_returns_by_default() {
        assertTrue(breathAfter(Motion(), millis = 1_800) < 0.5f)
    }

    @Test
    fun the_colon_stays_at_full_opacity_with_reduced_motion() {
        assertEquals(1f, breathAfter(Motion(reducedMotion = true), millis = 1_800), 0f)
    }

    @Test
    fun the_colon_stays_at_full_opacity_with_animations_off() {
        assertEquals(1f, breathAfter(Motion(animations = false), millis = 1_800), 0f)
    }
}
