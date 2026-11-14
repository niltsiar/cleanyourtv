package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
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
import org.robolectric.annotation.GraphicsMode

/** The hero scrim's alpha follows whether a line other than the tile's is on the band, in the time the line takes to swap. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(sdk = [31])
class HeroScrimFadeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val source = mutableStateOf<HeroSource>(HeroSource.Quiet)
    private lateinit var fade: State<Float>

    private fun show(motion: Motion = Motion()) {
        compose.setContent {
            TvTheme(motion = motion) {
                fade = rememberHeroScrimFade(source = { source.value }, wanted = { source.value != HeroSource.Quiet })
            }
        }
        compose.waitForIdle()
    }

    /** Puts [next] on the band and returns the fade [millis] later, then lets everything settle. */
    private fun moveTo(
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

    @Test
    fun the_scrim_starts_out_with_no_line_on_the_band() {
        show()

        assertEquals(0f, fade.value, 0f)
    }

    @Test
    fun a_top_bar_line_fades_the_scrim_in_and_out_between_off_and_on() {
        show()

        val rising = moveTo(HeroSource.Bar(BarControl.Network), millis = 64)
        assertTrue("fade $rising", rising > 0.05f && rising < 0.95f)
        assertEquals(1f, fade.value, 0f)

        val falling = moveTo(HeroSource.Quiet, millis = 64)
        assertTrue("fade $falling", falling > 0.05f && falling < 0.95f)
        assertEquals(0f, fade.value, 0f)
    }

    @Test
    fun a_chrome_line_fades_in_the_quick_swap_time_and_a_reorder_line_in_the_longer_one() {
        show()
        assertEquals(1f, moveTo(HeroSource.Library, millis = 170), 0f)

        moveTo(HeroSource.Quiet, millis = 1)
        val reorder = moveTo(HeroSource.Mode(ReorderHint.Reorder), millis = 170)
        assertTrue("fade $reorder", reorder < 0.95f)
    }

    @Test
    fun a_move_between_lines_that_all_want_the_scrim_leaves_it_where_it_is() {
        show()
        moveTo(HeroSource.Library, millis = 400)

        assertEquals(1f, moveTo(HeroSource.Bar(BarControl.Network), millis = 16), 0f)
    }

    @Test
    fun the_scrim_switches_at_once_with_animations_off() {
        show(Motion(animations = false))

        assertEquals(1f, moveTo(HeroSource.Library, millis = 16), 0f)
        assertEquals(0f, moveTo(HeroSource.Quiet, millis = 16), 0f)
    }

    @Test
    fun the_scrim_switches_at_once_with_reduced_motion() {
        show(Motion(reducedMotion = true))

        assertEquals(1f, moveTo(HeroSource.Library, millis = 16), 0f)
        assertEquals(0f, moveTo(HeroSource.Quiet, millis = 16), 0f)
    }
}
