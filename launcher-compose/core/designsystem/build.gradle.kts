plugins {
    id("tvhome.android.library.compose")
}

android {
    namespace = "dev.niltsiar.tvhome.core.designsystem"
}

dependencies {
    api(libs.haze)
    implementation(libs.haze.blur)
}
