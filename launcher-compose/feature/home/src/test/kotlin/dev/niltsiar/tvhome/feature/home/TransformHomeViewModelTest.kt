package dev.niltsiar.tvhome.feature.home

import androidx.appstate.transform.testing.TransformTestScope
import androidx.appstate.transform.testing.transformTest
import dev.niltsiar.tvhome.core.model.AppEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class TransformHomeViewModelTest {
    private fun TransformTestScope.viewModel(
        apps: Flow<List<AppEntry>> = flowOf(NineApps),
        clock: Flow<LocalDateTime> = flowOf(Evening),
    ) = TransformHomeViewModel(apps, clock, Locale.ENGLISH, backgroundScope, testDispatcher).also { settle() }

    /** Collected flows need one pass to start and one to apply their first value. */
    private fun TransformTestScope.settle() = repeat(3) { advance() }

    @Test
    fun focusing_a_dock_app_updates_the_shelf_label() =
        transformTest {
            val viewModel = viewModel()
            viewModel.onFocus("pkg.4")
            settle()
            assertEquals("App 4", viewModel.uiState.value.focusedLabel)
        }

    @Test
    fun a_clock_tick_updates_the_clock_text() =
        transformTest {
            val clock = MutableStateFlow(Evening)
            val viewModel = viewModel(clock = clock)
            assertEquals("20:15", viewModel.uiState.value.clock)
            assertEquals("Tuesday 3 November", viewModel.uiState.value.date)

            clock.value = Evening.plusMinutes(1)
            settle()
            assertEquals("20:16", viewModel.uiState.value.clock)
        }

    @Test
    fun nine_apps_split_into_six_in_the_dock_and_three_in_the_grid() =
        transformTest {
            val state = viewModel().uiState.value
            assertEquals(NineApps.take(6), state.dock)
            assertEquals(NineApps.drop(6), state.grid)
        }

    @Test
    fun clicking_an_app_emits_a_launch_effect_for_its_package() =
        transformTest {
            val viewModel = viewModel()
            viewModel.onClick("pkg.2")
            assertEquals(HomeEffect.LaunchApp("pkg.2"), viewModel.effects.first())
        }
}
