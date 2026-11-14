plugins {
    id("tvhome.android.library.compose")
    id("tvhome.android.testballoon")
}

android {
    namespace = "dev.niltsiar.tvhome.feature.settings"

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // TestBalloon does not read @GraphicsMode and TestConfig.robolectric has no graphics mode; the pixel tests need the
        // native canvas.
        unitTests.all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
    }
}

dependencies {
    api(libs.androidx.lifecycle.viewmodel)
    api(project(":core:config"))
    api(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.molecule.runtime)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
