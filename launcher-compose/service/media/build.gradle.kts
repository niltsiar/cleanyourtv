plugins {
    id("tvhome.android.library")
    id("tvhome.android.testballoon")
}

android {
    namespace = "dev.niltsiar.tvhome.service.media"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
