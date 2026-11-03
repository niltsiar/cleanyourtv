plugins {
    id("tvhome.android.application")
    alias(libs.plugins.metro)
}

android {
    namespace = "dev.niltsiar.tvhome"

    defaultConfig {
        applicationId = "dev.niltsiar.tvhome"
        versionCode = 1
        versionName = "0.1.0"
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
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:home"))
    implementation(libs.androidx.activity.compose)
}
