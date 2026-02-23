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
 * Progress state for playlist imports.
 */
sealed class ImportProgress {
    data class Loading(
        val importedCount: Int,
        val totalEstimate: Int? = null,
        val message: String = "Importing tracks..."
    ) : ImportProgress()
    
    data class Success(val playlist: ParsedPlaylist) : ImportProgress()
    data class Error(val error: Throwable) : ImportProgress()
}

/**
 * Configuration for Spotify scraper from Firebase Remote Config.
 */
data class SpotifyScraperConfig(
    val initialJsonPath: String,
    val continuationTokenPath: String,
    val apiEndpoint: String,
    val maxTracksLimit: Int = 2000,
    val batchSize: Int = 100,
    val paginationType: String = "STANDARD_API", // "STANDARD_API" or "LEGACY"
    val standardApiEndpoint: String = "https://api.spotify.com/v1/playlists/%s/tracks",
    val guestTokenRegex: String = "[\"']accessToken[\"']\\s*:\\s*[\"']([^\"']+)[\"']"
)

/**
 * Interface for providing scraper configuration.
 * Implementations can use Firebase Remote Config or other sources.
 */
interface ScraperConfigProvider {
    fun getSpotifyScraperConfig(): SpotifyScraperConfig
}

/**
 * Supported playlist sources.
 */
enum class PlaylistSource {
    SPOTIFY,
    APPLE_MUSIC,
    TEXT_INPUT,
    UNKNOWN
}
