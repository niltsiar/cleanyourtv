package dev.niltsiar.tvhome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.feature.settings.MoleculeSettingsViewModel
import dev.niltsiar.tvhome.feature.settings.SettingsScreen

/** Launcher settings in the TV theme of the config, which the choices on this screen change as they are made. */
@Composable
internal fun SettingsHost(
    graph: AppGraph,
    viewModel: MoleculeSettingsViewModel,
    onOpenTvSettings: () -> Unit,
    onSaveFailed: () -> Unit,
) {
    LaunchedEffect(viewModel) { viewModel.saveFailed.collect { onSaveFailed() } }
    val config = graph.configRepository.config.collectAsStateWithLifecycle()
    val motion by config.derived(TvConfig::motion)
    val focusStyle by config.derived(TvConfig::focusStyle)
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    TvTheme(motion = motion, focusStyle = focusStyle) {
        SettingsScreen(state = { state.value }, actions = viewModel.actions, onOpenTvSettings = onOpenTvSettings)
    }
}
