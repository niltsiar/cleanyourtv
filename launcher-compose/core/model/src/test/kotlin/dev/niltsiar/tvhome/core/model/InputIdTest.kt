package dev.niltsiar.tvhome.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputIdTest {
    @Test
    fun an_input_value_gives_the_input_id_without_the_prefix_and_trimmed() {
        assertEquals(
            "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5",
            inputIdOf("input:com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"),
        )
        assertEquals("HW5", inputIdOf("input: HW5 "))
    }

    @Test
    fun every_other_value_is_no_input() {
        assertNull(inputIdOf(null))
        assertNull(inputIdOf("FEATURED_LAUNCH"))
        assertNull(inputIdOf("INPUT:HW5"))
        assertNull(inputIdOf("not-input:HW5"))
    }

    @Test
    fun an_empty_input_id_is_no_input() {
        assertNull(inputIdOf("input:"))
        assertNull(inputIdOf("input:   "))
    }
}
