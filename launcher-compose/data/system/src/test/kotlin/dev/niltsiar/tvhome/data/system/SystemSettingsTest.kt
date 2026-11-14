package dev.niltsiar.tvhome.data.system

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

val SystemSettingsTest by testSuite {
    robolectricTestSuite<SystemSettingsTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class SystemSettingsTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app = RuntimeEnvironment.getApplication()
                val packages = shadowOf(app.packageManager)
                val vendor = "com.vendor.settings/com.vendor.settings.Main"

                val opened = mutableListOf<Intent>()

                fun settings(vpnApp: String? = null) = SystemSettings(app, listOf("broken", vendor), opened::add) { vpnApp }

                fun answerStandardAction(activity: String) {
                    val info =
                        ResolveInfo().apply {
                            activityInfo =
                                ActivityInfo().apply {
                                    name = activity
                                    packageName = "com.android.settings"
                                }
                        }
                    packages.addResolveInfoForIntent(Intent(Settings.ACTION_SETTINGS), info)
                }

                fun install(packageName: String) = packages.installPackage(PackageInfo().apply { this.packageName = packageName })

                fun installVpnApp() {
                    val component = ComponentName("com.vpn.app", "com.vpn.app.Main")
                    packages.addActivityIfNotPresent(component)
                    packages.addIntentFilterForActivity(
                        component,
                        IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
                    )
                }
            }
        } asContextForEach {
            test("a standard handler is used as it is") {
                answerStandardAction("com.android.settings.Settings")
                install("com.vendor.settings")

                settings().intent(Settings.ACTION_SETTINGS).action shouldBe Settings.ACTION_SETTINGS
            }

            test("settings open in a new task and clear the old one only when asked") {
                answerStandardAction("com.android.settings.Settings")

                settings().openSettings().shouldBeTrue()
                shadowOf(app).nextStartedActivity.flags shouldBe Intent.FLAG_ACTIVITY_NEW_TASK

                settings().openSettings(clearTask = true).shouldBeTrue()
                shadowOf(app).nextStartedActivity.flags shouldBe (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            test("a vendor settings activity keeps the clear task flag") {
                install("com.vendor.settings")

                settings().openSettings(clearTask = true).shouldBeTrue()

                val started = shadowOf(app).nextStartedActivity
                started.component shouldBe ComponentName.unflattenFromString(vendor)
                started.flags shouldBe (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            test("the fire os stub counts as missing and the installed vendor entry opens") {
                answerStandardAction("com.amazon.CTSDummyActivity")
                install("com.vendor.settings")

                settings().intent(Settings.ACTION_SETTINGS).component shouldBe ComponentName.unflattenFromString(vendor)
            }

            test("without a standard handler or vendor app the aosp tv settings main screen opens") {
                install("com.android.tv.settings")

                val intent = settings().intent(Settings.ACTION_SETTINGS)

                intent.component?.className shouldBe "com.android.tv.settings.MainSettings"
            }

            test("with nothing installed the standard intent is returned") {
                val intent = settings().intent(Settings.ACTION_SETTINGS)

                intent.action shouldBe Settings.ACTION_SETTINGS
                intent.component.shouldBeNull()
            }

            test("the network button opens the vpn app when it is installed") {
                installVpnApp()

                settings(vpnApp = "com.vpn.app").openNetwork().shouldBeTrue()

                shadowOf(app).nextStartedActivity.component?.packageName shouldBe "com.vpn.app"
                opened.map { it.component } shouldBe listOf(ComponentName("com.vpn.app", "com.vpn.app.Main"))
            }

            test("the network button opens wifi settings without a vpn app") {
                answerStandardAction("com.android.settings.Settings")
                packages.addResolveInfoForIntent(
                    Intent(Settings.ACTION_WIFI_SETTINGS),
                    ResolveInfo().apply {
                        activityInfo =
                            ActivityInfo().apply {
                                name = "Wifi"
                                packageName = "com.android.settings"
                            }
                    },
                )

                settings(vpnApp = "com.vpn.missing").openNetwork().shouldBeTrue()

                shadowOf(app).nextStartedActivity.action shouldBe Settings.ACTION_WIFI_SETTINGS
                opened.shouldBeEmpty()
            }

            test("the wifi key opens wifi settings even with a vpn app") {
                installVpnApp()
                answerStandardAction("com.android.settings.Settings")

                settings(vpnApp = "com.vpn.app").openWifiSettings().shouldBeTrue()

                shadowOf(app).nextStartedActivity.action shouldBe Settings.ACTION_WIFI_SETTINGS
            }

            test("the default constructor knows the vendor settings activities") {
                packages.installPackage(PackageInfo().apply { packageName = "com.tcl.settings" })

                SystemSettings(app).intent(Settings.ACTION_SETTINGS).component?.className shouldBe "com.tcl.settings.ui.MainActivity"
            }

            test("app info is offered only where the tv has a page for it") {
                settings().hasAppInfo("com.example.app").shouldBeFalse()

                packages.addResolveInfoForIntent(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.example.app")),
                    ResolveInfo().apply {
                        activityInfo =
                            ActivityInfo().apply {
                                name = "AppInfo"
                                packageName = "com.android.settings"
                            }
                    },
                )

                settings().hasAppInfo("com.example.app").shouldBeTrue()
            }

            test("opening app info sends the details action for the package") {
                settings().openAppInfo("com.example.app").shouldBeTrue()

                val started = shadowOf(app).nextStartedActivity
                started.action shouldBe Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                started.dataString shouldBe "package:com.example.app"
            }

            test("native settings exist only while a vendor settings app is installed") {
                settings().hasNativeSettings().shouldBeFalse()
                settings().openNativeSettings().shouldBeFalse()

                install("com.vendor.settings")

                settings().hasNativeSettings().shouldBeTrue()
                settings().openNativeSettings().shouldBeTrue()
                shadowOf(app).nextStartedActivity.component shouldBe ComponentName.unflattenFromString(vendor)
            }
        }
    })
