package dev.niltsiar.tvhome.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class FirstCodePointTest {
    @Test
    fun a_letter_outside_the_basic_plane_is_not_split_in_two() {
        assertEquals("𝐀", "𝐀pple".firstCodePoint())
    }

    @Test
    fun an_ordinary_first_letter_and_an_empty_label_work() {
        assertEquals("N", "Netflix".firstCodePoint())
        assertEquals("", "".firstCodePoint())
    }
}
