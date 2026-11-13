plugins {
    id("tvhome.android.library.compose")
    id("tvhome.android.testballoon")
}

android {
    namespace = "dev.niltsiar.tvhome.core.designsystem"

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // TestBalloon does not read @GraphicsMode and TestConfig.robolectric has no graphics mode; the pixel tests need the
        // native canvas.
        unitTests.all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
