package dev.niltsiar.tvhome.service.keys

/** A [Timer] on a clock the test moves by hand. */
internal class FakeTimer : Timer {
    var now = 0L
        private set
    private val tasks = mutableListOf<Pair<Long, Runnable>>()

    val pending: Int get() = tasks.size

    override fun post(
        task: Runnable,
        delayMs: Long,
    ) {
        tasks += now + delayMs to task
    }

    override fun cancel(task: Runnable) {
        tasks.removeAll { it.second === task }
    }

    /** Runs every task that comes due in the next [ms], in order, a task that posts again included. */
    fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            val next = tasks.filter { it.first <= end }.minByOrNull { it.first } ?: break
            tasks.remove(next)
            now = next.first
            next.second.run()
        }
        now = end
    }
}
