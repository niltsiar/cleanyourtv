package dev.niltsiar.tvhome.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReorderStateTest {
    private fun keys(vararg n: Int) = n.map { "k$it" }

    private fun session(
        count: Int = 10,
        columns: Int = 4,
        singleRow: Boolean = false,
        fillDock: Boolean = true,
        docked: Int = columns,
    ) = ReorderState.start((1..count).map { "k$it" }, docked, columns, singleRow, fillDock)!!

    private fun ReorderState.carrying(n: Int) = pickUp("k$n")

    private fun ReorderState.order() = keys.map { it.removePrefix("k").toInt() }

    @Test
    fun left_and_right_move_one_place_along_the_list_across_rows() {
        val s = session().carrying(4)

        assertEquals(listOf(1, 2, 3, 5, 4, 6, 7, 8, 9, 10), s.move(Arrow.Right).order())
        assertEquals(listOf(1, 2, 4, 3, 5, 6, 7, 8, 9, 10), s.move(Arrow.Left).order())
    }

    @Test
    fun up_and_down_move_a_row() {
        val s = session().carrying(6)

        assertEquals(listOf(1, 6, 2, 3, 4, 5, 7, 8, 9, 10), s.move(Arrow.Up).order())
        assertEquals(listOf(1, 2, 3, 4, 5, 7, 8, 9, 10, 6), s.move(Arrow.Down).order())
    }

    @Test
    fun down_into_a_short_last_row_lands_on_its_last_tile() {
        val s = session().carrying(4)

        assertEquals(
            listOf(1, 2, 3, 5, 6, 7, 8, 9, 10, 4),
            s
                .move(Arrow.Down)
                .move(Arrow.Down)
                .move(Arrow.Down)
                .order(),
        )
    }

    @Test
    fun nothing_moves_past_either_end() {
        assertEquals(session().order(), session().carrying(1).move(Arrow.Left).order())
        assertEquals(session().order(), session().carrying(1).move(Arrow.Up).order())
        assertEquals(session().order(), session().carrying(10).move(Arrow.Right).order())
        assertEquals(session().order(), session().carrying(10).move(Arrow.Down).order())
    }

    @Test
    fun a_single_row_ignores_up_and_down() {
        val s = session(singleRow = true).carrying(5)

        assertEquals(s.order(), s.move(Arrow.Up).order())
        assertEquals(s.order(), s.move(Arrow.Down).order())
        assertEquals(listOf(1, 2, 3, 4, 6, 5, 7, 8, 9, 10), s.move(Arrow.Right).order())
    }

    @Test
    fun arrows_move_nothing_while_no_tile_is_carried() {
        val s = session()

        assertEquals(s, s.move(Arrow.Right))
        assertEquals(s, s.carrying(2).drop().move(Arrow.Right))
    }

    @Test
    fun a_session_with_fewer_than_two_tiles_does_not_start() {
        assertNull(ReorderState.start(keys(1), 1, 4, singleRow = false, fillDock = true))
    }

    @Test
    fun nothing_moved_saves_nothing_even_after_a_pick_up_and_drop() {
        assertNull(session().carrying(3).drop().finish())
        assertNull(
            session()
                .carrying(3)
                .move(Arrow.Right)
                .move(Arrow.Left)
                .finish(),
        )
    }

    @Test
    fun a_moved_tile_saves_the_new_order_without_a_dock_size_when_fill_dock_is_on() {
        val change = session().carrying(2).move(Arrow.Right).finish()

        assertEquals(OrderChange(keys(1, 3, 2, 4, 5, 6, 7, 8, 9, 10), -1), change)
    }

    @Test
    fun with_fill_dock_off_a_grid_tile_carried_into_the_dock_joins_it() {
        val s = session(fillDock = false, docked = 3).carrying(6).move(Arrow.Up)

        assertEquals(OrderChange(keys(1, 6, 2, 3, 4, 5, 7, 8, 9, 10), 4), s.finish())
    }

    @Test
    fun with_fill_dock_off_a_grid_tile_that_only_slid_into_the_free_slot_goes_back_to_the_grid() {
        // Three dock apps and a free slot: k4 sits in the fourth place without being put there.
        val s = session(fillDock = false, docked = 3).carrying(1).move(Arrow.Right).move(Arrow.Right)

        assertEquals(listOf(2, 3, 1, 4, 5, 6, 7, 8, 9, 10), s.order())
        assertEquals(OrderChange(keys(2, 3, 1, 4, 5, 6, 7, 8, 9, 10), 3), s.finish())
    }

    @Test
    fun with_fill_dock_off_a_dock_app_pushed_past_the_dock_leaves_it_and_the_size_is_saved() {
        val s = session(fillDock = false, docked = 4).carrying(4).move(Arrow.Right)

        assertEquals(OrderChange(keys(1, 2, 3, 5, 4, 6, 7, 8, 9, 10), 3), s.finish())
    }

    @Test
    fun with_fill_dock_off_a_changed_dock_size_alone_is_worth_saving() {
        // k4 sits in the free fourth place without being put there; carrying it out and back puts it there.
        val s = session(fillDock = false, docked = 3).carrying(4).move(Arrow.Right).move(Arrow.Left)

        assertEquals(OrderChange(keys(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 4), s.finish())
    }

    @Test
    fun swapping_a_grid_app_into_a_dock_place_sends_the_old_dock_app_to_the_grid_spot() {
        val swapped = session().swapIntoDock(slot = 1, pos = 6)

        assertEquals(listOf(1, 7, 3, 4, 5, 6, 2, 8, 9, 10), swapped.order())
        assertEquals(OrderChange(swapped.keys, -1), swapped.finish())
    }

    @Test
    fun swapping_a_tile_into_its_own_place_changes_nothing() {
        assertNull(session().swapIntoDock(slot = 2, pos = 2).finish())
    }

    @Test
    fun with_fill_dock_off_the_swapped_in_app_is_in_the_dock_and_the_swapped_out_one_is_not() {
        val swapped = session(fillDock = false, docked = 4).swapIntoDock(slot = 1, pos = 7)

        assertEquals(OrderChange(keys(1, 8, 3, 4, 5, 6, 7, 2, 9, 10), 4), swapped.finish())
        assertEquals(setOf("k1", "k8", "k3", "k4"), swapped.inDock)
    }

    @Test
    fun only_the_carried_tile_is_undimmed() {
        val s = session(count = 3).carrying(2)

        assertEquals(setOf("k1", "k3"), s.dimmed())
        assertEquals(emptySet<String>(), s.drop().dimmed())
    }

    @Test
    fun choosing_a_dock_place_dims_the_other_dock_tiles_only() {
        val s = session().copy(dockEdit = DockEdit(slot = 1))

        assertEquals(setOf("k1", "k3", "k4"), s.dimmed())
        assertEquals(ReorderHint.DockChooseApp, s.hint())
    }

    @Test
    fun only_the_app_choice_step_names_its_dock_place_from_1_and_the_app_in_it_now() {
        val label = { key: String -> "Name of $key" }

        assertEquals(DockSlot(3, "Name of k3"), session().copy(dockEdit = DockEdit(slot = 2)).view(label).slot)
        assertNull(session().copy(dockEdit = DockEdit()).view(label).slot)
        assertNull(session().copy(dockEdit = DockEdit(appKey = "k7")).view(label).slot)
        assertNull(session().view(label).slot)
    }

    @Test
    fun the_hint_follows_the_step() {
        assertEquals(ReorderHint.Reorder, session().hint())
        assertEquals(ReorderHint.DockChoosePlace, session().copy(dockEdit = DockEdit()).hint())
        assertEquals(ReorderHint.DockPlaceForApp, session().copy(dockEdit = DockEdit(appKey = "k7")).hint())
    }

    @Test
    fun a_dock_with_free_slots_starts_with_only_the_filled_ones_in_it() {
        val s = session(fillDock = false, docked = 2)

        assertEquals(setOf("k1", "k2"), s.inDock)
        assertEquals(2, s.dockedAtStart)
    }

    // The cases of the reference launcher's DockSlotsTest.

    private fun set(vararg n: String) = n.toSet()

    @Test
    fun an_app_carried_into_the_dock_stays_there() {
        val keys = listOf("tivimate", "jellyfin", "iplayer", "appletv", "my5", "youtube")
        val dock = set("tivimate", "jellyfin", "iplayer", "appletv")

        assertEquals(keys, dockFirst(keys, 4, dock))
        assertEquals(4, dockCount(keys, 4, dock))
    }

    @Test
    fun a_grid_app_that_only_slid_into_a_free_slot_goes_back_to_the_grid() {
        val keys = listOf("tivimate", "jellyfin", "iplayer", "my5", "vlc", "appletv")
        val dock = set("tivimate", "jellyfin", "iplayer")

        assertEquals(keys, dockFirst(keys, 4, dock))
        assertEquals(3, dockCount(keys, 4, dock))
    }

    @Test
    fun a_slid_in_grid_app_ahead_of_a_dock_app_moves_behind_the_dock() {
        val keys = listOf("jellyfin", "iplayer", "my5", "tivimate", "vlc")
        val dock = set("tivimate", "jellyfin", "iplayer")

        assertEquals(listOf("jellyfin", "iplayer", "tivimate", "my5", "vlc"), dockFirst(keys, 4, dock))
        assertEquals(3, dockCount(keys, 4, dock))
    }

    @Test
    fun a_dock_app_pushed_past_the_dock_leaves_it() {
        val keys = listOf("appletv", "tivimate", "jellyfin", "iplayer", "youtube")
        val dock = set("appletv", "tivimate", "jellyfin", "iplayer", "youtube")

        assertEquals(keys, dockFirst(keys, 4, dock))
        assertEquals(4, dockCount(keys, 4, dock))
    }

    @Test
    fun fewer_tiles_than_dock_slots_still_put_the_dock_first() {
        assertEquals(listOf("b", "a"), dockFirst(listOf("a", "b"), 4, set("b")))
        assertEquals(1, dockCount(listOf("a", "b"), 4, set("b")))
    }

    @Test
    fun an_app_chosen_for_the_free_place_it_already_sits_in_joins_the_dock() {
        val keys = listOf("tivimate", "jellyfin", "iplayer", "my5", "vlc")

        assertEquals(4, dockCount(keys, 4, set("tivimate", "jellyfin", "iplayer", "my5")))
        assertEquals(0, emptyDockSlots(docked = 4, dockSize = 4, tiles = 5, fill = false))
    }
}
