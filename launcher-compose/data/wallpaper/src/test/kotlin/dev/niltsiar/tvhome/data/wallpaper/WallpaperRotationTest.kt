package dev.niltsiar.tvhome.data.wallpaper

import dev.niltsiar.tvhome.core.config.WallpaperOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

class WallpaperRotationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun touch(
        name: String,
        bytes: Int,
    ) = folder.newFile(name).also { it.writeBytes(ByteArray(bytes)) }

    private fun files(vararg names: String) = names.map { File(it) }

    @Test
    fun images_keeps_pictures_only_sorted_by_name() {
        touch("b.JPG", 10)
        touch("a.png", 10)
        touch("notes.txt", 10)
        touch("empty.jpg", 0)
        folder.newFolder("folder.jpg")
        assertEquals(listOf("a.png", "b.JPG"), WallpaperRotation.images(folder.root).map { it.name })
        assertTrue(WallpaperRotation.images(null).isEmpty())
        assertTrue(WallpaperRotation.images(File(folder.root, "missing")).isEmpty())
    }

    @Test
    fun pick_avoids_the_previous_picture_when_there_is_another() {
        val images = files("a.jpg", "b.jpg")
        val random = Random(1)
        repeat(20) { assertNotEquals("a.jpg", WallpaperRotation.pick(images, "a.jpg", random)!!.name) }
        assertEquals("a.jpg", WallpaperRotation.pick(files("a.jpg"), "a.jpg", random)!!.name)
        assertNull(WallpaperRotation.pick(emptyList(), "", random))
    }

    @Test
    fun sequential_advances_in_sorted_order_and_wraps() {
        val images = files("a.jpg", "b.jpg", "c.jpg")
        assertEquals("b.jpg", WallpaperRotation.next(images, "a.jpg")!!.name)
        assertEquals("c.jpg", WallpaperRotation.next(images, "b.jpg")!!.name)
        assertEquals("a.jpg", WallpaperRotation.next(images, "c.jpg")!!.name)
        // A first run, or an image removed from the collection, starts at the first picture.
        assertEquals("a.jpg", WallpaperRotation.next(images, "")!!.name)
        assertEquals("a.jpg", WallpaperRotation.next(images, "gone.jpg")!!.name)
        assertEquals("only.jpg", WallpaperRotation.next(files("only.jpg"), "only.jpg")!!.name)
        assertNull(WallpaperRotation.next(emptyList(), ""))
    }

    @Test
    fun pick_uses_the_requested_order() {
        val images = files("a.jpg", "b.jpg", "c.jpg")
        val random = Random(1)
        assertEquals("b.jpg", WallpaperRotation.pick(images, "a.jpg", WallpaperOrder.Sequential, random)!!.name)
        assertEquals("a.jpg", WallpaperRotation.pick(images, "c.jpg", WallpaperOrder.Sequential, random)!!.name)
        repeat(20) { assertNotEquals("a.jpg", WallpaperRotation.pick(images, "a.jpg", WallpaperOrder.Shuffle, random)!!.name) }
    }

    @Test
    fun finds_the_first_picture_when_the_previous_one_is_gone() {
        assertEquals("a.jpg", WallpaperRotation.next(files("a.jpg", "b.jpg"), "deleted.jpg")!!.name)
    }

    @Test
    fun find_matches_by_name() {
        val images = files("a.jpg", "b.jpg")
        assertEquals("b.jpg", WallpaperRotation.find(images, "b.jpg")!!.name)
        assertNull(WallpaperRotation.find(images, "c.jpg"))
    }

    @Test
    fun due_only_with_rotation_on_and_the_interval_passed() {
        val minute = 60_000L
        assertFalse(WallpaperRotation.due(0, 10 * minute, 0)) // rotation off
        assertTrue(WallpaperRotation.due(0, 10 * minute, 5 * minute)) // never changed yet
        assertFalse(WallpaperRotation.due(8 * minute, 10 * minute, 5 * minute))
        assertTrue(WallpaperRotation.due(5 * minute, 10 * minute, 5 * minute))
        assertTrue(WallpaperRotation.due(20 * minute, 10 * minute, 5 * minute)) // clock went back
    }

    @Test
    fun the_on_tv_choice_is_nothing_for_the_theme_to_decide_none_or_one_picture() {
        val collection = files("a.jpg", "b.jpg")
        val single = File("wallpaper.jpg")

        assertNull(WallpaperRotation.chosen(collection, single, null))
        assertEquals(emptyList<File>(), WallpaperRotation.chosen(collection, single, "none"))
        assertEquals(files("b.jpg"), WallpaperRotation.chosen(collection, single, "b.jpg"))
        assertEquals(listOf(single), WallpaperRotation.chosen(collection, single, "wallpaper.jpg"))
        assertNull(WallpaperRotation.chosen(collection, single, "gone.jpg"))
        assertNull(WallpaperRotation.chosen(collection, null, "wallpaper.jpg"))
    }

    @Test
    fun the_picker_offers_the_single_wallpaper_first_when_it_has_content_then_the_folder() {
        val wallpapers = folder.newFolder("wallpapers")
        File(wallpapers, "b.jpg").writeBytes(ByteArray(4))
        File(wallpapers, "a.jpg").writeBytes(ByteArray(4))
        val single = touch("wallpaper.jpg", 4)

        assertEquals(listOf("wallpaper.jpg", "a.jpg", "b.jpg"), WallpaperRotation.offered(wallpapers, single).map { it.name })
        assertEquals(listOf("a.jpg", "b.jpg"), WallpaperRotation.offered(wallpapers, File(folder.root, "missing.jpg")).map { it.name })
        assertEquals(listOf("a.jpg", "b.jpg"), WallpaperRotation.offered(wallpapers, touch("empty.jpg", 0)).map { it.name })
    }
}
