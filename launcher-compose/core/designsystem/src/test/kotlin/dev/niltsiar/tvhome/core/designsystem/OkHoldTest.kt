package dev.niltsiar.tvhome.core.designsystem

import android.view.KeyEvent
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

val OkHoldTest by testSuite {
    testFixture { OkHold(holdMs = 550) } asParameterForEach {
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
