package dev.niltsiar.tvhome.data.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileImagesTest {
    @Test
    fun wide_banners_are_kept() {
        assertTrue(isBannerShaped(320, 180))
        assertTrue(isBannerShaped(1920, 1080))
    }

    @Test
    fun square_and_tall_pictures_are_not_banners() {
        assertFalse(isBannerShaped(64, 64))
        assertFalse(isBannerShaped(256, 256))
        assertFalse(isBannerShaped(100, 160))
    }

    @Test
    fun a_drawable_without_a_size_still_fills_the_tile() {
        assertTrue(isBannerShaped(-1, -1))
        assertTrue(isBannerShaped(0, 0))
    }

    @Test
    fun only_banner_names_pick_a_bundled_drawable() {
        assertTrue(isBundledBannerName("banner_netflix"))
        assertFalse(isBundledBannerName("ic_launcher"))
        assertFalse(isBundledBannerName(null))
    }

    @Test
    fun the_tile_width_is_the_cell_less_room_for_the_focus_zoom_up_to_the_design_size() {
        assertEquals(360, tileWidthPx(innerWidthPx = 1920, columns = 4, density = 2f))
        assertEquals(269, tileWidthPx(innerWidthPx = 1920, columns = 6, density = 2f))
    }

    @Test
    fun a_tile_never_gets_narrower_than_the_smallest_size() {
        assertEquals(160, tileWidthPx(innerWidthPx = 400, columns = 8, density = 2f))
    }

    @Test
    fun an_unknown_grid_width_gives_the_design_size() {
        assertEquals(360, tileWidthPx(innerWidthPx = 0, columns = 6, density = 2f))
    }
}
