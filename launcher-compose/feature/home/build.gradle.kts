plugins {
    id("tvhome.android.library.compose")
}

android {
    namespace = "dev.niltsiar.tvhome.feature.home"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.lifecycle.viewmodel)
    implementation(project(":core:designsystem"))
    implementation(libs.molecule.runtime)
    implementation(libs.androidx.appstate.transform)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.appstate.transform.testing)
}
