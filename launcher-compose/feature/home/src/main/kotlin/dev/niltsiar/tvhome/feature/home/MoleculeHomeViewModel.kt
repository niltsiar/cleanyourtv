package dev.niltsiar.tvhome.feature.home

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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale
import kotlin.coroutines.CoroutineContext

/** Hosts [homePresenter] with Molecule: an event Flow in, a StateFlow out. */
public class MoleculeHomeViewModel(
    sources: HomeSources,
    config: TvConfig,
    locale: Locale = Locale.getDefault(),
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    recompositionMode: RecompositionMode = RecompositionMode.ContextClock,
    context: CoroutineContext = AndroidUiDispatcher.Main,
) : ViewModel(coroutineScope) {
    private val events = Channel<HomeEvent>(Channel.UNLIMITED)
    private val eventFlow = events.receiveAsFlow()
    private val effectChannel = Channel<HomeEffect>(Channel.BUFFERED)

    public val effects: Flow<HomeEffect> = effectChannel.receiveAsFlow()

    public val uiState: StateFlow<HomeUiState> =
        viewModelScope.launchMolecule(recompositionMode, context) {
            homePresenter(eventFlow, sources, config, effectChannel::trySend, locale)
        }

    public fun onFocus(key: String) {
        events.trySend(HomeEvent.Focus(key))
    }

    public fun onClick(key: String) {
        events.trySend(HomeEvent.Click(key))
    }

    public fun onOpenSettings() {
        events.trySend(HomeEvent.OpenSettings)
    }

    public fun onOpenNetwork() {
        events.trySend(HomeEvent.OpenNetwork)
    }

    /** The menus and the reorder and edit-dock modes. */
    public val actions: HomeActions =
        HomeActions(
            onTileMenu = { key, held -> events.trySend(HomeEvent.TileMenu(key, held)) },
            onSettingsMenu = { events.trySend(HomeEvent.SettingsMenu) },
            onInputs = { events.trySend(HomeEvent.OpenInputs) },
            onInputPick = { events.trySend(HomeEvent.PickInput(it)) },
            onMenuPick = { events.trySend(HomeEvent.MenuPick(it)) },
            onBack = { events.trySend(HomeEvent.Back) },
            onToggleReorder = { events.trySend(HomeEvent.ToggleReorder) },
            onReorderMove = { events.trySend(HomeEvent.ReorderMove(it)) },
            onReorderOk = { events.trySend(HomeEvent.ReorderOk) },
            onHomePressed = { events.trySend(HomeEvent.HomePressed) },
        )

    /** Home left the screen: a reorder ends and saves, a menu closes. */
    public fun onStopped() {
        events.trySend(HomeEvent.Stopped)
    }
}
