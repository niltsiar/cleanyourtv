package dev.niltsiar.tvhome.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("tvhome.quality")
            configureKotlin(explicitApi = true)
        }
}
