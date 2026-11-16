package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputTile
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.rules.TemporaryFolder
import java.io.File

val InputPinsTest by testSuite {
    robolectricTestSuite<InputPinsTestContent>("default sdk")
}

private val Console = InputTile("tv/.Cec/HDMI40008", "tv/.Hdmi/HW4", InputIcon.Hdmi)
private val Jack = InputTile("tv/.Av/AV1", null, InputIcon.Av)

class InputPinsTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val folder = rule(TemporaryFolder())

                val store get() = InputPinStore(folder.root)

                val file get() = File(folder.root, INPUT_PINS_FILE)
            }
        } asContextForEach {
            test("a pin survives a write and a read") {
                store.pin(Console, "PlayStation 5 · HDMI 4").shouldBeTrue()
                store.pin(Jack, "AV 1").shouldBeTrue()

                file.readText() shouldBe "tv/.Cec/HDMI40008\ttv/.Hdmi/HW4\tHdmi\tPlayStation 5 · HDMI 4\ntv/.Av/AV1\t-\tAv\tAV 1\n"
                store.load() shouldBe listOf(InputPin(Console, "PlayStation 5 · HDMI 4"), InputPin(Jack, "AV 1"))
            }

            test("pinning an input again keeps its place and takes the new name") {
                store.pin(Console, "PlayStation 5 · HDMI 4")
                store.pin(Jack, "AV 1")

                store.pin(Console, "HDMI 4").shouldBeTrue()

                store.load() shouldBe listOf(InputPin(Console, "HDMI 4"), InputPin(Jack, "AV 1"))
            }

            test("unpinning takes out that input alone") {
                store.pin(Console, "PlayStation 5 · HDMI 4")
                store.pin(Jack, "AV 1")

                store.unpin(Console.id).shouldBeTrue()

                store.load() shouldBe listOf(InputPin(Jack, "AV 1"))
            }

            test("an unreadable pin file is left as it is and reads as no pins") {
                file.writeText("tv/.Hdmi/HW1\t-\tHdmi\n")

                store.pin(Jack, "AV 1").shouldBeFalse()
                store.unpin("tv/.Hdmi/HW1").shouldBeFalse()

                file.readText() shouldBe "tv/.Hdmi/HW1\t-\tHdmi\n"
                store.load().shouldBeEmpty()
            }

            test("a pin over the cap is refused") {
                file.writeText((0 until MAX_PINS).joinToString("") { "tv/.Hdmi/HW$it\t-\tHdmi\tHDMI $it\n" })

                store.pin(Jack, "AV 1").shouldBeFalse()

                store.load() shouldHaveSize MAX_PINS
            }
        }
    })
