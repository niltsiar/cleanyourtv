package dev.niltsiar.tvhome.feature.settings

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import dev.niltsiar.tvhome.core.model.WallpaperPicture
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** The screen over the real view model and repository: keys go in at the root, the choices come out of the store. */
val SettingsScreenTest by testSuite {
    robolectricTestSuite<SettingsScreenTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
    robolectricTestSuite<SettingsScreenArabicTestContent>(
        "sdk 31, ar-rSA, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "ar-rSA-w960dp-h540dp-xhdpi"
            },
    )
    robolectricTestSuite<SettingsScreenSpanishTestContent>(
        "sdk 31, es, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "es-w960dp-h540dp-xhdpi"
            },
    )
}

class SettingsScreenTestContent :
    RobolectricTestSuiteContent({
        testFixture { SettingsScreenFixture() } asContextForEach {
            test("focus starts on the selected option of the first row") {
                show("""{"columns": 6}""")

                option(SettingsRowId.Columns, 2).assertIsFocused()
                option(SettingsRowId.Columns, 2).assertIsSelected()
                option(SettingsRowId.Columns, 0).assertIsNotSelected()
            }

            test("up on the first row stays on the screen") {
                show()

                press(Key.DirectionUp)

                option(SettingsRowId.Columns, 0).assertIsFocused()
            }

            test("down visits every row on its selected option and the list scrolls to keep it in view") {
                show("""{"columns": 6, "dock_opacity": 50, "clock_format": "24", "show_greeting": false}""")
                val rows = settings.state.sections.flatMap { it.rows }

                for (row in rows.drop(1)) {
                    press(Key.DirectionDown)

                    val focused = option(row.id, row.selected.coerceAtLeast(0))
                    focused.assertIsFocused()
                    focused.assertIsDisplayed()
                }
                press(Key.DirectionDown)
                compose.onNodeWithTag("settings:follow_theme").assertIsFocused()
                press(Key.DirectionDown)
                compose.onNodeWithTag("settings:default_home").assertIsFocused()
                press(Key.DirectionDown)
                compose.onNodeWithTag("settings:tv_settings").assertIsFocused()
                compose.onNodeWithTag("settings:tv_settings").assertIsDisplayed()
            }

            test("down on the last row and up on the first stay on the screen") {
                show()
                repeat(rowCount() + 2) { press(Key.DirectionDown) }
                compose.onNodeWithTag("settings:tv_settings").assertIsFocused()

                repeat(3) { press(Key.DirectionDown) }
                compose.onNodeWithTag("settings:tv_settings").assertIsFocused()
                compose.onNodeWithTag("settings:tv_settings").assertIsDisplayed()

                repeat(rowCount() + 4) { press(Key.DirectionUp) }
                option(SettingsRowId.Columns, 0).assertIsFocused()
            }

            test("left on a rows first option and right on its last stay on the row") {
                show()
                val rows =
                    listOf(
                        SettingsRowId.Columns,
                        SettingsRowId.RowsBelowDock,
                        SettingsRowId.DockOpacity,
                        SettingsRowId.ClockFormat,
                        SettingsRowId.Animations,
                    )

                rows.forEachIndexed { index, row ->
                    if (index > 0) press(Key.DirectionDown)
                    val options = settings.row(row).options.size

                    repeat(options) { press(Key.DirectionLeft) }
                    option(row, 0).assertIsFocused()
                    repeat(options) { press(Key.DirectionRight) }
                    option(row, options - 1).assertIsFocused()
                }
            }

            test("right then left returns to the same option") {
                show()

                press(Key.DirectionRight)
                option(SettingsRowId.Columns, 1).assertIsFocused()
                press(Key.DirectionRight)
                press(Key.DirectionLeft)
                option(SettingsRowId.Columns, 1).assertIsFocused()
                press(Key.DirectionLeft)
                option(SettingsRowId.Columns, 0).assertIsFocused()
            }

            test("down and up land on the selected option of the next row whatever the option focused") {
                show("""{"rows_below_dock": 2}""")
                repeat(3) { press(Key.DirectionRight) }
                option(SettingsRowId.Columns, 3).assertIsFocused()

                press(Key.DirectionDown)
                option(SettingsRowId.RowsBelowDock, 2).assertIsFocused()
                press(Key.DirectionDown)
                option(SettingsRowId.DockOpacity, 0).assertIsFocused()
                press(Key.DirectionUp)
                option(SettingsRowId.RowsBelowDock, 2).assertIsFocused()
            }

            test("ok on an option stores it and marks it selected") {
                show()

                press(Key.DirectionRight)
                press(Key.DirectionCenter)
                waitUntilIdle { settings.row(SettingsRowId.Columns).selected == 1 }
                compose.waitForIdle()

                option(SettingsRowId.Columns, 1).assertIsSelected()
                option(SettingsRowId.Columns, 0).assertIsNotSelected()
                settings.row(SettingsRowId.Columns).selected shouldBe 1
            }

            test("ok on the selected option stores nothing so the theme keeps deciding") {
                show("""{"columns": 6}""")
                option(SettingsRowId.Columns, 2).assertIsFocused()
                option(SettingsRowId.Columns, 2).assertIsSelected()

                press(Key.DirectionCenter)

                stillFollowsTheme().shouldBeTrue()
                compose.onNodeWithTag("settings:follow_theme").assertIsSelected()
            }

            test("the follow theme button is marked while nothing is stored and ok on it clears the choices") {
                show("""{"columns": 5}""")
                compose.onNodeWithTag("settings:follow_theme").assertIsSelected()
                press(Key.DirectionRight)
                press(Key.DirectionCenter)
                waitUntilIdle { !settings.state.followsTheme }
                compose.waitForIdle()
                compose.onNodeWithTag("settings:follow_theme").assertIsNotSelected()

                goTo(SettingsRowId.InputArrival)
                press(Key.DirectionDown)
                press(Key.DirectionCenter)
                waitUntilIdle { settings.state.followsTheme }
                compose.waitForIdle()

                settings.config.columns shouldBe 5
                compose.onNodeWithTag("settings:follow_theme").assertIsSelected()
            }

            test("ok on tv system settings opens them") {
                show()
                repeat(rowCount() + 2) { press(Key.DirectionDown) }

                press(Key.DirectionCenter)

                tvSettingsOpened shouldBe 1
                defaultHomeOpened shouldBe 0
            }

            test("choose default home sits between follow theme and tv settings and ok on it fires its action") {
                show()
                repeat(rowCount()) { press(Key.DirectionDown) }
                compose.onNodeWithTag("settings:follow_theme").assertIsFocused()

                press(Key.DirectionDown)
                compose.onNodeWithTag("settings:default_home").assertIsFocused()
                compose.onNodeWithTag("settings:default_home").assertTextEquals("Choose default home")
                press(Key.DirectionCenter)
                defaultHomeOpened shouldBe 1
                tvSettingsOpened shouldBe 0

                press(Key.DirectionDown)
                compose.onNodeWithTag("settings:tv_settings").assertIsFocused()
                press(Key.DirectionDown)
                compose.onNodeWithTag("settings:tv_settings").assertIsFocused()
                press(Key.DirectionUp)
                press(Key.DirectionUp)
                compose.onNodeWithTag("settings:follow_theme").assertIsFocused()
            }

            test("with launcher settings button the reorder row comes first has focus at open and ok on it reports") {
                show("""{"launcher_settings_button": true}""")

                compose.onNodeWithTag("settings:reorder").assertIsFocused()
                press(Key.DirectionUp)
                compose.onNodeWithTag("settings:reorder").assertIsFocused()
                press(Key.DirectionDown)
                option(SettingsRowId.Columns, 0).assertIsFocused()
                press(Key.DirectionUp)
                compose.onNodeWithTag("settings:reorder").assertIsFocused()
                press(Key.DirectionCenter)

                reordered shouldBe 1
            }

            test("the last rows stay reachable with the reorder row") {
                show("""{"launcher_settings_button": true}""")

                repeat(rowCount() + 4) { press(Key.DirectionDown) }
                compose.onNodeWithTag("settings:tv_settings").assertIsFocused()
                press(Key.DirectionUp)
                compose.onNodeWithTag("settings:default_home").assertIsFocused()
                press(Key.DirectionUp)
                compose.onNodeWithTag("settings:follow_theme").assertIsFocused()
            }

            test("without launcher settings button there is no reorder row and focus starts on the first option") {
                show()

                compose.onAllNodesWithTag("settings:reorder").fetchSemanticsNodes().size shouldBe 0
                option(SettingsRowId.Columns, 0).assertIsFocused()
            }

            test("the open on start row follows animations and down goes on to the next group") {
                show(WITH_CHOICE)

                repeat(4) { press(Key.DirectionDown) }
                option(SettingsRowId.Animations, 0).assertIsFocused()
                press(Key.DirectionDown)
                startRow().assertIsFocused()
                startRow().assertTextEquals("Open on start", "Off")
                press(Key.DirectionDown)
                option(SettingsRowId.AlwaysShowNames, settings.row(SettingsRowId.AlwaysShowNames).selected).assertIsFocused()
                press(Key.DirectionUp)
                startRow().assertIsFocused()
                press(Key.DirectionUp)
                option(SettingsRowId.Animations, 0).assertIsFocused()
            }

            test("without the choice key there is no open on start row") {
                show()

                compose.onAllNodesWithTag("settings:open_on_start").fetchSemanticsNodes().size shouldBe 0
            }

            test("ok on the row opens the picker with off first and focus on the current pick") {
                showPicker(pick = "com.app5")

                compose.onNodeWithTag("settings:picker").assertIsDisplayed()
                compose.onNodeWithTag("settings:picker:title").assertTextEquals("Open on start")
                entry(5).assertIsFocused()
                entry(5).assertIsSelected()
                entry(5).assertIsDisplayed()
                entry(5).assertTextEquals("App 05")
                repeat(5) { press(Key.DirectionUp) }
                entry(0).assertIsFocused()
                entry(0).assertIsNotSelected()
                entry(0).assertTextEquals("Off")
            }

            test("with no pick the picker focuses off") {
                showPicker()

                entry(0).assertIsFocused()
                entry(0).assertIsSelected()
                entry(0).assertTextEquals("Off")
                entry(0).getBoundsInRoot().top shouldBeLessThan entry(1).getBoundsInRoot().top
                entry(1).getBoundsInRoot().top shouldBeLessThan entry(2).getBoundsInRoot().top
            }

            test("keys from every edge of the picker stay inside it") {
                showPicker()

                press(Key.DirectionUp)
                entry(0).assertIsFocused()
                press(Key.DirectionLeft)
                entry(0).assertIsFocused()
                press(Key.DirectionRight)
                entry(0).assertIsFocused()

                repeat(installed.size) { press(Key.DirectionDown) }
                entry(installed.size).assertIsFocused()
                entry(installed.size).assertIsDisplayed()
                press(Key.DirectionDown)
                entry(installed.size).assertIsFocused()
                press(Key.DirectionRight)
                entry(installed.size).assertIsFocused()
                press(Key.DirectionLeft)
                entry(installed.size).assertIsFocused()
                startRow().assertIsNotFocused()
            }

            test("down on the last entry of a short list does not reach the page behind") {
                showPicker(apps = installed.take(2))

                repeat(3) { press(Key.DirectionDown) }

                entry(2).assertIsFocused()
                compose.onNodeWithTag("settings:follow_theme").assertIsNotFocused()
                compose.onNodeWithTag("settings:tv_settings").assertIsNotFocused()
            }

            test("back closes the picker changes nothing and returns focus to the row") {
                showPicker(pick = "com.app5")
                press(Key.DirectionDown)
                press(Key.DirectionDown)
                entry(7).assertIsFocused()

                back()

                compose.onAllNodesWithTag("settings:picker").fetchSemanticsNodes().size shouldBe 0
                settings.pickedApp shouldBe "com.app5"
                settings.skippedAt shouldBe emptyList<String?>()
                startRow().assertIsFocused()
            }

            test("ok on an app stores it closes the picker and the row names it") {
                showPicker()
                repeat(3) { press(Key.DirectionDown) }

                press(Key.DirectionCenter)
                waitUntilIdle {
                    settings.pickedApp == "com.app3" && settings.state.picker == null &&
                        settings.state.openOnStart == OptionLabel.Text("App 03")
                }
                compose.waitForIdle()

                compose.onAllNodesWithTag("settings:picker").fetchSemanticsNodes().size shouldBe 0
                startRow().assertTextEquals("Open on start", "App 03")
                settings.skippedAt shouldBe listOf<String?>(null)
                startRow().assertIsFocused()
            }

            test("ok on off removes the pick") {
                showPicker(pick = "com.app2")
                repeat(2) { press(Key.DirectionUp) }
                entry(0).assertIsFocused()

                press(Key.DirectionCenter)
                waitUntilIdle { settings.pickedApp == null && settings.state.picker == null }
                compose.waitForIdle()

                startRow().assertTextEquals("Open on start", "Off")
                settings.skippedAt shouldBe emptyList<String?>()
            }

            test("the wallpaper row sits between clock and animations") {
                showWallpaperRow()

                wallpaperRow().assertTextEquals("Wallpaper", "Theme default")
                press(Key.DirectionDown)
                option(SettingsRowId.Animations, 0).assertIsFocused()
                press(Key.DirectionUp)
                wallpaperRow().assertIsFocused()
                press(Key.DirectionUp)
                option(SettingsRowId.ClockFormat, settings.row(SettingsRowId.ClockFormat).selected).assertIsFocused()
            }

            test("without pictures there is no wallpaper row") {
                show()

                compose.onAllNodesWithTag("settings:wallpaper").fetchSemanticsNodes().size shouldBe 0
                repeat(4) { press(Key.DirectionDown) }
                option(SettingsRowId.Animations, 0).assertIsFocused()
            }

            test("the wallpaper picker opens on the current choice with theme default first and none last") {
                showWallpaperPicker(pick = "dusk.jpg")

                compose.onNodeWithTag("settings:picker").assertIsDisplayed()
                compose.onNodeWithTag("settings:picker:title").assertTextEquals("Wallpaper")
                listOf("Theme default", "Theme picture", "Dusk", "Dawn", "None").forEachIndexed {
                    index,
                    text,
                    ->
                    entry(index).assertTextEquals(text)
                }
                entry(2).assertIsFocused()
                entry(2).assertIsSelected()
                entry(0).assertIsNotSelected()
                entry(4).assertIsNotSelected()
            }

            test("keys from every edge of the wallpaper picker stay inside it") {
                showWallpaperPicker()

                press(Key.DirectionUp)
                entry(0).assertIsFocused()
                press(Key.DirectionLeft)
                press(Key.DirectionRight)
                entry(0).assertIsFocused()
                repeat(5) { press(Key.DirectionDown) }
                entry(4).assertIsFocused()
                press(Key.DirectionLeft)
                press(Key.DirectionRight)
                entry(4).assertIsFocused()
                wallpaperRow().assertIsNotFocused()
            }

            test("back closes the wallpaper picker changes nothing and returns focus to the row") {
                showWallpaperPicker(pick = "dusk.jpg")
                press(Key.DirectionDown)

                back()

                compose.onAllNodesWithTag("settings:picker").fetchSemanticsNodes().size shouldBe 0
                settings.wallpaperChoice shouldBe "dusk.jpg"
                settings.noMoreApplied().shouldBeTrue()
                wallpaperRow().assertIsFocused()
            }

            test("ok on a picture stores it closes the picker and the row names it") {
                showWallpaperPicker()
                repeat(3) { press(Key.DirectionDown) }

                press(Key.DirectionCenter)
                waitUntilIdle { settings.wallpaperChoice == "dawn.jpg" && settings.state.wallpaperPicker == null }
                compose.waitForIdle()

                compose.onAllNodesWithTag("settings:picker").fetchSemanticsNodes().size shouldBe 0
                wallpaperRow().assertTextEquals("Wallpaper", "Dawn")
                wallpaperRow().assertIsFocused()
                settings.awaitApplied()
            }

            test("ok on none stores none and ok on theme default removes the choice") {
                showWallpaperPicker()
                repeat(4) { press(Key.DirectionDown) }
                press(Key.DirectionCenter)
                waitUntilIdle { settings.wallpaperChoice == "none" && settings.state.wallpaperPicker == null }
                compose.waitForIdle()
                wallpaperRow().assertTextEquals("Wallpaper", "None")

                press(Key.DirectionCenter)
                waitUntilIdle { settings.state.wallpaperPicker != null }
                compose.waitForIdle()
                entry(4).assertIsFocused()
                repeat(4) { press(Key.DirectionUp) }
                press(Key.DirectionCenter)
                waitUntilIdle { settings.wallpaperChoice == null && settings.state.wallpaperPicker == null }
                compose.waitForIdle()

                wallpaperRow().assertTextEquals("Wallpaper", "Theme default")
                wallpaperRow().assertIsFocused()
            }

            test("with both rows each picker returns focus to its own row") {
                show(WITH_CHOICE, wallpapers = pictures)
                waitUntilIdle { settings.state.wallpaper != null }
                compose.waitForIdle()
                repeat(4) { press(Key.DirectionDown) }
                press(Key.DirectionCenter)
                waitUntilIdle { settings.state.wallpaperPicker != null }
                compose.waitForIdle()
                back()
                wallpaperRow().assertIsFocused()

                press(Key.DirectionDown)
                option(SettingsRowId.Animations, 0).assertIsFocused()
                press(Key.DirectionDown)
                startRow().assertIsFocused()
                press(Key.DirectionCenter)
                waitUntilIdle { settings.state.picker != null }
                compose.waitForIdle()
                back()
                startRow().assertIsFocused()
            }

            /*
             * A page the module does not list itself: the Clock row is in the second section, not the first, so the Wallpaper row is
             * too, and the Open on start row follows a first section of two rows. Focus must still walk every stop top to bottom and
             * back, and each picker must give focus back to its own row.
             */
            test("focus walks a page with its rows in another order and each picker returns to its own row") {
                val byId = settingsSections(TvConfig()).flatMap { it.rows }.associateBy { it.id }

                fun section(
                    heading: Int,
                    vararg ids: SettingsRowId,
                ) = SettingsSection(heading, ids.map { byId.getValue(it) })
                val sections =
                    listOf(
                        section(R.string.settings_group_general, SettingsRowId.Columns, SettingsRowId.Animations),
                        section(
                            R.string.settings_group_home,
                            SettingsRowId.DockOpacity,
                            SettingsRowId.ClockFormat,
                            SettingsRowId.ShowGreeting,
                        ),
                        section(R.string.settings_group_motion, SettingsRowId.InputArrival),
                    )
                val rows = { id: SettingsRowId -> "option:${id.name}:${byId.getValue(id).selected.coerceAtLeast(0)}" }
                val topToBottom =
                    listOf(
                        "settings:reorder",
                        rows(SettingsRowId.Columns),
                        rows(SettingsRowId.Animations),
                        "settings:open_on_start",
                        rows(SettingsRowId.DockOpacity),
                        rows(SettingsRowId.ClockFormat),
                        "settings:wallpaper",
                        rows(SettingsRowId.ShowGreeting),
                        rows(SettingsRowId.InputArrival),
                        "settings:follow_theme",
                        "settings:default_home",
                        "settings:tv_settings",
                    )
                var startPicker by mutableStateOf<OpenOnStartPicker?>(null)
                var wallpaperPicker by mutableStateOf<WallpaperPicker?>(null)
                val actions =
                    SettingsActions(
                        onOpenPicker = { startPicker = OpenOnStartPicker(installed.take(2), 0) },
                        onOpenWallpaperPicker = {
                            wallpaperPicker =
                                WallpaperPicker(listOf(OptionLabel.Res(R.string.wallpaper_none)), emptyList(), 0)
                        },
                        onClosePicker = {
                            startPicker = null
                            wallpaperPicker = null
                        },
                    )
                leaveTouchMode()
                compose.setContent {
                    TvTheme(motion = Motion(animations = false)) {
                        SettingsScreen(
                            state = {
                                SettingsUiState(
                                    sections,
                                    followsTheme = true,
                                    reorder = true,
                                    openOnStart = OptionLabel.Res(R.string.settings_off),
                                    picker = startPicker,
                                    wallpaper = OptionLabel.Res(R.string.wallpaper_theme_default),
                                    wallpaperPicker = wallpaperPicker,
                                )
                            },
                            actions = actions,
                            onChooseDefaultHome = {},
                            onOpenTvSettings = {},
                        )
                    }
                }
                compose.waitForIdle()

                topToBottom.first().let { compose.onNodeWithTag(it).assertIsFocused() }
                for (tag in topToBottom.drop(1)) {
                    press(Key.DirectionDown)
                    compose.onNodeWithTag(tag).assertIsFocused()
                }
                press(Key.DirectionDown)
                compose.onNodeWithTag(topToBottom.last()).assertIsFocused()
                for (tag in topToBottom.dropLast(1).reversed()) {
                    press(Key.DirectionUp)
                    compose.onNodeWithTag(tag).assertIsFocused()
                }
                press(Key.DirectionUp)
                compose.onNodeWithTag(topToBottom.first()).assertIsFocused()

                for (tag in listOf("settings:open_on_start", "settings:wallpaper")) {
                    repeat(topToBottom.indexOf(tag)) { press(Key.DirectionDown) }
                    press(Key.DirectionCenter)
                    waitUntilIdle { compose.onAllNodesWithTag("settings:picker").fetchSemanticsNodes().isNotEmpty() }
                    compose.waitForIdle()
                    back()
                    compose.onNodeWithTag(tag).assertIsFocused()
                    repeat(topToBottom.indexOf(tag)) { press(Key.DirectionUp) }
                }
            }
        }
    })

class SettingsScreenArabicTestContent :
    RobolectricTestSuiteContent({
        testFixture { SettingsScreenFixture() } asContextForEach {
            test("in a right to left language the first option is on the right and the keys follow the screen") {
                show()
                option(SettingsRowId.Columns, 0).getBoundsInRoot().left shouldBeGreaterThan
                    option(SettingsRowId.Columns, 1).getBoundsInRoot().left

                press(Key.DirectionRight)
                option(SettingsRowId.Columns, 0).assertIsFocused()
                press(Key.DirectionLeft)
                option(SettingsRowId.Columns, 1).assertIsFocused()
                press(Key.DirectionRight)
                option(SettingsRowId.Columns, 0).assertIsFocused()
                repeat(4) { press(Key.DirectionLeft) }
                option(SettingsRowId.Columns, 3).assertIsFocused()
            }
        }
    })

class SettingsScreenSpanishTestContent :
    RobolectricTestSuiteContent({
        testFixture { SettingsScreenFixture() } asContextForEach {
            test("in spanish the themes wallpaper jpg is imagen del tema in the picker and the row and a pick stores its file name") {
                showWallpaperPicker()
                entry(1).assertTextEquals("Imagen del tema")

                press(Key.DirectionDown)
                press(Key.DirectionCenter)
                waitUntilIdle { settings.wallpaperChoice == "wallpaper.jpg" && settings.state.wallpaperPicker == null }
                compose.waitForIdle()

                wallpaperRow().assertTextEquals("Fondo", "Imagen del tema")
            }
        }
    })

private class SettingsScreenFixture : JUnit4RulesContext() {
    val compose = createAndroidComposeRule<ComponentActivity>()

    val rules: RuleChain =
        rule(
            RuleChain
                .outerRule(compose)
                .around(
                    object : ExternalResource() {
                        override fun after() {
                            if (::settings.isInitialized) settings.stop()
                        }
                    },
                ),
        )

    val app: Application = RuntimeEnvironment.getApplication()
    lateinit var settings: SettingsHarness
    var tvSettingsOpened = 0
    var defaultHomeOpened = 0
    var reordered = 0

    val installed = (1..12).map { OpenOnStartApp("App %02d".format(it), "com.app$it") }

    fun show(
        theme: String = "{}",
        apps: List<OpenOnStartApp> = installed,
        wallpapers: List<WallpaperPicture> = emptyList(),
    ) {
        settings =
            SettingsHarness(
                app,
                theme,
                uiThread = true,
                apps = apps,
                wallpapers = wallpapers,
            )
        leaveTouchMode()
        compose.setContent {
            TvTheme(motion = Motion(animations = false)) {
                val state = settings.viewModel.uiState.collectAsState()
                SettingsScreen(
                    { state.value },
                    settings.viewModel.actions,
                    onChooseDefaultHome = { defaultHomeOpened++ },
                    onOpenTvSettings = { tvSettingsOpened++ },
                    onReorder = { reordered++ },
                )
            }
        }
        compose.waitForIdle()
    }

    /** A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus. */
    fun leaveTouchMode() =
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** A store write comes back from another thread to the UI thread's queue, so each check first runs what the queue holds. */
    fun waitUntilIdle(condition: () -> Boolean) =
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }

    /** True when no stored choice turns the theme off within a short wait. */
    fun stillFollowsTheme(): Boolean {
        val until = System.nanoTime() + 500_000_000L
        while (System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            if (!settings.state.followsTheme) return false
            Thread.sleep(10)
        }
        return true
    }

    fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    fun startRow() = compose.onNodeWithTag("settings:open_on_start")

    fun entry(index: Int) = compose.onNodeWithTag("picker:$index")

    /** The page with the Open on start row, its picker open. */
    fun showPicker(
        pick: String? = null,
        apps: List<OpenOnStartApp> = installed,
    ) {
        show(WITH_CHOICE, apps)
        pick?.let {
            settings.storePick(it)
            waitUntilIdle { settings.state.openOnStart.let { it is OptionLabel.Text && it.text.isNotEmpty() } }
        }
        repeat(5) { press(Key.DirectionDown) }
        startRow().assertIsFocused()
        press(Key.DirectionCenter)
        waitUntilIdle { settings.state.picker != null }
        compose.waitForIdle()
    }

    fun wallpaperRow() = compose.onNodeWithTag("settings:wallpaper")

    /** The page for a theme with [pictures], its Wallpaper row focused. */
    fun showWallpaperRow(
        theme: String = "{}",
        pick: String? = null,
    ) {
        show(theme, wallpapers = pictures)
        waitUntilIdle { settings.state.wallpaper != null }
        pick?.let {
            settings.storeWallpaper(it)
            waitUntilIdle { settings.state.wallpaper != OptionLabel.Res(R.string.wallpaper_theme_default) }
        }
        compose.waitForIdle()
        repeat(4) { press(Key.DirectionDown) }
        wallpaperRow().assertIsFocused()
    }

    /** The page with the Wallpaper row, its picker open. */
    fun showWallpaperPicker(pick: String? = null) {
        showWallpaperRow(pick = pick)
        press(Key.DirectionCenter)
        waitUntilIdle { settings.state.wallpaperPicker != null }
        compose.waitForIdle()
    }

    fun option(
        row: SettingsRowId,
        index: Int,
    ) = compose.onNodeWithTag("option:${row.name}:$index")

    fun rowCount() = settings.state.sections.sumOf { it.rows.size }

    /** Down from the first row to [row]. */
    fun goTo(row: SettingsRowId) {
        val order =
            settings.state.sections
                .flatMap { it.rows }
                .map { it.id }
        repeat(order.indexOf(row)) { press(Key.DirectionDown) }
    }

    fun SettingsHarness.storeWallpaper(value: String) = runBlocking { repository.set(Choices.WallpaperChoice, value) }
}

private const val WITH_CHOICE = """{"open_on_start_choice": true}"""
private val pictures = listOf(WallpaperPicture("wallpaper.jpg", single = true), WallpaperPicture("dusk.jpg"), WallpaperPicture("dawn.jpg"))
