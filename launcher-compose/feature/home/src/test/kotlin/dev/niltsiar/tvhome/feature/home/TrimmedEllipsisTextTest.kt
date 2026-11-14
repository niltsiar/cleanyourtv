package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

val TrimmedEllipsisTextTest by testSuite {
    robolectricTestSuite<TrimmedEllipsisTextTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class TrimmedEllipsisTextTestContent :
    RobolectricTestSuiteContent({
        testFixture { TrimmedEllipsisTextTestFixture() } asContextForEach {
            test("a name cut for a narrow room shows whole again when the room grows") {
                compose.setContent {
                    Box(
                        Modifier.width(room),
                    ) { TrimmedEllipsisText(name, TextStyle(fontSize = 14.sp), maxLines = 1, Modifier.testTag("name")) }
                }

                val narrow = shown(120.dp)
                withClue(narrow) { (narrow.length < name.length && narrow.endsWith(Char(0x2026).toString())).shouldBeTrue() }
                shown(600.dp) shouldBe name
            }
        }
    })

private class TrimmedEllipsisTextTestFixture : JUnit4RulesContext() {
    val compose = rule(createComposeRule())

    val name = "Control de TV con altavoces inteligentes"

    var room by mutableStateOf(60.dp)

    fun shown(room: Dp): String {
        this.room = room
        compose.waitForIdle()
        return compose
            .onNodeWithTag("name")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }
    }
}
