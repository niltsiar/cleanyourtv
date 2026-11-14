package dev.niltsiar.tvhome.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Spotless with ktlint on every project, plus Android lint and [CheckNoJUnitTests] on app and library modules. Applied by the other
 * convention plugins; the root project applies it directly to cover build-logic and the build scripts.
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.diffplug.spotless")

            val ktlintVersion = libs.version("ktlint")
            val isRoot = this == rootProject
            extensions.configure<SpotlessExtension> {
                kotlin {
                    target(if (isRoot) "build-logic/**/src/**/*.kt" else "src/**/*.kt")
                    ktlint(ktlintVersion)
                }
                kotlinGradle {
                    target(if (isRoot) listOf("*.gradle.kts", "build-logic/**/*.gradle.kts") else listOf("*.gradle.kts"))
                    targetExclude("**/build/**")
                    ktlint(ktlintVersion)
                }
            }

            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> {
                    lint.configureLint()
                    // Off by default in AGP; without an icon the system draws its default robot on toasts.
                    lint.error += "MissingApplicationIcon"
                }
                // The `lint` task covers one variant, so check lints every debug variant.
                extensions.configure<ApplicationAndroidComponentsExtension> {
                    onVariants(selector().withBuildType("debug")) { variant ->
                        tasks.named("check") { dependsOn("lint${variant.name.replaceFirstChar(Char::titlecase)}") }
                    }
                }
                checkNoJUnitTests()
            }
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { lint.configureLint() }
                checkNoJUnitTests()
            }
        }
}

private fun Lint.configureLint() {
    abortOnError = true
    warningsAsErrors = false
    checkDependencies = false
    // A language ported from the reference launcher can lack strings; they fall back to English.
    informational += "MissingTranslation"
    textReport = true
    htmlReport = false
    xmlReport = false
}
