package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.config.is24Hour
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.util.Locale

private val NoNetwork = NetworkState()
private val NoWallpaper = Wallpaper()
private val NoOpened = emptyMap<String, Long>()

/** How long a port must stay connected before its device is offered: CEC flaps between standby and connected. */
internal const val ARRIVAL_HOLD_MS = 2_000L

/** How long the Inputs button offers a device that nobody acted on. */
internal const val OFFER_MS = 20_000L

/** How often the habit is looked at again while Home shows. */
internal const val HABIT_REFRESH_MS = 15 * 60 * 1_000L

/** The Inputs button before the TV has said what it lists: hidden, as the reference hides it until it finds something to list. */
private val UnknownInputs = InputsMenu(available = false)

/** Home's state logic for any host that runs a composable: Molecule today. It uses the Compose runtime only. */
@Composable
internal fun homePresenter(
    events: Flow<HomeEvent>,
    sources: HomeSources,
    config: TvConfig,
    onEffect: (HomeEffect) -> Unit,
    locale: Locale,
    datePattern: String,
): HomeUiState {
    val currentOnEffect by rememberUpdatedState(onEffect)
    val interaction = remember(sources, config) { HomeInteraction(sources, config) { currentOnEffect(it) } }
    LaunchedEffect(interaction) { sources.apps.collect { interaction.listLoaded(it) } }
    LaunchedEffect(interaction) { sources.topBarApps.collect(interaction::topBarAppsLoaded) }
    LaunchedEffect(interaction) { sources.inputs.collect(interaction::inputsLoaded) }
    LaunchedEffect(events) { events.collect(interaction::handle) }
    val arriving = interaction.arriving
    LaunchedEffect(interaction, arriving) {
        if (arriving != null) {
            delay(ARRIVAL_HOLD_MS)
            interaction.arrivalHeld(arriving)
        }
    }
    val offered = interaction.offer?.token
    LaunchedEffect(interaction, offered) {
        if (offered != null) {
            delay(OFFER_MS)
            interaction.offerEnded(offered)
        }
    }
    val showing = interaction.showing
    LaunchedEffect(interaction, showing) {
        if (config.habitHint && showing) {
            while (true) {
                interaction.habitFound(sources.habit())
                delay(HABIT_REFRESH_MS)
            }
        }
    }
    val now by sources.clock.collectAsState(null)
    val network by sources.network.collectAsState(NoNetwork)
    val wallpaper by sources.wallpaper.collectAsState(NoWallpaper)
    val lastOpened by sources.lastOpened.collectAsState(NoOpened)
    val libraryCount by sources.libraryCount.collectAsState(null)
    // Read again with each new time, which also follows the TV's 12/24-hour setting after a stop and start of Home.
    val hour24 = remember(now, config.clockFormat) { config.clockFormat.is24Hour(sources.systemHour24) }
    val header =
        remember(hour24, config, datePattern) {
            HeaderStyle(hour24, datePattern, config.showGreeting, config.greetingName)
        }
    val session = interaction.session
    return homeUiState(
        interaction.shown,
        now,
        interaction.focusedKey,
        locale,
        header,
        network,
        interaction.topBarApps,
        wallpaper,
        cinematic = config.cinematicHome,
        lastOpened = lastOpened,
        menu = interaction.menu,
        reorder = remember(session) { session?.view() },
        focusRequest = interaction.focusRequest,
        inputs = interaction.inputs ?: UnknownInputs,
        inputsOpen = interaction.inputsOpen,
        inputOffer = interaction.offer,
        libraryCount = libraryCount,
        habit = interaction.habit,
    )
}
