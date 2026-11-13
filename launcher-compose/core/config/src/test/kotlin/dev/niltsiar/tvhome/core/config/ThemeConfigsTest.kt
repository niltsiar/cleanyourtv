package dev.niltsiar.tvhome.core.config

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.io.File

val ThemeConfigsTest by testSuite {
    test("every theme in the repository parses without unknown keys or rejected values") {
        val themes = File(System.getProperty("tvhome.themesDir")!!)
        val configs =
            themes
                .listFiles { dir -> dir.isDirectory }
                .orEmpty()
                .map { File(it, "config.json") }
                .filter { it.isFile }
        withClue("no themes/*/config.json under $themes") { configs.shouldNotBeEmpty() }

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
        problems.shouldBeEmpty()
    }
}
