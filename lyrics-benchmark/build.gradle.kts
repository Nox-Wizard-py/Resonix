plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":paxsenix"))
    implementation(project(":betterlyrics"))
    implementation(project(":lrclib"))
    implementation(project(":kugou"))
    
    // Core coroutines for stress testing
    implementation(libs.kotlinx.coroutines.core)
    
    testImplementation(libs.junit)
}
