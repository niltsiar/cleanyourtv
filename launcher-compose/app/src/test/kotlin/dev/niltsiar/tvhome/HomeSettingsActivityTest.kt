package dev.niltsiar.tvhome

import android.app.Application
import android.os.Bundle
import android.provider.Settings
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import dev.niltsiar.tvhome.feature.settings.R as SettingsR

val HomeSettingsActivityTest by testSuite {
    robolectricTestSuite<HomeSettingsActivityTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

class HomeSettingsActivityTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()

                fun backdrop() = Robolectric.buildActivity(HomeSettingsActivity::class.java)
            }
        } asContextForEach {
            test("it starts the home picker with the help toast and stays behind it") {
                val controller = backdrop().create().start().resume()

                shadowOf(app).nextStartedActivity.action shouldBe Settings.ACTION_HOME_SETTINGS
                val label = app.applicationInfo.loadLabel(app.packageManager).toString()
                val toast = ShadowToast.getTextOfLatestToast()
                toast shouldBe app.getString(SettingsR.string.home_settings_help, label)
                withClue(toast) { toast.contains(label).shouldBeTrue() }
                withClue(toast) { toast.contains("CleanYourTV").shouldBeFalse() }
                controller.get().isFinishing.shouldBeFalse()
            }

            test("with no picker to answer it says not available and finishes") {
                shadowOf(app).checkActivities(true)

                val activity = backdrop().create().get()

                ShadowToast.getTextOfLatestToast() shouldBe app.getString(R.string.not_available)
                activity.isFinishing.shouldBeTrue()
            }

            test("back from the picker finishes the backdrop") {
                val controller = backdrop().create().start().resume()

                controller.pause()
                controller.get().isFinishing.shouldBeFalse()
                controller.resume()

                controller.get().isFinishing.shouldBeTrue()
            }

            test("recreated with saved state it finishes at the first resume without starting the picker again") {
                val controller = backdrop().create(Bundle()).start()

                shadowOf(app).nextStartedActivity.shouldBeNull()
                controller.resume()

                controller.get().isFinishing.shouldBeTrue()
            }
        }
    })
