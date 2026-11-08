package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppList
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A cold start: the window asks Home for focus on its first frame, the app list arrives frames later, and its tiles show names until their art lands. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeColdStartFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val config = SixColumns.copy(cinematicHome = true, appLibrary = true, libraryButton = true)
    private val apps = MutableSharedFlow<AppList>(replay = 1)

    private fun focusedTags(): List<String> =
        compose
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true))
            .fetchSemanticsNodes()
            .map {
                it.config.getOrNull(SemanticsProperties.TestTag)
                    ?: it.config.getOrNull(SemanticsProperties.ContentDescription).toString()
            }

    @Test
    fun focus_goes_to_dock_slot_1_and_no_top_bar_button_is_ever_focused_when_the_apps_arrive_after_the_first_frame() {
        val sources = sources(apps = apps)
        compose.setContent {
            TvTheme {
                val viewModel = rememberHomeViewModel(sources, config, mutableListOf())
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
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocus()
        }
        val seen = mutableListOf<String>()
        repeat(10) {
            compose.mainClock.advanceTimeByFrame()
            seen += focusedTags()
        }
        apps.tryEmit(AppList(NineApps))
        repeat(20) {
            compose.mainClock.advanceTimeByFrame()
            seen += focusedTags()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        assertEquals(emptyList<String>(), seen.filter { it != "tile:pkg.1" }.distinct())
    }

    private fun nameContrastUnderTopBarShade(darkTheme: Boolean): Float {
        var fill = Color.Unspecified
        apps.tryEmit(AppList(NineApps))
        compose.setContent {
            TvTheme(darkTheme = darkTheme) {
                val viewModel = rememberHomeViewModel(sources(apps = apps), config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                fill = TvTheme.colors.surface
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

        val name = compose.onNodeWithTag("tile:pkg.1").textStyle().color

        fun shaded(color: Color) = Color.Black.copy(alpha = TOP_BAR_DOCK_SHADE).compositeOver(color)
        return (shaded(name).luminance() + 0.05f) / (shaded(fill).luminance() + 0.05f)
    }

    @Test
    fun the_name_on_a_tile_without_art_reads_4_5_to_1_on_its_fill_under_the_dock_shade_of_top_bar_focus() {
        val ratio = nameContrastUnderTopBarShade(darkTheme = true)

        assertTrue("$ratio", ratio >= 4.5f)
    }

    @Test
    fun the_name_on_a_tile_without_art_reads_4_5_to_1_on_its_fill_with_night_mode_off() {
        val ratio = nameContrastUnderTopBarShade(darkTheme = false)

        assertTrue("$ratio", ratio >= 4.5f)
    }
}
