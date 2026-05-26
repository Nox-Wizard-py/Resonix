package com.noxwizard.resonix.betterlyrics.models

enum class ProviderCategory {
    PREMIUM,    // high-accuracy, word/karaoke sync (Spotify, MusixMatch via BL)
    ENHANCED,   // synced with extra metadata (TTML)
    STANDARD,   // basic line-synced
    FALLBACK,   // plain text / unsynced
}

data class ProviderCapabilities(
    val supportsWordSync: Boolean = false,
    val supportsLineSync: Boolean = true,
    val supportsTranslations: Boolean = false,
    val supportsRomanized: Boolean = false,
    val supportsBackgroundVocals: Boolean = false,
    val supportsKaraoke: Boolean = false,
)

data class ProviderMetadata(
    val providerName: String,
    val sourceName: String,
    val confidence: Float = 1f,
    val category: ProviderCategory = ProviderCategory.STANDARD,
    val capabilities: ProviderCapabilities = ProviderCapabilities(),
)
