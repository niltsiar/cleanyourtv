package dev.niltsiar.tvhome.data.shelf

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.TransactionTooLargeException
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfKind
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowApplicationPackageManager

private const val APP = "com.edde746.plezy"
private const val GRANT_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
private const val OTHER = "com.other"
private const val TOO_BIG = "too_big"

/** Fails to resolve an intent with a [TOO_BIG] extra, as the binder call fails for an intent over its buffer. */
@Implements(className = "android.app.ApplicationPackageManager", isInAndroidSdk = false)
class ShadowBinderLimit : ShadowApplicationPackageManager() {
    @Implementation
    override fun resolveActivity(
        intent: Intent,
        flags: Int,
    ): ResolveInfo? {
        if (intent.hasExtra(TOO_BIG)) throw RuntimeException(TransactionTooLargeException())
        return super.resolveActivity(intent, flags)
    }
}

private object OwnSandbox

/** Which intent OK on a shelf item starts: the app's own, only into that app, or else the app's launch intent. */
val ShelfLaunchTest by testSuite {
    robolectricTestSuite<ShelfLaunchTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                shadows += ShadowBinderLimit::class
                portableClasses += OwnSandbox::class
            },
    )
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

                val started = ArrayList<Intent>()

                /** What startActivity throws for an intent, as the system would; null starts it. */
                var failure: (Intent) -> RuntimeException? = { null }

                val context =
                    object : ContextWrapper(app) {
                        override fun startActivity(intent: Intent) {
                            failure(intent)?.let { throw it }
                            started += Intent(intent)
                        }
                    }

                /** The intent OK on an item with [intentUri] starts, null when none starts. */
                fun open(
                    intentUri: String?,
                    pkg: String = APP,
                ): Intent? {
                    val opened = context.startShelfItem(ShelfItem(pkg, "Severance", ShelfKind.Continue, intentUri = intentUri))
                    return if (opened) started.last() else null
                }

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
                intent.component shouldBe player
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

            test("an intent with file: data is refused") {
                activity(
                    ComponentName(APP, "$APP.Files"),
                    filter =
                        IntentFilter(Intent.ACTION_VIEW).apply {
                            addCategory(Intent.CATEGORY_DEFAULT)
                            addDataScheme("file")
                        },
                )

                open("intent:///sdcard/movie.mkv#Intent;scheme=file;action=android.intent.action.VIEW;end").isLaunchIntent()
            }

            test("an intent that asks for URI grants opens without them, so Home passes none of its own on") {
                val uri =
                    Intent(Intent.ACTION_VIEW, Uri.parse("plezy://play"))
                        .addFlags(GRANT_FLAGS or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .toUri(Intent.URI_INTENT_SCHEME)

                val intent = open(uri).shouldNotBeNull()

                intent.data shouldBe Uri.parse("plezy://play")
                (intent.flags and GRANT_FLAGS) shouldBe 0
                (intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP) shouldBe Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            test("an intent that fails to resolve, such as one too big for the binder, opens the app instead") {
                open("intent://play#Intent;scheme=plezy;S.$TOO_BIG=x;end").isLaunchIntent()
            }

            test("an intent that fails to start, such as one too big for the binder, opens the app instead") {
                failure = { if (it.data != null) RuntimeException(TransactionTooLargeException()) else null }

                open("plezy://play").isLaunchIntent()
            }

            test("when the launch intent fails to start too, nothing opens") {
                failure = { SecurityException("Permission Denial") }

                open("plezy://play").shouldBeNull()
                started.shouldBeEmpty()
            }
        }
    })
