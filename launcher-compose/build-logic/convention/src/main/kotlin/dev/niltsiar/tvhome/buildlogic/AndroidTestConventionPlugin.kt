package dev.niltsiar.tvhome.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Instrumentation-only module that drives the app from outside, such as Macrobenchmark. */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.test")
            pluginManager.apply("tvhome.quality")
            configureKotlin(explicitApi = false)
            configureAndroid()
        }
}
