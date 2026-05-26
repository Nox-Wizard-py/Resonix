plugins {
    alias(libs.plugins.kotlin.serialization)
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)

    // Existing provider modules — used by adapter providers
    implementation(project(":betterlyrics"))
    implementation(project(":lrclib"))
    implementation(project(":kugou"))
    implementation(libs.json)

    testImplementation(libs.junit)
}
