package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.mutableStateOf
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfKind
import io.kotest.matchers.shouldBe

/** When the app shelf's cards show: on the shelf's dock tile or a card, with nothing else open over Home. */
val ShelfOnScreenTest by testSuite {
    val dock = (1..6).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }
    val onTile =
        HomeUiState(
            dock = dock,
            focusedKey = "pkg.1",
            appShelf = AppShelf("pkg.1", listOf(ShelfItem(packageName = "pkg.1", title = "Severance", kind = ShelfKind.Continue))),
        )

    testFixture {
        object {
            val input =
                HomeInput(
                    mutableStateOf(HomeMenuActions()),
                    carried = mutableStateOf(null),
                    dimmed = mutableStateOf(emptySet()),
                    reordering = mutableStateOf(false),
                ).apply { gridFocused = true }
            val cards = ShelfCardsState()

            /** Whether the cards show for [state], from the dock tile and from a card with focus. */
            fun shows(
                state: HomeUiState,
                libraryOpen: Boolean = false,
            ): Pair<Boolean, Boolean> {
                cards.focused = -1
                val fromTile = shelfOnScreen(state, input, cards, libraryOpen)
                cards.focused = 0
                val fromCard = shelfOnScreen(state, input, cards, libraryOpen)
                return fromTile to fromCard
            }
        }
    } asContextForEach {
        test("the cards show while focus is on the shelf's dock tile or on a card") {
            shows(onTile) shouldBe (true to true)
        }

        test("a reorder or edit-dock session hides them") {
            shows(onTile.copy(reorder = ReorderView(carried = null, dimmed = emptySet(), hint = ReorderHint.Reorder))) shouldBe
                (false to false)
        }

        test("an open tile or settings menu hides them") {
            shows(onTile.copy(menu = HomeMenu("pkg.1", listOf(MenuRow.Move)))) shouldBe (false to false)
            shows(onTile.copy(menu = HomeMenu(null, listOf(MenuRow.Move)))) shouldBe (false to false)
        }

        test("the open inputs list hides them") {
            shows(onTile.copy(inputsOpen = true)) shouldBe (false to false)
        }

        test("the open app library hides them") {
            shows(onTile, libraryOpen = true) shouldBe (false to false)
        }

        test("focus on the library pill hides them, though the grid has focus") {
            input.pillFocused = true

            shows(onTile).first shouldBe false
        }
    }
}
