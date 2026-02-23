package com.noxwizard.resonix.playlistimport

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

    private data class InitialBatchResult(
        val tracks: List<ParsedTrack>,
        val cursor: String?,
        val playlistName: String?,
        val accessToken: String?
    )

/**
 * Parses Spotify playlist URLs to extract track information.
 * Works with public playlists only (no OAuth required).
 */
object SpotifyParser {
    
    private val client = HttpClient(OkHttp)
    // Regex patterns for Spotify URLs
    private val PLAYLIST_ID_REGEX = Regex("""spotify\.com/playlist/([a-zA-Z0-9]+)""")
    private val ALBUM_ID_REGEX = Regex("""spotify\.com/album/([a-zA-Z0-9]+)""")
    
    private const val MAX_TRACKS_LIMIT = 2000
    private const val BATCH_SIZE = 100
    private const val STANDARD_API_ENDPOINT = "https://api.spotify.com/v1/playlists/%s/tracks"
    
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
     * Interface for providing Spotify access tokens (e.g. from OAuth).
     */
    interface SpotifyAuthProvider {
        suspend fun getAccessToken(): String?
        fun isAuthorized(): Boolean
    }

    private var authProvider: SpotifyAuthProvider? = null

    /**
     * Set the auth provider instance.
     */
    fun setAuthProvider(provider: SpotifyAuthProvider) {
        println("[SpotifyParser] Setting auth provider: $provider")
        authProvider = provider
    }

    /**
     * Import a full Spotify playlist with progress updates.
     * Bypasses the 100-track limit using recursive pagination.
     * 
     * @param url Spotify playlist URL
     * @return Flow of ImportProgress events (Loading updates, Success, or Error)
     */
    fun importFullPlaylist(url: String): Flow<ImportProgress> = flow {
        try {
            val playlistId = extractPlaylistId(url)
                ?: throw IllegalArgumentException("Invalid Spotify playlist URL")
            
            // Hardcoded config for Standard API
            // val config = configProvider?.getSpotifyScraperConfig() ?: getDefaultConfig()
            
            val allTracks = mutableListOf<ParsedTrack>()
            var playlistName: String? = null
            var continuationToken: String? = null
            var batchCount = 0
            
            // Step 1: Fetch initial batch from embed page
            emit(ImportProgress.Loading(0, null, "Loading playlist..."))
            
            val result = fetchInitialBatch(playlistId)
            val initialTracks = result.tracks
            continuationToken = result.cursor
            playlistName = result.playlistName
            val accessToken = result.accessToken
            
            allTracks.addAll(initialTracks)
            batchCount++
            
            emit(ImportProgress.Loading(allTracks.size, null))
            
            // Step 2: Recursive pagination loop
            while (continuationToken != null && allTracks.size < MAX_TRACKS_LIMIT) {
                delay(100) // Small delay to prevent rate limiting
                
                val (nextTracks, nextToken) = fetchNextBatch(
                    cursor = continuationToken,
                    playlistId = playlistId,
                    accessToken = accessToken
                )
                
                if (nextTracks.isEmpty()) break
                
                allTracks.addAll(nextTracks)
                continuationToken = nextToken
                batchCount++
                
                emit(ImportProgress.Loading(
                    importedCount = allTracks.size,
                    message = "Importing ${allTracks.size} songs..."
                ))
            }
            
            // Check if we hit the safety cap
            if (allTracks.size >= MAX_TRACKS_LIMIT) {
                println("[SpotifyParser] Hit safety cap at $MAX_TRACKS_LIMIT tracks")
            }
            
            // Emit success
            val playlist = ParsedPlaylist(
                name = playlistName ?: "Imported Playlist",
                tracks = allTracks,
                source = PlaylistSource.SPOTIFY
            )
            
            emit(ImportProgress.Success(playlist))
            
        } catch (e: Exception) {
            println("[SpotifyParser] Error importing playlist: ${e.message}")
            e.printStackTrace()
            emit(ImportProgress.Error(e))
        }
    }
    
    /**
     * Parse a Spotify playlist URL and return track information.
     * Uses the Spotify embed endpoint which doesn't require authentication.
     * 
     * @deprecated Use importFullPlaylist() for better progress tracking and pagination support.
     */
    @Deprecated("Use importFullPlaylist() instead", ReplaceWith("importFullPlaylist(url)"))
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
        val jsonPattern = Regex("""<script[^>]*id="__NEXT_DATA__"[^>]*>(.+?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val jsonMatch = jsonPattern.find(html)
        
        if (jsonMatch != null) {

            try {
                val jsonString = jsonMatch.groupValues[1]
                val json = Json { ignoreUnknownKeys = true }
                val data = json.parseToJsonElement(jsonString).jsonObject
                println("DEBUG: JSON parsed successfully")
                
                // Navigate through the JSON structure to find tracks
                println("DEBUG: Searching for tracks in JSON...")
                val trackList = findTrackList(data)
                println("DEBUG: findTrackList returned: ${trackList?.size ?: "null"}")
                println("DEBUG: findTrackList returned: ${trackList?.size ?: "null"}")
                
                if (trackList != null) {
                    tracks.addAll(parseTracksFromJsonArray(trackList))
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
                json["trackList"]?.let { trackList ->
                    if (trackList is JsonArray) return trackList
                }
                json["items"]?.let { items ->
                    if (items is JsonArray) return items
                }
                
                // Recursively search in nested objects
                for ((key, value) in json) {
                    // println("DEBUG: Visiting key: $key")
                    val result = findTrackList(value)
                    if (result != null && result.isNotEmpty()) {
                        println("DEBUG: Found tracks in recursion at key: $key")
                        return result
                    }
                }
            }
// ...
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

    /**
     * Fetch the initial batch of tracks from Spotify using the Standard API.
     * Returns: InitialBatchResult with tracks, continuation token (offset), and playlist name
     */
    private suspend fun fetchInitialBatch(
        playlistId: String
    ): InitialBatchResult = withContext(Dispatchers.IO) {
        try {
            // Get access token from Auth Provider
            val accessToken = authProvider?.getAccessToken()
            
            if (accessToken == null) {
                println("[SpotifyParser] NO ACCESS TOKEN FOUND! Cannot import without authentication.")
                return@withContext InitialBatchResult(emptyList(), null, null, null)
            }
            
            println("[SpotifyParser] Found access token: ${accessToken.take(10)}...")
            
            // Fetch initial batch using Standard API (offset 0)
            val url = STANDARD_API_ENDPOINT.format(playlistId) + 
                     "?offset=0&limit=$BATCH_SIZE&fields=name,items(track(name,duration_ms,album(name),artists(name)))&market=from_token"
            
            val response = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                header("Authorization", "Bearer $accessToken")
            }
            
            println("[SpotifyParser] Initial batch API Response Status: ${response.status}")
            
            if (response.status.value != 200) {
                val errorBody = response.bodyAsText()
                println("[SpotifyParser] Error Response: $errorBody")
                return@withContext InitialBatchResult(emptyList(), null, null, null)
            }
            
            val jsonString = response.bodyAsText()
            val json = Json { ignoreUnknownKeys = true }
            val data = json.parseToJsonElement(jsonString).jsonObject
            
            // Extract playlist name from the response
            val playlistName = data["name"]?.jsonPrimitive?.contentOrNull
            
            // Extract tracks from Standard API format
            val tracksArray = extractTracksArrayStandard(data)
            val tracks = parseTracksFromJsonArray(tracksArray)
            
            println("[SpotifyParser] Initial batch fetched: ${tracks.size} tracks")
            
            // Set continuation token to next offset
            val nextToken = if (tracks.size >= BATCH_SIZE) {
                tracks.size.toString() // Next offset
            } else {
                null // No more tracks
            }
            
            InitialBatchResult(tracks, nextToken, playlistName, accessToken)
        } catch (e: Exception) {
            println("[SpotifyParser] Error fetching initial batch: ${e.message}")
            e.printStackTrace()
            InitialBatchResult(emptyList(), null, null, null)
        }
    }
    
    /**
     * Fetch the next batch of tracks using pagination cursor.
     * Returns: Pair of (tracks, nextContinuationToken)
     */
    private suspend fun fetchNextBatch(
        cursor: String,
        playlistId: String,
        accessToken: String?
    ): Pair<List<ParsedTrack>, String?> = withContext(Dispatchers.IO) {
        // Prefer explicit auth provider token if available, otherwise use the one passed in (guest)
        val finalAccessToken = authProvider?.getAccessToken() ?: accessToken

        // Always use Standard API
        return@withContext fetchNextBatchStandard(cursor.toIntOrNull() ?: 0, playlistId, finalAccessToken)
    }

    private suspend fun fetchNextBatchStandard(
        offset: Int,
        playlistId: String,
        accessToken: String?
    ): Pair<List<ParsedTrack>, String?> {
        try {
            // Standard V1 API endpoint with market=from_token
            val url = STANDARD_API_ENDPOINT.format(playlistId) + 
                     "?offset=$offset&limit=$BATCH_SIZE&fields=items(track(name,duration_ms,album(name),artists(name)))&market=from_token"
            
            val response = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                if (accessToken != null) {
                    println("[SpotifyParser] Making Standard API call to: $url with token: ${accessToken.take(10)}...")
                    header("Authorization", "Bearer $accessToken")
                } else {
                    println("[SpotifyParser] Making Standard API call WITHOUT token (likely to fail) to: $url")
                }
            }
            
            println("[SpotifyParser] Standard API Response Status: ${response.status}")
            val jsonString = response.bodyAsText()
            if (response.status.value != 200) {
                 println("[SpotifyParser] Error Response Body: $jsonString")
            }
            val json = Json { ignoreUnknownKeys = true }
            val data = json.parseToJsonElement(jsonString)
            
            // Standard API returns { "items": [ ... ] }
            val tracksArray = extractTracksArrayStandard(data)
            val tracks = parseTracksFromJsonArray(tracksArray)
            
            return if (tracks.isNotEmpty()) {
                Pair(tracks, (offset + tracks.size).toString())
            } else {
                Pair(emptyList(), null)
            }
        } catch (e: Exception) {
            println("[SpotifyParser] Error in Standard API fetch: ${e.message}")
            return Pair(emptyList(), null)
        }
    }

    private fun extractTracksArrayStandard(json: JsonElement): JsonArray {
        return try {
            if (json is JsonObject) {
                val items = json["items"]?.jsonArray ?: return JsonArray(emptyList())
                // Unwrap the "track" object from each item
                val tracks = items.mapNotNull { 
                    it.jsonObject["track"] 
                }
                JsonArray(tracks)
            } else {
                JsonArray(emptyList())
            }
        } catch (e: Exception) {
            JsonArray(emptyList())
        }
    }
    
    /**
     * Extract cursor/token from JSON using a dot-separated path.
     * Example path: "props.pageProps.state.data.entity.tracks.pagingInfo.nextPageToken"
     */
    private fun extractCursorFromJson(json: JsonElement, path: String): String? {
        return try {
            val parts = path.split(".")
            var current: JsonElement? = json
            
            for (part in parts) {
                current = when (current) {
                    is JsonObject -> current[part]
                    is JsonArray -> {
                        // If it's an array, try to get the first element
                        current.firstOrNull()?.let { 
                            if (it is JsonObject) it[part] else null 
                        }
                    }
                    else -> null
                }
                
                if (current == null) return null
            }
            
            current?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            println("[SpotifyParser] Error extracting cursor from path: $path - ${e.message}")
            null
        }
    }
    
    /**
     * Extract tracks from JSON using a dot-separated path.
     * Example path: "props.pageProps.state.data.entity.tracks.items"
     */
    private fun extractTracksFromJson(json: JsonElement, path: String): List<ParsedTrack> {
        return try {
            // Priority 1: Try the provided config path
            var tracks = extractTracksFromPath(json, path)
            
            // Priority 2: If empty, try the new known 'trackList' path
            if (tracks.isEmpty()) {
                tracks = extractTracksFromPath(json, "props.pageProps.state.data.entity.trackList")
            }
            
            // Priority 3: If still empty, try the old 'tracks.items' path
            if (tracks.isEmpty()) {
                tracks = extractTracksFromPath(json, "props.pageProps.state.data.entity.tracks.items")
            }
            
            tracks
        } catch (e: Exception) {
            println("[SpotifyParser] Error extracting tracks: ${e.message}")
            emptyList()
        }
    }

    private fun extractTracksFromPath(json: JsonElement, path: String): List<ParsedTrack> {
        return try {
            val parts = path.split(".")
            var current: JsonElement? = json
            
            for (part in parts) {
                current = when (current) {
                    is JsonObject -> current[part]
                    is JsonArray -> {
                        if (part == parts.last()) {
                            return parseTracksFromJsonArray(current)
                        }
                        current.firstOrNull()?.let { 
                            if (it is JsonObject) it[part] else null 
                        }
                    }
                    else -> null
                }
                if (current == null) return emptyList()
            }
            
            if (current is JsonArray) {
                parseTracksFromJsonArray(current)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Parse tracks from a JSON array.
     */
    private fun parseTracksFromJsonArray(array: JsonArray): List<ParsedTrack> {
        return array.mapNotNull { element ->
            try {
                val track = element.jsonObject
                
                // Try new format first (title/subtitle), then old format (name/artists)
                val title = track["title"]?.jsonPrimitive?.contentOrNull 
                    ?: track["name"]?.jsonPrimitive?.contentOrNull 
                    ?: return@mapNotNull null
                
                val artists = track["subtitle"]?.jsonPrimitive?.contentOrNull
                    ?: track["artists"]?.jsonArray
                        ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                        ?.joinToString(", ") 
                    ?: ""
                    
                val duration = track["duration"]?.jsonPrimitive?.longOrNull // New format (ms)
                    ?: track["duration_ms"]?.jsonPrimitive?.longOrNull // Old format
                
                val album = track["album"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                
                ParsedTrack(
                    title = title,
                    artist = artists,
                    album = album,
                    durationMs = duration
                )
            } catch (e: Exception) {
                println("[SpotifyParser] Error parsing track: ${e.message}")
                null
            }
        }
    }
    




    /**
     * Fallback method to parse tracks using Regex when JSON parsing fails.
     */
    private fun parseTracksWithRegex(html: String): List<ParsedTrack> {
        val tracks = mutableListOf<ParsedTrack>()
        try {
            // Basic regex to find track names and artists in the HTML structure
            // This is a best-effort fallback
            val trackPattern = Regex("""<div[^>]*dir="auto"[^>]*>([^<]+)</div>""")
            val matches = trackPattern.findAll(html)
            
            for (match in matches) {
                val content = match.groupValues[1].trim()
                if (content.isNotEmpty() && !content.contains("Spotify")) {
                    tracks.add(ParsedTrack(
                        title = content,
                        artist = "", // Hard to extract reliably with simple regex
                        album = null,
                        durationMs = null
                    ))
                }
            }
        } catch (e: Exception) {
            println("[SpotifyParser] Error parsing tracks with regex: ${e.message}")
        }
        return tracks
    }
}