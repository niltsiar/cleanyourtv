package dev.niltsiar.tvhome.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies

/**
 * TestBalloon with Kotest assertions for a module's local unit tests (docs/adr/0004-unit-tests-on-testballoon.md), and the
 * system properties the golden tests read (docs/testing.md, "Golden tests").
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
            val module = path
            val record = providers.gradleProperty("golden.record").isPresent
            tasks.withType(Test::class.java).configureEach {
                systemProperty("golden.module", module)
                if (record) systemProperty("golden.record", "true")
            }
        }
}
