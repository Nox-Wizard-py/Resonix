package com.noxwizard.resonix.playlistimport

/**
 * Represents a track parsed from an external platform.
 */
data class ParsedTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val originalId: String? = null // Original ID from source platform
)

/**
 * Result of parsing a playlist URL.
 */
data class ParsedPlaylist(
    val name: String,
    val description: String? = null,
    val tracks: List<ParsedTrack>,
    val imageUrl: String? = null,
    val source: PlaylistSource
)

/**
 * Supported playlist sources.
 */
enum class PlaylistSource {
    SPOTIFY,
    APPLE_MUSIC,
    TEXT_INPUT,
    UNKNOWN
}
