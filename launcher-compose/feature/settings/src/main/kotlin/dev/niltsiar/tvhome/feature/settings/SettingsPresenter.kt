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
import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import dev.niltsiar.tvhome.core.model.WallpaperPicture
import kotlinx.coroutines.flow.Flow
import java.io.IOException

/**
 * The settings screen's state logic: the rows are the repository's config read through [settingsGroups], nothing is
 * kept apart from it, and a pick or Follow theme goes to the repository, which answers through the same flow.
 * Only the open pickers, the theme's pictures, the picked app's label and whether Home holds READ_TV_LISTINGS are kept
 * here. [onEffect] gets [SettingsEffect.SaveFailed] when the store refuses a write, [SettingsEffect.WallpaperApplied] after a
 * picture or None was stored as the wallpaper, [SettingsEffect.AskShelfAccess] when App shelf is turned on or its sources
 * picker opened without that permission, and [SettingsEffect.NoShelfSources] when that picker would list nothing.
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
    var themeDefault by remember { mutableStateOf<WallpaperPicture?>(null) }
    var shelfAccess by remember { mutableStateOf(sources.shelfAccess()) }
    var shelfEntries by remember { mutableStateOf<List<ShelfSourceEntry>?>(null) }
    val pictures by produceState(emptyList<WallpaperPicture>(), sources) { value = sources.wallpaperPictures() }
    val currentPictures by rememberUpdatedState(pictures)
    LaunchedEffect(events, repository, sources) {
        events.collect { event ->
            try {
                when (event) {
                    is SettingsEvent.Pick -> {
                        rowOf(event.row).pick(repository, event.option)
                        if (turnsShelfOn(event.row, event.option) && !shelfAccess) currentOnEffect(SettingsEffect.AskShelfAccess)
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
                        shelfEntries = null
                    }

                    SettingsEvent.OpenWallpaperPicker -> {
                        if (currentPictures.isNotEmpty()) themeDefault = sources.themeDefaultPicture()
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

                    SettingsEvent.OpenShelfPicker -> {
                        if (!shelfAccess) {
                            currentOnEffect(SettingsEffect.AskShelfAccess)
                        } else {
                            val entries = shelfEntries(sources.shelfPublishers())
                            if (entries.isEmpty()) currentOnEffect(SettingsEffect.NoShelfSources) else shelfEntries = entries
                        }
                    }

                    is SettingsEvent.ToggleShelfSource -> {
                        shelfEntries?.getOrNull(event.index)?.let { repository.toggleShelfSource(it.source) }
                    }

                    SettingsEvent.ShelfAccessAnswered -> {
                        shelfAccess = sources.shelfAccess()
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
        remember(wallpaperOpen, pictures, wallpaperChoice, themeDefault) {
            if (wallpaperOpen) wallpaperPicker(pictures, wallpaperChoice, themeDefault) else null
        }
    val shelfRow = remember(config, shelfAccess) { shelfSourcesLabel(config, shelfAccess) }
    val ticked = config.appShelfSources
    val shelfPicker = remember(shelfEntries, ticked) { shelfEntries?.let { ShelfPicker(it, ticked.toSet()) } }
    return remember(config, followsTheme, startRow, picker, wallpaperRow, wallpaperPicker, shelfRow, shelfPicker) {
        SettingsUiState(
            settingsSections(config),
            followsTheme,
            config.launcherSettingsButton,
            startRow,
            picker,
            wallpaperRow,
            wallpaperPicker,
            shelfRow,
            shelfPicker,
            Color(config.backgroundColor),
        )
    }
}
