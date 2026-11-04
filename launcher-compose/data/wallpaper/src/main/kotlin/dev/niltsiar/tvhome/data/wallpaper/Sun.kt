package dev.niltsiar.tvhome.data.wallpaper

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** Sunrise and sunset in minutes of the local day. */
internal data class SunTimes(
    val sunrise: Int,
    val sunset: Int,
)

/**
 * Sunrise and sunset for a place, computed offline with NOAA's general solar position equations (accurate to a few
 * minutes, which is plenty for a backdrop). No network and no location permission: the place comes from the theme.
 */
internal object Sun {
    /**
     * The times on [date] at [latitude] and [longitude], or null when the sun does not rise or does not set that day
     * (polar night or midnight sun).
     */
    fun riseAndSet(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
    ): SunTimes? {
        val g = 2 * Math.PI / 365 * (date.dayOfYear - 1)
        val eqTime =
            229.18 *
                (
                    0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
                        0.014615 * cos(2 * g) - 0.040849 * sin(2 * g)
                )
        val decl =
            0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) -
                0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
                0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val lat = Math.toRadians(latitude)
        // 90.833 degrees: the sun's upper edge on the horizon, with refraction.
        val cosHa = cos(Math.toRadians(90.833)) / (cos(lat) * cos(decl)) - tan(lat) * tan(decl)
        if (cosHa > 1 || cosHa < -1) return null
        val ha = Math.toDegrees(acos(cosHa))
        val offset = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 60
        return SunTimes(
            sunrise = (720 - 4 * (longitude + ha) - eqTime + offset).roundToInt(),
            sunset = (720 - 4 * (longitude - ha) - eqTime + offset).roundToInt(),
        )
    }
}
