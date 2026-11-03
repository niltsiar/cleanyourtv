plugins {
    id("tvhome.android.library.compose")
}

android {
    namespace = "dev.niltsiar.tvhome.feature.home"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
}
