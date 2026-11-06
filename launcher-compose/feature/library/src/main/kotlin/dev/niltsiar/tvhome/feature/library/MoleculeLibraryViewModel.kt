package dev.niltsiar.tvhome.feature.library

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale
import kotlin.coroutines.CoroutineContext

/** What the library screen asks of its state. */
public class LibraryActions(
    public val onFocus: (String) -> Unit = {},
    public val onClick: (String) -> Unit = {},
    /** A held OK on a card. */
    public val onHold: (String) -> Unit = {},
    public val onPick: (LibraryRow) -> Unit = {},
    public val onBack: () -> Unit = {},
    /** The MENU key: switch to the other order. */
    public val onOrder: () -> Unit = {},
)

/** Hosts [libraryPresenter] with Molecule: an event Flow in, a StateFlow out. */
public class MoleculeLibraryViewModel(
    sources: LibrarySources,
    config: TvConfig,
    locale: Locale,
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    recompositionMode: RecompositionMode = RecompositionMode.ContextClock,
    context: CoroutineContext = AndroidUiDispatcher.Main,
) : ViewModel(coroutineScope) {
    private val events = Channel<LibraryEvent>(Channel.UNLIMITED)
    private val eventFlow = events.receiveAsFlow()
    private val effectChannel = Channel<LibraryEffect>(Channel.BUFFERED)

    public val effects: Flow<LibraryEffect> = effectChannel.receiveAsFlow()

    private val currentLocale = MutableStateFlow(locale)

    public val uiState: StateFlow<LibraryUiState> =
        viewModelScope.launchMolecule(recompositionMode, context) {
            val locale by currentLocale.collectAsState()
            libraryPresenter(eventFlow, sources, config, effectChannel::trySend, locale)
        }

    /** The TV's language changed: the view model outlives the activity that is recreated for it. */
    public fun onLocaleChanged(locale: Locale) {
        currentLocale.value = locale
    }

    public val actions: LibraryActions =
        LibraryActions(
            onFocus = { events.trySend(LibraryEvent.Focus(it)) },
            onClick = { events.trySend(LibraryEvent.Click(it)) },
            onHold = { events.trySend(LibraryEvent.Hold(it)) },
            onPick = { events.trySend(LibraryEvent.Pick(it)) },
            onBack = { events.trySend(LibraryEvent.Back) },
            onOrder = { events.trySend(LibraryEvent.ToggleOrder) },
        )

    /** Open the library over Home. Nothing happens while it is open. */
    public fun onOpen() {
        events.trySend(LibraryEvent.Open)
    }

    /** Home left the screen or the Home key was pressed: close the library. */
    public fun onClose() {
        events.trySend(LibraryEvent.Close)
    }
}
