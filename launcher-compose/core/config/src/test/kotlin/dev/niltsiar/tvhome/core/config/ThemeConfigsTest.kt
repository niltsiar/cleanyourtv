package dev.niltsiar.tvhome.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeConfigsTest {
    @Test
    fun every_theme_in_the_repository_parses_without_unknown_keys_or_rejected_values() {
        val themes = File(System.getProperty("tvhome.themesDir")!!)
        val configs =
            themes
                .listFiles { dir -> dir.isDirectory }
                .orEmpty()
                .map { File(it, "config.json") }
                .filter { it.isFile }
        assertTrue("no themes/*/config.json under $themes", configs.isNotEmpty())

        val problems =
            configs.sorted().flatMap { file ->
                val theme = file.parentFile!!.name
                val parsed = parseTvConfig(file.readText())
                listOfNotNull(
                    "$theme: not a JSON object".takeUnless { parsed.overlayApplied },
                    "$theme: unknown keys ${parsed.unknownKeys}".takeIf { parsed.unknownKeys.isNotEmpty() },
                    "$theme: rejected values ${parsed.rejectedKeys}".takeIf { parsed.rejectedKeys.isNotEmpty() },
                )
            }
        assertEquals(emptyList<String>(), problems)
    }
}
