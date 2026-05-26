package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.paxsenix.api.PaxsenixLyrics
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.parser.LrcParser
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer
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

class PaxsenixDeezerProvider(
    private val baseUrl: String = "https://paxsenixofc.my.id/server",
    private val confidenceThreshold: Float = 0.5f,
) : LyricsProvider {

    override val name: String = "Deezer (Paxsenix)"
    override val category: LyricsProviderCategory = LyricsProviderCategory.STANDARD_SYNC

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            defaultRequest { url(baseUrl) }
            expectSuccess = true
        }
    }

    override suspend fun search(track: LyricsTrack): LyricsDocument? = runCatching {
        val raw = PaxsenixLyrics.getLyrics(
            track.title, track.artist, track.durationSec.toInt()
        ).getOrNull() ?: return@runCatching null

        if (raw.isBlank()) return null
        val sanitized = LyricsPayloadSanitizer.sanitize(raw)
        val parsed = LrcParser.parse(sanitized)
        if (parsed.lines.isEmpty()) return null

        val confidence = MatchScorer.score(track, track.title, track.artist, candidateDurationSec = track.durationSec)

        if (confidence < confidenceThreshold) return null

        LyricsDocument(
            rawText = sanitized,
            lines = parsed.lines,
            providerName = name,
            providerCategory = category,
            syncType = parsed.syncType,
        )
    }.getOrNull()
}
