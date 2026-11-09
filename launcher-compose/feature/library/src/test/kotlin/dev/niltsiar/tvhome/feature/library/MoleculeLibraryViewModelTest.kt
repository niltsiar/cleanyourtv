package dev.niltsiar.tvhome.feature.library

import app.cash.molecule.RecompositionMode
import app.cash.turbine.test
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

class MoleculeLibraryViewModelTest {
    @Test
    fun a_language_change_sorts_the_next_opening_by_the_new_language() =
        runTest {
            val library = FakeLibrary(listOf(libraryApp("Zeta"), libraryApp("Äpple")))
            val viewModel =
                MoleculeLibraryViewModel(
                    library.sources,
                    MutableStateFlow(TvConfig(appLibrary = true)),
                    Locale.ENGLISH,
                    backgroundScope,
                    RecompositionMode.Immediate,
                    EmptyCoroutineContext,
                )
            viewModel.uiState.test {
                viewModel.onOpen()
                assertEquals(listOf("Äpple", "Zeta"), awaitMatching { it.shown }.cards.map { it.app.label })

                viewModel.onLocaleChanged(Locale.forLanguageTag("sv-SE"))
                awaitMatching { !it.open }
                viewModel.onOpen()

                assertEquals(listOf("Zeta", "Äpple"), awaitMatching { it.shown }.cards.map { it.app.label })
                cancelAndIgnoreRemainingEvents()
            }
        }
}
