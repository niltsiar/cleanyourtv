plugins {
    id("tvhome.android.application")
    alias(libs.plugins.metro)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "dev.niltsiar.tvhome"

    defaultConfig {
        applicationId = "dev.niltsiar.tvhome"
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "presenter"
    productFlavors {
        create("molecule") { dimension = "presenter" }
        create("transform") { dimension = "presenter" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sideloaded over ADB and never published, so the debug key is enough.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":feature:home"))
    implementation(project(":data:apps"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
}
