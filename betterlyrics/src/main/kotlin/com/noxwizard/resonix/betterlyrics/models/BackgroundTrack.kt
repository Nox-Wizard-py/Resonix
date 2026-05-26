package com.noxwizard.resonix.betterlyrics.models

/**
 * Represents a background vocal line that plays alongside a main [LyricsLine].
 * Used to distinguish backing singers / chorus layers from lead vocals.
 */
data class BackgroundTrack(
    val id: String,
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val words: List<WordTiming> = emptyList(),
)
