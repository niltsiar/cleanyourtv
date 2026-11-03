package dev.niltsiar.tvhome.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

internal fun Project.configureKotlin(explicitApi: Boolean) {
    extensions.configure<KotlinBaseExtension> {
        jvmToolchain(JVM_TOOLCHAIN)
        if (explicitApi) explicitApi()
    }
}

internal fun Project.configureAndroid() {
    extensions.configure<CommonExtension> {
        compileSdk { version = release(COMPILE_SDK) }
        defaultConfig.minSdk = MIN_SDK
    }
    extensions.findByType(ApplicationExtension::class.java)?.defaultConfig?.targetSdk = TARGET_SDK
}

internal fun Project.configureCompose() {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    extensions.configure<CommonExtension> {
        buildFeatures.compose = true
    }
    dependencies {
        add("implementation", platform(libs.library("androidx-compose-bom").get()))
        add("implementation", libs.library("androidx-compose-runtime"))
        add("implementation", libs.library("androidx-compose-foundation"))
        add("implementation", libs.library("androidx-compose-ui"))
        add("implementation", libs.library("androidx-compose-ui-toolingPreview"))
        add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
        add("lintChecks", libs.library("composeLints"))
    }
}
