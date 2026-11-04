package dev.niltsiar.tvhome.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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

class LibraryRulesTest {
    private val english = Collator.getInstance(Locale.ENGLISH)

    @Test
    fun the_pill_hides_only_while_the_top_bar_button_is_a_way_in() {
        assertTrue(showLibraryPill(library = true, button = false, pill = true))
        assertTrue(showLibraryPill(library = true, button = true, pill = true))
        assertTrue(showLibraryPill(library = true, button = false, pill = false))
        assertFalse(showLibraryPill(library = true, button = true, pill = false))
        assertFalse(showLibraryPill(library = false, button = true, pill = true))
        assertFalse(showLibraryPill(library = false, button = false, pill = true))
    }

    @Test
    fun ok_opens_home_apps_and_every_app_with_the_switch_but_never_in_selection_mode() {
        assertTrue(okOpens(selection = false, openAll = false, onHome = true))
        assertFalse(okOpens(selection = false, openAll = false, onHome = false))
        assertTrue(okOpens(selection = false, openAll = true, onHome = false))
        assertTrue(okOpens(selection = false, openAll = true, onHome = true))
        assertFalse(okOpens(selection = true, openAll = true, onHome = false))
        assertFalse(okOpens(selection = true, openAll = false, onHome = true))
    }

    @Test
    fun removing_keeps_the_next_app_or_the_previous_app_at_the_end() {
        assertEquals(1, nextAfterRemoval(0, 5))
        assertEquals(3, nextAfterRemoval(2, 5))
        assertEquals(3, nextAfterRemoval(4, 5))
        assertEquals(-1, nextAfterRemoval(0, 1))
        assertEquals(-1, nextAfterRemoval(0, 0))
        assertEquals(1, nextAfterRemoval(-1, 3))
        assertEquals(1, nextAfterRemoval(9, 3))
    }

    @Test
    fun selection_mode_puts_apps_on_home_first_and_each_group_in_label_order() {
        assertTrue(compareSelection(app("Zeta", onHome = true), app("Alpha"), english) < 0)
        assertTrue(compareSelection(app("Alpha", onHome = true), app("Zeta", onHome = true), english) < 0)
        assertTrue(compareSelection(app("Alpha"), app("Zeta"), english) < 0)
        assertTrue(compareSelection(app("Same", pkg = "a.pkg"), app("Same", pkg = "b.pkg"), english) < 0)
    }

    @Test
    fun missing_shortcuts_come_before_home_apps_even_when_the_home_app_is_newer() {
        assertTrue(comparePriority(app("a", installedAt = DAY), app("b", onHome = true, installedAt = NOW), NOW) < 0)
        assertTrue(comparePriority(app("b", onHome = true, installedAt = NOW), app("a", installedAt = DAY), NOW) > 0)
    }

    @Test
    fun recent_missing_apps_come_first_newest_first_and_the_rest_use_label_order() {
        assertTrue(comparePriority(app("a", installedAt = NOW), app("b", installedAt = NOW - DAY), NOW) < 0)
        assertTrue(comparePriority(app("a", installedAt = NOW - DAY), app("b", installedAt = DAY), NOW) < 0)
        assertEquals(0, comparePriority(app("a", installedAt = DAY), app("b", installedAt = 2 * DAY), NOW))
        assertEquals(0, comparePriority(app("a", onHome = true, installedAt = NOW), app("b", onHome = true, installedAt = DAY), NOW))
    }

    @Test
    fun recent_means_first_installed_within_seven_days_and_not_an_unknown_or_future_date() {
        assertTrue(isRecent(NOW - 7 * DAY, NOW))
        assertFalse(isRecent(NOW - 7 * DAY - 1, NOW))
        assertFalse(isRecent(0, NOW))
        assertFalse(isRecent(NOW + 1, NOW))
    }

    @Test
    fun the_normal_order_is_missing_recent_first_then_label_and_the_home_apps_last() {
        val apps =
            listOf(
                app("Zeta"),
                app("Home", onHome = true),
                app("alpha"),
                app("Fresh", installedAt = NOW - DAY),
                app("Beta"),
            )

        val sorted = sortLibrary(apps, selection = false, now = NOW, collator = english)

        assertEquals(listOf("Fresh", "alpha", "Beta", "Zeta", "Home"), sorted.map { it.app.label })
    }

    @Test
    fun the_selection_order_ignores_how_new_an_app_is() {
        val apps = listOf(app("Zeta"), app("Home", onHome = true), app("Fresh", installedAt = NOW - DAY), app("Beta"))

        val sorted = sortLibrary(apps, selection = true, now = NOW, collator = english)

        assertEquals(listOf("Home", "Beta", "Fresh", "Zeta"), sorted.map { it.app.label })
    }

    @Test
    fun adding_advances_past_apps_on_home_to_the_next_missing_shortcut() {
        assertEquals(3, nextFocus(1, 5) { it == 3 || it == 4 })
        assertEquals(0, nextFocus(4, 5) { it == 0 || it == 2 })
    }

    @Test
    fun adding_the_last_missing_shortcut_keeps_a_nearby_card_instead_of_jumping_to_the_start() {
        assertEquals(3, nextFocus(2, 5) { false })
        assertEquals(3, nextFocus(4, 5) { false })
        assertEquals(0, nextFocus(0, 1) { error("No other card to inspect") })
        assertEquals(-1, nextFocus(0, 0) { error("Empty catalogue") })
    }

    @Test
    fun a_stale_focus_is_clamped_after_an_app_goes() {
        assertEquals(1, nextFocus(9, 3) { it == 1 })
        assertEquals(1, nextFocus(-1, 3) { it == 1 })
    }
}
