package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import dev.niltsiar.tvhome.core.model.AppEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.LocalDateTime
import java.util.Locale
import kotlin.coroutines.CoroutineContext

/** Hosts [homePresenter] with Molecule: an event Flow in, a StateFlow out. */
public class MoleculeHomeViewModel(
    apps: Flow<List<AppEntry>>,
    clock: Flow<LocalDateTime>,
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
            homePresenter(eventFlow, apps, clock, effectChannel::trySend, locale)
        }

    public fun onFocus(packageName: String) {
        events.trySend(HomeEvent.Focus(packageName))
    }

    public fun onClick(packageName: String) {
        events.trySend(HomeEvent.Click(packageName))
    }
}

@Composable
internal fun homePresenter(
    events: Flow<HomeEvent>,
    apps: Flow<List<AppEntry>>,
    clock: Flow<LocalDateTime>,
    onEffect: (HomeEffect) -> Unit,
    locale: Locale,
): HomeUiState {
    var focusedPackage by remember { mutableStateOf<String?>(null) }
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is HomeEvent.Focus -> focusedPackage = event.packageName
                is HomeEvent.Click -> currentOnEffect(HomeEffect.LaunchApp(event.packageName))
            }
        }
    }
    val appList by apps.collectAsState(emptyList())
    val homeApps = remember(appList) { HomeApps(appList) }
    val now by clock.collectAsState(null)
    return homeUiState(homeApps, now, focusedPackage, locale)
}
