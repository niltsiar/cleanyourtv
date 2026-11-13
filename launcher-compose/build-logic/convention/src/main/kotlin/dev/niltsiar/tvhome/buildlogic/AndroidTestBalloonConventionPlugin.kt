package dev.niltsiar.tvhome.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * TestBalloon with Kotest assertions for a module's local unit tests. JUnit 4 classes in the same module keep running
 * beside the suites, so a module can move one class at a time.
 */
class AndroidTestBalloonConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("de.infix.testBalloon")
            dependencies {
                add("testImplementation", libs.library("testBalloon-framework-core"))
                add("testImplementation", libs.library("testBalloon-integration-robolectric"))
                add("testImplementation", libs.library("testBalloon-integration-kotestAssertions"))
                add("testImplementation", libs.library("kotest-assertions-core"))
            }
        }
}
