package dev.niltsiar.tvhome.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

class HomeBaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = targetPackage, includeInStartupProfile = true) {
            launchHome()
            awaitTileImages()
            dpadSweep()
        }

    /**
     * Home, the App library and the Settings page in the look the app has, then in the other one, and back. The flip back
     * stores the first look as a Settings choice, so when no choice was stored before, Follow theme removes it again.
     */
    @Test
    fun generateBothLooks() =
        rule.collect(packageName = targetPackage, includeInStartupProfile = false) {
            walkHomeAndLibrary()
            val themeOnly = followsTheme()
            openLookRow()
            flipLook()
            walkHomeAndLibrary()
            openLookRow()
            flipLook()
            if (themeOnly) followTheme()
        }

    private fun MacrobenchmarkScope.walkHomeAndLibrary() {
        launchHome()
        awaitTileImages()
        dpadSweep()
        openLibrary()
        libraryHoldDown()
    }
}
