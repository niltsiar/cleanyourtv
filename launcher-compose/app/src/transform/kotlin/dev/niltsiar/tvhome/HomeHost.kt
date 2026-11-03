package dev.niltsiar.tvhome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.niltsiar.tvhome.feature.home.HomeEffect
import dev.niltsiar.tvhome.feature.home.HomeScreen
import dev.niltsiar.tvhome.feature.home.TransformHomeViewModel
import dev.niltsiar.tvhome.feature.home.minuteClock

/** The `transform` flavor: Home state comes from an AppState Transform in the ViewModel. */
@Composable
internal fun HomeHost(
    graph: AppGraph,
    onLaunchApp: (String) -> Unit,
    viewModel: TransformHomeViewModel = viewModel { TransformHomeViewModel(graph.appsProvider.apps, minuteClock()) },
) {
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.LaunchApp -> onLaunchApp(effect.packageName)
            }
        }
    }
    val state = viewModel.uiState
    HomeScreen(state = { state.value }, onFocus = viewModel::onFocus, onClick = viewModel::onClick)
}
