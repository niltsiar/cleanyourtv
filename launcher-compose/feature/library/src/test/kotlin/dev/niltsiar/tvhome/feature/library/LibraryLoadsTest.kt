package dev.niltsiar.tvhome.feature.library

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

val LibraryLoadsTest by testSuite {
    test("a refresh during the opening still reveals once") {
        val loads = LibraryLoads()
        val opening = loads.start(reveal = true)
        val refresh = loads.start(reveal = false)

        loads.isLatest(opening).shouldBeFalse()
        loads.isLatest(refresh).shouldBeTrue()
        loads.takeReveal().shouldBeTrue()
        loads.takeReveal().shouldBeFalse()
    }

    test("a refresh on its own never reveals") {
        val loads = LibraryLoads()

        loads.isLatest(loads.start(reveal = false)).shouldBeTrue()
        loads.takeReveal().shouldBeFalse()
    }

    test("an opening after a refresh reveals with its own load") {
        val loads = LibraryLoads()
        val refresh = loads.start(reveal = false)
        val opening = loads.start(reveal = true)

        loads.isLatest(refresh).shouldBeFalse()
        loads.isLatest(opening).shouldBeTrue()
        loads.takeReveal().shouldBeTrue()
    }

    test("refreshes after the reveal do not reveal again") {
        val loads = LibraryLoads()
        loads.start(reveal = true)
        loads.takeReveal().shouldBeTrue()

        loads.start(reveal = false)

        loads.takeReveal().shouldBeFalse()
    }
}
