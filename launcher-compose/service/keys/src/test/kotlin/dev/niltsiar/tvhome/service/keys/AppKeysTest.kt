package dev.niltsiar.tvhome.service.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppKeysTest {
    // Key names resolved by a stand-in: android.view.KeyEvent is not backed by code in JVM tests.
    private fun keyCode(name: String): Int =
        when (name) {
            "KEYCODE_BUTTON_3" -> 190
            "KEYCODE_BUTTON_8" -> 195
            else -> 0
        }

    private fun parse(vararg entries: String) = parseAppKeys(entries.asList(), ::keyCode)

    private fun keysOf(vararg entries: String) = AppKeys(entries.asList(), ::keyCode)

    @Test
    fun key_names_work_like_numbers() {
        val keys = parse("KEYCODE_BUTTON_3:com.google.android.youtube.tv", "KEYCODE_NOT_A_KEY:a.b")
        assertEquals(mapOf(190 to "com.google.android.youtube.tv"), keys)
    }

    @Test
    fun a_keycode_and_a_package_with_spaces_around_them() {
        val keys = parse("190:com.google.android.youtube.tv", " 193 : com.netflix.ninja ")
        assertEquals(mapOf(190 to "com.google.android.youtube.tv", 193 to "com.netflix.ninja"), keys)
    }

    @Test
    fun bad_entries_are_skipped() {
        assertTrue(parse("com.google.android.youtube.tv", "youtube:com.google.android.youtube.tv", "190:", "").isEmpty())
        assertTrue(parseAppKeys(listOf(null)).isEmpty())
    }

    @Test
    fun the_last_entry_for_a_keycode_wins() {
        assertEquals("c.d", parse("190:a.b", "190:c.d")[190])
    }

    @Test
    fun scancode_entries_are_read_apart_from_keycode_entries() {
        val entries = listOf("scan:490:com.wbd.hbomax", " scan : 491 : a.b ", "scan:x:a.b", "scan:12:", "190:c.d", "scan:491:e.f")
        assertEquals(mapOf(490 to "com.wbd.hbomax", 491 to "e.f"), parseAppScancodes(entries))
        assertEquals(mapOf(190 to "c.d"), parseAppKeys(entries, ::keyCode))
    }

    @Test
    fun hold_entries_get_their_own_maps() {
        val keys =
            keysOf(
                "hold:KEYCODE_BUTTON_8:org.xbmc.kodi",
                " hold:190:a.b ",
                "hold:KEYCODE_NOT_A_KEY:x.y",
                "hold:195:",
                "190:c.d",
                "hold:scan:490:d.e",
                "hold:scan:491:",
                "hold:",
            )
        assertEquals(mapOf(195 to "org.xbmc.kodi", 190 to "a.b"), keys.holdKeycodes)
        assertEquals(mapOf(490 to "d.e"), keys.holdScancodes)
        assertEquals(mapOf(190 to "c.d"), keys.keycodes)
        assertTrue(keys.scancodes.isEmpty())
    }

    @Test
    fun front_entries_apply_while_the_buttons_own_app_is_in_front() {
        val keys =
            keysOf(
                "172:com.vt.tv.tvcenter",
                "front:172:guide:com.vt.tv.tvcenter",
                "scan:490:com.wbd.hbomax",
                "front:scan:490:input:a/.B/HW1",
                "hold:172:a.b",
            )
        assertTrue(keys.watchesFront)
        assertEquals("guide:com.vt.tv.tvcenter", keys.keyTap(172, "com.vt.tv.tvcenter"))
        assertEquals("com.vt.tv.tvcenter", keys.keyTap(172, "com.gathin.tvhome"))
        assertEquals("com.vt.tv.tvcenter", keys.keyTap(172, ""))
        assertEquals("input:a/.B/HW1", keys.scanTap(490, "com.wbd.hbomax"))
        assertEquals("com.wbd.hbomax", keys.scanTap(490, "other"))
        assertEquals(mapOf(172 to "a.b"), keys.holdKeycodes)
        assertFalse(keysOf("172:a.b").watchesFront)
    }

    @Test
    fun in_front_matches_the_targets_package_only() {
        assertTrue(inFront("com.vt.tv.tvcenter", "com.vt.tv.tvcenter"))
        assertTrue(inFront("io.github.maxhayim.brightness/.QuickActivity", "io.github.maxhayim.brightness"))
        assertFalse(inFront("com.vt.tv.tvcenter", "com.gathin.tvhome"))
        assertFalse(inFront("com.vt.tv.tvcenter", ""))
        assertFalse(inFront(null, "com.vt.tv.tvcenter"))
        assertFalse(inFront("input:com.vt.source.external/.HdmiInputService/HW2", "com.vt.source.external"))
    }

    @Test
    fun hold_time_picks_the_hold_target() {
        assertEquals("hold.app", tapOrHold("app", "hold.app", HOLD_MS))
        assertEquals("hold.app", tapOrHold("app", "hold.app", 5000))
        assertEquals("app", tapOrHold("app", "hold.app", HOLD_MS - 1))
        assertEquals("app", tapOrHold("app", null, 5000))
        assertNull(tapOrHold(null, "hold.app", 0))
    }
}
