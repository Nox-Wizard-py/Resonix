package com.noxwizard.resonix.playlistimport

/**
 * Represents a track parsed from an external platform.
 */
data class ParsedTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val originalId: String? = null
)

/**
 * Result of parsing a playlist.
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
    TEXT_INPUT,
    URL_EXTRACT,
    UNKNOWN
}

/**
 * Result of the initial import step.
 * Either we got tracks directly, or we need the user to paste a tracklist manually.
 */
sealed class ImportInput {
    data class ParsedTracks(val playlist: ParsedPlaylist) : ImportInput()
    data class NeedsManualInput(
        val tracklistText: String,
        val playlistName: String
    ) : ImportInput()
}

/**
 * State for an individual track match attempt.
 */
sealed class TrackMatchState {
    data object Pending : TrackMatchState()
    data class Matched(
        val score: Float,
        val matchedTitle: String,
        val matchedArtist: String,
        val matchedId: String
    ) : TrackMatchState()
    data object NoMatch : TrackMatchState()
    data class Error(val message: String) : TrackMatchState()
}

/**
 * Real-time import progress state.
 */
sealed class ImportState {
    data object Idle : ImportState()
    data class Parsing(val message: String = "Parsing input...") : ImportState()

    data class Matching(
        val current: Int,
        val total: Int,
        val currentTrackName: String = ""
    ) : ImportState()
    data class Saving(
        val current: Int,
        val total: Int
    ) : ImportState()
    data class Done(
        val playlistName: String,
        val matchedCount: Int,
        val totalCount: Int
    ) : ImportState()
    data class Failed(val error: String) : ImportState()
}
