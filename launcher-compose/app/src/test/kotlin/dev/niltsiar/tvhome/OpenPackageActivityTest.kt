package dev.niltsiar.tvhome

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OpenPackageActivityTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val packages = shadowOf(app.packageManager)

    private fun install(
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

    private fun open(uri: String) =
        Robolectric
            .buildActivity(OpenPackageActivity::class.java, Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            .create()
            .get()

    @Test
    fun a_package_with_a_tv_entry_opens_that_entry_and_the_activity_finishes() {
        install("com.netflix.ninja", Intent.CATEGORY_LEANBACK_LAUNCHER)

        val activity = open("market://launch?id=com.netflix.ninja")

        assertEquals(ComponentName("com.netflix.ninja", "com.netflix.ninja.Main"), shadowOf(app).nextStartedActivity.component)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun a_package_with_only_a_phone_entry_opens_that_entry() {
        install("com.phone.app", Intent.CATEGORY_LAUNCHER)

        val activity = open("market://launch?id=com.phone.app")

        assertEquals(ComponentName("com.phone.app", "com.phone.app.Main"), shadowOf(app).nextStartedActivity.component)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun a_package_with_both_entries_prefers_the_tv_one() {
        install("com.both.app", Intent.CATEGORY_LAUNCHER)
        packages.addActivityIfNotPresent(ComponentName("com.both.app", "com.both.app.Tv"))
        packages.addIntentFilterForActivity(
            ComponentName("com.both.app", "com.both.app.Tv"),
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
        )

        open("market://launch?id=com.both.app")

        assertEquals("com.both.app.Tv", shadowOf(app).nextStartedActivity.component?.className)
    }

    @Test
    fun a_missing_blank_or_unknown_id_starts_nothing_and_finishes() {
        listOf("market://launch", "market://launch?id=", "market://launch?id=com.not.installed").forEach { uri ->
            val activity = open(uri)

            assertNull(uri, shadowOf(app).nextStartedActivity)
            assertTrue(uri, activity.isFinishing)
        }
    }
}
