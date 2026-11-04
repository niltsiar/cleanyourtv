plugins {
    id("tvhome.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "dev.niltsiar.tvhome.baselineprofile"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // The Sony is the only target and is reached over adb, so no Gradle managed device.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
