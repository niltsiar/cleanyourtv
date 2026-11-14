plugins {
    id("tvhome.android.library")
    id("tvhome.android.testballoon")
}

android {
    namespace = "dev.niltsiar.tvhome.data.wallpaper"

    testOptions {
        unitTests.isReturnDefaultValues = true
        // TestBalloon does not read @GraphicsMode; the wallpaper picture tests draw and decode real bitmaps.
        unitTests.all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:config"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
