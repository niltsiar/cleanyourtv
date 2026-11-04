package dev.niltsiar.tvhome.feature.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLoadsTest {
    @Test
    fun a_refresh_during_the_opening_still_reveals_once() {
        val loads = LibraryLoads()
        val opening = loads.start(reveal = true)
        val refresh = loads.start(reveal = false)

        assertFalse(loads.isLatest(opening))
        assertTrue(loads.isLatest(refresh))
        assertTrue(loads.takeReveal())
        assertFalse(loads.takeReveal())
    }

    @Test
    fun a_refresh_on_its_own_never_reveals() {
        val loads = LibraryLoads()

        assertTrue(loads.isLatest(loads.start(reveal = false)))
        assertFalse(loads.takeReveal())
    }

    @Test
    fun an_opening_after_a_refresh_reveals_with_its_own_load() {
        val loads = LibraryLoads()
        val refresh = loads.start(reveal = false)
        val opening = loads.start(reveal = true)

        assertFalse(loads.isLatest(refresh))
        assertTrue(loads.isLatest(opening))
        assertTrue(loads.takeReveal())
    }

    @Test
    fun refreshes_after_the_reveal_do_not_reveal_again() {
        val loads = LibraryLoads()
        loads.start(reveal = true)
        assertTrue(loads.takeReveal())

        loads.start(reveal = false)

        assertFalse(loads.takeReveal())
    }
}
