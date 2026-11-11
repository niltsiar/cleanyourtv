package dev.niltsiar.tvhome

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.cash.molecule.RecompositionMode
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.Wallpaper
import dev.niltsiar.tvhome.feature.home.HomeSources
import dev.niltsiar.tvhome.feature.home.MoleculeHomeViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

/** [HomeHost] over the real repository: the layouts it draws always belong to the split of the dock and grid it draws. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = TvHomeApplication::class, qualifiers = "w960dp-h540dp-xhdpi")
class HomeHostConfigTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val graph get() = (compose.activity.application as TvHomeApplication).graph
    private val apps = (1..9).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }
    private lateinit var viewModel: MoleculeHomeViewModel

    /** The tiles in the dock's row, and how wide one is. */
    private data class DockRow(
        val tiles: Int,
        val tileWidth: Float,
    )

    private fun dockRow(): DockRow? {
        val tiles =
            compose
                .onAllNodes(SemanticsMatcher("a tile") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tile:") == true })
                .fetchSemanticsNodes()
        val first = tiles.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == "tile:pkg.1" } ?: return null
        val bounds = first.boundsInRoot
        return DockRow(tiles.count { it.boundsInRoot.top == bounds.top }, bounds.width)
    }

    private fun choose(columns: Int) {
        runBlocking { graph.configRepository.set(Choices.Columns, columns) }
        compose.waitUntil(10_000) { graph.configRepository.config.value.columns == columns }
    }

    /** Home's own lifecycle, which the host follows: an activity under another one is stopped. */
    private class HomeLifecycle : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry

        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun showHome(home: LifecycleOwner? = null) {
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides (home ?: LocalLifecycleOwner.current)) {
                TvTheme {
                    val scope = rememberCoroutineScope()
                    viewModel =
                        remember {
                            val sources =
                                HomeSources(
                                    apps = flowOf(AppList(apps)),
                                    clock = flowOf(null),
                                    network = flowOf(NetworkState()),
                                    topBarApps = flowOf(emptyList()),
                                    inputs = flowOf(InputsMenu(available = true)),
                                    wallpaper = flowOf(Wallpaper()),
                                    systemHour24 = { true },
                                    saveOrder = { _, _ -> true },
                                    removeFromHome = { true },
                                )
                            MoleculeHomeViewModel(
                                sources,
                                graph.configRepository.config,
                                Locale.ENGLISH,
                                "EEEE d MMMM",
                                scope,
                                RecompositionMode.Immediate,
                                EmptyCoroutineContext,
                            )
                        }
                    HomeHost(
                        graph = graph,
                        onLaunchApp = {},
                        onOpenSettings = {},
                        onOpenNetwork = {},
                        onOpenWifiSettings = {},
                        onOpenAppInfo = {},
                        onOpenNativeSettings = {},
                        onOpenLauncherSettings = {},
                        onOpenPowerMenu = {},
                        onOrderNotSaved = {},
                        onLibrarySaveFailed = {},
                        onOpenInput = {},
                        onNoInputs = {},
                        glass = rememberGlassState(),
                        libraryOpen = { false },
                        onOpenLibrary = {},
                        homePresses = { 0 },
                        inputsRequests = { 0 },
                        settingsClosing = { null },
                        overlays = {},
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    @Test
    fun a_choice_made_while_home_is_stopped_never_draws_the_new_layout_over_the_old_split() {
        choose(7)
        showHome()
        compose.waitUntil(10_000) { dockRow()?.tiles == 7 }
        val seven = dockRow()!!
        compose.runOnUiThread { viewModel.onStopped() }
        compose.waitForIdle()

        choose(4)
        compose.waitForIdle()
        assertEquals(seven, dockRow())

        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { viewModel.onStarted() }
        val drawn = mutableListOf<DockRow?>()
        repeat(6) {
            compose.mainClock.advanceTimeByFrame()
            drawn += dockRow()
        }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10_000) { dockRow()?.tiles == 4 }
        val four = dockRow()!!

        assertTrue(four.tileWidth > seven.tileWidth)
        assertTrue("$drawn", drawn.all { it == seven || it == four })
    }

    @Test
    fun leaving_home_ends_reorder_mode_so_it_is_gone_when_home_comes_back() {
        val home = HomeLifecycle()
        showHome(home)
        compose.waitUntil(10_000) { dockRow() != null }
        compose.runOnUiThread { viewModel.actions.onToggleReorder() }
        compose.waitUntil(10_000) { exists("reorder:hint") }

        compose.runOnUiThread { home.moveTo(Lifecycle.State.CREATED) }
        compose.waitForIdle()
        compose.runOnUiThread { home.moveTo(Lifecycle.State.RESUMED) }
        compose.waitForIdle()

        assertFalse(exists("reorder:hint"))
    }

    @Test
    fun a_choice_made_while_home_is_stopped_shows_once_home_starts_again() {
        choose(7)
        val home = HomeLifecycle()
        showHome(home)
        compose.waitUntil(10_000) { dockRow()?.tiles == 7 }

        compose.runOnUiThread { home.moveTo(Lifecycle.State.CREATED) }
        compose.waitForIdle()
        choose(4)
        compose.runOnUiThread { home.moveTo(Lifecycle.State.RESUMED) }

        compose.waitUntil(10_000) { dockRow()?.tiles == 4 }
    }
}
