package dev.niltsiar.tvhome.service.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressesTest {
    /** Presses on a timer the test moves, and the targets they open. */
    private class Remote {
        val timer = FakeTimer()
        val opened = mutableListOf<String>()
        val presses = Presses(timer)

        fun down(
            button: String,
            at: Long,
            hold: String?,
        ) = presses.down(button, at, 0, hold, this) { opened += it }

        /** A DOWN as one of two owners sees it; what it opens is noted with its name. */
        fun down(
            owner: String,
            button: String,
            at: Long,
            hold: String?,
        ) = presses.down(button, at, 0, hold, owner) { opened += "$owner: $it" }

        fun up(
            button: String,
            at: Long,
            downTime: Long,
            tap: String?,
            hold: String?,
            canceled: Boolean = false,
        ) = presses.up(button, at, downTime, canceled, tap, hold) { opened += it }

        fun holdTimeout() = timer.advance(HOLD_MS)
    }

    @Test
    fun chorded_keys_keep_their_own_hold() {
        val remote = Remote()
        remote.down("1:190:0", 1000, "a.hold")
        remote.down("1:191:0", 1100, "b.hold")
        // A is released reporting B's down time; it is still A's tap, and only A's hold is cancelled.
        remote.up("1:190:0", 1200, 1100, "a.tap", "a.hold")
        remote.holdTimeout()
        remote.up("1:191:0", 1700, 1100, "b.tap", "b.hold")
        assertEquals(listOf("a.tap", "b.hold"), remote.opened)
    }

    @Test
    fun holds_on_two_devices_stay_apart() {
        val remote = Remote()
        remote.down("1:190:0", 1000, "hold")
        remote.holdTimeout()
        remote.down("2:190:0", 1000, "hold")
        remote.holdTimeout()
        remote.up("1:190:0", 1800, 1000, "tap", "hold")
        remote.up("2:190:0", 1900, 1000, "tap", "hold")
        assertEquals(listOf("hold", "hold"), remote.opened)
    }

    @Test
    fun two_owners_answer_each_press_once() {
        val remote = Remote()
        // A tap, each event delivered to both owners.
        remote.down("1:190:0", 1000, "hold")
        remote.down("1:190:0", 1000, "hold")
        remote.up("1:190:0", 1100, 1000, "tap", "hold")
        remote.up("1:190:0", 1100, 1000, "tap", "hold")
        // A hold, with the second owner's DOWN arriving after the hold fired.
        remote.down("1:190:0", 2000, "hold")
        remote.holdTimeout()
        remote.down("1:190:0", 2000, "hold")
        remote.up("1:190:0", 2800, 2000, "tap", "hold")
        remote.up("1:190:0", 2800, 2000, "tap", "hold")
        assertEquals(listOf("tap", "hold"), remote.opened)
        assertEquals(0, remote.timer.pending)
    }

    @Test
    fun a_canceled_up_opens_nothing_and_drops_the_hold() {
        val remote = Remote()
        remote.down("1:190:0", 1000, "hold")
        remote.up("1:190:0", 1200, 1000, "tap", "hold", canceled = true)
        remote.holdTimeout()
        assertTrue(remote.opened.isEmpty())
    }

    @Test
    fun an_owner_that_leaves_before_the_hold_cancels_it() {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "hold")
        remote.presses.release("home")
        remote.presses.release("home") // onUnbind, then onDestroy
        remote.holdTimeout()
        assertTrue(remote.opened.isEmpty())
        assertEquals(0, remote.timer.pending)
    }

    @Test
    fun losing_one_owner_leaves_the_hold_with_the_other() {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "hold")
        remote.down("input", "1:190:0", 1000, "hold")
        remote.presses.release("home")
        remote.holdTimeout()
        remote.up("1:190:0", 1800, 1000, "tap", "hold")
        assertEquals(listOf("input: hold"), remote.opened)
    }

    @Test
    fun losing_one_owner_keeps_the_others_tap() {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "hold")
        remote.down("input", "1:190:0", 1000, "hold")
        remote.presses.release("input")
        remote.up("1:190:0", 1200, 1000, "tap", "hold")
        remote.holdTimeout()
        assertEquals(listOf("tap"), remote.opened)
    }

    @Test
    fun an_up_after_the_hold_was_released_opens_nothing_and_the_next_press_is_answered() {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "hold")
        remote.presses.release("home")
        remote.up("1:190:0", 1200, 1000, "tap", "hold")
        remote.holdTimeout()
        remote.down("home", "1:190:0", 2000, "hold")
        remote.up("1:190:0", 2100, 2000, "tap", "hold")
        assertEquals(listOf("tap"), remote.opened)
    }

    @Test
    fun releasing_an_owner_leaves_another_buttons_hold_alone() {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "a.hold")
        remote.down("input", "1:191:0", 1100, "b.hold")
        remote.presses.release("home")
        remote.holdTimeout()
        assertEquals(listOf("input: b.hold"), remote.opened)
    }

    @Test
    fun an_up_without_its_down_uses_the_events_down_time() {
        val remote = Remote()
        remote.up("1:190:0", 1100, 1000, "tap", "hold")
        remote.up("1:191:0", 1700, 1000, "tap", "hold")
        assertEquals(listOf("tap", "hold"), remote.opened)
    }

    @Test
    fun a_key_with_only_a_hold_entry_does_nothing_on_a_tap() {
        val remote = Remote()
        remote.down("1:190:0", 1000, "hold")
        remote.up("1:190:0", 1100, 1000, null, "hold")
        assertTrue(remote.opened.isEmpty())
        assertEquals(0, remote.timer.pending)
    }
}
