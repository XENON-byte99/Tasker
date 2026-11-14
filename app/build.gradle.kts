plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Each GitHub build gets the next number, so the app can tell when a newer build exists.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.nahian.tasker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nahian.tasker"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }
    signingConfigs {
        // One fixed key for every build, so a new build installs over the old one and keeps the data.
        create("tasker") {
            storeFile = rootProject.file("keystore/tasker.jks")
            storePassword = "tasker-update"
            keyAlias = "tasker"
            keyPassword = "tasker-update"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("tasker")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("tasker")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
