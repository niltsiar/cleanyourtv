package dev.niltsiar.tvhome.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("tvhome.android.library")
            configureCompose()
        }
}
