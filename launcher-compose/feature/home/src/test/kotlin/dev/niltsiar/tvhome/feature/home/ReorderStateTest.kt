package dev.niltsiar.tvhome.feature.home

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val ReorderStateTest by testSuite {
    fun keys(vararg n: Int) = n.map { "k$it" }

    fun session(
        count: Int = 10,
        columns: Int = 4,
        singleRow: Boolean = false,
        fillDock: Boolean = true,
        docked: Int = columns,
    ) = ReorderState.start((1..count).map { "k$it" }, docked, columns, singleRow, fillDock)!!

    fun ReorderState.carrying(n: Int) = pickUp("k$n")

    fun ReorderState.order() = keys.map { it.removePrefix("k").toInt() }

    // The cases of the reference launcher's DockSlotsTest.

    fun set(vararg n: String) = n.toSet()

    test("left and right move one place along the list across rows") {
        val s = session().carrying(4)

        s.move(Arrow.Right).order() shouldBe listOf(1, 2, 3, 5, 4, 6, 7, 8, 9, 10)
        s.move(Arrow.Left).order() shouldBe listOf(1, 2, 4, 3, 5, 6, 7, 8, 9, 10)
    }

    test("up and down move a row") {
        val s = session().carrying(6)

        s.move(Arrow.Up).order() shouldBe listOf(1, 6, 2, 3, 4, 5, 7, 8, 9, 10)
        s.move(Arrow.Down).order() shouldBe listOf(1, 2, 3, 4, 5, 7, 8, 9, 10, 6)
    }

    test("down into a short last row lands on its last tile") {
        val s = session().carrying(4)

        s
            .move(Arrow.Down)
            .move(Arrow.Down)
            .move(Arrow.Down)
            .order() shouldBe listOf(1, 2, 3, 5, 6, 7, 8, 9, 10, 4)
    }

    test("nothing moves past either end") {
        session().carrying(1).move(Arrow.Left).order() shouldBe session().order()
        session().carrying(1).move(Arrow.Up).order() shouldBe session().order()
        session().carrying(10).move(Arrow.Right).order() shouldBe session().order()
        session().carrying(10).move(Arrow.Down).order() shouldBe session().order()
    }

    test("a single row ignores up and down") {
        val s = session(singleRow = true).carrying(5)

        s.move(Arrow.Up).order() shouldBe s.order()
        s.move(Arrow.Down).order() shouldBe s.order()
        s.move(Arrow.Right).order() shouldBe listOf(1, 2, 3, 4, 6, 5, 7, 8, 9, 10)
    }

    test("arrows move nothing while no tile is carried") {
        val s = session()

        s.move(Arrow.Right) shouldBe s
        s.carrying(2).drop().move(Arrow.Right) shouldBe s
    }

    test("a session with fewer than two tiles does not start") {
        ReorderState.start(keys(1), 1, 4, singleRow = false, fillDock = true).shouldBeNull()
    }

    test("nothing moved saves nothing even after a pick up and drop") {
        session()
            .carrying(3)
            .drop()
            .finish()
            .shouldBeNull()
        session()
            .carrying(3)
            .move(Arrow.Right)
            .move(Arrow.Left)
            .finish()
            .shouldBeNull()
    }

    test("a moved tile saves the new order without a dock size when fill dock is on") {
        val change = session().carrying(2).move(Arrow.Right).finish()

        change shouldBe OrderChange(keys(1, 3, 2, 4, 5, 6, 7, 8, 9, 10), -1)
    }

    test("with fill dock off a grid tile carried into the dock joins it") {
        val s = session(fillDock = false, docked = 3).carrying(6).move(Arrow.Up)

        s.finish() shouldBe OrderChange(keys(1, 6, 2, 3, 4, 5, 7, 8, 9, 10), 4)
    }

    test("with fill dock off a grid tile that only slid into the free slot goes back to the grid") {
        // Three dock apps and a free slot: k4 sits in the fourth place without being put there.
        val s = session(fillDock = false, docked = 3).carrying(1).move(Arrow.Right).move(Arrow.Right)

        s.order() shouldBe listOf(2, 3, 1, 4, 5, 6, 7, 8, 9, 10)
        s.finish() shouldBe OrderChange(keys(2, 3, 1, 4, 5, 6, 7, 8, 9, 10), 3)
    }

    test("with fill dock off a dock app pushed past the dock leaves it and the size is saved") {
        val s = session(fillDock = false, docked = 4).carrying(4).move(Arrow.Right)

        s.finish() shouldBe OrderChange(keys(1, 2, 3, 5, 4, 6, 7, 8, 9, 10), 3)
    }

    test("with fill dock off a changed dock size alone is worth saving") {
        // k4 sits in the free fourth place without being put there; carrying it out and back puts it there.
        val s = session(fillDock = false, docked = 3).carrying(4).move(Arrow.Right).move(Arrow.Left)

        s.finish() shouldBe OrderChange(keys(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 4)
    }

    test("swapping a grid app into a dock place sends the old dock app to the grid spot") {
        val swapped = session().swapIntoDock(slot = 1, pos = 6)

        swapped.order() shouldBe listOf(1, 7, 3, 4, 5, 6, 2, 8, 9, 10)
        swapped.finish() shouldBe OrderChange(swapped.keys, -1)
    }

    test("swapping a tile into its own place changes nothing") {
        session().swapIntoDock(slot = 2, pos = 2).finish().shouldBeNull()
    }

    test("with fill dock off the swapped in app is in the dock and the swapped out one is not") {
        val swapped = session(fillDock = false, docked = 4).swapIntoDock(slot = 1, pos = 7)

        swapped.finish() shouldBe OrderChange(keys(1, 8, 3, 4, 5, 6, 7, 2, 9, 10), 4)
        swapped.inDock shouldBe setOf("k1", "k8", "k3", "k4")
    }

    test("only the carried tile is undimmed") {
        val s = session(count = 3).carrying(2)

        s.dimmed() shouldBe setOf("k1", "k3")
        s.drop().dimmed() shouldBe emptySet<String>()
    }

    test("choosing a dock place dims the other dock tiles only") {
        val s = session().copy(dockEdit = DockEdit(slot = 1))

        s.dimmed() shouldBe setOf("k1", "k3", "k4")
        s.hint() shouldBe ReorderHint.DockChooseApp
    }

    test("only the app choice step names its dock place from 1 of the dock places and the app in it now") {
        val label = { key: String -> "Name of $key" }

        session().copy(dockEdit = DockEdit(slot = 2)).view(label).slot shouldBe DockSlot(3, 4, "Name of k3")
        session(count = 3).copy(dockEdit = DockEdit(slot = 1)).view(label).slot shouldBe DockSlot(2, 3, "Name of k2")
        session()
            .copy(dockEdit = DockEdit())
            .view(label)
            .slot
            .shouldBeNull()
        session()
            .copy(dockEdit = DockEdit(appKey = "k7"))
            .view(label)
            .slot
            .shouldBeNull()
        session().view(label).slot.shouldBeNull()
    }

    test("the hint follows the step") {
        session().hint() shouldBe ReorderHint.Reorder
        session().copy(dockEdit = DockEdit()).hint() shouldBe ReorderHint.DockChoosePlace
        session().copy(dockEdit = DockEdit(appKey = "k7")).hint() shouldBe ReorderHint.DockPlaceForApp
    }

    test("a dock with free slots starts with only the filled ones in it") {
        val s = session(fillDock = false, docked = 2)

        s.inDock shouldBe setOf("k1", "k2")
        s.dockedAtStart shouldBe 2
    }

    test("an app carried into the dock stays there") {
        val keys = listOf("tivimate", "jellyfin", "iplayer", "appletv", "my5", "youtube")
        val dock = set("tivimate", "jellyfin", "iplayer", "appletv")

        dockFirst(keys, 4, dock) shouldBe keys
        dockCount(keys, 4, dock) shouldBe 4
    }

    test("a grid app that only slid into a free slot goes back to the grid") {
        val keys = listOf("tivimate", "jellyfin", "iplayer", "my5", "vlc", "appletv")
        val dock = set("tivimate", "jellyfin", "iplayer")

        dockFirst(keys, 4, dock) shouldBe keys
        dockCount(keys, 4, dock) shouldBe 3
    }

    test("a slid in grid app ahead of a dock app moves behind the dock") {
        val keys = listOf("jellyfin", "iplayer", "my5", "tivimate", "vlc")
        val dock = set("tivimate", "jellyfin", "iplayer")

        dockFirst(keys, 4, dock) shouldBe listOf("jellyfin", "iplayer", "tivimate", "my5", "vlc")
        dockCount(keys, 4, dock) shouldBe 3
    }

    test("a dock app pushed past the dock leaves it") {
        val keys = listOf("appletv", "tivimate", "jellyfin", "iplayer", "youtube")
        val dock = set("appletv", "tivimate", "jellyfin", "iplayer", "youtube")

        dockFirst(keys, 4, dock) shouldBe keys
        dockCount(keys, 4, dock) shouldBe 4
    }

    test("fewer tiles than dock slots still put the dock first") {
        dockFirst(listOf("a", "b"), 4, set("b")) shouldBe listOf("b", "a")
        dockCount(listOf("a", "b"), 4, set("b")) shouldBe 1
    }

    test("an app chosen for the free place it already sits in joins the dock") {
        val keys = listOf("tivimate", "jellyfin", "iplayer", "my5", "vlc")

        dockCount(keys, 4, set("tivimate", "jellyfin", "iplayer", "my5")) shouldBe 4
        emptyDockSlots(docked = 4, dockSize = 4, tiles = 5, fill = false) shouldBe 0
    }
}
