package dev.niltsiar.tvhome.posters

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private const val TAG = "TvHome.Posters"

internal const val FETCHES_AT_ONCE = 2
internal const val MAX_WAITING = 32
internal val RETRY_AFTER_MILLIS = TimeUnit.HOURS.toMillis(1)

/** The URI Home watches: every fetch that lands notifies it. */
internal val POSTER_CHANGES: Uri = Uri.parse("content://$POSTER_AUTHORITY/$POSTER_PATH")

/**
 * The posters Home asks for. A cached one opens at once; a missing or stale one queues a fetch on [executor], at most
 * [FETCHES_AT_ONCE] running and [MAX_WAITING] waiting, one per key. A URL whose fetch failed is not fetched again for
 * [RETRY_AFTER_MILLIS]. Each fetch that lands notifies [POSTER_CHANGES].
 */
internal class Posters(
    private val context: Context,
    private val cache: PosterCache = PosterCache(File(context.cacheDir, "posters")),
    private val fetcher: PosterFetcher = PosterFetcher(),
    private val executor: Executor = fetchThreads(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val pending = HashSet<String>()
    private val failedAt = HashMap<String, Long>()

    /** The cached poster of [request], or null when there is none yet and a fetch may have been queued. */
    fun open(request: PosterRequest): ParcelFileDescriptor? {
        val cached = cache.open(request.key, now())
        if (cached == null || cached.stale) queue(request)
        return cached?.file
    }

    @Synchronized
    private fun queue(request: PosterRequest) {
        val time = now()
        failedAt.values.removeAll { time - it >= RETRY_AFTER_MILLIS }
        if (request.url.toString() in failedAt) return
        if (request.key in pending || pending.size >= FETCHES_AT_ONCE + MAX_WAITING) return
        pending += request.key
        executor.execute { fetch(request) }
    }

    private fun fetch(request: PosterRequest) {
        val landed =
            try {
                val picture = fetcher.picture(request)
                cache.write(request.key, picture, now())
                picture.recycle()
                true
            } catch (e: IOException) {
                Log.w(TAG, "fetch failed: $e")
                false
            }
        synchronized(this) {
            pending -= request.key
            if (!landed) failedAt[request.url.toString()] = now()
        }
        if (landed) context.contentResolver.notifyChange(POSTER_CHANGES, null)
    }
}

/** [FETCHES_AT_ONCE] background threads that end after 10 s without work, so an idle helper holds none. */
private fun fetchThreads(): Executor =
    ThreadPoolExecutor(FETCHES_AT_ONCE, FETCHES_AT_ONCE, 10, TimeUnit.SECONDS, LinkedBlockingQueue()) { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "poster-fetch")
    }.apply { allowCoreThreadTimeOut(true) }
