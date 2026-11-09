package dev.niltsiar.tvhome.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import kotlinx.coroutines.flow.Flow
import java.io.IOException

/**
 * The settings screen's state logic: the rows are the repository's config read through [settingsGroups], nothing is
 * kept apart from it, and a pick or Follow theme goes to the repository, which answers through the same flow.
 * Only the open pickers, the theme's picture names and the picked app's label are kept here. [onEffect] gets
 * [SettingsEffect.SaveFailed] when the store refuses a write and [SettingsEffect.WallpaperApplied] after a picture or None
 * was stored as the wallpaper.
 */
@Composable
internal fun settingsPresenter(
    events: Flow<SettingsEvent>,
    repository: ConfigRepository,
    sources: SettingsSources,
    onEffect: (SettingsEffect) -> Unit,
): SettingsUiState {
    val currentOnEffect by rememberUpdatedState(onEffect)
    var picker by remember { mutableStateOf<OpenOnStartPicker?>(null) }
    var wallpaperOpen by remember { mutableStateOf(false) }
    val pictures by produceState(emptyList<String>(), sources) { value = sources.wallpaperPictures() }
    val currentPictures by rememberUpdatedState(pictures)
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
                        wallpaperOpen = false
                    }

                    SettingsEvent.OpenWallpaperPicker -> {
                        wallpaperOpen = currentPictures.isNotEmpty()
                    }

                    is SettingsEvent.PickWallpaper -> {
                        if (wallpaperOpen) {
                            wallpaperOpen = false
                            val value = wallpaperValue(currentPictures, event.index)
                            if (value == null) {
                                repository.clear(Choices.WallpaperChoice)
                            } else {
                                repository.set(Choices.WallpaperChoice, value)
                                currentOnEffect(SettingsEffect.WallpaperApplied)
                            }
                        }
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
                currentOnEffect(SettingsEffect.SaveFailed)
            }
        }
    }
    val config by repository.config.collectAsState()
    val followsTheme by repository.followsTheme.collectAsState()
    val pick by repository.openOnStartApp.collectAsState()
    val startChoice = config.openOnStartChoice
    val pickedApp by produceState<OpenOnStartApp?>(null, pick, startChoice) {
        val pkg = pick
        if (startChoice && pkg != null) value = sources.openOnStartApps().firstOrNull { it.packageName == pkg } ?: OpenOnStartApp(pkg, pkg)
    }
    val startRow =
        remember(pick, startChoice, pickedApp) {
            when {
                !startChoice -> null
                pick == null -> OptionLabel.Res(R.string.settings_off)
                else -> OptionLabel.Text(pickedApp?.takeIf { it.packageName == pick }?.label.orEmpty())
            }
        }
    val wallpaperChoice by repository.wallpaperChoice.collectAsState()
    val wallpaperRow = remember(pictures, wallpaperChoice) { wallpaperLabel(pictures, wallpaperChoice) }
    val wallpaperPicker =
        remember(wallpaperOpen, pictures, wallpaperChoice) { if (wallpaperOpen) wallpaperPicker(pictures, wallpaperChoice) else null }
    return remember(config, followsTheme, startRow, picker, wallpaperRow, wallpaperPicker) {
        SettingsUiState(
            settingsSections(config),
            followsTheme,
            config.launcherSettingsButton,
            startRow,
            picker,
            wallpaperRow,
            wallpaperPicker,
        )
    }
}
