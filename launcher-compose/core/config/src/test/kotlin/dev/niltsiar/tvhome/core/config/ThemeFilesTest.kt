package dev.niltsiar.tvhome.core.config

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val ThemeFilesTest by testSuite {
    test("theme files are allowed") {
        ThemeFiles.path(listOf("config.json")) shouldBe "config.json"
        ThemeFiles.path(listOf("wallpaper.jpg")) shouldBe "wallpaper.jpg"
        ThemeFiles.path(listOf("theme_revision")) shouldBe "theme_revision"
    }

    test("collection images are allowed") {
        ThemeFiles.path(listOf("wallpapers", "dusk.jpg")) shouldBe "wallpapers/dusk.jpg"
        ThemeFiles.path(listOf("wallpapers", "Night_2.WEBP")) shouldBe "wallpapers/Night_2.WEBP"
    }

    test("the collections captions are allowed") {
        ThemeFiles.path(listOf("wallpapers", "captions.txt")) shouldBe "wallpapers/captions.txt"
        ThemeFiles.path(listOf("captions.txt")).shouldBeNull()
        ThemeFiles.path(listOf("wallpapers", "notes.txt")).shouldBeNull()
        ThemeFiles.collectionImage("captions.txt").shouldBeFalse()
    }

    test("anything else is refused") {
        ThemeFiles.path(null).shouldBeNull()
        ThemeFiles.path(emptyList()).shouldBeNull()
        ThemeFiles.path(listOf("cards.json")).shouldBeNull()
        ThemeFiles.path(listOf("..")).shouldBeNull()
        ThemeFiles.path(listOf("..", "config.json")).shouldBeNull()
        ThemeFiles.path(listOf("wallpapers", "..")).shouldBeNull()
        ThemeFiles.path(listOf("wallpapers", "run.sh")).shouldBeNull()
        ThemeFiles.path(listOf("wallpapers", "a b.jpg")).shouldBeNull()
        ThemeFiles.path(listOf("wallpapers", "../config.jpg")).shouldBeNull()
        ThemeFiles.path(listOf("wallpapers", ".jpg")).shouldBeNull()
        ThemeFiles.path(listOf("other", "dusk.jpg")).shouldBeNull()
        ThemeFiles.path(listOf("wallpapers", "sub", "dusk.jpg")).shouldBeNull()
    }

    test("only the bare folder is the collection") {
        ThemeFiles.isCollection(listOf("wallpapers")).shouldBeTrue()
        ThemeFiles.isCollection(listOf("wallpapers", "dusk.jpg")).shouldBeFalse()
        ThemeFiles.isCollection(listOf("config.json")).shouldBeFalse()
    }
}
