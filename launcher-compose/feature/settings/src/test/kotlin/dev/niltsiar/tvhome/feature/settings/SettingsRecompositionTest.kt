package dev.niltsiar.tvhome.feature.settings

import android.app.Application
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A focus move recomposes no row: the focus fill and the label's brightness are drawn from animations, so a row
 * recomposes only when its own selection changes.
 */
@OptIn(InternalComposeTracingApi::class, ExperimentalComposeRuntimeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class SettingsRecompositionTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private lateinit var settings: SettingsHarness

    /** Counts the SettingRow bodies that run; the compiler calls it for each composable that does not skip. */
    private val rows =
        object : CompositionTracer {
            var count = 0

            override fun isTraceInProgress() = true

            override fun traceEventStart(
                key: Int,
                dirty1: Int,
                dirty2: Int,
                info: String,
            ) {
                if (info.startsWith("dev.niltsiar.tvhome.feature.settings.SettingRow ")) count++
            }

            override fun traceEventEnd() = Unit
        }

    @Before
    fun showSettings() {
        Composer.setTracer(rows)
        settings = SettingsHarness(app, """{"columns": 6}""")
        compose.setContent {
            TvTheme {
                val state = settings.viewModel.uiState.collectAsState()
                SettingsScreen({ state.value }, settings.viewModel.actions, onOpenTvSettings = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("option:Columns:2").assertIsFocused()
        assertTrue("rows composed at first: ${rows.count}", rows.count >= settings.state.sections.sumOf { it.rows.size })
        rows.count = 0
    }

    @After
    fun stopCounting() {
        Composer.setTracer(Idle)
        settings.stop()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun a_focus_move_along_a_row_and_down_the_list_recomposes_no_row() {
        press(Key.DirectionRight)
        press(Key.DirectionLeft)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionUp)

        compose.onNodeWithTag("option:RowsBelowDock:0").assertIsFocused()
        assertEquals(0, rows.count)
    }

    @Test
    fun a_pick_recomposes_the_row_it_changes_alone() {
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.waitUntil(10_000) { settings.row(SettingsRowId.Columns).selected == 3 }
        compose.waitForIdle()

        assertEquals(1, rows.count)
    }
}

@OptIn(InternalComposeTracingApi::class)
private object Idle : CompositionTracer {
    override fun isTraceInProgress() = false

    override fun traceEventStart(
        key: Int,
        dirty1: Int,
        dirty2: Int,
        info: String,
    ) = Unit

    override fun traceEventEnd() = Unit
}
