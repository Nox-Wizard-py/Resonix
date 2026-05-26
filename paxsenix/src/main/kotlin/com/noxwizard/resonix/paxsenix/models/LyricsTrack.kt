package com.noxwizard.resonix.paxsenix.models

data class LyricsTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = -1L,
    val spotifyTrackId: String? = null,
) {
    val durationSec: Int get() = if (durationMs > 0) (durationMs / 1000).toInt() else -1
}
