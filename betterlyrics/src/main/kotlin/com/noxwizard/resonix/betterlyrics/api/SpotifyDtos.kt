package com.noxwizard.resonix.betterlyrics.api

import kotlinx.serialization.Serializable

/** Response from akashrchandran/spotify-lyrics-api using `format=id3` */
@Serializable
internal data class SpotifyLyricsResponse(
    val error: Boolean = false,
    val message: String? = null,
    val syncType: String = "LINE_SYNCED",
    val lines: List<SpotifyLyricsLine> = emptyList(),
)

@Serializable
internal data class SpotifyLyricsLine(
    val startTimeMs: String = "0",
    val words: String = "",
    val endTimeMs: String = "0",
    val syllables: List<String> = emptyList(),
)
