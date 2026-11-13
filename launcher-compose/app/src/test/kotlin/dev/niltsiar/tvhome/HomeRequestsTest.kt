package dev.niltsiar.tvhome

import android.content.Intent
import android.content.pm.PackageManager
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.model.LauncherIntents
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private const val PACKAGE = "dev.niltsiar.tvhome"

val HomeRequestsTest by testSuite {
    robolectricTestSuite<HomeRequestsTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class HomeRequestsTestContent :
    RobolectricTestSuiteContent({
        fun home() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        test("the library and inputs actions ask for them") {
            homeRequestOf(Intent(LauncherIntents.APP_LIBRARY), PACKAGE) shouldBe HomeRequest.OpenLibrary
            homeRequestOf(Intent(LauncherIntents.VIEW_INPUTS), PACKAGE) shouldBe HomeRequest.ShowInputs
        }

        test("the launcher settings action of this package opens the page and no other does") {
            homeRequestOf(Intent(launcherSettingsAction(PACKAGE)), PACKAGE) shouldBe HomeRequest.OpenLauncherSettings
            launcherSettingsAction("dev.example.tv") shouldBe "dev.example.tv.action.LAUNCHER_SETTINGS"
            homeRequestOf(Intent(launcherSettingsAction("dev.example.tv")), PACKAGE) shouldBe HomeRequest.None
            homeRequestOf(Intent(LauncherIntents.CLOCK), PACKAGE) shouldBe HomeRequest.None
            homeRequestOf(Intent(launcherSettingsAction(PACKAGE)).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY), PACKAGE) shouldBe
                HomeRequest.None
        }

        test("an action sent through the home intent still asks for itself") {
            homeRequestOf(Intent(LauncherIntents.APP_LIBRARY).addCategory(Intent.CATEGORY_HOME), PACKAGE) shouldBe HomeRequest.OpenLibrary
        }

        test("an intent the system replays from history asks for nothing") {
            val fromHistory = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

            homeRequestOf(Intent(LauncherIntents.APP_LIBRARY).addFlags(fromHistory), PACKAGE) shouldBe HomeRequest.None
            homeRequestOf(Intent(LauncherIntents.VIEW_INPUTS).addFlags(fromHistory), PACKAGE) shouldBe HomeRequest.None
            homeRequestOf(Intent(LauncherIntents.VIEW_INPUTS).addCategory(Intent.CATEGORY_HOME).addFlags(fromHistory), PACKAGE) shouldBe
                HomeRequest.HomeKey
        }

        test("the home key is the home category alone") {
            homeRequestOf(home(), PACKAGE) shouldBe HomeRequest.HomeKey
            homeRequestOf(Intent(Intent.ACTION_MAIN), PACKAGE) shouldBe HomeRequest.None
            homeRequestOf(null, PACKAGE) shouldBe HomeRequest.None
        }

        test("the view inputs handler is enabled when asked and back to the manifest default when not") {
            val enabled = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            val default = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

            viewInputsHandlerChange(on = true, current = default) shouldBe enabled
            viewInputsHandlerChange(on = false, current = enabled) shouldBe default
        }

        test("the view inputs handler is not written when it already is as asked") {
            viewInputsHandlerChange(on = true, current = PackageManager.COMPONENT_ENABLED_STATE_ENABLED).shouldBeNull()
            viewInputsHandlerChange(on = false, current = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT).shouldBeNull()
        }
    })
