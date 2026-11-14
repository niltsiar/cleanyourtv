package dev.niltsiar.tvhome.data.shelf

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.content.pm.Signature
import android.graphics.Bitmap
import android.net.Uri
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.PosterHelper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

private const val YOUTUBE = "https://i.ytimg.com/vi/x/hqdefault.jpg"
private const val JELLYFIN = "http://192.168.1.50:8096/Items/7/Images/Primary"
private val HomeKey = Signature("0a0b0c")
private val OtherKey = Signature("0d0e0f")

/** The contract with the poster helper app, which the helper copies: change both sides together. */
val PosterHelperContractTest by testSuite {
    test("the authority, package, path and parameter names are the helper's") {
        POSTER_HELPER_AUTHORITY shouldBe "dev.niltsiar.tvhome.posters"
        POSTER_HELPER_PACKAGE shouldBe "dev.niltsiar.tvhome.posters"
        POSTER_HELPER_PATH shouldBe "poster"
        listOf(POSTER_PARAM_URL, POSTER_PARAM_WIDTH, POSTER_PARAM_HEIGHT, POSTER_PARAM_LAN) shouldBe listOf("u", "w", "h", "lan")
    }
}

/** Posters from the web through the poster helper: when Home asks it, what it sends, and how a picture fetched later shows. */
val WebPostersTest by testSuite {
    robolectricTestSuite<WebPostersTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class WebPostersTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app = RuntimeEnvironment.getApplication()
                val config = MutableStateFlow(TvConfig(appShelf = true))
                val posters = ShelfPosters(app, config, Dispatchers.Unconfined)
                val helper: FakePosterHelper =
                    Robolectric.buildContentProvider(FakePosterHelper::class.java).create(POSTER_HELPER_AUTHORITY).get()
                val scope = CoroutineScope(Dispatchers.Unconfined)
                val changes = ArrayList<Int>()
                val helperPosters: Uri = Uri.parse("content://$POSTER_HELPER_AUTHORITY/$POSTER_HELPER_PATH")

                init {
                    shadowOf(app.packageManager).getInternalMutablePackageInfo(app.packageName).signatures = arrayOf(HomeKey)
                }

                /** Installs [pkg] as the owner of the helper's authority, signed with [key]. */
                fun install(
                    key: Signature = HomeKey,
                    pkg: String = POSTER_HELPER_PACKAGE,
                ) {
                    val info = ApplicationInfo().apply { packageName = pkg }
                    shadowOf(app.packageManager).installPackage(
                        PackageInfo().apply {
                            packageName = pkg
                            applicationInfo = info
                            signatures = arrayOf(key)
                            providers =
                                arrayOf(
                                    ProviderInfo().apply {
                                        authority = POSTER_HELPER_AUTHORITY
                                        packageName = pkg
                                        name = "$pkg.PosterProvider"
                                        applicationInfo = info
                                    },
                                )
                        },
                    )
                }

                fun webPosters(lan: Boolean = false) {
                    config.value = config.value.copy(appShelfWebPosters = true, appShelfLanPosters = lan)
                }

                fun collectChanges() {
                    scope.launch { posters.changes.collect { changes += it } }
                }

                fun observers() = shadowOf(app.contentResolver).getContentObservers(helperPosters).size

                fun png(): ByteArray {
                    val out = ByteArrayOutputStream()
                    Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out)
                    return out.toByteArray()
                }

                fun poster(uri: String) = runBlocking { posters.poster(uri, 480, 270) }
            }
        } closeWith {
            scope.cancel()
        } asContextForEach {
            test("with pictures from the web off an https poster never reaches the helper") {
                install()
                helper.pictures[YOUTUBE] = png()

                poster(YOUTUBE).shouldBeNull()

                helper.opened.shouldBeEmpty()
            }

            test("with pictures from the web on and the helper ready an https poster is read from the helper with u, w and h") {
                install()
                webPosters()
                helper.pictures[YOUTUBE] = png()

                poster(YOUTUBE).shouldNotBeNull()

                val asked = helper.opened.first()
                asked.authority shouldBe "dev.niltsiar.tvhome.posters"
                asked.path shouldBe "/poster"
                asked.queryParameterNames shouldBe setOf("u", "w", "h")
                asked.getQueryParameter("u") shouldBe YOUTUBE
                (asked.getQueryParameter("w") to asked.getQueryParameter("h")) shouldBe ("480" to "270")
            }

            test("a helper signed with another key is sent nothing") {
                install(key = OtherKey)
                webPosters()
                helper.pictures[YOUTUBE] = png()

                posterHelper(app) shouldBe PosterHelper.OtherKey
                poster(YOUTUBE).shouldBeNull()
                helper.opened.shouldBeEmpty()
            }

            test("with no helper installed, or another package owning its authority, nothing is sent") {
                webPosters()
                helper.pictures[YOUTUBE] = png()

                posterHelper(app) shouldBe PosterHelper.Missing
                poster(YOUTUBE).shouldBeNull()

                install(pkg = "com.example.impostor")
                posters.onStart()
                posterHelper(app) shouldBe PosterHelper.Missing
                poster(YOUTUBE).shouldBeNull()

                helper.opened.shouldBeEmpty()
            }

            test("an http poster needs pictures from your network too, and then carries lan=1") {
                install()
                helper.pictures[JELLYFIN] = png()
                webPosters(lan = false)
                poster(JELLYFIN).shouldBeNull()
                helper.opened.shouldBeEmpty()

                webPosters(lan = true)
                poster(JELLYFIN).shouldNotBeNull()

                val asked = helper.opened.distinct().single()
                asked.getQueryParameter("lan") shouldBe "1"
                asked.getQueryParameter("u") shouldBe JELLYFIN
            }

            test("the helper is checked once per start of home, not per poster") {
                install()
                webPosters()
                helper.pictures[YOUTUBE] = png()
                poster(YOUTUBE).shouldNotBeNull()

                install(key = OtherKey)
                poster("https://i.ytimg.com/vi/y/hqdefault.jpg").shouldBeNull()
                helper.urls() shouldBe listOf(YOUTUBE, "https://i.ytimg.com/vi/y/hqdefault.jpg")

                posters.onStart()
                poster("https://i.ytimg.com/vi/z/hqdefault.jpg").shouldBeNull()
                helper.urls() shouldBe listOf(YOUTUBE, "https://i.ytimg.com/vi/y/hqdefault.jpg")
            }

            test("a helper miss is read again after the helper notifies, while a local poster that failed to decode is not") {
                install()
                webPosters()
                posters.onStart()
                collectChanges()
                shadowOf(app.contentResolver).registerInputStreamSupplier(Uri.parse("content://app.posters/broken")) {
                    ByteArrayInputStream("not a picture".toByteArray())
                }
                poster("content://app.posters/broken").shouldBeNull()
                poster(YOUTUBE).shouldBeNull()
                val before = changes.last()
                val misses = helper.opened.size

                helper.pictures[YOUTUBE] = png()
                app.contentResolver.notifyChange(helperPosters, null)

                changes.last() shouldBe before + 1
                poster(YOUTUBE).shouldNotBeNull()
                helper.opened.size shouldBeGreaterThan misses
                var reads = 0
                shadowOf(app.contentResolver).registerInputStreamSupplier(Uri.parse("content://app.posters/broken")) {
                    reads++
                    ByteArrayInputStream("not a picture".toByteArray())
                }
                poster("content://app.posters/broken").shouldBeNull()
                reads shouldBe 0
            }

            test("the helper is watched only after a request, while home is started and the changes are collected") {
                install()
                webPosters()
                helper.pictures[YOUTUBE] = png()
                posters.onStart()
                collectChanges()
                observers() shouldBe 0

                poster(YOUTUBE).shouldNotBeNull()
                observers() shouldBe 1

                posters.onStop()
                observers() shouldBe 0
                posters.onStart()
                observers() shouldBe 1

                scope.cancel()
                observers() shouldBe 0
            }

            test("after a request to the helper, home coming to the front raises the changes") {
                install()
                webPosters()
                posters.onStart()
                collectChanges()
                val first = changes.last()
                posters.onStart()
                changes.last() shouldBe first

                poster(YOUTUBE).shouldBeNull()
                posters.onStop()
                posters.onStart()

                changes.last() shouldBe first + 1
            }
        }
    })
