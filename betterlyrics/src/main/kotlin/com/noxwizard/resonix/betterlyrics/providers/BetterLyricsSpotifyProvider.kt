package com.noxwizard.resonix.betterlyrics.providers

import com.noxwizard.resonix.betterlyrics.api.SpotifyLyricsResponse
import com.noxwizard.resonix.betterlyrics.models.LyricsDocument
import com.noxwizard.resonix.betterlyrics.parser.SpotifyLyricsParser
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
 * Fetches Spotify lyrics via the akashrchandran/spotify-lyrics-api.
 * Docs: https://github.com/akashrchandran/spotify-lyrics-api
 *
 * Requires a self-hosted instance configured with a Spotify SP_DC cookie.
 * The [baseUrl] should point to your deployed instance.
 *
 * This provider is SKIPPED when [spotifyTrackId] is null.
 * Spotify data is PREMIUM — word-sync + karaoke-ready syllable support.
 */
class BetterLyricsSpotifyProvider(
    private val baseUrl: String,  // e.g. "https://your-lyrics-api.example.com"
) : BetterLyricsProvider {

    override val name: String = "BetterLyrics Spotify"
    override val priority: Int = 10

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

    override suspend fun fetch(
        title: String,
        artist: String,
        durationSec: Int,
        spotifyTrackId: String?,
    ): LyricsDocument? {
        if (spotifyTrackId.isNullOrBlank()) return null

        return runCatching {
            val response = client.get("/") {
                parameter("trackid", spotifyTrackId)
                parameter("format", "id3")
            }.body<SpotifyLyricsResponse>()

            if (response.error) return null

            val doc = SpotifyLyricsParser.parse(response)
            if (doc.isEmpty) null else doc
        }.getOrNull()
    }
}
