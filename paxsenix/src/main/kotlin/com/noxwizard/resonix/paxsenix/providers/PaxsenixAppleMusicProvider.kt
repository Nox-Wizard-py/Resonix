package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.betterlyrics.TTMLParser
import com.noxwizard.resonix.paxsenix.api.PaxsenixLyrics
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsLine
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.models.SyncType
import com.noxwizard.resonix.paxsenix.models.WordLine
import com.noxwizard.resonix.paxsenix.parser.TTMLNormalizationPipeline
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

class PaxsenixAppleMusicProvider(
    private val baseUrl: String = "https://paxsenixofc.my.id/server",
    private val confidenceThreshold: Float = 0.5f,
) : LyricsProvider {

    override val name: String = "AppleMusic (Paxsenix)"
    override val category: LyricsProviderCategory = LyricsProviderCategory.PREMIUM_WORD_SYNC

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
        val raw = PaxsenixLyrics.getAppleMusicLyrics(
            track.title, track.artist, track.durationSec.toInt()
        ).getOrNull() ?: return@runCatching null

        if (raw.isBlank()) return null
        val sanitized = LyricsPayloadSanitizer.sanitize(raw)
        val parsedLines = TTMLParser.parseTTML(sanitized)
        if (parsedLines.isEmpty()) return null

        val rawLines = parsedLines.map { parsed ->
            val words = parsed.words.map { w ->
                WordLine(
                    text = w.text,
                    startMs = (w.startTime * 1000).toLong(),
                    endMs = (w.endTime * 1000).toLong(),
                )
            }
            LyricsLine(
                text = parsed.text,
                startMs = (parsed.startTime * 1000).toLong(),
                words = words,
            )
        }

        val lines = TTMLNormalizationPipeline.normalize(rawLines)
        val syncType = if (lines.any { it.hasWordSync }) SyncType.WORD_SYNCED else SyncType.LINE_SYNCED
        val confidence = MatchScorer.score(track, track.title, track.artist, candidateDurationSec = track.durationSec)

        if (confidence < confidenceThreshold) return null

        LyricsDocument(
            rawText = sanitized,
            lines = lines,
            providerName = name,
            providerCategory = category,
            syncType = syncType,
        )
    }.getOrNull()
}
