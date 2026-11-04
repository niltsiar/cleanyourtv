package dev.niltsiar.tvhome.feature.clock

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class ClockSceneTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val face = ClockFace(hours = "9", minutes = "41", amPm = "PM", date = "WEDNESDAY  4 NOVEMBER")

    @Test
    fun the_scene_shows_the_time_the_am_pm_marker_and_the_date() {
        compose.mainClock.autoAdvance = false
        compose.setContent { ClockScene(face, glow = true, picture = null, random = Random(2)) }

        for (text in listOf("9", ":", "41", "PM", "WEDNESDAY  4 NOVEMBER")) compose.onNodeWithText(text).assertExists()
    }

    @Test
    fun the_24_hour_clock_shows_no_marker_and_no_time_shows_nothing() {
        compose.mainClock.autoAdvance = false
        compose.setContent { ClockScene(face.copy(amPm = ""), glow = false, picture = null, random = Random(2)) }
        compose.onNodeWithText("PM").assertDoesNotExist()
        compose.onNodeWithText("41").assertExists()
    }

    @Test
    fun a_scene_without_a_time_yet_draws_no_text() {
        compose.mainClock.autoAdvance = false
        compose.setContent { ClockScene(null, glow = true, picture = null, random = Random(2)) }
        compose.onAllNodesWithText("9").assertCountEquals(0)
    }
}
