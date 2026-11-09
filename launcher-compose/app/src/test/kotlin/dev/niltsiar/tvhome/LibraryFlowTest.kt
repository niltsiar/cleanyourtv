package dev.niltsiar.tvhome

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import app.cash.molecule.RecompositionMode
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.LibraryApp
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.Wallpaper
import dev.niltsiar.tvhome.feature.home.HomeScreen
import dev.niltsiar.tvhome.feature.home.HomeSources
import dev.niltsiar.tvhome.feature.home.MoleculeHomeViewModel
import dev.niltsiar.tvhome.feature.home.homeLayout
import dev.niltsiar.tvhome.feature.home.topBarLayout
import dev.niltsiar.tvhome.feature.library.LibraryOverlay
import dev.niltsiar.tvhome.feature.library.LibrarySources
import dev.niltsiar.tvhome.feature.library.MoleculeLibraryViewModel
import dev.niltsiar.tvhome.feature.library.libraryLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

/** Home and the library as the activity stacks them: the library in Home's overlay host. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class LibraryFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val apps = (1..9).map { AppEntry(key = "pkg.$it/.Main", packageName = "pkg.$it", label = "App $it") }

    private fun showHomeAndLibrary(config: TvConfig) {
        compose.setContent {
            TvTheme {
                val glass = rememberGlassState()
                val scope = rememberCoroutineScope()
                val home =
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
                            MutableStateFlow(config),
                            Locale.ENGLISH,
                            "EEEE d MMMM",
                            scope,
                            RecompositionMode.Immediate,
                            EmptyCoroutineContext,
                        )
                    }
                val library =
                    remember {
                        val sources = LibrarySources(load = { apps.map { LibraryApp(it, onHome = true) } }, changes = emptyFlow())
                        MoleculeLibraryViewModel(
                            sources,
                            MutableStateFlow(config),
                            Locale.ENGLISH,
                            scope,
                            RecompositionMode.Immediate,
                            EmptyCoroutineContext,
                        )
                    }
                val homeState by home.uiState.collectAsState()
                val libraryState by library.uiState.collectAsState()
                HomeScreen(
                    state = { homeState },
                    layout = config.homeLayout(),
                    onFocus = home::onFocus,
                    onClick = home::onClick,
                    topBar = config.topBarLayout(),
                    actions = home.actions,
                    glass = glass,
                    libraryOpen = { libraryState.open },
                    onOpenLibrary = library::onOpen,
                    overlays = {
                        LibraryOverlay(
                            state = { libraryState },
                            layout = config.libraryLayout(),
                            actions = library.actions,
                            art = { null },
                        )
                    },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1/.Main").assertIsFocused()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    @Test
    fun the_pill_opens_the_library_and_back_returns_focus_to_the_pill() {
        showHomeAndLibrary(TvConfig(columns = 6, appLibrary = true))
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("library:pill").assertIsFocused()

        press(Key.DirectionCenter)
        settle()
        compose.onNodeWithTag("library").assertExists()
        compose.onNodeWithTag("card:pkg.1/.Main").assertIsFocused()

        back()
        settle()
        compose.onNodeWithTag("library").assertDoesNotExist()
        compose.onNodeWithTag("library:pill").assertIsFocused()
    }

    @Test
    fun the_top_bar_button_opens_the_library_and_back_returns_focus_to_the_button() {
        showHomeAndLibrary(TvConfig(columns = 6, appLibrary = true, libraryButton = true, showLibraryPill = false))
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("App library").assertIsFocused()

        press(Key.DirectionCenter)
        settle()
        compose.onNodeWithTag("library").assertExists()

        back()
        settle()
        compose.onNodeWithTag("library").assertDoesNotExist()
        compose.onNodeWithContentDescription("App library").assertIsFocused()
    }
}
