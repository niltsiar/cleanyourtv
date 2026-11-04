plugins {
    id("tvhome.android.library.compose")
}

android {
    namespace = "dev.niltsiar.tvhome.core.designsystem"

    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    implementation(libs.haze)
    implementation(libs.haze.glass)
    implementation(libs.haze.glass.material3)

    testImplementation(libs.junit)
}
