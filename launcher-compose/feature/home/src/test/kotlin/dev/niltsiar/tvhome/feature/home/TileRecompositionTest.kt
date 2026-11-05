package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A focus move changes the shelf and the focused tile's look, and composes no tile again. */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class TileRecompositionTest {
    @get:Rule
    val compose = createComposeRule()

    /** Counts the AppTile bodies that run; the compiler calls it for each composable that does not skip. */
    private val tiles =
        object : CompositionTracer {
            var count = 0

            override fun isTraceInProgress() = true

            override fun traceEventStart(
                key: Int,
                dirty1: Int,
                dirty2: Int,
                info: String,
            ) {
                if (info.startsWith("dev.niltsiar.tvhome.feature.home.AppTile ")) count++
            }

            override fun traceEventEnd() = Unit
        }

    @Before
    fun showHome() {
        Composer.setTracer(tiles)
        val apps = (1..12).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }
        val config = TvConfig(columns = 6, cinematicHome = true, appLibrary = true, focusOvershoot = false)
        compose.setContent {
            TvTheme(glassKind = GlassKind.Flat) {
                val viewModel = rememberHomeViewModel(sources(apps = flowOf(AppList(apps))), config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = config.topBarLayout(),
                    shelfLayout = config.shelfLayout().copy(scrimAlpha = 0f),
                    actions = viewModel.actions,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        assertTrue("tiles composed at first: ${tiles.count}", tiles.count >= 12)
        tiles.count = 0
    }

    @After
    fun stopCounting() {
        Composer.setTracer(Idle)
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun a_focus_move_along_the_dock_composes_no_tile_again() {
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
        assertEquals(0, tiles.count)
    }

    @Test
    fun a_focus_move_into_and_along_the_grid_composes_no_tile_again() {
        press(Key.DirectionDown)
        press(Key.DirectionRight)

        compose.onNodeWithTag("tile:pkg.8").assertIsFocused()
        assertEquals(0, tiles.count)
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
