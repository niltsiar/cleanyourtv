package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

class MoleculeHomeViewModelTest {
    private fun CoroutineScope.viewModel() =
        MoleculeHomeViewModel(
            sources(),
            MutableStateFlow(SixColumns),
            Locale.ENGLISH,
            "EEEE d MMMM",
            this,
            RecompositionMode.Immediate,
            EmptyCoroutineContext,
        )

    @Test
    fun focusing_a_dock_app_updates_the_shelf_label() =
        runTest {
            val viewModel = backgroundScope.viewModel()
            viewModel.uiState.test {
                viewModel.onFocus("pkg.4")
                assertEquals("App 4", awaitMatching { it.focusedLabel == "App 4" }.focusedLabel)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clicking_an_app_emits_a_launch_effect_for_its_package() =
        runTest {
            val viewModel = backgroundScope.viewModel()
            viewModel.uiState.first { it.dock.isNotEmpty() }
            viewModel.effects.test {
                viewModel.onClick("pkg.2")
                assertEquals(HomeEffect.LaunchApp(NineApps[1]), awaitItem())
            }
        }

    @Test
    fun the_settings_button_emits_an_open_settings_effect() =
        runTest {
            val viewModel = backgroundScope.viewModel()
            viewModel.uiState.first { it.dock.isNotEmpty() }
            viewModel.effects.test {
                viewModel.onOpenSettings()
                assertEquals(HomeEffect.OpenSettings, awaitItem())
                viewModel.onOpenNetwork()
                assertEquals(HomeEffect.OpenNetwork, awaitItem())
                viewModel.onOpenWifiSettings()
                assertEquals(HomeEffect.OpenWifiSettings, awaitItem())
            }
        }

    @Test
    fun a_language_change_formats_the_date_in_the_new_language_and_pattern() =
        runTest {
            val viewModel = backgroundScope.viewModel()
            viewModel.uiState.test {
                assertEquals("Tuesday 3 November", awaitMatching { it.date.isNotEmpty() }.date)

                viewModel.onLocaleChanged(Locale.forLanguageTag("es-ES"), "EEEE d 'de' MMMM")

                assertEquals("martes 3 de noviembre", awaitMatching { it.date.startsWith("martes") }.date)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
