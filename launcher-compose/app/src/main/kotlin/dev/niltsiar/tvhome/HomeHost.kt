package dev.niltsiar.tvhome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.feature.home.HomeEffect
import dev.niltsiar.tvhome.feature.home.HomeScreen
import dev.niltsiar.tvhome.feature.home.MoleculeHomeViewModel
import dev.niltsiar.tvhome.feature.home.homeLayout
import dev.niltsiar.tvhome.feature.home.shelfLayout
import dev.niltsiar.tvhome.feature.home.topBarLayout
import dev.niltsiar.tvhome.feature.home.wallpaperLayout
import java.util.Locale

/** Home state comes from the Molecule-hosted presenter. */
@Composable
internal fun HomeHost(
    graph: AppGraph,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onOpenNativeSettings: () -> Unit,
    onOrderNotSaved: () -> Unit,
    onLibrarySaveFailed: () -> Unit,
    onOpenInput: (InputTarget) -> Unit,
    onNoInputs: () -> Unit,
    glass: GlassState,
    libraryOpen: () -> Boolean,
    onOpenLibrary: () -> Unit,
    homePresses: () -> Int,
    inputsRequests: () -> Int,
    locale: Locale = LocalConfiguration.current.locales[0],
    datePattern: String = stringResource(R.string.date_format),
    viewModel: MoleculeHomeViewModel = viewModel { MoleculeHomeViewModel(graph.homeSources, graph.config, locale, datePattern) },
) {
    SideEffect { viewModel.onLocaleChanged(locale, datePattern) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.LaunchApp -> onLaunchApp(effect.app)
                HomeEffect.OpenSettings -> onOpenSettings()
                HomeEffect.OpenNetwork -> onOpenNetwork()
                is HomeEffect.OpenAppInfo -> onOpenAppInfo(effect.packageName)
                HomeEffect.OpenNativeSettings -> onOpenNativeSettings()
                HomeEffect.OrderNotSaved -> onOrderNotSaved()
                HomeEffect.LibrarySaveFailed -> onLibrarySaveFailed()
                is HomeEffect.OpenInput -> onOpenInput(effect.target)
                HomeEffect.NoInputs -> onNoInputs()
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStopped() }
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val layout = remember(graph) { graph.config.homeLayout() }
    val topBar = remember(graph) { graph.config.topBarLayout() }
    val wallpaperLayout = remember(graph) { graph.config.wallpaperLayout() }
    val shelfLayout = remember(graph) { graph.config.shelfLayout() }
    HomeScreen(
        state = { state.value },
        layout = layout,
        onFocus = viewModel::onFocus,
        onClick = viewModel::onClick,
        homePresses = homePresses,
        topBar = topBar,
        wallpaperLayout = wallpaperLayout,
        shelfLayout = shelfLayout,
        heroArt = graph.homeSources.heroArt,
        onOpenSettings = viewModel::onOpenSettings,
        onOpenNetwork = viewModel::onOpenNetwork,
        actions = viewModel.actions,
        glass = glass,
        libraryOpen = libraryOpen,
        onOpenLibrary = onOpenLibrary,
        inputsRequests = inputsRequests,
    )
}
