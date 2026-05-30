package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.lrclib.LrcLib
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.parser.LrcParser
import com.noxwizard.resonix.paxsenix.utils.MatchScorer
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer

/**
 * Adapter wrapping the existing :lrclib module.
 * LRCLib returns synced LRC strings.
 */
class LrcLibProvider(
    private val confidenceThreshold: Float = 0.45f,
) : LyricsProvider {

    override val name: String = "LRCLib"
    override val category: LyricsProviderCategory = LyricsProviderCategory.STANDARD_SYNC

    override suspend fun search(track: LyricsTrack): LyricsDocument? = runCatching {
        var diagnosticsRejectedReason = "Unknown"
        var searchCount = 1
        var candidateTitle = track.title
        var candidateArtist = track.artist
        var durationDelta = -1L
        var fetchSuccess = false
        var parseSuccess = false
        var lineCount = 0
        var finalConfidence = 0f

        try {
            val lrcString = LrcLib.getLyrics(
                title = track.title,
                artist = track.artist,
                duration = track.durationSec,
                album = track.album,
            ).getOrNull()

            if (lrcString.isNullOrBlank()) {
                diagnosticsRejectedReason = "Fetch Failed or Empty Raw"
                return@runCatching null
            }
            fetchSuccess = true

            val sanitized = LyricsPayloadSanitizer.sanitize(lrcString)
            val parsed = LrcParser.parse(sanitized)
            
            if (parsed.lines.isEmpty() || !parsed.lines.any { it.text.isNotBlank() }) {
                diagnosticsRejectedReason = "Renderability Gate: Empty/Blank Parsed Lines"
                return@runCatching null
            }
            parseSuccess = true
            lineCount = parsed.lines.size

            finalConfidence = MatchScorer.score(
                track = track,
                candidateTitle = track.title,
                candidateArtist = track.artist,
                candidateDurationSec = track.durationSec,
            )

            if (finalConfidence < confidenceThreshold) {
                diagnosticsRejectedReason = "Confidence Too Low (<$confidenceThreshold)"
                return@runCatching null
            }

            diagnosticsRejectedReason = "None"
            LyricsDocument(
                rawText = sanitized,
                lines = parsed.lines,
                providerName = name,
                providerCategory = category,
                syncType = parsed.syncType,
            )
        } finally {
            System.err.println("PaxsenixDiagnostics:\n" + """
                |Provider: $name
                |Search Results: $searchCount
                |Selected Candidate: $candidateTitle - $candidateArtist
                |Duration Delta: ${if (durationDelta == -1L) "N/A" else "${durationDelta}ms"}
                |Lyrics Fetch Success: $fetchSuccess
                |Parse Success: $parseSuccess
                |Line Count: $lineCount
                |Final Confidence: $finalConfidence
                |Rejected Reason: $diagnosticsRejectedReason
            """.trimMargin())
        }
    }.getOrNull()
}
