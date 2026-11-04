package dev.niltsiar.tvhome.data.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperCaptionsTest {
    @Test
    fun reads_filename_tab_name() {
        val names =
            WallpaperCaptions.parse(
                listOf("001.jpg\tRed Spider Nebula · Hubble", "002.jpg\t  Falcon 9 at sunset  "),
            )
        assertEquals("Red Spider Nebula · Hubble", names["001.jpg"])
        assertEquals("Falcon 9 at sunset", names["002.jpg"])
    }

    @Test
    fun skips_comments_blank_and_malformed_lines() {
        val names =
            WallpaperCaptions.parse(
                listOf("# captions for the collection", "", "   ", "no-tab-here.jpg Dusk", "\tno file", "003.jpg\t", null),
            )
        assertTrue(names.isEmpty())
    }

    @Test
    fun cuts_very_long_names() {
        val longName = "word ".repeat(30)
        val names = WallpaperCaptions.parse(listOf("004.jpg\t$longName"))
        assertTrue(names["004.jpg"]!!.length <= WallpaperCaptions.MAX_LENGTH)
    }

    @Test
    fun later_line_wins_for_the_same_file() {
        val names = WallpaperCaptions.parse(listOf("005.jpg\tOld", "005.jpg\tNew"))
        assertEquals("New", names["005.jpg"])
        assertFalse(names.containsKey("006.jpg"))
    }

    @Test
    fun missing_folder_has_no_names() {
        assertTrue(WallpaperCaptions.read(null).isEmpty())
    }
}
