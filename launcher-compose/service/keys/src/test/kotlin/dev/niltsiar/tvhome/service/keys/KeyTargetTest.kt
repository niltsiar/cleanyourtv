package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService
import dev.niltsiar.tvhome.core.model.inputIdOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyTargetTest {
    private val hdmi = "com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413743104"

    @Test
    fun every_action_word_is_an_action_and_nothing_else_is() {
        for (word in listOf("back", "home", "recents", "mute", "sleep", "screensaver", "settings", "wifi", "library", "ignore")) {
            assertEquals(word, KeyTarget.Action(ActionWord.entries.single { it.word == word }), parseTarget(word))
        }
        assertEquals(KeyTarget.App("Mute"), parseTarget("Mute"))
    }

    @Test
    fun targets_are_told_apart_by_their_shape() {
        assertEquals(KeyTarget.Inputs, parseTarget("inputs"))
        assertEquals(KeyTarget.Guide("com.vt.tv.tvcenter"), parseTarget("guide:com.vt.tv.tvcenter"))
        assertEquals(KeyTarget.Input(hdmi), parseTarget("input:$hdmi"))
        assertEquals(
            KeyTarget.Activity("io.github.maxhayim.brightness/.QuickActivity"),
            parseTarget("io.github.maxhayim.brightness/.QuickActivity"),
        )
        assertEquals(KeyTarget.App("com.netflix.ninja"), parseTarget("com.netflix.ninja"))
    }

    @Test
    fun an_empty_guide_or_input_id_is_a_plain_package() {
        assertEquals(KeyTarget.App("guide:"), parseTarget("guide:"))
        assertEquals(KeyTarget.App("input: "), parseTarget("input: "))
        assertNull(inputIdOf("input: "))
        assertNull(inputIdOf("com.netflix.ninja"))
    }

    @Test
    fun an_input_target_is_not_an_activity() {
        // Its id has slashes, but it must reach the passthrough branch, not ComponentName.unflattenFromString().
        assertFalse(isActivity("input:$hdmi"))
        assertTrue(isActivity("io.github.maxhayim.brightness/.QuickActivity"))
        assertFalse(isActivity("com.netflix.ninja"))
        assertFalse(isActivity("/.QuickActivity"))
    }

    @Test
    fun global_actions_and_the_android_9_floor_for_sleep() {
        assertEquals(AccessibilityService.GLOBAL_ACTION_BACK, globalAction(ActionWord.Back, 26))
        assertEquals(AccessibilityService.GLOBAL_ACTION_HOME, globalAction(ActionWord.Home, 26))
        assertEquals(AccessibilityService.GLOBAL_ACTION_RECENTS, globalAction(ActionWord.Recents, 26))
        assertEquals(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, globalAction(ActionWord.Sleep, 28))
        // No lock-screen action before Android 9: the key is consumed and nothing happens.
        assertNull(globalAction(ActionWord.Sleep, 27))
        assertNull(globalAction(ActionWord.Mute, 34))
    }
}
