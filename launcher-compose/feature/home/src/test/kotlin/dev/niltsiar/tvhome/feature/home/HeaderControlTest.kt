package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which top-bar control the band over the grid names. */
class HeaderControlTest {
    private val input =
        HomeInput(
            mutableStateOf(HomeMenuActions()),
            carried = mutableStateOf(null),
            dimmed = mutableStateOf(emptySet()),
            reordering = mutableStateOf(false),
        )

    private fun state(
        inputsOpen: Boolean = false,
        menu: HomeMenu? = null,
    ) = HomeUiState(inputsOpen = inputsOpen, menu = menu)

    @Test
    fun the_band_names_the_button_that_has_focus() {
        input.topBarControl = BarControl.Network
        input.topBarFocused = true

        assertEquals(BarControl.Network, input.headerControl(state()))
    }

    @Test
    fun no_control_is_named_while_focus_is_not_on_the_top_bar() {
        input.topBarControl = BarControl.Network

        assertEquals(null, input.headerControl(state()))
        assertEquals(null, input.headerControl(state(menu = HomeMenu("pkg.1", listOf(MenuRow.Move)))))
    }

    @Test
    fun the_inputs_list_is_named_as_the_inputs_control_whatever_button_had_focus_last() {
        input.topBarControl = BarControl.Network

        assertEquals(BarControl.Inputs, input.headerControl(state(inputsOpen = true)))
    }

    @Test
    fun a_menu_with_no_tile_is_named_as_the_settings_control_whatever_button_had_focus_last() {
        input.topBarControl = BarControl.Inputs

        assertEquals(BarControl.Settings, input.headerControl(state(menu = HomeMenu(null, listOf(MenuRow.Move)))))
    }
}
