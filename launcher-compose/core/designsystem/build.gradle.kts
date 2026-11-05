plugins {
    id("tvhome.android.library.compose")
}

android {
    namespace = "dev.niltsiar.tvhome.core.designsystem"

    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    testImplementation(libs.junit)
}
