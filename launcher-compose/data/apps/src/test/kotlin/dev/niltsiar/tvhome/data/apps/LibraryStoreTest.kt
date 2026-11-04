package dev.niltsiar.tvhome.data.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LibraryStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val store get() = LibraryStore(folder.root)

    private fun file(name: String) = File(folder.root, name)

    @Test
    fun without_files_nothing_is_chosen() {
        assertEquals(LibraryChoices(), store.load())
    }

    @Test
    fun adding_an_app_writes_it_sorted_and_clears_an_earlier_removal() {
        file(LIBRARY_REMOVED_FILE).writeText("com.b\ncom.z\n")
        file(LIBRARY_ADDED_FILE).writeText("com.y\n")

        assertTrue(store.save("com.b", onHome = true))

        assertEquals("com.b\ncom.y\n", file(LIBRARY_ADDED_FILE).readText())
        assertEquals("com.z\n", file(LIBRARY_REMOVED_FILE).readText())
        assertEquals(LibraryChoices(added = setOf("com.b", "com.y"), removed = setOf("com.z")), store.load())
    }

    @Test
    fun removing_an_app_records_the_removal_and_drops_an_earlier_addition() {
        file(LIBRARY_ADDED_FILE).writeText("com.a\ncom.b\n")

        assertTrue(store.save("com.a", onHome = false))

        assertEquals("com.a\n", file(LIBRARY_REMOVED_FILE).readText())
        assertEquals("com.b\n", file(LIBRARY_ADDED_FILE).readText())
    }

    @Test
    fun a_file_that_cannot_be_read_stays_as_it_is_and_nothing_is_written() {
        file(LIBRARY_ADDED_FILE).writeText("not a package\n")

        assertFalse(store.save("com.a", onHome = true))
        assertFalse(store.save("com.a", onHome = false))

        assertEquals("not a package\n", file(LIBRARY_ADDED_FILE).readText())
        assertFalse(file(LIBRARY_REMOVED_FILE).exists())
        assertEquals(LibraryChoices(), store.load())
    }

    @Test
    fun a_file_over_the_size_limit_is_not_read() {
        file(LIBRARY_REMOVED_FILE).writeText("a".repeat(MAX_ADDED * 256 + 1))

        assertFalse(store.save("com.a", onHome = true))

        assertFalse(file(LIBRARY_ADDED_FILE).exists())
    }

    @Test
    fun an_app_is_not_saved_past_the_limit_or_under_an_invalid_name() {
        file(LIBRARY_ADDED_FILE).writeText((0 until MAX_ADDED).joinToString("") { "com.example.app$it\n" })

        assertFalse(store.save("com.example.extra", onHome = true))
        assertFalse(store.save("../escape", onHome = true))

        assertEquals(MAX_ADDED, store.load().added.size)
    }
}
