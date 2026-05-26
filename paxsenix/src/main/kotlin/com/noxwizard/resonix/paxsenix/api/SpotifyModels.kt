package com.noxwizard.resonix.paxsenix.api

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyLyricsResponse(
    val syncType: String = "LINE_SYNCED",
    val lines: List<SpotifyLyricsLine> = emptyList(),
)

@Serializable
data class SpotifyLyricsLine(
    val timeTag: String,
    val words: String,
)
