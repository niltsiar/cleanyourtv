package dev.niltsiar.tvhome.service.keys

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvContract
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.data.apps.AppLauncher
import dev.niltsiar.tvhome.data.apps.LastOpened
import dev.niltsiar.tvhome.data.apps.LaunchLog
import dev.niltsiar.tvhome.data.system.SystemSettings
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File

val TargetLauncherTest by testSuite {
    robolectricTestSuite<TargetLauncherTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

class TargetLauncherTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()
                val hdmi = "com.tv.live/.HdmiInput/HW5"

                val record = MutableStateFlow(TvConfig(cinematicHome = true))
                val lastOpened = LastOpened(app, record, Dispatchers.Unconfined)
                val appLauncher =
                    AppLauncher(app, record, lastOpened, LaunchLog(File(app.filesDir, "launch_log.txt"), record, Dispatchers.Unconfined))

                fun launcher(
                    liveTvPackage: String? = null,
                    context: Context = app,
                ) = TargetLauncher(
                    context,
                    SystemSettings(app),
                    performGlobal = { false },
                    liveTvPackage = liveTvPackage,
                    opened = appLauncher,
                )

                fun installTvApp(pkg: String) {
                    val component = ComponentName(pkg, "$pkg.Main")
                    shadowOf(app.packageManager).addActivityIfNotPresent(component)
                    shadowOf(app.packageManager).addIntentFilterForActivity(
                        component,
                        IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
                    )
                }

                fun installChannelViewer(pkg: String) {
                    val component = ComponentName(pkg, "$pkg.Channel")
                    shadowOf(app.packageManager).addActivityIfNotPresent(component)
                    shadowOf(app.packageManager).addIntentFilterForActivity(
                        component,
                        IntentFilter(Intent.ACTION_VIEW).apply {
                            addCategory(Intent.CATEGORY_DEFAULT)
                            addDataScheme("content")
                            addDataAuthority(TvContract.AUTHORITY, null)
                            addDataType("vnd.android.cursor.item/channel")
                        },
                    )
                }
            }
        } asContextForEach {
            test("an input key asks the live tv app first") {
                launcher("com.tv.live").launch("input:$hdmi")

                val first = shadowOf(app).nextStartedActivity
                first.`package` shouldBe "com.tv.live"
                first.data shouldBe TvContract.buildChannelUriForPassthroughInput(hdmi)
                shadowOf(app).nextStartedActivity.shouldBeNull()
            }

            test("an input key falls back to the plain intent when the live tv app cannot start it") {
                shadowOf(app).checkActivities(true)
                installChannelViewer("com.files")

                launcher("com.tv.live").launch("input:$hdmi")

                val started = shadowOf(app).nextStartedActivity
                started.`package`.shouldBeNull()
                started.data shouldBe TvContract.buildChannelUriForPassthroughInput(hdmi)
                shadowOf(app).nextStartedActivity.shouldBeNull()
            }

            test("an input key sends only the plain intent without a live tv package") {
                for (unset in listOf(null, "", "  ")) {
                    launcher(unset).launch("input:$hdmi")

                    shadowOf(app).nextStartedActivity.`package`.shouldBeNull()
                    shadowOf(app).nextStartedActivity.shouldBeNull()
                }
            }

            test("an app the system refuses to start is not noted") {
                installTvApp("com.netflix.ninja")
                val refusing =
                    object : ContextWrapper(app) {
                        override fun startActivity(intent: Intent) = throw SecurityException("needs a permission")
                    }

                launcher(context = refusing).launch("com.netflix.ninja")
                lastOpened.times.value shouldBe (emptyMap<String, Long>())

                launcher().launch("com.netflix.ninja")
                lastOpened.times.value.keys shouldBe setOf("com.netflix.ninja/.Main")
            }

            test("an activity target that started is noted under its component") {
                installTvApp("com.netflix.ninja")

                launcher().launch("com.netflix.ninja/.Main")

                lastOpened.times.value.keys shouldBe setOf("com.netflix.ninja/.Main")
            }
        }
    })
