package dev.niltsiar.tvhome.data.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrderFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun a_written_order_reads_back() {
        val file = folder.newFile()
        val order = Order("a,b", listOf("x/.X", "y/.Y"), docked = 1)

        assertTrue(writeOrder(file, order))

        assertEquals(order, readOrder(file))
    }

    @Test
    fun no_file_is_no_order() {
        assertNull(readOrder(folder.root.resolve(ORDER_FILE_NAME)))
    }

    @Test
    fun a_file_that_is_not_an_order_is_ignored_but_kept() {
        val file = folder.newFile()
        file.writeText("not an order")

        assertNull(readOrder(file))
        assertTrue(file.exists())
    }

    @Test
    fun a_write_that_cannot_be_made_reports_failure() {
        val notAFolder = folder.newFile()

        assertFalse(writeOrder(notAFolder.resolve("order.txt"), Order("a", emptyList())))
    }

    @Test
    fun the_file_holds_the_reference_text() {
        val file = folder.newFile()

        writeOrder(file, Order("a,b", listOf("x/.X", "y/.Y"), docked = 1))

        assertEquals("tvhome-order 1\ndock a,b\ndocked 1\nx/.X\ny/.Y\n", file.readText())
    }

    @Test
    fun a_second_write_replaces_the_first_whole_and_leaves_no_backup() {
        val file = folder.newFile()
        writeOrder(file, Order("a", listOf("x/.X", "y/.Y", "z/.Z")))

        writeOrder(file, Order("a", listOf("z/.Z")))

        assertEquals(Order("a", listOf("z/.Z")), readOrder(file))
        assertEquals(listOf(file.name), folder.root.list()!!.toList())
    }
}
