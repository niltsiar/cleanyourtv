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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class TrimmedEllipsisTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val name = "Control de TV con altavoces inteligentes"
    private var room by mutableStateOf(60.dp)

    private fun shown(room: Dp): String {
        this.room = room
        compose.waitForIdle()
        return compose
            .onNodeWithTag("name")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }
    }

    @Test
    fun a_name_cut_for_a_narrow_room_shows_whole_again_when_the_room_grows() {
        compose.setContent {
            Box(Modifier.width(room)) { TrimmedEllipsisText(name, TextStyle(fontSize = 14.sp), maxLines = 1, Modifier.testTag("name")) }
        }

        val narrow = shown(120.dp)
        assertTrue(narrow, narrow.length < name.length && narrow.endsWith(Char(0x2026).toString()))
        assertEquals(name, shown(600.dp))
    }
}
