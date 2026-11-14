package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

val LibraryChoicesTest by testSuite {
    fun shows(
        tvApp: Boolean,
        hidden: Boolean,
        added: Boolean,
        removed: Boolean = false,
    ): Boolean {
        val choices = LibraryChoices(added = setOf("pkg").filter { added }.toSet(), removed = setOf("pkg").filter { removed }.toSet())
        return choices.showsOnHome("pkg", tvApp, hidden)
    }

    test("theme and tv defaults stay in place until the owner chooses an app") {
        shows(tvApp = true, hidden = false, added = false).shouldBeTrue()
        shows(tvApp = true, hidden = true, added = false).shouldBeFalse()
        shows(tvApp = false, hidden = false, added = false).shouldBeFalse()
        shows(tvApp = false, hidden = false, added = true).shouldBeTrue()
        shows(tvApp = true, hidden = true, added = true).shouldBeTrue()
    }

    test("removing any home app overrides theme defaults and previous additions") {
        shows(tvApp = true, hidden = false, added = false, removed = true).shouldBeFalse()
        shows(tvApp = true, hidden = false, added = true, removed = true).shouldBeFalse()
        shows(tvApp = false, hidden = false, added = true, removed = true).shouldBeFalse()
    }

    test("undoing an addition restores default visibility and keeps other choices") {
        val original = setOf("com.example.phone", "com.example.hidden")

        val next = changeChoices(original, "com.example.phone", added = false)

        next shouldBe setOf("com.example.hidden")
        original shouldHaveSize 2
        LibraryChoices(added = next).showsOnHome("com.example.phone", tvApp = false, hidden = false).shouldBeFalse()
    }

    test("choices round trip in stable order including an empty list") {
        val apps = setOf("org.z.example", "com.a.example")

        encodeChoices(apps) shouldBe "com.a.example\norg.z.example\n"
        decodeChoices(encodeChoices(apps)) shouldBe apps
        decodeChoices("").shouldBeEmpty()
    }

    test("duplicate and malformed records are rejected") {
        for (raw in listOf(
            "com.example\ncom.example\n",
            "com..example\n",
            "com.example\n\n",
            "../file\n",
            "9bad.app\n",
            "com.example\ninvalid/name\n",
        )) {
            withClue(raw) { shouldThrow<IllegalArgumentException> { decodeChoices(raw) } }
        }
    }

    test("a package name is dot separated words that do not start with a digit") {
        isValidPackage("com.example.app_2").shouldBeTrue()
        isValidPackage("single").shouldBeTrue()
        for (bad in listOf("", ".a", "a.", "a..b", "a.9b", "a b", "a/b", "a-b", "x".repeat(256))) {
            withClue(bad) { isValidPackage(bad).shouldBeFalse() }
        }
        isValidPackage(null).shouldBeFalse()
    }

    test("a full list allows removal and an existing choice but not another app") {
        val apps = (0 until MAX_ADDED).map { "com.example.app$it" }.toSet()

        changeChoices(apps, "com.example.app0", added = true) shouldBe apps
        changeChoices(apps, "com.example.app0", added = false) shouldHaveSize MAX_ADDED - 1
        shouldThrow<IllegalArgumentException> { changeChoices(apps, "com.example.extra", added = true) }
        apps shouldHaveSize MAX_ADDED
    }

    test("a file with more than the most packages or bytes is rejected") {
        val tooMany = (0..MAX_ADDED).joinToString("") { "com.example.app$it\n" }
        shouldThrow<IllegalArgumentException> { decodeChoices(tooMany) }
        shouldThrow<IllegalArgumentException> { decodeChoices("a".repeat(MAX_ADDED * 256 + 1)) }
    }
}
