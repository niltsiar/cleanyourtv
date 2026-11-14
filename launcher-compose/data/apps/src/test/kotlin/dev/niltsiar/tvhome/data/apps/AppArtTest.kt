package dev.niltsiar.tvhome.data.apps

import android.app.Application
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment

val AppArtTest by testSuite {
    robolectricTestSuite<AppArtTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class AppArtTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()
                val provider = PackageManagerAppsProvider(app, MutableStateFlow(TvConfig()))
            }
        } asContextForEach {
            test("a tv app gets its tile art at the size asked for") {
                installTvApp(app, "com.tv.app")

                val art = runBlocking { provider.art("com.tv.app", 192, 108) }!!

                (art.width to art.height) shouldBe (192 to 108)
            }

            test("an app without a tv entry point has no art") {
                runBlocking { provider.art("com.not.installed", 192, 108) }.shouldBeNull()
            }
        }
    })
