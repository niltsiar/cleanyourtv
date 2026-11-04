package dev.niltsiar.tvhome.data.wallpaper

import dev.niltsiar.tvhome.core.config.Place
import dev.niltsiar.tvhome.core.config.WallpaperMood
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * wallpaper_moods: which group of a rotating wallpaper collection fits the time of day. Morning from sunrise to noon,
 * evening until half an hour after sunset, night the rest. The sun is the one at the theme's place, worked out offline
 * by [Sun], or 07:00 and 18:30 without a place. A group that is missing, or has none of the collection's pictures,
 * falls back to the whole collection.
 */
internal object WallpaperMoods {
    /** Sunrise and sunset without a place (or where the sun does not rise or set that day). */
    val DEFAULT_SUN: SunTimes = SunTimes(sunrise = 7 * 60, sunset = 18 * 60 + 30)
    private const val NOON = 12 * 60

    /** Evening lasts this long after sunset, while the sky still glows. */
    private const val DUSK_MINUTES = 30

    /** The mood at this minute of the day, for that day's [sun]. */
    fun timeOfDay(
        minute: Int,
        sun: SunTimes,
    ): WallpaperMood =
        when {
            minute >= sun.sunrise && minute < NOON -> WallpaperMood.Morning
            minute >= NOON && minute < sun.sunset + DUSK_MINUTES -> WallpaperMood.Evening
            else -> WallpaperMood.Night
        }

    /** That day's sunrise and sunset at [place]; the defaults without one. */
    fun sun(
        day: LocalDate,
        place: Place?,
        zone: ZoneId,
    ): SunTimes = place?.let { Sun.riseAndSet(day, it.latitude, it.longitude, zone) } ?: DEFAULT_SUN

    /** The pictures in the mood's group, in the collection's order; all of them when the group has none. */
    fun select(
        all: List<File>,
        groups: Map<WallpaperMood, Set<String>>,
        mood: WallpaperMood,
    ): List<File> {
        val names = groups[mood] ?: return all
        return all.filter { it.name in names }.ifEmpty { all }
    }
}
