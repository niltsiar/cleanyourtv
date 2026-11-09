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
)

/** Hosts [settingsPresenter] with Molecule: an event Flow in, a StateFlow out. */
public class MoleculeSettingsViewModel(
    repository: ConfigRepository,
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    recompositionMode: RecompositionMode = RecompositionMode.ContextClock,
    context: CoroutineContext = AndroidUiDispatcher.Main,
) : ViewModel(coroutineScope) {
    private val events = Channel<SettingsEvent>(Channel.UNLIMITED)
    private val eventFlow = events.receiveAsFlow()
    private val saveFailures = Channel<Unit>(Channel.BUFFERED)

    /** One item each time the store refused a write. */
    public val saveFailed: Flow<Unit> = saveFailures.receiveAsFlow()

    public val uiState: StateFlow<SettingsUiState> =
        viewModelScope.launchMolecule(recompositionMode, context) {
            settingsPresenter(eventFlow, repository) { saveFailures.trySend(Unit) }
        }

    public val actions: SettingsActions =
        SettingsActions(
            onPick = { row, option -> events.trySend(SettingsEvent.Pick(row, option)) },
            onFollowTheme = { events.trySend(SettingsEvent.FollowTheme) },
        )
}
