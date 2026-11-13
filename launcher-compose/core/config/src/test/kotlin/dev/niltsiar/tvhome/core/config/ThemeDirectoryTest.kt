package dev.niltsiar.tvhome.core.config

import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.rules.TemporaryFolder

val ThemeDirectoryTest by testSuite {
    testFixture {
        object : JUnit4RulesContext() {
            val folder = rule(TemporaryFolder())
        }
    } asContextForEach {
        test("mounted storage parses config json and keeps a copy") {
            val file = folder.newFile("config.json").apply { writeText("""{"columns": 6}""") }
            val copy = folder.root.resolve("copy.json")

            readConfig(file, copy, mounted = true).config.columns shouldBe 6
            parseTvConfig(copy.readText()).config.columns shouldBe 6
        }

        test("unmounted storage reads the copy") {
            val copy = folder.newFile("copy.json").apply { writeText("""{"columns": 7}""") }

            val parsed = readConfig(null, copy, mounted = false)
            parsed.config.columns shouldBe 7
            copy.exists().shouldBeTrue()
        }

        test("a missing file on mounted storage deletes the copy") {
            val copy = folder.newFile("copy.json").apply { writeText("""{"columns": 7}""") }

            val parsed = readConfig(folder.root.resolve("config.json"), copy, mounted = true)
            parsed.overlayApplied.shouldBeFalse()
            copy.exists().shouldBeFalse()
        }
    }
}
