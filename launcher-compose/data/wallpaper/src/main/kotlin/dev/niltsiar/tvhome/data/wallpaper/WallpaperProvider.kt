package dev.niltsiar.tvhome.data.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.SkyPalette
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 * wallpaper_rotation_minutes is over 0, else `wallpaper.jpg`, else none (the theme's background colour shows). The
 * on-TV [wallpaperChoice] replaces that rotation with one picture, or with none, and is read at each advance.
 * The picture on screen, and when it came up, survive a restart in the app's preferences; a different collection
 * starts again from its first picture. [wallpaper] follows the minute clock only between [onStart] and [onStop].
 */
public class WallpaperProvider internal constructor(
    private val config: StateFlow<TvConfig>,
    private val wallpaperChoice: StateFlow<String?>,
    private val single: File?,
    private val folder: File?,
    private val preferences: SharedPreferences,
    private val decode: (File) -> DecodedPicture?,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val random: Random = Random.Default,
) {
    /**
     * [dim] picks the percent painted into each picture: wallpaper_dim for Home, clock_wallpaper_dim for the screensaver.
     * [thumbnails] gives each [Wallpaper] its [Wallpaper.thumbnail].
     */
    public constructor(
        context: Context,
        config: StateFlow<TvConfig>,
        wallpaperChoice: StateFlow<String?>,
        theme: ThemeDirectory,
        dim: (TvConfig) -> Int = TvConfig::wallpaperDim,
        thumbnails: Boolean = true,
    ) : this(
        config = config,
        wallpaperChoice = wallpaperChoice,
        single = theme.wallpaperFile,
        folder = theme.wallpapersFolder,
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        decode = { file ->
            val current = config.value
            with(context.resources.displayMetrics) {
                WallpaperDecoder(widthPixels, heightPixels, current.wallpaperTrueColor, dim(current), thumbnails).decode(file)
            }
        },
    )

    private class Pictures(
        val choice: String?,
        val files: MutableList<File>,
        val captions: Map<String, String>,
    )

    private val started = MutableStateFlow(false)
    private var loaded: Pictures? = null
    private val releases = MutableSharedFlow<Wallpaper>(extraBufferCapacity = 1)

    @Volatile
    private var picture: DecodedPicture? = null

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
     * The wallpaper at each tick of [clock] while started, and again when the on-TV choice changes; a tick with no valid
     * time is skipped. The listing, decoding and preferences run on the IO dispatcher, one tick at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun wallpaper(clock: Flow<LocalDateTime?>): Flow<Wallpaper> =
        merge(
            started
                .flatMapLatest { if (it) combine(clock.filterNotNull(), wallpaperChoice) { now, _ -> now } else emptyFlow() }
                .map(::advance)
                .flowOn(Dispatchers.IO),
            releases,
        ).distinctUntilChanged()

    /** Shows what [now] calls for: the next picture when it is due, one of the time of day's group, or the same one. */
    internal fun advance(now: LocalDateTime): Wallpaper {
        val zone = zone()
        val current = config.value
        val pictures = pictures()
        selectPicture(pictures, now, zone, current)
        val minute = Sky.minuteOfDay(now.hour, now.minute)
        val shown = picture
        return Wallpaper(
            picture = shown?.picture,
            caption = if (shown == null) null else pictures.captions[displayed],
            sky = if (current.skyIntensity > 0) sky(current, now, minute, zone) else null,
            thumbnail = shown?.thumbnail,
        )
    }

    private fun selectPicture(
        pictures: Pictures,
        now: LocalDateTime,
        zone: ZoneId,
        current: TvConfig,
    ) {
        val files = pictures.files
        val millis = now.atZone(zone).toInstant().toEpochMilli()
        val interval = TimeUnit.MINUTES.toMillis(current.wallpaperRotationMinutes.toLong())
        while (files.isNotEmpty()) {
            val previous = preferences.getString(KEY_IMAGE, "").orEmpty()
            val changed = preferences.getLong(KEY_CHANGED, 0)
            // A picture outside the time of day's group (the group changed) is replaced at once, not after the interval.
            val playable = playable(current, files, now, zone)
            var selected = WallpaperRotation.find(playable, previous)
            if (selected == null || WallpaperRotation.due(changed, millis, interval)) {
                selected = WallpaperRotation.pick(playable, previous, current.wallpaperOrder, random)
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
        picture = null
        displayed = ""
    }

    /** The pictures the rotation picks from now: wallpaper_moods' group for the time of day, or the whole collection. */
    private fun playable(
        current: TvConfig,
        files: List<File>,
        now: LocalDateTime,
        zone: ZoneId,
    ): List<File> {
        if (current.wallpaperMoods.isEmpty()) return files
        val sun = WallpaperMoods.sun(now.toLocalDate(), current.place, zone)
        return WallpaperMoods.select(files, current.wallpaperMoods, WallpaperMoods.timeOfDay(now.hour * 60 + now.minute, sun))
    }

    private fun sky(
        current: TvConfig,
        now: LocalDateTime,
        minute: Int,
        zone: ZoneId,
    ): SkyPalette {
        val sun = current.place?.let { Sun.riseAndSet(now.toLocalDate(), it.latitude, it.longitude, zone) }
        return if (sun == null) Sky.at(minute) else Sky.at(minute, sun.sunrise, sun.sunset)
    }

    /** The listing for the choice stored now. The theme's files are read once; a changed choice lists them again. */
    @Synchronized
    private fun pictures(): Pictures {
        val choice = wallpaperChoice.value
        return loaded?.takeIf { it.choice == choice } ?: loadPictures(choice).also { loaded = it }
    }

    private fun loadPictures(choice: String?): Pictures {
        val current = config.value
        val collection = WallpaperRotation.images(folder)
        val files = mutableListOf<File>()
        var fromFolder = false
        if (current.wallpaper) {
            if (current.wallpaperRotationMinutes > 0) {
                files += collection
                fromFolder = files.isNotEmpty()
            }
            if (files.isEmpty()) WallpaperRotation.usable(single)?.let(files::add)
        }
        // The on-TV pick wins over the theme's rotation and over wallpaper false, as in the reference launcher.
        WallpaperRotation.chosen(collection, WallpaperRotation.usable(single), choice)?.let { picked ->
            files.clear()
            files += picked
            fromFolder = picked.isNotEmpty() && picked.first() in collection
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
        val captions = if (current.wallpaperCaptions && fromFolder) WallpaperCaptions.read(folder) else emptyMap()
        return Pictures(choice, files, captions)
    }
}
