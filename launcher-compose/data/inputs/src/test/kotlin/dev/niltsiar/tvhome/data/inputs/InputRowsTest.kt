package dev.niltsiar.tvhome.data.inputs

import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.visible
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

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

val InputRowsTest by testSuite {
    test("hdmi ports come first in label order and a line separates the av jacks") {
        val out = rows(av(), hdmi(2), hdmi(1))

        out.take(2).map { it.label() } shouldBe listOf("HDMI 1", "HDMI 2")
        out[2] shouldBe InputRow.Separator()
        out[3].label() shouldBe "AV 1"
        (out[3] as InputRow.Port).icon shouldBe InputIcon.Av
        out shouldHaveSize 4
    }

    test("a label without hdmi gets the prefix") {
        rows(hdmi(1, label = "Game")).single().label() shouldBe "HDMI Game"
        rows(hdmi(1, label = "hdmi 1")).single().label() shouldBe "hdmi 1"
    }

    test("inputs hidden in the tv settings or mapped to a dash are left out") {
        val out = rows(hdmi(1), hdmi(2, hidden = true), hdmi(3), settings = InputsSettings(inputSources = listOf("HW3=-")))

        out.map { it.label() } shouldBe listOf("HDMI 1")
    }

    test("a cec device takes its ports row and names itself before the port") {
        val port = hdmi(1, state = PortState.Connected)
        val device = hdmi(1, id = "tv/.Cec/HDMI1", label = "Apple TV", parent = port.id, state = PortState.Connected)

        val out = rows(port, device, hdmi(2))

        out.map { it.label() } shouldBe listOf("Apple TV · HDMI 1", "HDMI 2")
        (out[0] as InputRow.Port).target shouldBe InputTarget.TvInput("tv/.Cec/HDMI1")
    }

    test("a name given in settings goes before the port and philips types become emoji") {
        val out = rows(hdmi(1, custom = "MacBook Pro"), hdmi(2, custom = "Xbox&GAME_CONSOLE"), hdmi(3, custom = "null"))

        out.map { it.label() } shouldBe listOf("MacBook Pro · HDMI 1", "🎮 Xbox · HDMI 2", "HDMI 3")
    }

    test("a cec device without a name of its own takes the name given to its port") {
        val port = hdmi(1, custom = "Living room box")
        val device = hdmi(1, id = "tv/.Cec/HDMI1", label = "Device", parent = port.id)

        rows(port, device).single().label() shouldBe "Living room box · HDMI 1"
    }

    test("only connected and disconnected ports show a state") {
        val out = rows(hdmi(1, PortState.Connected), hdmi(2, PortState.Disconnected), hdmi(3, PortState.Standby), av())

        listOf(out[0], out[1], out[2], out[4]).map { it.connection() } shouldBe
            listOf(InputConnection.Connected, InputConnection.Disconnected, InputConnection.Unknown, InputConnection.Unknown)
    }

    test("a tv that reports every port connected shows no hdmi state") {
        val out = rows(hdmi(1, PortState.Connected), hdmi(2, PortState.Connected), hdmi(3, PortState.Connected))

        out.map { it.connection() } shouldBe List(3) { InputConnection.Unknown }
    }

    test("a single connected port keeps its state") {
        rows(hdmi(1, PortState.Connected)).single().connection() shouldBe InputConnection.Connected
    }

    test("cec state only drops port states and keeps those of cec devices") {
        val port = hdmi(1, PortState.Connected)
        val device = hdmi(1, id = "tv/.Cec/HDMI1", label = "Soundbar", parent = port.id, state = PortState.Connected)

        val out = rows(port, device, hdmi(2, PortState.Disconnected), settings = InputsSettings(cecStateOnly = true))

        out.map { it.connection() } shouldBe listOf(InputConnection.Connected, InputConnection.Unknown)
    }

    test("a cec device loses its state when every port looks connected") {
        val port = hdmi(1, PortState.Connected)
        val device = hdmi(1, id = "tv/.Cec/HDMI1", label = "Soundbar", parent = port.id, state = PortState.Connected)

        val out = rows(port, device, hdmi(2, PortState.Connected))

        out[0].connection() shouldBe InputConnection.Unknown
    }

    test("connected only folds the rest behind more inputs until it is asked for") {
        val folding = InputsSettings(connectedOnly = true)
        val ports =
            rows(hdmi(1, PortState.Connected), hdmi(2, PortState.Disconnected), hdmi(3, PortState.Standby), av(), settings = folding)
        val menu = inputRows(ports, emptyList(), sourcePanel = false, liveTv = false)

        menu.visible(expanded = false) shouldBe listOf(ports[0], InputRow.More)
        menu.visible(expanded = true) shouldBe ports
    }

    test("nothing folds without connected only and no more row shows") {
        val out = inputRows(rows(hdmi(1), av()), emptyList(), sourcePanel = false, liveTv = false)

        (InputRow.More in out) shouldBe false
        out.any { (it as? InputRow.Port)?.folded == true } shouldBe false
    }

    test("live tv is the last row behind a line") {
        val out = inputRows(rows(hdmi(1)), emptyList(), sourcePanel = false, liveTv = true)

        out.map { (it as? InputRow.Port)?.label ?: it } shouldBe listOf("HDMI 1", InputRow.Separator(), InputRow.LiveTv)
        inputRows(emptyList(), emptyList(), sourcePanel = false, liveTv = true) shouldBe listOf<InputRow>(InputRow.LiveTv)
    }

    test("coocaa sources fill in only when the tv listed nothing") {
        val sources = listOf(CoocaaSource("HDMI", 1, "HDMI 1"), CoocaaSource("VOD", 0, "TV"), CoocaaSource("AV", 0, "AV"))

        val empty = inputRows(emptyList(), sources, sourcePanel = true, liveTv = false)
        val listed = inputRows(rows(hdmi(1)), sources, sourcePanel = true, liveTv = false)

        empty.map { (it as InputRow.Port).icon } shouldBe listOf(InputIcon.Hdmi, InputIcon.LiveTv, InputIcon.Av)
        (empty[0] as InputRow.Port).target shouldBe InputTarget.Coocaa("HDMI", 1)
        listed.map { it.label() } shouldBe listOf("HDMI 1")
    }

    test("the source panel is one inputs row where nothing else is listed") {
        inputRows(emptyList(), emptyList(), sourcePanel = true, liveTv = false) shouldBe listOf<InputRow>(InputRow.Panel)
        inputRows(emptyList(), emptyList(), sourcePanel = true, liveTv = true) shouldBe
            listOf<InputRow>(InputRow.Panel, InputRow.Separator(), InputRow.LiveTv)
    }

    test("coocaa sources sort hdmi then av then others then the tuner") {
        val sorted =
            sortedCoocaaSources(
                listOf(
                    CoocaaSource("VOD", 0, "TV"),
                    CoocaaSource("AV", 0, "AV"),
                    CoocaaSource("HDMI", 2, "HDMI 2"),
                    CoocaaSource("HDMI", 1, "HDMI 1"),
                ),
            )

        sorted.map { it.label } shouldBe listOf("HDMI 1", "HDMI 2", "AV", "TV")
    }
}

val TunerChoiceTest by testSuite {
    test("a hardware tuner beats a software one and digital beats analog") {
        val inputs =
            listOf(
                tuner("com.apple.airplay/.Tuner"),
                tuner("com.mediatek/.AnalogInputService/HW0"),
                tuner("com.mediatek/.TunerInputService/HW1"),
            )

        chooseTuner(inputs, null, rootChannel = false) shouldBe "com.mediatek/.TunerInputService/HW1"
        chooseTuner(inputs.take(1), null, rootChannel = false) shouldBe "com.apple.airplay/.Tuner"
    }

    test("the lowest id wins among equals and apps that are not system apps never do") {
        val inputs = listOf(tuner("b/.T/HW2"), tuner("a/.T/HW3"), tuner("0/.T/HW1", system = false))

        chooseTuner(inputs, null, rootChannel = false) shouldBe "a/.T/HW3"
        chooseTuner(listOf(tuner("x/.T", system = false)), null, rootChannel = false).shouldBeNull()
    }

    test("a configured input id restricts the choice unless it is the root channel") {
        val inputs = listOf(tuner("a/.T/HW1"), tuner("b/.S/HW2"))

        chooseTuner(inputs, "b/.S/HW2", rootChannel = false) shouldBe "b/.S/HW2"
        chooseTuner(inputs, "c/.Missing", rootChannel = false).shouldBeNull()
        chooseTuner(inputs, "content://android.media.tv/channel", rootChannel = true) shouldBe "a/.T/HW1"
    }
}
