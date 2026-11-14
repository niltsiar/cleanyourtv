package dev.niltsiar.tvhome.data.inputs

import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val SourcePanelTest by testSuite {
    robolectricTestSuite<SourcePanelTestContent>("default sdk")
}

class SourcePanelTestContent :
    RobolectricTestSuiteContent({
        test("the panel is the component with its action and one int extra") {
            val intent =
                sourcePanelIntent(
                    TvConfig(
                        sourcePanelService = "com.coocaa.tv/.ControlCenterService",
                        sourcePanelAction = "com.coocaa.SHOW",
                        sourcePanelExtra = "control_center_command = 100",
                    ),
                )!!

            intent.component?.packageName shouldBe "com.coocaa.tv"
            intent.action shouldBe "com.coocaa.SHOW"
            intent.getIntExtra("control_center_command", -1) shouldBe 100
        }

        test("an extra that is not a number is left out and no service means no panel") {
            val intent = sourcePanelIntent(TvConfig(sourcePanelService = "a.b/.C", sourcePanelExtra = "key=abc"))!!

            intent.hasExtra("key").shouldBeFalse()
            sourcePanelIntent(TvConfig()).shouldBeNull()
        }
    })
