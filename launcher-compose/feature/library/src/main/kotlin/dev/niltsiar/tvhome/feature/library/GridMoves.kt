package dev.niltsiar.tvhome.feature.library

/** A D-pad press in the grid, as the direction the focus goes in the layout's order. */
internal enum class GridMove { Up, Down, Left, Right }

/**
 * The card [move] takes focus to from the card at [index] in a grid of [columns] columns and [count] cards, null at an
 * edge, where the focus stays. Down from a column the short last row lacks goes to the last card.
 */
internal fun gridNeighbour(
    index: Int,
    move: GridMove,
    count: Int,
    columns: Int,
): Int? =
    when (move) {
        GridMove.Left -> {
            (index - 1).takeIf { index % columns != 0 }
        }

        GridMove.Right -> {
            (index + 1).takeIf { it % columns != 0 && it < count }
        }

        GridMove.Up -> {
            (index - columns).takeIf { it >= 0 }
        }

        GridMove.Down -> {
            when {
                index + columns < count -> index + columns
                index / columns < (count - 1) / columns -> count - 1
                else -> null
            }
        }
    }
