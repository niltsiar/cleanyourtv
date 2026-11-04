package dev.niltsiar.tvhome.data.inputs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputFoldTest {
    @Test
    fun a_connected_hdmi_port_stays() {
        assertFalse(inputFolds(hdmi = true, PortState.Connected))
    }

    @Test
    fun an_empty_port_reporting_standby_folds() {
        assertTrue(inputFolds(hdmi = true, PortState.Standby))
    }

    @Test
    fun a_disconnected_port_folds() {
        assertTrue(inputFolds(hdmi = true, PortState.Disconnected))
    }

    @Test
    fun an_hdmi_row_with_no_state_stays() {
        assertFalse(inputFolds(hdmi = true, PortState.None))
    }

    @Test
    fun an_av_jack_always_folds() {
        assertTrue(inputFolds(hdmi = false, PortState.None))
        assertTrue(inputFolds(hdmi = false, PortState.Connected))
    }
}
