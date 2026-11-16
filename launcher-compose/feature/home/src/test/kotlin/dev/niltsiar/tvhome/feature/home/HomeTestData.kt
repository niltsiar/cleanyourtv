package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import app.cash.molecule.RecompositionMode
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputTile
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.NowPlayingItem
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfPoster
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDateTime
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

internal val NineApps = (1..9).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

internal val Evening: LocalDateTime = LocalDateTime.of(2026, 11, 3, 20, 15)

internal val SixColumns = TvConfig(columns = 6)

internal fun homeApps(
    apps: List<AppEntry> = NineApps,
    docked: Int = 0,
    config: TvConfig = SixColumns,
) = HomeApps(AppList(apps, docked), config.homeLayout())

internal fun sources(
    apps: Flow<AppList> = flowOf(AppList(NineApps)),
    clock: Flow<LocalDateTime?> = flowOf(Evening),
    network: Flow<NetworkState> = flowOf(NetworkState()),
    topBarApps: Flow<List<AppEntry>> = flowOf(emptyList()),
    wallpaper: Flow<Wallpaper> = flowOf(Wallpaper()),
    systemHour24: () -> Boolean = { true },
    lastOpened: Flow<Map<String, Long>> = flowOf(emptyMap()),
    hasAppInfo: (String) -> Boolean = { true },
    hasNativeSettings: () -> Boolean = { false },
    powerMenuAvailable: () -> Boolean = { false },
    saveOrder: suspend (List<String>, Int) -> Boolean = { _, _ -> true },
    removeFromHome: suspend (String) -> Boolean = { true },
    inputs: Flow<InputsMenu> = emptyFlow(),
    focusMemory: FocusMemory = FocusMemory(),
    libraryCount: Flow<Int> = emptyFlow(),
    habit: suspend () -> String? = { null },
    openedSince: Flow<Long?> = flowOf(null),
    timeMillis: () -> Long = System::currentTimeMillis,
    shelfItems: suspend (String) -> List<ShelfItem> = { emptyList() },
    shelfPoster: suspend (String, Int, Int) -> ShelfPoster? = { _, _, _ -> null },
    nowPlaying: (String) -> Flow<NowPlayingItem?> = { flowOf(null) },
    unpinInput: suspend (String) -> Boolean = { true },
) = HomeSources(
    apps,
    clock,
    network,
    topBarApps,
    wallpaper,
    systemHour24 = systemHour24,
    lastOpened = lastOpened,
    hasAppInfo = hasAppInfo,
    hasNativeSettings = hasNativeSettings,
    powerMenuAvailable = powerMenuAvailable,
    saveOrder = saveOrder,
    removeFromHome = removeFromHome,
    inputs = inputs,
    focusMemory = focusMemory,
    libraryCount = libraryCount,
    habit = habit,
    openedSince = openedSince,
    timeMillis = timeMillis,
    shelfItems = shelfItems,
    shelfPoster = shelfPoster,
    nowPlaying = nowPlaying,
    unpinInput = unpinInput,
)

internal fun port(
    n: Int,
    connection: InputConnection = InputConnection.Unknown,
    folded: Boolean = false,
) = InputRow.Port("HDMI $n", InputIcon.Hdmi, connection, InputTarget.TvInput("tv/.Hdmi/HW$n"), folded)

/** Four HDMI ports, the second one with a CEC device on it, and Live TV. */
internal val FourPorts =
    InputsMenu(
        available = true,
        rows =
            listOf(
                port(1, InputConnection.Connected),
                InputRow.Port("Apple TV \u00B7 HDMI 2", InputIcon.Hdmi, InputConnection.Connected, InputTarget.TvInput("tv/.Cec/HDMI2")),
                port(3, InputConnection.Disconnected),
                port(4),
                InputRow.Separator(),
                InputRow.LiveTv,
            ),
    )

/** The input id a PlayStation on HDMI 2 was pinned under, and that port's id. */
internal val PlayStationId: String = "tv/.Cec/HDMI2004"
internal val Hdmi2Id: String = "tv/.Hdmi/HW2"

/** A tile pinned from the PlayStation's Inputs row, under the name that row read then. */
internal val PlayStationTile =
    AppEntry(
        key = "input:$PlayStationId",
        packageName = "",
        label = "PlayStation 5 \u00B7 HDMI 2",
        input = InputTile(PlayStationId, Hdmi2Id, InputIcon.Hdmi),
    )

/**
 * HDMI 1, then on HDMI 2 the PlayStation in [connection] as a CEC device named [label], or the bare port without [label];
 * nothing on HDMI 2 unless [listed].
 */
internal fun playStationInputs(
    connection: InputConnection = InputConnection.Connected,
    label: String? = "PlayStation 5 \u00B7 HDMI 2",
    listed: Boolean = true,
): InputsMenu {
    val hdmi2 =
        if (label != null) {
            InputRow.Port(label, InputIcon.Hdmi, connection, InputTarget.TvInput(PlayStationId), port = Hdmi2Id)
        } else {
            InputRow.Port("HDMI 2", InputIcon.Hdmi, connection, InputTarget.TvInput(Hdmi2Id), port = Hdmi2Id)
        }
    val hdmi1 = InputRow.Port("HDMI 1", InputIcon.Hdmi, InputConnection.Unknown, InputTarget.TvInput("tv/.Hdmi/HW1"), port = "tv/.Hdmi/HW1")
    return InputsMenu(available = true, rows = listOfNotNull(hdmi1, hdmi2.takeIf { listed }, InputRow.Separator(), InputRow.LiveTv))
}

/** [homePresenter] over a config that never changes. */
@Composable
internal fun homePresenter(
    events: Flow<HomeEvent>,
    sources: HomeSources,
    config: TvConfig,
    onEffect: (HomeEffect) -> Unit,
    locale: Locale,
    datePattern: String,
): HomeUiState = homePresenter(events, sources, remember(config) { MutableStateFlow(config) }, onEffect, locale, datePattern)

/** The real view model on the composition's scope over a config that never changes, its effects collected into [effects]. */
@Composable
internal fun rememberHomeViewModel(
    sources: HomeSources,
    config: TvConfig,
    effects: MutableList<HomeEffect>,
): MoleculeHomeViewModel = rememberHomeViewModel(sources, remember(config) { MutableStateFlow(config) }, effects)

/** The real view model on the composition's scope, its effects collected into [effects]. */
@Composable
internal fun rememberHomeViewModel(
    sources: HomeSources,
    config: StateFlow<TvConfig>,
    effects: MutableList<HomeEffect>,
): MoleculeHomeViewModel {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            MoleculeHomeViewModel(sources, config, Locale.ENGLISH, "EEEE d MMMM", scope, RecompositionMode.Immediate, EmptyCoroutineContext)
        }
    LaunchedEffect(viewModel) { viewModel.effects.collect { effects += it } }
    return viewModel
}

/** The style the text of this node was laid out with. */
internal fun SemanticsNodeInteraction.textStyle(): TextStyle {
    val layouts = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode()
        .config
        .getOrNull(SemanticsActions.GetTextLayoutResult)
        ?.action
        ?.invoke(layouts)
    return layouts.single().layoutInput.style
}
