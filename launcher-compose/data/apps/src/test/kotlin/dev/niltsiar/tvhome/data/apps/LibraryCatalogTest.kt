package dev.niltsiar.tvhome.data.apps

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.LibraryApp
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

val LibraryCatalogTest by testSuite {
    robolectricTestSuite<LibraryCatalogTestContent>("default sdk")
}

class LibraryCatalogTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val context = RuntimeEnvironment.getApplication()
                val packageManager = shadowOf(context.packageManager)
                val installed = mutableSetOf<String>()

                fun install(
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

                fun catalog(config: TvConfig): LibraryCatalog = catalog(MutableStateFlow(config))

                fun catalog(config: MutableStateFlow<TvConfig>): LibraryCatalog =
                    LibraryCatalog(context, config, PackageManagerAppsProvider(context, config))

                fun load(config: TvConfig): List<LibraryApp> = runBlocking { catalog(config).load() }

                val config = TvConfig(hiddenApps = listOf("hidden.tv"), libraryExcludedApps = listOf("factory.menu"))
            }
        } asContextForEach {
            test("every launchable exported app is offered once less this launcher and the excluded apps") {
                install("tv.app", "TV App", Intent.CATEGORY_LEANBACK_LAUNCHER)
                install("tv.app", "TV App", Intent.CATEGORY_LAUNCHER)
                install("phone.app", "Phone App", Intent.CATEGORY_LAUNCHER)
                install("hidden.tv", "Hidden", Intent.CATEGORY_LEANBACK_LAUNCHER)
                install("factory.menu", "Factory", Intent.CATEGORY_LEANBACK_LAUNCHER)
                install("closed.app", "Closed", Intent.CATEGORY_LAUNCHER, exported = false)
                install(context.packageName, "Self", Intent.CATEGORY_LEANBACK_LAUNCHER)

                val apps = load(config).associateBy { it.app.packageName }

                apps.keys shouldBe setOf("tv.app", "phone.app", "hidden.tv")
                apps.getValue("tv.app").app.label shouldBe "TV App"
            }

            test("the library leaves out every build of this launcher and keeps another home app") {
                install(context.packageName + ".benchmark", "Inicio", Intent.CATEGORY_LEANBACK_LAUNCHER)
                install("com.vendor.home", "Inicio", Intent.CATEGORY_LEANBACK_LAUNCHER)

                load(config).map { it.app.packageName }.toSet() shouldBe setOf("com.vendor.home")
            }

            test("an app knows whether it is a tv app and whether it shows on home") {
                install("tv.app", "TV App", Intent.CATEGORY_LEANBACK_LAUNCHER)
                install("phone.app", "Phone App", Intent.CATEGORY_LAUNCHER)
                install("hidden.tv", "Hidden", Intent.CATEGORY_LEANBACK_LAUNCHER)
                install("seeded.phone", "Seeded", Intent.CATEGORY_LAUNCHER)

                val apps = load(config.copy(libraryApps = listOf("seeded.phone"))).associateBy { it.app.packageName }

                (apps.getValue("tv.app").app.leanback && apps.getValue("tv.app").onHome).shouldBeTrue()
                (apps.getValue("phone.app").app.leanback || apps.getValue("phone.app").onHome).shouldBeFalse()
                apps.getValue("hidden.tv").onHome.shouldBeFalse()
                apps.getValue("seeded.phone").onHome.shouldBeTrue()
            }

            test("putting an app on home and taking it off changes what the catalogue says") {
                install("phone.app", "Phone App", Intent.CATEGORY_LAUNCHER)
                install("tv.app", "TV App", Intent.CATEGORY_LEANBACK_LAUNCHER)
                val catalog = catalog(config)

                runBlocking { catalog.setOnHome("phone.app", true) }.shouldBeTrue()
                runBlocking { catalog.setOnHome("tv.app", false) }.shouldBeTrue()

                val apps = runBlocking { catalog.load() }.associateBy { it.app.packageName }
                apps.getValue("phone.app").onHome.shouldBeTrue()
                apps.getValue("tv.app").onHome.shouldBeFalse()
            }

            test("the count starts and stops with app library on the same catalogue") {
                install("tv.app", "TV App", Intent.CATEGORY_LEANBACK_LAUNCHER)
                install("phone.app", "Phone App", Intent.CATEGORY_LAUNCHER)
                val config = MutableStateFlow(TvConfig(appLibrary = false))
                val catalog = catalog(config)

                runBlocking {
                    val counts = Channel<Int>(Channel.UNLIMITED)
                    val collecting = launch { catalog.counts.collect { counts.send(it) } }
                    withTimeoutOrNull(QUIET_MS) { counts.receive() }.shouldBeNull()

                    config.value = TvConfig(appLibrary = true)
                    withTimeout(LOAD_TIMEOUT_MS) { counts.receive() } shouldBe 2

                    config.value = TvConfig(appLibrary = false)
                    install("new.app", "New", Intent.CATEGORY_LEANBACK_LAUNCHER)
                    catalog.setOnHome("new.app", true)
                    withTimeoutOrNull(QUIET_MS) { counts.receive() }.shouldBeNull()

                    config.value = TvConfig(appLibrary = true)
                    withTimeout(LOAD_TIMEOUT_MS) { counts.receive() } shouldBe 3
                    collecting.cancel()
                }
            }

            test("the install time comes from the package") {
                install("new.app", "New", Intent.CATEGORY_LEANBACK_LAUNCHER, installedAt = 1234)

                load(config).single { it.app.packageName == "new.app" }.installedAt shouldBe 1234L
            }
        }
    })

private const val LOAD_TIMEOUT_MS = 30_000L
private const val QUIET_MS = 300L
