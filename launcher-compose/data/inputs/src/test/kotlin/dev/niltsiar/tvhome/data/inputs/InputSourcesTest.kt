package dev.niltsiar.tvhome.data.inputs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputSourcesTest {
    @Test
    fun maps_the_last_input_component() {
        assertEquals(25, mappedSource(listOf("HW7=25"), "service/.Hdmi/HW7"))
    }

    @Test
    fun trims_mapping_whitespace() {
        assertEquals(25, mappedSource(listOf(" HW7 = 25 "), "service/.Hdmi/HW7"))
    }

    @Test
    fun a_dash_hides_an_input() {
        assertEquals(-1, mappedSource(listOf("HW8=-"), "service/.Hdmi/HW8"))
    }

    @Test
    fun an_unknown_or_malformed_input_is_not_mapped() {
        assertNull(mappedSource(listOf("HW5=23", "HW7=nope"), "service/.Hdmi/HW6"))
        assertNull(mappedSource(listOf("HW7=nope"), "service/.Hdmi/HW7"))
        assertNull(mappedSource(emptyList(), "service/.Hdmi/HW7"))
    }

    @Test
    fun an_activity_is_a_component_and_an_action_is_not() {
        assertTrue(isComponent("com.xiaomi.mitv.tvplayer/.ExternalSourceActivity"))
        assertTrue(isComponent("com.xiaomi.mitv.tvplayer/com.xiaomi.mitv.tvplayer.ExternalSourceActivity"))
        assertFalse(isComponent("com.xgimi.action.hdmiPlayer"))
        assertFalse(isComponent("com.xiaomi.mitv.tvplayer.EXTSRC_PLAY"))
        assertFalse(isComponent(""))
        assertFalse(isComponent(null))
        assertFalse(isComponent("/.Activity"))
        assertFalse(isComponent("com.example/"))
        assertFalse(isComponent("a/b/c"))
    }
}
