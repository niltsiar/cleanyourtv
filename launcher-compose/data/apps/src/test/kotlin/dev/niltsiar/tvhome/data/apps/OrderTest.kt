package dev.niltsiar.tvhome.data.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderTest {
    private val dock = listOf("com.plexapp.android", "com.netflix.ninja")

    @Test
    fun an_order_round_trips() {
        val order = Order(Order.dockKey(dock), listOf("b/.B", "a/.A", "c/.C"))

        assertEquals(order, Order.decode(order.encode()))
    }

    @Test
    fun the_file_text_is_the_reference_format() {
        val order = Order(Order.dockKey(dock), listOf("b/.B", "a/.A", "c/.C"), docked = 2)

        assertEquals("tvhome-order 1\ndock com.plexapp.android,com.netflix.ninja\ndocked 2\nb/.B\na/.A\nc/.C\n", order.encode())
        assertEquals(order, Order.decode(order.encode()))
    }

    @Test
    fun an_order_without_docked_leaves_the_dock_to_dock_apps() {
        val order = Order.decode("tvhome-order 1\ndock x\na/.A\nb/.B\n")!!

        assertEquals(-1, order.docked)
        assertEquals(listOf("a/.A", "b/.B"), order.keys)
        assertEquals(-1, order.dockedFor(listOf("x")))
        assertTrue("docked" !in Order("x", listOf("a/.A")).encode())
    }

    @Test
    fun an_empty_order_round_trips() {
        assertEquals(Order("", emptyList()), Order.decode(Order("", emptyList()).encode()))
    }

    @Test
    fun a_bad_docked_line_is_not_an_order() {
        assertNull(Order.decode("tvhome-order 1\ndock x\ndocked two\na/.A\n"))
        assertNull(Order.decode("tvhome-order 1\ndock x\ndocked -1\na/.A\n"))
        assertNull(Order.decode("tvhome-order 1\ndock x\ndocked 3\na/.A\n"))
    }

    @Test
    fun text_that_is_not_an_order_is_rejected() {
        assertNull(Order.decode(""))
        assertNull(Order.decode("{\"version\":2}"))
        assertNull(Order.decode("tvhome-order 2\ndock x\na/.A\n"))
        assertNull(Order.decode("tvhome-order 1\na/.A\n"))
        assertNull(Order.decode("tvhome-order 1\ndock x\na/.A\n\nb/.B\n"))
        assertNull(Order.decode("tvhome-order 1\ndock x\na/.A\na/.A\n"))
    }

    @Test
    fun ranks_follow_the_saved_order() {
        val order = Order(Order.dockKey(dock), listOf("b/.B", "a/.A"))

        assertEquals(mapOf("b/.B" to 0, "a/.A" to 1), order.ranks(dock))
    }

    @Test
    fun changed_dock_apps_drop_the_saved_order_and_dock_size() {
        val order = Order(Order.dockKey(dock), listOf("b/.B", "a/.A"), docked = 1)

        assertEquals(1, order.dockedFor(dock))
        assertEquals(emptyMap<String, Int>(), order.ranks(dock.reversed()))
        assertEquals(-1, order.dockedFor(dock.take(1)))
    }
}
