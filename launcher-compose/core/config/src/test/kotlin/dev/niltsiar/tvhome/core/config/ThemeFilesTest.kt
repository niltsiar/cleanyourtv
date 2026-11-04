package dev.niltsiar.tvhome.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeFilesTest {
    @Test
    fun theme_files_are_allowed() {
        assertEquals("config.json", ThemeFiles.path(listOf("config.json")))
        assertEquals("wallpaper.jpg", ThemeFiles.path(listOf("wallpaper.jpg")))
        assertEquals("theme_revision", ThemeFiles.path(listOf("theme_revision")))
    }

    @Test
    fun collection_images_are_allowed() {
        assertEquals("wallpapers/dusk.jpg", ThemeFiles.path(listOf("wallpapers", "dusk.jpg")))
        assertEquals("wallpapers/Night_2.WEBP", ThemeFiles.path(listOf("wallpapers", "Night_2.WEBP")))
    }

    @Test
    fun the_collections_captions_are_allowed() {
        assertEquals("wallpapers/captions.txt", ThemeFiles.path(listOf("wallpapers", "captions.txt")))
        assertNull(ThemeFiles.path(listOf("captions.txt")))
        assertNull(ThemeFiles.path(listOf("wallpapers", "notes.txt")))
        assertFalse(ThemeFiles.collectionImage("captions.txt"))
    }

    @Test
    fun anything_else_is_refused() {
        assertNull(ThemeFiles.path(null))
        assertNull(ThemeFiles.path(emptyList()))
        assertNull(ThemeFiles.path(listOf("cards.json")))
        assertNull(ThemeFiles.path(listOf("..")))
        assertNull(ThemeFiles.path(listOf("..", "config.json")))
        assertNull(ThemeFiles.path(listOf("wallpapers", "..")))
        assertNull(ThemeFiles.path(listOf("wallpapers", "run.sh")))
        assertNull(ThemeFiles.path(listOf("wallpapers", "a b.jpg")))
        assertNull(ThemeFiles.path(listOf("wallpapers", "../config.jpg")))
        assertNull(ThemeFiles.path(listOf("wallpapers", ".jpg")))
        assertNull(ThemeFiles.path(listOf("other", "dusk.jpg")))
        assertNull(ThemeFiles.path(listOf("wallpapers", "sub", "dusk.jpg")))
    }

    @Test
    fun only_the_bare_folder_is_the_collection() {
        assertTrue(ThemeFiles.isCollection(listOf("wallpapers")))
        assertFalse(ThemeFiles.isCollection(listOf("wallpapers", "dusk.jpg")))
        assertFalse(ThemeFiles.isCollection(listOf("config.json")))
    }
}
