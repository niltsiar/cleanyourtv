package dev.niltsiar.tvhome.data.apps

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

val TopBarAppsTest by testSuite {
    robolectricTestSuite<TopBarAppsTestContent>("default sdk")
}

class TopBarAppsTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val context = RuntimeEnvironment.getApplication()
                val packages = shadowOf(context.packageManager)

                fun install(
                    pkg: String,
                    vararg categories: String,
                ) {
                    val component = ComponentName(pkg, "$pkg.Main")
                    packages.addActivityIfNotPresent(component)
                    packages.addIntentFilterForActivity(
                        component,
                        IntentFilter(Intent.ACTION_MAIN).apply { categories.forEach(::addCategory) },
                    )
                }

                fun load(vararg wanted: String) = loadTopBarApps(context, wanted.toList(), iconPx = 22)
            }
        } asContextForEach {
            test("an app opens through its tv activity when it has one") {
                install("com.tv", Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)

                val app = load("com.tv").single()

                app.key shouldBe "com.tv/.Main"
                app.leanback.shouldBeTrue()
                app.image?.width shouldBe 22
            }

            test("a phone only app opens through its launcher activity") {
                install("com.phone", Intent.CATEGORY_LAUNCHER)

                load("com.phone").single().leanback.shouldBeFalse()
            }

            test("a missing app and a repeated one leave the bar in config order") {
                install("com.a", Intent.CATEGORY_LAUNCHER)
                install("com.b", Intent.CATEGORY_LAUNCHER)

                load("com.b", "com.missing", "com.a", "com.b").map { it.packageName } shouldBe listOf("com.b", "com.a")
            }
        }
    })
