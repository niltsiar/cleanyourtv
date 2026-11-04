package dev.niltsiar.tvhome.data.wallpaper

import dev.niltsiar.tvhome.core.model.SkyPalette
import kotlin.math.roundToInt

/**
 * Time-of-day sky palette for sky_intensity. The caller passes minutes since midnight, so the palette can be tested
 * on the JVM. The colours are interpolated between a handful of anchor points through the day, which is what makes
 * the backdrop drift from night to dawn to sunset instead of switching at fixed hours. Every colour stays dark
 * enough for the white text, the shelf title and the dock to keep their contrast.
 */
internal object Sky {
    /** One anchor: a minute of the day and the values the palette takes exactly there. */
    private class Stop(
        val minute: Int,
        val top: Int,
        val mid: Int,
        val bottom: Int,
        /** Alpha of the sun or moon glow near the horizon, 0 to 255. */
        val sun: Int,
        /** Horizontal place of the glow, 0 = left edge, 1 = right edge. */
        val sunX: Float,
    )

    private const val NIGHT_TOP = 0xFF04060E.toInt()
    private const val NIGHT_MID = 0xFF070C1A.toInt()
    private const val NIGHT_BOTTOM = 0xFF0B1326.toInt()
    private const val GLOW_WARMTH = 0xFFFFD9A0.toInt()

    // Anchors were picked around a temperate evening: full night until half past five, dawn before seven, the warmest
    // glow between seven in the evening and a quarter to eight. With a place set, at(minute, sunrise, sunset)
    // stretches this reference day onto the real one.
    const val REF_SUNRISE: Int = 450
    const val REF_SUNSET: Int = 1140
    private const val DAY = 1440
    private val stops =
        listOf(
            Stop(0, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
            Stop(320, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
            Stop(390, 0xFF160F2E.toInt(), 0xFF3B2352.toInt(), 0xFF8A4A6B.toInt(), 100, 0.24f),
            Stop(450, 0xFF12203F.toInt(), 0xFF5C3A63.toInt(), 0xFFD3674F.toInt(), 160, 0.30f),
            Stop(570, 0xFF0E1A33.toInt(), 0xFF1F3557.toInt(), 0xFF43648B.toInt(), 90, 0.38f),
            Stop(780, 0xFF0B1524.toInt(), 0xFF17304F.toInt(), 0xFF31587F.toInt(), 60, 0.50f),
            Stop(1020, 0xFF0D1526.toInt(), 0xFF20395C.toInt(), 0xFF4C5470.toInt(), 90, 0.62f),
            Stop(1140, 0xFF1B1030.toInt(), 0xFF6C2E52.toInt(), 0xFFE2704A.toInt(), 190, 0.76f),
            Stop(1185, 0xFF140C27.toInt(), 0xFF4A2049.toInt(), 0xFFA54A5C.toInt(), 130, 0.82f),
            Stop(1260, 0xFF0A0A1C.toInt(), 0xFF191230.toInt(), 0xFF3A1F49.toInt(), 60, 0.86f),
            Stop(1350, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
            Stop(DAY, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
        )

    /**
     * The palette at [minuteOfDay] on a day whose sun rises at [sunrise] and sets at [sunset] (minutes of the day):
     * night, day and evening are each stretched piecewise so the reference sunrise and sunset glows land on the real
     * ones. Times that make no day give the reference day.
     */
    fun at(
        minuteOfDay: Int,
        sunrise: Int,
        sunset: Int,
    ): SkyPalette {
        if (sunrise <= 0 || sunset >= DAY || sunrise >= sunset) return at(minuteOfDay)
        val m = minuteOfDay.coerceIn(0, DAY)
        val ref =
            when {
                m < sunrise -> m * REF_SUNRISE / sunrise.toFloat()
                m < sunset -> REF_SUNRISE + (m - sunrise) * (REF_SUNSET - REF_SUNRISE) / (sunset - sunrise).toFloat()
                else -> REF_SUNSET + (m - sunset) * (DAY - REF_SUNSET) / (DAY - sunset).toFloat()
            }
        return at(ref.roundToInt())
    }

    /** The palette at [minuteOfDay], clamped to a day. */
    fun at(minuteOfDay: Int): SkyPalette {
        val m = minuteOfDay.coerceIn(0, DAY)
        val at = stops.indexOfLast { it.minute <= m }.coerceAtMost(stops.size - 2)
        val a = stops[at]
        val b = stops[at + 1]
        val t = if (b.minute == a.minute) 0f else (m - a.minute) / (b.minute - a.minute).toFloat()
        val bottom = lerpColor(a.bottom, b.bottom, t)
        return SkyPalette(
            top = lerpColor(a.top, b.top, t),
            mid = lerpColor(a.mid, b.mid, t),
            bottom = bottom,
            glow = lerpColor(bottom, GLOW_WARMTH, 0.65f),
            sun = lerp(a.sun, b.sun, t),
            sunX = lerp(a.sunX, b.sunX, t),
        )
    }

    /** Minute of day, 0 to 1439, of a wall clock time. */
    fun minuteOfDay(
        hour: Int,
        minute: Int,
    ): Int = hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)

    /** Blends two ARGB colours channel by channel; [t] is 0 to 1. */
    fun lerpColor(
        from: Int,
        to: Int,
        t: Float,
    ): Int {
        val f = t.coerceIn(0f, 1f)

        fun channel(shift: Int): Int {
            val a = (from ushr shift) and 0xFF
            val b = (to ushr shift) and 0xFF
            return (a + (b - a) * f).roundToInt()
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun lerp(
        from: Int,
        to: Int,
        t: Float,
    ): Int = (from + (to - from) * t.coerceIn(0f, 1f)).roundToInt()

    private fun lerp(
        from: Float,
        to: Float,
        t: Float,
    ): Float = from + (to - from) * t.coerceIn(0f, 1f)
}
