package dev.niltsiar.tvhome.data.wallpaper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperSizeTest {
    @Test
    fun very_wide_picture_is_refused() {
        assertFalse(WallpaperSize.accepted(65535, 2159))
    }

    @Test
    fun four_k_picture_is_accepted() {
        assertTrue(WallpaperSize.accepted(3840, 2160))
        assertTrue(WallpaperSize.accepted(1920, 1080))
    }

    @Test
    fun unreadable_picture_is_refused() {
        assertFalse(WallpaperSize.accepted(-1, -1))
        assertFalse(WallpaperSize.accepted(0, 1080))
    }

    @Test
    fun four_k_on_1080p_samples_by_two() {
        assertEquals(2, WallpaperSize.sampleSize(3840, 2160, 1920, 1080))
        assertNull(WallpaperSize.coverScale(1920, 1080, 1920, 1080))
    }

    @Test
    fun photo_between_sample_sizes_is_scaled_to_the_screen() {
        // 3000x2000 keeps sample size 1; the cover is limited by the width, so it decodes 1920 wide.
        assertEquals(1, WallpaperSize.sampleSize(3000, 2000, 1920, 1080))
        assertArrayEquals(intArrayOf(3000, 1920), WallpaperSize.coverScale(3000, 2000, 1920, 1080))
    }

    @Test
    fun wide_picture_is_scaled_by_its_height() {
        assertArrayEquals(intArrayOf(2000, 1080), WallpaperSize.coverScale(4000, 2000, 1920, 1080))
    }

    @Test
    fun small_picture_is_not_scaled() {
        assertEquals(1, WallpaperSize.sampleSize(1280, 720, 1920, 1080))
        assertNull(WallpaperSize.coverScale(1280, 720, 1920, 1080))
    }

    @Test
    fun a_phone_sized_photo_is_accepted_once_sampled_down() {
        val s = WallpaperSize.sampleSize(4032, 3024, 1920, 1080)
        assertTrue(WallpaperSize.accepted(4032 / s, 3024 / s))
        val t = WallpaperSize.sampleSize(65535, 2159, 1920, 1080)
        assertFalse(WallpaperSize.accepted(65535 / t, 2159 / t))
    }
}
