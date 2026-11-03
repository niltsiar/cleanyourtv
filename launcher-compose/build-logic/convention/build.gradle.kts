plugins {
    `kotlin-dsl`
}

group = "dev.niltsiar.tvhome.buildlogic"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.composeCompilerGradlePlugin)
    implementation(libs.spotless.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("quality") {
            id = "tvhome.quality"
            implementationClass = "dev.niltsiar.tvhome.buildlogic.QualityConventionPlugin"
        }
        register("androidApplication") {
            id = "tvhome.android.application"
            implementationClass = "dev.niltsiar.tvhome.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidTest") {
            id = "tvhome.android.test"
            implementationClass = "dev.niltsiar.tvhome.buildlogic.AndroidTestConventionPlugin"
        }
        register("androidLibrary") {
            id = "tvhome.android.library"
            implementationClass = "dev.niltsiar.tvhome.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "tvhome.android.library.compose"
            implementationClass = "dev.niltsiar.tvhome.buildlogic.AndroidLibraryComposeConventionPlugin"
        }
    }
}
