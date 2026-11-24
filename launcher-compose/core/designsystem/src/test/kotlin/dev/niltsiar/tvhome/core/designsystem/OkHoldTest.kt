package dev.niltsiar.tvhome.core.designsystem

import android.view.KeyEvent
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

val OkHoldTest by testSuite {
    testFixture { OkHold(holdMs = 550) { 0L } } asParameterForEach {
        test("the first down opens nothing") { hold ->
            hold.down(repeatCount = 0, downTime = 1_000, eventTime = 1_000).shouldBeFalse()
        }

        test("a repeat opens the menu from 550 ms after the down and only once") { hold ->
            hold.down(0, downTime = 1_000, eventTime = 1_000)

            hold.down(1, downTime = 1_000, eventTime = 1_549).shouldBeFalse()
            hold.down(2, downTime = 1_000, eventTime = 1_550).shouldBeTrue()
            hold.down(3, downTime = 1_000, eventTime = 1_600).shouldBeFalse()
            hold.up().shouldBeTrue()
            hold.up().shouldBeFalse()
        }

        test("a short press is not eaten") { hold ->
            hold.down(0, 1_000, 1_000)

            hold.up().shouldBeFalse()
        }

        test("the timer opens the menu once for a remote that never repeats") { hold ->
            hold.down(0, 1_000, 1_000)

            hold.timeUp().shouldBeTrue()
            hold.timeUp().shouldBeFalse()
            hold.up().shouldBeTrue()
        }

        test("the timer of a press that ended opens nothing") { hold ->
            hold.down(0, 1_000, 1_000)
            hold.up()

            hold.timeUp().shouldBeFalse()
        }

        test("a new press starts over after a hold") { hold ->
            hold.down(0, 1_000, 1_000)
            hold.timeUp()
            hold.up()

            hold.down(0, 5_000, 5_000).shouldBeFalse()
            hold.up().shouldBeFalse()
        }

        test("a press cancelled midway opens nothing and its up is eaten") { hold ->
            hold.down(0, 1_000, 1_000)

            hold.cancel()

            hold.timeUp().shouldBeFalse()
            hold.up().shouldBeTrue()
        }
    }

    testFixture { HeldTiles() } asContextForEach {
        test("the progress runs from the down to whole at the hold time for the tile that took the press only") {
            press(tile)

            hold.progress(tile) shouldBe 0f
            clock += 275
            hold.progress(tile) shouldBe 0.5f
            hold.progress(other) shouldBe 0f
            clock += 500
            hold.progress(tile) shouldBe 1f
        }

        test("the up of the press ends the progress") {
            press(tile)
            clock += 275

            hold.up()

            hold.progress(tile) shouldBe 0f
        }

        test("a cancel ends the progress") {
            press(tile)
            clock += 275

            hold.cancel()

            hold.progress(tile) shouldBe 0f
        }

        test("the menu opening on the timer ends the progress") {
            press(tile)
            clock += 550

            hold.timeUp().shouldBeTrue()

            hold.progress(tile) shouldBe 0f
        }

        test("the menu opening on a repeat ends the progress") {
            press(tile)
            clock += 550

            hold.down(1, downTime = 1_000, eventTime = 1_550, owner = tile).shouldBeTrue()

            hold.progress(tile) shouldBe 0f
        }

        test("a tile that leaves the screen ends only its own press") {
            press(tile)
            clock += 275

            hold.detach(other)
            hold.progress(tile) shouldBe 0.5f
            hold.detach(tile)
            hold.progress(tile) shouldBe 0f
        }

        test("a new press on another tile takes the progress from the start") {
            press(tile)
            clock += 275
            hold.up()

            press(other)
            clock += 110

            hold.progress(other) shouldBe 0.2f
            hold.progress(tile) shouldBe 0f
        }
    }

    test("menu opens on a normal release and on xiaomis injected one") {
        val cancelled = KeyEvent.FLAG_CANCELED
        menuReleased(isUp = true, flags = 0).shouldBeTrue()
        menuReleased(isUp = true, flags = KeyEvent.FLAG_FROM_SYSTEM).shouldBeTrue()
        menuReleased(isUp = true, flags = XIAOMI_SHORT_MENU_FLAGS).shouldBeTrue()
        menuReleased(isUp = false, flags = 0).shouldBeFalse()
        menuReleased(isUp = false, flags = XIAOMI_SHORT_MENU_FLAGS).shouldBeFalse()
        menuReleased(isUp = true, flags = cancelled).shouldBeFalse()
        menuReleased(isUp = true, flags = cancelled or KeyEvent.FLAG_CANCELED_LONG_PRESS or KeyEvent.FLAG_FROM_SYSTEM).shouldBeFalse()
        menuReleased(isUp = true, flags = XIAOMI_SHORT_MENU_FLAGS and 0x4000.inv()).shouldBeFalse()
    }
}

/** One [OkHold] on a clock the test moves, and two tiles that can take a press. */
private class HeldTiles {
    var clock = 1_000L
    val hold = OkHold(holdMs = 550) { clock }
    val tile = Any()
    val other = Any()

    fun press(on: Any) = hold.down(0, downTime = clock, eventTime = clock, owner = on)
}
