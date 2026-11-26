package dev.niltsiar.tvhome.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.focusStyle
import dev.niltsiar.tvhome.core.designsystem.golden.assertMatchesGolden
import dev.niltsiar.tvhome.core.designsystem.golden.bands
import dev.niltsiar.tvhome.core.designsystem.motion
import dev.niltsiar.tvhome.core.model.OpenOnStartApp

/** Golden images of the settings page in each look, over a fixed state (docs/testing.md, "Golden tests"). */
val SettingsGoldenTest by testSuite {
    robolectricTestSuite<SettingsGoldenTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class SettingsGoldenTestContent :
    RobolectricTestSuiteContent({
        testFixture { SettingsGoldenFixture() } asContextForEach {
            test("the top of the page") {
                show()
                golden("page-top")
            }

            test("the open on start picker") {
                picker = sixApps
                show()
                compose.onNodeWithTag("picker:0").assertExists()
                golden("picker-open")
            }

            test("an open on start picker 0, 80 and 160 ms after ok on its row opens it, in Expressive") {
                show(look = Look.Expressive)
                compose.onNodeWithTag("settings:open_on_start").requestFocus()
                compose.mainClock.advanceTimeBy(2_000)
                picker = sixApps
                while (compose.onAllNodesWithTag("settings:picker").fetchSemanticsNodes().isEmpty()) compose.mainClock.advanceTimeByFrame()
                val opened = compose.mainClock.currentTime
                for (ms in listOf(0L, 80L, 160L)) {
                    while (compose.mainClock.currentTime - opened < ms) compose.mainClock.advanceTimeByFrame()
                    golden("expressive-picker-grow-${ms}ms")
                }
            }

            test("a failed save") {
                show()
                compose.runOnUiThread { notice.showWarning(R.string.settings_save_failed) }
                compose.waitForIdle()
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitForIdle()
                compose.onNodeWithTag("settings:notice").assertExists()
                golden("notice")
            }
        }
    })

/** The house seed of the Expressive scenes, the warm one the design-system goldens use. */
private val Ember = Color(0xFFFF6A3D)

private class SettingsGoldenFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val notice = PageNotice()

    /** The open on start picker the page shows, or null. */
    var picker by mutableStateOf<OpenOnStartPicker?>(null)

    val sixApps =
        OpenOnStartPicker(
            apps = (1..6).map { OpenOnStartApp(label = "App $it", packageName = "pkg.$it") },
            selected = 2,
        )

    /** Each app's art: two bands in hues that follow its package number. */
    val art =
        PickerArt(
            app = { pkg, _, _ ->
                val n = pkg.removePrefix("pkg.").toInt()
                bands(32, 18, listOf(Color.hsv((n * 47f) % 360f, 0.55f, 0.75f), Color.hsv((n * 47f + 141f) % 360f, 0.55f, 0.75f)))
            },
        )

    fun show(look: Look = Look.Glass) {
        val config = TvConfig()
        compose.mainClock.autoAdvance = false
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            TvTheme(look = look, seed = { Ember }, motion = config.motion(), focusStyle = config.focusStyle(), darkTheme = true) {
                // The settings activities have a black window.
                Box(Modifier.fillMaxSize().background(Color.Black))
                SettingsScreen(
                    state = {
                        SettingsUiState(
                            settingsSections(config),
                            followsTheme = true,
                            openOnStart = OptionLabel.Res(R.string.settings_off),
                            picker = picker,
                            wallpaper = OptionLabel.Res(R.string.wallpaper_theme_default),
                            backdrop = Color(0xFF203040),
                        )
                    },
                    actions = SettingsActions(),
                    onChooseDefaultHome = {},
                    onOpenTvSettings = {},
                    notice = notice,
                    art = art,
                )
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    fun golden(scene: String) =
        compose
            .onRoot()
            .captureToImage()
            .assertMatchesGolden("SettingsGoldenTest", scene)
}
