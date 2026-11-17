package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService
import android.content.res.Resources
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.time.Duration

val RestartPressTest by testSuite {
    robolectricTestSuite<RestartPressTestContent>(
        "sdk 31, Spanish",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "es-rES"
            },
    )
}

private const val SYSTEM_UI = "com.android.systemui"

/** A node of [pkg] with [children] under it. */
private fun node(
    pkg: String,
    text: String? = null,
    clickable: Boolean = false,
    vararg children: AccessibilityNodeInfo,
): AccessibilityNodeInfo =
    AccessibilityNodeInfo().apply {
        packageName = pkg
        this.text = text
        isClickable = clickable
        children.forEach { shadowOf(this).addChild(it) }
        // getChild hands out copies; a copy shares the record of performed actions only once the record exists.
        shadowOf(this).performedActions
    }

class RestartPressTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val service: KeyService = Robolectric.setupService(KeyService::class.java)

                /** A clickable item of the power dialog: an icon and the [label] under it, as on the Sony XR-75X95J. */
                fun item(
                    label: String,
                    pkg: String = SYSTEM_UI,
                ) = node(pkg, clickable = true, children = arrayOf(node(pkg), node(pkg, text = label)))

                /** The AOSP phone power dialog: a clickable frame that holds focus, and [items] in a list under it. */
                fun dialog(
                    vararg items: AccessibilityNodeInfo,
                    pkg: String = SYSTEM_UI,
                ) = node(pkg, children = arrayOf(node(pkg, clickable = true, children = arrayOf(node(pkg, children = items)))))

                fun clicks(node: AccessibilityNodeInfo) =
                    shadowOf(node).performedActions.filter { it == AccessibilityNodeInfo.ACTION_CLICK }

                fun waitMs(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

                /** Presses Restart with [window] in front, past the time the press looks for its item. */
                fun pressWith(window: AccessibilityNodeInfo) {
                    PowerMenu.bind(service)
                    try {
                        PowerMenu.restart()
                        shadowOf(service).setRootInActiveWindow(window)
                        waitMs(3_500)
                    } finally {
                        PowerMenu.unbind(service)
                    }
                }

                fun saysNotPossible() = ShadowToast.getTextOfLatestToast() shouldBe service.getString(R.string.restart_not_possible)
            }
        } asContextForEach {
            test("Restart opens the power dialog and clicks only the item labelled with the framework restart label") {
                val id = Resources.getSystem().getIdentifier("global_action_restart", "string", "android")
                Resources.getSystem().getString(id) shouldBe "Reiniciar"
                val emergency = item("Emergencia")
                val powerOff = item("Apagar")
                val restart = item("Reiniciar")

                PowerMenu.bind(service)
                try {
                    PowerMenu.restart()
                    shadowOf(service).globalActionsPerformed shouldBe listOf(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                    shadowOf(service).setRootInActiveWindow(dialog(emergency, powerOff, restart))
                    waitMs(500)

                    clicks(restart) shouldBe listOf(AccessibilityNodeInfo.ACTION_CLICK)
                    clicks(emergency).shouldBeEmpty()
                    clicks(powerOff).shouldBeEmpty()
                    shadowOf(service).globalActionsPerformed shouldBe listOf(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                } finally {
                    PowerMenu.unbind(service)
                }
            }

            test("a dialog with no item labelled restart gets no click, Back closes it and Home says restarting is not possible") {
                val emergency = item("Emergencia")
                val powerOff = item("Apagar")

                pressWith(dialog(emergency, powerOff))

                clicks(emergency).shouldBeEmpty()
                clicks(powerOff).shouldBeEmpty()
                shadowOf(service).globalActionsPerformed shouldBe
                    listOf(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, AccessibilityService.GLOBAL_ACTION_BACK)
                saysNotPossible()
            }

            test("two items labelled restart get no click") {
                val first = item("Reiniciar")
                val second = item("Reiniciar")
                val powerOff = item("Apagar")

                pressWith(dialog(first, powerOff, second))

                clicks(first).shouldBeEmpty()
                clicks(second).shouldBeEmpty()
                clicks(powerOff).shouldBeEmpty()
                shadowOf(service).globalActionsPerformed.last() shouldBe AccessibilityService.GLOBAL_ACTION_BACK
                saysNotPossible()
            }

            test("a restart item in a power menu that is not the system UI's gets no click") {
                val restart = item("Reiniciar", pkg = "com.sony.dtv.powermenu")

                pressWith(dialog(item("Apagar", pkg = "com.sony.dtv.powermenu"), restart, pkg = "com.sony.dtv.powermenu"))

                clicks(restart).shouldBeEmpty()
                shadowOf(service).globalActionsPerformed.last() shouldBe AccessibilityService.GLOBAL_ACTION_BACK
                saysNotPossible()
            }

            test("no Back reaches Home when the power dialog never opened") {
                pressWith(node(service.packageName))

                shadowOf(service).globalActionsPerformed shouldBe listOf(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                saysNotPossible()
            }
        }
    })
