package com.noxwizard.resonix.betterlyrics.models

/**
 * Translated line paired with its parent [LyricsLine].
 * Populated only when the provider supports translations.
 */
data class TranslationLine(
    val text: String,
    val language: String,
)
