package dev.niltsiar.tvhome.data.inputs

import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.model.InputArrival
import dev.niltsiar.tvhome.core.model.InputTarget
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

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
val ArrivalWatchTest by testSuite {
    testFixture {
        object {
            var tokens = 0
            val watch = ArrivalWatch { ++tokens }

            fun look(
                vararg inputs: TvInput,
                settings: InputsSettings = InputsSettings(),
            ): InputArrival? = watch.update(portEntries(inputs.toList(), settings))
        }
    } asContextForEach {
        test("a port that goes from no device to connected is an arrival named by its cec device and port") {
            look(port(1, PortState.Connected), port(2, PortState.Disconnected), port(3, PortState.Disconnected))

            val arrival =
                look(
                    port(1, PortState.Connected),
                    port(2, PortState.Connected),
                    device(2, PortState.Connected),
                    port(3, PortState.Disconnected),
                )

            arrival shouldBe InputArrival("PlayStation 5 · HDMI 2", InputTarget.TvInput("tv/.Cec/HDMI2"), token = 1)
        }

        test("a port without a cec device is named by the port alone") {
            look(port(1, PortState.Disconnected), port(2, PortState.Disconnected))

            val arrival = look(port(1, PortState.Connected), port(2, PortState.Disconnected))

            arrival shouldBe InputArrival("HDMI 1", InputTarget.TvInput("tv/.Hdmi/HW1"), token = 1)
        }

        test("a cec device that wakes from standby is an arrival") {
            look(port(2, PortState.Connected), device(2, PortState.Standby))

            val arrival = look(port(2, PortState.Connected), device(2, PortState.Connected))

            arrival?.label shouldBe "PlayStation 5 · HDMI 2"
        }

        test("a port that was connected when home came to the front is not an arrival") {
            val first = look(port(1, PortState.Connected), port(2, PortState.Disconnected))
            val second = look(port(1, PortState.Connected), port(2, PortState.Disconnected), port(3, PortState.Disconnected))

            first.shouldBeNull()
            second.shouldBeNull()
        }

        test("the arrival stays while its port stays connected and ends when it leaves") {
            look(port(2, PortState.Disconnected))
            look(port(2, PortState.Connected))

            look(port(2, PortState.Connected), device(2, PortState.Connected))?.token shouldBe 1
            look(port(2, PortState.Disconnected)).shouldBeNull()
        }

        test("a port that flaps back to connected is a new arrival") {
            look(port(2, PortState.Disconnected))
            val first = look(port(2, PortState.Connected))
            look(port(2, PortState.Standby))

            val second = look(port(2, PortState.Connected))

            second?.token shouldNotBe first?.token
        }

        test("a name that arrives late updates the arrival and keeps its token") {
            look(port(2, PortState.Disconnected))
            val bare = look(port(2, PortState.Connected))

            val named = look(port(2, PortState.Connected), device(2, PortState.Connected))

            bare?.label shouldBe "HDMI 2"
            named shouldBe InputArrival("PlayStation 5 · HDMI 2", InputTarget.TvInput("tv/.Cec/HDMI2"), bare?.token ?: -1)
        }

        test("a device that goes while its port still reports connected is not an arrival") {
            look(port(2, PortState.Connected), device(2, PortState.Connected))

            val arrival = look(port(2, PortState.Connected))

            arrival.shouldBeNull()
        }

        test("the latest arrival replaces the one before") {
            look(port(1, PortState.Disconnected), port(2, PortState.Disconnected), port(3, PortState.Disconnected))
            look(port(1, PortState.Connected), port(2, PortState.Disconnected), port(3, PortState.Disconnected))

            val second = look(port(1, PortState.Connected), port(2, PortState.Connected), port(3, PortState.Disconnected))

            second shouldBe InputArrival("HDMI 2", InputTarget.TvInput("tv/.Hdmi/HW2"), token = 2)
        }

        test("a tv that reports every port connected cannot tell an arrival") {
            look(port(1, PortState.Connected), port(2, PortState.Connected))

            val arrival = look(port(1, PortState.Connected), port(2, PortState.Connected), device(2, PortState.Connected))

            arrival.shouldBeNull()
        }

        test("with inputs cec state only a ports own state is ignored and the device counts") {
            val settings = InputsSettings(cecStateOnly = true)
            look(port(2, PortState.Disconnected), device(2, PortState.Standby), settings = settings)

            look(port(2, PortState.Connected), device(2, PortState.Standby), settings = settings).shouldBeNull()
            look(port(2, PortState.Connected), device(2, PortState.Connected), settings = settings)?.label shouldBe
                "PlayStation 5 · HDMI 2"
        }
    }
}
