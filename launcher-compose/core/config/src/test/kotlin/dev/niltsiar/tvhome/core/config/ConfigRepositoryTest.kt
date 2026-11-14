package dev.niltsiar.tvhome.core.config

import android.app.Application
import android.content.Intent
import android.os.Environment
import android.os.Looper
import android.os.UserManager
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowEnvironment
import org.robolectric.shadows.ShadowLog
import java.io.File
import kotlin.coroutines.CoroutineContext

val ConfigRepositoryTest by testSuite {
    robolectricTestSuite<ConfigRepositoryTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class ConfigRepositoryTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()
                val scopes = mutableListOf<CoroutineScope>()
                val users get() = shadowOf(app.getSystemService(UserManager::class.java))

                init {
                    ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
                }

                fun stop() =
                    runBlocking {
                        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
                        scopes.clear()
                    }

                /** A start on unmounted storage saves no signature; then the theme is read again on mounted storage. */
                fun columnsAfterAnUnsignedChoiceAnd(revision: String?): Int {
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
                fun repository(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)): ConfigRepository {
                    scopes += scope
                    return ConfigRepository(app, scope)
                }

                /** Runs [block] on a repository, then ends it, so the next one can open the same file. */
                fun chooseAndStop(block: (ConfigRepository) -> Unit) {
                    block(repository())
                    stop()
                }

                fun <T> ConfigRepository.choose(
                    choice: Choice<T>,
                    value: T,
                ) = runBlocking { set(choice, value) }

                fun awaitConfig(
                    repo: ConfigRepository,
                    until: (TvConfig) -> Boolean,
                ) = runBlocking { withTimeout(30_000) { repo.config.first(until) } }

                fun awaitFollowsTheme(
                    repo: ConfigRepository,
                    expected: Boolean,
                ) = runBlocking { withTimeout(30_000) { repo.followsTheme.first { it == expected } } }

                fun awaitOpenOnStart(
                    repo: ConfigRepository,
                    expected: String?,
                ) = runBlocking { withTimeout(30_000) { repo.openOnStartApp.first { it == expected } } }

                fun awaitWallpaperChoice(
                    repo: ConfigRepository,
                    expected: String?,
                ) = runBlocking { withTimeout(30_000) { repo.wallpaperChoice.first { it == expected } } }

                /** Writes the store's file before a repository opens it. */
                fun writeStore(edit: (MutablePreferences) -> Unit) {
                    val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    val store = PreferenceDataStoreFactory.create(scope = writer) { app.preferencesDataStoreFile("launcher_settings") }
                    runBlocking {
                        store.edit { edit(it) }
                        writer.coroutineContext.job.cancelAndJoin()
                    }
                }

                /** Waits until the repository has logged a message containing [text]. */
                fun awaitLog(text: String) {
                    val deadline = System.nanoTime() + 30_000_000_000L
                    while (ShadowLog.getLogsForTag("TvHome.Config").none { text in it.msg.orEmpty() }) {
                        check(System.nanoTime() < deadline) { "no log with \"$text\"" }
                        Thread.sleep(10)
                    }
                }

                fun themeFile(name: String): File = File(ThemeDirectory(app).dir!!, name)

                /** Writes the theme's files and returns the config they parse to. */
                fun pushTheme(
                    json: String,
                    revision: String? = null,
                ): TvConfig {
                    themeFile("config.json").writeText(json)
                    revision?.let { themeFile("theme_revision").writeText(it) }
                    return parseTvConfig(json).config
                }
            }
        } closeWith { stop() } asContextForEach {
            test("a stored choice wins over the theme inside the reference ranges") {
                val theme =
                    pushTheme("""{"columns": 5, "rows_below_dock": 1, "dock_opacity": 80, "clock_format": "24", "animations": true}""")
                val repo = repository()

                repo.choose(Choices.Columns, 7)
                repo.choose(Choices.RowsBelowDock, 3)
                repo.choose(Choices.DockOpacity, 30)
                repo.choose(Choices.TimeFormat, ClockFormat.Hour12)
                repo.choose(Choices.Animations, false)
                awaitConfig(repo) { it.clockFormat == ClockFormat.Hour12 && it.dockOpacity == 30 && it.rowsBelowDock == 3 }
                repo.config.value shouldBe
                    theme.copy(columns = 7, rowsBelowDock = 3, dockOpacity = 30, clockFormat = ClockFormat.Hour12, animations = false)

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

            test("the options added here overlay the theme") {
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

                repo.config.value shouldBe
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
                    )
            }

            test("auto dock opacity overrides a number in the theme and a number overrides auto") {
                pushTheme("""{"dock_opacity": 80}""")
                val repo = repository()
                repo.config.value.dockOpacity shouldBe 80

                repo.choose(Choices.DockOpacity, null)
                awaitConfig(repo) { it.dockOpacity == null }

                repo.choose(Choices.DockOpacity, 50)
                awaitConfig(repo) { it.dockOpacity == 50 }
            }

            test("follow theme clears every choice") {
                val theme = pushTheme("""{"columns": 5}""")
                val repo = repository()
                repo.choose(Choices.Columns, 7)
                repo.choose(Choices.AlwaysShowNames, true)
                repo.choose(Choices.OpenOnStart, "com.netflix.ninja")
                awaitConfig(repo) { it.columns == 7 }
                awaitOpenOnStart(repo, "com.netflix.ninja")

                runBlocking { repo.followTheme() }

                awaitConfig(repo) { it == theme }
                awaitOpenOnStart(repo, null)
            }

            test("the open on start app follows the store and clearing it is off") {
                pushTheme("""{"columns": 5}""")
                val repo = repository()
                repo.openOnStartApp.value.shouldBeNull()

                repo.choose(Choices.OpenOnStart, "com.netflix.ninja")
                awaitOpenOnStart(repo, "com.netflix.ninja")
                awaitFollowsTheme(repo, false)

                runBlocking { repo.clear(Choices.OpenOnStart) }
                awaitOpenOnStart(repo, null)
                awaitFollowsTheme(repo, true)
            }

            test("the wallpaper choice follows the store and clearing it leaves the theme deciding") {
                pushTheme("""{"columns": 5}""")
                val repo = repository()
                repo.wallpaperChoice.value.shouldBeNull()

                repo.choose(Choices.WallpaperChoice, "dusk.jpg")
                awaitWallpaperChoice(repo, "dusk.jpg")
                awaitFollowsTheme(repo, false)

                repo.choose(Choices.WallpaperChoice, Choices.WALLPAPER_NONE)
                awaitWallpaperChoice(repo, "none")

                runBlocking { repo.clear(Choices.WallpaperChoice) }
                awaitWallpaperChoice(repo, null)
                awaitFollowsTheme(repo, true)

                repo.choose(Choices.WallpaperChoice, "dusk.jpg")
                awaitWallpaperChoice(repo, "dusk.jpg")
                runBlocking { repo.followTheme() }
                awaitWallpaperChoice(repo, null)
            }

            test("a repository opened on a stored wallpaper choice holds it at once") {
                pushTheme("{}")
                chooseAndStop {
                    it.choose(Choices.WallpaperChoice, "dusk.jpg")
                    awaitWallpaperChoice(it, "dusk.jpg")
                }

                repository().wallpaperChoice.value shouldBe "dusk.jpg"
            }

            test("the config follows the theme until a choice is stored open on start included") {
                pushTheme("""{"columns": 5}""")
                val repo = repository()
                repo.followsTheme.value.shouldBeTrue()

                repo.choose(Choices.OpenOnStart, "com.netflix.ninja")
                awaitFollowsTheme(repo, false)

                runBlocking { repo.followTheme() }
                awaitFollowsTheme(repo, true)
            }

            test("the first value of a repository opened on stored choices does not follow the theme") {
                pushTheme("""{"columns": 5}""")
                chooseAndStop {
                    it.choose(Choices.Columns, 7)
                    awaitFollowsTheme(it, false)
                }

                repository().followsTheme.value.shouldBeFalse()
            }

            test("a different theme clears the choices except open on start") {
                pushTheme("""{"columns": 5}""")
                chooseAndStop {
                    it.choose(Choices.Columns, 7)
                    it.choose(Choices.ShowGreeting, false)
                    it.choose(Choices.OpenOnStart, "com.netflix.ninja")
                    it.choose(Choices.WallpaperChoice, "dusk.jpg")
                    awaitConfig(it) { c -> c.columns == 7 }
                    awaitWallpaperChoice(it, "dusk.jpg")
                }

                val theme = pushTheme("""{"columns": 6, "rows_below_dock": 2}""")
                val repo = repository()

                repo.config.value shouldBe theme
                repo.openOnStartApp.value shouldBe "com.netflix.ninja"
                repo.wallpaperChoice.value.shouldBeNull()
            }

            test("a new revision of the same theme clears the choices") {
                pushTheme("""{"columns": 5}""", revision = "1")
                chooseAndStop {
                    it.choose(Choices.Columns, 7)
                    awaitConfig(it) { c -> c.columns == 7 }
                }

                themeFile("theme_revision").writeText("2")

                repository().config.value.columns shouldBe 5
            }

            test("the same theme keeps the choices across a restart") {
                pushTheme("""{"columns": 5}""", revision = "1")
                chooseAndStop {
                    it.choose(Choices.Columns, 7)
                    awaitConfig(it) { c -> c.columns == 7 }
                }

                repository().config.value.columns shouldBe 7
            }

            test("unmounted storage never resets the choices") {
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
                chooseAndStop { it.config.value.columns shouldBe 7 }
                check(hidden.renameTo(config))
                ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)

                repository().config.value.columns shouldBe 7
            }

            test("choices without a saved theme signature stay when the theme has no revision") {
                columnsAfterAnUnsignedChoiceAnd(revision = null) shouldBe 7
            }

            test("choices without a saved theme signature go when a push left a revision") {
                columnsAfterAnUnsignedChoiceAnd(revision = "3") shouldBe 5
            }

            test("a locked user gets the theme alone and the choices arrive with the unlock") {
                pushTheme("""{"columns": 5}""")
                chooseAndStop {
                    it.choose(Choices.Columns, 7)
                    awaitConfig(it) { c -> c.columns == 7 }
                }

                users.setUserUnlocked(false)
                val repo = repository()
                repo.config.value.columns shouldBe 5

                users.setUserUnlocked(true)
                app.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
                shadowOf(Looper.getMainLooper()).idle()

                awaitConfig(repo) { it.columns == 7 }
            }

            test("the first value after an unlock already carries the choices") {
                pushTheme("""{"columns": 5}""")
                chooseAndStop {
                    it.choose(Choices.Columns, 7)
                    it.choose(Choices.DockOpacity, 40)
                    awaitConfig(it) { c -> c.dockOpacity == 40 }
                }

                val collecting = Gate()
                try {
                    val repo = repository(CoroutineScope(SupervisorJob() + collecting))
                    repo.config.value.columns shouldBe 7
                    repo.config.value.dockOpacity shouldBe 40
                } finally {
                    collecting.open()
                }
            }

            test("a choice written between the first read and the collection reaches the config") {
                pushTheme("""{"columns": 5}""")
                val collecting = Gate()
                try {
                    val repo = repository(CoroutineScope(SupervisorJob() + collecting))
                    repo.choose(Choices.Columns, 7)
                    collecting.open()

                    awaitConfig(repo) { it.columns == 7 }
                } finally {
                    collecting.open()
                }
            }

            test("a stored empty pick is off") {
                pushTheme("{}")
                val repo = repository()

                repo.choose(Choices.OpenOnStart, "")

                awaitFollowsTheme(repo, false)
                repo.openOnStartApp.value.shouldBeNull()
            }

            test("a stored value of another type is ignored and a later choice replaces it") {
                val theme = pushTheme("""{"columns": 5, "dock_opacity": 80, "clock_format": "24", "animations": true}""")
                writeStore {
                    it[stringPreferencesKey("dock_opacity")] = "70"
                    it[stringPreferencesKey("columns")] = "6"
                    it[intPreferencesKey("clock_format")] = 12
                    it[stringPreferencesKey("animations")] = "false"
                    it[intPreferencesKey("open_on_start")] = 3
                    it[intPreferencesKey("wallpaper_choice")] = 1
                }

                val repo = repository()

                repo.config.value shouldBe theme
                repo.openOnStartApp.value.shouldBeNull()
                repo.wallpaperChoice.value.shouldBeNull()

                repo.choose(Choices.Columns, 7)
                awaitConfig(repo) { it.columns == 7 }
            }

            test("every choice has its own key name and the list holds every choice of the object") {
                val declared =
                    Choices::class.java.declaredFields
                        .filter { Choice::class.java.isAssignableFrom(it.type) }
                        .map { field -> field.apply { isAccessible = true }.get(Choices) as Choice<*> }

                Choices.all.toSet() shouldBe declared.toSet()
                Choices.all
                    .map { it.key.name }
                    .toSet()
                    .size shouldBe Choices.all.size
            }

            test("a corrupt file gives the theme and a later choice replaces it") {
                val theme = pushTheme("""{"columns": 5}""")
                val file = app.preferencesDataStoreFile("launcher_settings")
                file.parentFile!!.mkdirs()
                file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9))

                val repo = repository()
                repo.config.value shouldBe theme

                repo.choose(Choices.Columns, 7)
                awaitConfig(repo) { it.columns == 7 }
                stop()
                repository().config.value.columns shouldBe 7
            }

            test("a read error keeps the config and the store is followed again once it reads") {
                pushTheme("""{"columns": 5}""")
                chooseAndStop {
                    it.choose(Choices.Columns, 7)
                    awaitConfig(it) { c -> c.columns == 7 }
                }
                // A directory in place of the file reads as an I/O error.
                val file = app.preferencesDataStoreFile("launcher_settings")
                val saved = File(file.parentFile, "saved")
                check(file.renameTo(saved))
                check(file.mkdir())

                val repo = repository()
                awaitLog("trying again")
                repo.config.value.columns shouldBe 5

                check(file.delete())
                check(saved.renameTo(file))
                awaitConfig(repo) { it.columns == 7 }
                repo.choose(Choices.Columns, 6)
                awaitConfig(repo) { it.columns == 6 }
            }

            test("a choice made while the user is locked waits for the store and arrives with the unlock") {
                pushTheme("""{"columns": 5}""")
                users.setUserUnlocked(false)
                val repo = repository()
                val writer = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
                val write = writer.launch { repo.set(Choices.Columns, 7) }
                repo.config.value.columns shouldBe 5

                users.setUserUnlocked(true)
                app.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
                shadowOf(Looper.getMainLooper()).idle()

                awaitConfig(repo) { it.columns == 7 }
                runBlocking { withTimeout(30_000) { write.join() } }
                write.isCancelled.shouldBeFalse()
            }
        }
    })

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
