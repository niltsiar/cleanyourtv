package dev.niltsiar.tvhome.data.wallpaper

import dev.niltsiar.tvhome.core.config.WallpaperOrder
import java.io.File
import java.util.Locale
import kotlin.random.Random

/** Which picture of a wallpaper collection is next, and when. */
internal object WallpaperRotation {
    private const val MAX_IMAGES = 100
    private const val MAX_BYTES = 10L * 1024 * 1024
    private val extensions = listOf(".jpg", ".jpeg", ".png", ".webp")

    /** The pictures in [directory], sorted by name: 1 byte to 10 MB each, at most 100. */
    fun images(directory: File?): List<File> =
        directory
            ?.listFiles()
            .orEmpty()
            .sortedBy { it.name }
            .filter { entry ->
                val name = entry.name.lowercase(Locale.ROOT)
                entry.isFile && entry.length() in 1..MAX_BYTES && extensions.any(name::endsWith)
            }.take(MAX_IMAGES)

    fun find(
        images: List<File>,
        name: String,
    ): File? = images.firstOrNull { it.name == name }

    /** A random picture other than [previous], unless that is the only one. */
    fun pick(
        images: List<File>,
        previous: String,
        random: Random,
    ): File? {
        val candidates = if (images.size > 1) images.filter { it.name != previous } else images
        return if (candidates.isEmpty()) null else candidates[random.nextInt(candidates.size)]
    }

    /**
     * The next picture in the sorted collection, wrapping to the first at the end. A [previous] name that is no longer
     * in the collection starts again at the first picture.
     */
    fun next(
        images: List<File>,
        previous: String,
    ): File? {
        if (images.isEmpty()) return null
        val at = images.indexOfFirst { it.name == previous }
        return if (at < 0) images[0] else images[(at + 1) % images.size]
    }

    fun pick(
        images: List<File>,
        previous: String,
        order: WallpaperOrder,
        random: Random,
    ): File? =
        when (order) {
            WallpaperOrder.Sequential -> next(images, previous)
            WallpaperOrder.Shuffle -> pick(images, previous, random)
        }

    /**
     * Whether the picture shown since [previous] (epoch ms, 0 for never) has been up for [interval] ms by [now]. The
     * minute tick drifts, so it often lands a few hundred ms short of the interval; compared exactly, that pass is
     * skipped and a one-minute collection changes every two. Two seconds of slack absorb the drift without letting a
     * genuinely early call advance the picture.
     */
    fun due(
        previous: Long,
        now: Long,
        interval: Long,
    ): Boolean {
        val slack = minOf(2_000L, interval / 10)
        return interval > 0 && (previous == 0L || now < previous || now - previous >= interval - slack)
    }
}
