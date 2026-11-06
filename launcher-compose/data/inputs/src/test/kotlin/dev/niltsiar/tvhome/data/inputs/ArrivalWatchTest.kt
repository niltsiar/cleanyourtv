package dev.niltsiar.tvhome.data.inputs

import dev.niltsiar.tvhome.core.model.InputArrival
import dev.niltsiar.tvhome.core.model.InputTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun port(
    n: Int,
    state: PortState,
    label: String = "HDMI $n",
) = TvInput(
    "tv/.Hdmi/HW$n",
    null,
    PortType.Hdmi,
    system = true,
    passthrough = true,
    hidden = false,
    label = label,
    customLabel = null,
    state = state,
)

private fun device(
    n: Int,
    state: PortState,
    label: String = "PlayStation 5",
) = TvInput(
    "tv/.Cec/HDMI$n",
    "tv/.Hdmi/HW$n",
    PortType.Hdmi,
    system = true,
    passthrough = true,
    hidden = false,
    label = label,
    customLabel = null,
    state = state,
)

/** What Home is told about a device switched on while it shows, read from TvInputManager snapshots. */
class ArrivalWatchTest {
    private var tokens = 0
    private val watch = ArrivalWatch { ++tokens }

    private fun look(
        vararg inputs: TvInput,
        settings: InputsSettings = InputsSettings(),
    ): InputArrival? = watch.update(portEntries(inputs.toList(), settings))

    @Test
    fun a_port_that_goes_from_no_device_to_connected_is_an_arrival_named_by_its_cec_device_and_port() {
        look(port(1, PortState.Connected), port(2, PortState.Disconnected), port(3, PortState.Disconnected))

        val arrival =
            look(
                port(1, PortState.Connected),
                port(2, PortState.Connected),
                device(2, PortState.Connected),
                port(3, PortState.Disconnected),
            )

        assertEquals(InputArrival("PlayStation 5 · HDMI 2", InputTarget.TvInput("tv/.Cec/HDMI2"), token = 1), arrival)
    }

    @Test
    fun a_port_without_a_cec_device_is_named_by_the_port_alone() {
        look(port(1, PortState.Disconnected), port(2, PortState.Disconnected))

        val arrival = look(port(1, PortState.Connected), port(2, PortState.Disconnected))

        assertEquals(InputArrival("HDMI 1", InputTarget.TvInput("tv/.Hdmi/HW1"), token = 1), arrival)
    }

    @Test
    fun a_cec_device_that_wakes_from_standby_is_an_arrival() {
        look(port(2, PortState.Connected), device(2, PortState.Standby))

        val arrival = look(port(2, PortState.Connected), device(2, PortState.Connected))

        assertEquals("PlayStation 5 · HDMI 2", arrival?.label)
    }

    @Test
    fun a_port_that_was_connected_when_home_came_to_the_front_is_not_an_arrival() {
        val first = look(port(1, PortState.Connected), port(2, PortState.Disconnected))
        val second = look(port(1, PortState.Connected), port(2, PortState.Disconnected), port(3, PortState.Disconnected))

        assertNull(first)
        assertNull(second)
    }

    @Test
    fun the_arrival_stays_while_its_port_stays_connected_and_ends_when_it_leaves() {
        look(port(2, PortState.Disconnected))
        look(port(2, PortState.Connected))

        assertEquals(1, look(port(2, PortState.Connected), device(2, PortState.Connected))?.token)
        assertNull(look(port(2, PortState.Disconnected)))
    }

    @Test
    fun a_port_that_flaps_back_to_connected_is_a_new_arrival() {
        look(port(2, PortState.Disconnected))
        val first = look(port(2, PortState.Connected))
        look(port(2, PortState.Standby))

        val second = look(port(2, PortState.Connected))

        assertNotEquals(first?.token, second?.token)
    }

    @Test
    fun a_name_that_arrives_late_updates_the_arrival_and_keeps_its_token() {
        look(port(2, PortState.Disconnected))
        val bare = look(port(2, PortState.Connected))

        val named = look(port(2, PortState.Connected), device(2, PortState.Connected))

        assertEquals("HDMI 2", bare?.label)
        assertEquals(InputArrival("PlayStation 5 · HDMI 2", InputTarget.TvInput("tv/.Cec/HDMI2"), bare?.token ?: -1), named)
    }

    @Test
    fun a_device_that_goes_while_its_port_still_reports_connected_is_not_an_arrival() {
        look(port(2, PortState.Connected), device(2, PortState.Connected))

        val arrival = look(port(2, PortState.Connected))

        assertNull(arrival)
    }

    @Test
    fun the_latest_arrival_replaces_the_one_before() {
        look(port(1, PortState.Disconnected), port(2, PortState.Disconnected), port(3, PortState.Disconnected))
        look(port(1, PortState.Connected), port(2, PortState.Disconnected), port(3, PortState.Disconnected))

        val second = look(port(1, PortState.Connected), port(2, PortState.Connected), port(3, PortState.Disconnected))

        assertEquals(InputArrival("HDMI 2", InputTarget.TvInput("tv/.Hdmi/HW2"), token = 2), second)
    }

    @Test
    fun a_tv_that_reports_every_port_connected_cannot_tell_an_arrival() {
        look(port(1, PortState.Connected), port(2, PortState.Connected))

        val arrival = look(port(1, PortState.Connected), port(2, PortState.Connected), device(2, PortState.Connected))

        assertNull(arrival)
    }

    @Test
    fun with_inputs_cec_state_only_a_ports_own_state_is_ignored_and_the_device_counts() {
        val settings = InputsSettings(cecStateOnly = true)
        look(port(2, PortState.Disconnected), device(2, PortState.Standby), settings = settings)

        assertNull(look(port(2, PortState.Connected), device(2, PortState.Standby), settings = settings))
        assertEquals(
            "PlayStation 5 · HDMI 2",
            look(port(2, PortState.Connected), device(2, PortState.Connected), settings = settings)?.label,
        )
    }
}
