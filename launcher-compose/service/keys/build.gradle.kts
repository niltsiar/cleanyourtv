plugins {
    id("tvhome.android.library")
}

android {
    namespace = "dev.niltsiar.tvhome.service.keys"

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":core:config"))
    implementation(project(":core:model"))
    implementation(project(":data:inputs"))
    implementation(project(":data:system"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":data:apps"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
