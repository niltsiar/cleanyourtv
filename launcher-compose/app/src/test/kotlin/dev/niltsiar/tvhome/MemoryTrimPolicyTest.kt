package dev.niltsiar.tvhome

import android.content.ComponentCallbacks2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTrimPolicyTest {
    @Test
    fun an_ordinary_background_transition_keeps_the_warm_resources() {
        assertFalse(shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, homeStarted = false))
        assertFalse(shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, homeStarted = true))
    }

    @Test
    fun background_pressure_releases_the_resources_only_when_home_is_stopped() {
        assertTrue(shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, homeStarted = false))
        assertTrue(shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, homeStarted = false))
        assertFalse(shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, homeStarted = true))
        assertFalse(shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, homeStarted = true))
    }

    @Test
    fun low_memory_signals_release_the_resources_when_home_is_stopped() {
        assertTrue(shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, homeStarted = false))
        assertTrue(shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, homeStarted = false))
    }
}
