package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.math.abs

private const val SCREEN_HEIGHT = 1080f

/** The least a row shows to draw, 48 dp at the TV's 2x density. */
private const val MIN_VISIBLE = 96f

/** The owner's 1920x1080 Home with six columns: tiles 138 px high, rows 186 px apart, the third grid row ending at 1063. */
private const val TILE_HEIGHT = 138
private const val ROW_PITCH = 186
private const val THIRD_ROW_BOTTOM = 1063

/** The sealed grid interfaces cannot be implemented outside the library, so the layout is a proxy that answers the getters the fade reads. */
private inline fun <reified T : Any> proxy(crossinline answer: (String) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> answer(method.name) } as T

private fun tile(
    index: Int,
    top: Int,
    height: Int,
): LazyGridItemInfo =
    proxy { getter ->
        when {
            getter == "getIndex" -> index
            getter == "getKey" -> "tile.$index"
            getter.startsWith("getOffset") -> IntOffset(0, top).packedValue
            getter.startsWith("getSize") -> IntSize(246, height).packedValue
            else -> error(getter)
        }
    }

private fun rows(items: List<LazyGridItemInfo>): LazyGridLayoutInfo =
    proxy { getter ->
        when (getter) {
            "getVisibleItemsInfo" -> items
            else -> error(getter)
        }
    }

/** The dock row and three grid rows, scrolled so the last one ends at [lastBottom]. */
private fun layout(
    lastBottom: Int,
    lastHeight: Int = TILE_HEIGHT,
): LazyGridLayoutInfo {
    val lastTop = lastBottom - lastHeight
    return rows(
        (0..3).map { row ->
            val top = lastTop - (3 - row) * ROW_PITCH
            tile(row, top, if (row == 3) lastHeight else TILE_HEIGHT)
        },
    )
}

class GridEdgesTest {
    private fun fade(
        lastBottom: Int,
        lastHeight: Int = TILE_HEIGHT,
    ) = bottomFade(layout(lastBottom, lastHeight), shift = 0f, height = SCREEN_HEIGHT, minVisible = MIN_VISIBLE)

    @Test
    fun a_last_row_that_ends_inside_the_screen_is_not_faded() {
        val mask = fade(THIRD_ROW_BOTTOM)

        assertTrue("fade starts at ${mask.start}, under the row that ends at $THIRD_ROW_BOTTOM", mask.start >= THIRD_ROW_BOTTOM)
    }

    @Test
    fun a_row_that_ends_past_the_edge_keeps_its_peek_fade() {
        val hidden = fade(lastBottom = 1110)
        assertEquals(940f, hidden.start, 0.01f)
        assertEquals(1012f, hidden.end, 0.01f)

        val peeking = fade(lastBottom = 1100, lastHeight = 220)
        assertEquals(988f, peeking.start, 0.01f)
        assertEquals(1060f, peeking.end, 0.01f)
    }

    @Test
    fun the_fade_start_moves_without_a_jump_as_a_rows_bottom_passes_the_edge() {
        val step = 2
        var previous = fade(THIRD_ROW_BOTTOM + 60).start
        var moved = 0f
        for (bottom in THIRD_ROW_BOTTOM + 60 - step downTo THIRD_ROW_BOTTOM - 60 step step) {
            val start = fade(bottom).start
            assertTrue("start $previous to $start in a $step px scroll to a row bottom of $bottom", abs(start - previous) <= step * 10f)
            moved += abs(start - previous)
            previous = start
        }
        assertTrue("the fade start moved $moved px, so the sweep crossed the edge", moved > 100f)
    }
}
