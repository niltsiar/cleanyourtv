plugins {
    id("tvhome.android.library")
}

android {
    namespace = "dev.niltsiar.tvhome.core.model"
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui.graphics)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
