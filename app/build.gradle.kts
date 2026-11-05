import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** "0.4.0" from the release tag (-PappVersion), as versionName and 400 as versionCode. */
val appVersion = (findProperty("appVersion") as String?) ?: "0.0.1"
val appVersionCode = appVersion.split('.').map(String::toInt).let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.josemiguel.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.josemiguel.launcher"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    // The release key is this project's first debug key, kept (1Password, CI
    // secrets) so releases install over the debug builds already on the
    // devices: a new key would mean uninstalling, losing every setting and
    // permission. CI hands it over through INTENTIONAL_KEYSTORE & co; locally
    // it's the debug keystore itself.
    signingConfigs {
        create("release") {
            // Locally, whichever keystore the debug build signs with (on this
            // machine the XDG one, ~/.config/.android/debug.keystore).
            storeFile = System.getenv("INTENTIONAL_KEYSTORE")?.let(::file) ?: signingConfigs.getByName("debug").storeFile
            storePassword = System.getenv("INTENTIONAL_KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("INTENTIONAL_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("INTENTIONAL_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
}
