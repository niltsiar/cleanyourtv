package dev.niltsiar.tvhome.data.inputs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputNamesTest {
    @Test
    fun philips_type_becomes_an_emoji() {
        assertEquals("🎮 Xbox", tidyInputName("Xbox&GAME_CONSOLE"))
        assertEquals("🎮 Switch", tidyInputName("Switch &GAME_CONSOLE"))
        assertEquals("💻 Ordinateur", tidyInputName("Ordinateur&COMPUTER"))
    }

    @Test
    fun an_unknown_type_leaves_the_name_alone() {
        assertEquals("Box", tidyInputName("Box&SOMETHING_NEW"))
    }

    @Test
    fun a_type_with_no_name_is_just_its_emoji() {
        assertEquals("🎮", tidyInputName("&GAME_CONSOLE"))
    }

    @Test
    fun the_text_null_and_blanks_are_no_name() {
        assertNull(tidyInputName("null"))
        assertNull(tidyInputName("  "))
        assertNull(tidyInputName(null))
    }

    @Test
    fun other_names_are_kept() {
        assertEquals("MacBook Pro", tidyInputName("MacBook Pro"))
        assertEquals("Tom & Jerry", tidyInputName("Tom & Jerry"))
        assertEquals("AT&T", tidyInputName("AT&T"))
        assertEquals("R&D", tidyInputName("R&D"))
    }
}
