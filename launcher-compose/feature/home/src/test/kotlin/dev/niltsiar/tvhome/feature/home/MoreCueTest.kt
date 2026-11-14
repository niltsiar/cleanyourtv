package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.lang.reflect.Proxy

private const val SCREEN_HEIGHT = 1080f

/** The least a row shows to draw (48 dp) and the room the cue needs (its 20 dp pill and 4 dp), at the TV's 2x density. */
private const val MIN_VISIBLE = 96f
private const val ROOM = 48f

/** The owner's 1920x1080 Home with four columns: tiles 404 x 227, rows 275 apart, the first grid row's top at 488. */
private const val TILE_WIDTH = 404
private const val TILE_HEIGHT = 227
private const val ROW_PITCH = 275
private const val FIRST_ROW_TOP = 488
private const val COLUMNS = 4

/** What a row adds under its tiles for the names with always_show_names. */
private const val NAME_ROOM = 48

private inline fun <reified T : Any> proxy(crossinline answer: (String) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> answer(method.name) } as T

private fun item(
    index: Int,
    key: Any,
    top: Int,
    height: Int,
): LazyGridItemInfo =
    proxy { getter ->
        when {
            getter == "getIndex" -> index
            getter == "getKey" -> key
            getter.startsWith("getOffset") -> IntOffset(0, top).packedValue
            getter.startsWith("getSize") -> IntSize(TILE_WIDTH, height).packedValue
            else -> error(getter)
        }
    }

private fun layout(items: List<LazyGridItemInfo>): LazyGridLayoutInfo =
    proxy { getter ->
        when (getter) {
            "getVisibleItemsInfo" -> items
            "getViewportStartOffset" -> 0
            else -> error(getter)
        }
    }

/** The grid's rows [rows], counting from 0, one tile each, the first at [firstTop] and the rest [ROW_PITCH] apart; item 0 is the shelf, 1 the dock. */
private fun gridRows(
    rows: IntRange,
    firstTop: Int = FIRST_ROW_TOP,
    height: Int = TILE_HEIGHT,
): List<LazyGridItemInfo> =
    rows.map { row -> item(GRID_ITEMS_BEFORE + row * COLUMNS, "tile.$row", firstTop + (row - rows.first) * ROW_PITCH, height) }

private fun cue(
    items: List<LazyGridItemInfo>,
    rows: Int = 4,
    names: Boolean = false,
    fold: Float = Float.MAX_VALUE,
): MoreCuePlace? = moreCuePlace(layout(items), 0f, fold, SCREEN_HEIGHT, COLUMNS, rows, MIN_VISIBLE, names, ROOM)

val MoreCueTest by testSuite {
    test("the cue is centred in the band under the last row that draws and a row too low to draw does not move it") {
        // Rows 0 and 1 end at 990; row 2 starts at 1038 with 22 px above the end of the bottom fade, under the 96 it needs.
        val centre = cue(gridRows(0..2, firstTop = 488))

        centre!!.centre shouldBe ((990f + SCREEN_HEIGHT) / 2f plusOrMinus 0.01f)
    }

    test("the cue fades in with the row it marks as the fold moves past that row") {
        // The last row that draws is row 1, which the grid fades in as the fold goes from 1 to 2.
        val rows = gridRows(0..2)

        cue(rows, fold = 1f)!!.alpha shouldBe (0f plusOrMinus 0.001f)
        cue(rows, fold = 1.4f)!!.alpha shouldBe (0.4f plusOrMinus 0.001f)
        cue(rows, fold = 2f)!!.alpha shouldBe (1f plusOrMinus 0.001f)
        cue(rows, fold = 3f)!!.alpha shouldBe (1f plusOrMinus 0.001f)
    }

    test("there is no cue when the last row of apps is on screen") {
        cue(gridRows(0..1), rows = 2).shouldBeNull()
        cue(gridRows(2..3), rows = 4).shouldBeNull()
    }

    test("there is no cue while no row draws as in dock focus") {
        val dock = item(1, "dock", 700, 300)
        // The group lowered under the dock: row 0 starts at 1000, 60 px above the end of the fade.
        cue(listOf(dock) + gridRows(0..0, firstTop = 1000)).shouldBeNull()
    }

    test("the cue is centred in a band of exactly its pill and 4 dp and a shorter band puts it on the screen edge") {
        val exact = cue(gridRows(0..1, firstTop = 1032 - TILE_HEIGHT - ROW_PITCH))
        exact!!.centre shouldBe (SCREEN_HEIGHT - ROOM / 2f plusOrMinus 0.01f)

        val short = cue(gridRows(0..1, firstTop = 1033 - TILE_HEIGHT - ROW_PITCH))
        short!!.centre shouldBe (SCREEN_HEIGHT - ROOM / 2f plusOrMinus 0.01f)
    }

    test("with names a band of exactly the cues room under the names takes the cue and not the tiles") {
        val rowHeight = TILE_HEIGHT + NAME_ROOM
        val rows = gridRows(0..2, firstTop = 1032 - rowHeight - ROW_PITCH, height = rowHeight)

        cue(rows, names = true)!!.centre shouldBe (SCREEN_HEIGHT - ROOM / 2f plusOrMinus 0.01f)
    }

    test("a row that peeks past the bottom edge with apps below it gets the cue on the screen edge over it") {
        val peeking = cue(gridRows(0..1, firstTop = 600))

        peeking!!.centre shouldBe (SCREEN_HEIGHT - ROOM / 2f plusOrMinus 0.01f)
    }

    test("with names a row whose names fall off the screen gets the cue over its tiles above the names") {
        // The second row's tiles end at 1067, its names below the screen, a third row below.
        val tileTop = 1067 - TILE_HEIGHT
        val rows = gridRows(0..2, firstTop = tileTop - ROW_PITCH, height = TILE_HEIGHT + NAME_ROOM)

        val place = cue(rows, names = true)

        withClue("no cue with names on and apps below") { place.shouldNotBeNull() }
        val tileBottom = tileTop + TILE_WIDTH / TILE_ASPECT
        place!!.centre shouldBe (tileBottom - ROOM / 2f plusOrMinus 0.01f)
    }

    test("with names a row cut by the bottom edge gets the cue on the screen edge over its tiles") {
        val rows = gridRows(0..2, firstTop = 1100 - TILE_HEIGHT - ROW_PITCH, height = TILE_HEIGHT + NAME_ROOM)

        cue(rows, names = true)!!.centre shouldBe (SCREEN_HEIGHT - ROOM / 2f plusOrMinus 0.01f)
    }
}
