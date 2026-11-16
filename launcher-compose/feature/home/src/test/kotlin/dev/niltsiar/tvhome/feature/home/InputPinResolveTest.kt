package dev.niltsiar.tvhome.feature.home

import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputTile
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/** Which Inputs row a pinned input stands for now, as the TV lists its ports and CEC devices. */
val InputPinResolveTest by testSuite {
    val pin = InputTile("tv/.Cec/HDMI2004", "tv/.Hdmi/HW2", InputIcon.Hdmi)

    fun device(
        id: String,
        label: String,
        port: String = "tv/.Hdmi/HW2",
    ) = InputRow.Port(label, InputIcon.Hdmi, InputConnection.Connected, InputTarget.TvInput(id), port = port)

    val hdmi1 = InputRow.Port("HDMI 1", InputIcon.Hdmi, InputConnection.Unknown, InputTarget.TvInput("tv/.Hdmi/HW1"), port = "tv/.Hdmi/HW1")

    test("a pinned device id that is listed resolves to its row") {
        val own = device("tv/.Cec/HDMI2004", "PlayStation 5 · HDMI 2")
        val other = device("tv/.Cec/HDMI2008", "Soundbar · HDMI 2")

        resolvePin(pin, listOf(hdmi1, other, own, InputRow.LiveTv)) shouldBe own
    }

    test("a pinned device that left resolves to the device now on its port") {
        val readdressed = device("tv/.Cec/HDMI2008", "PlayStation 5 · HDMI 2")

        resolvePin(pin, listOf(hdmi1, readdressed, InputRow.LiveTv)) shouldBe readdressed
    }

    test("a pin whose port is gone resolves to nothing") {
        val elsewhere = device("tv/.Cec/HDMI3004", "PlayStation 5 · HDMI 3", port = "tv/.Hdmi/HW3")

        resolvePin(pin, listOf(hdmi1, elsewhere, InputRow.LiveTv)).shouldBeNull()
    }
}
