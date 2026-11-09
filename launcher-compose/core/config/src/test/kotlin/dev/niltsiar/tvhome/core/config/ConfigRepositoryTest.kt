package dev.niltsiar.tvhome.core.config

import android.app.Application
import android.content.Intent
import android.os.Environment
import android.os.Looper
import android.os.UserManager
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ConfigRepositoryTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val scopes = mutableListOf<CoroutineScope>()
    private val users get() = shadowOf(app.getSystemService(UserManager::class.java))

    @Before
    fun mountStorage() = ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)

    @After
    fun stop() =
        runBlocking {
            scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
            scopes.clear()
        }

    @Test
    fun a_stored_choice_wins_over_the_theme_inside_the_reference_ranges() {
        val theme = pushTheme("""{"columns": 5, "rows_below_dock": 1, "dock_opacity": 80, "clock_format": "24", "animations": true}""")
        val repo = repository()

        repo.choose(Choices.Columns, 7)
        repo.choose(Choices.RowsBelowDock, 3)
        repo.choose(Choices.DockOpacity, 30)
        repo.choose(Choices.TimeFormat, ClockFormat.Hour12)
        repo.choose(Choices.Animations, false)
        awaitConfig(repo) { it.clockFormat == ClockFormat.Hour12 && it.dockOpacity == 30 && it.rowsBelowDock == 3 }
        assertEquals(
            theme.copy(columns = 7, rowsBelowDock = 3, dockOpacity = 30, clockFormat = ClockFormat.Hour12, animations = false),
            repo.config.value,
        )

        // Out of Java's range, a stored value is ignored and the theme's stands.
        repo.choose(Choices.Columns, 3)
        repo.choose(Choices.RowsBelowDock, 4)
        repo.choose(Choices.DockOpacity, 29)
        awaitConfig(repo) { it.columns == 5 && it.rowsBelowDock == 1 && it.dockOpacity == 80 }
        repo.choose(Choices.Columns, 8)
        awaitConfig(repo) { it.columns == 5 }
        repo.choose(Choices.DockOpacity, 101)
        awaitConfig(repo) { it.dockOpacity == 80 }
    }

    @Test
    fun the_options_added_here_overlay_the_theme() {
        val theme = pushTheme("""{"unused_fade_days": 9}""")
        val repo = repository()

        repo.choose(Choices.AlwaysShowNames, true)
        repo.choose(Choices.ShowGreeting, false)
        repo.choose(Choices.DateBelowClock, true)
        repo.choose(Choices.AppLibrary, true)
        repo.choose(Choices.LibraryLeastUsed, true)
        repo.choose(Choices.UnusedFadeDays, 30)
        repo.choose(Choices.HomeIdleClockMinutes, 15)
        repo.choose(Choices.BackOpensClock, true)
        repo.choose(Choices.ClockWallpaper, true)
        repo.choose(Choices.ReducedMotion, true)
        repo.choose(Choices.FocusOvershoot, true)
        repo.choose(Choices.HabitHint, true)
        repo.choose(Choices.InputArrival, true)
        awaitConfig(repo) { it.inputArrival }

        assertEquals(
            theme.copy(
                alwaysShowNames = true,
                showGreeting = false,
                dateBelowClock = true,
                appLibrary = true,
                libraryLeastUsed = true,
                unusedFadeDays = 30,
                homeIdleClockMinutes = 15,
                backOpensClock = true,
                clockWallpaper = true,
                reducedMotion = true,
                focusOvershoot = true,
                habitHint = true,
                inputArrival = true,
            ),
            repo.config.value,
        )
    }

    @Test
    fun auto_dock_opacity_overrides_a_number_in_the_theme_and_a_number_overrides_auto() {
        pushTheme("""{"dock_opacity": 80}""")
        val repo = repository()
        assertEquals(80, repo.config.value.dockOpacity)

        repo.choose(Choices.DockOpacity, null)
        awaitConfig(repo) { it.dockOpacity == null }

        repo.choose(Choices.DockOpacity, 50)
        awaitConfig(repo) { it.dockOpacity == 50 }
    }

    @Test
    fun follow_theme_clears_every_choice() {
        val theme = pushTheme("""{"columns": 5}""")
        val repo = repository()
        repo.choose(Choices.Columns, 7)
        repo.choose(Choices.AlwaysShowNames, true)
        repo.choose(Choices.OpenOnStart, "com.netflix.ninja")
        awaitConfig(repo) { it.columns == 7 }

        runBlocking { repo.followTheme() }

        awaitConfig(repo) { it == theme }
        assertNull(openOnStart(repo))
    }

    @Test
    fun the_config_follows_the_theme_until_a_choice_is_stored_open_on_start_included() {
        pushTheme("""{"columns": 5}""")
        val repo = repository()
        assertTrue(repo.followsTheme.value)

        repo.choose(Choices.OpenOnStart, "com.netflix.ninja")
        awaitFollowsTheme(repo, false)

        runBlocking { repo.followTheme() }
        awaitFollowsTheme(repo, true)
    }

    @Test
    fun the_first_value_of_a_repository_opened_on_stored_choices_does_not_follow_the_theme() {
        pushTheme("""{"columns": 5}""")
        chooseAndStop {
            it.choose(Choices.Columns, 7)
            awaitFollowsTheme(it, false)
        }

        assertFalse(repository().followsTheme.value)
    }

    @Test
    fun a_different_theme_clears_the_choices_except_open_on_start() {
        pushTheme("""{"columns": 5}""")
        chooseAndStop {
            it.choose(Choices.Columns, 7)
            it.choose(Choices.ShowGreeting, false)
            it.choose(Choices.OpenOnStart, "com.netflix.ninja")
            awaitConfig(it) { c -> c.columns == 7 }
        }

        val theme = pushTheme("""{"columns": 6, "rows_below_dock": 2}""")
        val repo = repository()

        assertEquals(theme, repo.config.value)
        assertEquals("com.netflix.ninja", openOnStart(repo))
    }

    @Test
    fun a_new_revision_of_the_same_theme_clears_the_choices() {
        pushTheme("""{"columns": 5}""", revision = "1")
        chooseAndStop {
            it.choose(Choices.Columns, 7)
            awaitConfig(it) { c -> c.columns == 7 }
        }

        themeFile("theme_revision").writeText("2")

        assertEquals(5, repository().config.value.columns)
    }

    @Test
    fun the_same_theme_keeps_the_choices_across_a_restart() {
        pushTheme("""{"columns": 5}""", revision = "1")
        chooseAndStop {
            it.choose(Choices.Columns, 7)
            awaitConfig(it) { c -> c.columns == 7 }
        }

        assertEquals(7, repository().config.value.columns)
    }

    @Test
    fun unmounted_storage_never_resets_the_choices() {
        pushTheme("""{"columns": 5}""")
        chooseAndStop {
            it.choose(Choices.Columns, 7)
            awaitConfig(it) { c -> c.columns == 7 }
        }

        // Unmounted storage shows no theme files; the same files come back when it mounts.
        val config = themeFile("config.json")
        val hidden = File(config.parentFile, "hidden.json")
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_UNMOUNTED)
        check(config.renameTo(hidden))
        chooseAndStop { assertEquals(7, it.config.value.columns) }
        check(hidden.renameTo(config))
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)

        assertEquals(7, repository().config.value.columns)
    }

    @Test
    fun choices_without_a_saved_theme_signature_stay_when_the_theme_has_no_revision() {
        assertEquals(7, columnsAfterAnUnsignedChoiceAnd(revision = null))
    }

    @Test
    fun choices_without_a_saved_theme_signature_go_when_a_push_left_a_revision() {
        assertEquals(5, columnsAfterAnUnsignedChoiceAnd(revision = "3"))
    }

    @Test
    fun a_locked_user_gets_the_theme_alone_and_the_choices_arrive_with_the_unlock() {
        pushTheme("""{"columns": 5}""")
        chooseAndStop {
            it.choose(Choices.Columns, 7)
            awaitConfig(it) { c -> c.columns == 7 }
        }

        users.setUserUnlocked(false)
        val repo = repository()
        assertEquals(5, repo.config.value.columns)

        users.setUserUnlocked(true)
        app.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        shadowOf(Looper.getMainLooper()).idle()

        awaitConfig(repo) { it.columns == 7 }
    }

    @Test
    fun the_first_value_after_an_unlock_already_carries_the_choices() {
        pushTheme("""{"columns": 5}""")
        chooseAndStop {
            it.choose(Choices.Columns, 7)
            it.choose(Choices.DockOpacity, 40)
            awaitConfig(it) { c -> c.dockOpacity == 40 }
        }

        val collecting = Gate()
        try {
            val repo = repository(CoroutineScope(SupervisorJob() + collecting))
            assertEquals(7, repo.config.value.columns)
            assertEquals(40, repo.config.value.dockOpacity)
        } finally {
            collecting.open()
        }
    }

    /** A start on unmounted storage saves no signature; then the theme is read again on mounted storage. */
    private fun columnsAfterAnUnsignedChoiceAnd(revision: String?): Int {
        pushTheme("""{"columns": 5}""")
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_UNMOUNTED)
        chooseAndStop {
            it.choose(Choices.Columns, 7)
            awaitConfig(it) { c -> c.columns == 7 }
        }
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        revision?.let { themeFile("theme_revision").writeText(it) }
        return repository().config.value.columns
    }

    /** [scope] defaults to one that collects the store at once; a paused one leaves only the read made at creation. */
    private fun repository(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)): ConfigRepository {
        scopes += scope
        return ConfigRepository(app, scope)
    }

    /** Runs [block] on a repository, then ends it, so the next one can open the same file. */
    private fun chooseAndStop(block: (ConfigRepository) -> Unit) {
        block(repository())
        stop()
    }

    private fun <T> ConfigRepository.choose(
        choice: Choice<T>,
        value: T,
    ) = runBlocking { set(choice, value) }

    private fun awaitConfig(
        repo: ConfigRepository,
        until: (TvConfig) -> Boolean,
    ) = runBlocking { withTimeout(10_000) { repo.config.first(until) } }

    private fun awaitFollowsTheme(
        repo: ConfigRepository,
        expected: Boolean,
    ) = runBlocking { withTimeout(10_000) { repo.followsTheme.first { it == expected } } }

    private fun openOnStart(repo: ConfigRepository): String? = runBlocking { repo.stored.first()[stringPreferencesKey("open_on_start")] }

    private fun themeFile(name: String): File = File(ThemeDirectory(app).dir!!, name)

    /** Writes the theme's files and returns the config they parse to. */
    private fun pushTheme(
        json: String,
        revision: String? = null,
    ): TvConfig {
        themeFile("config.json").writeText(json)
        revision?.let { themeFile("theme_revision").writeText(it) }
        return parseTvConfig(json).config
    }
}

/** Holds back what is dispatched to it until [open], then runs it on the default dispatcher. */
private class Gate : CoroutineDispatcher() {
    private val held = mutableListOf<Pair<CoroutineContext, Runnable>>()
    private var open = false

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        val hold = synchronized(held) { (!open).also { if (it) held += context to block } }
        if (!hold) Dispatchers.Default.dispatch(context, block)
    }

    fun open() {
        val pending =
            synchronized(held) {
                open = true
                held.toList().also { held.clear() }
            }
        pending.forEach { (context, block) -> Dispatchers.Default.dispatch(context, block) }
    }
}
