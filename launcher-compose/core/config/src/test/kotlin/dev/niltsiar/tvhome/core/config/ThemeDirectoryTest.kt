package dev.niltsiar.tvhome.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeDirectoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun mounted_storage_parses_config_json_and_keeps_a_copy() {
        val file = folder.newFile("config.json").apply { writeText("""{"columns": 6}""") }
        val copy = folder.root.resolve("copy.json")

        assertEquals(6, readConfig(file, copy, mounted = true).config.columns)
        assertEquals(6, parseTvConfig(copy.readText()).config.columns)
    }

    @Test
    fun unmounted_storage_reads_the_copy() {
        val copy = folder.newFile("copy.json").apply { writeText("""{"columns": 7}""") }

        val parsed = readConfig(null, copy, mounted = false)
        assertEquals(7, parsed.config.columns)
        assertTrue(copy.exists())
    }

    @Test
    fun a_missing_file_on_mounted_storage_deletes_the_copy() {
        val copy = folder.newFile("copy.json").apply { writeText("""{"columns": 7}""") }

        val parsed = readConfig(folder.root.resolve("config.json"), copy, mounted = true)
        assertFalse(parsed.overlayApplied)
        assertFalse(copy.exists())
    }
}
