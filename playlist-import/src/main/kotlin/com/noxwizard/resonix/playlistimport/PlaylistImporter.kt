package com.noxwizard.resonix.playlistimport

/**
 * Main entry point for playlist importing.
 * Automatically detects the source and parses accordingly.
 */
object PlaylistImporter {
    
    /**
     * Import a playlist from URL or text input.
     * Automatically detects the source type.
     */
    suspend fun import(input: String): Result<ParsedPlaylist> {
        val trimmedInput = input.trim()
        
        return when {
            SpotifyParser.isSpotifyUrl(trimmedInput) -> {
                SpotifyParser.parsePlaylist(trimmedInput)
            }
            // AppleMusicParser can be added here later
            // AppleMusicParser.isAppleMusicUrl(trimmedInput) -> { ... }
            TextParser.isUrl(trimmedInput) -> {
                // Unknown URL format
                Result.failure(IllegalArgumentException("Unsupported URL format. Supported: Spotify"))
            }
            else -> {
                // Treat as text input
                Result.success(TextParser.parseText(trimmedInput))
            }
        }
    }
    
    /**
     * Get a list of supported import sources.
     */
    fun getSupportedSources(): List<String> {
        return listOf(
            "Spotify playlist URLs (open.spotify.com/playlist/...)",
            "Text input (one track per line: 'Artist - Title')"
        )
    }
}
