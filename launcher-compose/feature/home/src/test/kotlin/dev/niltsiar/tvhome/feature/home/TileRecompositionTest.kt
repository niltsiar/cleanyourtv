package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.setObserver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.niltsiar.tvhome.core.config.ClockFormat
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputArrival
import dev.niltsiar.tvhome.core.model.InputTarget
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.robolectric.annotation.GraphicsMode

/**
 * A focus move changes the shelf and the focused tile's look, composes no tile again and recomposes no grid item: each
 * grid item is a composition of its own, and one that recomposes costs the frame even when all it holds skips.
 */
@OptIn(InternalComposeTracingApi::class, ExperimentalComposeRuntimeApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
open class TileRecompositionTest {
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

    /** Counts the recompositions of the grid's items: every composition under Home's but Home's own. */
    private val items =
        object : CompositionObserver {
            var home: Any? = null
            var count = 0

            override fun onBeginComposition(composition: ObservableComposition) {
                if (composition !== home) count++
            }

            override fun onScopeEnter(scope: RecomposeScope) = Unit

            override fun onReadInScope(
                scope: RecomposeScope,
                value: Any,
            ) = Unit

            override fun onScopeExit(scope: RecomposeScope) = Unit

            override fun onEndComposition(composition: ObservableComposition) = Unit

            override fun onScopeInvalidated(
                scope: RecomposeScope,
                value: Any?,
            ) = Unit

            override fun onScopeDisposed(scope: RecomposeScope) = Unit
        }

    protected open val alwaysShowNames = false
    protected open val motion = Motion()

    private val inputs = MutableStateFlow(FourPorts)
    private val config = MutableStateFlow(TvConfig())
    private var habitApp = "pkg.3"
    private var habitLooks = 0
    private lateinit var viewModel: MoleculeHomeViewModel

    @Before
    fun showHome() {
        Composer.setTracer(tiles)
        val apps = (1..12).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }
        config.value =
            TvConfig(
                columns = 6,
                cinematicHome = true,
                appLibrary = true,
                focusOvershoot = motion.focusOvershoot,
                alwaysShowNames = alwaysShowNames,
                inputArrival = true,
                habitHint = true,
                unusedFadeDays = 60,
            )
        compose.setContent {
            val home = currentComposer.composition
            DisposableEffect(home) {
                items.home = home
                val observing = home.setObserver(items)
                onDispose { observing?.dispose() }
            }
            TvTheme(motion = motion) {
                viewModel =
                    rememberHomeViewModel(
                        sources(
                            apps = flowOf(AppList(apps)),
                            inputs = inputs,
                            habit = { habitApp.also { habitLooks++ } },
                            openedSince = flowOf(0L),
                        ),
                        config,
                        mutableListOf(),
                    )
                val state = viewModel.uiState.collectAsState()
                // As the host reads it: each layout is derived from the config and changes only when its own fields do.
                val current = config.collectAsState()
                val layout by remember { derivedStateOf { current.value.homeLayout() } }
                val topBar by remember { derivedStateOf { current.value.topBarLayout() } }
                val shelfLayout by remember { derivedStateOf { current.value.shelfLayout().copy(scrimAlpha = 0f) } }
                HomeScreen(
                    state = { state.value },
                    layout = layout,
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = topBar,
                    shelfLayout = shelfLayout,
                    actions = viewModel.actions,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        assertTrue("tiles composed at first: ${tiles.count}", tiles.count >= 12)
        assertEquals(setOf("pkg.7", "pkg.8", "pkg.9", "pkg.10", "pkg.11", "pkg.12"), viewModel.uiState.value.faded)
        tiles.count = 0
        items.count = 0
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
    fun a_device_offered_on_the_inputs_button_composes_no_tile_and_recomposes_no_grid_item() {
        inputs.value = FourPorts.copy(arrival = InputArrival("Apple TV \u00B7 HDMI 2", InputTarget.TvInput("tv/.Cec/HDMI2"), token = 1))
        compose.mainClock.advanceTimeBy(2_100)
        compose.waitForIdle()

        compose.onNode(hasContentDescription("Apple TV", substring = true)).assertExists()
        assertEquals(0, tiles.count)
        assertEquals(0, items.count)
    }

    @Test
    fun a_new_habit_composes_no_tile_and_recomposes_no_grid_item() {
        assertEquals(1, habitLooks)
        habitApp = "pkg.9"
        compose.mainClock.advanceTimeBy(HABIT_REFRESH_MS)
        compose.waitForIdle()

        assertEquals(2, habitLooks)
        assertEquals(0, tiles.count)
        assertEquals(0, items.count)
    }

    @Test
    fun a_stored_choice_that_no_tile_reads_composes_no_tile_and_the_focus_moves_after_it_cost_no_more() {
        config.value = config.value.copy(clockFormat = ClockFormat.Hour12)
        compose.waitForIdle()

        assertEquals("8:15", viewModel.uiState.value.clock)
        assertEquals(0, tiles.count)
        assertEquals(0, items.count)

        press(Key.DirectionRight)
        press(Key.DirectionRight)

        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
        assertEquals(0, tiles.count)
        assertEquals(0, items.count)
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

    @Test
    fun a_focus_move_along_the_dock_recomposes_no_grid_item() {
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
        assertEquals(0, items.count)
    }

    @Test
    fun a_focus_move_along_the_grid_recomposes_no_grid_item() {
        press(Key.DirectionDown)
        items.count = 0
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
        assertEquals(0, items.count)
    }
}

/** The names under the tiles fade in the draw phase, so a focus move recomposes no more than without them. */
class NamedTileRecompositionTest : TileRecompositionTest() {
    override val alwaysShowNames = true
}

/** A tile that pops draws over its neighbours by an order read in the placement phase, so a focus move recomposes no more. */
class PoppingTileRecompositionTest : TileRecompositionTest() {
    override val motion = Motion(focusOvershoot = true)
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
