package com.noxwizard.resonix.betterlyrics.models

/**
 * Word-level timing for karaoke-ready rendering.
 * Covers Apple Music-style fill animation and syllable morphing.
 */
data class WordTiming(
    val word: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    /** Progression [0f..1f] — computed during animation, not stored here */
    val syllables: List<String> = emptyList(),
)
