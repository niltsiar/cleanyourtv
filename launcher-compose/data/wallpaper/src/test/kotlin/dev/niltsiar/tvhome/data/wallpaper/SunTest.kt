package dev.niltsiar.tvhome.data.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Sunrise/sunset maths and the sky's stretch onto a real day. */
class SunTest {
    private val vilnius = ZoneId.of("Europe/Vilnius")
    private val svalbard = ZoneId.of("Arctic/Longyearbyen")

    @Test
    fun vilnius_winter_day_is_short_and_late() {
        val t = Sun.riseAndSet(LocalDate.of(2026, 12, 21), 54.69, 25.28, vilnius)
        assertNotNull(t)
        assertTrue("sunrise ${t!!.sunrise}", t.sunrise in (8 * 60 + 31)..(9 * 60 - 1)) // about 08:40
        assertTrue("sunset ${t.sunset}", t.sunset in (15 * 60 + 41)..(16 * 60 + 4)) // about 15:55
    }

    @Test
    fun vilnius_summer_day_is_long_in_local_summer_time() {
        val t = Sun.riseAndSet(LocalDate.of(2026, 6, 21), 54.69, 25.28, vilnius)
        assertNotNull(t)
        assertTrue("sunrise ${t!!.sunrise}", t.sunrise in (4 * 60 + 31)..(4 * 60 + 54)) // about 04:42
        assertTrue("sunset ${t.sunset}", t.sunset in (21 * 60 + 51)..(22 * 60 + 9)) // about 22:00
    }

    @Test
    fun no_sunrise_or_sunset_at_the_poles() {
        assertNull(Sun.riseAndSet(LocalDate.of(2026, 12, 21), 78.2, 15.6, svalbard))
        assertNull(Sun.riseAndSet(LocalDate.of(2026, 6, 21), 78.2, 15.6, svalbard))
    }

    @Test
    fun sky_glows_land_on_the_real_sunrise_and_sunset() {
        val sunrise = 8 * 60 + 40
        val sunset = 15 * 60 + 55
        assertEquals(Sky.at(Sky.REF_SUNRISE).sun, Sky.at(sunrise, sunrise, sunset).sun)
        assertEquals(Sky.at(Sky.REF_SUNSET).bottom, Sky.at(sunset, sunrise, sunset).bottom)
        assertEquals(Sky.at(0).top, Sky.at(0, sunrise, sunset).top)
        assertEquals(Sky.at(1440).top, Sky.at(1440, sunrise, sunset).top)
        // Five o'clock on that winter day is past sunset: the glow has gone down on the right,
        // where the reference day still has the sun in the afternoon sky.
        assertTrue(Sky.at(17 * 60, sunrise, sunset).sunX > Sky.at(17 * 60).sunX + 0.1f)
    }

    @Test
    fun the_reference_day_maps_onto_itself() {
        for (m in 0..1440 step 30) {
            assertEquals(Sky.at(m).bottom, Sky.at(m, Sky.REF_SUNRISE, Sky.REF_SUNSET).bottom)
        }
    }

    @Test
    fun nonsense_sun_times_fall_back_to_the_reference_day() {
        assertEquals(Sky.at(600).bottom, Sky.at(600, 900, 800).bottom)
        assertEquals(Sky.at(600).bottom, Sky.at(600, 0, 1440).bottom)
    }
}
