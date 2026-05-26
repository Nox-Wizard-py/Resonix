package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.paxsenix.api.SpotifyLyricsLine
import com.noxwizard.resonix.paxsenix.api.SpotifyLyricsResponse
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.parser.SpotifyLyricsParser
import com.noxwizard.resonix.paxsenix.utils.MatchScorer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Wraps the Paxsenix Spotify Lyrics API.
 * Endpoint: GET /api/lyrics?url=<spotify_track_url>&format=lrc
 * Docs: https://github.com/Paxsenix0/Spotify-Lyrics-API
 *
 * Requires a Spotify track URL or ID. When [LyricsTrack.spotifyTrackId] is provided,
 * it is used directly. Otherwise this provider is skipped.
 */
class SpotifyProvider(
    private val baseUrl: String = "https://spotify-lyrics-api-yeah.vercel.app",
) : LyricsProvider {

    override val name: String = "Spotify (Paxsenix)"
    override val category: LyricsProviderCategory = LyricsProviderCategory.PREMIUM_WORD_SYNC

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            defaultRequest { url(baseUrl) }
            expectSuccess = true
        }
    }

    override suspend fun search(track: LyricsTrack): LyricsDocument? {
        val spotifyId = track.spotifyTrackId ?: return null
        return runCatching {
            val spotifyUrl = "https://open.spotify.com/track/$spotifyId"
            val response = client.get("/api/lyrics") {
                parameter("url", spotifyUrl)
                parameter("format", "lrc")
            }.body<SpotifyLyricsResponse>()

            if (response.lines.isEmpty()) return null

            val parsed = SpotifyLyricsParser.parse(
                lines = response.lines.map { SpotifyLyricsParser.SpotifyLine(it.timeTag, it.words) },
                syncTypeStr = response.syncType,
            )

            if (parsed.lines.isEmpty()) return null

            LyricsDocument(
                rawText = "", // Raw text not available from this structured API
                lines = parsed.lines,
                providerName = name,
                providerCategory = category,
                syncType = parsed.syncType,
            )
        }.getOrNull()
    }
}
