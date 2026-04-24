plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
    kotlin("android")
}

android {
    namespace = "com.resonix.sync"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }
}

dependencies {
    // WebSocket client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // JSON serialization (message schemas)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Media3 ExoPlayer bridge
    implementation("androidx.media3:media3-exoplayer:1.3.1")

    // Android core
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation(libs.junit)
}
