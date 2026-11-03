plugins {
    id("tvhome.android.library")
}

android {
    namespace = "dev.niltsiar.tvhome.data.apps"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
