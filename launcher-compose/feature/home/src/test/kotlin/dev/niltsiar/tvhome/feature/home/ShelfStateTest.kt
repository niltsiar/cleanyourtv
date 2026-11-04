package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.model.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class ShelfStateTest {
    private val apps = NineApps.mapIndexed { i, app -> app.copy(accent = Color(0xFF000000 or (i + 1L) * 0x111111)) }

    private fun shelf(
        focusedKey: String?,
        cinematic: Boolean = true,
        lastOpened: Map<String, Long> = emptyMap(),
        now: LocalDateTime? = Evening,
        list: List<AppEntry> = apps,
    ) = homeUiState(
        homeApps(list),
        now,
        focusedKey,
        Locale.ENGLISH,
        cinematic = cinematic,
        lastOpened = lastOpened,
        zone = ZoneOffset.UTC,
    ).shelf

    @Test
    fun without_cinematic_home_there_is_no_shelf() {
        assertNull(shelf("pkg.2", cinematic = false))
    }

    @Test
    fun a_focused_dock_app_fills_the_shelf_with_its_label_colour_and_place_among_the_dots() {
        val shelf = shelf("pkg.3")!!

        assertEquals("pkg.3", shelf.key)
        assertEquals("App 3", shelf.label)
        assertEquals(apps[2].accent, shelf.accent)
        assertEquals(2, shelf.position)
        assertEquals(6, shelf.count)
    }

    @Test
    fun a_grid_app_has_no_shelf() {
        assertNull(shelf("pkg.7"))
    }

    @Test
    fun before_any_focus_or_with_a_gone_app_the_first_dock_app_is_shown() {
        assertEquals("pkg.1", shelf(null)!!.key)
        assertEquals("pkg.1", shelf("pkg.gone")!!.key)
    }

    @Test
    fun a_dock_with_fewer_apps_has_one_dot_per_app() {
        assertEquals(3, shelf("pkg.2", list = apps.take(3))!!.count)
    }

    @Test
    fun the_last_opened_line_is_the_relative_time_of_that_tile_only() {
        val now = Evening.toEpochSecond(ZoneOffset.UTC) * 1000L
        val fiveMinutesAgo = now - 5 * 60_000L

        assertEquals(
            OpenedAt(fiveMinutesAgo, now, hour24 = true),
            shelf("pkg.2", lastOpened = mapOf("pkg.2" to fiveMinutesAgo))!!.lastOpened,
        )
        assertNull(shelf("pkg.3", lastOpened = mapOf("pkg.2" to fiveMinutesAgo))!!.lastOpened)
    }

    @Test
    fun the_last_opened_line_waits_for_a_valid_time() {
        assertNull(shelf("pkg.2", lastOpened = mapOf("pkg.2" to 1_000L), now = null)!!.lastOpened)
    }
}
