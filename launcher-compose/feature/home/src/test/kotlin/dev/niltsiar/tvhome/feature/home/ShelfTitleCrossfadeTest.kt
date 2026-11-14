package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import java.util.Locale

/** A new shelf title fades in over the old one, which stays on screen until it has faded out. */
val ShelfTitleCrossfadeTest by testSuite {
    robolectricTestSuite<ShelfTitleCrossfadeTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class ShelfTitleCrossfadeTestContent :
    RobolectricTestSuiteContent({
        testFixture { ShelfTitleCrossfadeTestFixture() } asContextForEach {
            test("the cinematic shelf keeps the old name on screen while the new one fades in") {
                showHome(cinematic = true)

                val (moving, settled) = titlesWhileMoving(Key.DirectionRight, "shelf:name")

                moving shouldBe setOf("App 1", "App 2")
                settled shouldBe setOf("App 2")
            }

            test("the plain shelf title keeps the old name on screen while the new one fades in") {
                showHome(cinematic = false)
                press(Key.DirectionDown)

                val (moving, settled) = titlesWhileMoving(Key.DirectionRight, "shelf:title")

                moving shouldBe setOf("App 7", "App 8")
                settled shouldBe setOf("App 8")
            }

            test("a new cinematic name rises into place without moving its node") {
                showHome(cinematic = true)
                compose.mainClock.autoAdvance = false
                compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
                repeat(3) { compose.mainClock.advanceTimeByFrame() }

                val rising = topOf("shelf:name", "App 2")
                compose.mainClock.advanceTimeBy(500)

                rising shouldBe (topOf("shelf:name", "App 2") plusOrMinus 0.01f)
            }
        }
    })

private class ShelfTitleCrossfadeTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    var focusedKey by mutableStateOf<String?>(null)

    fun showHome(cinematic: Boolean) {
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            TvTheme {
                HomeScreen(
                    state = { homeUiState(homeApps(), Evening, focusedKey, Locale.ENGLISH, cinematic = cinematic) },
                    layout = SixColumns.homeLayout(),
                    onFocus = { focusedKey = it },
                    onClick = {},
                    shelfLayout = ShelfLayout(cinematic = cinematic, scrimAlpha = 0f),
                )
            }
        }
        compose.waitForIdle()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** Presses [key] and returns the texts under [tag] three frames later and once the title has settled. */
    fun titlesWhileMoving(
        key: Key,
        tag: String,
    ): Pair<Set<String>, Set<String>> {
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(key) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        val moving = texts(tag)
        compose.mainClock.advanceTimeBy(500)
        return moving to texts(tag)
    }

    fun texts(tag: String): Set<String> =
        compose
            .onAllNodesWithTag(tag)
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .map { it.text }
            .toSet()

    fun topOf(
        tag: String,
        text: String,
    ): Float =
        compose
            .onAllNodesWithTag(tag)
            .filter(hasText(text))
            .onFirst()
            .getBoundsInRoot()
            .top.value
}
