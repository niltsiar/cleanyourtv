package dev.niltsiar.tvhome

import android.app.Application
import android.content.res.Configuration
import android.os.Environment
import android.os.IBinder
import android.view.Display
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowEnvironment
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.util.Locale

/**
 * The locale key on a TV set to Spanish, over the real graph. Home is read by its header text, which a MainActivity drawn
 * after another one still shows (MainActivityConfigTest says what it loses).
 */
val LauncherLocaleTest by testSuite {
    robolectricTestSuite<LauncherLocaleTestContent>(
        "sdk 31, es, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                application = TvHomeApplication::class
                qualifiers = "es-w960dp-h540dp-xhdpi"
            },
    )
}

class LauncherLocaleTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createEmptyComposeRule())

                val app: Application = RuntimeEnvironment.getApplication()

                /** The theme file has to be on disk before the graph first reads it. */
                fun writeTheme(json: String) {
                    ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
                    File(ThemeDirectory(app).dir!!, "config.json").writeText(json)
                }

                fun showHome(): MainActivity {
                    shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
                    return Robolectric.buildActivity(MainActivity::class.java).setup().get()
                }

                fun waitForText(text: String) =
                    compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

                fun shows(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        } asContextForEach {
            test("a theme with en shows home in english and dates follow it") {
                writeTheme("""{"locale": "en"}""")

                showHome()

                waitForText("Settings")
                shows("Ajustes") shouldBe false
                Locale.getDefault() shouldBe Locale.ENGLISH
            }

            test("an empty locale keeps home in the tvs spanish") {
                writeTheme("""{"locale": ""}""")

                showHome()

                waitForText("Ajustes")
                shows("Settings") shouldBe false
                Locale.getDefault().language shouldBe "es"
            }

            test("a theme with en shows the launcher settings in english") {
                writeTheme("""{"locale": "en"}""")

                Robolectric.buildActivity(SettingsActivity::class.java).setup()

                waitForText("Launcher settings")
                shows("Ajustes del lanzador") shouldBe false
            }

            test("an empty locale keeps the launcher settings in the tvs spanish") {
                writeTheme("""{"locale": ""}""")

                Robolectric.buildActivity(SettingsActivity::class.java).setup()

                waitForText("Ajustes del lanzador")
                shows("Launcher settings") shouldBe false
            }

            test("a theme with ar lays the launcher settings out right to left") {
                writeTheme("""{"locale": "ar"}""")

                Robolectric.buildActivity(SettingsActivity::class.java).setup()

                waitForText("إعدادات المشغّل")
                compose.onNodeWithTag("option:Columns:0").getBoundsInRoot().left shouldBeGreaterThan
                    compose.onNodeWithTag("option:Columns:1").getBoundsInRoot().left
            }

            test("the theme language stays when the tv language changes while the launcher settings show") {
                writeTheme("""{"locale": "en"}""")
                val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup()
                waitForText("Launcher settings")

                // Robolectric's configurationChange writes the new configuration into the old page's resources, which no
                // TV does, so the relaunch Android does for a language change is played as a close and a new start.
                settings.pause().stop().destroy()
                RuntimeEnvironment.setQualifiers("fr-w960dp-h540dp-xhdpi")
                Robolectric.buildActivity(SettingsActivity::class.java).setup()

                waitForText("Launcher settings")
                shows("Paramètres du lanceur") shouldBe false
            }

            test("the theme language stays in home's compose configuration after night mode changes in place") {
                writeTheme("""{"locale": "en"}""")
                // A context with the activity's own token, as a TV gives: Robolectric's default base is the application's.
                System.setProperty("robolectric.createActivityContexts", "true")
                val home =
                    try {
                        showHome()
                    } finally {
                        System.clearProperty("robolectric.createActivityContexts")
                    }
                var composeConfig = Configuration()
                home.addContentView(
                    ComposeView(home).apply { setContent { composeConfig = LocalConfiguration.current } },
                    ViewGroup.LayoutParams(1, 1),
                )
                compose.waitForIdle()
                composeConfig.locales[0] shouldBe Locale.ENGLISH

                // ActivityThread's own handlers for a change the activity takes in place, process first, then activity.
                // Robolectric's configurationChange() would write the TV's locale into the activity's resources itself.
                val night =
                    Configuration(app.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
                    }
                val thread = RuntimeEnvironment.getActivityThread()
                ReflectionHelpers.callInstanceMethod<Unit>(
                    thread,
                    "handleConfigurationChanged",
                    ClassParameter.from(Configuration::class.java, night),
                )
                val token = ReflectionHelpers.callInstanceMethod<IBinder>(home, "getActivityToken")
                val record = ReflectionHelpers.getField<Map<IBinder, Any>>(thread, "mActivities").getValue(token)
                ReflectionHelpers.callInstanceMethod<Unit>(
                    thread,
                    "handleActivityConfigurationChanged",
                    ClassParameter.from(record.javaClass, record),
                    ClassParameter.from(Configuration::class.java, Configuration()),
                    ClassParameter.from(Int::class.javaPrimitiveType, Display.INVALID_DISPLAY),
                )
                compose.waitForIdle()

                (composeConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) shouldBe Configuration.UI_MODE_NIGHT_YES
                composeConfig.locales[0] shouldBe Locale.ENGLISH
            }
        }
    })
