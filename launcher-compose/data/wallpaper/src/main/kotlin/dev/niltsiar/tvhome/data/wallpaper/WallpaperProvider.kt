package dev.niltsiar.tvhome.data.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.SkyPalette
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val TAG = "TvHome.Wallpaper"
private const val PREFERENCES_NAME = "wallpaper_rotation"
private const val KEY_COLLECTION = "collection"
private const val KEY_IMAGE = "image"
private const val KEY_CHANGED = "changed"

/**
 * The picture behind Home, read the way the reference launcher plays it: the theme's `wallpapers/` collection when
 * wallpaper_rotation_minutes is over 0, else `wallpaper.jpg`, else none (the theme's background colour shows).
 * The picture on screen, and when it came up, survive a restart in the app's preferences; a different collection
 * starts again from its first picture. [wallpaper] follows the minute clock only between [onStart] and [onStop].
 */
public class WallpaperProvider internal constructor(
    private val config: TvConfig,
    private val single: File?,
    private val folder: File?,
    private val preferences: SharedPreferences,
    private val decode: (File) -> ImageBitmap?,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val random: Random = Random.Default,
) {
    /** [dim] is the percent painted into each picture: wallpaper_dim for Home, clock_wallpaper_dim for the screensaver. */
    public constructor(
        context: Context,
        config: TvConfig,
        theme: ThemeDirectory,
        dim: Int = config.wallpaperDim,
    ) : this(
        config = config,
        single = theme.wallpaperFile,
        folder = theme.wallpapersFolder,
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        decode =
            with(context.resources.displayMetrics) {
                WallpaperDecoder(widthPixels, heightPixels, config.wallpaperTrueColor, dim)::decode
            },
    )

    private class Pictures(
        val files: MutableList<File>,
        val captions: Map<String, String>,
    )

    private val started = MutableStateFlow(false)
    private val pictures by lazy(::loadPictures)
    private val releases = MutableSharedFlow<Wallpaper>(extraBufferCapacity = 1)

    @Volatile
    private var picture: ImageBitmap? = null

    @Volatile
    private var displayed = ""

    /** Home is on screen: the picture follows the clock. Main thread. */
    public fun onStart() {
        started.value = true
    }

    /** Home left the screen: nothing runs until [onStart]. Main thread. */
    public fun onStop() {
        started.value = false
    }

    /**
     * Memory is short while Home is stopped: forget the decoded picture, and send [wallpaper] on again without it. The
     * next tick after [onStart] decodes it again. Main thread.
     */
    public fun releasePicture() {
        picture = null
        displayed = ""
        releases.tryEmit(Wallpaper())
    }

    /**
     * The wallpaper at each tick of [clock] while started; a tick with no valid time is skipped. The listing, decoding
     * and preferences run on the IO dispatcher, one tick at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun wallpaper(clock: Flow<LocalDateTime?>): Flow<Wallpaper> =
        merge(
            started
                .flatMapLatest { if (it) clock.filterNotNull() else emptyFlow() }
                .map(::advance)
                .flowOn(Dispatchers.IO),
            releases,
        ).distinctUntilChanged()

    /** Shows what [now] calls for: the next picture when it is due, one of the time of day's group, or the same one. */
    internal fun advance(now: LocalDateTime): Wallpaper {
        val zone = zone()
        selectPicture(now, zone)
        val minute = Sky.minuteOfDay(now.hour, now.minute)
        return Wallpaper(
            picture = picture,
            caption = if (picture == null) null else pictures.captions[displayed],
            sky = if (config.skyIntensity > 0) sky(now, minute, zone) else null,
        )
    }

    private fun selectPicture(
        now: LocalDateTime,
        zone: ZoneId,
    ) {
        val files = pictures.files
        val millis = now.atZone(zone).toInstant().toEpochMilli()
        val interval = TimeUnit.MINUTES.toMillis(config.wallpaperRotationMinutes.toLong())
        while (files.isNotEmpty()) {
            val previous = preferences.getString(KEY_IMAGE, "").orEmpty()
            val changed = preferences.getLong(KEY_CHANGED, 0)
            // A picture outside the time of day's group (the group changed) is replaced at once, not after the interval.
            val playable = playable(files, now, zone)
            var selected = WallpaperRotation.find(playable, previous)
            if (selected == null || WallpaperRotation.due(changed, millis, interval)) {
                selected = WallpaperRotation.pick(playable, previous, config.wallpaperOrder, random)
            }
            if (selected == null || selected.name == displayed) return
            val decoded = decode(selected)
            if (decoded == null) {
                Log.w(TAG, "Skipping unreadable image: ${selected.name}")
                files.remove(selected)
                continue
            }
            picture = decoded
            displayed = selected.name
            if (displayed != previous || changed == 0L) {
                preferences
                    .edit()
                    .putString(KEY_IMAGE, displayed)
                    .putLong(KEY_CHANGED, millis)
                    .apply()
            }
            return
        }
    }

    /** The pictures the rotation picks from now: wallpaper_moods' group for the time of day, or the whole collection. */
    private fun playable(
        files: List<File>,
        now: LocalDateTime,
        zone: ZoneId,
    ): List<File> {
        if (config.wallpaperMoods.isEmpty()) return files
        val sun = WallpaperMoods.sun(now.toLocalDate(), config.place, zone)
        return WallpaperMoods.select(files, config.wallpaperMoods, WallpaperMoods.timeOfDay(now.hour * 60 + now.minute, sun))
    }

    private fun sky(
        now: LocalDateTime,
        minute: Int,
        zone: ZoneId,
    ): SkyPalette {
        val sun = config.place?.let { Sun.riseAndSet(now.toLocalDate(), it.latitude, it.longitude, zone) }
        return if (sun == null) Sky.at(minute) else Sky.at(minute, sun.sunrise, sun.sunset)
    }

    private fun loadPictures(): Pictures {
        val files = mutableListOf<File>()
        var fromFolder = false
        if (config.wallpaper) {
            if (config.wallpaperRotationMinutes > 0) {
                files += WallpaperRotation.images(folder)
                fromFolder = files.isNotEmpty()
            }
            if (files.isEmpty()) single?.takeIf { it.isFile && it.length() > 0 }?.let(files::add)
        }
        // Playback state belongs to one collection. A different collection starts from its first picture instead of
        // resuming a file name that belonged to the previous one. Names and sizes notice a change without hashing.
        val key = files.joinToString("") { "${it.name}:${it.length()};" }
        if (key != preferences.getString(KEY_COLLECTION, "")) {
            preferences
                .edit()
                .putString(KEY_COLLECTION, key)
                .remove(KEY_IMAGE)
                .remove(KEY_CHANGED)
                .apply()
        }
        val captions = if (config.wallpaperCaptions && fromFolder) WallpaperCaptions.read(folder) else emptyMap()
        return Pictures(files, captions)
    }
}
