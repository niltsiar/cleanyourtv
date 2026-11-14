package dev.niltsiar.tvhome.data.wallpaper

import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.WallpaperOrder
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

val WallpaperRotationTest by testSuite {
    fun files(vararg names: String) = names.map { File(it) }

    testFixture {
        object : JUnit4RulesContext() {
            val folder = rule(TemporaryFolder())

            fun touch(
                name: String,
                bytes: Int,
            ) = folder.newFile(name).also { it.writeBytes(ByteArray(bytes)) }
        }
    } asContextForEach {
        test("images keeps pictures only sorted by name") {
            touch("b.JPG", 10)
            touch("a.png", 10)
            touch("notes.txt", 10)
            touch("empty.jpg", 0)
            folder.newFolder("folder.jpg")
            WallpaperRotation.images(folder.root).map { it.name } shouldBe listOf("a.png", "b.JPG")
            WallpaperRotation.images(null).isEmpty().shouldBeTrue()
            WallpaperRotation.images(File(folder.root, "missing")).isEmpty().shouldBeTrue()
        }

        test("pick avoids the previous picture when there is another") {
            val images = files("a.jpg", "b.jpg")
            val random = Random(1)
            repeat(20) { WallpaperRotation.pick(images, "a.jpg", random)!!.name shouldNotBe "a.jpg" }
            WallpaperRotation.pick(files("a.jpg"), "a.jpg", random)!!.name shouldBe "a.jpg"
            WallpaperRotation.pick(emptyList(), "", random).shouldBeNull()
        }

        test("sequential advances in sorted order and wraps") {
            val images = files("a.jpg", "b.jpg", "c.jpg")
            WallpaperRotation.next(images, "a.jpg")!!.name shouldBe "b.jpg"
            WallpaperRotation.next(images, "b.jpg")!!.name shouldBe "c.jpg"
            WallpaperRotation.next(images, "c.jpg")!!.name shouldBe "a.jpg"
            // A first run, or an image removed from the collection, starts at the first picture.
            WallpaperRotation.next(images, "")!!.name shouldBe "a.jpg"
            WallpaperRotation.next(images, "gone.jpg")!!.name shouldBe "a.jpg"
            WallpaperRotation.next(files("only.jpg"), "only.jpg")!!.name shouldBe "only.jpg"
            WallpaperRotation.next(emptyList(), "").shouldBeNull()
        }

        test("pick uses the requested order") {
            val images = files("a.jpg", "b.jpg", "c.jpg")
            val random = Random(1)
            WallpaperRotation.pick(images, "a.jpg", WallpaperOrder.Sequential, random)!!.name shouldBe "b.jpg"
            WallpaperRotation.pick(images, "c.jpg", WallpaperOrder.Sequential, random)!!.name shouldBe "a.jpg"
            repeat(20) { WallpaperRotation.pick(images, "a.jpg", WallpaperOrder.Shuffle, random)!!.name shouldNotBe "a.jpg" }
        }

        test("finds the first picture when the previous one is gone") {
            WallpaperRotation.next(files("a.jpg", "b.jpg"), "deleted.jpg")!!.name shouldBe "a.jpg"
        }

        test("find matches by name") {
            val images = files("a.jpg", "b.jpg")
            WallpaperRotation.find(images, "b.jpg")!!.name shouldBe "b.jpg"
            WallpaperRotation.find(images, "c.jpg").shouldBeNull()
        }

        test("due only with rotation on and the interval passed") {
            val minute = 60_000L
            WallpaperRotation.due(0, 10 * minute, 0).shouldBeFalse() // rotation off
            WallpaperRotation.due(0, 10 * minute, 5 * minute).shouldBeTrue() // never changed yet
            WallpaperRotation.due(8 * minute, 10 * minute, 5 * minute).shouldBeFalse()
            WallpaperRotation.due(5 * minute, 10 * minute, 5 * minute).shouldBeTrue()
            WallpaperRotation.due(20 * minute, 10 * minute, 5 * minute).shouldBeTrue() // clock went back
        }

        test("the on tv choice is nothing for the theme to decide none or one picture") {
            val collection = files("a.jpg", "b.jpg")
            val single = File("wallpaper.jpg")

            WallpaperRotation.chosen(collection, single, null).shouldBeNull()
            WallpaperRotation.chosen(collection, single, "none") shouldBe emptyList()
            WallpaperRotation.chosen(collection, single, "b.jpg") shouldBe files("b.jpg")
            WallpaperRotation.chosen(collection, single, "wallpaper.jpg") shouldBe listOf(single)
            WallpaperRotation.chosen(collection, single, "gone.jpg").shouldBeNull()
            WallpaperRotation.chosen(collection, null, "wallpaper.jpg").shouldBeNull()
        }

        test("the picker offers the single wallpaper first when it has content then the folder") {
            val wallpapers = folder.newFolder("wallpapers")
            File(wallpapers, "b.jpg").writeBytes(ByteArray(4))
            File(wallpapers, "a.jpg").writeBytes(ByteArray(4))
            val single = touch("wallpaper.jpg", 4)

            WallpaperRotation.offered(wallpapers, single).map { it.name } shouldBe listOf("wallpaper.jpg", "a.jpg", "b.jpg")
            WallpaperRotation.offered(wallpapers, File(folder.root, "missing.jpg")).map { it.name } shouldBe listOf("a.jpg", "b.jpg")
            WallpaperRotation.offered(wallpapers, touch("empty.jpg", 0)).map { it.name } shouldBe listOf("a.jpg", "b.jpg")
        }
    }
}
