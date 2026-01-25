package com.noxwizard.resonix.playlistimport

/**
 * Parses text input to extract track information.
 * Supports formats like:
 * - "Artist - Title"
 * - "Title by Artist"
 * - "Artist: Title"
 * - Just "Title" (no artist)
 */
object TextParser {
    
    private val DASH_PATTERN = Regex("""^\s*(.+?)\s*[-–—]\s*(.+?)\s*$""")
    private val BY_PATTERN = Regex("""^\s*(.+?)\s+by\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)
    private val COLON_PATTERN = Regex("""^\s*(.+?)\s*:\s*(.+?)\s*$""")
    
    /**
     * Parse a multi-line text input into a list of tracks.
     */
    fun parseText(input: String): ParsedPlaylist {
        val lines = input.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { !it.startsWith("#") } // Allow comments
        
        val tracks = lines.mapNotNull { line ->
            parseLine(line)
        }
        
        return ParsedPlaylist(
            name = "Imported Playlist",
            tracks = tracks,
            source = PlaylistSource.TEXT_INPUT
        )
    }
    
    /**
     * Parse a single line into a track.
     */
    fun parseLine(line: String): ParsedTrack? {
        if (line.isBlank()) return null
        
        // Try "Artist - Title" format
        DASH_PATTERN.find(line)?.let { match ->
            val (artist, title) = match.destructured
            return ParsedTrack(title = title.trim(), artist = artist.trim())
        }
        
        // Try "Title by Artist" format
        BY_PATTERN.find(line)?.let { match ->
            val (title, artist) = match.destructured
            return ParsedTrack(title = title.trim(), artist = artist.trim())
        }
        
        // Try "Artist: Title" format
        COLON_PATTERN.find(line)?.let { match ->
            val (artist, title) = match.destructured
            return ParsedTrack(title = title.trim(), artist = artist.trim())
        }
        
        // If no pattern matches, treat entire line as title
        return ParsedTrack(title = line.trim(), artist = "")
    }
    
    /**
     * Check if input looks like a URL.
     */
    fun isUrl(input: String): Boolean {
        return input.trim().startsWith("http://") || input.trim().startsWith("https://")
    }
}
