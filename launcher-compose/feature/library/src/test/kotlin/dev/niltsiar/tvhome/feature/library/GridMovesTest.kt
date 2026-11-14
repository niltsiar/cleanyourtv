package dev.niltsiar.tvhome.feature.library

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val GridMovesTest by testSuite {
    // 23 cards in 6 columns: three full rows and a last row of five, cards 18 to 22.
    fun move(
        index: Int,
        move: GridMove,
    ) = gridNeighbour(index, move, count = 23, columns = 6)

    test("a move inside the grid goes to the next card in that direction") {
        move(7, GridMove.Right) shouldBe 8
        move(7, GridMove.Left) shouldBe 6
        move(7, GridMove.Down) shouldBe 13
        move(7, GridMove.Up) shouldBe 1
    }

    test("the first row the last row and the sides are edges") {
        move(3, GridMove.Up).shouldBeNull()
        move(6, GridMove.Left).shouldBeNull()
        move(11, GridMove.Right).shouldBeNull()
        move(20, GridMove.Down).shouldBeNull()
    }

    test("the last card ends its short row") {
        move(22, GridMove.Right).shouldBeNull()
        move(22, GridMove.Left) shouldBe 21
    }

    test("down into the short last row keeps the column it has and otherwise goes to the last card") {
        move(15, GridMove.Down) shouldBe 21
        move(17, GridMove.Down) shouldBe 22
    }
}
