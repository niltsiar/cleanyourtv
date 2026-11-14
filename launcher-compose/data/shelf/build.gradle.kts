plugins {
    id("tvhome.android.library")
    id("tvhome.android.testballoon")
}

android {
    namespace = "dev.niltsiar.tvhome.data.shelf"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:config"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
