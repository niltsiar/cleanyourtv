package dev.niltsiar.tvhome.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/** The app module is a Compose host, so it gets the Compose setup too. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("tvhome.quality")
            configureKotlin(explicitApi = false)
            configureAndroid()
            configureCompose()
        }
}
