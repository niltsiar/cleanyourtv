package dev.niltsiar.tvhome.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.niltsiar.tvhome.core.config.ConfigRepository
import kotlinx.coroutines.flow.Flow
import java.io.IOException

/**
 * The settings screen's state logic: the rows are the repository's config read through [settingsGroups], nothing is
 * kept apart from it, and a pick or Follow theme goes to the repository, which answers through the same flow.
 * [onSaveFailed] runs when the store refuses a write.
 */
@Composable
internal fun settingsPresenter(
    events: Flow<SettingsEvent>,
    repository: ConfigRepository,
    onSaveFailed: () -> Unit,
): SettingsUiState {
    val currentOnSaveFailed by rememberUpdatedState(onSaveFailed)
    LaunchedEffect(events, repository) {
        events.collect { event ->
            try {
                when (event) {
                    is SettingsEvent.Pick -> rowOf(event.row).pick(repository, event.option)
                    SettingsEvent.FollowTheme -> repository.followTheme()
                }
            } catch (_: IOException) {
                currentOnSaveFailed()
            }
        }
    }
    val config by repository.config.collectAsState()
    val followsTheme by repository.followsTheme.collectAsState()
    return remember(config, followsTheme) { SettingsUiState(settingsSections(config), followsTheme, config.launcherSettingsButton) }
}
