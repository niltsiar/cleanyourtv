package dev.niltsiar.tvhome.data.shelf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** The posters of Home's shelf cards: which URIs are read, the size they decode at, and the cache. */
val ShelfPostersTest by testSuite {
    robolectricTestSuite<ShelfPostersTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

/** The decode options of a poster: its sample, its last scale, and the pixel cap. */
val PosterOptionsTest by testSuite {
    /** The sample (0 means 1), and the density pair of the last scale, 0 for none. */
    fun BitmapFactory.Options.scale() = Triple(inSampleSize.coerceAtLeast(1), inDensity, inTargetDensity)

    test("a 1920 x 1080 poster for a 480 x 270 card samples by 4 and needs no scale") {
        posterOptions(1920, 1080, 480, 270).shouldNotBeNull().scale() shouldBe Triple(4, 0, 0)
    }

    test("an odd size samples by 2 and scales the 500 sampled pixels of its width to 480") {
        posterOptions(1001, 563, 480, 270).shouldNotBeNull().scale() shouldBe Triple(2, 500, 480)
    }

    test("a 2 by 3 poster for a 480 x 270 card covers it at 480 x 720, under the cap") {
        posterOptions(1000, 1500, 480, 270).shouldNotBeNull().scale() shouldBe Triple(2, 500, 480)
    }

    test("a 16:9 poster for a 960 x 540 card samples to 1919 x 1079, under the cap") {
        posterOptions(3838, 2158, 960, 540).shouldNotBeNull().scale() shouldBe Triple(2, 1919, 960)
    }

    test("a poster no bigger than the card keeps its size, even at 1:100 and 1:10000") {
        posterOptions(320, 180, 480, 270).shouldNotBeNull().scale() shouldBe Triple(1, 0, 0)
        posterOptions(2700, 27, 480, 270).shouldNotBeNull().scale() shouldBe Triple(1, 0, 0)
        posterOptions(10_000, 1, 480, 270).shouldNotBeNull().scale() shouldBe Triple(1, 0, 0)
    }

    test("a 1:100 poster that only its short side would shrink is over the cap and not decoded") {
        posterOptions(27_000, 270, 480, 270).shouldBeNull()
        posterOptions(54_000, 540, 480, 270).shouldBeNull()
        posterOptions(270, 27_000, 480, 270).shouldBeNull()
    }

    test("a 1:10000 poster is over the cap and not decoded") {
        posterOptions(2_700_000, 270, 480, 270).shouldBeNull()
        posterOptions(5_400_000, 540, 480, 270).shouldBeNull()
    }

    test("a picture with no size is not decoded") {
        posterOptions(-1, -1, 480, 270).shouldBeNull()
        posterOptions(0, 270, 480, 270).shouldBeNull()
    }
}

class ShelfPostersTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app = RuntimeEnvironment.getApplication()
                val posters = ShelfPosters(app, MutableStateFlow(TvConfig()), Dispatchers.Unconfined)
                val opened = HashMap<String, Int>()

                fun serve(
                    uri: String,
                    bytes: ByteArray,
                ) {
                    shadowOf(app.contentResolver).registerInputStreamSupplier(Uri.parse(uri)) {
                        opened.merge(uri, 1, Int::plus)
                        ByteArrayInputStream(bytes)
                    }
                }

                fun png(
                    width: Int,
                    height: Int,
                ): ByteArray {
                    val out = ByteArrayOutputStream()
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out)
                    return out.toByteArray()
                }

                fun poster(
                    uri: String,
                    width: Int = 480,
                    height: Int = 270,
                ) = runBlocking { posters.poster(uri, width, height) }
            }
        } asContextForEach {
            test("a 1920 x 1080 content poster decodes at the card's 480 x 270 pixels") {
                serve("content://app.posters/1", png(1920, 1080))

                val poster = poster("content://app.posters/1").shouldNotBeNull()

                (poster.width to poster.height) shouldBe (480 to 270)
            }

            test("a 4 by 3 poster decodes at the least size that covers the card, 480 x 360") {
                serve("content://app.posters/2", png(1600, 1200))

                val poster = poster("content://app.posters/2").shouldNotBeNull()

                (poster.width to poster.height) shouldBe (480 to 360)
            }

            test("a poster smaller than the card keeps its size") {
                serve("content://app.posters/3", png(320, 180))

                val poster = poster("content://app.posters/3").shouldNotBeNull()

                (poster.width to poster.height) shouldBe (320 to 180)
            }

            test("an https poster is never opened") {
                serve("https://i.ytimg.com/vi/x/hqdefault.jpg", png(480, 270))

                poster("https://i.ytimg.com/vi/x/hqdefault.jpg").shouldBeNull()

                opened.size shouldBe 0
            }

            test("a poster the app does not let Home read, or one that does not decode, gives none") {
                shadowOf(app.contentResolver).registerInputStreamSupplier(Uri.parse("content://app.posters/private")) {
                    throw SecurityException("Permission Denial")
                }
                serve("content://app.posters/broken", "not a picture".toByteArray())

                poster("content://app.posters/private").shouldBeNull()
                poster("content://app.posters/broken").shouldBeNull()
            }

            test("a poster asked for again at the same size comes from the cache") {
                serve("content://app.posters/4", png(1920, 1080))

                poster("content://app.posters/4").shouldNotBeNull()
                val reads = opened.getValue("content://app.posters/4")
                poster("content://app.posters/4").shouldNotBeNull()

                opened.getValue("content://app.posters/4") shouldBe reads
            }

            test("a poster over the pixel cap gives none, and only its size is read") {
                serve("content://app.posters/wide", png(14_500, 145))

                poster("content://app.posters/wide").shouldBeNull()

                opened.getValue("content://app.posters/wide") shouldBe 1
            }

            test("a poster that failed is not read again until home comes to the front") {
                serve("content://app.posters/broken", "not a picture".toByteArray())
                poster("content://app.posters/broken").shouldBeNull()
                val reads = opened.getValue("content://app.posters/broken")

                poster("content://app.posters/broken").shouldBeNull()
                opened.getValue("content://app.posters/broken") shouldBe reads

                posters.onStart()
                poster("content://app.posters/broken").shouldBeNull()
                opened.getValue("content://app.posters/broken") shouldBe reads * 2
            }

            test("trim drops the decoded posters, which the next ask reads again") {
                serve("content://app.posters/5", png(480, 270))
                poster("content://app.posters/5").shouldNotBeNull()
                val reads = opened.getValue("content://app.posters/5")

                posters.trim()
                poster("content://app.posters/5").shouldNotBeNull()

                opened.getValue("content://app.posters/5") shouldBe reads * 2
            }

            test("past 12 MB of posters the one asked for longest ago leaves the cache") {
                // 24 posters of 480 x 270 at 4 bytes each fit in 12 MB; the 25th pushes the first out.
                val uris = (1..25).map { "content://app.posters/lru/$it" }
                val picture = png(480, 270)
                uris.forEach { uri ->
                    serve(uri, picture)
                    poster(uri).shouldNotBeNull()
                }
                val reads = opened.toMap()

                poster(uris.last()).shouldNotBeNull()
                poster(uris.first()).shouldNotBeNull()

                opened.getValue(uris.last()) shouldBe reads.getValue(uris.last())
                opened.getValue(uris.first()) shouldBe reads.getValue(uris.first()) * 2
            }

            test("the ten card posters of two apps all stay in the cache") {
                val uris = (1..20).map { "content://app${it % 2}.posters/$it" }
                val picture = png(1920, 1080)
                uris.forEach { uri ->
                    serve(uri, picture)
                    poster(uri).shouldNotBeNull()
                }
                val reads = opened.toMap()

                uris.forEach { poster(it).shouldNotBeNull() }

                opened shouldBe reads
            }
        }
    })
