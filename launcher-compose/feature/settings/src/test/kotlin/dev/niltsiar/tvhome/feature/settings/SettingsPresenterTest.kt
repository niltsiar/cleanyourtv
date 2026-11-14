package dev.niltsiar.tvhome.feature.settings

import android.app.Application
import androidx.compose.ui.graphics.Color
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.disable
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.ClockFormat
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.config.parseTvConfig
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import dev.niltsiar.tvhome.core.model.WallpaperPicture
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.nio.file.Files
import java.util.Locale

/** The presenter over the real repository: a pick is stored, and the rows read back what the store holds. */
val SettingsPresenterTest by testSuite {
    robolectricTestSuite<SettingsPresenterTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

class SettingsPresenterTestContent :
    RobolectricTestSuiteContent({
        // A root user writes to a folder that is not writable: a refused write cannot be made, so those tests are skipped.
        val refusedWrite =
            Files.createTempDirectory("refused").toFile().run {
                setWritable(false)
                val writes = canWrite()
                setWritable(true)
                delete()
                if (writes) TestConfig.disable() else TestConfig
            }

        class RowSpec(
            val id: SettingsRowId,
            val values: List<Any?>,
            val read: (TvConfig) -> Any?,
        )

        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()
                var harness: SettingsHarness? = null

                fun settings(
                    theme: String = "{}",
                    apps: List<OpenOnStartApp> = emptyList(),
                    appsGate: Deferred<Unit>? = null,
                    wallpapers: List<WallpaperPicture> = emptyList(),
                    themeDefault: WallpaperPicture? = null,
                ) = SettingsHarness(app, theme, apps = apps, appsGate = appsGate, wallpapers = wallpapers, themeDefault = themeDefault)
                    .also { harness = it }

                val withChoice = """{"open_on_start_choice": true}"""
                val apps =
                    listOf(OpenOnStartApp("Alpha", "com.alpha"), OpenOnStartApp("Beta", "com.beta"), OpenOnStartApp("Gamma", "com.gamma"))

                val specs =
                    listOf(
                        RowSpec(SettingsRowId.Columns, listOf(4, 5, 6, 7)) { it.columns },
                        RowSpec(SettingsRowId.RowsBelowDock, listOf(0, 1, 2, 3)) { it.rowsBelowDock },
                        RowSpec(SettingsRowId.DockOpacity, listOf(null, 100, 70, 50, 30)) { it.dockOpacity },
                        RowSpec(
                            SettingsRowId.ClockFormat,
                            listOf(ClockFormat.System, ClockFormat.Hour12, ClockFormat.Hour24),
                        ) { it.clockFormat },
                        RowSpec(SettingsRowId.Animations, onOff) { it.animations },
                        RowSpec(SettingsRowId.AlwaysShowNames, onOff) { it.alwaysShowNames },
                        RowSpec(SettingsRowId.ShowGreeting, onOff) { it.showGreeting },
                        RowSpec(SettingsRowId.DateBelowClock, onOff) { it.dateBelowClock },
                        RowSpec(SettingsRowId.MoveToMenu, onOff) { it.moveToMenu },
                        RowSpec(SettingsRowId.AppLibrary, onOff) { it.appLibrary },
                        RowSpec(SettingsRowId.LibraryLeastUsed, onOff) { it.libraryLeastUsed },
                        RowSpec(SettingsRowId.UnusedFadeDays, listOf(0, 30, 60, 90)) { it.unusedFadeDays },
                        RowSpec(SettingsRowId.HomeIdleClockMinutes, listOf(0, 5, 10, 30, 60)) { it.homeIdleClockMinutes },
                        RowSpec(SettingsRowId.BackOpensClock, onOff) { it.backOpensClock },
                        RowSpec(SettingsRowId.ClockWallpaper, onOff) { it.clockWallpaper },
                        RowSpec(SettingsRowId.ReducedMotion, onOff) { it.reducedMotion },
                        RowSpec(SettingsRowId.FocusOvershoot, onOff) { it.focusOvershoot },
                        RowSpec(SettingsRowId.PressFeedback, onOff) { it.pressFeedback },
                        RowSpec(SettingsRowId.HabitHint, onOff) { it.habitHint },
                        RowSpec(SettingsRowId.InputArrival, onOff) { it.inputArrival },
                    )

                fun SettingsHarness.openWallpaperPicker(): WallpaperPicker {
                    viewModel.actions.onOpenWallpaperPicker()
                    return awaitState { it.wallpaperPicker != null }.wallpaperPicker!!
                }

                fun SettingsHarness.storeWallpaper(value: String) = runBlocking { repository.set(Choices.WallpaperChoice, value) }

                fun SettingsHarness.selections() = state.sections.flatMap { it.rows }.associate { it.id to it.selected }
            }
        } closeWith {
            harness?.stop()
        } asContextForEach {
            test("every option of every row stores its own value in its own choice") {
                val settings = settings(LAST_OPTIONS)
                (
                    settings.state.sections
                        .flatMap { it.rows }
                        .map { it.id }
                ) shouldBe (specs.map { it.id })

                for (spec in specs) {
                    for ((index, value) in spec.values.withIndex()) {
                        val others = settings.selections() - spec.id
                        settings.pick(spec.id, index)
                        settings.awaitState { it.row(spec.id).selected == index }

                        withClue("${spec.id} option $index") { spec.read(settings.config) shouldBe value }
                        withClue("${spec.id} option $index leaves the other rows") { (settings.selections() - spec.id) shouldBe others }
                    }
                    withClue("${spec.id} lists its options") { settings.row(spec.id).options.size shouldBe spec.values.size }
                }
            }

            test("the rows come in javas order then the groups") {
                val sections = settings().state.sections

                (
                    sections.map {
                        it.heading
                    }
                ) shouldBe
                    listOf(
                        R.string.settings_group_general,
                        R.string.settings_group_home,
                        R.string.settings_group_library,
                        R.string.settings_group_clock,
                        R.string.settings_group_motion,
                    )
                (sections.map { section -> section.rows.map { it.id } }) shouldBe
                    listOf(
                        listOf(
                            SettingsRowId.Columns,
                            SettingsRowId.RowsBelowDock,
                            SettingsRowId.DockOpacity,
                            SettingsRowId.ClockFormat,
                            SettingsRowId.Animations,
                        ),
                        listOf(
                            SettingsRowId.AlwaysShowNames,
                            SettingsRowId.ShowGreeting,
                            SettingsRowId.DateBelowClock,
                            SettingsRowId.MoveToMenu,
                        ),
                        listOf(SettingsRowId.AppLibrary, SettingsRowId.LibraryLeastUsed, SettingsRowId.UnusedFadeDays),
                        listOf(SettingsRowId.HomeIdleClockMinutes, SettingsRowId.BackOpensClock, SettingsRowId.ClockWallpaper),
                        listOf(
                            SettingsRowId.ReducedMotion,
                            SettingsRowId.FocusOvershoot,
                            SettingsRowId.PressFeedback,
                            SettingsRowId.HabitHint,
                            SettingsRowId.InputArrival,
                        ),
                    )
            }

            test("a row selects the option the theme holds and shows none for a value it does not offer") {
                val settings =
                    settings(
                        """{"columns": 6, "rows_below_dock": 2, "unused_fade_days": 45, "home_idle_clock_minutes": 7, "dock_opacity": 80}""",
                    )

                settings.row(SettingsRowId.Columns).selected shouldBe 2
                settings.row(SettingsRowId.RowsBelowDock).selected shouldBe 2
                settings.row(SettingsRowId.UnusedFadeDays).selected shouldBe -1
                settings.row(SettingsRowId.HomeIdleClockMinutes).selected shouldBe -1
                settings.row(SettingsRowId.DockOpacity).selected shouldBe -1

                settings.pick(SettingsRowId.UnusedFadeDays, 2)
                settings.awaitState { it.row(SettingsRowId.UnusedFadeDays).selected == 2 }
                settings.config.unusedFadeDays shouldBe 60
            }

            test("columns outside the four to seven of the page show no selection") {
                settings("""{"columns": 8}""").row(SettingsRowId.Columns).selected shouldBe -1
            }

            test("auto dock opacity is selected while the theme sets no number") {
                settings().row(SettingsRowId.DockOpacity).selected shouldBe 0
            }

            test("picking auto overrides the themes dock opacity") {
                val settings = settings("""{"dock_opacity": 80}""")
                settings.row(SettingsRowId.DockOpacity).selected shouldBe -1

                settings.pick(SettingsRowId.DockOpacity, 0)
                settings.awaitState { it.row(SettingsRowId.DockOpacity).selected == 0 }

                settings.config.dockOpacity shouldBe null
            }

            test("follow theme is selected only while no choice is stored and picking it clears every choice") {
                val theme = """{"columns": 5}"""
                val settings = settings(theme)
                settings.state.followsTheme.shouldBeTrue()

                settings.pick(SettingsRowId.Columns, 3)
                settings.pick(SettingsRowId.AlwaysShowNames, 0)
                settings.awaitState { !it.followsTheme && it.row(SettingsRowId.AlwaysShowNames).selected == 0 }
                settings.config.columns shouldBe 7

                settings.followTheme()
                settings.awaitState { it.followsTheme && it.row(SettingsRowId.Columns).selected == 1 }

                settings.config shouldBe parseTvConfig(theme).config
            }

            test("picking the option a row shows stores nothing in any row") {
                val settings = settings("""{"columns": 5}""")
                val shown = settings.selections()
                (shown.values.count { it >= 0 }) shouldBe specs.size

                for ((id, option) in shown) settings.pick(id, option)

                settings.staysFollowingTheme().shouldBeTrue()
                settings.state.followsTheme.shouldBeTrue()
            }

            test("a refused write is reported and the row keeps what the store holds", testConfig = refusedWrite) {
                val settings = settings()
                settings.pick(SettingsRowId.Columns, 1)
                settings.awaitState { it.row(SettingsRowId.Columns).selected == 1 }
                val folder = File(app.filesDir, "datastore")
                folder.setWritable(false)
                try {
                    withClue("a root user writes anyway") { folder.canWrite().shouldBeFalse() }

                    settings.pick(SettingsRowId.Columns, 2)
                    settings.awaitSaveFailed()

                    settings.row(SettingsRowId.Columns).selected shouldBe 1
                } finally {
                    folder.setWritable(true)
                }
            }

            test("the reorder row is offered with launcher settings button") {
                settings("""{"launcher_settings_button": true}""").state.reorder shouldBe true
            }

            test("the reorder row is not offered without it") {
                settings().state.reorder shouldBe false
            }

            test("the open on start row is not offered without its choice key") {
                settings().state.openOnStart.shouldBeNull()
            }

            test("the open on start row reads off while nothing is picked") {
                settings(withChoice).state.openOnStart shouldBe OptionLabel.Res(R.string.settings_off)
            }

            test("the open on start row is empty until the label of the picked app has loaded") {
                val gate = CompletableDeferred<Unit>()
                val settings = settings(withChoice, apps, appsGate = gate)

                settings.storePick("com.beta")
                settings.awaitState { it.openOnStart == OptionLabel.Text("") }
                settings.state.openOnStart shouldBe OptionLabel.Text("")

                gate.complete(Unit)
                settings.awaitState { it.openOnStart == OptionLabel.Text("Beta") }
            }

            test("the open on start row shows the label of the picked app its package when it is gone and off once cleared") {
                val settings = settings(withChoice, apps)

                settings.storePick("com.beta")
                settings.awaitState { it.openOnStart == OptionLabel.Text("Beta") }

                settings.storePick("com.gone")
                settings.awaitState { it.openOnStart == OptionLabel.Text("com.gone") }

                runBlocking { settings.repository.clear(Choices.OpenOnStart) }
                settings.awaitState { it.openOnStart == OptionLabel.Res(R.string.settings_off) }
            }

            test("the picker opens on the apps with the current pick selected and off when the pick is not among them") {
                val settings = settings(withChoice, apps)
                settings.state.picker.shouldBeNull()

                settings.viewModel.actions.onOpenPicker()
                (settings.awaitState { it.picker != null }.picker) shouldBe OpenOnStartPicker(apps, 0)

                settings.viewModel.actions.onClosePicker()
                settings.awaitState { it.picker == null }
                settings.storePick("com.beta")
                settings.viewModel.actions.onOpenPicker()
                (settings.awaitState { it.picker != null }.picker) shouldBe OpenOnStartPicker(apps, 2)

                settings.viewModel.actions.onClosePicker()
                settings.awaitState { it.picker == null }
                settings.storePick("com.gone")
                settings.viewModel.actions.onOpenPicker()
                (settings.awaitState { it.picker != null }.picker) shouldBe OpenOnStartPicker(apps, 0)
            }

            test("closing the picker changes nothing") {
                val settings = settings(withChoice, apps)
                settings.storePick("com.alpha")
                settings.viewModel.actions.onOpenPicker()
                settings.awaitState { it.picker != null }

                settings.viewModel.actions.onClosePicker()
                settings.awaitState { it.picker == null }

                settings.pickedApp shouldBe "com.alpha"
                settings.skippedAt shouldBe emptyList<String?>()
            }

            test("picking an app skips this boot before it stores the pick and closes the picker") {
                val settings = settings(withChoice, apps)
                settings.viewModel.actions.onOpenPicker()
                settings.awaitState { it.picker != null }

                settings.viewModel.actions.onPickStartApp(3)
                val shown =
                    settings.awaitState { it.picker == null && !it.followsTheme && it.openOnStart == OptionLabel.Text("Gamma") }

                shown.openOnStart shouldBe OptionLabel.Text("Gamma")
                settings.pickedApp shouldBe "com.gamma"
                settings.skippedAt shouldBe listOf<String?>(null)
            }

            test("picking off removes the pick and skips nothing") {
                val settings = settings(withChoice, apps)
                settings.storePick("com.beta")
                settings.awaitState { !it.followsTheme }
                settings.viewModel.actions.onOpenPicker()
                settings.awaitState { it.picker != null }

                settings.viewModel.actions.onPickStartApp(0)
                settings.awaitState { it.picker == null && it.followsTheme }

                settings.pickedApp.shouldBeNull()
                settings.state.openOnStart shouldBe OptionLabel.Res(R.string.settings_off)
                settings.skippedAt shouldBe emptyList<String?>()
            }

            test("a pick with no picker open changes nothing") {
                val settings = settings(withChoice, apps)
                settings.storePick("com.beta")
                settings.awaitState { !it.followsTheme }

                settings.viewModel.actions.onPickStartApp(0)
                settings.pick(SettingsRowId.Columns, 0)
                settings.awaitState { it.row(SettingsRowId.Columns).selected == 0 }

                settings.pickedApp shouldBe "com.beta"
            }

            test("follow theme clears the open on start pick") {
                val settings = settings(withChoice, apps)
                settings.storePick("com.beta")
                settings.awaitState { it.openOnStart == OptionLabel.Text("Beta") }

                settings.followTheme()
                settings.awaitState { it.followsTheme && it.openOnStart == OptionLabel.Res(R.string.settings_off) }

                settings.pickedApp.shouldBeNull()
            }

            test("the wallpaper row is offered for a theme with pictures and reads theme default until a choice is stored") {
                val settings = settings(wallpapers = pictures)

                (settings.awaitState { it.wallpaper != null }.wallpaper) shouldBe OptionLabel.Res(R.string.wallpaper_theme_default)
            }

            test("the wallpaper row is not offered for a theme without pictures") {
                settings().state.wallpaper.shouldBeNull()
            }

            test("the wallpaper picker does not open for a theme without pictures") {
                val settings = settings()
                settings.pick(SettingsRowId.Columns, 0)
                settings.viewModel.actions.onOpenWallpaperPicker()
                settings.awaitState { it.row(SettingsRowId.Columns).selected == 0 }

                settings.state.wallpaperPicker.shouldBeNull()
            }

            test("the wallpaper row names the picture without its extension none or theme default") {
                val settings = settings(wallpapers = pictures)
                settings.awaitState { it.wallpaper != null }

                settings.storeWallpaper("dusk.jpg")
                settings.awaitState { it.wallpaper == OptionLabel.Text("Dusk") }

                settings.storeWallpaper("none")
                settings.awaitState { it.wallpaper == OptionLabel.Res(R.string.wallpaper_none) }

                settings.storeWallpaper("gone.jpg")
                settings.awaitState { it.wallpaper == OptionLabel.Res(R.string.wallpaper_theme_default) }
            }

            test("the wallpaper picker lists theme default the pictures and none with the current choice selected") {
                val settings = settings(wallpapers = pictures)
                settings.awaitState { it.wallpaper != null }
                val entries =
                    listOf(
                        OptionLabel.Res(R.string.wallpaper_theme_default),
                        OptionLabel.Res(R.string.wallpaper_theme_picture),
                        OptionLabel.Text("Dusk"),
                        OptionLabel.Text("Dawn"),
                        OptionLabel.Res(R.string.wallpaper_none),
                    )

                settings.openWallpaperPicker() shouldBe WallpaperPicker(entries, pictures, 0)

                settings.viewModel.actions.onClosePicker()
                settings.awaitState { it.wallpaperPicker == null }
                settings.storeWallpaper("dusk.jpg")
                settings.awaitState { it.wallpaper == OptionLabel.Text("Dusk") }
                settings.openWallpaperPicker() shouldBe WallpaperPicker(entries, pictures, 2)

                settings.viewModel.actions.onClosePicker()
                settings.awaitState { it.wallpaperPicker == null }
                settings.storeWallpaper("none")
                settings.awaitState { it.wallpaper == OptionLabel.Res(R.string.wallpaper_none) }
                settings.openWallpaperPicker() shouldBe WallpaperPicker(entries, pictures, 4)

                settings.viewModel.actions.onClosePicker()
                settings.awaitState { it.wallpaperPicker == null }
                settings.storeWallpaper("gone.jpg")
                settings.awaitState { it.wallpaper == OptionLabel.Res(R.string.wallpaper_theme_default) }
                settings.openWallpaperPicker() shouldBe WallpaperPicker(entries, pictures, 0)
            }

            test("the wallpaper picker carries the picture home shows for the theme default and the page the themes backdrop") {
                val settings = settings("""{"background_color": "#203040"}""", wallpapers = pictures, themeDefault = pictures[2])
                settings.awaitState { it.wallpaper != null }

                settings.openWallpaperPicker().themeDefault shouldBe pictures[2]
                settings.state.backdrop shouldBe Color(0xFF203040)
            }

            test("a wallpaper pick stores the file name or none closes the picker and reports it applied") {
                val settings = settings(wallpapers = pictures)
                settings.awaitState { it.wallpaper != null }
                settings.openWallpaperPicker()

                settings.viewModel.actions.onPickWallpaper(3)
                settings.awaitState { it.wallpaperPicker == null && !it.followsTheme && it.wallpaper == OptionLabel.Text("Dawn") }
                settings.wallpaperChoice shouldBe "dawn.jpg"
                settings.awaitApplied()

                settings.openWallpaperPicker()
                settings.viewModel.actions.onPickWallpaper(4)
                settings.awaitState { it.wallpaperPicker == null && it.wallpaper == OptionLabel.Res(R.string.wallpaper_none) }
                settings.wallpaperChoice shouldBe "none"
                settings.awaitApplied()
                settings.noMoreApplied().shouldBeTrue()
            }

            test("theme default removes the choice and reports nothing") {
                val settings = settings(wallpapers = pictures)
                settings.storeWallpaper("dusk.jpg")
                settings.awaitState { it.wallpaper == OptionLabel.Text("Dusk") }
                settings.openWallpaperPicker()

                settings.viewModel.actions.onPickWallpaper(0)
                settings.awaitState { it.wallpaperPicker == null && it.followsTheme }

                settings.wallpaperChoice.shouldBeNull()
                settings.noMoreApplied().shouldBeTrue()
            }

            test("closing the wallpaper picker and a pick with no picker open change nothing") {
                val settings = settings(wallpapers = pictures)
                settings.storeWallpaper("dusk.jpg")
                settings.awaitState { it.wallpaper == OptionLabel.Text("Dusk") }
                settings.openWallpaperPicker()

                settings.viewModel.actions.onClosePicker()
                settings.awaitState { it.wallpaperPicker == null }
                settings.viewModel.actions.onPickWallpaper(4)
                settings.pick(SettingsRowId.Columns, 0)
                settings.awaitState { it.row(SettingsRowId.Columns).selected == 0 }

                settings.wallpaperChoice shouldBe "dusk.jpg"
                settings.noMoreApplied().shouldBeTrue()
            }

            test("follow theme clears the wallpaper choice") {
                val settings = settings(wallpapers = pictures)
                settings.storeWallpaper("none")
                settings.awaitState { it.wallpaper == OptionLabel.Res(R.string.wallpaper_none) }

                settings.followTheme()
                settings.awaitState { it.followsTheme && it.wallpaper == OptionLabel.Res(R.string.wallpaper_theme_default) }

                settings.wallpaperChoice.shouldBeNull()
            }

            test("a refused wallpaper write is reported as a failed save and not as applied", testConfig = refusedWrite) {
                val settings = settings(wallpapers = pictures)
                settings.pick(SettingsRowId.Columns, 1)
                settings.awaitState { it.row(SettingsRowId.Columns).selected == 1 }
                settings.openWallpaperPicker()
                val folder = File(app.filesDir, "datastore")
                folder.setWritable(false)
                try {
                    withClue("a root user writes anyway") { folder.canWrite().shouldBeFalse() }

                    settings.viewModel.actions.onPickWallpaper(2)
                    settings.awaitSaveFailed()

                    settings.wallpaperChoice.shouldBeNull()
                    settings.noMoreApplied().shouldBeTrue()
                } finally {
                    folder.setWritable(true)
                }
            }

            test("a picture shows as a name and its file name is what a pick stores") {
                val settings = settings(wallpapers = listOf(WallpaperPicture("moon_lake.jpg"), WallpaperPicture("golden-hour.png")))
                settings.awaitState { it.wallpaper != null }
                val picker = settings.openWallpaperPicker()
                picker.entries.subList(1, 3) shouldBe listOf(OptionLabel.Text("Moon lake"), OptionLabel.Text("Golden hour"))

                settings.viewModel.actions.onPickWallpaper(1)
                settings.awaitState { it.wallpaperPicker == null && !it.followsTheme }

                settings.wallpaperChoice shouldBe "moon_lake.jpg"
                settings.awaitState { it.wallpaper == OptionLabel.Text("Moon lake") }
            }

            test(
                "a captioned picture shows its caption another its file name and wallpaper jpg the theme picture and a pick stores the file",
            ) {
                val settings =
                    settings(
                        wallpapers =
                            listOf(
                                WallpaperPicture("wallpaper.jpg", single = true),
                                WallpaperPicture("001.jpg", "Dusk over the peaks"),
                                WallpaperPicture("moon_lake.jpg"),
                            ),
                    )
                settings.awaitState { it.wallpaper != null }
                val picker = settings.openWallpaperPicker()
                picker.entries.subList(1, 4) shouldBe
                    listOf(
                        OptionLabel.Res(R.string.wallpaper_theme_picture),
                        OptionLabel.Text("Dusk over the peaks"),
                        OptionLabel.Text("Moon lake"),
                    )

                settings.viewModel.actions.onPickWallpaper(2)
                settings.awaitState { it.wallpaperPicker == null && it.wallpaper == OptionLabel.Text("Dusk over the peaks") }
                settings.wallpaperChoice shouldBe "001.jpg"

                settings.openWallpaperPicker()
                settings.viewModel.actions.onPickWallpaper(1)
                settings.awaitState { it.wallpaperPicker == null && it.wallpaper == OptionLabel.Res(R.string.wallpaper_theme_picture) }
                settings.wallpaperChoice shouldBe "wallpaper.jpg"
            }

            test("picture names lose the extension and separators and start upper case unless two come out the same or blank") {
                wallpaperLabels(listOf("a.jpg", "b.png")) shouldBe listOf("A", "B")
                wallpaperLabels(listOf("moon_lake.jpg", "golden-hour.png")) shouldBe listOf("Moon lake", "Golden hour")
                wallpaperLabels(listOf("a.jpg", "b")) shouldBe listOf("A", "B")
                wallpaperLabels(listOf("my.dusk.jpg")) shouldBe listOf("My.dusk")
                wallpaperLabels(listOf("izmir.jpg"), Locale.forLanguageTag("tr")) shouldBe listOf("İzmir")
                wallpaperLabels(listOf("a.jpg", "a.png")) shouldBe listOf("a.jpg", "a.png")
                wallpaperLabels(listOf("moon_lake.jpg", "moon-lake.jpg")) shouldBe listOf("moon_lake.jpg", "moon-lake.jpg")
                wallpaperLabels(listOf("a.jpg", ".png")) shouldBe listOf("a.jpg", ".png")
            }
        }
    })

private val onOff = listOf(true, false)

/** What the theme's source lists: its single wallpaper, then its folder. */
private val pictures = listOf(WallpaperPicture("wallpaper.jpg", single = true), WallpaperPicture("dusk.jpg"), WallpaperPicture("dawn.jpg"))

/** Every key at the last option of its row, so the first pick of each row differs from what the theme holds. */
private const val LAST_OPTIONS =
    """{"columns": 7, "rows_below_dock": 3, "dock_opacity": 30, "clock_format": "24", "animations": false,
    "always_show_names": false, "show_greeting": false, "date_below_clock": false, "move_to_menu": false, "app_library": false,
    "library_least_used": false, "unused_fade_days": 90, "home_idle_clock_minutes": 60, "back_opens_clock": false,
    "clock_wallpaper": false, "reduced_motion": false, "focus_overshoot": false, "press_feedback": false, "habit_hint": false,
    "input_arrival": false}"""
