plugins {
    id("tvhome.android.library")
}

android {
    namespace = "dev.niltsiar.tvhome.data.inputs"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:config"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
