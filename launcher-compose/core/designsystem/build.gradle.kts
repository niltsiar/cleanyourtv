plugins {
    id("tvhome.android.library.compose")
}

android {
    namespace = "dev.niltsiar.tvhome.core.designsystem"
}

dependencies {
    implementation(libs.haze)
    implementation(libs.haze.glass)
    implementation(libs.haze.glass.material3)
}
