package dev.niltsiar.tvhome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.feature.settings.MoleculeSettingsViewModel
import dev.niltsiar.tvhome.feature.settings.PageNotice
import dev.niltsiar.tvhome.feature.settings.SettingsEffect
import dev.niltsiar.tvhome.feature.settings.SettingsScreen
import dev.niltsiar.tvhome.feature.settings.R as SettingsR

/**
 * Launcher settings in the TV theme of the config, which the choices on this screen change as they are made. A refused
 * write, a stored wallpaper and TV settings that [onOpenTvSettings] could not open are told in the page's notice.
 */
@Composable
internal fun SettingsHost(
    graph: AppGraph,
    viewModel: MoleculeSettingsViewModel,
    onChooseDefaultHome: () -> Unit,
    onOpenTvSettings: () -> Boolean,
    onReorder: () -> Unit,
) {
    val notice = remember { PageNotice() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.SaveFailed -> notice.show(SettingsR.string.settings_save_failed)
                SettingsEffect.WallpaperApplied -> notice.show(SettingsR.string.wallpaper_applied)
            }
        }
    }
    val config = graph.configRepository.config.collectAsStateWithLifecycle()
    val motion by config.derived(TvConfig::motion)
    val focusStyle by config.derived(TvConfig::focusStyle)
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    TvTheme(motion = motion, focusStyle = focusStyle) {
        SettingsScreen(
            state = { state.value },
            actions = viewModel.actions,
            onChooseDefaultHome = onChooseDefaultHome,
            onOpenTvSettings = { if (!onOpenTvSettings()) notice.show(R.string.not_available) },
            onReorder = onReorder,
            notice = notice,
            art = viewModel.art,
        )
    }
}
