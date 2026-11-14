package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.rules.TemporaryFolder

val OrderFileTest by testSuite {
    robolectricTestSuite<OrderFileTestContent>("default sdk")
}

class OrderFileTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val folder = rule(TemporaryFolder())
            }
        } asContextForEach {
            test("a written order reads back") {
                val file = folder.newFile()
                val order = Order("a,b", listOf("x/.X", "y/.Y"), docked = 1)

                writeOrder(file, order).shouldBeTrue()

                readOrder(file) shouldBe order
            }

            test("no file is no order") {
                readOrder(folder.root.resolve(ORDER_FILE_NAME)).shouldBeNull()
            }

            test("a file that is not an order is ignored but kept") {
                val file = folder.newFile()
                file.writeText("not an order")

                readOrder(file).shouldBeNull()
                file.exists().shouldBeTrue()
            }

            test("a write that cannot be made reports failure") {
                val notAFolder = folder.newFile()

                writeOrder(notAFolder.resolve("order.txt"), Order("a", emptyList())).shouldBeFalse()
            }

            test("the file holds the reference text") {
                val file = folder.newFile()

                writeOrder(file, Order("a,b", listOf("x/.X", "y/.Y"), docked = 1))

                file.readText() shouldBe "tvhome-order 1\ndock a,b\ndocked 1\nx/.X\ny/.Y\n"
            }

            test("a second write replaces the first whole and leaves no backup") {
                val file = folder.newFile()
                writeOrder(file, Order("a", listOf("x/.X", "y/.Y", "z/.Z")))

                writeOrder(file, Order("a", listOf("z/.Z")))

                readOrder(file) shouldBe Order("a", listOf("z/.Z"))
                folder.root.list()!!.toList() shouldBe listOf(file.name)
            }
        }
    })
