package dev.niltsiar.tvhome.data.wallpaper

import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.Place
import dev.niltsiar.tvhome.core.config.WallpaperMood
import io.kotest.matchers.shouldBe
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** wallpaper_moods: the time of day's group and the pictures it picks from. */
val WallpaperMoodsTest by testSuite {
    val sun = SunTimes(sunrise = 7 * 60 + 20, sunset = 18 * 60 + 20)

    fun at(
        hour: Int,
        minute: Int,
    ) = WallpaperMoods.timeOfDay(hour * 60 + minute, sun)

    fun files(vararg names: String) = names.map { File("/walls", it) }

    fun names(files: List<File>) = files.map { it.name }

    test("time of day follows sunrise noon and dusk") {
        at(0, 30) shouldBe WallpaperMood.Night
        at(7, 19) shouldBe WallpaperMood.Night
        at(7, 20) shouldBe WallpaperMood.Morning
        at(11, 59) shouldBe WallpaperMood.Morning
        at(12, 0) shouldBe WallpaperMood.Evening
        at(18, 49) shouldBe WallpaperMood.Evening // half an hour after sunset is still evening
        at(18, 50) shouldBe WallpaperMood.Night
        at(23, 59) shouldBe WallpaperMood.Night
    }

    test("the sun comes from the configured place or the defaults") {
        val day = LocalDate.of(2026, 10, 22)
        val madrid = ZoneId.of("Europe/Madrid")
        val place = Place(40.4168, -3.7038)
        WallpaperMoods.sun(day, place, madrid) shouldBe Sun.riseAndSet(day, 40.4168, -3.7038, madrid)
        WallpaperMoods.sun(day, null, madrid) shouldBe SunTimes(7 * 60, 18 * 60 + 30)
        // the sun does not set at the pole in June: the defaults
        WallpaperMoods.sun(LocalDate.of(2026, 6, 21), Place(89.9, 0.0), ZoneId.of("UTC")) shouldBe WallpaperMoods.DEFAULT_SUN
    }

    test("select keeps the collections order and falls back to all") {
        val all = files("a.jpg", "b.jpg", "c.jpg", "d.jpg")
        val groups =
            mapOf(
                WallpaperMood.Night to setOf("d.jpg", "a.jpg"),
                WallpaperMood.Morning to setOf("gone.jpg"),
            )
        names(WallpaperMoods.select(all, groups, WallpaperMood.Night)) shouldBe listOf("a.jpg", "d.jpg")
        WallpaperMoods.select(all, groups, WallpaperMood.Morning) shouldBe all // none of its pictures here
        WallpaperMoods.select(all, groups, WallpaperMood.Evening) shouldBe all // no group at all
        WallpaperMoods.select(all, emptyMap(), WallpaperMood.Night) shouldBe all
    }
}
