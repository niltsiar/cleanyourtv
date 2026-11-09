package dev.niltsiar.tvhome.feature.settings

import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import dev.niltsiar.tvhome.core.config.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.coroutines.CoroutineContext

/** What the settings screen asks of its state. */
public class SettingsActions(
    /** The option at an index of a row was picked. */
    public val onPick: (SettingsRowId, Int) -> Unit = { _, _ -> },
    public val onFollowTheme: () -> Unit = {},
    public val onOpenPicker: () -> Unit = {},
    public val onClosePicker: () -> Unit = {},
    /** The entry at an index of the picker was picked; 0 is Off. */
    public val onPickStartApp: (Int) -> Unit = {},
    public val onOpenWallpaperPicker: () -> Unit = {},
    /** The entry at an index of the wallpaper picker was picked; 0 is Theme default. */
    public val onPickWallpaper: (Int) -> Unit = {},
)

/** Hosts [settingsPresenter] with Molecule: an event Flow in, a StateFlow out. */
public class MoleculeSettingsViewModel(
    repository: ConfigRepository,
    sources: SettingsSources,
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    recompositionMode: RecompositionMode = RecompositionMode.ContextClock,
    context: CoroutineContext = AndroidUiDispatcher.Main,
) : ViewModel(coroutineScope) {
    private val events = Channel<SettingsEvent>(Channel.UNLIMITED)
    private val eventFlow = events.receiveAsFlow()
    private val effectChannel = Channel<SettingsEffect>(Channel.BUFFERED)

    public val effects: Flow<SettingsEffect> = effectChannel.receiveAsFlow()

    public val uiState: StateFlow<SettingsUiState> =
        viewModelScope.launchMolecule(recompositionMode, context) {
            settingsPresenter(eventFlow, repository, sources, effectChannel::trySend)
        }

    public val actions: SettingsActions =
        SettingsActions(
            onPick = { row, option -> events.trySend(SettingsEvent.Pick(row, option)) },
            onFollowTheme = { events.trySend(SettingsEvent.FollowTheme) },
            onOpenPicker = { events.trySend(SettingsEvent.OpenPicker) },
            onClosePicker = { events.trySend(SettingsEvent.ClosePicker) },
            onPickStartApp = { events.trySend(SettingsEvent.PickStartApp(it)) },
            onOpenWallpaperPicker = { events.trySend(SettingsEvent.OpenWallpaperPicker) },
            onPickWallpaper = { events.trySend(SettingsEvent.PickWallpaper(it)) },
        )
}
