package dev.niltsiar.tvhome.posters

import android.graphics.Bitmap
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.random.Random

/** The poster files on disk. */
val PosterCacheTest by testSuite {
    robolectricTestSuite<PosterCacheTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class PosterCacheTestContent :
    RobolectricTestSuiteContent({
        test("the cache stays under its cap and drops the least recently used poster first") {
            val app = RuntimeEnvironment.getApplication()
            val dir = File(app.cacheDir, "posters")
            val random = Random(7)
            val noise = Bitmap.createBitmap(IntArray(200 * 200) { random.nextInt() }, 200, 200, Bitmap.Config.ARGB_8888)
            val probe = File(app.cacheDir, "probe")
            PosterCache(probe).write("f".repeat(64), noise, 0)
            val size = probe.listFiles()!!.single().length()
            val cap = size * 3 + size / 2
            val cache = PosterCache(dir, cap)
            val (first, second, third, fourth) = listOf("a", "b", "c", "d").map { it.repeat(64) }

            cache.write(first, noise, 1)
            cache.write(second, noise, 2)
            cache.write(third, noise, 3)
            cache.open(first, 4).shouldNotBeNull()
            cache.write(fourth, noise, 5)

            dir.listFiles()!!.sumOf { it.length() } shouldBeLessThanOrEqual cap
            cache.open(second, 6).shouldBeNull()
            cache.open(first, 6).shouldNotBeNull()
            cache.open(third, 6).shouldNotBeNull()
            cache.open(fourth, 6).shouldNotBeNull()
        }

        test("a cropped poster stored under the old name is deleted when the index is first read") {
            val app = RuntimeEnvironment.getApplication()
            val dir = File(app.cacheDir, "cropped")
            dir.mkdirs()
            val cropped = File(dir, "${"a".repeat(64)}-1.jpg").apply { writeBytes(byteArrayOf(1)) }

            PosterCache(dir).open("a".repeat(64), 2).shouldBeNull()

            cropped.exists() shouldBe false
        }
    })
