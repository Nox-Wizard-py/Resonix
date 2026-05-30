package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.betterlyrics.TTMLParser
import com.noxwizard.resonix.paxsenix.api.PaxsenixLyrics
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsLine
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.models.SyncType
import com.noxwizard.resonix.paxsenix.models.WordLine
import com.noxwizard.resonix.paxsenix.parser.LrcParser
import com.noxwizard.resonix.paxsenix.parser.TTMLNormalizationPipeline
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer

/**
 * Paxsenix Apple Music provider.
 *
 * Delegates to PaxsenixLyrics.getAppleMusicLyrics() — the AT-identical HTTP layer —
 * which handles search, duration matching (< 10s tolerance), TTML fetch,
 * JSON-wrapped TTML fallback, and LRC fallback internally.
 *
 * This provider only owns the parse/model translation step.
 */
class PaxsenixAppleMusicProvider(
    @Suppress("unused") private val confidenceThreshold: Float = 0.35f,
) : LyricsProvider {

    override val name: String = "AppleMusic (Paxsenix)"
    override val category: LyricsProviderCategory = LyricsProviderCategory.PREMIUM_WORD_SYNC

    override suspend fun search(track: LyricsTrack): LyricsDocument? = runCatching {
        val raw = PaxsenixLyrics.getAppleMusicLyrics(
            title = track.title,
            artist = track.artist,
            durationSeconds = track.durationSec,
        ).getOrNull()

        if (raw.isNullOrBlank()) return@runCatching null

        val sanitized = LyricsPayloadSanitizer.sanitize(raw)

        // TTML path
        if (sanitized.trimStart().let { it.startsWith("<tt") || it.startsWith("<?xml") }) {
            val parsedLines = TTMLParser.parseTTML(sanitized)
            if (parsedLines.isEmpty()) return@runCatching null

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
            if (lines.isEmpty() || lines.none { it.text.isNotBlank() }) return@runCatching null

            val syncType = if (lines.any { it.hasWordSync }) SyncType.WORD_SYNCED else SyncType.LINE_SYNCED
            return@runCatching LyricsDocument(
                rawText = sanitized,
                lines = lines,
                providerName = name,
                providerCategory = category,
                syncType = syncType,
            )
        }

        // LRC fallback path (AT's convertAppleMusicToLrc output)
        val parsed = LrcParser.parse(sanitized)
        if (parsed.lines.isEmpty() || parsed.lines.none { it.text.isNotBlank() }) return@runCatching null

        LyricsDocument(
            rawText = sanitized,
            lines = parsed.lines,
            providerName = name,
            providerCategory = category,
            syncType = parsed.syncType,
        )
    }.getOrNull()
}
