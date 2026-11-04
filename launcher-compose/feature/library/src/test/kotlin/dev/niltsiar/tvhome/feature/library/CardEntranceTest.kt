package dev.niltsiar.tvhome.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardEntranceTest {
    @Test
    fun cards_wait_for_the_ones_before_them_during_the_opening() {
        val entrance = CardEntrance()

        assertEquals(0, entrance.delayFor("a", 0))
        assertEquals(STAGGER_STEP_MS, entrance.delayFor("b", 1))
        assertEquals(3 * STAGGER_STEP_MS, entrance.delayFor("d", 3))
    }

    @Test
    fun a_card_far_down_the_row_waits_no_longer_than_the_eighth() {
        val entrance = CardEntrance()

        assertEquals(8 * STAGGER_STEP_MS, entrance.delayFor("far", 40))
    }

    @Test
    fun a_card_enters_once_whatever_happens_to_the_row_afterwards() {
        val entrance = CardEntrance()
        entrance.delayFor("a", 0)

        // The cards loaded again, or the card scrolled out of view and was built again.
        assertNull(entrance.delayFor("a", 0))
        assertNull(entrance.delayFor("a", 5))
    }

    @Test
    fun a_card_first_built_after_the_opening_enters_at_once() {
        val entrance = CardEntrance()
        entrance.staggering = false

        assertEquals(0, entrance.delayFor("late", 12))
        assertNull(entrance.delayFor("late", 12))
    }
}
