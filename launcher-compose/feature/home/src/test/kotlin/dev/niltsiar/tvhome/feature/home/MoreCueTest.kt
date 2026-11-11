package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
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

class MoreCueTest {
    @Test
    fun the_cue_is_centred_in_the_band_under_the_last_row_that_draws_and_a_row_too_low_to_draw_does_not_move_it() {
        // Rows 0 and 1 end at 990; row 2 starts at 1038 with 22 px above the end of the bottom fade, under the 96 it needs.
        val centre = cue(gridRows(0..2, firstTop = 488))

        assertEquals((990f + SCREEN_HEIGHT) / 2f, centre!!.centre, 0.01f)
    }

    @Test
    fun the_cue_fades_in_with_the_row_it_marks_as_the_fold_moves_past_that_row() {
        // The last row that draws is row 1, which the grid fades in as the fold goes from 1 to 2.
        val rows = gridRows(0..2)

        assertEquals(0f, cue(rows, fold = 1f)!!.alpha, 0.001f)
        assertEquals(0.4f, cue(rows, fold = 1.4f)!!.alpha, 0.001f)
        assertEquals(1f, cue(rows, fold = 2f)!!.alpha, 0.001f)
        assertEquals(1f, cue(rows, fold = 3f)!!.alpha, 0.001f)
    }

    @Test
    fun there_is_no_cue_when_the_last_row_of_apps_is_on_screen() {
        assertNull(cue(gridRows(0..1), rows = 2))
        assertNull(cue(gridRows(2..3), rows = 4))
    }

    @Test
    fun there_is_no_cue_while_no_row_draws_as_in_dock_focus() {
        val dock = item(1, "dock", 700, 300)
        // The group lowered under the dock: row 0 starts at 1000, 60 px above the end of the fade.
        assertNull(cue(listOf(dock) + gridRows(0..0, firstTop = 1000)))
    }

    @Test
    fun the_cue_is_centred_in_a_band_of_exactly_its_pill_and_4_dp_and_a_shorter_band_puts_it_on_the_screen_edge() {
        val exact = cue(gridRows(0..1, firstTop = 1032 - TILE_HEIGHT - ROW_PITCH))
        assertEquals(SCREEN_HEIGHT - ROOM / 2f, exact!!.centre, 0.01f)

        val short = cue(gridRows(0..1, firstTop = 1033 - TILE_HEIGHT - ROW_PITCH))
        assertEquals(SCREEN_HEIGHT - ROOM / 2f, short!!.centre, 0.01f)
    }

    @Test
    fun a_row_that_peeks_past_the_bottom_edge_with_apps_below_it_gets_the_cue_on_the_screen_edge_over_it() {
        val peeking = cue(gridRows(0..1, firstTop = 600))

        assertEquals(SCREEN_HEIGHT - ROOM / 2f, peeking!!.centre, 0.01f)
    }

    @Test
    fun with_names_a_row_whose_names_fall_off_the_screen_gets_the_cue_over_its_tiles_above_the_names() {
        // The second row's tiles end at 1067, its names below the screen, a third row below.
        val tileTop = 1067 - TILE_HEIGHT
        val rows = gridRows(0..2, firstTop = tileTop - ROW_PITCH, height = TILE_HEIGHT + NAME_ROOM)

        val place = cue(rows, names = true)

        assertNotNull("no cue with names on and apps below", place)
        val tileBottom = tileTop + TILE_WIDTH / TILE_ASPECT
        assertEquals(tileBottom - ROOM / 2f, place!!.centre, 0.01f)
    }

    @Test
    fun with_names_a_row_cut_by_the_bottom_edge_gets_the_cue_on_the_screen_edge_over_its_tiles() {
        val rows = gridRows(0..2, firstTop = 1100 - TILE_HEIGHT - ROW_PITCH, height = TILE_HEIGHT + NAME_ROOM)

        assertEquals(SCREEN_HEIGHT - ROOM / 2f, cue(rows, names = true)!!.centre, 0.01f)
    }
}
