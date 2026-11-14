package dev.niltsiar.tvhome.feature.library

import app.cash.molecule.RecompositionMode
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

val MoleculeLibraryViewModelTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    test("a language change sorts the next opening by the new language") {
        with(testScope) {
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
                awaitMatching { it.shown }.cards.map { it.app.label } shouldBe listOf("Äpple", "Zeta")

                viewModel.onLocaleChanged(Locale.forLanguageTag("sv-SE"))
                awaitMatching { !it.open }
                viewModel.onOpen()

                awaitMatching { it.shown }.cards.map { it.app.label } shouldBe listOf("Zeta", "Äpple")
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
