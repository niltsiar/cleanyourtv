package dev.niltsiar.tvhome

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Environment
import android.os.Looper
import android.os.UserManager
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * TvHomeApplication started by hand over a base context that holds back any thread but main in the repository's constructor,
 * so the test sees where the repository is built and what the main thread does while it waits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class ConfigPrewarmTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val main: Thread = Looper.getMainLooper().thread
    private val users get() = shadowOf(app.getSystemService(UserManager::class.java))

    @Volatile private var mainWaited = false

    @Before
    fun storedChoicesOverAPushedTheme() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        File(ThemeDirectory(app).dir!!, "config.json").writeText("""{"columns": 5}""")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = ConfigRepository(app, scope)
        runBlocking {
            repo.set(Choices.Columns, 7)
            withTimeout(10_000) { repo.config.first { it.columns == 7 } }
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun the_repository_is_built_off_the_main_thread_from_the_start_and_the_first_read_waits_for_it() {
        val base = HeldContext(app, main)
        val application = start(base)

        assertTrue("nothing built the repository", base.entered.await(10, TimeUnit.SECONDS))
        assertNotSame(main, base.offMain.single())

        val columns = readWhileOffMainIsHeld(base) { application.graph.configRepository.config.value.columns }

        assertTrue("the read did not wait for the build", mainWaited)
        assertEquals(7, columns)
    }

    @Test
    fun a_locked_start_builds_nothing_and_the_first_read_still_gets_the_choices_after_the_unlock() {
        users.setUserUnlocked(false)
        val base = HeldContext(app, main)
        val application = start(base)

        val config = readWhileOffMainIsHeld(base) { application.graph.configRepository.config }

        assertTrue(base.offMain.isEmpty())
        assertEquals(5, config.value.columns)

        users.setUserUnlocked(true)
        app.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        shadowOf(Looper.getMainLooper()).idle()

        runBlocking { withTimeout(10_000) { config.first { it.columns == 7 } } }
    }

    private fun start(base: Context): TvHomeApplication =
        TvHomeApplication().also {
            ContextWrapper::class.java
                .getDeclaredMethod("attachBaseContext", Context::class.java)
                .apply { isAccessible = true }
                .invoke(it, base)
            it.onCreate()
        }

    /** Runs [read] on main and lets the held threads go once main is blocked on them, or when [read] is done. */
    private fun <T> readWhileOffMainIsHeld(
        base: HeldContext,
        read: () -> T,
    ): T {
        val done = AtomicBoolean()
        val releaser =
            thread {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (!done.get() && main.state != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.onSpinWait()
                mainWaited = main.state == Thread.State.BLOCKED
                base.release.countDown()
            }
        try {
            return read()
        } finally {
            done.set(true)
            releaser.join()
        }
    }
}

private class HeldContext(
    base: Context,
    private val main: Thread,
) : ContextWrapper(base) {
    val offMain = CopyOnWriteArrayList<Thread>()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)

    /** The repository's constructor asks for the user service first. */
    override fun getSystemService(name: String): Any? {
        if (name == USER_SERVICE && Thread.currentThread() !== main) {
            offMain += Thread.currentThread()
            entered.countDown()
            release.await()
        }
        return super.getSystemService(name)
    }
}
