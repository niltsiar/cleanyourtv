package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

val OrderTest by testSuite {
    val dock = listOf("com.plexapp.android", "com.netflix.ninja")

    test("an order round trips") {
        val order = Order(Order.dockKey(dock), listOf("b/.B", "a/.A", "c/.C"))

        Order.decode(order.encode()) shouldBe order
    }

    test("the file text is the reference format") {
        val order = Order(Order.dockKey(dock), listOf("b/.B", "a/.A", "c/.C"), docked = 2)

        order.encode() shouldBe "tvhome-order 1\ndock com.plexapp.android,com.netflix.ninja\ndocked 2\nb/.B\na/.A\nc/.C\n"
        Order.decode(order.encode()) shouldBe order
    }

    test("an order without docked leaves the dock to dock apps") {
        val order = Order.decode("tvhome-order 1\ndock x\na/.A\nb/.B\n")!!

        order.docked shouldBe -1
        order.keys shouldBe listOf("a/.A", "b/.B")
        order.dockedFor(listOf("x")) shouldBe -1
        Order("x", listOf("a/.A")).encode() shouldNotContain "docked"
    }

    test("an empty order round trips") {
        Order.decode(Order("", emptyList()).encode()) shouldBe Order("", emptyList())
    }

    test("a bad docked line is not an order") {
        Order.decode("tvhome-order 1\ndock x\ndocked two\na/.A\n").shouldBeNull()
        Order.decode("tvhome-order 1\ndock x\ndocked -1\na/.A\n").shouldBeNull()
        Order.decode("tvhome-order 1\ndock x\ndocked 3\na/.A\n").shouldBeNull()
    }

    test("text that is not an order is rejected") {
        Order.decode("").shouldBeNull()
        Order.decode("{\"version\":2}").shouldBeNull()
        Order.decode("tvhome-order 2\ndock x\na/.A\n").shouldBeNull()
        Order.decode("tvhome-order 1\na/.A\n").shouldBeNull()
        Order.decode("tvhome-order 1\ndock x\na/.A\n\nb/.B\n").shouldBeNull()
        Order.decode("tvhome-order 1\ndock x\na/.A\na/.A\n").shouldBeNull()
    }

    test("ranks follow the saved order") {
        val order = Order(Order.dockKey(dock), listOf("b/.B", "a/.A"))

        order.ranks(dock) shouldBe mapOf("b/.B" to 0, "a/.A" to 1)
    }

    test("changed dock apps drop the saved order and dock size") {
        val order = Order(Order.dockKey(dock), listOf("b/.B", "a/.A"), docked = 1)

        order.dockedFor(dock) shouldBe 1
        order.ranks(dock.reversed()).shouldBeEmpty()
        order.dockedFor(dock.take(1)) shouldBe -1
    }
}
