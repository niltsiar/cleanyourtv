package dev.niltsiar.tvhome.data.system

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SystemSettingsTest {
    private val app = RuntimeEnvironment.getApplication()
    private val packages = shadowOf(app.packageManager)
    private val vendor = "com.vendor.settings/com.vendor.settings.Main"

    private fun settings(vpnApp: String? = null) = SystemSettings(app, listOf("broken", vendor), vpnApp)

    private fun answerStandardAction(activity: String) {
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

    private fun install(packageName: String) = packages.installPackage(PackageInfo().apply { this.packageName = packageName })

    @Test
    fun a_standard_handler_is_used_as_it_is() {
        answerStandardAction("com.android.settings.Settings")
        install("com.vendor.settings")

        assertEquals(Settings.ACTION_SETTINGS, settings().intent(Settings.ACTION_SETTINGS).action)
    }

    @Test
    fun the_fire_os_stub_counts_as_missing_and_the_installed_vendor_entry_opens() {
        answerStandardAction("com.amazon.CTSDummyActivity")
        install("com.vendor.settings")

        assertEquals(ComponentName.unflattenFromString(vendor), settings().intent(Settings.ACTION_SETTINGS).component)
    }

    @Test
    fun without_a_standard_handler_or_vendor_app_the_aosp_tv_settings_main_screen_opens() {
        install("com.android.tv.settings")

        val intent = settings().intent(Settings.ACTION_SETTINGS)

        assertEquals("com.android.tv.settings.MainSettings", intent.component?.className)
    }

    @Test
    fun with_nothing_installed_the_standard_intent_is_returned() {
        val intent = settings().intent(Settings.ACTION_SETTINGS)

        assertEquals(Settings.ACTION_SETTINGS, intent.action)
        assertNull(intent.component)
    }

    private fun installVpnApp() {
        val component = ComponentName("com.vpn.app", "com.vpn.app.Main")
        packages.addActivityIfNotPresent(component)
        packages.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
        )
    }

    @Test
    fun the_network_button_opens_the_vpn_app_when_it_is_installed() {
        installVpnApp()

        assertEquals(true, settings(vpnApp = "com.vpn.app").openNetwork())

        assertEquals("com.vpn.app", shadowOf(app).nextStartedActivity.component?.packageName)
    }

    @Test
    fun the_network_button_opens_wifi_settings_without_a_vpn_app() {
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

        assertEquals(true, settings(vpnApp = "com.vpn.missing").openNetwork())

        assertEquals(Settings.ACTION_WIFI_SETTINGS, shadowOf(app).nextStartedActivity.action)
    }

    @Test
    fun the_wifi_key_opens_wifi_settings_even_with_a_vpn_app() {
        installVpnApp()
        answerStandardAction("com.android.settings.Settings")

        assertEquals(true, settings(vpnApp = "com.vpn.app").openWifiSettings())

        assertEquals(Settings.ACTION_WIFI_SETTINGS, shadowOf(app).nextStartedActivity.action)
    }

    @Test
    fun the_default_constructor_knows_the_vendor_settings_activities() {
        packages.installPackage(PackageInfo().apply { packageName = "com.tcl.settings" })

        assertEquals("com.tcl.settings.ui.MainActivity", SystemSettings(app, null).intent(Settings.ACTION_SETTINGS).component?.className)
    }

    @Test
    fun app_info_is_offered_only_where_the_tv_has_a_page_for_it() {
        assertEquals(false, settings().hasAppInfo("com.example.app"))

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

        assertEquals(true, settings().hasAppInfo("com.example.app"))
    }

    @Test
    fun opening_app_info_sends_the_details_action_for_the_package() {
        assertEquals(true, settings().openAppInfo("com.example.app"))

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals("package:com.example.app", started.dataString)
    }

    @Test
    fun native_settings_exist_only_while_a_vendor_settings_app_is_installed() {
        assertEquals(false, settings().hasNativeSettings())
        assertEquals(false, settings().openNativeSettings())

        install("com.vendor.settings")

        assertEquals(true, settings().hasNativeSettings())
        assertEquals(true, settings().openNativeSettings())
        assertEquals(ComponentName.unflattenFromString(vendor), shadowOf(app).nextStartedActivity.component)
    }
}
