package dev.niltsiar.tvhome

import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.service.media.NowPlayingAccess
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** The real MainActivity switching the Now playing listener. Its own SDK level: one MainActivity per class loader. */
val NowPlayingListenerTest by testSuite {
    robolectricTestSuite<NowPlayingListenerTestContent>(
        "sdk 32",
        testConfig =
            TestConfig.robolectric {
                sdk = 32
                application = TvHomeApplication::class
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class NowPlayingListenerTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val app: Application = RuntimeEnvironment.getApplication()
                val compose = createAndroidComposeRule<MainActivity>()

                val rules: RuleChain =
                    rule(
                        RuleChain
                            .outerRule(
                                object : ExternalResource() {
                                    override fun before() {
                                        shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
                                    }
                                },
                            ).around(compose),
                    )

                fun listenerState() = app.packageManager.getComponentEnabledSetting(NowPlayingAccess.component(app))

                fun choose(on: Boolean) = runBlocking { (app as TvHomeApplication).graph.configRepository.set(Choices.NowPlaying, on) }
            }
        } asContextForEach {
            test("home turns the now playing listener on while the choice is on and back to its manifest state when it is off") {
                listenerState() shouldBe PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

                choose(true)
                compose.waitUntil(30_000) { listenerState() == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }

                choose(false)
                compose.waitUntil(30_000) { listenerState() == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT }
            }
        }
    })
