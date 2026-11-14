package dev.niltsiar.tvhome.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.text.Collator
import java.util.Locale

/** How long after a card starts its app the library gives up waiting for it and takes OK again, as Home does. */
internal const val OPENING_MAX_MS = 10_000L

/**
 * The library's state logic for any host that runs a composable: Molecule today. It uses the Compose runtime only. It
 * follows [config]: the library closes when app_library turns off, and the choices that shape it apply to the open one.
 */
@Composable
internal fun libraryPresenter(
    events: Flow<LibraryEvent>,
    sources: LibrarySources,
    config: StateFlow<TvConfig>,
    onEffect: (LibraryEffect) -> Unit,
    locale: Locale,
): LibraryUiState {
    val currentOnEffect by rememberUpdatedState(onEffect)
    val scope = rememberCoroutineScope()
    val current = config.collectAsState()
    val layout = remember(current) { derivedStateOf { current.value.libraryLayout() } }
    val interaction =
        remember(sources, layout, locale) {
            LibraryInteraction(sources, layout, Collator.getInstance(locale), scope) { currentOnEffect(it) }
        }
    val enabled = current.value.appLibrary
    LaunchedEffect(interaction, enabled) { if (!enabled && interaction.open) interaction.handle(LibraryEvent.Close) }
    LaunchedEffect(events, interaction) { events.collect(interaction::handle) }
    LaunchedEffect(interaction.open) {
        if (interaction.open) sources.changes.collect { interaction.reload() }
    }
    val launching = interaction.launching
    LaunchedEffect(interaction, launching?.token) {
        if (launching != null) {
            delay(OPENING_MAX_MS)
            interaction.launchEnded(launching.token)
        }
    }
    return LibraryUiState(
        open = interaction.open,
        shown = interaction.shown,
        cards = interaction.cards,
        focusedKey = interaction.focusedKey,
        hint = interaction.hint,
        menu = interaction.menu,
        focusRequest = interaction.focusRequest,
        order = interaction.order,
        noUsageYet = interaction.noUsageYet,
        launching = launching != null,
    )
}
