plugins {
    id("tvhome.android.application")
    alias(libs.plugins.metro)
    alias(libs.plugins.baselineprofile)
}

// -Ptvhome.glass=flat builds the flat scrim instead of the refractive glass, for comparing the two on the TV.
val glassKind =
    when (val glass = providers.gradleProperty("tvhome.glass").getOrElse("refractive")) {
        "refractive" -> "Refractive"
        "flat" -> "Flat"
        else -> throw GradleException("tvhome.glass must be 'refractive' or 'flat', not '$glass'")
    }

android {
    namespace = "dev.niltsiar.tvhome"

    buildFeatures.buildConfig = true

    defaultConfig {
        applicationId = "dev.niltsiar.tvhome"
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GLASS_KIND", "\"$glassKind\"")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
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
    implementation(project(":core:config"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:clock"))
    implementation(project(":feature:home"))
    implementation(project(":feature:library"))
    implementation(project(":service:keys"))
    implementation(project(":data:apps"))
    implementation(project(":data:inputs"))
    implementation(project(":data:system"))
    implementation(project(":data:wallpaper"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.molecule.runtime)
    testImplementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
