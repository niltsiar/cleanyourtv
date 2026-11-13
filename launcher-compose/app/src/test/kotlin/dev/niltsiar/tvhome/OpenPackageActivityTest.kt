package dev.niltsiar.tvhome

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowEnvironment
import java.io.File

val OpenPackageActivityTest by testSuite {
    robolectricTestSuite<OpenPackageActivityTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

class OpenPackageActivityTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()
                val packages = shadowOf(app.packageManager)

                fun install(
                    packageName: String,
                    category: String,
                ) {
                    val component = ComponentName(packageName, "$packageName.Main")
                    packages.addActivityIfNotPresent(component)
                    packages.addIntentFilterForActivity(
                        component,
                        IntentFilter(Intent.ACTION_MAIN).apply { addCategory(category) },
                    )
                }

                fun open(uri: String) =
                    Robolectric
                        .buildActivity(OpenPackageActivity::class.java, Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                        .create()
                        .get()
            }
        } asContextForEach {
            test("a package with a tv entry opens that entry and the activity finishes") {
                install("com.netflix.ninja", Intent.CATEGORY_LEANBACK_LAUNCHER)

                val activity = open("market://launch?id=com.netflix.ninja")

                shadowOf(app).nextStartedActivity.component shouldBe ComponentName("com.netflix.ninja", "com.netflix.ninja.Main")
                activity.isFinishing.shouldBeTrue()
            }

            test("a package with only a phone entry opens that entry") {
                install("com.phone.app", Intent.CATEGORY_LAUNCHER)

                val activity = open("market://launch?id=com.phone.app")

                shadowOf(app).nextStartedActivity.component shouldBe ComponentName("com.phone.app", "com.phone.app.Main")
                activity.isFinishing.shouldBeTrue()
            }

            test("a package with both entries prefers the tv one") {
                install("com.both.app", Intent.CATEGORY_LAUNCHER)
                packages.addActivityIfNotPresent(ComponentName("com.both.app", "com.both.app.Tv"))
                packages.addIntentFilterForActivity(
                    ComponentName("com.both.app", "com.both.app.Tv"),
                    IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
                )

                open("market://launch?id=com.both.app")

                shadowOf(app).nextStartedActivity.component?.className shouldBe "com.both.app.Tv"
            }

            test("with the cinematic home the app it opens is noted under its home tile key") {
                ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
                File(ThemeDirectory(app).dir!!, "config.json").writeText("""{"cinematic_home": true}""")
                install("com.netflix.ninja", Intent.CATEGORY_LEANBACK_LAUNCHER)

                open("market://launch?id=com.netflix.ninja")

                val opened =
                    runBlocking {
                        (app as TvHomeApplication)
                            .graph.homeSources.lastOpened
                            .first()
                    }
                opened.keys shouldBe setOf("com.netflix.ninja/.Main")
            }

            test("a missing blank or unknown id starts nothing and finishes") {
                listOf("market://launch", "market://launch?id=", "market://launch?id=com.not.installed").forEach { uri ->
                    val activity = open(uri)

                    withClue(uri) { shadowOf(app).nextStartedActivity.shouldBeNull() }
                    withClue(uri) { activity.isFinishing.shouldBeTrue() }
                }
            }
        }
    })
