plugins {
    id("tvhome.android.library.compose")
    id("tvhome.android.testballoon")
}

android {
    namespace = "dev.niltsiar.tvhome.core.designsystem"

    // The golden helper the feature modules' tests share (docs/testing.md, "Golden tests").
    testFixtures.enable = true

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // TestBalloon does not read @GraphicsMode and TestConfig.robolectric has no graphics mode; the pixel tests need the
        // native canvas.
        unitTests.all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
    }
}

dependencies {
    api(project(":core:config"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    // The Compose compiler plugin runs on these sources too and needs the runtime.
    testFixturesImplementation(libs.androidx.compose.runtime)
    testFixturesImplementation(libs.androidx.compose.ui.graphics)
}
