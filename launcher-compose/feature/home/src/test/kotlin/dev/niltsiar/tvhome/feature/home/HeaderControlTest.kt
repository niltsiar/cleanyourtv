package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.mutableStateOf
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe

/** Which top-bar control the band over the grid names. */
val HeaderControlTest by testSuite {
    fun state(
        inputsOpen: Boolean = false,
        menu: HomeMenu? = null,
    ) = HomeUiState(inputsOpen = inputsOpen, menu = menu)

    testFixture {
        HomeInput(
            mutableStateOf(HomeMenuActions()),
            carried = mutableStateOf(null),
            dimmed = mutableStateOf(emptySet()),
            reordering = mutableStateOf(false),
        )
    } asParameterForEach {
        test("the band names the button that has focus") { input ->
            input.topBarControl = BarControl.Network
            input.topBarFocused = true

            input.headerControl(state()) shouldBe BarControl.Network
        }

        test("no control is named while focus is not on the top bar") { input ->
            input.topBarControl = BarControl.Network

            input.headerControl(state()) shouldBe null
            input.headerControl(state(menu = HomeMenu("pkg.1", listOf(MenuRow.Move)))) shouldBe null
        }

        test("the inputs list is named as the inputs control whatever button had focus last") { input ->
            input.topBarControl = BarControl.Network

            input.headerControl(state(inputsOpen = true)) shouldBe BarControl.Inputs
        }

        test("a menu with no tile is named as the settings control whatever button had focus last") { input ->
            input.topBarControl = BarControl.Inputs

            input.headerControl(state(menu = HomeMenu(null, listOf(MenuRow.Move)))) shouldBe BarControl.Settings
        }
    }
}
