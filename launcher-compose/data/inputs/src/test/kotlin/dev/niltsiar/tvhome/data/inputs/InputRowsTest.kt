package dev.niltsiar.tvhome.data.inputs

import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.visible
import org.junit.Assert.assertEquals
import org.junit.Test

private fun hdmi(
    n: Int,
    state: PortState = PortState.Disconnected,
    label: String? = "HDMI $n",
    custom: String? = null,
    parent: String? = null,
    id: String = "tv/.Hdmi/HW$n",
    hidden: Boolean = false,
) = TvInput(
    id,
    parent,
    PortType.Hdmi,
    system = true,
    passthrough = true,
    hidden = hidden,
    label = label,
    customLabel = custom,
    state = state,
)

private fun av(
    n: Int = 1,
    label: String? = "AV $n",
) = TvInput(
    "tv/.Av/AV$n",
    null,
    PortType.Other,
    system = true,
    passthrough = true,
    hidden = false,
    label = label,
    customLabel = null,
    state = PortState.None,
)

private fun tuner(
    id: String,
    system: Boolean = true,
) = TvInput(
    id,
    null,
    PortType.Tuner,
    system,
    passthrough = false,
    hidden = false,
    label = "Tuner",
    customLabel = null,
    state = PortState.None,
)

private fun rows(
    vararg inputs: TvInput,
    settings: InputsSettings = InputsSettings(),
) = portRows(inputs.toList(), settings)

private fun InputRow.label() = (this as InputRow.Port).label

private fun InputRow.connection() = (this as InputRow.Port).connection

class InputRowsTest {
    @Test
    fun hdmi_ports_come_first_in_label_order_and_a_line_separates_the_av_jacks() {
        val out = rows(av(), hdmi(2), hdmi(1))

        assertEquals(listOf("HDMI 1", "HDMI 2"), out.take(2).map { it.label() })
        assertEquals(InputRow.Separator(), out[2])
        assertEquals("AV 1", out[3].label())
        assertEquals(InputIcon.Av, (out[3] as InputRow.Port).icon)
        assertEquals(4, out.size)
    }

    @Test
    fun a_label_without_hdmi_gets_the_prefix() {
        assertEquals("HDMI Game", rows(hdmi(1, label = "Game")).single().label())
        assertEquals("hdmi 1", rows(hdmi(1, label = "hdmi 1")).single().label())
    }

    @Test
    fun inputs_hidden_in_the_tv_settings_or_mapped_to_a_dash_are_left_out() {
        val out = rows(hdmi(1), hdmi(2, hidden = true), hdmi(3), settings = InputsSettings(inputSources = listOf("HW3=-")))

        assertEquals(listOf("HDMI 1"), out.map { it.label() })
    }

    @Test
    fun a_cec_device_takes_its_ports_row_and_names_itself_before_the_port() {
        val port = hdmi(1, state = PortState.Connected)
        val device = hdmi(1, id = "tv/.Cec/HDMI1", label = "Apple TV", parent = port.id, state = PortState.Connected)

        val out = rows(port, device, hdmi(2))

        assertEquals(listOf("Apple TV · HDMI 1", "HDMI 2"), out.map { it.label() })
        assertEquals(InputTarget.TvInput("tv/.Cec/HDMI1"), (out[0] as InputRow.Port).target)
    }

    @Test
    fun a_name_given_in_settings_goes_before_the_port_and_philips_types_become_emoji() {
        val out = rows(hdmi(1, custom = "MacBook Pro"), hdmi(2, custom = "Xbox&GAME_CONSOLE"), hdmi(3, custom = "null"))

        assertEquals(listOf("MacBook Pro · HDMI 1", "🎮 Xbox · HDMI 2", "HDMI 3"), out.map { it.label() })
    }

    @Test
    fun a_cec_device_without_a_name_of_its_own_takes_the_name_given_to_its_port() {
        val port = hdmi(1, custom = "Living room box")
        val device = hdmi(1, id = "tv/.Cec/HDMI1", label = "Device", parent = port.id)

        assertEquals("Living room box · HDMI 1", rows(port, device).single().label())
    }

    @Test
    fun only_connected_and_disconnected_ports_show_a_state() {
        val out = rows(hdmi(1, PortState.Connected), hdmi(2, PortState.Disconnected), hdmi(3, PortState.Standby), av())

        assertEquals(
            listOf(InputConnection.Connected, InputConnection.Disconnected, InputConnection.Unknown, InputConnection.Unknown),
            listOf(out[0], out[1], out[2], out[4]).map { it.connection() },
        )
    }

    @Test
    fun a_tv_that_reports_every_port_connected_shows_no_hdmi_state() {
        val out = rows(hdmi(1, PortState.Connected), hdmi(2, PortState.Connected), hdmi(3, PortState.Connected))

        assertEquals(List(3) { InputConnection.Unknown }, out.map { it.connection() })
    }

    @Test
    fun a_single_connected_port_keeps_its_state() {
        assertEquals(InputConnection.Connected, rows(hdmi(1, PortState.Connected)).single().connection())
    }

    @Test
    fun cec_state_only_drops_port_states_and_keeps_those_of_cec_devices() {
        val port = hdmi(1, PortState.Connected)
        val device = hdmi(1, id = "tv/.Cec/HDMI1", label = "Soundbar", parent = port.id, state = PortState.Connected)

        val out = rows(port, device, hdmi(2, PortState.Disconnected), settings = InputsSettings(cecStateOnly = true))

        assertEquals(listOf(InputConnection.Connected, InputConnection.Unknown), out.map { it.connection() })
    }

    @Test
    fun a_cec_device_loses_its_state_when_every_port_looks_connected() {
        val port = hdmi(1, PortState.Connected)
        val device = hdmi(1, id = "tv/.Cec/HDMI1", label = "Soundbar", parent = port.id, state = PortState.Connected)

        val out = rows(port, device, hdmi(2, PortState.Connected))

        assertEquals(InputConnection.Unknown, out[0].connection())
    }

    @Test
    fun connected_only_folds_the_rest_behind_more_inputs_until_it_is_asked_for() {
        val folding = InputsSettings(connectedOnly = true)
        val ports =
            rows(hdmi(1, PortState.Connected), hdmi(2, PortState.Disconnected), hdmi(3, PortState.Standby), av(), settings = folding)
        val menu = inputRows(ports, emptyList(), sourcePanel = false, liveTv = false)

        assertEquals(listOf(ports[0], InputRow.More), menu.visible(expanded = false))
        assertEquals(ports, menu.visible(expanded = true))
    }

    @Test
    fun nothing_folds_without_connected_only_and_no_more_row_shows() {
        val out = inputRows(rows(hdmi(1), av()), emptyList(), sourcePanel = false, liveTv = false)

        assertEquals(false, InputRow.More in out)
        assertEquals(false, out.any { (it as? InputRow.Port)?.folded == true })
    }

    @Test
    fun live_tv_is_the_last_row_behind_a_line() {
        val out = inputRows(rows(hdmi(1)), emptyList(), sourcePanel = false, liveTv = true)

        assertEquals(listOf("HDMI 1", InputRow.Separator(), InputRow.LiveTv), out.map { (it as? InputRow.Port)?.label ?: it })
        assertEquals(listOf<InputRow>(InputRow.LiveTv), inputRows(emptyList(), emptyList(), sourcePanel = false, liveTv = true))
    }

    @Test
    fun coocaa_sources_fill_in_only_when_the_tv_listed_nothing() {
        val sources = listOf(CoocaaSource("HDMI", 1, "HDMI 1"), CoocaaSource("VOD", 0, "TV"), CoocaaSource("AV", 0, "AV"))

        val empty = inputRows(emptyList(), sources, sourcePanel = true, liveTv = false)
        val listed = inputRows(rows(hdmi(1)), sources, sourcePanel = true, liveTv = false)

        assertEquals(listOf(InputIcon.Hdmi, InputIcon.LiveTv, InputIcon.Av), empty.map { (it as InputRow.Port).icon })
        assertEquals(InputTarget.Coocaa("HDMI", 1), (empty[0] as InputRow.Port).target)
        assertEquals(listOf("HDMI 1"), listed.map { it.label() })
    }

    @Test
    fun the_source_panel_is_one_inputs_row_where_nothing_else_is_listed() {
        assertEquals(listOf<InputRow>(InputRow.Panel), inputRows(emptyList(), emptyList(), sourcePanel = true, liveTv = false))
        assertEquals(
            listOf<InputRow>(InputRow.Panel, InputRow.Separator(), InputRow.LiveTv),
            inputRows(emptyList(), emptyList(), sourcePanel = true, liveTv = true),
        )
    }

    @Test
    fun coocaa_sources_sort_hdmi_then_av_then_others_then_the_tuner() {
        val sorted =
            sortedCoocaaSources(
                listOf(
                    CoocaaSource("VOD", 0, "TV"),
                    CoocaaSource("AV", 0, "AV"),
                    CoocaaSource("HDMI", 2, "HDMI 2"),
                    CoocaaSource("HDMI", 1, "HDMI 1"),
                ),
            )

        assertEquals(listOf("HDMI 1", "HDMI 2", "AV", "TV"), sorted.map { it.label })
    }
}

class TunerChoiceTest {
    @Test
    fun a_hardware_tuner_beats_a_software_one_and_digital_beats_analog() {
        val inputs =
            listOf(
                tuner("com.apple.airplay/.Tuner"),
                tuner("com.mediatek/.AnalogInputService/HW0"),
                tuner("com.mediatek/.TunerInputService/HW1"),
            )

        assertEquals("com.mediatek/.TunerInputService/HW1", chooseTuner(inputs, null, rootChannel = false))
        assertEquals("com.apple.airplay/.Tuner", chooseTuner(inputs.take(1), null, rootChannel = false))
    }

    @Test
    fun the_lowest_id_wins_among_equals_and_apps_that_are_not_system_apps_never_do() {
        val inputs = listOf(tuner("b/.T/HW2"), tuner("a/.T/HW3"), tuner("0/.T/HW1", system = false))

        assertEquals("a/.T/HW3", chooseTuner(inputs, null, rootChannel = false))
        assertEquals(null, chooseTuner(listOf(tuner("x/.T", system = false)), null, rootChannel = false))
    }

    @Test
    fun a_configured_input_id_restricts_the_choice_unless_it_is_the_root_channel() {
        val inputs = listOf(tuner("a/.T/HW1"), tuner("b/.S/HW2"))

        assertEquals("b/.S/HW2", chooseTuner(inputs, "b/.S/HW2", rootChannel = false))
        assertEquals(null, chooseTuner(inputs, "c/.Missing", rootChannel = false))
        assertEquals("a/.T/HW1", chooseTuner(inputs, "content://android.media.tv/channel", rootChannel = true))
    }
}
