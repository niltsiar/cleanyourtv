package dev.niltsiar.tvhome.service.keys

import android.os.Handler
import android.os.Looper

/** Delayed work on the main thread; tests run it by hand. */
internal interface Timer {
    fun post(
        task: Runnable,
        delayMs: Long,
    )

    fun cancel(task: Runnable)
}

internal class HandlerTimer : Timer {
    private val handler = Handler(Looper.getMainLooper())

    override fun post(
        task: Runnable,
        delayMs: Long,
    ) {
        handler.postDelayed(task, delayMs)
    }

    override fun cancel(task: Runnable) {
        handler.removeCallbacks(task)
    }
}
