package dev.niltsiar.tvhome.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/** An application module without Compose, whose merged manifest then has no Compose startup provider or receiver. */
class AndroidApplicationPlainConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("tvhome.quality")
            configureKotlin(explicitApi = false)
            configureAndroid()
        }
}
