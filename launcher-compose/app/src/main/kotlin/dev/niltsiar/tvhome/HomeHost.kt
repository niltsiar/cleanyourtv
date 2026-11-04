package dev.niltsiar.tvhome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.niltsiar.tvhome.feature.home.HomeEffect
import dev.niltsiar.tvhome.feature.home.HomeScreen
import dev.niltsiar.tvhome.feature.home.MoleculeHomeViewModel
import dev.niltsiar.tvhome.feature.home.minuteClock

/** Home state comes from the Molecule-hosted presenter. */
@Composable
internal fun HomeHost(
    graph: AppGraph,
    onLaunchApp: (String) -> Unit,
    homePresses: () -> Int,
    viewModel: MoleculeHomeViewModel = viewModel { MoleculeHomeViewModel(graph.appsProvider.apps, minuteClock()) },
) {
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.LaunchApp -> onLaunchApp(effect.packageName)
            }
        }
    }
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state = { state.value }, onFocus = viewModel::onFocus, onClick = viewModel::onClick, homePresses = homePresses)
}
