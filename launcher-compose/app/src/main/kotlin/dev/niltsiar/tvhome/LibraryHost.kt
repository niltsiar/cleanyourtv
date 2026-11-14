package dev.niltsiar.tvhome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.feature.library.LibraryEffect
import dev.niltsiar.tvhome.feature.library.LibraryOverlay
import dev.niltsiar.tvhome.feature.library.MoleculeLibraryViewModel
import dev.niltsiar.tvhome.feature.library.libraryLayout

/**
 * The App library, for Home's overlay host: its state comes from the Molecule-hosted presenter. [onLaunchApp] opens a card's
 * app and says whether it started.
 */
@Composable
internal fun LibraryHost(
    graph: AppGraph,
    viewModel: MoleculeLibraryViewModel,
    onLaunchApp: (AppEntry) -> Boolean,
    onOpenAppInfo: (String) -> Unit,
    onSaveFailed: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    SideEffect { viewModel.onLocaleChanged(locale) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LibraryEffect.LaunchApp -> if (!onLaunchApp(effect.app)) viewModel.onAppNotStarted(effect.app.key)
                is LibraryEffect.OpenAppInfo -> onOpenAppInfo(effect.packageName)
                LibraryEffect.SaveFailed -> onSaveFailed()
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStopped() }
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val config = graph.configRepository.config.collectAsStateWithLifecycle()
    val layout by config.derived(TvConfig::libraryLayout)
    LibraryOverlay(
        state = { state.value },
        layout = layout,
        actions = viewModel.actions,
        art = graph.librarySources.art,
    )
}
