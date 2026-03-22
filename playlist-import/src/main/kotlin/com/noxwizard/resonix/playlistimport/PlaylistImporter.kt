package com.noxwizard.resonix.playlistimport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Main entry point for playlist importing.
 * Automatically detects the source and parses accordingly.
 */
object PlaylistImporter {
    
    /**
     * Import a playlist from URL or text input.
     * Automatically detects the source type.
     */
    fun import(input: String): Flow<ImportProgress> = flow {
        val trimmedInput = input.trim()
        
        when {
            SpotifyParser.isSpotifyUrl(trimmedInput) -> {
                SpotifyParser.importFullPlaylist(trimmedInput).collect { emit(it) }
            }
            // AppleMusicParser can be added here later
            // AppleMusicParser.isAppleMusicUrl(trimmedInput) -> { ... }
            TextParser.isUrl(trimmedInput) -> {
                // Unknown URL format
                emit(ImportProgress.Error(IllegalArgumentException("Unsupported URL format. Supported: Spotify")))
            }
            else -> {
                // Treat as text input
                try {
                    val playlist = TextParser.parseText(trimmedInput)
                    emit(ImportProgress.Success(playlist))
                } catch (e: Exception) {
                    emit(ImportProgress.Error(e))
                }
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
