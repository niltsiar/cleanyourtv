plugins {
    id("tvhome.android.library")
}

// The theme files that the parser test reads: every themes/*/config.json in the repository.
val themesDir = rootDir.resolve("../themes")

android {
    namespace = "dev.niltsiar.tvhome.core.config"

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.inputs
                .dir(themesDir)
                .withPropertyName("themes")
                .withPathSensitivity(PathSensitivity.RELATIVE)
            it.systemProperty("tvhome.themesDir", themesDir.absolutePath)
        }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
