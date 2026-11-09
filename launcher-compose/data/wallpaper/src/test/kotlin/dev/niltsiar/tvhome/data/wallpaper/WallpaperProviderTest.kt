package dev.niltsiar.tvhome.data.wallpaper

import android.content.SharedPreferences
import androidx.compose.ui.graphics.ImageBitmap
import app.cash.turbine.test
import dev.niltsiar.tvhome.core.config.Place
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.config.WallpaperMood
import dev.niltsiar.tvhome.core.config.WallpaperOrder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WallpaperProviderTest {
    @get:Rule
    val themeDir = TemporaryFolder()

    private lateinit var preferences: SharedPreferences
    private lateinit var collection: File
    private val decoded = mutableListOf<String>()
    private val pictures = HashMap<String, ImageBitmap>()
    private val unreadable = mutableSetOf<String>()
    private val noon = LocalDateTime.of(2026, 11, 4, 12, 0)

    private val rotating = TvConfig(wallpaperRotationMinutes = 5, wallpaperOrder = WallpaperOrder.Sequential)

    @Before
    fun setUp() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("test", 0)
        collection = themeDir.newFolder("wallpapers")
        listOf("a.jpg", "b.jpg", "c.jpg").forEach { add(it) }
    }

    private fun add(name: String) = File(collection, name).also { it.writeBytes(ByteArray(8) { _ -> 1 }) }

    private fun provider(config: TvConfig) =
        WallpaperProvider(
            config = MutableStateFlow(config),
            single = File(themeDir.root, "wallpaper.jpg"),
            folder = collection,
            preferences = preferences,
            decode = { file ->
                decoded += file.name
                if (file.name in unreadable) null else DecodedPicture(pictures.getOrPut(file.name) { ImageBitmap(1, 1) }, null)
            },
            zone = { ZoneOffset.UTC },
        )

    private fun WallpaperProvider.shows(
        name: String,
        at: LocalDateTime,
    ) {
        val shown = advance(at).picture
        assertSame(name, pictures.getValue(name), shown)
    }

    @Test
    fun the_first_tick_shows_a_picture_and_later_ticks_keep_it_until_it_is_due() {
        val provider = provider(rotating)
        provider.shows("a.jpg", noon)
        provider.shows("a.jpg", noon.plusMinutes(4))
        assertEquals(listOf("a.jpg"), decoded)
        provider.shows("b.jpg", noon.plusMinutes(5))
        provider.shows("b.jpg", noon.plusMinutes(6))
    }

    @Test
    fun a_restart_resumes_the_picture_that_was_up() {
        provider(rotating).apply {
            advance(noon)
            shows("b.jpg", noon.plusMinutes(5))
        }
        provider(rotating).shows("b.jpg", noon.plusMinutes(6))
    }

    @Test
    fun a_different_collection_starts_from_its_first_picture() {
        provider(rotating).apply {
            advance(noon)
            shows("b.jpg", noon.plusMinutes(5))
        }
        add("0.jpg")
        provider(rotating).shows("0.jpg", noon.plusMinutes(6))
    }

    @Test
    fun an_unreadable_picture_is_skipped() {
        unreadable += "a.jpg"
        val provider = provider(rotating)
        provider.shows("b.jpg", noon)
        provider.shows("b.jpg", noon.plusMinutes(1))
        assertEquals(listOf("a.jpg", "b.jpg"), decoded)
    }

    @Test
    fun a_picture_outside_the_time_of_days_group_is_replaced_at_once() {
        val moods = mapOf(WallpaperMood.Morning to setOf("a.jpg"), WallpaperMood.Night to setOf("c.jpg"))
        val provider = provider(rotating.copy(wallpaperRotationMinutes = 1440, wallpaperMoods = moods))
        provider.shows("a.jpg", noon.withHour(8))
        provider.shows("c.jpg", noon.withHour(23))
    }

    @Test
    fun without_rotation_the_single_wallpaper_shows_and_the_theme_can_switch_it_off() {
        val single = File(themeDir.root, "wallpaper.jpg").also { it.writeBytes(ByteArray(8) { _ -> 1 }) }
        provider(TvConfig()).shows(single.name, noon)
        assertNull(provider(TvConfig(wallpaper = false)).advance(noon).picture)
        assertEquals(listOf("wallpaper.jpg"), decoded)
    }

    @Test
    fun captions_name_the_shown_picture_when_the_theme_asks() {
        File(collection, "captions.txt").writeText("a.jpg\tAlpha\n")
        assertEquals("Alpha", provider(rotating.copy(wallpaperCaptions = true)).advance(noon).caption)
        assertNull(provider(rotating).advance(noon).caption)
    }

    @Test
    fun the_sky_follows_the_minute_at_the_place_when_the_theme_asks() {
        val madrid = Place(40.4168, -3.7038)
        val sun = Sun.riseAndSet(noon.toLocalDate(), madrid.latitude, madrid.longitude, ZoneOffset.UTC)!!
        val sky = provider(rotating.copy(skyIntensity = 40, place = madrid))
        assertEquals(Sky.at(12 * 60, sun.sunrise, sun.sunset), sky.advance(noon).sky)
        assertEquals(Sky.at(21 * 60 + 30, sun.sunrise, sun.sunset), sky.advance(noon.withHour(21).withMinute(30)).sky)
        assertEquals(Sky.at(12 * 60), provider(rotating.copy(skyIntensity = 40)).advance(noon).sky)
        assertNull(provider(rotating).advance(noon).sky)
    }

    @Test
    fun nothing_runs_while_stopped() =
        runBlocking {
            val clock = MutableSharedFlow<LocalDateTime?>()
            val provider = provider(rotating)
            provider.wallpaper(clock).test {
                expectNoEvents()
                assertEquals(0, clock.subscriptionCount.value)
                provider.onStart()
                withTimeout(5_000) { clock.subscriptionCount.first { it > 0 } }
                clock.emit(null)
                clock.emit(noon)
                val shown = awaitItem().picture
                assertSame(pictures.getValue("a.jpg"), shown)
                provider.onStop()
                withTimeout(5_000) { clock.subscriptionCount.first { it == 0 } }
                expectNoEvents()
            }
        }

    @Test
    fun a_trim_while_stopped_drops_the_picture_and_the_next_start_decodes_it_again() =
        runBlocking {
            val clock = MutableSharedFlow<LocalDateTime?>()
            val provider = provider(rotating)
            provider.wallpaper(clock).test {
                provider.onStart()
                withTimeout(5_000) { clock.subscriptionCount.first { it > 0 } }
                clock.emit(noon)
                val shown = awaitItem().picture
                assertSame(pictures.getValue("a.jpg"), shown)
                provider.onStop()
                withTimeout(5_000) { clock.subscriptionCount.first { it == 0 } }

                provider.releasePicture()
                assertNull(awaitItem().picture)

                provider.onStart()
                withTimeout(5_000) { clock.subscriptionCount.first { it > 0 } }
                clock.emit(noon)
                assertSame(pictures.getValue("a.jpg"), awaitItem().picture)
                assertEquals(listOf("a.jpg", "a.jpg"), decoded)
            }
        }
}
