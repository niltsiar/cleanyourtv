package dev.niltsiar.tvhome.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import app.cash.molecule.RecompositionMode
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.OverlayHost
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

/** The library in Home's overlay host over a Home that is as bright as it gets, read from the rendered pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class LibraryOverlayLookUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var home by mutableStateOf(Color.Black)

    /** How much of 255 the panel's inside changes when Home behind it goes from black to white. */
    private fun bleedThrough(kind: GlassKind): Int {
        val config = TvConfig(appLibrary = true)
        val library = FakeLibrary(listOf(libraryApp("Alpha"), libraryApp("Beta")))
        lateinit var viewModel: MoleculeLibraryViewModel
        compose.setContent {
            TvTheme(glassKind = kind) {
                val scope = rememberCoroutineScope()
                viewModel =
                    remember {
                        MoleculeLibraryViewModel(
                            library.sources,
                            config,
                            Locale.ENGLISH,
                            scope,
                            RecompositionMode.Immediate,
                            EmptyCoroutineContext,
                        )
                    }
                val state = viewModel.uiState.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(home))
                    OverlayHost(open = { state.value.open }) {
                        LibraryOverlay(
                            state = { state.value },
                            layout = config.libraryLayout(),
                            actions = viewModel.actions,
                            art = { null },
                        )
                    }
                }
            }
        }
        viewModel.onOpen()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        // The gap between the title and the count, in the panel's header row.
        val inside = {
            compose
                .onRoot()
                .captureToImage()
                .toPixelMap()[PANEL_X, PANEL_HEADER_Y]
                .red
        }
        val dark = inside()
        home = Color.White
        compose.waitForIdle()
        return Math.round((inside() - dark) * 255)
    }

    @Test
    fun a_bright_home_does_not_show_through_the_panel_with_flat_glass() {
        assertTrue(bleedThrough(GlassKind.Flat) <= MAX_BLEED)
    }

    @Test
    fun a_bright_home_does_not_show_through_the_panel_with_refractive_glass() {
        assertTrue(bleedThrough(GlassKind.Refractive) <= MAX_BLEED)
    }

    private companion object {
        const val PANEL_X = 960
        const val PANEL_HEADER_Y = 304
        const val MAX_BLEED = 10
    }
}
