package dev.niltsiar.tvhome.data.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LibraryChoicesTest {
    private fun shows(
        tvApp: Boolean,
        hidden: Boolean,
        added: Boolean,
        removed: Boolean = false,
    ): Boolean {
        val choices = LibraryChoices(added = setOf("pkg").filter { added }.toSet(), removed = setOf("pkg").filter { removed }.toSet())
        return choices.showsOnHome("pkg", tvApp, hidden)
    }

    @Test
    fun theme_and_tv_defaults_stay_in_place_until_the_owner_chooses_an_app() {
        assertTrue(shows(tvApp = true, hidden = false, added = false))
        assertFalse(shows(tvApp = true, hidden = true, added = false))
        assertFalse(shows(tvApp = false, hidden = false, added = false))
        assertTrue(shows(tvApp = false, hidden = false, added = true))
        assertTrue(shows(tvApp = true, hidden = true, added = true))
    }

    @Test
    fun removing_any_home_app_overrides_theme_defaults_and_previous_additions() {
        assertFalse(shows(tvApp = true, hidden = false, added = false, removed = true))
        assertFalse(shows(tvApp = true, hidden = false, added = true, removed = true))
        assertFalse(shows(tvApp = false, hidden = false, added = true, removed = true))
    }

    @Test
    fun undoing_an_addition_restores_default_visibility_and_keeps_other_choices() {
        val original = setOf("com.example.phone", "com.example.hidden")

        val next = changeChoices(original, "com.example.phone", added = false)

        assertEquals(setOf("com.example.hidden"), next)
        assertEquals(2, original.size)
        assertFalse(LibraryChoices(added = next).showsOnHome("com.example.phone", tvApp = false, hidden = false))
    }

    @Test
    fun choices_round_trip_in_stable_order_including_an_empty_list() {
        val apps = setOf("org.z.example", "com.a.example")

        assertEquals("com.a.example\norg.z.example\n", encodeChoices(apps))
        assertEquals(apps, decodeChoices(encodeChoices(apps)))
        assertEquals(emptySet<String>(), decodeChoices(""))
    }

    @Test
    fun duplicate_and_malformed_records_are_rejected() {
        for (raw in listOf(
            "com.example\ncom.example\n",
            "com..example\n",
            "com.example\n\n",
            "../file\n",
            "9bad.app\n",
            "com.example\ninvalid/name\n",
        )) {
            try {
                decodeChoices(raw)
                fail(raw)
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun a_package_name_is_dot_separated_words_that_do_not_start_with_a_digit() {
        assertTrue(isValidPackage("com.example.app_2"))
        assertTrue(isValidPackage("single"))
        for (bad in listOf("", ".a", "a.", "a..b", "a.9b", "a b", "a/b", "a-b", "x".repeat(256))) assertFalse(bad, isValidPackage(bad))
        assertFalse(isValidPackage(null))
    }

    @Test
    fun a_full_list_allows_removal_and_an_existing_choice_but_not_another_app() {
        val apps = (0 until MAX_ADDED).map { "com.example.app$it" }.toSet()

        assertEquals(apps, changeChoices(apps, "com.example.app0", added = true))
        assertEquals(MAX_ADDED - 1, changeChoices(apps, "com.example.app0", added = false).size)
        try {
            changeChoices(apps, "com.example.extra", added = true)
            fail()
        } catch (expected: IllegalArgumentException) {
            // expected
        }
        assertEquals(MAX_ADDED, apps.size)
    }

    @Test
    fun a_file_with_more_than_the_most_packages_or_bytes_is_rejected() {
        val tooMany = (0..MAX_ADDED).joinToString("") { "com.example.app$it\n" }
        try {
            decodeChoices(tooMany)
            fail()
        } catch (expected: IllegalArgumentException) {
            // expected
        }
        try {
            decodeChoices("a".repeat(MAX_ADDED * 256 + 1))
            fail()
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
