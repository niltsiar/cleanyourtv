package dev.niltsiar.tvhome.feature.library

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val CardEntranceTest by testSuite {
    test("cards wait for the ones before them during the opening") {
        val entrance = CardEntrance()

        entrance.delayFor("a", 0) shouldBe 0
        entrance.delayFor("b", 1) shouldBe STAGGER_STEP_MS
        entrance.delayFor("d", 3) shouldBe (3 * STAGGER_STEP_MS)
    }

    test("a card far down the row waits no longer than the eighth") {
        val entrance = CardEntrance()

        entrance.delayFor("far", 40) shouldBe (8 * STAGGER_STEP_MS)
    }

    test("a card enters once whatever happens to the row afterwards") {
        val entrance = CardEntrance()
        entrance.delayFor("a", 0)

        // The cards loaded again, or the card scrolled out of view and was built again.
        entrance.delayFor("a", 0).shouldBeNull()
        entrance.delayFor("a", 5).shouldBeNull()
    }

    test("a card first built after the opening enters at once") {
        val entrance = CardEntrance()
        entrance.staggering = false

        entrance.delayFor("late", 12) shouldBe 0
        entrance.delayFor("late", 12).shouldBeNull()
    }
}
