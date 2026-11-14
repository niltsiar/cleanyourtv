package dev.niltsiar.tvhome.data.shelf

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfKind
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private const val APP = "com.edde746.plezy"
private const val OTHER = "com.other"

/** Which intent OK on a shelf item sends: the app's own, only into that app, or else the app's launch intent. */
val ShelfLaunchTest by testSuite {
    robolectricTestSuite<ShelfLaunchTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class ShelfLaunchTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app = RuntimeEnvironment.getApplication()
                val launcher = ComponentName(APP, "$APP.Main")
                val player = ComponentName(APP, "$APP.Play")

                val installed = HashSet<String>()

                /** An activity of [component]'s app, exported or not, with [filter] when given. The app is installed once: installing it again drops its activities. */
                fun activity(
                    component: ComponentName,
                    exported: Boolean = true,
                    filter: IntentFilter? = null,
                ) {
                    val application = ApplicationInfo().apply { packageName = component.packageName }
                    shadowOf(app.packageManager).apply {
                        if (installed.add(component.packageName)) {
                            installPackage(
                                PackageInfo().apply {
                                    packageName = component.packageName
                                    applicationInfo = application
                                },
                            )
                        }
                        addOrUpdateActivity(
                            ActivityInfo().apply {
                                packageName = component.packageName
                                name = component.className
                                this.exported = exported
                                applicationInfo = application
                            },
                        )
                        filter?.let { addIntentFilterForActivity(component, it) }
                    }
                }

                init {
                    activity(launcher, filter = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) })
                    activity(
                        player,
                        filter =
                            IntentFilter(Intent.ACTION_VIEW).apply {
                                addCategory(Intent.CATEGORY_DEFAULT)
                                addDataScheme("plezy")
                            },
                    )
                }

                fun open(
                    intentUri: String?,
                    pkg: String = APP,
                ): Intent? = app.shelfIntent(ShelfItem(pkg, "Severance", ShelfKind.Continue, intentUri = intentUri))

                /** The intent was refused: the app's launch intent stands in for it. */
                fun Intent?.isLaunchIntent() {
                    shouldNotBeNull()
                    component shouldBe launcher
                    hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) shouldBe true
                    (flags and Intent.FLAG_ACTIVITY_NEW_TASK) shouldBe Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
        } asContextForEach {
            test("the app's own intent opens it in a new task, bound to its package") {
                val intent = open("plezy://play?content_id=plezy_srv_1").shouldNotBeNull()

                intent.action shouldBe Intent.ACTION_VIEW
                intent.data shouldBe Uri.parse("plezy://play?content_id=plezy_srv_1")
                intent.`package` shouldBe APP
                (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) shouldBe Intent.FLAG_ACTIVITY_NEW_TASK
            }

            test("an intent for an activity of another app is refused") {
                activity(ComponentName(OTHER, "$OTHER.Steal"))

                open("intent:#Intent;component=$OTHER/.Steal;end").isLaunchIntent()
            }

            test("an intent for another package is refused") {
                activity(ComponentName(OTHER, "$OTHER.Steal"))

                open("intent://play#Intent;scheme=plezy;package=$OTHER;end").isLaunchIntent()
            }

            test("a selector is dropped, so the app's own activity opens and not the selector's") {
                activity(ComponentName(OTHER, "$OTHER.Steal"))
                val uri =
                    Intent(Intent.ACTION_VIEW, Uri.parse("plezy://play"))
                        .apply { selector = Intent().setComponent(ComponentName(OTHER, "$OTHER.Steal")) }
                        .toUri(Intent.URI_INTENT_SCHEME)

                val intent = open(uri).shouldNotBeNull()

                intent.selector.shouldBeNull()
                intent.data shouldBe Uri.parse("plezy://play")
                intent.`package` shouldBe APP
            }

            test("an intent that opens no activity of the app is refused") {
                open("other://play").isLaunchIntent()
            }

            test("an activity the app does not export is refused") {
                activity(ComponentName(APP, "$APP.Hidden"), exported = false)

                open("intent:#Intent;component=$APP/.Hidden;end").isLaunchIntent()
            }

            test("an unreadable intent uri is refused") {
                open("intent:#Intent;action=x").isLaunchIntent()
                open("intent:#Intent;i.count=many;end").isLaunchIntent()
            }

            test("an item without an intent opens the app, and an app without a launch entry opens nothing") {
                open(null).isLaunchIntent()
                open("plezy://play", pkg = "com.gone").shouldBeNull()
            }
        }
    })
