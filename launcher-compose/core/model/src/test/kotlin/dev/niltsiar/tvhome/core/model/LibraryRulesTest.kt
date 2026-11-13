package dev.niltsiar.tvhome.core.model

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.text.Collator
import java.util.Locale

private const val DAY = 24L * 60 * 60 * 1000
private const val NOW = 100 * DAY

private fun app(
    name: String,
    onHome: Boolean = false,
    installedAt: Long = 0,
    pkg: String = "pkg.$name",
) = LibraryApp(AppEntry(key = "$pkg/.Main", packageName = pkg, label = name), onHome, installedAt)

val LibraryRulesTest by testSuite {
    val english = Collator.getInstance(Locale.ENGLISH)

    test("the pill hides only while the top bar button is a way in") {
        showLibraryPill(library = true, button = false, pill = true).shouldBeTrue()
        showLibraryPill(library = true, button = true, pill = true).shouldBeTrue()
        showLibraryPill(library = true, button = false, pill = false).shouldBeTrue()
        showLibraryPill(library = true, button = true, pill = false).shouldBeFalse()
        showLibraryPill(library = false, button = true, pill = true).shouldBeFalse()
        showLibraryPill(library = false, button = false, pill = true).shouldBeFalse()
    }

    test("ok opens home apps and every app with the switch but never in selection mode") {
        okOpens(selection = false, openAll = false, onHome = true).shouldBeTrue()
        okOpens(selection = false, openAll = false, onHome = false).shouldBeFalse()
        okOpens(selection = false, openAll = true, onHome = false).shouldBeTrue()
        okOpens(selection = false, openAll = true, onHome = true).shouldBeTrue()
        okOpens(selection = true, openAll = true, onHome = false).shouldBeFalse()
        okOpens(selection = true, openAll = false, onHome = true).shouldBeFalse()
    }

    test("selection mode orders by label then package whether or not an app is on home") {
        val apps = listOf(app("Zeta", onHome = true), app("Same", pkg = "b.pkg"), app("alpha"), app("Same", pkg = "a.pkg", onHome = true))

        val sorted = sortLibrary(apps, selection = true, now = NOW, collator = english)

        sorted.map { it.app.packageName } shouldBe listOf("pkg.alpha", "a.pkg", "b.pkg", "pkg.Zeta")
    }

    test("missing shortcuts come before home apps even when the home app is newer") {
        comparePriority(app("a", installedAt = DAY), app("b", onHome = true, installedAt = NOW), NOW) shouldBeLessThan 0
        comparePriority(app("b", onHome = true, installedAt = NOW), app("a", installedAt = DAY), NOW) shouldBeGreaterThan 0
    }

    test("recent missing apps come first newest first and the rest use label order") {
        comparePriority(app("a", installedAt = NOW), app("b", installedAt = NOW - DAY), NOW) shouldBeLessThan 0
        comparePriority(app("a", installedAt = NOW - DAY), app("b", installedAt = DAY), NOW) shouldBeLessThan 0
        comparePriority(app("a", installedAt = DAY), app("b", installedAt = 2 * DAY), NOW) shouldBe 0
        comparePriority(app("a", onHome = true, installedAt = NOW), app("b", onHome = true, installedAt = DAY), NOW) shouldBe 0
    }

    test("recent means first installed within seven days and not an unknown or future date") {
        isRecent(NOW - 7 * DAY, NOW).shouldBeTrue()
        isRecent(NOW - 7 * DAY - 1, NOW).shouldBeFalse()
        isRecent(0, NOW).shouldBeFalse()
        isRecent(NOW + 1, NOW).shouldBeFalse()
    }

    test("the normal order is missing recent first then label and the home apps last") {
        val apps =
            listOf(
                app("Zeta"),
                app("Home", onHome = true),
                app("alpha"),
                app("Fresh", installedAt = NOW - DAY),
                app("Beta"),
            )

        val sorted = sortLibrary(apps, selection = false, now = NOW, collator = english)

        sorted.map { it.app.label } shouldBe listOf("Fresh", "alpha", "Beta", "Zeta", "Home")
    }

    test("the selection order ignores how new an app is") {
        val apps = listOf(app("Zeta"), app("Home", onHome = true), app("Fresh", installedAt = NOW - DAY), app("Beta"))

        val sorted = sortLibrary(apps, selection = true, now = NOW, collator = english)

        sorted.map { it.app.label } shouldBe listOf("Beta", "Fresh", "Home", "Zeta")
    }

    test("adding advances past apps on home to the next missing shortcut") {
        nextFocus(1, 5) { it == 3 || it == 4 } shouldBe 3
        nextFocus(4, 5) { it == 0 || it == 2 } shouldBe 0
    }

    test("adding the last missing shortcut keeps a nearby card instead of jumping to the start") {
        nextFocus(2, 5) { false } shouldBe 3
        nextFocus(4, 5) { false } shouldBe 3
        nextFocus(0, 1) { error("No other card to inspect") } shouldBe 0
        nextFocus(0, 0) { error("Empty catalogue") } shouldBe -1
    }

    test("a stale focus is clamped after an app goes") {
        nextFocus(9, 3) { it == 1 } shouldBe 1
        nextFocus(-1, 3) { it == 1 } shouldBe 1
    }
}
