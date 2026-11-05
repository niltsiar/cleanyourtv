package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** A new shelf title fades in over the old one, which stays on screen until it has faded out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class ShelfTitleCrossfadeTest {
    @get:Rule
    val compose = createComposeRule()

    private var focusedKey by mutableStateOf<String?>(null)

    private fun showHome(cinematic: Boolean) {
        compose.setContent {
            TvTheme(glassKind = GlassKind.Flat) {
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

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** Presses [key] and returns the texts under [tag] three frames later and once the title has settled. */
    private fun titlesWhileMoving(
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

    private fun texts(tag: String): Set<String> =
        compose
            .onAllNodesWithTag(tag)
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .map { it.text }
            .toSet()

    @Test
    fun the_cinematic_shelf_keeps_the_old_name_on_screen_while_the_new_one_fades_in() {
        showHome(cinematic = true)

        val (moving, settled) = titlesWhileMoving(Key.DirectionRight, "shelf:name")

        assertEquals(setOf("App 1", "App 2"), moving)
        assertEquals(setOf("App 2"), settled)
    }

    @Test
    fun the_plain_shelf_title_keeps_the_old_name_on_screen_while_the_new_one_fades_in() {
        showHome(cinematic = false)
        press(Key.DirectionDown)

        val (moving, settled) = titlesWhileMoving(Key.DirectionRight, "shelf:title")

        assertEquals(setOf("App 7", "App 8"), moving)
        assertEquals(setOf("App 8"), settled)
    }

    @Test
    fun a_new_cinematic_name_rises_into_place_without_moving_its_node() {
        showHome(cinematic = true)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }

        val rising = topOf("shelf:name", "App 2")
        compose.mainClock.advanceTimeBy(500)

        assertEquals(topOf("shelf:name", "App 2"), rising, 0.01f)
    }

    private fun topOf(
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
