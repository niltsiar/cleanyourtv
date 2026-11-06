package dev.niltsiar.tvhome.data.apps

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.LibraryApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LibraryCatalogTest {
    private val context = RuntimeEnvironment.getApplication()
    private val packageManager = shadowOf(context.packageManager)
    private val installed = mutableSetOf<String>()

    private fun install(
        pkg: String,
        label: String,
        category: String,
        exported: Boolean = true,
        installedAt: Long = 0,
    ) {
        val component = ComponentName(pkg, "$pkg.Main")
        if (installed.add(pkg)) {
            val application = ApplicationInfo().apply { packageName = pkg }
            val activity =
                ActivityInfo().apply {
                    packageName = pkg
                    name = component.className
                    this.exported = exported
                    applicationInfo = application
                    nonLocalizedLabel = label
                }
            packageManager.installPackage(
                PackageInfo().apply {
                    packageName = pkg
                    firstInstallTime = installedAt
                    applicationInfo = application
                },
            )
            packageManager.addOrUpdateActivity(activity)
        }
        packageManager.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(category) })
    }

    private fun catalog(config: TvConfig): LibraryCatalog = LibraryCatalog(context, config, PackageManagerAppsProvider(context, config))

    private fun load(config: TvConfig): List<LibraryApp> = runBlocking { catalog(config).load() }

    private val config = TvConfig(hiddenApps = listOf("hidden.tv"), libraryExcludedApps = listOf("factory.menu"))

    @Test
    fun every_launchable_exported_app_is_offered_once_less_this_launcher_and_the_excluded_apps() {
        install("tv.app", "TV App", Intent.CATEGORY_LEANBACK_LAUNCHER)
        install("tv.app", "TV App", Intent.CATEGORY_LAUNCHER)
        install("phone.app", "Phone App", Intent.CATEGORY_LAUNCHER)
        install("hidden.tv", "Hidden", Intent.CATEGORY_LEANBACK_LAUNCHER)
        install("factory.menu", "Factory", Intent.CATEGORY_LEANBACK_LAUNCHER)
        install("closed.app", "Closed", Intent.CATEGORY_LAUNCHER, exported = false)
        install(context.packageName, "Self", Intent.CATEGORY_LEANBACK_LAUNCHER)

        val apps = load(config).associateBy { it.app.packageName }

        assertEquals(setOf("tv.app", "phone.app", "hidden.tv"), apps.keys)
        assertEquals("TV App", apps.getValue("tv.app").app.label)
    }

    @Test
    fun the_library_leaves_out_every_build_of_this_launcher_and_keeps_another_home_app() {
        install(context.packageName + ".benchmark", "Inicio", Intent.CATEGORY_LEANBACK_LAUNCHER)
        install("com.vendor.home", "Inicio", Intent.CATEGORY_LEANBACK_LAUNCHER)

        assertEquals(setOf("com.vendor.home"), load(config).map { it.app.packageName }.toSet())
    }

    @Test
    fun an_app_knows_whether_it_is_a_tv_app_and_whether_it_shows_on_home() {
        install("tv.app", "TV App", Intent.CATEGORY_LEANBACK_LAUNCHER)
        install("phone.app", "Phone App", Intent.CATEGORY_LAUNCHER)
        install("hidden.tv", "Hidden", Intent.CATEGORY_LEANBACK_LAUNCHER)
        install("seeded.phone", "Seeded", Intent.CATEGORY_LAUNCHER)

        val apps = load(config.copy(libraryApps = listOf("seeded.phone"))).associateBy { it.app.packageName }

        assertTrue(apps.getValue("tv.app").app.leanback && apps.getValue("tv.app").onHome)
        assertFalse(apps.getValue("phone.app").app.leanback || apps.getValue("phone.app").onHome)
        assertFalse(apps.getValue("hidden.tv").onHome)
        assertTrue(apps.getValue("seeded.phone").onHome)
    }

    @Test
    fun putting_an_app_on_home_and_taking_it_off_changes_what_the_catalogue_says() {
        install("phone.app", "Phone App", Intent.CATEGORY_LAUNCHER)
        install("tv.app", "TV App", Intent.CATEGORY_LEANBACK_LAUNCHER)
        val catalog = catalog(config)

        assertTrue(runBlocking { catalog.setOnHome("phone.app", true) })
        assertTrue(runBlocking { catalog.setOnHome("tv.app", false) })

        val apps = runBlocking { catalog.load() }.associateBy { it.app.packageName }
        assertTrue(apps.getValue("phone.app").onHome)
        assertFalse(apps.getValue("tv.app").onHome)
    }

    @Test
    fun the_install_time_comes_from_the_package() {
        install("new.app", "New", Intent.CATEGORY_LEANBACK_LAUNCHER, installedAt = 1234)

        assertEquals(1234L, load(config).single { it.app.packageName == "new.app" }.installedAt)
    }
}
