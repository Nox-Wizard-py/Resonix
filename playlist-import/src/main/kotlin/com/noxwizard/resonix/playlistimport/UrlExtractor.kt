package com.noxwizard.resonix.playlistimport

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * URL detection and non-Spotify extraction utility.
 * Spotify extraction is handled by SpotifyWebViewExtractor.
 */
object UrlExtractor {

    private val client = HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
            }
        }
    }

    fun isSupportedUrl(url: String): Boolean = isSpotifyUrl(url)

    fun isSpotifyUrl(url: String): Boolean =
        url.contains("spotify.com/playlist/") || url.contains("spotify.com/album/")

    /**
     * Extract tracks from a supported URL (Spotify).
     * Parses the embed page for up to 100 tracks natively.
     */
    suspend fun extract(url: String): ImportInput = withContext(Dispatchers.IO) {
        try {
            if (!isSpotifyUrl(url)) {
                return@withContext ImportInput.NeedsManualInput("", "Imported Playlist")
            }

            val isAlbum = url.contains("spotify.com/album/")
            val type = if (isAlbum) "album" else "playlist"
            val id = Regex("""spotify\.com/(playlist|album)/([a-zA-Z0-9]+)""")
                .find(url)?.groupValues?.getOrNull(2)
                ?: return@withContext ImportInput.NeedsManualInput("", "Imported Playlist")

            val embedUrl = "https://open.spotify.com/embed/$type/$id"
            
            val response = client.get(embedUrl) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
            val html = response.bodyAsText()

            val scriptRegex = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            val jsonString = scriptRegex.find(html)?.groupValues?.getOrNull(1)
                ?: return@withContext ImportInput.NeedsManualInput("", "Imported Playlist")

            // Cleanly parse name and up to 100 tracks using zero-dependency Regex
            val nameRegex = Regex(""""name"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
            val playlistName = nameRegex.find(jsonString)?.groupValues?.getOrNull(1)
                ?.replace("\\\"", "\"") ?: "Imported Playlist"

            val trackChunks = jsonString.split(""""uid":""")
            val parsedTracks = mutableListOf<ParsedTrack>()

            // Skip the first chunk (everything before the first track)
            for (i in 1 until trackChunks.size) {
                val chunk = trackChunks[i]
                
                // Grab the next "title", "subtitle", and "duration"
                val titleMatch = Regex(""""title"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""").find(chunk)
                val subtitleMatch = Regex(""""subtitle"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""").find(chunk)
                val durationMatch = Regex(""""duration"\s*:\s*(\d+)""").find(chunk)

                val title = titleMatch?.groupValues?.getOrNull(1)?.replace("\\\"", "\"") ?: ""
                val subtitle = subtitleMatch?.groupValues?.getOrNull(1)?.replace("\\\"", "\"") ?: ""
                val duration = durationMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L

                if (title.isNotEmpty()) {
                    parsedTracks.add(
                        ParsedTrack(
                            title = title,
                            artist = subtitle,
                            durationMs = if (duration > 0) duration else null
                        )
                    )
                }

                if (parsedTracks.size >= 100) break
            }

            if (parsedTracks.isNotEmpty()) {
                ImportInput.ParsedTracks(
                    ParsedPlaylist(
                        name = playlistName,
                        tracks = parsedTracks,
                        source = PlaylistSource.URL_EXTRACT
                    )
                )
            } else {
                ImportInput.NeedsManualInput("", playlistName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ImportInput.NeedsManualInput("", "Imported Playlist")
        }
    }
}
