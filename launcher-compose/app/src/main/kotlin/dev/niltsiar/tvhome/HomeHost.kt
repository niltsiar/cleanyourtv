package dev.niltsiar.tvhome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.feature.home.HomeEffect
import dev.niltsiar.tvhome.feature.home.HomeScreen
import dev.niltsiar.tvhome.feature.home.MoleculeHomeViewModel
import dev.niltsiar.tvhome.feature.home.dockLook
import dev.niltsiar.tvhome.feature.home.homeLayout
import dev.niltsiar.tvhome.feature.home.shelfLayout
import dev.niltsiar.tvhome.feature.home.topBarLayout
import dev.niltsiar.tvhome.feature.home.wallpaperLayout
import kotlinx.coroutines.flow.filterNotNull
import java.util.Locale

/** The Launcher settings page closed. Each close is its own [token], so the second of two equal results still reaches Home. */
internal data class SettingsClosing(
    val reorder: Boolean,
    val token: Int,
)

/** Home state comes from the Molecule-hosted presenter. [overlays] go in Home's overlay host. */
@Composable
internal fun HomeHost(
    graph: AppGraph,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onOpenNativeSettings: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onOpenPowerMenu: () -> Unit,
    onOrderNotSaved: () -> Unit,
    onLibrarySaveFailed: () -> Unit,
    onOpenInput: (InputTarget) -> Unit,
    onNoInputs: () -> Unit,
    glass: GlassState,
    libraryOpen: () -> Boolean,
    onOpenLibrary: () -> Unit,
    homePresses: () -> Int,
    inputsRequests: () -> Int,
    settingsClosing: () -> SettingsClosing?,
    overlays: @Composable () -> Unit,
    locale: Locale = LocalConfiguration.current.locales[0],
    datePattern: String = stringResource(R.string.date_format),
    viewModel: MoleculeHomeViewModel =
        viewModel { MoleculeHomeViewModel(graph.homeSources, graph.configRepository.config, locale, datePattern) },
) {
    SideEffect { viewModel.onLocaleChanged(locale, datePattern) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.LaunchApp -> onLaunchApp(effect.app)
                HomeEffect.OpenSettings -> onOpenSettings()
                HomeEffect.OpenNetwork -> onOpenNetwork()
                HomeEffect.OpenWifiSettings -> onOpenWifiSettings()
                is HomeEffect.OpenAppInfo -> onOpenAppInfo(effect.packageName)
                HomeEffect.OpenNativeSettings -> onOpenNativeSettings()
                HomeEffect.OpenLauncherSettings -> onOpenLauncherSettings()
                HomeEffect.OpenPowerMenu -> onOpenPowerMenu()
                HomeEffect.OrderNotSaved -> onOrderNotSaved()
                HomeEffect.LibrarySaveFailed -> onLibrarySaveFailed()
                is HomeEffect.OpenInput -> onOpenInput(effect.target)
                HomeEffect.NoInputs -> onNoInputs()
            }
        }
    }
    LaunchedEffect(viewModel) {
        snapshotFlow(settingsClosing).filterNotNull().collect { viewModel.onSettingsClosed(it.reorder) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStarted() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStopped() }
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val config = remember(state) { derivedStateOf { state.value.config } }
    val layout by config.derived(TvConfig::homeLayout)
    val topBar by config.derived(TvConfig::topBarLayout)
    val wallpaperLayout by config.derived(TvConfig::wallpaperLayout)
    val shelfLayout by config.derived(TvConfig::shelfLayout)
    val dockLook by config.derived(TvConfig::dockLook)
    HomeScreen(
        state = { state.value },
        layout = layout,
        onFocus = viewModel::onFocus,
        onClick = viewModel::onClick,
        homePresses = homePresses,
        topBar = topBar,
        wallpaperLayout = wallpaperLayout,
        shelfLayout = shelfLayout,
        dockLook = dockLook,
        heroArt = graph.homeSources.heroArt,
        onOpenSettings = viewModel::onOpenSettings,
        onOpenNetwork = viewModel::onOpenNetwork,
        onOpenWifiSettings = viewModel::onOpenWifiSettings,
        actions = viewModel.actions,
        glass = glass,
        libraryOpen = libraryOpen,
        onOpenLibrary = onOpenLibrary,
        inputsRequests = inputsRequests,
        overlays = overlays,
    )
}
