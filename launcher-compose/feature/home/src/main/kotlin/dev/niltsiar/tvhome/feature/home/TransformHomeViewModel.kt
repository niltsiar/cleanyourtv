package dev.niltsiar.tvhome.feature.home

import androidx.appstate.transform.transform
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.niltsiar.tvhome.core.model.AppEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.LocalDateTime
import java.util.Locale

/** Hosts the same Home state with AppState Transform: inputs are Compose state, the output is Compose state. */
public class TransformHomeViewModel(
    apps: Flow<List<AppEntry>>,
    clock: Flow<LocalDateTime>,
    locale: Locale = Locale.getDefault(),
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel(coroutineScope) {
    private var focusedPackage by mutableStateOf<String?>(null)
    private val effectChannel = Channel<HomeEffect>(Channel.BUFFERED)

    public val effects: Flow<HomeEffect> = effectChannel.receiveAsFlow()

    public val uiState: State<HomeUiState> =
        transform(HomeUiState(), viewModelScope, dispatcher) {
            val appList by apps.collectAsState(emptyList())
            val homeApps = remember(appList) { HomeApps(appList) }
            val now by clock.collectAsState(null)
            homeUiState(homeApps, now, focusedPackage, locale)
        }

    public fun onFocus(packageName: String) {
        focusedPackage = packageName
    }

    public fun onClick(packageName: String) {
        effectChannel.trySend(HomeEffect.LaunchApp(packageName))
    }
}
