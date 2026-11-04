package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.niltsiar.tvhome.core.model.AppEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.util.Locale

/** Home's state logic for any host that runs a composable: Molecule today. It uses the Compose runtime only. */
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
