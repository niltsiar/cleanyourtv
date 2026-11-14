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
import dev.niltsiar.tvhome.core.config.is24Hour
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.OpenedRecord
import dev.niltsiar.tvhome.core.model.Wallpaper
import dev.niltsiar.tvhome.core.model.millisUntilNextDay
import dev.niltsiar.tvhome.core.model.unusedKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val NoNetwork = NetworkState()
private val NoWallpaper = Wallpaper()
private val NoOpened = emptyMap<String, Long>()

/** How long a port must stay connected before its device is offered: CEC flaps between standby and connected. */
internal const val ARRIVAL_HOLD_MS = 2_000L

/** How long the Inputs button offers a device that nobody acted on. */
internal const val OFFER_MS = 20_000L

/** How long Home stays in front after a tile press before the line under the title says the app is opening. */
internal const val OPENING_SAYS_MS = 1_000L

/** How long after a tile press Home gives up waiting for the app and takes presses again. */
internal const val OPENING_MAX_MS = 10_000L

/** How often the habit is looked at again while Home shows. */
internal const val HABIT_REFRESH_MS = 15 * 60 * 1_000L

/** The Inputs button before the TV has said what it lists: hidden, as the reference hides it until it finds something to list. */
private val UnknownInputs = InputsMenu(available = false)

/**
 * Home's state logic for any host that runs a composable: Molecule today. It uses the Compose runtime only. It follows
 * [config] only while Home shows: a choice made on another screen reaches Home when it comes back, not under the
 * person's feet, and all at once.
 */
@Composable
internal fun homePresenter(
    events: Flow<HomeEvent>,
    sources: HomeSources,
    config: StateFlow<TvConfig>,
    onEffect: (HomeEffect) -> Unit,
    locale: Locale,
    datePattern: String,
): HomeUiState {
    val currentOnEffect by rememberUpdatedState(onEffect)
    val applied = remember(config) { mutableStateOf(config.value) }
    val interaction = remember(sources, config) { HomeInteraction(sources, applied) { currentOnEffect(it) } }
    val showing = interaction.showing
    LaunchedEffect(config, showing) { if (showing) config.collect { applied.value = it } }
    val returning = interaction.settingsReturn
    LaunchedEffect(interaction, showing, returning) {
        if (showing && returning != null) {
            applied.value = config.value
            interaction.settingsReturned(returning.reorder)
        }
    }
    val counts = interaction.dockCounts
    LaunchedEffect(interaction, counts) { interaction.dockCountsChanged(counts) }
    val current = applied.value
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
    val launch = interaction.launch
    LaunchedEffect(interaction, launch?.token) {
        if (launch != null) {
            delay(OPENING_SAYS_MS)
            interaction.launchSays(launch.token)
            delay(OPENING_MAX_MS - OPENING_SAYS_MS)
            interaction.launchEnded(launch.token)
        }
    }
    LaunchedEffect(interaction, showing, current.habitHint) {
        if (current.habitHint && showing) {
            while (true) {
                interaction.habitFound(sources.habit())
                delay(HABIT_REFRESH_MS)
            }
        }
    }
    var today by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(interaction, showing, current.unusedFadeDays > 0) {
        if (current.unusedFadeDays > 0 && showing) {
            while (true) {
                val at = sources.timeMillis()
                today = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
                delay(millisUntilNextDay(at))
            }
        }
    }
    val now by sources.clock.collectAsState(null)
    val network by sources.network.collectAsState(NoNetwork)
    val wallpaper by sources.wallpaper.collectAsState(NoWallpaper)
    val lastOpened by sources.lastOpened.collectAsState(NoOpened)
    val libraryCount by sources.libraryCount.collectAsState(null)
    val openedSince by sources.openedSince.collectAsState(null)
    // Read again with each new time, which also follows the TV's 12/24-hour setting after a stop and start of Home.
    val hour24 = remember(now, current.clockFormat) { current.clockFormat.is24Hour(sources.systemHour24) }
    val header =
        remember(hour24, datePattern, current.showGreeting, current.greetingName) {
            HeaderStyle(hour24, datePattern, current.showGreeting, current.greetingName)
        }
    val session = interaction.session
    val shown = interaction.shown
    val faded =
        remember(shown, lastOpened, openedSince, today, current.unusedFadeDays) {
            today?.let { OpenedRecord(lastOpened, openedSince).unusedKeys(shown.grid.map { it.key }, current.unusedFadeDays, it) }.orEmpty()
        }
    return homeUiState(
        shown,
        now,
        interaction.focusedKey,
        locale,
        header,
        network,
        interaction.topBarApps,
        wallpaper,
        cinematic = current.cinematicHome,
        lastOpened = lastOpened,
        menu = interaction.menu,
        reorder = remember(session, shown) { session?.view { key -> shown.all.firstOrNull { it.key == key }?.label } },
        focusRequest = interaction.focusRequest,
        inputs = interaction.inputs ?: UnknownInputs,
        inputsOpen = interaction.inputsOpen,
        inputOffer = interaction.offer,
        libraryCount = libraryCount.takeIf { current.appLibrary },
        habit = interaction.habit,
        faded = faded,
        awaitingApps = interaction.awaitingApps,
        opening = remember(launch) { launch?.let { Opening(it.label, it.says) } },
        config = current,
    )
}
