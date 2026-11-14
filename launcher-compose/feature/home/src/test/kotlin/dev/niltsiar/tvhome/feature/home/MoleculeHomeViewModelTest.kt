package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

val MoleculeHomeViewModelTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    fun CoroutineScope.viewModel() =
        MoleculeHomeViewModel(
            sources(),
            MutableStateFlow(SixColumns),
            Locale.ENGLISH,
            "EEEE d MMMM",
            this,
            RecompositionMode.Immediate,
            EmptyCoroutineContext,
        )

    test("focusing a dock app updates the shelf label through the view model") {
        with(testScope) {
            val viewModel = backgroundScope.viewModel()
            viewModel.uiState.test {
                viewModel.onFocus("pkg.4")
                awaitMatching { it.focusedLabel == "App 4" }.focusedLabel shouldBe "App 4"
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    test("clicking an app emits a launch effect for its package through the view model") {
        with(testScope) {
            val viewModel = backgroundScope.viewModel()
            viewModel.uiState.first { it.dock.isNotEmpty() }
            viewModel.effects.test {
                viewModel.onClick("pkg.2")
                awaitItem() shouldBe HomeEffect.LaunchApp(NineApps[1])
            }
        }
    }

    test("the settings button emits an open settings effect") {
        with(testScope) {
            val viewModel = backgroundScope.viewModel()
            viewModel.uiState.first { it.dock.isNotEmpty() }
            viewModel.effects.test {
                viewModel.onOpenSettings()
                awaitItem() shouldBe HomeEffect.OpenSettings
                viewModel.onOpenNetwork()
                awaitItem() shouldBe HomeEffect.OpenNetwork
                viewModel.onOpenWifiSettings()
                awaitItem() shouldBe HomeEffect.OpenWifiSettings
            }
        }
    }

    test("a language change formats the date in the new language and pattern") {
        with(testScope) {
            val viewModel = backgroundScope.viewModel()
            viewModel.uiState.test {
                awaitMatching { it.date.isNotEmpty() }.date shouldBe "Tuesday 3 November"

                viewModel.onLocaleChanged(Locale.forLanguageTag("es-ES"), "EEEE d 'de' MMMM")

                awaitMatching { it.date.startsWith("martes") }.date shouldBe "martes 3 de noviembre"
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
