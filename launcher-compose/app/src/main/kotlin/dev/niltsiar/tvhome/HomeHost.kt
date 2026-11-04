package dev.niltsiar.tvhome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.feature.home.HomeEffect
import dev.niltsiar.tvhome.feature.home.HomeScreen
import dev.niltsiar.tvhome.feature.home.MoleculeHomeViewModel
import dev.niltsiar.tvhome.feature.home.homeLayout
import dev.niltsiar.tvhome.feature.home.minuteClock

/** Home state comes from the Molecule-hosted presenter. */
@Composable
internal fun HomeHost(
    graph: AppGraph,
    onLaunchApp: (AppEntry) -> Unit,
    homePresses: () -> Int,
    viewModel: MoleculeHomeViewModel = viewModel { MoleculeHomeViewModel(graph.appsProvider.apps, minuteClock(), graph.config) },
) {
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.LaunchApp -> onLaunchApp(effect.app)
            }
        }
    }
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val layout = remember(graph) { graph.config.homeLayout() }
    HomeScreen(
        state = { state.value },
        layout = layout,
        onFocus = viewModel::onFocus,
        onClick = viewModel::onClick,
        homePresses = homePresses,
    )
}
