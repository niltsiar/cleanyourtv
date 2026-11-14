package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.model.AppEntry
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

val HomeLayoutTest by testSuite {
    testFixture {
        object {
            val gridWidth = 880f

            val height = 324f

            val gap = 14f

            /** The height of the dock panel and [rows] rows when the grid uses [share] of its width. */
            fun needed(
                share: Float,
                rows: Int,
                columns: Int = 6,
                dockPadding: Float = gap,
            ): Float {
                val inner = gridWidth * share
                val dockTile = (inner - (columns + 1) * gap) / columns * 9 / 16
                val gridTile = (inner - (columns - 1) * gap) / columns * 9 / 16
                return dockTile + 2 * dockPadding + rows * (gridTile + gap)
            }

            fun tiles(n: Int) = (1..n).map { AppEntry(key = "t$it", packageName = "t$it", label = "T$it") }
        }
    } asContextForEach {
        test("no rows below the dock keep the full width") {
            tileWidthShare(gridWidth, height, columns = 6, rowsBelowDock = 0, gap = gap) shouldBe (1f plusOrMinus 0f)
        }

        test("rows that already fit keep the full width") {
            tileWidthShare(gridWidth, height, columns = 6, rowsBelowDock = 1, gap = gap) shouldBe (1f plusOrMinus 0f)
        }

        test("rows that do not fit shrink the tiles until they do") {
            val share = tileWidthShare(gridWidth, height, columns = 6, rowsBelowDock = 3, gap = gap)
            withClue("share $share") { (share < 1f && share > 0.85f).shouldBeTrue() }
            needed(share, rows = 3) shouldBe (height plusOrMinus 0.5f)
        }

        test("a dock panel padded more than a gap shrinks the tiles further so the rows still fit") {
            val padded = tileWidthShare(gridWidth, height, columns = 6, rowsBelowDock = 3, gap = gap, dockTop = 15f, dockBottom = 15f)

            padded shouldBeLessThan tileWidthShare(gridWidth, height, columns = 6, rowsBelowDock = 3, gap = gap)
            needed(padded, rows = 3, dockPadding = 15f) shouldBe (height plusOrMinus 0.5f)
        }

        test("names do not change how wide the tiles are though the rows under the dock do not fit") {
            val bare = HomeLayout(columns = 4, rowsBelowDock = 3, singleRow = false, fillDock = true)

            fun share(layout: HomeLayout) = gridShare(layout, gridWidth = 880.dp, windowHeight = 540.dp, shelfBottom = 245.dp)

            withClue("share ${share(bare)}") { share(bare) shouldBeLessThan 1f }
            share(bare.copy(showNames = true)) shouldBe (share(bare) plusOrMinus 0f)
        }

        test("the tiles never shrink below the floor") {
            tileWidthShare(gridWidth, 200f, columns = 6, rowsBelowDock = 3, gap = gap) shouldBe (0.85f plusOrMinus 0f)
        }

        test("an unknown window size keeps the full width") {
            tileWidthShare(gridWidth, 0f, columns = 6, rowsBelowDock = 3, gap = gap) shouldBe (1f plusOrMinus 0f)
        }

        test("dock focus lowers the dock by 120 dp for the peek and by what is left under the rows that must fit") {
            fun lowering(
                gridRows: Int,
                rowsBelowDock: Int,
            ) = dockLowering(
                windowHeight = 540f,
                shelfBottom = 245f,
                tileHeight = 70f,
                gap = 24f,
                gridRows = gridRows,
                rowsBelowDock = rowsBelowDock,
            )

            lowering(gridRows = 3, rowsBelowDock = 0) shouldBe (120f plusOrMinus 0f)
            lowering(gridRows = 3, rowsBelowDock = 1) shouldBe (540f - 48f - (245f + 70f + 48f + 94f) plusOrMinus 0.01f)
            lowering(gridRows = 3, rowsBelowDock = 3) shouldBe (0f plusOrMinus 0f)
            lowering(gridRows = 1, rowsBelowDock = 3) shouldBe (lowering(gridRows = 3, rowsBelowDock = 1) plusOrMinus 0.01f)
        }

        test("a dock panel padded more than a gap lowers the dock by twice the extra less") {
            fun lowering(dockPadding: Float) =
                dockLowering(
                    windowHeight = 600f,
                    shelfBottom = 245f,
                    tileHeight = 70f,
                    gap = 24f,
                    dockTop = dockPadding,
                    dockBottom = dockPadding,
                    gridRows = 3,
                    rowsBelowDock = 1,
                )

            lowering(29.8f) shouldBe (lowering(24f) - 2 * 5.8f plusOrMinus 0.01f)
        }

        test("the names under the rows that must fit lower the dock by less") {
            fun lowering(nameRoom: Float) =
                dockLowering(
                    windowHeight = 600f,
                    shelfBottom = 245f,
                    tileHeight = 70f,
                    gap = 24f,
                    gridRows = 3,
                    rowsBelowDock = 1,
                    nameRoom = nameRoom,
                )

            lowering(nameRoom = 36f) shouldBe (lowering(nameRoom = 0f) - 2 * 36f plusOrMinus 0.01f)
        }

        test("down from a tile with no cell below goes to the last tile so the pill does not capture it") {
            // Seven grid tiles in 3 columns: rows 0 1 2 / 3 4 5 / 6.
            gridDownOverride(row = 1, column = 1, gridCount = 7, columns = 3) shouldBe 6
            gridDownOverride(row = 1, column = 2, gridCount = 7, columns = 3) shouldBe 6
            gridDownOverride(row = 1, column = 0, gridCount = 7, columns = 3) shouldBe null
            gridDownOverride(row = 0, column = 1, gridCount = 7, columns = 3) shouldBe null
            gridDownOverride(row = 2, column = 0, gridCount = 7, columns = 3) shouldBe null
        }

        test("down from a dock tile over a short first row goes to the last grid tile") {
            gridDownOverride(row = -1, column = 2, gridCount = 2, columns = 4) shouldBe 1
            gridDownOverride(row = -1, column = 1, gridCount = 2, columns = 4) shouldBe null
            gridDownOverride(row = -1, column = 0, gridCount = 0, columns = 4) shouldBe null
        }

        test("up from the pill returns to the last row under the column that had focus") {
            val dock = tiles(3)
            val grid = (4..12).map { AppEntry(key = "t$it", packageName = "t$it", label = "T$it") }

            pillUpKey(dock, grid, columns = 3, singleRow = false, focusedKey = "t5") shouldBe "t11"
            pillUpKey(dock, grid, columns = 3, singleRow = false, focusedKey = "t9") shouldBe "t12"
            pillUpKey(dock, grid, columns = 3, singleRow = false, focusedKey = "t3") shouldBe "t12"
            pillUpKey(dock, grid, columns = 3, singleRow = false, focusedKey = null) shouldBe "t10"
            pillUpKey(dock, grid.take(7), columns = 3, singleRow = false, focusedKey = "t5") shouldBe "t10"
        }

        test("up from the pill with no grid goes to the dock and in a single row to the tile that had focus") {
            val dock = tiles(4)

            pillUpKey(dock, emptyList(), columns = 4, singleRow = false, focusedKey = "t2") shouldBe "t2"
            pillUpKey(dock, emptyList(), columns = 2, singleRow = true, focusedKey = "t3") shouldBe "t3"
            pillUpKey(dock, emptyList(), columns = 2, singleRow = true, focusedKey = null) shouldBe "t4"
            pillUpKey(emptyList(), emptyList(), columns = 4, singleRow = false, focusedKey = null) shouldBe null
        }
    }
}
