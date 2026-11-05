package dev.niltsiar.tvhome.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.flow.Flow
import java.text.Collator
import java.util.Locale

/** The library's state logic for any host that runs a composable: Molecule today. It uses the Compose runtime only. */
@Composable
internal fun libraryPresenter(
    events: Flow<LibraryEvent>,
    sources: LibrarySources,
    config: TvConfig,
    onEffect: (LibraryEffect) -> Unit,
    locale: Locale,
): LibraryUiState {
    val currentOnEffect by rememberUpdatedState(onEffect)
    val scope = rememberCoroutineScope()
    val interaction =
        remember(sources, config, locale) {
            LibraryInteraction(sources, config.libraryLayout(), Collator.getInstance(locale), scope) { currentOnEffect(it) }
        }
    LaunchedEffect(events, interaction) { events.collect(interaction::handle) }
    LaunchedEffect(interaction.open) {
        if (interaction.open) sources.changes.collect { interaction.reload() }
    }
    return LibraryUiState(
        open = interaction.open,
        shown = interaction.shown,
        cards = interaction.cards,
        focusedKey = interaction.focusedKey,
        hint = interaction.hint,
        menu = interaction.menu,
        focusRequest = interaction.focusRequest,
    )
}
