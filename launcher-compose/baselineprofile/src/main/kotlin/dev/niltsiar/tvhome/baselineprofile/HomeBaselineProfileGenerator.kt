package dev.niltsiar.tvhome.baselineprofile

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
}
