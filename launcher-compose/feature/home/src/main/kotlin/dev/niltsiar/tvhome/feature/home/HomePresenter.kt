package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppList
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.util.Locale

private val NoApps = AppList()

/** Home's state logic for any host that runs a composable: Molecule today. It uses the Compose runtime only. */
@Composable
internal fun homePresenter(
    events: Flow<HomeEvent>,
    apps: Flow<AppList>,
    clock: Flow<LocalDateTime>,
    config: TvConfig,
    onEffect: (HomeEffect) -> Unit,
    locale: Locale,
): HomeUiState {
    var focusedKey by remember { mutableStateOf<String?>(null) }
    val appList by apps.collectAsState(NoApps)
    val currentAppList by rememberUpdatedState(appList)
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is HomeEvent.Focus -> {
                    focusedKey = event.key
                }

                is HomeEvent.Click -> {
                    currentAppList.apps.firstOrNull { it.key == event.key }?.let {
                        currentOnEffect(
                            HomeEffect.LaunchApp(it),
                        )
                    }
                }
            }
        }
    }
    val layout = remember(config) { config.homeLayout() }
    val homeApps = remember(appList, layout) { HomeApps(appList, layout) }
    val now by clock.collectAsState(null)
    return homeUiState(homeApps, now, focusedKey, locale)
}
