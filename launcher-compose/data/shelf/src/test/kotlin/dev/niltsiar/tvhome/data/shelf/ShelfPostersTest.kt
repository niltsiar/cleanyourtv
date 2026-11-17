package dev.niltsiar.tvhome.data.shelf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.Color
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

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

    test("a poster within a factor of 1.2 of the card's aspect ratio is cropped, and one past it on either side is drawn whole") {
        drawsWhole(576, 270, 480, 270) shouldBe false
        drawsWhole(577, 270, 480, 270) shouldBe true
        drawsWhole(400, 270, 480, 270) shouldBe false
        drawsWhole(399, 270, 480, 270) shouldBe true
    }

    test("a 2 by 3 poster for a 480 x 270 card is drawn whole, so it decodes at the 180 x 270 that fits in the card") {
        posterOptions(1000, 1500, 480, 270).shouldNotBeNull().scale() shouldBe Triple(4, 250, 180)
    }

    test("a 16:9 poster for a 960 x 540 card samples to 1919 x 1079, under the cap") {
        posterOptions(3838, 2158, 960, 540).shouldNotBeNull().scale() shouldBe Triple(2, 1919, 960)
    }

    test("a poster that fits in the card keeps its size") {
        posterOptions(320, 180, 480, 270).shouldNotBeNull().scale() shouldBe Triple(1, 0, 0)
        posterOptions(200, 200, 480, 270).shouldNotBeNull().scale() shouldBe Triple(1, 0, 0)
    }

    test("a 1:100 or 1:10000 poster is drawn whole, so its long side brings it under the cap") {
        posterOptions(27_000, 270, 480, 270).shouldNotBeNull().scale() shouldBe Triple(32, 843, 480)
        posterOptions(270, 27_000, 480, 270).shouldNotBeNull().scale() shouldBe Triple(64, 4, 3)
        posterOptions(2_700_000, 270, 480, 270).shouldNotBeNull().scale() shouldBe Triple(4096, 659, 480)
    }

    test("a poster near the card's shape that would decode over the cap is not decoded") {
        posterOptions(2300, 1079, 960, 540).shouldBeNull()
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
                val granted = HashSet<String>()
                val posters = ShelfPosters(app, MutableStateFlow(TvConfig()), Dispatchers.Unconfined) { it.toString() in granted }
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

                /** A PNG of [middle] with a [border] a tenth of its short side wide; both transparent by default. */
                fun png(
                    width: Int,
                    height: Int,
                    border: Int = 0,
                    middle: Int = 0,
                ): ByteArray {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val inset = minOf(width, height) / 10f
                    Canvas(bitmap).apply {
                        drawColor(border)
                        drawRect(inset, inset, width - inset, height - inset, Paint().apply { color = middle })
                    }
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    return out.toByteArray()
                }

                /** The app at [uri] does not let Home read it, as before it grants the URI again after a restart. */
                fun refuse(uri: String) {
                    shadowOf(app.contentResolver).registerInputStreamSupplier(Uri.parse(uri)) {
                        opened.merge(uri, 1, Int::plus)
                        throw SecurityException("Permission Denial: opening provider not exported")
                    }
                }

                /** The app at [uri] grants Home read access to it and serves its picture, as after a restart. */
                fun grant(uri: String) {
                    granted += uri
                    serve(uri, png(480, 270))
                }

                fun poster(
                    uri: String,
                    width: Int = 480,
                    height: Int = 270,
                ) = runBlocking { posters.poster(uri, width, height) }

                /**
                 * Collects [ShelfPosters.changes] as the card does, asking for [uri] again at each rise, and gives the virtual
                 * times of the rises after the first value.
                 */
                fun TestScope.cardAsking(uri: String): List<Long> {
                    val raised = ArrayList<Long>()
                    backgroundScope.launch {
                        posters.changes.drop(1).collect {
                            raised += currentTime
                            posters.poster(uri, 480, 270)
                        }
                    }
                    runCurrent()
                    return raised
                }

                fun TestScope.wait(time: Duration) {
                    advanceTimeBy(time)
                    runCurrent()
                }
            }
        } asContextForEach {
            test("a 1920 x 1080 content poster decodes at the card's 480 x 270 pixels") {
                serve("content://app.posters/1", png(1920, 1080))

                val poster = poster("content://app.posters/1").shouldNotBeNull()

                (poster.picture.width to poster.picture.height) shouldBe (480 to 270)
            }

            test("a 3 by 2 poster, near the card's shape, decodes at the least size that covers the card, 480 x 320, with no fill") {
                serve("content://app.posters/2", png(1500, 1000))

                val poster = poster("content://app.posters/2").shouldNotBeNull()

                (poster.picture.width to poster.picture.height) shouldBe (480 to 320)
                poster.fill shouldBe Color.Unspecified
            }

            test("a square poster decodes at the size that fits in the card, 270 x 270") {
                serve("content://app.posters/square", png(1080, 1080))

                val poster = poster("content://app.posters/square").shouldNotBeNull()

                (poster.picture.width to poster.picture.height) shouldBe (270 to 270)
            }

            test("a poster drawn whole with a flat coloured border fills round it with that colour") {
                serve("content://app.posters/cover", png(1080, 1080, border = 0xFF1E88E5.toInt(), middle = 0xFFE53935.toInt()))

                val poster = poster("content://app.posters/cover").shouldNotBeNull()

                poster.fill shouldBe Color(0xFF1E88E5)
            }

            test("a poster smaller than the card keeps its size") {
                serve("content://app.posters/3", png(320, 180))

                val poster = poster("content://app.posters/3").shouldNotBeNull()

                (poster.picture.width to poster.picture.height) shouldBe (320 to 180)
            }

            test("an https poster is never opened") {
                serve("https://i.ytimg.com/vi/x/hqdefault.jpg", png(480, 270))

                poster("https://i.ytimg.com/vi/x/hqdefault.jpg").shouldBeNull()

                opened.size shouldBe 0
            }

            test("a content poster aimed at the poster helper gives none, and the helper is never opened") {
                val query = "$POSTER_HELPER_PATH?u=http%3A%2F%2F192.168.1.1%2F&w=480&h=270&lan=1"
                listOf("content://$POSTER_HELPER_AUTHORITY/$query", "content://0@$POSTER_HELPER_AUTHORITY/$query").forEach { uri ->
                    serve(uri, png(480, 270))

                    withClue(uri) { poster(uri).shouldBeNull() }
                }

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
                serve("content://app.posters/wide", png(2300, 1079))

                poster("content://app.posters/wide", 960, 540).shouldBeNull()

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

            test("a poster refused for permission shows once the app grants it, while home stays started") {
                runTest {
                    val uri = "content://app.posters/granted-late"
                    refuse(uri)
                    posters.onStart()
                    val raised = cardAsking(uri)
                    posters.poster(uri, 480, 270).shouldBeNull()

                    wait(10.seconds)
                    raised.size shouldBe 1
                    serve(uri, png(480, 270))
                    wait(10.seconds)

                    raised.size shouldBe 2
                    posters.poster(uri, 480, 270).shouldNotBeNull()
                }
            }

            test("a refused poster shows within one check of the app's grant, between two steps of the backoff") {
                runTest {
                    val uri = "content://app.posters/granted-between-steps"
                    refuse(uri)
                    posters.onStart()
                    val raised = cardAsking(uri)
                    posters.poster(uri, 480, 270).shouldBeNull()

                    wait(43.seconds)
                    grant(uri)
                    wait(POSTER_GRANT_CHECK)

                    raised shouldBe listOf(10_000L, 20_000L, 40_000L, 44_000L)
                    posters.poster(uri, 480, 270).shouldNotBeNull()
                }
            }

            test("a check that finds no new grant raises no changes, so the card does not ask at every check") {
                runTest {
                    val uri = "content://app.posters/granted-still-refused"
                    refuse(uri)
                    posters.onStart()
                    val raised = cardAsking(uri)
                    posters.poster(uri, 480, 270).shouldBeNull()

                    granted += uri
                    wait(30.seconds)

                    raised shouldBe listOf(2_000L, 10_000L, 20_000L)
                }
            }

            test("the retries and checks of a refused poster stop after the last step of the backoff, 320 s after the refusal") {
                runTest {
                    val uri = "content://app.posters/never-granted"
                    refuse(uri)
                    posters.onStart()
                    val raised = cardAsking(uri)
                    posters.poster(uri, 480, 270).shouldBeNull()

                    wait(POSTER_GRANT_RETRIES.last())
                    granted += uri
                    wait(1.hours)

                    raised shouldBe listOf(10L, 20L, 40L, 80L, 160L, 320L).map { it * 1000 }
                    opened.getValue(uri) shouldBe 7
                }
            }

            test("a stopped home makes no retries or checks of a refused poster") {
                runTest {
                    val uri = "content://app.posters/refused-while-stopped"
                    refuse(uri)
                    posters.onStart()
                    val raised = cardAsking(uri)
                    posters.poster(uri, 480, 270).shouldBeNull()

                    posters.onStop()
                    granted += uri
                    wait(1.hours)

                    raised shouldBe emptyList()
                    opened.getValue(uri) shouldBe 1
                }
            }

            test("a poster that does not decode is not retried while home stays started") {
                runTest {
                    val uri = "content://app.posters/garbled"
                    serve(uri, "not a picture".toByteArray())
                    posters.onStart()
                    val raised = cardAsking(uri)
                    posters.poster(uri, 480, 270).shouldBeNull()
                    val reads = opened.getValue(uri)

                    wait(1.hours)
                    posters.poster(uri, 480, 270).shouldBeNull()

                    raised shouldBe emptyList()
                    opened.getValue(uri) shouldBe reads
                }
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
