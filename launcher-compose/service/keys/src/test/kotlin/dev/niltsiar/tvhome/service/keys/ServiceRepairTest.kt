package dev.niltsiar.tvhome.service.keys

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private const val KEYS = "dev.niltsiar.tvhome/.service.keys.KeyService"
private const val OTHER = "com.other.app/.Service"
private val keyService = ComponentName("dev.niltsiar.tvhome", "dev.niltsiar.tvhome.service.keys.KeyService")

val ServiceRepairTest by testSuite {
    robolectricTestSuite<ServiceRepairTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

class ServiceRepairTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()
                val resolver = app.contentResolver

                fun enabled(): String? = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

                fun setEnabled(list: String) {
                    Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list)
                }
            }
        } asContextForEach {
            test("a recorded service missing from the list is added after the others") {
                val plan = repairPlan(OTHER, listOf(keyService), 1)

                plan shouldBe RepairPlan(without = OTHER, with = "$OTHER:$KEYS")
            }

            test("an empty or absent list after a force stop rebuilds to the recorded service alone") {
                repairPlan("", listOf(keyService), 1) shouldBe RepairPlan(without = "", with = KEYS)
                repairPlan(null, listOf(keyService), 1) shouldBe RepairPlan(without = "", with = KEYS)
            }

            test("nothing is rebuilt when no service is recorded or every recorded one is listed and the switch is not 0") {
                repairPlan("", emptyList(), 0).shouldBeNull()
                for (switch in listOf(1, -1)) {
                    repairPlan("$OTHER:$KEYS", listOf(keyService), switch).shouldBeNull()
                    repairPlan("dev.niltsiar.tvhome/dev.niltsiar.tvhome.service.keys.KeyService", listOf(keyService), switch).shouldBeNull()
                }
            }

            test("a listed service is cycled when the switch reads 0 and the cycle turns accessibility on") {
                setEnabled("$KEYS:$OTHER")
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
                val plan = checkNotNull(repairPlan("$KEYS:$OTHER", listOf(keyService), 0))
                val seen = mutableListOf<String?>()

                runRepair(resolver, "$KEYS:$OTHER", plan) { seen += enabled() }

                plan shouldBe RepairPlan(without = OTHER, with = "$OTHER:$KEYS")
                seen shouldBe listOf("$OTHER:$KEYS", OTHER)
                enabled() shouldBe "$OTHER:$KEYS"
                Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED) shouldBe 1
            }

            test("the cycle writes with without with a second apart and turns accessibility on") {
                val plan = checkNotNull(repairPlan(OTHER, listOf(keyService), 1))
                setEnabled(OTHER)
                val seen = mutableListOf<String?>()
                val pauses = mutableListOf<Long>()

                runRepair(resolver, OTHER, plan) {
                    pauses += it
                    seen += enabled()
                }

                pauses shouldBe listOf(1_000L, 1_000L)
                seen shouldBe listOf("$OTHER:$KEYS", OTHER)
                enabled() shouldBe "$OTHER:$KEYS"
                Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED) shouldBe 1
            }

            test("a list changed during a pause stops the cycle and is left as it is") {
                val plan = checkNotNull(repairPlan(OTHER, listOf(keyService), 1))
                setEnabled(OTHER)
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
                var pauses = 0

                runRepair(resolver, OTHER, plan) {
                    pauses++
                    setEnabled("com.third.app/.Service")
                }

                pauses shouldBe 1
                enabled() shouldBe "com.third.app/.Service"
                Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED) shouldBe 0
            }

            test("a list changed before the first write is not overwritten") {
                val plan = checkNotNull(repairPlan(OTHER, listOf(keyService), 1))
                setEnabled("com.third.app/.Service")

                runRepair(resolver, OTHER, plan) { error("no pause expected") }

                enabled() shouldBe "com.third.app/.Service"
            }

            test("restore leaves the list alone without the secure settings permission") {
                ServiceRepair.connected(app, KeyService::class.java)
                shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
                setEnabled(OTHER)

                ServiceRepair.restore(app)

                enabled() shouldBe OTHER
            }
        }
    })
