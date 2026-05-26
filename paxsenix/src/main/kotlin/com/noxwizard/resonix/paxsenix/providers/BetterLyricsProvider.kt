package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.betterlyrics.BetterLyrics
import com.noxwizard.resonix.betterlyrics.TTMLParser
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsLine
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.models.SyncType
import com.noxwizard.resonix.paxsenix.models.WordLine
import com.noxwizard.resonix.paxsenix.utils.MatchScorer

/**
 * Adapter wrapping the existing :betterlyrics module.
 * BetterLyrics returns TTML which includes word-level timing — mapped to WORD_SYNCED.
 */
class BetterLyricsProvider : LyricsProvider {

    override val name: String = "BetterLyrics"
    override val category: LyricsProviderCategory = LyricsProviderCategory.PREMIUM_WORD_SYNC

    override suspend fun search(track: LyricsTrack): LyricsDocument? = runCatching {
        val ttml = BetterLyrics.fetchTTML(
            artist = track.artist,
            title = track.title,
            duration = track.durationSec,
        ) ?: return null

        val sanitized = LyricsPayloadSanitizer.sanitize(ttml)
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

        val lines = com.noxwizard.resonix.paxsenix.parser.TTMLNormalizationPipeline.normalize(rawLines)

        val syncType = if (lines.any { it.hasWordSync }) SyncType.WORD_SYNCED else SyncType.LINE_SYNCED

        val confidence = MatchScorer.score(
            track = track,
            candidateTitle = track.title,
            candidateArtist = track.artist,
            candidateDurationSec = track.durationSec,
        )

        LyricsDocument(
            rawText = ttml,
            lines = lines,
            providerName = name,
            providerCategory = category,
            syncType = syncType,
        )
    }.getOrNull()
}
