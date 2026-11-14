package dev.niltsiar.tvhome.service.keys

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

val PressesTest by testSuite {
    /** Presses on a timer the test moves, and the targets they open. */
    class Remote {
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

    test("chorded keys keep their own hold") {
        val remote = Remote()
        remote.down("1:190:0", 1000, "a.hold")
        remote.down("1:191:0", 1100, "b.hold")
        // A is released reporting B's down time; it is still A's tap, and only A's hold is cancelled.
        remote.up("1:190:0", 1200, 1100, "a.tap", "a.hold")
        remote.holdTimeout()
        remote.up("1:191:0", 1700, 1100, "b.tap", "b.hold")
        remote.opened shouldBe listOf("a.tap", "b.hold")
    }

    test("holds on two devices stay apart") {
        val remote = Remote()
        remote.down("1:190:0", 1000, "hold")
        remote.holdTimeout()
        remote.down("2:190:0", 1000, "hold")
        remote.holdTimeout()
        remote.up("1:190:0", 1800, 1000, "tap", "hold")
        remote.up("2:190:0", 1900, 1000, "tap", "hold")
        remote.opened shouldBe listOf("hold", "hold")
    }

    test("two owners answer each press once") {
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
        remote.opened shouldBe listOf("tap", "hold")
        remote.timer.pending shouldBe 0
    }

    test("a canceled up opens nothing and drops the hold") {
        val remote = Remote()
        remote.down("1:190:0", 1000, "hold")
        remote.up("1:190:0", 1200, 1000, "tap", "hold", canceled = true)
        remote.holdTimeout()
        remote.opened.shouldBeEmpty()
    }

    test("an owner that leaves before the hold cancels it") {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "hold")
        remote.presses.release("home")
        remote.presses.release("home") // onUnbind, then onDestroy
        remote.holdTimeout()
        remote.opened.shouldBeEmpty()
        remote.timer.pending shouldBe 0
    }

    test("losing one owner leaves the hold with the other") {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "hold")
        remote.down("input", "1:190:0", 1000, "hold")
        remote.presses.release("home")
        remote.holdTimeout()
        remote.up("1:190:0", 1800, 1000, "tap", "hold")
        remote.opened shouldBe listOf("input: hold")
    }

    test("losing one owner keeps the others tap") {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "hold")
        remote.down("input", "1:190:0", 1000, "hold")
        remote.presses.release("input")
        remote.up("1:190:0", 1200, 1000, "tap", "hold")
        remote.holdTimeout()
        remote.opened shouldBe listOf("tap")
    }

    test("an up after the hold was released opens nothing and the next press is answered") {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "hold")
        remote.presses.release("home")
        remote.up("1:190:0", 1200, 1000, "tap", "hold")
        remote.holdTimeout()
        remote.down("home", "1:190:0", 2000, "hold")
        remote.up("1:190:0", 2100, 2000, "tap", "hold")
        remote.opened shouldBe listOf("tap")
    }

    test("releasing an owner leaves another buttons hold alone") {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "a.hold")
        remote.down("input", "1:191:0", 1100, "b.hold")
        remote.presses.release("home")
        remote.holdTimeout()
        remote.opened shouldBe listOf("input: b.hold")
    }

    test("an up without its down uses the events down time") {
        val remote = Remote()
        remote.up("1:190:0", 1100, 1000, "tap", "hold")
        remote.up("1:191:0", 1700, 1000, "tap", "hold")
        remote.opened shouldBe listOf("tap", "hold")
    }

    test("a key with only a hold entry does nothing on a tap") {
        val remote = Remote()
        remote.down("1:190:0", 1000, "hold")
        remote.up("1:190:0", 1100, 1000, null, "hold")
        remote.opened.shouldBeEmpty()
        remote.timer.pending shouldBe 0
    }
}
