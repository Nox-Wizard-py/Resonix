package com.noxwizard.resonix.playlistimport

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*

/**
 * Parses Spotify playlist URLs to extract track information.
 * Works with public playlists only (no OAuth required).
 */
object SpotifyParser {
    
    private val client = HttpClient(OkHttp)
    
    // Regex patterns for Spotify URLs
    private val PLAYLIST_ID_REGEX = Regex("""spotify\.com/playlist/([a-zA-Z0-9]+)""")
    private val ALBUM_ID_REGEX = Regex("""spotify\.com/album/([a-zA-Z0-9]+)""")
    
    /**
     * Check if a URL is a valid Spotify playlist or album URL.
     */
    fun isSpotifyUrl(url: String): Boolean {
        return url.contains("spotify.com/playlist/") || url.contains("spotify.com/album/")
    }
    
    /**
     * Extract playlist ID from Spotify URL.
     */
    fun extractPlaylistId(url: String): String? {
        return PLAYLIST_ID_REGEX.find(url)?.groupValues?.getOrNull(1)
    }
    
    /**
     * Extract album ID from Spotify URL.
     */
    fun extractAlbumId(url: String): String? {
        return ALBUM_ID_REGEX.find(url)?.groupValues?.getOrNull(1)
    }
    
    /**
     * Parse a Spotify playlist URL and return track information.
     * Uses the Spotify embed endpoint which doesn't require authentication.
     */
    suspend fun parsePlaylist(url: String): Result<ParsedPlaylist> = runCatching {
        val playlistId = extractPlaylistId(url) 
            ?: throw IllegalArgumentException("Invalid Spotify playlist URL")
        
        // Fetch the embed page which contains playlist data as JSON
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
        val response = client.get(embedUrl) {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }
        
        val html = response.bodyAsText()
        
        // Extract the JSON data from the embed page
        val tracks = parseTracksFromHtml(html)
        val playlistName = parsePlaylistNameFromHtml(html) ?: "Imported Playlist"
        
        ParsedPlaylist(
            name = playlistName,
            tracks = tracks,
            source = PlaylistSource.SPOTIFY
        )
    }
    
    /**
     * Parse track data from Spotify embed HTML.
     * The embed page contains a script tag with JSON data.
     */
    private fun parseTracksFromHtml(html: String): List<ParsedTrack> {
        val tracks = mutableListOf<ParsedTrack>()
        
        // Try to find the resource JSON in the HTML
        // Spotify embeds contain data in a script tag with id="__NEXT_DATA__" or similar
        val jsonPattern = Regex("""<script[^>]*id="__NEXT_DATA__"[^>]*>(.+?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val jsonMatch = jsonPattern.find(html)
        
        if (jsonMatch != null) {
            try {
                val jsonString = jsonMatch.groupValues[1]
                val json = Json { ignoreUnknownKeys = true }
                val data = json.parseToJsonElement(jsonString).jsonObject
                
                // Navigate through the JSON structure to find tracks
                val trackList = findTrackList(data)
                trackList?.forEach { trackElement ->
                    val track = trackElement.jsonObject
                    val title = track["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val artists = track["artists"]?.jsonArray
                        ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                        ?.joinToString(", ") ?: ""
                    val duration = track["duration_ms"]?.jsonPrimitive?.longOrNull
                    val album = track["album"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                    
                    if (title.isNotEmpty()) {
                        tracks.add(ParsedTrack(
                            title = title,
                            artist = artists,
                            album = album,
                            durationMs = duration
                        ))
                    }
                }
            } catch (e: Exception) {
                // Fall back to regex parsing if JSON parsing fails
                return parseTracksWithRegex(html)
            }
        }
        
        // If no JSON found, try regex parsing
        if (tracks.isEmpty()) {
            return parseTracksWithRegex(html)
        }
        
        return tracks
    }
    
    /**
     * Fallback: Parse tracks using regex patterns.
     */
    private fun parseTracksWithRegex(html: String): List<ParsedTrack> {
        val tracks = mutableListOf<ParsedTrack>()
        
        // Look for track names and artists in the HTML
        // This is a simplified pattern - may need adjustment based on actual HTML structure
        val trackPattern = Regex(""""name"\s*:\s*"([^"]+)"[^}]*"artists"\s*:\s*\[([^\]]+)\]""")
        
        trackPattern.findAll(html).forEach { match ->
            val title = match.groupValues.getOrNull(1) ?: return@forEach
            val artistsJson = match.groupValues.getOrNull(2) ?: ""
            
            // Extract artist names from the artists array
            val artistPattern = Regex(""""name"\s*:\s*"([^"]+)"""")
            val artists = artistPattern.findAll(artistsJson)
                .mapNotNull { it.groupValues.getOrNull(1) }
                .joinToString(", ")
            
            tracks.add(ParsedTrack(
                title = title,
                artist = artists
            ))
        }
        
        return tracks
    }
    
    /**
     * Recursively find the track list in a JSON object.
     */
    private fun findTrackList(json: JsonElement): JsonArray? {
        when (json) {
            is JsonObject -> {
                // Check if this object has a "tracks" or "items" key
                json["tracks"]?.let { tracks ->
                    if (tracks is JsonArray) return tracks
                    if (tracks is JsonObject) {
                        tracks["items"]?.let { items ->
                            if (items is JsonArray) return items
                        }
                    }
                }
                json["items"]?.let { items ->
                    if (items is JsonArray) return items
                }
                
                // Recursively search in nested objects
                for ((_, value) in json) {
                    val result = findTrackList(value)
                    if (result != null && result.isNotEmpty()) return result
                }
            }
            is JsonArray -> {
                for (element in json) {
                    val result = findTrackList(element)
                    if (result != null && result.isNotEmpty()) return result
                }
            }
            else -> {}
        }
        return null
    }
    
    /**
     * Extract playlist name from HTML.
     */
    private fun parsePlaylistNameFromHtml(html: String): String? {
        // Try to find the title in the HTML
        val titlePattern = Regex("""<title>([^<]+)</title>""")
        val match = titlePattern.find(html)
        return match?.groupValues?.getOrNull(1)
            ?.replace(" - playlist by .* | Spotify".toRegex(), "")
            ?.replace(" | Spotify", "")
            ?.trim()
    }
}
