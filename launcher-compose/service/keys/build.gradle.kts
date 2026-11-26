plugins {
    id("tvhome.android.library.compose")
    id("tvhome.android.testballoon")
}

android {
    namespace = "dev.niltsiar.tvhome.service.keys"

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // TestBalloon does not read @GraphicsMode and TestConfig.robolectric has no graphics mode; the window pixel tests need
        // the native canvas.
        unitTests.all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
    }
}

dependencies {
    api(project(":core:config"))
    implementation(project(":core:model"))
    implementation(project(":data:inputs"))
    implementation(project(":data:system"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":data:apps"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
