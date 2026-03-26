package com.noxwizard.resonix.playlistimport

/**
 * Main entry point for playlist importing.
 * Routes URL-based inputs to UrlExtractor, text inputs to TextParser.
 */
object PlaylistImporter {

    /**
     * Process user input and determine the import path.
     *
     * - URL input → attempts extraction via UrlExtractor
     *   - Returns ParsedTracks if successful (≤100 tracks)
     *   - Returns NeedsManualInput if >100 tracks or extraction fails
     * - Text input → parses directly via TextParser
     *   - Returns ParsedTracks
     */
    suspend fun import(input: String, playlistName: String? = null): ImportInput {
        val trimmedInput = input.trim()

        return when {
            TextParser.isUrl(trimmedInput) && UrlExtractor.isSupportedUrl(trimmedInput) -> {
                UrlExtractor.extract(trimmedInput)
            }
            TextParser.isUrl(trimmedInput) -> {
                // Unsupported URL — tell user to paste tracklist
                ImportInput.NeedsManualInput(
                    tracklistText = "",
                    playlistName = playlistName ?: "Imported Playlist"
                )
            }
            else -> {
                val playlist = TextParser.parseText(trimmedInput, playlistName)
                if (playlist.tracks.isEmpty()) {
                    ImportInput.NeedsManualInput(
                        tracklistText = "",
                        playlistName = playlistName ?: "Imported Playlist"
                    )
                } else {
                    ImportInput.ParsedTracks(playlist)
                }
            }
        }
    }
}
