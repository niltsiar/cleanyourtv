plugins {
    id("tvhome.android.application.plain")
    id("tvhome.android.testballoon")
}

android {
    namespace = "dev.niltsiar.tvhome.posters"

    defaultConfig {
        applicationId = "dev.niltsiar.tvhome.posters"
        versionCode = 1
        versionName = "0.1.0"
    }

    testOptions {
        // TestBalloon does not read @GraphicsMode; the tests decode and encode real pictures.
        unitTests.all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Home serves its poster URLs only to a helper signed with its own key, and Home's release uses the debug key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(libs.okhttp.tls)
}
