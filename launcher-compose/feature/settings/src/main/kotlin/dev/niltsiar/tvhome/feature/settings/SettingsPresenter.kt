package dev.niltsiar.tvhome.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.ConfigRepository
import kotlinx.coroutines.flow.Flow
import java.io.IOException

/**
 * The settings screen's state logic: the rows are the repository's config read through [settingsGroups], nothing is
 * kept apart from it, and a pick or Follow theme goes to the repository, which answers through the same flow.
 * Only the open picker is kept here. [onSaveFailed] runs when the store refuses a write.
 */
@Composable
internal fun settingsPresenter(
    events: Flow<SettingsEvent>,
    repository: ConfigRepository,
    sources: SettingsSources,
    onSaveFailed: () -> Unit,
): SettingsUiState {
    val currentOnSaveFailed by rememberUpdatedState(onSaveFailed)
    var picker by remember { mutableStateOf<OpenOnStartPicker?>(null) }
    LaunchedEffect(events, repository, sources) {
        events.collect { event ->
            try {
                when (event) {
                    is SettingsEvent.Pick -> {
                        rowOf(event.row).pick(repository, event.option)
                    }

                    SettingsEvent.FollowTheme -> {
                        repository.followTheme()
                    }

                    SettingsEvent.OpenPicker -> {
                        picker = startPicker(sources.openOnStartApps(), repository.openOnStartApp.value)
                    }

                    SettingsEvent.ClosePicker -> {
                        picker = null
                    }

                    is SettingsEvent.PickStartApp -> {
                        picker?.let { open ->
                            picker = null
                            if (event.index == 0) {
                                repository.clear(Choices.OpenOnStart)
                            } else {
                                sources.skipThisBoot()
                                repository.set(Choices.OpenOnStart, open.apps[event.index - 1].packageName)
                            }
                        }
                    }
                }
            } catch (_: IOException) {
                currentOnSaveFailed()
            }
        }
    }
    val config by repository.config.collectAsState()
    val followsTheme by repository.followsTheme.collectAsState()
    val pick by repository.openOnStartApp.collectAsState()
    val startChoice = config.openOnStartChoice
    val startRow = remember(pick, startChoice) { if (startChoice) OpenOnStartRow(startValue(pick, sources)) else null }
    return remember(config, followsTheme, startRow, picker) {
        SettingsUiState(settingsSections(config), followsTheme, config.launcherSettingsButton, startRow, picker)
    }
}

private fun startValue(
    pick: String,
    sources: SettingsSources,
): OptionLabel = if (pick.isEmpty()) OptionLabel.Res(R.string.settings_off) else OptionLabel.Text(sources.appLabel(pick) ?: pick)
