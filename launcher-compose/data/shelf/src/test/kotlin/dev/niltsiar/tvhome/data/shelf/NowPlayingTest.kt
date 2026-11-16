package dev.niltsiar.tvhome.data.shelf

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.model.NowPlayingArt
import dev.niltsiar.tvhome.core.model.NowPlayingItem
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaSessionManager
import kotlin.time.Duration.Companion.minutes

private const val SPOTIFY = "com.spotify.tv.android"
private const val ART_WIDTH = 320
private const val ART_HEIGHT = 180

/** The elapsed-realtime clock at the start of each test: a TV up for ten hours. */
private const val BOOT_TO_NOW = 10 * 60 * 60 * 1000L

/** What MediaSessionManager does for an app whose listener has no notification access. */
@Implements(MediaSessionManager::class)
class ShadowNoListenerAccess : ShadowMediaSessionManager() {
    @Implementation
    override fun getActiveSessions(notificationListener: ComponentName?): List<MediaController> =
        throw SecurityException("Missing permission to control media.")

    @Implementation
    override fun addOnActiveSessionsChangedListener(
        sessionListener: MediaSessionManager.OnActiveSessionsChangedListener?,
        notificationListener: ComponentName?,
        handler: Handler?,
    ): Unit = throw SecurityException("Missing permission to control media.")
}

/** Gives the no-access suite a sandbox of its own, so its shadow reaches no other suite. */
private object NoAccessSandbox

val NowPlayingTest by testSuite {
    robolectricTestSuite<NowPlayingTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
    robolectricTestSuite<NowPlayingNoAccessTestContent>(
        "sdk 31 without access",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                shadows += ShadowNoListenerAccess::class
                portableClasses += NoAccessSandbox::class
            },
    )
}

private fun app(): Application = RuntimeEnvironment.getApplication()

private fun idle() = shadowOf(Looper.getMainLooper()).idle()

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.reader(): NowPlaying =
    NowPlaying(
        app(),
        ComponentName(app(), "dev.niltsiar.tvhome.service.media.NowPlayingListener"),
        io = UnconfinedTestDispatcher(testScheduler),
        clock = { BOOT_TO_NOW + testScheduler.currentTime },
    )

private fun state(
    code: Int,
    updated: Long = BOOT_TO_NOW,
): PlaybackState = PlaybackState.Builder().setState(code, 0L, 1f, updated).build()

private fun song(
    title: String? = "The Sound of Silence",
    artist: String? = "Disturbed",
    extra: MediaMetadata.Builder.() -> Unit = {},
): MediaMetadata =
    MediaMetadata
        .Builder()
        .apply {
            title?.let { putString(MediaMetadata.METADATA_KEY_TITLE, it) }
            artist?.let { putString(MediaMetadata.METADATA_KEY_ARTIST, it) }
            extra()
        }.build()

private fun controller(
    packageName: String,
    state: PlaybackState?,
    metadata: MediaMetadata?,
): MediaController {
    val controller = MediaController(app(), MediaSession(app(), "test").sessionToken)
    shadowOf(controller).apply {
        setPackageName(packageName)
        setPlaybackState(state)
        setMetadata(metadata)
    }
    return controller
}

private fun publish(controller: MediaController) {
    shadowOf(app().getSystemService(MediaSessionManager::class.java)).addController(controller)
}

private fun playing(
    title: String = "The Sound of Silence",
    artist: String? = "Disturbed",
    art: NowPlayingArt? = null,
) = NowPlayingItem(SPOTIFY, title, artist, paused = false, art = art)

/** The item a session with [metadata] maps to, read without the flow. */
private fun itemOf(metadata: MediaMetadata): NowPlayingItem? =
    nowPlayingItem(Session(SPOTIFY, state(PlaybackState.STATE_PLAYING), metadata), BOOT_TO_NOW, ART_WIDTH, ART_HEIGHT)

class NowPlayingTestContent :
    RobolectricTestSuiteContent({
        test("the playing session of the package maps to its title and artist") {
            runTest {
                publish(controller("com.other.music", state(PlaybackState.STATE_PLAYING), song(title = "Other")))
                publish(controller(SPOTIFY, state(PlaybackState.STATE_PLAYING), song()))

                reader().item(SPOTIFY, ART_WIDTH, ART_HEIGHT).test {
                    idle()
                    awaitItem() shouldBe playing()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("without title and artist keys the description gives them") {
            val metadata =
                song(title = null, artist = null) {
                    putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, "The Sound of Silence")
                    putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, "Disturbed")
                }

            itemOf(metadata) shouldBe playing()
        }

        test("a session paused within the hour shows as paused") {
            val paused = state(PlaybackState.STATE_PAUSED, updated = BOOT_TO_NOW - PAUSED_SHOWN_MS + 1)

            nowPlayingItem(Session(SPOTIFY, paused, song()), BOOT_TO_NOW, ART_WIDTH, ART_HEIGHT) shouldBe
                playing().copy(paused = true)
        }

        test("a session paused over an hour ago is hidden") {
            val paused = state(PlaybackState.STATE_PAUSED, updated = BOOT_TO_NOW - PAUSED_SHOWN_MS - 1)

            nowPlayingItem(Session(SPOTIFY, paused, song()), BOOT_TO_NOW, ART_WIDTH, ART_HEIGHT).shouldBeNull()
        }

        test("a paused item goes when its hour runs out while it is collected") {
            runTest {
                publish(controller(SPOTIFY, state(PlaybackState.STATE_PAUSED), song()))

                reader().item(SPOTIFY, ART_WIDTH, ART_HEIGHT).test {
                    idle()
                    awaitItem() shouldBe playing().copy(paused = true)
                    advanceTimeBy(PAUSED_SHOWN_MS + 1)
                    awaitItem().shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("stopped, error and none states, no state and no title are hidden") {
            val hidden =
                mapOf(
                    "stopped" to Session(SPOTIFY, state(PlaybackState.STATE_STOPPED), song()),
                    "error" to Session(SPOTIFY, state(PlaybackState.STATE_ERROR), song()),
                    "none" to Session(SPOTIFY, state(PlaybackState.STATE_NONE), song()),
                    "no state" to Session(SPOTIFY, null, song()),
                    "no metadata" to Session(SPOTIFY, state(PlaybackState.STATE_PLAYING), null),
                    "blank title" to Session(SPOTIFY, state(PlaybackState.STATE_PLAYING), song(title = " ")),
                )
            for ((case, session) in hidden) {
                withClue(case) { nowPlayingItem(session, BOOT_TO_NOW, ART_WIDTH, ART_HEIGHT).shouldBeNull() }
            }
        }

        test("a track change emits the new item without collecting again") {
            runTest {
                val spotify = controller(SPOTIFY, state(PlaybackState.STATE_PLAYING), song())
                publish(spotify)

                reader().item(SPOTIFY, ART_WIDTH, ART_HEIGHT).test {
                    idle()
                    awaitItem() shouldBe playing()

                    val next = song(title = "Hello", artist = null)
                    shadowOf(spotify).setMetadata(next)
                    shadowOf(spotify).executeOnMetadataChanged(next)
                    idle()

                    awaitItem() shouldBe playing(title = "Hello", artist = null)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the session going away emits nothing and drops its callback") {
            runTest {
                val spotify = controller(SPOTIFY, state(PlaybackState.STATE_PLAYING), song())
                publish(spotify)

                reader().item(SPOTIFY, ART_WIDTH, ART_HEIGHT).test {
                    idle()
                    awaitItem() shouldBe playing()

                    shadowOf(app().getSystemService(MediaSessionManager::class.java)).clearControllers()

                    awaitItem().shouldBeNull()
                    shadowOf(spotify).callbacks.shouldBeEmpty()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("collection stopping removes the controller callback") {
            runTest {
                val spotify = controller(SPOTIFY, state(PlaybackState.STATE_PLAYING), song())
                publish(spotify)

                reader().item(SPOTIFY, ART_WIDTH, ART_HEIGHT).test {
                    idle()
                    awaitItem() shouldBe playing()
                    shadowOf(spotify).callbacks.size shouldBe 1
                    cancelAndIgnoreRemainingEvents()
                }
                idle()

                shadowOf(spotify).callbacks.shouldBeEmpty()
            }
        }

        // The first picture of a sandbox loads the native graphics library, which can take over a minute on a busy machine.
        test("art from a bitmap is scaled down to the card", testConfig = TestConfig.testScope(isEnabled = true, timeout = 5.minutes)) {
            val cover = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)

            val art = itemOf(song { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover) })?.art

            val picture = art.shouldBeInstanceOf<NowPlayingArt.Picture>().poster.picture
            // A square cover on a 16:9 card shows whole, so it fits the card's height.
            (picture.width to picture.height) shouldBe (ART_HEIGHT to ART_HEIGHT)
        }

        test("art uris map local first, then https, and other schemes to none") {
            fun artOf(vararg uris: Pair<String, String>) = itemOf(song { uris.forEach { (key, uri) -> putString(key, uri) } })?.art

            val web = "https://image-cdn.spotifycdn.com/cover"
            val content = "content://media/external/audio/albumart/7"
            val resource = "android.resource://com.spotify.tv.android/drawable/cover"

            artOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI to web, MediaMetadata.METADATA_KEY_ART_URI to content) shouldBe
                NowPlayingArt.Poster(content)
            artOf(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI to resource) shouldBe NowPlayingArt.Poster(resource)
            artOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI to web) shouldBe NowPlayingArt.Poster(web)
            artOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI to "http://example.com/cover").shouldBeNull()
            artOf(MediaMetadata.METADATA_KEY_ART_URI to "spotify:image:ab67").shouldBeNull()
        }
    })

class NowPlayingNoAccessTestContent :
    RobolectricTestSuiteContent({
        test("without notification access the reader emits nothing and does not crash") {
            runTest {
                publish(controller(SPOTIFY, state(PlaybackState.STATE_PLAYING), song()))

                reader().item(SPOTIFY, ART_WIDTH, ART_HEIGHT).test {
                    idle()
                    awaitItem().shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    })
