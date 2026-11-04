package dev.niltsiar.tvhome.data.wallpaper

import dev.niltsiar.tvhome.core.config.Place
import dev.niltsiar.tvhome.core.config.WallpaperMood
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** wallpaper_moods: the time of day's group and the pictures it picks from. */
class WallpaperMoodsTest {
    private val sun = SunTimes(sunrise = 7 * 60 + 20, sunset = 18 * 60 + 20)

    private fun at(
        hour: Int,
        minute: Int,
    ) = WallpaperMoods.timeOfDay(hour * 60 + minute, sun)

    @Test
    fun time_of_day_follows_sunrise_noon_and_dusk() {
        assertEquals(WallpaperMood.Night, at(0, 30))
        assertEquals(WallpaperMood.Night, at(7, 19))
        assertEquals(WallpaperMood.Morning, at(7, 20))
        assertEquals(WallpaperMood.Morning, at(11, 59))
        assertEquals(WallpaperMood.Evening, at(12, 0))
        assertEquals(WallpaperMood.Evening, at(18, 49)) // half an hour after sunset is still evening
        assertEquals(WallpaperMood.Night, at(18, 50))
        assertEquals(WallpaperMood.Night, at(23, 59))
    }

    @Test
    fun the_sun_comes_from_the_configured_place_or_the_defaults() {
        val day = LocalDate.of(2026, 10, 22)
        val madrid = ZoneId.of("Europe/Madrid")
        val place = Place(40.4168, -3.7038)
        assertEquals(Sun.riseAndSet(day, 40.4168, -3.7038, madrid), WallpaperMoods.sun(day, place, madrid))
        assertEquals(SunTimes(7 * 60, 18 * 60 + 30), WallpaperMoods.sun(day, null, madrid))
        // the sun does not set at the pole in June: the defaults
        assertEquals(WallpaperMoods.DEFAULT_SUN, WallpaperMoods.sun(LocalDate.of(2026, 6, 21), Place(89.9, 0.0), ZoneId.of("UTC")))
    }

    private fun files(vararg names: String) = names.map { File("/walls", it) }

    private fun names(files: List<File>) = files.map { it.name }

    @Test
    fun select_keeps_the_collections_order_and_falls_back_to_all() {
        val all = files("a.jpg", "b.jpg", "c.jpg", "d.jpg")
        val groups =
            mapOf(
                WallpaperMood.Night to setOf("d.jpg", "a.jpg"),
                WallpaperMood.Morning to setOf("gone.jpg"),
            )
        assertEquals(listOf("a.jpg", "d.jpg"), names(WallpaperMoods.select(all, groups, WallpaperMood.Night)))
        assertEquals(all, WallpaperMoods.select(all, groups, WallpaperMood.Morning)) // none of its pictures here
        assertEquals(all, WallpaperMoods.select(all, groups, WallpaperMood.Evening)) // no group at all
        assertEquals(all, WallpaperMoods.select(all, emptyMap(), WallpaperMood.Night))
    }
}
