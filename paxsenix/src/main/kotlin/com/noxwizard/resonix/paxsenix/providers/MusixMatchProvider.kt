package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.parser.MusixMatchLyricsParser
import com.noxwizard.resonix.paxsenix.utils.MatchScorer
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer
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
 * Wraps the Paxsenix MusixMatch Lyrics API.
 * Endpoint: GET /getLyricsMusix.php?t=<title>&a=<artist>&d=<duration>&type=alternative
 * Docs: https://github.com/Paxsenix0/MusixMatch-Lyrics
 *
 * Returns raw LRC string. Duration can be in "mm:ss" or total seconds.
 */
class MusixMatchProvider(
    private val baseUrl: String = "https://paxsenixofc.my.id/server",
    private val confidenceThreshold: Float = 0.5f,
) : LyricsProvider {

    override val name: String = "MusixMatch (Paxsenix)"
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

    override suspend fun search(track: LyricsTrack): LyricsDocument? = runCatching {
        val durationParam = if (track.durationSec > 0) {
            val min = track.durationSec / 60
            val sec = track.durationSec % 60
            "%d:%02d".format(min, sec)
        } else null

        val raw: String = client.get("/getLyricsMusix.php") {
            parameter("t", track.title)
            parameter("a", track.artist)
            if (durationParam != null) parameter("d", durationParam)
            parameter("type", "alternative")
        }.body()

        if (raw.isBlank()) return null

        val sanitized = LyricsPayloadSanitizer.sanitize(raw)
        val parsed = MusixMatchLyricsParser.parse(sanitized)
        if (parsed.lines.isEmpty()) return null

        val confidence = MatchScorer.score(
            track = track,
            candidateTitle = track.title,
            candidateArtist = track.artist,
            candidateDurationSec = track.durationSec,
        )

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
