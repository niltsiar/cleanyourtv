plugins {
    id("tvhome.android.library")
    id("tvhome.android.testballoon")
}

android {
    namespace = "dev.niltsiar.tvhome.data.apps"

    testOptions {
        unitTests.isReturnDefaultValues = true
        // TestBalloon does not read @GraphicsMode; AppArtTest keeps the native canvas it had under JUnit.
        unitTests.all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:config"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
