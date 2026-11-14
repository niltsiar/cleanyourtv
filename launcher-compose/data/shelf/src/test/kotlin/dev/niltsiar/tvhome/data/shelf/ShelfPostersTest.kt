package dev.niltsiar.tvhome.data.shelf

import android.graphics.Bitmap
import android.net.Uri
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** The posters of Home's shelf cards: which URIs are read, the size they decode at, and the cache. */
val ShelfPostersTest by testSuite {
    robolectricTestSuite<ShelfPostersTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class ShelfPostersTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app = RuntimeEnvironment.getApplication()
                val posters = ShelfPosters(app, Dispatchers.Unconfined)
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
        }
    })
