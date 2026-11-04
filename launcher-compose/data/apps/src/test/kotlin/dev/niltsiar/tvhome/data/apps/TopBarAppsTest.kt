package dev.niltsiar.tvhome.data.apps

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TopBarAppsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val packages = shadowOf(context.packageManager)

    private fun install(
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

    private fun load(vararg wanted: String) = loadTopBarApps(context, wanted.toList(), iconPx = 22)

    @Test
    fun an_app_opens_through_its_tv_activity_when_it_has_one() {
        install("com.tv", Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)

        val app = load("com.tv").single()

        assertEquals("com.tv/.Main", app.key)
        assertTrue(app.leanback)
        assertEquals(22, app.image?.width)
    }

    @Test
    fun a_phone_only_app_opens_through_its_launcher_activity() {
        install("com.phone", Intent.CATEGORY_LAUNCHER)

        assertFalse(load("com.phone").single().leanback)
    }

    @Test
    fun a_missing_app_and_a_repeated_one_leave_the_bar_in_config_order() {
        install("com.a", Intent.CATEGORY_LAUNCHER)
        install("com.b", Intent.CATEGORY_LAUNCHER)

        assertEquals(listOf("com.b", "com.a"), load("com.b", "com.missing", "com.a", "com.b").map { it.packageName })
    }
}
