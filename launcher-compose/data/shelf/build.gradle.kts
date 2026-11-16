plugins {
    id("tvhome.android.library")
    id("tvhome.android.testballoon")
}

android {
    namespace = "dev.niltsiar.tvhome.data.shelf"

    testOptions {
        unitTests.isReturnDefaultValues = true
        // TestBalloon does not read @GraphicsMode; ShelfPostersTest decodes real pictures.
        unitTests.all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:config"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
