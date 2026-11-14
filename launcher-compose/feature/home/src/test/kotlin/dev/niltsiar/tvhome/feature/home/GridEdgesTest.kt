package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
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

val GridEdgesTest by testSuite {
    fun fade(
        lastBottom: Int,
        lastHeight: Int = TILE_HEIGHT,
    ) = bottomFade(layout(lastBottom, lastHeight), shift = 0f, height = SCREEN_HEIGHT, minVisible = MIN_VISIBLE)

    test("a last row that ends inside the screen is not faded") {
        val mask = fade(THIRD_ROW_BOTTOM)

        withClue("fade starts at ${mask.start}, under the row that ends at $THIRD_ROW_BOTTOM") {
            mask.start shouldBeGreaterThanOrEqualTo
                THIRD_ROW_BOTTOM.toFloat()
        }
    }

    test("a row that ends past the edge keeps its peek fade") {
        val hidden = fade(lastBottom = 1110)
        hidden.start shouldBe (940f plusOrMinus 0.01f)
        hidden.end shouldBe (1012f plusOrMinus 0.01f)

        val peeking = fade(lastBottom = 1100, lastHeight = 220)
        peeking.start shouldBe (988f plusOrMinus 0.01f)
        peeking.end shouldBe (1060f plusOrMinus 0.01f)
    }

    test("the fade start moves without a jump as a rows bottom passes the edge") {
        val step = 2
        var previous = fade(THIRD_ROW_BOTTOM + 60).start
        var moved = 0f
        for (bottom in THIRD_ROW_BOTTOM + 60 - step downTo THIRD_ROW_BOTTOM - 60 step step) {
            val start = fade(bottom).start
            withClue("start $previous to $start in a $step px scroll to a row bottom of $bottom") {
                abs(start - previous) shouldBeLessThanOrEqualTo
                    step * 10f
            }
            moved += abs(start - previous)
            previous = start
        }
        withClue("the fade start moved $moved px, so the sweep crossed the edge") { moved shouldBeGreaterThan 100f }
    }
}
